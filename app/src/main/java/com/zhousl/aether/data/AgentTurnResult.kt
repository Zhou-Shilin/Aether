package com.zhousl.aether.data

data class AetherAgentTurnResult(
    val assistantText: String,
    val tokenUsage: LlmTokenUsage? = null,
    val providerPayloadJson: String = "",
    val piSessionId: String = "",
    val piSessionFile: String = "",
    val piSessionLeafId: String = "",
    val runtime: String = "",
    val cwd: String = "",
    val piEntryIds: List<String> = emptyList(),
    val customMessages: List<com.zhousl.aether.data.pi.PiDisplayMessage> = emptyList(),
)

data class AgentToolEvent(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val outputJson: String? = null,
    val isRunning: Boolean? = null,
)

data class StreamingStatus(
    val text: String,
    val detail: String = "",
)
