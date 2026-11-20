package com.zhousl.aether.data

import android.content.Context
import com.zhousl.aether.ui.ChatUsageStatistics
import org.json.JSONObject
import java.io.File

private const val UsageLedgerMaxBytes = 512 * 1024
private const val UsageLedgerTrimBytes = 384 * 1024

/** Bounded append-only usage ledger for extension / hosted-app model completions. */
class ExtensionUsageLedger private constructor(
    private val ledgerFile: File?,
) {
    private val lock = Any()

    constructor(context: Context) : this(
        File(File(context.filesDir, "hosted-apps-v1"), "usage-ledger.jsonl"),
    )

    companion object {
        fun forFile(file: File): ExtensionUsageLedger = ExtensionUsageLedger(file)
    }

    fun record(
        caller: String,
        statistics: ChatUsageStatistics,
    ) {
        val file = ledgerFile ?: return
        val normalizedCaller = caller.trim().ifBlank { "extension" }
        val entry = JSONObject().apply {
            put("sessionId", "extension:$normalizedCaller")
            statistics.inputTokens?.let { put("inputTokens", it) }
            statistics.outputTokens?.let { put("outputTokens", it) }
            statistics.totalTokens?.let { put("totalTokens", it) }
            statistics.reasoningTokens?.let { put("reasoningTokens", it) }
            statistics.cachedInputTokens?.let { put("cachedInputTokens", it) }
            statistics.cacheWriteTokens?.let { put("cacheWriteTokens", it) }
            statistics.outputDurationMillis?.let { put("outputDurationMillis", it) }
            put("requestCount", statistics.requestCount.coerceAtLeast(1))
            put("tokenUsageSource", statistics.tokenUsageSource)
            put("startedAtMillis", statistics.startedAtMillis)
            statistics.firstTokenAtMillis?.let { put("firstTokenAtMillis", it) }
            put("completedAtMillis", statistics.completedAtMillis)
        }.toString()
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(entry + "\n", Charsets.UTF_8)
                trimIfNeeded(file)
            }
        }
    }

    fun snapshots(): List<ChatUsageStatisticsSnapshot> {
        val file = ledgerFile ?: return emptyList()
        val lines = synchronized(lock) {
            if (!file.isFile) return emptyList()
            runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
        }
        return lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            runCatching {
                val json = JSONObject(trimmed)
                val sessionId = json.optString("sessionId").ifBlank { "extension" }
                ChatUsageStatisticsSnapshot(
                    sessionId = sessionId,
                    statistics = ChatUsageStatistics(
                        inputTokens = json.optLongOrNull("inputTokens"),
                        outputTokens = json.optLongOrNull("outputTokens"),
                        totalTokens = json.optLongOrNull("totalTokens"),
                        reasoningTokens = json.optLongOrNull("reasoningTokens"),
                        cachedInputTokens = json.optLongOrNull("cachedInputTokens"),
                        cacheWriteTokens = json.optLongOrNull("cacheWriteTokens"),
                        outputDurationMillis = json.optLongOrNull("outputDurationMillis"),
                        requestCount = json.optInt("requestCount", 1).coerceAtLeast(1),
                        tokenUsageSource = json.optString("tokenUsageSource", "unavailable"),
                        startedAtMillis = json.optLong("startedAtMillis", 0L),
                        firstTokenAtMillis = json.optLongOrNull("firstTokenAtMillis"),
                        completedAtMillis = json.optLong("completedAtMillis", 0L),
                    ),
                )
            }.getOrNull()
        }
    }

    private fun trimIfNeeded(file: File) {
        val length = file.length()
        if (length <= UsageLedgerMaxBytes) return
        val bytes = file.readBytes()
        val start = (bytes.size - UsageLedgerTrimBytes).coerceAtLeast(0)
        val newlineIndex = bytes.indexOf('\n'.code.toByte(), start)
        val sliceStart = if (newlineIndex >= 0 && newlineIndex + 1 < bytes.size) {
            newlineIndex + 1
        } else {
            start
        }
        file.writeBytes(bytes.copyOfRange(sliceStart, bytes.size))
    }

    private fun ByteArray.indexOf(target: Byte, startIndex: Int): Int {
        for (i in startIndex until size) {
            if (this[i] == target) return i
        }
        return -1
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
