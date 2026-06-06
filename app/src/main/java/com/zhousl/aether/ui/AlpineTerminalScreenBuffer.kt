package com.zhousl.aether.ui

import java.util.Base64

internal class AlpineTerminalScreenBuffer(
    private val maxLines: Int = 2_000,
    rows: Int = 32,
    columns: Int = 96,
    private val onResponse: (String) -> Unit = {},
    private val onClipboardText: (String) -> Unit = {},
) {
    private val mainScreen = TerminalScreen(maxLines = maxLines, rows = rows, columns = columns)
    private val alternateScreen = TerminalScreen(maxLines = rows.coerceAtLeast(1), rows = rows, columns = columns)
    private var activeScreen = mainScreen
    private var pendingEscape = ""
    private var bracketedPasteEnabled = false
    private var alternateScreenEnabled = false
    private var mouseTrackingMode = TerminalMouseTrackingMode.None
    private var mouseProtocol = TerminalMouseProtocol.Normal
    private var applicationCursorKeysEnabled = false
    private var applicationKeypadEnabled = false
    private var insertModeEnabled = false
    private var autoWrapEnabled = true
    private var reverseVideoEnabled = false
    private var originModeEnabled = false
    private var leftRightMarginModeEnabled = false
    private var cursorVisible = true
    private var cursorStyle = TerminalCursorStyle.Bar
    private var focusEventsEnabled = false
    private var title = ""
    private var g0LineDrawingEnabled = false
    private var g1LineDrawingEnabled = false
    private var useG1CharacterSet = false
    private var currentStyle = TerminalTextStyle()
    private val defaultColors = terminalDefaultColors()
    private var currentColors = defaultColors.copyOf()
    private var lastGraphicText: String? = null
    private var savedState = SavedTerminalState()

    val isBracketedPasteEnabled: Boolean
        get() = bracketedPasteEnabled

    val currentMouseTrackingMode: TerminalMouseTrackingMode
        get() = mouseTrackingMode

    val currentMouseProtocol: TerminalMouseProtocol
        get() = mouseProtocol

    val isAlternateScreenEnabled: Boolean
        get() = alternateScreenEnabled

    val isApplicationCursorKeysEnabled: Boolean
        get() = applicationCursorKeysEnabled

    val isApplicationKeypadEnabled: Boolean
        get() = applicationKeypadEnabled

    val cursorRow: Int
        get() = activeScreen.displayCursorRow

    val cursorColumn: Int
        get() = activeScreen.cursorColumn

    val isCursorVisible: Boolean
        get() = cursorVisible

    val isReverseVideoEnabled: Boolean
        get() = reverseVideoEnabled

    val currentCursorStyle: TerminalCursorStyle
        get() = cursorStyle

    val isFocusEventsEnabled: Boolean
        get() = focusEventsEnabled

    val currentTitle: String
        get() = title

    val styledText: TerminalStyledText
        get() = activeScreen.styledText().withReverseVideo(reverseVideoEnabled)

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        mainScreen.resize(rows, columns)
        alternateScreen.resize(rows, columns)
    }

    fun clear() {
        mainScreen.clear()
        alternateScreen.clear()
        activeScreen = mainScreen
        pendingEscape = ""
        bracketedPasteEnabled = false
        alternateScreenEnabled = false
        mouseTrackingMode = TerminalMouseTrackingMode.None
        mouseProtocol = TerminalMouseProtocol.Normal
        applicationCursorKeysEnabled = false
        applicationKeypadEnabled = false
        insertModeEnabled = false
        autoWrapEnabled = true
        reverseVideoEnabled = false
        originModeEnabled = false
        leftRightMarginModeEnabled = false
        cursorVisible = true
        cursorStyle = TerminalCursorStyle.Bar
        focusEventsEnabled = false
        title = ""
        g0LineDrawingEnabled = false
        g1LineDrawingEnabled = false
        useG1CharacterSet = false
        currentStyle = TerminalTextStyle()
        currentColors = defaultColors.copyOf()
        lastGraphicText = null
        savedState = SavedTerminalState()
    }

    fun append(chunk: String): String {
        val input = pendingEscape + chunk
        pendingEscape = ""
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when (char) {
                '\u001B' -> {
                    val consumed = consumeEscape(input, index)
                    if (consumed == 0) {
                        pendingEscape = input.substring(index)
                        index = input.length
                    } else {
                        index += consumed
                    }
                }
                '\r' -> {
                    activeScreen.carriageReturn()
                    index++
                }
                '\n' -> {
                    activeScreen.newLine()
                    index++
                }
                '\b' -> {
                    activeScreen.backspace()
                    index++
                }
                '\u000E' -> {
                    useG1CharacterSet = true
                    index++
                }
                '\u000F' -> {
                    useG1CharacterSet = false
                    index++
                }
                '\t' -> {
                    activeScreen.tab()
                    index++
                }
                '\u0007' -> index++
                else -> {
                    if (!char.isISOControl()) {
                        val printable = readPrintable(input, index)
                        activeScreen.putText(
                            text = printable.text,
                            width = printable.width,
                            style = currentStyle,
                            insertMode = insertModeEnabled,
                            autoWrap = autoWrapEnabled,
                        )
                        if (printable.width > 0) lastGraphicText = printable.text
                        index += printable.consumed
                    } else {
                        index++
                    }
                }
            }
        }
        return text()
    }

    fun text(): String = activeScreen.text()

    private fun consumeEscape(
        input: String,
        start: Int,
    ): Int {
        if (start + 1 >= input.length) return 0
        return when (input[start + 1]) {
            '[' -> consumeCsi(input, start)
            ']' -> consumeOsc(input, start)
            'P' -> consumeDcs(input, start)
            '_', '^', 'X' -> consumeStringControl(input, start)
            '(', ')' -> consumeCharacterSetSelection(input, start)
            '#' -> consumeEscPound(input, start)
            '=' -> {
                applicationKeypadEnabled = true
                2
            }
            '>' -> {
                applicationKeypadEnabled = false
                2
            }
            '7' -> {
                saveCursor()
                2
            }
            '8' -> {
                restoreCursor()
                2
            }
            'c' -> {
                clear()
                2
            }
            'D' -> {
                activeScreen.index()
                2
            }
            'E' -> {
                activeScreen.nextLine(originModeEnabled)
                2
            }
            'H' -> {
                activeScreen.setTabStop()
                2
            }
            'M' -> {
                activeScreen.reverseIndex()
                2
            }
            else -> 2
        }
    }

    private fun consumeEscPound(
        input: String,
        start: Int,
    ): Int {
        if (start + 2 >= input.length) return 0
        if (input[start + 2] == '8') activeScreen.fillScreen('E', currentStyle)
        return 3
    }

    private fun consumeCharacterSetSelection(
        input: String,
        start: Int,
    ): Int {
        if (start + 2 >= input.length) return 0
        val isG0 = input[start + 1] == '('
        val lineDrawing = input[start + 2] == '0'
        if (isG0) {
            g0LineDrawingEnabled = lineDrawing
        } else {
            g1LineDrawingEnabled = lineDrawing
        }
        return 3
    }

    private fun consumeCsi(
        input: String,
        start: Int,
    ): Int {
        var end = start + 2
        while (end < input.length) {
            val char = input[end]
            if (char in '@'..'~') {
                applyCsi(input.substring(start + 2, end), char)
                return end - start + 1
            }
            end++
        }
        return 0
    }

    private fun consumeOsc(
        input: String,
        start: Int,
    ): Int {
        val control = readStringControl(input, start) ?: return 0
        applyOsc(control.content)
        return control.consumed
    }

    private fun consumeDcs(
        input: String,
        start: Int,
    ): Int {
        val control = readStringControl(input, start) ?: return 0
        applyDcs(control.content)
        return control.consumed
    }

    private fun consumeStringControl(
        input: String,
        start: Int,
    ): Int {
        return readStringControl(input, start)?.consumed ?: 0
    }

    private fun readStringControl(
        input: String,
        start: Int,
    ): TerminalStringControl? {
        var end = start + 2
        while (end < input.length) {
            if (input[end] == '\u0007') {
                return TerminalStringControl(
                    content = input.substring(start + 2, end),
                    consumed = end - start + 1,
                )
            }
            if (input[end] == '\u001B' && end + 1 < input.length && input[end + 1] == '\\') {
                return TerminalStringControl(
                    content = input.substring(start + 2, end),
                    consumed = end - start + 2,
                )
            }
            end++
        }
        return null
    }

    private fun applyOsc(content: String) {
        val command = content.substringBefore(';').toIntOrNull() ?: return
        val parameter = content.substringAfter(';', missingDelimiterValue = "")
        when (command) {
            0, 2 -> title = parameter
            4 -> applyOscPalette(parameter)
            10, 11, 12 -> applyOscSpecialColors(command, parameter)
            52 -> decodeOsc52Clipboard(parameter)?.let(onClipboardText)
            104 -> resetOscPalette(parameter)
            110, 111, 112 -> {
                currentColors[TERMINAL_COLOR_FOREGROUND + (command - 110)] =
                    defaultColors[TERMINAL_COLOR_FOREGROUND + (command - 110)]
            }
        }
    }

    private fun applyOscPalette(parameter: String) {
        val parts = parameter.split(';')
        var index = 0
        while (index + 1 < parts.size) {
            val colorIndex = parts[index].toIntOrNull()
            val spec = parts[index + 1]
            if (colorIndex != null && colorIndex in 0..255) {
                if (spec == "?") {
                    respondOscColor(4, colorIndex, currentColors[colorIndex])
                } else {
                    parseTerminalColorSpec(spec)?.let { currentColors[colorIndex] = it }
                }
            }
            index += 2
        }
    }

    private fun applyOscSpecialColors(
        command: Int,
        parameter: String,
    ) {
        var colorIndex = TERMINAL_COLOR_FOREGROUND + (command - 10)
        parameter.split(';').forEach { spec ->
            if (colorIndex > TERMINAL_COLOR_CURSOR) return
            if (spec == "?") {
                respondOscColor(command, null, currentColors[colorIndex])
            } else {
                parseTerminalColorSpec(spec)?.let { currentColors[colorIndex] = it }
            }
            colorIndex++
        }
    }

    private fun resetOscPalette(parameter: String) {
        if (parameter.isEmpty()) {
            currentColors = defaultColors.copyOf()
            return
        }
        parameter.split(';').forEach { token ->
            val colorIndex = token.toIntOrNull()
            if (colorIndex != null && colorIndex in 0..255) {
                currentColors[colorIndex] = defaultColors[colorIndex]
            }
        }
    }

    private fun respondOscColor(
        command: Int,
        colorIndex: Int?,
        color: Int,
    ) {
        val prefix = if (colorIndex == null) {
            "\u001B]$command;"
        } else {
            "\u001B]$command;$colorIndex;"
        }
        onResponse(prefix + terminalColorToOscRgb(color) + "\u001B\\")
    }

    private fun applyDcs(content: String) {
        when {
            content == "\$q\"p" -> onResponse("\u001BP1\$r64;1\"p\u001B\\")
            content.startsWith("+q") -> respondTermcapQuery(content.removePrefix("+q"))
        }
    }

    private fun respondTermcapQuery(encodedNames: String) {
        val responseParts = encodedNames.split(';')
            .mapNotNull { encodedName ->
                val name = decodeHexAscii(encodedName) ?: return@mapNotNull null
                val value = terminalTermcapValue(name) ?: return@mapNotNull null
                encodedName + "=" + encodeHexAscii(value)
            }
        if (responseParts.isEmpty()) {
            onResponse("\u001BP0+r\u001B\\")
        } else {
            onResponse("\u001BP1+r${responseParts.joinToString(";")}\u001B\\")
        }
    }

    private fun mapCharacterSet(char: Char): Char {
        val lineDrawing = if (useG1CharacterSet) g1LineDrawingEnabled else g0LineDrawingEnabled
        if (!lineDrawing) return char
        return when (char) {
            '`' -> '\u25C6'
            'a' -> '\u2592'
            'f' -> '\u00B0'
            'g' -> '\u00B1'
            'h' -> '\u2424'
            'i' -> '\u240B'
            'j' -> '\u2518'
            'k' -> '\u2510'
            'l' -> '\u250C'
            'm' -> '\u2514'
            'n' -> '\u253C'
            'o' -> '\u23BA'
            'p' -> '\u23BB'
            'q' -> '\u2500'
            'r' -> '\u23BC'
            's' -> '\u23BD'
            't' -> '\u251C'
            'u' -> '\u2524'
            'v' -> '\u2534'
            'w' -> '\u252C'
            'x' -> '\u2502'
            'y' -> '\u2264'
            'z' -> '\u2265'
            '{' -> '\u03C0'
            '|' -> '\u2260'
            '}' -> '\u00A3'
            '~' -> '\u00B7'
            else -> char
        }
    }

    private fun readPrintable(
        input: String,
        index: Int,
    ): TerminalPrintable {
        val char = input[index]
        val consumed = if (char.isHighSurrogate() &&
            index + 1 < input.length &&
            input[index + 1].isLowSurrogate()
        ) {
            2
        } else {
            1
        }
        val rawText = input.substring(index, index + consumed)
        val text = if (consumed == 1) mapCharacterSet(char).toString() else rawText
        return TerminalPrintable(
            text = text,
            width = terminalCellWidth(text),
            consumed = consumed,
        )
    }

    private fun applyCsi(
        rawParams: String,
        final: Char,
    ) {
        val privateMode = rawParams.startsWith("?")
        val secondaryDeviceAttributes = rawParams.startsWith(">")
        val intermediate = rawParams.takeLastWhile { it in ' '..'/' }
        val cleanParams = rawParams.dropLast(intermediate.length)
            .trimStart('?')
            .trimStart('>')
            .trimEnd { it in ' '..'/' }
        val csiParams = parseCsiParameters(cleanParams)
        val params = csiParams.mapNotNull { it.value }
        fun param(index: Int, default: Int): Int =
            params.getOrNull(index)?.takeIf { it > 0 } ?: default

        if (intermediate == "$") {
            if (privateMode && final == 'p') {
                respondPrivateModeStatus(param(0, 0))
                return
            }
            when (final) {
                'v' -> activeScreen.copyRectangle(params, originModeEnabled)
                'x' -> activeScreen.fillRectangle(params, currentStyle, originModeEnabled)
                'z', '{' -> activeScreen.eraseRectangle(params, currentStyle, originModeEnabled)
                'r' -> activeScreen.changeRectangleAttributes(params, reverse = false, originMode = originModeEnabled)
                't' -> activeScreen.changeRectangleAttributes(params, reverse = true, originMode = originModeEnabled)
            }
            return
        }

        if (intermediate == "'") {
            when (final) {
                '}' -> activeScreen.insertColumns(param(0, 1))
                '~' -> activeScreen.deleteColumns(param(0, 1))
            }
            return
        }

        when (final) {
            'A' -> activeScreen.cursorUp(param(0, 1))
            'B', 'e' -> activeScreen.cursorDown(param(0, 1))
            'C', 'a' -> activeScreen.cursorForward(param(0, 1))
            'D' -> activeScreen.cursorBack(param(0, 1))
            'E' -> activeScreen.cursorNextLine(param(0, 1), originModeEnabled)
            'F' -> activeScreen.cursorPreviousLine(param(0, 1), originModeEnabled)
            'G' -> activeScreen.setColumn(param(0, 1) - 1)
            'H', 'f' -> activeScreen.setCursor(
                row = param(0, 1) - 1,
                column = param(1, 1) - 1,
                originMode = originModeEnabled,
            )
            'I' -> activeScreen.forwardTab(param(0, 1))
            'J' -> activeScreen.eraseDisplay(params.firstOrNull() ?: 0)
            'K' -> activeScreen.eraseLine(params.firstOrNull() ?: 0)
            'L' -> activeScreen.insertLines(param(0, 1))
            'M' -> activeScreen.deleteLines(param(0, 1))
            'P' -> activeScreen.deleteCharacters(param(0, 1))
            '@' -> activeScreen.insertBlankCharacters(param(0, 1))
            'S' -> activeScreen.scrollUp(param(0, 1))
            'T' -> activeScreen.scrollDown(param(0, 1))
            'X' -> activeScreen.eraseCharacters(param(0, 1))
            'Z' -> activeScreen.backwardTab(param(0, 1))
            '`' -> activeScreen.setColumn(param(0, 1) - 1)
            'b' -> repeatGraphicCharacter(param(0, 1))
            'c' -> respondDeviceAttributes(params, secondaryDeviceAttributes)
            'd' -> activeScreen.setRow(param(0, 1) - 1, originModeEnabled)
            'g' -> activeScreen.clearTabStop(params.firstOrNull() ?: 0)
            'n' -> respondDeviceStatus(params, privateMode)
            'r' -> activeScreen.setScrollRegion(param(0, 1) - 1, param(1, activeScreen.rows) - 1)
            's' -> if (leftRightMarginModeEnabled) {
                activeScreen.setHorizontalMargins(param(0, 1) - 1, param(1, activeScreen.columnCount) - 1)
            } else {
                saveCursor()
            }
            't' -> respondWindowManipulation(params)
            'u' -> restoreCursor()
            'q' -> if (intermediate == " ") applyCursorStyle(params.firstOrNull() ?: 0)
            'h' -> if (privateMode) params.forEach(::setPrivateMode) else params.forEach(::setMode)
            'l' -> if (privateMode) params.forEach(::resetPrivateMode) else params.forEach(::resetMode)
            'm' -> applyGraphicRendition(csiParams)
        }
    }

    private fun parseCsiParameters(params: String): List<CsiParameter> {
        if (params.isEmpty()) return emptyList()
        val result = mutableListOf<CsiParameter>()
        var tokenStart = 0
        var subParameter = false
        params.forEachIndexed { index, char ->
            if (char == ';' || char == ':') {
                result += CsiParameter(
                    value = params.substring(tokenStart, index).toIntOrNull(),
                    isSubParameter = subParameter,
                )
                tokenStart = index + 1
                subParameter = char == ':'
            }
        }
        result += CsiParameter(
            value = params.substring(tokenStart).toIntOrNull(),
            isSubParameter = subParameter,
        )
        return result
    }

    private fun respondPrivateModeStatus(mode: Int) {
        val value = when (mode) {
            1 -> if (applicationCursorKeysEnabled) 1 else 2
            5 -> if (reverseVideoEnabled) 1 else 2
            6 -> if (originModeEnabled) 1 else 2
            7 -> if (autoWrapEnabled) 1 else 2
            25 -> if (cursorVisible) 1 else 2
            47, 1047, 1049 -> if (alternateScreenEnabled) 1 else 2
            66 -> if (applicationKeypadEnabled) 1 else 2
            69 -> if (leftRightMarginModeEnabled) 1 else 2
            1000 -> if (mouseTrackingMode == TerminalMouseTrackingMode.Click) 1 else 2
            1002 -> if (mouseTrackingMode == TerminalMouseTrackingMode.Drag) 1 else 2
            1003 -> 2
            1004 -> if (focusEventsEnabled) 1 else 2
            1006 -> if (mouseProtocol == TerminalMouseProtocol.Sgr) 1 else 2
            2004 -> if (bracketedPasteEnabled) 1 else 2
            else -> 0
        }
        onResponse("\u001B[?$mode;$value\$y")
    }

    private fun applyCursorStyle(style: Int) {
        cursorStyle = when (style) {
            1, 2 -> TerminalCursorStyle.Block
            3, 4 -> TerminalCursorStyle.Underline
            5, 6 -> TerminalCursorStyle.Bar
            else -> TerminalCursorStyle.Bar
        }
    }

    private fun saveCursor() {
        activeScreen.saveCursor()
        savedState = SavedTerminalState(
            currentStyle = currentStyle,
            autoWrapEnabled = autoWrapEnabled,
            originModeEnabled = originModeEnabled,
            g0LineDrawingEnabled = g0LineDrawingEnabled,
            g1LineDrawingEnabled = g1LineDrawingEnabled,
            useG1CharacterSet = useG1CharacterSet,
        )
    }

    private fun restoreCursor() {
        activeScreen.restoreCursor()
        currentStyle = savedState.currentStyle
        autoWrapEnabled = savedState.autoWrapEnabled
        originModeEnabled = savedState.originModeEnabled
        g0LineDrawingEnabled = savedState.g0LineDrawingEnabled
        g1LineDrawingEnabled = savedState.g1LineDrawingEnabled
        useG1CharacterSet = savedState.useG1CharacterSet
    }

    private fun repeatGraphicCharacter(count: Int) {
        val text = lastGraphicText ?: return
        val width = terminalCellWidth(text)
        repeat(count) {
            activeScreen.putText(
                text = text,
                width = width,
                style = currentStyle,
                insertMode = insertModeEnabled,
                autoWrap = autoWrapEnabled,
            )
        }
    }

    private fun respondWindowManipulation(params: List<Int>) {
        when (params.firstOrNull() ?: 0) {
            11 -> onResponse("\u001B[1t")
            13 -> onResponse("\u001B[3;0;0t")
            18 -> onResponse("\u001B[8;${activeScreen.rows};${activeScreen.columnCount}t")
            19 -> onResponse("\u001B[9;${activeScreen.rows};${activeScreen.columnCount}t")
            20 -> onResponse("\u001B]LIconLabel\u001B\\")
            21 -> onResponse("\u001B]l${title}\u001B\\")
        }
    }

    private fun respondDeviceAttributes(
        params: List<Int>,
        secondary: Boolean,
    ) {
        if ((params.firstOrNull() ?: 0) != 0) return
        onResponse(
            if (secondary) {
                "\u001B[>41;320;0c"
            } else {
                "\u001B[?64;1;2;6;9;15;18;21;22c"
            }
        )
    }

    private fun respondDeviceStatus(
        params: List<Int>,
        privateMode: Boolean,
    ) {
        val query = params.firstOrNull() ?: 0
        when {
            privateMode && query == 6 -> {
                onResponse("\u001B[?${activeScreen.screenCursorRow + 1};${activeScreen.cursorColumn + 1};1R")
            }
            !privateMode && query == 5 -> onResponse("\u001B[0n")
            !privateMode && query == 6 -> {
                onResponse("\u001B[${activeScreen.screenCursorRow + 1};${activeScreen.cursorColumn + 1}R")
            }
        }
    }

    private fun applyGraphicRendition(params: List<CsiParameter>) {
        val effectiveParams = if (params.any { it.value != null }) {
            params
        } else {
            listOf(CsiParameter(value = 0, isSubParameter = false))
        }
        var index = 0
        while (index < effectiveParams.size) {
            val parameter = effectiveParams[index]
            if (parameter.isSubParameter) {
                index++
                continue
            }
            when (val param = parameter.value) {
                null -> Unit
                0 -> currentStyle = TerminalTextStyle()
                1 -> currentStyle = currentStyle.copy(bold = true)
                2 -> currentStyle = currentStyle.copy(dim = true)
                3 -> currentStyle = currentStyle.copy(italic = true)
                4 -> {
                    val underline = effectiveParams.getOrNull(index + 1)
                        ?.takeIf { it.isSubParameter }
                    if (underline != null) {
                        currentStyle = currentStyle.copy(underline = underline.value != 0)
                        index++
                    } else {
                        currentStyle = currentStyle.copy(underline = true)
                    }
                }
                7 -> currentStyle = currentStyle.copy(inverse = true)
                8 -> currentStyle = currentStyle.copy(invisible = true)
                9 -> currentStyle = currentStyle.copy(strikethrough = true)
                22 -> currentStyle = currentStyle.copy(bold = false, dim = false)
                23 -> currentStyle = currentStyle.copy(italic = false)
                24 -> currentStyle = currentStyle.copy(underline = false)
                27 -> currentStyle = currentStyle.copy(inverse = false)
                28 -> currentStyle = currentStyle.copy(invisible = false)
                29 -> currentStyle = currentStyle.copy(strikethrough = false)
                30, 31, 32, 33, 34, 35, 36, 37 ->
                    currentStyle = currentStyle.copy(foregroundColor = currentColors[param - 30])
                90, 91, 92, 93, 94, 95, 96, 97 ->
                    currentStyle = currentStyle.copy(foregroundColor = currentColors[param - 90 + 8])
                39 -> currentStyle = currentStyle.copy(foregroundColor = null)
                40, 41, 42, 43, 44, 45, 46, 47 ->
                    currentStyle = currentStyle.copy(backgroundColor = currentColors[param - 40])
                100, 101, 102, 103, 104, 105, 106, 107 ->
                    currentStyle = currentStyle.copy(backgroundColor = currentColors[param - 100 + 8])
                49 -> currentStyle = currentStyle.copy(backgroundColor = null)
                38, 48 -> {
                    val parsed = parseExtendedColor(effectiveParams, index + 1)
                    if (parsed != null) {
                        currentStyle = if (param == 38) {
                            currentStyle.copy(foregroundColor = parsed.color)
                        } else {
                            currentStyle.copy(backgroundColor = parsed.color)
                        }
                        index += parsed.consumed
                    }
                }
            }
            index++
        }
    }

    private fun parseExtendedColor(
        params: List<CsiParameter>,
        start: Int,
    ): ParsedTerminalColor? {
        return when (params.getOrNull(start)?.value) {
            5 -> {
                val colorIndex = params.getOrNull(start + 1)?.value ?: return null
                ParsedTerminalColor(color = currentColors[colorIndex.coerceIn(0, 255)], consumed = 2)
            }
            2 -> {
                val rgbStart = extendedRgbStart(params, start + 1)
                val red = params.getOrNull(rgbStart)?.value ?: return null
                val green = params.getOrNull(rgbStart + 1)?.value ?: return null
                val blue = params.getOrNull(rgbStart + 2)?.value ?: return null
                ParsedTerminalColor(
                    color = terminalRgbColor(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)),
                    consumed = rgbStart - start + 3,
                )
            }
            else -> null
        }
    }

    private fun extendedRgbStart(
        params: List<CsiParameter>,
        start: Int,
    ): Int {
        val next = params.getOrNull(start) ?: return start
        if (!next.isSubParameter) return start
        if (next.value == null) return start + 1
        val hasColorSpaceAndRgb = params.getOrNull(start + 3)?.isSubParameter == true &&
            params.getOrNull(start + 4)?.isSubParameter == true
        return if (hasColorSpaceAndRgb) start + 1 else start
    }

    private fun setMode(mode: Int) {
        when (mode) {
            4 -> insertModeEnabled = true
        }
    }

    private fun resetMode(mode: Int) {
        when (mode) {
            4 -> insertModeEnabled = false
        }
    }

    private fun setPrivateMode(mode: Int) {
        when (mode) {
            1 -> applicationCursorKeysEnabled = true
            3 -> resetColumnModeSideEffects()
            5 -> reverseVideoEnabled = true
            6 -> {
                originModeEnabled = true
                activeScreen.setCursor(0, 0, originMode = true)
            }
            7 -> autoWrapEnabled = true
            69 -> leftRightMarginModeEnabled = true
            25 -> cursorVisible = true
            66 -> applicationKeypadEnabled = true
            1048 -> saveCursor()
            1047 -> enableAlternateScreen(clear = true, saveCursor = false)
            1049 -> enableAlternateScreen(clear = true, saveCursor = true)
            47 -> enableAlternateScreen(clear = false)
            1000 -> mouseTrackingMode = TerminalMouseTrackingMode.Click
            1002 -> mouseTrackingMode = TerminalMouseTrackingMode.Drag
            1003 -> Unit
            1004 -> focusEventsEnabled = true
            1006 -> mouseProtocol = TerminalMouseProtocol.Sgr
            2004 -> bracketedPasteEnabled = true
        }
    }

    private fun resetPrivateMode(mode: Int) {
        when (mode) {
            1 -> applicationCursorKeysEnabled = false
            3 -> resetColumnModeSideEffects()
            5 -> reverseVideoEnabled = false
            6 -> {
                originModeEnabled = false
                activeScreen.setCursor(0, 0, originMode = false)
            }
            7 -> autoWrapEnabled = false
            69 -> {
                leftRightMarginModeEnabled = false
                activeScreen.resetHorizontalMargins()
            }
            25 -> cursorVisible = false
            66 -> applicationKeypadEnabled = false
            1048 -> restoreCursor()
            47, 1047 -> disableAlternateScreen(restoreCursor = false)
            1049 -> disableAlternateScreen(restoreCursor = true)
            1000, 1002 -> mouseTrackingMode = TerminalMouseTrackingMode.None
            1003 -> Unit
            1004 -> focusEventsEnabled = false
            1006 -> mouseProtocol = TerminalMouseProtocol.Normal
            2004 -> bracketedPasteEnabled = false
        }
    }

    private fun resetColumnModeSideEffects() {
        leftRightMarginModeEnabled = false
        activeScreen.resetMarginsAndClearPage()
    }

    private fun enableAlternateScreen(
        clear: Boolean,
        saveCursor: Boolean = false,
    ) {
        if (saveCursor) saveCursor()
        alternateScreenEnabled = true
        activeScreen = alternateScreen
        if (clear) activeScreen.clear()
    }

    private fun disableAlternateScreen(restoreCursor: Boolean = false) {
        alternateScreenEnabled = false
        activeScreen = mainScreen
        if (restoreCursor) restoreCursor()
    }
}

