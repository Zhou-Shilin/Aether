package com.zhousl.aether.data

import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.MessageAuthor
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateStoreTest {
    @Test
    fun checkpointQueuedWithDeletionPersistsWithoutPublishingCheckpointToUi() = runBlocking {
        val deletedSession = ChatSession(
            id = "deleted-session",
            title = "Deleted",
            preview = "before",
            messages = emptyList(),
        )
        val checkpointSessionId = "checkpoint-session"
        val responseGroupId = "response-group"
        val checkpointMessage = ChatMessage(
            id = "agent-checkpoint",
            author = MessageAuthor.Agent,
            text = "partial",
            isIncomplete = true,
            responseGroupId = responseGroupId,
        )
        val checkpointSession = ChatSession(
            id = checkpointSessionId,
            title = "Checkpoint",
            preview = "before",
            messages = emptyList(),
        )
        val initialState = PersistedChatState(
            sessions = listOf(deletedSession, checkpointSession),
            currentSessionId = checkpointSessionId,
        )
        val repository = RecordingChatStatePersistence(initialState)
        val dispatcher = QueuedCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val store = ChatStateStore(
                scope = scope,
                chatRepository = repository,
            )
            dispatcher.runUntilIdle()

            store.update(writeIntent = PersistedChatWriteIntent.DeleteSession) { state ->
                state.copy(sessions = state.sessions.filterNot { it.id == deletedSession.id })
            }
            val checkpoint = AssistantResponseCheckpoint(
                target = AssistantResponseCheckpointTarget(
                    sessionId = checkpointSessionId,
                    responseGroupId = responseGroupId,
                ),
                fromPosition = 0,
                messages = listOf(checkpointMessage),
            )
            assertTrue(store.updateAssistantCheckpoint(checkpoint))

            assertEquals(emptyList<ChatMessage>(), store.state.value.sessions.single().messages)
            assertTrue(repository.operations.isEmpty())
            dispatcher.runUntilIdle()

            assertEquals(emptyList<ChatMessage>(), store.state.value.sessions.single().messages)
            val snapshotWrite = repository.operations.filterIsInstance<PersistedOperation.Snapshot>().single()
            val checkpointWrite = repository.operations.filterIsInstance<PersistedOperation.Checkpoint>().single()
            assertEquals(listOf(checkpointSessionId), snapshotWrite.sessions.map { it.id })
            assertEquals(emptyList<ChatMessage>(), snapshotWrite.sessions.single().messages)
            assertEquals(listOf(checkpoint), checkpointWrite.checkpoints)
            assertEquals(
                listOf(PersistedOperation.Snapshot::class, PersistedOperation.Checkpoint::class),
                repository.operations.map { it::class },
            )
        } finally {
            scope.cancel()
            dispatcher.runUntilIdle()
        }
    }

    @Test
    fun checkpointQueuePersistsOnlyDirtyResponseAndReplaysActiveResponsesAfterSnapshot() = runBlocking {
        val firstSession = ChatSession(id = "first", title = "First", preview = "")
        val secondSession = ChatSession(id = "second", title = "Second", preview = "")
        val repository = RecordingChatStatePersistence(
            PersistedChatState(
                sessions = listOf(firstSession, secondSession),
                currentSessionId = firstSession.id,
            )
        )
        val dispatcher = QueuedCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val store = ChatStateStore(scope = scope, chatRepository = repository)
            dispatcher.runUntilIdle()

            val firstCheckpoint = checkpointFor(sessionId = firstSession.id, responseGroupId = "first-response")
            val secondCheckpoint = checkpointFor(sessionId = secondSession.id, responseGroupId = "second-response")

            store.updateAssistantCheckpoint(firstCheckpoint)
            dispatcher.runUntilIdle()
            store.updateAssistantCheckpoint(secondCheckpoint)
            dispatcher.runUntilIdle()

            assertEquals(
                listOf(listOf(firstCheckpoint), listOf(secondCheckpoint)),
                repository.operations
                    .filterIsInstance<PersistedOperation.Checkpoint>()
                    .map { it.checkpoints },
            )

            store.update { state ->
                state.copy(
                    sessions = state.sessions.map { session ->
                        if (session.id == firstSession.id) session.copy(title = "Renamed") else session
                    },
                )
            }
            dispatcher.runUntilIdle()

            val lastOperations = repository.operations.takeLast(2)
            assertTrue(lastOperations[0] is PersistedOperation.Snapshot)
            assertEquals(
                setOf(firstCheckpoint, secondCheckpoint),
                (lastOperations[1] as PersistedOperation.Checkpoint).checkpoints.toSet(),
            )

            val completed = firstCheckpoint.messages.single().copy(isIncomplete = false)
            store.update { state ->
                state.copy(
                    sessions = state.sessions.map { session ->
                        if (session.id == firstSession.id) {
                            session.copy(messages = listOf(completed))
                        } else {
                            session
                        }
                    },
                )
            }
            dispatcher.runUntilIdle()

            assertEquals(
                listOf(secondCheckpoint),
                (repository.operations.last() as PersistedOperation.Checkpoint).checkpoints,
            )
        } finally {
            scope.cancel()
            dispatcher.runUntilIdle()
        }
    }

    private fun checkpointFor(
        sessionId: String,
        responseGroupId: String,
    ): AssistantResponseCheckpoint = AssistantResponseCheckpoint(
        target = AssistantResponseCheckpointTarget(
            sessionId = sessionId,
            responseGroupId = responseGroupId,
        ),
        fromPosition = 0,
        messages = listOf(
            ChatMessage(
                id = "$sessionId-agent",
                author = MessageAuthor.Agent,
                text = "partial",
                isIncomplete = true,
                responseGroupId = responseGroupId,
            ),
        ),
    )
}

private class RecordingChatStatePersistence(
    initialState: PersistedChatState,
) : ChatStatePersistence {
    override val chatState: Flow<PersistedChatState> = MutableStateFlow(initialState)
    val operations = mutableListOf<PersistedOperation>()

    override suspend fun updateChatState(
        sessions: List<ChatSession>,
        currentSessionId: String,
        writeIntent: PersistedChatWriteIntent,
    ) {
        operations += PersistedOperation.Snapshot(
            sessions = sessions,
            currentSessionId = currentSessionId,
            writeIntent = writeIntent,
        )
    }

    override suspend fun upsertAssistantResponseCheckpoints(
        checkpoints: List<AssistantResponseCheckpoint>,
    ) {
        operations += PersistedOperation.Checkpoint(checkpoints)
    }
}

private sealed interface PersistedOperation {
    data class Snapshot(
        val sessions: List<ChatSession>,
        val currentSessionId: String,
        val writeIntent: PersistedChatWriteIntent,
    ) : PersistedOperation

    data class Checkpoint(
        val checkpoints: List<AssistantResponseCheckpoint>,
    ) : PersistedOperation
}

private class QueuedCoroutineDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(tasks) {
            tasks.addLast(block)
        }
    }

    fun runUntilIdle() {
        while (true) {
            val task = synchronized(tasks) {
                if (tasks.isEmpty()) null else tasks.removeFirst()
            } ?: return
            task.run()
        }
    }
}
