package com.zhousl.aether.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

private const val DiagnosticLogMaxBytes = 768 * 1024
private const val DiagnosticLogTrimBytes = 512 * 1024
private const val DiagnosticValueMaxChars = 700
private const val DiagnosticStackMaxChars = 5_000

class AetherDiagnosticLogger private constructor(
    private val diagnosticsDir: File?,
) {
    private val lock = Any()
    private val eventsFile: File? = diagnosticsDir?.resolve("events.jsonl")
    private val crashFile: File? = diagnosticsDir?.resolve("last-crash.json")

    constructor(context: Context) : this(
        File(context.filesDir, "diagnostics").apply { mkdirs() }
    )

    fun event(
        category: String,
        event: String,
        level: String = "info",
        sessionId: String? = null,
        turnId: String? = null,
        requestId: String? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val file = eventsFile ?: return
        val line = JSONObject().apply {
            put("timestamp", isoTimestamp(System.currentTimeMillis()))
            put("timestampMillis", System.currentTimeMillis())
            put("level", level)
            put("category", category)
            put("event", event)
            if (!sessionId.isNullOrBlank()) put("sessionId", sessionId)
            if (!turnId.isNullOrBlank()) put("turnId", turnId)
            if (!requestId.isNullOrBlank()) put("requestId", requestId)
            put("details", DiagnosticRedactor.sanitizeMap(details))
        }.toString()
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n", Charsets.UTF_8)
                trimEventsFileIfNeeded(file)
            }
        }
    }

    fun exception(
        category: String,
        event: String,
        throwable: Throwable,
        level: String = "error",
        sessionId: String? = null,
        turnId: String? = null,
        requestId: String? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        this.event(
            category = category,
            event = event,
            level = level,
            sessionId = sessionId,
            turnId = turnId,
            requestId = requestId,
            details = details + throwableDetails(throwable),
        )
    }

    fun recordCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        val crash = JSONObject().apply {
            put("timestamp", isoTimestamp(System.currentTimeMillis()))
            put("timestampMillis", System.currentTimeMillis())
            put("thread", thread.name)
            put("exceptionType", throwable.javaClass.name)
            put("message", DiagnosticRedactor.sanitizeString(throwable.message.orEmpty()))
            put("stackTrace", DiagnosticRedactor.sanitizeString(throwable.stackTraceToString(), DiagnosticStackMaxChars))
        }
        synchronized(lock) {
            runCatching {
                crashFile?.parentFile?.mkdirs()
                crashFile?.writeText(crash.toString(2), Charsets.UTF_8)
            }
        }
        event(
            category = "crash",
            event = "uncaught_exception",
            level = "error",
            details = mapOf(
                "thread" to thread.name,
                "exception_type" to throwable.javaClass.name,
                "message" to throwable.message.orEmpty(),
            ),
        )
    }

    fun readEventsText(): String =
        synchronized(lock) {
            eventsFile
                ?.takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                .orEmpty()
        }

    fun readLastCrashText(): String =
        synchronized(lock) {
            crashFile
                ?.takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                .orEmpty()
        }

    fun installUncaughtExceptionHandler() {
        if (eventsFile == null) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is AetherUncaughtExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            AetherUncaughtExceptionHandler(
                logger = this,
                previous = previous,
            )
        )
    }

    private fun trimEventsFileIfNeeded(file: File) {
        if (file.length() <= DiagnosticLogMaxBytes) return
        val bytes = file.readBytes()
        val start = (bytes.size - DiagnosticLogTrimBytes).coerceAtLeast(0)
        val keepBytes = bytes.copyOfRange(start, bytes.size)
        val firstNewline = keepBytes.indexOf('\n'.code.toByte())
        val trimmed = if (firstNewline >= 0 && firstNewline + 1 < keepBytes.size) {
            keepBytes.copyOfRange(firstNewline + 1, keepBytes.size)
        } else {
            keepBytes
        }
        file.writeBytes(trimmed)
    }

    companion object {
        val NoOp = AetherDiagnosticLogger(null)
    }
}