private class TerminalScreen(
    private val maxLines: Int,
    rows: Int,
    columns: Int,
) {
    var rows: Int = rows.coerceAtLeast(1)
        private set
    private var columns: Int = columns.coerceAtLeast(1)
    private val historyLines = mutableListOf<TerminalLine>()
    private val lines = mutableListOf(TerminalLine())
    private var row = 0
    private var column = 0
    private var savedRow = 0
    private var savedColumn = 0
    private var aboutToAutoWrap = false
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var leftMargin = 0
    private var rightMargin = this.columns - 1
    private var tabStops = BooleanArray(this.columns) { index -> index > 0 && index % 8 == 0 }

    val displayCursorRow: Int
        get() = historyLines.size + row

    val screenCursorRow: Int
        get() = row

    val cursorColumn: Int
        get() = column

    val columnCount: Int
        get() = columns

    init {
        clear()
    }

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        this.rows = rows.coerceAtLeast(1)
        val oldTabStops = tabStops
        this.columns = columns.coerceAtLeast(1)
        tabStops = BooleanArray(this.columns) { index ->
            oldTabStops.getOrNull(index) ?: (index > 0 && index % 8 == 0)
        }
        leftMargin = 0
        rightMargin = this.columns - 1
        scrollTop = scrollTop.coerceIn(0, this.rows - 1)
        scrollBottom = scrollBottom.coerceIn(scrollTop, this.rows - 1)
        row = row.coerceAtLeast(0)
        column = column.coerceIn(0, this.columns - 1)
        ensureLine(row)
    }

    fun clear() {
        historyLines.clear()
        lines.clear()
        repeat(rows) { lines += TerminalLine() }
        row = 0
        column = 0
        savedRow = 0
        savedColumn = 0
        aboutToAutoWrap = false
        scrollTop = 0
        scrollBottom = rows - 1
        leftMargin = 0
        rightMargin = columns - 1
        tabStops = BooleanArray(columns) { index -> index > 0 && index % 8 == 0 }
    }

    fun fillScreen(
        char: Char,
        style: TerminalTextStyle,
    ) {
        historyLines.clear()
        lines.clear()
        repeat(rows) {
            lines += TerminalLine().apply {
                repeat(columns) { append(char.toString(), style) }
            }
        }
        row = 0
        column = 0
        aboutToAutoWrap = false
    }

    fun text(): String =
        (historyLines + lines).joinToString("\n") { line -> line.trimmedText() }.trimEnd('\n')

    fun styledText(): TerminalStyledText {
        val builder = StringBuilder()
        val ranges = mutableListOf<TerminalStyledRange>()
        val allLines = historyLines + lines
        allLines.forEachIndexed { lineIndex, line ->
            val lineStart = builder.length
            val lineText = line.trimmedText()
            builder.append(lineText)
            line.styledRanges(offset = lineStart, length = lineText.length, ranges = ranges)
            if (lineIndex != allLines.lastIndex) builder.append('\n')
        }
        val text = builder.toString().trimEnd('\n')
        return TerminalStyledText(
            text = text,
            ranges = ranges.filter { it.start < it.end && it.start < text.length }
                .map { range -> range.copy(end = range.end.coerceAtMost(text.length)) },
        )
    }

    fun saveCursor() {
        savedRow = row
        savedColumn = column
    }

    fun restoreCursor() {
        row = savedRow.coerceAtLeast(0)
        column = savedColumn.coerceIn(0, columns - 1)
        aboutToAutoWrap = false
        ensureLine(row)
    }

    fun carriageReturn() {
        column = leftMargin
        aboutToAutoWrap = false
    }

    fun backspace() {
        if (column == leftMargin) {
            val previousRow = row - 1
            if (previousRow >= 0 && lines.getOrNull(previousRow)?.lineWrap == true) {
                lines[previousRow].lineWrap = false
                row = previousRow
                column = rightMargin
            }
        } else {
            column--
        }
        aboutToAutoWrap = false
    }

    fun tab() {
        column = nextTabStop(1)
        aboutToAutoWrap = false
    }

    fun forwardTab(count: Int) {
        column = nextTabStop(count)
        aboutToAutoWrap = false
    }

    fun backwardTab(count: Int) {
        column = previousTabStop(count)
        aboutToAutoWrap = false
    }

    fun setTabStop() {
        if (column in tabStops.indices) tabStops[column] = true
    }

    fun clearTabStop(mode: Int) {
        when (mode) {
            0 -> if (column in tabStops.indices) tabStops[column] = false
            3 -> tabStops.fill(false)
        }
    }

    fun newLine() {
        aboutToAutoWrap = false
        if (row == scrollBottom) {
            scrollUp(1)
        } else {
            row++ 
            ensureLine(row)
        }
        column = leftMargin
    }

    fun nextLine(originMode: Boolean) {
        column = if (originMode) leftMargin else 0
        index()
    }

    fun index() {
        aboutToAutoWrap = false
        if (row == scrollBottom) {
            scrollUp(1)
        } else {
            row++
            ensureLine(row)
        }
    }

    fun reverseIndex() {
        aboutToAutoWrap = false
        if (row == scrollTop) {
            scrollDown(1)
        } else {
            row = (row - 1).coerceAtLeast(0)
        }
    }

    fun cursorUp(count: Int) {
        row = (row - count).coerceAtLeast(0)
        aboutToAutoWrap = false
    }

    fun cursorDown(count: Int) {
        row += count
        aboutToAutoWrap = false
        ensureLine(row)
    }

    fun cursorForward(count: Int) {
        column = (column + count).coerceAtMost(rightMargin)
        aboutToAutoWrap = false
    }

    fun cursorBack(count: Int) {
        column = (column - count).coerceAtLeast(leftMargin)
        aboutToAutoWrap = false
    }

    fun cursorNextLine(
        count: Int,
        originMode: Boolean,
    ) {
        row += count
        column = leftMargin
        aboutToAutoWrap = false
        if (originMode) row = row.coerceIn(scrollTop, scrollBottom)
        ensureLine(row)
    }

    fun cursorPreviousLine(
        count: Int,
        originMode: Boolean,
    ) {
        row = (row - count).coerceAtLeast(0)
        column = leftMargin
        aboutToAutoWrap = false
        if (originMode) row = row.coerceAtLeast(scrollTop)
    }

    fun setCursor(
        row: Int,
        column: Int,
        originMode: Boolean = false,
    ) {
        this.row = if (originMode) {
            (scrollTop + row).coerceIn(scrollTop, scrollBottom)
        } else {
            row.coerceAtLeast(0)
        }
        this.column = if (originMode) {
            (leftMargin + column).coerceIn(leftMargin, rightMargin)
        } else {
            column.coerceIn(0, columns - 1)
        }
        aboutToAutoWrap = false
        ensureLine(this.row)
    }

    fun setColumn(column: Int) {
        this.column = column.coerceIn(0, columns - 1)
        aboutToAutoWrap = false
    }

    fun setRow(
        row: Int,
        originMode: Boolean,
    ) {
        this.row = if (originMode) {
            (scrollTop + row).coerceIn(scrollTop, scrollBottom)
        } else {
            row.coerceAtLeast(0)
        }
        aboutToAutoWrap = false
        ensureLine(this.row)
    }

    fun setScrollRegion(
        top: Int,
        bottom: Int,
    ) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
        setCursor(0, 0)
    }

    fun setHorizontalMargins(
        left: Int,
        right: Int,
    ) {
        leftMargin = left.coerceIn(0, columns - 2)
        rightMargin = right.coerceIn(leftMargin + 1, columns - 1)
        setCursor(0, 0)
    }

    fun resetHorizontalMargins() {
        leftMargin = 0
        rightMargin = columns - 1
    }

    fun resetMarginsAndClearPage() {
        historyLines.clear()
        lines.clear()
        repeat(rows) { lines += TerminalLine() }
        row = 0
        column = 0
        aboutToAutoWrap = false
        scrollTop = 0
        scrollBottom = rows - 1
        leftMargin = 0
        rightMargin = columns - 1
    }

    fun eraseDisplay(mode: Int) {
        aboutToAutoWrap = false
        when (mode) {
            2 -> clear()
            3 -> {
                historyLines.clear()
                trimLines()
            }
            1 -> {
                for (index in 0 until row.coerceAtMost(lines.size)) lines[index].clear()
                eraseLine(1)
            }
            else -> {
                eraseLine(0)
                val from = row + 1
                if (from < lines.size) {
                    lines.subList(from, lines.size).clear()
                }
            }
        }
    }

    fun eraseLine(mode: Int) {
        aboutToAutoWrap = false
        ensureLine(row)
        val line = lines[row]
        when (mode) {
            1 -> {
                val end = column.coerceAtMost(line.length - 1)
                for (index in 0..end) line.set(index, " ", TerminalTextStyle())
            }
            2 -> line.clear()
            else -> if (column < line.length) line.delete(column, line.length)
        }
    }

    fun eraseCharacters(count: Int) {
        aboutToAutoWrap = false
        ensureLine(row)
        val line = lines[row]
        val end = (column + count).coerceAtMost(rightMargin + 1).coerceAtMost(line.length)
        for (index in column until end) line.set(index, " ", TerminalTextStyle())
    }

    fun insertBlankCharacters(count: Int) {
        aboutToAutoWrap = false
        ensureLine(row)
        val line = lines[row]
        while (line.length < column) line.append(" ")
        repeat(count) { line.insert(column, " ", TerminalTextStyle()) }
        if (line.length > rightMargin + 1) line.delete(rightMargin + 1, line.length)
    }

    fun deleteCharacters(count: Int) {
        aboutToAutoWrap = false
        ensureLine(row)
        val line = lines[row]
        if (column < line.length) {
            val end = (column + count).coerceAtMost(rightMargin + 1).coerceAtMost(line.length)
            line.delete(column, end)
        }
    }

    fun insertLines(count: Int) {
        aboutToAutoWrap = false
        ensureLine(scrollBottom)
        repeat(count) {
            lines.add(row.coerceIn(scrollTop, scrollBottom), TerminalLine())
            if (scrollBottom + 1 < lines.size) lines.removeAt(scrollBottom + 1)
        }
    }

    fun deleteLines(count: Int) {
        aboutToAutoWrap = false
        ensureLine(scrollBottom)
        repeat(count) {
            if (row in scrollTop..scrollBottom && row < lines.size) lines.removeAt(row)
            lines.add(scrollBottom.coerceAtMost(lines.size), TerminalLine())
        }
    }

    fun scrollUp(count: Int) {
        aboutToAutoWrap = false
        ensureLine(scrollBottom)
        repeat(count) {
            if (scrollTop < lines.size) {
                if (isFullHorizontalRegion()) {
                    val removedLine = lines.removeAt(scrollTop)
                    if (isFullScrollRegion()) {
                        historyLines += removedLine.copy()
                        trimHistory()
                    }
                    lines.add(scrollBottom.coerceAtMost(lines.size), TerminalLine())
                } else {
                    for (lineIndex in scrollTop until scrollBottom) {
                        copyLineSegment(lineIndex + 1, lineIndex)
                    }
                    clearLineSegment(scrollBottom)
                }
            }
        }
        trimLines()
    }

    fun scrollDown(count: Int) {
        aboutToAutoWrap = false
        ensureLine(scrollBottom)
        repeat(count) {
            if (isFullHorizontalRegion()) {
                lines.add(scrollTop.coerceAtMost(lines.size), TerminalLine())
                if (scrollBottom + 1 < lines.size) lines.removeAt(scrollBottom + 1)
            } else {
                for (lineIndex in scrollBottom downTo scrollTop + 1) {
                    copyLineSegment(lineIndex - 1, lineIndex)
                }
                clearLineSegment(scrollTop)
            }
        }
    }

    fun fillRectangle(
        params: List<Int>,
        style: TerminalTextStyle,
        originMode: Boolean,
    ) {
        val fillCode = param(params, 0, -1)
        if (fillCode !in 32..126 && fillCode !in 160..255) return
        val rectangle = rectangleFromParams(
            params = params,
            startIndex = 1,
            originMode = originMode,
        )
        fillRectangle(rectangle, fillCode.toChar().toString(), style)
    }

    fun eraseRectangle(
        params: List<Int>,
        style: TerminalTextStyle,
        originMode: Boolean,
    ) {
        val rectangle = rectangleFromParams(
            params = params,
            startIndex = 0,
            originMode = originMode,
        )
        fillRectangle(rectangle, " ", style)
    }

    fun copyRectangle(
        params: List<Int>,
        originMode: Boolean,
    ) {
        val source = rectangleFromParams(
            params = params,
            startIndex = 0,
            originMode = originMode,
        )
        val origin = coordinateOrigin(originMode)
        val destinationTop = (origin.top + param(params, 5, 1) - 1).coerceIn(0, rows)
        val destinationLeft = (origin.left + param(params, 6, 1) - 1).coerceIn(0, columns)
        val height = (source.bottomExclusive - source.top).coerceAtMost(rows - destinationTop)
        val width = (source.rightExclusive - source.left).coerceAtMost(columns - destinationLeft)
        if (height <= 0 || width <= 0) return
        val snapshot = List(height) { rowOffset ->
            List(width) { columnOffset ->
                cellAt(source.top + rowOffset, source.left + columnOffset)
            }
        }
        for (rowOffset in 0 until height) {
            ensureLine(destinationTop + rowOffset)
            for (columnOffset in 0 until width) {
                lines[destinationTop + rowOffset].setCell(
                    destinationLeft + columnOffset,
                    snapshot[rowOffset][columnOffset],
                )
            }
        }
    }

    fun changeRectangleAttributes(
        params: List<Int>,
        reverse: Boolean,
        originMode: Boolean,
    ) {
        if (params.size <= 4) return
        val rectangle = rectangleFromParams(
            params = params,
            startIndex = 0,
            originMode = originMode,
        )
        for (rowIndex in rectangle.top until rectangle.bottomExclusive) {
            ensureLine(rowIndex)
            for (columnIndex in rectangle.left until rectangle.rightExclusive) {
                val cell = lines[rowIndex].cellAt(columnIndex)
                lines[rowIndex].setCell(
                    columnIndex,
                    cell.copy(style = applyRectangleAttributes(cell.style, params.drop(4), reverse)),
                )
            }
        }
    }

    fun insertColumns(count: Int) {
        aboutToAutoWrap = false
        val boundedCount = count.coerceAtMost(rightMargin - column + 1).coerceAtLeast(0)
        if (boundedCount == 0) return
        val columnsToMove = rightMargin - column + 1 - boundedCount
        for (rowIndex in 0 until rows) {
            ensureLine(rowIndex)
            for (offset in columnsToMove - 1 downTo 0) {
                lines[rowIndex].setCell(column + boundedCount + offset, lines[rowIndex].cellAt(column + offset))
            }
            for (offset in 0 until boundedCount) {
                lines[rowIndex].set(column + offset, " ", TerminalTextStyle())
            }
        }
    }

    fun deleteColumns(count: Int) {
        aboutToAutoWrap = false
        val boundedCount = count.coerceAtMost(rightMargin - column + 1).coerceAtLeast(0)
        if (boundedCount == 0) return
        val columnsToMove = rightMargin - column + 1 - boundedCount
        for (rowIndex in 0 until rows) {
            ensureLine(rowIndex)
            for (offset in 0 until columnsToMove) {
                lines[rowIndex].setCell(column + offset, lines[rowIndex].cellAt(column + boundedCount + offset))
            }
        }
    }

    fun putText(
        text: String,
        width: Int,
        style: TerminalTextStyle,
        insertMode: Boolean,
        autoWrap: Boolean,
    ) {
        if (width <= 0) {
            ensureLine(row)
            lines[row].appendToLastCell(text)
            return
        }
        if (autoWrap && aboutToAutoWrap) {
            lines.getOrNull(row)?.lineWrap = true
            column = leftMargin
            index()
        }
        aboutToAutoWrap = false
        if (autoWrap && width == 2 && column == rightMargin) {
            lines.getOrNull(row)?.lineWrap = true
            column = leftMargin
            index()
        }
        ensureLine(row)
        val line = lines[row]
        while (line.length < column) line.append(" ")
        if (!insertMode) line.prepareOverwrite(column, width)
        if (insertMode && column < line.length) {
            line.insert(column, text, style)
            if (width == 2 && column + 1 <= rightMargin) line.insert(column + 1, "", style)
            if (line.length > rightMargin + 1) line.delete(rightMargin + 1, line.length)
        } else if (column == line.length) {
            line.append(text, style)
            if (width == 2 && column + 1 <= rightMargin) line.append("", style)
        } else {
            line.set(column, text, style)
            if (width == 2 && column + 1 <= rightMargin) line.set(column + 1, "", style)
        }
        if (column >= rightMargin) {
            if (autoWrap) {
                aboutToAutoWrap = true
            } else {
                column = rightMargin
            }
        } else {
            column = (column + width).coerceAtMost(rightMargin)
        }
    }

    private fun ensureLine(index: Int) {
        while (lines.size <= index) lines += TerminalLine()
    }

    private fun nextTabStop(count: Int): Int {
        var remaining = count.coerceAtLeast(1)
        for (index in (column + 1) until columns) {
            if (tabStops.getOrNull(index) == true && --remaining == 0) return index
        }
        return columns - 1
    }

    private fun previousTabStop(count: Int): Int {
        var remaining = count.coerceAtLeast(1)
        for (index in (column - 1) downTo 0) {
            if (tabStops.getOrNull(index) == true && --remaining == 0) return index
        }
        return 0
    }

    private fun trimLines() {
        while (lines.size > maxLines) {
            lines.removeAt(0)
            row = (row - 1).coerceAtLeast(0)
            savedRow = (savedRow - 1).coerceAtLeast(0)
            scrollTop = (scrollTop - 1).coerceAtLeast(0)
            scrollBottom = (scrollBottom - 1).coerceAtLeast(scrollTop)
        }
    }

    private fun trimHistory() {
        val maxHistoryLines = (maxLines - rows).coerceAtLeast(0)
        while (historyLines.size > maxHistoryLines) {
            historyLines.removeAt(0)
        }
    }

    private fun isFullScrollRegion(): Boolean =
        scrollTop == 0 && scrollBottom == rows - 1

    private fun isFullHorizontalRegion(): Boolean =
        leftMargin == 0 && rightMargin == columns - 1

    private fun copyLineSegment(
        sourceRow: Int,
        targetRow: Int,
    ) {
        ensureLine(sourceRow)
        ensureLine(targetRow)
        lines[targetRow].lineWrap = lines[sourceRow].lineWrap
        for (cellIndex in leftMargin..rightMargin) {
            lines[targetRow].setCell(cellIndex, lines[sourceRow].cellAt(cellIndex))
        }
    }

    private fun clearLineSegment(row: Int) {
        ensureLine(row)
        lines[row].lineWrap = false
        for (cellIndex in leftMargin..rightMargin) {
            lines[row].set(cellIndex, " ", TerminalTextStyle())
        }
    }

    private fun fillRectangle(
        rectangle: TerminalRectangle,
        text: String,
        style: TerminalTextStyle,
    ) {
        if (rectangle.top >= rectangle.bottomExclusive || rectangle.left >= rectangle.rightExclusive) return
        for (rowIndex in rectangle.top until rectangle.bottomExclusive) {
            ensureLine(rowIndex)
            for (columnIndex in rectangle.left until rectangle.rightExclusive) {
                lines[rowIndex].set(columnIndex, text, style)
            }
        }
    }

    private fun rectangleFromParams(
        params: List<Int>,
        startIndex: Int,
        originMode: Boolean,
    ): TerminalRectangle {
        val origin = coordinateOrigin(originMode)
        val top = (origin.top + param(params, startIndex, 1) - 1).coerceIn(origin.top, origin.bottomExclusive)
        val left = (origin.left + param(params, startIndex + 1, 1) - 1).coerceIn(origin.left, origin.rightExclusive)
        val bottom = (origin.top + param(params, startIndex + 2, rows)).coerceIn(top, origin.bottomExclusive)
        val right = (origin.left + param(params, startIndex + 3, columns)).coerceIn(left, origin.rightExclusive)
        return TerminalRectangle(top, left, bottom, right)
    }

    private fun coordinateOrigin(originMode: Boolean): TerminalRectangle =
        if (originMode) {
            TerminalRectangle(scrollTop, leftMargin, scrollBottom + 1, rightMargin + 1)
        } else {
            TerminalRectangle(0, 0, rows, columns)
        }

    private fun cellAt(
        row: Int,
        column: Int,
    ): TerminalCell {
        ensureLine(row)
        return lines[row].cellAt(column)
    }

    private fun applyRectangleAttributes(
        style: TerminalTextStyle,
        attributes: List<Int>,
        reverse: Boolean,
    ): TerminalTextStyle {
        var updated = style
        attributes.forEach { attribute ->
            updated = when (attribute) {
                0 -> if (reverse) {
                    updated.copy(bold = !updated.bold, underline = !updated.underline, inverse = !updated.inverse)
                } else {
                    updated.copy(bold = false, underline = false, inverse = false)
                }
                1 -> if (reverse) updated.copy(bold = !updated.bold) else updated.copy(bold = true)
                4 -> if (reverse) updated.copy(underline = !updated.underline) else updated.copy(underline = true)
                7 -> if (reverse) updated.copy(inverse = !updated.inverse) else updated.copy(inverse = true)
                22 -> if (reverse) updated else updated.copy(bold = false)
                24 -> if (reverse) updated else updated.copy(underline = false)
                27 -> if (reverse) updated else updated.copy(inverse = false)
                else -> updated
            }
        }
        return updated
    }

    private fun param(
        params: List<Int>,
        index: Int,
        default: Int,
    ): Int = params.getOrNull(index)?.takeIf { it > 0 } ?: default
}

