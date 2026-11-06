package com.zhousl.aether

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import com.zhousl.aether.data.HostedApp
import com.zhousl.aether.data.HostedApps
import java.io.File

/** One generic activity; its data URI identifies the app's reusable document task. */
class HostedAppActivity : MainActivity() {
    override val isHostedWindow = true
    override val hostedAppId: String?
        get() = appId(this, intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        hostedAppId?.let { (application as AetherApplication).runtime.hostedApps.resumeWindow(it) }
        super.onCreate(savedInstanceState)
        val app = hostedAppId?.let { (application as AetherApplication).runtime.hostedApps.windowApp(it) }
        if (app != null) {
            title = app.label
            val file = File(app.assets, "icon-192.png")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file.isFile) BitmapFactory.decodeFile(file.path, bounds)
            val icon = if (file.length() <= 1048576 && bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
                BitmapFactory.decodeFile(file.path) else null
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(app.label, icon))
        }
    }

    companion object {
        // Public shortcut entry point: only an installed ID, never an asset path,
        // credential, grant, capability request or arbitrary URL is accepted.
        fun appId(context: Context, intent: Intent?): String? = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }?.data
            ?.takeIf { it.scheme == "aether-app" && it.authority == context.packageName && it.query == null && it.fragment == null }
            ?.pathSegments?.singleOrNull()?.takeIf { runCatching { HostedApps.validId(it) }.isSuccess }

        fun intent(context: Context, app: HostedApp): Intent = Intent(context, HostedAppActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.Builder().scheme("aether-app").authority(context.packageName).appendPath(app.id).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
    }
}
