package com.zhousl.aether.ui

data class ChatBranchGroup(
    val branches: List<List<ChatMessage>>,
    val selectedIndex: Int = 0,
)

data class ChatBranchNavigation(
    val selectedIndex: Int,
    val branchCount: Int,
) {
    val canGoPrevious: Boolean = selectedIndex > 0
    val canGoNext: Boolean = selectedIndex < branchCount - 1
}

fun ChatMessage.branchNavigation(): ChatBranchNavigation? {
    val group = branchGroup ?: return null
    if (group.branches.size <= 1) return null
    return ChatBranchNavigation(
        selectedIndex = group.selectedIndex.coerceIn(0, group.branches.lastIndex),
        branchCount = group.branches.size,
    )
}

fun List<ChatMessage>.piBranchMessageIdBeforeLastUser(): String? {
    val userMessageIndex = indexOfLast { it.author == MessageAuthor.User }
    if (userMessageIndex <= 0) return null
    return piBranchMessageIdBeforeUserAt(userMessageIndex)
}

fun List<ChatMessage>.piBranchMessageIdBeforeUserAt(userMessageIndex: Int): String? =
    take(userMessageIndex).lastOrNull {
        it.author == MessageAuthor.Agent && it.displayKind != MessageDisplayKind.CompactStatus
    }?.id

fun createEditedMessageBranch(
    messages: List<ChatMessage>,
    messageId: String,
    replacement: ChatMessage,
): List<ChatMessage>? {
    val synced = syncActiveBranches(messages)
    val messageIndex = synced.indexOfFirst {
        it.id == messageId && it.author == MessageAuthor.User
    }
    if (messageIndex < 0) return null

    val currentMessage = synced[messageIndex]
    val existingGroup = currentMessage.branchGroup
    val currentTail = stripBranchController(synced.drop(messageIndex))
    val baseBranches = existingGroup?.branches
        ?.replaceAt(existingGroup.selectedIndex.coerceIn(0, existingGroup.branches.lastIndex), currentTail)
        ?: listOf(currentTail)
    val newBranchIndex = baseBranches.size
    val newGroup = ChatBranchGroup(
        branches = baseBranches + listOf(listOf(replacement)),
        selectedIndex = newBranchIndex,
    )
    return synced.take(messageIndex) + replacement.copy(branchGroup = newGroup)
}

fun switchMessageBranch(
    messages: List<ChatMessage>,
    messageId: String,
    delta: Int,
): List<ChatMessage>? {
    if (delta == 0) return messages
    val synced = syncActiveBranches(messages)
    val messageIndex = synced.indexOfFirst { it.id == messageId && it.author == MessageAuthor.User }
    if (messageIndex < 0) return null

    val currentMessage = synced[messageIndex]
    val group = currentMessage.branchGroup ?: return null
    if (group.branches.isEmpty()) return null

    val selectedIndex = group.selectedIndex.coerceIn(0, group.branches.lastIndex)
    val currentTail = stripBranchController(synced.drop(messageIndex))
    val updatedBranches = group.branches.replaceAt(selectedIndex, currentTail)
    val targetIndex = (selectedIndex + delta).coerceIn(0, updatedBranches.lastIndex)
    if (targetIndex == selectedIndex) return null

    val targetTail = updatedBranches[targetIndex]
    if (targetTail.isEmpty()) return null

    val updatedGroup = group.copy(
        branches = updatedBranches,
        selectedIndex = targetIndex,
    )
    val replacementTail = targetTail.mapIndexed { index, message ->
        if (index == 0) {
            message.copy(branchGroup = updatedGroup)
        } else {
            message
        }
    }
    return synced.take(messageIndex) + replacementTail
}

fun syncActiveBranches(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.none { it.branchGroup != null }) return messages

    var updated = messages
    updated.indices.reversed().forEach { index ->
        val message = updated[index]
        val group = message.branchGroup ?: return@forEach
        if (group.branches.isEmpty()) return@forEach

        val selectedIndex = group.selectedIndex.coerceIn(0, group.branches.lastIndex)
        val activeTail = stripBranchController(updated.drop(index))
        val syncedGroup = group.copy(
            branches = group.branches.replaceAt(selectedIndex, activeTail),
            selectedIndex = selectedIndex,
        )
        updated = updated.toMutableList().apply {
            set(index, message.copy(branchGroup = syncedGroup))
        }
    }
    return updated
}

private fun stripBranchController(tail: List<ChatMessage>): List<ChatMessage> =
    tail.mapIndexed { index, message ->
        if (index == 0) {
            message.copy(branchGroup = null)
        } else {
            message
        }
    }

private fun <T> List<T>.replaceAt(
    index: Int,
    value: T,
): List<T> {
    if (index !in indices) return this
    return toMutableList().apply { set(index, value) }
}
