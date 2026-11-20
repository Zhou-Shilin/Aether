package com.zhousl.aether.data

import com.zhousl.aether.ui.ChatUsageStatistics
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HostedAppsSupportTest {
    @Test
    fun extensionUsageLedgerRecordsAndLoadsSnapshots() {
        val dir = createTempDirectory("usage-ledger-test").toFile()
        try {
            val file = File(dir, "usage-ledger.jsonl")
            val ledger = ExtensionUsageLedger.forFile(file)
            ledger.record(
                caller = "storylab",
                statistics = ChatUsageStatistics(
                    inputTokens = 1200,
                    outputTokens = 350,
                    totalTokens = 1550,
                    reasoningTokens = 80,
                    cachedInputTokens = 400,
                    tokenUsageSource = "provider",
                    startedAtMillis = 1000L,
                    firstTokenAtMillis = 1400L,
                    completedAtMillis = 3400L,
                ),
            )
            // Corrupt line should be skipped without breaking valid records.
            file.appendText("{not valid json\n", Charsets.UTF_8)
            ledger.record(
                caller = "osce",
                statistics = ChatUsageStatistics(
                    inputTokens = 500,
                    outputTokens = 100,
                    totalTokens = 600,
                    tokenUsageSource = "provider",
                    startedAtMillis = 5000L,
                    completedAtMillis = 6000L,
                ),
            )

            val snapshots = ledger.snapshots()
            assertEquals(2, snapshots.size)
            assertEquals("extension:storylab", snapshots[0].sessionId)
            assertEquals(1550L, snapshots[0].statistics.totalTokens)
            assertEquals(400L, snapshots[0].statistics.firstTokenLatencyMillis)
            assertEquals("extension:osce", snapshots[1].sessionId)
            assertEquals(600L, snapshots[1].statistics.totalTokens)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun skipFullyFallsBackToReadWhenSkipReturnsZero() {
        val payload = "0123456789abcdef".toByteArray()
        val zeroSkipStream = object : FilterInputStream(ByteArrayInputStream(payload)) {
            override fun skip(n: Long): Long = 0L
        }
        SharedDocuments.skipFully(zeroSkipStream, 10L)
        val remaining = zeroSkipStream.readBytes()
        assertArrayEquals("abcdef".toByteArray(), remaining)
    }

    @Test
    fun skipFullyRejectsOffsetPastEndOfStream() {
        val stream = ByteArrayInputStream("short".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            SharedDocuments.skipFully(stream, 100L)
        }
    }

    @Test
    fun hostedAppIdValidationRejectsUnsafeIds() {
        assertEquals("storylab-2", HostedApps.validId("storylab-2"))
        assertThrows(IllegalArgumentException::class.java) { HostedApps.validId("") }
        assertThrows(IllegalArgumentException::class.java) { HostedApps.validId("../evil") }
        assertThrows(IllegalArgumentException::class.java) { HostedApps.validId("StoryLab") }
        assertThrows(IllegalArgumentException::class.java) { HostedApps.validId("-leading-dash") }
    }
}
