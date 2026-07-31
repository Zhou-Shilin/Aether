package com.zhousl.aether.data.chatdb

import com.zhousl.aether.data.SharedActiveSkillContext
import com.zhousl.aether.data.platformCurrentTimeMillis
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val SharedDraftSessionId = "draft"

private const val LegacySharedDraftSessionId = "aether-draft-session"

internal fun isSharedDraftSessionId(sessionId: String?): Boolean =
    sessionId == SharedDraftSessionId || sessionId == LegacySharedDraftSessionId

internal fun resolveSharedCurrentSessionId(
    currentSessionId: String?,
    sessionIds: Collection<String>,
): String = currentSessionId
    ?.takeIf { id -> isSharedDraftSessionId(id) || id in sessionIds }
    ?.let { id -> if (isSharedDraftSessionId(id)) SharedDraftSessionId else id }
    ?: SharedDraftSessionId

private fun String?.toStoredSharedCurrentSessionId(): String? = this
    ?.trim()
    ?.takeUnless { id -> id.isEmpty() || isSharedDraftSessionId(id) }

@Serializable
data class PersistedChatMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
    val reasoningText: String = "",
    val tools: List<PersistedChatTool> = emptyList(),
    val responseBlocks: List<PersistedAssistantResponseBlock> = emptyList(),
    val attachments: List<PersistedChatAttachment> = emptyList(),
    val usage: PersistedChatUsage? = null,
    val responseGroupId: String = "",
    val isActiveBranch: Boolean = true,
    val branchIndex: Int = 0,
    val createdAtMillis: Long = platformCurrentTimeMillis(),
    val completedAtMillis: Long? = null,
    val providerId: String = "",
    val modelId: String = "",
    val providerPayloadJson: String = "",
    val thoughtDurationMillis: Long = 0,
    val responseDurationMillis: Long = 0,
    val firstTokenLatencyMillis: Long? = null,
    val tokenUsageSource: String = "unavailable",
    val assistantActionsHidden: Boolean = false,
    val displayKind: PersistedMessageDisplayKind = PersistedMessageDisplayKind.Standard,
    val userBranches: List<List<PersistedChatMessage>> = emptyList(),
    val selectedUserBranchIndex: Int = 0,
)

@Serializable
enum class PersistedMessageDisplayKind {
    Standard,
    HiddenContext,
    CompactStatus,
}

@Serializable
data class PersistedChatUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val inputTokensAvailable: Boolean = true,
    val outputTokensAvailable: Boolean = true,
    val totalTokensAvailable: Boolean = true,
    val reasoningTokensAvailable: Boolean = true,
    val cachedInputTokensAvailable: Boolean = true,
    val requestCount: Int = 1,
)

@Serializable
data class PersistedChatTool(
    val id: String,
    val name: String,
    val summary: String,
    val output: String = "",
    val argumentsJson: String = "",
    val outputJson: String = "",
    val isRunning: Boolean = false,
    val isError: Boolean = false,
    val startedAtUptimeMillis: Long = 0L,
    val completedAtUptimeMillis: Long? = null,
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    val timelineOrder: Long = 0L,
)

@Serializable
data class PersistedReasoningSummaryChunk(
    val id: String,
    val title: String = "",
    val detail: String = "",
    val rawText: String = "",
    val isPending: Boolean = false,
    val createdAtMillis: Long = 0L,
    val timelineOrder: Long = 0L,
)

@Serializable
data class PersistedReasoningTrace(
    val id: String,
    val rawText: String = "",
    val chunks: List<PersistedReasoningSummaryChunk> = emptyList(),
    val toolInvocations: List<PersistedChatTool> = emptyList(),
    val latestStatusText: String = "",
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
)

@Serializable
enum class PersistedAssistantResponseBlockType {
    Text,
    Reasoning,
    ToolGroup,
}

@Serializable
data class PersistedAssistantResponseBlock(
    val id: String,
    val type: PersistedAssistantResponseBlockType,
    val text: String = "",
    val tools: List<PersistedChatTool> = emptyList(),
    val reasoningTrace: PersistedReasoningTrace? = null,
)

@Serializable
data class PersistedChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val workspacePath: String,
    val sizeBytes: Long = 0,
    val inlineBase64: String = "",
    val sourceIdentifier: String = "",
)

