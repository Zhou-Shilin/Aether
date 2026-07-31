package com.zhousl.aether.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhousl.aether.runtime.MultiplatformLocalRuntime

actual val platformNativeTerminalAvailable: Boolean = false

@Composable
actual fun PlatformTerminalSurface(
    runtime: MultiplatformLocalRuntime,
    interruptSignal: Int,
    inputEvent: PlatformTerminalInputEvent?,
    darkTheme: Boolean,
    onTitleChanged: (String) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    Box(modifier)
}
