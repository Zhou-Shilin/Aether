package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.PersistedChatUsage
import com.zhousl.aether.data.chatdb.SharedChatHistoryStore
import kotlin.math.roundToLong

data class SharedStatisticsDate(
    val key: String,
    val label: String,
    val shortLabel: String,
)

data class SharedDailyTokenUsage(
    val key: String,
    val label: String,
    val shortLabel: String,
    val tokens: Long,
)

data class SharedSpeedSample(
    val label: String,
    val shortLabel: String,
    val tokensPerSecond: Double,
    val timestampMillis: Long,
)

data class SharedUsageStatisticsReport(
    val totalTokens: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val sessionCount: Int = 0,
    val messageCount: Int = 0,
    val turnCount: Int = 0,
    val recentDailyTokenUsage: List<SharedDailyTokenUsage> = emptyList(),
    val allDailyTokenUsage: List<SharedDailyTokenUsage> = emptyList(),
    val peakDay: SharedDailyTokenUsage? = null,
    val largestTurnTokens: Long? = null,
    val averageTurnTokens: Long? = null,
    val averageOutputTokensPerSecond: Double? = null,
    val averageFirstTokenLatencyMillis: Long? = null,
    val recentSpeedSamples: List<SharedSpeedSample> = emptyList(),
)

suspend fun SharedChatHistoryStore.loadUsageStatistics(
    nowMillis: Long = platformCurrentTimeMillis(),
): SharedUsageStatisticsReport = buildSharedUsageStatisticsReport(loadAll(), nowMillis)

fun buildSharedUsageStatisticsReport(
    sessions: List<PersistedChatSession>,
    nowMillis: Long = platformCurrentTimeMillis(),
    recentDates: List<SharedStatisticsDate> = platformRecentStatisticsDates(14, nowMillis),
    resolveDate: (Long) -> SharedStatisticsDate = ::platformStatisticsDate,
): SharedUsageStatisticsReport {
    data class Turn(val message: PersistedChatMessage, val timestampMillis: Long)

    val turns = sessions.flatMap { session ->
        session.messages.mapNotNull { message ->
            if (message.usage == null) return@mapNotNull null
            val timestamp = message.completedAtMillis?.takeIf { it > 0L }
                ?: message.createdAtMillis
            Turn(message, timestamp)
        }
    }
    val usages = turns.map { it.message.usage!! }
    val dailyTotals = turns.filter { it.timestampMillis > 0L }
        .groupBy { resolveDate(it.timestampMillis).key }
        .mapValues { (_, dailyTurns) -> dailyTurns.sumOf { it.message.usage?.totalTokens ?: 0L } }
    val allDates = turns.filter { it.timestampMillis > 0L }
        .map { resolveDate(it.timestampMillis) }
        .distinctBy(SharedStatisticsDate::key)
        .sortedBy(SharedStatisticsDate::key)
    val allDaily = allDates.map { date ->
        SharedDailyTokenUsage(
            key = date.key,
            label = date.label,
            shortLabel = date.shortLabel,
            tokens = dailyTotals[date.key] ?: 0L,
        )
    }
    val recentDaily = recentDates.map { date ->
        SharedDailyTokenUsage(
            key = date.key,
            label = date.label,
            shortLabel = date.shortLabel,
            tokens = dailyTotals[date.key] ?: 0L,
        )
    }
    val speedSamples = turns.mapNotNull { turn ->
        if (turn.timestampMillis <= 0L) return@mapNotNull null
        val usage = turn.message.usage ?: return@mapNotNull null
        if (!usage.outputTokensAvailable) return@mapNotNull null
        val outputTokens = usage.outputTokens
        val completedAt = turn.message.completedAtMillis?.takeIf { it > 0L }
            ?: return@mapNotNull null
        val outputStartedAt = turn.message.firstTokenLatencyMillis?.let { latency ->
            turn.message.createdAtMillis + latency.coerceAtLeast(0L)
        } ?: turn.message.createdAtMillis.takeIf { it > 0L }
            ?: return@mapNotNull null
        val outputDuration = completedAt - outputStartedAt
        if (outputTokens <= 0L || outputDuration <= 0L) return@mapNotNull null
        val date = resolveDate(turn.timestampMillis)
        SharedSpeedSample(
            label = date.label,
            shortLabel = date.label,
            tokensPerSecond = outputTokens * 1_000.0 / outputDuration,
            timestampMillis = turn.timestampMillis,
        )
    }.sortedBy(SharedSpeedSample::timestampMillis)
    val latencies = turns.mapNotNull { it.message.firstTokenLatencyMillis }
    val totalTokens = usages.sumOf { it.totalTokens }

    return SharedUsageStatisticsReport(
        totalTokens = totalTokens,
        inputTokens = usages.sumOf { it.inputTokens },
        outputTokens = usages.sumOf { it.outputTokens },
        reasoningTokens = usages.sumOf { it.reasoningTokens },
        cachedInputTokens = usages.sumOf { it.cachedInputTokens },
        sessionCount = sessions.count { session ->
            session.messages.any { message -> message.usage != null }
        },
        messageCount = sessions.sumOf { it.messages.size },
        turnCount = turns.size,
        recentDailyTokenUsage = recentDaily,
        allDailyTokenUsage = allDaily,
        peakDay = recentDaily.maxByOrNull { it.tokens }?.takeIf { it.tokens > 0L },
        largestTurnTokens = usages.filter(PersistedChatUsage::totalTokensAvailable)
            .maxOfOrNull(PersistedChatUsage::totalTokens),
        averageTurnTokens = usages.takeIf { it.isNotEmpty() }?.let { totalTokens / it.size },
        averageOutputTokensPerSecond = speedSamples
            .takeIf { it.isNotEmpty() }
            ?.map(SharedSpeedSample::tokensPerSecond)
            ?.average(),
        averageFirstTokenLatencyMillis = latencies
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToLong(),
        recentSpeedSamples = speedSamples,
    )
}

internal expect fun platformStatisticsDate(epochMillis: Long): SharedStatisticsDate

internal expect fun platformRecentStatisticsDates(
    count: Int,
    nowMillis: Long,
): List<SharedStatisticsDate>
