package com.zhousl.aether.data.pi

import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.ModelsDevModelLimits
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.SharedDiagnosticLogger
import com.zhousl.aether.data.normalizeReasoningEffort
import com.zhousl.aether.data.normalizeLlmUserAgent
import com.zhousl.aether.data.platformDefaultSystemPrompt
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.runtime.SharedPiBridgeClient
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val SharedInjectedMessagePollIntervalMillis = 150L
private const val SharedHostToolSessionIdArgument = "__aether_session_id"

data class SharedPiChatMessage(
    val role: String,
    val text: String,
    val images: List<SharedPiImage> = emptyList(),
    val providerPayload: JsonObject? = null,
    val contentParts: List<SharedPiContentPart> = emptyList(),
)

data class SharedPiImage(
    val mimeType: String,
    val data: String,
)

sealed interface SharedPiContentPart {
    data class Text(val text: String) : SharedPiContentPart
    data class Image(val mimeType: String, val data: String) : SharedPiContentPart
}

data class SharedPiTurnResult(
    val assistantText: String,
    val reasoningText: String = "",
    val provider: String = "",
    val model: String = "",
    val errorMessage: String = "",
    val usage: SharedPiUsage = SharedPiUsage(),
    val usageAvailable: Boolean = false,
    val providerPayloadJson: String = "",
    val updatedOauthCredentialJson: String = "",
    val piSessionId: String = "",
    val piSessionFile: String = "",
    val piRuntime: String = "",
    val piEntryIds: List<String> = emptyList(),
)

data class SharedPiUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val inputTokensAvailable: Boolean = true,
    val outputTokensAvailable: Boolean = true,
    val totalTokensAvailable: Boolean = true,
    val reasoningTokensAvailable: Boolean = true,
    val cachedInputTokensAvailable: Boolean = true,
    val requestCount: Int = 1,
)

data class SharedPiStreamingStatus(
    val text: String,
    val detail: String = "",
)

data class SharedPiToolEvent(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val outputJson: String? = null,
    val isRunning: Boolean,
    val isError: Boolean = false,
)

