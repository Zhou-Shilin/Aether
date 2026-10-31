package com.zhousl.aether.ui

import org.json.JSONArray
import org.json.JSONObject

/** Extension snapshots must never serialize message history or inline attachments. */
internal fun extensionSessionSummaries(sessions: List<ChatSession>): JSONArray = JSONArray().apply {
    sessions.forEach { session ->
        put(JSONObject().apply {
            put("id", session.id)
            put("title", session.title.take(4_000))
            put("preview", session.preview.take(4_000))
            put("hasCustomTitle", session.hasCustomTitle)
            put("agentModeEnabled", session.agentModeEnabled)
            put("chromeEnabled", session.chromeEnabled)
            put("selectedModelKey", session.selectedModelKey)
            put("message_count", session.messageCount)
            put("last_message_at_millis", session.lastMessageAtMillis ?: 0L)
        })
    }
}

internal fun extensionStateRootKey(path: String): String? =
    path.trim().trim('/').replace('/', '.').split('.').firstOrNull(String::isNotBlank)
