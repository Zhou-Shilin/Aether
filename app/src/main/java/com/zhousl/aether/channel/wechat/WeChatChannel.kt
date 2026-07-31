package com.zhousl.aether.channel.wechat

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
import com.zhousl.aether.channel.postJson
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** WeChat iLink Bot long polling with typing tickets and encrypted CDN file delivery. */
class WeChatChannel(
    private val config: ChannelConfig,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val inboundFileStore: ChannelInboundFileStore,
) : BaseAetherChannel(ChannelKind.WeChat) {
    private data class UploadedMedia(
        val encryptedQueryParameter: String,
        val messageAesKey: String,
        val encryptedSize: Int,
    )

    private var pollingJob: Job? = null
    private val typingTickets = ConcurrentHashMap<String, String>()
    private val typingJobs = ConcurrentHashMap<String, Job>()

    override suspend fun start() {
        if (!config.enabled) return updateStatus(ChannelConnectionState.Disabled)
        if (!config.isConfigured) return updateStatus(ChannelConnectionState.Error, "Bot token is required")
        if (pollingJob?.isActive == true) return
        updateStatus(ChannelConnectionState.Starting)
        pollingJob = scope.launch {
            var cursor = ""
            var failures = 0
            while (isActive) {
                try {
                    val response = http.postJson(
                        "${config.baseUrl.trimEnd('/')}/ilink/bot/getupdates",
                        JSONObject().put("get_updates_buf", cursor).put("base_info", baseInfo()),
                        headers(),
                    )
                    val ret = response.optInt("ret")
                    if (ret != 0 && ret != -1) error("WeChat getupdates ret=$ret")
                    cursor = response.optString("get_updates_buf", cursor)
                    val messages = response.optJSONArray("msgs")
                    if (messages != null) repeat(messages.length()) { index ->
                        val message = messages.optJSONObject(index) ?: return@repeat
                        parseIncomingMessage(message)?.let { emitIncoming(it) }
                    }
                    failures = 0
                    updateStatus(ChannelConnectionState.Connected)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failures++
                    updateStatus(ChannelConnectionState.Reconnecting, error.message.orEmpty())
                    delay((1_000L shl failures.coerceAtMost(5)).coerceAtMost(30_000L))
                }
            }
        }
    }

    override suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        typingJobs.values.forEach(Job::cancel)
        typingJobs.clear()
        typingTickets.clear()
        updateStatus(ChannelConnectionState.Disabled)
    }

    private suspend fun parseIncomingMessage(message: JSONObject): ChannelIncomingMessage? {
        if (message.optInt("message_type") != 1) return null
        val from = message.optString("from_user_id")
        if (from.isBlank()) return null
        val contextToken = message.optString("context_token")
        val messageId = contextToken.ifBlank {
            message.optString("msg_id").ifBlank { UUID.randomUUID().toString() }
        }
        val textParts = mutableListOf<String>()
        val attachments = mutableListOf<ChannelIncomingAttachment>()
        val items = message.optJSONArray("item_list") ?: JSONArray()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            when (item.optInt("type")) {
                1 -> item.optJSONObject("text_item")?.optString("text")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && !looksLikeFileName(it) }
                    ?.let(textParts::add)
                2 -> {
                    val image = item.optJSONObject("image_item") ?: JSONObject()
                    val media = image.optJSONObject("media") ?: JSONObject()
                    val parameter = media.optString("encrypt_query_param")
                    val aesKey = image.optString("aeskey").ifBlank { media.optString("aes_key") }
                    if (parameter.isBlank()) {
                        textParts += "[image: no url]"
                    } else {
                        runCatching {
                            downloadMedia(messageId, parameter, aesKey, "image.jpg", ChannelFileKind.Image)
                        }.onSuccess { attachments += it }
                            .onFailure { textParts += "[image: download failed]" }
                    }
                }
                3 -> {
                    val voice = item.optJSONObject("voice_item") ?: JSONObject()
                    val transcription = voice.optJSONObject("text_item")?.optString("text")
                        ?.ifBlank { voice.optString("text") }
                        .orEmpty()
                        .trim()
                    textParts += transcription.ifBlank { "[voice: no transcription]" }
                }
                4 -> {
                    val file = item.optJSONObject("file_item") ?: JSONObject()
                    val media = file.optJSONObject("media") ?: JSONObject()
                    val parameter = media.optString("encrypt_query_param")
                    val fileName = file.optString("file_name").ifBlank { "file.bin" }
                    if (parameter.isBlank()) {
                        textParts += "[file: no url]"
                    } else {
                        runCatching {
                            downloadMedia(
                                messageId,
                                parameter,
                                media.optString("aes_key"),
                                fileName,
                                ChannelFileKind.File,
                            )
                        }.onSuccess { attachments += it }
                            .onFailure { textParts += "[file: download failed]" }
                    }
                }
                5 -> {
                    val video = item.optJSONObject("video_item") ?: JSONObject()
                    val media = video.optJSONObject("media") ?: JSONObject()
                    val parameter = media.optString("encrypt_query_param")
                    if (parameter.isBlank()) {
                        textParts += "[video: no url]"
                    } else {
                        runCatching {
                            downloadMedia(
                                messageId,
                                parameter,
                                media.optString("aes_key"),
                                "video.mp4",
                                ChannelFileKind.Video,
                            )
                        }.onSuccess { attachments += it }
                            .onFailure { textParts += "[video: download failed]" }
                    }
                }
                else -> textParts += "[unsupported type: ${item.optInt("type")}]"
            }
        }
        val text = textParts.distinct().joinToString("\n").trim()
        if (text.isBlank() && attachments.isEmpty()) return null
        val groupId = message.optString("group_id")
        return ChannelIncomingMessage(
            channel = kind,
            messageId = messageId,
            address = ChannelAddress(groupId.ifBlank { from }, from, contextToken),
            text = text,
            attachments = attachments,
        )
    }

    private suspend fun downloadMedia(
        messageId: String,
        encryptedQueryParameter: String,
        encodedAesKey: String,
        fileName: String,
        kind: ChannelFileKind,
    ): ChannelIncomingAttachment {
        val url = "https://novac2c.cdn.weixin.qq.com/c2c/download?encrypted_query_param=" +
            URLEncoder.encode(encryptedQueryParameter, "UTF-8")
        val request = Request.Builder().url(url).get().build()
        val encrypted = http.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) error("WeChat media download failed: HTTP ${response.code}")
            val body = response.body ?: error("WeChat media download returned no body")
            inboundFileStore.readBytes(
                declaredSize = body.contentLength().takeIf { it >= 0L },
                input = body::byteStream,
            )
        }
        val bytes = if (encodedAesKey.isBlank()) encrypted else decryptWechatMedia(encrypted, encodedAesKey)
        return inboundFileStore.save(
            channel = this@WeChatChannel.kind,
            messageId = messageId,
            name = fileName,
            mimeType = "",
            kind = kind,
            declaredSize = bytes.size.toLong(),
            input = { ByteArrayInputStream(bytes) },
        )
    }

    private fun decryptWechatMedia(encrypted: ByteArray, encodedKey: String): ByteArray {
        val raw = encodedKey.trim()
        val key = when {
            raw.length in setOf(32, 48, 64) && raw.all { it.isHexDigit() } -> raw.hexToBytes()
            else -> {
                val decoded = Base64.decode(raw, Base64.DEFAULT)
                if (decoded.size == 32 && decoded.all { it.toInt().toChar().isHexDigit() }) {
                    decoded.toString(Charsets.US_ASCII).hexToBytes()
                } else {
                    decoded
                }
            }
        }
        require(key.size in setOf(16, 24, 32)) { "Invalid WeChat media AES key" }
        return Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(encrypted)
        }
    }

    private fun String.hexToBytes(): ByteArray = chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun looksLikeFileName(text: String): Boolean = FileNameExtensions.any {
        text.lowercase().endsWith(it)
    }

    override suspend fun onProcessing(message: ChannelIncomingMessage) {
        val userId = message.address.userId
        val ticket = typingTickets[userId] ?: runCatching {
            val response = http.postJson(
                "${config.baseUrl.trimEnd('/')}/ilink/bot/getconfig",
                JSONObject()
                    .put("ilink_user_id", userId)
                    .put("context_token", message.address.replyToken)
                    .put("base_info", baseInfo()),
                headers(),
            )
            response.takeIf { it.optInt("ret") == 0 && it.optInt("errcode") == 0 }
                ?.optString("typing_ticket")
                .orEmpty()
        }.getOrDefault("")
        if (ticket.isBlank()) return
        typingTickets[userId] = ticket
        typingJobs.remove(userId)?.cancel()
        typingJobs[userId] = scope.launch {
            while (isActive) {
                runCatching { setTyping(userId, ticket, 1) }
                delay(5_000)
            }
        }
    }

    override suspend fun onCompleted(
        message: ChannelIncomingMessage,
        receipt: ChannelSendReceipt,
    ) = stopTyping(message)

    override suspend fun onFailed(message: ChannelIncomingMessage) = stopTyping(message)

    private suspend fun stopTyping(message: ChannelIncomingMessage) {
        typingJobs.remove(message.address.userId)?.cancel()
        typingTickets[message.address.userId]?.let { ticket ->
            runCatching { setTyping(message.address.userId, ticket, 2) }
        }
    }

    private suspend fun setTyping(userId: String, ticket: String, status: Int) {
        http.postJson(
            "${config.baseUrl.trimEnd('/')}/ilink/bot/sendtyping",
            JSONObject()
                .put("ilink_user_id", userId)
                .put("typing_ticket", ticket)
                .put("status", status)
                .put("base_info", baseInfo()),
            headers(),
        )
    }

    override suspend fun send(reply: ChannelReply): ChannelSendReceipt {
        require(reply.address.replyToken.isNotBlank()) { "WeChat context token is missing" }
        if (reply.text.isNotBlank()) {
            sendItems(
                reply.address,
                JSONArray().put(
                    JSONObject().put("type", 1).put(
                        "text_item",
                        JSONObject().put("text", reply.text),
                    ),
                ),
            )
        }
        reply.files.forEach { sendFile(reply.address, it) }
        return ChannelSendReceipt()
    }

    private suspend fun sendFile(address: ChannelAddress, payload: ChannelFile) {
        val isImage = payload.kind == ChannelFileKind.Image
        val uploaded = uploadMedia(
            payload = payload,
            mediaType = if (isImage) 1 else 3,
            userId = address.userId,
        )
        val media = JSONObject()
            .put("encrypt_query_param", uploaded.encryptedQueryParameter)
            .put("aes_key", uploaded.messageAesKey)
            .put("encrypt_type", 1)
        val item = if (isImage) {
            JSONObject().put("type", 2).put(
                "image_item",
                JSONObject().put("media", media).put("mid_size", uploaded.encryptedSize),
            )
        } else {
            JSONObject().put("type", 4).put(
                "file_item",
                JSONObject()
                    .put("media", media)
                    .put("file_name", payload.name)
                    .put("len", uploaded.encryptedSize.toString()),
            )
        }
        sendItems(address, JSONArray().put(item))
    }

    private suspend fun sendItems(address: ChannelAddress, items: JSONArray) {
        val message = JSONObject()
            .put("from_user_id", "")
            .put("to_user_id", address.userId)
            .put("client_id", UUID.randomUUID().toString())
            .put("message_type", 2)
            .put("message_state", 2)
            .put("context_token", address.replyToken)
            .put("item_list", items)
        val response = http.postJson(
            "${config.baseUrl.trimEnd('/')}/ilink/bot/sendmessage",
            JSONObject().put("msg", message).put("base_info", baseInfo()),
            headers(),
        )
        if (response.optInt("ret") != 0) error("WeChat send failed: ret=${response.optInt("ret")}")
    }

    private suspend fun uploadMedia(
        payload: ChannelFile,
        mediaType: Int,
        userId: String,
    ): UploadedMedia {
        val aesKey = ByteArray(16).also(SecureRandom()::nextBytes)
        val aesKeyHex = aesKey.joinToString("") { "%02x".format(it) }
        val fileKey = ByteArray(16).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        }
        val encrypted = cipher.doFinal(payload.bytes)
        val md5 = MessageDigest.getInstance("MD5").digest(payload.bytes)
            .joinToString("") { "%02x".format(it) }
        val uploadInfo = http.postJson(
            "${config.baseUrl.trimEnd('/')}/ilink/bot/getuploadurl",
            JSONObject()
                .put("filekey", fileKey)
                .put("media_type", mediaType)
                .put("to_user_id", userId)
                .put("rawsize", payload.bytes.size)
                .put("rawfilemd5", md5)
                .put("filesize", encrypted.size)
                .put("aeskey", aesKeyHex)
                .put("no_need_thumb", true)
                .put("base_info", baseInfo()),
            headers(),
        )
        val uploadUrl = uploadInfo.optString("upload_full_url").ifBlank {
            val parameter = uploadInfo.optString("upload_param")
            require(parameter.isNotBlank()) { "WeChat upload returned no URL" }
            "https://novac2c.cdn.weixin.qq.com/c2c/upload?encrypted_query_param=" +
                URLEncoder.encode(parameter, "UTF-8") +
                "&filekey=$fileKey"
        }
        val request = Request.Builder()
            .url(uploadUrl)
            .post(encrypted.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val encryptedParameter = http.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) error("WeChat CDN upload failed: HTTP ${response.code}")
            response.header("x-encrypted-param").orEmpty()
                .ifBlank { error("WeChat CDN returned no encrypted parameter") }
        }
        return UploadedMedia(
            encryptedQueryParameter = encryptedParameter,
            messageAesKey = Base64.encodeToString(aesKeyHex.toByteArray(), Base64.NO_WRAP),
            encryptedSize = encrypted.size,
        )
    }

    private fun baseInfo(): JSONObject = JSONObject().put("channel_version", ChannelVersion)

    private fun headers(): Map<String, String> {
        val uin = Base64.encodeToString(
            Random.nextLong(0, 0xffff_ffffL).toString().toByteArray(),
            Base64.NO_WRAP,
        )
        return mapOf(
            "AuthorizationType" to "ilink_bot_token",
            "Authorization" to "Bearer ${config.token}",
            "X-WECHAT-UIN" to uin,
        )
    }

    private companion object {
        const val ChannelVersion = "2.0.1"
        val FileNameExtensions = setOf(
            ".txt", ".doc", ".docx", ".pdf", ".jpg", ".jpeg", ".png", ".gif",
            ".mp4", ".avi", ".mov", ".mp3", ".wav", ".zip", ".rar", ".xlsx",
            ".xls", ".ppt", ".pptx",
        )
    }
}
