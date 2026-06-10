package com.zhousl.aether.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import com.zhousl.aether.R
import com.zhousl.aether.data.WorkspaceFileBridge
import com.zhousl.aether.termux.TermuxContract
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val LinkAnnotationTag = "url"
private const val MaxMarkdownImageBytes = 8 * 1024 * 1024
private const val MarkdownHtmlBridgeName = "AetherMarkdownHtmlBridge"
private const val MermaidScriptUrl = "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"
private const val KatexCssUrl = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css"
private const val KatexScriptUrl = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"
private const val KatexAutoRenderScriptUrl = "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"
private const val MarkdownHtmlBaseUrl = "https://localhost/"
internal const val DefaultImageMinHeightDp = 160
internal const val DefaultImageMaxHeightDp = 420
internal const val DefaultMermaidMinHeightDp = 220
internal const val DefaultMermaidMaxHeightDp = 640
private const val DefaultTextBlockMinHeightDp = 24
private const val DefaultTextBlockMaxHeightDp = 2048
private const val PreviewDialogMinHeightDp = 320
private const val PreviewDialogMaxHeightDp = 820
private val MarkdownTableMinColumnWidth = 128.dp
private val MarkdownTableDescriptionMinColumnWidth = 160.dp
private val MarkdownTableScrollableColumnWidth = 148.dp
private val MarkdownImageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}

data class MarkdownFadeSpan(
    val sourceRange: IntRange,
    val alpha: Float,
)

internal data class MarkdownSourceText(
    val text: String,
    val sourceOffset: Int,
)

private data class MarkdownLine(
    val text: String,
    val startOffset: Int,
)

internal data class MarkdownImageSpec(
    val altText: String,
    val url: String,
    val layout: MarkdownMediaLayout = defaultMarkdownImageLayout(),
)

private data class MarkdownImageLoadResult(
    val bitmap: ImageBitmap? = null,
    val html: String? = null,
    val error: String? = null,
)

private data class MarkdownAutoLinkMatch(
    val displayText: String,
    val targetUrl: String,
)

private data class MarkdownImageBinary(
    val bytes: ByteArray,
    val mimeType: String? = null,
)

internal data class MarkdownMediaLayout(
    val width: MarkdownMediaWidth? = null,
    val heightDp: Int? = null,
    val minHeightDp: Int? = null,
    val maxHeightDp: Int? = null,
    val fit: MarkdownMediaFit = MarkdownMediaFit.Contain,
    val scroll: Boolean = false,
    val showAll: Boolean = false,
)

internal sealed interface MarkdownMediaWidth {
    data class Fraction(val value: Float) : MarkdownMediaWidth
    data class DpValue(val value: Int) : MarkdownMediaWidth
}

internal enum class MarkdownMediaFit {
    Contain,
    Cover,
}

internal data class MarkdownMermaidSpec(
    val code: MarkdownSourceText,
    val layout: MarkdownMediaLayout = defaultMarkdownMermaidLayout(),
)

