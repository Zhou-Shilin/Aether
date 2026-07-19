package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiAgentPromptTest {
    @Test
    fun instructionsRequireFileUriLinksForLocalDownloads() {
        val instructions = buildPiAgentInstructions(
            settings = AppSettings(),
            workspaceDirectory = "/workspace",
            availableSkills = emptyList(),
            activeSkills = emptyList(),
            mcpSnapshots = emptyList(),
            mcpToolBindings = emptyList(),
            agentModeEnabled = false,
        )

        assertTrue(instructions.contains("[report.pdf](file:///absolute/path/report.pdf)"))
        assertTrue(instructions.contains("Do not use another URI scheme for local file downloads."))
    }

    @Test
    fun chromeInstructionsAreOnlyAddedWhenSelected() {
        val disabledInstructions = buildPiAgentInstructions(
            settings = AppSettings(),
            workspaceDirectory = "/workspace",
            availableSkills = emptyList(),
            activeSkills = emptyList(),
            mcpSnapshots = emptyList(),
            mcpToolBindings = emptyList(),
            agentModeEnabled = false,
        )
        val enabledInstructions = buildPiAgentInstructions(
            settings = AppSettings(),
            workspaceDirectory = "/workspace",
            availableSkills = emptyList(),
            activeSkills = emptyList(),
            mcpSnapshots = emptyList(),
            mcpToolBindings = emptyList(),
            agentModeEnabled = false,
            chromeEnabled = true,
        )

        assertFalse(disabledInstructions.contains("Alpine Chrome is enabled for this chat."))
        assertFalse(disabledInstructions.contains("normalized 0..1000 range"))
        assertTrue(enabledInstructions.contains("Alpine Chrome is enabled for this chat."))
        assertTrue(enabledInstructions.contains("normalized 0..1000 range"))
    }
}
