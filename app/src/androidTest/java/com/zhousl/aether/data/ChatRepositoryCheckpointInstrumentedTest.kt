package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhousl.aether.data.chatdb.ChatHistoryDatabase
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.MessageAuthor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private val TestSessionsJson = stringPreferencesKey("sessions_json")
private val TestCurrentSessionId = stringPreferencesKey("current_session_id")
private val TestRoomMigrationComplete = booleanPreferencesKey("room_migration_complete")

private suspend fun clearLegacyChatState(context: Context) {
    context.chatDataStore.edit { preferences ->
        preferences.remove(TestSessionsJson)
        preferences.remove(TestCurrentSessionId)
        preferences.remove(TestRoomMigrationComplete)
    }
}

@RunWith(AndroidJUnit4::class)
class ChatRepositoryCheckpointInstrumentedTest {
    private lateinit var database: ChatHistoryDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { clearLegacyChatState(context) }
        database = Room.inMemoryDatabaseBuilder(context, ChatHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { clearLegacyChatState(context) }
    }

    @Test
    fun checkpointUpsertTouchesOnlyTargetResponseAndCompletionReplacesIt() = runBlocking {
        val sessionId = "session-checkpoint"
        val responseGroupId = "agent-group-turn-checkpoint"
        val before = ChatMessage(
            id = "user-before",
            author = MessageAuthor.User,
            text = "before",
        )
        val checkpointText = ChatMessage(
            id = "agent-checkpoint-text",
            author = MessageAuthor.Agent,
            text = "partial",
            isIncomplete = true,
            responseGroupId = responseGroupId,
        )
        val checkpointTool = ChatMessage(
            id = "agent-checkpoint-tool",
            author = MessageAuthor.Agent,
            text = "tool output",
            isIncomplete = true,
            responseGroupId = responseGroupId,
        )
        val session = ChatSession(
            id = sessionId,
            title = "Checkpoint",
            preview = "before",
            messages = listOf(before),
        )
        val unrelated = ChatSession(
            id = "unrelated-session",
            title = "Unrelated",
            preview = "untouched",
            messages = listOf(
                ChatMessage(
                    id = "unrelated-message",
                    author = MessageAuthor.User,
                    text = "untouched",
                ),
            ),
        )

        repository.updateChatState(
            sessions = listOf(session, unrelated),
            currentSessionId = sessionId,
        )
        repository.upsertAssistantResponseCheckpoints(
            checkpoints = listOf(
                AssistantResponseCheckpoint(
                    target = AssistantResponseCheckpointTarget(
                        sessionId = sessionId,
                        responseGroupId = responseGroupId,
                    ),
                    fromPosition = 1,
                    messages = listOf(checkpointText),
                ),
            ),
        )
        repository.upsertAssistantResponseCheckpoints(
            checkpoints = listOf(
                AssistantResponseCheckpoint(
                    target = AssistantResponseCheckpointTarget(
                        sessionId = sessionId,
                        responseGroupId = responseGroupId,
                    ),
                    fromPosition = 1,
                    messages = listOf(
                        checkpointText.copy(text = "more partial"),
                        checkpointTool,
                    ),
                ),
            ),
        )

        val checkpointPositions = database.chatHistoryDao()
            .getMessageSummariesForSession(sessionId)
            .map { it.position }
        val checkpointSession = repository.getSessionWithMessages(sessionId)
        val checkpointMessages = checkpointSession?.messages.orEmpty()
        assertEquals(listOf(0, 1, 2), checkpointPositions)
        assertEquals(
            listOf("user-before", "agent-checkpoint-text", "agent-checkpoint-tool"),
            checkpointMessages.map { it.id },
        )
        assertEquals("more partial", checkpointMessages[1].text)
        assertTrue(checkpointMessages.drop(1).all { it.isIncomplete })
        assertEquals("before", checkpointSession?.preview)
        assertEquals(
            listOf("unrelated-message"),
            repository.getSessionWithMessages(unrelated.id)?.messages.orEmpty().map { it.id },
        )

        val reshapedCheckpoint = ChatMessage(
            id = "agent-checkpoint-reshaped",
            author = MessageAuthor.Agent,
            text = "reshaped partial",
            isIncomplete = true,
            responseGroupId = responseGroupId,
        )
        repository.upsertAssistantResponseCheckpoints(
            checkpoints = listOf(
                AssistantResponseCheckpoint(
                    target = AssistantResponseCheckpointTarget(
                        sessionId = sessionId,
                        responseGroupId = responseGroupId,
                    ),
                    fromPosition = 1,
                    messages = listOf(reshapedCheckpoint),
                ),
            ),
        )

        val reshapedPositions = database.chatHistoryDao()
            .getMessageSummariesForSession(sessionId)
            .map { it.position }
        val reshapedSession = repository.getSessionWithMessages(sessionId)
        assertEquals(listOf(0, 1), reshapedPositions)
        assertEquals(
            listOf("user-before", "agent-checkpoint-reshaped"),
            reshapedSession?.messages.orEmpty().map { it.id },
        )

        val completedMessages = listOf(
            reshapedCheckpoint.copy(text = "complete", isIncomplete = false),
        )
        repository.updateChatState(
            sessions = listOf(
                session.copy(
                    preview = "complete",
                    messages = listOf(before) + completedMessages,
                ),
                unrelated,
            ),
            currentSessionId = sessionId,
        )

        val completedPositions = database.chatHistoryDao()
            .getMessageSummariesForSession(sessionId)
            .map { it.position }
        val restoredCompletion = repository.getSessionWithMessages(sessionId)
        assertEquals(listOf(0, 1), completedPositions)
        assertEquals(
            listOf("user-before", "agent-checkpoint-reshaped"),
            restoredCompletion?.messages.orEmpty().map { it.id },
        )
        assertTrue(restoredCompletion?.messages.orEmpty().drop(1).all { !it.isIncomplete })
        assertFalse(restoredCompletion?.messages.orEmpty()[1].isIncomplete)
        assertEquals(responseGroupId, restoredCompletion?.messages.orEmpty()[1].responseGroupId)
        assertEquals("complete", restoredCompletion?.preview)
    }

