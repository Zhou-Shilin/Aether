package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AppThemeMode
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.SharedActiveSkillContext
import com.zhousl.aether.data.SharedInstalledSkill
import com.zhousl.aether.data.SharedSkillManager
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.normalizeOldCommandHistoryRetentionHours
import com.zhousl.aether.data.normalizeTavilyBaseUrl
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.SharedPiBridgeClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException

private const val MaximumSkillResourceBytes = 1024 * 1024
private const val DefaultSkillResourceCharacters = 20_000
private const val MaximumSkillResourceCharacters = 100_000
private const val MaximumImageBytes = 5 * 1024 * 1024

/**
 * Host tools that expose Aether-owned state to the Pi agent. The callbacks keep
 * this executor independent from Compose state while preserving the Android
 * tool names and payload shapes.
 */
class SharedAgentManagementTools(
    private val runtime: MultiplatformLocalRuntime,
    private val bridge: SharedPiBridgeClient,
    private val skillManager: SharedSkillManager,
    private val mcpManager: SharedMcpManager,
    private val completionClient: SharedPiChatClient,
    private val settings: suspend () -> AppSettings,
    private val updateSettings: suspend (AppSettings) -> Unit,
    private val activeSkills: suspend (sessionId: String) -> List<SharedActiveSkillContext>,
    private val activateSkill: suspend (sessionId: String, skill: SharedActiveSkillContext) -> Unit,
    private val activeMcpServerIds: suspend (sessionId: String) -> Set<String> = { emptySet() },
    private val currentSessionId: suspend () -> String,
    private val resolveProvider: suspend (String) -> LlmProviderConfig?,
) : SharedSessionAwareHostToolExecutor {
    private val runtimeTools = RuntimeHostToolExecutor(runtime)

    override val definitions: JsonArray = buildJsonArray {
        add(agentToolDefinition(
            name = "analyze_image",
            description = "Analyze an image file from the current workspace with model vision. Use this instead of assuming what an uploaded image contains.",
            executionMode = "parallel",
            required = listOf("path"),
            properties = mapOf(
                "path" to agentStringSchema("The image file path to inspect. Relative paths resolve from the current workspace."),
                "prompt" to agentStringSchema("Optional question or instruction for what to inspect in the image."),
                "model" to agentStringSchema("Optional model id or model option key."),
                "model_key" to agentStringSchema("Optional exact model option key. Alias of model."),
                "modelKey" to agentStringSchema("Alias of model_key."),
                "working_directory" to agentStringSchema("Optional working directory used to resolve relative paths."),
                "workingDirectory" to agentStringSchema("Alias of working_directory."),
            ),
        ))
        add(agentToolDefinition(
            name = "activate_skill",
            description = "Load an installed Agent Skill into the current chat session. Use this when an installed skill matches the task or the user explicitly requests one.",
            executionMode = "sequential",
            required = listOf("name"),
            properties = mapOf("name" to agentStringSchema("The installed skill name or id to activate.")),
        ))
        add(agentToolDefinition(
            name = "read_skill_resource",
            description = "Read a bundled file from an already active Agent Skill by relative path. Use this for progressive disclosure when a skill asks you to inspect references, scripts, assets, or agents metadata.",
            executionMode = "parallel",
            required = listOf("skill", "relative_path"),
            properties = mapOf(
                "skill" to agentStringSchema("The active skill name or id."),
                "relative_path" to agentStringSchema("The resource path relative to the skill root, such as references/guide.md or scripts/run.py."),
                "path" to agentStringSchema("Alias of relative_path."),
                "max_chars" to agentIntegerSchema("Optional maximum number of UTF-8 text characters to return."),
            ),
        ))
        addGenericMcpDefinitions()
        addSelfManagementDefinitions()
    }

    override fun definitions(sessionId: String): JsonArray = if (mcpManager.hasCatalog(sessionId)) {
        definitions
    } else {
        JsonArray(definitions.filterNot { definition ->
            (definition as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull in GenericMcpToolNames
        })
    }

    override suspend fun execute(name: String, arguments: JsonObject): SharedHostToolResult = try {
            when (name) {
                "analyze_image" -> analyzeImage(arguments)
                "activate_skill" -> activateSkill(arguments)
                "read_skill_resource" -> readSkillResource(arguments)
                "mcp_list_tools" -> inspectMcp(arguments, SharedMcpInspection.Tools)
                "mcp_call_tool" -> callMcpTool(arguments)
                "mcp_list_resources" -> inspectMcp(arguments, SharedMcpInspection.Resources)
                "mcp_read_resource" -> readMcpResource(arguments)
                "mcp_list_prompts" -> inspectMcp(arguments, SharedMcpInspection.Prompts)
                "mcp_get_prompt" -> getMcpPrompt(arguments)
                "aether_config_get" -> getConfiguration(arguments)
                "aether_config_set" -> setConfiguration(arguments)
                "aether_skill_manage" -> manageSkills(arguments)
                "aether_mcp_manage" -> manageMcp(arguments)
                "aether_extension_manage" -> manageExtensions(arguments)
                "aether_developer_manage" -> manageDeveloper(arguments)
                else -> error("Unsupported Aether host tool: $name")
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (failure: Throwable) {
            agentToolFailure(failure)
        }

    private suspend fun analyzeImage(arguments: JsonObject): SharedHostToolResult {
        val path = arguments.string("path").trim()
        require(path.isNotBlank()) { "Missing required 'path' argument." }
        val workingDirectory = arguments.string("working_directory")
            .ifBlank { arguments.string("workingDirectory") }
            .ifBlank { runtime.workspaceRoot }
        val absolutePath = resolveWorkspacePath(path, workingDirectory)
        val mimeType = imageMimeType(absolutePath)
            ?: error("The selected file does not look like a supported image.")
        val bytes = runtime.fileSystem.read(absolutePath)
        require(bytes.isNotEmpty()) { "The selected image is empty." }
        require(bytes.size <= MaximumImageBytes) { "File exceeds the allowed size." }
        val preferredModel = arguments.string("model_key")
            .ifBlank { arguments.string("modelKey") }
            .ifBlank { arguments.string("model") }
        val provider = resolveProvider(preferredModel)
            ?: error("No configured model is available for image analysis.")
        val prompt = arguments.string("prompt").trim().ifBlank {
            "Describe the image and answer any relevant details needed for the task."
        }
        val result = completionClient.completeOnce(
            config = provider,
            messages = listOf(
                SharedPiChatMessage(
                    role = "user",
                    text = prompt,
                    images = listOf(SharedPiImage(mimeType, bytes.encodeAgentBase64())),
                )
            ),
            systemPrompt = "You are an image analysis helper for an Android coding agent. Answer only with observations and conclusions grounded in the image and the prompt.",
            reasoning = "off",
            timeoutMillis = settings().llmInactivityReconnectTimeoutSeconds
                .coerceIn(30, 3_600) * 1_000,
        )
        val analysis = result.assistantText.trim()
        require(result.errorMessage.isBlank()) { result.errorMessage }
        require(analysis.isNotBlank()) { "Image analysis returned no text." }
        return agentToolSuccess("Analyzed $absolutePath") {
            put("path", absolutePath)
            put("prompt", prompt)
            put("model", provider.modelId)
            put("analysis", analysis)
            put("stdout", analysis)
        }
    }

    private suspend fun activateSkill(arguments: JsonObject): SharedHostToolResult {
        val requested = arguments.string("name").trim()
        require(requested.isNotBlank()) { "Missing required 'name' argument." }
        val available = skillManager.list().filter(SharedInstalledSkill::isEnabled)
        val skill = available.firstOrNull {
            it.name.equals(requested, ignoreCase = true) || it.id.equals(requested, ignoreCase = true)
        } ?: return SharedHostToolResult(
            outputJson = buildJsonObject {
                put("ok", false)
                put("errmsg", "No installed enabled skill matched '$requested'.")
                put("available_skills", JsonArray(available.take(32).map { JsonPrimitive(it.name) }))
            }.toString(),
            isError = true,
        )
        val activeSkill = skillManager.buildActiveSkillContext(skill)
        activateSkill(arguments.sharedHostToolSessionId(), activeSkill)
        return agentToolSuccess("Activated Agent Skill ${skill.name} with ${skill.resourceCount} bundled files.") {
            put("name", activeSkill.name)
            put("skill_id", activeSkill.skillId)
            put("description", activeSkill.description)
            put("compatibility", activeSkill.compatibility)
            put("skill_root_path", activeSkill.skillRootPath)
            put("body_markdown", activeSkill.bodyMarkdown)
            put("allowed_tools", JsonArray(activeSkill.allowedTools.map(::JsonPrimitive)))
            put("resources", buildJsonArray {
                activeSkill.resourceEntries.forEach { resource ->
                    add(buildJsonObject {
                        put("path", resource.relativePath)
                        put("kind", resource.kind)
                    })
                }
            })
        }
    }

    private suspend fun readSkillResource(arguments: JsonObject): SharedHostToolResult {
        val requestedSkill = arguments.string("skill")
            .ifBlank { arguments.string("name") }
            .ifBlank { arguments.string("skill_id") }
            .trim()
        val requestedPath = arguments.string("relative_path")
            .ifBlank { arguments.string("path") }
            .trim()
        require(requestedSkill.isNotBlank() && requestedPath.isNotBlank()) {
            "Both 'skill' and 'relative_path' are required."
        }
        val skill = activeSkills(arguments.sharedHostToolSessionId()).firstOrNull {
                it.name.equals(requestedSkill, ignoreCase = true) ||
                    it.skillId.equals(requestedSkill, ignoreCase = true)
        } ?: return SharedHostToolResult(
            outputJson = buildJsonObject {
                put("ok", false)
                put("errmsg", "No active skill matched '$requestedSkill'. Call activate_skill first.")
                put(
                    "active_skills",
                    JsonArray(activeSkills(arguments.sharedHostToolSessionId()).map { JsonPrimitive(it.name) }),
                )
            }.toString(),
            isError = true,
        )
        val normalized = normalizeRelativeResourcePath(requestedPath)
        val absolutePath = resolveSkillResourcePath(skill.skillRootPath, normalized)
        val bytes = runtime.fileSystem.read(absolutePath)
        if (bytes.size > MaximumSkillResourceBytes) {
            return SharedHostToolResult(
                outputJson = buildJsonObject {
                    put("ok", false)
                    put("errmsg", "Skill resource is too large to read in one tool call.")
                    put("size_bytes", bytes.size)
                    put("max_bytes", MaximumSkillResourceBytes)
                }.toString(),
                isError = true,
            )
        }
        val maxCharacters = (arguments.int("max_chars") ?: DefaultSkillResourceCharacters)
            .coerceIn(1, MaximumSkillResourceCharacters)
        val textResource = isLikelyTextResource(normalized, bytes)
        return agentToolSuccess("Read ${skill.name}/$normalized") {
            put("skill", skill.name)
            put("skill_id", skill.skillId)
            put("relative_path", normalized)
            put("size_bytes", bytes.size)
            if (textResource) {
                val text = bytes.decodeToString()
                put("content", text.take(maxCharacters))
                put("truncated", text.length > maxCharacters)
                put("encoding", "utf-8")
            } else {
                put("base64", bytes.encodeAgentBase64())
                put("encoding", "base64")
            }
        }
    }

    private suspend fun resolveSkillResourcePath(skillRoot: String, relativePath: String): String {
        val root = skillRoot.trimEnd('/')
        val candidate = "$root/$relativePath"
        val command = """
            root=${'$'}(realpath -e -- ${agentShellQuote(root)}) || exit 2
            resource=${'$'}(realpath -e -- ${agentShellQuote(candidate)}) || exit 3
            case "${'$'}resource" in
                "${'$'}root"/*) ;;
                *) exit 4 ;;
            esac
            test -f "${'$'}resource" || exit 5
            printf '%s' "${'$'}resource"
        """.trimIndent()
        val result = runtimeTools.execute(
            "bash",
            buildJsonObject {
                put("command", command)
                put("working_directory", runtime.homeDirectory)
            },
        )
        require(!result.isError) {
            "Skill resource was not found inside the active Skill directory."
        }
        val resolved = Json.parseToJsonElement(result.outputJson).jsonObject
            .get("stdout")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        require(resolved.startsWith("/")) {
            "Skill resource path could not be resolved."
        }
        return resolved
    }

    private suspend fun inspectMcp(
        arguments: JsonObject,
        inspection: SharedMcpInspection,
    ): SharedHostToolResult {
        val serverId = arguments.string("server_id").ifBlank { arguments.string("serverId") }
        val sessionId = arguments.sharedHostToolSessionId()
        val selectedServers = mcpManager.serversForSession(sessionId)
        val servers = if (serverId.isBlank()) {
            selectedServers
        } else {
            val server = when (inspection) {
                SharedMcpInspection.Tools -> selectedServers.firstOrNull {
                    it.id == serverId || it.name.equals(serverId, ignoreCase = true)
                }
                SharedMcpInspection.Resources,
                SharedMcpInspection.Prompts -> selectedServers.firstOrNull { it.id == serverId }
            }
            listOfNotNull(server)
        }
        if (serverId.isNotBlank() && servers.isEmpty()) {
            error("MCP server '$serverId' is not connected.")
        }
        val responseKey = when (inspection) {
            SharedMcpInspection.Tools -> "tools"
            SharedMcpInspection.Resources -> "resources"
            SharedMcpInspection.Prompts -> "prompts"
        }
        val results = buildJsonArray {
            servers.forEach { server ->
                val response = try {
                    mcpManager.inspectServer(server.id, inspection)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    if (serverId.isNotBlank()) throw failure
                    return@forEach
                }
                (response[responseKey] as? JsonArray).orEmpty().forEach { element ->
                    val item = element as? JsonObject ?: return@forEach
                    add(when (inspection) {
                        SharedMcpInspection.Tools -> buildJsonObject {
                            val toolName = item.string("name")
                            put("server_id", server.id)
                            put("server_name", server.name)
                            put("tool_name", toolName)
                            put("description", item.string("description"))
                            put("call_name", sharedMcpToolName(server.id, toolName))
                            put("legacy_call_name", "${server.id}:$toolName")
                            put("input_schema", item["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()))
                        }
                        SharedMcpInspection.Resources -> buildJsonObject {
                            put("server_id", server.id)
                            put("server_name", server.name)
                            put("uri", item.string("uri"))
                            put("name", item.string("name"))
                            put("description", item.string("description"))
                            put("mime_type", item.string("mimeType"))
                        }
                        SharedMcpInspection.Prompts -> buildJsonObject {
                            put("server_id", server.id)
                            put("server_name", server.name)
                            put("name", item.string("name"))
                            put("description", item.string("description"))
                            put("arguments", item["arguments"] as? JsonArray ?: JsonArray(emptyList()))
                        }
                    })
                }
            }
        }
        if (inspection == SharedMcpInspection.Tools && serverId.isNotBlank() && results.isEmpty()) {
            error("MCP server '$serverId' is not connected.")
        }
        val itemName = when (inspection) {
            SharedMcpInspection.Tools -> "tools"
            SharedMcpInspection.Resources -> "resources"
            SharedMcpInspection.Prompts -> "prompts"
        }
        return agentToolSuccess("Listed ${results.size} MCP $itemName.") {
            put(responseKey, results)
        }
    }

    private suspend fun callMcpTool(arguments: JsonObject): SharedHostToolResult {
        val serverId = arguments.string("server_id").ifBlank { arguments.string("serverId") }
        val toolName = arguments.string("tool_name").ifBlank { arguments.string("toolName") }
        require(serverId.isNotBlank() && toolName.isNotBlank()) {
            "Both 'server_id' and 'tool_name' are required."
        }
        val server = mcpManager.serversForSession(arguments.sharedHostToolSessionId()).firstOrNull {
            it.id == serverId || it.name.equals(serverId, ignoreCase = true)
        } ?: error("MCP server '$serverId' is not connected.")
        val response = mcpManager.callTool(
            server.id,
            toolName,
            arguments["arguments"] as? JsonObject ?: JsonObject(emptyMap()),
        )
        return SharedHostToolResult(response.toString())
    }

    private suspend fun readMcpResource(arguments: JsonObject): SharedHostToolResult {
        val serverId = arguments.string("server_id").ifBlank { arguments.string("serverId") }
        val uri = arguments.string("uri")
        require(serverId.isNotBlank() && uri.isNotBlank()) { "Both 'server_id' and 'uri' are required." }
        require(mcpManager.serversForSession(arguments.sharedHostToolSessionId()).any { it.id == serverId }) {
            "MCP server '$serverId' is not connected."
        }
        return SharedHostToolResult(mcpManager.readResource(serverId, uri).toString())
    }

    private suspend fun getMcpPrompt(arguments: JsonObject): SharedHostToolResult {
        val serverId = arguments.string("server_id").ifBlank { arguments.string("serverId") }
        val name = arguments.string("name")
        require(serverId.isNotBlank() && name.isNotBlank()) { "Both 'server_id' and 'name' are required." }
        require(mcpManager.serversForSession(arguments.sharedHostToolSessionId()).any { it.id == serverId }) {
            "MCP server '$serverId' is not connected."
        }
        val promptArguments = (arguments["arguments"] as? JsonObject).orEmpty()
            .mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }
            .toMap()
        return SharedHostToolResult(mcpManager.getPrompt(serverId, name, promptArguments).toString())
    }

    private suspend fun getConfiguration(arguments: JsonObject): SharedHostToolResult {
        val requested = (arguments["categories"] as? JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .map { it.lowercase() }
            .toSet()
        val categories = requested.ifEmpty {
            setOf("general", "web_tools", "reliability", "agent_skills", "mcp_servers", "developer")
        }
        val current = settings()
        return agentToolSuccess("Read ${categories.size} Aether configuration categories.") {
            categories.forEach { category ->
                when (category) {
                    "general" -> put("general", buildJsonObject {
                        put("language", current.language.storageValue)
                        put("theme_mode", current.themeMode.storageValue)
                    })
                    "web_tools" -> put("web_tools", buildJsonObject {
                        put("tavily_configured", current.tavilyApiKey.isNotBlank())
                        put("tavily_api_key", redactAgentSecret(current.tavilyApiKey))
                        put("tavily_base_url", current.tavilyBaseUrl)
                    })
                    "reliability" -> put("reliability", buildJsonObject {
                        put("llm_inactivity_reconnect_timeout_seconds", current.llmInactivityReconnectTimeoutSeconds)
                        put("auto_clean_old_command_history", current.autoCleanOldCommandHistory)
                        put("old_command_history_retention_hours", current.oldCommandHistoryRetentionHours)
                    })
                    "agent_skills" -> put("agent_skills", skillsJson(skillManager.list()))
                    "mcp_servers" -> put("mcp_servers", mcpServersJson(mcpManager.loadServers()))
                    "developer" -> put("developer", buildJsonObject {
                        put("runtime_home", runtime.homeDirectory)
                        put("workspace", runtime.workspaceRoot)
                        put("tools", JsonArray(listOf(JsonPrimitive("aether_developer_manage"))))
                    })
                    else -> error("Unsupported category '$category'.")
                }
            }
        }
    }

    private suspend fun setConfiguration(arguments: JsonObject): SharedHostToolResult {
        val category = arguments.string("category").trim().lowercase()
        val patch = arguments["settings"] as? JsonObject ?: error("settings must be an object.")
        val current = settings()
        val updated = when (category) {
            "general" -> current.copy(
                language = patch.stringOrNull("language")
                    ?.let { AppLanguage.fromStorage(it, current.language) } ?: current.language,
                themeMode = patch.stringOrNull("theme_mode")
                    ?.let(AppThemeMode::fromStorage) ?: current.themeMode,
            )
            "web_tools" -> current.copy(
                tavilyApiKey = patch.stringOrNull("tavily_api_key") ?: current.tavilyApiKey,
                tavilyBaseUrl = patch.stringOrNull("tavily_base_url")
                    ?.let(::normalizeTavilyBaseUrl) ?: current.tavilyBaseUrl,
            )
            "reliability" -> current.copy(
                llmInactivityReconnectTimeoutSeconds = patch.int("llm_inactivity_reconnect_timeout_seconds")
                    ?.let(::normalizeLlmInactivityReconnectTimeoutSeconds)
                    ?: current.llmInactivityReconnectTimeoutSeconds,
                autoCleanOldCommandHistory = patch.booleanOrNull("auto_clean_old_command_history")
                    ?: current.autoCleanOldCommandHistory,
                oldCommandHistoryRetentionHours = patch.int("old_command_history_retention_hours")
                    ?.let(::normalizeOldCommandHistoryRetentionHours)
                    ?: current.oldCommandHistoryRetentionHours,
            )
            else -> error("Unsupported settings category '$category'.")
        }
        updateSettings(updated)
        return agentToolSuccess("Updated Aether $category settings.") {
            put("category", category)
        }
    }

    private suspend fun manageSkills(arguments: JsonObject): SharedHostToolResult {
        val action = arguments.string("action").lowercase()
        when (action) {
            "list" -> Unit
            "install_remote" -> {
                val url = arguments.string("url")
                require(url.isNotBlank()) { "url is required for install_remote." }
                skillManager.installRemote(url)
            }
            "remove" -> {
                val skillId = arguments.string("skill_id")
                require(skillId.isNotBlank()) { "skill_id is required for remove." }
                skillManager.remove(skillId)
            }
            "set_enabled" -> {
                val skillId = arguments.string("skill_id")
                val enabled = arguments.booleanOrNull("enabled")
                require(skillId.isNotBlank() && enabled != null) {
                    "skill_id and enabled are required for set_enabled."
                }
                skillManager.setEnabled(skillId, enabled)
            }
            else -> error("Unsupported Skill action '$action'.")
        }
        val skills = skillManager.list()
        return agentToolSuccess("${action.ifBlank { "Listed" }} ${skills.size} Agent Skill(s).") {
            put("skills", skillsJson(skills))
        }
    }

    private suspend fun manageMcp(arguments: JsonObject): SharedHostToolResult {
        val action = arguments.string("action").lowercase()
        val servers = mcpManager.loadServers().toMutableList()
        when (action) {
            "list" -> Unit
            "remove" -> {
                val id = arguments.string("server_id")
                require(id.isNotBlank()) { "server_id is required for remove." }
                require(servers.removeAll { it.id == id }) { "MCP server '$id' was not found." }
            }
            "set_enabled" -> {
                val id = arguments.string("server_id")
                val enabled = arguments.booleanOrNull("enabled")
                require(id.isNotBlank() && enabled != null) { "server_id and enabled are required." }
                val index = servers.indexOfFirst { it.id == id }
                require(index >= 0) { "MCP server '$id' was not found." }
                servers[index] = servers[index].copy(enabled = enabled)
            }
            "upsert_streamable_http", "upsert_stdio" -> {
                val id = arguments.string("server_id").ifBlank { platformRandomUuid() }
                val existing = servers.firstOrNull { it.id == id }
                val transport = if (action == "upsert_stdio") SharedMcpTransport.Stdio else SharedMcpTransport.Http
                val name = arguments.string("display_name").ifBlank { existing?.name.orEmpty() }
                require(name.isNotBlank()) { "display_name is required." }
                val updated = SharedMcpServerConfig(
                    id = id,
                    name = name,
                    actionLabel = existing?.actionLabel.orEmpty(),
                    transport = transport,
                    url = arguments.string("url").ifBlank { existing?.url.orEmpty() },
                    command = arguments.string("command").ifBlank { existing?.command.orEmpty() },
                    arguments = arguments.stringList("arguments").ifEmpty {
                        arguments.stringList("args").ifEmpty { existing?.arguments.orEmpty() }
                    },
                    headers = arguments.stringMap("headers").ifEmpty { existing?.headers.orEmpty() },
                    workingDirectory = arguments.string("working_directory")
                        .ifBlank { existing?.workingDirectory.orEmpty() },
                    environment = arguments.stringMap("environment").ifEmpty { existing?.environment.orEmpty() },
                    runtimeEnvironment = arguments.string("runtime_environment")
                        .ifBlank { arguments.string("runtime") }
                        .ifBlank { existing?.runtimeEnvironment ?: "default" },
                    connectTimeoutMillis = arguments.long("connect_timeout_millis")
                        ?: existing?.connectTimeoutMillis ?: 15_000,
                    requestTimeoutMillis = arguments.long("request_timeout_millis")
                        ?: existing?.requestTimeoutMillis ?: 60_000,
                    enabled = arguments.booleanOrNull("enabled") ?: existing?.enabled ?: true,
                    createdAtMillis = existing?.createdAtMillis ?: platformCurrentTimeMillis(),
                    updatedAtMillis = platformCurrentTimeMillis(),
                )
                require(
                    (transport == SharedMcpTransport.Http && updated.url.isNotBlank()) ||
                        (transport == SharedMcpTransport.Stdio && updated.command.isNotBlank())
                ) { if (transport == SharedMcpTransport.Http) "url is required." else "command is required." }
                val index = servers.indexOfFirst { it.id == id }
                if (index >= 0) servers[index] = updated else servers += updated
            }
            else -> error("Unsupported MCP action '$action'.")
        }
        if (action != "list") {
            mcpManager.saveServers(servers)
            try {
                val selectedIds = activeMcpServerIds(arguments.sharedHostToolSessionId())
                mcpManager.refreshBindings(
                    servers.filter { it.enabled && it.id in selectedIds },
                    sessionId = arguments.sharedHostToolSessionId(),
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // The saved configuration can reconnect on the next turn.
            }
        }
        return agentToolSuccess("${action.ifBlank { "Listed" }} ${servers.size} MCP server(s).") {
            put("mcp_servers", mcpServersJson(servers))
        }
    }

    private suspend fun manageExtensions(arguments: JsonObject): SharedHostToolResult {
        val action = arguments.string("action").lowercase()
        val sessionId = currentSessionId()
        val response = when (action) {
            "list" -> bridge.listExtensions(sessionId)
            "reload" -> bridge.reloadExtensions(sessionId)
            "invoke_command" -> {
                val command = arguments.string("command")
                require(command.isNotBlank()) { "command is required for invoke_command." }
                bridge.invokeExtensionCommand(sessionId, command, arguments.string("args"))
            }
            else -> error("Unsupported Pi extension action '$action'.")
        }
        return agentToolSuccess("Completed Pi extension action '$action'.") {
            response.forEach { (key, value) -> put(key, value) }
        }
    }

    private suspend fun manageDeveloper(arguments: JsonObject): SharedHostToolResult {
        require(arguments.string("action").ifBlank { "read_diagnostics" } == "read_diagnostics") {
            "Unsupported developer action."
        }
        val ping = bridge.ping()
        return agentToolSuccess("Read Aether runtime diagnostics.") {
            put("runtime", ping)
            put("runtime_home", runtime.homeDirectory)
            put("workspace", runtime.workspaceRoot)
            put("events_tail", "")
            put("last_crash", "")
        }
    }

    private fun resolveWorkspacePath(path: String, workingDirectory: String): String {
        val root = normalizeAbsolutePath(runtime.workspaceRoot, "/")
        val base = if (workingDirectory.startsWith('/')) {
            normalizeAbsolutePath(workingDirectory, root)
        } else {
            normalizeAbsolutePath(workingDirectory, root)
        }
        val resolved = normalizeAbsolutePath(path, base)
        require(resolved == root || resolved.startsWith("$root/")) {
            "Image path must stay inside the current workspace."
        }
        return resolved
    }
}

private fun JsonArrayBuilder.addGenericMcpDefinitions() {
    add(agentToolDefinition("mcp_list_tools", "List callable MCP tools across all connected servers or for one server.", "parallel", properties = mcpServerFilterProperties()))
    add(agentToolDefinition(
        "mcp_call_tool",
        "Call an MCP tool by server id and tool name.",
        "parallel",
        listOf("server_id", "tool_name"),
        mcpServerFilterProperties() + mapOf(
            "tool_name" to agentStringSchema("The MCP tool name to invoke."),
            "toolName" to agentStringSchema("Alias of tool_name."),
            "arguments" to buildJsonObject {
                put("type", "object")
                put("description", "Arguments to pass to the MCP tool.")
                put("additionalProperties", true)
            },
        ),
    ))
    add(agentToolDefinition("mcp_list_resources", "List available MCP resources across all connected servers or for one server.", "parallel", properties = mcpServerFilterProperties()))
    add(agentToolDefinition(
        "mcp_read_resource",
        "Read a specific MCP resource from a connected server.",
        "parallel",
        listOf("server_id", "uri"),
        mcpServerFilterProperties() + ("uri" to agentStringSchema("The MCP resource URI.")),
    ))
    add(agentToolDefinition("mcp_list_prompts", "List available MCP prompts across all connected servers or for one server.", "parallel", properties = mcpServerFilterProperties()))
    add(agentToolDefinition(
        "mcp_get_prompt",
        "Fetch a rendered MCP prompt from a connected server.",
        "parallel",
        listOf("server_id", "name"),
        mcpServerFilterProperties() + mapOf(
            "name" to agentStringSchema("The MCP prompt name."),
            "arguments" to buildJsonObject {
                put("type", "object")
                put("description", "Optional prompt arguments.")
                put("additionalProperties", true)
            },
        ),
    ))
}

private fun JsonArrayBuilder.addSelfManagementDefinitions() {
    add(agentToolDefinition(
        "aether_config_get",
        "Read Aether configuration for general preferences, web tools, reliability, Skills, MCP servers, and developer diagnostics. LLM provider secrets are omitted.",
        "parallel",
        properties = mapOf("categories" to agentStringArraySchema("Optional categories to read.")),
    ))
    add(agentToolDefinition(
        "aether_config_set",
        "Modify allowed Aether general, web-tools, or reliability settings. Provider and model configuration cannot be changed.",
        "sequential",
        listOf("category", "settings"),
        mapOf(
            "category" to agentStringSchema("One of general, web_tools, reliability."),
            "settings" to buildJsonObject { put("type", "object"); put("additionalProperties", true) },
        ),
    ))
    add(agentToolDefinition(
        "aether_skill_manage",
        "List, remotely install, enable, disable, or remove Aether Agent Skills.",
        "sequential",
        listOf("action"),
        mapOf(
            "action" to agentStringSchema("One of list, install_remote, remove, set_enabled."),
            "skill_id" to agentStringSchema("Installed Skill id."),
            "url" to agentStringSchema("HTTPS GitHub or zip URL."),
            "enabled" to agentBooleanSchema("Whether the Skill is enabled."),
        ),
    ))
    add(agentToolDefinition(
        "aether_mcp_manage",
        "List, add, update, enable, disable, or remove Aether MCP server configurations.",
        "sequential",
        listOf("action"),
        mapOf(
            "action" to agentStringSchema("One of list, upsert_streamable_http, upsert_stdio, remove, set_enabled."),
            "server_id" to agentStringSchema("MCP server id."),
            "display_name" to agentStringSchema("Display name."),
            "url" to agentStringSchema("Streamable HTTP URL."),
            "command" to agentStringSchema("stdio command."),
            "arguments" to agentStringArraySchema("stdio arguments."),
            "args" to agentStringArraySchema("Alias of arguments."),
            "working_directory" to agentStringSchema("stdio working directory."),
            "enabled" to agentBooleanSchema("Whether the server is enabled."),
            "headers" to agentKeyValueSchema("HTTP headers."),
            "environment" to agentKeyValueSchema("stdio environment."),
            "runtime" to agentStringSchema("Runtime environment."),
            "runtime_environment" to agentStringSchema("Runtime environment."),
            "connect_timeout_millis" to agentIntegerSchema("Connection timeout."),
            "request_timeout_millis" to agentIntegerSchema("Request timeout."),
        ),
    ))
    add(agentToolDefinition(
        "aether_extension_manage",
        "List or reload source-compatible Pi extensions for the current Aether session, or invoke a registered extension command.",
        "sequential",
        listOf("action"),
        mapOf(
            "action" to agentStringSchema("One of list, reload, invoke_command."),
            "command" to agentStringSchema("Registered extension command."),
            "args" to agentStringSchema("Raw command argument string."),
        ),
    ))
    add(agentToolDefinition(
        "aether_developer_manage",
        "Read non-sensitive Aether runtime diagnostics.",
        "parallel",
        listOf("action"),
        mapOf(
            "action" to agentStringSchema("read_diagnostics"),
            "include" to agentStringSchema("events, last_crash, or both."),
            "max_chars" to agentIntegerSchema("Maximum diagnostic characters."),
        ),
    ))
}

private fun agentToolDefinition(
    name: String,
    description: String,
    executionMode: String,
    required: List<String> = emptyList(),
    properties: Map<String, JsonObject> = emptyMap(),
): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("execution_mode", executionMode)
    put("parameters", buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        put("required", JsonArray(required.map(::JsonPrimitive)))
        put("additionalProperties", false)
    })
}

private fun agentStringSchema(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun agentIntegerSchema(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun agentBooleanSchema(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

private fun agentStringArraySchema(description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    put("items", buildJsonObject { put("type", "string") })
}

private fun agentKeyValueSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("object"), JsonPrimitive("array"))))
    put("description", description)
    put("additionalProperties", true)
}

private fun mcpServerFilterProperties(): Map<String, JsonObject> = mapOf(
    "server_id" to agentStringSchema("Optional MCP server id to filter by."),
    "serverId" to agentStringSchema("Alias of server_id."),
)

private inline fun agentToolSuccess(
    stdout: String,
    content: JsonObjectBuilder.() -> Unit = {},
): SharedHostToolResult = SharedHostToolResult(
    buildJsonObject {
        put("ok", true)
        put("stdout", stdout)
        content()
    }.toString()
)

private fun agentToolFailure(error: Throwable): SharedHostToolResult = SharedHostToolResult(
    buildJsonObject {
        put("ok", false)
        put("errmsg", error.message ?: "Aether host tool failed.")
    }.toString(),
    isError = true,
)

private val GenericMcpToolNames = setOf(
    "mcp_list_tools",
    "mcp_call_tool",
    "mcp_list_resources",
    "mcp_read_resource",
    "mcp_list_prompts",
    "mcp_get_prompt",
)

private fun skillsJson(skills: List<SharedInstalledSkill>): JsonArray = buildJsonArray {
    skills.sortedBy { it.name.lowercase() }.forEach { skill ->
        add(buildJsonObject {
            put("id", skill.id)
            put("name", skill.name)
            put("description", skill.description)
            put("is_enabled", skill.isEnabled)
            put("compatibility", skill.compatibility)
            put("license", skill.license)
            put("source", skill.source)
            put("allowed_tools", JsonArray(skill.allowedTools.map(::JsonPrimitive)))
            put("resource_count", skill.resourceCount)
        })
    }
}

private fun mcpServersJson(servers: List<SharedMcpServerConfig>): JsonArray = buildJsonArray {
    servers.sortedBy { it.name.lowercase() }.forEach { server ->
        add(buildJsonObject {
            put("id", server.id)
            put("display_name", server.name)
            put("action_label", server.actionLabel)
            put("is_enabled", server.enabled)
            put("transport", buildJsonObject {
                put("type", if (server.transport == SharedMcpTransport.Stdio) "stdio" else "streamable_http")
                if (server.transport == SharedMcpTransport.Http) {
                    put("url", server.url)
                    put("headers", redactedKeyValues(server.headers))
                } else {
                    put("command", server.command)
                    put("arguments", JsonArray(server.arguments.map(::JsonPrimitive)))
                    put("working_directory", server.workingDirectory)
                    put("environment", redactedKeyValues(server.environment))
                    put("runtime_environment", server.runtimeEnvironment)
                }
            })
            put("connect_timeout_millis", server.connectTimeoutMillis)
            put("request_timeout_millis", server.requestTimeoutMillis)
        })
    }
}

private fun redactedKeyValues(values: Map<String, String>): JsonArray = buildJsonArray {
    values.forEach { (key, value) ->
        add(buildJsonObject {
            put("key", key)
            put("value", if (isSensitiveAgentKey(key)) redactAgentSecret(value) else value)
        })
    }
}

private fun isSensitiveAgentKey(key: String): Boolean {
    val normalized = key.lowercase()
    return listOf("authorization", "token", "secret", "password", "api_key", "apikey", "cookie")
        .any(normalized::contains)
}

private fun normalizeRelativeResourcePath(path: String): String {
    val normalized = path.replace('\\', '/').trim('/')
    require(
        normalized.isNotBlank() &&
            !normalized.startsWith("../") &&
            !normalized.contains("/../")
    ) {
        "relative_path must stay inside the active Skill directory."
    }
    return normalized
}

private fun agentShellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun normalizeAbsolutePath(path: String, base: String): String {
    val expanded = when {
        path.startsWith('/') -> path
        else -> "${base.trimEnd('/')}/$path"
    }
    val parts = mutableListOf<String>()
    expanded.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return "/" + parts.joinToString("/")
}

private fun imageMimeType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    else -> null
}

private val TextResourceExtensions = setOf(
    "txt", "md", "markdown", "json", "jsonl", "yaml", "yml", "toml", "xml", "html", "htm",
    "css", "csv", "tsv", "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "swift", "py",
    "rb", "rs", "go", "java", "c", "h", "cpp", "hpp", "sh", "bash", "zsh", "fish", "sql",
)

private fun isLikelyTextResource(path: String, bytes: ByteArray): Boolean {
    if (path.substringAfterLast('.', "").lowercase() in TextResourceExtensions) return true
    val sample = bytes.take(512)
    return sample.none { it == 0.toByte() }
}

private val AgentBase64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun ByteArray.encodeAgentBase64(): String = buildString(((size + 2) / 3) * 4) {
    var index = 0
    while (index < size) {
        val first = this@encodeAgentBase64[index++].toInt() and 0xff
        val hasSecond = index < size
        val second = if (hasSecond) this@encodeAgentBase64[index++].toInt() and 0xff else 0
        val hasThird = index < size
        val third = if (hasThird) this@encodeAgentBase64[index++].toInt() and 0xff else 0
        append(AgentBase64Alphabet[first shr 2])
        append(AgentBase64Alphabet[((first and 0x03) shl 4) or (second shr 4)])
        append(if (hasSecond) AgentBase64Alphabet[((second and 0x0f) shl 2) or (third shr 6)] else '=')
        append(if (hasThird) AgentBase64Alphabet[third and 0x3f] else '=')
    }
}

private fun redactAgentSecret(value: String): String = when {
    value.isBlank() -> ""
    value.length <= 8 -> "********"
    else -> value.take(3) + "..." + value.takeLast(3)
}

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String): Int? =
    get(name)?.jsonPrimitive?.intOrNull

private fun JsonObject.long(name: String): Long? =
    get(name)?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private fun JsonObject.boolean(name: String): Boolean =
    get(name)?.jsonPrimitive?.booleanOrNull ?: false

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    get(name)?.jsonPrimitive?.booleanOrNull

private fun JsonObject.stringList(name: String): List<String> =
    (get(name) as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

private fun JsonObject.stringMap(name: String): Map<String, String> = when (val value = get(name)) {
    is JsonObject -> value.mapNotNull { (key, entry) ->
        entry.jsonPrimitive.contentOrNull?.let { key to it }
    }.toMap()
    is JsonArray -> value.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        entry.string("key").takeIf(String::isNotBlank)?.let { it to entry.string("value") }
    }.toMap()
    else -> emptyMap()
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
