package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedRuntimeSetupTest {
    @Test
    fun nativeAndBridgePhasesUseAndroidFiveStepProgress() {
        assertEquals(1, runtimeSetupStepIndex("rootfs"))
        assertEquals(1, runtimeSetupStepIndex("kernel"))
        assertEquals(2, runtimeSetupStepIndex("checking_node"))
        assertEquals(2, runtimeSetupStepIndex("installing_node"))
        assertEquals(3, runtimeSetupStepIndex("preparing_bridge"))
        assertEquals(4, runtimeSetupStepIndex("starting_bridge"))
        assertEquals(5, runtimeSetupStepIndex("verifying_bridge"))
    }

    @Test
    fun readyAgentDisplaysCompletedFiveStepProgress() {
        assertEquals(5, runtimeSetupDisplayedStep("ready", ready = true))
    }
}
