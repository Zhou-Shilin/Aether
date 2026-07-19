package com.zhousl.aether.data.pi

import com.zhousl.aether.data.PiExtensionLoadOptions
import com.zhousl.aether.data.AetherDiagnosticLogger
import com.zhousl.aether.data.DiagnosticRedactor
import com.zhousl.aether.runtime.AlpineRuntime
import com.zhousl.aether.runtime.AlpineSetupActivity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val PiBridgeAssetPath = "pi-bridge/bridge.mjs"
private const val PiBridgeGuestPath = "/root/.aether/pi-bridge/bridge.mjs"
private const val PiBridgeWorkingDirectory = "/root/.aether/pi-bridge"
private const val PiBridgeNodeMinVersion = "22.19.0"
private const val PiBridgeVersion = "2.0.0-alpha.0"
private const val PiAiVersion = "0.80.6"
private const val PiAgentCoreVersion = "0.80.6"
private const val PiCodingAgentVersion = "0.80.6"
private const val PiBridgeRequestTimeoutMillis = 10 * 60 * 1000L
private const val PiBridgeOAuthTimeoutMillis = 15 * 60 * 1000L
private const val PiBridgePingTimeoutMillis = 15_000L
private const val CancelledRequestRetentionMillis = 5 * 60 * 1000L

private data class PendingPiBridgeRequest(
    val response: CompletableDeferred<PiBridgeFrame>,
    val processGeneration: Long,
    val eventChannel: Channel<PiBridgeFrame>? = null,
    val eventJob: Job? = null,
)

private data class ActivePiBridgeProcess(
    val process: Process,
    val writer: BufferedWriter,
    val generation: Long,
)

