package com.zhousl.aether.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Thinking levels understood by Aether and Pi, in ascending order. */
val ModelsDevThinkingLevels: List<String> = listOf("off", "minimal", "low", "medium", "high", "xhigh", "max")

/** Request limits models.dev publishes for one provider model. */
@Serializable
data class ModelsDevModelLimits(
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsImageInput: Boolean? = null,
)

data class ModelsDevModelCapabilities(
    val reasoning: Boolean,
    /** Selectable levels; includes "off" only when the provider can disable reasoning. */
    val thinkingLevels: List<String>,
    /** Pi wire overrides, currently only off -> none. */
    val thinkingLevelMap: Map<String, String>,
    val limits: ModelsDevModelLimits,
)

data class ModelsDevCapabilitiesResult(
    val levelsByProviderModel: Map<String, List<String>> = emptyMap(),
    val levelMapsByProviderModel: Map<String, Map<String, String>> = emptyMap(),
    val reasoningModels: Set<String> = emptySet(),
    val limitsByProviderModel: Map<String, ModelsDevModelLimits> = emptyMap(),
)

fun modelsDevCatalogKey(providerId: String, modelId: String): String =
    "${providerId.trim()}/${modelId.substringAfterLast('/').trim()}"

/** Resolve every configured model against the raw models.dev catalog JSON. */
fun resolveModelsDevCapabilities(
    catalogJson: String,
    options: List<ProviderModelOption>,
): ModelsDevCapabilitiesResult {
    val catalog = runCatching { Json.parseToJsonElement(catalogJson) as? JsonObject }.getOrNull()
        ?: return ModelsDevCapabilitiesResult()
    return resolveModelsDevCapabilities(catalog, options)
}

internal fun resolveModelsDevCapabilities(
    catalog: JsonObject,
    options: List<ProviderModelOption>,
): ModelsDevCapabilitiesResult {
    val providers = catalog["providers"] as? JsonObject ?: return ModelsDevCapabilitiesResult()
    val index = ModelsDevCatalogIndex(providers, catalog["models"] as? JsonObject)
    val levels = mutableMapOf<String, List<String>>()
    val levelMaps = mutableMapOf<String, Map<String, String>>()
    val reasoningModels = mutableSetOf<String>()
    val limits = mutableMapOf<String, ModelsDevModelLimits>()
    options.forEach { option ->
        val capabilities = index.resolve(option) ?: return@forEach
        val key = modelsDevCatalogKey(option.piProviderId, option.modelId)
        levels[key] = capabilities.thinkingLevels
        if (capabilities.thinkingLevelMap.isNotEmpty()) levelMaps[key] = capabilities.thinkingLevelMap
        if (capabilities.reasoning) reasoningModels += key
        limits[key] = capabilities.limits
    }
    return ModelsDevCapabilitiesResult(levels, levelMaps, reasoningModels, limits)
}

/**
 * Lookup order, all driven by catalog data:
 * 1. the configured provider's own models.dev entry;
 * 2. for providers models.dev does not list (custom endpoints, relays), the
 *    entry of the lab that publishes the model, taken from its canonical id
 *    (meta for meta/muse-spark-1.3) or from a lab prefix in the configured id;
 * 3. otherwise the union of every provider entry for the same model.
 */
