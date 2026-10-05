package com.zhousl.aether.ui

import com.zhousl.aether.data.pi.SharedPiUsage
import com.zhousl.aether.platform.IosAnalyticsBridge
import com.zhousl.aether.platform.IosAnalyticsListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosAnalyticsTest {
    @Test
    fun consentGatesInitializationAndDropsEarlierEvents() {
        val bridge = IosAnalyticsBridge()
        val calls = mutableListOf<String>()
        bridge.setListener(object : IosAnalyticsListener {
            override fun onConsentAccepted() { calls += "setup" }
            override fun onCapture(event: String, properties: Map<String, Any>) { calls += event }
        })
        bridge.capture("before consent")
        assertEquals(emptyList(), calls)
        bridge.acceptConsent()
        bridge.acceptConsent()
        bridge.capture("message sent")
        assertEquals(listOf("setup", "message sent"), calls)
    }

    @Test
    fun lateListenerIsInitializedBeforeReceivingEvents() {
        val bridge = IosAnalyticsBridge()
        bridge.acceptConsent()
        val calls = mutableListOf<String>()
        bridge.setListener(object : IosAnalyticsListener {
            override fun onConsentAccepted() { calls += "setup" }
            override fun onCapture(event: String, properties: Map<String, Any>) { calls += event }
        })
        bridge.capture("conversation started")
        assertEquals(listOf("setup", "conversation started"), calls)
    }

    @Test
    fun analyticsFailureDoesNotBreakUserActions() {
        val bridge = IosAnalyticsBridge()
        bridge.setListener(object : IosAnalyticsListener {
            override fun onConsentAccepted() = Unit
            override fun onCapture(event: String, properties: Map<String, Any>) { error("offline") }
        })
        bridge.acceptConsent()
        bridge.capture("message sent")
    }

    @Test
    fun tokenPropertiesMatchAndroidWithoutConversationContent() {
        val message = SharedChatMessage(
            text = "private response", fromUser = false,
            usage = SharedPiUsage(inputTokens = 30, outputTokens = 10, totalTokens = 40),
            tokenUsageSource = "api",
            tools = listOf(SharedChatToolInvocation("tool", "bash", "private command", output = "private output")),
        )
        val properties = iosTurnAnalyticsProperties(message, "success", 100, 4, 2)
        assertEquals(40L, properties["total_tokens"])
        assertEquals(10.0, properties["average_tokens_per_input_message"])
        assertEquals(20.0, properties["average_tokens_per_user_message"])
        assertEquals(listOf("bash"), properties["tool_names"])
        assertFalse(properties.toString().contains("private"))
    }

    @Test
    fun cacheTokenPropertiesAreReportedOnlyWhenAvailable() {
        val message = SharedChatMessage(
            text = "cached", fromUser = false,
            usage = SharedPiUsage(
                inputTokens = 10,
                outputTokens = 5,
                totalTokens = 15,
                cachedInputTokens = 7,
                cacheWriteTokens = 3,
            ),
            tokenUsageSource = "api",
        )
        val properties = iosTurnAnalyticsProperties(message, "success", 100, 1, 1)
        assertEquals(7L, properties["cached_input_tokens"])
        assertEquals(3L, properties["cache_write_tokens"])

        val unavailable = iosTurnAnalyticsProperties(
            SharedChatMessage(
                text = "no cache",
                fromUser = false,
                usage = SharedPiUsage(
                    cachedInputTokensAvailable = false,
                    cacheWriteTokensAvailable = false,
                ),
            ),
            "success",
            100,
            1,
            1,
        )
        assertFalse("cached_input_tokens" in unavailable)
        assertFalse("cache_write_tokens" in unavailable)
    }

    @Test
    fun missingUsageAndEmptyInputRemainFinite() {
        val properties = iosTurnAnalyticsProperties(null, "neutral", -1, 0, 0)
        assertEquals(false, properties["has_token_usage"])
        assertEquals("unavailable", properties["token_usage_source"])
        assertEquals(0L, properties["duration_millis"])
        assertEquals(0.0, properties["average_tokens_per_input_message"])
    }
}
