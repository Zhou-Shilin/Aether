package com.zhousl.aether.data.pi

import kotlinx.serialization.json.*

/** Pi custom_message entries are persisted by Pi and stay on their originating branch. */
data class PiDisplayMessage(val id: String, val type: String, val text: String, val payloadJson: String)

fun parsePiDisplayMessage(json: String): PiDisplayMessage? =
    runCatching { piDisplayMessage(Json.parseToJsonElement(json) as JsonObject) }.getOrNull()

internal fun piDisplayMessage(entry: JsonObject): PiDisplayMessage? {
    if (entry["type"]?.jsonPrimitive?.contentOrNull != "custom_message" ||
        entry["display"]?.jsonPrimitive?.booleanOrNull != true) return null
    val id = entry["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val type = entry["customType"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (id.isBlank() || type.isBlank()) return null
    val content = entry["content"]
    val text = when (content) {
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        is JsonArray -> content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
        else -> ""
    }.take(8000)
    val payload = (entry["details"] as? JsonObject)?.toString() ?: "{}"
    if (payload.length > 16384) return null
    return PiDisplayMessage(id, type.take(200), text, payload)
}
