package com.zhousl.aether.channel.wecom

import android.util.Base64
import com.zhousl.aether.channel.BaseAetherChannel
import com.zhousl.aether.channel.ChannelAddress
import com.zhousl.aether.channel.ChannelConfig
import com.zhousl.aether.channel.ChannelConnectionState
import com.zhousl.aether.channel.ChannelFile
import com.zhousl.aether.channel.ChannelFileKind
import com.zhousl.aether.channel.ChannelInboundFileStore
import com.zhousl.aether.channel.ChannelIncomingAttachment
import com.zhousl.aether.channel.ChannelIncomingMessage
import com.zhousl.aether.channel.ChannelKind
import com.zhousl.aether.channel.ChannelReply
import com.zhousl.aether.channel.ChannelSendReceipt
import com.zhousl.aether.channel.awaitResponse
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** WeCom intelligent-bot WebSocket with streaming replies and chunked media upload. */
class WeComChannel(
    private val config: ChannelConfig,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val inboundFileStore: ChannelInboundFileStore,
) : BaseAetherChannel(ChannelKind.WeCom) {
    override val supportsStreamingReplies: Boolean = config.display.streamingEnabled
    private var socket: WebSocket? = null
    private val requestIds = ConcurrentHashMap<String, String>()
    private val activeStreams = ConcurrentHashMap<String, String>()
    private val pendingCommands = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var heartbeatJob: Job? = null

    override suspend fun start() {
        if (!config.enabled) return updateStatus(ChannelConnectionState.Disabled)
        if (!config.isConfigured) return updateStatus(ChannelConnectionState.Error, "Bot ID and secret are required")
        updateStatus(ChannelConnectionState.Starting)
        val request = Request.Builder()
            .url(config.baseUrl)
            .header("x-wecom-bot-id", config.appId)
            .header("x-wecom-bot-secret", config.appSecret)
            .build()
        socket = http.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(
                JSONObject().put("cmd", "aibot_subscribe")
                    .put(
                        "headers",
                        JSONObject().put("req_id", "aibot_subscribe_${UUID.randomUUID()}"),
                    )
                    .put("body", JSONObject().put("bot_id", config.appId).put("secret", config.appSecret))
                    .toString()
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
            val headers = frame.optJSONObject("headers") ?: JSONObject()
            val command = frame.optString("cmd")
            val requestId = headers.optString("req_id")
            pendingCommands.remove(requestId)?.let { pending ->
                if (frame.optInt("errcode") == 0) {
                    pending.complete(frame)
                } else {
                    pending.completeExceptionally(
                        IllegalStateException(frame.optString("errmsg").ifBlank { "WeCom command failed" }),
                    )
                }
                return
            }
            if (command.isBlank() && requestId.startsWith("aibot_subscribe")) {
                if (frame.optInt("errcode") == 0) {
                    updateStatus(ChannelConnectionState.Connected)
                    startHeartbeat(webSocket)
                } else {
                    updateStatus(ChannelConnectionState.Error, frame.optString("errmsg"))
                }
                return
            }
            if (command != "aibot_msg_callback" && command != "aibot_event_callback") return
            val body = frame.optJSONObject("body") ?: return
            val sender = body.optJSONObject("from")?.optString("userid").orEmpty()
            val chatId = body.optString("chatid").ifBlank { sender }
            if (sender.isBlank() || body.optString("msgtype").isBlank()) return
            requestIds[chatId] = requestId
            scope.launch(Dispatchers.IO) {
                parseIncomingBody(body, requestId)?.let { emitIncoming(it) }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeatJob?.cancel()
            pendingCommands.values.forEach { it.completeExceptionally(t) }
            pendingCommands.clear()
            updateStatus(ChannelConnectionState.Reconnecting, t.message.orEmpty())
            scope.launch {
                delay(2_000)
                runCatching { start() }
                    .onFailure { updateStatus(ChannelConnectionState.Error, it.message.orEmpty()) }
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000)
                webSocket.send(
                    JSONObject().put("cmd", "ping")
                        .put("headers", JSONObject().put("req_id", "ping_${UUID.randomUUID()}"))
                        .toString()
                )
            }
        }
    }

    override suspend fun stop() {
        socket?.close(1000, "Aether channel stopped")
        socket = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        requestIds.clear()
        activeStreams.clear()
        pendingCommands.values.forEach { it.cancel() }
        pendingCommands.clear()
        updateStatus(ChannelConnectionState.Disabled)
    }

    private suspend fun parseIncomingBody(
        body: JSONObject,
        requestId: String,
    ): ChannelIncomingMessage? {
        val sender = body.optJSONObject("from")?.optString("userid").orEmpty()
        if (sender.isBlank()) return null
        val chatId = body.optString("chatid").ifBlank { sender }
        val messageId = body.optString("msgid").ifBlank { "$sender:${body.optLong("send_time")}" }
        val textParts = mutableListOf<String>()
        val attachments = mutableListOf<ChannelIncomingAttachment>()
        val messageType = body.optString("msgtype")

        if (messageType == "mixed") {
            val items = body.optJSONObject("mixed")?.optJSONArray("msg_item")
            if (items != null) repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                parseMessagePart(item.optString("msgtype"), item, messageId, textParts, attachments)
            }
        } else {
            parseMessagePart(messageType, body, messageId, textParts, attachments)
        }

        val text = textParts.distinct().joinToString("\n").trim()
        if (text.isBlank() && attachments.isEmpty()) return null
        return ChannelIncomingMessage(
            channel = kind,
            messageId = messageId,
            address = ChannelAddress(chatId, sender, requestId),
            text = text,
            attachments = attachments,
        )
    }

    private suspend fun parseMessagePart(
        messageType: String,
        container: JSONObject,
        messageId: String,
        textParts: MutableList<String>,
        attachments: MutableList<ChannelIncomingAttachment>,
    ) {
        when (messageType) {
            "text" -> container.optJSONObject("text")?.optString("content")
                ?.trim()?.takeIf(String::isNotBlank)?.let(textParts::add)
            "voice" -> {
                val text = container.optJSONObject("voice")?.optString("content").orEmpty().trim()
                textParts += text.ifBlank { "[voice: no text]" }
            }
            "image", "file", "video" -> {
                val media = container.optJSONObject(messageType) ?: JSONObject()
                val url = media.optString("url")
                if (url.isBlank()) {
                    textParts += "[$messageType: no url]"
                    return
                }
                val fileName = media.optString("filename").ifBlank {
                    when (messageType) {
                        "image" -> "image.jpg"
                        "video" -> "video.mp4"
                        else -> "file.bin"
                    }
                }
                runCatching {
                    downloadMedia(
                        messageId = messageId,
                        url = url,
                        encodedAesKey = media.optString("aeskey"),
                        fileName = fileName,
                        fileKind = when (messageType) {
                            "image" -> ChannelFileKind.Image
                            "video" -> ChannelFileKind.Video
                            else -> ChannelFileKind.File
                        },
                    )
                }.onSuccess { attachments += it }
                    .onFailure { textParts += "[$messageType: download failed]" }
            }
            else -> if (messageType.isNotBlank()) textParts += "[$messageType]"
        }
    }

    private suspend fun downloadMedia(
        messageId: String,
        url: String,
        encodedAesKey: String,
        fileName: String,
        fileKind: ChannelFileKind,
    ): ChannelIncomingAttachment {
        val request = Request.Builder().url(url).get().build()
        val downloaded = http.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) error("WeCom media download failed: HTTP ${response.code}")
            val body = response.body ?: error("WeCom media download returned no body")
            inboundFileStore.readBytes(
                declaredSize = body.contentLength().takeIf { it >= 0L },
                input = body::byteStream,
            )
        }
        val bytes = if (encodedAesKey.isBlank()) downloaded else decryptWeComMedia(downloaded, encodedAesKey)
        return inboundFileStore.save(
            channel = kind,
            messageId = messageId,
            name = fileName,
            mimeType = "",
            kind = fileKind,
            declaredSize = bytes.size.toLong(),
            input = { ByteArrayInputStream(bytes) },
        )
    }

    private fun decryptWeComMedia(encrypted: ByteArray, encodedAesKey: String): ByteArray {
        val key = Base64.decode(encodedAesKey, Base64.DEFAULT)
        require(key.size == 32) { "Invalid WeCom media AES key" }
        val aligned = if (encrypted.size % 16 == 0) encrypted else {
            encrypted.copyOf(encrypted.size + (16 - encrypted.size % 16))
        }
        val decrypted = Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key.copyOfRange(0, 16)))
            doFinal(aligned)
        }
        val padding = decrypted.lastOrNull()?.toInt()?.and(0xff) ?: error("Empty WeCom media")
        require(padding in 1..32 && padding <= decrypted.size) { "Invalid WeCom media padding" }
        require(decrypted.takeLast(padding).all { it.toInt().and(0xff) == padding }) {
            "Invalid WeCom media padding"
        }
        return decrypted.copyOf(decrypted.size - padding)
    }

    override suspend fun onProcessing(message: ChannelIncomingMessage) {
        send(ChannelReply(message.address, "🤔 Thinking…", isFinal = false))
    }

    override suspend fun send(reply: ChannelReply): ChannelSendReceipt {
        val requestId = reply.address.replyToken
            .ifBlank { requestIds[reply.address.conversationId].orEmpty() }
        require(requestId.isNotBlank()) { "WeCom inbound frame is no longer available" }
        if (reply.text.isNotBlank()) sendStream(reply, requestId)
        reply.files.forEach { sendMedia(reply.address, requestId, it) }
        return ChannelSendReceipt(requestId)
    }

    private fun sendStream(reply: ChannelReply, requestId: String) {
        val streamId = activeStreams.computeIfAbsent(reply.address.conversationId) {
            "aether-${UUID.randomUUID()}"
        }
        val frame = JSONObject()
            .put("cmd", "aibot_respond_msg")
            .put("headers", JSONObject().put("req_id", requestId))
            .put(
                "body",
                JSONObject().put("msgtype", "stream").put(
                    "stream",
                    JSONObject()
                        .put("id", streamId)
                        .put("finish", reply.isFinal)
                        .put("content", reply.text),
                ),
            )
        check(socket?.send(frame.toString()) == true) { "WeCom socket is not connected" }
        if (reply.isFinal) activeStreams.remove(reply.address.conversationId)
    }

    private suspend fun sendMedia(
        address: ChannelAddress,
        requestId: String,
        payload: ChannelFile,
    ) {
        val uploadId = sendCommand(
            command = "aibot_upload_media_init",
            body = JSONObject()
                .put("type", weComMediaType(payload))
                .put("filename", payload.name)
                .put("total_size", payload.bytes.size)
                .put("total_chunks", (payload.bytes.size + ChunkSize - 1) / ChunkSize)
                .put("md5", md5(payload.bytes)),
        ).optJSONObject("body")?.optString("upload_id")
            .orEmpty()
            .ifBlank { error("WeCom media init returned no upload ID") }

        payload.bytes.asList().chunked(ChunkSize).forEachIndexed { index, chunk ->
            sendCommand(
                command = "aibot_upload_media_chunk",
                body = JSONObject()
                    .put("upload_id", uploadId)
                    .put("chunk_index", index)
                    .put(
                        "base64_data",
                        Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP),
                    ),
            )
        }
        val mediaId = sendCommand(
            command = "aibot_upload_media_finish",
            body = JSONObject().put("upload_id", uploadId),
        ).optJSONObject("body")?.optString("media_id")
            .orEmpty()
            .ifBlank { error("WeCom media finish returned no media ID") }

        val type = weComMediaType(payload)
        val body = JSONObject()
            .put("msgtype", type)
            .put(type, JSONObject().put("media_id", mediaId))
        val frame = JSONObject()
            .put("cmd", "aibot_respond_msg")
            .put("headers", JSONObject().put("req_id", requestId))
            .put("body", body)
        check(socket?.send(frame.toString()) == true) { "WeCom socket is not connected" }
        requestIds[address.conversationId] = requestId
    }

    private suspend fun sendCommand(command: String, body: JSONObject): JSONObject {
        val requestId = "$command-${UUID.randomUUID()}"
        val deferred = CompletableDeferred<JSONObject>()
        pendingCommands[requestId] = deferred
        val frame = JSONObject()
            .put("cmd", command)
            .put("headers", JSONObject().put("req_id", requestId))
            .put("body", body)
        if (socket?.send(frame.toString()) != true) {
            pendingCommands.remove(requestId)
            error("WeCom socket is not connected")
        }
        return try {
            withTimeout(30_000) { deferred.await() }
        } finally {
            pendingCommands.remove(requestId)
        }
    }

    private fun weComMediaType(payload: ChannelFile): String = when (payload.kind) {
        ChannelFileKind.Image -> "image"
        ChannelFileKind.Audio -> "voice"
        ChannelFileKind.Video -> "video"
        ChannelFileKind.File -> "file"
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ChunkSize = 512 * 1024
    }
}
