package com.zhousl.aether.data.pi

import com.zhousl.aether.data.ActiveSkillContext
import com.zhousl.aether.data.AetherAgentTurnResult
import com.zhousl.aether.data.AetherDiagnosticLogger
import com.zhousl.aether.data.AetherSelfManagementTool
import com.zhousl.aether.data.AetherToolExecutor
import com.zhousl.aether.data.AgentToolEvent
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.LlmMessage
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.McpClientManager
import com.zhousl.aether.data.McpToolBinding
import com.zhousl.aether.data.PiExtensionStateRepository
import com.zhousl.aether.data.StreamingStatus
import com.zhousl.aether.data.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val InjectedMessagePollIntervalMillis = 150L

class PiAgentRunner(
    private val bridge: PiKernelBridge,
    private val toolExecutor: AetherToolExecutor? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val piExtensionStateRepository: PiExtensionStateRepository? = null,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    suspend fun runTurn(
        settings: AppSettings,
        messages: List<LlmMessage>,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        availableSkills: List<InstalledSkill> = emptyList(),
        activeSkills: List<ActiveSkillContext> = emptyList(),
        mcpToolBindings: List<McpToolBinding> = emptyList(),
        mcpClientManager: McpClientManager? = null,
        selfManagementTool: AetherSelfManagementTool? = null,
        agentModeEnabled: Boolean = false,
        chromeEnabled: Boolean = false,
        providerConfigs: List<LlmProviderConfig> = emptyList(),
        sessionId: String = "",
        onToolEvent: suspend (AgentToolEvent) -> Unit = {},
        onToolProgress: (suspend (AgentToolEvent) -> Unit)? = null,
        onAssistantTextDelta: suspend (String) -> Unit = {},
        onAssistantReasoningDelta: suspend (String) -> Unit = {},
        onAssistantReasoningSummaryDelta: suspend (String) -> Unit = {},
        onAssistantTextReset: suspend () -> Unit = {},
        onStreamingStatus: suspend (StreamingStatus?) -> Unit = {},
        onSkillActivated: suspend (ActiveSkillContext) -> Unit = {},
        pollInjectedUserMessages: suspend () -> List<LlmMessage> = { emptyList() },
    ): Result<AetherAgentTurnResult> {
        onStreamingStatus(StreamingStatus("Thinking", "Aether is working on this turn."))
        return try {
            runCatchingPreservingCancellation {
                val resolvedSessionId = sessionId.ifBlank {
                    "aether-session-${System.currentTimeMillis()}"
                }
                val resolvedAvailableSkills = availableSkills
                    .filter { it.isEnabled }
                    .sortedBy { it.name.lowercase() }
                val resolvedActiveSkills = activeSkills.toMutableList()
                val prompt = {
                    buildPiAgentInstructions(
                        settings = settings,
                        workspaceDirectory = workspaceDirectory,
                        availableSkills = resolvedAvailableSkills,
                        activeSkills = resolvedActiveSkills,
                        mcpSnapshots = mcpClientManager?.snapshots().orEmpty(),
                        mcpToolBindings = mcpToolBindings,
                        agentModeEnabled = agentModeEnabled,
                        chromeEnabled = chromeEnabled,
                    )
                }
                val payload = JSONObject().apply {
                    val extensionLoadOptions = piExtensionStateRepository?.loadOptions()
                    put("model_config", settings.toPiModelConfig().toJson())
                    put("session_id", resolvedSessionId)
                    put("system_prompt", prompt())
                    put("messages", messages.toPiJson())
                    put("workspace_directory", workspaceDirectory)
                    put("reasoning", settings.toPiThinkingLevel())
                    put(
                        "disabled_extension_paths",
                        JSONArray(extensionLoadOptions?.disabledExtensionPaths?.toList().orEmpty()),
                    )
                    put(
                        "disabled_package_sources",
                        JSONArray(extensionLoadOptions?.disabledPackageSources?.toList().orEmpty()),
                    )
                    put(
                        "host_tools",
                        AetherToolExecutor.hostToolDefinitions(
                            selfManagementTool = selfManagementTool,
                            mcpClientManager = mcpClientManager,
                            mcpToolBindings = mcpToolBindings,
                            agentModeEnabled = agentModeEnabled,
                            chromeEnabled = chromeEnabled,
                        ),
                    )
                }

                coroutineScope {
                    val parallelHostToolJobs = ConcurrentHashMap<String, Job>()
                    val handledHostToolRequestIds = ConcurrentHashMap.newKeySet<String>()
                    val sequentialHostToolRequests = Channel<JSONObject>(Channel.UNLIMITED)
                    val sequentialHostToolWorker = launch {
                        for (requestPayload in sequentialHostToolRequests) {
                            handleHostToolRequest(
                                payload = requestPayload,
                                sessionId = resolvedSessionId,
                                settings = settings,
                                workspaceDirectory = workspaceDirectory,
                                termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                                availableSkills = resolvedAvailableSkills,
                                activeSkills = resolvedActiveSkills,
                                providerConfigs = providerConfigs,
                                mcpClientManager = mcpClientManager,
                                selfManagementTool = selfManagementTool,
                                mcpToolBindings = mcpToolBindings,
                                agentModeEnabled = agentModeEnabled,
                                chromeEnabled = chromeEnabled,
                                updatedSystemPrompt = prompt,
                                onSkillActivated = onSkillActivated,
                            )
                        }
                    }

                    suspend fun dispatchHostToolRequest(eventPayload: JSONObject) {
                        val toolRequestId = eventPayload.optString("tool_request_id").trim()
                        if (toolRequestId.isBlank()) {
                            logMalformedHostToolRequest(eventPayload, resolvedSessionId)
                            return
                        }
                        if (!handledHostToolRequestIds.add(toolRequestId)) return
                        val requestPayload = JSONObject(eventPayload.toString())
                        if (eventPayload.optString("execution_mode") == "sequential") {
                            sequentialHostToolRequests.send(requestPayload)
                            return
                        }
                        val job = launch(start = CoroutineStart.LAZY) {
                            try {
                                handleHostToolRequest(
                                    payload = requestPayload,
                                    sessionId = resolvedSessionId,
                                    settings = settings,
                                    workspaceDirectory = workspaceDirectory,
                                    termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                                    availableSkills = resolvedAvailableSkills,
                                    activeSkills = resolvedActiveSkills,
                                    providerConfigs = providerConfigs,
                                    mcpClientManager = mcpClientManager,
                                    selfManagementTool = selfManagementTool,
                                    mcpToolBindings = mcpToolBindings,
                                    agentModeEnabled = agentModeEnabled,
                                    chromeEnabled = chromeEnabled,
                                    updatedSystemPrompt = prompt,
                                    onSkillActivated = onSkillActivated,
                                )
                            } finally {
                                parallelHostToolJobs.remove(toolRequestId)
                            }
                        }
                        parallelHostToolJobs[toolRequestId] = job
                        job.start()
                    }

                    val eventHandler: suspend (String, JSONObject) -> Unit = { event, eventPayload ->
                        when (event) {
                            "assistant_text_delta" ->
                                onAssistantTextDelta(eventPayload.optString("delta"))

                            "assistant_reasoning_delta" -> {
                                val delta = eventPayload.optString("delta")
                                onAssistantReasoningDelta(delta)
                                if (eventPayload.optString("kind") == "summary") {
                                    onAssistantReasoningSummaryDelta(delta)
                                }
                            }

                            "tool_call_start" -> {
                                onAssistantTextReset()
                                onToolEvent(eventPayload.toToolEvent(isRunning = true))
                            }

                            "tool_call_delta" -> {
                                val toolEvent = eventPayload.toToolEvent(isRunning = true)
                                if (toolEvent.outputJson == null) {
                                    onToolEvent(toolEvent)
                                } else {
                                    (onToolProgress ?: onToolEvent)(toolEvent)
                                }
                            }

                            "tool_call_end" ->
                                onToolEvent(eventPayload.toToolEvent(isRunning = false))

                            "host_tool_request" -> dispatchHostToolRequest(eventPayload)

                            "assistant_error" -> onStreamingStatus(
                                StreamingStatus(
                                    text = "Agent engine error",
                                    detail = eventPayload.optString("error_message"),
                                )
                            )
                        }
                    }

                    val deferredInjectedMessages = ConcurrentLinkedQueue<LlmMessage>()
                    suspend fun forwardInjectedMessages() {
                        pollInjectedUserMessages().forEach { message ->
                            val accepted = runCatching {
                                bridge.steer(resolvedSessionId, message.toPiJson())
                                    .optBoolean("accepted")
                            }.getOrElse { throwable ->
                                if (throwable is CancellationException) throw throwable
                                false
                            }
                            if (!accepted) {
                                deferredInjectedMessages += message
                            }
                        }
                    }

                    val pollingJob = launch {
                        while (isActive) {
                            forwardInjectedMessages()
                            delay(InjectedMessagePollIntervalMillis)
                        }
                    }
                    try {
                        var response = bridge.runTurn(payload, eventHandler)
                        forwardInjectedMessages()
                        while (true) {
                            val injected = deferredInjectedMessages.poll() ?: break
                            response = bridge.followUp(
                                sessionId = resolvedSessionId,
                                message = injected.toPiJson(),
                                onEvent = eventHandler,
                            )
                        }

                        val completion = response.toPiCompletionResult()
                        if (
                            settings.providerConfigId.isNotBlank() &&
                            completion.updatedOauthCredentialJson.isNotBlank()
                        ) {
                            settingsRepository?.updateProviderOAuthCredential(
                                settings.providerConfigId,
                                completion.updatedOauthCredentialJson,
                            )
                        }
                        if (completion.errorMessage.isNotBlank()) {
                            error(completion.errorMessage)
                        }
                        AetherAgentTurnResult(
                            assistantText = completion.assistantText.ifBlank {
                                "The model finished without returning any assistant text."
                            },
                            tokenUsage = completion.usage,
                            providerPayloadJson = completion.toProviderPayloadJson(),
                        )
                    } finally {
                        pollingJob.cancelAndJoin()
                        sequentialHostToolRequests.close()
                        sequentialHostToolWorker.cancelAndJoin()
                        parallelHostToolJobs.values.toList().forEach { job ->
                            job.cancelAndJoin()
                        }
                    }
                }
            }
        } finally {
            onStreamingStatus(null)
        }
    }

    private suspend fun handleHostToolRequest(
        payload: JSONObject,
        sessionId: String,
        settings: AppSettings,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        providerConfigs: List<LlmProviderConfig>,
        mcpClientManager: McpClientManager?,
        selfManagementTool: AetherSelfManagementTool?,
        mcpToolBindings: List<McpToolBinding>,
        agentModeEnabled: Boolean,
        chromeEnabled: Boolean,
        updatedSystemPrompt: () -> String,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ) {
        val toolRequestId = payload.optString("tool_request_id").trim()
        val toolCallId = payload.optString("tool_call_id").trim()
        val toolName = payload.optString("tool_name").trim()
        if (toolRequestId.isBlank()) {
            logMalformedHostToolRequest(payload, sessionId)
            return
        }

        val argumentsJson = payload.argumentsJson()
        val executor = toolExecutor
        if (executor == null || !AetherToolExecutor.supports(toolName)) {
            bridge.sendHostToolResult(
                hostToolPayload(
                    sessionId = sessionId,
                    toolRequestId = toolRequestId,
                    toolCallId = toolCallId,
                    toolName = toolName.ifBlank { "unknown" },
                    argumentsJson = argumentsJson,
                    rawOutput = JSONObject().apply {
                        put("ok", false)
                        put("errmsg", "Host tool '$toolName' is not available.")
                    }.toString(),
                    isError = true,
                )
            )
            return
        }

        val result = runCatching {
            executor.execute(
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                toolName = toolName,
                argumentsJson = argumentsJson,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                providerConfigs = providerConfigs,
                mcpClientManager = mcpClientManager,
                selfManagementTool = selfManagementTool,
                agentModeEnabled = agentModeEnabled,
                chromeEnabled = chromeEnabled,
                onProgress = { progress ->
                    bridge.sendHostToolProgress(
                        hostToolPayload(
                            sessionId = sessionId,
                            toolRequestId = toolRequestId,
                            toolCallId = toolCallId,
                            toolName = toolName,
                            argumentsJson = argumentsJson,
                            rawOutput = progress,
                            isError = !AetherToolExecutor.inferToolOutputOk(
                                AetherToolExecutor.sanitizeToolOutputForConversation(toolName, progress),
                            ),
                        )
                    )
                },
                onSkillActivated = onSkillActivated,
            )
        }

        val responsePayload = result.fold(
            onSuccess = { executionResult ->
                hostToolPayload(
                    sessionId = sessionId,
                    toolRequestId = toolRequestId,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    rawOutput = executionResult.rawOutput,
                    visibleOutput = executionResult.visibleOutput,
                    isError = executionResult.isError,
                    systemPrompt = updatedSystemPrompt(),
                )
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) throw throwable
                hostToolPayload(
                    sessionId = sessionId,
                    toolRequestId = toolRequestId,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    rawOutput = JSONObject().apply {
                        put("ok", false)
                        put("errmsg", throwable.message ?: "Tool execution failed.")
                    }.toString(),
                    isError = true,
                )
            },
        )
        bridge.sendHostToolResult(responsePayload)
    }

    private fun logMalformedHostToolRequest(
        payload: JSONObject,
        sessionId: String,
    ) {
        diagnosticLogger.event(
            category = "pi_agent",
            event = "malformed_host_tool_request",
            level = "warn",
            sessionId = sessionId,
            details = mapOf(
                "tool_call_id" to payload.optString("tool_call_id").trim(),
                "tool_name" to payload.optString("tool_name").trim(),
                "reason" to "Missing tool_request_id.",
            ),
        )
    }
}

