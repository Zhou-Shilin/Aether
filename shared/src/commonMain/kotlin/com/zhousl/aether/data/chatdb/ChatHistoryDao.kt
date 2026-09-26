package com.zhousl.aether.data.chatdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface ChatHistoryDao {
    @Transaction
    suspend fun replaceAll(
        sessions: List<ChatSessionEntity>,
        messages: List<ChatMessageEntity>,
        workspaceFileRefs: List<ChatWorkspaceFileRefEntity>,
        meta: ChatStateMetaEntity,
    ) {
        deleteAllAgentMessageRefs()
        deleteAllAgentSessions()
        deleteAllWorkspaceFileRefs()
        deleteAllMessages()
        deleteAllSessions()
        if (sessions.isNotEmpty()) upsertSessions(sessions)
        if (messages.isNotEmpty()) upsertMessages(messages)
        if (workspaceFileRefs.isNotEmpty()) upsertWorkspaceFileRefs(workspaceFileRefs)
        upsertMeta(meta)
    }

    @Query("SELECT * FROM chat_sessions ORDER BY sortOrder ASC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY sortOrder ASC")
    suspend fun getSessions(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_state_meta WHERE id = :id")
    fun observeMeta(id: String = ChatStateMetaEntityId): Flow<ChatStateMetaEntity?>

    @Query("SELECT * FROM chat_state_meta WHERE id = :id")
    suspend fun getMeta(id: String = ChatStateMetaEntityId): ChatStateMetaEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_agent_sessions WHERE chatSessionId = :sessionId")
    suspend fun getAgentSession(sessionId: String): ChatAgentSessionEntity?

    @Query("SELECT * FROM chat_agent_sessions ORDER BY updatedAtMillis DESC")
    suspend fun getAgentSessions(): List<ChatAgentSessionEntity>

    @Query("SELECT * FROM chat_agent_message_refs WHERE chatSessionId = :sessionId ORDER BY aetherMessageId, ordinal")
    suspend fun getAgentMessageRefs(sessionId: String): List<ChatAgentMessageRefEntity>

    @Query("SELECT * FROM chat_agent_message_refs WHERE chatSessionId = :sessionId AND aetherMessageId = :messageId ORDER BY ordinal")
    suspend fun getAgentMessageRefs(sessionId: String, messageId: String): List<ChatAgentMessageRefEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND position >= 0")
    suspend fun getMessageCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId AND position >= :fromPosition")
    suspend fun getMessageCountForResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
    ): Int

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND position >= 0 ORDER BY position ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND position >= :fromPosition AND position < :toPosition ORDER BY position ASC")
    suspend fun getMessagesForSessionInPositionRange(
        sessionId: String,
        fromPosition: Int,
        toPosition: Int,
    ): List<ChatMessageEntity>

    @Query("""
        SELECT sessionId, COUNT(*) AS messageCount, MAX(COALESCE(createdAtMillis, 0)) AS lastMessageAtMillis
        FROM chat_messages
        WHERE sessionId IN (:sessionIds) AND position >= 0
        GROUP BY sessionId
    """)
    suspend fun getMessageStatsForSessions(sessionIds: List<String>): List<ChatSessionMessageStatsEntity>

    @Query("""
        SELECT sessionId, COUNT(*) AS messageCount, MAX(COALESCE(createdAtMillis, 0)) AS lastMessageAtMillis
        FROM chat_messages
        WHERE sessionId IN (:sessionIds) AND position >= 0
        GROUP BY sessionId
    """)
    fun observeMessageStatsForSessions(sessionIds: List<String>): Flow<List<ChatSessionMessageStatsEntity>>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength, isIncomplete
        FROM chat_messages
        WHERE hasUsageStatistics = 1 AND position >= 0
        ORDER BY sessionId ASC, position ASC
    """)
    suspend fun getUsageStatisticsMessageSummaries(): List<ChatMessageSummaryEntity>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength, isIncomplete
        FROM chat_messages
        WHERE sessionId = :sessionId AND position >= 0
        ORDER BY position ASC
    """)
    fun observeMessageSummariesForSession(sessionId: String): Flow<List<ChatMessageSummaryEntity>>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength, isIncomplete
        FROM chat_messages
        WHERE sessionId IN (:sessionIds) AND position >= 0
        ORDER BY sessionId ASC, position ASC
    """)
    suspend fun getMessageSummariesForSessions(sessionIds: List<String>): List<ChatMessageSummaryEntity>

    suspend fun getMessageSummariesForSession(sessionId: String): List<ChatMessageSummaryEntity> =
        observeMessageSummariesForSession(sessionId).first()

    @Query("""
        SELECT length(messageJson)
        FROM chat_messages
        WHERE sessionId = :sessionId AND id = :messageId
    """)
    suspend fun getMessageJsonLength(
        sessionId: String,
        messageId: String,
    ): Int?

    @Query("""
        SELECT substr(messageJson, :start, :length)
        FROM chat_messages
        WHERE sessionId = :sessionId AND id = :messageId
    """)
    suspend fun getMessageJsonChunk(
        sessionId: String,
        messageId: String,
        start: Int,
        length: Int,
    ): String?

    @Query("""
        SELECT DISTINCT path
        FROM chat_workspace_file_refs
        WHERE sessionId = :sessionId
        ORDER BY path ASC
    """)
    suspend fun getWorkspaceFilePathsForSession(sessionId: String): List<String>

    @Query("""
        SELECT DISTINCT path
        FROM chat_workspace_file_refs
        WHERE sessionId = :sessionId AND messageId IN (:messageIds)
        ORDER BY path ASC
    """)
    suspend fun getWorkspaceFilePathsForMessages(
        sessionId: String,
        messageIds: List<String>,
    ): List<String>

    @Query("""
        SELECT sessionId, messageId, path
        FROM chat_workspace_file_refs
        WHERE path IN (:paths)
        ORDER BY path ASC
    """)
    suspend fun getWorkspaceFileRefsForPaths(paths: List<String>): List<ChatWorkspaceFileRefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: ChatStateMetaEntity)

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Upsert
    suspend fun upsertSessions(sessions: List<ChatSessionEntity>)

    @Upsert
    suspend fun upsertAgentSession(session: ChatAgentSessionEntity)

    @Upsert
    suspend fun upsertAgentMessageRefs(refs: List<ChatAgentMessageRefEntity>)

    @Query("UPDATE chat_sessions SET selectedModelKey = :selectedModelKey WHERE id = :sessionId")
    suspend fun updateSelectedModelKey(sessionId: String, selectedModelKey: String)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Upsert
    suspend fun upsertWorkspaceFileRefs(refs: List<ChatWorkspaceFileRefEntity>)

    /**
     * Synchronizes the active history from its first changed message.
     *
     * Compare before clearing parked checkpoint rows so unchanged history and its references remain intact.
     */
    @Transaction
    suspend fun syncMessagesForSession(
        sessionId: String,
        messages: List<ChatMessageEntity>,
        retainedAgentMessageIds: Set<String>,
        workspaceFileRefs: List<ChatWorkspaceFileRefEntity> = emptyList(),
    ) {
        val canonical = canonicalActiveChatMessages(sessionId, messages)
        val retainedMessageIds = retainedAgentMessageIds + canonical.map(ChatMessageEntity::id)
        // Retain branch-only IDs from the snapshot; they have no row in chat_messages.
        getAgentMessageRefs(sessionId)
            .asSequence()
            .map(ChatAgentMessageRefEntity::aetherMessageId)
            .distinct()
            .filterNot(retainedMessageIds::contains)
            .toList()
            .chunked(ChatHistoryAgentRefSyncChunkSize)
            .forEach { batch -> deleteAgentMessageRefsForMessages(sessionId, batch) }
        val existingCount = getMessageCountForSession(sessionId)
        var firstChangedPosition: Int? = null
        for (startPosition in canonical.indices step ChatHistoryMessageSyncChunkSize) {
            val endPosition = minOf(startPosition + ChatHistoryMessageSyncChunkSize, canonical.size)
            val existingBatch = getMessagesForSessionInPositionRange(sessionId, startPosition, endPosition)
            val incomingBatch = canonical.subList(startPosition, endPosition)
            firstChangedPosition = firstChangedMessagePosition(existingBatch, incomingBatch, startPosition)
            if (firstChangedPosition != null) break
        }
        val syncFromPosition = firstChangedPosition ?: canonical.size.takeIf { existingCount > canonical.size }
        if (syncFromPosition == null) {
            if (getStoredParkedMessageCount(sessionId) > 0) {
                deleteParkedMessagesForSession(sessionId)
                deleteWorkspaceFileRefsForInactiveMessages(sessionId)
            }
            return
        }

        val changedMessages = canonical.subList(syncFromPosition, canonical.size)
        deleteWorkspaceFileRefsFromPosition(sessionId, syncFromPosition)
        deleteMessagesFromPosition(sessionId, syncFromPosition)
        deleteParkedMessagesForSession(sessionId)
        changedMessages
            .chunked(ChatHistoryMessageSyncChunkSize)
            .forEach { batch -> upsertMessages(batch) }
        deleteWorkspaceFileRefsForInactiveMessages(sessionId)
        val changedMessageIds = changedMessages.asSequence().map(ChatMessageEntity::id).toSet()
        workspaceFileRefs
            .asSequence()
            .filter { it.messageId in changedMessageIds }
            .chunked(ChatHistoryWorkspaceRefSyncChunkSize)
            .forEach { batch -> upsertWorkspaceFileRefs(batch.toList()) }
    }

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND position < 0")
    suspend fun getStoredParkedMessageCount(sessionId: String): Int

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND position < 0")
    suspend fun deleteParkedMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId = :messageId")
    suspend fun deleteWorkspaceFileRefsForMessage(sessionId: String, messageId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId AND position >= :fromPosition)")
    suspend fun deleteWorkspaceFileRefsForResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
    )

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId AND position >= :fromPosition)")
    suspend fun deleteWorkspaceFileRefsFromPosition(sessionId: String, fromPosition: Int)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId NOT IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId AND position >= 0)")
    suspend fun deleteWorkspaceFileRefsForInactiveMessages(sessionId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId")
    suspend fun deleteWorkspaceFileRefsForSession(sessionId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId NOT IN (:sessionIds)")
    suspend fun deleteWorkspaceFileRefsExceptSessions(sessionIds: List<String>)

    @Query("DELETE FROM chat_workspace_file_refs")
    suspend fun deleteAllWorkspaceFileRefs()

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND position >= :fromPosition")
    suspend fun deleteMessagesFromPosition(sessionId: String, fromPosition: Int)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId AND position >= :fromPosition")
    suspend fun deleteMessagesForResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
    )

    @Query("""
        SELECT id
        FROM chat_messages
        WHERE sessionId = :sessionId
            AND position >= :fromPosition
            AND position < :toPosition
            AND (responseGroupId IS NULL OR responseGroupId != :responseGroupId)
    """)
    suspend fun getMessageIdsToParkOutsideResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
        toPosition: Int,
    ): List<String>

    @Query("""
        UPDATE chat_messages
        SET position = -position - 1
        WHERE sessionId = :sessionId
            AND position >= :fromPosition
            AND position < :toPosition
            AND (responseGroupId IS NULL OR responseGroupId != :responseGroupId)
    """)
    suspend fun parkMessagesFromPositionOutsideResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
        toPosition: Int,
    )

    @Query("""
        UPDATE chat_messages
        SET position = (-position - 1) + :positionDelta
        WHERE sessionId = :sessionId
            AND id IN (:parkedMessageIds)
            AND position >= (0 - :toPosition)
            AND position <= (0 - :fromPosition) - 1
            AND (-position - 1) + :positionDelta >= :checkpointEndPosition
            AND (responseGroupId IS NULL OR responseGroupId != :responseGroupId)
    """)
    suspend fun restoreParkedMessagesOutsideResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
        toPosition: Int,
        checkpointEndPosition: Int,
        parkedMessageIds: List<String>,
        positionDelta: Int,
    )

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_agent_sessions WHERE chatSessionId = :sessionId")
    suspend fun deleteAgentSession(sessionId: String)

    @Query("DELETE FROM chat_agent_message_refs WHERE chatSessionId = :sessionId AND aetherMessageId IN (:messageIds)")
    suspend fun deleteAgentMessageRefsForMessages(sessionId: String, messageIds: List<String>)

    @Query("DELETE FROM chat_agent_message_refs WHERE chatSessionId = :sessionId")
    suspend fun deleteAgentMessageRefs(sessionId: String)

    @Query("DELETE FROM chat_agent_sessions")
    suspend fun deleteAllAgentSessions()

    @Query("DELETE FROM chat_agent_message_refs")
    suspend fun deleteAllAgentMessageRefs()

    @Query("DELETE FROM chat_sessions WHERE id NOT IN (:sessionIds)")
    suspend fun deleteSessionsExcept(sessionIds: List<String>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId NOT IN (:sessionIds)")
    suspend fun deleteMessagesExceptSessions(sessionIds: List<String>)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}
