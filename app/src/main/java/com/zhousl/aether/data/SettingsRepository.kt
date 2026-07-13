package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "aether_settings")

class SettingsRepository(
    private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val defaults = AppSettings()
        val storedWorkspaceMode = AgentWorkspaceMode.fromStorage(preferences[AGENT_WORKSPACE_MODE])
        val storedBaseUrl = preferences[BASE_URL] ?: defaults.baseUrl
        val storedPiProviderId = preferences[PI_PROVIDER_ID]
            ?.takeIf(String::isNotBlank)
            ?: inferLegacyPiProviderId(preferences[PROVIDER], storedBaseUrl)
        val storedUserAgent = normalizeProviderUserAgent(
            piProviderId = storedPiProviderId,
            baseUrl = storedBaseUrl,
            mode = ProviderUserAgentMode.fromStorage(preferences[PROVIDER_USER_AGENT_MODE]),
            customValue = preferences[CUSTOM_PROVIDER_USER_AGENT],
            headers = emptyList(),
        )
        AppSettings(
            piProviderId = storedPiProviderId,
            providerConfigId = preferences[PROVIDER_CONFIG_ID].orEmpty(),
            providerAuthMethod = ProviderAuthMethod.fromStorage(preferences[PROVIDER_AUTH_METHOD]),
            apiKey = preferences[API_KEY].orEmpty(),
            oauthCredentialJson = preferences[OAUTH_CREDENTIAL_JSON].orEmpty(),
            providerEnvironmentVariables = parseProviderEnvironmentVariables(
                preferences[PROVIDER_ENVIRONMENT_VARIABLES]
                    ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() },
            ),
            baseUrl = storedBaseUrl,
            modelId = preferences[MODEL_ID] ?: defaults.modelId,
            providerUserAgentMode = storedUserAgent.mode,
            customProviderUserAgent = storedUserAgent.customValue,
            reasoningEffort = normalizeReasoningEffort(preferences[REASONING_EFFORT]),
            systemPrompt = preferences[SYSTEM_PROMPT] ?: defaults.systemPrompt,
            tavilyApiKey = preferences[TAVILY_API_KEY].orEmpty(),
            tavilyBaseUrl = normalizeTavilyBaseUrl(preferences[TAVILY_BASE_URL] ?: defaults.tavilyBaseUrl),
            llmInactivityReconnectTimeoutSeconds = normalizeLlmInactivityReconnectTimeoutSeconds(
                preferences[LLM_INACTIVITY_RECONNECT_TIMEOUT_SECONDS]
            ),
            keepTasksRunningInBackground = preferences[KEEP_TASKS_RUNNING_IN_BACKGROUND] ?: true,
            notifyOnTaskCompletion = preferences[NOTIFY_ON_TASK_COMPLETION] ?: true,
            agentWorkspaceMode = if (preferences[WORKSPACE_MODE_INITIALIZED] == true) {
                storedWorkspaceMode
            } else {
                defaults.agentWorkspaceMode
            },
            autoCleanOldCommandHistory =
                preferences[AUTO_CLEAN_OLD_COMMAND_HISTORY] ?: true,
            oldCommandHistoryRetentionHours = normalizeOldCommandHistoryRetentionHours(
                preferences[OLD_COMMAND_HISTORY_RETENTION_HOURS],
            ),
            termuxSetupCompleted = preferences[TERMUX_SETUP_COMPLETED] ?: false,
            termuxSetupNoticeDismissed = preferences[TERMUX_SETUP_NOTICE_DISMISSED] ?: false,
            termuxLiveOutputEnabled = preferences[TERMUX_LIVE_OUTPUT_ENABLED] ?: true,
            termuxEnvironmentVariables = parseTermuxEnvironmentVariables(
                preferences[TERMUX_ENVIRONMENT_VARIABLES].orEmpty()
            ),
            enabledRuntimeIds = resolveEnabledRuntimeIds(
                rawValue = preferences[ENABLED_RUNTIME_IDS],
                termuxSetupCompleted = preferences[TERMUX_SETUP_COMPLETED] ?: false,
                alpineSetupCompleted = preferences[ALPINE_SETUP_COMPLETED] ?: false,
            ),
            defaultRuntimeId = resolveDefaultRuntimeId(
                rawValue = preferences[DEFAULT_RUNTIME_ID],
                enabledRuntimeIds = resolveEnabledRuntimeIds(
                    rawValue = preferences[ENABLED_RUNTIME_IDS],
                    termuxSetupCompleted = preferences[TERMUX_SETUP_COMPLETED] ?: false,
                    alpineSetupCompleted = preferences[ALPINE_SETUP_COMPLETED] ?: false,
                ),
                termuxSetupCompleted = preferences[TERMUX_SETUP_COMPLETED] ?: false,
                alpineSetupCompleted = preferences[ALPINE_SETUP_COMPLETED] ?: false,
            ),
            alpineSetupCompleted = preferences[ALPINE_SETUP_COMPLETED] ?: false,
            alpinePackageProfiles = parsePackageProfileStates(
                preferences[ALPINE_PACKAGE_PROFILES].orEmpty()
            ),
            alpineEnvironmentVariables = parseAlpineEnvironmentVariables(
                preferences[ALPINE_ENVIRONMENT_VARIABLES].orEmpty()
            ),
            agentModeAuthorizationEnabled = preferences[AGENT_MODE_AUTHORIZATION_ENABLED] ?: false,
            agentModeAuthorizationMethod = AgentModeAuthorizationMethod.fromStorage(
                preferences[AGENT_MODE_AUTHORIZATION_METHOD],
                defaultValue = defaultAgentModeAuthorizationMethod(context),
            ),
            language = AppLanguage.fromStorage(preferences[LANGUAGE]),
            themeMode = AppThemeMode.fromStorage(preferences[THEME_MODE]),
            defaultChatModelKey = preferences[DEFAULT_CHAT_MODEL_KEY].orEmpty(),
            defaultTitleModelKey = preferences[DEFAULT_TITLE_MODEL_KEY].orEmpty(),
            defaultNamingModelKey = preferences[DEFAULT_NAMING_MODEL_KEY].orEmpty(),
            defaultCompactingModelKey = preferences[DEFAULT_COMPACTING_MODEL_KEY].orEmpty(),
            onboardingSeenVersion = preferences[ONBOARDING_SEEN_VERSION] ?: 0,
            onboardingCompletedVersion = preferences[ONBOARDING_COMPLETED_VERSION] ?: 0,
            privacyPolicyAccepted = preferences[PRIVACY_POLICY_ACCEPTED] ?: false,
            lastUpdateCheckAtMillis = preferences[LAST_UPDATE_CHECK_AT_MILLIS] ?: 0L,
        )
    }

    // ── Multi-Provider support ───────────────────────────────────────────────
    val providerConfigs: Flow<List<LlmProviderConfig>> = context.dataStore.data.map { preferences ->
        parseProviderConfigs(preferences[PROVIDER_CONFIGS].orEmpty())
    }

    suspend fun migrateLegacyProvidersToPi() {
        context.dataStore.edit { prefs ->
            val rawConfigs = prefs[PROVIDER_CONFIGS].orEmpty()
            val parsedConfigs = parseProviderConfigs(rawConfigs).toMutableList()
            val requiresConfigMigration = runCatching {
                val array = JSONArray(rawConfigs)
                (0 until array.length()).any { index ->
                    val item = array.optJSONObject(index)
                    item != null && (
                        item.optString("piProviderId").isBlank() ||
                            item.optString("authMethod").isBlank() ||
                            item.has("providerType") ||
                            item.has("basicFunctionCallingCompatibilityMode")
                        )
                }
            }.getOrDefault(false)
            if (
                !requiresConfigMigration &&
                prefs[PI_PROVIDER_ID]?.isNotBlank() == true &&
                prefs[PROVIDER_AUTH_METHOD]?.isNotBlank() == true &&
                prefs[PROVIDER] == null
            ) {
                return@edit
            }
            val legacyBaseUrl = prefs[BASE_URL] ?: AppSettings().baseUrl
            val legacyApiKey = prefs[API_KEY].orEmpty()
            val legacyModelId = prefs[MODEL_ID] ?: AppSettings().modelId
            val migratedPiProviderId = prefs[PI_PROVIDER_ID]
                ?.takeIf(String::isNotBlank)
                ?: inferLegacyPiProviderId(prefs[PROVIDER], legacyBaseUrl)
            val definition = PiProviderCatalog.resolve(migratedPiProviderId)
            var matchingConfig = prefs[PROVIDER_CONFIG_ID]
                ?.let { currentId -> parsedConfigs.firstOrNull { it.id == currentId } }
                ?: parsedConfigs.firstOrNull { config ->
                    config.piProviderId == definition.id &&
                        config.baseUrl.trim() == legacyBaseUrl.trim() &&
                        config.apiKey.trim() == legacyApiKey.trim() &&
                        config.availableModels().contains(legacyModelId.trim())
                }

            val hasLegacySingleProvider = prefs[PROVIDER] != null ||
                prefs[API_KEY]?.isNotBlank() == true ||
                prefs[BASE_URL]?.isNotBlank() == true ||
                prefs[MODEL_ID]?.isNotBlank() == true
            if (matchingConfig == null && hasLegacySingleProvider) {
                val baseProviderId = definition.id.sanitizeProviderId()
                    .ifBlank { "provider" }
                val migratedProviderId = generateSequence(baseProviderId) { current ->
                    val suffix = current.substringAfterLast('_').toIntOrNull()
                    "${baseProviderId}_${(suffix ?: 1) + 1}"
                }.first { candidate -> parsedConfigs.none { it.providerId == candidate } }
                matchingConfig = LlmProviderConfig(
                    id = UUID.randomUUID().toString(),
                    providerId = migratedProviderId,
                    name = definition.displayName,
                    piProviderId = definition.id,
                    authMethod = definition.defaultAuthMethod(),
                    apiKey = legacyApiKey,
                    baseUrl = legacyBaseUrl.ifBlank { definition.defaultBaseUrl },
                    modelId = legacyModelId.ifBlank { definition.defaultModelId },
                    manualModelIds = listOf(
                        legacyModelId.ifBlank { definition.defaultModelId },
                    ).filter(String::isNotBlank),
                    enabledModelIds = listOf(
                        legacyModelId.ifBlank { definition.defaultModelId },
                    ).filter(String::isNotBlank),
                    userAgentMode = defaultProviderUserAgentMode(
                        piProviderId = definition.id,
                        baseUrl = legacyBaseUrl.ifBlank { definition.defaultBaseUrl },
                    ),
                )
                parsedConfigs += matchingConfig
            }

            if (requiresConfigMigration || matchingConfig != null) {
                prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(parsedConfigs)
            }
            prefs[PI_PROVIDER_ID] = definition.id
            prefs[PROVIDER_AUTH_METHOD] = (
                matchingConfig?.authMethod ?: definition.defaultAuthMethod()
                ).storageValue
            matchingConfig?.let { config ->
                prefs[PROVIDER_CONFIG_ID] = config.id
                prefs[API_KEY] = config.apiKey
                prefs[OAUTH_CREDENTIAL_JSON] = config.oauthCredentialJson
                prefs[PROVIDER_ENVIRONMENT_VARIABLES] =
                    serializeProviderEnvironmentVariables(
                        config.providerEnvironmentVariables,
                    )
                prefs[BASE_URL] = config.baseUrl
                prefs[MODEL_ID] = legacyModelId.ifBlank { config.modelId }
                prefs.writeProviderUserAgent(config.userAgentMode, config.customUserAgent)
            }
            prefs.remove(PROVIDER)
            prefs.remove(BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE)
            prefs.remove(UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS)
        }
    }

    suspend fun upsertProviderConfig(config: LlmProviderConfig) {
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty()).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == config.id }
            val updatedConfig = config.copy(updatedAtMillis = System.currentTimeMillis())
            if (existingIndex >= 0) {
                current[existingIndex] = updatedConfig
            } else {
                current.add(updatedConfig)
            }
            prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(current)
        }
    }

    suspend fun removeProviderConfig(id: String) {
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty())
            val updated = current.filter { it.id != id }
            prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(updated)
        }
    }

    suspend fun updateProviderOAuthCredential(
        id: String,
        credentialJson: String,
    ) {
        if (id.isBlank() || credentialJson.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty())
            val updated = current.map { config ->
                if (config.id == id) {
                    config.copy(
                        oauthCredentialJson = credentialJson,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                } else {
                    config
                }
            }
            prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(updated)
            if (prefs[PROVIDER_CONFIG_ID] == id) {
                prefs[OAUTH_CREDENTIAL_JSON] = credentialJson
            }
        }
    }

    suspend fun setProviderEnabled(
        id: String,
        enabled: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty())
            val currentPiProviderId = prefs[PI_PROVIDER_ID]
                ?.takeIf(String::isNotBlank)
                ?: inferLegacyPiProviderId(
                    prefs[PROVIDER],
                    prefs[BASE_URL] ?: AppSettings().baseUrl,
                )
            val currentProviderConfigId = prefs[PROVIDER_CONFIG_ID].orEmpty()
            val currentApiKey = prefs[API_KEY].orEmpty()
            val currentBaseUrl = prefs[BASE_URL] ?: AppSettings().baseUrl
            val currentModelId = prefs[MODEL_ID] ?: AppSettings().modelId
            val toggledConfigWasCurrent = current
                .firstOrNull { it.id == id }
                ?.matchesStoredModel(
                    piProviderId = currentPiProviderId,
                    providerConfigId = currentProviderConfigId,
                    apiKey = currentApiKey,
                    baseUrl = currentBaseUrl,
                    modelId = currentModelId,
                ) == true
            val updated = current.map { config ->
                if (config.id == id) config.copy(isEnabled = enabled) else config
            }
            prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(updated)

            val availableOptions = updated.availableModelOptions()
            val currentStillAvailable = availableOptions.any {
                it.matchesStoredModel(
                    piProviderId = currentPiProviderId,
                    providerConfigId = currentProviderConfigId,
                    apiKey = currentApiKey,
                    baseUrl = currentBaseUrl,
                    modelId = currentModelId,
                )
            }
            val fallbackOption = availableOptions.firstOrNull()
            if (!enabled && toggledConfigWasCurrent && !currentStillAvailable && fallbackOption != null) {
                prefs[PI_PROVIDER_ID] = fallbackOption.piProviderId
                prefs[PROVIDER_CONFIG_ID] = fallbackOption.providerConfigId
                prefs[PROVIDER_AUTH_METHOD] = fallbackOption.authMethod.storageValue
                prefs[API_KEY] = fallbackOption.apiKey
                prefs[OAUTH_CREDENTIAL_JSON] = fallbackOption.oauthCredentialJson
                prefs[PROVIDER_ENVIRONMENT_VARIABLES] = serializeProviderEnvironmentVariables(
                    fallbackOption.providerEnvironmentVariables,
                )
                prefs[BASE_URL] = fallbackOption.baseUrl
                prefs[MODEL_ID] = fallbackOption.modelId
                prefs.writeProviderUserAgent(
                    fallbackOption.userAgentMode,
                    fallbackOption.customUserAgent,
                )
            }
        }
    }

    suspend fun replaceImportedSettings(
        settings: AppSettings,
        providerConfigs: List<LlmProviderConfig>,
    ) {
        context.dataStore.edit {
            it[PI_PROVIDER_ID] = settings.piProviderId
            it[PROVIDER_CONFIG_ID] = settings.providerConfigId
            it[PROVIDER_AUTH_METHOD] = settings.providerAuthMethod.storageValue
            it[API_KEY] = settings.apiKey
            it[OAUTH_CREDENTIAL_JSON] = settings.oauthCredentialJson
            it[PROVIDER_ENVIRONMENT_VARIABLES] = serializeProviderEnvironmentVariables(
                settings.providerEnvironmentVariables,
            )
            it[BASE_URL] = settings.baseUrl
            it[MODEL_ID] = settings.modelId
            it.writeProviderUserAgent(
                settings.providerUserAgentMode,
                settings.customProviderUserAgent,
            )
            it[REASONING_EFFORT] = normalizeReasoningEffort(settings.reasoningEffort)
            it[SYSTEM_PROMPT] = settings.systemPrompt
            it[TAVILY_API_KEY] = settings.tavilyApiKey
            it[TAVILY_BASE_URL] = normalizeTavilyBaseUrl(settings.tavilyBaseUrl)
            it[LLM_INACTIVITY_RECONNECT_TIMEOUT_SECONDS] =
                normalizeLlmInactivityReconnectTimeoutSeconds(
                    settings.llmInactivityReconnectTimeoutSeconds
                )
            it[KEEP_TASKS_RUNNING_IN_BACKGROUND] = settings.keepTasksRunningInBackground
            it[NOTIFY_ON_TASK_COMPLETION] = settings.notifyOnTaskCompletion
            it[AGENT_WORKSPACE_MODE] = settings.agentWorkspaceMode.storageValue
            it[WORKSPACE_MODE_INITIALIZED] = true
            it[AUTO_CLEAN_OLD_COMMAND_HISTORY] = settings.autoCleanOldCommandHistory
            it[OLD_COMMAND_HISTORY_RETENTION_HOURS] =
                normalizeOldCommandHistoryRetentionHours(settings.oldCommandHistoryRetentionHours)
            it[TERMUX_SETUP_COMPLETED] = settings.termuxSetupCompleted
            it[TERMUX_SETUP_NOTICE_DISMISSED] = settings.termuxSetupNoticeDismissed
            it[TERMUX_LIVE_OUTPUT_ENABLED] = settings.termuxLiveOutputEnabled
            it[TERMUX_ENVIRONMENT_VARIABLES] =
                serializeTermuxEnvironmentVariables(settings.termuxEnvironmentVariables)
            it[ENABLED_RUNTIME_IDS] = serializeRuntimeIds(settings.enabledRuntimeIds)
            settings.defaultRuntimeId?.let { runtimeId ->
                it[DEFAULT_RUNTIME_ID] = runtimeId.storageValue
            } ?: it.remove(DEFAULT_RUNTIME_ID)
            it[ALPINE_SETUP_COMPLETED] = settings.alpineSetupCompleted
            it[ALPINE_PACKAGE_PROFILES] = serializePackageProfileStates(settings.alpinePackageProfiles)
            it[ALPINE_ENVIRONMENT_VARIABLES] =
                serializeAlpineEnvironmentVariables(settings.alpineEnvironmentVariables)
            it[AGENT_MODE_AUTHORIZATION_ENABLED] = settings.agentModeAuthorizationEnabled
            it[AGENT_MODE_AUTHORIZATION_METHOD] = settings.agentModeAuthorizationMethod.storageValue
            it[LANGUAGE] = settings.language.storageValue
            it[THEME_MODE] = settings.themeMode.storageValue
            it[DEFAULT_CHAT_MODEL_KEY] = settings.defaultChatModelKey
            it[DEFAULT_TITLE_MODEL_KEY] = settings.defaultTitleModelKey
            it[DEFAULT_NAMING_MODEL_KEY] = settings.defaultNamingModelKey
            it[DEFAULT_COMPACTING_MODEL_KEY] = settings.defaultCompactingModelKey
            it.remove(PROVIDER)
            it.remove(BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE)
            it.remove(UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS)
            it[ONBOARDING_SEEN_VERSION] = settings.onboardingSeenVersion
            it[ONBOARDING_COMPLETED_VERSION] = settings.onboardingCompletedVersion
            it[PRIVACY_POLICY_ACCEPTED] = settings.privacyPolicyAccepted
            it[LAST_UPDATE_CHECK_AT_MILLIS] = settings.lastUpdateCheckAtMillis
            it[PROVIDER_CONFIGS] = serializeProviderConfigs(providerConfigs)
        }
    }

    suspend fun updateApiKey(value: String) {
        context.dataStore.edit { it[API_KEY] = value }
    }

    suspend fun updateBaseUrl(value: String) {
        context.dataStore.edit { it[BASE_URL] = value }
    }

    suspend fun updateModelId(value: String) {
        context.dataStore.edit { it[MODEL_ID] = value }
    }

    suspend fun updateSystemPrompt(value: String) {
        context.dataStore.edit { it[SYSTEM_PROMPT] = value }
    }

    suspend fun updateTavilyApiKey(value: String) {
        context.dataStore.edit { it[TAVILY_API_KEY] = value }
    }

    suspend fun updateTavilyBaseUrl(value: String) {
        context.dataStore.edit { it[TAVILY_BASE_URL] = normalizeTavilyBaseUrl(value) }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = language.storageValue }
    }

    suspend fun updateThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = themeMode.storageValue }
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit {
            it[PI_PROVIDER_ID] = settings.piProviderId
            it[PROVIDER_CONFIG_ID] = settings.providerConfigId
            it[PROVIDER_AUTH_METHOD] = settings.providerAuthMethod.storageValue
            it[API_KEY] = settings.apiKey
            it[OAUTH_CREDENTIAL_JSON] = settings.oauthCredentialJson
            it[PROVIDER_ENVIRONMENT_VARIABLES] = serializeProviderEnvironmentVariables(
                settings.providerEnvironmentVariables,
            )
            it[BASE_URL] = settings.baseUrl
            it[MODEL_ID] = settings.modelId
            it.writeProviderUserAgent(
                settings.providerUserAgentMode,
                settings.customProviderUserAgent,
            )
            it[REASONING_EFFORT] = normalizeReasoningEffort(settings.reasoningEffort)
            it[SYSTEM_PROMPT] = settings.systemPrompt
            it[TAVILY_API_KEY] = settings.tavilyApiKey
            it[TAVILY_BASE_URL] = normalizeTavilyBaseUrl(settings.tavilyBaseUrl)
            it[LLM_INACTIVITY_RECONNECT_TIMEOUT_SECONDS] =
                normalizeLlmInactivityReconnectTimeoutSeconds(
                    settings.llmInactivityReconnectTimeoutSeconds
                )
            it[KEEP_TASKS_RUNNING_IN_BACKGROUND] = settings.keepTasksRunningInBackground
            it[NOTIFY_ON_TASK_COMPLETION] = settings.notifyOnTaskCompletion
            it[AGENT_WORKSPACE_MODE] = settings.agentWorkspaceMode.storageValue
            it[WORKSPACE_MODE_INITIALIZED] = true
            it[AUTO_CLEAN_OLD_COMMAND_HISTORY] = settings.autoCleanOldCommandHistory
            it[OLD_COMMAND_HISTORY_RETENTION_HOURS] =
                normalizeOldCommandHistoryRetentionHours(settings.oldCommandHistoryRetentionHours)
            it[TERMUX_SETUP_COMPLETED] = settings.termuxSetupCompleted
            it[TERMUX_SETUP_NOTICE_DISMISSED] = settings.termuxSetupNoticeDismissed
            it[TERMUX_LIVE_OUTPUT_ENABLED] = settings.termuxLiveOutputEnabled
            it[TERMUX_ENVIRONMENT_VARIABLES] =
                serializeTermuxEnvironmentVariables(settings.termuxEnvironmentVariables)
            it[ENABLED_RUNTIME_IDS] = serializeRuntimeIds(settings.enabledRuntimeIds)
            settings.defaultRuntimeId?.let { runtimeId ->
                it[DEFAULT_RUNTIME_ID] = runtimeId.storageValue
            } ?: it.remove(DEFAULT_RUNTIME_ID)
            it[ALPINE_SETUP_COMPLETED] = settings.alpineSetupCompleted
            it[ALPINE_PACKAGE_PROFILES] = serializePackageProfileStates(settings.alpinePackageProfiles)
            it[ALPINE_ENVIRONMENT_VARIABLES] =
                serializeAlpineEnvironmentVariables(settings.alpineEnvironmentVariables)
            it[AGENT_MODE_AUTHORIZATION_ENABLED] = settings.agentModeAuthorizationEnabled
            it[AGENT_MODE_AUTHORIZATION_METHOD] = settings.agentModeAuthorizationMethod.storageValue
            it[LANGUAGE] = settings.language.storageValue
            it[THEME_MODE] = settings.themeMode.storageValue
            it[DEFAULT_CHAT_MODEL_KEY] = settings.defaultChatModelKey
            it[DEFAULT_TITLE_MODEL_KEY] = settings.defaultTitleModelKey
            it[DEFAULT_NAMING_MODEL_KEY] = settings.defaultNamingModelKey
            it[DEFAULT_COMPACTING_MODEL_KEY] = settings.defaultCompactingModelKey
            it.remove(PROVIDER)
            it.remove(BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE)
            it.remove(UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS)
            it[PRIVACY_POLICY_ACCEPTED] = settings.privacyPolicyAccepted
            it[LAST_UPDATE_CHECK_AT_MILLIS] = settings.lastUpdateCheckAtMillis
        }
    }

    suspend fun updatePrivacyPolicyAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PRIVACY_POLICY_ACCEPTED] = accepted
        }
    }

    suspend fun updateOnboardingSeenVersion(version: Int) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_SEEN_VERSION] = version
        }
    }

    suspend fun updateOnboardingCompletedVersion(version: Int) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED_VERSION] = version
        }
    }

    suspend fun updateLastUpdateCheckAtMillis(value: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK_AT_MILLIS] = value
        }
    }

    suspend fun isWorkspaceModeInitialized(): Boolean =
        context.dataStore.data.map { preferences ->
            preferences[WORKSPACE_MODE_INITIALIZED] ?: false
        }.first()

    private fun MutablePreferences.writeProviderUserAgent(
        mode: ProviderUserAgentMode,
        customValue: String?,
    ) {
        this[PROVIDER_USER_AGENT_MODE] = mode.storageValue
        customValue?.let { value ->
            this[CUSTOM_PROVIDER_USER_AGENT] = value
        } ?: remove(CUSTOM_PROVIDER_USER_AGENT)
    }

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val PI_PROVIDER_ID = stringPreferencesKey("pi_provider_id")
        val PROVIDER_CONFIG_ID = stringPreferencesKey("provider_config_id")
        val PROVIDER_AUTH_METHOD = stringPreferencesKey("provider_auth_method")
        val API_KEY = stringPreferencesKey("api_key")
        val OAUTH_CREDENTIAL_JSON = stringPreferencesKey("oauth_credential_json")
        val PROVIDER_ENVIRONMENT_VARIABLES = stringPreferencesKey("provider_environment_variables")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_ID = stringPreferencesKey("model_id")
        val PROVIDER_USER_AGENT_MODE = stringPreferencesKey("provider_user_agent_mode")
        val CUSTOM_PROVIDER_USER_AGENT = stringPreferencesKey("custom_provider_user_agent")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
        val TAVILY_BASE_URL = stringPreferencesKey("tavily_base_url")
        val LLM_INACTIVITY_RECONNECT_TIMEOUT_SECONDS =
            intPreferencesKey("llm_inactivity_reconnect_timeout_seconds")
        val KEEP_TASKS_RUNNING_IN_BACKGROUND =
            booleanPreferencesKey("keep_tasks_running_in_background")
        val NOTIFY_ON_TASK_COMPLETION =
            booleanPreferencesKey("notify_on_task_completion")
        val AGENT_WORKSPACE_MODE = stringPreferencesKey("agent_workspace_mode")
        val WORKSPACE_MODE_INITIALIZED =
            booleanPreferencesKey("workspace_mode_initialized")
        val AUTO_CLEAN_OLD_COMMAND_HISTORY =
            booleanPreferencesKey("auto_clean_old_command_history")
        val OLD_COMMAND_HISTORY_RETENTION_HOURS =
            intPreferencesKey("old_command_history_retention_hours")
        val TERMUX_SETUP_COMPLETED =
            booleanPreferencesKey("termux_setup_completed")
        val TERMUX_SETUP_NOTICE_DISMISSED =
            booleanPreferencesKey("termux_setup_notice_dismissed")
        val TERMUX_LIVE_OUTPUT_ENABLED =
            booleanPreferencesKey("termux_live_output_enabled")
        val TERMUX_ENVIRONMENT_VARIABLES =
            stringPreferencesKey("termux_environment_variables")
        val ENABLED_RUNTIME_IDS =
            stringPreferencesKey("enabled_runtime_ids")
        val DEFAULT_RUNTIME_ID =
            stringPreferencesKey("default_runtime_id")
        val ALPINE_SETUP_COMPLETED =
            booleanPreferencesKey("alpine_setup_completed")
        val ALPINE_PACKAGE_PROFILES =
            stringPreferencesKey("alpine_package_profiles")
        val ALPINE_ENVIRONMENT_VARIABLES =
            stringPreferencesKey("alpine_environment_variables")
        val AGENT_MODE_AUTHORIZATION_ENABLED =
            booleanPreferencesKey("agent_mode_authorization_enabled")
        val AGENT_MODE_AUTHORIZATION_METHOD =
            stringPreferencesKey("agent_mode_authorization_method")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CHAT_MODEL_KEY = stringPreferencesKey("default_chat_model_key")
        val DEFAULT_TITLE_MODEL_KEY = stringPreferencesKey("default_title_model_key")
        val DEFAULT_NAMING_MODEL_KEY = stringPreferencesKey("default_naming_model_key")
        val DEFAULT_COMPACTING_MODEL_KEY = stringPreferencesKey("default_compacting_model_key")
        val UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS =
            stringPreferencesKey("unsupported_parallel_tool_call_provider_keys")
        val BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE =
            booleanPreferencesKey("basic_function_calling_compatibility_mode")
        val PROVIDER_CONFIGS = stringPreferencesKey("provider_configs")
        val ONBOARDING_SEEN_VERSION = intPreferencesKey("onboarding_seen_version")
        val ONBOARDING_COMPLETED_VERSION = intPreferencesKey("onboarding_completed_version")
        val PRIVACY_POLICY_ACCEPTED = booleanPreferencesKey("privacy_policy_accepted")
        val LAST_UPDATE_CHECK_AT_MILLIS = longPreferencesKey("last_update_check_at_millis")
    }
}

