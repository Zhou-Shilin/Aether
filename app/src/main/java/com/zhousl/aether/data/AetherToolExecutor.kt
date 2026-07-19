package com.zhousl.aether.data

import com.zhousl.aether.data.pi.PiCompletionClient
import com.zhousl.aether.runtime.RuntimeFilesystemTool
import com.zhousl.aether.runtime.RuntimeRouter
import com.zhousl.aether.runtime.RuntimeShellTool
import com.zhousl.aether.runtime.runtimeEnvironment
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private const val MaxToolSleepDurationMillis = 10 * 60 * 1000L
private const val MaxToolSkillResourceBytes = 1024 * 1024
private const val MaxToolAnalyzeImageBytes = 5 * 1024 * 1024
private const val DefaultToolSkillResourceMaxChars = 20_000
private val TextSkillResourceExtensions = setOf(
    "md",
    "markdown",
    "txt",
    "json",
    "yaml",
    "yml",
    "toml",
    "xml",
    "html",
    "css",
    "csv",
    "tsv",
    "py",
    "sh",
    "js",
    "ts",
    "kt",
    "java",
    "rs",
    "rb",
    "pl",
    "ps1",
)

data class AetherToolExecutionResult(
    val toolName: String,
    val argumentsJson: String,
    val rawOutput: String,
    val visibleOutput: String = AetherToolExecutor.sanitizeToolOutputForConversation(toolName, rawOutput),
) {
    val isError: Boolean = !AetherToolExecutor.inferToolOutputOk(visibleOutput)
}

