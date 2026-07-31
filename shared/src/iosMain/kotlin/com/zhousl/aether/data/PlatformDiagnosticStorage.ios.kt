package com.zhousl.aether.data

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSThread

private const val SharedDiagnosticStackMaximumCharacters = 5_000
private val SharedDiagnosticPrettyJson = Json { prettyPrint = true }

internal actual object PlatformDiagnosticStorage {
    private val diagnosticsDirectory: Path by lazy {
        (NSHomeDirectory() + "/Library/Application Support/diagnostics").toPath()
    }
    private val eventsPath: Path by lazy { diagnosticsDirectory / "events.jsonl" }
    private val crashPath: Path by lazy { diagnosticsDirectory / "last-crash.json" }
    private var crashHandlerInstalled = false

    actual suspend fun readEventsText(): String = readText(eventsPath)

    actual suspend fun writeEventsText(value: String) {
        writeText(eventsPath, value)
    }

    actual suspend fun readLastCrashText(): String = readText(crashPath)

    @OptIn(ExperimentalNativeApi::class)
    actual fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = getUnhandledExceptionHook()
        setUnhandledExceptionHook { throwable ->
            runCatching {
                val timestampMillis = platformCurrentTimeMillis()
                val crash = buildJsonObject {
                    put("timestamp", sharedIsoTimestamp(timestampMillis))
                    put("timestampMillis", timestampMillis)
                    put("thread", NSThread.currentThread.name.orEmpty().ifBlank { "unknown" })
                    put("exceptionType", throwable::class.qualifiedName.orEmpty())
                    put("message", SharedDiagnosticRedactor.sanitizeString(throwable.message.orEmpty()))
                    put(
                        "stackTrace",
                        SharedDiagnosticRedactor.sanitizeString(throwable.stackTraceToString())
                            .take(SharedDiagnosticStackMaximumCharacters),
                    )
                }
                writeText(crashPath, SharedDiagnosticPrettyJson.encodeToString(crash))
            }
            if (previous != null) previous(throwable) else terminateWithUnhandledException(throwable)
        }
    }

    private fun readText(path: Path): String = runCatching {
        if (!FileSystem.SYSTEM.exists(path)) "" else FileSystem.SYSTEM.read(path) { readUtf8() }
    }.getOrDefault("")

    private fun writeText(path: Path, value: String) {
        runCatching {
            FileSystem.SYSTEM.createDirectories(diagnosticsDirectory)
            FileSystem.SYSTEM.write(path) { writeUtf8(value) }
        }
    }
}