private fun LlmProviderConfig.matchesStoredModel(
    piProviderId: String,
    providerConfigId: String,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean =
    (id == providerConfigId || this.piProviderId == piProviderId) &&
        this.apiKey.trim() == apiKey.trim() &&
        this.baseUrl.trim() == baseUrl.trim() &&
        enabledModels().contains(modelId.trim())

private fun ProviderModelOption.matchesStoredModel(
    piProviderId: String,
    providerConfigId: String,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean =
    (this.providerConfigId == providerConfigId || this.piProviderId == piProviderId) &&
        this.apiKey.trim() == apiKey.trim() &&
        this.baseUrl.trim() == baseUrl.trim() &&
        this.modelId.trim() == modelId.trim()

private fun serializeProviderEnvironmentVariables(
    variables: List<PiProviderEnvironmentVariable>,
): String = JSONArray().apply {
    variables
        .mapNotNull { variable ->
            variable.name.trim().takeIf(String::isNotBlank)?.let { name ->
                PiProviderEnvironmentVariable(name, variable.value)
            }
        }
        .distinctBy { it.name.uppercase() }
        .forEach { variable ->
            put(
                JSONObject()
                    .put("name", variable.name)
                    .put("value", variable.value)
            )
        }
}.toString()

private val TermuxEnvironmentVariableNamePattern = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

fun normalizeTermuxEnvironmentVariables(
    variables: List<TermuxEnvironmentVariable>,
): List<TermuxEnvironmentVariable> =
    variables
        .mapNotNull { variable ->
            val name = variable.name.trim()
            if (!TermuxEnvironmentVariableNamePattern.matches(name)) {
                null
            } else {
                TermuxEnvironmentVariable(name = name, value = variable.value)
            }
        }
        .distinctBy { it.name }

fun normalizeAlpineEnvironmentVariables(
    variables: List<AlpineEnvironmentVariable>,
): List<AlpineEnvironmentVariable> =
    variables
        .mapNotNull { variable ->
            val name = variable.name.trim()
            if (!TermuxEnvironmentVariableNamePattern.matches(name)) {
                null
            } else {
                AlpineEnvironmentVariable(name = name, value = variable.value)
            }
        }
        .distinctBy { it.name }

private fun resolveEnabledRuntimeIds(
    rawValue: String?,
    termuxSetupCompleted: Boolean,
    alpineSetupCompleted: Boolean,
): Set<LocalRuntimeId> {
    val stored = parseRuntimeIds(rawValue.orEmpty())
    if (stored.isNotEmpty() || rawValue != null) return stored
    return buildSet {
        if (termuxSetupCompleted) add(LocalRuntimeId.Termux)
        if (alpineSetupCompleted) add(LocalRuntimeId.Alpine)
    }
}

private fun resolveDefaultRuntimeId(
    rawValue: String?,
    enabledRuntimeIds: Set<LocalRuntimeId>,
    termuxSetupCompleted: Boolean,
    alpineSetupCompleted: Boolean,
): LocalRuntimeId? {
    LocalRuntimeId.fromStorage(rawValue)?.let { runtimeId ->
        if (runtimeId in enabledRuntimeIds) return runtimeId
    }
    return when {
        termuxSetupCompleted && LocalRuntimeId.Termux in enabledRuntimeIds -> LocalRuntimeId.Termux
        alpineSetupCompleted && LocalRuntimeId.Alpine in enabledRuntimeIds -> LocalRuntimeId.Alpine
        else -> enabledRuntimeIds.firstOrNull()
    }
}

private fun parseRuntimeIds(rawValue: String): Set<LocalRuntimeId> {
    if (rawValue.isBlank()) return emptySet()
    return runCatching {
        val array = JSONArray(rawValue)
        buildSet {
            for (index in 0 until array.length()) {
                LocalRuntimeId.fromStorage(array.optString(index))?.let(::add)
            }
        }
    }.getOrDefault(emptySet())
}

private fun serializeRuntimeIds(runtimeIds: Set<LocalRuntimeId>): String =
    JSONArray().apply {
        runtimeIds.sortedBy { it.storageValue }.forEach { put(it.storageValue) }
    }.toString()

private fun parsePackageProfileStates(rawValue: String): Map<String, PackageProfileState> {
    if (rawValue.isBlank()) return emptyMap()
    return runCatching {
        val array = JSONArray(rawValue)
        buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val profileId = item.optString("profileId").trim()
                    .ifBlank { item.optString("profile_id").trim() }
                if (profileId.isBlank()) continue
                put(
                    profileId,
                    PackageProfileState(
                        profileId = profileId,
                        installed = item.optBoolean("installed", false),
                        installedAtMillis = item.optLong("installedAtMillis", 0L),
                        lastError = item.optString("lastError"),
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())
}

private fun serializePackageProfileStates(
    profiles: Map<String, PackageProfileState>,
): String =
    JSONArray().apply {
        profiles.values.sortedBy { it.profileId }.forEach { profile ->
            put(
                JSONObject().apply {
                    put("profileId", profile.profileId)
                    put("installed", profile.installed)
                    put("installedAtMillis", profile.installedAtMillis)
                    put("lastError", profile.lastError)
                }
            )
        }
    }.toString()

private fun parseTermuxEnvironmentVariables(rawValue: String): List<TermuxEnvironmentVariable> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        normalizeTermuxEnvironmentVariables(
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        TermuxEnvironmentVariable(
                            name = item.optString("name"),
                            value = item.optString("value"),
                        )
                    )
                }
            }
        )
    }.getOrDefault(emptyList())
}

