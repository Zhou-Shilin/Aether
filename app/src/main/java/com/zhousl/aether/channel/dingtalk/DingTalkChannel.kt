package com.zhousl.aether.channel.dingtalk

import com.zhousl.aether.channel.BaseAetherChannel
import com.zhousl.aether.channel.ChannelAddress
import com.zhousl.aether.channel.ChannelConfig
import com.zhousl.aether.channel.ChannelConnectionState
import com.zhousl.aether.channel.ChannelFile
import com.zhousl.aether.channel.ChannelFileKind
import com.zhousl.aether.channel.ChannelIncomingMessage
import com.zhousl.aether.channel.ChannelKind
import com.zhousl.aether.channel.ChannelReply
import com.zhousl.aether.channel.ChannelSendReceipt
import com.zhousl.aether.channel.JsonMediaType
import com.zhousl.aether.channel.postJson
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** DingTalk Stream mode with QwenPaw-compatible emotions, AI cards, and file uploads. */
class DingTalkChannel(
    private val config: ChannelConfig,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) : BaseAetherChannel(ChannelKind.DingTalk) {
    private data class AiCard(val trackId: String)

    override val supportsStreamingReplies: Boolean =
        config.display.streamingEnabled &&
            config.robotCode.isNotBlank() &&
            config.cardTemplateId.isNotBlank()

    private var socket: WebSocket? = null
    private var opened = CompletableDeferred<Unit>()
    private val aiCards = ConcurrentHashMap<String, AiCard>()
    @Volatile private var accessToken = ""
    @Volatile private var accessTokenExpiresAt = 0L

    override suspend fun start() {
        if (!config.enabled) return updateStatus(ChannelConnectionState.Disabled)
        if (!config.isConfigured) return updateStatus(ChannelConnectionState.Error, "Client ID and secret are required")
        updateStatus(ChannelConnectionState.Starting)
        opened = CompletableDeferred()
        val gateway = http.postJson(
            "${config.baseUrl.trimEnd('/')}/v1.0/gateway/connections/open",
            JSONObject().put("clientId", config.appId).put("clientSecret", config.appSecret),
        )
        val endpoint = gateway.optString("endpoint")
        val ticket = gateway.optString("ticket")
        require(endpoint.isNotBlank() && ticket.isNotBlank()) { "DingTalk gateway returned no endpoint" }
        socket = http.newWebSocket(Request.Builder().url("$endpoint?ticket=$ticket").build(), listener)
        opened.await()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            updateStatus(ChannelConnectionState.Connected)
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
            val headers = frame.optJSONObject("headers") ?: JSONObject()
            when (headers.optString("type")) {
                "SYSTEM" -> if (headers.optString("topic").contains("KEEPALIVE")) {
                    webSocket.send(JSONObject().put("code", 200).put("headers", headers).put("message", "OK").toString())
                }
                "CALLBACK" -> {
                    val payload = runCatching { JSONObject(frame.optString("data")) }.getOrNull()
                        ?: frame.optJSONObject("data")
                        ?: return
                    val messageId = payload.optString("msgId").ifBlank { headers.optString("messageId") }
                    val textValue = payload.optJSONObject("text")?.optString("content").orEmpty().trim()
                    if (textValue.isNotBlank()) scope.launch {
                        emitIncoming(
                            ChannelIncomingMessage(
                                kind,
                                messageId.ifBlank { UUID.randomUUID().toString() },
                                ChannelAddress(
                                    conversationId = payload.optString("conversationId")
                                        .ifBlank { payload.optString("senderId") },
                                    userId = payload.optString("senderStaffId")
                                        .ifBlank { payload.optString("senderId") },
                                    replyToken = payload.optString("sessionWebhook"),
                                    attributes = mapOf(
                                        "conversationType" to payload.optString("conversationType", "1"),
                                    ),
                                ),
                                textValue,
                            )
                        )
                    }
                    webSocket.send(JSONObject().put("code", 200).put("headers", headers).put("message", "OK").toString())
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            updateStatus(ChannelConnectionState.Error, t.message.orEmpty())
            if (!opened.isCompleted) opened.completeExceptionally(t)
        }
    }

    override suspend fun stop() {
        socket?.close(1000, "Aether channel stopped")
        socket = null
        aiCards.clear()
        updateStatus(ChannelConnectionState.Disabled)
    }

    override suspend fun onProcessing(message: ChannelIncomingMessage) {
        sendEmotion(message, "🤔Thinking")
    }

    override suspend fun onCompleted(
        message: ChannelIncomingMessage,
        receipt: ChannelSendReceipt,
    ) {
        sendEmotion(message, "🤔Thinking", recall = true)
        sendEmotion(message, "🥳Done")
    }

    override suspend fun onFailed(message: ChannelIncomingMessage) {
        sendEmotion(message, "🤔Thinking", recall = true)
        sendEmotion(message, "☹️Error")
    }

    override suspend fun send(reply: ChannelReply): ChannelSendReceipt = withContext(Dispatchers.IO) {
        if (reply.text.isNotBlank()) {
            if (supportsStreamingReplies) sendAiCard(reply) else sendWebhookText(reply.address, reply.text)
        }
        reply.files.forEach { sendWebhookFile(reply.address, it) }
        ChannelSendReceipt()
    }

    private fun sendWebhookText(address: ChannelAddress, text: String) {
        require(address.replyToken.isNotBlank()) { "DingTalk message has no session webhook" }
        val body = JSONObject()
            .put("msgtype", "markdown")
            .put("markdown", JSONObject().put("title", "Aether").put("text", text))
        executeWebhook(address.replyToken, body)
    }

    private fun sendWebhookFile(address: ChannelAddress, payload: ChannelFile) {
        require(address.replyToken.isNotBlank()) { "DingTalk message has no session webhook" }
        val mediaId = uploadMedia(payload)
        val body = if (payload.kind == ChannelFileKind.Image) {
            JSONObject()
                .put("msgtype", "markdown")
                .put(
                    "markdown",
                    JSONObject().put("title", payload.name).put("text", "![${payload.name}]($mediaId)"),
                )
        } else {
            JSONObject()
                .put("msgtype", "file")
                .put(
                    "file",
                    JSONObject()
                        .put("mediaId", mediaId)
                        .put("fileType", payload.mimeType)
                        .put("fileName", payload.name),
                )
        }
        executeWebhook(address.replyToken, body)
    }

    private fun executeWebhook(webhook: String, body: JSONObject) {
        val request = Request.Builder()
            .url(webhook)
            .post(body.toString().toRequestBody(JsonMediaType))
            .build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) error("DingTalk send failed: HTTP ${it.code}")
        }
    }

    private fun uploadMedia(payload: ChannelFile): String {
        val type = if (payload.kind == ChannelFileKind.Image) "image" else "file"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "media",
                payload.name,
                payload.bytes.toRequestBody(payload.mimeType.toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("https://oapi.dingtalk.com/media/upload?access_token=${token()}&type=$type")
            .post(body)
            .build()
        return http.newCall(request).execute().use {
            val json = JSONObject(it.body?.string().orEmpty())
            if (!it.isSuccessful || json.optInt("errcode") != 0) {
                error("DingTalk media upload failed: ${json.optString("errmsg")}")
            }
            json.optString("media_id").ifBlank { error("DingTalk upload returned no media ID") }
        }
    }

    private fun sendAiCard(reply: ChannelReply) {
        val conversationId = reply.address.conversationId
        var card = aiCards[conversationId]
        if (card == null) {
            if (reply.isFinal) return sendWebhookText(reply.address, reply.text)
            val trackId = "aether-${UUID.randomUUID()}"
            val isGroup = reply.address.attributes["conversationType"] == "2"
            val openSpaceId = if (isGroup) {
                "dtv1.card//IM_GROUP.$conversationId"
            } else {
                "dtv1.card//IM_ROBOT.${reply.address.userId}"
            }
            val body = JSONObject()
                .put("cardTemplateId", config.cardTemplateId)
                .put("outTrackId", trackId)
                .put(
                    "cardData",
                    JSONObject().put(
                        "cardParamMap",
                        JSONObject().put(config.cardTemplateKey, ""),
                    ),
                )
                .put("callbackType", "STREAM")
                .put("openSpaceId", openSpaceId)
                .put("userIdType", 1)
            if (isGroup) {
                body.put(
                    "imGroupOpenDeliverModel",
                    JSONObject().put("robotCode", config.robotCode),
                )
            } else {
                body.put(
                    "imRobotOpenDeliverModel",
                    JSONObject().put("spaceType", "IM_ROBOT"),
                )
            }
            postOpenApi("/v1.0/card/instances/createAndDeliver", body)
            card = AiCard(trackId)
            aiCards[conversationId] = card
        }
        val body = JSONObject()
            .put("outTrackId", card.trackId)
            .put("guid", UUID.randomUUID().toString())
            .put("key", config.cardTemplateKey)
            .put("content", reply.text)
            .put("isFull", true)
            .put("isFinalize", reply.isFinal)
            .put("isError", false)
        putOpenApi("/v1.0/card/streaming", body)
        if (reply.isFinal) aiCards.remove(conversationId)
    }

    private suspend fun sendEmotion(
        message: ChannelIncomingMessage,
        name: String,
        recall: Boolean = false,
    ) {
        val robotCode = config.robotCode.ifBlank { config.appId }
        if (robotCode.isBlank() || message.messageId.isBlank()) return
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("robotCode", robotCode)
                .put("openMsgId", message.messageId)
                .put("openConversationId", message.address.conversationId)
                .put("emotionType", 2)
                .put("emotionName", name)
            if (recall) {
                body.put(
                    "textEmotion",
                    JSONObject()
                        .put("emotionId", "2659900")
                        .put("emotionName", name)
                        .put("text", name)
                        .put("backgroundId", "im_bg_1"),
                )
            }
            runCatching {
                if (recall) {
                    postOpenApi("/v1.0/robot/emotion/recall", body)
                } else {
                    postOpenApi("/v1.0/robot/emotion/reply", body)
                }
            }
        }
    }

    private fun token(): String = synchronized(this) {
        val now = System.currentTimeMillis()
        if (accessToken.isNotBlank() && now < accessTokenExpiresAt) return@synchronized accessToken
        val body = JSONObject()
            .put("appKey", config.appId)
            .put("appSecret", config.appSecret)
            .toString()
            .toRequestBody(JsonMediaType)
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/v1.0/oauth2/accessToken")
            .post(body)
            .build()
        val response = http.newCall(request).execute().use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) error("DingTalk OAuth failed: HTTP ${it.code}")
            JSONObject(text)
        }
        accessToken = response.optString("accessToken")
        require(accessToken.isNotBlank()) { "DingTalk OAuth returned no access token" }
        accessTokenExpiresAt = now + (response.optLong("expireIn", 7_200) - 120).coerceAtLeast(60) * 1_000
        accessToken
    }

    private fun postOpenApi(path: String, body: JSONObject): JSONObject =
        executeOpenApi("POST", path, body)

    private fun putOpenApi(path: String, body: JSONObject): JSONObject =
        executeOpenApi("PUT", path, body)

    private fun executeOpenApi(method: String, path: String, body: JSONObject): JSONObject {
        val requestBody = body.toString().toRequestBody(JsonMediaType)
        val builder = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}$path")
            .header("x-acs-dingtalk-access-token", token())
        val request = when (method) {
            "PUT" -> builder.put(requestBody).build()
            else -> builder.post(requestBody).build()
        }
        return http.newCall(request).execute().use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) error("DingTalk OpenAPI failed: HTTP ${it.code}")
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}
