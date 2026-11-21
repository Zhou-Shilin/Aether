package com.zhousl.aether.data.pi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val piSessionHeaderJson = Json { ignoreUnknownKeys = true }

/**
 * Validates the first line of a persisted Pi session JSONL file (`{"type":"session","id":"..."}`).
 */
fun isValidPiSessionHeaderLine(
    firstLine: String,
    expectedSessionId: String,
): Boolean {
    if (expectedSessionId.isBlank() || firstLine.isBlank()) return false
    val header = runCatching {
        piSessionHeaderJson.parseToJsonElement(firstLine.trim()) as? JsonObject
    }.getOrNull() ?: return false
    val type = header["type"]?.jsonPrimitive?.contentOrNull
    val id = header["id"]?.jsonPrimitive?.contentOrNull
    return type == "session" && id == expectedSessionId
}

/**
 * Selects which messages must be serialized in a `run_turn` request to `pi-bridge`.
 *
 * When a validated Pi session JSONL file already exists on disk and we are NOT performing a
 * legacy branch reset (`sessionBranch != null && sessionBranch.entryId.isNullOrBlank()`),
 * `pi-bridge` opens the existing session file directly (`existed = true`) and only reads
 * the last message (`promptFromLastUserMessage`), discarding all earlier messages in `payload.messages`.
 *
 * Sending only the trailing message avoids re-serializing dozens of megabytes of historical
 * messages and inline base64 images over IPC on every turn in long sessions.
 */
fun <T> selectPiTurnRequestMessages(
    messages: List<T>,
    hasValidatedSessionFile: Boolean,
    sessionBranch: PiSessionBranch?,
): List<T> {
    if (messages.size <= 1) return messages
    val needsLegacyHistoryRebuild = sessionBranch != null && sessionBranch.entryId.isNullOrBlank()
    if (!hasValidatedSessionFile || needsLegacyHistoryRebuild) return messages
    return listOf(messages.last())
}
