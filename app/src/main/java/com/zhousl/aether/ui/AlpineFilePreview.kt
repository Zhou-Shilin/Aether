package com.zhousl.aether.ui

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import android.webkit.MimeTypeMap
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AlpinePreviewKind { Pdf, Image, Media, Text, External }

internal fun alpinePreviewKind(name: String, prefix: ByteArray): AlpinePreviewKind {
    val extension = name.substringAfterLast('.', "").lowercase()
    val header = prefix.take(1024).toByteArray().toString(Charsets.ISO_8859_1)
    if (extension == "pdf" || header.trimStart().startsWith("%PDF-")) return AlpinePreviewKind.Pdf
    val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff")
    if (extension in imageExtensions || header.startsWith("\u0089PNG") || header.startsWith("GIF8") ||
        (prefix.size >= 3 && prefix[0] == 0xff.toByte() && prefix[1] == 0xd8.toByte() && prefix[2] == 0xff.toByte()) ||
        (header.startsWith("RIFF") && header.substring(8).startsWith("WEBP"))) return AlpinePreviewKind.Image
    if (extension in setOf("mp4", "m4v", "mov", "webm", "3gp", "mp3", "m4a", "aac", "wav", "ogg", "flac")) return AlpinePreviewKind.Media
    if (extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "zip", "7z", "gz", "tar")) return AlpinePreviewKind.External
    // Never offer the text editor for a binary file: saving it would corrupt it.
    if (prefix.any { it == 0.toByte() }) return AlpinePreviewKind.External
    return AlpinePreviewKind.Text
}

internal data class AlpineFilePreview(val name: String, val file: File, val kind: AlpinePreviewKind)

internal fun openAlpineFileWithOtherApp(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    check(intent.resolveActivity(context.packageManager) != null) { "No installed app can open this file." }
    context.startActivity(Intent.createChooser(intent, "Open with another app"))
}

@Composable
internal fun AlpineFilePreviewScreen(preview: AlpineFilePreview, onBack: () -> Unit) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(preview.file) { onDispose { preview.file.parentFile?.deleteRecursively() } }
    fun openExternally() {
        runCatching {
            openAlpineFileWithOtherApp(context, preview.file)
        }.onFailure { openError = "No installed app can open this file." }
    }
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(preview.name, Modifier.weight(1f).padding(top = 12.dp), maxLines = 1)
            IconButton(onClick = ::openExternally) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open with another app") }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (preview.kind) {
                AlpinePreviewKind.Pdf -> AlpinePdfPreview(preview.file)
                AlpinePreviewKind.Image -> AlpineImagePreview(preview.file)
                AlpinePreviewKind.Media -> AlpineMediaPreview(preview.file)
                else -> Column(Modifier.padding(24.dp)) {
                    Text("Preview unavailable for this format.")
                    TextButton(onClick = ::openExternally) { Text("Open with another app") }
                }
            }
        }
    }
    openError?.let { message ->
        AlertDialog(onDismissRequest = { openError = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { openError = null }) { Text("OK") } })
    }
}

private class OpenPdf(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Throwable) { descriptor.close(); throw error }
    val pageCount = renderer.pageCount
    private var closed = false
    @Synchronized fun render(index: Int): Bitmap {
        check(!closed) { "PDF preview closed." }
        return renderer.openPage(index).use { page ->
            val width = minOf(page.width * 2, 1600)
            val height = (page.height.toDouble() / page.width * width).toInt().coerceIn(1, 4096)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
    @Synchronized override fun close() {
        if (!closed) { closed = true; renderer.close(); descriptor.close() }
    }
}

@Composable
private fun AlpinePdfPreview(file: File) {
    val opened = remember(file) { runCatching { OpenPdf(file) } }
    val pdf = opened.getOrNull()
    val error = opened.exceptionOrNull()?.message
    DisposableEffect(file) { onDispose { pdf?.close() } }
    val document = pdf
    when {
        error != null -> Text(error.orEmpty(), Modifier.padding(24.dp))
        document == null -> CircularProgressIndicator(Modifier.padding(24.dp))
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
            items((0 until document.pageCount).toList()) { index ->
                var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }
                var pageError by remember(document, index) { mutableStateOf<String?>(null) }
                LaunchedEffect(document, index) {
                    runCatching { withContext(Dispatchers.IO) { document.render(index) } }
                        .onSuccess { bitmap = it }.onFailure { pageError = it.message }
                }
                Column {
                    Text("${index + 1} / ${document.pageCount}", style = MaterialTheme.typography.labelSmall)
                    bitmap?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        ?: if (pageError != null) Text(pageError.orEmpty()) else CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun AlpineImagePreview(file: File) {
    if (file.extension.equals("svg", ignoreCase = true)) {
        LocalMediaWebView(file, media = false)
        return
    }
    val context = LocalContext.current
    var drawable by remember(file) { mutableStateOf<Drawable?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var zoom by remember(file) { mutableStateOf(1f) }
    var pan by remember(file) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { scale, offset, _ ->
        zoom = (zoom * scale).coerceIn(1f, 5f)
        pan = if (zoom == 1f) Offset.Zero else pan + offset
    }
    LaunchedEffect(file) {
        runCatching {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val factor = minOf(1f, 4096f / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize((info.size.width * factor).toInt().coerceAtLeast(1), (info.size.height * factor).toInt().coerceAtLeast(1))
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 4096) inSampleSize *= 2
                    }
                    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("Unsupported image format.")
                    android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                }
            }
        }.onSuccess { drawable = it }.onFailure { error = it.message ?: "Unable to decode image." }
    }
    when {
        error != null -> Text(error.orEmpty(), Modifier.padding(24.dp))
        drawable == null -> CircularProgressIndicator(Modifier.padding(24.dp))
        else -> Box(Modifier.fillMaxSize().clipToBounds()) {
            AndroidView(modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
            }.transformable(transform), factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { view ->
                    if (view.drawable !== drawable) {
                        view.setImageDrawable(drawable)
                        if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.start()
                    }
                }, onRelease = { view ->
                    if (Build.VERSION.SDK_INT >= 28) (view.drawable as? AnimatedImageDrawable)?.stop()
                    view.setImageDrawable(null)
                })
        }
    }
}

@Composable
private fun AlpineMediaPreview(file: File) = LocalMediaWebView(file, media = true)

@Composable
private fun LocalMediaWebView(file: File, media: Boolean) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            val source = Uri.fromFile(file).toString().replace("&", "&amp;").replace("\"", "&quot;")
            val element = if (media) "<video controls style=\"width:100%;max-height:90vh\" src=\"$source\"></video>"
                else "<img style=\"max-width:100%;height:auto\" src=\"$source\" />"
            loadDataWithBaseURL(Uri.fromFile(file.parentFile).toString() + "/", "<html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><body style=\"margin:0;background:#202020;display:flex;align-items:center;justify-content:center;min-height:100vh\">$element</body></html>", "text/html", "UTF-8", null)
        }
    }, onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() })
}
