package com.zhousl.aether.channel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMessageRendererTest {
    @Test
    fun toolPayloadsAreRedactedAndTruncated() {
        val renderer = ChannelMessageRenderer(
            ChannelDisplayOptions(toolCallMaxLength = 80, toolResultMaxLength = 30)
        )

        val call = renderer.toolCall(
            "fetch",
            """{"authorization":"Bearer secret","query":"${"x".repeat(120)}"}""",
        ).orEmpty()
        val result = renderer.toolResult(
            "fetch",
            """{"token":"secret","value":"${"y".repeat(120)}"}""",
            isError = false,
        ).orEmpty()

        assertTrue("[REDACTED]" in call)
        assertTrue(call.endsWith("```"))
        assertFalse("Bearer secret" in call)
        assertFalse("\"secret\"" in result)
        assertTrue("…" in result)
    }

    @Test
    fun disabledEventTypesDoNotRender() {
        val renderer = ChannelMessageRenderer(
            ChannelDisplayOptions(
                showToolCalls = false,
                showToolResults = false,
                showThinking = false,
            )
        )

        assertNull(renderer.toolCall("tool", "{}"))
        assertNull(renderer.toolResult("tool", "ok", false))
        assertNull(renderer.thinking("reasoning"))
    }

    @Test
    fun sentFileResultNeverLeaksWorkspacePath() {
        val output = """
            {
              "ok": true,
              "_aether_channel_file": {
                "path": "/workspace/private/report.pdf",
                "name": "report.pdf",
                "size_bytes": 2048
              }
            }
        """.trimIndent()

        val rendered = ChannelMessageRenderer(ChannelDisplayOptions())
            .toolResult("send_file_to_user", output, false)
            .orEmpty()

        assertTrue("report.pdf" in rendered)
        assertFalse("/workspace/private" in rendered)
    }
}
