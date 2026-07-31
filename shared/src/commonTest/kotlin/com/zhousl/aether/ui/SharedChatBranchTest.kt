package com.zhousl.aether.ui

import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.pi.SharedPiContentPart
import com.zhousl.aether.data.pi.SharedPiTurnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedChatBranchTest {
    @Test
    fun seenOnboardingRestoresChatEvenWhenProviderWasSkipped() {
        assertTrue(shouldRestoreSharedChat(onboardingSeenVersion = 1))
        assertFalse(shouldRestoreSharedChat(onboardingSeenVersion = 0))
    }

    @Test
    fun backgroundExpirationPreservesPartialOutputAndStopsTools() {
        val partial = SharedChatMessage(
            text = "Partial answer",
            fromUser = false,
            isStreaming = true,
            tools = listOf(
                SharedChatToolInvocation(
                    id = "tool",
                    name = "bash",
                    summary = "Running",
                    isRunning = true,
                )
            ),
        ).interruptedByBackgroundExpiration()

        assertEquals("Partial answer", partial.text)
        assertFalse(partial.isStreaming)
        assertEquals("Interrupted", partial.status)
        assertFalse(partial.tools.single().isRunning)
        assertTrue(partial.tools.single().isError)
    }

    @Test
    fun userStopFinalizesReasoningAndNestedToolsWithoutInventingErrorText() {
        val runningTool = SharedChatToolInvocation(
            id = "tool",
            name = "fetch_web_url",
            summary = "Fetching",
            isRunning = true,
            startedAtMillis = 900,
        )
        val message = SharedChatMessage(
            text = "",
            fromUser = false,
            isStreaming = true,
            tools = listOf(runningTool),
            responseBlocks = listOf(
                SharedAssistantResponseBlock.Reasoning(
                    id = "reasoning",
                    trace = SharedReasoningTrace(
                        id = "reasoning",
                        rawText = "working",
                        toolInvocations = listOf(runningTool),
                        startedAtMillis = 800,
                    ),
                ),
                SharedAssistantResponseBlock.ToolGroup("group", listOf(runningTool)),
            ),
        ).finalizeSharedInterruptedAssistantWork(
            status = "Stopped",
            completedAtMillis = 1_000,
        )

        assertEquals("", message.text)
        assertFalse(message.isError)
        assertFalse(message.isStreaming)
        assertEquals(1_000L, message.completedAtMillis)
        assertEquals(1_000L, message.tools.single().completedAtMillis)
        val reasoning = message.responseBlocks[0] as SharedAssistantResponseBlock.Reasoning
        assertEquals(1_000L, reasoning.trace.completedAtMillis)
        assertFalse(reasoning.trace.toolInvocations.single().isRunning)
        assertEquals(1_000L, reasoning.trace.toolInvocations.single().completedAtMillis)
        val group = message.responseBlocks[1] as SharedAssistantResponseBlock.ToolGroup
        assertFalse(group.tools.single().isRunning)
        assertEquals(1_000L, group.tools.single().completedAtMillis)
        val output = Json.parseToJsonElement(group.tools.single().outputJson).jsonObject
        assertFalse(output["ok"]!!.jsonPrimitive.boolean)
        assertEquals("cancelled", output["status"]!!.jsonPrimitive.content)
        assertEquals(143, output["exit_code"]!!.jsonPrimitive.int)
        assertEquals("Stopped by user.", output["errmsg"]!!.jsonPrimitive.content)
    }

    @Test
    fun userStopDoesNotKeepAnEmptyAssistantPlaceholder() {
        val stopped = SharedChatMessage(
            text = "",
            fromUser = false,
            isStreaming = true,
        ).finalizeSharedInterruptedAssistantWork(status = "Stopped")

        assertFalse(stopped.hasSharedVisibleAssistantWork())
    }

    @Test
    fun requestFailurePreservesPartialOutputAndUsesAndroidPrefix() {
        val failed = SharedChatMessage(
            text = "Partial answer",
            fromUser = false,
            responseBlocks = listOf(SharedAssistantResponseBlock.Text("text", "Partial answer")),
        ).withSharedRequestFailure("connection lost")

        assertEquals("Partial answer\n\nRequest failed: connection lost", failed.text)
        assertEquals(
            "Partial answer\n\nRequest failed: connection lost",
            (failed.responseBlocks.single() as SharedAssistantResponseBlock.Text).text,
        )
    }

    @Test
    fun emptySuccessfulResponseUsesAndroidFallbackText() {
        val completed = SharedChatMessage(
            text = "",
            fromUser = false,
        ).withAssistantTextResultFallback(SharedPiTurnResult(assistantText = ""))

        assertEquals(
            "The model finished without returning any assistant text.",
            completed.text,
        )
        assertEquals(
            "The model finished without returning any assistant text.",
            (completed.responseBlocks.single() as SharedAssistantResponseBlock.Text).text,
        )
    }

    @Test
    fun steerUsesAndroidSupplementalContextInstructionWithoutChangingStoredMessage() {
        val original = SharedChatMessage(
            id = "steer",
            text = "Focus on the failing test",
            fromUser = true,
        )

        val injected = original.withSharedSteerInstruction()

        assertEquals("Focus on the failing test", original.text)
        assertEquals(
            "The user sent this while you were already working. Treat it as supplemental context for the current task. " +
                "Continue the ongoing work, do not restart just to acknowledge it, and only change course if the new note requires it.\n\n" +
                "Supplemental user note:\nFocus on the failing test",
            injected.text,
        )
    }

    @Test
    fun attachmentOnlySteerUsesAndroidAttachmentInstruction() {
        val injected = SharedChatMessage(
            text = "",
            fromUser = true,
            attachments = listOf(
                SharedChatAttachment(
                    id = "file",
                    name = "notes.txt",
                    mimeType = "text/plain",
                    workspacePath = "/workspace/notes.txt",
                )
            ),
        ).withSharedSteerInstruction()

        assertTrue(injected.text.endsWith("The user also attached additional files for the current task."))
    }

    @Test
    fun inlineImageCapabilityMatchesAndroidMoonshotRules() {
        fun config(baseUrl: String, modelId: String) = LlmProviderConfig(
            providerId = "test",
            name = "Test",
            piProviderId = "openai",
            apiKey = "key",
            baseUrl = baseUrl,
            modelId = modelId,
        )

        assertFalse(sharedSupportsInlineImageWithTools(config("https://api.moonshot.cn/v1", "model")))
        assertFalse(sharedSupportsInlineImageWithTools(config("https://example.com/v1", "kimi-k2")))
        assertFalse(sharedSupportsInlineImageWithTools(config("https://example.com/v1", "moonshot-v1")))
        assertTrue(sharedSupportsInlineImageWithTools(config("https://api.openai.com/v1", "gpt-5")))
    }

    @Test
    fun workspaceAttachmentRequestMatchesAndroidMetadataAndInlineRules() {
        val message = SharedChatMessage(
            text = "Inspect this",
            fromUser = true,
            attachments = listOf(
                SharedChatAttachment(
                    id = "image",
                    name = "screen.png",
                    mimeType = "image/png",
                    workspacePath = "/workspace/screen.png",
                    sizeBytes = 2_048,
                    inlineBase64 = "AQID",
                )
            ),
        )

        val withoutInline = message.toPiChatMessage(supportsInlineImageWithTools = false)
        assertEquals(2, withoutInline.contentParts.size)
        assertEquals("Inspect this", (withoutInline.contentParts[0] as SharedPiContentPart.Text).text)
        assertEquals(
            "Workspace attachment:\n" +
                "Name: screen.png\n" +
                "Type: image/png\n" +
                "Size: 2.0 KB\n" +
                "Path: /workspace/screen.png\n" +
                "This file was uploaded in the current session.\n" +
                "This image was copied into the workspace. Call analyze_image on this exact path before answering questions about the image; " +
                "this model endpoint does not reliably read images in tool-enabled agent requests.",
            (withoutInline.contentParts[1] as SharedPiContentPart.Text).text,
        )

        val withInline = message.toPiChatMessage(supportsInlineImageWithTools = true)
        assertEquals(3, withInline.contentParts.size)
        assertEquals("AQID", (withInline.contentParts[2] as SharedPiContentPart.Image).data)
    }

    @Test
    fun acceptedSteerCommitsPartialWorkBeforeUserAndContinuesWithFreshPendingState() {
        val pending = SharedChatMessage(
            id = "pending",
            text = "Partial",
            fromUser = false,
            isStreaming = true,
            status = "Thinking",
            responseGroupId = "original-user",
            responseBlocks = listOf(SharedAssistantResponseBlock.Text("text", "Partial")),
        )
        val steer = SharedChatMessage(id = "steer", text = "Change direction", fromUser = true)

        val split = splitSharedAssistantForAcceptedSteer(pending, steer, nowMillis = 2_000)

        assertEquals(3, split.size)
        assertEquals("Partial", split[0].text)
        assertFalse(split[0].isStreaming)
        assertTrue(split[0].assistantActionsHidden)
        assertEquals(2_000L, split[0].completedAtMillis)
        assertEquals("steer", split[1].id)
        assertEquals("pending", split[2].id)
        assertTrue(split[2].isStreaming)
        assertEquals(null, split[2].completedAtMillis)
        assertEquals("", split[2].text)
        assertTrue(split[2].responseBlocks.isEmpty())
        assertEquals("original-user", split[2].responseGroupId)
    }

    @Test
    fun multipleSteersCommitPartialWorkOnceAndKeepUserOrder() {
        val pending = SharedChatMessage(
            id = "pending",
            text = "Partial",
            fromUser = false,
            isStreaming = true,
        )
        val first = SharedChatMessage(id = "first", text = "First note", fromUser = true)
        val second = SharedChatMessage(id = "second", text = "Second note", fromUser = true)

        val split = splitSharedAssistantForAcceptedSteers(
            pendingAssistant = pending,
            userMessages = listOf(first, second),
            nowMillis = 2_000,
        )

        assertEquals(4, split.size)
        assertEquals("Partial", split[0].text)
        assertEquals("first", split[1].id)
        assertEquals("second", split[2].id)
        assertEquals("pending", split[3].id)
        assertTrue(split[3].isStreaming)
    }

    @Test
    fun persistenceOmitsStreamingPlaceholderAndKeepsHiddenActionFlag() {
        val persisted = listOf(
            SharedChatMessage(
                id = "committed",
                text = "Partial",
                fromUser = false,
                assistantActionsHidden = true,
                completedAtMillis = 2_000,
            ),
            SharedChatMessage(
                id = "pending",
                text = "",
                fromUser = false,
                isStreaming = true,
            ),
        ).toPersistedMessages()

        assertEquals(1, persisted.size)
        assertEquals("committed", persisted.single().id)
        assertTrue(persisted.single().assistantActionsHidden)
        assertEquals(2_000L, persisted.single().completedAtMillis)
    }
}
