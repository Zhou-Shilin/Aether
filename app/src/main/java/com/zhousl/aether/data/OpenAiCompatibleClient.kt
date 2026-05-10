package com.zhousl.aether.data

import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong

class OpenAiCompatibleClient(
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DefaultHttpConnectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(DefaultHttpReadTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(DefaultHttpWriteTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(DefaultHttpCallTimeoutMillis, TimeUnit.MILLISECONDS)
        .build(),
) {
    private val requestCounter = AtomicLong(1)

    suspend fun createChatCompletion(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject> = emptyList(),
        toolChoice: String? = null,
        parallelToolCalls: Boolean? = null,
        disableReasoning: Boolean = false,
    ): Result<ChatCompletionResult> {
        val requestId = nextLlmRequestId()
        diagnosticLogger.event(
            category = "llm",
            event = "request_start",
            requestId = requestId,
            details = llmDiagnosticDetails(settings) + mapOf(
                "streaming" to false,
                "conversation_message_count" to conversation.size,
                "tool_count" to tools.size,
                "tool_choice" to toolChoice.orEmpty(),
                "parallel_tool_calls" to parallelToolCalls,
            ),
        )
        return try {
            val request = when (settings.provider) {
                LlmProvider.OpenAiResponses -> buildOpenAiResponsesRequest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    parallelToolCalls = parallelToolCalls,
                    disableReasoning = disableReasoning,
            )

                LlmProvider.OpenAiCompatible -> buildOpenAiRequest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    parallelToolCalls = parallelToolCalls,
                    disableReasoning = disableReasoning,
            )

                LlmProvider.VertexExpress -> buildVertexRequest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
            )

                LlmProvider.AnthropicMessages -> buildAnthropicMessagesRequest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
            )
            }

            val responsePayload = executeRequest(request)
            val json = parseJsonObject(responsePayload.bodyString)

            if (!responsePayload.isSuccessful) {
                val errorMessage = extractLlmErrorMessage(json, responsePayload)
                throw buildLlmRequestException(responsePayload, errorMessage)
            }

            if (json == null) {
                error(buildUnexpectedResponseMessage(responsePayload))
            }

            val result = when (settings.provider) {
                LlmProvider.OpenAiResponses -> parseOpenAiResponses(json)
                LlmProvider.OpenAiCompatible -> parseOpenAiChatCompletion(json)
                LlmProvider.VertexExpress -> parseVertexGenerateContent(json)
                LlmProvider.AnthropicMessages -> parseAnthropicMessage(json)
            }
            diagnosticLogger.event(
                category = "llm",
                event = "request_end",
                requestId = requestId,
                details = mapOf(
                    "http_code" to responsePayload.code,
                    "content_type" to responsePayload.contentType,
                    "request_url" to responsePayload.requestUrl,
                    "assistant_text_chars" to result.assistantText.length,
                    "tool_call_count" to result.toolCalls.size,
                ),
            )
            Result.success(result)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "llm",
                event = "request_failed",
                requestId = requestId,
                throwable = throwable,
                details = llmDiagnosticDetails(settings) + mapOf("streaming" to false),
            )
            Result.failure(throwable)
        }
    }

    suspend fun streamChatCompletion(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject> = emptyList(),
        toolChoice: String? = null,
        parallelToolCalls: Boolean? = null,
        onTextDelta: suspend (String) -> Unit = {},
        onReasoningDelta: suspend (String) -> Unit = {},
        onReasoningSummaryDelta: suspend (String) -> Unit = {},
        onStreamActivity: suspend () -> Unit = {},
    ): Result<ChatCompletionResult> {
        val requestId = nextLlmRequestId()
        diagnosticLogger.event(
            category = "llm",
            event = "request_start",
            requestId = requestId,
            details = llmDiagnosticDetails(settings) + mapOf(
                "streaming" to true,
                "conversation_message_count" to conversation.size,
                "tool_count" to tools.size,
                "tool_choice" to toolChoice.orEmpty(),
                "parallel_tool_calls" to parallelToolCalls,
            ),
        )
        return try {
            val result = when (settings.provider) {
                LlmProvider.OpenAiResponses -> streamOpenAiResponses(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    parallelToolCalls = parallelToolCalls,
                    onTextDelta = onTextDelta,
                    onStreamActivity = onStreamActivity,
                )

                LlmProvider.OpenAiCompatible -> streamOpenAiChatCompletion(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    parallelToolCalls = parallelToolCalls,
                    onTextDelta = onTextDelta,
                    onReasoningDelta = onReasoningDelta,
                    onReasoningSummaryDelta = onReasoningSummaryDelta,
                    onStreamActivity = onStreamActivity,
                )

                LlmProvider.VertexExpress -> streamVertexGenerateContent(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    onTextDelta = onTextDelta,
                    onStreamActivity = onStreamActivity,
                )

                LlmProvider.AnthropicMessages -> streamAnthropicMessages(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = tools,
                    toolChoice = toolChoice,
                    onTextDelta = onTextDelta,
                    onStreamActivity = onStreamActivity,
                )
            }
            diagnosticLogger.event(
                category = "llm",
                event = "request_end",
                requestId = requestId,
                details = mapOf(
                    "streaming" to true,
                    "assistant_text_chars" to result.assistantText.length,
                    "tool_call_count" to result.toolCalls.size,
                ),
            )
            Result.success(result)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "llm",
                event = "request_failed",
                requestId = requestId,
                throwable = throwable,
                details = llmDiagnosticDetails(settings) + mapOf("streaming" to true),
            )
            Result.failure(throwable)
        }
    }

    fun buildConversation(
        settings: AppSettings,
        messages: List<LlmMessage>,
    ): List<JSONObject> = messages.map { message ->
        when (settings.provider) {
            LlmProvider.OpenAiResponses -> serializeOpenAiResponsesConversationMessage(message)
            LlmProvider.OpenAiCompatible -> serializeOpenAiConversationMessage(message)
            LlmProvider.VertexExpress -> serializeVertexConversationMessage(message)
            LlmProvider.AnthropicMessages -> serializeAnthropicConversationMessage(message)
        }
    }

    fun buildToolResultMessage(
        settings: AppSettings,
        callId: String,
        name: String,
        output: String,
    ): JSONObject = when (settings.provider) {
        LlmProvider.OpenAiResponses -> JSONObject().apply {
            put("type", "function_call_output")
            put("call_id", callId)
            put("output", output)
        }

        LlmProvider.OpenAiCompatible -> JSONObject().apply {
            put("role", "tool")
            put("tool_call_id", callId)
            put("name", name)
            put("content", output)
        }

        LlmProvider.VertexExpress -> JSONObject().apply {
            put("role", "user")
            put(
                "parts",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "functionResponse",
                            JSONObject().apply {
                                put("name", name)
                                put(
                                    "response",
                                    JSONObject().apply {
                                        put("output", parseJsonValue(output))
                                    }
                                )
                            }
                        )
                    }
                )
            )
        }

        LlmProvider.AnthropicMessages -> JSONObject().apply {
            put("role", "user")
            put(
                "content",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "tool_result")
                        put("tool_use_id", callId)
                        put("content", output)
                    }
                )
            )
        }
    }

    fun buildToolResultMessages(
        settings: AppSettings,
        results: List<ChatCompletionToolResult>,
    ): List<JSONObject> = when (settings.provider) {
        LlmProvider.OpenAiResponses,
        LlmProvider.OpenAiCompatible -> results.map { result ->
            buildToolResultMessage(
                settings = settings,
                callId = result.callId,
                name = result.name,
                output = result.output,
            )
        }

        LlmProvider.VertexExpress -> listOf(
            JSONObject().apply {
                put("role", "user")
                put(
                    "parts",
                    JSONArray().apply {
                        results.forEach { result ->
                            put(
                                JSONObject().apply {
                                    put(
                                        "functionResponse",
                                        JSONObject().apply {
                                            put("name", result.name)
                                            put(
                                                "response",
                                                JSONObject().apply {
                                                    put("output", parseJsonValue(result.output))
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        )

        LlmProvider.AnthropicMessages -> listOf(
            JSONObject().apply {
                put("role", "user")
                put(
                    "content",
                    JSONArray().apply {
                        results.forEach { result ->
                            put(
                                JSONObject().apply {
                                    put("type", "tool_result")
                                    put("tool_use_id", result.callId)
                                    put("content", result.output)
                                }
                            )
                        }
                    }
                )
            }
        )
    }

    private fun nextLlmRequestId(): String = "llm-${requestCounter.getAndIncrement()}"

    private fun llmDiagnosticDetails(settings: AppSettings): Map<String, Any?> =
        mapOf(
            "provider" to settings.provider.storageValue,
            "model" to settings.modelId,
            "base_url" to DiagnosticRedactor.sanitizedBaseUrl(settings.baseUrl),
            "inactivity_reconnect_timeout_seconds" to settings.llmInactivityReconnectTimeoutSeconds,
            "basic_tool_compatibility" to settings.basicFunctionCallingCompatibilityMode,
        )

    private fun parseOpenAiChatCompletion(json: JSONObject): ChatCompletionResult {
        val message = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: error("Missing assistant message in response.")

        val assistantText = when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val item = content.opt(index)
                    if (item is JSONObject) {
                        append(
                            item.optString("text")
                                .ifBlank { item.optString("content") }
                        )
                    } else if (item is String) {
                        append(item)
                    }
                }
            }

            else -> ""
        }

        val toolCalls = buildList {
            val toolCallsArray = message.optJSONArray("tool_calls") ?: JSONArray()
            for (index in 0 until toolCallsArray.length()) {
                val toolCall = toolCallsArray.optJSONObject(index) ?: continue
                val function = toolCall.optJSONObject("function") ?: continue
                add(
                    ChatCompletionToolCall(
                        id = toolCall.optString("id"),
                        name = function.optString("name"),
                        arguments = function.optString("arguments"),
                    )
                )
            }
        }

        return ChatCompletionResult(
            assistantText = assistantText,
            toolCalls = toolCalls,
            assistantMessage = JSONObject(message.toString()),
            reasoningText = extractOpenAiReasoningText(message),
            reasoningSummaryText = extractReasoningDetailsSummary(message.optJSONArray("reasoning_details")),
        )
    }

    private fun parseOpenAiResponses(json: JSONObject): ChatCompletionResult {
        val output = json.optJSONArray("output") ?: JSONArray()
        return buildOpenAiResponsesResult(output)
    }

    private fun parseAnthropicMessage(json: JSONObject): ChatCompletionResult {
        val content = json.optJSONArray("content") ?: JSONArray()
        val assistantText = buildString {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") != "text") continue
                val text = block.optString("text")
                if (text.isBlank()) continue
                if (isNotEmpty()) append('\n')
                append(text)
            }
        }
        val toolCalls = buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") != "tool_use") continue
                add(
                    ChatCompletionToolCall(
                        id = block.optString("id").ifBlank { generatedToolCallId("anthropic", index) },
                        name = block.optString("name"),
                        arguments = jsonValueToString(block.opt("input")),
                    )
                )
            }
        }

        return ChatCompletionResult(
            assistantText = assistantText,
            toolCalls = toolCalls,
            assistantMessage = JSONObject().apply {
                put("role", "assistant")
                put("content", JSONArray(content.toString()))
            },
        )
    }

    private fun parseVertexGenerateContent(json: JSONObject): ChatCompletionResult {
        val candidate = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?: error("Missing candidate in response.")
        val content = candidate.optJSONObject("content")
            ?: error("Missing model content in response.")
        val assistantMessage = sanitizeVertexConversationMessage(content).apply {
            if (optString("role").isBlank()) {
                put("role", "model")
            }
        }
        val parts = assistantMessage.optJSONArray("parts") ?: JSONArray()
        val assistantText = buildString {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val text = part.optString("text")
                if (text.isBlank()) continue
                if (isNotEmpty()) append('\n')
                append(text)
            }
        }
        val toolCalls = buildList {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val functionCall = part.optJSONObject("functionCall") ?: continue
                add(
                    ChatCompletionToolCall(
                        id = functionCall.optString("id").trim().ifBlank {
                            generatedToolCallId("vertex", index)
                        },
                        name = functionCall.optString("name"),
                        arguments = jsonValueToString(functionCall.opt("args")),
                    )
                )
            }
        }

        return ChatCompletionResult(
            assistantText = assistantText,
            toolCalls = toolCalls,
            assistantMessage = assistantMessage,
        )
    }

    private fun serializeOpenAiConversationMessage(message: LlmMessage): JSONObject {
        val onlyTextPart = message.contentParts.singleOrNull() as? LlmTextPart
        return JSONObject().apply {
            put("role", message.role)
            if (onlyTextPart != null) {
                put("content", onlyTextPart.text)
            } else {
                put(
                    "content",
                    JSONArray().apply {
                        message.contentParts.forEach { part -> put(serializeOpenAiContentPart(part)) }
                    }
                )
            }
        }
    }

    private fun serializeOpenAiResponsesConversationMessage(message: LlmMessage): JSONObject = JSONObject().apply {
        put("role", message.role)
        put(
            "content",
            JSONArray().apply {
                message.contentParts.forEach { part -> put(serializeOpenAiResponsesContentPart(part, message.role)) }
            }
        )
    }

    private fun serializeAnthropicConversationMessage(message: LlmMessage): JSONObject = JSONObject().apply {
        put(
            "role",
            when (message.role) {
                "assistant" -> "assistant"
                else -> "user"
            }
        )
        put(
            "content",
            JSONArray().apply {
                message.contentParts.forEach { part -> put(serializeAnthropicContentPart(part)) }
            }
        )
    }

    private fun serializeVertexConversationMessage(message: LlmMessage): JSONObject = JSONObject().apply {
        put(
            "role",
            when (message.role) {
                "assistant" -> "model"
                else -> "user"
            }
        )
        put(
            "parts",
            JSONArray().apply {
                message.contentParts.forEach { part -> put(serializeVertexContentPart(part)) }
            }
        )
    }

    private fun serializeOpenAiContentPart(part: LlmContentPart): JSONObject = when (part) {
        is LlmTextPart -> JSONObject().apply {
            put("type", "text")
            put("text", part.text)
        }

        is LlmImagePart -> JSONObject().apply {
            put("type", "image_url")
            put(
                "image_url",
                JSONObject().apply {
                    put("url", "data:${part.mimeType};base64,${part.base64Data}")
                }
            )
        }
    }

    private fun serializeOpenAiResponsesContentPart(
        part: LlmContentPart,
        role: String,
    ): JSONObject = when (part) {
        is LlmTextPart -> JSONObject().apply {
            put("type", if (role == "assistant") "output_text" else "input_text")
            put("text", part.text)
        }

        is LlmImagePart -> JSONObject().apply {
            put("type", "input_image")
            put("image_url", "data:${part.mimeType};base64,${part.base64Data}")
        }
    }

    private fun serializeAnthropicContentPart(part: LlmContentPart): JSONObject = when (part) {
        is LlmTextPart -> JSONObject().apply {
            put("type", "text")
            put("text", part.text)
        }

        is LlmImagePart -> JSONObject().apply {
            put("type", "image")
            put(
                "source",
                JSONObject().apply {
                    put("type", "base64")
                    put("media_type", part.mimeType)
                    put("data", part.base64Data)
                }
            )
        }
    }

    private fun serializeVertexContentPart(part: LlmContentPart): JSONObject = when (part) {
        is LlmTextPart -> JSONObject().apply {
            put("text", part.text)
        }

        is LlmImagePart -> JSONObject().apply {
            put(
                "inlineData",
                JSONObject().apply {
                    put("mimeType", part.mimeType)
                    put("data", part.base64Data)
                }
            )
        }
    }

    private suspend fun streamOpenAiResponses(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCalls: Boolean?,
        onTextDelta: suspend (String) -> Unit,
        onStreamActivity: suspend () -> Unit,
    ): ChatCompletionResult {
        val accumulator = OpenAiResponsesStreamAccumulator(onTextDelta)
        return executeStreamingRequest(
            request = buildOpenAiResponsesRequest(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                parallelToolCalls = parallelToolCalls,
                stream = true,
            ),
            parseJsonResponse = ::parseOpenAiResponses,
            consumeSseChunk = accumulator::consume,
            buildStreamResult = accumulator::buildResult,
            inactivityTimeoutSeconds = settings.llmInactivityReconnectTimeoutSeconds,
            onStreamActivity = onStreamActivity,
            isTerminalEventData = { eventData -> eventData == "[DONE]" },
            isTerminalChunk = ::openAiResponsesChunkSignalsCompletion,
        )
    }

    private suspend fun streamOpenAiChatCompletion(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCalls: Boolean?,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
        onReasoningSummaryDelta: suspend (String) -> Unit,
        onStreamActivity: suspend () -> Unit,
    ): ChatCompletionResult {
        val accumulator = OpenAiStreamAccumulator(
            onTextDelta = onTextDelta,
            onReasoningDelta = onReasoningDelta,
            onReasoningSummaryDelta = onReasoningSummaryDelta,
            includeEmptyReasoningContentForToolCalls = shouldIncludeEmptyReasoningContentForToolCalls(settings),
        )
        return executeStreamingRequest(
            request = buildOpenAiRequest(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                parallelToolCalls = parallelToolCalls,
                stream = true,
            ),
            parseJsonResponse = ::parseOpenAiChatCompletion,
            consumeSseChunk = accumulator::consume,
            buildStreamResult = accumulator::buildResult,
            inactivityTimeoutSeconds = settings.llmInactivityReconnectTimeoutSeconds,
            onStreamActivity = onStreamActivity,
            isTerminalEventData = { eventData -> eventData == "[DONE]" },
        )
    }

    private suspend fun streamAnthropicMessages(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        onTextDelta: suspend (String) -> Unit,
        onStreamActivity: suspend () -> Unit,
    ): ChatCompletionResult {
        val accumulator = AnthropicStreamAccumulator(onTextDelta)
        return executeStreamingRequest(
            request = buildAnthropicMessagesRequest(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                stream = true,
            ),
            parseJsonResponse = ::parseAnthropicMessage,
            consumeSseChunk = accumulator::consume,
            buildStreamResult = accumulator::buildResult,
            inactivityTimeoutSeconds = settings.llmInactivityReconnectTimeoutSeconds,
            onStreamActivity = onStreamActivity,
            isTerminalChunk = ::anthropicChunkSignalsCompletion,
        )
    }

    private suspend fun streamVertexGenerateContent(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        onTextDelta: suspend (String) -> Unit,
        onStreamActivity: suspend () -> Unit,
    ): ChatCompletionResult {
        val accumulator = VertexStreamAccumulator(onTextDelta)
        return executeStreamingRequest(
            request = buildVertexRequest(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                stream = true,
            ),
            parseJsonResponse = ::parseVertexGenerateContent,
            consumeSseChunk = accumulator::consume,
            buildStreamResult = accumulator::buildResult,
            inactivityTimeoutSeconds = settings.llmInactivityReconnectTimeoutSeconds,
            onStreamActivity = onStreamActivity,
            isTerminalChunk = ::vertexChunkSignalsCompletion,
        )
    }

    private fun buildOpenAiResponsesRequest(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCalls: Boolean?,
        stream: Boolean = false,
        disableReasoning: Boolean = false,
    ): Request {
        val endpoint = buildOpenAiResponsesEndpoint(settings.baseUrl)
        val trimmedApiKey = settings.apiKey.trim()
        if (settings.provider.requiresApiKey(settings.baseUrl) && trimmedApiKey.isBlank()) {
            error("API Key is required for ${settings.provider.displayName}.")
        }
        val payload = JSONObject().apply {
            put("model", settings.modelId.trim())
            put("store", false)
            if (systemPrompt.isNotBlank()) {
                put("instructions", systemPrompt)
            }
            put(
                "input",
                JSONArray().apply {
                    conversation.forEach { item ->
                        appendOpenAiResponsesInputItem(item)
                    }
                }
            )
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    JSONArray().apply {
                        tools.forEach {
                            put(
                                convertOpenAiToolToResponsesTool(
                                    tool = it,
                                    includeStrict = !settings.basicFunctionCallingCompatibilityMode,
                                )
                            )
                        }
                    }
                )
                put("tool_choice", if (settings.basicFunctionCallingCompatibilityMode) "auto" else toolChoice ?: "auto")
                if (!settings.basicFunctionCallingCompatibilityMode) {
                    parallelToolCalls?.let { put("parallel_tool_calls", it) }
                }
            }
            if (stream) {
                put("stream", true)
            }
            putReasoningDisabledIfNeeded(settings, disableReasoning)
        }

        return Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (stream) {
                    addHeader("Accept", "text/event-stream")
                }
            }
            .apply {
                if (trimmedApiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $trimmedApiKey")
                }
            }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
    }

    private fun buildOpenAiRequest(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCalls: Boolean?,
        stream: Boolean = false,
        disableReasoning: Boolean = false,
    ): Request {
        val endpoint = buildOpenAiChatCompletionEndpoint(settings.baseUrl)
        val trimmedApiKey = settings.apiKey.trim()
        if (settings.provider.requiresApiKey(settings.baseUrl) && trimmedApiKey.isBlank()) {
            error("API Key is required for ${settings.provider.displayName}.")
        }
        val payload = JSONObject().apply {
            put("model", settings.modelId.trim())
            put(
                "messages",
                JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            }
                        )
                    }
                    conversation.forEach(::put)
                }
            )
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    JSONArray().apply {
                        tools.forEach { tool ->
                            put(
                                if (settings.basicFunctionCallingCompatibilityMode) {
                                    convertOpenAiToolToBasicFunctionTool(tool)
                                } else {
                                    tool
                                }
                            )
                        }
                    }
                )
                put("tool_choice", if (settings.basicFunctionCallingCompatibilityMode) "auto" else toolChoice ?: "auto")
                if (!settings.basicFunctionCallingCompatibilityMode) {
                    parallelToolCalls?.let { put("parallel_tool_calls", it) }
                }
            }
            if (stream) {
                put("stream", true)
            }
            putReasoningDisabledIfNeeded(settings, disableReasoning)
        }

        return Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (stream) {
                    addHeader("Accept", "text/event-stream")
                }
            }
            .apply {
                if (trimmedApiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $trimmedApiKey")
                }
            }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
    }

    private fun buildAnthropicMessagesRequest(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        stream: Boolean = false,
    ): Request {
        val endpoint = buildAnthropicMessagesEndpoint(settings.baseUrl)
        val trimmedApiKey = settings.apiKey.trim()
        if (trimmedApiKey.isBlank()) {
            error("API Key is required for Anthropic Messages API.")
        }
        val payload = JSONObject().apply {
            put("model", settings.modelId.trim())
            put("max_tokens", DefaultAnthropicMaxTokens)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put(
                "messages",
                JSONArray().apply {
                    conversation.forEach(::put)
                }
            )
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply { tools.forEach { put(convertOpenAiToolToAnthropicTool(it)) } })
                put(
                    "tool_choice",
                    JSONObject().apply {
                        put(
                            "type",
                            when (toolChoice) {
                                "required" -> "any"
                                else -> "auto"
                            }
                        )
                    }
                )
            }
            if (stream) {
                put("stream", true)
            }
        }

        return Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", if (stream) "text/event-stream" else "application/json")
            .addHeader("x-api-key", trimmedApiKey)
            .addHeader("anthropic-version", AnthropicVersion)
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
    }

    private fun buildVertexRequest(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        stream: Boolean = false,
    ): Request {
        val endpoint = buildVertexEndpoint(
            baseUrl = settings.baseUrl,
            modelId = settings.modelId,
            apiKey = settings.apiKey,
            methodName = if (stream) "streamGenerateContent" else "generateContent",
            includeSseAlt = stream,
        )
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    conversation.forEach { message ->
                        put(sanitizeVertexConversationMessage(message))
                    }
                }
            )
            if (systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("text", systemPrompt)
                                }
                            )
                        )
                    }
                )
            }
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    JSONArray().put(
                        JSONObject().apply {
                            put(
                                "functionDeclarations",
                                JSONArray().apply {
                                    tools.forEach { tool ->
                                        put(convertOpenAiToolToVertexFunctionDeclaration(tool))
                                    }
                                }
                            )
                        }
                    )
                )
                put(
                    "toolConfig",
                    JSONObject().apply {
                        put(
                            "functionCallingConfig",
                            JSONObject().apply {
                                put(
                                    "mode",
                                    when (toolChoice) {
                                        "required" -> "ANY"
                                        else -> "AUTO"
                                    }
                                )
                            }
                        )
                    }
                )
            }
        }

        return Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (stream) {
                    addHeader("Accept", "text/event-stream")
                }
            }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
    }

    private fun buildOpenAiResponsesEndpoint(baseUrl: String): String {
        val normalizedBaseUrl = parseAbsoluteBaseUrl(baseUrl)
        val pathSegments = normalizedBaseUrl.pathSegments.filter { it.isNotBlank() }
        val builder = normalizedBaseUrl.newBuilder()
            .query(null)
            .fragment(null)

        if (pathSegments.lastOrNull() == "responses") {
            return builder.build().toString()
        }

        if (pathSegments.takeLast(2) == listOf("chat", "completions")) {
            builder.removePathSegment(builder.build().pathSize - 1)
            builder.removePathSegment(builder.build().pathSize - 1)
        }

        return builder
            .addPathSegment("responses")
            .build()
            .toString()
    }

    private fun buildOpenAiChatCompletionEndpoint(baseUrl: String): String {
        val normalizedBaseUrl = parseAbsoluteBaseUrl(baseUrl)
        val pathSegments = normalizedBaseUrl.pathSegments.filter { it.isNotBlank() }
        val builder = normalizedBaseUrl.newBuilder()
            .query(null)
            .fragment(null)

        if (pathSegments.takeLast(2) == listOf("chat", "completions")) {
            return builder.build().toString()
        }

        return builder
            .addPathSegments("chat/completions")
            .build()
            .toString()
    }

    private fun buildAnthropicMessagesEndpoint(baseUrl: String): String {
        val normalizedBaseUrl = parseAbsoluteBaseUrl(baseUrl)
        val pathSegments = normalizedBaseUrl.pathSegments.filter { it.isNotBlank() }
        val builder = normalizedBaseUrl.newBuilder()
            .query(null)
            .fragment(null)

        if (pathSegments.lastOrNull() == "messages") {
            return builder.build().toString()
        }

        return builder
            .addPathSegment("messages")
            .build()
            .toString()
    }

    private fun parseAbsoluteBaseUrl(baseUrl: String): HttpUrl =
        baseUrl.trim()
            .ifBlank { error("Base URL is required.") }
            .toHttpUrlOrNull()
            ?: error("Base URL is not a valid absolute URL.")

    private fun buildVertexEndpoint(
        baseUrl: String,
        modelId: String,
        apiKey: String,
        methodName: String,
        includeSseAlt: Boolean = false,
    ): String {
        val trimmedApiKey = apiKey.trim()
        if (trimmedApiKey.isBlank()) {
            error("API Key is required for Vertex AI (Express Mode).")
        }

        val normalizedBaseUrl = baseUrl.trim()
            .ifBlank { error("Base URL is required.") }
            .toHttpUrlOrNull()
            ?: error("Base URL is not a valid absolute URL.")

        val normalizedModelPath = normalizeVertexModelPath(modelId)
        val currentPath = normalizedBaseUrl.encodedPath
            .trimEnd('/')
            .ifBlank { "/" }
        val normalizedCurrentPath = currentPath
            .removeSuffix(":generateContent")
            .removeSuffix(":streamGenerateContent")
        val targetPath = when {
            currentPath.endsWith(":generateContent") ||
                currentPath.endsWith(":streamGenerateContent") -> {
                "$normalizedCurrentPath:$methodName"
            }

            currentPath.contains("/publishers/google/models/") -> "$currentPath:$methodName"
            currentPath.endsWith("/publishers/google/models") -> {
                "$currentPath/${normalizedModelPath.substringAfterLast('/')}:$methodName"
            }

            else -> "$currentPath/$normalizedModelPath:$methodName"
        }

        return normalizedBaseUrl.newBuilder().apply {
            encodedPath(targetPath)
            query(null)
            fragment(null)
            addQueryParameter("key", trimmedApiKey)
            if (includeSseAlt) {
                addQueryParameter("alt", "sse")
            }
        }.build().toString()
    }

    private fun normalizeVertexModelPath(modelId: String): String {
        val trimmedModelId = modelId.trim().ifBlank { error("Model ID is required.") }
        val withoutMethodSuffix = trimmedModelId
            .substringBefore(":generateContent")
            .substringBefore(":streamGenerateContent")
        return when {
            withoutMethodSuffix.startsWith("publishers/") -> withoutMethodSuffix
            withoutMethodSuffix.startsWith("models/") -> "publishers/google/$withoutMethodSuffix"
            withoutMethodSuffix.startsWith("google/") -> {
                "publishers/google/models/${withoutMethodSuffix.substringAfter('/')}"
            }

            else -> "publishers/google/models/$withoutMethodSuffix"
        }
    }

    private fun convertOpenAiToolToVertexFunctionDeclaration(tool: JSONObject): JSONObject {
        val function = tool.optJSONObject("function")
            ?: error("Tool definition is missing its function payload.")
        return JSONObject().apply {
            put("name", function.optString("name"))
            put("description", function.optString("description"))
            function.optJSONObject("parameters")?.let { parameters ->
                put("parametersJsonSchema", JSONObject(parameters.toString()))
            }
        }
    }

    private fun convertOpenAiToolToBasicFunctionTool(tool: JSONObject): JSONObject {
        val function = tool.optJSONObject("function")
            ?: error("Tool definition is missing its function payload.")
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", function.optString("name"))
                    put("description", function.optString("description"))
                    put(
                        "parameters",
                        function.optJSONObject("parameters")?.let { JSONObject(it.toString()) } ?: emptyObjectSchema()
                    )
                }
            )
        }
    }

    private fun convertOpenAiToolToResponsesTool(
        tool: JSONObject,
        includeStrict: Boolean = true,
    ): JSONObject {
        val function = tool.optJSONObject("function")
            ?: error("Tool definition is missing its function payload.")
        return JSONObject().apply {
            put("type", "function")
            put("name", function.optString("name"))
            put("description", function.optString("description"))
            put(
                "parameters",
                function.optJSONObject("parameters")?.let { JSONObject(it.toString()) } ?: emptyObjectSchema()
            )
            if (includeStrict && function.has("strict")) {
                put("strict", function.optBoolean("strict"))
            }
        }
    }

    private fun convertOpenAiToolToAnthropicTool(tool: JSONObject): JSONObject {
        val function = tool.optJSONObject("function")
            ?: error("Tool definition is missing its function payload.")
        return JSONObject().apply {
            put("name", function.optString("name"))
            put("description", function.optString("description"))
            put(
                "input_schema",
                function.optJSONObject("parameters")?.let { JSONObject(it.toString()) } ?: emptyObjectSchema()
            )
        }
    }

    private fun JSONArray.appendOpenAiResponsesInputItem(item: JSONObject) {
        val wrappedItems = item.optJSONArray(OpenAiResponsesItemsKey)
        if (wrappedItems != null) {
            for (index in 0 until wrappedItems.length()) {
                wrappedItems.optJSONObject(index)?.let(::put)
            }
            return
        }
        put(item)
    }

    private fun emptyObjectSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
    }

    private fun parseJsonObject(bodyString: String): JSONObject? {
        val trimmed = bodyString.trimStart()
        if (trimmed.isBlank()) {
            return JSONObject()
        }
        if (!trimmed.startsWith("{")) {
            return null
        }

        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun parseJsonValue(rawValue: String): Any = runCatching {
        JSONTokener(rawValue).nextValue()
    }.getOrElse {
        rawValue
    }

    private fun jsonValueToString(value: Any?): String = when (value) {
        null -> "{}"
        JSONObject.NULL -> "null"
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        else -> JSONObject.wrap(value)?.toString() ?: value.toString()
    }

    private fun buildUnexpectedResponseMessage(responsePayload: HttpResponsePayload): String {
        val responseType = when {
            responsePayload.contentType.contains("html", ignoreCase = true) -> "HTML"
            responsePayload.bodyString.trimStart().startsWith("<") -> "HTML"
            responsePayload.contentType.isBlank() -> "non-JSON"
            else -> responsePayload.contentType
        }
        val preview = responsePayload.bodyString
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(160)
        val hint = when {
            responsePayload.code in 500..599 -> {
                "The upstream provider or gateway failed before returning JSON. Retry later or switch to a healthier endpoint."
            }

            responsePayload.code in 300..399 -> {
                "The request was redirected, which usually means Base URL points at a website instead of the API endpoint."
            }

            responsePayload.code == 404 -> {
                "The endpoint was not found. Check Base URL. Use either a /v1 base URL or a full endpoint for the selected provider."
            }

            else -> {
                "Check Base URL. Use either a /v1 base URL or a full endpoint for the selected provider."
            }
        }
        val details = if (preview.isBlank()) {
            ""
        } else {
            " Response preview: $preview"
        }

        return "HTTP ${responsePayload.code}: server returned $responseType instead of JSON from ${responsePayload.requestUrl}. " +
            "$hint$details"
    }

    private fun extractLlmErrorMessage(
        json: JSONObject?,
        responsePayload: HttpResponsePayload,
    ): String {
        if (json == null) return buildUnexpectedResponseMessage(responsePayload)

        json.optJSONObject("error")?.let { error ->
            error.optString("message").trim().ifBlank { null }?.let { return it }
            val type = error.optString("type").trim()
            val code = error.opt("code")?.toString()?.trim().orEmpty()
            val param = error.opt("param")?.toString()?.trim().orEmpty()
            return buildString {
                append("HTTP ${responsePayload.code}: upstream error")
                if (type.isNotBlank()) append(" type=$type")
                if (code.isNotBlank() && code != "null") append(" code=$code")
                if (param.isNotBlank() && param != "null") append(" param=$param")
                append(responsePreviewSuffix(responsePayload))
            }
        }

        json.optString("message").trim().ifBlank { null }?.let { return it }
        json.optString("error").trim().ifBlank { null }?.let { return it }
        json.optString("detail").trim().ifBlank { null }?.let { return it }

        return "HTTP ${responsePayload.code}: upstream returned an error without a message." +
            responsePreviewSuffix(responsePayload)
    }

    private fun responsePreviewSuffix(responsePayload: HttpResponsePayload): String {
        val preview = responsePayload.bodyString
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(240)
        return if (preview.isBlank()) "" else " Response preview: $preview"
    }

    private fun shouldIncludeEmptyReasoningContentForToolCalls(settings: AppSettings): Boolean {
        if (settings.provider != LlmProvider.OpenAiCompatible) return false
        val host = runCatching {
            URI(settings.baseUrl.trim()).host.orEmpty().lowercase()
        }.getOrDefault("")
        val model = settings.modelId.lowercase()
        return "deepseek" in host || "deepseek" in model
    }

    private fun JSONObject.putReasoningDisabledIfNeeded(
        settings: AppSettings,
        disableReasoning: Boolean,
    ) {
        if (!disableReasoning) return
        if (settings.provider != LlmProvider.OpenAiCompatible && settings.provider != LlmProvider.OpenAiResponses) return

        val host = runCatching {
            URI(settings.baseUrl.trim()).host.orEmpty().lowercase()
        }.getOrDefault("")
        val model = settings.modelId.lowercase()
        when {
            "openrouter" in host || "openrouter" in model -> {
                put(
                    "reasoning",
                    JSONObject().apply {
                        put("effort", "none")
                    },
                )
            }

            "deepseek" in host || "deepseek" in model -> {
                put(
                    "thinking",
                    JSONObject().apply {
                        put("type", "disabled")
                    },
                )
            }
        }
    }

    private suspend fun executeStreamingRequest(
        request: Request,
        parseJsonResponse: (JSONObject) -> ChatCompletionResult,
        consumeSseChunk: suspend (JSONObject) -> Unit,
        buildStreamResult: () -> ChatCompletionResult,
        inactivityTimeoutSeconds: Int,
        onStreamActivity: suspend () -> Unit,
        isTerminalEventData: (String) -> Boolean = { false },
        isTerminalChunk: (JSONObject) -> Boolean = { false },
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val timeoutMillis = inactivityTimeoutSeconds.coerceAtLeast(1) * 1000L
        val lastActivityAt = AtomicLong(monotonicTimeMillis())
        val inactivityFailure = AtomicReference<IOException?>(null)
        var streamCompleted = false
        val call = httpClientForStreaming(inactivityTimeoutSeconds).newCall(request)
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        val checkIntervalMillis = timeoutMillis.coerceAtMost(5_000L).coerceAtLeast(250L)
        val watchdogExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "AetherStreamWatchdog").apply { isDaemon = true }
        }
        watchdogExecutor.scheduleAtFixedRate(
            {
                if (monotonicTimeMillis() - lastActivityAt.get() < timeoutMillis) {
                    return@scheduleAtFixedRate
                }
                inactivityFailure.compareAndSet(
                    null,
                    IOException(
                        "LLM inactivity timeout after ${timeoutMillis / 1000} seconds without any response activity."
                    ),
                )
                call.cancel()
            },
            checkIntervalMillis,
            checkIntervalMillis,
            TimeUnit.MILLISECONDS,
        )

        try {
            call.execute().use { response ->
                lastActivityAt.set(monotonicTimeMillis())
                val responseBody = response.body ?: error("Missing response body.")
                val contentType = response.header("Content-Type").orEmpty()
                val requestUrl = sanitizeRequestUrl(response.request.url)

                if (!response.isSuccessful) {
                    val bodyString = responseBody.string()
                    val responsePayload = HttpResponsePayload(
                        code = response.code,
                        isSuccessful = response.isSuccessful,
                        bodyString = bodyString,
                        contentType = contentType,
                        requestUrl = requestUrl,
                        retryAfterHeader = response.header("Retry-After").orEmpty(),
                    )
                    val json = parseJsonObject(bodyString)
                    val errorMessage = extractLlmErrorMessage(json, responsePayload)
                    throw buildLlmRequestException(responsePayload, errorMessage)
                }

                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    val bodyString = responseBody.string()
                    val responsePayload = HttpResponsePayload(
                        code = response.code,
                        isSuccessful = response.isSuccessful,
                        bodyString = bodyString,
                        contentType = contentType,
                        requestUrl = requestUrl,
                        retryAfterHeader = response.header("Retry-After").orEmpty(),
                    )
                    val json = parseJsonObject(bodyString)
                        ?: error(buildUnexpectedResponseMessage(responsePayload))
                    return@withContext parseJsonResponse(json)
                }

                readSseStream(responseBody.source()) { eventData ->
                    val trimmedEventData = eventData.trim()
                    if (trimmedEventData.isBlank()) {
                        return@readSseStream
                    }
                    if (isTerminalEventData(trimmedEventData)) {
                        streamCompleted = true
                        return@readSseStream
                    }

                    lastActivityAt.set(monotonicTimeMillis())
                    onStreamActivity()
                    val json = parseJsonObject(trimmedEventData) ?: return@readSseStream
                    if (isTerminalChunk(json)) {
                        streamCompleted = true
                    }
                    consumeSseChunk(json)
                }

                if (!streamCompleted) {
                    throw IOException("Stream disconnected before completion.")
                }
                buildStreamResult()
            }
        } catch (ioException: IOException) {
            throw inactivityFailure.get() ?: ioException
        } finally {
            watchdogExecutor.shutdownNow()
            cancellationHandle.dispose()
        }
    }

    private fun httpClientForStreaming(
        inactivityTimeoutSeconds: Int,
    ): OkHttpClient {
        val timeoutSeconds = inactivityTimeoutSeconds.coerceAtLeast(1).toLong()
        return httpClient.newBuilder()
            .connectTimeout(DefaultStreamingConnectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(DefaultStreamingWriteTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private suspend fun executeRequest(request: Request): HttpResponsePayload =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isCancelled) return
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.use {
                            val bodyString = it.body?.string().orEmpty()
                            if (continuation.isCancelled) return
                            continuation.resume(
                                HttpResponsePayload(
                                    code = it.code,
                                    isSuccessful = it.isSuccessful,
                                    bodyString = bodyString,
                                    contentType = it.header("Content-Type").orEmpty(),
                                    requestUrl = sanitizeRequestUrl(it.request.url),
                                    retryAfterHeader = it.header("Retry-After").orEmpty(),
                                )
                            )
                        }
                    }
                }
            )
        }

    private suspend fun readSseStream(
        source: BufferedSource,
        onEvent: suspend (String) -> Unit,
    ) {
        val eventData = StringBuilder()

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (eventData.isNotEmpty()) {
                    onEvent(eventData.toString())
                    eventData.setLength(0)
                }
                continue
            }

            if (line.startsWith(":")) continue
            if (!line.startsWith("data:")) continue

            if (eventData.isNotEmpty()) {
                eventData.append('\n')
            }
            eventData.append(line.removePrefix("data:").trimStart())
        }

        if (eventData.isNotEmpty()) {
            onEvent(eventData.toString())
        }
    }

    private fun sanitizeRequestUrl(url: HttpUrl): String =
        url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()

    private companion object {
        val JsonMediaType = "application/json".toMediaType()
    }
}

private fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000L

internal class LlmHttpException(
    val statusCode: Int,
    override val message: String,
    val retryAfterMillis: Long? = null,
) : IOException(message) {
    fun isRetryable(): Boolean =
        statusCode == 408 ||
            statusCode == 409 ||
            statusCode == 425 ||
            statusCode == 429 ||
            statusCode in 500..599
}

internal fun preferredRetryDelayMillis(throwable: Throwable): Long? {
    var current: Throwable? = throwable
    while (current != null) {
        if (current is LlmHttpException && current.retryAfterMillis != null) {
            return current.retryAfterMillis
        }
        parseRetryAfterMessageMillis(current.message).let { delayMillis ->
            if (delayMillis != null) return delayMillis
        }
        current = current.cause
    }
    return null
}

private class OpenAiStreamAccumulator(
    private val onTextDelta: suspend (String) -> Unit,
    private val onReasoningDelta: suspend (String) -> Unit,
    private val onReasoningSummaryDelta: suspend (String) -> Unit,
    private val includeEmptyReasoningContentForToolCalls: Boolean,
) {
    private val assistantText = StringBuilder()
    private val reasoningContent = StringBuilder()
    private var sawReasoningContent = false
    private val reasoning = StringBuilder()
    private val reasoningDetailsText = StringBuilder()
    private val reasoningSummary = StringBuilder()
    private val reasoningDetails = mutableListOf<JSONObject>()
    private val toolCalls = linkedMapOf<Int, MutableToolCallAccumulator>()

    suspend fun consume(chunk: JSONObject) {
        val choices = chunk.optJSONArray("choices") ?: return
        for (choiceIndex in 0 until choices.length()) {
            val choice = choices.optJSONObject(choiceIndex) ?: continue
            val delta = choice.optJSONObject("delta")
                ?: choice.optJSONObject("message")
                ?: continue

            val reasoningDelta = extractReasoningDelta(delta)
            if (reasoningDelta.isNotEmpty()) {
                reasoning.append(reasoningDelta)
                onReasoningDelta(reasoningDelta)
            }
            val reasoningContentDelta = delta.optNullableString("reasoning_content")
            if (reasoningContentDelta != null) {
                sawReasoningContent = true
            }
            if (!reasoningContentDelta.isNullOrEmpty()) {
                reasoningContent.append(reasoningContentDelta)
                onReasoningDelta(reasoningContentDelta)
            }
            val reasoningDetailsDelta = delta.optJSONArray("reasoning_details")
            if (reasoningDetailsDelta != null) {
                for (index in 0 until reasoningDetailsDelta.length()) {
                    val detail = reasoningDetailsDelta.optJSONObject(index) ?: continue
                    reasoningDetails += JSONObject(detail.toString())
                }
                val reasoningDetailsSummaryDelta = extractReasoningDetailsSummary(reasoningDetailsDelta)
                if (reasoningDetailsSummaryDelta.isNotEmpty()) {
                    reasoningSummary.append(reasoningDetailsSummaryDelta)
                    onReasoningSummaryDelta(reasoningDetailsSummaryDelta)
                }
                if (reasoningDelta.isEmpty() && reasoningContentDelta.isNullOrEmpty()) {
                    val reasoningDetailsTextDelta = extractReasoningDetailsText(reasoningDetailsDelta)
                    if (reasoningDetailsTextDelta.isNotEmpty()) {
                        reasoningDetailsText.append(reasoningDetailsTextDelta)
                        onReasoningDelta(reasoningDetailsTextDelta)
                    }
                }
            }

            val textDelta = extractTextDelta(delta)
            if (textDelta.isNotEmpty()) {
                assistantText.append(textDelta)
                onTextDelta(textDelta)
            }

            val toolCallDeltas = delta.optJSONArray("tool_calls") ?: continue
            for (toolCallIndex in 0 until toolCallDeltas.length()) {
                val toolCallDelta = toolCallDeltas.optJSONObject(toolCallIndex) ?: continue
                val index = if (toolCallDelta.has("index")) {
                    toolCallDelta.optInt("index")
                } else {
                    toolCallIndex
                }
                val accumulator = toolCalls.getOrPut(index) { MutableToolCallAccumulator() }
                val id = toolCallDelta.optString("id").trim()
                if (id.isNotEmpty()) {
                    accumulator.id = id
                }

                val function = toolCallDelta.optJSONObject("function") ?: continue
                val name = function.optString("name").trim()
                if (name.isNotEmpty()) {
                    accumulator.name = name
                }
                accumulator.arguments.append(function.optString("arguments"))
            }
        }
    }

    fun buildResult(): ChatCompletionResult {
        val resolvedToolCalls = toolCalls.entries
            .sortedBy { it.key }
            .mapIndexed { index, (_, accumulator) ->
                ChatCompletionToolCall(
                    id = accumulator.id.ifBlank { generatedToolCallId("openai", index) },
                    name = accumulator.name,
                    arguments = accumulator.arguments.toString(),
                )
            }
        val resolvedText = assistantText.toString()
        return ChatCompletionResult(
            assistantText = resolvedText,
            toolCalls = resolvedToolCalls,
            assistantMessage = buildOpenAiAssistantMessage(
                assistantText = resolvedText,
                toolCalls = resolvedToolCalls,
                reasoningContent = reasoningContent.toString().takeIf { it.isNotBlank() }
                    ?: if (includeEmptyReasoningContentForToolCalls && sawReasoningContent && resolvedToolCalls.isNotEmpty()) "" else null,
                reasoning = reasoning.toString(),
                reasoningDetails = reasoningDetails,
            ),
            reasoningText = reasoningContent.toString()
                .ifBlank { reasoning.toString() }
                .ifBlank { reasoningDetailsText.toString() }
                .ifBlank { reasoningSummary.toString() },
            reasoningSummaryText = reasoningSummary.toString(),
        )
    }
}

