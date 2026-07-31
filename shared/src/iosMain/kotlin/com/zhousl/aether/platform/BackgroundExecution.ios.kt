package com.zhousl.aether.platform

import com.zhousl.aether.runtime.NativeBackgroundExecutionListener
import com.zhousl.aether.runtime.NativeRuntimeHost

actual fun createBackgroundExecutionManager(platformServices: PlatformServices): BackgroundExecutionManager {
    val host = (platformServices as? IosPlatformServices)?.host
    return if (host == null) NoOpIosBackgroundExecutionManager else IosBackgroundExecutionManager(host)
}

private class IosBackgroundExecutionManager(
    private val host: NativeRuntimeHost,
) : BackgroundExecutionManager {
    override fun begin(name: String, onExpired: () -> Unit): BackgroundExecutionLease =
        IosBackgroundExecutionLease(host, name, onExpired)
}

private class IosBackgroundExecutionLease(
    private val host: NativeRuntimeHost,
    name: String,
    onExpired: () -> Unit,
) : BackgroundExecutionLease {
    private var identifier = host.beginBackgroundExecution(
        name = name,
        listener = object : NativeBackgroundExecutionListener {
            override fun onExpired() = onExpired()
        },
    )

    override val isActive: Boolean
        get() = identifier.isNotBlank()

    override fun update(detail: String) {
        identifier.takeIf(String::isNotBlank)?.let { host.updateBackgroundExecution(it, detail) }
    }

    override fun end() {
        val activeIdentifier = identifier.takeIf(String::isNotBlank) ?: return
        identifier = ""
        host.endBackgroundExecution(activeIdentifier, true)
    }
}

private object NoOpIosBackgroundExecutionManager : BackgroundExecutionManager {
    override fun begin(name: String, onExpired: () -> Unit): BackgroundExecutionLease =
        object : BackgroundExecutionLease {
            override val isActive: Boolean = false
            override fun end() = Unit
        }
}
