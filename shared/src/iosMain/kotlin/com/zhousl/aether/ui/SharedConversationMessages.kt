package com.zhousl.aether.ui

import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.platform.LocalReduceMotion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.zhousl.aether.data.pi.SharedPiUsage
import com.zhousl.aether.data.pi.BrowserToolName
import com.zhousl.aether.data.pi.LegacyChromeToolName
import com.zhousl.aether.data.pi.SharedBrowserDisplayState
import com.zhousl.aether.data.pi.SharedChromeManager
import com.zhousl.aether.platform.PlatformBrowserView
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.platformUptimeMillis
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOnPrimaryContainer
import com.zhousl.aether.ui.theme.AetherMessageBubble
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import com.zhousl.aether.ui.theme.AetherTertiary
import com.zhousl.aether.ui.theme.AetherError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SharedStatisticsPopupEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private const val SharedToolInvocationCollapseThreshold = 3
private const val SharedToolTransitionDurationMillis = 360
private const val SharedToolGroupCollapseStageDelayMillis = 180L
private const val SharedToolInvocationAutoExpandDelayMillis = 1_000L
private const val SharedStreamingChunkFadeDurationMillis = 400
private const val SharedStreamingInitialChunkFadeDurationMillis = 600
private const val SharedMinimumWallClockMillis = 946_684_800_000L
private val SharedToolGroupIndent = 14.dp
private val SharedTimelineGlyphWidth = 22.dp
private val SharedTimelineIconSize = 18.dp
private val SharedTimelineLineWidth = 2.dp
private val SharedTimelineLineTopGap = 9.dp
private val SharedTimelineLineBottomGap = 0.dp

internal data class SharedChatToolInvocation(
    val id: String,
    val name: String,
    val summary: String,
    val output: String = "",
    val argumentsJson: String = "",
    val outputJson: String = "",
    val isRunning: Boolean = true,
    val isError: Boolean = false,
    val startedAtUptimeMillis: Long = 0L,
    val completedAtUptimeMillis: Long? = null,
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    val timelineOrder: Long = 0L,
)

private fun isSharedBrowserTool(tool: SharedChatToolInvocation): Boolean =
    tool.name.trim().equals(BrowserToolName, ignoreCase = true) ||
        tool.name.trim().equals(LegacyChromeToolName, ignoreCase = true)

internal fun SharedChatMessage.sharedBrowserTools(): List<SharedChatToolInvocation> = buildList {
    addAll(tools.filter(::isSharedBrowserTool))
    responseBlocks.forEach { block ->
        when (block) {
            is SharedAssistantResponseBlock.ToolGroup -> addAll(block.tools.filter(::isSharedBrowserTool))
            is SharedAssistantResponseBlock.Reasoning -> addAll(block.trace.toolInvocations.filter(::isSharedBrowserTool))
            is SharedAssistantResponseBlock.Text -> Unit
            is SharedAssistantResponseBlock.Status -> Unit
        }
    }
}.distinctBy(SharedChatToolInvocation::id)

internal fun SharedChatMessage.withoutSharedBrowserTools(): SharedChatMessage {
    val firstBrowserBlock = responseBlocks.indexOfFirst { block ->
        when (block) {
            is SharedAssistantResponseBlock.ToolGroup -> block.tools.any(::isSharedBrowserTool)
            is SharedAssistantResponseBlock.Reasoning -> block.trace.toolInvocations.any(::isSharedBrowserTool)
            is SharedAssistantResponseBlock.Text -> false
            is SharedAssistantResponseBlock.Status -> false
        }
    }
    return copy(
        tools = tools.filterNot(::isSharedBrowserTool),
        reasoningText = if (firstBrowserBlock >= 0 || sharedBrowserTools().isNotEmpty()) "" else reasoningText,
        responseBlocks = responseBlocks.mapIndexedNotNull { index, block ->
            when (block) {
                is SharedAssistantResponseBlock.Text -> block
                is SharedAssistantResponseBlock.Status -> block
                is SharedAssistantResponseBlock.ToolGroup -> block.copy(
                    tools = block.tools.filterNot(::isSharedBrowserTool),
                ).takeIf { it.tools.isNotEmpty() }
                is SharedAssistantResponseBlock.Reasoning -> {
                    val retainedTools = block.trace.toolInvocations.filterNot(::isSharedBrowserTool)
                    block.copy(
                        trace = block.trace.copy(toolInvocations = retainedTools),
                    ).takeUnless {
                        firstBrowserBlock >= 0 && index >= firstBrowserBlock && retainedTools.isEmpty()
                    }
                }
            }
        },
    )
}

internal fun SharedChatMessage.sharedBrowserOverlayText(): String {
    val firstBrowserBlock = responseBlocks.indexOfFirst { block ->
        when (block) {
            is SharedAssistantResponseBlock.ToolGroup -> block.tools.any(::isSharedBrowserTool)
            is SharedAssistantResponseBlock.Reasoning -> block.trace.toolInvocations.any(::isSharedBrowserTool)
            is SharedAssistantResponseBlock.Text -> false
            is SharedAssistantResponseBlock.Status -> false
        }
    }
    if (firstBrowserBlock < 0) return reasoningText.takeIf { sharedBrowserTools().isNotEmpty() }.orEmpty()
    return responseBlocks.drop(firstBrowserBlock).asReversed().firstNotNullOfOrNull { block ->
        val trace = (block as? SharedAssistantResponseBlock.Reasoning)?.trace ?: return@firstNotNullOfOrNull null
        trace.latestStatusText.ifBlank {
            trace.chunks.lastOrNull { it.detail.isNotBlank() || it.title.isNotBlank() }
                ?.let { it.detail.ifBlank(it::title) }
                .orEmpty()
        }.takeIf(String::isNotBlank)
    }.orEmpty().ifBlank { reasoningText }
}

