package com.zhousl.aether

import android.os.Build
import android.view.WindowManager

/**
 * Enables AndroidX's legacy IME detection on API 29 and below with adjustResize.
 * API 30+ supplies native IME insets, so Compose alone moves the composer and
 * adjustNothing retains the Russian build's fix for the collapsed chat layout.
 * Replace only adjustment bits; preserve keyboard visibility and navigation flags.
 */
@Suppress("DEPRECATION")
internal fun chatSoftInputMode(sdkInt: Int, currentMode: Int): Int {
    val adjustment = if (sdkInt < Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    } else {
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }
    return (currentMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjustment
}
