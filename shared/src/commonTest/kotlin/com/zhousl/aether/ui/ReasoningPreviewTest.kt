package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReasoningPreviewTest {
    @Test
    fun pendingPreviewTakesFirstHundredWordsAndPreservesFormatting() {
        val prefix = (1..100).joinToString("\n  ") { "word$it" }
        assertEquals(prefix, pendingReasoningPreview("  $prefix word101 word102"))
        assertEquals("Short\n excerpt", pendingReasoningPreview(" Short\n excerpt "))
        assertEquals("", pendingReasoningPreview(" \n "))
    }
}