data class TerminalStyledText(
    val text: String = "",
    val ranges: List<TerminalStyledRange> = emptyList(),
) {
    fun withReverseVideo(enabled: Boolean): TerminalStyledText {
        if (!enabled || text.isEmpty()) return this
        val updatedRanges = mutableListOf<TerminalStyledRange>()
        var offset = 0
        ranges.sortedBy { it.start }.forEach { range ->
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(start, text.length)
            if (offset < start) {
                updatedRanges += TerminalStyledRange(offset, start, TerminalTextStyle(inverse = true))
            }
            if (start < end) {
                updatedRanges += range.copy(style = range.style.copy(inverse = !range.style.inverse))
            }
            offset = offset.coerceAtLeast(end)
        }
        if (offset < text.length) {
            updatedRanges += TerminalStyledRange(offset, text.length, TerminalTextStyle(inverse = true))
        }
        return copy(ranges = updatedRanges)
    }

    fun takeLast(maxCharacters: Int): TerminalStyledText {
        if (text.length <= maxCharacters) return this
        val startOffset = text.length - maxCharacters
        val clippedText = text.substring(startOffset)
        return copy(
            text = clippedText,
            ranges = ranges.mapNotNull { range ->
                val start = (range.start - startOffset).coerceAtLeast(0)
                val end = (range.end - startOffset).coerceAtMost(clippedText.length)
                if (start < end) range.copy(start = start, end = end) else null
            },
        )
    }
}

