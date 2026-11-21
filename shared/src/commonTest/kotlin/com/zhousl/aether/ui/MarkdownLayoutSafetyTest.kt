package com.zhousl.aether.ui

import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownLayoutSafetyTest {
    @Test
    fun unclampedRunawayHeightExceedsComposeConstraintsLimitWhileResolvedHeightStaysValid() {
        // Reproduce issue #117: width=0 (unbounded maxWidth) + height=383108 px overflows Constraints.
        val exception = assertFailsWith<IllegalArgumentException> {
            Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 383_108,
                maxHeight = 383_108,
            )
        }
        assertTrue(exception.message.orEmpty().contains("383108"))

        val density = 3.5f
        val safeHeightDp = resolveSafeMarkdownHtmlHeightDp(
            measuredHeightDp = 109_459,
            hasMeasuredContent = true,
            layoutMinHeightDp = 24,
            layoutMaxHeightDp = null,
            layoutHeightDp = null,
            showAll = true,
            scroll = false,
            defaultMinHeightDp = 24,
            defaultMaxHeightDp = SafeMaxMarkdownTextHeightDp,
            density = density,
        )
        val safeHeightPx = (safeHeightDp * density).roundToInt()
        val constraints = Constraints(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = safeHeightPx,
            maxHeight = safeHeightPx,
        )
        assertEquals(safeHeightPx, constraints.minHeight)
        assertEquals(safeHeightPx, constraints.maxHeight)
        assertTrue(safeHeightDp <= SafeMaxMarkdownDimensionDp)
    }

    @Test
    fun resolveSafeMarkdownHtmlHeightDpBoundsExplicitAndExtremeDensityHeights() {
        val explicitClamped = resolveSafeMarkdownHtmlHeightDp(
            measuredHeightDp = 120,
            hasMeasuredContent = true,
            layoutMinHeightDp = 24,
            layoutMaxHeightDp = 999_999,
            layoutHeightDp = 999_999,
            showAll = false,
            scroll = true,
            defaultMinHeightDp = 24,
            defaultMaxHeightDp = 640,
            density = 4f,
        )
        assertEquals(SafeMaxMarkdownDimensionDp, explicitClamped)

        // Even at hypothetical extreme display density, pixel height must stay below Compose limit.
        val extremeDensity = 128f
        val extremeClamped = resolveSafeMarkdownHtmlHeightDp(
            measuredHeightDp = SafeMaxMarkdownDimensionDp,
            hasMeasuredContent = true,
            layoutMinHeightDp = 1,
            layoutMaxHeightDp = null,
            layoutHeightDp = null,
            showAll = true,
            scroll = false,
            defaultMinHeightDp = 1,
            defaultMaxHeightDp = SafeMaxMarkdownDimensionDp,
            density = extremeDensity,
        )
        val px = (extremeClamped * extremeDensity).roundToInt()
        assertTrue(px < MaxComposeConstraintAxisPx)
        val constraints = Constraints(0, Constraints.Infinity, px, px)
        assertEquals(px, constraints.maxHeight)
    }

    @Test
    fun resolveSafeMarkdownBitmapHeightDpClampsSkinnyUltraTallImagesAndNonFiniteValues() {
        val ultraTall = resolveSafeMarkdownBitmapHeightDp(
            naturalHeightDp = 737_280f,
            layoutMaxHeightDp = null,
            layoutHeightDp = null,
            showAll = true,
            defaultMaxHeightDp = 420,
            density = 3.5f,
        )
        assertEquals(SafeMaxMarkdownDimensionDp.toFloat(), ultraTall)

        val nanHeight = resolveSafeMarkdownBitmapHeightDp(
            naturalHeightDp = Float.NaN,
            layoutMaxHeightDp = 420,
            layoutHeightDp = null,
            showAll = false,
            defaultMaxHeightDp = 420,
            density = 3f,
        )
        assertEquals(420f, nanHeight)
    }

    @Test
    fun parseSafeReportedHtmlHeightDpHandlesFloatsInvalidAndRunawayValues() {
        assertEquals(43, parseSafeReportedHtmlHeightDp("42.1"))
        assertEquals(SafeMaxMarkdownDimensionDp, parseSafeReportedHtmlHeightDp("999999.9"))
        assertNull(parseSafeReportedHtmlHeightDp("0"))
        assertNull(parseSafeReportedHtmlHeightDp("-12"))
        assertNull(parseSafeReportedHtmlHeightDp("NaN"))
        assertNull(parseSafeReportedHtmlHeightDp("Infinity"))
        assertNull(parseSafeReportedHtmlHeightDp(null))
    }

    @Test
    fun clampSafeExtensionDpRejectsNonFiniteAndBoundsHugeValues() {
        assertNull(clampSafeExtensionDp(Double.NaN))
        assertNull(clampSafeExtensionDp(Double.POSITIVE_INFINITY))
        assertEquals(0.0, clampSafeExtensionDp(-20.0))
        assertEquals(SafeMaxMarkdownDimensionDp.toDouble(), clampSafeExtensionDp(500_000.0))
    }

    @Test
    fun contentHeightJsNeverReadsDocumentOrBodyScrollHeight() {
        val snippet = buildSafeMarkdownContentHeightJs(".aether-text", 24)
        assertTrue(snippet.contains(".aether-text"))
        assertTrue(snippet.contains("getBoundingClientRect"))
        assertFalse(snippet.contains("document.documentElement.scrollHeight"))
        assertFalse(snippet.contains("document.body.scrollHeight"))
    }
}
