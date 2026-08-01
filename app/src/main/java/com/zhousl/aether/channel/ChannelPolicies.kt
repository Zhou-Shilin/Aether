package com.zhousl.aether.channel

import java.util.LinkedHashMap

internal class ChannelMessageDeduplicator(
    private val ttlMillis: Long = 10 * 60_000L,
    private val maxEntries: Int = 2_048,
) {
    private val seen = LinkedHashMap<String, Long>(16, .75f, true)

    @Synchronized
    fun accept(channel: ChannelKind, messageId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val key = "${channel.storageValue}:$messageId"
        seen.entries.removeAll { nowMillis - it.value > ttlMillis }
        if (seen.containsKey(key)) return false
        seen[key] = nowMillis
        while (seen.size > maxEntries) seen.remove(seen.entries.first().key)
        return true
    }
}

internal object ChannelAccessController {
    fun isAllowed(policy: ChannelAccessPolicy, userId: String): Boolean = when (policy.mode) {
        ChannelAccessMode.Disabled -> false
        ChannelAccessMode.Open -> true
        ChannelAccessMode.AllowList -> userId.isNotBlank() && userId in policy.allowedUserIds
    }
}

internal fun String.normalizedChannelReply(maxChars: Int = 12_000): String =
    trim().ifBlank { "Aether completed the turn without a text response." }.take(maxChars)
