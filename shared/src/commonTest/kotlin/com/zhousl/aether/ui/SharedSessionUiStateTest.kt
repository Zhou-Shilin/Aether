package com.zhousl.aether.ui

import com.zhousl.aether.data.SharedActiveSkillContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedSessionUiStateTest {
    @Test
    fun clearingComposerDraftMatchesAndroidSessionNavigation() {
        val state = SharedSessionUiState(id = "session-1")
        state.input = "unfinished draft"
        state.editingMessageId = "message-1"

        state.clearComposerDraft()

        assertEquals("", state.input)
        assertEquals("", state.editingMessageId)
    }

    @Test
    fun disabledAndRemovedSkillsArePrunedFromEverySession() {
        val state = SharedSessionUiState(
            id = "session-1",
            selectedSkillIds = listOf("enabled", "disabled", "removed"),
            activeSkills = listOf(
                SharedActiveSkillContext(
                    skillId = "enabled",
                    name = "Enabled",
                    description = "Enabled",
                    skillRootPath = "/skills/enabled",
                    bodyMarkdown = "Enabled",
                ),
                SharedActiveSkillContext(
                    skillId = "removed",
                    name = "Removed",
                    description = "Removed",
                    skillRootPath = "/skills/removed",
                    bodyMarkdown = "Removed",
                ),
            ),
        )

        assertTrue(state.retainEnabledSkillSelections(setOf("enabled")))
        assertEquals(listOf("enabled"), state.selectedSkillIds.toList())
        assertEquals(listOf("enabled"), state.activeSkills.map { it.skillId })
        assertFalse(state.retainEnabledSkillSelections(setOf("enabled")))
    }

    @Test
    fun disabledAndRemovedMcpServersArePrunedFromEverySession() {
        val state = SharedSessionUiState(
            id = "session-1",
            activeMcpServerIds = listOf("enabled", "disabled", "removed"),
        )

        assertTrue(state.retainEnabledMcpSelections(setOf("enabled")))
        assertEquals(listOf("enabled"), state.activeMcpServerIds.toList())
        assertFalse(state.retainEnabledMcpSelections(setOf("enabled")))
    }
}
