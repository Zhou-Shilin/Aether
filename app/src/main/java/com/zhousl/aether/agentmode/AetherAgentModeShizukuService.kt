package com.zhousl.aether.agentmode

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.annotation.Keep
import androidx.core.content.getSystemService
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

private const val VirtualDisplayFlagPublic = 1 shl 0
private const val VirtualDisplayFlagOwnContentOnly = 1 shl 3
private const val VirtualDisplayFlagSupportsTouch = 1 shl 6
private const val VirtualDisplayFlagDestroyContentOnRemoval = 1 shl 8
private const val VirtualDisplayFlagTrusted = 1 shl 10
private const val VirtualDisplayFlagTouchFeedbackDisabled = 1 shl 13
private const val VirtualDisplayFlagOwnFocus = 1 shl 14
private const val VirtualDisplayFlagStealTopFocusDisabled = 1 shl 16

private const val InjectInputEventModeWaitForFinish = 2
private const val TapDurationMillis = 60L
private const val KeyPressDurationMillis = 30L
private const val ShellPackageName = "com.android.shell"
private const val TextInputMethodKeyEvents = "key_events"
private const val TextInputMethodClipboardPaste = "clipboard_paste"
private const val SystemPackageName = "android"
private const val RootUid = 0

/**
 * The privileged Agent Mode service. It runs in a separate shell (Shizuku) or root process started
 * by [AgentModeServiceStarter], which passes a [context] for Aether in the launching Android user.
 */
