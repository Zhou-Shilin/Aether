package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlpineTerminalInputTest {
    @Test
    fun ctrlInputMapsLettersAndSymbolsToControlBytes() {
        assertEquals("\u0003\u0004\u001F\u0000", buildTerminalInputSequence("cd- ", ctrlDown = true, altDown = false, fnDown = false))
        assertEquals("\u001B", controlSequenceForCodePoint('['.code))
        assertEquals("\u001C", controlSequenceForCodePoint('\\'.code))
    }

    @Test
    fun altInputPrefixesEscape() {
        assertEquals("\u001Ba\u001Bb", buildTerminalInputSequence("ab", ctrlDown = false, altDown = true, fnDown = false))
    }

    @Test
    fun fnInputMatchesTermuxStyleNavigationAliases() {
        assertEquals("\u001B[A\u001B[D\u001B[B\u001B[C", buildTerminalInputSequence("wasd", ctrlDown = false, altDown = false, fnDown = true))
        assertEquals("\u001B[5~", terminalFnSequence('p'.code))
        assertEquals("\u001BOP", terminalFnSequence('1'.code))
        assertEquals("\u001Bb\u001Bf\u001Bx", buildTerminalInputSequence("bfx", ctrlDown = false, altDown = false, fnDown = true))
    }

    @Test
    fun bracketedPasteWrapsPayloadWhenRequested() {
        assertEquals("a\rb", buildTerminalPasteSequence("a\nb", bracketedPasteEnabled = false))
        assertEquals("\u001B[200~a\rb\u001B[201~", buildTerminalPasteSequence("a\nb", bracketedPasteEnabled = true))
        assertEquals("a\rb\rc", buildTerminalPasteSequence("a\r\nb\nc", bracketedPasteEnabled = false))
        assertEquals("a[31mbc", buildTerminalPasteSequence("a\u001B[31mb\u0085c", bracketedPasteEnabled = false))
    }

    @Test
    fun terminalUrlExtractionDeduplicatesAndTrimsShellPunctuation() {
        assertEquals(
            listOf(
                "https://example.com/docs?a=1",
                "http://localhost:8080/path(foo)",
                "file:///tmp/aether.log",
            ),
            extractTerminalUrls(
                """
                open https://example.com/docs?a=1.
                duplicate https://example.com/docs?a=1,
                bracketed (http://localhost:8080/path(foo)).
                file file:///tmp/aether.log]
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun terminalUrlHitDetectionUsesTextCellLocation() {
        val text = """
            no link here
            open https://example.com/docs?a=1.
            file file:///tmp/aether.log]
        """.trimIndent()

        assertEquals(
            "https://example.com/docs?a=1",
            findTerminalUrlAtCell(text = text, row = 1, column = 8),
        )
        assertEquals(
            "https://example.com/docs?a=1",
            findTerminalUrlAtCell(text = text, row = 1, column = 32),
        )
        assertEquals(
            null,
            findTerminalUrlAtCell(text = text, row = 1, column = 37),
        )
        assertEquals(
            "file:///tmp/aether.log",
            findTerminalUrlAtCell(text = text, row = 2, column = 7),
        )
    }

    @Test
    fun terminalLongPressHitDetectionDistinguishesTextAndBlankCells() {
        val text = "prompt\n  abc\n"

        assertEquals(true, isTerminalTextCellOccupied(text = text, row = 0, column = 0))
        assertEquals(false, isTerminalTextCellOccupied(text = text, row = 1, column = 0))
        assertEquals(true, isTerminalTextCellOccupied(text = text, row = 1, column = 2))
        assertEquals(false, isTerminalTextCellOccupied(text = text, row = 1, column = 8))
    }

    @Test
    fun terminalRenderedCellWidthCountsTerminalColumnsIncludingWideCharacters() {
        assertEquals(0, terminalRenderedCellWidth(""))
        assertEquals(3, terminalRenderedCellWidth("abc"))
        assertEquals(5, terminalRenderedCellWidth("a界bc"))
    }

    @Test
    fun terminalImeBackspaceEmitsDeleteEvenWhenOnlySentinelIsRemoved() {
        assertEquals("\u007F", terminalImeInputSequence(""))
        assertEquals("", terminalImeInputSequence("x"))
        assertEquals("abc", terminalImeInputSequence("xabc"))
        assertEquals("abc", terminalImeInputSequence("abc"))
        assertEquals("x", terminalImeInputSequence("xx"))
    }

    @Test
    fun terminalFontSizeChangesWithinTermuxStyleBounds() {
        assertEquals(14, nextTerminalFontSize(current = 13, increase = true))
        assertEquals(12, nextTerminalFontSize(current = 13, increase = false))
        assertEquals(10, nextTerminalFontSize(current = 10, increase = false))
        assertEquals(24, nextTerminalFontSize(current = 24, increase = true))
    }

    @Test
    fun terminalOutputOnlyAutoFollowsNearScrollbackBottom() {
        assertEquals(true, shouldFollowTerminalOutput(scrollValue = 0, maxScrollValue = 0))
        assertEquals(true, shouldFollowTerminalOutput(scrollValue = 980, maxScrollValue = 1_000))
        assertEquals(false, shouldFollowTerminalOutput(scrollValue = 900, maxScrollValue = 1_000))
    }

    @Test
    fun cursorKeysSwitchToApplicationModeSequencesWhenRequested() {
        assertEquals("\u001B[A", terminalCursorKeySequence(TerminalKey.ArrowUp, applicationCursorKeysEnabled = false))
        assertEquals("\u001BOA", terminalCursorKeySequence(TerminalKey.ArrowUp, applicationCursorKeysEnabled = true))
        assertEquals("\u001B[D", terminalCursorKeySequence(TerminalKey.ArrowLeft, applicationCursorKeysEnabled = false))
        assertEquals("\u001BOD", terminalCursorKeySequence(TerminalKey.ArrowLeft, applicationCursorKeysEnabled = true))
    }

    @Test
    fun modifiedSpecialKeysUseTermuxStyleModifierParameters() {
        assertEquals(2, terminalKeyModifier(shift = true, alt = false, ctrl = false))
        assertEquals(3, terminalKeyModifier(shift = false, alt = true, ctrl = false))
        assertEquals(5, terminalKeyModifier(shift = false, alt = false, ctrl = true))
        assertEquals(7, terminalKeyModifier(shift = false, alt = true, ctrl = true))

        assertEquals(
            "\u001B[1;5A",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.DirectionUp,
                shift = false,
                alt = false,
                ctrl = true,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
        assertEquals(
            "\u001B[5;3~",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.PageUp,
                shift = false,
                alt = true,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
        assertEquals(
            "\u001B[1;2P",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.F1,
                shift = true,
                alt = false,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
    }

    @Test
    fun enterAndNumpadEnterMatchTermuxKeyHandlerSequences() {
        assertEquals(
            "\r",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.Enter,
                shift = false,
                alt = false,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
        assertEquals(
            "\u001B\r",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.Enter,
                shift = false,
                alt = true,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
        assertEquals(
            "\n",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.NumPadEnter,
                shift = false,
                alt = false,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = false,
            ),
        )
        assertEquals(
            "\u001BOM",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.NumPadEnter,
                shift = false,
                alt = false,
                ctrl = false,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = true,
            ),
        )
        assertEquals(
            "\u001BO;5M",
            terminalSpecialKeySequence(
                key = androidx.compose.ui.input.key.Key.NumPadEnter,
                shift = false,
                alt = false,
                ctrl = true,
                applicationCursorKeysEnabled = false,
                applicationKeypadEnabled = true,
            ),
        )
    }

    @Test
    fun keypadKeysSwitchToApplicationModeSequencesWhenRequested() {
        assertEquals("1", terminalKeypadSequence('1', applicationKeypadEnabled = false))
        assertEquals("+", terminalKeypadSequence('+', applicationKeypadEnabled = false))
        assertEquals("\u001BOq", terminalKeypadSequence('1', applicationKeypadEnabled = true))
        assertEquals("\u001BOk", terminalKeypadSequence('+', applicationKeypadEnabled = true))
        assertEquals("\u001BOn", terminalKeypadSequence('.', applicationKeypadEnabled = true))
    }

    @Test
    fun modifiedApplicationKeypadKeysUseTermuxTransformFormat() {
        assertEquals(
            "\u001BO;5q",
            terminalKeypadSequence(
                '1',
                applicationKeypadEnabled = true,
                modifier = terminalKeyModifier(shift = false, alt = false, ctrl = true),
            ),
        )
        assertEquals(
            "\u001BO;3k",
            terminalKeypadSequence(
                '+',
                applicationKeypadEnabled = true,
                modifier = terminalKeyModifier(shift = false, alt = true, ctrl = false),
            ),
        )
    }

    @Test
    fun mouseSequencesSupportSgrAndNormalProtocols() {
        assertEquals(
            "\u001B[<0;4;3M",
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.PrimaryDown,
                column = 3,
                row = 2,
                protocol = TerminalMouseProtocol.Sgr,
            ),
        )
        assertEquals(
            "\u001B[<0;4;3m",
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.PrimaryUp,
                column = 3,
                row = 2,
                protocol = TerminalMouseProtocol.Sgr,
            ),
        )
        assertEquals(
            "\u001B[<64;1;1M",
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.WheelUp,
                column = 0,
                row = 0,
                protocol = TerminalMouseProtocol.Sgr,
            ),
        )
        assertEquals(
            "\u001B[M !!",
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.PrimaryDown,
                column = 0,
                row = 0,
                protocol = TerminalMouseProtocol.Normal,
            ),
        )
        assertEquals(
            null,
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.PrimaryDown,
                column = 223,
                row = 0,
                protocol = TerminalMouseProtocol.Normal,
            ),
        )
        assertEquals(
            "\u001B[<0;224;1M",
            buildTerminalMouseSequence(
                event = TerminalMouseEvent.PrimaryDown,
                column = 223,
                row = 0,
                protocol = TerminalMouseProtocol.Sgr,
            ),
        )
    }
}
