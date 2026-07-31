package com.zhousl.aether.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun formatSharedMessageTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            dateFormat = "MMMM d, h:mm a"
        }
        formatter.stringFromDate(
            NSDate(timeIntervalSinceReferenceDate = epochMillis / 1_000.0 - 978_307_200.0)
        )
    }.getOrDefault("")
}
