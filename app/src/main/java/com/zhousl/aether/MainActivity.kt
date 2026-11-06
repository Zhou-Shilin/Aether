package com.zhousl.aether

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zhousl.aether.ui.AetherApp

open class MainActivity : AppCompatActivity() {
    protected open val hostedAppId: String? get() = null
    protected open val isHostedWindow: Boolean get() = false
    private var folderReply: CancellableContinuation<Uri?>? = null
    private var fileReply: CancellableContinuation<Uri?>? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> folderReply?.let { if (it.isActive) it.resume(uri) }; folderReply = null }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> fileReply?.let { if (it.isActive) it.resume(uri) }; fileReply = null }
    private val documentChooser: suspend (Boolean) -> Uri? = { tree ->
        suspendCancellableCoroutine { continuation ->
            check(folderReply == null && fileReply == null) { "A document picker is already open." }
            if (tree) { folderReply = continuation; folderPicker.launch(null) }
            else { fileReply = continuation; filePicker.launch(arrayOf("application/zip", "application/octet-stream")) }
            continuation.invokeOnCancellation { if (tree) folderReply = null else fileReply = null }
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.lifecycle.ViewModelProvider(this).get(AetherWindowLease::class.java)
        (application as AetherApplication).runtime.sharedDocuments.choose = documentChooser
        (application as AetherApplication).runtime.hostedApps.let { host ->
            hostedAppId?.let { host.resumeWindow(it); host.prepareWebView() }
        }
        setContent {
            val app = application as AetherApplication
            val controller = androidx.lifecycle.ViewModelProvider(app.uiSession,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))
                .get(com.zhousl.aether.ui.AetherViewModel::class.java)
            AetherApp(viewModel = controller, hostedAppId = hostedAppId, isHostedWindow = isHostedWindow,
                onHostedClose = { finishAndRemoveTask() },
                onNotificationPermissionRequested = ::maybeRequestNotificationPermission)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as AetherApplication).runtime.hostedApps.let { host ->
            hostedAppId?.let { host.resumeWindow(it); host.prepareWebView() }
        }
    }

    override fun onDestroy() {
        (application as AetherApplication).runtime.sharedDocuments.let { if (it.choose === documentChooser) it.choose = null }
        folderReply?.cancel(); fileReply?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        (application as AetherApplication).runtime.sharedDocuments.choose = documentChooser
        (application as AetherApplication).runtime.nativeModManager.notifyUiStable()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
