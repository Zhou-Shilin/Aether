package com.zhousl.aether.data

import com.zhousl.aether.data.pi.RuntimeHostToolExecutor
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SharedInstalledSkill(
    val id: String,
    val name: String,
    val description: String,
    val actionLabel: String = "",
    val guestPath: String,
    val isEnabled: Boolean = true,
    val compatibility: String = "",
    val license: String = "",
    val allowedTools: List<String> = emptyList(),
    val resourceCount: Int = 0,
    val source: String = "",
)

@Serializable
data class SharedSkillResourceEntry(
    val relativePath: String,
    val kind: String,
)

@Serializable
data class SharedActiveSkillContext(
    val skillId: String,
    val name: String,
    val description: String,
    val compatibility: String = "",
    val allowedTools: List<String> = emptyList(),
    val skillRootPath: String,
    val bodyMarkdown: String,
    val resourceEntries: List<SharedSkillResourceEntry> = emptyList(),
    val activatedAtMillis: Long = platformCurrentTimeMillis(),
)

@Serializable
data class SharedSkillBundleFile(
    val path: String,
    val dataBase64: String,
)

data class SharedSkillDirectoryEntry(
    val relativePath: String,
    val bytes: ByteArray,
)

@Serializable
data class SharedSkillBundle(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String = id,
    val actionLabel: String = "",
    val isEnabled: Boolean = true,
    val installedAtMillis: Long = 0,
    val source: SharedSkillBundleSource = SharedSkillBundleSource(),
    val files: List<SharedSkillBundleFile>,
)

@Serializable
data class SharedSkillBundleSource(
    val kind: String = "unknown",
    val label: String = "",
    val uri: String = "",
    val ref: String = "",
    val subpath: String = "",
)

