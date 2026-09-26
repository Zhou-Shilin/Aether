package com.zhousl.aether.ui

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMessagesTest {
    @Test
    fun bashHighlightingPreservesLongQuotedCommands() {
        val command = "sh -c \"" + "echo test; ".repeat(30_000) + "\""
        assertEquals("$ $command", highlightBashCommand(command).text)
    }

    @Test
    fun retryPiBranchResetsFirstTurnAndUsesPreviousAssistantForLaterTurn() {
        val firstUser = ChatMessage(id = "u1", author = MessageAuthor.User, text = "first")
        val firstAssistant = ChatMessage(id = "a1", author = MessageAuthor.Agent, text = "first reply")
        val secondUser = ChatMessage(id = "u2", author = MessageAuthor.User, text = "second")

        assertNull(listOf(firstUser).piBranchMessageIdBeforeLastUser())
        assertEquals(
            firstAssistant.id,
            listOf(firstUser, firstAssistant, secondUser).piBranchMessageIdBeforeLastUser(),
        )
    }

    @Test
    fun retryPiBranchSkipsSyntheticCompactionStatus() {
        val firstUser = ChatMessage(id = "u1", author = MessageAuthor.User, text = "first")
        val firstAssistant = ChatMessage(id = "a1", author = MessageAuthor.Agent, text = "first reply")
        val compactStatus = ChatMessage(
            id = "compact",
            author = MessageAuthor.Agent,
            text = "Context compacted",
            displayKind = MessageDisplayKind.CompactStatus,
        )
        val secondUser = ChatMessage(id = "u2", author = MessageAuthor.User, text = "second")
        val messages = listOf(firstUser, firstAssistant, compactStatus, secondUser)

        assertEquals(firstAssistant.id, messages.piBranchMessageIdBeforeLastUser())
        assertEquals(firstAssistant.id, messages.piBranchMessageIdBeforeUserAt(3))
    }

    @Test
    fun decodeUriAttachmentBitmapReturnsNullWhenPickerUriIsUnavailable() {
        val bitmap = decodeUriAttachmentBitmap(
            uriString = "content://media/picker/0/com.android.providers.media.photopicker/media/1000012900",
            maxSize = 600,
            openInputStream = { throw FileNotFoundException("File not found for uri") },
        )

        assertNull(bitmap)
    }
}
