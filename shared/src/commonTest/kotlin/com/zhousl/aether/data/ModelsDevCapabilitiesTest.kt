package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsDevCapabilitiesTest {
    // Aggregators come first in models.dev's provider order; the lab's own entry is last.
    private val catalog = """
        {
          "models": {"meta/muse-spark-1.3": {"id": "meta/muse-spark-1.3", "name": "Muse Spark 1.3", "reasoning": true}},
          "providers": {
            "nano-gpt": {"models": {"meta/muse-spark-1.3": {"id": "meta/muse-spark-1.3",
              "canonical_model_id": "meta/muse-spark-1.3", "reasoning": true,
              "reasoning_options": [{"type": "effort", "values": ["minimal", "low", "medium", "high", "xhigh"]}]}}},
            "opencode": {"models": {"muse-spark-1.3": {"id": "muse-spark-1.3",
              "canonical_model_id": "meta/muse-spark-1.3", "reasoning": true,
              "reasoning_options": [{"type": "effort", "values": ["minimal", "low", "medium", "high", "xhigh"]}]}}},
            "meta": {"models": {"muse-spark-1.3": {"id": "muse-spark-1.3",
              "canonical_model_id": "meta/muse-spark-1.3", "reasoning": true,
              "reasoning_options": [{"type": "effort", "values": ["minimal", "low", "medium", "high", "xhigh", "max"]}],
              "limit": {"context": 1048576, "output": 131072},
              "modalities": {"input": ["text", "image", "video"]}}}},
            "anthropic": {"models": {"claude-legacy": {"id": "claude-legacy", "reasoning": true,
              "reasoning_options": [{"type": "toggle"}, {"type": "budget_tokens", "min": 1024}]}}}
          }
        }
    """.trimIndent()

    private fun option(piProviderId: String, modelId: String, baseUrl: String = "https://relay.example/v1") =
        listOf(
            LlmProviderConfig(
                providerId = "relay",
                name = "Relay",
                piProviderId = piProviderId,
                apiKey = "key",
                baseUrl = baseUrl,
                modelId = modelId,
                cachedModels = listOf(modelId),
                enabledModelIds = listOf(modelId),
            ),
        ).availableModelOptions().single()

    @Test
    fun customEndpointResolvesMuseSparkMaxFromTheLabEntry() {
        val result = resolveModelsDevCapabilities(catalog, listOf(option("openai-compatible", "muse-spark-1.3")))
        val key = modelsDevCatalogKey("openai-compatible", "muse-spark-1.3")

        assertEquals(listOf("minimal", "low", "medium", "high", "xhigh", "max"), result.levelsByProviderModel[key])
        assertEquals(ModelsDevModelLimits(1_048_576, 131_072, true), result.limitsByProviderModel[key])
        assertEquals(setOf(key), result.reasoningModels)
    }

    @Test
    fun builtInProviderKeepsItsOwnSupportedLevels() {
        val result = resolveModelsDevCapabilities(catalog, listOf(option("opencode", "muse-spark-1.3", "")))
        val key = modelsDevCatalogKey("opencode", "muse-spark-1.3")

        assertEquals(listOf("minimal", "low", "medium", "high", "xhigh"), result.levelsByProviderModel[key])
    }

    @Test
    fun toggleOnlyModelsExposeOffWithoutInventingEfforts() {
        val result = resolveModelsDevCapabilities(catalog, listOf(option("anthropic", "claude-legacy", "")))
        val key = modelsDevCatalogKey("anthropic", "claude-legacy")

        assertEquals(listOf("off"), result.levelsByProviderModel[key])
        assertEquals(mapOf("off" to "none"), result.levelMapsByProviderModel[key])
    }

    @Test
    fun unknownModelsStayUnresolved() {
        val result = resolveModelsDevCapabilities(catalog, listOf(option("openai-compatible", "unknown-model")))

        assertNull(result.levelsByProviderModel[modelsDevCatalogKey("openai-compatible", "unknown-model")])
    }
}
