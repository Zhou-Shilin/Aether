package com.zhousl.aether.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.zhousl.aether.data.DocumentExports
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.zhousl.aether.data.HostedApp
import com.zhousl.aether.data.HostedApps
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Owns a bounded WebView directly, not the extension renderer's height fallback.
 * Stable app-specific HTTPS origins scope extension capability requests.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HostedAppsOverlay(manager: HostedApps, selected: HostedApp, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var importReply by remember(selected.id, selected.version) { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var saveReply by remember(selected.id, selected.version) { mutableStateOf<CancellableContinuation<Uri?>?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        importReply?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)); importReply = null
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        saveReply?.let { if (it.isActive) it.resume(uri) }; saveReply = null
    }
    val exports = remember(selected.id, selected.version) { DocumentExports(context) { name ->
        suspendCancellableCoroutine { continuation ->
            check(saveReply == null) { "Another save dialog is open." }
            saveReply = continuation
            continuation.invokeOnCancellation { saveReply = null }
            saveLauncher.launch(name)
        }
    } }
    var status by remember(selected.id, selected.version) { mutableStateOf("Opening local app…") }
    var webView by remember(selected.id, selected.version) { mutableStateOf<WebView?>(null) }
    val requests = remember(selected.id, selected.version) { mutableMapOf<String, Job>() }
    val seen = remember(selected.id, selected.version) { LinkedHashSet<String>() }
    val colors = MaterialTheme.colorScheme
    fun cssColor(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
    val theme = JSONObject().put("primary", cssColor(colors.primary)).put("onPrimary", cssColor(colors.onPrimary))
        .put("container", cssColor(colors.primaryContainer)).put("onContainer", cssColor(colors.onPrimaryContainer))
        .put("background", cssColor(colors.background)).put("surface", cssColor(colors.surfaceContainerLow))
        .put("text", cssColor(colors.onSurface)).put("muted", cssColor(colors.onSurfaceVariant)).put("line", cssColor(colors.outlineVariant))
        .put("errorContainer", cssColor(colors.errorContainer)).put("onErrorContainer", cssColor(colors.onErrorContainer))
    var webViewReady by remember(manager) { mutableStateOf(false) }
    LaunchedEffect(manager) {
        try { manager.prepareWebView().await(); webViewReady = true }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { status = "Android WebView could not initialize. Close and reopen Aether; saved app data is retained." }
    }
    // Feature probes and construction must follow async startup, or they can force
    // its remaining initialization work back onto the main thread.
    val compatible = !webViewReady || WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    BackHandler {
        val view = webView
        if (view == null) onClose()
        else view.evaluateJavascript("""(function(){
            const dialog = document.querySelector('dialog[open]');
            if (dialog) { dialog.close(); return true; }
            const event = new Event('aether:back', {cancelable:true});
            if (!window.dispatchEvent(event)) return true;
            return false;
        })()""") { handled ->
            if (handled != "true") { if (view.canGoBack()) view.goBack() else onClose() }
        }
    }
    DisposableEffect(selected.id, selected.version, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val detach = manager.attach(selected)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detach()
            importReply?.onReceiveValue(null); importReply = null
            saveReply?.cancel(); saveReply = null; exports.close()
            requests.values.toList().forEach { it.cancel() }; requests.clear()
            // AndroidView.onRelease destroys the view after detaching it.
            webView = null
        }
    }
    key(selected.id, selected.version) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (status.isNotBlank()) Text(status, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
            if (!webViewReady) return@Column
            if (!compatible) {
                Text("This Android WebView lacks the required origin-checked app bridge. Update Android System WebView; no insecure JavaScript-interface fallback was enabled.", Modifier.padding(16.dp))
                return@Column
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context -> WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            importReply?.onReceiveValue(null); importReply = callback
                            return try { importLauncher.launch(params.createIntent()); true } catch (_: Exception) { importReply = null; callback.onReceiveValue(null); true }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (!manager.accepts(request.url.host)) return null
                            if (request.url.scheme != "https" || request.url.host != Uri.parse(selected.origin).host || request.method != "GET") return errorResponse(403, "App origin mismatch.")
                            return assetResponse(selected, request.url.path ?: "/")
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // Other apps, token URLs and external navigation never inherit this bridge.
                            return request.isForMainFrame && (request.url.scheme != "https" || request.url.host != Uri.parse(selected.origin).host)
                        }
                        override fun onPageFinished(view: WebView, url: String) { status = "" }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) status = "Local app could not load: ${error.errorCode}. Close and update its assets from Apps; data was not cleared."
                        }
                    }
                    // Unlike addJavascriptInterface, WebMessageListener identifies the true
                    // frame origin. Opaque/untrusted generated iframes cannot call the AI host.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        val info = selected.json().put("theme", theme)
                        WebViewCompat.addDocumentStartJavaScript(this,
                            "Object.defineProperty(globalThis,'AetherAppInfo',{value:Object.freeze(JSON.parse(" + JSONObject.quote(info.toString()) + "))});",
                            setOf(selected.origin))
                    }
                    WebViewCompat.addWebMessageListener(this, "AetherHosted", setOf(selected.origin)) { _, message, sourceOrigin, isMainFrame, reply ->
                        if (!isMainFrame || sourceOrigin.toString().trimEnd('/') != selected.origin) return@addWebMessageListener
                        val raw = message.data ?: return@addWebMessageListener
                        if (raw.toByteArray().size > 1024 * 1024) return@addWebMessageListener
                        val input = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
                        val id = input.optString("id")
                        if (!Regex("^[a-fA-F0-9-]{36}$").matches(id)) return@addWebMessageListener
                        fun respond(value: JSONObject) { reply.postMessage(value.put("id", id).toString()) }
                        if (input.optString("op") == "cancel") {
                            requests.remove(input.optString("target"))?.cancel()
                            respond(JSONObject().put("ok", true).put("result", JSONObject().put("cancelled", true)))
                            return@addWebMessageListener
                        }
                        if (!seen.add(id) || requests.size >= 8) { respond(JSONObject().put("ok", false).put("error", "Duplicate request or app request queue bound reached.")); return@addWebMessageListener }
                        if (seen.size > 256) seen.remove(seen.first())
                        val job = scope.launch(start = CoroutineStart.LAZY) {
                            try {
                                val args = input.optJSONObject("args") ?: JSONObject()
                                val result = when (input.optString("op")) {
                                    "export.begin", "export.chunk", "export.finish", "export.cancel" -> exports.invoke(input.getString("op").removePrefix("export."), args)
                                    "app.info" -> selected.json().put("managed", true).put("protocol", 1).put("storage", "shared-project-folder").put("theme", theme)
                                    else -> if(input.optString("op").startsWith("files.")) manager.fileRequest(selected, input.getString("op").removePrefix("files."), args)
                                        else manager.extensionRequest(selected, input.optString("op"), args)
                                }
                                require(result.toString().toByteArray().size <= 1024 * 1024) { "App reply exceeds the 1 MiB response budget." }
                                respond(JSONObject().put("ok", true).put("result", result))
                            } catch (_: kotlinx.coroutines.CancellationException) {
                                runCatching { respond(JSONObject().put("ok", false).put("error", "App request cancelled or timed out. No fallback or automatic retry.")) }
                            } catch (error: IllegalArgumentException) {
                                runCatching { respond(JSONObject().put("ok", false).put("error", error.message?.take(600) ?: "Invalid app request.")) }
                            } catch (error: IllegalStateException) {
                                runCatching { respond(JSONObject().put("ok", false).put("error", error.message?.take(600) ?: "App capability failed.")) }
                            } catch (_: Exception) {
                                runCatching { respond(JSONObject().put("ok", false).put("error", "Aether could not complete this app request. Check its selected provider and permission; credentials were not exposed or replaced.")) }
                            } finally { requests.remove(id) }
                        }
                        requests[id] = job
                        job.start()
                    }
                    loadUrl(selected.origin + "/index.html")
                } },
                onRelease = { view -> view.stopLoading(); view.removeAllViews(); view.destroy() },
                update = { /* Immutable version/origin owns this WebView; never reload on token deltas. */ },
            )
        }
    }
    }
}

