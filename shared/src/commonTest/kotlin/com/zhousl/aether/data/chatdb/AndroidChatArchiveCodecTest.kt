package com.zhousl.aether.data.chatdb

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidChatArchiveCodecTest {
    @Test
    fun completedSummariesKeepTheirRawExcerptsThroughAndroidArchive() {
        val trace = PersistedReasoningTrace(
            id = "reasoning",
            rawText = "first excerpt\nsecond excerpt",
            completedAtMillis = 100L,
            chunks = listOf(
                PersistedReasoningSummaryChunk("first", "First", "First summary", "first excerpt"),
                PersistedReasoningSummaryChunk("second", "Second", "Second summary", "second excerpt"),
            ),
        )
        val session = PersistedChatSession(
            id = "session",
            title = "Test",
            preview = "Answer",
            messages = listOf(PersistedChatMessage(
                id = "assistant",
                text = "Answer",
                fromUser = false,
                responseBlocks = listOf(PersistedAssistantResponseBlock(
                    id = "reasoning",
                    type = PersistedAssistantResponseBlockType.Reasoning,
                    reasoningTrace = trace,
                )),
            )),
        )
        val restored = decodeAndroidChatSessions(encodeAndroidChatSessions(listOf(session))).single()
        assertEquals(trace, restored.messages.single().responseBlocks.single().reasoningTrace)
    }
}
