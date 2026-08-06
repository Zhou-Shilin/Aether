package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherDrawerOpenedEventGateTest {
    @Test
    fun suppressesRegistrationDuringCloseAndRecoversIfCloseIsCanceled() {
        val gate = AetherDrawerOpenedEventGate()

        assertFalse(
            gate.onDrawerSnapshotChanged(
                currentOpen = true,
                targetOpen = true,
                eventRegistered = false,
            )
        )
        assertFalse(
            gate.onDrawerSnapshotChanged(
                currentOpen = true,
                targetOpen = false,
                eventRegistered = true,
            )
        )
        assertTrue(
            gate.onDrawerSnapshotChanged(
                currentOpen = true,
                targetOpen = true,
                eventRegistered = true,
            )
        )
        assertFalse(
            gate.onDrawerSnapshotChanged(
                currentOpen = true,
                targetOpen = true,
                eventRegistered = true,
            )
        )
    }

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
                gate.onDrawerSnapshotChanged(
                    currentOpen = true,
                    targetOpen = true,
                    eventRegistered = false,
                ),
                gate.onDrawerSnapshotChanged(
                    currentOpen = true,
                    targetOpen = false,
                    eventRegistered = true,
                ),
                gate.onDrawerSnapshotChanged(
                    currentOpen = false,
                    targetOpen = false,
                    eventRegistered = true,
                ),
                gate.onDrawerSnapshotChanged(
                    currentOpen = true,
                    targetOpen = true,
                    eventRegistered = true,
                ),
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
