package com.zhousl.aether.data.chatdb

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
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
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_workspace_file_refs_path` ON `chat_workspace_file_refs` (`path`)",
        )
        connection.execSQL(
            "ALTER TABLE `chat_state_meta` ADD COLUMN `workspaceFileRefsComplete` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `chat_messages` ADD COLUMN `hasUsageStatistics` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            """
            UPDATE `chat_messages`
            SET `hasUsageStatistics` = 1
            WHERE `messageJson` LIKE '%"usageStatistics"%'
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_messages_hasUsageStatistics` ON `chat_messages` (`hasUsageStatistics`)",
        )
    }
}

val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `chat_sessions` ADD COLUMN `chromeEnabled` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `chat_messages` ADD COLUMN `isIncomplete` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            """
            UPDATE `chat_messages`
            SET `isIncomplete` = 1
            WHERE json_valid(`messageJson`) = 1
                AND json_extract(`messageJson`, '$.isIncomplete') = 1
            """.trimIndent(),
        )
    }
}

val ChatHistoryMigrations = arrayOf(
    Migration1To2,
    Migration2To3,
    Migration3To4,
    Migration4To5,
)