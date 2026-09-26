package com.zhousl.aether.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable

data class SharedProviderModelsResult(
    val models: List<String>,
    val error: String? = null,
)

data class SharedThinkingCatalogResult(
    val levelsByProviderModel: Map<String, List<String>> = emptyMap(),
    val levelMapsByProviderModel: Map<String, Map<String, String>> = emptyMap(),
    val reasoningModels: Set<String> = emptySet(),
)

@Serializable
data class SharedModelCatalogInfo(
    val displayName: String,
    val labId: String,
    val labName: String,
    val labLogoUrl: String,
    val labLogoPathData: List<String> = emptyList(),
    val labLogoViewportWidth: Float = 40f,
    val labLogoViewportHeight: Float = 40f,
)

private val SharedThinkingLevels = listOf("off", "minimal", "low", "medium", "high", "xhigh", "max")

internal fun sharedThinkingCatalogKey(providerId: String, modelId: String): String =
    "${providerId.trim()}/${modelId.substringAfterLast('/').trim()}"

class SharedProviderModelCatalogClient(engine: HttpClientEngine? = null) {
    private val client = if (engine == null) createClient() else createClient(engine)
    private val publicCatalogMutex = Mutex()
    private val labLogoMutex = Mutex()
    private var cachedPublicCatalog: JsonObject? = null
    private val cachedLabLogos = mutableMapOf<String, SharedLabLogo?>()

