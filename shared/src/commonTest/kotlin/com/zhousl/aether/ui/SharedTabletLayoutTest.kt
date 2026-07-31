package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedTabletLayoutTest {
    @Test
    fun usesTabletLayoutOnlyForSupportedWideScreens() {
        assertTrue(shouldUseSharedTabletLayout(supportsTabletLayout = true, availableWidthDp = 700f))
        assertTrue(shouldUseSharedTabletLayout(supportsTabletLayout = true, availableWidthDp = 1_024f))
        assertFalse(shouldUseSharedTabletLayout(supportsTabletLayout = true, availableWidthDp = 699f))
        assertFalse(shouldUseSharedTabletLayout(supportsTabletLayout = false, availableWidthDp = 1_024f))
    }

    @Test
    fun settingsDismissGuardTracksOnlyTheActiveDraft() {
        val guard = SharedSettingsDismissGuard()
        val firstPage = Any()
        val secondPage = Any()

        guard.report(firstPage, hasChanges = true)
        assertTrue(guard.hasUnsavedChanges)
        guard.rejectDismiss()
        assertTrue(guard.saveShakeRequest == 1)

        guard.report(secondPage, hasChanges = false)
        guard.clear(firstPage)
        assertFalse(guard.hasUnsavedChanges)
    }
}