class PiKernelBridge(
    private val alpineRuntime: AlpineRuntime,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    private val mutex = Mutex()
    private val pendingRequests = ConcurrentHashMap<String, PendingPiBridgeRequest>()
    private val cancelledRequestIds = ConcurrentHashMap<String, Long>()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processStateLock = Any()
    private val nextProcessGeneration = AtomicLong(0L)
    @Volatile
    private var activeProcess: ActivePiBridgeProcess? = null

    suspend fun ping(
        onSetupProgress: (PiCoreSetupUpdate) -> Unit = {},
    ): JSONObject =
        request(
            type = "ping",
            timeoutMillis = PiBridgePingTimeoutMillis,
            onSetupProgress = onSetupProgress,
        )

    suspend fun listProviders(startIfNeeded: Boolean = true): JSONObject =
        request(
            type = "list_providers",
            timeoutMillis = PiBridgePingTimeoutMillis,
            startIfNeeded = startIfNeeded,
        )

    suspend fun loginProvider(
        providerConfigId: String,
        providerId: String,
        authMethod: String,
        oauthFlow: String = "",
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "login_provider",
            payload = JSONObject()
                .put("provider_config_id", providerConfigId)
                .put("provider_id", providerId)
                .put("auth_method", authMethod)
                .put("oauth_flow", oauthFlow),
            timeoutMillis = PiBridgeOAuthTimeoutMillis,
            onEvent = onEvent,
            abortOnCancellation = true,
        )

    suspend fun clearProviderCredential(providerConfigId: String): JSONObject =
        request(
            type = "clear_provider_credential",
            payload = JSONObject().put("provider_config_id", providerConfigId),
            timeoutMillis = PiBridgePingTimeoutMillis,
        )

    suspend fun submitAuthPrompt(
        promptId: String,
        value: String,
        cancelled: Boolean = false,
    ): JSONObject =
        request(
            type = "auth_prompt_result",
            payload = JSONObject()
                .put("prompt_id", promptId)
                .put("value", value)
                .put("cancelled", cancelled),
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun completeOnce(
        payload: JSONObject,
        onEvent: (suspend (String, JSONObject) -> Unit)? = null,
    ): JSONObject =
        request(
            type = "complete_once",
            payload = payload,
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
        )

    suspend fun runTurn(
        payload: JSONObject,
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "run_turn",
            payload = payload,
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
        )

    suspend fun steer(
        sessionId: String,
        message: JSONObject,
    ): JSONObject =
        request(
            type = "steer",
            payload = JSONObject()
                .put("session_id", sessionId)
                .put("message", message),
            timeoutMillis = PiBridgePingTimeoutMillis,
        )

    suspend fun followUp(
        sessionId: String,
        message: JSONObject,
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "follow_up",
            payload = JSONObject()
                .put("session_id", sessionId)
                .put("message", message),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
        )

    suspend fun closeSession(sessionId: String): JSONObject =
        request(
            type = "close_session",
            payload = JSONObject().put("session_id", sessionId),
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
            startIfNeeded = false,
        )

    suspend fun listExtensions(sessionId: String): JSONObject =
        request(
            type = "list_extensions",
            payload = JSONObject().put("session_id", sessionId),
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun reloadExtensions(sessionId: String): JSONObject =
        request(
            type = "reload_extensions",
            payload = JSONObject().put("session_id", sessionId),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun invokeExtensionCommand(
        sessionId: String,
        command: String,
        args: String = "",
    ): JSONObject =
        request(
            type = "invoke_extension_command",
            payload = JSONObject()
                .put("session_id", sessionId)
                .put("command", command)
                .put("args", args),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun listExtensionPackages(): JSONObject =
        request(
            type = "list_extension_packages",
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun installExtensionPackage(
        source: String,
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
    ): JSONObject =
        request(
            type = "install_extension_package",
            payload = extensionLoadOptionsPayload(loadOptions).put("source", source),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun removeExtensionPackage(
        source: String,
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
    ): JSONObject =
        request(
            type = "remove_extension_package",
            payload = extensionLoadOptionsPayload(loadOptions).put("source", source),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun updateExtensionPackage(
        source: String,
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
    ): JSONObject =
        request(
            type = "update_extension_package",
            payload = extensionLoadOptionsPayload(loadOptions).put("source", source),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun reloadAllExtensions(
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
    ): JSONObject =
        request(
            type = "reload_all_extensions",
            payload = extensionLoadOptionsPayload(loadOptions),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            abortOnCancellation = false,
        )

    suspend fun reloadAetherExtensions(
        context: JSONObject,
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "reload_aether_extensions",
            payload = extensionLoadOptionsPayload(loadOptions).put("context", context),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
            abortOnCancellation = false,
        )

    suspend fun getAetherExtensions(
        context: JSONObject,
        loadOptions: PiExtensionLoadOptions = PiExtensionLoadOptions(),
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "get_aether_extensions",
            payload = extensionLoadOptionsPayload(loadOptions).put("context", context),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
            abortOnCancellation = false,
        )

    suspend fun invokeAetherExtensionAction(
        extensionId: String,
        action: String,
        args: JSONObject,
        context: JSONObject,
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "invoke_aether_extension_action",
            payload = JSONObject()
                .put("extension_id", extensionId)
                .put("action", action)
                .put("args", args)
                .put("context", context),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
            abortOnCancellation = false,
        )

    suspend fun dispatchAetherExtensionEvent(
        event: String,
        data: JSONObject,
        context: JSONObject,
        onEvent: suspend (String, JSONObject) -> Unit,
    ): JSONObject =
        request(
            type = "dispatch_aether_extension_event",
            payload = JSONObject()
                .put("event", event)
                .put("data", data)
                .put("context", context),
            timeoutMillis = PiBridgeRequestTimeoutMillis,
            onEvent = onEvent,
            abortOnCancellation = false,
        )

    suspend fun subscribeAetherExtensions(
        onEvent: suspend (String, JSONObject) -> Unit,
    ) {
        request(
            type = "subscribe_aether_extensions",
            timeoutMillis = null,
            onEvent = onEvent,
            abortOnCancellation = false,
        )
    }

    suspend fun sendAetherHostResult(
        callId: String,
        result: JSONObject = JSONObject(),
        error: String = "",
    ): JSONObject =
        request(
            type = "aether_host_result",
            payload = JSONObject()
                .put("call_id", callId)
                .put("ok", error.isBlank())
                .put("result", result)
                .put("error", error),
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
        )

    private fun extensionLoadOptionsPayload(
        loadOptions: PiExtensionLoadOptions,
    ): JSONObject = JSONObject().apply {
        put("disabled_extension_paths", org.json.JSONArray(loadOptions.disabledExtensionPaths.toList()))
        put("disabled_package_sources", org.json.JSONArray(loadOptions.disabledPackageSources.toList()))
    }

    suspend fun sendHostToolResult(payload: JSONObject) {
        request(
            type = "host_tool_result",
            payload = payload,
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
        )
    }

    suspend fun sendHostToolProgress(payload: JSONObject) {
        request(
            type = "host_tool_progress",
            payload = payload,
            timeoutMillis = PiBridgePingTimeoutMillis,
            abortOnCancellation = false,
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock {
            eventScope.coroutineContext.cancelChildren()
            pendingRequests.values.forEach { pending ->
                pending.response.completeExceptionally(PiBridgeException("Pi bridge stopped."))
                pending.eventChannel?.close()
            }
            pendingRequests.clear()
            cancelledRequestIds.clear()
            val stoppedProcess = synchronized(processStateLock) {
                activeProcess.also { activeProcess = null }
            }
            runCatching { stoppedProcess?.writer?.close() }
            runCatching { stoppedProcess?.process?.destroy() }
        }
    }

    private suspend fun request(
        type: String,
        payload: JSONObject = JSONObject(),
        timeoutMillis: Long?,
        onEvent: (suspend (String, JSONObject) -> Unit)? = null,
        abortOnCancellation: Boolean = type == "run_turn" || type == "complete_once" || type == "follow_up",
        onSetupProgress: (PiCoreSetupUpdate) -> Unit = {},
        startIfNeeded: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val id = nextRequestId(type)
        val response = CompletableDeferred<PiBridgeFrame>()
        val eventChannel = onEvent?.let { Channel<PiBridgeFrame>(Channel.UNLIMITED) }
        val eventJob = if (onEvent != null && eventChannel != null) {
            eventScope.launch {
                for (frame in eventChannel) {
                    if (frame.type == "event") {
                        try {
                            onEvent(frame.event, frame.payload)
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            diagnosticLogger.exception(
                                category = "pi_bridge",
                                event = "event_handler_failed",
                                throwable = throwable,
                                details = mapOf(
                                    "request_id" to frame.id,
                                    "event" to frame.event,
                                ),
                            )
                            response.completeExceptionally(throwable)
                            break
                        }
                    } else {
                        response.complete(frame)
                        break
                    }
                }
            }
        } else {
            null
        }
        val diagnosticDetails = requestDiagnosticDetails(type, payload)
        try {
            diagnosticLogger.event(
                category = "pi_bridge",
                event = "request_start",
                requestId = id,
                details = diagnosticDetails,
            )
            val requestProcess = if (startIfNeeded) {
                ensureStartedLocked(onSetupProgress)
            } else {
                currentLiveProcess() ?: return@withContext JSONObject().put("closed", false)
            }
            pendingRequests[id] = PendingPiBridgeRequest(
                response = response,
                processGeneration = requestProcess.generation,
                eventChannel = eventChannel,
                eventJob = eventJob,
            )
            onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.VerifyingBridge))
            val request = PiBridgeRequest(id = id, type = type, payload = payload)
            synchronized(requestProcess.writer) {
                request.writeJsonLine(requestProcess.writer)
                requestProcess.writer.flush()
            }
            val frame = if (timeoutMillis == null) {
                response.await()
            } else {
                withTimeout(timeoutMillis) { response.await() }
            }
            if (!frame.ok || frame.type == "error") {
                val error = frame.error
                throw PiBridgeException(
                    message = error?.message?.ifBlank { "Pi bridge request failed." }
                        ?: "Pi bridge request failed.",
                    code = error?.code?.ifBlank { "pi_bridge_error" } ?: "pi_bridge_error",
                )
            }
            diagnosticLogger.event(
                category = "pi_bridge",
                event = "request_end",
                requestId = id,
                details = diagnosticDetails + mapOf("frame_type" to frame.type),
            )
            frame.payload
        } catch (cancellationException: CancellationException) {
            diagnosticLogger.event(
                category = "pi_bridge",
                event = "request_cancelled",
                level = "warn",
                requestId = id,
                details = diagnosticDetails,
            )
            if (abortOnCancellation) {
                markRequestCancelled(id)
                withContext(NonCancellable) {
                    runCatching {
                        request(
                            type = "abort",
                            payload = JSONObject()
                                .put("request_id", id)
                                .put("session_id", payload.optString("session_id")),
                            timeoutMillis = PiBridgePingTimeoutMillis,
                            abortOnCancellation = false,
                        )
                    }
                }
            }
            throw cancellationException
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "pi_bridge",
                event = "request_failed",
                throwable = throwable,
                requestId = id,
                details = diagnosticDetails,
            )
            throw throwable
        } finally {
            pendingRequests.remove(id)
            eventChannel?.close()
            eventJob?.cancelAndJoin()
        }
    }

    private suspend fun ensureStartedLocked(
        onSetupProgress: (PiCoreSetupUpdate) -> Unit = {},
    ): ActivePiBridgeProcess {
        mutex.withLock {
            currentLiveProcess()?.let { return it }
            val staleProcess = synchronized(processStateLock) {
                activeProcess.also { activeProcess = null }
            }
            runCatching { staleProcess?.writer?.close() }
            runCatching { staleProcess?.process?.destroy() }

            ensureNodeAvailable(onSetupProgress)
            onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.PreparingBridge))
            alpineRuntime.installAsset(
                assetPath = PiBridgeAssetPath,
                guestPath = PiBridgeGuestPath,
                executable = false,
            )
            onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.StartingBridge))
            val started = alpineRuntime.startManagedProcess(
                command = "node ${shellQuote(PiBridgeGuestPath)}",
                workingDirectory = PiBridgeWorkingDirectory,
            )
            val startedProcess = ActivePiBridgeProcess(
                process = started,
                writer = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8)),
                generation = nextProcessGeneration.incrementAndGet(),
            )
            synchronized(processStateLock) {
                activeProcess = startedProcess
            }
            startStdoutReader(startedProcess)
            startStderrReader(startedProcess)
            diagnosticLogger.event(
                category = "pi_bridge",
                event = "process_started",
                details = mapOf(
                    "guest_path" to PiBridgeGuestPath,
                    "bridge_version" to PiBridgeVersion,
                    "pi_ai_version" to PiAiVersion,
                    "pi_agent_core_version" to PiAgentCoreVersion,
                    "pi_coding_agent_version" to PiCodingAgentVersion,
                    "node_version" to (readNodeVersion() ?: "unknown"),
                    "process_generation" to startedProcess.generation,
                ),
            )
            return startedProcess
        }
    }

    private fun currentLiveProcess(): ActivePiBridgeProcess? =
        synchronized(processStateLock) {
            activeProcess?.takeIf { it.process.isAlive }
        }

    private suspend fun ensureNodeAvailable(
        onSetupProgress: (PiCoreSetupUpdate) -> Unit,
    ) {
        onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.CheckingAlpine))
        val setup = alpineRuntime.initialize { progress ->
            onSetupProgress(
                PiCoreSetupUpdate(
                    phase = PiCoreSetupPhase.CheckingAlpine,
                    activity = when (progress.activity) {
                        AlpineSetupActivity.Extracting -> PiCoreSetupActivity.Extracting
                        AlpineSetupActivity.Downloading -> PiCoreSetupActivity.Downloading
                        AlpineSetupActivity.Installing -> PiCoreSetupActivity.None
                        AlpineSetupActivity.None -> PiCoreSetupActivity.None
                    },
                    bytesPerSecond = progress.bytesPerSecond,
                    output = progress.output,
                )
            )
        }
        if (!setup.isReady) {
            throw PiBridgeException(
                setup.detail.ifBlank { "Alpine runtime is not ready for the Pi bridge." },
                code = "alpine_not_ready",
            )
        }
        onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.CheckingNode))
        val version = readNodeVersion()
        if (version != null && compareSemver(version, PiBridgeNodeMinVersion) >= 0) return

        onSetupProgress(PiCoreSetupUpdate(PiCoreSetupPhase.InstallingNode))
        diagnosticLogger.event(
            category = "pi_bridge",
            event = "node_profile_install_start",
            level = "warn",
            details = mapOf(
                "current_version" to version.orEmpty(),
                "required_version" to PiBridgeNodeMinVersion,
            ),
        )
        val installState = alpineRuntime.installPackageProfile("node") { progress ->
            onSetupProgress(
                PiCoreSetupUpdate(
                    phase = PiCoreSetupPhase.InstallingNode,
                    activity = PiCoreSetupActivity.Downloading,
                    bytesPerSecond = progress.bytesPerSecond,
                    output = progress.output,
                )
            )
        }
        if (!installState.isReady) {
            throw PiBridgeException(
                installState.detail.ifBlank { "Failed to install Node.js inside Alpine." },
                code = "node_install_failed",
            )
        }
        val installedVersion = readNodeVersion()
        if (installedVersion == null || compareSemver(installedVersion, PiBridgeNodeMinVersion) < 0) {
            throw PiBridgeException(
                "Pi bridge requires Alpine node >= $PiBridgeNodeMinVersion, found ${installedVersion ?: "none"}.",
                code = "node_version_too_old",
            )
        }
    }

    private suspend fun readNodeVersion(): String? {
        val raw = JSONObject(
            alpineRuntime.executeCommand(
                command = "node --version",
                workingDirectory = alpineRuntime.homeDirectory,
                awaitTimeoutMillis = 30_000L,
            )
        )
        if (!raw.optBoolean("ok")) return null
        return raw.optString("stdout")
            .lineSequence()
            .firstOrNull { it.trim().isNotBlank() }
            ?.trim()
            ?.removePrefix("v")
    }

    private fun startStdoutReader(startedProcess: ActivePiBridgeProcess) {
        Thread(
            {
                val parser = PiJsonlParser(
                    onFrame = { frame ->
                        handleFrameFromReader(frame, startedProcess.generation)
                    },
                    onInvalidLine = { line, throwable ->
                        diagnosticLogger.exception(
                            category = "pi_bridge",
                            event = "invalid_stdout_json",
                            throwable = throwable,
                            details = mapOf(
                                "line" to DiagnosticRedactor.sanitizeString(line.take(700)),
                            ),
                        )
                    },
                )
                runCatching {
                    BufferedReader(
                        InputStreamReader(startedProcess.process.inputStream, Charsets.UTF_8)
                    ).useLines { lines ->
                        lines.forEach { line -> parser.accept(line + "\n") }
                    }
                    parser.flush()
                }.onFailure { throwable ->
                    diagnosticLogger.exception(
                        category = "pi_bridge",
                        event = "stdout_reader_failed",
                        throwable = throwable,
                        details = mapOf("process_generation" to startedProcess.generation),
                    )
                }
                eventScope.launch {
                    failPendingRequests(
                        exitedProcess = startedProcess,
                        message = "Pi bridge process exited.",
                    )
                }
            },
            "aether-pi-bridge-stdout-${startedProcess.generation}",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun startStderrReader(startedProcess: ActivePiBridgeProcess) {
        Thread(
            {
                runCatching {
                    BufferedReader(
                        InputStreamReader(startedProcess.process.errorStream, Charsets.UTF_8)
                    ).useLines { lines ->
                        lines.forEach { line ->
                            diagnosticLogger.event(
                                category = "pi_bridge",
                                event = "stderr",
                                level = "warn",
                                details = mapOf(
                                    "line" to DiagnosticRedactor.sanitizeString(line),
                                    "process_generation" to startedProcess.generation,
                                ),
                            )
                        }
                    }
                }
            },
            "aether-pi-bridge-stderr-${startedProcess.generation}",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun handleFrameFromReader(
        frame: PiBridgeFrame,
        processGeneration: Long,
    ) {
        val pending = pendingRequests[frame.id]
        if (pending != null && pending.processGeneration != processGeneration) return
        when {
            pending != null && pending.eventChannel != null -> {
                if (pending.eventChannel.trySend(frame).isFailure) {
                    pending.response.completeExceptionally(
                        PiBridgeException("Pi bridge event queue was closed.", code = "event_queue_closed")
                    )
                }
            }

            pending != null && (frame.type == "response" || frame.type == "error") ->
                pending.response.complete(frame)

            pending != null && frame.type == "event" -> Unit

            pending == null && isRecentlyCancelledRequest(frame.id) -> Unit

            else -> diagnosticLogger.event(
                category = "pi_bridge",
                event = "unknown_frame",
                level = "warn",
                details = mapOf(
                    "frame_type" to frame.type,
                    "request_id" to frame.id,
                ),
            )
        }
    }

    private fun markRequestCancelled(requestId: String) {
        val now = System.currentTimeMillis()
        cancelledRequestIds[requestId] = now
        cancelledRequestIds.forEach { (candidateId, cancelledAt) ->
            if (now - cancelledAt > CancelledRequestRetentionMillis) {
                cancelledRequestIds.remove(candidateId, cancelledAt)
            }
        }
    }

    private fun isRecentlyCancelledRequest(requestId: String): Boolean {
        val cancelledAt = cancelledRequestIds[requestId] ?: return false
        if (System.currentTimeMillis() - cancelledAt <= CancelledRequestRetentionMillis) {
            return true
        }
        cancelledRequestIds.remove(requestId, cancelledAt)
        return false
    }

    private suspend fun failPendingRequests(
        exitedProcess: ActivePiBridgeProcess,
        message: String,
    ) {
        mutex.withLock {
            val isCurrentProcess = synchronized(processStateLock) {
                activeProcess?.process === exitedProcess.process
            }
            if (!isCurrentProcess) return
            pendingRequests.forEach { (requestId, pending) ->
                if (pending.processGeneration != exitedProcess.generation) return@forEach
                if (!pendingRequests.remove(requestId, pending)) return@forEach
                pending.response.completeExceptionally(PiBridgeException(message, code = "bridge_exited"))
                pending.eventChannel?.close()
            }
            synchronized(processStateLock) {
                activeProcess = null
            }
        }
    }

    private fun nextRequestId(type: String): String =
        "${type}-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

    private fun requestDiagnosticDetails(
        type: String,
        payload: JSONObject,
    ): Map<String, Any?> {
        val modelConfig = payload.optJSONObject("model_config")
        return mapOf(
            "request_type" to type,
            "session_id" to payload.optString("session_id"),
            "provider" to modelConfig?.optString("pi_provider_id").orEmpty(),
            "model" to modelConfig?.optString("model_id").orEmpty(),
        ).filterValues(String::isNotBlank)
    }
}

private fun shellQuote(value: String): String =
    "'" + value.replace("'", "'\"'\"'") + "'"

private fun compareSemver(left: String, right: String): Int {
    val leftParts = left.split('.', '-').mapNotNull { it.toIntOrNull() }
    val rightParts = right.split('.', '-').mapNotNull { it.toIntOrNull() }
    val maxSize = maxOf(leftParts.size, rightParts.size, 3)
    for (index in 0 until maxSize) {
        val leftValue = leftParts.getOrNull(index) ?: 0
        val rightValue = rightParts.getOrNull(index) ?: 0
        if (leftValue != rightValue) return leftValue.compareTo(rightValue)
    }
    return 0
}
