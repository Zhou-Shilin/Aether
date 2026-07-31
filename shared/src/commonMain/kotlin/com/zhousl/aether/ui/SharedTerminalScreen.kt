package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeProcess
import com.zhousl.aether.runtime.RuntimeProcessSignal
import com.zhousl.aether.runtime.RuntimeProcessSpec
import com.zhousl.aether.platform.PlatformTerminalSurface
import com.zhousl.aether.platform.PlatformTerminalInputEvent
import com.zhousl.aether.platform.PlatformTerminalKey
import com.zhousl.aether.platform.platformNativeTerminalAvailable
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.back_label
import com.zhousl.aether.shared.resources.common_send
import com.zhousl.aether.shared.resources.settings_open_terminal
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val TerminalBackground = Color(0xFF111214)
private val TerminalSurface = Color(0xFF202226)
private val TerminalText = Color(0xFFE8E8E8)
private val TerminalMuted = Color(0xFF9EA2A8)

@Composable
fun SharedTerminalScreen(
    runtime: MultiplatformLocalRuntime,
    onBack: () -> Unit,
) {
    if (platformNativeTerminalAvailable) {
        var inputSequence by remember { mutableStateOf(0) }
        var inputEvent by remember { mutableStateOf<PlatformTerminalInputEvent?>(null) }
        var controlDown by remember { mutableStateOf(false) }
        var altDown by remember { mutableStateOf(false) }
        var title by remember { mutableStateOf("Alpine") }
        var terminalReady by remember(runtime) { mutableStateOf(false) }
        var terminalError by remember(runtime) { mutableStateOf("") }
        val darkTheme = isSystemInDarkTheme()

        fun sendText(text: String) {
            inputSequence += 1
            inputEvent = PlatformTerminalInputEvent(
                sequence = inputSequence,
                text = transformNativeTerminalTextInput(text, controlDown, altDown),
            )
        }

        fun sendKey(key: PlatformTerminalKey) {
            inputSequence += 1
            inputEvent = PlatformTerminalInputEvent(
                sequence = inputSequence,
                key = key,
                controlDown = controlDown,
                altDown = altDown,
            )
        }

        fun requestTerminalFocus() {
            inputSequence += 1
            inputEvent = PlatformTerminalInputEvent(inputSequence, requestFocus = true)
        }

        Column(
            modifier = Modifier.fillMaxSize().background(AetherBackground)
                .imePadding().navigationBarsPadding(),
        ) {
            NativeTerminalTopBar(title = title, onBack = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlatformTerminalSurface(
                    runtime = runtime,
                    interruptSignal = 0,
                    inputEvent = inputEvent,
                    darkTheme = darkTheme,
                    onTitleChanged = { title = it.ifBlank { "Alpine" } },
                    onReady = {
                        terminalReady = true
                        terminalError = ""
                    },
                    onError = {
                        terminalReady = false
                        terminalError = it
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                when {
                    terminalError.isNotBlank() -> Text(
                        text = terminalError,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                    !terminalReady -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AetherOnSurface,
                    )
                }
            }
            NativeTerminalExtraKeysBar(
                controlDown = controlDown,
                altDown = altDown,
                onControlDownChange = { controlDown = it },
                onAltDownChange = { altDown = it },
                onSendText = ::sendText,
                onSendKey = ::sendKey,
                onRequestFocus = ::requestTerminalFocus,
            )
        }
        return
    }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var process by remember { mutableStateOf<RuntimeProcess?>(null) }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Starting Alpine shell...") }

    fun appendOutput(value: String) {
        if (value.isEmpty()) return
        output = (output + value).takeLast(300_000)
    }

    fun submitInput(value: String = input) {
        val active = process ?: return
        if (value.isEmpty()) return
        appendOutput(value + "\n")
        input = ""
        scope.launch { active.writeStdin((value + "\n").encodeToByteArray()) }
    }

    LaunchedEffect(runtime) {
        runCatching {
            runtime.initialize()
            runtime.startProcess(
                RuntimeProcessSpec(
                    executable = "/bin/sh",
                    arguments = listOf("-l"),
                    environment = mapOf(
                        "HOME" to runtime.homeDirectory,
                        "TERM" to "xterm-256color",
                        "AETHER_WORKSPACE" to runtime.workspaceRoot,
                    ),
                    workingDirectory = runtime.workspaceRoot,
                )
            )
        }.fold(
            onSuccess = { active ->
                process = active
                status = "Alpine"
                launch { active.stdout.collect { appendOutput(it.decodeToString()) } }
                launch { active.stderr.collect { appendOutput(it.decodeToString()) } }
                launch {
                    val exit = active.awaitExit()
                    status = "Exited (${exit.exitCode})"
                    process = null
                }
            },
            onFailure = { status = it.message ?: "Unable to start Alpine shell." },
        )
    }
    LaunchedEffect(output) { scrollState.scrollTo(scrollState.maxValue) }
    DisposableEffect(process) {
        val active = process
        onDispose {
            if (active != null) scope.launch { active.signal(RuntimeProcessSignal.Terminate) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(TerminalBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(Res.string.back_label),
                    tint = TerminalText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    stringResource(Res.string.settings_open_terminal),
                    color = TerminalText,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(status, color = TerminalMuted, style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable {
                    process?.let { active -> scope.launch { active.signal(RuntimeProcessSignal.Interrupt) } }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Ctrl-C", tint = TerminalText, modifier = Modifier.size(19.dp))
            }
        }
        Text(
            text = output,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(14.dp),
            color = TerminalText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .imePadding()
                .navigationBarsPadding()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$ ", color = Color(0xFF67D391), fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TerminalText,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(TerminalText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitInput() }),
                    singleLine = true,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF925BFF))
                    .clickable(enabled = input.isNotBlank()) { submitInput() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    stringResource(Res.string.common_send),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun NativeTerminalTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(AetherBackground.copy(alpha = 0.86f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.padding(end = 12.dp).background(AetherSurface, CircleShape)
                .clickable(onClick = onBack).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(Res.string.back_label),
                tint = AetherOnSurface,
                modifier = Modifier.padding(2.dp),
            )
        }
        Text(title, color = AetherOnSurface)
    }
}

@Composable
private fun NativeTerminalExtraKeysBar(
    controlDown: Boolean,
    altDown: Boolean,
    onControlDownChange: (Boolean) -> Unit,
    onAltDownChange: (Boolean) -> Unit,
    onSendText: (String) -> Unit,
    onSendKey: (PlatformTerminalKey) -> Unit,
    onRequestFocus: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(AetherSurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NativeExtraKeyRow {
            NativeExtraKey("ESC") { onSendKey(PlatformTerminalKey.Escape) }
            NativeExtraKey("TAB") { onSendKey(PlatformTerminalKey.Tab) }
            NativeExtraKey("CTRL", active = controlDown) { onControlDownChange(!controlDown) }
            NativeExtraKey("ALT", active = altDown) { onAltDownChange(!altDown) }
            NativeExtraKey("-") { onSendText("-") }
            NativeExtraKey("/") { onSendText("/") }
            NativeExtraKey("|") { onSendText("|") }
            NativeExtraKey("HOME") { onSendKey(PlatformTerminalKey.Home) }
            NativeExtraKey("END") { onSendKey(PlatformTerminalKey.End) }
            NativeExtraKey("PGUP") { onSendKey(PlatformTerminalKey.PageUp) }
            NativeExtraKey("PGDN") { onSendKey(PlatformTerminalKey.PageDown) }
        }
        NativeExtraKeyRow {
            NativeExtraKey("KEYB", onClick = onRequestFocus)
            NativeExtraKey("BKSP") { onSendKey(PlatformTerminalKey.Backspace) }
            NativeExtraKey("DEL") { onSendKey(PlatformTerminalKey.Delete) }
            NativeExtraKey("INS") { onSendKey(PlatformTerminalKey.Insert) }
            NativeExtraKey("LEFT") { onSendKey(PlatformTerminalKey.Left) }
            NativeExtraKey("DOWN") { onSendKey(PlatformTerminalKey.Down) }
            NativeExtraKey("UP") { onSendKey(PlatformTerminalKey.Up) }
            NativeExtraKey("RIGHT") { onSendKey(PlatformTerminalKey.Right) }
            NativeExtraKey("ENTER") { onSendKey(PlatformTerminalKey.Enter) }
        }
    }
}

internal fun transformNativeTerminalTextInput(
    text: String,
    controlDown: Boolean,
    altDown: Boolean,
): String = buildString {
    text.forEach { character ->
        val transformed = if (controlDown) {
            when (character) {
                in 'a'..'z' -> character.code - 'a'.code + 1
                in 'A'..'Z' -> character.code - 'A'.code + 1
                ' ', '2' -> 0
                '[', '3' -> 27
                '\\', '4' -> 28
                ']', '5' -> 29
                '^', '6' -> 30
                '_', '7', '/' -> 31
                '8' -> 127
                else -> character.code
            }.toChar()
        } else {
            character
        }
        if (altDown) append('\u001b')
        append(transformed)
    }
}

@Composable
private fun NativeExtraKeyRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.size(1.dp))
    }
}

@Composable
private fun NativeExtraKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.heightIn(min = 32.dp).background(
            color = if (active) AetherOnSurfaceVariant.copy(alpha = 0.28f) else AetherSurfaceHigher,
            shape = RoundedCornerShape(6.dp),
        ).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) AetherOnSurface else AetherOnSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
