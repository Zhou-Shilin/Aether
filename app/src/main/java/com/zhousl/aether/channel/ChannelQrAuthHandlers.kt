package com.zhousl.aether.channel

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

internal class WeChatQrAuthHandler(
    private val http: OkHttpClient,
) : ChannelQrAuthHandler {
    override val kind = ChannelKind.WeChat

    override suspend fun fetchQrCode(config: ChannelConfig): ChannelQrCode {
        val baseUrl = config.baseUrl.ifBlank { ChannelConfig.default(kind).baseUrl }.trimEnd('/')
        val url = "$baseUrl/ilink/bot/get_bot_qrcode".toHttpUrl().newBuilder()
            .addQueryParameter("bot_type", "3")
            .build()
        val data = http.getJson(url.toString(), weChatHeaders())
        val token = data.optString("qrcode")
        val imageContent = data.optString("qrcode_img_content")
        check(token.isNotBlank() || imageContent.isNotBlank()) { "WeChat returned empty QR code data" }
        val scanUrl = if (imageContent.startsWith("http")) {
            imageContent
        } else {
            "https://liteapp.weixin.qq.com/q/7GiQu1".toHttpUrl().newBuilder()
                .addQueryParameter("qrcode", token)
                .addQueryParameter("bot_type", "3")
                .build()
                .toString()
        }
        return ChannelQrCode(scanUrl, token)
    }

    override suspend fun pollStatus(token: String, config: ChannelConfig): ChannelQrPollResult {
        val baseUrl = config.baseUrl.ifBlank { ChannelConfig.default(kind).baseUrl }.trimEnd('/')
        val url = "$baseUrl/ilink/bot/get_qrcode_status".toHttpUrl().newBuilder()
            .addQueryParameter("qrcode", token)
            .build()
        val data = http.getJson(url.toString(), weChatHeaders())
        return ChannelQrPollResult(
            status = when (data.optString("status").lowercase()) {
                "scanned" -> ChannelQrPollStatus.Scanned
                "confirmed", "success" -> ChannelQrPollStatus.Success
                "expired" -> ChannelQrPollStatus.Expired
                "fail", "failed" -> ChannelQrPollStatus.Failure
                else -> ChannelQrPollStatus.Waiting
            },
            credentials = mapOf(
                "bot_token" to data.optString("bot_token"),
                "base_url" to data.optString("baseurl"),
            ),
            detail = data.optString("errmsg"),
        )
    }

    private fun weChatHeaders(): Map<String, String> {
        val unsigned = SecureRandom().nextInt().toLong() and 0xffff_ffffL
        val uin = Base64.getEncoder().encodeToString(unsigned.toString().toByteArray())
        return mapOf(
            "Content-Type" to "application/json",
            "AuthorizationType" to "ilink_bot_token",
            "X-WECHAT-UIN" to uin,
        )
    }
}

internal class WeComQrAuthHandler(
    private val http: OkHttpClient,
    private val authOrigin: String = "https://work.weixin.qq.com",
) : ChannelQrAuthHandler {
    override val kind = ChannelKind.WeCom
    override val pollIntervalMillis = 3_000L

    override suspend fun fetchQrCode(config: ChannelConfig): ChannelQrCode {
        val url = "$authOrigin/ai/qc/gen".toHttpUrl().newBuilder()
            .addQueryParameter("source", "aether")
            .addQueryParameter("state", UUID.randomUUID().toString())
            .addQueryParameter("timestamp", System.currentTimeMillis().toString())
            .build()
        val html = http.getText(url.toString())
        val settingsJson = SettingsPattern.find(html)?.groupValues?.getOrNull(1)
            ?: error("Unable to parse the WeCom authorization page")
        val settings = JSONObject(settingsJson)
        val scanUrl = settings.optString("auth_url")
        val pollToken = settings.optString("scode")
        check(scanUrl.isNotBlank() && pollToken.isNotBlank()) {
            "WeCom returned an empty authorization URL"
        }
        return ChannelQrCode(scanUrl, pollToken)
    }

    override suspend fun pollStatus(token: String, config: ChannelConfig): ChannelQrPollResult {
        val url = "$authOrigin/ai/qc/query_result".toHttpUrl().newBuilder()
            .addQueryParameter("scode", token)
            .build()
        val data = http.getJson(url.toString()).optJSONObject("data") ?: JSONObject()
        val botInfo = data.optJSONObject("bot_info") ?: JSONObject()
        val rawStatus = data.optString("status").lowercase()
        return ChannelQrPollResult(
            status = when (rawStatus) {
                "success", "confirmed" -> ChannelQrPollStatus.Success
                "scanned" -> ChannelQrPollStatus.Scanned
                "expired" -> ChannelQrPollStatus.Expired
                "fail", "failed", "denied" -> ChannelQrPollStatus.Failure
                else -> ChannelQrPollStatus.Waiting
            },
            credentials = mapOf(
                "bot_id" to botInfo.optString("botid"),
                "secret" to botInfo.optString("secret"),
            ),
            detail = data.optString("message"),
        )
    }

    private companion object {
        val SettingsPattern = Regex("""window\.settings\s*=\s*(\{[^<]+\})""")
    }
}

