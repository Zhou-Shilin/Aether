package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.zhousl.aether.data.chatdb.ChatHistoryDao
import com.zhousl.aether.data.chatdb.ChatHistoryDatabase
import com.zhousl.aether.data.chatdb.ChatMessageEntity
import com.zhousl.aether.data.chatdb.ChatMessageSummaryEntity
import com.zhousl.aether.data.chatdb.ChatSessionEntity
import com.zhousl.aether.data.chatdb.ChatSessionSnapshot
import com.zhousl.aether.data.chatdb.ChatStateMetaEntity

import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.AttachmentWorkspaceState
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatBranchGroup
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.ChatUsageStatistics
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.MessageDisplayKind
import com.zhousl.aether.ui.ReasoningSummaryChunk
import com.zhousl.aether.ui.ReasoningTrace
import com.zhousl.aether.ui.syncActiveBranches
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val DraftSessionId = "draft"


private val Context.chatDataStore by preferencesDataStore(name = "aether_chats")

data class PersistedChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = DraftSessionId,
)

class ChatRepository(
    private val context: Context,
    private val database: ChatHistoryDatabase = ChatHistoryDatabase.getInstance(context),
) {
    private val chatHistoryDao: ChatHistoryDao = database.chatHistoryDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatState: Flow<PersistedChatState> = flow {
        migrateLegacyChatStateIfNeeded()
        emitAll(
            combine(
                chatHistoryDao.observeSessions(),
                chatHistoryDao.observeMeta(),
            ) { sessionRows, meta ->
                val currentSessionId = meta?.currentSessionId ?: DraftSessionId
                SessionListState(
                    rows = sessionRows,
                    currentSessionId = currentSessionId
                        .takeIf { id -> id == DraftSessionId || sessionRows.any { it.id == id } }
                        ?: sessionRows.firstOrNull()?.id
                        ?: DraftSessionId,
                )
            }.flatMapLatest { state ->
                val sessionIds = state.rows.map { it.id }
                val messagesFlow = if (sessionIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    chatHistoryDao.observeMessagesForSessions(sessionIds)
                }
                messagesFlow.map { messages ->
                    val messagesBySessionId = messages.groupBy { it.sessionId }
                    val sessions = state.rows.map { session ->
                        session.toChatSession(messagesBySessionId[session.id].orEmpty())
                    }
                    PersistedChatState(
                        sessions = sessions,
                        currentSessionId = state.currentSessionId,
                    )
                }
            }
        )
    }

    suspend fun updateChatState(
        sessions: List<ChatSession>,
        currentSessionId: String,
    ) {
        migrateLegacyChatStateIfNeeded()
        replaceChatState(
            sessions = sessions.mapIndexed { index, session -> session.toSnapshot(index) },
            currentSessionId = currentSessionId,
            migrationComplete = true,
        )
        context.chatDataStore.edit { preferences ->
            preferences.remove(SESSIONS_JSON)
            preferences.remove(CURRENT_SESSION_ID)
            preferences[ROOM_MIGRATION_COMPLETE] = true
        }
    }


    suspend fun upsertSessionSnapshot(
        session: ChatSession,
        sortOrder: Long,
    ) {
        migrateLegacyChatStateIfNeeded()
        database.withTransaction {
            chatHistoryDao.upsertSession(session.toSessionEntity(sortOrder))
        }
    }

    suspend fun upsertMessageSnapshot(
        sessionId: String,
        message: ChatMessage,
        position: Int,
    ) {
        require(position >= 0) { "position must be non-negative" }
        migrateLegacyChatStateIfNeeded()
        database.withTransaction {
            chatHistoryDao.upsertMessage(
                ChatMessageEntityMapper.toEntity(
                    sessionId = sessionId,
                    position = position,
                    message = message,
                )
            )
        }
    }

    suspend fun deleteSessionById(sessionId: String) {
        migrateLegacyChatStateIfNeeded()
        database.withTransaction {
            chatHistoryDao.deleteSession(sessionId)
            val meta = chatHistoryDao.getMeta()
            if (meta?.currentSessionId == sessionId) {
                chatHistoryDao.upsertMeta(meta.copy(currentSessionId = null))
            }
        }
    }

    suspend fun replaceMessagesFromPosition(
        sessionId: String,
        fromPosition: Int,
        messages: List<ChatMessage>,
    ) {
        require(fromPosition >= 0) { "fromPosition must be non-negative" }
        migrateLegacyChatStateIfNeeded()
        database.withTransaction {
            replaceMessagesFromPositionInTransaction(sessionId, fromPosition, messages)
        }
    }

    private suspend fun replaceMessagesFromPositionInTransaction(
        sessionId: String,
        fromPosition: Int,
        messages: List<ChatMessage>,
    ) {
        chatHistoryDao.deleteMessagesFromPosition(sessionId, fromPosition)
        if (messages.isNotEmpty()) {
            chatHistoryDao.upsertMessages(
                messages.mapIndexed { index, message ->
                    ChatMessageEntityMapper.toEntity(
                        sessionId = sessionId,
                        position = fromPosition + index,
                        message = message,
                    )
                }
            )
        }
    }

    private suspend fun replaceChatState(
        sessions: List<ChatSessionSnapshot>,
        currentSessionId: String,
        migrationComplete: Boolean,
    ) {
        database.withTransaction {
            val safeCurrentSessionId = currentSessionId
                .takeIf { id -> id == DraftSessionId || sessions.any { it.session.id == id } }
                ?: sessions.firstOrNull()?.session?.id
                ?: DraftSessionId
            if (sessions.isEmpty()) {
                chatHistoryDao.upsertMeta(
                    ChatStateMetaEntity(
                        currentSessionId = null,
                        roomMigrationComplete = migrationComplete,
                    )
                )
                chatHistoryDao.deleteAllMessages()
                chatHistoryDao.deleteAllSessions()
                return@withTransaction
            }

            val sessionEntities = sessions.map { it.session }
            val sessionIds = sessionEntities.map { it.id }
            chatHistoryDao.upsertSessions(sessionEntities)
            chatHistoryDao.deleteSessionsExcept(sessionIds)
            chatHistoryDao.deleteMessagesExceptSessions(sessionIds)
            chatHistoryDao.upsertMeta(
                ChatStateMetaEntity(
                    currentSessionId = safeCurrentSessionId.toStoredCurrentSessionId(),
                    roomMigrationComplete = migrationComplete,
                )
            )

            sessions.forEach { snapshot ->
                chatHistoryDao.deleteMessagesForSession(snapshot.session.id)
                if (snapshot.messages.isNotEmpty()) {
                    chatHistoryDao.upsertMessages(snapshot.messages)
                }
            }
        }
    }

    // TODO(Room v2): remove legacy DataStore chat import.
    private suspend fun migrateLegacyChatStateIfNeeded() = migrationMutex.withLock {
        val preferences = context.chatDataStore.data.first()
        val legacySessionsJson = preferences[SESSIONS_JSON].orEmpty()
        val legacyMigrationComplete = preferences[ROOM_MIGRATION_COMPLETE] == true
        val legacyCurrentSessionId = preferences[CURRENT_SESSION_ID]
        val roomMeta = chatHistoryDao.getMeta()

        if (roomMeta?.roomMigrationComplete == true) {
            if (legacySessionsJson.isNotBlank() || preferences[CURRENT_SESSION_ID] != null) {
                context.chatDataStore.edit { data ->
                    data.remove(SESSIONS_JSON)
                    data.remove(CURRENT_SESSION_ID)
                    data[ROOM_MIGRATION_COMPLETE] = true
                }
            }
            return@withLock
        }

        if (legacyMigrationComplete && legacySessionsJson.isBlank()) {
            database.withTransaction {
                chatHistoryDao.upsertMeta(
                    ChatStateMetaEntity(
                        currentSessionId = null,
                        roomMigrationComplete = true,
                    )
                )
            }
            if (preferences[CURRENT_SESSION_ID] != null) {
                context.chatDataStore.edit { data ->
                    data.remove(CURRENT_SESSION_ID)
                    data[ROOM_MIGRATION_COMPLETE] = true
                }
            }
            return@withLock
        }

        if (legacySessionsJson.isBlank()) {
            database.withTransaction {
                chatHistoryDao.upsertMeta(
                    ChatStateMetaEntity(
                        currentSessionId = null,
                        roomMigrationComplete = true,
                    )
                )
            }
            context.chatDataStore.edit { data ->
                data.remove(SESSIONS_JSON)
                data.remove(CURRENT_SESSION_ID)
                data[ROOM_MIGRATION_COMPLETE] = true
            }
            return@withLock
        }

        val legacyParseResult = parseChatSessionsForMigration(legacySessionsJson)
        val legacySessions = legacyParseResult.sessions
        if (legacyParseResult.recoveredFromCorruption) {
            // TODO(Room v2): remove with legacy DataStore chat import.
            replaceChatState(
                sessions = legacySessions.mapIndexed { index, session -> session.toSnapshot(index) },
                currentSessionId = resolveLegacyCurrentSessionIdForMigration(
                    legacyCurrentSessionId = legacyCurrentSessionId,
                    legacySessions = legacySessions,
                ),
                migrationComplete = true,
            )
            context.chatDataStore.edit { data ->
                data.remove(SESSIONS_JSON)
                data.remove(CURRENT_SESSION_ID)
                data[ROOM_MIGRATION_COMPLETE] = true
            }
            return@withLock
        }
        if (legacySessions.isNotEmpty()) {
            replaceChatState(
                sessions = legacySessions.mapIndexed { index, session -> session.toSnapshot(index) },
                currentSessionId = resolveLegacyCurrentSessionIdForMigration(
                    legacyCurrentSessionId = legacyCurrentSessionId,
                    legacySessions = legacySessions,
                ),
                migrationComplete = true,
            )
            context.chatDataStore.edit { data ->
                data.remove(SESSIONS_JSON)
                data.remove(CURRENT_SESSION_ID)
                data[ROOM_MIGRATION_COMPLETE] = true
            }
            return@withLock
        }

        database.withTransaction {
            chatHistoryDao.upsertMeta(
                ChatStateMetaEntity(
                    currentSessionId = null,
                    roomMigrationComplete = true,
                )
            )
        }
        context.chatDataStore.edit { data ->
            data.remove(SESSIONS_JSON)
            data.remove(CURRENT_SESSION_ID)
            data[ROOM_MIGRATION_COMPLETE] = true
        }
    }


    private companion object {
        val SESSIONS_JSON = stringPreferencesKey("sessions_json")
        val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
        val ROOM_MIGRATION_COMPLETE = booleanPreferencesKey("room_migration_complete")
        val migrationMutex = Mutex()
    }
}

