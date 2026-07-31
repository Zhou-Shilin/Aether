package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.SharedSkillManager
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeFileSystem
import com.zhousl.aether.runtime.RuntimePiBridgeTransport
import com.zhousl.aether.runtime.RuntimeProcess
import com.zhousl.aether.runtime.RuntimeProcessSpec
import com.zhousl.aether.runtime.RuntimeSetupProgress
import com.zhousl.aether.runtime.SharedPiBridgeClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SharedAgentManagementToolsTest {
    @Test
    fun exposesPortableAndroidParityToolsOnly() {
        val fixture = AgentManagementFixture()
        val names = fixture.tools.definitions.mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }.toSet()

        assertTrue(
            setOf(
                "analyze_image",
                "activate_skill",
                "read_skill_resource",
                "mcp_list_tools",
                "mcp_call_tool",
                "mcp_list_resources",
                "mcp_read_resource",
                "mcp_list_prompts",
                "mcp_get_prompt",
                "aether_config_get",
                "aether_config_set",
                "aether_skill_manage",
                "aether_mcp_manage",
                "aether_extension_manage",
                "aether_developer_manage",
            ).all(names::contains)
        )
        assertFalse("aether_termux_manage" in names)
        assertFalse("aether_agent_mode_manage" in names)
        assertFalse("aether_scheduled_task_manage" in names)
        assertFalse("chrome" in names)
    }

    @Test
    fun hidesGenericMcpToolsWhenCurrentSessionHasNoCatalog() {
        val fixture = AgentManagementFixture()
        val names = fixture.tools.definitions("session-without-mcp").mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }.toSet()

        assertTrue(GenericMcpNamesForTest.none(names::contains))
        assertTrue("aether_mcp_manage" in names)
    }

    @Test
    fun compositeRoutesSessionAwareDynamicTools() = runTest {
        val dynamic = object : SharedSessionAwareHostToolExecutor {
            override val definitions = JsonArray(emptyList())
            override fun definitions(sessionId: String): JsonArray = JsonArray(listOf(buildJsonObject {
                put("name", "mcp__server__tool")
            }))

            override suspend fun execute(name: String, arguments: JsonObject): SharedHostToolResult =
                SharedHostToolResult("{\"ok\":true}")
        }
        val result = SharedCompositeHostTools(listOf(dynamic)).execute(
            "mcp__server__tool",
            JsonObject(mapOf("__aether_session_id" to JsonPrimitive("session"))),
        )

        assertFalse(result.isError)
        assertEquals("{\"ok\":true}", result.outputJson)
    }

    @Test
    fun updatesPortableReliabilitySettings() = runTest {
        val fixture = AgentManagementFixture()

        val result = fixture.tools.execute(
            "aether_config_set",
            buildJsonObject {
                put("category", "reliability")
                put("settings", buildJsonObject {
                    put("llm_inactivity_reconnect_timeout_seconds", 5)
                    put("auto_clean_old_command_history", false)
                    put("old_command_history_retention_hours", 500)
                })
            },
        )

        assertFalse(result.isError)
        assertEquals(30, fixture.settings.llmInactivityReconnectTimeoutSeconds)
        assertFalse(fixture.settings.autoCleanOldCommandHistory)
        assertEquals(168, fixture.settings.oldCommandHistoryRetentionHours)
    }

    @Test
    fun imageAnalysisRejectsPathsOutsideWorkspaceBeforeReading() = runTest {
        val fixture = AgentManagementFixture()

        val result = fixture.tools.execute(
            "analyze_image",
            buildJsonObject { put("path", "../../root/private.png") },
        )
        val payload = Json.parseToJsonElement(result.outputJson).jsonObject

        assertTrue(result.isError)
        assertTrue(payload.getValue("errmsg").jsonPrimitive.content.contains("workspace"))
        assertFalse("error" in payload)
        assertTrue(fixture.runtime.readPaths.isEmpty())
    }

    @Test
    fun imageAnalysisUsesAndroidFiveMebibyteLimit() = runTest {
        val fixture = AgentManagementFixture()
        fixture.runtime.files["/workspace/large.png"] = ByteArray(5 * 1024 * 1024 + 1)

        val result = fixture.tools.execute(
            "analyze_image",
            buildJsonObject { put("path", "large.png") },
        )

        assertTrue(result.isError)
        assertTrue(
            Json.parseToJsonElement(result.outputJson).jsonObject
                .getValue("errmsg").jsonPrimitive.content.contains("allowed size"),
        )
    }

    @Test
    fun cancellationIsNotConvertedIntoToolFailure() = runTest {
        val fixture = AgentManagementFixture(
            updateSettings = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            fixture.tools.execute(
                "aether_config_set",
                buildJsonObject {
                    put("category", "general")
                    put("settings", buildJsonObject { put("theme_mode", "dark") })
                },
            )
        }
    }
}

private val GenericMcpNamesForTest = setOf(
    "mcp_list_tools",
    "mcp_call_tool",
    "mcp_list_resources",
    "mcp_read_resource",
    "mcp_list_prompts",
    "mcp_get_prompt",
)

private class AgentManagementFixture(
    updateSettings: (suspend (AppSettings) -> Unit)? = null,
) {
    val runtime = AgentManagementFakeRuntime()
    private val bridge = SharedPiBridgeClient(RuntimePiBridgeTransport(runtime))
    var settings = AppSettings()
    val tools = SharedAgentManagementTools(
        runtime = runtime,
        bridge = bridge,
        skillManager = SharedSkillManager(runtime),
        mcpManager = SharedMcpManager(runtime),
        completionClient = SharedPiChatClient(bridge),
        settings = { settings },
        updateSettings = updateSettings ?: { settings = it },
        activeSkills = { emptyList() },
        activateSkill = { _, _ -> },
        currentSessionId = { "test-session" },
        resolveProvider = { null },
    )
}

private class AgentManagementFakeRuntime : MultiplatformLocalRuntime {
    override val homeDirectory = "/root"
    override val workspaceRoot = "/workspace"
    val readPaths = mutableListOf<String>()
    val files = mutableMapOf<String, ByteArray>()

    override val fileSystem: RuntimeFileSystem = object : RuntimeFileSystem {
        override suspend fun exists(path: String): Boolean = path in files
        override suspend fun createDirectories(path: String) = Unit
        override suspend fun read(path: String): ByteArray {
            readPaths += path
            return files[path] ?: error("Unexpected read: $path")
        }
        override suspend fun write(path: String, content: ByteArray, executable: Boolean) = Unit
        override suspend fun remove(path: String, recursive: Boolean) = Unit
        override suspend fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean) = Unit
    }

    override suspend fun initialize(onProgress: (RuntimeSetupProgress) -> Unit) = Unit

    override suspend fun startProcess(spec: RuntimeProcessSpec): RuntimeProcess =
        error("Unexpected process: ${spec.executable}")
}
