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

private val Context.dataStore by preferencesDataStore(name = "aether_settings")

class SettingsRepository(
    private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val defaults = AppSettings()
        val storedWorkspaceMode = AgentWorkspaceMode.fromStorage(preferences[AGENT_WORKSPACE_MODE])
        AppSettings(
            provider = LlmProvider.fromStorage(preferences[PROVIDER]),
            apiKey = preferences[API_KEY].orEmpty(),
            baseUrl = preferences[BASE_URL] ?: defaults.baseUrl,
            modelId = preferences[MODEL_ID] ?: defaults.modelId,
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
            unsupportedParallelToolCallProviderKeys = parseStoredStringList(
                preferences[UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS].orEmpty()
            ),
            basicFunctionCallingCompatibilityMode =
                preferences[BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE] ?: false,
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

    suspend fun setProviderEnabled(
        id: String,
        enabled: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val current = parseProviderConfigs(prefs[PROVIDER_CONFIGS].orEmpty())
            val currentProvider = LlmProvider.fromStorage(prefs[PROVIDER])
            val currentApiKey = prefs[API_KEY].orEmpty()
            val currentBaseUrl = prefs[BASE_URL] ?: AppSettings().baseUrl
            val currentModelId = prefs[MODEL_ID] ?: AppSettings().modelId
            val toggledConfigWasCurrent = current
                .firstOrNull { it.id == id }
                ?.matchesStoredModel(
                    provider = currentProvider,
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
                    provider = currentProvider,
                    apiKey = currentApiKey,
                    baseUrl = currentBaseUrl,
                    modelId = currentModelId,
                )
            }
            val fallbackOption = availableOptions.firstOrNull()
            if (!enabled && toggledConfigWasCurrent && !currentStillAvailable && fallbackOption != null) {
                prefs[PROVIDER] = fallbackOption.providerType.storageValue
                prefs[API_KEY] = fallbackOption.apiKey
                prefs[BASE_URL] = fallbackOption.baseUrl
                prefs[MODEL_ID] = fallbackOption.modelId
            }
        }
    }

    // ── Legacy single-provider methods ───────────────────────────────────────

    suspend fun replaceImportedSettings(
        settings: AppSettings,
        providerConfigs: List<LlmProviderConfig>,
    ) {
        context.dataStore.edit {
            it[PROVIDER] = settings.provider.storageValue
            it[API_KEY] = settings.apiKey
            it[BASE_URL] = settings.baseUrl
            it[MODEL_ID] = settings.modelId
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
            it[UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS] =
                serializeStoredStringList(settings.unsupportedParallelToolCallProviderKeys)
            it[BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE] = settings.basicFunctionCallingCompatibilityMode
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
            it[PROVIDER] = settings.provider.storageValue
            it[API_KEY] = settings.apiKey
            it[BASE_URL] = settings.baseUrl
            it[MODEL_ID] = settings.modelId
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
            it[UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS] =
                serializeStoredStringList(settings.unsupportedParallelToolCallProviderKeys)
            it[BASIC_FUNCTION_CALLING_COMPATIBILITY_MODE] = settings.basicFunctionCallingCompatibilityMode
            it[PRIVACY_POLICY_ACCEPTED] = settings.privacyPolicyAccepted
            it[LAST_UPDATE_CHECK_AT_MILLIS] = settings.lastUpdateCheckAtMillis
        }
    }

    suspend fun markParallelToolCallsUnsupported(providerKey: String) {
        val normalizedProviderKey = providerKey.trim()
        if (normalizedProviderKey.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parseStoredStringList(
                prefs[UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS].orEmpty()
            )
            if (normalizedProviderKey !in current) {
                prefs[UNSUPPORTED_PARALLEL_TOOL_CALL_PROVIDER_KEYS] =
                    serializeStoredStringList((current + normalizedProviderKey).takeLast(MaxUnsupportedParallelToolCallKeys))
            }
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

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_ID = stringPreferencesKey("model_id")
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
        const val MaxUnsupportedParallelToolCallKeys = 128
    }
}

private fun LlmProviderConfig.matchesStoredModel(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean =
    providerType == provider &&
        this.apiKey.trim() == apiKey.trim() &&
        this.baseUrl.trim() == baseUrl.trim() &&
        enabledModels().contains(modelId.trim())

private fun ProviderModelOption.matchesStoredModel(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean =
    providerType == provider &&
        this.apiKey.trim() == apiKey.trim() &&
        this.baseUrl.trim() == baseUrl.trim() &&
        this.modelId.trim() == modelId.trim()

private fun parseStoredStringList(rawValue: String): List<String> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }.distinct()
    }.getOrDefault(emptyList())
}

private fun serializeStoredStringList(values: List<String>): String =
    JSONArray().apply {
        values.map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach(::put)
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
