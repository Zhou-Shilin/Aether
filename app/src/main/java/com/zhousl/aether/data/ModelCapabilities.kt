package com.zhousl.aether.data

import java.net.URI
import java.util.Locale

internal enum class LlmCompatibilityFamily {
    Generic,
    DeepSeek,
    MiniMax,
    MiMo,
    Moonshot,
    OpenRouter,
    Requesty,
}

internal enum class ReasoningDisableStyle {
    None,
    DeepSeekThinkingDisabled,
    MiniMaxThinkingDisabled,
    OpenRouterReasoningEffortNone,
}

internal data class ModelCapabilities(
    val family: LlmCompatibilityFamily = LlmCompatibilityFamily.Generic,
    val supportsRequiredToolChoice: Boolean = true,
    val supportsInlineImageWithTools: Boolean = true,
    val supportsParallelToolCalls: Boolean = true,
    val supportsReasoningSplit: Boolean = false,
    val reasoningDisableStyle: ReasoningDisableStyle = ReasoningDisableStyle.None,
    val mustPreserveReasoningContentForToolCalls: Boolean = false,
)

internal object ModelCapabilitiesResolver {
    fun resolve(settings: AppSettings): ModelCapabilities {
        val host = settings.normalizedBaseUrlHost()
        val model = settings.modelId.normalizedCapabilityText()

        return when {
            isOpenRouter(host, model) -> ModelCapabilities(
                family = LlmCompatibilityFamily.OpenRouter,
                reasoningDisableStyle = ReasoningDisableStyle.OpenRouterReasoningEffortNone,
            )

            isRequesty(host) -> ModelCapabilities(
                family = LlmCompatibilityFamily.Requesty,
                reasoningDisableStyle = ReasoningDisableStyle.OpenRouterReasoningEffortNone,
            )

            isDeepSeek(host, model) -> ModelCapabilities(
                family = LlmCompatibilityFamily.DeepSeek,
                reasoningDisableStyle = ReasoningDisableStyle.DeepSeekThinkingDisabled,
                mustPreserveReasoningContentForToolCalls = true,
            )

            isMiniMax(host, model) -> ModelCapabilities(
                family = LlmCompatibilityFamily.MiniMax,
                reasoningDisableStyle = ReasoningDisableStyle.MiniMaxThinkingDisabled,
                supportsReasoningSplit = true,
                mustPreserveReasoningContentForToolCalls = true,
            )

            isMiMo(host, model) -> ModelCapabilities(
                family = LlmCompatibilityFamily.MiMo,
                mustPreserveReasoningContentForToolCalls = true,
            )

            isMoonshot(host, model) -> ModelCapabilities(
                family = LlmCompatibilityFamily.Moonshot,
                supportsRequiredToolChoice = false,
                supportsInlineImageWithTools = false,
            )

            else -> ModelCapabilities()
        }
    }

    private fun isOpenRouter(host: String, model: String): Boolean =
        "openrouter" in host || model.startsWith("openrouter/") || model.startsWith("openrouter:")

    private fun isRequesty(host: String): Boolean =
        "requesty" in host

    private fun isDeepSeek(host: String, model: String): Boolean =
        "deepseek" in host || "deepseek" in model

    private fun isMiniMax(host: String, model: String): Boolean =
        "minimax" in host || model.startsWith("minimax-") || model.startsWith("minimax/") ||
            model.startsWith("abab")

    private fun isMiMo(host: String, model: String): Boolean =
        "xiaomimimo" in host || "mimo.mi.com" in host || "mimo" in host ||
            model.startsWith("mimo-") || "/mimo-" in model

    private fun isMoonshot(host: String, model: String): Boolean =
        "moonshot.cn" in host || model.startsWith("kimi-") || "moonshot" in model
}

internal fun AppSettings.modelCapabilities(): ModelCapabilities =
    ModelCapabilitiesResolver.resolve(this)

private fun AppSettings.normalizedBaseUrlHost(): String = runCatching {
    URI(baseUrl.trim()).host.orEmpty().lowercase(Locale.US)
}.getOrDefault("")

private fun String.normalizedCapabilityText(): String =
    trim().lowercase(Locale.US)
