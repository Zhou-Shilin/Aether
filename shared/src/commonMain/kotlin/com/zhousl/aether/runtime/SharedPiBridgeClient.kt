package com.zhousl.aether.runtime

import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.SharedDiagnosticLogger
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

private const val PiBridgeStderrMaximumCharacters = 32 * 1024
private const val PiBridgeStderrMaximumChunkCharacters = 2 * 1024

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

    suspend fun listExtensionPackages(): JsonObject =
        request("list_extension_packages", timeoutMillis = 30_000, abortOnCancellation = false)

    suspend fun installExtensionPackage(source: String): JsonObject =
        request(
            type = "install_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
        )

    suspend fun updateExtensionPackage(source: String): JsonObject =
        request(
            type = "update_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
        )

    suspend fun removeExtensionPackage(source: String): JsonObject =
        request(
            type = "remove_extension_package",
            payload = extensionPackagePayload(source, extensionLoadOptions()),
            timeoutMillis = 10 * 60_000L,
            abortOnCancellation = false,
        )

    suspend fun reloadAllExtensions(): JsonObject = request(
        type = "reload_all_extensions",
        payload = extensionLoadOptions().toPayload(),
        timeoutMillis = 10 * 60_000L,
        abortOnCancellation = false,
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
        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = "request_start",
            details = mapOf("requestId" to requestId, "type" to type),
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
                details = mapOf(
                    "requestId" to requestId,
                    "type" to type,
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
                put("payload", payload)
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
                SharedDiagnosticLogger.event(
                    category = "pi_bridge",
                    event = "request_end",
                    details = mapOf(
                        "requestId" to requestId,
                        "type" to type,
                        "durationMillis" to (platformCurrentTimeMillis() - startedAtMillis).toString(),
                    ),
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
                    logRequestFailure(requestId, type, startedAtMillis, throwable)
                }
            } else {
                logRequestFailure(requestId, type, startedAtMillis, throwable)
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
        type: String,
        startedAtMillis: Long,
        throwable: Throwable,
    ) {
        SharedDiagnosticLogger.event(
            category = "pi_bridge",
            event = if (throwable is CancellationException) "request_cancelled" else "request_failed",
            level = if (throwable is CancellationException) "info" else "error",
            details = mapOf(
                "requestId" to requestId,
                "type" to type,
                "durationMillis" to (platformCurrentTimeMillis() - startedAtMillis).toString(),
                "error" to throwable.message.orEmpty(),
            ),
        )
    }

    suspend fun close() {
        stateMutex.withLock {
            pending.values.forEach { request ->
                request.events.close()
                request.eventJob.cancel()
                request.response.completeExceptionally(PiBridgeRequestException("Pi Bridge closed."))
            }
            pending.clear()
            readerJob?.cancel()
            readerJob = null
            stderrJob?.cancel()
            stderrJob = null
            process = null
        }
        transport.stop()
        scope.cancel()
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
        val codec = BridgeFrameCodec()
        try {
            activeProcess.stdout.collect { chunk ->
                codec.append(chunk).forEach { dispatchFrame(it) }
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
        var loggedCharacters = 0
        var reportedTruncation = false
        try {
            activeProcess.stderr.collect { chunk ->
                val decoded = chunk.decodeToString()
                val remaining = PiBridgeStderrMaximumCharacters - loggedCharacters
                if (remaining <= 0) {
                    if (!reportedTruncation) {
                        reportedTruncation = true
                        SharedDiagnosticLogger.event(
                            category = "pi_bridge",
                            event = "process_stderr_truncated",
                            level = "warn",
                            details = mapOf("pid" to activeProcess.pid.toString()),
                        )
                    }
                    return@collect
                }
                val retained = decoded.take(
                    minOf(remaining, PiBridgeStderrMaximumChunkCharacters),
                )
                if (retained.isNotBlank()) {
                    loggedCharacters += retained.length
                    SharedDiagnosticLogger.event(
                        category = "pi_bridge",
                        event = "process_stderr",
                        level = "warn",
                        details = mapOf(
                            "pid" to activeProcess.pid.toString(),
                            "message" to retained,
                            "chunkCharacters" to decoded.length.toString(),
                            "truncated" to (retained.length < decoded.length).toString(),
                        ),
                    )
                }
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
        val id = frame.string("id")
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

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
