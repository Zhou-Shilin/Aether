package com.zhousl.aether.ui

import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
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
                val blocks = parseMarkdownBlocks("Before\n$math\nAfter\n\nTitle\n=====")
                assertEquals(listOf("Paragraph", "Paragraph", "Paragraph", "Heading"),
                    blocks.map { it?.javaClass?.simpleName })
                val block = requireNotNull(blocks[1])
                val source = block.javaClass.getDeclaredMethod("getText").apply {
                    isAccessible = true
                }.invoke(block) as MarkdownSourceText
                assertEquals(math, source.text)
                assertEquals(7, source.sourceOffset)
            }
        }
    }

    @Test
    fun displayMathInCodeFencesStaysCode() {
        val blocks = parseMarkdownBlocks("```tex\n\\[\na\n=\nb\n\\]\n```")
        assertEquals(listOf("CodeFence"), blocks.map { it?.javaClass?.simpleName })
    }

    @Test
    fun mathHtmlUsesSingleBackslashTexDelimitersAtRuntime() {
        val source = "Inline \\(x^2\\) and \\[\\frac{1}{2}\\] and ${'$'}x${'$'} and ${'$'}${'$'}y${'$'}${'$'}"
        val html = buildMarkdownTextHtml(source, MarkdownHtmlTextVariant.Paragraph)
        val delimiters = JSONArray(
            html.substringAfter("delimiters:").substringBefore("],") + "]",
        ).let { array -> (0 until array.length()).map(array::getJSONObject) }

        assertEquals(listOf("$$", "\\[", "$", "\\("), delimiters.map {
            it.getString("left")
        })
        assertEquals(listOf("$$", "\\]", "$", "\\)"), delimiters.map {
            it.getString("right")
        })
        assertEquals(listOf(true, true, false, false), delimiters.map {
            it.getBoolean("display")
        })
        assertTrue(html.contains(source))
    }

    @Test
    fun inlineMarkdownDoesNotInjectBidiControlsIntoSelectableText() {
        val rendered = inlineMarkdown(
            text = "سلام `printf('%s', value)` دنیا",
            sourceOffset = 0,
            fadeSpan = null,
        )

        assertEquals("سلام printf('%s', value) دنیا", rendered.text)
        assertFalse(rendered.text.any { it in "\u2066\u2067\u2068\u2069" })
    }

    @Test(timeout = 1_000L)
    fun parseMarkdownTreatsIncompleteTableLineAsParagraph() {
        val blocks = parseMarkdownBlocks("| Effort | Share |")

        assertEquals(1, blocks.size)
        assertEquals("Paragraph", blocks.single()?.javaClass?.simpleName)
    }

    @Test(timeout = 1_000L)
    fun parseMarkdownKeepsPipeTextInsideParagraphUntilTableIsComplete() {
        val blocks = parseMarkdownBlocks(
            """
            The stream may contain a partial table next.
            | Effort | Share |
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        assertEquals("Paragraph", blocks.single()?.javaClass?.simpleName)
    }

    @Test(timeout = 1_000L)
    fun parseMarkdownStillParsesCompleteTables() {
        val blocks = parseMarkdownBlocks(
            """
            | Effort | Share |
            |--------|-------|
            | high   | 80%   |
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        assertEquals("Table", blocks.single()?.javaClass?.simpleName)
    }

    @Test
    fun markdownTableColumnWidthsFitTwoColumnTablesIntoViewport() {
        val widths = markdownTableColumnWidths(columnCount = 2, viewportWidth = 320.dp)

        assertEquals(2, widths.size)
        assertEquals(320.dp, widths.reduce { total, width -> total + width })
        assertTrue(widths[1] > widths[0])
    }

    @Test
    fun markdownTableColumnWidthsKeepManyColumnTablesScrollable() {
        val widths = markdownTableColumnWidths(columnCount = 5, viewportWidth = 320.dp)

        assertEquals(5, widths.size)
        assertTrue(widths.reduce { total, width -> total + width } > 320.dp)
    }

    @Test
    fun parseMarkdownImageSupportsOptionalTitleAndAngleWrappedUrls() {
        val image = parseMarkdownImage(
            "![Example](<https://example.com/assets/diagram(v2).svg> \"A title\")"
        )

        requireNotNull(image)
        assertEquals("Example", image.altText)
        assertEquals("https://example.com/assets/diagram(v2).svg", image.url)
    }

    @Test
    fun parseMarkdownImageSequenceSupportsLinkedAndAdjacentImages() {
        val images = parseMarkdownImageSequence(
            "[![Build](https://example.com/build.svg)](https://example.com/actions) " +
                "![Coverage](https://example.com/coverage.svg)"
        )

        requireNotNull(images)
        assertEquals(2, images.size)
        assertEquals("Build", images[0].altText)
        assertEquals("https://example.com/build.svg", images[0].url)
        assertEquals("Coverage", images[1].altText)
    }

    @Test
    fun parseMarkdownRecognizesHtmlConverterListsRulesAndSetextHeadings() {
        val blocks = parseMarkdownBlocks(
            """
            Package README
            ==============

            * First feature
            * Second feature

            *** ** * ** ***
            """.trimIndent(),
        )

        assertEquals(
            listOf("Heading", "UnorderedList", "Rule"),
            blocks.map { it?.javaClass?.simpleName },
        )
    }

    @Test
    fun parseMarkdownCreatesImageBlocksForLinkedBadgeRows() {
        val blocks = parseMarkdownBlocks(
            "[![Build](https://example.com/build.svg)](https://example.com/actions) " +
                "[![Coverage](https://example.com/coverage.svg)](https://example.com/coverage)"
        )

        assertEquals(listOf("ImageGroup"), blocks.map { it?.javaClass?.simpleName })
    }

    @Test
    fun normalizeMarkdownImageUrlDecodesCommonHtmlEscapes() {
        assertEquals(
            "https://example.com/image.png?foo=1&bar=2",
            normalizeMarkdownImageUrl("https://example.com/image.png?foo=1&amp;bar=2"),
        )
    }

    @Test
    fun extractMarkdownLinkDestinationStopsBeforeOptionalTitle() {
        assertEquals(
            "https://example.com/a(b)c.png",
            extractMarkdownLinkDestination("https://example.com/a(b)c.png 'preview'"),
        )
    }

    @Test
    fun inferMarkdownImageMimeTypeDetectsSvgFromBytes() {
        val mimeType = inferMarkdownImageMimeType(
            reportedMimeType = null,
            rawUrl = "https://example.com/logo",
            bytes = "<svg viewBox=\"0 0 10 10\"></svg>".toByteArray(),
        )

        assertEquals("image/svg+xml", mimeType)
    }

    @Test
    fun inferMarkdownImageMimeTypeNormalizesSvgMimeVariants() {
        val mimeType = inferMarkdownImageMimeType(
            reportedMimeType = "image/svg",
            rawUrl = "/tmp/preview",
            bytes = ByteArray(0),
        )

        assertEquals("image/svg+xml", mimeType)
    }

    @Test
    fun sanitizeInlineMarkdownSvgRemovesScriptAndJavascriptHandlers() {
        val sanitized = sanitizeInlineMarkdownSvg(
            """
            <?xml version="1.0"?>
            <svg onclick="alert(1)" viewBox="0 0 10 10">
                <script>alert(1)</script>
                <a href="javascript:alert(1)"><rect width="10" height="10" /></a>
            </svg>
            """.trimIndent(),
        )

        assertTrue(sanitized.startsWith("<svg"))
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
    }

    @Test
    fun buildMarkdownInlineSvgHtmlEmbedsSvgWithoutPreviewImageAlt() {
        val html = buildMarkdownInlineSvgHtml("<svg viewBox=\"0 0 10 10\"></svg>")

        assertTrue(html.contains("<svg viewBox=\"0 0 10 10\"></svg>"))
        assertFalse(html.contains("alt=\"preview\""))
    }

    @Test
    fun buildMarkdownBadgeGroupHtmlUsesCompactNaturalImageSizing() {
        val html = buildMarkdownBadgeGroupHtml(
            listOf(
                MarkdownImageSpec("Build", "https://example.com/build.svg"),
                MarkdownImageSpec("Coverage", "https://example.com/coverage.svg"),
            )
        )

        assertTrue(html.contains("class=\"badge-row\""))
        assertTrue(html.contains("max-height: 32px"))
        assertFalse(html.contains("min-height: 136px"))
    }

    @Test
    fun parseMarkdownImageRejectsBlankDestinations() {
        assertNull(parseMarkdownImage("![Example]()"))
    }

    @Test
    fun parseMarkdownImageReadsMediaLayoutAttributes() {
        val image = parseMarkdownImage(
            "![Example](https://example.com/image.png){width=75% height=280 scroll=true show-all=false fit=cover}"
        )

        requireNotNull(image)
        assertEquals(MarkdownMediaWidth.Fraction(0.75f), image.layout.width)
        assertEquals(280, image.layout.heightDp)
        assertTrue(image.layout.scroll)
        assertFalse(image.layout.showAll)
        assertEquals(MarkdownMediaFit.Cover, image.layout.fit)
    }

    @Test
    fun parseMarkdownCodeFenceHeaderReadsMermaidAttributes() {
        val header = parseMarkdownCodeFenceHeader("```mermaid {height=420 show-all=true width=640}")
        val layout = parseMarkdownMediaLayout(
            attributes = header.attributes,
            defaults = MarkdownMediaLayout(maxHeightDp = 640),
        )

        assertEquals("mermaid", header.language)
        assertEquals(MarkdownMediaWidth.DpValue(640), layout.width)
        assertEquals(420, layout.heightDp)
        assertTrue(layout.showAll)
        assertFalse(layout.scroll)
        assertNull(layout.maxHeightDp)
    }

    @Test
    fun markdownTextHtmlMeasuresContentContainerAndIgnoresHeightOnlyResize() {
        val html = buildMarkdownTextHtml(
            text = "Formula: ${'$'}x^2 + y^2 = z^2${'$'}",
            variant = MarkdownHtmlTextVariant.TableCell(isHeader = false),
        )

        assertTrue(html.contains(".aether-text"))
        assertTrue(html.contains("getBoundingClientRect"))
        assertTrue(html.contains("lastReportedWidth"))
        assertFalse(html.contains("document.documentElement.scrollHeight"))
        assertFalse(html.contains("window.addEventListener('resize', reportAetherHeight)"))
    }

    @Test
    fun markdownMediaAttributesClampExtremeDimensionsToSafeCeiling() {
        val image = parseMarkdownImage(
            "![Tall](https://example.com/tall.png){width=999999 height=999999 min-height=500000 max-height=800000}"
        )

        requireNotNull(image)
        assertEquals(MarkdownMediaWidth.DpValue(SafeMaxMarkdownDimensionDp), image.layout.width)
        assertEquals(SafeMaxMarkdownDimensionDp, image.layout.heightDp)
        assertEquals(SafeMaxMarkdownDimensionDp, image.layout.minHeightDp)
        assertEquals(SafeMaxMarkdownDimensionDp, image.layout.maxHeightDp)
    }

    private fun parseMarkdownBlocks(markdown: String): List<*> {
        val method = Class.forName("com.zhousl.aether.ui.MarkdownRendererKt")
            .getDeclaredMethod("parseMarkdown", String::class.java)
        method.isAccessible = true
        return method.invoke(null, markdown) as List<*>
    }
}