private fun errorResponse(code: Int, text: String) = WebResourceResponse("text/plain", "UTF-8", code, "Local app request refused", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(text.toByteArray()))
private fun assetResponse(app: HostedApp, requested: String): WebResourceResponse {
    return runCatching {
        require(!requested.contains('\\') && !requested.contains('\u0000') && requested.split('/').none { it == ".." })
        val asset = File(app.assets, requested.removePrefix("/")).canonicalFile
        val route = if (requested == "/" || (!asset.isFile && !requested.substringAfterLast('/').contains('.'))) "/index.html" else requested
        val file = File(app.assets, route.removePrefix("/")).canonicalFile
        require(file.path.startsWith(app.assets.canonicalPath + File.separator) && file.isFile)
        val mime = when (file.extension.lowercase()) {
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json", "webmanifest" -> "application/json"
            "wasm" -> "application/wasm"
            "html" -> "text/html"
            else -> URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        }
        WebResourceResponse(mime, if (mime.startsWith("text/") || mime == "application/json") "UTF-8" else null, 200, "OK", mapOf(
            "Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff", "Referrer-Policy" to "no-referrer",
            "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data: blob:; font-src 'self' data:; worker-src 'self' blob:; frame-src 'self' data: blob:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'",
        ), file.inputStream())
    }.getOrElse { errorResponse(404, "Local asset is unavailable. Update this app's hosted assets; its database was not cleared.") }
}
