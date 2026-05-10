package com.zhousl.aether.data

import android.os.SystemClock
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxFilesystemTool
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val MaxAnalyzeImageBytes = 5 * 1024 * 1024
private const val MaxSkillResourceBytes = 1024 * 1024
private const val DefaultSkillResourceMaxChars = 20_000
private const val SkillMetadataContextBudgetChars = 8_000
private const val MaxSleepDurationMillis = 10 * 60 * 1000L

class AetherAgent(
    private val client: OpenAiCompatibleClient,
    private val bashTool: TermuxBashTool,
    private val workspaceFileBridge: WorkspaceFileBridge,
    private val agentModeController: AgentModeController,
    private val skillManager: AgentSkillManager,
    private val mcpClientManager: McpClientManager,
    private val webToolsClient: WebToolsClient,
    private val selfManagementTool: AetherSelfManagementTool,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
    private val onParallelToolCallsUnsupported: suspend (String) -> Unit = {},
) {
    private val filesystemTool = TermuxFilesystemTool(bashTool)

    suspend fun runTurn(
        settings: AppSettings,
        messages: List<LlmMessage>,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill> = emptyList(),
        activeSkills: List<ActiveSkillContext> = emptyList(),
        mcpToolBindings: List<McpToolBinding> = emptyList(),
        agentModeEnabled: Boolean = false,
        onToolEvent: suspend (AgentToolEvent) -> Unit = {},
        onAssistantTextDelta: suspend (String) -> Unit = {},
        onAssistantReasoningDelta: suspend (String) -> Unit = {},
        onAssistantReasoningSummaryDelta: suspend (String) -> Unit = {},
        onAssistantTextReset: suspend () -> Unit = {},
        onStreamingStatus: suspend (StreamingStatus?) -> Unit = {},
        onSkillActivated: suspend (ActiveSkillContext) -> Unit = {},
        pollInjectedUserMessages: suspend () -> List<LlmMessage> = { emptyList() },
    ): Result<String> {
        diagnosticLogger.event(
            category = "agent",
            event = "turn_start",
            details = mapOf(
                "provider" to settings.provider.storageValue,
                "model" to settings.modelId,
                "base_url" to DiagnosticRedactor.sanitizedBaseUrl(settings.baseUrl),
                "message_count" to messages.size,
                "available_skill_count" to availableSkills.size,
                "active_skill_count" to activeSkills.size,
                "mcp_tool_count" to mcpToolBindings.size,
                "agent_mode_enabled" to agentModeEnabled,
            ),
        )
        return try {
        val conversation = client.buildConversation(
            settings = settings,
            messages = messages,
        ).toMutableList()
        val resolvedAvailableSkills = availableSkills
            .filter { it.isEnabled }
            .sortedBy { it.name.lowercase() }
        val resolvedActiveSkills = activeSkills.toMutableList()
        val baseTools = listOf(
            buildReadToolDefinition(),
            buildEditToolDefinition(),
            buildWriteToolDefinition(),
            buildGrepToolDefinition(),
            buildFindToolDefinition(),
            buildLsToolDefinition(),
            buildBashToolDefinition(),
            buildFetchBashOutputToolDefinition(),
            buildKillBashToolDefinition(),
            buildSleepToolDefinition(),
            buildAnalyzeImageToolDefinition(),
            buildFetchWebUrlToolDefinition(),
            buildTavilySearchToolDefinition(),
            *selfManagementTool.toolDefinitions().toTypedArray(),
            if (agentModeEnabled) buildAgentModeToolDefinition() else null,
        ).filterNotNull()
        val hasMcpCatalog = mcpToolBindings.isNotEmpty() ||
            mcpClientManager.snapshots().any { it.resources.isNotEmpty() || it.prompts.isNotEmpty() }
        val useBasicToolCompatibility = settings.basicFunctionCallingCompatibilityMode
        val exposeNamespacedMcpTools =
            !useBasicToolCompatibility &&
            settings.provider in setOf(
                LlmProvider.OpenAiResponses,
                LlmProvider.OpenAiCompatible,
            ) && mcpToolBindings.isNotEmpty()
        var lastAssistantText = ""
        var lastAgentModeScreenshotMessageIndex: Int? = null
        val latestUserText = messages.lastOrNull { it.role == "user" }
            ?.contentParts
            ?.filterIsInstance<LlmTextPart>()
            ?.joinToString("\n") { it.text }
            .orEmpty()
        val initialToolChoice = if (shouldForceToolUse(latestUserText)) "required" else "auto"
        val parallelToolCallSupportKey = settings.parallelToolCallSupportKey()
        var parallelToolCallsEnabled = !useBasicToolCompatibility && settings.supportsParallelToolCalls()

        var round = 0
        while (true) {
            val injectedMessages = pollInjectedUserMessages()
            if (injectedMessages.isNotEmpty()) {
                lastAssistantText = ""
                conversation += client.buildConversation(
                    settings = settings,
                    messages = injectedMessages,
                )
            }
            val systemPrompt = buildAgentInstructions(
                systemPrompt = settings.systemPrompt,
                workspaceDirectory = workspaceDirectory,
                availableSkills = resolvedAvailableSkills,
                activeSkills = resolvedActiveSkills,
                mcpToolBindings = mcpToolBindings,
                exposeNamespacedMcpTools = exposeNamespacedMcpTools,
                agentModeEnabled = agentModeEnabled,
                parallelToolCallsEnabled = parallelToolCallsEnabled,
                basicToolCompatibilityMode = useBasicToolCompatibility,
            )
            val tools = buildList {
                addAll(baseTools)
                if (!parallelToolCallsEnabled && !useBasicToolCompatibility) {
                    add(buildRunToolBatchToolDefinition())
                }
                if (resolvedAvailableSkills.isNotEmpty()) {
                    add(buildActivateSkillToolDefinition())
                }
                if (resolvedAvailableSkills.isNotEmpty() || resolvedActiveSkills.isNotEmpty()) {
                    add(buildReadSkillResourceToolDefinition())
                }
            }
            val effectiveTools = if (hasMcpCatalog) {
                tools +
                    buildMcpGenericToolDefinitions() +
                    if (exposeNamespacedMcpTools) {
                        mcpToolBindings.map(::buildMcpToolDefinition)
                    } else {
                        emptyList()
                    }
            } else {
                tools
            }
            val response = try {
                streamChatCompletionWithReconnect(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    conversation = conversation,
                    tools = effectiveTools,
                    toolChoice = if (useBasicToolCompatibility) {
                        "auto"
                    } else if (round == 0) {
                        initialToolChoice
                    } else {
                        "auto"
                    },
                    parallelToolCallsEnabled = parallelToolCallsEnabled,
                    onParallelToolCallsUnsupported = {
                        parallelToolCallsEnabled = false
                        onParallelToolCallsUnsupported(parallelToolCallSupportKey)
                    },
                    onTextDelta = onAssistantTextDelta,
                    onReasoningDelta = onAssistantReasoningDelta,
                    onReasoningSummaryDelta = onAssistantReasoningSummaryDelta,
                    onTextReset = onAssistantTextReset,
                    onStreamingStatus = onStreamingStatus,
                )
            } catch (_: ParallelToolCallsUnsupportedRestart) {
                continue
            }

            conversation += response.assistantMessage

            if (response.assistantText.isNotBlank()) {
                lastAssistantText = response.assistantText
            }

            if (response.toolCalls.isEmpty()) {
                val trailingInjectedMessages = pollInjectedUserMessages()
                if (trailingInjectedMessages.isNotEmpty()) {
                    lastAssistantText = ""
                    conversation += client.buildConversation(
                        settings = settings,
                        messages = trailingInjectedMessages,
                    )
                    round += 1
                    continue
                }
                break
            }

            onAssistantTextReset()
            var pendingAgentDisplayScreenshotMessage: LlmMessage? = null
            val toolResults = executeToolCalls(
                toolCalls = response.toolCalls,
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                availableSkills = resolvedAvailableSkills,
                activeSkills = resolvedActiveSkills,
                round = round,
                parallelToolCallsEnabled = parallelToolCallsEnabled,
                onToolEvent = onToolEvent,
                onSkillActivated = onSkillActivated,
            )
            toolResults.forEach { result ->
                buildAgentDisplayScreenshotMessage(result.rawOutput)?.let { screenshotMessage ->
                    pendingAgentDisplayScreenshotMessage = screenshotMessage
                }
            }
            conversation += client.buildToolResultMessages(
                settings = settings,
                results = toolResults.map { result ->
                    ChatCompletionToolResult(
                        callId = result.id,
                        name = result.name,
                        output = result.visibleOutput,
                    )
                },
            )
            pendingAgentDisplayScreenshotMessage?.let { screenshotMessage ->
                lastAgentModeScreenshotMessageIndex?.let { index ->
                    if (index in conversation.indices) {
                        conversation.removeAt(index)
                    }
                }
                lastAgentModeScreenshotMessageIndex = null
                conversation += client.buildConversation(
                    settings = settings,
                    messages = listOf(screenshotMessage),
                )
                lastAgentModeScreenshotMessageIndex = conversation.lastIndex
            }
            round += 1
        }
        Result.success(
            lastAssistantText.ifBlank {
                "The model finished without returning any assistant text."
            }
        ).also {
            diagnosticLogger.event(
                category = "agent",
                event = "turn_end",
                details = mapOf("ok" to true),
            )
        }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "agent",
                event = "turn_failed",
                throwable = throwable,
            )
            Result.failure(throwable)
        }
    }

    private suspend fun executeToolCalls(
        toolCalls: List<ChatCompletionToolCall>,
        settings: AppSettings,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        round: Int,
        parallelToolCallsEnabled: Boolean,
        onToolEvent: suspend (AgentToolEvent) -> Unit,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): List<ExecutedToolCallResult> {
        val results = mutableListOf<ExecutedToolCallResult>()
        var index = 0
        while (index < toolCalls.size) {
            val current = IndexedToolCall(
                toolCall = toolCalls[index],
                id = toolCalls[index].id.ifBlank { "tool-$round-$index" },
            )
            if (!parallelToolCallsEnabled || !isParallelSafeToolCall(current.toolCall.name)) {
                onToolStarted(current, onToolEvent)
                val result = executeToolCall(
                    indexedToolCall = current,
                    settings = settings,
                    workspaceDirectory = workspaceDirectory,
                    availableSkills = availableSkills,
                    activeSkills = activeSkills,
                    onSkillActivated = onSkillActivated,
                )
                onToolCompleted(result, onToolEvent)
                results += result
                index += 1
                continue
            }

            val batch = mutableListOf(current)
            index += 1
            while (
                index < toolCalls.size &&
                isParallelSafeToolCall(toolCalls[index].name)
            ) {
                batch += IndexedToolCall(
                    toolCall = toolCalls[index],
                    id = toolCalls[index].id.ifBlank { "tool-$round-$index" },
                )
                index += 1
            }

            batch.forEach { onToolStarted(it, onToolEvent) }
            val indexedBatchResults = coroutineScope {
                batch.mapIndexed { batchIndex, item ->
                    async {
                        val result = executeToolCall(
                            indexedToolCall = item,
                            settings = settings,
                            workspaceDirectory = workspaceDirectory,
                            availableSkills = availableSkills,
                            activeSkills = activeSkills,
                            onSkillActivated = onSkillActivated,
                        )
                        onToolCompleted(result, onToolEvent)
                        batchIndex to result
                    }
                }.map { it.await() }
            }
            results += indexedBatchResults
                .sortedBy { it.first }
                .map { it.second }
        }
        return results
    }

    private suspend fun onToolStarted(
        indexedToolCall: IndexedToolCall,
        onToolEvent: suspend (AgentToolEvent) -> Unit,
    ) {
        diagnosticLogger.event(
            category = "tool",
            event = "tool_start",
            requestId = indexedToolCall.id,
            details = mapOf(
                "tool_name" to indexedToolCall.toolCall.name,
                "argument_chars" to indexedToolCall.toolCall.arguments.length,
            ),
        )
        onToolEvent(
            AgentToolEvent(
                id = indexedToolCall.id,
                name = indexedToolCall.toolCall.name,
                argumentsJson = indexedToolCall.toolCall.arguments,
            )
        )
    }

    private suspend fun onToolCompleted(
        result: ExecutedToolCallResult,
        onToolEvent: suspend (AgentToolEvent) -> Unit,
    ) {
        val output = runCatching { JSONObject(result.rawOutput) }.getOrNull()
        val ok = output?.optBoolean("ok", true) ?: true
        diagnosticLogger.event(
            category = "tool",
            event = "tool_end",
            level = if (ok) "info" else "warn",
            requestId = result.id,
            details = mapOf(
                "tool_name" to result.name,
                "ok" to ok,
                "output_chars" to result.visibleOutput.length,
                "message" to output?.optString("errmsg").orEmpty().ifBlank {
                    output?.optString("error").orEmpty()
                },
            ),
        )
        onToolEvent(
            AgentToolEvent(
                id = result.id,
                name = result.name,
                argumentsJson = result.argumentsJson,
                outputJson = result.visibleOutput,
            )
        )
    }

    private suspend fun executeToolCall(
        indexedToolCall: IndexedToolCall,
        settings: AppSettings,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): ExecutedToolCallResult {
        val toolCall = indexedToolCall.toolCall
        val rawOutput = try {
            executeFunctionCall(
                toolCall = toolCall,
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                onSkillActivated = onSkillActivated,
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "tool",
                event = "tool_failed",
                requestId = indexedToolCall.id,
                throwable = throwable,
                details = mapOf("tool_name" to toolCall.name),
            )
            JSONObject().apply {
                put("ok", false)
                put("errmsg", throwable.message ?: "Tool execution failed.")
            }.toString()
        }
        val visibleOutput = sanitizeToolOutputForConversation(toolCall.name, rawOutput)
        return ExecutedToolCallResult(
            id = indexedToolCall.id,
            name = toolCall.name,
            argumentsJson = toolCall.arguments,
            rawOutput = rawOutput,
            visibleOutput = visibleOutput,
        )
    }

    private fun isParallelSafeToolCall(toolName: String): Boolean = when (toolName) {
        "run_tool_batch",
        "activate_skill",
        "aether_config_set",
        "aether_skill_manage",
        "aether_mcp_manage",
        "aether_termux_manage",
        "aether_agent_mode_manage",
        "aether_developer_manage",
        "agent_display" -> false

        else -> true
    }

    private suspend fun executeFunctionCall(
        toolCall: ChatCompletionToolCall,
        settings: AppSettings,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): String {
        return when (toolCall.name) {
            "read" -> filesystemTool.executeRead(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "edit" -> filesystemTool.executeEdit(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "write" -> filesystemTool.executeWrite(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "grep" -> filesystemTool.executeGrep(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "find" -> filesystemTool.executeFind(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "ls" -> filesystemTool.executeLs(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "bash" -> bashTool.execute(
                injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory)
            )
            "fetch_bash_output" -> bashTool.fetchExecution(toolCall.arguments)
            "kill_bash" -> bashTool.killExecution(toolCall.arguments)
            "sleep" -> executeSleep(toolCall.arguments)
            "activate_skill" -> executeActivateSkill(
                argumentsJson = toolCall.arguments,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                onSkillActivated = onSkillActivated,
            )
            "read_skill_resource" -> executeReadSkillResource(
                argumentsJson = toolCall.arguments,
                activeSkills = activeSkills,
            )
            "fetch_web_url" -> executeFetchWebUrl(toolCall.arguments)
            "tavily_search" -> executeTavilySearch(
                settings = settings,
                argumentsJson = toolCall.arguments,
            )
            "aether_config_get",
            "aether_config_set",
            "aether_skill_manage",
            "aether_mcp_manage",
            "aether_termux_manage",
            "aether_agent_mode_manage",
            "aether_developer_manage" -> selfManagementTool.execute(
                toolName = toolCall.name,
                argumentsJson = toolCall.arguments,
            )
            "run_tool_batch" -> executeRunToolBatch(
                argumentsJson = toolCall.arguments,
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                onSkillActivated = onSkillActivated,
            )
            "mcp_list_tools" -> executeMcpListTools(toolCall.arguments)
            "mcp_call_tool" -> executeMcpCallTool(toolCall.arguments)
            "mcp_list_resources" -> executeMcpListResources(toolCall.arguments)
            "mcp_read_resource" -> executeMcpReadResource(toolCall.arguments)
            "mcp_list_prompts" -> executeMcpListPrompts(toolCall.arguments)
            "mcp_get_prompt" -> executeMcpGetPrompt(toolCall.arguments)
            "analyze_image" -> executeAnalyzeImage(
                settings = settings,
                argumentsJson = injectDefaultWorkingDirectory(toolCall.arguments, workspaceDirectory),
            )
            "agent_display" -> agentModeController.execute(
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                argumentsJson = toolCall.arguments,
            )
            else -> if (looksLikeMcpToolCallName(toolCall.name)) {
                mcpClientManager.callToolByName(toolCall.name, toolCall.arguments)
                    .getOrElse { throwable -> toolFailureOutput(throwable, "MCP tool call failed.") }
            } else {
                JSONObject().apply {
                    put("ok", false)
                    put("error", "Unknown tool '${toolCall.name}'.")
                }.toString()
            }
        }
    }

    private fun sanitizeToolOutputForConversation(
        toolName: String,
        output: String,
    ): String {
        if (toolName != "agent_display") return output
        val parsed = runCatching { JSONObject(output) }.getOrNull() ?: return output
        if (!parsed.has("screenshot_base64")) return output
        parsed.remove("screenshot_base64")
        parsed.put("screenshot_injected_into_next_model_request", true)
        return parsed.toString()
    }

    private fun buildAgentDisplayScreenshotMessage(output: String): LlmMessage? {
        val parsed = runCatching { JSONObject(output) }.getOrNull() ?: return null
        if (!parsed.optBoolean("ok")) return null
        val base64Data = parsed.optString("screenshot_base64").takeIf { it.isNotBlank() }
            ?: return null
        val mimeType = parsed.optString("screenshot_mime_type").ifBlank { "image/png" }
        val displayId = parsed.opt("display_id")?.toString().orEmpty()
        return LlmMessage(
            role = "user",
            contentParts = listOf(
                LlmTextPart(
                    "Latest Agent Mode virtual display screenshot" +
                        if (displayId.isNotBlank() && displayId != "null") " for display $displayId." else "."
                ),
                LlmImagePart(
                    mimeType = mimeType,
                    base64Data = base64Data,
                ),
            ),
        )
    }

    private fun toolFailureOutput(
        throwable: Throwable,
        fallbackMessage: String,
        configure: JSONObject.() -> Unit = {},
    ): String {
        if (throwable is CancellationException) throw throwable
        return JSONObject().apply {
            put("ok", false)
            configure()
            put("errmsg", throwable.message ?: fallbackMessage)
        }.toString()
    }

    private suspend fun executeRunToolBatch(
        argumentsJson: String,
        settings: AppSettings,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val mode = arguments.optString("mode").trim().lowercase()
        val runInParallel = when (mode) {
            "parallel", "concurrent", "simultaneous" -> true
            "sequential", "serial", "ordered" -> false
            else -> return JSONObject().apply {
                put("ok", false)
                put("errmsg", "mode must be either 'parallel' or 'sequential'.")
            }.toString()
        }
        val calls = parseRunToolBatchCalls(arguments.optJSONArray("calls"))
        if (calls.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "calls must contain at least one tool call.")
            }.toString()
        }
        val nestedCall = calls.firstOrNull { it.toolName == "run_tool_batch" }
        if (nestedCall != null) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "run_tool_batch cannot call itself.")
            }.toString()
        }
        if (runInParallel) {
            val blockedCall = calls.firstOrNull { !canRunInsideExplicitParallelBatch(it.toolName) }
            if (blockedCall != null) {
                return JSONObject().apply {
                    put("ok", false)
                    put("errmsg", "${blockedCall.toolName} must be run sequentially, not inside a parallel batch.")
                }.toString()
            }
        }

        val results = if (runInParallel) {
            coroutineScope {
                calls.mapIndexed { index, batchCall ->
                    async {
                        index to executeBatchToolCall(
                            batchCall = batchCall,
                            settings = settings,
                            workspaceDirectory = workspaceDirectory,
                            availableSkills = availableSkills,
                            activeSkills = activeSkills,
                            onSkillActivated = onSkillActivated,
                        )
                    }
                }.map { it.await() }
                    .sortedBy { it.first }
                    .map { it.second }
            }
        } else {
            calls.map { batchCall ->
                executeBatchToolCall(
                    batchCall = batchCall,
                    settings = settings,
                    workspaceDirectory = workspaceDirectory,
                    availableSkills = availableSkills,
                    activeSkills = activeSkills,
                    onSkillActivated = onSkillActivated,
                )
            }
        }

        return JSONObject().apply {
            put("ok", results.all { it.optBoolean("ok", true) })
            put("mode", if (runInParallel) "parallel" else "sequential")
            put("results", JSONArray().apply { results.forEach(::put) })
        }.toString()
    }

    private fun parseRunToolBatchCalls(calls: JSONArray?): List<BatchToolCall> {
        if (calls == null) return emptyList()
        return buildList {
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                val toolName = call.optString("tool_name").trim().ifBlank {
                    call.optString("toolName").trim()
                }
                if (toolName.isBlank()) continue
                val argumentsJson = call.optString("arguments_json").trim().ifBlank {
                    call.optString("argumentsJson").trim()
                }
                val rawArguments = call.opt("arguments")
                add(
                    BatchToolCall(
                        toolName = toolName,
                        argumentsJson = argumentsJson.ifBlank {
                            when (rawArguments) {
                            null,
                            JSONObject.NULL -> "{}"
                            is JSONObject -> rawArguments.toString()
                            is String -> rawArguments.ifBlank { "{}" }
                            else -> JSONObject.wrap(rawArguments)?.toString() ?: "{}"
                            }
                        },
                    )
                )
            }
        }
    }

    private suspend fun executeBatchToolCall(
        batchCall: BatchToolCall,
        settings: AppSettings,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): JSONObject {
        val rawOutput = try {
            executeFunctionCall(
                toolCall = ChatCompletionToolCall(
                    id = "",
                    name = batchCall.toolName,
                    arguments = batchCall.argumentsJson,
                ),
                settings = settings,
                workspaceDirectory = workspaceDirectory,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                onSkillActivated = onSkillActivated,
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            toolFailureOutput(throwable, "Tool execution failed.")
        }
        val visibleOutput = sanitizeToolOutputForConversation(batchCall.toolName, rawOutput)
        return JSONObject().apply {
            put("ok", inferToolOutputOk(visibleOutput))
            put("tool_name", batchCall.toolName)
            put("arguments", runCatching { JSONObject(batchCall.argumentsJson) }.getOrDefault(JSONObject()))
            put("output", visibleOutput)
        }
    }

    private fun inferToolOutputOk(output: String): Boolean {
        val parsed = runCatching { JSONObject(output) }.getOrNull() ?: return true
        return parsed.optBoolean("ok", !parsed.optBoolean("err", false))
    }

    private fun canRunInsideExplicitParallelBatch(toolName: String): Boolean = when (toolName) {
        "run_tool_batch",
        "activate_skill",
        "aether_config_set",
        "aether_skill_manage",
        "aether_mcp_manage",
        "aether_termux_manage",
        "aether_agent_mode_manage",
        "aether_developer_manage",
        "agent_display" -> false

        else -> true
    }

    private suspend fun streamChatCompletionWithReconnect(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCallsEnabled: Boolean,
        onParallelToolCallsUnsupported: suspend () -> Unit,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
        onReasoningSummaryDelta: suspend (String) -> Unit,
        onTextReset: suspend () -> Unit,
        onStreamingStatus: suspend (StreamingStatus?) -> Unit,
    ): ChatCompletionResult {
        var reconnectFailures = 0
        var reconnectStatusVisible = false
        var currentParallelToolCallsEnabled = parallelToolCallsEnabled

        while (true) {
            var receivedTextThisAttempt = false
            val result = streamChatCompletionAttempt(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                parallelToolCallsEnabled = currentParallelToolCallsEnabled,
                onTextDelta = { delta ->
                    if (delta.isNotEmpty()) {
                        receivedTextThisAttempt = true
                    }
                    onTextDelta(delta)
                },
                onReasoningDelta = onReasoningDelta,
                onReasoningSummaryDelta = onReasoningSummaryDelta,
                onStreamActivity = {
                    if (reconnectStatusVisible) {
                        reconnectStatusVisible = false
                        onStreamingStatus(null)
                    }
                },
            )

            result.onSuccess {
                if (reconnectStatusVisible) {
                    reconnectStatusVisible = false
                    onStreamingStatus(null)
                }
                return it
            }

            val failure = result.exceptionOrNull() ?: error("Streaming request failed without an exception.")
            if (currentParallelToolCallsEnabled && isParallelToolCallsUnsupportedFailure(failure)) {
                diagnosticLogger.event(
                    category = "llm",
                    event = "parallel_tool_calls_unsupported",
                    level = "warn",
                    details = mapOf(
                        "provider" to settings.provider.storageValue,
                        "model" to settings.modelId,
                        "message" to failure.message.orEmpty(),
                    ),
                )
                if (receivedTextThisAttempt) {
                    onTextReset()
                }
                onParallelToolCallsUnsupported()
                throw ParallelToolCallsUnsupportedRestart()
            }
            if (!shouldReconnectLlmRequest(failure) || reconnectFailures >= LlmReconnectDelayScheduleMillis.size) {
                if (reconnectStatusVisible) {
                    reconnectStatusVisible = false
                    onStreamingStatus(null)
                }
                throw failure
            }

            if (receivedTextThisAttempt) {
                onTextReset()
            }
            val attemptNumber = reconnectFailures + 1
            val reconnectDelayMillis = resolveReconnectDelayMillis(failure, reconnectFailures)
            diagnosticLogger.exception(
                category = "llm",
                event = "request_reconnect_scheduled",
                level = "warn",
                throwable = failure,
                details = mapOf(
                    "provider" to settings.provider.storageValue,
                    "model" to settings.modelId,
                    "attempt" to attemptNumber,
                    "max_attempts" to LlmReconnectDelayScheduleMillis.size,
                    "delay_millis" to reconnectDelayMillis,
                    "received_text_this_attempt" to receivedTextThisAttempt,
                ),
            )
            reconnectStatusVisible = true
            onStreamingStatus(
                StreamingStatus(
                    text = "Reconnecting... $attemptNumber/${LlmReconnectDelayScheduleMillis.size}",
                    detail = formatReconnectFailureDetail(
                        throwable = failure,
                        attemptNumber = attemptNumber,
                        maxAttempts = LlmReconnectDelayScheduleMillis.size,
                        delayMillis = reconnectDelayMillis,
                    ),
                )
            )
            delay(reconnectDelayMillis)
            reconnectFailures += 1
        }
    }

    private fun formatReconnectFailureDetail(
        throwable: Throwable,
        attemptNumber: Int,
        maxAttempts: Int,
        delayMillis: Long,
    ): String = buildString {
        appendLine("Attempt $attemptNumber/$maxAttempts failed.")
        appendLine("Retry delay: ${formatReconnectDelay(delayMillis)}")
        append("Error: ")
        append(formatThrowableSummary(throwable))

        var cause = throwable.cause
        var depth = 0
        while (cause != null && cause !== throwable && depth < 3) {
            appendLine()
            append("Cause: ")
            append(formatThrowableSummary(cause))
            cause = cause.cause
            depth += 1
        }
    }

    private fun formatReconnectDelay(delayMillis: Long): String =
        if (delayMillis % 1000L == 0L) {
            "${delayMillis / 1000L}s"
        } else {
            "${delayMillis}ms"
        }

    private fun formatThrowableSummary(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName.ifBlank { "Throwable" }
        val statusPrefix = (throwable as? LlmHttpException)?.let { "HTTP ${it.statusCode}: " }.orEmpty()
        val message = throwable.message?.trim().orEmpty()
        return if (message.isBlank()) {
            "$statusPrefix$type"
        } else {
            "$statusPrefix$type: $message"
        }
    }

    private suspend fun streamChatCompletionAttempt(
        settings: AppSettings,
        systemPrompt: String,
        conversation: List<JSONObject>,
        tools: List<JSONObject>,
        toolChoice: String?,
        parallelToolCallsEnabled: Boolean,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
        onReasoningSummaryDelta: suspend (String) -> Unit,
        onStreamActivity: suspend () -> Unit,
    ): Result<ChatCompletionResult> = coroutineScope {
        val timeoutMillis = settings.llmInactivityReconnectTimeoutSeconds * 1000L
        val lastActivityAt = AtomicReference(SystemClock.elapsedRealtime())
        val inactivityFailure = AtomicReference<LlmInactivityTimeoutException?>(null)
        val responseJob = async {
            client.streamChatCompletion(
                settings = settings,
                systemPrompt = systemPrompt,
                conversation = conversation,
                tools = tools,
                toolChoice = toolChoice,
                parallelToolCalls = if (parallelToolCallsEnabled && settings.provider.supportsParallelToolCallParameter) {
                    true
                } else {
                    null
                },
                onTextDelta = onTextDelta,
                onReasoningDelta = onReasoningDelta,
                onReasoningSummaryDelta = onReasoningSummaryDelta,
                onStreamActivity = {
                    lastActivityAt.set(SystemClock.elapsedRealtime())
                    onStreamActivity()
                },
            )
        }
        val watchdogJob = launch {
            val checkIntervalMillis = timeoutMillis.coerceAtMost(5_000L).coerceAtLeast(1_000L)
            while (responseJob.isActive) {
                delay(checkIntervalMillis)
                if (!responseJob.isActive) break
                if (SystemClock.elapsedRealtime() - lastActivityAt.get() >= timeoutMillis) {
                    val failure = LlmInactivityTimeoutException(timeoutMillis)
                    inactivityFailure.compareAndSet(null, failure)
                    responseJob.cancel(
                        LlmInactivityTimeoutCancellationException(
                            failure.message ?: "LLM inactivity timeout."
                        )
                    )
                    break
                }
            }
        }

        try {
            responseJob.await()
        } catch (cancellationException: CancellationException) {
            inactivityFailure.get()?.let { return@coroutineScope Result.failure(it) }
            throw cancellationException
        } finally {
            watchdogJob.cancelAndJoin()
        }
    }

    private suspend fun executeMcpListTools(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return mcpClientManager.listTools(
            serverId = extractMcpServerId(arguments),
        ).getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP tools.") }
    }

    private suspend fun executeMcpCallTool(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val serverId = extractMcpServerId(arguments)
        val toolName = arguments.optString("tool_name").trim().ifBlank {
            arguments.optString("toolName").trim()
        }
        val toolArguments = arguments.optJSONObject("arguments") ?: JSONObject()
        if (serverId.isBlank() || toolName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'tool_name' are required.")
            }.toString()
        }
        return mcpClientManager.callTool(serverId, toolName, toolArguments)
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't call the MCP tool.") }
    }

    private suspend fun executeMcpListResources(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return mcpClientManager.listResources(
            serverId = extractMcpServerId(arguments),
        ).getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP resources.") }
    }

    private suspend fun executeMcpReadResource(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val serverId = extractMcpServerId(arguments)
        val uri = arguments.optString("uri").trim()
        if (serverId.isBlank() || uri.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'uri' are required.")
            }.toString()
        }
        return mcpClientManager.readResource(serverId, uri)
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't read MCP resource.") }
    }

    private suspend fun executeMcpListPrompts(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return mcpClientManager.listPrompts(
            serverId = extractMcpServerId(arguments),
        ).getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP prompts.") }
    }

    private suspend fun executeMcpGetPrompt(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val serverId = extractMcpServerId(arguments)
        val promptName = arguments.optString("name").trim()
        val promptArguments = arguments.optJSONObject("arguments") ?: JSONObject()
        if (serverId.isBlank() || promptName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'name' are required.")
            }.toString()
        }
        return mcpClientManager.getPrompt(serverId, promptName, promptArguments)
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't fetch the MCP prompt.") }
    }

    private suspend fun executeActivateSkill(
        argumentsJson: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val requestedName = arguments.optString("name").trim()
        if (requestedName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'name' argument.")
            }.toString()
        }
        val skill = availableSkills.firstOrNull {
            it.name.equals(requestedName, ignoreCase = true) ||
                it.id.equals(requestedName, ignoreCase = true)
        } ?: return JSONObject().apply {
            put("ok", false)
                put("errmsg", "No installed enabled skill matched '$requestedName'.")
            put(
                "available_skills",
                JSONArray().apply { availableSkills.take(32).forEach { put(it.name) } },
            )
        }.toString()

        val activeSkill = skillManager.buildActiveSkillContext(skill).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't activate ${skill.name}.")
        }

        val existingIndex = activeSkills.indexOfFirst { it.skillId == activeSkill.skillId }
        if (existingIndex >= 0) {
            activeSkills[existingIndex] = activeSkill
        } else {
            activeSkills += activeSkill
        }
        onSkillActivated(activeSkill)

        return JSONObject().apply {
            put("ok", true)
            put("name", activeSkill.name)
            put("skill_id", activeSkill.skillId)
            put("description", activeSkill.description)
            put("compatibility", activeSkill.compatibility)
            put("skill_root_path", activeSkill.skillRootPath)
            put("body_markdown", activeSkill.bodyMarkdown)
            put("allowed_tools", JSONArray().apply { activeSkill.allowedTools.forEach(::put) })
            put(
                "resources",
                JSONArray().apply {
                    activeSkill.resourceEntries.forEach { resource ->
                        put(
                            JSONObject().apply {
                                put("path", resource.relativePath)
                                put("kind", resource.kind.storageValue)
                            }
                        )
                    }
                },
            )
            put(
                "stdout",
                "Activated Agent Skill ${activeSkill.name} with ${activeSkill.resourceEntries.size} bundled files.",
            )
        }.toString()
    }

    private fun executeReadSkillResource(
        argumentsJson: String,
        activeSkills: List<ActiveSkillContext>,
    ): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()
        val requestedSkill = arguments.optString("skill").trim().ifBlank {
            arguments.optString("name").trim().ifBlank {
                arguments.optString("skill_id").trim()
            }
        }
        val requestedPath = arguments.optString("relative_path").trim().ifBlank {
            arguments.optString("path").trim()
        }
        if (requestedSkill.isBlank() || requestedPath.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'skill' and 'relative_path' are required.")
            }.toString()
        }
        val skill = activeSkills.firstOrNull {
            it.name.equals(requestedSkill, ignoreCase = true) ||
                it.skillId.equals(requestedSkill, ignoreCase = true)
        } ?: return JSONObject().apply {
            put("ok", false)
            put("errmsg", "No active skill matched '$requestedSkill'. Call activate_skill first.")
            put("active_skills", JSONArray().apply { activeSkills.forEach { put(it.name) } })
        }.toString()

        val normalizedPath = requestedPath.replace('\\', '/').trim('/')
        if (
            normalizedPath.isBlank() ||
            normalizedPath.startsWith("../") ||
            normalizedPath.contains("/../") ||
            File(normalizedPath).isAbsolute
        ) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "relative_path must stay inside the active skill directory.")
            }.toString()
        }

        val root = runCatching { File(skill.skillRootPath).canonicalFile }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Skill root is not readable.")
            }.toString()
        val file = runCatching { File(root, normalizedPath).canonicalFile }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Skill resource path could not be resolved.")
            }.toString()
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Skill resource was not found inside the active skill directory.")
            }.toString()
        }
        if (file.length() > MaxSkillResourceBytes) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Skill resource is too large to read in one tool call.")
                put("size_bytes", file.length())
                put("max_bytes", MaxSkillResourceBytes)
            }.toString()
        }

        val bytes = runCatching { file.readBytes() }.getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't read skill resource.")
        }
        val maxChars = arguments.optInt("max_chars", DefaultSkillResourceMaxChars)
            .coerceIn(1, 100_000)
        val isText = isLikelyTextResource(file.name, bytes)
        return JSONObject().apply {
            put("ok", true)
            put("skill", skill.name)
            put("skill_id", skill.skillId)
            put("relative_path", normalizedPath)
            put("size_bytes", bytes.size)
            if (isText) {
                val text = bytes.toString(Charsets.UTF_8)
                val truncated = text.length > maxChars
                put("content", if (truncated) text.take(maxChars) else text)
                put("truncated", truncated)
                put("encoding", "utf-8")
            } else {
                put("base64", Base64.getEncoder().encodeToString(bytes))
                put("encoding", "base64")
            }
        }.toString()
    }

    private suspend fun executeAnalyzeImage(
        settings: AppSettings,
        argumentsJson: String,
    ): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()

        val path = arguments.optString("path").trim()
        val workingDirectory = arguments.optString("working_directory").trim()
            .ifBlank { arguments.optString("workingDirectory").trim() }
        val prompt = arguments.optString("prompt").trim().ifBlank {
            "Describe the image and answer any relevant details needed for the task."
        }

        if (path.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'path' argument.")
            }.toString()
        }

        val payload = workspaceFileBridge.readWorkspaceFile(
            path = path,
            workingDirectory = workingDirectory,
            byteLimit = MaxAnalyzeImageBytes,
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't read the image from the workspace.") {
                put("path", workspaceFileBridge.resolveTermuxPath(path, workingDirectory))
            }
        }

        val mimeType = guessImageMimeType(payload.absolutePath)
            ?: return JSONObject().apply {
                put("ok", false)
                put("path", payload.absolutePath)
                put("errmsg", "The selected file does not look like a supported image.")
            }.toString()
        if (payload.bytes.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("path", payload.absolutePath)
                put("errmsg", "The selected image is empty.")
            }.toString()
        }

        val response = client.createChatCompletion(
            settings = settings,
            systemPrompt = "You are an image analysis helper for an Android coding agent. Answer only with observations and conclusions grounded in the image and the prompt.",
            conversation = client.buildConversation(
                settings = settings,
                messages = listOf(
                    LlmMessage(
                        role = "user",
                        contentParts = listOf(
                            LlmTextPart(prompt),
                            LlmImagePart(
                                mimeType = mimeType,
                                base64Data = Base64.getEncoder().encodeToString(payload.bytes),
                            ),
                        ),
                    ),
                ),
            ),
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Image analysis request failed.") {
                put("path", payload.absolutePath)
            }
        }

        return JSONObject().apply {
            put("ok", true)
            put("path", payload.absolutePath)
            put("prompt", prompt)
            put("analysis", response.assistantText)
            put("stdout", response.assistantText)
        }.toString()
    }

    private suspend fun executeFetchWebUrl(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()

        val url = arguments.optString("url").trim()
        if (url.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'url' argument.")
            }.toString()
        }

        val maxChars = when {
            arguments.has("max_chars") -> arguments.optInt("max_chars")
            arguments.has("maxChars") -> arguments.optInt("maxChars")
            else -> 20_000
        }

        val page = webToolsClient.fetchUrlAsMarkdown(
            url = url,
            maxChars = maxChars,
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't fetch the URL.") {
                put("url", url)
            }
        }

        return JSONObject().apply {
            put("ok", true)
            put("request_url", page.requestUrl)
            put("final_url", page.finalUrl)
            put("title", page.title)
            put("content_type", page.contentType)
            put("markdown", page.markdown)
            put("truncated", page.wasTruncated)
            put(
                "stdout",
                buildString {
                    append("Fetched ")
                    append(page.title.ifBlank { page.finalUrl })
                    if (page.wasTruncated) {
                        append(" (truncated)")
                    }
                },
            )
        }.toString()
    }

    private suspend fun executeTavilySearch(
        settings: AppSettings,
        argumentsJson: String,
    ): String {
        if (settings.tavilyApiKey.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put(
                    "errmsg",
                    "Tavily API key is not configured. Add it in Settings > Web Tools before using tavily_search.",
                )
            }.toString()
        }

        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()

        val query = arguments.optString("query").trim()
        if (query.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'query' argument.")
            }.toString()
        }

        val response = webToolsClient.searchTavily(
            apiKey = settings.tavilyApiKey,
            baseUrl = settings.tavilyBaseUrl,
            request = TavilySearchRequest(
                query = query,
                topic = arguments.stringValue("topic").ifBlank { "general" },
                searchDepth = arguments.stringValue("search_depth", "searchDepth").ifBlank { "basic" },
                maxResults = arguments.intValue("max_results", "maxResults") ?: 5,
                timeRange = arguments.stringValue("time_range", "timeRange").ifBlank { null },
                includeAnswer = arguments.booleanValue("include_answer", "includeAnswer") ?: true,
                includeRawContent = arguments.booleanValue("include_raw_content", "includeRawContent") ?: false,
                includeDomains = arguments.stringArrayValue("include_domains", "includeDomains"),
                excludeDomains = arguments.stringArrayValue("exclude_domains", "excludeDomains"),
                country = arguments.stringValue("country").ifBlank { null },
                startDate = arguments.stringValue("start_date", "startDate").ifBlank { null },
                endDate = arguments.stringValue("end_date", "endDate").ifBlank { null },
            ),
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Tavily search failed.") {
                put("query", query)
            }
        }

        response.put("ok", true)
        response.put("stdout", buildTavilySearchSummary(response))
        return response.toString()
    }

    private suspend fun executeSleep(argumentsJson: String): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Arguments were not valid JSON.")
            }.toString()

        val durationMillis = arguments.takeIf { it.has("duration_ms") }?.optLong("duration_ms")
            ?: arguments.takeIf { it.has("durationMs") }?.optLong("durationMs")
            ?: -1L

        if (durationMillis < 0L) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'duration_ms' argument.")
            }.toString()
        }
        if (durationMillis > MaxSleepDurationMillis) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "'duration_ms' must be between 0 and $MaxSleepDurationMillis.")
            }.toString()
        }

        delay(durationMillis)
        return JSONObject().apply {
            put("ok", true)
            put("duration_ms", durationMillis)
            put("stdout", "Slept for ${durationMillis}ms.")
        }.toString()
    }

    private fun injectDefaultWorkingDirectory(
        argumentsJson: String,
        workspaceDirectory: String,
    ): String {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull() ?: return argumentsJson
        if (!arguments.has("workingDirectory") && !arguments.has("working_directory")) {
            arguments.put("working_directory", workspaceDirectory)
        }
        return arguments.toString()
    }

    private fun guessImageMimeType(path: String): String? {
        val guessedMimeType = workspaceFileBridge.guessMimeType(path)
        if (guessedMimeType.startsWith("image/")) return guessedMimeType

        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg",
            "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> null
        }
    }

    private fun isLikelyTextResource(fileName: String, bytes: ByteArray): Boolean {
        if (bytes.any { it == 0.toByte() }) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension in TextSkillResourceExtensions) return true
        val sample = bytes.take(512)
        if (sample.isEmpty()) return true
        return sample.count { byte ->
            val value = byte.toInt() and 0xff
            value == 9 || value == 10 || value == 13 || value in 32..126
        } >= sample.size * 9 / 10
    }

    private fun renderAvailableSkillLines(
        skills: List<InstalledSkill>,
    ): Pair<List<String>, Int> {
        val lines = mutableListOf<String>()
        var usedChars = 0
        var omitted = 0
        skills.sortedBy { it.name.lowercase() }.forEach { skill ->
            val minimumLine = renderAvailableSkillLine(skill, description = "")
            if (usedChars + minimumLine.length + 1 > SkillMetadataContextBudgetChars) {
                omitted += 1
                return@forEach
            }

            val fullLine = renderAvailableSkillLine(skill, skill.description)
            val remaining = SkillMetadataContextBudgetChars - usedChars - 1
            val line = if (fullLine.length <= remaining) {
                fullLine
            } else {
                val prefix = "- ${skill.name}: "
                val suffix = " (file: ${skill.skillMdPath.replace('\\', '/')})"
                val descriptionBudget = (remaining - prefix.length - suffix.length)
                    .coerceAtLeast(0)
                if (descriptionBudget > 0) {
                    prefix + skill.description.take(descriptionBudget) + suffix
                } else {
                    minimumLine
                }
            }
            usedChars += line.length + 1
            lines += line
        }
        return lines to omitted
    }

    private fun renderAvailableSkillLine(
        skill: InstalledSkill,
        description: String,
    ): String {
        val path = skill.skillMdPath.replace('\\', '/')
        return if (description.isBlank()) {
            "- ${skill.name}: (file: $path)"
        } else {
            "- ${skill.name}: $description (file: $path)"
        }
    }

    private fun buildAgentInstructions(
        systemPrompt: String,
        workspaceDirectory: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: List<ActiveSkillContext>,
        mcpToolBindings: List<McpToolBinding>,
        exposeNamespacedMcpTools: Boolean,
        agentModeEnabled: Boolean,
        parallelToolCallsEnabled: Boolean,
        basicToolCompatibilityMode: Boolean,
    ): String = buildString {
        val trimmedPrompt = systemPrompt.trim()
        if (trimmedPrompt.isNotBlank()) {
            append(trimmedPrompt)
            append("\n\n")
        }
        append(
            "You are running inside Aether agent mode on Android. " +
                "Use available tools instead of guessing about the local device state. " +
                "The current session workspace is $workspaceDirectory, which corresponds to ~/.aether/workspaces for this chat. " +
                "All user-uploaded files are copied into that workspace, usually under uploads/. " +
                "User-uploaded workspace images are not inserted into model vision automatically; call analyze_image on a workspace path when you need to inspect one. " +
                "You can show inline media in replies with Markdown images and Mermaid. For images, use ![alt](url){width=75% height=280 scroll=true show-all=false fit=contain}; width accepts percentages or dp, height/min-height/max-height accept dp, scroll/show-all accept true or false, and fit accepts contain or cover. " +
                "For Mermaid, use fenced blocks like ```mermaid {height=360 scroll=true show-all=false}\\ngraph TD\\nA-->B\\n``` and the same width/height/min-height/max-height/scroll/show-all attributes apply. Users can tap rendered images to enlarge them. " +
                "Use fetch_web_url when you need the contents of a specific webpage or the user gives you a URL. " +
                "Use tavily_search for public-web discovery and fresh online information. " +
                "For tavily_search, prefer a simple query plus include_domains or max_results when useful. " +
                "Use either time_range or start_date/end_date, never both. " +
                "Only set country when you know Tavily supports that lowercase country value, such as china or united states; otherwise leave it null. " +
                "Use snake_case tavily_search keys only; do not invent duplicate camelCase aliases. " +
                "When the user asks for help diagnosing or repairing Aether itself, use the aether_* self-management tools to inspect and update allowed app settings directly. " +
                "These tools may manage general language/theme settings, Web Tools, Reliability, Agent Skills, MCP servers, Termux setup, Agent Mode authorization, and developer diagnostics. " +
                "Never try to modify LLM provider, model, base URL, provider API key, or default model configuration; Aether intentionally does not expose those through self-management tools. " +
                "Prefer aether_skill_manage for installing, enabling, disabling, or removing skills, and aether_mcp_manage for adding, editing, enabling, disabling, or removing MCP servers. " +
                "Use aether_termux_manage and aether_agent_mode_manage for setup/status repair before asking the user to perform manual steps. " +
                "Prefer read, edit, write, grep, find, and ls for filesystem work. " +
                "If a tool call omits working_directory, Aether will run it in the current session workspace by default. " +
                "The read tool reads file contents with optional line offset and limit. " +
                "The edit tool applies exact text replacements and should be used for precise file edits. " +
                "For exactly one edit, call edit with {path, oldText, newText} and omit edits. " +
                "For multiple edits, call edit with {path, edits:[{oldText, newText}, ...]} and omit top-level oldText/newText. " +
                "Never send both edit formats together unless you intentionally want the edits array to be used. " +
                "The write tool creates or overwrites a file with full contents. " +
                "The grep tool searches file contents. The find tool matches file paths by glob pattern. " +
                "The ls tool lists directory contents. " +
                "All filesystem tools accept ~ or ~/... to mean the Termux home directory. " +
                "For non-trivial or multi-step work, interleave concise assistant updates with tool use: briefly say what you are about to inspect or do, call the relevant tool or independent tool batch for that step, then continue with another short update before the next distinct tool step. " +
                "Keep these updates short and skip them for obvious single-tool lookups, purely mechanical polling, or when the user asks for no narration. " +
                "The bash tool runs inside Termux on the user's phone. It watches the command for up to 45 seconds. " +
                "If the command finishes quickly, bash returns the final structured JSON with stdout, stderr, exit_code, err, errmsg, duration_ms, command, and working_directory. " +
                "If the command is still running after 45 seconds, bash returns status=running plus run_id and the latest stdout/stderr snapshot without stopping the command. " +
                "When bash returns status=running, use sleep to wait, then call fetch_bash_output with the same run_id to poll for more logs or completion. " +
                "If a long-running command is stuck or no longer needed, call kill_bash with the run_id. " +
                "Use sleep instead of busy waiting. " +
                "Use bash for shell commands, scripts, and tasks that are not covered by the specialized filesystem tools. " +
                "If the user asks you to run a bash, shell, terminal, or Termux command, you must call the bash tool instead of describing what they should run manually. " +
                "When you create or modify a file that the user should download, include a Markdown link that uses file:// with the absolute workspace path, for example [report.txt](file://$workspaceDirectory/report.txt). " +
                "Only claim you executed shell commands if you actually called bash. " +
                "After using tools, summarize the result clearly for the user."
        )
        if (agentModeEnabled) {
            append("\n\n")
            append(
                "Agent Mode is enabled for this chat. Use agent_display to operate an isolated Android virtual display, not the user's main screen. " +
                    "Coordinates for tap and swipe are normalized from 0 to 1000, matching the Ruto/AutoGLM convention. " +
                    "Call agent_display with action=list_apps when you need the installed app name to package name mapping, then call action=start before operating apps, action=launch to open an app by package name or exact label, and action=screenshot after visible changes. " +
                    "During multi-step Agent Mode work, interleave concise assistant text between display actions so the user can see what you are doing and why, such as the next app, screen, or decision you are checking. " +
                    "After each agent_display action that captures the display, the latest screenshot is automatically inserted into the next model request as an image, following the Ruto-GLM workflow. Use that image directly instead of calling analyze_image for Agent Mode screenshots. " +
                    "Do not use Agent Mode tools when the user only wants a normal chat answer."
            )
        }
        append("\n\n")
        append(
            if (basicToolCompatibilityMode) {
                "Basic tool compatibility mode is enabled for this model endpoint. " +
                    "Call at most one normal top-level function tool per assistant turn. " +
                    "Do not request native parallel tool calls, batch tool calls, or provider-specific tool call modes."
            } else if (parallelToolCallsEnabled) {
                "Parallel tool calls are available for this model endpoint. " +
                    "When independent operations belong to the same explained step and should start at the same time, issue multiple normal top-level tool calls directly in one assistant turn. " +
                    "When order matters, call only the next required tool and wait for its result before choosing the following tool. " +
                    "The fallback batch tool is not available while native parallel tool calls are supported."
            } else {
                "This model endpoint rejected Aether's native parallel tool call request shape earlier. " +
                    "Call at most one normal top-level tool per assistant turn. " +
                    "When multiple independent operations belong to the same explained step, use run_tool_batch as the single top-level tool call and choose mode=parallel for simultaneous execution or mode=sequential when order matters."
            }
        )
        if (availableSkills.isNotEmpty()) {
            val (availableSkillLines, omittedSkillCount) = renderAvailableSkillLines(availableSkills)
            append("\n\n")
            append(
                "Installed Agent Skills are available in this app. " +
                    "Decide autonomously whether a task matches a skill, or whether the user explicitly named one. " +
                    "When it does, call activate_skill before following that skill's instructions. " +
                    "The visible list is metadata only and may be budget-truncated; activate_skill is the framework-level disclosure step that reads SKILL.md into context. " +
                    "Do not use dollar-sign syntax for skills in Aether; the user-facing manual path remains the plus button. " +
                    "Do not pretend a skill is active until you have called activate_skill."
            )
            append("\n<available_skills>")
            availableSkillLines.forEach { line ->
                append("\n")
                append(line)
            }
            if (omittedSkillCount > 0) {
                append("\n- ")
                append(omittedSkillCount)
                append(" additional skills omitted from this model-visible metadata list because of the context budget.")
            }
            append("\n</available_skills>")
        }
        if (activeSkills.isNotEmpty()) {
            append("\n\n")
            append("The following Agent Skills are already active for this chat session and must remain in effect:")
            activeSkills.forEach { skill ->
                append("\n<active_skill name=\"")
                append(skill.name.replace("\"", "'"))
                append("\">")
                if (skill.description.isNotBlank()) {
                    append("\n<description>")
                    append(skill.description)
                    append("</description>")
                }
                if (skill.compatibility.isNotBlank()) {
                    append("\n<compatibility>")
                    append(skill.compatibility)
                    append("</compatibility>")
                }
                if (skill.allowedTools.isNotEmpty()) {
                    append("\n<allowed_tools>")
                    skill.allowedTools.forEach { tool ->
                        append("\n- ")
                        append(tool)
                    }
                    append("\n</allowed_tools>")
                }
                append("\n<skill_root>")
                append(skill.skillRootPath)
                append("</skill_root>")
                if (skill.resourceEntries.isNotEmpty()) {
                    append("\n<resources>")
                    skill.resourceEntries.forEach { resource ->
                        append("\n- ")
                        append(resource.relativePath)
                        append(" (")
                        append(resource.kind.storageValue)
                        append(')')
                    }
                    append("\n</resources>")
                    append("\nUse read_skill_resource to read only the specific bundled files needed for the task.")
                }
                append("\n<instructions>\n")
                append(skill.bodyMarkdown)
                append("\n</instructions>")
                append("\n</active_skill>")
            }
        }
        val mcpSnapshots = mcpClientManager.snapshots()
        if (mcpSnapshots.isNotEmpty()) {
            append("\n\n")
            append(
                "Connected MCP servers are also available in this session. " +
                    "Use mcp_list_tools to inspect callable MCP tools. " +
                    "Use mcp_call_tool to invoke an MCP tool with server_id, tool_name, and arguments. " +
                    "Use mcp_list_resources and mcp_read_resource for resources. " +
                    "Use mcp_list_prompts and mcp_get_prompt for prompts. " +
                    "Never invent tool names such as server:tool."
            )
            if (exposeNamespacedMcpTools) {
                append(" If exact MCP call names are listed below as call_name=..., you may also use those exact names directly.")
            }
            append("\n<mcp_servers>")
            mcpSnapshots.forEach { snapshot ->
                append("\n- ")
                append(snapshot.config.displayName)
                append(" [")
                append(snapshot.config.id)
                append("]")
                if (snapshot.tools.isNotEmpty()) {
                    append(" tools=")
                    append(snapshot.tools.joinToString { it.toolName })
                }
                if (snapshot.resources.isNotEmpty()) {
                    append(" resources=")
                    append(snapshot.resources.size)
                }
                if (snapshot.prompts.isNotEmpty()) {
                    append(" prompts=")
                    append(snapshot.prompts.size)
                }
            }
            append("\n</mcp_servers>")
            if (mcpToolBindings.isNotEmpty()) {
                append("\n<mcp_tools>")
                mcpToolBindings.forEach { binding ->
                    append("\n- ")
                    append(binding.serverId)
                    append("/")
                    append(binding.toolName)
                    if (exposeNamespacedMcpTools) {
                        append(" call_name=")
                        append(binding.namespacedToolName)
                    }
                    if (binding.description.isNotBlank()) {
                        append(": ")
                        append(binding.description)
                    }
                }
                append("\n</mcp_tools>")
            }
        }
    }

    private fun shouldForceToolUse(latestUserText: String): Boolean {
        val normalized = latestUserText.lowercase()
        if (normalized.isBlank()) return false

        return listOf(
            "bash",
            "shell",
            "termux",
            "terminal",
            "run pwd",
            "run ls",
            "run cat",
            "execute ",
            "command ",
            "read ",
            "open file",
            "edit file",
            "modify file",
            "write file",
            "create file",
            "overwrite file",
            "grep ",
            "search files",
            "find file",
            "list directory",
            "analyze image",
            "inspect image",
            "search the web",
            "search web",
            "browse the web",
            "browse web",
            "look up online",
            "fetch url",
            "web url",
            "tavily",
            "https://",
            "http://",
        ).any(normalized::contains)
    }

    private fun buildTavilySearchSummary(response: JSONObject): String = buildString {
        val answer = response.optString("answer").trim()
        if (answer.isNotBlank()) {
            append(answer)
        }

        val results = response.optJSONArray("results") ?: JSONArray()
        if (results.length() > 0) {
            if (isNotEmpty()) append("\n\n")
            append("Top results:")
            for (index in 0 until minOf(results.length(), 5)) {
                val result = results.optJSONObject(index) ?: continue
                append("\n")
                append(index + 1)
                append(". ")
                append(result.optString("title").ifBlank { result.optString("url") })
                val url = result.optString("url").trim()
                if (url.isNotBlank()) {
                    append(" - ")
                    append(url)
                }
                val snippet = result.optString("content").trim()
                if (snippet.isNotBlank()) {
                    append("\n")
                    append(snippet.take(280))
                }
            }
        }
    }

    private fun JSONObject.stringValue(
        primaryKey: String,
        aliasKey: String? = null,
    ): String {
        val primary = cleanOptionalString(primaryKey)
        if (primary.isNotBlank()) return primary
        return aliasKey?.let { cleanOptionalString(it) }.orEmpty()
    }

    private fun JSONObject.intValue(
        primaryKey: String,
        aliasKey: String? = null,
    ): Int? = when {
        hasUsableValue(primaryKey) -> optInt(primaryKey)
        aliasKey != null && hasUsableValue(aliasKey) -> optInt(aliasKey)
        else -> null
    }

    private fun JSONObject.booleanValue(
        primaryKey: String,
        aliasKey: String? = null,
    ): Boolean? = when {
        hasUsableValue(primaryKey) -> optBoolean(primaryKey)
        aliasKey != null && hasUsableValue(aliasKey) -> optBoolean(aliasKey)
        else -> null
    }

    private fun JSONObject.stringArrayValue(
        primaryKey: String,
        aliasKey: String? = null,
    ): List<String> {
        val array = when {
            hasUsableValue(primaryKey) -> optJSONArray(primaryKey)
            aliasKey != null && hasUsableValue(aliasKey) -> optJSONArray(aliasKey)
            else -> null
        } ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    private fun JSONObject.hasUsableValue(key: String): Boolean =
        has(key) && !isNull(key) && !cleanOptionalString(key).equals("null", ignoreCase = true)

    private fun JSONObject.cleanOptionalString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key).trim()
        return value.takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
            .orEmpty()
    }

    private fun buildReadToolDefinition(): JSONObject = buildToolDefinition(
        name = "read",
        description = "Read a text file from Termux with optional line-based offset and limit. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The file path to read."))
            put("offset", integerProperty("Optional zero-based line offset to start reading from."))
            put("limit", integerProperty("Optional maximum number of lines to return."))
            put(
                "showLineNumbers",
                booleanProperty("Whether stdout should prefix each returned line with its original 1-based line number."),
            )
            put(
                "show_line_numbers",
                booleanProperty("Alias of showLineNumbers."),
            )
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path"),
    )

    private fun buildEditToolDefinition(): JSONObject = buildToolDefinition(
        name = "edit",
        description = "Precisely edit a text file using exact oldText/newText replacements. For one edit use only oldText/newText. For multiple edits use only edits[]. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The file path to edit."))
            put("oldText", stringProperty("For a single edit only, the exact text to replace. Omit this when using edits[]."))
            put("newText", stringProperty("For a single edit only, the replacement text. Omit this when using edits[]."))
            put(
                "edits",
                JSONObject().apply {
                    put("type", "array")
                    put(
                        "description",
                        "For multiple edits only, a list of non-overlapping precise replacements. Omit top-level oldText/newText when using this.",
                    )
                    put(
                        "items",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("oldText", stringProperty("The exact text to replace."))
                                    put("newText", stringProperty("The replacement text."))
                                }
                            )
                            put("required", JSONArray().put("oldText").put("newText"))
                            put("additionalProperties", false)
                        }
                    )
                }
            )
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path"),
    )

    private fun buildWriteToolDefinition(): JSONObject = buildToolDefinition(
        name = "write",
        description = "Create a new text file or completely overwrite an existing text file. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The file path to create or overwrite."))
            put("content", stringProperty("The full file contents to write."))
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path", "content"),
    )

    private fun buildGrepToolDefinition(): JSONObject = buildToolDefinition(
        name = "grep",
        description = "Search for text or a regex pattern inside a file or directory tree. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The file or directory path to search."))
            put("pattern", stringProperty("The text or regex pattern to search for."))
            put("isRegex", booleanProperty("Whether pattern should be treated as a regex."))
            put("caseSensitive", booleanProperty("Whether the search should be case-sensitive."))
            put("maxResults", integerProperty("Optional maximum number of matches to return."))
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path", "pattern"),
    )

    private fun buildFindToolDefinition(): JSONObject = buildToolDefinition(
        name = "find",
        description = "Find files or directories by glob pattern. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The directory path to search in."))
            put("pattern", stringProperty("The glob pattern to match, such as *.kt."))
            put(
                "type",
                stringProperty("Optional match type: any, file, or directory."),
            )
            put("caseSensitive", booleanProperty("Whether the glob match should be case-sensitive."))
            put("maxDepth", integerProperty("Optional maximum search depth."))
            put("maxResults", integerProperty("Optional maximum number of results to return."))
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path", "pattern"),
    )

    private fun buildLsToolDefinition(): JSONObject = buildToolDefinition(
        name = "ls",
        description = "List the contents of a directory or inspect a file path. path accepts ~ or ~/... for the Termux home directory.",
        properties = JSONObject().apply {
            put("path", stringProperty("The file or directory path to list."))
            put("recursive", booleanProperty("Whether to list recursively."))
            put("includeHidden", booleanProperty("Whether to include hidden files and directories."))
            put("maxDepth", integerProperty("Optional maximum recursion depth."))
            put("maxEntries", integerProperty("Optional maximum number of entries to return."))
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path"),
    )

    private fun buildBashToolDefinition(): JSONObject = JSONObject().apply {
        put("type", "function")
        put(
            "function",
            JSONObject().apply {
                put("name", "bash")
                put(
                    "description",
                    "Execute a bash command inside Termux on the user's Android device. The tool watches the command for up to 45 seconds. If the command is still running after that, it returns status=running, a run_id, and the latest stdout/stderr snapshot without interrupting the command. working_directory accepts ~ or ~/... for the Termux home directory."
                )
                put(
                    "parameters",
                    buildStrictToolParameters(
                        properties = JSONObject().apply {
                            put(
                                "command",
                                JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The bash command or script to execute.")
                                }
                            )
                            put(
                                "working_directory",
                                JSONObject().apply {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Optional working directory inside Termux, for example ~/.aether/workspaces/<session-id>."
                                    )
                                }
                            )
                            put(
                                "workingDirectory",
                                JSONObject().apply {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Alias of working_directory."
                                    )
                                }
                            )
                        },
                        required = listOf("command"),
                    )
                )
                put("strict", true)
            }
        )
    }

    private fun buildFetchBashOutputToolDefinition(): JSONObject = buildToolDefinition(
        name = "fetch_bash_output",
        description = "Fetch the latest stdout/stderr snapshot and status for a previously started long-running bash command by run_id.",
        properties = JSONObject().apply {
            put("run_id", stringProperty("The run_id returned by bash when it reported status=running."))
            put("runId", stringProperty("Alias of run_id."))
            put("tail_bytes", integerProperty("Optional maximum number of bytes to return from the end of stdout and stderr."))
            put("tailBytes", integerProperty("Alias of tail_bytes."))
        },
        required = listOf("run_id"),
    )

    private fun buildKillBashToolDefinition(): JSONObject = buildToolDefinition(
        name = "kill_bash",
        description = "Stop a previously started long-running bash command by run_id and return its latest logs.",
        properties = JSONObject().apply {
            put("run_id", stringProperty("The run_id returned by bash when it reported status=running."))
            put("runId", stringProperty("Alias of run_id."))
            put("tail_bytes", integerProperty("Optional maximum number of bytes to return from the end of stdout and stderr."))
            put("tailBytes", integerProperty("Alias of tail_bytes."))
        },
        required = listOf("run_id"),
    )

    private fun buildSleepToolDefinition(): JSONObject = buildToolDefinition(
        name = "sleep",
        description = "Pause the agent for a fixed duration so a long-running bash command can continue before you fetch logs again.",
        properties = JSONObject().apply {
            put("duration_ms", integerProperty("How long to sleep in milliseconds. Use this before polling a running bash command again."))
            put("durationMs", integerProperty("Alias of duration_ms."))
        },
        required = listOf("duration_ms"),
    )

    private fun buildAnalyzeImageToolDefinition(): JSONObject = buildToolDefinition(
        name = "analyze_image",
        description = "Analyze an image file from the current workspace with model vision. Use this instead of assuming what an uploaded image contains.",
        properties = JSONObject().apply {
            put("path", stringProperty("The image file path to inspect. Relative paths resolve from the current workspace."))
            put("prompt", stringProperty("Optional question or instruction for what to inspect in the image."))
            put(
                "workingDirectory",
                stringProperty("Optional working directory used to resolve relative paths."),
            )
            put(
                "working_directory",
                stringProperty("Alias of workingDirectory."),
            )
        },
        required = listOf("path"),
    )

    private fun buildFetchWebUrlToolDefinition(): JSONObject = buildToolDefinition(
        name = "fetch_web_url",
        description = "Fetch a specific HTTP or HTTPS URL and return the page content converted to Markdown. Use this when the user gives you a URL or you need the contents of one page.",
        properties = JSONObject().apply {
            put("url", stringProperty("The HTTP or HTTPS URL to fetch."))
            put("max_chars", integerProperty("Optional maximum number of Markdown characters to return."))
            put("maxChars", integerProperty("Alias of max_chars."))
        },
        required = listOf("url"),
    )

    private fun buildTavilySearchToolDefinition(): JSONObject = buildToolDefinition(
        name = "tavily_search",
        description = "Search the public web with Tavily. Requires a Tavily API key in Settings > Web Tools. Use this for web discovery or current online information.",
        properties = JSONObject().apply {
            put("query", stringProperty("The search query to execute."))
            put("topic", stringProperty("Optional search topic: general, news, or finance."))
            put("search_depth", stringProperty("Optional search depth: basic, advanced, fast, or ultra-fast."))
            put("max_results", integerProperty("Optional maximum number of results to return, between 1 and 20."))
            put("time_range", stringProperty("Optional recency filter, such as day, week, month, or year. Do not combine this with start_date or end_date."))
            put("include_answer", booleanProperty("Whether Tavily should include a synthesized answer."))
            put("include_raw_content", booleanProperty("Whether each result should include raw page content in Markdown."))
            put("include_domains", stringArrayProperty("Optional list of domains to include."))
            put("exclude_domains", stringArrayProperty("Optional list of domains to exclude."))
            put("country", stringProperty("Optional lowercase Tavily country value for localized general search, such as united states or china. Leave null when unsure."))
            put("start_date", stringProperty("Optional start date in YYYY-MM-DD format. Do not combine this with time_range."))
            put("end_date", stringProperty("Optional end date in YYYY-MM-DD format. Do not combine this with time_range."))
        },
        required = listOf("query"),
    )

    private fun buildRunToolBatchToolDefinition(): JSONObject = buildToolDefinition(
        name = "run_tool_batch",
        description = "Submit multiple Aether tool calls in one top-level tool call. mode=parallel runs them at the same time; mode=sequential starts the next call only after the previous call finishes. If native parallel top-level tool calls are available, prefer direct multiple tool calls for simultaneous work and use this tool for ordered sequential batches or explicit fallback batching.",
        properties = JSONObject().apply {
            put(
                "mode",
                stringProperty("Execution mode: parallel for simultaneous execution, or sequential for one-after-another runtime execution."),
            )
            put(
                "calls",
                JSONObject().apply {
                    put("type", "array")
                    put("description", "Tool calls to execute.")
                    put(
                        "items",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("tool_name", stringProperty("The Aether tool name to call, such as read, bash, grep, edit, or mcp_call_tool."))
                                    put(
                                        "arguments_json",
                                        stringProperty("JSON object string containing the arguments for that tool, for example {\"path\":\"README.md\"}. Use {} when the tool has no arguments."),
                                    )
                                },
                            )
                            put("required", JSONArray().put("tool_name").put("arguments_json"))
                            put("additionalProperties", false)
                        },
                    )
                },
            )
        },
        required = listOf("mode", "calls"),
    )

    private fun buildAgentModeToolDefinition(): JSONObject = buildToolDefinition(
        name = "agent_display",
        description = "Operate Aether Agent Mode on an isolated Android virtual display. Use this only when Agent Mode is selected in the chat composer.",
        properties = JSONObject().apply {
            put("action", stringProperty("One of: list_apps, start, status, launch, tap, swipe, key, text, screenshot, stop."))
            put("query", stringProperty("For list_apps: optional app label, package, or activity filter."))
            put("include_system", booleanProperty("For list_apps: whether to include system apps. Defaults to true."))
            put("includeSystem", booleanProperty("Alias of include_system."))
            put("max_results", integerProperty("For list_apps: maximum number of apps to return. Defaults to 500."))
            put("maxResults", integerProperty("Alias of max_results."))
            put("target", stringProperty("For launch: package name or exact app label."))
            put("x", integerProperty("For tap: normalized X coordinate from 0 to 1000."))
            put("y", integerProperty("For tap: normalized Y coordinate from 0 to 1000."))
            put("x1", integerProperty("For swipe: normalized start X coordinate from 0 to 1000."))
            put("y1", integerProperty("For swipe: normalized start Y coordinate from 0 to 1000."))
            put("x2", integerProperty("For swipe: normalized end X coordinate from 0 to 1000."))
            put("y2", integerProperty("For swipe: normalized end Y coordinate from 0 to 1000."))
            put("duration_ms", integerProperty("For swipe: gesture duration in milliseconds."))
            put("durationMs", integerProperty("Alias of duration_ms."))
            put("key", stringProperty("For key: Android key code name or number, such as BACK, HOME, ENTER, or 4."))
            put("text", stringProperty("For text: text to type into the focused field."))
        },
        required = listOf("action"),
    )

    private fun buildActivateSkillToolDefinition(): JSONObject = buildToolDefinition(
        name = "activate_skill",
        description = "Load an installed Agent Skill into the current chat session. Use this when an installed skill matches the task or the user explicitly requests one.",
        properties = JSONObject().apply {
            put("name", stringProperty("The installed skill name or id to activate."))
        },
        required = listOf("name"),
    )

    private fun buildReadSkillResourceToolDefinition(): JSONObject = buildToolDefinition(
        name = "read_skill_resource",
        description = "Read a bundled file from an already active Agent Skill by relative path. Use this for progressive disclosure when a skill's SKILL.md tells you to inspect references, scripts, assets, or agents metadata.",
        properties = JSONObject().apply {
            put("skill", stringProperty("The active skill name or id."))
            put("relative_path", stringProperty("The resource path relative to the skill root, such as references/guide.md or scripts/run.py."))
            put("path", stringProperty("Alias of relative_path."))
            put("max_chars", integerProperty("Optional maximum number of UTF-8 text characters to return."))
        },
        required = listOf("skill"),
    )

    private fun buildMcpGenericToolDefinitions(): List<JSONObject> = listOf(
        buildToolDefinition(
            name = "mcp_list_tools",
            description = "List callable MCP tools across all connected servers or for one server.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("Optional MCP server id to filter by."))
                put("serverId", stringProperty("Alias of server_id."))
            },
            required = emptyList(),
        ),
        buildToolDefinition(
            name = "mcp_call_tool",
            description = "Call an MCP tool by server id and tool name.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("The MCP server id to call."))
                put("serverId", stringProperty("Alias of server_id."))
                put("tool_name", stringProperty("The MCP tool name to invoke."))
                put("toolName", stringProperty("Alias of tool_name."))
                put(
                    "arguments",
                    JSONObject().apply {
                        put("type", "object")
                        put("description", "Arguments to pass to the MCP tool.")
                        put("additionalProperties", true)
                    },
                )
            },
            required = listOf("server_id", "tool_name"),
        ),
        buildToolDefinition(
            name = "mcp_list_resources",
            description = "List available MCP resources across all connected servers or for one server.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("Optional MCP server id to filter by."))
                put("serverId", stringProperty("Alias of server_id."))
            },
            required = emptyList(),
        ),
        buildToolDefinition(
            name = "mcp_read_resource",
            description = "Read a specific MCP resource from a connected server.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("The MCP server id to read from."))
                put("serverId", stringProperty("Alias of server_id."))
                put("uri", stringProperty("The MCP resource URI."))
            },
            required = listOf("server_id", "uri"),
        ),
        buildToolDefinition(
            name = "mcp_list_prompts",
            description = "List available MCP prompts across all connected servers or for one server.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("Optional MCP server id to filter by."))
                put("serverId", stringProperty("Alias of server_id."))
            },
            required = emptyList(),
        ),
        buildToolDefinition(
            name = "mcp_get_prompt",
            description = "Fetch a rendered MCP prompt from a connected server.",
            properties = JSONObject().apply {
                put("server_id", stringProperty("The MCP server id to query."))
                put("serverId", stringProperty("Alias of server_id."))
                put("name", stringProperty("The MCP prompt name."))
                put(
                    "arguments",
                    JSONObject().apply {
                        put("type", "object")
                        put("description", "Optional prompt arguments.")
                        put("additionalProperties", true)
                    },
                )
            },
            required = listOf("server_id", "name"),
        ),
    )

    private fun buildMcpToolDefinition(binding: McpToolBinding): JSONObject = JSONObject().apply {
        put("type", "function")
        put(
            "function",
            JSONObject().apply {
                put("name", binding.namespacedToolName)
                put(
                    "description",
                    buildString {
                        append("Call MCP tool ")
                        append(binding.serverName)
                        append("/")
                        append(binding.toolName)
                        if (binding.description.isNotBlank()) {
                            append(": ")
                            append(binding.description)
                        }
                    },
                )
                put(
                    "parameters",
                    JSONObject(binding.inputSchema.toString()).apply {
                        if (!has("type")) put("type", "object")
                    },
                )
                put("strict", false)
            },
        )
    }

    private fun buildToolDefinition(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>,
    ): JSONObject = JSONObject().apply {
        put("type", "function")
        put(
            "function",
            JSONObject().apply {
                put("name", name)
                put("description", description)
                put(
                    "parameters",
                    buildStrictToolParameters(
                        properties = properties,
                        required = required,
                    )
                )
                put("strict", true)
            }
        )
    }

    private fun stringProperty(description: String): JSONObject = JSONObject().apply {
        put("type", "string")
        put("description", description)
    }

    private fun integerProperty(description: String): JSONObject = JSONObject().apply {
        put("type", "integer")
        put("description", description)
    }

    private fun booleanProperty(description: String): JSONObject = JSONObject().apply {
        put("type", "boolean")
        put("description", description)
    }

    private fun stringArrayProperty(description: String): JSONObject = JSONObject().apply {
        put("type", "array")
        put("description", description)
        put(
            "items",
            JSONObject().apply {
                put("type", "string")
            },
        )
    }

    private fun extractMcpServerId(arguments: JSONObject): String =
        arguments.optString("server_id").trim().ifBlank {
            arguments.optString("serverId").trim()
        }

    private fun looksLikeMcpToolCallName(toolName: String): Boolean =
        toolName.startsWith("mcp__") || toolName.contains(':')
}
