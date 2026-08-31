package com.zhousl.aether

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
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
            ),
        )
        serviceScope.launch {
            combine(
                runtime.chatStateStore.state,
                runtime.sessionExecutionManager.executionStates,
                runtime.settingsRepository.settings,
            ) { chatState, executionStates, settings ->
                Triple(chatState.sessions, executionStates, settings)
            }.collectLatest { (sessions, executionStates, settings) ->
                val activeCount = executionStates.values.count { it.isRunning }
                if (activeCount == 0 || !settings.keepTasksRunningInBackground) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateForeground(
                        runtime.notificationController.buildForegroundNotification(
                            sessions = sessions,
                            executionStates = executionStates,
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

    /**
     * Updates the Live Update foreground notification in place (same ID), so the
     * system keeps the promoted-ongoing behavior and just refreshes the content
     * — this is what makes the task progress "live".
     */
    private fun updateForeground(notification: android.app.Notification) {
        val notificationManager = NotificationManagerCompat.from(this)
        if (hasEnteredForeground) {
            notificationManager.notify(ForegroundNotificationId, notification)
        } else {
            enterForeground(notification)
        }
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
