package com.zhousl.aether.agentmode

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentModeUserScopeTest {
    @Test
    fun ownerUserUidsResolveToUserZero() {
        assertEquals(0, agentModeUserIdFromUid(0))
        assertEquals(0, agentModeUserIdFromUid(10_000))
        assertEquals(0, agentModeUserIdFromUid(10_199))
    }

    @Test
    fun secondaryUserAndWorkProfileUidsResolveToTheirUser() {
        // Issue #100 was reported from a secondary user (10); work profiles are the same scheme.
        assertEquals(10, agentModeUserIdFromUid(10 * 100_000 + 10_123))
        assertEquals(11, agentModeUserIdFromUid(11 * 100_000 + 10_123))
        assertEquals(999, agentModeUserIdFromUid(999 * 100_000))
    }

    @Test
    fun systemUidsResolveToTheOwnerUser() {
        // Shizuku and the su fallback start the user service as shell (2000) or root (0). Both live
        // in user 0, which is exactly why the service has to be told which user it acts for.
        assertEquals(0, agentModeUserIdFromUid(2000))
        assertEquals(0, agentModeUserIdFromUid(1000))
        assertEquals(0, agentModeUserIdFromUid(0))
    }
}
