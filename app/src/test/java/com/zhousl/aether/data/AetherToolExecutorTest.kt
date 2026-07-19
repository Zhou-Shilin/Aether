package com.zhousl.aether.data

import com.zhousl.aether.runtime.LocalRuntime
import com.zhousl.aether.runtime.LocalRuntimeIssue
import com.zhousl.aether.runtime.LocalRuntimeSetupState
import com.zhousl.aether.runtime.RuntimeRouter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherToolExecutorTest {
    @Test
    fun hostToolDefinitionsExposeInitialPiToolSlice() {
        val definitions = AetherToolExecutor.hostToolDefinitions()
        val names = buildList {
            for (index in 0 until definitions.length()) {
                add(definitions.getJSONObject(index).getString("name"))
            }
        }

        assertTrue(AetherToolExecutor.hostToolNames.containsAll(names))
        val bash = (0 until definitions.length())
            .map { definitions.getJSONObject(it) }
            .first { it.getString("name") == "bash" }
        assertEquals("sequential", bash.getString("execution_mode"))
        assertEquals("object", bash.getJSONObject("parameters").getString("type"))
        assertEquals(
            listOf("command"),
            bash.getJSONObject("parameters").getJSONArray("required").toStringList(),
        )
        assertEquals(
            "string",
            bash.getJSONObject("parameters")
                .getJSONObject("properties")
                .getJSONObject("working_directory")
                .getString("type"),
        )

        val activateSkill = (0 until definitions.length())
            .map { definitions.getJSONObject(it) }
            .first { it.getString("name") == "activate_skill" }
        assertEquals("sequential", activateSkill.getString("execution_mode"))
        assertEquals(
            listOf("name"),
            activateSkill.getJSONObject("parameters").getJSONArray("required").toStringList(),
        )

        val fetchWebUrl = (0 until definitions.length())
            .map { definitions.getJSONObject(it) }
            .first { it.getString("name") == "fetch_web_url" }
        assertEquals("parallel", fetchWebUrl.getString("execution_mode"))
        assertEquals(
            listOf("url"),
            fetchWebUrl.getJSONObject("parameters").getJSONArray("required").toStringList(),
        )
    }

    @Test
    fun sanitizeAgentDisplayOutputRemovesScreenshotBytes() {
        val sanitized = AetherToolExecutor.sanitizeToolOutputForConversation(
            toolName = "agent_display",
            output = JSONObject().apply {
                put("ok", true)
                put("screenshot_base64", "abc123")
                put("screenshot_mime_type", "image/png")
            }.toString(),
        )

        val json = JSONObject(sanitized)
        assertFalse(json.has("screenshot_base64"))
        assertTrue(json.getBoolean("screenshot_injected_into_next_model_request"))
        assertEquals("image/png", json.getString("screenshot_mime_type"))
    }

    @Test
    fun sanitizeChromeOutputRemovesScreenshotBytes() {
        val sanitized = AetherToolExecutor.sanitizeToolOutputForConversation(
            toolName = "chrome",
            output = JSONObject().apply {
                put("ok", true)
                put("screenshot_base64", "chrome-bytes")
                put("screenshot_mime_type", "image/jpeg")
                put("preview_path", "/cache/chrome-preview.jpg")
            }.toString(),
        )

        val json = JSONObject(sanitized)
        assertFalse(json.has("screenshot_base64"))
        assertTrue(json.getBoolean("screenshot_injected_into_next_model_request"))
        assertEquals("/cache/chrome-preview.jpg", json.getString("preview_path"))
    }

    @Test
    fun dynamicHostToolDefinitionsIncludeMcpAndAgentMode() {
        val definitions = AetherToolExecutor.hostToolDefinitions(
            selfManagementTool = null,
            mcpClientManager = null,
            mcpToolBindings = listOf(
                McpToolBinding(
                    serverId = "docs",
                    serverName = "Docs",
                    toolName = "search",
                    description = "Search documentation.",
                    inputSchema = JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().put(
                                "query",
                                JSONObject().put("type", "string"),
                            ),
                        )
                    },
                )
            ),
            agentModeEnabled = true,
            chromeEnabled = true,
        )
        val names = (0 until definitions.length())
            .map { definitions.getJSONObject(it).getString("name") }

        assertTrue("mcp_list_tools" in names)
        assertTrue("mcp__docs__search" in names)
        assertTrue("agent_display" in names)
        assertTrue("chrome" in names)
        assertEquals(
            "sequential",
            (0 until definitions.length())
                .map { definitions.getJSONObject(it) }
                .first { it.getString("name") == "agent_display" }
                .getString("execution_mode"),
        )
        assertEquals(
            "sequential",
            (0 until definitions.length())
                .map { definitions.getJSONObject(it) }
                .first { it.getString("name") == "chrome" }
                .getString("execution_mode"),
        )
    }

    @Test
    fun inferToolOutputOkHonorsAetherJsonFlags() {
        assertTrue(AetherToolExecutor.inferToolOutputOk("""{"ok":true}"""))
        assertFalse(AetherToolExecutor.inferToolOutputOk("""{"ok":false}"""))
        assertFalse(AetherToolExecutor.inferToolOutputOk("""{"err":true}"""))
        assertTrue(AetherToolExecutor.inferToolOutputOk("plain text"))
    }

    @Test
    fun defaultWorkingDirectoryFollowsExplicitRuntime() {
        val runtimeRouter = RuntimeRouter(
            termuxRuntime = ToolExecutorFakeRuntime(
                id = LocalRuntimeId.Termux,
                workspaceRoot = "/termux-default-workspace",
            ),
            alpineRuntime = ToolExecutorFakeRuntime(
                id = LocalRuntimeId.Alpine,
                workspaceRoot = "/workspace",
            ),
        )
        val settings = AppSettings(
            enabledRuntimeIds = setOf(LocalRuntimeId.Termux, LocalRuntimeId.Alpine),
            defaultRuntimeId = LocalRuntimeId.Alpine,
            termuxSetupCompleted = true,
            alpineSetupCompleted = true,
        )
        val termuxArguments = JSONObject(
            injectDefaultWorkingDirectory(
                argumentsJson = """{"environment":"termux","command":"pwd"}""",
                settings = settings,
                runtimeRouter = runtimeRouter,
                workspaceDirectory = "/workspace",
                termuxWorkspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            ),
        )
        val alpineArguments = JSONObject(
            injectDefaultWorkingDirectory(
                argumentsJson = """{"environment":"alpine","command":"pwd"}""",
                settings = settings.copy(defaultRuntimeId = LocalRuntimeId.Termux),
                runtimeRouter = runtimeRouter,
                workspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
                termuxWorkspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            ),
        )

        assertEquals(
            "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            termuxArguments.getString("working_directory"),
        )
        assertEquals("/workspace", alpineArguments.getString("working_directory"))
    }

    @Test
    fun explicitWorkingDirectoryStillWinsAcrossRuntimeSwitches() {
        val runtimeRouter = RuntimeRouter(
            termuxRuntime = ToolExecutorFakeRuntime(LocalRuntimeId.Termux, "/termux-workspace"),
            alpineRuntime = ToolExecutorFakeRuntime(LocalRuntimeId.Alpine, "/workspace"),
        )

        val arguments = JSONObject(
            injectDefaultWorkingDirectory(
                argumentsJson = """{"environment":"termux","command":"pwd","workingDirectory":"~"}""",
                settings = AppSettings(
                    enabledRuntimeIds = setOf(LocalRuntimeId.Termux, LocalRuntimeId.Alpine),
                    defaultRuntimeId = LocalRuntimeId.Alpine,
                ),
                runtimeRouter = runtimeRouter,
                workspaceDirectory = "/workspace",
                termuxWorkspaceDirectory = "/termux-workspace",
            ),
        )

        assertEquals("~", arguments.getString("working_directory"))
        assertFalse(arguments.has("workingDirectory"))
    }

    @Test
    fun workspaceFileRoutingRecognizesAlpineAndTermuxRoots() {
        assertEquals(
            LocalRuntimeId.Alpine,
            resolveWorkspaceRuntimeId(
                path = "/workspace/agent-mode/capture.png",
                workingDirectory = "",
                defaultRuntimeId = LocalRuntimeId.Termux,
            ),
        )
        assertEquals(
            LocalRuntimeId.Termux,
            resolveWorkspaceRuntimeId(
                path = "/data/data/com.termux/files/home/.aether/workspace/uploads/image.png",
                workingDirectory = "",
                defaultRuntimeId = LocalRuntimeId.Alpine,
            ),
        )
        assertEquals(
            LocalRuntimeId.Alpine,
            resolveWorkspaceRuntimeId(
                path = "relative.png",
                workingDirectory = "/workspace",
                defaultRuntimeId = LocalRuntimeId.Termux,
            ),
        )
        assertEquals(
            LocalRuntimeId.Termux,
            resolveWorkspaceRuntimeId(
                path = "/data/data/com.termux/files/home/.aether/workspace/output.png",
                workingDirectory = "/workspace",
                defaultRuntimeId = LocalRuntimeId.Alpine,
            ),
        )
        assertEquals(
            LocalRuntimeId.Alpine,
            resolveWorkspaceRuntimeId(
                path = "file:///workspace/agent-mode/capture.png",
                workingDirectory = "/data/data/com.termux/files/home/.aether/workspace",
                defaultRuntimeId = LocalRuntimeId.Termux,
            ),
        )
    }

    @Test
    fun stdioMcpWorkspaceFollowsItsConfiguredRuntime() {
        val runtimeRouter = RuntimeRouter(
            termuxRuntime = ToolExecutorFakeRuntime(LocalRuntimeId.Termux, "/termux-workspace"),
            alpineRuntime = ToolExecutorFakeRuntime(LocalRuntimeId.Alpine, "/workspace"),
        )
        val settings = AppSettings(
            enabledRuntimeIds = setOf(LocalRuntimeId.Termux, LocalRuntimeId.Alpine),
            defaultRuntimeId = LocalRuntimeId.Alpine,
        )
        val termuxServer = McpServerConfig(
            id = "termux-server",
            displayName = "Termux server",
            transport = McpTransportConfig.StdIo(
                command = "server",
                runtimeEnvironment = LocalRuntimeId.Termux,
            ),
        )
        val alpineServer = McpServerConfig(
            id = "alpine-server",
            displayName = "Alpine server",
            transport = McpTransportConfig.StdIo(
                command = "server",
                runtimeEnvironment = LocalRuntimeId.Alpine,
            ),
        )

        assertEquals(
            "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            resolveMcpWorkspaceDirectory(
                server = termuxServer,
                settings = settings,
                runtimeRouter = runtimeRouter,
                workspaceDirectory = "/workspace",
                termuxWorkspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            ),
        )
        assertEquals(
            "/workspace",
            resolveMcpWorkspaceDirectory(
                server = alpineServer,
                settings = settings.copy(defaultRuntimeId = LocalRuntimeId.Termux),
                runtimeRouter = runtimeRouter,
                workspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
                termuxWorkspaceDirectory = "/data/data/com.termux/files/home/.aether/workspaces/session-1",
            ),
        )
    }
}

private fun org.json.JSONArray.toStringList(): List<String> =
    (0 until length()).map(::getString)

private class ToolExecutorFakeRuntime(
    override val id: LocalRuntimeId,
    override val workspaceRoot: String,
) : LocalRuntime {
    override val displayName: String = id.displayName
    override val homeDirectory: String = "/home"
    override val managedCommandsDirectory: String = "/runs"

    override suspend fun inspectSetup(): LocalRuntimeSetupState =
        LocalRuntimeSetupState(id, LocalRuntimeIssue.Ready)

    override suspend fun execute(
        argumentsJson: String,
        onProgress: (suspend (String) -> Unit)?,
    ): String = """{"ok":true}"""

    override suspend fun fetchExecution(argumentsJson: String): String = """{"ok":true}"""

    override suspend fun killExecution(argumentsJson: String): String = """{"ok":true}"""

    override suspend fun killExecutionByRunId(runId: String, tailBytes: Int): String =
        """{"ok":true}"""

    override suspend fun executeCommand(
        command: String,
        workingDirectory: String,
        awaitTimeoutMillis: Long,
    ): String = """{"ok":true}"""
}
