package com.zhousl.aether.data

internal actual object PlatformDiagnosticStorage {
    actual suspend fun readEventsText(): String = ""

    actual suspend fun writeEventsText(value: String) = Unit

    actual suspend fun readLastCrashText(): String = ""

    actual fun installCrashHandler() = Unit
}