private class OpenAiResponsesStreamAccumulator(
    private val onTextDelta: suspend (String) -> Unit,
) {
    private val assistantText = StringBuilder()
    private val messageContent = mutableListOf<JSONObject>()
    private val toolCalls = linkedMapOf<Int, MutableToolCallAccumulator>()
    private val completedFunctionCalls = linkedMapOf<Int, JSONObject>()

    suspend fun consume(chunk: JSONObject) {
        when (chunk.optString("type")) {
            "response.output_text.delta" -> {
                val delta = chunk.optString("delta")
                if (delta.isNotEmpty()) {
                    assistantText.append(delta)
                    onTextDelta(delta)
                }
            }

            "response.output_item.added" -> {
                consumeOutputItem(
                    index = chunk.optInt("output_index", completedFunctionCalls.size),
                    item = chunk.optJSONObject("item"),
                    overwriteArguments = false,
                )
            }

            "response.output_item.done" -> {
                consumeOutputItem(
                    index = chunk.optInt("output_index", completedFunctionCalls.size),
                    item = chunk.optJSONObject("item"),
                    overwriteArguments = true,
                )
            }

            "response.function_call_arguments.delta" -> {
                val index = chunk.optInt("output_index", toolCalls.size)
                val accumulator = toolCalls.getOrPut(index) { MutableToolCallAccumulator() }
                accumulator.arguments.append(chunk.optString("delta"))
            }

            "response.function_call_arguments.done" -> {
                val index = chunk.optInt("output_index", toolCalls.size)
                val arguments = chunk.optString("arguments")
                if (arguments.isNotBlank()) {
                    val accumulator = toolCalls.getOrPut(index) { MutableToolCallAccumulator() }
                    accumulator.arguments.setLength(0)
                    accumulator.arguments.append(arguments)
                }
            }
        }
    }

    fun buildResult(): ChatCompletionResult {
        val resolvedMessageContent = if (assistantText.isNotBlank()) {
            listOf(
                JSONObject().apply {
                    put("type", "output_text")
                    put("text", assistantText.toString())
                }
            )
        } else {
            messageContent
        }
        val responseItems = JSONArray().apply {
            if (resolvedMessageContent.isNotEmpty()) {
                put(
                    JSONObject().apply {
                        put("type", "message")
                        put("role", "assistant")
                        put("content", JSONArray().apply { resolvedMessageContent.forEach(::put) })
                    }
                )
            }
            completedFunctionCalls.toSortedMap().values.forEach(::put)
        }
        return buildOpenAiResponsesResult(responseItems)
    }

    private fun consumeOutputItem(
        index: Int,
        item: JSONObject?,
        overwriteArguments: Boolean,
    ) {
        if (item == null) return
        when (item.optString("type")) {
            "message" -> {
                val content = item.optJSONArray("content") ?: return
                for (contentIndex in 0 until content.length()) {
                    val block = content.optJSONObject(contentIndex) ?: continue
                    if (block.optString("type") == "output_text" && block.optString("text").isNotBlank()) {
                        messageContent += JSONObject(block.toString())
                    }
                }
            }

            "function_call" -> {
                val accumulator = toolCalls.getOrPut(index) { MutableToolCallAccumulator() }
                item.optString("call_id").trim().ifBlank { item.optString("id").trim() }.let { id ->
                    if (id.isNotBlank()) accumulator.id = id
                }
                item.optString("name").trim().let { name ->
                    if (name.isNotBlank()) accumulator.name = name
                }
                val arguments = item.optString("arguments")
                if (arguments.isNotBlank()) {
                    if (overwriteArguments) accumulator.arguments.setLength(0)
                    accumulator.arguments.append(arguments)
                }
                completedFunctionCalls[index] = JSONObject(item.toString()).apply {
                    if (!has("call_id") && accumulator.id.isNotBlank()) {
                        put("call_id", accumulator.id)
                    }
                    if (!has("name") && accumulator.name.isNotBlank()) {
                        put("name", accumulator.name)
                    }
                    if (!has("arguments")) {
                        put("arguments", accumulator.arguments.toString())
                    }
                }
            }
        }
    }
}

