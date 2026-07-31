package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.SharedChatHistoryStore
import com.zhousl.aether.data.chatdb.resolveSharedCurrentSessionId
import com.zhousl.aether.data.chatdb.decodeAndroidChatSessions
import com.zhousl.aether.data.chatdb.encodeAndroidChatSessions
import com.zhousl.aether.data.pi.SharedMcpManager
import com.zhousl.aether.data.pi.SharedMcpServerConfig
import com.zhousl.aether.data.pi.SharedMcpTransport
import com.zhousl.aether.data.pi.parseSharedMcpServers
import com.zhousl.aether.data.pi.serializeSharedMcpServers
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException

const val SharedAppDataSchemaVersion = 2

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
    val mcpServers: JsonArray,
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
    private val mcpManager: SharedMcpManager,
) {
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
        return SharedAppDataArchive(
            exportedAtMillis = platformCurrentTimeMillis(),
            settings = settings,
            providerConfigs = Json.parseToJsonElement(
                serializeProviderConfigs(persisted.providerConfigs),
            ).jsonArray,
            activeProviderConfigId = persisted.activeProviderConfigId,
            sessions = historyStore.loadAll().map { it.copy(activeSkills = emptyList()) },
            currentSessionId = historyStore.loadCurrentSessionId(),
            skillBundles = skillManager.exportBundles(),
            mcpServers = Json.parseToJsonElement(
                serializeSharedMcpServers(mcpManager.loadServers()),
            ).jsonArray,
        )
    }

    private suspend fun applyArchive(archive: SharedAppDataArchive): SharedAppDataRestoreResult {
        val decoded = validateSharedAppDataArchive(archive)
        val installedSkills = skillManager.replaceBundles(archive.skillBundles)
        val enabledSkillIds = installedSkills
            .filter(SharedInstalledSkill::isEnabled)
            .map(SharedInstalledSkill::id)
            .toSet()
        val enabledMcpServerIds = decoded.mcpServers
            .filter(SharedMcpServerConfig::enabled)
            .map(SharedMcpServerConfig::id)
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
                activeMcpServerIds = session.activeMcpServerIds.filter(enabledMcpServerIds::contains),
            )
        }
        val currentSessionId = resolveSharedCurrentSessionId(
            currentSessionId = archive.currentSessionId,
            sessionIds = sessions.map(PersistedChatSession::id),
        )
        historyStore.replaceAll(sessions, currentSessionId)
        mcpManager.saveServers(decoded.mcpServers)
        try {
            val activeMcpServerIds = sessions
                .firstOrNull { it.id == currentSessionId }
                ?.activeMcpServerIds
                .orEmpty()
                .toSet()
            mcpManager.refreshBindings(
                decoded.mcpServers.filter { it.enabled && it.id in activeMcpServerIds },
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Restored configuration remains valid even when a server is temporarily unavailable.
        }

        return SharedAppDataRestoreResult(
            persistedSettings = persisted,
            sessions = sessions,
            currentSessionId = currentSessionId,
            installedSkills = installedSkills,
            mcpServers = decoded.mcpServers,
        )
    }
}

private data class ValidatedSharedAppData(
    val providerConfigs: List<LlmProviderConfig>,
    val activeProviderConfigId: String,
    val mcpServers: List<SharedMcpServerConfig>,
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
        put("mcpServers", encodeAndroidMcpServers(validated.mcpServers))
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
    )
    validateSharedAppDataArchive(archive)
    return archive
}

