package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.platform.PlatformWebView
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.common_close
import com.zhousl.aether.shared.resources.markdown_image_error_load_preview
import com.zhousl.aether.shared.resources.markdown_image_error_load_preview_http
import com.zhousl.aether.shared.resources.markdown_image_error_read_data
import com.zhousl.aether.shared.resources.markdown_image_error_read_workspace
import com.zhousl.aether.shared.resources.markdown_image_error_too_large
import com.zhousl.aether.shared.resources.markdown_image_preview_title
import com.zhousl.aether.shared.resources.markdown_open_original
import com.zhousl.aether.shared.resources.markdown_preview_unavailable
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlin.io.encoding.Base64
import okio.Buffer
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal const val SharedMarkdownFileMaximumBytes = 24L * 1024L * 1024L
internal const val SharedMarkdownImageMaximumBytes = 8L * 1024L * 1024L

internal data class SharedMarkdownImageBinary(
    val bytes: ByteArray,
    val mimeType: String?,
)

private data class SharedMarkdownImageLoadResult(
    val binary: SharedMarkdownImageBinary? = null,
    val bitmap: ImageBitmap? = null,
    val html: String? = null,
    val error: String? = null,
)

private data class SharedMarkdownImagePreview(
    val title: String,
    val image: SharedMarkdownImageLoadResult,
    val originalTarget: String?,
)

private val sharedMarkdownHttpClient by lazy { HttpClient() }