private class AnthropicStreamAccumulator(
    private val onTextDelta: suspend (String) -> Unit,
) {
    private val assistantText = StringBuilder()
    private val contentBlocks = mutableListOf<JSONObject>()
    private val toolUseBlocks = linkedMapOf<Int, MutableAnthropicToolUseAccumulator>()

    suspend fun consume(chunk: JSONObject) {
        when (chunk.optString("type")) {
            "content_block_start" -> {
                val index = chunk.optInt("index", contentBlocks.size)
                val block = chunk.optJSONObject("content_block") ?: return
                ensureContentBlock(index, block)
                if (block.optString("type") == "tool_use") {
                    val accumulator = toolUseBlocks.getOrPut(index) { MutableAnthropicToolUseAccumulator() }
                    accumulator.id = block.optString("id")
                    accumulator.name = block.optString("name")
                    block.optJSONObject("input")?.let { input ->
                        accumulator.input.setLength(0)
                        accumulator.input.append(input.toString())
                    }
                }
            }

            "content_block_delta" -> {
                val index = chunk.optInt("index", contentBlocks.lastIndex.coerceAtLeast(0))
                val delta = chunk.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> {
                        val textDelta = delta.optString("text")
                        if (textDelta.isNotEmpty()) {
                            assistantText.append(textDelta)
                            mergeAnthropicTextDelta(index, textDelta)
                            onTextDelta(textDelta)
                        }
                    }

                    "input_json_delta" -> {
                        val accumulator = toolUseBlocks.getOrPut(index) { MutableAnthropicToolUseAccumulator() }
                        if (!accumulator.hasInputJsonDeltas) {
                            accumulator.input.setLength(0)
                            accumulator.hasInputJsonDeltas = true
                        }
                        accumulator.input.append(delta.optString("partial_json"))
                    }
                }
            }

            "content_block_stop" -> {
                val index = chunk.optInt("index", contentBlocks.lastIndex.coerceAtLeast(0))
                toolUseBlocks[index]?.let { accumulator ->
                    contentBlocks[index] = JSONObject().apply {
                        put("type", "tool_use")
                        put("id", accumulator.id)
                        put("name", accumulator.name)
                        put("input", parseJsonValueSafe(accumulator.input.toString()))
                    }
                }
            }
        }
    }

    fun buildResult(): ChatCompletionResult {
        val normalizedBlocks = JSONArray().apply {
            contentBlocks.forEach { block ->
                if (block.optString("type") == "text" && block.optString("text").isBlank()) return@forEach
                put(block)
            }
        }
        return buildAnthropicResultFromContent(
            assistantText = assistantText.toString(),
            content = normalizedBlocks,
        )
    }

    private fun ensureContentBlock(index: Int, block: JSONObject): JSONObject {
        while (contentBlocks.size <= index) {
            contentBlocks += JSONObject()
        }
        contentBlocks[index] = JSONObject(block.toString())
        return contentBlocks[index]
    }

    private fun mergeAnthropicTextDelta(
        index: Int,
        textDelta: String,
    ) {
        val block = if (index in contentBlocks.indices) {
            contentBlocks[index]
        } else {
            ensureContentBlock(index, JSONObject().apply { put("type", "text") })
        }
        block.put("type", "text")
        block.put("text", block.optString("text") + textDelta)
    }
}