class SharedPiChatClient(
    private val bridge: SharedPiBridgeClient,
    private val hostToolExecutor: SharedHostToolExecutor? = null,
    private val onOAuthCredentialUpdated: suspend (providerConfigId: String, credentialJson: String) -> Unit =
        { _, _ -> },
    private val onDeveloperRoleUnsupportedDetected: suspend (providerConfigId: String) -> Unit = {},
) {
    suspend fun completeOnce(
        config: LlmProviderConfig,
        messages: List<SharedPiChatMessage>,
        systemPrompt: String = platformDefaultSystemPrompt(),
        reasoning: String = "off",
        timeoutMillis: Int = 360_000,
        thinkingLevelMap: Map<String, String> = emptyMap(),
        isReasoningModel: Boolean? = null,
        modelsDevThinkingLevels: List<String>? = null,
        modelsDevLimits: ModelsDevModelLimits? = null,
    ): SharedPiTurnResult {
        val response = bridge.request(
            type = "complete_once",
            payload = buildJsonObject {
                put("model_config", config.toSharedPiModelConfig(
                    timeoutMillis = timeoutMillis,
                    reasoningEnabled = isReasoningModel ?: (
                        reasoning != "off" || thinkingLevelMap["off"] == "none"
                    ),
                    thinkingLevelMap = thinkingLevelMap,
                    modelsDevThinkingLevels = modelsDevThinkingLevels,
                    modelsDevLimits = modelsDevLimits,
                ))
                put("system_prompt", systemPrompt.ifBlank { platformDefaultSystemPrompt() })
                put("messages", messages.toPiMessages())
                put("stream", false)
                put("reasoning", reasoning)
            },
        )
        return response.toSharedPiTurnResult(config)
    }

    suspend fun steer(
        sessionId: String,
        message: SharedPiChatMessage,
    ): Boolean {
        val response = bridge.request(
            type = "steer",
            payload = buildJsonObject {
                put("session_id", sessionId)
                put("message", message.toPiMessage())
            },
            abortOnCancellation = false,
        )
        return response["accepted"]?.jsonPrimitive?.booleanOrNull == true
    }

    suspend fun runTurn(
        config: LlmProviderConfig,
        messages: List<SharedPiChatMessage>,
        sessionId: String,
        workspaceDirectory: String = "/workspace",
        skillPaths: List<String> = emptyList(),
        skillCommand: String = "",
        systemPrompt: String = platformDefaultSystemPrompt(),
        reasoning: String = "off",
        timeoutMillis: Int = 360_000,
        thinkingLevelMap: Map<String, String> = emptyMap(),
        isReasoningModel: Boolean? = null,
        modelsDevThinkingLevels: List<String>? = null,
        modelsDevLimits: ModelsDevModelLimits? = null,
        onAssistantTextDelta: suspend (String) -> Unit = {},
        onAssistantReasoningDelta: suspend (String) -> Unit = {},
        onAssistantReasoningSummaryDelta: suspend (String) -> Unit = {},
        onAssistantRequestStarted: suspend () -> Unit = {},
        onAssistantResponseReset: suspend () -> Unit = {},
        onToolEvent: suspend (SharedPiToolEvent) -> Unit = {},
        onHostToolStarted: suspend (SharedPiHostToolCall) -> Unit = {},
        onHostToolFinished: suspend (SharedPiHostToolCall, SharedHostToolResult) -> Unit = { _, _ -> },
        onStreamingStatus: suspend (SharedPiStreamingStatus?) -> Unit = {},
        pollInjectedUserMessages: suspend () -> List<SharedPiChatMessage> = { emptyList() },
    ): SharedPiTurnResult {
        val appendedPiEntryIds = mutableListOf<String>()
        val extensionLoadOptions = bridge.extensionLoadOptions()
        val resolvedSessionId = sessionId.ifBlank { "aether-session-${platformRandomUuid()}" }
        val hostToolDefinitions = when (val executor = hostToolExecutor) {
            is SharedSessionAwareHostToolExecutor -> executor.definitions(resolvedSessionId)
            else -> executor?.definitions ?: JsonArray(emptyList())
        }
        val normalizedReasoning = normalizeReasoningEffort(reasoning)
        val resolvedReasoningEnabled = isReasoningModel ?: (
            normalizedReasoning != "off" || thinkingLevelMap["off"] == "none"
        )
        val modelConfig = config.toSharedPiModelConfig(
            timeoutMillis = timeoutMillis,
            reasoningEnabled = resolvedReasoningEnabled,
            thinkingLevelMap = thinkingLevelMap,
            modelsDevThinkingLevels = modelsDevThinkingLevels,
            modelsDevLimits = modelsDevLimits,
        )
        val payload = buildJsonObject {
            put("model_config", modelConfig)
            put("session_id", resolvedSessionId)
            put("system_prompt", systemPrompt.ifBlank { platformDefaultSystemPrompt() })
            put("messages", messages.withSkillCommand(skillCommand).toPiMessages())
            put("workspace_directory", workspaceDirectory)
            put("termux_workspace_directory", workspaceDirectory)
            put("runtime", "alpine")
            put("platform", "ios")
            // Aether owns this workspace; allow Pi to discover project Skills.
            put("workspace_trusted", true)
            put("chrome_enabled", false)
            put("skill_paths", buildJsonArray {
                skillPaths.distinct().forEach { add(JsonPrimitive(it)) }
            })
            put("reasoning", normalizedReasoning)
            extensionLoadOptions.toPayload().forEach { (key, value) -> put(key, value) }
            put("host_tools", hostToolDefinitions)
        }
        SharedDiagnosticLogger.event(
            category = "pi_agent",
            event = "reasoning_effort_resolved",
            sessionId = resolvedSessionId,
            details = mapOf(
                "configured_effort" to reasoning,
                "normalized_effort" to normalizedReasoning,
                "model_config_reasoning" to resolvedReasoningEnabled,
                "reasoning_model_catalog_match" to isReasoningModel,
                "mapped_effort" to thinkingLevelMap[normalizedReasoning].orEmpty(),
                "thinking_level_map" to thinkingLevelMap,
                "provider" to config.piProviderId,
                "model" to config.modelId,
                "session_id_was_generated" to sessionId.isBlank(),
            ),
        )
        onStreamingStatus(
            SharedPiStreamingStatus(
                text = "Thinking",
                detail = "Aether is working on this turn.",
            )
        )
        val eventHandler: suspend (String, JsonObject) -> Unit = { event, eventPayload ->
            when (event) {
                "assistant_text_delta" -> onAssistantTextDelta(eventPayload.string("delta"))
                "assistant_reasoning_delta" -> {
                    val delta = eventPayload.string("delta")
                    onAssistantReasoningDelta(delta)
                    if (eventPayload.string("kind") == "summary") {
                        onAssistantReasoningSummaryDelta(delta)
                    }
                }
                "assistant_request_start" -> onAssistantRequestStarted()
                "assistant_stream_reset" -> onAssistantResponseReset()
                "tool_call_start" -> onToolEvent(eventPayload.toSharedPiToolEvent(isRunning = true))
                "tool_call_delta" -> onToolEvent(eventPayload.toSharedPiToolEvent(isRunning = true))
                "tool_call_end" -> onToolEvent(eventPayload.toSharedPiToolEvent(isRunning = false))
                "session_entry_appended" -> {
                    val entryId = (eventPayload["entry"] as? JsonObject)?.string("id").orEmpty()
                    if (entryId.isNotBlank()) appendedPiEntryIds += entryId
                }
                "assistant_retry" -> onStreamingStatus(
                    SharedPiStreamingStatus(
                        text = "Reconnecting... ${eventPayload.int("attempt")}/${eventPayload.int("max_attempts")}",
                        detail = buildString {
                            append(eventPayload.string("error_message"))
                            val delayMillis = eventPayload.int("delay_ms")
                            if (delayMillis > 0) {
                                if (isNotEmpty()) append('\n')
                                append("Retrying in ")
                                if (delayMillis % 1_000 == 0) {
                                    append(delayMillis / 1_000).append('s')
                                } else {
                                    append(delayMillis).append("ms")
                                }
                            }
                        },
                    )
                )
                "assistant_error" -> onStreamingStatus(
                    SharedPiStreamingStatus(
                        text = "Agent engine error",
                        detail = eventPayload.string("error_message"),
                    )
                )
                "host_tool_request" -> executeHostTool(
                    request = eventPayload,
                    onStarted = onHostToolStarted,
                    onFinished = onHostToolFinished,
                )
            }
        }
        val response = try {
            coroutineScope {
                val deferredInjectedMessages = Channel<SharedPiChatMessage>(Channel.UNLIMITED)
                val injectedMessageForwardMutex = Mutex()
                suspend fun forwardInjectedMessages() = injectedMessageForwardMutex.withLock {
                    pollInjectedUserMessages().forEach { message ->
                        val accepted = try {
                            steer(resolvedSessionId, message)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Throwable) {
                            false
                        }
                        if (!accepted) deferredInjectedMessages.send(message)
                    }
                }

                val pollingJob = launch {
                    while (isActive) {
                        forwardInjectedMessages()
                        delay(SharedInjectedMessagePollIntervalMillis)
                    }
                }
                try {
                    var resolvedResponse = bridge.request(
                        type = "run_turn",
                        payload = payload,
                        timeoutMillis = null,
                        onEvent = eventHandler,
                    )
                    forwardInjectedMessages()
                    pollingJob.cancelAndJoin()
                    while (true) {
                        val injected = deferredInjectedMessages.tryReceive().getOrNull() ?: break
                        resolvedResponse = bridge.request(
                            type = "follow_up",
                            payload = buildJsonObject {
                                put("session_id", resolvedSessionId)
                                put("message", injected.toPiMessage())
                            },
                            timeoutMillis = null,
                            onEvent = eventHandler,
                        )
                    }
                    resolvedResponse
                } finally {
                    pollingJob.cancelAndJoin()
                    deferredInjectedMessages.close()
                }
            }
        } finally {
            onStreamingStatus(null)
        }
        return response.toSharedPiTurnResult(config, appendedPiEntryIds)
    }

    private suspend fun JsonObject.toSharedPiTurnResult(
        config: LlmProviderConfig,
        piEntryIds: List<String> = emptyList(),
    ): SharedPiTurnResult {
        val usage = this["usage"] as? JsonObject ?: JsonObject(emptyMap())
        val updatedOauthCredentialJson = (this["oauth_credential"] as? JsonObject)
            ?.takeIf(JsonObject::isNotEmpty)
            ?.toString()
            .orEmpty()
        if (config.id.isNotBlank() && updatedOauthCredentialJson.isNotBlank()) {
            onOAuthCredentialUpdated(config.id, updatedOauthCredentialJson)
        }
        if (
            config.id.isNotBlank() &&
            this["developer_role_unsupported_detected"]?.jsonPrimitive?.booleanOrNull == true
        ) {
            onDeveloperRoleUnsupportedDetected(config.id)
        }
        return SharedPiTurnResult(
            assistantText = string("assistant_text"),
            reasoningText = string("reasoning_text"),
            provider = string("provider"),
            model = string("model"),
            errorMessage = string("error_message"),
            usage = SharedPiUsage(
                inputTokens = usage.long("input_tokens"),
                outputTokens = usage.long("output_tokens"),
                totalTokens = usage.long("total_tokens"),
                reasoningTokens = usage.long("reasoning_tokens"),
                cachedInputTokens = usage.long("cached_input_tokens"),
                inputTokensAvailable = "input_tokens" in usage,
                outputTokensAvailable = "output_tokens" in usage,
                totalTokensAvailable = "total_tokens" in usage,
                reasoningTokensAvailable = "reasoning_tokens" in usage,
                cachedInputTokensAvailable = "cached_input_tokens" in usage,
            ),
            usageAvailable = usage.isNotEmpty(),
            providerPayloadJson = toSharedProviderPayloadJson(),
            updatedOauthCredentialJson = updatedOauthCredentialJson,
            piSessionId = string("session_id"),
            piSessionFile = string("session_file"),
            piRuntime = string("runtime"),
            piEntryIds = piEntryIds.distinct(),
        )
    }

    private suspend fun executeHostTool(
        request: JsonObject,
        onStarted: suspend (SharedPiHostToolCall) -> Unit,
        onFinished: suspend (SharedPiHostToolCall, SharedHostToolResult) -> Unit,
    ) {
        val executor = hostToolExecutor ?: return
        val toolName = request.string("tool_name")
        val arguments = request["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val call = SharedPiHostToolCall(
            id = request.string("tool_call_id").ifBlank { request.string("tool_request_id") },
            name = toolName,
            arguments = arguments,
        )
        onStarted(call)
        val executorArguments = JsonObject(
            arguments + (SharedHostToolSessionIdArgument to JsonPrimitive(request.string("session_id"))),
        )
        val result = executor.execute(toolName, executorArguments)
        val parsedOutput = runCatching { Json.parseToJsonElement(result.outputJson).jsonObject }.getOrNull()
        val screenshotBase64 = parsedOutput?.string("screenshot_base64").orEmpty()
        val screenshotMimeType = parsedOutput?.string("screenshot_mime_type").orEmpty().ifBlank { "image/png" }
        val visibleOutput = parsedOutput
            ?.let { JsonObject(it - "screenshot_base64") }
            ?.toString()
            ?: result.outputJson
        onFinished(call, result.copy(outputJson = visibleOutput))
        bridge.request(
            type = "host_tool_result",
            payload = buildJsonObject {
                put("tool_request_id", request.string("tool_request_id"))
                put("session_id", request.string("session_id"))
                put("tool_call_id", request.string("tool_call_id"))
                put("tool_name", toolName)
                put("arguments_json", request.string("arguments_json"))
                put("output_json", visibleOutput)
                put("is_error", result.isError)
                if (screenshotBase64.isNotBlank()) {
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", visibleOutput)
                        })
                        add(buildJsonObject {
                            put("type", "image")
                            put("mime_type", screenshotMimeType)
                            put("data", screenshotBase64)
                        })
                    })
                }
            },
            timeoutMillis = 15_000,
            abortOnCancellation = false,
        )
    }
}

