package com.zhousl.aether.data

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderAuthMethod(
    val storageValue: String,
) {
    ApiKey("api_key"),
    OAuth("oauth"),
    Ambient("ambient");

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: ProviderAuthMethod = ApiKey,
        ): ProviderAuthMethod =
            entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

@Serializable
data class PiProviderEnvironmentVariable(
    val name: String,
    val value: String,
)

data class PiProviderDefinition(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModelId: String,
    val supportsApiKey: Boolean = true,
    val supportsInteractiveApiKey: Boolean = supportsApiKey,
    val supportsOAuth: Boolean = false,
    val supportsAmbientAuth: Boolean = false,
    val requiresBaseUrl: Boolean = false,
    val supportsCustomBaseUrl: Boolean = false,
    val isBuiltIn: Boolean = true,
    val category: String = "Other",
)

const val DefaultPiProviderId = "openai-compatible"
const val DefaultCustomProviderBaseUrl = "https://api.openai.com/v1"
const val DefaultCustomModelId = "gpt-5.4"

object PiProviderCatalog {
    val providers: List<PiProviderDefinition> = listOf(
        builtin(
            "openai",
            "OpenAI",
            "https://api.openai.com/v1",
            "gpt-5.4",
            supportsCustomBaseUrl = true,
            category = "Recommended",
        ),
        builtin("openai-codex", "OpenAI Codex", "https://chatgpt.com/backend-api", "gpt-5.3-codex-spark", supportsApiKey = false, supportsOAuth = true, category = "Recommended"),
        builtin(
            "anthropic",
            "Anthropic",
            "https://api.anthropic.com",
            "claude-sonnet-4-5",
            supportsOAuth = true,
            supportsCustomBaseUrl = true,
            category = "Recommended",
        ),
        builtin("google", "Google", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", category = "Recommended"),
        builtin("google-vertex", "Google Vertex AI", "", "gemini-2.5-flash", supportsInteractiveApiKey = false, supportsAmbientAuth = true, category = "Recommended"),
        builtin("github-copilot", "GitHub Copilot", "https://api.individual.githubcopilot.com", "gpt-5.4", supportsOAuth = true, category = "Recommended"),
        builtin("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-5.4", category = "Recommended"),
        builtin("amazon-bedrock", "Amazon Bedrock", "", "amazon.nova-2-lite-v1:0", supportsInteractiveApiKey = false, supportsAmbientAuth = true, category = "Cloud"),
        builtin("azure-openai-responses", "Azure OpenAI", "", "gpt-5.4", requiresBaseUrl = true, category = "Cloud"),
        builtin("cloudflare-ai-gateway", "Cloudflare AI Gateway", "", "claude-3-5-haiku", category = "Cloud"),
        builtin("cloudflare-workers-ai", "Cloudflare Workers AI", "", "@cf/google/gemma-4-26b-a4b-it", category = "Cloud"),
        builtin("vercel-ai-gateway", "Vercel AI Gateway", "https://ai-gateway.vercel.sh", "anthropic/claude-sonnet-4.5", category = "Cloud"),
        builtin("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash", category = "Model labs"),
        builtin("xai", "xAI", "https://api.x.ai/v1", "grok-4.3", category = "Model labs"),
        builtin("mistral", "Mistral", "https://api.mistral.ai", "mistral-large-latest", category = "Model labs"),
        builtin("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", category = "Model labs"),
        builtin("cerebras", "Cerebras", "https://api.cerebras.ai/v1", "gpt-oss-120b", category = "Model labs"),
        builtin("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct", category = "Model labs"),
        builtin("huggingface", "Hugging Face", "https://router.huggingface.co/v1", "MiniMaxAI/MiniMax-M2.7", category = "Aggregators"),
        builtin("together", "Together", "https://api.together.ai/v1", "Qwen/Qwen3.5-397B-A17B", category = "Aggregators"),
        builtin("fireworks", "Fireworks", "https://api.fireworks.ai/inference", "accounts/fireworks/models/deepseek-v4-flash", category = "Aggregators"),
        builtin("opencode", "OpenCode Zen", "", "big-pickle", category = "Coding"),
        builtin("opencode-go", "OpenCode Zen Go", "", "deepseek-v4-flash", category = "Coding"),
        builtin("kimi-coding", "Kimi For Coding", "https://api.kimi.com/coding", "k2p7", category = "Coding"),
        builtin("zai", "Z.AI", "https://api.z.ai/api/coding/paas/v4", "glm-5.2", category = "China"),
        builtin("zai-coding-cn", "Z.AI Coding CN", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5.2", category = "China"),
        builtin("moonshotai", "Moonshot AI", "https://api.moonshot.ai/v1", "kimi-k2-thinking", category = "China"),
        builtin("moonshotai-cn", "Moonshot AI CN", "https://api.moonshot.cn/v1", "kimi-k2-thinking", category = "China"),
        builtin("minimax", "MiniMax", "https://api.minimax.io/anthropic", "MiniMax-M2.7", category = "China"),
        builtin("minimax-cn", "MiniMax CN", "https://api.minimaxi.com/anthropic", "MiniMax-M2.7", category = "China"),
        builtin("xiaomi", "Xiaomi", "https://api.xiaomimimo.com/v1", "mimo-v2.5-pro", category = "China"),
        builtin("xiaomi-token-plan-cn", "Xiaomi Token Plan CN", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5-pro", category = "China"),
        builtin("xiaomi-token-plan-ams", "Xiaomi Token Plan AMS", "https://token-plan-ams.xiaomimimo.com/v1", "mimo-v2.5-pro", category = "China"),
        builtin("xiaomi-token-plan-sgp", "Xiaomi Token Plan SGP", "https://token-plan-sgp.xiaomimimo.com/v1", "mimo-v2.5-pro", category = "China"),
        builtin("ant-ling", "Ant Ling", "https://api.ant-ling.com/v1", "Ling-2.6-flash", category = "China"),
        custom(DefaultPiProviderId, "OpenAI-compatible endpoint"),
    )

    val builtInProviders: List<PiProviderDefinition>
        get() = providers.filter(PiProviderDefinition::isBuiltIn)

    val recommendedProviders: List<PiProviderDefinition>
        get() = providers.filter { it.category == "Recommended" }

    fun find(id: String?): PiProviderDefinition? =
        providers.firstOrNull { it.id == id?.trim() }

    fun resolve(id: String?): PiProviderDefinition =
        find(id) ?: providers.first { it.id == DefaultPiProviderId }
}

fun inferLegacyPiProviderId(
    legacyProviderStorageValue: String?,
    baseUrl: String,
): String = when (legacyProviderStorageValue?.trim()) {
    "openai_responses" -> "openai"
    "anthropic_messages" -> "anthropic"
    "vertex_express" -> "google-vertex"
    "openai_compatible" -> builtInProviderIdForHost(hostOf(baseUrl)) ?: DefaultPiProviderId
    else -> builtInProviderIdForHost(hostOf(baseUrl)) ?: DefaultPiProviderId
}

fun PiProviderDefinition.defaultAuthMethod(): ProviderAuthMethod = when {
    !supportsApiKey && supportsOAuth -> ProviderAuthMethod.OAuth
    supportsAmbientAuth && !supportsInteractiveApiKey -> ProviderAuthMethod.Ambient
    else -> ProviderAuthMethod.ApiKey
}

private fun builtin(
    id: String,
    displayName: String,
    defaultBaseUrl: String,
    defaultModelId: String,
    supportsApiKey: Boolean = true,
    supportsInteractiveApiKey: Boolean = supportsApiKey,
    supportsOAuth: Boolean = false,
    supportsAmbientAuth: Boolean = false,
    requiresBaseUrl: Boolean = false,
    supportsCustomBaseUrl: Boolean = false,
    category: String,
): PiProviderDefinition = PiProviderDefinition(
    id = id,
    displayName = displayName,
    defaultBaseUrl = defaultBaseUrl,
    defaultModelId = defaultModelId,
    supportsApiKey = supportsApiKey,
    supportsInteractiveApiKey = supportsInteractiveApiKey,
    supportsOAuth = supportsOAuth,
    supportsAmbientAuth = supportsAmbientAuth,
    requiresBaseUrl = requiresBaseUrl,
    supportsCustomBaseUrl = supportsCustomBaseUrl,
    category = category,
)

private fun custom(
    id: String,
    displayName: String,
): PiProviderDefinition = PiProviderDefinition(
    id = id,
    displayName = displayName,
    defaultBaseUrl = DefaultCustomProviderBaseUrl,
    defaultModelId = DefaultCustomModelId,
    supportsApiKey = true,
    requiresBaseUrl = true,
    isBuiltIn = false,
    category = "Custom",
)

private fun hostOf(baseUrl: String): String {
    val authority = baseUrl.trim()
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    return authority.substringBefore(':').lowercase()
}

private fun builtInProviderIdForHost(host: String): String? = when (host) {
    "api.openai.com" -> "openai"
    "api.anthropic.com" -> "anthropic"
    "generativelanguage.googleapis.com" -> "google"
    "aiplatform.googleapis.com" -> "google-vertex"
    "api.deepseek.com" -> "deepseek"
    "openrouter.ai" -> "openrouter"
    "api.groq.com" -> "groq"
    "api.x.ai" -> "xai"
    "api.mistral.ai" -> "mistral"
    "api.cerebras.ai" -> "cerebras"
    "integrate.api.nvidia.com" -> "nvidia"
    "router.huggingface.co" -> "huggingface"
    "api.together.ai" -> "together"
    "api.fireworks.ai" -> "fireworks"
    "api.moonshot.ai" -> "moonshotai"
    "api.moonshot.cn" -> "moonshotai-cn"
    "api.minimax.io" -> "minimax"
    "api.minimaxi.com" -> "minimax-cn"
    "api.xiaomimimo.com" -> "xiaomi"
    else -> null
}
