package com.zhousl.aether.data.chatdb

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.zhousl.aether.data.ChatMessageEntityMapper
import com.zhousl.aether.ui.MessageAuthor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun migrate1To5PreservesDataAndAddsCurrentSchema() {
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
                    '{"usageStatistics":{"inputTokens":1},"isIncomplete":true}',
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
                INSERT INTO chat_messages (
                    sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion
                ) VALUES (
                    'session-1', 'message-3', 2,
                    '{"note":"\"isIncomplete\":true","isIncomplete":false}',
                    'assistant', 'Complete with lookalike text', NULL, NULL, NULL, 1
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
            5,
            true,
            *ChatHistoryMigrations,
        ).use { database ->
            database.query(
                "SELECT id, chromeEnabled FROM chat_sessions WHERE id = 'session-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("session-1", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }

            database.query(
                "SELECT id, hasUsageStatistics, isIncomplete FROM chat_messages ORDER BY position",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("message-1", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertTrue(cursor.moveToNext())
                assertEquals("message-2", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertTrue(cursor.moveToNext())
                assertEquals("message-3", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
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

    @Test
    fun migrate4To5LeavesMalformedMessageJsonForMapperRecovery() {
        val malformedJson = "{not-valid-json"
        helper.createDatabase(MALFORMED_JSON_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, title, preview, hasCustomTitle, selectedSkillIdsJson,
                    activeSkillsJson, activeMcpServerIdsJson, agentModeEnabled,
                    chromeEnabled, selectedModelKey, sortOrder
                ) VALUES ('session-1', 'Title', 'Preview', 0, '[]', '[]', '[]', 0, 0, '', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chat_messages (
                    sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion,
                    hasUsageStatistics
                ) VALUES (
                    'session-1', 'message-1', 0, '{"isIncomplete":true}',
                    'assistant', 'Incomplete response', 1001, 'group-1', NULL, 1, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chat_messages (
                    sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion,
                    hasUsageStatistics
                ) VALUES (
                    'session-1', 'message-2', 1, '$malformedJson',
                    'assistant', 'Partial response', 1002, 'group-2', NULL, 1, 0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            MALFORMED_JSON_DATABASE,
            5,
            true,
            *ChatHistoryMigrations,
        ).use { database ->
            database.query(
                "SELECT id, isIncomplete FROM chat_messages ORDER BY position",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("message-1", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertTrue(cursor.moveToNext())
                assertEquals("message-2", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }

            database.query(
                """
                SELECT sessionId, id, position, messageJson, author, text,
                    createdAtMillis, responseGroupId, displayKind, messageSchemaVersion,
                    hasUsageStatistics, isIncomplete
                FROM chat_messages
                WHERE id = 'message-2'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                val recovered = ChatMessageEntityMapper.toChatMessage(
                    entity = ChatMessageEntity(
                        sessionId = cursor.getString(0),
                        id = cursor.getString(1),
                        position = cursor.getInt(2),
                        messageJson = cursor.getString(3),
                        author = cursor.getString(4),
                        text = cursor.getString(5),
                        createdAtMillis = cursor.getLong(6),
                        responseGroupId = cursor.getString(7),
                        displayKind = cursor.getString(8),
                        messageSchemaVersion = cursor.getInt(9),
                        hasUsageStatistics = cursor.getInt(10) != 0,
                        isIncomplete = cursor.getInt(11) != 0,
                    ),
                    messageIndex = 1,
                )

                assertEquals("message-2", recovered.id)
                assertEquals(MessageAuthor.Agent, recovered.author)
                assertEquals("Partial response", recovered.text)
                assertEquals(1002L, recovered.createdAtMillis)
                assertEquals("group-2", recovered.responseGroupId)
                assertEquals(malformedJson, recovered.providerPayloadJson)
                assertFalse(recovered.isIncomplete)
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "chat-history-migration-test"
        const val MALFORMED_JSON_DATABASE = "chat-history-malformed-json-migration-test"
    }
}