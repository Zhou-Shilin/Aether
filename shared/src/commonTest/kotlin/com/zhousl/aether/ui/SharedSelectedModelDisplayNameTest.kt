package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedSelectedModelDisplayNameTest {
    @Test
    fun formatsProviderPathsAndModelVariantsLikeAndroid() {
        assertEquals(
            SharedSelectedModelDisplayName("Claude", "3.7 Sonnet", null),
            formatSharedSelectedModelDisplayName("anthropic/claude-3.7-sonnet"),
        )
        assertEquals(
            SharedSelectedModelDisplayName("GPT", "5", SharedSelectedModelDisplayIcon.Reasoning),
            formatSharedSelectedModelDisplayName("openai/gpt-5-reasoning-preview"),
        )
        assertEquals(
            SharedSelectedModelDisplayName("Gemini", "2.5 Pro", SharedSelectedModelDisplayIcon.Fast),
            formatSharedSelectedModelDisplayName("gemini-2.5-pro-fast"),
        )
    }

    @Test
    fun preservesUnknownAndEmptyNames() {
        val unknown = formatSharedSelectedModelDisplayName("custom_model")
        assertEquals("Custom", unknown.primary)
        assertEquals("Model", unknown.secondary)
        assertNull(unknown.icon)

        assertEquals(
            SharedSelectedModelDisplayName("", "", null),
            formatSharedSelectedModelDisplayName(""),
        )
    }
}
