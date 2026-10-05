package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.LlmCustomHeader
import com.zhousl.aether.data.LlmTokenUsage
import com.zhousl.aether.data.ModelsDevModelLimits
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.normalizeLlmUserAgent
import com.zhousl.aether.data.normalizeReasoningEffort
import com.zhousl.aether.data.ProviderAuthMethod
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val DefaultContextWindow = 128_000
private const val DefaultMaxTokens = 16_384
private const val DefaultMaxRetries = 5
private const val DefaultMaxRetryDelayMillis = 60_000

data class PiModelConfig(
    val providerType: String,
    val providerConfigId: String,
    val piProviderId: String,
    val piApi: String,
    val modelId: String,
    val baseUrl: String,
    val apiKey: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val compatibilityMode: Boolean = false,
    val developerRoleUnsupported: Boolean = false,
    val reasoning: Boolean = true,
    val thinkingLevelMap: Map<String, String> = emptyMap(),
    val contextWindow: Int = DefaultContextWindow,
    val maxTokens: Int = DefaultMaxTokens,
    val timeoutMillis: Int = 360_000,
    val maxRetries: Int = DefaultMaxRetries,
    val maxRetryDelayMillis: Int = DefaultMaxRetryDelayMillis,
    val authMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    val oauthCredentialJson: String = "",
    val providerEnvironment: Map<String, String> = emptyMap(),
    val fauxResponse: String = "",
    /** Levels models.dev reports for this model; null when models.dev does not know it. */
    val modelsDevThinkingLevels: List<String>? = null,
    val modelsDevLimits: ModelsDevModelLimits? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("provider_type", providerType)
        put("provider_config_id", providerConfigId)
        put("pi_provider_id", piProviderId)
        put("pi_api", piApi)
        put("model_id", modelId)
        put("base_url", baseUrl)
        put("api_key", apiKey)
        put("custom_headers", JSONObject().apply {
            customHeaders.forEach { (name, value) -> put(name, value) }
        })
        if (compatibilityMode) put("compatibility_mode", true)
        if (developerRoleUnsupported) put("supports_developer_role", false)
        put("reasoning", reasoning)
        if (thinkingLevelMap.isNotEmpty()) {
            put("thinking_level_map", JSONObject().apply {
                thinkingLevelMap.forEach { (name, value) -> put(name, value) }
            })
        }
        if (modelsDevThinkingLevels != null) {
            put("models_dev", JSONObject().apply {
                put("reasoning", reasoning)
                put("thinking_levels", JSONArray(modelsDevThinkingLevels))
                modelsDevLimits?.contextWindow?.let { put("context_window", it) }
                modelsDevLimits?.maxOutputTokens?.let { put("max_tokens", it) }
                modelsDevLimits?.supportsImageInput?.let { supportsImages ->
                    put("input", JSONArray(if (supportsImages) listOf("text", "image") else listOf("text")))
                }
            })
        }
        put("context_window", contextWindow)
        put("max_tokens", maxTokens)
        put("timeout_ms", timeoutMillis)
        put("max_retries", maxRetries)
        put("max_retry_delay_ms", maxRetryDelayMillis)
        put("auth_method", authMethod.storageValue)
        if (oauthCredentialJson.isNotBlank()) {
            put(
                "oauth_credential",
                runCatching { JSONObject(oauthCredentialJson) }.getOrDefault(JSONObject()),
            )
        }
        put("provider_env", JSONObject().apply {
            providerEnvironment.forEach { (name, value) -> put(name, value) }
        })
        if (fauxResponse.isNotBlank()) put("faux_response", fauxResponse)
    }
}

data class PiCompletionResult(
    val assistantText: String,
    val reasoningText: String = "",
    val assistantMessage: JSONObject = JSONObject(),
    val usage: LlmTokenUsage? = null,
    val provider: String = "",
    val model: String = "",
    val responseId: String = "",
    val stopReason: String = "",
    val errorMessage: String = "",
    val updatedOauthCredentialJson: String = "",
    val developerRoleUnsupportedDetected: Boolean = false,
    val sessionId: String = "",
    val sessionFile: String = "",
    val sessionLeafId: String = "",
    val runtime: String = "",
    val cwd: String = "",
)

fun AppSettings.toPiModelConfig(
    thinkingLevelMap: Map<String, String> = emptyMap(),
    isReasoningModel: Boolean? = null,
    modelsDevThinkingLevels: List<String>? = null,
    modelsDevLimits: ModelsDevModelLimits? = null,
): PiModelConfig {
    val definition = PiProviderCatalog.resolve(piProviderId)
    val effectiveAuthMethod = if (
        providerAuthMethod == ProviderAuthMethod.ApiKey &&
        !definition.supportsApiKey &&
        definition.supportsAmbientAuth
    ) {
        ProviderAuthMethod.Ambient
    } else {
        providerAuthMethod
    }
    val effort = toPiThinkingLevel()
    val reasoningEnabled = isReasoningModel ?: (
        effort != "off" || thinkingLevelMap["off"] == "none"
    )
    return PiModelConfig(
        providerType = if (definition.isBuiltIn) "builtin" else "custom",
        providerConfigId = providerConfigId.ifBlank {
            if (definition.isBuiltIn) definition.id else "aether-${stableProviderSuffix(baseUrl)}"
        },
        piProviderId = if (definition.isBuiltIn) {
            definition.id
        } else {
            "aether-${stableProviderSuffix(providerConfigId.ifBlank { baseUrl })}"
        },
        piApi = if (definition.isBuiltIn) "builtin" else "openai-completions",
        modelId = modelId.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = if (effectiveAuthMethod == ProviderAuthMethod.ApiKey) {
            apiKey.trim()
        } else {
            ""
        },
        customHeaders = customHeaders.toPiHeaderMap() +
            ("User-Agent" to normalizeLlmUserAgent(userAgent)),
        compatibilityMode = !definition.isBuiltIn && compatibilityMode,
        developerRoleUnsupported = developerRoleUnsupported,
        reasoning = reasoningEnabled,
        thinkingLevelMap = thinkingLevelMap,
        contextWindow = modelsDevLimits?.contextWindow ?: DefaultContextWindow,
        maxTokens = modelsDevLimits?.maxOutputTokens ?: DefaultMaxTokens,
        modelsDevThinkingLevels = modelsDevThinkingLevels,
        modelsDevLimits = modelsDevLimits,
        timeoutMillis = llmInactivityReconnectTimeoutSeconds
            .coerceIn(30, 3_600) * 1_000,
        authMethod = effectiveAuthMethod,
        oauthCredentialJson = oauthCredentialJson,
        providerEnvironment = providerEnvironmentVariables
            .mapNotNull { variable ->
                variable.name.trim().takeIf(String::isNotBlank)?.let { name ->
                    name to variable.value
                }
            }
            .toMap(),
    )
}