private fun List<SharedPiChatMessage>.withSkillCommand(name: String): List<SharedPiChatMessage> {
    val normalized = name.trim()
    if (normalized.isBlank()) return this
    val index = indexOfLast { it.role == "user" }
    if (index < 0) return this
    val current = get(index)
    return toMutableList().apply {
        this[index] = current.copy(text = "/skill:$normalized ${current.text}")
    }
}

data class SharedPiHostToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

fun LlmProviderConfig.toSharedPiModelConfig(
    timeoutMillis: Int = 360_000,
    reasoningEnabled: Boolean = false,
    thinkingLevelMap: Map<String, String> = emptyMap(),
    modelsDevThinkingLevels: List<String>? = null,
    modelsDevLimits: ModelsDevModelLimits? = null,
): JsonObject {
    val definition = PiProviderCatalog.resolve(piProviderId)
    val effectiveAuthMethod = if (
        authMethod == ProviderAuthMethod.ApiKey &&
        !definition.supportsApiKey &&
        definition.supportsAmbientAuth
    ) {
        ProviderAuthMethod.Ambient
    } else {
        authMethod
    }
    val resolvedPiProviderId = if (definition.isBuiltIn) {
        definition.id
    } else {
        "aether-${stableProviderSuffix(providerId.ifBlank { baseUrl })}"
    }
    return buildJsonObject {
        put("provider_type", if (definition.isBuiltIn) "builtin" else "custom")
        put("provider_config_id", id)
        put("pi_provider_id", resolvedPiProviderId)
        put("pi_api", if (definition.isBuiltIn) "builtin" else "openai-completions")
        put("model_id", modelId.trim())
        put("base_url", baseUrl.trim())
        put("api_key", if (effectiveAuthMethod == ProviderAuthMethod.ApiKey) apiKey.trim() else "")
        put("custom_headers", buildJsonObject {
            customHeaders.forEach { header ->
                header.name.trim().takeIf(String::isNotBlank)?.let { put(it, header.value) }
            }
            put("User-Agent", normalizeLlmUserAgent(userAgent))
        })
        if (!definition.isBuiltIn && compatibilityMode) put("compatibility_mode", true)
        if (developerRoleUnsupported) put("supports_developer_role", false)
        put("reasoning", reasoningEnabled)
        if (thinkingLevelMap.isNotEmpty()) {
            put("thinking_level_map", buildJsonObject {
                thinkingLevelMap.forEach { (k, v) -> put(k, v) }
            })
        }
        if (modelsDevThinkingLevels != null) {
            put("models_dev", buildJsonObject {
                put("reasoning", reasoningEnabled)
                put("thinking_levels", buildJsonArray { modelsDevThinkingLevels.forEach { add(it) } })
                modelsDevLimits?.contextWindow?.let { put("context_window", it) }
                modelsDevLimits?.maxOutputTokens?.let { put("max_tokens", it) }
                modelsDevLimits?.supportsImageInput?.let { supportsImages ->
                    put("input", buildJsonArray {
                        add("text")
                        if (supportsImages) add("image")
                    })
                }
            })
        }
        put("context_window", modelsDevLimits?.contextWindow ?: 128_000)
        put("max_tokens", modelsDevLimits?.maxOutputTokens ?: 16_384)
        put("timeout_ms", timeoutMillis.coerceIn(30_000, 3_600_000))
        put("max_retries", 5)
        put("max_retry_delay_ms", 60_000)
        put("auth_method", effectiveAuthMethod.storageValue)
        if (oauthCredentialJson.isNotBlank()) {
            val credential = runCatching {
                Json.parseToJsonElement(oauthCredentialJson) as? JsonObject
            }.getOrNull()
            if (credential != null) put("oauth_credential", credential)
        }
        put("provider_env", buildJsonObject {
            providerEnvironmentVariables.forEach { variable ->
                variable.name.trim().takeIf(String::isNotBlank)?.let { put(it, variable.value) }
            }
        })
    }
}

