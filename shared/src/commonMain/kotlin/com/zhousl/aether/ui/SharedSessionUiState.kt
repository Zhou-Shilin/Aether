package com.zhousl.aether.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhousl.aether.data.SharedActiveSkillContext
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.deriveSharedSessionMetadata
import com.zhousl.aether.data.platformRandomUuid
import kotlinx.coroutines.Job

internal class SharedSessionUiState(
    id: String,
    initialMessages: List<SharedChatMessage> = emptyList(),
    selectedSkillIds: List<String> = emptyList(),
    activeSkills: List<SharedActiveSkillContext> = emptyList(),
    activeMcpServerIds: List<String> = emptyList(),
    selectedModelKey: String = "",
    title: String = "New chat",
    isDraft: Boolean = false,
) {
    val composerKey = "$id:${platformRandomUuid()}"
    var id by mutableStateOf(id)
    var isDraft by mutableStateOf(isDraft)
    val messages = mutableStateListOf<SharedChatMessage>().apply { addAll(initialMessages) }
    val queuedTurns = mutableStateListOf<SharedPendingTurn>()
    val selectedSkillIds = mutableStateListOf<String>().apply { addAll(selectedSkillIds) }
    val activeSkills = mutableStateListOf<SharedActiveSkillContext>().apply { addAll(activeSkills) }
    val activeMcpServerIds = mutableStateListOf<String>().apply { addAll(activeMcpServerIds) }
    var input by mutableStateOf("")
    var editingMessageId by mutableStateOf("")
    var job by mutableStateOf<Job?>(null)
    var streamingStatus by mutableStateOf("")
    var selectedModelKey by mutableStateOf(selectedModelKey)
    var title by mutableStateOf(title.ifBlank { "New chat" })
    var hasCustomTitle by mutableStateOf(false)
    var hasUnviewedCompletion by mutableStateOf(false)

    val isWorking: Boolean
        get() = job?.isActive == true
}

internal fun SharedSessionUiState.clearComposerDraft() {
    input = ""
    editingMessageId = ""
}

internal fun SharedSessionUiState.retainEnabledSkillSelections(enabledSkillIds: Set<String>): Boolean {
    val selectedChanged = selectedSkillIds.removeAll { it !in enabledSkillIds }
    val activeChanged = activeSkills.removeAll { it.skillId !in enabledSkillIds }
    return selectedChanged || activeChanged
}

internal fun SharedSessionUiState.retainEnabledMcpSelections(enabledMcpServerIds: Set<String>): Boolean {
    val previousSize = activeMcpServerIds.size
    activeMcpServerIds.retainAll(enabledMcpServerIds)
    return activeMcpServerIds.size != previousSize
}

internal fun PersistedChatSession.toSharedSessionUiState(): SharedSessionUiState =
    SharedSessionUiState(
        id = id,
        initialMessages = messages.map(PersistedChatMessage::toSharedChatMessage).filter { message ->
            message.fromUser || message.hasSharedVisibleAssistantWork()
        },
        selectedSkillIds = selectedSkillIds,
        activeSkills = activeSkills,
        activeMcpServerIds = activeMcpServerIds,
        selectedModelKey = selectedModelKey,
        title = title,
    ).also { state -> state.hasCustomTitle = hasCustomTitle }

internal fun SharedSessionUiState.toPersistedSession(): PersistedChatSession {
    val persistedMessages = messages.toPersistedMessages()
    return PersistedChatSession(
        id = id,
        title = title,
        preview = deriveSharedSessionMetadata(persistedMessages).second,
        messages = persistedMessages,
        hasCustomTitle = hasCustomTitle,
        selectedSkillIds = selectedSkillIds.toList(),
        activeSkills = activeSkills.toList(),
        activeMcpServerIds = activeMcpServerIds.toList(),
        selectedModelKey = selectedModelKey,
    )
}
