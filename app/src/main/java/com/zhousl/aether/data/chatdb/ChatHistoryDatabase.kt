package com.zhousl.aether.data.chatdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChatStateMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChatHistoryDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var instance: ChatHistoryDatabase? = null

        fun getInstance(context: Context): ChatHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChatHistoryDatabase::class.java,
                "aether_chat_history.db",
            ).build().also { instance = it }
        }
    }
}