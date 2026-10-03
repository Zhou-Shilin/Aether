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

/** Reads the Android device language tag used to select the initial interface language. */
actual fun platformLanguageTag(): String = Locale.getDefault().toLanguageTag()

/**
 * Chooses the initial Android agent prompt from the device locale.
 * Settings persist this value independently of UI language; changing the language
 * must not overwrite a prompt that the user may have edited.
 */
actual fun platformDefaultSystemPrompt(): String =
    if (platformLanguageTag().startsWith("ru", ignoreCase = true)) {
        "Вы — Aether, локальный Android-агент, который умеет вызывать инструменты и выполнять задачи на устройстве. Используйте доступные инструменты вместо предположений о локальном состоянии."
    } else {
        "You are Aether, a local-first Android agent that can call tools and complete tasks on-device. Use available tools instead of guessing local state."
    }

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

/** Maps Android device locales to supported interface languages, falling back to English. */
fun defaultAppLanguage(locale: Locale): AppLanguage = when {
    locale.language.equals("zh", ignoreCase = true) -> AppLanguage.SimplifiedChinese
    locale.language.equals("fa", ignoreCase = true) -> AppLanguage.Persian
    locale.language.equals("ru", ignoreCase = true) -> AppLanguage.Russian
    else -> AppLanguage.English
}
