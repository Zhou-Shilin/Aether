package com.zhousl.aether.data.chatdb

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatHistoryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatHistoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To4PreservesDataAndAddsCurrentSchema() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, title, preview, hasCustomTitle, selectedSkillIdsJson,
                    activeSkillsJson, activeMcpServerIdsJson, agentModeEnabled,
                    selectedModelKey, sortOrder
                ) VALUES ('session-1', 'Title', 'Preview', 0, '[]', '[]', '[]', 0, '', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chat_messages (
                    sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion
                ) VALUES (
                    'session-1', 'message-1', 0,
                    '{"usageStatistics":{"inputTokens":1}}',
                    'assistant', 'With usage', NULL, NULL, NULL, 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chat_messages (
                    sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion
                ) VALUES (
                    'session-1', 'message-2', 1, '{}',
                    'user', 'Without usage', NULL, NULL, NULL, 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chat_state_meta (id, currentSessionId, roomMigrationComplete)
                VALUES ('singleton', 'session-1', 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            Migration1To2,
            Migration2To3,
            Migration3To4,
        ).use { database ->
            database.query(
                "SELECT id, chromeEnabled FROM chat_sessions WHERE id = 'session-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("session-1", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }

            database.query(
                "SELECT id, hasUsageStatistics FROM chat_messages ORDER BY position",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("message-1", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertTrue(cursor.moveToNext())
                assertEquals("message-2", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }

            database.query("PRAGMA table_info(chat_workspace_file_refs)").use { cursor ->
                assertTrue(cursor.count > 0)
            }

            database.query(
                "SELECT workspaceFileRefsComplete FROM chat_state_meta WHERE id = 'singleton'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "chat-history-migration-test"
    }
}
