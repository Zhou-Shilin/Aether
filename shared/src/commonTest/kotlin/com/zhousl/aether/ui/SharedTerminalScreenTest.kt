package com.zhousl.aether.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTerminalScreenTest {
    @Test
    fun controlTextMatchesTermuxVirtualKeyboardMappings() {
        assertEquals("\u0001", transformNativeTerminalTextInput("a", controlDown = true, altDown = false))
        assertEquals("\u001f", transformNativeTerminalTextInput("/", controlDown = true, altDown = false))
        assertEquals("\u007f", transformNativeTerminalTextInput("8", controlDown = true, altDown = false))
        assertEquals("|", transformNativeTerminalTextInput("|", controlDown = true, altDown = false))
    }

    @Test
    fun altPrefixesEachInputCodePointAfterControlMapping() {
        assertEquals(
            "\u001b\u001f",
            transformNativeTerminalTextInput("/", controlDown = true, altDown = true),
        )
        assertEquals(
            "\u001ba\u001bb",
            transformNativeTerminalTextInput("ab", controlDown = false, altDown = true),
        )
    }
}
