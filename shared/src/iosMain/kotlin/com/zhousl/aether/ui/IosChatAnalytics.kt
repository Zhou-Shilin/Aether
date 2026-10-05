package com.zhousl.aether.ui

import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.platform.IosAnalytics

internal fun captureIosMessageSent(
    config: LlmProviderConfig,
    target: SharedSessionUiState,
    attachmentCount: Int,
    isEdit: Boolean,
    submissionType: String,
) {
    val model = mapOf(
        "model" to config.modelId.trim(),
        "provider" to PiProviderCatalog.resolve(config.piProviderId).displayName,
        "provider_type" to config.piProviderId,
        "source" to "message",
        "agent_mode_enabled" to true,
        "skill_count" to target.selectedSkillIds.size,
        "mcp_server_count" to target.activeMcpServerIds.size,
    )
    val properties = model + mapOf(
        "has_attachments" to (attachmentCount > 0),
        "attachment_count" to attachmentCount,
        "is_edit" to isEdit,
        "submission_type" to submissionType,
    )
    IosAnalytics.capture("message sent", properties)
    IosAnalytics.capture("model used", properties)
    // iOS always runs in the app-owned Alpine runtime; no Termux authorization is needed.
    IosAnalytics.capture("agent mode started", properties + ("source" to "agent_mode"))
}

internal fun iosTurnAnalyticsProperties(
    message: SharedChatMessage?,
    outcome: String,
    durationMillis: Long,
    inputMessageCount: Int,
    userMessageCount: Int,
): Map<String, Any> {
    val usage = message?.usage
    val tools = buildList {
        addAll(message?.tools.orEmpty())
        message?.responseBlocks.orEmpty().forEach { block ->
            when (block) {
                is SharedAssistantResponseBlock.ToolGroup -> addAll(block.tools)
                is SharedAssistantResponseBlock.Reasoning -> addAll(block.trace.toolInvocations)
                else -> Unit
            }
        }
    }.distinctBy { it.id }
    val total = usage?.totalTokens ?: 0L
    val input = usage?.inputTokens ?: 0L
    return buildMap {
        put("outcome", outcome)
        put("duration_millis", durationMillis.coerceAtLeast(0L))
        put("tool_call_count", tools.size)
        put("distinct_tool_count", tools.map { it.name }.distinct().size)
        put("tool_names", tools.map { it.name }.distinct())
        put("has_tool_calls", tools.isNotEmpty())
        put("has_token_usage", usage != null)
        put("token_usage_source", message?.tokenUsageSource.orEmpty().ifBlank { "unavailable" })
        put("input_message_count", inputMessageCount.coerceAtLeast(0))
        put("user_message_count", userMessageCount.coerceAtLeast(0))
        put("llm_request_count", usage?.requestCount ?: 0)
        put("input_tokens", input)
        put("output_tokens", usage?.outputTokens ?: 0L)
        put("total_tokens", total)
        usage?.takeIf { it.reasoningTokensAvailable }?.let { put("reasoning_tokens", it.reasoningTokens) }
        usage?.takeIf { it.cachedInputTokensAvailable }?.let { put("cached_input_tokens", it.cachedInputTokens) }
        usage?.takeIf { it.cacheWriteTokensAvailable }?.let { put("cache_write_tokens", it.cacheWriteTokens) }
        put("average_tokens_per_input_message", if (inputMessageCount > 0) total.toDouble() / inputMessageCount else 0.0)
        put("average_input_tokens_per_input_message", if (inputMessageCount > 0) input.toDouble() / inputMessageCount else 0.0)
        put("average_tokens_per_user_message", if (userMessageCount > 0) total.toDouble() / userMessageCount else 0.0)
    }
}
