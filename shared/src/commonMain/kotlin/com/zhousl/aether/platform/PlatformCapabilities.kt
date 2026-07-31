package com.zhousl.aether.platform

/** Platform feature switches are resolved once at composition/runtime creation. */
data class PlatformCapabilities(
    val termux: Boolean,
    val runtimeSelection: Boolean,
    val agentMode: Boolean,
    val scheduledTasks: Boolean,
    val persistentBackground: Boolean,
    val nativeMods: Boolean,
    val alpine: Boolean = true,
    val alpineChrome: Boolean = true,
    val stdioMcp: Boolean = true,
    val scriptExtensions: Boolean = true,
    val layeredScreenTransitions: Boolean = true,
    val supportsTabletLayout: Boolean = false,
) {
    companion object {
        val Android = PlatformCapabilities(
            termux = true,
            runtimeSelection = true,
            agentMode = true,
            scheduledTasks = true,
            persistentBackground = true,
            nativeMods = true,
        )

        val Ios = PlatformCapabilities(
            termux = false,
            runtimeSelection = false,
            agentMode = false,
            scheduledTasks = false,
            persistentBackground = false,
            nativeMods = false,
            alpineChrome = false,
            layeredScreenTransitions = true,
            supportsTabletLayout = true,
        )
    }
}

expect val currentPlatformCapabilities: PlatformCapabilities