data class TerminalStyledRange(
    val start: Int,
    val end: Int,
    val style: TerminalTextStyle,
)

data class TerminalTextStyle(
    val foregroundColor: Int? = null,
    val backgroundColor: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val invisible: Boolean = false,
    val strikethrough: Boolean = false,
)

private data class ParsedTerminalColor(
    val color: Int,
    val consumed: Int,
)

private data class CsiParameter(
    val value: Int?,
    val isSubParameter: Boolean,
)

private data class TerminalStringControl(
    val content: String,
    val consumed: Int,
)

private data class TerminalPrintable(
    val text: String,
    val width: Int,
    val consumed: Int,
)

private data class SavedTerminalState(
    val currentStyle: TerminalTextStyle = TerminalTextStyle(),
    val autoWrapEnabled: Boolean = true,
    val originModeEnabled: Boolean = false,
    val g0LineDrawingEnabled: Boolean = false,
    val g1LineDrawingEnabled: Boolean = false,
    val useG1CharacterSet: Boolean = false,
)

private data class TerminalRectangle(
    val top: Int,
    val left: Int,
    val bottomExclusive: Int,
    val rightExclusive: Int,
)

private class TerminalLine(
    private val cells: MutableList<TerminalCell> = mutableListOf(),
    var lineWrap: Boolean = false,
) {
    val length: Int
        get() = cells.size

    fun append(
        text: String,
        style: TerminalTextStyle = TerminalTextStyle(),
    ) {
        cells += TerminalCell(text = text, style = style)
    }

    fun insert(
        index: Int,
        text: String,
        style: TerminalTextStyle,
    ) {
        cells.add(index.coerceIn(0, cells.size), TerminalCell(text = text, style = style))
    }

    fun set(
        index: Int,
        text: String,
        style: TerminalTextStyle,
    ) {
        while (cells.size <= index) append(" ")
        cells[index] = TerminalCell(text = text, style = style)
    }

    fun cellAt(index: Int): TerminalCell =
        cells.getOrNull(index) ?: TerminalCell(text = " ", style = TerminalTextStyle())

    fun setCell(
        index: Int,
        cell: TerminalCell,
    ) {
        while (cells.size <= index) append(" ")
        cells[index] = cell
    }

    fun prepareOverwrite(
        index: Int,
        newWidth: Int,
    ) {
        if (index > 0 && index < cells.size && cells[index].text.isEmpty()) {
            cells[index - 1] = TerminalCell(text = " ", style = TerminalTextStyle())
        }
        if (index in cells.indices &&
            newWidth < 2 &&
            terminalCellWidth(cells[index].text) == 2 &&
            index + 1 < cells.size &&
            cells[index + 1].text.isEmpty()
        ) {
            cells[index + 1] = TerminalCell(text = " ", style = TerminalTextStyle())
        }
    }

    fun appendToLastCell(text: String) {
        if (text.isEmpty()) return
        if (cells.isEmpty()) {
            append(text)
        } else {
            val index = cells.lastIndex
            cells[index] = cells[index].copy(text = cells[index].text + text)
        }
    }

    fun delete(
        start: Int,
        end: Int,
    ) {
        cells.subList(start.coerceIn(0, cells.size), end.coerceIn(0, cells.size)).clear()
    }

    fun clear() {
        cells.clear()
        lineWrap = false
    }

    fun setLength(length: Int) {
        if (length < cells.size) {
            cells.subList(length.coerceAtLeast(0), cells.size).clear()
        } else {
            while (cells.size < length) append(" ")
        }
    }

    fun trimmedText(): String =
        cells.joinToString(separator = "") { it.text }.trimEnd()

    fun styledRanges(
        offset: Int,
        length: Int,
        ranges: MutableList<TerminalStyledRange>,
    ) {
        var rangeStart = -1
        var rangeStyle = TerminalTextStyle()
        var textOffset = 0
        cells.forEach { cell ->
            if (textOffset >= length) return@forEach
            val cellText = cell.text
            if (cellText.isEmpty()) return@forEach
            val cellStart = textOffset
            val cellEnd = (textOffset + cellText.length).coerceAtMost(length)
            textOffset += cellText.length
            val style = cell.style
            if (style == TerminalTextStyle()) {
                if (rangeStart >= 0) {
                    ranges += TerminalStyledRange(offset + rangeStart, offset + cellStart, rangeStyle)
                    rangeStart = -1
                }
            } else if (rangeStart < 0) {
                rangeStart = cellStart
                rangeStyle = style
            } else if (style != rangeStyle) {
                ranges += TerminalStyledRange(offset + rangeStart, offset + cellStart, rangeStyle)
                rangeStart = cellStart
                rangeStyle = style
            }
            if (cellEnd >= length) return@forEach
        }
        if (rangeStart >= 0) {
            ranges += TerminalStyledRange(offset + rangeStart, offset + length, rangeStyle)
        }
    }

    fun copy(): TerminalLine =
        TerminalLine(cells.toMutableList(), lineWrap)
}

