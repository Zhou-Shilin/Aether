package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import com.zhousl.aether.platform.PlatformCapabilities
import com.zhousl.aether.platform.PlatformPickedFile
import com.zhousl.aether.platform.PlatformServices
import com.zhousl.aether.platform.platformAppVersion
import com.zhousl.aether.platform.NoOpPlatformServices
import com.zhousl.aether.platform.BackgroundExecutionLease
import com.zhousl.aether.platform.SharedApplicationLifecycle
import com.zhousl.aether.platform.createBackgroundExecutionManager
import com.zhousl.aether.platform.applyPlatformAppLanguage
import com.zhousl.aether.platform.LocalReduceMotion
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.ProviderModelOption
import com.zhousl.aether.data.AetherSettingsStore
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.CurrentOnboardingVersion
import com.zhousl.aether.data.OnboardingStarterPrompt
import com.zhousl.aether.data.SharedAppDataManager
import com.zhousl.aether.data.SharedAppDataRestoreResult
import com.zhousl.aether.data.SharedDiagnosticLogger
import com.zhousl.aether.data.SharedDiagnosticRedactor
import com.zhousl.aether.data.SharedActiveSkillContext
import com.zhousl.aether.data.SharedSkillManager
import com.zhousl.aether.data.SharedInstalledSkill
import com.zhousl.aether.data.generateSharedQuickActionLabel
import com.zhousl.aether.data.SharedAetherExtensionManager
import com.zhousl.aether.data.SharedAetherExtensionSnapshot
import com.zhousl.aether.data.SharedAetherExtensionPage
import com.zhousl.aether.data.SharedExtensionStateStore
import com.zhousl.aether.data.SharedProviderModelCatalogClient
import com.zhousl.aether.data.SharedModelCatalogInfo
import com.zhousl.aether.data.SharedThinkingCatalogCache
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.AetherPrivacyPolicyUrl
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.isSharedProviderSetupValid
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.data.shouldMarkOnboardingCompleted
import com.zhousl.aether.data.shouldRevealFollowUpTourCard
import com.zhousl.aether.data.withModelOption
import com.zhousl.aether.data.toJsonObject
import com.zhousl.aether.data.providerModelsFromCatalog
import com.zhousl.aether.data.sharedThinkingCatalogKey
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.platformUptimeMillis
import com.zhousl.aether.data.loadUsageStatistics
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.normalizeOldCommandHistoryRetentionHours
import com.zhousl.aether.data.normalizeTavilyBaseUrl
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.data.pi.SharedPiChatClient
import com.zhousl.aether.data.pi.SharedPiChatMessage
import com.zhousl.aether.data.pi.SharedPiContentPart
import com.zhousl.aether.data.pi.SharedPiUsage
import com.zhousl.aether.data.pi.RuntimeHostToolExecutor
import com.zhousl.aether.data.pi.SharedAgentManagementTools
import com.zhousl.aether.data.pi.SharedMcpManager
import com.zhousl.aether.data.pi.SharedMcpServerConfig
import com.zhousl.aether.data.pi.SharedMcpTransport
import com.zhousl.aether.data.pi.SharedToolRegistry
import com.zhousl.aether.data.pi.SharedChromeManager
import com.zhousl.aether.data.pi.SharedCompositeHostTools
import com.zhousl.aether.data.pi.SharedHostToolResult
import com.zhousl.aether.data.pi.SharedWebToolExecutor
import com.zhousl.aether.data.pi.toPiOAuthPrompt
import com.zhousl.aether.data.pi.toPiProviderEnvironmentVariables
import com.zhousl.aether.data.chatdb.ChatHistoryDatabase
import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatTool
import com.zhousl.aether.data.chatdb.PersistedChatAttachment
import com.zhousl.aether.data.chatdb.PersistedChatUsage
import com.zhousl.aether.data.chatdb.PersistedAssistantResponseBlock
import com.zhousl.aether.data.chatdb.PersistedAssistantResponseBlockType
import com.zhousl.aether.data.chatdb.PersistedReasoningSummaryChunk
import com.zhousl.aether.data.chatdb.PersistedReasoningTrace
import com.zhousl.aether.data.chatdb.PersistedMessageDisplayKind
import com.zhousl.aether.data.chatdb.SharedChatHistoryStore
import com.zhousl.aether.data.chatdb.SharedDraftSessionId
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.deriveSharedSessionMetadata
import com.zhousl.aether.data.chatdb.serializePersistedChatSession
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimePiBridgeTransport
import com.zhousl.aether.runtime.RuntimeSetupProgress
import com.zhousl.aether.runtime.PiBridgeSetupPhase
import com.zhousl.aether.runtime.SharedPiBridgeClient
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherBackgroundGradientTop
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOnPrimaryContainer
import com.zhousl.aether.ui.theme.AetherOnSecondaryContainer
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherPrimaryContainer
import com.zhousl.aether.ui.theme.AetherSecondaryContainer
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import com.zhousl.aether.ui.theme.AetherTertiary
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private enum class SharedRoute { Onboarding, Chat, Settings }
private enum class OnboardingStage { Landing, Runtime, Provider, Search }
private const val SharedScreenTransitionDuration = 320
private const val SharedOnboardingStepFadeDuration = 560
private val SharedScreenTransitionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private const val SharedPrivacyPolicyAnnotationTag = "privacy-policy"
private const val SharedReasoningInitialSummaryTokenThreshold = 100
private const val SharedReasoningTimedSummaryIntervalMillis = 5_000L
private const val SharedReasoningSummaryMaxInputChars = 8_000
private const val SharedReasoningSummaryTitleMaxChars = 120
private const val SharedReasoningSummaryDetailMaxChars = 520
private val SharedDiagnosticPrettyJson = Json { prettyPrint = true }
private const val SharedReasoningSummarySystemPrompt =
    "You write concise user-visible progress summaries for assistant reasoning. Use a consistent first-person planning style, and never quote long private reasoning verbatim."

private fun SharedRoute.depth(): Int = when (this) {
    SharedRoute.Onboarding -> 0
    SharedRoute.Chat -> 1
    SharedRoute.Settings -> 2
}

private data class SharedReasoningSummary(
    val title: String,
    val detail: String,
)

private class SharedNonSnapshotJobSlot {
    var job: Job? = null
}

private object SharedModelLogoPathCache {
    private val paths = mutableMapOf<List<String>, List<Path>>()

    fun getOrParse(pathData: List<String>): List<Path> = paths.getOrPut(pathData) {
        pathData.mapNotNull { value ->
            runCatching { PathParser().parsePathString(value).toPath() }.getOrNull()
        }
    }
}

internal data class SharedReasoningSummarySubmission(
    val blockId: String,
    val chunk: SharedReasoningSummaryChunk,
)

internal class SharedReasoningTurnTracker {
    private var activeBlockId: String? = null
    private var activeDirectSummaryBlockId: String? = null
    private var activeDirectSummaryChunkId: String? = null
    private var firstSummarySubmitted = false
    private var lastSubmittedCharIndex = 0
    private var lastTimedSummaryAtMillis = 0L
    private var chunkCounter = 0L
    private var timelineCounter = 0L

    fun nextTimelineOrder(): Long {
        timelineCounter += 1
        return timelineCounter
    }

    fun finishDirectSummaryChunk() {
        activeDirectSummaryBlockId = null
        activeDirectSummaryChunkId = null
    }

    fun directSummaryChunkId(blockId: String): String {
        if (activeDirectSummaryBlockId == blockId) {
            activeDirectSummaryChunkId?.let { return it }
        }
        return "$blockId-summary-${chunkCounter++}".also { chunkId ->
            activeDirectSummaryBlockId = blockId
            activeDirectSummaryChunkId = chunkId
        }
    }

    fun prepareSummary(
        trace: SharedReasoningTrace,
        forceRemaining: Boolean,
        nowMillis: Long,
    ): SharedReasoningSummarySubmission? {
        if (trace.rawText.isBlank()) return null
        if (activeBlockId != trace.id) {
            activeBlockId = trace.id
            firstSummarySubmitted = false
            lastSubmittedCharIndex = 0
            lastTimedSummaryAtMillis = trace.startedAtMillis.takeIf { it > 0L } ?: nowMillis
        }

        val rawText = trace.rawText
        val summaryText = if (!firstSummarySubmitted) {
            val tokenCount = approximateSharedReasoningTokenCount(rawText)
            if (tokenCount < SharedReasoningInitialSummaryTokenThreshold && !forceRemaining) return null
            if (tokenCount >= SharedReasoningInitialSummaryTokenThreshold) {
                takeApproximateSharedReasoningTokens(rawText, SharedReasoningInitialSummaryTokenThreshold)
            } else {
                rawText
            }.also { selected ->
                firstSummarySubmitted = true
                lastSubmittedCharIndex = selected.length.coerceAtMost(rawText.length)
                lastTimedSummaryAtMillis = nowMillis
            }
        } else {
            val startIndex = lastSubmittedCharIndex.coerceIn(0, rawText.length)
            if (startIndex >= rawText.length) return null
            if (!forceRemaining && nowMillis - lastTimedSummaryAtMillis < SharedReasoningTimedSummaryIntervalMillis) {
                return null
            }
            rawText.substring(startIndex).also {
                lastSubmittedCharIndex = rawText.length
                lastTimedSummaryAtMillis = nowMillis
            }
        }.trim()
        if (summaryText.isBlank()) return null

        val chunkId = "${trace.id}-summary-${chunkCounter++}"
        return SharedReasoningSummarySubmission(
            blockId = trace.id,
            chunk = SharedReasoningSummaryChunk(
                id = chunkId,
                rawText = summaryText,
                isPending = true,
                createdAtMillis = nowMillis,
                timelineOrder = nextTimelineOrder(),
            ),
        )
    }
}

internal fun approximateSharedReasoningTokenCount(text: String): Int {
    var count = 0
    var inToken = false
    text.forEach { char ->
        when {
            char.isWhitespace() -> inToken = false
            char.code in 0x3400..0x9FFF || char.code in 0xF900..0xFAFF -> {
                count += 1
                inToken = false
            }
            !inToken -> {
                count += 1
                inToken = true
            }
        }
    }
    return count
}

internal fun estimateSharedRequestTokenUsage(messages: List<SharedChatMessage>): SharedPiUsage {
    val inputTokens = messages.sumOf { message ->
        approximateSharedReasoningTokenCount(message.text) +
            message.attachments.sumOf { attachment ->
                approximateSharedReasoningTokenCount(attachment.name) +
                    if (attachment.mimeType.startsWith("image/", ignoreCase = true)) {
                        85
                    } else {
                        (attachment.sizeBytes.coerceAtLeast(0L) / 4L)
                            .coerceAtMost(16_000L)
                            .toInt()
                    }
            }
    }.toLong()
    return SharedPiUsage(
        inputTokens = inputTokens,
        totalTokens = inputTokens,
    )
}

private fun takeApproximateSharedReasoningTokens(text: String, maxTokens: Int): String {
    if (maxTokens <= 0) return ""
    var count = 0
    var inToken = false
    text.forEachIndexed { index, char ->
        when {
            char.isWhitespace() -> inToken = false
            char.code in 0x3400..0x9FFF || char.code in 0xF900..0xFAFF -> {
                count += 1
                inToken = false
            }
            !inToken -> {
                count += 1
                inToken = true
            }
        }
        if (count >= maxTokens) return text.substring(0, index + 1)
    }
    return text
}

private suspend fun <T> runSharedAppCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (failure: TimeoutCancellationException) {
    Result.failure(failure)
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    Result.failure(failure)
}

private fun Throwable.sharedUserFacingMessage(): String =
    message?.trim().takeUnless { it.isNullOrBlank() }
        ?: this::class.simpleName.orEmpty().ifBlank { "Throwable" }

private suspend fun removeSharedUnreferencedWorkspaceFiles(
    runtime: MultiplatformLocalRuntime,
    paths: List<String>,
) {
    val workspaceRoot = runtime.workspaceRoot.trimEnd('/')
    if (workspaceRoot.isBlank()) return
    paths.asSequence()
        .map(String::trim)
        .filter { it.startsWith("$workspaceRoot/") }
        .distinct()
        .forEach { path -> runCatching { runtime.fileSystem.remove(path) } }
}

internal data class SharedChatMessage(
    val id: String = platformRandomUuid(),
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
    val reasoningText: String = "",
    val tools: List<SharedChatToolInvocation> = emptyList(),
    val responseBlocks: List<SharedAssistantResponseBlock> = emptyList(),
    val isStreaming: Boolean = false,
    val status: String = "",
    val statusDetail: String = "",
    val attachments: List<SharedChatAttachment> = emptyList(),
    val usage: SharedPiUsage? = null,
    val responseGroupId: String = "",
    val isActiveBranch: Boolean = true,
    val branchIndex: Int = 0,
    val branchCount: Int = 1,
    val createdAtMillis: Long = platformCurrentTimeMillis(),
    val completedAtMillis: Long? = null,
    val providerId: String = "",
    val modelId: String = "",
    val providerPayloadJson: String = "",
    val thoughtDurationMillis: Long = 0,
    val responseDurationMillis: Long = 0,
    val firstTokenLatencyMillis: Long? = null,
    val tokenUsageSource: String = "unavailable",
    val assistantActionsHidden: Boolean = false,
    val displayKind: SharedMessageDisplayKind = SharedMessageDisplayKind.Standard,
    val userBranches: List<List<SharedChatMessage>> = emptyList(),
    val selectedUserBranchIndex: Int = 0,
)

private fun buildSharedImplicitSkillRequestText(messages: List<SharedChatMessage>): String {
    val recentUserMessages = mutableListOf<SharedChatMessage>()
    for (message in messages.asReversed()) {
        if (message.fromUser) {
            recentUserMessages += message
        } else if (recentUserMessages.isNotEmpty()) {
            break
        }
    }
    return recentUserMessages.asReversed().joinToString("\n") { message ->
        buildString {
            if (message.text.isNotBlank()) append(message.text)
            if (message.attachments.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append("Attachments: ")
                append(
                    message.attachments.joinToString(", ") { attachment ->
                        listOf(attachment.name, attachment.mimeType)
                            .filter(String::isNotBlank)
                            .joinToString(" ")
                    }
                )
            }
        }
    }.trim()
}

private fun MutableList<SharedActiveSkillContext>.upsertSharedActiveSkill(
    activeSkill: SharedActiveSkillContext,
) {
    val index = indexOfFirst { it.skillId == activeSkill.skillId }
    if (index >= 0) this[index] = activeSkill else add(activeSkill)
}

private fun <T> MutableList<T>.replaceSharedContents(values: Collection<T>) {
    clear()
    addAll(values)
}

internal enum class SharedMessageDisplayKind {
    Standard,
    HiddenContext,
    CompactStatus,
}
internal data class SharedPendingTurn(
    val id: String = platformRandomUuid(),
    val text: String,
    val attachments: List<SharedChatAttachment> = emptyList(),
    val mode: SharedPendingTurnMode = SharedPendingTurnMode.Queue,
    val createdAtMillis: Long = platformCurrentTimeMillis(),
    val promotedFromSteer: Boolean = false,
)
internal enum class SharedPendingTurnMode { Queue, Steer }

internal data class SharedAssistantRetryPlan(
    val retainedMessages: List<SharedChatMessage>,
    val userMessage: SharedChatMessage,
)

internal fun List<SharedPendingTurn>.nextSharedQueuedTurnIndex(): Int =
    indexOfLast { it.mode == SharedPendingTurnMode.Queue && it.promotedFromSteer }
        .takeIf { it >= 0 }
        ?: indexOfFirst { it.mode == SharedPendingTurnMode.Queue }

internal fun SharedPendingTurn.fallbackSharedSteerToQueue(): SharedPendingTurn =
    copy(mode = SharedPendingTurnMode.Queue, promotedFromSteer = true)

internal fun List<SharedPendingTurn>.promoteSharedSteersToQueue(): List<SharedPendingTurn> =
    map { pending ->
        if (pending.mode == SharedPendingTurnMode.Steer) {
            pending.fallbackSharedSteerToQueue()
        } else {
            pending
        }
    }

internal fun SharedPendingTurn.sharedPreviewText(): String = when {
    text.trim().isNotBlank() -> text.trim()
    attachments.isEmpty() -> "Empty message"
    attachments.size == 1 -> attachments.first().name
    else -> "${attachments.size} attachments"
}.take(72)

internal fun splitSharedAssistantForAcceptedSteer(
    pendingAssistant: SharedChatMessage,
    userMessage: SharedChatMessage,
    nowMillis: Long = platformCurrentTimeMillis(),
): List<SharedChatMessage> = splitSharedAssistantForAcceptedSteers(
    pendingAssistant = pendingAssistant,
    userMessages = listOf(userMessage),
    nowMillis = nowMillis,
)

internal fun splitSharedAssistantForAcceptedSteers(
    pendingAssistant: SharedChatMessage,
    userMessages: List<SharedChatMessage>,
    nowMillis: Long = platformCurrentTimeMillis(),
): List<SharedChatMessage> {
    val continuation = pendingAssistant.copy(
        text = "",
        isError = false,
        reasoningText = "",
        tools = emptyList(),
        responseBlocks = emptyList(),
        isStreaming = true,
        status = SharedInitialStreamingStatusText,
        statusDetail = SharedInitialStreamingStatusDetail,
        attachments = emptyList(),
        usage = null,
        thoughtDurationMillis = 0,
        responseDurationMillis = 0,
        firstTokenLatencyMillis = null,
        tokenUsageSource = "unavailable",
        assistantActionsHidden = false,
        completedAtMillis = null,
    )
    val committed = pendingAssistant.copy(
        id = platformRandomUuid(),
        isStreaming = false,
        status = "",
        statusDetail = "",
        usage = null,
        responseGroupId = "agent-group-$nowMillis-${platformRandomUuid()}",
        isActiveBranch = true,
        branchIndex = 0,
        branchCount = 1,
        createdAtMillis = nowMillis,
        completedAtMillis = nowMillis,
        thoughtDurationMillis = 0,
        responseDurationMillis = 0,
        firstTokenLatencyMillis = null,
        tokenUsageSource = "unavailable",
        assistantActionsHidden = true,
    )
    return buildList {
        if (committed.hasSharedVisibleAssistantWork()) add(committed)
        addAll(userMessages)
        add(continuation)
    }
}

internal fun buildSharedAssistantRetryPlan(
    messages: List<SharedChatMessage>,
    assistantMessageId: String,
): SharedAssistantRetryPlan? {
    val targetIndex = messages.indexOfFirst { it.id == assistantMessageId && !it.fromUser }
    if (targetIndex < 0) return null
    val target = messages[targetIndex]
    val trimIndex = target.responseGroupId.takeIf(String::isNotBlank)?.let { groupId ->
        messages.indexOfFirst { !it.fromUser && it.responseGroupId == groupId }
            .takeIf { it >= 0 }
    } ?: targetIndex
    val retained = messages.take(trimIndex)
    val user = retained.lastOrNull()?.takeIf { it.fromUser } ?: return null
    return SharedAssistantRetryPlan(retained, user)
}

private enum class SharedSettingsKind {
    Generic,
    General,
    Providers,
    Personalization,
    WebTools,
    Reliability,
    Skills,
    Extensions,
    Mcp,
    Alpine,
    Terminal,
    Chrome,
    Statistics,
    Developer,
    About,
}
private data class SettingsDestination(
    val title: String,
    val subtitle: String,
    val kind: SharedSettingsKind = SharedSettingsKind.Generic,
)

private val SharedSettingsDestinationSaver = Saver<SettingsDestination?, String>(
    save = { it?.kind?.name.orEmpty() },
    restore = { savedKind ->
        savedKind.takeIf(String::isNotBlank)?.let { kindName ->
            SettingsDestination(
                title = "",
                subtitle = "",
                kind = SharedSettingsKind.valueOf(kindName),
            )
        }
    },
)

private fun SettingsDestination?.depth(): Int = when (this?.kind) {
    null -> 0
    SharedSettingsKind.Terminal,
    SharedSettingsKind.Chrome -> 2
    else -> 1
}

internal fun resolveSharedProviderForModel(
    providerConfigs: List<LlmProviderConfig>,
    baseConfig: LlmProviderConfig?,
    preferredKey: String,
    fallbackKey: String = "",
): LlmProviderConfig? {
    val options = providerConfigs.availableModelOptions()
    val selected = options.findModelOption(preferredKey)
        ?: options.firstOrNull { it.modelId == preferredKey }
        ?: options.firstOrNull { it.fullLabel == preferredKey }
        ?: options.findModelOption(fallbackKey)
        ?: options.firstOrNull()
    return selected?.let { option ->
        providerConfigs.firstOrNull { it.id == option.providerConfigId }
            ?.copy(modelId = option.modelId)
    } ?: baseConfig
}

internal fun resolveSharedConversationModelKey(
    selectedModelKey: String,
    defaultChatModelKey: String,
    options: List<ProviderModelOption>,
): String = selectedModelKey.takeIf { key -> options.any { it.key == key } }
    ?: defaultChatModelKey.takeIf { key -> options.any { it.key == key } }
    ?: options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)

private val TopFadeHeight = 42.dp
private val SettingsTopFadeHeight = 40.dp
private const val FollowUpTourAutoOpenDelayMillis = 2_500L
private const val TransientMessageDurationMillis = 2_000L
private val ComposerShape = RoundedCornerShape(26.dp)
private val ComposerFocusedShape = RoundedCornerShape(28.dp)
private val ComposerPlusMenuMaxHeight = 372.dp
private val ControlShadow = Color(0x14000000)
private val ComposerShadow = Color(0x18000000)
private val ComposerPurple = Color(0xFF9B5CFF)
private val SharedConversationMotionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val SharedBranchBlurInEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val SharedBranchBlurOutEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val SharedBranchBlurInDurationMillis = 180
private const val SharedBranchBlurOutDurationMillis = 340
private const val SharedTabletLayoutMinWidthDp = 700f
private const val SharedCompactCommand = "/compact"
private const val SharedCompactingStatus = "compacting"
private const val SharedCompactingMaxInputChars = 120_000
// Pi Coding Agent defaults: compact before the model loses the 16K response reserve.
private const val SharedContextWindowTokens = 128_000L
private const val SharedAutoCompactionReserveTokens = 16_384L

private const val SharedInitialStreamingStatusText = "Thinking"
private const val SharedInitialStreamingStatusDetail = "Aether is working on this turn."
private const val SharedProviderValidationErrorText =
    "The selected provider is not fully configured."
private const val SharedInlineImageAttachmentMaxBytes = 5L * 1024L * 1024L
private const val SharedSessionTitleSystemPrompt =
    "Generate a concise chat title for this conversation. Return only the title, in the user's language when possible, with no quotes, no emoji, and at most 6 words."
private const val SharedSessionCompactingSystemPrompt =
    "You are Aether's conversation compactor. Summarize the provided conversation so a future assistant can continue seamlessly. Preserve user goals, constraints, decisions, important facts, open tasks, files/paths mentioned, tool results, errors, and next steps. Do not invent details. Return only the compacted context."

