package com.zhousl.aether.ui

/** Only used inside a pending timeline row, never as the conversation's status. */
fun pendingReasoningPreview(rawText: String): String {
    val words = Regex("\\S+").findAll(rawText).take(100).toList()
    return words.lastOrNull()?.let { rawText.substring(0, it.range.last + 1).trimStart() }.orEmpty()
}