internal class DingTalkQrAuthHandler(
    private val http: OkHttpClient,
    private val apiBase: String = "https://oapi.dingtalk.com",
) : ChannelQrAuthHandler {
    override val kind = ChannelKind.DingTalk
    override val pollIntervalMillis = 5_000L

    override suspend fun fetchQrCode(config: ChannelConfig): ChannelQrCode {
        val init = http.postJson(
            "$apiBase/app/registration/init",
            JSONObject().put("source", "QWENPAW"),
        )
        check(init.optInt("errcode", -1) == 0) {
            "DingTalk initialization failed: ${init.optString("errmsg", "unknown error")}"
        }
        val nonce = init.optString("nonce")
        check(nonce.isNotBlank()) { "DingTalk returned an empty nonce" }
        val begin = http.postJson(
            "$apiBase/app/registration/begin",
            JSONObject().put("nonce", nonce),
        )
        check(begin.optInt("errcode", -1) == 0) {
            "DingTalk authorization failed: ${begin.optString("errmsg", "unknown error")}"
        }
        val token = begin.optString("device_code")
        val scanUrl = begin.optString("verification_uri_complete")
        check(token.isNotBlank() && scanUrl.isNotBlank()) { "DingTalk returned an empty QR code" }
        return ChannelQrCode(scanUrl, token)
    }

    override suspend fun pollStatus(token: String, config: ChannelConfig): ChannelQrPollResult {
        val data = http.postJson(
            "$apiBase/app/registration/poll",
            JSONObject().put("device_code", token),
        )
        return when (data.optString("status", "WAITING").uppercase()) {
            "SUCCESS" -> ChannelQrPollResult(
                ChannelQrPollStatus.Success,
                mapOf(
                    "client_id" to data.optString("client_id"),
                    "client_secret" to data.optString("client_secret"),
                ),
            )
            "FAIL" -> ChannelQrPollResult(
                ChannelQrPollStatus.Failure,
                detail = data.optString("fail_reason"),
            )
            "EXPIRED" -> ChannelQrPollResult(ChannelQrPollStatus.Expired)
            else -> ChannelQrPollResult(ChannelQrPollStatus.Waiting)
        }
    }
}

internal class FeishuQrAuthHandler(
    private val http: OkHttpClient,
    private val feishuAccountsOrigin: String = "https://accounts.feishu.cn",
    private val larkAccountsOrigin: String = "https://accounts.larksuite.com",
) : ChannelQrAuthHandler {
    override val kind = ChannelKind.Feishu

    override suspend fun fetchQrCode(config: ChannelConfig): ChannelQrCode {
        val endpoint = endpoint(config)
        val init = http.postForm(endpoint, mapOf("action" to "init"))
        val methods = init.optJSONArray("supported_auth_methods")
        check(methods != null && (0 until methods.length()).any { methods.optString(it) == "client_secret" }) {
            "Feishu does not support client-secret authorization"
        }
        val begin = http.postForm(
            endpoint,
            mapOf(
                "action" to "begin",
                "archetype" to "PersonalAgent",
                "auth_method" to "client_secret",
                "request_user_info" to "open_id",
            ),
        )
        val token = begin.optString("device_code")
        val verificationUrl = begin.optString("verification_uri_complete")
        check(token.isNotBlank() && verificationUrl.isNotBlank()) { "Feishu returned an empty QR code" }
        val scanUrl = verificationUrl.toHttpUrl().newBuilder()
            .addQueryParameter("source", "Aether")
            .build()
            .toString()
        return ChannelQrCode(scanUrl, token)
    }

    override suspend fun pollStatus(token: String, config: ChannelConfig): ChannelQrPollResult {
        val data = http.postForm(
            endpoint(config),
            mapOf("action" to "poll", "device_code" to token),
        )
        if (data.optString("client_id").isNotBlank() && data.optString("client_secret").isNotBlank()) {
            val userInfo = data.optJSONObject("user_info") ?: JSONObject()
            return ChannelQrPollResult(
                ChannelQrPollStatus.Success,
                mapOf(
                    "app_id" to data.optString("client_id"),
                    "app_secret" to data.optString("client_secret"),
                    "open_id" to userInfo.optString("open_id"),
                    "tenant_brand" to userInfo.optString("tenant_brand", "feishu"),
                ),
            )
        }
        return when (val error = data.optString("error")) {
            "expired_token", "invalid_grant" -> ChannelQrPollResult(
                ChannelQrPollStatus.Expired,
                detail = "QR code expired",
            )
            "access_denied" -> ChannelQrPollResult(
                ChannelQrPollStatus.Failure,
                detail = "Authorization was denied",
            )
            "", "authorization_pending", "slow_down" -> ChannelQrPollResult(ChannelQrPollStatus.Waiting)
            else -> ChannelQrPollResult(ChannelQrPollStatus.Failure, detail = error)
        }
    }

    private fun endpoint(config: ChannelConfig): String {
        val origin = if (config.baseUrl.contains("larksuite", ignoreCase = true)) {
            larkAccountsOrigin
        } else {
            feishuAccountsOrigin
        }
        return "$origin/oauth/v1/app/registration"
    }
}