internal fun resolveLegacyCurrentSessionIdForMigration(
    legacyCurrentSessionId: String?,
    legacySessions: List<ChatSession>,
): String {
    if (legacySessions.isEmpty()) return legacyCurrentSessionId ?: DraftSessionId
    val firstSessionId = legacySessions.first().id
    return legacyCurrentSessionId
        ?.takeIf { id -> id == DraftSessionId || legacySessions.any { it.id == id } }
        ?: firstSessionId.takeIf { it.isNotBlank() }
        ?: DraftSessionId
}

private fun String?.toStoredCurrentSessionId(): String? = this
    ?.takeUnless { it.isBlank() || it == DraftSessionId }

private data class SessionListState(
    val rows: List<ChatSessionEntity>,
    val currentSessionId: String,
)

private fun ChatSession.toSessionEntity(sortOrder: Long): ChatSessionEntity = ChatSessionEntity(
    id = id,
    title = title,
    preview = preview,
    hasCustomTitle = hasCustomTitle,
    selectedSkillIdsJson = JSONArray().apply { selectedSkillIds.forEach(::put) }.toString(),
    activeSkillsJson = serializeActiveSkillContexts(activeSkills),
    activeMcpServerIdsJson = JSONArray().apply { activeMcpServerIds.forEach(::put) }.toString(),
    agentModeEnabled = agentModeEnabled,
    selectedModelKey = selectedModelKey,
    sortOrder = sortOrder,
)

