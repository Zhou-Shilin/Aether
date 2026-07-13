package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigSerializationTest {
    @Test
    fun fullProviderSerializationPreservesCredentials() {
        val config = LlmProviderConfig(
            providerId = "openai",
            name = "OpenAI",
            piProviderId = "openai",
            apiKey = "secret-key",
            oauthCredentialJson = "{\"refresh_token\":\"secret\"}",
            providerEnvironmentVariables = listOf(
                PiProviderEnvironmentVariable(name = "TOKEN", value = "secret-token")
            ),
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.4",
        )

        val serialized = config.toJson()
        assertEquals("secret-key", serialized.getString("apiKey"))
        assertEquals(
            "{\"refresh_token\":\"secret\"}",
            serialized.getString("oauthCredentialJson"),
        )
        assertEquals(
            "secret-token",
            serialized
                .getJSONArray("providerEnvironmentVariables")
                .getJSONObject(0)
                .getString("value"),
        )
    }

    @Test
    fun importedProviderConfigBackfillsMissingNameAndBaseUrl() {
        val configs = parseProviderConfigs(
            JSONArray().put(
                JSONObject()
                    .put("providerType", "anthropic_messages")
                    .put("name", "")
                    .put("baseUrl", "")
                    .put("modelId", "claude-test")
            ).toString()
        )

        val config = configs.single()
        assertEquals("anthropic", config.piProviderId)
        assertEquals("Anthropic", config.name)
        assertEquals("https://api.anthropic.com", config.baseUrl)

        val option = configs.availableModelOptions().single()
        assertEquals("https://api.anthropic.com", option.baseUrl)
        assertEquals("claude-test", option.modelId)
    }

    @Test
    fun importedCloudProviderConfigsDefaultToAmbientAuthentication() {
        listOf("google-vertex", "amazon-bedrock").forEach { providerId ->
            val config = parseProviderConfigs(
                JSONArray().put(
                    JSONObject()
                        .put("piProviderId", providerId)
                        .put("modelId", "test-model")
                ).toString()
            ).single()

            assertEquals(ProviderAuthMethod.Ambient, config.authMethod)
        }
    }

    @Test
    fun piNativeSerializationDropsLegacyProviderFields() {
        val serialized = serializeProviderConfigs(
            listOf(
                LlmProviderConfig(
                    providerId = "anthropic",
                    name = "Anthropic",
                    piProviderId = "anthropic",
                    apiKey = "test-key",
                    baseUrl = "https://api.anthropic.com",
                    modelId = "claude-sonnet-4-5",
                )
            )
        )

        val json = JSONArray(serialized).getJSONObject(0)
        assertEquals("anthropic", json.getString("piProviderId"))
        assertTrue(!json.has("providerType"))
        assertTrue(!json.has("basicFunctionCallingCompatibilityMode"))
    }

    @Test
    fun availableModelOptionsSkipsConfigsWithBlankBaseUrl() {
        val options = listOf(
            LlmProviderConfig(
                id = "bad-provider",
                providerId = "bad",
                name = "Bad",
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "",
                modelId = "model-a",
            )
        ).availableModelOptions()

        assertTrue(options.isEmpty())
    }

    @Test
    fun availableModelOptionsSkipsDisabledProvidersAndModels() {
        val options = listOf(
            LlmProviderConfig(
                id = "enabled-provider",
                providerId = "enabled",
                name = "Enabled",
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "https://enabled.example/v1",
                modelId = "enabled-model",
                cachedModels = listOf("enabled-model", "disabled-model"),
                enabledModelIds = listOf("enabled-model"),
                isEnabled = true,
            ),
            LlmProviderConfig(
                id = "disabled-provider",
                providerId = "disabled",
                name = "Disabled",
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "https://disabled.example/v1",
                modelId = "disabled-provider-model",
                cachedModels = listOf("disabled-provider-model"),
                enabledModelIds = listOf("disabled-provider-model"),
                isEnabled = false,
            ),
        ).availableModelOptions()

        assertEquals(listOf("enabled/enabled-model"), options.map { it.fullLabel })
    }

    @Test
    fun availableModelOptionsGroupsMatchingModelProviderPrefixes() {
        val options = listOf(
            LlmProviderConfig(
                id = "first-site",
                providerId = "site_a",
                name = "Site A",
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "https://site-a.example/v1",
                modelId = "google/gemini-pro",
                cachedModels = listOf("google/gemini-pro", "openai/gpt-5"),
                enabledModelIds = listOf("google/gemini-pro", "openai/gpt-5"),
            ),
            LlmProviderConfig(
                id = "second-site",
                providerId = "site_b",
                name = "Site B",
                piProviderId = "openai-compatible",
                apiKey = "",
                baseUrl = "https://site-b.example/v1",
                modelId = "openai/gpt-4o",
                cachedModels = listOf("anthropic/claude-sonnet", "openai/gpt-4o"),
                enabledModelIds = listOf("anthropic/claude-sonnet", "openai/gpt-4o"),
            ),
        ).availableModelOptions()

        assertEquals(
            listOf(
                "site_b/anthropic/claude-sonnet",
                "site_a/google/gemini-pro",
                "site_a/openai/gpt-5",
                "site_b/openai/gpt-4o",
            ),
            options.map { it.fullLabel },
        )
    }

    @Test
    fun providerConfigRoundTripsCustomHeaders() {
        val serialized = serializeProviderConfigs(
            listOf(
                LlmProviderConfig(
                    providerId = "openrouter",
                    name = "OpenRouter",
                    piProviderId = "openrouter",
                    apiKey = "test-key",
                    baseUrl = "https://openrouter.ai/api/v1",
                    modelId = "openai/gpt-test",
                    customHeaders = listOf(
                        LlmCustomHeader("HTTP-Referer", "https://example.com"),
                        LlmCustomHeader("X-Title", "Aether"),
                    ),
                )
            )
        )

        val config = parseProviderConfigs(serialized).single()

        assertEquals("HTTP-Referer", config.customHeaders[0].name)
        assertEquals("https://example.com", config.customHeaders[0].value)
        assertEquals("X-Title", config.customHeaders[1].name)
        assertEquals("Aether", config.customHeaders[1].value)
        assertEquals(config.customHeaders, listOf(config).availableModelOptions().single().customHeaders)
    }

    @Test
    fun providerConfigRoundTripsUserAgentModeAndCustomValue() {
        val config = parseProviderConfigs(
            serializeProviderConfigs(
                listOf(
                    LlmProviderConfig(
                        providerId = "openrouter",
                        name = "OpenRouter",
                        piProviderId = "openrouter",
                        apiKey = "test-key",
                        baseUrl = "https://openrouter.ai/api/v1",
                        modelId = "openai/gpt-test",
                        userAgentMode = ProviderUserAgentMode.Custom,
                        customUserAgent = "ExampleClient/1.0",
                    )
                )
            )
        ).single()

        assertEquals(ProviderUserAgentMode.Custom, config.userAgentMode)
        assertEquals("ExampleClient/1.0", config.customUserAgent)
    }

    @Test
    fun legacyUserAgentHeaderMigratesToTheDedicatedCustomSetting() {
        val config = parseProviderConfigs(
            """[{"id":"legacy","providerId":"openai","name":"OpenAI","piProviderId":"openai","baseUrl":"https://example.invalid/v1","modelId":"test-model","customHeaders":[{"name":"User-Agent","value":"LegacyClient/1.0"},{"name":"X-Test","value":"yes"}]}]""",
        ).single()

        assertEquals(ProviderUserAgentMode.Custom, config.userAgentMode)
        assertEquals("LegacyClient/1.0", config.customUserAgent)
        assertEquals(listOf(LlmCustomHeader("X-Test", "yes")), config.customHeaders)
    }


    @Test
    fun explicitUserAgentModeTakesPrecedenceOverLegacyHeader() {
        val config = parseProviderConfigs(
            """[{"id":"legacy","providerId":"openai","name":"OpenAI","piProviderId":"openai","baseUrl":"https://example.test/v1","modelId":"test-model","userAgentMode":"default","customHeaders":[{"name":"User-Agent","value":"LegacyClient/1.0"},{"name":"X-Test","value":"yes"}]}]""",
        ).single()

        assertEquals(ProviderUserAgentMode.Default, config.userAgentMode)
        assertNull(config.customUserAgent)
        assertEquals(listOf(LlmCustomHeader("X-Test", "yes")), config.customHeaders)
    }

    @Test
    fun blankCustomUserAgentNormalizesToNoCustomValue() {
        val config = parseProviderConfigs(
            """[{"id":"legacy","providerId":"anthropic","name":"Anthropic","piProviderId":"anthropic","baseUrl":"https://api.anthropic.com","modelId":"claude-test","userAgentMode":"custom","customUserAgent":"   "}]""",
        ).single()

        assertEquals(ProviderUserAgentMode.Default, config.userAgentMode)
        assertNull(config.customUserAgent)
        assertTrue(!config.toJson().has("customUserAgent"))
    }

    @Test
    fun legacyNonOfficialOpenAiProviderDefaultsToAetherUserAgent() {
        val config = parseProviderConfigs(
            """[{"id":"legacy","providerId":"openai","name":"OpenAI","piProviderId":"openai","baseUrl":"https://example.test/v1","modelId":"gpt-5.5"}]""",
        ).single()

        assertEquals(ProviderUserAgentMode.Aether, config.userAgentMode)
    }

    @Test
    fun legacyOtherProviderDefaultsToRequestClientUserAgent() {
        val config = parseProviderConfigs(
            """[{"id":"legacy","providerId":"anthropic","name":"Anthropic","piProviderId":"anthropic","baseUrl":"https://api.anthropic.com/v1","modelId":"claude-test"}]""",
        ).single()

        assertEquals(ProviderUserAgentMode.Default, config.userAgentMode)
    }

    @Test
    fun selectingProviderModelPreservesItsUserAgentSetting() {
        val provider = LlmProviderConfig(
            providerId = "openrouter",
            name = "OpenRouter",
            piProviderId = "openrouter",
            apiKey = "test-key",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "openai/gpt-test",
            userAgentMode = ProviderUserAgentMode.Custom,
            customUserAgent = "ExampleClient/1.0",
        )

        val settings = AppSettings().withModelOption(provider.availableModelOptions().single())

        assertEquals(ProviderUserAgentMode.Custom, settings.providerUserAgentMode)
        assertEquals("ExampleClient/1.0", settings.customProviderUserAgent)
    }

    @Test
    fun providerConfigRoundTripsManualModelIdsSeparatelyFromCachedModels() {
        val serialized = serializeProviderConfigs(
            listOf(
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
        )

        val config = parseProviderConfigs(serialized).single()

        assertEquals(listOf("manual-a", "manual-b"), config.manualModelIds)
        assertEquals(listOf("fetched-a"), config.cachedModels)
        assertEquals(listOf("fetched-a", "manual-a", "manual-b"), config.availableModels())
    }

    @Test
    fun providerConfigRoundTripsAnExplicitlyEmptyModelId() {
        val serialized = serializeProviderConfigs(
            listOf(
                LlmProviderConfig(
                    providerId = "openai",
                    name = "OpenAI",
                    piProviderId = "openai",
                    apiKey = "test-key",
                    baseUrl = "https://api.openai.com/v1",
                    modelId = "",
                    manualModelIds = emptyList(),
                    enabledModelIds = emptyList(),
                )
            )
        )

        val config = parseProviderConfigs(serialized).single()

        assertEquals("", config.modelId)
        assertEquals(emptyList<String>(), config.manualModelIds)
        assertEquals(emptyList<String>(), config.enabledModelIds)
    }

    @Test
    fun providerConfigDropsEnabledManualModelWhenManualModelIdIsRemoved() {
        val config = LlmProviderConfig(
            providerId = "custom",
            name = "Custom",
            piProviderId = "openai-compatible",
            apiKey = "test-key",
            baseUrl = "https://api.example.com/v1",
            modelId = "fetched-a",
            manualModelIds = emptyList(),
            cachedModels = listOf("fetched-a"),
            enabledModelIds = listOf("manual-a", "fetched-a"),
        )

        assertEquals(listOf("fetched-a"), config.availableModels())
        assertEquals(listOf("fetched-a"), config.enabledModels())
    }
}