internal data class MarkdownCodeFenceHeader(
    val language: String,
    val attributes: Map<String, String>,
)

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = AetherOnSurface,
    workspaceDirectory: String? = null,
    allowRootImageRead: Boolean = false,
    onLinkClick: (String) -> Unit = {},
    fadeSpan: MarkdownFadeSpan? = null,
) {
    val normalizedMarkdown = remember(markdown) { markdown.replace("\r\n", "\n") }
    val blocks = remember(normalizedMarkdown) { parseMarkdown(normalizedMarkdown) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> {
                        MarkdownParagraph(block.text, color, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.Heading -> {
                        MarkdownHeading(block.level, block.text, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.UnorderedList -> {
                        MarkdownBullets(block.items, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.OrderedList -> {
                        MarkdownNumbers(block.items, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.Quote -> {
                        MarkdownQuote(block.text, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.Table -> {
                        MarkdownTable(block.headers, block.rows, onLinkClick, fadeSpan)
                    }

                    is MarkdownBlock.CodeFence -> {
                        MarkdownCodeFence(block.code, fadeSpan)
                    }

                    is MarkdownBlock.Image -> MarkdownImageBlock(
                        image = block.image,
                        workspaceDirectory = workspaceDirectory,
                        allowRootImageRead = allowRootImageRead,
                        onLinkClick = onLinkClick,
                    )

                    is MarkdownBlock.Mermaid -> MarkdownMermaidBlock(block.diagram)
                    MarkdownBlock.Rule -> HorizontalDivider(color = AetherOutlineSoft)
                }
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(
    text: MarkdownSourceText,
    color: Color,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    MarkdownRichTextBlock(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        onLinkClick = onLinkClick,
        variant = MarkdownHtmlTextVariant.Paragraph,
        fadeSpan = fadeSpan,
    )
}

@Composable
private fun MarkdownHeading(
    level: Int,
    text: MarkdownSourceText,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.labelLarge
    }

    MarkdownRichTextBlock(
        text = text,
        style = style,
        color = AetherOnSurface,
        onLinkClick = onLinkClick,
        variant = MarkdownHtmlTextVariant.Heading(level),
        fadeSpan = fadeSpan,
    )
}

@Composable
private fun MarkdownBullets(
    items: List<MarkdownSourceText>,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                )
                Spacer(modifier = Modifier.width(10.dp))
                MarkdownRichTextBlock(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    variant = MarkdownHtmlTextVariant.ListItem,
                    fadeSpan = fadeSpan,
                )
            }
        }
    }
}

@Composable
private fun MarkdownNumbers(
    items: List<MarkdownSourceText>,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                )
                Spacer(modifier = Modifier.width(10.dp))
                MarkdownRichTextBlock(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    variant = MarkdownHtmlTextVariant.ListItem,
                    fadeSpan = fadeSpan,
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(
    text: MarkdownSourceText,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(AetherOutlineSoft, RoundedCornerShape(999.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        MarkdownRichTextBlock(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
            modifier = Modifier.weight(1f),
            onLinkClick = onLinkClick,
            variant = MarkdownHtmlTextVariant.Quote,
            fadeSpan = fadeSpan,
        )
    }
}

@Composable
private fun MarkdownTable(
    headers: List<MarkdownSourceText>,
    rows: List<List<MarkdownSourceText>>,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
) {
    val columnCount = remember(headers, rows) {
        maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidths = remember(columnCount, maxWidth) {
            markdownTableColumnWidths(columnCount, maxWidth)
        }
        val tableWidth = columnWidths.fold(0.dp) { width, columnWidth -> width + columnWidth }
            .coerceAtLeast(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(18.dp))
                .background(AetherSurface.copy(alpha = 0.92f)),
        ) {
            MarkdownTableRow(
                cells = headers,
                columnWidths = columnWidths,
                tableWidth = tableWidth,
                onLinkClick = onLinkClick,
                fadeSpan = fadeSpan,
                isHeader = true,
            )
            rows.forEachIndexed { index, row ->
                MarkdownTableRow(
                    cells = row,
                    columnWidths = columnWidths,
                    tableWidth = tableWidth,
                    onLinkClick = onLinkClick,
                    fadeSpan = fadeSpan,
                    isHeader = false,
                    shaded = index % 2 == 1,
                )
            }
        }
    }
}

internal fun markdownTableColumnWidths(
    columnCount: Int,
    viewportWidth: Dp,
): List<Dp> {
    val normalizedColumnCount = columnCount.coerceAtLeast(1)
    return when {
        normalizedColumnCount == 1 -> listOf(viewportWidth.coerceAtLeast(MarkdownTableMinColumnWidth))
        normalizedColumnCount == 2 -> {
            val firstColumn = (viewportWidth * 0.42f).coerceAtLeast(MarkdownTableMinColumnWidth)
            val secondColumn = (viewportWidth - firstColumn)
                .coerceAtLeast(MarkdownTableDescriptionMinColumnWidth)
            listOf(firstColumn, secondColumn)
        }
        normalizedColumnCount == 3 -> {
            val columnWidth = (viewportWidth / normalizedColumnCount)
                .coerceAtLeast(MarkdownTableMinColumnWidth)
            List(normalizedColumnCount) { columnWidth }
        }
        else -> List(normalizedColumnCount) { MarkdownTableScrollableColumnWidth }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<MarkdownSourceText>,
    columnWidths: List<Dp>,
    tableWidth: Dp,
    onLinkClick: (String) -> Unit,
    fadeSpan: MarkdownFadeSpan?,
    isHeader: Boolean,
    shaded: Boolean = false,
) {
    Row(
        modifier = Modifier
            .width(tableWidth)
            .background(
                when {
                    isHeader -> AetherSurfaceHigh
                    shaded -> AetherSurface.copy(alpha = 0.68f)
                    else -> Color.Transparent
                }
            )
    ) {
        columnWidths.forEachIndexed { index, columnWidth ->
            val cell = cells.getOrNull(index) ?: MarkdownSourceText("", 0)
            Box(
                modifier = Modifier
                    .width(columnWidth)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                MarkdownRichTextBlock(
                    text = cell,
                    style = if (isHeader) {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = AetherOnSurface,
                    modifier = Modifier.heightIn(min = 20.dp),
                    onLinkClick = onLinkClick,
                    variant = MarkdownHtmlTextVariant.TableCell(isHeader = isHeader),
                    fadeSpan = fadeSpan,
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeFence(
    code: MarkdownSourceText,
    fadeSpan: MarkdownFadeSpan?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AetherSurfaceHigh, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = plainMarkdownText(code.text, code.sourceOffset, fadeSpan),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun MarkdownImageBlock(
    image: MarkdownImageSpec,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean,
    onLinkClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val workspaceFileBridge = remember(context) { WorkspaceFileBridge(context) }
    val resolvedUrl = remember(image.url) { normalizeMarkdownImageUrl(image.url).orEmpty() }
    val originalLinkTarget = remember(resolvedUrl, workspaceDirectory) {
        buildMarkdownImageOriginalLinkTarget(
            rawUrl = resolvedUrl,
            workspaceFileBridge = workspaceFileBridge,
            workspaceDirectory = workspaceDirectory,
        )
    }
    val imageState by produceState(
        initialValue = MarkdownImageLoadResult(),
        key1 = resolvedUrl,
        key2 = workspaceDirectory,
        key3 = allowRootImageRead,
    ) {
        value = withContext(Dispatchers.IO) {
            loadMarkdownImage(
                context = context,
                workspaceFileBridge = workspaceFileBridge,
                rawUrl = resolvedUrl,
                workspaceDirectory = workspaceDirectory,
                allowRootImageRead = allowRootImageRead,
            )
        }
    }
    var showPreview by remember(resolvedUrl) { mutableStateOf(false) }
    val canPreview = imageState.bitmap != null || imageState.html != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkdownMediaWidthContainer(layout = image.layout) { widthModifier ->
            when {
                imageState.html != null -> MarkdownHtmlBlock(
                    html = imageState.html!!,
                    layout = image.layout,
                    defaultMinHeightDp = DefaultImageMinHeightDp,
                    defaultMaxHeightDp = DefaultImageMaxHeightDp,
                    modifier = widthModifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(AetherSurfaceHigh),
                    backgroundColor = AetherSurfaceHigh,
                    onTap = if (canPreview) {
                        { showPreview = true }
                    } else {
                        null
                    },
                )

                imageState.bitmap != null -> MarkdownBitmapImageBlock(
                    bitmap = imageState.bitmap!!,
                    altText = image.altText,
                    layout = image.layout,
                    modifier = widthModifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(AetherSurfaceHigh)
                        .clickable { showPreview = true },
                )

                else -> Box(
                    modifier = widthModifier
                        .heightIn(
                            min = (image.layout.minHeightDp ?: DefaultImageMinHeightDp).dp,
                            max = (image.layout.maxHeightDp ?: DefaultImageMaxHeightDp).dp,
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(AetherSurfaceHigh)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageState.error != null) {
                        Text(
                            text = imageState.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        if (image.altText.isNotBlank()) {
            Text(
                text = image.altText,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        if (!originalLinkTarget.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.markdown_open_original),
                style = MaterialTheme.typography.bodySmall,
                color = AetherPrimary,
                modifier = Modifier.clickable { onLinkClick(originalLinkTarget) },
            )
        }
    }

    if (showPreview && canPreview) {
        MarkdownImagePreviewDialog(
            altText = image.altText,
            imageState = imageState,
            originalLinkTarget = originalLinkTarget,
            onDismiss = { showPreview = false },
            onOpenLink = onLinkClick,
        )
    }
}

@Composable
private fun MarkdownMermaidBlock(
    diagram: MarkdownMermaidSpec,
) {
    val renderErrorTitle = stringResource(R.string.markdown_mermaid_error_render)
    val invalidSyntaxError = stringResource(R.string.markdown_mermaid_error_invalid_syntax)
    val previewLayout = remember(diagram.layout) {
        diagram.layout.copy(
            heightDp = null,
            minHeightDp = PreviewDialogMinHeightDp,
            maxHeightDp = PreviewDialogMaxHeightDp,
            scroll = true,
            showAll = false,
        )
    }
    var showPreview by remember(diagram.code.text, diagram.layout) { mutableStateOf(false) }
    MarkdownMediaWidthContainer(layout = diagram.layout) { widthModifier ->
        MarkdownHtmlBlock(
            html = remember(diagram.code.text, diagram.layout) {
                buildMermaidHtml(
                    code = diagram.code.text,
                    layout = diagram.layout,
                    renderErrorTitle = renderErrorTitle,
                    invalidSyntaxError = invalidSyntaxError,
                )
            },
            layout = diagram.layout,
            defaultMinHeightDp = DefaultMermaidMinHeightDp,
            defaultMaxHeightDp = DefaultMermaidMaxHeightDp,
            modifier = widthModifier
                .clip(RoundedCornerShape(18.dp))
                .background(AetherSurface),
            onTap = { showPreview = true },
        )
    }

    if (showPreview) {
        MarkdownMermaidPreviewDialog(
            html = remember(diagram.code.text, previewLayout) {
                buildMermaidHtml(
                    code = diagram.code.text,
                    layout = previewLayout,
                    renderErrorTitle = renderErrorTitle,
                    invalidSyntaxError = invalidSyntaxError,
                )
            },
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun MarkdownHtmlBlock(
    html: String,
    layout: MarkdownMediaLayout,
    defaultMinHeightDp: Int,
    defaultMaxHeightDp: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AetherSurface,
    onTap: (() -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val resolvedMinHeightDp = layout.minHeightDp ?: defaultMinHeightDp
    val resolvedMaxHeightDp = layout.maxHeightDp ?: defaultMaxHeightDp
    val scrollViewportHeightDp = if (layout.scroll) {
        (layout.heightDp ?: resolvedMaxHeightDp).coerceAtLeast(1)
    } else {
        null
    }
    var measuredHeightDp by remember(html) { mutableIntStateOf(resolvedMinHeightDp.coerceAtLeast(1)) }
    var hasMeasuredContent by remember(html) { mutableStateOf(false) }
    val effectiveMinHeightDp = if (hasMeasuredContent) 1 else resolvedMinHeightDp.coerceAtLeast(1)
    val appliedHeightDp = when {
        layout.showAll -> measuredHeightDp.coerceAtLeast(effectiveMinHeightDp)
        layout.heightDp != null -> layout.heightDp.coerceAtLeast(effectiveMinHeightDp)
        scrollViewportHeightDp != null -> measuredHeightDp.coerceIn(
            effectiveMinHeightDp,
            scrollViewportHeightDp,
        )
        else -> measuredHeightDp.coerceIn(
            effectiveMinHeightDp,
            resolvedMaxHeightDp.coerceAtLeast(effectiveMinHeightDp),
        )
    }

    AndroidView(
        modifier = modifier.height(appliedHeightDp.dp),
        factory = { context ->
            val gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                        onTap?.invoke()
                        return false
                    }
                },
            )
            WebView(context).apply {
                setBackgroundColor(backgroundColor.toArgb())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                isVerticalScrollBarEnabled = layout.scroll && !layout.showAll
                isHorizontalScrollBarEnabled = layout.scroll && !layout.showAll
                isFocusable = true
                isFocusableInTouchMode = true
                ViewCompat.setNestedScrollingEnabled(this, layout.scroll && !layout.showAll)
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    MarkdownHtmlBridge(
                        onHeightMeasured = { measuredHeight ->
                            measuredHeightDp = measuredHeight.coerceAtLeast(1)
                            hasMeasuredContent = true
                        },
                        onTap = onTap,
                        onLinkClick = onLinkClick,
                    ),
                    MarkdownHtmlBridgeName,
                )
                setOnTouchListener { view, event ->
                    if (layout.scroll && !layout.showAll) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    gestureDetector.onTouchEvent(event)
                    false
                }
                setOnClickListener { onTap?.invoke() }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val targetUrl = request?.url?.toString().orEmpty()
                        if (targetUrl.isBlank()) return false
                        onLinkClick?.invoke(targetUrl)
                        return true
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("window.reportAetherHeight && window.reportAetherHeight();", null)
                    }
                }
            }
        },
        update = { webView ->
            webView.isVerticalScrollBarEnabled = layout.scroll && !layout.showAll
            webView.isHorizontalScrollBarEnabled = layout.scroll && !layout.showAll
            ViewCompat.setNestedScrollingEnabled(webView, layout.scroll && !layout.showAll)
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    MarkdownHtmlBaseUrl,
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

@Composable
private fun MarkdownBitmapImageBlock(
    bitmap: ImageBitmap,
    altText: String,
    layout: MarkdownMediaLayout,
    modifier: Modifier = Modifier,
) {
    val contentScale = if (layout.fit == MarkdownMediaFit.Cover) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    BoxWithConstraints(modifier = modifier) {
        val resolvedMaxHeight = (layout.maxHeightDp ?: DefaultImageMaxHeightDp).dp
        val explicitHeight = layout.heightDp?.dp
        val naturalHeight = if (bitmap.width > 0 && bitmap.height > 0) {
            maxWidth * (bitmap.height.toFloat() / bitmap.width.toFloat())
        } else {
            resolvedMaxHeight
        }
        val containerHeight = when {
            layout.showAll -> naturalHeight.coerceAtLeast(1.dp)
            explicitHeight != null -> explicitHeight
            else -> naturalHeight.coerceAtMost(resolvedMaxHeight).coerceAtLeast(1.dp)
        }
        val needsVerticalScroll = !layout.showAll && layout.scroll && naturalHeight > containerHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .padding(12.dp),
            contentAlignment = if (needsVerticalScroll) Alignment.TopCenter else Alignment.Center,
        ) {
            when {
                needsVerticalScroll -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = altText.takeIf { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = contentScale,
                    )
                }

                explicitHeight != null || (!layout.showAll && naturalHeight > containerHeight) -> Image(
                    bitmap = bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )

                else -> Image(
                    bitmap = bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

@Composable
private fun MarkdownMediaWidthContainer(
    layout: MarkdownMediaLayout,
    content: @Composable (Modifier) -> Unit,
) {
    val outerModifier = if (layout.width is MarkdownMediaWidth.DpValue) {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = outerModifier) {
        content(Modifier.markdownMediaWidth(layout.width))
    }
}

private fun Modifier.markdownMediaWidth(
    mediaWidth: MarkdownMediaWidth?,
): Modifier = when (mediaWidth) {
    null -> fillMaxWidth()
    is MarkdownMediaWidth.Fraction -> fillMaxWidth(mediaWidth.value.coerceIn(0.1f, 1f))
    is MarkdownMediaWidth.DpValue -> width(mediaWidth.value.dp)
}

@Composable
private fun MarkdownImagePreviewDialog(
    altText: String,
    imageState: MarkdownImageLoadResult,
    originalLinkTarget: String?,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val fallbackTitle = stringResource(R.string.markdown_image_preview_title)
    MarkdownPreviewDialogFrame(
        title = altText.ifBlank { fallbackTitle },
        onDismiss = onDismiss,
    ) {
        when {
            imageState.bitmap != null -> MarkdownBitmapPreviewBlock(
                bitmap = imageState.bitmap,
                altText = altText,
                maxHeightDp = PreviewDialogMaxHeightDp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AetherSurfaceHigh),
            )

            imageState.html != null -> MarkdownHtmlBlock(
                html = imageState.html,
                layout = MarkdownMediaLayout(
                    maxHeightDp = PreviewDialogMaxHeightDp,
                    scroll = true,
                    showAll = false,
                ),
                defaultMinHeightDp = 1,
                defaultMaxHeightDp = PreviewDialogMaxHeightDp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AetherSurfaceHigh),
                backgroundColor = AetherSurfaceHigh,
            )

            else -> Text(
                text = imageState.error ?: stringResource(R.string.markdown_preview_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }

        if (!originalLinkTarget.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.markdown_open_original),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherPrimary,
                modifier = Modifier.clickable { onOpenLink(originalLinkTarget) },
            )
        }
    }
}

@Composable
private fun MarkdownMermaidPreviewDialog(
    html: String,
    onDismiss: () -> Unit,
) {
    MarkdownPreviewDialogFrame(
        title = stringResource(R.string.markdown_mermaid_preview_title),
        onDismiss = onDismiss,
    ) {
        MarkdownHtmlBlock(
            html = html,
            layout = MarkdownMediaLayout(
                minHeightDp = PreviewDialogMinHeightDp,
                maxHeightDp = PreviewDialogMaxHeightDp,
                scroll = true,
                showAll = false,
            ),
            defaultMinHeightDp = PreviewDialogMinHeightDp,
            defaultMaxHeightDp = PreviewDialogMaxHeightDp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
            backgroundColor = Color.White,
        )
    }
}

@Composable
private fun MarkdownPreviewDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.98f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.common_close),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherPrimary,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
            content()
        }
    }
}

@Composable
private fun MarkdownBitmapPreviewBlock(
    bitmap: ImageBitmap,
    altText: String,
    maxHeightDp: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val maxHeight = maxHeightDp.dp
        val naturalHeight = if (bitmap.width > 0 && bitmap.height > 0) {
            maxWidth * (bitmap.height.toFloat() / bitmap.width.toFloat())
        } else {
            maxHeight
        }
        val containerHeight = naturalHeight.coerceAtMost(maxHeight).coerceAtLeast(1.dp)
        val needsVerticalScroll = naturalHeight > containerHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .padding(12.dp),
            contentAlignment = if (needsVerticalScroll) Alignment.TopCenter else Alignment.Center,
        ) {
            if (needsVerticalScroll) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = altText.takeIf { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private sealed interface MarkdownHtmlTextVariant {
    data object Paragraph : MarkdownHtmlTextVariant
    data class Heading(val level: Int) : MarkdownHtmlTextVariant
    data object ListItem : MarkdownHtmlTextVariant
    data object Quote : MarkdownHtmlTextVariant
    data class TableCell(val isHeader: Boolean) : MarkdownHtmlTextVariant
}

@Composable
private fun MarkdownRichTextBlock(
    text: MarkdownSourceText,
    style: TextStyle,
    color: Color,
    onLinkClick: (String) -> Unit,
    variant: MarkdownHtmlTextVariant,
    fadeSpan: MarkdownFadeSpan?,
    modifier: Modifier = Modifier,
) {
    val shouldUseMathHtml = remember(text.text) {
        containsRenderableMarkdownMath(text.text)
    }
    if (shouldUseMathHtml) {
        MarkdownTextHtmlBlock(
            text = text.text,
            variant = variant,
            modifier = modifier,
            onLinkClick = onLinkClick,
        )
        return
    }

    MarkdownText(
        text = inlineMarkdown(text.text, text.sourceOffset, fadeSpan),
        style = style,
        color = color,
        modifier = modifier,
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun MarkdownTextHtmlBlock(
    text: String,
    variant: MarkdownHtmlTextVariant,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val html = remember(text, variant) {
        buildMarkdownTextHtml(
            text = text,
            variant = variant,
        )
    }
    MarkdownHtmlBlock(
        html = html,
        layout = MarkdownMediaLayout(showAll = true),
        defaultMinHeightDp = DefaultTextBlockMinHeightDp,
        defaultMaxHeightDp = DefaultTextBlockMaxHeightDp,
        modifier = modifier.fillMaxWidth(),
        backgroundColor = Color.Transparent,
        onLinkClick = onLinkClick,
    )
}

@Composable
private fun MarkdownText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val hasLinks = text.getStringAnnotations(
        tag = LinkAnnotationTag,
        start = 0,
        end = text.length,
    ).isNotEmpty()

    if (!hasLinks) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier,
        )
        return
    }

    ClickableText(
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
    ) { offset ->
        text.getStringAnnotations(
            tag = LinkAnnotationTag,
            start = offset,
            end = offset,
        ).firstOrNull()?.let { annotation ->
            onLinkClick(annotation.item)
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: MarkdownSourceText) : MarkdownBlock
    data class Heading(val level: Int, val text: MarkdownSourceText) : MarkdownBlock
    data class UnorderedList(val items: List<MarkdownSourceText>) : MarkdownBlock
    data class OrderedList(val items: List<MarkdownSourceText>) : MarkdownBlock
    data class Quote(val text: MarkdownSourceText) : MarkdownBlock
    data class Image(val image: MarkdownImageSpec) : MarkdownBlock
    data class Mermaid(val diagram: MarkdownMermaidSpec) : MarkdownBlock
    data class Table(
        val headers: List<MarkdownSourceText>,
        val rows: List<List<MarkdownSourceText>>,
    ) : MarkdownBlock
    data class CodeFence(val code: MarkdownSourceText) : MarkdownBlock
    data object Rule : MarkdownBlock
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = splitMarkdownLines(markdown)
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.text.trim()

        if (trimmed.isBlank()) {
            index++
            continue
        }

        if (trimmed.startsWith("```")) {
            val fenceHeader = parseMarkdownCodeFenceHeader(trimmed)
            val fenceLanguage = fenceHeader.language.lowercase()
            index++
            val codeStartOffset = if (index < lines.size) lines[index].startOffset else line.startOffset + line.text.length
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].text.trim().startsWith("```")) {
                codeLines += lines[index].text
                index++
            }
            if (index < lines.size) index++
            val code = MarkdownSourceText(
                text = codeLines.joinToString("\n"),
                sourceOffset = codeStartOffset,
            )
            blocks += if (fenceLanguage == "mermaid") {
                MarkdownBlock.Mermaid(
                    MarkdownMermaidSpec(
                        code = code,
                        layout = parseMarkdownMediaLayout(
                            attributes = fenceHeader.attributes,
                            defaults = defaultMarkdownMermaidLayout(),
                        ),
                    )
                )
            } else {
                MarkdownBlock.CodeFence(code)
            }
            continue
        }

        val headingMatch = headingPattern.matchEntire(trimmed)
        if (headingMatch != null) {
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = MarkdownSourceText(
                    text = headingMatch.groupValues[2].trim(),
                    sourceOffset = contentStartOffset(
                        line = line,
                        trimmedLine = trimmed,
                        markerLength = headingMatch.groupValues[1].length,
                    ),
                ),
            )
            index++
            continue
        }

        if (trimmed == "---" || trimmed == "***") {
            blocks += MarkdownBlock.Rule
            index++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            val quoteStartOffset = contentStartOffset(
                line = line,
                trimmedLine = trimmed,
                markerLength = 1,
            )
            while (index < lines.size && lines[index].text.trim().startsWith(">")) {
                quoteLines += lines[index].text.trim().removePrefix(">").trimStart()
                index++
            }
            blocks += MarkdownBlock.Quote(
                MarkdownSourceText(
                    text = quoteLines.joinToString("\n"),
                    sourceOffset = quoteStartOffset,
                )
            )
            continue
        }

        if (looksLikeMarkdownTable(lines, index)) {
            val headerLine = lines[index]
            val headerCells = parseMarkdownTableCells(headerLine)
            val columnCount = headerCells.size
            index += 2

            val rows = mutableListOf<List<MarkdownSourceText>>()
            while (index < lines.size) {
                val candidate = lines[index]
                if (candidate.text.trim().isBlank() || !looksLikeMarkdownTableDataRow(candidate.text, columnCount)) {
                    break
                }
                rows += normalizeMarkdownTableRow(
                    cells = parseMarkdownTableCells(candidate),
                    columnCount = columnCount,
                    line = candidate,
                )
                index++
            }

            blocks += MarkdownBlock.Table(
                headers = normalizeMarkdownTableRow(
                    cells = headerCells,
                    columnCount = columnCount,
                    line = headerLine,
                ),
                rows = rows,
            )
            continue
        }

        if (unorderedPattern.matches(trimmed)) {
            val items = mutableListOf<MarkdownSourceText>()
            while (index < lines.size) {
                val candidate = lines[index]
                val match = unorderedPattern.matchEntire(candidate.text.trim())
                if (match == null) break
                items += MarkdownSourceText(
                    text = match.groupValues[1],
                    sourceOffset = contentStartOffset(
                        line = candidate,
                        trimmedLine = candidate.text.trim(),
                        markerLength = 1,
                    ),
                )
                index++
            }
            blocks += MarkdownBlock.UnorderedList(items)
            continue
        }

        if (orderedPattern.matches(trimmed)) {
            val items = mutableListOf<MarkdownSourceText>()
            while (index < lines.size) {
                val candidate = lines[index]
                val candidateTrimmed = candidate.text.trim()
                val match = orderedPattern.matchEntire(candidateTrimmed)
                if (match == null) break
                items += MarkdownSourceText(
                    text = match.groupValues[2],
                    sourceOffset = contentStartOffset(
                        line = candidate,
                        trimmedLine = candidateTrimmed,
                        markerLength = match.groupValues[1].length + 1,
                    ),
                )
                index++
            }
            blocks += MarkdownBlock.OrderedList(items)
            continue
        }

        val image = parseMarkdownImage(trimmed)
        if (image != null) {
            blocks += MarkdownBlock.Image(image)
            index++
            continue
        }

        val paragraphLines = mutableListOf<String>()
        val paragraphStartOffset = line.startOffset
        while (index < lines.size) {
            val candidate = lines[index].text
            if (candidate.trim().isBlank() || beginsSpecialBlock(lines, index)) {
                break
            }
            paragraphLines += candidate.trimEnd()
            index++
        }
        if (paragraphLines.isEmpty()) {
            paragraphLines += line.text.trimEnd()
            index++
        }
        blocks += MarkdownBlock.Paragraph(
            MarkdownSourceText(
                text = paragraphLines.joinToString("\n"),
                sourceOffset = paragraphStartOffset,
            )
        )
    }

    return blocks
}

private fun splitMarkdownLines(markdown: String): List<MarkdownLine> {
    val lines = mutableListOf<MarkdownLine>()
    var start = 0
    for (index in 0..markdown.length) {
        if (index == markdown.length || markdown[index] == '\n') {
            lines += MarkdownLine(
                text = markdown.substring(start, index),
                startOffset = start,
            )
            start = index + 1
        }
    }
    return lines
}

private fun contentStartOffset(
    line: MarkdownLine,
    trimmedLine: String,
    markerLength: Int,
): Int {
    val trimmedOffset = line.text.indexOf(trimmedLine).coerceAtLeast(0)
    var contentOffset = line.startOffset + trimmedOffset + markerLength
    val lineEndOffset = line.startOffset + line.text.length
    while (contentOffset < lineEndOffset && line.text[contentOffset - line.startOffset].isWhitespace()) {
        contentOffset++
    }
    return contentOffset
}

private fun beginsSpecialBlock(
    lines: List<MarkdownLine>,
    index: Int,
): Boolean {
    val line = lines.getOrNull(index)?.text ?: return false
    val trimmed = line.trim()
    return trimmed.startsWith("```") ||
        looksLikeMarkdownImageLine(trimmed) ||
        looksLikeMarkdownTable(lines, index) ||
        headingPattern.matches(trimmed) ||
        unorderedPattern.matches(trimmed) ||
        orderedPattern.matches(trimmed) ||
        trimmed.startsWith(">") ||
        trimmed == "---" ||
        trimmed == "***"
}

private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
private val unorderedPattern = Regex("^[-*+]\\s+(.+)$")
private val orderedPattern = Regex("^(\\d+)[.)]\\s+(.+)$")
private val markdownTableSeparatorPattern = Regex("^:?-{3,}:?$")
private val autoLinkPattern = Regex("""^(https?://\S+|www\.\S+)""")

private fun looksLikeMarkdownTable(
    lines: List<MarkdownLine>,
    index: Int,
): Boolean {
    if (index + 1 >= lines.size) return false
    val headerCells = parseMarkdownTableCells(lines[index])
    if (headerCells.size < 2) return false
    return isMarkdownTableSeparator(
        line = lines[index + 1].text,
        expectedColumns = headerCells.size,
    )
}

private fun looksLikeMarkdownTableLine(line: String): Boolean =
    line.count { it == '|' } >= 1

private fun looksLikeMarkdownImageLine(line: String): Boolean =
    parseMarkdownImage(line) != null

private fun looksLikeMarkdownTableDataRow(
    line: String,
    expectedColumns: Int,
): Boolean {
    if (!looksLikeMarkdownTableLine(line)) return false
    return splitMarkdownTableCells(line).size == expectedColumns
}

private fun isMarkdownTableSeparator(
    line: String,
    expectedColumns: Int,
): Boolean {
    val cells = splitMarkdownTableCells(line)
    if (cells.size != expectedColumns) return false
    return cells.all { markdownTableSeparatorPattern.matches(it.trim()) }
}

private fun parseMarkdownTableCells(line: MarkdownLine): List<MarkdownSourceText> =
    splitMarkdownTableCellsWithOffsets(line.text).map { (cellText, startOffset) ->
        val trimmedCell = cellText.trim()
        val leadingWhitespace = cellText.indexOfFirst { !it.isWhitespace() }
            .let { if (it < 0) cellText.length else it }
        MarkdownSourceText(
            text = trimmedCell,
            sourceOffset = line.startOffset + startOffset + leadingWhitespace,
        )
    }

private fun normalizeMarkdownTableRow(
    cells: List<MarkdownSourceText>,
    columnCount: Int,
    line: MarkdownLine,
): List<MarkdownSourceText> {
    if (cells.size >= columnCount) return cells.take(columnCount)
    val trailingOffset = line.startOffset + line.text.length
    return cells + List(columnCount - cells.size) {
        MarkdownSourceText(text = "", sourceOffset = trailingOffset)
    }
}

private fun splitMarkdownTableCells(line: String): List<String> =
    splitMarkdownTableCellsWithOffsets(line).map { it.first }

private fun splitMarkdownTableCellsWithOffsets(line: String): List<Pair<String, Int>> {
    if ('|' !in line) return emptyList()

    val pipeIndices = line.indices.filter { line[it] == '|' }
    val cells = mutableListOf<Pair<String, Int>>()
    var segmentStart = 0

    if (pipeIndices.isNotEmpty() && line.substring(0, pipeIndices.first()).isBlank()) {
        segmentStart = pipeIndices.first() + 1
    }

    pipeIndices.forEach { pipeIndex ->
        if (pipeIndex < segmentStart) return@forEach
        val cellText = line.substring(segmentStart, pipeIndex)
        val isTrailingEmptyCell = pipeIndex == line.lastIndex && cellText.isBlank()
        if (!isTrailingEmptyCell) {
            cells += cellText to segmentStart
        }
        segmentStart = pipeIndex + 1
    }

    if (segmentStart <= line.length) {
        val tail = line.substring(segmentStart)
        val hasExplicitTrailingPipe = line.trimEnd().endsWith("|")
        if (!(hasExplicitTrailingPipe && tail.isBlank())) {
            cells += tail to segmentStart
        }
    }

    return cells
}

private data class MarkdownInlineLinkMatch(
    val label: String,
    val destination: String,
    val endExclusive: Int,
)

private data class MarkdownMathMatch(
    val rawText: String,
    val endExclusive: Int,
)

private fun buildMarkdownTextHtml(
    text: String,
    variant: MarkdownHtmlTextVariant,
): String {
    val contentHtml = inlineMarkdownToHtml(text)
    val textColor = AetherOnSurface.toCssHex()
    val linkColor = AetherPrimary.toCssHex()
    val codeBackgroundColor = AetherSurfaceHigh.toCssHex()
    val variantCss = buildMarkdownTextVariantCss(variant)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <link rel="stylesheet" href="$KatexCssUrl" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                }
                body {
                    color: $textColor;
                    font-family: sans-serif;
                }
                .aether-text {
                    $variantCss
                    white-space: pre-wrap;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }
                .aether-text a {
                    color: $linkColor;
                    text-decoration: none;
                }
                .aether-text strong {
                    font-weight: 600;
                }
                .aether-text em {
                    font-style: italic;
                }
                .aether-text code {
                    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                    font-size: 0.92em;
                    background: $codeBackgroundColor;
                    border-radius: 8px;
                    padding: 0.08em 0.34em;
                }
                .aether-text .katex {
                    white-space: normal;
                }
                .aether-text .katex-display {
                    margin: 0.45em 0;
                    overflow-x: auto;
                    overflow-y: hidden;
                    padding-bottom: 2px;
                }
                .aether-text .katex-display > .katex {
                    white-space: nowrap;
                }
            </style>
            <script defer src="$KatexScriptUrl"></script>
            <script defer src="$KatexAutoRenderScriptUrl"></script>
        </head>
        <body>
            <div class="aether-text">$contentHtml</div>
            <script>
                function reportAetherHeight() {
                    const height = Math.max(
                        document.documentElement.scrollHeight || 0,
                        document.body.scrollHeight || 0,
                        $DefaultTextBlockMinHeightDp
                    );
                    if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportHeight) {
                        window.$MarkdownHtmlBridgeName.reportHeight(String(height));
                    }
                }

                function bindAetherLinks() {
                    const anchors = document.querySelectorAll('a[href]');
                    anchors.forEach(function(anchor) {
                        anchor.onclick = function(event) {
                            event.preventDefault();
                            const href = anchor.getAttribute('href');
                            if (href && window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportLink) {
                                window.$MarkdownHtmlBridgeName.reportLink(href);
                            }
                            return false;
                        };
                    });
                }

                function renderAetherMath() {
                    bindAetherLinks();
                    try {
                        if (window.renderMathInElement) {
                            window.renderMathInElement(document.body, {
                                delimiters: [
                                    { left: '$$', right: '$$', display: true },
                                    { left: '\\\\[', right: '\\\\]', display: true },
                                    { left: '$', right: '$', display: false },
                                    { left: '\\\\(', right: '\\\\)', display: false }
                                ],
                                throwOnError: false,
                                strict: 'ignore',
                                ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option'],
                            });
                        }
                    } catch (error) {
                    } finally {
                        bindAetherLinks();
                        setTimeout(reportAetherHeight, 0);
                        setTimeout(reportAetherHeight, 120);
                        setTimeout(reportAetherHeight, 360);
                    }
                }

                window.reportAetherHeight = reportAetherHeight;
                window.addEventListener('resize', reportAetherHeight);
                window.addEventListener('load', function() {
                    setTimeout(renderAetherMath, 0);
                    setTimeout(reportAetherHeight, 120);
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun buildMarkdownTextVariantCss(
    variant: MarkdownHtmlTextVariant,
): String = when (variant) {
    MarkdownHtmlTextVariant.Paragraph,
    MarkdownHtmlTextVariant.ListItem,
    MarkdownHtmlTextVariant.Quote -> "font-size: 16px; line-height: 24px; font-weight: 400;"
    is MarkdownHtmlTextVariant.Heading -> when (variant.level) {
        1 -> "font-size: 28px; line-height: 34px; font-weight: 600;"
        2 -> "font-size: 22px; line-height: 30px; font-weight: 600;"
        3 -> "font-size: 18px; line-height: 26px; font-weight: 600;"
        else -> "font-size: 14px; line-height: 20px; font-weight: 600;"
    }
    is MarkdownHtmlTextVariant.TableCell -> if (variant.isHeader) {
        "font-size: 14px; line-height: 20px; font-weight: 600;"
    } else {
        "font-size: 14px; line-height: 20px; font-weight: 400;"
    }
}

private fun inlineMarkdownToHtml(
    text: String,
): String = buildString {
    appendInlineHtml(text)
}

private fun StringBuilder.appendInlineHtml(
    text: String,
) {
    var index = 0
    while (index < text.length) {
        val mathMatch = parseMarkdownMathAt(text, index)
        if (mathMatch != null) {
            append(escapeHtml(mathMatch.rawText))
            index = mathMatch.endExclusive
            continue
        }

        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", index + 2)
            if (end > index + 2) {
                append("<strong>")
                appendInlineHtml(text.substring(index + 2, end))
                append("</strong>")
                index = end + 2
                continue
            }
        }

        if (text.startsWith("`", index)) {
            val end = text.indexOf('`', index + 1)
            if (end > index + 1) {
                append("<code>")
                append(escapeHtml(text.substring(index + 1, end)))
                append("</code>")
                index = end + 1
                continue
            }
        }

        val linkMatch = parseInlineMarkdownLink(text, index)
        if (linkMatch != null) {
            append("<a href=\"")
            append(escapeHtml(linkMatch.destination))
            append("\">")
            appendInlineHtml(linkMatch.label)
            append("</a>")
            index = linkMatch.endExclusive
            continue
        }

        val autoLink = parseAutoLink(text, index)
        if (autoLink != null) {
            append("<a href=\"")
            append(escapeHtml(autoLink.targetUrl))
            append("\">")
            append(escapeHtml(autoLink.displayText))
            append("</a>")
            index += autoLink.displayText.length
            continue
        }

        if (text.startsWith("*", index)) {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1) {
                append("<em>")
                appendInlineHtml(text.substring(index + 1, end))
                append("</em>")
                index = end + 1
                continue
            }
        }

        append(escapeHtml(text[index].toString()))
        index++
    }
}

private fun containsRenderableMarkdownMath(
    text: String,
): Boolean {
    var index = 0
    while (index < text.length) {
        if (text.startsWith("`", index)) {
            val codeEnd = text.indexOf('`', index + 1)
            if (codeEnd > index) {
                index = codeEnd + 1
                continue
            }
        }
        val mathMatch = parseMarkdownMathAt(text, index)
        if (mathMatch != null) return true
        index++
    }
    return false
}

private fun parseMarkdownMathAt(
    text: String,
    startIndex: Int,
): MarkdownMathMatch? {
    if (startIndex !in text.indices) return null
    val openingDelimiter = when {
        text.startsWith("$$", startIndex) -> "$$"
        text.startsWith("\\[", startIndex) -> "\\["
        text.startsWith("\\(", startIndex) -> "\\("
        text[startIndex] == '$' -> "$"
        else -> return null
    }
    val closingDelimiter = when (openingDelimiter) {
        "$$" -> "$$"
        "\\[" -> "\\]"
        "\\(" -> "\\)"
        else -> "$"
    }
    val contentStart = startIndex + openingDelimiter.length
    if (contentStart >= text.length) return null

    var index = contentStart
    while (index < text.length) {
        if (text.startsWith(closingDelimiter, index)) {
            val content = text.substring(contentStart, index)
            if (!isRenderableMarkdownMathContent(openingDelimiter, content)) return null
            return MarkdownMathMatch(
                rawText = text.substring(startIndex, index + closingDelimiter.length),
                endExclusive = index + closingDelimiter.length,
            )
        }
        if (text[index] == '\\' && !text.startsWith(closingDelimiter, index) && index + 1 < text.length) {
            index += 2
            continue
        }
        index++
    }
    return null
}

private fun isRenderableMarkdownMathContent(
    openingDelimiter: String,
    content: String,
): Boolean {
    val trimmed = content.trim()
    if (trimmed.isBlank()) return false
    if (openingDelimiter != "$") return true
    if (trimmed.contains('\n')) return false
    return trimmed.any { it in "\\_^{}=+-*/()[]<>" } || trimmed.none(Char::isWhitespace)
}

private fun parseInlineMarkdownLink(
    text: String,
    startIndex: Int,
): MarkdownInlineLinkMatch? {
    if (startIndex !in text.indices || text[startIndex] != '[') return null
    val closeBracket = text.indexOf("](", startIndex)
    if (closeBracket <= startIndex) return null

    var index = closeBracket + 2
    var nestedParentheses = 0
    while (index < text.length) {
        val character = text[index]
        if (character == '\\' && index + 1 < text.length) {
            index += 2
            continue
        }
        when (character) {
            '(' -> nestedParentheses++
            ')' -> {
                if (nestedParentheses == 0) break
                nestedParentheses--
            }
        }
        index++
    }
    if (index >= text.length || text[index] != ')') return null

    val destination = extractMarkdownLinkDestination(
        text.substring(closeBracket + 2, index)
    ).orEmpty()
    if (destination.isBlank()) return null

    return MarkdownInlineLinkMatch(
        label = text.substring(startIndex + 1, closeBracket),
        destination = destination,
        endExclusive = index + 1,
    )
}

private fun inlineMarkdown(
    text: String,
    sourceOffset: Int,
    fadeSpan: MarkdownFadeSpan?,
): AnnotatedString = buildAnnotatedString {
    appendInline(text, sourceOffset, fadeSpan)
}

private fun plainMarkdownText(
    text: String,
    sourceOffset: Int,
    fadeSpan: MarkdownFadeSpan?,
): AnnotatedString = buildAnnotatedString {
    appendSourceSegment(text, sourceOffset, fadeSpan)
}

private fun AnnotatedString.Builder.appendInline(
    text: String,
    sourceOffset: Int,
    fadeSpan: MarkdownFadeSpan?,
) {
    var index = 0
    while (index < text.length) {
        val mathMatch = parseMarkdownMathAt(text, index)
        if (mathMatch != null) {
            appendSourceSegment(
                text = mathMatch.rawText,
                sourceOffset = sourceOffset + index,
                fadeSpan = fadeSpan,
            )
            index = mathMatch.endExclusive
            continue
        }

        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", index + 2)
            if (end > index + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                appendInline(
                    text = text.substring(index + 2, end),
                    sourceOffset = sourceOffset + index + 2,
                    fadeSpan = fadeSpan,
                )
                pop()
                index = end + 2
                continue
            }
        }

        if (text.startsWith("`", index)) {
            val end = text.indexOf('`', index + 1)
            if (end > index + 1) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = AetherSurfaceHigh,
                    )
                )
                appendSourceSegment(
                    text = text.substring(index + 1, end),
                    sourceOffset = sourceOffset + index + 1,
                    fadeSpan = fadeSpan,
                )
                pop()
                index = end + 1
                continue
            }
        }

        val linkMatch = parseInlineMarkdownLink(text, index)
        if (linkMatch != null) {
            pushStyle(
                SpanStyle(
                    color = AetherPrimary,
                    textDecoration = TextDecoration.Underline,
                )
            )
            pushStringAnnotation(
                tag = LinkAnnotationTag,
                annotation = linkMatch.destination,
            )
            appendInline(
                text = linkMatch.label,
                sourceOffset = sourceOffset + index + 1,
                fadeSpan = fadeSpan,
            )
            pop()
            pop()
            index = linkMatch.endExclusive
            continue
        }

        val autoLink = parseAutoLink(text, index)
        if (autoLink != null) {
            pushStyle(
                SpanStyle(
                    color = AetherPrimary,
                    textDecoration = TextDecoration.Underline,
                )
            )
            pushStringAnnotation(
                tag = LinkAnnotationTag,
                annotation = autoLink.targetUrl,
            )
            appendSourceSegment(
                text = autoLink.displayText,
                sourceOffset = sourceOffset + index,
                fadeSpan = fadeSpan,
            )
            pop()
            pop()
            index += autoLink.displayText.length
            continue
        }

        if (text.startsWith("*", index)) {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1) {
                pushStyle(
                    SpanStyle(fontStyle = FontStyle.Italic)
                )
                appendInline(
                    text = text.substring(index + 1, end),
                    sourceOffset = sourceOffset + index + 1,
                    fadeSpan = fadeSpan,
                )
                pop()
                index = end + 1
                continue
            }
        }

        appendSourceSegment(
            text = text[index].toString(),
            sourceOffset = sourceOffset + index,
            fadeSpan = fadeSpan,
        )
        index++
    }
}

private fun parseAutoLink(
    text: String,
    startIndex: Int,
): MarkdownAutoLinkMatch? {
    val rawMatch = autoLinkPattern.find(text.substring(startIndex)) ?: return null
    if (rawMatch.range.first != 0) return null
    val displayText = rawMatch.value.trimEnd('.', ',', ';', ':')
    if (displayText.isBlank()) return null
    val targetUrl = if (displayText.startsWith("www.", ignoreCase = true)) {
        "https://$displayText"
    } else {
        displayText
    }
    return MarkdownAutoLinkMatch(
        displayText = displayText,
        targetUrl = targetUrl,
    )
}

private suspend fun loadMarkdownImage(
    context: Context,
    workspaceFileBridge: WorkspaceFileBridge,
    rawUrl: String,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean,
): MarkdownImageLoadResult = runCatching {
    val loadPreviewError = context.getString(R.string.markdown_image_error_load_preview)
    val readDataError = context.getString(R.string.markdown_image_error_read_data)
    val readWorkspaceError = context.getString(R.string.markdown_image_error_read_workspace)
    val normalizedUrl = normalizeMarkdownImageUrl(rawUrl)
        ?: error(loadPreviewError)
    val imageBinary = when {
        normalizedUrl.startsWith("http://", ignoreCase = true) ||
            normalizedUrl.startsWith("https://", ignoreCase = true) -> {
            fetchRemoteMarkdownImage(context, normalizedUrl)
        }

        normalizedUrl.startsWith("data:", ignoreCase = true) -> {
            decodeDataUrl(context, normalizedUrl)
        }

        normalizedUrl.startsWith("content://", ignoreCase = true) -> {
            readContentMarkdownImage(context, normalizedUrl)
        }

        else -> {
            val localFilePath = parseAssistantLocalFileLink(normalizedUrl)
            loadWorkspaceImageBinary(
                context = context,
                workspaceFileBridge = workspaceFileBridge,
                rawPath = localFilePath ?: normalizedUrl,
                workingDirectory = workspaceDirectory
                    ?.trim()
                    ?.ifBlank { TermuxContract.HomeDirectory }
                    ?: TermuxContract.HomeDirectory,
                allowRootImageRead = allowRootImageRead,
                readDataError = readDataError,
                readWorkspaceError = readWorkspaceError,
            ) ?: readLocalMarkdownImage(context, normalizedUrl)
        }
    } ?: error(loadPreviewError)

    decodeMarkdownImageResult(
        context = context,
        imageBinary = imageBinary,
        rawUrl = normalizedUrl,
    )
}.getOrElse { throwable ->
    MarkdownImageLoadResult(
        error = throwable.message ?: context.getString(R.string.markdown_image_error_load_preview),
    )
}

private suspend fun loadWorkspaceImageBinary(
    context: Context,
    workspaceFileBridge: WorkspaceFileBridge,
    rawPath: String,
    workingDirectory: String,
    allowRootImageRead: Boolean,
    readDataError: String,
    readWorkspaceError: String,
) : MarkdownImageBinary? {
    val resolvedPath = if (rawPath.startsWith("file://", ignoreCase = true)) {
        workspaceFileBridge.resolveLinkPath(rawPath)
    } else {
        workspaceFileBridge.resolveTermuxPath(
            path = rawPath,
            workingDirectory = workingDirectory,
        )
    }
    val localFile = File(resolvedPath)
    var localReadFailure: Throwable? = null
    if (localFile.exists() && localFile.isFile) {
        val localResult = runCatching { readLocalMarkdownImage(context, localFile.absolutePath) }
        localResult.getOrNull()?.let { return it }
        localReadFailure = localResult.exceptionOrNull()
        if (!allowRootImageRead) {
            error(localReadFailure?.message ?: readDataError)
        }
    }
    val workspaceResult = workspaceFileBridge.readWorkspaceFile(
        path = resolvedPath,
        byteLimit = MaxMarkdownImageBytes,
    )
    val payload = workspaceResult.getOrElse { workspaceThrowable ->
        if (!allowRootImageRead) {
            error(
                localReadFailure?.message
                    ?: workspaceThrowable.message
                    ?: readWorkspaceError
            )
        }
        workspaceFileBridge.readRootImageFile(
            path = resolvedPath,
            workingDirectory = workingDirectory,
            byteLimit = MaxMarkdownImageBytes,
        ).getOrElse { rootThrowable ->
            error(rootThrowable.message ?: workspaceThrowable.message ?: readDataError)
        }
    }
    return MarkdownImageBinary(
        bytes = payload.bytes,
        mimeType = workspaceFileBridge.guessMimeType(resolvedPath).ifBlank { null },
    )
}

private fun readLocalMarkdownImage(
    context: Context,
    rawPath: String,
): MarkdownImageBinary? {
    val file = File(rawPath)
    if (!file.exists() || !file.isFile) return null
    val byteLimit = MaxMarkdownImageBytes + 1
    val bytes = file.inputStream().use { readBytesWithLimit(context, it, byteLimit) }
    ensureMarkdownImageWithinLimit(context, bytes.size)
    return MarkdownImageBinary(
        bytes = bytes,
        mimeType = inferMarkdownImageMimeType(
            reportedMimeType = guessMimeTypeFromPath(file.name),
            rawUrl = rawPath,
            bytes = bytes,
        ),
    )
}

private fun readContentMarkdownImage(
    context: Context,
    rawUrl: String,
): MarkdownImageBinary? {
    val uri = Uri.parse(rawUrl)
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        readBytesWithLimit(context, input, MaxMarkdownImageBytes + 1)
    } ?: return null
    ensureMarkdownImageWithinLimit(context, bytes.size)
    return MarkdownImageBinary(
        bytes = bytes,
        mimeType = inferMarkdownImageMimeType(
            reportedMimeType = context.contentResolver.getType(uri),
            rawUrl = rawUrl,
            bytes = bytes,
        ),
    )
}

private fun fetchRemoteMarkdownImage(
    context: Context,
    url: String,
): MarkdownImageBinary {
    val request = Request.Builder()
        .url(url)
        .header("Accept", "image/*,*/*;q=0.8")
        .header("User-Agent", "Aether/0.1")
        .build()
    return MarkdownImageHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error(context.getString(R.string.markdown_image_error_load_preview_http, response.code))
        }
        val body = response.body ?: error(context.getString(R.string.markdown_image_error_load_preview))
        val bytes = body.byteStream().use { readBytesWithLimit(context, it, MaxMarkdownImageBytes + 1) }
        ensureMarkdownImageWithinLimit(context, bytes.size)
        MarkdownImageBinary(
            bytes = bytes,
            mimeType = inferMarkdownImageMimeType(
                reportedMimeType = body.contentType()?.toString(),
                rawUrl = url,
                bytes = bytes,
            ),
        )
    }
}

private fun decodeDataUrl(
    context: Context,
    dataUrl: String,
): MarkdownImageBinary {
    val commaIndex = dataUrl.indexOf(',')
    require(commaIndex > "data:".length) { context.getString(R.string.markdown_image_error_load_preview) }

    val metadata = dataUrl.substring("data:".length, commaIndex)
    val payload = dataUrl.substring(commaIndex + 1)
    val metadataParts = metadata.split(';')
    val reportedMimeType = metadataParts.firstOrNull().orEmpty().ifBlank { null }
    val isBase64 = metadataParts.any { it.equals("base64", ignoreCase = true) }
    val bytes = if (isBase64) {
        Base64.getDecoder().decode(payload)
    } else {
        URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
    }
    ensureMarkdownImageWithinLimit(context, bytes.size)
    return MarkdownImageBinary(
        bytes = bytes,
        mimeType = inferMarkdownImageMimeType(
            reportedMimeType = reportedMimeType,
            rawUrl = dataUrl,
            bytes = bytes,
        ),
    )
}

private fun decodeMarkdownImageResult(
    context: Context,
    imageBinary: MarkdownImageBinary,
    rawUrl: String,
): MarkdownImageLoadResult {
    val bitmap = decodeMarkdownBitmap(imageBinary.bytes)
    if (bitmap != null) {
        return MarkdownImageLoadResult(bitmap = bitmap)
    }

    val mimeType = inferMarkdownImageMimeType(
        reportedMimeType = imageBinary.mimeType,
        rawUrl = rawUrl,
        bytes = imageBinary.bytes,
    )
    if (mimeType != null) {
        if (isSvgMarkdownImage(mimeType)) {
            val svg = decodeMarkdownSvgText(imageBinary.bytes)
            if (svg.isNotBlank()) {
                return MarkdownImageLoadResult(html = buildMarkdownInlineSvgHtml(svg))
            }
        }
        val imageUrl = writeMarkdownImageCacheFile(
            context = context,
            bytes = imageBinary.bytes,
            rawUrl = rawUrl,
            mimeType = mimeType,
        ) ?: buildImageDataUrl(imageBinary.bytes, mimeType)
        return MarkdownImageLoadResult(
            html = buildMarkdownImageHtml(
                imageUrl = imageUrl,
                loadErrorMessage = context.getString(R.string.markdown_image_error_load_preview),
            ),
        )
    }

    error(context.getString(R.string.markdown_image_error_load_preview))
}

internal fun inferMarkdownImageMimeType(
    reportedMimeType: String?,
    rawUrl: String,
    bytes: ByteArray,
): String? {
    val normalizedMimeType = reportedMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.ifBlank { null }
    if (looksLikeSvgDocument(bytes) || normalizedMimeType?.contains("svg") == true) return "image/svg+xml"
    if (bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) return "image/png"
    if (bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) return "image/jpeg"
    if (bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray())) return "image/gif"
    if (bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
    ) {
        return "image/webp"
    }

    if (normalizedMimeType?.startsWith("image/") == true) {
        return normalizedMimeType
    }

    return guessMimeTypeFromPath(rawUrl)
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

private fun decodeMarkdownBitmap(bytes: ByteArray): ImageBitmap? {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it.asImageBitmap() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))).asImageBitmap()
    }.getOrNull()
}

private fun looksLikeSvgDocument(bytes: ByteArray): Boolean {
    val preview = bytes.toString(Charsets.UTF_8).trimStart()
    return preview.startsWith("<svg", ignoreCase = true) ||
        preview.startsWith("<?xml", ignoreCase = true) && preview.contains("<svg", ignoreCase = true)
}

private fun buildImageDataUrl(
    bytes: ByteArray,
    mimeType: String,
): String = "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"

internal fun buildMarkdownInlineSvgHtml(
    svg: String,
): String {
    val sanitizedSvg = sanitizeInlineMarkdownSvg(svg)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                }
                body {
                    padding: 12px;
                }
                .image-shell {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 136px;
                }
                .image-shell > svg {
                    display: block;
                    max-width: 100%;
                    height: auto;
                    border-radius: 12px;
                }
            </style>
        </head>
        <body>
            <div id="preview-image" class="image-shell">
                $sanitizedSvg
            </div>
            <script>
                function reportAetherHeight() {
                    const height = Math.max(
                        document.documentElement.scrollHeight || 0,
                        document.body.scrollHeight || 0,
                        160
                    );
                    if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportHeight) {
                        window.$MarkdownHtmlBridgeName.reportHeight(String(height));
                    }
                }

                const image = document.getElementById('preview-image');
                if (image) {
                    image.addEventListener('click', function() {
                        if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportTap) {
                            window.$MarkdownHtmlBridgeName.reportTap();
                        }
                    });
                }

                window.addEventListener('load', function() {
                    setTimeout(reportAetherHeight, 0);
                    setTimeout(reportAetherHeight, 120);
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun buildMarkdownImageHtml(
    imageUrl: String,
    loadErrorMessage: String,
): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
            html, body {
                margin: 0;
                padding: 0;
                background: transparent;
            }
            body {
                padding: 12px;
            }
            .image-shell {
                display: flex;
                align-items: center;
                justify-content: center;
                min-height: 136px;
            }
            img {
                display: block;
                max-width: 100%;
                height: auto;
                border-radius: 12px;
            }
            .image-error {
                color: #6b7280;
                display: none;
                font: 14px sans-serif;
                padding: 16px;
            }
        </style>
    </head>
    <body>
        <div class="image-shell">
            <img id="preview-image" src="${escapeHtml(imageUrl)}" alt="" />
        </div>
        <div id="image-error" class="image-error">${escapeHtml(loadErrorMessage)}</div>
        <script>
            function reportAetherHeight() {
                const height = Math.max(
                    document.documentElement.scrollHeight || 0,
                    document.body.scrollHeight || 0,
                    160
                );
                if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportHeight) {
                    window.$MarkdownHtmlBridgeName.reportHeight(String(height));
                }
            }

            const image = document.getElementById('preview-image');
            if (image) {
                image.addEventListener('load', function() {
                    setTimeout(reportAetherHeight, 0);
                    setTimeout(reportAetherHeight, 120);
                });
                image.addEventListener('click', function() {
                    if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportTap) {
                        window.$MarkdownHtmlBridgeName.reportTap();
                    }
                });
                image.addEventListener('error', function() {
                    const error = document.getElementById('image-error');
                    if (error) {
                        error.style.display = 'block';
                    }
                    image.style.display = 'none';
                    reportAetherHeight();
                });
            }

            window.addEventListener('load', function() {
                setTimeout(reportAetherHeight, 0);
                setTimeout(reportAetherHeight, 120);
            });
        </script>
    </body>
    </html>
