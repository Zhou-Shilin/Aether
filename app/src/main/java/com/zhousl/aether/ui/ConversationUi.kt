package com.zhousl.aether.ui

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zhousl.aether.R
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.McpServerConfig
import com.zhousl.aether.data.McpTransportConfig
import com.zhousl.aether.data.PendingSessionInput
import com.zhousl.aether.data.ProviderModelOption
import com.zhousl.aether.data.SessionExecutionState
import com.zhousl.aether.data.SessionFollowUpMode
import com.zhousl.aether.data.quickActionLabel
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherBackgroundGradientTop
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.json.JSONObject

private sealed interface ConversationListItem {
    val key: String

    data class Message(
        val message: ChatMessage,
    ) : ConversationListItem {
        override val key: String = message.id
    }

    data class AssistantGroup(
        val messages: List<ChatMessage>,
    ) : ConversationListItem {
        override val key: String = messages.firstOrNull()?.responseGroupId
            ?: messages.firstOrNull()?.id
            ?: "assistant-group"
    }

    data class CompactStatus(
        val message: ChatMessage,
    ) : ConversationListItem {
        override val key: String = message.id
    }
}

private val ConversationTopFadeHeight = 42.dp
private val ComposerCardShape = RoundedCornerShape(26.dp)
private val ComposerFocusedCardShape = RoundedCornerShape(28.dp)
private val ComposerPlusMenuMaxHeight = 372.dp
private const val ComposerPopupActionDelayMillis = 240L
private const val MinimumWallClockMillis = 946_684_800_000L
private val ChatGptControlShadow = Color(0x14000000)
private val ChatGptComposerShadow = Color(0x18000000)
private val ChatGptPurple = Color(0xFF9B5CFF)
private val ChatGptMotionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

internal enum class PendingGenerationIndicator {
    None,
    Thinking,
    Status,
}

internal fun pendingGenerationIndicator(
    isSending: Boolean,
    pendingAssistantText: String,
    pendingStatusText: String,
    hasVisiblePendingReasoning: Boolean = false,
    hasVisiblePendingWork: Boolean = false,
    lastVisibleMessageAuthor: MessageAuthor? = null,
): PendingGenerationIndicator = when {
    !isSending -> PendingGenerationIndicator.None
    pendingStatusText.isNotBlank() -> PendingGenerationIndicator.Status
    hasVisiblePendingReasoning -> PendingGenerationIndicator.None
    hasVisiblePendingWork -> PendingGenerationIndicator.None
    lastVisibleMessageAuthor == MessageAuthor.Agent -> PendingGenerationIndicator.None
    pendingAssistantText.isBlank() -> PendingGenerationIndicator.Thinking
    else -> PendingGenerationIndicator.None
}

internal fun shouldRenderPendingGenerationBlock(
    isSending: Boolean,
    pendingResponseBlocks: List<AssistantResponseBlock>,
    pendingToolInvocations: List<ChatToolInvocation>,
    pendingStatusText: String,
    lastVisibleAgentText: String?,
): Boolean {
    val hasPendingContent = pendingResponseBlocks.isNotEmpty() ||
        pendingToolInvocations.isNotEmpty() ||
        isSending
    if (!hasPendingContent) return false

    val pendingText = pendingResponseBlocks.visibleText().trim()
    val lastAgentText = lastVisibleAgentText?.trim().orEmpty()
    val isCommittedTextEcho = pendingText.isNotBlank() &&
        lastAgentText.isNotBlank() &&
        pendingText == lastAgentText &&
        pendingToolInvocations.isEmpty() &&
        pendingStatusText.isBlank()
    return !isCommittedTextEcho
}

internal fun hasVisibleReasoningStatus(trace: ReasoningTrace): Boolean =
    trace.latestStatusText.isNotBlank() ||
        trace.rawText.isNotBlank() ||
        trace.hasTimelineContent ||
        trace.completedAtMillis != null

private fun topOverlayBodyGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.98f),
        0.28f to AetherBackground.copy(alpha = 0.92f),
        0.58f to AetherBackground.copy(alpha = 0.52f),
        0.82f to AetherBackground.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
    )
)

