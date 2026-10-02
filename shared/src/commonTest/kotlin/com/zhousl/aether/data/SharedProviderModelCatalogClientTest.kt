package com.zhousl.aether.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SharedProviderModelCatalogClientTest {
    @Test
    fun customProviderFetchesConfiguredModelsEndpointWithHeaders() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://models.example/v1/models", request.url.toString())
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("Aether-Test", request.headers[HttpHeaders.UserAgent])
            assertEquals("tenant-1", request.headers["X-Tenant"])
            respond(
                content = """{"data":[{"id":"model-a"},{"id":"MODEL-A"},{"id":"model-b"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(),
        )

        assertEquals(listOf("model-a", "model-b"), result.models)
        assertNull(result.error)
    }

    @Test
    fun builtInProviderUsesConfiguredModelsEndpointFirst() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://api.anthropic.com/models", request.url.toString())
            respond(
                """{"data":[{"id":"claude-live"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "anthropic",
                baseUrl = "https://api.anthropic.com",
                authMethod = ProviderAuthMethod.OAuth,
            ),
        )

        assertEquals(listOf("claude-live"), result.models)
        assertNull(result.error)
    }

    @Test
    fun requestyListsManagedModelsBeforeFullCatalog() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            val body = when (request.url.encodedPath) {
                "/v1/models/managed" -> """{"data":[{"id":"claude-sonnet-4-5"}]}"""
                else -> """{"data":[{"id":"openai/gpt-4o-mini"},{"id":"claude-sonnet-4-5"}]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "requesty",
                baseUrl = "https://router.requesty.ai/v1",
            ),
        )

        assertEquals(
            listOf("https://router.requesty.ai/v1/models/managed", "https://router.requesty.ai/v1/models"),
            requestedUrls,
        )
        assertEquals(listOf("claude-sonnet-4-5", "openai/gpt-4o-mini"), result.models)
        assertNull(result.error)
    }

    @Test
    fun modelsDevProviderAliasesMatchAetherBuiltIns() {
        assertEquals(
            listOf("togetherai"),
            PiProviderCatalog.resolve("together").modelsDevProviderIds(),
        )
        assertEquals(
            listOf("kimi-for-coding"),
            PiProviderCatalog.resolve("kimi-coding").modelsDevProviderIds(),
        )
    }

    @Test
    fun modelEndpointFollowsConfiguredBaseUrl() {
        assertEquals("https://example.com/v1/models", modelsEndpoint("https://example.com/v1/responses"))
        assertEquals("https://example.com/v1/models", modelsEndpoint("https://example.com/v1/chat/completions"))
    }

    @Test
    fun successfulProviderRequestDoesNotFetchModelsDev() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals("api.openai.com", request.url.host)
            respond(
                """{"data":[{"id":"gpt-endpoint"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "openai",
                baseUrl = "https://api.openai.com/v1",
                authMethod = ProviderAuthMethod.ApiKey,
            ),
        )

        assertEquals(listOf("gpt-endpoint"), result.models)
        assertEquals(1, requestCount)
        assertNull(result.error)
    }

    @Test
    fun failedProviderRequestFallsBackToModelsDev() = runTest {
        val requestedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedHosts += request.url.host
            when (request.url.host) {
                "api.anthropic.com" -> respondError(HttpStatusCode.Unauthorized)
                "models.dev" -> respond(
                    """{"providers":{"anthropic":{"models":{"claude-fallback":{"id":"claude-fallback"}}}}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "anthropic",
                baseUrl = "https://api.anthropic.com",
                authMethod = ProviderAuthMethod.OAuth,
            ),
        )

        assertEquals(listOf("api.anthropic.com", "models.dev"), requestedHosts)
        assertEquals(listOf("claude-fallback"), result.models)
        assertNull(result.error)
    }

    @Test
    fun publicCatalogProvidesReasoningEffortFallback() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"openai":{"models":{"gpt-5":{"id":"gpt-5","reasoning":true,"reasoning_options":[{"type":"toggle"},{"type":"effort","values":["low","medium","high"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(
            piProviderId = "openai",
            baseUrl = "https://api.openai.com/v1",
            authMethod = ProviderAuthMethod.ApiKey,
        ).copy(modelId = "gpt-5", cachedModels = listOf("gpt-5"), enabledModelIds = listOf("gpt-5"))
        val option = listOf(config).availableModelOptions().single()

        val levels = SharedProviderModelCatalogClient(engine).fetchThinkingLevels(listOf(option))

        assertEquals(
            listOf("off", "low", "medium", "high"),
            levels[sharedThinkingCatalogKey("openai", "gpt-5")],
        )
    }

    @Test
    fun openAiCompatibleKimiK3UsesMoonshotPublicCatalog() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"moonshotai":{"models":{"kimi-k3":{"id":"kimi-k3","reasoning":true,"reasoning_options":[{"type":"toggle"},{"type":"effort","values":["low","high","max"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(piProviderId = "openai-compatible").copy(
            modelId = "kimi-k3",
            cachedModels = listOf("kimi-k3"),
            enabledModelIds = listOf("kimi-k3"),
        )
        val option = listOf(config).availableModelOptions().single()

        val catalog = SharedProviderModelCatalogClient(engine).fetchThinkingCatalog(listOf(option))
        val key = sharedThinkingCatalogKey("openai-compatible", "kimi-k3")

        assertEquals(
            listOf("off", "low", "high", "max"),
            catalog.levelsByProviderModel[key],
        )
        assertEquals(mapOf("off" to "none"), catalog.levelMapsByProviderModel[key])
    }

    @Test
    fun prefixedModalKimiK3UsesMoonshotToggleMetadata() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"modal":{"models":{"moonshotai/Kimi-K3":{"id":"moonshotai/Kimi-K3","reasoning":true,"reasoning_options":[]}}},"moonshotai":{"models":{"kimi-k3":{"id":"kimi-k3","reasoning":true,"reasoning_options":[{"type":"toggle"},{"type":"effort","values":["low","high","max"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(piProviderId = "openai-compatible").copy(
            modelId = "moonshotai/Kimi-K3",
            cachedModels = listOf("moonshotai/Kimi-K3"),
            enabledModelIds = listOf("moonshotai/Kimi-K3"),
        )
        val option = listOf(config).availableModelOptions().single()
        val key = sharedThinkingCatalogKey("openai-compatible", "moonshotai/Kimi-K3")

        val catalog = SharedProviderModelCatalogClient(engine).fetchThinkingCatalog(listOf(option))

        assertEquals(listOf("off", "low", "high", "max"), catalog.levelsByProviderModel[key])
        assertEquals(mapOf("off" to "none"), catalog.levelMapsByProviderModel[key])
    }

    @Test
    fun codexUsesOpenAiPublicCatalogAndNormalizesNoneToOff() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"openai":{"models":{"gpt-5.3-codex-spark":{"id":"gpt-5.3-codex-spark","reasoning":true,"reasoning_options":[{"type":"effort","values":["none","low","medium","high","xhigh"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(
            piProviderId = "openai-codex",
            baseUrl = "https://chatgpt.com/backend-api",
            authMethod = ProviderAuthMethod.OAuth,
        ).copy(
            modelId = "gpt-5.3-codex-spark",
            cachedModels = listOf("gpt-5.3-codex-spark"),
            enabledModelIds = listOf("gpt-5.3-codex-spark"),
        )
        val option = listOf(config).availableModelOptions().single()

        val levels = SharedProviderModelCatalogClient(engine).fetchThinkingLevels(listOf(option))

        assertEquals(
            listOf("off", "low", "medium", "high", "xhigh"),
            levels[sharedThinkingCatalogKey("openai-codex", "gpt-5.3-codex-spark")],
        )
    }

    @Test
    fun reasoningModelMatchesByUnprefixedIdAcrossProviderDirectories() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"openai":{"models":{"gpt-5.6-sol":{"id":"gpt-5.6-sol","reasoning":true,"reasoning_options":[{"type":"effort","values":["none","low","medium","high","xhigh","max"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig().copy(
            modelId = "openai/gpt-5.6-sol",
            cachedModels = listOf("openai/gpt-5.6-sol"),
            enabledModelIds = listOf("openai/gpt-5.6-sol"),
        )
        val option = listOf(config).availableModelOptions().single()

        val levels = SharedProviderModelCatalogClient(engine).fetchThinkingLevels(listOf(option))

        assertEquals(
            listOf("off", "low", "medium", "high", "xhigh", "max"),
            levels[sharedThinkingCatalogKey(config.piProviderId, "gpt-5.6-sol")],
        )
    }

    @Test
    fun nonReasoningModelIsCachedAsResolvedEmpty() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"anthropic":{"models":{"claude-haiku":{"id":"claude-haiku","reasoning":false}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig().copy(
            modelId = "claude-haiku",
            cachedModels = listOf("claude-haiku"),
            enabledModelIds = listOf("claude-haiku"),
        )
        val option = listOf(config).availableModelOptions().single()
        val cacheKey = sharedThinkingCatalogKey(config.piProviderId, option.modelId)

        val levels = SharedProviderModelCatalogClient(engine).fetchThinkingLevels(listOf(option))

        assertTrue(cacheKey in levels)
        assertEquals(emptyList(), levels[cacheKey])
    }

    @Test
    fun codexCatalogReturnsThinkingLevelMapWithOffMappedToNone() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"openai":{"models":{"gpt-5.3-codex-spark":{"id":"gpt-5.3-codex-spark","reasoning":true,"reasoning_options":[{"type":"effort","values":["none","low","medium","high","xhigh"]}]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(
            piProviderId = "openai-codex",
            baseUrl = "https://chatgpt.com/backend-api",
            authMethod = ProviderAuthMethod.OAuth,
        ).copy(
            modelId = "gpt-5.3-codex-spark",
            cachedModels = listOf("gpt-5.3-codex-spark"),
            enabledModelIds = listOf("gpt-5.3-codex-spark"),
        )
        val option = listOf(config).availableModelOptions().single()

        val catalog = SharedProviderModelCatalogClient(engine).fetchThinkingCatalog(listOf(option))
        val key = sharedThinkingCatalogKey("openai-codex", "gpt-5.3-codex-spark")

        assertEquals(listOf("off", "low", "medium", "high", "xhigh"), catalog.levelsByProviderModel[key])
        assertEquals(mapOf("off" to "none"), catalog.levelMapsByProviderModel[key])
    }

    @Test
    fun alwaysThinkingModelDoesNotInventReasoningEfforts() = runTest {
        val engine = MockEngine {
            respond(
                """{"providers":{"moonshotai":{"models":{"kimi-k2.7-code":{"id":"kimi-k2.7-code","reasoning":true,"reasoning_options":[]}}}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = customConfig(piProviderId = "moonshotai-cn").copy(
            modelId = "kimi-k2.7-code",
            cachedModels = listOf("kimi-k2.7-code"),
            enabledModelIds = listOf("kimi-k2.7-code"),
        )
        val option = listOf(config).availableModelOptions().single()
        val key = sharedThinkingCatalogKey("moonshotai-cn", "kimi-k2.7-code")

        val catalog = SharedProviderModelCatalogClient(engine).fetchThinkingCatalog(listOf(option))

        assertTrue(key in catalog.levelsByProviderModel)
        assertEquals(emptyList(), catalog.levelsByProviderModel[key])
        assertEquals(emptyMap(), catalog.levelMapsByProviderModel)
        assertEquals(setOf(key), catalog.reasoningModels)
    }
}

private fun customConfig(
    piProviderId: String = DefaultPiProviderId,
    baseUrl: String = "https://models.example/v1",
    authMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
) = LlmProviderConfig(
    providerId = "test-provider",
    name = "Test",
    piProviderId = piProviderId,
    apiKey = "secret",
    baseUrl = baseUrl,
    authMethod = authMethod,
    modelId = "model-a",
    userAgent = "Aether-Test",
    customHeaders = listOf(LlmCustomHeader("X-Tenant", "tenant-1")),
)
