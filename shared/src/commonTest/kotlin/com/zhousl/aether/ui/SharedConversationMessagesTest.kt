package com.zhousl.aether.ui

import com.zhousl.aether.data.pi.SharedHostToolResult
import com.zhousl.aether.data.pi.SharedPiHostToolCall
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SharedConversationMessagesTest {
    @Test
    fun pendingWorkVisibilityMatchesAndroidResponseBlockRules() {
        assertFalse(emptyList<SharedAssistantResponseBlock>().hasVisibleSharedPendingWork())
        assertFalse(
            listOf(SharedAssistantResponseBlock.Text(id = "blank", text = ""))
                .hasVisibleSharedPendingWork(),
        )
        assertTrue(
            listOf(SharedAssistantResponseBlock.Text(id = "text", text = "answer"))
                .hasVisibleSharedPendingWork(),
        )
        assertTrue(
            listOf(
                SharedAssistantResponseBlock.Reasoning(
                    id = "reasoning",
                    trace = SharedReasoningTrace(id = "trace", latestStatusText = "Thinking"),
                ),
            ).hasVisibleSharedPendingWork(),
        )
        assertTrue(
            listOf(
                SharedAssistantResponseBlock.ToolGroup(
                    id = "tools",
                    tools = listOf(
                        SharedChatToolInvocation(id = "tool", name = "read", summary = "Reading"),
                    ),
                ),
            ).hasVisibleSharedPendingWork(),
        )
    }

    @Test
    fun runningWorkDurationUsesEarliestValidBlockTimestampAndOneSecondFloor() {
        val blocks = listOf(
            SharedAssistantResponseBlock.ToolGroup(
                id = "tools",
                tools = listOf(
                    SharedChatToolInvocation(
                        id = "later",
                        name = "read",
                        summary = "Reading",
                        startedAtMillis = 1_700_000_005_000L,
                    ),
                ),
            ),
            SharedAssistantResponseBlock.Reasoning(
                id = "reasoning",
                trace = SharedReasoningTrace(
                    id = "trace",
                    startedAtMillis = 1_700_000_002_000L,
                ),
            ),
        )

        val startedAt = blocks.sharedWorkStartedAtMillis(1_700_000_004_000L)
        assertEquals(1_700_000_002_000L, startedAt)
        assertEquals(
            3_000L,
            sharedRunningWorkDurationMillis(startedAt, nowMillis = 1_700_000_005_000L),
        )
        assertEquals(
            1_000L,
            sharedRunningWorkDurationMillis(startedAt, nowMillis = 1_700_000_002_100L),
        )
    }

    @Test
    fun toolPresentationMatchesAndroidToolNames() {
        assertEquals(SharedToolPresentation.Generic, sharedToolPresentation("web_fetch"))
        assertEquals(SharedToolPresentation.WebFetch, sharedToolPresentation("fetch_web_url"))
        assertEquals(SharedToolPresentation.Generic, sharedToolPresentation("web_search"))
        assertEquals(SharedToolPresentation.WebSearch, sharedToolPresentation("tavily_search"))
        assertEquals(SharedToolPresentation.Generic, sharedToolPresentation(" TAVILY_SEARCH "))
    }

    @Test
    fun toolInvocationDetailMatchesAndroidCommandAndOutputRules() {
        val noOutput = "No output"
        val truncated = "Content truncated for readability."
        fun detail(tool: SharedChatToolInvocation) = formatSharedToolInvocationDetail(
            tool = tool,
            noOutputLabel = noOutput,
            contentTruncatedLabel = truncated,
            exitCodeLabel = { "Exit code: $it" },
        )

        assertEquals(
            SharedToolInvocationDetail("read /workspace/note.txt (offset=4, limit=20)", noOutput),
            detail(
                SharedChatToolInvocation(
                    id = "read",
                    name = "read",
                    summary = "ignored",
                    argumentsJson = """{"path":"/workspace/note.txt","offset":4,"limit":20}""",
                    isRunning = false,
                ),
            ),
        )
        assertEquals(
            "standard output\n\nstderr: standard error",
            detail(
                SharedChatToolInvocation(
                    id = "bash",
                    name = "bash",
                    summary = "",
                    argumentsJson = """{"command":"printf test"}""",
                    outputJson = """{"stdout":"standard output","stderr":"standard error","exit_code":1}""",
                    isRunning = false,
                ),
            ).result,
        )
        assertEquals(
            "Exit code: 7",
            detail(
                SharedChatToolInvocation(
                    id = "exit",
                    name = "bash",
                    summary = "",
                    argumentsJson = """{"command":"false"}""",
                    outputJson = """{"exit_code":7}""",
                    isRunning = false,
                ),
            ).result,
        )
        assertEquals(
            "Page body\n\n$truncated",
            detail(
                SharedChatToolInvocation(
                    id = "fetch",
                    name = "fetch_web_url",
                    summary = "",
                    argumentsJson = """{"url":"https://example.com"}""",
                    outputJson = """{"markdown":"Page body","truncated":true}""",
                    isRunning = false,
                ),
            ).result,
        )
        assertNull(
            detail(
                SharedChatToolInvocation(
                    id = "running",
                    name = "grep",
                    summary = "",
                    argumentsJson = """{"pattern":"TODO","path":"/workspace"}""",
                ),
            ).result,
        )
        assertEquals(
            """{"ok":true}""",
            detail(
                SharedChatToolInvocation(
                    id = "aether",
                    name = "aether_skill_manage",
                    summary = "",
                    argumentsJson = """{"action":"list"}""",
                    outputJson = """{"ok":true}""",
                    isRunning = false,
                ),
            ).result,
        )
    }

    @Test
    fun webSourceMetadataUsesFetchRedirectAndTavilyFallbacks() {
        val fetch = sharedWebSourceMetadata(
            toolName = "fetch_web_url",
            argumentsJson = """{"url":"https://request.example/start"}""",
            outputJson = """{"request_url":"https://request.example/start","final_url":"https://www.final.example/page"}""",
        )
        assertEquals("final.example", fetch?.domain)
        assertEquals("https://www.final.example/page", fetch?.url)
        assertEquals(
            "https://www.google.com/s2/favicons?domain=final.example&sz=64",
            fetch?.faviconUrl,
        )

        val tavilyResult = sharedWebSourceMetadata(
            toolName = "tavily_search",
            argumentsJson = """{"query":"latest news","include_domains":["docs.example.com"]}""",
            outputJson = """{"results":[]}""",
        )
        assertEquals("docs.example.com", tavilyResult?.domain)
        assertEquals("https://docs.example.com", tavilyResult?.url)

        val queryFallback = sharedWebSourceMetadata(
            toolName = "tavily_search",
            argumentsJson = """{"query":"site:developer.example.org release notes"}""",
            outputJson = "{}",
        )
        assertEquals("developer.example.org", queryFallback?.domain)
    }

    @Test
    fun tokenCountsUseCompactStableFormatting() {
        assertEquals("999", formatSharedTokenCount(999))
        assertEquals("1.0K", formatSharedTokenCount(1_000))
        assertEquals("1.3K", formatSharedTokenCount(1_250))
        assertEquals("1.0M", formatSharedTokenCount(1_000_000))
        assertEquals("2500.0M", formatSharedTokenCount(2_500_000_000))
    }

    @Test
    fun estimatedRequestUsageMatchesAndroidTextAndAttachmentRules() {
        val usage = estimateSharedRequestTokenUsage(
            listOf(
                SharedChatMessage(
                    text = "hello world \u4f60\u597d",
                    fromUser = true,
                    attachments = listOf(
                        SharedChatAttachment(
                            id = "image",
                            name = "photo one.png",
                            mimeType = "image/png",
                            workspacePath = "/workspace/photo-one.png",
                            sizeBytes = 40_000,
                        ),
                        SharedChatAttachment(
                            id = "file",
                            name = "notes.txt",
                            mimeType = "text/plain",
                            workspacePath = "/workspace/notes.txt",
                            sizeBytes = 40,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(102, usage.inputTokens)
        assertEquals(102, usage.totalTokens)
        assertEquals(0, usage.outputTokens)
    }

    @Test
    fun estimatedFileUsageIsCappedAndNegativeSizesDoNotSubtractTokens() {
        val usage = estimateSharedRequestTokenUsage(
            listOf(
                SharedChatMessage(
                    text = "",
                    fromUser = true,
                    attachments = listOf(
                        SharedChatAttachment(
                            id = "large",
                            name = "large.bin",
                            mimeType = "application/octet-stream",
                            workspacePath = "/workspace/large.bin",
                            sizeBytes = 100_000,
                        ),
                        SharedChatAttachment(
                            id = "unknown",
                            name = "unknown.bin",
                            mimeType = "application/octet-stream",
                            workspacePath = "/workspace/unknown.bin",
                            sizeBytes = -1,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(16_002, usage.inputTokens)
        assertEquals(16_002, usage.totalTokens)
    }

    @Test
    fun compactionInputUsesAndroidReasoningAndToolFields() {
        val compactStatus = SharedChatMessage(
            id = "compact-status",
            text = "Context compacted",
            fromUser = false,
            displayKind = SharedMessageDisplayKind.CompactStatus,
        )
        val input = buildSharedCompactConversationInput(
            listOf(
                SharedChatMessage(id = "user", text = "Question", fromUser = true),
                SharedChatMessage(
                    id = "assistant",
                    text = "Answer",
                    fromUser = false,
                    reasoningText = "raw reasoning must not be included",
                    responseBlocks = listOf(
                        SharedAssistantResponseBlock.Reasoning(
                            id = "reasoning",
                            trace = SharedReasoningTrace(
                                id = "trace",
                                chunks = listOf(
                                    SharedReasoningSummaryChunk(
                                        id = "chunk",
                                        title = "Fallback title",
                                        detail = "Retained reasoning summary",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    tools = listOf(
                        SharedChatToolInvocation(
                            id = "tool",
                            name = "read",
                            summary = "presentation summary must not be included",
                            output = "presentation output must not be included",
                            argumentsJson = "{\"path\":\"/workspace/note.txt\"}",
                            outputJson = "{\"text\":\"contents\"}",
                        ),
                    ),
                ),
                compactStatus,
            ),
        )

        assertContains(input, "## 1. User\nQuestion")
        assertContains(input, "Retained reasoning summary")
        assertContains(input, "- read: {\"path\":\"/workspace/note.txt\"}")
        assertContains(input, "output: {\"text\":\"contents\"}")
        assertFalse(input.contains("raw reasoning must not be included"))
        assertFalse(input.contains("presentation summary must not be included"))
        assertFalse(input.contains("Context compacted"))
    }

    @Test
    fun compactPercentageMatchesAndroidVisiblePayloadEstimate() {
        val argumentText = "a".repeat(30_000)
        val outputText = "o".repeat(30_000)
        val messages = listOf(
            SharedChatMessage(id = "user", text = "u".repeat(30_000), fromUser = true),
            SharedChatMessage(
                id = "assistant",
                text = "r".repeat(29_996),
                fromUser = false,
                reasoningText = "ignored reasoning",
                tools = listOf(
                    SharedChatToolInvocation(
                        id = "tool",
                        name = "read",
                        summary = "ignored summary",
                        argumentsJson = argumentText,
                        outputJson = outputText,
                    ),
                ),
            ),
        )

        assertEquals(100, sharedCompactContextPercent(messages))
    }

    @Test
    fun durationsAndRatesUseCompactStableFormatting() {
        assertEquals("450ms", formatSharedDuration(450))
        assertEquals("1.25s", formatSharedDuration(1_250))
        assertEquals("60.00s", formatSharedDuration(60_000))
        assertEquals("66.00s", formatSharedDuration(65_999))
        assertEquals("1s", formatSharedThoughtDuration(1))
        assertEquals("1min 5s", formatSharedThoughtDuration(65_999))
        assertEquals("1h 1min 1s", formatSharedThoughtDuration(3_661_000))
        assertEquals("12.3", formatSharedDecimal(12.34))
        assertEquals("0.0", formatSharedDecimal(-4.0))
        assertEquals("-", formatSharedDecimal(Double.NaN))
    }

    @Test
    fun attachmentDetailIncludesKnownMetadata() {
        assertEquals("0 B", formatSharedByteCount(0))
        assertEquals("1 KB", formatSharedByteCount(1_024))
        assertEquals("1.5 KB", formatSharedByteCount(1_536))
        assertEquals("1 MB", formatSharedByteCount(1_048_576))
        assertEquals(
            "File | 1.5 KB",
            sharedAttachmentMetaLabel(
                "File",
                SharedChatAttachment(
                    id = "attachment",
                    name = "notes.pdf",
                    mimeType = "application/pdf",
                    workspacePath = "/workspace/notes.pdf",
                    sizeBytes = 1_536,
                ),
            ),
        )
        assertEquals("File | 1.0 KB", sharedAttachmentMetaLabel("File", SharedChatAttachment(
            id = "attachment",
            name = "notes.txt",
            mimeType = "text/plain",
            workspacePath = "/workspace/notes.txt",
            sizeBytes = 1_024,
        )))
        assertEquals("1.6 KB", formatSharedAttachmentSize(1_588))
        assertTrue(
            isLikelySharedTextAttachment(
                SharedChatAttachment("source", "Main.kt", "application/octet-stream", "/workspace/Main.kt"),
            ),
        )
        assertEquals("hello", decodeSharedTextAttachment("hello".encodeToByteArray()))
        assertNull(decodeSharedTextAttachment(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun workspaceLinksResolveFileUrisAndRelativeImages() {
        assertEquals(
            "/workspace/reports/final report.png",
            resolveSharedWorkspacePath("reports/final%20report.png", "/workspace"),
        )
        assertEquals(
            "/root/report.pdf",
            resolveSharedWorkspacePath("file:///root/report.pdf", "/workspace"),
        )
        assertTrue(isSharedWorkspaceFileLink("file:///workspace/report.pdf"))
        assertTrue(isSharedWorkspaceFileLink("aether-local-file://%2Fworkspace%2Freport.pdf"))
        assertFalse(isSharedWorkspaceFileLink("reports/final.pdf"))
        assertFalse(isSharedWorkspaceFileLink("https://example.com/report.pdf"))
        assertNull(resolveSharedWorkspacePath("../secrets.txt", "/workspace"))
        assertNull(resolveSharedWorkspacePath("reports/../../secrets.txt", "/workspace"))
        assertEquals(
            "/workspace/final.png",
            resolveSharedWorkspacePath("reports/../final.png", "/workspace"),
        )
        assertEquals("application/pdf", sharedMimeTypeForPath("/workspace/report.PDF"))
        assertEquals("https://example.com/report", normalizeSharedAssistantLinkTarget("example.com/report"))
        assertEquals("https://www.example.com", normalizeSharedAssistantLinkTarget("www.example.com"))
        assertEquals("mailto:test@example.com", normalizeSharedAssistantLinkTarget("mailto:test@example.com"))
    }

    @Test
    fun dataImagesSupportBase64PercentEncodingAndSvgDetection() {
        val svg = "<svg viewBox=\"0 0 10 10\"><path d=\"M0 0h10v10z\"/></svg>"
        val base64 = kotlin.io.encoding.Base64.encode(svg.encodeToByteArray())
        val decodedSvg = decodeSharedMarkdownDataUrl("data:image/svg+xml;base64,$base64")
        assertEquals(svg, decodedSvg?.bytes?.decodeToString())
        assertEquals("image/svg+xml", decodedSvg?.mimeType)

        val decodedText = decodeSharedMarkdownDataUrl("data:image/svg+xml,%3Csvg%20viewBox%3D%220%200%201%201%22%3E%3C%2Fsvg%3E")
        assertEquals("<svg viewBox=\"0 0 1 1\"></svg>", decodedText?.bytes?.decodeToString())
        assertEquals("image/svg+xml", decodedText?.mimeType)
        assertEquals("image.svg", sharedImagePreviewName(decodedSvg?.mimeType))
    }

    @Test
    fun markdownImagesKeepAndroidAltDestinationAndLayoutAttributes() {
        val image = parseSharedMarkdownImage(
            """![Architecture](<reports/system diagram.png> "overview"){width=75% height=240 max-height=300 fit=cover scroll=yes}""",
        )

        assertEquals("Architecture", image?.altText)
        assertEquals("reports/system diagram.png", image?.url)
        assertEquals(SharedMarkdownMediaWidth.Fraction(0.75f), image?.layout?.width)
        assertEquals(240, image?.layout?.heightDp)
        assertEquals(300, image?.layout?.maxHeightDp)
        assertEquals(SharedMarkdownMediaFit.Cover, image?.layout?.fit)
        assertTrue(image?.layout?.scroll == true)
    }

    @Test
    fun markdownImageLinesAndBadgeGroupsBecomeDedicatedSegments() {
        val badgeLine = "[![Build](https://img.example/build.svg)](https://ci.example) " +
            "![Coverage](https://img.example/coverage.svg)"
        val images = parseSharedMarkdownImageSequence(badgeLine)
        assertEquals(listOf("Build", "Coverage"), images?.map { it.altText })
        assertEquals(
            listOf(
                SharedMarkdownSegment.Markdown("Before"),
                SharedMarkdownSegment.Image(
                    SharedMarkdownImageSpec("Preview", "data:image/png;base64,AA=="),
                ),
                SharedMarkdownSegment.ImageGroup(images!!),
                SharedMarkdownSegment.Markdown("After"),
            ),
            parseSharedMarkdownSegments(
                "Before\n\n![Preview](data:image/png;base64,AA==)\n$badgeLine\n\nAfter",
            ),
        )
    }

    @Test
    fun markdownImageOriginalTargetsAndSvgSanitizingMatchAndroid() {
        assertEquals(
            "https://example.com/image.png",
            sharedMarkdownImageOriginalTarget("https://example.com/image.png", null),
        )
        assertNull(sharedMarkdownImageOriginalTarget("data:image/png;base64,AA==", null))
        assertNull(sharedMarkdownImageOriginalTarget("content://images/1", null))

        val sanitized = sanitizeSharedInlineMarkdownSvg(
            """<?xml version="1.0"?><svg onclick="bad()"><script>alert(1)</script><a href="javascript:bad()" /></svg>""",
        )
        assertFalse(sanitized.contains("script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertContains(buildSharedMarkdownBadgeGroupHtml(emptyList()), "max-height:32px")
    }

    @Test
    fun mermaidFencesBecomeRenderedSegmentsAndKeepAndroidLayoutAttributes() {
        val segments = parseSharedMarkdownSegments(
            """
                Before

                ```mermaid {height=420 show-all=true width=640}
                flowchart LR
                  A --> B
                ```

                After
            """.trimIndent()
        )

        assertEquals(3, segments.size)
        assertEquals("Before", assertIs<SharedMarkdownSegment.Markdown>(segments[0]).content.trim())
        val mermaid = assertIs<SharedMarkdownSegment.Mermaid>(segments[1])
        assertEquals("flowchart LR\n  A --> B", mermaid.code)
        assertEquals(420, mermaid.layout.heightDp)
        assertEquals(SharedMarkdownMediaWidth.DpValue(640), mermaid.layout.width)
        assertTrue(mermaid.layout.showAll)
        assertFalse(mermaid.layout.scroll)
        assertNull(mermaid.layout.maxHeightDp)
        assertEquals("After", assertIs<SharedMarkdownSegment.Markdown>(segments[2]).content.trim())
    }

    @Test
    fun nonMermaidFencesStayInMarkdownAndMermaidHtmlReportsHeightAndTap() {
        val segments = parseSharedMarkdownSegments("```kotlin\nval x = 1\n```")
        assertEquals(1, segments.size)
        assertContains(assertIs<SharedMarkdownSegment.Markdown>(segments.single()).content, "```kotlin")

        val html = buildSharedMermaidHtml(
            code = "flowchart LR\nA --> B",
            layout = SharedMarkdownMediaLayout(scroll = true),
            renderError = "Could not render",
            invalidSyntax = "Invalid syntax",
        )
        assertContains(html, "mermaid@10")
        assertContains(html, "postAether('height:'")
        assertContains(html, "postAether('tap')")
        assertContains(html, "flowchart LR")
        assertContains(html, "max-width:none")
    }

    @Test
    fun streamingEventsPreserveResponseOrderAndCompleteToolJson() {
        val arguments = buildJsonObject {
            put("path", "/workspace/report.json")
            put("options", buildJsonObject {
                put("limit", 3)
                put("include_hidden", true)
            })
        }
        val outputJson = """{"content":[{"type":"text","text":"complete output"}],"meta":{"count":3}}"""
        val call = SharedPiHostToolCall("tool-1", "read", arguments)

        val message = SharedChatMessage(id = "assistant", text = "", fromUser = false)
            .appendAssistantTextDelta("First ")
            .appendAssistantTextDelta("answer")
            .appendAssistantReasoningDelta("Check inputs")
            .withStartedAssistantTool(call)
            .withFinishedAssistantTool("tool-1", SharedHostToolResult(outputJson))
            .appendAssistantTextDelta(" after tool")

        assertEquals("First answer after tool", message.text)
        assertEquals("Check inputs", message.reasoningText)
        assertEquals(3, message.responseBlocks.size)
        assertEquals("First answer", assertIs<SharedAssistantResponseBlock.Text>(message.responseBlocks[0]).text)
        val reasoning = assertIs<SharedAssistantResponseBlock.Reasoning>(message.responseBlocks[1])
        assertEquals("Check inputs", reasoning.trace.rawText)
        val tool = reasoning.trace.toolInvocations.single()
        assertEquals(arguments.toString(), tool.argumentsJson)
        assertEquals(outputJson, tool.outputJson)
        assertFalse(tool.isRunning)
        assertFalse(tool.isError)
        assertEquals(" after tool", assertIs<SharedAssistantResponseBlock.Text>(message.responseBlocks[2]).text)
        assertEquals(tool, message.tools.single())
    }

    @Test
    fun adjacentToolCallsShareAGroupAndErrorsCompleteOnlyTheirCall() {
        val first = SharedPiHostToolCall("first", "read", buildJsonObject { put("path", "/one") })
        val second = SharedPiHostToolCall("second", "read", buildJsonObject { put("path", "/two") })
        val message = SharedChatMessage(text = "", fromUser = false)
            .withStartedAssistantTool(first)
            .withStartedAssistantTool(second)
            .withFinishedAssistantTool("second", SharedHostToolResult("{\"error\":\"denied\"}", true))

        val group = assertIs<SharedAssistantResponseBlock.ToolGroup>(message.responseBlocks.single())
        assertEquals(listOf("first", "second"), group.tools.map { it.id })
        assertTrue(group.tools.first().isRunning)
        assertFalse(group.tools.first().isError)
        assertFalse(group.tools.last().isRunning)
        assertTrue(group.tools.last().isError)
        assertEquals("{\"error\":\"denied\"}", group.tools.last().outputJson)
    }

    @Test
    fun reasoningSummaryTrackerUsesInitialThresholdThenTimedChunks() {
        val tracker = SharedReasoningTurnTracker()
        val initialText = (1..99).joinToString(" ") { "token$it" } + " \u597d"
        val trace = SharedReasoningTrace(id = "trace", rawText = initialText, startedAtMillis = 1_000)

        val first = tracker.prepareSummary(trace, forceRemaining = false, nowMillis = 2_000)
        assertEquals(initialText, first?.chunk?.rawText)
        assertTrue(first?.chunk?.isPending == true)

        val extended = trace.copy(rawText = "$initialText extra words")
        assertNull(tracker.prepareSummary(extended, forceRemaining = false, nowMillis = 6_999))
        val timed = tracker.prepareSummary(extended, forceRemaining = false, nowMillis = 7_000)
        assertEquals("extra words", timed?.chunk?.rawText)
        assertTrue((timed?.chunk?.timelineOrder ?: 0L) > (first?.chunk?.timelineOrder ?: 0L))
    }

    @Test
    fun directReasoningSummaryDeltasAppendToTheActiveTimelineChunk() {
        val tracker = SharedReasoningTurnTracker()
        var message = SharedChatMessage(text = "", fromUser = false)
            .appendAssistantReasoningDelta("raw reasoning", nowMillis = 1_000)
        message = message.appendDirectAssistantReasoningSummaryDelta("Checking ", tracker, 1_100)
            .appendDirectAssistantReasoningSummaryDelta("inputs", tracker, 1_200)

        val trace = message.activeSharedReasoningTrace()!!
        assertEquals("raw reasoning", trace.rawText)
        assertEquals("Reasoning", trace.chunks.single().title)
        assertEquals("Checking inputs", trace.chunks.single().detail)

        tracker.finishDirectSummaryChunk()
        message = message.appendDirectAssistantReasoningSummaryDelta("Next step", tracker, 1_300)
        assertEquals(2, message.activeSharedReasoningTrace()!!.chunks.size)
    }

    @Test
    fun reasoningTimelineInterleavesSummariesAndToolsByTimelineOrder() {
        val trace = SharedReasoningTrace(
            id = "trace",
            chunks = listOf(
                SharedReasoningSummaryChunk("summary-1", rawText = "one", timelineOrder = 1),
                SharedReasoningSummaryChunk("summary-2", rawText = "two", timelineOrder = 3),
            ),
            toolInvocations = listOf(
                SharedChatToolInvocation("tool", "read", "", timelineOrder = 2),
            ),
        )

        val items = sharedReasoningTimelineItems(trace)
        assertIs<SharedReasoningTimelineItem.Summary>(items[0])
        assertIs<SharedReasoningTimelineItem.Tool>(items[1])
        assertIs<SharedReasoningTimelineItem.Summary>(items[2])
    }

    @Test
    fun reasoningTraceAndToolTimelineSurvivePersistenceMapping() {
        val call = SharedPiHostToolCall("tool", "read", buildJsonObject { put("path", "/notes") })
        val tracker = SharedReasoningTurnTracker()
        var message = SharedChatMessage(id = "assistant", text = "", fromUser = false)
            .appendAssistantReasoningDelta("inspect the available notes", nowMillis = 1_000)
        val trace = message.activeSharedReasoningTrace()!!
        val submission = tracker.prepareSummary(trace, forceRemaining = true, nowMillis = 1_100)!!
        message = message.withPendingReasoningSummary(submission)
            .withCompletedReasoningSummary(
                blockId = submission.blockId,
                chunkId = submission.chunk.id,
                title = "Inspecting notes",
                detail = "I need to read the available notes.",
            )
            .withStartedAssistantTool(call, startedAtMillis = 1_200, timelineOrder = tracker.nextTimelineOrder())
            .withFinishedAssistantTool("tool", SharedHostToolResult("{}"), completedAtMillis = 1_300)
            .completeAssistantReasoning(1_400)

        val restored = listOf(message).toPersistedMessages().single().toSharedChatMessage()
        val restoredTrace = assertIs<SharedAssistantResponseBlock.Reasoning>(restored.responseBlocks.single()).trace
        assertEquals("Inspecting notes", restoredTrace.chunks.single().title)
        assertEquals(1_200L, restoredTrace.toolInvocations.single().startedAtMillis)
        assertEquals(1_300L, restoredTrace.toolInvocations.single().completedAtMillis)
        assertEquals(1_400L, restoredTrace.completedAtMillis)
    }

    @Test
    fun tokenUsageSourceSurvivesPersistenceMapping() {
        val restored = listOf(
            SharedChatMessage(
                id = "assistant",
                text = "answer",
                fromUser = false,
                tokenUsageSource = "estimated",
            ),
        ).toPersistedMessages().single().toSharedChatMessage()

        assertEquals("estimated", restored.tokenUsageSource)
    }

    @Test
    fun completedAssistantWorkFoldsForEveryAndroidWorkSignal() {
        fun shouldFold(
            hasOrderedWorkBlocks: Boolean = false,
            hasFallbackReasoningOrTools: Boolean = false,
            hasAttachments: Boolean = false,
            thoughtDurationMillis: Long? = null,
        ) = shouldFoldSharedAssistantWork(
            isStreaming = false,
            text = "answer",
            hasOrderedWorkBlocks = hasOrderedWorkBlocks,
            hasFallbackReasoningOrTools = hasFallbackReasoningOrTools,
            hasAttachments = hasAttachments,
            thoughtDurationMillis = thoughtDurationMillis,
        )

        assertTrue(shouldFold(hasOrderedWorkBlocks = true))
        assertTrue(shouldFold(hasFallbackReasoningOrTools = true))
        assertTrue(shouldFold(hasAttachments = true))
        assertTrue(shouldFold(thoughtDurationMillis = 1_000L))
        assertFalse(shouldFold())
    }

    @Test
    fun streamingOrTextlessAssistantWorkStaysExpanded() {
        assertFalse(
            shouldFoldSharedAssistantWork(
                isStreaming = true,
                text = "answer",
                hasOrderedWorkBlocks = true,
                hasFallbackReasoningOrTools = true,
                hasAttachments = true,
                thoughtDurationMillis = 1_000L,
            ),
        )
        assertFalse(
            shouldFoldSharedAssistantWork(
                isStreaming = false,
                text = "",
                hasOrderedWorkBlocks = true,
                hasFallbackReasoningOrTools = true,
                hasAttachments = true,
                thoughtDurationMillis = 1_000L,
            ),
        )
    }

    @Test
    fun retryPlanCanTargetAnEarlierAssistantReply() {
        val firstUser = SharedChatMessage(id = "u1", text = "first", fromUser = true)
        val firstReply = SharedChatMessage(
            id = "a1",
            text = "first reply",
            fromUser = false,
            responseGroupId = firstUser.id,
        )
        val secondUser = SharedChatMessage(id = "u2", text = "second", fromUser = true)
        val secondReply = SharedChatMessage(
            id = "a2",
            text = "second reply",
            fromUser = false,
            responseGroupId = secondUser.id,
        )
        val messages = listOf(firstUser, firstReply, secondUser, secondReply)

        val plan = buildSharedAssistantRetryPlan(messages, firstReply.id)
        assertEquals(listOf(firstUser), plan?.retainedMessages)
        assertEquals(firstUser, plan?.userMessage)
        assertNull(buildSharedAssistantRetryPlan(messages, firstUser.id))
    }

    @Test
    fun queueSelectionSkipsPendingSteersAndPromotesThemWithAndroidPriority() {
        val steer = SharedPendingTurn(id = "steer", text = "redirect", mode = SharedPendingTurnMode.Steer)
        val queue = SharedPendingTurn(id = "queue", text = "follow up")
        assertEquals(1, listOf(steer, queue).nextSharedQueuedTurnIndex())

        val fallback = steer.fallbackSharedSteerToQueue()
        assertEquals("steer", fallback.id)
        assertEquals("redirect", fallback.text)
        assertEquals(SharedPendingTurnMode.Queue, fallback.mode)
        assertTrue(fallback.promotedFromSteer)

        val promoted = listOf(
            queue,
            steer,
            SharedPendingTurn(id = "steer-2", text = "latest", mode = SharedPendingTurnMode.Steer),
        ).promoteSharedSteersToQueue()
        assertEquals(2, promoted.nextSharedQueuedTurnIndex())
        val afterLatest = promoted.toMutableList().apply { removeAt(2) }
        assertEquals(1, afterLatest.nextSharedQueuedTurnIndex())
        val afterSteers = afterLatest.toMutableList().apply { removeAt(1) }
        assertEquals(0, afterSteers.nextSharedQueuedTurnIndex())
    }

    @Test
    fun pendingTurnPreviewMatchesAndroidSummaryAndLengthLimit() {
        assertEquals(
            "x".repeat(72),
            SharedPendingTurn(text = "  ${"x".repeat(80)}  ").sharedPreviewText(),
        )
        assertEquals(
            "notes.txt",
            SharedPendingTurn(
                text = "",
                attachments = listOf(
                    SharedChatAttachment(
                        id = "file",
                        name = "notes.txt",
                        mimeType = "text/plain",
                        workspacePath = "/workspace/notes.txt",
                    )
                ),
            ).sharedPreviewText(),
        )
    }
}