private class VertexStreamAccumulator(
    private val onTextDelta: suspend (String) -> Unit,
) {
    private val assistantText = StringBuilder()
    private val parts = mutableListOf<MutableVertexPartAccumulator>()

    suspend fun consume(chunk: JSONObject) {
        val candidates = chunk.optJSONArray("candidates") ?: return
        for (candidateIndex in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(candidateIndex) ?: continue
            val content = candidate.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue

            for (partIndex in 0 until parts.length()) {
                val incomingPart = parts.optJSONObject(partIndex) ?: continue
                val accumulator = this.parts.getOrNull(partIndex) ?: MutableVertexPartAccumulator().also {
                    this.parts += it
                }
                val textDelta = accumulator.mergeFrom(incomingPart)
                if (textDelta.isNotEmpty()) {
                    assistantText.append(textDelta)
                    onTextDelta(textDelta)
                }
            }
        }
    }

    fun buildResult(): ChatCompletionResult {
        val resolvedText = assistantText.toString()
        val resolvedParts = parts
            .map { it.toJson() }
            .filter { it.length() > 0 }
        val resolvedToolCalls = resolvedParts.mapIndexedNotNull { index, part ->
            val functionCall = part.optJSONObject("functionCall") ?: return@mapIndexedNotNull null
            ChatCompletionToolCall(
                id = functionCall.optString("id").trim().ifBlank { generatedToolCallId("vertex", index) },
                name = functionCall.optString("name").trim(),
                arguments = when (val argsValue = functionCall.opt("args")) {
                    null,
                    JSONObject.NULL -> "{}"
                    else -> argsValue.toString()
                },
            )
        }
        return ChatCompletionResult(
            assistantText = resolvedText,
            toolCalls = resolvedToolCalls,
            assistantMessage = buildVertexAssistantMessage(resolvedParts),
        )
    }
}

