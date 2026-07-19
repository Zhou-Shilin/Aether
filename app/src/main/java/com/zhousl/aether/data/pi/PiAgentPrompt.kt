package com.zhousl.aether.data.pi

import com.zhousl.aether.data.ActiveSkillContext
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.McpServerSnapshot
import com.zhousl.aether.data.McpToolBinding
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SkillMetadataContextBudgetChars = 8_000
private val DynamicPromptPlaceholderRegex = Regex("""\{\{\s*([A-Za-z0-9_-]+)\s*\}\}""")

internal fun buildPiAgentInstructions(
    settings: AppSettings,
    workspaceDirectory: String,
    availableSkills: List<InstalledSkill>,
    activeSkills: List<ActiveSkillContext>,
    mcpSnapshots: List<McpServerSnapshot>,
    mcpToolBindings: List<McpToolBinding>,
    agentModeEnabled: Boolean,
    chromeEnabled: Boolean = false,
): String = buildString {
    val configuredPrompt = expandDynamicPromptPlaceholders(settings.systemPrompt).trim()
    if (configuredPrompt.isNotBlank()) {
        append(configuredPrompt)
        append("\n\n")
    }
    append(
            "You are running inside Aether on Android with the Pi agent kernel. " +
            "Use available tools instead of guessing about local device state. " +
            "The default workspace for this chat is $workspaceDirectory. " +
            "User-uploaded files are under uploads/. Call analyze_image when you need to inspect a workspace image. " +
            "When linking a local file for the user to download, use a Markdown link with a file:// target and the absolute path, " +
            "for example [report.pdf](file:///absolute/path/report.pdf). Do not use another URI scheme for local file downloads. " +
            "Use fetch_web_url for a specific page and tavily_search for public-web discovery or current information. " +
            "Use aether_* tools to inspect or repair allowed Aether settings, skills, MCP servers, runtimes, Agent Mode, scheduled tasks, and diagnostics. " +
            "Never modify LLM provider credentials or model configuration through self-management tools. " +
            "Prefer read, edit, write, grep, find, and ls for filesystem work, and bash for shell commands or scripts. " +
            "If working_directory is omitted, tools run in the current session workspace. " +
            "Local tools accept environment='default', 'termux', or 'alpine'. " +
            "Termux is best for Android integration; Alpine is best for the built-in Linux environment and development tools. " +
            "When bash returns status=running, wait with sleep and poll with fetch_bash_output; stop it with kill_bash when needed. " +
            "Independent parallel-safe tools may run together; tools marked sequential must run in order. " +
            "For multi-step work, interleave concise progress updates with tool calls. " +
            "Only claim commands or device actions that were actually performed. " +
            "After using tools, summarize the result clearly."
    )

    if (agentModeEnabled) {
        append("\n\n")
        append(
            "Agent Mode is enabled for this chat. Use agent_display only for tasks that require operating the isolated Android virtual display. " +
                "Tap and swipe coordinates use the normalized 0..1000 range. " +
                "After display actions, screenshot image content is inserted directly into the Pi tool result for the next model step."
        )
    }

    if (chromeEnabled) {
        append("\n\n")
        append(
            "Alpine Chrome is enabled for this chat. Use the chrome tool only for tasks that require operating the dedicated Chromium browser. " +
                "Tap and swipe coordinates use the normalized 0..1000 range. " +
                "Use navigate for URLs, text and key for focused fields, and screenshot after actions when you need the current page. " +
                "Screenshot image content is inserted directly into the next model step."
        )
    }

    if (availableSkills.isNotEmpty()) {
        val (skillLines, omittedCount) = renderAvailableSkillLines(availableSkills)
        append("\n\n")
        append(
            "Installed Agent Skills are available. Aether may auto-activate matching skills for the current turn. " +
                "If the user explicitly names a skill or the task clearly matches one, you must use that skill's instructions. " +
                "Use activate_skill when you need an additional installed skill that is not already active, and do not claim a skill is active until the tool succeeds."
        )
        append("\n<available_skills>")
        skillLines.forEach { line ->
            append("\n")
            append(line)
        }
        if (omittedCount > 0) {
            append("\n- ")
            append(omittedCount)
            append(" additional skills omitted because of the context budget.")
        }
        append("\n</available_skills>")
    }

    activeSkills.forEach { skill ->
        append("\n\n<active_skill name=\"")
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
                append(")")
            }
            append("\n</resources>")
            append("\nUse read_skill_resource for only the bundled files needed by the task.")
        }
        append("\n<instructions>\n")
        append(skill.bodyMarkdown)
        append("\n</instructions>")
        append("\n</active_skill>")
    }

    if (mcpSnapshots.isNotEmpty()) {
        append("\n\n")
        append(
            "Connected MCP servers are available. Use the exact namespaced MCP tools when present, " +
                "or the mcp_list_*, mcp_call_tool, mcp_read_resource, and mcp_get_prompt tools."
        )
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
                append(binding.namespacedToolName)
                if (binding.description.isNotBlank()) {
                    append(": ")
                    append(binding.description)
                }
            }
            append("\n</mcp_tools>")
        }
    }
}

private fun expandDynamicPromptPlaceholders(
    prompt: String,
    now: ZonedDateTime = ZonedDateTime.now(),
): String {
    if (!prompt.contains("{{")) return prompt
    val values = mapOf(
        "current_datetime" to now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "current_date" to now.toLocalDate().toString(),
        "current_time" to now.toLocalTime().withNano(0).toString(),
        "timezone" to now.zone.id,
        "unix_timestamp" to now.toEpochSecond().toString(),
    )
    return DynamicPromptPlaceholderRegex.replace(prompt) { match ->
        values[match.groupValues[1].lowercase(Locale.US)] ?: match.value
    }
}

private fun renderAvailableSkillLines(
    skills: List<InstalledSkill>,
): Pair<List<String>, Int> {
    val lines = mutableListOf<String>()
    var usedChars = 0
    var omitted = 0
    skills.sortedBy { it.name.lowercase() }.forEach { skill ->
        val path = skill.skillMdPath.replace('\\', '/')
        val minimumLine = "- ${skill.name}: (file: $path)"
        if (usedChars + minimumLine.length + 1 > SkillMetadataContextBudgetChars) {
            omitted += 1
            return@forEach
        }
        val fullLine = "- ${skill.name}: ${skill.description} (file: $path)"
        val remaining = SkillMetadataContextBudgetChars - usedChars - 1
        val line = if (fullLine.length <= remaining) {
            fullLine
        } else {
            minimumLine
        }
        usedChars += line.length + 1
        lines += line
    }
    return lines to omitted
}
