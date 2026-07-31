package com.zhousl.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhousl.aether.runtime.MultiplatformLocalRuntime

expect val platformNativeTerminalAvailable: Boolean

data class PlatformTerminalInputEvent(
    val sequence: Int,
    val text: String = "",
    val key: PlatformTerminalKey? = null,
    val controlDown: Boolean = false,
    val altDown: Boolean = false,
    val requestFocus: Boolean = false,
)

enum class PlatformTerminalKey {
    Escape,
    Tab,
    Home,
    End,
    PageUp,
    PageDown,
    Backspace,
    Delete,
    Insert,
    Left,
    Down,
    Up,
    Right,
    Enter,
}

@Composable
expect fun PlatformTerminalSurface(
    runtime: MultiplatformLocalRuntime,
    interruptSignal: Int,
    inputEvent: PlatformTerminalInputEvent? = null,
    darkTheme: Boolean,
    onTitleChanged: (String) -> Unit = {},
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier,
)
