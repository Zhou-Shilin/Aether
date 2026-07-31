package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhousl.aether.platform.PlatformWebView
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlin.math.roundToInt

private const val SharedMarkdownLinkAnnotationTag = "url"
private const val SharedMarkdownTextMinHeightDp = 24
private const val SharedMarkdownTextMaxHeightDp = 2048
private const val SharedKatexCssUrl = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css"
private const val SharedKatexScriptUrl = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"
private const val SharedKatexAutoRenderScriptUrl =
    "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"
private val SharedMarkdownTableMinColumnWidth = 128.dp
private val SharedMarkdownTableDescriptionMinColumnWidth = 160.dp
private val SharedMarkdownTableScrollableColumnWidth = 148.dp

internal data class SharedMarkdownFadeSpan(
    val sourceRange: IntRange,
    val alpha: Float,
)

internal data class SharedMarkdownSourceText(
    val text: String,
    val sourceOffset: Int,
)

private data class SharedMarkdownLine(
    val text: String,
    val startOffset: Int,
)

internal sealed interface SharedMarkdownTextBlock {
    data class Paragraph(val text: SharedMarkdownSourceText) : SharedMarkdownTextBlock
    data class Heading(val level: Int, val text: SharedMarkdownSourceText) : SharedMarkdownTextBlock
    data class UnorderedList(val items: List<SharedMarkdownSourceText>) : SharedMarkdownTextBlock
    data class OrderedList(val items: List<SharedMarkdownSourceText>) : SharedMarkdownTextBlock
    data class Quote(val text: SharedMarkdownSourceText) : SharedMarkdownTextBlock
    data class Table(
        val headers: List<SharedMarkdownSourceText>,
        val rows: List<List<SharedMarkdownSourceText>>,
    ) : SharedMarkdownTextBlock
    data class CodeFence(val code: SharedMarkdownSourceText) : SharedMarkdownTextBlock
    data object Rule : SharedMarkdownTextBlock
}

private sealed interface SharedMarkdownHtmlTextVariant {
    data object Paragraph : SharedMarkdownHtmlTextVariant
    data class Heading(val level: Int) : SharedMarkdownHtmlTextVariant
    data object ListItem : SharedMarkdownHtmlTextVariant
    data object Quote : SharedMarkdownHtmlTextVariant
    data class TableCell(val isHeader: Boolean) : SharedMarkdownHtmlTextVariant
}

private data class SharedMarkdownInlineLinkMatch(
    val label: String,
    val destination: String,
    val endExclusive: Int,
)

private data class SharedMarkdownAutoLinkMatch(
    val displayText: String,
    val targetUrl: String,
)

private data class SharedMarkdownMathMatch(
    val rawText: String,
    val endExclusive: Int,
)

private val sharedMarkdownHeadingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val sharedMarkdownUnorderedPattern = Regex("^[-*+]\\s+(.+)$")
private val sharedMarkdownOrderedPattern = Regex("^(\\d+)[.)]\\s+(.+)$")
private val sharedMarkdownSetextHeadingPattern = Regex("^\\s*(=+|-+)\\s*$")
private val sharedMarkdownHorizontalRulePattern =
    Regex("^(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$")
private val sharedMarkdownTableSeparatorPattern = Regex("^:?-{3,}:?$")
private val sharedMarkdownAutoLinkPattern = Regex("""^(https?://\S+|www\.\S+)""")

