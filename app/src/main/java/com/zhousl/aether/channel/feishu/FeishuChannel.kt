package com.zhousl.aether.channel.feishu

import com.lark.oapi.Client
import com.lark.oapi.core.request.RequestOptions
import com.lark.oapi.event.EventDispatcher
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReq
import com.lark.oapi.service.cardkit.v1.model.ContentCardElementReqBody
import com.lark.oapi.service.cardkit.v1.model.CreateCardReq
import com.lark.oapi.service.cardkit.v1.model.CreateCardReqBody
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReq
import com.lark.oapi.service.cardkit.v1.model.SettingsCardReqBody
import com.lark.oapi.service.im.ImService
import com.lark.oapi.service.im.v1.model.CreateFileReq
import com.lark.oapi.service.im.v1.model.CreateFileReqBody
import com.lark.oapi.service.im.v1.model.CreateImageReq
import com.lark.oapi.service.im.v1.model.CreateImageReqBody
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReq
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReqBody
import com.lark.oapi.service.im.v1.model.CreateMessageReq
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody
import com.lark.oapi.service.im.v1.model.Emoji
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1
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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Feishu long connection with reactions, CardKit streaming, and native file delivery. */
class FeishuChannel(
    private val config: ChannelConfig,
    private val scope: CoroutineScope,
) : BaseAetherChannel(ChannelKind.Feishu) {
    private data class StreamingCard(
        val cardId: String,
        val messageId: String,
        var sequence: Int = 0,
    )

    override val supportsStreamingReplies: Boolean = config.display.streamingEnabled
    private val apiClient = Client.newBuilder(config.appId, config.appSecret)
        .openBaseUrl(config.baseUrl.trimEnd('/'))
        .build()
    private val streamingCards = ConcurrentHashMap<String, StreamingCard>()
    private var receiveJob: Job? = null
    private var socketClient: com.lark.oapi.ws.Client? = null

    override suspend fun start() {
        if (!config.enabled) return updateStatus(ChannelConnectionState.Disabled)
        if (!config.isConfigured) return updateStatus(ChannelConnectionState.Error, "App ID and secret are required")
        updateStatus(ChannelConnectionState.Starting)
        val dispatcher = EventDispatcher.newBuilder("", "")
            .onP2MessageReceiveV1(
                object : ImService.P2MessageReceiveV1Handler() {
                    override fun handle(envelope: P2MessageReceiveV1) {
                        val event = envelope.event
                        val message = event?.message
                        val sender = event?.sender?.senderId?.openId.orEmpty()
                        if (message?.messageType == "text") {
                            val text = runCatching {
                                JSONObject(message.content.orEmpty()).optString("text")
                            }.getOrDefault("")
                            if (text.isNotBlank()) scope.launch {
                                emitIncoming(
                                    ChannelIncomingMessage(
                                        channel = kind,
                                        messageId = message.messageId.orEmpty(),
                                        address = ChannelAddress(message.chatId.orEmpty(), sender),
                                        text = text,
                                    )
                                )
                            }
                        }
                    }
                }
            )
            .build()
        socketClient = com.lark.oapi.ws.Client.Builder(config.appId, config.appSecret)
            .eventHandler(dispatcher)
            .domain(config.baseUrl.trimEnd('/'))
            .build()
        receiveJob = scope.launch(Dispatchers.IO) {
            runCatching {
                updateStatus(ChannelConnectionState.Connected)
                socketClient?.start()
            }.onFailure { updateStatus(ChannelConnectionState.Error, it.message.orEmpty()) }
        }
    }

    override suspend fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        socketClient?.close()
        socketClient = null
        streamingCards.clear()
        updateStatus(ChannelConnectionState.Disabled)
    }

    override suspend fun onProcessing(message: ChannelIncomingMessage) {
        addReaction(message.messageId, "Typing")
    }

    override suspend fun onCompleted(
        message: ChannelIncomingMessage,
        receipt: ChannelSendReceipt,
    ) {
        receipt.messageId.takeIf(String::isNotBlank)?.let { addReaction(it, "DONE") }
    }

    override suspend fun send(reply: ChannelReply): ChannelSendReceipt = withContext(Dispatchers.IO) {
        var receipt = ChannelSendReceipt()
        if (reply.text.isNotBlank()) {
            receipt = if (supportsStreamingReplies) sendStreamingText(reply) else sendText(reply.address, reply.text)
        }
        reply.files.forEach { receipt = sendFile(reply.address, it) }
        receipt
    }

    private fun sendText(address: ChannelAddress, text: String): ChannelSendReceipt =
        createMessage(address, "text", JSONObject().put("text", text).toString())

    private fun sendStreamingText(reply: ChannelReply): ChannelSendReceipt {
        val key = reply.address.conversationId
        var card = streamingCards[key]
        if (card == null) {
            if (reply.isFinal) return sendText(reply.address, reply.text)
            val cardJson = JSONObject()
                .put("schema", "2.0")
                .put("config", JSONObject().put("streaming_mode", true))
                .put(
                    "body",
                    JSONObject().put(
                        "elements",
                        JSONArray().put(
                            JSONObject()
                                .put("tag", "markdown")
                                .put("content", reply.text)
                                .put("element_id", StreamingElementId)
                        ),
                    ),
                )
            val create = CreateCardReq.newBuilder()
                .createCardReqBody(
                    CreateCardReqBody.newBuilder()
                        .type("card_json")
                        .data(cardJson.toString())
                        .build()
                )
                .build()
            val created = apiClient.cardkit().v1().card().create(create)
            if (!created.success()) error("Feishu CardKit create failed: ${created.msg}")
            val cardId = created.data?.cardId.orEmpty()
            require(cardId.isNotBlank()) { "Feishu CardKit returned no card ID" }
            val message = createMessage(
                reply.address,
                "interactive",
                JSONObject().put("type", "card")
                    .put("data", JSONObject().put("card_id", cardId))
                    .toString(),
            )
            card = StreamingCard(cardId, message.messageId)
            streamingCards[key] = card
            return message
        }

        card.sequence += 1
        val update = ContentCardElementReq.newBuilder()
            .cardId(card.cardId)
            .elementId(StreamingElementId)
            .contentCardElementReqBody(
                ContentCardElementReqBody.newBuilder()
                    .uuid(UUID.randomUUID().toString())
                    .content(JSONObject().put("content", reply.text).toString())
                    .sequence(card.sequence)
                    .build()
            )
            .build()
        val updated = apiClient.cardkit().v1().cardElement().content(update)
        if (!updated.success()) error("Feishu CardKit update failed: ${updated.msg}")

        if (reply.isFinal) {
            card.sequence += 1
            val settings = JSONObject()
                .put("config", JSONObject().put("streaming_mode", false))
                .put("summary", JSONObject().put("content", reply.text.take(120)))
            val finish = SettingsCardReq.newBuilder()
                .cardId(card.cardId)
                .settingsCardReqBody(
                    SettingsCardReqBody.newBuilder()
                        .settings(settings.toString())
                        .uuid(UUID.randomUUID().toString())
                        .sequence(card.sequence)
                        .build()
                )
                .build()
            val finished = apiClient.cardkit().v1().card().settings(finish)
            if (!finished.success()) error("Feishu CardKit finalize failed: ${finished.msg}")
            streamingCards.remove(key)
        }
        return ChannelSendReceipt(card.messageId)
    }

    private fun createMessage(
        address: ChannelAddress,
        messageType: String,
        content: String,
    ): ChannelSendReceipt {
        val request = CreateMessageReq.newBuilder()
            .receiveIdType("chat_id")
            .createMessageReqBody(
                CreateMessageReqBody.newBuilder()
                    .receiveId(address.conversationId)
                    .msgType(messageType)
                    .content(content)
                    .build()
            )
            .build()
        val response = apiClient.im().message().create(request, RequestOptions.newBuilder().build())
        if (!response.success()) error("Feishu send failed: ${response.msg}")
        return ChannelSendReceipt(response.data?.messageId.orEmpty())
    }

    private fun sendFile(address: ChannelAddress, payload: ChannelFile): ChannelSendReceipt {
        val suffix = payload.name.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let { ".$it" }
            ?: ".bin"
        val temporary = File.createTempFile("aether-channel-", suffix)
        return try {
            temporary.writeBytes(payload.bytes)
            if (payload.kind == ChannelFileKind.Image && payload.bytes.size <= 10 * 1024 * 1024) {
                val request = CreateImageReq.newBuilder()
                    .createImageReqBody(
                        CreateImageReqBody.newBuilder()
                            .imageType("message")
                            .image(temporary)
                            .build()
                    )
                    .build()
                val response = apiClient.im().image().create(request)
                if (!response.success()) error("Feishu image upload failed: ${response.msg}")
                createMessage(
                    address,
                    "image",
                    JSONObject().put("image_key", response.data?.imageKey.orEmpty()).toString(),
                )
            } else {
                val request = CreateFileReq.newBuilder()
                    .createFileReqBody(
                        CreateFileReqBody.newBuilder()
                            .fileType(feishuFileType(payload))
                            .fileName(payload.name)
                            .file(temporary)
                            .build()
                    )
                    .build()
                val response = apiClient.im().file().create(request)
                if (!response.success()) error("Feishu file upload failed: ${response.msg}")
                createMessage(
                    address,
                    if (payload.kind == ChannelFileKind.Audio) "audio" else "file",
                    JSONObject().put("file_key", response.data?.fileKey.orEmpty()).toString(),
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun feishuFileType(payload: ChannelFile): String =
        when (payload.name.substringAfterLast('.', "").lowercase()) {
            "opus", "ogg" -> "opus"
            "mp4" -> "mp4"
            "pdf" -> "pdf"
            "doc", "docx" -> "doc"
            "xls", "xlsx" -> "xls"
            "ppt", "pptx" -> "ppt"
            else -> "stream"
        }

    private suspend fun addReaction(messageId: String, emojiType: String) {
        if (messageId.isBlank()) return
        withContext(Dispatchers.IO) {
            val request = CreateMessageReactionReq.newBuilder()
                .messageId(messageId)
                .createMessageReactionReqBody(
                    CreateMessageReactionReqBody.newBuilder()
                        .reactionType(Emoji.newBuilder().emojiType(emojiType).build())
                        .build()
                )
                .build()
            val response = apiClient.im().messageReaction().create(request)
            if (!response.success()) error("Feishu reaction failed: ${response.msg}")
        }
    }

    private companion object {
        const val StreamingElementId = "aether_streaming_content"
    }
}
