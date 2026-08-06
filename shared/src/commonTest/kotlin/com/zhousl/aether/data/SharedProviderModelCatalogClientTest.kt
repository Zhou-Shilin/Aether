package com.zhousl.aether.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SharedProviderModelCatalogClientTest {
    @Test
    fun parsesPiModelCapabilitiesIncludingClamps() {
        val capabilities = sharedPiModelCapabilities(
            Json.parseToJsonElement(
                """{"reasoning":true,"thinking_levels":["off","low","high","unknown"],"thinking_level_clamps":{"max":"high","invalid":"low"}}""",
            ) as JsonObject,
        )

        assertTrue(capabilities.reasoning)
        assertEquals(listOf("off", "low", "high"), capabilities.thinkingLevels)
        assertEquals(mapOf("max" to "high"), capabilities.thinkingLevelClamps)
    }

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
            fetchBuiltinCatalog = { error("Built-in catalog should not be used") },
        )

        assertEquals(listOf("model-a", "model-b"), result.models)
        assertNull(result.error)
    }

    @Test
    fun builtInProviderUsesPiCatalogWithoutCallingNetwork() = runTest {
        var networkCalled = false
        val engine = MockEngine {
            networkCalled = true
            respond("{}")
        }
        val catalog = Json.parseToJsonElement(
            """{"providers":[{"id":"anthropic","models":[{"id":"claude-a","reasoning":true,"thinking_levels":["off","low","high","unknown"],"thinking_level_clamps":{"max":"high","invalid":"low"}},{"id":"claude-b"}]}]}""",
        ) as JsonObject
        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "anthropic",
                baseUrl = "https://api.anthropic.com",
                authMethod = ProviderAuthMethod.OAuth,
            ),
            fetchBuiltinCatalog = { catalog },
        )

        assertEquals(listOf("claude-a", "claude-b"), result.models)
        assertEquals(listOf("off", "low", "high"), result.thinkingLevelsByModel["claude-a"])
        assertEquals(mapOf("max" to "high"), result.thinkingLevelClampsByModel["claude-a"])
        assertFalse(networkCalled)
    }

    @Test
    fun openAiApiKeyUsesRemoteEndpoint() {
        assertTrue(
            shouldFetchModelsFromEndpoint(
                customConfig(
                    piProviderId = "openai",
                    baseUrl = "https://api.openai.com/v1",
                    authMethod = ProviderAuthMethod.ApiKey,
                ),
            ),
        )
        assertEquals("https://example.com/v1/models", modelsEndpoint("https://example.com/v1/responses"))
        assertEquals("https://example.com/v1/models", modelsEndpoint("https://example.com/v1/chat/completions"))
    }

    @Test
    fun openAiEndpointModelsAreMergedWithPublicCatalog() = runTest {
        val engine = MockEngine { request ->
            when (request.url.host) {
                "api.openai.com" -> respond(
                    """{"data":[{"id":"gpt-endpoint"}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "models.dev" -> respond(
                    """{"providers":{"openai":{"models":{"gpt-catalog":{"id":"gpt-catalog"}}}}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val result = SharedProviderModelCatalogClient(engine).fetchModels(
            customConfig(
                piProviderId = "openai",
                baseUrl = "https://api.openai.com/v1",
                authMethod = ProviderAuthMethod.ApiKey,
            ),
            fetchBuiltinCatalog = { error("Pi catalog should not be needed") },
        )

        assertEquals(listOf("gpt-endpoint", "gpt-catalog"), result.models)
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
