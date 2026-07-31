package com.zhousl.aether.data

import com.zhousl.aether.runtime.SharedPiBridgeClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SharedAetherExtensionInfo(
    val id: String,
    val name: String,
    val path: String,
)

data class SharedAetherExtensionSurface(
    val id: String,
    val extensionId: String,
    val extensionName: String,
    val slot: String,
    val order: Int,
    val tree: JsonElement?,
)

data class SharedAetherExtensionComponent(
    val id: String,
    val extensionId: String,
    val extensionName: String,
    val target: String,
    val mode: String,
    val order: Int,
    val tree: JsonElement?,
)

data class SharedAetherExtensionPage(
    val id: String,
    val localId: String,
    val extensionId: String,
    val extensionName: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val order: Int,
    val tree: JsonElement?,
)

data class SharedAetherExtensionError(
    val path: String,
    val extensionId: String,
    val phase: String,
    val message: String,
)

data class SharedAetherExtensionSnapshot(
    val apiVersion: Int = 2,
    val version: Long = 0,
    val extensions: List<SharedAetherExtensionInfo> = emptyList(),
    val surfaces: List<SharedAetherExtensionSurface> = emptyList(),
    val components: List<SharedAetherExtensionComponent> = emptyList(),
    val pages: List<SharedAetherExtensionPage> = emptyList(),
    val eventNames: Set<String> = emptySet(),
    val errors: List<SharedAetherExtensionError> = emptyList(),
) {
    fun surfacesAt(slot: String): List<SharedAetherExtensionSurface> =
        surfaces.filter { it.slot == slot }.sortedWith(
            compareBy<SharedAetherExtensionSurface> { it.order }.thenBy { it.id }
        )

    fun componentsAt(target: String): List<SharedAetherExtensionComponent> =
        components.filter { it.target == target }.sortedWith(
            compareBy<SharedAetherExtensionComponent> { it.order }.thenBy { it.id }
        )
}

class SharedAetherExtensionManager(
    private val bridge: SharedPiBridgeClient,
    private val hostHandler: suspend (String, JsonObject) -> JsonObject,
) {
    private val mutex = Mutex()
    var snapshot: SharedAetherExtensionSnapshot = SharedAetherExtensionSnapshot()
        private set
    var error: String = ""
        private set
    var notification: String = ""
        private set

    suspend fun refresh(
        context: JsonObject = JsonObject(emptyMap()),
    ): SharedAetherExtensionSnapshot = mutex.withLock {
        val response = bridge.getAetherExtensions(context, ::handleEvent)
        parseResponse(response)
    }

    suspend fun reload(
        context: JsonObject = JsonObject(emptyMap()),
    ): SharedAetherExtensionSnapshot = mutex.withLock {
        val response = bridge.reloadAetherExtensions(context, ::handleEvent)
        parseResponse(response)
    }

    suspend fun invokeAction(
        extensionId: String,
        action: String,
        args: JsonObject = JsonObject(emptyMap()),
        context: JsonObject = JsonObject(emptyMap()),
    ): SharedAetherExtensionSnapshot = mutex.withLock {
        val response = bridge.invokeAetherExtensionAction(
            extensionId = extensionId,
            action = action,
            args = args,
            context = context,
            onEvent = ::handleEvent,
        )
        parseResponse(response)
    }

    suspend fun dispatchEvent(
        event: String,
        data: JsonObject = JsonObject(emptyMap()),
        context: JsonObject = JsonObject(emptyMap()),
    ): JsonObject {
        if (event !in snapshot.eventNames) return JsonObject(emptyMap())
        return mutex.withLock {
            val response = bridge.dispatchAetherExtensionEvent(
                event = event,
                data = data,
                context = context,
                onEvent = ::handleEvent,
            )
            response.objectOrNull("snapshot")?.let {
                snapshot = parseSharedAetherExtensionSnapshot(it)
            }
            response
        }
    }

    private suspend fun handleEvent(event: String, payload: JsonObject) {
        when (event) {
            "aether_notification" -> notification = payload.string("message")
            "aether_host_call" -> {
                val callId = payload.string("call_id")
                val method = payload.string("method")
                val args = payload.objectOrNull("args") ?: JsonObject(emptyMap())
                val result = runCatching { hostHandler(method, args) }
                bridge.sendAetherHostResult(
                    callId = callId,
                    result = result.getOrDefault(JsonObject(emptyMap())),
                    error = result.exceptionOrNull()?.message.orEmpty(),
                )
            }
        }
    }

    private fun parseResponse(response: JsonObject): SharedAetherExtensionSnapshot {
        snapshot = parseSharedAetherExtensionSnapshot(response.objectOrNull("snapshot"))
        error = (response["errors"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("error")?.takeIf(String::isNotBlank) }
            .take(3)
            .joinToString("; ")
        return snapshot
    }
}

internal fun parseSharedAetherExtensionSnapshot(
    json: JsonObject?,
): SharedAetherExtensionSnapshot {
    if (json == null) return SharedAetherExtensionSnapshot()
    return SharedAetherExtensionSnapshot(
        apiVersion = json.int("api_version") ?: 2,
        version = json.long("version") ?: 0,
        extensions = json.objects("extensions").map { item ->
            SharedAetherExtensionInfo(
                id = item.string("id"),
                name = item.string("name"),
                path = item.string("path"),
            )
        },
        surfaces = json.objects("surfaces").map { item ->
            SharedAetherExtensionSurface(
                id = item.string("id"),
                extensionId = item.string("extension_id"),
                extensionName = item.string("extension_name"),
                slot = item.string("slot"),
                order = item.int("order") ?: 0,
                tree = item["tree"],
            )
        },
        components = json.objects("components").map { item ->
            SharedAetherExtensionComponent(
                id = item.string("id"),
                extensionId = item.string("extension_id"),
                extensionName = item.string("extension_name"),
                target = item.string("target"),
                mode = item.string("mode").ifBlank { "wrap" },
                order = item.int("order") ?: 0,
                tree = item["tree"],
            )
        },
        pages = json.objects("pages").map { item ->
            SharedAetherExtensionPage(
                id = item.string("id"),
                localId = item.string("local_id"),
                extensionId = item.string("extension_id"),
                extensionName = item.string("extension_name"),
                title = item.string("title"),
                subtitle = item.string("subtitle"),
                icon = item.string("icon"),
                order = item.int("order") ?: 0,
                tree = item["tree"],
            )
        },
        eventNames = (json["event_names"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .toSet(),
        errors = json.objects("errors").map { item ->
            SharedAetherExtensionError(
                path = item.string("path"),
                extensionId = item.string("extension_id"),
                phase = item.string("phase"),
                message = item.string("error"),
            )
        },
    )
}

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(name: String): Int? =
    get(name)?.jsonPrimitive?.intOrNull

private fun JsonObject.long(name: String): Long? =
    get(name)?.jsonPrimitive?.longOrNull

private fun JsonObject.objectOrNull(name: String): JsonObject? = get(name) as? JsonObject

private fun JsonObject.objects(name: String): List<JsonObject> =
    (get(name) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