    suspend fun fetchModelInfo(options: List<ProviderModelOption>): Map<String, SharedModelCatalogInfo> {
        if (options.isEmpty()) return emptyMap()
        return runCatching {
            val models = (fetchPublicCatalog()?.get("models") as? JsonObject).orEmpty()
            val matched = withContext(Dispatchers.Default) {
                val catalog = buildMap {
                    models.forEach { (key, value) ->
                        val model = value as? JsonObject ?: return@forEach
                        val id = model.stringValue("id").ifBlank { key }.trim()
                        val labId = id.substringBefore('/').takeIf { it != id }.orEmpty()
                        val name = model.stringValue("name").trim()
                        if (name.isBlank()) return@forEach
                        val info = sharedModelCatalogInfo(name, labId)
                        put(key.trim().lowercase(), info)
                        put(id.lowercase(), info)
                        id.substringAfterLast('/').takeIf(String::isNotBlank)?.let { shortId ->
                            val normalizedShortId = shortId.lowercase()
                            if (normalizedShortId !in this) put(normalizedShortId, info)
                        }
                    }
                }
                options.associate { option ->
                    val info = option.sharedCatalogLookupKeys()
                        .firstNotNullOfOrNull { catalog[it.lowercase()] }
                        ?: sharedModelCatalogInfo(option.modelId, inferSharedModelLabId(option.modelId))
                    option.key to info
                }
            }
            val logos = fetchSharedLabLogos(matched.values.map(SharedModelCatalogInfo::labId))
            withContext(Dispatchers.Default) {
                matched.mapValues { (_, info) ->
                    logos[info.labId]?.let { logo ->
                        info.copy(
                            labLogoPathData = logo.pathData,
                            labLogoViewportWidth = logo.viewportWidth,
                            labLogoViewportHeight = logo.viewportHeight,
                        )
                    } ?: info
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun fetchThinkingCatalog(
        options: List<ProviderModelOption>,
    ): SharedThinkingCatalogResult = runCatching {
        val providers = fetchPublicCatalog()?.get("providers") as? JsonObject ?: return@runCatching SharedThinkingCatalogResult()
        withContext(Dispatchers.Default) {
            val fallbackModels = providers.sharedPublicCatalogModelIndex()
            val levelsMap = mutableMapOf<String, List<String>>()
            val levelMapsMap = mutableMapOf<String, Map<String, String>>()
            val reasoningModels = mutableSetOf<String>()
            options.forEach { option ->
                val model = option.publicCatalogProviderIds()
                    .firstNotNullOfOrNull { providerId ->
                        ((providers[providerId] as? JsonObject)?.get("models") as? JsonObject)
                            ?.findSharedPublicCatalogModel(option)
                    }
                    ?: option.sharedPublicCatalogModelKeys()
                        .firstNotNullOfOrNull { fallbackModels[it.lowercase()] }
                val key = sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                if (model?.get("reasoning")?.jsonPrimitive?.booleanOrNull == true) {
                    reasoningModels += key
                    val optionsArray = model["reasoning_options"] as? JsonArray
                    val hasToggle = optionsArray.orEmpty().any { entry ->
                        (entry as? JsonObject)?.stringValue("type") == "toggle"
                    }
                    var hasNone = false
                    val levels = buildList {
                        if (hasToggle) add("off")
                        optionsArray.orEmpty().forEach { entry ->
                            val reasoningOption = entry as? JsonObject ?: return@forEach
                            if (reasoningOption.stringValue("type") != "effort") return@forEach
                            (reasoningOption["values"] as? JsonArray).orEmpty()
                                .mapNotNull { it.jsonPrimitive.contentOrNull }
                                .forEach { raw ->
                                    val trimmed = raw.trim()
                                    if (trimmed == "none") {
                                        hasNone = true
                                        if ("off" !in this) add("off")
                                    } else if (trimmed in SharedThinkingLevels && trimmed !in this) {
                                        add(trimmed)
                                    }
                                }
                        }
                    }
                    val levelMap = buildMap<String, String> {
                        if (hasToggle || hasNone) put("off", "none")
                    }
                    levelsMap[key] = levels
                    if (levelMap.isNotEmpty()) levelMapsMap[key] = levelMap
                } else {
                    levelsMap[key] = emptyList()
                }
            }
            SharedThinkingCatalogResult(levelsMap, levelMapsMap, reasoningModels)
        }
    }.getOrDefault(SharedThinkingCatalogResult())

    suspend fun fetchThinkingLevels(
        options: List<ProviderModelOption>,
    ): Map<String, List<String>> = fetchThinkingCatalog(options).levelsByProviderModel

    suspend fun fetchModels(
        config: LlmProviderConfig,
    ): SharedProviderModelsResult {
        val definition = PiProviderCatalog.resolve(config.piProviderId)
        val providerModels = if (definition.id == "requesty") {
            fetchRequestyModels(config)
        } else {
            fetchProviderModels(config)
        }
        if (providerModels.models.isNotEmpty()) return providerModels

        val publicModels = fetchPublicProviderModels(definition)
        return if (publicModels.models.isNotEmpty()) publicModels else SharedProviderModelsResult(
            models = emptyList(),
            error = providerModels.error ?: publicModels.error,
        )
    }

    private suspend fun fetchPublicProviderModels(
        definition: PiProviderDefinition,
    ): SharedProviderModelsResult = runCatching {
        val catalog = fetchPublicCatalog()
            ?: return@runCatching SharedProviderModelsResult(
                emptyList(),
                "No provider catalog was returned by models.dev.",
            )
        modelsFromPublicProviderCatalog(catalog, definition.modelsDevProviderIds())
    }.getOrElse { error ->
        SharedProviderModelsResult(emptyList(), error.message ?: "Unable to fetch models from models.dev.")
    }

    private suspend fun fetchPublicCatalog(): JsonObject? {
        cachedPublicCatalog?.let { return it }
        return publicCatalogMutex.withLock {
            cachedPublicCatalog?.let { return@withLock it }
            val response = client.get(SharedModelCatalogUrl)
            if (!response.status.isSuccess()) return@withLock null
            val body = response.body<String>()
            withContext(Dispatchers.Default) {
                Json.parseToJsonElement(body) as? JsonObject
            }?.also { cachedPublicCatalog = it }
        }
    }

    private suspend fun fetchSharedLabLogo(labId: String): SharedLabLogo? = runCatching {
        val response = client.get("$SharedModelLogoBaseUrl/$labId.svg")
        if (!response.status.isSuccess()) return@runCatching null
        val body = response.body<String>()
        withContext(Dispatchers.Default) { parseSharedLabLogo(body) }
    }.getOrNull()

    private suspend fun fetchSharedLabLogos(labIds: Collection<String>): Map<String, SharedLabLogo?> =
        coroutineScope {
            val normalizedLabIds = labIds.map(String::trim).filter(String::isNotBlank).distinct()
            val missingLabIds = labLogoMutex.withLock {
                normalizedLabIds.filterNot(cachedLabLogos::containsKey)
            }
            missingLabIds.map { labId ->
                async { labId to fetchSharedLabLogo(labId) }
            }.awaitAll().let { fetched ->
                labLogoMutex.withLock {
                    fetched.forEach { (labId, logo) -> cachedLabLogos[labId] = logo }
                    normalizedLabIds.associateWith { cachedLabLogos[it] }
                }
            }
    }

    // Requesty lists its curated managed models first, followed by the full catalog.
    private suspend fun fetchRequestyModels(config: LlmProviderConfig): SharedProviderModelsResult {
        val managedModels = fetchProviderModels(config, endpointSuffix = "/managed")
        val catalogModels = fetchProviderModels(config)
        val models = (managedModels.models + catalogModels.models).distinctBy(String::lowercase)
        return SharedProviderModelsResult(
            models,
            if (models.isEmpty()) catalogModels.error ?: managedModels.error else null,
        )
    }

    private suspend fun fetchProviderModels(
        config: LlmProviderConfig,
        endpointSuffix: String = "",
    ): SharedProviderModelsResult {
        return runCatching {
            val modelsUrl = modelsEndpoint(config.baseUrl) + endpointSuffix
            val response = client.get(modelsUrl) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${config.apiKey.trim()}")
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.UserAgent, normalizeLlmUserAgent(config.userAgent))
                    config.customHeaders.normalizedLlmHeaders().forEach { header ->
                        remove(header.name)
                        append(header.name, header.value)
                    }
                }
            }
            val body = response.body<String>()
            if (!response.status.isSuccess()) {
                return@runCatching SharedProviderModelsResult(
                    emptyList(),
                    body.ifBlank { "HTTP ${response.status.value}" },
                )
            }
            val root = Json.parseToJsonElement(body) as? JsonObject
                ?: return@runCatching SharedProviderModelsResult(emptyList(), "Invalid model response.")
            val models = (root["data"] as? JsonArray)
                ?.mapNotNull { entry ->
                    (entry as? JsonObject)
                        ?.get("id")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }
                ?.distinctBy(String::lowercase)
                .orEmpty()
            SharedProviderModelsResult(
                models,
                if (models.isEmpty()) "The model endpoint returned no models." else null,
            )
        }.getOrElse { error ->
            SharedProviderModelsResult(emptyList(), error.message ?: "Unable to fetch models.")
        }
    }

    private companion object {
        fun createClient(engine: HttpClientEngine? = null): HttpClient =
            if (engine == null) {
                HttpClient { configureTimeouts() }
            } else {
                HttpClient(engine) { configureTimeouts() }
            }

        fun io.ktor.client.HttpClientConfig<*>.configureTimeouts() {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 30_000
            }
        }
    }
}

