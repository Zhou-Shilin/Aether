package com.zhousl.aether.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
data class PackageProfileState(
    val profileId: String,
    val installed: Boolean = false,
    val installedAtMillis: Long = 0L,
    val lastError: String = "",
)

@Serializable
data class AlpineEnvironmentVariable(
    val name: String,
    val value: String,
)

@Serializable
data class AppSettings(
    val piProviderId: String = DefaultPiProviderId,
    val providerConfigId: String = "",
    val providerAuthMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    val apiKey: String = "",
    val oauthCredentialJson: String = "",
    val providerEnvironmentVariables: List<PiProviderEnvironmentVariable> = emptyList(),
    val baseUrl: String = DefaultCustomProviderBaseUrl,
    val modelId: String = DefaultCustomModelId,
    val userAgent: String = AetherLlmUserAgent,
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val reasoningEffort: String = DefaultReasoningEffort,
    val systemPrompt: String = platformDefaultSystemPrompt(),
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
    val defaultSelectedSkillIds: List<String> = emptyList(),
    val onboardingSeenVersion: Int = 0,
    val onboardingCompletedVersion: Int = 0,
    val privacyPolicyAccepted: Boolean = false,
    val lastUpdateCheckAtMillis: Long = 0L,
)

@Serializable
data class LlmCustomHeader(
    val name: String,
    val value: String,
)

@Serializable
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
    value?.trim()?.lowercase()
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
const val DefaultTavilyBaseUrl = "https://api.tavily.com/"

private val AppSettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun serializeAppSettings(settings: AppSettings): String = AppSettingsJson.encodeToString(settings)

fun parseAppSettings(value: String, fallback: AppSettings = AppSettings()): AppSettings =
    if (value.isBlank()) fallback else runCatching {
        AppSettingsJson.decodeFromString<AppSettings>(value)
    }.getOrDefault(fallback)

fun defaultAppLanguage(): AppLanguage = if (
    platformLanguageTag().startsWith("zh", ignoreCase = true)
) {
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

private fun isProviderSetupValid(
    piProviderId: String,
    authMethod: ProviderAuthMethod,
    apiKey: String,
    baseUrl: String,
    oauthCredentialJson: String,
): Boolean {
    val definition = PiProviderCatalog.resolve(piProviderId)
    if ((definition.requiresBaseUrl || !definition.isBuiltIn) && baseUrl.trim().isEmpty()) return false
    return when (authMethod) {
        ProviderAuthMethod.ApiKey ->
            !definition.isBuiltIn ||
                (definition.supportsApiKey && apiKey.isNotBlank())

        ProviderAuthMethod.OAuth ->
            definition.supportsOAuth && oauthCredentialJson.isNotBlank()

        ProviderAuthMethod.Ambient ->
            definition.supportsAmbientAuth
    }
}

fun AppSettings.isProviderSetupValid(): Boolean = isProviderSetupValid(
    piProviderId = piProviderId,
    authMethod = providerAuthMethod,
    apiKey = apiKey,
    baseUrl = baseUrl,
    oauthCredentialJson = oauthCredentialJson,
)

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

data class LlmProviderConfig(
    val id: String = platformRandomUuid(),
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
    val userAgent: String = AetherLlmUserAgent,
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val cachedModels: List<String> = emptyList(),
    val enabledModelIds: List<String> = cachedModels + manualModelIds,
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = platformCurrentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

fun LlmProviderConfig.isSharedProviderSetupValid(): Boolean = isProviderSetupValid(
    piProviderId = piProviderId,
    authMethod = authMethod,
    apiKey = apiKey,
    baseUrl = baseUrl,
    oauthCredentialJson = oauthCredentialJson,
)

fun LlmProviderConfig.toJsonObject(): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(id),
        "providerId" to JsonPrimitive(providerId),
        "name" to JsonPrimitive(name),
        "piProviderId" to JsonPrimitive(piProviderId),
        "authMethod" to JsonPrimitive(authMethod.storageValue),
        "apiKey" to JsonPrimitive(apiKey),
        "oauthCredentialJson" to JsonPrimitive(oauthCredentialJson),
        "providerEnvironmentVariables" to providerEnvironmentVariables.toEnvironmentJsonArray(),
        "baseUrl" to JsonPrimitive(baseUrl),
        "modelId" to JsonPrimitive(modelId),
        "userAgent" to JsonPrimitive(normalizeLlmUserAgent(userAgent)),
        "manualModelIds" to manualModelIds.toStringJsonArray(),
        "customHeaders" to customHeaders.toKotlinJsonArray(),
        "cachedModels" to cachedModels.toStringJsonArray(),
        "enabledModelIds" to enabledModelIds.toStringJsonArray(),
        "isEnabled" to JsonPrimitive(isEnabled),
        "createdAtMillis" to JsonPrimitive(createdAtMillis),
        "updatedAtMillis" to JsonPrimitive(updatedAtMillis),
    )
)

