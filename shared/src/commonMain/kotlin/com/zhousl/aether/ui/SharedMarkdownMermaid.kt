package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhousl.aether.platform.PlatformWebView
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.common_close
import com.zhousl.aether.shared.resources.markdown_mermaid_error_invalid_syntax
import com.zhousl.aether.shared.resources.markdown_mermaid_error_render
import com.zhousl.aether.shared.resources.markdown_mermaid_preview_title
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import org.jetbrains.compose.resources.stringResource

internal const val SharedDefaultMermaidMinHeightDp = 220
internal const val SharedDefaultMermaidMaxHeightDp = 640
private const val SharedMermaidPreviewMinHeightDp = 320
private const val SharedMermaidPreviewMaxHeightDp = 820
private const val SharedMermaidScriptUrl = "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"

internal sealed interface SharedMarkdownSegment {
    data class Markdown(val content: String) : SharedMarkdownSegment
    data class Image(val image: SharedMarkdownImageSpec) : SharedMarkdownSegment
    data class ImageGroup(val images: List<SharedMarkdownImageSpec>) : SharedMarkdownSegment
    data class Mermaid(
        val code: String,
        val layout: SharedMarkdownMediaLayout = sharedDefaultMermaidLayout(),
    ) : SharedMarkdownSegment
}

internal data class SharedMarkdownMediaLayout(
    val width: SharedMarkdownMediaWidth? = null,
    val heightDp: Int? = null,
    val minHeightDp: Int? = null,
    val maxHeightDp: Int? = null,
    val fit: SharedMarkdownMediaFit = SharedMarkdownMediaFit.Contain,
    val scroll: Boolean = false,
    val showAll: Boolean = false,
)

internal enum class SharedMarkdownMediaFit {
    Contain,
    Cover,
}

internal data class SharedMarkdownImageSpec(
    val altText: String,
    val url: String,
    val layout: SharedMarkdownMediaLayout = sharedDefaultImageLayout(),
)

internal sealed interface SharedMarkdownMediaWidth {
    data class Fraction(val value: Float) : SharedMarkdownMediaWidth
    data class DpValue(val value: Int) : SharedMarkdownMediaWidth
}

private data class SharedMarkdownCodeFenceHeader(
    val language: String,
    val attributes: Map<String, String>,
)

private val sharedMarkdownCodeFenceHeaderPattern =
    Regex("^```\\s*([^\\s{]+)?\\s*(?:\\{(.*)\\})?\\s*$")
private val sharedMarkdownAttributePattern =
    Regex("""([A-Za-z][A-Za-z0-9_-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\s]+)))?""")
private val sharedMarkdownImagePattern = Regex("^!\\[(.*?)]\\((.+)\\)(?:\\{(.*)\\})?$")

internal data class SharedMarkdownPositionedSegment(
    val segment: SharedMarkdownSegment,
    val sourceOffset: Int,
)

private data class SharedMarkdownSegmentLine(
    val text: String,
    val sourceOffset: Int,
)

internal fun parseSharedMarkdownSegments(markdown: String): List<SharedMarkdownSegment> =
    parseSharedMarkdownPositionedSegments(markdown).map(SharedMarkdownPositionedSegment::segment)