internal fun AppSettings.toPiThinkingLevel(): String =
    normalizeReasoningEffort(reasoningEffort)

fun fauxPiModelConfig(
    modelId: String = "faux-1",
    response: String = "ok",
): PiModelConfig =
    PiModelConfig(
        providerType = "faux",
        providerConfigId = "faux",
        piProviderId = "faux",
        piApi = "faux",
        modelId = modelId,
        baseUrl = "http://localhost:0",
        fauxResponse = response,
    )

fun JSONObject.toPiCompletionResult(): PiCompletionResult =
    PiCompletionResult(
        assistantText = optString("assistant_text"),
        reasoningText = optString("reasoning_text"),
        assistantMessage = optJSONObject("assistant_message") ?: JSONObject(),
        usage = optJSONObject("usage")?.toLlmTokenUsage(),
        provider = optString("provider"),
        model = optString("model"),
        responseId = optString("response_id"),
        stopReason = optString("stop_reason"),
        errorMessage = optString("error_message"),
        updatedOauthCredentialJson = optJSONObject("oauth_credential")?.toString().orEmpty(),
        developerRoleUnsupportedDetected = optBoolean("developer_role_unsupported_detected"),
        sessionId = optString("session_id"),
        sessionFile = optString("session_file"),
        sessionLeafId = optString("session_leaf_id"),
        runtime = optString("runtime"),
        cwd = optString("cwd"),
    )

fun PiCompletionResult.toProviderPayloadJson(): String = JSONObject().apply {
    if (assistantMessage.length() > 0) {
        put("piAssistantMessage", JSONObject(assistantMessage.toString()))
    }
    put("provider", provider)
    put("model", model)
    put("responseId", responseId)
    put("stopReason", stopReason)
    if (sessionId.isNotBlank()) put("piSessionId", sessionId)
    if (sessionFile.isNotBlank()) put("piSessionFile", sessionFile)
    if (sessionLeafId.isNotBlank()) put("piSessionLeafId", sessionLeafId)
    if (runtime.isNotBlank()) put("piRuntime", runtime)
    if (cwd.isNotBlank()) put("piCwd", cwd)
    usage?.let { tokenUsage ->
        put(
            "usage",
            JSONObject().apply {
                tokenUsage.inputTokens?.let { put("input_tokens", it) }
                tokenUsage.outputTokens?.let { put("output_tokens", it) }
                tokenUsage.totalTokens?.let { put("total_tokens", it) }
                tokenUsage.reasoningTokens?.let { put("reasoning_tokens", it) }
                tokenUsage.cachedInputTokens?.let { put("cached_input_tokens", it) }
                tokenUsage.cacheWriteTokens?.let { put("cache_write_tokens", it) }
                put("request_count", tokenUsage.requestCount)
            },
        )
    }
}.toString()

private fun stableProviderSuffix(baseUrl: String): String =
    baseUrl
        .trim()
        .lowercase(Locale.US)
        .ifBlank { "custom" }
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "custom" }

private fun List<LlmCustomHeader>.toPiHeaderMap(): Map<String, String> =
    mapNotNull { header ->
        val name = header.name.trim()
        if (name.isBlank()) {
            null
        } else {
            name to header.value
        }
    }.toMap()


private fun JSONObject.toLlmTokenUsage(): LlmTokenUsage? {
    val usage = LlmTokenUsage(
        inputTokens = optPositiveLong("input_tokens"),
        outputTokens = optPositiveLong("output_tokens"),
        totalTokens = optPositiveLong("total_tokens"),
        reasoningTokens = optPositiveLong("reasoning_tokens"),
        cachedInputTokens = optPositiveLong("cached_input_tokens"),
        cacheWriteTokens = optPositiveLong("cache_write_tokens"),
    ).withMissingTotalResolved()
    return usage.takeIf {
        it.inputTokens != null ||
            it.outputTokens != null ||
            it.totalTokens != null ||
            it.reasoningTokens != null ||
            it.cachedInputTokens != null ||
            it.cacheWriteTokens != null
    }
}

private fun JSONObject.optPositiveLong(name: String): Long? =
    if (has(name) && !isNull(name)) {
        optLong(name).takeIf { it >= 0L }
    } else {
        null
    }