private fun topOverlayTailGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.10f),
        0.42f to AetherBackground.copy(alpha = 0.04f),
        1.0f to Color.Transparent,
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    conversationStateKey: String,
    messages: List<ChatMessage>,
    workspaceDirectory: String,
    pendingToolInvocations: List<ChatToolInvocation>,
    pendingToolInvocationStateKey: String,
    pendingResponseBlocks: List<AssistantResponseBlock>,
    pendingAssistantText: String,
    pendingStatusText: String,
    pendingStatusDetail: String,
    activeTurnStartedAtMillis: Long?,
    isCompacting: Boolean,
    pendingInputs: List<PendingSessionInput>,
    inputValue: String,
    draftAttachments: List<ChatAttachment>,
    modelOptions: List<ProviderModelOption>,
    selectedModelKey: String,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    agentModeDisplayState: AgentModeDisplayState,
    allowRootImageRead: Boolean = false,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    onInputChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onRemoveDraftAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onSaveAttachment: (ChatAttachment) -> Unit,
    onOpenLink: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRedoAgentMessage: (String) -> Unit,
    onRetryUserMessage: (String) -> Unit,
    onSwitchUserMessageBranch: (String, Int) -> Unit,
    onCopyMessage: (ChatMessage) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onAttachAgentModePreviewSurface: (Surface) -> Unit,
    onDetachAgentModePreviewSurface: (Surface) -> Unit,
    onPauseGeneration: () -> Unit,
    onDismissTermuxSetupNotice: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    isSending: Boolean,
) {
    val listState = rememberSaveable(
        conversationStateKey,
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }
    val conversationItems = remember(messages) { buildConversationListItems(messages) }
    val compactSuggestion = remember(messages) { compactCommandSuggestion(messages) }
    val compactSuggestionText = compactSuggestion.percent?.let { percent ->
        stringResource(R.string.chat_compact_thread_context_percent, percent)
    } ?: stringResource(R.string.chat_compact_thread_context)
    val lastVisibleMessageAuthor = remember(messages) {
        messages.lastOrNull { message ->
            message.displayKind == MessageDisplayKind.Standard
        }?.author
    }
    val lastVisibleAgentText = remember(messages) {
        messages.lastOrNull { message ->
            message.displayKind == MessageDisplayKind.Standard &&
                message.author == MessageAuthor.Agent
        }?.text
    }
    var previewAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var shouldAutoFollow by rememberSaveable(conversationStateKey) { mutableStateOf(true) }
    var topBarBodyHeightPx by remember { mutableIntStateOf(0) }
    var composerBodyHeightPx by remember { mutableIntStateOf(0) }
    var pendingGenerationHeightPx by remember { mutableIntStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val fallbackTopBarBodyHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 68.dp
    }
    val topBarBodyHeight = with(density) {
        if (topBarBodyHeightPx > 0) topBarBodyHeightPx.toDp() else fallbackTopBarBodyHeight
    }
    val composerBodyHeight = with(density) {
        if (composerBodyHeightPx > 0) composerBodyHeightPx.toDp() else 112.dp
    }
    val imeBottom = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val animatedImeBottom by animateDpAsState(
        targetValue = imeBottom,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "conversation_empty_ime_bottom",
    )
    val animatedImeBottomPx = with(density) { animatedImeBottom.roundToPx() }
    val conversationScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    shouldAutoFollow = listState.isAtConversationBottom()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldAutoFollow = listState.isAtConversationBottom()
                return Velocity.Zero
            }
        }
    }

    suspend fun scrollToConversationBottom() {
        val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastItemIndex >= 0) {
            listState.scrollToItem(lastItemIndex)
        }
    }

    LaunchedEffect(listState, shouldAutoFollow, animatedImeBottomPx, composerBodyHeightPx) {
        if (!shouldAutoFollow) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            listOf(
                layoutInfo.totalItemsCount,
                lastVisibleItem?.index ?: -1,
                lastVisibleItem?.offset ?: 0,
                lastVisibleItem?.size ?: 0,
            )
        }
            .distinctUntilChanged()
            .collect {
                if (
                    shouldAutoFollow &&
                    !listState.isScrollInProgress &&
                    listState.layoutInfo.totalItemsCount > 0
                ) {
                    scrollToConversationBottom()
                }
            }
    }
    val autoFollowContentKey = remember(
        conversationItems,
        pendingInputs,
        pendingResponseBlocks,
        pendingAssistantText,
        pendingToolInvocations,
        pendingStatusText,
        pendingStatusDetail,
        isCompacting,
        isSending,
    ) {
        buildString {
            append(conversationItems.lastOrNull()?.key.orEmpty())
            append('|')
            append(pendingInputs.joinToString("|") { "${it.id}:${it.preview.length}:${it.attachmentCount}" })
            append('|')
            append(
                pendingResponseBlocks.joinToString("|") { block ->
                    when (block) {
                        is AssistantResponseBlock.Text -> "${block.id}:text:${block.text.length}"
                        is AssistantResponseBlock.ToolGroup -> block.toolInvocations.joinToString(
                            prefix = "${block.id}:tools:",
                            separator = ",",
                        ) { invocation ->
                            "${invocation.id}:${invocation.isRunning}:${invocation.outputJson.length}"
                        }
                        is AssistantResponseBlock.Reasoning -> buildString {
                            append("${block.id}:reasoning:")
                            append(block.trace.rawText.length)
                            append(':')
                            append(block.trace.latestStatusText.length)
                            append(':')
                            append(block.trace.completedAtMillis ?: 0L)
                            append(':')
                            append(block.trace.chunks.joinToString(",") { chunk ->
                                "${chunk.id}:${chunk.title.length}:${chunk.detail.length}:${chunk.isPending}:${chunk.timelineOrder}"
                            })
                            append(':')
                            append(block.trace.toolInvocations.joinToString(",") { invocation ->
                                "${invocation.id}:${invocation.isRunning}:${invocation.outputJson.length}:${invocation.startedAtMillis}:${invocation.completedAtMillis ?: 0L}:${invocation.timelineOrder}"
                            })
                        }
                    }
                }
            )
            append('|')
            append(pendingAssistantText.length)
            append('|')
            append(
                pendingToolInvocations.joinToString("|") { invocation ->
                    "${invocation.id}:${invocation.isRunning}:${invocation.outputJson.length}"
                }
            )
            append('|')
            append(pendingStatusText)
            append('|')
            append(pendingStatusDetail)
            append('|')
            append(isCompacting)
            append('|')
            append(isSending)
        }
    }
    LaunchedEffect(
        autoFollowContentKey,
        pendingGenerationHeightPx,
        animatedImeBottomPx,
        composerBodyHeightPx,
        shouldAutoFollow,
    ) {
        if (!shouldAutoFollow || listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect
        withFrameNanos { }
        if (shouldAutoFollow && !listState.isScrollInProgress) {
            scrollToConversationBottom()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(AetherBackgroundGradientTop, AetherBackground, AetherSurface)
                    )
                )
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                ConversationEmptyState(
                    modifier = Modifier.padding(
                        top = topBarBodyHeight + 20.dp,
                        bottom = composerBodyHeight + animatedImeBottom + 16.dp,
                    ),
                    inputFocused = composerFocused,
                    onStarterPromptSelected = onInputChanged,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(conversationScrollConnection),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topBarBodyHeight + 10.dp,
                        bottom = composerBodyHeight + animatedImeBottom + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(conversationItems, key = { it.key }) { item ->
                        when (item) {
                            is ConversationListItem.Message -> {
                                val message = item.message
                                ConversationMessageBubble(
                                    message = message,
                                    actionsEnabled = !isSending,
                                    workspaceDirectory = workspaceDirectory,
                                    allowRootImageRead = allowRootImageRead,
                                    onOpenAttachment = { previewAttachment = it },
                                    onOpenLink = onOpenLink,
                                    onEdit = { onEditMessage(message.id) },
                                    onDelete = { onDeleteMessage(message.id) },
                                    onCopy = { onCopyMessage(message) },
                                    onRedo = { onRedoAgentMessage(message.id) },
                                    onRetry = { onRetryUserMessage(message.id) },
                                    onSwitchBranch = { delta -> onSwitchUserMessageBranch(message.id, delta) },
                                )
                            }

                            is ConversationListItem.AssistantGroup -> {
                                val lastMessage = item.messages.last()
                                ConversationAssistantGroupBubble(
                                    messages = item.messages,
                                    actionsEnabled = !isSending,
                                    workspaceDirectory = workspaceDirectory,
                                    allowRootImageRead = allowRootImageRead,
                                    onOpenAttachment = { previewAttachment = it },
                                    onOpenLink = onOpenLink,
                                    onCopy = {
                                        onCopyMessage(
                                            lastMessage.copy(
                                                text = item.messages.joinToString("\n\n") { message -> message.text }
                                                    .trim(),
                                            )
                                        )
                                    },
                                    onRedo = { onRedoAgentMessage(lastMessage.id) },
                                    onDelete = { onDeleteMessage(lastMessage.id) },
                                )
                            }

                            is ConversationListItem.CompactStatus -> {
                                CompactStatusDivider(text = item.message.text.ifBlank { stringResource(R.string.chat_context_compacted) })
                            }
                        }
                    }
                    if (isCompacting) {
                        item(key = "compact-running-status") {
                            CompactStatusDivider(
                                text = stringResource(R.string.chat_compacting_context),
                                isRunning = true,
                            )
                        }
                    }
                    if (
                        shouldRenderPendingGenerationBlock(
                            isSending = isSending,
                            pendingResponseBlocks = pendingResponseBlocks,
                            pendingToolInvocations = pendingToolInvocations,
                            pendingStatusText = pendingStatusText,
                            lastVisibleAgentText = lastVisibleAgentText,
                        )
                    ) {
                        item(key = "pending-generation-block") {
                            val indicator = pendingGenerationIndicator(
                                isSending = isSending,
                                pendingAssistantText = pendingAssistantText,
                                pendingStatusText = pendingStatusText,
                                hasVisiblePendingWork = pendingResponseBlocks.hasVisiblePendingWork() ||
                                    pendingToolInvocations.isNotEmpty(),
                                lastVisibleMessageAuthor = lastVisibleMessageAuthor,
                                hasVisiblePendingReasoning = pendingResponseBlocks.any {
                                    it is AssistantResponseBlock.Reasoning &&
                                        hasVisibleReasoningStatus(it.trace)
                                },
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { pendingGenerationHeightPx = it.height }
                                    .animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PendingAssistantTimeline(
                                    blocks = pendingResponseBlocks,
                                    workspaceDirectory = workspaceDirectory,
                                    allowRootImageRead = allowRootImageRead,
                                    onOpenLink = onOpenLink,
                                    pendingToolInvocationStateKey = pendingToolInvocationStateKey,
                                    pendingToolInvocations = pendingToolInvocations,
                                    activeTurnStartedAtMillis = activeTurnStartedAtMillis,
                                    agentModeSelected = agentModeSelected,
                                    agentModeDisplayState = agentModeDisplayState,
                                    onAttachAgentModePreviewSurface = onAttachAgentModePreviewSurface,
                                    onDetachAgentModePreviewSurface = onDetachAgentModePreviewSurface,
                                )
                                when (indicator) {
                                    PendingGenerationIndicator.Thinking -> {
                                        ConversationThinkingIndicator()
                                    }

                                    PendingGenerationIndicator.Status -> {
                                        ReconnectingStatusCard(
                                            text = pendingStatusText,
                                            detail = pendingStatusDetail,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }

                                    PendingGenerationIndicator.None -> Unit
                                }
                            }
                        }
                    }
                    items(pendingInputs, key = { it.id }) { pendingInput ->
                        PendingSessionInputBubble(pendingInput = pendingInput)
                    }
                    item(key = "conversation-bottom-anchor") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                        )
                    }
                }
            }

            ConversationTopOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                onBodyHeightChanged = { topBarBodyHeightPx = it },
                modelOptions = modelOptions,
                selectedModelKey = selectedModelKey,
                onMenu = onMenu,
                onModelSelected = onModelSelected,
                onNewChat = onNewChat,
            )

            ConversationComposerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                conversationStateKey = conversationStateKey,
                onBodyHeightChanged = { composerBodyHeightPx = it },
                value = inputValue,
                attachments = draftAttachments,
                availableSkills = availableSkills,
                availableMcpServers = availableMcpServers,
                selectedSkillIds = selectedSkillIds,
                selectedMcpServerIds = selectedMcpServerIds,
                agentModeAvailable = agentModeAvailable,
                agentModeSelected = agentModeSelected,
                isEditing = isEditing,
                termuxSetupState = termuxSetupState,
                isSending = isSending,
                showStarterPromptHint = showStarterPromptHint,
                showTermuxSetupNotice = showTermuxSetupNotice,
                compactSuggestionText = compactSuggestionText,
                onValueChange = onInputChanged,
                onRemoveAttachment = onRemoveDraftAttachment,
                onSetSkillSelected = onSetSkillSelected,
                onSetMcpServerSelected = onSetMcpServerSelected,
                onSetAgentModeSelected = onSetAgentModeSelected,
                onCancelEdit = onCancelEdit,
                onPickImages = onPickImages,
                onPickFiles = onPickFiles,
                onRequestTermuxPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefreshTermuxSetup = onRefreshTermuxSetup,
                onPauseGeneration = onPauseGeneration,
                onDismissTermuxSetupNotice = onDismissTermuxSetupNotice,
                onDismissStarterPromptHint = onDismissStarterPromptHint,
                onFocusChanged = { composerFocused = it },
                onSend = onSend,
                onQueueFollowUp = onQueueFollowUp,
                onSteerFollowUp = onSteerFollowUp,
            )

            previewAttachment?.let { attachment ->
                AttachmentPreviewDialog(
                    attachment = attachment,
                    onDismiss = { previewAttachment = null },
                    onSave = { onSaveAttachment(attachment) },
                )
            }
        }
    }
}

private fun LazyListState.isAtConversationBottom(): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return true
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    val isLastItemVisible = lastVisibleItem.index == layoutInfo.totalItemsCount - 1
    val distanceFromBottom = layoutInfo.viewportEndOffset - (lastVisibleItem.offset + lastVisibleItem.size)
    return isLastItemVisible && distanceFromBottom >= -32
}

@Composable
private fun ConversationTopOverlay(
    modifier: Modifier = Modifier,
    onBodyHeightChanged: (Int) -> Unit,
    modelOptions: List<ProviderModelOption>,
    selectedModelKey: String,
    onMenu: () -> Unit,
    onModelSelected: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(topOverlayBodyGradient())
                .onSizeChanged { onBodyHeightChanged(it.height) },
        ) {
            ConversationTopBar(
                modelOptions = modelOptions,
                selectedModelKey = selectedModelKey,
                onMenu = onMenu,
                onModelSelected = onModelSelected,
                onNewChat = onNewChat,
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(ConversationTopFadeHeight)
                .background(topOverlayTailGradient())
        )
    }
}

@Composable
private fun ConversationTopBar(
    modifier: Modifier = Modifier,
    modelOptions: List<ProviderModelOption>,
    selectedModelKey: String,
    onMenu: () -> Unit,
    onModelSelected: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCircleButton(
            icon = Icons.Rounded.Menu,
            contentDescription = stringResource(R.string.common_menu),
            onClick = onMenu,
            size = 44.dp,
            containerColor = AetherSurface.copy(alpha = 0.96f),
        )
        ConversationModelSelector(
            options = modelOptions,
            selectedModelKey = selectedModelKey,
            onSelected = onModelSelected,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        HeaderCircleButton(
            icon = LucideIcons.SquarePen,
            contentDescription = stringResource(R.string.common_new_chat),
            onClick = onNewChat,
            size = 44.dp,
            containerColor = AetherSurface.copy(alpha = 0.96f),
        )
    }
}

