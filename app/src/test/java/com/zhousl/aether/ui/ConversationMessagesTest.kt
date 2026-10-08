package com.zhousl.aether.ui

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMessagesTest {
    @Test
    fun retryFourthTurnSendsOnlyItsAncestorsAndRestoresOriginalTail() {
        val original = (1..5).flatMap { turn ->
            listOf(
                ChatMessage(id = "u$turn", author = MessageAuthor.User, text = "user $turn"),
                ChatMessage(id = "a$turn", author = MessageAuthor.Agent, text = "answer $turn"),
            )
        }
        val retry = original[6].copy(id = "u4-retry")
        val branched = createEditedMessageBranch(original, "u4", retry)!!
        assertEquals(listOf("u1", "a1", "u2", "a2", "u3", "a3", "u4-retry"), branched.map { it.id })
        assertEquals("a3", branched.piBranchMessageIdBeforeLastUser())
        val completed = branched + ChatMessage(id = "a4-retry", author = MessageAuthor.Agent, text = "new answer")
        val restored = switchMessageBranch(completed, "u4-retry", -1)!!
        assertEquals(original.map { it.id }, restored.map { it.id })
        assertEquals(completed.map { it.id }, switchMessageBranch(restored, "u4", 1)!!.map { it.id })
    }

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
