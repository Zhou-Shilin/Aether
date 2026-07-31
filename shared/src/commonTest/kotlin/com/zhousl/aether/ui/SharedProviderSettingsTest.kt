package com.zhousl.aether.ui

import com.zhousl.aether.data.LlmProviderConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SharedProviderSettingsTest {
    @Test
    fun activeProviderKeepsEnabledPreferredConfig() {
        val providers = listOf(provider("first"), provider("preferred"))

        assertEquals(
            "preferred",
            resolveSharedActiveProviderConfigId(providers, "preferred"),
        )
    }

    @Test
    fun disabledActiveProviderFallsBackToFirstEnabledConfig() {
        val providers = listOf(
            provider("disabled", enabled = false),
            provider("enabled"),
        )

        assertEquals(
            "enabled",
            resolveSharedActiveProviderConfigId(providers, "disabled"),
        )
    }

    @Test
    fun providerListWithoutEnabledConfigsRetainsAStableFallback() {
        val providers = listOf(
            provider("first", enabled = false),
            provider("second", enabled = false),
        )

        assertEquals(
            "first",
            resolveSharedActiveProviderConfigId(providers, "second"),
        )
        assertEquals("", resolveSharedActiveProviderConfigId(emptyList(), "missing"))
    }

    @Test
    fun editingProviderCannotReuseAnotherFullProviderId() {
        val state = ProviderFormState.fromConfig(provider("first"))
        state.setProviderIdFromUser("provider_second")

        assertFalse(
            state.isValid(setOf("provider_first", "provider_second")),
        )
    }
}

private fun provider(id: String, enabled: Boolean = true) = LlmProviderConfig(
    id = id,
    providerId = "provider_$id",
    name = id,
    piProviderId = "openai",
    apiKey = "key",
    baseUrl = "https://example.com/v1",
    modelId = "model",
    manualModelIds = listOf("model"),
    enabledModelIds = listOf("model"),
    isEnabled = enabled,
)
