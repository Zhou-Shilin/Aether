package com.zhousl.aether

import androidx.compose.ui.window.ComposeUIViewController
import com.zhousl.aether.platform.currentPlatformCapabilities
import com.zhousl.aether.runtime.IosAlpineRuntime
import com.zhousl.aether.runtime.NativeRuntimeHost
import com.zhousl.aether.ui.AetherSharedApp
import com.zhousl.aether.data.createIosAetherSettingsStore
import com.zhousl.aether.data.createIosAetherChatHistoryDatabase
import com.zhousl.aether.platform.IosPlatformServices

fun MainViewController(runtimeHost: NativeRuntimeHost): platform.UIKit.UIViewController {
    val runtime = IosAlpineRuntime(runtimeHost)
    val settingsStore = createIosAetherSettingsStore()
    val chatHistoryDatabase = createIosAetherChatHistoryDatabase()
    val platformServices = IosPlatformServices(runtimeHost)
    return ComposeUIViewController {
        AetherSharedApp(
        runtime = runtime,
        capabilities = currentPlatformCapabilities,
        settingsStore = settingsStore,
        chatHistoryDatabase = chatHistoryDatabase,
        platformServices = platformServices,
        )
    }
}