internal object DiagnosticRedactor {
    private val sensitiveKeyFragments = listOf(
        "apikey",
        "api_key",
        "authorization",
        "authheader",
        "bearer",
        "token",
        "secret",
        "password",
        "tavilykey",
    )
    private val largeContentKeyFragments = listOf(
        "base64",
        "screenshot",
        "attachment",
        "prompt",
        "content",
        "body",
        "markdown",
    )
    private val inlineSecretPatterns = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;}{]+"),
        Regex("(?i)((api[_-]?key|token|secret|password)\\s*[:=]\\s*)[^\\s,;}{]+"),
        Regex("(?i)((api[_-]?key|token|secret|password)=)[^&\\s]+"),
    )

    fun sanitizeMap(values: Map<String, Any?>): JSONObject =
        JSONObject().apply {
            values.forEach { (key, value) ->
                put(key, sanitizeValue(key, value))
            }
        }

    fun sanitizeValue(
        key: String,
        value: Any?,
    ): Any = when {
        value == null -> JSONObject.NULL
        isSensitiveKey(key) -> "[REDACTED]"
        shouldSummarizeLargeContent(key) -> summarizeLargeContent(value)
        value is Throwable -> sanitizeMap(throwableDetails(value))
        value is JSONObject -> sanitizeJsonObject(value)
        value is JSONArray -> sanitizeJsonArray(value)
        value is Map<*, *> -> sanitizeMap(
            value.entries.associate { it.key.toString() to it.value }
        )
        value is Iterable<*> -> JSONArray().apply {
            value.forEachIndexed { index, item -> put(sanitizeValue("item_$index", item)) }
        }
        value is Array<*> -> JSONArray().apply {
            value.forEachIndexed { index, item -> put(sanitizeValue("item_$index", item)) }
        }
        isStackTraceKey(key) -> sanitizeString(value.toString(), DiagnosticStackMaxChars)
        value is Number || value is Boolean -> value
        else -> sanitizeString(value.toString())
    }

    fun sanitizeString(value: String, maxChars: Int = DiagnosticValueMaxChars): String {
        var sanitized = value
        inlineSecretPatterns.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { match ->
                match.groupValues.getOrNull(1).orEmpty() + "[REDACTED]"
            }
        }
        return sanitized.takeWithSuffix(maxChars)
    }

    private fun isStackTraceKey(key: String): Boolean =
        key.endsWith("_stack", ignoreCase = true) ||
            key.equals("stackTrace", ignoreCase = true) ||
            key.equals("stack_trace", ignoreCase = true)

    fun sanitizedBaseUrl(baseUrl: String): String {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return sanitizeString(baseUrl)
        return buildString {
            append(uri.scheme.orEmpty())
            append("://")
            append(uri.host.orEmpty())
            if (uri.port >= 0) append(":").append(uri.port)
            append(uri.path.orEmpty().ifBlank { "/" })
        }.trimEnd('/')
    }

    private fun sanitizeJsonObject(value: JSONObject): JSONObject =
        JSONObject().apply {
            value.keys().forEach { key ->
                put(key, sanitizeValue(key, value.opt(key)))
            }
        }

    private fun sanitizeJsonArray(value: JSONArray): JSONArray =
        JSONArray().apply {
            for (index in 0 until value.length()) {
                put(sanitizeValue("item_$index", value.opt(index)))
            }
        }

    private fun summarizeLargeContent(value: Any?): String {
        val text = value?.toString().orEmpty()
        if (text.isBlank()) return ""
        return "[OMITTED content_chars=${text.length}]"
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.replace("-", "_").lowercase(Locale.US)
        return sensitiveKeyFragments.any { it in normalized }
    }

    private fun shouldSummarizeLargeContent(key: String): Boolean {
        val normalized = key.replace("-", "_").lowercase(Locale.US)
        return largeContentKeyFragments.any { it in normalized }
    }
}

private class AetherUncaughtExceptionHandler(
    private val logger: AetherDiagnosticLogger,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        logger.recordCrash(thread, throwable)
        previous?.uncaughtException(thread, throwable)
    }
}

internal fun throwableDetails(throwable: Throwable): Map<String, Any?> =
    mapOf(
        "exception_type" to throwable.javaClass.name,
        "exception_message" to throwable.message.orEmpty(),
        "exception_stack" to throwable.stackTraceToString().take(DiagnosticStackMaxChars),
    )

internal fun isoTimestamp(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestampMillis))

private fun String.takeWithSuffix(maxChars: Int): String =
    if (length <= maxChars) {
        this
    } else {
        take(max(0, maxChars - 18)) + "...[truncated]"
    }