private fun extractTextDelta(message: JSONObject): String =
    when (val content = message.opt("content")) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                when (val item = content.opt(index)) {
                    is JSONObject -> {
                        append(
                            item.optString("text")
                                .ifBlank { item.optString("content") }
                        )
                    }

                    is String -> append(item)
                }
            }
        }

        else -> ""
    }

private fun extractReasoningDelta(message: JSONObject): String =
    when (val value = message.opt("reasoning")) {
        is String -> value
        is JSONObject -> value.optNullableString("text")
            .orEmpty()
            .ifBlank { value.optNullableString("content").orEmpty() }
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                when (val item = value.opt(index)) {
                    is JSONObject -> append(
                        item.optNullableString("text")
                            .orEmpty()
                            .ifBlank { item.optNullableString("content").orEmpty() }
                    )
                    is String -> append(item)
                }
            }
        }
        else -> ""
    }

private fun extractOpenAiReasoningText(message: JSONObject): String =
    message.optNullableString("reasoning_content").orEmpty()
        .ifBlank { extractReasoningDelta(message) }
        .ifBlank { extractReasoningDetailsText(message.optJSONArray("reasoning_details")) }
        .ifBlank { extractReasoningDetailsSummary(message.optJSONArray("reasoning_details")) }

private fun extractReasoningDetailsText(details: JSONArray?): String {
    if (details == null) return ""
    return buildString {
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            if (detail.optString("type") != "reasoning.text") continue
            appendReasoningTextValue(detail.opt("text"))
            appendReasoningTextValue(detail.opt("content"))
        }
    }
}

