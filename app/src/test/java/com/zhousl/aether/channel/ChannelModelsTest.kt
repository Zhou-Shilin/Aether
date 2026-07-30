package com.zhousl.aether.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelModelsTest {
    @Test
    fun configRoundTripPreservesCredentialsAndPolicy() {
        val original = ChannelConfig(
            kind = ChannelKind.DingTalk,
            enabled = true,
            appId = "app-id",
            appSecret = "secret",
            baseUrl = "https://api.dingtalk.com",
            accessPolicy = ChannelAccessPolicy(
                mode = ChannelAccessMode.AllowList,
                allowedUserIds = setOf("alice", "bob"),
            ),
            mergeWindowMillis = 900,
            display = ChannelDisplayOptions(
                showToolCalls = false,
                showToolResults = true,
                showThinking = false,
                streamingEnabled = true,
            ),
            robotCode = "robot-code",
            cardTemplateId = "card-template",
            cardTemplateKey = "answer",
        )

        assertEquals(original, ChannelConfig.fromJson(original.toJson()))
    }

    @Test
    fun oldConfigDefaultsToQwenPawDisplayBehavior() {
        val decoded = ChannelConfig.fromJson(
            ChannelConfig.default(ChannelKind.Feishu).toJson().apply { remove("display") }
        )

        assertEquals(ChannelDisplayOptions(), decoded?.display)
    }

    @Test
    fun channelSessionIdIsStableAndConversationScoped() {
        fun message(conversation: String) = ChannelIncomingMessage(
            channel = ChannelKind.WeCom,
            messageId = "message",
            address = ChannelAddress(conversation, "user"),
            text = "hello",
        )

        assertEquals(message("chat-a").sessionId, message("chat-a").sessionId)
        assertNotEquals(message("chat-a").sessionId, message("chat-b").sessionId)
        assertTrue(message("chat-a").sessionId.startsWith("channel:wecom:"))
    }

    @Test
    fun configurationRequirementsArePlatformSpecific() {
        assertTrue(ChannelConfig.default(ChannelKind.WeChat).copy(token = "token").isConfigured)
        assertFalse(ChannelConfig.default(ChannelKind.WeChat).copy(appId = "id", appSecret = "secret").isConfigured)
        assertTrue(ChannelConfig.default(ChannelKind.DingTalk).copy(appId = "id", appSecret = "secret").isConfigured)
    }
}
