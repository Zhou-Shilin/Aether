package com.zhousl.aether.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExtensionsSerializationTest {
    @Test
    fun installedSkillsRoundTripPreservesKeyFields() {
        val skills = listOf(
            InstalledSkill(
                id = "skill-1",
                name = "Test Skill",
                description = "A test skill",
                license = "MIT",
                compatibility = "android",
                metadataJson = """{"owner":"aether"}""",
                allowedTools = listOf("bash", "read"),
                skillRootPath = "/tmp/skill-1",
                skillMdPath = "/tmp/skill-1/SKILL.md",
                source = SkillInstallSource(
                    kind = SkillInstallKind.RemoteZip,
                    label = "remote",
                    uri = "https://example.com/skill.zip",
                ),
                checksumSha256 = "abc123",
                isEnabled = true,
                diagnostics = listOf("diag-1"),
                resourceEntries = listOf(
                    SkillResourceEntry("SKILL.md", SkillResourceKind.Skill),
                    SkillResourceEntry("scripts/run.sh", SkillResourceKind.Script),
                ),
            ),
        )

        val reparsed = parseInstalledSkills(serializeInstalledSkills(skills))

        assertEquals(skills.size, reparsed.size)
        assertEquals(skills.first().id, reparsed.first().id)
        assertEquals(skills.first().allowedTools, reparsed.first().allowedTools)
        assertEquals(skills.first().resourceEntries, reparsed.first().resourceEntries)
        assertTrue(reparsed.first().isEnabled)
    }

    @Test
    fun activeSkillContextsRoundTripPreservesInstructions() {
        val activeSkills = listOf(
            ActiveSkillContext(
                skillId = "skill-1",
                name = "Planner",
                description = "Planning skill",
                compatibility = "android",
                allowedTools = listOf("read"),
                skillRootPath = "/tmp/planner",
                bodyMarkdown = "# Planner\n\nUse checklists.",
                resourceEntries = listOf(
                    SkillResourceEntry("references/guide.md", SkillResourceKind.Reference),
                ),
            ),
        )

        val reparsed = parseActiveSkillContexts(serializeActiveSkillContexts(activeSkills))

        assertEquals(activeSkills, reparsed)
    }

    @Test
    fun mcpServersRoundTripPreservesTransportDetails() {
        val servers = listOf(
            McpServerConfig(
                id = "server-http",
                displayName = "Remote",
                transport = McpTransportConfig.StreamableHttp(
                    url = "https://mcp.example.com",
                    headers = listOf(
                        McpKeyValue("Authorization", "Bearer token"),
                    ),
                ),
                isEnabled = true,
            ),
            McpServerConfig(
                id = "server-stdio",
                displayName = "Local",
                transport = McpTransportConfig.StdIo(
                    command = "python server.py",
                    arguments = listOf("--profile", "demo"),
                    workingDirectory = "~/.servers/demo",
                    environment = listOf(
                        McpKeyValue("DEBUG", "1"),
                    ),
                ),
                isEnabled = false,
            ),
        )

        val reparsed = parseMcpServerConfigs(serializeMcpServerConfigs(servers))

        assertEquals(servers.size, reparsed.size)
        assertEquals(servers.first().displayName, reparsed.first().displayName)
        assertTrue(reparsed[1].transport is McpTransportConfig.StdIo)
        assertEquals(
            "~/.servers/demo",
            (reparsed[1].transport as McpTransportConfig.StdIo).workingDirectory,
        )
        assertEquals(
            listOf("--profile", "demo"),
            (reparsed[1].transport as McpTransportConfig.StdIo).arguments,
        )
    }

    @Test
    fun skillResourceKindClassifiesStandardFolders() {
        assertEquals(SkillResourceKind.Skill, SkillResourceKind.fromRelativePath("SKILL.md"))
        assertEquals(SkillResourceKind.Script, SkillResourceKind.fromRelativePath("scripts/run.sh"))
        assertEquals(SkillResourceKind.Reference, SkillResourceKind.fromRelativePath("references/guide.md"))
        assertEquals(SkillResourceKind.Asset, SkillResourceKind.fromRelativePath("assets/logo.png"))
        assertEquals(SkillResourceKind.AgentMetadata, SkillResourceKind.fromRelativePath("agents/openai.yaml"))
    }
}