@Serializable
data class PersistedChatSession(
    val id: String,
    val title: String,
    val preview: String,
    val messages: List<PersistedChatMessage>,
    val hasCustomTitle: Boolean = false,
    val selectedSkillIds: List<String> = emptyList(),
    val activeSkills: List<SharedActiveSkillContext> = emptyList(),
    val activeMcpServerIds: List<String> = emptyList(),
    val chromeEnabled: Boolean = false,
    val selectedModelKey: String = "",
)

internal fun deriveSharedSessionMetadata(
    messages: List<PersistedChatMessage>,
): Pair<String, String> {
    val title = messages
        .firstOrNull { it.fromUser && it.displayKind == PersistedMessageDisplayKind.Standard }
        ?.sharedSummaryText()
        .orEmpty()
        .ifBlank { "New chat" }
        .take(36)
    val preview = messages
        .lastOrNull { it.displayKind != PersistedMessageDisplayKind.HiddenContext }
        ?.sharedSummaryText()
        .orEmpty()
        .ifBlank { "No messages yet." }
        .take(96)
    return title to preview
}

internal fun PersistedChatMessage.sharedSummaryText(): String {
    if (displayKind == PersistedMessageDisplayKind.CompactStatus) {
        return text.ifBlank { "Context compacted" }
    }
    text.trim().takeIf(String::isNotBlank)?.let { return it }
    responseBlocks.asReversed().forEach { block ->
        when (block.type) {
            PersistedAssistantResponseBlockType.Text ->
                block.text.trim().takeIf(String::isNotBlank)?.let { return it }

            PersistedAssistantResponseBlockType.Reasoning -> {
                val trace = block.reasoningTrace
                trace?.chunks?.lastOrNull { it.detail.isNotBlank() || it.title.isNotBlank() }
                    ?.let { return it.detail.ifBlank { it.title } }
                return if (trace?.toolInvocations.orEmpty().isNotEmpty()) {
                    "Thought and used ${trace?.toolInvocations.orEmpty().size} tools"
                } else {
                    "Thought"
                }
            }

            PersistedAssistantResponseBlockType.ToolGroup ->
                if (block.tools.isNotEmpty()) return block.tools.sharedToolSummaryText()
        }
    }
    if (reasoningText.isNotBlank()) return "Thought"
    if (tools.isNotEmpty()) return tools.sharedToolSummaryText()
    if (attachments.isEmpty()) return "Empty message"
    if (attachments.size == 1) return attachments.first().name
    return "${attachments.size} attachments"
}

private fun List<PersistedChatTool>.sharedToolSummaryText(): String = if (size == 1) {
    when (first().name.lowercase()) {
        "bash" -> "Ran bash command"
        "fetch_bash_output" -> "Fetched bash output"
        "kill_bash" -> "Stopped bash command"
        "sleep" -> "Waited"
        else -> "Used ${first().name}"
    }
} else {
    "Used $size tools"
}

