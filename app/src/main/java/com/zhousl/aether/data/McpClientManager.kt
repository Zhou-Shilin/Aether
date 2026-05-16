package com.zhousl.aether.data

import android.util.Log
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxContract
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

private const val DefaultMcpProtocolVersion = "2025-11-25"
private const val McpRequestPollIntervalMillis = 150L
private const val McpDefaultConnectTimeoutMillis = 15_000L
private const val McpDefaultRequestTimeoutMillis = 60_000L
private const val McpProtocolVersionHeader = "MCP-Protocol-Version"
private const val McpSessionIdHeader = "Mcp-Session-Id"
private const val McpLogTag = "AetherMcp"
private const val EnableMcpLogging = false

enum class McpConnectionStatus {
    Disconnected,
    Connecting,
    Ready,
    Error,
}

enum class McpServerTestOperation {
    ListTools,
    ListResources,
    ListPrompts,
}

data class McpToolBinding(
    val serverId: String,
    val serverName: String,
    val toolName: String,
    val description: String,
    val inputSchema: JSONObject,
) {
    val namespacedToolName: String
        get() = "mcp__${serverId}__${toolName}"

    val legacyToolName: String
        get() = "${serverId}:${toolName}"

    fun matchesToolCallName(callName: String): Boolean {
        val normalizedCallName = callName.trim()
        return namespacedToolName.equals(normalizedCallName, ignoreCase = true) ||
            legacyToolName.equals(normalizedCallName, ignoreCase = true) ||
            "${serverName}:${toolName}".equals(normalizedCallName, ignoreCase = true)
    }
}

data class McpResourceItem(
    val serverId: String,
    val serverName: String,
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String,
)

data class McpPromptItem(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val arguments: JSONArray,
)

