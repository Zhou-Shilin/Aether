package com.zhousl.aether.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun thinkingCatalogKey(providerId: String, modelId: String): String =
    modelsDevCatalogKey(providerId, modelId)

data class PublicCatalogThinkingResult(
    val levelsByProviderModel: Map<String, List<String>> = emptyMap(),
    val levelMapsByProviderModel: Map<String, Map<String, String>> = emptyMap(),
    val reasoningModels: Set<String> = emptySet(),
    val limitsByProviderModel: Map<String, ModelsDevModelLimits> = emptyMap(),
)

internal fun publicCatalogThinkingResult(
    catalogJson: String,
    options: List<ProviderModelOption>,
): PublicCatalogThinkingResult {
    val resolved = resolveModelsDevCapabilities(catalogJson, options)
    return PublicCatalogThinkingResult(
        levelsByProviderModel = resolved.levelsByProviderModel,
        levelMapsByProviderModel = resolved.levelMapsByProviderModel,
        reasoningModels = resolved.reasoningModels,
        limitsByProviderModel = resolved.limitsByProviderModel,
    )
}

internal fun publicCatalogThinkingResult(
    catalog: JSONObject,
    options: List<ProviderModelOption>,
): PublicCatalogThinkingResult = publicCatalogThinkingResult(catalog.toString(), options)

internal fun publicCatalogThinkingLevels(
    catalog: JSONObject,
    options: List<ProviderModelOption>,
): Map<String, List<String>> = publicCatalogThinkingResult(catalog, options).levelsByProviderModel

object ProviderModelCatalogClient {

    private const val ModelsDevUrl = "https://models.dev/catalog.json"

    data class FetchModelsResult(
        val models: List<String>,
        val error: String? = null,
    )

    suspend fun fetchModels(
        config: LlmProviderConfig,
    ): FetchModelsResult = fetchModels(config, ModelsDevUrl)

    internal suspend fun fetchModels(
        config: LlmProviderConfig,
        modelsDevUrl: String,
    ): FetchModelsResult = withContext(Dispatchers.IO) {
        try {
            val definition = PiProviderCatalog.resolve(config.piProviderId)
            val providerModels = runCatching { fetchProviderModels(config) }.getOrElse { error ->
                FetchModelsResult(emptyList(), error.message ?: "Unable to fetch models.")
            }
            if (providerModels.models.isNotEmpty()) return@withContext providerModels

            val publicModels = fetchModelsDevModels(definition, modelsDevUrl)
            if (publicModels.models.isNotEmpty()) publicModels else FetchModelsResult(
                models = emptyList(),
                error = providerModels.error ?: publicModels.error,
            )
        } catch (e: Exception) {
            FetchModelsResult(emptyList(), e.message ?: "Unknown error")
        }
    }

    suspend fun fetchPublicThinkingCatalog(options: List<ProviderModelOption>): PublicCatalogThinkingResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("https://models.dev/catalog.json").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                try {
                    if (connection.responseCode != 200) return@runCatching PublicCatalogThinkingResult()
                    publicCatalogThinkingResult(
                        connection.inputStream.bufferedReader().readText(),
                        options,
                    )
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(PublicCatalogThinkingResult())
        }

    suspend fun fetchPublicThinkingLevels(options: List<ProviderModelOption>): Map<String, List<String>> =
        fetchPublicThinkingCatalog(options).levelsByProviderModel

    private fun fetchModelsDevModels(
        definition: PiProviderDefinition,
        modelsDevUrl: String = ModelsDevUrl,
    ): FetchModelsResult {
        val connection = URL(modelsDevUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try {
            if (connection.responseCode != 200) {
                return FetchModelsResult(emptyList(), "models.dev returned HTTP ${connection.responseCode}.")
            }
            modelsDevProviderModels(
                JSONObject(connection.inputStream.bufferedReader().readText()),
                definition.modelsDevProviderIds(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchProviderModels(config: LlmProviderConfig): FetchModelsResult {
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        val modelsUrl = when {
            baseUrl.endsWith("/responses") -> baseUrl.replace("/responses", "/models")
            baseUrl.endsWith("/chat/completions") -> baseUrl.replace("/chat/completions", "/models")
            baseUrl.endsWith("/v1") -> "$baseUrl/models"
            else -> "$baseUrl/models"
        }

        val connection = URL(modelsUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.applyAetherLlmHeaders(config.userAgent, config.customHeaders)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                val models = mutableListOf<String>()
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val modelObj = dataArray.optJSONObject(i)
                        val modelId = modelObj?.optString("id")
                        if (!modelId.isNullOrBlank()) {
                            models.add(modelId)
                        }
                    }
                }
                FetchModelsResult(
                    models
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinctBy { it.lowercase() },
                )
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP ${connection.responseCode}"
                FetchModelsResult(emptyList(), errorText)
            }
        } finally {
            connection.disconnect()
        }
    }

}

internal fun modelsDevProviderModels(
    catalog: JSONObject,
    providerIds: List<String>,
): ProviderModelCatalogClient.FetchModelsResult {
    val providers = catalog.optJSONObject("providers")
        ?: return ProviderModelCatalogClient.FetchModelsResult(
            emptyList(),
            "No provider catalog was returned by models.dev.",
        )
    providerIds.forEach { providerId ->
        val models = providers.optJSONObject(providerId)?.optJSONObject("models") ?: return@forEach
        val modelIds = buildList {
            models.keys().forEach { key ->
                val modelId = models.optJSONObject(key)?.optString("id").orEmpty()
                    .ifBlank { key }
                    .trim()
                if (modelId.isNotBlank()) add(modelId)
            }
        }.distinctBy(String::lowercase)
        if (modelIds.isNotEmpty()) {
            return ProviderModelCatalogClient.FetchModelsResult(modelIds)
        }
    }
    return ProviderModelCatalogClient.FetchModelsResult(
        emptyList(),
        "Provider ${providerIds.joinToString()} is unavailable in models.dev.",
    )
}