private fun ChatSession.toSnapshot(index: Int): ChatSessionSnapshot = ChatSessionSnapshot(
    session = toSessionEntity(index.toLong()),
    messages = syncActiveBranches(messages).mapIndexed { messageIndex, message ->
        ChatMessageEntityMapper.toEntity(
            sessionId = id,
            position = messageIndex,
            message = message,
        )
    },
)

private fun ChatSessionEntity.toChatSession(messages: List<ChatMessageEntity>): ChatSession {
    val orderedMessages = messages.sortedBy { it.position }.mapIndexed { index, message ->
        ChatMessageEntityMapper.toChatMessage(message, index)
    }
    val activeSkills = parseActiveSkillContexts(activeSkillsJson)
    return ChatSession(
        id = id,
        title = title,
        preview = preview,
        hasCustomTitle = hasCustomTitle,
        messages = orderedMessages,
        selectedSkillIds = parseStringList(selectedSkillIdsJson).ifEmpty { activeSkills.map { it.skillId } },
        activeSkills = activeSkills,
        activeMcpServerIds = parseStringList(activeMcpServerIdsJson),
        agentModeEnabled = agentModeEnabled,
        selectedModelKey = selectedModelKey,
    )
}

internal fun parseChatSessions(rawValue: String): List<ChatSession> =
    parseChatSessionsForMigration(rawValue).sessions

