package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.SharedChatHistoryStore
import com.zhousl.aether.data.chatdb.resolveSharedCurrentSessionId
import com.zhousl.aether.data.chatdb.decodeAndroidChatSessions
import com.zhousl.aether.data.chatdb.encodeAndroidChatSessions
import com.zhousl.aether.data.pi.SharedMcpServerConfig
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.SharedPiBridgeClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

const val SharedAppDataSchemaVersion = 3

@Serializable
data class SharedAppDataArchive(
    val schemaVersion: Int = SharedAppDataSchemaVersion,
    val exportType: String = "app",
    val exportedAtMillis: Long,
    val settings: AppSettings,
    val providerConfigs: JsonArray,
    val activeProviderConfigId: String = "",
    val sessions: List<PersistedChatSession>,
    val currentSessionId: String? = null,
    val skillBundles: List<SharedSkillBundle>,
    val mcpServers: JsonArray = JsonArray(emptyList()),
    val piSessions: List<SharedPiSessionArchive> = emptyList(),
    val extensionArchive: SharedExtensionArchive? = null,
)

@Serializable
data class SharedPiSessionArchive(
    val sessionId: String,
    val jsonl: String,
)

data class SharedAppDataRestoreResult(
    val persistedSettings: SharedPersistedSettings,
    val sessions: List<PersistedChatSession>,
    val currentSessionId: String?,
    val installedSkills: List<SharedInstalledSkill>,
    val mcpServers: List<SharedMcpServerConfig>,
)