private fun parseAlpineEnvironmentVariables(rawValue: String): List<AlpineEnvironmentVariable> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        normalizeAlpineEnvironmentVariables(
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AlpineEnvironmentVariable(
                            name = item.optString("name"),
                            value = item.optString("value"),
                        )
                    )
                }
            }
        )
    }.getOrDefault(emptyList())
}

private fun serializeTermuxEnvironmentVariables(
    variables: List<TermuxEnvironmentVariable>,
): String =
    JSONArray().apply {
        normalizeTermuxEnvironmentVariables(variables).forEach { variable ->
            put(
                JSONObject().apply {
                    put("name", variable.name)
                    put("value", variable.value)
                }
            )
        }
    }.toString()

private fun serializeAlpineEnvironmentVariables(
    variables: List<AlpineEnvironmentVariable>,
): String =
    JSONArray().apply {
        normalizeAlpineEnvironmentVariables(variables).forEach { variable ->
            put(
                JSONObject().apply {
                    put("name", variable.name)
                    put("value", variable.value)
                }
            )
        }
    }.toString()

private val ShizukuManagerPackages = listOf(
    "moe.shizuku.privileged.api",
    "moe.shizuku.manager",
)

private fun defaultAgentModeAuthorizationMethod(
    context: Context,
): AgentModeAuthorizationMethod =
    if (isAnyPackageInstalled(context, ShizukuManagerPackages)) {
        AgentModeAuthorizationMethod.Shizuku
    } else {
        AgentModeAuthorizationMethod.Root
    }

private fun isAnyPackageInstalled(
    context: Context,
    packageNames: List<String>,
): Boolean {
    val packageManager = context.packageManager
    return packageNames.any { packageName ->
        runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}
