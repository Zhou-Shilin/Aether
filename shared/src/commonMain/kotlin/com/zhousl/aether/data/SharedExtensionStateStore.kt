package com.zhousl.aether.data

import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.SharedExtensionLoadOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SharedExtensionStateStore(
    private val runtime: MultiplatformLocalRuntime,
) {
    private val statePath = "${runtime.homeDirectory.trimEnd('/')}/.aether/extension-state.json"
    private val mutex = Mutex()

    suspend fun load(): SharedExtensionLoadOptions = mutex.withLock { loadUnlocked() }

    suspend fun setPackageEnabled(source: String, enabled: Boolean): SharedExtensionLoadOptions =
        update { current ->
            current.copy(
                disabledPackageSources = current.disabledPackageSources.toMutableSet().apply {
                    if (enabled) remove(source) else add(source)
                }
            )
        }

    suspend fun setImportedExtensionEnabled(path: String, enabled: Boolean): SharedExtensionLoadOptions =
        update { current ->
            current.copy(
                disabledExtensionPaths = current.disabledExtensionPaths.toMutableSet().apply {
                    if (enabled) remove(path) else add(path)
                }
            )
        }

    suspend fun removePackage(source: String): SharedExtensionLoadOptions =
        setPackageEnabled(source, true)

    suspend fun removeImportedExtension(path: String): SharedExtensionLoadOptions =
        setImportedExtensionEnabled(path, true)

    private suspend fun update(
        transform: (SharedExtensionLoadOptions) -> SharedExtensionLoadOptions,
    ): SharedExtensionLoadOptions = mutex.withLock {
        val updated = transform(loadUnlocked())
        runtime.fileSystem.createDirectories(statePath.substringBeforeLast('/'))
        runtime.fileSystem.write(
            statePath,
            buildJsonObject {
                put(
                    "disabled_extension_paths",
                    JsonArray(updated.disabledExtensionPaths.sorted().map(::JsonPrimitive)),
                )
                put(
                    "disabled_package_sources",
                    JsonArray(updated.disabledPackageSources.sorted().map(::JsonPrimitive)),
                )
            }.toString().encodeToByteArray(),
        )
        updated
    }

    private suspend fun loadUnlocked(): SharedExtensionLoadOptions {
        if (!runtime.fileSystem.exists(statePath)) return SharedExtensionLoadOptions()
        return runCatching {
            val state = Json.parseToJsonElement(runtime.fileSystem.read(statePath).decodeToString()) as JsonObject
            SharedExtensionLoadOptions(
                disabledExtensionPaths = state.stringSet("disabled_extension_paths"),
                disabledPackageSources = state.stringSet("disabled_package_sources"),
            )
        }.getOrDefault(SharedExtensionLoadOptions())
    }
}

private fun JsonObject.stringSet(name: String): Set<String> =
    (get(name) as? JsonArray).orEmpty()
        .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .toSet()
