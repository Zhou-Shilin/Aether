package com.zhousl.aether.ui

import com.zhousl.aether.data.AetherLlmUserAgent
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.PiProviderEnvironmentVariable
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.availableModels
import com.zhousl.aether.data.pi.PiProviderAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigFormTest {
    @Test
    fun catalogIncludesEveryPiBuiltInProviderAndCustomEndpoint() {
        assertEquals(35, PiProviderCatalog.builtInProviders.size)
        assertEquals(37, PiProviderCatalog.providers.size)

        val customProviders = PiProviderCatalog.providers.filterNot { it.isBuiltIn }
        assertEquals(listOf("openai-compatible", "requesty"), customProviders.map { it.id })
        assertEquals(
            listOf("openai", "openai-compatible"),
            PiProviderCatalog.providers.take(2).map { it.id },
        )
    }

    @Test
    fun requestyPrefillsItsOpenAiCompatibleEndpoint() {
        val state = ProviderFormState.fromConfig(null)

        state.applyProviderDefaults(PiProviderCatalog.resolve("requesty"))

        assertEquals("https://router.requesty.ai/v1", state.baseUrl)
        assertTrue(state.selectedDefinition.requiresBaseUrl)
        assertFalse(state.buildConfig().compatibilityMode)
    }

    @Test
    fun compatibilityModeIsScopedToOpenAiCompatibleProvider() {
        val state = ProviderFormState.fromConfig(null)
        state.applyProviderDefaults(PiProviderCatalog.resolve("openai-compatible"))
        state.compatibilityMode = true

        assertTrue(state.buildConfig().compatibilityMode)

        state.applyProviderDefaults(PiProviderCatalog.resolve("openai"))

        assertFalse(state.compatibilityMode)
        assertFalse(state.buildConfig().compatibilityMode)
    }

    @Test
    fun cloudProvidersUseAmbientAuthenticationByDefault() {
        listOf("amazon-bedrock", "google-vertex").forEach { providerId ->
            val state = ProviderFormState.fromConfig(null)
            val definition = PiProviderCatalog.resolve(providerId)

            assertTrue(definition.supportsApiKey)
            assertFalse(definition.supportsInteractiveApiKey)
            assertTrue(definition.supportsAmbientAuth)

            state.applyProviderDefaults(definition)
            assertEquals(ProviderAuthMethod.Ambient, state.authMethod)
            assertTrue(state.isAuthenticationConfigured())
        }
    }

    @Test
    fun cloudflareGatewayUsesPiCredentialFieldsWithoutRequiringBaseUrl() {
        val state = ProviderFormState.fromConfig(null)

        state.applyProviderDefaults(PiProviderCatalog.resolve("cloudflare-ai-gateway"))
        state.apiKey = "test-key"

        assertFalse(state.selectedDefinition.requiresBaseUrl)
        assertEquals("", state.baseUrl)
        assertTrue(state.isAuthenticationConfigured())
    }

    @Test
    fun ensureAvailableProviderIdUsesNextUnusedSuffix() {
        val state = ProviderFormState.fromConfig(null)

        state.applyProviderDefaults(PiProviderCatalog.resolve("openai"))
        state.ensureAvailableProviderId(setOf("openai", "openai_2", "openai_3"))

        assertEquals("openai_4", state.providerId)
    }

    @Test
    fun providerAuthResultAppliesApiKeyAndProviderEnvironment() {
        val state = ProviderFormState.fromConfig(null)
        val environment = listOf(
            PiProviderEnvironmentVariable("CLOUDFLARE_ACCOUNT_ID", "account"),
            PiProviderEnvironmentVariable("CLOUDFLARE_GATEWAY_ID", "gateway"),
        )

        state.applyProviderDefaults(PiProviderCatalog.resolve("cloudflare-ai-gateway"))
        applyProviderAuthResult(
            state = state,
            authState = PiProviderAuthState(
                providerId = "cloudflare-ai-gateway",
                authMethod = ProviderAuthMethod.ApiKey,
                apiKey = "test-key",
                providerEnvironmentVariables = environment,
            ),
        )

        assertEquals("test-key", state.apiKey)
        assertEquals(environment, state.providerEnvironmentVariables)
        assertTrue(state.isAuthenticationConfigured())
    }

    @Test
    fun parseManualModelIdsAcceptsMultipleSeparators() {
        assertEquals(
            listOf("manual-a", "manual-b", "manual-c", "manual-d"),
            parseManualModelIds("manual-a\nmanual-b, manual-c; manual-d"),
        )
    }

    @Test
    fun newProviderKeepsManualModelIdEmptyAndCanStillBeSaved() {
        val state = ProviderFormState.fromConfig(null)
        state.applyProviderDefaults(PiProviderCatalog.resolve("openai"))
        state.apiKey = "test-key"

        assertEquals("", state.modelId)
        assertEquals("", state.buildConfig().modelId)
        assertEquals(AetherLlmUserAgent, state.userAgent)
        assertTrue(state.isValid(emptySet()))
    }

    @Test
    fun buildConfigKeepsCustomUserAgentAndRestoresDefaultWhenBlank() {
        val state = ProviderFormState.fromConfig(null)

        state.userAgent = "  CustomAgent/4.0  "
        assertEquals("CustomAgent/4.0", state.buildConfig().userAgent)

        state.userAgent = ""
        assertEquals(AetherLlmUserAgent, state.buildConfig().userAgent)
    }

    @Test
    fun oauthAccountLabelUsesTheBestAvailableIdentity() {
        assertEquals(
            "person@example.com",
            oauthAccountLabel("""{"email":"person@example.com","accountId":"account-1"}"""),
        )
        assertEquals(
            "account-1",
            oauthAccountLabel("""{"accountId":"account-1"}"""),
        )
        assertEquals("", oauthAccountLabel("not-json"))
    }

    @Test
    fun buildConfigRemovesManualModelWhenDeletedFromInput() {
        val state = ProviderFormState.fromConfig(
            LlmProviderConfig(
                providerId = "custom",
                name = "Custom",
                piProviderId = "openai-compatible",
                apiKey = "test-key",
                baseUrl = "https://api.example.com/v1",
                modelId = "manual-a",
                manualModelIds = listOf("manual-a", "manual-b"),
                cachedModels = listOf("fetched-a"),
                enabledModelIds = listOf("manual-a", "manual-b", "fetched-a"),
            )
        )

        state.modelId = "manual-b"
        val config = state.buildConfig()

        assertEquals(listOf("manual-b"), config.manualModelIds)
        assertEquals(listOf("fetched-a"), config.cachedModels)
        assertEquals(listOf("fetched-a", "manual-b"), config.availableModels())
        assertEquals(listOf("manual-b", "fetched-a"), config.enabledModelIds)
    }

    @Test
    fun buildConfigKeepsModelEnabledChanges() {
        val state = ProviderFormState.fromConfig(
            LlmProviderConfig(
                providerId = "custom",
                name = "Custom",
                piProviderId = "openai-compatible",
                apiKey = "test-key",
                baseUrl = "https://api.example.com/v1",
                modelId = "manual-a",
                manualModelIds = listOf("manual-a", "manual-b"),
                enabledModelIds = listOf("manual-a", "manual-b"),
            )
        )

        state.setModelEnabled("manual-b", false)

        assertEquals(listOf("manual-a"), state.buildConfig().enabledModelIds)
    }
}