private data class TerminalCell(
    val text: String,
    val style: TerminalTextStyle,
)

private const val TERMINAL_COLOR_FOREGROUND = 256
private const val TERMINAL_COLOR_BACKGROUND = 257
private const val TERMINAL_COLOR_CURSOR = 258
private const val TERMINAL_COLOR_COUNT = 259

private fun terminalDefaultColors(): IntArray =
    IntArray(TERMINAL_COLOR_COUNT) { index ->
        when (index) {
            in 0..255 -> terminalXterm256Color(index)
            TERMINAL_COLOR_FOREGROUND -> 0xFFE5E7EB.toInt()
            TERMINAL_COLOR_BACKGROUND -> 0xFF111827.toInt()
            TERMINAL_COLOR_CURSOR -> 0xFFE5E7EB.toInt()
            else -> 0
        }
    }

private fun terminalAnsiColor(
    index: Int,
    bright: Boolean,
): Int {
    val normal = intArrayOf(
        0xFF1F2937.toInt(),
        0xFFDC2626.toInt(),
        0xFF16A34A.toInt(),
        0xFFD97706.toInt(),
        0xFF2563EB.toInt(),
        0xFFC026D3.toInt(),
        0xFF0891B2.toInt(),
        0xFFE5E7EB.toInt(),
    )
    val brightColors = intArrayOf(
        0xFF6B7280.toInt(),
        0xFFF87171.toInt(),
        0xFF4ADE80.toInt(),
        0xFFFACC15.toInt(),
        0xFF60A5FA.toInt(),
        0xFFE879F9.toInt(),
        0xFF22D3EE.toInt(),
        0xFFFFFFFF.toInt(),
    )
    return (if (bright) brightColors else normal)[index.coerceIn(0, 7)]
}

