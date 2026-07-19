package com.zhousl.aether.data

import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.syncActiveBranches

internal class QueuedTurnRequestBuilder(
    private val chatStateStore: ChatStateStore,
) {
    fun build(
        sessionId: String,
        queuedInput: ChatMessage,
        baseMessages: List<ChatMessage> = emptyList(),
        baseSettings: AppSettings,
        providerConfigs: List<LlmProviderConfig>,
    ): SessionTurnRequest? {
        var selection = QueuedTurnSelection()

        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            val updatedSession = buildQueuedTurnSession(
                session = session,
                queuedInput = queuedInput,
                baseMessages = baseMessages,
            )
            selection = QueuedTurnSelection(
                requestMessages = updatedSession.messages,
                selectedSkillIds = updatedSession.selectedSkillIds,
                activeSkills = updatedSession.activeSkills,
                activeMcpServerIds = updatedSession.activeMcpServerIds,
                agentModeEnabled = updatedSession.agentModeEnabled,
                chromeEnabled = updatedSession.chromeEnabled,
                selectedModelKey = updatedSession.selectedModelKey,
            )
            updatedSessions.add(0, updatedSession)
            persisted.copy(sessions = updatedSessions)
        }

        if (selection.requestMessages.isEmpty()) return null

        return SessionTurnRequest(
            sessionId = sessionId,
            settings = resolveModelSettings(
                baseSettings = baseSettings,
                providerConfigs = providerConfigs,
                preferredModelKey = selection.selectedModelKey,
                fallbackModelKey = resolveDefaultChatModelKey(baseSettings, providerConfigs),
            ),
            requestMessages = selection.requestMessages,
            selectedSkillIds = selection.selectedSkillIds,
            activeSkills = selection.activeSkills,
            activeMcpServerIds = selection.activeMcpServerIds,
            agentModeEnabled = selection.agentModeEnabled,
            chromeEnabled = selection.chromeEnabled,
            providerConfigs = providerConfigs,
        )
    }

    private data class QueuedTurnSelection(
        val requestMessages: List<ChatMessage> = emptyList(),
        val selectedSkillIds: List<String> = emptyList(),
        val activeSkills: List<ActiveSkillContext> = emptyList(),
        val activeMcpServerIds: List<String> = emptyList(),
        val agentModeEnabled: Boolean = false,
        val chromeEnabled: Boolean = false,
        val selectedModelKey: String = "",
    )
}

internal fun buildQueuedTurnSession(
    session: ChatSession,
    queuedInput: ChatMessage,
    baseMessages: List<ChatMessage> = emptyList(),
): ChatSession {
    val currentMessages = session.messages.ifEmpty { baseMessages }
    return session.withDerivedMessages(
        syncActiveBranches(currentMessages + queuedInput)
    )
}
