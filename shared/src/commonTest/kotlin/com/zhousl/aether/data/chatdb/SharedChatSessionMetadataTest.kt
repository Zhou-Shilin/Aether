package com.zhousl.aether.data.chatdb

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedChatSessionMetadataTest {
    @Test
    fun metadataMatchesAndroidVisibilityAndAttachmentRules() {
        val metadata = deriveSharedSessionMetadata(
            listOf(
                PersistedChatMessage(
                    id = "hidden",
                    text = "retained compacted context",
                    fromUser = true,
                    displayKind = PersistedMessageDisplayKind.HiddenContext,
                ),
                PersistedChatMessage(
                    id = "user",
                    text = "",
                    fromUser = true,
                    attachments = listOf(
                        PersistedChatAttachment(
                            id = "file",
                            name = "notes.txt",
                            mimeType = "text/plain",
                            workspacePath = "/workspace/notes.txt",
                        )
                    ),
                ),
            )
        )

        assertEquals("notes.txt", metadata.first)
        assertEquals("notes.txt", metadata.second)
    }

    @Test
    fun previewSummarizesReasoningAndToolsLikeAndroid() {
        val reasoning = PersistedChatMessage(
            id = "reasoning",
            text = "",
            fromUser = false,
            responseBlocks = listOf(
                PersistedAssistantResponseBlock(
                    id = "reasoning-block",
                    type = PersistedAssistantResponseBlockType.Reasoning,
                    reasoningTrace = PersistedReasoningTrace(
                        id = "trace",
                        chunks = listOf(
                            PersistedReasoningSummaryChunk(
                                id = "chunk",
                                title = "Checking the request",
                                detail = "I need to compare both implementations.",
                            )
                        ),
                    ),
                )
            ),
        )
        val tool = PersistedChatMessage(
            id = "tool",
            text = "",
            fromUser = false,
            tools = listOf(
                PersistedChatTool(
                    id = "tool-call",
                    name = "bash",
                    summary = "",
                )
            ),
        )

        assertEquals("I need to compare both implementations.", reasoning.sharedSummaryText())
        assertEquals("Ran bash command", tool.sharedSummaryText())
    }

    @Test
    fun compactStatusAndPreviewLengthMatchAndroid() {
        assertEquals(
            "Context compacted",
            PersistedChatMessage(
                id = "compact",
                text = "",
                fromUser = false,
                displayKind = PersistedMessageDisplayKind.CompactStatus,
            ).sharedSummaryText(),
        )
        val longText = "x".repeat(140)
        val preview = deriveSharedSessionMetadata(
            listOf(PersistedChatMessage(id = "user", text = longText, fromUser = true))
        ).second
        assertEquals(96, preview.length)
    }
}
