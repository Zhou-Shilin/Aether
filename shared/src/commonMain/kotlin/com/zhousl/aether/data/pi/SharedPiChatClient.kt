package com.zhousl.aether.data.pi

import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.ProviderAuthMethod
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
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

class SharedPiChatClient(
    private val bridge: SharedPiBridgeClient,
    private val hostToolExecutor: SharedHostToolExecutor? = null,
    private val onOAuthCredentialUpdated: suspend (providerConfigId: String, credentialJson: String) -> Unit =
        { _, _ -> },
) {
    suspend fun completeOnce(
        config: LlmProviderConfig,
        messages: List<SharedPiChatMessage>,
        systemPrompt: String = platformDefaultSystemPrompt(),
        reasoning: String = "off",
        timeoutMillis: Int = 360_000,
    ): SharedPiTurnResult {
        val response = bridge.request(
            type = "complete_once",
            payload = buildJsonObject {
                put("model_config", config.toSharedPiModelConfig(timeoutMillis))
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
        systemPrompt: String = platformDefaultSystemPrompt(),
        reasoning: String = "off",
        timeoutMillis: Int = 360_000,
        onAssistantTextDelta: suspend (String) -> Unit = {},
        onAssistantReasoningDelta: suspend (String) -> Unit = {},
        onAssistantReasoningSummaryDelta: suspend (String) -> Unit = {},
        onHostToolStarted: suspend (SharedPiHostToolCall) -> Unit = {},
        onHostToolFinished: suspend (SharedPiHostToolCall, SharedHostToolResult) -> Unit = { _, _ -> },
        onStreamingStatus: suspend (SharedPiStreamingStatus?) -> Unit = {},
        pollInjectedUserMessages: suspend () -> List<SharedPiChatMessage> = { emptyList() },
    ): SharedPiTurnResult {
        val extensionLoadOptions = bridge.extensionLoadOptions()
        val resolvedSessionId = sessionId.ifBlank { "aether-session-${platformRandomUuid()}" }
        val hostToolDefinitions = when (val executor = hostToolExecutor) {
            is SharedSessionAwareHostToolExecutor -> executor.definitions(resolvedSessionId)
            else -> executor?.definitions ?: JsonArray(emptyList())
        }
        val payload = buildJsonObject {
            put("model_config", config.toSharedPiModelConfig(timeoutMillis))
            put("session_id", resolvedSessionId)
            put("system_prompt", systemPrompt.ifBlank { platformDefaultSystemPrompt() })
            put("messages", messages.toPiMessages())
            put("workspace_directory", workspaceDirectory)
            put("reasoning", reasoning)
            extensionLoadOptions.toPayload().forEach { (key, value) -> put(key, value) }
            put("host_tools", hostToolDefinitions)
        }
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
        return response.toSharedPiTurnResult(config)
    }

    private suspend fun JsonObject.toSharedPiTurnResult(
        config: LlmProviderConfig,
    ): SharedPiTurnResult {
        val usage = this["usage"] as? JsonObject ?: JsonObject(emptyMap())
        val updatedOauthCredentialJson = (this["oauth_credential"] as? JsonObject)
            ?.takeIf(JsonObject::isNotEmpty)
            ?.toString()
            .orEmpty()
        if (config.id.isNotBlank() && updatedOauthCredentialJson.isNotBlank()) {
            onOAuthCredentialUpdated(config.id, updatedOauthCredentialJson)
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
            ),
            usageAvailable = usage.isNotEmpty(),
            providerPayloadJson = toSharedProviderPayloadJson(),
            updatedOauthCredentialJson = updatedOauthCredentialJson,
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
        onFinished(call, result)
        bridge.request(
            type = "host_tool_result",
            payload = buildJsonObject {
                put("tool_request_id", request.string("tool_request_id"))
                put("session_id", request.string("session_id"))
                put("tool_call_id", request.string("tool_call_id"))
                put("tool_name", toolName)
                put("arguments_json", request.string("arguments_json"))
                put("output_json", result.outputJson)
                put("is_error", result.isError)
            },
            timeoutMillis = 15_000,
            abortOnCancellation = false,
        )
    }
}

data class SharedPiHostToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

fun LlmProviderConfig.toSharedPiModelConfig(timeoutMillis: Int = 360_000): JsonObject {
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
        put("reasoning", false)
        put("context_window", 128_000)
        put("max_tokens", 16_384)
        put("timeout_ms", timeoutMillis.coerceIn(30_000, 3_600_000))
        put("max_retries", 2)
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

internal fun JsonObject.sharedHostToolSessionId(): String =
    get(SharedHostToolSessionIdArgument)?.jsonPrimitive?.contentOrNull.orEmpty()
