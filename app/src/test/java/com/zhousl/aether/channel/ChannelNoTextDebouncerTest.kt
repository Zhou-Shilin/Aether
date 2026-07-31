package com.zhousl.aether.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNoTextDebouncerTest {
    @Test
    fun mediaOnlyWaitsForFollowingText() {
        val debouncer = ChannelNoTextDebouncer()
        val media = message("media", attachments = listOf(attachment(ChannelFileKind.Image)))
        val text = message("text", text = "describe it")

        assertNull(debouncer.offer(listOf(media), enabled = true))
        assertEquals(listOf(media, text), debouncer.offer(listOf(text), enabled = true))
    }

    @Test
    fun disabledDebounceFlushesPendingMediaImmediately() {
        val debouncer = ChannelNoTextDebouncer()
        val media = message("media", attachments = listOf(attachment(ChannelFileKind.File)))
        val next = message("next", attachments = listOf(attachment(ChannelFileKind.Video)))

        assertNull(debouncer.offer(listOf(media), enabled = true))
        assertEquals(listOf(media, next), debouncer.offer(listOf(next), enabled = false))
    }

    @Test
    fun audioOnlyBypassesDebounce() {
        val debouncer = ChannelNoTextDebouncer()
        val audio = message("voice", attachments = listOf(attachment(ChannelFileKind.Audio)))

        assertEquals(listOf(audio), debouncer.offer(listOf(audio), enabled = true))
    }

    private fun message(
        id: String,
        text: String = "",
        attachments: List<ChannelIncomingAttachment> = emptyList(),
    ) = ChannelIncomingMessage(
        channel = ChannelKind.Feishu,
        messageId = id,
        address = ChannelAddress("chat", "user"),
        text = text,
        attachments = attachments,
    )

    private fun attachment(kind: ChannelFileKind) = ChannelIncomingAttachment(
        id = "attachment-${kind.name}",
        name = "media.bin",
        mimeType = "application/octet-stream",
        kind = kind,
        localPath = "/tmp/media.bin",
        sizeBytes = 1,
    )
}
