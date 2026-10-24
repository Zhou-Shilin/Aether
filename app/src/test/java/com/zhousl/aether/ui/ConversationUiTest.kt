package com.zhousl.aether.ui

import com.zhousl.aether.data.LlmTokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationUiTest {
    @Test
    fun reasoningOffKeepsToolsAndAllVisibleTextBlocks() {
        val tools = listOf(
            ChatToolInvocation(
                id = "call-1",
                toolName = "bash",
                argumentsJson = "{}",
            ),
        )
        val blocks = listOf(
            AssistantResponseBlock.Text("draft", "Intermediate answer"),
            AssistantResponseBlock.Reasoning(
                "reasoning",
                ReasoningTrace(id = "reasoning", rawText = "Hidden", toolInvocations = tools),
            ),
            AssistantResponseBlock.Text("final", "Final answer"),
        )

        assertEquals(
            listOf(
                AssistantResponseBlock.Text("draft", "Intermediate answer"),
                AssistantResponseBlock.ToolGroup("reasoning", tools),
                AssistantResponseBlock.Text("final", "Final answer"),
            ),
            blocks.sanitizedForReasoningOff(),
        )
    }

    @Test
    fun retryFinalPayloadRetainsEarlierAttemptAndStatus() {
        val firstAttempt = listOf(
            AssistantResponseBlock.Text("first", "Long partial answer"),
            AssistantResponseBlock.Status("retry", "Reconnected 1/3", "stream dropped"),
        )
        val completed = firstAttempt.withAssistantFinalTextFallback("Recovered answer") { "second" }
        assertEquals(firstAttempt + AssistantResponseBlock.Text("second", "Recovered answer"), completed)
        assertEquals(completed, completed.withAssistantFinalTextFallback("Different final payload") { "third" })
        assertEquals(completed, completed.sanitizedForReasoningOff())
    }

    @Test
    fun autoCompactionKeepsPiReserveBeforeContextIsFull() {
        assertFalse(shouldAutoCompactContext(LlmTokenUsage(totalTokens = 111_616), "api", ""))
        assertTrue(shouldAutoCompactContext(LlmTokenUsage(totalTokens = 111_617), "api", ""))
        assertFalse(
            shouldAutoCompactContext(
                LlmTokenUsage(totalTokens = 127_999),
                "api",
                "",
                contextWindow = 128_000,
                reserveTokens = 0,
            )
        )
    }

    @Test
    fun selectedModelDisplaySplitsFamilyAndVariant() {
        assertEquals(
            SelectedModelDisplayName("GPT", "5.5"),
            formatSelectedModelDisplayName("gpt-5.5"),
        )
        assertEquals(
            SelectedModelDisplayName("Gemini", "3.1 Flash Lite"),
            formatSelectedModelDisplayName("Gemini 3.1 Flash Lite Preview"),
        )
        assertEquals(
            SelectedModelDisplayName("Qwen", "3.6 Max"),
            formatSelectedModelDisplayName("qwen3.6-max-preview"),
        )
    }

    @Test
    fun selectedModelDisplayUsesCapabilityIcons() {
        assertEquals(
            SelectedModelDisplayName("GPT", "5.3 Codex", SelectedModelDisplayIcon.Fast),
            formatSelectedModelDisplayName("gpt-5.3-codex-spark"),
        )
        assertEquals(
            SelectedModelDisplayName("Mimo", "v2.5 Pro", SelectedModelDisplayIcon.Fast),
            formatSelectedModelDisplayName("mimo-v2.5-pro-ultraspeed"),
        )
        assertEquals(
            SelectedModelDisplayName("Nemotron", "3 Nano Omni", SelectedModelDisplayIcon.Reasoning),
            formatSelectedModelDisplayName("nemotron-3-nano-omni-30b-a3b-reasoning"),
        )
    }

    @Test
    fun selectedModelDisplayKeepsSingleFamilyWhenNoVariantExists() {
        assertEquals(
            SelectedModelDisplayName("Fugu", ""),
            formatSelectedModelDisplayName("fugu"),
        )
        assertEquals(
            SelectedModelDisplayName("Fugu", "Ultra"),
            formatSelectedModelDisplayName("fugu-ultra"),
        )
        assertEquals(
            SelectedModelDisplayName("GLM", "5.1"),
            formatSelectedModelDisplayName("GLM-5.1"),
        )
    }

    @Test
    fun pendingIndicatorShowsThinkingAfterBodyTextResetsForToolCall() {
        val previousBlocks = listOf(
            AssistantResponseBlock.Text(
                id = "text-1",
                text = "I will inspect the file first.",
            ),
            AssistantResponseBlock.ToolGroup(
                id = "tools-1",
                toolInvocations = listOf(
                    ChatToolInvocation(
                        id = "call-1",
                        toolName = "read",
                        argumentsJson = """{"path":"README.md"}""",
                        isRunning = true,
                    )
                ),
            ),
        )

        assertTrue(previousBlocks.any { it is AssistantResponseBlock.Text && it.text.isNotBlank() })
        assertEquals(
            PendingGenerationIndicator.Thinking,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingWhileBodyTextIsActive() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "Streaming body text",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingAfterAgentMessageAppears() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.Agent,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingWhilePendingWorkIsVisible() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingWork = true,
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorShowsThinkingForEmptyReasoningPlaceholder() {
        assertEquals(
            PendingGenerationIndicator.Thinking,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingReasoning = hasVisibleReasoningStatus(ReasoningTrace(id = "empty")),
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingForVisibleReasoningStatus() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingReasoning = hasVisibleReasoningStatus(
                    ReasoningTrace(id = "reasoning", latestStatusText = "Checking"),
                ),
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorShowsStatusWhenStatusTextExists() {
        assertEquals(
            PendingGenerationIndicator.Status,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "Streaming body text",
                pendingStatusText = "Reconnecting...",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesAfterTurnEnds() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = false,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.Agent,
            ),
        )
    }

    @Test
    fun pendingGenerationBlockHidesCommittedTextEcho() {
        val reply = "I'm doing great, thank you for asking!"

        assertEquals(
            false,
            shouldRenderPendingGenerationBlock(
                isSending = true,
                pendingResponseBlocks = listOf(
                    AssistantResponseBlock.Text(id = "pending-text", text = reply),
                ),
                pendingToolInvocations = emptyList(),
                pendingStatusText = "",
                lastVisibleAgentText = reply,
            ),
        )
    }

    @Test
    fun pendingGenerationBlockKeepsDistinctPendingText() {
        assertEquals(
            true,
            shouldRenderPendingGenerationBlock(
                isSending = true,
                pendingResponseBlocks = listOf(
                    AssistantResponseBlock.Text(id = "pending-text", text = "Still streaming"),
                ),
                pendingToolInvocations = emptyList(),
                pendingStatusText = "",
                lastVisibleAgentText = "Previous reply",
            ),
        )
    }

    @Test
    fun runningWorkDurationAdvancesFromRecordedStartTime() {
        assertEquals(
            5_000L,
            runningWorkDurationMillis(
                startedAtMillis = 10_000L,
                fallbackStartedRealtimeMillis = 1_000L,
                nowMillis = 15_000L,
                nowRealtimeMillis = 1_000L,
            ),
        )
    }

    @Test
    fun runningWorkDurationUsesStableFallbackStartTime() {
        assertEquals(
            4_000L,
            runningWorkDurationMillis(
                startedAtMillis = null,
                fallbackStartedRealtimeMillis = 2_000L,
                nowMillis = 20_000L,
                nowRealtimeMillis = 6_000L,
            ),
        )
    }

    @Test
    fun completedWorkDurationUsesTurnStatisticsInsteadOfOlderTraceTimestamps() {
        val turnStartedAt = 1_700_000_090_000L
        val turnCompletedAt = 1_700_000_100_000L
        val messages = listOf(
            ChatMessage(
                id = "reasoning",
                author = MessageAuthor.Agent,
                text = "",
                createdAtMillis = turnCompletedAt,
                reasoningTrace = ReasoningTrace(
                    id = "trace",
                    startedAtMillis = 1_700_000_000_000L,
                    completedAtMillis = turnCompletedAt,
                ),
            ),
            ChatMessage(
                id = "answer",
                author = MessageAuthor.Agent,
                text = "Done",
                createdAtMillis = turnCompletedAt + 1L,
                thoughtDurationMillis = 90_000L,
                usageStatistics = ChatUsageStatistics(
                    startedAtMillis = turnStartedAt,
                    completedAtMillis = turnCompletedAt,
                ),
            ),
        )

        assertEquals(
            10_000L,
            workDurationMillisForMessages(messages, endAtMillis = turnCompletedAt + 1L),
        )
        assertEquals(
            7_000L,
            workDurationMillisForMessages(
                messages = listOf(
                    ChatMessage(
                        id = "legacy-answer",
                        author = MessageAuthor.Agent,
                        text = "Done",
                        createdAtMillis = turnCompletedAt,
                        thoughtDurationMillis = 7_000L,
                    ),
                ),
                endAtMillis = turnCompletedAt,
            ),
        )
    }

    @Test
    fun reasoningTimelineKeepsSummaryAndToolsInRecordedOrder() {
        val trace = ReasoningTrace(
            id = "reasoning-1",
            chunks = listOf(
                ReasoningSummaryChunk(
                    id = "summary-1",
                    title = "Planning",
                    detail = "I am checking the input first.",
                    timelineOrder = 1,
                ),
                ReasoningSummaryChunk(
                    id = "summary-2",
                    title = "Reviewing output",
                    detail = "I should inspect the command result.",
                    timelineOrder = 3,
                ),
            ),
            toolInvocations = listOf(
                ChatToolInvocation(
                    id = "tool-1",
                    toolName = "bash",
                    argumentsJson = """{"command":"pwd"}""",
                    timelineOrder = 2,
                ),
            ),
        )

        val items = reasoningTimelineItems(trace)

        assertEquals(
            listOf("summary-1", "tool-1", "summary-2"),
            items.map { item ->
                when (item) {
                    is ReasoningTimelineItem.Summary -> item.chunk.id
                    is ReasoningTimelineItem.Tool -> item.toolInvocation.id
                }
            },
        )
    }
}
