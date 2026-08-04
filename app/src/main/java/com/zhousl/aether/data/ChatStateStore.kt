package com.zhousl.aether.data

import android.util.Log
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DraftSessionId = "draft"
private const val ChatStateStoreLogTag = "ChatStateStore"

class ChatStateStore(
    private val scope: CoroutineScope,
    private val chatRepository: ChatRepository,
) {
    private val updateLock = Any()
    private val persistMutex = Mutex()
    private val persistenceQueue = Channel<PendingPersistedChatState>(capacity = Channel.CONFLATED)
    private val _state = MutableStateFlow(PersistedChatState())
    private var localGeneration = 0L
    private var persistedGeneration = 0L
    private var latestPending: PendingPersistedChatState? = null
    private var repositoryStateLoaded = false
    private val repositoryStateReady = CompletableDeferred<Unit>()

    val state: StateFlow<PersistedChatState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                chatRepository.chatState.collect { persisted ->
                    val pendingAfterInitialLoad = synchronized(updateLock) {
                        if (!repositoryStateLoaded) {
                            repositoryStateLoaded = true
                            if (localGeneration == persistedGeneration) {
                                _state.value = persisted
                                null
                            } else {
                                val merged = mergeRepositoryStateWithLocalUpdates(
                                    repositoryState = persisted,
                                    localState = _state.value,
                                )
                                _state.value = merged
                                latestPending = latestPending?.copy(state = merged)
                                latestPending
                            }
                        } else {
                            if (localGeneration == persistedGeneration) {
                                _state.value = persisted
                            }
                            null
                        }
                    }
                    if (!repositoryStateReady.isCompleted) {
                        repositoryStateReady.complete(Unit)
                    }
                    if (pendingAfterInitialLoad != null) {
                        persistWithoutQueue(pendingAfterInitialLoad)
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (!repositoryStateReady.isCompleted) {
                    repositoryStateReady.completeExceptionally(throwable)
                }
                Log.e(ChatStateStoreLogTag, "Failed to load chat state", throwable)
            }
        }
        scope.launch {
            try {
                for (pending in persistenceQueue) {
                    persistPending(pending)
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { flushLatestPending() }
                        .onFailure { throwable ->
                            Log.e(ChatStateStoreLogTag, "Failed to flush pending chat state", throwable)
                        }
                }
            }
        }
    }

    suspend fun flush() {
        repositoryStateReady.await()
        flushLatestPending(propagateFailure = true)
    }

    suspend fun updateAndFlush(
        writeIntent: PersistedChatWriteIntent = PersistedChatWriteIntent.SyncSnapshot,
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        repositoryStateReady.await()
        val updated = update(
            writeIntent = writeIntent,
            transform = transform,
        )
        flushLatestPending(propagateFailure = true)
        return updated
    }

    private suspend fun persistPending(
        pending: PendingPersistedChatState,
        propagateFailure: Boolean = false,
    ) {
        persistMutex.withLock {
            if (synchronized(updateLock) { !repositoryStateLoaded }) {
                return@withLock
            }

            if (synchronized(updateLock) { pending.generation <= persistedGeneration }) {
                return@withLock
            }

            val persistError = runCatching {
                chatRepository.updateChatState(
                    sessions = pending.state.sessions,
                    currentSessionId = pending.state.currentSessionId,
                    writeIntent = pending.writeIntent,
                )
            }.exceptionOrNull()
            if (persistError != null) {
                if (persistError is CancellationException) {
                    throw persistError
                }
                synchronized(updateLock) {
                    if (latestPending == null || pending.generation >= latestPending!!.generation) {
                        latestPending = pending
                    }
                }
                Log.e(ChatStateStoreLogTag, "Failed to persist chat state", persistError)
                scheduleRetry(pending)
                if (propagateFailure) {
                    throw persistError
                }
                return@withLock
            }

            synchronized(updateLock) {
                if (pending.generation > persistedGeneration) {
                    persistedGeneration = pending.generation
                }
                val latest = latestPending
                if (latest != null && latest.generation <= pending.generation) {
                    latestPending = null
                }
            }
        }
    }

    private suspend fun flushLatestPending(propagateFailure: Boolean = false) {
        val pending = synchronized(updateLock) {
            latestPending?.takeIf { it.generation > persistedGeneration }
        } ?: return
        persistPending(pending, propagateFailure)
    }

    private fun persistWithoutQueue(pending: PendingPersistedChatState) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                persistPending(pending)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(ChatStateStoreLogTag, "Failed to persist merged chat state", throwable)
                scheduleRetry(pending)
            }
        }
    }

    private fun scheduleRetry(pending: PendingPersistedChatState) {
        scope.launch {
            delay(PersistRetryDelayMillis)
            val shouldRetry = synchronized(updateLock) {
                latestPending?.generation == pending.generation &&
                    pending.generation > persistedGeneration
            }
            if (!shouldRetry) return@launch
            val sendResult = persistenceQueue.trySend(pending)
            if (sendResult.isFailure) {
                persistWithoutQueue(pending)
            }
        }
    }

    private fun mergeRepositoryStateWithLocalUpdates(
        repositoryState: PersistedChatState,
        localState: PersistedChatState,
    ): PersistedChatState {
        val localSessionsById = localState.sessions.associateBy { it.id }
        val repositorySessionIds = repositoryState.sessions.mapTo(mutableSetOf()) { it.id }
        val mergedSessionIds = repositorySessionIds + localSessionsById.keys
        val currentSessionId = localState.currentSessionId
            .takeIf { id -> id != DraftSessionId && id in mergedSessionIds }
            ?: repositoryState.currentSessionId
                .takeIf { id -> id != DraftSessionId && id in mergedSessionIds }
            ?: repositoryState.sessions.firstOrNull()?.id
            ?: localState.sessions.firstOrNull()?.id
            ?: DraftSessionId
        val mergedSessions = buildList(repositoryState.sessions.size + localState.sessions.size) {
            repositoryState.sessions.forEach { repositorySession ->
                val localSession = localSessionsById[repositorySession.id]
                add(
                    if (localSession == null || repositorySession.id != currentSessionId) {
                        repositorySession
                    } else {
                        localSession.withDerivedMessages(
                            mergeMessages(
                                repositoryMessages = repositorySession.messages,
                                localMessages = localSession.messages,
                            ),
                        )
                    }
                )
            }
            localState.sessions.forEach { localSession ->
                if (localSession.id !in repositorySessionIds) {
                    add(localSession)
                }
            }
        }
        return PersistedChatState(
            sessions = mergedSessions,
            currentSessionId = currentSessionId,
        )
    }

    private fun mergeMessages(
        repositoryMessages: List<ChatMessage>,
        localMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        if (repositoryMessages.isEmpty()) return localMessages
        if (localMessages.isEmpty()) return repositoryMessages

        val localMessagesById = localMessages.associateBy { it.id }
        val repositoryMessageIds = repositoryMessages.mapTo(mutableSetOf()) { it.id }
        return buildList(repositoryMessages.size + localMessages.size) {
            repositoryMessages.forEach { repositoryMessage ->
                add(localMessagesById[repositoryMessage.id] ?: repositoryMessage)
            }
            localMessages.forEach { localMessage ->
                if (localMessage.id !in repositoryMessageIds) {
                    add(localMessage)
                }
            }
        }
    }

    fun update(
        writeIntent: PersistedChatWriteIntent = PersistedChatWriteIntent.SyncSnapshot,
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        val pending = synchronized(updateLock) {
            val updated = transform(_state.value)
            localGeneration += 1
            _state.value = updated
            val effectiveWriteIntent = latestPending
                ?.writeIntent
                ?.takeIf { pendingIntent ->
                    writeIntent == PersistedChatWriteIntent.SyncSnapshot &&
                        updated.sessions.isEmpty() &&
                        pendingIntent != PersistedChatWriteIntent.SyncSnapshot
                }
                ?: writeIntent
            PendingPersistedChatState(
                generation = localGeneration,
                state = updated,
                writeIntent = effectiveWriteIntent,
            ).also {
                latestPending = it
            }
        }
        val sendResult = persistenceQueue.trySend(pending)
        if (sendResult.isFailure) {
            persistWithoutQueue(pending)
        }
        return pending.state
    }

    private data class PendingPersistedChatState(
        val generation: Long,
        val state: PersistedChatState,
        val writeIntent: PersistedChatWriteIntent,
    )

    private companion object {
        const val PersistRetryDelayMillis = 1_000L
    }
}