private fun extractReasoningDetailsSummary(details: JSONArray?): String {
    if (details == null) return ""
    return buildString {
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            if (detail.optString("type") != "reasoning.summary") continue
            appendReasoningSummaryValue(detail.opt("summary"))
        }
    }
}

private fun StringBuilder.appendReasoningTextValue(value: Any?) {
    when (value) {
        is String -> append(value)
        is JSONObject -> {
            val text = value.optNullableString("text")
                .orEmpty()
                .ifBlank { value.optNullableString("content").orEmpty() }
            append(text)
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                appendReasoningTextValue(value.opt(index))
            }
        }
    }
}

private fun StringBuilder.appendReasoningSummaryValue(value: Any?) {
    when (value) {
        is String -> append(value)
        is JSONObject -> {
            val text = value.optNullableString("text")
                .orEmpty()
                .ifBlank { value.optNullableString("content").orEmpty() }
            append(text)
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                appendReasoningSummaryValue(value.opt(index))
            }
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    return when (value) {
        JSONObject.NULL -> null
        is String -> value
        else -> value.toString()
    }
}

private fun buildOpenAiResponsesResult(output: JSONArray): ChatCompletionResult {
    val assistantText = StringBuilder()
    val toolCalls = mutableListOf<ChatCompletionToolCall>()
    val assistantItems = JSONArray()

    for (index in 0 until output.length()) {
        val item = output.optJSONObject(index) ?: continue
        when (item.optString("type")) {
            "message" -> {
                assistantItems.put(JSONObject(item.toString()))
                val content = item.optJSONArray("content") ?: JSONArray()
                for (contentIndex in 0 until content.length()) {
                    val block = content.optJSONObject(contentIndex) ?: continue
                    val text = when (block.optString("type")) {
                        "output_text", "text" -> block.optString("text")
                        else -> ""
                    }
                    if (text.isBlank()) continue
                    if (assistantText.isNotEmpty()) assistantText.append('\n')
                    assistantText.append(text)
                }
            }

            "function_call" -> {
                assistantItems.put(JSONObject(item.toString()))
                toolCalls += ChatCompletionToolCall(
                    id = item.optString("call_id")
                        .ifBlank { item.optString("id") }
                        .ifBlank { generatedToolCallId("responses", index) },
                    name = item.optString("name"),
                    arguments = item.optString("arguments").ifBlank { "{}" },
                )
            }
        }
    }

    return ChatCompletionResult(
        assistantText = assistantText.toString(),
        toolCalls = toolCalls,
        assistantMessage = JSONObject().apply {
            put(OpenAiResponsesItemsKey, assistantItems)
        },
    )
}

private fun buildAnthropicResultFromContent(
    assistantText: String,
    content: JSONArray,
): ChatCompletionResult {
    val toolCalls = buildList {
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index) ?: continue
            if (block.optString("type") != "tool_use") continue
            add(
                ChatCompletionToolCall(
                    id = block.optString("id").ifBlank { generatedToolCallId("anthropic", index) },
                    name = block.optString("name"),
                    arguments = jsonValueToStringTopLevel(block.opt("input")),
                )
            )
        }
    }
    return ChatCompletionResult(
        assistantText = assistantText,
        toolCalls = toolCalls,
        assistantMessage = JSONObject().apply {
            put("role", "assistant")
            put("content", JSONArray(content.toString()))
        },
    )
}

private fun buildOpenAiAssistantMessage(
    assistantText: String,
    toolCalls: List<ChatCompletionToolCall>,
    reasoningContent: String? = null,
    reasoning: String = "",
    reasoningDetails: List<JSONObject> = emptyList(),
): JSONObject = JSONObject().apply {
    put("role", "assistant")
    if (assistantText.isBlank() && toolCalls.isNotEmpty()) {
        put("content", JSONObject.NULL)
    } else {
        put("content", assistantText)
    }
    if (reasoningContent != null) {
        put("reasoning_content", reasoningContent)
    }
    if (reasoning.isNotBlank()) {
        put("reasoning", reasoning)
    }
    if (reasoningDetails.isNotEmpty()) {
        put(
            "reasoning_details",
            JSONArray().apply {
                reasoningDetails.forEach { detail -> put(JSONObject(detail.toString())) }
            }
        )
    }
    if (toolCalls.isNotEmpty()) {
        put(
            "tool_calls",
            JSONArray().apply {
                toolCalls.forEach { toolCall ->
                    put(
                        JSONObject().apply {
                            put("id", toolCall.id)
                            put("type", "function")
                            put(
                                "function",
                                JSONObject().apply {
                                    put("name", toolCall.name)
                                    put("arguments", toolCall.arguments)
                                }
                            )
                        }
                    )
                }
            }
        )
    }
}

private fun buildVertexAssistantMessage(
    assistantParts: List<JSONObject>,
): JSONObject = JSONObject().apply {
    put("role", "model")
    put(
        "parts",
        JSONArray().apply {
            assistantParts.forEach { part ->
                sanitizeVertexConversationParts(part).forEach(::put)
            }
        }
    )
}

private fun sanitizeVertexConversationMessage(message: JSONObject): JSONObject =
    JSONObject(message.toString()).apply {
        val parts = optJSONArray("parts") ?: return@apply
        val sanitizedParts = JSONArray()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            sanitizeVertexConversationParts(part).forEach(sanitizedParts::put)
        }
        put("parts", sanitizedParts)
    }

private fun sanitizeVertexConversationPart(part: JSONObject): JSONObject =
    JSONObject(part.toString()).apply {
        if (optString("text").isBlank()) {
            remove("text")
        }
        optJSONObject("functionCall")?.remove("id")
        optJSONObject("functionResponse")?.remove("id")
        optJSONObject("inlineData")?.let { inlineData ->
            if (inlineData.optString("data").isBlank() || inlineData.optString("mimeType").isBlank()) {
                remove("inlineData")
            }
        }
        optJSONObject("fileData")?.let { fileData ->
            if (fileData.optString("fileUri").isBlank()) {
                remove("fileData")
            }
        }
    }

