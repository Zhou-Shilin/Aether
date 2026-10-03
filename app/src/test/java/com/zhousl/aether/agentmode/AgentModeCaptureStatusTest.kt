package com.zhousl.aether.agentmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModeCaptureStatusTest {
    @Test
    fun displayCaptureIsNotAPlaceholder() {
        val outcome = parseAgentModeCaptureOutcome(agentModeCaptureStatus(AgentModeCaptureSourceDisplay))
        assertFalse(outcome.isBlank)
        assertEquals("", outcome.blankReason)
    }

    @Test
    fun failedLaunchBlankFrameExplainsItself() {
        // Issue #100: after a failed launch every screenshot used to be a silent black image.
        val outcome = parseAgentModeCaptureOutcome(
            agentModeCaptureStatus(
                source = AgentModeCaptureSourceBlank,
                blankReason = AgentModeBlankReasonLaunchFailed,
            ),
        )
        assertTrue(outcome.isBlank)
        assertEquals(AgentModeBlankReasonLaunchFailed, outcome.blankReason)
    }

    @Test
    fun emptyDisplayBlankFrameExplainsItself() {
        val outcome = parseAgentModeCaptureOutcome(
            agentModeCaptureStatus(
                source = AgentModeCaptureSourceBlank,
                blankReason = AgentModeBlankReasonNoLaunchedContent,
            ),
        )
        assertTrue(outcome.isBlank)
        assertEquals(AgentModeBlankReasonNoLaunchedContent, outcome.blankReason)
    }

    @Test
    fun blankFrameWithoutReasonFallsBackToEmptyDisplay() {
        val outcome = parseAgentModeCaptureOutcome("""{"source":"blank"}""")
        assertTrue(outcome.isBlank)
        assertEquals(AgentModeBlankReasonEmptyDisplay, outcome.blankReason)
    }

    @Test
    fun unknownPayloadCountsAsARealCapture() {
        assertFalse(parseAgentModeCaptureOutcome("").isBlank)
        assertFalse(parseAgentModeCaptureOutcome("not json").isBlank)
        assertFalse(parseAgentModeCaptureOutcome("""{"source":"display"}""").isBlank)
    }
}