@Composable
internal fun SharedMarkdownTextContent(
    markdown: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    sourceOffset: Int = 0,
    fadeSpan: SharedMarkdownFadeSpan? = null,
) {
    val normalizedMarkdown = remember(markdown) { markdown.replace("\r\n", "\n") }
    val blocks = remember(normalizedMarkdown, sourceOffset) {
        parseSharedMarkdownTextBlocks(normalizedMarkdown, sourceOffset)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is SharedMarkdownTextBlock.Paragraph -> SharedMarkdownRichTextBlock(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                    onOpenLink = onOpenLink,
                    variant = SharedMarkdownHtmlTextVariant.Paragraph,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.Heading -> SharedMarkdownRichTextBlock(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelLarge
                    },
                    color = AetherOnSurface,
                    onOpenLink = onOpenLink,
                    variant = SharedMarkdownHtmlTextVariant.Heading(block.level),
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.UnorderedList -> SharedMarkdownBullets(
                    items = block.items,
                    onOpenLink = onOpenLink,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.OrderedList -> SharedMarkdownNumbers(
                    items = block.items,
                    onOpenLink = onOpenLink,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.Quote -> SharedMarkdownQuote(
                    text = block.text,
                    onOpenLink = onOpenLink,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.Table -> SharedMarkdownTable(
                    headers = block.headers,
                    rows = block.rows,
                    onOpenLink = onOpenLink,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownTextBlock.CodeFence -> Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(AetherSurfaceHigh, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = sharedPlainMarkdownText(block.code, fadeSpan),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = AetherOnSurface,
                    )
                }
                SharedMarkdownTextBlock.Rule -> HorizontalDivider(color = AetherOutlineSoft)
            }
        }
    }
}

@Composable
private fun SharedMarkdownBullets(
    items: List<SharedMarkdownSourceText>,
    onOpenLink: (String) -> Unit,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("\u2022", style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
                Spacer(Modifier.width(10.dp))
                SharedMarkdownRichTextBlock(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                    onOpenLink = onOpenLink,
                    variant = SharedMarkdownHtmlTextVariant.ListItem,
                    fadeSpan = fadeSpan,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SharedMarkdownNumbers(
    items: List<SharedMarkdownSourceText>,
    onOpenLink: (String) -> Unit,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
                Spacer(Modifier.width(10.dp))
                SharedMarkdownRichTextBlock(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                    onOpenLink = onOpenLink,
                    variant = SharedMarkdownHtmlTextVariant.ListItem,
                    fadeSpan = fadeSpan,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SharedMarkdownQuote(
    text: SharedMarkdownSourceText,
    onOpenLink: (String) -> Unit,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.width(4.dp).background(AetherOutlineSoft, RoundedCornerShape(999.dp)))
        Spacer(Modifier.width(12.dp))
        SharedMarkdownRichTextBlock(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
            onOpenLink = onOpenLink,
            variant = SharedMarkdownHtmlTextVariant.Quote,
            fadeSpan = fadeSpan,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SharedMarkdownTable(
    headers: List<SharedMarkdownSourceText>,
    rows: List<List<SharedMarkdownSourceText>>,
    onOpenLink: (String) -> Unit,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    val columnCount = remember(headers, rows) {
        maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidths = remember(columnCount, maxWidth) {
            sharedMarkdownTableColumnWidths(columnCount, maxWidth)
        }
        val tableWidth = columnWidths.fold(0.dp) { width, columnWidth -> width + columnWidth }
            .coerceAtLeast(maxWidth)
        Column(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(18.dp)).background(AetherSurface.copy(alpha = 0.92f)),
        ) {
            SharedMarkdownTableRow(
                cells = headers,
                columnWidths = columnWidths,
                tableWidth = tableWidth,
                onOpenLink = onOpenLink,
                fadeSpan = fadeSpan,
                isHeader = true,
            )
            rows.forEachIndexed { index, row ->
                SharedMarkdownTableRow(
                    cells = row,
                    columnWidths = columnWidths,
                    tableWidth = tableWidth,
                    onOpenLink = onOpenLink,
                    fadeSpan = fadeSpan,
                    isHeader = false,
                    shaded = index % 2 == 1,
                )
            }
        }
    }
}

internal fun sharedMarkdownTableColumnWidths(columnCount: Int, viewportWidth: Dp): List<Dp> {
    val normalizedColumnCount = columnCount.coerceAtLeast(1)
    return when {
        normalizedColumnCount == 1 -> listOf(viewportWidth.coerceAtLeast(SharedMarkdownTableMinColumnWidth))
        normalizedColumnCount == 2 -> {
            val firstColumn = (viewportWidth * 0.42f).coerceAtLeast(SharedMarkdownTableMinColumnWidth)
            val secondColumn = (viewportWidth - firstColumn)
                .coerceAtLeast(SharedMarkdownTableDescriptionMinColumnWidth)
            listOf(firstColumn, secondColumn)
        }
        normalizedColumnCount == 3 -> {
            val width = (viewportWidth / normalizedColumnCount).coerceAtLeast(SharedMarkdownTableMinColumnWidth)
            List(normalizedColumnCount) { width }
        }
        else -> List(normalizedColumnCount) { SharedMarkdownTableScrollableColumnWidth }
    }
}

@Composable
private fun SharedMarkdownTableRow(
    cells: List<SharedMarkdownSourceText>,
    columnWidths: List<Dp>,
    tableWidth: Dp,
    onOpenLink: (String) -> Unit,
    fadeSpan: SharedMarkdownFadeSpan?,
    isHeader: Boolean,
    shaded: Boolean = false,
) {
    Row(
        modifier = Modifier.width(tableWidth).background(
            when {
                isHeader -> AetherSurfaceHigh
                shaded -> AetherSurface.copy(alpha = 0.68f)
                else -> Color.Transparent
            },
        ),
    ) {
        columnWidths.forEachIndexed { index, width ->
            Box(Modifier.width(width).padding(horizontal = 12.dp, vertical = 10.dp)) {
                SharedMarkdownRichTextBlock(
                    text = cells.getOrNull(index) ?: SharedMarkdownSourceText("", 0),
                    style = if (isHeader) {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = AetherOnSurface,
                    onOpenLink = onOpenLink,
                    variant = SharedMarkdownHtmlTextVariant.TableCell(isHeader),
                    fadeSpan = fadeSpan,
                    modifier = Modifier.heightIn(min = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedMarkdownRichTextBlock(
    text: SharedMarkdownSourceText,
    style: TextStyle,
    color: Color,
    onOpenLink: (String) -> Unit,
    variant: SharedMarkdownHtmlTextVariant,
    fadeSpan: SharedMarkdownFadeSpan?,
    modifier: Modifier = Modifier,
) {
    if (remember(text.text) { containsSharedRenderableMarkdownMath(text.text) }) {
        SharedMarkdownMathTextBlock(
            text = text.text,
            variant = variant,
            onOpenLink = onOpenLink,
            modifier = modifier,
        )
        return
    }
    val annotated = sharedInlineMarkdown(text, fadeSpan)
    val hasLinks = annotated.getStringAnnotations(
        tag = SharedMarkdownLinkAnnotationTag,
        start = 0,
        end = annotated.length,
    ).isNotEmpty()
    if (!hasLinks) {
        Text(annotated, style = style, color = color, modifier = modifier)
    } else {
        @Suppress("DEPRECATION")
        ClickableText(
            text = annotated,
            style = style.copy(color = color),
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(SharedMarkdownLinkAnnotationTag, offset, offset)
                    .firstOrNull()?.let { onOpenLink(it.item) }
            },
        )
    }
}

@Composable
private fun SharedMarkdownMathTextBlock(
    text: String,
    variant: SharedMarkdownHtmlTextVariant,
    onOpenLink: (String) -> Unit,
    modifier: Modifier,
) {
    val textColor = AetherOnSurface
    val linkColor = AetherPrimary
    val codeColor = AetherSurfaceHigh
    val html = remember(text, variant, textColor, linkColor, codeColor) {
        buildSharedMarkdownTextHtml(text, variant, textColor, linkColor, codeColor)
    }
    var measuredHeight by remember(html) { mutableIntStateOf(SharedMarkdownTextMinHeightDp) }
    PlatformWebView(
        url = "",
        html = html,
        onMessage = { message ->
            when {
                message.startsWith("height:") -> message.substringAfter(':').toDoubleOrNull()?.let {
                    measuredHeight = it.roundToInt().coerceIn(
                        SharedMarkdownTextMinHeightDp,
                        SharedMarkdownTextMaxHeightDp,
                    )
                }
                message.startsWith("link:") -> message.substringAfter(':').takeIf(String::isNotBlank)
                    ?.let(onOpenLink)
            }
        },
        transparentBackground = true,
        scrollEnabled = false,
        modifier = modifier.fillMaxWidth().height(measuredHeight.dp),
    )
}

internal fun parseSharedMarkdownTextBlocks(
    markdown: String,
    sourceOffset: Int = 0,
): List<SharedMarkdownTextBlock> {
    val lines = splitSharedMarkdownLines(markdown, sourceOffset)
    val blocks = mutableListOf<SharedMarkdownTextBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.text.trim()
        if (trimmed.isBlank()) {
            index++
            continue
        }
        if (trimmed.startsWith("```")) {
            index++
            val codeStartOffset = lines.getOrNull(index)?.startOffset ?: line.startOffset + line.text.length
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].text.trim().startsWith("```")) {
                codeLines += lines[index].text
                index++
            }
            if (index < lines.size) index++
            blocks += SharedMarkdownTextBlock.CodeFence(
                SharedMarkdownSourceText(codeLines.joinToString("\n"), codeStartOffset),
            )
            continue
        }
        val setextLevel = sharedSetextHeadingLevel(lines, index)
        if (setextLevel != null) {
            blocks += SharedMarkdownTextBlock.Heading(
                setextLevel,
                SharedMarkdownSourceText(
                    trimmed,
                    line.startOffset + line.text.indexOf(trimmed).coerceAtLeast(0),
                ),
            )
            index += 2
            continue
        }
        val headingMatch = sharedMarkdownHeadingPattern.matchEntire(trimmed)
        if (headingMatch != null) {
            blocks += SharedMarkdownTextBlock.Heading(
                headingMatch.groupValues[1].length,
                SharedMarkdownSourceText(
                    headingMatch.groupValues[2].trim(),
                    sharedMarkdownContentStartOffset(line, trimmed, headingMatch.groupValues[1].length),
                ),
            )
            index++
            continue
        }
        if (sharedMarkdownHorizontalRulePattern.matches(trimmed)) {
            blocks += SharedMarkdownTextBlock.Rule
            index++
            continue
        }
        if (trimmed.startsWith(">")) {
            val quoteStart = sharedMarkdownContentStartOffset(line, trimmed, 1)
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].text.trim().startsWith(">")) {
                quoteLines += lines[index].text.trim().removePrefix(">").trimStart()
                index++
            }
            blocks += SharedMarkdownTextBlock.Quote(
                SharedMarkdownSourceText(quoteLines.joinToString("\n"), quoteStart),
            )
            continue
        }
        if (looksLikeSharedMarkdownTable(lines, index)) {
            val headerLine = lines[index]
            val headers = parseSharedMarkdownTableCells(headerLine)
            val columnCount = headers.size
            index += 2
            val rows = mutableListOf<List<SharedMarkdownSourceText>>()
            while (index < lines.size) {
                val candidate = lines[index]
                if (
                    candidate.text.trim().isBlank() ||
                    !looksLikeSharedMarkdownTableDataRow(candidate.text, columnCount)
                ) break
                rows += normalizeSharedMarkdownTableRow(
                    parseSharedMarkdownTableCells(candidate),
                    columnCount,
                    candidate,
                )
                index++
            }
            blocks += SharedMarkdownTextBlock.Table(
                headers = normalizeSharedMarkdownTableRow(headers, columnCount, headerLine),
                rows = rows,
            )
            continue
        }
        if (sharedMarkdownUnorderedPattern.matches(trimmed)) {
            val items = mutableListOf<SharedMarkdownSourceText>()
            while (index < lines.size) {
                val candidate = lines[index]
                val candidateTrimmed = candidate.text.trim()
                val match = sharedMarkdownUnorderedPattern.matchEntire(candidateTrimmed) ?: break
                items += SharedMarkdownSourceText(
                    match.groupValues[1],
                    sharedMarkdownContentStartOffset(candidate, candidateTrimmed, 1),
                )
                index++
            }
            blocks += SharedMarkdownTextBlock.UnorderedList(items)
            continue
        }
        if (sharedMarkdownOrderedPattern.matches(trimmed)) {
            val items = mutableListOf<SharedMarkdownSourceText>()
            while (index < lines.size) {
                val candidate = lines[index]
                val candidateTrimmed = candidate.text.trim()
                val match = sharedMarkdownOrderedPattern.matchEntire(candidateTrimmed) ?: break
                items += SharedMarkdownSourceText(
                    match.groupValues[2],
                    sharedMarkdownContentStartOffset(candidate, candidateTrimmed, match.groupValues[1].length + 1),
                )
                index++
            }
            blocks += SharedMarkdownTextBlock.OrderedList(items)
            continue
        }
        val paragraphLines = mutableListOf<String>()
        val paragraphStart = line.startOffset
        while (index < lines.size) {
            if (lines[index].text.trim().isBlank() || beginsSharedMarkdownSpecialBlock(lines, index)) break
            paragraphLines += lines[index].text.trimEnd()
            index++
        }
        if (paragraphLines.isEmpty()) {
            paragraphLines += line.text.trimEnd()
            index++
        }
        blocks += SharedMarkdownTextBlock.Paragraph(
            SharedMarkdownSourceText(paragraphLines.joinToString("\n"), paragraphStart),
        )
    }
    return blocks
}

private fun splitSharedMarkdownLines(markdown: String, sourceOffset: Int): List<SharedMarkdownLine> {
    val lines = mutableListOf<SharedMarkdownLine>()
    var start = 0
    for (index in 0..markdown.length) {
        if (index == markdown.length || markdown[index] == '\n') {
            lines += SharedMarkdownLine(markdown.substring(start, index), sourceOffset + start)
            start = index + 1
        }
    }
    return lines
}

private fun sharedMarkdownContentStartOffset(
    line: SharedMarkdownLine,
    trimmedLine: String,
    markerLength: Int,
): Int {
    val trimmedOffset = line.text.indexOf(trimmedLine).coerceAtLeast(0)
    var offset = line.startOffset + trimmedOffset + markerLength
    val end = line.startOffset + line.text.length
    while (offset < end && line.text[offset - line.startOffset].isWhitespace()) offset++
    return offset
}

private fun beginsSharedMarkdownSpecialBlock(lines: List<SharedMarkdownLine>, index: Int): Boolean {
    val trimmed = lines.getOrNull(index)?.text?.trim() ?: return false
    return trimmed.startsWith("```") ||
        sharedSetextHeadingLevel(lines, index) != null ||
        looksLikeSharedMarkdownTable(lines, index) ||
        sharedMarkdownHeadingPattern.matches(trimmed) ||
        sharedMarkdownUnorderedPattern.matches(trimmed) ||
        sharedMarkdownOrderedPattern.matches(trimmed) ||
        trimmed.startsWith(">") ||
        sharedMarkdownHorizontalRulePattern.matches(trimmed)
}

private fun sharedSetextHeadingLevel(lines: List<SharedMarkdownLine>, index: Int): Int? {
    val heading = lines.getOrNull(index)?.text?.trim().orEmpty()
    if (heading.isBlank() || beginsSharedStandaloneMarkdownBlock(heading)) return null
    val underline = lines.getOrNull(index + 1)?.text ?: return null
    val match = sharedMarkdownSetextHeadingPattern.matchEntire(underline) ?: return null
    return if (match.groupValues[1].startsWith("=")) 1 else 2
}

private fun beginsSharedStandaloneMarkdownBlock(trimmed: String): Boolean =
    trimmed.startsWith("```") || trimmed.startsWith(">") ||
        sharedMarkdownHeadingPattern.matches(trimmed) ||
        sharedMarkdownUnorderedPattern.matches(trimmed) ||
        sharedMarkdownOrderedPattern.matches(trimmed) ||
        sharedMarkdownHorizontalRulePattern.matches(trimmed)

private fun looksLikeSharedMarkdownTable(lines: List<SharedMarkdownLine>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val headers = parseSharedMarkdownTableCells(lines[index])
    return headers.size >= 2 && isSharedMarkdownTableSeparator(lines[index + 1].text, headers.size)
}

private fun looksLikeSharedMarkdownTableDataRow(line: String, expectedColumns: Int): Boolean =
    line.count { it == '|' } >= 1 && splitSharedMarkdownTableCells(line).size == expectedColumns

private fun isSharedMarkdownTableSeparator(line: String, expectedColumns: Int): Boolean {
    val cells = splitSharedMarkdownTableCells(line)
    return cells.size == expectedColumns && cells.all { sharedMarkdownTableSeparatorPattern.matches(it.trim()) }
}

private fun parseSharedMarkdownTableCells(line: SharedMarkdownLine): List<SharedMarkdownSourceText> =
    splitSharedMarkdownTableCellsWithOffsets(line.text).map { (cell, offset) ->
        val trimmed = cell.trim()
        val leadingWhitespace = cell.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) cell.length else it }
        SharedMarkdownSourceText(trimmed, line.startOffset + offset + leadingWhitespace)
    }

private fun normalizeSharedMarkdownTableRow(
    cells: List<SharedMarkdownSourceText>,
    columnCount: Int,
    line: SharedMarkdownLine,
): List<SharedMarkdownSourceText> = if (cells.size >= columnCount) {
    cells.take(columnCount)
} else {
    cells + List(columnCount - cells.size) {
        SharedMarkdownSourceText("", line.startOffset + line.text.length)
    }
}

private fun splitSharedMarkdownTableCells(line: String): List<String> =
    splitSharedMarkdownTableCellsWithOffsets(line).map { it.first }

private fun splitSharedMarkdownTableCellsWithOffsets(line: String): List<Pair<String, Int>> {
    if ('|' !in line) return emptyList()
    val pipeIndices = line.indices.filter { line[it] == '|' }
    val cells = mutableListOf<Pair<String, Int>>()
    var segmentStart = 0
    if (pipeIndices.isNotEmpty() && line.substring(0, pipeIndices.first()).isBlank()) {
        segmentStart = pipeIndices.first() + 1
    }
    pipeIndices.forEach { pipeIndex ->
        if (pipeIndex >= segmentStart) {
            val cell = line.substring(segmentStart, pipeIndex)
            val isTrailingEmpty = pipeIndex == line.lastIndex && cell.isBlank()
            if (!isTrailingEmpty) cells += cell to segmentStart
            segmentStart = pipeIndex + 1
        }
    }
    if (segmentStart <= line.length) {
        val tail = line.substring(segmentStart)
        val hasTrailingPipe = line.trimEnd().endsWith("|")
        if (!(hasTrailingPipe && tail.isBlank())) cells += tail to segmentStart
    }
    return cells
}

private fun sharedInlineMarkdown(
    source: SharedMarkdownSourceText,
    fadeSpan: SharedMarkdownFadeSpan?,
): AnnotatedString = buildAnnotatedString {
    appendSharedInline(source.text, source.sourceOffset, fadeSpan)
}

private fun sharedPlainMarkdownText(
    source: SharedMarkdownSourceText,
    fadeSpan: SharedMarkdownFadeSpan?,
): AnnotatedString = buildAnnotatedString {
    appendSharedSourceSegment(source.text, source.sourceOffset, fadeSpan)
}

private fun AnnotatedString.Builder.appendSharedInline(
    text: String,
    sourceOffset: Int,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    var index = 0
    while (index < text.length) {
        val mathMatch = parseSharedMarkdownMathAt(text, index)
        if (mathMatch != null) {
            appendSharedSourceSegment(mathMatch.rawText, sourceOffset + index, fadeSpan)
            index = mathMatch.endExclusive
            continue
        }
        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", index + 2)
            if (end > index + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                appendSharedInline(text.substring(index + 2, end), sourceOffset + index + 2, fadeSpan)
                pop()
                index = end + 2
                continue
            }
        }
        if (text[index] == '`') {
            val end = text.indexOf('`', index + 1)
            if (end > index + 1) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = AetherSurfaceHigh))
                appendSharedSourceSegment(text.substring(index + 1, end), sourceOffset + index + 1, fadeSpan)
                pop()
                index = end + 1
                continue
            }
        }
        val linkMatch = parseSharedInlineMarkdownLink(text, index)
        if (linkMatch != null) {
            pushStyle(SpanStyle(color = AetherPrimary))
            pushStringAnnotation(SharedMarkdownLinkAnnotationTag, linkMatch.destination)
            appendSharedInline(linkMatch.label, sourceOffset + index + 1, fadeSpan)
            pop()
            pop()
            index = linkMatch.endExclusive
            continue
        }
        val autoLink = parseSharedAutoLink(text, index)
        if (autoLink != null) {
            pushStyle(SpanStyle(color = AetherPrimary))
            pushStringAnnotation(SharedMarkdownLinkAnnotationTag, autoLink.targetUrl)
            appendSharedSourceSegment(autoLink.displayText, sourceOffset + index, fadeSpan)
            pop()
            pop()
            index += autoLink.displayText.length
            continue
        }
        if (text[index] == '*') {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendSharedInline(text.substring(index + 1, end), sourceOffset + index + 1, fadeSpan)
                pop()
                index = end + 1
                continue
            }
        }
        appendSharedSourceSegment(text[index].toString(), sourceOffset + index, fadeSpan)
        index++
    }
}

private fun AnnotatedString.Builder.appendSharedSourceSegment(
    text: String,
    sourceOffset: Int,
    fadeSpan: SharedMarkdownFadeSpan?,
) {
    if (text.isEmpty()) return
    if (fadeSpan == null || fadeSpan.sourceRange.isEmpty()) {
        append(text)
        return
    }
    val segmentEnd = sourceOffset + text.length
    val fadeStart = fadeSpan.sourceRange.first.coerceAtLeast(sourceOffset)
    val fadeEnd = (fadeSpan.sourceRange.last + 1).coerceAtMost(segmentEnd)
    if (fadeEnd <= fadeStart) {
        append(text)
        return
    }
    val localStart = fadeStart - sourceOffset
    val localEnd = fadeEnd - sourceOffset
    if (localStart > 0) append(text.substring(0, localStart))
    pushStyle(SpanStyle(color = AetherOnSurface.copy(alpha = fadeSpan.alpha)))
    append(text.substring(localStart, localEnd))
    pop()
    if (localEnd < text.length) append(text.substring(localEnd))
}

private fun parseSharedInlineMarkdownLink(text: String, startIndex: Int): SharedMarkdownInlineLinkMatch? {
    if (startIndex !in text.indices || text[startIndex] != '[') return null
    val closeBracket = text.indexOf("](", startIndex)
    if (closeBracket <= startIndex) return null
    var index = closeBracket + 2
    var nested = 0
    while (index < text.length) {
        val character = text[index]
        if (character == '\\' && index + 1 < text.length) {
            index += 2
            continue
        }
        when (character) {
            '(' -> nested++
            ')' -> if (nested == 0) break else nested--
        }
        index++
    }
    if (index >= text.length || text[index] != ')') return null
    val destination = extractSharedMarkdownLinkDestination(text.substring(closeBracket + 2, index)).orEmpty()
    if (destination.isBlank()) return null
    return SharedMarkdownInlineLinkMatch(
        label = text.substring(startIndex + 1, closeBracket),
        destination = destination,
        endExclusive = index + 1,
    )
}

private fun parseSharedAutoLink(text: String, startIndex: Int): SharedMarkdownAutoLinkMatch? {
    val match = sharedMarkdownAutoLinkPattern.find(text.substring(startIndex)) ?: return null
    if (match.range.first != 0) return null
    val display = match.value.trimEnd('.', ',', ';', ':')
    if (display.isBlank()) return null
    return SharedMarkdownAutoLinkMatch(
        displayText = display,
        targetUrl = if (display.startsWith("www.", ignoreCase = true)) "https://$display" else display,
    )
}

internal fun containsSharedRenderableMarkdownMath(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        if (text[index] == '`') {
            val end = text.indexOf('`', index + 1)
            if (end > index) {
                index = end + 1
                continue
            }
        }
        if (parseSharedMarkdownMathAt(text, index) != null) return true
        index++
    }
    return false
}

