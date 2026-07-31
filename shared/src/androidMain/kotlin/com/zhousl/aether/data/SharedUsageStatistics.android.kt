package com.zhousl.aether.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SharedStatisticsKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val SharedStatisticsLabelFormatter = DateTimeFormatter.ofPattern("M/d")

internal actual fun platformStatisticsDate(epochMillis: Long): SharedStatisticsDate {
    val date = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return SharedStatisticsDate(
        key = date.format(SharedStatisticsKeyFormatter),
        label = date.format(SharedStatisticsLabelFormatter),
        shortLabel = date.dayOfMonth.toString(),
    )
}

internal actual fun platformRecentStatisticsDates(
    count: Int,
    nowMillis: Long,
): List<SharedStatisticsDate> {
    val today = Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return ((count - 1) downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        SharedStatisticsDate(
            key = date.format(SharedStatisticsKeyFormatter),
            label = date.format(SharedStatisticsLabelFormatter),
            shortLabel = date.dayOfMonth.toString(),
        )
    }
}