internal fun parseSharedMarkdownPositionedSegments(markdown: String): List<SharedMarkdownPositionedSegment> {
    val normalizedMarkdown = markdown.replace("\r\n", "\n")
    val lines = buildList {
        var sourceOffset = 0
        normalizedMarkdown.split('\n').forEach { line ->
            add(SharedMarkdownSegmentLine(line, sourceOffset))
            sourceOffset += line.length + 1
        }
    }
    val segments = mutableListOf<SharedMarkdownPositionedSegment>()
    val pendingMarkdown = mutableListOf<SharedMarkdownSegmentLine>()

    fun flushMarkdown() {
        val rawValue = pendingMarkdown.joinToString("\n") { it.text }
        val value = rawValue.trim('\n')
        if (value.isNotBlank()) {
            val leadingCharacters = rawValue.indexOf(value).coerceAtLeast(0)
            segments += SharedMarkdownPositionedSegment(
                segment = SharedMarkdownSegment.Markdown(value),
                sourceOffset = pendingMarkdown.first().sourceOffset + leadingCharacters,
            )
        }
        pendingMarkdown.clear()
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.text.trim()
        val images = parseSharedMarkdownImageSequence(trimmed)
        if (images != null) {
            flushMarkdown()
            val segment = if (images.size == 1) {
                SharedMarkdownSegment.Image(images.single())
            } else {
                SharedMarkdownSegment.ImageGroup(images)
            }
            segments += SharedMarkdownPositionedSegment(
                segment = segment,
                sourceOffset = line.sourceOffset + line.text.indexOf(trimmed).coerceAtLeast(0),
            )
            index++
            continue
        }
        if (!trimmed.startsWith("```")) {
            pendingMarkdown += line
            index++
            continue
        }

        val header = parseSharedMarkdownCodeFenceHeader(trimmed)
        if (!header.language.equals("mermaid", ignoreCase = true)) {
            pendingMarkdown += line
            index++
            while (index < lines.size) {
                val codeLine = lines[index]
                pendingMarkdown += codeLine
                index++
                if (codeLine.text.trim().startsWith("```")) break
            }
            continue
        }

        flushMarkdown()
        val fenceSourceOffset = line.sourceOffset + line.text.indexOf(trimmed).coerceAtLeast(0)
        index++
        val code = mutableListOf<String>()
        while (index < lines.size && !lines[index].text.trim().startsWith("```")) {
            code += lines[index].text
            index++
        }
        if (index < lines.size) index++
        segments += SharedMarkdownPositionedSegment(
            segment = SharedMarkdownSegment.Mermaid(
                code = code.joinToString("\n"),
                layout = parseSharedMarkdownMediaLayout(
                    attributes = header.attributes,
                    defaults = sharedDefaultMermaidLayout(),
                ),
            ),
            sourceOffset = fenceSourceOffset,
        )
    }
    flushMarkdown()
    return segments
}

private fun parseSharedMarkdownCodeFenceHeader(line: String): SharedMarkdownCodeFenceHeader {
    val match = sharedMarkdownCodeFenceHeaderPattern.matchEntire(line.trim())
    return SharedMarkdownCodeFenceHeader(
        language = match?.groupValues?.getOrNull(1).orEmpty().trim(),
        attributes = parseSharedMarkdownAttributes(match?.groupValues?.getOrNull(2).orEmpty()),
    )
}

private fun parseSharedMarkdownAttributes(rawAttributes: String): Map<String, String> {
    val trimmed = rawAttributes.trim().removePrefix("{").removeSuffix("}").trim()
    if (trimmed.isBlank()) return emptyMap()
    return buildMap {
        sharedMarkdownAttributePattern.findAll(trimmed).forEach { match ->
            val key = match.groupValues[1].trim().lowercase()
            if (key.isNotBlank()) {
                val value = listOf(match.groupValues[2], match.groupValues[3], match.groupValues[4])
                    .firstOrNull { it.isNotEmpty() }
                    ?: "true"
                put(key, value.trim())
            }
        }
    }
}