class SharedSkillManager(
    private val runtime: MultiplatformLocalRuntime,
) {
    private val executor = RuntimeHostToolExecutor(runtime)
    private val storageRoot = "${runtime.workspaceRoot.trimEnd('/')}/.aether"
    private val legacyStorageRoot = "${runtime.homeDirectory.trimEnd('/')}/.aether"
    private val skillsRoot = "$storageRoot/skills"
    private val statePath = "$storageRoot/skills-state.json"
    private val sourcesPath = "$storageRoot/skills-sources.json"
    private val storageMigrationMutex = Mutex()

    suspend fun list(): List<SharedInstalledSkill> {
        ensureStorageReady()
        runtime.fileSystem.createDirectories(skillsRoot)
        val enabledState = readEnabledState()
        val sourceState = readStringState(sourcesPath)
        val result = bash("find ${quote(skillsRoot)} -mindepth 2 -maxdepth 2 -name SKILL.md -type f -print | sort")
        if (result.isError) return emptyList()
        return buildList {
            for (path in result.stdout().lineSequence().map(String::trim).filter(String::isNotBlank)) {
                val skill = runCatching {
                    val markdown = runtime.fileSystem.read(path).decodeToString()
                    val metadata = validateSharedSkillDocument(markdown)
                    val root = path.substringBeforeLast('/')
                    val resourceCount = bash("find ${quote(root)} -type f | wc -l").stdout().trim().toIntOrNull() ?: 0
                    SharedInstalledSkill(
                        id = root.substringAfterLast('/'),
                        name = metadata.name,
                        description = metadata.description,
                        actionLabel = generateSharedQuickActionLabel(
                            metadata.name.ifBlank { root.substringAfterLast('/') },
                            metadata.description,
                        ),
                        guestPath = root,
                        isEnabled = enabledState[root.substringAfterLast('/')] ?: true,
                        compatibility = metadata.compatibility,
                        license = metadata.license,
                        allowedTools = metadata.allowedTools,
                        resourceCount = resourceCount,
                        source = sourceState[root.substringAfterLast('/')].orEmpty(),
                    )
                }.getOrNull()
                if (skill != null) add(skill)
            }
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun exportBundles(): List<SharedSkillBundle> = list()
        .sortedBy { it.name.lowercase() }
        .map { skill ->
            var totalBytes = 0L
            val result = bash("find ${quote(skill.guestPath)} -type f -print0 | sort -z")
            check(!result.isError) { result.errorText().ifBlank { "Unable to enumerate Skill files." } }
            val files = result.stdout()
                .split('\u0000')
                .filter(String::isNotBlank)
                .mapIndexed { index, path ->
                    require(index < MaxSharedSkillBundleEntries) { "Skill contains too many bundled files." }
                    val relativePath = path.removePrefix("${skill.guestPath.trimEnd('/')}/")
                    require(path != relativePath) { "Skill file escaped its Skill directory." }
                    validateSharedSkillBundlePath(relativePath)
                    val bytes = runtime.fileSystem.read(path)
                    require(bytes.size.toLong() <= MaxSharedSkillBundleEntryBytes) {
                        "Skill file is too large: $relativePath"
                    }
                    totalBytes += bytes.size
                    require(totalBytes <= MaxSharedSkillBundleBytes) { "Skill bundle is too large." }
                    SharedSkillBundleFile(relativePath, Base64.encode(bytes))
                }
            require(files.any { it.path == "SKILL.md" }) { "Skill does not contain SKILL.md." }
            SharedSkillBundle(
                id = skill.id,
                name = skill.name,
                actionLabel = skill.actionLabel.ifBlank {
                    generateSharedQuickActionLabel(skill.name, skill.description)
                },
                isEnabled = skill.isEnabled,
                source = skill.source.toSharedSkillBundleSource(),
                files = files,
            )
        }

    suspend fun replaceBundles(bundles: List<SharedSkillBundle>): List<SharedInstalledSkill> {
        ensureStorageReady()
        validateSharedSkillBundles(bundles)
        val staging = "$storageRoot/skills-restore-${platformRandomUuid()}"
        val previous = "$storageRoot/skills-previous-${platformRandomUuid()}"
        runtime.fileSystem.remove(staging, recursive = true)
        runtime.fileSystem.createDirectories(staging)
        var movedIntoPlace = false
        try {
            bundles.forEach { bundle ->
                bundle.files.forEach { file ->
                    val output = "$staging/${bundle.id}/${file.path}"
                    runtime.fileSystem.createDirectories(output.substringBeforeLast('/'))
                    runtime.fileSystem.write(output, Base64.decode(file.dataBase64))
                }
            }
            val result = bash(
                """
                set -eu
                if [ -e ${quote(previous)} ]; then rm -rf ${quote(previous)}; fi
                if [ -e ${quote(skillsRoot)} ]; then mv ${quote(skillsRoot)} ${quote(previous)}; fi
                if mv ${quote(staging)} ${quote(skillsRoot)}; then
                    rm -rf ${quote(previous)}
                else
                    if [ -e ${quote(previous)} ]; then mv ${quote(previous)} ${quote(skillsRoot)}; fi
                    exit 1
                fi
                """.trimIndent(),
            )
            check(!result.isError) { result.errorText().ifBlank { "Unable to replace installed Skills." } }
            movedIntoPlace = true
            writeEnabledState(bundles.associate { it.id to it.isEnabled })
            writeStringState(
                sourcesPath,
                bundles.mapNotNull { bundle ->
                    bundle.source.label.ifBlank { bundle.source.uri }
                        .takeIf(String::isNotBlank)
                        ?.let { bundle.id to it }
                }.toMap(),
            )
            return list()
        } finally {
            if (!movedIntoPlace) runCatching { runtime.fileSystem.remove(staging, recursive = true) }
        }
    }

    suspend fun installDirectory(sourcePath: String): SharedInstalledSkill {
        ensureStorageReady()
        runtime.fileSystem.createDirectories(skillsRoot)
        val normalized = sourcePath.trim().trimEnd('/')
        require(normalized.startsWith('/')) { "Skill folder must be an absolute runtime path." }
        require(normalized.split('/').none { it == ".." }) { "Skill folder must not contain '..'." }
        val skillRoot = locateSharedSkillRoot(normalized)
        validateSharedSkillTree(skillRoot)
        val metadata = validateSharedSkillDocument(
            runtime.fileSystem.read("$skillRoot/SKILL.md").decodeToString(),
        )
        val installedId = buildSharedSkillId(metadata.name)
        val result = bash(
            "rm -rf ${quote("$skillsRoot/$installedId")} && " +
                "cp -R ${quote(skillRoot)} ${quote("$skillsRoot/$installedId")}",
        )
        check(!result.isError) { result.errorText().ifBlank { "Unable to install Skill folder." } }
        val installed = list().firstOrNull { it.id == installedId }
        if (installed == null) {
            runtime.fileSystem.remove("$skillsRoot/$installedId", recursive = true)
            error("Installed Skill was not valid.")
        }
        setEnabled(installedId, true)
        setSource(installedId, normalized)
        return list().first { it.id == installedId }.copy(source = normalized)
    }

    suspend fun installDirectoryEntries(
        sourceLabel: String,
        entries: List<SharedSkillDirectoryEntry>,
    ): SharedInstalledSkill {
        ensureStorageReady()
        validateSharedSkillDirectoryEntries(entries)
        val staging = "$storageRoot/skill-directory-import-${platformRandomUuid()}"
        runtime.fileSystem.createDirectories(staging)
        try {
            entries.forEach { entry ->
                val destination = "$staging/${entry.relativePath}"
                destination.substringBeforeLast('/', missingDelimiterValue = staging)
                    .takeIf { it != staging }
                    ?.let { runtime.fileSystem.createDirectories(it) }
                runtime.fileSystem.write(destination, entry.bytes)
            }
            val installed = installDirectory(staging)
            setSource(installed.id, sourceLabel)
            return list().firstOrNull { it.id == installed.id }
                ?: error("Installed Skill was not found.")
        } finally {
            runCatching { runtime.fileSystem.remove(staging, recursive = true) }
        }
    }

    suspend fun installArchive(
        archivePath: String,
        preferredSubpath: String = "",
        sourceLabel: String = archivePath.substringAfterLast('/'),
    ): SharedInstalledSkill {
        ensureStorageReady()
        runtime.fileSystem.createDirectories(skillsRoot)
        require(runtime.fileSystem.read(archivePath).size.toLong() <= MaxSharedSkillArchiveBytes) {
            "Skill archive exceeds the maximum allowed size."
        }
        val staging = "$storageRoot/skill-import-${platformRandomUuid()}"
        val normalizedSubpath = preferredSubpath.trim('/').also { path ->
            require(path.split('/').none { it == ".." }) { "Skill subpath must not contain '..'." }
        }
        runtime.fileSystem.remove(staging, recursive = true)
        runtime.fileSystem.createDirectories(staging)
        try {
            val archiveListing = bash(
                "command -v unzip >/dev/null 2>&1 || apk add --no-cache unzip >/dev/null; " +
                    "unzip -Z1 ${quote(archivePath)}",
            )
            check(!archiveListing.isError) {
                archiveListing.errorText().ifBlank { "Unable to inspect Skill archive." }
            }
            val archiveEntries = archiveListing.stdout().lineSequence()
                .map { it.trimEnd('/') }
                .filter(String::isNotBlank)
                .toList()
            require(archiveEntries.size <= MaxSharedSkillBundleEntries) {
                "Skill archive contains too many entries."
            }
            archiveEntries.forEach(::validateSharedSkillBundlePath)
            val extract = bash(
                "unzip -q ${quote(archivePath)} -d ${quote(staging)}",
            )
            check(!extract.isError) { extract.errorText().ifBlank { "Unable to install Skill archive." } }
            val symlinks = bash("find ${quote(staging)} -type l -print -quit")
            check(!symlinks.isError && symlinks.stdout().isBlank()) {
                "Skill archive contains unsupported symbolic links."
            }
            val skillRoot = locateSharedSkillRoot(staging, normalizedSubpath)
            validateSharedSkillTree(skillRoot)
            val metadata = validateSharedSkillDocument(
                runtime.fileSystem.read("$skillRoot/SKILL.md").decodeToString(),
            )
            val installedId = buildSharedSkillId(metadata.name)
            val copy = bash(
                "rm -rf ${quote("$skillsRoot/$installedId")} && " +
                    "cp -R ${quote(skillRoot)} ${quote("$skillsRoot/$installedId")}",
            )
            check(!copy.isError) { copy.errorText().ifBlank { "Unable to install Skill archive." } }
            val installed = list().firstOrNull { it.id == installedId }
            if (installed == null) {
                runtime.fileSystem.remove("$skillsRoot/$installedId", recursive = true)
                error("Installed Skill was not valid.")
            }
            setEnabled(installedId, true)
            setSource(installedId, sourceLabel)
            return list().first { it.id == installedId }.copy(source = sourceLabel)
        } finally {
            runCatching { runtime.fileSystem.remove(staging, recursive = true) }
        }
    }

    suspend fun installRemote(url: String): SharedInstalledSkill {
        val archive = "${runtime.workspaceRoot.trimEnd('/')}/.aether-skill-${platformRandomUuid()}.zip"
        val source = resolveRemoteSkillSource(url)
        val result = bash(
            "command -v curl >/dev/null 2>&1 || apk add --no-cache curl >/dev/null; " +
                "curl -fL --max-time 90 ${quote(source.downloadUrl)} -o ${quote(archive)}"
        )
        check(!result.isError) { result.errorText().ifBlank { "Unable to download Skill." } }
        return try {
            installArchive(archive, source.subpath, url)
        } finally {
            runtime.fileSystem.remove(archive)
        }
    }

    private suspend fun validateSharedSkillTree(root: String) {
        val listing = bash("find ${quote(root)} -type f -print0 | sort -z")
        check(!listing.isError) { listing.errorText().ifBlank { "Unable to inspect Skill files." } }
        val files = listing.stdout().split('\u0000').filter(String::isNotBlank)
        require(files.size <= MaxSharedSkillBundleEntries) { "Skill contains too many files." }
        var totalBytes = 0L
        files.forEach { path ->
            val relativePath = path.removePrefix("${root.trimEnd('/')}/")
            require(path != relativePath) { "Skill file escaped its Skill directory." }
            validateSharedSkillBundlePath(relativePath)
            val size = runtime.fileSystem.read(path).size.toLong()
            require(size <= MaxSharedSkillBundleEntryBytes) { "Skill file is too large: $relativePath" }
            totalBytes += size
            require(totalBytes <= MaxSharedSkillBundleBytes) { "Skill is too large." }
        }
    }

    suspend fun remove(skillId: String) {
        ensureStorageReady()
        require(skillId.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid Skill ID." }
        runtime.fileSystem.remove("$skillsRoot/$skillId", recursive = true)
        val updated = readEnabledState().toMutableMap().apply { remove(skillId) }
        writeEnabledState(updated)
        val updatedSources = readStringState(sourcesPath).toMutableMap().apply { remove(skillId) }
        writeStringState(sourcesPath, updatedSources)
    }

    suspend fun setEnabled(skillId: String, enabled: Boolean) {
        ensureStorageReady()
        require(skillId.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid Skill ID." }
        require(runtime.fileSystem.exists("$skillsRoot/$skillId/SKILL.md")) { "Installed Skill was not found." }
        val updated = readEnabledState().toMutableMap().apply { put(skillId, enabled) }
        writeEnabledState(updated)
    }

    suspend fun buildActiveSkillContext(skill: SharedInstalledSkill): SharedActiveSkillContext {
        require(skill.isEnabled) { "Installed Skill is disabled." }
        val markdown = runtime.fileSystem.read("${skill.guestPath}/SKILL.md").decodeToString()
        val resources = listSkillResourceEntries(skill.guestPath)
        return SharedActiveSkillContext(
            skillId = skill.id,
            name = skill.name,
            description = skill.description,
            compatibility = skill.compatibility,
            allowedTools = skill.allowedTools,
            skillRootPath = skill.guestPath,
            bodyMarkdown = sharedSkillInstructionBody(markdown),
            resourceEntries = resources,
        )
    }

    suspend fun resolveTurnSkills(
        selectedIds: List<String>,
        requestText: String,
    ): SharedTurnSkillSelection {
        val enabledSkills = list().filter(SharedInstalledSkill::isEnabled)
        val enabledById = enabledSkills.associateBy(SharedInstalledSkill::id)
        val explicit = selectedIds.distinct().mapNotNull { id ->
            enabledById[id]?.let { skill -> runCatching { buildActiveSkillContext(skill) }.getOrNull() }
        }
        val implicit = findImplicitlyRelevantSharedSkills(
            skills = enabledSkills,
            requestText = requestText,
            excludedSkillIds = explicit.map(SharedActiveSkillContext::skillId).toSet(),
        ).mapNotNull { skill -> runCatching { buildActiveSkillContext(skill) }.getOrNull() }
        return SharedTurnSkillSelection(
            selectedSkillIds = explicit.map(SharedActiveSkillContext::skillId),
            activeSkills = (explicit + implicit).distinctBy(SharedActiveSkillContext::skillId),
            availableSkills = enabledSkills.sortedBy { it.name.lowercase() },
        )
    }

    suspend fun buildPrompt(selectedIds: Set<String>): String {
        val contexts = list()
            .filter { it.isEnabled && it.id in selectedIds }
            .mapNotNull { skill -> runCatching { buildActiveSkillContext(skill) }.getOrNull() }
        return renderSharedActiveSkillPrompt(contexts)
    }

    private suspend fun listSkillResourceEntries(skillRoot: String): List<SharedSkillResourceEntry> {
        val normalizedRoot = skillRoot.trimEnd('/')
        val result = bash("find ${quote(normalizedRoot)} -type f -print | sort")
        if (result.isError) return emptyList()
        return result.stdout().lineSequence()
            .map(String::trim)
            .filter { it.startsWith("$normalizedRoot/") }
            .map { it.removePrefix("$normalizedRoot/") }
            .filter(String::isNotBlank)
            .map { path -> SharedSkillResourceEntry(path, sharedSkillResourceKind(path)) }
            .toList()
    }

    private suspend fun ensureStorageReady() {
        storageMigrationMutex.withLock {
            runtime.fileSystem.createDirectories(storageRoot)
            if (legacyStorageRoot == storageRoot) return@withLock

            val legacySkillsRoot = "$legacyStorageRoot/skills"
            if (
                !runtime.fileSystem.exists(skillsRoot) &&
                runtime.fileSystem.exists(legacySkillsRoot)
            ) {
                val result = bash("cp -R ${quote(legacySkillsRoot)} ${quote(skillsRoot)}")
                check(!result.isError) {
                    result.errorText().ifBlank { "Unable to preserve installed Skills." }
                }
                runtime.fileSystem.remove(legacySkillsRoot, recursive = true)
            }

            migrateLegacyStateFile("skills-state.json", statePath)
            migrateLegacyStateFile("skills-sources.json", sourcesPath)
        }
    }

    private suspend fun migrateLegacyStateFile(name: String, destination: String) {
        val legacyPath = "$legacyStorageRoot/$name"
        if (runtime.fileSystem.exists(destination) || !runtime.fileSystem.exists(legacyPath)) return
        runtime.fileSystem.write(destination, runtime.fileSystem.read(legacyPath))
        runtime.fileSystem.remove(legacyPath)
    }

    private suspend fun bash(command: String) = executor.execute(
        "bash",
        buildJsonObject {
            put("command", command)
            put("working_directory", runtime.workspaceRoot)
        },
    )

    private suspend fun locateSharedSkillRoot(
        root: String,
        requestedSubpath: String = "",
    ): String {
        val result = bash("find ${quote(root)} -type f -iname SKILL.md -print")
        check(!result.isError) { result.errorText().ifBlank { "Unable to inspect Skill source." } }
        val candidates = result.stdout().lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.substringBeforeLast('/') }
            .toList()
        require(candidates.isNotEmpty()) { "No SKILL.md file was found in the selected source." }
        val normalizedSubpath = requestedSubpath.trim('/').replace('\\', '/')
        return candidates.firstOrNull { candidate ->
            normalizedSubpath.isNotBlank() &&
                candidate.removePrefix(root.trimEnd('/') + "/").endsWith(normalizedSubpath)
        } ?: candidates.minBy { it.length }
    }

    private suspend fun readEnabledState(): Map<String, Boolean> {
        if (!runtime.fileSystem.exists(statePath)) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(runtime.fileSystem.read(statePath).decodeToString()).jsonObject
                .mapNotNull { (id, value) ->
                    value.jsonPrimitive.contentOrNull?.toBooleanStrictOrNull()?.let { id to it }
                }.toMap()
        }.getOrDefault(emptyMap())
    }

    private suspend fun writeEnabledState(state: Map<String, Boolean>) {
        runtime.fileSystem.createDirectories(statePath.substringBeforeLast('/'))
        runtime.fileSystem.write(
            statePath,
            JsonObject(state.mapValues { JsonPrimitive(it.value) }).toString().encodeToByteArray(),
        )
    }

    private suspend fun setSource(skillId: String, source: String) {
        val updated = readStringState(sourcesPath).toMutableMap().apply { put(skillId, source) }
        writeStringState(sourcesPath, updated)
    }

    private suspend fun readStringState(path: String): Map<String, String> {
        if (!runtime.fileSystem.exists(path)) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(runtime.fileSystem.read(path).decodeToString()).jsonObject
                .mapNotNull { (id, value) -> value.jsonPrimitive.contentOrNull?.let { id to it } }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private suspend fun writeStringState(path: String, state: Map<String, String>) {
        runtime.fileSystem.createDirectories(path.substringBeforeLast('/'))
        runtime.fileSystem.write(
            path,
            JsonObject(state.mapValues { JsonPrimitive(it.value) }).toString().encodeToByteArray(),
        )
    }
}

data class SharedTurnSkillSelection(
    val selectedSkillIds: List<String>,
    val activeSkills: List<SharedActiveSkillContext>,
    val availableSkills: List<SharedInstalledSkill>,
)

internal fun findImplicitlyRelevantSharedSkills(
    skills: List<SharedInstalledSkill>,
    requestText: String,
    excludedSkillIds: Set<String> = emptySet(),
    limit: Int = 2,
): List<SharedInstalledSkill> {
    if (limit <= 0 || requestText.isBlank()) return emptyList()
    return skills.asSequence()
        .filter { it.isEnabled && it.id !in excludedSkillIds }
        .mapNotNull { skill -> scoreSharedImplicitSkillMatch(skill, requestText)?.let { skill to it } }
        .sortedWith(
            compareByDescending<Pair<SharedInstalledSkill, Int>> { it.second }
                .thenBy { it.first.name.lowercase() },
        )
        .take(limit)
        .map(Pair<SharedInstalledSkill, Int>::first)
        .toList()
}

internal fun scoreSharedImplicitSkillMatch(
    skill: SharedInstalledSkill,
    requestText: String,
): Int? {
    val normalizedRequest = normalizeSharedImplicitSkillText(requestText)
    val requestTokens = extractSharedImplicitSkillTokens(requestText)
    if (normalizedRequest.isBlank() && requestTokens.isEmpty()) return null

    val normalizedName = normalizeSharedImplicitSkillText(skill.name)
    val normalizedActionLabel = normalizeSharedImplicitSkillText(
        skill.actionLabel.ifBlank { generateSharedQuickActionLabel(skill.name, skill.description) },
    )
    val phraseMatches = buildSet {
        if (normalizedName.length >= 3 && normalizedRequest.contains(normalizedName)) add(normalizedName)
        if (
            normalizedActionLabel.length >= 3 &&
            normalizedActionLabel != normalizedName &&
            normalizedRequest.contains(normalizedActionLabel)
        ) {
            add(normalizedActionLabel)
        }
    }
    val primaryTokens = extractSharedImplicitSkillTokens("${skill.name} ${skill.actionLabel}")
    val secondaryTokens = extractSharedImplicitSkillTokens(
        buildString {
            append(skill.description)
            append(' ')
            append(skill.compatibility)
            if (skill.allowedTools.isNotEmpty()) {
                append(' ')
                append(skill.allowedTools.joinToString(" "))
            }
        },
    )
    val matchedPrimary = primaryTokens.intersect(requestTokens)
    val matchedSecondary = secondaryTokens.intersect(requestTokens) - matchedPrimary
    val score = phraseMatches.sumOf { if (it == normalizedName) 8 else 6 } +
        matchedPrimary.sumOf(::sharedImplicitPrimaryTokenScore) +
        matchedSecondary.sumOf(::sharedImplicitSecondaryTokenScore)
    if (phraseMatches.isEmpty() && matchedPrimary.size + matchedSecondary.size < 2) return null
    return score.takeIf { it >= 5 }
}

internal fun renderSharedActiveSkillPrompt(activeSkills: List<SharedActiveSkillContext>): String = buildString {
    activeSkills.forEachIndexed { index, skill ->
        if (index > 0) append("\n\n")
        append("<active_skill name=\"")
        append(skill.name.replace("\"", "'"))
        append("\">")
        if (skill.description.isNotBlank()) append("\n<description>${skill.description}</description>")
        if (skill.compatibility.isNotBlank()) append("\n<compatibility>${skill.compatibility}</compatibility>")
        if (skill.allowedTools.isNotEmpty()) {
            append("\n<allowed_tools>")
            skill.allowedTools.forEach { append("\n- $it") }
            append("\n</allowed_tools>")
        }
        append("\n<skill_root>${skill.skillRootPath}</skill_root>")
        if (skill.resourceEntries.isNotEmpty()) {
            append("\n<resources>")
            skill.resourceEntries.forEach { append("\n- ${it.relativePath} (${it.kind})") }
            append("\n</resources>")
            append("\nUse read_skill_resource for only the bundled files needed by the task.")
        }
        append("\n<instructions>\n${skill.bodyMarkdown}\n</instructions>")
        append("\n</active_skill>")
    }
}

private fun sharedSkillInstructionBody(markdown: String): String {
    val lines = markdown.lines()
    if (lines.firstOrNull()?.trim() != "---") return markdown.trim()
    val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return markdown.trim()
    return lines.drop(closingIndex + 1).joinToString("\n").trim()
}

private fun sharedSkillResourceKind(relativePath: String): String = when {
    relativePath.equals("SKILL.md", ignoreCase = true) -> "skill"
    relativePath.startsWith("scripts/") -> "script"
    relativePath.startsWith("references/") -> "reference"
    relativePath.startsWith("assets/") -> "asset"
    relativePath.startsWith("agents/") -> "agent_metadata"
    else -> "other"
}

private fun normalizeSharedImplicitSkillText(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
    .trim()

private fun extractSharedImplicitSkillTokens(value: String): Set<String> =
    Regex("[\\p{L}\\p{Nd}][\\p{L}\\p{Nd}_+.-]*")
        .findAll(value.lowercase())
        .map { it.value.trim('.', '-', '_', '+') }
        .filter { token ->
            token.length >= 2 &&
                token !in SharedImplicitSkillStopwords &&
                token.any(Char::isLetterOrDigit)
        }
        .toSet()

private fun sharedImplicitPrimaryTokenScore(token: String): Int = when {
    token.length >= 8 -> 4
    token.length >= 5 -> 3
    else -> 2
}

private fun sharedImplicitSecondaryTokenScore(token: String): Int = when {
    token.length >= 8 -> 3
    token.length >= 5 -> 2
    else -> 1
}

private val SharedImplicitSkillStopwords = setOf(
    "a", "an", "and", "are", "for", "from", "into", "that", "the", "this", "use", "using",
    "with", "agent", "skill", "skills", "tool", "tools", "task", "tasks", "help", "your",
)

internal fun generateSharedQuickActionLabel(
    primaryName: String,
    secondaryDescription: String = "",
): String {
    val combined = listOf(primaryName, secondaryDescription)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .lowercase()
    val keywordMatch = when {
        "skill-creator" in combined || "skill creator" in combined || "create skill" in combined -> "Create Skill"
        Regex("""\bpdf\b""").containsMatchIn(combined) -> "PDF"
        "deep research" in combined -> "Deep Research"
        "create image" in combined || "imagegen" in combined -> "Create Image"
        "android" in combined && ("qa" in combined || "test" in combined) -> "Android QA"
        "github" in combined -> "GitHub"
        else -> ""
    }
    if (keywordMatch.isNotBlank()) return keywordMatch

    val words = primaryName.split(Regex("[^A-Za-z0-9]+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
    val cleanedWords = words.filterNot { it.lowercase() in SharedActionLabelStopwords }
        .ifEmpty { words }
        .take(3)
    if (cleanedWords.isEmpty()) return "Tool"
    return cleanedWords.joinToString(" ") { word ->
        val upper = word.uppercase()
        if (word.length <= 4 && word.all(Char::isLetter) && upper in SharedActionLabelAcronyms) {
            upper
        } else {
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}

private val SharedActionLabelAcronyms = setOf("PDF", "HTTP", "JSON", "SQL", "QA")
private val SharedActionLabelStopwords = setOf(
    "agent", "skill", "skills", "server", "mcp", "plugin", "tool", "tools",
    "extension", "extensions", "assistant", "app",
)

internal const val MaxSharedSkillBundleBytes = 128L * 1024L * 1024L
internal const val MaxSharedSkillBundleEntryBytes = 16L * 1024L * 1024L
internal const val MaxSharedSkillBundleEntries = 4096
internal const val MaxSharedSkillArchiveBytes = 32L * 1024L * 1024L

internal fun validateSharedSkillBundles(bundles: List<SharedSkillBundle>) {
    require(bundles.map { it.id }.distinct().size == bundles.size) { "Duplicate Skill IDs." }
    bundles.forEach { bundle ->
        var totalBytes = 0L
        require(bundle.schemaVersion == 1) { "Unsupported Skill bundle version." }
        require(bundle.id.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid Skill ID." }
        require(bundle.files.isNotEmpty()) { "Skill bundle did not contain files." }
        require(bundle.files.size <= MaxSharedSkillBundleEntries) { "Skill bundle contains too many files." }
        require(bundle.files.map { it.path }.distinct().size == bundle.files.size) {
            "Skill bundle contains duplicate paths."
        }
        require(bundle.files.any { it.path == "SKILL.md" }) { "Skill bundle does not contain SKILL.md." }
        bundle.files.forEach { file ->
            validateSharedSkillBundlePath(file.path)
            val size = runCatching { Base64.decode(file.dataBase64).size.toLong() }
                .getOrElse { throw IllegalArgumentException("Skill bundle contains invalid base64 data.", it) }
            require(size <= MaxSharedSkillBundleEntryBytes) { "Skill bundle entry is too large." }
            totalBytes += size
            require(totalBytes <= MaxSharedSkillBundleBytes) { "Skill bundles are too large." }
        }
        val skillDocument = bundle.files.first { it.path == "SKILL.md" }
        validateSharedSkillDocument(Base64.decode(skillDocument.dataBase64).decodeToString())
    }
}

internal fun validateSharedSkillBundlePath(value: String) {
    val normalized = value.replace('\\', '/').trim('/')
    val segments = normalized.split('/')
    require(
        value == normalized &&
            normalized.isNotBlank() &&
            segments.all { it.isNotBlank() && it != "." && it != ".." } &&
            !normalized.startsWith('/') &&
            ':' !in normalized
    ) { "Skill bundle path must stay inside the Skill directory." }
}

internal fun validateSharedSkillDirectoryEntries(entries: List<SharedSkillDirectoryEntry>) {
    require(entries.isNotEmpty()) { "The selected folder did not contain files." }
    require(entries.size <= MaxSharedSkillBundleEntries) { "The selected folder contains too many files." }
    require(entries.map { it.relativePath }.distinct().size == entries.size) {
        "The selected folder contains duplicate paths."
    }
    val skillDocument = entries.filter { it.relativePath.substringAfterLast('/') == "SKILL.md" }
        .minByOrNull { it.relativePath.length }
    require(skillDocument != null) {
        "No SKILL.md file was found in the selected folder."
    }
    var totalBytes = 0L
    entries.forEach { entry ->
        validateSharedSkillBundlePath(entry.relativePath)
        require(entry.relativePath.none { it.code < 0x20 }) { "Skill paths must not contain control characters." }
        require(entry.bytes.size.toLong() <= MaxSharedSkillBundleEntryBytes) {
            "Skill file is too large: ${entry.relativePath}"
        }
        totalBytes += entry.bytes.size
        require(totalBytes <= MaxSharedSkillBundleBytes) { "The selected folder is too large." }
    }
    validateSharedSkillDocument(skillDocument.bytes.decodeToString())
}

private fun String.toSharedSkillBundleSource(): SharedSkillBundleSource {
    val kind = when {
        isBlank() -> "unknown"
        startsWith("https://github.com/") -> "github"
        startsWith("https://") -> "remote_zip"
        endsWith(".zip", ignoreCase = true) -> "zip_uri"
        else -> "document_tree"
    }
    return SharedSkillBundleSource(kind = kind, label = this, uri = this)
}

internal data class SharedSkillMetadata(
    val name: String = "",
    val description: String = "",
    val compatibility: String = "",
    val license: String = "",
    val allowedTools: List<String> = emptyList(),
)

internal fun parseSkillMetadata(markdown: String): SharedSkillMetadata {
    val lines = markdown.lineSequence().toList()
    if (lines.firstOrNull()?.trim() != "---") return SharedSkillMetadata()
    val header = lines.drop(1).takeWhile { it.trim() != "---" }
    fun value(key: String): String {
        val index = header.indexOfFirst { it.trimStart().startsWith("$key:") }
        if (index < 0) return ""
        val raw = header[index].substringAfter(':').trim()
        if (raw != ">" && raw != "|") return raw.trim('"', '\'')
        val continuation = header.drop(index + 1).takeWhile { line ->
            line.isBlank() || line.firstOrNull()?.isWhitespace() == true
        }.map(String::trim)
        return if (raw == ">") continuation.filter(String::isNotBlank).joinToString(" ")
        else continuation.joinToString("\n").trim()
    }
    val rawAllowedTools = value("allowed-tools")
    val allowedTools = when {
        rawAllowedTools.startsWith('[') && rawAllowedTools.endsWith(']') ->
            rawAllowedTools.trim('[', ']').split(',')
        rawAllowedTools.isNotBlank() -> listOf(rawAllowedTools)
        else -> header.dropWhile { !it.trimStart().startsWith("allowed-tools:") }
            .drop(1).takeWhile { it.trimStart().startsWith('-') }
            .map { it.substringAfter('-') }
    }.map { it.trim().trim('"', '\'') }.filter(String::isNotBlank)
    return SharedSkillMetadata(
        name = value("name"),
        description = value("description"),
        compatibility = value("compatibility"),
        license = value("license"),
        allowedTools = allowedTools,
    )
}

internal fun validateSharedSkillDocument(markdown: String): SharedSkillMetadata =
    parseSkillMetadata(markdown).also { metadata ->
        require(metadata.name.isNotBlank()) { "Skill frontmatter is missing 'name'." }
        require(metadata.description.isNotBlank()) { "Skill frontmatter is missing 'description'." }
    }

internal fun buildSharedSkillId(name: String, fallbackId: String = platformRandomUuid()): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "skill-$fallbackId" }

internal data class SharedSkillRemoteSource(
    val downloadUrl: String,
    val subpath: String = "",
)

internal fun resolveRemoteSkillSource(url: String): SharedSkillRemoteSource {
    val normalized = url.trim().ifBlank { error("A remote skill URL is required.") }
    val match = Regex("^(https?)://([^/?#]+)(/[^?#]*)?(?:[?#].*)?$", RegexOption.IGNORE_CASE)
        .matchEntire(normalized)
        ?: error("Skill URL is not a valid absolute URL.")
    val host = match.groupValues[2].substringBefore(':').lowercase()
    val path = match.groupValues[3].ifBlank { "/" }
    val segments = path.split('/').filter(String::isNotBlank)
    val isZipPath = path.lowercase().endsWith(".zip")
    if (host == "github.com") {
        require(segments.size >= 2) { "GitHub URL must include owner and repository." }
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (segments.size >= 4 && segments[2] == "tree") {
            val ref = segments[3]
            return SharedSkillRemoteSource(
                downloadUrl = "https://api.github.com/repos/$owner/$repository/zipball/$ref",
                subpath = segments.drop(4).joinToString("/"),
            )
        }
        if (isZipPath) return SharedSkillRemoteSource(normalized)
        return SharedSkillRemoteSource(
            downloadUrl = "https://api.github.com/repos/$owner/$repository/zipball",
        )
    }
    val isGitHubCodeloadZip = host == "codeload.github.com" &&
        segments.getOrNull(2)?.equals("zip", ignoreCase = true) == true
    require(isZipPath || isGitHubCodeloadZip) {
        "Remote skill URL must be a GitHub repository/tree URL or a direct .zip file."
    }
    return SharedSkillRemoteSource(normalized)
}

private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun com.zhousl.aether.data.pi.SharedHostToolResult.payload(): JsonObject =
    Json.parseToJsonElement(outputJson).jsonObject

private fun com.zhousl.aether.data.pi.SharedHostToolResult.stdout(): String =
    payload()["stdout"]?.jsonPrimitive?.content.orEmpty()

private fun com.zhousl.aether.data.pi.SharedHostToolResult.errorText(): String =
    sequenceOf(
        payload()["stderr"]?.jsonPrimitive?.content.orEmpty(),
        payload()["error"]?.jsonPrimitive?.content.orEmpty(),
    ).firstOrNull { it.isNotBlank() }.orEmpty()
