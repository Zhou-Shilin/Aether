package com.zhousl.aether

import android.view.WindowManager.LayoutParams
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class ChatImePolicyTest {
    /** Android 8–10 must expose the resized frame used by the AndroidX IME fallback. */
    @Test
    fun legacyAndroidUsesResizeIncludingApi29() {
        for (sdk in 26..29) {
            assertEquals(LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                chatSoftInputMode(sdk, LayoutParams.SOFT_INPUT_ADJUST_NOTHING))
        }
    }

    /** The API 30 boundary retains Compose-owned keyboard spacing on modern Android. */
    @Test
    fun modernAndroidKeepsAdjustNothing() {
        for (sdk in listOf(30, 31, 35, 36)) {
            assertEquals(LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                chatSoftInputMode(sdk, LayoutParams.SOFT_INPUT_ADJUST_RESIZE))
        }
    }

    /** Recreating or reconfiguring the window preserves state flags without accumulating adjustments. */
    @Test
    fun preservesVisibilityFlagsAndRepeatedApplication() {
        val flags = LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION
        for (sdk in listOf(29, 30)) {
            val expectedAdjustment = if (sdk == 29) LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                else LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            val result = chatSoftInputMode(sdk, flags or LayoutParams.SOFT_INPUT_ADJUST_PAN)
            assertEquals(flags or expectedAdjustment, result)
            assertEquals(result, chatSoftInputMode(sdk, result))
        }
    }
}
