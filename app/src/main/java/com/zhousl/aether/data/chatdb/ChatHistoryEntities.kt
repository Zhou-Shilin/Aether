package com.zhousl.aether.data.chatdb

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val preview: String,
    val hasCustomTitle: Boolean,
    val selectedSkillIdsJson: String,
    val activeSkillsJson: String,
    val activeMcpServerIdsJson: String,
    val agentModeEnabled: Boolean,
    val chromeEnabled: Boolean,
    val selectedModelKey: String,
    val sortOrder: Long,
)

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["sessionId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "position"], unique = true),
        Index(value = ["sessionId", "responseGroupId"]),
        Index(value = ["sessionId", "author"]),
        Index(value = ["hasUsageStatistics"]),
    ],
)
data class ChatMessageEntity(
    val sessionId: String,
    val id: String,
    val position: Int,
    val messageJson: String,
    val author: String = "UNKNOWN",
    val text: String = "",
    val createdAtMillis: Long? = null,
    val responseGroupId: String? = null,
    val displayKind: String? = null,
    val messageSchemaVersion: Int = 1,
    val hasUsageStatistics: Boolean = false,
)

data class ChatMessageSummaryEntity(
    val sessionId: String,
    val id: String,
    val position: Int,
    val author: String = "UNKNOWN",
    val text: String = "",
    val createdAtMillis: Long? = null,
    val responseGroupId: String? = null,
    val displayKind: String? = null,
    val messageSchemaVersion: Int = 1,
    val messageJsonLength: Int? = null,
)

data class ChatSessionMessageStatsEntity(
    val sessionId: String,
    val messageCount: Int,
    val lastMessageAtMillis: Long?,
)

@Entity(
    tableName = "chat_workspace_file_refs",
    primaryKeys = ["sessionId", "messageId", "path"],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["sessionId", "id"],
            childColumns = ["sessionId", "messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["path"])],
)
data class ChatWorkspaceFileRefEntity(
    val sessionId: String,
    val messageId: String,
    val path: String,
)

@Entity(
    tableName = "chat_state_meta",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["currentSessionId"])],
)
data class ChatStateMetaEntity(
    @PrimaryKey
    val id: String = ChatStateMetaEntityId,
    val currentSessionId: String?,
    val roomMigrationComplete: Boolean,
    val workspaceFileRefsComplete: Boolean,
)

const val ChatStateMetaEntityId = "default"

data class ChatSessionSnapshot(
    val session: ChatSessionEntity,
    val messages: List<ChatMessageEntity>,
    val workspaceFileRefs: List<ChatWorkspaceFileRefEntity> = emptyList(),
)