@Composable
fun AetherSharedApp(
    runtime: MultiplatformLocalRuntime,
    capabilities: PlatformCapabilities,
    settingsStore: AetherSettingsStore? = null,
    chatHistoryDatabase: ChatHistoryDatabase? = null,
    platformServices: PlatformServices = NoOpPlatformServices,
) {
    var sharedAppSettings by remember { mutableStateOf(AppSettings()) }
    applyPlatformAppLanguage(sharedAppSettings.language)
    SharedAetherTheme(
        themeMode = sharedAppSettings.themeMode,
        language = sharedAppSettings.language,
    ) {
        val reduceMotion = LocalReduceMotion.current
        val finishEditingBeforeCompactingMessage = stringResource(Res.string.message_finish_editing_before_compacting)
        val noConversationToCompactMessage = stringResource(Res.string.message_no_conversation_to_compact)
        val pauseBeforeCompactingMessage = stringResource(Res.string.message_pause_before_compacting)
        val notEnoughConversationMessage = stringResource(Res.string.message_not_enough_conversation_to_compact)
        val noTextToCompactMessage = stringResource(Res.string.message_no_text_to_compact)
        val configureProviderBeforeCompactingMessage =
            stringResource(Res.string.message_configure_provider_before_compacting)
        val compactionFailedPrefix = stringResource(Res.string.message_compaction_failed, "").trimEnd()
        val pauseBeforeEditingMessage =
            stringResource(Res.string.message_pause_before_editing_message)
        val sessionExportedMessage = stringResource(Res.string.message_session_exported)
        val sessionExportFailedMessage = stringResource(Res.string.message_session_export_failed)
        val unableToOpenLinkMessage = stringResource(Res.string.app_unable_to_open_link)
        val chatStoppedStatus = stringResource(Res.string.chat_stopped)
        val chatInterruptedStatus = stringResource(Res.string.chat_interrupted)
        val unknownErrorMessage = stringResource(Res.string.common_unknown_error)
        val requestErrorPlaceholder = "{request_error}"
        val requestFailureTemplate = stringResource(
            Res.string.chat_request_failed_detail,
            requestErrorPlaceholder,
        )
        val backgroundExpiredMessage = stringResource(Res.string.chat_background_expired)
        val mcpRefreshErrorPlaceholder = "{mcp_error}"
        val mcpRefreshFailedTemplate = stringResource(
            Res.string.message_refresh_mcp_failed,
            mcpRefreshErrorPlaceholder,
        )
        val appScope = rememberCoroutineScope()
        var pendingDeleteSessionId by rememberSaveable { mutableStateOf<String?>(null) }
        val extensionStateStore = remember(runtime) { SharedExtensionStateStore(runtime) }
        val bridgeClient = remember(runtime, extensionStateStore) {
            SharedPiBridgeClient(
                transport = RuntimePiBridgeTransport(runtime),
                extensionLoadOptionsProvider = extensionStateStore::load,
            )
        }
        val mcpManager = remember(runtime) { SharedMcpManager(runtime) }
        val chromeManager = remember(runtime) { SharedChromeManager(runtime) }
        val runtimeTools = remember(runtime) { RuntimeHostToolExecutor(runtime) }
        val skillManager = remember(runtime) { SharedSkillManager(runtime) }
        val providerConfigs = remember { mutableStateListOf<LlmProviderConfig>() }
        var providerConfig by remember { mutableStateOf<LlmProviderConfig?>(null) }
        val persistOAuthCredential: suspend (String, String) -> Unit = remember(settingsStore) {
            { configId, credentialJson ->
                if (configId.isNotBlank() && credentialJson.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        val index = providerConfigs.indexOfFirst { it.id == configId }
                        val current = providerConfigs.getOrNull(index)
                        if (current != null && current.oauthCredentialJson != credentialJson) {
                            val updated = providerConfigs.toMutableList().apply {
                                this[index] = current.copy(
                                    oauthCredentialJson = credentialJson,
                                    updatedAtMillis = platformCurrentTimeMillis(),
                                )
                            }
                            val activeConfigId = providerConfig?.id.orEmpty()
                            providerConfigs.clear()
                            providerConfigs.addAll(updated)
                            providerConfig = updated.firstOrNull { it.id == activeConfigId }
                            settingsStore?.saveProviders(updated, activeConfigId)
                        }
                    }
                }
            }
        }
        val completionClient = remember(bridgeClient, persistOAuthCredential) {
            SharedPiChatClient(
                bridge = bridgeClient,
                onOAuthCredentialUpdated = persistOAuthCredential,
            )
        }
        val providerConfigsSnapshot = providerConfigs.toList()
        val modelOptions = remember(providerConfigsSnapshot) {
            providerConfigsSnapshot.availableModelOptions()
        }
        val modelCatalogRequestKey = remember(modelOptions) {
            modelOptions.joinToString("|") { "${it.key}:${it.fullLabel}" }
        }
        val modelCatalogClient = remember { SharedProviderModelCatalogClient() }
        var modelCatalogInfo by remember {
            mutableStateOf<Map<String, SharedModelCatalogInfo>>(emptyMap())
        }
        var thinkingLevelsByProviderModel by remember {
            mutableStateOf<Map<String, List<String>>>(emptyMap())
        }
        var thinkingLevelClampsByProviderModel by remember {
            mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
        }
        val thinkingCatalogRefreshMutex = remember { Mutex() }
        LaunchedEffect(modelCatalogRequestKey) {
            val fetched = modelCatalogClient.fetchModelInfo(modelOptions)
            fetched.values.distinctBy(SharedModelCatalogInfo::labLogoPathData).forEach { info ->
                kotlinx.coroutines.yield()
                SharedModelLogoPathCache.getOrParse(info.labLogoPathData)
            }
            modelCatalogInfo = fetched
        }
        val installedSkills = remember { mutableStateListOf<SharedInstalledSkill>() }
        val mcpServers = remember { mutableStateListOf<SharedMcpServerConfig>() }
        var chromeEnabled by rememberSaveable { mutableStateOf(false) }
        DisposableEffect(bridgeClient) {
            onDispose { appScope.launch { bridgeClient.close() } }
        }
        var route by rememberSaveable { mutableStateOf(SharedRoute.Onboarding) }
        var tabletSettingsVisible by rememberSaveable { mutableStateOf(false) }
        var tabletSettingsDismissRequest by remember { mutableIntStateOf(0) }
        var startupResolved by remember { mutableStateOf(false) }
        val historyStore = remember(chatHistoryDatabase) {
            chatHistoryDatabase?.let(::SharedChatHistoryStore)
        }
        val appDataManager = remember(settingsStore, historyStore, skillManager, mcpManager) {
            if (settingsStore != null && historyStore != null) {
                SharedAppDataManager(settingsStore, historyStore, skillManager, mcpManager)
            } else {
                null
            }
        }
        val sessions = remember { mutableStateListOf<SharedConversationSummary>() }
        val initialSession = remember {
            SharedSessionUiState(id = SharedDraftSessionId, isDraft = true)
        }
        val sessionStates = remember { mutableMapOf<String, SharedSessionUiState>() }
        var currentSession by remember { mutableStateOf(initialSession) }
        var sessionId by rememberSaveable { mutableStateOf(initialSession.id) }
        val messages = currentSession.messages
        val queuedTurns = currentSession.queuedTurns
        val selectedSkillIds = currentSession.selectedSkillIds
        val activeMcpServerIds = currentSession.activeMcpServerIds
        val backgroundLeases = remember { mutableMapOf<String, BackgroundExecutionLease>() }
        var extensionSnapshot by remember { mutableStateOf(SharedAetherExtensionSnapshot()) }
        var activeExtensionPageId by rememberSaveable { mutableStateOf("") }
        var transientMessage by remember { mutableStateOf("") }
        var onboardingReplayMode by remember { mutableStateOf(false) }
        var onboardingEntryStage by remember { mutableStateOf(OnboardingStage.Landing) }
        var showStarterPromptHint by remember { mutableStateOf(false) }
        var awaitingFollowUpTour by remember { mutableStateOf(false) }
        var alpineSetupPreviewVisible by remember { mutableStateOf(false) }
        var extensionManagerRef by remember { mutableStateOf<SharedAetherExtensionManager?>(null) }
        val backgroundExecutionManager = remember(platformServices) {
            createBackgroundExecutionManager(platformServices)
        }
        DisposableEffect(backgroundExecutionManager) {
            onDispose {
                backgroundLeases.values.forEach(BackgroundExecutionLease::end)
                backgroundLeases.clear()
            }
        }

        LaunchedEffect(Unit) {
            SharedDiagnosticLogger.initializePersistence()
        }

        LaunchedEffect(transientMessage) {
            if (transientMessage.isNotBlank()) {
                kotlinx.coroutines.delay(TransientMessageDurationMillis)
                transientMessage = ""
            }
        }

        fun reportMcpRefreshFailure(failure: Throwable) {
            transientMessage = mcpRefreshFailedTemplate.replace(
                mcpRefreshErrorPlaceholder,
                failure.message.orEmpty().ifBlank { "Unknown error." },
            )
        }
        val managementTools = remember(runtime, bridgeClient, skillManager, mcpManager, completionClient) {
            SharedAgentManagementTools(
                runtime = runtime,
                bridge = bridgeClient,
                skillManager = skillManager,
                mcpManager = mcpManager,
                completionClient = completionClient,
                settings = { withContext(Dispatchers.Main) { sharedAppSettings } },
                updateSettings = { updated ->
                    withContext(Dispatchers.Main) {
                        sharedAppSettings = updated
                        settingsStore?.saveGeneralSettings(updated)
                    }
                },
                activeSkills = { requestedSessionId ->
                    withContext(Dispatchers.Main) {
                        val target = sessionStates[requestedSessionId]
                            ?: currentSession.takeIf { it.id == requestedSessionId }
                            ?: currentSession.takeIf { requestedSessionId.isBlank() }
                        target?.activeSkills?.toList().orEmpty()
                    }
                },
                activateSkill = { requestedSessionId, activeSkill ->
                    val target = withContext(Dispatchers.Main) {
                        val state = sessionStates[requestedSessionId]
                            ?: currentSession.takeIf { it.id == requestedSessionId }
                            ?: currentSession.takeIf { requestedSessionId.isBlank() }
                        state?.also {
                            if (activeSkill.skillId !in it.selectedSkillIds) {
                                it.selectedSkillIds += activeSkill.skillId
                            }
                            it.activeSkills.upsertSharedActiveSkill(activeSkill)
                        }
                    }
                    if (target != null && !target.isDraft) {
                        historyStore?.save(
                            sessionId = target.id,
                            messages = target.messages.toPersistedMessages(),
                            selectedSkillIds = target.selectedSkillIds.toList(),
                            activeSkills = target.activeSkills.toList(),
                            activeMcpServerIds = target.activeMcpServerIds.toList(),
                            chromeEnabled = chromeEnabled,
                            selectedModelKey = target.selectedModelKey,
                            titleOverride = target.title,
                            hasCustomTitle = target.hasCustomTitle,
                        )
                    }
                },
                activeMcpServerIds = { requestedSessionId ->
                    withContext(Dispatchers.Main) {
                        val target = sessionStates[requestedSessionId]
                            ?: currentSession.takeIf { it.id == requestedSessionId }
                            ?: currentSession.takeIf { requestedSessionId.isBlank() }
                        target?.activeMcpServerIds?.toSet().orEmpty()
                    }
                },
                currentSessionId = { withContext(Dispatchers.Main) { currentSession.id } },
                resolveProvider = { preferredKey ->
                    withContext(Dispatchers.Main) {
                        resolveSharedProviderForModel(
                            providerConfigs = providerConfigs,
                            baseConfig = providerConfig,
                            preferredKey = preferredKey,
                            fallbackKey = sharedAppSettings.defaultChatModelKey,
                        )
                    }
                },
            )
        }
        val hostToolRegistry = remember(runtimeTools, mcpManager, chromeManager, managementTools) {
            SharedCompositeHostTools(
                listOf(
                    SharedToolRegistry(runtimeTools, mcpManager),
                    managementTools,
                    chromeManager,
                    SharedWebToolExecutor(settings = { sharedAppSettings }),
                )
            )
        }
        val chatClient = remember(bridgeClient, hostToolRegistry, persistOAuthCredential) {
            SharedPiChatClient(
                bridge = bridgeClient,
                hostToolExecutor = hostToolRegistry,
                onOAuthCredentialUpdated = persistOAuthCredential,
            )
        }

        suspend fun persistSession(
            target: SharedSessionUiState = currentSession,
            moveToFront: Boolean = false,
        ) {
            if (target.isDraft) return
            historyStore?.save(
                sessionId = target.id,
                messages = target.messages.toPersistedMessages(),
                selectedSkillIds = target.selectedSkillIds.toList(),
                activeSkills = target.activeSkills.toList(),
                activeMcpServerIds = target.activeMcpServerIds.toList(),
                chromeEnabled = chromeEnabled,
                selectedModelKey = target.selectedModelKey,
                titleOverride = target.title,
                hasCustomTitle = target.hasCustomTitle,
            )
            val summary = SharedConversationSummary(
                id = target.id,
                title = target.title,
                indicator = when {
                    target.isWorking -> SharedConversationIndicator.Working
                    target.hasUnviewedCompletion -> SharedConversationIndicator.UnviewedComplete
                    else -> SharedConversationIndicator.None
                },
            )
            val index = sessions.indexOfFirst { it.id == target.id }
            when {
                index < 0 -> sessions.add(0, summary)
                moveToFront && index > 0 -> {
                    sessions.removeAt(index)
                    sessions.add(0, summary)
                }
                else -> sessions[index] = summary
            }
        }

        fun retainEnabledSkillSelections(enabledSkillIds: Set<String>) {
            (sessionStates.values + currentSession).toSet().forEach { state ->
                if (state.retainEnabledSkillSelections(enabledSkillIds)) {
                    appScope.launch { persistSession(state) }
                }
            }
        }

        fun retainEnabledMcpSelections(enabledMcpServerIds: Set<String>) {
            (sessionStates.values + currentSession).toSet().forEach { state ->
                if (state.retainEnabledMcpSelections(enabledMcpServerIds)) {
                    appScope.launch { persistSession(state) }
                }
            }
        }

        fun commitProviderConfigs(
            updatedConfigs: List<LlmProviderConfig>,
            preferredActiveConfigId: String = providerConfig?.id.orEmpty(),
        ) {
            val normalized = updatedConfigs.distinctBy(LlmProviderConfig::id)
            val activeConfigId = resolveSharedActiveProviderConfigId(
                providerConfigs = normalized,
                preferredActiveConfigId = preferredActiveConfigId,
            )
            providerConfigs.clear()
            providerConfigs.addAll(normalized)
            providerConfig = normalized.firstOrNull { it.id == activeConfigId }
            appScope.launch {
                settingsStore?.saveProviders(normalized, activeConfigId)
            }
        }

        fun upsertProviderConfig(config: LlmProviderConfig) {
            val updated = providerConfigs.toMutableList()
            val index = updated.indexOfFirst { it.id == config.id }
            val persistedConfig = config.copy(updatedAtMillis = platformCurrentTimeMillis())
            if (index >= 0) updated[index] = persistedConfig else updated += persistedConfig
            commitProviderConfigs(
                updatedConfigs = updated,
                preferredActiveConfigId = providerConfig?.id.orEmpty().ifBlank { persistedConfig.id },
            )
        }

        fun setProviderEnabled(configId: String, enabled: Boolean) {
            commitProviderConfigs(
                providerConfigs.map { config ->
                    if (config.id == configId) {
                        config.copy(
                            isEnabled = enabled,
                            updatedAtMillis = platformCurrentTimeMillis(),
                        )
                    } else {
                        config
                    }
                }
            )
        }

        fun removeProviderConfig(configId: String) {
            commitProviderConfigs(providerConfigs.filterNot { it.id == configId })
        }

        fun persistResolvedAppSettings(updated: AppSettings) {
            val modelOptions = providerConfigs.availableModelOptions()
            fun normalizedModelKey(value: String): String = value.takeIf { modelKey ->
                modelKey.isBlank() || modelOptions.any { it.key == modelKey }
            }.orEmpty()

            var resolved = updated.copy(
                defaultChatModelKey = normalizedModelKey(updated.defaultChatModelKey),
                defaultTitleModelKey = normalizedModelKey(updated.defaultTitleModelKey),
                defaultNamingModelKey = normalizedModelKey(updated.defaultNamingModelKey),
                defaultCompactingModelKey = normalizedModelKey(updated.defaultCompactingModelKey),
            )
            val resolvedChatModelKey = resolved.defaultChatModelKey.ifBlank {
                modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
            }
            modelOptions.findModelOption(resolvedChatModelKey)?.let { option ->
                resolved = resolved.withModelOption(option)
                commitProviderConfigs(providerConfigs.toList(), option.providerConfigId)
            }
            sharedAppSettings = resolved
            appScope.launch { settingsStore?.saveGeneralSettings(resolved) }
        }

        fun endBackgroundExecution(target: SharedSessionUiState) {
            backgroundLeases.remove(target.id)?.end()
        }

        fun ensureBackgroundExecution(target: SharedSessionUiState) {
            if (backgroundLeases[target.id]?.isActive == true) return
            backgroundLeases.remove(target.id)?.end()
            backgroundLeases[target.id] = backgroundExecutionManager.begin("Aether Agent") {
                target.job?.cancel()
                target.job = null
                appScope.launch {
                    target.streamingStatus = chatInterruptedStatus
                    val pending = target.messages.lastOrNull()
                    if (pending?.fromUser == false) {
                        target.messages.updateMessage(pending.id) {
                            it.interruptedByBackgroundExpiration(
                                status = chatInterruptedStatus,
                                fallbackText = backgroundExpiredMessage,
                            )
                        }
                    }
                    persistSession(target)
                    endBackgroundExecution(target)
                }
            }
        }

        LaunchedEffect(Unit) {
            SharedApplicationLifecycle.backgrounded.collect { backgrounded ->
                if (backgrounded) {
                    val backgroundedSession = currentSession
                    appScope.launch { persistSession(backgroundedSession) }
                    (sessionStates.values + backgroundedSession)
                        .distinctBy(SharedSessionUiState::id)
                        .filter { it.job?.isActive == true }
                        .forEach(::ensureBackgroundExecution)
                }
            }
        }

        LaunchedEffect(settingsStore, historyStore) {
            withContext(Dispatchers.Default) { settingsStore?.load() }?.let { persisted ->
                sharedAppSettings = persisted.appSettings
                providerConfigs.clear()
                providerConfigs.addAll(persisted.providerConfigs)
                providerConfig = persisted.activeProviderConfig
                val persistedModelOptions = withContext(Dispatchers.Default) {
                    persisted.providerConfigs.availableModelOptions()
                }
                val persistedThinkingKeys = persistedModelOptions.mapTo(mutableSetOf()) { option ->
                    sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                }
                thinkingLevelsByProviderModel = persisted.thinkingCatalogCache.levelsByProviderModel
                    .filterKeys(persistedThinkingKeys::contains)
                thinkingLevelClampsByProviderModel = persisted.thinkingCatalogCache.clampsByProviderModel
                    .filterKeys(persistedThinkingKeys::contains)
                if (currentSession.isDraft) {
                    currentSession.selectedModelKey = resolveSharedConversationModelKey(
                        selectedModelKey = currentSession.selectedModelKey,
                        defaultChatModelKey = persisted.appSettings.defaultChatModelKey,
                        options = persistedModelOptions,
                    )
                }
                if (shouldRestoreSharedChat(persisted.appSettings.onboardingSeenVersion)) {
                    route = SharedRoute.Chat
                }
            }
            val persistedSessions = historyStore?.loadAll().orEmpty()
            sessionStates.clear()
            sessions.clear()
            persistedSessions.forEach { persisted ->
                val state = persisted.toSharedSessionUiState()
                sessionStates[state.id] = state
                sessions += SharedConversationSummary(state.id, state.title)
            }
            val persistedCurrentSessionId = historyStore?.loadCurrentSessionId()
            val restored = if (persistedCurrentSessionId == SharedDraftSessionId) {
                initialSession
            } else {
                historyStore?.loadCurrent()?.let { sessionStates[it.id] }
                    ?: sessionStates.values.firstOrNull()
                    ?: initialSession
            }
            currentSession = restored
            sessionId = restored.id
            historyStore?.load(restored.id)?.let { persisted ->
                chromeEnabled = persisted.chromeEnabled && capabilities.alpineChrome
                chromeManager.enabled = chromeEnabled
            }
            startupResolved = true
        }

        LaunchedEffect(route) {
            if (route == SharedRoute.Chat || route == SharedRoute.Settings) {
                val runtimeReady = runSharedAppCatching { runtime.isReady() }
                    .getOrDefault(false)
                if (!runtimeReady) return@LaunchedEffect
                runSharedAppCatching {
                    runtime.initialize()
                    skillManager.list()
                }.onSuccess { skills ->
                    installedSkills.clear()
                    installedSkills.addAll(skills)
                    retainEnabledSkillSelections(
                        skills.filter(SharedInstalledSkill::isEnabled)
                            .map(SharedInstalledSkill::id)
                            .toSet(),
                    )
                    if (selectedSkillIds.isEmpty() && messages.isEmpty()) {
                        selectedSkillIds.addAll(
                            sharedAppSettings.defaultSelectedSkillIds.filter { id ->
                                skills.any { it.id == id && it.isEnabled }
                            }
                        )
                    }
                }
                val loadedMcpServers = runSharedAppCatching { mcpManager.loadServers() }
                loadedMcpServers.onSuccess { servers ->
                    mcpServers.clear()
                    mcpServers.addAll(servers)
                    retainEnabledMcpSelections(
                        servers.filter(SharedMcpServerConfig::enabled)
                            .map(SharedMcpServerConfig::id)
                            .toSet(),
                    )
                    runSharedAppCatching {
                        mcpManager.refreshBindings(
                            servers.filter { it.enabled && it.id in activeMcpServerIds },
                            sessionId = currentSession.id,
                        )
                    }
                        .onFailure(::reportMcpRefreshFailure)
                }
                loadedMcpServers.onFailure(::reportMcpRefreshFailure)
            }
        }

        suspend fun persistThinkingCatalogCache() {
            val validKeys = modelOptions.mapTo(mutableSetOf()) { option ->
                sharedThinkingCatalogKey(option.piProviderId, option.modelId)
            }
            val cache = SharedThinkingCatalogCache(
                levelsByProviderModel = thinkingLevelsByProviderModel.filterKeys(validKeys::contains),
                clampsByProviderModel = thinkingLevelClampsByProviderModel.filterKeys(validKeys::contains),
            )
            withContext(Dispatchers.Default) {
                settingsStore?.saveThinkingCatalogCache(cache)
            }
        }

        suspend fun refreshThinkingCatalog(
            options: List<ProviderModelOption> = modelOptions,
        ): Boolean = thinkingCatalogRefreshMutex.withLock {
            runSharedAppCatching {
                val publicLevels = modelCatalogClient.fetchThinkingLevels(options)
                if (publicLevels.isNotEmpty()) {
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel + publicLevels
                    persistThinkingCatalogCache()
                }

                val providerConfigIds = options.mapTo(mutableSetOf(), ProviderModelOption::providerConfigId)
                val configs = providerConfigs.filter { it.id in providerConfigIds }
                val runtimeCatalog = runSharedAppCatching {
                    bridgeClient.listProviders(startIfNeeded = false)
                }.getOrNull()
                if (runtimeCatalog != null && configs.isNotEmpty()) {
                    val runtimeResult = withContext(Dispatchers.Default) {
                        val levels = mutableMapOf<String, List<String>>()
                        val clamps = mutableMapOf<String, Map<String, String>>()
                        configs.distinctBy(LlmProviderConfig::piProviderId).forEach { config ->
                            val result = providerModelsFromCatalog(runtimeCatalog, config.piProviderId)
                            result.thinkingLevelsByModel.forEach { (modelId, supported) ->
                                levels[sharedThinkingCatalogKey(config.piProviderId, modelId)] = supported
                            }
                            result.thinkingLevelClampsByModel.forEach { (modelId, supported) ->
                                clamps[sharedThinkingCatalogKey(config.piProviderId, modelId)] = supported
                            }
                        }
                        levels to clamps
                    }
                    val refreshedKeys = options.mapTo(mutableSetOf()) { option ->
                        sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                    }
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel + runtimeResult.first
                    thinkingLevelClampsByProviderModel =
                        thinkingLevelClampsByProviderModel.filterKeys { it !in refreshedKeys } + runtimeResult.second
                    persistThinkingCatalogCache()
                }
                true
            }.getOrDefault(false)
        }

        LaunchedEffect(route, modelCatalogRequestKey) {
            if (route == SharedRoute.Chat && modelOptions.isNotEmpty()) {
                refreshThinkingCatalog()
            }
        }

        fun extensionContext(state: SharedSessionUiState = currentSession): JsonObject = buildJsonObject {
            put("screen", route.name.lowercase())
            put("session_id", state.id)
            put("draft_input", state.input)
            put("is_generating", state.isWorking)
            put("selected_model_key", state.selectedModelKey)
            put("reasoning_effort", sharedAppSettings.reasoningEffort)
            put("message_count", state.messages.size)
        }

        fun resolveProviderForModel(preferredKey: String, fallbackKey: String = ""): LlmProviderConfig? {
            return resolveSharedProviderForModel(
                providerConfigs = providerConfigs,
                baseConfig = providerConfig,
                preferredKey = preferredKey,
                fallbackKey = fallbackKey,
            )
        }

        fun generateSessionTitle(
            target: SharedSessionUiState,
            seedMessage: SharedChatMessage,
            fallbackConfig: LlmProviderConfig,
        ) {
            val titleInput = buildSharedTitleGenerationInput(seedMessage)
            if (titleInput.isBlank()) return
            val modelOptions = providerConfigs.availableModelOptions()
            val titleModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultTitleModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Title,
                fallbackPurpose = AutomaticModelPurpose.Chat,
            )
            val fallbackModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultChatModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Chat,
            )
            val titleConfig = resolveProviderForModel(
                preferredKey = titleModelKey,
                fallbackKey = fallbackModelKey,
            ) ?: fallbackConfig
            if (!titleConfig.isSharedProviderSetupValid()) return
            appScope.launch {
                val result = runSharedAppCatching {
                    completionClient.completeOnce(
                        config = titleConfig,
                        messages = listOf(SharedPiChatMessage("user", titleInput)),
                        systemPrompt = SharedSessionTitleSystemPrompt,
                        reasoning = "off",
                        timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                            .coerceIn(30, 3_600) * 1_000,
                    )
                }.getOrNull() ?: return@launch
                val title = result.assistantText.sanitizeSharedSessionTitle()
                if (title.isBlank()) return@launch
                if (sessionStates[target.id] !== target) return@launch
                val firstUserMessage = target.messages.firstOrNull { it.fromUser }
                if (firstUserMessage?.id == seedMessage.id) {
                    target.title = title
                    target.hasCustomTitle = true
                    persistSession(target)
                }
            }
        }

        fun enqueueReasoningSummary(
            target: SharedSessionUiState,
            assistantId: String,
            tracker: SharedReasoningTurnTracker,
            forceRemaining: Boolean,
            fallbackConfig: LlmProviderConfig,
        ) {
            var submission: SharedReasoningSummarySubmission? = null
            val now = platformCurrentTimeMillis()
            target.messages.updateMessage(assistantId) { current ->
                val trace = current.activeSharedReasoningTrace() ?: return@updateMessage current
                tracker.prepareSummary(trace, forceRemaining, now)?.let { prepared ->
                    submission = prepared
                    current.withPendingReasoningSummary(prepared)
                } ?: current
            }
            val prepared = submission ?: return
            val modelOptions = providerConfigs.availableModelOptions()
            val titleModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultTitleModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Title,
                fallbackPurpose = AutomaticModelPurpose.Chat,
            )
            val fallbackModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultChatModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Chat,
            )
            val summaryConfig = resolveProviderForModel(
                preferredKey = titleModelKey,
                fallbackKey = fallbackModelKey,
            ) ?: fallbackConfig
            appScope.launch {
                if (sessionStates[target.id] !== target) return@launch
                val summary = if (summaryConfig.isSharedProviderSetupValid()) {
                    try {
                        val result = completionClient.completeOnce(
                            config = summaryConfig,
                            messages = listOf(
                                SharedPiChatMessage(
                                    role = "user",
                                    text = buildSharedReasoningSummaryPrompt(prepared.chunk.rawText),
                                )
                            ),
                            systemPrompt = SharedReasoningSummarySystemPrompt,
                            reasoning = "off",
                            timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                                .coerceIn(30, 3_600) * 1_000,
                        )
                        if (result.errorMessage.isBlank()) {
                            parseSharedReasoningSummary(result.assistantText)
                        } else {
                            null
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                } else {
                    null
                } ?: fallbackSharedReasoningSummary(prepared.chunk.rawText)
                if (sessionStates[target.id] !== target) return@launch
                target.messages.updateMessage(assistantId) { current ->
                    current.withCompletedReasoningSummary(
                        blockId = prepared.blockId,
                        chunkId = prepared.chunk.id,
                        title = summary.title,
                        detail = summary.detail,
                    )
                }
                if (target.job?.isActive != true && sessionStates[target.id] === target) {
                    persistSession(target)
                }
            }
        }

        fun compactSession(
            target: SharedSessionUiState,
            allowRunning: Boolean = false,
            onCompleted: () -> Unit = {},
        ) {
            if (target.editingMessageId.isNotBlank()) {
                transientMessage = finishEditingBeforeCompactingMessage
                return
            }
            if (target.isDraft) {
                transientMessage = noConversationToCompactMessage
                return
            }
            if (!allowRunning && target.job?.isActive == true) {
                transientMessage = pauseBeforeCompactingMessage
                return
            }
            if (target.messages.size < 2) {
                transientMessage = notEnoughConversationMessage
                return
            }
            val compactInput = buildSharedCompactConversationInput(target.messages)
            if (compactInput.isBlank()) {
                transientMessage = noTextToCompactMessage
                return
            }
            target.input = ""
            val modelOptions = providerConfigs.availableModelOptions()
            val compactingModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultCompactingModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Compacting,
                fallbackPurpose = AutomaticModelPurpose.Chat,
            )
            val fallbackModelKey = resolveSharedStoredOrAutomaticModelKey(
                storedKey = sharedAppSettings.defaultChatModelKey,
                options = modelOptions,
                purpose = AutomaticModelPurpose.Chat,
            )
            val compactConfig = resolveProviderForModel(
                preferredKey = compactingModelKey,
                fallbackKey = fallbackModelKey,
            )
            if (compactConfig == null || !compactConfig.isSharedProviderSetupValid()) {
                transientMessage = configureProviderBeforeCompactingMessage
                return
            }
            target.streamingStatus = SharedCompactingStatus
            target.job = appScope.launch {
                try {
                    runSharedAppCatching {
                        completionClient.completeOnce(
                            config = compactConfig,
                            messages = listOf(SharedPiChatMessage("user", compactInput)),
                            systemPrompt = SharedSessionCompactingSystemPrompt,
                            reasoning = "off",
                            timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                                .coerceIn(30, 3_600) * 1_000,
                        )
                    }.fold(
                        onSuccess = { result ->
                            val summary = result.assistantText.trim()
                            if (result.errorMessage.isNotBlank() || summary.isBlank()) {
                                val detail = result.errorMessage.ifBlank { "empty model response" }
                                transientMessage = "$compactionFailedPrefix $detail".trim()
                            } else {
                                val now = platformCurrentTimeMillis()
                                target.messages += SharedChatMessage(
                                    id = "compact-context-$now",
                                    text = buildSharedCompactedContextMessage(summary),
                                    fromUser = true,
                                    createdAtMillis = now,
                                    providerPayloadJson = result.providerPayloadJson,
                                    displayKind = SharedMessageDisplayKind.HiddenContext,
                                )
                                target.messages += SharedChatMessage(
                                    id = "compact-status-$now",
                                    text = "Context compacted",
                                    fromUser = false,
                                    createdAtMillis = now + 1,
                                    assistantActionsHidden = true,
                                    displayKind = SharedMessageDisplayKind.CompactStatus,
                                )
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException && error !is TimeoutCancellationException) {
                                throw error
                            }
                            transientMessage = "$compactionFailedPrefix ${error.message.orEmpty()}".trim()
                        },
                    )
                } finally {
                    target.streamingStatus = ""
                    target.job = null
                    endBackgroundExecution(target)
                    persistSession(target)
                    onCompleted()
                }
            }
            ensureBackgroundExecution(target)
        }

        fun startChatTurn(
            rawValue: String,
            attachments: List<SharedChatAttachment> = emptyList(),
            retryResponseGroupId: String = "",
            target: SharedSessionUiState = currentSession,
        ) {
            val value = rawValue.trim()
            if (value.isEmpty() && attachments.isEmpty()) return
            if (value.equals(SharedCompactCommand, ignoreCase = true)) {
                compactSession(target)
                return
            }
            if (target.job?.isActive == true) {
                target.queuedTurns += SharedPendingTurn(text = value, attachments = attachments)
                target.input = ""
                return
            }
            val modelOptions = providerConfigs.availableModelOptions()
            val requestedKey = target.selectedModelKey.ifBlank {
                resolveSharedConversationModelKey(
                    selectedModelKey = "",
                    defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                    options = modelOptions,
                )
            }
            val fallbackModelKey = resolveSharedConversationModelKey(
                selectedModelKey = "",
                defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                options = modelOptions,
            )
            val config = resolveProviderForModel(requestedKey, fallbackModelKey)
            val shouldGenerateTitle = target.isDraft
            if (target.isDraft) {
                target.id = "aether-session-${platformRandomUuid()}"
                target.isDraft = false
                target.hasCustomTitle = true
                sessionStates[target.id] = target
                if (currentSession === target) sessionId = target.id
                sessions.add(0, SharedConversationSummary(target.id, target.title))
            }
            val editingIndex = target.editingMessageId.takeIf(String::isNotBlank)?.let { editingId ->
                target.messages.indexOfFirst { it.id == editingId && it.fromUser }
            } ?: -1
            val replacementMessage = SharedChatMessage(
                text = value,
                fromUser = true,
                attachments = attachments,
                createdAtMillis = platformCurrentTimeMillis(),
            )
            val userMessage = when {
                retryResponseGroupId.isNotBlank() -> {
                    target.messages.lastOrNull { it.id == retryResponseGroupId && it.fromUser }
                        ?: return
                }
                editingIndex >= 0 -> {
                    val updated = createEditedSharedMessageBranch(
                        messages = target.messages,
                        messageId = target.editingMessageId,
                        replacement = replacementMessage,
                    ) ?: return
                    target.messages.clear()
                    target.messages.addAll(updated)
                    target.editingMessageId = ""
                    target.messages.getOrNull(editingIndex) ?: return
                }
                else -> replacementMessage.also {
                    target.messages += it
                }
            }
            target.input = ""
            if (config == null || !config.isSharedProviderSetupValid()) {
                val completedAt = platformCurrentTimeMillis()
                target.messages += SharedChatMessage(
                    text = SharedProviderValidationErrorText,
                    fromUser = false,
                    responseGroupId = "agent-group-$completedAt",
                    createdAtMillis = completedAt,
                    completedAtMillis = completedAt,
                )
                target.streamingStatus = ""
                if (target.id != currentSession.id) target.hasUnviewedCompletion = true
                appScope.launch { persistSession(target, moveToFront = true) }
                return
            }
            val assistantId = platformRandomUuid()
            val turnStartedAt = platformCurrentTimeMillis()
            var responseStartedAt = 0L
            val reasoningTracker = SharedReasoningTurnTracker()
            target.messages += SharedChatMessage(
                id = assistantId,
                text = "",
                fromUser = false,
                isStreaming = true,
                status = SharedInitialStreamingStatusText,
                statusDetail = SharedInitialStreamingStatusDetail,
                responseGroupId = "agent-group-$turnStartedAt",
                createdAtMillis = turnStartedAt,
                providerId = config.id,
                modelId = config.modelId,
            )
            target.streamingStatus = SharedInitialStreamingStatusText
            target.hasUnviewedCompletion = false
            target.job = appScope.launch {
                val runningJob = currentCoroutineContext()[Job]
                var shouldAutoCompact = false
                try {
                    persistSession(target, moveToFront = true)
                if (shouldGenerateTitle) generateSessionTitle(target, userMessage, config)
                extensionManagerRef?.dispatchEvent(
                    event = "before_send",
                    data = buildJsonObject {
                        put("text", value)
                        put("session_id", target.id)
                    },
                    context = extensionContext(target),
                )
                extensionManagerRef?.dispatchEvent(
                    event = "message_sent",
                    data = buildJsonObject {
                        put("message_id", userMessage.id)
                        put("text", userMessage.text)
                    },
                    context = extensionContext(target),
                )
                val syncedMessages = target.messages.syncSharedUserBranches()
                val compactContextIndex = syncedMessages.indexOfLast {
                    it.displayKind == SharedMessageDisplayKind.HiddenContext
                }
                val retainedMessages = if (compactContextIndex >= 0) {
                    syncedMessages.drop(compactContextIndex)
                } else {
                    syncedMessages
                }
                val requestMessages = retainedMessages.filter {
                        it.id != assistantId && !it.isError && it.isActiveBranch &&
                            it.displayKind != SharedMessageDisplayKind.CompactStatus
                    }
                val estimatedUsage = estimateSharedRequestTokenUsage(requestMessages)
                val turnMessages = requestMessages.map { message ->
                    message.toPiChatMessage(
                        supportsInlineImageWithTools = sharedSupportsInlineImageWithTools(config),
                    )
                }
                val skillSelection = runSharedAppCatching {
                    skillManager.resolveTurnSkills(
                        selectedIds = target.selectedSkillIds.toList(),
                        requestText = buildSharedImplicitSkillRequestText(requestMessages),
                    )
                }.getOrElse {
                    com.zhousl.aether.data.SharedTurnSkillSelection(
                        selectedSkillIds = target.selectedSkillIds.toList(),
                        activeSkills = target.activeSkills.toList(),
                        availableSkills = installedSkills.filter(SharedInstalledSkill::isEnabled),
                    )
                }
                target.selectedSkillIds.replaceSharedContents(skillSelection.selectedSkillIds)
                target.activeSkills.replaceSharedContents(skillSelection.activeSkills)
                val enabledMcpServersById = mcpServers
                    .filter(SharedMcpServerConfig::enabled)
                    .associateBy(SharedMcpServerConfig::id)
                val resolvedMcpServers = target.activeMcpServerIds.distinct()
                    .mapNotNull(enabledMcpServersById::get)
                target.activeMcpServerIds.replaceSharedContents(
                    resolvedMcpServers.map(SharedMcpServerConfig::id),
                )
                persistSession(target)
                runSharedAppCatching {
                    mcpManager.refreshBindings(resolvedMcpServers, sessionId = target.id)
                }.onFailure(::reportMcpRefreshFailure)
                runSharedAppCatching {
                    chatClient.runTurn(
                        config = config,
                        messages = turnMessages,
                        sessionId = target.id,
                        systemPrompt = buildSharedPiAgentInstructions(
                            configuredPrompt = sharedAppSettings.systemPrompt,
                            workspaceDirectory = runtime.workspaceRoot,
                            availableSkills = skillSelection.availableSkills,
                            activeSkills = skillSelection.activeSkills,
                        ),
                        reasoning = sharedAppSettings.reasoningEffort,
                        timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                            .coerceIn(30, 3_600) * 1_000,
                        onAssistantTextDelta = { delta ->
                            backgroundLeases[target.id]?.update("Writing response")
                            reasoningTracker.finishDirectSummaryChunk()
                            val now = platformCurrentTimeMillis()
                            if (responseStartedAt == 0L) responseStartedAt = now
                            enqueueReasoningSummary(
                                target = target,
                                assistantId = assistantId,
                                tracker = reasoningTracker,
                                forceRemaining = true,
                                fallbackConfig = config,
                            )
                            target.messages.updateMessage(assistantId) { current ->
                                current.completeAssistantReasoning(now).appendAssistantTextDelta(delta).copy(
                                    status = "",
                                    statusDetail = "",
                                    firstTokenLatencyMillis = current.firstTokenLatencyMillis
                                        ?: (now - turnStartedAt).coerceAtLeast(0L),
                                )
                            }
                            target.streamingStatus = ""
                        },
                        onAssistantReasoningDelta = { delta ->
                            backgroundLeases[target.id]?.update("Reasoning")
                            reasoningTracker.finishDirectSummaryChunk()
                            val now = platformCurrentTimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                current.appendAssistantReasoningDelta(delta, now).copy(
                                    status = SharedInitialStreamingStatusText,
                                    statusDetail = SharedInitialStreamingStatusDetail,
                                )
                            }
                            enqueueReasoningSummary(
                                target = target,
                                assistantId = assistantId,
                                tracker = reasoningTracker,
                                forceRemaining = false,
                                fallbackConfig = config,
                            )
                        },
                        onAssistantReasoningSummaryDelta = { delta ->
                            backgroundLeases[target.id]?.update("Reasoning")
                            val now = platformCurrentTimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                current.appendDirectAssistantReasoningSummaryDelta(
                                    delta = delta,
                                    tracker = reasoningTracker,
                                    nowMillis = now,
                                ).copy(
                                    status = SharedInitialStreamingStatusText,
                                    statusDetail = SharedInitialStreamingStatusDetail,
                                )
                            }
                        },
                        onHostToolStarted = { call ->
                            backgroundLeases[target.id]?.update("Running ${call.name}")
                            reasoningTracker.finishDirectSummaryChunk()
                            val now = platformCurrentTimeMillis()
                            enqueueReasoningSummary(
                                target = target,
                                assistantId = assistantId,
                                tracker = reasoningTracker,
                                forceRemaining = true,
                                fallbackConfig = config,
                            )
                            target.messages.updateMessage(assistantId) { current ->
                                current.withStartedAssistantTool(
                                    call = call,
                                    startedAtMillis = now,
                                    timelineOrder = reasoningTracker.nextTimelineOrder(),
                                ).copy(
                                    status = SharedInitialStreamingStatusText,
                                    statusDetail = SharedInitialStreamingStatusDetail,
                                )
                            }
                        },
                        onHostToolFinished = { call, result ->
                            backgroundLeases[target.id]?.update("Finished ${call.name}")
                            val now = platformCurrentTimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                current.withFinishedAssistantTool(call.id, result, now)
                            }
                        },
                        onStreamingStatus = { status ->
                            status?.text?.takeIf(String::isNotBlank)
                                ?.let { backgroundLeases[target.id]?.update(it) }
                            target.messages.updateMessage(assistantId) { current ->
                                current.copy(
                                    status = status?.text.orEmpty(),
                                    statusDetail = status?.detail.orEmpty(),
                                )
                            }
                            target.streamingStatus = status?.text.orEmpty()
                        },
                        pollInjectedUserMessages = {
                            val drained = target.queuedTurns.filter {
                                it.mode == SharedPendingTurnMode.Steer
                            }
                            if (drained.isEmpty()) {
                                emptyList()
                            } else {
                                val drainedIds = drained.map(SharedPendingTurn::id).toSet()
                                target.queuedTurns.removeAll { it.id in drainedIds }
                                val userMessages = drained.map { pending ->
                                    SharedChatMessage(
                                        text = pending.text,
                                        fromUser = true,
                                        attachments = pending.attachments,
                                        createdAtMillis = pending.createdAtMillis,
                                    )
                                }
                                val assistantIndex = target.messages.indexOfLast {
                                    !it.fromUser && it.isStreaming
                                }
                                if (assistantIndex >= 0) {
                                    val replacement = splitSharedAssistantForAcceptedSteers(
                                        pendingAssistant = target.messages[assistantIndex],
                                        userMessages = userMessages,
                                    )
                                    target.messages.removeAt(assistantIndex)
                                    target.messages.addAll(assistantIndex, replacement)
                                } else {
                                    target.messages += userMessages
                                }
                                appScope.launch { persistSession(target) }
                                userMessages.map { userMessage ->
                                    userMessage.withSharedSteerInstruction().toPiChatMessage(
                                        supportsInlineImageWithTools = sharedSupportsInlineImageWithTools(config),
                                    )
                                }
                            }
                        },
                    )
                }.fold(
                    onSuccess = { result ->
                        val completedAt = platformCurrentTimeMillis()
                        val resolvedUsage = if (result.usageAvailable) result.usage else estimatedUsage
                        target.messages.updateMessage(assistantId) { current ->
                            current.withAssistantResultFallback(result)
                        }
                        enqueueReasoningSummary(
                            target = target,
                            assistantId = assistantId,
                            tracker = reasoningTracker,
                            forceRemaining = true,
                            fallbackConfig = config,
                        )
                        target.messages.updateMessage(assistantId) { current ->
                            val completed = current.completeAssistantReasoning(completedAt)
                            val finalized = if (result.errorMessage.isNotBlank()) {
                                completed.withSharedRequestFailure(
                                    result.errorMessage,
                                    requestFailureTemplate,
                                    requestErrorPlaceholder,
                                    unknownErrorMessage,
                                )
                            } else {
                                completed.withAssistantTextResultFallback(result)
                            }
                            val hasAgentWork = current.reasoningText.isNotBlank() ||
                                current.responseBlocks.any { block ->
                                    block is SharedAssistantResponseBlock.Reasoning ||
                                        block is SharedAssistantResponseBlock.ToolGroup
                                }
                            finalized.copy(
                                reasoningText = current.reasoningText.ifBlank { result.reasoningText },
                                isError = false,
                                isStreaming = false,
                                status = "",
                                statusDetail = "",
                                completedAtMillis = completedAt,
                                usage = resolvedUsage,
                                tokenUsageSource = if (result.usageAvailable) "api" else "estimated",
                                providerId = result.provider.ifBlank { config.id },
                                modelId = result.model.ifBlank { config.modelId },
                                providerPayloadJson = result.providerPayloadJson,
                                thoughtDurationMillis = if (hasAgentWork) {
                                    ((responseStartedAt.takeIf { it > 0L } ?: completedAt) - turnStartedAt)
                                        .coerceAtLeast(0L)
                                } else {
                                    0L
                                },
                                responseDurationMillis = if (responseStartedAt > 0L) {
                                    (completedAt - responseStartedAt).coerceAtLeast(0L)
                                } else {
                                    0L
                                },
                            )
                        }
                        val completedAssistant = target.messages.lastOrNull { it.id == assistantId }
                        shouldAutoCompact = result.errorMessage.isBlank() && completedAssistant != null &&
                            shouldAutoCompactSharedContext(
                                usage = completedAssistant.usage,
                                tokenUsageSource = completedAssistant.tokenUsageSource,
                                assistantText = completedAssistant.text,
                            )
                        if (
                            result.errorMessage.isBlank() &&
                            shouldMarkOnboardingCompleted(
                                settings = sharedAppSettings,
                                isSuccessfulAssistantReply = true,
                            )
                        ) {
                            sharedAppSettings = sharedAppSettings.copy(
                                onboardingCompletedVersion = CurrentOnboardingVersion,
                            )
                            appScope.launch { settingsStore?.markOnboardingComplete() }
                        }
                        if (
                            shouldRevealFollowUpTourCard(
                                isAwaitingFollowUpTour = awaitingFollowUpTour,
                                isSuccessfulAssistantReply = result.errorMessage.isBlank(),
                            )
                        ) {
                            awaitingFollowUpTour = false
                            appScope.launch {
                                kotlinx.coroutines.delay(FollowUpTourAutoOpenDelayMillis)
                                if (route == SharedRoute.Chat) {
                                    onboardingEntryStage = OnboardingStage.Search
                                    onboardingReplayMode = false
                                    route = SharedRoute.Onboarding
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException && error !is TimeoutCancellationException) {
                            throw error
                        }
                        val completedAt = platformCurrentTimeMillis()
                        enqueueReasoningSummary(
                            target = target,
                            assistantId = assistantId,
                            tracker = reasoningTracker,
                            forceRemaining = true,
                            fallbackConfig = config,
                        )
                        target.messages.updateMessage(assistantId) { current ->
                            current.completeAssistantReasoning(completedAt)
                                .withSharedRequestFailure(
                                    sharedFailureMessage(error),
                                    requestFailureTemplate,
                                    requestErrorPlaceholder,
                                    unknownErrorMessage,
                                ).copy(
                                isError = false,
                                isStreaming = false,
                                status = "",
                                statusDetail = "",
                                completedAtMillis = completedAt,
                                usage = estimatedUsage,
                                tokenUsageSource = "estimated",
                            )
                        }
                    },
                )
                target.messages.lastOrNull { it.id == assistantId }
                    ?.takeUnless(SharedChatMessage::hasSharedVisibleAssistantWork)
                    ?.let { emptyMessage -> target.messages.removeAll { it.id == emptyMessage.id } }
                target.streamingStatus = ""
                endBackgroundExecution(target)
                if (target.id != currentSession.id) {
                    target.hasUnviewedCompletion = true
                }
                persistSession(target)
                extensionManagerRef?.dispatchEvent(
                    event = "turn_complete",
                    data = buildJsonObject {
                        put("session_id", target.id)
                        put("assistant_message_id", assistantId)
                    },
                    context = extensionContext(target),
                )
                val promotedTurns = target.queuedTurns.promoteSharedSteersToQueue()
                if (promotedTurns != target.queuedTurns) {
                    target.queuedTurns.clear()
                    target.queuedTurns.addAll(promotedTurns)
                }
                if (shouldAutoCompact) {
                    compactSession(target, allowRunning = true) {
                        val queuedIndex = target.queuedTurns.nextSharedQueuedTurnIndex()
                        if (queuedIndex >= 0) {
                            val queued = target.queuedTurns.removeAt(queuedIndex)
                            startChatTurn(queued.text, queued.attachments, target = target)
                        } else {
                            appScope.launch { persistSession(target) }
                        }
                    }
                    return@launch
                }
                val nextIndex = target.queuedTurns.nextSharedQueuedTurnIndex()
                target.job = null
                if (nextIndex >= 0) {
                    val next = target.queuedTurns.removeAt(nextIndex)
                    startChatTurn(next.text, next.attachments, target = target)
                } else {
                    persistSession(target)
                }
                } finally {
                    if (target.job === runningJob) {
                        withContext(NonCancellable) {
                            val completedAt = platformCurrentTimeMillis()
                            target.messages.lastOrNull { it.id == assistantId && it.isStreaming }
                                ?.let { pending ->
                                    val finalized = pending.finalizeSharedInterruptedAssistantWork(
                                        status = chatInterruptedStatus,
                                        completedAtMillis = completedAt,
                                    )
                                    if (finalized.hasSharedVisibleAssistantWork()) {
                                        target.messages.updateMessage(pending.id) { finalized }
                                    } else {
                                        target.messages.removeAll { it.id == pending.id }
                                    }
                                }
                            target.streamingStatus = ""
                            target.job = null
                            endBackgroundExecution(target)
                            if (target.id != currentSession.id) {
                                target.hasUnviewedCompletion = true
                            }
                            persistSession(target)
                        }
                    }
                }
            }
            ensureBackgroundExecution(target)
        }

        fun createNewSession(useDefaultSkills: Boolean = true): SharedSessionUiState {
            currentSession.clearComposerDraft()
            val resolvedDefaultModelKey = resolveSharedConversationModelKey(
                selectedModelKey = "",
                defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                options = providerConfigs.availableModelOptions(),
            )
            val state = SharedSessionUiState(
                id = SharedDraftSessionId,
                isDraft = true,
                selectedSkillIds = if (useDefaultSkills) {
                    sharedAppSettings.defaultSelectedSkillIds.filter { defaultId ->
                        installedSkills.any { it.id == defaultId && it.isEnabled }
                    }
                } else {
                    emptyList()
                },
                activeMcpServerIds = emptyList(),
                selectedModelKey = resolvedDefaultModelKey,
            )
            currentSession = state
            sessionId = state.id
            appScope.launch {
                historyStore?.setCurrentSession(state.id)
                runSharedAppCatching {
                    mcpManager.refreshBindings(emptyList(), sessionId = state.id)
                }
                    .onFailure(::reportMcpRefreshFailure)
            }
            return state
        }

        fun showSession(state: SharedSessionUiState) {
            currentSession.clearComposerDraft()
            state.clearComposerDraft()
            currentSession = state
            sessionId = state.id
            state.hasUnviewedCompletion = false
            appScope.launch {
                historyStore?.setCurrentSession(state.id)
                runSharedAppCatching {
                    mcpManager.refreshBindings(
                        mcpServers.filter { it.enabled && it.id in state.activeMcpServerIds },
                        sessionId = state.id,
                    )
                }.onFailure(::reportMcpRefreshFailure)
                persistSession(state)
            }
        }

        fun exportSession(exportSessionId: String) {
            val state = sessionStates[exportSessionId] ?: return
            val persistedMessages = state.messages.toPersistedMessages()
            val json = serializePersistedChatSession(
                PersistedChatSession(
                    id = state.id,
                    title = state.title,
                    preview = deriveSharedSessionMetadata(persistedMessages).second,
                    messages = persistedMessages,
                    hasCustomTitle = state.hasCustomTitle,
                    selectedSkillIds = state.selectedSkillIds.toList(),
                    activeSkills = state.activeSkills.toList(),
                    activeMcpServerIds = state.activeMcpServerIds.toList(),
                    chromeEnabled = chromeEnabled,
                    selectedModelKey = state.selectedModelKey,
                )
            )
            val fileName = state.title.sanitizeSharedExportFileName() + ".json"
            appScope.launch {
                runSharedAppCatching {
                    platformServices.exportFile(fileName, "application/json", json.encodeToByteArray())
                }.fold(
                    onSuccess = { exported ->
                        when (exported) {
                            true -> transientMessage = sessionExportedMessage
                            false -> transientMessage = sessionExportFailedMessage
                            null -> Unit
                        }
                    },
                    onFailure = {
                        transientMessage = sessionExportFailedMessage
                    },
                )
            }
        }

        suspend fun handleSharedExtensionHostCall(method: String, args: JsonObject): JsonObject =
            when (method) {
                    "app.getState", "state.get" -> extensionContext()
                    "app.setDraftInput" -> withContext(Dispatchers.Main) {
                        currentSession.input = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        buildJsonObject { put("updated", true) }
                    }
                    "app.appendDraftInput" -> withContext(Dispatchers.Main) {
                        currentSession.input += args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        buildJsonObject { put("updated", true) }
                    }
                    "app.sendMessage" -> withContext(Dispatchers.Main) {
                        val text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val mode = args["mode"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        when (mode.lowercase()) {
                            "queue" -> currentSession.queuedTurns += SharedPendingTurn(text = text)
                            "steer" -> appScope.launch {
                                val message = SharedChatMessage(text = text, fromUser = true)
                                if (chatClient.steer(currentSession.id, message.toPiChatMessage())) {
                                    currentSession.messages += message
                                    persistSession(currentSession)
                                }
                            }
                            else -> startChatTurn(text)
                        }
                        buildJsonObject { put("submitted", true); put("mode", mode.ifBlank { "send" }) }
                    }
                    "app.newChat" -> withContext(Dispatchers.Main) {
                        createNewSession()
                        route = SharedRoute.Chat
                        buildJsonObject { put("opened", "chat") }
                    }
                    "app.selectSession" -> withContext(Dispatchers.Main) {
                        val id = args["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        sessionStates[id]?.let(::showSession)
                        buildJsonObject { put("selected", currentSession.id == id) }
                    }
                    "app.pauseGeneration" -> withContext(Dispatchers.Main) {
                        val target = args["session_id"]?.jsonPrimitive?.contentOrNull
                            ?.let(sessionStates::get) ?: currentSession
                        target.job?.cancel()
                        target.job = null
                        target.streamingStatus = ""
                        target.queuedTurns.clear()
                        val completedAt = platformCurrentTimeMillis()
                        target.messages.lastOrNull { !it.fromUser && it.isStreaming }?.let { pending ->
                            val finalized = pending.finalizeSharedInterruptedAssistantWork(
                                status = chatStoppedStatus,
                                completedAtMillis = completedAt,
                            )
                            if (finalized.hasSharedVisibleAssistantWork()) {
                                target.messages.updateMessage(pending.id) { finalized }
                            } else {
                                target.messages.removeAll { it.id == pending.id }
                            }
                        }
                        endBackgroundExecution(target)
                        persistSession(target)
                        buildJsonObject { put("paused", true) }
                    }
                    "app.setReasoningEffort" -> withContext(Dispatchers.Main) {
                        val value = args["effort"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        sharedAppSettings = sharedAppSettings.copy(reasoningEffort = value)
                        appScope.launch { settingsStore?.saveGeneralSettings(sharedAppSettings) }
                        buildJsonObject { put("updated", true) }
                    }
                    "app.setModel" -> withContext(Dispatchers.Main) {
                        val key = args["model_key"]?.jsonPrimitive?.contentOrNull
                            ?: args["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val valid = providerConfigs.availableModelOptions().any { it.key == key }
                        if (valid) currentSession.selectedModelKey = key
                        buildJsonObject { put("updated", valid) }
                    }
                    "app.openScreen" -> withContext(Dispatchers.Main) {
                        val screen = args["screen"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        route = if (screen.equals("settings", true)) SharedRoute.Settings else SharedRoute.Chat
                        buildJsonObject { put("opened", screen) }
                    }
                    "app.notify" -> withContext(Dispatchers.Main) {
                        transientMessage = args["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        buildJsonObject { put("notified", transientMessage.isNotBlank()) }
                    }
                    "settings.get" -> buildJsonObject {
                        put("system_prompt", sharedAppSettings.systemPrompt)
                        put("reasoning_effort", sharedAppSettings.reasoningEffort)
                        put("theme", sharedAppSettings.themeMode.storageValue)
                        put("language", sharedAppSettings.language.storageValue)
                        put("tavily_api_key", sharedAppSettings.tavilyApiKey)
                        put("tavily_base_url", sharedAppSettings.tavilyBaseUrl)
                        put("provider_configs", JsonArray(providerConfigs.map { it.toJsonObject() }))
                    }
                    "settings.patch" -> withContext(Dispatchers.Main) {
                        args["system_prompt"]?.jsonPrimitive?.contentOrNull?.let {
                            sharedAppSettings = sharedAppSettings.copy(systemPrompt = it)
                        }
                        args["reasoning_effort"]?.jsonPrimitive?.contentOrNull?.let {
                            sharedAppSettings = sharedAppSettings.copy(reasoningEffort = it)
                        }
                        args["tavily_api_key"]?.jsonPrimitive?.contentOrNull?.let {
                            sharedAppSettings = sharedAppSettings.copy(tavilyApiKey = it)
                        }
                        settingsStore?.saveGeneralSettings(sharedAppSettings)
                        buildJsonObject { put("updated", true) }
                    }
                    "state.transaction" -> withContext(Dispatchers.Main) {
                        args["draft_input"]?.jsonPrimitive?.contentOrNull?.let { currentSession.input = it }
                        args["model_key"]?.jsonPrimitive?.contentOrNull?.let { key ->
                            if (providerConfigs.availableModelOptions().any { it.key == key }) {
                                currentSession.selectedModelKey = key
                            }
                        }
                        extensionContext()
                    }
                    "runtime.execute" -> {
                        val command = args["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        runtimeTools.execute("bash", buildJsonObject { put("command", command) })
                            .outputJson
                            .let { Json.parseToJsonElement(it) as? JsonObject ?: JsonObject(emptyMap()) }
                    }
                    "kernel.listServices" -> buildJsonObject {
                        put("services", JsonArray(listOf("app", "settings", "state", "runtime").map(::JsonPrimitive)))
                    }
                    "kernel.describeService" -> buildJsonObject {
                        val name = args["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        put("name", name)
                        put("available", name in setOf("app", "settings", "state", "runtime"))
                    }
                    "service.invoke" -> {
                        val service = args["service"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val operation = args["operation"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val nested = args["args"] as? JsonObject ?: JsonObject(emptyMap())
                        val mapped = when (service) {
                            "app" -> "app.$operation"
                            "settings" -> "settings.$operation"
                            "state" -> "state.$operation"
                            "runtime" -> "runtime.$operation"
                            else -> error("Unknown extension service: $service")
                        }
                        handleSharedExtensionHostCall(mapped, nested)
                    }
                    else -> error("Unsupported Aether extension host method on this platform: " + method)
                }

        val extensionManager = remember(bridgeClient) {
            SharedAetherExtensionManager(bridgeClient, ::handleSharedExtensionHostCall)
        }
        val extensionDraftRefreshJob = remember(extensionManager) { SharedNonSnapshotJobSlot() }

        fun scheduleExtensionDraftRefresh() {
            if (!capabilities.scriptExtensions || route == SharedRoute.Onboarding) return
            extensionDraftRefreshJob.job?.cancel()
            extensionDraftRefreshJob.job = appScope.launch {
                kotlinx.coroutines.delay(250)
                runSharedAppCatching { extensionManager.refresh(extensionContext()) }
                    .onSuccess { extensionSnapshot = it }
                extensionManager.notification.takeIf(String::isNotBlank)
                    ?.let { transientMessage = it }
            }
        }

        DisposableEffect(extensionManager) {
            onDispose { extensionDraftRefreshJob.job?.cancel() }
        }

        LaunchedEffect(extensionManager) {
            extensionManagerRef = extensionManager
        }

        LaunchedEffect(
            route,
            currentSession.id,
            currentSession.isWorking,
            currentSession.selectedModelKey,
            currentSession.messages.size,
            capabilities.scriptExtensions,
        ) {
            if (capabilities.scriptExtensions && route != SharedRoute.Onboarding) {
                runSharedAppCatching { extensionManager.refresh(extensionContext()) }
                    .onSuccess { extensionSnapshot = it }
                extensionManager.notification.takeIf(String::isNotBlank)?.let { transientMessage = it }
            }
        }

        val extensionController = SharedAetherExtensionUiController(
            snapshot = extensionSnapshot,
            onAction = { extensionId, action, args ->
                appScope.launch {
                    runSharedAppCatching {
                        extensionManager.invokeAction(extensionId, action, args, extensionContext())
                    }
                        .onSuccess { extensionSnapshot = it }
                }
            },
            onOpenPage = { activeExtensionPageId = it },
        )
        val pauseBeforeDeletingSessionMessage =
            stringResource(Res.string.message_pause_before_deleting_session)

        SharedAetherExtensionUiProvider(extensionController) {
        BoxWithConstraints {
        val useTabletLayout = shouldUseSharedTabletLayout(
            supportsTabletLayout = capabilities.supportsTabletLayout,
            availableWidthDp = maxWidth.value,
        )
        val settingsContent: @Composable () -> Unit = {
            SharedSettingsScreen(
                capabilities = capabilities,
                runtime = runtime,
                platformServices = platformServices,
                providerConfigs = providerConfigs,
                appSettings = sharedAppSettings,
                loadStatistics = {
                    if (historyStore != null) {
                        withContext(Dispatchers.Default) {
                            historyStore.loadUsageStatistics()
                        }
                    } else {
                        val persistedSessions =
                            sessionStates.values.map(SharedSessionUiState::toPersistedSession)
                        withContext(Dispatchers.Default) {
                            com.zhousl.aether.data.buildSharedUsageStatisticsReport(
                                persistedSessions,
                            )
                        }
                    }
                },
                bridgeClient = bridgeClient,
                extensionManager = extensionManager,
                extensionStateStore = extensionStateStore,
                onExtensionSnapshotChanged = { extensionSnapshot = it },
                skillManager = skillManager,
                installedSkills = installedSkills,
                extensionCount = extensionSnapshot.extensions.size,
                mcpManager = mcpManager,
                mcpServers = mcpServers,
                activeMcpServerIds = activeMcpServerIds.toSet(),
                onMcpServersChanged = { servers ->
                    mcpServers.clear()
                    mcpServers.addAll(servers)
                    retainEnabledMcpSelections(
                        servers.filter(SharedMcpServerConfig::enabled)
                            .map(SharedMcpServerConfig::id)
                            .toSet(),
                    )
                },
                chromeManager = chromeManager,
                onSkillsChanged = { skills ->
                    installedSkills.clear()
                    installedSkills.addAll(skills)
                    retainEnabledSkillSelections(
                        skills.filter(SharedInstalledSkill::isEnabled)
                            .map(SharedInstalledSkill::id)
                            .toSet(),
                    )
                },
                onProviderSaved = ::upsertProviderConfig,
                onProviderEnabledChanged = ::setProviderEnabled,
                onProviderRemoved = ::removeProviderConfig,
                onGeneralSettingsSaved = ::persistResolvedAppSettings,
                onAlpineResetSettingsSaved = { updated ->
                    sharedAppSettings = updated
                    settingsStore?.saveGeneralSettings(updated)
                },
                onExportAppData = { pendingSettings ->
                    val manager = checkNotNull(appDataManager) { "App data storage is unavailable." }
                    settingsStore?.saveGeneralSettings(pendingSettings)
                    settingsStore?.saveProviders(providerConfigs.toList(), providerConfig?.id.orEmpty())
                    persistSession()
                    historyStore?.setCurrentSession(sessionId)
                    manager.exportJson()
                },
                onImportAppData = { value ->
                    val manager = checkNotNull(appDataManager) { "App data storage is unavailable." }
                    val restored = manager.restoreJson(value)
                    val persisted = restored.persistedSettings
                    sharedAppSettings = persisted.appSettings
                    providerConfigs.clear()
                    providerConfigs.addAll(persisted.providerConfigs)
                    providerConfig = persisted.activeProviderConfig
                    installedSkills.clear()
                    installedSkills.addAll(restored.installedSkills)
                    mcpServers.clear()
                    mcpServers.addAll(restored.mcpServers)
                    sessionStates.clear()
                    sessions.clear()
                    restored.sessions.forEach { persistedSession ->
                        val state = persistedSession.toSharedSessionUiState()
                        sessionStates[state.id] = state
                        sessions += SharedConversationSummary(state.id, state.title)
                    }
                    val restoredCurrent = if (restored.currentSessionId == SharedDraftSessionId) {
                        SharedSessionUiState(
                            id = SharedDraftSessionId,
                            isDraft = true,
                            selectedModelKey = persisted.appSettings.defaultChatModelKey,
                        )
                    } else {
                        restored.currentSessionId?.let(sessionStates::get)
                            ?: SharedSessionUiState(
                                id = SharedDraftSessionId,
                                isDraft = true,
                                selectedModelKey = persisted.appSettings.defaultChatModelKey,
                            )
                    }
                    currentSession = restoredCurrent
                    sessionId = restoredCurrent.id
                    if (restoredCurrent.isDraft) {
                        historyStore?.setCurrentSession(SharedDraftSessionId)
                    }
                    chromeEnabled = false
                    chromeManager.enabled = false
                    restored
                },
                onBack = {
                    tabletSettingsVisible = false
                    route = SharedRoute.Chat
                },
                onReplayOnboarding = {
                    tabletSettingsVisible = false
                    onboardingReplayMode = true
                    onboardingEntryStage = OnboardingStage.Landing
                    route = SharedRoute.Onboarding
                },
                onReplayFollowUpOnboarding = {
                    tabletSettingsVisible = false
                    onboardingReplayMode = true
                    onboardingEntryStage = OnboardingStage.Search
                    route = SharedRoute.Onboarding
                },
                onReplayAlpineSetupPreview = {
                    alpineSetupPreviewVisible = true
                },
                onExportLogs = {
                    SharedDiagnosticLogger.event(
                        category = "export",
                        event = "diagnostic_export_start",
                        details = mapOf(
                            "screen" to if (tabletSettingsVisible) "Settings" else route.name,
                            "session_count" to sessionStates.size,
                        ),
                    )
                    buildSharedDiagnosticLogText(
                        appVersion = platformAppVersion(),
                        route = if (tabletSettingsVisible) SharedRoute.Settings else route,
                        currentSession = currentSession,
                        sessionStates = sessionStates.values,
                        providerConfigs = providerConfigs,
                        installedSkillCount = installedSkills.size,
                        mcpServers = mcpServers,
                        settings = sharedAppSettings,
                    )
                },
                onTransientMessage = { transientMessage = it },
                dismissRequestToken = tabletSettingsDismissRequest,
            )
        }
        val activeExtensionPage = extensionSnapshot.pages.firstOrNull { it.id == activeExtensionPageId }
        if (activeExtensionPage != null) {
            SharedAetherExtensionPageScreen(
                page = activeExtensionPage,
                onBack = { activeExtensionPageId = "" },
            )
        } else AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (reduceMotion) {
                    return@AnimatedContent fadeIn(tween(80)) togetherWith fadeOut(tween(60))
                }
                if (!capabilities.layeredScreenTransitions) {
                    return@AnimatedContent fadeIn(tween(120)) togetherWith
                        fadeOut(tween(80))
                }
                val isForward = targetState.depth() > initialState.depth()
                val enterSlide = slideInHorizontally(
                    animationSpec = tween(
                        SharedScreenTransitionDuration,
                        easing = SharedScreenTransitionEasing,
                    ),
                    initialOffsetX = { if (isForward) it / 3 else -it / 3 },
                ) + fadeIn(
                    tween(SharedScreenTransitionDuration, easing = SharedScreenTransitionEasing),
                )
                val exitSlide = slideOutHorizontally(
                    animationSpec = tween(
                        SharedScreenTransitionDuration,
                        easing = SharedScreenTransitionEasing,
                    ),
                    targetOffsetX = { if (isForward) -it / 3 else it / 3 },
                ) + fadeOut(
                    tween(SharedScreenTransitionDuration, easing = SharedScreenTransitionEasing),
                )
                enterSlide togetherWith exitSlide
            },
            label = "app_screen_transition",
        ) { current ->
            when (current) {
                SharedRoute.Onboarding -> SharedOnboarding(
                    runtime = runtime,
                    bridgeClient = bridgeClient,
                    existingProviderConfig = providerConfig,
                    replayMode = onboardingReplayMode,
                    onTransientMessage = { transientMessage = it },
                    onSkip = {
                        sharedAppSettings = sharedAppSettings.copy(
                            onboardingSeenVersion = CurrentOnboardingVersion,
                        )
                        appScope.launch { settingsStore?.markOnboardingSeen() }
                        onboardingReplayMode = false
                        onboardingEntryStage = OnboardingStage.Landing
                        route = SharedRoute.Chat
                    },
                    onClose = {
                        val returnRoute = if (onboardingReplayMode) {
                            SharedRoute.Settings
                        } else {
                            SharedRoute.Chat
                        }
                        onboardingReplayMode = false
                        onboardingEntryStage = OnboardingStage.Landing
                        route = returnRoute
                    },
                    onComplete = { configured ->
                        val enabledConfig = configured.copy(isEnabled = true)
                        val updated = providerConfigs.filterNot { it.id == enabledConfig.id } + enabledConfig
                        commitProviderConfigs(updated, enabledConfig.id)
                        sharedAppSettings = sharedAppSettings
                            .withSharedExplicitDefaultChatModel(enabledConfig)
                            .copy(onboardingSeenVersion = CurrentOnboardingVersion)
                        appScope.launch {
                            settingsStore?.saveGeneralSettings(sharedAppSettings)
                            settingsStore?.markOnboardingSeen()
                        }
                        onboardingReplayMode = false
                        onboardingEntryStage = OnboardingStage.Landing
                        val draft = createNewSession()
                        draft.input = OnboardingStarterPrompt
                        draft.selectedSkillIds.clear()
                        draft.selectedModelKey = resolveSharedConversationModelKey(
                            selectedModelKey = draft.selectedModelKey,
                            defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                            options = providerConfigs.availableModelOptions(),
                        )
                        showStarterPromptHint = true
                        awaitingFollowUpTour = true
                        route = SharedRoute.Chat
                    },
                    initialStage = onboardingEntryStage,
                    initialSearchValue = sharedAppSettings.tavilyApiKey,
                    onSearchDone = { apiKey ->
                        if (apiKey.isNotBlank()) {
                            sharedAppSettings = sharedAppSettings.copy(tavilyApiKey = apiKey)
                            appScope.launch { settingsStore?.saveGeneralSettings(sharedAppSettings) }
                        }
                        val returnRoute = if (onboardingReplayMode) {
                            SharedRoute.Settings
                        } else {
                            SharedRoute.Chat
                        }
                        onboardingReplayMode = false
                        onboardingEntryStage = OnboardingStage.Landing
                        route = returnRoute
                    },
                )
                SharedRoute.Chat -> Box(Modifier.fillMaxSize()) {
                    SharedChatScreen(
                    sessions = sessions.map { summary ->
                        val state = sessionStates[summary.id]
                        summary.copy(
                            title = state?.title ?: summary.title,
                            indicator = when {
                                state?.isWorking == true -> SharedConversationIndicator.Working
                                state?.hasUnviewedCompletion == true -> SharedConversationIndicator.UnviewedComplete
                                else -> SharedConversationIndicator.None
                            },
                        )
                    },
                    selectedSessionId = sessionId,
                    composerSessionKey = currentSession.composerKey,
                    messages = messages,
                    pendingTurns = queuedTurns,
                    runtime = runtime,
                    platformServices = platformServices,
                    availableSkills = installedSkills.filter { it.isEnabled },
                    selectedSkillIds = selectedSkillIds,
                    onSkillSelected = { skillId, selected ->
                        if (selected) {
                            if (skillId !in selectedSkillIds) selectedSkillIds += skillId
                        } else {
                            selectedSkillIds.remove(skillId)
                            currentSession.activeSkills.removeAll {
                                it.skillId !in currentSession.selectedSkillIds
                            }
                        }
                        appScope.launch { persistSession() }
                    },
                    mcpServers = mcpServers.filter { it.enabled },
                    activeMcpServerIds = activeMcpServerIds,
                    onMcpServerSelected = { serverId, selected ->
                        if (selected) {
                            if (serverId !in activeMcpServerIds) activeMcpServerIds += serverId
                        } else {
                            activeMcpServerIds.remove(serverId)
                        }
                        appScope.launch {
                            runSharedAppCatching {
                                mcpManager.refreshBindings(
                                    mcpServers.filter { it.enabled && it.id in activeMcpServerIds },
                                    sessionId = currentSession.id,
                                )
                            }.onFailure(::reportMcpRefreshFailure)
                            persistSession()
                        }
                    },
                    chromeAvailable = capabilities.alpineChrome,
                    chromeEnabled = chromeEnabled,
                    onChromeSelected = { selected ->
                        chromeEnabled = selected && capabilities.alpineChrome
                        chromeManager.enabled = chromeEnabled
                        appScope.launch { persistSession() }
                    },
                    composerState = currentSession,
                    isSending = currentSession.isWorking,
                    streamingStatus = currentSession.streamingStatus,
                    selectedModelKey = resolveSharedConversationModelKey(
                        selectedModelKey = currentSession.selectedModelKey,
                        defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                        options = modelOptions,
                    ),
                    modelOptions = modelOptions,
                    modelCatalogInfo = modelCatalogInfo,
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel,
                    thinkingLevelClampsByProviderModel = thinkingLevelClampsByProviderModel,
                    reasoningEffort = sharedAppSettings.reasoningEffort,
                    onTransientMessage = { transientMessage = it },
                    onModelMenuOpened = {},
                    onModelSelected = { key, onResolved ->
                        currentSession.selectedModelKey = key
                        val selectedSession = currentSession
                        if (!selectedSession.isDraft) {
                            appScope.launch {
                                historyStore?.updateSelectedModelKey(selectedSession.id, key)
                            }
                        }
                        val option = modelOptions.findModelOption(key)
                        if (option == null) {
                            onResolved(false)
                        } else {
                            val thinkingKey = sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                            val cachedLevels = thinkingLevelsByProviderModel[thinkingKey]
                            if (cachedLevels != null) {
                                onResolved(cachedLevels.isNotEmpty())
                            } else {
                                onResolved(false)
                                appScope.launch { refreshThinkingCatalog(listOf(option)) }
                            }
                        }
                    },
                    onReasoningSelected = { effort ->
                        sharedAppSettings = sharedAppSettings.copy(reasoningEffort = effort)
                        appScope.launch { settingsStore?.saveGeneralSettings(sharedAppSettings) }
                    },
                    editingMessageId = currentSession.editingMessageId,
                    showStarterPromptHint = showStarterPromptHint,
                    onDismissStarterPromptHint = { showStarterPromptHint = false },
                    onCancelEdit = {
                        currentSession.editingMessageId = ""
                        currentSession.input = ""
                    },
                    onInputChanged = {
                        if (it != currentSession.input) showStarterPromptHint = false
                        currentSession.input = it
                        scheduleExtensionDraftRefresh()
                    },
                    onSend = { attachments ->
                        showStarterPromptHint = false
                        startChatTurn(currentSession.input, attachments)
                    },
                    onRetry = { messageId ->
                        if (currentSession.isWorking) return@SharedChatScreen
                        val plan = buildSharedAssistantRetryPlan(messages, messageId)
                            ?: return@SharedChatScreen
                        currentSession.editingMessageId = ""
                        currentSession.input = ""
                        messages.clear()
                        messages.addAll(plan.retainedMessages)
                        startChatTurn(
                            rawValue = plan.userMessage.text,
                            attachments = plan.userMessage.attachments,
                            retryResponseGroupId = plan.userMessage.id,
                        )
                    },
                    onRetryUserMessage = { messageId ->
                        if (currentSession.isWorking) return@SharedChatScreen
                        val original = messages.firstOrNull { it.id == messageId && it.fromUser }
                            ?: return@SharedChatScreen
                        val replacement = original.copy(
                            id = platformRandomUuid(),
                            createdAtMillis = platformCurrentTimeMillis(),
                            userBranches = emptyList(),
                            selectedUserBranchIndex = 0,
                            branchIndex = 0,
                            branchCount = 1,
                        )
                        val updated = createEditedSharedMessageBranch(messages, messageId, replacement)
                            ?: return@SharedChatScreen
                        currentSession.editingMessageId = ""
                        currentSession.input = ""
                        messages.clear()
                        messages.addAll(updated)
                        startChatTurn(
                            rawValue = replacement.text,
                            attachments = replacement.attachments,
                            retryResponseGroupId = replacement.id,
                        )
                    },
                    onQueueFollowUp = { attachments ->
                        startChatTurn(currentSession.input, attachments)
                    },
                    onSteerFollowUp = { attachments ->
                        val target = currentSession
                        val value = target.input.trim()
                        if (value.isNotBlank() || attachments.isNotEmpty()) {
                            if (target.isWorking) {
                                target.input = ""
                                target.queuedTurns += SharedPendingTurn(
                                    text = value,
                                    attachments = attachments,
                                    mode = SharedPendingTurnMode.Steer,
                                )
                            } else {
                                startChatTurn(value, attachments, target = target)
                            }
                        }
                    },
                    onEditUserMessage = { messageId ->
                        if (currentSession.isWorking) {
                            transientMessage = pauseBeforeEditingMessage
                            return@SharedChatScreen
                        }
                        val index = messages.indexOfFirst { it.id == messageId && it.fromUser }
                        if (index >= 0) {
                            currentSession.input = messages[index].text
                            currentSession.editingMessageId = messageId
                        }
                    },
                    onSelectUserBranch = { messageId, branchIndex ->
                        if (currentSession.isWorking) return@SharedChatScreen
                        switchSharedUserMessageBranch(messages, messageId, branchIndex)?.let { updated ->
                            messages.clear()
                            messages.addAll(updated)
                            appScope.launch { persistSession() }
                        }
                    },
                    onDeleteMessage = { messageId ->
                        if (!currentSession.isWorking) {
                            val targetIndex = messages.indexOfFirst { it.id == messageId }
                            if (targetIndex >= 0) {
                            val targetMessage = messages[targetIndex]
                                val trimIndex = if (
                                    !targetMessage.fromUser && targetMessage.responseGroupId.isNotBlank()
                                ) {
                                    messages.indexOfFirst {
                                        !it.fromUser && it.responseGroupId == targetMessage.responseGroupId
                                    }.takeIf { it >= 0 } ?: targetIndex
                                } else {
                                    targetIndex
                                }
                                val targetSession = currentSession
                                val removedMessageIds = messages.drop(trimIndex).map { it.id }
                                while (messages.size > trimIndex) messages.removeAt(messages.lastIndex)
                                targetSession.editingMessageId = ""
                                targetSession.input = ""
                                if (messages.isEmpty()) {
                                    sessionStates.remove(targetSession.id)
                                    sessions.removeAll { it.id == targetSession.id }
                                    createNewSession(useDefaultSkills = false)
                                }
                                appScope.launch {
                                    if (messages.isEmpty()) {
                                        val unreferencedPaths = historyStore
                                            ?.getUnreferencedWorkspaceFilePathsForDeletedSession(targetSession.id)
                                            .orEmpty()
                                        historyStore?.delete(targetSession.id)
                                        removeSharedUnreferencedWorkspaceFiles(runtime, unreferencedPaths)
                                    } else {
                                        val unreferencedPaths = historyStore
                                            ?.getUnreferencedWorkspaceFilePathsForDeletedMessages(
                                                sessionId = targetSession.id,
                                                messageIds = removedMessageIds,
                                            ).orEmpty()
                                        persistSession(targetSession)
                                        removeSharedUnreferencedWorkspaceFiles(runtime, unreferencedPaths)
                                    }
                                }
                            }
                        }
                    },
                    onStop = {
                        currentSession.job?.cancel()
                        currentSession.job = null
                        currentSession.streamingStatus = ""
                        currentSession.queuedTurns.clear()
                        messages.lastOrNull { !it.fromUser && it.isStreaming }?.let { pending ->
                            val finalized = pending.finalizeSharedInterruptedAssistantWork(
                                status = chatStoppedStatus,
                                completedAtMillis = platformCurrentTimeMillis(),
                            )
                            if (finalized.hasSharedVisibleAssistantWork()) {
                                messages.updateMessage(pending.id) { finalized }
                            } else {
                                messages.removeAll { it.id == pending.id }
                            }
                        }
                        endBackgroundExecution(currentSession)
                        appScope.launch {
                            persistSession()
                        }
                    },
                    onNewChat = {
                        showStarterPromptHint = false
                        createNewSession()
                    },
                    onSessionSelected = { selectedId ->
                        if (selectedId != sessionId) {
                            showStarterPromptHint = false
                            sessionStates[selectedId]?.let(::showSession)
                        }
                    },
                    onRenameSession = { selectedId, title ->
                        val normalizedTitle = title.trim().take(80)
                        if (normalizedTitle.isBlank()) return@SharedChatScreen
                        sessionStates[selectedId]?.let {
                            it.title = normalizedTitle
                            it.hasCustomTitle = true
                        }
                        val index = sessions.indexOfFirst { it.id == selectedId }
                        if (index >= 0) {
                            sessions[index] = sessions[index].copy(title = normalizedTitle)
                        }
                        appScope.launch { historyStore?.rename(selectedId, normalizedTitle) }
                    },
                    onDeleteSession = { selectedId ->
                        val selectedState = sessionStates[selectedId]
                        if (selectedState?.isWorking == true) {
                            transientMessage = pauseBeforeDeletingSessionMessage
                            return@SharedChatScreen
                        }
                        pendingDeleteSessionId = selectedId
                    },
                    onExportSession = ::exportSession,
                    extensionPages = extensionSnapshot.pages,
                    onExtensionPageSelected = { activeExtensionPageId = it },
                    onOpenSettings = {
                        if (useTabletLayout) tabletSettingsVisible = true
                        else route = SharedRoute.Settings
                    },
                    onDrawerOpened = {
                        appScope.launch {
                            runSharedAppCatching {
                                extensionManager.dispatchEvent(
                                    event = "drawer.opened",
                                    context = extensionContext(),
                                )
                            }
                        }
                    },
                    drawerOpenedEventRegistered = "drawer.opened" in extensionSnapshot.eventNames,
                    useTabletLayout = useTabletLayout,
                )
                    if (useTabletLayout) {
                        SharedTabletSettingsOverlay(
                            visible = tabletSettingsVisible,
                            onDismiss = { tabletSettingsDismissRequest += 1 },
                        ) {
                            settingsContent()
                        }
                    }
                }
                SharedRoute.Settings -> settingsContent()
            }
        }
        SharedAetherExtensionOverlay(Modifier.fillMaxSize())
        if (transientMessage.isNotBlank()) {
            Popup(
                alignment = Alignment.BottomCenter,
                properties = PopupProperties(focusable = false),
            ) {
                Text(
                    text = transientMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 32.dp, vertical = 48.dp)
                        .clip(RoundedCornerShape(24.dp)).background(Color(0xE6323232))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        pendingDeleteSessionId?.let { selectedId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteSessionId = null },
                containerColor = AetherSurface,
                titleContentColor = AetherOnSurface,
                textContentColor = AetherOnSurfaceVariant,
                title = { Text(stringResource(Res.string.chat_delete_session_title)) },
                text = { Text(stringResource(Res.string.chat_delete_session_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteSessionId = null
                            sessionStates.remove(selectedId)
                            sessions.removeAll { it.id == selectedId }
                            if (sessionId == selectedId) {
                                createNewSession(useDefaultSkills = false)
                            }
                            appScope.launch {
                                val unreferencedPaths = historyStore
                                    ?.getUnreferencedWorkspaceFilePathsForDeletedSession(selectedId)
                                    .orEmpty()
                                historyStore?.delete(selectedId)
                                removeSharedUnreferencedWorkspaceFiles(runtime, unreferencedPaths)
                            }
                        },
                    ) {
                        Text(
                            stringResource(Res.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteSessionId = null }) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                },
            )
        }
        if (!startupResolved) {
            Box(Modifier.fillMaxSize().background(AetherBackground))
        } else if (!sharedAppSettings.privacyPolicyAccepted) {
            SharedPrivacyPolicyConsentDialog(
                onOpenPolicy = {
                    if (!platformServices.openUrl(AetherPrivacyPolicyUrl)) {
                        transientMessage = unableToOpenLinkMessage
                    }
                },
                onAccept = {
                    appScope.launch {
                        settingsStore?.acceptPrivacyPolicy()
                        sharedAppSettings = sharedAppSettings.copy(privacyPolicyAccepted = true)
                    }
                },
                onDecline = { platformServices.terminateApplication() },
            )
        } else if (alpineSetupPreviewVisible) {
            RuntimeSetupStep(
                runtime = runtime,
                bridgeClient = bridgeClient,
                replayMode = false,
                onBack = { alpineSetupPreviewVisible = false },
                onClose = { alpineSetupPreviewVisible = false },
                onContinue = { alpineSetupPreviewVisible = false },
            )
        }
        }
        }
    }
}

internal fun shouldUseSharedTabletLayout(
    supportsTabletLayout: Boolean,
    availableWidthDp: Float,
): Boolean = supportsTabletLayout && availableWidthDp >= SharedTabletLayoutMinWidthDp

internal fun isSharedDrawerClosing(
    currentOpen: Boolean,
    targetOpen: Boolean,
): Boolean = currentOpen && !targetOpen

/** Defers opens until registration and emits the tablet event once per layout epoch. */
internal class SharedDrawerOpenedEventGate {
    private var tabletLayoutActive = false
    private var tabletEventDispatched = false
    private var pendingMobileOpenEvent = false

    fun onMobileDrawerOpened(eventRegistered: Boolean): Boolean {
        if (eventRegistered) {
            pendingMobileOpenEvent = false
            return true
        }
        pendingMobileOpenEvent = true
        return false
    }

    fun onMobileDrawerClosed() {
        pendingMobileOpenEvent = false
    }

    fun onLayoutRegistrationOrDrawerSnapshotChanged(
        useTabletLayout: Boolean,
        currentOpen: Boolean,
        targetOpen: Boolean,
        eventRegistered: Boolean,
    ): Boolean {
        if (!useTabletLayout) {
            if (!currentOpen) onMobileDrawerClosed()
            // Keep a pending event through the animation so a canceled close can still deliver it.
            if (isSharedDrawerClosing(currentOpen, targetOpen)) return false
        }
        return onLayoutOrRegistrationChanged(
            useTabletLayout = useTabletLayout,
            eventRegistered = eventRegistered,
        )
    }

    fun onLayoutOrRegistrationChanged(
        useTabletLayout: Boolean,
        eventRegistered: Boolean,
    ): Boolean {
        if (useTabletLayout != tabletLayoutActive) {
            tabletLayoutActive = useTabletLayout
            tabletEventDispatched = false
            pendingMobileOpenEvent = false
        }
        if (!eventRegistered) return false

        if (useTabletLayout && !tabletEventDispatched) {
            tabletEventDispatched = true
            return true
        }
        if (!useTabletLayout && pendingMobileOpenEvent) {
            pendingMobileOpenEvent = false
            return true
        }
        return false
    }
}

@Composable
private fun SharedTabletSettingsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val panelEnterTransition = if (reduceMotion) {
        fadeIn(tween(80))
    } else {
        fadeIn(tween(300, easing = SharedConversationMotionEasing)) +
            slideInVertically(
                animationSpec = tween(460, easing = SharedConversationMotionEasing),
                initialOffsetY = { it },
            )
    }
    val panelExitTransition = if (reduceMotion) {
        fadeOut(tween(60))
    } else {
        fadeOut(tween(420, easing = SharedConversationMotionEasing)) +
            slideOutVertically(
                animationSpec = tween(650, easing = SharedConversationMotionEasing),
                targetOffsetY = { it },
            )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(if (reduceMotion) 80 else 280, easing = SharedConversationMotionEasing)),
            exit = fadeOut(tween(if (reduceMotion) 60 else 500, easing = SharedConversationMotionEasing)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(AetherScrim.copy(alpha = 0.38f))
                    .pointerInput(visible, onDismiss) {
                        if (visible) detectTapGestures { onDismiss() }
                    },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = panelEnterTransition,
            exit = panelExitTransition,
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 56.dp, vertical = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 720.dp).heightIn(max = 860.dp).fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures {} }
                        .shadow(
                            18.dp,
                            RoundedCornerShape(24.dp),
                            ambientColor = AetherScrim,
                            spotColor = AetherScrim,
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = AetherBackground,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SharedPrivacyPolicyConsentDialog(
    onOpenPolicy: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = AetherSurface,
        titleContentColor = AetherOnSurface,
        textContentColor = AetherOnSurfaceVariant,
        title = {
            Text(
                text = stringResource(Res.string.app_privacy_policy_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            val policyText = stringResource(Res.string.app_privacy_policy_title)
            val messagePrefix = stringResource(Res.string.app_privacy_policy_message_prefix)
            val messageSuffix = stringResource(Res.string.app_privacy_policy_message_suffix)
            val annotatedText = buildAnnotatedString {
                append(messagePrefix)
                pushStringAnnotation(SharedPrivacyPolicyAnnotationTag, AetherPrivacyPolicyUrl)
                withStyle(SpanStyle(color = AetherPrimary)) { append(policyText) }
                pop()
                append(messageSuffix)
            }
            @Suppress("DEPRECATION")
            ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(color = AetherOnSurfaceVariant),
                onClick = { offset ->
                    annotatedText
                        .getStringAnnotations(SharedPrivacyPolicyAnnotationTag, offset, offset)
                        .firstOrNull()
                        ?.let { onOpenPolicy() }
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AetherPrimary,
                    contentColor = Color.White,
                ),
            ) { Text(stringResource(Res.string.common_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(Res.string.common_decline), color = AetherOnSurfaceVariant)
            }
        },
    )
}

private suspend fun buildSharedDiagnosticLogText(
    appVersion: String,
    route: SharedRoute,
    currentSession: SharedSessionUiState,
    sessionStates: Collection<SharedSessionUiState>,
    providerConfigs: List<LlmProviderConfig>,
    installedSkillCount: Int,
    mcpServers: List<SharedMcpServerConfig>,
    settings: AppSettings,
): String {
    val diagnosticEvents = SharedDiagnosticLogger.readEventsText()
    val lastCrash = SharedDiagnosticLogger.readLastCrashText()
    return buildString {
        appendLine("Aether diagnostic log")
        appendLine("generatedAtMillis=${platformCurrentTimeMillis()}")
        appendLine("versionName=$appVersion")
        appendLine("screen=${route.name}")
        appendLine("currentSessionId=${currentSession.id}")
        appendLine("sessionCount=${sessionStates.size}")
        appendLine("runningSessionCount=${sessionStates.count { it.isWorking }}")
        appendLine("piProviderId=${settings.piProviderId}")
        appendLine("providerConfigCount=${providerConfigs.size}")
        appendLine("skillCount=$installedSkillCount")
        appendLine("mcpServerCount=${mcpServers.size}")
        appendLine()
        appendLine("settingsSummary:")
        appendLine(buildSharedSettingsDiagnosticSummary(settings).toSharedDiagnosticText())
        appendLine()
        appendLine("providerConfigsSummary:")
        appendLine(buildSharedProviderConfigsDiagnosticSummary(providerConfigs).toSharedDiagnosticText())
        appendLine()
        appendLine("mcpServersSummary:")
        appendLine(buildSharedMcpServersDiagnosticSummary(mcpServers).toSharedDiagnosticText())
        appendLine()
        appendLine("sessionsSummary:")
        appendLine(
            buildSharedSessionsDiagnosticSummary(currentSession.id, sessionStates)
                .toSharedDiagnosticText(),
        )
        appendLine()
        appendLine("lastCrash:")
        appendLine(lastCrash.ifBlank { "No crash breadcrumb recorded." })
        appendLine()
        appendLine("diagnosticEventsJsonl:")
        append(diagnosticEvents.ifBlank { "No diagnostic events recorded." })
    }
}

private fun buildSharedSettingsDiagnosticSummary(settings: AppSettings): JsonObject = buildJsonObject {
    put("piProviderId", settings.piProviderId)
    put("modelId", settings.modelId)
    put("baseUrl", SharedDiagnosticRedactor.sanitizedBaseUrl(settings.baseUrl))
    put("defaultChatModelKey", settings.defaultChatModelKey)
    put("defaultTitleModelKey", settings.defaultTitleModelKey)
    put("defaultNamingModelKey", settings.defaultNamingModelKey)
    put("llmInactivityReconnectTimeoutSeconds", settings.llmInactivityReconnectTimeoutSeconds)
    put("privacyPolicyAccepted", settings.privacyPolicyAccepted)
}

private fun buildSharedProviderConfigsDiagnosticSummary(
    providerConfigs: List<LlmProviderConfig>,
): JsonArray = buildJsonArray {
    providerConfigs.forEach { config ->
        add(buildJsonObject {
            put("id", config.id)
            put("name", config.name)
            put("piProviderId", config.piProviderId)
            put("baseUrl", SharedDiagnosticRedactor.sanitizedBaseUrl(config.baseUrl))
            put("modelId", config.modelId)
            put("cachedModelCount", config.cachedModels.size)
            put("enabledModelCount", config.enabledModelIds.size)
            put("isEnabled", config.isEnabled)
        })
    }
}

private fun buildSharedMcpServersDiagnosticSummary(
    mcpServers: List<SharedMcpServerConfig>,
): JsonArray = buildJsonArray {
    mcpServers.forEach { server ->
        add(buildJsonObject {
            put("id", server.id)
            put("displayName", server.name)
            put("isEnabled", server.enabled)
            put(
                "transportType",
                if (server.transport == SharedMcpTransport.Stdio) "stdio" else "streamable_http",
            )
            put("connectTimeoutMillis", server.connectTimeoutMillis)
            put("requestTimeoutMillis", server.requestTimeoutMillis)
            when (server.transport) {
                SharedMcpTransport.Stdio -> {
                    put("commandSummary", server.command.lineSequence().firstOrNull().orEmpty().take(160))
                    put("argumentCount", server.arguments.size)
                    put("workingDirectory", server.workingDirectory)
                    put("environmentKeyCount", server.environment.size)
                }
                SharedMcpTransport.Http -> {
                    put("url", SharedDiagnosticRedactor.sanitizedBaseUrl(server.url))
                    put("headerKeyCount", server.headers.size)
                }
            }
        })
    }
}

private fun buildSharedSessionsDiagnosticSummary(
    currentSessionId: String,
    sessionStates: Collection<SharedSessionUiState>,
): JsonObject = buildJsonObject {
    put("currentSessionId", currentSessionId)
    put("sessionCount", sessionStates.size)
    put("runningSessions", buildJsonArray {
        sessionStates.filter(SharedSessionUiState::isWorking).forEach { session ->
            val activeAssistant = session.messages.lastOrNull { it.isStreaming }
            add(buildJsonObject {
                put("sessionId", session.id)
                put("pendingToolCount", activeAssistant?.sharedRunningToolCount() ?: 0)
                put("pendingInputCount", session.queuedTurns.size)
                activeAssistant?.createdAtMillis?.let { put("activeTurnStartedAtMillis", it) }
                    ?: put("activeTurnStartedAtMillis", JsonNull)
                put("pendingStatusText", session.streamingStatus.ifBlank { activeAssistant?.status.orEmpty() })
                put("pendingStatusDetail", activeAssistant?.statusDetail.orEmpty())
            })
        }
    })
    put("recentSessions", buildJsonArray {
        sessionStates.sortedByDescending { session ->
            session.messages.maxOfOrNull(SharedChatMessage::createdAtMillis) ?: 0L
        }.take(12).forEach { session ->
            add(buildJsonObject {
                put("id", session.id)
                put("title", session.title)
                put("messageCount", session.messages.size)
                put(
                    "lastMessageAtMillis",
                    session.messages.maxOfOrNull(SharedChatMessage::createdAtMillis) ?: 0L,
                )
                put("selectedModelKey", session.selectedModelKey)
                put("selectedSkillCount", session.selectedSkillIds.size)
                put("activeMcpServerCount", session.activeMcpServerIds.size)
            })
        }
    })
}

private fun SharedChatMessage.sharedRunningToolCount(): Int = buildList {
    addAll(tools)
    responseBlocks.forEach { block ->
        when (block) {
            is SharedAssistantResponseBlock.ToolGroup -> addAll(block.tools)
            is SharedAssistantResponseBlock.Reasoning -> addAll(block.trace.toolInvocations)
            is SharedAssistantResponseBlock.Text -> Unit
        }
    }
}.distinctBy(SharedChatToolInvocation::id).count(SharedChatToolInvocation::isRunning)

private fun JsonObject.toSharedDiagnosticText(): String = SharedDiagnosticPrettyJson.encodeToString(this)

private fun JsonArray.toSharedDiagnosticText(): String = SharedDiagnosticPrettyJson.encodeToString(this)

internal fun shouldRestoreSharedChat(onboardingSeenVersion: Int): Boolean =
    onboardingSeenVersion >= CurrentOnboardingVersion

private fun AppSettings.withSharedExplicitDefaultChatModel(
    providerConfig: LlmProviderConfig,
): AppSettings {
    val selectedModelId = providerConfig.modelId.trim()
    if (selectedModelId.isBlank()) return this
    val selectableConfig = providerConfig.copy(
        isEnabled = true,
        cachedModels = providerConfig.cachedModels + selectedModelId,
        enabledModelIds = providerConfig.enabledModelIds + selectedModelId,
    )
    val selectedOption = listOf(selectableConfig)
        .availableModelOptions()
        .firstOrNull { it.modelId == selectedModelId }
        ?: return this
    return withModelOption(selectedOption).copy(defaultChatModelKey = selectedOption.key)
}

private fun buildSharedTitleGenerationInput(message: SharedChatMessage): String = buildString {
    message.text.trim().takeIf(String::isNotBlank)?.let { text ->
        appendLine("First user message:")
        appendLine(text)
    }
    if (message.attachments.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("Attachments:")
        message.attachments.forEach { attachment -> appendLine("- ${attachment.name}") }
    }
}.trim()

internal fun resolveSharedStoredOrAutomaticModelKey(
    storedKey: String,
    options: List<ProviderModelOption>,
    purpose: AutomaticModelPurpose,
    fallbackPurpose: AutomaticModelPurpose? = null,
): String = storedKey.takeIf { key -> options.any { it.key == key } }.orEmpty()
    .ifBlank { options.resolveAutomaticModelKey(purpose) }
    .ifBlank { fallbackPurpose?.let(options::resolveAutomaticModelKey).orEmpty() }

private fun String.sanitizeSharedSessionTitle(): String = lineSequence()
    .map { line ->
        line.trim()
            .removePrefix("Title:")
            .removePrefix("title:")
            .trim()
            .trim('"', '\'', '`')
    }
    .firstOrNull(String::isNotBlank)
    .orEmpty()
    .trimEnd('.', '!', '?')
    .take(36)

private fun buildSharedReasoningSummaryPrompt(rawText: String): String = buildString {
    appendLine("Summarize this assistant reasoning excerpt for a user-visible thinking timeline.")
    appendLine("Return exactly two short paragraphs: first a concise title, then one detail paragraph.")
    appendLine("Title style: a short gerund or noun phrase about the purpose or outcome, without 'I', 'The assistant', or a tool-action headline.")
    appendLine("Detail style: natural first-person planning language. 'I need to...', 'I should...', 'I will...', and 'I am...' are all acceptable when they fit.")
    appendLine("Never write from a third-person assistant perspective such as 'The assistant is...' or 'The model is...'.")
    appendLine("Do not mention that this is a summary, do not add bullets, and do not invent context.")
    appendLine()
    appendLine("Use this style:")
    appendLine("Providing accurate and properly cited documentation")
    appendLine()
    appendLine("I need to make sure I include citations for all factual information, especially from official docs, since I haven't performed any live API tests. It's essential to clarify that my info is based on public documentation and mention the safety of returning raw reasoning in OpenRouter. I should avoid long CoT examples.")
    appendLine()
    appendLine("Reasoning excerpt:")
    append(rawText.take(SharedReasoningSummaryMaxInputChars))
}

private fun parseSharedReasoningSummary(text: String): SharedReasoningSummary? {
    val lines = text.lines().map(String::trim).filter(String::isNotBlank)
    if (lines.isEmpty()) return null
    val title = lines.first().trim('"').take(SharedReasoningSummaryTitleMaxChars)
    val detail = lines.drop(1).joinToString(" ").trim().ifBlank { title }
        .take(SharedReasoningSummaryDetailMaxChars)
    return SharedReasoningSummary(title, detail)
}

private fun fallbackSharedReasoningSummary(rawText: String): SharedReasoningSummary {
    val compact = rawText.lineSequence().map(String::trim).filter(String::isNotBlank)
        .joinToString(" ").replace(Regex("\\s+"), " ")
    return SharedReasoningSummary(
        title = "Thinking through the next step",
        detail = compact.take(SharedReasoningSummaryDetailMaxChars).ifBlank { "Preparing the next action." },
    )
}

internal fun buildSharedCompactConversationInput(messages: List<SharedChatMessage>): String {
    val compactable = messages.syncSharedUserBranches()
        .filter { it.displayKind != SharedMessageDisplayKind.CompactStatus }
    return buildString {
        appendLine("Conversation to compact:")
        compactable.forEachIndexed { index, message ->
            appendLine()
            appendLine("## ${index + 1}. ${if (message.fromUser) "User" else "Agent"}")
            appendLine(message.toSharedCompactionText())
        }
    }.trim().takeLast(SharedCompactingMaxInputChars)
}

internal fun SharedChatMessage.toSharedCompactionText(): String = buildString {
    text.trim().takeIf(String::isNotBlank)?.let(::appendLine)
    val reasoningSummary = responseBlocks.asSequence()
        .filterIsInstance<SharedAssistantResponseBlock.Reasoning>()
        .flatMap { block -> block.trace.chunks.asSequence() }
        .mapNotNull { chunk -> chunk.detail.ifBlank { chunk.title }.takeIf(String::isNotBlank) }
        .joinToString("\n")
    reasoningSummary.takeIf(String::isNotBlank)?.let { reasoning ->
        if (isNotEmpty()) appendLine()
        appendLine("Reasoning summary:")
        appendLine(reasoning)
    }
    if (attachments.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("Attachments:")
        attachments.forEach { attachment ->
            appendLine("- ${attachment.name} (${attachment.mimeType}) ${attachment.workspacePath}".trimEnd())
        }
    }
    if (tools.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("Tool activity:")
        tools.forEach { tool ->
            appendLine("- ${tool.name}: ${tool.argumentsJson.take(600)}")
            if (tool.outputJson.isNotBlank()) appendLine("  output: ${tool.outputJson.take(1_200)}")
        }
    }
}.trim().ifBlank { "[Empty message]" }

private fun buildSharedCompactedContextMessage(summary: String): String =
    "This conversation was compacted. Continue from this retained context:\n\n$summary"

private fun String.sanitizeSharedExportFileName(): String = trim()
    .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
    .trim('.', ' ', '-')
    .take(80)
    .ifBlank { "aether-session" }

private val Base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal fun SharedChatMessage.toPiChatMessage(
    supportsInlineImageWithTools: Boolean = true,
): SharedPiChatMessage {
    val contentParts = buildList {
        if (text.isNotBlank()) add(SharedPiContentPart.Text(text))
        attachments.forEach { attachment ->
            if (attachment.workspacePath.isBlank()) {
                if (
                    attachment.mimeType.startsWith("image/") &&
                    attachment.inlineBase64.isNotBlank()
                ) {
                    add(
                        SharedPiContentPart.Text(
                            "Visual attachment:\n" +
                                "Name: ${attachment.name}\n" +
                                "Type: ${attachment.mimeType}\n" +
                                "This image is attached directly to the model request and has no workspace copy."
                        )
                    )
                    add(SharedPiContentPart.Image(attachment.mimeType, attachment.inlineBase64))
                } else {
                    add(
                        SharedPiContentPart.Text(
                            "Attached file '${attachment.name}' is missing a workspace path. " +
                                "Ask the user to re-upload it if you need to inspect the file."
                        )
                    )
                }
                return@forEach
            }

            val isWorkspaceImage = attachment.mimeType.startsWith("image/")
            val canInlineImage = isWorkspaceImage &&
                supportsInlineImageWithTools &&
                attachment.inlineBase64.isNotBlank()
            val accessHint = if (isWorkspaceImage) {
                if (canInlineImage) {
                    "This image was copied into the workspace and is also inserted into this model request when local bytes are available. " +
                        "Use analyze_image on this path for a focused second pass if needed."
                } else {
                    "This image was copied into the workspace. Call analyze_image on this exact path before answering questions about the image; " +
                        "this model endpoint does not reliably read images in tool-enabled agent requests."
                }
            } else {
                "Inspect this file through read, grep, find, ls, or bash inside the workspace instead of assuming its contents."
            }
            add(
                SharedPiContentPart.Text(
                    buildString {
                        append("Workspace attachment:\n")
                        append("Name: ${attachment.name}\n")
                        append("Type: ${attachment.mimeType.ifBlank { "unknown" }}\n")
                        if (attachment.sizeBytes > 0L) {
                            append("Size: ${formatSharedRequestBytes(attachment.sizeBytes)}\n")
                        }
                        append("Path: ${attachment.workspacePath}\n")
                        append("This file was uploaded in the current session.\n")
                        append(accessHint)
                    }
                )
            )
            if (canInlineImage) {
                add(SharedPiContentPart.Image(attachment.mimeType, attachment.inlineBase64))
            }
        }
        if (isEmpty()) add(SharedPiContentPart.Text("[Empty message]"))
    }
    return SharedPiChatMessage(
        role = if (fromUser) "user" else "assistant",
        text = text,
        contentParts = contentParts,
        providerPayload = providerPayloadJson.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        },
    )
}

internal fun sharedSupportsInlineImageWithTools(config: LlmProviderConfig): Boolean {
    val host = config.baseUrl.trim().lowercase()
        .substringAfter("://", "")
        .substringBefore('/')
        .substringBefore(':')
    val model = config.modelId.trim().lowercase()
    return !("moonshot.cn" in host || model.startsWith("kimi-") || "moonshot" in model)
}

private fun formatSharedRequestBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> formatSharedDecimal(bytes.toDouble() / (1024.0 * 1024.0)) + " MB"
    bytes >= 1024L -> formatSharedDecimal(bytes.toDouble() / 1024.0) + " KB"
    else -> "$bytes B"
}

private fun ByteArray.encodeBase64(): String = buildString(((size + 2) / 3) * 4) {
    var index = 0
    while (index < size) {
        val first = this@encodeBase64[index++].toInt() and 0xff
        val hasSecond = index < size
        val second = if (hasSecond) this@encodeBase64[index++].toInt() and 0xff else 0
        val hasThird = index < size
        val third = if (hasThird) this@encodeBase64[index++].toInt() and 0xff else 0
        append(Base64Alphabet[first ushr 2])
        append(Base64Alphabet[((first and 0x03) shl 4) or (second ushr 4)])
        append(if (hasSecond) Base64Alphabet[((second and 0x0f) shl 2) or (third ushr 6)] else '=')
        append(if (hasThird) Base64Alphabet[third and 0x3f] else '=')
    }
}

private fun PlatformPickedFile.sharedSourceIdentifier(): String = buildString {
    append(name)
    append('|')
    append(mimeType)
    append('|')
    append(bytes.size)
    append('|')
    append(bytes.contentHashCode())
}

@Composable
private fun SharedOnboarding(
    runtime: MultiplatformLocalRuntime,
    bridgeClient: SharedPiBridgeClient,
    existingProviderConfig: LlmProviderConfig?,
    replayMode: Boolean,
    onTransientMessage: (String) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onComplete: (LlmProviderConfig) -> Unit,
    initialStage: OnboardingStage = OnboardingStage.Landing,
    initialSearchValue: String,
    onSearchDone: (String) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    var stage by rememberSaveable(replayMode, initialStage) { mutableStateOf(initialStage) }
    val timelinePosition by animateFloatAsState(
        targetValue = stage.ordinal.toFloat(),
        animationSpec = tween(if (reduceMotion) 0 else 620, easing = SharedScreenTransitionEasing),
        label = "shared_onboarding_timeline_position",
    )
    val onTimelineStepSelected: (OnboardingTimelineStep) -> Unit = { selected ->
        stage = when (selected) {
            OnboardingTimelineStep.Welcome -> OnboardingStage.Landing
            OnboardingTimelineStep.Setup -> OnboardingStage.Runtime
            OnboardingTimelineStep.Provider -> OnboardingStage.Provider
            OnboardingTimelineStep.Search -> OnboardingStage.Search
        }
    }
    CompositionLocalProvider(LocalOnboardingTimelinePosition provides timelinePosition) {
    when (stage) {
            OnboardingStage.Landing -> OnboardingLandingStep(
                stepIndex = 1,
                stepCount = 3,
                replayMode = replayMode,
                onPrimary = { stage = OnboardingStage.Runtime },
                onSecondary = if (replayMode) onClose else onSkip,
                timelineSpec = OnboardingTimelineSpec(
                    activeStep = OnboardingTimelineStep.Welcome,
                    onStepSelected = onTimelineStepSelected,
                ),
            )
            OnboardingStage.Runtime -> RuntimeSetupStep(
                runtime = runtime,
                bridgeClient = bridgeClient,
                replayMode = replayMode,
                onBack = { stage = OnboardingStage.Landing },
                onClose = onClose,
                onContinue = { stage = OnboardingStage.Provider },
                onTimelineStepSelected = onTimelineStepSelected,
            )
            OnboardingStage.Provider -> SharedProviderSetupStep(
                bridgeClient = bridgeClient,
                existingProviderConfig = existingProviderConfig,
                onTransientMessage = onTransientMessage,
                onBack = { stage = OnboardingStage.Runtime },
                replayMode = replayMode,
                onSkip = if (replayMode) onClose else onSkip,
                onComplete = onComplete,
                onTimelineStepSelected = onTimelineStepSelected,
            )
            OnboardingStage.Search -> SharedTavilyFollowUpTour(
                initialValue = initialSearchValue,
                onClose = onClose,
                onDone = onSearchDone,
                timelineSpec = OnboardingTimelineSpec(
                    activeStep = OnboardingTimelineStep.Search,
                    onStepSelected = onTimelineStepSelected,
                ),
            )
    }
    }
}

@Composable
private fun SharedProviderSetupStep(
    bridgeClient: SharedPiBridgeClient,
    existingProviderConfig: LlmProviderConfig?,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
    replayMode: Boolean,
    onSkip: () -> Unit,
    onComplete: (LlmProviderConfig) -> Unit,
    onTimelineStepSelected: (OnboardingTimelineStep) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val oauthWaitingMessage = stringResource(Res.string.provider_form_oauth_waiting)
    val credentialsWaitingMessage = stringResource(Res.string.provider_form_credentials_waiting)
    val oauthConnectedMessage = stringResource(Res.string.provider_form_oauth_connected)
    val apiKeyConfiguredMessage = stringResource(Res.string.provider_form_api_key_configured)
    val completeAuthorizationMessage = stringResource(Res.string.provider_form_complete_authorization_browser)
    val enterDeviceCodeMessage = stringResource(Res.string.provider_form_enter_device_code_browser)
    val unknownErrorMessage = stringResource(Res.string.common_unknown_error)
    val fetchErrorPlaceholder = "{fetch_error}"
    val fetchModelsFailedTemplate = stringResource(
        Res.string.message_fetch_models_failed,
        fetchErrorPlaceholder,
    )
    val formState = rememberProviderFormState(existingProviderConfig)
    val modelCatalogClient = remember { SharedProviderModelCatalogClient() }
    var authState by remember { mutableStateOf(PiProviderAuthState()) }
    var authJob by remember { mutableStateOf<Job?>(null) }
    var fetchingModels by remember { mutableStateOf(false) }

    DisposableEffect(bridgeClient) {
        onDispose { authJob?.cancel() }
    }

    fun clearAuthState() {
        authJob?.cancel()
        authJob = null
        authState = PiProviderAuthState()
    }

    SharedProviderOnboardingStep(
        stepIndex = 3,
        stepCount = 3,
        replayMode = replayMode,
        formState = formState,
        isFetchingModels = fetchingModels,
        onFetchModels = { config, callback ->
                fetchingModels = true
                scope.launch {
                    try {
                        val result = modelCatalogClient.fetchModels(config, bridgeClient::listProviders)
                        callback(result.models)
                        result.error?.let { error ->
                            onTransientMessage(fetchModelsFailedTemplate.replace(fetchErrorPlaceholder, error))
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Throwable) {
                        callback(emptyList())
                        onTransientMessage(
                            fetchModelsFailedTemplate.replace(
                                fetchErrorPlaceholder,
                                failure.message.orEmpty().ifBlank { unknownErrorMessage },
                            )
                        )
                    } finally {
                        fetchingModels = false
                    }
                }
            },
        authState = authState,
        onStartProviderLogin = login@ { configId, providerId, authMethod, oauthFlow ->
                val normalizedProviderId = providerId.trim()
                if (normalizedProviderId.isBlank() || authMethod == ProviderAuthMethod.Ambient) {
                    return@login
                }
                authJob?.cancel()
                authState = PiProviderAuthState(
                    providerId = normalizedProviderId,
                    authMethod = authMethod,
                    isRunning = true,
                    statusMessage = if (authMethod == ProviderAuthMethod.OAuth) {
                        oauthWaitingMessage
                    } else {
                        credentialsWaitingMessage
                    },
                )
                authJob = scope.launch {
                    runSharedAppCatching {
                        bridgeClient.loginProvider(
                            providerConfigId = configId,
                            providerId = normalizedProviderId,
                            authMethod = authMethod.storageValue,
                            oauthFlow = oauthFlow,
                        ) { event, payload ->
                            if (
                                authState.providerId == normalizedProviderId &&
                                authState.authMethod == authMethod
                            ) {
                                authState = authState.withBridgeAuthEvent(
                                    event = event,
                                    payload = payload,
                                    completeAuthorizationMessage = completeAuthorizationMessage,
                                    enterDeviceCodeMessage = enterDeviceCodeMessage,
                                )
                            }
                        }
                    }.fold(
                        onSuccess = { payload ->
                            if (
                                authState.providerId == normalizedProviderId &&
                                authState.authMethod == authMethod
                            ) {
                                authState = authState.copy(
                                    isRunning = false,
                                    prompt = null,
                                    apiKey = payload.string("api_key"),
                                    oauthCredentialJson = (payload["oauth_credential"] as? JsonObject)
                                        ?.toString()
                                        .orEmpty(),
                                    providerEnvironmentVariables = payload.toPiProviderEnvironmentVariables(),
                                    statusMessage = if (authMethod == ProviderAuthMethod.OAuth) {
                                        oauthConnectedMessage
                                    } else {
                                        apiKeyConfiguredMessage
                                    },
                                    errorMessage = "",
                                )
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) return@fold
                            if (
                                authState.providerId == normalizedProviderId &&
                                authState.authMethod == authMethod
                            ) {
                                authState = authState.copy(
                                    isRunning = false,
                                    prompt = null,
                                    statusMessage = "",
                                    errorMessage = error.sharedUserFacingMessage(),
                                )
                            }
                        },
                    )
                }
            },
        onSubmitAuthPrompt = { promptId, value, cancelled ->
                scope.launch {
                    try {
                        bridgeClient.submitAuthPrompt(promptId, value, cancelled)
                        if (authState.prompt?.id == promptId) {
                            authState = authState.copy(prompt = null)
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Throwable) {
                        if (authState.prompt?.id == promptId) {
                            authState = authState.copy(errorMessage = failure.sharedUserFacingMessage())
                        }
                    }
                }
            },
        onClearAuthState = ::clearAuthState,
        onExit = onSkip,
        onClose = onSkip,
        onReturnToLanding = onBack,
        onComplete = { onComplete(formState.buildConfig()) },
        onTimelineStepSelected = onTimelineStepSelected,
    )
}

private fun PiProviderAuthState.withBridgeAuthEvent(
    event: String,
    payload: JsonObject,
    completeAuthorizationMessage: String,
    enterDeviceCodeMessage: String,
): PiProviderAuthState =
    when (event) {
        "auth_url" -> copy(
            authorizationUrl = payload.string("url"),
            statusMessage = payload.string("instructions").ifBlank {
                completeAuthorizationMessage
            },
        )
        "auth_device_code" -> copy(
            deviceCode = payload.string("user_code"),
            verificationUrl = payload.string("verification_uri"),
            statusMessage = enterDeviceCodeMessage,
        )
        "auth_prompt" -> copy(
            prompt = payload.toPiOAuthPrompt(),
            statusMessage = payload.string("message"),
        )
        "auth_progress" -> copy(statusMessage = payload.string("message"))
        else -> this
    }

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.toolSummary(): String = sequenceOf(
    string("path"),
    string("command"),
    string("pattern"),
    string("duration_ms"),
).firstOrNull { it.isNotBlank() }.orEmpty().take(180)

private fun String.toolOutputSummary(): String = runCatching {
    val payload = Json.parseToJsonElement(this) as? JsonObject ?: return@runCatching this
    sequenceOf(
        payload.string("stdout"),
        payload.string("stderr"),
        payload.string("error"),
        payload.string("path"),
    ).firstOrNull { it.isNotBlank() }.orEmpty()
}.getOrDefault(this).take(12_000)

@Composable
private fun RuntimeSetupStep(
    runtime: MultiplatformLocalRuntime,
    bridgeClient: SharedPiBridgeClient,
    replayMode: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onTimelineStepSelected: (OnboardingTimelineStep) -> Unit = {},
) {
    var retryKey by rememberSaveable { mutableIntStateOf(0) }
    var alpineReady by rememberSaveable { mutableStateOf(false) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var nodeVersion by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf(RuntimeSetupProgress("idle")) }
    var running by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(retryKey) {
        val shouldReset = retryKey > 0 && error.isNotBlank()
        ready = false
        error = ""
        if (retryKey > 0) running = true
        if (shouldReset) {
            try {
                bridgeClient.reset()
                runtime.resetForRetry()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "AI engine setup failed."
                progress = progress.copy(
                    output = (progress.output + "Setup failed: $error\n").takeLast(120_000),
                )
                running = false
                return@LaunchedEffect
            }
        }
        val installed = runSharedAppCatching { runtime.isReady() }.getOrElse { failure ->
            alpineReady = false
            error = failure.message.orEmpty()
            running = false
            return@LaunchedEffect
        }
        alpineReady = installed
        if (retryKey == 0) return@LaunchedEffect

        try {
            progress = RuntimeSetupProgress(
                phase = RuntimePhaseCheckingAlpine,
                output = progress.output,
            )
            runtime.initialize { update ->
                progress = update.copy(phase = normalizeRuntimeSetupPhase(update.phase))
            }
            alpineReady = true
            if (runtimeSetupStepIndex(progress.phase) < 2) {
                progress = progress.copy(phase = RuntimePhaseCheckingNode, detail = "")
            }
            val response = bridgeClient.ping { phase ->
                progress = progress.copy(
                    phase = phase.runtimeSetupPhase(),
                    detail = "",
                    fraction = null,
                )
            }
            nodeVersion = response["node_version"]
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
                .removePrefix("v")
            progress = progress.copy(
                phase = RuntimePhaseReady,
                detail = "",
                fraction = 1f,
                output = (progress.output + "AI engine setup complete.\n").takeLast(120_000),
            )
            ready = true
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "AI engine setup failed."
            progress = progress.copy(
                output = (progress.output + "Setup failed: $error\n").takeLast(120_000),
            )
        } finally {
            running = false
        }
    }

    OnboardingConversationStepPage(
        stepIndex = 2,
        stepCount = 3,
        message = stringResource(Res.string.onboarding_alpine_runtime_message),
        onBack = onBack,
        topRightLabel = stringResource(Res.string.close_label),
        onTopRight = onClose,
        timelineSpec = OnboardingTimelineSpec(
            activeStep = OnboardingTimelineStep.Setup,
            onStepSelected = onTimelineStepSelected,
        ),
        wideAuxiliaryVisible = retryKey > 0 && (running || progress.output.isNotBlank()),
        wideAuxiliaryContent = {
            RuntimeSetupLogPane(
                output = progress.output,
                running = running,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OnboardingStepLead(
                icon = Icons.Rounded.Code,
                accent = when {
                    alpineReady -> AetherSecondary
                    error.isNotBlank() -> AetherTertiary
                    else -> AetherPrimary
                },
                title = stringResource(Res.string.alpine_title),
                body = when {
                    alpineReady -> stringResource(Res.string.onboarding_alpine_status_ready)
                    error.isNotBlank() -> stringResource(Res.string.onboarding_alpine_status_failed)
                    else -> stringResource(Res.string.onboarding_alpine_status_not_installed)
                },
            )
            if (running || progress.output.isNotBlank() || ready || error.isNotBlank()) {
                RuntimeSetupProgressPanel(
                    progress = progress,
                    ready = ready,
                    error = error,
                    nodeVersion = nodeVersion,
                    onShowDetails = { showDetails = true },
                    showDetailsAction = !LocalOnboardingWideLayout.current,
                )
            }
            OnboardingActionRow(
                primaryLabel = stringResource(
                    when {
                        replayMode && alpineReady -> Res.string.common_next
                        ready -> Res.string.continue_label
                        error.isNotBlank() -> Res.string.retry_label
                        retryKey == 0 -> Res.string.settings_initialize
                        else -> Res.string.onboarding_pi_setup_working
                    },
                ),
                onPrimary = if (ready || replayMode && alpineReady) onContinue else ({ retryKey += 1 }),
                primaryEnabled = !running,
                primaryLoading = running,
                secondaryLabel = stringResource(
                    if (ready || replayMode && alpineReady) Res.string.common_refresh else Res.string.back_label,
                ),
                onSecondary = if (ready || replayMode && alpineReady) ({ retryKey += 1 }) else onBack,
            )
        }
    }
    if (showDetails) {
        RuntimeSetupDetailsDialog(
            output = progress.output,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun RuntimeSetupLogPane(
    output: String,
    running: Boolean,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(output) {
        if (output.isNotBlank()) scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = Modifier.fillMaxSize().background(AetherSurfaceHigh.copy(alpha = 0.54f))
            .statusBarsPadding().navigationBarsPadding()
            .padding(start = 34.dp, top = 34.dp, end = 34.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_setup_log_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AetherOnSurface,
            )
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AetherPrimary,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        SelectionContainer(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
        ) {
            Text(
                text = output.ifBlank {
                    stringResource(Res.string.onboarding_setup_log_waiting)
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp,
                ),
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuntimeSetupProgressPanel(
    progress: RuntimeSetupProgress,
    ready: Boolean,
    error: String,
    nodeVersion: String,
    onShowDetails: () -> Unit,
    showDetailsAction: Boolean,
) {
    val currentStep = runtimeSetupDisplayedStep(progress.phase, ready)
    val fraction = if (ready) 1f else currentStep / 5f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 700, easing = SharedScreenTransitionEasing),
        label = "pi_core_setup_progress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                error.isNotBlank() -> stringResource(Res.string.onboarding_pi_setup_failed)
                ready -> stringResource(
                    Res.string.onboarding_pi_setup_ready,
                    nodeVersion.ifBlank { "-" },
                )
                else -> runtimeSetupStatusText(progress.phase)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (error.isNotBlank()) AetherTertiary else AetherOnSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                Text(
                    text = stringResource(Res.string.onboarding_pi_setup_step, currentStep, 5),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (showDetailsAction) {
                Text(
                    text = stringResource(Res.string.onboarding_pi_setup_details),
                    modifier = Modifier.clickable(onClick = onShowDetails).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AetherOutlineSoft),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (ready) AetherSecondary else AetherPrimary),
                )
            }
        }
        if (normalizeRuntimeSetupPhase(progress.phase) == RuntimePhaseInstallingNode) {
            Text(
                text = stringResource(Res.string.onboarding_pi_setup_node_wait_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        } else if (error.isNotBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

private const val RuntimePhaseCheckingAlpine = "checking_alpine"
private const val RuntimePhaseCheckingNode = "checking_node"
private const val RuntimePhaseInstallingNode = "installing_node"
private const val RuntimePhasePreparingBridge = "preparing_bridge"
private const val RuntimePhaseStartingBridge = "starting_bridge"
private const val RuntimePhaseVerifyingBridge = "verifying_bridge"
private const val RuntimePhaseReady = "ready"

private fun normalizeRuntimeSetupPhase(phase: String): String = when (phase.lowercase()) {
    "rootfs", "kernel", RuntimePhaseCheckingAlpine -> RuntimePhaseCheckingAlpine
    "node_check", RuntimePhaseCheckingNode -> RuntimePhaseCheckingNode
    "node", "node_install", RuntimePhaseInstallingNode -> RuntimePhaseInstallingNode
    RuntimePhasePreparingBridge -> RuntimePhasePreparingBridge
    RuntimePhaseStartingBridge -> RuntimePhaseStartingBridge
    RuntimePhaseVerifyingBridge -> RuntimePhaseVerifyingBridge
    RuntimePhaseReady -> RuntimePhaseCheckingNode
    else -> phase.lowercase()
}

internal fun runtimeSetupStepIndex(phase: String): Int = when (normalizeRuntimeSetupPhase(phase)) {
    RuntimePhaseCheckingAlpine -> 1
    RuntimePhaseCheckingNode, RuntimePhaseInstallingNode -> 2
    RuntimePhasePreparingBridge -> 3
    RuntimePhaseStartingBridge -> 4
    RuntimePhaseVerifyingBridge -> 5
    else -> 0
}

internal fun runtimeSetupDisplayedStep(phase: String, ready: Boolean): Int =
    if (ready) 5 else runtimeSetupStepIndex(phase)

private fun PiBridgeSetupPhase.runtimeSetupPhase(): String = when (this) {
    PiBridgeSetupPhase.PreparingBridge -> RuntimePhasePreparingBridge
    PiBridgeSetupPhase.StartingBridge -> RuntimePhaseStartingBridge
    PiBridgeSetupPhase.VerifyingBridge -> RuntimePhaseVerifyingBridge
}

@Composable
private fun runtimeSetupStatusText(phase: String): String = when (normalizeRuntimeSetupPhase(phase)) {
    RuntimePhaseCheckingAlpine -> stringResource(Res.string.onboarding_pi_setup_checking_alpine)
    RuntimePhaseCheckingNode -> stringResource(Res.string.onboarding_pi_setup_checking_node)
    RuntimePhaseInstallingNode -> stringResource(Res.string.onboarding_pi_setup_installing_node)
    RuntimePhasePreparingBridge -> stringResource(Res.string.onboarding_pi_setup_preparing_bridge)
    RuntimePhaseStartingBridge -> stringResource(Res.string.onboarding_pi_setup_starting_bridge)
    RuntimePhaseVerifyingBridge -> stringResource(Res.string.onboarding_pi_setup_verifying_bridge)
    else -> stringResource(Res.string.onboarding_pi_setup_pending)
}

@Composable
private fun RuntimeSetupDetailsDialog(
    output: String,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(output) { scrollState.scrollTo(scrollState.maxValue) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AetherSurfaceHigh,
            contentColor = AetherOnSurface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.onboarding_pi_setup_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.common_close),
                            tint = AetherOnSurfaceVariant,
                        )
                    }
                }
                val terminalOutput = output.ifBlank {
                    stringResource(Res.string.onboarding_pi_setup_waiting_for_output)
                }
                SharedSyntaxHighlightedCodeBlock(
                    label = stringResource(Res.string.onboarding_pi_setup_output),
                    content = remember(terminalOutput) {
                        highlightSharedTerminalTranscript(terminalOutput)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    maxHeight = 420.dp,
                    scrollState = scrollState,
                )
            }
        }
    }
}

@Composable
private fun SharedCompactStatusDivider(text: String, isRunning: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.weight(1f).height(1.dp)
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
            val displayText = text.ifBlank { stringResource(Res.string.chat_context_compacted) }
            if (isRunning) {
                SharedReasoningShimmerText(
                    text = displayText,
                    modifier = Modifier.widthIn(max = 190.dp),
                    travelDurationMillis = 2_200,
                    pauseDurationMillis = 700,
                )
            } else {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(AetherOnSurfaceVariant.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun SharedCompactSuggestionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(AetherSurfaceHigh.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            stringResource(Res.string.chat_compact),
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text,
            color = AetherOnSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun sharedCompactContextPercent(messages: List<SharedChatMessage>): Int? {
    val visible = messages.filter {
        it.displayKind != SharedMessageDisplayKind.HiddenContext &&
            it.displayKind != SharedMessageDisplayKind.CompactStatus
    }
    if (visible.size < 2) return null
    val estimatedChars = visible.sumOf { message ->
        message.text.length +
            message.attachments.sumOf { attachment ->
                attachment.name.length + attachment.mimeType.length + attachment.workspacePath.length
            } +
            message.tools.sumOf { tool ->
                tool.name.length + tool.argumentsJson.length + tool.outputJson.length
            }
    }
    return ((estimatedChars * 100L) / SharedCompactingMaxInputChars)
        .toInt()
        .coerceIn(1, 100)
}

internal fun shouldAutoCompactSharedContext(
    usage: SharedPiUsage?,
    tokenUsageSource: String,
    assistantText: String,
    contextWindow: Long = SharedContextWindowTokens,
    reserveTokens: Long = SharedAutoCompactionReserveTokens,
): Boolean {
    if (usage == null || !usage.totalTokensAvailable || contextWindow <= reserveTokens) return false
    val trailingEstimate = if (tokenUsageSource == "estimated") {
        approximateSharedReasoningTokenCount(assistantText).toLong()
    } else {
        0L
    }
    return usage.totalTokens + trailingEstimate > contextWindow - reserveTokens
}

@Composable
private fun SharedChatScreen(
    sessions: List<SharedConversationSummary>,
    selectedSessionId: String,
    composerSessionKey: String,
    messages: List<SharedChatMessage>,
    pendingTurns: List<SharedPendingTurn>,
    runtime: MultiplatformLocalRuntime,
    platformServices: PlatformServices,
    availableSkills: List<SharedInstalledSkill>,
    selectedSkillIds: List<String>,
    onSkillSelected: (String, Boolean) -> Unit,
    mcpServers: List<SharedMcpServerConfig>,
    activeMcpServerIds: List<String>,
    onMcpServerSelected: (String, Boolean) -> Unit,
    chromeAvailable: Boolean,
    chromeEnabled: Boolean,
    onChromeSelected: (Boolean) -> Unit,
    composerState: SharedSessionUiState,
    isSending: Boolean,
    streamingStatus: String,
    selectedModelKey: String,
    modelOptions: List<ProviderModelOption>,
    modelCatalogInfo: Map<String, SharedModelCatalogInfo>,
    thinkingLevelsByProviderModel: Map<String, List<String>>,
    thinkingLevelClampsByProviderModel: Map<String, Map<String, String>>,
    reasoningEffort: String,
    onTransientMessage: (String) -> Unit,
    onModelMenuOpened: () -> Unit,
    onModelSelected: (String, (Boolean) -> Unit) -> Unit,
    onReasoningSelected: (String) -> Unit,
    editingMessageId: String,
    showStarterPromptHint: Boolean,
    onDismissStarterPromptHint: () -> Unit,
    onCancelEdit: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: (List<SharedChatAttachment>) -> Unit,
    onRetry: (String) -> Unit,
    onRetryUserMessage: (String) -> Unit,
    onQueueFollowUp: (List<SharedChatAttachment>) -> Unit,
    onSteerFollowUp: (List<SharedChatAttachment>) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onSelectUserBranch: (String, Int) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onExportSession: (String) -> Unit,
    extensionPages: List<SharedAetherExtensionPage>,
    onExtensionPageSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDrawerOpened: () -> Unit,
    drawerOpenedEventRegistered: Boolean,
    useTabletLayout: Boolean,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val latestOnDrawerOpened by rememberUpdatedState(onDrawerOpened)
    val latestDrawerOpenedEventRegistered by rememberUpdatedState(drawerOpenedEventRegistered)
    val drawerOpenedEventGate = remember { SharedDrawerOpenedEventGate() }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val visibleMessages = messages.filter {
        it.displayKind != SharedMessageDisplayKind.HiddenContext
    }
    if (!useTabletLayout) {
        LaunchedEffect(drawerState) {
            var previousDrawerValue: DrawerValue? = null
            snapshotFlow { drawerState.currentValue to drawerState.targetValue }
                .distinctUntilChanged()
                .collect { (currentValue, targetValue) ->
                    val currentOpen = currentValue == DrawerValue.Open
                    val targetOpen = targetValue == DrawerValue.Open
                    val openedAfterClosed =
                        previousDrawerValue == DrawerValue.Closed && currentOpen && targetOpen
                    previousDrawerValue = currentValue
                    if (!currentOpen) {
                        drawerOpenedEventGate.onMobileDrawerClosed()
                    }
                    if (
                        !isSharedDrawerClosing(currentOpen, targetOpen) &&
                        openedAfterClosed
                    ) {
                        val shouldDispatchDrawerOpened = drawerOpenedEventGate.onMobileDrawerOpened(
                            latestDrawerOpenedEventRegistered
                        )
                        if (shouldDispatchDrawerOpened) {
                            latestOnDrawerOpened()
                        }
                    }
                }
        }
    }
    LaunchedEffect(
        useTabletLayout,
        drawerOpenedEventRegistered,
        drawerState.currentValue,
        drawerState.targetValue,
    ) {
        val shouldDispatchDrawerOpened =
            drawerOpenedEventGate.onLayoutRegistrationOrDrawerSnapshotChanged(
                useTabletLayout = useTabletLayout,
                currentOpen = drawerState.currentValue == DrawerValue.Open,
                targetOpen = drawerState.targetValue == DrawerValue.Open,
                eventRegistered = drawerOpenedEventRegistered,
            )
        if (shouldDispatchDrawerOpened) {
            latestOnDrawerOpened()
        }
    }
    val listState = rememberSaveable(selectedSessionId, saver = LazyListState.Saver) { LazyListState() }
    var shouldAutoFollow by rememberSaveable(selectedSessionId) { mutableStateOf(true) }
    var topBarBodyHeightPx by remember(selectedSessionId) { mutableIntStateOf(0) }
    var composerBodyHeightPx by remember(selectedSessionId) { mutableIntStateOf(0) }
    var composerFocused by remember(selectedSessionId) { mutableStateOf(false) }
    var previewAttachment by remember(selectedSessionId) { mutableStateOf<SharedChatAttachment?>(null) }
    val density = LocalDensity.current
    val edgeBounce = remember(selectedSessionId) { Animatable(0f) }
    val maxEdgeBouncePx = with(density) { 34.dp.toPx() }
    val branchBlur = remember(selectedSessionId) { Animatable(0f) }
    val fallbackTopBarBodyHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 68.dp
    }
    val topBarBodyHeight = with(density) {
        if (topBarBodyHeightPx > 0) topBarBodyHeightPx.toDp() else fallbackTopBarBodyHeight
    }
    val composerBodyHeight = with(density) {
        if (composerBodyHeightPx > 0) composerBodyHeightPx.toDp() else 112.dp
    }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val animatedImeBottom by animateDpAsState(
        targetValue = imeBottom,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_conversation_ime_bottom",
    )
    val sessionTotalTokens = messages.mapNotNull { it.usage }
        .sumOf { usage -> if (usage.totalTokensAvailable) usage.totalTokens else 0L }
        .takeIf { it > 0L }
    val compactPercent = sharedCompactContextPercent(messages)
    val compactSuggestionText = compactPercent?.let { percent ->
        stringResource(
            if (useTabletLayout) {
                Res.string.chat_compact_thread_context_percent
            } else {
                Res.string.chat_context_percent
            },
            percent,
        )
    } ?: stringResource(Res.string.chat_compact_thread_context)
    val attachmentPreviewFailedMessage = stringResource(Res.string.attachment_preview_failed)
    val unableToOpenLinkMessage = stringResource(Res.string.app_unable_to_open_link)
    val replyCopiedMessage = stringResource(Res.string.file_reply_copied)
    val fileSavedMessage = stringResource(Res.string.file_saved)
    val fileCouldNotSaveMessage = stringResource(Res.string.file_could_not_save)
    val aetherFileName = stringResource(Res.string.chat_aether_file)
    val conversationContentKey = remember(visibleMessages, pendingTurns, streamingStatus) {
        buildString {
            visibleMessages.forEach { message ->
                append(message.id)
                append(':')
                append(message.text.length)
                append(':')
                append(message.reasoningText.length)
                append(':')
                append(message.status)
                append(':')
                append(message.statusDetail)
                append(':')
                append(message.isStreaming)
                message.tools.forEach { tool ->
                    append('|')
                    append(tool.id)
                    append(':')
                    append(tool.summary.length)
                    append(':')
                    append(tool.output.length)
                    append(':')
                    append(tool.isRunning)
                }
                message.responseBlocks.filterIsInstance<SharedAssistantResponseBlock.Reasoning>()
                    .forEach { block ->
                        append("|reasoning:")
                        append(block.id)
                        append(':')
                        append(block.trace.rawText.length)
                        append(':')
                        append(block.trace.latestStatusText.length)
                        append(':')
                        append(block.trace.chunks.size)
                        append(':')
                        append(block.trace.toolInvocations.size)
                        append(':')
                        append(block.trace.completedAtMillis ?: 0L)
                    }
            }
            pendingTurns.forEach { pending ->
                append("|pending:")
                append(pending.id)
                append(':')
                append(pending.text.length)
                append(':')
                append(pending.attachments.size)
            }
            append("|status:")
            append(streamingStatus)
        }
    }
    val conversationScrollConnection = remember(listState, maxEdgeBouncePx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    shouldAutoFollow = listState.isAtSharedConversationBottom()
                    if (available.y != 0f) {
                        val resisted = (edgeBounce.value + available.y * 0.18f)
                            .coerceIn(-maxEdgeBouncePx, maxEdgeBouncePx)
                        scope.launch { edgeBounce.snapTo(resisted) }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldAutoFollow = listState.isAtSharedConversationBottom()
                edgeBounce.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 720f,
                    ),
                )
                return Velocity.Zero
            }
        }
    }
    fun switchUserBranch(messageId: String, branchIndex: Int) {
        scope.launch {
            branchBlur.animateTo(
                targetValue = 5.5f,
                animationSpec = tween(
                    durationMillis = SharedBranchBlurInDurationMillis,
                    easing = SharedBranchBlurInEasing,
                ),
            )
            onSelectUserBranch(messageId, branchIndex)
            kotlinx.coroutines.yield()
            branchBlur.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SharedBranchBlurOutDurationMillis,
                    easing = SharedBranchBlurOutEasing,
                ),
            )
        }
    }
    suspend fun scrollToBottom() {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }
    LaunchedEffect(listState, shouldAutoFollow) {
        if (!shouldAutoFollow) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            listOf(
                layout.totalItemsCount,
                lastVisible?.index ?: -1,
                lastVisible?.offset ?: 0,
                lastVisible?.size ?: 0,
            )
        }.distinctUntilChanged().collect {
            if (shouldAutoFollow && !listState.isScrollInProgress) scrollToBottom()
        }
    }
    LaunchedEffect(
        conversationContentKey,
        topBarBodyHeightPx,
        composerBodyHeightPx,
        animatedImeBottom,
        shouldAutoFollow,
    ) {
        if (shouldAutoFollow) {
            kotlinx.coroutines.yield()
            if (!listState.isScrollInProgress) scrollToBottom()
        }
    }
    SharedAdaptiveConversationLayout(
        useTabletLayout = useTabletLayout,
        drawerState = drawerState,
        drawerContent = {
            AetherConversationDrawer(
                sessions = sessions,
                selectedSessionId = selectedSessionId,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onSessionSelected = { id ->
                    onSessionSelected(id)
                    scope.launch { drawerState.close() }
                },
                onRenameSession = onRenameSession,
                onExportSession = onExportSession,
                onDeleteSession = onDeleteSession,
                onSettingsSelected = {
                    scope.launch {
                        drawerState.close()
                        onOpenSettings()
                    }
                },
                headerContent = {
                    SharedAetherExtensionSlot(SharedExtensionSlotDrawerHeader)
                },
                footerContent = {
                    SharedAetherExtensionSlot(SharedExtensionSlotDrawerFooter)
                },
                permanent = useTabletLayout,
                extraContent = { dismissSearch ->
                    SharedAetherExtensionSlot(SharedExtensionSlotDrawer)
                    SharedAetherExtensionSlot(SharedExtensionSlotDrawerListEnd)
                    extensionPages.forEach { page ->
                        SharedAetherExtensionPageLauncher(
                            page = page,
                            onClick = {
                                dismissSearch()
                                scope.launch { drawerState.close() }
                                onExtensionPageSelected(page.id)
                            },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                },
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AetherBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AetherBackgroundGradientTop, AetherBackground, AetherSurface))
                ).padding(innerPadding),
            ) {
                if (visibleMessages.isEmpty()) {
                    AetherConversationEmptyState(
                        modifier = Modifier.fillMaxSize().padding(
                            top = topBarBodyHeight + 20.dp,
                            bottom = composerBodyHeight + animatedImeBottom + 16.dp,
                        ),
                        welcomeLabel = stringResource(Res.string.chat_welcome_help),
                        analyzeImageLabel = stringResource(Res.string.chat_analyze_image_chip),
                        codeLabel = stringResource(Res.string.chat_code_chip),
                        helpWriteLabel = stringResource(Res.string.chat_help_me_write_chip),
                        summarizeFileLabel = stringResource(Res.string.chat_summarize_file_chip),
                        analyzeImagePrompt = stringResource(Res.string.chat_analyze_image_prompt),
                        codePrompt = stringResource(Res.string.chat_code_prompt),
                        helpWritePrompt = stringResource(Res.string.chat_help_write_prompt),
                        summarizeFilePrompt = stringResource(Res.string.chat_summarize_file_prompt),
                        inputFocused = composerFocused,
                        onStarterPromptSelected = onInputChanged,
                    )
                    SharedAetherExtensionSlot(
                        SharedExtensionSlotChatEmpty,
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = topBarBodyHeight + 12.dp,
                        ),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .nestedScroll(conversationScrollConnection)
                            .graphicsLayer { translationY = edgeBounce.value }
                            .blur(branchBlur.value.dp),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = topBarBodyHeight + 10.dp,
                            bottom = composerBodyHeight + animatedImeBottom + 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        item {
                            SharedAetherExtensionSlot(SharedExtensionSlotChatListStart)
                        }
                        itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, rawMessage ->
                            if (rawMessage.displayKind == SharedMessageDisplayKind.CompactStatus) {
                                SharedCompactStatusDivider(rawMessage.text)
                                return@itemsIndexed
                            }
                            val message = if (rawMessage.fromUser && rawMessage.userBranches.isNotEmpty()) {
                                rawMessage.copy(
                                    branchIndex = rawMessage.selectedUserBranchIndex,
                                    branchCount = rawMessage.userBranches.size,
                                )
                            } else {
                                rawMessage.copy(
                                    branchIndex = 0,
                                    branchCount = 1,
                                )
                            }
                            SharedConversationMessage(
                                message = message,
                                canRetry = !message.fromUser && !isSending,
                                onRetry = { onRetry(message.id) },
                                onCopy = { text ->
                                    platformServices.copyText(text).also { copied ->
                                        if (copied) onTransientMessage(replyCopiedMessage)
                                    }
                                },
                                onEdit = { onEditUserMessage(message.id) },
                                onPreviousBranch = {
                                    if (message.fromUser) {
                                        switchUserBranch(message.id, message.branchIndex - 1)
                                    }
                                },
                                onNextBranch = {
                                    if (message.fromUser) {
                                        switchUserBranch(message.id, message.branchIndex + 1)
                                    }
                                },
                                onDelete = if (message.fromUser) null else {
                                    { onDeleteMessage(message.id) }
                                },
                                onRetryUserMessage = if (message.fromUser) {
                                    { onRetryUserMessage(message.id) }
                                } else null,
                                onOpenAttachment = { attachment -> previewAttachment = attachment },
                                runtime = runtime,
                                onOpenLink = { rawLink ->
                                    val target = normalizeSharedMarkdownTarget(rawLink)
                                    if (target.startsWith("data:", ignoreCase = true)) {
                                        scope.launch {
                                            val binary = decodeSharedMarkdownDataUrl(target)
                                            if (binary == null) {
                                                onTransientMessage(attachmentPreviewFailedMessage)
                                                return@launch
                                            }
                                            val mimeType = binary.mimeType ?: "application/octet-stream"
                                            val name = sharedImagePreviewName(binary.mimeType)
                                            val previewed = platformServices.previewFile(name, mimeType, binary.bytes)
                                            if (!previewed && !platformServices.shareFile(name, mimeType, binary.bytes)) {
                                                onTransientMessage(attachmentPreviewFailedMessage)
                                            }
                                        }
                                    } else if (!isSharedWorkspaceFileLink(target)) {
                                        val externalTarget = normalizeSharedAssistantLinkTarget(target)
                                        if (!platformServices.openUrl(externalTarget)) {
                                            onTransientMessage(unableToOpenLinkMessage)
                                        }
                                    } else {
                                        scope.launch {
                                            val path = resolveSharedWorkspacePath(target, runtime.workspaceRoot)
                                            if (path == null) {
                                                onTransientMessage(attachmentPreviewFailedMessage)
                                                return@launch
                                            }
                                            runSharedAppCatching {
                                                val bytes = runtime.fileSystem.read(path)
                                                val name = path.substringAfterLast('/').ifBlank { aetherFileName }
                                                val mimeType = sharedMimeTypeForPath(path)
                                                platformServices.exportFile(name, mimeType, bytes)
                                            }.onSuccess { exported ->
                                                when (exported) {
                                                    true -> onTransientMessage(fileSavedMessage)
                                                    false -> onTransientMessage(fileCouldNotSaveMessage)
                                                    null -> Unit
                                                }
                                            }.onFailure {
                                                onTransientMessage(fileCouldNotSaveMessage)
                                            }
                                        }
                                    }
                                },
                                sessionTotalTokens = sessionTotalTokens,
                                metrics = SharedMessageMetrics(
                                    thoughtDurationMillis = message.thoughtDurationMillis.takeIf { it > 0 },
                                    outputTokensPerSecond = message.usage
                                        ?.takeIf { usage ->
                                            usage.outputTokensAvailable && usage.outputTokens > 0 &&
                                                message.responseDurationMillis > 0
                                        }
                                        ?.outputTokens
                                        ?.let { it * 1_000.0 / message.responseDurationMillis },
                                    firstTokenLatencyMillis = message.firstTokenLatencyMillis,
                                    tokenUsageSource = message.tokenUsageSource,
                                ),
                            )
                        }
                        if (streamingStatus == SharedCompactingStatus) {
                            item(key = "shared-compact-running") {
                                SharedCompactStatusDivider(
                                    text = stringResource(Res.string.chat_compacting_context),
                                    isRunning = true,
                                )
                            }
                        }
                        items(pendingTurns, key = SharedPendingTurn::id) { pending ->
                            SharedPendingInputBubble(pending)
                        }
                        item {
                            SharedAetherExtensionSlot(SharedExtensionSlotChatListEnd)
                        }
                    }
                }
                ConversationTopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onHeightChanged = { topBarBodyHeightPx = it },
                    onMenu = { scope.launch { drawerState.open() } },
                    showMenu = !useTabletLayout,
                    onNewChat = onNewChat,
                    selectedModelKey = selectedModelKey,
                    modelOptions = modelOptions,
                    modelCatalogInfo = modelCatalogInfo,
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel,
                    thinkingLevelClampsByProviderModel = thinkingLevelClampsByProviderModel,
                    reasoningEffort = reasoningEffort,
                    onOpened = onModelMenuOpened,
                    onModelSelected = onModelSelected,
                    onReasoningSelected = onReasoningSelected,
                )
                SharedComposer(
                    sessionKey = composerSessionKey,
                    composerState = composerState,
                    onValueChange = onInputChanged,
                    onSend = onSend,
                    isSending = isSending,
                    onStop = onStop,
                    onQueueFollowUp = onQueueFollowUp,
                    onSteerFollowUp = onSteerFollowUp,
                    runtime = runtime,
                    platformServices = platformServices,
                    availableSkills = availableSkills,
                    selectedSkillIds = selectedSkillIds,
                    onSkillSelected = onSkillSelected,
                    mcpServers = mcpServers,
                    activeMcpServerIds = activeMcpServerIds,
                    onMcpServerSelected = onMcpServerSelected,
                    chromeAvailable = chromeAvailable,
                    chromeEnabled = chromeEnabled,
                    onChromeSelected = onChromeSelected,
                    editingMessage = messages.firstOrNull { it.id == editingMessageId },
                    showStarterPromptHint = showStarterPromptHint,
                    onDismissStarterPromptHint = onDismissStarterPromptHint,
                    onCancelEdit = onCancelEdit,
                    compactSuggestionText = compactSuggestionText,
                    onFocusChanged = { composerFocused = it },
                    onHeightChanged = { composerBodyHeightPx = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                previewAttachment?.let { attachment ->
                    SharedAttachmentPreviewDialog(
                        attachment = attachment,
                        runtime = runtime,
                        onDismiss = { previewAttachment = null },
                        onSave = {
                            scope.launch {
                                runSharedAppCatching {
                                    val bytes = readSharedAttachmentBytes(attachment, runtime)
                                    platformServices.exportFile(
                                        attachment.name,
                                        attachment.mimeType,
                                        bytes,
                                    )
                                }.onSuccess { exported ->
                                    when (exported) {
                                        true -> onTransientMessage(fileSavedMessage)
                                        false -> onTransientMessage(fileCouldNotSaveMessage)
                                        null -> Unit
                                    }
                                }.onFailure {
                                    onTransientMessage(fileCouldNotSaveMessage)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedAdaptiveConversationLayout(
    useTabletLayout: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (useTabletLayout) {
        Row(Modifier.fillMaxSize()) {
            drawerContent()
            Box(Modifier.fillMaxHeight().width(1.dp).background(AetherOutlineSoft))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            content = content,
        )
    }
}

private fun LazyListState.isAtSharedConversationBottom(): Boolean {
    val layout = layoutInfo
    if (layout.totalItemsCount == 0) return true
    val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return true
    val isLastVisible = lastVisible.index == layout.totalItemsCount - 1
    val distanceFromBottom = layout.viewportEndOffset - (lastVisible.offset + lastVisible.size)
    return isLastVisible && distanceFromBottom >= -32
}

@Composable
private fun ConversationTopBar(
    modifier: Modifier,
    onHeightChanged: (Int) -> Unit,
    onMenu: () -> Unit,
    showMenu: Boolean,
    onNewChat: () -> Unit,
    selectedModelKey: String,
    modelOptions: List<ProviderModelOption>,
    modelCatalogInfo: Map<String, SharedModelCatalogInfo>,
    thinkingLevelsByProviderModel: Map<String, List<String>>,
    thinkingLevelClampsByProviderModel: Map<String, Map<String, String>>,
    reasoningEffort: String,
    onOpened: () -> Unit,
    onModelSelected: (String, (Boolean) -> Unit) -> Unit,
    onReasoningSelected: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to AetherBackground.copy(alpha = 0.98f),
                        0.28f to AetherBackground.copy(alpha = 0.92f),
                        0.58f to AetherBackground.copy(alpha = 0.52f),
                        0.82f to AetherBackground.copy(alpha = 0.18f),
                        1.0f to Color.Transparent,
                    ),
                )
            ).onSizeChanged { onHeightChanged(it.height) },
        ) {
            Column {
                AetherConversationTopBarFrame(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    menuDescription = stringResource(Res.string.common_menu),
                    newChatDescription = stringResource(Res.string.common_new_chat),
                    onMenu = onMenu,
                    onNewChat = onNewChat,
                    showMenu = showMenu,
                ) {
                    SharedConversationModelSelector(
                        options = modelOptions,
                        modelCatalogInfo = modelCatalogInfo,
                        selectedModelKey = selectedModelKey,
                        reasoningEffort = reasoningEffort,
                        thinkingLevelsByProviderModel = thinkingLevelsByProviderModel,
                        thinkingLevelClampsByProviderModel = thinkingLevelClampsByProviderModel,
                        onSelected = onModelSelected,
                        onOpened = onOpened,
                        onReasoningEffortSelected = onReasoningSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SharedAetherExtensionSlot(
                    SharedExtensionSlotChatTop,
                    Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        Spacer(
            modifier = Modifier.fillMaxWidth().height(TopFadeHeight).background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to AetherBackground.copy(alpha = 0.10f),
                        0.42f to AetherBackground.copy(alpha = 0.04f),
                        1.0f to Color.Transparent,
                    ),
                )
            )
        )
    }
}

@Composable
private fun SharedConversationModelSelector(
    options: List<ProviderModelOption>,
    modelCatalogInfo: Map<String, SharedModelCatalogInfo>,
    selectedModelKey: String,
    reasoningEffort: String,
    thinkingLevelsByProviderModel: Map<String, List<String>>,
    thinkingLevelClampsByProviderModel: Map<String, Map<String, String>>,
    onSelected: (String, (Boolean) -> Unit) -> Unit,
    onOpened: () -> Unit,
    onReasoningEffortSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showingReasoningEffort by remember { mutableStateOf(false) }
    var menuSelectedModelKey by remember { mutableStateOf(selectedModelKey) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val selectedOption = options.findModelOption(menuSelectedModelKey) ?: options.firstOrNull()
    val thinkingKey = selectedOption?.let { option ->
        sharedThinkingCatalogKey(option.piProviderId, option.modelId)
    }
    val supportedThinkingLevels = thinkingKey?.let(thinkingLevelsByProviderModel::get).orEmpty()
    val effectiveReasoningEffort = thinkingKey
        ?.let(thinkingLevelClampsByProviderModel::get)
        ?.get(reasoningEffort)
        ?: reasoningEffort
    val fallbackLabel = stringResource(Res.string.chat_select_model)
    val selectedDisplay = remember(selectedOption, modelCatalogInfo) {
        selectedOption?.let { option ->
            formatSharedSelectedModelDisplayName(
                modelCatalogInfo[option.key]?.displayName ?: option.modelId,
            )
        }
    }
    val selectedModelName = selectedDisplay
        ?.let { display -> listOf(display.primary, display.secondary).filter(String::isNotBlank) }
        ?.joinToString(" ")
        ?.takeIf(String::isNotBlank)
        ?: selectedOption?.chatLabel
        ?: fallbackLabel

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    anchorHeightPx = coordinates.boundsInWindow().height.toInt()
                }
                .height(38.dp)
                .shadow(4.dp, RoundedCornerShape(999.dp), ambientColor = ControlShadow, spotColor = ControlShadow)
                .clip(RoundedCornerShape(999.dp))
                .background(AetherSurface.copy(alpha = 0.96f))
                .clickable(enabled = options.isNotEmpty()) {
                    onOpened()
                    menuSelectedModelKey = selectedModelKey
                    showingReasoningEffort = false
                    expanded = true
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedDisplay != null) {
                SharedSelectedModelDisplay(
                    displayName = selectedDisplay,
                    modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = 17.dp),
                )
            } else {
                Text(
                    text = fallbackLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                    color = AetherOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 17.dp),
                )
            }
        }

        SharedAnimatedPopupHost(visible = expanded) { menuVisibility ->
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorHeightPx + with(density) { 10.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = fadeIn(tween(140, easing = SharedConversationMotionEasing)) +
                        scaleIn(
                            initialScale = 0.90f,
                            transformOrigin = TransformOrigin(0f, 0f),
                            animationSpec = tween(220, easing = SharedConversationMotionEasing),
                        ) + slideInVertically(
                            animationSpec = tween(240, easing = SharedConversationMotionEasing),
                            initialOffsetY = { -it / 12 },
                        ),
                    exit = fadeOut(tween(120, easing = SharedConversationMotionEasing)) +
                        scaleOut(
                            targetScale = 0.96f,
                            transformOrigin = TransformOrigin(0f, 0f),
                            animationSpec = tween(160, easing = SharedConversationMotionEasing),
                        ),
                ) {
                    AnimatedContent(
                        targetState = showingReasoningEffort && supportedThinkingLevels.isNotEmpty(),
                        transitionSpec = {
                            val enteringOffset: (Int) -> Int = { width ->
                                if (targetState) width / 10 else -width / 10
                            }
                            val exitingOffset: (Int) -> Int = { width ->
                                if (targetState) -width / 10 else width / 10
                            }
                            (
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        delayMillis = 60,
                                        easing = SharedConversationMotionEasing,
                                    ),
                                ) + slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 340,
                                        easing = SharedConversationMotionEasing,
                                    ),
                                    initialOffsetX = enteringOffset,
                                )
                            ).togetherWith(
                                fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 150,
                                        easing = SharedConversationMotionEasing,
                                    ),
                                ) + slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 280,
                                        easing = SharedConversationMotionEasing,
                                    ),
                                    targetOffsetX = exitingOffset,
                                )
                            ).using(
                                SizeTransform(clip = false) { _, _ ->
                                    tween(durationMillis = 360, easing = SharedConversationMotionEasing)
                                }
                            )
                        },
                        modifier = Modifier.width(242.dp)
                            .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AetherSurface)
                            .padding(vertical = 5.dp),
                        label = "shared-conversation-model-menu-mode",
                    ) { showReasoning ->
                        if (showReasoning) {
                            SharedConversationReasoningEffortMenu(
                                efforts = supportedThinkingLevels,
                                selectedEffort = effectiveReasoningEffort,
                                selectedModelName = selectedModelName,
                                onBack = { showingReasoningEffort = false },
                                onSelected = { effort ->
                                    onReasoningEffortSelected(effort)
                                    expanded = false
                                },
                            )
                        } else {
                            SharedConversationModelListMenu(
                                options = options,
                                modelCatalogInfo = modelCatalogInfo,
                                selectedOption = selectedOption,
                                reasoningEffort = effectiveReasoningEffort,
                                showReasoningEffort = supportedThinkingLevels.isNotEmpty(),
                                onReasoningEffortClick = { showingReasoningEffort = true },
                                onSelected = { option ->
                                    menuSelectedModelKey = option.key
                                    onSelected(option.key) { hasThinkingLevels ->
                                        if (expanded && menuSelectedModelKey == option.key) {
                                            if (hasThinkingLevels) {
                                                showingReasoningEffort = true
                                            } else {
                                                expanded = false
                                            }
                                        }
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

@Composable
private fun SharedConversationMenuModeEntry(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 19.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurface)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SharedConversationReasoningEffortMenu(
    efforts: List<String>,
    selectedEffort: String,
    selectedModelName: String,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        SharedConversationMenuModeEntry(
            title = stringResource(Res.string.chat_model),
            value = selectedModelName,
            onClick = onBack,
        )
        efforts.forEach { effort ->
            val selected = effort == selectedEffort
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) AetherOnSurface.copy(alpha = 0.06f) else Color.Transparent)
                    .clickable { onSelected(effort) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sharedReasoningEffortLabel(effort),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = AetherOnSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedConversationModelListMenu(
    options: List<ProviderModelOption>,
    modelCatalogInfo: Map<String, SharedModelCatalogInfo>,
    selectedOption: ProviderModelOption?,
    reasoningEffort: String,
    showReasoningEffort: Boolean,
    onReasoningEffortClick: () -> Unit,
    onSelected: (ProviderModelOption) -> Unit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        if (showReasoningEffort) {
            SharedConversationMenuModeEntry(
                title = stringResource(Res.string.chat_reasoning_effort),
                value = sharedReasoningEffortLabel(reasoningEffort),
                onClick = onReasoningEffortClick,
            )
        }
        val modelListHeight = (options.size * 42).coerceAtMost(312).dp
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(modelListHeight),
        ) {
            items(options, key = ProviderModelOption::key) { option ->
                SharedConversationModelMenuRow(
                    option = option,
                    catalogInfo = modelCatalogInfo[option.key],
                    selected = option.key == selectedOption?.key,
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun SharedConversationModelMenuRow(
    option: ProviderModelOption,
    catalogInfo: SharedModelCatalogInfo?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) AetherOnSurface.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SharedLabLogoBadge(
            catalogInfo = catalogInfo,
            fallbackProviderId = option.piProviderId,
            modifier = Modifier.size(22.dp),
        )
        Text(
            option.chatLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SharedLabLogoBadge(
    catalogInfo: SharedModelCatalogInfo?,
    fallbackProviderId: String,
    modifier: Modifier = Modifier,
) {
    val info = catalogInfo
    val paths = remember(info?.labLogoPathData) {
        SharedModelLogoPathCache.getOrParse(info?.labLogoPathData.orEmpty())
    }
    if (info != null && paths.isNotEmpty()) {
        Canvas(modifier) {
            val scaleX = size.width / info.labLogoViewportWidth
            val scaleY = size.height / info.labLogoViewportHeight
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                paths.forEach { path -> drawPath(path, AetherOnSurface) }
            }
        }
        return
    }
    ProviderBrandIcon(
        providerId = info?.labId.orEmpty().ifBlank { fallbackProviderId },
        contentDescription = null,
        modifier = modifier.padding(2.5.dp),
    )
}

internal data class SharedSelectedModelDisplayName(
    val primary: String,
    val secondary: String,
    val icon: SharedSelectedModelDisplayIcon? = null,
)

internal enum class SharedSelectedModelDisplayIcon { Fast, Reasoning }

@Composable
private fun SharedSelectedModelDisplay(
    displayName: SharedSelectedModelDisplayName,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            displayName.primary,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (displayName.secondary.isNotBlank()) {
            Text(
                displayName.secondary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        displayName.icon?.let { icon ->
            Icon(
                imageVector = when (icon) {
                    SharedSelectedModelDisplayIcon.Fast -> LucideIcons.Zap
                    SharedSelectedModelDisplayIcon.Reasoning -> LucideIcons.Brain
                },
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

internal fun formatSharedSelectedModelDisplayName(rawName: String): SharedSelectedModelDisplayName {
    val normalizedTokens = rawName.trim().substringAfterLast('/').replace(Regex("[()]"), " ")
        .split(Regex("[\\s_-]+"))
        .mapNotNull { token -> token.trim().takeIf(String::isNotEmpty) }
    val icon = when {
        normalizedTokens.any { it.equals("reasoning", true) || it.equals("thinking", true) } ->
            SharedSelectedModelDisplayIcon.Reasoning
        normalizedTokens.any {
            it.equals("ultraspeed", true) || it.equals("fast", true) ||
                it.equals("spark", true) || it.equals("highspeed", true)
        } -> SharedSelectedModelDisplayIcon.Fast
        else -> null
    }
    val visibleTokens = normalizedTokens.filterNot { token ->
        token.equals("preview", true) || token.equals("reasoning", true) ||
            token.equals("thinking", true) || token.equals("ultraspeed", true) ||
            token.equals("fast", true) || token.equals("spark", true) || token.equals("highspeed", true)
    }.let { tokens ->
        if (tokens.joinToString(" ").length <= 28 && tokens.size <= 4) tokens
        else tokens.filterNot { token ->
            token.matches(Regex("\\d+b", RegexOption.IGNORE_CASE)) ||
                token.matches(Regex("a\\d+b", RegexOption.IGNORE_CASE))
        }
    }
    if (visibleTokens.isEmpty()) {
        return SharedSelectedModelDisplayName(rawName.trim(), "", icon)
    }
    val first = splitSharedTrailingModelNumber(visibleTokens.first())
    return SharedSelectedModelDisplayName(
        primary = titleSharedModelToken(first.first),
        secondary = buildList {
            first.second?.takeIf(String::isNotBlank)?.let(::add)
            addAll(visibleTokens.drop(1))
        }.joinToString(" ") { titleSharedModelToken(it) },
        icon = icon,
    )
}

private fun splitSharedTrailingModelNumber(token: String): Pair<String, String?> {
    val match = Regex("^([A-Za-z]+)(\\d[\\w.]*)$").matchEntire(token) ?: return token to null
    return match.groupValues[1] to match.groupValues[2]
}

private fun titleSharedModelToken(token: String): String {
    if (token.isBlank()) return token
    if (token.uppercase() == token && token.any(Char::isLetter)) return token
    if (token.equals("gpt", true) || token.equals("glm", true)) return token.uppercase()
    if (token.startsWith("v") && token.drop(1).firstOrNull()?.isDigit() == true) return "v" + token.drop(1)
    if (token.first().isDigit()) return token
    return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun sharedReasoningEffortLabel(effort: String): String = when (effort.trim().lowercase()) {
    "off" -> stringResource(Res.string.chat_reasoning_effort_off)
    "minimal" -> stringResource(Res.string.chat_reasoning_effort_minimal)
    "low" -> stringResource(Res.string.chat_reasoning_effort_low)
    "medium" -> stringResource(Res.string.chat_reasoning_effort_medium)
    "high" -> stringResource(Res.string.chat_reasoning_effort_high)
    "xhigh" -> stringResource(Res.string.chat_reasoning_effort_xhigh)
    "max" -> stringResource(Res.string.chat_reasoning_effort_max)
    else -> effort.trim().replaceFirstChar { it.titlecase() }
}

@Composable
private fun SharedComposer(
    sessionKey: String,
    composerState: SharedSessionUiState,
    onValueChange: (String) -> Unit,
    onSend: (List<SharedChatAttachment>) -> Unit,
    isSending: Boolean,
    onStop: () -> Unit,
    onQueueFollowUp: (List<SharedChatAttachment>) -> Unit,
    onSteerFollowUp: (List<SharedChatAttachment>) -> Unit,
    runtime: MultiplatformLocalRuntime,
    platformServices: PlatformServices,
    availableSkills: List<SharedInstalledSkill>,
    selectedSkillIds: List<String>,
    onSkillSelected: (String, Boolean) -> Unit,
    mcpServers: List<SharedMcpServerConfig>,
    activeMcpServerIds: List<String>,
    onMcpServerSelected: (String, Boolean) -> Unit,
    chromeAvailable: Boolean,
    chromeEnabled: Boolean,
    onChromeSelected: (Boolean) -> Unit,
    editingMessage: SharedChatMessage?,
    showStarterPromptHint: Boolean,
    onDismissStarterPromptHint: () -> Unit,
    onCancelEdit: () -> Unit,
    compactSuggestionText: String,
    onFocusChanged: (Boolean) -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier,
) {
    val value = composerState.input
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val attachments = remember(sessionKey, editingMessage?.id) {
        mutableStateListOf<SharedChatAttachment>().apply {
            addAll(editingMessage?.attachments.orEmpty())
        }
    }
    var menuOpen by remember(sessionKey) { mutableStateOf(false) }
    var followUpMenuOpen by remember(sessionKey) { mutableStateOf(false) }
    var textFieldFocused by remember(sessionKey) { mutableStateOf(false) }
    var measuredTextLineCount by remember(sessionKey) { mutableIntStateOf(1) }
    var measuredTextHeight by remember(sessionKey) { mutableStateOf(22.dp) }
    val density = LocalDensity.current
    val selectedSkills = availableSkills.filter { it.id in selectedSkillIds }
    val selectedMcpServers = mcpServers.filter { it.id in activeMcpServerIds }
    val hasComposerActionTray = selectedSkills.isNotEmpty() || selectedMcpServers.isNotEmpty() || chromeEnabled
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val composerPlaceholder = when {
        value.isNotBlank() -> ""
        attachments.isNotEmpty() -> stringResource(Res.string.chat_add_note)
        selectedSkills.size + selectedMcpServers.size == 1 ->
            selectedSkills.firstOrNull()?.sharedQuickActionLabel()
                ?: selectedMcpServers.firstOrNull()?.sharedQuickActionLabel()
                ?: stringResource(Res.string.chat_reply_to_aether)
        selectedSkills.isNotEmpty() || selectedMcpServers.isNotEmpty() ->
            stringResource(Res.string.chat_ask_with_selected_tools)
        else -> stringResource(Res.string.chat_ask_aether)
    }
    val showCompactSuggestion = compactSuggestionText.isNotBlank() &&
        attachments.isEmpty() && value.isNotBlank() &&
        SharedCompactCommand.startsWith(value.trim(), ignoreCase = true)
    val attachmentFailedMessage = stringResource(Res.string.chat_attach_file_failed)
    val hasDraft = value.isNotBlank() || attachments.isNotEmpty()
    val canSendDraft = attachments.all {
        it.workspaceState == SharedAttachmentWorkspaceState.Ready
    }
    val showPauseButton = isSending && !hasDraft
    val showSubmitButton = !isSending || hasDraft
    val keepPlusSeparated = value.isNotBlank() || hasComposerActionTray
    val plusSeparated = keepPlusSeparated || (textFieldFocused && imeVisible)
    val explicitTextLineCount = if (value.isBlank()) 1 else value.count { it == '\n' } + 1
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
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    val fieldTopPadding = if (hasComposerActionTray || isMultilineComposer) 12.dp else 8.dp
    val fieldBottomPadding = if (hasComposerActionTray || isMultilineComposer) 12.dp else 8.dp
    val composerHorizontalPadding by animateDpAsState(
        targetValue = when {
            plusSeparated -> 14.dp
            hasComposerActionTray -> 18.dp
            else -> 30.dp
        },
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_horizontal_padding",
    )
    val fieldStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 50.dp else 0.dp,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_field_start",
    )
    val fieldContentStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 18.dp else 52.dp,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_field_content_start",
    )
    val fieldMinHeight by animateDpAsState(
        targetValue = maxOf(
            if (plusSeparated) 56.dp else 50.dp,
            measuredTextHeight + fieldTopPadding + fieldBottomPadding,
        ),
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_field_min_height",
    )
    val plusShadowElevation by animateDpAsState(
        targetValue = if (plusSeparated) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_plus_shadow",
    )
    val bottomLift by animateDpAsState(
        targetValue = if (imeVisible) 12.dp else 18.dp,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260, easing = SharedConversationMotionEasing),
        label = "shared_composer_bottom_lift",
    )
    LaunchedEffect(plusSeparated) { onFocusChanged(plusSeparated) }

    fun pickAttachment(imagesOnly: Boolean) {
        menuOpen = false
        scope.launch {
            val pendingIds = mutableListOf<String>()
            runSharedAppCatching {
                val selectedFiles = platformServices.pickFiles(imagesOnly)
                if (selectedFiles.isEmpty()) return@runSharedAppCatching
                val attachmentsDirectory = "${runtime.workspaceRoot.trimEnd('/')}/attachments"
                val selectedWithIds = selectedFiles
                    .map { selected -> selected to selected.sharedSourceIdentifier() }
                    .distinctBy { (_, sourceIdentifier) -> sourceIdentifier }
                    .filterNot { (_, sourceIdentifier) ->
                        attachments.any { it.sourceIdentifier == sourceIdentifier }
                    }
                if (selectedWithIds.isEmpty()) return@runSharedAppCatching
                val pendingAttachments = selectedWithIds.map { (selected, sourceIdentifier) ->
                    val safeName = selected.name
                        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
                        .trim('-')
                        .ifBlank { "attachment" }
                    val path = "$attachmentsDirectory/${platformRandomUuid()}-$safeName"
                    selected to SharedChatAttachment(
                        id = platformRandomUuid(),
                        name = selected.name,
                        mimeType = selected.mimeType,
                        workspacePath = path,
                        sizeBytes = selected.bytes.size.toLong(),
                        workspaceState = SharedAttachmentWorkspaceState.Pending,
                        inlineBase64 = selected.bytes.takeIf {
                            selected.mimeType.startsWith("image/", ignoreCase = true) &&
                                it.size.toLong() <= SharedInlineImageAttachmentMaxBytes
                        }?.encodeBase64().orEmpty(),
                        sourceIdentifier = sourceIdentifier,
                        previewBytes = selected.bytes.takeIf {
                            selected.mimeType.startsWith("image/", ignoreCase = true)
                        },
                    )
                }
                pendingIds += pendingAttachments.map { it.second.id }
                attachments += pendingAttachments.map { it.second }
                runtime.fileSystem.createDirectories(attachmentsDirectory)
                pendingAttachments.forEach { (selected, pending) ->
                    launch {
                        val startedAtMillis = platformCurrentTimeMillis()
                        runSharedAppCatching {
                            runtime.fileSystem.writeWithProgress(
                                path = pending.workspacePath,
                                content = selected.bytes,
                            ) { bytesCopied ->
                                val index = attachments.indexOfFirst { it.id == pending.id }
                                if (index >= 0) {
                                    val elapsedMillis = (platformCurrentTimeMillis() - startedAtMillis).coerceAtLeast(1L)
                                    attachments[index] = attachments[index].copy(
                                        workspaceBytesCopied = bytesCopied,
                                        workspaceBytesPerSecond = bytesCopied * 1_000L / elapsedMillis,
                                    )
                                }
                            }
                        }.fold(
                            onSuccess = {
                                val index = attachments.indexOfFirst { it.id == pending.id }
                                if (index >= 0) {
                                    attachments[index] = attachments[index].copy(
                                        workspaceState = SharedAttachmentWorkspaceState.Ready,
                                        workspaceError = "",
                                        workspaceBytesCopied = selected.bytes.size.toLong(),
                                        workspaceBytesPerSecond = 0L,
                                        previewBytes = null,
                                    )
                                }
                            },
                            onFailure = { failure ->
                                val index = attachments.indexOfFirst { it.id == pending.id }
                                if (index >= 0) {
                                    val existing = attachments[index]
                                    attachments[index] = if (existing.inlineBase64.isNotBlank()) {
                                        existing.copy(
                                            workspacePath = "",
                                            workspaceState = SharedAttachmentWorkspaceState.Ready,
                                            workspaceError = "",
                                            workspaceBytesPerSecond = 0L,
                                            previewBytes = null,
                                        )
                                    } else {
                                        existing.copy(
                                            workspaceState = SharedAttachmentWorkspaceState.Failed,
                                            workspaceError = failure.message ?: attachmentFailedMessage,
                                            workspaceBytesPerSecond = 0L,
                                            previewBytes = null,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }.onFailure { failure ->
                pendingIds.forEach { attachmentId ->
                    val index = attachments.indexOfFirst { it.id == attachmentId }
                    if (index >= 0) {
                        val existing = attachments[index]
                        attachments[index] = if (existing.inlineBase64.isNotBlank()) {
                            existing.copy(
                                workspacePath = "",
                                workspaceState = SharedAttachmentWorkspaceState.Ready,
                                workspaceError = "",
                                workspaceBytesPerSecond = 0L,
                                previewBytes = null,
                            )
                        } else {
                            existing.copy(
                                workspaceState = SharedAttachmentWorkspaceState.Failed,
                                workspaceError = failure.message ?: attachmentFailedMessage,
                                workspaceBytesPerSecond = 0L,
                                previewBytes = null,
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
            .navigationBarsPadding()
            .padding(bottom = bottomLift),
    ) {
        Column(modifier = Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }) {
            SharedAetherExtensionSlot(
                SharedExtensionSlotChatComposerTop,
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = composerHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStarterPromptHint) {
                    SharedSurfaceNotice(
                        title = stringResource(Res.string.chat_first_prompt_ready_title),
                        subtitle = stringResource(Res.string.chat_first_prompt_ready_subtitle),
                        actionLabel = stringResource(Res.string.common_hide),
                        onAction = onDismissStarterPromptHint,
                    )
                }
                if (editingMessage != null) {
                    SharedSurfaceNotice(
                        title = stringResource(Res.string.chat_editing_earlier_message_title),
                        subtitle = stringResource(Res.string.chat_editing_earlier_message_subtitle),
                        actionLabel = stringResource(Res.string.common_cancel),
                        onAction = onCancelEdit,
                    )
                }
                AnimatedVisibility(
                    visible = showCompactSuggestion,
                    enter = fadeIn(tween(160, easing = SharedConversationMotionEasing)) +
                        slideInVertically(tween(220, easing = SharedConversationMotionEasing)) { it / 3 },
                    exit = fadeOut(tween(120, easing = SharedConversationMotionEasing)) +
                        slideOutVertically(tween(180, easing = SharedConversationMotionEasing)) { it / 3 },
                ) {
                    SharedCompactSuggestionRow(
                        text = compactSuggestionText,
                        onClick = { onValueChange(SharedCompactCommand) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (attachments.isNotEmpty()) {
                    SharedComposerAttachmentTray(
                        attachments = attachments,
                        runtime = runtime,
                        onRemoveAttachment = { attachmentId ->
                            attachments.removeAll { it.id == attachmentId }
                        },
                    )
                }
                val fieldShape = if (plusSeparated) ComposerFocusedShape else ComposerShape
                val fieldControlAlignment = if (isMultilineComposer) Alignment.Bottom else Alignment.CenterVertically
                val fieldTextAlignment = if (isMultilineComposer) Alignment.TopStart else Alignment.CenterStart
                val plusButtonAlignment = if (isMultilineComposer || hasComposerActionTray) {
                    Alignment.BottomStart
                } else {
                    Alignment.CenterStart
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(start = fieldStartPadding)) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .shadow(
                                    elevation = 10.dp,
                                    shape = fieldShape,
                                    ambientColor = ComposerShadow,
                                    spotColor = ComposerShadow,
                                )
                                .heightIn(min = fieldMinHeight)
                                .animateContentSize(tween(320, easing = SharedConversationMotionEasing))
                                .clip(fieldShape).background(AetherSurface)
                                .padding(
                                    start = fieldContentStartPadding,
                                    end = 8.dp,
                                    top = fieldTopPadding,
                                    bottom = fieldBottomPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(if (hasComposerActionTray) 10.dp else 0.dp),
                        ) {
                            AnimatedVisibility(
                                visible = hasComposerActionTray,
                                enter = fadeIn(tween(220, easing = SharedConversationMotionEasing)) +
                                    slideInVertically(tween(280, easing = SharedConversationMotionEasing)) { -it / 2 },
                                exit = fadeOut(tween(160, easing = SharedConversationMotionEasing)) +
                                    slideOutVertically(tween(220, easing = SharedConversationMotionEasing)) { -it / 3 },
                            ) {
                                SharedComposerActionTray(
                                    skills = selectedSkills,
                                    mcpServers = selectedMcpServers,
                                    chromeEnabled = chromeEnabled,
                                    onRemoveSkill = { onSkillSelected(it, false) },
                                    onRemoveMcpServer = { onMcpServerSelected(it, false) },
                                    onRemoveChrome = { onChromeSelected(false) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = fieldControlAlignment,
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f).heightIn(min = measuredTextHeight.coerceAtLeast(22.dp)),
                                    contentAlignment = fieldTextAlignment,
                                ) {
                                    if (value.isBlank()) {
                                        Text(
                                            composerPlaceholder,
                                            color = Color(0xFF8C8C8C),
                                            style = composerTextStyle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = value,
                                        onValueChange = onValueChange,
                                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                                            textFieldFocused = it.isFocused
                                        },
                                        textStyle = composerTextStyle,
                                        cursorBrush = SolidColor(AetherOnSurface),
                                        maxLines = 5,
                                        onTextLayout = { layout ->
                                            val lineCount = layout.lineCount.coerceIn(1, 5)
                                            if (measuredTextLineCount != lineCount) measuredTextLineCount = lineCount
                                            val textHeight = with(density) {
                                                (layout.getLineBottom(lineCount - 1) - layout.getLineTop(0)).toDp()
                                            }
                                            if (measuredTextHeight != textHeight) measuredTextHeight = textHeight
                                        },
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (showPauseButton) {
                                    SharedComposerPauseButton(onStop)
                                    Spacer(Modifier.width(6.dp))
                                }
                                if (showSubmitButton) {
                                    Box {
                                        SharedComposerSubmitButton(
                                            hasDraft = hasDraft,
                                            canSendDraft = canSendDraft,
                                            isSending = isSending,
                                            onClick = {
                                                if (!hasDraft || !canSendDraft) {
                                                    return@SharedComposerSubmitButton
                                                }
                                                if (isSending) {
                                                    followUpMenuOpen = true
                                                } else {
                                                    onSend(attachments.toList())
                                                    attachments.clear()
                                                    menuOpen = false
                                                }
                                            },
                                        )
                                        if (isSending) {
                                            SharedFollowUpMenu(
                                                visible = followUpMenuOpen,
                                                density = density,
                                                onDismiss = { followUpMenuOpen = false },
                                                onSteer = {
                                                    followUpMenuOpen = false
                                                    onSteerFollowUp(attachments.toList())
                                                    attachments.clear()
                                                },
                                                onQueue = {
                                                    followUpMenuOpen = false
                                                    onQueueFollowUp(attachments.toList())
                                                    attachments.clear()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.align(plusButtonAlignment)) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .shadow(
                                    plusShadowElevation,
                                    CircleShape,
                                    ambientColor = ControlShadow,
                                    spotColor = ControlShadow,
                                )
                                .clip(CircleShape)
                                .background(if (plusSeparated) AetherSurface else Color.Transparent)
                                .clickable { menuOpen = !menuOpen },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(Res.string.chat_add_attachment),
                                tint = AetherOnSurface,
                                modifier = Modifier.size(27.dp),
                            )
                        }
                        SharedComposerPlusMenu(
                            visible = menuOpen,
                            density = density,
                            chromeAvailable = chromeAvailable,
                            chromeEnabled = chromeEnabled,
                            availableSkills = availableSkills,
                            selectedSkillIds = selectedSkillIds,
                            mcpServers = mcpServers,
                            activeMcpServerIds = activeMcpServerIds,
                            onDismiss = { menuOpen = false },
                            onPickImages = { pickAttachment(true) },
                            onPickFiles = { pickAttachment(false) },
                            onChromeSelected = onChromeSelected,
                            onSkillSelected = onSkillSelected,
                            onMcpServerSelected = onMcpServerSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSurfaceNotice(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AetherOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AetherSurfaceHigh)
                .clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = AetherOnSurface, modifier = Modifier.size(14.dp))
            Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = AetherOnSurface)
        }
    }
}

@Composable
private fun SharedComposerPauseButton(onClick: () -> Unit) {
    val stopDescription = stringResource(Res.string.chat_stop_response)
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(ComposerPurple)
            .semantics { contentDescription = stopDescription }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.offset(x = 0.5.dp).size(11.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color.White),
        )
    }
}

@Composable
private fun SharedComposerSubmitButton(
    hasDraft: Boolean,
    canSendDraft: Boolean,
    isSending: Boolean,
    onClick: () -> Unit,
) {
    val enabled = hasDraft && canSendDraft
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(if (enabled) ComposerPurple else AetherSurfaceHigher)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = stringResource(
                if (isSending) Res.string.common_send_follow_up else Res.string.common_send,
            ),
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SharedFollowUpMenu(
    visible: Boolean,
    density: androidx.compose.ui.unit.Density,
    onDismiss: () -> Unit,
    onSteer: () -> Unit,
    onQueue: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val enterTransition = if (reduceMotion) {
        fadeIn(tween(80))
    } else {
        fadeIn(tween(160, easing = SharedConversationMotionEasing)) + scaleIn(
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(1f, 1f),
            animationSpec = tween(220, easing = SharedConversationMotionEasing),
        ) + slideInVertically(
            animationSpec = tween(240, easing = SharedConversationMotionEasing),
            initialOffsetY = { it / 10 },
        )
    }
    val exitTransition = if (reduceMotion) {
        fadeOut(tween(60))
    } else {
        fadeOut(tween(120, easing = SharedConversationMotionEasing)) + scaleOut(
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(1f, 1f),
            animationSpec = tween(160, easing = SharedConversationMotionEasing),
        ) + slideOutVertically(
            animationSpec = tween(180, easing = SharedConversationMotionEasing),
            targetOffsetY = { it / 12 },
        )
    }
    SharedAnimatedPopupHost(visible = visible) { visibility ->
        Popup(
            alignment = Alignment.BottomEnd,
            offset = IntOffset(0, -with(density) { 12.dp.roundToPx() }),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 252.dp, max = 284.dp)
                        .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                        .clip(RoundedCornerShape(30.dp)).background(AetherSurface)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SharedComposerPlusMenuRow(
                        title = stringResource(Res.string.branch_steer_current_run),
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = AetherOnSecondaryContainer,
                        iconContainerColor = AetherSecondaryContainer,
                        onClick = onSteer,
                    )
                    SharedComposerPlusMenuRow(
                        title = stringResource(Res.string.branch_queue_next_turn),
                        icon = Icons.Rounded.ArrowUpward,
                        iconTint = AetherOnPrimaryContainer,
                        iconContainerColor = AetherPrimaryContainer,
                        onClick = onQueue,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedComposerPlusMenu(
    visible: Boolean,
    density: androidx.compose.ui.unit.Density,
    chromeAvailable: Boolean,
    chromeEnabled: Boolean,
    availableSkills: List<SharedInstalledSkill>,
    selectedSkillIds: List<String>,
    mcpServers: List<SharedMcpServerConfig>,
    activeMcpServerIds: List<String>,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onChromeSelected: (Boolean) -> Unit,
    onSkillSelected: (String, Boolean) -> Unit,
    onMcpServerSelected: (String, Boolean) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val enterTransition = if (reduceMotion) {
        fadeIn(tween(80))
    } else {
        fadeIn(tween(160, easing = SharedConversationMotionEasing)) + scaleIn(
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(0f, 1f),
            animationSpec = tween(220, easing = SharedConversationMotionEasing),
        ) + slideInVertically(
            animationSpec = tween(240, easing = SharedConversationMotionEasing),
            initialOffsetY = { it / 10 },
        )
    }
    val exitTransition = if (reduceMotion) {
        fadeOut(tween(60))
    } else {
        fadeOut(tween(120, easing = SharedConversationMotionEasing)) + scaleOut(
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(0f, 1f),
            animationSpec = tween(160, easing = SharedConversationMotionEasing),
        ) + slideOutVertically(
            animationSpec = tween(180, easing = SharedConversationMotionEasing),
            targetOffsetY = { it / 12 },
        )
    }
    SharedAnimatedPopupHost(visible = visible) { visibility ->
        Popup(
            alignment = Alignment.BottomStart,
            offset = IntOffset(0, -with(density) { 42.dp.roundToPx() }),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                Box(
                    modifier = Modifier.widthIn(min = 284.dp, max = 304.dp)
                        .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                        .clip(RoundedCornerShape(30.dp)).background(AetherSurface),
                ) {
                    Column(
                        modifier = Modifier.heightIn(max = ComposerPlusMenuMaxHeight)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SharedComposerPlusMenuRow(
                            title = stringResource(Res.string.chat_photos),
                            icon = Icons.Rounded.Image,
                            iconTint = Color(0xFF4E8D5A),
                            onClick = onPickImages,
                        )
                        SharedComposerPlusMenuRow(
                            title = stringResource(Res.string.chat_files),
                            icon = Icons.Rounded.AttachFile,
                            iconTint = AetherOnSurface,
                            onClick = onPickFiles,
                        )
                        if (chromeAvailable || availableSkills.isNotEmpty() || mcpServers.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                        }
                        if (chromeAvailable) {
                            SharedComposerPlusMenuRow(
                                title = stringResource(Res.string.chrome_label),
                                icon = Icons.Rounded.Public,
                                iconTint = Color(0xFF2F6DA3),
                                selected = chromeEnabled,
                                onClick = {
                                    onDismiss()
                                    onChromeSelected(!chromeEnabled)
                                },
                            )
                        }
                        availableSkills.forEach { skill ->
                            val selected = skill.id in selectedSkillIds
                            SharedComposerPlusMenuRow(
                                title = skill.sharedQuickActionLabel(),
                                icon = Icons.Rounded.Extension,
                                iconTint = Color(0xFF9C6B2F),
                                selected = selected,
                                onClick = {
                                    onDismiss()
                                    onSkillSelected(skill.id, !selected)
                                },
                            )
                        }
                        mcpServers.forEach { server ->
                            val selected = server.id in activeMcpServerIds
                            val stdio = server.transport == SharedMcpTransport.Stdio
                            SharedComposerPlusMenuRow(
                                title = server.sharedQuickActionLabel(),
                                icon = if (stdio) Icons.Rounded.Terminal else Icons.Rounded.Cloud,
                                iconTint = if (stdio) Color(0xFF2F6DA3) else Color(0xFF2A9C9A),
                                selected = selected,
                                onClick = {
                                    onDismiss()
                                    onMcpServerSelected(server.id, !selected)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedComposerPlusMenuRow(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    iconContainerColor: Color = AetherSurfaceHigh,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(title, modifier = Modifier.weight(1f), color = AetherOnSurface, style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(AetherPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = AetherPrimary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

private fun SharedInstalledSkill.sharedQuickActionLabel(): String =
    actionLabel.ifBlank { generateSharedQuickActionLabel(name, description) }

private fun SharedMcpServerConfig.sharedQuickActionLabel(): String = actionLabel.ifBlank {
    generateSharedQuickActionLabel(
        name,
        if (transport == SharedMcpTransport.Stdio) command else url,
    )
}

@Composable
private fun SharedComposerActionTray(
    skills: List<SharedInstalledSkill>,
    mcpServers: List<SharedMcpServerConfig>,
    chromeEnabled: Boolean,
    onRemoveSkill: (String) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onRemoveChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (chromeEnabled) {
            SharedComposerActionChip(
                label = stringResource(Res.string.chrome_label),
                icon = Icons.Rounded.Public,
                onRemove = onRemoveChrome,
            )
        }
        skills.forEach { skill ->
            SharedComposerActionChip(
                label = skill.sharedQuickActionLabel(),
                icon = Icons.Rounded.Extension,
                onRemove = { onRemoveSkill(skill.id) },
            )
        }
        mcpServers.forEach { server ->
            SharedComposerActionChip(
                label = server.sharedQuickActionLabel(),
                icon = if (server.transport == SharedMcpTransport.Stdio) Icons.Rounded.Terminal else Icons.Rounded.Cloud,
                onRemove = { onRemoveMcpServer(server.id) },
            )
        }
    }
}

@Composable
private fun SharedComposerActionChip(
    label: String,
    icon: ImageVector,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(18.dp))
            .background(AetherPrimaryContainer).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = AetherPrimary, modifier = Modifier.size(16.dp))
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier.size(18.dp).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.common_remove),
                tint = AetherPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private inline fun androidx.compose.runtime.snapshots.SnapshotStateList<SharedChatMessage>.updateMessage(
    id: String,
    transform: (SharedChatMessage) -> SharedChatMessage,
) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index])
}

internal fun SharedChatMessage.appendAssistantTextDelta(delta: String): SharedChatMessage {
    if (delta.isEmpty()) return this
    val updatedBlocks = if (responseBlocks.lastOrNull() is SharedAssistantResponseBlock.Text) {
        responseBlocks.dropLast(1) +
            (responseBlocks.last() as SharedAssistantResponseBlock.Text).let { block ->
                block.copy(text = block.text + delta)
            }
    } else {
        responseBlocks + SharedAssistantResponseBlock.Text(platformRandomUuid(), delta)
    }
    return copy(text = text + delta, responseBlocks = updatedBlocks)
}

internal fun SharedChatMessage.appendAssistantReasoningDelta(
    delta: String,
    nowMillis: Long = platformCurrentTimeMillis(),
): SharedChatMessage {
    if (delta.isEmpty()) return this
    val lastReasoning = responseBlocks.lastOrNull() as? SharedAssistantResponseBlock.Reasoning
    val updatedBlocks = if (lastReasoning != null && lastReasoning.trace.completedAtMillis == null) {
        responseBlocks.dropLast(1) +
            lastReasoning.let { block ->
                block.copy(trace = block.trace.copy(rawText = block.trace.rawText + delta))
            }
    } else {
        val blockId = platformRandomUuid()
        responseBlocks + SharedAssistantResponseBlock.Reasoning(
            id = blockId,
            trace = SharedReasoningTrace(
                id = blockId,
                rawText = delta,
                startedAtMillis = nowMillis,
            ),
        )
    }
    return copy(reasoningText = reasoningText + delta, responseBlocks = updatedBlocks)
}

internal fun SharedChatMessage.appendDirectAssistantReasoningSummaryDelta(
    delta: String,
    tracker: SharedReasoningTurnTracker,
    nowMillis: Long = platformCurrentTimeMillis(),
): SharedChatMessage {
    if (delta.isEmpty()) return this
    val activeReasoning = (responseBlocks.lastOrNull() as? SharedAssistantResponseBlock.Reasoning)
        ?.takeIf { it.trace.completedAtMillis == null }
    val block = activeReasoning ?: run {
        val blockId = platformRandomUuid()
        SharedAssistantResponseBlock.Reasoning(
            id = blockId,
            trace = SharedReasoningTrace(id = blockId, startedAtMillis = nowMillis),
        )
    }
    val chunkId = tracker.directSummaryChunkId(block.id)
    val existingChunk = block.trace.chunks.firstOrNull { it.id == chunkId }
    val updatedDetail = existingChunk?.detail.orEmpty() + delta
    val updatedChunks = if (existingChunk == null) {
        block.trace.chunks + SharedReasoningSummaryChunk(
            id = chunkId,
            title = "Reasoning",
            detail = updatedDetail,
            createdAtMillis = nowMillis,
            timelineOrder = tracker.nextTimelineOrder(),
        )
    } else {
        block.trace.chunks.map { chunk ->
            if (chunk.id == chunkId) chunk.copy(detail = updatedDetail, isPending = false) else chunk
        }
    }
    val updatedBlock = block.copy(
        trace = block.trace.copy(
            chunks = updatedChunks,
            latestStatusText = updatedDetail,
        ),
    )
    val updatedBlocks = if (activeReasoning == null) {
        responseBlocks + updatedBlock
    } else {
        responseBlocks.dropLast(1) + updatedBlock
    }
    return copy(responseBlocks = updatedBlocks)
}

internal fun SharedChatMessage.activeSharedReasoningTrace(): SharedReasoningTrace? =
    (responseBlocks.lastOrNull() as? SharedAssistantResponseBlock.Reasoning)
        ?.trace
        ?.takeIf { it.completedAtMillis == null }

internal fun SharedChatMessage.withPendingReasoningSummary(
    submission: SharedReasoningSummarySubmission,
): SharedChatMessage = copy(
    responseBlocks = responseBlocks.map { block ->
        if (block is SharedAssistantResponseBlock.Reasoning && block.id == submission.blockId) {
            block.copy(trace = block.trace.copy(chunks = block.trace.chunks + submission.chunk))
        } else {
            block
        }
    },
)

internal fun SharedChatMessage.withCompletedReasoningSummary(
    blockId: String,
    chunkId: String,
    title: String,
    detail: String,
): SharedChatMessage = copy(
    responseBlocks = responseBlocks.map { block ->
        if (block is SharedAssistantResponseBlock.Reasoning && block.id == blockId) {
            block.copy(
                trace = block.trace.copy(
                    chunks = block.trace.chunks.map { chunk ->
                        if (chunk.id == chunkId) {
                            chunk.copy(title = title, detail = detail, isPending = false)
                        } else {
                            chunk
                        }
                    },
                    latestStatusText = detail.ifBlank { title },
                )
            )
        } else {
            block
        }
    },
)

internal fun SharedChatMessage.completeAssistantReasoning(
    completedAtMillis: Long = platformCurrentTimeMillis(),
): SharedChatMessage = copy(
    responseBlocks = responseBlocks.map { block ->
        if (block is SharedAssistantResponseBlock.Reasoning && block.trace.completedAtMillis == null) {
            block.copy(trace = block.trace.copy(completedAtMillis = completedAtMillis))
        } else {
            block
        }
    },
)

internal fun SharedChatMessage.withStartedAssistantTool(
    call: com.zhousl.aether.data.pi.SharedPiHostToolCall,
    startedAtMillis: Long = platformCurrentTimeMillis(),
    startedAtUptimeMillis: Long = platformUptimeMillis(),
    timelineOrder: Long = startedAtMillis,
): SharedChatMessage {
    val tool = SharedChatToolInvocation(
        id = call.id,
        name = call.name,
        summary = call.arguments.toolSummary(),
        argumentsJson = call.arguments.toString(),
        startedAtUptimeMillis = startedAtUptimeMillis,
        startedAtMillis = startedAtMillis,
        timelineOrder = timelineOrder,
    )
    val activeReasoning = responseBlocks.lastOrNull() as? SharedAssistantResponseBlock.Reasoning
    val updatedBlocks = if (activeReasoning != null && activeReasoning.trace.completedAtMillis == null) {
        responseBlocks.dropLast(1) + activeReasoning.copy(
            trace = activeReasoning.trace.copy(
                toolInvocations = activeReasoning.trace.toolInvocations + tool,
            ),
        )
    } else if (responseBlocks.lastOrNull() is SharedAssistantResponseBlock.ToolGroup) {
        responseBlocks.dropLast(1) +
            (responseBlocks.last() as SharedAssistantResponseBlock.ToolGroup).let { block ->
                block.copy(tools = block.tools + tool)
            }
    } else {
        responseBlocks + SharedAssistantResponseBlock.ToolGroup(platformRandomUuid(), listOf(tool))
    }
    return copy(tools = tools + tool, responseBlocks = updatedBlocks)
}

internal fun SharedChatMessage.withFinishedAssistantTool(
    toolId: String,
    result: SharedHostToolResult,
    completedAtMillis: Long = platformCurrentTimeMillis(),
    completedAtUptimeMillis: Long = platformUptimeMillis(),
): SharedChatMessage {
    fun SharedChatToolInvocation.complete(): SharedChatToolInvocation =
        if (id == toolId) {
            copy(
                output = result.outputJson.toolOutputSummary(),
                outputJson = result.outputJson,
                isRunning = false,
                isError = result.isError,
                completedAtUptimeMillis = completedAtUptimeMillis,
                completedAtMillis = completedAtMillis,
            )
        } else {
            this
        }
    return copy(
        tools = tools.map(SharedChatToolInvocation::complete),
        responseBlocks = responseBlocks.map { block ->
            when (block) {
                is SharedAssistantResponseBlock.ToolGroup ->
                    block.copy(tools = block.tools.map(SharedChatToolInvocation::complete))
                is SharedAssistantResponseBlock.Reasoning -> block.copy(
                    trace = block.trace.copy(
                        toolInvocations = block.trace.toolInvocations.map(SharedChatToolInvocation::complete),
                    ),
                )
                else -> block
            }
        },
    )
}

private fun SharedChatMessage.withAssistantResultFallback(
    result: com.zhousl.aether.data.pi.SharedPiTurnResult,
): SharedChatMessage {
    return if (reasoningText.isBlank() && result.reasoningText.isNotBlank()) {
        appendAssistantReasoningDelta(result.reasoningText)
    } else {
        this
    }
}

internal fun SharedChatMessage.withAssistantTextResultFallback(
    result: com.zhousl.aether.data.pi.SharedPiTurnResult,
): SharedChatMessage {
    val finalText = result.assistantText.ifBlank {
        "The model finished without returning any assistant text."
    }
    return if (text.isBlank()) appendAssistantTextDelta(finalText) else this
}

internal fun SharedChatMessage.withSharedSteerInstruction(): SharedChatMessage = copy(
    text = buildString {
        append(
            "The user sent this while you were already working. Treat it as supplemental context for the current task. " +
                "Continue the ongoing work, do not restart just to acknowledge it, and only change course if the new note requires it."
        )
        if (this@withSharedSteerInstruction.text.isNotBlank()) {
            append("\n\nSupplemental user note:\n")
            append(this@withSharedSteerInstruction.text)
        } else if (attachments.isNotEmpty()) {
            append("\n\nThe user also attached additional files for the current task.")
        }
    },
)

internal fun SharedChatMessage.withSharedRequestFailure(
    message: String,
    template: String = "Request failed: {request_error}",
    placeholder: String = "{request_error}",
    unknownError: String = "Unknown error",
): SharedChatMessage {
    val detail = message.trim().ifBlank { unknownError }
    val failureText = template.replace(placeholder, detail)
    val joinsExistingTextBlock = responseBlocks.lastOrNull() is SharedAssistantResponseBlock.Text
    val updated = appendAssistantTextDelta(if (joinsExistingTextBlock) "\n\n$failureText" else failureText)
    return if (!joinsExistingTextBlock && text.isNotBlank()) {
        updated.copy(text = "$text\n\n$failureText")
    } else {
        updated
    }
}

private fun sharedFailureMessage(error: Throwable): String =
    error.message?.trim().orEmpty().ifBlank { "Unknown error" }

internal fun SharedChatMessage.finalizeSharedInterruptedAssistantWork(
    status: String,
    fallbackText: String = "",
    isErrorWhenBlank: Boolean = false,
    completedAtMillis: Long = platformCurrentTimeMillis(),
): SharedChatMessage {
    val hadNoText = text.isBlank()
    val completedAtUptimeMillis = platformUptimeMillis()
    fun SharedChatToolInvocation.finalizeInterrupted(): SharedChatToolInvocation =
        if (isSharedInterruptedToolInvocation()) {
            val interruptedOutput = sharedInterruptedToolOutput(outputJson)
            copy(
                isRunning = false,
                isError = true,
                output = interruptedOutput.toolOutputSummary(),
                outputJson = interruptedOutput,
                completedAtUptimeMillis = completedAtUptimeMillis,
                completedAtMillis = completedAtMillis,
            )
        } else {
            this
        }
    return copy(
        text = text.ifBlank { fallbackText },
        isError = isError || (isErrorWhenBlank && hadNoText),
        isStreaming = false,
        status = status,
        statusDetail = "",
        completedAtMillis = completedAtMillis,
        thoughtDurationMillis = if (
            thoughtDurationMillis <= 0L &&
            responseBlocks.none { it is SharedAssistantResponseBlock.Reasoning } &&
            createdAtMillis > 0L
        ) {
            (completedAtMillis - createdAtMillis).coerceAtLeast(0L)
        } else {
            thoughtDurationMillis
        },
        tools = tools.map { it.finalizeInterrupted() },
        responseBlocks = responseBlocks.map { block ->
            when (block) {
                is SharedAssistantResponseBlock.ToolGroup -> block.copy(
                    tools = block.tools.map { it.finalizeInterrupted() },
                )
                is SharedAssistantResponseBlock.Reasoning -> block.copy(
                    trace = block.trace.copy(
                        completedAtMillis = block.trace.completedAtMillis ?: completedAtMillis,
                        toolInvocations = block.trace.toolInvocations.map { it.finalizeInterrupted() },
                    ),
                )
                else -> block
            }
        },
    )
}

private fun SharedChatToolInvocation.isSharedInterruptedToolInvocation(): Boolean {
    if (isRunning) return true
    if (!name.equals("bash", ignoreCase = true)) return false
    val output = runCatching { Json.parseToJsonElement(outputJson) as? JsonObject }.getOrNull() ?: return false
    val status = output["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return status == "running" || status == "launching"
}

internal fun SharedChatMessage.hasSharedVisibleAssistantWork(): Boolean =
    text.isNotBlank() || responseBlocks.any { block ->
        when (block) {
            is SharedAssistantResponseBlock.Text -> block.text.isNotBlank()
            is SharedAssistantResponseBlock.Reasoning -> true
            is SharedAssistantResponseBlock.ToolGroup -> block.tools.isNotEmpty()
        }
    } || tools.isNotEmpty()

internal fun sharedInterruptedToolOutput(rawOutput: String): String {
    val existing = runCatching { Json.parseToJsonElement(rawOutput) as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
    return buildJsonObject {
        existing.forEach { (key, value) -> put(key, value) }
        put("ok", false)
        put("status", "cancelled")
        put("running", false)
        put("completed", true)
        if ("stdout" !in existing) put("stdout", "")
        if ("stderr" !in existing) put("stderr", "")
        if ("exit_code" !in existing) put("exit_code", 143)
        if ("err" !in existing) put("err", -1)
        put("errmsg", "Stopped by user.")
    }.toString()
}

internal fun SharedChatMessage.interruptedByBackgroundExpiration(
    status: String = "Interrupted",
    fallbackText: String = "This response was interrupted when iOS background time expired. Retry the message to continue.",
): SharedChatMessage =
    finalizeSharedInterruptedAssistantWork(
        status = status,
        fallbackText = fallbackText,
        isErrorWhenBlank = true,
    )

internal fun List<SharedChatMessage>.toPersistedMessages(): List<PersistedChatMessage> =
    syncSharedUserBranches()
        .filterNot(SharedChatMessage::isStreaming)
        .map(SharedChatMessage::toPersistedMessage)

private fun SharedChatMessage.toPersistedMessage(): PersistedChatMessage =
    PersistedChatMessage(
        id = id,
        text = text,
        fromUser = fromUser,
        isError = isError,
        reasoningText = reasoningText,
        tools = tools.map { tool ->
            PersistedChatTool(
                id = tool.id,
                name = tool.name,
                summary = tool.summary,
                output = tool.output,
                argumentsJson = tool.argumentsJson,
                outputJson = tool.outputJson,
                isRunning = tool.isRunning,
                isError = tool.isError,
                startedAtUptimeMillis = tool.startedAtUptimeMillis,
                completedAtUptimeMillis = tool.completedAtUptimeMillis,
                startedAtMillis = tool.startedAtMillis,
                completedAtMillis = tool.completedAtMillis,
                timelineOrder = tool.timelineOrder,
            )
        },
        responseBlocks = responseBlocks.map { block ->
            when (block) {
                is SharedAssistantResponseBlock.Text -> PersistedAssistantResponseBlock(
                    id = block.id,
                    type = PersistedAssistantResponseBlockType.Text,
                    text = block.text,
                )
                is SharedAssistantResponseBlock.Reasoning -> PersistedAssistantResponseBlock(
                    id = block.id,
                    type = PersistedAssistantResponseBlockType.Reasoning,
                    text = block.trace.rawText,
                    reasoningTrace = block.trace.toPersistedReasoningTrace(),
                )
                is SharedAssistantResponseBlock.ToolGroup -> PersistedAssistantResponseBlock(
                    id = block.id,
                    type = PersistedAssistantResponseBlockType.ToolGroup,
                    tools = block.tools.map(SharedChatToolInvocation::toPersistedChatTool),
                )
            }
        },
        attachments = attachments.map { attachment ->
            PersistedChatAttachment(
                id = attachment.id,
                name = attachment.name,
                mimeType = attachment.mimeType,
                workspacePath = attachment.workspacePath,
                sizeBytes = attachment.sizeBytes,
                inlineBase64 = attachment.inlineBase64,
                sourceIdentifier = attachment.sourceIdentifier,
            )
        },
        usage = usage?.let { usage ->
            PersistedChatUsage(
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                totalTokens = usage.totalTokens,
                reasoningTokens = usage.reasoningTokens,
                cachedInputTokens = usage.cachedInputTokens,
                inputTokensAvailable = usage.inputTokensAvailable,
                outputTokensAvailable = usage.outputTokensAvailable,
                totalTokensAvailable = usage.totalTokensAvailable,
                reasoningTokensAvailable = usage.reasoningTokensAvailable,
                cachedInputTokensAvailable = usage.cachedInputTokensAvailable,
                requestCount = usage.requestCount,
            )
        },
        responseGroupId = responseGroupId,
        isActiveBranch = isActiveBranch,
        branchIndex = branchIndex,
        createdAtMillis = createdAtMillis,
        completedAtMillis = completedAtMillis,
        providerId = providerId,
        modelId = modelId,
        providerPayloadJson = providerPayloadJson,
        thoughtDurationMillis = thoughtDurationMillis,
        responseDurationMillis = responseDurationMillis,
        firstTokenLatencyMillis = firstTokenLatencyMillis,
        tokenUsageSource = tokenUsageSource,
        assistantActionsHidden = assistantActionsHidden,
        displayKind = when (displayKind) {
            SharedMessageDisplayKind.Standard -> PersistedMessageDisplayKind.Standard
            SharedMessageDisplayKind.HiddenContext -> PersistedMessageDisplayKind.HiddenContext
            SharedMessageDisplayKind.CompactStatus -> PersistedMessageDisplayKind.CompactStatus
        },
        userBranches = userBranches.map { branch ->
            branch.map(SharedChatMessage::toPersistedMessage)
        },
        selectedUserBranchIndex = selectedUserBranchIndex,
    )

private fun SharedChatToolInvocation.toPersistedChatTool(): PersistedChatTool = PersistedChatTool(
    id = id,
    name = name,
    summary = summary,
    output = output,
    argumentsJson = argumentsJson,
    outputJson = outputJson,
    isRunning = isRunning,
    isError = isError,
    startedAtUptimeMillis = startedAtUptimeMillis,
    completedAtUptimeMillis = completedAtUptimeMillis,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis,
    timelineOrder = timelineOrder,
)

private fun SharedReasoningTrace.toPersistedReasoningTrace(): PersistedReasoningTrace =
    PersistedReasoningTrace(
        id = id,
        rawText = rawText,
        chunks = chunks.map { chunk ->
            PersistedReasoningSummaryChunk(
                id = chunk.id,
                title = chunk.title,
                detail = chunk.detail,
                rawText = chunk.rawText,
                isPending = chunk.isPending,
                createdAtMillis = chunk.createdAtMillis,
                timelineOrder = chunk.timelineOrder,
            )
        },
        toolInvocations = toolInvocations.map(SharedChatToolInvocation::toPersistedChatTool),
        latestStatusText = latestStatusText,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
    )

private fun PersistedChatTool.toSharedChatToolInvocation(): SharedChatToolInvocation =
    SharedChatToolInvocation(
        id = id,
        name = name,
        summary = summary,
        output = output,
        argumentsJson = argumentsJson,
        outputJson = outputJson,
        isRunning = isRunning,
        isError = isError,
        startedAtUptimeMillis = startedAtUptimeMillis,
        completedAtUptimeMillis = completedAtUptimeMillis,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        timelineOrder = timelineOrder,
    )

private fun PersistedReasoningTrace.toSharedReasoningTrace(): SharedReasoningTrace = SharedReasoningTrace(
    id = id,
    rawText = rawText,
    chunks = chunks.map { chunk ->
        SharedReasoningSummaryChunk(
            id = chunk.id,
            title = chunk.title,
            detail = chunk.detail,
            rawText = chunk.rawText,
            isPending = chunk.isPending,
            createdAtMillis = chunk.createdAtMillis,
            timelineOrder = chunk.timelineOrder,
        )
    },
    toolInvocations = toolInvocations.map(PersistedChatTool::toSharedChatToolInvocation),
    latestStatusText = latestStatusText,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis,
)

internal fun PersistedChatMessage.toSharedChatMessage(): SharedChatMessage {
    val restoredBlocks = responseBlocks.map { block ->
        when (block.type) {
            PersistedAssistantResponseBlockType.Text -> SharedAssistantResponseBlock.Text(
                id = block.id,
                text = block.text,
            )
            PersistedAssistantResponseBlockType.Reasoning -> SharedAssistantResponseBlock.Reasoning(
                id = block.id,
                trace = block.reasoningTrace?.toSharedReasoningTrace() ?: SharedReasoningTrace(
                    id = block.id,
                    rawText = block.text,
                    startedAtMillis = createdAtMillis,
                    completedAtMillis = createdAtMillis + thoughtDurationMillis,
                ),
            )
            PersistedAssistantResponseBlockType.ToolGroup -> SharedAssistantResponseBlock.ToolGroup(
                id = block.id,
                tools = block.tools.map(PersistedChatTool::toSharedChatToolInvocation),
            )
        }
    }.ifEmpty {
        buildList {
            reasoningText.takeIf(String::isNotBlank)?.let {
                add(
                    SharedAssistantResponseBlock.Reasoning(
                        id = "$id-reasoning",
                        trace = SharedReasoningTrace(
                            id = "$id-reasoning",
                            rawText = it,
                            startedAtMillis = createdAtMillis,
                            completedAtMillis = createdAtMillis + thoughtDurationMillis,
                        ),
                    )
                )
            }
            if (tools.isNotEmpty()) {
                add(
                    SharedAssistantResponseBlock.ToolGroup(
                        "$id-tools",
                        tools.map(PersistedChatTool::toSharedChatToolInvocation),
                    ),
                )
            }
            text.takeIf(String::isNotBlank)?.let {
                add(SharedAssistantResponseBlock.Text("$id-text", it))
            }
        }
    }
    return SharedChatMessage(
    id = id,
    text = text,
    fromUser = fromUser,
    isError = isError,
    reasoningText = reasoningText,
    tools = tools.map(PersistedChatTool::toSharedChatToolInvocation),
    responseBlocks = restoredBlocks,
    attachments = attachments.map { attachment ->
        SharedChatAttachment(
            id = attachment.id,
            name = attachment.name,
            mimeType = attachment.mimeType,
            workspacePath = attachment.workspacePath,
            sizeBytes = attachment.sizeBytes,
            workspaceState = if (
                attachment.workspacePath.isBlank() && attachment.inlineBase64.isBlank()
            ) {
                SharedAttachmentWorkspaceState.Failed
            } else {
                SharedAttachmentWorkspaceState.Ready
            },
            workspaceError = if (
                attachment.workspacePath.isBlank() && attachment.inlineBase64.isBlank()
            ) {
                "This attachment is missing its workspace copy."
            } else {
                ""
            },
            inlineBase64 = attachment.inlineBase64,
            sourceIdentifier = attachment.sourceIdentifier,
        )
    },
    usage = usage?.let { usage ->
        SharedPiUsage(
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            totalTokens = usage.totalTokens,
            reasoningTokens = usage.reasoningTokens,
            cachedInputTokens = usage.cachedInputTokens,
            inputTokensAvailable = usage.inputTokensAvailable,
            outputTokensAvailable = usage.outputTokensAvailable,
            totalTokensAvailable = usage.totalTokensAvailable,
            reasoningTokensAvailable = usage.reasoningTokensAvailable,
            cachedInputTokensAvailable = usage.cachedInputTokensAvailable,
            requestCount = usage.requestCount,
        )
    },
    responseGroupId = responseGroupId,
    isActiveBranch = isActiveBranch,
    branchIndex = branchIndex,
    createdAtMillis = createdAtMillis,
    completedAtMillis = completedAtMillis,
    providerId = providerId,
    modelId = modelId,
    providerPayloadJson = providerPayloadJson,
    thoughtDurationMillis = thoughtDurationMillis,
    responseDurationMillis = responseDurationMillis,
    firstTokenLatencyMillis = firstTokenLatencyMillis,
        tokenUsageSource = tokenUsageSource,
        assistantActionsHidden = assistantActionsHidden,
    displayKind = when (displayKind) {
        PersistedMessageDisplayKind.Standard -> SharedMessageDisplayKind.Standard
        PersistedMessageDisplayKind.HiddenContext -> SharedMessageDisplayKind.HiddenContext
        PersistedMessageDisplayKind.CompactStatus -> SharedMessageDisplayKind.CompactStatus
    },
    userBranches = userBranches.map { branch -> branch.map(PersistedChatMessage::toSharedChatMessage) },
    selectedUserBranchIndex = selectedUserBranchIndex,
    )
}

@Composable
private fun SharedConversationDrawer(
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(322.dp),
        drawerContainerColor = AetherSurface,
        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                )
                HeaderCircleButton(
                    icon = LucideIcons.SquarePen,
                    contentDescription = stringResource(Res.string.new_chat),
                    onClick = onNewChat,
                    size = 38.dp,
                    iconSize = 18.dp,
                    containerColor = AetherSurfaceHigh,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(CircleShape).background(AetherSurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LucideIcons.Search, null, tint = AetherOnSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.search_chats), color = AetherOnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(Res.string.today_label), style = MaterialTheme.typography.labelMedium, color = AetherOnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.new_chat),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AetherSurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                color = AetherOnSurface,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LucideIcons.Settings, stringResource(Res.string.settings_title), tint = AetherOnSurface, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.settings_title), color = AetherOnSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SharedSettingsScreen(
    capabilities: PlatformCapabilities,
    runtime: MultiplatformLocalRuntime,
    platformServices: PlatformServices,
    providerConfigs: List<LlmProviderConfig>,
    appSettings: AppSettings,
    loadStatistics: suspend () -> com.zhousl.aether.data.SharedUsageStatisticsReport,
    bridgeClient: SharedPiBridgeClient,
    extensionManager: SharedAetherExtensionManager,
    extensionStateStore: SharedExtensionStateStore,
    onExtensionSnapshotChanged: (SharedAetherExtensionSnapshot) -> Unit,
    skillManager: SharedSkillManager,
    installedSkills: List<SharedInstalledSkill>,
    extensionCount: Int,
    onSkillsChanged: (List<SharedInstalledSkill>) -> Unit,
    mcpManager: SharedMcpManager,
    mcpServers: List<SharedMcpServerConfig>,
    activeMcpServerIds: Set<String>,
    onMcpServersChanged: (List<SharedMcpServerConfig>) -> Unit,
    chromeManager: SharedChromeManager,
    onProviderSaved: (LlmProviderConfig) -> Unit,
    onProviderEnabledChanged: (String, Boolean) -> Unit,
    onProviderRemoved: (String) -> Unit,
    onGeneralSettingsSaved: (AppSettings) -> Unit,
    onAlpineResetSettingsSaved: suspend (AppSettings) -> Unit,
    onImportAppData: suspend (String) -> SharedAppDataRestoreResult,
    onExportAppData: suspend (AppSettings) -> String,
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onReplayFollowUpOnboarding: () -> Unit,
    onReplayAlpineSetupPreview: () -> Unit,
    onExportLogs: suspend () -> String,
    onTransientMessage: (String) -> Unit,
    dismissRequestToken: Int = 0,
) {
    val terminalTitle = stringResource(Res.string.terminal_title)
    val terminalSubtitle = stringResource(Res.string.terminal_subtitle)
    val alpineTitle = stringResource(Res.string.alpine_title)
    val alpineSubtitle = stringResource(Res.string.alpine_subtitle)
    var destination by rememberSaveable(stateSaver = SharedSettingsDestinationSaver) {
        mutableStateOf<SettingsDestination?>(null)
    }
    var statisticsReport by remember {
        mutableStateOf<com.zhousl.aether.data.SharedUsageStatisticsReport?>(null)
    }
    var alpineReady by remember(appSettings.alpineSetupCompleted) {
        mutableStateOf(appSettings.alpineSetupCompleted)
    }
    var pendingSettings by remember { mutableStateOf(appSettings) }

    fun updatePendingSettings(updated: AppSettings) {
        pendingSettings = updated
    }

    fun commitPendingSettings() {
        val updated = appSettings.withSharedSettingsDraft(pendingSettings)
        pendingSettings = updated
        if (updated != appSettings) onGeneralSettingsSaved(updated)
    }

    fun persistAndExit() {
        commitPendingSettings()
        onBack()
    }

    fun persistAndReplayOnboarding() {
        commitPendingSettings()
        onReplayOnboarding()
    }

    fun persistAndReplayFollowUpOnboarding() {
        commitPendingSettings()
        onReplayFollowUpOnboarding()
    }

    val dismissGuard = remember { SharedSettingsDismissGuard() }
    var handledDismissRequestToken by remember { mutableIntStateOf(dismissRequestToken) }
    LaunchedEffect(dismissRequestToken) {
        if (dismissRequestToken != handledDismissRequestToken) {
            handledDismissRequestToken = dismissRequestToken
            if (dismissGuard.hasUnsavedChanges) dismissGuard.rejectDismiss()
            else persistAndExit()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(SharedScreenTransitionDuration.toLong())
        runSharedAppCatching { loadStatistics() }.onSuccess { statisticsReport = it }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(SharedScreenTransitionDuration.toLong())
        runSharedAppCatching {
            withContext(Dispatchers.Default) { runtime.isReady() }
        }.onSuccess { alpineReady = it }
    }
    val detailContent: @Composable (SettingsDestination) -> Unit = { selected ->
        when (selected.kind) {
            SharedSettingsKind.General -> SharedGeneralSettingsDetail(
                settings = appSettings,
                onSave = { updated ->
                    onGeneralSettingsSaved(
                        appSettings.copy(
                            language = updated.language,
                            themeMode = updated.themeMode,
                        ),
                    )
                },
                onBack = { destination = null },
            )
            SharedSettingsKind.Providers -> SharedProviderSettingsDetail(
                providerConfigs = providerConfigs,
                appSettings = pendingSettings,
                bridgeClient = bridgeClient,
                onUpsertProvider = onProviderSaved,
                onSetProviderEnabled = onProviderEnabledChanged,
                onRemoveProvider = onProviderRemoved,
                onSettingsSaved = ::updatePendingSettings,
                onTransientMessage = onTransientMessage,
                onBack = { destination = null },
            )
            SharedSettingsKind.Personalization -> SharedPersonalizationSettingsDetail(
                settings = pendingSettings,
                onSave = ::updatePendingSettings,
                onBack = { destination = null },
            )
            SharedSettingsKind.WebTools -> SharedWebToolsSettingsDetail(
                settings = pendingSettings,
                onSave = ::updatePendingSettings,
                onBack = { destination = null },
            )
            SharedSettingsKind.Reliability -> SharedReliabilitySettingsDetail(
                settings = pendingSettings,
                capabilities = capabilities,
                onSave = ::updatePendingSettings,
                onBack = { destination = null },
            )
            SharedSettingsKind.Skills -> SharedSkillsSettingsDetail(
                skillManager = skillManager,
                runtime = runtime,
                platformServices = platformServices,
                installedSkills = installedSkills,
                onSkillsChanged = onSkillsChanged,
                onTransientMessage = onTransientMessage,
                onBack = { destination = null },
            )
            SharedSettingsKind.Mcp -> SharedMcpSettingsDetail(
                manager = mcpManager,
                servers = mcpServers,
                activeServerIds = activeMcpServerIds,
                onServersChanged = onMcpServersChanged,
                onBack = { destination = null },
            )
            SharedSettingsKind.Alpine -> SharedAlpineSettingsDetail(
                runtime = runtime,
                settings = appSettings,
                onSettingsSaved = onGeneralSettingsSaved,
                onResetSettingsSaved = onAlpineResetSettingsSaved,
                onTransientMessage = onTransientMessage,
                onOpenTerminal = {
                    destination = SettingsDestination(
                        title = terminalTitle,
                        subtitle = terminalSubtitle,
                        kind = SharedSettingsKind.Terminal,
                    )
                },
                onBack = { destination = null },
            )
            SharedSettingsKind.Extensions -> SharedExtensionsSettingsDetail(
                bridgeClient = bridgeClient,
                extensionManager = extensionManager,
                extensionStateStore = extensionStateStore,
                runtime = runtime,
                platformServices = platformServices,
                onSnapshotChanged = onExtensionSnapshotChanged,
                onTransientMessage = onTransientMessage,
                onBack = { destination = null },
            )
            SharedSettingsKind.Terminal -> SharedTerminalScreen(
                runtime = runtime,
                onBack = {
                    destination = SettingsDestination(
                        title = alpineTitle,
                        subtitle = alpineSubtitle,
                        kind = SharedSettingsKind.Alpine,
                    )
                },
            )
            SharedSettingsKind.Chrome -> SharedChromeScreen(
                manager = chromeManager,
                onBack = {
                    destination = SettingsDestination(
                        title = alpineTitle,
                        subtitle = alpineSubtitle,
                        kind = SharedSettingsKind.Alpine,
                    )
                },
            )
            SharedSettingsKind.Statistics -> SharedStatisticsSettingsDetail(
                report = statisticsReport
                    ?: com.zhousl.aether.data.SharedUsageStatisticsReport(),
                onBack = { destination = null },
            )
            SharedSettingsKind.Developer -> SharedDeveloperSettingsDetail(
                settings = pendingSettings,
                platformServices = platformServices,
                onSave = ::updatePendingSettings,
                onImportAppData = { value ->
                    onImportAppData(value).also { restored ->
                        pendingSettings = restored.persistedSettings.appSettings
                    }
                },
                onExportAppData = { onExportAppData(appSettings) },
                onReplayOnboarding = onReplayOnboarding,
                onReplayFollowUpOnboarding = ::persistAndReplayFollowUpOnboarding,
                onReplayAlpineSetupPreview = onReplayAlpineSetupPreview,
                onExportLogs = onExportLogs,
                onTransientMessage = onTransientMessage,
                onBack = { destination = null },
            )
            SharedSettingsKind.About -> SharedAboutSettingsDetail(
                platformServices = platformServices,
                onTransientMessage = onTransientMessage,
                onBack = { destination = null },
            )
            SharedSettingsKind.Generic -> SettingsDetail(
                selected = selected,
                onBack = { destination = null },
            )
        }
    }

    fun open(title: String, subtitle: String) {
        destination = SettingsDestination(title, subtitle)
    }

    val general = SettingsDestination(
        stringResource(Res.string.settings_general),
        stringResource(
            Res.string.settings_general_summary,
            stringResource(
                when (appSettings.language) {
                    com.zhousl.aether.data.AppLanguage.English -> Res.string.language_english
                    com.zhousl.aether.data.AppLanguage.SimplifiedChinese ->
                        Res.string.language_simplified_chinese
                    com.zhousl.aether.data.AppLanguage.Persian ->
                        Res.string.language_persian
                },
            ),
            stringResource(
                when (appSettings.themeMode) {
                    com.zhousl.aether.data.AppThemeMode.System -> Res.string.theme_system
                    com.zhousl.aether.data.AppThemeMode.Light -> Res.string.theme_light
                    com.zhousl.aether.data.AppThemeMode.Dark -> Res.string.theme_dark
                },
            ),
        ),
        SharedSettingsKind.General,
    )
    val providers = SettingsDestination(
        stringResource(Res.string.settings_model_providers),
        providerConfigs.count(LlmProviderConfig::isEnabled).let { enabledCount ->
            when {
                enabledCount > 1 -> stringResource(
                    Res.string.settings_enabled_providers_count,
                    enabledCount,
                )
                enabledCount == 1 -> providerConfigs.firstOrNull { it.isEnabled }?.name.orEmpty()
                else -> stringResource(Res.string.settings_no_providers_configured)
            }
        },
        SharedSettingsKind.Providers,
    )
    val personalization = SettingsDestination(
        stringResource(Res.string.settings_personalization),
        stringResource(Res.string.settings_personalization_summary),
        SharedSettingsKind.Personalization,
    )
    val webTools = SettingsDestination(
        stringResource(Res.string.settings_web_tools),
        stringResource(
            if (pendingSettings.tavilyApiKey.isNotBlank()) {
                Res.string.settings_tavily_configured
            } else {
                Res.string.settings_tavily_not_configured
            },
        ),
        SharedSettingsKind.WebTools,
    )
    val reliability = SettingsDestination(
        stringResource(Res.string.settings_reliability),
        buildString {
            append(
                stringResource(
                    Res.string.settings_reconnect_after_seconds,
                    pendingSettings.llmInactivityReconnectTimeoutSeconds,
                ),
            )
            if (capabilities.persistentBackground) {
                append(" · ")
                append(
                    stringResource(
                        if (pendingSettings.keepTasksRunningInBackground) {
                            Res.string.settings_background_runs_on
                        } else {
                            Res.string.settings_background_runs_off
                        },
                    ),
                )
            }
        },
        SharedSettingsKind.Reliability,
    )
    val skills = SettingsDestination(
        stringResource(Res.string.settings_agent_skills),
        stringResource(Res.string.settings_skills_count_configured, installedSkills.size),
        SharedSettingsKind.Skills,
    )
    val extensions = SettingsDestination(
        stringResource(Res.string.settings_pi_extensions),
        stringResource(Res.string.settings_pi_extensions_count_configured, extensionCount),
        SharedSettingsKind.Extensions,
    )
    val mcp = SettingsDestination(
        stringResource(Res.string.settings_mcp_servers),
        stringResource(Res.string.settings_mcp_server_count_summary, mcpServers.size),
        SharedSettingsKind.Mcp,
    )
    val alpine = SettingsDestination(
        "Alpine",
        stringResource(
            if (alpineReady) {
                Res.string.settings_alpine_subtitle_ready
            } else {
                Res.string.settings_alpine_subtitle_setup
            },
        ),
        SharedSettingsKind.Alpine,
    )
    val statistics = SettingsDestination(
        stringResource(Res.string.statistics_title),
        statisticsReport?.takeIf { it.turnCount > 0 }?.let { report ->
            stringResource(
                Res.string.settings_statistics_summary,
                formatSharedTokenCount(report.totalTokens),
                report.turnCount,
            )
        } ?: stringResource(Res.string.settings_statistics_empty),
        SharedSettingsKind.Statistics,
    )
    val developer = SettingsDestination(
        stringResource(Res.string.settings_developer),
        stringResource(Res.string.settings_developer_subtitle),
        SharedSettingsKind.Developer,
    )
    val about = SettingsDestination(
        stringResource(Res.string.about_title),
        stringResource(Res.string.settings_release_summary, platformAppVersion()),
        SharedSettingsKind.About,
    )
    CompositionLocalProvider(LocalSharedSettingsDismissGuard provides dismissGuard) {
        SharedSettingsPageTransition(
            targetState = destination,
            depth = { it.depth() },
            label = "settings_page_transition",
        ) { selected ->
        if (selected != null) {
            detailContent(selected)
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = AetherBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    val hasSettingsExtensionSurface =
                        LocalSharedAetherExtensionUiController.current
                            ?.snapshot
                            ?.surfacesAt(SharedExtensionSlotSettingsHub)
                            .orEmpty()
                            .isNotEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding(),
                        contentPadding = PaddingValues(
                            top = sharedSettingsContentTopPadding(),
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (hasSettingsExtensionSurface) {
                            item(key = "settings-extension-slot") {
                                SharedAetherExtensionSlot(
                                    SharedExtensionSlotSettingsHub,
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        item(key = "settings-general") {
                            SettingsCardGroup {
                                SettingsNavRow(
                                    Icons.Rounded.AutoAwesome,
                                    general.title,
                                    general.subtitle,
                                ) { destination = general }
                            }
                        }
                        item(key = "settings-providers") {
                            SettingsCardGroup {
                                SettingsNavRow(
                                    Icons.Rounded.Cloud,
                                    providers.title,
                                    providers.subtitle,
                                ) { destination = providers }
                                CardDivider()
                                SettingsNavRow(
                                    Icons.Rounded.Person,
                                    personalization.title,
                                    personalization.subtitle,
                                ) { destination = personalization }
                                CardDivider()
                                SettingsNavRow(
                                    Icons.Rounded.Link,
                                    webTools.title,
                                    webTools.subtitle,
                                ) { destination = webTools }
                                CardDivider()
                                SettingsNavRow(
                                    Icons.Rounded.Refresh,
                                    reliability.title,
                                    reliability.subtitle,
                                ) { destination = reliability }
                            }
                        }
                        item(key = "settings-tools") {
                            SettingsCardGroup {
                                SettingsNavRow(
                                    Icons.Rounded.Extension,
                                    skills.title,
                                    skills.subtitle,
                                ) { destination = skills }
                                CardDivider()
                                SettingsNavRow(
                                    painterResource(Res.drawable.pi_logo_on_light),
                                    extensions.title,
                                    extensions.subtitle,
                                ) { destination = extensions }
                                CardDivider()
                                SettingsNavRow(Icons.Rounded.Code, mcp.title, mcp.subtitle) {
                                    destination = mcp
                                }
                                if (capabilities.scheduledTasks) {
                                    CardDivider()
                                    SettingsNavRow(
                                        Icons.Rounded.Schedule,
                                        stringResource(Res.string.settings_scheduled_tasks),
                                        stringResource(Res.string.settings_scheduled_tasks_description),
                                    ) { }
                                }
                                CardDivider()
                                SettingsNavRow(Icons.Rounded.Code, alpine.title, alpine.subtitle) {
                                    destination = alpine
                                }
                                if (capabilities.termux) {
                                    CardDivider()
                                    SettingsNavRow(
                                        Icons.Rounded.Terminal,
                                        stringResource(Res.string.settings_termux),
                                        stringResource(Res.string.settings_termux_description),
                                    ) { }
                                }
                                if (capabilities.runtimeSelection) {
                                    CardDivider()
                                    SettingsNavRow(
                                        Icons.Rounded.Check,
                                        stringResource(Res.string.settings_runtime_defaults),
                                        stringResource(Res.string.settings_runtime_defaults_hint),
                                    ) { }
                                }
                                if (capabilities.agentMode) {
                                    CardDivider()
                                    SettingsNavRow(
                                        LucideIcons.MousePointer2,
                                        stringResource(Res.string.settings_agent_mode),
                                        stringResource(Res.string.settings_agent_mode_subtitle),
                                    ) { }
                                }
                            }
                        }
                        item(key = "settings-statistics") {
                            SettingsCardGroup {
                                SettingsNavRow(
                                    LucideIcons.ChartNoAxesColumn,
                                    statistics.title,
                                    statistics.subtitle,
                                ) { destination = statistics }
                            }
                        }
                        item(key = "settings-guides") {
                            SettingsCardGroup {
                                SettingsNavRow(
                                    Icons.Rounded.AutoAwesome,
                                    stringResource(Res.string.settings_get_started_tour),
                                    stringResource(Res.string.settings_get_started_tour_subtitle),
                                    onClick = ::persistAndReplayOnboarding,
                                )
                                CardDivider()
                                SettingsNavRow(
                                    Icons.Rounded.Code,
                                    developer.title,
                                    developer.subtitle,
                                ) { destination = developer }
                            }
                        }
                        item(key = "settings-about") {
                            SettingsCardGroup {
                                SettingsNavRow(Icons.Rounded.Info, about.title, about.subtitle) {
                                    destination = about
                                }
                            }
                        }
                    }
                    SettingsTopBar(
                        title = stringResource(Res.string.settings_title),
                        onBack = ::persistAndExit,
                    )
                }
            }
        }
        }
    }
}

private fun AppSettings.withSharedSettingsDraft(draft: AppSettings): AppSettings = copy(
    systemPrompt = draft.systemPrompt,
    tavilyApiKey = draft.tavilyApiKey,
    tavilyBaseUrl = normalizeTavilyBaseUrl(draft.tavilyBaseUrl),
    llmInactivityReconnectTimeoutSeconds = normalizeLlmInactivityReconnectTimeoutSeconds(
        draft.llmInactivityReconnectTimeoutSeconds,
    ),
    autoCleanOldCommandHistory = draft.autoCleanOldCommandHistory,
    oldCommandHistoryRetentionHours = normalizeOldCommandHistoryRetentionHours(
        draft.oldCommandHistoryRetentionHours,
    ),
    defaultChatModelKey = draft.defaultChatModelKey,
    defaultTitleModelKey = draft.defaultTitleModelKey,
    defaultNamingModelKey = draft.defaultNamingModelKey,
    defaultCompactingModelKey = draft.defaultCompactingModelKey,
)

@Composable
private fun SharedAlpineSettingsDetail(
    runtime: MultiplatformLocalRuntime,
    settings: AppSettings,
    onSettingsSaved: (AppSettings) -> Unit,
    onResetSettingsSaved: suspend (AppSettings) -> Unit,
    onOpenTerminal: () -> Unit,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    SharedAlpineSettingsDetailPage(
        runtime = runtime,
        settings = settings,
        onSettingsSaved = onSettingsSaved,
        onResetSettingsSaved = onResetSettingsSaved,
        onOpenTerminal = onOpenTerminal,
        onTransientMessage = onTransientMessage,
        onBack = onBack,
    )
}

@Composable
private fun SettingsDetail(selected: SettingsDestination, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            SettingsCardGroup {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, color = AetherOnSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(selected.subtitle, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
                }
            }
        }
        SettingsTopBar(title = selected.title, onBack = onBack)
    }
}

@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingEnabled: Boolean = true,
    trailingContentDescription: String = "",
    onTrailingAction: () -> Unit = {},
) {
    val dismissGuard = LocalSharedSettingsDismissGuard.current
    val reduceMotion = LocalReduceMotion.current
    val saveShakeOffset = remember { Animatable(0f) }
    val saveShakeRequest = dismissGuard?.saveShakeRequest ?: 0
    LaunchedEffect(saveShakeRequest) {
        if (saveShakeRequest > 0 && !reduceMotion) {
            saveShakeOffset.snapTo(0f)
            saveShakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 420
                    -9f at 55
                    9f at 110
                    -7f at 165
                    7f at 220
                    -4f at 285
                    4f at 340
                },
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to AetherBackground.copy(alpha = 0.96f),
                        0.18f to AetherBackground.copy(alpha = 0.86f),
                        0.42f to AetherBackground.copy(alpha = 0.48f),
                        0.72f to AetherBackground.copy(alpha = 0.22f),
                        1.0f to AetherBackground.copy(alpha = 0.12f),
                    ),
                ),
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SharedSettingsCircleButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back),
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (trailingIcon != null) {
                    SharedSettingsCircleButton(
                        icon = trailingIcon,
                        contentDescription = trailingContentDescription,
                        onClick = onTrailingAction,
                        enabled = trailingEnabled,
                        modifier = Modifier.align(Alignment.CenterEnd).offset {
                            IntOffset(saveShakeOffset.value.roundToInt(), 0)
                        },
                    )
                } else {
                    Spacer(Modifier.align(Alignment.CenterEnd).size(44.dp))
                }
            }
        }
        Spacer(
            modifier = Modifier.fillMaxWidth().height(SettingsTopFadeHeight).background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to AetherBackground.copy(alpha = 0.12f),
                        0.42f to AetherBackground.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                    ),
                )
            )
        )
    }
}

@Composable
private fun SharedSettingsCircleButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(
                10.dp,
                RoundedCornerShape(50),
                ambientColor = AetherScrim,
                spotColor = AetherScrim,
            )
            .clip(RoundedCornerShape(50))
            .background(if (enabled) AetherSurface else AetherSurface.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) AetherOnSurface else AetherOnSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
internal fun sharedSettingsContentTopPadding(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.statusBars.getTop(this).toDp() + 74.dp }
}
