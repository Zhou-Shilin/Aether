package com.zhousl.aether.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedMarkdownRendererTest {
    @Test
    fun displayMathKeepsStandaloneEqualsAndSourceOffsets() {
        val formulas = listOf(
            "\\text{mass}\\times\\text{acceleration}\n=\n\\text{force}",
            "\\rho\\left(\n\\frac{\\partial \\mathbf{u}}{\\partial t}\n+\n" +
                "\\mathbf{u}\\cdot\\nabla\\mathbf{u}\n\\right)\n=\n" +
                "-\\nabla p+\\mu\\nabla^2\\mathbf{u}+\\mathbf{f}",
        )
        for ((open, close) in listOf("\\[" to "\\]", "$$" to "$$")) {
            for (formula in formulas) {
                val math = "$open\n$formula\n$close"
                val blocks = parseSharedMarkdownTextBlocks(
                    "Before\n$math\nAfter\n\nTitle\n=====", sourceOffset = 40,
                )
                assertEquals(4, blocks.size)
                assertIs<SharedMarkdownTextBlock.Paragraph>(blocks[0])
                val source = assertIs<SharedMarkdownTextBlock.Paragraph>(blocks[1]).text
                assertEquals(math, source.text)
                assertEquals(47, source.sourceOffset)
                assertEquals("After", assertIs<SharedMarkdownTextBlock.Paragraph>(blocks[2]).text.text)
                assertIs<SharedMarkdownTextBlock.Heading>(blocks[3])
                assertTrue(containsSharedRenderableMarkdownMath(source.text))
            }
        }
    }

    @Test
    fun displayMathInCodeFencesStaysCode() {
        val blocks = parseSharedMarkdownTextBlocks("```tex\n\\[\na\n=\nb\n\\]\n```")
        assertIs<SharedMarkdownTextBlock.CodeFence>(blocks.single())
    }

    @Test
    fun mathHtmlUsesSingleBackslashTexDelimitersAtRuntime() {
        val source = "Inline \\(x^2\\) and \\[\\frac{1}{2}\\] and ${'$'}x${'$'} and ${'$'}${'$'}y${'$'}${'$'}"
        val html = buildSharedMarkdownTextHtml(
            source, SharedMarkdownHtmlTextVariant.Paragraph, Color.Black, Color.Blue, Color.Gray,
        )
        val delimiters = Json.parseToJsonElement(
            html.substringAfter("delimiters:").substringBefore("],") + "]",
        ).jsonArray

        assertEquals(listOf("$$", "\\[", "$", "\\("), delimiters.map {
            it.jsonObject.getValue("left").jsonPrimitive.content
        })
        assertEquals(listOf("$$", "\\]", "$", "\\)"), delimiters.map {
            it.jsonObject.getValue("right").jsonPrimitive.content
        })
        assertEquals(listOf("true", "true", "false", "false"), delimiters.map {
            it.jsonObject.getValue("display").jsonPrimitive.content
        })
        assertContains(html, source)
    }

    @Test
    fun inlineMarkdownDoesNotInjectBidiControlsIntoSelectableText() {
        val rendered = sharedInlineMarkdown(
            source = SharedMarkdownSourceText("سلام `printf('%s', value)` دنیا", 0),
            fadeSpan = null,
        )

        assertEquals("سلام printf('%s', value) دنیا", rendered.text)
        assertFalse(rendered.text.any { it in "\u2066\u2067\u2068\u2069" })
    }

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

    @Test
    fun mathHtmlMeasuresContentContainerAndIgnoresHeightOnlyResize() {
        val html = buildSharedMarkdownTextHtml(
            text = "Formula: ${'$'}x^2 + y^2 = z^2${'$'}",
            variant = SharedMarkdownHtmlTextVariant.TableCell(isHeader = false),
            textColor = Color.Black,
            linkColor = Color.Blue,
            codeColor = Color.Gray,
        )

        assertContains(html, ".aether-text")
        assertContains(html, "getBoundingClientRect")
        assertContains(html, "lastReportedWidth")
        assertFalse(html.contains("document.documentElement.scrollHeight"))
        assertFalse(html.contains("window.addEventListener('resize',reportAetherHeight)"))
    }
}
