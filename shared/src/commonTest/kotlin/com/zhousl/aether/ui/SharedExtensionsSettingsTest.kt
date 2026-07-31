package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SharedExtensionsSettingsTest {
    @Test
    fun downloadCountsUseAndroidOneDecimalRounding() {
        assertEquals("999", formatSharedExtensionDownloads(999))
        assertEquals("2.0K", formatSharedExtensionDownloads(1_999))
        assertEquals("1.3M", formatSharedExtensionDownloads(1_250_000))
    }

    @Test
    fun preservesReadmeStructureAndResolvesRelativeAssets() {
        val markdown = """
            <h1>Package README</h1>
            <p><a href="https://example.com/project"><img src="/assets/status.svg" alt="Status"></a></p>
            <p>Use <strong>carefully</strong>.</p>
            <ul><li>First feature</li><li>Second feature</li></ul>
            <pre><code>pi install example</code></pre>
        """.trimIndent().sharedHtmlToMarkdown("https://pi.dev/packages/example")

        assertContains(markdown, "# Package README")
        assertContains(markdown, "[![Status](https://pi.dev/assets/status.svg)](https://example.com/project)")
        assertContains(markdown, "Use **carefully**.")
        assertContains(markdown, "- First feature")
        assertContains(markdown, "- Second feature")
        assertContains(markdown, "```\npi install example\n```")
    }
}