private fun parseSharedMarkdownMediaLayout(
    attributes: Map<String, String>,
    defaults: SharedMarkdownMediaLayout,
): SharedMarkdownMediaLayout {
    if (attributes.isEmpty()) return defaults
    fun value(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { attributes[it.lowercase()]?.trim()?.takeIf(String::isNotBlank) }

    val explicitMaxHeight = value("max-height", "max_height", "maxheight") != null
    val showAll = value("show-all", "show_all", "showall", "full")
        ?.let(::parseSharedMarkdownBoolean) ?: defaults.showAll
    val scroll = value("scroll")?.let(::parseSharedMarkdownBoolean) ?: defaults.scroll
    return SharedMarkdownMediaLayout(
        width = value("width", "w")?.let(::parseSharedMarkdownMediaWidth) ?: defaults.width,
        heightDp = value("height", "h")?.let(::parseSharedMarkdownDp) ?: defaults.heightDp,
        minHeightDp = value("min-height", "min_height", "minheight")
            ?.let(::parseSharedMarkdownDp) ?: defaults.minHeightDp,
        maxHeightDp = when {
            showAll && !explicitMaxHeight -> null
            else -> value("max-height", "max_height", "maxheight")
                ?.let(::parseSharedMarkdownDp) ?: defaults.maxHeightDp
        },
        fit = value("fit")?.let(::parseSharedMarkdownMediaFit) ?: defaults.fit,
        scroll = if (showAll) false else scroll,
        showAll = showAll,
    )
}

private fun sharedDefaultImageLayout() = SharedMarkdownMediaLayout(
    minHeightDp = 1,
    maxHeightDp = 420,
    fit = SharedMarkdownMediaFit.Contain,
)

private fun sharedDefaultMermaidLayout() = SharedMarkdownMediaLayout(
    minHeightDp = SharedDefaultMermaidMinHeightDp,
    maxHeightDp = SharedDefaultMermaidMaxHeightDp,
    scroll = true,
)

private fun parseSharedMarkdownMediaWidth(value: String): SharedMarkdownMediaWidth? {
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() || normalized == "full" -> null
        normalized.endsWith('%') -> normalized.removeSuffix("%").toFloatOrNull()?.let {
            SharedMarkdownMediaWidth.Fraction((it / 100f).coerceIn(0.1f, 1f))
        }
        else -> parseSharedMarkdownDp(normalized)?.let(SharedMarkdownMediaWidth::DpValue)
    }
}

private fun parseSharedMarkdownDp(value: String): Int? = value.trim().lowercase()
    .removeSuffix("dp").removeSuffix("px").trim().toIntOrNull()?.takeIf { it > 0 }

private fun parseSharedMarkdownBoolean(value: String): Boolean? = when (value.trim().lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}

private fun parseSharedMarkdownMediaFit(value: String): SharedMarkdownMediaFit =
    if (value.trim().equals("cover", ignoreCase = true)) {
        SharedMarkdownMediaFit.Cover
    } else {
        SharedMarkdownMediaFit.Contain
    }

internal fun parseSharedMarkdownImage(text: String): SharedMarkdownImageSpec? {
    val match = sharedMarkdownImagePattern.matchEntire(text) ?: return null
    val normalizedUrl = normalizeSharedMarkdownImageUrl(match.groupValues[2]) ?: return null
    return SharedMarkdownImageSpec(
        altText = match.groupValues[1].trim(),
        url = normalizedUrl,
        layout = parseSharedMarkdownMediaLayout(
            attributes = parseSharedMarkdownAttributes(match.groupValues.getOrNull(3).orEmpty()),
            defaults = sharedDefaultImageLayout(),
        ),
    )
}

internal fun parseSharedMarkdownImageSequence(text: String): List<SharedMarkdownImageSpec>? {
    val images = mutableListOf<SharedMarkdownImageSpec>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && text[index].isWhitespace()) index++
        if (index >= text.length) break
        val token = parseSharedMarkdownImageToken(text, index) ?: return null
        images += token.image
        index = token.endExclusive
    }
    return images.takeIf { it.isNotEmpty() }
}

private data class SharedMarkdownImageToken(
    val image: SharedMarkdownImageSpec,
    val endExclusive: Int,
)

private fun parseSharedMarkdownImageToken(text: String, startIndex: Int): SharedMarkdownImageToken? {
    if (text.startsWith("[![", startIndex)) {
        val inner = parseSharedDirectMarkdownImageToken(text, startIndex + 1) ?: return null
        if (inner.endExclusive >= text.length || text[inner.endExclusive] != ']') return null
        val destinationStart = inner.endExclusive + 1
        if (destinationStart >= text.length || text[destinationStart] != '(') return null
        val outerEnd = findSharedMarkdownDestinationEnd(text, destinationStart) ?: return null
        return SharedMarkdownImageToken(inner.image, outerEnd + 1)
    }
    return parseSharedDirectMarkdownImageToken(text, startIndex)
}

