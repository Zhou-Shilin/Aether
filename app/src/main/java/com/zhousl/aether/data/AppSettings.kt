package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class AgentModeAuthorizationMethod(
    val storageValue: String,
    val displayName: String,
) {
    Root(
        storageValue = "root",
        displayName = "Root",
    ),
    Shizuku(
        storageValue = "shizuku",
        displayName = "Shizuku",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AgentModeAuthorizationMethod = Shizuku,
        ): AgentModeAuthorizationMethod =
            entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppLanguage(
    val storageValue: String,
    val languageTag: String,
) {
    English(
        storageValue = "en",
        languageTag = "en",
    ),
    SimplifiedChinese(
        storageValue = "zh-CN",
        languageTag = "zh-CN",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AppLanguage = defaultAppLanguage(),
        ): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppThemeMode(
    val storageValue: String,
) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class AgentWorkspaceMode(
    val storageValue: String,
    val displayName: String,
) {
    Shared(
        storageValue = "shared",
        displayName = "Single Workspace",
    ),
    PerSession(
        storageValue = "per_session",
        displayName = "Independent Workspaces",
    );

    companion object {
        fun fromStorage(value: String?): AgentWorkspaceMode =
            entries.firstOrNull { it.storageValue == value } ?: Shared
    }
}

enum class LocalRuntimeId(
    val storageValue: String,
    val displayName: String,
) {
    Termux(
        storageValue = "termux",
        displayName = "Termux",
    ),
    Alpine(
        storageValue = "alpine",
        displayName = "Alpine",
    );

    companion object {
        fun fromStorage(value: String?): LocalRuntimeId? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class PackageProfileState(
    val profileId: String,
    val installed: Boolean = false,
    val installedAtMillis: Long = 0L,
    val lastError: String = "",
)

data class AlpineEnvironmentVariable(
    val name: String,
    val value: String,
)

data class AppSettings(
    val piProviderId: String = DefaultPiProviderId,
    val providerConfigId: String = "",
    val providerAuthMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    val apiKey: String = "",
    val oauthCredentialJson: String = "",
    val providerEnvironmentVariables: List<PiProviderEnvironmentVariable> = emptyList(),
    val baseUrl: String = DefaultCustomProviderBaseUrl,
    val modelId: String = DefaultCustomModelId,
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val providerUserAgentMode: ProviderUserAgentMode = ProviderUserAgentMode.Default,
    val customProviderUserAgent: String? = null,
    val reasoningEffort: String = DefaultReasoningEffort,
    val systemPrompt: String = "You are Aether, a local-first Android agent that can call tools and complete tasks on-device. Use available tools instead of guessing local state.",
    val tavilyApiKey: String = "",
    val tavilyBaseUrl: String = DefaultTavilyBaseUrl,
    val llmInactivityReconnectTimeoutSeconds: Int = DefaultLlmInactivityReconnectTimeoutSeconds,
    val keepTasksRunningInBackground: Boolean = true,
    val notifyOnTaskCompletion: Boolean = true,
    val agentWorkspaceMode: AgentWorkspaceMode = AgentWorkspaceMode.Shared,
    val autoCleanOldCommandHistory: Boolean = true,
    val oldCommandHistoryRetentionHours: Int = DefaultOldCommandHistoryRetentionHours,
    val termuxSetupCompleted: Boolean = false,
    val termuxSetupNoticeDismissed: Boolean = false,
    val termuxLiveOutputEnabled: Boolean = true,
    val termuxEnvironmentVariables: List<TermuxEnvironmentVariable> = emptyList(),
    val enabledRuntimeIds: Set<LocalRuntimeId> = emptySet(),
    val defaultRuntimeId: LocalRuntimeId? = null,
    val alpineSetupCompleted: Boolean = false,
    val alpinePackageProfiles: Map<String, PackageProfileState> = emptyMap(),
    val alpineEnvironmentVariables: List<AlpineEnvironmentVariable> = emptyList(),
    val agentModeAuthorizationEnabled: Boolean = false,
    val agentModeAuthorizationMethod: AgentModeAuthorizationMethod = AgentModeAuthorizationMethod.Shizuku,
    val language: AppLanguage = defaultAppLanguage(),
    val themeMode: AppThemeMode = AppThemeMode.System,
    val defaultChatModelKey: String = "",
    val defaultTitleModelKey: String = "",
    val defaultNamingModelKey: String = "",
    val defaultCompactingModelKey: String = "",
    val onboardingSeenVersion: Int = 0,
    val onboardingCompletedVersion: Int = 0,
    val privacyPolicyAccepted: Boolean = false,
    val lastUpdateCheckAtMillis: Long = 0L,
)

data class LlmCustomHeader(
    val name: String,
    val value: String,
)

data class TermuxEnvironmentVariable(
    val name: String,
    val value: String,
)

const val CurrentOnboardingVersion = 1
const val DefaultReasoningEffort = "off"
val SupportedReasoningEfforts: List<String> = listOf(
    "off",
    "minimal",
    "low",
    "medium",
    "high",
    "xhigh",
    "max",
)

fun normalizeReasoningEffort(value: String?): String =
    value?.trim()?.lowercase(Locale.US)
        ?.let { value -> if (value == "none") "off" else value }
        ?.takeIf { it in SupportedReasoningEfforts }
        ?: DefaultReasoningEffort
const val DefaultLlmInactivityReconnectTimeoutSeconds = 360
const val DefaultOldCommandHistoryRetentionHours = 6
const val MinOldCommandHistoryRetentionHours = 1
const val MaxOldCommandHistoryRetentionHours = 168
private const val MinLlmInactivityReconnectTimeoutSeconds = 30
private const val MaxLlmInactivityReconnectTimeoutSeconds = 3600
const val OnboardingStarterPrompt = "Hi"
const val AetherWebsiteUrl = "https://github.com/Zhou-Shilin"
const val AetherPrivacyPolicyUrl = "https://github.com/Zhou-Shilin/Aether/wiki/Privacy-Policy"

fun defaultAppLanguage(
    locale: Locale = Locale.getDefault(),
): AppLanguage = if (locale.language.equals("zh", ignoreCase = true)) {
    AppLanguage.SimplifiedChinese
} else {
    AppLanguage.English
}


fun normalizeOldCommandHistoryRetentionHours(
    value: Int?,
): Int = when (value) {
    null -> DefaultOldCommandHistoryRetentionHours
    else -> value.coerceIn(
        minimumValue = MinOldCommandHistoryRetentionHours,
        maximumValue = MaxOldCommandHistoryRetentionHours,
    )
}

fun normalizeLlmInactivityReconnectTimeoutSeconds(
    value: Int?,
): Int = when (value) {
    null -> DefaultLlmInactivityReconnectTimeoutSeconds
    else -> value.coerceIn(
        MinLlmInactivityReconnectTimeoutSeconds,
        MaxLlmInactivityReconnectTimeoutSeconds,
    )
}

fun normalizeTavilyBaseUrl(value: String): String =
    value.trim().ifBlank { DefaultTavilyBaseUrl }

fun AppSettings.shouldLaunchOnboarding(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingSeenVersion < onboardingVersion

fun AppSettings.isOnboardingComplete(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingCompletedVersion >= onboardingVersion

fun AppSettings.isProviderSetupValid(): Boolean {
    val definition = PiProviderCatalog.resolve(piProviderId)
    if ((definition.requiresBaseUrl || !definition.isBuiltIn) && baseUrl.trim().isEmpty()) return false
    return when (providerAuthMethod) {
        ProviderAuthMethod.ApiKey ->
            !definition.isBuiltIn ||
                (definition.supportsApiKey && apiKey.isNotBlank())

        ProviderAuthMethod.OAuth ->
            definition.supportsOAuth && oauthCredentialJson.isNotBlank()

        ProviderAuthMethod.Ambient ->
            definition.supportsAmbientAuth
    }
}

fun shouldMarkOnboardingCompleted(
    settings: AppSettings,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isSuccessfulAssistantReply && !settings.isOnboardingComplete()

fun shouldRevealFollowUpTourCard(
    isAwaitingFollowUpTour: Boolean,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isAwaitingFollowUpTour && isSuccessfulAssistantReply

// ──────────────────────────────────────────────────────────────────────────────
// Multi-Provider Configuration
// ──────────────────────────────────────────────────────────────────────────────

enum class ProviderUserAgentMode(val storageValue: String) {
    Aether("aether"),
    Default("default"),
    Custom("custom");

    companion object {
        fun fromStorage(value: String?): ProviderUserAgentMode? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

internal data class NormalizedProviderUserAgent(
    val mode: ProviderUserAgentMode,
    val customValue: String?,
    val headers: List<LlmCustomHeader>,
)

internal fun defaultProviderUserAgentMode(
    piProviderId: String,
    baseUrl: String,
): ProviderUserAgentMode = if (
    piProviderId == "openai" && !isOfficialOpenAiBaseUrl(baseUrl)
) {
    ProviderUserAgentMode.Aether
} else {
    ProviderUserAgentMode.Default
}

internal fun normalizeProviderUserAgent(
    piProviderId: String,
    baseUrl: String,
    mode: ProviderUserAgentMode?,
    customValue: String?,
    headers: List<LlmCustomHeader>,
): NormalizedProviderUserAgent {
    val migratedValue = headers.firstOrNull {
        it.name.trim().equals("User-Agent", ignoreCase = true)
    }?.value
        ?.trim()
        ?.takeIf(String::isSafeHttpHeaderValue)
        .orEmpty()
    val headersWithoutUserAgent = headers.filterNot {
        it.name.trim().equals("User-Agent", ignoreCase = true)
    }
    val fallbackMode = defaultProviderUserAgentMode(
        piProviderId = piProviderId,
        baseUrl = baseUrl,
    )
    val resolvedMode = mode ?: if (migratedValue.isNotBlank()) {
        ProviderUserAgentMode.Custom
    } else {
        fallbackMode
    }
    val resolvedCustomValue = if (resolvedMode == ProviderUserAgentMode.Custom) {
        customValue?.trim()
            ?.takeIf(String::isSafeHttpHeaderValue)
            .orEmpty()
            .ifBlank { migratedValue }
            .takeIf(String::isNotBlank)
    } else {
        null
    }
    return NormalizedProviderUserAgent(
        mode = if (resolvedMode == ProviderUserAgentMode.Custom && resolvedCustomValue == null) {
            fallbackMode
        } else {
            resolvedMode
        },
        customValue = resolvedCustomValue,
        headers = headersWithoutUserAgent,
    )
}


internal fun isOfficialOpenAiBaseUrl(baseUrl: String): Boolean =
    runCatching { java.net.URI(baseUrl.trim()).host.equals("api.openai.com", ignoreCase = true) }
        .getOrDefault(false)

data class LlmProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val name: String,
    val piProviderId: String,
    val apiKey: String,
    val baseUrl: String,
    val authMethod: ProviderAuthMethod = PiProviderCatalog
        .resolve(piProviderId)
        .defaultAuthMethod(),
    val oauthCredentialJson: String = "",
    val providerEnvironmentVariables: List<PiProviderEnvironmentVariable> = emptyList(),
    val modelId: String,
    val manualModelIds: List<String> = listOf(modelId).filter(String::isNotBlank),
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val userAgentMode: ProviderUserAgentMode = ProviderUserAgentMode.Default,
    val customUserAgent: String? = null,
    val cachedModels: List<String> = emptyList(),
    val enabledModelIds: List<String> = cachedModels + manualModelIds,
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

internal fun LlmProviderConfig.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("providerId", providerId)
    put("name", name)
    put("piProviderId", piProviderId)
    put("authMethod", authMethod.storageValue)
    put("apiKey", apiKey)
    put("oauthCredentialJson", oauthCredentialJson)
    put(
        "providerEnvironmentVariables",
        JSONArray().apply {
            providerEnvironmentVariables.forEach { variable ->
                put(
                    JSONObject()
                        .put("name", variable.name)
                        .put("value", variable.value)
                )
            }
        },
    )
    put("baseUrl", baseUrl)
    put("modelId", modelId)
    put("manualModelIds", JSONArray().apply { manualModelIds.forEach(::put) })
    put("customHeaders", customHeaders.toJsonArray())
    put("userAgentMode", userAgentMode.storageValue)
    customUserAgent?.let { put("customUserAgent", it) }
    put("cachedModels", JSONArray().apply { cachedModels.forEach(::put) })
    put("enabledModelIds", JSONArray().apply { enabledModelIds.forEach(::put) })
    put("isEnabled", isEnabled)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

internal fun parseProviderConfigs(rawValue: String): List<LlmProviderConfig> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val storedPiProviderId = json.optString("piProviderId").trim()
                val storedBaseUrl = json.optString("baseUrl").trim()
                val piProviderId = storedPiProviderId.ifBlank {
                    inferLegacyPiProviderId(json.optString("providerType"), storedBaseUrl)
                }
                val providerDefinition = PiProviderCatalog.resolve(piProviderId)
                val providerName = json.optString("name").trim()
                    .ifBlank { providerDefinition.displayName }
                val baseUrl = storedBaseUrl.ifBlank { providerDefinition.defaultBaseUrl }
                val modelId = if (json.has("modelId")) {
                    json.optString("modelId").trim()
                } else {
                    providerDefinition.defaultModelId
                }
                val enabledModelIds = json.optJSONArray("enabledModelIds").toStringListSafe()
                val manualModelIds = if (json.has("manualModelIds")) {
                    json.optJSONArray("manualModelIds").toStringListSafe()
                } else {
                    listOf(modelId).filter(String::isNotBlank)
                }
                val cachedModels = normalizeStringList(
                    buildList {
                        addAll(json.optJSONArray("cachedModels").toStringListSafe())
                        if (!json.has("manualModelIds")) {
                            removeAll(manualModelIds)
                        }
                    }
                )
                val availableModels = normalizeStringList(cachedModels + manualModelIds)
                val inferredProviderId = providerName
                    .sanitizeProviderId()
                    .ifBlank { "${providerDefinition.id.sanitizeProviderId()}_${index + 1}" }
                val storedHeaders = parseCustomHeaders(json.optJSONArray("customHeaders"))
                val userAgent = normalizeProviderUserAgent(
                    piProviderId = piProviderId,
                    baseUrl = baseUrl,
                    mode = ProviderUserAgentMode.fromStorage(json.optString("userAgentMode")),
                    customValue = json.optString("customUserAgent").trim().takeIf(String::isNotBlank),
                    headers = storedHeaders,
                )
                add(
                    LlmProviderConfig(
                        id = json.optString("id").trim().ifBlank { UUID.randomUUID().toString() },
                        providerId = json.optString("providerId").trim().ifBlank { inferredProviderId },
                        name = providerName,
                        piProviderId = piProviderId,
                        authMethod = ProviderAuthMethod.fromStorage(
                            json.optString("authMethod"),
                            providerDefinition.defaultAuthMethod(),
                        ),
                        apiKey = json.optString("apiKey"),
                        oauthCredentialJson = json.optString("oauthCredentialJson"),
                        providerEnvironmentVariables = parseProviderEnvironmentVariables(
                            json.optJSONArray("providerEnvironmentVariables"),
                        ),
                        baseUrl = baseUrl,
                        modelId = modelId,
                        manualModelIds = manualModelIds,
                        customHeaders = userAgent.headers,
                        userAgentMode = userAgent.mode,
                        customUserAgent = userAgent.customValue,
                        cachedModels = cachedModels,
                        enabledModelIds = if (json.has("enabledModelIds")) {
                            normalizeStringList(enabledModelIds.filter(availableModels::contains))
                        } else {
                            availableModels
                        },
                        isEnabled = if (json.has("isEnabled")) {
                            json.optBoolean("isEnabled", true)
                        } else {
                            true
                        },
                        createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun serializeProviderConfigs(configs: List<LlmProviderConfig>): String =
    JSONArray().apply { configs.forEach { put(it.toJson()) } }.toString()

internal fun List<LlmCustomHeader>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { header ->
        put(
            JSONObject().apply {
                put("name", header.name)
                put("value", header.value)
            }
        )
    }
}

internal fun parseCustomHeaders(array: JSONArray?): List<LlmCustomHeader> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val name = json.optString("name").trim()
            if (name.isBlank()) continue
            add(
                LlmCustomHeader(
                    name = name,
                    value = json.optString("value"),
                )
            )
        }
    }.distinctBy { it.name.lowercase(Locale.US) }
}

internal fun parseProviderEnvironmentVariables(
    array: JSONArray?,
): List<PiProviderEnvironmentVariable> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val name = json.optString("name").trim()
            if (name.isBlank()) continue
            add(
                PiProviderEnvironmentVariable(
                    name = name,
                    value = json.optString("value"),
                )
            )
        }
    }.distinctBy { it.name.uppercase(Locale.US) }
}

private fun JSONArray?.toStringListSafe(): List<String> {
    if (this == null) return emptyList()
    return normalizeStringList(
        buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    )
}

private fun normalizeStringList(values: List<String>): List<String> =
    values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

private val ProviderIdPattern = Regex("^[a-z0-9_]+$")

fun isValidProviderId(value: String): Boolean = ProviderIdPattern.matches(value.trim())

fun String.sanitizeProviderId(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')

fun buildModelOptionKey(
    providerConfigId: String,
    modelId: String,
): String = "$providerConfigId::$modelId"

fun LlmProviderConfig.availableModels(): List<String> = normalizeStringList(cachedModels + manualModelIds)

fun LlmProviderConfig.enabledModels(): List<String> = normalizeStringList(
    enabledModelIds.filter { availableModels().contains(it) }
)

data class ProviderModelOption(
    val key: String,
    val providerConfigId: String,
    val providerId: String,
    val providerName: String,
    val piProviderId: String,
    val authMethod: ProviderAuthMethod,
    val apiKey: String,
    val oauthCredentialJson: String,
    val providerEnvironmentVariables: List<PiProviderEnvironmentVariable>,
    val baseUrl: String,
    val modelId: String,
    val customHeaders: List<LlmCustomHeader>,
    val userAgentMode: ProviderUserAgentMode,
    val customUserAgent: String?,
    val fullLabel: String,
    val chatLabel: String,
)

enum class AutomaticModelPurpose {
    Chat,
    Title,
    Naming,
    Compacting,
}

fun List<LlmProviderConfig>.availableModelOptions(
    includeDisabledProviders: Boolean = false,
    includeDisabledModels: Boolean = false,
): List<ProviderModelOption> {
    val scopedConfigs = (if (includeDisabledProviders) this else filter { it.isEnabled })
        .filter { config ->
            val definition = PiProviderCatalog.resolve(
                config.piProviderId,
            )
            config.providerId.trim().isNotEmpty() &&
                (!definition.requiresBaseUrl || config.baseUrl.trim().isNotEmpty())
        }
    val modelCounts = scopedConfigs
        .flatMap { config ->
            val models = if (includeDisabledModels) config.availableModels() else config.enabledModels()
            models.map { modelId -> modelId to config.id }
        }
        .groupingBy { it.first }
        .eachCount()

    return scopedConfigs.flatMap { config ->
        val models = if (includeDisabledModels) config.availableModels() else config.enabledModels()
        models.map { modelId ->
            val providerId = config.providerId.trim()
            val providerName = config.name.trim().ifBlank { providerId }
            val normalizedModelId = modelId.trim()
            val fullLabel = "$providerId/$normalizedModelId"
            ProviderModelOption(
                key = buildModelOptionKey(config.id, normalizedModelId),
                providerConfigId = config.id,
                providerId = providerId,
                providerName = providerName,
                piProviderId = config.piProviderId,
                authMethod = config.authMethod,
                apiKey = config.apiKey,
                oauthCredentialJson = config.oauthCredentialJson,
                providerEnvironmentVariables = config.providerEnvironmentVariables,
                baseUrl = config.baseUrl.trim(),
                modelId = normalizedModelId,
                customHeaders = config.customHeaders,
                userAgentMode = config.userAgentMode,
                customUserAgent = config.customUserAgent,
                fullLabel = fullLabel,
                chatLabel = if ((modelCounts[normalizedModelId] ?: 0) > 1) fullLabel else normalizedModelId,
            )
        }
    }.sortedWith(
        compareBy<ProviderModelOption> { it.modelProviderPrefixSortKey() }
            .thenBy { it.providerId }
            .thenBy { it.modelId }
    )
}

private fun ProviderModelOption.modelProviderPrefixSortKey(): String =
    modelId.substringBefore('/').trim().ifBlank { modelId }

fun AppSettings.withModelOption(option: ProviderModelOption): AppSettings = copy(
    piProviderId = option.piProviderId,
    providerConfigId = option.providerConfigId,
    providerAuthMethod = option.authMethod,
    apiKey = option.apiKey.trim(),
    oauthCredentialJson = option.oauthCredentialJson,
    providerEnvironmentVariables = option.providerEnvironmentVariables,
    baseUrl = option.baseUrl.trim(),
    modelId = option.modelId.trim(),
    customHeaders = option.customHeaders,
    providerUserAgentMode = option.userAgentMode,
    customProviderUserAgent = option.customUserAgent,
)

fun List<ProviderModelOption>.findModelOption(key: String?): ProviderModelOption? =
    firstOrNull { it.key == key }

fun List<ProviderModelOption>.resolveAutomaticModelKey(
    purpose: AutomaticModelPurpose,
): String {
    if (isEmpty()) return ""
    val rankedOption = mapNotNull { option ->
        automaticModelPriority(option.modelId, purpose)?.let { priority -> option to priority }
    }
        .minWithOrNull(compareBy<Pair<ProviderModelOption, Int>> { it.second }.thenBy { it.first.providerId }.thenBy { it.first.modelId })
        ?.first
    return rankedOption?.key ?: firstOrNull()?.key.orEmpty()
}

private fun automaticModelPriority(
    modelId: String,
    purpose: AutomaticModelPurpose,
): Int? {
    val normalized = modelId.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    val isGemini31Pro = normalized.contains("gemini31pro")
    val isGemini3Flash = Regex("gemini3(?:0)?flash").containsMatchIn(normalized) ||
        normalized.contains("gemini3flashpreview")
    val isGemini31FlashLite = normalized.contains("gemini31flashlite")

    return when (purpose) {
        AutomaticModelPurpose.Chat -> when {
            normalized.contains("gpt55") -> 0
            normalized.contains("gpt54") -> 1
            normalized.contains("claude") && normalized.contains("opus") && normalized.contains("47") -> 2
            normalized.contains("claude") && normalized.contains("sonnet") && normalized.contains("46") -> 3
            isGemini31Pro -> 4
            isGemini3Flash -> 5
            else -> null
        }

        AutomaticModelPurpose.Title,
        AutomaticModelPurpose.Naming,
        AutomaticModelPurpose.Compacting -> when {
            isGemini3Flash -> 0
            isGemini31FlashLite -> 1
            normalized.contains("gpt54mini") -> 2
            normalized.contains("claude") && normalized.contains("haiku") && normalized.contains("46") -> 3
            normalized.contains("gpt54") -> 4
            normalized.contains("claude") && normalized.contains("sonnet") -> 5
            else -> null
        }
    }
}
