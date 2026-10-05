package com.zhousl.aether.data

import org.json.JSONObject

data class LlmTokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val outputDurationMillis: Long? = null,
    val requestCount: Int = 1,
) {
    operator fun plus(other: LlmTokenUsage): LlmTokenUsage = LlmTokenUsage(
        inputTokens = sumNullable(inputTokens, other.inputTokens),
        outputTokens = sumNullable(outputTokens, other.outputTokens),
        totalTokens = sumNullable(totalTokens, other.totalTokens),
        reasoningTokens = sumNullable(reasoningTokens, other.reasoningTokens),
        cachedInputTokens = sumNullable(cachedInputTokens, other.cachedInputTokens),
        cacheWriteTokens = sumNullable(cacheWriteTokens, other.cacheWriteTokens),
        outputDurationMillis = sumNullable(outputDurationMillis, other.outputDurationMillis),
        requestCount = requestCount + other.requestCount,
    )

    fun withMissingTotalResolved(): LlmTokenUsage = if (totalTokens != null) {
        this
    } else {
        copy(totalTokens = sumNullable(inputTokens, outputTokens))
    }

    companion object {
        fun sumNullable(first: Long?, second: Long?): Long? = when {
            first == null -> second
            second == null -> first
            else -> first + second
        }
    }
}

sealed interface LlmContentPart

data class LlmTextPart(
    val text: String,
) : LlmContentPart

data class LlmImagePart(
    val mimeType: String,
    val base64Data: String,
) : LlmContentPart

data class LlmMessage(
    val role: String,
    val contentParts: List<LlmContentPart>,
    val providerPayload: JSONObject? = null,
    val toolCallId: String = "",
    val toolName: String = "",
    val isError: Boolean = false,
)
