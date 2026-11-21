package com.zhousl.aether.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Jetpack Compose's 64-bit `Constraints` packs height into at most 18 bits (`262,142 px`)
 * when width is narrow/zero (and fewer bits at very wide widths). Keeping every dynamic
 * Markdown/WebView/extension dimension well below both that ceiling and GPU texture limits
 * prevents `IllegalArgumentException: Can't represent a width of 0 and height of X in Constraints`.
 */
const val MaxComposeConstraintAxisPx: Int = 262_142
const val SafeMaxMarkdownDimensionDp: Int = 4_096
const val SafeMaxMarkdownTextHeightDp: Int = 2_048

fun maxComposeSafeDpForDensity(
    density: Float,
    capDp: Int = SafeMaxMarkdownDimensionDp,
): Int {
    val safeDensity = if (density.isFinite() && density > 0f) density else 1f
    val maxPxSafeDp = floor((MaxComposeConstraintAxisPx - 1) / safeDensity.toDouble())
        .toInt()
        .coerceAtLeast(1)
    return min(capDp.coerceAtLeast(1), maxPxSafeDp)
}

fun clampMarkdownMediaDp(
    rawDp: Int,
    maxDp: Int = SafeMaxMarkdownDimensionDp,
): Int = rawDp.coerceIn(1, maxDp.coerceAtLeast(1))

fun parseSafeReportedHtmlHeightDp(
    rawHeight: String?,
    maxSafeHeightDp: Int = SafeMaxMarkdownDimensionDp,
): Int? {
    val parsed = rawHeight?.trim()?.toDoubleOrNull() ?: return null
    if (!parsed.isFinite() || parsed <= 0.0) return null
    val upper = maxSafeHeightDp.coerceAtLeast(1)
    return if (parsed >= upper.toDouble()) {
        upper
    } else {
        ceil(parsed).toInt().coerceIn(1, upper)
    }
}

fun resolveSafeMarkdownHtmlHeightDp(
    measuredHeightDp: Int,
    hasMeasuredContent: Boolean,
    layoutMinHeightDp: Int?,
    layoutMaxHeightDp: Int?,
    layoutHeightDp: Int?,
    showAll: Boolean,
    scroll: Boolean,
    defaultMinHeightDp: Int,
    defaultMaxHeightDp: Int,
    density: Float = 1f,
    maxSafeHeightDp: Int = SafeMaxMarkdownDimensionDp,
): Int {
    val safeCeiling = maxComposeSafeDpForDensity(density = density, capDp = maxSafeHeightDp)
    val resolvedMin = (layoutMinHeightDp ?: defaultMinHeightDp).coerceIn(1, safeCeiling)
    val resolvedMax = (layoutMaxHeightDp ?: defaultMaxHeightDp).coerceIn(resolvedMin, safeCeiling)
    val effectiveMin = if (hasMeasuredContent) 1 else resolvedMin
    val safeMeasured = measuredHeightDp.coerceIn(1, safeCeiling)
    val safeExplicitHeight = layoutHeightDp?.coerceIn(effectiveMin, safeCeiling)
    val scrollViewportHeight = if (scroll) {
        (safeExplicitHeight ?: resolvedMax).coerceIn(effectiveMin, safeCeiling)
    } else {
        null
    }
    return when {
        showAll -> {
            val showAllMax = layoutMaxHeightDp?.coerceIn(effectiveMin, safeCeiling) ?: safeCeiling
            safeMeasured.coerceIn(effectiveMin, showAllMax)
        }
        safeExplicitHeight != null -> safeExplicitHeight
        scrollViewportHeight != null -> safeMeasured.coerceIn(effectiveMin, scrollViewportHeight)
        else -> safeMeasured.coerceIn(effectiveMin, resolvedMax.coerceAtLeast(effectiveMin))
    }.coerceIn(1, safeCeiling)
}

fun resolveSafeMarkdownBitmapHeightDp(
    naturalHeightDp: Float,
    layoutMaxHeightDp: Int?,
    layoutHeightDp: Int?,
    showAll: Boolean,
    defaultMaxHeightDp: Int,
    density: Float = 1f,
    maxSafeHeightDp: Int = SafeMaxMarkdownDimensionDp,
): Float {
    val safeCeiling = maxComposeSafeDpForDensity(density = density, capDp = maxSafeHeightDp).toFloat()
    val resolvedMax = (layoutMaxHeightDp ?: defaultMaxHeightDp).toFloat().coerceIn(1f, safeCeiling)
    val safeNatural = if (naturalHeightDp.isFinite() && naturalHeightDp > 0f) {
        naturalHeightDp.coerceIn(1f, safeCeiling)
    } else {
        resolvedMax
    }
    val safeExplicit = layoutHeightDp?.toFloat()?.coerceIn(1f, safeCeiling)
    return when {
        showAll -> safeNatural
        safeExplicit != null -> safeExplicit
        else -> safeNatural.coerceAtMost(resolvedMax).coerceAtLeast(1f)
    }.coerceIn(1f, safeCeiling)
}

fun clampSafeExtensionDp(
    rawDp: Double,
    minDp: Double = 0.0,
    maxDp: Double = SafeMaxMarkdownDimensionDp.toDouble(),
): Double? {
    if (!rawDp.isFinite()) return null
    val upper = maxDp.coerceAtLeast(minDp)
    return rawDp.coerceIn(minDp, upper)
}

/**
 * Shared JS snippet for measuring the actual content wrapper element rather than
 * `document.documentElement.scrollHeight` / `document.body.scrollHeight`, which include the
 * WebView's own viewport height and can enter a runaway vertical resize feedback loop.
 */
fun buildSafeMarkdownContentHeightJs(
    selector: String,
    minHeightDp: Int,
): String =
    "var el=document.querySelector('$selector')||document.body;" +
        "var rectH=el&&el.getBoundingClientRect?Math.ceil(el.getBoundingClientRect().height||0):0;" +
        "var scrollH=el&&el.scrollHeight?Math.ceil(el.scrollHeight):0;" +
        "var height=Math.max(rectH,scrollH,$minHeightDp);"