internal fun SharedChatToolInvocation.sharedStoredBrowserDisplayState(): SharedBrowserDisplayState {
    val output = runCatching { Json.parseToJsonElement(outputJson).jsonObject }.getOrNull()
        ?: return SharedBrowserDisplayState(status = outputJson.ifBlank { summary })
    return SharedBrowserDisplayState(
        isActive = output["started"]?.jsonPrimitive?.booleanOrNull == true || isRunning,
        width = output["width"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 390,
        height = output["height"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 844,
        screenshotBase64 = output["screenshot_base64"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        screenshotMimeType = output["screenshot_mime_type"]?.jsonPrimitive?.contentOrNull
            .orEmpty().ifBlank { "image/png" },
        previewPath = output["preview_path"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        url = output["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        title = output["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        cursorX = output["cursor_x"]?.jsonPrimitive?.intOrNull,
        cursorY = output["cursor_y"]?.jsonPrimitive?.intOrNull,
        cursorAnimationDurationMillis = output["cursor_animation_duration_ms"]
            ?.jsonPrimitive?.intOrNull?.coerceIn(80, 1_200) ?: 220,
        status = output["stdout"]?.jsonPrimitive?.contentOrNull
            .orEmpty().ifBlank { output["error"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .ifBlank { summary },
    )
}

internal data class SharedBrowserReplayFrame(
    val tool: SharedChatToolInvocation,
    val displayState: SharedBrowserDisplayState,
)

internal fun SharedChatMessage.sharedBrowserReplayFrames(): List<SharedBrowserReplayFrame> =
    sharedBrowserTools().mapNotNull { tool ->
        val state = tool.sharedStoredBrowserDisplayState()
        if (state.previewPath.isBlank() && state.screenshotBase64.isBlank()) return@mapNotNull null
        SharedBrowserReplayFrame(tool = tool, displayState = state.copy(isActive = false))
    }

@Composable
internal fun SharedBrowserPreviewCard(
    displayState: SharedBrowserDisplayState,
    tool: SharedChatToolInvocation,
    runtime: MultiplatformLocalRuntime,
    manager: SharedChromeManager? = null,
    isLive: Boolean = false,
    replayFrames: List<SharedBrowserReplayFrame> = emptyList(),
    overlayText: String = "",
    onOpenLink: (String) -> Unit = {},
) {
    var selectedFrameIndex by rememberSaveable(tool.id) {
        mutableStateOf((replayFrames.size - 1).coerceAtLeast(0))
    }
    if (selectedFrameIndex !in replayFrames.indices) {
        selectedFrameIndex = (replayFrames.size - 1).coerceAtLeast(0)
    }
    val selectedFrame = replayFrames.getOrNull(selectedFrameIndex)
    val presentedState = if (isLive) displayState else selectedFrame?.displayState ?: displayState
    val presentedTool = if (isLive) tool else selectedFrame?.tool ?: tool
    val presentedOverlayText = if (
        isLive || selectedFrameIndex == replayFrames.lastIndex
    ) {
        overlayText
    } else {
        ""
    }
    val previewBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = presentedState.previewPath,
        key2 = presentedState.lastUpdatedMillis,
    ) {
        value = runCatching {
            when {
                presentedState.screenshotBase64.isNotBlank() ->
                    Base64.decode(presentedState.screenshotBase64).decodeToImageBitmap()
                presentedState.previewPath.isNotBlank() ->
                    runtime.fileSystem.read(presentedState.previewPath).decodeToImageBitmap()
                else -> null
            }
        }.getOrNull()
    }
    val arguments = runCatching { Json.parseToJsonElement(presentedTool.argumentsJson).jsonObject }.getOrNull()
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SharedBrowserToolStatus(tool = presentedTool, arguments = arguments)
        Box(
            modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp))
                .background(sharedBrowserPreviewBackdropBrush()),
            contentAlignment = Alignment.Center,
        ) {
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val imagePaddingPx = with(density) { 10.dp.toPx() }
            val cursorOffset = remember(
                previewSize,
                presentedState.width,
                presentedState.height,
                presentedState.cursorX,
                presentedState.cursorY,
            ) {
                resolveSharedBrowserCursorOffset(
                    previewSize = previewSize,
                    imagePaddingPx = imagePaddingPx,
                    displayWidth = presentedState.width,
                    displayHeight = presentedState.height,
                    cursorX = presentedState.cursorX,
                    cursorY = presentedState.cursorY,
                )
            }
            val animationDuration = presentedState.cursorAnimationDurationMillis.coerceIn(80, 1_200)
            val animatedCursorOffset by animateIntOffsetAsState(
                targetValue = cursorOffset,
                animationSpec = tween(animationDuration, easing = SharedStatisticsPopupEasing),
                label = "shared_browser_cursor_offset",
            )
            if (isLive && manager != null) {
                PlatformBrowserView(
                    manager = manager,
                    modifier = Modifier.fillMaxHeight().padding(10.dp).aspectRatio(
                        presentedState.width.coerceAtLeast(1).toFloat() /
                            presentedState.height.coerceAtLeast(1).toFloat(),
                        matchHeightConstraintsFirst = true,
                    ).clip(RoundedCornerShape(14.dp)),
                )
            } else if (previewBitmap != null) {
                Image(
                    bitmap = requireNotNull(previewBitmap),
                    contentDescription = "Browser preview",
                    modifier = Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = if (presentedTool.isRunning) {
                        stringResource(Res.string.chat_browser_preview_waiting)
                    } else presentedState.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it },
            ) {
                Icon(
                    imageVector = LucideIcons.MousePointer2WhiteFill,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.offset {
                        val tipInsetPx = with(density) { 5.dp.roundToPx() }
                        IntOffset(animatedCursorOffset.x - tipInsetPx, animatedCursorOffset.y - tipInsetPx)
                    }.size(30.dp),
                )
                if (presentedOverlayText.isNotBlank()) {
                    val bubbleOffset = remember(cursorOffset, previewSize, density) {
                        resolveSharedBrowserBubbleOffset(cursorOffset, previewSize, density)
                    }
                    val animatedBubbleOffset by animateIntOffsetAsState(
                        targetValue = bubbleOffset,
                        animationSpec = tween(animationDuration, easing = SharedStatisticsPopupEasing),
                        label = "shared_browser_bubble_offset",
                    )
                    SharedBrowserCursorTextBubble(
                        text = presentedOverlayText,
                        runtime = runtime,
                        onOpenLink = onOpenLink,
                        modifier = Modifier.offset { animatedBubbleOffset },
                    )
                }
            }
        }
        if (!isLive && replayFrames.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SharedBranchStepButton(
                    enabled = selectedFrameIndex > 0,
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    onClick = { selectedFrameIndex = (selectedFrameIndex - 1).coerceAtLeast(0) },
                )
                Text(
                    text = "${selectedFrameIndex + 1} / ${replayFrames.size}",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurfaceVariant,
                )
                SharedBranchStepButton(
                    enabled = selectedFrameIndex < replayFrames.lastIndex,
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    onClick = {
                        selectedFrameIndex = (selectedFrameIndex + 1).coerceAtMost(replayFrames.lastIndex)
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedBrowserToolStatus(
    tool: SharedChatToolInvocation,
    arguments: JsonObject?,
) {
    val label = sharedBrowserToolTitle(tool, arguments)
    Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AetherSurfaceHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF5D7CFF), modifier = Modifier.size(16.dp))
            if (tool.isRunning) {
                SharedReasoningShimmerText(label, modifier = Modifier.weight(1f), travelDurationMillis = 2_600)
            } else {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AetherOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
}

@Composable
private fun sharedBrowserToolTitle(
    tool: SharedChatToolInvocation,
    arguments: JsonObject?,
): String {
    val action = arguments?.get("action")?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
    val running = tool.isRunning
    return when (action) {
        "start" -> stringResource(if (running) Res.string.tool_title_starting_chrome else Res.string.tool_title_started_chrome)
        "status" -> stringResource(if (running) Res.string.tool_title_checking_chrome else Res.string.tool_title_checked_chrome)
        "navigate", "open" -> {
            val url = arguments?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (url.isBlank()) {
                stringResource(if (running) Res.string.tool_title_navigating_chrome else Res.string.tool_title_navigated_chrome)
            } else {
                stringResource(
                    if (running) Res.string.tool_title_navigating_chrome_url else Res.string.tool_title_navigated_chrome_url,
                    url.take(72) + if (url.length > 72) "..." else "",
                )
            }
        }
        "click", "tap" -> stringResource(if (running) Res.string.tool_title_tapping_chrome else Res.string.tool_title_tapped_chrome)
        "scroll", "swipe" -> stringResource(if (running) Res.string.tool_title_scrolling_chrome else Res.string.tool_title_scrolled_chrome)
        "type", "text" -> stringResource(if (running) Res.string.tool_title_typing_chrome else Res.string.tool_title_typed_chrome)
        "key" -> {
            val key = arguments?.get("key")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (key.isBlank()) {
                stringResource(if (running) Res.string.tool_title_pressing_chrome else Res.string.tool_title_pressed_chrome)
            } else {
                stringResource(
                    if (running) Res.string.tool_title_pressing_chrome_key else Res.string.tool_title_pressed_chrome_key,
                    key.take(32),
                )
            }
        }
        "back" -> stringResource(if (running) Res.string.tool_title_going_back_chrome else Res.string.tool_title_went_back_chrome)
        "forward" -> stringResource(if (running) Res.string.tool_title_going_forward_chrome else Res.string.tool_title_went_forward_chrome)
        "reload" -> stringResource(if (running) Res.string.tool_title_reloading_chrome else Res.string.tool_title_reloaded_chrome)
        "screenshot" -> stringResource(if (running) Res.string.tool_title_capturing_chrome else Res.string.tool_title_captured_chrome)
        "stop" -> stringResource(if (running) Res.string.tool_title_stopping_chrome else Res.string.tool_title_stopped_chrome)
        else -> stringResource(if (running) Res.string.tool_title_using_chrome else Res.string.tool_title_used_chrome)
    }
}

@Composable
private fun SharedBrowserCursorTextBubble(
    text: String,
    runtime: MultiplatformLocalRuntime,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text, scrollState.maxValue) {
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }
    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(12.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.heightIn(max = 104.dp).verticalScroll(scrollState)) {
            SharedStreamingMarkdownContent(content = text, runtime = runtime, onOpenLink = onOpenLink)
        }
    }
}

private fun resolveSharedBrowserCursorOffset(
    previewSize: IntSize,
    imagePaddingPx: Float,
    displayWidth: Int,
    displayHeight: Int,
    cursorX: Int?,
    cursorY: Int?,
): IntOffset {
    if (previewSize.width <= 0 || previewSize.height <= 0) return IntOffset.Zero
    val contentWidth = (previewSize.width - imagePaddingPx * 2f).coerceAtLeast(1f)
    val contentHeight = (previewSize.height - imagePaddingPx * 2f).coerceAtLeast(1f)
    val sourceWidth = displayWidth.coerceAtLeast(1)
    val sourceHeight = displayHeight.coerceAtLeast(1)
    val scale = minOf(contentWidth / sourceWidth, contentHeight / sourceHeight)
    val renderedWidth = sourceWidth * scale
    val renderedHeight = sourceHeight * scale
    val renderedLeft = imagePaddingPx + (contentWidth - renderedWidth) / 2f
    val renderedTop = imagePaddingPx + (contentHeight - renderedHeight) / 2f
    val fractionX = cursorX?.toFloat()?.div(sourceWidth) ?: 0.58f
    val fractionY = cursorY?.toFloat()?.div(sourceHeight) ?: 0.56f
    return IntOffset(
        (renderedLeft + renderedWidth * fractionX.coerceIn(0f, 1f)).roundToInt(),
        (renderedTop + renderedHeight * fractionY.coerceIn(0f, 1f)).roundToInt(),
    )
}

private fun resolveSharedBrowserBubbleOffset(
    cursorOffset: IntOffset,
    previewSize: IntSize,
    density: androidx.compose.ui.unit.Density,
): IntOffset {
    val bubbleMaxWidthPx = with(density) { 320.dp.roundToPx() }
    val bubbleMaxHeightPx = with(density) { 128.dp.roundToPx() }
    val gapPx = with(density) { 34.dp.roundToPx() }
    val edgePaddingPx = with(density) { 8.dp.roundToPx() }
    val targetY = if (cursorOffset.y < previewSize.height / 2) {
        cursorOffset.y + gapPx
    } else {
        cursorOffset.y - gapPx - bubbleMaxHeightPx
    }
    return IntOffset(
        (cursorOffset.x + gapPx).coerceIn(
            edgePaddingPx,
            (previewSize.width - bubbleMaxWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
        ),
        targetY.coerceIn(edgePaddingPx, (previewSize.height - edgePaddingPx).coerceAtLeast(edgePaddingPx)),
    )
}

private fun sharedBrowserPreviewBackdropBrush(): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFFBEEBFF),
        0.22f to Color(0xFF75C7FF),
        0.44f to Color(0xFFD5E9FF),
        0.68f to Color(0xFF83B5FF),
        1.00f to Color(0xFF4E86F7),
    ),
    start = Offset.Zero,
    end = Offset(900f, 620f),
)

internal data class SharedReasoningSummaryChunk(
    val id: String,
    val title: String = "",
    val detail: String = "",
    val rawText: String = "",
    val isPending: Boolean = false,
    val createdAtMillis: Long = 0L,
    val timelineOrder: Long = 0L,
)

internal data class SharedReasoningTrace(
    val id: String,
    val rawText: String = "",
    val chunks: List<SharedReasoningSummaryChunk> = emptyList(),
    val toolInvocations: List<SharedChatToolInvocation> = emptyList(),
    val latestStatusText: String = "",
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
) {
    val hasTimelineContent: Boolean
        get() = chunks.isNotEmpty() || toolInvocations.isNotEmpty()
}

internal sealed interface SharedAssistantResponseBlock {
    val id: String

    data class Text(
        override val id: String,
        val text: String,
    ) : SharedAssistantResponseBlock

    data class Reasoning(
        override val id: String,
        val trace: SharedReasoningTrace,
    ) : SharedAssistantResponseBlock

    data class ToolGroup(
        override val id: String,
        val tools: List<SharedChatToolInvocation>,
    ) : SharedAssistantResponseBlock

    data class Status(
        override val id: String,
        val text: String,
        val detail: String = "",
    ) : SharedAssistantResponseBlock
}

internal fun completedReconnectStatus(status: String): String {
    val match = Regex("^Reconnecting\\.\\.\\.\\s*(.*)$", RegexOption.IGNORE_CASE).matchEntire(status.trim())
        ?: return status
    return "Reconnected" + match.groupValues[1].takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
}

internal fun SharedChatMessage.withStreamingStatus(
    text: String,
    detail: String,
): SharedChatMessage {
    if (!text.startsWith("Reconnecting", ignoreCase = true)) {
        return completePendingReconnect().copy(status = text, statusDetail = detail)
    }
    val completed = completeAssistantReasoning()
    val last = completed.responseBlocks.lastOrNull()
    val blocks = if (last is SharedAssistantResponseBlock.Status &&
        last.text.startsWith("Reconnecting", ignoreCase = true)
    ) {
        completed.responseBlocks.dropLast(1) + last.copy(text = text, detail = detail)
    } else {
        completed.responseBlocks + SharedAssistantResponseBlock.Status(platformRandomUuid(), text, detail)
    }
    return completed.copy(responseBlocks = blocks, status = "", statusDetail = "")
}

internal fun SharedChatMessage.completePendingReconnect(): SharedChatMessage {
    val blocks = responseBlocks.map { block ->
        if (block is SharedAssistantResponseBlock.Status &&
            block.text.startsWith("Reconnecting", ignoreCase = true)
        ) {
            block.copy(text = completedReconnectStatus(block.text))
        } else {
            block
        }
    }
    return copy(responseBlocks = blocks, status = completedReconnectStatus(status))
}

internal fun SharedReasoningTrace.hasVisibleSharedReasoningStatus(): Boolean =
    latestStatusText.isNotBlank() ||
        rawText.isNotBlank() ||
        hasTimelineContent ||
        completedAtMillis != null

internal fun List<SharedAssistantResponseBlock>.hasVisibleSharedPendingWork(): Boolean =
    any { block ->
        when (block) {
            is SharedAssistantResponseBlock.Text -> block.text.isNotBlank()
            is SharedAssistantResponseBlock.ToolGroup -> block.tools.isNotEmpty()
            is SharedAssistantResponseBlock.Reasoning -> block.trace.hasVisibleSharedReasoningStatus()
            is SharedAssistantResponseBlock.Status -> block.text.isNotBlank()
        }
    }

internal fun List<SharedAssistantResponseBlock>.sharedWorkStartedAtMillis(
    fallbackStartedAtMillis: Long,
): Long = flatMap { block ->
    when (block) {
        is SharedAssistantResponseBlock.Text -> emptyList()
        is SharedAssistantResponseBlock.Status -> emptyList()
        is SharedAssistantResponseBlock.ToolGroup -> block.tools.mapNotNull { tool ->
            tool.startedAtMillis.takeIf { it >= SharedMinimumWallClockMillis }
        }
        is SharedAssistantResponseBlock.Reasoning -> buildList {
            block.trace.startedAtMillis.takeIf { it >= SharedMinimumWallClockMillis }?.let(::add)
            block.trace.chunks.forEach { chunk ->
                chunk.createdAtMillis.takeIf { it >= SharedMinimumWallClockMillis }?.let(::add)
            }
            block.trace.toolInvocations.forEach { tool ->
                tool.startedAtMillis.takeIf { it >= SharedMinimumWallClockMillis }?.let(::add)
            }
        }
    }
}.minOrNull()
    ?: fallbackStartedAtMillis.takeIf { it >= SharedMinimumWallClockMillis }
    ?: 0L

internal fun sharedRunningWorkDurationMillis(
    startedAtMillis: Long,
    nowMillis: Long = platformCurrentTimeMillis(),
): Long = if (startedAtMillis >= SharedMinimumWallClockMillis) {
    (nowMillis - startedAtMillis).coerceAtLeast(1_000L)
} else {
    1_000L
}

internal fun sharedCompletedWorkDurationMillis(
    startedAtMillis: Long,
    completedAtMillis: Long?,
    fallbackDurationMillis: Long?,
): Long {
    if (
        startedAtMillis >= SharedMinimumWallClockMillis &&
        completedAtMillis != null &&
        completedAtMillis >= startedAtMillis
    ) {
        return (completedAtMillis - startedAtMillis).coerceAtLeast(1_000L)
    }
    return fallbackDurationMillis?.takeIf { it > 0L }?.coerceAtLeast(1_000L) ?: 1_000L
}

internal data class SharedChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val workspacePath: String,
    val sizeBytes: Long = 0,
    val workspaceState: SharedAttachmentWorkspaceState = SharedAttachmentWorkspaceState.Ready,
    val workspaceError: String = "",
    val workspaceBytesCopied: Long = 0L,
    val workspaceBytesPerSecond: Long = 0L,
    val inlineBase64: String = "",
    val sourceIdentifier: String = "",
    val previewBytes: ByteArray? = null,
)

internal enum class SharedAttachmentWorkspaceState {
    Pending,
    Ready,
    Failed,
}

internal data class SharedMessageMetrics(
    val thoughtDurationMillis: Long? = null,
    val outputTokensPerSecond: Double? = null,
    val firstTokenLatencyMillis: Long? = null,
    val tokenUsageSource: String? = null,
)

@Composable
internal fun SharedComposerAttachmentTray(
    attachments: List<SharedChatAttachment>,
    runtime: MultiplatformLocalRuntime,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        attachments.forEach { attachment ->
            if (attachment.mimeType.startsWith("image/", ignoreCase = true)) {
                SharedComposerImageAttachmentCard(
                    attachment = attachment,
                    runtime = runtime,
                    onRemove = { onRemoveAttachment(attachment.id) },
                )
            } else {
                SharedComposerFileAttachmentCard(
                    attachment = attachment,
                    onRemove = { onRemoveAttachment(attachment.id) },
                )
            }
        }
    }
}

@Composable
private fun SharedComposerImageAttachmentCard(
    attachment: SharedChatAttachment,
    runtime: MultiplatformLocalRuntime,
    onRemove: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = attachment.workspacePath,
        key2 = attachment.previewBytes,
        key3 = runtime,
    ) {
        value = runCatching {
            readSharedAttachmentBytes(attachment, runtime).decodeToImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(AetherSurfaceHigh).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = requireNotNull(bitmap),
                    contentDescription = attachment.name,
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = AetherOnSurfaceVariant)
            }
        }
        SharedComposerAttachmentDetails(
            attachment = attachment,
            kindLabel = stringResource(Res.string.attachment_type_photo),
            modifier = Modifier.weight(1f),
        )
        SharedComposerRemoveAttachmentButton(onRemove)
    }
}

