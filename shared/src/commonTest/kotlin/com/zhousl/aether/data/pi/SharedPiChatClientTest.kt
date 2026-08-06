package com.zhousl.aether.data.pi

import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.runtime.PiBridgeTransport
import com.zhousl.aether.runtime.RuntimeProcess
import com.zhousl.aether.runtime.RuntimeProcessExit
import com.zhousl.aether.runtime.RuntimeProcessSignal
import com.zhousl.aether.runtime.SharedPiBridgeClient
import com.zhousl.aether.runtime.SharedExtensionLoadOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPiChatClientTest {
    @Test
    fun modelConfigUsesReliabilityTimeoutAndAndroidBounds() {
        assertEquals("30000", testProvider().toSharedPiModelConfig(1)["timeout_ms"].toString())
        assertEquals("90000", testProvider().toSharedPiModelConfig(90_000)["timeout_ms"].toString())
        assertEquals("3600000", testProvider().toSharedPiModelConfig(Int.MAX_VALUE)["timeout_ms"].toString())
    }

    @Test
    fun modelConfigEnablesPiReasoningForAnActiveThinkingTurn() {
        assertEquals(
            "true",
            testProvider().toSharedPiModelConfig(reasoningEnabled = true)["reasoning"].toString(),
        )
        assertEquals(
            "false",
            testProvider().toSharedPiModelConfig(reasoningEnabled = false)["reasoning"].toString(),
        )
        assertEquals(
            "false",
            testProvider().copy(piProviderId = "openai")
                .toSharedPiModelConfig(reasoningEnabled = true)["reasoning"].toString(),
        )
    }

    @Test
    fun sendsMultimodalContentAndParsesUsage() = runTest {
        val process = ChatProtocolProcess()
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val client = SharedPiChatClient(bridge)

        val result = client.runTurn(
            config = testProvider(),
            messages = listOf(
                SharedPiChatMessage(
                    role = "user",
                    text = "describe",
                    images = listOf(SharedPiImage("image/png", "AQID")),
                    providerPayload = buildJsonObject {
                        put("provider", "persisted-provider")
                    },
                )
            ),
            sessionId = "session-1",
        )

        val request = process.requests.single()
        val payload = request["payload"]!!.jsonObject
        val content = payload["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", content[1].jsonObject["mime_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", content[1].jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals(
            "persisted-provider",
            payload["messages"]!!.jsonArray.single().jsonObject["provider_payload"]!!
                .jsonObject["provider"]!!.jsonPrimitive.content,
        )
        assertEquals(7, result.usage.inputTokens)
        assertEquals(11, result.usage.outputTokens)
        assertEquals(18, result.usage.totalTokens)
        assertTrue(result.usageAvailable)
        val providerPayload = Json.parseToJsonElement(result.providerPayloadJson).jsonObject
        assertEquals("assistant", providerPayload["piAssistantMessage"]!!.jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("response-1", providerPayload["responseId"]!!.jsonPrimitive.content)
        assertEquals(1, providerPayload["usage"]!!.jsonObject["request_count"]!!.jsonPrimitive.content.toInt())
        bridge.close()
    }

    @Test
    fun oneShotCompletionUsesAndroidCompleteOnceProtocol() = runTest {
        val process = ChatProtocolProcess()
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = SharedPiChatClient(bridge).completeOnce(
            config = testProvider(),
            messages = listOf(SharedPiChatMessage("user", "Name this chat")),
            systemPrompt = "Return a short title.",
        )

        val request = process.requests.single()
        assertEquals("complete_once", request["type"]!!.jsonPrimitive.content)
        val payload = request["payload"]!!.jsonObject
        assertEquals("Return a short title.", payload["system_prompt"]!!.jsonPrimitive.content)
        assertEquals("false", payload["stream"]!!.jsonPrimitive.content)
        assertEquals("off", payload["reasoning"]!!.jsonPrimitive.content)
        assertEquals("done", result.assistantText)
        bridge.close()
    }

    @Test
    fun steerTargetsRunningSession() = runTest {
        val process = ChatProtocolProcess()
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val client = SharedPiChatClient(bridge)

        assertTrue(client.steer("session-2", SharedPiChatMessage("user", "change direction")))

        val payload = process.requests.single()["payload"]!!.jsonObject
        assertEquals("session-2", payload["session_id"]!!.jsonPrimitive.content)
        val content = payload["message"]!!.jsonObject["content"]!!.jsonArray
        assertEquals("text", content.single().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("change direction", content.single().jsonObject["text"]!!.jsonPrimitive.content)
        bridge.close()
    }

    @Test
    fun rejectedInjectedSteerContinuesAsFollowUpInTheSameRun() = runTest {
        val process = ChatProtocolProcess(steerAccepted = false)
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        var pending = listOf(SharedPiChatMessage("user", "late direction"))

        val result = SharedPiChatClient(bridge).runTurn(
            config = testProvider(),
            messages = listOf(SharedPiChatMessage("user", "start")),
            sessionId = "session-follow-up",
            pollInjectedUserMessages = {
                pending.also { pending = emptyList() }
            },
        )

        assertEquals(listOf("run_turn", "steer", "follow_up"), process.requests.map {
            it["type"]!!.jsonPrimitive.content
        })
        assertEquals("continued", result.assistantText)
        val followUpPayload = process.requests.last()["payload"]!!.jsonObject
        assertEquals("session-follow-up", followUpPayload["session_id"]!!.jsonPrimitive.content)
        assertEquals(
            "late direction",
            followUpPayload["message"]!!.jsonObject["content"]!!.jsonArray.single()
                .jsonObject["text"]!!.jsonPrimitive.content,
        )
        bridge.close()
    }

    @Test
    fun sendsPersistedExtensionLoadOptionsWithEveryTurn() = runTest {
        val process = ChatProtocolProcess()
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
            extensionLoadOptionsProvider = {
                SharedExtensionLoadOptions(
                    disabledExtensionPaths = setOf("/root/.aether/extensions/local.ts"),
                    disabledPackageSources = setOf("npm:disabled-extension"),
                )
            },
        )

        SharedPiChatClient(bridge).runTurn(
            config = testProvider(),
            messages = listOf(SharedPiChatMessage("user", "hello")),
            sessionId = "session-options",
        )

        val payload = process.requests.single()["payload"]!!.jsonObject
        assertEquals(
            "/root/.aether/extensions/local.ts",
            payload["disabled_extension_paths"]!!.jsonArray.single().jsonPrimitive.content,
        )
        assertEquals(
            "npm:disabled-extension",
            payload["disabled_package_sources"]!!.jsonArray.single().jsonPrimitive.content,
        )
        bridge.close()
    }

    @Test
    fun returnsAndPersistsRefreshedOAuthCredential() = runTest {
        val credential = buildJsonObject {
            put("type", "oauth")
            put("access", "new-access-token")
            put("refresh", "refresh-token")
        }
        val process = ChatProtocolProcess(oauthCredential = credential)
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val updates = mutableListOf<Pair<String, String>>()
        val client = SharedPiChatClient(
            bridge = bridge,
            onOAuthCredentialUpdated = { configId, credentialJson ->
                updates += configId to credentialJson
            },
        )

        val result = client.runTurn(
            config = testProvider().copy(id = "oauth-config"),
            messages = listOf(SharedPiChatMessage("user", "hello")),
            sessionId = "oauth-session",
        )

        assertEquals(credential.toString(), result.updatedOauthCredentialJson)
        assertEquals(listOf("oauth-config" to credential.toString()), updates)
        bridge.close()
    }

    @Test
    fun dispatchesProviderReasoningSummaryDeltasSeparately() = runTest {
        val process = ChatProtocolProcess(reasoningSummaryDelta = "Checking inputs")
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val rawDeltas = mutableListOf<String>()
        val summaryDeltas = mutableListOf<String>()

        SharedPiChatClient(bridge).runTurn(
            config = testProvider(),
            messages = listOf(SharedPiChatMessage("user", "hello")),
            sessionId = "summary-session",
            onAssistantReasoningDelta = rawDeltas::add,
            onAssistantReasoningSummaryDelta = summaryDeltas::add,
        )

        assertEquals(listOf("Checking inputs"), rawDeltas)
        assertEquals(listOf("Checking inputs"), summaryDeltas)
        bridge.close()
    }

    @Test
    fun reportsAndroidStreamingStatusesAndClearsThemAtCompletion() = runTest {
        val process = ChatProtocolProcess(assistantErrorEvent = "provider disconnected")
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val statuses = mutableListOf<SharedPiStreamingStatus?>()

        SharedPiChatClient(bridge).runTurn(
            config = testProvider(),
            messages = listOf(SharedPiChatMessage("user", "hello")),
            sessionId = "status-session",
            onStreamingStatus = statuses::add,
        )

        assertEquals("Thinking", statuses[0]?.text)
        assertEquals("Aether is working on this turn.", statuses[0]?.detail)
        assertEquals("Agent engine error", statuses[1]?.text)
        assertEquals("provider disconnected", statuses[1]?.detail)
        assertNull(statuses[2])
        bridge.close()
    }

    @Test
    fun turnCanContinuePastTheBridgeDefaultTenMinuteDeadline() = runTest {
        val process = DeferredChatProtocolProcess()
        val bridge = SharedPiBridgeClient(
            transport = SingleProcessTransport(process),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val turn = async {
            SharedPiChatClient(bridge).runTurn(
                config = testProvider(),
                messages = listOf(SharedPiChatMessage("user", "long task")),
                sessionId = "long-running-session",
            )
        }
        runCurrent()
        advanceTimeBy(10 * 60_000L + 1)
        runCurrent()

        assertFalse(turn.isCompleted)
        assertEquals(
            listOf("run_turn"),
            process.requests.map { it["type"]!!.jsonPrimitive.content },
        )
        process.respondToFirstRequest()
        runCurrent()
        assertEquals("done", turn.await().assistantText)
        bridge.close()
    }
}

private class SingleProcessTransport(private val process: RuntimeProcess) : PiBridgeTransport {
    override suspend fun start(): RuntimeProcess = process
    override suspend fun stop() = Unit
}

private class DeferredChatProtocolProcess : RuntimeProcess {
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    val requests = mutableListOf<JsonObject>()
    override val pid = 22
    override val stdout: Flow<ByteArray> = output.receiveAsFlow()
    override val stderr: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        requests += Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
    }

    suspend fun respondToFirstRequest() {
        val id = requests.first()["id"]!!.jsonPrimitive.content
        output.send(
            (buildJsonObject {
                put("type", "response")
                put("id", id)
                put("ok", true)
                put("payload", buildJsonObject {
                    put("assistant_text", "done")
                    put("provider", "test-provider")
                    put("model", "test-model")
                })
            }.toString() + "\n").encodeToByteArray(),
        )
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = CompletableDeferred<RuntimeProcessExit>().await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit
}

private class ChatProtocolProcess(
    private val reasoningSummaryDelta: String? = null,
    private val assistantErrorEvent: String? = null,
    private val oauthCredential: JsonObject? = null,
    private val steerAccepted: Boolean = true,
) : RuntimeProcess {
    private val output = Channel<ByteArray>(Channel.UNLIMITED)
    val requests = mutableListOf<JsonObject>()
    override val pid: Int = 21
    override val stdout: Flow<ByteArray> = output.receiveAsFlow()
    override val stderr: Flow<ByteArray> = Channel<ByteArray>().receiveAsFlow()

    override suspend fun writeStdin(bytes: ByteArray) {
        val request = Json.parseToJsonElement(bytes.decodeToString().trim()).jsonObject
        requests += request
        val id = request["id"]!!.jsonPrimitive.content
        val type = request["type"]!!.jsonPrimitive.content
        val payload = if (type == "steer") {
            buildJsonObject { put("accepted", steerAccepted) }
        } else {
            buildJsonObject {
                put("assistant_text", if (type == "follow_up") "continued" else "done")
                put("assistant_message", buildJsonObject {
                    put("role", "assistant")
                    put("content", JsonArray(emptyList()))
                })
                put("provider", "test-provider")
                put("model", "test-model")
                put("response_id", "response-1")
                put("stop_reason", "stop")
                oauthCredential?.let { put("oauth_credential", it) }
                put("usage", buildJsonObject {
                    put("input_tokens", 7)
                    put("output_tokens", 11)
                    put("total_tokens", 18)
                })
            }
        }
        reasoningSummaryDelta?.takeIf { type == "run_turn" }?.let { delta ->
            output.send((buildJsonObject {
                put("type", "event")
                put("id", id)
                put("event", "assistant_reasoning_delta")
                put("payload", buildJsonObject {
                    put("delta", delta)
                    put("kind", "summary")
                })
            }.toString() + "\n").encodeToByteArray())
        }
        assistantErrorEvent?.takeIf { type == "run_turn" }?.let { error ->
            output.send((buildJsonObject {
                put("type", "event")
                put("id", id)
                put("event", "assistant_error")
                put("payload", buildJsonObject { put("error_message", error) })
            }.toString() + "\n").encodeToByteArray())
        }
        output.send((buildJsonObject {
            put("type", "response")
            put("id", id)
            put("ok", true)
            put("payload", payload)
        }.toString() + "\n").encodeToByteArray())
    }

    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit(): RuntimeProcessExit = CompletableDeferred<RuntimeProcessExit>().await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit
}

private fun testProvider() = LlmProviderConfig(
    providerId = "test",
    name = "Test",
    piProviderId = "openai-compatible",
    apiKey = "key",
    baseUrl = "https://example.com/v1",
    modelId = "test-model",
)