@Composable
private fun ConversationModelSelector(
    options: List<ProviderModelOption>,
    selectedModelKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBottomPx by remember { mutableIntStateOf(0) }
    val menuVisibility = remember { MutableTransitionState(false) }
    menuVisibility.targetState = expanded
    val density = LocalDensity.current
    val menuWidth = 276.dp
    val selectedOption = options.firstOrNull { it.key == selectedModelKey } ?: options.firstOrNull()
    val fallbackLabel = stringResource(R.string.chat_select_model)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    anchorBottomPx = coordinates.boundsInRoot().bottom.toInt()
                }
                .clickable(enabled = options.isNotEmpty()) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selectedOption?.chatLabel ?: fallbackLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }

        if (menuVisibility.currentState || menuVisibility.targetState) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, anchorBottomPx - with(density) { 32.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ) + slideInVertically(initialOffsetY = { -it / 10 }),
                    exit = fadeOut() + scaleOut(
                        targetScale = 0.98f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ) + slideOutVertically(targetOffsetY = { -it / 12 }),
                ) {
                    Column(
                        modifier = Modifier
                            .width(menuWidth)
                            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                            .clip(RoundedCornerShape(28.dp))
                            .background(AetherSurface)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            options.forEachIndexed { index, option ->
                                val isSelected = option.key == selectedOption?.key
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            expanded = false
                                            onSelected(option.key)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 15.dp),
                                ) {
                                    Text(
                                        text = option.chatLabel,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        ),
                                        color = AetherOnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 24.dp),
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = AetherOnSurface,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(22.dp),
                                        )
                                    }
                                }
                                if (index != options.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp)
                                            .height(1.dp)
                                            .background(AetherOnSurfaceVariant.copy(alpha = 0.12f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationEmptyState(
    modifier: Modifier = Modifier,
    inputFocused: Boolean,
    onStarterPromptSelected: (String) -> Unit,
) {
    val titleOffset by animateDpAsState(
        targetValue = if (inputFocused) (-34).dp else (-24).dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "empty_state_title_offset",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .offset(y = titleOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_welcome_help),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.2).sp,
            ),
            color = AetherOnSurface,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyStateChip(
                    icon = Icons.Rounded.Image,
                    label = stringResource(R.string.chat_analyze_image_chip),
                    iconTint = Color(0xFF38A961),
                    onClick = { onStarterPromptSelected("Analyze this image and describe the important details.") },
                )
                EmptyStateChip(
                    icon = Icons.Rounded.Terminal,
                    label = stringResource(R.string.chat_code_chip),
                    iconTint = Color(0xFF7D70DD),
                    onClick = { onStarterPromptSelected("Help me write or debug this code: ") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyStateChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.chat_help_me_write_chip),
                    iconTint = Color(0xFFE48AAE),
                    onClick = { onStarterPromptSelected("Help me write a clear, polished message about ") },
                )
                EmptyStateChip(
                    icon = Icons.Rounded.AttachFile,
                    label = stringResource(R.string.chat_summarize_file_chip),
                    iconTint = Color(0xFF66C7D4),
                    onClick = { onStarterPromptSelected("Summarize this file and list the key points.") },
                )
            }
        }
    }
}

