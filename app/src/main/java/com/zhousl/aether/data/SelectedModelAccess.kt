package com.zhousl.aether.data

import com.zhousl.aether.data.pi.PiKernelBridge
import com.zhousl.aether.data.pi.PiModelConfig
import com.zhousl.aether.data.pi.toPiCompletionResult
import com.zhousl.aether.data.pi.toPiThinkingLevel
import com.zhousl.aether.ui.ChatUsageStatistics
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Runs through Aether's EXISTING native model configuration/auth-refresh path.
 * The browser supplies context, never credentials, provider settings or host tools.
 */
data class SelectedAiConfiguration(val settings: AppSettings, val config: PiModelConfig)

class SelectedModelAccess(
    private val bridge: PiKernelBridge,
    private val repository: SettingsRepository,
    private val selection: suspend (JSONObject) -> SelectedAiConfiguration,
    private val catalog: suspend () -> JSONObject,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
    private val usageLedger: ExtensionUsageLedger? = null,
    private val onStreamEvent: ((String, String, JSONObject) -> Unit)? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val cancelledHandles = ConcurrentHashMap<String, Long>()
    private val requests = ConcurrentHashMap<String, Job>()
    private val callerWindows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun register(kernel: AetherModKernel): () -> Unit = kernel.services.register(
        "model_access", "aether-core", priority = -10_000,
        description = "Selected-model access for trusted extensions; auth remains native, no coding session is created.",
        methods = listOf(AetherModServiceMethod("catalog"), AetherModServiceMethod("selection"), AetherModServiceMethod("complete"), AetherModServiceMethod("cancel")),
        handler = { method, args -> invoke(method, args) },
    )

    suspend fun invoke(method: String, args: JSONObject): JSONObject {
        if (method == "cancel") {
            val key = args.getString("request_handle")
            require(Regex("^[a-f0-9-]{36}$").matches(key))
            cancelledHandles[key] = nowMillis()
            requests.remove(key)?.cancel()
            return JSONObject().put("cancelled", true)
        }
        if (method != "complete") return perform(method, args, handle = "")
        val handle = args.getString("request_handle")
        require(Regex("^[a-f0-9-]{36}$").matches(handle) && requests.size < 8)
        val now = nowMillis()
        cancelledHandles.entries.removeAll { now - it.value > 180_000 }
        require(!cancelledHandles.containsKey(handle)) { "Request cancelled before execution." }
        return coroutineScope {
            val work = async(start = CoroutineStart.LAZY) { perform(method, args, handle) }
            require(requests.putIfAbsent(handle, work) == null)
            try {
                if (cancelledHandles.containsKey(handle)) work.cancel()
                work.start()
                work.await()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                error("Extension model request cancelled.")
            } finally {
                requests.remove(handle)
            }
        }
    }

    private fun enforceCallerRateLimit(caller: String) {
        val now = nowMillis()
        val window = callerWindows.computeIfAbsent(caller) { ArrayDeque() }
        synchronized(window) {
            while (window.isNotEmpty() && now - window.first() > RATE_LIMIT_WINDOW_MS) {
                window.removeFirst()
            }
            require(window.size < MAX_REQUESTS_PER_CALLER_WINDOW) {
                "Extension model request rate limit reached ($MAX_REQUESTS_PER_CALLER_WINDOW requests per 10 minutes)."
            }
            window.addLast(now)
        }
    }

    private suspend fun perform(method: String, args: JSONObject, handle: String): JSONObject {
        if (method == "catalog") return catalog()
        val selected = selection(args.optJSONObject("preference") ?: JSONObject())
        val config = selected.config
        val effort = selected.settings.toPiThinkingLevel()
        require(config.modelId.isNotBlank()) { "Select a model in Aether first." }
        val identity = listOf(config.providerConfigId, config.piProviderId, config.piApi, config.baseUrl, config.modelId, effort, config.thinkingLevelMap.toSortedMap().toString()).joinToString("\u0000")
        val selectionId = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val model = JSONObject().put("id", config.modelId).put("name", config.modelId)
            .put("provider", config.piProviderId).put("api", config.piApi).put("baseUrl", "")
            .put("input", org.json.JSONArray(if (config.modelsDevLimits?.supportsImageInput == true) listOf("text", "image") else listOf("text")))
            .put("inputCapabilitiesKnown", config.modelsDevLimits?.supportsImageInput != null)
            .put("reasoning", config.reasoning).put("thinkingLevelMap", JSONObject(config.thinkingLevelMap))
            .put("contextWindow", config.contextWindow).put("maxTokens", config.maxTokens)
        val expected = JSONObject().put("provider", config.piProviderId).put("id", config.modelId).put("thinkingLevel", effort).put("selectionId", selectionId)
        if (method == "selection") return JSONObject().put("supported", true).put("managed", true).put("streaming", true)
            .put("model", model).put("thinkingLevel", effort).put("selectionId", selectionId).put("usagePricesAvailable", false)
            .put("reason", "Uses Aether's selected provider/model/effort automatically; credentials remain in the host.")
        require(method == "complete") { "Unsupported managed-app AI operation." }
        val supplied = args.getJSONObject("expected")
        require(listOf("provider", "id", "thinkingLevel", "selectionId").all { supplied.optString(it) == expected.getString(it) }) {
            "Aether model/effort changed. Review the current selection before continuing; no fallback was chosen."
        }
        require(listOf("apiKey", "api_key", "headers", "baseUrl", "model", "provider", "reasoning", "thinkingLevel", "model_config").none { args.has(it) }) { "Apps cannot override Aether credentials or model settings." }
        val context = args.getJSONObject("context")
        require(context.toString().toByteArray().size <= 900_000) { "App context exceeds its budget. Choose a smaller relevant source; history was not silently truncated." }
        val messages = context.getJSONArray("messages")
        require(messages.length() <= 128) { "App message context limit is 128. No messages were silently discarded." }
        for (i in 0 until messages.length()) require(messages.getJSONObject(i).optString("role") in setOf("system", "user", "assistant", "toolResult")) { "Unsupported app message role." }
        context.optJSONArray("tools")?.let { require(it.length() <= 16 && it.toString().toByteArray().size <= 65536) { "App tool-schema budget exceeded." } }
        val maximum = args.optInt("maxTokens", 8192)
        require(maximum in 64..8192) { "App output budget must be 64–8192 requested tokens." }
        val caller = args.optString("caller", "extension")
        require(Regex("^[A-Za-z0-9_-]{1,80}$").matches(caller))
        val session = args.optString("session_id")
        require(session.isEmpty() || Regex("^[A-Za-z0-9_-]{1,160}$").matches(session)) { "Invalid app session ID." }
        enforceCallerRateLimit(caller)
        val stream = args.optBoolean("stream", false)
        val payload = JSONObject().put("model_config", config.toJson()).put("structured_context", context)
            .put("stream", stream).put("reasoning", effort).put("max_tokens", maximum)
            .put("max_retries", 0).put("timeout_ms", 120_000)
            .put("session_id", "extension:$caller:$session")
        val startedAt = nowMillis()
        val firstTokenAt = AtomicReference<Long?>(null)
        val eventCallback: (suspend (String, JSONObject) -> Unit)? = if (stream) {
            { event, data ->
                if (event in STREAM_DELTA_EVENTS) {
                    firstTokenAt.compareAndSet(null, nowMillis())
                    onStreamEvent?.invoke(handle, event, data)
                }
            }
        } else {
            null
        }
        val raw = try {
            withTimeout(120_000) { bridge.completeOnce(payload, eventCallback) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnosticLogger.exception(
                category = "hosted_model_access",
                event = "complete_failed",
                throwable = error,
                requestId = handle,
                details = mapOf(
                    "caller" to caller,
                    "provider" to config.piProviderId,
                    "model" to config.modelId,
                    "thinking_level" to effort,
                    "stream" to stream,
                ),
            )
            val detail = DiagnosticRedactor.sanitizeString(error.message.orEmpty(), 300).trim()
            val message = if (detail.isNotEmpty()) {
                "The selected Aether provider could not complete this request ($detail). No replacement login or model was used."
            } else {
                "The selected Aether provider could not complete this request. No replacement login or model was used."
            }
            error(message)
        }
        val completedAt = nowMillis()
        // Normal OAuth refresh stays entirely inside the same host repository.
        val completion = raw.toPiCompletionResult()
        if (selected.settings.providerConfigId.isNotBlank() && completion.updatedOauthCredentialJson.isNotBlank()) {
            repository.updateProviderOAuthCredential(selected.settings.providerConfigId, completion.updatedOauthCredentialJson)
        }
        if (selected.settings.providerConfigId.isNotBlank() && completion.developerRoleUnsupportedDetected) {
            repository.setProviderDeveloperRoleUnsupported(selected.settings.providerConfigId, true)
        }
        completion.usage?.let { usage ->
            usageLedger?.record(
                caller = caller,
                statistics = ChatUsageStatistics(
                    inputTokens = usage.inputTokens,
                    outputTokens = usage.outputTokens,
                    totalTokens = usage.totalTokens,
                    reasoningTokens = usage.reasoningTokens,
                    cachedInputTokens = usage.cachedInputTokens,
                    cacheWriteTokens = usage.cacheWriteTokens,
                    outputDurationMillis = usage.outputDurationMillis,
                    requestCount = usage.requestCount.coerceAtLeast(1),
                    tokenUsageSource = if (usage.totalTokens != null || usage.outputTokens != null) "provider" else "unavailable",
                    startedAtMillis = startedAt,
                    firstTokenAtMillis = firstTokenAt.get(),
                    completedAtMillis = completedAt,
                ),
            )
        }
        if (completion.stopReason in setOf("error", "aborted")) {
            val providerDetail = DiagnosticRedactor.sanitizeString(completion.errorMessage, 300).trim()
            diagnosticLogger.event(
                category = "hosted_model_access",
                event = "complete_stopped_with_error",
                level = "warn",
                requestId = handle,
                details = mapOf(
                    "caller" to caller,
                    "provider" to config.piProviderId,
                    "model" to config.modelId,
                    "stop_reason" to completion.stopReason,
                    "error_message" to providerDetail,
                ),
            )
            val reason = if (providerDetail.isNotEmpty()) {
                "The selected Aether provider refused, failed or cancelled the request ($providerDetail)."
            } else {
                "The selected Aether provider refused, failed or cancelled the request."
            }
            throw IllegalArgumentException(reason)
        }
        val message = completion.assistantMessage
        require(!message.has("deferred")) { "Deferred provider results are not automatically polled by this app host." }
        val clean = JSONObject()
        for (key in listOf("role", "content", "api", "provider", "model", "usage", "stopReason", "timestamp", "responseId", "responseModel", "providerThinkingLevel", "thinkingLevel", "endTurn", "durationMs", "rawStopReason")) if (message.has(key)) clean.put(key, message.get(key))
        return JSONObject().put("message", clean).put("selection", expected).put("truncated", completion.stopReason == "length")
    }

    companion object {
        private const val RATE_LIMIT_WINDOW_MS = 10 * 60_000L
        private const val MAX_REQUESTS_PER_CALLER_WINDOW = 120
        private val STREAM_DELTA_EVENTS = setOf(
            "assistant_text_delta",
            "assistant_reasoning_delta",
            "tool_call_start",
            "tool_call_delta",
            "tool_call_end",
        )
    }
}