class SharedAppDataManager(
    private val settingsStore: AetherSettingsStore,
    private val historyStore: SharedChatHistoryStore,
    private val skillManager: SharedSkillManager,
    private val runtime: MultiplatformLocalRuntime,
    private val bridgeClient: SharedPiBridgeClient,
    private val extensionStateStore: SharedExtensionStateStore,
) {
    private val extensionArchiveManager = SharedExtensionArchiveManager(
        runtime = runtime,
        bridge = bridgeClient,
        stateStore = extensionStateStore,
    )
    suspend fun exportJson(): String = encodeSharedAppDataArchive(readArchive())

    suspend fun restoreJson(value: String): SharedAppDataRestoreResult {
        val imported = decodeSharedAppDataArchive(value)
        val previous = readArchive()
        return try {
            applyArchive(imported)
        } catch (error: Throwable) {
            runCatching { applyArchive(previous) }
            throw error
        }
    }

    private suspend fun readArchive(): SharedAppDataArchive {
        val persisted = settingsStore.load()
        val settings = persisted.appSettings.copy(
            providerConfigId = persisted.activeProviderConfigId,
        )
        val sessions = historyStore.loadAll().map { it.copy(activeSkills = emptyList()) }
        val piSessions = sessions.mapNotNull { session ->
            runCatching {
                val exported = bridgeClient.exportSessionJsonl(session.id)
                val path = exported["exported_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val jsonl = path.takeIf(String::isNotBlank)
                    ?.let { runtime.fileSystem.read(it).decodeToString() }
                    .orEmpty()
                SharedPiSessionArchive(sessionId = session.id, jsonl = jsonl)
            }.getOrNull()?.takeIf { it.jsonl.isNotBlank() }
        }
        return SharedAppDataArchive(
            exportedAtMillis = platformCurrentTimeMillis(),
            settings = settings,
            providerConfigs = Json.parseToJsonElement(
                serializeProviderConfigs(persisted.providerConfigs),
            ).jsonArray,
            activeProviderConfigId = persisted.activeProviderConfigId,
            sessions = sessions,
            currentSessionId = historyStore.loadCurrentSessionId(),
            skillBundles = skillManager.exportBundles(),
            mcpServers = JsonArray(emptyList()),
            piSessions = piSessions,
            extensionArchive = extensionArchiveManager.export(),
        )
    }

    private suspend fun applyArchive(archive: SharedAppDataArchive): SharedAppDataRestoreResult {
        val decoded = validateSharedAppDataArchive(archive)
        val installedSkills = skillManager.replaceBundles(archive.skillBundles)
        archive.extensionArchive?.let { extensionArchiveManager.restore(it) }
        val enabledSkillIds = installedSkills
            .filter(SharedInstalledSkill::isEnabled)
            .map(SharedInstalledSkill::id)
            .toSet()
        val settings = archive.settings.copy(
            providerConfigId = decoded.activeProviderConfigId,
        )
        val persisted = SharedPersistedSettings(
            providerConfigs = decoded.providerConfigs,
            activeProviderConfigId = decoded.activeProviderConfigId,
            onboardingCompletedVersion = settings.onboardingCompletedVersion,
            appSettings = settings,
        )

        settingsStore.replaceAll(persisted)
        val sessions = archive.sessions.map { session ->
            session.copy(
                selectedSkillIds = session.selectedSkillIds.filter(enabledSkillIds::contains),
                activeSkills = emptyList(),
                activeMcpServerIds = emptyList(),
            )
        }
        val currentSessionId = resolveSharedCurrentSessionId(
            currentSessionId = archive.currentSessionId,
            sessionIds = sessions.map(PersistedChatSession::id),
        )
        historyStore.replaceAll(sessions, currentSessionId)
        archive.piSessions.forEach { piSession ->
            if (sessions.none { it.id == piSession.sessionId } || piSession.jsonl.isBlank()) return@forEach
            val imported = bridgeClient.importSessionJsonl(piSession.sessionId, piSession.jsonl)
            val sessionFile = imported["session_file"]?.jsonPrimitive?.contentOrNull.orEmpty()
            historyStore.upsertAgentSessionMetadata(
                chatSessionId = piSession.sessionId,
                piSessionId = piSession.sessionId,
                jsonlPath = sessionFile,
                runtime = "alpine",
            )
        }

        return SharedAppDataRestoreResult(
            persistedSettings = persisted,
            sessions = sessions,
            currentSessionId = currentSessionId,
            installedSkills = installedSkills,
            mcpServers = emptyList(),
        )
    }
}

private data class ValidatedSharedAppData(
    val providerConfigs: List<LlmProviderConfig>,
    val activeProviderConfigId: String,
)

private val SharedAppDataJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun encodeSharedAppDataArchive(archive: SharedAppDataArchive): String {
    val validated = validateSharedAppDataArchive(archive)
    val exportedSettings = archive.settings.copy(
        providerConfigId = archive.activeProviderConfigId.ifBlank { archive.settings.providerConfigId },
    )
    val root = buildJsonObject {
        put("schemaVersion", SharedAppDataSchemaVersion)
        put("exportType", "app")
        put("exportedAtMillis", archive.exportedAtMillis)
        put("settings", exportedSettings.toAndroidAppSettingsJson())
        put(
            "providerConfigs",
            Json.parseToJsonElement(serializeProviderConfigs(validated.providerConfigs)),
        )
        put("sessions", encodeAndroidChatSessions(archive.sessions))
        put(
            "currentSessionId",
            resolveSharedCurrentSessionId(
                currentSessionId = archive.currentSessionId,
                sessionIds = archive.sessions.map(PersistedChatSession::id),
            ),
        )
        put(
            "skillBundles",
            Json.parseToJsonElement(SharedAppDataJson.encodeToString(archive.skillBundles)),
        )
        put("mcpServers", archive.mcpServers)
        put(
            "piSessions",
            Json.parseToJsonElement(SharedAppDataJson.encodeToString(archive.piSessions)),
        )
        archive.extensionArchive?.let { extensionArchive ->
            put(
                "extensionArchive",
                Json.parseToJsonElement(SharedAppDataJson.encodeToString(extensionArchive)),
            )
        }
    }
    return SharedAppDataJson.encodeToString(JsonObject.serializer(), root)
}

fun decodeSharedAppDataArchive(value: String): SharedAppDataArchive {
    require(value.isNotBlank()) { "App data backup is empty." }
    val root = runCatching { Json.parseToJsonElement(value).jsonObject }
        .getOrElse { throw IllegalArgumentException("App data backup is not valid JSON.", it) }
    val settingsObject = root["settings"] as? JsonObject ?: JsonObject(emptyMap())
    val settings = parseAndroidAppSettings(settingsObject)
    val providers = root["providerConfigs"] as? JsonArray ?: JsonArray(emptyList())
    val sessionsJson = root["sessions"] as? JsonArray ?: JsonArray(emptyList())
    val sessions = if (sessionsJson.usesAndroidChatSchema()) {
        decodeAndroidChatSessions(sessionsJson)
    } else {
        runCatching {
            SharedAppDataJson.decodeFromString<List<PersistedChatSession>>(sessionsJson.toString())
        }.getOrDefault(emptyList())
    }
    val skillBundlesJson = root["skillBundles"] as? JsonArray ?: JsonArray(emptyList())
    val skillBundles = skillBundlesJson.mapNotNull { element ->
        runCatching {
            SharedAppDataJson.decodeFromString<SharedSkillBundle>(element.toString())
        }.getOrNull()?.takeIf { bundle ->
            runCatching { validateSharedSkillBundles(listOf(bundle)) }.isSuccess
        }
    }
    val piSessions = (root["piSessions"] as? JsonArray).orEmpty().mapNotNull { element ->
        runCatching {
            SharedAppDataJson.decodeFromString<SharedPiSessionArchive>(element.toString())
        }.getOrNull()?.takeIf { it.sessionId.isNotBlank() && it.jsonl.isNotBlank() }
    }
    val extensionArchive = (root["extensionArchive"] as? JsonObject)?.let { element ->
        runCatching {
            SharedAppDataJson.decodeFromString<SharedExtensionArchive>(element.toString())
        }.getOrElse {
            throw IllegalArgumentException("Extensions backup is invalid.", it)
        }.also(::validateSharedExtensionArchive)
    }
    val archive = SharedAppDataArchive(
        schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: SharedAppDataSchemaVersion,
        exportType = (root["exportType"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "app" },
        exportedAtMillis = (root["exportedAtMillis"] as? JsonPrimitive)?.longOrNull ?: 0L,
        settings = settings,
        providerConfigs = providers,
        activeProviderConfigId = (root["activeProviderConfigId"] as? JsonPrimitive)
            ?.contentOrNull.orEmpty().ifBlank { settings.providerConfigId },
        sessions = sessions,
        currentSessionId = resolveSharedCurrentSessionId(
            currentSessionId = (root["currentSessionId"] as? JsonPrimitive)?.contentOrNull,
            sessionIds = sessions.map(PersistedChatSession::id),
        ),
        skillBundles = skillBundles,
        mcpServers = root["mcpServers"] as? JsonArray ?: JsonArray(emptyList()),
        piSessions = piSessions,
        extensionArchive = extensionArchive,
    )
    validateSharedAppDataArchive(archive)
    return archive
}

private fun validateSharedAppDataArchive(archive: SharedAppDataArchive): ValidatedSharedAppData {
    val providerConfigs = parseProviderConfigs(archive.providerConfigs.toString())
    val activeProviderConfigId = archive.activeProviderConfigId
        .ifBlank { archive.settings.providerConfigId }

    validateSharedSkillBundles(archive.skillBundles)
    archive.extensionArchive?.let(::validateSharedExtensionArchive)
    return ValidatedSharedAppData(
        providerConfigs = providerConfigs,
        activeProviderConfigId = activeProviderConfigId,
    )
}

private fun AppSettings.toAndroidAppSettingsJson(): JsonObject = buildJsonObject {
    put("piProviderId", piProviderId)
    put("providerConfigId", providerConfigId)
    put("providerAuthMethod", providerAuthMethod.storageValue)
    put("apiKey", apiKey)
    put("oauthCredentialJson", oauthCredentialJson)
    put("providerEnvironmentVariables", buildJsonArray {
        providerEnvironmentVariables.forEach { variable ->
            add(buildJsonObject { put("name", variable.name); put("value", variable.value) })
        }
    })
    put("baseUrl", baseUrl)
    put("modelId", modelId)
    put("serviceTier", serviceTier.trim())
    put("userAgent", normalizeLlmUserAgent(userAgent))
    put("customHeaders", buildJsonArray {
        customHeaders.forEach { header ->
            add(buildJsonObject { put("name", header.name); put("value", header.value) })
        }
    })
    put("reasoningEffort", normalizeReasoningEffort(reasoningEffort))
    put("systemPrompt", systemPrompt)
    put("llmInactivityReconnectTimeoutSeconds", llmInactivityReconnectTimeoutSeconds)
    put("keepTasksRunningInBackground", keepTasksRunningInBackground)
    put("notifyOnTaskCompletion", notifyOnTaskCompletion)
    put("agentWorkspaceMode", agentWorkspaceMode.storageValue)
    put("termuxSetupCompleted", termuxSetupCompleted)
    put("termuxSetupNoticeDismissed", termuxSetupNoticeDismissed)
    put("termuxEnvironmentVariables", buildJsonArray {
        termuxEnvironmentVariables.forEach { variable ->
            add(buildJsonObject { put("name", variable.name); put("value", variable.value) })
        }
    })
    put("enabledRuntimeIds", buildJsonArray {
        enabledRuntimeIds.forEach { add(JsonPrimitive(it.storageValue)) }
    })
    put("defaultRuntimeId", defaultRuntimeId?.let { JsonPrimitive(it.storageValue) } ?: JsonNull)
    put("alpineSetupCompleted", alpineSetupCompleted)
    put("alpinePackageProfiles", buildJsonArray {
        alpinePackageProfiles.values.forEach { profile ->
            add(buildJsonObject {
                put("profileId", profile.profileId)
                put("installed", profile.installed)
                put("installedAtMillis", profile.installedAtMillis)
                put("lastError", profile.lastError)
            })
        }
    })
    put("alpineEnvironmentVariables", buildJsonArray {
        alpineEnvironmentVariables.forEach { variable ->
            add(buildJsonObject { put("name", variable.name); put("value", variable.value) })
        }
    })
    put("autoCleanOldCommandHistory", autoCleanOldCommandHistory)
    put("oldCommandHistoryRetentionHours", oldCommandHistoryRetentionHours)
    put("phoneUseInputStrategy", phoneUseInputStrategy.storageValue)
    put("phoneUseOutputStrategy", phoneUseOutputStrategy.storageValue)
    put("phoneUseAllowStrategySwitch", phoneUseAllowStrategySwitch)
    put("phoneUseVirtualDisplayEnabled", phoneUseVirtualDisplayEnabled)
    put("agentModeDisplayOrientation", agentModeDisplayOrientation.storageValue)
    put("agentModeAuthorizationEnabled", agentModeAuthorizationEnabled)
    put("agentModeAuthorizationMethod", agentModeAuthorizationMethod.storageValue)
    put("language", language.storageValue)
    put("themeMode", themeMode.storageValue)
    put("defaultChatModelKey", defaultChatModelKey)
    put("defaultTitleModelKey", defaultTitleModelKey)
    put("defaultNamingModelKey", defaultNamingModelKey)
    put("defaultCompactingModelKey", defaultCompactingModelKey)
    put("defaultSelectedSkillIds", buildJsonArray { defaultSelectedSkillIds.forEach { add(JsonPrimitive(it)) } })
    put("onboardingSeenVersion", onboardingSeenVersion)
    put("onboardingCompletedVersion", onboardingCompletedVersion)
    put("privacyPolicyAccepted", privacyPolicyAccepted)
    put("lastUpdateCheckAtMillis", lastUpdateCheckAtMillis)
}

private fun parseAndroidAppSettings(value: JsonObject): AppSettings {
    val defaults = AppSettings()
    val importedBaseUrl = value.stringValueOrDefault("baseUrl", defaults.baseUrl)
    val importedPiProviderId = value.stringValue("piProviderId").trim().ifBlank {
        inferLegacyPiProviderId(value.stringValue("provider"), importedBaseUrl)
    }
    return AppSettings(
        piProviderId = importedPiProviderId,
        providerConfigId = value.stringValue("providerConfigId"),
        providerAuthMethod = ProviderAuthMethod.fromStorage(value.stringValue("providerAuthMethod")),
        apiKey = value.stringValueOrDefault("apiKey", defaults.apiKey),
        oauthCredentialJson = value.stringValueOrDefault(
            "oauthCredentialJson",
            defaults.oauthCredentialJson,
        ),
        providerEnvironmentVariables = parseProviderEnvironmentVariables(
            value["providerEnvironmentVariables"] as? JsonArray,
        ),
        baseUrl = importedBaseUrl,
        modelId = value.stringValueOrDefault("modelId", defaults.modelId),
        userAgent = normalizeLlmUserAgent(value.stringValueOrDefault("userAgent", defaults.userAgent)),
        serviceTier = value.stringValue("serviceTier").trim(),
        customHeaders = parseCustomHeaders(value["customHeaders"] as? JsonArray),
        reasoningEffort = normalizeReasoningEffort(
            value.stringValueOrDefault("reasoningEffort", defaults.reasoningEffort),
        ),
        systemPrompt = value.stringValueOrDefault("systemPrompt", defaults.systemPrompt),
        llmInactivityReconnectTimeoutSeconds = normalizeLlmInactivityReconnectTimeoutSeconds(
            value.intValueOrDefault(
                "llmInactivityReconnectTimeoutSeconds",
                defaults.llmInactivityReconnectTimeoutSeconds,
            ),
        ),
        keepTasksRunningInBackground = value.booleanValueOrDefault(
            "keepTasksRunningInBackground",
            defaults.keepTasksRunningInBackground,
        ),
        notifyOnTaskCompletion = value.booleanValueOrDefault(
            "notifyOnTaskCompletion",
            defaults.notifyOnTaskCompletion,
        ),
        agentWorkspaceMode = AgentWorkspaceMode.fromStorage(
            value.stringValueOrDefault("agentWorkspaceMode", defaults.agentWorkspaceMode.storageValue),
        ),
        autoCleanOldCommandHistory = value.booleanValueOrDefault(
            "autoCleanOldCommandHistory",
            defaults.autoCleanOldCommandHistory,
        ),
        oldCommandHistoryRetentionHours = normalizeOldCommandHistoryRetentionHours(
            value.intValueOrDefault(
                "oldCommandHistoryRetentionHours",
                defaults.oldCommandHistoryRetentionHours,
            ),
        ),
        termuxSetupCompleted = value.booleanValueOrDefault(
            "termuxSetupCompleted",
            defaults.termuxSetupCompleted,
        ),
        termuxSetupNoticeDismissed = value.booleanValueOrDefault(
            "termuxSetupNoticeDismissed",
            defaults.termuxSetupNoticeDismissed,
        ),
        termuxEnvironmentVariables = parseImportedTermuxEnvironmentVariables(
            value["termuxEnvironmentVariables"] as? JsonArray,
        ),
        enabledRuntimeIds = (value["enabledRuntimeIds"] as? JsonArray).orEmpty()
            .mapNotNull { element ->
                LocalRuntimeId.fromStorage((element as? JsonPrimitive)?.contentOrNull)
            }
            .toSet(),
        defaultRuntimeId = LocalRuntimeId.fromStorage(value.stringValue("defaultRuntimeId")),
        alpineSetupCompleted = value.booleanValueOrDefault(
            "alpineSetupCompleted",
            defaults.alpineSetupCompleted,
        ),
        alpinePackageProfiles = parseImportedPackageProfileStates(
            value["alpinePackageProfiles"] as? JsonArray,
        ),
        alpineEnvironmentVariables = parseImportedAlpineEnvironmentVariables(
            value["alpineEnvironmentVariables"] as? JsonArray,
        ),
        phoneUseInputStrategy = PhoneUseInputStrategy.fromStorage(value.stringValue("phoneUseInputStrategy")),
        phoneUseOutputStrategy = PhoneUseOutputStrategy.fromStorage(value.stringValue("phoneUseOutputStrategy")),
        phoneUseAllowStrategySwitch = value.booleanValueOrDefault("phoneUseAllowStrategySwitch", true),
        phoneUseVirtualDisplayEnabled = value.booleanValueOrDefault("phoneUseVirtualDisplayEnabled", false),
        agentModeDisplayOrientation = AgentModeDisplayOrientation.fromStorage(value.stringValue("agentModeDisplayOrientation")),
        agentModeAuthorizationEnabled = value.booleanValueOrDefault(
            "agentModeAuthorizationEnabled",
            defaults.agentModeAuthorizationEnabled,
        ),
        agentModeAuthorizationMethod = AgentModeAuthorizationMethod.fromStorage(
            value.stringValue("agentModeAuthorizationMethod"),
            defaults.agentModeAuthorizationMethod,
        ),
        language = AppLanguage.fromStorage(value.stringValue("language"), defaults.language),
        themeMode = AppThemeMode.fromStorage(value.stringValue("themeMode")),
        defaultChatModelKey = value.stringValueOrDefault(
            "defaultChatModelKey",
            defaults.defaultChatModelKey,
        ),
        defaultTitleModelKey = value.stringValueOrDefault(
            "defaultTitleModelKey",
            defaults.defaultTitleModelKey,
        ),
        defaultNamingModelKey = value.stringValueOrDefault(
            "defaultNamingModelKey",
            defaults.defaultNamingModelKey,
        ),
        defaultCompactingModelKey = value.stringValueOrDefault(
            "defaultCompactingModelKey",
            defaults.defaultCompactingModelKey,
        ),
        defaultSelectedSkillIds = (value["defaultSelectedSkillIds"] as? JsonArray)
            .toTrimmedStringList(),
        onboardingSeenVersion = value.intValueOrDefault(
            "onboardingSeenVersion",
            defaults.onboardingSeenVersion,
        ),
        onboardingCompletedVersion = value.intValueOrDefault(
            "onboardingCompletedVersion",
            defaults.onboardingCompletedVersion,
        ),
        privacyPolicyAccepted = value.booleanValueOrDefault(
            "privacyPolicyAccepted",
            defaults.privacyPolicyAccepted,
        ),
        lastUpdateCheckAtMillis = value.longValueOrDefault(
            "lastUpdateCheckAtMillis",
            defaults.lastUpdateCheckAtMillis,
        ),
    )
}

private val ImportedEnvironmentVariableNamePattern = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

private fun parseImportedTermuxEnvironmentVariables(value: JsonArray?): List<TermuxEnvironmentVariable> =
    value.orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val name = item.stringValue("name").trim()
        if (!ImportedEnvironmentVariableNamePattern.matches(name)) return@mapNotNull null
        TermuxEnvironmentVariable(name = name, value = item.stringValue("value"))
    }.distinctBy(TermuxEnvironmentVariable::name)

private fun parseImportedAlpineEnvironmentVariables(value: JsonArray?): List<AlpineEnvironmentVariable> =
    value.orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val name = item.stringValue("name").trim()
        if (!ImportedEnvironmentVariableNamePattern.matches(name)) return@mapNotNull null
        AlpineEnvironmentVariable(name = name, value = item.stringValue("value"))
    }.distinctBy(AlpineEnvironmentVariable::name)

