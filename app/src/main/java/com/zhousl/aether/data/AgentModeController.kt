package com.zhousl.aether.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.view.Display
import android.view.Surface
import androidx.core.content.getSystemService
import com.zhousl.aether.agentmode.AgentModeServiceHandle
import com.zhousl.aether.agentmode.AgentModeServiceLauncher
import com.zhousl.aether.agentmode.IAetherAgentModeService
import com.zhousl.aether.phoneuse.PhoneUseElementException
import com.zhousl.aether.phoneuse.PhoneUseAccessibilityService
import com.zhousl.aether.termux.TermuxBashTool
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
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
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

private const val FallbackAgentDisplayWidth = 720
private const val FallbackAgentDisplayHeight = 1280
private const val FallbackAgentDisplayDensityDpi = 320
private const val AgentDisplayName = "aether-agent-mode"
private const val AgentModeCaptureExtension = "jpg"
private const val AgentModeCaptureMimeType = "image/jpeg"
private const val AgentModeCaptureMaxEdge = 1280
private const val AgentModeCoordinateSpace = "normalized_0_1000"
private const val AgentModeCaptureJpegQuality = 85
private const val ShizukuPermissionRequestCode = 4201
private const val RootAuthorizationProbeTimeoutMillis = 2_000L
private const val AgentModeServiceStartTimeoutMillis = 20_000L

private val ShizukuManagerPackages = listOf(
    "moe.shizuku.privileged.api",
    "moe.shizuku.manager",
)

data class AgentModeDisplayState(
    val isActive: Boolean = false,
    val isVirtualDisplay: Boolean = false,
    val displayId: Int? = null,
    val width: Int = FallbackAgentDisplayWidth,
    val height: Int = FallbackAgentDisplayHeight,
    val displays: List<AgentModeDisplayInfo> = emptyList(),
    val latestPreviewPath: String = "",
    val latestWorkspacePath: String = "",
    val cursorX: Int? = null,
    val cursorY: Int? = null,
    val cursorAnimationDurationMillis: Int = 220,
    val isLivePreviewActive: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
    val status: String = "",
)

data class AgentModeDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val isAetherDisplay: Boolean,
)

data class AgentModeInstalledAppInfo(
    val packageName: String,
    val appName: String,
    val activityName: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
)

enum class AgentModeAuthorizationIssue {
    Disabled,
    AccessibilityMissing,
    Ready,
    ShizukuNotInstalled,
    ShizukuNotRunning,
    ShizukuPermissionMissing,
    ShizukuPermissionDenied,
    RootUnavailable,
    RootPermissionMissing,
    RootPermissionDenied,
    Error,
}

data class AgentModeAuthorizationState(
    val issue: AgentModeAuthorizationIssue = AgentModeAuthorizationIssue.Disabled,
    val detail: String = "",
    val method: AgentModeAuthorizationMethod? = null,
) {
    val isReady: Boolean
        get() = issue == AgentModeAuthorizationIssue.Ready
}

