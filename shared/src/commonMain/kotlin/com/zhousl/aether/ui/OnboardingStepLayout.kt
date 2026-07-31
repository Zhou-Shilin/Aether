package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.back_label
import com.zhousl.aether.shared.resources.onboarding_timeline_credentials
import com.zhousl.aether.shared.resources.onboarding_timeline_models
import com.zhousl.aether.shared.resources.onboarding_timeline_provider
import com.zhousl.aether.shared.resources.onboarding_timeline_provider_choice
import com.zhousl.aether.shared.resources.onboarding_timeline_search
import com.zhousl.aether.shared.resources.onboarding_timeline_setup
import com.zhousl.aether.shared.resources.onboarding_timeline_welcome
import com.zhousl.aether.shared.resources.onboarding_timeline_connection
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val SharedMessageTravelDuration = 1_520
private const val SharedContentFadeDuration = 920
private const val SharedMessageSettleDelayMillis = 800L
private const val SharedMessageMinDurationMillis = 1_000L
private const val SharedMessageMaxDurationMillis = 3_300L
private val SharedTourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private const val OnboardingWideLayoutMinWidthDp = 700f
private val OnboardingTimelineWidth = 232.dp
private val OnboardingTimelineRowHeight = 46.dp
private val OnboardingTimelineNodeCenterX = 14.dp
private val OnboardingProviderSubstepRowHeight = 34.dp
private val OnboardingProviderSubstepsVerticalPadding = 10.dp
internal val LocalOnboardingWideLayout = staticCompositionLocalOf { false }
internal val LocalOnboardingTimelinePosition = staticCompositionLocalOf<Float?> { null }

enum class OnboardingTimelineStep {
    Welcome,
    Setup,
    Provider,
    Search,
}

data class OnboardingTimelineSpec(
    val activeStep: OnboardingTimelineStep,
    val providerSubstep: Int? = null,
    val onStepSelected: (OnboardingTimelineStep) -> Unit,
    val onProviderSubstepSelected: ((Int) -> Unit)? = null,
)

internal fun shouldUseWideOnboardingLayout(
    availableWidthDp: Float,
    availableHeightDp: Float,
): Boolean = availableWidthDp >= OnboardingWideLayoutMinWidthDp &&
    availableWidthDp > availableHeightDp

@Composable
fun OnboardingConversationStepPage(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
    isExiting: Boolean = false,
    timelineSpec: OnboardingTimelineSpec? = null,
    widePrimaryMessage: String? = null,
    widePrimaryContent: (@Composable ColumnScope.() -> Unit)? = null,
    wideAuxiliaryContent: (@Composable () -> Unit)? = null,
    wideAuxiliaryVisible: Boolean = wideAuxiliaryContent != null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingResponsiveFrame(
        timelineSpec = timelineSpec,
        auxiliaryVisible = wideAuxiliaryVisible,
        auxiliaryContent = wideAuxiliaryContent,
    ) { wideLayout ->
        SharedConversationPageContent(
            stepIndex = stepIndex,
            stepCount = stepCount,
            message = if (wideLayout) widePrimaryMessage ?: message else message,
            onBack = onBack,
            topRightLabel = topRightLabel,
            onTopRight = onTopRight,
            isExiting = isExiting,
            showCompactProgress = !wideLayout,
            content = if (wideLayout) widePrimaryContent ?: content else content,
        )
    }
}

@Composable
internal fun OnboardingResponsiveFrame(
    timelineSpec: OnboardingTimelineSpec?,
    auxiliaryContent: (@Composable () -> Unit)? = null,
    auxiliaryVisible: Boolean = auxiliaryContent != null,
    content: @Composable (wideLayout: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        val wideLayout = timelineSpec != null && shouldUseWideOnboardingLayout(
            availableWidthDp = maxWidth.value,
            availableHeightDp = maxHeight.value,
        )
        if (!wideLayout) {
            content(false)
            return@BoxWithConstraints
        }

        val sidebarWidth by animateDpAsState(
            targetValue = if (auxiliaryVisible) 0.dp else OnboardingTimelineWidth,
            animationSpec = tween(620, easing = SharedTourEasing),
            label = "onboarding_timeline_width",
        )
        val auxiliaryWidth by animateDpAsState(
            targetValue = if (auxiliaryVisible) maxWidth * 0.49f else 0.dp,
            animationSpec = tween(620, easing = SharedTourEasing),
            label = "onboarding_auxiliary_width",
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxHeight().width(sidebarWidth).clipToBounds(),
            ) {
                OnboardingTimelineSidebar(checkNotNull(timelineSpec))
            }
            Box(modifier = Modifier.fillMaxHeight().weight(1f).clipToBounds()) {
                CompositionLocalProvider(LocalOnboardingWideLayout provides true) {
                    content(true)
                }
            }
            Box(
                modifier = Modifier.fillMaxHeight().width(auxiliaryWidth).clipToBounds(),
            ) {
                OnboardingAuxiliaryVisibility(
                    visible = auxiliaryVisible,
                    content = auxiliaryContent,
                )
            }
        }
    }
}

