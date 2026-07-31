package com.zhousl.aether.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPiBridgeClientTest {
    @Test
    fun dispatchesEventsBeforeCompletingResponse() = runTest {
        val process = ProtocolFakeProcess()
        val client = SharedPiBridgeClient(
            transport = FakeBridgeTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val events = mutableListOf<String>()

        val response = client.request("login_provider", onEvent = { event, _ -> events += event })

        assertEquals(listOf("auth_progress"), events)
        assertEquals("ready", response["status"]?.jsonPrimitive?.content)
        client.close()
    }

    @Test
    fun allowsEventHandlerToSendNestedBridgeRequest() = runTest {
        val process = NestedRequestFakeProcess()
        val client = SharedPiBridgeClient(
            transport = FakeBridgeTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val response = client.request(
            type = "run_turn",
            onEvent = { event, _ ->
                assertEquals("host_tool_request", event)
                client.request("host_tool_result", abortOnCancellation = false)
            },
        )

        assertEquals("complete", response["status"]?.jsonPrimitive?.content)
        client.close()
    }

    @Test
    fun reportsBridgeSetupPhasesWhileStartingAndPinging() = runTest {
        val process = ProtocolFakeProcess()
        val client = SharedPiBridgeClient(
            transport = FakeBridgeTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val phases = mutableListOf<PiBridgeSetupPhase>()

        client.ping { phases += it }

        assertEquals(
            listOf(
                PiBridgeSetupPhase.PreparingBridge,
                PiBridgeSetupPhase.StartingBridge,
                PiBridgeSetupPhase.VerifyingBridge,
            ),
            phases,
        )
        client.close()
    }

    @Test
    fun restartsBridgeAndRetriesFrameWhenStdinIsRejected() = runTest {
        val replacement = ProtocolFakeProcess()
        val transport = RestartingBridgeTransport(RejectedStdinFakeProcess(), replacement)
        val client = SharedPiBridgeClient(
            transport = transport,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val response = client.request("ping")

        assertEquals("ready", response["status"]?.jsonPrimitive?.content)
        assertEquals(2, transport.startCount)
        assertEquals(1, transport.stopCount)
        client.close()
    }

    @Test
    fun listProvidersDoesNotStartBridgeWhenDisabled() = runTest {
        val transport = CountingBridgeTransport()
        val client = SharedPiBridgeClient(
            transport = transport,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val failure = assertFailsWith<PiBridgeRequestException> {
            client.listProviders(startIfNeeded = false)
        }

        assertEquals("pi_bridge_not_running", failure.code)
        assertEquals(0, transport.startCount)
        client.close()
    }

    @Test
    fun requestWithoutDeadlineCanCompleteAfterTenMinutes() = runTest {
        val process = DeferredResponseFakeProcess()
        val client = SharedPiBridgeClient(
            transport = FakeBridgeTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val request = async {
            client.request(type = "run_turn", timeoutMillis = null)
        }
        runCurrent()
        advanceTimeBy(10 * 60_000L + 1)
        runCurrent()

        assertFalse(request.isCompleted)
        process.respond(buildJsonObject { put("status", "complete") })
        runCurrent()
        assertEquals("complete", request.await()["status"]?.jsonPrimitive?.content)
        client.close()
    }

    @Test
    fun processExitFailsPendingRequest() = runTest {
        val process = DeferredResponseFakeProcess()
        val client = SharedPiBridgeClient(
            transport = FakeBridgeTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val request = async {
            runCatching { client.request(type = "run_turn", timeoutMillis = null) }
        }
        runCurrent()
        process.exit(exitCode = 9)
        runCurrent()

        val failure = request.await().exceptionOrNull() as? PiBridgeRequestException
            ?: error("Expected the pending request to fail when the bridge exits.")
        assertEquals("Pi Bridge exited with code 9.", failure.message)
        client.close()
    }
}

private class FakeBridgeTransport(
    private val process: RuntimeProcess,
) : PiBridgeTransport {
    override suspend fun start(): RuntimeProcess = process
    override suspend fun stop() = Unit
}

private class CountingBridgeTransport : PiBridgeTransport {
    var startCount = 0

    override suspend fun start(): RuntimeProcess {
        startCount++
        return ProtocolFakeProcess()
    }

    override suspend fun stop() = Unit
}

private class RestartingBridgeTransport(
    first: RuntimeProcess,
    private val replacement: RuntimeProcess,
) : PiBridgeTransport {
    private var current: RuntimeProcess? = first
    var startCount = 0
    var stopCount = 0

    override suspend fun start(): RuntimeProcess {
        startCount++
        return current ?: replacement.also { current = it }
    }

    override suspend fun stop() {
        stopCount++
        current = null
    }
}

private class RejectedStdinFakeProcess : RuntimeProcess {
    override val pid = 33
    override val stdout: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()
    override val stderr: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        throw RuntimeProcessStdinException(pid, "Alpine process $pid rejected stdin.")
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = CompletableDeferred<RuntimeProcessExit>().await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit
}

private class DeferredResponseFakeProcess : RuntimeProcess {
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    private val errorOutput = Channel<ByteArray>(Channel.UNLIMITED)
    private val exit = CompletableDeferred<RuntimeProcessExit>()
    private var requestId = ""
    override val pid = 41
    override val stdout: Flow<ByteArray> = output.receiveAsFlow()
    override val stderr: Flow<ByteArray> = errorOutput.receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        val request = Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
        requestId = request["id"]!!.jsonPrimitive.content
    }

    suspend fun respond(payload: JsonObject) {
        output.send(
            (buildJsonObject {
                put("type", "response")
                put("id", requestId)
                put("ok", true)
                put("payload", payload)
            }.toString() + "\n").encodeToByteArray(),
        )
    }

    fun exit(exitCode: Int) {
        output.close()
        errorOutput.close()
        exit.complete(RuntimeProcessExit(exitCode))
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = exit.await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit
}

private class ProtocolFakeProcess : RuntimeProcess {
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    override val pid: Int = 7
    override val stdout: Flow<ByteArray> = output.receiveAsFlow()
    override val stderr: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        val request = Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
        val id = request["id"]!!.jsonPrimitive.content
        output.send(frame("event", id, buildJsonObject { put("message", "working") }, "auth_progress"))
        output.send(frame("response", id, buildJsonObject { put("status", "ready") }))
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = CompletableDeferred<RuntimeProcessExit>().await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit

    private fun frame(type: String, id: String, payload: JsonObject, event: String = ""): ByteArray =
        (buildJsonObject {
            put("type", type)
            put("id", id)
            put("ok", true)
            if (event.isNotBlank()) put("event", event)
            put("payload", payload)
        }.toString() + "\n").encodeToByteArray()
}

private class NestedRequestFakeProcess : RuntimeProcess {
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    private var outerRequestId = ""
    override val pid: Int = 8
    override val stdout: Flow<ByteArray> = output.receiveAsFlow()
    override val stderr: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        val request = Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
        val id = request["id"]!!.jsonPrimitive.content
        when (request["type"]!!.jsonPrimitive.content) {
            "run_turn" -> {
                outerRequestId = id
                output.send(frame("event", id, JsonObject(emptyMap()), "host_tool_request"))
            }
            "host_tool_result" -> {
                output.send(frame("response", id, buildJsonObject { put("accepted", true) }))
                output.send(frame("response", outerRequestId, buildJsonObject { put("status", "complete") }))
            }
        }
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = CompletableDeferred<RuntimeProcessExit>().await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit

    private fun frame(type: String, id: String, payload: JsonObject, event: String = ""): ByteArray =
        (buildJsonObject {
            put("type", type)
            put("id", id)
            put("ok", true)
            if (event.isNotBlank()) put("event", event)
            put("payload", payload)
        }.toString() + "\n").encodeToByteArray()
}
