package com.zhousl.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeProcess
import com.zhousl.aether.runtime.RuntimeProcessSignal
import com.zhousl.aether.runtime.RuntimeProcessSpec
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.UIKit.UIApplication
import platform.darwin.NSObject

actual val platformNativeTerminalAvailable: Boolean = true

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun PlatformTerminalSurface(
    runtime: MultiplatformLocalRuntime,
    interruptSignal: Int,
    inputEvent: PlatformTerminalInputEvent?,
    darkTheme: Boolean,
    onTitleChanged: (String) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val bridge = remember(runtime, darkTheme) { IosHtermBridge(onTitleChanged, darkTheme) }
    LaunchedEffect(runtime, bridge) {
        try {
            runtime.initialize()
            val process = runtime.startProcess(
                RuntimeProcessSpec(
                    executable = "/bin/sh",
                arguments = listOf("-l"),
                environment = mapOf(
                    "HOME" to runtime.homeDirectory,
                    "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "AETHER_RUNTIME" to "alpine",
                    "AETHER_HOST_WORKSPACE" to runtime.workspaceRoot,
                    "PS1" to "aether-alpine:\\w# ",
                    "TERM" to "xterm-256color",
                    "COLORTERM" to "truecolor",
                ),
                workingDirectory = runtime.homeDirectory,
                    interactiveTerminal = true,
                )
            )
            bridge.attach(process)
            onReady()
            coroutineScope {
                launch { process.stdout.collect(bridge::write) }
                launch { process.stderr.collect(bridge::write) }
                process.awaitExit()
            }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            onError(failure.message ?: "Unable to start Alpine terminal.")
        }
    }
    DisposableEffect(bridge) {
        onDispose { bridge.close() }
    }
    LaunchedEffect(interruptSignal) {
        if (interruptSignal > 0) bridge.interrupt()
    }
    LaunchedEffect(inputEvent?.sequence) {
        inputEvent?.let { event ->
            if (event.text.isNotEmpty()) bridge.send(event.text)
            event.key?.let { key -> bridge.sendKey(key, event.controlDown, event.altDown) }
            if (event.requestFocus) bridge.focus()
        }
    }
    UIKitView(
        modifier = modifier,
        factory = { bridge.webView },
        update = { bridge.focus() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosHtermBridge(
    private val onTitleChanged: (String) -> Unit,
    private val darkTheme: Boolean,
) : NSObject(), WKScriptMessageHandlerProtocol {
    private val scope = kotlinx.coroutines.MainScope()
    private var process: RuntimeProcess? = null
    private var loaded = false
    private val pending = mutableListOf<ByteArray>()
    private val configuration = WKWebViewConfiguration().apply {
        listOf("load", "sendInput", "resize", "propUpdate", "focus", "syncFocus", "newScrollHeight", "newScrollTop", "openLink")
            .forEach { userContentController.addScriptMessageHandler(this@IosHtermBridge, it) }
    }
    val webView = WKWebView(
        frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
        configuration = configuration,
    ).apply {
        opaque = false
        backgroundColor = platform.UIKit.UIColor.clearColor
        scrollView.backgroundColor = platform.UIKit.UIColor.clearColor
        val resource = NSBundle.mainBundle.URLForResource("term", "html")
        if (resource != null) loadFileURL(resource, allowingReadAccessToURL = resource.URLByDeletingLastPathComponent ?: resource)
    }

    fun attach(process: RuntimeProcess) {
        this.process = process
    }

    fun write(bytes: ByteArray) {
        if (!loaded) {
            pending += bytes.copyOf()
            return
        }
        val latin1 = buildString(bytes.size) { bytes.forEach { append((it.toInt() and 0xff).toChar()) } }
        webView.evaluateJavaScript("exports.write(${JsonPrimitive(latin1)})", null)
    }

    fun focus() {
        if (loaded) webView.evaluateJavaScript("exports.setFocused(true);term.scrollPort_.screen_.contentEditable=true;term.focus()", null)
    }

    suspend fun interrupt() {
        process?.signal(RuntimeProcessSignal.Interrupt)
    }

    suspend fun send(text: String) {
        process?.writeStdin(text.encodeToByteArray())
    }

    fun sendKey(key: PlatformTerminalKey, controlDown: Boolean, altDown: Boolean) {
        webView.evaluateJavaScript(
            "exports.sendKey(${JsonPrimitive(key.name)},$controlDown,$altDown)",
            null,
        )
    }

    fun close() {
        val running = process
        process = null
        if (running != null) {
            scope.launch { running.signal(RuntimeProcessSignal.Terminate) }
        }
        scope.cancel()
        configuration.userContentController.removeAllScriptMessageHandlers()
    }

    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        when (didReceiveScriptMessage.name) {
            "load" -> {
                loaded = true
                val foregroundColor = if (darkTheme) "#ffffff" else "#000000"
                val backgroundColor = if (darkTheme) "#000000" else "#ffffff"
                webView.evaluateJavaScript(
                    "exports.updateStyle({foregroundColor:'$foregroundColor',backgroundColor:'$backgroundColor',fontFamily:'JetBrains Mono, ui-monospace, Menlo, monospace',fontSize:12,colorPaletteOverrides:{},blinkCursor:true,cursorShape:'BLOCK'});term.scrollPort_.screen_.contentEditable=true;term.focus()",
                    null,
                )
                val buffered = pending.toList()
                pending.clear()
                buffered.forEach(::write)
            }
            "sendInput" -> {
                val text = didReceiveScriptMessage.body as? String ?: return
                val target = process ?: return
                scope.launch { target.writeStdin(text.encodeToByteArray()) }
            }
            "propUpdate" -> {
                val values = didReceiveScriptMessage.body as? List<*> ?: return
                if (values.getOrNull(0) == "title") {
                    onTitleChanged(values.getOrNull(1) as? String ?: "")
                }
            }
            "resize" -> {
                val target = process ?: return
                webView.evaluateJavaScript("JSON.stringify(exports.getSize())") { value, _ ->
                    val dimensions = (value as? String)
                        ?.removePrefix("[")
                        ?.removeSuffix("]")
                        ?.split(',')
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        .orEmpty()
                    if (dimensions.size == 2) {
                        scope.launch { target.resize(dimensions[0], dimensions[1]) }
                    }
                }
            }
            "openLink" -> {
                val url = didReceiveScriptMessage.body as? String ?: return
                NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
            }
        }
    }
}
