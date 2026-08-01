package com.zhousl.aether.channel

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChannelQrAuthHandlersTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun weChatFetchAndPollFollowILinkContract() = runBlocking {
        server.enqueue(json("""{"qrcode":"qr-token","qrcode_img_content":""}"""))
        server.enqueue(json("""{"status":"confirmed","bot_token":"bot-token","baseurl":"https://ilink.example"}"""))
        val handler = WeChatQrAuthHandler(http)
        val config = ChannelConfig.default(ChannelKind.WeChat).copy(
            baseUrl = server.url("/").toString().trimEnd('/'),
        )

        val qrCode = handler.fetchQrCode(config)
        val result = handler.pollStatus(qrCode.pollToken, config)

        assertEquals("qr-token", qrCode.pollToken)
        assertTrue(qrCode.scanUrl.contains("qrcode=qr-token"))
        assertEquals(ChannelQrPollStatus.Success, result.status)
        assertEquals("bot-token", result.credentials["bot_token"])
        val fetchRequest = server.takeRequest()
        assertEquals("/ilink/bot/get_bot_qrcode?bot_type=3", fetchRequest.path)
        assertEquals("ilink_bot_token", fetchRequest.getHeader("AuthorizationType"))
        assertEquals("/ilink/bot/get_qrcode_status?qrcode=qr-token", server.takeRequest().path)
    }

    @Test
    fun dingTalkDeviceFlowReturnsClientCredentials() = runBlocking {
        server.enqueue(json("""{"errcode":0,"nonce":"nonce-1"}"""))
        server.enqueue(json("""{"errcode":0,"device_code":"device-1","verification_uri_complete":"https://example.test/ding"}"""))
        server.enqueue(json("""{"status":"SUCCESS","client_id":"ding-id","client_secret":"ding-secret"}"""))
        val base = server.url("/").toString().trimEnd('/')
        val handler = DingTalkQrAuthHandler(http, base)

        val qrCode = handler.fetchQrCode(ChannelConfig.default(ChannelKind.DingTalk))
        val result = handler.pollStatus(qrCode.pollToken, ChannelConfig.default(ChannelKind.DingTalk))

        assertEquals("device-1", qrCode.pollToken)
        assertEquals(ChannelQrPollStatus.Success, result.status)
        assertEquals("ding-id", result.credentials["client_id"])
        assertEquals("/app/registration/init", server.takeRequest().path)
        assertEquals("/app/registration/begin", server.takeRequest().path)
        assertEquals("/app/registration/poll", server.takeRequest().path)
    }

    @Test
    fun feishuDeviceFlowUsesFormsAndReturnsAppCredentials() = runBlocking {
        server.enqueue(json("""{"supported_auth_methods":["client_secret"]}"""))
        server.enqueue(json("""{"device_code":"device-2","verification_uri_complete":"https://example.test/feishu?code=2"}"""))
        server.enqueue(json("""{"client_id":"cli_id","client_secret":"cli_secret","user_info":{"open_id":"ou_1","tenant_brand":"feishu"}}"""))
        val base = server.url("/").toString().trimEnd('/')
        val handler = FeishuQrAuthHandler(http, base, base)

        val qrCode = handler.fetchQrCode(ChannelConfig.default(ChannelKind.Feishu))
        val result = handler.pollStatus(qrCode.pollToken, ChannelConfig.default(ChannelKind.Feishu))

        assertTrue(qrCode.scanUrl.contains("source=Aether"))
        assertEquals(ChannelQrPollStatus.Success, result.status)
        assertEquals("cli_secret", result.credentials["app_secret"])
        assertEquals("action=init", server.takeRequest().body.readUtf8())
        assertTrue(server.takeRequest().body.readUtf8().contains("action=begin"))
        assertTrue(server.takeRequest().body.readUtf8().contains("action=poll"))
    }

    @Test
    fun weComParsesAuthorizationPageAndBotCredentials() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "<html><script>window.settings = {\"scode\":\"scode-1\",\"auth_url\":\"https://example.test/wecom\"}</script></html>"
            )
        )
        server.enqueue(json("""{"data":{"status":"success","bot_info":{"botid":"bot-1","secret":"secret-1"}}}"""))
        val base = server.url("/").toString().trimEnd('/')
        val handler = WeComQrAuthHandler(http, base)

        val qrCode = handler.fetchQrCode(ChannelConfig.default(ChannelKind.WeCom))
        val result = handler.pollStatus(qrCode.pollToken, ChannelConfig.default(ChannelKind.WeCom))

        assertEquals("scode-1", qrCode.pollToken)
        assertEquals(ChannelQrPollStatus.Success, result.status)
        assertEquals("bot-1", result.credentials["bot_id"])
        assertTrue(server.takeRequest().path.orEmpty().startsWith("/ai/qc/gen?"))
        assertEquals("/ai/qc/query_result?scode=scode-1", server.takeRequest().path)
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