""".trimIndent()

internal fun sanitizeInlineMarkdownSvg(svg: String): String = svg
    .trim { it <= ' ' || it == '\uFEFF' }
    .replace(Regex("(?is)^\\s*<\\?xml[^>]*>"), "")
    .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
    .replace(Regex("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\""), "")
    .replace(Regex("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'"), "")
    .replace(Regex("(?i)\\s+(?:xlink:href|href)\\s*=\\s*\"\\s*javascript:[^\"]*\""), "")
    .replace(Regex("(?i)\\s+(?:xlink:href|href)\\s*=\\s*'\\s*javascript:[^']*'"), "")
    .trim()

private fun decodeMarkdownSvgText(bytes: ByteArray): String =
    bytes.toString(Charsets.UTF_8)

private fun isSvgMarkdownImage(mimeType: String): Boolean =
    mimeType.substringBefore(';').trim().lowercase().contains("svg")

private fun writeMarkdownImageCacheFile(
    context: Context,
    bytes: ByteArray,
    rawUrl: String,
    mimeType: String,
): String? = runCatching {
    val directory = File(context.cacheDir, "markdown-images").apply { mkdirs() }
    val extension = markdownImageCacheExtension(mimeType, rawUrl)
    val file = File(directory, "${markdownImageCacheKey(rawUrl, bytes)}.$extension")
    if (!file.exists() || file.length() != bytes.size.toLong()) {
        file.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }
    }
    Uri.fromFile(file).toString()
}.getOrNull()

private fun markdownImageCacheExtension(
    mimeType: String,
    rawUrl: String,
): String = when (mimeType.substringBefore(';').trim().lowercase()) {
    "image/png" -> "png"
    "image/jpeg", "image/jpg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/avif" -> "avif"
    "image/heic", "image/heif" -> "heic"
    "image/bmp", "image/x-bmp" -> "bmp"
    else -> rawUrl
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "img"
}

private fun markdownImageCacheKey(
    rawUrl: String,
    bytes: ByteArray,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(rawUrl.toByteArray(Charsets.UTF_8))
    digest.update(0.toByte())
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun guessMimeTypeFromPath(rawUrl: String): String? {
    val candidatePath = rawUrl
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
    val extension = candidatePath.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg", "svgz" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        else -> null
    }
}

private fun readBytesWithLimit(
    context: Context,
    inputStream: java.io.InputStream,
    byteLimit: Int,
): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalRead = 0

    while (true) {
        val read = inputStream.read(buffer)
        if (read <= 0) break
        totalRead += read
        if (totalRead > byteLimit) {
            error(context.getString(R.string.markdown_image_error_too_large))
        }
        output.write(buffer, 0, read)
    }

    return output.toByteArray()
}

private fun ensureMarkdownImageWithinLimit(context: Context, sizeBytes: Int) {
    if (sizeBytes > MaxMarkdownImageBytes) {
        error(context.getString(R.string.markdown_image_error_too_large))
    }
}

private fun buildMermaidHtml(
    code: String,
    layout: MarkdownMediaLayout,
    renderErrorTitle: String,
    invalidSyntaxError: String,
): String {
    val escapedCode = escapeHtml(code)
    val svgMaxWidth = if (layout.scroll) "none" else "100%"
    val containerWidthRule = if (layout.scroll) {
        "display: inline-block; min-width: 100%;"
    } else {
        ""
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                    color: #111827;
                    font-family: sans-serif;
                }
                #container {
                    padding: 12px;
                    $containerWidthRule
                }
                svg {
                    max-width: $svgMaxWidth;
                    height: auto;
                }
                pre {
                    white-space: pre-wrap;
                    font-family: monospace;
                    background: #f5f5f5;
                    border-radius: 12px;
                    padding: 12px;
                }
                .mermaid-error {
                    padding: 12px;
                    color: #1f2937;
                    font-family: sans-serif;
                }
                .mermaid-error-title {
                    font-size: 15px;
                    font-weight: 600;
                    margin-bottom: 8px;
                }
                .mermaid-error-detail {
                    color: #6b7280;
                    font-size: 13px;
                }
                .mermaid-source {
                    margin: 0 0 10px;
                }
            </style>
            <script src="$MermaidScriptUrl"></script>
        </head>
        <body>
            <div id="container">
                <pre id="diagram" class="mermaid">$escapedCode</pre>
            </div>
            <script>
                function escapeHtml(value) {
                    return String(value || '')
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#39;');
                }

                function reportAetherHeight() {
                    const height = Math.max(
                        document.documentElement.scrollHeight || 0,
                        document.body.scrollHeight || 0,
                        220
                    );
                    if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportHeight) {
                        window.$MarkdownHtmlBridgeName.reportHeight(String(height));
                    }
                }

                function reportAetherTap() {
                    if (window.$MarkdownHtmlBridgeName && window.$MarkdownHtmlBridgeName.reportTap) {
                        window.$MarkdownHtmlBridgeName.reportTap();
                    }
                }

                function showMermaidError(code, detail) {
                    document.getElementById('container').innerHTML =
                        '<div class="mermaid-error">' +
                        '<div class="mermaid-error-title">' + escapeHtml(renderErrorTitle) + '</div>' +
                        '<pre class="mermaid-source">' + escapeHtml(code) + '</pre>' +
                        '<div class="mermaid-error-detail">' + escapeHtml(detail || invalidSyntaxError) + '</div>' +
                        '</div>';
                }

                async function renderDiagram() {
                    const element = document.getElementById('diagram');
                    const code = element.textContent.trim();
                    try {
                        if (!window.mermaid) {
                            throw new Error('Mermaid library failed to load.');
                        }
                        mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: 'neutral' });
                        await mermaid.parse(code, { suppressErrors: false });
                        const rendered = await mermaid.render('aether-mermaid-' + Date.now(), code);
                        if ((rendered.svg || '').includes('class="error-icon"') || (rendered.svg || '').includes('Syntax error in text')) {
                            throw new Error('Mermaid reported a syntax error.');
                        }
                        document.getElementById('container').innerHTML = rendered.svg;
                        document.getElementById('container').onclick = reportAetherTap;
                    } catch (error) {
                        showMermaidError(
                            code,
                            error && error.message ? error.message : invalidSyntaxError,
                        );
                        document.getElementById('container').onclick = reportAetherTap;
                    } finally {
                        setTimeout(reportAetherHeight, 0);
                        setTimeout(reportAetherHeight, 120);
                        setTimeout(reportAetherHeight, 360);
                    }
                }

                window.addEventListener('load', renderDiagram);
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(value: String): String = buildString {
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

private fun Color.toCssHex(): String {
    val argb = toArgb()
    val alpha = (argb ushr 24) and 0xFF
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    return if (alpha == 0xFF) {
        String.format("#%02X%02X%02X", red, green, blue)
    } else {
        String.format("#%02X%02X%02X%02X", red, green, blue, alpha)
    }
}

private class MarkdownHtmlBridge(
    private val onHeightMeasured: (Int) -> Unit,
    private val onTap: (() -> Unit)? = null,
    private val onLinkClick: ((String) -> Unit)? = null,
) {
    @JavascriptInterface
    fun reportHeight(height: String?) {
        height?.toIntOrNull()?.let(onHeightMeasured)
    }

    @JavascriptInterface
    fun reportTap() {
        onTap?.invoke()
    }

    @JavascriptInterface
    fun reportLink(url: String?) {
        url?.takeIf { it.isNotBlank() }?.let { targetUrl ->
            onLinkClick?.invoke(targetUrl)
        }
    }
}

private fun AnnotatedString.Builder.appendSourceSegment(
    text: String,
    sourceOffset: Int,
    fadeSpan: MarkdownFadeSpan?,
) {
    if (text.isEmpty()) return
    if (fadeSpan == null || fadeSpan.sourceRange.isEmpty()) {
        append(text)
        return
    }

    val segmentEndExclusive = sourceOffset + text.length
    val fadeStart = fadeSpan.sourceRange.first.coerceAtLeast(sourceOffset)
    val fadeEndExclusive = (fadeSpan.sourceRange.last + 1).coerceAtMost(segmentEndExclusive)
    if (fadeEndExclusive <= fadeStart) {
        append(text)
        return
    }

    val localFadeStart = fadeStart - sourceOffset
    val localFadeEndExclusive = fadeEndExclusive - sourceOffset

    if (localFadeStart > 0) {
        append(text.substring(0, localFadeStart))
    }

    pushStyle(SpanStyle(color = AetherOnSurface.copy(alpha = fadeSpan.alpha)))
    append(text.substring(localFadeStart, localFadeEndExclusive))
    pop()

    if (localFadeEndExclusive < text.length) {
        append(text.substring(localFadeEndExclusive))
    }
}