internal data class LegacyChatSessionsParseResult(
    val sessions: List<ChatSession>,
    val recoveredFromCorruption: Boolean,
)

internal fun parseChatSessionsForMigration(rawValue: String): LegacyChatSessionsParseResult {
    if (rawValue.isBlank()) {
        return LegacyChatSessionsParseResult(
            sessions = emptyList(),
            recoveredFromCorruption = false,
        )
    }

    return runCatching {
        val sessions = JSONArray(rawValue)
        LegacyChatSessionsParseResult(
            sessions = buildList {
                for (sessionIndex in 0 until sessions.length()) {
                    val session = checkNotNull(sessions.optJSONObject(sessionIndex)) {
                        "Invalid chat session at index $sessionIndex"
                    }
                    add(
                        ChatSession(
                            id = session.optString("id").ifBlank { "session-$sessionIndex" },
                            title = session.optString("title"),
                            preview = session.optString("preview"),
                            hasCustomTitle = session.optBoolean("hasCustomTitle", false),
                            messages = parseMessages(session.optJSONArrayOrThrow("messages", sessionIndex)),
                            selectedSkillIds = parseStringList(session.optJSONArray("selectedSkillIds")).ifEmpty {
                                parseActiveSkillContexts(session.optString("activeSkillsJson")).map { it.skillId }
                            },
                            activeSkills = parseActiveSkillContexts(session.optString("activeSkillsJson")),
                            activeMcpServerIds = parseStringList(session.optJSONArray("activeMcpServerIds")),
                            agentModeEnabled = session.optBoolean("agentModeEnabled", false),
                            selectedModelKey = session.optString("selectedModelKey"),
                        )
                    )
                }
            },
            recoveredFromCorruption = false,
        )
    }.getOrElse { throwable ->
        LegacyChatSessionsParseResult(
            sessions = listOf(corruptedChatStateSession(rawValue, throwable)),
            recoveredFromCorruption = true,
        )
    }
}

private fun corruptedChatStateSession(
    rawValue: String,
    throwable: Throwable,
): ChatSession = ChatSession(
    id = "corrupt-chat-state-${rawValue.hashCode()}",
    title = "Chat storage needs recovery",
    preview = "Stored chat data could not be parsed.",
    hasCustomTitle = true,
    messages = listOf(
        ChatMessage(
            id = "agent-corrupt-chat-state-${rawValue.hashCode()}",
            author = MessageAuthor.Agent,
            text = "Aether could not read the stored chat history (${throwable.javaClass.simpleName}). " +
                "The app is showing this recovery placeholder instead of hiding the conversation list.",
            createdAtMillis = 0L,
            providerPayloadJson = rawValue,
        )
    ),
)

internal fun serializeChatSessions(sessions: List<ChatSession>): String = buildString {
    append('[')
    sessions.forEachIndexed { index, session ->
        if (index > 0) append(',')
        append(session.toJson().toString())
    }
    append(']')
}