private fun JSONObject.toToolEvent(isRunning: Boolean): AgentToolEvent =
    AgentToolEvent(
        id = optString("id").ifBlank { "pi-tool-${optInt("content_index", 0)}" },
        name = optString("name").ifBlank { "tool_call" },
        argumentsJson = argumentsJson(),
        outputJson = outputJson(),
        isRunning = isRunning,
    )

private fun JSONObject.argumentsJson(): String {
    val explicit = optString("arguments_json").trim()
    if (explicit.isNotBlank()) return explicit
    return when (val arguments = opt("arguments")) {
        is JSONObject -> arguments.toString()
        is JSONArray -> arguments.toString()
        is String -> arguments.ifBlank { "{}" }
        null,
        JSONObject.NULL -> optString("delta").ifBlank { "{}" }
        else -> JSONObject.wrap(arguments)?.toString() ?: "{}"
    }
}

private fun JSONObject.outputJson(): String? {
    val explicit = optString("output_json")
    if (explicit.isNotBlank()) return explicit
    return when (val output = opt("output")) {
        is JSONObject -> output.toString()
        is JSONArray -> output.toString()
        is String -> output.takeIf { it.isNotBlank() }
        else -> null
    }
}

private fun hostToolPayload(
    sessionId: String,
    toolRequestId: String,
    toolCallId: String,
    toolName: String,
    argumentsJson: String,
    rawOutput: String,
    visibleOutput: String = AetherToolExecutor.sanitizeToolOutputForConversation(toolName, rawOutput),
    isError: Boolean,
    systemPrompt: String = "",
): JSONObject = JSONObject().apply {
    put("session_id", sessionId)
    put("tool_request_id", toolRequestId)
    put("tool_call_id", toolCallId)
    put("tool_name", toolName)
    put("arguments_json", argumentsJson)
    put("output_json", visibleOutput)
    put("raw_output_json", rawOutput)
    put("is_error", isError)
    if (systemPrompt.isNotBlank()) put("system_prompt", systemPrompt)
    put(
        "content",
        JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", visibleOutput)
                }
            )
            if (toolName == "agent_display" || toolName == "chrome") {
                val parsed = runCatching { JSONObject(rawOutput) }.getOrNull()
                val imageData = parsed?.optString("screenshot_base64").orEmpty()
                if (parsed?.optBoolean("ok") == true && imageData.isNotBlank()) {
                    put(
                        JSONObject().apply {
                            put("type", "image")
                            put(
                                "mime_type",
                                parsed.optString("screenshot_mime_type").ifBlank { "image/png" },
                            )
                            put("data", imageData)
                        }
                    )
                }
            }
        },
    )
    put(
        "details",
        JSONObject().apply {
            put("tool_request_id", toolRequestId)
            put("tool_call_id", toolCallId)
            put("tool_name", toolName)
            put("arguments_json", argumentsJson)
            put("output_json", visibleOutput)
            put("is_error", isError)
        },
    )
}

private inline fun <T> runCatchingPreservingCancellation(
    block: () -> T,
): Result<T> = try {
    Result.success(block())
} catch (cancellationException: CancellationException) {
    throw cancellationException
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
