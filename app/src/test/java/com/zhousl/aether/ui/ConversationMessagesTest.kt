package com.zhousl.aether.ui

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMessagesTest {
    @Test
    fun adjacentThoughtsWithoutCommentaryRenderAsOneWorkSegment() {
        val firstTool = ChatToolInvocation(id = "tool-1", toolName = "bash", argumentsJson = "{}")
        val secondTool = ChatToolInvocation(id = "tool-2", toolName = "bash", argumentsJson = "{}")
        val first = ChatMessage(
            id = "thought-1",
            author = MessageAuthor.Agent,
            text = "",
            createdAtMillis = 10,
            toolInvocations = listOf(firstTool),
            reasoningTrace = ReasoningTrace("trace-1", rawText = "first", toolInvocations = listOf(firstTool), startedAtMillis = 10, completedAtMillis = 20),
        )
        val second = ChatMessage(
            id = "thought-2",
            author = MessageAuthor.Agent,
            text = "",
            createdAtMillis = 21,
            toolInvocations = listOf(secondTool),
            reasoningTrace = ReasoningTrace("trace-2", rawText = "second", toolInvocations = listOf(secondTool), startedAtMillis = 21, completedAtMillis = 30),
        )

        val merged = mergeAdjacentWorkMessages(listOf(first, second))

        assertEquals(1, merged.size)
        assertEquals("first\nsecond", merged.single().reasoningTrace?.rawText)
        assertEquals(listOf("tool-1", "tool-2"), merged.single().reasoningTrace?.toolInvocations?.map { it.id })
        assertEquals(10L, merged.single().reasoningTrace?.startedAtMillis ?: -1L)
        assertEquals(30L, merged.single().reasoningTrace?.completedAtMillis ?: -1L)
    }

    @Test
    fun commentaryKeepsThoughtsInSeparateWorkSegments() {
        val first = ChatMessage(
            id = "thought-1", author = MessageAuthor.Agent, text = "",
            reasoningTrace = ReasoningTrace("trace-1", rawText = "first"),
        )
        val commentary = ChatMessage(id = "commentary", author = MessageAuthor.Agent, text = "Updating the server now.")
        val second = ChatMessage(
            id = "thought-2", author = MessageAuthor.Agent, text = "",
            reasoningTrace = ReasoningTrace("trace-2", rawText = "second"),
        )

        assertEquals(listOf("thought-1", "commentary", "thought-2"), mergeAdjacentWorkMessages(listOf(first, commentary, second)).map { it.id })
    }

    @Test
    fun toolOnlyMessageBetweenThoughtsStaysInTheMergedTimeline() {
        val tool = ChatToolInvocation(id = "tool-only", toolName = "bash", argumentsJson = "{}")
        val first = ChatMessage(
            id = "thought-1", author = MessageAuthor.Agent, text = "",
            reasoningTrace = ReasoningTrace("trace-1", rawText = "first", startedAtMillis = 10, completedAtMillis = 20),
        )
        val middle = ChatMessage(
            id = "tool", author = MessageAuthor.Agent, text = "", toolInvocations = listOf(tool),
        )
        val last = ChatMessage(
            id = "thought-2", author = MessageAuthor.Agent, text = "",
            reasoningTrace = ReasoningTrace("trace-2", rawText = "second", startedAtMillis = 30, completedAtMillis = 40),
        )

        val merged = mergeAdjacentWorkMessages(listOf(first, middle, last))

        assertEquals(1, merged.size)
        assertEquals(listOf("tool-only"), merged.single().reasoningTrace?.toolInvocations?.map { it.id })
    }

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
