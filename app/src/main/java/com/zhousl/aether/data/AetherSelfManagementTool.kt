package com.zhousl.aether.data

import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxSetupState
import java.util.Locale
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private const val DefaultDeveloperLogTailChars = 20_000
private const val MaxDeveloperLogTailChars = 80_000

class AetherSelfManagementTool(
    private val settingsRepository: SettingsRepository,
    private val extensionsRepository: AgentExtensionsRepository,
    private val skillManager: AgentSkillManager,
    private val bashTool: TermuxBashTool,
    private val rootSetupController: RootSetupController,
    private val agentModeController: AgentModeController,
    private val mcpClientManager: McpClientManager,
    private val scheduledTaskManager: ScheduledTaskManager,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    fun toolDefinitions(): List<JSONObject> = listOf(
        buildAetherToolDefinition(
            name = "aether_config_get",
            description = "Read Aether app configuration for non-LLM-provider areas: general app preferences, web tools, reliability, skills, MCP servers, Termux, Agent Mode, and developer diagnostics. Provider, model, base URL, and LLM API key settings are intentionally omitted.",
            properties = JSONObject().apply {
                put(
                    "categories",
                    JSONObject().apply {
                        put("type", "array")
                        put("description", "Optional categories to read. Omit or pass an empty array to read all supported categories.")
                        put(
                            "items",
                            JSONObject().apply {
                                put("type", "string")
                                put(
                                    "enum",
                                    JSONArray(
                                        listOf(
                                            "general",
                                            "web_tools",
                                            "reliability",
                                            "agent_skills",
                                            "mcp_servers",
                                            "termux",
                                            "agent_mode",
                                            "scheduled_tasks",
                                            "developer",
                                        )
                                    ),
                                )
                            },
                        )
                    },
                )
            },
        ),
        buildAetherToolDefinition(
            name = "aether_config_set",
            description = "Modify allowed Aether settings. This tool cannot modify LLM provider, model, base URL, provider API keys, or default model selections.",
            properties = JSONObject().apply {
                put(
                    "category",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("general", "web_tools", "reliability", "agent_mode")))
                        put("description", "Allowed settings category to update.")
                    },
                )
                put(
                    "settings",
                    JSONObject().apply {
                        put("type", "object")
                        put("description", "Patch object for the selected category.")
                        put("additionalProperties", true)
                    },
                )
            },
            required = listOf("category", "settings"),
        ),
        buildAetherToolDefinition(
            name = "aether_skill_manage",
            description = "List, install, enable, disable, or remove Aether Agent Skills. Remote installs accept GitHub repository URLs or zip URLs supported by Aether.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("list", "install_remote", "remove", "set_enabled")))
                    },
                )
                put("skill_id", JSONObject().apply { put("type", "string") })
                put("url", JSONObject().apply { put("type", "string") })
                put("enabled", JSONObject().apply { put("type", "boolean") })
            },
            required = listOf("action"),
        ),
        buildAetherToolDefinition(
            name = "aether_mcp_manage",
            description = "List, add, update, enable, disable, or remove Aether MCP server configurations. Supports streamable HTTP and stdio MCP servers.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put(
                            "enum",
                            JSONArray(
                                listOf(
                                    "list",
                                    "upsert_streamable_http",
                                    "upsert_stdio",
                                    "remove",
                                    "set_enabled",
                                )
                            ),
                        )
                    },
                )
                put("server_id", JSONObject().apply { put("type", "string") })
                put("display_name", JSONObject().apply { put("type", "string") })
                put("url", JSONObject().apply { put("type", "string") })
                put("command", JSONObject().apply { put("type", "string") })
                put("arguments", stringArraySchema("Command-line arguments for stdio MCP servers."))
                put("args", stringArraySchema("Alias for arguments."))
                put("working_directory", JSONObject().apply { put("type", "string") })
                put("enabled", JSONObject().apply { put("type", "boolean") })
                put("connect_timeout_millis", JSONObject().apply { put("type", "integer") })
                put("request_timeout_millis", JSONObject().apply { put("type", "integer") })
                put("headers", keyValueArraySchema("HTTP headers for streamable HTTP MCP servers."))
                put("environment", keyValueArraySchema("Environment variables for stdio MCP servers."))
            },
            required = listOf("action"),
        ),
        buildAetherToolDefinition(
            name = "aether_termux_manage",
            description = "Inspect or repair Aether's Termux integration without using the bash tool. Root setup may trigger a system su request.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("inspect_setup", "inspect_root_setup", "configure_root_access")))
                    },
                )
            },
            required = listOf("action"),
        ),
        buildAetherToolDefinition(
            name = "aether_agent_mode_manage",
            description = "Read or change Agent Mode authorization settings, refresh status, request Shizuku permission, and manage the current virtual display. Does not operate the display UI; use agent_display for that when Agent Mode is enabled for the chat.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put(
                            "enum",
                            JSONArray(
                                listOf(
                                    "inspect_authorization",
                                    "set_authorization",
                                    "refresh_authorization",
                                    "request_shizuku_permission",
                                    "stop_display",
                                    "refresh_displays",
                                )
                            ),
                        )
                    },
                )
                put("enabled", JSONObject().apply { put("type", "boolean") })
                put(
                    "method",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("root", "shizuku")))
                    },
                )
            },
            required = listOf("action"),
        ),
        buildAetherToolDefinition(
            name = "aether_scheduled_task_manage",
            description = "List, create, update, enable, disable, or remove Aether scheduled tasks. Scheduled tasks wake Aether at the next matching time and run the prompt as an automated Agent turn.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("list", "create", "update", "remove", "set_enabled")))
                    },
                )
                put("task_id", JSONObject().apply { put("type", "string") })
                put("name", JSONObject().apply { put("type", "string") })
                put("prompt", JSONObject().apply { put("type", "string") })
                put("session_id", JSONObject().apply { put("type", "string") })
                put("enabled", JSONObject().apply { put("type", "boolean") })
                put(
                    "schedule",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "description",
                            "Schedule object. interval: {type:'interval', interval_minutes:30, active_start_time:'13:00', active_end_time:'18:00'}; daily: {type:'daily', times:['09:00','17:30']}; weekly: {type:'weekly', days_of_week:['mon','fri'], time:'10:00'}.",
                        )
                        put("additionalProperties", true)
                    },
                )
            },
            required = listOf("action"),
        ),
        buildAetherToolDefinition(
            name = "aether_developer_manage",
            description = "Read Aether developer diagnostics such as recent diagnostic events or last crash details. Sensitive values are redacted.",
            properties = JSONObject().apply {
                put(
                    "action",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("read_diagnostics")))
                    },
                )
                put(
                    "include",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("events", "last_crash", "both")))
                    },
                )
                put("max_chars", JSONObject().apply { put("type", "integer") })
            },
            required = listOf("action"),
        ),
    )

    suspend fun execute(
        toolName: String,
        argumentsJson: String,
    ): String = when (toolName) {
        "aether_config_get" -> executeConfigGet(argumentsJson)
        "aether_config_set" -> executeConfigSet(argumentsJson)
        "aether_skill_manage" -> executeSkillManage(argumentsJson)
        "aether_mcp_manage" -> executeMcpManage(argumentsJson)
        "aether_termux_manage" -> executeTermuxManage(argumentsJson)
        "aether_agent_mode_manage" -> executeAgentModeManage(argumentsJson)
        "aether_scheduled_task_manage" -> executeScheduledTaskManage(argumentsJson)
        "aether_developer_manage" -> executeDeveloperManage(argumentsJson)
        else -> failure("Unknown Aether self-management tool '$toolName'.")
    }

    private suspend fun executeConfigGet(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        val requestedCategories = parseCategories(arguments.optJSONArray("categories"))
        val categories = if (requestedCategories.isEmpty()) {
            SupportedConfigCategories
        } else {
            requestedCategories
        }
        val settings = settingsRepository.settings.first()
        val extensionState = extensionsRepository.extensionState.first()
        val payload = JSONObject()
        categories.forEach { category ->
            when (category) {
                "general" -> payload.put("general", generalSettingsJson(settings))
                "web_tools" -> payload.put("web_tools", webToolsSettingsJson(settings))
                "reliability" -> payload.put("reliability", reliabilitySettingsJson(settings))
                "agent_skills" -> payload.put("agent_skills", skillsJson(extensionState.installedSkills))
                "mcp_servers" -> payload.put("mcp_servers", mcpServersJson(extensionState.mcpServers))
                "termux" -> payload.put("termux", termuxStatusJson())
                "agent_mode" -> payload.put("agent_mode", agentModeSettingsJson(settings))
                "scheduled_tasks" -> payload.put("scheduled_tasks", scheduledTasksJson(scheduledTaskManager.snapshot()))
                "developer" -> payload.put("developer", developerSummaryJson())
                else -> return failure("Unsupported category '$category'.")
            }
        }
        return success(payload) {
            put("stdout", "Read ${categories.size} Aether configuration categories.")
        }
    }

    private suspend fun executeConfigSet(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        val category = arguments.optString("category").trim().lowercase(Locale.US)
        val patch = arguments.optJSONObject("settings") ?: return failure("settings must be an object.")
        val current = settingsRepository.settings.first()
        val updated = when (category) {
            "general" -> current.copy(
                language = if (patch.hasAny("language", "app_language")) {
                    AppLanguage.fromStorage(patch.optStringAny("language", "app_language"), current.language)
                } else {
                    current.language
                },
                themeMode = if (patch.hasAny("theme_mode", "themeMode")) {
                    AppThemeMode.fromStorage(patch.optStringAny("theme_mode", "themeMode"))
                } else {
                    current.themeMode
                },
            )

            "web_tools" -> current.copy(
                tavilyApiKey = if (patch.hasAny("tavily_api_key", "tavilyApiKey")) {
                    patch.optStringAny("tavily_api_key", "tavilyApiKey").trim()
                } else {
                    current.tavilyApiKey
                },
                tavilyBaseUrl = if (patch.hasAny("tavily_base_url", "tavilyBaseUrl")) {
                    normalizeTavilyBaseUrl(patch.optStringAny("tavily_base_url", "tavilyBaseUrl"))
                } else {
                    current.tavilyBaseUrl
                },
            )

            "reliability" -> current.copy(
                llmInactivityReconnectTimeoutSeconds = if (
                    patch.hasAny(
                            "llm_inactivity_reconnect_timeout_seconds",
                            "llmInactivityReconnectTimeoutSeconds",
                        )
                ) {
                    normalizeLlmInactivityReconnectTimeoutSeconds(
                        patch.optIntAny(
                            "llm_inactivity_reconnect_timeout_seconds",
                            "llmInactivityReconnectTimeoutSeconds",
                        )
                    )
                } else {
                    current.llmInactivityReconnectTimeoutSeconds
                },
                keepTasksRunningInBackground = if (
                    patch.hasAny("keep_tasks_running_in_background", "keepTasksRunningInBackground")
                ) {
                    patch.optBooleanAny(
                        "keep_tasks_running_in_background",
                        "keepTasksRunningInBackground",
                        current.keepTasksRunningInBackground,
                    )
                } else {
                    current.keepTasksRunningInBackground
                },
                notifyOnTaskCompletion = if (
                    patch.hasAny("notify_on_task_completion", "notifyOnTaskCompletion")
                ) {
                    patch.optBooleanAny(
                        "notify_on_task_completion",
                        "notifyOnTaskCompletion",
                        current.notifyOnTaskCompletion,
                    )
                } else {
                    current.notifyOnTaskCompletion
                },
            )

            "agent_mode" -> current.copy(
                agentModeAuthorizationEnabled = if (
                    patch.hasAny("agent_mode_authorization_enabled", "authorization_enabled", "enabled")
                ) {
                    patch.optBooleanAny(
                        "agent_mode_authorization_enabled",
                        "authorization_enabled",
                        current.agentModeAuthorizationEnabled,
                    )
                } else {
                    current.agentModeAuthorizationEnabled
                },
                agentModeAuthorizationMethod = if (
                    patch.hasAny("agent_mode_authorization_method", "authorization_method", "method")
                ) {
                    AgentModeAuthorizationMethod.fromStorage(
                        patch.optStringAny(
                            "agent_mode_authorization_method",
                            "authorization_method",
                            "method",
                        ),
                        defaultValue = current.agentModeAuthorizationMethod,
                    )
                } else {
                    current.agentModeAuthorizationMethod
                },
            )

            else -> return failure("Unsupported or read-only category '$category'.")
        }
        settingsRepository.updateSettings(updated)
        if (category == "agent_mode") {
            agentModeController.refreshAuthorization(updated)
        }
        return success(JSONObject().put(category, configCategoryJson(category, updated))) {
            put("stdout", "Updated Aether $category settings.")
        }
    }

    private suspend fun executeSkillManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "list" -> {
                val skills = extensionsRepository.extensionState.first().installedSkills
                success(JSONObject().put("skills", skillsJson(skills))) {
                    put("stdout", "Listed ${skills.size} installed skills.")
                }
            }

            "install_remote" -> {
                val url = arguments.optString("url").trim()
                if (url.isBlank()) return failure("url is required for install_remote.")
                val installed = skillManager.installSkillFromRemote(url)
                    .getOrElse { throwable -> return failure(throwable.message ?: "Skill install failed.") }
                success(JSONObject().put("skill", installedSkillJson(installed))) {
                    put("stdout", "Installed skill '${installed.name}'.")
                }
            }

            "remove" -> {
                val skillId = arguments.optString("skill_id").trim()
                    .ifBlank { arguments.optString("skillId").trim() }
                if (skillId.isBlank()) return failure("skill_id is required for remove.")
                skillManager.uninstallSkill(skillId)
                    .getOrElse { throwable -> return failure(throwable.message ?: "Skill removal failed.") }
                success(JSONObject().put("skill_id", skillId)) {
                    put("stdout", "Removed skill '$skillId'.")
                }
            }

            "set_enabled" -> {
                val skillId = arguments.optString("skill_id").trim()
                    .ifBlank { arguments.optString("skillId").trim() }
                if (skillId.isBlank()) return failure("skill_id is required for set_enabled.")
                if (!arguments.has("enabled")) return failure("enabled is required for set_enabled.")
                val enabled = arguments.optBoolean("enabled")
                extensionsRepository.setSkillEnabled(skillId, enabled)
                success(JSONObject().put("skill_id", skillId).put("enabled", enabled)) {
                    put("stdout", "Set skill '$skillId' enabled=$enabled.")
                }
            }

            else -> failure("Unsupported skill action '$action'.")
        }
    }

    private suspend fun executeMcpManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "list" -> {
                val servers = extensionsRepository.extensionState.first().mcpServers
                success(JSONObject().put("servers", mcpServersJson(servers))) {
                    put("stdout", "Listed ${servers.size} MCP servers.")
                }
            }

            "upsert_streamable_http" -> upsertMcpServer(arguments, McpTransportType.StreamableHttp)
            "upsert_stdio" -> upsertMcpServer(arguments, McpTransportType.StdIo)

            "remove" -> {
                val serverId = mcpServerId(arguments)
                if (serverId.isBlank()) return failure("server_id is required for remove.")
                extensionsRepository.removeMcpServer(serverId)
                mcpClientManager.disconnect(serverId)
                success(JSONObject().put("server_id", serverId)) {
                    put("stdout", "Removed MCP server '$serverId'.")
                }
            }

            "set_enabled" -> {
                val serverId = mcpServerId(arguments)
                if (serverId.isBlank()) return failure("server_id is required for set_enabled.")
                if (!arguments.has("enabled")) return failure("enabled is required for set_enabled.")
                val enabled = arguments.optBoolean("enabled")
                extensionsRepository.setMcpServerEnabled(serverId, enabled)
                if (!enabled) {
                    mcpClientManager.disconnect(serverId)
                }
                success(JSONObject().put("server_id", serverId).put("enabled", enabled)) {
                    put("stdout", "Set MCP server '$serverId' enabled=$enabled.")
                }
            }

            else -> failure("Unsupported MCP action '$action'.")
        }
    }

    private suspend fun upsertMcpServer(
        arguments: JSONObject,
        transportType: McpTransportType,
    ): String {
        val existing = mcpServerId(arguments)
            .takeIf(String::isNotBlank)
            ?.let { serverId ->
                extensionsRepository.extensionState.first().mcpServers.firstOrNull { it.id == serverId }
            }
        val now = System.currentTimeMillis()
        val displayName = arguments.optString("display_name").trim()
            .ifBlank { arguments.optString("displayName").trim() }
            .ifBlank { existing?.displayName.orEmpty() }
        if (displayName.isBlank()) return failure("display_name is required.")

        val transport = when (transportType) {
            McpTransportType.StreamableHttp -> {
                val url = arguments.optString("url").trim()
                    .ifBlank {
                        (existing?.transport as? McpTransportConfig.StreamableHttp)?.url.orEmpty()
                    }
                if (url.isBlank()) return failure("url is required for upsert_streamable_http.")
                McpTransportConfig.StreamableHttp(
                    url = url,
                    headers = parseKeyValues(arguments.optJSONArray("headers")),
                )
            }

            McpTransportType.StdIo -> {
                val command = arguments.optString("command").trim()
                    .ifBlank {
                        (existing?.transport as? McpTransportConfig.StdIo)?.command.orEmpty()
                    }
                if (command.isBlank()) return failure("command is required for upsert_stdio.")
                McpTransportConfig.StdIo(
                    command = command,
                    arguments = if (arguments.hasAny("arguments", "args")) {
                        parseStringArray(arguments.optJSONArray("arguments"))
                            .ifEmpty { parseStringArray(arguments.optJSONArray("args")) }
                    } else {
                        (existing?.transport as? McpTransportConfig.StdIo)?.arguments.orEmpty()
                    },
                    workingDirectory = arguments.optString("working_directory").trim()
                        .ifBlank { arguments.optString("workingDirectory").trim() }
                        .ifBlank {
                            (existing?.transport as? McpTransportConfig.StdIo)?.workingDirectory.orEmpty()
                        },
                    environment = parseKeyValues(arguments.optJSONArray("environment")),
                )
            }
        }

        val server = McpServerConfig(
            id = existing?.id ?: mcpServerId(arguments).ifBlank { "mcp-$now" },
            displayName = displayName,
            actionLabel = generateQuickActionLabel(displayName, transport.quickActionSource()),
            transport = transport,
            isEnabled = arguments.optNullableBoolean("enabled") ?: existing?.isEnabled ?: true,
            connectTimeoutMillis = arguments.optNullableLong("connect_timeout_millis")
                ?: arguments.optNullableLong("connectTimeoutMillis")
                ?: existing?.connectTimeoutMillis
                ?: 15_000L,
            requestTimeoutMillis = arguments.optNullableLong("request_timeout_millis")
                ?: arguments.optNullableLong("requestTimeoutMillis")
                ?: existing?.requestTimeoutMillis
                ?: 60_000L,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        extensionsRepository.upsertMcpServer(server)
        if (existing != null) {
            mcpClientManager.disconnect(existing.id)
        }
        return success(JSONObject().put("server", mcpServerJson(server))) {
            put("stdout", "Saved MCP server '${server.displayName}'.")
        }
    }

    private suspend fun executeTermuxManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "inspect_setup" -> success(JSONObject().put("termux", termuxSetupStateJson(bashTool.inspectSetup())))
            "inspect_root_setup" -> success(JSONObject().put("root_setup", rootSetupStateJson(rootSetupController.inspect())))
            "configure_root_access" -> {
                val rootState = rootSetupController.configureLocalAccess()
                success(JSONObject().put("root_setup", rootSetupStateJson(rootState))) {
                    put(
                        "stdout",
                        if (rootState.isReady) {
                            "Root setup completed."
                        } else {
                            "Root setup did not complete: ${rootState.detail.ifBlank { rootState.issue.name }}"
                        },
                    )
                }
            }

            else -> failure("Unsupported Termux action '$action'.")
        }
    }

    private suspend fun executeAgentModeManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        val current = settingsRepository.settings.first()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "inspect_authorization" -> success(JSONObject().put("agent_mode", agentModeSettingsJson(current)))

            "set_authorization" -> {
                val updated = current.copy(
                    agentModeAuthorizationEnabled = arguments.optNullableBoolean("enabled")
                        ?: current.agentModeAuthorizationEnabled,
                    agentModeAuthorizationMethod = arguments.optString("method").trim()
                        .takeIf(String::isNotBlank)
                        ?.let {
                            AgentModeAuthorizationMethod.fromStorage(
                                it,
                                defaultValue = current.agentModeAuthorizationMethod,
                            )
                        }
                        ?: current.agentModeAuthorizationMethod,
                )
                settingsRepository.updateSettings(updated)
                agentModeController.refreshAuthorization(updated)
                success(JSONObject().put("agent_mode", agentModeSettingsJson(updated))) {
                    put("stdout", "Updated Agent Mode authorization settings.")
                }
            }

            "refresh_authorization" -> {
                agentModeController.refreshAuthorization(current)
                success(JSONObject().put("agent_mode", agentModeSettingsJson(current))) {
                    put("stdout", "Refreshed Agent Mode authorization.")
                }
            }

            "request_shizuku_permission" -> {
                val state = agentModeController.requestShizukuPermission()
                success(JSONObject().put("authorization", agentModeAuthorizationStateJson(state))) {
                    put("stdout", state.detail.ifBlank { state.issue.name })
                }
            }

            "stop_display" -> {
                agentModeController.stopDisplay()
                success(JSONObject().put("display", agentModeDisplayStateJson(agentModeController.displayState.value))) {
                    put("stdout", "Stopped Agent Mode display if one was active.")
                }
            }

            "refresh_displays" -> {
                agentModeController.refreshDisplays(current)
                success(JSONObject().put("display", agentModeDisplayStateJson(agentModeController.displayState.value))) {
                    put("stdout", "Refreshed Agent Mode display list.")
                }
            }

            else -> failure("Unsupported Agent Mode action '$action'.")
        }
    }

    private suspend fun executeScheduledTaskManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "list" -> {
                val tasks = scheduledTaskManager.snapshot()
                success(JSONObject().put("tasks", scheduledTasksJson(tasks))) {
                    put("stdout", "Listed ${tasks.size} scheduled tasks.")
                }
            }

            "create" -> {
                val name = arguments.optString("name").trim().ifBlank { "Scheduled task" }
                val prompt = arguments.optString("prompt").trim()
                if (prompt.isBlank()) return failure("prompt is required for create.")
                val schedule = parseScheduledTaskSchedule(arguments.optJSONObject("schedule"))
                    ?: return failure("schedule is required and must be a valid interval, daily, or weekly schedule.")
                val task = scheduledTaskManager.upsertTask(
                    ScheduledTask(
                        name = name,
                        prompt = prompt,
                        schedule = schedule,
                        isEnabled = arguments.optNullableBoolean("enabled") ?: true,
                        sessionId = optAetherString(arguments, "session_id", "sessionId"),
                        createdBy = ScheduledTaskCreator.Agent,
                    )
                )
                success(JSONObject().put("task", task.toPublicJson())) {
                    put("stdout", "Created scheduled task '${task.name}'.")
                }
            }

            "update" -> {
                val taskId = optAetherString(arguments, "task_id", "taskId")
                if (taskId.isBlank()) return failure("task_id is required for update.")
                val existing = scheduledTaskManager.findTask(taskId)
                    ?: return failure("Scheduled task '$taskId' was not found.")
                val schedule = if (arguments.has("schedule") && !arguments.isNull("schedule")) {
                    parseScheduledTaskSchedule(arguments.optJSONObject("schedule"))
                        ?: return failure("schedule must be a valid interval, daily, or weekly schedule.")
                } else {
                    existing.schedule
                }
                val enabled = arguments.optNullableBoolean("enabled") ?: existing.isEnabled
                val updated = scheduledTaskManager.upsertTask(
                    existing.copy(
                        name = arguments.optString("name").trim().ifBlank { existing.name },
                        prompt = arguments.optString("prompt").trim().ifBlank { existing.prompt },
                        schedule = schedule,
                        isEnabled = enabled,
                        sessionId = optAetherString(arguments, "session_id", "sessionId").ifBlank { existing.sessionId },
                    )
                )
                success(JSONObject().put("task", updated.toPublicJson())) {
                    put("stdout", "Updated scheduled task '${updated.name}'.")
                }
            }

            "remove" -> {
                val taskId = optAetherString(arguments, "task_id", "taskId")
                if (taskId.isBlank()) return failure("task_id is required for remove.")
                scheduledTaskManager.removeTask(taskId)
                success(JSONObject().put("task_id", taskId)) {
                    put("stdout", "Removed scheduled task '$taskId'.")
                }
            }

            "set_enabled" -> {
                val taskId = optAetherString(arguments, "task_id", "taskId")
                if (taskId.isBlank()) return failure("task_id is required for set_enabled.")
                if (!arguments.has("enabled")) return failure("enabled is required for set_enabled.")
                val task = scheduledTaskManager.setTaskEnabled(taskId, arguments.optBoolean("enabled"))
                    ?: return failure("Scheduled task '$taskId' was not found.")
                success(JSONObject().put("task", task.toPublicJson())) {
                    put("stdout", "Set scheduled task '${task.name}' enabled=${task.isEnabled}.")
                }
            }

            else -> failure("Unsupported scheduled task action '$action'.")
        }
    }

    private fun executeDeveloperManage(argumentsJson: String): String {
        val arguments = parseArguments(argumentsJson) ?: return invalidJson()
        return when (val action = arguments.optString("action").trim().lowercase(Locale.US)) {
            "read_diagnostics" -> {
                val include = arguments.optString("include").trim().lowercase(Locale.US).ifBlank { "both" }
                val maxChars = arguments.optInt("max_chars", DefaultDeveloperLogTailChars)
                    .coerceIn(1_000, MaxDeveloperLogTailChars)
                val output = JSONObject()
                if (include == "events" || include == "both") {
                    output.put("events_tail", diagnosticLogger.readEventsText().takeLast(maxChars))
                }
                if (include == "last_crash" || include == "both") {
                    output.put("last_crash", diagnosticLogger.readLastCrashText().takeLast(maxChars))
                }
                success(output) {
                    put("stdout", "Read Aether diagnostics.")
                }
            }

            else -> failure("Unsupported developer action '$action'.")
        }
    }

    private suspend fun termuxStatusJson(): JSONObject =
        JSONObject()
            .put("setup", termuxSetupStateJson(bashTool.inspectSetup()))
            .put("root_setup", rootSetupStateJson(rootSetupController.inspect()))

    private fun generalSettingsJson(settings: AppSettings): JSONObject =
        JSONObject()
            .put("language", settings.language.storageValue)
            .put("theme_mode", settings.themeMode.storageValue)

    private fun webToolsSettingsJson(settings: AppSettings): JSONObject =
        JSONObject()
            .put("tavily_configured", settings.tavilyApiKey.isNotBlank())
            .put("tavily_api_key", redactSecret(settings.tavilyApiKey))
            .put("tavily_base_url", settings.tavilyBaseUrl)

    private fun reliabilitySettingsJson(settings: AppSettings): JSONObject =
        JSONObject()
            .put("llm_inactivity_reconnect_timeout_seconds", settings.llmInactivityReconnectTimeoutSeconds)
            .put("keep_tasks_running_in_background", settings.keepTasksRunningInBackground)
            .put("notify_on_task_completion", settings.notifyOnTaskCompletion)

    private fun agentModeSettingsJson(settings: AppSettings): JSONObject =
        JSONObject()
            .put("authorization_enabled", settings.agentModeAuthorizationEnabled)
            .put("authorization_method", settings.agentModeAuthorizationMethod.storageValue)
            .put("authorization", agentModeAuthorizationStateJson(agentModeController.authorizationState.value))
            .put("display", agentModeDisplayStateJson(agentModeController.displayState.value))

    private fun configCategoryJson(
        category: String,
        settings: AppSettings,
    ): JSONObject = when (category) {
        "general" -> generalSettingsJson(settings)
        "web_tools" -> webToolsSettingsJson(settings)
        "reliability" -> reliabilitySettingsJson(settings)
        "agent_mode" -> agentModeSettingsJson(settings)
        else -> JSONObject()
    }

    private fun developerSummaryJson(): JSONObject =
        JSONObject()
            .put("diagnostic_events_available", diagnosticLogger.readEventsText().isNotBlank())
            .put("last_crash_available", diagnosticLogger.readLastCrashText().isNotBlank())
            .put("tools", JSONArray(listOf("aether_developer_manage")))

    private fun scheduledTasksJson(tasks: List<ScheduledTask>): JSONArray =
        JSONArray().apply {
            tasks.sortedWith(compareBy<ScheduledTask> { it.nextRunAtMillis ?: Long.MAX_VALUE }.thenBy { it.name.lowercase(Locale.US) })
                .forEach { put(it.toPublicJson()) }
        }

    private fun skillsJson(skills: List<InstalledSkill>): JSONArray =
        JSONArray().apply {
            skills.sortedBy { it.name.lowercase(Locale.US) }.forEach { put(installedSkillJson(it)) }
        }

    private fun installedSkillJson(skill: InstalledSkill): JSONObject =
        JSONObject()
            .put("id", skill.id)
            .put("name", skill.name)
            .put("description", skill.description)
            .put("action_label", skill.quickActionLabel())
            .put("is_enabled", skill.isEnabled)
            .put("source", JSONObject().apply {
                put("kind", skill.source.kind.storageValue)
                put("label", skill.source.label)
                put("uri", skill.source.uri)
                put("ref", skill.source.ref)
                put("subpath", skill.source.subpath)
            })
            .put("allowed_tools", JSONArray().apply { skill.allowedTools.forEach(::put) })
            .put("diagnostics", JSONArray().apply { skill.diagnostics.forEach(::put) })
            .put("resource_count", skill.resourceEntries.size)
            .put("installed_at_millis", skill.installedAtMillis)
            .put("updated_at_millis", skill.updatedAtMillis)

    private fun mcpServersJson(servers: List<McpServerConfig>): JSONArray =
        JSONArray().apply {
            servers.sortedBy { it.displayName.lowercase(Locale.US) }.forEach { put(mcpServerJson(it)) }
        }

    private fun mcpServerJson(server: McpServerConfig): JSONObject =
        JSONObject()
            .put("id", server.id)
            .put("display_name", server.displayName)
            .put("action_label", server.quickActionLabel())
            .put("is_enabled", server.isEnabled)
            .put("transport", mcpTransportJson(server.transport))
            .put("connect_timeout_millis", server.connectTimeoutMillis)
            .put("request_timeout_millis", server.requestTimeoutMillis)
            .put("created_at_millis", server.createdAtMillis)
            .put("updated_at_millis", server.updatedAtMillis)

    private fun mcpTransportJson(transport: McpTransportConfig): JSONObject =
        JSONObject().apply {
            put("type", transport.transportType.storageValue)
            when (transport) {
                is McpTransportConfig.StreamableHttp -> {
                    put("url", transport.url)
                    put("headers", keyValuesJson(transport.headers, redactValues = true))
                }

                is McpTransportConfig.StdIo -> {
                    put("command", transport.command)
                    put("arguments", JSONArray().apply { transport.arguments.forEach(::put) })
                    put("working_directory", transport.workingDirectory)
                    put("environment", keyValuesJson(transport.environment, redactValues = true))
                }
            }
        }

    private fun keyValuesJson(
        values: List<McpKeyValue>,
        redactValues: Boolean,
    ): JSONArray = JSONArray().apply {
        values.forEach { item ->
            put(
                JSONObject()
                    .put("key", item.key)
                    .put(
                        "value",
                        if (redactValues && shouldRedactKey(item.key)) redactSecret(item.value) else item.value,
                    )
            )
        }
    }

    private fun termuxSetupStateJson(state: TermuxSetupState): JSONObject =
        JSONObject()
            .put("issue", state.issue.name)
            .put("detail", state.detail)
            .put("previously_configured", state.previouslyConfigured)
            .put("is_ready", state.isReady)

    private fun rootSetupStateJson(state: RootSetupState): JSONObject =
        JSONObject()
            .put("issue", state.issue.name)
            .put("detail", state.detail)
            .put("root_available", state.rootAvailable)
            .put("su_path", state.suPath)
            .put("did_launch_termux_for_background", state.didLaunchTermuxForBackground)
            .put("last_updated_millis", state.lastUpdatedMillis)
            .put("is_ready", state.isReady)
            .put("is_running", state.isRunning)

    private fun agentModeAuthorizationStateJson(state: AgentModeAuthorizationState): JSONObject =
        JSONObject()
            .put("issue", state.issue.name)
            .put("detail", state.detail)
            .put("is_ready", state.isReady)

    private fun agentModeDisplayStateJson(state: AgentModeDisplayState): JSONObject =
        JSONObject()
            .put("is_active", state.isActive)
            .put("display_id", state.displayId ?: JSONObject.NULL)
            .put("width", state.width)
            .put("height", state.height)
            .put("is_live_preview_active", state.isLivePreviewActive)
            .put("status", state.status)
            .put("last_updated_millis", state.lastUpdatedMillis)
            .put(
                "displays",
                JSONArray().apply {
                    state.displays.forEach { display ->
                        put(
                            JSONObject()
                                .put("display_id", display.displayId)
                                .put("name", display.name)
                                .put("width", display.width)
                                .put("height", display.height)
                                .put("is_aether_display", display.isAetherDisplay)
                        )
                    }
                },
            )

    private fun buildAetherToolDefinition(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String> = emptyList(),
    ): JSONObject = JSONObject().apply {
        put("type", "function")
        put(
            "function",
            JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", buildStrictToolParameters(properties, required))
            },
        )
    }

    private fun keyValueArraySchema(description: String): JSONObject =
        JSONObject().apply {
            put("type", "array")
            put("description", description)
            put(
                "items",
                JSONObject().apply {
                    put("type", "object")
                    put(
                        "properties",
                        JSONObject().apply {
                            put("key", JSONObject().apply { put("type", "string") })
                            put("value", JSONObject().apply { put("type", "string") })
                        },
                    )
                    put("required", JSONArray(listOf("key", "value")))
                    put("additionalProperties", false)
                },
            )
        }

    private fun stringArraySchema(description: String): JSONObject =
        JSONObject().apply {
            put("type", "array")
            put("description", description)
            put(
                "items",
                JSONObject().apply {
                    put("type", "string")
                },
            )
        }

    private fun parseArguments(argumentsJson: String): JSONObject? =
        runCatching { JSONObject(argumentsJson) }.getOrNull()

    private fun parseCategories(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val category = array.optString(index).trim().lowercase(Locale.US)
                if (category.isNotBlank()) add(category)
            }
        }.distinct()
    }

    private fun parseKeyValues(array: JSONArray?): List<McpKeyValue> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val key = json.optString("key").trim()
                if (key.isBlank()) continue
                add(McpKeyValue(key = key, value = json.optString("value")))
            }
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun mcpServerId(arguments: JSONObject): String =
        arguments.optString("server_id").trim().ifBlank {
            arguments.optString("serverId").trim()
        }

    private fun McpTransportConfig.quickActionSource(): String = when (this) {
        is McpTransportConfig.StreamableHttp -> url
        is McpTransportConfig.StdIo -> command
    }

    private fun success(
        payload: JSONObject = JSONObject(),
        configure: JSONObject.() -> Unit = {},
    ): String = JSONObject().apply {
        put("ok", true)
        payload.keys().forEach { key -> put(key, payload.opt(key)) }
        configure()
    }.toString()

    private fun failure(message: String): String =
        JSONObject()
            .put("ok", false)
            .put("errmsg", message)
            .toString()

    private fun invalidJson(): String = failure("Arguments were not valid JSON.")

    private fun redactSecret(value: String): String =
        if (value.isBlank()) "" else "[REDACTED]"

    private fun shouldRedactKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.US)
        return SensitiveKeyFragments.any { it in normalized }
    }

    private fun JSONObject.hasAny(vararg names: String): Boolean =
        names.any(::has)

    private fun JSONObject.optStringAny(vararg names: String): String =
        names.firstOrNull(::has)?.let(::optString).orEmpty()

    private fun JSONObject.optIntAny(vararg names: String): Int? =
        names.firstOrNull(::has)?.let { optInt(it) }

    private fun JSONObject.optBooleanAny(
        primary: String,
        secondary: String,
        defaultValue: Boolean,
    ): Boolean = when {
        has(primary) -> optBoolean(primary, defaultValue)
        has(secondary) -> optBoolean(secondary, defaultValue)
        else -> defaultValue
    }

    private fun JSONObject.optBooleanAny(
        first: String,
        second: String,
        third: String,
        defaultValue: Boolean,
    ): Boolean = when {
        has(first) -> optBoolean(first, defaultValue)
        has(second) -> optBoolean(second, defaultValue)
        has(third) -> optBoolean(third, defaultValue)
        else -> defaultValue
    }

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (has(name)) optBoolean(name) else null

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name)) optLong(name) else null

    private fun optAetherString(
        arguments: JSONObject,
        primary: String,
        secondary: String,
    ): String = arguments.optString(primary).trim().ifBlank {
        arguments.optString(secondary).trim()
    }

    private companion object {
        val SupportedConfigCategories = listOf(
            "general",
            "web_tools",
            "reliability",
            "agent_skills",
            "mcp_servers",
            "termux",
            "agent_mode",
            "scheduled_tasks",
            "developer",
        )
        val SensitiveKeyFragments = listOf(
            "apikey",
            "api_key",
            "authorization",
            "auth",
            "bearer",
            "token",
            "secret",
            "password",
            "key",
        )
    }
}
