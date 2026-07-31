package com.zhousl.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.darwin.NSObject

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun PlatformWebView(
    url: String,
    html: String,
    onMessage: (String) -> Unit,
    transparentBackground: Boolean,
    scrollEnabled: Boolean,
    modifier: Modifier,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    key(url, html, transparentBackground, scrollEnabled) {
        UIKitView(
            modifier = modifier,
            factory = {
                val userContent = WKUserContentController().apply {
                    addScriptMessageHandler(
                        AetherWebMessageHandler { message -> currentOnMessage(message) },
                        name = "Aether",
                    )
                    addUserScript(
                        WKUserScript(
                            source = "window.Aether={postMessage:function(message){window.webkit.messageHandlers.Aether.postMessage(message);}};",
                            injectionTime =
                                WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                            forMainFrameOnly = false,
                        )
                    )
                }
                WKWebView(
                    frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                    configuration = WKWebViewConfiguration().apply {
                        userContentController = userContent
                    },
                ).apply {
                    allowsBackForwardNavigationGestures = true
                    opaque = !transparentBackground
                    if (transparentBackground) {
                        backgroundColor = UIColor.clearColor
                        scrollView.backgroundColor = UIColor.clearColor
                    }
                    scrollView.scrollEnabled = scrollEnabled
                    scrollView.bounces = scrollEnabled
                    if (url.isNotBlank()) {
                        NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(it)) }
                    } else if (html.isNotBlank()) {
                        loadHTMLString(html, baseURL = NSURL.URLWithString("https://aether.local/"))
                    }
                }
            },
        )
    }
}

private class AetherWebMessageHandler(
    private val onMessage: (String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        (didReceiveScriptMessage.body as? String)?.let(onMessage)
    }
}