@Composable
private fun OnboardingAuxiliaryVisibility(
    visible: Boolean,
    content: (@Composable () -> Unit)?,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(360, delayMillis = 120, easing = SharedTourEasing)),
        exit = fadeOut(tween(180, easing = SharedTourEasing)),
    ) {
        content?.invoke()
    }
}

@Composable
private fun SharedConversationPageContent(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
    isExiting: Boolean,
    showCompactProgress: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pageKey = remember(stepIndex, stepCount, message) { "$stepIndex/$stepCount:$message" }
    val contentVisible = rememberSharedStepContentVisible(pageKey, message)
    val topPadding by animateDpAsState(
        targetValue = if (contentVisible) 56.dp else 168.dp,
        animationSpec = tween(SharedMessageTravelDuration, easing = SharedTourEasing),
        label = "tour_message_travel",
    )

    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        AnimatedVisibility(
            visible = !isExiting,
            enter = fadeIn(animationSpec = tween(durationMillis = 0)),
            exit = fadeOut(tween(280, easing = SharedTourEasing)),
            label = "step_page_visibility",
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                SharedTourChromeBar(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    onBack = onBack,
                    topRightLabel = topRightLabel,
                    onTopRight = onTopRight,
                    showProgress = showCompactProgress,
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, top = 12.dp, end = 28.dp, bottom = 20.dp),
                ) {
                    Spacer(modifier = Modifier.height(topPadding))
                    SharedStreamingStepMessage(playKey = pageKey, text = message)
                    Spacer(modifier = Modifier.height(32.dp))
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(
                            tween(
                                SharedContentFadeDuration,
                                delayMillis = 180,
                                easing = SharedTourEasing,
                            ),
                        ),
                        exit = fadeOut(tween(180)),
                        label = "step_content_fade",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            content = content,
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
fun OnboardingStepLead(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )
    }
}

@Composable
fun OnboardingPrimaryActionButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
            disabledContainerColor = AetherOutlineSoft,
            disabledContentColor = AetherOnSurfaceVariant,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.8.dp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
fun OnboardingActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingPrimaryActionButton(
            label = primaryLabel,
            modifier = Modifier.weight(1f),
            enabled = primaryEnabled,
            isLoading = primaryLoading,
            onClick = onPrimary,
        )
        TextButton(onClick = onSecondary, modifier = Modifier.weight(0.62f)) {
            Text(
                text = secondaryLabel,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SharedTourChromeBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
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
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.back_label),
                        tint = AetherOnSurface,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
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

@Composable
private fun OnboardingTimelineSidebar(spec: OnboardingTimelineSpec) {
    val steps = listOf(
        OnboardingTimelineStep.Welcome to stringResource(Res.string.onboarding_timeline_welcome),
        OnboardingTimelineStep.Setup to stringResource(Res.string.onboarding_timeline_setup),
        OnboardingTimelineStep.Provider to stringResource(Res.string.onboarding_timeline_provider),
        OnboardingTimelineStep.Search to stringResource(Res.string.onboarding_timeline_search),
    )
    val providerSubsteps = listOf(
        stringResource(Res.string.onboarding_timeline_connection),
        stringResource(Res.string.onboarding_timeline_provider_choice),
        stringResource(Res.string.onboarding_timeline_credentials),
        stringResource(Res.string.onboarding_timeline_models),
    )
    val providerSubstepsVisible = spec.activeStep == OnboardingTimelineStep.Provider
    val providerSubstepsHeight = if (providerSubstepsVisible) {
        OnboardingProviderSubstepRowHeight * providerSubsteps.size +
            OnboardingProviderSubstepsVerticalPadding
    } else {
        0.dp
    }
    val timelineContentHeight = OnboardingTimelineRowHeight * steps.size + providerSubstepsHeight
    val timelineHeight = OnboardingTimelineRowHeight * steps.size +
        OnboardingProviderSubstepRowHeight * providerSubsteps.size +
        OnboardingProviderSubstepsVerticalPadding
    val localActiveStepCenterY by animateDpAsState(
        targetValue = OnboardingTimelineRowHeight * spec.activeStep.ordinal +
            OnboardingTimelineRowHeight / 2,
        animationSpec = tween(620, easing = SharedTourEasing),
        label = "onboarding_timeline_active_node_y",
    )
    val activeStepCenterY = LocalOnboardingTimelinePosition.current?.let { position ->
        OnboardingTimelineRowHeight * position + OnboardingTimelineRowHeight / 2
    } ?: localActiveStepCenterY

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(start = 34.dp, end = 18.dp, top = 78.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(timelineHeight)) {
            // One uninterrupted rail connects the exact center of every main step.
            Box(
                modifier = Modifier.offset(
                    x = OnboardingTimelineNodeCenterX - 0.75.dp,
                    y = OnboardingTimelineRowHeight / 2,
                ).width(1.5.dp).height(timelineContentHeight - OnboardingTimelineRowHeight)
                    .background(AetherOutlineSoft),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                steps.forEach { (step, label) ->
                    val selected = step == spec.activeStep
                    OnboardingTimelineRow(
                        label = label,
                        selected = selected,
                        onClick = { spec.onStepSelected(step) },
                    )
                    if (step == OnboardingTimelineStep.Provider && providerSubstepsVisible) {
                        OnboardingProviderSubsteps(
                            labels = providerSubsteps,
                            selectedIndex = spec.providerSubstep ?: 0,
                            onSelected = spec.onProviderSubstepSelected,
                        )
                    }
                }
            }

            // The selected marker is a single element, so it physically travels between nodes.
            Box(
                modifier = Modifier.offset(
                    x = OnboardingTimelineNodeCenterX - 6.dp,
                    y = activeStepCenterY - 6.dp,
                ).size(12.dp).clip(CircleShape).background(AetherOnSurface),
            )
        }
    }
}

