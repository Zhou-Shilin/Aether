package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    suspend fun loadModelCatalogCache(): Map<String, ModelCatalogInfo> = context.dataStore.data.first()
        .let { preferences -> parseModelCatalogCache(preferences[MODEL_CATALOG_CACHE_JSON].orEmpty()) }

    suspend fun saveModelCatalogCache(cache: Map<String, ModelCatalogInfo>) {
        context.dataStore.edit { preferences ->
            val merged = parseModelCatalogCache(preferences[MODEL_CATALOG_CACHE_JSON].orEmpty()) + cache
            preferences[MODEL_CATALOG_CACHE_JSON] = serializeModelCatalogCache(merged)
        }
    }

    suspend fun loadThinkingCatalogCache(): Map<String, List<String>> = context.dataStore.data.first()
        .let { preferences -> parseThinkingCatalogCache(preferences[THINKING_CATALOG_CACHE_JSON].orEmpty()).levels }

    suspend fun loadThinkingLevelMapsCache(): Map<String, Map<String, String>> = context.dataStore.data.first()
        .let { preferences -> parseThinkingCatalogCache(preferences[THINKING_CATALOG_CACHE_JSON].orEmpty()).clamps }

    suspend fun loadReasoningModelsCache(): Set<String> = context.dataStore.data.first()
        .let { preferences -> parseThinkingCatalogCache(preferences[THINKING_CATALOG_CACHE_JSON].orEmpty()).reasoningModels }

    suspend fun loadModelLimitsCache(): Map<String, ModelsDevModelLimits> = context.dataStore.data.first()
        .let { preferences -> parseThinkingCatalogCache(preferences[THINKING_CATALOG_CACHE_JSON].orEmpty()).limits }

    suspend fun saveThinkingCatalogCache(
        cache: Map<String, List<String>>,
        levelMaps: Map<String, Map<String, String>> = emptyMap(),
        reasoningModels: Set<String> = emptySet(),
        limits: Map<String, ModelsDevModelLimits> = emptyMap(),
    ) {
        if (cache.isEmpty() && levelMaps.isEmpty() && reasoningModels.isEmpty() && limits.isEmpty()) return
        context.dataStore.edit { preferences ->
            val existing = parseThinkingCatalogCache(preferences[THINKING_CATALOG_CACHE_JSON].orEmpty())
            val mergedLevels = existing.levels + cache
            val mergedLevelMaps = (existing.clamps - cache.keys) + levelMaps
            val mergedReasoningModels = (existing.reasoningModels - cache.keys) + reasoningModels
            val mergedLimits = (existing.limits - cache.keys) + limits
            preferences[THINKING_CATALOG_CACHE_JSON] = serializeThinkingCatalogCache(
                mergedLevels,
                mergedLevelMaps,
                mergedReasoningModels,
                mergedLimits,
            )
        }
    }
    suspend fun initializeLanguageIfNeeded() {
        val preferences = context.dataStore.data.first()
        if (preferences[LANGUAGE] == null) {
            context.dataStore.edit { it[LANGUAGE] = defaultAppLanguage().storageValue }
        }
    }

    suspend fun repairBuiltInSkillSelectionDefaults() {
        context.dataStore.edit { preferences ->
            if (preferences[BUILT_IN_SKILL_SELECTION_REPAIRED] == true) return@edit
            if (preferences[BUILT_IN_SKILL_DEFAULTS_INITIALIZED] == true) {
                val selectedSkillIds = preferences[DEFAULT_SELECTED_SKILL_IDS]
                    ?.let(::parseStringArray)
                    .orEmpty()
                    .filterNot(BuiltInAgentSkillIds::contains)
                preferences[DEFAULT_SELECTED_SKILL_IDS] = serializeStringArray(selectedSkillIds)
            }
            preferences[BUILT_IN_SKILL_SELECTION_REPAIRED] = true
        }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val defaults = AppSettings()
        val storedWorkspaceMode = AgentWorkspaceMode.fromStorage(preferences[AGENT_WORKSPACE_MODE])
        val storedBaseUrl = preferences[BASE_URL] ?: defaults.baseUrl
        AppSettings(
            piProviderId = preferences[PI_PROVIDER_ID]
                ?.takeIf(String::isNotBlank)
                ?: inferLegacyPiProviderId(preferences[PROVIDER], storedBaseUrl),
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
            userAgent = normalizeLlmUserAgent(preferences[USER_AGENT]),
            customHeaders = parseCustomHeaders(preferences[CUSTOM_HEADERS].orEmpty()),
            compatibilityMode = preferences[COMPATIBILITY_MODE] ?: false,
            developerRoleUnsupported = preferences[DEVELOPER_ROLE_UNSUPPORTED] ?: false,
            reasoningEffort = normalizeReasoningEffort(preferences[REASONING_EFFORT]),
            systemPrompt = preferences[SYSTEM_PROMPT] ?: defaults.systemPrompt,
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
            defaultSelectedSkillIds = preferences[DEFAULT_SELECTED_SKILL_IDS]
                ?.let(::parseStringArray)
                ?.let { selectedSkillIds ->
                    if (
                        preferences[BUILT_IN_SKILL_DEFAULTS_INITIALIZED] == true &&
                        preferences[BUILT_IN_SKILL_SELECTION_REPAIRED] != true
                    ) {
                        selectedSkillIds.filterNot(BuiltInAgentSkillIds::contains)
                    } else {
                        selectedSkillIds
                    }
                }
                ?: defaults.defaultSelectedSkillIds,
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
                prefs[USER_AGENT] = normalizeLlmUserAgent(config.userAgent)
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

    suspend fun setProviderDeveloperRoleUnsupported(
        id: String,
        unsupported: Boolean,
    ) {
        if (id.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty())
            val updated = current.map { config ->
                if (config.id == id && config.developerRoleUnsupported != unsupported) {
                    config.copy(
                        developerRoleUnsupported = unsupported,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                } else {
                    config
                }
            }
            prefs[PROVIDER_CONFIGS] = serializeProviderConfigs(updated)
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
                prefs[USER_AGENT] = normalizeLlmUserAgent(fallbackOption.userAgent)
                prefs[CUSTOM_HEADERS] = serializeCustomHeaders(fallbackOption.customHeaders)
                prefs[COMPATIBILITY_MODE] = fallbackOption.compatibilityMode
                prefs[DEVELOPER_ROLE_UNSUPPORTED] = fallbackOption.developerRoleUnsupported
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
            it[USER_AGENT] = normalizeLlmUserAgent(settings.userAgent)
            it[CUSTOM_HEADERS] = serializeCustomHeaders(settings.customHeaders)
            it[COMPATIBILITY_MODE] = settings.compatibilityMode
            it[DEVELOPER_ROLE_UNSUPPORTED] = settings.developerRoleUnsupported
            it[REASONING_EFFORT] = normalizeReasoningEffort(settings.reasoningEffort)
            it[SYSTEM_PROMPT] = settings.systemPrompt
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
            it[DEFAULT_SELECTED_SKILL_IDS] = serializeStringArray(settings.defaultSelectedSkillIds)
            it[BUILT_IN_SKILL_SELECTION_REPAIRED] = true
            it.remove(PROVIDER)
            it.remove(BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE)
            it.remove(UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS)
            it[ONBOARDING_SEEN_VERSION] = settings.onboardingSeenVersion
            it[ONBOARDING_COMPLETED_VERSION] = settings.onboardingCompletedVersion
            it[PRIVACY_POLICY_ACCEPTED] =
                (it[PRIVACY_POLICY_ACCEPTED] ?: false) || settings.privacyPolicyAccepted
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

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = language.storageValue }
    }

    suspend fun updateThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = themeMode.storageValue }
    }

    suspend fun updateUserSettings(settings: AppSettings) {
        context.dataStore.edit {
            it[SYSTEM_PROMPT] = settings.systemPrompt
            it[LLM_INACTIVITY_RECONNECT_TIMEOUT_SECONDS] =
                normalizeLlmInactivityReconnectTimeoutSeconds(
                    settings.llmInactivityReconnectTimeoutSeconds,
                )
            it[KEEP_TASKS_RUNNING_IN_BACKGROUND] = settings.keepTasksRunningInBackground
            it[NOTIFY_ON_TASK_COMPLETION] = settings.notifyOnTaskCompletion
            it[AGENT_WORKSPACE_MODE] = settings.agentWorkspaceMode.storageValue
            it[WORKSPACE_MODE_INITIALIZED] = true
            it[AUTO_CLEAN_OLD_COMMAND_HISTORY] = settings.autoCleanOldCommandHistory
            it[OLD_COMMAND_HISTORY_RETENTION_HOURS] =
                normalizeOldCommandHistoryRetentionHours(settings.oldCommandHistoryRetentionHours)
            it[TERMUX_ENVIRONMENT_VARIABLES] =
                serializeTermuxEnvironmentVariables(settings.termuxEnvironmentVariables)
            it[LANGUAGE] = settings.language.storageValue
            it[THEME_MODE] = settings.themeMode.storageValue
        }
    }

    suspend fun updateAgentModeAuthorization(
        enabled: Boolean,
        method: AgentModeAuthorizationMethod,
    ) {
        context.dataStore.edit {
            it[AGENT_MODE_AUTHORIZATION_ENABLED] = enabled
            it[AGENT_MODE_AUTHORIZATION_METHOD] = method.storageValue
        }
    }

    suspend fun updateDefaultModelKeys(
        chat: String,
        title: String,
        naming: String,
        compacting: String,
    ) {
        context.dataStore.edit {
            it[DEFAULT_CHAT_MODEL_KEY] = chat
            it[DEFAULT_TITLE_MODEL_KEY] = title
            it[DEFAULT_NAMING_MODEL_KEY] = naming
            it[DEFAULT_COMPACTING_MODEL_KEY] = compacting
        }
    }

    suspend fun markRootSetupComplete() {
        context.dataStore.edit { preferences ->
            val enabledRuntimes = resolveEnabledRuntimeIds(
                rawValue = preferences[ENABLED_RUNTIME_IDS],
                termuxSetupCompleted = true,
                alpineSetupCompleted = preferences[ALPINE_SETUP_COMPLETED] ?: false,
            ) + LocalRuntimeId.Termux
            preferences[TERMUX_SETUP_COMPLETED] = true
            preferences[ENABLED_RUNTIME_IDS] = serializeRuntimeIds(enabledRuntimes)
            if (preferences[DEFAULT_RUNTIME_ID] == null) {
                preferences[DEFAULT_RUNTIME_ID] = LocalRuntimeId.Termux.storageValue
            }
            preferences[AGENT_MODE_AUTHORIZATION_ENABLED] = true
            preferences[AGENT_MODE_AUTHORIZATION_METHOD] =
                AgentModeAuthorizationMethod.Root.storageValue
        }
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
            it[USER_AGENT] = normalizeLlmUserAgent(settings.userAgent)
            it[CUSTOM_HEADERS] = serializeCustomHeaders(settings.customHeaders)
            it[COMPATIBILITY_MODE] = settings.compatibilityMode
            it[DEVELOPER_ROLE_UNSUPPORTED] = settings.developerRoleUnsupported
            it[REASONING_EFFORT] = normalizeReasoningEffort(settings.reasoningEffort)
            it[TERMUX_SETUP_COMPLETED] = settings.termuxSetupCompleted
            it[TERMUX_SETUP_NOTICE_DISMISSED] = settings.termuxSetupNoticeDismissed
            it[ENABLED_RUNTIME_IDS] = serializeRuntimeIds(settings.enabledRuntimeIds)
            settings.defaultRuntimeId?.let { runtimeId ->
                it[DEFAULT_RUNTIME_ID] = runtimeId.storageValue
            } ?: it.remove(DEFAULT_RUNTIME_ID)
            it[ALPINE_SETUP_COMPLETED] = settings.alpineSetupCompleted
            it[ALPINE_PACKAGE_PROFILES] = serializePackageProfileStates(settings.alpinePackageProfiles)
            it[ALPINE_ENVIRONMENT_VARIABLES] =
                serializeAlpineEnvironmentVariables(settings.alpineEnvironmentVariables)
            it.remove(PROVIDER)
            it.remove(BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE)
            it.remove(UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS)
            it[PRIVACY_POLICY_ACCEPTED] =
                (it[PRIVACY_POLICY_ACCEPTED] ?: false) || settings.privacyPolicyAccepted
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

    suspend fun updateDefaultSelectedSkillIds(skillIds: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_SELECTED_SKILL_IDS] = serializeStringArray(skillIds)
            preferences[BUILT_IN_SKILL_SELECTION_REPAIRED] = true
        }
    }

    suspend fun isWorkspaceModeInitialized(): Boolean =
        context.dataStore.data.map { preferences ->
            preferences[WORKSPACE_MODE_INITIALIZED] ?: false
        }.first()

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
        val USER_AGENT = stringPreferencesKey("user_agent")
        val CUSTOM_HEADERS = stringPreferencesKey("custom_headers")
        val COMPATIBILITY_MODE = booleanPreferencesKey("compatibility_mode")
        val DEVELOPER_ROLE_UNSUPPORTED = booleanPreferencesKey("developer_role_unsupported")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
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
        val MODEL_CATALOG_CACHE_JSON = stringPreferencesKey("model_catalog_cache_json")
        val THINKING_CATALOG_CACHE_JSON = stringPreferencesKey("thinking_catalog_cache_json")
        val DEFAULT_SELECTED_SKILL_IDS = stringPreferencesKey("default_selected_skill_ids")
        val BUILT_IN_SKILL_DEFAULTS_INITIALIZED =
            booleanPreferencesKey("built_in_skill_defaults_initialized_v1")
        val BUILT_IN_SKILL_SELECTION_REPAIRED =
            booleanPreferencesKey("built_in_skill_selection_repaired_v2")
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

