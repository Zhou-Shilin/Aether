package com.zhousl.aether.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesTest {
    @Test
    fun iosExposesAlpineWithoutAndroidOnlyFeatures() {
        assertTrue(PlatformCapabilities.Ios.alpine)
        assertFalse(PlatformCapabilities.Ios.alpineChrome)
        assertTrue(PlatformCapabilities.Ios.stdioMcp)
        assertTrue(PlatformCapabilities.Ios.scriptExtensions)
        assertFalse(PlatformCapabilities.Ios.termux)
        assertFalse(PlatformCapabilities.Ios.runtimeSelection)
        assertFalse(PlatformCapabilities.Ios.agentMode)
        assertFalse(PlatformCapabilities.Ios.scheduledTasks)
        assertFalse(PlatformCapabilities.Ios.persistentBackground)
        assertFalse(PlatformCapabilities.Ios.nativeMods)
        assertTrue(PlatformCapabilities.Ios.layeredScreenTransitions)
        assertTrue(PlatformCapabilities.Android.layeredScreenTransitions)
    }
}