private fun terminalRgbColor(
    red: Int,
    green: Int,
    blue: Int,
): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun terminalXterm256Color(index: Int): Int {
    val normalized = index.coerceIn(0, 255)
    if (normalized < 16) {
        return terminalAnsiColor(
            index = normalized % 8,
            bright = normalized >= 8,
        )
    }
    if (normalized in 232..255) {
        val value = 8 + (normalized - 232) * 10
        return terminalRgbColor(value, value, value)
    }
    val cube = normalized - 16
    fun component(value: Int): Int =
        if (value == 0) 0 else 55 + value * 40
    return terminalRgbColor(
        red = component(cube / 36),
        green = component((cube / 6) % 6),
        blue = component(cube % 6),
    )
}

private fun parseTerminalColorSpec(spec: String): Int? {
    if (spec.isEmpty()) return null
    return when {
        spec.startsWith("#") -> parseCompactTerminalColor(spec.drop(1), hasSeparators = false)
        spec.startsWith("rgb:") -> parseCompactTerminalColor(spec.drop(4), hasSeparators = true)
        else -> null
    }
}

private fun parseCompactTerminalColor(
    spec: String,
    hasSeparators: Boolean,
): Int? {
    val components = if (hasSeparators) {
        spec.split('/')
    } else {
        if (spec.length % 3 != 0) return null
        val componentLength = spec.length / 3
        listOf(
            spec.substring(0, componentLength),
            spec.substring(componentLength, componentLength * 2),
            spec.substring(componentLength * 2),
        )
    }
    if (components.size != 3 || components.any { it.isEmpty() || it.length > 4 }) return null
    val values = components.map { component ->
        val parsed = component.toIntOrNull(16) ?: return null
        val max = (1 shl (component.length * 4)) - 1
        ((parsed * 255.0) / max).toInt().coerceIn(0, 255)
    }
    return terminalRgbColor(values[0], values[1], values[2])
}

private fun terminalColorToOscRgb(color: Int): String {
    fun component(value: Int): String =
        "%04x".format((value.coerceIn(0, 255) * 65535) / 255)
    return "rgb:${component((color ushr 16) and 0xFF)}/${component((color ushr 8) and 0xFF)}/${component(color and 0xFF)}"
}