@Composable
internal fun SharedMarkdownContent(
    content: String,
    runtime: MultiplatformLocalRuntime?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    fadeSpan: SharedMarkdownFadeSpan? = null,
) {
    var imagePreview by remember { mutableStateOf<SharedMarkdownImagePreview?>(null) }
    val segments = remember(content) { parseSharedMarkdownPositionedSegments(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        segments.forEach { positionedSegment ->
            when (val segment = positionedSegment.segment) {
                is SharedMarkdownSegment.Markdown -> SharedMarkdownTextContent(
                    markdown = segment.content,
                    onOpenLink = onOpenLink,
                    modifier = Modifier.fillMaxWidth(),
                    sourceOffset = positionedSegment.sourceOffset,
                    fadeSpan = fadeSpan,
                )
                is SharedMarkdownSegment.Image -> SharedMarkdownImageBlock(
                    image = segment.image,
                    runtime = runtime,
                    onOpenLink = onOpenLink,
                    onPreview = { title, image, originalTarget ->
                        imagePreview = SharedMarkdownImagePreview(title, image, originalTarget)
                    },
                )
                is SharedMarkdownSegment.ImageGroup -> SharedMarkdownImageGroup(segment.images)
                is SharedMarkdownSegment.Mermaid -> SharedMarkdownMermaidBlock(segment)
            }
        }
    }
    imagePreview?.let { preview ->
        SharedMarkdownImagePreviewDialog(
            preview = preview,
            onDismiss = { imagePreview = null },
            onOpenOriginal = preview.originalTarget?.let { originalTarget ->
                {
                    onOpenLink(originalTarget)
                }
            },
        )
    }
}

@Composable
private fun SharedMarkdownImageBlock(
    image: SharedMarkdownImageSpec,
    runtime: MultiplatformLocalRuntime?,
    onOpenLink: (String) -> Unit,
    onPreview: (String, SharedMarkdownImageLoadResult, String?) -> Unit,
) {
    val loadPreviewError = stringResource(Res.string.markdown_image_error_load_preview)
    val httpErrorWithZero = stringResource(Res.string.markdown_image_error_load_preview_http, 0)
    val readDataError = stringResource(Res.string.markdown_image_error_read_data)
    val readWorkspaceError = stringResource(Res.string.markdown_image_error_read_workspace)
    val tooLargeError = stringResource(Res.string.markdown_image_error_too_large)
    val originalTarget = remember(image.url, runtime) {
        sharedMarkdownImageOriginalTarget(image.url, runtime)
    }
    val imageState by produceState(
        initialValue = SharedMarkdownImageLoadResult(),
        key1 = image.url,
        key2 = runtime,
    ) {
        value = loadSharedMarkdownImage(
            rawLink = image.url,
            runtime = runtime,
            loadPreviewError = loadPreviewError,
            httpErrorWithZero = httpErrorWithZero,
            readDataError = readDataError,
            readWorkspaceError = readWorkspaceError,
            tooLargeError = tooLargeError,
        )
    }
    val currentImageState = imageState
    val canPreview = currentImageState.bitmap != null || currentImageState.html != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SharedMarkdownMediaWidthContainer(image.layout) { widthModifier ->
            when {
                currentImageState.html != null -> SharedMarkdownImageHtmlBlock(
                    html = currentImageState.html,
                    layout = image.layout,
                    defaultMinHeightDp = 1,
                    defaultMaxHeightDp = 420,
                    modifier = widthModifier.clip(RoundedCornerShape(8.dp)),
                    onTap = if (canPreview) {
                        { onPreview(image.altText, currentImageState, originalTarget) }
                    } else null,
                )
                currentImageState.bitmap != null -> SharedMarkdownBitmapImageBlock(
                    bitmap = currentImageState.bitmap,
                    altText = image.altText,
                    layout = image.layout,
                    modifier = widthModifier.clip(RoundedCornerShape(8.dp)).clickable {
                        onPreview(image.altText, currentImageState, originalTarget)
                    },
                )
                else -> Box(
                    modifier = widthModifier.heightIn(
                        min = 72.dp,
                        max = (image.layout.maxHeightDp ?: 420).dp,
                    ).clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentImageState.error != null) {
                        Text(
                            currentImageState.error,
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
                image.altText,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        if (!originalTarget.isNullOrBlank()) {
            Text(
                stringResource(Res.string.markdown_open_original),
                style = MaterialTheme.typography.bodySmall,
                color = AetherPrimary,
                modifier = Modifier.clickable { onOpenLink(originalTarget) },
            )
        }
    }
}

@Composable
private fun SharedMarkdownImageGroup(images: List<SharedMarkdownImageSpec>) {
    val layout = remember {
        SharedMarkdownMediaLayout(minHeightDp = 1, maxHeightDp = 120, showAll = true)
    }
    SharedMarkdownImageHtmlBlock(
        html = remember(images) { buildSharedMarkdownBadgeGroupHtml(images) },
        layout = layout,
        defaultMinHeightDp = 1,
        defaultMaxHeightDp = 120,
        modifier = Modifier.fillMaxWidth(),
    )
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
        content(
            when (val width = layout.width) {
                null -> Modifier.fillMaxWidth()
                is SharedMarkdownMediaWidth.Fraction -> Modifier.fillMaxWidth(width.value.coerceIn(0.1f, 1f))
                is SharedMarkdownMediaWidth.DpValue -> Modifier.width(width.value.dp)
            }
        )
    }
}

@Composable
private fun SharedMarkdownBitmapImageBlock(
    bitmap: ImageBitmap,
    altText: String,
    layout: SharedMarkdownMediaLayout,
    modifier: Modifier,
) {
    val contentScale = if (layout.fit == SharedMarkdownMediaFit.Cover) ContentScale.Crop else ContentScale.Fit
    BoxWithConstraints(modifier) {
        val resolvedMaxHeight = (layout.maxHeightDp ?: 420).dp
        val explicitHeight = layout.heightDp?.dp
        val naturalWidth = if (bitmap.width > 0) bitmap.width.dp.coerceAtMost(maxWidth) else maxWidth
        val renderWidth = if (layout.width == null) naturalWidth else maxWidth
        val naturalHeight = if (bitmap.width > 0 && bitmap.height > 0) {
            renderWidth * (bitmap.height.toFloat() / bitmap.width.toFloat())
        } else resolvedMaxHeight
        val containerHeight = when {
            layout.showAll -> naturalHeight.coerceAtLeast(1.dp)
            explicitHeight != null -> explicitHeight
            else -> naturalHeight.coerceAtMost(resolvedMaxHeight).coerceAtLeast(1.dp)
        }
        val needsVerticalScroll = !layout.showAll && layout.scroll && naturalHeight > containerHeight
        Box(
            modifier = Modifier.width(renderWidth).height(containerHeight),
            contentAlignment = if (needsVerticalScroll) Alignment.TopCenter else Alignment.Center,
        ) {
            when {
                needsVerticalScroll -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    Image(
                        bitmap,
                        contentDescription = altText.takeIf { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = contentScale,
                    )
                }
                explicitHeight != null || (!layout.showAll && naturalHeight > containerHeight) -> Image(
                    bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
                else -> Image(
                    bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

@Composable
private fun SharedMarkdownImageHtmlBlock(
    html: String,
    layout: SharedMarkdownMediaLayout,
    defaultMinHeightDp: Int,
    defaultMaxHeightDp: Int,
    modifier: Modifier,
    onTap: (() -> Unit)? = null,
) {
    val minHeight = (layout.minHeightDp ?: defaultMinHeightDp).coerceAtLeast(1)
    val maxHeight = (layout.maxHeightDp ?: defaultMaxHeightDp).coerceAtLeast(minHeight)
    val scrollViewportHeight = if (layout.scroll) {
        (layout.heightDp ?: maxHeight).coerceAtLeast(1)
    } else null
    var measuredHeight by remember(html) { mutableStateOf(minHeight) }
    var measured by remember(html) { mutableStateOf(false) }
    val effectiveMinHeight = if (measured) 1 else minHeight
    val appliedHeight = when {
        layout.showAll -> measuredHeight.coerceAtLeast(effectiveMinHeight)
        layout.heightDp != null -> layout.heightDp.coerceAtLeast(effectiveMinHeight)
        scrollViewportHeight != null -> measuredHeight.coerceIn(effectiveMinHeight, scrollViewportHeight)
        else -> measuredHeight.coerceIn(effectiveMinHeight, maxHeight)
    }
    PlatformWebView(
        url = "",
        html = html,
        onMessage = { message ->
            when {
                message == "tap" -> onTap?.invoke()
                message.startsWith("height:") -> message.substringAfter(':').toDoubleOrNull()?.let {
                    measuredHeight = it.roundToInt().coerceAtLeast(1)
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
private fun SharedMarkdownImagePreviewDialog(
    preview: SharedMarkdownImagePreview,
    onDismiss: () -> Unit,
    onOpenOriginal: (() -> Unit)?,
) {
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
                    preview.title.ifBlank { stringResource(Res.string.markdown_image_preview_title) },
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
            when {
                preview.image.bitmap != null -> SharedMarkdownBitmapPreviewBlock(
                    bitmap = preview.image.bitmap,
                    altText = preview.title,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(AetherSurfaceHigh),
                )
                preview.image.html != null -> SharedMarkdownImageHtmlBlock(
                    html = preview.image.html,
                    layout = SharedMarkdownMediaLayout(maxHeightDp = 820, scroll = true),
                    defaultMinHeightDp = 1,
                    defaultMaxHeightDp = 820,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(AetherSurfaceHigh),
                )
                else -> Text(
                    preview.image.error ?: stringResource(Res.string.markdown_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
            }
            if (onOpenOriginal != null) {
                Text(
                    stringResource(Res.string.markdown_open_original),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherPrimary,
                    modifier = Modifier.clickable(onClick = onOpenOriginal),
                )
            }
        }
    }
}

@Composable
private fun SharedMarkdownBitmapPreviewBlock(
    bitmap: ImageBitmap,
    altText: String,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxHeight = 820.dp
        val naturalHeight = if (bitmap.width > 0 && bitmap.height > 0) {
            maxWidth * (bitmap.height.toFloat() / bitmap.width.toFloat())
        } else maxHeight
        val containerHeight = naturalHeight.coerceAtMost(maxHeight).coerceAtLeast(1.dp)
        val needsVerticalScroll = naturalHeight > containerHeight
        Box(
            modifier = Modifier.fillMaxWidth().height(containerHeight).padding(12.dp),
            contentAlignment = if (needsVerticalScroll) Alignment.TopCenter else Alignment.Center,
        ) {
            if (needsVerticalScroll) {
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        bitmap,
                        contentDescription = altText.takeIf { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Image(
                    bitmap,
                    contentDescription = altText.takeIf { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private suspend fun loadSharedMarkdownImage(
    rawLink: String,
    runtime: MultiplatformLocalRuntime?,
    loadPreviewError: String,
    httpErrorWithZero: String,
    readDataError: String,
    readWorkspaceError: String,
    tooLargeError: String,
): SharedMarkdownImageLoadResult = runCatching {
    val binary = loadSharedMarkdownImageBinary(
        rawLink = rawLink,
        runtime = runtime,
        loadPreviewError = loadPreviewError,
        readWorkspaceError = readWorkspaceError,
        httpErrorWithZero = httpErrorWithZero,
        readDataError = readDataError,
        tooLargeError = tooLargeError,
    )
    decodeSharedMarkdownImageResult(binary, loadPreviewError)
}.getOrElse { failure ->
    val message = failure.message.orEmpty().ifBlank { loadPreviewError }
    SharedMarkdownImageLoadResult(
        error = if (
            message.contains("exceed", ignoreCase = true) ||
            message.contains("larger", ignoreCase = true)
        ) tooLargeError else message,
    )
}

private suspend fun loadSharedMarkdownImageBinary(
    rawLink: String,
    runtime: MultiplatformLocalRuntime?,
    loadPreviewError: String,
    readWorkspaceError: String,
    httpErrorWithZero: String = loadPreviewError,
    readDataError: String = loadPreviewError,
    tooLargeError: String = loadPreviewError,
): SharedMarkdownImageBinary {
    val normalized = normalizeSharedMarkdownImageUrl(rawLink) ?: error(loadPreviewError)
    if (normalized.startsWith("data:", ignoreCase = true)) {
        return decodeSharedMarkdownDataUrlOrThrow(normalized, readDataError, tooLargeError)
    }
    if (
        normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("https://", ignoreCase = true)
    ) {
        return fetchSharedMarkdownImage(normalized, loadPreviewError, httpErrorWithZero, tooLargeError)
    }
    val selectedRuntime = runtime ?: error(readWorkspaceError)
    val path = resolveSharedWorkspacePath(normalized, selectedRuntime.workspaceRoot) ?: error(readWorkspaceError)
    val bytes = runCatching {
        selectedRuntime.fileSystem.read(path, SharedMarkdownImageMaximumBytes)
    }.getOrElse { failure ->
        if (failure.message.orEmpty().contains("size", ignoreCase = true)) error(tooLargeError)
        error(failure.message ?: readWorkspaceError)
    }
    if (bytes.size.toLong() > SharedMarkdownImageMaximumBytes) error(tooLargeError)
    return SharedMarkdownImageBinary(
        bytes = bytes,
        mimeType = inferSharedMarkdownImageMimeType(sharedMimeTypeForPath(path), path, bytes),
    )
}

private suspend fun fetchSharedMarkdownImage(
    url: String,
    loadPreviewError: String,
    httpErrorWithZero: String,
    tooLargeError: String,
): SharedMarkdownImageBinary {
    val response = sharedMarkdownHttpClient.get(url) {
        header(HttpHeaders.Accept, "image/*,*/*;q=0.8")
        header(HttpHeaders.UserAgent, "Aether/0.1")
    }
    if (!response.status.isSuccess()) {
        error(httpErrorWithZero.replace("0", response.status.value.toString()))
    }
    response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { length ->
        if (length > SharedMarkdownImageMaximumBytes) error(tooLargeError)
    }
    val channel = response.bodyAsChannel()
    val buffer = Buffer()
    val chunk = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = channel.readAvailable(chunk, 0, chunk.size)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > SharedMarkdownImageMaximumBytes) error(tooLargeError)
        buffer.write(chunk, 0, read)
    }
    val bytes = buffer.readByteArray()
    if (bytes.isEmpty()) error(loadPreviewError)
    return SharedMarkdownImageBinary(
        bytes = bytes,
        mimeType = inferSharedMarkdownImageMimeType(
            response.headers[HttpHeaders.ContentType],
            url,
            bytes,
        ),
    )
}

internal fun decodeSharedMarkdownDataUrl(rawTarget: String): SharedMarkdownImageBinary? = runCatching {
    decodeSharedMarkdownDataUrlOrThrow(rawTarget, "Couldn\'t read image data.", "Image is larger than 8.0 MB.")
}.getOrNull()

private fun decodeSharedMarkdownDataUrlOrThrow(
    rawTarget: String,
    readDataError: String,
    tooLargeError: String,
): SharedMarkdownImageBinary {
    val normalized = normalizeSharedMarkdownImageUrl(rawTarget) ?: error(readDataError)
    if (!normalized.startsWith("data:", ignoreCase = true)) error(readDataError)
    val separator = normalized.indexOf(',')
    if (separator <= "data:".length) error(readDataError)
    val metadata = normalized.substring("data:".length, separator)
    val parts = metadata.split(';')
    val reportedMimeType = parts.firstOrNull()?.trim()?.ifBlank { null }
    val payload = normalized.substring(separator + 1)
    val bytes = if (parts.any { it.equals("base64", ignoreCase = true) }) {
        runCatching { Base64.decode(payload) }.getOrElse { error(readDataError) }
    } else {
        decodeSharedDataPayload(payload, readDataError).encodeToByteArray()
    }
    if (bytes.size.toLong() > SharedMarkdownImageMaximumBytes) error(tooLargeError)
    return SharedMarkdownImageBinary(
        bytes = bytes,
        mimeType = inferSharedMarkdownImageMimeType(reportedMimeType, normalized, bytes),
    )
}

private fun decodeSharedMarkdownImageResult(
    binary: SharedMarkdownImageBinary,
    loadPreviewError: String,
): SharedMarkdownImageLoadResult {
    val mimeType = inferSharedMarkdownImageMimeType(binary.mimeType, "", binary.bytes)
    if (mimeType?.contains("svg", ignoreCase = true) == true) {
        val svg = binary.bytes.decodeToString(throwOnInvalidSequence = false)
        if (svg.isNotBlank()) {
            return SharedMarkdownImageLoadResult(
                binary = binary,
                html = buildSharedMarkdownInlineSvgHtml(svg),
            )
        }
    }
    runCatching { binary.bytes.decodeToImageBitmap() }.getOrNull()?.let { bitmap ->
        return SharedMarkdownImageLoadResult(binary = binary, bitmap = bitmap)
    }
    if (mimeType?.startsWith("image/") == true) {
        return SharedMarkdownImageLoadResult(
            binary = binary,
            html = buildSharedMarkdownImageHtml(
                imageUrl = "data:$mimeType;base64,${Base64.encode(binary.bytes)}",
                loadErrorMessage = loadPreviewError,
            ),
        )
    }
    return SharedMarkdownImageLoadResult(binary = binary, error = loadPreviewError)
}

internal fun inferSharedMarkdownImageMimeType(
    reportedMimeType: String?,
    rawTarget: String,
    bytes: ByteArray,
): String? {
    val normalizedMimeType = reportedMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.ifBlank { null }
    val preview = bytes.decodeToString(throwOnInvalidSequence = false).trimStart()
    if (
        preview.startsWith("<svg", ignoreCase = true) ||
        preview.startsWith("<?xml", ignoreCase = true) && preview.contains("<svg", ignoreCase = true) ||
        normalizedMimeType?.contains("svg") == true
    ) {
        return "image/svg+xml"
    }
    if (bytes.startsWithSharedBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) return "image/png"
    if (bytes.startsWithSharedBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) return "image/jpeg"
    if (
        bytes.startsWithSharedBytes("GIF87a".encodeToByteArray()) ||
        bytes.startsWithSharedBytes("GIF89a".encodeToByteArray())
    ) {
        return "image/gif"
    }
    if (
        bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray())
    ) {
        return "image/webp"
    }
    if (normalizedMimeType?.startsWith("image/") == true) return normalizedMimeType
    return sharedMimeTypeForPath(rawTarget).takeIf { it.startsWith("image/") }
}

internal fun normalizeSharedMarkdownImageUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val withoutTitle = extractSharedMarkdownLinkDestination(trimmed).orEmpty()
    return withoutTitle.removeSurrounding("<", ">").replace("&amp;", "&").trim().ifBlank { null }
}

internal fun extractSharedMarkdownLinkDestination(rawDestination: String): String? {
    val trimmed = rawDestination.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith('<')) {
        val endIndex = trimmed.indexOf('>')
        if (endIndex > 1) return trimmed.substring(1, endIndex).trim().ifBlank { null }
    }
    val destination = StringBuilder()
    var nestedParentheses = 0
    var index = 0
    while (index < trimmed.length) {
        val character = trimmed[index]
        if (character == '\\' && index + 1 < trimmed.length) {
            destination.append(trimmed[index + 1])
            index += 2
            continue
        }
        if (character.isWhitespace() && nestedParentheses == 0) break
        when (character) {
            '(' -> nestedParentheses++
            ')' -> if (nestedParentheses > 0) nestedParentheses--
        }
        destination.append(character)
        index++
    }
    return destination.toString().trim().ifBlank { null }
}

internal fun sharedMarkdownImageOriginalTarget(
    rawUrl: String,
    runtime: MultiplatformLocalRuntime?,
): String? {
    val normalized = normalizeSharedMarkdownImageUrl(rawUrl) ?: return null
    return when {
        normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true) -> normalized
        normalized.startsWith("data:", ignoreCase = true) ||
            normalized.startsWith("content://", ignoreCase = true) -> null
        else -> runtime?.let {
            resolveSharedWorkspacePath(normalized, it.workspaceRoot)?.let { path -> "file://$path" }
        }
    }
}

internal fun buildSharedMarkdownBadgeGroupHtml(images: List<SharedMarkdownImageSpec>): String {
    val tags = images.joinToString("\n") { image ->
        """<img src="${escapeSharedMarkdownHtml(image.url)}" alt="${escapeSharedMarkdownHtml(image.altText)}" />"""
    }
    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>html,body{margin:0;padding:0;background:transparent}.badge-row{display:flex;flex-wrap:wrap;align-items:center;gap:6px}img{display:block;width:auto;height:auto;max-width:100%;max-height:32px}</style>
        </head><body><div class="badge-row">$tags</div>
        <script>
        function postAether(message){if(window.Aether&&window.Aether.postMessage){window.Aether.postMessage(message);}}
        function reportAetherHeight(){const height=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0,1);postAether('height:'+height);}
        document.querySelectorAll('img').forEach(function(image){image.addEventListener('load',reportAetherHeight);image.addEventListener('error',function(){image.style.display='none';reportAetherHeight();});});
        window.addEventListener('load',function(){setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);});
        </script></body></html>
    """.trimIndent()
}

internal fun buildSharedMarkdownInlineSvgHtml(svg: String): String = """
    <!DOCTYPE html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <style>html,body{margin:0;padding:0;background:transparent}.image-shell{display:flex;align-items:center;justify-content:flex-start}.image-shell>svg{display:block;max-width:100%;height:auto;border-radius:12px}</style>
    </head><body><div id="preview-image" class="image-shell">${sanitizeSharedInlineMarkdownSvg(svg)}</div>
    <script>
    function postAether(message){if(window.Aether&&window.Aether.postMessage){window.Aether.postMessage(message);}}
    function reportAetherHeight(){const height=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0,1);postAether('height:'+height);}
    const image=document.getElementById('preview-image');if(image){image.addEventListener('click',function(){postAether('tap');});}
    window.addEventListener('load',function(){setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);});
    </script></body></html>
""".trimIndent()

private fun buildSharedMarkdownImageHtml(imageUrl: String, loadErrorMessage: String): String = """
    <!DOCTYPE html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <style>html,body{margin:0;padding:0;background:transparent}.image-shell{display:flex;align-items:center;justify-content:flex-start}img{display:block;max-width:100%;height:auto;border-radius:12px}.image-error{color:#6b7280;display:none;font:14px sans-serif;padding:16px}</style>
    </head><body><div class="image-shell"><img id="preview-image" src="${escapeSharedMarkdownHtml(imageUrl)}" alt="" /></div><div id="image-error" class="image-error">${escapeSharedMarkdownHtml(loadErrorMessage)}</div>
    <script>
    function postAether(message){if(window.Aether&&window.Aether.postMessage){window.Aether.postMessage(message);}}
    function reportAetherHeight(){const height=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0,1);postAether('height:'+height);}
    const image=document.getElementById('preview-image');if(image){image.addEventListener('load',function(){setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);});image.addEventListener('click',function(){postAether('tap');});image.addEventListener('error',function(){const error=document.getElementById('image-error');if(error){error.style.display='block';}image.style.display='none';reportAetherHeight();});}
    window.addEventListener('load',function(){setTimeout(reportAetherHeight,0);setTimeout(reportAetherHeight,120);});
    </script></body></html>
""".trimIndent()

internal fun sanitizeSharedInlineMarkdownSvg(svg: String): String = svg
    .trim { it <= ' ' || it == '\uFEFF' }
    .replace(Regex("(?is)^\\s*<\\?xml[^>]*>"), "")
    .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
    .replace(Regex("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\""), "")
    .replace(Regex("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'"), "")
    .replace(Regex("(?i)\\s+(?:xlink:href|href)\\s*=\\s*\"\\s*javascript:[^\"]*\""), "")
    .replace(Regex("(?i)\\s+(?:xlink:href|href)\\s*=\\s*'\\s*javascript:[^']*'"), "")
    .trim()

private fun escapeSharedMarkdownHtml(value: String): String = buildString {
    value.forEach { character ->
        append(when (character) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            else -> character
        })
    }
}

internal fun resolveSharedWorkspacePath(rawTarget: String, workspaceRoot: String): String? {
    val normalized = normalizeSharedMarkdownTarget(rawTarget)
    if (normalized.isBlank() || normalized.startsWith('#')) return null
    val isRelative = !normalized.startsWith("aether-local-file://", ignoreCase = true) &&
        !normalized.startsWith("file://", ignoreCase = true) &&
        !normalized.startsWith("~/") &&
        !normalized.startsWith('/') &&
        !normalized.contains("://") &&
        !normalized.startsWith("data:", ignoreCase = true)
    val decoded = when {
        normalized.startsWith("aether-local-file://", ignoreCase = true) ->
            decodeSharedPercentEncoding(normalized.substringAfter("://"))
        normalized.startsWith("file://", ignoreCase = true) ->
            decodeSharedPercentEncoding(normalized.removePrefixIgnoringCase("file://"))
        normalized.contains("://") -> return null
        normalized.startsWith("data:", ignoreCase = true) -> return null
        else -> decodeSharedPercentEncoding(normalized)
    }.replace('\\', '/')
    val workspaceComponents = workspaceRoot.normalizedSharedPathComponents()
    val components = if (isRelative) workspaceComponents.toMutableList() else mutableListOf()
    val minimumDepth = if (isRelative) components.size else 0
    val pathToResolve = when {
        isRelative -> decoded
        decoded.startsWith("~/") -> "/root/${decoded.removePrefix("~/")}"
        decoded.startsWith('/') -> decoded
        else -> "${workspaceRoot.trimEnd('/')}/$decoded"
    }
    pathToResolve.split('/').forEach { component ->
        when (component) {
            "", "." -> Unit
            ".." -> {
                if (components.size <= minimumDepth) return null
                components.removeAt(components.lastIndex)
            }
            else -> components += component
        }
    }
    return "/${components.joinToString("/")}".takeIf { it != "/" }
}

internal fun isSharedWorkspaceFileLink(rawTarget: String): Boolean {
    val normalized = normalizeSharedMarkdownTarget(rawTarget)
    return normalized.startsWith("aether-local-file://", ignoreCase = true) ||
        normalized.startsWith("file://", ignoreCase = true) ||
        normalized.startsWith("~/") ||
        normalized.startsWith('/')
}

private val SharedBareWebLinkPattern = Regex(
    "^(?:(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}|" +
        "(?:[0-9]{1,3}\\.){3}[0-9]{1,3})(?::[0-9]{1,5})?(?:[/?#].*)?$",
)

internal fun normalizeSharedAssistantLinkTarget(rawTarget: String): String {
    val normalized = normalizeSharedMarkdownTarget(rawTarget)
    if (normalized.isBlank() || isSharedWorkspaceFileLink(normalized) || normalized.contains("://")) {
        return normalized
    }
    return if (
        normalized.startsWith("www.", ignoreCase = true) ||
        SharedBareWebLinkPattern.matches(normalized)
    ) {
        "https://$normalized"
    } else {
        normalized
    }
}

internal fun normalizeSharedMarkdownTarget(rawTarget: String): String = rawTarget.trim()
    .removeSurrounding("<", ">")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

internal fun sharedMimeTypeForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "md", "markdown" -> "text/markdown"
    "txt", "log" -> "text/plain"
    "html", "htm" -> "text/html"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

internal fun sharedImagePreviewName(mimeType: String?): String = when (mimeType?.substringBefore(';')?.lowercase()) {
    "image/svg+xml" -> "image.svg"
    "image/jpeg" -> "image.jpg"
    "image/gif" -> "image.gif"
    "image/webp" -> "image.webp"
    else -> "image.png"
}

private fun String.removePrefixIgnoringCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

private fun String.normalizedSharedPathComponents(): List<String> =
    replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }

private fun ByteArray.startsWithSharedBytes(prefix: ByteArray): Boolean =
    size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

private fun decodeSharedDataPayload(value: String, errorMessage: String): String {
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            if (index + 2 >= value.length || value.substring(index + 1, index + 3).toIntOrNull(16) == null) {
                error(errorMessage)
            }
            index += 3
        } else {
            index++
        }
    }
    return decodeSharedPercentEncoding(value.replace('+', ' '))
}

private fun decodeSharedPercentEncoding(value: String): String {
    val result = StringBuilder(value.length)
    val bytes = mutableListOf<Byte>()
    fun flushBytes() {
        if (bytes.isNotEmpty()) {
            result.append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }
    }
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (byte != null) {
                bytes += byte.toByte()
                index += 3
                continue
            }
        }
        flushBytes()
        result.append(value[index])
        index += 1
    }
    flushBytes()
    return result.toString()
}
