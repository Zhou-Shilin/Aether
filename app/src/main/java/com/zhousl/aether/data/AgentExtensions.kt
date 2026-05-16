package com.zhousl.aether.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class AgentExtensionsState(
    val installedSkills: List<InstalledSkill> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
)

data class InstalledSkill(
    val id: String,
    val name: String,
    val description: String,
    val actionLabel: String = "",
    val license: String = "",
    val compatibility: String = "",
    val metadataJson: String = "{}",
    val allowedTools: List<String> = emptyList(),
    val skillRootPath: String,
    val skillMdPath: String,
    val source: SkillInstallSource = SkillInstallSource(),
    val checksumSha256: String = "",
    val isEnabled: Boolean = true,
    val installedAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = installedAtMillis,
    val diagnostics: List<String> = emptyList(),
    val resourceEntries: List<SkillResourceEntry> = emptyList(),
)

data class SkillInstallSource(
    val kind: SkillInstallKind = SkillInstallKind.Unknown,
    val label: String = "",
    val uri: String = "",
    val ref: String = "",
    val subpath: String = "",
)

enum class SkillInstallKind(
    val storageValue: String,
) {
    Unknown("unknown"),
    DocumentTree("document_tree"),
    ZipUri("zip_uri"),
    RemoteZip("remote_zip"),
    GitHub("github");

    companion object {
        fun fromStorage(value: String?): SkillInstallKind =
            entries.firstOrNull { it.storageValue == value } ?: Unknown
    }
}

data class SkillResourceEntry(
    val relativePath: String,
    val kind: SkillResourceKind,
)

enum class SkillResourceKind(
    val storageValue: String,
) {
    Skill("skill"),
    Script("script"),
    Reference("reference"),
    Asset("asset"),
    AgentMetadata("agent_metadata"),
    Other("other");

    companion object {
        fun fromRelativePath(relativePath: String): SkillResourceKind = when {
            relativePath.equals("SKILL.md", ignoreCase = true) -> Skill
            relativePath.startsWith("scripts/") -> Script
            relativePath.startsWith("references/") -> Reference
            relativePath.startsWith("assets/") -> Asset
            relativePath.startsWith("agents/") -> AgentMetadata
            else -> Other
        }

        fun fromStorage(value: String?): SkillResourceKind =
            entries.firstOrNull { it.storageValue == value } ?: Other
    }
}

data class ActiveSkillContext(
    val skillId: String,
    val name: String,
    val description: String,
    val compatibility: String = "",
    val allowedTools: List<String> = emptyList(),
    val skillRootPath: String,
    val bodyMarkdown: String,
    val resourceEntries: List<SkillResourceEntry> = emptyList(),
    val activatedAtMillis: Long = System.currentTimeMillis(),
)

data class McpServerConfig(
    val id: String,
    val displayName: String,
    val actionLabel: String = "",
    val transport: McpTransportConfig,
    val isEnabled: Boolean = true,
    val connectTimeoutMillis: Long = 15_000L,
    val requestTimeoutMillis: Long = 60_000L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

sealed interface McpTransportConfig {
    val transportType: McpTransportType

    data class StdIo(
        val command: String,
        val arguments: List<String> = emptyList(),
        val workingDirectory: String = "",
        val environment: List<McpKeyValue> = emptyList(),
    ) : McpTransportConfig {
        override val transportType: McpTransportType = McpTransportType.StdIo
    }

    data class StreamableHttp(
        val url: String,
        val headers: List<McpKeyValue> = emptyList(),
    ) : McpTransportConfig {
        override val transportType: McpTransportType = McpTransportType.StreamableHttp
    }
}

enum class McpTransportType(
    val storageValue: String,
) {
    StdIo("stdio"),
    StreamableHttp("streamable_http");

    companion object {
        fun fromStorage(value: String?): McpTransportType =
            entries.firstOrNull { it.storageValue == value } ?: StreamableHttp
    }
}

data class McpKeyValue(
    val key: String,
    val value: String,
)

internal fun InstalledSkill.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("description", description)
    put("actionLabel", actionLabel)
    put("license", license)
    put("compatibility", compatibility)
    put("metadataJson", metadataJson)
    put("allowedTools", JSONArray().apply { allowedTools.forEach(::put) })
    put("skillRootPath", skillRootPath)
    put("skillMdPath", skillMdPath)
    put("source", source.toJson())
    put("checksumSha256", checksumSha256)
    put("isEnabled", isEnabled)
    put("installedAtMillis", installedAtMillis)
    put("updatedAtMillis", updatedAtMillis)
    put("diagnostics", JSONArray().apply { diagnostics.forEach(::put) })
    put("resourceEntries", JSONArray().apply { resourceEntries.forEach { put(it.toJson()) } })
}

