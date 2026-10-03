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
import android.os.Bundle
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
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
private const val AgentModeLogTag = "AetherAgentMode"
private const val TextInputMethodKeyEvents = "key_events"
private const val TextInputMethodClipboardPaste = "clipboard_paste"
private const val SystemPackageName = "android"

class AetherAgentModeShizukuService @Keep constructor(
    private val context: Context,
) : IAetherAgentModeService.Stub() {
    constructor() : this(resolveLegacyUserServiceContext())

    private val privilegedContext: Context by lazy { contextForCurrentProcess(context) }
    private val displayManager: DisplayManager by lazy {
        privilegedContext.getSystemService<DisplayManager>()!!
    }

    /**
     * One context per Android user this service was asked to act on. The user service never runs in
     * Aether's user: Shizuku and the su-based fallback both start it as shell (uid 2000) or root
     * (uid 0), and Shizuku shares a single user service process between the same package in every
     * user, so [privilegedContext] may belong to whichever user bound the service first. Everything
     * that acts on "the user" therefore goes through [contextForUser].
     */
    private val userContexts = ConcurrentHashMap<Int, Context>()
    private val clipboardManagers = ConcurrentHashMap<Int, ClipboardManager>()
    private val displays = ConcurrentHashMap<Int, VirtualDisplay>()
    private val imageReaders = ConcurrentHashMap<Int, ImageReader>()
    private val previewSurfaces = ConcurrentHashMap<Int, Surface>()
    private val displayLocks = ConcurrentHashMap<Int, Any>()
    private val displaysWithLaunchedContent = ConcurrentHashMap.newKeySet<Int>()
    private val displaysWithFailedLaunch = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Virtual displays are not owned by an Android user: DisplayManagerService only records the
     * calling package, so this display is the same one the launched app draws into regardless of the
     * user the launch later targets. Only the calls that act on "the user" need [contextForUser].
     */
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
            displaysWithFailedLaunch.remove(displayId)
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
        displaysWithFailedLaunch.clear()
        displayLocks.clear()
        System.exit(0)
    }

    override fun launchPackage(packageName: String, displayId: Int, userId: Int) {
        warnIfCallerUserDiffers(userId)
        try {
            launchPackageInUser(packageName, displayId, userId)
        } catch (throwable: Throwable) {
            // Remember the failed launch so later captures report "nothing was drawn" instead of
            // handing back a black image that looks like a rendered screen.
            displaysWithFailedLaunch.add(displayId)
            throw throwable
        }
        displaysWithFailedLaunch.remove(displayId)
        displaysWithLaunchedContent.add(displayId)
    }

    private fun launchPackageInUser(packageName: String, displayId: Int, userId: Int) {
        val launchContext = contextForUser(userId)
        val intent = launchContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launchable activity for $packageName in user $userId.")
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.launchDisplayId = displayId
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

        val targetDisplay = displayManager.getDisplay(displayId)
            ?: error("Display $displayId is not available.")
        // createDisplayContext keeps the user of its base context, and PendingIntent.getActivity
        // stamps that user into the pending intent on Android 9+, so the activity starts as Aether's
        // user instead of the user service's user (issue #100).
        val displayContext = launchContext.createDisplayContext(targetDisplay)
        activityPendingIntentForUser(displayContext, intent, userId).send(
            launchContext,
            0,
            null,
            null,
            null,
            null,
            options.toBundle(),
        )
    }

    /**
     * `PendingIntent.getActivity` only honours the context's user since Android 9; before that it
     * stamps the *process* user, which would silently launch in the user service's user. Ask for the
     * target user explicitly there and refuse to launch rather than acting on the wrong user.
     */
    private fun activityPendingIntentForUser(
        context: Context,
        intent: Intent,
        userId: Int,
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return PendingIntent.getActivity(context, intent.hashCode(), intent, flags)
        }
        val userHandle = runCatching {
            UserHandle.getUserHandleForUid(userId * AgentModePerUserUidRange)
        }.getOrNull()
        val asUser = userHandle?.let { user ->
            runCatching {
                PendingIntent::class.java
                    .getMethod(
                        "getActivityAsUser",
                        Context::class.java,
                        Int::class.javaPrimitiveType,
                        Intent::class.java,
                        Int::class.javaPrimitiveType,
                        Bundle::class.java,
                        UserHandle::class.java,
                    )
                    .invoke(null, context, intent.hashCode(), intent, flags, null, user) as? PendingIntent
            }.getOrNull()
        }
        return asUser
            ?: error("Agent Mode cannot launch apps as user $userId on this Android version.")
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

    override fun text(displayId: Int, text: String, userId: Int): String {
        ensureManagedDisplay(displayId)
        if (text.isEmpty()) return TextInputMethodKeyEvents
        val keyMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        // getEvents() returns null when any character has no key mapping (CJK, emoji, accented
        // letters, ...). Those cannot be typed as KeyEvents, so the whole text is pasted instead.
        val events = keyMap.getEvents(text.toCharArray())
        if (events == null) {
            pasteText(displayId, text, userId)
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

    private fun pasteText(displayId: Int, text: String, userId: Int) {
        // The clipboard is per user, so a paste into an app that belongs to another user needs that
        // user's clipboard, not the user service's one.
        clipboardManagerFor(userId).setPrimaryClip(ClipData.newPlainText("Aether Agent Mode", text))
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
    ): String {
        val display = displays[displayId]
            ?: error("Display $displayId is not managed by Aether Agent Mode.")
        val reader = imageReaders[displayId]
            ?: error("Display $displayId does not have an internal capture surface.")
        val boundedMaxEdge = maxEdge.coerceIn(320, 4096)
        val boundedQuality = quality.coerceIn(40, 95)
        var status = agentModeCaptureStatus(AgentModeCaptureSourceDisplay)
        synchronized(displayLock(displayId)) {
            drainImageReader(reader)
            val previewSurface = previewSurfaces[displayId]?.takeIf { it.isValid }
            display.setSurface(reader.surface)
            try {
                val image = awaitLatestImage(reader)
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                    when {
                        image != null -> {
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
                        }

                        displaysWithFailedLaunch.contains(displayId) -> {
                            blankImageToJpegStream(
                                width = reader.width,
                                height = reader.height,
                                output = stream,
                                maxEdge = boundedMaxEdge,
                                quality = boundedQuality,
                            )
                            status = agentModeCaptureStatus(
                                source = AgentModeCaptureSourceBlank,
                                blankReason = AgentModeBlankReasonLaunchFailed,
                            )
                        }

                        !displaysWithLaunchedContent.contains(displayId) -> {
                            blankImageToJpegStream(
                                width = reader.width,
                                height = reader.height,
                                output = stream,
                                maxEdge = boundedMaxEdge,
                                quality = boundedQuality,
                            )
                            status = agentModeCaptureStatus(
                                source = AgentModeCaptureSourceBlank,
                                blankReason = AgentModeBlankReasonNoLaunchedContent,
                            )
                        }

                        else -> error("Timed out while capturing display $displayId.")
                    }
                }
            } finally {
                if (previewSurface != null && displays[displayId] === display) {
                    runCatching { display.setSurface(previewSurface) }
                }
            }
        }
        return status
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
    override fun listInstalledAppsJson(userId: Int): String {
        warnIfCallerUserDiffers(userId)
        val packageManager = contextForUser(userId).packageManager
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

    /** Builds (and caches) the context that acts on [userId] instead of on the service's user. */
    private fun contextForUser(userId: Int): Context {
        require(userId >= 0) { "Agent Mode received an invalid user id ($userId)." }
        return userContexts.computeIfAbsent(userId) { targetUserId ->
            createUserScopedContext(privilegedContext, targetUserId)
                ?: error(
                    "Agent Mode cannot act on user $targetUserId. Cross-user Agent Mode is not " +
                        "supported by this device's framework.",
                )
        }
    }

    /**
     * `Context.createContextAsUser` and `Context.createPackageContextAsUser` are `@SystemApi`, so
     * they are invoked through reflection.
     *
     * The `android` package is preferred because the framework special-cases it: it copies the
     * current context and only replaces its user instead of looking the package up for the target
     * user. That keeps this process's package identity (`com.android.shell` under Shizuku, which the
     * clipboard and the virtual display owner check rely on) and also works in users where the host
     * package is not installed.
     */
    private fun createUserScopedContext(baseContext: Context, userId: Int): Context? {
        val userHandle = runCatching {
            UserHandle.getUserHandleForUid(userId * AgentModePerUserUidRange)
        }.getOrNull() ?: return null
        runCatching {
            Context::class.java
                .getMethod(
                    "createPackageContextAsUser",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    UserHandle::class.java,
                )
                .invoke(baseContext, SystemPackageName, Context.CONTEXT_IGNORE_SECURITY, userHandle) as Context
        }.getOrNull()?.let { userContext ->
            Log.i(AgentModeLogTag, "Agent Mode user service scoped to user $userId")
            return userContext
        }
        return runCatching {
            Context::class.java
                .getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
                .invoke(baseContext, userHandle, 0) as Context
        }.getOrNull()?.also {
            Log.i(AgentModeLogTag, "Agent Mode user service scoped to user $userId")
        }
    }

    /**
     * The service binder lives in this process, so a call from Aether reports Aether's own uid as the
     * caller. A different user means the caller is not the app whose user we were asked to act on;
     * that is worth a log line, but the explicit [userId] still wins because it came from Aether.
     */
    private fun warnIfCallerUserDiffers(userId: Int) {
        val callerUserId = runCatching {
            agentModeUserIdFromUid(Binder.getCallingUid())
        }.getOrNull() ?: return
        if (callerUserId != userId) {
            Log.w(
                AgentModeLogTag,
                "Agent Mode was asked to act on user $userId but the caller runs in user $callerUserId.",
            )
        }
    }

    /**
     * ClipboardService verifies the caller's op package against its UID. [privilegedContext] is a
     * package context created from Aether's context, and such contexts keep Aether's op package, so
     * under Shizuku (shell UID) every write failed with "Package com.baimoqilin.aether does not belong
     * to 2000". Build the manager on a context that reports this process's own package instead, and
     * for the user the clipboard write must happen in.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun clipboardManagerFor(userId: Int): ClipboardManager {
        val userContext = contextForUser(userId)
        return clipboardManagers.computeIfAbsent(userId) {
            val identityContext = object : ContextWrapper(userContext) {
                override fun getOpPackageName(): String = baseContext.packageName
            }
            runCatching {
                ClipboardManager::class.java
                    .getDeclaredConstructor(Context::class.java, Handler::class.java)
                    .apply { isAccessible = true }
                    .newInstance(identityContext, null)
            }.getOrNull()
                ?: userContext.getSystemService<ClipboardManager>()
                ?: error("Clipboard service is unavailable for this display.")
        }
    }

    private fun packageNameForCurrentProcess(defaultPackageName: String): String =
        when (Process.myUid()) {
            Process.SHELL_UID -> ShellPackageName
            Process.SYSTEM_UID -> SystemPackageName
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

    companion object {
        private fun resolveLegacyUserServiceContext(): Context {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass
                .getDeclaredMethod("currentActivityThread")
                .apply { isAccessible = true }
                .invoke(null)
            val currentApplication = activityThreadClass
                .getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
                .invoke(null) as? Context
            if (currentApplication != null) return currentApplication
            if (currentActivityThread != null) {
                val systemContext = activityThreadClass
                    .getDeclaredMethod("getSystemContext")
                    .apply { isAccessible = true }
                    .invoke(currentActivityThread) as? Context
                if (systemContext != null) return systemContext
            }
            error("Unable to create an Android context for Shizuku Agent Mode service.")
        }
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
