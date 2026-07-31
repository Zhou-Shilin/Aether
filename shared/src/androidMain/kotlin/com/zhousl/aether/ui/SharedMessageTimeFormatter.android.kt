package com.zhousl.aether.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SharedMessageTimestampFormatter =
    DateTimeFormatter.ofPattern("MMMM d, h:mm a", Locale.US)

internal actual fun formatSharedMessageTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(SharedMessageTimestampFormatter)
    }.getOrDefault("")
}