fun parseProviderConfigs(rawValue: String): List<LlmProviderConfig> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = Json.parseToJsonElement(rawValue).jsonArray
        buildList {
            array.forEachIndexed { index, element ->
                val json = element as? JsonObject ?: return@forEachIndexed
                val storedPiProviderId = json.string("piProviderId").trim()
                val storedBaseUrl = json.string("baseUrl").trim()
                val piProviderId = storedPiProviderId.ifBlank {
                    inferLegacyPiProviderId(json.string("providerType"), storedBaseUrl)
                }
                val providerDefinition = PiProviderCatalog.resolve(piProviderId)
                val providerName = json.string("name").trim()
                    .ifBlank { providerDefinition.displayName }
                val baseUrl = storedBaseUrl.ifBlank { providerDefinition.defaultBaseUrl }
                val modelId = if ("modelId" in json) {
                    json.string("modelId").trim()
                } else {
                    providerDefinition.defaultModelId
                }
                val enabledModelIds = json.array("enabledModelIds").toStringListSafe()
                val manualModelIds = if ("manualModelIds" in json) {
                    json.array("manualModelIds").toStringListSafe()
                } else {
                    listOf(modelId).filter(String::isNotBlank)
                }
                val cachedModels = normalizeStringList(
                    buildList {
                        addAll(json.array("cachedModels").toStringListSafe())
                        if ("manualModelIds" !in json) {
                            removeAll(manualModelIds)
                        }
                    }
                )
                val availableModels = normalizeStringList(cachedModels + manualModelIds)
                val parsedCustomHeaders = parseCustomHeaders(json.array("customHeaders"))
                val userAgent = if ("userAgent" in json) {
                    normalizeLlmUserAgent(json.string("userAgent"))
                } else {
                    normalizeLlmUserAgent(
                        parsedCustomHeaders.firstOrNull {
                            it.name.equals("User-Agent", ignoreCase = true)
                        }?.value
                    )
                }
                val inferredProviderId = providerName
                    .sanitizeProviderId()
                    .ifBlank { "${providerDefinition.id.sanitizeProviderId()}_${index + 1}" }
                add(
                    LlmProviderConfig(
                        id = json.string("id").trim().ifBlank { platformRandomUuid() },
                        providerId = json.string("providerId").trim().ifBlank { inferredProviderId },
                        name = providerName,
                        piProviderId = piProviderId,
                        authMethod = ProviderAuthMethod.fromStorage(
                            json.string("authMethod"),
                            providerDefinition.defaultAuthMethod(),
                        ),
                        apiKey = json.string("apiKey"),
                        oauthCredentialJson = json.string("oauthCredentialJson"),
                        providerEnvironmentVariables = parseProviderEnvironmentVariables(
                            json.array("providerEnvironmentVariables"),
                        ),
                        baseUrl = baseUrl,
                        modelId = modelId,
                        manualModelIds = manualModelIds,
                        userAgent = userAgent,
                        customHeaders = parsedCustomHeaders.filterNot {
                            it.name.equals("User-Agent", ignoreCase = true)
                        },
                        cachedModels = cachedModels,
                        enabledModelIds = if ("enabledModelIds" in json) {
                            normalizeStringList(enabledModelIds.filter(availableModels::contains))
                        } else {
                            availableModels
                        },
                        isEnabled = if ("isEnabled" in json) {
                            json.boolean("isEnabled", true)
                        } else {
                            true
                        },
                        createdAtMillis = json.long("createdAtMillis", platformCurrentTimeMillis()),
                        updatedAtMillis = json.long("updatedAtMillis", platformCurrentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun serializeProviderConfigs(configs: List<LlmProviderConfig>): String =
    JsonArray(configs.map(LlmProviderConfig::toJsonObject)).toString()

fun List<LlmCustomHeader>.toKotlinJsonArray(): JsonArray = JsonArray(map { header ->
    JsonObject(mapOf("name" to JsonPrimitive(header.name), "value" to JsonPrimitive(header.value)))
})

private fun List<PiProviderEnvironmentVariable>.toEnvironmentJsonArray(): JsonArray = JsonArray(map { variable ->
    JsonObject(mapOf("name" to JsonPrimitive(variable.name), "value" to JsonPrimitive(variable.value)))
})

private fun List<String>.toStringJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

fun parseCustomHeaders(array: JsonArray?): List<LlmCustomHeader> {
    if (array == null) return emptyList()
    return buildList {
        array.forEach { element ->
            val json = element as? JsonObject ?: return@forEach
            val name = json.string("name").trim()
            if (name.isBlank()) return@forEach
            add(
                LlmCustomHeader(
                    name = name,
                    value = json.string("value"),
                )
            )
        }
    }.distinctBy { it.name.lowercase() }
}

fun parseProviderEnvironmentVariables(
    array: JsonArray?,
): List<PiProviderEnvironmentVariable> {
    if (array == null) return emptyList()
    return buildList {
        array.forEach { element ->
            val json = element as? JsonObject ?: return@forEach
            val name = json.string("name").trim()
            if (name.isBlank()) return@forEach
            add(
                PiProviderEnvironmentVariable(
                    name = name,
                    value = json.string("value"),
                )
            )
        }
    }.distinctBy { it.name.uppercase() }
}

private fun JsonArray?.toStringListSafe(): List<String> {
    if (this == null) return emptyList()
    return normalizeStringList(
        buildList {
            this@toStringListSafe.forEach { element ->
                val value = element.jsonPrimitive.contentOrNull.orEmpty().trim()
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
    lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')

fun buildModelOptionKey(
    providerConfigId: String,
    modelId: String,
): String = "$providerConfigId::$modelId"

fun LlmProviderConfig.availableModels(): List<String> = normalizeStringList(cachedModels + manualModelIds)

fun LlmProviderConfig.enabledModels(): List<String> {
    val availableModels = availableModels().toHashSet()
    return normalizeStringList(enabledModelIds.filter(availableModels::contains))
}

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
    val userAgent: String,
    val customHeaders: List<LlmCustomHeader>,
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
    val modelsByConfig = scopedConfigs.map { config ->
        config to if (includeDisabledModels) config.availableModels() else config.enabledModels()
    }
    val modelCounts = modelsByConfig
        .flatMap { (config, models) -> models.map { modelId -> modelId to config.id } }
        .groupingBy { it.first }
        .eachCount()

    return modelsByConfig.flatMap { (config, models) ->
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
                userAgent = normalizeLlmUserAgent(config.userAgent),
                customHeaders = config.customHeaders,
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
    userAgent = normalizeLlmUserAgent(option.userAgent),
    customHeaders = option.customHeaders,
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

fun List<ProviderModelOption>.sortedForAutomaticModelPurpose(
    purpose: AutomaticModelPurpose,
): List<ProviderModelOption> = sortedWith(
    compareBy<ProviderModelOption> {
        automaticModelPriority(it.modelId, purpose) ?: Int.MAX_VALUE
    }
        .thenBy { it.providerId }
        .thenBy { it.modelId }
)

fun automaticModelPriority(
    modelId: String,
    purpose: AutomaticModelPurpose,
): Int? {
    val normalized = modelId.lowercase().replace(Regex("[^a-z0-9]+"), "")

    return when (purpose) {
        AutomaticModelPurpose.Chat -> when {
            normalized.contains("claude") &&
                (normalized.contains("fable5") || normalized.contains("5fable")) -> 0
            normalized.contains("gpt56") && normalized.contains("sol") -> 1
            normalized.contains("gpt56") && normalized.contains("terra") -> 2
            normalized.contains("claude") && normalized.contains("opus") && normalized.contains("48") -> 3
            normalized.contains("claude") &&
                (normalized.contains("sonnet5") || normalized.contains("5sonnet")) -> 4
            normalized.contains("gemini35flash") -> 5
            normalized.contains("grok45") -> 6
            normalized.contains("gemini31pro") -> 7
            normalized.contains("kimik3") -> 8
            normalized.contains("deepseek") && normalized.contains("v4") &&
                normalized.contains("pro") -> 9
            normalized.contains("deepseek") && normalized.contains("v4") &&
                normalized.contains("flash") -> 10
            normalized.contains("glm52") -> 11
            normalized.contains("musespark11") -> 12
            else -> null
        }

        AutomaticModelPurpose.Title,
        AutomaticModelPurpose.Naming -> when {
            normalized.contains("gemini31flashlite") -> 0
            normalized.contains("gpt56luna") -> 1
            normalized.contains("gpt54mini") -> 2
            normalized.contains("claude45haiku") ||
                (normalized.contains("claude") && normalized.contains("haiku45")) -> 3
            normalized.contains("mini") ||
                normalized.contains("haiku") ||
                normalized.contains("lite") -> 4
            else -> null
        }

        AutomaticModelPurpose.Compacting -> when {
            normalized.contains("gemini35flash") -> 0
            normalized.contains("gpt56luna") -> 1
            normalized.contains("claude45haiku") ||
                (normalized.contains("claude") && normalized.contains("haiku45")) -> 2
            normalized.contains("gemini31flashlite") -> 3
            else -> null
        }
    }
}

private fun JsonObject.string(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.array(name: String): JsonArray? =
    this[name] as? JsonArray

private fun JsonObject.boolean(name: String, defaultValue: Boolean): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull ?: defaultValue

private fun JsonObject.long(name: String, defaultValue: Long): Long =
    this[name]?.jsonPrimitive?.longOrNull ?: defaultValue
