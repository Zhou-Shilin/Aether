package com.zhousl.aether.data.chatdb

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatAgentSessionEntity::class,
        ChatAgentMessageRefEntity::class,
        ChatMessageEntity::class,
        ChatWorkspaceFileRefEntity::class,
        ChatStateMetaEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@ConstructedBy(ChatHistoryDatabaseConstructor::class)
abstract class ChatHistoryDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ChatHistoryDatabaseConstructor : RoomDatabaseConstructor<ChatHistoryDatabase> {
    override fun initialize(): ChatHistoryDatabase
}
