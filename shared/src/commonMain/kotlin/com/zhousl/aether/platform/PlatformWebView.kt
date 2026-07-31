package com.zhousl.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformWebView(
    url: String,
    html: String = "",
    onMessage: (String) -> Unit = {},
    transparentBackground: Boolean = false,
    scrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
)