class AgentModeController(
    private val context: Context,
    private val bashTool: TermuxBashTool,
    private val runtimeWorkspaceFileBridge: RuntimeWorkspaceFileBridge,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    private val displayManager = context.getSystemService<DisplayManager>()!!
    private val cacheDirectory = File(context.cacheDir, "agent-mode").apply { mkdirs() }
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureMutex = Mutex()
    private val actionMutex = Mutex()
    private val shizukuServiceMutex = Mutex()
    private val _displayState = MutableStateFlow(AgentModeDisplayState())
    @Volatile
    private var lastLoggedAuthorizationSignature: String? = null
    private val _virtualDisplayAuthorizationState = MutableStateFlow(AgentModeAuthorizationState())
    val virtualDisplayAuthorizationState = _virtualDisplayAuthorizationState.asStateFlow()
    private var pendingVirtualDisplayAuthorization = false
    private val _authorizationState = MutableStateFlow(AgentModeAuthorizationState())
    @Volatile private var authorizationSettings = AppSettings()
    @Volatile private var hasRefreshedAuthorization = false
    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuPermissionRequestCode) {
                AetherAnalytics.capture(
                    event = "permission result",
                    properties = mapOf(
                        "permission" to "shizuku",
                        "source" to "agent_mode_authorization",
                        "granted" to (grantResult == PackageManager.PERMISSION_GRANTED),
                        "result" to if (grantResult == PackageManager.PERMISSION_GRANTED) "granted" else "denied",
                    ),
                )
                _authorizationState.value = if (!PhoneUseAccessibilityService.connected.value) {
                    AgentModeAuthorizationState(AgentModeAuthorizationIssue.AccessibilityMissing,
                        "Enable Aether in Android Accessibility settings.")
                } else if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    diagnosticLogger.event(
                        category = "agent_mode",
                        event = "shizuku_permission_granted",
                    )
                    AgentModeAuthorizationState(
                        issue = AgentModeAuthorizationIssue.Ready,
                        detail = "Shizuku permission is granted.",
                    )
                } else {
                    diagnosticLogger.event(
                        category = "agent_mode",
                        event = "shizuku_permission_denied",
                        level = "warn",
                    )
                    AgentModeAuthorizationState(
                        issue = AgentModeAuthorizationIssue.ShizukuPermissionDenied,
                        detail = "Shizuku permission was denied. Grant Aether permission in Shizuku before using Phone Use.",
                    )
                }
                controllerScope.launch {
                    if (pendingVirtualDisplayAuthorization) {
                        pendingVirtualDisplayAuthorization = false
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            authorizeVirtualDisplay(AgentModeAuthorizationMethod.Shizuku)
                        } else {
                            _virtualDisplayAuthorizationState.value = AgentModeAuthorizationState(
                                AgentModeAuthorizationIssue.ShizukuPermissionDenied, "Shizuku permission was denied.", AgentModeAuthorizationMethod.Shizuku)
                        }
                    }
                    refreshAuthorization(authorizationSettings)
                }
            }
        }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        if (authorizationSettings.agentModeAuthorizationEnabled && authorizationSettings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Shizuku) {
            _authorizationState.value = AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.ShizukuNotRunning,
                detail = "Shizuku stopped. Start Shizuku, then refresh Phone Use status.",
            )
        }
        clearShizukuService("Shizuku stopped. Phone Use virtual display was reset.")
    }

    private var displayOrientationOverride: AgentModeDisplayOrientation? = null
    private var createdDisplayOrientation: AgentModeDisplayOrientation? = null
    private var shizukuDisplayId: Int? = null
    private var displayOwnerMethod: AgentModeAuthorizationMethod? = null
    private var displayOwnerBinder: IBinder? = null
    private var shizukuService: IAetherAgentModeService? = null
    private var shizukuServiceHandle: AgentModeServiceHandle? = null
    private var rootService: IAetherAgentModeService? = null
    private var rootServiceHandle: AgentModeServiceHandle? = null
    @Volatile
    private var previewSurface: Surface? = null

    val displayState: StateFlow<AgentModeDisplayState> = _displayState.asStateFlow()
    val authorizationState: StateFlow<AgentModeAuthorizationState> = _authorizationState.asStateFlow()

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        }
        runCatching {
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        }
    }

    suspend fun execute(
        settings: AppSettings,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        argumentsJson: String,
    ): String = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            if (!settings.agentModeAuthorizationEnabled ||
                (hasRefreshedAuthorization && !authorizationSettings.agentModeAuthorizationEnabled)) {
                captureAgentModeFailed(
                    settings = settings,
                    action = "unknown",
                    reason = "authorization_disabled",
                    message = "Phone Use is not authorized.",
                )
                return@withLock JSONObject().apply {
                    put("ok", false)
                    put("errmsg", "Phone Use is not authorized. Enable it in Settings > Phone Use first.")
                }.toString()
            }
            if (hasRefreshedAuthorization &&
                authorizationSettings.phoneUseAuthorizationMethod != settings.phoneUseAuthorizationMethod) {
                return@withLock toolError("Phone Use mode changed during this turn. Start a new turn to use the selected mode.", "unknown")
            }

            val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
                ?: return@withLock invalidArguments("Arguments were not valid JSON.").also {
                    captureAgentModeFailed(
                        settings = settings,
                        action = "unknown",
                        reason = "invalid_arguments",
                        message = "Arguments were not valid JSON.",
                    )
                }
            val action = arguments.optString("action").trim().lowercase()
            diagnosticLogger.event(
                category = "agent_mode",
                event = "action_start",
                details = mapOf(
                    "action" to action.ifBlank { "unknown" },
                    "authorization_method" to settings.phoneUseAuthorizationMethod.storageValue,
                    "display_active" to _displayState.value.isActive,
                ),
            )

            val liveSettings = if (hasRefreshedAuthorization) authorizationSettings else settings
            val previousOrientationOverride = displayOrientationOverride
            runCatching {
                val requestedOrientation = phoneUseDisplayOrientationRequested(liveSettings, action, arguments)
                if (action == "launch") require(arguments.optString("target").trim().isNotBlank()) { "Missing required 'target' argument." }
                val returnToAether = phoneUseReturnToAetherRequested(liveSettings, action, arguments)
                val requestedInput = resolvePhoneUseInputStrategy(liveSettings, action,
                    arguments.optString("input_strategy").trim())
                suspend fun observation(delayMillis: Long): String =
                    if (requestedInput == PhoneUseInputStrategy.ScreenshotFirst) {
                        captureAfterDelay(settings, workspaceDirectory, termuxWorkspaceDirectory, delayMillis)
                    } else observeAfterDelay(settings, delayMillis)
                val usesElement = phoneUseUsesElementAction(liveSettings, action,
                    hasNode = arguments.optString("node_id").isNotBlank(),
                    hasCoordinates = arguments.has("x") && arguments.has("y"),
                    override = arguments.optString("output_strategy").trim())
                if (requestedOrientation != null) displayOrientationOverride = requestedOrientation
                when (action) {
                    "start", "observe", "tree" -> {
                        ensureDisplay(settings)
                        observation(150)
                    }
                    "status" -> statusResult(settings)
                    "list_apps", "apps", "installed_apps" -> listInstalledAppsResult(settings, arguments)
                    "launch" -> {
                        val target = arguments.optString("target").trim()
                        require(target.isNotBlank()) { "Missing required 'target' argument." }
                        launchTarget(settings, target)
                        observation(900)
                    }
                    "tap", "click", "long_click", "scroll", "text" -> {
                        val displayId = ensureDisplay(settings)
                        val nodeId = arguments.optString("node_id").trim()
                        val text = arguments.optString("text")
                        if (action == "text") require(arguments.has("text")) { "Missing required 'text' argument." }
                        var targetResolution: String? = null
                        if (usesElement && nodeId.isNotBlank()) {
                            val rebound = withContext(Dispatchers.Main) {
                                PhoneUseAccessibilityService.requireConnected().nodeAction(
                                    displayId, nodeId, action, text, arguments.optString("direction", "forward"),
                                )
                            }
                            targetResolution = if (rebound) "rebound_unique_match" else "refreshed_original_node"
                        } else if (action == "text") {
                            withContext(Dispatchers.Main) {
                                PhoneUseAccessibilityService.requireConnected().setFocusedText(displayId, text)
                            }
                        } else {
                            require(action in setOf("tap", "click", "long_click")) { "scroll requires node_id." }
                            when (val point = resolvePoint(arguments, "x", "y")) {
                                is ResolvedPoint.Invalid -> error(point.message)
                                is ResolvedPoint.Valid -> {
                                    withContext(Dispatchers.Main) {
                                        PhoneUseAccessibilityService.requireConnected().gesture(
                                            displayId, point.x, point.y, duration = if (action == "long_click") 700 else 80,
                                        )
                                    }
                                    updateCursorPosition(point.x, point.y)
                                }
                            }
                        }
                        JSONObject(observation(350)).apply {
                            targetResolution?.let { put("target_resolution", it) }
                        }.toString()
                    }
                    "swipe" -> {
                        val displayId = ensureDisplay(settings)
                        val start = resolvePoint(arguments, "x1", "y1")
                        val end = resolvePoint(arguments, "x2", "y2")
                        require(start is ResolvedPoint.Valid) { (start as ResolvedPoint.Invalid).message }
                        require(end is ResolvedPoint.Valid) { (end as ResolvedPoint.Invalid).message }
                        val duration = arguments.optInt("duration_ms", arguments.optInt("durationMs", 500)).coerceIn(50, 10_000)
                        withContext(Dispatchers.Main) {
                            PhoneUseAccessibilityService.requireConnected().gesture(displayId, start.x, start.y, end.x, end.y, duration.toLong())
                        }
                        updateCursorPosition(end.x, end.y, duration)
                        observation(250)
                    }
                    "key" -> {
                        val displayId = ensureDisplay(settings)
                        withContext(Dispatchers.Main) {
                            PhoneUseAccessibilityService.requireConnected().navigationKey(displayId, arguments.optString("key"))
                        }
                        observation(300)
                    }
                    "screenshot" -> {
                        ensureDisplay(settings)
                        observation(0)
                    }
                    "stop" -> {
                        val service = if (returnToAether) PhoneUseAccessibilityService.requireConnected() else null
                        releaseDisplay()
                        val returned = service?.returnToAether() ?: false
                        JSONObject().apply {
                            put("ok", !returnToAether || returned)
                            put("returned_to_aether", returned)
                            put("stdout", if (returned) "Phone Use stopped. Aether is now in the foreground."
                                else "Phone Use session stopped.")
                            if (returnToAether && !returned) put("errmsg", "Phone Use stopped, but Android did not confirm that Aether returned to the foreground.")
                        }.toString()
                    }
                    else -> invalidArguments("Unsupported action '$action'.")
                }.also {
                    val result = runCatching { JSONObject(it) }.getOrNull()
                    diagnosticLogger.event(
                        category = "agent_mode",
                        event = "action_end",
                        level = if (result?.optBoolean("ok", true) == false) "warn" else "info",
                        details = mapOf(
                            "action" to action.ifBlank { "unknown" },
                            "ok" to (result?.optBoolean("ok", true) ?: true),
                            "display_id" to _displayState.value.displayId,
                            "display_active" to _displayState.value.isActive,
                            "message" to result?.optString("errmsg").orEmpty(),
                        ),
                    )
                }
            }.getOrElse { throwable ->
                if (arguments.has("orientation") && createdDisplayOrientation != displayOrientationOverride) {
                    displayOrientationOverride = previousOrientationOverride.takeIf { shizukuDisplayId != null }
                }
                if (throwable is CancellationException) throw throwable
                captureAgentModeFailed(
                    settings = settings,
                    action = action.ifBlank { "unknown" },
                    reason = if (throwable is PhoneUseElementException) throwable.code else "exception",
                    message = throwable.message ?: throwable.javaClass.simpleName,
                )
                val result = JSONObject(toolError(
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    action = action,
                ))
                if (throwable is PhoneUseElementException) {
                    result.put("error_code", throwable.code)
                    result.put("action_succeeded", false)
                    result.put("recovery", "observe_or_use_allowed_alternative")
                    val state = _displayState.value
                    if (state.isActive && state.displayId != null &&
                        (!hasRefreshedAuthorization || authorizationSettings.agentModeAuthorizationEnabled) &&
                        (liveSettings.phoneUseInputStrategy == PhoneUseInputStrategy.ElementTreeFirst || liveSettings.phoneUseAllowStrategySwitch)) {
                        try {
                            val fresh = PhoneUseAccessibilityService.requireConnected()
                                .observeAsync(state.displayId, state.width, state.height)
                            result.put("observation", fresh)
                            result.put("recovery", "use_returned_observation")
                        } catch (cause: Throwable) {
                            if (cause is CancellationException) throw cause
                        }
                    }
                }
                result.toString()
            }
        }
    }

    suspend fun refreshAuthorization(settings: AppSettings) {
        authorizationSettings = settings
        hasRefreshedAuthorization = true
        if (!settings.agentModeAuthorizationEnabled || !PhoneUseAccessibilityService.connected.value) {
            releaseDisplay("Phone Use disabled or Accessibility disconnected.")
        }
        if (_displayState.value.isActive && !_displayState.value.isVirtualDisplay &&
            settings.phoneUseAuthorizationMethod != AgentModeAuthorizationMethod.Accessibility) releaseDisplay()
        if (
            shizukuDisplayId != null &&
            displayOwnerMethod != null &&
            displayOwnerMethod != settings.phoneUseAuthorizationMethod
        ) {
            releaseDisplay("Virtual display reset because Phone Use authorization method changed.")
        }
        _authorizationState.value = inspectAuthorization(settings).also { state ->
            val signature = "${state.issue.name}|${state.detail}|${settings.phoneUseAuthorizationMethod.storageValue}|${settings.agentModeAuthorizationEnabled}"
            if (signature != lastLoggedAuthorizationSignature) {
                lastLoggedAuthorizationSignature = signature
                diagnosticLogger.event(
                    category = "agent_mode",
                    event = "authorization_refreshed",
                    level = if (state.isReady || state.issue == AgentModeAuthorizationIssue.Disabled) "info" else "warn",
                    details = mapOf(
                        "issue" to state.issue.name,
                        "detail" to state.detail,
                        "method" to settings.phoneUseAuthorizationMethod.storageValue,
                        "enabled" to settings.agentModeAuthorizationEnabled,
                    ),
                )
            }
        }
    }

    fun requestShizukuPermission(): AgentModeAuthorizationState {
        val current = inspectShizukuAuthorization()
        if (current.issue == AgentModeAuthorizationIssue.Ready) {
            _authorizationState.value = current
            return current
        }
        if (
            current.issue != AgentModeAuthorizationIssue.ShizukuPermissionMissing &&
            current.issue != AgentModeAuthorizationIssue.ShizukuPermissionDenied
        ) {
            _authorizationState.value = current
            return current
        }

        return runCatching {
            AetherAnalytics.capture(
                event = "permission requested",
                properties = mapOf(
                    "permission" to "shizuku",
                    "source" to "agent_mode_authorization",
                    "current_issue" to current.issue.name.lowercase(),
                ),
            )
            Shizuku.requestPermission(ShizukuPermissionRequestCode)
            AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.ShizukuPermissionMissing,
                detail = "Confirm the Shizuku permission prompt, then refresh Phone Use status.",
            )
        }.getOrElse { throwable ->
            AetherAnalytics.capture(
                event = "permission result",
                properties = mapOf(
                    "permission" to "shizuku",
                    "source" to "agent_mode_authorization",
                    "granted" to false,
                    "result" to "request_failed",
                    "error" to (throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
            AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Error,
                detail = throwable.message ?: "Failed to request Shizuku permission.",
            )
        }.also { _authorizationState.value = it }
    }

    fun cancelVirtualDisplayAuthorization() {
        pendingVirtualDisplayAuthorization = false
    }

    suspend fun authorizeVirtualDisplay(method: AgentModeAuthorizationMethod) {
        _virtualDisplayAuthorizationState.value = AgentModeAuthorizationState()
        _virtualDisplayAuthorizationState.value = try {
            check(Build.VERSION.SDK_INT >= 30) { "Virtual Display requires Android 11 or later." }
            when (method) {
                AgentModeAuthorizationMethod.Root -> requireRootService()
                AgentModeAuthorizationMethod.Shizuku -> {
                    val state = inspectShizukuAuthorization()
                    if (!state.isReady) {
                        if (state.issue == AgentModeAuthorizationIssue.ShizukuPermissionMissing ||
                            state.issue == AgentModeAuthorizationIssue.ShizukuPermissionDenied) {
                            pendingVirtualDisplayAuthorization = true
                            _virtualDisplayAuthorizationState.value = requestShizukuPermission().copy(method = method)
                        } else {
                            _virtualDisplayAuthorizationState.value = state.copy(method = method)
                        }
                        return
                    }
                    requireShizukuService()
                }
                AgentModeAuthorizationMethod.Accessibility -> error("Select Root or Shizuku.")
            }
            AgentModeAuthorizationState(AgentModeAuthorizationIssue.Ready, "Virtual Display authorized.", method)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            AgentModeAuthorizationState(AgentModeAuthorizationIssue.Error, error.message.orEmpty(), method)
        }
    }

    private suspend fun ensureDisplay(settings: AppSettings): Int {
        check(!hasRefreshedAuthorization || authorizationSettings.agentModeAuthorizationEnabled) {
            "Phone Use was disabled during this turn."
        }
        check(!hasRefreshedAuthorization ||
            authorizationSettings.phoneUseAuthorizationMethod == settings.phoneUseAuthorizationMethod) {
            "Phone Use mode changed during this turn. Start a new turn to use the selected mode."
        }
        PhoneUseAccessibilityService.requireConnected()
        if (settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Accessibility) {
            if (shizukuDisplayId != null) releaseDisplay()
            val spec = currentDeviceDisplaySpec()
            _displayState.value = _displayState.value.copy(
                isActive = true, isVirtualDisplay = false, displayId = Display.DEFAULT_DISPLAY,
                width = spec.width, height = spec.height, status = "Phone Use foreground ready",
                lastUpdatedMillis = System.currentTimeMillis(),
            )
            return Display.DEFAULT_DISPLAY
        }
        check(Build.VERSION.SDK_INT >= 30) { "Virtual Display accessibility requires Android 11 or later." }
        val orientation = displayOrientationOverride ?:
            (if (hasRefreshedAuthorization) authorizationSettings else settings).agentModeDisplayOrientation
        val service = requireAgentModeService(settings)
        val serviceBinder = service.asBinder()
        shizukuDisplayId?.let { displayId ->
            if (isCurrentDisplayOwner(settings, serviceBinder) && createdDisplayOrientation == orientation) {
                return displayId
            }
            releaseDisplay("Virtual display reset because Phone Use service or orientation changed. Relaunch the target app.", preserveOrientationOverride = true)
        }
        val deviceSpec = currentDeviceDisplaySpec()
        val (width, height) = agentModeDisplayDimensions(deviceSpec.width, deviceSpec.height, orientation)
        val displaySpec = deviceSpec.copy(width = width, height = height)
        val displayId = service.createOwnedDisplay(
            AgentDisplayName,
            displaySpec.width,
            displaySpec.height,
            displaySpec.densityDpi,
        )
        shizukuDisplayId = displayId
        createdDisplayOrientation = orientation
        displayOwnerMethod = settings.phoneUseAuthorizationMethod
        displayOwnerBinder = serviceBinder
        diagnosticLogger.event(
            category = "agent_mode",
            event = "display_created",
            details = mapOf(
                "display_id" to displayId,
                "width" to displaySpec.width,
                "height" to displaySpec.height,
                "density_dpi" to displaySpec.densityDpi,
                "method" to settings.phoneUseAuthorizationMethod.storageValue,
            ),
        )
        _displayState.value = AgentModeDisplayState(
            isActive = true,
            isVirtualDisplay = true,
            displayId = displayId,
            width = displaySpec.width,
            height = displaySpec.height,
            displays = currentDisplays(settings, displayId),
            status = "${settings.phoneUseAuthorizationMethod.displayName} virtual display ready",
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        attachCurrentPreviewSurface(settings, displayId)
        return displayId
    }

    fun stopDisplay() {
        releaseDisplay()
    }

    suspend fun attachPreviewSurface(settings: AppSettings, surface: Surface) = withContext(Dispatchers.IO) {
        previewSurface = surface
        val displayId = currentManagedDisplayId(settings) ?: return@withContext
        attachCurrentPreviewSurface(settings, displayId)
    }

    suspend fun detachPreviewSurface(settings: AppSettings, surface: Surface) = withContext(Dispatchers.IO) {
        if (previewSurface !== surface) return@withContext
        previewSurface = null
        val displayId = currentManagedDisplayId(settings) ?: run {
            _displayState.value = _displayState.value.copy(
                isLivePreviewActive = false,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
            return@withContext
        }
        runCatching {
            requireAgentModeService(settings).detachPreviewSurface(displayId)
        }
        val state = _displayState.value
        _displayState.value = state.copy(
            isLivePreviewActive = false,
            status = if (state.isActive) {
                "${settings.phoneUseAuthorizationMethod.displayName} virtual display ready"
            } else {
                state.status
            },
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    suspend fun refreshDisplays(settings: AppSettings) {
        currentManagedDisplayId(settings)
        val state = _displayState.value
        _displayState.value = state.copy(
            displays = currentDisplays(settings, state.displayId),
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun attachCurrentPreviewSurface(settings: AppSettings, displayId: Int) {
        val surface = previewSurface?.takeIf { it.isValid } ?: return
        runCatching {
            requireAgentModeService(settings).attachPreviewSurface(displayId, surface)
        }.onSuccess {
            val state = _displayState.value
            if (state.displayId == displayId && state.isActive) {
                _displayState.value = state.copy(
                    isLivePreviewActive = true,
                    status = "Streaming virtual display",
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
            }
        }.onFailure { throwable ->
            val state = _displayState.value
            if (state.displayId == displayId && state.isActive) {
                _displayState.value = state.copy(
                    isLivePreviewActive = false,
                    status = throwable.message ?: "Live preview surface is not available.",
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private suspend fun launchTarget(
        settings: AppSettings,
        target: String,
    ) {
        val displayId = ensureDisplay(settings)
        val launchPackage = resolveLaunchPackage(settings, target)
            ?: error("No launchable app matched '$target'. Try a package name such as com.android.chrome, or a shorter app label.")
        if (displayId == Display.DEFAULT_DISPLAY) {
            val service = PhoneUseAccessibilityService.requireConnected()
            val intent = context.packageManager.getLaunchIntentForPackage(launchPackage)
                ?: error("No launchable activity for '$launchPackage'.")
            withContext(Dispatchers.Main) { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            requireAgentModeService(settings).launchPackage(launchPackage, displayId)
        }
    }

    private suspend fun resolveLaunchPackage(
        settings: AppSettings,
        target: String,
    ): String? {
        val normalizedTarget = target.trim().lowercase()
        if (normalizedTarget.isBlank()) return null
        context.packageManager.getLaunchIntentForPackage(target)?.let { return target }

        val launchables = currentInstalledApps(settings)
        val tokens = normalizedTarget.split(Regex("\\s+"))
            .filter { it.length > 2 && it !in setOf("app", "browser", "managed") }
        return launchables.firstOrNull { app ->
            app.packageName.equals(normalizedTarget, ignoreCase = true) ||
                app.appName.equals(target, ignoreCase = true)
        }?.packageName ?: launchables.firstOrNull { app ->
            app.packageName.lowercase().contains(normalizedTarget) ||
                app.appName.lowercase().contains(normalizedTarget) ||
                app.activityName.lowercase().contains(normalizedTarget) ||
                tokens.any { token ->
                    app.packageName.lowercase().contains(token) ||
                        app.appName.lowercase().contains(token) ||
                        app.activityName.lowercase().contains(token)
                }
        }?.packageName ?: target.takeIf {
            it.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+"""))
        }
    }

    private suspend fun listInstalledAppsResult(
        settings: AppSettings,
        arguments: JSONObject,
    ): String {
        val query = arguments.optString("query").trim()
        val normalizedQuery = query.lowercase()
        val includeSystem = arguments.optBoolean(
            "include_system",
            arguments.optBoolean("includeSystem", true),
        )
        val maxResults = arguments.optInt(
            "max_results",
            arguments.optInt("maxResults", 500),
        ).coerceIn(1, 1_000)
        val apps = currentInstalledApps(settings)
            .asSequence()
            .filter { includeSystem || !it.isSystemApp }
            .filter { app ->
                normalizedQuery.isBlank() ||
                    app.packageName.lowercase().contains(normalizedQuery) ||
                    app.appName.lowercase().contains(normalizedQuery) ||
                    app.activityName.lowercase().contains(normalizedQuery)
            }
            .toList()
        val visibleApps = apps.take(maxResults)
        return JSONObject().apply {
            put("ok", true)
            put("count", apps.size)
            put("truncated", apps.size > visibleApps.size)
            put(
                "apps",
                JSONArray().apply {
                    visibleApps.forEach { app ->
                        put(
                            JSONObject().apply {
                                put("app_name", app.appName)
                                put("package_name", app.packageName)
                                put("activity_name", app.activityName)
                                put("enabled", app.isEnabled)
                                put("system", app.isSystemApp)
                                put("launchable", true)
                            }
                        )
                    }
                },
            )
            put(
                "stdout",
                buildString {
                    append("Found ")
                    append(apps.size)
                    append(if (includeSystem) " launchable apps." else " non-system launchable apps.")
                    if (apps.size > visibleApps.size) {
                        append(" Showing ")
                        append(visibleApps.size)
                        append(".")
                    }
                    visibleApps.forEach { app ->
                        append('\n')
                        append(app.appName)
                        append(" -> ")
                        append(app.packageName)
                    }
                },
            )
        }.toString()
    }

    private suspend fun currentInstalledApps(settings: AppSettings): List<AgentModeInstalledAppInfo> {
        if (settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Accessibility) return currentInstalledAppsLocal()
        val privilegedApps = runCatching {
            parseInstalledApps(requireAgentModeService(settings).listInstalledAppsJson())
        }.getOrNull()
        return (privilegedApps?.takeIf { it.isNotEmpty() } ?: currentInstalledAppsLocal())
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
    }

    @Suppress("DEPRECATION")
    private fun currentInstalledAppsLocal(): List<AgentModeInstalledAppInfo> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val applicationInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            AgentModeInstalledAppInfo(
                packageName = activityInfo.packageName.orEmpty(),
                appName = info.loadLabel(packageManager).toString(),
                activityName = activityInfo.name.orEmpty(),
                isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                isEnabled = activityInfo.enabled && applicationInfo.enabled,
            )
        }
    }

    private fun parseInstalledApps(rawValue: String): List<AgentModeInstalledAppInfo> {
        val array = JSONArray(rawValue)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.optString("package_name").ifBlank {
                    item.optString("packageName")
                }
                if (packageName.isBlank()) continue
                add(
                    AgentModeInstalledAppInfo(
                        packageName = packageName,
                        appName = item.optString("app_name").ifBlank {
                            item.optString("appName").ifBlank { packageName }
                        },
                        activityName = item.optString("activity_name").ifBlank {
                            item.optString("activityName")
                        },
                        isSystemApp = item.optBoolean("system"),
                        isEnabled = item.optBoolean("enabled", true),
                    )
                )
            }
        }
    }

    private suspend fun observeAfterDelay(settings: AppSettings, delayMillis: Long): String {
        val expectedDisplayId = _displayState.value.displayId
        if (delayMillis > 0) delay(delayMillis)
        val state = _displayState.value
        check(state.isActive && state.displayId == expectedDisplayId) { "Phone Use session stopped before observation." }
        val displayId = state.displayId ?: error("Phone Use session has no display.")
        return PhoneUseAccessibilityService.requireConnected()
            .observeAsync(displayId, state.width, state.height).toString()
    }

    private suspend fun captureAfterDelay(
        settings: AppSettings,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        delayMillis: Long,
        extras: JSONObject? = null,
    ): String {
        if (delayMillis > 0) delay(delayMillis)
        val captureId = "capture-${System.currentTimeMillis()}"
        val previewFile = File(cacheDirectory, "$captureId.$AgentModeCaptureExtension")
        captureImageFile(settings, previewFile)
        if (!previewFile.isFile || previewFile.length() <= 0L) {
            error("Phone Use screenshot capture produced an empty file.")
        }
        val bytes = previewFile.readBytes()
        val latestPreviewFile = File(cacheDirectory, "latest.$AgentModeCaptureExtension")
        previewFile.copyTo(latestPreviewFile, overwrite = true)
        val previewPath = previewFile.absolutePath
        val workspacePath = "$workspaceDirectory/agent-mode/$captureId.$AgentModeCaptureExtension"
        runtimeWorkspaceFileBridge.writeWorkspaceBytes(
            settings = settings,
            workspaceDirectory = workspaceDirectory,
            termuxWorkspaceDirectory = termuxWorkspaceDirectory,
            absolutePath = workspacePath,
            bytes = bytes,
        ).getOrThrow()
        val displayId = ensureDisplay(settings)
        val state = _displayState.value
        _displayState.value = state.copy(
            isActive = true,
            displayId = displayId,
            displays = currentDisplays(settings, displayId),
            latestPreviewPath = previewPath,
            latestWorkspacePath = workspacePath,
            lastUpdatedMillis = System.currentTimeMillis(),
            status = "Captured virtual display",
        )
        return JSONObject().apply {
            put("ok", true)
            put("mode", if (state.isVirtualDisplay) "virtual_display" else "foreground")
            put("display_id", displayId)
            put("width", state.width)
            put("height", state.height)
            val (imageWidth, imageHeight) = agentModeScreenshotSize(state.width, state.height, AgentModeCaptureMaxEdge)
            put("image_width", imageWidth)
            put("image_height", imageHeight)
            put("coordinate_space", AgentModeCoordinateSpace)
            put("screenshot_path", workspacePath)
            put("preview_path", previewPath)
            // cursor_x/cursor_y are display pixels (kept for the UI); cursor_norm_* are the 0..1000 values to reuse.
            state.cursorX?.let {
                put("cursor_x", it)
                put("cursor_norm_x", normalizeAgentModePixel(it, state.width))
            }
            state.cursorY?.let {
                put("cursor_y", it)
                put("cursor_norm_y", normalizeAgentModePixel(it, state.height))
            }
            extras?.keys()?.forEach { key -> put(key, extras.get(key)) }
            put("screenshot_mime_type", AgentModeCaptureMimeType)
            put("screenshot_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("stdout", "Captured Phone Use screenshot: $workspacePath")
        }.toString()
    }

    private fun updateCursorPosition(
        x: Int,
        y: Int,
        animationDurationMillis: Int = 220,
    ) {
        val state = _displayState.value
        _displayState.value = state.copy(
            cursorX = x.coerceIn(0, state.width),
            cursorY = y.coerceIn(0, state.height),
            cursorAnimationDurationMillis = animationDurationMillis.coerceIn(80, 1_200),
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun captureImageFile(
        settings: AppSettings,
        outputFile: File,
    ) {
        val displayId = ensureDisplay(settings)
        captureMutex.withLock {
            outputFile.parentFile?.mkdirs()
            runCatching { outputFile.delete() }
            try {
                withContext(Dispatchers.Main) {
                    PhoneUseAccessibilityService.requireConnected().screenshot(
                        displayId, outputFile, AgentModeCaptureMaxEdge, AgentModeCaptureJpegQuality,
                    )
                }
            } catch (throwable: Throwable) {
                runCatching { outputFile.delete() }
                throw throwable
            }
        }
    }

    private suspend fun statusResult(settings: AppSettings): String {
        currentManagedDisplayId(settings)
        val state = _displayState.value
        val displays = currentDisplays(settings, state.displayId)
        _displayState.value = state.copy(
            displays = displays,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        return JSONObject().apply {
            put("ok", true)
            put("active", state.isActive)
            if (settings.phoneUseVirtualDisplayEnabled) {
                put("orientation", (createdDisplayOrientation ?: settings.agentModeDisplayOrientation).storageValue)
            }
            put("mode", if (settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Accessibility) "foreground" else "virtual_display")
            put("display_id", state.displayId)
            put("width", state.width)
            put("height", state.height)
            put("live_preview", state.isLivePreviewActive)
            put(
                "displays",
                org.json.JSONArray().apply {
                    displays.forEach { display ->
                        put(
                            JSONObject().apply {
                                put("display_id", display.displayId)
                                put("name", display.name)
                                put("width", display.width)
                                put("height", display.height)
                                put("is_aether_display", display.isAetherDisplay)
                            }
                        )
                    }
                },
            )
            put("screenshot_path", state.latestWorkspacePath)
            put("stdout", if (state.isActive) "Phone Use display is active." else "Phone Use display is stopped.")
        }.toString()
    }

    private fun releaseDisplay(status: String = "Virtual display stopped", preserveOrientationOverride: Boolean = false) {
        if (!preserveOrientationOverride) displayOrientationOverride = null
        createdDisplayOrientation = null
        shizukuDisplayId?.let { displayId ->
            diagnosticLogger.event(
                category = "agent_mode",
                event = "display_released",
                details = mapOf(
                    "display_id" to displayId,
                    "method" to displayOwnerMethod?.storageValue.orEmpty(),
                    "status" to status,
                ),
            )
            when (displayOwnerMethod) {
                AgentModeAuthorizationMethod.Shizuku -> runCatching { shizukuService?.releaseDisplay(displayId) }
                AgentModeAuthorizationMethod.Root -> runCatching { rootService?.releaseDisplay(displayId) }
                AgentModeAuthorizationMethod.Accessibility -> Unit
                null -> {
                    runCatching { shizukuService?.releaseDisplay(displayId) }
                    runCatching { rootService?.releaseDisplay(displayId) }
                }
            }
        }
        shizukuDisplayId = null
        displayOwnerMethod = null
        displayOwnerBinder = null
        _displayState.value = AgentModeDisplayState(
            isActive = false,
            displays = currentDisplaysLocal(null),
            status = status,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun currentManagedDisplayId(settings: AppSettings): Int? {
        val displayId = shizukuDisplayId ?: return null
        val service = runCatching { requireAgentModeService(settings) }
            .getOrElse { throwable ->
                if (displayOwnerMethod == settings.phoneUseAuthorizationMethod) {
                    releaseDisplay(
                        throwable.message ?: "Virtual display reset because Phone Use service is unavailable."
                    )
                }
                return null
            }
        if (isCurrentDisplayOwner(settings, service.asBinder())) {
            return displayId
        }
        releaseDisplay("Virtual display reset because Phone Use authorization service changed.")
        return null
    }

    private fun isCurrentDisplayOwner(
        settings: AppSettings,
        binder: IBinder,
    ): Boolean =
        displayOwnerMethod == settings.phoneUseAuthorizationMethod &&
            displayOwnerBinder === binder &&
            binder.isBinderAlive

    private fun clearShizukuService(displayStatus: String) {
        val handle = shizukuServiceHandle
        shizukuService = null
        shizukuServiceHandle = null
        if (displayOwnerMethod == AgentModeAuthorizationMethod.Shizuku) {
            releaseDisplay(displayStatus)
        }
        handle?.destroy()
    }

    private fun clearRootService(displayStatus: String) {
        val handle = rootServiceHandle
        rootService = null
        rootServiceHandle = null
        if (displayOwnerMethod == AgentModeAuthorizationMethod.Root) {
            releaseDisplay(displayStatus)
        }
        handle?.destroy()
    }

    private fun captureAgentModeFailed(
        settings: AppSettings,
        action: String,
        reason: String,
        message: String,
    ) {
        diagnosticLogger.event(
            category = "agent_mode",
            event = "action_failed",
            level = "warn",
            details = mapOf(
                "action" to action,
                "reason" to reason,
                "message" to message,
                "authorization_enabled" to settings.agentModeAuthorizationEnabled,
                "authorization_method" to settings.phoneUseAuthorizationMethod.storageValue,
                "display_active" to _displayState.value.isActive,
            ),
        )
        AetherAnalytics.capture(
            event = "agent mode failed",
            properties = mapOf(
                "action" to action,
                "reason" to reason,
                "message" to message.take(280),
                "authorization_enabled" to settings.agentModeAuthorizationEnabled,
                "authorization_method" to settings.phoneUseAuthorizationMethod.storageValue,
                "display_active" to _displayState.value.isActive,
            ),
        )
    }

    private suspend fun currentDisplays(
        settings: AppSettings,
        aetherDisplayId: Int?,
    ): List<AgentModeDisplayInfo> {
        if (settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Accessibility) return currentDisplaysLocal(null)
        val privilegedDisplays = runCatching {
            parseDisplays(requireAgentModeService(settings).listDisplaysJson(), aetherDisplayId)
        }.onFailure {
        }.getOrNull()
        if (privilegedDisplays != null) return privilegedDisplays
        return currentDisplaysLocal(aetherDisplayId)
    }

    private fun currentDisplaysLocal(aetherDisplayId: Int?): List<AgentModeDisplayInfo> =
        displayManager.displays.map { display ->
            val size = Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            AgentModeDisplayInfo(
                displayId = display.displayId,
                name = display.name.orEmpty(),
                width = size.x,
                height = size.y,
                isAetherDisplay = display.displayId == aetherDisplayId ||
                    display.name.orEmpty().contains(AgentDisplayName, ignoreCase = true),
            )
        }.sortedBy { it.displayId }

    private fun parseDisplays(
        rawValue: String,
        aetherDisplayId: Int?,
    ): List<AgentModeDisplayInfo> {
        val array = org.json.JSONArray(rawValue)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val displayId = item.optInt("display_id")
                add(
                    AgentModeDisplayInfo(
                        displayId = displayId,
                        name = item.optString("name"),
                        width = item.optInt("width"),
                        height = item.optInt("height"),
                        isAetherDisplay = item.optBoolean("is_aether_display") ||
                            displayId == aetherDisplayId ||
                            item.optString("name").contains(AgentDisplayName, ignoreCase = true),
                    )
                )
            }
        }.sortedBy { it.displayId }
    }

    private suspend fun requireAgentModeService(settings: AppSettings): IAetherAgentModeService =
        when (settings.phoneUseAuthorizationMethod) {
            AgentModeAuthorizationMethod.Accessibility -> error("Foreground Phone Use does not require a privileged service.")
            AgentModeAuthorizationMethod.Shizuku -> requireShizukuService()
            AgentModeAuthorizationMethod.Root -> requireRootService()
        }

    private suspend fun requireRootService(): IAetherAgentModeService = withContext(Dispatchers.IO) {
        val existing = rootService
        if (existing != null) {
            if (existing.asBinder().isBinderAlive) {
                return@withContext existing
            }
            clearRootService("Root Phone Use service disconnected. Virtual display was reset.")
        }
        val suPath = findSuPath()
        if (suPath.isBlank()) {
            _authorizationState.value = AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.RootUnavailable,
                detail = "No su binary was detected on this device.",
            )
            error("No su binary was detected on this device.")
        }
        val handle = runCatching {
            AgentModeServiceLauncher.launch(context, AgentModeServiceStartTimeoutMillis) { command ->
                AgentModeServiceLauncher.startRootProcess(suPath, command)
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            diagnosticLogger.event(
                category = "agent_mode",
                event = "root_service_start_failed",
                level = "warn",
                details = mapOf("message" to throwable.message.orEmpty()),
            )
            _authorizationState.value = AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.RootPermissionDenied,
                detail = "Root Phone Use service failed to start. Check that su can be granted to Aether.",
            )
            error(
                "Root Phone Use service failed to start. Check that su can be granted to Aether. " +
                    throwable.message.orEmpty()
            )
        }
        val service = handle.service
        rootServiceHandle = handle
        rootService = service
        _authorizationState.value = AgentModeAuthorizationState(
            issue = AgentModeAuthorizationIssue.Ready,
            detail = "Root Phone Use service is connected.",
        )
        service
    }

    @Suppress("RestrictedApi")
    private suspend fun requireShizukuService(): IAetherAgentModeService = shizukuServiceMutex.withLock {
        val existing = shizukuService
        if (existing != null) {
            if (existing.asBinder().isBinderAlive) {
                return@withLock existing
            }
            clearShizukuService("Shizuku Phone Use service disconnected. Virtual display was reset.")
        }
        if (!Shizuku.pingBinder()) {
            error("Shizuku is not running.")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            error("Aether does not have Shizuku permission. Grant it in Shizuku first.")
        }
        diagnosticLogger.event(
            category = "agent_mode",
            event = "shizuku_service_start",
            details = mapOf("timeout_ms" to AgentModeServiceStartTimeoutMillis),
        )
        val startMillis = System.currentTimeMillis()
        val handle = try {
            AgentModeServiceLauncher.launch(
                context,
                AgentModeServiceStartTimeoutMillis,
                AgentModeServiceLauncher::startShizukuProcess,
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            diagnosticLogger.event(
                category = "agent_mode",
                event = "shizuku_service_start_failed",
                level = "warn",
                details = mapOf(
                    "duration_ms" to (System.currentTimeMillis() - startMillis),
                    "message" to throwable.message.orEmpty(),
                    "shizuku_version" to runCatching { Shizuku.getVersion() }.getOrDefault(-1),
                    "shizuku_server_patch_version" to runCatching { Shizuku.getServerPatchVersion() }.getOrDefault(-1),
                ),
            )
            error(
                "Shizuku Phone Use service failed to start: ${throwable.message.orEmpty()} " +
                    "Restart Shizuku or update Shizuku, then refresh Phone Use status."
            )
        }
        diagnosticLogger.event(
            category = "agent_mode",
            event = "shizuku_service_started",
            details = mapOf("duration_ms" to (System.currentTimeMillis() - startMillis)),
        )
        val service = handle.service
        runCatching {
            service.asBinder().linkToDeath({
                if (shizukuServiceHandle === handle) {
                    clearShizukuService("Shizuku Phone Use service disconnected. Virtual display was reset.")
                }
            }, 0)
        }
        shizukuServiceHandle = handle
        shizukuService = service
        service
    }

    private suspend fun inspectAuthorization(settings: AppSettings): AgentModeAuthorizationState =
        when {
            !settings.agentModeAuthorizationEnabled -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Disabled,
                detail = "Phone Use authorization is disabled.",
            )

            !PhoneUseAccessibilityService.connected.value -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.AccessibilityMissing,
                detail = "Enable Aether in Android Accessibility settings.",
            )

            settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Accessibility -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Ready,
                detail = "Accessibility is connected. Phone Use controls the foreground screen.",
            )

            Build.VERSION.SDK_INT < 30 -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Error,
                detail = "Virtual Display accessibility requires Android 11 or later.",
            )

            settings.phoneUseAuthorizationMethod == AgentModeAuthorizationMethod.Root -> inspectRootAuthorization()

            else -> inspectShizukuAuthorization()
        }

    private suspend fun inspectRootAuthorization(): AgentModeAuthorizationState = withContext(Dispatchers.IO) {
        val existing = rootService
        if (existing != null && existing.asBinder().isBinderAlive) {
            return@withContext AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Ready,
                detail = "Root Phone Use service is already connected.",
            )
        }
        if (existing != null) {
            clearRootService("Root Phone Use service disconnected. Virtual display was reset.")
        }

        val suPath = findSuPath()
        if (suPath.isBlank()) {
            return@withContext AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.RootUnavailable,
                detail = "No su binary was detected on this device.",
            )
        }

        val probe = runRootAuthorizationProbe(suPath)
        when {
            probe.launchError.isNotBlank() -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Error,
                detail = probe.launchError,
            )

            probe.exitCode == 0 -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Ready,
                detail = "Root authorization is granted.",
            )

            probe.timedOut -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.RootPermissionMissing,
                detail = "Root authorization timed out. Grant su to Aether, then refresh Phone Use status.",
            )

            else -> AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.RootPermissionDenied,
                detail = probe.combinedOutput().ifBlank {
                    "Root authorization was not granted. Grant su to Aether, then refresh Phone Use status."
                }.take(280),
            )
        }
    }

    private fun inspectShizukuAuthorization(): AgentModeAuthorizationState {
        if (!isAnyPackageInstalled(ShizukuManagerPackages)) {
            return AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.ShizukuNotInstalled,
                detail = "Install Shizuku before using Shizuku Phone Use.",
            )
        }
        val isRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!isRunning) {
            return AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.ShizukuNotRunning,
                detail = "Start Shizuku, then refresh Phone Use status.",
            )
        }
        return runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                AgentModeAuthorizationState(
                    issue = AgentModeAuthorizationIssue.Ready,
                    detail = "Shizuku permission is granted.",
                )
            } else {
                AgentModeAuthorizationState(
                    issue = AgentModeAuthorizationIssue.ShizukuPermissionMissing,
                    detail = "Grant Aether permission in Shizuku before using Phone Use.",
                )
            }
        }.getOrElse { throwable ->
            AgentModeAuthorizationState(
                issue = AgentModeAuthorizationIssue.Error,
                detail = throwable.message ?: "Unable to inspect Shizuku permission.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun isAnyPackageInstalled(packageNames: List<String>): Boolean =
        packageNames.any { packageName ->
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
        }

    private fun findSuPath(): String {
        val commonPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
        )
        commonPaths.firstOrNull { path ->
            File(path).let { it.exists() && it.canExecute() }
        }?.let { return it }

        val result = runProcess(
            command = listOf("sh", "-c", "command -v su 2>/dev/null || true"),
            timeoutMillis = RootAuthorizationProbeTimeoutMillis,
        )
        return result.stdout.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    private fun runRootAuthorizationProbe(suPath: String): RootCommandResult =
        runProcess(
            command = listOf(suPath, "-c", "true"),
            timeoutMillis = RootAuthorizationProbeTimeoutMillis,
        )

    private fun runProcess(
        command: List<String>,
        timeoutMillis: Long,
    ): RootCommandResult {
        val process = runCatching {
            ProcessBuilder(command).start()
        }.getOrElse { throwable ->
            return RootCommandResult(
                exitCode = -1,
                launchError = throwable.message.orEmpty(),
            )
        }

        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroy() }
            if (!process.waitFor(400, TimeUnit.MILLISECONDS)) {
                runCatching { process.destroyForcibly() }
            }
        }

        val stdout = runCatching {
            process.inputStream.bufferedReader().readText()
        }.getOrDefault("")
        val stderr = runCatching {
            process.errorStream.bufferedReader().readText()
        }.getOrDefault("")
        return RootCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout,
            stderr = stderr,
            timedOut = !finished,
        )
    }


    private sealed interface ResolvedPoint {
        data class Valid(val x: Int, val y: Int) : ResolvedPoint
        data class Invalid(val message: String) : ResolvedPoint
    }

    private fun resolvePoint(arguments: JSONObject, xKey: String, yKey: String): ResolvedPoint {
        val state = _displayState.value
        val x = resolveAgentModeCoordinate(xKey, arguments.optDouble(xKey, Double.NaN), state.width)
        val y = resolveAgentModeCoordinate(yKey, arguments.optDouble(yKey, Double.NaN), state.height)
        return when {
            x is AgentModeCoordinateResult.OutOfRange -> ResolvedPoint.Invalid(x.message)
            y is AgentModeCoordinateResult.OutOfRange -> ResolvedPoint.Invalid(y.message)
            x is AgentModeCoordinateResult.Valid && y is AgentModeCoordinateResult.Valid ->
                ResolvedPoint.Valid(x.pixel, y.pixel)
            else -> ResolvedPoint.Invalid(
                "Both '$xKey' and '$yKey' are required, using normalized 0..$AgentModeNormalizedCoordinateMax coordinates.",
            )
        }
    }

    private fun invalidArguments(message: String): String =
        JSONObject().apply {
            put("ok", false)
            put("errmsg", message)
        }.toString()

    private fun toolError(
        message: String,
        action: String,
    ): String = JSONObject().apply {
        put("ok", false)
        put("action", action)
        put("errmsg", message)
        put("stdout", "")
    }.toString()

    private fun currentDeviceDisplaySpec(): DisplaySpec {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val size = Point()
        @Suppress("DEPRECATION")
        display?.getRealSize(size)
        val metrics = context.resources.displayMetrics
        return DisplaySpec(
            width = size.x.takeIf { it > 0 }
                ?: metrics.widthPixels.takeIf { it > 0 }
                ?: FallbackAgentDisplayWidth,
            height = size.y.takeIf { it > 0 }
                ?: metrics.heightPixels.takeIf { it > 0 }
                ?: FallbackAgentDisplayHeight,
            densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: FallbackAgentDisplayDensityDpi,
        )
    }

    private data class DisplaySpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    private data class RootCommandResult(
        val exitCode: Int,
        val stdout: String = "",
        val stderr: String = "",
        val timedOut: Boolean = false,
        val launchError: String = "",
    ) {
        fun combinedOutput(): String = listOf(stdout, stderr, launchError)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
    }
}
