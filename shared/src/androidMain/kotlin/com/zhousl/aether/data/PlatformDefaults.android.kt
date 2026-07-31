package com.zhousl.aether.data

import android.os.SystemClock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

actual fun platformCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformUptimeMillis(): Long = try {
    SystemClock.uptimeMillis()
} catch (_: RuntimeException) {
    // Android's local unit-test stubs throw here; nanoTime remains monotonic on the host JVM.
    System.nanoTime() / 1_000_000L
}

actual fun platformRandomUuid(): String = UUID.randomUUID().toString()

actual fun platformLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun platformDefaultSystemPrompt(): String =
    "You are Aether, a local-first Android agent that can call tools and complete tasks on-device. Use available tools instead of guessing local state."

actual fun platformDefaultLlmUserAgent(): String = "Aether/1.0 (Android)"

actual fun platformDynamicPromptValues(): Map<String, String> {
    val now = ZonedDateTime.now()
    return mapOf(
        "current_datetime" to now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "current_date" to now.toLocalDate().toString(),
        "current_time" to now.toLocalTime().withNano(0).toString(),
        "timezone" to now.zone.id,
        "unix_timestamp" to now.toEpochSecond().toString(),
    )
}

fun defaultAppLanguage(locale: Locale): AppLanguage = if (
    locale.language.equals("zh", ignoreCase = true)
) {
    AppLanguage.SimplifiedChinese
} else {
    AppLanguage.English
}
