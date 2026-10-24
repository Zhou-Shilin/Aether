package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReasoningDocumentTest {
    @Test
    fun repeatedExcerptsNavigateToTheirOwnOccurrenceWithoutDroppingUnsubmittedText() {
        val document = reasoningRawDocument("same\n between\nsame\n still streaming", listOf(
            ReasoningRawSection("first", "same"), ReasoningRawSection("second", "same"),
        ))
        assertEquals(ReasoningRawRange(0, 4), document.ranges["first"])
        assertEquals(ReasoningRawRange(14, 18), document.ranges["second"])
        assertEquals("same\n between\nsame\n still streaming", document.text)
    }

    @Test
    fun legacyChunksFormContinuousDocumentAndMissingExcerptsDoNotTargetWrongText() {
        val sections = listOf(ReasoningRawSection("a", "first"), ReasoningRawSection("b", "second"))
        assertEquals("first\n\nsecond", reasoningRawDocument("", sections).text)
        assertEquals(ReasoningRawRange(7, 13), reasoningRawDocument("", sections).ranges["b"])
        assertFalse("b" in reasoningRawDocument("first", sections).ranges)
    }
}