private fun validateSharedAppDataArchive(archive: SharedAppDataArchive): ValidatedSharedAppData {
    val providerConfigs = parseProviderConfigs(archive.providerConfigs.toString())
    val activeProviderConfigId = archive.activeProviderConfigId
        .ifBlank { archive.settings.providerConfigId }

    val mcpServers = parseImportedMcpServers(archive.mcpServers)

    validateSharedSkillBundles(archive.skillBundles)
    return ValidatedSharedAppData(
        providerConfigs = providerConfigs,
        activeProviderConfigId = activeProviderConfigId,
        mcpServers = mcpServers,
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
    put("userAgent", normalizeLlmUserAgent(userAgent))
    put("customHeaders", buildJsonArray {
        customHeaders.forEach { header ->
            add(buildJsonObject { put("name", header.name); put("value", header.value) })
        }
    })
    put("reasoningEffort", normalizeReasoningEffort(reasoningEffort))
    put("systemPrompt", systemPrompt)
    put("tavilyApiKey", tavilyApiKey)
    put("tavilyBaseUrl", normalizeTavilyBaseUrl(tavilyBaseUrl))
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
    put("termuxLiveOutputEnabled", termuxLiveOutputEnabled)
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
    put("autoCleanOldCommandHistory", autoCleanOldCommandHistory)
    put("oldCommandHistoryRetentionHours", oldCommandHistoryRetentionHours)
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
        customHeaders = parseCustomHeaders(value["customHeaders"] as? JsonArray),
        reasoningEffort = normalizeReasoningEffort(
            value.stringValueOrDefault("reasoningEffort", defaults.reasoningEffort),
        ),
        systemPrompt = value.stringValueOrDefault("systemPrompt", defaults.systemPrompt),
        tavilyApiKey = value.stringValueOrDefault("tavilyApiKey", defaults.tavilyApiKey),
        tavilyBaseUrl = normalizeTavilyBaseUrl(
            value.stringValueOrDefault("tavilyBaseUrl", defaults.tavilyBaseUrl),
        ),
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
        termuxLiveOutputEnabled = value.booleanValueOrDefault(
            "termuxLiveOutputEnabled",
            defaults.termuxLiveOutputEnabled,
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

private fun JsonArray.usesAndroidChatSchema(): Boolean = any { session ->
    val sessionObject = session as? JsonObject ?: return@any true
    if ("agentModeEnabled" in sessionObject || "activeSkillsJson" in sessionObject) return@any true
    val messagesValue = sessionObject["messages"] ?: return@any false
    if (messagesValue !is JsonArray) return@any true
    messagesValue.any { message ->
        message !is JsonObject || "author" in message
    }
}

private fun parseImportedMcpServers(value: JsonArray): List<SharedMcpServerConfig> =
    value.mapIndexedNotNull { index, element ->
        val item = element as? JsonObject ?: return@mapIndexedNotNull null
        val transportObject = item["transport"] as? JsonObject
        if (transportObject == null) {
            if ((item["transport"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) {
                return@mapIndexedNotNull null
            }
            return@mapIndexedNotNull parseSharedMcpServers(JsonArray(listOf(item)).toString()).firstOrNull()
        }
        val transport = if (transportObject.stringValue("type") == "stdio") {
            SharedMcpTransport.Stdio
        } else {
            SharedMcpTransport.Http
        }
        val name = item.stringValue("displayName")
        val quickActionSource = if (transport == SharedMcpTransport.Stdio) {
            transportObject.stringValue("command")
        } else {
            transportObject.stringValue("url")
        }
        val createdAt = (item["createdAtMillis"] as? JsonPrimitive)?.longOrNull
            ?: platformCurrentTimeMillis()
        SharedMcpServerConfig(
            id = item.stringValue("id").ifBlank { "mcp-$index" },
            name = name,
            actionLabel = item.stringValue("actionLabel")
                .ifBlank { generateSharedQuickActionLabel(name, quickActionSource) },
            transport = transport,
            url = transportObject.stringValue("url"),
            command = transportObject.stringValue("command"),
            arguments = ((transportObject["arguments"] ?: transportObject["args"]) as? JsonArray)
                .toTrimmedStringList(),
            headers = (transportObject["headers"] as? JsonArray).toKeyValueMap(),
            workingDirectory = transportObject.stringValue("workingDirectory"),
            environment = (transportObject["environment"] as? JsonArray).toKeyValueMap(),
            runtimeEnvironment = transportObject.stringValue("runtimeEnvironment")
                .ifBlank { transportObject.stringValue("runtime_environment") }
                .ifBlank { "default" },
            connectTimeoutMillis = (item["connectTimeoutMillis"] as? JsonPrimitive)?.longOrNull ?: 15_000L,
            requestTimeoutMillis = (item["requestTimeoutMillis"] as? JsonPrimitive)?.longOrNull ?: 60_000L,
            enabled = ((item["isEnabled"] as? JsonPrimitive)?.booleanOrNull ?: true) &&
                ((item["isTrusted"] as? JsonPrimitive)?.booleanOrNull ?: true),
            createdAtMillis = createdAt,
            updatedAtMillis = (item["updatedAtMillis"] as? JsonPrimitive)?.longOrNull ?: createdAt,
        )
    }

private fun JsonArray?.toTrimmedStringList(): List<String> = orEmpty().mapNotNull { element ->
    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonArray?.toKeyValueMap(): Map<String, String> = orEmpty().mapNotNull { element ->
    val item = element as? JsonObject ?: return@mapNotNull null
    item.stringValue("key").trim().takeIf(String::isNotEmpty)?.let { key ->
        key to item.stringValue("value")
    }
}.toMap()

private fun encodeAndroidMcpServers(servers: List<SharedMcpServerConfig>): JsonArray = buildJsonArray {
    servers.forEach { server ->
        add(buildJsonObject {
            put("id", server.id)
            put("displayName", server.name)
            put("actionLabel", server.actionLabel)
            put("transport", buildJsonObject {
                when (server.transport) {
                    SharedMcpTransport.Stdio -> {
                        put("type", "stdio")
                        put("command", server.command)
                        put("arguments", buildJsonArray { server.arguments.forEach { add(JsonPrimitive(it)) } })
                        put("workingDirectory", server.workingDirectory)
                        put("environment", buildJsonArray {
                            server.environment.forEach { (key, value) ->
                                add(buildJsonObject { put("key", key); put("value", value) })
                            }
                        })
                        server.runtimeEnvironment.takeUnless { it == "default" || it.isBlank() }
                            ?.let { put("runtimeEnvironment", it) }
                    }
                    SharedMcpTransport.Http -> {
                        put("type", "streamable_http")
                        put("url", server.url)
                        put("headers", buildJsonArray {
                            server.headers.forEach { (key, value) ->
                                add(buildJsonObject { put("key", key); put("value", value) })
                            }
                        })
                    }
                }
            })
            put("isEnabled", server.enabled)
            put("connectTimeoutMillis", server.connectTimeoutMillis)
            put("requestTimeoutMillis", server.requestTimeoutMillis)
            put("createdAtMillis", server.createdAtMillis)
            put("updatedAtMillis", server.updatedAtMillis)
        })
    }
}
