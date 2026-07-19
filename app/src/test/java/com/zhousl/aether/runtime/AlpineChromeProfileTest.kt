package com.zhousl.aether.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlpineChromeProfileTest {
    @Test
    fun chromeProfileStaysOptionalAndIncludesCjkFonts() {
        val packages = AlpineRuntime.AlpinePackageProfiles.getValue("chrome")

        assertEquals("chromium", packages.first())
        assertTrue("font-noto" in packages)
        assertTrue("font-noto-cjk" in packages)
        assertTrue("openbox" in packages)
        assertTrue("tigervnc" in packages)
        assertTrue("xprop" in packages)
        assertTrue("novnc" in packages)
        assertTrue("websockify" in packages)
    }

    @Test
    fun apkInstallOutputReportsPercentageAcrossChunks() {
        val tracker = AlpinePackageInstallProgressTracker()

        tracker.onOutput("(7/32) Instal")
        val progress = tracker.onOutput("ling chromium (1.2.3-r0)\n")

        assertEquals(AlpineSetupActivity.Installing, progress.activity)
        assertEquals(21, progress.progressPercent)
    }

    @Test
    fun downloadRateRemainsVisibleBeforePackageInstallationStarts() {
        val tracker = AlpinePackageInstallProgressTracker()

        val progress = tracker.onRate(6L * 1024L * 1024L)

        assertEquals(AlpineSetupActivity.Downloading, progress.activity)
        assertEquals(6L * 1024L * 1024L, progress.bytesPerSecond)
    }
}
