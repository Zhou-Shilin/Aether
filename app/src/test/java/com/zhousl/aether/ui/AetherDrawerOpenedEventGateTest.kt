package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AetherDrawerOpenedEventGateTest {
    @Test
    fun defersOpenUntilRegistrationAndDispatchesOnce() {
        val gate = AetherDrawerOpenedEventGate()

        assertEquals(
            listOf(false, true, false, false),
            listOf(
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = false),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = false),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
            ),
        )
    }

    @Test
    fun discardsDeferredOpenWhenDrawerCloses() {
        val gate = AetherDrawerOpenedEventGate()

        assertEquals(
            listOf(false, false, false, true),
            listOf(
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = false),
                gate.onDrawerStateChanged(drawerOpen = false, eventRegistered = false),
                gate.onDrawerStateChanged(drawerOpen = false, eventRegistered = true),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
            ),
        )
    }

    @Test
    fun emitsOnceForEveryRegisteredOpenEpoch() {
        val gate = AetherDrawerOpenedEventGate()

        assertEquals(
            listOf(true, false, false, true),
            listOf(
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
                gate.onDrawerStateChanged(drawerOpen = false, eventRegistered = true),
                gate.onDrawerStateChanged(drawerOpen = true, eventRegistered = true),
            ),
        )
    }
}
