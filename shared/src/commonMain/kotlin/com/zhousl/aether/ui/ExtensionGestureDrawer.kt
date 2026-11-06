package com.zhousl.aether.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal class ExtensionWebGestures {
    private var owners by mutableStateOf(emptySet<Any>())
    val active: Boolean get() = owners.isNotEmpty()
    fun begin(owner: Any) { owners = owners + owner }
    fun end(owner: Any) { owners = owners - owner }
}

private val LocalExtensionWebGestures = staticCompositionLocalOf<ExtensionWebGestures?> { null }

/** Observe without consuming: WebView input and vertical conversation scrolling keep their policy. */
fun Modifier.extensionWebGestureRegion(): Modifier = composed {
    val gestures = LocalExtensionWebGestures.current
    if (gestures == null) this else pointerInput(gestures) {
        val owner = Any()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            gestures.begin(owner)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                gestures.end(owner)
            }
        }
    }
}

@Composable
fun ExtensionGestureDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    scrimColor: Color = DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
) {
    val gestures = remember { ExtensionWebGestures() }
    CompositionLocalProvider(LocalExtensionWebGestures provides gestures) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            modifier = modifier,
            gesturesEnabled = gesturesEnabled && (drawerState.isOpen || !gestures.active),
            scrimColor = scrimColor,
            content = content,
        )
    }
}
