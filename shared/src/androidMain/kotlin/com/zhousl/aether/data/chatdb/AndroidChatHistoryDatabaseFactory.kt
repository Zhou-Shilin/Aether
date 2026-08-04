package com.zhousl.aether.data.chatdb

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AndroidChatHistoryDatabaseFactory {
    @Volatile
    private var instance: ChatHistoryDatabase? = null

    fun getInstance(context: Context): ChatHistoryDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            ChatHistoryDatabase::class.java,
            "aether_chat_history.db",
        ).addMigrations(Migration1To2, Migration2To3, Migration3To4)
            .build()
            .also { instance = it }
    }
}

object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_workspace_file_refs` (
                `sessionId` TEXT NOT NULL,
                `messageId` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                PRIMARY KEY(`sessionId`, `messageId`, `path`),
                FOREIGN KEY(`sessionId`, `messageId`) REFERENCES `chat_messages`(`sessionId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_workspace_file_refs_path` ON `chat_workspace_file_refs` (`path`)",
        )
        db.execSQL(
            "ALTER TABLE `chat_state_meta` ADD COLUMN `workspaceFileRefsComplete` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `chat_messages` ADD COLUMN `hasUsageStatistics` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            UPDATE `chat_messages`
            SET `hasUsageStatistics` = 1
            WHERE `messageJson` LIKE '%"usageStatistics"%'
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_messages_hasUsageStatistics` ON `chat_messages` (`hasUsageStatistics`)",
        )
    }
}

object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `chat_sessions` ADD COLUMN `chromeEnabled` INTEGER NOT NULL DEFAULT 0",
        )
    }
}
