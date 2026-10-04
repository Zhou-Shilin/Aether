package com.zhousl.aether.agentmode

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import androidx.annotation.Keep
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

/** Wire protocol shared by [AgentModeServiceStarter] and [AgentModeServiceLauncher]. */
internal object AgentModeServiceProtocol {
    const val ExtraToken = "token"
    const val ExtraBinder = "binder"

    /**
     * Only shell and root hold this, so no third-party app can answer the launcher's broadcast
     * receiver with a binder of its own.
     */
    const val SenderPermission = "android.permission.INTERACT_ACROSS_USERS_FULL"

    /** Matches android.os.UserHandle.PER_USER_RANGE. */
    const val PerUserRange = 100_000

    fun readyAction(packageName: String): String = "$packageName.agentmode.SERVICE_READY"
}

/**
 * Entry point of the privileged Agent Mode process, run through `app_process` as shell (Shizuku)
 * or root with Aether's APK on the class path.
 *
 * This replaces Shizuku's UserService starter, which builds the service's Application through
 * LoadedApk.makeApplication(). That looks Aether up in the starter process's own Android user,
 * user 0, and aborts when Aether is installed only on a secondary user. Here the context is created
 * directly for the launching user, and the binder goes back to that user by broadcast.
 */
@Keep
object AgentModeServiceStarter {
    private const val Tag = "AetherAgentModeStarter"

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            start(args)
        } catch (throwable: Throwable) {
            val cause = (throwable as? InvocationTargetException)?.targetException ?: throwable
            Log.e(Tag, "Agent Mode service failed to start", cause)
            System.err.println("Agent Mode service failed to start: $cause")
            cause.printStackTrace()
            exitProcess(1)
        }
        exitProcess(0)
    }

    private fun start(args: Array<String>) {
        val options = args.mapNotNull { argument ->
            argument.removePrefix("--").split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val packageName = options["package"]?.takeIf { it.isNotBlank() } ?: error("Missing --package.")
        val userId = options["user"]?.toIntOrNull() ?: error("Missing --user.")
        val token = options["token"]?.takeIf { it.isNotBlank() } ?: error("Missing --token.")
        val user = UserHandle.getUserHandleForUid(userId * AgentModeServiceProtocol.PerUserRange)

        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }
        val systemContext = createSystemContext()
        val appContext = createPackageContextAsUser(systemContext, packageName, user)
        val service = AetherAgentModeShizukuService(appContext)

        val intent = Intent(AgentModeServiceProtocol.readyAction(packageName))
            .setPackage(packageName)
            .putExtras(
                Bundle().apply {
                    putString(AgentModeServiceProtocol.ExtraToken, token)
                    putBinder(AgentModeServiceProtocol.ExtraBinder, service)
                }
            )
        systemContext.sendBroadcastAsUser(intent, user)
        Log.i(Tag, "Agent Mode service started for $packageName in user $userId")
        Looper.loop()
    }

    @SuppressLint("PrivateApi")
    private fun createSystemContext(): Context {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val activityThread = activityThreadClass.getMethod("systemMain").invoke(null)
        return activityThreadClass.getMethod("getSystemContext").invoke(activityThread) as Context
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createPackageContextAsUser(base: Context, packageName: String, user: UserHandle): Context =
        Context::class.java
            .getMethod(
                "createPackageContextAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                UserHandle::class.java,
            )
            .invoke(base, packageName, Context.CONTEXT_IGNORE_SECURITY, user) as Context
}
