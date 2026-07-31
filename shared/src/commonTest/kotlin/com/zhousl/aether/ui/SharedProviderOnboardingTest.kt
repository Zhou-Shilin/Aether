package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedProviderOnboardingTest {
    @Test
    fun prioritizesAutomaticChatModelsLikeAndroidOnboarding() {
        assertEquals(
            listOf(
                "claude-fable-5",
                "gpt-5.6-sol",
                "gpt-5.6-terra",
            ),
            prioritizedSharedProviderModelOptions(
                piProviderId = "openai",
                cachedModels = listOf(
                    " gpt-5.6-terra ",
                    "gpt-5.6-sol",
                    "claude-fable-5",
                ),
            ),
        )
    }

    @Test
    fun trimsDeduplicatesAndUsesProviderModelOrdering() {
        assertEquals(
            listOf(
                "gpt-a",
                "gpt-z",
                "z-model",
            ),
            prioritizedSharedProviderModelOptions(
                piProviderId = "openai",
                cachedModels = listOf(
                    " z-model ",
                    "gpt-z",
                    "GPT-Z",
                    "gpt-a",
                    "",
                ),
            ),
        )
    }
}
