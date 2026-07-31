package com.zhousl.aether.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SharedExtensionLoadOptions(
    val disabledExtensionPaths: Set<String> = emptySet(),
    val disabledPackageSources: Set<String> = emptySet(),
) {
    internal fun toPayload() = buildJsonObject {
        put("disabled_extension_paths", JsonArray(disabledExtensionPaths.sorted().map(::JsonPrimitive)))
        put("disabled_package_sources", JsonArray(disabledPackageSources.sorted().map(::JsonPrimitive)))
    }
}

