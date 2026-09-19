package com.zhousl.aether.data.chatdb

internal const val ChatHistoryMessageSyncChunkSize = 8
internal const val ChatHistoryWorkspaceRefSyncChunkSize = 32

data class ChatHistoryMessageSyncPlan(
    val firstChangedPosition: Int,
    val hasStaleTail: Boolean,
    val isNoOp: Boolean,
    val changedMessageIds: Set<String>,
)

fun canonicalActiveChatMessages(
    sessionId: String,
    messages: List<ChatMessageEntity>,
): List<ChatMessageEntity> {
    if (messages.isEmpty()) return emptyList()
    val seenIds = HashSet<String>(messages.size)
    return messages.mapIndexed { index, message ->
        require(message.sessionId == sessionId) {
            "Message ${message.id} belongs to ${message.sessionId}, not $sessionId."
        }
        require(message.id.isNotBlank()) { "Chat message IDs cannot be blank." }
        require(seenIds.add(message.id)) {
            "Duplicate chat message id ${message.id} in session $sessionId."
        }
        if (message.position == index) message else message.copy(position = index)
    }
}

fun planCanonicalMessageSync(
    existing: List<ChatMessageEntity>,
    incoming: List<ChatMessageEntity>,
): ChatHistoryMessageSyncPlan {
    val firstChangedPosition = incoming.indices.firstOrNull { position ->
        existing.getOrNull(position) != incoming[position]
    } ?: minOf(existing.size, incoming.size)
    val hasStaleTail = existing.size > incoming.size
    val changedMessages = incoming.drop(firstChangedPosition)
    return ChatHistoryMessageSyncPlan(
        firstChangedPosition = firstChangedPosition,
        hasStaleTail = hasStaleTail,
        isNoOp = !hasStaleTail && firstChangedPosition == incoming.size,
        changedMessageIds = changedMessages.map(ChatMessageEntity::id).toSet(),
    )
}
