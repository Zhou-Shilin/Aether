package com.zhousl.aether.runtime

import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.SharedDiagnosticLogger
import com.zhousl.aether.data.SharedDiagnosticRedactor
import com.zhousl.aether.data.classifySharedPiBridgeStderrLevel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val PiBridgeStderrMaximumLineCharacters = 2 * 1024
private const val PiBridgeMalformedOutputMaximumCharacters = 2 * 1024

class PiBridgeRequestException(
    message: String,
    val code: String = "pi_bridge_error",
) : IllegalStateException(message)

enum class PiBridgeSetupPhase {
    PreparingBridge,
    StartingBridge,
    VerifyingBridge,
}

class SharedPiBridgeClient(
    private val transport: PiBridgeTransport,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val observeComputer: () -> Boolean = { false },
    private val extensionLoadOptionsProvider: suspend () -> SharedExtensionLoadOptions = {
        SharedExtensionLoadOptions()
    },
) {
    private data class PendingRequest(
        val response: CompletableDeferred<JsonObject>,
        val events: Channel<Pair<String, JsonObject>>,
        val eventJob: Job,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private val recoveryMutex = Mutex()
    private val pending = mutableMapOf<String, PendingRequest>()
    private var process: RuntimeProcess? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null

    suspend fun ping(
        onSetupProgress: (PiBridgeSetupPhase) -> Unit = {},
    ): JsonObject = request(
        type = "ping",
        timeoutMillis = 15_000,
        onSetupProgress = onSetupProgress,
    )

    internal suspend fun extensionLoadOptions(): SharedExtensionLoadOptions =
        extensionLoadOptionsProvider()

    suspend fun listProviders(startIfNeeded: Boolean = true): JsonObject =
        request(
            type = "list_providers",
            timeoutMillis = 15_000,
            startIfNeeded = startIfNeeded,
        )

    suspend fun loginProvider(
        providerConfigId: String,
        providerId: String,
        authMethod: String,
        oauthFlow: String,
        onEvent: suspend (String, JsonObject) -> Unit,
    ): JsonObject = request(
        type = "login_provider",
        payload = buildJsonObject {
            put("provider_config_id", providerConfigId)
            put("provider_id", providerId)
            put("auth_method", authMethod)
            put("oauth_flow", oauthFlow)
        },
        timeoutMillis = 15 * 60_000L,
        onEvent = onEvent,
    )

    suspend fun submitAuthPrompt(promptId: String, value: String, cancelled: Boolean): JsonObject =
        request(
            type = "auth_prompt_result",
            payload = buildJsonObject {
                put("prompt_id", promptId)
                put("value", value)
                put("cancelled", cancelled)
            },
            timeoutMillis = 15_000,
            abortOnCancellation = false,
        )

    suspend fun clearProviderCredential(providerConfigId: String): JsonObject =
        request(
            type = "clear_provider_credential",
            payload = buildJsonObject { put("provider_config_id", providerConfigId) },
            timeoutMillis = 15_000,
        )

    suspend fun listExtensions(sessionId: String): JsonObject =
        request(
            type = "list_extensions",
            payload = extensionSessionPayload(sessionId, extensionLoadOptions()),
            timeoutMillis = 30_000,
            abortOnCancellation = false,
        )

    suspend fun reloadExtensions(sessionId: String): JsonObject =
        request(
            type = "reload_extensions",
            payload = extensionSessionPayload(sessionId, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
        )

    suspend fun invokeExtensionCommand(
        sessionId: String,
        command: String,
        args: String = "",
    ): JsonObject = request(
        type = "invoke_extension_command",
        payload = buildJsonObject {
            put("session_id", sessionId)
            put("command", command)
            put("args", args)
            extensionLoadOptions().toPayload().forEach { (key, value) -> put(key, value) }
        },
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = false,
    )

    suspend fun compactSession(
        sessionId: String,
        customInstructions: String = "",
    ): JsonObject = request(
        type = "compact_session",
        payload = buildJsonObject {
            put("session_id", sessionId)
            if (customInstructions.isNotBlank()) put("custom_instructions", customInstructions)
        },
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = false,
    )

    suspend fun navigateSession(
        sessionId: String,
        entryId: String,
        reset: Boolean = false,
        summarize: Boolean = false,
        customInstructions: String = "",
        modelConfig: JsonObject? = null,
        workspaceDirectory: String = "",
        systemPrompt: String = "",
        skillPaths: List<String> = emptyList(),
        runtime: String = "alpine",
        platform: String = "ios",
        workspaceTrusted: Boolean = true,
    ): JsonObject = request(
        type = "navigate_session",
        payload = buildJsonObject {
            put("session_id", sessionId)
            put("entry_id", entryId)
            put("reset", reset)
            put("summarize", summarize)
            if (customInstructions.isNotBlank()) put("custom_instructions", customInstructions)
            modelConfig?.let { put("model_config", it) }
            workspaceDirectory.trim().takeIf(String::isNotBlank)?.let { put("workspace_directory", it) }
            systemPrompt.trim().takeIf(String::isNotBlank)?.let { put("system_prompt", it) }
            if (skillPaths.isNotEmpty()) {
                put("skill_paths", buildJsonArray {
                    skillPaths.distinct().forEach { add(JsonPrimitive(it)) }
                })
            }
            put("runtime", runtime)
            put("platform", platform)
            put("workspace_trusted", workspaceTrusted)
        },
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = true,
    )

    suspend fun reloadSession(sessionId: String): JsonObject = request(
        type = "reload_session",
        payload = buildJsonObject { put("session_id", sessionId) },
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = false,
    )

    suspend fun exportSessionJsonl(sessionId: String): JsonObject = request(
        type = "export_session_jsonl",
        payload = buildJsonObject { put("session_id", sessionId) },
        timeoutMillis = 60_000L,
        abortOnCancellation = false,
    )

    suspend fun importSessionJsonl(sessionId: String, jsonl: String): JsonObject = request(
        type = "import_session_jsonl",
        payload = buildJsonObject {
            put("session_id", sessionId)
            put("jsonl", jsonl)
        },
        timeoutMillis = 60_000L,
        abortOnCancellation = false,
    )

    suspend fun listExtensionPackages(): JsonObject =
        request("list_extension_packages", timeoutMillis = 30_000, abortOnCancellation = false)

    suspend fun listDiscoveredSkills(
        workspaceDirectory: String,
        skillPaths: List<String> = emptyList(),
    ): JsonObject = request(
        type = "list_discovered_skills",
        payload = buildJsonObject {
            put("workspace_directory", workspaceDirectory)
            put("workspace_trusted", true)
            put("skill_paths", kotlinx.serialization.json.JsonArray(skillPaths.map(::JsonPrimitive)))
        },
        timeoutMillis = 30_000,
        abortOnCancellation = false,
    )

    suspend fun installExtensionPackage(source: String, onProgress: suspend (String) -> Unit = {}): JsonObject =
        request(
            type = "install_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
            onEvent = { event, payload ->
                if (event == "extension_package_progress") {
                    onProgress(payload["phase"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            },
        )

    suspend fun updateExtensionPackage(source: String, onProgress: suspend (String) -> Unit = {}): JsonObject =
        request(
            type = "update_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
            onEvent = { event, payload ->
                if (event == "extension_package_progress") {
                    onProgress(payload["phase"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            },
        )

    suspend fun removeExtensionPackage(source: String): JsonObject =
        request(
            type = "remove_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
        )

    suspend fun reloadAllExtensions(
        startIfNeeded: Boolean = true,
        reloadAetherExtensions: Boolean = true,
    ): JsonObject = request(
        type = "reload_all_extensions",
        payload = buildJsonObject {
            extensionLoadOptions().toPayload().forEach { (key, value) -> put(key, value) }
            if (!reloadAetherExtensions) put("skip_aether_extensions", true)
        },
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = false,
        startIfNeeded = startIfNeeded,
    )

    suspend fun getAetherExtensions(
        context: JsonObject = JsonObject(emptyMap()),
        onEvent: suspend (String, JsonObject) -> Unit = { _, _ -> },
    ): JsonObject = request(
        type = "get_aether_extensions",
        payload = aetherExtensionPayload(context, extensionLoadOptions()),
        timeoutMillis = 10 * 60_000L,
        onEvent = onEvent,
        abortOnCancellation = false,
    )

    suspend fun reloadAetherExtensions(
        context: JsonObject = JsonObject(emptyMap()),
        onEvent: suspend (String, JsonObject) -> Unit = { _, _ -> },
    ): JsonObject = request(
        type = "reload_aether_extensions",
        payload = aetherExtensionPayload(context, extensionLoadOptions()),
        timeoutMillis = 10 * 60_000L,
        onEvent = onEvent,
        abortOnCancellation = false,
    )

    suspend fun invokeAetherExtensionAction(
        extensionId: String,
        action: String,
        args: JsonObject = JsonObject(emptyMap()),
        context: JsonObject = JsonObject(emptyMap()),
        onEvent: suspend (String, JsonObject) -> Unit = { _, _ -> },
    ): JsonObject = request(
        type = "invoke_aether_extension_action",
        payload = buildJsonObject {
            put("extension_id", extensionId)
            put("action", action)
            put("args", args)
            put("context", context)
            extensionLoadOptions().toPayload().forEach { (key, value) -> put(key, value) }
        },
        timeoutMillis = 10 * 60_000L,
        onEvent = onEvent,
        abortOnCancellation = false,
    )

    suspend fun dispatchAetherExtensionEvent(
        event: String,
        data: JsonObject = JsonObject(emptyMap()),
        context: JsonObject = JsonObject(emptyMap()),
        onEvent: suspend (String, JsonObject) -> Unit = { _, _ -> },
    ): JsonObject = request(
        type = "dispatch_aether_extension_event",
        payload = buildJsonObject {
            put("event", event)
            put("data", data)
            put("context", context)
            extensionLoadOptions().toPayload().forEach { (key, value) -> put(key, value) }
        },
        timeoutMillis = 10 * 60_000L,
        onEvent = onEvent,
        abortOnCancellation = false,
    )

    suspend fun subscribeAetherExtensions(
        onEvent: suspend (String, JsonObject) -> Unit,
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
        result: JsonObject = JsonObject(emptyMap()),
        error: String = "",
    ): JsonObject = request(
        type = "aether_host_result",
        payload = buildJsonObject {
            put("call_id", callId)
            put("ok", error.isBlank())
            put("result", result)
            put("error", error)
        },
        timeoutMillis = 15_000L,
        abortOnCancellation = false,
    )

    suspend fun request(
        type: String,
        payload: JsonObject = JsonObject(emptyMap()),
        timeoutMillis: Long? = 10 * 60_000L,
        onEvent: suspend (String, JsonObject) -> Unit = { _, _ -> },
        abortOnCancellation: Boolean = type in setOf("run_turn", "complete_once", "follow_up", "login_provider"),
        onSetupProgress: (PiBridgeSetupPhase) -> Unit = {},
        startIfNeeded: Boolean = true,
    ): JsonObject {
        val requestId = "$type-${platformRandomUuid()}"
        val startedAtMillis = platformCurrentTimeMillis()
        val sessionId = payload.string("session_id").ifBlank { null }
        val diagnosticDetails = sharedRequestDiagnosticDetails(type, payload)
        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "request_start",
            sessionId = sessionId,
            requestId = requestId,
            details = diagnosticDetails + mapOf("timeout_millis" to timeoutMillis),
        )
        val activeProcess = try {
            if (startIfNeeded) {
                ensureStarted {
                    onSetupProgress(PiBridgeSetupPhase.PreparingBridge)
                    onSetupProgress(PiBridgeSetupPhase.StartingBridge)
                }
            } else {
                stateMutex.withLock { process }
                    ?: throw PiBridgeRequestException(
                        message = "Pi Bridge is not running.",
                        code = "pi_bridge_not_running",
                    )
            }
        } catch (failure: Throwable) {
            SharedDiagnosticLogger.event(
                category = "pi_bridge",
                event = "request_start_failed",
                level = "error",
                sessionId = sessionId,
                requestId = requestId,
                details = diagnosticDetails + mapOf(
                    "error" to failure.message.orEmpty(),
                ),
            )
            throw failure
        }
        onSetupProgress(PiBridgeSetupPhase.VerifyingBridge)
        val deferred = CompletableDeferred<JsonObject>()
        val events = Channel<Pair<String, JsonObject>>(Channel.UNLIMITED)
        val eventJob = scope.launch {
            for ((event, eventPayload) in events) onEvent(event, eventPayload)
        }
        val pendingRequest = PendingRequest(deferred, events, eventJob)
        stateMutex.withLock {
            pending[requestId] = pendingRequest
        }
        try {
            val frame = buildJsonObject {
                put("id", requestId)
                put("type", type)
                put("payload", if (observeComputer()) buildJsonObject {
                    payload.forEach { (key, value) -> put(key, value) }
                    put("observe_computer", true)
                } else payload)
            }
            writeFrameWithRecovery(
                initialProcess = activeProcess,
                requestId = requestId,
                request = pendingRequest,
                bytes = BridgeFrameCodec().encode(frame),
                restartOnFailure = startIfNeeded,
                onStarting = {
                    onSetupProgress(PiBridgeSetupPhase.PreparingBridge)
                    onSetupProgress(PiBridgeSetupPhase.StartingBridge)
                },
            )
            val response = if (timeoutMillis == null) {
                deferred.await()
            } else {
                withTimeout(timeoutMillis) { deferred.await() }
            }
            return response.also {
                val stopReason = it.string("stop_reason").trim()
                val errorMessage = it.string("error_message").trim()
                val hasPayloadError = stopReason == "error" || errorMessage.isNotBlank()
                SharedDiagnosticLogger.event(
                    category = "pi_bridge",
                    event = "request_end",
                    level = if (hasPayloadError) "warn" else "info",
                    sessionId = sessionId,
                    requestId = requestId,
                    details = diagnosticDetails + buildMap {
                        put("duration_millis", platformCurrentTimeMillis() - startedAtMillis)
                        if (stopReason.isNotBlank()) put("stop_reason", stopReason)
                        if (errorMessage.isNotBlank()) put("error_message", errorMessage)
                    },
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                withContext(NonCancellable) {
                    if (abortOnCancellation) {
                        runCatching {
                            request(
                                type = "abort",
                                payload = buildJsonObject { put("request_id", requestId) },
                                timeoutMillis = 2_000,
                                abortOnCancellation = false,
                            )
                        }
                    }
                    logRequestFailure(requestId, sessionId, diagnosticDetails, startedAtMillis, throwable)
                }
            } else {
                logRequestFailure(requestId, sessionId, diagnosticDetails, startedAtMillis, throwable)
            }
            throw throwable
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock { pending.remove(requestId) }
                events.close()
                eventJob.cancel()
            }
        }
    }

    private suspend fun logRequestFailure(
        requestId: String,
        sessionId: String?,
        diagnosticDetails: Map<String, Any?>,
        startedAtMillis: Long,
        throwable: Throwable,
    ) {
        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = if (throwable is CancellationException) "request_cancelled" else "request_failed",
            level = if (throwable is CancellationException) "warn" else "error",
            sessionId = sessionId,
            requestId = requestId,
            details = diagnosticDetails + mapOf(
                "duration_millis" to (platformCurrentTimeMillis() - startedAtMillis),
                "error" to throwable.message.orEmpty(),
            ),
        )
    }

    suspend fun reset() {
        stateMutex.withLock {
            pending.values.forEach { request ->
                request.events.close()
                request.eventJob.cancel()
                request.response.completeExceptionally(PiBridgeRequestException("Pi Bridge reset."))
            }
            pending.clear()
            readerJob?.cancel()
            readerJob = null
            stderrJob?.cancel()
            stderrJob = null
            process = null
        }
        transport.stop()
    }

    suspend fun close() {
        reset()
        scope.cancel()
    }

    fun dispose() {
        scope.launch { close() }
    }

    private suspend fun ensureStarted(
        onStarting: () -> Unit = {},
    ): RuntimeProcess {
        stateMutex.withLock { process?.let { return it } }
        onStarting()
        SharedDiagnosticLogger.event("pi_bridge", "process_start")
        val started = transport.start()
        stateMutex.withLock {
            process?.let { return it }
            process = started
            readerJob = scope.launch { readFrames(started) }
            stderrJob = scope.launch { readStderr(started) }
        }
        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "process_started",
            details = mapOf("pid" to started.pid.toString()),
        )
        return started
    }

    private suspend fun writeFrameWithRecovery(
        initialProcess: RuntimeProcess,
        requestId: String,
        request: PendingRequest,
        bytes: ByteArray,
        restartOnFailure: Boolean,
        onStarting: () -> Unit,
    ) = writeMutex.withLock {
        try {
            initialProcess.writeStdin(bytes)
        } catch (failure: RuntimeProcessStdinException) {
            stateMutex.withLock {
                if (pending[requestId] === request) pending.remove(requestId)
            }
            if (!restartOnFailure) throw failure
            recoverProcess(initialProcess, failure)
            val restarted = ensureStarted(onStarting)
            stateMutex.withLock { pending[requestId] = request }
            restarted.writeStdin(bytes)
        }
    }

    private suspend fun recoverProcess(
        failedProcess: RuntimeProcess,
        failure: RuntimeProcessStdinException,
    ) = recoveryMutex.withLock {
        val shouldRecover = stateMutex.withLock {
            if (process !== failedProcess) {
                false
            } else {
                process = null
                readerJob?.cancel()
                readerJob = null
                stderrJob?.cancel()
                stderrJob = null
                true
            }
        }
        if (!shouldRecover) return@withLock

        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "process_recover",
            level = "error",
            details = mapOf(
                "pid" to failedProcess.pid.toString(),
                "error" to failure.message.orEmpty(),
            ),
        )
        failAll("Pi Bridge restarted after its runtime process stopped accepting input.")
        transport.stop()
    }

    private suspend fun readFrames(activeProcess: RuntimeProcess) {
        val malformedLines = mutableListOf<Pair<String, String>>()
        val codec = BridgeFrameCodec { line, error ->
            malformedLines += line to error.message.orEmpty()
        }
        try {
            activeProcess.stdout.collect { chunk ->
                val frames = codec.append(chunk)
                malformedLines.forEach { (line, error) ->
                    SharedDiagnosticLogger.event(
                        category = "pi_bridge",
                        event = "malformed_protocol_output",
                        level = "error",
                        details = mapOf(
                            "pid" to activeProcess.pid.toString(),
                            "error" to error,
                            "output" to line.take(PiBridgeMalformedOutputMaximumCharacters),
                            "outputCharacters" to line.length.toString(),
                            "truncated" to
                                (line.length > PiBridgeMalformedOutputMaximumCharacters).toString(),
                        ),
                    )
                }
                malformedLines.clear()
                frames.forEach { dispatchFrame(it) }
            }
            val exit = activeProcess.awaitExit()
            SharedDiagnosticLogger.event(
                category = "pi_bridge",
                event = "process_exit",
                level = if (exit.exitCode == 0) "info" else "error",
                details = mapOf(
                    "pid" to activeProcess.pid.toString(),
                    "exitCode" to exit.exitCode.toString(),
                    "signal" to exit.signalNumber.toString(),
                ),
            )
            failAll("Pi Bridge exited with code ${exit.exitCode}.")
        } catch (throwable: Throwable) {
            if (throwable !is CancellationException) {
                SharedDiagnosticLogger.event(
                    category = "pi_bridge",
                    event = "process_read_failed",
                    level = "error",
                    details = mapOf("error" to throwable.message.orEmpty()),
                )
                failAll(throwable.message ?: "Pi Bridge output failed.")
            }
        } finally {
            val stderrToFinish = stateMutex.withLock {
                if (process === activeProcess) {
                    process = null
                    readerJob = null
                    stderrJob.also { stderrJob = null }
                } else {
                    null
                }
            }
            stderrToFinish?.let { job ->
                val drained = withTimeoutOrNull(1_000) {
                    job.join()
                    true
                } ?: false
                if (!drained) job.cancel()
            }
        }
    }

    private suspend fun readStderr(activeProcess: RuntimeProcess) {
        val pendingLine = StringBuilder()
        suspend fun emitStderrLine(rawLine: String) {
            val trimmed = rawLine.trimEnd('\r')
            if (trimmed.isBlank()) return
            val retained = trimmed.take(PiBridgeStderrMaximumLineCharacters)
            SharedDiagnosticLogger.event(
                category = "pi_bridge",
                event = "stderr",
                level = classifySharedPiBridgeStderrLevel(retained),
                details = mapOf(
                    "pid" to activeProcess.pid.toString(),
                    "line" to retained,
                    "truncated" to (retained.length < trimmed.length),
                ),
            )
        }
        try {
            activeProcess.stderr.collect { chunk ->
                pendingLine.append(chunk.decodeToString())
                while (true) {
                    val newlineIndex = pendingLine.indexOf('\n')
                    if (newlineIndex < 0) break
                    val line = pendingLine.substring(0, newlineIndex)
                    pendingLine.deleteRange(0, newlineIndex + 1)
                    emitStderrLine(line)
                }
                if (pendingLine.length > PiBridgeStderrMaximumLineCharacters * 2) {
                    val line = pendingLine.toString()
                    pendingLine.clear()
                    emitStderrLine(line)
                }
            }
            if (pendingLine.isNotEmpty()) {
                emitStderrLine(pendingLine.toString())
                pendingLine.clear()
            }
        } catch (throwable: Throwable) {
            if (throwable !is CancellationException) {
                SharedDiagnosticLogger.event(
                    category = "pi_bridge",
                    event = "process_stderr_read_failed",
                    level = "warn",
                    details = mapOf(
                        "pid" to activeProcess.pid.toString(),
                        "error" to throwable.message.orEmpty(),
                    ),
                )
            }
        }
    }

    private suspend fun dispatchFrame(frame: JsonObject) {
        if (observeComputer() && frame.string("type") == "event" && (frame.string("event").startsWith("process_") || frame.string("event") == "context_usage")) {
            com.zhousl.aether.data.AgentComputerStore.accept(
                frame.string("event"), frame["payload"] as? JsonObject ?: JsonObject(emptyMap()),
            )
            return
        }
        val id = frame.string("id")
        if (frame.string("type") == "event") {
            logSharedBridgeEventFrame(
                requestId = id,
                event = frame.string("event"),
                payload = frame["payload"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
        val request = stateMutex.withLock { pending[id] } ?: return
        when (frame.string("type")) {
            "event" -> request.events.send(
                frame.string("event") to
                    (frame["payload"] as? JsonObject ?: JsonObject(emptyMap()))
            )
            "error" -> {
                val error = frame["error"] as? JsonObject
                request.events.close()
                scope.launch {
                    request.eventJob.join()
                    request.response.completeExceptionally(
                        PiBridgeRequestException(
                            message = error?.string("message").orEmpty().ifBlank { "Pi Bridge request failed." },
                            code = error?.string("code").orEmpty().ifBlank { "pi_bridge_error" },
                        )
                    )
                }
            }
            "response" -> {
                val ok = frame["ok"]?.jsonPrimitive?.booleanOrNull ?: true
                request.events.close()
                scope.launch {
                    request.eventJob.join()
                    if (ok) {
                        request.response.complete(frame["payload"] as? JsonObject ?: JsonObject(emptyMap()))
                    } else {
                        request.response.completeExceptionally(PiBridgeRequestException("Pi Bridge request failed."))
                    }
                }
            }
        }
    }

    private suspend fun failAll(message: String) {
        stateMutex.withLock {
            pending.values.forEach { request ->
                request.events.close()
                request.eventJob.cancel()
                request.response.completeExceptionally(PiBridgeRequestException(message))
            }
            pending.clear()
        }
    }
}

private fun aetherExtensionPayload(
    context: JsonObject,
    options: SharedExtensionLoadOptions,
): JsonObject = buildJsonObject {
    options.toPayload().forEach { (key, value) -> put(key, value) }
    put("context", context)
}

private fun extensionSessionPayload(
    sessionId: String,
    options: SharedExtensionLoadOptions,
): JsonObject = buildJsonObject {
    put("session_id", sessionId)
    options.toPayload().forEach { (key, value) -> put(key, value) }
}

private fun extensionPackagePayload(
    source: String,
    options: SharedExtensionLoadOptions,
): JsonObject = buildJsonObject {
    put("source", source)
    options.toPayload().forEach { (key, value) -> put(key, value) }
}

private suspend fun logSharedBridgeEventFrame(
    requestId: String,
    event: String,
    payload: JsonObject,
) {
    val sessionId = payload.string("session_id").ifBlank { null }
    when (event) {
        "assistant_retry" -> SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "assistant_retry",
            level = "warn",
            sessionId = sessionId,
            requestId = requestId,
            details = mapOf(
                "attempt" to payload.string("attempt").toIntOrNull(),
                "max_attempts" to payload.string("max_attempts").toIntOrNull(),
                "delay_ms" to payload.string("delay_ms").toLongOrNull(),
                "error_message" to payload.string("error_message"),
            ),
        )

        "assistant_error" -> SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "assistant_error",
            level = "error",
            sessionId = sessionId,
            requestId = requestId,
            details = mapOf(
                "provider" to payload.string("provider"),
                "model" to payload.string("model"),
                "stop_reason" to payload.string("stop_reason"),
                "error_message" to payload.string("error_message"),
            ),
        )

        "tool_call_start" -> SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "tool_call_start",
            sessionId = sessionId,
            requestId = requestId,
            details = mapOf(
                "tool_call_id" to payload.string("id"),
                "tool_name" to payload.string("name"),
            ),
        )

        "tool_call_end" -> {
            val isError = payload["is_error"]?.jsonPrimitive?.booleanOrNull == true
            SharedDiagnosticLogger.event(
                category = "pi_bridge",
                event = "tool_call_end",
                level = if (isError) "warn" else "info",
                sessionId = sessionId,
                requestId = requestId,
                details = mapOf(
                    "tool_call_id" to payload.string("id"),
                    "tool_name" to payload.string("name"),
                    "is_error" to isError,
                ),
            )
        }

        "compaction_start" -> SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "compaction_start",
            sessionId = sessionId,
            requestId = requestId,
            details = mapOf("reason" to payload.string("reason")),
        )

        "compaction_end" -> {
            val aborted = payload["aborted"]?.jsonPrimitive?.booleanOrNull == true
            val errorMessage = payload.string("error_message")
            SharedDiagnosticLogger.event(
                category = "pi_bridge",
                event = "compaction_end",
                level = if (aborted || errorMessage.isNotBlank()) "warn" else "info",
                sessionId = sessionId,
                requestId = requestId,
                details = mapOf(
                    "reason" to payload.string("reason"),
                    "aborted" to aborted,
                    "will_retry" to (payload["will_retry"]?.jsonPrimitive?.booleanOrNull == true),
                    "error_message" to errorMessage,
                ),
            )
        }

        "extension_error" -> SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "extension_error",
            level = "warn",
            sessionId = sessionId,
            requestId = requestId,
            details = mapOf(
                "extension_path" to payload.string("extension_path"),
                "event" to payload.string("event"),
                "error" to payload.string("error"),
            ),
        )
    }
}

private fun sharedRequestDiagnosticDetails(
    type: String,
    payload: JsonObject,
): Map<String, Any?> {
    val modelConfig = payload["model_config"] as? JsonObject
    val baseUrl = modelConfig?.string("base_url").orEmpty()
    return buildMap {
        put("request_type", type)
        payload.string("session_id").takeIf(String::isNotBlank)?.let { put("session_id", it) }
        modelConfig?.string("pi_provider_id")?.takeIf(String::isNotBlank)?.let { put("provider", it) }
        modelConfig?.string("model_id")?.takeIf(String::isNotBlank)?.let { put("model", it) }
        if (baseUrl.isNotBlank()) {
            put("base_url", SharedDiagnosticRedactor.sanitizedBaseUrl(baseUrl))
        }
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