class AetherAgentModeShizukuService @Keep constructor(
    private val context: Context,
) : IAetherAgentModeService.Stub() {
    private val privilegedContext: Context by lazy { contextForCurrentProcess(context) }
    private val displayManager: DisplayManager by lazy {
        privilegedContext.getSystemService<DisplayManager>()!!
    }

    /**
     * ClipboardService verifies the caller's op package against its UID. [privilegedContext] is a
     * package context created from Aether's context, and such contexts keep Aether's op package, so
     * under Shizuku (shell UID) every write failed with "Package com.baimoqilin.aether does not belong
     * to 2000". Build the manager on a context that reports this process's own package instead.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun createClipboardManager(userContext: Context): ClipboardManager {
        val identityContext = object : ContextWrapper(userContext) {
            override fun getOpPackageName(): String = baseContext.packageName
        }
        return runCatching {
            ClipboardManager::class.java
                .getDeclaredConstructor(Context::class.java, Handler::class.java)
                .apply { isAccessible = true }
                .newInstance(identityContext, null)
        }.getOrNull()
            ?: userContext.getSystemService<ClipboardManager>()
            ?: error("Clipboard service is unavailable for this display.")
    }

    /**
     * This process runs as shell (Shizuku) or root, both in user 0, while [context] belongs to the
     * Android user that launched it. Package queries, activity launches and the clipboard are
     * per-user, so each call targets the Android user that owns the calling Aether process, never
     * the process's own user nor the user [context] happens to be bound to.
     */
    private val userContexts = ConcurrentHashMap<UserHandle, Context>()
    private val clipboardManagers = ConcurrentHashMap<UserHandle, ClipboardManager>()

    /** Must be called on the binder thread of the incoming call. */
    private fun callerUserHandle(): UserHandle = UserHandle.getUserHandleForUid(Binder.getCallingUid())

    private fun callerUserContext(): Context = userContextFor(callerUserHandle())

    private fun callerClipboardManager(): ClipboardManager {
        val user = callerUserHandle()
        return clipboardManagers.computeIfAbsent(user) {
            createClipboardManager(userContextFor(user))
        }
    }

    private fun userContextFor(user: UserHandle): Context =
        userContexts.computeIfAbsent(user) { createPackageContextAsUser(it) }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createPackageContextAsUser(user: UserHandle): Context {
        val packageName = privilegedContext.packageName
        return runCatching {
            Context::class.java
                .getMethod(
                    "createPackageContextAsUser",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    UserHandle::class.java,
                )
                .invoke(privilegedContext, packageName, Context.CONTEXT_IGNORE_SECURITY, user) as Context
        }.getOrElse { throwable ->
            val cause = (throwable as? java.lang.reflect.InvocationTargetException)?.targetException ?: throwable
            error("Agent Mode cannot act on behalf of Android user $user: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }
    private val displays = ConcurrentHashMap<Int, VirtualDisplay>()
    private val imageReaders = ConcurrentHashMap<Int, ImageReader>()
    private val previewSurfaces = ConcurrentHashMap<Int, Surface>()
    private val displayLocks = ConcurrentHashMap<Int, Any>()
    private val displaysWithLaunchedContent = ConcurrentHashMap.newKeySet<Int>()

    override fun createDisplay(
        name: String,
        width: Int,
        height: Int,
        density: Int,
        surface: Surface,
    ): Int {
        val display = displayManager.createVirtualDisplay(
            name,
            width,
            height,
            density,
            surface,
            agentVirtualDisplayFlags(),
        )
        val displayId = display.display.displayId
        displays[displayId] = display
        displayLocks[displayId] = Any()
        return displayId
    }

    override fun createOwnedDisplay(
        name: String,
        width: Int,
        height: Int,
        density: Int,
    ): Int {
        val reader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2,
        )
        val display = displayManager.createVirtualDisplay(
            name,
            width,
            height,
            density,
            reader.surface,
            agentVirtualDisplayFlags(),
        )
        val displayId = display.display.displayId
        displays[displayId] = display
        imageReaders[displayId] = reader
        displayLocks[displayId] = Any()
        return displayId
    }

    override fun attachPreviewSurface(displayId: Int, surface: Surface) {
        val display = displays[displayId]
            ?: error("Display $displayId is not managed by Aether Agent Mode.")
        synchronized(displayLock(displayId)) {
            previewSurfaces[displayId] = surface
            display.setSurface(surface)
        }
    }

    override fun detachPreviewSurface(displayId: Int) {
        val display = displays[displayId]
            ?: error("Display $displayId is not managed by Aether Agent Mode.")
        synchronized(displayLock(displayId)) {
            previewSurfaces.remove(displayId)
            display.setSurface(imageReaders[displayId]?.surface)
        }
    }

    override fun releaseDisplay(displayId: Int) {
        synchronized(displayLock(displayId)) {
            previewSurfaces.remove(displayId)
            displays.remove(displayId)?.release()
            imageReaders.remove(displayId)?.close()
            displaysWithLaunchedContent.remove(displayId)
            displayLocks.remove(displayId)
        }
    }

    override fun destroy() {
        displays.keys.toList().forEach { displayId ->
            runCatching { releaseDisplay(displayId) }
        }
        previewSurfaces.clear()
        imageReaders.clear()
        displaysWithLaunchedContent.clear()
        displayLocks.clear()
        System.exit(0)
    }

    override fun linkClient(client: IBinder) {
        client.linkToDeath({ destroy() }, 0)
    }

    override fun launchPackage(packageName: String, displayId: Int) {
        val userContext = callerUserContext()
        val intent = userContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launchable activity for $packageName.")
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.launchDisplayId = displayId
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

        val targetDisplay = displayManager.getDisplay(displayId)
            ?: error("Display $displayId is not available.")
        val displayContext = userContext.createDisplayContext(targetDisplay)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        PendingIntent.getActivity(
            displayContext,
            intent.hashCode(),
            intent,
            flags,
        ).send(
            userContext,
            0,
            null,
            null,
            null,
            null,
            options.toBundle(),
        )
        displaysWithLaunchedContent.add(displayId)
    }

    override fun runInputCommand(command: String) {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error(output.ifBlank { "Input command failed with exit code $exitCode." })
        }
    }

    override fun tap(displayId: Int, x: Int, y: Int) {
        ensureManagedDisplay(displayId)
        val downTime = SystemClock.uptimeMillis()
        injectMotionEvent(displayId, downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
        SystemClock.sleep(TapDurationMillis)
        injectMotionEvent(
            displayId,
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            x.toFloat(),
            y.toFloat(),
        )
    }

    override fun swipe(
        displayId: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    ) {
        ensureManagedDisplay(displayId)
        val duration = durationMs.coerceIn(50, 10_000)
        val downTime = SystemClock.uptimeMillis()
        injectMotionEvent(displayId, downTime, downTime, MotionEvent.ACTION_DOWN, x1.toFloat(), y1.toFloat())

        val steps = (duration / 16).coerceIn(3, 80)
        for (step in 1 until steps) {
            val progress = step.toFloat() / steps.toFloat()
            val x = x1 + ((x2 - x1) * progress)
            val y = y1 + ((y2 - y1) * progress)
            SystemClock.sleep((duration / steps).toLong().coerceAtLeast(1L))
            injectMotionEvent(
                displayId,
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                x,
                y,
            )
        }

        SystemClock.sleep((duration / steps).toLong().coerceAtLeast(1L))
        injectMotionEvent(
            displayId,
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            x2.toFloat(),
            y2.toFloat(),
        )
    }

    override fun key(displayId: Int, keyCode: String) {
        ensureManagedDisplay(displayId)
        val code = parseKeyCode(keyCode)
        val downTime = SystemClock.uptimeMillis()
        injectKeyEvent(displayId, downTime, downTime, KeyEvent.ACTION_DOWN, code, 0)
        SystemClock.sleep(KeyPressDurationMillis)
        injectKeyEvent(displayId, downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)
    }

    override fun text(displayId: Int, text: String): String {
        ensureManagedDisplay(displayId)
        if (text.isEmpty()) return TextInputMethodKeyEvents
        val keyMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        // getEvents() returns null when any character has no key mapping (CJK, emoji, accented
        // letters, ...). Those cannot be typed as KeyEvents, so the whole text is pasted instead.
        val events = keyMap.getEvents(text.toCharArray())
        if (events == null) {
            pasteText(displayId, text)
            return TextInputMethodClipboardPaste
        }
        var downTime = SystemClock.uptimeMillis()
        events.forEach { sourceEvent ->
            val now = SystemClock.uptimeMillis()
            if (sourceEvent.action == KeyEvent.ACTION_DOWN) {
                downTime = now
            }
            injectKeyEvent(
                displayId = displayId,
                downTime = downTime,
                eventTime = now,
                action = sourceEvent.action,
                keyCode = sourceEvent.keyCode,
                metaState = sourceEvent.metaState,
                scanCode = sourceEvent.scanCode,
                flags = sourceEvent.flags or KeyEvent.FLAG_SOFT_KEYBOARD,
            )
            if (sourceEvent.action == KeyEvent.ACTION_UP) {
                SystemClock.sleep(4L)
            }
        }
        return TextInputMethodKeyEvents
    }

    private fun pasteText(displayId: Int, text: String) {
        callerClipboardManager().setPrimaryClip(ClipData.newPlainText("Aether Agent Mode", text))
        val downTime = SystemClock.uptimeMillis()
        injectKeyEvent(
            displayId = displayId,
            downTime = downTime,
            eventTime = downTime,
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_PASTE,
            metaState = 0,
        )
        SystemClock.sleep(KeyPressDurationMillis)
        injectKeyEvent(
            displayId = displayId,
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_PASTE,
            metaState = 0,
        )
    }

    override fun captureImageToFd(
        displayId: Int,
        output: ParcelFileDescriptor,
        maxEdge: Int,
        quality: Int,
    ) {
        val display = displays[displayId]
            ?: error("Display $displayId is not managed by Aether Agent Mode.")
        val reader = imageReaders[displayId]
            ?: error("Display $displayId does not have an internal capture surface.")
        val boundedMaxEdge = maxEdge.coerceIn(320, 4096)
        val boundedQuality = quality.coerceIn(40, 95)
        synchronized(displayLock(displayId)) {
            drainImageReader(reader)
            val previewSurface = previewSurfaces[displayId]?.takeIf { it.isValid }
            display.setSurface(reader.surface)
            try {
                val image = awaitLatestImage(reader)
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                    if (image != null) {
                        try {
                            imageToJpegStream(
                                image = image,
                                output = stream,
                                maxEdge = boundedMaxEdge,
                                quality = boundedQuality,
                            )
                        } finally {
                            image.close()
                        }
                    } else if (!displaysWithLaunchedContent.contains(displayId)) {
                        blankImageToJpegStream(
                            width = reader.width,
                            height = reader.height,
                            output = stream,
                            maxEdge = boundedMaxEdge,
                            quality = boundedQuality,
                        )
                    } else {
                        error("Timed out while capturing display $displayId.")
                    }
                }
            } finally {
                if (previewSurface != null && displays[displayId] === display) {
                    runCatching { display.setSurface(previewSurface) }
                }
            }
        }
    }

    private fun displayLock(displayId: Int): Any =
        displayLocks.computeIfAbsent(displayId) { Any() }

    private fun drainImageReader(reader: ImageReader) {
        while (true) {
            val image = reader.acquireLatestImage() ?: return
            image.close()
        }
    }

    private fun awaitLatestImage(reader: ImageReader): Image? {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (SystemClock.uptimeMillis() < deadline) {
            reader.acquireLatestImage()?.let { return it }
            SystemClock.sleep(16L)
        }
        return null
    }

    private fun blankImageToJpegStream(
        width: Int,
        height: Int,
        output: OutputStream,
        maxEdge: Int,
        quality: Int,
    ) {
        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            bitmap.eraseColor(android.graphics.Color.BLACK)
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxEdge)
            try {
                if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    error("Unable to encode empty Agent Mode screenshot.")
                }
                output.flush()
            } finally {
                if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun imageToJpegStream(
        image: Image,
        output: OutputStream,
        maxEdge: Int,
        quality: Int,
    ) {
        val plane = image.planes.firstOrNull()
            ?: error("Captured display image had no pixel planes.")
        val width = image.width.coerceAtLeast(1)
        val height = image.height.coerceAtLeast(1)
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowStride = plane.rowStride.coerceAtLeast(width * pixelStride)
        val paddedWidth = (rowStride / pixelStride).coerceAtLeast(width)
        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        try {
            paddedBitmap.copyPixelsFromBuffer(plane.buffer)
            val bitmap = if (paddedWidth == width) {
                paddedBitmap
            } else {
                Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
            }
            try {
                val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxEdge)
                try {
                    if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        error("Unable to encode Agent Mode screenshot.")
                    }
                    output.flush()
                } finally {
                    if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                }
            } finally {
                if (bitmap !== paddedBitmap) bitmap.recycle()
            }
        } finally {
            paddedBitmap.recycle()
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val largestEdge = maxOf(bitmap.width, bitmap.height)
        if (largestEdge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / largestEdge.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    override fun focusedWindowJson(displayId: Int): String {
        ensureManagedDisplay(displayId)
        val process = ProcessBuilder("dumpsys", "input")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return parseInputDispatcherFocus(output, displayId).toString()
    }

    override fun listDisplaysJson(): String =
        JSONArray().apply {
            displayManager.displays.forEach { display ->
                val size = Point()
                @Suppress("DEPRECATION")
                display.getSize(size)
                put(
                    JSONObject().apply {
                        put("display_id", display.displayId)
                        put("name", display.name.orEmpty())
                        put("width", display.mode?.physicalWidth ?: size.x)
                        put("height", display.mode?.physicalHeight ?: size.y)
                        put("is_aether_display", displays.containsKey(display.displayId))
                    }
                )
            }
        }.toString()

    @Suppress("DEPRECATION")
    override fun listInstalledAppsJson(): String {
        val packageManager = callerUserContext().packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val uniqueApps = linkedMapOf<String, JSONObject>()
        launchables
            .sortedWith(
                compareBy(
                    { it.loadLabel(packageManager).toString().lowercase() },
                    { it.activityInfo.packageName },
                )
            )
            .forEach { info ->
                val activityInfo = info.activityInfo ?: return@forEach
                val applicationInfo = activityInfo.applicationInfo ?: return@forEach
                val packageName = activityInfo.packageName.orEmpty()
                if (packageName.isBlank() || uniqueApps.containsKey(packageName)) return@forEach
                uniqueApps[packageName] = JSONObject().apply {
                    put("package_name", packageName)
                    put("app_name", info.loadLabel(packageManager).toString())
                    put("activity_name", activityInfo.name.orEmpty())
                    put("enabled", activityInfo.enabled && applicationInfo.enabled)
                    put("system", applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    put("launchable", true)
                }
            }
        return JSONArray(uniqueApps.values).toString()
    }

    private fun contextForCurrentProcess(baseContext: Context): Context {
        val packageName = packageNameForCurrentProcess(baseContext.packageName)
        if (packageName == baseContext.packageName) return baseContext
        return baseContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
    }

    private fun packageNameForCurrentProcess(defaultPackageName: String): String =
        when (Process.myUid()) {
            Process.SHELL_UID -> ShellPackageName
            // Root owns no package; act as the platform, as the root service always has.
            RootUid, Process.SYSTEM_UID -> SystemPackageName
            else -> defaultPackageName
        }

    private fun agentVirtualDisplayFlags(): Int =
        VirtualDisplayFlagPublic or
            VirtualDisplayFlagOwnContentOnly or
            VirtualDisplayFlagSupportsTouch or
            VirtualDisplayFlagDestroyContentOnRemoval or
            VirtualDisplayFlagTrusted or
            VirtualDisplayFlagTouchFeedbackDisabled or
            VirtualDisplayFlagOwnFocus or
            VirtualDisplayFlagStealTopFocusDisabled

    private fun ensureManagedDisplay(displayId: Int) {
        if (!displays.containsKey(displayId)) {
            error("Display $displayId is not managed by Aether Agent Mode.")
        }
    }

    private fun injectMotionEvent(
        displayId: Int,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        // Mirror `input tap`: a finger pointer with pressure from a real touchscreen device. The
        // short MotionEvent.obtain() overload produces TOOL_TYPE_UNKNOWN from device 0, which some
        // apps (WeChat, WebView-based pages) ignore for focus and click handling.
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().also { pointer ->
                pointer.x = x
                pointer.y = y
                pointer.pressure = 1f
                pointer.size = 1f
            }
        )
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            inputDeviceIdFor(InputDevice.SOURCE_TOUCHSCREEN),
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        injectInputEventOnDisplay(displayId, event)
    }

    private fun injectKeyEvent(
        displayId: Int,
        downTime: Long,
        eventTime: Long,
        action: Int,
        keyCode: Int,
        metaState: Int,
        scanCode: Int = 0,
        flags: Int = 0,
    ) {
        val event = KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            scanCode,
            flags,
            InputDevice.SOURCE_KEYBOARD,
        )
        injectInputEventOnDisplay(displayId, event)
    }

    private fun injectInputEventOnDisplay(displayId: Int, event: InputEvent) {
        try {
            setInputEventDisplayId(event, displayId)
            val inputManager = inputManagerInstance()
            val method = inputManager.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            val injected = method.invoke(inputManager, event, InjectInputEventModeWaitForFinish) as Boolean
            if (!injected) {
                error(
                    "Android input manager rejected ${describeInputEvent(event)} on display $displayId. " +
                        "This usually means no window on the display can receive it (nothing focused or " +
                        "touchable at that point) or the app did not handle it within the dispatch timeout.",
                )
            }
        } finally {
            if (event is MotionEvent) {
                event.recycle()
            }
        }
    }

    @SuppressLint("BlockedPrivateApi")
    private fun setInputEventDisplayId(event: InputEvent, displayId: Int) {
        val method = InputEvent::class.java.getDeclaredMethod(
            "setDisplayId",
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(event, displayId)
    }

    private fun inputManagerInstance(): Any {
        val inputManagerClass = Class.forName("android.hardware.input.InputManager")
        val getInstance = inputManagerClass.getDeclaredMethod("getInstance")
        getInstance.isAccessible = true
        return getInstance.invoke(null)
            ?: error("Android input manager was not available.")
    }

    private fun inputDeviceIdFor(source: Int): Int =
        InputDevice.getDeviceIds().firstOrNull { id ->
            InputDevice.getDevice(id)?.supportsSource(source) == true
        } ?: 0

    private fun describeInputEvent(event: InputEvent): String = when (event) {
        is MotionEvent -> "touch ${MotionEvent.actionToString(event.actionMasked)} at (${event.x.toInt()}, ${event.y.toInt()})"
        is KeyEvent -> "key ${KeyEvent.keyCodeToString(event.keyCode)} ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"}"
        else -> "input event"
    }

    private fun parseKeyCode(rawValue: String): Int {
        val normalized = rawValue.trim()
        normalized.toIntOrNull()?.let { return it }
        val direct = KeyEvent.keyCodeFromString(normalized)
        if (direct != KeyEvent.KEYCODE_UNKNOWN) return direct
        val prefixed = KeyEvent.keyCodeFromString("KEYCODE_${normalized.uppercase()}")
        if (prefixed != KeyEvent.KEYCODE_UNKNOWN) return prefixed
        error("Unsupported key code '$rawValue'.")
    }
}

/**
 * Extracts the focused application and window for [displayId] from `dumpsys input` output. The
 * InputDispatcher section lists one `displayId=N, name='...'` line per display under the
 * `FocusedApplications:` and `FocusedWindows:` headers. `focused_window` is present (possibly empty)
 * only when the headers were found, so callers can tell "nothing focused" from "unknown".
 */
internal fun parseInputDispatcherFocus(dump: String, displayId: Int): JSONObject {
    val lines = dump.lines()
    fun sectionEntry(header: String): String? {
        val start = lines.indexOfFirst { it.trim().startsWith(header) }
        if (start < 0) return null
        // e.g. "FocusedWindows: <none>"
        if (lines[start].trim().removePrefix(header).isNotBlank()) return ""
        val prefix = "displayId=$displayId,"
        for (line in lines.drop(start + 1)) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("displayId=")) break
            if (trimmed.startsWith(prefix)) {
                return trimmed.substringAfter("name='", "").substringBefore("'")
            }
        }
        return ""
    }
    return JSONObject().apply {
        put("display_id", displayId)
        sectionEntry("FocusedApplications:")?.takeIf { it.isNotEmpty() }?.let { put("focused_application", it) }
        sectionEntry("FocusedWindows:")?.let { put("focused_window", it) }
    }
}
