package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderModelCatalogClientTest {
    @Test
    fun thinkingCatalogKeyIncludesProviderConfigAndUsesLastModelIdSegment() {
        assertEquals(
            "config-a:openrouter/gpt-5.6-sol",
            thinkingCatalogKey("config-a", "openrouter", "openai/gpt-5.6-sol"),
        )
        assertEquals(
            "config-b:openrouter/gpt-5.6-sol",
            thinkingCatalogKey("config-b", "openrouter", "openai/gpt-5.6-sol"),
        )
    }

    @Test
    fun thinkingLevelsUsePiCatalogResults() {
        assertEquals(emptyList<String>(), supportedThinkingLevels(JSONArray()))
        assertEquals(
            listOf("off", "low", "medium", "high", "xhigh", "max"),
            supportedThinkingLevels(
                JSONArray()
                    .put("off")
                    .put("low")
                    .put("medium")
                    .put("high")
                    .put("xhigh")
                    .put("max"),
            ),
        )
        assertEquals(
            listOf("minimal", "high", "xhigh"),
            supportedThinkingLevels(
                JSONArray()
                    .put("minimal")
                    .put("high")
                    .put("xhigh")
                    .put("high")
                    .put("unknown"),
            ),
        )
    }

    @Test
    fun thinkingLevelClampsUsePiCatalogResults() {
        assertEquals(
            mapOf("off" to "off", "high" to "high", "xhigh" to "max", "max" to "max"),
            piThinkingLevelClamps(
                JSONObject()
                    .put("off", "off")
                    .put("high", "high")
                    .put("xhigh", "max")
                    .put("max", "max")
                    .put("unknown", "high")
                    .put("low", "unknown"),
            ),
        )
    }

    @Test
    fun modelCapabilitiesUsePiReasoningLevelsAndClamps() {
        val capabilities = piModelCapabilities(
            JSONObject()
                .put("reasoning", true)
                .put("thinking_levels", JSONArray().put("low").put("high").put("max"))
                .put(
                    "thinking_level_clamps",
                    JSONObject().put("off", "low").put("medium", "high").put("xhigh", "max"),
                ),
        )

        assertEquals(true, capabilities.reasoning)
        assertEquals(listOf("low", "high", "max"), capabilities.thinkingLevels)
        assertEquals(
            mapOf("off" to "low", "medium" to "high", "xhigh" to "max"),
            capabilities.thinkingLevelClamps,
        )
    }

    @Test
    fun fetchModelsIncludesConfiguredUserAgentAndCustomHeaders() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"gpt-test"}]}""")
        )
        server.start()

        try {
            val result = ProviderModelCatalogClient.fetchModels(
                LlmProviderConfig(
                    providerId = "openai",
                    name = "OpenAI",
                    piProviderId = "openai-compatible",
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gpt-test",
                    userAgent = "CatalogClient/1.0",
                    customHeaders = listOf(
                        LlmCustomHeader("X-Aether-Test", "models"),
                        LlmCustomHeader("User-Agent", "ignored"),
                    ),
                )
            )

            assertEquals(listOf("gpt-test"), result.models)
            val request = server.takeRequest()
            assertEquals("CatalogClient/1.0", request.getHeader("User-Agent"))
            assertEquals("models", request.getHeader("X-Aether-Test"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun builtInProviderUsesPiCatalogInsteadOfAndroidCompatibilityPaths() = runBlocking {
        val result = ProviderModelCatalogClient.fetchModels(
            LlmProviderConfig(
                providerId = "vertex",
                name = "Vertex",
                piProviderId = "google-vertex",
                apiKey = "test-key",
                baseUrl = "",
                modelId = "gemini-2.5-flash",
            )
        )

        assertEquals(null, result.error)
        assertEquals(listOf("gemini-2.5-flash"), result.models)
    }

    @Test
    fun builtInOpenAiApiKeyUsesLiveModelsEndpoint() {
        val definition = PiProviderCatalog.resolve("openai")
        val config = LlmProviderConfig(
            providerId = "openai",
            name = "OpenAI",
            piProviderId = "openai",
            apiKey = "test-key",
            baseUrl = definition.defaultBaseUrl,
            modelId = "",
            authMethod = ProviderAuthMethod.ApiKey,
        )

        assertEquals(
            true,
            ProviderModelCatalogClient.shouldFetchModelsFromEndpoint(config, definition),
        )
    }

    @Test
    fun openAiCodexOAuthKeepsProviderCatalog() {
        val definition = PiProviderCatalog.resolve("openai-codex")
        val config = LlmProviderConfig(
            providerId = "openai-codex",
            name = "OpenAI Codex",
            piProviderId = "openai-codex",
            apiKey = "",
            baseUrl = definition.defaultBaseUrl,
            modelId = "",
            authMethod = ProviderAuthMethod.OAuth,
        )

        assertEquals(
            false,
            ProviderModelCatalogClient.shouldFetchModelsFromEndpoint(config, definition),
        )
    }

    @Test
    fun customOpenAiBaseUrlFetchesModelsFromConfiguredEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"third-party-model"}]}""")
        )
        server.start()

        try {
            val result = ProviderModelCatalogClient.fetchModels(
                LlmProviderConfig(
                    providerId = "custom-openai",
                    name = "Custom OpenAI",
                    piProviderId = "openai",
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "",
                )
            )

            assertEquals(null, result.error)
            assertEquals(listOf("third-party-model"), result.models)
            assertEquals("/v1/models", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failedLiveRequestDoesNotReturnBundledModels() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid key"}}""")
        )
        server.start()

        try {
            val result = ProviderModelCatalogClient.fetchModels(
                LlmProviderConfig(
                    providerId = "openai",
                    name = "OpenAI",
                    piProviderId = "openai",
                    apiKey = "invalid-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "",
                    authMethod = ProviderAuthMethod.ApiKey,
                )
            )

            assertEquals(emptyList<String>(), result.models)
            assertEquals("""{"error":{"message":"invalid key"}}""", result.error)
        } finally {
            server.shutdown()
        }
    }
}