internal fun ChatSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("preview", preview)
    put("hasCustomTitle", hasCustomTitle)
    put("agentModeEnabled", agentModeEnabled)
    put("selectedModelKey", selectedModelKey)
    put("selectedSkillIds", JSONArray().apply { selectedSkillIds.forEach(::put) })
    put("messages", JSONArray().apply { syncActiveBranches(messages).forEach { put(it.toJson()) } })
    put("activeSkillsJson", serializeActiveSkillContexts(activeSkills))
    put("activeMcpServerIds", JSONArray().apply { activeMcpServerIds.forEach(::put) })
}

private fun parseMessages(messages: JSONArray?): List<ChatMessage> {
    if (messages == null) return emptyList()

    return buildList {
        for (messageIndex in 0 until messages.length()) {
            val message = checkNotNull(messages.optJSONObject(messageIndex)) {
                "Invalid chat message at index $messageIndex"
            }
            add(parseMessage(message, messageIndex))
        }
    }
}

internal fun parseMessage(message: JSONObject, messageIndex: Int): ChatMessage = ChatMessage(
    id = message.optString("id").ifBlank { "message-$messageIndex" },
    author = if (message.optString("author") == MessageAuthor.User.name) {
        MessageAuthor.User
    } else {
        MessageAuthor.Agent
    },
    text = message.optString("text"),
    createdAtMillis = message.optLong("createdAtMillis").takeIf { it > 0L }
        ?: timestampFromMessageId(message.optString("id")),
    attachments = parseAttachments(message.optJSONArray("attachments")),
    toolInvocations = parseToolInvocations(message.optJSONArray("toolInvocations")),
    thoughtDurationMillis = if (message.has("thoughtDurationMillis")) {
        message.optLong("thoughtDurationMillis")
    } else {
        null
    },
    reasoningTrace = parseReasoningTrace(message.optJSONObject("reasoningTrace")),
    branchGroup = parseBranchGroup(message.optJSONObject("branchGroup")),
    responseGroupId = message.optString("responseGroupId").ifBlank { null },
    assistantActionsHidden = message.optBoolean("assistantActionsHidden"),
    providerPayloadJson = message.optString("providerPayloadJson"),
    displayKind = parseMessageDisplayKind(message.optString("displayKind")),
    usageStatistics = parseUsageStatistics(message.optJSONObject("usageStatistics")),
)

private fun parseMessageDisplayKind(value: String): MessageDisplayKind =
    MessageDisplayKind.entries.firstOrNull { it.name == value } ?: MessageDisplayKind.Standard

internal fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("author", author.name)
    put("text", text)
    if (createdAtMillis > 0L) {
        put("createdAtMillis", createdAtMillis)
    }
    thoughtDurationMillis?.let { put("thoughtDurationMillis", it) }
    reasoningTrace?.let { put("reasoningTrace", it.toJson()) }
    branchGroup?.let { put("branchGroup", it.toJson()) }
    responseGroupId?.let { put("responseGroupId", it) }
    if (assistantActionsHidden) {
        put("assistantActionsHidden", true)
    }
    providerPayloadJson.takeIf { it.isNotBlank() }?.let {
        put("providerPayloadJson", it)
    }
    if (displayKind != MessageDisplayKind.Standard) {
        put("displayKind", displayKind.name)
    }
    usageStatistics?.let { put("usageStatistics", it.toJson()) }
    put("toolInvocations", JSONArray().apply { toolInvocations.forEach { put(it.toJson()) } })
    put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
}

private fun parseUsageStatistics(json: JSONObject?): ChatUsageStatistics? {
    if (json == null) return null
    return ChatUsageStatistics(
        inputTokens = json.optionalLong("inputTokens"),
        outputTokens = json.optionalLong("outputTokens"),
        totalTokens = json.optionalLong("totalTokens"),
        reasoningTokens = json.optionalLong("reasoningTokens"),
        cachedInputTokens = json.optionalLong("cachedInputTokens"),
        requestCount = json.optInt("requestCount", 1).coerceAtLeast(1),
        tokenUsageSource = json.optString("tokenUsageSource").ifBlank { "unavailable" },
        startedAtMillis = json.optLong("startedAtMillis"),
        firstTokenAtMillis = json.optionalLong("firstTokenAtMillis"),
        completedAtMillis = json.optLong("completedAtMillis"),
    )
}