@Composable
private fun SharedComposerFileAttachmentCard(
    attachment: SharedChatAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(AetherSurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = AetherOnSurface)
        }
        SharedComposerAttachmentDetails(
            attachment = attachment,
            kindLabel = stringResource(Res.string.attachment_type_file),
            modifier = Modifier.weight(1f),
        )
        SharedComposerRemoveAttachmentButton(onRemove)
    }
}

@Composable
private fun SharedComposerAttachmentDetails(
    attachment: SharedChatAttachment,
    kindLabel: String,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sharedComposerAttachmentMetaLabel(kindLabel, attachment),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sharedComposerAttachmentMetaLabel(
    kindLabel: String,
    attachment: SharedChatAttachment,
): String {
    val base = sharedAttachmentMetaLabel(kindLabel, attachment)
    val copyingLabel = stringResource(Res.string.attachment_copying_to_workspace)
    val status = when (attachment.workspaceState) {
        SharedAttachmentWorkspaceState.Pending -> sharedWorkspaceCopyProgress(copyingLabel, attachment)
        SharedAttachmentWorkspaceState.Failed ->
            stringResource(Res.string.attachment_workspace_copy_failed)
        SharedAttachmentWorkspaceState.Ready -> ""
    }
    return listOf(base, status).filter(String::isNotBlank).joinToString(" | ")
}

internal fun sharedWorkspaceCopyProgress(
    copyingLabel: String,
    attachment: SharedChatAttachment,
): String {
    val copiedLabel = attachment.workspaceBytesCopied
        .takeIf { it > 0L }
        ?.let(::formatSharedAttachmentSize)
    val totalLabel = attachment.sizeBytes.takeIf { it > 0L }?.let(::formatSharedAttachmentSize)
    val speedLabel = attachment.workspaceBytesPerSecond
        .takeIf { it > 0L }
        ?.let { "${formatSharedAttachmentSize(it)}/s" }
    val progressLabel = when {
        copiedLabel != null && totalLabel != null -> "$copiedLabel / $totalLabel"
        copiedLabel != null -> copiedLabel
        else -> null
    }
    return listOfNotNull(copyingLabel, progressLabel, speedLabel).joinToString(" · ")
}

@Composable
private fun SharedComposerRemoveAttachmentButton(onRemove: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(Res.string.attachment_remove),
            tint = AetherOnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun SharedPendingInputBubble(pending: SharedPendingTurn) {
    val attachmentLabel = when (pending.attachments.size) {
        0 -> null
        1 -> stringResource(Res.string.chat_attachment_count_one)
        else -> stringResource(Res.string.chat_attachment_count_other, pending.attachments.size)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(24.dp))
                .background(AetherSurfaceHigh.copy(alpha = 0.96f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (pending.mode == SharedPendingTurnMode.Steer) {
                            Res.string.chat_pending_input_steering
                        } else {
                            Res.string.chat_pending_input_queued
                        },
                    ),
                    color = AetherOnSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                attachmentLabel?.let { label ->
                    Text(label, color = AetherOnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                pending.sharedPreviewText(),
                color = AetherOnSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SharedConversationMessage(
    message: SharedChatMessage,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCopy: (String) -> Boolean,
    onEdit: () -> Unit,
    onPreviousBranch: () -> Unit,
    onNextBranch: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onRetryUserMessage: (() -> Unit)? = null,
    onOpenAttachment: ((SharedChatAttachment) -> Unit)? = null,
    runtime: MultiplatformLocalRuntime? = null,
    onOpenLink: (String) -> Unit = {},
    sessionTotalTokens: Long? = null,
    metrics: SharedMessageMetrics = SharedMessageMetrics(),
) {
    val customMessage = LocalSharedAetherExtensionUiController.current
        ?.snapshot
        ?.customMessages
        ?.firstOrNull { it.id == message.id }
    if (customMessage != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            SharedAetherExtensionTree(
                value = customMessage.tree,
                extensionId = customMessage.extensionId,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (message.fromUser) {
            var menuOpen by remember(message.id) { mutableStateOf(false) }
            var selectTextOpen by remember(message.id) { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxWithConstraints {
                    val userBubbleMaxWidth = (maxWidth * 0.72f).coerceIn(300.dp, 520.dp)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        message.attachments.forEach { attachment ->
                            SharedAttachmentCard(attachment, onOpenAttachment, runtime)
                        }
                        ShowcaseSkillBadge(message.id, message.providerPayloadJson)
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                modifier = Modifier
                                    .widthIn(max = userBubbleMaxWidth)
                                    .shadow(
                                        10.dp,
                                        RoundedCornerShape(24.dp),
                                        ambientColor = AetherScrim,
                                        spotColor = AetherScrim,
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(AetherMessageBubble)
                                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AetherOnPrimaryContainer,
                            )
                        }
                        if (message.branchCount > 1) {
                            Row(
                                modifier = Modifier.padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SharedBranchStepButton(
                                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    enabled = message.branchIndex > 0,
                                    onClick = onPreviousBranch,
                                )
                                Text(
                                    text = "${message.branchIndex + 1}/${message.branchCount}",
                                    color = AetherOnSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                                SharedBranchStepButton(
                                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    enabled = message.branchIndex < message.branchCount - 1,
                                    onClick = onNextBranch,
                                )
                            }
                        }
                    }

                    SharedUserMessageActionPopup(
                        expanded = menuOpen,
                        timestamp = formatSharedMessageTimestamp(message.createdAtMillis),
                        onDismissRequest = { menuOpen = false },
                        onCopy = {
                            menuOpen = false
                            onCopy(message.text)
                        },
                        onSelectText = {
                            menuOpen = false
                            selectTextOpen = true
                        },
                        onEdit = {
                            menuOpen = false
                            onEdit()
                        },
                        onRetry = onRetryUserMessage?.let { retry ->
                            {
                                menuOpen = false
                                retry()
                            }
                        },
                    )

                    SharedMessageTextSelectionDialog(
                        expanded = selectTextOpen,
                        text = message.text,
                        onDismissRequest = { selectTextOpen = false },
                    )
                }
            }
        } else {
            val showAttachmentsAfterReply = com.zhousl.aether.data.ShowcaseCatalog.isSession(message.id)
            val finalTextBlockIndex = message.responseBlocks.indexOfLast { block ->
                block is SharedAssistantResponseBlock.Text && block.text.isNotBlank()
            }
            val orderedWorkBlocks = if (finalTextBlockIndex > 0) {
                message.responseBlocks.take(finalTextBlockIndex)
            } else {
                emptyList()
            }
            val thoughtDuration = metrics.thoughtDurationMillis
                ?: message.thoughtDurationMillis.takeIf { it > 0L }
            val completedWorkDuration = sharedCompletedWorkDurationMillis(
                startedAtMillis = message.createdAtMillis,
                completedAtMillis = message.completedAtMillis,
                fallbackDurationMillis = thoughtDuration,
            )
            val hasReasoningTrace = message.responseBlocks.any {
                it is SharedAssistantResponseBlock.Reasoning
            } || message.reasoningText.isNotBlank()
            val shouldFoldWork = shouldFoldSharedAssistantWork(
                isStreaming = message.isStreaming,
                text = message.text,
                hasOrderedWorkBlocks = orderedWorkBlocks.isNotEmpty(),
                hasFallbackReasoningOrTools = message.responseBlocks.isEmpty() &&
                    (message.reasoningText.isNotBlank() || message.tools.isNotEmpty()),
                hasAttachments = message.attachments.isNotEmpty(),
                thoughtDurationMillis = thoughtDuration,
            )
            val hasVisibleStreamingWork = message.isStreaming && (
                message.responseBlocks.hasVisibleSharedPendingWork() ||
                    (
                        message.responseBlocks.isEmpty() &&
                            (message.text.isNotBlank() || message.reasoningText.isNotBlank() || message.tools.isNotEmpty())
                        )
                )
            val streamingWorkStartedAtMillis = remember(message.id, message.createdAtMillis) {
                message.createdAtMillis
            }
            val streamingWorkDurationMillis by produceState(
                initialValue = sharedRunningWorkDurationMillis(streamingWorkStartedAtMillis),
                key1 = message.id,
                key2 = hasVisibleStreamingWork,
                key3 = streamingWorkStartedAtMillis,
            ) {
                if (!hasVisibleStreamingWork) return@produceState
                while (true) {
                    value = sharedRunningWorkDurationMillis(streamingWorkStartedAtMillis)
                    delay(1_000L)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasVisibleStreamingWork) {
                    SharedAgentWorkingStatusHeader(
                        title = stringResource(
                            Res.string.chat_working_for_duration,
                            formatSharedThoughtDuration(streamingWorkDurationMillis),
                        ),
                    )
                } else if (!message.isStreaming && !shouldFoldWork && !hasReasoningTrace && thoughtDuration != null) {
                    Text(
                        text = stringResource(
                            Res.string.chat_thought_for_duration,
                            formatSharedThoughtDuration(thoughtDuration),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
                if (shouldFoldWork) {
                    SharedAgentWorkSummaryDisclosure(
                        title = stringResource(
                            Res.string.chat_working_for_duration,
                            formatSharedThoughtDuration(completedWorkDuration),
                        ),
                        stateKey = "shared-message-work-${message.id}",
                    ) {
                        if (!hasReasoningTrace && thoughtDuration != null) {
                            Text(
                                text = stringResource(
                                    Res.string.chat_thought_for_duration,
                                    formatSharedThoughtDuration(thoughtDuration),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = AetherOnSurfaceVariant,
                            )
                        }
                        if (orderedWorkBlocks.isNotEmpty()) {
                            orderedWorkBlocks.forEach { block ->
                                SharedAssistantResponseBlockContent(
                                    block = block,
                                    message = message,
                                    metrics = metrics,
                                    runtime = runtime,
                                    onOpenLink = onOpenLink,
                                    autoExpandTools = false,
                                )
                            }
                        } else {
                            SharedFallbackAssistantWorkContent(message, metrics, onOpenLink)
                        }
                        if (!showAttachmentsAfterReply) {
                            message.attachments.forEach { attachment ->
                                SharedAttachmentCard(attachment, onOpenAttachment, runtime)
                            }
                        }
                    }
                } else if (!showAttachmentsAfterReply) {
                    message.attachments.forEach { attachment ->
                        SharedAttachmentCard(attachment, onOpenAttachment, runtime)
                    }
                }

                if (message.responseBlocks.isNotEmpty()) {
                    val visibleBlocks = when {
                        shouldFoldWork && finalTextBlockIndex >= 0 ->
                            message.responseBlocks.drop(finalTextBlockIndex)
                        shouldFoldWork -> emptyList()
                        else -> message.responseBlocks
                    }
                    visibleBlocks.forEachIndexed { index, block ->
                        SharedAssistantResponseBlockContent(
                            block = block,
                            message = message,
                            metrics = metrics,
                            runtime = runtime,
                            onOpenLink = onOpenLink,
                            autoExpandTools = message.isStreaming && index == visibleBlocks.lastIndex,
                        )
                    }
                    if (finalTextBlockIndex < 0 && message.text.isNotBlank()) {
                        SharedAssistantTextContent(
                            text = message.text,
                            isError = message.isError,
                            isStreaming = message.isStreaming,
                            runtime = runtime,
                            onOpenLink = onOpenLink,
                        )
                    }
                } else if (shouldFoldWork) {
                    SharedAssistantTextContent(
                        text = message.text,
                        isError = message.isError,
                        isStreaming = message.isStreaming,
                        runtime = runtime,
                        onOpenLink = onOpenLink,
                    )
                } else {
                    SharedFallbackAssistantWorkContent(message, metrics, onOpenLink)
                    if (message.text.isNotBlank()) {
                        SharedAssistantTextContent(
                            text = message.text,
                            isError = message.isError,
                            isStreaming = message.isStreaming,
                            runtime = runtime,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
                if (showAttachmentsAfterReply) {
                    message.attachments.forEach { attachment ->
                        SharedAttachmentCard(attachment, onOpenAttachment, runtime)
                    }
                }
                if (shouldShowSharedGenerationStatus(message)) {
                    SharedGenerationStatusCard(
                        text = message.status,
                        detail = message.statusDetail,
                        isRunning = message.isStreaming,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else if (shouldShowSharedThinkingFallback(message)) {
                    SharedThinkingIndicator()
                }
                if (
                    !message.isStreaming &&
                    !message.assistantActionsHidden
                ) {
                    SharedMessageActions(
                        text = message.text,
                        usage = message.usage,
                        canRetry = canRetry,
                        onCopy = onCopy,
                        onRetry = onRetry,
                        onDelete = onDelete,
                        sessionTotalTokens = sessionTotalTokens,
                        metrics = metrics,
                    )
                }
            }
        }
    }
}

internal fun shouldShowSharedThinkingFallback(message: SharedChatMessage): Boolean =
    message.isStreaming &&
        message.responseBlocks.isEmpty() &&
        message.reasoningText.isBlank() &&
        message.tools.isEmpty()

internal fun shouldShowSharedGenerationStatus(message: SharedChatMessage): Boolean =
    message.status.isNotBlank() &&
        (!message.status.equals("Thinking", ignoreCase = true) ||
            !message.responseBlocks.hasVisibleSharedPendingWork())

internal fun shouldFoldSharedAssistantWork(
    isStreaming: Boolean,
    text: String,
    hasOrderedWorkBlocks: Boolean,
    hasFallbackReasoningOrTools: Boolean,
    hasAttachments: Boolean,
    thoughtDurationMillis: Long?,
): Boolean = !isStreaming && text.isNotBlank() && (
    hasOrderedWorkBlocks ||
        hasFallbackReasoningOrTools ||
        hasAttachments ||
        thoughtDurationMillis != null
    )

@Composable
private fun SharedAssistantResponseBlockContent(
    block: SharedAssistantResponseBlock,
    message: SharedChatMessage,
    metrics: SharedMessageMetrics,
    runtime: MultiplatformLocalRuntime?,
    onOpenLink: (String) -> Unit,
    autoExpandTools: Boolean,
) {
    when (block) {
        is SharedAssistantResponseBlock.Reasoning -> SharedReasoningStatus(
            trace = block.trace,
            onOpenLink = onOpenLink,
        )
        is SharedAssistantResponseBlock.ToolGroup -> SharedToolInvocationGroup(
            blockId = block.id,
            tools = block.tools,
            autoExpand = autoExpandTools,
        )
        is SharedAssistantResponseBlock.Text -> SharedAssistantTextContent(
            text = block.text,
            isError = message.isError,
            isStreaming = message.isStreaming,
            runtime = runtime,
            onOpenLink = onOpenLink,
        )
        is SharedAssistantResponseBlock.Status -> SharedGenerationStatusCard(
            text = block.text,
            detail = block.detail,
            isRunning = block.text.startsWith("Reconnecting", ignoreCase = true),
        )
    }
}

@Composable
private fun SharedFallbackAssistantWorkContent(
    message: SharedChatMessage,
    metrics: SharedMessageMetrics,
    onOpenLink: (String) -> Unit,
) {
    if (message.reasoningText.isNotBlank()) {
        val completedAtMillis = message.createdAtMillis.takeIf { it > 0L }
        val startedAtMillis = completedAtMillis?.let { completedAt ->
            metrics.thoughtDurationMillis
                ?.takeIf { it > 0L }
                ?.let { duration -> (completedAt - duration).coerceAtLeast(1L) }
                ?: completedAt
        } ?: 0L
        SharedReasoningStatus(
            trace = SharedReasoningTrace(
                id = "${message.id}-reasoning",
                rawText = message.reasoningText,
                toolInvocations = message.tools,
                startedAtMillis = startedAtMillis,
                completedAtMillis = completedAtMillis.takeUnless { message.isStreaming && message.text.isBlank() },
            ),
            onOpenLink = onOpenLink,
        )
    }
    if (message.reasoningText.isBlank() && message.tools.isNotEmpty()) {
        SharedToolInvocationGroup(
            blockId = "${message.id}-tools",
            tools = message.tools,
            autoExpand = message.isStreaming,
        )
    }
}

@Composable
private fun SharedAssistantTextContent(
    text: String,
    isError: Boolean,
    isStreaming: Boolean,
    runtime: MultiplatformLocalRuntime?,
    onOpenLink: (String) -> Unit,
) {
    if (text.isBlank()) return
    if (isError) {
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    } else {
        SelectionContainer {
            if (isStreaming) {
                SharedStreamingMarkdownContent(
                    content = text,
                    runtime = runtime,
                    onOpenLink = onOpenLink,
                )
            } else {
                SharedMarkdownContent(
                    content = text,
                    runtime = runtime,
                    onOpenLink = onOpenLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SharedStreamingMarkdownContent(
    content: String,
    runtime: MultiplatformLocalRuntime?,
    onOpenLink: (String) -> Unit,
) {
    var trackedSource by remember { mutableStateOf("") }
    var activeFadeRange by remember { mutableStateOf<IntRange?>(null) }
    var activeFadeDurationMillis by remember {
        mutableStateOf(SharedStreamingChunkFadeDurationMillis)
    }
    val fadeProgress = remember { Animatable(1f) }

    LaunchedEffect(content) {
        if (content.isBlank()) {
            trackedSource = ""
            activeFadeRange = null
            fadeProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!content.startsWith(trackedSource)) {
            trackedSource = ""
            activeFadeRange = null
            fadeProgress.snapTo(1f)
        }
        val deltaStart = trackedSource.length
        val delta = content.removePrefix(trackedSource)
        trackedSource = content
        if (delta.isEmpty()) return@LaunchedEffect
        activeFadeRange = deltaStart until content.length
        activeFadeDurationMillis = if (deltaStart == 0) {
            SharedStreamingInitialChunkFadeDurationMillis
        } else {
            SharedStreamingChunkFadeDurationMillis
        }
    }

    LaunchedEffect(activeFadeRange, activeFadeDurationMillis) {
        val range = activeFadeRange ?: return@LaunchedEffect
        if (range.isEmpty()) {
            fadeProgress.snapTo(1f)
            return@LaunchedEffect
        }
        fadeProgress.snapTo(0f)
        fadeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = activeFadeDurationMillis,
                easing = LinearEasing,
            ),
        )
    }

    val fadeSpan = remember(content, activeFadeRange, fadeProgress.value) {
        activeFadeRange?.let { range ->
            val start = range.first.coerceIn(0, content.length)
            val end = (range.last + 1).coerceIn(start, content.length)
            if (end <= start) null else SharedMarkdownFadeSpan(
                sourceRange = start until end,
                alpha = fadeProgress.value.coerceIn(0f, 1f),
            )
        }
    }
    SharedMarkdownContent(
        content = content,
        runtime = runtime,
        onOpenLink = onOpenLink,
        modifier = Modifier.fillMaxWidth(),
        fadeSpan = fadeSpan,
    )
}

@Composable
private fun SharedAgentWorkSummaryDisclosure(
    title: String,
    stateKey: String,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
        label = "shared_agent_work_arrow_rotation",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = stringResource(
                    if (expanded) Res.string.agent_work_collapse else Res.string.agent_work_expand,
                ),
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(SharedToolTransitionDurationMillis - 90, delayMillis = 40)),
            exit = shrinkVertically(tween(260, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
                fadeOut(tween(180, easing = FastOutLinearInEasing)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = SharedToolGroupIndent)
                    .animateContentSize(tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SharedAgentWorkingStatusHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(AetherOutlineSoft.copy(alpha = 0.62f)),
        )
    }
}

@Composable
private fun SharedAttachmentCard(
    attachment: SharedChatAttachment,
    onOpenAttachment: ((SharedChatAttachment) -> Unit)?,
    runtime: MultiplatformLocalRuntime?,
) {
    if (attachment.mimeType.startsWith("image/", ignoreCase = true)) {
        SharedImageAttachmentCard(attachment, onOpenAttachment, runtime)
        return
    }
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(22.dp))
            .background(AetherSurface)
            .clickable(enabled = onOpenAttachment != null) { onOpenAttachment?.invoke(attachment) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(AetherSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = AetherOnSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.name,
                color = AetherOnSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sharedAttachmentMetaLabel(
                    stringResource(Res.string.attachment_type_file),
                    attachment,
                ),
                color = AetherOnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SharedImageAttachmentCard(
    attachment: SharedChatAttachment,
    onOpenAttachment: ((SharedChatAttachment) -> Unit)?,
    runtime: MultiplatformLocalRuntime?,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = attachment.workspacePath,
        key2 = runtime,
    ) {
        value = runCatching {
            runtime?.let { readSharedAttachmentBytes(attachment, it).decodeToImageBitmap() }
        }.getOrNull()
    }
    Box(
        modifier = Modifier.widthIn(max = 300.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .clickable(enabled = onOpenAttachment != null) { onOpenAttachment?.invoke(attachment) },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap),
                contentDescription = attachment.name,
                modifier = Modifier.fillMaxWidth().height(210.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(210.dp).background(AetherSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

private sealed interface SharedAttachmentPreviewState {
    data class Image(val bitmap: ImageBitmap) : SharedAttachmentPreviewState
    data object ImageUnavailable : SharedAttachmentPreviewState
    data class Text(val text: String, val isTruncated: Boolean) : SharedAttachmentPreviewState
    data object Unavailable : SharedAttachmentPreviewState
}

@Composable
internal fun SharedAttachmentPreviewDialog(
    attachment: SharedChatAttachment,
    runtime: MultiplatformLocalRuntime,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val isImage = attachment.mimeType.startsWith("image/", ignoreCase = true)
    val preview by produceState<SharedAttachmentPreviewState>(
        initialValue = if (isImage) {
            SharedAttachmentPreviewState.ImageUnavailable
        } else {
            SharedAttachmentPreviewState.Unavailable
        },
        key1 = attachment.workspacePath,
        key2 = runtime,
    ) {
        value = try {
            if (isImage) {
                val bytes = readSharedAttachmentBytes(attachment, runtime)
                SharedAttachmentPreviewState.Image(bytes.decodeToImageBitmap())
            } else if (isLikelySharedTextAttachment(attachment)) {
                val bytes = runtime.fileSystem.readPrefix(
                    attachment.workspacePath,
                    SharedAttachmentTextPreviewByteLimit + 1L,
                )
                val decoded = decodeSharedTextAttachment(bytes)
                if (decoded == null) {
                    SharedAttachmentPreviewState.Unavailable
                } else {
                    SharedAttachmentPreviewState.Text(
                        text = decoded.take(SharedAttachmentTextPreviewCharacterLimit),
                        isTruncated = attachment.sizeBytes > SharedAttachmentTextPreviewByteLimit ||
                            bytes.size > SharedAttachmentTextPreviewByteLimit ||
                            decoded.length > SharedAttachmentTextPreviewCharacterLimit,
                    )
                }
            } else {
                SharedAttachmentPreviewState.Unavailable
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            if (isImage) SharedAttachmentPreviewState.ImageUnavailable else SharedAttachmentPreviewState.Unavailable
        }
    }
    val kindLabel = stringResource(
        if (isImage) Res.string.attachment_type_photo else Res.string.attachment_type_file,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp)
                .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(30.dp))
                .background(AetherSurface.copy(alpha = 0.98f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(AetherSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = AetherOnSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sharedAttachmentMetaLabel(kindLabel, attachment),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
                SharedIconOnlyAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.attachment_close_preview),
                    onClick = onDismiss,
                )
            }

            when (val current = preview) {
                is SharedAttachmentPreviewState.Image -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp)
                        .clip(RoundedCornerShape(24.dp)).background(AetherSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = current.bitmap,
                        contentDescription = attachment.name,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                }
                SharedAttachmentPreviewState.ImageUnavailable -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp)
                        .clip(RoundedCornerShape(24.dp)).background(AetherSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = AetherOnSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
                is SharedAttachmentPreviewState.Text -> Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(AetherSurfaceHigh).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = current.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AetherOnSurface,
                            )
                        }
                    }
                    if (current.isTruncated) {
                        Text(
                            text = stringResource(Res.string.attachment_preview_truncated),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                    }
                }
                SharedAttachmentPreviewState.Unavailable -> Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(AetherSurfaceHigh).padding(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.attachment_file_preview_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SharedActionIconLabel(
                    icon = Icons.Rounded.Download,
                    label = stringResource(Res.string.common_save),
                    enabled = true,
                    onClick = onSave,
                )
            }
        }
    }
}

internal suspend fun readSharedAttachmentBytes(
    attachment: SharedChatAttachment,
    runtime: MultiplatformLocalRuntime,
): ByteArray = when {
    attachment.previewBytes != null -> attachment.previewBytes
    attachment.inlineBase64.isNotBlank() -> Base64.decode(attachment.inlineBase64)
    attachment.workspacePath.isNotBlank() -> runtime.fileSystem.read(attachment.workspacePath)
    else -> error("Attachment has neither a workspace path nor inline data.")
}

private const val SharedAttachmentTextPreviewByteLimit = 64 * 1024
private const val SharedAttachmentTextPreviewCharacterLimit = 12_000

internal fun decodeSharedTextAttachment(bytes: ByteArray): String? {
    if (bytes.take(512).any { it == 0.toByte() }) return null
    val decoded = bytes.decodeToString(throwOnInvalidSequence = false)
    return decoded.takeIf { text -> text.count { it == '\uFFFD' } <= 12 }
}

internal fun isLikelySharedTextAttachment(attachment: SharedChatAttachment): Boolean {
    val mimeType = attachment.mimeType.lowercase()
    if (
        mimeType.startsWith("text/") ||
        listOf("json", "xml", "yaml", "csv", "javascript").any(mimeType::contains)
    ) {
        return true
    }
    return attachment.name.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "json", "xml", "yaml", "yml", "csv", "log", "kt", "java", "kts",
        "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "c", "cc", "cpp", "h", "hpp",
        "html", "css", "sh", "bash", "zsh", "toml", "ini", "conf",
    )
}

@Composable
private fun SharedMessageTextSelectionDialog(
    expanded: Boolean,
    text: String,
    onDismissRequest: () -> Unit,
) {
    if (!expanded) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 520.dp)
                .shadow(22.dp, RoundedCornerShape(28.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(28.dp))
                .background(AetherSurface)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.common_select_text),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                )
            }
        }
    }
}

@Composable
private fun SharedUserMessageActionPopup(
    expanded: Boolean,
    timestamp: String,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val density = LocalDensity.current
    SharedAnimatedPopupHost(visible = expanded) { visibility ->
        Popup(
            alignment = Alignment.TopEnd,
            offset = with(density) { IntOffset(0, 30.dp.roundToPx()) },
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = androidx.compose.animation.fadeIn(
                    tween(160, easing = SharedStatisticsPopupEasing),
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(220, easing = SharedStatisticsPopupEasing),
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = tween(240, easing = SharedStatisticsPopupEasing),
                    initialOffsetY = { -it / 10 },
                ),
                exit = androidx.compose.animation.fadeOut(
                    tween(120, easing = SharedStatisticsPopupEasing),
                ) + androidx.compose.animation.scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(160, easing = SharedStatisticsPopupEasing),
                ) + androidx.compose.animation.slideOutVertically(
                    animationSpec = tween(180, easing = SharedStatisticsPopupEasing),
                    targetOffsetY = { -it / 12 },
                ),
            ) {
                Column(
                    modifier = Modifier.width(228.dp)
                        .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                        .clip(RoundedCornerShape(30.dp))
                        .background(AetherSurface)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (timestamp.isNotBlank()) {
                        Text(
                            timestamp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    SharedUserMessageActionRow(Icons.Rounded.ContentCopy, stringResource(Res.string.common_copy), onCopy)
                    SharedUserMessageActionRow(Icons.Rounded.Description, stringResource(Res.string.common_select_text), onSelectText)
                    SharedUserMessageActionRow(Icons.Rounded.Edit, stringResource(Res.string.common_edit_message), onEdit)
                    onRetry?.let { retry ->
                        SharedUserMessageActionRow(Icons.Rounded.Refresh, stringResource(Res.string.common_retry), retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedUserMessageActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AetherOnSurface, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
    }
}

@Composable
private fun SharedBranchStepButton(enabled: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.32f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedReasoningStatus(
    trace: SharedReasoningTrace,
    onOpenLink: (String) -> Unit,
) {
    var sheetVisible by remember(trace.id) { mutableStateOf(false) }
    val interactionSource = remember(trace.id) { MutableInteractionSource() }
    val latestDetail = remember(trace.latestStatusText, trace.chunks) {
        trace.latestStatusText.ifBlank {
            trace.chunks.lastOrNull { it.detail.isNotBlank() || it.title.isNotBlank() }
                ?.let { chunk -> chunk.detail.ifBlank { chunk.title } }
                .orEmpty()
        }
    }
    val completed = trace.completedAtMillis != null
    val statusText = if (completed) {
        val duration = sharedReasoningTraceDurationLabel(trace)
        if (trace.toolInvocations.isNotEmpty()) stringResource(
            Res.string.chat_thought_for_duration_and_tools,
            duration,
            trace.toolInvocations.size,
        ) else stringResource(
            Res.string.chat_thought_for_duration, duration,
        )
    } else {
        latestDetail
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { sheetVisible = true },
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            completed -> Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
            statusText.isNotBlank() -> SharedReasoningTypewriterText(statusText)
            else -> SharedReasoningShimmerText(stringResource(Res.string.chat_thinking))
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            containerColor = AetherSurface,
            contentColor = AetherOnSurface,
            dragHandle = {
                Box(
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp).width(56.dp).height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AetherOnSurfaceVariant.copy(alpha = 0.16f)),
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (trace.hasTimelineContent || trace.completedAtMillis != null) {
                    SharedReasoningTimeline(trace, onOpenLink)
                } else {
                    SharedRawReasoningPanel(trace.rawText)
                }
            }
        }
    }
}

@Composable
internal fun SharedReasoningShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    travelDurationMillis: Int = 1_800,
    pauseDurationMillis: Int = 1_000,
) {
    if (LocalReduceMotion.current) {
        Text(text = text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
        return
    }
    val totalDurationMillis = travelDurationMillis + pauseDurationMillis
    val travelDistance = (280f + text.length * 18f).coerceIn(280f, 760f)
    val shimmerOffset by rememberInfiniteTransition(label = "shared_reasoning_status_shimmer").animateFloat(
        initialValue = -travelDistance,
        targetValue = travelDistance,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = totalDurationMillis
                travelDistance at travelDurationMillis using LinearEasing
                travelDistance at totalDurationMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "shared_reasoning_status_shimmer_offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            AetherOnSurfaceVariant.copy(alpha = 0.42f),
            AetherOnSurface.copy(alpha = 0.96f),
            AetherOnSurfaceVariant.copy(alpha = 0.42f),
        ),
        start = Offset(shimmerOffset - 180f, 0f),
        end = Offset(shimmerOffset + 180f, 0f),
    )
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium.copy(brush = brush))
}

@Composable
private fun SharedRawReasoningPanel(rawText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.chat_raw_reasoning),
            style = MaterialTheme.typography.labelMedium,
            color = AetherOnSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = rawText.ifBlank { stringResource(Res.string.chat_waiting_for_reasoning) }
                    .let { if (it.length <= 12_000) it else it.take(12_000).trimEnd() + "\n..." },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(AetherSurfaceHigh).padding(14.dp),
                color = AetherOnSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal sealed interface SharedReasoningTimelineItem {
    val sortOrder: Long
    val fallbackOrder: Int

    data class Summary(
        val chunk: SharedReasoningSummaryChunk,
        override val sortOrder: Long,
        override val fallbackOrder: Int,
    ) : SharedReasoningTimelineItem

    data class Tool(
        val tool: SharedChatToolInvocation,
        override val sortOrder: Long,
        override val fallbackOrder: Int,
    ) : SharedReasoningTimelineItem
}

internal fun sharedReasoningTimelineItems(trace: SharedReasoningTrace): List<SharedReasoningTimelineItem> {
    val visibleChunks = trace.chunks.filter {
        it.title.isNotBlank() || it.detail.isNotBlank() || it.isPending || it.rawText.isNotBlank()
    }
    return buildList {
        visibleChunks.forEachIndexed { index, chunk ->
            add(
                SharedReasoningTimelineItem.Summary(
                    chunk = chunk,
                    sortOrder = chunk.timelineOrder.takeIf { it > 0L }
                        ?: chunk.createdAtMillis.takeIf { it > 0L }
                        ?: Long.MAX_VALUE,
                    fallbackOrder = index,
                )
            )
        }
        trace.toolInvocations.forEachIndexed { index, tool ->
            add(
                SharedReasoningTimelineItem.Tool(
                    tool = tool,
                    sortOrder = tool.timelineOrder.takeIf { it > 0L }
                        ?: tool.startedAtMillis.takeIf { it > 0L }
                        ?: Long.MAX_VALUE,
                    fallbackOrder = visibleChunks.size + index,
                )
            )
        }
    }.sortedWith(compareBy<SharedReasoningTimelineItem> { it.sortOrder }.thenBy { it.fallbackOrder })
}

@Composable
private fun SharedReasoningTimeline(
    trace: SharedReasoningTrace,
    onOpenLink: (String) -> Unit,
) {
    val items = remember(trace.chunks, trace.toolInvocations) { sharedReasoningTimelineItems(trace) }
    val hasDone = trace.completedAtMillis != null
    val summarizingTitle = stringResource(Res.string.chat_summarizing_reasoning)
    val preparingDetail = stringResource(Res.string.chat_preparing_reasoning_summary)
    Column {
        items.forEachIndexed { index, item ->
            val isLast = !hasDone && index == items.lastIndex
            when (item) {
                is SharedReasoningTimelineItem.Summary -> SharedReasoningTimelineRow(
                    title = item.chunk.title.ifBlank { summarizingTitle },
                    detail = item.chunk.detail.ifBlank { preparingDetail },
                    isLast = isLast,
                )
                is SharedReasoningTimelineItem.Tool -> SharedReasoningTimelineToolRow(
                    tool = item.tool,
                    isLast = isLast,
                    onOpenLink = onOpenLink,
                )
            }
        }
        if (hasDone) SharedReasoningTimelineDoneRow(trace)
    }
}

@Composable
private fun SharedReasoningTimelineRow(title: String, detail: String, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharedReasoningTimelineGlyph(isLast = isLast)
        Column(
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = AetherOnSurface,
            )
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedReasoningTimelineToolRow(
    tool: SharedChatToolInvocation,
    isLast: Boolean,
    onOpenLink: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharedReasoningTimelineGlyph(icon = toolIcon(tool.name), isLast = isLast)
        Column(
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SharedToolInvocationCard(tool, topPadding = 0.dp)
            SharedToolWebSourceLink(tool, onOpenLink)
        }
    }
}

@Composable
private fun SharedToolWebSourceLink(
    tool: SharedChatToolInvocation,
    onOpenLink: (String) -> Unit,
) {
    val metadata = remember(tool.name, tool.argumentsJson, tool.outputJson) {
        sharedWebSourceMetadata(tool.name, tool.argumentsJson, tool.outputJson)
    } ?: return
    val faviconPainter = rememberAsyncImagePainter(metadata.faviconUrl.orEmpty())
    val faviconState by faviconPainter.state.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(AetherSurfaceHigh.copy(alpha = 0.72f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onOpenLink(metadata.url) },
            )
            .padding(start = 10.dp, top = 7.dp, end = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (faviconState is AsyncImagePainter.State.Success) {
            Image(
                painter = faviconPainter,
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            metadata.domain,
            color = AetherOnSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal data class SharedWebSourceMetadata(
    val domain: String,
    val url: String,
    val faviconUrl: String?,
)

internal fun sharedWebSourceMetadata(
    toolName: String,
    argumentsJson: String,
    outputJson: String,
): SharedWebSourceMetadata? {
    val arguments = parseSharedJsonObject(argumentsJson)
    val output = parseSharedJsonObject(outputJson)
    return when (toolName.lowercase()) {
        "fetch_web_url" -> {
            val sourceUrl = output.sharedString("final_url")
                .ifBlank { output.sharedString("request_url") }
                .ifBlank { arguments.sharedString("url") }
            sharedWebSourceMetadataFromUrl(sourceUrl)
        }
        "tavily_search" -> sharedTavilySourceMetadata(arguments, output)
        else -> null
    }
}

private fun sharedTavilySourceMetadata(
    arguments: JsonObject?,
    output: JsonObject?,
): SharedWebSourceMetadata {
    val result = (output?.get("results") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.sharedString("url").isNotBlank() || it.sharedString("favicon").isNotBlank() }
    val resultUrl = result.sharedString("url")
    val domain = sharedNormalizedDomain(resultUrl)
        .ifBlank { sharedFirstSearchArgumentDomain(arguments) }
        .ifBlank { "tavily.com" }
    val url = sharedNormalizedHttpUrl(resultUrl)
        .ifBlank { sharedNormalizedHttpUrl(domain) }
        .ifBlank { "https://tavily.com" }
    val faviconUrl = result.sharedString("favicon")
        .takeIf {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                !it.endsWith(".svg", ignoreCase = true)
        }
        ?: sharedFaviconUrlForDomain(domain)
    return SharedWebSourceMetadata(domain, url, faviconUrl)
}

private fun sharedWebSourceMetadataFromUrl(url: String): SharedWebSourceMetadata? {
    val domain = sharedNormalizedDomain(url)
    if (domain.isBlank()) return null
    return SharedWebSourceMetadata(
        domain = domain,
        url = sharedNormalizedHttpUrl(url).ifBlank { "https://$domain" },
        faviconUrl = sharedFaviconUrlForDomain(domain),
    )
}

private fun sharedFirstSearchArgumentDomain(arguments: JsonObject?): String {
    val includeDomains = (arguments?.get("include_domains") as? JsonArray)
        ?: (arguments?.get("includeDomains") as? JsonArray)
    includeDomains?.forEach { value ->
        val domain = sharedNormalizedDomain((value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty())
        if (domain.isNotBlank()) return domain
    }
    val query = arguments.sharedString("query")
    val domainPattern = Regex("""(?:site:)?([A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,})(?:/[^\s]*)?""")
    return domainPattern.find(query)?.groupValues?.getOrNull(1)
        ?.let(::sharedNormalizedDomain).orEmpty()
}

private fun sharedNormalizedDomain(urlOrDomain: String): String {
    val trimmed = urlOrDomain.trim()
    if (trimmed.isBlank()) return ""
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val host = runCatching { Url(candidate).host }.getOrNull().orEmpty()
        .ifBlank {
            trimmed.substringAfter("://", trimmed).substringBefore('/')
                .substringBefore('?').substringBefore('#').substringBefore(':')
        }
        .trim('.')
    return host.removePrefix("www.").lowercase()
}

private fun sharedNormalizedHttpUrl(urlOrDomain: String): String {
    val trimmed = urlOrDomain.trim()
    if (trimmed.isBlank()) return ""
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return runCatching { Url(candidate) }.getOrNull()?.takeIf {
        (it.protocol.name == "http" || it.protocol.name == "https") && it.host.isNotBlank()
    }?.toString().orEmpty()
}

private fun sharedFaviconUrlForDomain(domain: String): String =
    "https://www.google.com/s2/favicons?domain=${sharedEncodeUrlQueryComponent(domain)}&sz=64"

private fun sharedEncodeUrlQueryComponent(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val number = byte.toInt() and 0xff
        val character = number.toChar()
        if (number in 'a'.code..'z'.code || number in 'A'.code..'Z'.code ||
            number in '0'.code..'9'.code || character == '-' || character == '_' ||
            character == '.' || character == '~'
        ) {
            append(character)
        } else {
            append('%')
            append("0123456789ABCDEF"[number ushr 4])
            append("0123456789ABCDEF"[number and 0x0f])
        }
    }
}

private fun parseSharedJsonObject(value: String): JsonObject? = runCatching {
    Json.parseToJsonElement(value).jsonObject
}.getOrNull()

private fun JsonObject?.sharedString(key: String): String =
    this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject?.sharedInt(key: String): Int? =
    (this?.get(key) as? JsonPrimitive)?.intOrNull

private fun JsonObject?.sharedBoolean(key: String): Boolean =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull ?: false

internal data class SharedToolInvocationDetail(
    val command: String,
    val result: String?,
)

internal fun formatSharedToolInvocationDetail(
    tool: SharedChatToolInvocation,
    noOutputLabel: String,
    contentTruncatedLabel: String,
    exitCodeLabel: (Int) -> String,
): SharedToolInvocationDetail {
    val arguments = parseSharedJsonObject(tool.argumentsJson)
    val output = parseSharedJsonObject(tool.outputJson)
    val command = output.sharedString("command").trim()
        .ifBlank { summarizeSharedToolInvocationCommand(tool.name, arguments) }
        .ifBlank { tool.name }

    if (tool.isRunning && output == null) {
        return SharedToolInvocationDetail(command = command, result = null)
    }

    val result = when {
        output == null -> tool.outputJson.trim().ifBlank { noOutputLabel }
        tool.name.startsWith("aether_", ignoreCase = true) -> {
            tool.outputJson.trim().ifBlank { noOutputLabel }
        }
        tool.name.equals("fetch_web_url", ignoreCase = true) -> {
            formatSharedFetchWebUrlResult(output, noOutputLabel, contentTruncatedLabel)
        }
        output.sharedString("stdout").trim().isNotBlank() &&
            output.sharedString("stderr").trim().isNotBlank() -> buildString {
                appendLine(output.sharedString("stdout").trim())
                appendLine()
                append("stderr: ")
                append(output.sharedString("stderr").trim())
            }
        output.sharedString("stdout").trim().isNotBlank() -> output.sharedString("stdout").trim()
        output.sharedString("stderr").trim().isNotBlank() -> output.sharedString("stderr").trim()
        output.sharedString("errmsg").trim().isNotBlank() -> output.sharedString("errmsg").trim()
        output.sharedString("hint").trim().isNotBlank() -> output.sharedString("hint").trim()
        output.containsKey("exit_code") && (output.sharedInt("exit_code") ?: 0) != 0 -> {
            exitCodeLabel(output.sharedInt("exit_code") ?: 0)
        }
        else -> noOutputLabel
    }
    return SharedToolInvocationDetail(command = command, result = result)
}

private fun formatSharedFetchWebUrlResult(
    output: JsonObject,
    noOutputLabel: String,
    contentTruncatedLabel: String,
): String {
    val markdown = output.sharedString("markdown").trim()
    if (markdown.isNotBlank()) {
        return buildString {
            append(markdown)
            if (output.sharedBoolean("truncated")) {
                appendLine()
                appendLine()
                append(contentTruncatedLabel)
            }
        }.trim()
    }
    return output.sharedString("stdout").trim().ifBlank { noOutputLabel }
}

internal fun summarizeSharedToolInvocationCommand(
    toolName: String,
    arguments: JsonObject?,
): String {
    if (arguments == null) return toolName
    return when (toolName.lowercase()) {
        "bash" -> arguments.sharedString("command").trim()
        "read" -> buildString {
            append("read ")
            append(arguments.sharedString("path").trim())
            val offset = arguments.sharedInt("offset") ?: 0
            val limit = arguments.sharedInt("limit")
            if (offset > 0 || limit != null) {
                append(" (offset=")
                append(offset)
                if (limit != null) append(", limit=").append(limit)
                append(')')
            }
        }
        "edit" -> {
            val path = arguments.sharedString("path").trim()
            val editCount = (arguments["edits"] as? JsonArray)?.size
                ?: if (arguments.containsKey("oldText") || arguments.containsKey("newText")) 1 else 0
            "edit $path${if (editCount > 0) " ($editCount edit${if (editCount == 1) "" else "s"})" else ""}"
        }
        "write" -> "write ${arguments.sharedString("path").trim()}"
        "grep" -> "grep ${arguments.sharedString("pattern").trim()} in ${arguments.sharedString("path").trim()}"
        "find" -> "find ${arguments.sharedString("pattern").trim()} in ${arguments.sharedString("path").trim()}"
        "ls" -> "ls ${arguments.sharedString("path").trim()}"
        "analyze_image" -> buildString {
            append("analyze_image ")
            append(arguments.sharedString("path").trim())
            val prompt = arguments.sharedString("prompt").trim()
            if (prompt.isNotBlank()) {
                append(" (")
                append(prompt.take(48))
                if (prompt.length > 48) append("...")
                append(')')
            }
        }
        "tavily_search" -> "search ${arguments.sharedString("query").trim()}"
        "fetch_web_url" -> "fetch ${arguments.sharedString("url").trim()}"
        "aether_config_get",
        "aether_config_set",
        "aether_skill_manage",
        "aether_mcp_manage",
        "aether_developer_manage" -> summarizeSharedAetherToolCommand(toolName, arguments)
        else -> toolName
    }.trim()
}

private fun summarizeSharedAetherToolCommand(toolName: String, arguments: JsonObject): String {
    val action = arguments.sharedString("action").trim()
    return when (toolName.lowercase()) {
        "aether_config_get" -> {
            "aether_config_get categories=${summarizeSharedAetherCategories(arguments).ifBlank { "all" }}"
        }
        "aether_config_set" -> {
            val settings = arguments["settings"] as? JsonObject
            val fields = settings?.keys?.joinToString(",").orEmpty()
            "aether_config_set category=${arguments.sharedString("category").trim()} ${if (fields.isBlank()) "" else "fields=$fields"}".trim()
        }
        "aether_skill_manage" -> buildString {
            append("aether_skill_manage action=")
            append(action.ifBlank { "list" })
            appendSharedAetherKeyValue(arguments, "skill_id", "skillId")
            appendSharedAetherKeyValue(arguments, "url")
            if (arguments.containsKey("enabled")) append(" enabled=").append(arguments.sharedBoolean("enabled"))
        }.trim()
        "aether_mcp_manage" -> buildString {
            append("aether_mcp_manage action=")
            append(action.ifBlank { "list" })
            appendSharedAetherKeyValue(arguments, "server_id", "serverId")
            appendSharedAetherKeyValue(arguments, "display_name", "displayName")
            appendSharedAetherKeyValue(arguments, "url")
            appendSharedAetherKeyValue(arguments, "command")
            if (arguments.containsKey("enabled")) append(" enabled=").append(arguments.sharedBoolean("enabled"))
        }.trim()
        "aether_developer_manage" -> buildString {
            append("aether_developer_manage action=")
            append(action.ifBlank { "read_diagnostics" })
            appendSharedAetherKeyValue(arguments, "include")
            appendSharedAetherKeyValue(arguments, "max_chars", "maxChars")
        }.trim()
        else -> toolName
    }
}

private fun summarizeSharedAetherCategories(arguments: JsonObject?): String =
    (arguments?.get("categories") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null } }
        ?.joinToString(",")
        .orEmpty()

private fun StringBuilder.appendSharedAetherKeyValue(
    arguments: JsonObject,
    primary: String,
    secondary: String = "",
) {
    val value = arguments.sharedString(primary).ifBlank {
        if (secondary.isBlank()) "" else arguments.sharedString(secondary)
    }.trim()
    if (value.isNotBlank()) {
        append(' ').append(primary).append('=').append(value.take(96))
        if (value.length > 96) append("...")
    }
}

@Composable
private fun SharedReasoningTimelineDoneRow(trace: SharedReasoningTrace) {
    val duration = sharedReasoningTraceDurationLabel(trace)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharedReasoningTimelineGlyph(icon = Icons.Rounded.CheckCircle, isLast = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.chat_thought_for_duration, duration),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurface,
            )
            Text(
                stringResource(Res.string.common_done),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

private fun sharedReasoningTraceDurationLabel(trace: SharedReasoningTrace): String {
    val startedAt = trace.startedAtMillis.takeIf { it > 0L } ?: return "0s"
    val completedAt = trace.completedAtMillis ?: startedAt
    return formatSharedThoughtDuration((completedAt - startedAt).coerceAtLeast(1L))
}

@Composable
private fun SharedReasoningTimelineGlyph(
    icon: ImageVector? = null,
    isLast: Boolean,
) {
    Box(
        modifier = Modifier.width(SharedTimelineGlyphWidth).fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!isLast) {
            Box(
                Modifier
                    .padding(
                        top = SharedTimelineIconSize + SharedTimelineLineTopGap,
                        bottom = SharedTimelineLineBottomGap,
                    )
                    .width(SharedTimelineLineWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(AetherOnSurfaceVariant.copy(alpha = 0.12f)),
            )
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(SharedTimelineIconSize),
            )
        } else {
            Box(
                Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                    .background(AetherOnSurfaceVariant),
            )
        }
    }
}

@Composable
private fun SharedReasoningTypewriterText(text: String) {
    var rendered by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        if (!text.startsWith(rendered)) rendered = ""
        while (rendered.length < text.length) {
            rendered = text.substring(0, (rendered.length + 3).coerceAtMost(text.length))
            delay(18)
        }
    }
    Text(
        rendered.ifBlank { text },
        style = MaterialTheme.typography.bodyMedium,
        color = AetherOnSurfaceVariant,
    )
}

@Composable
private fun SharedToolInvocationGroup(
    blockId: String,
    tools: List<SharedChatToolInvocation>,
    autoExpand: Boolean,
) {
    if (tools.size < SharedToolInvocationCollapseThreshold) {
        Column(modifier = Modifier.fillMaxWidth()) {
            tools.forEach { tool ->
                SharedToolInvocationAnimatedCard(tool)
            }
        }
        return
    }
    var headerVisible by rememberSaveable(blockId) { mutableStateOf(!autoExpand) }
    var expanded by rememberSaveable(blockId) { mutableStateOf(autoExpand) }
    var lastAutoExpanded by rememberSaveable(blockId) { mutableStateOf(autoExpand) }
    LaunchedEffect(autoExpand) {
        if (lastAutoExpanded != autoExpand) {
            if (autoExpand) {
                headerVisible = false
                expanded = true
            } else {
                headerVisible = true
                expanded = true
                delay(SharedToolGroupCollapseStageDelayMillis)
                expanded = false
            }
            lastAutoExpanded = autoExpand
        }
    }
    val isRunning = tools.any { it.isRunning }
    val childIndent by animateDpAsState(
        targetValue = if (headerVisible) SharedToolGroupIndent else 0.dp,
        animationSpec = tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
        label = "shared_tool_group_indent",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
        label = "shared_tool_group_arrow_rotation",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(
                tween(
                    SharedToolTransitionDurationMillis - 100,
                    easing = SharedStatisticsPopupEasing,
                ),
            ) + expandVertically(
                tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                shrinkVertically(tween(220, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top),
        ) {
            val interactionSource = remember(blockId) { MutableInteractionSource() }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val groupTitle = stringResource(
                    if (isRunning) {
                        Res.string.tool_invocation_group_executing
                    } else {
                        Res.string.tool_invocation_group_executed
                    },
                    tools.size,
                )
                if (isRunning) {
                    SharedReasoningShimmerText(
                        text = groupTitle,
                        travelDurationMillis = 2_600,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        groupTitle,
                        modifier = Modifier.weight(1f),
                        color = AetherOnSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = stringResource(
                        if (expanded) Res.string.tool_invocation_collapse_tools
                        else Res.string.tool_invocation_expand_tools,
                    ),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(SharedToolTransitionDurationMillis - 90, delayMillis = 40)),
            exit = shrinkVertically(tween(260, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
                fadeOut(tween(180, easing = FastOutLinearInEasing)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = childIndent),
            ) {
                tools.forEach { tool ->
                    SharedToolInvocationAnimatedCard(tool, topPadding = 4.dp)
                }
            }
        }
    }
}

@Composable
private fun SharedToolInvocationAnimatedCard(
    tool: SharedChatToolInvocation,
    topPadding: Dp = 6.dp,
) {
    var visible by rememberSaveable(tool.id) { mutableStateOf(false) }
    LaunchedEffect(tool.id) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(
            tween(SharedToolTransitionDurationMillis - 90, delayMillis = 30),
        ),
    ) {
        SharedToolInvocationCard(tool, topPadding)
    }
}

@Composable
private fun SharedToolInvocationCard(
    tool: SharedChatToolInvocation,
    topPadding: Dp = 6.dp,
) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val noOutputLabel = stringResource(Res.string.chat_no_output)
    val contentTruncatedLabel = stringResource(Res.string.chat_content_truncated)
    val exitCode = remember(tool.outputJson) {
        parseSharedJsonObject(tool.outputJson).sharedInt("exit_code") ?: 0
    }
    val exitCodeText = stringResource(Res.string.tool_invocation_exit_code, exitCode)
    val detail = remember(
        tool.name,
        tool.isRunning,
        tool.argumentsJson,
        tool.outputJson,
        noOutputLabel,
        contentTruncatedLabel,
        exitCodeText,
    ) {
        formatSharedToolInvocationDetail(
            tool = tool,
            noOutputLabel = noOutputLabel,
            contentTruncatedLabel = contentTruncatedLabel,
            exitCodeLabel = { exitCodeText },
        )
    }
    val interactionSource = remember(tool.id) { MutableInteractionSource() }
    LaunchedEffect(
        tool.id,
        tool.isRunning,
        tool.startedAtUptimeMillis,
        tool.completedAtUptimeMillis,
    ) {
        if (!tool.isRunning) {
            expanded = false
        } else {
            val startedAt = tool.startedAtUptimeMillis.takeIf { it > 0L } ?: platformUptimeMillis()
            val remainingDelay = SharedToolInvocationAutoExpandDelayMillis -
                (platformUptimeMillis() - startedAt)
            if (remainingDelay > 0L) delay(remainingDelay)
            expanded = true
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { expanded = !expanded }
            .padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tool.isRunning) {
            SharedReasoningShimmerText(
                text = toolTitle(tool),
                travelDurationMillis = 3_200,
            )
        } else {
            Text(
                text = toolTitle(tool),
                color = AetherOnSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AnimatedVisibility(
            visible = expanded && detail.command.isNotBlank(),
            enter = expandVertically(
                tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(SharedToolTransitionDurationMillis - 90, delayMillis = 40)),
            exit = shrinkVertically(tween(240, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
                fadeOut(tween(160, easing = FastOutLinearInEasing)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedToolCodeBlock(
                    label = stringResource(Res.string.tool_invocation_command),
                    content = remember(detail.command) { highlightSharedBashCommand(detail.command) },
                    maxHeight = 220,
                )
                detail.result?.let { result ->
                    SharedToolCodeBlock(
                        label = stringResource(Res.string.tool_invocation_result),
                        content = remember(result) { highlightSharedToolResult(result) },
                        maxHeight = 220,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedToolCodeBlock(
    label: String,
    content: AnnotatedString,
    maxHeight: Int,
) {
    SharedSyntaxHighlightedCodeBlock(
        label = label,
        content = content,
        maxHeight = maxHeight.dp,
    )
}

@Composable
internal fun SharedSyntaxHighlightedCodeBlock(
    label: String,
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 220.dp,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant)
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(AetherSurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp)
                    .heightIn(max = maxHeight).verticalScroll(scrollState),
            ) {
                Text(
                    content,
                    color = AetherOnSurface,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SharedThinkingIndicator() {
    SharedReasoningShimmerText(
        text = stringResource(Res.string.chat_thinking),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SharedGenerationStatusCard(
    text: String,
    detail: String,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(text, detail) { mutableStateOf(false) }
    val interactionSource = remember(text, detail) { MutableInteractionSource() }
    LaunchedEffect(detail) {
        if (detail.isBlank()) expanded = false
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing))
            .clickable(
                enabled = detail.isNotBlank(),
                interactionSource = interactionSource,
                indication = null,
            ) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRunning) {
            SharedReasoningShimmerText(text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded && detail.isNotBlank(),
            enter = expandVertically(
                tween(SharedToolTransitionDurationMillis, easing = SharedStatisticsPopupEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(SharedToolTransitionDurationMillis - 90, delayMillis = 40)),
            exit = shrinkVertically(
                tween(240, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(160, easing = FastOutLinearInEasing)),
        ) {
            SharedToolCodeBlock(
                label = stringResource(Res.string.common_error),
                content = remember(detail) { highlightSharedToolResult(detail) },
                maxHeight = 220,
            )
        }
    }
}

@Composable
private fun SharedMessageActions(
    text: String,
    usage: SharedPiUsage?,
    canRetry: Boolean,
    onCopy: (String) -> Boolean,
    onRetry: () -> Unit,
    onDelete: (() -> Unit)?,
    sessionTotalTokens: Long?,
    metrics: SharedMessageMetrics,
) {
    var showStatistics by rememberSaveable { mutableStateOf(false) }
    var keepStatisticsPopup by remember { mutableStateOf(false) }
    val statisticsPopupAlpha by animateFloatAsState(
        targetValue = if (showStatistics) 1f else 0f,
        animationSpec = if (showStatistics) {
            tween(durationMillis = 140, easing = SharedStatisticsPopupEasing)
        } else {
            tween(durationMillis = 90, easing = FastOutLinearInEasing)
        },
        finishedListener = { alpha -> if (alpha == 0f) keepStatisticsPopup = false },
        label = "shared_statistics_popup_alpha",
    )
    val statisticsPopupScale by animateFloatAsState(
        targetValue = if (showStatistics) 1f else 0.98f,
        animationSpec = if (showStatistics) {
            tween(durationMillis = 160, easing = SharedStatisticsPopupEasing)
        } else {
            tween(durationMillis = 90, easing = FastOutLinearInEasing)
        },
        label = "shared_statistics_popup_scale",
    )
    Box {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageActionIcon(
                LucideIcons.Copy,
                stringResource(Res.string.common_copy_reply),
                enabled = true,
            ) { onCopy(text) }
            MessageActionIcon(
                LucideIcons.RotateCcw,
                stringResource(Res.string.common_redo_reply),
                enabled = canRetry,
                onClick = onRetry,
            )
            onDelete?.let { delete ->
                MessageActionIcon(
                    LucideIcons.Trash2,
                    stringResource(Res.string.common_delete_reply),
                    enabled = canRetry,
                    onClick = delete,
                )
            }
            MessageActionIcon(
                LucideIcons.ChartNoAxesColumn,
                stringResource(Res.string.statistics_title),
                enabled = true,
            ) {
                keepStatisticsPopup = true
                showStatistics = true
            }
        }
        if (keepStatisticsPopup || showStatistics) {
            Popup(
                popupPositionProvider = remember { SharedStatisticsPopupPositionProvider() },
                onDismissRequest = { showStatistics = false },
                properties = PopupProperties(focusable = true),
            ) {
                SharedUsageStatisticsPanel(
                    usage = usage,
                    sessionTotalTokens = sessionTotalTokens,
                    metrics = metrics,
                    modifier = Modifier.graphicsLayer {
                        alpha = statisticsPopupAlpha
                        scaleX = statisticsPopupScale
                        scaleY = statisticsPopupScale
                        transformOrigin = TransformOrigin(0.18f, 1f)
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedUsageStatisticsPanel(
    usage: SharedPiUsage?,
    sessionTotalTokens: Long?,
    metrics: SharedMessageMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 236.dp, max = 300.dp)
            .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(22.dp))
            .background(AetherSurface.copy(alpha = 0.98f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(AetherPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LucideIcons.ChartNoAxesColumn,
                    contentDescription = null,
                    tint = AetherPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                stringResource(Res.string.statistics_title),
                color = AetherOnSurface,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SharedStatisticRow(stringResource(Res.string.statistics_session_tokens), sessionTotalTokens?.let(::formatSharedTokenCount))
            SharedStatisticRow(
                stringResource(Res.string.statistics_turn_tokens),
                usage?.takeIf { it.totalTokensAvailable }?.totalTokens?.let(::formatSharedTokenCount),
            )
            SharedStatisticRow(
                stringResource(Res.string.statistics_reasoning_tokens),
                usage?.takeIf { it.reasoningTokensAvailable }?.reasoningTokens
                    ?.let(::formatSharedTokenCount),
            )
            SharedStatisticRow(
                stringResource(Res.string.statistics_cached_input),
                usage?.takeIf { it.cachedInputTokensAvailable }?.cachedInputTokens
                    ?.let(::formatSharedTokenCount),
            )
            SharedStatisticRow(
                stringResource(Res.string.statistics_cache_write),
                usage?.takeIf { it.cacheWriteTokensAvailable }?.cacheWriteTokens
                    ?.let(::formatSharedTokenCount),
            )
            SharedStatisticRow(
                stringResource(Res.string.statistics_output_rate),
                metrics.outputTokensPerSecond?.let { "${formatSharedDecimal(it)} tok/s" },
            )
            SharedStatisticRow(stringResource(Res.string.statistics_first_token_latency), metrics.firstTokenLatencyMillis?.let(::formatSharedDuration))
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(AetherSurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SharedCompactStatisticValue(
                stringResource(Res.string.statistics_input),
                usage?.takeIf { it.inputTokensAvailable }?.inputTokens?.let(::formatSharedTokenCount),
            )
            SharedCompactStatisticValue(
                stringResource(Res.string.statistics_output),
                usage?.takeIf { it.outputTokensAvailable }?.outputTokens?.let(::formatSharedTokenCount),
            )
            SharedCompactStatisticValue(stringResource(Res.string.statistics_source), metrics.tokenUsageSource?.ifBlank { null })
        }
    }
}

@Composable
private fun SharedStatisticRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AetherOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(16.dp))
        Text(
            value ?: stringResource(Res.string.statistics_unavailable),
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SharedCompactStatisticValue(label: String, value: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value ?: stringResource(Res.string.statistics_dash),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
        )
    }
}

private class SharedStatisticsPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 28
        val preferredX = anchorBounds.right + 18
        val fallbackX = anchorBounds.left - popupContentSize.width + 18
        val x = if (preferredX + popupContentSize.width <= windowSize.width - margin) {
            preferredX
        } else {
            fallbackX.coerceAtLeast(margin)
        }
        val y = (anchorBounds.top - 10).coerceIn(
            margin,
            max(margin, windowSize.height - popupContentSize.height - margin),
        )
        return IntOffset(x, y)
    }
}

@Composable
private fun MessageActionIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.36f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SharedActionIconLabel(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) AetherSurfaceHigh else AetherSurfaceHigh.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AetherOnSurface else AetherOnSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) AetherOnSurface else AetherOnSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun SharedIconOnlyAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (enabled) AetherSurface.copy(alpha = 0.72f)
                else AetherSurface.copy(alpha = 0.35f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) AetherOnSurfaceVariant
            else AetherOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
    }
}

internal enum class SharedToolPresentation {
    Bash,
    BashOutput,
    KillBash,
    Sleep,
    Read,
    Edit,
    Write,
    Grep,
    Find,
    List,
    AnalyzeImage,
    WebSearch,
    WebFetch,
    Generic,
}

internal fun sharedToolPresentation(name: String): SharedToolPresentation = when (name.lowercase()) {
    "bash" -> SharedToolPresentation.Bash
    "read" -> SharedToolPresentation.Read
    "edit" -> SharedToolPresentation.Edit
    "write" -> SharedToolPresentation.Write
    "grep" -> SharedToolPresentation.Grep
    "find" -> SharedToolPresentation.Find
    "ls" -> SharedToolPresentation.List
    else -> SharedToolPresentation.Generic
}

@Composable
private fun toolTitle(tool: SharedChatToolInvocation): String {
    val isRunning = tool.isRunning
    val arguments = remember(tool.argumentsJson) { parseSharedJsonObject(tool.argumentsJson) }
    when (tool.name.lowercase()) {
        "aether_config_get",
        "aether_config_set",
        "aether_skill_manage",
        "aether_mcp_manage",
        "aether_termux_manage",
        "aether_agent_mode_manage",
        "aether_developer_manage" -> return formatSharedAetherToolTitle(
            toolName = tool.name,
            isRunning = isRunning,
            arguments = arguments,
        )
    }
    val resource: StringResource? = when (sharedToolPresentation(tool.name)) {
        SharedToolPresentation.Bash -> {
            if (isRunning) Res.string.tool_title_bash_running else Res.string.tool_title_bash_done
        }
        SharedToolPresentation.BashOutput -> {
            if (isRunning) {
                Res.string.tool_title_fetch_bash_output_running
            } else {
                Res.string.tool_title_fetch_bash_output_done
            }
        }
        SharedToolPresentation.KillBash -> {
            if (isRunning) Res.string.tool_title_kill_bash_running else Res.string.tool_title_kill_bash_done
        }
        SharedToolPresentation.Sleep -> {
            if (isRunning) Res.string.tool_title_sleep_running else Res.string.tool_title_sleep_done
        }
        SharedToolPresentation.Read -> {
            if (isRunning) Res.string.tool_title_read_running else Res.string.tool_title_read_done
        }
        SharedToolPresentation.Edit -> {
            if (isRunning) Res.string.tool_title_edit_running else Res.string.tool_title_edit_done
        }
        SharedToolPresentation.Write -> {
            if (isRunning) Res.string.tool_title_write_running else Res.string.tool_title_write_done
        }
        SharedToolPresentation.Grep -> {
            if (isRunning) Res.string.tool_title_grep_running else Res.string.tool_title_grep_done
        }
        SharedToolPresentation.Find -> {
            if (isRunning) Res.string.tool_title_find_running else Res.string.tool_title_find_done
        }
        SharedToolPresentation.List -> {
            if (isRunning) Res.string.tool_title_ls_running else Res.string.tool_title_ls_done
        }
        SharedToolPresentation.AnalyzeImage -> {
            if (isRunning) Res.string.tool_title_analyze_image_running else Res.string.tool_title_analyze_image_done
        }
        SharedToolPresentation.WebSearch,
        SharedToolPresentation.WebFetch -> null
        SharedToolPresentation.Generic -> null
    }
    if (resource != null) {
        return stringResource(resource)
    }
    return stringResource(
        if (isRunning) Res.string.tool_title_using_tool else Res.string.tool_title_used_tool,
        tool.name,
    )
}

@Composable
private fun formatSharedArgumentDrivenToolTitle(
    isRunning: Boolean,
    runningVerb: StringResource,
    completedVerb: StringResource,
    subject: String,
    fallback: StringResource,
): String {
    val action = stringResource(if (isRunning) runningVerb else completedVerb)
    val normalizedSubject = subject.trim()
    val displaySubject = if (normalizedSubject.isBlank()) {
        stringResource(fallback)
    } else {
        normalizedSubject.take(72) + if (normalizedSubject.length > 72) "..." else ""
    }
    return "$action $displaySubject"
}

@Composable
private fun formatSharedAetherToolTitle(
    toolName: String,
    isRunning: Boolean,
    arguments: JsonObject?,
): String {
    val action = arguments.sharedString("action").trim().lowercase()
    return when (toolName.lowercase()) {
        "aether_config_get" -> formatSharedArgumentDrivenToolTitle(
            isRunning,
            Res.string.tool_title_reading,
            Res.string.tool_title_read,
            summarizeSharedAetherCategories(arguments),
            Res.string.tool_title_aether_settings_fallback,
        )
        "aether_config_set" -> formatSharedArgumentDrivenToolTitle(
            isRunning,
            Res.string.tool_title_updating,
            Res.string.tool_title_updated,
            arguments.sharedString("category"),
            Res.string.tool_title_aether_settings_fallback,
        )
        "aether_skill_manage" -> when (action) {
            "install_remote" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_installing, Res.string.tool_title_installed,
                arguments.sharedString("url"), Res.string.tool_title_agent_skill_fallback,
            )
            "remove" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_removing, Res.string.tool_title_removed,
                sharedAetherString(arguments, "skill_id", "skillId"), Res.string.tool_title_agent_skill_fallback,
            )
            "set_enabled" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_updating, Res.string.tool_title_updated,
                sharedAetherString(arguments, "skill_id", "skillId"), Res.string.tool_title_agent_skill_fallback,
            )
            else -> stringResource(
                if (isRunning) Res.string.tool_title_reading_agent_skills else Res.string.tool_title_read_agent_skills,
            )
        }
        "aether_mcp_manage" -> when (action) {
            "upsert_streamable_http", "upsert_stdio" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_saving, Res.string.tool_title_saved,
                sharedAetherString(arguments, "display_name", "displayName"), Res.string.tool_title_mcp_server_fallback,
            )
            "remove" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_removing, Res.string.tool_title_removed,
                sharedAetherString(arguments, "server_id", "serverId"), Res.string.tool_title_mcp_server_fallback,
            )
            "set_enabled" -> formatSharedArgumentDrivenToolTitle(
                isRunning, Res.string.tool_title_updating, Res.string.tool_title_updated,
                sharedAetherString(arguments, "server_id", "serverId"), Res.string.tool_title_mcp_server_fallback,
            )
            else -> stringResource(
                if (isRunning) Res.string.tool_title_reading_mcp_servers else Res.string.tool_title_read_mcp_servers,
            )
        }
        "aether_termux_manage" -> when (action) {
            "configure_root_access" -> stringResource(
                if (isRunning) Res.string.tool_title_configuring_termux_root
                else Res.string.tool_title_configured_termux_root,
            )
            "inspect_root_setup" -> stringResource(
                if (isRunning) Res.string.tool_title_checking_root_setup
                else Res.string.tool_title_checked_root_setup,
            )
            else -> stringResource(
                if (isRunning) Res.string.tool_title_checking_termux_setup
                else Res.string.tool_title_checked_termux_setup,
            )
        }
        "aether_agent_mode_manage" -> when (action) {
            "set_authorization" -> stringResource(
                if (isRunning) Res.string.tool_title_updating_agent_mode_authorization
                else Res.string.tool_title_updated_agent_mode_authorization,
            )
            "request_shizuku_permission" -> stringResource(
                if (isRunning) Res.string.tool_title_requesting_shizuku_permission
                else Res.string.tool_title_requested_shizuku_permission,
            )
            "stop_display" -> stringResource(
                if (isRunning) Res.string.tool_title_stopping_agent_mode_display
                else Res.string.tool_title_stopped_agent_mode_display,
            )
            "refresh_displays" -> stringResource(
                if (isRunning) Res.string.tool_title_refreshing_agent_mode_displays
                else Res.string.tool_title_refreshed_agent_mode_displays,
            )
            else -> stringResource(
                if (isRunning) Res.string.tool_title_checking_agent_mode_authorization
                else Res.string.tool_title_checked_agent_mode_authorization,
            )
        }
        "aether_developer_manage" -> stringResource(
            if (isRunning) Res.string.tool_title_reading_aether_diagnostics
            else Res.string.tool_title_read_aether_diagnostics,
        )
        else -> stringResource(
            if (isRunning) Res.string.tool_title_managing_aether else Res.string.tool_title_managed_aether,
        )
    }
}

private fun sharedAetherString(arguments: JsonObject?, primary: String, secondary: String): String =
    arguments.sharedString(primary).ifBlank { arguments.sharedString(secondary) }

private fun toolIcon(name: String): ImageVector = when (sharedToolPresentation(name)) {
    SharedToolPresentation.Bash,
    SharedToolPresentation.BashOutput,
    SharedToolPresentation.KillBash -> Icons.Rounded.Terminal
    SharedToolPresentation.WebSearch,
    SharedToolPresentation.WebFetch -> Icons.Rounded.Language
    else -> Icons.Rounded.Build
}

internal fun highlightSharedBashCommand(command: String): AnnotatedString = buildAnnotatedString {
    appendSharedStyled("$ ", SpanStyle(color = AetherSecondary, fontWeight = FontWeight.SemiBold))
    var expectsCommand = true
    bashCommandTokens(command).forEach { token ->
        val style = when {
            token.isBlank() -> null
            token in setOf("|", "||", "&&", ";", ">", "<", "(", ")") -> {
                expectsCommand = true
                SpanStyle(color = AetherOnSurfaceVariant)
            }
            token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = AetherTertiary)
            token.startsWith("$") -> SpanStyle(color = AetherPrimary)
            token.startsWith("-") -> SpanStyle(color = AetherSecondary)
            token.startsWith("/") || token.startsWith("~/") || token.startsWith("./") ||
                token.startsWith("../") -> SpanStyle(color = AetherSecondary)
            expectsCommand -> {
                expectsCommand = false
                SpanStyle(color = AetherPrimary, fontWeight = FontWeight.SemiBold)
            }
            token.all(Char::isDigit) -> SpanStyle(color = AetherTertiary)
            else -> SpanStyle(color = AetherOnSurface)
        }
        appendSharedStyled(token, style)
    }
}

internal fun highlightSharedToolResult(result: String): AnnotatedString = buildAnnotatedString {
    val tokenPattern = Regex("""\s+|~?/[\w./-]+|\b\d+\b|[A-Za-z_][A-Za-z0-9_]*:|[^\s]+""")
    tokenPattern.findAll(result).forEach { match ->
        val token = match.value
        val lowerToken = token.lowercase()
        val style = when {
            token.isBlank() -> null
            lowerToken.contains("error") || lowerToken.contains("failed") ||
                lowerToken.contains("denied") -> SpanStyle(color = AetherError, fontWeight = FontWeight.Medium)
            lowerToken.startsWith("http://") || lowerToken.startsWith("https://") ||
                token.startsWith("/") || token.startsWith("~/") || token.contains("/") -> {
                SpanStyle(color = AetherSecondary)
            }
            token.all(Char::isDigit) -> SpanStyle(color = AetherTertiary)
            token.endsWith(":") -> SpanStyle(color = AetherOnSurfaceVariant, fontWeight = FontWeight.Medium)
            else -> SpanStyle(color = AetherOnSurface)
        }
        appendSharedStyled(token, style)
    }
}

internal fun highlightSharedTerminalTranscript(result: String): AnnotatedString = buildAnnotatedString {
    result.lineSequence().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        if (line.startsWith("$ ")) {
            append(highlightSharedBashCommand(line.removePrefix("$ ")))
        } else {
            append(highlightSharedToolResult(line))
        }
    }
}

private fun AnnotatedString.Builder.appendSharedStyled(text: String, style: SpanStyle?) {
    if (style == null) {
        append(text)
    } else {
        pushStyle(style)
        append(text)
        pop()
    }
}

internal fun sharedAttachmentMetaLabel(
    kindLabel: String,
    attachment: SharedChatAttachment,
): String = listOfNotNull(
    kindLabel,
    attachment.sizeBytes.takeIf { it >= 0L }?.let(::formatSharedAttachmentSize),
).joinToString(" | ")

internal fun formatSharedAttachmentSize(value: Long): String = when {
    value >= 1_048_576L -> "${formatSharedFixedTenths(value, 1_048_576L)} MB"
    value >= 1_024L -> "${formatSharedFixedTenths(value, 1_024L)} KB"
    else -> "$value B"
}

private fun formatSharedFixedTenths(value: Long, divisor: Long): String {
    val tenths = (value.toDouble() / divisor.toDouble() * 10.0).roundToLong()
    return "${tenths / 10L}.${tenths % 10L}"
}

internal fun formatSharedTokenCount(value: Long): String = when {
    value >= 1_000_000L -> "${formatSharedOneDecimal(value, 1_000_000L)}M"
    value >= 1_000L -> "${formatSharedOneDecimal(value, 1_000L)}K"
    else -> value.toString()
}

internal fun formatSharedDuration(durationMillis: Long): String = when {
    durationMillis < 1_000L -> "${durationMillis}ms"
    else -> {
        val hundredths = (durationMillis / 10.0).roundToLong()
        "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}s"
    }
}

internal fun formatSharedThoughtDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || hours > 0) add("${minutes}min")
        add("${seconds}s")
    }.joinToString(" ")
}

internal fun formatSharedDecimal(value: Double): String {
    if (!value.isFinite()) return "-"
    val tenths = (value.coerceAtLeast(0.0) * 10.0).roundToLong()
    val whole = tenths / 10L
    val decimal = tenths % 10L
    return "$whole.$decimal"
}

private fun formatSharedOneDecimal(value: Long, divisor: Long): String {
    val tenths = (value.toDouble() / divisor.toDouble() * 10.0).roundToLong()
    return "${tenths / 10L}.${tenths % 10L}"
}

internal fun formatSharedByteCount(value: Long): String = when {
    value < 1_024L -> "$value B"
    value < 1_048_576L -> "${formatSharedScaledCount(value, 1_024L)} KB"
    value < 1_073_741_824L -> "${formatSharedScaledCount(value, 1_048_576L)} MB"
    else -> "${formatSharedScaledCount(value, 1_073_741_824L)} GB"
}

private fun formatSharedScaledCount(value: Long, divisor: Long): String {
    val whole = value / divisor
    val decimal = (value % divisor) * 10L / divisor
    return if (decimal == 0L) whole.toString() else "$whole.$decimal"
}
