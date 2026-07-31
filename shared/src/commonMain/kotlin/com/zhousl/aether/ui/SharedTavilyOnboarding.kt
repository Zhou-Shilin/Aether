package com.zhousl.aether.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedTavilyFollowUpTour(
    initialValue: String,
    onClose: () -> Unit,
    onDone: (String) -> Unit,
    timelineSpec: OnboardingTimelineSpec? = null,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val apiKeyLabel = stringResource(Res.string.onboarding_api_key)
    var revealSecret by rememberSaveable(apiKeyLabel) { mutableStateOf(false) }
    OnboardingConversationStepPage(
        stepIndex = if (timelineSpec == null) 1 else 4,
        stepCount = if (timelineSpec == null) 1 else 4,
        message = stringResource(Res.string.onboarding_tavily_message),
        onBack = onClose,
        topRightLabel = stringResource(Res.string.common_close),
        onTopRight = onClose,
        timelineSpec = timelineSpec,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.tavily_mark),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text("Tavily", style = MaterialTheme.typography.titleMedium, color = AetherOnSurface)
                }
                Text(
                    stringResource(Res.string.onboarding_tavily_optional_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    apiKeyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurfaceVariant,
                )
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AetherSurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .sharedTavilyBringIntoViewOnFocus(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
                    cursorBrush = SolidColor(AetherOnSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (revealSecret) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    decorationBox = { field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (value.isBlank()) {
                                    Text(
                                        stringResource(Res.string.onboarding_paste_it_here),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AetherOnSurfaceVariant.copy(alpha = 0.72f),
                                    )
                                }
                                field()
                            }
                            IconButton(
                                onClick = { revealSecret = !revealSecret },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    if (revealSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(
                                        if (revealSecret) {
                                            Res.string.common_hide_password
                                        } else {
                                            Res.string.common_show_password
                                        },
                                    ),
                                    tint = AetherOnSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
            }
            OnboardingPrimaryActionButton(
                label = stringResource(Res.string.common_done),
                onClick = { onDone(value.trim()) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.sharedTavilyBringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}
