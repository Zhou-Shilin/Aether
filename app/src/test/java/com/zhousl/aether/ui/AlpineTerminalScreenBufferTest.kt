package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlpineTerminalScreenBufferTest {
    @Test
    fun carriageReturnRewritesCurrentLine() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("progress 10%")
        val text = buffer.append("\rprogress 90%")

        assertEquals("progress 90%", text)
    }

    @Test
    fun clearScreenRemovesPreviousTranscript() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("before\n")
        val text = buffer.append("\u001B[2J\u001B[Hafter")

        assertEquals("after", text)
    }

    @Test
    fun deviceAttributesAndStatusReportsWriteResponses() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("abc\u001B[2D\u001B[c\u001B[>c\u001B[5n\u001B[6n\u001B[?6n")

        assertEquals(
            listOf(
                "\u001B[?64;1;2;6;9;15;18;21;22c",
                "\u001B[>41;320;0c",
                "\u001B[0n",
                "\u001B[1;2R",
                "\u001B[?1;2;1R",
            ),
            responses,
        )
    }

    @Test
    fun privateModeStatusQueriesReportTrackedModes() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("\u001B[?1\$p\u001B[?5\$p\u001B[?7\$p\u001B[?25\$p\u001B[?2004\$p\u001B[?9999\$p")
        buffer.append("\u001B[?1h\u001B[?5h\u001B[?25l\u001B[?2004h\u001B[?1\$p\u001B[?5\$p\u001B[?25\$p\u001B[?2004\$p")
        buffer.append("\u001B[?1049h\u001B[?1049\$p\u001B[?1049l\u001B[?1049\$p")

        assertEquals(
            listOf(
                "\u001B[?1;2\$y",
                "\u001B[?5;2\$y",
                "\u001B[?7;1\$y",
                "\u001B[?25;1\$y",
                "\u001B[?2004;2\$y",
                "\u001B[?9999;0\$y",
                "\u001B[?1;1\$y",
                "\u001B[?5;1\$y",
                "\u001B[?25;2\$y",
                "\u001B[?2004;1\$y",
                "\u001B[?1049;1\$y",
                "\u001B[?1049;2\$y",
            ),
            responses,
        )
    }

    @Test
    fun privateModeStatusQueriesReportMouseAndMarginModes() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("\u001B[?69h\u001B[?1002h\u001B[?1006h")
        buffer.append("\u001B[?69\$p\u001B[?1000\$p\u001B[?1002\$p\u001B[?1003\$p\u001B[?1004\$p\u001B[?1006\$p")
        buffer.append("\u001B[?1004h\u001B[?1004\$p")

        assertEquals(
            listOf(
                "\u001B[?69;1\$y",
                "\u001B[?1000;2\$y",
                "\u001B[?1002;1\$y",
                "\u001B[?1003;2\$y",
                "\u001B[?1004;2\$y",
                "\u001B[?1006;1\$y",
                "\u001B[?1004;1\$y",
            ),
            responses,
        )
    }

    @Test
    fun cursorMovementCanOverwriteVisibleCells() {
        val buffer = AlpineTerminalScreenBuffer()

        val text = buffer.append("abcd\u001B[2DXY")

        assertEquals("abXY", text)
    }

    @Test
    fun alternateScreenRestoresMainTranscript() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("main")
        assertEquals("alt", buffer.append("\u001B[?1049halt"))
        assertEquals("main", buffer.append("\u001B[?1049l"))
    }

    @Test
    fun alternateScreen1049RestoresCursorAndStyle() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[31mmain\u001B[?1049h\u001B[32malt\u001B[?1049lZ")
        val styledText = buffer.styledText

        assertEquals("mainZ", styledText.text)
        assertEquals(0xFFDC2626.toInt(), styledText.ranges.single().style.foregroundColor)
    }

    @Test
    fun bracketedPasteModeTracksPrivateMode() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[?2004h")
        assertEquals(true, buffer.isBracketedPasteEnabled)
        buffer.append("\u001B[?2004l")
        assertEquals(false, buffer.isBracketedPasteEnabled)
    }

    @Test
    fun mouseModesTrackPrivateSequences() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[?1003h")
        assertEquals(TerminalMouseTrackingMode.None, buffer.currentMouseTrackingMode)
        buffer.append("\u001B[?1000h")
        assertEquals(TerminalMouseTrackingMode.Click, buffer.currentMouseTrackingMode)
        assertEquals(TerminalMouseProtocol.Normal, buffer.currentMouseProtocol)
        buffer.append("\u001B[?1002h\u001B[?1003h\u001B[?1006h")
        assertEquals(TerminalMouseTrackingMode.Drag, buffer.currentMouseTrackingMode)
        assertEquals(TerminalMouseProtocol.Sgr, buffer.currentMouseProtocol)
        buffer.append("\u001B[?1003l")
        assertEquals(TerminalMouseTrackingMode.Drag, buffer.currentMouseTrackingMode)
        buffer.append("\u001B[?1002l\u001B[?1006l")
        assertEquals(TerminalMouseTrackingMode.None, buffer.currentMouseTrackingMode)
        assertEquals(TerminalMouseProtocol.Normal, buffer.currentMouseProtocol)
    }

    @Test
    fun focusEventModeTracksPrivateSequence() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[?1004h")
        assertEquals(true, buffer.isFocusEventsEnabled)
        buffer.append("\u001B[?1004l")
        assertEquals(false, buffer.isFocusEventsEnabled)
    }

    @Test
    fun applicationCursorKeyModeTracksPrivateSequence() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[?1h")
        assertEquals(true, buffer.isApplicationCursorKeysEnabled)
        buffer.append("\u001B[?1l")
        assertEquals(false, buffer.isApplicationCursorKeysEnabled)
    }

    @Test
    fun applicationKeypadModeTracksEscAndPrivateSequences() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B=")
        assertEquals(true, buffer.isApplicationKeypadEnabled)
        buffer.append("\u001B>")
        assertEquals(false, buffer.isApplicationKeypadEnabled)
        buffer.append("\u001B[?66h")
        assertEquals(true, buffer.isApplicationKeypadEnabled)
        buffer.append("\u001B[?66l")
        assertEquals(false, buffer.isApplicationKeypadEnabled)
    }

    @Test
    fun insertModeInsertsPrintableCharacters() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("abcd\u001B[2D\u001B[4hXY")

        assertEquals("abXYcd", buffer.text())
    }

    @Test
    fun decLineDrawingCharacterSetRendersCommonBoxCharacters() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B(0lqk\u001B(B\n\u001B(0x x\u001B(B\n\u001B(0mqj\u001B(B")

        assertEquals("┌─┐\n│ │\n└─┘", buffer.text())
    }

    @Test
    fun decScreenAlignmentFillsVisibleScreen() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 5)

        buffer.append("old\ntext\u001B#8")

        assertEquals("EEEEE\nEEEEE\nEEEEE", buffer.text())
        assertEquals(0, buffer.cursorRow)
        assertEquals(0, buffer.cursorColumn)
    }

    @Test
    fun escRisResetsTerminalStateLikeTermux() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 8)

        buffer.append("\u001B[31mred\u001B[?7l\u001B[?25l\u001B[?66h\u001B[?1002h\u001B[?1006h\u001B[?2004h")
        buffer.append("\u001B[?1049halt\u001Bcplain")

        assertEquals("plain", buffer.text())
        assertEquals(false, buffer.isAlternateScreenEnabled)
        assertEquals(false, buffer.isBracketedPasteEnabled)
        assertEquals(false, buffer.isApplicationKeypadEnabled)
        assertEquals(TerminalMouseTrackingMode.None, buffer.currentMouseTrackingMode)
        assertEquals(TerminalMouseProtocol.Normal, buffer.currentMouseProtocol)
        assertEquals(true, buffer.isCursorVisible)
        buffer.append("123456789")
        assertEquals("plain123\n456789", buffer.text())
        assertEquals(emptyList<TerminalStyledRange>(), buffer.styledText.ranges)
    }

    @Test
    fun sgrColorsAreTrackedAsStyledRanges() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("plain \u001B[31;1mred\u001B[0m normal")
        val styledText = buffer.styledText

        assertEquals("plain red normal", styledText.text)
        assertEquals(
            TerminalTextStyle(
                foregroundColor = 0xFFDC2626.toInt(),
                bold = true,
            ),
            styledText.ranges.single().style,
        )
        assertEquals(6, styledText.ranges.single().start)
        assertEquals(9, styledText.ranges.single().end)
    }

    @Test
    fun extendedSgrAttributesAreTrackedAndReset() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append(
            "\u001B[2mdim\u001B[22m " +
                "\u001B[3mitalic\u001B[23m " +
                "\u001B[4munder\u001B[24m " +
                "\u001B[8mhide\u001B[28m " +
                "\u001B[9mstrike\u001B[29m"
        )
        val ranges = buffer.styledText.ranges

        assertEquals(true, ranges[0].style.dim)
        assertEquals(false, ranges[0].style.bold)
        assertEquals(true, ranges[1].style.italic)
        assertEquals(true, ranges[2].style.underline)
        assertEquals(true, ranges[3].style.invisible)
        assertEquals(true, ranges[4].style.strikethrough)
    }

    @Test
    fun colonSeparatedSgrParametersMatchTermuxParsing() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append(
            "\u001B[4:3munder\u001B[4:0m plain " +
                "\u001B[38:2::1:2:3mrgb " +
                "\u001B[48:5:1mbg"
        )
        val styledText = buffer.styledText
        val ranges = styledText.ranges

        assertEquals("under plain rgb bg", styledText.text)
        assertEquals(0, ranges[0].start)
        assertEquals(5, ranges[0].end)
        assertEquals(true, ranges[0].style.underline)
        assertEquals(12, ranges[1].start)
        assertEquals(16, ranges[1].end)
        assertEquals(0xFF010203.toInt(), ranges[1].style.foregroundColor)
        assertEquals(16, ranges[2].start)
        assertEquals(18, ranges[2].end)
        assertEquals(0xFF010203.toInt(), ranges[2].style.foregroundColor)
        assertEquals(0xFFDC2626.toInt(), ranges[2].style.backgroundColor)
    }

    @Test
    fun reverseVideoPrivateModeAppliesGlobalInverseStyle() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("plain \u001B[?5hrev \u001B[7mnormal\u001B[27m still")
        val ranges = buffer.styledText.ranges

        assertEquals("plain rev normal still", buffer.styledText.text)
        assertEquals(0, ranges[0].start)
        assertEquals(10, ranges[0].end)
        assertEquals(true, ranges[0].style.inverse)
        assertEquals(10, ranges[1].start)
        assertEquals(16, ranges[1].end)
        assertEquals(false, ranges[1].style.inverse)
        assertEquals(16, ranges[2].start)
        assertEquals(22, ranges[2].end)
        assertEquals(true, ranges[2].style.inverse)

        buffer.append("\u001B[?5l plain")
        assertEquals(true, buffer.styledText.ranges.single().style.inverse)
        assertEquals(10, buffer.styledText.ranges.single().start)
        assertEquals(16, buffer.styledText.ranges.single().end)
    }

    @Test
    fun saveAndRestoreCursorPreservesStyleAndCharsetState() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("\u001B[32mx\u001B[31m\u001B(0\u001B7\u001B[0m\u001B(By\u001B8q")
        val styledText = buffer.styledText

        assertEquals("x─", styledText.text)
        assertEquals(0xFF16A34A.toInt(), styledText.ranges[0].style.foregroundColor)
        assertEquals(0xFFDC2626.toInt(), styledText.ranges[1].style.foregroundColor)
    }

    @Test
    fun autoWrapCanBeDisabledAndRestored() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 4)

        buffer.append("\u001B[?7labcdef")
        assertEquals("abcf", buffer.text())
        buffer.append("\u001B[?7h\nabcdX")
        assertEquals("abcf\nabcd\nX", buffer.text())
    }

    @Test
    fun insertAndDeleteCharactersEditCurrentLine() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("abcd\u001B[2D\u001B[2@XY")
        assertEquals("abXYcd", buffer.text())
        buffer.append("\u001B[2D\u001B[2P")
        assertEquals("abcd", buffer.text())
    }

    @Test
    fun customTabStopsCanBeSetClearedAndNavigated() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 20)

        buffer.append("\u001B[3gA\tB")
        assertEquals("A                  B", buffer.text())
        buffer.append("\r\u001B[5G\u001BH\rA\tB")
        assertEquals("A   B              B", buffer.text())
        buffer.append("\r\u001B[10G\u001B[ZC")
        assertEquals("A   C              B", buffer.text())
        buffer.append("\u001B[3g\rA\tD")
        assertEquals("A   C              D", buffer.text())
    }

    @Test
    fun oscTitleAndClipboardAreParsedWithoutRendering() {
        val clipboard = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onClipboardText = clipboard::add)

        buffer.append("one\u001B]2;Alpine shell\u0007two\u001B]52;c;Y29waWVkIHRleHQ=\u001B\\")

        assertEquals("onetwo", buffer.text())
        assertEquals("Alpine shell", buffer.currentTitle)
        assertEquals(listOf("copied text"), clipboard)
    }

    @Test
    fun oscPaletteQueriesSetsAndResetsIndexedColors() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("\u001B]4;1;?\u0007")
        buffer.append("\u001B]4;1;#123456\u001B\\\u001B[31mred")
        buffer.append("\u001B]4;1;?\u001B\\")
        val styledText = buffer.styledText

        assertEquals("red", styledText.text)
        assertEquals(0xFF123456.toInt(), styledText.ranges.single().style.foregroundColor)
        assertEquals(
            listOf(
                "\u001B]4;1;rgb:dcdc/2626/2626\u001B\\",
                "\u001B]4;1;rgb:1212/3434/5656\u001B\\",
            ),
            responses,
        )

        buffer.append("\u001B]104;1\u001B\\\u001B[31mreset")
        assertEquals(0xFFDC2626.toInt(), buffer.styledText.ranges.last().style.foregroundColor)
    }

    @Test
    fun oscSpecialColorQueriesSetsAndResets() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("\u001B]10;?;?;?\u0007")
        buffer.append("\u001B]10;rgb:1/2/3;#445566;#778899\u001B\\")
        buffer.append("\u001B]10;?\u001B\\\u001B]11;?\u001B\\\u001B]12;?\u001B\\")
        buffer.append("\u001B]110\u001B\\\u001B]10;?\u001B\\")

        assertEquals(
            listOf(
                "\u001B]10;rgb:e5e5/e7e7/ebeb\u001B\\",
                "\u001B]10;rgb:1111/1818/2727\u001B\\",
                "\u001B]10;rgb:e5e5/e7e7/ebeb\u001B\\",
                "\u001B]10;rgb:1111/2222/3333\u001B\\",
                "\u001B]11;rgb:4444/5555/6666\u001B\\",
                "\u001B]12;rgb:7777/8888/9999\u001B\\",
                "\u001B]10;rgb:e5e5/e7e7/ebeb\u001B\\",
            ),
            responses,
        )
    }

    @Test
    fun incompleteOscIsCompletedByNextAppend() {
        val buffer = AlpineTerminalScreenBuffer()

        buffer.append("before\u001B]0;Split")
        assertEquals("before", buffer.text())
        buffer.append(" title\u0007after")

        assertEquals("beforeafter", buffer.text())
        assertEquals("Split title", buffer.currentTitle)
    }

    @Test
    fun cursorShapeCanBeChangedWithDecScUsr() {
        val buffer = AlpineTerminalScreenBuffer()

        assertEquals(TerminalCursorStyle.Bar, buffer.currentCursorStyle)
        buffer.append("\u001B[4 q")
        assertEquals(TerminalCursorStyle.Underline, buffer.currentCursorStyle)
        buffer.append("\u001B[6 q")
        assertEquals(TerminalCursorStyle.Bar, buffer.currentCursorStyle)
        buffer.append("\u001B[2 q")
        assertEquals(TerminalCursorStyle.Block, buffer.currentCursorStyle)
    }

    @Test
    fun hpaRelativeMotionAndRepeatCharacterAreApplied() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 20)

        buffer.append("A\u001B[5`B\u001B[2aC\u001B[2eD\u001B[3b")

        assertEquals("A   B  C\n\n        DDDD", buffer.text())
    }

    @Test
    fun backspaceCanMoveAcrossAutoWrappedLine() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 4)

        buffer.append("abcdX\b\bY")

        assertEquals("abcY\nX", buffer.text())
        assertEquals(3, buffer.cursorColumn)
    }

    @Test
    fun wideAndCombiningCharactersAdvanceByTerminalCells() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 12)

        buffer.append("你B\u001B[1Gx")
        assertEquals("x B", buffer.text())
        assertEquals(1, buffer.cursorColumn)

        buffer.clear()
        buffer.append("e\u0301B")
        assertEquals("e\u0301B", buffer.text())
        assertEquals(2, buffer.cursorColumn)
    }

    @Test
    fun unicodeCellWidthsMatchTermuxWcWidthCases() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 12)

        buffer.append("A\uD83D\uDC28B")
        assertEquals(4, buffer.cursorColumn)

        buffer.clear()
        buffer.append("A\uD83D\uDF81B")
        assertEquals(3, buffer.cursorColumn)

        buffer.clear()
        buffer.append("A\u00ADB\u2060C")
        assertEquals("A\u00ADB\u2060C", buffer.text())
        assertEquals(4, buffer.cursorColumn)
    }

    @Test
    fun styledRangesUseTextOffsetsForWideCharacters() {
        val buffer = AlpineTerminalScreenBuffer(rows = 2, columns = 12)

        buffer.append("\u001B[31m你\u001B[0mB")
        val styledText = buffer.styledText

        assertEquals("你B", styledText.text)
        assertEquals(0, styledText.ranges.single().start)
        assertEquals(1, styledText.ranges.single().end)
    }

    @Test
    fun eraseDisplayModeThreeClearsOnlyScrollback() {
        val buffer = AlpineTerminalScreenBuffer(maxLines = 5, rows = 2, columns = 20)

        buffer.append("one\ntwo\nthree")
        assertEquals("one\ntwo\nthree", buffer.text())
        buffer.append("\u001B[3J")

        assertEquals("two\nthree", buffer.text())
    }

    @Test
    fun windowQueriesWriteTermuxStyleResponses() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(rows = 7, columns = 22, onResponse = responses::add)

        buffer.append("\u001B]2;Shell\u0007\u001B[11t\u001B[13t\u001B[18t\u001B[19t\u001B[20t\u001B[21t")

        assertEquals(
            listOf(
                "\u001B[1t",
                "\u001B[3;0;0t",
                "\u001B[8;7;22t",
                "\u001B[9;7;22t",
                "\u001B]LIconLabel\u001B\\",
                "\u001B]lShell\u001B\\",
            ),
            responses,
        )
    }

    @Test
    fun dcsStatusAndTermcapQueriesWriteResponsesWithoutRendering() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("x\u001BP\$q\"p\u001B\\y\u001BP+q6B75;436F;5858\u001B\\z")

        assertEquals("xyz", buffer.text())
        assertEquals(
            listOf(
                "\u001BP1\$r64;1\"p\u001B\\",
                "\u001BP1+r6B75=1B5B41;436F=323536\u001B\\",
            ),
            responses,
        )
    }

    @Test
    fun incompleteDcsIsCompletedByNextAppend() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("before\u001BP\$q")
        assertEquals("before", buffer.text())
        buffer.append("\"p\u001B\\after")

        assertEquals("beforeafter", buffer.text())
        assertEquals(listOf("\u001BP1\$r64;1\"p\u001B\\"), responses)
    }

    @Test
    fun unknownTermcapQueryWritesFailureResponse() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(onResponse = responses::add)

        buffer.append("\u001BP+q5858\u001B\\")

        assertEquals(listOf("\u001BP0+r\u001B\\"), responses)
    }

    @Test
    fun insertAndDeleteLinesEditScrollRegion() {
        val buffer = AlpineTerminalScreenBuffer(rows = 4, columns = 20)

        buffer.append("one\ntwo\nthree\u001B[2;1H\u001B[Linserted")
        assertEquals("one\ninserted\ntwo\nthree", buffer.text())
        buffer.append("\u001B[2;1H\u001B[M")
        assertEquals("one\ntwo\nthree", buffer.text())
    }

    @Test
    fun scrollRegionKeepsHeaderWhenContentScrolls() {
        val buffer = AlpineTerminalScreenBuffer(rows = 4, columns = 20)

        buffer.append("header\none\ntwo\nthree\u001B[2;4r\u001B[4;1H\nfour")

        assertEquals("header\ntwo\nthree\nfour", buffer.text())
    }

    @Test
    fun originModePositionsCursorRelativeToScrollRegion() {
        val buffer = AlpineTerminalScreenBuffer(rows = 5, columns = 20)

        buffer.append("header\none\ntwo\nthree\nfooter")
        buffer.append("\u001B[2;4r\u001B[?6h\u001B[1;1Htop")
        assertEquals("header\ntop\ntwo\nthree\nfooter", buffer.text())
        buffer.append("\u001B[3;1Hbottom")
        assertEquals("header\ntop\ntwo\nbottom\nfooter", buffer.text())
        buffer.append("\u001B[?6l\u001B[1;1Hroot")
        assertEquals("rooter\ntop\ntwo\nbottom\nfooter", buffer.text())
    }

    @Test
    fun leftRightMarginsConstrainCursorWrappingAndCarriageReturn() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 8)

        buffer.append("\u001B[?69h\u001B[3;6s\u001B[?6habcdefgh")
        buffer.append("\u001B[2;1H123\rZ")

        assertEquals("  abcd\n  Z23h", buffer.text())
        assertEquals(1, buffer.cursorRow)
        assertEquals(3, buffer.cursorColumn)
    }

    @Test
    fun escNextLineUsesTermuxOriginModeMarginSemantics() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 8)

        buffer.append("\u001B[?69h\u001B[3;6s\u001B[1;4Habc\u001BEZ")
        assertEquals("   abc\nZ", buffer.text())
        assertEquals(1, buffer.cursorRow)
        assertEquals(1, buffer.cursorColumn)

        buffer.clear()
        buffer.append("\u001B[?69h\u001B[3;6s\u001B[?6h\u001B[1;1Habc\u001BEZ")
        assertEquals("  abc\n  Z", buffer.text())
        assertEquals(1, buffer.cursorRow)
        assertEquals(3, buffer.cursorColumn)
    }

    @Test
    fun leftRightMarginsConstrainScrollRegion() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 8)

        buffer.append("AAAAAA\nBBBBBB\nCCCCCC")
        buffer.append("\u001B[?69h\u001B[3;6s\u001B[1;3r\u001B[3;3H\n")

        assertEquals("AABBBB\nBBCCCC\nCC", buffer.text())
    }

    @Test
    fun decColumnModeResetsMarginsAndClearsPageLikeTermux() {
        val responses = mutableListOf<String>()
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 8, onResponse = responses::add)

        buffer.append("AAAAAA\nBBBBBB\nCCCCCC")
        buffer.append("\u001B[?69h\u001B[3;6s\u001B[2;3r\u001B[3;3H")
        buffer.append("\u001B[?3h\u001B[?69\$pabcdefghi")

        assertEquals(listOf("\u001B[?69;2\$y"), responses)
        assertEquals("abcdefgh\ni", buffer.text())
        assertEquals(1, buffer.cursorRow)
        assertEquals(1, buffer.cursorColumn)

        buffer.append("\u001B[?69h\u001B[4;6s\u001B[2;3r\u001B[?3lZ")

        assertEquals("Z", buffer.text())
        assertEquals(0, buffer.cursorRow)
        assertEquals(1, buffer.cursorColumn)
    }

    @Test
    fun rectangularFillEraseAndCopyMatchTermuxSequences() {
        val buffer = AlpineTerminalScreenBuffer(rows = 4, columns = 8)

        buffer.append("abcdef\n123456\nUVWXYZ\nmnopqr")
        buffer.append("\u001B[42;2;3;3;5\$x")
        assertEquals("abcdef\n12***6\nUV***Z\nmnopqr", buffer.text())

        buffer.append("\u001B[2;4;3;4\$z")
        assertEquals("abcdef\n12* *6\nUV* *Z\nmnopqr", buffer.text())

        buffer.append("\u001B[2;3;3;5;1;4;2\$v")
        assertEquals("abcdef\n12* *6\nUV* *Z\nm* *qr", buffer.text())
    }

    @Test
    fun rectangularOperationsRespectOriginModeMargins() {
        val buffer = AlpineTerminalScreenBuffer(rows = 4, columns = 8)

        buffer.append("aaaaaaaa\nbbbbbbbb\ncccccccc\ndddddddd")
        buffer.append("\u001B[?69h\u001B[3;6s\u001B[2;3r\u001B[?6h")
        buffer.append("\u001B[88;1;1;2;2\$x")

        assertEquals("aaaaaaaa\nbbXXbbbb\nccXXcccc\ndddddddd", buffer.text())
    }

    @Test
    fun insertAndDeleteColumnsApplyToVisibleRowsWithinRightMargin() {
        val buffer = AlpineTerminalScreenBuffer(rows = 3, columns = 8)

        buffer.append("abcdef\n123456\nUVWXYZ")
        buffer.append("\u001B[2;3H\u001B[2'}")
        assertEquals("ab  cdef\n12  3456\nUV  WXYZ", buffer.text())

        buffer.append("\u001B[2;4H\u001B[2'~")
        assertEquals("ab defef\n12 45656\nUV XYZYZ", buffer.text())
    }

    @Test
    fun normalScrollKeepsTranscriptHistory() {
        val buffer = AlpineTerminalScreenBuffer(maxLines = 6, rows = 3, columns = 20)

        buffer.append("one\ntwo\nthree\nfour\nfive")

        assertEquals("one\ntwo\nthree\nfour\nfive", buffer.text())
    }

    @Test
    fun transcriptHistoryIsBoundedByMaxLines() {
        val buffer = AlpineTerminalScreenBuffer(maxLines = 4, rows = 2, columns = 20)

        buffer.append("one\ntwo\nthree\nfour\nfive")

        assertEquals("two\nthree\nfour\nfive", buffer.text())
    }
}