internal fun ActiveSkillContext.toJson(): JSONObject = JSONObject().apply {
    put("skillId", skillId)
    put("name", name)
    put("description", description)
    put("compatibility", compatibility)
    put("allowedTools", JSONArray().apply { allowedTools.forEach(::put) })
    put("skillRootPath", skillRootPath)
    put("bodyMarkdown", bodyMarkdown)
    put("activatedAtMillis", activatedAtMillis)
    put("resourceEntries", JSONArray().apply { resourceEntries.forEach { put(it.toJson()) } })
}

internal fun SkillInstallSource.toJson(): JSONObject = JSONObject().apply {
    put("kind", kind.storageValue)
    put("label", label)
    put("uri", uri)
    put("ref", ref)
    put("subpath", subpath)
}

internal fun SkillResourceEntry.toJson(): JSONObject = JSONObject().apply {
    put("relativePath", relativePath)
    put("kind", kind.storageValue)
}

internal fun McpServerConfig.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("displayName", displayName)
    put("actionLabel", actionLabel)
    put("transport", transport.toJson())
    put("isEnabled", isEnabled)
    put("connectTimeoutMillis", connectTimeoutMillis)
    put("requestTimeoutMillis", requestTimeoutMillis)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

internal fun McpTransportConfig.toJson(): JSONObject = JSONObject().apply {
    put("type", transportType.storageValue)
    when (this@toJson) {
        is McpTransportConfig.StdIo -> {
            put("command", command)
            put("arguments", JSONArray().apply { arguments.forEach(::put) })
            put("workingDirectory", workingDirectory)
            put("environment", JSONArray().apply { environment.forEach { put(it.toJson()) } })
        }

        is McpTransportConfig.StreamableHttp -> {
            put("url", url)
            put("headers", JSONArray().apply { headers.forEach { put(it.toJson()) } })
        }
    }
}

internal fun McpKeyValue.toJson(): JSONObject = JSONObject().apply {
    put("key", key)
    put("value", value)
}

