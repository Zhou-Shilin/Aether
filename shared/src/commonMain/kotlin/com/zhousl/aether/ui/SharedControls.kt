package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.platform.currentPlatformCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SharedSettingsPageTransitionDuration = 320
private val SharedSettingsPageTransitionEasing =
    CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

/**
 * Popup content is hosted in a separate native window on iOS. Mount that window for a complete
 * composition/draw frame before starting the transition, then retain it until exit completes.
 */
@Composable
internal fun SharedAnimatedPopupHost(
    visible: Boolean,
    content: @Composable (MutableTransitionState<Boolean>) -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    var mounted by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            withFrameNanos { }
            withFrameNanos { }
            transitionState.targetState = true
        } else {
            transitionState.targetState = false
        }
    }
    LaunchedEffect(visible, transitionState.currentState, transitionState.isIdle) {
        if (!visible && transitionState.isIdle && !transitionState.currentState) {
            mounted = false
        }
    }

    if (mounted) content(transitionState)
}

internal class SharedSettingsDismissGuard {
    private var activeReporter: Any? = null
    var hasUnsavedChanges by mutableStateOf(false)
        private set
    var saveShakeRequest by mutableIntStateOf(0)
        private set

    fun report(reporter: Any, hasChanges: Boolean) {
        activeReporter = reporter
        hasUnsavedChanges = hasChanges
    }

    fun clear(reporter: Any) {
        if (activeReporter === reporter) {
            activeReporter = null
            hasUnsavedChanges = false
        }
    }

    fun rejectDismiss() {
        saveShakeRequest += 1
    }
}

internal val LocalSharedSettingsDismissGuard =
    staticCompositionLocalOf<SharedSettingsDismissGuard?> { null }

internal val LocalSharedSettingsRichMotion = staticCompositionLocalOf { false }

@Composable
internal fun ReportSharedSettingsUnsavedChanges(hasChanges: Boolean) {
    val guard = LocalSharedSettingsDismissGuard.current ?: return
    val reporter = remember { Any() }
    SideEffect { guard.report(reporter, hasChanges) }
    DisposableEffect(guard, reporter) {
        onDispose { guard.clear(reporter) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.sharedSettingsBringIntoViewOnFocus(): Modifier = composed {
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

@Composable
internal fun <T> SharedSettingsPageTransition(
    targetState: T,
    depth: (T) -> Int,
    label: String,
    content: @Composable (T) -> Unit,
) {
    val richMotion = LocalSharedSettingsRichMotion.current
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            if (!currentPlatformCapabilities.layeredScreenTransitions && !richMotion) {
                return@AnimatedContent fadeIn(tween(120)) togetherWith
                    fadeOut(tween(80))
            }
            val isForward = depth(targetState) > depth(initialState)
            val enterSlide = slideInHorizontally(
                animationSpec = tween(
                    SharedSettingsPageTransitionDuration,
                    easing = SharedSettingsPageTransitionEasing,
                ),
                initialOffsetX = { if (isForward) it / 3 else -it / 3 },
            ) + fadeIn(
                tween(
                    SharedSettingsPageTransitionDuration,
                    easing = SharedSettingsPageTransitionEasing,
                ),
            )
            val exitSlide = slideOutHorizontally(
                animationSpec = tween(
                    SharedSettingsPageTransitionDuration,
                    easing = SharedSettingsPageTransitionEasing,
                ),
                targetOffsetX = { if (isForward) -it / 3 else it / 3 },
            ) + fadeOut(
                tween(
                    SharedSettingsPageTransitionDuration,
                    easing = SharedSettingsPageTransitionEasing,
                ),
            )
            enterSlide togetherWith exitSlide
        },
        label = label,
    ) { currentState ->
        content(currentState)
    }
}

@Composable
fun HeaderCircleButton(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = Color.White,
    iconTint: Color = AetherOnSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(CircleShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(iconSize),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun SettingsCardGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh),
    ) {
        content()
    }
}

@Composable
fun CardDivider() {
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun SharedSettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AetherPrimary,
            contentColor = AetherOnPrimary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SharedSettingsSubtleActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AetherSurface,
            contentColor = AetherOnSurface,
            disabledContainerColor = AetherSurface.copy(alpha = 0.55f),
            disabledContentColor = AetherOnSurfaceVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SharedActionPreviewPill(label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AetherBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = AetherPrimary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AetherOnSurface)
    }
}

@Composable
internal fun SharedSettingsToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun SharedSettingsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                else AetherBackground
            )
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SharedSmallChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val textColor = if (destructive) MaterialTheme.colorScheme.error else AetherOnSurface
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(
            if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
            else AetherSurfaceHigh
        ).clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsNavRowContent(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        title = title,
        subtitle = subtitle,
        showChevron = showChevron,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsNavRow(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsNavRowContent(
        icon = {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        title = title,
        subtitle = subtitle,
        showChevron = showChevron,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun SettingsNavRowContent(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    showChevron: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.alpha(contentAlpha)) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = AetherOnSurfaceVariant.copy(alpha = if (enabled) 0.5f else 0.2f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
