package com.zhousl.aether.data.pi

import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeProcessSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class SharedMcpTransport { Http, Stdio }

enum class SharedMcpInspection { Tools, Resources, Prompts }

data class SharedMcpServerConfig(
    val id: String = "mcp-${platformCurrentTimeMillis()}",
    val name: String,
    val actionLabel: String = "",
    val transport: SharedMcpTransport,
    val url: String = "",
    val command: String = "",
    val arguments: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val workingDirectory: String = "",
    val environment: Map<String, String> = emptyMap(),
    val runtimeEnvironment: String = "default",
    val connectTimeoutMillis: Long = 15_000L,
    val requestTimeoutMillis: Long = 60_000L,
    val enabled: Boolean = true,
    val createdAtMillis: Long = platformCurrentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

data class SharedMcpToolBinding(
    val exposedName: String,
    val serverId: String,
    val serverName: String,
    val remoteName: String,
    val description: String,
    val inputSchema: JsonObject,
)

class SharedMcpManager(
    private val runtime: MultiplatformLocalRuntime,
) {
    private val storageRoot = "${runtime.workspaceRoot.trimEnd('/')}/.aether"
    private val legacyConfigPath = "${runtime.homeDirectory.trimEnd('/')}/.aether/mcp-servers.json"
    private val configPath = "$storageRoot/mcp-servers.json"
    private val clientPath = "$storageRoot/mcp-client-v2.mjs"
    private var bindingsBySession: Map<String, List<SharedMcpToolBinding>> = emptyMap()
    private var serversBySession: Map<String, List<SharedMcpServerConfig>> = emptyMap()
    private var catalogAvailabilityBySession: Map<String, Boolean> = emptyMap()
    private var clientInstalledForThisRun = false
    private val clientInstallMutex = Mutex()
    private val storageMigrationMutex = Mutex()

    suspend fun loadServers(): List<SharedMcpServerConfig> {
        ensureStorageReady()
        if (!runtime.fileSystem.exists(configPath)) return emptyList()
        return parseSharedMcpServers(runtime.fileSystem.read(configPath).decodeToString())
    }

    suspend fun saveServers(servers: List<SharedMcpServerConfig>) {
        ensureStorageReady()
        runtime.fileSystem.write(
            configPath,
            serializeSharedMcpServers(servers).encodeToByteArray(),
        )
    }

    suspend fun refreshBindings(
        servers: List<SharedMcpServerConfig>? = null,
        sessionId: String = "",
    ): List<SharedMcpToolBinding> {
        val resolvedServers = (servers ?: loadServers()).filter(SharedMcpServerConfig::enabled)
        serversBySession = serversBySession + (sessionId to resolvedServers)
        val discovered = mutableListOf<SharedMcpToolBinding>()
        var hasCatalog = false
        var firstFailure: Throwable? = null
        for (server in resolvedServers) {
            val response = try {
                runClient("tools/list", server, null)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
                continue
            }
            val tools = (response["tools"] as? JsonArray).orEmpty()
            if (tools.isNotEmpty()) hasCatalog = true
            tools.mapNotNullTo(discovered) { element ->
                val tool = element as? JsonObject ?: return@mapNotNullTo null
                val remoteName = tool.string("name")
                if (remoteName.isBlank()) return@mapNotNullTo null
                SharedMcpToolBinding(
                    exposedName = sharedMcpToolName(server.id, remoteName),
                    serverId = server.id,
                    serverName = server.name,
                    remoteName = remoteName,
                    description = tool.string("description"),
                    inputSchema = tool["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }
            for (inspection in listOf(SharedMcpInspection.Resources, SharedMcpInspection.Prompts)) {
                val action = if (inspection == SharedMcpInspection.Resources) "resources/list" else "prompts/list"
                try {
                    val catalog = runClient(action, server, null)
                    val key = if (inspection == SharedMcpInspection.Resources) "resources" else "prompts"
                    if ((catalog[key] as? JsonArray).orEmpty().isNotEmpty()) hasCatalog = true
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // Optional catalogs may be unsupported even when tools/list succeeds.
                }
            }
        }
        bindingsBySession = bindingsBySession + (sessionId to discovered)
        catalogAvailabilityBySession = catalogAvailabilityBySession + (sessionId to hasCatalog)
        firstFailure?.let { throw it }
        return discovered
    }

    fun serversForSession(sessionId: String): List<SharedMcpServerConfig> =
        serversBySession[sessionId].orEmpty()

    fun hasCatalog(sessionId: String): Boolean = catalogAvailabilityBySession[sessionId] == true

    fun definitions(sessionId: String = ""): JsonArray = buildJsonArray {
        bindingsBySession[sessionId].orEmpty().forEach { binding ->
            add(buildJsonObject {
                put("name", binding.exposedName)
                put(
                    "description",
                    buildString {
                        append("Call MCP tool ")
                        append(binding.serverName)
                        append("/")
                        append(binding.remoteName)
                        if (binding.description.isNotBlank()) {
                            append(": ")
                            append(binding.description)
                        }
                    },
                )
                put("execution_mode", "parallel")
                put(
                    "parameters",
                    if ("type" in binding.inputSchema) binding.inputSchema
                    else JsonObject(binding.inputSchema + ("type" to JsonPrimitive("object"))),
                )
            })
        }
    }

    suspend fun execute(exposedName: String, arguments: JsonObject): SharedHostToolResult = try {
        val sessionId = arguments.sharedHostToolSessionId()
        val binding = bindingsBySession[sessionId].orEmpty()
            .firstOrNull { it.matches(exposedName) }
            ?: error("Unknown MCP tool '$exposedName'.")
        val server = serversBySession[sessionId].orEmpty()
            .firstOrNull { it.id == binding.serverId && it.enabled }
            ?: error("MCP server '${binding.serverId}' is not connected.")
        val response = runClient(
            "tools/call",
            server,
            buildJsonObject {
                put("name", binding.remoteName)
                put(
                    "arguments",
                    JsonObject(arguments.filterKeys { !it.startsWith("__aether_") }),
                )
            },
        )
        SharedHostToolResult(mcpToolCallOutput(server, binding.remoteName, response).toString())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (failure: Throwable) {
        sharedMcpFailure(failure.message ?: "MCP tool call failed.")
    }

    suspend fun inspectServer(serverId: String, operation: SharedMcpInspection): JsonObject {
        val server = loadServers().firstOrNull { it.id == serverId }
            ?: error("MCP server was not found.")
        val action = when (operation) {
            SharedMcpInspection.Tools -> "tools/list"
            SharedMcpInspection.Resources -> "resources/list"
            SharedMcpInspection.Prompts -> "prompts/list"
        }
        return runClient(action, server, null, includeMetadata = true)
    }

    suspend fun callTool(serverId: String, name: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject {
        val server = configuredServer(serverId)
        val result = runClient(
            action = "tools/call",
            server = server,
            call = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        )
        return mcpToolCallOutput(server, name, result)
    }

    suspend fun readResource(serverId: String, uri: String): JsonObject {
        val server = configuredServer(serverId)
        val result = runClient(
            action = "resources/read",
            server = server,
            call = buildJsonObject { put("uri", uri) },
        )
        return buildJsonObject {
            put("ok", true)
            put("server_id", server.id)
            put("uri", uri)
            put("result", result)
            put("stdout", "Read MCP resource $uri.")
        }
    }

    suspend fun getPrompt(serverId: String, name: String, arguments: Map<String, String> = emptyMap()): JsonObject {
        val server = configuredServer(serverId)
        val result = runClient(
            action = "prompts/get",
            server = server,
            call = buildJsonObject {
                put("name", name)
                put("arguments", JsonObject(arguments.mapValues { JsonPrimitive(it.value) }))
            },
        )
        return buildJsonObject {
            put("ok", true)
            put("server_id", server.id)
            put("name", name)
            put("result", result)
            put("stdout", "Fetched MCP prompt ${server.name}/$name.")
        }
    }

    private suspend fun configuredServer(serverId: String): SharedMcpServerConfig =
        loadServers().firstOrNull { it.id == serverId && it.enabled }
            ?: error("MCP server '$serverId' is not connected.")

    private fun mcpToolCallOutput(
        server: SharedMcpServerConfig,
        name: String,
        result: JsonObject,
    ): JsonObject = buildJsonObject {
        put("ok", true)
        put("server_id", server.id)
        put("server_name", server.name)
        put("tool_name", name)
        put("result", result)
        put("stdout", "Called MCP tool ${server.name}/$name.")
    }

    private suspend fun runClient(
        action: String,
        server: SharedMcpServerConfig,
        call: JsonObject?,
        includeMetadata: Boolean = false,
    ): JsonObject = coroutineScope {
        ensureClientInstalled()
        val process = runtime.startProcess(
            RuntimeProcessSpec(
                executable = "/usr/bin/node",
                arguments = listOf(clientPath),
                workingDirectory = runtime.workspaceRoot,
                environment = mapOf("HOME" to runtime.homeDirectory),
            )
        )
        val stdout = async { process.stdout.toList().flattenMcpBytes().decodeToString() }
        val stderr = async { process.stderr.toList().flattenMcpBytes().decodeToString() }
        process.writeStdin(
            buildJsonObject {
                put("action", action)
                put("server", server.toJson())
                if (call != null) put("call", call)
                if (includeMetadata) put("includeMetadata", true)
            }.toString().encodeToByteArray()
        )
        process.closeStdin()
        val exit = process.awaitExit()
        val output = stdout.await().trim()
        val errors = stderr.await().trim()
        check(exit.exitCode == 0) { errors.ifBlank { "MCP client exited with ${exit.exitCode}." } }
        Json.parseToJsonElement(output).jsonObject
    }

    private suspend fun ensureClientInstalled() {
        ensureStorageReady()
        clientInstallMutex.withLock {
            if (clientInstalledForThisRun && runtime.fileSystem.exists(clientPath)) return@withLock
            runtime.fileSystem.createDirectories(clientPath.substringBeforeLast('/'))
            runtime.fileSystem.write(clientPath, SharedMcpNodeClient.encodeToByteArray())
            clientInstalledForThisRun = true
        }
    }

    private suspend fun ensureStorageReady() {
        storageMigrationMutex.withLock {
            runtime.fileSystem.createDirectories(storageRoot)
            if (
                legacyConfigPath != configPath &&
                !runtime.fileSystem.exists(configPath) &&
                runtime.fileSystem.exists(legacyConfigPath)
            ) {
                runtime.fileSystem.write(configPath, runtime.fileSystem.read(legacyConfigPath))
                runtime.fileSystem.remove(legacyConfigPath)
            }
        }
    }
}

class SharedToolRegistry(
    private val runtimeTools: SharedHostToolExecutor,
    private val mcp: SharedMcpManager,
) : SharedSessionAwareHostToolExecutor {
    override val definitions: JsonArray
        get() = JsonArray(runtimeTools.definitions + mcp.definitions())

    override fun definitions(sessionId: String): JsonArray =
        JsonArray(runtimeTools.definitions + mcp.definitions(sessionId))

    override suspend fun execute(name: String, arguments: JsonObject): SharedHostToolResult =
        if (name.startsWith("mcp__") || ':' in name) {
            mcp.execute(name, arguments)
        } else {
            runtimeTools.execute(name, arguments)
        }
}

private fun SharedMcpToolBinding.matches(callName: String): Boolean =
    exposedName.equals(callName.trim(), ignoreCase = true) ||
        "$serverId:$remoteName".equals(callName.trim(), ignoreCase = true) ||
        "$serverName:$remoteName".equals(callName.trim(), ignoreCase = true)

private fun sharedMcpFailure(message: String): SharedHostToolResult = SharedHostToolResult(
    outputJson = buildJsonObject {
        put("ok", false)
        put("errmsg", message)
    }.toString(),
    isError = true,
)

internal fun SharedMcpServerConfig.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("actionLabel", actionLabel)
    put("transport", transport.name.lowercase())
    put("url", url)
    put("command", command)
    put("arguments", buildJsonArray { arguments.forEach { add(JsonPrimitive(it)) } })
    put("headers", JsonObject(headers.mapValues { JsonPrimitive(it.value) }))
    put("workingDirectory", workingDirectory)
    put("environment", JsonObject(environment.mapValues { JsonPrimitive(it.value) }))
    put("runtimeEnvironment", runtimeEnvironment)
    put("connectTimeoutMillis", connectTimeoutMillis)
    put("requestTimeoutMillis", requestTimeoutMillis)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

internal fun sharedMcpToolName(serverId: String, remoteName: String): String =
    "mcp__${serverId}__${remoteName}"

internal fun serializeSharedMcpServers(servers: List<SharedMcpServerConfig>): String =
    buildJsonArray {
        servers.forEach { server ->
            add(JsonObject(server.toJson() + ("enabled" to JsonPrimitive(server.enabled))))
        }
    }.toString()

internal fun parseSharedMcpServers(value: String): List<SharedMcpServerConfig> = runCatching {
    Json.parseToJsonElement(value).jsonArray.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val transportConfig = item["transport"] as? JsonObject
        val transportName = item.string("transport").ifBlank { transportConfig?.string("type").orEmpty() }
        SharedMcpServerConfig(
            id = item.string("id"),
            name = item.string("name").ifBlank { item.string("displayName") }.ifBlank { item.string("display_name") },
            actionLabel = item.string("actionLabel").ifBlank { item.string("action_label") },
            transport = if (transportName == "stdio") SharedMcpTransport.Stdio else SharedMcpTransport.Http,
            url = item.string("url").ifBlank { transportConfig?.string("url").orEmpty() },
            command = item.string("command").ifBlank { transportConfig?.string("command").orEmpty() },
            arguments = ((item["arguments"] ?: transportConfig?.get("arguments") ?: transportConfig?.get("args")) as? JsonArray).orEmpty().mapNotNull {
                it.jsonPrimitive.contentOrNull
            },
            headers = item.stringMap("headers").ifEmpty { transportConfig?.stringMap("headers").orEmpty() },
            workingDirectory = item.string("workingDirectory")
                .ifBlank { item.string("working_directory") }
                .ifBlank { transportConfig?.string("workingDirectory").orEmpty() }
                .ifBlank { transportConfig?.string("working_directory").orEmpty() },
            environment = item.stringMap("environment").ifEmpty { transportConfig?.stringMap("environment").orEmpty() },
            runtimeEnvironment = item.string("runtimeEnvironment")
                .ifBlank { item.string("runtime_environment") }
                .ifBlank { transportConfig?.string("runtimeEnvironment").orEmpty() }
                .ifBlank { transportConfig?.string("runtime_environment").orEmpty() }
                .ifBlank { (transportConfig?.get("environment") as? JsonPrimitive)?.contentOrNull.orEmpty() }
                .ifBlank { "default" },
            connectTimeoutMillis = item["connectTimeoutMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 15_000L,
            requestTimeoutMillis = item["requestTimeoutMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 60_000L,
            enabled = (item["enabled"] ?: item["isEnabled"] ?: item["is_enabled"])
                ?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
            createdAtMillis = item["createdAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: platformCurrentTimeMillis(),
            updatedAtMillis = item["updatedAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: item["createdAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: platformCurrentTimeMillis(),
        )
    }
}.getOrDefault(emptyList())

private fun JsonObject.string(name: String): String = (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.stringMap(name: String): Map<String, String> = when (val value = get(name)) {
    is JsonObject -> value.mapNotNull { (key, entry) ->
        (entry as? JsonPrimitive)?.contentOrNull?.let { key to it }
    }.toMap()
    is JsonArray -> value.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        entry.string("key").takeIf(String::isNotBlank)?.let { it to entry.string("value") }
    }.toMap()
    else -> emptyMap()
}

private fun List<ByteArray>.flattenMcpBytes(): ByteArray {
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { bytes -> bytes.copyInto(result, offset).also { offset += bytes.size } }
    return result
}

internal val SharedMcpNodeClient = """
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
const input = await new Promise((resolve, reject) => {
  const chunks = []; process.stdin.on('data', c => chunks.push(c));
  process.stdin.on('end', () => resolve(Buffer.concat(chunks).toString('utf8'))); process.stdin.on('error', reject);
});
const request = JSON.parse(input); const server = request.server; let nextId = 1;
const parseHttp = async (response) => {
  const text = await response.text();
  if (!response.ok) throw new Error(`HTTP ${'$'}{response.status}: ${'$'}{text}`);
  if ((response.headers.get('content-type') || '').includes('text/event-stream')) {
    const lines = text.split(/\r?\n/).filter(x => x.startsWith('data:'));
    return JSON.parse(lines.at(-1).slice(5).trim());
  }
  return JSON.parse(text);
};
async function httpClient() {
  let session = '';
  let protocolVersion = '2025-11-25';
  const send = async (method, params, notification = false) => {
    const payload = {jsonrpc:'2.0', method, ...(params ? {params} : {}), ...(notification ? {} : {id: nextId++})};
    const timeout = method === 'initialize' ? (server.connectTimeoutMillis || 15000) : (server.requestTimeoutMillis || 60000);
    const response = await fetch(server.url, {method:'POST', headers:{'content-type':'application/json','accept':'application/json, text/event-stream', ...(server.headers || {}), ...(protocolVersion ? {'mcp-protocol-version':protocolVersion} : {}), ...(session ? {'mcp-session-id':session} : {})}, body:JSON.stringify(payload), signal:AbortSignal.timeout(timeout)});
    session ||= response.headers.get('mcp-session-id') || '';
    return notification ? {} : parseHttp(response);
  };
  const close = async () => {
    if (!session) return;
    try { await fetch(server.url, {method:'DELETE', headers:{...(server.headers || {}), ...(protocolVersion ? {'mcp-protocol-version':protocolVersion} : {}), 'mcp-session-id':session}}); } catch {}
  };
  return {send, setProtocolVersion: value => { protocolVersion = value || ''; }, close};
}
async function stdioClient() {
  const configuredCwd = server.workingDirectory || process.cwd();
  const cwd = configuredCwd === '~' ? (process.env.HOME || configuredCwd) : configuredCwd.replace(/^~\//, `${'$'}{process.env.HOME || '~'}/`);
  const child = spawn(server.command, server.arguments || [], {stdio:['pipe','pipe','pipe'], cwd, env:{...process.env, ...(server.environment || {})}});
  let stderr = ''; child.stderr.on('data', c => stderr += c.toString());
  const pending = new Map();
  createInterface({input:child.stdout}).on('line', line => { try { const msg=JSON.parse(line); if (msg.id != null && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); } } catch {} });
  const send = async (method, params, notification = false) => {
    const id = nextId++; const payload={jsonrpc:'2.0',method,...(params?{params}:{}),...(notification?{}:{id})};
    child.stdin.write(JSON.stringify(payload)+'\n'); if (notification) return {};
    const timeout = method === 'initialize' ? (server.connectTimeoutMillis || 15000) : (server.requestTimeoutMillis || 60000);
    return await new Promise((resolve,reject) => { pending.set(id,resolve); setTimeout(() => { if(pending.delete(id)) reject(new Error(`MCP stdio timeout: ${'$'}{stderr}`)); },timeout); });
  };
  return {send, setProtocolVersion: () => {}, close: async () => { child.kill('SIGTERM'); }};
}
const client = server.transport === 'stdio' ? await stdioClient() : await httpClient();
try {
  const initialized = await client.send('initialize',{protocolVersion:'2025-11-25',capabilities:{},clientInfo:{name:'Aether',version:'1'}});
  if (initialized.error) throw new Error(initialized.error.message || 'MCP initialize failed');
  const initialization = initialized.result || {};
  client.setProtocolVersion(initialization.protocolVersion || '');
  await client.send('notifications/initialized',{},true);
  const response = await client.send(request.action, request.call || {});
  if (response.error) throw new Error(response.error.message || 'MCP request failed');
  const result = response.result || {};
  const serverInfo = initialization.serverInfo || {};
  const output = request.includeMetadata ? {
    ...(result && typeof result === 'object' && !Array.isArray(result) ? result : {result}),
    protocol_version: initialization.protocolVersion || '',
    server_info: [serverInfo.name, serverInfo.version].filter(Boolean).join(' '),
    server_name: server.name || server.id || '',
  } : result;
  process.stdout.write(JSON.stringify(output));
} finally { await client.close(); }
""".trimIndent()