private fun parseSharedMarkdownMathAt(text: String, startIndex: Int): SharedMarkdownMathMatch? {
    if (startIndex !in text.indices) return null
    val opening = when {
        text.startsWith("$$", startIndex) -> "$$"
        text.startsWith("\\[", startIndex) -> "\\["
        text.startsWith("\\(", startIndex) -> "\\("
        text[startIndex] == '$' -> "$"
        else -> return null
    }
    val closing = when (opening) {
        "$$" -> "$$"
        "\\[" -> "\\]"
        "\\(" -> "\\)"
        else -> "$"
    }
    val contentStart = startIndex + opening.length
    if (contentStart >= text.length) return null
    var index = contentStart
    while (index < text.length) {
        if (text.startsWith(closing, index)) {
            val content = text.substring(contentStart, index)
            if (!isSharedRenderableMarkdownMathContent(opening, content)) return null
            return SharedMarkdownMathMatch(
                rawText = text.substring(startIndex, index + closing.length),
                endExclusive = index + closing.length,
            )
        }
        if (text[index] == '\\' && !text.startsWith(closing, index) && index + 1 < text.length) {
            index += 2
        } else {
            index++
        }
    }
    return null
}

private fun isSharedRenderableMarkdownMathContent(opening: String, content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isBlank()) return false
    if (opening != "$") return true
    if ('\n' in trimmed) return false
    return trimmed.any { it in "\\_^{}=+-*/()[]<>" } || trimmed.none(Char::isWhitespace)
}