private class ModelsDevCatalogIndex(
    private val providers: JsonObject,
    canonicalModels: JsonObject?,
) {
    private class Entry(val providerId: String, val model: JsonObject)

    private val entriesByCanonicalId = mutableMapOf<String, MutableList<Entry>>()
    private val entriesByModelId = mutableMapOf<String, MutableList<Entry>>()
    private val canonicalIdsByShortId = mutableMapOf<String, MutableList<String>>()
    private val canonicalIds = mutableSetOf<String>()

    init {
        canonicalModels?.keys?.forEach { id ->
            val canonicalId = id.trim().lowercase()
            canonicalIds += canonicalId
            canonicalIdsByShortId.getOrPut(canonicalId.substringAfterLast('/')) { mutableListOf() } += canonicalId
        }
        providers.forEach { (providerId, providerValue) ->
            val models = (providerValue as? JsonObject)?.get("models") as? JsonObject ?: return@forEach
            models.forEach { (key, modelValue) ->
                val model = modelValue as? JsonObject ?: return@forEach
                val entry = Entry(providerId, model)
                model.string("canonical_model_id").lowercase().takeIf(String::isNotBlank)?.let { canonicalId ->
                    entriesByCanonicalId.getOrPut(canonicalId) { mutableListOf() } += entry
                }
                val id = model.string("id").ifBlank { key }
                setOf(key, id, id.substringAfterLast('/'))
                    .map { it.trim().lowercase() }
                    .filter(String::isNotBlank)
                    .forEach { lookupKey -> entriesByModelId.getOrPut(lookupKey) { mutableListOf() } += entry }
            }
        }
    }

    fun resolve(option: ProviderModelOption): ModelsDevModelCapabilities? {
        val definition = PiProviderCatalog.find(option.piProviderId)?.takeIf(PiProviderDefinition::isBuiltIn)
        definition?.modelsDevProviderIds().orEmpty().firstNotNullOfOrNull { providerId ->
            findInProvider(providerId, option)
        }?.let { return capabilitiesOf(listOf(it)) }

        val canonicalId = canonicalIdFor(option)
        // The lab that publishes the model: from its canonical id, or from an
        // explicit lab prefix in the configured id (moonshotai/Kimi-K3).
        val labId = canonicalId?.substringBefore('/', "")?.takeIf(String::isNotBlank)
            ?: option.modelId.substringBeforeLast('/', "").substringAfterLast('/').trim().lowercase()
                .takeIf(String::isNotBlank)
        if (labId != null) {
            val firstParty = canonicalId?.let(entriesByCanonicalId::get).orEmpty()
                .firstOrNull { it.providerId.equals(labId, ignoreCase = true) }
                ?: findInProvider(labId, option)
            if (firstParty != null) return capabilitiesOf(listOf(firstParty))
        }
        val candidates = canonicalId?.let(entriesByCanonicalId::get).orEmpty().ifEmpty {
            option.lookupKeys().firstNotNullOfOrNull { key -> entriesByModelId[key]?.takeIf(List<Entry>::isNotEmpty) }
                .orEmpty()
        }
        if (candidates.isEmpty()) return null
        return capabilitiesOf(candidates)
    }

    private fun findInProvider(providerId: String, option: ProviderModelOption): Entry? {
        val models = (providers[providerId] as? JsonObject)?.get("models") as? JsonObject ?: return null
        option.lookupKeys(caseSensitive = true).firstNotNullOfOrNull { key -> models[key] as? JsonObject }
            ?.let { return Entry(providerId, it) }
        val shortId = option.modelId.substringAfterLast('/').trim()
        return models.entries.firstNotNullOfOrNull { (key, value) ->
            val model = value as? JsonObject ?: return@firstNotNullOfOrNull null
            model.takeIf {
                listOf(key, model.string("id")).any { id ->
                    id.substringAfterLast('/').trim().equals(shortId, ignoreCase = true)
                }
            }?.let { Entry(providerId, it) }
        }
    }

    private fun canonicalIdFor(option: ProviderModelOption): String? {
        val keys = option.lookupKeys()
        keys.firstOrNull { it in canonicalIds }?.let { return it }
        keys.forEach { key ->
            val fromEntries = entriesByModelId[key].orEmpty()
                .mapNotNull { entry -> entry.model.string("canonical_model_id").lowercase().takeIf(String::isNotBlank) }
            mostCommon(fromEntries)?.let { return it }
        }
        val shortId = option.modelId.substringAfterLast('/').trim().lowercase()
        val byShortId = canonicalIdsByShortId[shortId].orEmpty()
        val prefix = option.modelId.substringBeforeLast('/', "").substringAfterLast('/').trim().lowercase()
        return byShortId.firstOrNull { prefix.isNotBlank() && it.startsWith("$prefix/") } ?: byShortId.firstOrNull()
    }

    private fun capabilitiesOf(entries: List<Entry>): ModelsDevModelCapabilities {
        val reasoningEntries = entries.filter { it.model.boolean("reasoning") == true }
        val levels = mutableSetOf<String>()
        var canDisable = false
        reasoningEntries.forEach { entry ->
            (entry.model["reasoning_options"] as? JsonArray).orEmpty().forEach { reasoningOption ->
                val option = reasoningOption as? JsonObject ?: return@forEach
                when (option.string("type")) {
                    "toggle" -> canDisable = true
                    "effort" -> (option["values"] as? JsonArray).orEmpty().forEach { value ->
                        val level = (value as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
                        when {
                            level == "none" || level == "off" -> canDisable = true
                            level != null && level in ModelsDevThinkingLevels -> levels += level
                        }
                    }
                }
            }
        }
        val reasoning = reasoningEntries.isNotEmpty()
        val thinkingLevels = if (!reasoning) {
            emptyList()
        } else {
            ModelsDevThinkingLevels.filter { level -> if (level == "off") canDisable else level in levels }
        }
        return ModelsDevModelCapabilities(
            reasoning = reasoning,
            thinkingLevels = thinkingLevels,
            thinkingLevelMap = if (reasoning && canDisable) mapOf("off" to "none") else emptyMap(),
            limits = ModelsDevModelLimits(
                contextWindow = mostCommon(entries.mapNotNull { it.model.limit("context") }),
                maxOutputTokens = mostCommon(entries.mapNotNull { it.model.limit("output") }),
                supportsImageInput = entries
                    .mapNotNull { entry -> (entry.model["modalities"] as? JsonObject)?.get("input") as? JsonArray }
                    .takeIf(List<JsonArray>::isNotEmpty)
                    ?.any { input -> input.any { (it as? JsonPrimitive)?.contentOrNull == "image" } },
            ),
        )
    }
}

private fun ProviderModelOption.lookupKeys(caseSensitive: Boolean = false): List<String> =
    listOf(
        modelId,
        modelId.substringAfter("$piProviderId/", modelId),
        modelId.substringAfterLast('/'),
    ).map { key -> key.trim().let { if (caseSensitive) it else it.lowercase() } }
        .filter(String::isNotBlank)
        .distinct()

/** Most frequent value; ties resolve to the larger value so limits are never understated by one outlier. */
private fun <T : Comparable<T>> mostCommon(values: List<T>): T? =
    values.groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy<Map.Entry<T, Int>> { it.value }.thenBy { it.key })
        ?.key

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.limit(key: String): Int? =
    ((this["limit"] as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull
        ?.takeIf { it > 0 && it <= Int.MAX_VALUE }
        ?.toInt()
