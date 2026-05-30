package com.zhousl.aether

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.zhousl.aether.data.AgentExtensionsRepository
import com.zhousl.aether.data.AgentModeController
import com.zhousl.aether.data.AgentSkillManager
import com.zhousl.aether.data.AetherDiagnosticLogger
import com.zhousl.aether.data.ChatRepository
import com.zhousl.aether.data.RootSetupController
import com.zhousl.aether.data.ChatStateStore
import com.zhousl.aether.data.ScheduledTask
import com.zhousl.aether.data.ScheduledTaskManager
import com.zhousl.aether.data.ScheduledTaskRepository
import com.zhousl.aether.data.ScheduledTaskScheduler
import com.zhousl.aether.data.SessionExecutionManager
import com.zhousl.aether.data.SettingsRepository
import com.zhousl.aether.data.WebToolsClient
import com.zhousl.aether.data.WorkspaceFileBridge
import com.zhousl.aether.termux.TermuxBashTool
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AetherApplication : Application() {
    val runtime: AetherAppRuntime by lazy(LazyThreadSafetyMode.NONE) {
        AetherAppRuntime(this)
    }
    @Volatile
    private var isPostHogInitialized = false

    override fun onCreate() {
        super.onCreate()
        runtime.initialize()
    }

    fun initializePostHog() {
        if (isPostHogInitialized || BuildConfig.POSTHOG_API_KEY.isBlank()) return
        synchronized(this) {
            if (isPostHogInitialized || BuildConfig.POSTHOG_API_KEY.isBlank()) return
            val config = PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST,
            ).apply {
                captureApplicationLifecycleEvents = true
                captureDeepLinks = true
                captureScreenViews = true
                debug = BuildConfig.DEBUG
                releaseIdentifier = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                errorTrackingConfig.autoCapture = true
                errorTrackingConfig.inAppIncludes.addAll(
                    listOf(
                        "com.zhousl.aether",
                        "com.baimoqilin.aether",
                    ),
                )
            }
            PostHogAndroid.setup(this, config)
            isPostHogInitialized = true
        }
    }
}

class AetherAppRuntime(
    private val application: AetherApplication,
) {
    val diagnosticLogger = AetherDiagnosticLogger(application)
    private val appScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                diagnosticLogger.exception(
                    category = "coroutine",
                    event = "uncaught_exception",
                    throwable = throwable,
                )
            }
    )

    val settingsRepository = SettingsRepository(application)
    val chatRepository = ChatRepository(application)
    val extensionsRepository = AgentExtensionsRepository(application)
    val scheduledTaskRepository = ScheduledTaskRepository(application)
    val bashTool = TermuxBashTool(
        context = application,
        diagnosticLogger = diagnosticLogger,
    )
    val rootSetupController = RootSetupController(
        context = application,
        bashTool = bashTool,
        diagnosticLogger = diagnosticLogger,
    )
    val workspaceFileBridge = WorkspaceFileBridge(
        context = application,
        bashTool = bashTool,
    )
    val agentModeController = AgentModeController(
        context = application,
        bashTool = bashTool,
        workspaceFileBridge = workspaceFileBridge,
        diagnosticLogger = diagnosticLogger,
    )
    val skillManager = AgentSkillManager(
        context = application,
        extensionsRepository = extensionsRepository,
    )
    val webToolsClient = WebToolsClient()
    val appForegroundTracker = AppForegroundTracker()
    val notificationController = AetherNotificationController(application)
    val scheduledTaskScheduler = ScheduledTaskScheduler(
        context = application,
        diagnosticLogger = diagnosticLogger,
    )
    val scheduledTaskManager = ScheduledTaskManager(
        repository = scheduledTaskRepository,
        scheduler = scheduledTaskScheduler,
    )
    val chatStateStore = ChatStateStore(
        scope = appScope,
        chatRepository = chatRepository,
    )
    val sessionExecutionManager = SessionExecutionManager(
        application = application,
        scope = appScope,
        settingsRepository = settingsRepository,
        extensionsRepository = extensionsRepository,
        chatStateStore = chatStateStore,
        bashTool = bashTool,
        workspaceFileBridge = workspaceFileBridge,
        rootSetupController = rootSetupController,
        agentModeController = agentModeController,
        skillManager = skillManager,
        scheduledTaskManager = scheduledTaskManager,
        webToolsClient = webToolsClient,
        notificationController = notificationController,
        appForegroundTracker = appForegroundTracker,
        diagnosticLogger = diagnosticLogger,
    )

    fun initialize() {
        diagnosticLogger.installUncaughtExceptionHandler()
        diagnosticLogger.event(
            category = "app",
            event = "startup",
            details = mapOf(
                "version_name" to BuildConfig.VERSION_NAME,
                "version_code" to BuildConfig.VERSION_CODE,
                "debug" to BuildConfig.DEBUG,
            ),
        )
        notificationController.ensureChannels()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appForegroundTracker)
        appScope.launch {
            if (settingsRepository.settings.first().privacyPolicyAccepted) {
                initializePostHog()
            }
        }
        appScope.launch {
            scheduledTaskManager.rescheduleAll()
        }
    }

    fun initializePostHog() {
        application.initializePostHog()
    }

    fun handleScheduledTaskAlarm(
        taskId: String,
        pendingResult: android.content.BroadcastReceiver.PendingResult,
    ) {
        diagnosticLogger.event(
            category = "scheduled_task",
            event = "alarm_received",
            details = mapOf("task_id" to taskId),
        )
        appScope.launch {
            try {
                val task = scheduledTaskManager.markTriggeredAndScheduleNext(taskId)
                if (task == null) {
                    diagnosticLogger.event(
                        category = "scheduled_task",
                        event = "trigger_missing_task",
                        level = "warn",
                        details = mapOf("task_id" to taskId),
                    )
                    return@launch
                }
                val started = startScheduledTaskFromAlarm(task)
                diagnosticLogger.event(
                    category = "scheduled_task",
                    event = if (started) "trigger_started" else "trigger_skipped",
                    level = if (started) "info" else "warn",
                    details = mapOf("task_id" to taskId),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun startScheduledTaskFromAlarm(task: ScheduledTask): Boolean {
        return try {
            if (settingsRepository.settings.first().keepTasksRunningInBackground) {
                runCatching {
                    AetherForegroundService.ensureRunning(application)
                }.onFailure { throwable ->
                    diagnosticLogger.exception(
                        category = "scheduled_task",
                        event = "foreground_service_start_failed",
                        throwable = throwable,
                        details = mapOf("task_id" to task.id),
                    )
                }
            }
            sessionExecutionManager.startScheduledTask(task)
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "scheduled_task",
                event = "trigger_start_failed",
                throwable = throwable,
                details = mapOf("task_id" to task.id),
            )
            false
        }
    }

    fun rescheduleScheduledTasks(
        pendingResult: android.content.BroadcastReceiver.PendingResult,
    ) {
        appScope.launch {
            try {
                scheduledTaskManager.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class AppForegroundTracker : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(false)

    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}

val Context.aetherRuntime: AetherAppRuntime
    get() = (applicationContext as AetherApplication).runtime
