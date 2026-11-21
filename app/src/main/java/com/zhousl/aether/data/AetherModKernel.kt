package com.zhousl.aether.data

import com.zhousl.aether.mod.AetherNativeComponentRegistry
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

data class AetherModServiceMethod(
    val name: String,
    val description: String = "",
    val mutatesState: Boolean = false,
)

data class AetherModServiceDescriptor(
    val id: String,
    val description: String = "",
    val owner: String,
    val priority: Int,
    val methods: List<AetherModServiceMethod>,
)

fun interface AetherModServiceHandler {
    suspend fun invoke(
        method: String,
        args: JSONObject,
    ): JSONObject
}

class AetherModServiceRegistry {
    private data class Registration(
        val sequence: Long,
        val descriptor: AetherModServiceDescriptor,
        val handler: AetherModServiceHandler,
    )

    private val lock = Any()
    private val sequence = AtomicLong()
    private val registrations = linkedMapOf<String, MutableList<Registration>>()

    fun register(
        id: String,
        owner: String,
        description: String = "",
        priority: Int = 0,
        methods: List<AetherModServiceMethod>,
        handler: AetherModServiceHandler,
    ): () -> Unit {
        val normalizedId = id.trim()
        require(normalizedId.isNotBlank()) { "Aether mod services require an id." }
        val registration = Registration(
            sequence = sequence.incrementAndGet(),
            descriptor = AetherModServiceDescriptor(
                id = normalizedId,
                description = description,
                owner = owner.trim().ifBlank { "unknown" },
                priority = priority,
                methods = methods.distinctBy(AetherModServiceMethod::name),
            ),
            handler = handler,
        )
        synchronized(lock) {
            registrations.getOrPut(normalizedId, ::mutableListOf).add(registration)
        }
        return {
            synchronized(lock) {
                registrations[normalizedId]?.remove(registration)
                if (registrations[normalizedId].isNullOrEmpty()) {
                    registrations.remove(normalizedId)
                }
            }
        }
    }

    fun unregisterOwner(owner: String) {
        synchronized(lock) {
            registrations.values.forEach { entries ->
                entries.removeAll { it.descriptor.owner == owner }
            }
            registrations.entries.removeAll { it.value.isEmpty() }
        }
    }

    fun list(): List<AetherModServiceDescriptor> = synchronized(lock) {
        registrations.values.mapNotNull(::activeRegistration).map(Registration::descriptor)
    }

    fun describe(id: String): AetherModServiceDescriptor? = synchronized(lock) {
        activeRegistration(registrations[id.trim()].orEmpty())?.descriptor
    }

    suspend fun invoke(
        id: String,
        method: String,
        args: JSONObject,
    ): JSONObject {
        val registration = synchronized(lock) {
            activeRegistration(registrations[id.trim()].orEmpty())
        } ?: error("Unknown Aether mod service: $id")
        require(
            registration.descriptor.methods.any { it.name == method }
        ) {
            "Unknown method $method on Aether mod service ${registration.descriptor.id}."
        }
        return registration.handler.invoke(method, args)
    }

    fun listJson(): JSONObject = JSONObject().put(
        "services",
        JSONArray().apply {
            list().sortedBy(AetherModServiceDescriptor::id).forEach { descriptor ->
                put(descriptor.toJson())
            }
        },
    )

    fun describeJson(id: String): JSONObject =
        describe(id)?.toJson() ?: error("Unknown Aether mod service: $id")

    private fun activeRegistration(
        entries: List<Registration>,
    ): Registration? = entries.maxWithOrNull(
        compareBy<Registration> { it.descriptor.priority }.thenBy { it.sequence }
    )
}

data class AetherModOperationDecision(
    val payload: JSONObject,
    val cancelled: Boolean = false,
    val reason: String = "",
)

fun interface AetherModOperationInterceptor {
    suspend fun intercept(
        payload: JSONObject,
        context: JSONObject,
    ): AetherModOperationDecision
}

class AetherModOperationRegistry {
    private data class Registration(
        val sequence: Long,
        val operation: String,
        val owner: String,
        val priority: Int,
        val interceptor: AetherModOperationInterceptor,
    )

    private val lock = Any()
    private val sequence = AtomicLong()
    private val registrations = mutableListOf<Registration>()

    fun register(
        operation: String,
        owner: String,
        priority: Int = 0,
        interceptor: AetherModOperationInterceptor,
    ): () -> Unit {
        val normalizedOperation = operation.trim()
        require(normalizedOperation.isNotBlank()) {
            "Aether operation interceptors require an operation name."
        }
        val registration = Registration(
            sequence = sequence.incrementAndGet(),
            operation = normalizedOperation,
            owner = owner.trim().ifBlank { "unknown" },
            priority = priority,
            interceptor = interceptor,
        )
        synchronized(lock) {
            registrations += registration
        }
        return {
            synchronized(lock) {
                registrations.remove(registration)
            }
        }
    }

    fun hasInterceptors(operation: String): Boolean = synchronized(lock) {
        registrations.any { it.operation == operation || it.operation == "*" }
    }

    fun unregisterOwner(owner: String) {
        synchronized(lock) {
            registrations.removeAll { it.owner == owner }
        }
    }

    suspend fun intercept(
        operation: String,
        payload: JSONObject,
        context: JSONObject,
    ): AetherModOperationDecision {
        val interceptors = synchronized(lock) {
            registrations
                .filter { it.operation == operation || it.operation == "*" }
                .sortedWith(
                    compareBy<Registration> { it.priority }
                        .thenBy { it.sequence }
                )
        }
        if (interceptors.isEmpty()) {
            return AetherModOperationDecision(payload = payload)
        }
        var currentPayload = JSONObject(payload.toString())
        for (registration in interceptors) {
            val decision = registration.interceptor.intercept(
                JSONObject(currentPayload.toString()),
                context,
            )
            currentPayload = decision.payload
            if (decision.cancelled) {
                return decision.copy(payload = currentPayload)
            }
        }
        return AetherModOperationDecision(payload = currentPayload)
    }
}

class AetherModKernel {
    val services = AetherModServiceRegistry()
    val operations = AetherModOperationRegistry()
    val components = AetherNativeComponentRegistry()
    val toolTitles = com.zhousl.aether.mod.AetherNativeToolTitleRegistry()
}

private fun AetherModServiceDescriptor.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("description", description)
    put("owner", owner)
    put("priority", priority)
    put(
        "methods",
        JSONArray().apply {
            methods.forEach { method ->
                put(
                    JSONObject().apply {
                        put("name", method.name)
                        put("description", method.description)
                        put("mutates_state", method.mutatesState)
                    }
                )
            }
        },
    )
}
