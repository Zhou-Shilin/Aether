package com.zhousl.aether.data.chatdb

internal const val ChatHistoryMessageSyncChunkSize = 8
internal const val ChatHistoryWorkspaceRefSyncChunkSize = 32
internal const val ChatHistoryAgentRefSyncChunkSize = 500

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

/** Finds the first differing position in a dense incoming batch starting at [startPosition]. */
fun firstChangedMessagePosition(
    existing: List<ChatMessageEntity>,
    incoming: List<ChatMessageEntity>,
    startPosition: Int,
): Int? {
    require(startPosition >= 0) { "startPosition must be non-negative." }
    return incoming.indices.firstOrNull { index ->
        val stored = existing.getOrNull(index)
        stored?.position != startPosition + index || stored != incoming[index]
    }?.let { startPosition + it }
}
