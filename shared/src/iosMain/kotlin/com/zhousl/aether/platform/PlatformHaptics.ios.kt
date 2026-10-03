package com.zhousl.aether.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private val hapticGenerator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)

/**
 * Short light impact used when the composer sends a message. The Android app
 * fires the matching tick from its own composer (ConversationUi.kt).
 * Must be called on the main thread.
 */
internal fun platformHapticFeedback() {
    hapticGenerator.impactOccurred()
}