private fun parseStringArray(rawValue: String): List<String> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())
}

private fun serializeStringArray(values: List<String>): String =
    JSONArray().apply {
        values.map(String::trim).filter(String::isNotBlank).distinct().forEach(::put)
    }.toString()

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

private fun parseCustomHeaders(rawValue: String): List<LlmCustomHeader> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotBlank()) {
                    add(LlmCustomHeader(name = name, value = item.optString("value")))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun serializeCustomHeaders(headers: List<LlmCustomHeader>): String =
    JSONArray().apply {
        headers.forEach { header ->
            if (header.name.isNotBlank()) {
                put(
                    JSONObject().apply {
                        put("name", header.name.trim())
                        put("value", header.value)
                    }
                )
            }
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

private fun serializeModelCatalogCache(cache: Map<String, ModelCatalogInfo>): String = JSONArray().apply {
    cache.forEach { (key, info) ->
        put(JSONObject().apply {
            put("key", key)
            put("displayName", info.displayName)
            put("labId", info.labId)
            put("labName", info.labName)
            put("labLogoUrl", info.labLogoUrl)
            put("labLogoPathData", JSONArray(info.labLogoPathData))
            put("labLogoViewportWidth", info.labLogoViewportWidth)
            put("labLogoViewportHeight", info.labLogoViewportHeight)
        })
    }
}.toString()

private fun parseModelCatalogCache(raw: String): Map<String, ModelCatalogInfo> = runCatching {
    val array = JSONArray(raw)
    buildMap {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key").takeIf(String::isNotBlank) ?: continue
            val paths = item.optJSONArray("labLogoPathData")?.let { values ->
                buildList { for (i in 0 until values.length()) add(values.optString(i)) }
            }.orEmpty()
            put(key, ModelCatalogInfo(
                displayName = item.optString("displayName"), labId = item.optString("labId"),
                labName = item.optString("labName"), labLogoUrl = item.optString("labLogoUrl"),
                labLogoPathData = paths,
                labLogoViewportWidth = item.optDouble("labLogoViewportWidth", 40.0).toFloat(),
                labLogoViewportHeight = item.optDouble("labLogoViewportHeight", 40.0).toFloat(),
            ))
        }
    }
}.getOrDefault(emptyMap())

private fun serializeThinkingCatalogCache(
    levels: Map<String, List<String>>,
    clamps: Map<String, Map<String, String>> = emptyMap(),
    reasoningModels: Set<String> = emptySet(),
    limits: Map<String, ModelsDevModelLimits> = emptyMap(),
): String = JSONArray().apply {
    val allKeys = (levels.keys + clamps.keys + reasoningModels + limits.keys).distinct()
    allKeys.forEach { key ->
        put(JSONObject().apply {
            put("key", key)
            put("reasoning", key in reasoningModels)
            levels[key]?.let { put("levels", JSONArray(it)) }
            clamps[key]?.let { clampMap ->
                if (clampMap.isNotEmpty()) {
                    put("clamps", JSONObject().apply {
                        clampMap.forEach { (k, v) -> put(k, v) }
                    })
                }
            }
            limits[key]?.let { modelLimits ->
                put("limits", JSONObject().apply {
                    modelLimits.contextWindow?.let { put("context_window", it) }
                    modelLimits.maxOutputTokens?.let { put("max_output_tokens", it) }
                    modelLimits.supportsImageInput?.let { put("image_input", it) }
                })
            }
        })
    }
}.toString()

private data class ThinkingCatalogCache(
    val levels: Map<String, List<String>> = emptyMap(),
    val clamps: Map<String, Map<String, String>> = emptyMap(),
    val reasoningModels: Set<String> = emptySet(),
    val limits: Map<String, ModelsDevModelLimits> = emptyMap(),
)

private fun parseThinkingCatalogCache(raw: String): ThinkingCatalogCache = runCatching {
    val array = JSONArray(raw)
    val levelsMap = mutableMapOf<String, List<String>>()
    val clampsMap = mutableMapOf<String, Map<String, String>>()
    val reasoningModels = mutableSetOf<String>()
    val limitsMap = mutableMapOf<String, ModelsDevModelLimits>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val key = item.optString("key").takeIf(String::isNotBlank) ?: continue
        if (item.optBoolean("reasoning")) reasoningModels += key
        item.optJSONArray("levels")?.let { values ->
            val levels = buildList {
                for (i in 0 until values.length()) {
                    values.optString(i).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
            levelsMap[key] = levels
        }
        item.optJSONObject("clamps")?.let { clampsObj ->
            val clamps = buildMap {
                for (k in clampsObj.keys()) {
                    clampsObj.optString(k).takeIf(String::isNotBlank)?.let { put(k, it) }
                }
            }
            if (clamps.isNotEmpty()) clampsMap[key] = clamps
        }
        item.optJSONObject("limits")?.let { limitsObj ->
            limitsMap[key] = ModelsDevModelLimits(
                contextWindow = limitsObj.optInt("context_window").takeIf { it > 0 },
                maxOutputTokens = limitsObj.optInt("max_output_tokens").takeIf { it > 0 },
                supportsImageInput = if (limitsObj.has("image_input")) limitsObj.optBoolean("image_input") else null,
            )
        }
    }
    ThinkingCatalogCache(levelsMap, clampsMap, reasoningModels, limitsMap)
}.getOrDefault(ThinkingCatalogCache())
