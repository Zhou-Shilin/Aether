package com.zhousl.aether.ui

import com.zhousl.aether.data.SharedActiveSkillContext
import com.zhousl.aether.data.SharedInstalledSkill
import com.zhousl.aether.data.platformDynamicPromptValues
import com.zhousl.aether.data.renderSharedActiveSkillPrompt

private const val SharedSkillMetadataContextBudgetChars = 8_000

internal fun buildSharedPiAgentInstructions(
    configuredPrompt: String,
    workspaceDirectory: String,
    availableSkills: List<SharedInstalledSkill>,
    activeSkills: List<SharedActiveSkillContext>,
): String = buildString {
    expandSharedDynamicPromptPlaceholders(configuredPrompt).trim().takeIf(String::isNotBlank)?.let {
        append(it)
        append("\n\n")
    }
    append(
        "You are running inside Aether on iOS with the Pi agent kernel. " +
            "Use available tools instead of guessing about local state. " +
            "The default workspace for this chat is $workspaceDirectory. " +
            "User-uploaded files are under attachments/. Use read or analyze_image when inspection is needed. " +
            "When linking a local file for the user to preview or share, use a Markdown link with a file:// target and the absolute path, " +
            "for example [report.pdf](file:///absolute/path/report.pdf). Do not use another URI scheme for local file links. " +
            "Use fetch_web_url for a specific page and tavily_search for public-web discovery or current information. " +
            "Prefer read, edit, write, grep, find, and ls for filesystem work, and bash for shell commands. " +
            "Independent parallel-safe tools may run together; tools marked sequential must run in order. " +
            "Only claim commands or actions that were actually performed. After using tools, summarize the result clearly."
    )
    if (availableSkills.isNotEmpty()) {
        val (skillLines, omittedCount) = renderSharedAvailableSkillLines(availableSkills)
        append("\n\n")
        append(
            "Installed Agent Skills are available. Aether may auto-activate matching skills for the current turn. " +
                "If the user explicitly names a skill or the task clearly matches one, you must use that skill's instructions. " +
                "Use activate_skill when you need an additional installed skill that is not already active, and do not claim a skill is active until the tool succeeds."
        )
        append("\n<available_skills>")
        skillLines.forEach { append("\n$it") }
        if (omittedCount > 0) {
            append("\n- $omittedCount additional skills omitted because of the context budget.")
        }
        append("\n</available_skills>")
    }
    if (activeSkills.isNotEmpty()) {
        append("\n\n")
        append(renderSharedActiveSkillPrompt(activeSkills))
    }
}

private val SharedDynamicPromptPlaceholderRegex = Regex("""\{\{\s*([A-Za-z0-9_-]+)\s*\}\}""")

internal fun expandSharedDynamicPromptPlaceholders(
    prompt: String,
    values: Map<String, String> = platformDynamicPromptValues(),
): String {
    if (!prompt.contains("{{")) return prompt
    return SharedDynamicPromptPlaceholderRegex.replace(prompt) { match ->
        values[match.groupValues[1].lowercase()] ?: match.value
    }
}

private fun renderSharedAvailableSkillLines(
    skills: List<SharedInstalledSkill>,
): Pair<List<String>, Int> {
    val lines = mutableListOf<String>()
    var usedCharacters = 0
    var omittedCount = 0
    skills.sortedBy { it.name.lowercase() }.forEach { skill ->
        val path = "${skill.guestPath.trimEnd('/')}/SKILL.md"
        val minimumLine = "- ${skill.name}: (file: $path)"
        if (usedCharacters + minimumLine.length + 1 > SharedSkillMetadataContextBudgetChars) {
            omittedCount += 1
            return@forEach
        }
        val fullLine = "- ${skill.name}: ${skill.description} (file: $path)"
        val remaining = SharedSkillMetadataContextBudgetChars - usedCharacters - 1
        val line = if (fullLine.length <= remaining) fullLine else minimumLine
        usedCharacters += line.length + 1
        lines += line
    }
    return lines to omittedCount
}