data class McpLogEvent(
    val serverId: String,
    val level: String,
    val logger: String,
    val data: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class McpTaskState(
    val serverId: String,
    val taskId: String,
    val status: String,
    val title: String = "",
    val detail: String = "",
)

data class McpServerSnapshot(
    val config: McpServerConfig,
    val status: McpConnectionStatus = McpConnectionStatus.Disconnected,
    val protocolVersion: String = "",
    val serverInfo: String = "",
    val tools: List<McpToolBinding> = emptyList(),
    val resources: List<McpResourceItem> = emptyList(),
    val prompts: List<McpPromptItem> = emptyList(),
    val logs: List<McpLogEvent> = emptyList(),
    val tasks: List<McpTaskState> = emptyList(),
    val errorMessage: String = "",
)

interface McpClientCallbacks {
    suspend fun listRoots(workspaceDirectory: String): List<String>

    suspend fun handleSamplingRequest(
        serverId: String,
        request: JSONObject,
    ): JSONObject?

    suspend fun handleElicitationRequest(
        serverId: String,
        request: JSONObject,
    ): JSONObject?
}

class DenyingMcpClientCallbacks : McpClientCallbacks {
    override suspend fun listRoots(workspaceDirectory: String): List<String> =
        listOf(workspaceDirectory)

    override suspend fun handleSamplingRequest(
        serverId: String,
        request: JSONObject,
    ): JSONObject? = null

    override suspend fun handleElicitationRequest(
        serverId: String,
        request: JSONObject,
    ): JSONObject? = null
}

class McpClientManager(
    private val bashTool: TermuxBashTool,
    private val callbacks: McpClientCallbacks = DenyingMcpClientCallbacks(),
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val sessions = ConcurrentHashMap<String, McpServerSession>()

    suspend fun syncServers(
        servers: List<McpServerConfig>,
        workspaceDirectory: String,
    ) = withContext(Dispatchers.IO) {
        val enabledIds = servers
            .filter { it.isEnabled }
            .map { it.id }
            .toSet()
        val obsoleteIds = sessions.keys.filterNot(enabledIds::contains)
        obsoleteIds.forEach { serverId -> disconnect(serverId) }

        servers
            .filter { it.isEnabled }
            .forEach { server ->
                val existing = sessions[server.id]
                if (existing == null || existing.config != server || existing.workspaceDirectory != workspaceDirectory) {
                    disconnect(server.id)
                    val session = McpServerSession(
                        config = server,
                        transport = createTransport(server, workspaceDirectory),
                        workspaceDirectory = workspaceDirectory,
                        callbacks = callbacks,
                        diagnosticLogger = diagnosticLogger,
                    )
                    sessions[server.id] = session
                    session.connectAndRefresh()
                }
            }
    }

    suspend fun disconnect(serverId: String) = withContext(Dispatchers.IO) {
        diagnosticLogger.event(
            category = "mcp",
            event = "disconnect",
            details = mapOf("server_id" to serverId),
        )
        sessions.remove(serverId)?.close()
    }

    suspend fun refreshServer(serverId: String) = withContext(Dispatchers.IO) {
        sessions[serverId]?.refreshCatalog()
    }

    suspend fun testServer(
        server: McpServerConfig,
        workspaceDirectory: String,
        operation: McpServerTestOperation,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val testSession = McpServerSession(
                config = server.copy(isEnabled = true),
                transport = createTransport(server.copy(isEnabled = true), workspaceDirectory),
                workspaceDirectory = workspaceDirectory,
                callbacks = callbacks,
                diagnosticLogger = diagnosticLogger,
            )
            try {
                testSession.connectAndRefresh()
                val snapshot = testSession.snapshot
                if (snapshot.status == McpConnectionStatus.Error) {
                    error(snapshot.errorMessage.ifBlank { "Couldn't connect to MCP server." })
                }
                JSONObject().apply {
                    put("ok", true)
                    put("server_id", snapshot.config.id)
                    put("server_name", snapshot.config.displayName)
                    put("status", snapshot.status.name.lowercase())
                    put("protocol_version", snapshot.protocolVersion)
                    put("server_info", snapshot.serverInfo)
                    when (operation) {
                        McpServerTestOperation.ListTools -> {
                            put("operation", "tools/list")
                            put(
                                "tools",
                                JSONArray().apply {
                                    snapshot.tools.forEach { binding ->
                                        put(
                                            JSONObject()
                                                .put("name", binding.toolName)
                                                .put("description", binding.description)
                                                .put("call_name", binding.namespacedToolName)
                                                .put("input_schema", JSONObject(binding.inputSchema.toString()))
                                        )
                                    }
                                },
                            )
                        }

                        McpServerTestOperation.ListResources -> {
                            put("operation", "resources/list")
                            put(
                                "resources",
                                JSONArray().apply {
                                    snapshot.resources.forEach { resource ->
                                        put(
                                            JSONObject()
                                                .put("uri", resource.uri)
                                                .put("name", resource.name)
                                                .put("description", resource.description)
                                                .put("mime_type", resource.mimeType)
                                        )
                                    }
                                },
                            )
                        }

                        McpServerTestOperation.ListPrompts -> {
                            put("operation", "prompts/list")
                            put(
                                "prompts",
                                JSONArray().apply {
                                    snapshot.prompts.forEach { prompt ->
                                        put(
                                            JSONObject()
                                                .put("name", prompt.name)
                                                .put("description", prompt.description)
                                                .put("arguments", JSONArray(prompt.arguments.toString()))
                                        )
                                    }
                                },
                            )
                        }
                    }
                }.toString()
            } finally {
                testSession.close()
            }
        }
    }

    fun snapshots(): List<McpServerSnapshot> =
        sessions.values.map { it.snapshot }.sortedBy { it.config.displayName.lowercase() }

    fun toolBindings(): List<McpToolBinding> =
        sessions.values
            .flatMap { it.snapshot.tools }
            .sortedWith(compareBy({ it.serverName.lowercase() }, { it.toolName.lowercase() }))

    suspend fun listTools(
        serverId: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val tools = if (serverId.isNullOrBlank()) {
                toolBindings()
            } else {
                val resolvedServerId = resolveServerId(serverId)
                toolBindings().filter { it.serverId == resolvedServerId }
                    .ifEmpty { error("MCP server '$serverId' is not connected.") }
            }
            JSONObject().apply {
                put("ok", true)
                put(
                    "tools",
                    JSONArray().apply {
                        tools.forEach { binding ->
                            put(
                                JSONObject().apply {
                                    put("server_id", binding.serverId)
                                    put("server_name", binding.serverName)
                                    put("tool_name", binding.toolName)
                                    put("description", binding.description)
                                    put("call_name", binding.namespacedToolName)
                                    put("legacy_call_name", binding.legacyToolName)
                                    put("input_schema", JSONObject(binding.inputSchema.toString()))
                                },
                            )
                        }
                    },
                )
                put("stdout", "Listed ${tools.size} MCP tools.")
            }.toString()
        }
    }

    suspend fun callNamespacedTool(
        namespacedToolName: String,
        argumentsJson: String,
    ): Result<String> = callToolByName(namespacedToolName, argumentsJson)

    suspend fun callToolByName(
        toolCallName: String,
        argumentsJson: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val binding = resolveToolBinding(toolCallName)
                ?: error("Unknown MCP tool '$toolCallName'.")
            diagnosticLogger.event(
                category = "mcp",
                event = "tool_call_start",
                details = mapOf(
                    "server_id" to binding.serverId,
                    "server_name" to binding.serverName,
                    "tool_name" to binding.toolName,
                    "arguments_chars" to argumentsJson.length,
                ),
            )
            val arguments = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
            val result = sessions[binding.serverId]
                ?.callTool(binding.toolName, arguments)
                ?: error("MCP server '${binding.serverId}' is not connected.")
            diagnosticLogger.event(
                category = "mcp",
                event = "tool_call_end",
                details = mapOf(
                    "server_id" to binding.serverId,
                    "tool_name" to binding.toolName,
                ),
            )
            result.toString()
        }
    }

    suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: JSONObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedServerId = resolveServerId(serverId)
            diagnosticLogger.event(
                category = "mcp",
                event = "tool_call_start",
                details = mapOf(
                    "server_id" to resolvedServerId,
                    "tool_name" to toolName,
                    "arguments_chars" to arguments.toString().length,
                ),
            )
            val result = sessions[resolvedServerId]
                ?.callTool(toolName, arguments)
                ?: error("MCP server '$serverId' is not connected.")
            diagnosticLogger.event(
                category = "mcp",
                event = "tool_call_end",
                details = mapOf(
                    "server_id" to resolvedServerId,
                    "tool_name" to toolName,
                ),
            )
            result.toString()
        }
    }

    suspend fun listResources(
        serverId: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resources = if (serverId.isNullOrBlank()) {
                sessions.values.flatMap { it.snapshot.resources }
            } else {
                sessions[serverId]?.snapshot?.resources
                    ?: error("MCP server '$serverId' is not connected.")
            }
            JSONObject().apply {
                put("ok", true)
                put(
                    "resources",
                    JSONArray().apply {
                        resources.forEach { resource ->
                            put(
                                JSONObject().apply {
                                    put("server_id", resource.serverId)
                                    put("server_name", resource.serverName)
                                    put("uri", resource.uri)
                                    put("name", resource.name)
                                    put("description", resource.description)
                                    put("mime_type", resource.mimeType)
                                },
                            )
                        }
                    },
                )
                put("stdout", "Listed ${resources.size} MCP resources.")
            }.toString()
        }
    }

    suspend fun readResource(
        serverId: String,
        uri: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = sessions[serverId]?.readResource(uri)
                ?: error("MCP server '$serverId' is not connected.")
            result.toString()
        }
    }

    suspend fun listPrompts(
        serverId: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompts = if (serverId.isNullOrBlank()) {
                sessions.values.flatMap { it.snapshot.prompts }
            } else {
                sessions[serverId]?.snapshot?.prompts
                    ?: error("MCP server '$serverId' is not connected.")
            }
            JSONObject().apply {
                put("ok", true)
                put(
                    "prompts",
                    JSONArray().apply {
                        prompts.forEach { prompt ->
                            put(
                                JSONObject().apply {
                                    put("server_id", prompt.serverId)
                                    put("server_name", prompt.serverName)
                                    put("name", prompt.name)
                                    put("description", prompt.description)
                                    put("arguments", JSONArray(prompt.arguments.toString()))
                                },
                            )
                        }
                    },
                )
                put("stdout", "Listed ${prompts.size} MCP prompts.")
            }.toString()
        }
    }

    suspend fun getPrompt(
        serverId: String,
        promptName: String,
        arguments: JSONObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = sessions[serverId]?.getPrompt(promptName, arguments)
                ?: error("MCP server '$serverId' is not connected.")
            result.toString()
        }
    }

    private fun resolveToolBinding(toolCallName: String): McpToolBinding? =
        toolBindings().firstOrNull { it.matchesToolCallName(toolCallName) }

    private fun resolveServerId(serverId: String): String {
        val normalizedServerId = serverId.trim()
        if (normalizedServerId.isBlank()) {
            error("MCP server id is required.")
        }
        sessions[normalizedServerId]?.let { return it.config.id }
        return sessions.values.firstOrNull {
            it.config.displayName.equals(normalizedServerId, ignoreCase = true)
        }?.config?.id ?: normalizedServerId
    }

    private fun createTransport(
        server: McpServerConfig,
        workspaceDirectory: String,
    ): McpSessionTransport = when (val transportConfig = server.transport) {
        is McpTransportConfig.StdIo -> StdIoMcpTransport(
            serverId = server.id,
            config = transportConfig,
            workspaceDirectory = workspaceDirectory,
            bashTool = bashTool,
        )

        is McpTransportConfig.StreamableHttp -> StreamableHttpMcpTransport(
            config = transportConfig,
            protocolVersion = DefaultMcpProtocolVersion,
            httpClient = httpClient,
            connectTimeoutMillis = server.connectTimeoutMillis,
            requestTimeoutMillis = server.requestTimeoutMillis,
        )
    }
}