private fun parseSharedDirectMarkdownImageToken(
    text: String,
    startIndex: Int,
): SharedMarkdownImageToken? {
    if (!text.startsWith("![", startIndex)) return null
    val destinationMarker = text.indexOf("](", startIndex + 2)
    if (destinationMarker < 0) return null
    val destinationStart = destinationMarker + 1
    val destinationEnd = findSharedMarkdownDestinationEnd(text, destinationStart) ?: return null
    var endExclusive = destinationEnd + 1
    if (endExclusive < text.length && text[endExclusive] == '{') {
        val attributesEnd = text.indexOf('}', endExclusive + 1)
        if (attributesEnd < 0) return null
        endExclusive = attributesEnd + 1
    }
    val image = parseSharedMarkdownImage(text.substring(startIndex, endExclusive)) ?: return null
    return SharedMarkdownImageToken(image, endExclusive)
}

private fun findSharedMarkdownDestinationEnd(text: String, openingParenthesis: Int): Int? {
    if (openingParenthesis !in text.indices || text[openingParenthesis] != '(') return null
    var nestedParentheses = 0
    var index = openingParenthesis + 1
    while (index < text.length) {
        val character = text[index]
        if (character == '\\' && index + 1 < text.length) {
            index += 2
            continue
        }
        when (character) {
            '(' -> nestedParentheses++
            ')' -> {
                if (nestedParentheses == 0) return index
                nestedParentheses--
            }
        }
        index++
    }
    return null
}