internal fun sharedInlineMarkdownToHtml(text: String): String = buildString {
    appendSharedInlineHtml(text)
}

private fun StringBuilder.appendSharedInlineHtml(text: String) {
    var index = 0
    while (index < text.length) {
        val mathMatch = parseSharedMarkdownMathAt(text, index)
        if (mathMatch != null) {
            append(escapeSharedMarkdownHtml(mathMatch.rawText))
            index = mathMatch.endExclusive
            continue
        }
        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", index + 2)
            if (end > index + 2) {
                append("<strong>")
                appendSharedInlineHtml(text.substring(index + 2, end))
                append("</strong>")
                index = end + 2
                continue
            }
        }
        if (text[index] == '`') {
            val end = text.indexOf('`', index + 1)
            if (end > index + 1) {
                append("<code>").append(escapeSharedMarkdownHtml(text.substring(index + 1, end))).append("</code>")
                index = end + 1
                continue
            }
        }
        val linkMatch = parseSharedInlineMarkdownLink(text, index)
        if (linkMatch != null) {
            append("<a href=\"").append(escapeSharedMarkdownHtml(linkMatch.destination)).append("\">")
            appendSharedInlineHtml(linkMatch.label)
            append("</a>")
            index = linkMatch.endExclusive
            continue
        }
        val autoLink = parseSharedAutoLink(text, index)
        if (autoLink != null) {
            append("<a href=\"").append(escapeSharedMarkdownHtml(autoLink.targetUrl)).append("\">")
            append(escapeSharedMarkdownHtml(autoLink.displayText)).append("</a>")
            index += autoLink.displayText.length
            continue
        }
        if (text[index] == '*') {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1) {
                append("<em>")
                appendSharedInlineHtml(text.substring(index + 1, end))
                append("</em>")
                index = end + 1
                continue
            }
        }
        append(escapeSharedMarkdownHtml(text[index].toString()))
        index++
    }
}

