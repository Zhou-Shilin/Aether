package com.zhousl.aether.data

import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

private const val SharedDiagnosticMaximumCharacters = 768 * 1024
private const val SharedDiagnosticTrimmedCharacters = 512 * 1024
private const val SharedDiagnosticValueMaximumCharacters = 700

internal object SharedDiagnosticLogger {
    private val mutex = Mutex()
    private var events = ""
    private var persistenceInitialized = false

    suspend fun initializePersistence() {
        PlatformDiagnosticStorage.installCrashHandler()
        val persisted = PlatformDiagnosticStorage.readEventsText()
        val snapshot = mutex.withLock {
            if (persistenceInitialized) return
            events = trimSharedDiagnosticEvents(persisted + events)
            persistenceInitialized = true
            events
        }
        PlatformDiagnosticStorage.writeEventsText(snapshot)
    }

    suspend fun event(
        category: String,
        event: String,
        level: String = "info",
        sessionId: String? = null,
        turnId: String? = null,
        requestId: String? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val timestampMillis = platformCurrentTimeMillis()
        val line = buildJsonObject {
            put("timestamp", sharedIsoTimestamp(timestampMillis))
            put("timestampMillis", timestampMillis)
            put("level", level)
            put("category", category)
            put("event", event)
            sessionId?.takeIf(String::isNotBlank)?.let { put("sessionId", it) }
            turnId?.takeIf(String::isNotBlank)?.let { put("turnId", it) }
            requestId?.takeIf(String::isNotBlank)?.let { put("requestId", it) }
            put("details", SharedDiagnosticRedactor.sanitizeMap(details))
        }.toString()
        val snapshot = mutex.withLock {
            events = trimSharedDiagnosticEvents(events + "$line\n")
            events.takeIf { persistenceInitialized }
        }
        snapshot?.let { PlatformDiagnosticStorage.writeEventsText(it) }
    }

    suspend fun readEventsText(): String = mutex.withLock { events }

    suspend fun readLastCrashText(): String = PlatformDiagnosticStorage.readLastCrashText()
}

internal expect object PlatformDiagnosticStorage {
    suspend fun readEventsText(): String
    suspend fun writeEventsText(value: String)
    suspend fun readLastCrashText(): String
    fun installCrashHandler()
}

private fun trimSharedDiagnosticEvents(value: String): String {
    if (value.length <= SharedDiagnosticMaximumCharacters) return value
    val retained = value.takeLast(SharedDiagnosticTrimmedCharacters)
    return retained.substringAfter('\n', retained)
}

internal object SharedDiagnosticRedactor {
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

    fun sanitizeMap(values: Map<String, Any?>): JsonObject = buildJsonObject {
        values.forEach { (key, value) -> put(key, sanitizeValue(key, value)) }
    }

    fun sanitizeString(value: String): String {
        var sanitized = value
        inlineSecretPatterns.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { match ->
                match.groupValues.getOrNull(1).orEmpty() + "[REDACTED]"
            }
        }
        return sanitized.takeWithSharedDiagnosticSuffix(SharedDiagnosticValueMaximumCharacters)
    }

    fun sanitizedBaseUrl(baseUrl: String): String {
        val url = runCatching { Url(baseUrl.trim()) }.getOrNull()
            ?: return sanitizeString(baseUrl)
        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            if (url.port != url.protocol.defaultPort) append(':').append(url.port)
            append(url.encodedPath.ifBlank { "/" })
        }.trimEnd('/')
    }

    private fun sanitizeValue(key: String, value: Any?): JsonElement = when {
        value == null -> JsonNull
        isSensitiveKey(key) -> JsonPrimitive("[REDACTED]")
        shouldSummarizeLargeContent(key) -> JsonPrimitive(summarizeLargeContent(value))
        value is JsonObject -> buildJsonObject {
            value.forEach { (nestedKey, nestedValue) ->
                put(nestedKey, sanitizeValue(nestedKey, nestedValue))
            }
        }
        value is JsonArray -> buildJsonArray {
            value.forEachIndexed { index, item -> add(sanitizeValue("item_$index", item)) }
        }
        value is Map<*, *> -> sanitizeMap(
            value.entries.associate { it.key.toString() to it.value },
        )
        value is Iterable<*> -> buildJsonArray {
            value.forEachIndexed { index, item -> add(sanitizeValue("item_$index", item)) }
        }
        value is Array<*> -> buildJsonArray {
            value.forEachIndexed { index, item -> add(sanitizeValue("item_$index", item)) }
        }
        value is Boolean -> JsonPrimitive(value)
        value is Number -> JsonPrimitive(value)
        value is JsonPrimitive -> JsonPrimitive(sanitizeString(value.content))
        else -> JsonPrimitive(sanitizeString(value.toString()))
    }

    private fun summarizeLargeContent(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.isBlank()) "" else "[OMITTED content_chars=${text.length}]"
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.replace("-", "_").lowercase()
        return sensitiveKeyFragments.any { it in normalized }
    }

    private fun shouldSummarizeLargeContent(key: String): Boolean {
        val normalized = key.replace("-", "_").lowercase()
        return largeContentKeyFragments.any { it in normalized }
    }
}

internal fun sharedIsoTimestamp(timestampMillis: Long): String {
    val totalSeconds = timestampMillis / 1_000L
    val millis = (timestampMillis % 1_000L).toInt()
    val days = totalSeconds / 86_400L
    val secondsOfDay = (totalSeconds % 86_400L).toInt()
    val hour = secondsOfDay / 3_600
    val minute = secondsOfDay % 3_600 / 60
    val second = secondsOfDay % 60

    val shiftedDays = days + 719_468L
    val era = shiftedDays / 146_097L
    val dayOfEra = shiftedDays - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    var year = (yearOfEra + era * 400L).toInt()
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthPrime = (5L * dayOfYear + 2L) / 153L
    val day = (dayOfYear - (153L * monthPrime + 2L) / 5L + 1L).toInt()
    val month = (monthPrime + if (monthPrime < 10L) 3L else -9L).toInt()
    if (month <= 2) year += 1

    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-" +
        "${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:" +
        "${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}." +
        "${millis.toString().padStart(3, '0')}Z"
}

private fun String.takeWithSharedDiagnosticSuffix(maximumCharacters: Int): String =
    if (length <= maximumCharacters) {
        this
    } else {
        take(max(0, maximumCharacters - 18)) + "...[truncated]"
    }
