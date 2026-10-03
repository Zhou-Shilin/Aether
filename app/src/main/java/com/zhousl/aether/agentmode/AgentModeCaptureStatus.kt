package com.zhousl.aether.agentmode

import org.json.JSONObject

/**
 * Capture contract between the Agent Mode user service and the app.
 *
 * A capture either returns real pixels of the virtual display or a black placeholder, because a
 * freshly created display has nothing to show yet. The service reports which one it was, and why, so
 * the agent cannot mistake a placeholder for the state of an app (issue #100).
 */
internal const val AgentModeCaptureSourceDisplay = "display"
internal const val AgentModeCaptureSourceBlank = "blank"
internal const val AgentModeBlankReasonLaunchFailed = "launch_failed"
internal const val AgentModeBlankReasonNoLaunchedContent = "no_launched_content"
internal const val AgentModeBlankReasonEmptyDisplay = "empty_display"

internal data class AgentModeCaptureOutcome(
    val isBlank: Boolean,
    val blankReason: String,
) {
    companion object {
        val Display = AgentModeCaptureOutcome(isBlank = false, blankReason = "")
    }
}

/** Builds the status string the Agent Mode service returns from `captureImageToFd`. */
internal fun agentModeCaptureStatus(source: String, blankReason: String = ""): String =
    JSONObject().apply {
        put("source", source)
        if (blankReason.isNotBlank()) put("blank_reason", blankReason)
    }.toString()

/** Parses the capture status; payloads that do not describe a placeholder count as a real capture. */
internal fun parseAgentModeCaptureOutcome(rawValue: String): AgentModeCaptureOutcome {
    val status = runCatching { JSONObject(rawValue) }.getOrNull()
        ?: return AgentModeCaptureOutcome.Display
    if (status.optString("source") != AgentModeCaptureSourceBlank) return AgentModeCaptureOutcome.Display
    return AgentModeCaptureOutcome(
        isBlank = true,
        blankReason = status.optString("blank_reason").ifBlank { AgentModeBlankReasonEmptyDisplay },
    )
}