@Composable
private fun EmptyStateChip(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(999.dp), ambientColor = ChatGptControlShadow, spotColor = ChatGptControlShadow)
            .clip(RoundedCornerShape(999.dp))
            .background(AetherSurface.copy(alpha = 0.98f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationThinkingIndicator() {
    ShimmerStatusText(
        text = stringResource(R.string.chat_thinking),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PendingAssistantTimeline(
    blocks: List<AssistantResponseBlock>,
    workspaceDirectory: String,
    allowRootImageRead: Boolean,
    onOpenLink: (String) -> Unit,
    pendingToolInvocationStateKey: String,
    pendingToolInvocations: List<ChatToolInvocation>,
    activeTurnStartedAtMillis: Long?,
    agentModeSelected: Boolean,
    agentModeDisplayState: AgentModeDisplayState,
    onAttachAgentModePreviewSurface: (Surface) -> Unit,
    onDetachAgentModePreviewSurface: (Surface) -> Unit,
) {
    val visiblePendingInvocations = pendingToolInvocations.filterNot { it.isAgentModeDisplayInvocation() }
    val blockAgentModeInvocations = blocks.flatMap { it.agentModeToolInvocations() }
    val pendingAgentModeInvocations = pendingToolInvocations.filter { it.isAgentModeDisplayInvocation() }
    val agentModePreviewVisible =
        agentModeSelected &&
            agentModeDisplayState.isActive &&
            (blockAgentModeInvocations.isNotEmpty() ||
                pendingAgentModeInvocations.isNotEmpty() ||
                agentModeDisplayState.latestPreviewPath.isNotBlank())
    val firstAgentModeBlockIndex = blocks.firstAgentModeBlockIndex().let { index ->
        if (index >= 0) index else if (agentModePreviewVisible) blocks.size else -1
    }
    val agentModeOverlayText = if (agentModePreviewVisible) {
        blocks.lastTextBlockAfterAgentMode().orEmpty()
    } else {
        ""
    }
    if (blocks.isEmpty()) {
        if (!agentModePreviewVisible && visiblePendingInvocations.isNotEmpty()) {
            val pendingToolsDurationMillis by produceState(
                initialValue = workDurationMillisForToolInvocations(visiblePendingInvocations),
                visiblePendingInvocations,
            ) {
                val recordedStartedAtMillis = visiblePendingInvocations
                    .mapNotNull { it.startedAtMillis.takeIf { timestamp -> timestamp > 0L } }
                    .plus(listOfNotNull(activeTurnStartedAtMillis?.takeIf { it > 0L }))
                    .filter { it >= MinimumWallClockMillis }
                    .minOrNull()
                val startedRealtimeMillis = SystemClock.elapsedRealtime()
                while (true) {
                    value = if (recordedStartedAtMillis != null) {
                        workDurationMillisForToolInvocations(
                            visiblePendingInvocations,
                            endAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        fallbackDurationMillis(startedRealtimeMillis)
                    }
                    kotlinx.coroutines.delay(1_000L)
                }
            }
            AgentWorkingStatusHeader(
                title = formatWorkedSummaryTitle(pendingToolsDurationMillis),
            )
            ToolInvocationList(
                toolInvocations = visiblePendingInvocations,
                stateKey = pendingToolInvocationStateKey,
                autoExpand = true,
            )
        }
        return
    }

    val workingDurationMillis by produceState(initialValue = workDurationMillisForBlocks(blocks), blocks.firstOrNull()?.id) {
        val recordedStartedAtMillis = listOfNotNull(
            blocks.workStartedAtMillis(),
            activeTurnStartedAtMillis?.takeIf { it >= MinimumWallClockMillis },
        ).minOrNull()
        val startedRealtimeMillis = SystemClock.elapsedRealtime()
        while (true) {
            value = if (recordedStartedAtMillis != null) {
                workDurationMillisForBlocks(blocks, endAtMillis = System.currentTimeMillis())
            } else {
                fallbackDurationMillis(startedRealtimeMillis)
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val shouldShowWorkingDisclosure = blocks.any { block ->
        when (block) {
            is AssistantResponseBlock.Text -> block.text.isNotBlank()
            is AssistantResponseBlock.ToolGroup -> block.toolInvocations.isNotEmpty()
            is AssistantResponseBlock.Reasoning -> hasVisibleReasoningStatus(block.trace)
        }
    }

    if (shouldShowWorkingDisclosure) {
        AgentWorkingStatusHeader(
            title = formatWorkedSummaryTitle(workingDurationMillis),
        )
        blocks.forEachIndexed { index, block ->
            if (agentModePreviewVisible && index == firstAgentModeBlockIndex) {
                AgentModePreviewPanel(
                    displayState = agentModeDisplayState,
                    toolInvocation = (blockAgentModeInvocations + pendingAgentModeInvocations).lastOrNull()
                        ?: pendingToolInvocations.lastOrNull(),
                    overlayText = agentModeOverlayText,
                    workspaceDirectory = workspaceDirectory,
                    allowRootImageRead = allowRootImageRead,
                    onOpenLink = onOpenLink,
                    onAttachSurface = onAttachAgentModePreviewSurface,
                    onDetachSurface = onDetachAgentModePreviewSurface,
                )
            }
            PendingAssistantTimelineBlock(
                block = block,
                index = index,
                isLastBlock = index == blocks.lastIndex,
                agentModePreviewVisible = false,
                workspaceDirectory = workspaceDirectory,
                allowRootImageRead = allowRootImageRead,
                onOpenLink = onOpenLink,
                pendingToolInvocationStateKey = pendingToolInvocationStateKey,
                agentModeSelected = agentModeSelected,
                agentModeDisplayState = agentModeDisplayState,
                onAttachAgentModePreviewSurface = onAttachAgentModePreviewSurface,
                onDetachAgentModePreviewSurface = onDetachAgentModePreviewSurface,
            )
        }
    }
}

@Composable
private fun PendingAssistantTimelineBlock(
    block: AssistantResponseBlock,
    index: Int,
    isLastBlock: Boolean,
    agentModePreviewVisible: Boolean,
    workspaceDirectory: String,
    allowRootImageRead: Boolean,
    onOpenLink: (String) -> Unit,
    pendingToolInvocationStateKey: String,
    agentModeSelected: Boolean,
    agentModeDisplayState: AgentModeDisplayState,
    onAttachAgentModePreviewSurface: (Surface) -> Unit,
    onDetachAgentModePreviewSurface: (Surface) -> Unit,
) {
    when (block) {
        is AssistantResponseBlock.Text -> {
            if (!agentModePreviewVisible) {
                PendingAssistantResponseBlock(
                    text = block.text,
                    workspaceDirectory = workspaceDirectory,
                    allowRootImageRead = allowRootImageRead,
                    onOpenLink = onOpenLink,
                )
            }
        }

        is AssistantResponseBlock.ToolGroup -> {
            val visibleInvocations = if (agentModePreviewVisible) {
                block.toolInvocations.filterNot { it.isAgentModeDisplayInvocation() }
            } else {
                block.toolInvocations
            }
            val shouldShowAgentModePreview =
                !agentModePreviewVisible &&
                    isLastBlock &&
                    agentModeSelected &&
                    agentModeDisplayState.isActive &&
                    block.toolInvocations.any { it.toolName.equals("agent_display", ignoreCase = true) }
            if (shouldShowAgentModePreview) {
                AgentModePreviewPanel(
                    displayState = agentModeDisplayState,
                    toolInvocation = block.toolInvocations.lastOrNull(),
                    overlayText = "",
                    workspaceDirectory = workspaceDirectory,
                    allowRootImageRead = allowRootImageRead,
                    onOpenLink = onOpenLink,
                    onAttachSurface = onAttachAgentModePreviewSurface,
                    onDetachSurface = onDetachAgentModePreviewSurface,
                )
            } else if (visibleInvocations.isNotEmpty()) {
                ToolInvocationList(
                    toolInvocations = visibleInvocations,
                    stateKey = "$pendingToolInvocationStateKey-${block.id}",
                    autoExpand = isLastBlock,
                )
            }
        }

        is AssistantResponseBlock.Reasoning -> {
            if (hasVisibleReasoningStatus(block.trace)) {
                ReasoningTraceStatus(
                    trace = block.trace,
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

private fun AssistantResponseBlock.agentModeToolInvocations(): List<ChatToolInvocation> = when (this) {
    is AssistantResponseBlock.ToolGroup -> toolInvocations.filter { it.isAgentModeDisplayInvocation() }
    is AssistantResponseBlock.Reasoning -> trace.toolInvocations.filter { it.isAgentModeDisplayInvocation() }
    is AssistantResponseBlock.Text -> emptyList()
}

private fun ChatToolInvocation.isAgentModeDisplayInvocation(): Boolean =
    toolName.equals("agent_display", ignoreCase = true)

private fun List<AssistantResponseBlock>.firstAgentModeBlockIndex(): Int =
    indexOfFirst { it.agentModeToolInvocations().isNotEmpty() }

private fun List<AssistantResponseBlock>.lastTextBlockAfterAgentMode(): String? {
    val firstAgentModeBlockIndex = firstAgentModeBlockIndex()
    if (firstAgentModeBlockIndex < 0) {
        return null
    }
    return drop(firstAgentModeBlockIndex + 1)
        .filterIsInstance<AssistantResponseBlock.Text>()
        .lastOrNull { it.text.isNotBlank() }
        ?.text
}

private fun List<AssistantResponseBlock>.visibleText(): String =
    filterIsInstance<AssistantResponseBlock.Text>()
        .joinToString("\n\n") { it.text }

private fun List<AssistantResponseBlock>.hasVisiblePendingWork(): Boolean =
    any { block ->
        when (block) {
            is AssistantResponseBlock.Text -> block.text.isNotBlank()
            is AssistantResponseBlock.ToolGroup -> block.toolInvocations.isNotEmpty()
            is AssistantResponseBlock.Reasoning -> hasVisibleReasoningStatus(block.trace)
        }
    }

private fun List<AssistantResponseBlock>.workStartedAtMillis(): Long? =
    flatMap { block ->
        when (block) {
            is AssistantResponseBlock.Text -> emptyList()
            is AssistantResponseBlock.ToolGroup -> block.toolInvocations.mapNotNull {
                it.startedAtMillis.takeIf { timestamp -> timestamp > 0L }
            }
            is AssistantResponseBlock.Reasoning -> buildList {
                block.trace.startedAtMillis.takeIf { it > 0L }?.let(::add)
                block.trace.chunks.forEach { chunk ->
                    chunk.createdAtMillis.takeIf { it > 0L }?.let(::add)
                }
                block.trace.toolInvocations.forEach { invocation ->
                    invocation.startedAtMillis.takeIf { it > 0L }?.let(::add)
                }
            }
        }
    }
        .filter { it >= MinimumWallClockMillis }
        .minOrNull()

private fun fallbackDurationMillis(startedRealtimeMillis: Long): Long =
    (SystemClock.elapsedRealtime() - startedRealtimeMillis).coerceAtLeast(1_000L)

private fun buildConversationListItems(
    messages: List<ChatMessage>,
): List<ConversationListItem> = buildList {
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        when (message.displayKind) {
            MessageDisplayKind.HiddenContext -> {
                index += 1
                continue
            }

            MessageDisplayKind.CompactStatus -> {
                add(ConversationListItem.CompactStatus(message))
                index += 1
                continue
            }

            MessageDisplayKind.Standard -> Unit
        }
        val responseGroupId = message.responseGroupId
        if (
            message.author == MessageAuthor.Agent &&
            (!responseGroupId.isNullOrBlank() || isLegacyAssistantGroupStart(messages, index))
        ) {
            val groupedMessages = buildList {
                var groupIndex = index
                while (groupIndex < messages.size) {
                    val candidate = messages[groupIndex]
                    if (candidate.author != MessageAuthor.Agent) {
                        break
                    }
                    val matchesGroup = if (!responseGroupId.isNullOrBlank()) {
                        candidate.responseGroupId == responseGroupId
                    } else {
                        val offset = groupIndex - index
                        candidate.responseGroupId.isNullOrBlank() &&
                            candidate.createdAtMillis == message.createdAtMillis + offset
                    }
                    if (!matchesGroup) {
                        break
                    }
                    add(candidate)
                    groupIndex += 1
                }
            }
            if (groupedMessages.isNotEmpty()) {
                add(ConversationListItem.AssistantGroup(groupedMessages))
                index += groupedMessages.size
                continue
            }
        }
        add(ConversationListItem.Message(message))
        index += 1
    }
}

private data class CompactCommandSuggestion(
    val percent: Int?,
)

private fun compactCommandSuggestion(messages: List<ChatMessage>): CompactCommandSuggestion {
    val visibleMessages = messages.filter {
        it.displayKind != MessageDisplayKind.HiddenContext &&
            it.displayKind != MessageDisplayKind.CompactStatus
    }
    if (visibleMessages.size < 2) return CompactCommandSuggestion(percent = null)
    val estimatedChars = visibleMessages.sumOf { message ->
        message.text.length +
            message.attachments.sumOf { attachment ->
                attachment.name.length + attachment.mimeType.length + attachment.workspacePath.length
            } +
            message.toolInvocations.sumOf { invocation ->
                invocation.toolName.length + invocation.argumentsJson.length + invocation.outputJson.length
            }
    }
    val percent = ((estimatedChars * 100L) / 120_000L).toInt().coerceIn(1, 100)
    return CompactCommandSuggestion(percent = percent)
}

@Composable
private fun CompactStatusDivider(
    text: String,
    isRunning: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AetherOnSurfaceVariant.copy(alpha = 0.08f)),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.Check,
                contentDescription = null,
                tint = AetherOnSurfaceVariant.copy(alpha = 0.82f),
                modifier = Modifier.size(15.dp),
            )
            if (isRunning) {
                ShimmerStatusText(
                    text = text,
                    modifier = Modifier.widthIn(max = 190.dp),
                    travelDurationMillis = 2200,
                    pauseDurationMillis = 700,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AetherOnSurfaceVariant.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun CompactCommandSuggestion(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AetherSurfaceHigh.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = stringResource(R.string.chat_compact),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
            maxLines = 1,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun isLegacyAssistantGroupStart(
    messages: List<ChatMessage>,
    index: Int,
): Boolean {
    val message = messages.getOrNull(index) ?: return false
    if (
        message.author != MessageAuthor.Agent ||
        !message.responseGroupId.isNullOrBlank()
    ) {
        return false
    }
    val next = messages.getOrNull(index + 1) ?: return false
    return next.author == MessageAuthor.Agent &&
        next.responseGroupId.isNullOrBlank() &&
        next.createdAtMillis == message.createdAtMillis + 1
}

@Composable
private fun PendingSessionInputBubble(
    pendingInput: PendingSessionInput,
) {
    val modeLabel = when (pendingInput.mode) {
        SessionFollowUpMode.Queue -> stringResource(R.string.chat_pending_input_queued)
        SessionFollowUpMode.Steer -> stringResource(R.string.chat_pending_input_steering)
    }
    val attachmentLabel = when (pendingInput.attachmentCount) {
        0 -> null
        1 -> stringResource(R.string.chat_attachment_count_one)
        else -> stringResource(R.string.chat_attachment_count_other, pendingInput.attachmentCount)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
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
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurfaceVariant,
                )
                attachmentLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
            Text(
                text = pendingInput.preview.ifBlank {
                    stringResource(R.string.chat_additional_context)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface,
            )
        }
    }
}

@Composable
private fun ConversationComposerOverlay(
    modifier: Modifier = Modifier,
    conversationStateKey: String,
    onBodyHeightChanged: (Int) -> Unit,
    value: String,
    attachments: List<ChatAttachment>,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    isSending: Boolean,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    compactSuggestionText: String,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onPauseGeneration: () -> Unit,
    onDismissTermuxSetupNotice: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val bottomLift by animateDpAsState(
        targetValue = if (imeVisible) 12.dp else 18.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_bottom_lift",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
            .navigationBarsPadding()
            .padding(bottom = bottomLift),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onBodyHeightChanged(it.height) },
        ) {
            key(conversationStateKey) {
                ConversationComposerBar(
                    conversationStateKey = conversationStateKey,
                    value = value,
                    attachments = attachments,
                    availableSkills = availableSkills,
                    availableMcpServers = availableMcpServers,
                    selectedSkillIds = selectedSkillIds,
                    selectedMcpServerIds = selectedMcpServerIds,
                    agentModeAvailable = agentModeAvailable,
                    agentModeSelected = agentModeSelected,
                    isEditing = isEditing,
                    termuxSetupState = termuxSetupState,
                    isSending = isSending,
                    showStarterPromptHint = showStarterPromptHint,
                    showTermuxSetupNotice = showTermuxSetupNotice,
                    compactSuggestionText = compactSuggestionText,
                    onValueChange = onValueChange,
                    onRemoveAttachment = onRemoveAttachment,
                    onSetSkillSelected = onSetSkillSelected,
                    onSetMcpServerSelected = onSetMcpServerSelected,
                    onSetAgentModeSelected = onSetAgentModeSelected,
                    onCancelEdit = onCancelEdit,
                    onPickImages = onPickImages,
                    onPickFiles = onPickFiles,
                    onRequestTermuxPermission = onRequestTermuxPermission,
                    onOpenAppPermissions = onOpenAppPermissions,
                    onOpenTermuxSettings = onOpenTermuxSettings,
                    onOpenTermux = onOpenTermux,
                    onInstallTermux = onInstallTermux,
                    onRefreshTermuxSetup = onRefreshTermuxSetup,
                    onPauseGeneration = onPauseGeneration,
                    onDismissTermuxSetupNotice = onDismissTermuxSetupNotice,
                    onDismissStarterPromptHint = onDismissStarterPromptHint,
                    onFocusChanged = onFocusChanged,
                    onSend = onSend,
                    onQueueFollowUp = onQueueFollowUp,
                    onSteerFollowUp = onSteerFollowUp,
                )
            }
        }
    }
}

@Composable
private fun ConversationComposerBar(
    modifier: Modifier = Modifier,
    conversationStateKey: String,
    value: String,
    attachments: List<ChatAttachment>,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    isSending: Boolean,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    compactSuggestionText: String,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onPauseGeneration: () -> Unit,
    onDismissTermuxSetupNotice: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
) {
    var attachmentMenuExpanded by remember(conversationStateKey) { mutableStateOf(false) }
    val attachmentMenuVisibility = remember(conversationStateKey) { MutableTransitionState(false) }
    attachmentMenuVisibility.targetState = attachmentMenuExpanded
    val attachmentMenuActionScope = rememberCoroutineScope()
    var followUpMenuExpanded by remember(conversationStateKey) { mutableStateOf(false) }
    val followUpMenuVisibility = remember(conversationStateKey) { MutableTransitionState(false) }
    followUpMenuVisibility.targetState = followUpMenuExpanded
    var textFieldFocused by remember { mutableStateOf(false) }
    var measuredTextLineCount by remember { mutableIntStateOf(1) }
    var measuredTextHeight by remember { mutableStateOf(22.dp) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val selectedSkillSet = remember(selectedSkillIds) { selectedSkillIds.toSet() }
    val selectedMcpServerSet = remember(selectedMcpServerIds) { selectedMcpServerIds.toSet() }
    val selectedSkillActions = remember(availableSkills, selectedSkillSet) {
        availableSkills.filter { selectedSkillSet.contains(it.id) }
    }
    val selectedMcpActions = remember(availableMcpServers, selectedMcpServerSet) {
        availableMcpServers.filter { selectedMcpServerSet.contains(it.id) }
    }
    val hasSelectedActions = selectedSkillActions.isNotEmpty() || selectedMcpActions.isNotEmpty() || agentModeSelected
    val composerPlaceholder = when {
        value.isNotBlank() -> ""
        attachments.isNotEmpty() -> stringResource(R.string.chat_add_note)
        agentModeSelected && selectedSkillActions.isEmpty() && selectedMcpActions.isEmpty() ->
            stringResource(R.string.chat_ask_agent_mode)
        selectedSkillActions.size + selectedMcpActions.size == 1 && !agentModeSelected -> {
            selectedSkillActions.firstOrNull()?.quickActionLabel()
                ?: selectedMcpActions.firstOrNull()?.quickActionLabel()
                ?: stringResource(R.string.chat_reply_to_aether)
        }
        hasSelectedActions -> stringResource(R.string.chat_ask_with_selected_tools)
        else -> stringResource(R.string.chat_ask_aether)
    }
    val hasDraft = value.isNotBlank() || attachments.isNotEmpty()
    val showCompactSuggestion = compactSuggestionText.isNotBlank() &&
        attachments.isEmpty() &&
        value.isNotBlank() &&
        "/compact".startsWith(value.trim(), ignoreCase = true)
    val canSendDraft = attachments.all { it.workspaceState == AttachmentWorkspaceState.Ready }
    val showPauseButton = isSending && !hasDraft
    val showSubmitButton = !isSending || hasDraft
    val keepPlusSeparated = value.isNotBlank() || hasSelectedActions
    val plusSeparated = keepPlusSeparated || (textFieldFocused && imeVisible)
    val explicitTextLineCount = if (value.isBlank()) {
        1
    } else {
        value.count { it == '\n' } + 1
    }
    val composerTextLineCount = if (value.isBlank()) {
        1
    } else {
        maxOf(explicitTextLineCount, measuredTextLineCount).coerceIn(1, 5)
    }
    val isMultilineComposer = composerTextLineCount > 1
    val composerTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = AetherOnSurface,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    val fieldTopPadding = when {
        hasSelectedActions -> 12.dp
        isMultilineComposer -> 12.dp
        else -> 8.dp
    }
    val fieldBottomPadding = when {
        hasSelectedActions -> 12.dp
        isMultilineComposer -> 12.dp
        else -> 8.dp
    }
    LaunchedEffect(plusSeparated) {
        onFocusChanged(plusSeparated)
    }
    fun runAfterAttachmentMenuDismiss(action: () -> Unit) {
        attachmentMenuExpanded = false
        action()
    }
    val composerHorizontalPadding by animateDpAsState(
        targetValue = when {
            plusSeparated -> 14.dp
            hasSelectedActions -> 18.dp
            else -> 30.dp
        },
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_horizontal_padding",
    )
    val fieldStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 50.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_start",
    )
    val fieldContentStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 18.dp else 52.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_content_start",
    )
    val fieldMinHeight by animateDpAsState(
        targetValue = maxOf(
            if (plusSeparated) 56.dp else 50.dp,
            measuredTextHeight + fieldTopPadding + fieldBottomPadding,
        ),
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_min_height",
    )
    val plusShadowElevation by animateDpAsState(
        targetValue = if (plusSeparated) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_plus_shadow",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = composerHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTermuxSetupNotice) {
            TermuxSetupNotice(
                setupState = termuxSetupState,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
                onDismiss = onDismissTermuxSetupNotice,
            )
        }
        if (showStarterPromptHint) {
            SurfaceNotice(
                title = stringResource(R.string.chat_first_prompt_ready_title),
                subtitle = stringResource(R.string.chat_first_prompt_ready_subtitle),
                actionLabel = stringResource(R.string.common_hide),
                onAction = onDismissStarterPromptHint,
                actionEnabled = true,
            )
        }
        if (isEditing) {
            SurfaceNotice(
                title = stringResource(R.string.chat_editing_earlier_message_title),
                subtitle = stringResource(R.string.chat_editing_earlier_message_subtitle),
                actionLabel = stringResource(R.string.common_cancel),
                onAction = onCancelEdit,
                actionEnabled = true,
            )
        }
        AnimatedVisibility(
            visible = showCompactSuggestion,
            enter = fadeIn(animationSpec = tween(durationMillis = 160, easing = ChatGptMotionEasing)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 220, easing = ChatGptMotionEasing),
                    initialOffsetY = { it / 3 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120, easing = ChatGptMotionEasing)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 180, easing = ChatGptMotionEasing),
                    targetOffsetY = { it / 3 },
                ),
        ) {
            CompactCommandSuggestion(
                onClick = { onValueChange("/compact") },
                text = compactSuggestionText,
            )
        }
        if (attachments.isNotEmpty()) {
            ComposerAttachmentTray(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
            )
        }

        val fieldShape = if (plusSeparated) ComposerFocusedCardShape else ComposerCardShape
        val fieldControlAlignment = if (isMultilineComposer) Alignment.Bottom else Alignment.CenterVertically
        val fieldTextAlignment = if (isMultilineComposer) Alignment.TopStart else Alignment.CenterStart
        val plusButtonAlignment = if (isMultilineComposer || hasSelectedActions) Alignment.BottomStart else Alignment.CenterStart
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = fieldStartPadding)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = 8.dp)
                        .blur(
                            radius = 22.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        )
                        .clip(fieldShape)
                        .background(ChatGptComposerShadow),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = fieldMinHeight)
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 320, easing = ChatGptMotionEasing),
                        )
                        .clip(fieldShape)
                        .background(AetherSurface)
                        .padding(
                            start = fieldContentStartPadding,
                            end = 8.dp,
                            top = fieldTopPadding,
                            bottom = fieldBottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (hasSelectedActions) 10.dp else 0.dp),
                ) {
                    AnimatedVisibility(
                        visible = hasSelectedActions,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 220, easing = ChatGptMotionEasing),
                        ) + slideInVertically(
                            animationSpec = tween(durationMillis = 280, easing = ChatGptMotionEasing),
                            initialOffsetY = { -it / 2 },
                        ),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 160, easing = ChatGptMotionEasing),
                        ) + slideOutVertically(
                            animationSpec = tween(durationMillis = 220, easing = ChatGptMotionEasing),
                            targetOffsetY = { -it / 3 },
                        ),
                    ) {
                        ComposerActionTray(
                            modifier = Modifier.fillMaxWidth(),
                            skills = selectedSkillActions,
                            mcpServers = selectedMcpActions,
                            agentModeSelected = agentModeSelected,
                            onRemoveSkill = { skillId -> onSetSkillSelected(skillId, false) },
                            onRemoveMcpServer = { serverId -> onSetMcpServerSelected(serverId, false) },
                            onRemoveAgentMode = { onSetAgentModeSelected(false) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = fieldControlAlignment,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = measuredTextHeight.coerceAtLeast(22.dp)),
                            contentAlignment = fieldTextAlignment,
                        ) {
                            if (value.isBlank()) {
                                Text(
                                    text = composerPlaceholder,
                                    style = composerTextStyle,
                                    color = Color(0xFF8C8C8C),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (textFieldFocused != focusState.isFocused) {
                                            textFieldFocused = focusState.isFocused
                                        }
                                    },
                                textStyle = composerTextStyle,
                                maxLines = 5,
                                onTextLayout = { textLayoutResult ->
                                    val lineCount = textLayoutResult.lineCount.coerceIn(1, 5)
                                    if (measuredTextLineCount != lineCount) {
                                        measuredTextLineCount = lineCount
                                    }
                                    val visibleLineBottom = textLayoutResult.getLineBottom(lineCount - 1)
                                    val visibleLineTop = textLayoutResult.getLineTop(0)
                                    val textHeight = with(density) {
                                        (visibleLineBottom - visibleLineTop).toDp()
                                    }
                                    if (measuredTextHeight != textHeight) {
                                        measuredTextHeight = textHeight
                                    }
                                },
                                cursorBrush = SolidColor(AetherOnSurface),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (showPauseButton) {
                            ComposerPauseButton(
                                onClick = onPauseGeneration,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (showSubmitButton) {
                            Box {
                                ComposerSubmitButton(
                                    hasDraft = hasDraft,
                                    canSendDraft = canSendDraft,
                                    isSending = isSending,
                                    onClick = {
                                        if (!hasDraft || !canSendDraft) return@ComposerSubmitButton
                                        if (isSending) {
                                            followUpMenuExpanded = true
                                        } else {
                                            onSend()
                                        }
                                    },
                                )
                                if (isSending && (followUpMenuVisibility.currentState || followUpMenuVisibility.targetState)) {
                                    Popup(
                                        alignment = Alignment.BottomEnd,
                                        offset = with(density) {
                                            IntOffset(0, -12.dp.roundToPx())
                                        },
                                        onDismissRequest = { followUpMenuExpanded = false },
                                        properties = PopupProperties(
                                            focusable = true,
                                            dismissOnBackPress = true,
                                            dismissOnClickOutside = true,
                                        ),
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = followUpMenuVisibility,
                                            enter = fadeIn() +
                                                scaleIn(
                                                    initialScale = 0.92f,
                                                    transformOrigin = TransformOrigin(1f, 1f),
                                                ) +
                                                slideInVertically(initialOffsetY = { it / 10 }),
                                            exit = fadeOut() +
                                                scaleOut(
                                                    targetScale = 0.96f,
                                                    transformOrigin = TransformOrigin(1f, 1f),
                                                ) +
                                                slideOutVertically(targetOffsetY = { it / 12 }),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .widthIn(min = 252.dp, max = 284.dp)
                                                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                                                    .clip(RoundedCornerShape(30.dp))
                                                    .background(AetherSurface)
                                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                ComposerPlusMenuRow(
                                                    title = stringResource(R.string.branch_steer_current_run),
                                                    icon = Icons.Rounded.AutoAwesome,
                                                    iconTint = Color(0xFF8D6C2F),
                                                    iconContainerColor = Color(0xFFFFF3DE),
                                                    onClick = {
                                                        followUpMenuExpanded = false
                                                        onSteerFollowUp()
                                                    },
                                                )
                                                ComposerPlusMenuRow(
                                                    title = stringResource(R.string.branch_queue_next_turn),
                                                    icon = Icons.Rounded.ArrowUpward,
                                                    iconTint = Color(0xFF2F6DA3),
                                                    iconContainerColor = Color(0xFFEAF2FF),
                                                    onClick = {
                                                        followUpMenuExpanded = false
                                                        onQueueFollowUp()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.align(plusButtonAlignment)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(plusShadowElevation, CircleShape, ambientColor = ChatGptControlShadow, spotColor = ChatGptControlShadow)
                        .clip(CircleShape)
                        .background(if (plusSeparated) AetherSurface else Color.Transparent)
                        .clickable(onClick = { attachmentMenuExpanded = !attachmentMenuExpanded }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.chat_add_attachment_or_tool),
                        tint = AetherOnSurface,
                        modifier = Modifier.size(27.dp),
                    )
                }

                if (attachmentMenuVisibility.currentState || attachmentMenuVisibility.targetState) {
                    Popup(
                        alignment = Alignment.BottomStart,
                        offset = with(density) {
                            IntOffset(0, -42.dp.roundToPx())
                        },
                        onDismissRequest = { attachmentMenuExpanded = false },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = attachmentMenuVisibility,
                            enter = fadeIn() +
                                scaleIn(
                                    initialScale = 0.92f,
                                    transformOrigin = TransformOrigin(0f, 1f),
                                ) +
                                slideInVertically(initialOffsetY = { it / 10 }),
                            exit = fadeOut() +
                                scaleOut(
                                    targetScale = 0.96f,
                                    transformOrigin = TransformOrigin(0f, 1f),
                                ) +
                                slideOutVertically(targetOffsetY = { it / 12 }),
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 284.dp, max = 304.dp)
                                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(AetherSurface),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = ComposerPlusMenuMaxHeight)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    ComposerPlusMenuRow(
                                        title = stringResource(R.string.chat_photos),
                                        icon = Icons.Rounded.Image,
                                        iconTint = Color(0xFF4E8D5A),
                                        iconContainerColor = AetherSurfaceHigh,
                                        onClick = {
                                            runAfterAttachmentMenuDismiss(onPickImages)
                                        },
                                    )
                                    ComposerPlusMenuRow(
                                        title = stringResource(R.string.chat_files),
                                        icon = Icons.Rounded.AttachFile,
                                        iconTint = AetherOnSurface,
                                        iconContainerColor = AetherSurfaceHigh,
                                        onClick = {
                                            runAfterAttachmentMenuDismiss(onPickFiles)
                                        },
                                    )
                                    if (agentModeAvailable || availableSkills.isNotEmpty() || availableMcpServers.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    if (agentModeAvailable) {
                                        ComposerPlusMenuRow(
                                            title = stringResource(R.string.agent_mode_label),
                                            icon = LucideIcons.MousePointer2,
                                            selected = agentModeSelected,
                                            iconTint = Color(0xFF6D5CFF),
                                            iconContainerColor = AetherSurfaceHigh,
                                            onClick = {
                                                runAfterAttachmentMenuDismiss {
                                                    onSetAgentModeSelected(!agentModeSelected)
                                                }
                                            },
                                        )
                                    }
                                    availableSkills.forEach { skill ->
                                        val selected = selectedSkillSet.contains(skill.id)
                                        ComposerPlusMenuRow(
                                            title = skill.quickActionLabel(),
                                            icon = Icons.Rounded.Extension,
                                            selected = selected,
                                            iconTint = Color(0xFF9C6B2F),
                                            iconContainerColor = AetherSurfaceHigh,
                                            onClick = {
                                                runAfterAttachmentMenuDismiss {
                                                    onSetSkillSelected(skill.id, !selected)
                                                }
                                            },
                                        )
                                    }
                                    availableMcpServers.forEach { server ->
                                        val selected = selectedMcpServerSet.contains(server.id)
                                        val isStdIo = server.transport is McpTransportConfig.StdIo
                                        ComposerPlusMenuRow(
                                            title = server.quickActionLabel(),
                                            icon = if (isStdIo) Icons.Rounded.Terminal else Icons.Rounded.Cloud,
                                            selected = selected,
                                            iconTint = if (isStdIo) Color(0xFF2F6DA3) else Color(0xFF2A9C9A),
                                            iconContainerColor = AetherSurfaceHigh,
                                            onClick = {
                                                runAfterAttachmentMenuDismiss {
                                                    onSetMcpServerSelected(server.id, !selected)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPauseButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ChatGptPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = 0.5.dp)
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun ComposerSubmitButton(
    hasDraft: Boolean,
    canSendDraft: Boolean,
    isSending: Boolean,
    onClick: () -> Unit,
) {
    val enabled = hasDraft && canSendDraft
    val buttonColor = if (enabled) {
        ChatGptPurple
    } else {
        AetherSurfaceHigher
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = if (isSending) {
                stringResource(R.string.common_send_follow_up)
            } else {
                stringResource(R.string.common_send)
            },
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ComposerActionTray(
    modifier: Modifier = Modifier,
    skills: List<InstalledSkill>,
    mcpServers: List<McpServerConfig>,
    agentModeSelected: Boolean,
    onRemoveSkill: (String) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onRemoveAgentMode: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (agentModeSelected) {
            ComposerActionChip(
                label = stringResource(R.string.agent_mode_label),
                icon = LucideIcons.MousePointer2,
                onRemove = onRemoveAgentMode,
            )
        }
        skills.forEach { skill ->
            ComposerActionChip(
                label = skill.quickActionLabel(),
                icon = Icons.Rounded.Extension,
                onRemove = { onRemoveSkill(skill.id) },
            )
        }
        mcpServers.forEach { server ->
            ComposerActionChip(
                label = server.quickActionLabel(),
                icon = if (server.transport is McpTransportConfig.StdIo) {
                    Icons.Rounded.Terminal
                } else {
                    Icons.Rounded.Cloud
                },
                onRemove = { onRemoveMcpServer(server.id) },
            )
        }
    }
}

@Composable
private fun AgentModePreviewPanel(
    displayState: AgentModeDisplayState,
    toolInvocation: ChatToolInvocation?,
    overlayText: String = "",
    workspaceDirectory: String = "",
    allowRootImageRead: Boolean = false,
    onOpenLink: (String) -> Unit = {},
    onAttachSurface: (Surface) -> Unit,
    onDetachSurface: (Surface) -> Unit,
) {
    val bitmap = remember(displayState.latestPreviewPath, displayState.lastUpdatedMillis) {
        displayState.latestPreviewPath
            .takeIf { it.isNotBlank() }
            ?.let { BitmapFactory.decodeFile(it) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (toolInvocation != null) {
            AgentModePreviewToolStatus(toolInvocation = toolInvocation)
        } else {
            AgentModePreviewHeader(displayState = displayState)
        }
        if (displayState.isActive || bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(agentModePreviewBackdropBrush()),
                contentAlignment = Alignment.Center,
            ) {
                var previewSize by remember { mutableStateOf(IntSize.Zero) }
                val density = LocalDensity.current
                val imagePaddingPx = with(density) { 10.dp.toPx() }
                val cursorOffset = remember(
                    previewSize,
                    displayState.width,
                    displayState.height,
                    displayState.cursorX,
                    displayState.cursorY,
                ) {
                    resolveAgentModeCursorOffset(
                        previewSize = previewSize,
                        imagePaddingPx = imagePaddingPx,
                        displayWidth = displayState.width,
                        displayHeight = displayState.height,
                        cursorX = displayState.cursorX,
                        cursorY = displayState.cursorY,
                    )
                }
                val animationDurationMillis = displayState.cursorAnimationDurationMillis.coerceIn(80, 1_200)
                val animatedCursorOffset by animateIntOffsetAsState(
                    targetValue = cursorOffset,
                    animationSpec = tween(durationMillis = animationDurationMillis, easing = ChatGptMotionEasing),
                    label = "agent_mode_cursor_offset",
                )
                if (displayState.isActive) {
                    AgentModeLivePreviewSurface(
                        displayState = displayState,
                        onAttachSurface = onAttachSurface,
                        onDetachSurface = onDetachSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(10.dp)
                            .aspectRatio(
                                displayState.width.coerceAtLeast(1).toFloat() /
                                    displayState.height.coerceAtLeast(1).toFloat(),
                                matchHeightConstraintsFirst = true,
                            )
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else if (bitmap != null) {
                    Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.chat_agent_mode_virtual_display),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .onSizeChanged { previewSize = it },
                ) {
                    AgentModeCursor(
                        modifier = Modifier
                            .offset {
                                val tipInsetPx = with(density) { 5.dp.roundToPx() }
                                IntOffset(animatedCursorOffset.x - tipInsetPx, animatedCursorOffset.y - tipInsetPx)
                            }
                            .size(30.dp),
                    )
                    if (overlayText.isNotBlank()) {
                        val bubbleOffset = remember(
                            cursorOffset,
                            previewSize,
                            density,
                        ) {
                            resolveAgentModeBubbleOffset(
                                cursorOffset = cursorOffset,
                                previewSize = previewSize,
                                density = density,
                            )
                        }
                        val animatedBubbleOffset by animateIntOffsetAsState(
                            targetValue = bubbleOffset,
                            animationSpec = tween(durationMillis = animationDurationMillis, easing = ChatGptMotionEasing),
                            label = "agent_mode_bubble_offset",
                        )
                        AgentModeCursorTextBubble(
                            text = overlayText,
                            workspaceDirectory = workspaceDirectory,
                            allowRootImageRead = allowRootImageRead,
                            onOpenLink = onOpenLink,
                            modifier = Modifier.offset { animatedBubbleOffset },
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.chat_agent_mode_preview_pending),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgentModeLivePreviewSurface(
    displayState: AgentModeDisplayState,
    onAttachSurface: (Surface) -> Unit,
    onDetachSurface: (Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnAttachSurface by rememberUpdatedState(onAttachSurface)
    val latestOnDetachSurface by rememberUpdatedState(onDetachSurface)
    var attachedSurface by remember { mutableStateOf<Surface?>(null) }

    fun fixedWidth() = displayState.width.coerceAtLeast(1)
    fun fixedHeight() = displayState.height.coerceAtLeast(1)
    fun attach(surfaceTexture: SurfaceTexture?) {
        if (surfaceTexture == null || attachedSurface?.isValid == true) return
        surfaceTexture.setDefaultBufferSize(fixedWidth(), fixedHeight())
        val surface = Surface(surfaceTexture)
        attachedSurface = surface
        latestOnAttachSurface(surface)
    }

    fun detach() {
        val surface = attachedSurface ?: return
        attachedSurface = null
        latestOnDetachSurface(surface)
        surface.release()
    }

    DisposableEffect(Unit) {
        onDispose {
            detach()
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            TextureView(context).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        attach(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        surfaceTexture.setDefaultBufferSize(fixedWidth(), fixedHeight())
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        detach()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            view.surfaceTexture?.setDefaultBufferSize(fixedWidth(), fixedHeight())
            if (displayState.isActive) {
                attach(view.surfaceTexture)
            }
        },
    )
}

@Composable
private fun AgentModeCursor(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.mouse_pointer_2_white_fill),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun AgentModeCursorTextBubble(
    text: String,
    workspaceDirectory: String,
    allowRootImageRead: Boolean,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(18.dp, RoundedCornerShape(12.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(12.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = 104.dp)
                .verticalScroll(scrollState),
        ) {
            StreamingMarkdownContent(
                markdown = text,
                workspaceDirectory = workspaceDirectory,
                allowRootImageRead = allowRootImageRead,
                onLinkClick = onOpenLink,
            )
        }
    }
}

private fun resolveAgentModeCursorOffset(
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
    val cursorFractionX = cursorX?.let { it.toFloat() / sourceWidth } ?: 0.58f
    val cursorFractionY = cursorY?.let { it.toFloat() / sourceHeight } ?: 0.56f
    return IntOffset(
        x = (renderedLeft + renderedWidth * cursorFractionX.coerceIn(0f, 1f)).roundToInt(),
        y = (renderedTop + renderedHeight * cursorFractionY.coerceIn(0f, 1f)).roundToInt(),
    )
}

private fun resolveAgentModeBubbleOffset(
    cursorOffset: IntOffset,
    previewSize: IntSize,
    density: androidx.compose.ui.unit.Density,
): IntOffset {
    val bubbleMaxWidthPx = with(density) { 320.dp.roundToPx() }
    val bubbleMaxHeightPx = with(density) { 128.dp.roundToPx() }
    val horizontalGapPx = with(density) { 34.dp.roundToPx() }
    val verticalGapPx = with(density) { 34.dp.roundToPx() }
    val edgePaddingPx = with(density) { 8.dp.roundToPx() }
    val targetY = if (cursorOffset.y < previewSize.height / 2) {
        cursorOffset.y + verticalGapPx
    } else {
        cursorOffset.y - verticalGapPx - bubbleMaxHeightPx
    }
    return IntOffset(
        x = (cursorOffset.x + horizontalGapPx).coerceIn(
            edgePaddingPx,
            (previewSize.width - bubbleMaxWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
        ),
        y = targetY.coerceIn(
            edgePaddingPx,
            (previewSize.height - edgePaddingPx).coerceAtLeast(edgePaddingPx),
        ),
    )
}

@Composable
private fun AgentModePreviewHeader(
    displayState: AgentModeDisplayState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideIcons.MousePointer2,
            contentDescription = null,
            tint = Color(0xFF6D5CFF),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.agent_mode_label),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
        if (displayState.isLivePreviewActive) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE5F8EE))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF20A564)),
                )
                Text(
                    text = stringResource(R.string.chat_live),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF137A49),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = displayState.displayId?.let {
                stringResource(R.string.agent_mode_display_id, it)
            } ?: stringResource(R.string.chat_standby),
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
        )
    }
}

@Composable
private fun AgentModePreviewToolStatus(
    toolInvocation: ChatToolInvocation,
) {
    val label = formatPendingAgentModeToolLabel(toolInvocation)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (toolInvocation.toolName == "agent_display") {
                LucideIcons.MousePointer2
            } else {
                Icons.Rounded.AutoAwesome
            },
            contentDescription = null,
            tint = Color(0xFF5D7CFF),
            modifier = Modifier.size(16.dp),
        )
        if (toolInvocation.isRunning) {
            ShimmerStatusText(
                text = label,
                modifier = Modifier.weight(1f),
                travelDurationMillis = 2600,
                pauseDurationMillis = 900,
            )
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
private fun formatPendingAgentModeToolLabel(toolInvocation: ChatToolInvocation): String {
    val arguments = parseJsonObject(toolInvocation.argumentsJson)
    return formatPendingToolTitle(
        toolName = toolInvocation.toolName,
        isRunning = toolInvocation.isRunning,
        arguments = arguments,
    )
}

@Composable
private fun formatPendingToolTitle(
    toolName: String,
    isRunning: Boolean,
    arguments: JSONObject?,
): String = when (toolName.lowercase()) {
    "bash" -> toolStatusLabel(isRunning, R.string.tool_title_bash_running, R.string.tool_title_bash_done)
    "fetch_bash_output" -> toolStatusLabel(isRunning, R.string.tool_title_fetch_bash_output_running, R.string.tool_title_fetch_bash_output_done)
    "kill_bash" -> toolStatusLabel(isRunning, R.string.tool_title_kill_bash_running, R.string.tool_title_kill_bash_done)
    "sleep" -> toolStatusLabel(isRunning, R.string.tool_title_sleep_running, R.string.tool_title_sleep_done)
    "read" -> toolStatusLabel(isRunning, R.string.tool_title_read_running, R.string.tool_title_read_done)
    "edit" -> toolStatusLabel(isRunning, R.string.tool_title_edit_running, R.string.tool_title_edit_done)
    "write" -> toolStatusLabel(isRunning, R.string.tool_title_write_running, R.string.tool_title_write_done)
    "grep" -> toolStatusLabel(isRunning, R.string.tool_title_grep_running, R.string.tool_title_grep_done)
    "find" -> toolStatusLabel(isRunning, R.string.tool_title_find_running, R.string.tool_title_find_done)
    "ls" -> toolStatusLabel(isRunning, R.string.tool_title_ls_running, R.string.tool_title_ls_done)
    "analyze_image" -> toolStatusLabel(isRunning, R.string.tool_title_analyze_image_running, R.string.tool_title_analyze_image_done)
    "tavily_search" -> formatArgumentDrivenToolTitle(
        isRunning = isRunning,
        runningVerbRes = R.string.tool_title_searching,
        doneVerbRes = R.string.tool_title_searched,
        subject = arguments?.optString("query").orEmpty(),
        fallbackRes = R.string.tool_title_tavily_search_fallback,
    )
    "fetch_web_url" -> formatArgumentDrivenToolTitle(
        isRunning = isRunning,
        runningVerbRes = R.string.tool_title_fetching,
        doneVerbRes = R.string.tool_title_fetched,
        subject = arguments?.optString("url").orEmpty(),
        fallbackRes = R.string.tool_title_web_page_fallback,
    )
    "aether_config_get",
    "aether_config_set",
    "aether_skill_manage",
    "aether_mcp_manage",
    "aether_termux_manage",
    "aether_agent_mode_manage",
    "aether_scheduled_task_manage",
    "aether_developer_manage" -> formatAetherToolTitle(toolName, isRunning, arguments)
    "agent_display" -> formatAgentDisplayToolTitle(isRunning, arguments)
    else -> if (isRunning) {
        stringResource(R.string.tool_title_using_tool, toolName)
    } else {
        stringResource(R.string.tool_title_used_tool, toolName)
    }
}

@Composable
private fun toolStatusLabel(
    isRunning: Boolean,
    runningRes: Int,
    doneRes: Int,
): String = stringResource(if (isRunning) runningRes else doneRes)

@Composable
private fun formatArgumentDrivenToolTitle(
    isRunning: Boolean,
    runningVerbRes: Int,
    doneVerbRes: Int,
    subject: String,
    fallbackRes: Int,
): String {
    val action = stringResource(if (isRunning) runningVerbRes else doneVerbRes)
    val normalizedSubject = subject.trim()
    if (normalizedSubject.isBlank()) {
        return stringResource(R.string.tool_title_action_subject, action, stringResource(fallbackRes))
    }
    val clipped = normalizedSubject.take(72)
    val displaySubject = if (normalizedSubject.length > 72) "$clipped..." else clipped
    return stringResource(R.string.tool_title_action_subject, action, displaySubject)
}

@Composable
private fun formatAetherToolTitle(
    toolName: String,
    isRunning: Boolean,
    arguments: JSONObject?,
): String {
    val action = arguments?.optString("action").orEmpty().trim()
    return when (toolName.lowercase()) {
        "aether_config_get" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_reading, R.string.tool_title_read, formatAetherCategories(arguments), R.string.tool_title_aether_settings_fallback)
        "aether_config_set" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_updating, R.string.tool_title_updated, arguments?.optString("category").orEmpty(), R.string.tool_title_aether_settings_fallback)
        "aether_skill_manage" -> when (action.lowercase()) {
            "install_remote" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_installing, R.string.tool_title_installed, arguments?.optString("url").orEmpty(), R.string.tool_title_agent_skill_fallback)
            "remove" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_removing, R.string.tool_title_removed, optAetherString(arguments, "skill_id", "skillId"), R.string.tool_title_agent_skill_fallback)
            "set_enabled" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_updating, R.string.tool_title_updated, optAetherString(arguments, "skill_id", "skillId"), R.string.tool_title_agent_skill_fallback)
            else -> toolStatusLabel(isRunning, R.string.tool_title_reading_agent_skills, R.string.tool_title_read_agent_skills)
        }
        "aether_mcp_manage" -> when (action.lowercase()) {
            "upsert_streamable_http", "upsert_stdio" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_saving, R.string.tool_title_saved, optAetherString(arguments, "display_name", "displayName"), R.string.tool_title_mcp_server_fallback)
            "remove" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_removing, R.string.tool_title_removed, optAetherString(arguments, "server_id", "serverId"), R.string.tool_title_mcp_server_fallback)
            "set_enabled" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_updating, R.string.tool_title_updated, optAetherString(arguments, "server_id", "serverId"), R.string.tool_title_mcp_server_fallback)
            else -> toolStatusLabel(isRunning, R.string.tool_title_reading_mcp_servers, R.string.tool_title_read_mcp_servers)
        }
        "aether_termux_manage" -> when (action.lowercase()) {
            "configure_root_access" -> toolStatusLabel(isRunning, R.string.tool_title_configuring_termux_root, R.string.tool_title_configured_termux_root)
            "inspect_root_setup" -> toolStatusLabel(isRunning, R.string.tool_title_checking_root_setup, R.string.tool_title_checked_root_setup)
            else -> toolStatusLabel(isRunning, R.string.tool_title_checking_termux_setup, R.string.tool_title_checked_termux_setup)
        }
        "aether_agent_mode_manage" -> when (action.lowercase()) {
            "set_authorization" -> toolStatusLabel(isRunning, R.string.tool_title_updating_agent_mode_authorization, R.string.tool_title_updated_agent_mode_authorization)
            "request_shizuku_permission" -> toolStatusLabel(isRunning, R.string.tool_title_requesting_shizuku_permission, R.string.tool_title_requested_shizuku_permission)
            "stop_display" -> toolStatusLabel(isRunning, R.string.tool_title_stopping_agent_mode_display, R.string.tool_title_stopped_agent_mode_display)
            "refresh_displays" -> toolStatusLabel(isRunning, R.string.tool_title_refreshing_agent_mode_displays, R.string.tool_title_refreshed_agent_mode_displays)
            else -> toolStatusLabel(isRunning, R.string.tool_title_checking_agent_mode_authorization, R.string.tool_title_checked_agent_mode_authorization)
        }
        "aether_scheduled_task_manage" -> when (action.lowercase()) {
            "create" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_creating, R.string.tool_title_created, arguments?.optString("name").orEmpty(), R.string.tool_title_scheduled_task_fallback)
            "update" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_updating, R.string.tool_title_updated, optAetherString(arguments, "task_id", "taskId"), R.string.tool_title_scheduled_task_fallback)
            "remove" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_removing, R.string.tool_title_removed, optAetherString(arguments, "task_id", "taskId"), R.string.tool_title_scheduled_task_fallback)
            "set_enabled" -> formatArgumentDrivenToolTitle(isRunning, R.string.tool_title_updating, R.string.tool_title_updated, optAetherString(arguments, "task_id", "taskId"), R.string.tool_title_scheduled_task_fallback)
            else -> toolStatusLabel(isRunning, R.string.tool_title_reading_scheduled_tasks, R.string.tool_title_read_scheduled_tasks)
        }
        "aether_developer_manage" -> toolStatusLabel(isRunning, R.string.tool_title_reading_aether_diagnostics, R.string.tool_title_read_aether_diagnostics)
        else -> toolStatusLabel(isRunning, R.string.tool_title_managing_aether, R.string.tool_title_managed_aether)
    }
}

@Composable
private fun formatAgentDisplayToolTitle(
    isRunning: Boolean,
    arguments: JSONObject?,
): String = when (arguments?.optString("action").orEmpty().lowercase()) {
    "list_apps", "apps", "installed_apps" -> formatArgumentDrivenToolTitle(
        isRunning = isRunning,
        runningVerbRes = R.string.tool_title_reading,
        doneVerbRes = R.string.tool_title_read,
        subject = arguments?.optString("query").orEmpty(),
        fallbackRes = R.string.tool_title_installed_apps_fallback,
    )
    "start" -> toolStatusLabel(isRunning, R.string.tool_title_starting_agent_mode_display, R.string.tool_title_started_agent_mode_display)
    "status" -> toolStatusLabel(isRunning, R.string.tool_title_checking_agent_mode_display, R.string.tool_title_checked_agent_mode_display)
    "launch" -> formatArgumentDrivenToolTitle(
        isRunning = isRunning,
        runningVerbRes = R.string.tool_title_launching,
        doneVerbRes = R.string.tool_title_launched,
        subject = arguments?.optString("target").orEmpty(),
        fallbackRes = R.string.tool_title_agent_mode_app_fallback,
    )
    "tap" -> toolStatusLabel(isRunning, R.string.tool_title_tapping_agent_mode_display, R.string.tool_title_tapped_agent_mode_display)
    "swipe" -> toolStatusLabel(isRunning, R.string.tool_title_swiping_agent_mode_display, R.string.tool_title_swiped_agent_mode_display)
    "key" -> formatArgumentDrivenToolTitle(
        isRunning = isRunning,
        runningVerbRes = R.string.tool_title_pressing,
        doneVerbRes = R.string.tool_title_pressed,
        subject = arguments?.optString("key").orEmpty(),
        fallbackRes = R.string.tool_title_agent_mode_key_fallback,
    )
    "text" -> toolStatusLabel(isRunning, R.string.tool_title_typing_agent_mode, R.string.tool_title_typed_agent_mode)
    "screenshot" -> toolStatusLabel(isRunning, R.string.tool_title_capturing_agent_mode_display, R.string.tool_title_captured_agent_mode_display)
    "stop" -> toolStatusLabel(isRunning, R.string.tool_title_stopping_agent_mode_display, R.string.tool_title_stopped_agent_mode_display)
    else -> toolStatusLabel(isRunning, R.string.tool_title_using_agent_mode_display, R.string.tool_title_used_agent_mode_display)
}

private fun formatAetherCategories(arguments: JSONObject?): String {
    val categories = arguments?.optJSONArray("categories") ?: return ""
    return buildList {
        for (index in 0 until categories.length()) {
            val value = categories.optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }.joinToString(",")
}

private fun optAetherString(
    arguments: JSONObject?,
    primary: String,
    secondary: String,
): String = arguments?.optString(primary).orEmpty().ifBlank {
    arguments?.optString(secondary).orEmpty()
}

private fun parseJsonObject(rawValue: String): JSONObject? =
    if (rawValue.isBlank()) null else runCatching { JSONObject(rawValue) }.getOrNull()

private fun agentModePreviewBackdropBrush(): Brush = Brush.linearGradient(
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

@Composable
private fun ComposerActionChip(
    label: String,
    icon: ImageVector,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE8F1FF))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4F8CFF),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF2E6FD5),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_remove),
                tint = Color(0xFF4F8CFF),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ComposerPlusMenuRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AetherPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = AetherPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
