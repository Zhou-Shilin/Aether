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
import com.zhousl.aether.data.chatdb.ChatSessionMessageStatsEntity
import com.zhousl.aether.data.chatdb.ChatSessionSnapshot
import com.zhousl.aether.data.chatdb.ChatStateMetaEntity
import com.zhousl.aether.data.chatdb.ChatWorkspaceFileRefEntity

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val DraftSessionId = "draft"
private const val MessageJsonChunkSize = 64 * 1024
private const val WorkspaceFileRefQueryChunkSize = 500


private val Context.chatDataStore by preferencesDataStore(name = "aether_chats")

data class PersistedChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = DraftSessionId,
)

data class ChatUsageStatisticsSnapshot(
    val sessionId: String,
    val statistics: ChatUsageStatistics,
)

enum class PersistedChatWriteIntent {
    SyncSnapshot,
    DeleteSession,
    ReplaceFromImport,
}

class ChatRepository(
    private val context: Context,
    private val database: ChatHistoryDatabase = ChatHistoryDatabase.getInstance(context),
) {
    private val chatHistoryDao: ChatHistoryDao = database.chatHistoryDao()
    private val restoredMessageCache = mutableMapOf<ChatMessageCacheKey, LoadedChatMessage>()
    private val restoredMessageCacheMutex = Mutex()

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
                val currentSessionId = state.currentSessionId.takeIf { it != DraftSessionId }
                if (sessionIds.isEmpty()) {
                    clearRestoredMessageCache()
                    flowOf(
                        PersistedChatState(
                            sessions = emptyList(),
                            currentSessionId = state.currentSessionId,
                        )
                    )
                } else {
                    combine(
                        chatHistoryDao.observeMessageStatsForSessions(sessionIds),
                        if (currentSessionId == null) {
                            clearRestoredMessageCache()
                            flowOf(emptyList())
                        } else {
                            chatHistoryDao.observeMessageSummariesForSession(currentSessionId).mapLatest { summaries ->
                                restoredMessageCacheMutex.withLock {
                                    restoredMessageCache.keys.retainAll(summaries.mapTo(mutableSetOf()) { it.cacheKey })
                                    database.withTransaction {
                                        chatHistoryDao.getMessagesForSummariesSafely(
                                            summaries = summaries,
                                            restoredMessageCache = restoredMessageCache,
                                        )
                                    }
                                }
                            }
                        },
                    ) { stats, currentMessages ->
                        val statsBySessionId = stats.associateBy { it.sessionId }
                        val currentMessagesBySessionId = currentMessages.groupBy { it.sessionId }
                        val sessions = state.rows.map { session ->
                            session.toChatSession(
                                messages = currentMessagesBySessionId[session.id].orEmpty(),
                                stats = statsBySessionId[session.id],
                            )
                        }
                        PersistedChatState(
                            sessions = sessions,
                            currentSessionId = state.currentSessionId,
                        )
                    }
                }
            }
        )
    }

    suspend fun updateChatState(
        sessions: List<ChatSession>,
        currentSessionId: String,
        writeIntent: PersistedChatWriteIntent = PersistedChatWriteIntent.SyncSnapshot,
    ) {
        migrateLegacyChatStateIfNeeded()
        replaceChatState(
            sessions = sessions.mapIndexed { index, session -> session.toSnapshot(index) },
            currentSessionId = currentSessionId,
            migrationComplete = true,
            writeIntent = writeIntent,
        )
        context.chatDataStore.edit { preferences ->
            preferences.remove(SESSIONS_JSON)
            preferences.remove(CURRENT_SESSION_ID)
            preferences[ROOM_MIGRATION_COMPLETE] = true
        }
    }
    suspend fun getSessionWithMessages(sessionId: String): ChatSession? {
        migrateLegacyChatStateIfNeeded()
        return restoredMessageCacheMutex.withLock {
            database.withTransaction {
                val session = chatHistoryDao.getSession(sessionId) ?: return@withTransaction null
                val summaries = chatHistoryDao.getMessageSummariesForSession(sessionId)
                val messages = chatHistoryDao.getMessagesForSummariesSafely(
                    summaries = summaries,
                    restoredMessageCache = restoredMessageCache,
                )
                session.toChatSession(messages = messages)
            }
        }
    }

    suspend fun getSessionsWithMessages(): List<ChatSession> {
        migrateLegacyChatStateIfNeeded()
        return restoredMessageCacheMutex.withLock {
            database.withTransaction {
                val sessions = chatHistoryDao.getSessions()
                val sessionIds = sessions.map { it.id }
                if (sessionIds.isEmpty()) return@withTransaction emptyList()
                val summariesBySessionId = chatHistoryDao.getMessageSummariesForSessions(sessionIds)
                    .groupBy { it.sessionId }
                sessions.map { session ->
                    val messages = chatHistoryDao.getMessagesForSummariesSafely(
                        summaries = summariesBySessionId[session.id].orEmpty(),
                        restoredMessageCache = restoredMessageCache,
                    )
                    session.toChatSession(messages = messages)
                }
            }
        }
    }

    suspend fun getUsageStatisticsSnapshot(): List<ChatUsageStatisticsSnapshot> {
        migrateLegacyChatStateIfNeeded()
        return database.withTransaction {
            chatHistoryDao.getUsageStatisticsMessageSummaries().mapNotNull { summary ->
                val entity = try {
                    chatHistoryDao.loadMessageEntityInChunks(summary)
                } catch (throwable: Exception) {
                    if (throwable is CancellationException) throw throwable
                    null
                } ?: return@mapNotNull null
                val statistics = entity.messageJson.parseUsageStatisticsOrNull() ?: return@mapNotNull null
                ChatUsageStatisticsSnapshot(
                    sessionId = summary.sessionId,
                    statistics = statistics,
                )
            }
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
        invalidateRestoredMessage(sessionId = sessionId, messageId = message.id)
        database.withTransaction {
            val messageEntity = ChatMessageEntityMapper.toEntity(
                sessionId = sessionId,
                position = position,
                message = message,
            )
            chatHistoryDao.upsertMessage(messageEntity)
            replaceWorkspaceFileRefsForMessagesInTransaction(
                sessionId = sessionId,
                messages = listOf(message),
            )
        }
    }

    suspend fun deleteSessionById(sessionId: String) {
        migrateLegacyChatStateIfNeeded()
        invalidateRestoredSession(sessionId)
        database.withTransaction {
            chatHistoryDao.deleteSession(sessionId)
            val meta = chatHistoryDao.getMeta()
            if (meta?.currentSessionId == sessionId) {
                chatHistoryDao.upsertMeta(meta.copy(currentSessionId = null))
            }
        }
    }

    suspend fun getUnreferencedWorkspaceFilePathsForDeletedSession(sessionId: String): List<String> {
        migrateLegacyChatStateIfNeeded()
        return database.withTransaction {
            if (chatHistoryDao.getMeta()?.workspaceFileRefsComplete != true) {
                return@withTransaction emptyList()
            }
            val candidatePaths = chatHistoryDao.getWorkspaceFilePathsForSession(sessionId).normalizedWorkspaceFilePaths()
            if (candidatePaths.isEmpty()) {
                emptyList()
            } else {
                val referencedPaths = getWorkspaceFileRefsForPathsChunked(candidatePaths)
                    .asSequence()
                    .filterNot { ref -> ref.sessionId == sessionId }
                    .map { ref -> ref.path }
                    .toSet()
                candidatePaths.filterNot(referencedPaths::contains)
            }
        }
    }

    suspend fun getUnreferencedWorkspaceFilePathsForDeletedMessages(
        sessionId: String,
        messageIds: List<String>,
    ): List<String> {
        migrateLegacyChatStateIfNeeded()
        val safeMessageIds = messageIds.map(String::trim).filter(String::isNotEmpty).distinct()
        if (safeMessageIds.isEmpty()) return emptyList()
        val safeMessageIdSet = safeMessageIds.toSet()
        return database.withTransaction {
            if (chatHistoryDao.getMeta()?.workspaceFileRefsComplete != true) {
                return@withTransaction emptyList()
            }
            val candidatePaths = getWorkspaceFilePathsForMessagesChunked(
                sessionId = sessionId,
                messageIds = safeMessageIds,
            ).normalizedWorkspaceFilePaths()
            if (candidatePaths.isEmpty()) {
                emptyList()
            } else {
                val referencedPaths = getWorkspaceFileRefsForPathsChunked(candidatePaths)
                    .asSequence()
                    .filterNot { ref -> ref.sessionId == sessionId && ref.messageId in safeMessageIdSet }
                    .map { ref -> ref.path }
                    .toSet()
                candidatePaths.filterNot(referencedPaths::contains)
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
        invalidateRestoredMessagesFromPosition(sessionId = sessionId, fromPosition = fromPosition)
        database.withTransaction {
            replaceMessagesFromPositionInTransaction(sessionId, fromPosition, messages)
        }
    }

    private suspend fun replaceMessagesFromPositionInTransaction(
        sessionId: String,
        fromPosition: Int,
        messages: List<ChatMessage>,
    ) {
        chatHistoryDao.deleteWorkspaceFileRefsFromPosition(sessionId, fromPosition)
        chatHistoryDao.deleteMessagesFromPosition(sessionId, fromPosition)
        if (messages.isNotEmpty()) {
            val messageEntities = messages.mapIndexed { index, message ->
                ChatMessageEntityMapper.toEntity(
                    sessionId = sessionId,
                    position = fromPosition + index,
                    message = message,
                )
            }
            chatHistoryDao.upsertMessages(messageEntities)
            replaceWorkspaceFileRefsForMessagesInTransaction(
                sessionId = sessionId,
                messages = messages,
            )
        }
    }

    private suspend fun replaceChatState(
        sessions: List<ChatSessionSnapshot>,
        currentSessionId: String,
        migrationComplete: Boolean,
        writeIntent: PersistedChatWriteIntent = PersistedChatWriteIntent.SyncSnapshot,
    ) {
        clearRestoredMessageCache()
        database.withTransaction {
            val safeCurrentSessionId = currentSessionId
                .takeIf { id -> id == DraftSessionId || sessions.any { it.session.id == id } }
                ?: sessions.firstOrNull()?.session?.id
                ?: DraftSessionId
            if (sessions.isEmpty()) {
                if (writeIntent == PersistedChatWriteIntent.SyncSnapshot && chatHistoryDao.getSessions().isNotEmpty()) {
                    return@withTransaction
                }
                chatHistoryDao.upsertMeta(
                    ChatStateMetaEntity(
                        currentSessionId = null,
                        roomMigrationComplete = migrationComplete,
                        workspaceFileRefsComplete = true,
                    )
                )
                chatHistoryDao.deleteAllWorkspaceFileRefs()
                chatHistoryDao.deleteAllMessages()
                chatHistoryDao.deleteAllSessions()
                return@withTransaction
            }

            val sessionEntities = sessions.map { it.session }
            val sessionIds = sessionEntities.map { it.id }
            chatHistoryDao.upsertSessions(sessionEntities)
            chatHistoryDao.deleteWorkspaceFileRefsExceptSessions(sessionIds)
            chatHistoryDao.deleteSessionsExcept(sessionIds)
            chatHistoryDao.deleteMessagesExceptSessions(sessionIds)
            val existingWorkspaceFileRefsComplete = chatHistoryDao.getMeta()?.workspaceFileRefsComplete ?: true
            val existingMessageCounts = chatHistoryDao.getMessageStatsForSessions(sessionIds)
                .associate { stats -> stats.sessionId to stats.messageCount }
            chatHistoryDao.upsertMeta(
                ChatStateMetaEntity(
                    currentSessionId = safeCurrentSessionId.toStoredCurrentSessionId(),
                    roomMigrationComplete = migrationComplete,
                    workspaceFileRefsComplete = existingWorkspaceFileRefsComplete,
                )
            )

            sessions.forEach { snapshot ->
                val existingMessageCount = existingMessageCounts[snapshot.session.id] ?: 0
                val isMetadataOnlySnapshot = writeIntent != PersistedChatWriteIntent.ReplaceFromImport &&
                    snapshot.session.id != safeCurrentSessionId &&
                    snapshot.messages.isEmpty() &&
                    existingMessageCount > 0
                if (!isMetadataOnlySnapshot) {
                    chatHistoryDao.deleteWorkspaceFileRefsForSession(snapshot.session.id)
                    chatHistoryDao.deleteMessagesForSession(snapshot.session.id)
                    if (snapshot.messages.isNotEmpty()) {
                        chatHistoryDao.upsertMessages(snapshot.messages)
                        if (snapshot.workspaceFileRefs.isNotEmpty()) {
                            chatHistoryDao.upsertWorkspaceFileRefs(snapshot.workspaceFileRefs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun replaceWorkspaceFileRefsForMessagesInTransaction(
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        if (messages.isEmpty()) return
        messages.forEach { message ->
            chatHistoryDao.deleteWorkspaceFileRefsForMessage(sessionId, message.id)
        }
        val refs = messages.toWorkspaceFileRefs(sessionId)
        if (refs.isNotEmpty()) {
            chatHistoryDao.upsertWorkspaceFileRefs(refs)
        }
    }

    private suspend fun getWorkspaceFilePathsForMessagesChunked(
        sessionId: String,
        messageIds: List<String>,
    ): List<String> = messageIds.chunked(WorkspaceFileRefQueryChunkSize).flatMap { chunk ->
        chatHistoryDao.getWorkspaceFilePathsForMessages(sessionId = sessionId, messageIds = chunk)
    }

    private suspend fun getWorkspaceFileRefsForPathsChunked(paths: List<String>): List<ChatWorkspaceFileRefEntity> =
        paths.chunked(WorkspaceFileRefQueryChunkSize).flatMap { chunk ->
            chatHistoryDao.getWorkspaceFileRefsForPaths(chunk)
        }

    private fun Collection<String>.normalizedWorkspaceFilePaths(): List<String> =
        map(String::trim).filter(String::isNotEmpty).distinct().sorted()

    // TODO(Room v2): remove legacy DataStore chat import.
    private suspend fun migrateLegacyChatStateIfNeeded() = migrationMutex.withLock {
        val preferences = context.chatDataStore.data.first()
        val legacySessionsJson = preferences[SESSIONS_JSON].orEmpty()
        val legacyMigrationComplete = preferences[ROOM_MIGRATION_COMPLETE] == true
        val legacyCurrentSessionId = preferences[CURRENT_SESSION_ID]
        val roomMeta = chatHistoryDao.getMeta()

        if (roomMeta?.roomMigrationComplete == true) {
            rebuildWorkspaceFileRefsIfNeeded()
            if (legacySessionsJson.isNotBlank() || preferences[CURRENT_SESSION_ID] != null) {
                clearLegacyChatState()
            }
            return@withLock
        }

        if (legacyMigrationComplete && legacySessionsJson.isBlank()) {
            markRoomMigrationCompletePreservingExistingState()
            if (preferences[CURRENT_SESSION_ID] != null) {
                clearLegacyChatState()
            }
            return@withLock
        }

        if (legacySessionsJson.isBlank()) {
            markRoomMigrationCompletePreservingExistingState()
            clearLegacyChatState()
            return@withLock
        }

        val existingSessions = chatHistoryDao.getSessions()
        if (existingSessions.isNotEmpty()) {
            markRoomMigrationCompletePreservingExistingState()
            clearLegacyChatState()
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
            clearLegacyChatState()
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
            clearLegacyChatState()
            return@withLock
        }

        markRoomMigrationCompletePreservingExistingState()
        clearLegacyChatState()
    }

    private suspend fun markRoomMigrationCompletePreservingExistingState() {
        val existingSessions = chatHistoryDao.getSessions()
        val existingMeta = chatHistoryDao.getMeta()
        val existingSessionIds = existingSessions.mapTo(mutableSetOf()) { it.id }
        val currentSessionId = existingMeta
            ?.currentSessionId
            ?.takeIf { id -> id in existingSessionIds }

        database.withTransaction {
            chatHistoryDao.upsertMeta(
                ChatStateMetaEntity(
                    currentSessionId = currentSessionId,
                    roomMigrationComplete = true,
                    workspaceFileRefsComplete = existingMeta?.workspaceFileRefsComplete ?: existingSessions.isEmpty(),
                )
            )
        }
        rebuildWorkspaceFileRefsIfNeeded()
    }

    private suspend fun rebuildWorkspaceFileRefsIfNeeded() {
        database.withTransaction {
            val existingMeta = chatHistoryDao.getMeta() ?: return@withTransaction
            if (existingMeta.workspaceFileRefsComplete) return@withTransaction
            val refs = chatHistoryDao.getSessions()
                .map { session -> session.id }
                .chunked(WorkspaceFileRefQueryChunkSize)
                .flatMap { sessionIds -> chatHistoryDao.getMessageSummariesForSessions(sessionIds) }
                .flatMap { summary -> summary.toWorkspaceFileRefs(chatHistoryDao) }
            chatHistoryDao.deleteAllWorkspaceFileRefs()
            if (refs.isNotEmpty()) {
                chatHistoryDao.upsertWorkspaceFileRefs(refs)
            }
            chatHistoryDao.upsertMeta(existingMeta.copy(workspaceFileRefsComplete = true))
        }
    }

    private suspend fun ChatMessageSummaryEntity.toWorkspaceFileRefs(
        dao: ChatHistoryDao,
    ): List<ChatWorkspaceFileRefEntity> {
        val message = dao.loadMessageEntityInChunks(this)
            ?.let { entity -> ChatMessageEntityMapper.toChatMessage(entity, entity.position) }
            ?: ChatMessageEntityMapper.summaryToChatMessage(this)
        return message.collectWorkspaceFilePathsForIndex().map { path ->
            ChatWorkspaceFileRefEntity(
                sessionId = sessionId,
                messageId = id,
                path = path,
            )
        }.distinctBy { ref -> Triple(ref.sessionId, ref.messageId, ref.path) }
    }

    private suspend fun clearLegacyChatState() {
        context.chatDataStore.edit { data ->
            data.remove(SESSIONS_JSON)
            data.remove(CURRENT_SESSION_ID)
            data[ROOM_MIGRATION_COMPLETE] = true
        }
    }

    private suspend fun clearRestoredMessageCache() {
        restoredMessageCacheMutex.withLock {
            restoredMessageCache.clear()
        }
    }

    private suspend fun invalidateRestoredMessage(
        sessionId: String,
        messageId: String,
    ) {
        removeRestoredMessagesFromCache { cacheKey ->
            cacheKey.sessionId == sessionId && cacheKey.id == messageId
        }
    }

    private suspend fun invalidateRestoredSession(sessionId: String) {
        removeRestoredMessagesFromCache { cacheKey ->
            cacheKey.sessionId == sessionId
        }
    }

    private suspend fun invalidateRestoredMessagesFromPosition(
        sessionId: String,
        fromPosition: Int,
    ) {
        removeRestoredMessagesFromCache { cacheKey ->
            cacheKey.sessionId == sessionId && cacheKey.position >= fromPosition
        }
    }

    private suspend fun removeRestoredMessagesFromCache(
        shouldRemove: (ChatMessageCacheKey) -> Boolean,
    ) {
        restoredMessageCacheMutex.withLock {
            val iterator = restoredMessageCache.keys.iterator()
            while (iterator.hasNext()) {
                if (shouldRemove(iterator.next())) {
                    iterator.remove()
                }
            }
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

private suspend fun ChatHistoryDao.getMessagesForSummariesSafely(
    summaries: List<ChatMessageSummaryEntity>,
    restoredMessageCache: MutableMap<ChatMessageCacheKey, LoadedChatMessage>,
): List<LoadedChatMessage> = summaries.map { summary ->
    val cacheKey = summary.cacheKey
    restoredMessageCache[cacheKey] ?: run {
        val message = try {
            loadMessageEntityInChunks(summary)?.let { entity ->
                ChatMessageEntityMapper.toChatMessage(entity, entity.position)
            }
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            null
        } ?: ChatMessageEntityMapper.summaryToChatMessage(summary)

        LoadedChatMessage(
            sessionId = summary.sessionId,
            position = summary.position,
            message = message,
        ).also { loadedMessage -> restoredMessageCache[cacheKey] = loadedMessage }
    }
}

private suspend fun ChatHistoryDao.loadMessageEntityInChunks(
    summary: ChatMessageSummaryEntity,
): ChatMessageEntity? {
    val jsonLength = getMessageJsonLength(
        sessionId = summary.sessionId,
        messageId = summary.id,
    ) ?: return null
    if (jsonLength <= 0) {
        return summary.toMessageEntity(messageJson = "")
    }

    val builder = StringBuilder(jsonLength)
    var start = 1
    while (start <= jsonLength) {
        val requestedLength = minOf(MessageJsonChunkSize, jsonLength - start + 1)
        val chunk = getMessageJsonChunk(
            sessionId = summary.sessionId,
            messageId = summary.id,
            start = start,
            length = requestedLength,
        ) ?: return null
        if (chunk.isEmpty()) {
            return null
        }
        builder.append(chunk)
        start += requestedLength
    }
    return summary.toMessageEntity(builder.toString())
}

private fun ChatMessageSummaryEntity.toMessageEntity(messageJson: String): ChatMessageEntity = ChatMessageEntity(
    sessionId = sessionId,
    id = id,
    position = position,
    messageJson = messageJson,
    author = author,
    text = text,
    createdAtMillis = createdAtMillis,
    responseGroupId = responseGroupId,
    displayKind = displayKind,
    messageSchemaVersion = messageSchemaVersion,
)

private val ChatMessageSummaryEntity.cacheKey: ChatMessageCacheKey
    get() = ChatMessageCacheKey(
        sessionId = sessionId,
        id = id,
        position = position,
        author = author,
        text = text,
        createdAtMillis = createdAtMillis,
        responseGroupId = responseGroupId,
        displayKind = displayKind,
        messageSchemaVersion = messageSchemaVersion,
        messageJsonLength = messageJsonLength,
    )

private data class ChatMessageCacheKey(
    val sessionId: String,
    val id: String,
    val position: Int,
    val author: String,
    val text: String,
    val createdAtMillis: Long?,
    val responseGroupId: String?,
    val displayKind: String?,
    val messageSchemaVersion: Int,
    val messageJsonLength: Int?,
)

private data class LoadedChatMessage(
    val sessionId: String,
    val position: Int,
    val message: ChatMessage,
)

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
    chromeEnabled = chromeEnabled,
    selectedModelKey = selectedModelKey,
    sortOrder = sortOrder,
)

private fun List<ChatMessage>.toWorkspaceFileRefs(sessionId: String): List<ChatWorkspaceFileRefEntity> =
    flatMap { message ->
        message.collectWorkspaceFilePathsForIndex()
            .map { path ->
                ChatWorkspaceFileRefEntity(
                    sessionId = sessionId,
                    messageId = message.id,
                    path = path,
                )
            }
    }.distinctBy { ref -> Triple(ref.sessionId, ref.messageId, ref.path) }

private fun ChatMessage.collectWorkspaceFilePathsForIndex(): List<String> =
    (attachments
        .map { attachment -> attachment.workspacePath.trim() }
        .filter(String::isNotEmpty) +
        branchGroup
            ?.branches
            .orEmpty()
            .flatMap { branch -> branch.flatMap { it.collectWorkspaceFilePathsForIndex() } })
        .distinct()

private fun ChatSession.toSnapshot(index: Int): ChatSessionSnapshot {
    val syncedMessages = syncActiveBranches(messages)
    return ChatSessionSnapshot(
        session = toSessionEntity(index.toLong()),
        messages = syncedMessages.mapIndexed { messageIndex, message ->
            ChatMessageEntityMapper.toEntity(
                sessionId = id,
                position = messageIndex,
                message = message,
            )
        },
        workspaceFileRefs = syncedMessages.toWorkspaceFileRefs(id),
    )
}

private fun ChatSessionEntity.toChatSession(
    messages: List<LoadedChatMessage>,
    stats: ChatSessionMessageStatsEntity? = null,
): ChatSession {
    val orderedMessages = messages.sortedBy { it.position }.map { it.message }
    val activeSkills = parseActiveSkillContexts(activeSkillsJson)
    return ChatSession(
        id = id,
        title = title,
        preview = preview,
        hasCustomTitle = hasCustomTitle,
        messages = orderedMessages,
        messageCount = stats?.messageCount ?: orderedMessages.size,
        lastMessageAtMillis = stats?.lastMessageAtMillis ?: orderedMessages.maxOfOrNull { it.createdAtMillis },
        selectedSkillIds = parseStringList(selectedSkillIdsJson).ifEmpty { activeSkills.map { it.skillId } },
        activeSkills = activeSkills,
        activeMcpServerIds = parseStringList(activeMcpServerIdsJson),
        agentModeEnabled = agentModeEnabled,
        chromeEnabled = chromeEnabled,
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
                            chromeEnabled = session.optBoolean("chromeEnabled", false),
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
    put("chromeEnabled", chromeEnabled)
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

private fun String.parseUsageStatisticsOrNull(): ChatUsageStatistics? =
    runCatching {
        parseUsageStatistics(JSONObject(this).optJSONObject("usageStatistics"))
    }.getOrNull()

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
            val inlineBase64 = attachment.optString("inlineBase64")
            val kind = AttachmentKind.fromStored(
                value = attachment.optString("kind"),
                mimeType = mimeType,
            )
            val hasVisualFallback = kind == AttachmentKind.Image && inlineBase64.isNotBlank()
            add(
                ChatAttachment(
                    id = attachment.optString("id").ifBlank { "attachment-$attachmentIndex" },
                    uri = attachment.optString("uri"),
                    name = attachment.optString("name").ifBlank { "Attachment ${attachmentIndex + 1}" },
                    mimeType = mimeType,
                    sizeBytes = if (attachment.has("sizeBytes")) attachment.optLong("sizeBytes") else null,
                    kind = kind,
                    workspacePath = workspacePath,
                    workspaceState = if (workspacePath.isBlank() && !hasVisualFallback) {
                        AttachmentWorkspaceState.Failed
                    } else {
                        AttachmentWorkspaceState.Ready
                    },
                    workspaceError = if (workspacePath.isBlank() && !hasVisualFallback) {
                        "This attachment is missing its workspace copy."
                    } else {
                        ""
                    },
                    inlineBase64 = inlineBase64,
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