internal fun parseInstalledSkills(rawValue: String): List<InstalledSkill> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    run {
                        val name = json.optString("name")
                        val description = json.optString("description")
                        val legacyEnabled = json.optBoolean("isEnabled", true)
                        val legacyTrusted = if (json.has("isTrusted")) {
                            json.optBoolean("isTrusted", false)
                        } else {
                            true
                        }
                        InstalledSkill(
                            id = json.optString("id").ifBlank { "skill-$index" },
                            name = name,
                            description = description,
                            actionLabel = json.optString("actionLabel")
                                .ifBlank { generateQuickActionLabel(name, description) },
                            license = json.optString("license"),
                            compatibility = json.optString("compatibility"),
                            metadataJson = json.optString("metadataJson").ifBlank { "{}" },
                            allowedTools = json.optJSONArray("allowedTools").toStringList(),
                            skillRootPath = json.optString("skillRootPath"),
                            skillMdPath = json.optString("skillMdPath"),
                            source = parseSkillInstallSource(json.optJSONObject("source")),
                            checksumSha256 = json.optString("checksumSha256"),
                            isEnabled = legacyEnabled && legacyTrusted,
                            installedAtMillis = json.optLong("installedAtMillis", System.currentTimeMillis()),
                            updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                            diagnostics = json.optJSONArray("diagnostics").toStringList(),
                            resourceEntries = parseSkillResourceEntries(json.optJSONArray("resourceEntries")),
                        )
                    }
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun parseActiveSkillContexts(rawValue: String): List<ActiveSkillContext> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    ActiveSkillContext(
                        skillId = json.optString("skillId").ifBlank { "skill-$index" },
                        name = json.optString("name"),
                        description = json.optString("description"),
                        compatibility = json.optString("compatibility"),
                        allowedTools = json.optJSONArray("allowedTools").toStringList(),
                        skillRootPath = json.optString("skillRootPath"),
                        bodyMarkdown = json.optString("bodyMarkdown"),
                        resourceEntries = parseSkillResourceEntries(json.optJSONArray("resourceEntries")),
                        activatedAtMillis = json.optLong("activatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun parseMcpServerConfigs(rawValue: String): List<McpServerConfig> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val transportJson = json.optJSONObject("transport") ?: continue
                val transport = parseMcpTransportConfig(transportJson) ?: continue
                val displayName = json.optString("displayName")
                val legacyEnabled = json.optBoolean("isEnabled", true)
                val legacyTrusted = if (json.has("isTrusted")) {
                    json.optBoolean("isTrusted", false)
                } else {
                    true
                }
                add(
                    McpServerConfig(
                        id = json.optString("id").ifBlank { "mcp-$index" },
                        displayName = displayName,
                        actionLabel = json.optString("actionLabel")
                            .ifBlank { generateQuickActionLabel(displayName, transport.quickActionSource()) },
                        transport = transport,
                        isEnabled = legacyEnabled && legacyTrusted,
                        connectTimeoutMillis = json.optLong("connectTimeoutMillis", 15_000L),
                        requestTimeoutMillis = json.optLong("requestTimeoutMillis", 60_000L),
                        createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun serializeInstalledSkills(skills: List<InstalledSkill>): String =
    JSONArray().apply { skills.forEach { put(it.toJson()) } }.toString()

internal fun serializeActiveSkillContexts(activeSkills: List<ActiveSkillContext>): String =
    JSONArray().apply { activeSkills.forEach { put(it.toJson()) } }.toString()

internal fun serializeMcpServerConfigs(servers: List<McpServerConfig>): String =
    JSONArray().apply { servers.forEach { put(it.toJson()) } }.toString()

private fun parseSkillInstallSource(json: JSONObject?): SkillInstallSource =
    SkillInstallSource(
        kind = SkillInstallKind.fromStorage(json?.optString("kind")),
        label = json?.optString("label").orEmpty(),
        uri = json?.optString("uri").orEmpty(),
        ref = json?.optString("ref").orEmpty(),
        subpath = json?.optString("subpath").orEmpty(),
    )

private fun parseSkillResourceEntries(array: JSONArray?): List<SkillResourceEntry> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val relativePath = json.optString("relativePath")
            if (relativePath.isBlank()) continue
            add(
                SkillResourceEntry(
                    relativePath = relativePath,
                    kind = SkillResourceKind.fromStorage(json.optString("kind")),
                )
            )
        }
    }
}

private fun parseMcpTransportConfig(json: JSONObject): McpTransportConfig? =
    when (McpTransportType.fromStorage(json.optString("type"))) {
        McpTransportType.StdIo -> McpTransportConfig.StdIo(
            command = json.optString("command"),
            arguments = json.optJSONArray("arguments").toStringList()
                .ifEmpty { json.optJSONArray("args").toStringList() },
            workingDirectory = json.optString("workingDirectory"),
            environment = parseKeyValues(json.optJSONArray("environment")),
        )

        McpTransportType.StreamableHttp -> McpTransportConfig.StreamableHttp(
            url = json.optString("url"),
            headers = parseKeyValues(json.optJSONArray("headers")),
        )
    }

private fun parseKeyValues(array: JSONArray?): List<McpKeyValue> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val key = json.optString("key").trim()
            if (key.isEmpty()) continue
            add(McpKeyValue(key = key, value = json.optString("value")))
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

internal fun InstalledSkill.quickActionLabel(): String =
    actionLabel.ifBlank { generateQuickActionLabel(name, description) }

internal fun McpServerConfig.quickActionLabel(): String =
    actionLabel.ifBlank { generateQuickActionLabel(displayName, transport.quickActionSource()) }

internal fun generateQuickActionLabel(
    primaryName: String,
    secondaryDescription: String = "",
): String {
    val combined = listOf(primaryName, secondaryDescription)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .lowercase(Locale.US)
    val keywordMatch = when {
        "skill-creator" in combined || "skill creator" in combined || "create skill" in combined -> "Create Skill"
        Regex("""\bpdf\b""").containsMatchIn(combined) -> "PDF"
        "deep research" in combined -> "Deep Research"
        ("create image" in combined || "imagegen" in combined) -> "Create Image"
        "android" in combined && ("qa" in combined || "test" in combined) -> "Android QA"
        "github" in combined -> "GitHub"
        else -> ""
    }
    if (keywordMatch.isNotBlank()) return keywordMatch

    val cleanedWords = primaryName
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { ActionLabelStopwords.contains(it.lowercase(Locale.US)) }
        .ifEmpty {
            primaryName.split(Regex("[^A-Za-z0-9]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        .take(3)
    if (cleanedWords.isEmpty()) {
        return "Tool"
    }
    return cleanedWords.joinToString(" ") { formatActionWord(it) }
}

private fun McpTransportConfig.quickActionSource(): String = when (this) {
    is McpTransportConfig.StdIo -> command
    is McpTransportConfig.StreamableHttp -> url
}

private fun formatActionWord(word: String): String {
    if (word.length <= 4 && word.all { it.isLetter() }) {
        val upper = word.uppercase(Locale.US)
        if (upper in setOf("PDF", "HTTP", "JSON", "SQL", "QA")) {
            return upper
        }
    }
    return word.lowercase(Locale.US).replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
    }
}

private val ActionLabelStopwords = setOf(
    "agent",
    "skill",
    "skills",
    "server",
    "mcp",
    "plugin",
    "tool",
    "tools",
    "extension",
    "extensions",
    "assistant",
    "app",
)
