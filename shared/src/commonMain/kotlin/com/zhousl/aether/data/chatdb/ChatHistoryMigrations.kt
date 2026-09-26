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

val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_agent_sessions` (
                `chatSessionId` TEXT NOT NULL,
                `piSessionId` TEXT NOT NULL,
                `jsonlPath` TEXT NOT NULL,
                `runtime` TEXT NOT NULL,
                `migrationVersion` INTEGER NOT NULL,
                `updatedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`chatSessionId`),
                FOREIGN KEY(`chatSessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_agent_sessions_piSessionId` ON `chat_agent_sessions` (`piSessionId`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_agent_message_refs` (
                `chatSessionId` TEXT NOT NULL,
                `aetherMessageId` TEXT NOT NULL,
                `piEntryId` TEXT NOT NULL,
                `ordinal` INTEGER NOT NULL,
                PRIMARY KEY(`chatSessionId`, `aetherMessageId`, `piEntryId`),
                FOREIGN KEY(`chatSessionId`, `aetherMessageId`) REFERENCES `chat_messages`(`sessionId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_agent_message_refs_chatSessionId_piEntryId` ON `chat_agent_message_refs` (`chatSessionId`, `piEntryId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_agent_message_refs_chatSessionId_aetherMessageId` ON `chat_agent_message_refs` (`chatSessionId`, `aetherMessageId`)",
        )
    }
}

/** Remove legacy per-chat Skill/MCP activation state. Pi's SessionManager is now authoritative. */
val Migration6To7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys=OFF")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_sessions_new` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `preview` TEXT NOT NULL,
                `hasCustomTitle` INTEGER NOT NULL,
                `agentModeEnabled` INTEGER NOT NULL,
                `chromeEnabled` INTEGER NOT NULL,
                `selectedModelKey` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `chat_sessions_new` (
                `id`, `title`, `preview`, `hasCustomTitle`, `agentModeEnabled`,
                `chromeEnabled`, `selectedModelKey`, `sortOrder`
            )
            SELECT `id`, `title`, `preview`, `hasCustomTitle`, `agentModeEnabled`,
                `chromeEnabled`, `selectedModelKey`, `sortOrder`
            FROM `chat_sessions`
            """.trimIndent(),
        )
        connection.execSQL("DROP TABLE `chat_sessions`")
        connection.execSQL("ALTER TABLE `chat_sessions_new` RENAME TO `chat_sessions`")
        connection.execSQL("PRAGMA foreign_keys=ON")
    }
}

val ChatHistoryMigrations = arrayOf(
    Migration1To2,
    Migration2To3,
    Migration3To4,
    Migration4To5,
    Migration5To6,
    Migration6To7,
)
