package com.zhousl.aether.platform

interface BackgroundExecutionLease {
    val isActive: Boolean
    fun update(detail: String) = Unit
    fun end()
}

interface BackgroundExecutionManager {
    fun begin(name: String, onExpired: () -> Unit): BackgroundExecutionLease
}

expect fun createBackgroundExecutionManager(platformServices: PlatformServices): BackgroundExecutionManager
