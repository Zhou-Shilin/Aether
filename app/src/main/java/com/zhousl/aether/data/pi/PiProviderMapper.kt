package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.LlmCustomHeader
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.LlmTokenUsage
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.normalizeLlmUserAgent
import com.zhousl.aether.data.normalizeReasoningEffort
import com.zhousl.aether.data.ProviderAuthMethod
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
    val reasoning: Boolean = true,
    val contextWindow: Int = DefaultContextWindow,
    val maxTokens: Int = DefaultMaxTokens,
    val timeoutMillis: Int = 360_000,
    val maxRetries: Int = DefaultMaxRetries,
    val maxRetryDelayMillis: Int = DefaultMaxRetryDelayMillis,
    val authMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    val oauthCredentialJson: String = "",
    val providerEnvironment: Map<String, String> = emptyMap(),
    val fauxResponse: String = "",
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
        put("reasoning", reasoning)
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
)

fun AppSettings.toPiModelConfig(): PiModelConfig =
    LlmProviderConfig(
        id = providerConfigId,
        providerId = piProviderId,
        name = "",
        piProviderId = piProviderId,
        apiKey = apiKey,
        baseUrl = baseUrl,
        authMethod = providerAuthMethod,
        oauthCredentialJson = oauthCredentialJson,
        providerEnvironmentVariables = providerEnvironmentVariables,
        modelId = modelId,
        userAgent = userAgent,
        customHeaders = customHeaders,
    ).toPiModelConfig(
        reasoningEnabled = toPiThinkingLevel() != "off",
        timeoutMillis = llmInactivityReconnectTimeoutSeconds
            .coerceIn(30, 3_600) * 1_000,
    )

internal fun LlmProviderConfig.toPiModelConfig(
    reasoningEnabled: Boolean = false,
    timeoutMillis: Int = 360_000,
): PiModelConfig {
    val definition = PiProviderCatalog.resolve(piProviderId)
    val effectiveAuthMethod = if (
        authMethod == ProviderAuthMethod.ApiKey &&
        !definition.supportsApiKey &&
        definition.supportsAmbientAuth
    ) {
        ProviderAuthMethod.Ambient
    } else {
        authMethod
    }
    return PiModelConfig(
        providerType = if (definition.isBuiltIn) "builtin" else "custom",
        providerConfigId = id.ifBlank {
            if (definition.isBuiltIn) definition.id else "aether-${stableProviderSuffix(baseUrl)}"
        },
        piProviderId = if (definition.isBuiltIn) {
            definition.id
        } else {
            "aether-${stableProviderSuffix(id.ifBlank { baseUrl })}"
        },
        piApi = if (definition.isBuiltIn) "builtin" else "openai-completions",
        modelId = modelId.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = if (effectiveAuthMethod == ProviderAuthMethod.ApiKey) apiKey.trim() else "",
        customHeaders = customHeaders.toPiHeaderMap() +
            ("User-Agent" to normalizeLlmUserAgent(userAgent)),
        reasoning = reasoningEnabled && !definition.isBuiltIn,
        timeoutMillis = timeoutMillis.coerceIn(30_000, 3_600_000),
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
    )

fun PiCompletionResult.toProviderPayloadJson(): String = JSONObject().apply {
    if (assistantMessage.length() > 0) {
        put("piAssistantMessage", JSONObject(assistantMessage.toString()))
    }
    put("provider", provider)
    put("model", model)
    put("responseId", responseId)
    put("stopReason", stopReason)
    usage?.let { tokenUsage ->
        put(
            "usage",
            JSONObject().apply {
                tokenUsage.inputTokens?.let { put("input_tokens", it) }
                tokenUsage.outputTokens?.let { put("output_tokens", it) }
                tokenUsage.totalTokens?.let { put("total_tokens", it) }
                tokenUsage.reasoningTokens?.let { put("reasoning_tokens", it) }
                tokenUsage.cachedInputTokens?.let { put("cached_input_tokens", it) }
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
    ).withMissingTotalResolved()
    return usage.takeIf {
        it.inputTokens != null ||
            it.outputTokens != null ||
            it.totalTokens != null ||
            it.reasoningTokens != null ||
            it.cachedInputTokens != null
    }
}

private fun JSONObject.optPositiveLong(name: String): Long? =
    if (has(name) && !isNull(name)) {
        optLong(name).takeIf { it >= 0L }
    } else {
        null
    }
