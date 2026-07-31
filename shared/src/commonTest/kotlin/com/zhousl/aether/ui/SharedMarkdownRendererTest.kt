package com.zhousl.aether.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedMarkdownRendererTest {
    @Test
    fun incompleteTableLineRemainsParagraphWhileStreaming() {
        val blocks = parseSharedMarkdownTextBlocks("| Effort | Share |")

        assertEquals(1, blocks.size)
        assertIs<SharedMarkdownTextBlock.Paragraph>(blocks.single())
    }

    @Test
    fun pipeTextStaysInParagraphUntilTableSeparatorArrives() {
        val blocks = parseSharedMarkdownTextBlocks(
            "The stream may contain a partial table next.\n| Effort | Share |",
        )

        val paragraph = assertIs<SharedMarkdownTextBlock.Paragraph>(blocks.single())
        assertContains(paragraph.text.text, "| Effort | Share |")
    }

    @Test
    fun completeTableUsesAndroidColumnAndRowRules() {
        val blocks = parseSharedMarkdownTextBlocks(
            "| Effort | Share |\n|--------|-------|\n| high   | 80%   |",
        )

        val table = assertIs<SharedMarkdownTextBlock.Table>(blocks.single())
        assertEquals(listOf("Effort", "Share"), table.headers.map { it.text })
        assertEquals(listOf("high", "80%"), table.rows.single().map { it.text })
        val widths = sharedMarkdownTableColumnWidths(2, 320.dp)
        assertEquals(320.dp, widths.reduce { total, width -> total + width })
        assertTrue(widths[1] > widths[0])
        assertTrue(
            sharedMarkdownTableColumnWidths(5, 320.dp).reduce { total, width -> total + width } > 320.dp,
        )
    }

    @Test
    fun setextHeadingsFlatListsAndRulesMatchAndroidParser() {
        val blocks = parseSharedMarkdownTextBlocks(
            "Package README\n==============\n\n* First feature\n  * Nested becomes flat\n\n*** ** * ** ***",
        )

        assertIs<SharedMarkdownTextBlock.Heading>(blocks[0]).also { assertEquals(1, it.level) }
        assertEquals(
            listOf("First feature", "Nested becomes flat"),
            assertIs<SharedMarkdownTextBlock.UnorderedList>(blocks[1]).items.map { it.text },
        )
        assertIs<SharedMarkdownTextBlock.Rule>(blocks[2])
    }

    @Test
    fun orderedListsDiscardSourceNumbersLikeAndroid() {
        val list = assertIs<SharedMarkdownTextBlock.OrderedList>(
            parseSharedMarkdownTextBlocks("7) seven\n9. nine").single(),
        )

        assertEquals(listOf("seven", "nine"), list.items.map { it.text })
    }

    @Test
    fun rawHtmlIsEscapedAndInlineFormattingIsPreserved() {
        val html = sharedInlineMarkdownToHtml(
            "<details>raw</details> **bold** *italic* `code` [Docs](https://example.com)",
        )

        assertContains(html, "&lt;details&gt;raw&lt;/details&gt;")
        assertContains(html, "<strong>bold</strong>")
        assertContains(html, "<em>italic</em>")
        assertContains(html, "<code>code</code>")
        assertContains(html, "<a href=\"https://example.com\">Docs</a>")
    }

    @Test
    fun mathDetectionSkipsCodeAndRejectsCurrencySentences() {
        assertTrue(containsSharedRenderableMarkdownMath("Euler: ${'$'}e^{i\\pi}+1=0${'$'}"))
        assertTrue(containsSharedRenderableMarkdownMath("\\[x^2 + y^2 = z^2\\]"))
        assertFalse(containsSharedRenderableMarkdownMath("`${'$'}x^2${'$'}`"))
        assertFalse(containsSharedRenderableMarkdownMath("It costs ${'$'}5 today and ${'$'}6 tomorrow"))
    }

    @Test
    fun sourceOffsetsRemainRelativeToWholeStreamingMessage() {
        val block = assertIs<SharedMarkdownTextBlock.Heading>(
            parseSharedMarkdownTextBlocks("## Heading", sourceOffset = 40).single(),
        )

        assertEquals(43, block.text.sourceOffset)
    }

    @Test
    fun positionedSegmentsKeepOffsetsAcrossImagesAndMermaid() {
        val markdown = "Before\n\n![Preview](image.png)\n\n```mermaid\nA --> B\n```\n\nAfter"
        val segments = parseSharedMarkdownPositionedSegments(markdown)

        assertEquals(0, segments[0].sourceOffset)
        assertEquals(markdown.indexOf("![Preview]"), segments[1].sourceOffset)
        assertEquals(markdown.indexOf("```mermaid"), segments[2].sourceOffset)
        assertEquals(markdown.indexOf("After"), segments[3].sourceOffset)
    }
}