@Composable
internal fun SharedMarkdownMermaidBlock(segment: SharedMarkdownSegment.Mermaid) {
    val renderError = stringResource(Res.string.markdown_mermaid_error_render)
    val invalidSyntax = stringResource(Res.string.markdown_mermaid_error_invalid_syntax)
    var showPreview by remember(segment.code, segment.layout) { mutableStateOf(false) }
    SharedMarkdownMediaWidthContainer(segment.layout) { widthModifier ->
        SharedMarkdownMermaidHtml(
            html = remember(segment.code, segment.layout, renderError, invalidSyntax) {
                buildSharedMermaidHtml(segment.code, segment.layout, renderError, invalidSyntax)
            },
            layout = segment.layout,
            modifier = widthModifier.clip(RoundedCornerShape(18.dp)).background(AetherSurface),
            onTap = { showPreview = true },
        )
    }
    if (showPreview) {
        SharedMarkdownMermaidPreviewDialog(
            code = segment.code,
            renderError = renderError,
            invalidSyntax = invalidSyntax,
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun SharedMarkdownMediaWidthContainer(
    layout: SharedMarkdownMediaLayout,
    content: @Composable (Modifier) -> Unit,
) {
    val outer = if (layout.width is SharedMarkdownMediaWidth.DpValue) {
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Box(outer) {
        val width = when (val value = layout.width) {
            null -> Modifier.fillMaxWidth()
            is SharedMarkdownMediaWidth.Fraction -> Modifier.fillMaxWidth(value.value.coerceIn(0.1f, 1f))
            is SharedMarkdownMediaWidth.DpValue -> Modifier.width(value.value.dp)
        }
        content(width)
    }
}

@Composable
private fun SharedMarkdownMermaidHtml(
    html: String,
    layout: SharedMarkdownMediaLayout,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val minHeight = (layout.minHeightDp ?: SharedDefaultMermaidMinHeightDp).coerceAtLeast(1)
    val maxHeight = (layout.maxHeightDp ?: SharedDefaultMermaidMaxHeightDp).coerceAtLeast(minHeight)
    var measuredHeight by remember(html) { mutableIntStateOf(minHeight) }
    var measured by remember(html) { mutableStateOf(false) }
    val effectiveMinHeight = if (measured) 1 else minHeight
    val appliedHeight = when {
        layout.showAll -> measuredHeight.coerceAtLeast(effectiveMinHeight)
        layout.heightDp != null -> layout.heightDp.coerceAtLeast(effectiveMinHeight)
        else -> measuredHeight.coerceIn(effectiveMinHeight, maxHeight)
    }
    PlatformWebView(
        url = "",
        html = html,
        onMessage = { message ->
            when {
                message == "tap" -> onTap()
                message.startsWith("height:") -> message.substringAfter(':').toDoubleOrNull()?.let {
                    measuredHeight = it.toInt().coerceAtLeast(1)
                    measured = true
                }
            }
        },
        transparentBackground = true,
        scrollEnabled = layout.scroll && !layout.showAll,
        modifier = modifier.height(appliedHeight.dp),
    )
}

@Composable
private fun SharedMarkdownMermaidPreviewDialog(
    code: String,
    renderError: String,
    invalidSyntax: String,
    onDismiss: () -> Unit,
) {
    val layout = remember {
        SharedMarkdownMediaLayout(
            minHeightDp = SharedMermaidPreviewMinHeightDp,
            maxHeightDp = SharedMermaidPreviewMaxHeightDp,
            scroll = true,
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(28.dp)).background(AetherSurface.copy(alpha = 0.98f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.markdown_mermaid_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.common_close),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherPrimary,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
            SharedMarkdownMermaidHtml(
                html = remember(code, renderError, invalidSyntax) {
                    buildSharedMermaidHtml(code, layout, renderError, invalidSyntax)
                },
                layout = layout,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(AetherSurfaceHigh),
                onTap = {},
            )
        }
    }
}

internal fun buildSharedMermaidHtml(
    code: String,
    layout: SharedMarkdownMediaLayout,
    renderError: String,
    invalidSyntax: String,
): String {
    val maxWidth = if (layout.scroll) "none" else "100%"
    val containerWidth = if (layout.scroll) "display:inline-block;min-width:100%;" else ""
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                html,body{margin:0;padding:0;background:transparent;color:#111827;font-family:sans-serif;}
                #container{padding:12px;$containerWidth}
                svg{max-width:$maxWidth;height:auto;}
                pre{white-space:pre-wrap;font-family:monospace;background:#f5f5f5;border-radius:12px;padding:12px;}
                .mermaid-error{padding:12px;color:#1f2937;font-family:sans-serif;}
                .mermaid-error-title{font-size:15px;font-weight:600;margin-bottom:8px;}
                .mermaid-error-detail{color:#6b7280;font-size:13px;}
                .mermaid-source{margin:0 0 10px;}
            </style>
            <script src="$SharedMermaidScriptUrl"></script>
        </head>
        <body>
            <div id="container"><pre id="diagram" class="mermaid">${escapeSharedMermaidHtml(code)}</pre></div>
            <script>
                const renderErrorTitle=${sharedMermaidJsString(renderError)};
                const invalidSyntaxError=${sharedMermaidJsString(invalidSyntax)};
                function postAether(message){if(window.Aether&&window.Aether.postMessage){window.Aether.postMessage(message);}}
                function reportAetherHeight(){const height=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0,1);postAether('height:'+height);}
                function reportAetherTap(){postAether('tap');}
                function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
                function showMermaidError(source,detail){document.getElementById('container').innerHTML='<div class="mermaid-error"><div class="mermaid-error-title">'+escapeHtml(renderErrorTitle)+'</div><pre class="mermaid-source">'+escapeHtml(source)+'</pre><div class="mermaid-error-detail">'+escapeHtml(detail||invalidSyntaxError)+'</div></div>';}
                async function renderDiagram(){
                    const source=document.getElementById('diagram').textContent||'';
                    try{
                        if(!window.mermaid){throw new Error('Mermaid library failed to load.');}
                        mermaid.initialize({startOnLoad:false,securityLevel:'loose',theme:'neutral'});
                        await mermaid.parse(source,{suppressErrors:false});
                        const rendered=await mermaid.render('aether-mermaid-'+Date.now(),source);
                        if((rendered.svg||'').includes('class="error-icon"')||(rendered.svg||'').includes('Syntax error in text')){throw new Error(invalidSyntaxError);}
                        document.getElementById('container').innerHTML=rendered.svg;
                    }catch(error){showMermaidError(source,error&&error.message?error.message:invalidSyntaxError);}
                    document.getElementById('container').onclick=reportAetherTap;
                    setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);setTimeout(reportAetherHeight,360);
                }
                window.addEventListener('load',renderDiagram);
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeSharedMermaidHtml(value: String): String = buildString {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            }
        )
    }
}

private fun sharedMermaidJsString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '<' -> append("\\u003C")
            '>' -> append("\\u003E")
            '&' -> append("\\u0026")
            else -> append(character)
        }
    }
    append('"')
}