private class McpServerSession(
    val config: McpServerConfig,
    private val transport: McpSessionTransport,
    val workspaceDirectory: String,
    private val callbacks: McpClientCallbacks,
    private val diagnosticLogger: AetherDiagnosticLogger,
) {
    private var requestCounter = 1L
    private var initialized = false
    private var serverCapabilities = JSONObject()
    var snapshot: McpServerSnapshot = McpServerSnapshot(config = config)
        private set

    suspend fun connectAndRefresh() {
        snapshot = snapshot.copy(status = McpConnectionStatus.Connecting, errorMessage = "")
        diagnosticLogger.event(
            category = "mcp",
            event = "connect_start",
            details = mapOf(
                "server_id" to config.id,
                "server_name" to config.displayName,
                "transport" to config.transport.javaClass.simpleName,
            ),
        )
        runCatching {
            transport.open()
            val initializeResult = call(
                method = "initialize",
                params = JSONObject().apply {
                    put("protocolVersion", DefaultMcpProtocolVersion)
                    put(
                        "capabilities",
                        JSONObject().apply {
                            put("roots", JSONObject().apply { put("listChanged", true) })
                            put("sampling", JSONObject().apply { put("supported", true) })
                            put("elicitation", JSONObject().apply { put("supported", true) })
                        },
                    )
                    put(
                        "clientInfo",
                        JSONObject().apply {
                            put("name", "Aether Android")
                            put("version", "0.1.0")
                        },
                    )
                },
            )
            initialized = true
            sendNotification("notifications/initialized")
            val protocolVersion = initializeResult.optString("protocolVersion")
            serverCapabilities = initializeResult.optJSONObject("capabilities") ?: JSONObject()
            val serverInfo = initializeResult.optJSONObject("serverInfo")
                ?.let { "${it.optString("name")} ${it.optString("version")}".trim() }
                .orEmpty()
            snapshot = snapshot.copy(
                status = McpConnectionStatus.Ready,
                protocolVersion = protocolVersion,
                serverInfo = serverInfo,
                errorMessage = "",
            )
            diagnosticLogger.event(
                category = "mcp",
                event = "connect_ready",
                details = mapOf(
                    "server_id" to config.id,
                    "server_name" to config.displayName,
                    "protocol_version" to protocolVersion,
                    "server_info" to serverInfo,
                ),
            )
            refreshCatalog()
        }.onFailure { throwable ->
            snapshot = snapshot.copy(
                status = McpConnectionStatus.Error,
                errorMessage = throwable.message ?: "Couldn't connect to MCP server.",
            )
            diagnosticLogger.exception(
                category = "mcp",
                event = "connect_failed",
                throwable = throwable,
                details = mapOf(
                    "server_id" to config.id,
                    "server_name" to config.displayName,
                ),
            )
        }
    }

    suspend fun refreshCatalog() {
        if (!initialized) return
        runCatching {
            val toolsResult = callCatalogMethod(
                method = "tools/list",
                capabilityName = "tools",
                defaultEnabled = true,
            )
            val resourcesResult = callCatalogMethod(
                method = "resources/list",
                capabilityName = "resources",
                defaultEnabled = false,
            )
            val promptsResult = callCatalogMethod(
                method = "prompts/list",
                capabilityName = "prompts",
                defaultEnabled = false,
            )
            snapshot = snapshot.copy(
                status = McpConnectionStatus.Ready,
                tools = parseTools(config.id, config.displayName, toolsResult),
                resources = parseResources(config.id, config.displayName, resourcesResult),
                prompts = parsePrompts(config.id, config.displayName, promptsResult),
            )
            diagnosticLogger.event(
                category = "mcp",
                event = "catalog_refreshed",
                details = mapOf(
                    "server_id" to config.id,
                    "tool_count" to snapshot.tools.size,
                    "resource_count" to snapshot.resources.size,
                    "prompt_count" to snapshot.prompts.size,
                ),
            )
        }.onFailure { throwable ->
            snapshot = snapshot.copy(
                status = McpConnectionStatus.Error,
                errorMessage = throwable.message ?: "Couldn't refresh MCP server catalog.",
            )
            diagnosticLogger.exception(
                category = "mcp",
                event = "catalog_refresh_failed",
                throwable = throwable,
                details = mapOf("server_id" to config.id),
            )
        }
    }

    private suspend fun callCatalogMethod(
        method: String,
        capabilityName: String,
        defaultEnabled: Boolean,
    ): JSONObject {
        if (!isServerCapabilityEnabled(capabilityName, defaultEnabled)) {
            return JSONObject()
        }
        return try {
            call(method)
        } catch (exception: McpResponseException) {
            if (exception.isMethodNotFound()) {
                JSONObject()
            } else {
                throw exception
            }
        }
    }

    private fun isServerCapabilityEnabled(
        capabilityName: String,
        defaultEnabled: Boolean,
    ): Boolean {
        if (!serverCapabilities.has(capabilityName)) return defaultEnabled
        return !serverCapabilities.isNull(capabilityName)
    }

    suspend fun callTool(
        toolName: String,
        arguments: JSONObject,
    ): JSONObject {
        val result = call(
            method = "tools/call",
            params = JSONObject().apply {
                put("name", toolName)
                put("arguments", arguments)
            },
        )
        return JSONObject().apply {
            put("ok", true)
            put("server_id", config.id)
            put("server_name", config.displayName)
            put("tool_name", toolName)
            put("result", result)
            put("stdout", "Called MCP tool ${config.displayName}/$toolName.")
        }
    }

    suspend fun readResource(uri: String): JSONObject {
        val result = call(
            method = "resources/read",
            params = JSONObject().apply { put("uri", uri) },
        )
        return JSONObject().apply {
            put("ok", true)
            put("server_id", config.id)
            put("uri", uri)
            put("result", result)
            put("stdout", "Read MCP resource $uri.")
        }
    }

    suspend fun getPrompt(
        promptName: String,
        arguments: JSONObject,
    ): JSONObject {
        val result = call(
            method = "prompts/get",
            params = JSONObject().apply {
                put("name", promptName)
                put("arguments", arguments)
            },
        )
        return JSONObject().apply {
            put("ok", true)
            put("server_id", config.id)
            put("name", promptName)
            put("result", result)
            put("stdout", "Fetched MCP prompt ${config.displayName}/$promptName.")
        }
    }

    suspend fun close() {
        runCatching { transport.close() }
        snapshot = snapshot.copy(status = McpConnectionStatus.Disconnected)
    }

    private suspend fun sendNotification(method: String) {
        transport.sendMessage(
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", method)
            },
        )
    }

    private suspend fun call(
        method: String,
        params: JSONObject? = null,
    ): JSONObject {
        val requestId = nextRequestId()
        val startedAt = System.currentTimeMillis()
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }
        logMcp("[${
            config.id
        }] -> method=$method id=$requestId")
        diagnosticLogger.event(
            category = "mcp",
            event = "request_start",
            requestId = requestId,
            details = mapOf(
                "server_id" to config.id,
                "method" to method,
                "timeout_millis" to effectiveTimeoutMillis(
                    configuredMillis = config.requestTimeoutMillis,
                    defaultMillis = McpDefaultRequestTimeoutMillis,
                ),
            ),
        )

        var messages = runCatching { transport.sendMessage(request) }
            .getOrElse { throwable ->
                diagnosticLogger.exception(
                    category = "mcp",
                    event = "request_failed",
                    requestId = requestId,
                    throwable = throwable,
                    details = mapOf(
                        "server_id" to config.id,
                        "method" to method,
                        "duration_millis" to System.currentTimeMillis() - startedAt,
                    ),
                )
                throw throwable
            }
        val deadline = System.currentTimeMillis() + effectiveTimeoutMillis(
            configuredMillis = config.requestTimeoutMillis,
            defaultMillis = McpDefaultRequestTimeoutMillis,
        )

        while (System.currentTimeMillis() < deadline) {
            val result = consumeMessages(requestId, messages)
            if (result != null) {
                logMcp(
                    "[${config.id}] <- method=$method id=$requestId " +
                        "duration_ms=${System.currentTimeMillis() - startedAt}",
                )
                diagnosticLogger.event(
                    category = "mcp",
                    event = "request_end",
                    requestId = requestId,
                    details = mapOf(
                        "server_id" to config.id,
                        "method" to method,
                        "duration_millis" to System.currentTimeMillis() - startedAt,
                    ),
                )
                return result
            }
            delay(McpRequestPollIntervalMillis)
            messages = transport.pollMessages()
        }

        logMcp(
            "[${config.id}] timeout method=$method id=$requestId " +
                "duration_ms=${System.currentTimeMillis() - startedAt}",
        )
        diagnosticLogger.event(
            category = "mcp",
            event = "request_timeout",
            level = "warn",
            requestId = requestId,
            details = mapOf(
                "server_id" to config.id,
                "method" to method,
                "duration_millis" to System.currentTimeMillis() - startedAt,
            ),
        )
        error("Timed out waiting for MCP response to '$method'.")
    }

    private suspend fun consumeMessages(
        requestId: String,
        messages: List<JSONObject>,
    ): JSONObject? {
        for (message in messages) {
            if (message.optString("jsonrpc") != "2.0") continue
            if (message.has("id") && message.opt("id")?.toString() == requestId) {
                if (message.has("error")) {
                    val errorObject = message.optJSONObject("error")
                    val errorMessage = errorObject
                        ?.optString("message")
                        .orEmpty()
                        .ifBlank { "MCP request failed." }
                    throw McpResponseException(
                        code = errorObject?.optInt("code") ?: 0,
                        responseMessage = errorMessage,
                    )
                }
                return message.optJSONObject("result") ?: JSONObject()
            }
            if (message.has("method")) {
                val method = message.optString("method")
                val params = message.optJSONObject("params") ?: JSONObject()
                when {
                    method == "roots/list" -> respondToServerRequest(
                        id = message.opt("id"),
                        result = JSONObject().apply {
                            put(
                                "roots",
                                JSONArray().apply {
                                    callbacks.listRoots(workspaceDirectory).forEach { root ->
                                        put(
                                            JSONObject().apply {
                                                put("uri", "file://$root")
                                                put("name", root.substringAfterLast('/').ifBlank { "workspace" })
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )

                    method == "sampling/createMessage" -> respondToServerRequest(
                        id = message.opt("id"),
                        result = callbacks.handleSamplingRequest(config.id, params),
                    )

                    method == "elicitation/create" -> respondToServerRequest(
                        id = message.opt("id"),
                        result = callbacks.handleElicitationRequest(config.id, params),
                    )

                    method.startsWith("notifications/") -> processNotification(method, params)
                    method.startsWith("tasks/") -> processTaskMessage(method, params)
                }
            }
        }
        return null
    }

    private suspend fun respondToServerRequest(
        id: Any?,
        result: JSONObject?,
    ) {
        if (id == null) return
        val response = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            if (result != null) {
                put("result", result)
            } else {
                put(
                    "error",
                    JSONObject().apply {
                        put("code", -32000)
                        put("message", "Request denied by client.")
                    },
                )
            }
        }
        transport.sendMessage(response)
    }

    private fun processNotification(
        method: String,
        params: JSONObject,
    ) {
        when (method) {
            "notifications/message",
            "notifications/logging/message" -> {
                val level = params.optString("level").ifBlank { "info" }
                val logger = params.optString("logger")
                val data = params.opt("data")?.toString().orEmpty()
                snapshot = snapshot.copy(
                    logs = (snapshot.logs + McpLogEvent(config.id, level, logger, data)).takeLast(100),
                )
            }
        }
    }

    private fun processTaskMessage(
        method: String,
        params: JSONObject,
    ) {
        val taskId = params.optString("taskId")
            .ifBlank { params.optString("id") }
            .ifBlank { return }
        val tasks = snapshot.tasks.associateBy { it.taskId }.toMutableMap()
        tasks[taskId] = McpTaskState(
            serverId = config.id,
            taskId = taskId,
            status = method.substringAfterLast('/'),
            title = params.optString("title"),
            detail = params.toString(),
        )
        snapshot = snapshot.copy(tasks = tasks.values.sortedBy { it.taskId })
    }

    private fun nextRequestId(): String = "${config.id}-${requestCounter++}"
}

private interface McpSessionTransport {
    suspend fun open()

    suspend fun sendMessage(message: JSONObject): List<JSONObject>

    suspend fun pollMessages(): List<JSONObject>

    suspend fun close()
}

private class StreamableHttpMcpTransport(
    private val config: McpTransportConfig.StreamableHttp,
    private val protocolVersion: String,
    private val httpClient: OkHttpClient,
    connectTimeoutMillis: Long,
    requestTimeoutMillis: Long,
) : McpSessionTransport {
    private val queuedMessages = ConcurrentLinkedQueue<JSONObject>()
    private val requestHttpClient = httpClient.newBuilder()
        .connectTimeout(
            effectiveTimeoutMillis(connectTimeoutMillis, McpDefaultConnectTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .readTimeout(
            effectiveTimeoutMillis(requestTimeoutMillis, McpDefaultRequestTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .writeTimeout(
            effectiveTimeoutMillis(requestTimeoutMillis, McpDefaultRequestTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .callTimeout(
            effectiveTimeoutMillis(requestTimeoutMillis, McpDefaultRequestTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .build()
    private val streamHttpClient = httpClient.newBuilder()
        .connectTimeout(
            effectiveTimeoutMillis(connectTimeoutMillis, McpDefaultConnectTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(
            effectiveTimeoutMillis(requestTimeoutMillis, McpDefaultRequestTimeoutMillis),
            TimeUnit.MILLISECONDS,
        )
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var sessionId: String = ""

    @Volatile
    private var closed: Boolean = true

    @Volatile
    private var receiveCall: Call? = null

    @Volatile
    private var receiveStreamUnsupported: Boolean = false

    override suspend fun open() {
        closed = false
    }

    override suspend fun sendMessage(message: JSONObject): List<JSONObject> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(config.url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .addHeader(McpProtocolVersionHeader, protocolVersion)
            .applySessionHeader()
            .apply {
                config.headers.forEach { addHeader(it.key, it.value) }
            }
            .post(message.toString().toRequestBody("application/json".toMediaType()))
            .build()
        requestHttpClient.newCall(request).execute().use { response ->
            updateSessionId(response)
            if (response.code == 202 || response.code == 204) {
                ensureReceiveStreamStarted()
                return@withContext emptyList()
            }
            if (!response.isSuccessful) {
                error("MCP server returned HTTP ${response.code}.")
            }
            val body = response.body ?: return@withContext emptyList()
            val contentType = response.header("Content-Type").orEmpty()
            val messages = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                parseSseMessages(body.source())
            } else {
                parseJsonMessages(body.string())
            }
            ensureReceiveStreamStarted()
            return@withContext messages
        }
    }

    override suspend fun pollMessages(): List<JSONObject> {
        ensureReceiveStreamStarted()
        return drainQueuedMessages()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        closed = true
        receiveCall?.cancel()
        receiveCall = null
        receiveStreamUnsupported = false
        queuedMessages.clear()
        val currentSessionId = sessionId
        sessionId = ""
        if (currentSessionId.isBlank()) return@withContext
        runCatching {
            val request = Request.Builder()
                .url(config.url)
                .addHeader(McpProtocolVersionHeader, protocolVersion)
                .addHeader(McpSessionIdHeader, currentSessionId)
                .apply {
                    config.headers.forEach { addHeader(it.key, it.value) }
                }
                .delete()
                .build()
            requestHttpClient.newCall(request).execute().close()
        }
        Unit
    }

    private fun Request.Builder.applySessionHeader(): Request.Builder = apply {
        val currentSessionId = sessionId
        if (currentSessionId.isNotBlank()) {
            addHeader(McpSessionIdHeader, currentSessionId)
        }
    }

    private fun updateSessionId(response: Response) {
        val nextSessionId = response.header(McpSessionIdHeader).orEmpty()
        if (nextSessionId.isNotBlank() && nextSessionId != sessionId) {
            sessionId = nextSessionId
            receiveStreamUnsupported = false
            receiveCall?.cancel()
            receiveCall = null
        }
    }

    @Synchronized
    private fun ensureReceiveStreamStarted() {
        if (closed || receiveStreamUnsupported || sessionId.isBlank() || receiveCall != null) return
        val request = Request.Builder()
            .url(config.url)
            .addHeader("Accept", "text/event-stream")
            .addHeader(McpProtocolVersionHeader, protocolVersion)
            .addHeader(McpSessionIdHeader, sessionId)
            .apply {
                config.headers.forEach { addHeader(it.key, it.value) }
            }
            .get()
            .build()
        val call = streamHttpClient.newCall(request)
        receiveCall = call
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (receiveCall == call) {
                        receiveCall = null
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    try {
                        response.use {
                            if (response.code == 405 || response.code == 404) {
                                receiveStreamUnsupported = true
                                return
                            }
                            if (!response.isSuccessful) return
                            val body = response.body ?: return
                            val contentType = response.header("Content-Type").orEmpty()
                            if (!contentType.contains("text/event-stream", ignoreCase = true)) return
                            readSseMessages(body.source()) { queuedMessages.add(it) }
                        }
                    } finally {
                        if (receiveCall == call) {
                            receiveCall = null
                        }
                    }
                }
            },
        )
    }

    private fun drainQueuedMessages(): List<JSONObject> {
        val messages = mutableListOf<JSONObject>()
        while (true) {
            val message = queuedMessages.poll() ?: break
            messages += message
        }
        return messages
    }
}

private class StdIoMcpTransport(
    private val serverId: String,
    private val config: McpTransportConfig.StdIo,
    private val workspaceDirectory: String,
    private val bashTool: TermuxBashTool,
) : McpSessionTransport {
    private var runId: String = ""
    private var logOffset: Long = 0L
    private var lastHealthLog: String = ""

    override suspend fun open() {
        val launchResult = JSONObject(
            bashTool.execute(
                JSONObject().apply {
                    put("command", buildBrokerScript())
                    put("working_directory", TermuxContract.HomeDirectory)
                }.toString(),
            ),
        )
        if (!launchResult.optBoolean("ok") && launchResult.optString("status") != "running") {
            error(launchResult.optString("errmsg").ifBlank { "Couldn't launch the MCP stdio broker." })
        }
        runId = launchResult.optString("run_id")
    }

    override suspend fun sendMessage(message: JSONObject): List<JSONObject> {
        logMcp("[$serverId] send ${describeMcpMessage(message)}")
        val payloadBase64 = encodeBase64(message.toString())
        val command = buildString {
            appendLine("set -euo pipefail")
            appendLine("root='${brokerRootPath()}'")
            appendLine("mkdir -p \"\$root/inbox\"")
            appendLine("payload=\"\$(printf '%s' '$payloadBase64' | base64 -d)\"")
            appendLine("request_path=\"\$root/inbox/${System.currentTimeMillis()}-${UUID.randomUUID()}.json\"")
            appendLine("temp_request_path=\"\$root/.request-${UUID.randomUUID()}.tmp\"")
            appendLine("printf '%s' \"\$payload\" > \"\$temp_request_path\"")
            appendLine("mv \"\$temp_request_path\" \"\$request_path\"")
        }
        val rawResult = JSONObject(bashTool.executeCommand(command))
        if (!rawResult.optBoolean("ok")) {
            error(rawResult.optString("errmsg").ifBlank { "Couldn't write to the MCP broker inbox." })
        }
        return emptyList()
    }

    override suspend fun pollMessages(): List<JSONObject> {
        val command = buildString {
            appendLine("set -euo pipefail")
            appendLine("root='${brokerRootPath()}'")
            appendLine("events_path=\"\$root/events.jsonl\"")
            appendLine("stderr_path=\"\$root/stderr.log\"")
            appendLine("server_pid_path=\"\$root/server.pid\"")
            appendLine("current_offset=$logOffset")
            appendLine("server_pid=''")
            appendLine("if [ -f \"\$server_pid_path\" ]; then")
            appendLine("  server_pid=\"\$(tr -d '[:space:]' < \"\$server_pid_path\")\"")
            appendLine("fi")
            appendLine("server_alive=false")
            appendLine("if [ -n \"\$server_pid\" ] && kill -0 \"\$server_pid\" 2>/dev/null; then")
            appendLine("  server_alive=true")
            appendLine("fi")
            appendLine("inbox_count=\$(find \"\$root/inbox\" -maxdepth 1 -type f -name '*.json' 2>/dev/null | wc -l | tr -d '[:space:]')")
            appendLine("processed_count=\$(find \"\$root/processed\" -maxdepth 1 -type f -name '*.json' 2>/dev/null | wc -l | tr -d '[:space:]')")
            appendLine("stderr_b64=''")
            appendLine("if [ -f \"\$stderr_path\" ]; then")
            appendLine("  stderr_b64=\"\$(tail -c 4096 -- \"\$stderr_path\" 2>/dev/null | base64 | tr -d '\\n')\"")
            appendLine("fi")
            appendLine("if [ ! -f \"\$events_path\" ]; then")
            appendLine("  printf 'offset=%s\\n' \"\$current_offset\"")
            appendLine("  printf 'server_pid=%s\\n' \"\$server_pid\"")
            appendLine("  printf 'server_alive=%s\\n' \"\$server_alive\"")
            appendLine("  printf 'inbox_count=%s\\n' \"\$inbox_count\"")
            appendLine("  printf 'processed_count=%s\\n' \"\$processed_count\"")
            appendLine("  printf 'stderr_b64=%s\\n' \"\$stderr_b64\"")
            appendLine("  printf 'events_b64=\\n'")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("size=\$(wc -c < \"\$events_path\" | tr -d '[:space:]')")
            appendLine("if [ \"\$size\" -le \"\$current_offset\" ]; then")
            appendLine("  printf 'offset=%s\\n' \"\$size\"")
            appendLine("  printf 'server_pid=%s\\n' \"\$server_pid\"")
            appendLine("  printf 'server_alive=%s\\n' \"\$server_alive\"")
            appendLine("  printf 'inbox_count=%s\\n' \"\$inbox_count\"")
            appendLine("  printf 'processed_count=%s\\n' \"\$processed_count\"")
            appendLine("  printf 'stderr_b64=%s\\n' \"\$stderr_b64\"")
            appendLine("  printf 'events_b64=\\n'")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("byte_count=\$((size - current_offset))")
            appendLine("chunk=\"\$(dd if=\"\$events_path\" bs=1 skip=\"\$current_offset\" count=\"\$byte_count\" status=none 2>/dev/null; printf '\\037')\"")
            appendLine("chunk=\"\${chunk%\$'\\037'}\"")
            appendLine("complete_chunk=''")
            appendLine("case \"\$chunk\" in")
            appendLine("  *$'\\n')")
            appendLine("    complete_chunk=\"\$chunk\"")
            appendLine("    ;;")
            appendLine("  *$'\\n'*)")
            appendLine("    complete_chunk=\"\${chunk%$'\\n'*}\"\$'\\n'")
            appendLine("    ;;")
            appendLine("esac")
            appendLine("complete_bytes=\$(printf '%s' \"\$complete_chunk\" | wc -c | tr -d '[:space:]')")
            appendLine("next_offset=\$((current_offset + complete_bytes))")
            appendLine("printf 'offset=%s\\n' \"\$next_offset\"")
            appendLine("printf 'server_pid=%s\\n' \"\$server_pid\"")
            appendLine("printf 'server_alive=%s\\n' \"\$server_alive\"")
            appendLine("printf 'inbox_count=%s\\n' \"\$inbox_count\"")
            appendLine("printf 'processed_count=%s\\n' \"\$processed_count\"")
            appendLine("printf 'stderr_b64=%s\\n' \"\$stderr_b64\"")
            appendLine("printf 'events_b64=%s\\n' \"\$(printf '%s' \"\$complete_chunk\" | base64 | tr -d '\\n')\"")
        }
        val rawResult = JSONObject(bashTool.executeCommand(command))
        if (!rawResult.optBoolean("ok")) {
            error(rawResult.optString("errmsg").ifBlank { "Couldn't read from the MCP broker event log." })
        }
        val values = parseKeyValueOutput(rawResult.optString("stdout"))
        logOffset = values["offset"]?.toLongOrNull() ?: logOffset
        logHealth(values)
        val events = decodeBase64(values["events_b64"].orEmpty())
        if (events.isBlank()) return emptyList()
        return events.lineSequence()
            .mapNotNull { line ->
                val payloadBase64 = line.substringAfter('|', "")
                if (payloadBase64.isBlank()) {
                    null
                } else {
                    runCatching { JSONObject(decodeBase64(payloadBase64)) }.getOrNull()
                }
            }
            .toList()
            .also { messages ->
                if (messages.isNotEmpty()) {
                    logMcp(
                        "[$serverId] poll offset=$logOffset messages=" +
                            messages.joinToString(separator = "; ") { describeMcpMessage(it) },
                    )
                }
            }
    }

    override suspend fun close() {
        if (runId.isBlank()) return
        runCatching {
            bashTool.killExecution(
                JSONObject().apply {
                    put("run_id", runId)
                }.toString(),
            )
        }
    }

    private fun buildBrokerScript(): String = buildString {
        appendLine("set -euo pipefail")
        appendLine("root='${brokerRootPath()}'")
        appendLine("mkdir -p \"\$root\"")
        appendLine("rm -rf \"\$root/inbox\" \"\$root/processed\"")
        appendLine("rm -f \"\$root\"/.request-*.tmp \"\$root/server.pid\"")
        appendLine("mkdir -p \"\$root/inbox\" \"\$root/processed\"")
        appendLine("events_path=\"\$root/events.jsonl\"")
        appendLine("stderr_path=\"\$root/stderr.log\"")
        appendLine("rm -f \"\$root/server.stdin\" \"\$root/server.stdout\"")
        appendLine(": > \"\$events_path\"")
        appendLine(": > \"\$stderr_path\"")
        config.environment.forEach { keyValue ->
            appendLine("export ${escapeShellName(keyValue.key)}='${escapeForSingleQuoted(keyValue.value)}'")
        }
        appendLine("command='${escapeForSingleQuoted(config.command)}'")
        appendLine("arguments=(")
        config.arguments.forEach { argument ->
            appendLine("  '${escapeForSingleQuoted(argument)}'")
        }
        appendLine(")")
        appendLine("command_line=\"\$command\"")
        appendLine("for argument in \"\${arguments[@]}\"; do")
        appendLine("  printf -v quoted_argument '%q' \"\$argument\"")
        appendLine("  command_line=\"\$command_line \$quoted_argument\"")
        appendLine("done")
        appendLine("working_directory='${escapeForSingleQuoted(config.workingDirectory.ifBlank { workspaceDirectory })}'")
        appendLine("coproc SERVER_PROCESS {")
        appendLine("  cd \"\$working_directory\"")
        appendLine("  bash -lc \"\$command_line\" 2>> \"\$stderr_path\"")
        appendLine("}")
        appendLine("server_pid=\$SERVER_PROCESS_PID")
        appendLine("exec {server_stdin_fd}>&\"\${SERVER_PROCESS[1]}\"")
        appendLine("exec {server_stdout_fd}<&\"\${SERVER_PROCESS[0]}\"")
        appendLine("stdin_fd=\"\$server_stdin_fd\"")
        appendLine("stdout_fd=\"\$server_stdout_fd\"")
        appendLine("printf '%s' \"\$server_pid\" > \"\$root/server.pid\"")
        appendLine("(")
        appendLine("  while true; do")
        appendLine("    found=false")
        appendLine("    for request_path in \"\$root\"/inbox/*.json; do")
        appendLine("      [ -f \"\$request_path\" ] || continue")
        appendLine("      found=true")
        appendLine("      payload=\"\$(cat \"\$request_path\")\"")
        appendLine("      payload_bytes=\$(printf '%s' \"\$payload\" | wc -c | tr -d '[:space:]')")
        appendLine("      printf 'Content-Length: %s\\r\\n\\r\\n%s' \"\$payload_bytes\" \"\$payload\" >&\"\$stdin_fd\"")
        appendLine("      mv \"\$request_path\" \"\$root/processed/\"")
        appendLine("    done")
        appendLine("    [ \"\$found\" = true ] || sleep 0.1")
        appendLine("  done")
        appendLine(") &")
        appendLine("writer_pid=\$!")
        appendLine("sequence=0")
        appendLine("while true; do")
        appendLine("  content_length=''")
        appendLine("  while IFS= read -r line <&\"\$stdout_fd\"; do")
        appendLine("    line=\"\${line%$'\\r'}\"")
        appendLine("    [ -z \"\$line\" ] && break")
        appendLine("    case \"\$line\" in")
        appendLine("      Content-Length:*) content_length=\"\${line#Content-Length: }\" ;;")
        appendLine("    esac")
        appendLine("  done || break")
        appendLine("  [ -n \"\$content_length\" ] || continue")
        appendLine("  payload=\"\$(dd bs=1 count=\"\$content_length\" iflag=fullblock 2>/dev/null <&\"\$stdout_fd\"; printf '\\037')\"")
        appendLine("  payload=\"\${payload%\$'\\037'}\"")
        appendLine("  payload_b64=\"\$(printf '%s' \"\$payload\" | base64 | tr -d '\\n')\"")
        appendLine("  sequence=\$((sequence + 1))")
        appendLine("  printf '%s|%s\\n' \"\$sequence\" \"\$payload_b64\" >> \"\$events_path\"")
        appendLine("done")
        appendLine("kill \"\$writer_pid\" 2>/dev/null || true")
        appendLine("kill \"\$server_pid\" 2>/dev/null || true")
    }

    private fun brokerRootPath(): String =
        "${TermuxContract.HomeDirectory}/.aether/mcp-brokers/$serverId"

    private fun logHealth(values: Map<String, String>) {
        val serverPid = values["server_pid"].orEmpty().ifBlank { "?" }
        val serverAlive = values["server_alive"].orEmpty().ifBlank { "unknown" }
        val inboxCount = values["inbox_count"].orEmpty().ifBlank { "?" }
        val processedCount = values["processed_count"].orEmpty().ifBlank { "?" }
        val stderrTail = decodeBase64(values["stderr_b64"].orEmpty())
            .trim()
            .lineSequence()
            .toList()
            .takeLast(3)
            .joinToString(separator = " | ")
            .ifBlank { "<empty>" }
        val summary = "[$serverId] broker pid=$serverPid alive=$serverAlive inbox=$inboxCount processed=$processedCount stderr=$stderrTail"
        if (summary != lastHealthLog) {
            lastHealthLog = summary
            logMcp(summary)
        }
    }

    private fun escapeForSingleQuoted(value: String): String =
        value.replace("'", "'\"'\"'")

    private fun escapeShellName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_]"), "_").ifBlank { "MCP_VALUE" }
}

private fun parseTools(
    serverId: String,
    serverName: String,
    response: JSONObject,
): List<McpToolBinding> {
    val tools = response.optJSONArray("tools") ?: JSONArray()
    return buildList {
        for (index in 0 until tools.length()) {
            val tool = tools.optJSONObject(index) ?: continue
            add(
                McpToolBinding(
                    serverId = serverId,
                    serverName = serverName,
                    toolName = tool.optString("name"),
                    description = tool.optString("description"),
                    inputSchema = tool.optJSONObject("inputSchema")
                        ?: tool.optJSONObject("input_schema")
                        ?: JSONObject().apply { put("type", "object") },
                ),
            )
        }
    }
}

private fun parseResources(
    serverId: String,
    serverName: String,
    response: JSONObject,
): List<McpResourceItem> {
    val resources = response.optJSONArray("resources") ?: JSONArray()
    return buildList {
        for (index in 0 until resources.length()) {
            val resource = resources.optJSONObject(index) ?: continue
            add(
                McpResourceItem(
                    serverId = serverId,
                    serverName = serverName,
                    uri = resource.optString("uri"),
                    name = resource.optString("name"),
                    description = resource.optString("description"),
                    mimeType = resource.optString("mimeType")
                        .ifBlank { resource.optString("mime_type") },
                ),
            )
        }
    }
}

private fun parsePrompts(
    serverId: String,
    serverName: String,
    response: JSONObject,
): List<McpPromptItem> {
    val prompts = response.optJSONArray("prompts") ?: JSONArray()
    return buildList {
        for (index in 0 until prompts.length()) {
            val prompt = prompts.optJSONObject(index) ?: continue
            add(
                McpPromptItem(
                    serverId = serverId,
                    serverName = serverName,
                    name = prompt.optString("name"),
                    description = prompt.optString("description"),
                    arguments = prompt.optJSONArray("arguments") ?: JSONArray(),
                ),
            )
        }
    }
}

private class McpResponseException(
    val code: Int,
    responseMessage: String,
) : RuntimeException(responseMessage) {
    fun isMethodNotFound(): Boolean =
        code == -32601 || message?.contains("method not found", ignoreCase = true) == true
}

private fun effectiveTimeoutMillis(
    configuredMillis: Long,
    defaultMillis: Long,
): Long = configuredMillis.takeIf { it > 0L } ?: defaultMillis

private fun parseJsonMessages(body: String): List<JSONObject> {
    val trimmedBody = body.trim()
    if (trimmedBody.isBlank()) return emptyList()
    if (trimmedBody.startsWith("[")) {
        val array = JSONArray(trimmedBody)
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }
    return listOf(JSONObject(trimmedBody))
}

private fun parseSseMessages(source: BufferedSource): List<JSONObject> {
    val results = mutableListOf<JSONObject>()
    readSseMessages(source, results::add)
    return results
}

private fun readSseMessages(
    source: BufferedSource,
    onMessage: (JSONObject) -> Unit,
) {
    val eventData = StringBuilder()
    while (true) {
        val line = source.readUtf8Line() ?: break
        if (line.isEmpty()) {
            if (eventData.isNotEmpty()) {
                runCatching { JSONObject(eventData.toString()) }.getOrNull()?.let(onMessage)
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
        runCatching { JSONObject(eventData.toString()) }.getOrNull()?.let(onMessage)
    }
}

private fun encodeBase64(value: String): String =
    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeBase64(value: String): String =
    if (value.isBlank()) {
        ""
    } else {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }

private fun describeMcpMessage(message: JSONObject): String = buildString {
    val method = message.optString("method").trim()
    val id = message.opt("id")?.toString().orEmpty()
    if (method.isNotEmpty()) {
        append("method=")
        append(method)
    } else {
        append("response")
    }
    if (id.isNotEmpty()) {
        append(" id=")
        append(id)
    }
    when {
        message.has("result") -> append(" result")
        message.has("error") -> append(" error")
    }
}

private fun logMcp(message: String) {
    if (EnableMcpLogging) {
        Log.d(McpLogTag, message)
    }
}

private fun parseKeyValueOutput(stdout: String): Map<String, String> = buildMap {
    stdout.lineSequence()
        .filter { it.isNotBlank() }
        .forEach { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            put(
                line.substring(0, separatorIndex),
                line.substring(separatorIndex + 1),
            )
        }
}
