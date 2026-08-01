package com.zhousl.aether.channel

/**
 * Per-session media-only debounce. This is deliberately separate from the short
 * time-based merge window: media waits for a later text message, while voice is
 * considered complete input and bypasses the buffer, matching QwenPaw.
 */
internal class ChannelNoTextDebouncer {
    private val pending = mutableListOf<ChannelIncomingMessage>()

    fun offer(
        messages: List<ChannelIncomingMessage>,
        enabled: Boolean,
    ): List<ChannelIncomingMessage>? {
        if (!enabled) return drain() + messages

        val hasText = messages.any { it.text.isNotBlank() }
        val hasAudio = messages.any { message ->
            message.attachments.any { it.kind == ChannelFileKind.Audio }
        }
        if (!hasText && !hasAudio) {
            pending += messages
            return null
        }
        return drain() + messages
    }

    fun clear() {
        pending.clear()
    }

    private fun drain(): List<ChannelIncomingMessage> = pending.toList().also { pending.clear() }
}