private fun List<SharedPiChatMessage>.toPiMessages(): JsonArray = buildJsonArray {
    this@toPiMessages.forEach { message ->
        add(message.toPiMessage())
    }
}

private fun SharedPiChatMessage.toPiMessage(): JsonObject = buildJsonObject {
    put("role", role)
    val resolvedParts = contentParts.ifEmpty {
        buildList {
            add(SharedPiContentPart.Text(text))
            images.forEach { image ->
                add(SharedPiContentPart.Image(image.mimeType, image.data))
            }
        }
    }
    put("content", buildJsonArray {
        resolvedParts.forEach { part ->
            add(
                when (part) {
                    is SharedPiContentPart.Text -> buildJsonObject {
                        put("type", "text")
                        put("text", part.text)
                    }
                    is SharedPiContentPart.Image -> buildJsonObject {
                        put("type", "image")
                        put("mime_type", part.mimeType)
                        put("data", part.data)
                    }
                }
            )
        }
    })
    providerPayload?.let { put("provider_payload", it) }
}

private fun JsonObject.toSharedProviderPayloadJson(): String = buildJsonObject {
    (this@toSharedProviderPayloadJson["assistant_message"] as? JsonObject)
        ?.takeIf(JsonObject::isNotEmpty)
        ?.let { put("piAssistantMessage", it) }
    put("provider", string("provider"))
    put("model", string("model"))
    put("responseId", string("response_id"))
    put("stopReason", string("stop_reason"))
    (this@toSharedProviderPayloadJson["usage"] as? JsonObject)
        ?.takeIf(JsonObject::isNotEmpty)
        ?.let { usage ->
            put("usage", buildJsonObject {
                listOf(
                    "input_tokens",
                    "output_tokens",
                    "total_tokens",
                    "reasoning_tokens",
                    "cached_input_tokens",
                ).forEach { key -> usage[key]?.let { put(key, it) } }
                put("request_count", 1)
            })
        }
}.toString()

