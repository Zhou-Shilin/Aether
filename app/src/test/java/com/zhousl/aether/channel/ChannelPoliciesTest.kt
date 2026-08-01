package com.zhousl.aether.channel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPoliciesTest {
    @Test
    fun accessPolicyDefaultsToExplicitAllowList() {
        val policy = ChannelAccessPolicy(allowedUserIds = setOf("allowed"))
        assertTrue(ChannelAccessController.isAllowed(policy, "allowed"))
        assertFalse(ChannelAccessController.isAllowed(policy, "unknown"))
    }

    @Test
    fun deduplicatorRejectsReplayButAllowsOtherPlatforms() {
        val dedupe = ChannelMessageDeduplicator()
        assertTrue(dedupe.accept(ChannelKind.Feishu, "same", 100))
        assertFalse(dedupe.accept(ChannelKind.Feishu, "same", 101))
        assertTrue(dedupe.accept(ChannelKind.DingTalk, "same", 102))
    }

    @Test
    fun deduplicatorExpiresOldEntries() {
        val dedupe = ChannelMessageDeduplicator(ttlMillis = 10)
        assertTrue(dedupe.accept(ChannelKind.WeChat, "id", 100))
        assertTrue(dedupe.accept(ChannelKind.WeChat, "id", 111))
    }
}
