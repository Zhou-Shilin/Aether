package com.zhousl.aether.data.chatdb

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface ChatHistoryDao {
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

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId")
    suspend fun getMessageCountForResponseGroup(sessionId: String, responseGroupId: String): Int

    @Query("""
        SELECT sessionId, COUNT(*) AS messageCount, MAX(COALESCE(createdAtMillis, 0)) AS lastMessageAtMillis
        FROM chat_messages
        WHERE sessionId IN (:sessionIds)
        GROUP BY sessionId
    """)
    suspend fun getMessageStatsForSessions(sessionIds: List<String>): List<ChatSessionMessageStatsEntity>

    @Query("""
        SELECT sessionId, COUNT(*) AS messageCount, MAX(COALESCE(createdAtMillis, 0)) AS lastMessageAtMillis
        FROM chat_messages
        WHERE sessionId IN (:sessionIds)
        GROUP BY sessionId
    """)
    fun observeMessageStatsForSessions(sessionIds: List<String>): Flow<List<ChatSessionMessageStatsEntity>>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength
        FROM chat_messages
        WHERE hasUsageStatistics = 1
        ORDER BY sessionId ASC, position ASC
    """)
    suspend fun getUsageStatisticsMessageSummaries(): List<ChatMessageSummaryEntity>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength
        FROM chat_messages
        WHERE sessionId = :sessionId
        ORDER BY position ASC
    """)
    fun observeMessageSummariesForSession(sessionId: String): Flow<List<ChatMessageSummaryEntity>>

    @Query("""
        SELECT sessionId, id, position, author, text, createdAtMillis, responseGroupId, displayKind, messageSchemaVersion, length(messageJson) AS messageJsonLength
        FROM chat_messages
        WHERE sessionId IN (:sessionIds)
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

    @Upsert
    suspend fun upsertMeta(meta: ChatStateMetaEntity)

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Upsert
    suspend fun upsertSessions(sessions: List<ChatSessionEntity>)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Upsert
    suspend fun upsertWorkspaceFileRefs(refs: List<ChatWorkspaceFileRefEntity>)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId = :messageId")
    suspend fun deleteWorkspaceFileRefsForMessage(sessionId: String, messageId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId)")
    suspend fun deleteWorkspaceFileRefsForResponseGroup(sessionId: String, responseGroupId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId AND messageId IN (SELECT id FROM chat_messages WHERE sessionId = :sessionId AND position >= :fromPosition)")
    suspend fun deleteWorkspaceFileRefsFromPosition(sessionId: String, fromPosition: Int)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId = :sessionId")
    suspend fun deleteWorkspaceFileRefsForSession(sessionId: String)

    @Query("DELETE FROM chat_workspace_file_refs WHERE sessionId NOT IN (:sessionIds)")
    suspend fun deleteWorkspaceFileRefsExceptSessions(sessionIds: List<String>)

    @Query("DELETE FROM chat_workspace_file_refs")
    suspend fun deleteAllWorkspaceFileRefs()

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND position >= :fromPosition")
    suspend fun deleteMessagesFromPosition(sessionId: String, fromPosition: Int)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND responseGroupId = :responseGroupId")
    suspend fun deleteMessagesForResponseGroup(sessionId: String, responseGroupId: String)

    @Query("""
        UPDATE chat_messages
        SET position = -position - 1
        WHERE sessionId = :sessionId
            AND position >= :fromPosition
            AND (responseGroupId IS NULL OR responseGroupId != :responseGroupId)
    """)
    suspend fun parkMessagesFromPositionOutsideResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
    )

    @Query("""
        UPDATE chat_messages
        SET position = (-position - 1) + :positionDelta
        WHERE sessionId = :sessionId
            AND position <= -:fromPosition - 1
            AND (responseGroupId IS NULL OR responseGroupId != :responseGroupId)
    """)
    suspend fun restoreParkedMessagesOutsideResponseGroup(
        sessionId: String,
        responseGroupId: String,
        fromPosition: Int,
        positionDelta: Int,
    )

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

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
