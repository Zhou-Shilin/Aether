package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmProviderConfigValidationTest {
    @Test
    fun builtInApiKeyProviderRequiresSupportedNonBlankKey() {
        assertFalse(provider(piProviderId = "openai", apiKey = "").isSharedProviderSetupValid())
        assertTrue(provider(piProviderId = "openai", apiKey = "secret").isSharedProviderSetupValid())
        assertFalse(
            provider(
                piProviderId = "openai-codex",
                authMethod = ProviderAuthMethod.ApiKey,
                apiKey = "secret",
            ).isSharedProviderSetupValid(),
        )
    }

    @Test
    fun oauthProviderRequiresSupportAndCredential() {
        assertFalse(
            provider(
                piProviderId = "openai-codex",
                authMethod = ProviderAuthMethod.OAuth,
            ).isSharedProviderSetupValid(),
        )
        assertTrue(
            provider(
                piProviderId = "openai-codex",
                authMethod = ProviderAuthMethod.OAuth,
                oauthCredentialJson = "{\"access\":\"token\"}",
            ).isSharedProviderSetupValid(),
        )
        assertFalse(
            provider(
                piProviderId = "openai",
                authMethod = ProviderAuthMethod.OAuth,
                oauthCredentialJson = "{\"access\":\"token\"}",
            ).isSharedProviderSetupValid(),
        )
    }

    @Test
    fun ambientProviderRequiresCatalogSupport() {
        assertTrue(
            provider(
                piProviderId = "google-vertex",
                authMethod = ProviderAuthMethod.Ambient,
                baseUrl = "",
            ).isSharedProviderSetupValid(),
        )
        assertFalse(
            provider(
                piProviderId = "openai",
                authMethod = ProviderAuthMethod.Ambient,
            ).isSharedProviderSetupValid(),
        )
    }

    @Test
    fun customProviderMatchesAndroidBaseUrlAndApiKeyRules() {
        assertFalse(
            provider(
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "",
            ).isSharedProviderSetupValid(),
        )
        assertTrue(
            provider(
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "https://models.example/v1",
            ).isSharedProviderSetupValid(),
        )
    }

    @Test
    fun requiredBuiltInBaseUrlIsValidated() {
        assertFalse(
            provider(
                piProviderId = "azure-openai-responses",
                apiKey = "secret",
                baseUrl = " ",
            ).isSharedProviderSetupValid(),
        )
        assertTrue(
            provider(
                piProviderId = "azure-openai-responses",
                apiKey = "secret",
                baseUrl = "https://example.openai.azure.com",
            ).isSharedProviderSetupValid(),
        )
    }
}

private fun provider(
    piProviderId: String,
    authMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    apiKey: String = "",
    baseUrl: String = PiProviderCatalog.resolve(piProviderId).defaultBaseUrl,
    oauthCredentialJson: String = "",
) = LlmProviderConfig(
    providerId = "provider",
    name = "Provider",
    piProviderId = piProviderId,
    apiKey = apiKey,
    baseUrl = baseUrl,
    authMethod = authMethod,
    oauthCredentialJson = oauthCredentialJson,
    modelId = "model",
)