private fun ChatUsageStatistics.toJson(): JSONObject = JSONObject().apply {
    inputTokens?.let { put("inputTokens", it) }
    outputTokens?.let { put("outputTokens", it) }
    totalTokens?.let { put("totalTokens", it) }
    reasoningTokens?.let { put("reasoningTokens", it) }
    cachedInputTokens?.let { put("cachedInputTokens", it) }
    put("requestCount", requestCount)
    put("tokenUsageSource", tokenUsageSource)
    if (startedAtMillis > 0L) put("startedAtMillis", startedAtMillis)
    firstTokenAtMillis?.let { put("firstTokenAtMillis", it) }
    if (completedAtMillis > 0L) put("completedAtMillis", completedAtMillis)
}

private fun JSONObject.optionalLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private fun parseBranchGroup(json: JSONObject?): ChatBranchGroup? {
    if (json == null) return null
    val branchesJson = json.optJSONArray("branches") ?: return null
    val branches = buildList {
        for (index in 0 until branchesJson.length()) {
            add(parseMessages(branchesJson.optJSONArray(index)))
        }
    }.filter { it.isNotEmpty() }
    if (branches.size <= 1) return null
    return ChatBranchGroup(
        branches = branches,
        selectedIndex = json.optInt("selectedIndex", 0).coerceIn(0, branches.lastIndex),
    )
}

private fun ChatBranchGroup.toJson(): JSONObject = JSONObject().apply {
    val safeSelectedIndex = selectedIndex.coerceIn(0, branches.lastIndex.coerceAtLeast(0))
    put("selectedIndex", safeSelectedIndex)
    put(
        "branches",
        JSONArray().apply {
            branches.forEach { branch ->
                put(JSONArray().apply { branch.forEach { put(it.toJson()) } })
            }
        },
    )
}

private fun parseAttachments(attachments: JSONArray?): List<ChatAttachment> {
    if (attachments == null) return emptyList()

    return buildList {
        for (attachmentIndex in 0 until attachments.length()) {
            val attachment = attachments.optJSONObject(attachmentIndex) ?: continue
            val mimeType = attachment.optString("mimeType")
            val workspacePath = attachment.optString("workspacePath")
            add(
                ChatAttachment(
                    id = attachment.optString("id").ifBlank { "attachment-$attachmentIndex" },
                    uri = attachment.optString("uri"),
                    name = attachment.optString("name").ifBlank { "Attachment ${attachmentIndex + 1}" },
                    mimeType = mimeType,
                    sizeBytes = if (attachment.has("sizeBytes")) attachment.optLong("sizeBytes") else null,
                    kind = AttachmentKind.fromStored(
                        value = attachment.optString("kind"),
                        mimeType = mimeType,
                    ),
                    workspacePath = workspacePath,
                    workspaceState = if (workspacePath.isBlank()) {
                        AttachmentWorkspaceState.Failed
                    } else {
                        AttachmentWorkspaceState.Ready
                    },
                    workspaceError = if (workspacePath.isBlank()) {
                        "This attachment is missing its workspace copy."
                    } else {
                        ""
                    },
                    inlineBase64 = attachment.optString("inlineBase64"),
                )
            )
        }
    }
}

private fun ChatAttachment.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("uri", uri)
    put("name", name)
    put("mimeType", mimeType)
    put("kind", kind.name)
    put("workspacePath", workspacePath)
    sizeBytes?.let { put("sizeBytes", it) }
    inlineBase64.takeIf { it.isNotBlank() }?.let {
        put("inlineBase64", it)
    }
}

private fun parseReasoningTrace(json: JSONObject?): ReasoningTrace? {
    if (json == null) return null
    val id = json.optString("id").ifBlank { "reasoning-${json.optString("startedAtMillis")}" }
    return ReasoningTrace(
        id = id,
        rawText = json.optString("rawText"),
        chunks = parseReasoningSummaryChunks(json.optJSONArray("chunks")),
        toolInvocations = parseToolInvocations(json.optJSONArray("toolInvocations")),
        latestStatusText = json.optString("latestStatusText"),
        startedAtMillis = json.optLong("startedAtMillis"),
        completedAtMillis = if (json.has("completedAtMillis")) {
            json.optLong("completedAtMillis")
        } else {
            null
        },
    )
}

