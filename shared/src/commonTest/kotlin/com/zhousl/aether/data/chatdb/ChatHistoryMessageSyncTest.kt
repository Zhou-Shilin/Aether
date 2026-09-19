package com.zhousl.aether.data.chatdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatHistoryMessageSyncTest {
    @Test
    fun duplicateMessageIdsAreRejectedBeforeRoomWrites() {
        val error = assertFailsWith<IllegalArgumentException> {
            canonicalActiveChatMessages(
                sessionId = "session",
                messages = listOf(
                    testMessage("session", "user-1", 0),
                    testMessage("session", "user-1", 1),
                ),
            )
        }
        assertTrue(error.message.orEmpty().contains("user-1"))
    }

    @Test
    fun positionsAreNormalizedToADenseActivePrefix() {
        val canonical = canonicalActiveChatMessages(
            sessionId = "session",
            messages = listOf(
                testMessage("session", "user-1", 4),
                testMessage("session", "agent-1", 9),
            ),
        )
        assertEquals(listOf(0, 1), canonical.map(ChatMessageEntity::position))
        assertEquals(listOf("user-1", "agent-1"), canonical.map(ChatMessageEntity::id))
    }

    @Test
    fun findsFirstChangedMessageInBatchAndKeepsEarlierRowsUntouched() {
        val existing = listOf(
            testMessage("session", "agent-1", 8, "partial"),
            testMessage("session", "user-2", 9, "follow-up"),
        )
        val incoming = listOf(
            testMessage("session", "agent-1", 8, "complete"),
            testMessage("session", "user-2", 9, "follow-up"),
        )

        assertEquals(8, firstChangedMessagePosition(existing, incoming, startPosition = 8))
        assertNull(firstChangedMessagePosition(existing.drop(1), incoming.drop(1), startPosition = 9))
    }

    @Test
    fun missingPositionIsTreatedAsFirstChange() {
        val existing = listOf(testMessage("session", "user-2", 2))
        val incoming = listOf(
            testMessage("session", "user-1", 1),
            testMessage("session", "user-2", 2),
        )

        assertEquals(1, firstChangedMessagePosition(existing, incoming, startPosition = 1))
    }

    @Test
    fun completedTurnOnlyRewritesTheChangedSuffix() {
        val checkpoint = listOf(
            testMessage("session", "user-1", 0, "before"),
            testMessage("session", "agent-partial", 1, "partial"),
            testMessage("session", "user-after", 2, "keep"),
        )
        val completed = canonicalActiveChatMessages(
            sessionId = "session",
            messages = listOf(
                testMessage("session", "user-1", 0, "before"),
                testMessage("session", "agent-complete", 1, "complete"),
                testMessage("session", "user-after", 2, "keep"),
            ),
        )

        assertNull(firstChangedMessagePosition(checkpoint.take(1), completed.take(1), startPosition = 0))
        assertEquals(1, firstChangedMessagePosition(checkpoint.drop(1), completed.drop(1), startPosition = 1))
    }
}

private fun testMessage(
    sessionId: String,
    id: String,
    position: Int,
    text: String = id,
): ChatMessageEntity = ChatMessageEntity(
    sessionId = sessionId,
    id = id,
    position = position,
    messageJson = """{"id":"$id","text":"$text"}""",
    author = "User",
    text = text,
)
