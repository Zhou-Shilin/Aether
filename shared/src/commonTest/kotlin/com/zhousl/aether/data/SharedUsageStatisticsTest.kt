package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.PersistedChatUsage
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedUsageStatisticsTest {
    @Test
    fun aggregatesEveryPersistedSessionMessageTurnAndPerformanceSample() {
        val sessions = listOf(
            session(
                id = "one",
                messages = listOf(
                    message("u1", fromUser = true, timestamp = 100),
                    message(
                        "a1",
                        fromUser = false,
                        timestamp = 1_000,
                        usage = PersistedChatUsage(10, 20, 32, 2, 3),
                        duration = 2_000,
                        latency = 400,
                        completedAt = 3_400,
                    ),
                ),
            ),
            session(
                id = "two",
                messages = listOf(
                    message("u2", fromUser = true, timestamp = 4_000),
                    message(
                        "a2",
                        fromUser = false,
                        timestamp = 5_000,
                        usage = PersistedChatUsage(30, 40, 75, 5, 7),
                        duration = 4_000,
                        latency = 600,
                        completedAt = 9_600,
                    ),
                    message("note", fromUser = false, timestamp = 6_000),
                ),
            ),
            session(
                id = "without-usage",
                messages = listOf(message("u3", fromUser = true, timestamp = 7_000)),
            ),
        )
        val dayOne = SharedStatisticsDate("2026-01-01", "1/1", "1")
        val dayTwo = SharedStatisticsDate("2026-01-02", "1/2", "2")
        val report = buildSharedUsageStatisticsReport(
            sessions = sessions,
            nowMillis = 10_000,
            recentDates = listOf(dayOne, dayTwo),
            resolveDate = { millis -> if (millis < 8_000) dayOne else dayTwo },
        )

        assertEquals(2, report.sessionCount)
        assertEquals(6, report.messageCount)
        assertEquals(2, report.turnCount)
        assertEquals(107, report.totalTokens)
        assertEquals(40, report.inputTokens)
        assertEquals(60, report.outputTokens)
        assertEquals(7, report.reasoningTokens)
        assertEquals(10, report.cachedInputTokens)
        assertEquals(75, report.largestTurnTokens)
        assertEquals(53, report.averageTurnTokens)
        assertEquals(10.0, report.averageOutputTokensPerSecond!!, 0.0000001)
        assertEquals(500, report.averageFirstTokenLatencyMillis)
        assertEquals(listOf(32L, 75L), report.recentDailyTokenUsage.map { it.tokens })
        assertEquals(dayTwo.key, report.peakDay?.key)
        assertEquals(dayTwo.label, report.recentSpeedSamples.last().shortLabel)
    }

    @Test
    fun peakDayUsesTheSameRecentFourteenDayWindowAsAndroid() {
        val oldDay = SharedStatisticsDate("2025-12-01", "12/1", "1")
        val recentDay = SharedStatisticsDate("2026-01-02", "1/2", "2")
        val report = buildSharedUsageStatisticsReport(
            sessions = listOf(
                session(
                    id = "history",
                    messages = listOf(
                        message(
                            id = "old",
                            fromUser = false,
                            timestamp = 100,
                            usage = PersistedChatUsage(900, 100, 1_000, 0, 0),
                            duration = 1_000,
                        ),
                        message(
                            id = "recent",
                            fromUser = false,
                            timestamp = 2_000,
                            usage = PersistedChatUsage(10, 10, 20, 0, 0),
                            duration = 1_000,
                        ),
                    ),
                ),
            ),
            nowMillis = 10_000,
            recentDates = listOf(recentDay),
            resolveDate = { millis -> if (millis < 2_000) oldDay else recentDay },
        )

        assertEquals(recentDay.key, report.peakDay?.key)
        assertEquals(20L, report.peakDay?.tokens)
    }

    @Test
    fun doesNotDeriveOutputSpeedFromReasoningDuration() {
        val day = SharedStatisticsDate("2026-01-02", "1/2", "2")
        val report = buildSharedUsageStatisticsReport(
            sessions = listOf(
                session(
                    id = "reasoning-only",
                    messages = listOf(
                        message(
                            id = "assistant",
                            fromUser = false,
                            timestamp = 1_000,
                            usage = PersistedChatUsage(10, 20, 30, 0, 0),
                            duration = 0,
                            latency = 100,
                            thoughtDuration = 4_000,
                        ),
                    ),
                ),
            ),
            nowMillis = 10_000,
            recentDates = listOf(day),
            resolveDate = { day },
        )

        assertEquals(null, report.averageOutputTokensPerSecond)
        assertEquals(emptyList(), report.recentSpeedSamples)
    }

    @Test
    fun usesRecordedCompletionTimeForDailyUsageAndSpeedSamples() {
        val startedDay = SharedStatisticsDate("2026-01-01", "1/1", "1")
        val completedDay = SharedStatisticsDate("2026-01-02", "1/2", "2")
        val report = buildSharedUsageStatisticsReport(
            sessions = listOf(
                session(
                    id = "cross-midnight",
                    messages = listOf(
                        message(
                            id = "assistant",
                            fromUser = false,
                            timestamp = 1_000,
                            completedAt = 9_000,
                            usage = PersistedChatUsage(10, 20, 30, 0, 0),
                            duration = 1_000,
                            latency = 200,
                        ),
                    ),
                ),
            ),
            nowMillis = 10_000,
            recentDates = listOf(startedDay, completedDay),
            resolveDate = { millis -> if (millis < 8_000) startedDay else completedDay },
        )

        assertEquals(listOf(0L, 30L), report.recentDailyTokenUsage.map { it.tokens })
        assertEquals(9_000L, report.recentSpeedSamples.single().timestampMillis)
    }

    @Test
    fun countsAnyUsageSnapshotAndFallsBackToStartTimeLikeAndroid() {
        val startedDay = SharedStatisticsDate("2026-01-01", "1/1", "1")
        val derivedDay = SharedStatisticsDate("2026-01-02", "1/2", "2")
        val report = buildSharedUsageStatisticsReport(
            sessions = listOf(
                session(
                    id = "imported",
                    messages = listOf(
                        message(
                            id = "user-with-usage",
                            fromUser = true,
                            timestamp = 1_000,
                            usage = PersistedChatUsage(10, 20, 30, 0, 0),
                            duration = 9_000,
                        ),
                    ),
                ),
            ),
            nowMillis = 10_000,
            recentDates = listOf(startedDay, derivedDay),
            resolveDate = { millis -> if (millis < 5_000) startedDay else derivedDay },
        )

        assertEquals(1, report.sessionCount)
        assertEquals(1, report.turnCount)
        assertEquals(listOf(30L, 0L), report.recentDailyTokenUsage.map { it.tokens })
        assertEquals(emptyList(), report.recentSpeedSamples)
    }

    private fun session(id: String, messages: List<PersistedChatMessage>) = PersistedChatSession(
        id = id,
        title = id,
        preview = "",
        messages = messages,
    )

    private fun message(
        id: String,
        fromUser: Boolean,
        timestamp: Long,
        completedAt: Long? = null,
        usage: PersistedChatUsage? = null,
        duration: Long = 0,
        latency: Long = 0,
        thoughtDuration: Long = duration,
    ) = PersistedChatMessage(
        id = id,
        text = id,
        fromUser = fromUser,
        usage = usage,
        createdAtMillis = timestamp,
        completedAtMillis = completedAt,
        thoughtDurationMillis = thoughtDuration,
        responseDurationMillis = duration,
        firstTokenLatencyMillis = latency,
    )
}
