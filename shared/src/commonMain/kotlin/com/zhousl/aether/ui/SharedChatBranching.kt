package com.zhousl.aether.ui

internal fun createEditedSharedMessageBranch(
    messages: List<SharedChatMessage>,
    messageId: String,
    replacement: SharedChatMessage,
): List<SharedChatMessage>? {
    val synced = messages.syncSharedUserBranches()
    val messageIndex = synced.indexOfFirst { it.id == messageId && it.fromUser }
    if (messageIndex < 0) return null

    val currentMessage = synced[messageIndex]
    val currentTail = synced.drop(messageIndex).stripSharedBranchController()
    val selectedIndex = currentMessage.selectedUserBranchIndex
        .coerceIn(0, currentMessage.userBranches.lastIndex.coerceAtLeast(0))
    val baseBranches = if (currentMessage.userBranches.isEmpty()) {
        listOf(currentTail)
    } else {
        currentMessage.userBranches.replaceAt(selectedIndex, currentTail)
    }
    val newBranchIndex = baseBranches.size
    val newBranches = baseBranches + listOf(listOf(replacement.withoutSharedBranchController()))
    return synced.take(messageIndex) + replacement.copy(
        userBranches = newBranches,
        selectedUserBranchIndex = newBranchIndex,
        branchIndex = newBranchIndex,
        branchCount = newBranches.size,
    )
}

internal fun switchSharedUserMessageBranch(
    messages: List<SharedChatMessage>,
    messageId: String,
    targetIndex: Int,
): List<SharedChatMessage>? {
    val synced = messages.syncSharedUserBranches()
    val messageIndex = synced.indexOfFirst { it.id == messageId && it.fromUser }
    if (messageIndex < 0) return null
    val currentMessage = synced[messageIndex]
    if (currentMessage.userBranches.size <= 1) return null

    val selectedIndex = currentMessage.selectedUserBranchIndex
        .coerceIn(0, currentMessage.userBranches.lastIndex)
    val activeTail = synced.drop(messageIndex).stripSharedBranchController()
    val updatedBranches = currentMessage.userBranches.replaceAt(selectedIndex, activeTail)
    val resolvedTarget = targetIndex.coerceIn(0, updatedBranches.lastIndex)
    if (resolvedTarget == selectedIndex) return null
    val targetTail = updatedBranches[resolvedTarget]
    if (targetTail.isEmpty()) return null

    return synced.take(messageIndex) + targetTail.mapIndexed { index, message ->
        if (index == 0) {
            message.copy(
                userBranches = updatedBranches,
                selectedUserBranchIndex = resolvedTarget,
                branchIndex = resolvedTarget,
                branchCount = updatedBranches.size,
            )
        } else {
            message
        }
    }
}

internal fun List<SharedChatMessage>.syncSharedUserBranches(): List<SharedChatMessage> {
    if (none { it.userBranches.isNotEmpty() }) return this
    var updated = this
    updated.indices.reversed().forEach { index ->
        val message = updated[index]
        if (message.userBranches.isEmpty()) return@forEach
        val selectedIndex = message.selectedUserBranchIndex
            .coerceIn(0, message.userBranches.lastIndex)
        val activeTail = updated.drop(index).stripSharedBranchController()
        val syncedBranches = message.userBranches.replaceAt(selectedIndex, activeTail)
        updated = updated.toMutableList().apply {
            set(
                index,
                message.copy(
                    userBranches = syncedBranches,
                    selectedUserBranchIndex = selectedIndex,
                    branchIndex = selectedIndex,
                    branchCount = syncedBranches.size,
                ),
            )
        }
    }
    return updated
}

private fun List<SharedChatMessage>.stripSharedBranchController(): List<SharedChatMessage> =
    mapIndexed { index, message ->
        if (index == 0) message.withoutSharedBranchController() else message
    }

private fun SharedChatMessage.withoutSharedBranchController(): SharedChatMessage = copy(
    userBranches = emptyList(),
    selectedUserBranchIndex = 0,
    branchIndex = 0,
    branchCount = 1,
)

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    if (index !in indices) this else toMutableList().apply { set(index, value) }