class AetherToolExecutor(
    private val runtimeRouter: RuntimeRouter,
    private val skillManager: AgentSkillManager? = null,
    private val webToolsClient: WebToolsClient? = null,
    private val runtimeWorkspaceFileBridge: RuntimeWorkspaceFileBridge? = null,
    private val piCompletionClient: PiCompletionClient? = null,
    private val agentModeController: AgentModeController? = null,
    private val alpineChromeController: AlpineChromeController? = null,
) {
    private val filesystemTool = RuntimeFilesystemTool(runtimeRouter)
    private val shellTool = RuntimeShellTool(runtimeRouter)

    suspend fun execute(
        settings: AppSettings,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        toolName: String,
        argumentsJson: String,
        availableSkills: List<InstalledSkill> = emptyList(),
        activeSkills: MutableList<ActiveSkillContext> = mutableListOf(),
        providerConfigs: List<LlmProviderConfig> = emptyList(),
        mcpClientManager: McpClientManager? = null,
        selfManagementTool: AetherSelfManagementTool? = null,
        agentModeEnabled: Boolean = false,
        chromeEnabled: Boolean = false,
        onProgress: (suspend (String) -> Unit)? = null,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit = {},
    ): AetherToolExecutionResult {
        val localRuntimeArgumentsJson by lazy(LazyThreadSafetyMode.NONE) {
            injectDefaultWorkingDirectory(
                argumentsJson = argumentsJson,
                settings = settings,
                runtimeRouter = runtimeRouter,
                workspaceDirectory = workspaceDirectory,
                termuxWorkspaceDirectory = termuxWorkspaceDirectory,
            )
        }
        val rawOutput = when (toolName) {
            "read" -> filesystemTool.executeRead(
                settings,
                localRuntimeArgumentsJson,
            )
            "edit" -> filesystemTool.executeEdit(
                settings,
                localRuntimeArgumentsJson,
            )
            "write" -> filesystemTool.executeWrite(
                settings,
                localRuntimeArgumentsJson,
            )
            "grep" -> filesystemTool.executeGrep(
                settings,
                localRuntimeArgumentsJson,
            )
            "find" -> filesystemTool.executeFind(
                settings,
                localRuntimeArgumentsJson,
            )
            "ls" -> filesystemTool.executeLs(
                settings,
                localRuntimeArgumentsJson,
            )
            "bash" -> shellTool.execute(
                settings,
                localRuntimeArgumentsJson,
                onProgress = onProgress,
            )
            "fetch_bash_output" -> shellTool.fetch(settings, argumentsJson)
            "kill_bash" -> shellTool.kill(settings, argumentsJson)
            "sleep" -> executeSleep(argumentsJson)
            "activate_skill" -> executeActivateSkill(
                argumentsJson = argumentsJson,
                availableSkills = availableSkills,
                activeSkills = activeSkills,
                onSkillActivated = onSkillActivated,
            )
            "read_skill_resource" -> executeReadSkillResource(
                argumentsJson = argumentsJson,
                activeSkills = activeSkills,
            )
            "fetch_web_url" -> executeFetchWebUrl(argumentsJson)
            "tavily_search" -> executeTavilySearch(
                settings = settings,
                argumentsJson = argumentsJson,
            )
            "analyze_image" -> executeAnalyzeImage(
                settings = settings,
                providerConfigs = providerConfigs,
                workspaceDirectory = workspaceDirectory,
                termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                argumentsJson = argumentsJson,
            )
            "agent_display" -> if (agentModeEnabled) {
                agentModeController?.execute(
                    settings = settings,
                    workspaceDirectory = workspaceDirectory,
                    termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                    argumentsJson = argumentsJson,
                ) ?: toolUnavailableOutput("agent_display")
            } else {
                JSONObject().apply {
                    put("ok", false)
                    put("errmsg", "Agent Mode is not enabled for this chat.")
                }.toString()
            }
            "chrome" -> if (chromeEnabled) {
                alpineChromeController?.execute(argumentsJson) ?: toolUnavailableOutput("chrome")
            } else {
                JSONObject().apply {
                    put("ok", false)
                    put("errmsg", "Chrome is not enabled for this chat.")
                }.toString()
            }
            in selfManagementToolNames -> selfManagementTool?.execute(
                toolName = toolName,
                argumentsJson = argumentsJson,
            ) ?: toolUnavailableOutput(toolName)
            "mcp_list_tools" -> executeMcpListTools(mcpClientManager, argumentsJson)
            "mcp_call_tool" -> executeMcpCallTool(mcpClientManager, argumentsJson)
            "mcp_list_resources" -> executeMcpListResources(mcpClientManager, argumentsJson)
            "mcp_read_resource" -> executeMcpReadResource(mcpClientManager, argumentsJson)
            "mcp_list_prompts" -> executeMcpListPrompts(mcpClientManager, argumentsJson)
            "mcp_get_prompt" -> executeMcpGetPrompt(mcpClientManager, argumentsJson)
            else -> if (looksLikeMcpToolCallName(toolName) && mcpClientManager != null) {
                mcpClientManager.callToolByName(toolName, argumentsJson)
                    .getOrElse { throwable -> toolFailureOutput(throwable, "MCP tool call failed.") }
            } else {
                unknownToolOutput(toolName)
            }
        }
        return AetherToolExecutionResult(
            toolName = toolName,
            argumentsJson = argumentsJson,
            rawOutput = rawOutput,
        )
    }

    private suspend fun executeActivateSkill(
        argumentsJson: String,
        availableSkills: List<InstalledSkill>,
        activeSkills: MutableList<ActiveSkillContext>,
        onSkillActivated: suspend (ActiveSkillContext) -> Unit,
    ): String {
        val manager = skillManager ?: return toolUnavailableOutput("activate_skill")
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

        val activeSkill = manager.buildActiveSkillContext(skill).getOrElse { throwable ->
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
        if (file.length() > MaxToolSkillResourceBytes) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Skill resource is too large to read in one tool call.")
                put("size_bytes", file.length())
                put("max_bytes", MaxToolSkillResourceBytes)
            }.toString()
        }

        val bytes = runCatching { file.readBytes() }.getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't read skill resource.")
        }
        val maxChars = arguments.optInt("max_chars", DefaultToolSkillResourceMaxChars)
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

    private suspend fun executeFetchWebUrl(argumentsJson: String): String {
        val client = webToolsClient ?: return toolUnavailableOutput("fetch_web_url")
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

        val page = client.fetchUrlAsMarkdown(
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
        val client = webToolsClient ?: return toolUnavailableOutput("tavily_search")
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

        val response = client.searchTavily(
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

    private suspend fun executeAnalyzeImage(
        settings: AppSettings,
        providerConfigs: List<LlmProviderConfig>,
        workspaceDirectory: String,
        termuxWorkspaceDirectory: String,
        argumentsJson: String,
    ): String {
        val fileBridge = runtimeWorkspaceFileBridge ?: return toolUnavailableOutput("analyze_image")
        val completionClient = piCompletionClient ?: return toolUnavailableOutput("analyze_image")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return invalidToolArguments()
        val path = arguments.optString("path").trim()
        if (path.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Missing required 'path' argument.")
            }.toString()
        }
        val workingDirectory = arguments.optString("working_directory").trim()
            .ifBlank { arguments.optString("workingDirectory").trim() }
            .ifBlank { workspaceDirectory }
        val prompt = arguments.optString("prompt").trim().ifBlank {
            "Describe the image and answer any relevant details needed for the task."
        }
        val preferredModelKey = arguments.optString("model_key").trim()
            .ifBlank { arguments.optString("modelKey").trim() }
            .ifBlank { arguments.optString("model").trim() }
        val analysisSettings = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = providerConfigs,
            preferredModelKey = preferredModelKey,
            fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
        )
        val payload = fileBridge.readWorkspaceFile(
            settings = settings,
            workspaceDirectory = workspaceDirectory,
            termuxWorkspaceDirectory = termuxWorkspaceDirectory,
            path = path,
            workingDirectory = workingDirectory,
            byteLimit = MaxToolAnalyzeImageBytes,
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Couldn't read the image from the workspace.") {
                put("path", path)
            }
        }
        val mimeType = guessImageMimeType(fileBridge, payload.absolutePath)
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
        val completion = completionClient.completeOnce(
            settings = analysisSettings,
            systemPrompt = "You are an image analysis helper for an Android coding agent. Answer only with observations and conclusions grounded in the image and the prompt.",
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
                )
            ),
        ).getOrElse { throwable ->
            return toolFailureOutput(throwable, "Image analysis request failed.") {
                put("path", payload.absolutePath)
            }
        }
        val analysis = completion.assistantText.trim()
        if (analysis.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("path", payload.absolutePath)
                put("errmsg", completion.errorMessage.ifBlank { "Image analysis returned no text." })
            }.toString()
        }
        return JSONObject().apply {
            put("ok", true)
            put("path", payload.absolutePath)
            put("prompt", prompt)
            put("model", analysisSettings.modelId)
            put("analysis", analysis)
            put("stdout", analysis)
        }.toString()
    }

    private suspend fun executeMcpListTools(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_list_tools")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return manager.listTools(extractMcpServerId(arguments))
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP tools.") }
    }

    private suspend fun executeMcpCallTool(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_call_tool")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return invalidToolArguments()
        val serverId = extractMcpServerId(arguments)
        val toolName = arguments.optString("tool_name").trim()
            .ifBlank { arguments.optString("toolName").trim() }
        if (serverId.isBlank() || toolName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'tool_name' are required.")
            }.toString()
        }
        return manager.callTool(
            serverId = serverId,
            toolName = toolName,
            arguments = arguments.optJSONObject("arguments") ?: JSONObject(),
        ).getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't call the MCP tool.") }
    }

    private suspend fun executeMcpListResources(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_list_resources")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return manager.listResources(extractMcpServerId(arguments))
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP resources.") }
    }

    private suspend fun executeMcpReadResource(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_read_resource")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return invalidToolArguments()
        val serverId = extractMcpServerId(arguments)
        val uri = arguments.optString("uri").trim()
        if (serverId.isBlank() || uri.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'uri' are required.")
            }.toString()
        }
        return manager.readResource(serverId, uri)
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't read MCP resource.") }
    }

    private suspend fun executeMcpListPrompts(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_list_prompts")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        return manager.listPrompts(extractMcpServerId(arguments))
            .getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't list MCP prompts.") }
    }

    private suspend fun executeMcpGetPrompt(
        manager: McpClientManager?,
        argumentsJson: String,
    ): String {
        manager ?: return toolUnavailableOutput("mcp_get_prompt")
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return invalidToolArguments()
        val serverId = extractMcpServerId(arguments)
        val promptName = arguments.optString("name").trim()
        if (serverId.isBlank() || promptName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "Both 'server_id' and 'name' are required.")
            }.toString()
        }
        return manager.getPrompt(
            serverId = serverId,
            promptName = promptName,
            arguments = arguments.optJSONObject("arguments") ?: JSONObject(),
        ).getOrElse { throwable -> toolFailureOutput(throwable, "Couldn't fetch the MCP prompt.") }
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
        if (durationMillis > MaxToolSleepDurationMillis) {
            return JSONObject().apply {
                put("ok", false)
                put("errmsg", "'duration_ms' must be between 0 and $MaxToolSleepDurationMillis.")
            }.toString()
        }

        delay(durationMillis)
        return JSONObject().apply {
            put("ok", true)
            put("duration_ms", durationMillis)
            put("stdout", "Slept for ${durationMillis}ms.")
        }.toString()
    }

    companion object {
        val hostToolNames: Set<String> = setOf(
            "read",
            "edit",
            "write",
            "grep",
            "find",
            "ls",
            "bash",
            "fetch_bash_output",
            "kill_bash",
            "sleep",
            "activate_skill",
            "read_skill_resource",
            "fetch_web_url",
            "tavily_search",
            "analyze_image",
            "agent_display",
            "chrome",
            "mcp_list_tools",
            "mcp_call_tool",
            "mcp_list_resources",
            "mcp_read_resource",
            "mcp_list_prompts",
            "mcp_get_prompt",
            *selfManagementToolNames.toTypedArray(),
        )

        fun supports(toolName: String): Boolean =
            toolName in hostToolNames || looksLikeMcpToolCallName(toolName)

        fun hostToolDefinitions(): JSONArray = JSONArray().apply {
            toolDefinition(
                name = "read",
                description = "Read a text file from the selected local runtime with optional line-based offset and limit. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The file path to read."))
                    put("offset", integerProperty("Optional zero-based line offset to start reading from."))
                    put("limit", integerProperty("Optional maximum number of lines to return."))
                    put("showLineNumbers", booleanProperty("Whether stdout should prefix each returned line with its original 1-based line number."))
                    put("show_line_numbers", booleanProperty("Alias of showLineNumbers."))
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "edit",
                description = "Precisely edit a text file in the selected local runtime using exact oldText/newText replacements. For multiple edits use only edits[]. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The file path to edit."))
                    put("oldText", stringProperty("For a single edit only, the exact text to replace. Omit this when using edits[]."))
                    put("newText", stringProperty("For a single edit only, the replacement text. Omit this when using edits[]."))
                    put(
                        "edits",
                        JSONObject().apply {
                            put("type", "array")
                            put("description", "For multiple edits only, a list of non-overlapping precise replacements.")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("oldText", stringProperty("The exact text to replace."))
                                            put("newText", stringProperty("The replacement text."))
                                        },
                                    )
                                    put("required", JSONArray().put("oldText").put("newText"))
                                    put("additionalProperties", false)
                                },
                            )
                        },
                    )
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "write",
                description = "Create a new text file or completely overwrite an existing text file in the selected local runtime. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The file path to create or overwrite."))
                    put("content", stringProperty("The full file contents to write."))
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path", "content"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "grep",
                description = "Search for text or a regex pattern inside a file or directory tree in the selected local runtime. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The file or directory path to search."))
                    put("pattern", stringProperty("The text or regex pattern to search for."))
                    put("isRegex", booleanProperty("Whether pattern should be treated as a regex."))
                    put("caseSensitive", booleanProperty("Whether the search should be case-sensitive."))
                    put("maxResults", integerProperty("Optional maximum number of matches to return."))
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path", "pattern"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "find",
                description = "Find files or directories by glob pattern in the selected local runtime. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The directory path to search in."))
                    put("pattern", stringProperty("The glob pattern to match, such as *.kt."))
                    put("type", stringProperty("Optional match type: any, file, or directory."))
                    put("caseSensitive", booleanProperty("Whether the glob match should be case-sensitive."))
                    put("maxDepth", integerProperty("Optional maximum search depth."))
                    put("maxResults", integerProperty("Optional maximum number of results to return."))
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path", "pattern"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "ls",
                description = "List the contents of a directory or inspect a file path in the selected local runtime. path accepts ~ or ~/... for that runtime's home directory.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("path", stringProperty("The file or directory path to list."))
                    put("recursive", booleanProperty("Whether to list recursively."))
                    put("includeHidden", booleanProperty("Whether to include hidden files and directories."))
                    put("maxDepth", integerProperty("Optional maximum recursion depth."))
                    put("maxEntries", integerProperty("Optional maximum number of entries to return."))
                    put("workingDirectory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("working_directory", stringProperty("Alias of workingDirectory."))
                },
                required = listOf("path"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "bash",
                description = "Execute a bash command in the selected local runtime. If it is still running after the live window, the tool returns status=running and a runtime-prefixed run_id.",
                properties = JSONObject().apply {
                    put("environment", runtimeEnvironmentProperty())
                    put("command", stringProperty("The bash command or script to execute."))
                    put("working_directory", stringProperty("Optional working directory inside the selected runtime."))
                    put("workingDirectory", stringProperty("Alias of working_directory."))
                },
                required = listOf("command"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "fetch_bash_output",
                description = "Fetch the latest stdout/stderr snapshot and status for a previously started long-running bash command by runtime-prefixed run_id.",
                properties = JSONObject().apply {
                    put("run_id", stringProperty("The run_id returned by bash when it reported status=running."))
                    put("runId", stringProperty("Alias of run_id."))
                    put("environment", runtimeEnvironmentProperty())
                    put("tail_bytes", integerProperty("Optional maximum number of bytes to return from the end of stdout and stderr."))
                    put("tailBytes", integerProperty("Alias of tail_bytes."))
                },
                required = listOf("run_id"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "kill_bash",
                description = "Stop a previously started long-running bash command by runtime-prefixed run_id and return its latest logs.",
                properties = JSONObject().apply {
                    put("run_id", stringProperty("The run_id returned by bash when it reported status=running."))
                    put("runId", stringProperty("Alias of run_id."))
                    put("environment", runtimeEnvironmentProperty())
                    put("tail_bytes", integerProperty("Optional maximum number of bytes to return from the end of stdout and stderr."))
                    put("tailBytes", integerProperty("Alias of tail_bytes."))
                },
                required = listOf("run_id"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "sleep",
                description = "Pause the agent for a fixed duration so a long-running bash command can continue before you fetch logs again.",
                properties = JSONObject().apply {
                    put("duration_ms", integerProperty("How long to sleep in milliseconds."))
                    put("durationMs", integerProperty("Alias of duration_ms."))
                },
                required = listOf("duration_ms"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "analyze_image",
                description = "Analyze an image file from the current workspace with model vision. Use this instead of assuming what an uploaded image contains.",
                properties = JSONObject().apply {
                    put("path", stringProperty("The image file path to inspect. Relative paths resolve from the current workspace."))
                    put("prompt", stringProperty("Optional question or instruction for what to inspect in the image."))
                    put("model", stringProperty("Optional model id or model option key."))
                    put("model_key", stringProperty("Optional exact model option key. Alias of model."))
                    put("modelKey", stringProperty("Alias of model_key."))
                    put("working_directory", stringProperty("Optional working directory used to resolve relative paths."))
                    put("workingDirectory", stringProperty("Alias of working_directory."))
                },
                required = listOf("path"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "activate_skill",
                description = "Load an installed Agent Skill into the current chat session. Use this when an installed skill matches the task or the user explicitly requests one.",
                properties = JSONObject().apply {
                    put("name", stringProperty("The installed skill name or id to activate."))
                },
                required = listOf("name"),
                executionMode = "sequential",
            ).also(::put)
            toolDefinition(
                name = "read_skill_resource",
                description = "Read a bundled file from an already active Agent Skill by relative path. Use this for progressive disclosure when a skill asks you to inspect references, scripts, assets, or agents metadata.",
                properties = JSONObject().apply {
                    put("skill", stringProperty("The active skill name or id."))
                    put("relative_path", stringProperty("The resource path relative to the skill root, such as references/guide.md or scripts/run.py."))
                    put("path", stringProperty("Alias of relative_path."))
                    put("max_chars", integerProperty("Optional maximum number of UTF-8 text characters to return."))
                },
                required = listOf("skill", "relative_path"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
                name = "fetch_web_url",
                description = "Fetch a specific HTTP or HTTPS URL and return the page content converted to Markdown. Use this when the user gives you a URL or you need the contents of one page.",
                properties = JSONObject().apply {
                    put("url", stringProperty("The HTTP or HTTPS URL to fetch."))
                    put("max_chars", integerProperty("Optional maximum number of Markdown characters to return."))
                    put("maxChars", integerProperty("Alias of max_chars."))
                },
                required = listOf("url"),
                executionMode = "parallel",
            ).also(::put)
            toolDefinition(
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
                executionMode = "parallel",
            ).also(::put)
        }

        fun hostToolDefinitions(
            selfManagementTool: AetherSelfManagementTool?,
            mcpClientManager: McpClientManager?,
            mcpToolBindings: List<McpToolBinding>,
            agentModeEnabled: Boolean,
            chromeEnabled: Boolean = false,
        ): JSONArray = JSONArray(hostToolDefinitions().toString()).apply {
            selfManagementTool?.toolDefinitions()?.forEach { definition ->
                put(
                    flattenOpenAiToolDefinition(
                        definition = definition,
                        executionMode = if (
                            definition.optJSONObject("function")
                                ?.optString("name") == "aether_config_get"
                        ) {
                            "parallel"
                        } else {
                            "sequential"
                        },
                    )
                )
            }
            val hasMcpCatalog = mcpToolBindings.isNotEmpty() ||
                mcpClientManager?.snapshots()?.any {
                    it.tools.isNotEmpty() || it.resources.isNotEmpty() || it.prompts.isNotEmpty()
                } == true
            if (hasMcpCatalog) {
                genericMcpToolDefinitions().forEach(::put)
                mcpToolBindings.forEach { binding ->
                    put(
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
                            put("execution_mode", "parallel")
                        }
                    )
                }
            }
            if (agentModeEnabled) {
                put(agentModeToolDefinition())
            }
            if (chromeEnabled) {
                put(chromeToolDefinition())
            }
        }

        fun sanitizeToolOutputForConversation(
            toolName: String,
            output: String,
        ): String {
            if (toolName != "agent_display" && toolName != "chrome") return output
            val parsed = runCatching { JSONObject(output) }.getOrNull() ?: return output
            if (!parsed.has("screenshot_base64")) return output
            parsed.remove("screenshot_base64")
            parsed.put("screenshot_injected_into_next_model_request", true)
            return parsed.toString()
        }

        fun inferToolOutputOk(output: String): Boolean {
            val parsed = runCatching { JSONObject(output) }.getOrNull() ?: return true
            return parsed.optBoolean("ok", !parsed.optBoolean("err", false))
        }
    }
}

private fun toolDefinition(
    name: String,
    description: String,
    properties: JSONObject,
    required: List<String>,
    executionMode: String,
): JSONObject = JSONObject().apply {
    put("name", name)
    put("description", description)
    put("parameters", buildPiHostToolParameters(properties, required))
    put("execution_mode", executionMode)
}

private fun buildPiHostToolParameters(
    properties: JSONObject,
    required: List<String>,
): JSONObject =
    JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject(properties.toString()))
        put("required", JSONArray(required))
        put("additionalProperties", false)
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

private fun runtimeEnvironmentProperty(): JSONObject = stringProperty(
    "Optional local runtime: default, termux, or alpine. Use alpine for the built-in Linux VM and Termux for Android/phone integration.",
)

private val selfManagementToolNames = setOf(
    "aether_config_get",
    "aether_config_set",
    "aether_skill_manage",
    "aether_mcp_manage",
    "aether_termux_manage",
    "aether_agent_mode_manage",
    "aether_scheduled_task_manage",
    "aether_extension_manage",
    "aether_developer_manage",
)

private fun flattenOpenAiToolDefinition(
    definition: JSONObject,
    executionMode: String,
): JSONObject {
    val function = definition.optJSONObject("function") ?: JSONObject()
    return JSONObject().apply {
        put("name", function.optString("name"))
        put("description", function.optString("description"))
        put(
            "parameters",
            relaxStrictOptionalParameters(
                function.optJSONObject("parameters") ?: JSONObject().put("type", "object"),
            ),
        )
        put("execution_mode", executionMode)
    }
}

private fun relaxStrictOptionalParameters(parameters: JSONObject): JSONObject {
    val relaxed = JSONObject(parameters.toString())
    val properties = relaxed.optJSONObject("properties") ?: return relaxed
    val required = relaxed.optJSONArray("required") ?: return relaxed
    val actualRequired = JSONArray()
    for (index in 0 until required.length()) {
        val propertyName = required.optString(index)
        val propertySchema = properties.optJSONObject(propertyName)
        if (propertyName.isNotBlank() && !propertySchema.allowsNull()) {
            actualRequired.put(propertyName)
        }
    }
    relaxed.put("required", actualRequired)
    return relaxed
}

private fun JSONObject?.allowsNull(): Boolean {
    if (this == null) return false
    return when (val typeValue = opt("type")) {
        "null" -> true
        is JSONArray -> (0 until typeValue.length()).any { index ->
            typeValue.optString(index) == "null"
        }
        else -> false
    }
}

private fun genericMcpToolDefinitions(): List<JSONObject> = listOf(
    toolDefinition(
        name = "mcp_list_tools",
        description = "List callable MCP tools across all connected servers or for one server.",
        properties = JSONObject().apply {
            put("server_id", stringProperty("Optional MCP server id to filter by."))
            put("serverId", stringProperty("Alias of server_id."))
        },
        required = emptyList(),
        executionMode = "parallel",
    ),
    toolDefinition(
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
        executionMode = "parallel",
    ),
    toolDefinition(
        name = "mcp_list_resources",
        description = "List available MCP resources across all connected servers or for one server.",
        properties = JSONObject().apply {
            put("server_id", stringProperty("Optional MCP server id to filter by."))
            put("serverId", stringProperty("Alias of server_id."))
        },
        required = emptyList(),
        executionMode = "parallel",
    ),
    toolDefinition(
        name = "mcp_read_resource",
        description = "Read a specific MCP resource from a connected server.",
        properties = JSONObject().apply {
            put("server_id", stringProperty("The MCP server id to read from."))
            put("serverId", stringProperty("Alias of server_id."))
            put("uri", stringProperty("The MCP resource URI."))
        },
        required = listOf("server_id", "uri"),
        executionMode = "parallel",
    ),
    toolDefinition(
        name = "mcp_list_prompts",
        description = "List available MCP prompts across all connected servers or for one server.",
        properties = JSONObject().apply {
            put("server_id", stringProperty("Optional MCP server id to filter by."))
            put("serverId", stringProperty("Alias of server_id."))
        },
        required = emptyList(),
        executionMode = "parallel",
    ),
    toolDefinition(
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
        executionMode = "parallel",
    ),
)

private fun agentModeToolDefinition(): JSONObject = toolDefinition(
    name = "agent_display",
    description = "Operate Aether Agent Mode on an isolated Android virtual display. Use this only when Agent Mode is selected in the chat composer.",
    properties = JSONObject().apply {
        put("action", stringProperty("One of: list_apps, start, status, launch, tap, swipe, key, text, screenshot, stop."))
        put("query", stringProperty("For list_apps: optional app label, package, or activity filter."))
        put("include_system", booleanProperty("For list_apps: whether to include system apps."))
        put("includeSystem", booleanProperty("Alias of include_system."))
        put("max_results", integerProperty("For list_apps: maximum number of apps to return."))
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
        put("key", stringProperty("For key: Android key code name or number."))
        put("text", stringProperty("For text: text to type into the focused field."))
    },
    required = listOf("action"),
    executionMode = "sequential",
)

private fun chromeToolDefinition(): JSONObject = toolDefinition(
    name = "chrome",
    description = "Operate the optional Chromium browser installed inside Aether's Alpine environment through Chrome DevTools Protocol. Use this only when Chrome is selected in the chat composer.",
    properties = JSONObject().apply {
        put("action", stringProperty("One of: start, status, navigate, tap, swipe, text, key, back, forward, reload, evaluate, screenshot, stop."))
        put("url", stringProperty("For navigate: the URL to open."))
        put("x", integerProperty("For tap: normalized X coordinate from 0 to 1000."))
        put("y", integerProperty("For tap: normalized Y coordinate from 0 to 1000."))
        put("x1", integerProperty("For swipe: normalized start X coordinate from 0 to 1000."))
        put("y1", integerProperty("For swipe: normalized start Y coordinate from 0 to 1000."))
        put("x2", integerProperty("For swipe: normalized end X coordinate from 0 to 1000."))
        put("y2", integerProperty("For swipe: normalized end Y coordinate from 0 to 1000."))
        put("text", stringProperty("For text: text to insert into the focused field."))
        put("key", stringProperty("For key: Enter, Tab, Backspace, Escape, an arrow key, or a character."))
        put("expression", stringProperty("For evaluate: JavaScript to evaluate in the current page."))
    },
    required = listOf("action"),
    executionMode = "sequential",
)

private fun invalidToolArguments(): String = JSONObject().apply {
    put("ok", false)
    put("errmsg", "Arguments were not valid JSON.")
}.toString()

private fun extractMcpServerId(arguments: JSONObject): String =
    arguments.optString("server_id").trim().ifBlank {
        arguments.optString("serverId").trim()
    }

private fun looksLikeMcpToolCallName(toolName: String): Boolean =
    toolName.startsWith("mcp__") || toolName.contains(':')

private fun guessImageMimeType(
    workspaceFileBridge: RuntimeWorkspaceFileBridge,
    path: String,
): String? {
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

private fun toolUnavailableOutput(toolName: String): String =
    JSONObject().apply {
        put("ok", false)
        put("errmsg", "Host dependency for '$toolName' is not available.")
    }.toString()

private fun unknownToolOutput(toolName: String): String =
    JSONObject().apply {
        put("ok", false)
        put("error", "Unknown tool '$toolName'.")
    }.toString()

internal fun injectDefaultWorkingDirectory(
    argumentsJson: String,
    settings: AppSettings,
    runtimeRouter: RuntimeRouter,
    workspaceDirectory: String,
    termuxWorkspaceDirectory: String,
): String {
    val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull() ?: return argumentsJson

    val snake = arguments.cleanOptionalString("working_directory")
    val camel = arguments.cleanOptionalString("workingDirectory")

    arguments.remove("working_directory")
    arguments.remove("workingDirectory")

    when {
        snake.isNotBlank() -> arguments.put("working_directory", snake)
        camel.isNotBlank() -> arguments.put("working_directory", camel)
        else -> arguments.put(
            "working_directory",
            runtimeRouter.runtimeWorkspaceDirectory(
                settings = settings,
                termuxWorkspaceDirectory = termuxWorkspaceDirectory,
                environment = arguments.runtimeEnvironment(),
            ).ifBlank { workspaceDirectory },
        )
    }

    return arguments.toString()
}

private fun JSONObject.cleanOptionalString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    val value = optString(key).trim()
    return value.takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
        .orEmpty()
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