private fun sanitizeVertexConversationParts(part: JSONObject): List<JSONObject> {
    val sanitizedPart = sanitizeVertexConversationPart(part)
    val dataKeys = VertexPartDataKeys.filter(sanitizedPart::hasMeaningfulVertexData)
    if (dataKeys.isEmpty()) return emptyList()

    val metadata = JSONObject().apply {
        VertexPartMetadataKeys.forEach { key ->
            if (sanitizedPart.has(key)) {
                put(key, deepCopyJsonValue(sanitizedPart.opt(key)))
            }
        }
    }

    return dataKeys.map { key ->
        JSONObject().apply {
            VertexPartMetadataKeys.forEach { metadataKey ->
                if (metadata.has(metadataKey)) {
                    put(metadataKey, deepCopyJsonValue(metadata.opt(metadataKey)))
                }
            }
            put(key, deepCopyJsonValue(sanitizedPart.opt(key)))
        }
    }
}

private class MutableVertexPartAccumulator {
    private val part = JSONObject()

    fun mergeFrom(incomingPart: JSONObject): String {
        if (incomingPart.has("thought")) {
            part.put("thought", incomingPart.optBoolean("thought"))
        }
        if (incomingPart.has("thoughtSignature")) {
            part.put("thoughtSignature", incomingPart.optString("thoughtSignature"))
        }

        copyOptionalField("mediaResolution", incomingPart)
        copyOptionalField("inlineData", incomingPart)
        copyOptionalField("fileData", incomingPart)
        copyOptionalField("functionResponse", incomingPart)
        copyOptionalField("executableCode", incomingPart)
        copyOptionalField("codeExecutionResult", incomingPart)
        copyOptionalField("videoMetadata", incomingPart)

        val textDelta = if (incomingPart.has("text")) {
            val incomingText = incomingPart.optString("text")
            val existingText = part.optString("text")
            if (existingText.isNotEmpty() && incomingText.startsWith(existingText)) {
                part.put("text", incomingText)
                incomingText.removePrefix(existingText)
            } else {
                part.put("text", existingText + incomingText)
                incomingText
            }
        } else {
            ""
        }

        incomingPart.optJSONObject("functionCall")?.let { incomingFunctionCall ->
            val functionCall = part.optJSONObject("functionCall") ?: JSONObject()
            if (incomingFunctionCall.has("id")) {
                functionCall.put("id", incomingFunctionCall.optString("id"))
            }
            if (incomingFunctionCall.has("name")) {
                functionCall.put("name", incomingFunctionCall.optString("name"))
            }
            if (incomingFunctionCall.has("args")) {
                functionCall.put("args", deepCopyJsonValue(incomingFunctionCall.opt("args")))
            }
            if (incomingFunctionCall.has("partialArgs")) {
                functionCall.put("partialArgs", deepCopyJsonValue(incomingFunctionCall.opt("partialArgs")))
            }
            if (incomingFunctionCall.has("willContinue")) {
                functionCall.put("willContinue", incomingFunctionCall.optBoolean("willContinue"))
            }
            part.put("functionCall", functionCall)
        }

        return textDelta
    }

    fun toJson(): JSONObject = JSONObject(part.toString())

    private fun copyOptionalField(
        key: String,
        incomingPart: JSONObject,
    ) {
        if (incomingPart.hasMeaningfulVertexData(key)) {
            part.put(key, deepCopyJsonValue(incomingPart.opt(key)))
        }
    }
}

private fun JSONObject.hasMeaningfulVertexData(key: String): Boolean = when (key) {
    "text" -> optString("text").isNotBlank()
    "inlineData" -> optJSONObject("inlineData")?.let { inlineData ->
        inlineData.optString("data").isNotBlank() && inlineData.optString("mimeType").isNotBlank()
    } == true
    "fileData" -> optJSONObject("fileData")?.optString("fileUri")?.isNotBlank() == true
    "functionCall" -> optJSONObject("functionCall")?.let { functionCall ->
        functionCall.optString("name").isNotBlank() ||
            functionCall.has("args") ||
            functionCall.has("partialArgs") ||
            functionCall.has("willContinue")
    } == true
    "functionResponse" -> optJSONObject("functionResponse")?.let { functionResponse ->
        functionResponse.optString("name").isNotBlank() || functionResponse.has("response")
    } == true
    "executableCode" -> optJSONObject("executableCode")?.length()?.let { it > 0 } == true
    "codeExecutionResult" -> optJSONObject("codeExecutionResult")?.length()?.let { it > 0 } == true
    else -> has(key)
}

private fun deepCopyJsonValue(value: Any?): Any = when (value) {
    null,
    JSONObject.NULL -> JSONObject.NULL
    is JSONObject -> JSONObject(value.toString())
    is JSONArray -> JSONArray(value.toString())
    else -> value
}

private val VertexPartDataKeys = listOf(
    "text",
    "inlineData",
    "fileData",
    "functionCall",
    "functionResponse",
    "executableCode",
    "codeExecutionResult",
)

private val VertexPartMetadataKeys = listOf(
    "thought",
    "thoughtSignature",
    "videoMetadata",
    "mediaResolution",
)

private fun generatedToolCallId(
    providerPrefix: String,
    index: Int,
): String = "$providerPrefix-tool-$index-${UUID.randomUUID()}"

private data class MutableToolCallAccumulator(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
)

private data class MutableAnthropicToolUseAccumulator(
    var id: String = "",
    var name: String = "",
    val input: StringBuilder = StringBuilder(),
    var hasInputJsonDeltas: Boolean = false,
)

private data class HttpResponsePayload(
    val code: Int,
    val isSuccessful: Boolean,
    val bodyString: String,
    val contentType: String,
    val requestUrl: String,
    val retryAfterHeader: String = "",
)

private fun buildLlmRequestException(
    responsePayload: HttpResponsePayload,
    message: String,
): Throwable = LlmHttpException(
    statusCode = responsePayload.code,
    message = message,
    retryAfterMillis = parseRetryAfterMillis(responsePayload.retryAfterHeader, message),
)

private fun parseRetryAfterMillis(
    retryAfterHeader: String,
    message: String,
): Long? = parseRetryAfterHeaderMillis(retryAfterHeader) ?: parseRetryAfterMessageMillis(message)

private fun parseRetryAfterHeaderMillis(rawValue: String): Long? {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank()) return null
    trimmed.toLongOrNull()?.let { seconds ->
        if (seconds >= 0L) return seconds * 1000L
    }
    trimmed.toDoubleOrNull()?.let { seconds ->
        if (seconds >= 0.0) return (seconds * 1000.0).roundToLong()
    }
    return null
}

private fun parseRetryAfterMessageMillis(message: String?): Long? {
    val match = RetryAfterMessageRegex.find(message.orEmpty()) ?: return null
    val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
    val unit = match.groupValues.getOrNull(2).orEmpty().lowercase()
    val multiplier = when {
        unit == "ms" || unit.startsWith("millisecond") -> 1.0
        unit == "s" || unit.startsWith("sec") || unit.startsWith("second") -> 1000.0
        unit == "m" || unit == "min" || unit.startsWith("minute") -> 60_000.0
        unit == "h" || unit == "hr" || unit.startsWith("hour") -> 3_600_000.0
        else -> return null
    }
    return (amount * multiplier).roundToLong()
}

private fun vertexChunkSignalsCompletion(chunk: JSONObject): Boolean {
    val candidates = chunk.optJSONArray("candidates")
    if (candidates != null) {
        for (index in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(index) ?: continue
            if (candidate.optString("finishReason").isNotBlank()) {
                return true
            }
        }
    }
    return chunk.has("promptFeedback")
}

private fun openAiResponsesChunkSignalsCompletion(chunk: JSONObject): Boolean =
    chunk.optString("type") == "response.completed"

private fun anthropicChunkSignalsCompletion(chunk: JSONObject): Boolean =
    chunk.optString("type") == "message_stop"

private fun parseJsonValueSafe(rawValue: String): Any = runCatching {
    JSONTokener(rawValue.ifBlank { "{}" }).nextValue()
}.getOrDefault(JSONObject())

private fun jsonValueToStringTopLevel(value: Any?): String = when (value) {
    null -> "{}"
    JSONObject.NULL -> "null"
    is JSONObject -> value.toString()
    is JSONArray -> value.toString()
    else -> JSONObject.wrap(value)?.toString() ?: value.toString()
}

private val RetryAfterMessageRegex = Regex(
    "try again in\\s+(\\d+(?:\\.\\d+)?)\\s*(milliseconds?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)",
    RegexOption.IGNORE_CASE,
)

private const val DefaultHttpConnectTimeoutMillis = 30_000L
private const val DefaultHttpReadTimeoutMillis = 90_000L
private const val DefaultHttpWriteTimeoutMillis = 30_000L
private const val DefaultHttpCallTimeoutMillis = 90_000L
private const val DefaultStreamingConnectTimeoutMillis = 30_000L
private const val DefaultStreamingWriteTimeoutMillis = 30_000L
private const val DefaultAnthropicMaxTokens = 4096
private const val AnthropicVersion = "2023-06-01"
private const val OpenAiResponsesItemsKey = "aether_response_items"