    @Test
    fun checkpointUpsertPreservesUnrelatedMessagesAfterTarget() = runBlocking {
        val sessionId = "session-checkpoint-tail"
        val responseGroupId = "agent-group-target"
        val otherResponseGroupId = "agent-group-other"
        val before = ChatMessage(
            id = "user-before-tail",
            author = MessageAuthor.User,
            text = "before",
        )
        val oldCheckpointMessages = listOf(
            ChatMessage(
                id = "agent-target-text",
                author = MessageAuthor.Agent,
                text = "partial",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
            ChatMessage(
                id = "agent-target-tool",
                author = MessageAuthor.Agent,
                text = "tool output",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
        )
        val userAfter = ChatMessage(
            id = "user-after-target",
            author = MessageAuthor.User,
            text = "keep this user message",
        )
        val otherResponse = ChatMessage(
            id = "agent-other-response",
            author = MessageAuthor.Agent,
            text = "keep this response",
            responseGroupId = otherResponseGroupId,
        )
        val session = ChatSession(
            id = sessionId,
            title = "Checkpoint tail",
            preview = "keep this response",
            messages = listOf(before) + oldCheckpointMessages + userAfter + otherResponse,
        )
        repository.updateChatState(
            sessions = listOf(session),
            currentSessionId = sessionId,
        )

        repository.upsertAssistantResponseCheckpoints(
            checkpoints = listOf(
                AssistantResponseCheckpoint(
                    target = AssistantResponseCheckpointTarget(
                        sessionId = sessionId,
                        responseGroupId = responseGroupId,
                    ),
                    fromPosition = 1,
                    messages = listOf(
                        ChatMessage(
                            id = "agent-target-reshaped",
                            author = MessageAuthor.Agent,
                            text = "reshaped partial",
                            isIncomplete = true,
                            responseGroupId = responseGroupId,
                        ),
                    ),
                ),
            ),
        )

        val shrunkSummaries = database.chatHistoryDao().getMessageSummariesForSession(sessionId)
        val shrunkMessages = repository.getSessionWithMessages(sessionId)?.messages.orEmpty()
        assertEquals(listOf(0, 1, 2, 3), shrunkSummaries.map { it.position })
        assertEquals(
            listOf(
                "user-before-tail",
                "agent-target-reshaped",
                "user-after-target",
                "agent-other-response",
            ),
            shrunkMessages.map { it.id },
        )
        assertEquals(otherResponseGroupId, shrunkMessages.last().responseGroupId)

        val grownCheckpointMessages = listOf(
            ChatMessage(
                id = "agent-target-reshaped",
                author = MessageAuthor.Agent,
                text = "reshaped partial",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
            ChatMessage(
                id = "agent-target-reasoning",
                author = MessageAuthor.Agent,
                text = "reasoning",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
            ChatMessage(
                id = "agent-target-result",
                author = MessageAuthor.Agent,
                text = "result",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
        )
        repository.upsertAssistantResponseCheckpoints(
            checkpoints = listOf(
                AssistantResponseCheckpoint(
                    target = AssistantResponseCheckpointTarget(
                        sessionId = sessionId,
                        responseGroupId = responseGroupId,
                    ),
                    fromPosition = 1,
                    messages = grownCheckpointMessages,
                ),
            ),
        )

        val grownSummaries = database.chatHistoryDao().getMessageSummariesForSession(sessionId)
        val grownMessages = repository.getSessionWithMessages(sessionId)?.messages.orEmpty()
        assertEquals(listOf(0, 1, 2, 3, 4, 5), grownSummaries.map { it.position })
        assertEquals(
            listOf(
                "user-before-tail",
                "agent-target-reshaped",
                "agent-target-reasoning",
                "agent-target-result",
                "user-after-target",
                "agent-other-response",
            ),
            grownMessages.map { it.id },
        )
        assertEquals(otherResponseGroupId, grownMessages.last().responseGroupId)
    }
}