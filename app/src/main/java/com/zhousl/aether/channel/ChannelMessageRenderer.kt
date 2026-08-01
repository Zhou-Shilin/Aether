package com.zhousl.aether.channel

import org.json.JSONArray
import org.json.JSONObject

/**
 * Channel-neutral renderer for Agent events. Platform adapters only transport the
 * rendered text/files; they do not need to understand AgentHarness event schemas.
 */
class ChannelMessageRenderer(
    private val options: ChannelDisplayOptions,
) {
    fun toolCall(name: String, argumentsJson: String): String? {
        if (!options.showToolCalls) return null
        val preview = truncate(redactJson(argumentsJson), options.toolCallMaxLength)
        return "🔧 **${safeToolName(name)}**\n```\n$preview\n```"
    }

    fun toolResult(name: String, output: String, isError: Boolean): String? {
        if (!options.showToolResults) return null
        val marker = runCatching { JSONObject(output) }.getOrNull()
        val sentFile = marker?.optJSONObject(AetherChannelFileMarker)
        val preview = if (sentFile != null) {
            val fileName = sentFile.optString("name").ifBlank { "file" }
            val size = sentFile.optLong("size_bytes", -1L)
            "文件已发送：$fileName${if (size >= 0) "（${formatBytes(size)}）" else ""}"
        } else {
            truncate(redactJson(output), options.toolResultMaxLength)
        }
        val icon = if (isError) "⚠️" else "✅"
        return "$icon **${safeToolName(name)}**:\n```\n$preview\n```"
    }

    fun thinking(accumulatedText: String): String? =
        accumulatedText.trim().takeIf { options.showThinking && it.isNotEmpty() }
            ?.let { "💭 $it" }

    fun streamingSnapshot(
        reasoning: String,
        toolTimeline: List<String>,
        assistantText: String,
    ): String = buildList {
        thinking(reasoning)?.let(::add)
        addAll(toolTimeline.filter(String::isNotBlank))
        assistantText.trim().takeIf(String::isNotEmpty)?.let(::add)
    }.joinToString("\n\n")

    companion object {
        const val AetherChannelFileMarker = "_aether_channel_file"

        private val sensitiveKeys = setOf(
            "authorization",
            "token",
            "access_token",
            "refresh_token",
            "api_key",
            "apikey",
            "password",
            "secret",
            "app_secret",
            "client_secret",
        )

        fun redactJson(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return runCatching {
                when {
                    trimmed.startsWith("{") -> redactObject(JSONObject(trimmed)).toString(2)
                    trimmed.startsWith("[") -> redactArray(JSONArray(trimmed)).toString(2)
                    else -> raw
                }
            }.getOrDefault(raw)
        }

        private fun redactObject(source: JSONObject): JSONObject = JSONObject().also { target ->
            source.keys().forEach { key ->
                val normalized = key.lowercase().replace('-', '_')
                val value = source.opt(key)
                target.put(
                    key,
                    if (sensitiveKeys.any { normalized == it || normalized.endsWith("_$it") }) {
                        "[REDACTED]"
                    } else {
                        redactValue(value)
                    },
                )
            }
        }

        private fun redactArray(source: JSONArray): JSONArray = JSONArray().also { target ->
            repeat(source.length()) { target.put(redactValue(source.opt(it))) }
        }

        private fun redactValue(value: Any?): Any? = when (value) {
            is JSONObject -> redactObject(value)
            is JSONArray -> redactArray(value)
            else -> value
        }

        private fun truncate(value: String, limit: Int): String =
            if (limit <= 0 || value.length <= limit) value else value.take(limit) + "…"

        private fun safeToolName(value: String): String =
            value.ifBlank { "tool" }.replace("`", "").take(120)

        private fun formatBytes(bytes: Long): String = when {
            bytes < 1_024 -> "$bytes B"
            bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
            else -> String.format("%.1f MB", bytes / (1_024.0 * 1_024.0))
        }
    }
}
