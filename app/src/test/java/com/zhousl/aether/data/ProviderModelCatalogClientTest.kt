package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderModelCatalogClientTest {
    @Test
    fun thinkingCatalogKeyUsesOnlyLastModelIdSegment() {
        assertEquals(
            "openrouter/gpt-5.6-sol",
            thinkingCatalogKey("openrouter", "openai/gpt-5.6-sol"),
        )
    }

    @Test
    fun openAiCompatibleKimiK3UsesModelsDevMoonshotCatalog() {
        val config = LlmProviderConfig(
            providerId = "modal",
            name = "Modal",
            piProviderId = "openai-compatible",
            apiKey = "test-key",
            baseUrl = "https://example.modal.run/v1",
            modelId = "kimi-k3",
            cachedModels = listOf("kimi-k3"),
            enabledModelIds = listOf("kimi-k3"),
        )
        val option = listOf(config).availableModelOptions().single()
        val catalog = JSONObject(
            """{"providers":{"moonshotai":{"models":{"kimi-k3":{"id":"kimi-k3","reasoning":true,"reasoning_options":[{"type":"toggle"},{"type":"effort","values":["low","high","max"]}]}}}}}""",
        )

        val result = publicCatalogThinkingResult(catalog, listOf(option))
        val key = thinkingCatalogKey("openai-compatible", "kimi-k3")

        assertEquals(
            listOf("off", "low", "high", "max"),
            result.levelsByProviderModel[key],
        )
        assertEquals(mapOf("off" to "none"), result.levelMapsByProviderModel[key])
    }

    @Test
    fun prefixedModalKimiK3UsesMoonshotToggleMetadata() {
        val config = LlmProviderConfig(
            providerId = "modal",
            name = "Modal",
            piProviderId = "openai-compatible",
            apiKey = "test-key",
            baseUrl = "https://example.modal.run/v1",
            modelId = "moonshotai/Kimi-K3",
            cachedModels = listOf("moonshotai/Kimi-K3"),
            enabledModelIds = listOf("moonshotai/Kimi-K3"),
        )
        val option = listOf(config).availableModelOptions().single()
        val catalog = JSONObject(
            """{"providers":{"modal":{"models":{"moonshotai/Kimi-K3":{"id":"moonshotai/Kimi-K3","reasoning":true,"reasoning_options":[]}}},"moonshotai":{"models":{"kimi-k3":{"id":"kimi-k3","reasoning":true,"reasoning_options":[{"type":"toggle"},{"type":"effort","values":["low","high","max"]}]}}}}}""",
        )
        val key = thinkingCatalogKey("openai-compatible", "moonshotai/Kimi-K3")

        val result = publicCatalogThinkingResult(catalog, listOf(option))

        assertEquals(listOf("off", "low", "high", "max"), result.levelsByProviderModel[key])
        assertEquals(mapOf("off" to "none"), result.levelMapsByProviderModel[key])
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
    fun builtInProviderModelsAreParsedFromModelsDev() {
        val result = modelsDevProviderModels(
            JSONObject(
                """{"providers":{"google-vertex":{"models":{"gemini-new":{"id":"gemini-new"},"fallback-key":{}}}}}""",
            ),
            listOf("google-vertex"),
        )

        assertEquals(null, result.error)
        assertEquals(listOf("gemini-new", "fallback-key"), result.models)
    }

    @Test
    fun modelsDevProviderAliasesMatchAetherBuiltIns() {
        assertEquals(
            listOf("fireworks-ai"),
            PiProviderCatalog.resolve("fireworks").modelsDevProviderIds(),
        )
        assertEquals(
            listOf("kimi-for-coding"),
            PiProviderCatalog.resolve("kimi-coding").modelsDevProviderIds(),
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
    fun requestyListsManagedModelsBeforeFullCatalog() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"claude-sonnet-4-5"}]}""")
        )
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"openai/gpt-4o-mini"},{"id":"claude-sonnet-4-5"}]}""")
        )
        server.start()

        try {
            val result = ProviderModelCatalogClient.fetchModels(
                LlmProviderConfig(
                    providerId = "requesty",
                    name = "Requesty",
                    piProviderId = "requesty",
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "",
                )
            )

            assertEquals(null, result.error)
            assertEquals(listOf("claude-sonnet-4-5", "openai/gpt-4o-mini"), result.models)
            assertEquals("/v1/models/managed", server.takeRequest().path)
            assertEquals("/v1/models", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failedProviderRequestFallsBackToModelsDev() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid key"}}""")
        )
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"providers":{"openai":{"models":{"gpt-fallback":{"id":"gpt-fallback"}}}}}""")
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
                ),
                modelsDevUrl = server.url("/catalog.json").toString(),
            )

            assertEquals(listOf("gpt-fallback"), result.models)
            assertEquals(null, result.error)
            assertEquals("/v1/models", server.takeRequest().path)
            assertEquals("/catalog.json", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun publicCatalogReturnsThinkingLevelMapWithOffMappedToNone() {
        val config = LlmProviderConfig(
            providerId = "openai-custom",
            name = "OpenAI",
            piProviderId = "openai-codex",
            apiKey = "test-key",
            baseUrl = "https://chatgpt.com/backend-api",
            modelId = "gpt-5.3-codex-spark",
            cachedModels = listOf("gpt-5.3-codex-spark"),
            enabledModelIds = listOf("gpt-5.3-codex-spark"),
            authMethod = ProviderAuthMethod.OAuth,
        )
        val option = listOf(config).availableModelOptions().single()
        val catalog = JSONObject(
            """{"providers":{"openai":{"models":{"gpt-5.3-codex-spark":{"id":"gpt-5.3-codex-spark","reasoning":true,"reasoning_options":[{"type":"effort","values":["none","low","medium","high","xhigh"]}]}}}}}""",
        )

        val result = publicCatalogThinkingResult(catalog, listOf(option))
        val key = thinkingCatalogKey("openai-codex", "gpt-5.3-codex-spark")

        assertEquals(listOf("off", "low", "medium", "high", "xhigh"), result.levelsByProviderModel[key])
        assertEquals(mapOf("off" to "none"), result.levelMapsByProviderModel[key])
    }

    @Test
    fun alwaysThinkingModelDoesNotInventReasoningEfforts() {
        val config = LlmProviderConfig(
            providerId = "moonshot-cn",
            name = "Moonshot CN",
            piProviderId = "moonshotai-cn",
            apiKey = "test-key",
            baseUrl = "https://api.moonshot.cn/v1",
            modelId = "kimi-k2.7-code",
            cachedModels = listOf("kimi-k2.7-code"),
            enabledModelIds = listOf("kimi-k2.7-code"),
        )
        val option = listOf(config).availableModelOptions().single()
        val catalog = JSONObject(
            """{"providers":{"moonshotai":{"models":{"kimi-k2.7-code":{"id":"kimi-k2.7-code","reasoning":true,"reasoning_options":[]}}}}}""",
        )
        val key = thinkingCatalogKey("moonshotai-cn", "kimi-k2.7-code")

        val result = publicCatalogThinkingResult(catalog, listOf(option))

        assertEquals(true, key in result.levelsByProviderModel)
        assertEquals(emptyList<String>(), result.levelsByProviderModel[key])
        assertEquals(emptyMap<String, Map<String, String>>(), result.levelMapsByProviderModel)
    }
}
