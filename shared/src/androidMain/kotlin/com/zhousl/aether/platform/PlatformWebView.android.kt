package com.zhousl.aether.platform

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.JavascriptInterface

@Composable
actual fun PlatformWebView(
    url: String,
    html: String,
    onMessage: (String) -> Unit,
    transparentBackground: Boolean,
    scrollEnabled: Boolean,
    modifier: Modifier,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    AetherWebMessageBridge { message -> currentOnMessage(message) },
                    "Aether",
                )
            }
        },
        update = { view ->
            view.setBackgroundColor(
                if (transparentBackground) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE,
            )
            view.isVerticalScrollBarEnabled = scrollEnabled
            view.isHorizontalScrollBarEnabled = scrollEnabled
            if (url.isNotBlank() && view.url != url) {
                view.loadUrl(url)
            } else if (url.isBlank() && html.isNotBlank() && view.tag != html.hashCode()) {
                view.tag = html.hashCode()
                view.loadDataWithBaseURL("https://aether.local/", html, "text/html", "UTF-8", null)
            }
        },
    )
}

private class AetherWebMessageBridge(
    private val onMessage: (String) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        onMessage(message)
    }
}
