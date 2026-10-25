@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zhousl.aether.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import com.zhousl.aether.platform.*
import com.zhousl.aether.ui.theme.*
import kotlinx.serialization.json.*

internal val LocalNativeChatControlsObscured = staticCompositionLocalOf { false }

@Composable
internal fun NativeChatControl(
    kind: String,
    snapshot: JsonObject,
    modifier: Modifier,
    onAction: (String, String, (Boolean) -> Unit) -> Unit,
) {
    // Native overlays sit above the Compose canvas; remove them while a modal
    // drawer covers the conversation so they cannot draw or receive taps over it.
    if (LocalNativeChatControlsObscured.current) {
        Spacer(modifier)
        return
    }
    val currentAction by rememberUpdatedState(onAction)
    val factory = checkNotNull(IosNativeChatHost.factory) { "Native chat factory must be registered before creating the chat" }
    val payload = buildJsonObject {
        snapshot.forEach { (key, value) -> put(key, value) }
        put("dark", AetherBackground.luminance() < 0.5f)
    }.toString()
    UIKitViewController(
        factory = { factory.create(kind, payload, object : IosNativeChatActionHandler {
            override fun handle(action: String, value: String, completion: (Boolean) -> Unit) {
                currentAction(action, value, completion)
            }
        }) },
        update = { factory.update(it, payload) },
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            // The transparent anchors leave all visible control artwork in Compose.
            placedAsOverlay = true,
        ),
    )
}
