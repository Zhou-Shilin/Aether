package com.zhousl.aether.data.pi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiSessionHistorySelectionTest {
    @Test
    fun validatesPiSessionHeaderLine() {
        assertTrue(
            isValidPiSessionHeaderLine(
                firstLine = """{"type":"session","id":"session-123","version":3}""",
                expectedSessionId = "session-123",
            )
        )
        assertFalse(
            isValidPiSessionHeaderLine(
                firstLine = """{"type":"session","id":"other"}""",
                expectedSessionId = "session-123",
            )
        )
        assertFalse(
            isValidPiSessionHeaderLine(
                firstLine = """{"type":"message","id":"session-123"}""",
                expectedSessionId = "session-123",
            )
        )
        assertFalse(isValidPiSessionHeaderLine("not-json", "session-123"))
        assertFalse(isValidPiSessionHeaderLine("", "session-123"))
    }

    @Test
    fun trimsHistoricalMessagesWhenValidatedPiSessionFileExists() {
        val history = List(309) { index -> "message-$index" }

        val normalTurn = selectPiTurnRequestMessages(
            messages = history,
            hasValidatedSessionFile = true,
            sessionBranch = null,
        )
        assertEquals(listOf("message-308"), normalTurn)

        val branchedTurnWithEntryId = selectPiTurnRequestMessages(
            messages = history,
            hasValidatedSessionFile = true,
            sessionBranch = PiSessionBranch(entryId = "entry-42"),
        )
        assertEquals(listOf("message-308"), branchedTurnWithEntryId)
    }

    @Test
    fun preservesFullHistoryWhenSeedingNewSessionOrLegacyBranchReset() {
        val history = List(12) { index -> "message-$index" }

        val firstTurn = selectPiTurnRequestMessages(
            messages = history,
            hasValidatedSessionFile = false,
            sessionBranch = null,
        )
        assertEquals(history, firstTurn)

        val legacyBranchReset = selectPiTurnRequestMessages(
            messages = history,
            hasValidatedSessionFile = true,
            sessionBranch = PiSessionBranch(entryId = null),
        )
        assertEquals(history, legacyBranchReset)

        val blankBranchReset = selectPiTurnRequestMessages(
            messages = history,
            hasValidatedSessionFile = true,
            sessionBranch = PiSessionBranch(entryId = "   "),
        )
        assertEquals(history, blankBranchReset)
    }
}
