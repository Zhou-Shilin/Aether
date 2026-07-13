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
    fun fetchModelsIncludesSelectedAetherUserAgentAndCustomHeaders() = runBlocking {
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
                    customHeaders = listOf(LlmCustomHeader("X-Aether-Test", "models")),
                    userAgentMode = ProviderUserAgentMode.Aether,
                )
            )

            assertEquals(listOf("gpt-test"), result.models)
            val request = server.takeRequest()
            assertEquals(AetherLlmUserAgent, request.getHeader("User-Agent"))
            assertEquals("models", request.getHeader("X-Aether-Test"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchModelsIncludesCustomUserAgent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"gpt-test"}]}"""),
        )
        server.start()

        try {
            ProviderModelCatalogClient.fetchModels(
                LlmProviderConfig(
                    providerId = "custom",
                    name = "Custom",
                    piProviderId = "openai-compatible",
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gpt-test",
                    userAgentMode = ProviderUserAgentMode.Custom,
                    customUserAgent = "ExampleClient/1.0",
                ),
            )

            assertEquals("ExampleClient/1.0", server.takeRequest().getHeader("User-Agent"))
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
}