@Composable
private fun OnboardingTimelineRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(OnboardingTimelineRowHeight)
            .clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(9.dp).clip(CircleShape).background(AetherOutlineSoft),
            )
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(AetherBackground))
        }
        Text(
            text = label,
            style = if (selected) {
                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = if (selected) AetherOnSurface else AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OnboardingProviderSubsteps(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: ((Int) -> Unit)?,
) {
    val selectedCenterY by animateDpAsState(
        targetValue = OnboardingProviderSubstepRowHeight * selectedIndex +
            OnboardingProviderSubstepRowHeight / 2,
        animationSpec = tween(520, easing = SharedTourEasing),
        label = "onboarding_provider_active_substep_y",
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(
            OnboardingProviderSubstepRowHeight * labels.size +
                OnboardingProviderSubstepsVerticalPadding,
        ).padding(start = 31.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier.fillMaxWidth().height(OnboardingProviderSubstepRowHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = onSelected != null) { onSelected?.invoke(index) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(modifier = Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(AetherOutlineSoft),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) AetherOnSurface else AetherOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier.offset(x = 8.dp, y = selectedCenterY - 4.dp).size(8.dp)
                .clip(CircleShape).background(AetherOnSurface),
        )
    }
}

@Composable
private fun rememberSharedStepContentVisible(key: Any, message: String): Boolean {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, message) {
        visible = false
        delay(sharedMessageRevealDuration(message) + SharedMessageSettleDelayMillis)
        visible = true
    }
    return visible
}

@Composable
private fun SharedStreamingStepMessage(playKey: Any, text: String) {
    var revealed by remember(playKey, text) { mutableStateOf("") }
    LaunchedEffect(playKey, text) {
        revealed = ""
        splitSharedRevealUnits(text).forEach { unit ->
            delay(sharedRevealUnitDelay(unit))
            revealed += unit
        }
    }
    Text(
        text = revealed.ifEmpty { " " },
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
        color = AetherOnSurface,
    )
}

private fun splitSharedRevealUnits(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val units = mutableListOf<String>()
    val builder = StringBuilder()
    text.forEach { char ->
        builder.append(char)
        if (char == ' ' || char == '\n' || char == '.' || char == '!' || char == '?' || char == ',') {
            units += builder.toString()
            builder.clear()
        }
    }
    if (builder.isNotEmpty()) units += builder.toString()
    return units
}

private fun sharedRevealUnitDelay(unit: String): Long {
    val trimmed = unit.trim()
    if (trimmed.isEmpty()) return 18L
    if (trimmed.length == 1 && trimmed.first() in setOf('.', ',', '!', '?')) return 180L
    return (80L + trimmed.length * 18L).coerceIn(96L, 240L)
}

private fun sharedMessageRevealDuration(message: String): Long =
    splitSharedRevealUnits(message)
        .sumOf(::sharedRevealUnitDelay)
        .coerceIn(SharedMessageMinDurationMillis, SharedMessageMaxDurationMillis)