private fun ReasoningTrace.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("rawText", if (hasSummary) "" else rawText)
    put("latestStatusText", latestStatusText)
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("chunks", JSONArray().apply { chunks.forEach { put(it.toJson()) } })
    put("toolInvocations", JSONArray().apply { toolInvocations.forEach { put(it.toJson()) } })
}

private fun parseReasoningSummaryChunks(chunks: JSONArray?): List<ReasoningSummaryChunk> {
    if (chunks == null) return emptyList()
    return buildList {
        for (index in 0 until chunks.length()) {
            val chunk = chunks.optJSONObject(index) ?: continue
            add(
                ReasoningSummaryChunk(
                    id = chunk.optString("id").ifBlank { "reasoning-summary-$index" },
                    title = chunk.optString("title"),
                    detail = chunk.optString("detail"),
                    rawText = chunk.optString("rawText"),
                    isPending = chunk.optBoolean("isPending"),
                    createdAtMillis = chunk.optLong("createdAtMillis"),
                    timelineOrder = chunk.optLong("timelineOrder"),
                )
            )
        }
    }
}

private fun ReasoningSummaryChunk.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("detail", detail)
    put(
        "rawText",
        if (title.isNotBlank() || detail.isNotBlank()) "" else rawText,
    )
    put("isPending", isPending)
    put("createdAtMillis", createdAtMillis)
    put("timelineOrder", timelineOrder)
}

private fun parseToolInvocations(toolInvocations: JSONArray?): List<ChatToolInvocation> {
    if (toolInvocations == null) return emptyList()

    return buildList {
        for (toolIndex in 0 until toolInvocations.length()) {
            val toolInvocation = toolInvocations.optJSONObject(toolIndex) ?: continue
            add(
                ChatToolInvocation(
                    id = toolInvocation.optString("id").ifBlank { "tool-$toolIndex" },
                    toolName = toolInvocation.optString("toolName"),
                    argumentsJson = toolInvocation.optString("argumentsJson"),
                    outputJson = toolInvocation.optString("outputJson"),
                    isRunning = toolInvocation.optBoolean("isRunning"),
                    startedAtUptimeMillis = toolInvocation.optLong("startedAtUptimeMillis"),
                    completedAtUptimeMillis = if (toolInvocation.has("completedAtUptimeMillis")) {
                        toolInvocation.optLong("completedAtUptimeMillis")
                    } else {
                        null
                    },
                    startedAtMillis = toolInvocation.optLong("startedAtMillis"),
                    completedAtMillis = if (toolInvocation.has("completedAtMillis")) {
                        toolInvocation.optLong("completedAtMillis")
                    } else {
                        null
                    },
                    timelineOrder = toolInvocation.optLong("timelineOrder"),
                )
            )
        }
    }
}

private fun ChatToolInvocation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("toolName", toolName)
    put("argumentsJson", argumentsJson)
    put("outputJson", outputJson)
    put("isRunning", isRunning)
    put("startedAtUptimeMillis", startedAtUptimeMillis)
    completedAtUptimeMillis?.let { put("completedAtUptimeMillis", it) }
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("timelineOrder", timelineOrder)
}

private fun parseStringList(rawValue: String): List<String> = runCatching {
    parseStringList(JSONArray(rawValue))
}.getOrDefault(emptyList())


private fun JSONObject.optJSONArrayOrThrow(
    key: String,
    sessionIndex: Int,
): JSONArray? {
    if (!has(key) || isNull(key)) {
        return null
    }
    return checkNotNull(optJSONArray(key)) {
        "Invalid $key array for chat session at index $sessionIndex"
    }
}

private fun parseStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

internal fun timestampFromMessageId(messageId: String): Long {
    val timestamp = messageId.substringAfterLast('-', missingDelimiterValue = "")
    return timestamp.toLongOrNull()?.takeIf { it > 0L } ?: 0L
}