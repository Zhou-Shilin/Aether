package com.zhousl.aether.data

import com.zhousl.aether.data.pi.PiKernelBridge
import com.zhousl.aether.data.pi.PiModelConfig
import com.zhousl.aether.data.pi.toPiCompletionResult
import com.zhousl.aether.data.pi.toPiThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import java.security.MessageDigest

/** Runs through Aether's EXISTING native model configuration/auth-refresh path.
 * The browser supplies context, never credentials, provider settings or host tools.
 */
data class SelectedAiConfiguration(val settings: AppSettings, val config: PiModelConfig)
class SelectedModelAccess(
    private val bridge: PiKernelBridge,
    private val repository: SettingsRepository,
    private val selection: (JSONObject) -> SelectedAiConfiguration,
    private val catalog: () -> JSONObject,
) {
    private val cancelledHandles = ConcurrentHashMap<String, Long>()
    private val requests = ConcurrentHashMap<String, Job>()
    fun register(kernel: AetherModKernel): () -> Unit = kernel.services.register(
        "model_access", "aether-core", priority = -10_000,
        description = "Selected-model access for trusted extensions; auth remains native, no coding session is created.",
        methods = listOf(AetherModServiceMethod("catalog"), AetherModServiceMethod("selection"), AetherModServiceMethod("complete"), AetherModServiceMethod("cancel")),
        handler = { method, args -> invoke(method, args) },
    )
    suspend fun invoke(method: String, args: JSONObject): JSONObject {
        if (method == "cancel") { val key = args.getString("request_handle"); require(Regex("^[a-f0-9-]{36}$").matches(key)); cancelledHandles[key] = System.currentTimeMillis(); requests.remove(key)?.cancel(); return JSONObject().put("cancelled", true) }
        if (method != "complete") return perform(method, args)
        val handle = args.getString("request_handle")
        require(Regex("^[a-f0-9-]{36}$").matches(handle) && requests.size < 8)
        cancelledHandles.entries.removeAll { System.currentTimeMillis() - it.value > 180_000 }
        require(!cancelledHandles.containsKey(handle)) { "Request cancelled before execution." }
        return coroutineScope {
            val work = async(start = CoroutineStart.LAZY) { perform(method, args) }
            require(requests.putIfAbsent(handle, work) == null)
            try { if (cancelledHandles.containsKey(handle)) work.cancel(); work.start(); work.await() }
            catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive(); error("Extension model request cancelled.") }
            finally { requests.remove(handle) }
        }
    }
    private suspend fun perform(method: String, args: JSONObject): JSONObject {
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
        if (method == "selection") return JSONObject().put("supported", true).put("managed", true)
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
        val payload = JSONObject().put("model_config", config.toJson()).put("structured_context", context)
            .put("stream", false).put("reasoning", effort).put("max_tokens", maximum)
            .put("max_retries", 0).put("timeout_ms", 120_000)
            .put("session_id", "extension:$caller:$session")
        val raw = try { withTimeout(120_000) { bridge.completeOnce(payload) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error("The selected Aether provider could not complete this request. No replacement login or model was used.") }
        // Normal OAuth refresh stays entirely inside the same host repository.
        val completion = raw.toPiCompletionResult()
        if (selected.settings.providerConfigId.isNotBlank() && completion.updatedOauthCredentialJson.isNotBlank()) {
            repository.updateProviderOAuthCredential(selected.settings.providerConfigId, completion.updatedOauthCredentialJson)
        }
        require(completion.stopReason !in setOf("error", "aborted")) { "The selected Aether provider refused, failed or cancelled the request." }
        val message = completion.assistantMessage
        require(!message.has("deferred")) { "Deferred provider results are not automatically polled by this app host." }
        val clean = JSONObject()
        for (key in listOf("role", "content", "api", "provider", "model", "usage", "stopReason", "timestamp", "responseId", "responseModel", "providerThinkingLevel", "thinkingLevel", "endTurn", "durationMs", "rawStopReason")) if (message.has(key)) clean.put(key, message.get(key))
        return JSONObject().put("message", clean).put("selection", expected).put("truncated", completion.stopReason == "length")
    }
}
