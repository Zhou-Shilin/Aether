package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingWideLayoutTest {
    @Test
    fun wideOnboardingRequiresLandscapeAndTabletWidth() {
        assertTrue(
            shouldUseWideOnboardingLayout(
                availableWidthDp = 1_024f,
                availableHeightDp = 768f,
            ),
        )
        assertFalse(
            shouldUseWideOnboardingLayout(
                availableWidthDp = 699f,
                availableHeightDp = 500f,
            ),
        )
        assertFalse(
            shouldUseWideOnboardingLayout(
                availableWidthDp = 768f,
                availableHeightDp = 1_024f,
            ),
        )
        assertFalse(
            shouldUseWideOnboardingLayout(
                availableWidthDp = 700f,
                availableHeightDp = 700f,
            ),
        )
    }
}