private fun stableProviderSuffix(value: String): String = value
    .trim()
    .lowercase()
    .ifBlank { "custom" }
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(48)
    .ifBlank { "custom" }

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.long(name: String): Long =
    get(name)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0

private fun JsonObject.int(name: String): Int =
    get(name)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

internal fun JsonObject.toSharedPiToolEvent(isRunning: Boolean): SharedPiToolEvent =
    SharedPiToolEvent(
        id = string("id").ifBlank { "pi-tool-${int("content_index")}" },
        name = string("name").ifBlank { "tool_call" },
        argumentsJson = sharedToolEventJson(
            explicitKey = "arguments_json",
            valueKey = "arguments",
            fallbackKey = "delta",
        ) ?: "{}",
        outputJson = sharedToolEventJson(
            explicitKey = "output_json",
            valueKey = "output",
        ),
        isRunning = isRunning,
        isError = get("is_error")?.jsonPrimitive?.booleanOrNull == true,
    )

private fun JsonObject.sharedToolEventJson(
    explicitKey: String,
    valueKey: String,
    fallbackKey: String = "",
): String? {
    string(explicitKey).takeIf(String::isNotBlank)?.let { return it }
    get(valueKey)?.let { value ->
        return if (value is JsonPrimitive && value.isString) {
            value.contentOrNull?.takeIf(String::isNotBlank)
        } else {
            value.toString()
        }
    }
    return fallbackKey.takeIf(String::isNotBlank)
        ?.let(::string)
        ?.takeIf(String::isNotBlank)
}

internal fun JsonObject.sharedHostToolSessionId(): String =
    get(SharedHostToolSessionIdArgument)?.jsonPrimitive?.contentOrNull.orEmpty()
