package com.zhousl.aether.data.chatdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun unchangedPrefixLeavesExistingRowsUntouched() {
        val existing = listOf(
            testMessage("session", "user-1", 0, "hello"),
            testMessage("session", "agent-1", 1, "partial"),
            testMessage("session", "user-2", 2, "follow-up"),
        )
        val incoming = canonicalActiveChatMessages(
            sessionId = "session",
            messages = listOf(
                testMessage("session", "user-1", 0, "hello"),
                testMessage("session", "agent-1", 1, "complete"),
            ),
        )
        val plan = planCanonicalMessageSync(existing, incoming)
        assertEquals(1, plan.firstChangedPosition)
        assertTrue(plan.hasStaleTail)
        assertFalse(plan.isNoOp)
        assertEquals(setOf("agent-1"), plan.changedMessageIds)
    }

    @Test
    fun identicalActiveListsAreNoOpsEvenWhenParkedOverflowExists() {
        val messages = listOf(
            testMessage("session", "user-1", 0),
            testMessage("session", "agent-1", 1),
        )
        val plan = planCanonicalMessageSync(messages, messages)
        assertTrue(plan.isNoOp)
        assertEquals(2, plan.firstChangedPosition)
        assertTrue(plan.changedMessageIds.isEmpty())
    }

    @Test
    fun snapshotOfACompletedTurnDoesNotTreatCheckpointOverflowAsARewrite() {
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
        val plan = planCanonicalMessageSync(checkpoint, completed)
        assertEquals(1, plan.firstChangedPosition)
        assertFalse(plan.hasStaleTail)
        assertEquals(setOf("agent-complete"), plan.changedMessageIds)
        assertFalse("user-after" in plan.changedMessageIds)
        assertFalse("user-1" in plan.changedMessageIds)
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