private const val SharedModelCatalogUrl = "https://models.dev/catalog.json"
private const val SharedModelLogoBaseUrl = "https://models.dev/logos/labs"

private data class SharedLabLogo(
    val pathData: List<String>,
    val viewportWidth: Float,
    val viewportHeight: Float,
)

private fun parseSharedLabLogo(svg: String): SharedLabLogo? {
    val viewBox = Regex("""viewBox\s*=\s*"([^"]+)"""").find(svg)
        ?.groupValues?.getOrNull(1)
        ?.split(Regex("\\s+|,"))
        ?.mapNotNull(String::toFloatOrNull)
        .orEmpty()
    val pathData = Regex("""<path\b[^>]*\bd\s*=\s*"([^"]+)"""")
        .findAll(svg)
        .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
        .toList()
    if (pathData.isEmpty()) return null
    return SharedLabLogo(
        pathData = pathData,
        viewportWidth = viewBox.getOrNull(2) ?: 40f,
        viewportHeight = viewBox.getOrNull(3) ?: 40f,
    )
}

private fun ProviderModelOption.sharedCatalogLookupKeys(): List<String> = listOf(
    fullLabel,
    "$providerId/$modelId",
    modelId,
    modelId.substringAfterLast('/'),
).map(String::trim).filter(String::isNotEmpty).distinct()

private fun ProviderModelOption.sharedPublicCatalogModelKeys(): List<String> = listOf(
    modelId,
    modelId.substringAfter("$piProviderId/", modelId),
    modelId.substringAfterLast('/'),
).map(String::trim).filter(String::isNotEmpty).distinct()

private fun JsonObject.findSharedPublicCatalogModel(option: ProviderModelOption): JsonObject? {
    val lookupKeys = option.sharedPublicCatalogModelKeys()
    lookupKeys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }?.let { return it }
    val normalizedModelId = option.modelId.substringAfterLast('/').trim()
    return entries.firstNotNullOfOrNull { (key, value) ->
        val model = value as? JsonObject ?: return@firstNotNullOfOrNull null
        val candidateIds = listOf(key, model.stringValue("id"))
        model.takeIf { candidateIds.any { id ->
            id.substringAfterLast('/').trim().equals(normalizedModelId, ignoreCase = true)
        } }
    }
}

private fun JsonObject.sharedPublicCatalogModelIndex(): Map<String, JsonObject> = buildMap {
    this@sharedPublicCatalogModelIndex.values.forEach { providerValue ->
        val provider = providerValue as? JsonObject ?: return@forEach
        val models = provider["models"] as? JsonObject ?: return@forEach
        models.forEach { (key, modelValue) ->
            val model = modelValue as? JsonObject ?: return@forEach
            val id = model.stringValue("id").ifBlank { key }.trim()
            listOf(key, id, id.substringAfterLast('/'))
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { lookupKey ->
                    val normalizedKey = lookupKey.lowercase()
                    if (normalizedKey !in this) put(normalizedKey, model)
                }
        }
    }
}

private fun ProviderModelOption.publicCatalogProviderIds(): List<String> = buildList {
    add(
        when (piProviderId) {
            "openai-codex" -> "openai"
            "kimi-coding" -> "moonshotai"
            else -> piProviderId
        }
    )
    modelId.substringBeforeLast('/', "").trim().takeIf(String::isNotBlank)?.let(::add)
    if (modelId.substringAfterLast('/').trim().startsWith("kimi-", ignoreCase = true)) {
        add("moonshotai")
    }
}.filter(String::isNotBlank).distinct()

private fun sharedModelCatalogInfo(displayName: String, labId: String): SharedModelCatalogInfo =
    SharedModelCatalogInfo(
        displayName = displayName,
        labId = labId,
        labName = sharedModelLabDisplayName(labId),
        labLogoUrl = if (labId.isBlank()) "" else "$SharedModelLogoBaseUrl/$labId.svg",
    )

private fun inferSharedModelLabId(modelId: String): String {
    val normalized = modelId.substringAfterLast('/').lowercase()
    return when {
        normalized.startsWith("gpt") || normalized.startsWith("o1") ||
            normalized.startsWith("o3") || normalized.startsWith("o4") -> "openai"
        normalized.startsWith("gemini") -> "google"
        normalized.startsWith("claude") -> "anthropic"
        normalized.startsWith("grok") -> "xai"
        normalized.startsWith("qwen") -> "alibaba"
        normalized.startsWith("kimi") -> "moonshotai"
        normalized.startsWith("mimo") -> "xiaomi"
        normalized.startsWith("glm") -> "zhipuai"
        normalized.startsWith("nemotron") -> "nvidia"
        normalized.startsWith("deepseek") -> "deepseek"
        normalized.startsWith("mistral") || normalized.startsWith("mixtral") ||
            normalized.startsWith("codestral") -> "mistral"
        normalized.startsWith("llama") -> "meta"
        normalized.startsWith("phi") -> "microsoft"
        normalized.startsWith("minimax") || normalized.startsWith("abab") -> "minimax"
        normalized.startsWith("sonar") -> "perplexity"
        normalized.startsWith("command") -> "cohere"
        else -> ""
    }
}

private fun sharedModelLabDisplayName(labId: String): String = when (labId.lowercase()) {
    "alibaba" -> "Alibaba"
    "anthropic" -> "Anthropic"
    "cohere" -> "Cohere"
    "deepreinforce" -> "DeepReinforce"
    "deepseek" -> "DeepSeek"
    "google" -> "Google"
    "meituan" -> "Meituan"
    "meta" -> "Meta"
    "microsoft" -> "Microsoft"
    "minimax" -> "MiniMax"
    "mistral" -> "Mistral"
    "moonshotai" -> "Moonshot AI"
    "nvidia" -> "NVIDIA"
    "openai" -> "OpenAI"
    "perplexity" -> "Perplexity"
    "sakana" -> "Sakana AI"
    "sarvam" -> "Sarvam AI"
    "stepfun" -> "StepFun"
    "tencent" -> "Tencent"
    "xai" -> "xAI"
    "xiaomi" -> "Xiaomi"
    "zhipuai" -> "Zhipu AI"
    else -> labId.split('-', '_')
        .filter(String::isNotBlank)
        .joinToString(" ") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

internal fun modelsEndpoint(baseUrl: String): String {
    val normalized = baseUrl.trim().trimEnd('/')
    require(normalized.isNotBlank()) { "A base URL is required to fetch models." }
    return when {
        normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/models"
        normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/models"
        else -> "$normalized/models"
    }
}

internal fun modelsFromPublicProviderCatalog(
    catalog: JsonObject,
    providerIds: List<String>,
): SharedProviderModelsResult {
    val providers = catalog["providers"] as? JsonObject
        ?: return SharedProviderModelsResult(
            emptyList(),
            "No provider catalog was returned by models.dev.",
        )
    providerIds.forEach { providerId ->
        val models = ((providers[providerId] as? JsonObject)?.get("models") as? JsonObject)
            ?: return@forEach
        val modelIds = models.mapNotNull { (key, value) ->
            (value as? JsonObject)?.stringValue("id").orEmpty().ifBlank { key }
                .trim().takeIf(String::isNotBlank)
        }.distinctBy(String::lowercase)
        if (modelIds.isNotEmpty()) return SharedProviderModelsResult(modelIds)
    }
    return SharedProviderModelsResult(
        emptyList(),
        "Provider ${providerIds.joinToString()} is unavailable in models.dev.",
    )
}
