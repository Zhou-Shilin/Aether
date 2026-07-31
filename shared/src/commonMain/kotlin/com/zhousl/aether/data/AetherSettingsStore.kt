package com.zhousl.aether.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

@Serializable
data class SharedThinkingCatalogCache(
    val levelsByProviderModel: Map<String, List<String>> = emptyMap(),
    val clampsByProviderModel: Map<String, Map<String, String>> = emptyMap(),
)

data class SharedPersistedSettings(
    val providerConfigs: List<LlmProviderConfig> = emptyList(),
    val activeProviderConfigId: String = "",
    val onboardingCompletedVersion: Int = 0,
    val appSettings: AppSettings = AppSettings(),
    val thinkingCatalogCache: SharedThinkingCatalogCache = SharedThinkingCatalogCache(),
) {
    val activeProviderConfig: LlmProviderConfig?
        get() = providerConfigs.firstOrNull { it.id == activeProviderConfigId && it.isEnabled }
            ?: providerConfigs.firstOrNull { it.isEnabled }
            ?: providerConfigs.firstOrNull()
}

class AetherSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): SharedPersistedSettings {
        val preferences = dataStore.data.first()
        val defaults = AppSettings()
        val legacySettings = defaults.copy(
            language = AppLanguage.fromStorage(preferences[Language]),
            themeMode = AppThemeMode.fromStorage(preferences[ThemeMode]),
            systemPrompt = preferences[SystemPrompt] ?: defaults.systemPrompt,
            reasoningEffort = normalizeReasoningEffort(preferences[ReasoningEffort]),
            tavilyApiKey = preferences[TavilyApiKey].orEmpty(),
            tavilyBaseUrl = normalizeTavilyBaseUrl(
                preferences[TavilyBaseUrl] ?: defaults.tavilyBaseUrl,
            ),
            onboardingCompletedVersion = preferences[OnboardingCompletedVersion] ?: 0,
        )
        val fullSettings = parseAppSettings(preferences[AppSettingsJson].orEmpty(), legacySettings)
        return SharedPersistedSettings(
            providerConfigs = parseProviderConfigs(preferences[ProviderConfigs].orEmpty()),
            activeProviderConfigId = preferences[ActiveProviderConfigId].orEmpty(),
            onboardingCompletedVersion = preferences[OnboardingCompletedVersion] ?: 0,
            appSettings = fullSettings.copy(
                onboardingCompletedVersion = preferences[OnboardingCompletedVersion] ?: 0,
            ),
            thinkingCatalogCache = parseSharedThinkingCatalogCache(
                preferences[ThinkingCatalogCacheJson].orEmpty(),
            ),
        )
    }

    suspend fun saveProvider(config: LlmProviderConfig) {
        dataStore.edit { preferences ->
            val current = parseProviderConfigs(preferences[ProviderConfigs].orEmpty())
            val updated = current.filterNot { it.id == config.id } + config
            preferences[ProviderConfigs] = serializeProviderConfigs(updated)
            preferences[ActiveProviderConfigId] = config.id
        }
    }

    suspend fun saveProviders(
        configs: List<LlmProviderConfig>,
        activeProviderConfigId: String,
    ) {
        dataStore.edit { preferences ->
            val normalized = configs.distinctBy(LlmProviderConfig::id)
            preferences[ProviderConfigs] = serializeProviderConfigs(normalized)
            preferences[ActiveProviderConfigId] = activeProviderConfigId
                .takeIf { id -> normalized.any { it.id == id } }
                ?: normalized.firstOrNull()?.id.orEmpty()
        }
    }

    suspend fun setActiveProvider(configId: String) {
        dataStore.edit { preferences ->
            val configs = parseProviderConfigs(preferences[ProviderConfigs].orEmpty())
            if (configs.any { it.id == configId && it.isEnabled }) {
                preferences[ActiveProviderConfigId] = configId
            }
        }
    }

    suspend fun deleteProvider(configId: String) {
        dataStore.edit { preferences ->
            val updated = parseProviderConfigs(preferences[ProviderConfigs].orEmpty())
                .filterNot { it.id == configId }
            preferences[ProviderConfigs] = serializeProviderConfigs(updated)
            if (preferences[ActiveProviderConfigId] == configId) {
                preferences[ActiveProviderConfigId] = updated.firstOrNull { it.isEnabled }?.id
                    ?: updated.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun saveGeneralSettings(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[AppSettingsJson] = serializeAppSettings(settings)
            preferences[Language] = settings.language.storageValue
            preferences[ThemeMode] = settings.themeMode.storageValue
            preferences[SystemPrompt] = settings.systemPrompt
            preferences[ReasoningEffort] = normalizeReasoningEffort(settings.reasoningEffort)
            preferences[TavilyApiKey] = settings.tavilyApiKey
            preferences[TavilyBaseUrl] = normalizeTavilyBaseUrl(settings.tavilyBaseUrl)
        }
    }

    suspend fun saveThinkingCatalogCache(cache: SharedThinkingCatalogCache) {
        dataStore.edit { preferences ->
            preferences[ThinkingCatalogCacheJson] = serializeSharedThinkingCatalogCache(cache)
        }
    }

    suspend fun markOnboardingComplete() {
        dataStore.edit { preferences ->
            preferences[OnboardingCompletedVersion] = CurrentOnboardingVersion
            val current = parseAppSettings(preferences[AppSettingsJson].orEmpty())
            preferences[AppSettingsJson] = serializeAppSettings(
                current.copy(
                    onboardingSeenVersion = CurrentOnboardingVersion,
                    onboardingCompletedVersion = CurrentOnboardingVersion,
                )
            )
        }
    }

    suspend fun markOnboardingSeen() {
        dataStore.edit { preferences ->
            val current = parseAppSettings(preferences[AppSettingsJson].orEmpty())
            preferences[AppSettingsJson] = serializeAppSettings(
                current.copy(onboardingSeenVersion = CurrentOnboardingVersion)
            )
        }
    }

    suspend fun acceptPrivacyPolicy() {
        dataStore.edit { preferences ->
            val current = parseAppSettings(preferences[AppSettingsJson].orEmpty())
            val updated = current.copy(privacyPolicyAccepted = true)
            preferences[AppSettingsJson] = serializeAppSettings(updated)
        }
    }

    suspend fun replaceAll(persisted: SharedPersistedSettings) {
        dataStore.edit { preferences ->
            preferences[ProviderConfigs] = serializeProviderConfigs(persisted.providerConfigs)
            preferences[ActiveProviderConfigId] = persisted.activeProviderConfigId
            preferences[OnboardingCompletedVersion] = persisted.onboardingCompletedVersion
            preferences[AppSettingsJson] = serializeAppSettings(persisted.appSettings)
            preferences[Language] = persisted.appSettings.language.storageValue
            preferences[ThemeMode] = persisted.appSettings.themeMode.storageValue
            preferences[SystemPrompt] = persisted.appSettings.systemPrompt
            preferences[ReasoningEffort] = normalizeReasoningEffort(persisted.appSettings.reasoningEffort)
            preferences[TavilyApiKey] = persisted.appSettings.tavilyApiKey
            preferences[TavilyBaseUrl] = normalizeTavilyBaseUrl(persisted.appSettings.tavilyBaseUrl)
        }
    }

    private companion object {
        val ProviderConfigs = stringPreferencesKey("provider_configs")
        val ActiveProviderConfigId = stringPreferencesKey("provider_config_id")
        val OnboardingCompletedVersion = intPreferencesKey("onboarding_completed_version")
        val Language = stringPreferencesKey("language")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val SystemPrompt = stringPreferencesKey("system_prompt")
        val ReasoningEffort = stringPreferencesKey("reasoning_effort")
        val TavilyApiKey = stringPreferencesKey("tavily_api_key")
        val TavilyBaseUrl = stringPreferencesKey("tavily_base_url")
        val AppSettingsJson = stringPreferencesKey("app_settings_json")
        val ThinkingCatalogCacheJson = stringPreferencesKey("thinking_catalog_cache_json")
    }
}

private val SharedThinkingCatalogCacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun parseSharedThinkingCatalogCache(value: String): SharedThinkingCatalogCache =
    value.takeIf(String::isNotBlank)
        ?.let { runCatching { SharedThinkingCatalogCacheJson.decodeFromString<SharedThinkingCatalogCache>(it) }.getOrNull() }
        ?: SharedThinkingCatalogCache()

internal fun serializeSharedThinkingCatalogCache(cache: SharedThinkingCatalogCache): String =
    SharedThinkingCatalogCacheJson.encodeToString(cache)

fun createAetherSettingsStore(path: String): AetherSettingsStore = AetherSettingsStore(
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { path.toPath() },
    )
)
