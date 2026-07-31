package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.aether_mark
import com.zhousl.aether.shared.resources.close_label
import com.zhousl.aether.shared.resources.get_started
import com.zhousl.aether.shared.resources.onboarding_welcome_subtitle
import com.zhousl.aether.shared.resources.onboarding_welcome_title
import com.zhousl.aether.shared.resources.skip_label
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LandingContentFadeDuration = 920
private val LandingTourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

@Composable
fun OnboardingLandingStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    timelineSpec: OnboardingTimelineSpec? = null,
) {
    var visible by remember(stepIndex, replayMode) { mutableStateOf(false) }
    LaunchedEffect(stepIndex, replayMode) {
        delay(180)
        visible = true
    }

    OnboardingResponsiveFrame(timelineSpec = timelineSpec) { wideLayout ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AetherBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                LandingChromeBar(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    topRightLabel = stringResource(
                        if (replayMode) Res.string.close_label else Res.string.skip_label,
                    ),
                    onTopRight = onSecondary,
                    showProgress = !wideLayout,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 28.dp, top = 4.dp, end = 28.dp, bottom = 20.dp),
                ) {
                    Spacer(modifier = Modifier.weight(0.72f))
                    AnimatedVisibility(
                        visible = visible,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = LandingContentFadeDuration,
                                easing = LandingTourEasing,
                            ),
                        ),
                        label = "landing_content",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.aether_mark),
                                contentDescription = null,
                                modifier = Modifier.size(104.dp),
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = stringResource(Res.string.onboarding_welcome_title),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = AetherOnSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(Res.string.onboarding_welcome_subtitle),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AetherOnSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1.28f))
                    AnimatedVisibility(
                        visible = visible,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = LandingContentFadeDuration,
                                delayMillis = 220,
                                easing = LandingTourEasing,
                            ),
                        ),
                        label = "landing_actions",
                    ) {
                        Button(
                            onClick = onPrimary,
                            modifier = if (wideLayout) Modifier.width(220.dp) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(stringResource(Res.string.get_started))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingChromeBar(
    stepIndex: Int,
    stepCount: Int,
    topRightLabel: String,
    onTopRight: () -> Unit,
    showProgress: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AetherBackground)
            .statusBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.size(40.dp))
            if (showProgress) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(stepCount) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (index + 1 == stepIndex) 20.dp else 7.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (index + 1 == stepIndex) AetherOnSurface else AetherOutlineSoft,
                                ),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
            TextButton(onClick = onTopRight) {
                Text(text = topRightLabel, color = AetherOnSurfaceVariant)
            }
        }
    }
}
