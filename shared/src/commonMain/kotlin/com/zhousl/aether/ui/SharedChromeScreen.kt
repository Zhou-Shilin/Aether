package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.pi.SharedChromeManager
import com.zhousl.aether.platform.PlatformWebView
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.chrome_label
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedChromeScreen(
    manager: SharedChromeManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(manager, retry) {
        ready = false
        error = ""
        manager.enabled = true
        runCatching { manager.start() }
            .onSuccess { ready = true }
            .onFailure { error = it.message.orEmpty() }
    }

    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        when {
            ready -> PlatformWebView(
                url = manager.viewerUrl,
                modifier = Modifier.fillMaxSize().padding(top = 88.dp).navigationBarsPadding(),
            )
            error.isNotBlank() -> Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { retry += 1 }) { Text("Retry") }
            }
            else -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing Alpine Chromium", color = AetherOnSurfaceVariant)
            }
        }
        SettingsTopBar(stringResource(Res.string.chrome_label), onBack)
    }
}