private fun decodeOsc52Clipboard(parameter: String): String? {
    val encoded = parameter.substringAfter(';', missingDelimiterValue = parameter)
    if (encoded.isBlank() || encoded == "?") return null
    return runCatching {
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrNull()
}

private fun decodeHexAscii(hex: String): String? {
    if (hex.length % 2 != 0) return null
    return buildString {
        var index = 0
        while (index < hex.length) {
            val value = hex.substring(index, index + 2).toIntOrNull(16) ?: return null
            append(value.toChar())
            index += 2
        }
    }
}

private fun encodeHexAscii(text: String): String =
    text.encodeToByteArray().joinToString(separator = "") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

private fun terminalTermcapValue(name: String): String? = when (name) {
    "Co", "colors" -> "256"
    "TN", "name" -> "xterm-256color"
    "ku", "kcuu1" -> "\u001B[A"
    "kd", "kcud1" -> "\u001B[B"
    "kr", "kcuf1" -> "\u001B[C"
    "kl", "kcub1" -> "\u001B[D"
    "kh", "khome" -> "\u001B[H"
    "@7", "kend" -> "\u001B[F"
    "k1", "kf1" -> "\u001BOP"
    "k2", "kf2" -> "\u001BOQ"
    "k3", "kf3" -> "\u001BOR"
    "k4", "kf4" -> "\u001BOS"
    "k5", "kf5" -> "\u001B[15~"
    "k6", "kf6" -> "\u001B[17~"
    "k7", "kf7" -> "\u001B[18~"
    "k8", "kf8" -> "\u001B[19~"
    "k9", "kf9" -> "\u001B[20~"
    "k;", "kf10" -> "\u001B[21~"
    "F1", "kf11" -> "\u001B[23~"
    "F2", "kf12" -> "\u001B[24~"
    "#2", "kHOM" -> "\u001B[1;2H"
    "*7", "kEND" -> "\u001B[1;2F"
    "#4", "kLFT" -> "\u001B[1;2D"
    "%i", "kRIT" -> "\u001B[1;2C"
    else -> null
}

private fun terminalCellWidth(text: String): Int {
    val codePoint = text.codePointAt(0)
    return when {
        isZeroWidthCodePoint(codePoint) -> 0
        isWideCodePoint(codePoint) -> 2
        else -> 1
    }
}

private fun isZeroWidthCodePoint(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return codePoint == 0 ||
        codePoint == 0x034F ||
        codePoint in 0x200B..0x200F ||
        codePoint == 0x2028 ||
        codePoint == 0x2029 ||
        codePoint in 0x202A..0x202E ||
        codePoint in 0x2060..0x2063 ||
        codePoint < 32 ||
        codePoint in 0x7F until 0xA0 ||
        type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        isCodePointInTable(TERMINAL_ZERO_WIDTH_INTERVALS, codePoint)
}

private fun isWideCodePoint(codePoint: Int): Boolean =
    isCodePointInTable(TERMINAL_WIDE_EAST_ASIAN_INTERVALS, codePoint)

private fun isCodePointInTable(
    table: Array<IntArray>,
    codePoint: Int,
): Boolean {
    if (table.isEmpty() || codePoint < table[0][0]) return false
    var bottom = 0
    var top = table.lastIndex
    while (top >= bottom) {
        val middle = (bottom + top) / 2
        val range = table[middle]
        when {
            range[1] < codePoint -> bottom = middle + 1
            range[0] > codePoint -> top = middle - 1
            else -> return true
        }
    }
    return false
}

private val TERMINAL_ZERO_WIDTH_INTERVALS = arrayOf(
    intArrayOf(0x00300, 0x0036F),
    intArrayOf(0x00483, 0x00489),
    intArrayOf(0x00591, 0x005BD),
    intArrayOf(0x005BF, 0x005BF),
    intArrayOf(0x005C1, 0x005C2),
    intArrayOf(0x005C4, 0x005C5),
    intArrayOf(0x005C7, 0x005C7),
    intArrayOf(0x00610, 0x0061A),
    intArrayOf(0x0064B, 0x0065F),
    intArrayOf(0x00670, 0x00670),
    intArrayOf(0x006D6, 0x006DC),
    intArrayOf(0x006DF, 0x006E4),
    intArrayOf(0x006E7, 0x006E8),
    intArrayOf(0x006EA, 0x006ED),
    intArrayOf(0x00711, 0x00711),
    intArrayOf(0x00730, 0x0074A),
    intArrayOf(0x007A6, 0x007B0),
    intArrayOf(0x007EB, 0x007F3),
    intArrayOf(0x007FD, 0x007FD),
    intArrayOf(0x00816, 0x00819),
    intArrayOf(0x0081B, 0x00823),
    intArrayOf(0x00825, 0x00827),
    intArrayOf(0x00829, 0x0082D),
    intArrayOf(0x00859, 0x0085B),
    intArrayOf(0x00898, 0x0089F),
    intArrayOf(0x008CA, 0x00902),
    intArrayOf(0x0093A, 0x0093A),
    intArrayOf(0x0093C, 0x0093C),
    intArrayOf(0x00941, 0x00948),
    intArrayOf(0x0094D, 0x0094D),
    intArrayOf(0x00951, 0x00957),
    intArrayOf(0x00962, 0x00963),
    intArrayOf(0x00981, 0x00981),
    intArrayOf(0x009BC, 0x009BC),
    intArrayOf(0x009C1, 0x009C4),
    intArrayOf(0x009CD, 0x009CD),
    intArrayOf(0x009E2, 0x009E3),
    intArrayOf(0x009FE, 0x009FE),
    intArrayOf(0x00A01, 0x00A02),
    intArrayOf(0x00A3C, 0x00A3C),
    intArrayOf(0x00A41, 0x00A42),
    intArrayOf(0x00A47, 0x00A48),
    intArrayOf(0x00A4B, 0x00A4D),
    intArrayOf(0x00A51, 0x00A51),
    intArrayOf(0x00A70, 0x00A71),
    intArrayOf(0x00A75, 0x00A75),
    intArrayOf(0x00A81, 0x00A82),
    intArrayOf(0x00ABC, 0x00ABC),
    intArrayOf(0x00AC1, 0x00AC5),
    intArrayOf(0x00AC7, 0x00AC8),
    intArrayOf(0x00ACD, 0x00ACD),
    intArrayOf(0x00AE2, 0x00AE3),
    intArrayOf(0x00AFA, 0x00AFF),
    intArrayOf(0x00B01, 0x00B01),
    intArrayOf(0x00B3C, 0x00B3C),
    intArrayOf(0x00B3F, 0x00B3F),
    intArrayOf(0x00B41, 0x00B44),
    intArrayOf(0x00B4D, 0x00B4D),
    intArrayOf(0x00B55, 0x00B56),
    intArrayOf(0x00B62, 0x00B63),
    intArrayOf(0x00B82, 0x00B82),
    intArrayOf(0x00BC0, 0x00BC0),
    intArrayOf(0x00BCD, 0x00BCD),
    intArrayOf(0x00C00, 0x00C00),
    intArrayOf(0x00C04, 0x00C04),
    intArrayOf(0x00C3C, 0x00C3C),
    intArrayOf(0x00C3E, 0x00C40),
    intArrayOf(0x00C46, 0x00C48),
    intArrayOf(0x00C4A, 0x00C4D),
    intArrayOf(0x00C55, 0x00C56),
    intArrayOf(0x00C62, 0x00C63),
    intArrayOf(0x00C81, 0x00C81),
    intArrayOf(0x00CBC, 0x00CBC),
    intArrayOf(0x00CBF, 0x00CBF),
    intArrayOf(0x00CC6, 0x00CC6),
    intArrayOf(0x00CCC, 0x00CCD),
    intArrayOf(0x00CE2, 0x00CE3),
    intArrayOf(0x00D00, 0x00D01),
    intArrayOf(0x00D3B, 0x00D3C),
    intArrayOf(0x00D41, 0x00D44),
    intArrayOf(0x00D4D, 0x00D4D),
    intArrayOf(0x00D62, 0x00D63),
    intArrayOf(0x00D81, 0x00D81),
    intArrayOf(0x00DCA, 0x00DCA),
    intArrayOf(0x00DD2, 0x00DD4),
    intArrayOf(0x00DD6, 0x00DD6),
    intArrayOf(0x00E31, 0x00E31),
    intArrayOf(0x00E34, 0x00E3A),
    intArrayOf(0x00E47, 0x00E4E),
    intArrayOf(0x00EB1, 0x00EB1),
    intArrayOf(0x00EB4, 0x00EBC),
    intArrayOf(0x00EC8, 0x00ECE),
    intArrayOf(0x00F18, 0x00F19),
    intArrayOf(0x00F35, 0x00F35),
    intArrayOf(0x00F37, 0x00F37),
    intArrayOf(0x00F39, 0x00F39),
    intArrayOf(0x00F71, 0x00F7E),
    intArrayOf(0x00F80, 0x00F84),
    intArrayOf(0x00F86, 0x00F87),
    intArrayOf(0x00F8D, 0x00F97),
    intArrayOf(0x00F99, 0x00FBC),
    intArrayOf(0x00FC6, 0x00FC6),
    intArrayOf(0x0102D, 0x01030),
    intArrayOf(0x01032, 0x01037),
    intArrayOf(0x01039, 0x0103A),
    intArrayOf(0x0103D, 0x0103E),
    intArrayOf(0x01058, 0x01059),
    intArrayOf(0x0105E, 0x01060),
    intArrayOf(0x01071, 0x01074),
    intArrayOf(0x01082, 0x01082),
    intArrayOf(0x01085, 0x01086),
    intArrayOf(0x0108D, 0x0108D),
    intArrayOf(0x0109D, 0x0109D),
    intArrayOf(0x0135D, 0x0135F),
    intArrayOf(0x01712, 0x01714),
    intArrayOf(0x01732, 0x01733),
    intArrayOf(0x01752, 0x01753),
    intArrayOf(0x01772, 0x01773),
    intArrayOf(0x017B4, 0x017B5),
    intArrayOf(0x017B7, 0x017BD),
    intArrayOf(0x017C6, 0x017C6),
    intArrayOf(0x017C9, 0x017D3),
    intArrayOf(0x017DD, 0x017DD),
    intArrayOf(0x0180B, 0x0180D),
    intArrayOf(0x0180F, 0x0180F),
    intArrayOf(0x01885, 0x01886),
    intArrayOf(0x018A9, 0x018A9),
    intArrayOf(0x01920, 0x01922),
    intArrayOf(0x01927, 0x01928),
    intArrayOf(0x01932, 0x01932),
    intArrayOf(0x01939, 0x0193B),
    intArrayOf(0x01A17, 0x01A18),
    intArrayOf(0x01A1B, 0x01A1B),
    intArrayOf(0x01A56, 0x01A56),
    intArrayOf(0x01A58, 0x01A5E),
    intArrayOf(0x01A60, 0x01A60),
    intArrayOf(0x01A62, 0x01A62),
    intArrayOf(0x01A65, 0x01A6C),
    intArrayOf(0x01A73, 0x01A7C),
    intArrayOf(0x01A7F, 0x01A7F),
    intArrayOf(0x01AB0, 0x01ACE),
    intArrayOf(0x01B00, 0x01B03),
    intArrayOf(0x01B34, 0x01B34),
    intArrayOf(0x01B36, 0x01B3A),
    intArrayOf(0x01B3C, 0x01B3C),
    intArrayOf(0x01B42, 0x01B42),
    intArrayOf(0x01B6B, 0x01B73),
    intArrayOf(0x01B80, 0x01B81),
    intArrayOf(0x01BA2, 0x01BA5),
    intArrayOf(0x01BA8, 0x01BA9),
    intArrayOf(0x01BAB, 0x01BAD),
    intArrayOf(0x01BE6, 0x01BE6),
    intArrayOf(0x01BE8, 0x01BE9),
    intArrayOf(0x01BED, 0x01BED),
    intArrayOf(0x01BEF, 0x01BF1),
    intArrayOf(0x01C2C, 0x01C33),
    intArrayOf(0x01C36, 0x01C37),
    intArrayOf(0x01CD0, 0x01CD2),
    intArrayOf(0x01CD4, 0x01CE0),
    intArrayOf(0x01CE2, 0x01CE8),
    intArrayOf(0x01CED, 0x01CED),
    intArrayOf(0x01CF4, 0x01CF4),
    intArrayOf(0x01CF8, 0x01CF9),
    intArrayOf(0x01DC0, 0x01DFF),
    intArrayOf(0x020D0, 0x020F0),
    intArrayOf(0x02CEF, 0x02CF1),
    intArrayOf(0x02D7F, 0x02D7F),
    intArrayOf(0x02DE0, 0x02DFF),
    intArrayOf(0x0302A, 0x0302D),
    intArrayOf(0x03099, 0x0309A),
    intArrayOf(0x0A66F, 0x0A672),
    intArrayOf(0x0A674, 0x0A67D),
    intArrayOf(0x0A69E, 0x0A69F),
    intArrayOf(0x0A6F0, 0x0A6F1),
    intArrayOf(0x0A802, 0x0A802),
    intArrayOf(0x0A806, 0x0A806),
    intArrayOf(0x0A80B, 0x0A80B),
    intArrayOf(0x0A825, 0x0A826),
    intArrayOf(0x0A82C, 0x0A82C),
    intArrayOf(0x0A8C4, 0x0A8C5),
    intArrayOf(0x0A8E0, 0x0A8F1),
    intArrayOf(0x0A8FF, 0x0A8FF),
    intArrayOf(0x0A926, 0x0A92D),
    intArrayOf(0x0A947, 0x0A951),
    intArrayOf(0x0A980, 0x0A982),
    intArrayOf(0x0A9B3, 0x0A9B3),
    intArrayOf(0x0A9B6, 0x0A9B9),
    intArrayOf(0x0A9BC, 0x0A9BD),
    intArrayOf(0x0A9E5, 0x0A9E5),
    intArrayOf(0x0AA29, 0x0AA2E),
    intArrayOf(0x0AA31, 0x0AA32),
    intArrayOf(0x0AA35, 0x0AA36),
    intArrayOf(0x0AA43, 0x0AA43),
    intArrayOf(0x0AA4C, 0x0AA4C),
    intArrayOf(0x0AA7C, 0x0AA7C),
    intArrayOf(0x0AAB0, 0x0AAB0),
    intArrayOf(0x0AAB2, 0x0AAB4),
    intArrayOf(0x0AAB7, 0x0AAB8),
    intArrayOf(0x0AABE, 0x0AABF),
    intArrayOf(0x0AAC1, 0x0AAC1),
    intArrayOf(0x0AAEC, 0x0AAED),
    intArrayOf(0x0AAF6, 0x0AAF6),
    intArrayOf(0x0ABE5, 0x0ABE5),
    intArrayOf(0x0ABE8, 0x0ABE8),
    intArrayOf(0x0ABED, 0x0ABED),
    intArrayOf(0x0FB1E, 0x0FB1E),
    intArrayOf(0x0FE00, 0x0FE0F),
    intArrayOf(0x0FE20, 0x0FE2F),
    intArrayOf(0x101FD, 0x101FD),
    intArrayOf(0x102E0, 0x102E0),
    intArrayOf(0x10376, 0x1037A),
    intArrayOf(0x10A01, 0x10A03),
    intArrayOf(0x10A05, 0x10A06),
    intArrayOf(0x10A0C, 0x10A0F),
    intArrayOf(0x10A38, 0x10A3A),
    intArrayOf(0x10A3F, 0x10A3F),
    intArrayOf(0x10AE5, 0x10AE6),
    intArrayOf(0x10D24, 0x10D27),
    intArrayOf(0x10EAB, 0x10EAC),
    intArrayOf(0x10EFD, 0x10EFF),
    intArrayOf(0x10F46, 0x10F50),
    intArrayOf(0x10F82, 0x10F85),
    intArrayOf(0x11001, 0x11001),
    intArrayOf(0x11038, 0x11046),
    intArrayOf(0x11070, 0x11070),
    intArrayOf(0x11073, 0x11074),
    intArrayOf(0x1107F, 0x11081),
    intArrayOf(0x110B3, 0x110B6),
    intArrayOf(0x110B9, 0x110BA),
    intArrayOf(0x110C2, 0x110C2),
    intArrayOf(0x11100, 0x11102),
    intArrayOf(0x11127, 0x1112B),
    intArrayOf(0x1112D, 0x11134),
    intArrayOf(0x11173, 0x11173),
    intArrayOf(0x11180, 0x11181),
    intArrayOf(0x111B6, 0x111BE),
    intArrayOf(0x111C9, 0x111CC),
    intArrayOf(0x111CF, 0x111CF),
    intArrayOf(0x1122F, 0x11231),
    intArrayOf(0x11234, 0x11234),
    intArrayOf(0x11236, 0x11237),
    intArrayOf(0x1123E, 0x1123E),
    intArrayOf(0x11241, 0x11241),
    intArrayOf(0x112DF, 0x112DF),
    intArrayOf(0x112E3, 0x112EA),
    intArrayOf(0x11300, 0x11301),
    intArrayOf(0x1133B, 0x1133C),
    intArrayOf(0x11340, 0x11340),
    intArrayOf(0x11366, 0x1136C),
    intArrayOf(0x11370, 0x11374),
    intArrayOf(0x11438, 0x1143F),
    intArrayOf(0x11442, 0x11444),
    intArrayOf(0x11446, 0x11446),
    intArrayOf(0x1145E, 0x1145E),
    intArrayOf(0x114B3, 0x114B8),
    intArrayOf(0x114BA, 0x114BA),
    intArrayOf(0x114BF, 0x114C0),
    intArrayOf(0x114C2, 0x114C3),
    intArrayOf(0x115B2, 0x115B5),
    intArrayOf(0x115BC, 0x115BD),
    intArrayOf(0x115BF, 0x115C0),
    intArrayOf(0x115DC, 0x115DD),
    intArrayOf(0x11633, 0x1163A),
    intArrayOf(0x1163D, 0x1163D),
    intArrayOf(0x1163F, 0x11640),
    intArrayOf(0x116AB, 0x116AB),
    intArrayOf(0x116AD, 0x116AD),
    intArrayOf(0x116B0, 0x116B5),
    intArrayOf(0x116B7, 0x116B7),
    intArrayOf(0x1171D, 0x1171F),
    intArrayOf(0x11722, 0x11725),
    intArrayOf(0x11727, 0x1172B),
    intArrayOf(0x1182F, 0x11837),
    intArrayOf(0x11839, 0x1183A),
    intArrayOf(0x1193B, 0x1193C),
    intArrayOf(0x1193E, 0x1193E),
    intArrayOf(0x11943, 0x11943),
    intArrayOf(0x119D4, 0x119D7),
    intArrayOf(0x119DA, 0x119DB),
    intArrayOf(0x119E0, 0x119E0),
    intArrayOf(0x11A01, 0x11A0A),
    intArrayOf(0x11A33, 0x11A38),
    intArrayOf(0x11A3B, 0x11A3E),
    intArrayOf(0x11A47, 0x11A47),
    intArrayOf(0x11A51, 0x11A56),
    intArrayOf(0x11A59, 0x11A5B),
    intArrayOf(0x11A8A, 0x11A96),
    intArrayOf(0x11A98, 0x11A99),
    intArrayOf(0x11C30, 0x11C36),
    intArrayOf(0x11C38, 0x11C3D),
    intArrayOf(0x11C3F, 0x11C3F),
    intArrayOf(0x11C92, 0x11CA7),
    intArrayOf(0x11CAA, 0x11CB0),
    intArrayOf(0x11CB2, 0x11CB3),
    intArrayOf(0x11CB5, 0x11CB6),
    intArrayOf(0x11D31, 0x11D36),
    intArrayOf(0x11D3A, 0x11D3A),
    intArrayOf(0x11D3C, 0x11D3D),
    intArrayOf(0x11D3F, 0x11D45),
    intArrayOf(0x11D47, 0x11D47),
    intArrayOf(0x11D90, 0x11D91),
    intArrayOf(0x11D95, 0x11D95),
    intArrayOf(0x11D97, 0x11D97),
    intArrayOf(0x11EF3, 0x11EF4),
    intArrayOf(0x13430, 0x13438),
    intArrayOf(0x16AF0, 0x16AF4),
    intArrayOf(0x16B30, 0x16B36),
    intArrayOf(0x16F4F, 0x16F4F),
    intArrayOf(0x16F8F, 0x16F92),
    intArrayOf(0x16FE4, 0x16FE4),
    intArrayOf(0x1BC9D, 0x1BC9E),
    intArrayOf(0x1BCA0, 0x1BCA3),
    intArrayOf(0x1D167, 0x1D169),
    intArrayOf(0x1D173, 0x1D182),
    intArrayOf(0x1D185, 0x1D18B),
    intArrayOf(0x1D1AA, 0x1D1AD),
    intArrayOf(0x1D242, 0x1D244),
    intArrayOf(0x1DA00, 0x1DA36),
    intArrayOf(0x1DA3B, 0x1DA6C),
    intArrayOf(0x1DA75, 0x1DA75),
    intArrayOf(0x1DA84, 0x1DA84),
    intArrayOf(0x1DA9B, 0x1DA9F),
    intArrayOf(0x1DAA1, 0x1DAAF),
    intArrayOf(0x1E000, 0x1E006),
    intArrayOf(0x1E008, 0x1E018),
    intArrayOf(0x1E01B, 0x1E021),
    intArrayOf(0x1E023, 0x1E024),
    intArrayOf(0x1E026, 0x1E02A),
    intArrayOf(0x1E130, 0x1E136),
    intArrayOf(0x1E2AE, 0x1E2AE),
    intArrayOf(0x1E2EC, 0x1E2EF),
    intArrayOf(0x1E4EC, 0x1E4EF),
    intArrayOf(0x1E8D0, 0x1E8D6),
    intArrayOf(0x1E944, 0x1E94A),
    intArrayOf(0xE0100, 0xE01EF),
)

private val TERMINAL_WIDE_EAST_ASIAN_INTERVALS = arrayOf(
    intArrayOf(0x01100, 0x0115F),
    intArrayOf(0x0231A, 0x0231B),
    intArrayOf(0x02329, 0x0232A),
    intArrayOf(0x023E9, 0x023EC),
    intArrayOf(0x023F0, 0x023F0),
    intArrayOf(0x023F3, 0x023F3),
    intArrayOf(0x025FD, 0x025FE),
    intArrayOf(0x02614, 0x02615),
    intArrayOf(0x02648, 0x02653),
    intArrayOf(0x0267F, 0x0267F),
    intArrayOf(0x02693, 0x02693),
    intArrayOf(0x026A1, 0x026A1),
    intArrayOf(0x026AA, 0x026AB),
    intArrayOf(0x026BD, 0x026BE),
    intArrayOf(0x026C4, 0x026C5),
    intArrayOf(0x026CE, 0x026CE),
    intArrayOf(0x026D4, 0x026D4),
    intArrayOf(0x026EA, 0x026EA),
    intArrayOf(0x026F2, 0x026F3),
    intArrayOf(0x026F5, 0x026F5),
    intArrayOf(0x026FA, 0x026FA),
    intArrayOf(0x026FD, 0x026FD),
    intArrayOf(0x02705, 0x02705),
    intArrayOf(0x0270A, 0x0270B),
    intArrayOf(0x02728, 0x02728),
    intArrayOf(0x0274C, 0x0274C),
    intArrayOf(0x0274E, 0x0274E),
    intArrayOf(0x02753, 0x02755),
    intArrayOf(0x02757, 0x02757),
    intArrayOf(0x02795, 0x02797),
    intArrayOf(0x027B0, 0x027B0),
    intArrayOf(0x027BF, 0x027BF),
    intArrayOf(0x02B1B, 0x02B1C),
    intArrayOf(0x02B50, 0x02B50),
    intArrayOf(0x02B55, 0x02B55),
    intArrayOf(0x02E80, 0x02E99),
    intArrayOf(0x02E9B, 0x02EF3),
    intArrayOf(0x02F00, 0x02FD5),
    intArrayOf(0x02FF0, 0x02FFB),
    intArrayOf(0x03000, 0x0303E),
    intArrayOf(0x03041, 0x03096),
    intArrayOf(0x03099, 0x030FF),
    intArrayOf(0x03105, 0x0312F),
    intArrayOf(0x03131, 0x0318E),
    intArrayOf(0x03190, 0x031E3),
    intArrayOf(0x031F0, 0x0321E),
    intArrayOf(0x03220, 0x03247),
    intArrayOf(0x03250, 0x04DBF),
    intArrayOf(0x04E00, 0x0A48C),
    intArrayOf(0x0A490, 0x0A4C6),
    intArrayOf(0x0A960, 0x0A97C),
    intArrayOf(0x0AC00, 0x0D7A3),
    intArrayOf(0x0F900, 0x0FAFF),
    intArrayOf(0x0FE10, 0x0FE19),
    intArrayOf(0x0FE30, 0x0FE52),
    intArrayOf(0x0FE54, 0x0FE66),
    intArrayOf(0x0FE68, 0x0FE6B),
    intArrayOf(0x0FF01, 0x0FF60),
    intArrayOf(0x0FFE0, 0x0FFE6),
    intArrayOf(0x16FE0, 0x16FE4),
    intArrayOf(0x16FF0, 0x16FF1),
    intArrayOf(0x17000, 0x187F7),
    intArrayOf(0x18800, 0x18CD5),
    intArrayOf(0x18D00, 0x18D08),
    intArrayOf(0x1AFF0, 0x1AFF3),
    intArrayOf(0x1AFF5, 0x1AFFB),
    intArrayOf(0x1AFFD, 0x1AFFE),
    intArrayOf(0x1B000, 0x1B122),
    intArrayOf(0x1B132, 0x1B132),
    intArrayOf(0x1B150, 0x1B152),
    intArrayOf(0x1B155, 0x1B155),
    intArrayOf(0x1B164, 0x1B167),
    intArrayOf(0x1B170, 0x1B2FB),
    intArrayOf(0x1F004, 0x1F004),
    intArrayOf(0x1F0CF, 0x1F0CF),
    intArrayOf(0x1F18E, 0x1F18E),
    intArrayOf(0x1F191, 0x1F19A),
    intArrayOf(0x1F200, 0x1F202),
    intArrayOf(0x1F210, 0x1F23B),
    intArrayOf(0x1F240, 0x1F248),
    intArrayOf(0x1F250, 0x1F251),
    intArrayOf(0x1F260, 0x1F265),
    intArrayOf(0x1F300, 0x1F320),
    intArrayOf(0x1F32D, 0x1F335),
    intArrayOf(0x1F337, 0x1F37C),
    intArrayOf(0x1F37E, 0x1F393),
    intArrayOf(0x1F3A0, 0x1F3CA),
    intArrayOf(0x1F3CF, 0x1F3D3),
    intArrayOf(0x1F3E0, 0x1F3F0),
    intArrayOf(0x1F3F4, 0x1F3F4),
    intArrayOf(0x1F3F8, 0x1F43E),
    intArrayOf(0x1F440, 0x1F440),
    intArrayOf(0x1F442, 0x1F4FC),
    intArrayOf(0x1F4FF, 0x1F53D),
    intArrayOf(0x1F54B, 0x1F54E),
    intArrayOf(0x1F550, 0x1F567),
    intArrayOf(0x1F57A, 0x1F57A),
    intArrayOf(0x1F595, 0x1F596),
    intArrayOf(0x1F5A4, 0x1F5A4),
    intArrayOf(0x1F5FB, 0x1F64F),
    intArrayOf(0x1F680, 0x1F6C5),
    intArrayOf(0x1F6CC, 0x1F6CC),
    intArrayOf(0x1F6D0, 0x1F6D2),
    intArrayOf(0x1F6D5, 0x1F6D7),
    intArrayOf(0x1F6DC, 0x1F6DF),
    intArrayOf(0x1F6EB, 0x1F6EC),
    intArrayOf(0x1F6F4, 0x1F6FC),
    intArrayOf(0x1F7E0, 0x1F7EB),
    intArrayOf(0x1F7F0, 0x1F7F0),
    intArrayOf(0x1F90C, 0x1F93A),
    intArrayOf(0x1F93C, 0x1F945),
    intArrayOf(0x1F947, 0x1F9FF),
    intArrayOf(0x1FA70, 0x1FA7C),
    intArrayOf(0x1FA80, 0x1FA88),
    intArrayOf(0x1FA90, 0x1FABD),
    intArrayOf(0x1FABF, 0x1FAC5),
    intArrayOf(0x1FACE, 0x1FADB),
    intArrayOf(0x1FAE0, 0x1FAE8),
    intArrayOf(0x1FAF0, 0x1FAF8),
    intArrayOf(0x20000, 0x2FFFD),
    intArrayOf(0x30000, 0x3FFFD),
)
