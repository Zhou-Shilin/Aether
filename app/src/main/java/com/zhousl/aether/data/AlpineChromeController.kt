package com.zhousl.aether.data

import android.content.Context
import android.util.Base64
import com.zhousl.aether.runtime.AlpineRuntime
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

private const val ChromeCdpPort = 9222
private const val ChromeVncPort = 5900
private const val ChromeNoVncPort = 6080
private const val ChromeViewportWidth = 1080
private const val ChromeViewportHeight = 2040
private const val ChromeBrowserUiKeyboardMinY = 34
private const val ChromeBrowserUiKeyboardMaxY = 100
private const val ChromeStartupTimeoutMillis = 25_000L
private const val ChromeCommandTimeoutMillis = 12_000L
private const val ChromeScreenshotQuality = 82
internal const val AlpineChromeViewerUrl =
    "http://127.0.0.1:$ChromeNoVncPort/vnc_lite.html" +
        "?autoconnect=true&scale=true&show_dot=true&path=websockify"

class AlpineChromeController(
    context: Context,
    private val alpineRuntime: AlpineRuntime,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val cacheDirectory = File(appContext.cacheDir, "alpine-chrome").apply { mkdirs() }
    private val captureDirectory = File(appContext.filesDir, "alpine-chrome/captures").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val _displayState = MutableStateFlow(
        AgentModeDisplayState(
            width = ChromeViewportWidth,
            height = ChromeViewportHeight,
            status = "Chrome is stopped.",
        )
    )

    @Volatile
    private var chromeProcess: Process? = null
    @Volatile
    private var cdpSession: CdpSession? = null

    val displayState: StateFlow<AgentModeDisplayState> = _displayState.asStateFlow()

    suspend fun execute(argumentsJson: String): String = operationMutex.withLock {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return@withLock errorResult("Arguments were not valid JSON.")
        val action = arguments.optString("action").trim().lowercase()
        runCatching {
            when (action) {
                "start" -> {
                    ensureStarted()
                    captureResult("Chrome started.")
                }
                "status" -> {
                    if (isRunning()) captureResult("Chrome is running.") else statusResult()
                }
                "navigate", "open" -> {
                    val url = normalizeUrl(arguments.optString("url"))
                    if (url.isBlank()) {
                        errorResult("Missing required 'url' argument.")
                    } else {
                        ensureStarted().send("Page.navigate", JSONObject().put("url", url))
                        delay(650)
                        captureResult("Opened $url")
                    }
                }
                "tap" -> {
                    val x = normalizedCoordinate(arguments, "x", ChromeViewportWidth)
                    val y = normalizedCoordinate(arguments, "y", ChromeViewportHeight)
                    if (x == null || y == null) {
                        errorResult("Both 'x' and 'y' are required, using 0..1000 screen coordinates.")
                    } else {
                        dispatchTap(ensureStarted(), x, y)
                        updateCursor(x, y, 180)
                        delay(280)
                        captureResult("Tapped Chrome.")
                    }
                }
                "swipe", "scroll" -> {
                    val x1 = normalizedCoordinate(arguments, "x1", ChromeViewportWidth)
                        ?: ChromeViewportWidth / 2
                    val y1 = normalizedCoordinate(arguments, "y1", ChromeViewportHeight)
                    val x2 = normalizedCoordinate(arguments, "x2", ChromeViewportWidth)
                        ?: ChromeViewportWidth / 2
                    val y2 = normalizedCoordinate(arguments, "y2", ChromeViewportHeight)
                    if (y1 == null || y2 == null) {
                        errorResult("y1 and y2 are required, using 0..1000 screen coordinates.")
                    } else {
                        dispatchScroll(ensureStarted(), x1, y1, x2, y2)
                        updateCursor(x2, y2, 300)
                        delay(320)
                        captureResult("Scrolled Chrome.")
                    }
                }
                "text", "type" -> {
                    val text = arguments.optString("text")
                    if (text.isEmpty()) {
                        errorResult("Missing required 'text' argument.")
                    } else {
                        ensureStarted().send("Input.insertText", JSONObject().put("text", text))
                        delay(180)
                        captureResult("Typed in Chrome.")
                    }
                }
                "key" -> {
                    val key = arguments.optString("key").trim()
                    if (key.isBlank()) {
                        errorResult("Missing required 'key' argument.")
                    } else {
                        dispatchKey(ensureStarted(), key)
                        delay(220)
                        captureResult("Pressed $key in Chrome.")
                    }
                }
                "back" -> {
                    navigateHistory(ensureStarted(), -1)
                    delay(450)
                    captureResult("Went back in Chrome.")
                }
                "forward" -> {
                    navigateHistory(ensureStarted(), 1)
                    delay(450)
                    captureResult("Went forward in Chrome.")
                }
                "reload" -> {
                    ensureStarted().send("Page.reload", JSONObject().put("ignoreCache", false))
                    delay(550)
                    captureResult("Reloaded Chrome.")
                }
                "evaluate" -> {
                    val expression = arguments.optString("expression")
                    if (expression.isBlank()) {
                        errorResult("Missing required 'expression' argument.")
                    } else {
                        val result = ensureStarted().send(
                            "Runtime.evaluate",
                            JSONObject()
                                .put("expression", expression)
                                .put("returnByValue", true)
                                .put("awaitPromise", true),
                        )
                        captureResult(
                            message = "Evaluated JavaScript in Chrome.",
                            extra = JSONObject().put(
                                "result",
                                result.optJSONObject("result")?.opt("value") ?: JSONObject.NULL,
                            ),
                        )
                    }
                }
                "screenshot" -> {
                    ensureStarted()
                    captureResult("Captured Chrome.")
                }
                "stop" -> stopLocked()
                else -> errorResult(
                    "Unsupported action '$action'. Use start, status, navigate, tap, swipe, text, key, back, forward, reload, evaluate, screenshot, or stop."
                )
            }
        }.getOrElse { throwable ->
            diagnosticLogger.exception(
                category = "alpine_chrome",
                event = "action_failed",
                throwable = throwable,
                details = mapOf("action" to action),
            )
            errorResult(throwable.message ?: "Chrome action failed.")
        }
    }

    suspend fun startBrowser(): Result<Unit> = runCatching {
        operationMutex.withLock {
            ensureStarted()
            capturePreview()
        }
    }

    suspend fun shouldShowKeyboardAfterClick(x: Int, y: Int): Result<Boolean> = runCatching {
        operationMutex.withLock {
            ensureStarted()
            if (y in ChromeBrowserUiKeyboardMinY..ChromeBrowserUiKeyboardMaxY) {
                return@withLock true
            }
            delay(140)
            val result = cdpSession?.send(
                "Runtime.evaluate",
                JSONObject()
                    .put(
                        "expression",
                        """
                            (() => {
                              const element = document.activeElement;
                              return Boolean(
                                element &&
                                !element.disabled &&
                                !element.readOnly &&
                                (
                                  element.isContentEditable ||
                                  element.tagName === 'INPUT' ||
                                  element.tagName === 'TEXTAREA' ||
                                  element.tagName === 'SELECT'
                                )
                              );
                            })()
                        """.trimIndent(),
                    )
                    .put("returnByValue", true),
            )
            result
                ?.optJSONObject("result")
                ?.optBoolean("value", false)
                ?: false
        }
    }

    suspend fun stop(): Result<Unit> = runCatching {
        operationMutex.withLock { stopLocked() }
    }

    private suspend fun ensureStarted(): CdpSession {
        cdpSession?.takeIf { it.isOpen && isRunning() }?.let { return it }
        cdpSession?.close()
        cdpSession = null

        if (chromeProcess?.isAlive != true) {
            val command = """
                set -eu
                export DISPLAY=:99
                mkdir -p /root/.aether/chrome-profile /tmp/.X11-unix
                rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
                rm -f \
                    /root/.aether/chrome-profile/SingletonLock \
                    /root/.aether/chrome-profile/SingletonSocket \
                    /root/.aether/chrome-profile/SingletonCookie
                CHROME_BIN="${'$'}(command -v chromium-browser || command -v chromium)"
                test -n "${'$'}CHROME_BIN"
                cleanup() {
                    for pid in "${'$'}{CHROME_PID:-}" "${'$'}{NOVNC_PID:-}" "${'$'}{OPENBOX_PID:-}" "${'$'}{VNC_PID:-}"; do
                        if [ -n "${'$'}pid" ]; then kill "${'$'}pid" 2>/dev/null || true; fi
                    done
                }
                trap cleanup EXIT INT TERM
                Xvnc "${'$'}DISPLAY" \
                    -geometry ${ChromeViewportWidth}x${ChromeViewportHeight} \
                    -depth 24 \
                    -SecurityTypes None \
                    -localhost \
                    -rfbport $ChromeVncPort \
                    -AlwaysShared \
                    -extension MIT-SHM \
                    -nolock \
                    -ac &
                VNC_PID=${'$'}!
                for attempt in ${'$'}(seq 1 50); do
                    if [ -S /tmp/.X11-unix/X99 ]; then break; fi
                    sleep 0.1
                done
                test -S /tmp/.X11-unix/X99
                openbox &
                OPENBOX_PID=${'$'}!
                "${'$'}CHROME_BIN" \
                    --no-sandbox \
                    --disable-dev-shm-usage \
                    --disable-gpu \
                    --disable-gpu-compositing \
                    --disable-gpu-rasterization \
                    --no-first-run \
                    --no-default-browser-check \
                    --password-store=basic \
                    --remote-debugging-address=127.0.0.1 \
                    --remote-debugging-port=$ChromeCdpPort \
                    --remote-allow-origins=* \
                    --user-data-dir=/root/.aether/chrome-profile \
                    --window-size=$ChromeViewportWidth,$ChromeViewportHeight \
                    --window-position=0,0 \
                    --start-maximized \
                    --ozone-platform=x11 \
                    about:blank &
                CHROME_PID=${'$'}!
                CHROME_WINDOW_READY=
                for attempt in ${'$'}(seq 1 100); do
                    if ! kill -0 "${'$'}CHROME_PID" 2>/dev/null; then
                        wait "${'$'}CHROME_PID"
                    fi
                    if xprop -root _NET_CLIENT_LIST 2>/dev/null | grep -q '0x'; then
                        CHROME_WINDOW_READY=1
                        break
                    fi
                    sleep 0.1
                done
                test -n "${'$'}CHROME_WINDOW_READY"
                websockify --web=/usr/share/novnc 127.0.0.1:$ChromeNoVncPort 127.0.0.1:$ChromeVncPort &
                NOVNC_PID=${'$'}!
                wait "${'$'}CHROME_PID"
            """.trimIndent()
            chromeProcess = alpineRuntime.startManagedProcess(
                command = command,
                workingDirectory = alpineRuntime.homeDirectory,
                redirectErrorStream = true,
            ).also(::drainProcessOutput)
        }

        waitForNoVnc()
        val target = waitForPageTarget()
        val session = CdpSession(target.getString("webSocketDebuggerUrl"))
        session.connect()
        session.send("Page.enable", JSONObject())
        session.send("Runtime.enable", JSONObject())
        session.send(
            "Emulation.setDeviceMetricsOverride",
            JSONObject()
                .put("width", ChromeViewportWidth)
                .put("height", ChromeViewportHeight)
                .put("deviceScaleFactor", 1)
                .put("mobile", false),
        )
        cdpSession = session
        _displayState.value = _displayState.value.copy(
            isActive = true,
            status = "Chrome is running.",
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        return session
    }

    private suspend fun waitForNoVnc() {
        val deadline = System.currentTimeMillis() + ChromeStartupTimeoutMillis
        var lastError = ""
        while (System.currentTimeMillis() < deadline) {
            if (chromeProcess?.isAlive != true) {
                delay(100)
                error(
                    chromeProcessFailureMessage(
                        "Chromium display exited before noVNC became available."
                    )
                )
            }
            runCatching {
                requestNoVncPage()
                requestVncPort()
            }.onSuccess {
                return
            }.onFailure {
                lastError = it.message.orEmpty()
            }
            delay(200)
        }
        error(lastError.ifBlank { "Timed out waiting for the Chrome noVNC viewer." })
    }

    private suspend fun requestNoVncPage() = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder()
                .url("http://127.0.0.1:$ChromeNoVncPort/vnc_lite.html")
                .build()
        ).execute().use { response ->
            require(response.isSuccessful) { "noVNC HTTP ${response.code}" }
        }
    }

    private suspend fun requestVncPort() = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress("127.0.0.1", ChromeVncPort),
                1_000,
            )
        }
    }

    private suspend fun waitForPageTarget(): JSONObject {
        val deadline = System.currentTimeMillis() + ChromeStartupTimeoutMillis
        var lastError = ""
        while (System.currentTimeMillis() < deadline) {
            if (chromeProcess?.isAlive != true) {
                delay(100)
                error(chromeProcessFailureMessage("Chromium exited before CDP became available."))
            }
            runCatching {
                requestJsonArray("http://127.0.0.1:$ChromeCdpPort/json/list")
            }.onSuccess { targets ->
                for (index in 0 until targets.length()) {
                    val target = targets.optJSONObject(index) ?: continue
                    if (
                        target.optString("type") == "page" &&
                        target.optString("webSocketDebuggerUrl").isNotBlank()
                    ) {
                        return target
                    }
                }
            }.onFailure { lastError = it.message.orEmpty() }
            delay(200)
        }
        error(lastError.ifBlank { "Timed out waiting for Chromium CDP." })
    }

    private fun chromeProcessFailureMessage(prefix: String): String {
        val logTail = runCatching {
            File(cacheDirectory, "chromium.log")
                .takeIf(File::isFile)
                ?.readText()
                ?.trim()
                ?.takeLast(1_200)
                .orEmpty()
        }.getOrDefault("")
        return if (logTail.isBlank()) {
            "$prefix Check the Alpine Chrome installation."
        } else {
            "$prefix\n$logTail"
        }
    }

    private suspend fun captureResult(
        message: String,
        extra: JSONObject = JSONObject(),
    ): String {
        val screenshot = capturePreview()
        val persistedScreenshot = File(
            captureDirectory,
            "chrome-${System.currentTimeMillis()}-${System.nanoTime()}.jpg",
        )
        screenshot.first.copyTo(persistedScreenshot, overwrite = true)
        val pageInfo = pageInfo()
        return JSONObject().apply {
            put("ok", true)
            put("stdout", message)
            put("url", pageInfo.first)
            put("title", pageInfo.second)
            put("width", ChromeViewportWidth)
            put("height", ChromeViewportHeight)
            put("screenshot_path", persistedScreenshot.absolutePath)
            put("preview_path", persistedScreenshot.absolutePath)
            put("screenshot_mime_type", "image/jpeg")
            put("screenshot_base64", screenshot.second)
            _displayState.value.cursorX?.let { put("cursor_x", it) }
            _displayState.value.cursorY?.let { put("cursor_y", it) }
            extra.keys().forEach { key -> put(key, extra.opt(key)) }
        }.toString()
    }

    private suspend fun capturePreview(): Pair<File, String> {
        val session = cdpSession ?: error("Chrome CDP is not connected.")
        val result = session.send(
            "Page.captureScreenshot",
            JSONObject()
                .put("format", "jpeg")
                .put("quality", ChromeScreenshotQuality)
                .put("fromSurface", true)
                .put("captureBeyondViewport", false),
        )
        val encoded = result.optString("data")
        require(encoded.isNotBlank()) { "Chrome returned an empty screenshot." }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val previewFile = File(cacheDirectory, "chrome-preview.jpg")
        val stagingFile = File(cacheDirectory, "chrome-preview.tmp")
        stagingFile.writeBytes(bytes)
        if (previewFile.exists()) previewFile.delete()
        require(stagingFile.renameTo(previewFile)) { "Unable to update Chrome preview." }
        _displayState.value = _displayState.value.copy(
            isActive = true,
            latestPreviewPath = previewFile.absolutePath,
            latestWorkspacePath = "",
            lastUpdatedMillis = System.currentTimeMillis(),
            status = "Chrome is running.",
        )
        return previewFile to encoded
    }

    private suspend fun pageInfo(): Pair<String, String> {
        val result = cdpSession?.send(
            "Runtime.evaluate",
            JSONObject()
                .put("expression", "JSON.stringify({url: location.href, title: document.title})")
                .put("returnByValue", true),
        ) ?: return "" to ""
        val raw = result.optJSONObject("result")?.optString("value").orEmpty()
        val value = runCatching { JSONObject(raw) }.getOrNull()
        return value?.optString("url").orEmpty() to value?.optString("title").orEmpty()
    }

    private suspend fun dispatchTap(session: CdpSession, x: Int, y: Int) {
        session.send(
            "Input.dispatchMouseEvent",
            JSONObject()
                .put("type", "mousePressed")
                .put("x", x)
                .put("y", y)
                .put("button", "left")
                .put("clickCount", 1),
        )
        session.send(
            "Input.dispatchMouseEvent",
            JSONObject()
                .put("type", "mouseReleased")
                .put("x", x)
                .put("y", y)
                .put("button", "left")
                .put("clickCount", 1),
        )
    }

    private suspend fun dispatchScroll(
        session: CdpSession,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
    ) {
        session.send(
            "Input.dispatchMouseEvent",
            JSONObject()
                .put("type", "mouseWheel")
                .put("x", x2)
                .put("y", y2)
                .put("deltaX", x1 - x2)
                .put("deltaY", y1 - y2),
        )
    }

    private suspend fun dispatchKey(session: CdpSession, rawKey: String) {
        val key = ChromeKey.from(rawKey)
        val base = JSONObject()
            .put("key", key.key)
            .put("code", key.code)
            .put("windowsVirtualKeyCode", key.virtualKeyCode)
            .put("nativeVirtualKeyCode", key.virtualKeyCode)
        session.send("Input.dispatchKeyEvent", JSONObject(base.toString()).put("type", "keyDown"))
        session.send("Input.dispatchKeyEvent", JSONObject(base.toString()).put("type", "keyUp"))
    }

    private suspend fun navigateHistory(session: CdpSession, offset: Int) {
        val history = session.send("Page.getNavigationHistory", JSONObject())
        val entries = history.optJSONArray("entries") ?: return
        val currentIndex = history.optInt("currentIndex", -1)
        val targetIndex = currentIndex + offset
        if (targetIndex !in 0 until entries.length()) return
        val entryId = entries.optJSONObject(targetIndex)?.optInt("id", -1) ?: -1
        if (entryId >= 0) {
            session.send("Page.navigateToHistoryEntry", JSONObject().put("entryId", entryId))
        }
    }

    private fun updateCursor(x: Int, y: Int, durationMillis: Int) {
        _displayState.value = _displayState.value.copy(
            cursorX = x,
            cursorY = y,
            cursorAnimationDurationMillis = durationMillis,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private fun statusResult(): String = JSONObject().apply {
        put("ok", true)
        put("running", isRunning())
        put("width", ChromeViewportWidth)
        put("height", ChromeViewportHeight)
        put("stdout", if (isRunning()) "Chrome is running." else "Chrome is stopped.")
    }.toString()

    private fun stopLocked(): String {
        runCatching { cdpSession?.sendBlocking("Browser.close", JSONObject()) }
        cdpSession?.close()
        cdpSession = null
        chromeProcess?.let { process ->
            runCatching { process.destroy() }
            if (process.isAlive) runCatching { process.destroyForcibly() }
        }
        chromeProcess = null
        _displayState.value = _displayState.value.copy(
            isActive = false,
            cursorX = null,
            cursorY = null,
            status = "Chrome is stopped.",
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        return JSONObject()
            .put("ok", true)
            .put("stdout", "Chrome stopped.")
            .toString()
    }

    private fun isRunning(): Boolean = chromeProcess?.isAlive == true && cdpSession?.isOpen == true

    private fun drainProcessOutput(process: Process) {
        scope.launch {
            runCatching {
                FileOutputStream(File(cacheDirectory, "chromium.log"), false).use { output ->
                    process.inputStream.use { input -> input.copyTo(output) }
                }
            }
            if (chromeProcess === process && !process.isAlive) {
                cdpSession?.close()
                cdpSession = null
                _displayState.value = _displayState.value.copy(
                    isActive = false,
                    status = "Chrome exited.",
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private suspend fun requestJsonArray(url: String): JSONArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            require(response.isSuccessful) { "CDP HTTP ${response.code}" }
            JSONArray(response.body?.string().orEmpty())
        }
    }

    private fun normalizeUrl(rawValue: String): String {
        val value = rawValue.trim()
        if (value.isBlank()) return ""
        return when {
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("about:", ignoreCase = true) -> value
            else -> "https://$value"
        }
    }

    private fun normalizedCoordinate(
        arguments: JSONObject,
        key: String,
        size: Int,
    ): Int? {
        val value = arguments.optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() }
            ?.let { (it.coerceIn(0.0, 1000.0) * size / 1000.0).toInt() }
    }

    private fun errorResult(message: String): String = JSONObject()
        .put("ok", false)
        .put("errmsg", message)
        .toString()

    private inner class CdpSession(
        private val url: String,
    ) {
        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
        private val opened = CompletableDeferred<Unit>()
        @Volatile
        private var socket: WebSocket? = null
        @Volatile
        var isOpen: Boolean = false
            private set

        suspend fun connect() {
            socket = client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        isOpen = true
                        if (!opened.isCompleted) opened.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
                        val id = payload.optInt("id", 0)
                        if (id != 0) pending.remove(id)?.complete(payload)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        fail(t)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        fail(IllegalStateException(reason.ifBlank { "Chrome CDP closed." }))
                    }
                },
            )
            withTimeout(ChromeCommandTimeoutMillis) { opened.await() }
        }

        suspend fun send(method: String, params: JSONObject): JSONObject {
            check(isOpen) { "Chrome CDP is not connected." }
            val id = nextId.getAndIncrement()
            val deferred = CompletableDeferred<JSONObject>()
            pending[id] = deferred
            val sent = socket?.send(
                JSONObject()
                    .put("id", id)
                    .put("method", method)
                    .put("params", params)
                    .toString()
            ) == true
            if (!sent) {
                pending.remove(id)
                error("Unable to send Chrome CDP command.")
            }
            val response = withTimeout(ChromeCommandTimeoutMillis) { deferred.await() }
            response.optJSONObject("error")?.let { error ->
                throw IllegalStateException(error.optString("message").ifBlank { "Chrome CDP command failed." })
            }
            return response.optJSONObject("result") ?: JSONObject()
        }

        fun sendBlocking(method: String, params: JSONObject) {
            val id = nextId.getAndIncrement()
            socket?.send(
                JSONObject()
                    .put("id", id)
                    .put("method", method)
                    .put("params", params)
                    .toString()
            )
        }

        fun close() {
            isOpen = false
            socket?.close(1000, "closing")
            socket = null
            fail(IllegalStateException("Chrome CDP session closed."))
        }

        private fun fail(throwable: Throwable) {
            isOpen = false
            if (!opened.isCompleted) opened.completeExceptionally(throwable)
            pending.values.forEach { deferred ->
                if (!deferred.isCompleted) deferred.completeExceptionally(throwable)
            }
            pending.clear()
        }
    }

    private data class ChromeKey(
        val key: String,
        val code: String,
        val virtualKeyCode: Int,
    ) {
        companion object {
            fun from(rawValue: String): ChromeKey = when (rawValue.trim().uppercase()) {
                "ENTER", "RETURN" -> ChromeKey("Enter", "Enter", 13)
                "TAB" -> ChromeKey("Tab", "Tab", 9)
                "BACKSPACE", "BACK" -> ChromeKey("Backspace", "Backspace", 8)
                "DELETE", "DEL" -> ChromeKey("Delete", "Delete", 46)
                "ESC", "ESCAPE" -> ChromeKey("Escape", "Escape", 27)
                "SPACE" -> ChromeKey(" ", "Space", 32)
                "ARROWUP", "UP" -> ChromeKey("ArrowUp", "ArrowUp", 38)
                "ARROWDOWN", "DOWN" -> ChromeKey("ArrowDown", "ArrowDown", 40)
                "ARROWLEFT", "LEFT" -> ChromeKey("ArrowLeft", "ArrowLeft", 37)
                "ARROWRIGHT", "RIGHT" -> ChromeKey("ArrowRight", "ArrowRight", 39)
                "HOME" -> ChromeKey("Home", "Home", 36)
                "END" -> ChromeKey("End", "End", 35)
                "PAGEUP" -> ChromeKey("PageUp", "PageUp", 33)
                "PAGEDOWN" -> ChromeKey("PageDown", "PageDown", 34)
                else -> {
                    val value = rawValue.take(1).ifBlank { " " }
                    ChromeKey(value, "Key${value.uppercase()}", value.uppercase().first().code)
                }
            }
        }
    }
}