private fun buildSharedMarkdownTextHtml(
    text: String,
    variant: SharedMarkdownHtmlTextVariant,
    textColor: Color,
    linkColor: Color,
    codeColor: Color,
): String {
    val variantCss = when (variant) {
        SharedMarkdownHtmlTextVariant.Paragraph,
        SharedMarkdownHtmlTextVariant.ListItem,
        SharedMarkdownHtmlTextVariant.Quote -> "font-size:16px;line-height:24px;font-weight:400;"
        is SharedMarkdownHtmlTextVariant.Heading -> when (variant.level) {
            1 -> "font-size:28px;line-height:34px;font-weight:600;"
            2 -> "font-size:22px;line-height:30px;font-weight:600;"
            3 -> "font-size:18px;line-height:26px;font-weight:600;"
            else -> "font-size:14px;line-height:20px;font-weight:600;"
        }
        is SharedMarkdownHtmlTextVariant.TableCell -> if (variant.isHeader) {
            "font-size:14px;line-height:20px;font-weight:600;"
        } else {
            "font-size:14px;line-height:20px;font-weight:400;"
        }
    }
    val content = sharedInlineMarkdownToHtml(text)
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <link rel="stylesheet" href="$SharedKatexCssUrl" />
          <style>
            html,body{margin:0;padding:0;background:transparent;}
            body{color:${sharedMarkdownCssColor(textColor)};font-family:-apple-system,BlinkMacSystemFont,sans-serif;}
            .aether-text{$variantCss white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;}
            .aether-text a{color:${sharedMarkdownCssColor(linkColor)};text-decoration:none;}
            .aether-text strong{font-weight:600;}.aether-text em{font-style:italic;}
            .aether-text code{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:.92em;background:${sharedMarkdownCssColor(codeColor)};border-radius:8px;padding:.08em .34em;}
            .aether-text .katex{white-space:normal;}.aether-text .katex-display{margin:.45em 0;overflow-x:auto;overflow-y:hidden;padding-bottom:2px;}
            .aether-text .katex-display>.katex{white-space:nowrap;}
          </style>
          <script defer src="$SharedKatexScriptUrl"></script>
          <script defer src="$SharedKatexAutoRenderScriptUrl"></script>
        </head>
        <body>
          <div class="aether-text">$content</div>
          <script>
            function postAether(message){if(window.Aether&&window.Aether.postMessage){window.Aether.postMessage(message);}}
            function reportAetherHeight(){var h=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0,$SharedMarkdownTextMinHeightDp);postAether('height:'+h);}
            function bindAetherLinks(){document.querySelectorAll('a[href]').forEach(function(a){a.onclick=function(e){e.preventDefault();var href=a.getAttribute('href');if(href){postAether('link:'+href);}return false;};});}
            function renderAetherMath(){bindAetherLinks();try{if(window.renderMathInElement){window.renderMathInElement(document.body,{delimiters:[{left:'${'$'}${'$'}',right:'${'$'}${'$'}',display:true},{left:'\\[',right:'\\]',display:true},{left:'${'$'}',right:'${'$'}',display:false},{left:'\\(',right:'\\)',display:false}],throwOnError:false,strict:'ignore',ignoredTags:['script','noscript','style','textarea','pre','code','option']});}}catch(e){}finally{bindAetherLinks();setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);setTimeout(reportAetherHeight,360);}}
            window.addEventListener('resize',reportAetherHeight);window.addEventListener('load',function(){setTimeout(renderAetherMath,0);setTimeout(reportAetherHeight,120);});
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeSharedMarkdownHtml(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(character)
        }
    }
}

private fun sharedMarkdownCssColor(color: Color): String {
    fun component(value: Float): String = (value.coerceIn(0f, 1f) * 255f).roundToInt()
        .toString(16).padStart(2, '0').uppercase()
    val alpha = component(color.alpha)
    return "#${component(color.red)}${component(color.green)}${component(color.blue)}" +
        if (alpha == "FF") "" else alpha
}
