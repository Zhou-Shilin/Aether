package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPiAgentInstructionsTest {
    @Test
    fun dynamicPromptPlaceholdersMatchAndroidRules() {
        val values = mapOf(
            "current_date" to "2026-07-26",
            "timezone" to "Asia/Tokyo",
        )

        assertEquals(
            "Date 2026-07-26 in Asia/Tokyo; keep {{unknown}}.",
            expandSharedDynamicPromptPlaceholders(
                "Date {{ current_date }} in {{TIMEZONE}}; keep {{unknown}}.",
                values,
            ),
        )
    }
}
