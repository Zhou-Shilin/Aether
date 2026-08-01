package com.zhousl.aether

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class ForegroundServiceSnapshot(
    val sessions: List<com.zhousl.aether.ui.ChatSession>,
    val executionStates: Map<String, com.zhousl.aether.data.SessionExecutionState>,
    val keepTasksRunningInBackground: Boolean,
    val enabledChannelCount: Int,
    val channelStatuses: Map<com.zhousl.aether.channel.ChannelKind, com.zhousl.aether.channel.ChannelStatus>,
)

class AetherForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasEnteredForeground = false

    override fun onCreate() {
        super.onCreate()
        startRequested.set(true)
        val runtime = aetherRuntime
        enterForeground(
            runtime.notificationController.buildForegroundNotification(
                sessions = emptyList(),
                executionStates = emptyMap(),
                channelStatuses = emptyMap(),
            ),
        )
        serviceScope.launch {
            combine(
                runtime.chatStateStore.state,
                runtime.sessionExecutionManager.executionStates,
                runtime.settingsRepository.settings,
                runtime.channelConfigRepository.configs,
                runtime.channelManager.statuses,
            ) { chatState, executionStates, settings, channelConfigs, channelStatuses ->
                ForegroundServiceSnapshot(
                    sessions = chatState.sessions,
                    executionStates = executionStates,
                    keepTasksRunningInBackground = settings.keepTasksRunningInBackground,
                    enabledChannelCount = channelConfigs.count { it.enabled && it.isConfigured },
                    channelStatuses = channelStatuses,
                )
            }.collectLatest { snapshot ->
                val sessions = snapshot.sessions
                val executionStates = snapshot.executionStates
                val activeCount = executionStates.values.count { it.isRunning }
                val hasBackgroundTasks = activeCount > 0 && snapshot.keepTasksRunningInBackground
                if (!hasBackgroundTasks && snapshot.enabledChannelCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    enterForeground(
                        runtime.notificationController.buildForegroundNotification(
                            sessions = sessions,
                            executionStates = executionStates,
                            channelStatuses = snapshot.channelStatuses,
                        ),
                    )
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startRequested.set(true)
        if (!hasEnteredForeground) {
            val runtime = aetherRuntime
            enterForeground(
                runtime.notificationController.buildForegroundNotification(
                    sessions = emptyList(),
                    executionStates = emptyMap(),
                    channelStatuses = emptyMap(),
                ),
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        startRequested.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enterForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            ForegroundNotificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        hasEnteredForeground = true
    }

    companion object {
        private val startRequested = AtomicBoolean(false)

        fun ensureRunning(context: Context) {
            if (!startRequested.compareAndSet(false, true)) return
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, AetherForegroundService::class.java),
                )
            } catch (t: Throwable) {
                startRequested.set(false)
                throw t
            }
        }
    }
}
