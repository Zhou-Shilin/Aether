package com.zhousl.aether.data

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSLocale

private const val SharedStatisticsReferenceDateOffsetSeconds = 978_307_200.0

internal actual fun platformStatisticsDate(epochMillis: Long): SharedStatisticsDate {
    val date = NSDate(
        timeIntervalSinceReferenceDate = epochMillis / 1_000.0 - SharedStatisticsReferenceDateOffsetSeconds,
    )
    return SharedStatisticsDate(
        key = sharedStatisticsFormat(date, "yyyy-MM-dd"),
        label = sharedStatisticsFormat(date, "M/d"),
        shortLabel = sharedStatisticsFormat(date, "d"),
    )
}

internal actual fun platformRecentStatisticsDates(
    count: Int,
    nowMillis: Long,
): List<SharedStatisticsDate> {
    val now = NSDate(
        timeIntervalSinceReferenceDate = nowMillis / 1_000.0 - SharedStatisticsReferenceDateOffsetSeconds,
    )
    val calendar = NSCalendar.currentCalendar
    return ((count - 1) downTo 0).map { daysAgo ->
        val date = calendar.dateByAddingUnit(
            unit = NSCalendarUnitDay,
            value = -daysAgo.toLong(),
            toDate = now,
            options = 0u,
        ) ?: now
        SharedStatisticsDate(
            key = sharedStatisticsFormat(date, "yyyy-MM-dd"),
            label = sharedStatisticsFormat(date, "M/d"),
            shortLabel = sharedStatisticsFormat(date, "d"),
        )
    }
}

private fun sharedStatisticsFormat(date: NSDate, pattern: String): String =
    NSDateFormatter().run {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = pattern
        stringFromDate(date)
    }