class SharedChatHistoryStore(
    database: ChatHistoryDatabase,
) {
    private val dao = database.chatHistoryDao()
    private val writeMutex = Mutex()

    suspend fun loadMostRecent(): PersistedChatSession? {
        val session = dao.getSessions().firstOrNull() ?: return null
        return load(session.id)
    }

    suspend fun loadCurrent(): PersistedChatSession? {
        val currentId = loadCurrentSessionId()
        if (isSharedDraftSessionId(currentId)) return null
        return currentId?.takeIf(String::isNotBlank)?.let { load(it) } ?: loadMostRecent()
    }

    suspend fun loadCurrentSessionId(): String? =
        dao.getMeta()?.currentSessionId
            ?.takeIf(String::isNotBlank)
            ?.let { id -> if (isSharedDraftSessionId(id)) SharedDraftSessionId else id }
            ?: SharedDraftSessionId

    suspend fun setCurrentSession(sessionId: String?) = writeMutex.withLock {
        val storedSessionId = sessionId.toStoredSharedCurrentSessionId()
            ?.takeIf { dao.getSession(it) != null }
        dao.upsertMeta(
            ChatStateMetaEntity(
                currentSessionId = storedSessionId,
                roomMigrationComplete = true,
                workspaceFileRefsComplete = true,
            )
        )
    }

    suspend fun loadAll(): List<PersistedChatSession> =
        dao.getSessions().mapNotNull { session -> load(session.id) }

    suspend fun load(sessionId: String): PersistedChatSession? {
        val session = dao.getSession(sessionId) ?: return null
        val messages = dao.getMessagesForSession(session.id).mapNotNull { entity ->
            runCatching {
                val json = Json.parseToJsonElement(entity.messageJson).jsonObject
                json.toPersistedChatMessage(
                    fallbackId = entity.id,
                    fallbackCreatedAtMillis = entity.createdAtMillis ?: 0,
                    fallbackResponseGroupId = entity.responseGroupId.orEmpty(),
                    fallbackDisplayKind = entity.displayKind.orEmpty(),
                )
            }.getOrNull()
        }
        return PersistedChatSession(
            id = session.id,
            title = session.title,
            preview = session.preview,
            messages = messages,
            hasCustomTitle = session.hasCustomTitle,
            selectedSkillIds = parseStringArray(session.selectedSkillIdsJson),
            activeSkills = decodeSharedActiveSkillContexts(session.activeSkillsJson),
            activeMcpServerIds = parseStringArray(session.activeMcpServerIdsJson),
            chromeEnabled = session.chromeEnabled,
            selectedModelKey = session.selectedModelKey,
        )
    }

    suspend fun rename(sessionId: String, title: String) {
        val session = dao.getSession(sessionId) ?: return
        dao.upsertSession(session.copy(title = title.trim(), hasCustomTitle = true))
    }

    suspend fun updateSelectedModelKey(sessionId: String, selectedModelKey: String) = writeMutex.withLock {
        dao.updateSelectedModelKey(sessionId, selectedModelKey)
    }

    suspend fun delete(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun getUnreferencedWorkspaceFilePathsForDeletedSession(sessionId: String): List<String> {
        val candidatePaths = dao.getWorkspaceFilePathsForSession(sessionId)
            .map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        if (candidatePaths.isEmpty()) return emptyList()
        val referencedPaths = candidatePaths.chunked(WorkspaceFileRefQueryChunkSize)
            .flatMap { chunk -> dao.getWorkspaceFileRefsForPaths(chunk) }
            .asSequence()
            .filterNot { it.sessionId == sessionId }
            .map { it.path }
            .toSet()
        return candidatePaths.filterNot(referencedPaths::contains)
    }

    suspend fun getUnreferencedWorkspaceFilePathsForDeletedMessages(
        sessionId: String,
        messageIds: List<String>,
    ): List<String> {
        val safeMessageIds = messageIds.map(String::trim).filter(String::isNotEmpty).distinct()
        if (safeMessageIds.isEmpty()) return emptyList()
        val safeMessageIdSet = safeMessageIds.toSet()
        val candidatePaths = safeMessageIds.chunked(WorkspaceFileRefQueryChunkSize)
            .flatMap { chunk -> dao.getWorkspaceFilePathsForMessages(sessionId, chunk) }
            .map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        if (candidatePaths.isEmpty()) return emptyList()
        val referencedPaths = candidatePaths.chunked(WorkspaceFileRefQueryChunkSize)
            .flatMap { chunk -> dao.getWorkspaceFileRefsForPaths(chunk) }
            .asSequence()
            .filterNot { it.sessionId == sessionId && it.messageId in safeMessageIdSet }
            .map { it.path }
            .toSet()
        return candidatePaths.filterNot(referencedPaths::contains)
    }

    suspend fun replaceAll(
        sessions: List<PersistedChatSession>,
        currentSessionId: String?,
    ) = writeMutex.withLock {
        val normalizedCurrentId = resolveSharedCurrentSessionId(
            currentSessionId = currentSessionId,
            sessionIds = sessions.map(PersistedChatSession::id),
        )
        val sessionEntities = sessions.mapIndexed { index, session ->
            ChatSessionEntity(
                id = session.id,
                title = session.title,
                preview = session.preview,
                hasCustomTitle = session.hasCustomTitle,
                selectedSkillIdsJson = buildJsonArray {
                    session.selectedSkillIds.distinct().forEach { add(JsonPrimitive(it)) }
                }.toString(),
                activeSkillsJson = encodeSharedActiveSkillContexts(session.activeSkills),
                activeMcpServerIdsJson = buildJsonArray {
                    session.activeMcpServerIds.distinct().forEach { add(JsonPrimitive(it)) }
                }.toString(),
                agentModeEnabled = false,
                chromeEnabled = session.chromeEnabled,
                selectedModelKey = session.selectedModelKey,
                sortOrder = index.toLong(),
            )
        }
        val messageEntities = sessions.flatMap { session ->
            session.messages.mapIndexed { index, message ->
                ChatMessageEntity(
                    sessionId = session.id,
                    id = message.id,
                    position = index,
                    messageJson = message.toJsonObject().toString(),
                    author = if (message.fromUser) "User" else "Agent",
                    text = message.text,
                    createdAtMillis = message.createdAtMillis,
                    responseGroupId = message.responseGroupId.ifBlank { null },
                    displayKind = message.displayKind.name,
                    hasUsageStatistics = message.usage != null,
                )
            }
        }
        val workspaceFileRefs = sessions.flatMap { session ->
            session.messages.toWorkspaceFileRefs(session.id)
        }
        dao.replaceAll(
            sessions = sessionEntities,
            messages = messageEntities,
            workspaceFileRefs = workspaceFileRefs,
            meta = ChatStateMetaEntity(
                currentSessionId = normalizedCurrentId.toStoredSharedCurrentSessionId(),
                roomMigrationComplete = true,
                workspaceFileRefsComplete = true,
            ),
        )
    }

    suspend fun save(
        sessionId: String,
        messages: List<PersistedChatMessage>,
        selectedSkillIds: List<String> = emptyList(),
        activeSkills: List<SharedActiveSkillContext> = emptyList(),
        activeMcpServerIds: List<String> = emptyList(),
        chromeEnabled: Boolean = false,
        selectedModelKey: String = "",
        titleOverride: String? = null,
        hasCustomTitle: Boolean = false,
    ) = writeMutex.withLock {
        val metadata = deriveSharedSessionMetadata(messages)
        dao.upsertSession(
            ChatSessionEntity(
                id = sessionId,
                title = titleOverride?.trim().takeUnless { it.isNullOrBlank() }
                    ?: metadata.first,
                preview = metadata.second,
                hasCustomTitle = hasCustomTitle,
                selectedSkillIdsJson = buildJsonArray {
                    selectedSkillIds.distinct().forEach { add(JsonPrimitive(it)) }
                }.toString(),
                activeSkillsJson = encodeSharedActiveSkillContexts(activeSkills),
                activeMcpServerIdsJson = buildJsonArray {
                    activeMcpServerIds.distinct().forEach { add(JsonPrimitive(it)) }
                }.toString(),
                agentModeEnabled = false,
                chromeEnabled = chromeEnabled,
                selectedModelKey = selectedModelKey,
                sortOrder = -platformSortOrder(),
            )
        )
        dao.deleteMessagesForSession(sessionId)
        dao.deleteWorkspaceFileRefsForSession(sessionId)
        dao.upsertMessages(
            messages.mapIndexed { index, message ->
                val json = message.toJsonObject()
                ChatMessageEntity(
                    sessionId = sessionId,
                    id = message.id,
                    position = index,
                    messageJson = json.toString(),
                    author = if (message.fromUser) "User" else "Agent",
                    text = message.text,
                    createdAtMillis = message.createdAtMillis,
                    responseGroupId = message.responseGroupId.ifBlank { null },
                    displayKind = message.displayKind.name,
                    hasUsageStatistics = message.usage != null,
                )
            }
        )
        val workspaceFileRefs = messages.toWorkspaceFileRefs(sessionId)
        if (workspaceFileRefs.isNotEmpty()) dao.upsertWorkspaceFileRefs(workspaceFileRefs)
        dao.upsertMeta(
            ChatStateMetaEntity(
                currentSessionId = sessionId,
                roomMigrationComplete = true,
                workspaceFileRefsComplete = true,
            )
        )
    }
}

private const val WorkspaceFileRefQueryChunkSize = 500

private fun List<PersistedChatMessage>.toWorkspaceFileRefs(
    sessionId: String,
): List<ChatWorkspaceFileRefEntity> = flatMap { message ->
    message.collectWorkspaceFilePathsForIndex().map { path ->
        ChatWorkspaceFileRefEntity(
            sessionId = sessionId,
            messageId = message.id,
            path = path,
        )
    }
}.distinctBy { ref -> Triple(ref.sessionId, ref.messageId, ref.path) }

private fun PersistedChatMessage.collectWorkspaceFilePathsForIndex(): List<String> =
    (attachments.map { it.workspacePath.trim() }.filter(String::isNotEmpty) +
        userBranches.flatMap { branch ->
            branch.flatMap(PersistedChatMessage::collectWorkspaceFilePathsForIndex)
        }).distinct()

private fun JsonObject.toPersistedChatMessage(
    fallbackId: String = "",
    fallbackCreatedAtMillis: Long = 0,
    fallbackResponseGroupId: String = "",
    fallbackDisplayKind: String = "",
): PersistedChatMessage = PersistedChatMessage(
    id = string("id").ifBlank { fallbackId },
    text = string("text"),
    fromUser = get("fromUser")?.jsonPrimitive?.booleanOrNull ?: false,
    isError = get("isError")?.jsonPrimitive?.booleanOrNull ?: false,
    reasoningText = string("reasoningText"),
    responseGroupId = string("responseGroupId").ifBlank { fallbackResponseGroupId },
    isActiveBranch = get("isActiveBranch")?.jsonPrimitive?.booleanOrNull ?: true,
    branchIndex = get("branchIndex")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
    createdAtMillis = long("createdAtMillis").takeIf { it > 0 } ?: fallbackCreatedAtMillis,
    completedAtMillis = get("completedAtMillis")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    providerId = string("providerId"),
    modelId = string("modelId"),
    providerPayloadJson = string("providerPayloadJson"),
    thoughtDurationMillis = long("thoughtDurationMillis"),
    responseDurationMillis = long("responseDurationMillis"),
    firstTokenLatencyMillis = get("firstTokenLatencyMillis")
        ?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    tokenUsageSource = string("tokenUsageSource").ifBlank { "unavailable" },
    assistantActionsHidden = get("assistantActionsHidden")?.jsonPrimitive?.booleanOrNull ?: false,
    displayKind = PersistedMessageDisplayKind.entries.firstOrNull {
        it.name == string("displayKind").ifBlank { fallbackDisplayKind }
    } ?: PersistedMessageDisplayKind.Standard,
    selectedUserBranchIndex = get("selectedUserBranchIndex")
        ?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
    userBranches = (get("userBranches") as? JsonArray).orEmpty().mapNotNull { branchElement ->
        (branchElement as? JsonArray)?.mapNotNull { messageElement ->
            (messageElement as? JsonObject)?.toPersistedChatMessage()
        }
    },
    tools = (get("tools") as? JsonArray).orEmpty().mapNotNull(::parsePersistedChatTool),
    responseBlocks = (get("responseBlocks") as? JsonArray).orEmpty().mapNotNull { element ->
        val block = element as? JsonObject ?: return@mapNotNull null
        val type = PersistedAssistantResponseBlockType.entries.firstOrNull {
            it.name == block.string("type")
        } ?: return@mapNotNull null
        PersistedAssistantResponseBlock(
            id = block.string("id"),
            type = type,
            text = block.string("text"),
            tools = (block["tools"] as? JsonArray).orEmpty().mapNotNull(::parsePersistedChatTool),
            reasoningTrace = (block["reasoningTrace"] as? JsonObject)?.toPersistedReasoningTrace(),
        )
    },
    attachments = (get("attachments") as? JsonArray).orEmpty().mapNotNull { element ->
        val attachment = element as? JsonObject ?: return@mapNotNull null
        PersistedChatAttachment(
            id = attachment.string("id"),
            name = attachment.string("name"),
            mimeType = attachment.string("mimeType"),
            workspacePath = attachment.string("workspacePath"),
            sizeBytes = attachment.long("sizeBytes"),
            inlineBase64 = attachment.string("inlineBase64"),
            sourceIdentifier = attachment.string("sourceIdentifier"),
        )
    },
    usage = (get("usage") as? JsonObject)?.let { usage ->
        PersistedChatUsage(
            inputTokens = usage.long("inputTokens"),
            outputTokens = usage.long("outputTokens"),
            totalTokens = usage.long("totalTokens"),
            reasoningTokens = usage.long("reasoningTokens"),
            cachedInputTokens = usage.long("cachedInputTokens"),
            inputTokensAvailable = usage["inputTokensAvailable"]?.jsonPrimitive?.booleanOrNull ?: true,
            outputTokensAvailable = usage["outputTokensAvailable"]?.jsonPrimitive?.booleanOrNull ?: true,
            totalTokensAvailable = usage["totalTokensAvailable"]?.jsonPrimitive?.booleanOrNull ?: true,
            reasoningTokensAvailable = usage["reasoningTokensAvailable"]?.jsonPrimitive?.booleanOrNull ?: true,
            cachedInputTokensAvailable = usage["cachedInputTokensAvailable"]
                ?.jsonPrimitive?.booleanOrNull ?: true,
            requestCount = usage["requestCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.coerceAtLeast(1) ?: 1,
        )
    },
)

private fun PersistedChatMessage.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    put("text", text)
    put("fromUser", fromUser)
    put("isError", isError)
    put("reasoningText", reasoningText)
    put("responseGroupId", responseGroupId)
    put("isActiveBranch", isActiveBranch)
    put("branchIndex", branchIndex)
    put("createdAtMillis", createdAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("providerId", providerId)
    put("modelId", modelId)
    if (providerPayloadJson.isNotBlank()) put("providerPayloadJson", providerPayloadJson)
    put("thoughtDurationMillis", thoughtDurationMillis)
    put("responseDurationMillis", responseDurationMillis)
    firstTokenLatencyMillis?.let { put("firstTokenLatencyMillis", it) }
    put("tokenUsageSource", tokenUsageSource)
    if (assistantActionsHidden) put("assistantActionsHidden", true)
    put("displayKind", displayKind.name)
    put("selectedUserBranchIndex", selectedUserBranchIndex)
    put("userBranches", buildJsonArray {
        userBranches.forEach { branch ->
            add(buildJsonArray { branch.forEach { add(it.toJsonObject()) } })
        }
    })
    put("tools", buildJsonArray {
        tools.forEach { tool ->
            add(tool.toJsonObject())
        }
    })
    put("responseBlocks", buildJsonArray {
        responseBlocks.forEach { block ->
            add(buildJsonObject {
                put("id", block.id)
                put("type", block.type.name)
                put("text", block.text)
                put("tools", buildJsonArray {
                    block.tools.forEach { tool ->
                        add(tool.toJsonObject())
                    }
                })
                block.reasoningTrace?.let { trace -> put("reasoningTrace", trace.toJsonObject()) }
            })
        }
    })
    put("attachments", buildJsonArray {
        attachments.forEach { attachment ->
            add(buildJsonObject {
                put("id", attachment.id)
                put("name", attachment.name)
                put("mimeType", attachment.mimeType)
                put("workspacePath", attachment.workspacePath)
                put("sizeBytes", attachment.sizeBytes)
                attachment.inlineBase64.takeIf(String::isNotBlank)?.let { put("inlineBase64", it) }
                attachment.sourceIdentifier.takeIf(String::isNotBlank)?.let { put("sourceIdentifier", it) }
            })
        }
    })
    usage?.let { stats ->
        put("usage", buildJsonObject {
            put("inputTokens", stats.inputTokens)
            put("outputTokens", stats.outputTokens)
            put("totalTokens", stats.totalTokens)
            put("reasoningTokens", stats.reasoningTokens)
            put("cachedInputTokens", stats.cachedInputTokens)
            put("inputTokensAvailable", stats.inputTokensAvailable)
            put("outputTokensAvailable", stats.outputTokensAvailable)
            put("totalTokensAvailable", stats.totalTokensAvailable)
            put("reasoningTokensAvailable", stats.reasoningTokensAvailable)
            put("cachedInputTokensAvailable", stats.cachedInputTokensAvailable)
            put("requestCount", stats.requestCount)
        })
    }
}

private fun parsePersistedChatTool(element: kotlinx.serialization.json.JsonElement): PersistedChatTool? {
    val tool = element as? JsonObject ?: return null
    return PersistedChatTool(
        id = tool.string("id"),
        name = tool.string("name"),
        summary = tool.string("summary"),
        output = tool.string("output"),
        argumentsJson = tool.string("argumentsJson"),
        outputJson = tool.string("outputJson"),
        isRunning = tool["isRunning"]?.jsonPrimitive?.booleanOrNull ?: false,
        isError = tool["isError"]?.jsonPrimitive?.booleanOrNull ?: false,
        startedAtUptimeMillis = tool.long("startedAtUptimeMillis"),
        completedAtUptimeMillis = tool["completedAtUptimeMillis"]
            ?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        startedAtMillis = tool.long("startedAtMillis"),
        completedAtMillis = tool["completedAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        timelineOrder = tool.long("timelineOrder"),
    )
}

private fun PersistedChatTool.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("summary", summary)
    put("output", output)
    put("argumentsJson", argumentsJson)
    put("outputJson", outputJson)
    put("isRunning", isRunning)
    put("isError", isError)
    put("startedAtUptimeMillis", startedAtUptimeMillis)
    completedAtUptimeMillis?.let { put("completedAtUptimeMillis", it) }
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("timelineOrder", timelineOrder)
}

private fun JsonObject.toPersistedReasoningTrace(): PersistedReasoningTrace = PersistedReasoningTrace(
    id = string("id"),
    rawText = string("rawText"),
    chunks = (get("chunks") as? JsonArray).orEmpty().mapNotNull { element ->
        val chunk = element as? JsonObject ?: return@mapNotNull null
        PersistedReasoningSummaryChunk(
            id = chunk.string("id"),
            title = chunk.string("title"),
            detail = chunk.string("detail"),
            rawText = chunk.string("rawText"),
            isPending = chunk["isPending"]?.jsonPrimitive?.booleanOrNull ?: false,
            createdAtMillis = chunk.long("createdAtMillis"),
            timelineOrder = chunk.long("timelineOrder"),
        )
    },
    toolInvocations = (get("toolInvocations") as? JsonArray).orEmpty()
        .mapNotNull(::parsePersistedChatTool),
    latestStatusText = string("latestStatusText"),
    startedAtMillis = long("startedAtMillis"),
    completedAtMillis = get("completedAtMillis")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
)

private fun PersistedReasoningTrace.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    put("rawText", rawText)
    put("chunks", buildJsonArray {
        chunks.forEach { chunk ->
            add(buildJsonObject {
                put("id", chunk.id)
                put("title", chunk.title)
                put("detail", chunk.detail)
                put("rawText", chunk.rawText)
                put("isPending", chunk.isPending)
                put("createdAtMillis", chunk.createdAtMillis)
                put("timelineOrder", chunk.timelineOrder)
            })
        }
    })
    put("toolInvocations", buildJsonArray {
        toolInvocations.forEach { add(it.toJsonObject()) }
    })
    put("latestStatusText", latestStatusText)
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
}

@OptIn(ExperimentalSerializationApi::class)
private val PersistedChatSessionExportJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

fun serializePersistedChatSession(session: PersistedChatSession): String {
    val root = buildJsonObject {
        put("schemaVersion", 1)
        put("exportType", "session")
        put("exportedAtMillis", platformCurrentTimeMillis())
        put("session", session.toAndroidChatSessionJson())
    }
    return PersistedChatSessionExportJson.encodeToString(JsonObject.serializer(), root)
}

private fun platformSortOrder(): Long = platformCurrentTimeMillis()

private fun parseStringArray(value: String): List<String> = runCatching {
    (Json.parseToJsonElement(value) as? JsonArray)
        .orEmpty()
        .mapNotNull { it.jsonPrimitive.contentOrNull }
        .filter(String::isNotBlank)
        .distinct()
}.getOrDefault(emptyList())

private val SharedActiveSkillsJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal fun encodeSharedActiveSkillContexts(value: List<SharedActiveSkillContext>): String =
    SharedActiveSkillsJson.encodeToString(value)

internal fun decodeSharedActiveSkillContexts(value: String): List<SharedActiveSkillContext> =
    runCatching { SharedActiveSkillsJson.decodeFromString<List<SharedActiveSkillContext>>(value) }
        .getOrDefault(emptyList())

private fun kotlinx.serialization.json.JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun kotlinx.serialization.json.JsonObject.long(name: String): Long =
    get(name)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0