private fun parseImportedPackageProfileStates(value: JsonArray?): Map<String, PackageProfileState> =
    buildMap {
        value.orEmpty().forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val profileId = item.stringValue("profileId").trim()
            if (profileId.isBlank()) return@forEach
            put(
                profileId,
                PackageProfileState(
                    profileId = profileId,
                    installed = item.booleanValueOrDefault("installed", false),
                    installedAtMillis = item.longValueOrDefault("installedAtMillis", 0L),
                    lastError = item.stringValue("lastError"),
                ),
            )
        }
    }

private fun JsonObject.stringValue(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.stringValueOrDefault(name: String, fallback: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull ?: fallback

private fun JsonObject.booleanValueOrDefault(name: String, fallback: Boolean): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: fallback

private fun JsonObject.intValueOrDefault(name: String, fallback: Int): Int =
    (this[name] as? JsonPrimitive)?.intOrNull ?: fallback

private fun JsonObject.longValueOrDefault(name: String, fallback: Long): Long =
    (this[name] as? JsonPrimitive)?.longOrNull ?: fallback

private fun JsonArray?.toTrimmedStringList(): List<String> = orEmpty().mapNotNull { element ->
    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonArray.usesAndroidChatSchema(): Boolean = any { session ->
    val sessionObject = session as? JsonObject ?: return@any true
    if ("agentModeEnabled" in sessionObject || "activeSkillsJson" in sessionObject) return@any true
    val messagesValue = sessionObject["messages"] ?: return@any false
    if (messagesValue !is JsonArray) return@any true
    messagesValue.any { message ->
        message !is JsonObject || "author" in message
    }
}
