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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LibraryAdd
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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
import com.zhousl.aether.platform.NativeSettingsCommandHandler
import com.zhousl.aether.platform.NativeSettingsHost
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.LocalRuntimeId
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
import com.zhousl.aether.data.CreateExtensionSkillId
import com.zhousl.aether.data.SharedSkillDirectoryEntry
import com.zhousl.aether.data.generateSharedQuickActionLabel
import com.zhousl.aether.data.SharedAetherExtensionManager
import com.zhousl.aether.data.SharedAetherExtensionSnapshot
import com.zhousl.aether.data.SharedAetherExtensionSettingsPage
import com.zhousl.aether.data.SharedPiExtensionUiRequest
import com.zhousl.aether.data.SharedExtensionStateStore
import com.zhousl.aether.data.SharedProviderModelCatalogClient
import com.zhousl.aether.data.SharedModelCatalogInfo
import com.zhousl.aether.data.SharedThinkingCatalogCache
import com.zhousl.aether.data.ModelsDevThinkingCatalogSource
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
import com.zhousl.aether.data.sharedThinkingCatalogKey
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.platformUptimeMillis
import com.zhousl.aether.data.loadUsageStatistics
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.normalizeOldCommandHistoryRetentionHours
import com.zhousl.aether.data.parseProviderConfigs
import com.zhousl.aether.data.serializeAppSettings
import com.zhousl.aether.data.serializeProviderConfigs
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.data.pi.SharedPiChatClient
import com.zhousl.aether.data.pi.SharedPiChatMessage
import com.zhousl.aether.data.pi.SharedPiContentPart
import com.zhousl.aether.data.pi.SharedPiToolEvent
import com.zhousl.aether.data.pi.SharedPiTurnResult
import com.zhousl.aether.data.pi.SharedPiUsage
import com.zhousl.aether.platform.IosAnalytics
import com.zhousl.aether.data.pi.RuntimeHostToolExecutor
import com.zhousl.aether.data.pi.SharedAgentManagementTools
import com.zhousl.aether.data.pi.SharedMcpServerConfig
import com.zhousl.aether.data.pi.SharedMcpTransport
import com.zhousl.aether.data.pi.SharedChromeManager
import com.zhousl.aether.data.pi.SharedBrowserDisplayState
import com.zhousl.aether.data.pi.SharedCompositeHostTools
import com.zhousl.aether.data.pi.SharedHostToolResult
import com.zhousl.aether.data.pi.toPiOAuthPrompt
import com.zhousl.aether.data.pi.toPiProviderEnvironmentVariables
import com.zhousl.aether.data.pi.toSharedPiModelConfig
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
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSettingsBackground
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import com.zhousl.aether.ui.theme.AetherTertiary
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal enum class SharedRoute { Onboarding, Chat }

private enum class OnboardingStage { Landing, Runtime, Provider }
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
}

private data class SharedReasoningSummary(
    val title: String,
    val detail: String,
)

private class SharedNonSnapshotJobSlot {
    var job: Job? = null
}

private class SharedAgentExtensionSettingsAccess {
    var readHandler: (suspend () -> SharedAetherExtensionSnapshot)? = null
    var updateHandler: (suspend (String, String, String, JsonElement) -> SharedAetherExtensionSnapshot)? = null

    suspend fun snapshot(): SharedAetherExtensionSnapshot =
        readHandler?.invoke() ?: error("Native extension settings are not ready yet.")

    suspend fun update(
        extensionId: String,
        settingsId: String,
        settingId: String,
        value: JsonElement,
    ): SharedAetherExtensionSnapshot = updateHandler?.invoke(extensionId, settingsId, settingId, value)
        ?: error("Native extension settings are not ready yet.")
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

internal data class SharedReasoningSummaryFollowUp(
    val requested: Boolean,
    val forceRemaining: Boolean,
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
    private var summaryInFlight = false
    private var summaryFollowUpRequested = false
    private var summaryFollowUpForced = false

    fun nextTimelineOrder(): Long {
        timelineCounter += 1
        return timelineCounter
    }

    /** Keep background summary requests single-flight and remember the latest event. */
    fun beginSummary(forceRemaining: Boolean): Boolean {
        if (summaryInFlight) {
            summaryFollowUpRequested = true
            summaryFollowUpForced = summaryFollowUpForced || forceRemaining
            return false
        }
        summaryInFlight = true
        return true
    }

    fun finishSummary(): SharedReasoningSummaryFollowUp {
        val followUp = SharedReasoningSummaryFollowUp(
            requested = summaryFollowUpRequested,
            forceRemaining = summaryFollowUpForced,
        )
        summaryInFlight = false
        summaryFollowUpRequested = false
        summaryFollowUpForced = false
        return followUp
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
    val customType: String = "",
    val customPayloadJson: String = "",
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
    val piBranchMessageId: String?,
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
    val piBranchMessageId = retained.dropLast(1).lastOrNull { !it.fromUser }?.id
    return SharedAssistantRetryPlan(retained, user, piBranchMessageId)
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

internal fun resolveSharedActiveProviderConfigId(
    providerConfigs: List<LlmProviderConfig>,
    preferredActiveConfigId: String,
): String = preferredActiveConfigId.takeIf { configId ->
    providerConfigs.any { it.id == configId && it.isEnabled }
} ?: providerConfigs.firstOrNull { it.isEnabled }?.id
    ?: providerConfigs.firstOrNull()?.id.orEmpty()

internal fun resolveSharedConversationModelKey(
    selectedModelKey: String,
    defaultChatModelKey: String,
    options: List<ProviderModelOption>,
): String = selectedModelKey.takeIf { key -> options.any { it.key == key } }
    ?: defaultChatModelKey.takeIf { key -> options.any { it.key == key } }
    ?: options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)

private val TopFadeHeight = 42.dp
private const val FollowUpTourAutoOpenDelayMillis = 2_500L
private const val TransientMessageDurationMillis = 2_000L
private const val RuntimeSetupProgressTickMillis = 450L
private val ComposerShape = RoundedCornerShape(26.dp)
private val ComposerFocusedShape = RoundedCornerShape(28.dp)
private val ComposerPlusMenuMaxHeight = 372.dp
private val ControlShadow = Color(0x14000000)
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

@Composable
fun IosComposeApp(
    runtime: MultiplatformLocalRuntime,
    capabilities: PlatformCapabilities,
    settingsStore: AetherSettingsStore? = null,
    chatHistoryDatabase: ChatHistoryDatabase? = null,
    platformServices: PlatformServices = NoOpPlatformServices,
    nativeSettingsHost: NativeSettingsHost? = null,
) {
    CompositionLocalProvider(LocalPlatformServices provides platformServices) {
    var sharedAppSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(sharedAppSettings.privacyPolicyAccepted) {
        if (sharedAppSettings.privacyPolicyAccepted) IosAnalytics.acceptConsent()
    }
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
        val appScope = rememberCoroutineScope()
        val extensionStateStore = remember(runtime) { SharedExtensionStateStore(runtime) }
        val bridgeClient = remember(runtime, extensionStateStore) {
            SharedPiBridgeClient(
                transport = RuntimePiBridgeTransport(runtime),
                extensionLoadOptionsProvider = extensionStateStore::load,
            )
        }
        val extensionBridgeClient = remember(runtime, extensionStateStore) {
            SharedPiBridgeClient(
                transport = RuntimePiBridgeTransport(
                    runtime = runtime,
                    bridgePath = "/root/.aether/pi-bridge/extension-bridge.mjs",
                ),
                extensionLoadOptionsProvider = extensionStateStore::load,
            )
        }
        val chromeManager = remember(runtime) { SharedChromeManager(runtime) }
        val runtimeTools = remember(runtime) { RuntimeHostToolExecutor(runtime) }
        val skillManager = remember(runtime, extensionBridgeClient) {
            SharedSkillManager(runtime, extensionBridgeClient)
        }
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
        val persistDeveloperRoleFallback: suspend (String) -> Unit = remember(settingsStore) {
            { configId ->
                if (configId.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        val index = providerConfigs.indexOfFirst { it.id == configId }
                        val current = providerConfigs.getOrNull(index)
                        if (current != null && !current.developerRoleUnsupported) {
                            val updated = providerConfigs.toMutableList().apply {
                                this[index] = current.copy(
                                    developerRoleUnsupported = true,
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
        val completionClient = remember(
            bridgeClient,
            persistOAuthCredential,
            persistDeveloperRoleFallback,
        ) {
            SharedPiChatClient(
                bridge = bridgeClient,
                onOAuthCredentialUpdated = persistOAuthCredential,
                onDeveloperRoleUnsupportedDetected = persistDeveloperRoleFallback,
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
        var reasoningModels by remember { mutableStateOf<Set<String>>(emptySet()) }
        val thinkingCatalogRefreshMutex = remember { Mutex() }
        LaunchedEffect(modelCatalogRequestKey) {
            if (modelOptions.isNotEmpty()) {
                // Restore the persisted effort catalog as soon as provider/model
                // options exist. This is independent of the slower network
                // refresh and keeps the picker usable while offline.
                val cachedThinkingCatalog = withContext(Dispatchers.Default) {
                    settingsStore?.load()?.thinkingCatalogCache
                        ?.takeIf { it.source == ModelsDevThinkingCatalogSource }
                }
                val validKeys = modelOptions.mapTo(mutableSetOf()) { option ->
                    sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                }
                val restoredLevels = cachedThinkingCatalog?.levelsByProviderModel
                    .orEmpty()
                    .filterKeys(validKeys::contains)
                val restoredClamps = cachedThinkingCatalog?.clampsByProviderModel
                    .orEmpty()
                    .filterKeys(validKeys::contains)
                val restoredReasoningModels = cachedThinkingCatalog?.reasoningModels
                    .orEmpty()
                    .filterTo(mutableSetOf(), validKeys::contains)
                if (restoredLevels.isNotEmpty()) {
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel + restoredLevels
                    thinkingLevelClampsByProviderModel =
                        thinkingLevelClampsByProviderModel + restoredClamps
                    reasoningModels += restoredReasoningModels
                }
            }
            val fetched = modelCatalogClient.fetchModelInfo(modelOptions)
            if (fetched.isEmpty()) return@LaunchedEffect
            fetched.values.distinctBy(SharedModelCatalogInfo::labLogoPathData).forEach { info ->
                kotlinx.coroutines.yield()
                SharedModelLogoPathCache.getOrParse(info.labLogoPathData)
            }
            modelCatalogInfo = fetched
            settingsStore?.saveModelCatalogCache(
                com.zhousl.aether.data.SharedModelCatalogCache(fetched)
            )
        }
        val installedSkills = remember { mutableStateListOf<SharedInstalledSkill>() }
        val mcpServers = remember { mutableStateListOf<SharedMcpServerConfig>() }
        var chromeEnabled by rememberSaveable { mutableStateOf(false) }
        DisposableEffect(bridgeClient, extensionBridgeClient) {
            onDispose {
                bridgeClient.dispose()
                extensionBridgeClient.dispose()
            }
        }
        var route by rememberSaveable { mutableStateOf(SharedRoute.Onboarding) }
        var startupResolved by remember { mutableStateOf(false) }
        var extensionRuntimeReady by remember(runtime) { mutableStateOf(false) }
        LaunchedEffect(runtime, capabilities.scriptExtensions) {
            if (capabilities.scriptExtensions) {
                // Start restoring an existing iOS runtime while app state is loading.
                extensionRuntimeReady = runSharedAppCatching { runtime.isReady() }
                    .getOrDefault(false)
            }
        }
        val historyStore = remember(chatHistoryDatabase) {
            chatHistoryDatabase?.let(::SharedChatHistoryStore)
        }
        val appDataManager = remember(
            settingsStore,
            historyStore,
            skillManager,
            runtime,
            bridgeClient,
        ) {
            if (settingsStore != null && historyStore != null) {
                SharedAppDataManager(
                    settingsStore = settingsStore,
                    historyStore = historyStore,
                    skillManager = skillManager,
                    runtime = runtime,
                    bridgeClient = bridgeClient,
                    extensionStateStore = extensionStateStore,
                )
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
        var extensionSnapshotResolved by remember { mutableStateOf(false) }
        var nativeStatisticsReport by remember {
            mutableStateOf(com.zhousl.aether.data.SharedUsageStatisticsReport())
        }
        val nativeAlpineController = remember(runtime) { NativeAlpineSettingsController(runtime) }
        var nativeAlpineState by remember {
            mutableStateOf(
                NativeAlpineSettingsState(
                    ready = sharedAppSettings.alpineSetupCompleted,
                    issue = if (sharedAppSettings.alpineSetupCompleted) "ready" else "not_installed",
                )
            )
        }
        LaunchedEffect(sharedAppSettings.alpineSetupCompleted) {
            if (nativeAlpineState.operation.isBlank()) {
                nativeAlpineState = nativeAlpineState.copy(
                    ready = sharedAppSettings.alpineSetupCompleted,
                    issue = if (sharedAppSettings.alpineSetupCompleted) "ready" else "not_installed",
                    detail = "",
                )
            }
        }
        var nativeProviderModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
        var nativeProviderOperation by remember { mutableStateOf("") }
        var nativeProviderCompletedRequestId by remember { mutableStateOf("") }
        var nativeProviderAuthSessionId by remember { mutableStateOf("") }
        var nativeProviderAuthJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var nativeProviderError by remember { mutableStateOf("") }
        var nativeProviderAuthState by remember { mutableStateOf(PiProviderAuthState()) }
        var nativeAuthenticationCallback by remember { mutableStateOf("") }
        var nativeOperationMessage by remember { mutableStateOf("") }
        var nativeOperationError by remember { mutableStateOf("") }
        var nativeOperation by remember { mutableStateOf("") }
        var transientMessage by remember { mutableStateOf("") }
        var onboardingReplayMode by remember { mutableStateOf(false) }
        var onboardingEntryStage by remember { mutableStateOf(OnboardingStage.Landing) }
        var showStarterPromptHint by remember { mutableStateOf(false) }
        var awaitingFollowUpTour by remember { mutableStateOf(false) }
        var alpineSetupPreviewVisible by remember { mutableStateOf(false) }
        var extensionManagerRef by remember { mutableStateOf<SharedAetherExtensionManager?>(null) }
        val agentExtensionSettingsAccess = remember { SharedAgentExtensionSettingsAccess() }
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
        LaunchedEffect(nativeAuthenticationCallback, nativeProviderAuthState.prompt?.id) {
            val callback = nativeAuthenticationCallback.takeIf(String::isNotBlank)
                ?: return@LaunchedEffect
            val prompt = nativeProviderAuthState.prompt
                ?.takeIf { it.type == "manual_code" }
                ?: return@LaunchedEffect
            val sessionId = nativeProviderAuthSessionId
            nativeAuthenticationCallback = ""
            runSharedAppCatching {
                bridgeClient.submitAuthPrompt(prompt.id, callback, false)
            }.onSuccess {
                if (sessionId == nativeProviderAuthSessionId && prompt.id == nativeProviderAuthState.prompt?.id) {
                    nativeProviderAuthState = nativeProviderAuthState.copy(prompt = null)
                }
            }.onFailure { failure ->
                if (sessionId == nativeProviderAuthSessionId) {
                    nativeProviderAuthState = nativeProviderAuthState.copy(
                        errorMessage = failure.sharedUserFacingMessage(),
                    )
                }
            }
        }

        val managementTools = remember(runtime, bridgeClient, skillManager) {
            SharedAgentManagementTools(
                runtime = runtime,
                bridge = bridgeClient,
                skillManager = skillManager,
                settings = { withContext(Dispatchers.Main) { sharedAppSettings } },
                updateSettings = { updated ->
                    withContext(Dispatchers.Main) {
                        sharedAppSettings = updated
                        settingsStore?.saveGeneralSettings(updated)
                    }
                },
                currentSessionId = { withContext(Dispatchers.Main) { currentSession.id } },
                extensionSettings = agentExtensionSettingsAccess::snapshot,
                updateExtensionSetting = agentExtensionSettingsAccess::update,
            )
        }
        val hostToolRegistry = remember(managementTools, chromeManager) {
            SharedCompositeHostTools(
                listOf(
                    managementTools,
                    chromeManager,
                )
            )
        }
        val chatClient = remember(
            bridgeClient,
            hostToolRegistry,
            persistOAuthCredential,
            persistDeveloperRoleFallback,
        ) {
            SharedPiChatClient(
                bridge = bridgeClient,
                hostToolExecutor = hostToolRegistry,
                onOAuthCredentialUpdated = persistOAuthCredential,
                onDeveloperRoleUnsupportedDetected = persistDeveloperRoleFallback,
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
            if (index < 0) IosAnalytics.capture("provider added", mapOf(
                "provider" to PiProviderCatalog.resolve(config.piProviderId).displayName,
                "provider_id" to config.id,
            ))
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
            if (providerConfigs.any { it.id == configId }) IosAnalytics.capture("provider removed")
            commitProviderConfigs(providerConfigs.filterNot { it.id == configId })
        }

        fun persistResolvedAppSettings(updated: AppSettings) {
            val modelOptions = providerConfigs.availableModelOptions()
            var resolved = updated
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

        fun openSettings() {
            nativeSettingsHost?.openSettings()
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
                            it.interruptedByBackgroundExpiration(status = chatInterruptedStatus)
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

        val showcaseEnabled = remember { isIosShowcaseEnabled() }
        var showcaseCatalog by remember { mutableStateOf(emptyList<PersistedChatSession>()) }
        val showcasePlayers = remember { mutableMapOf<String, com.zhousl.aether.data.ShowcasePlayback>() }
        var showcasePausedSessions by remember { mutableStateOf(emptySet<String>()) }
        var showcaseSpeed by remember { mutableStateOf(1f) }

        fun replayShowcase(target: SharedSessionUiState = currentSession, restoreOnly: Boolean = false) {
            val template = showcaseCatalog.firstOrNull { it.id == target.id } ?: return
            val oldJob = target.job
            val player = com.zhousl.aether.data.ShowcasePlayback().also { it.speed = showcaseSpeed }
            showcasePlayers[target.id] = player
            showcasePausedSessions = showcasePausedSessions - target.id
            target.job = appScope.launch {
                oldJob?.cancel()
                oldJob?.join()
                try {
                    if (!restoreOnly) player.play(template) { completed, pending ->
                        val visible = completed.map { it.toSharedChatMessage() } + listOfNotNull(
                            pending?.toSharedChatMessage()?.copy(isStreaming = true),
                        )
                        target.messages.clear()
                        target.messages.addAll(visible)
                    }
                    target.messages.clear()
                    target.messages.addAll(template.messages.map { it.toSharedChatMessage() })
                    persistSession(target)
                } finally {
                    target.streamingStatus = ""
                    if (target.job === coroutineContext[kotlinx.coroutines.Job]) {
                        target.job = null
                        showcasePausedSessions = showcasePausedSessions - target.id
                    }
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
                val persistedCatalogKeys = persistedModelOptions.mapTo(mutableSetOf(), ProviderModelOption::key)
                modelCatalogInfo = persisted.modelCatalogCache.models
                    .filterKeys(persistedCatalogKeys::contains)
                val persistedThinkingKeys = persistedModelOptions.mapTo(mutableSetOf()) { option ->
                    sharedThinkingCatalogKey(option.piProviderId, option.modelId)
                }
                thinkingLevelsByProviderModel = persisted.thinkingCatalogCache
                    .takeIf { it.source == ModelsDevThinkingCatalogSource }
                    ?.levelsByProviderModel
                    .orEmpty()
                    .filterKeys(persistedThinkingKeys::contains)
                thinkingLevelClampsByProviderModel = persisted.thinkingCatalogCache
                    .takeIf { it.source == ModelsDevThinkingCatalogSource }
                    ?.clampsByProviderModel
                    .orEmpty()
                    .filterKeys(persistedThinkingKeys::contains)
                reasoningModels = persisted.thinkingCatalogCache
                    .takeIf { it.source == ModelsDevThinkingCatalogSource }
                    ?.reasoningModels
                    .orEmpty()
                    .filterTo(mutableSetOf(), persistedThinkingKeys::contains)
                if (currentSession.isDraft) {
                    currentSession.selectedModelKey = resolveSharedConversationModelKey(
                        selectedModelKey = currentSession.selectedModelKey,
                        defaultChatModelKey = persisted.appSettings.defaultChatModelKey,
                        options = persistedModelOptions,
                    )
                }
                if (shouldRestoreSharedChat(persisted.appSettings.onboardingSeenVersion)) {
                    route = runCatching { SharedRoute.valueOf(persisted.uiState.route) }
                        .getOrDefault(SharedRoute.Chat)
                }
            }
            if (showcaseEnabled) {
                showcaseCatalog = com.zhousl.aether.data.ShowcaseCatalog.load(android = false)
                val previousCurrentSessionId = historyStore?.loadCurrentSessionId()
                var added = false
                for (demo in showcaseCatalog) {
                    val existing = historyStore?.load(demo.id)
                    if (existing == null) added = true
                    historyStore?.save(
                        demo.id, demo.messages, titleOverride = demo.title, hasCustomTitle = true,
                        selectedModelKey = existing?.selectedModelKey?.takeIf(String::isNotBlank) ?: demo.selectedModelKey,
                    )
                }
                historyStore?.setCurrentSession(
                    if (added) showcaseCatalog.first().id
                    else previousCurrentSessionId?.takeIf(String::isNotBlank) ?: showcaseCatalog.first().id,
                )
                providerConfigs.addAll(com.zhousl.aether.data.ShowcaseCatalog.providers().filter { demo -> providerConfigs.none { it.id == demo.id } })
                route = SharedRoute.Chat
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
            restored.selectedModelKey = resolveSharedConversationModelKey(
                selectedModelKey = if (com.zhousl.aether.data.ShowcaseCatalog.isSession(restored.id)) restored.selectedModelKey else "",
                defaultChatModelKey = sharedAppSettings.defaultChatModelKey,
                options = providerConfigs.availableModelOptions(),
            )
            currentSession = restored
            sessionId = restored.id
            if (!restored.isDraft) persistSession(restored)
            historyStore?.load(restored.id)?.let { persisted ->
                chromeEnabled = persisted.chromeEnabled && capabilities.alpineChrome
                chromeManager.enabled = chromeEnabled
            }
            startupResolved = true
        }

        LaunchedEffect(route) {
            if (startupResolved) {
                settingsStore?.saveUiState(route.name)
            }
            if (route == SharedRoute.Chat) {
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
                mcpServers.clear()
                retainEnabledMcpSelections(emptySet())
            }
        }

        suspend fun persistThinkingCatalogCache() {
            val validKeys = modelOptions.mapTo(mutableSetOf()) { option ->
                sharedThinkingCatalogKey(option.piProviderId, option.modelId)
            }
            val cache = SharedThinkingCatalogCache(
                source = ModelsDevThinkingCatalogSource,
                levelsByProviderModel = thinkingLevelsByProviderModel.filterKeys(validKeys::contains),
                clampsByProviderModel =
                    thinkingLevelClampsByProviderModel.filterKeys(validKeys::contains),
                reasoningModels = reasoningModels.filterTo(mutableSetOf(), validKeys::contains),
            )
            withContext(Dispatchers.Default) {
                settingsStore?.saveThinkingCatalogCache(cache)
            }
        }

        suspend fun refreshThinkingCatalog(
            options: List<ProviderModelOption> = modelOptions,
        ): Boolean = thinkingCatalogRefreshMutex.withLock {
            runSharedAppCatching {
                val result = modelCatalogClient.fetchThinkingCatalog(options)
                if (result.levelsByProviderModel.isNotEmpty()) {
                    thinkingLevelsByProviderModel = thinkingLevelsByProviderModel + result.levelsByProviderModel
                    thinkingLevelClampsByProviderModel =
                        (thinkingLevelClampsByProviderModel - result.levelsByProviderModel.keys) +
                            result.levelMapsByProviderModel
                    reasoningModels = (reasoningModels - result.levelsByProviderModel.keys) +
                        result.reasoningModels
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
            put("session_title", state.title)
            put("draft_input", state.input)
            put("is_generating", state.isWorking)
            put("is_running", state.isWorking)
            put("is_editing", state.editingMessageId.isNotBlank())
            put("selected_model_key", state.selectedModelKey)
            put("agent_mode_enabled", false)
            put("selected_skill_ids", JsonArray(state.selectedSkillIds.map(::JsonPrimitive)))
            put("selected_mcp_server_ids", JsonArray(state.activeMcpServerIds.map(::JsonPrimitive)))
            put(
                "default_skill_ids",
                JsonArray(sharedAppSettings.defaultSelectedSkillIds.map(::JsonPrimitive)),
            )
            put("language", sharedAppSettings.language.storageValue)
            put("theme", sharedAppSettings.themeMode.storageValue)
            put("extension_count", extensionSnapshot.extensions.size)
            put("skill_count", installedSkills.count(SharedInstalledSkill::isEnabled))
            put("mcp_server_count", mcpServers.count(SharedMcpServerConfig::enabled))
            put("skills", JsonArray(installedSkills.map { skill ->
                buildJsonObject {
                    put("id", skill.id)
                    put("name", skill.name)
                    put("description", skill.description)
                    put("action_label", skill.actionLabel)
                    put("enabled", skill.isEnabled)
                    put("selected", skill.id in state.selectedSkillIds)
                    put("default_selected", skill.id in sharedAppSettings.defaultSelectedSkillIds)
                }
            }))
            put("reasoning_effort", sharedAppSettings.reasoningEffort)
            put("message_count", state.messages.size)
            put("custom_messages", JsonArray(state.messages.filter { it.customType.isNotBlank() }.map { message ->
                buildJsonObject {
                    put("id", message.id)
                    put("type", message.customType)
                    put("text", message.text)
                    put("payload", runCatching {
                        Json.parseToJsonElement(message.customPayloadJson) as? JsonObject
                    }.getOrNull() ?: JsonObject(emptyMap()))
                }
            }))
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
            val titleThinkingKey = sharedThinkingCatalogKey(
                titleConfig.piProviderId,
                titleConfig.modelId,
            )
            val titleThinkingLevelMap = thinkingLevelClampsByProviderModel[titleThinkingKey].orEmpty()
            val titleIsReasoningModel = titleThinkingKey in reasoningModels
            appScope.launch {
                val result = runSharedAppCatching {
                    completionClient.completeOnce(
                        config = titleConfig,
                        messages = listOf(SharedPiChatMessage("user", titleInput)),
                        systemPrompt = SharedSessionTitleSystemPrompt,
                        reasoning = "off",
                        timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                            .coerceIn(30, 3_600) * 1_000,
                        thinkingLevelMap = titleThinkingLevelMap,
                        isReasoningModel = titleIsReasoningModel,
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
            if (!tracker.beginSummary(forceRemaining)) return
            var submission: SharedReasoningSummarySubmission? = null
            val now = platformCurrentTimeMillis()
            target.messages.updateMessage(assistantId) { current ->
                val trace = current.activeSharedReasoningTrace() ?: return@updateMessage current
                tracker.prepareSummary(trace, forceRemaining, now)?.let { prepared ->
                    submission = prepared
                    current.withPendingReasoningSummary(prepared)
                } ?: current
            }
            val prepared = submission ?: run {
                tracker.finishSummary()
                return
            }
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
            val summaryThinkingKey = sharedThinkingCatalogKey(
                summaryConfig.piProviderId,
                summaryConfig.modelId,
            )
            val summaryThinkingLevelMap = thinkingLevelClampsByProviderModel[summaryThinkingKey]
                .orEmpty()
            val summaryIsReasoningModel = summaryThinkingKey in reasoningModels
            appScope.launch {
                try {
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
                                thinkingLevelMap = summaryThinkingLevelMap,
                                isReasoningModel = summaryIsReasoningModel,
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
                } finally {
                    val followUp = tracker.finishSummary()
                    if (followUp.requested && sessionStates[target.id] === target) {
                        enqueueReasoningSummary(
                            target = target,
                            assistantId = assistantId,
                            tracker = tracker,
                            forceRemaining = followUp.forceRemaining,
                            fallbackConfig = fallbackConfig,
                        )
                    }
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
            target.input = ""
            target.streamingStatus = SharedCompactingStatus
            target.job = appScope.launch {
                try {
                    runSharedAppCatching {
                        bridgeClient.compactSession(target.id)
                    }.fold(
                        onSuccess = { result ->
                            val now = platformCurrentTimeMillis()
                            target.messages += SharedChatMessage(
                                id = "compact-status-$now",
                                text = "Context compacted",
                                fromUser = false,
                                createdAtMillis = now,
                                assistantActionsHidden = true,
                                displayKind = SharedMessageDisplayKind.CompactStatus,
                            )
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
            piBranchMessageId: String? = null,
            resetPiBranchWhenMissing: Boolean = false,
            target: SharedSessionUiState = currentSession,
            submissionType: String = "new_turn",
        ) {
            if (showcaseEnabled && com.zhousl.aether.data.ShowcaseCatalog.isSession(target.id)) {
                replayShowcase(target)
                return
            }
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
            val resolvedPiBranchMessageId = piBranchMessageId ?: if (editingIndex >= 0) {
                target.messages.take(editingIndex).lastOrNull()?.id
            } else {
                null
            }
            val shouldResetPiBranch = resetPiBranchWhenMissing || editingIndex >= 0
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
            captureIosMessageSent(config, target, attachments.size, editingIndex >= 0,
                submissionType)
            var analyticsOutcome = "neutral"
            var analyticsInputCount = 0
            var analyticsUserCount = 0
            var responseStartedAt = 0L
            val reasoningTracker = SharedReasoningTurnTracker()
            var providerRequestCheckpoint: SharedChatMessage? = null
            var completedPiTurnResult: SharedPiTurnResult? = null
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
                val mappedPiEntryId = resolvedPiBranchMessageId?.let { messageId ->
                    historyStore?.getAgentMessageEntryIds(target.id, messageId)?.lastOrNull()
                }
                analyticsInputCount = requestMessages.size
                analyticsUserCount = requestMessages.count { it.fromUser }
                val modelKey = sharedThinkingCatalogKey(config.piProviderId, config.modelId)
                val thinkingLevelMap = thinkingLevelClampsByProviderModel[modelKey].orEmpty()
                val isReasoningModel = modelKey in reasoningModels
                val reasoningEffort = sharedAppSettings.reasoningEffort
                val reasoningEnabled = isReasoningModel
                if (mappedPiEntryId != null || shouldResetPiBranch) {
                    runSharedAppCatching {
                        bridgeClient.navigateSession(
                            sessionId = target.id,
                            entryId = mappedPiEntryId.orEmpty(),
                            reset = mappedPiEntryId == null && shouldResetPiBranch,
                            modelConfig = config.toSharedPiModelConfig(
                                timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                                    .coerceIn(30, 3_600) * 1_000,
                                reasoningEnabled = reasoningEnabled,
                                thinkingLevelMap = thinkingLevelMap,
                            ),
                            workspaceDirectory = runtime.workspaceRoot,
                            systemPrompt = sharedAppSettings.systemPrompt,
                            runtime = "alpine",
                            platform = "ios",
                            workspaceTrusted = true,
                        )
                    }
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
                // Skill selection is a one-shot command. Pi's ResourceLoader owns
                // discovery and reads SKILL.md lazily through the native read tool.
                target.selectedSkillIds.clear()
                target.activeSkills.clear()
                target.activeMcpServerIds.clear()
                persistSession(target)
                runSharedAppCatching {
                    val reasoningTraceToolRoutingEnabled = config.supportsSharedVisibleReasoningTrace()
                    chatClient.runTurn(
                        config = config,
                        messages = turnMessages,
                        sessionId = target.id,
                        skillPaths = skillSelection.availableSkills
                            .filter(SharedInstalledSkill::isEnabled)
                            .map(SharedInstalledSkill::guestPath),
                        skillCommand = skillSelection.activeSkills.firstOrNull()?.name.orEmpty(),
                        systemPrompt = buildSharedPiAgentInstructions(
                            configuredPrompt = sharedAppSettings.systemPrompt,
                            workspaceDirectory = runtime.workspaceRoot,
                            availableSkills = skillSelection.availableSkills,
                            activeSkills = skillSelection.activeSkills,
                        ),
                        reasoning = reasoningEffort,
                        timeoutMillis = sharedAppSettings.llmInactivityReconnectTimeoutSeconds
                            .coerceIn(30, 3_600) * 1_000,
                        thinkingLevelMap = thinkingLevelMap,
                        isReasoningModel = isReasoningModel,
                        onAssistantTextDelta = { delta ->
                            backgroundLeases[target.id]?.update("Writing response")
                            reasoningTracker.finishDirectSummaryChunk()
                            val now = platformCurrentTimeMillis()
                            if (responseStartedAt == 0L) responseStartedAt = now
                            enqueueReasoningSummary(
                                target = target,
                                assistantId = assistantId,
                                tracker = reasoningTracker,
                                forceRemaining = false,
                                fallbackConfig = config,
                            )
                            target.messages.updateMessage(assistantId) { current ->
                                current.completePendingReconnect()
                                    .completeAssistantReasoning(now)
                                    .appendAssistantTextDelta(delta).copy(
                                    status = "",
                                    statusDetail = "",
                                    firstTokenLatencyMillis = current.firstTokenLatencyMillis
                                        ?: (now - turnStartedAt).coerceAtLeast(0L),
                                )
                            }
                            target.streamingStatus = ""
                        },
                        onAssistantReasoningDelta = { delta ->
                            if (reasoningEffort == "off") return@runTurn
                            backgroundLeases[target.id]?.update("Reasoning")
                            reasoningTracker.finishDirectSummaryChunk()
                            val now = platformCurrentTimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                current.completePendingReconnect().appendAssistantReasoningDelta(delta, now).copy(
                                    status = "",
                                    statusDetail = "",
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
                            if (reasoningEffort == "off") return@runTurn
                            backgroundLeases[target.id]?.update("Reasoning")
                            val now = platformCurrentTimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                current.completePendingReconnect().appendDirectAssistantReasoningSummaryDelta(
                                    delta = delta,
                                    tracker = reasoningTracker,
                                    nowMillis = now,
                                ).copy(
                                    status = "",
                                    statusDetail = "",
                                )
                            }
                        },
                        onAssistantRequestStarted = {
                            providerRequestCheckpoint = target.messages.lastOrNull { it.id == assistantId }
                        },
                        onAssistantResponseReset = {
                            providerRequestCheckpoint?.let { checkpoint ->
                                target.messages.updateMessage(assistantId) {
                                    checkpoint.copy(
                                        isStreaming = true,
                                        status = SharedInitialStreamingStatusText,
                                        statusDetail = SharedInitialStreamingStatusDetail,
                                    )
                                }
                            }
                        },
                        onToolEvent = { event ->
                            backgroundLeases[target.id]?.update("Running ${event.name}")
                            if (event.outputJson == null) {
                                reasoningTracker.finishDirectSummaryChunk()
                                enqueueReasoningSummary(
                                    target = target,
                                    assistantId = assistantId,
                                    tracker = reasoningTracker,
                                    forceRemaining = true,
                                    fallbackConfig = config,
                                )
                            }
                            val now = platformCurrentTimeMillis()
                            val uptime = platformUptimeMillis()
                            target.messages.updateMessage(assistantId) { current ->
                                val existingTimelineOrder = current.tools
                                    .firstOrNull { it.id == event.id }
                                    ?.timelineOrder
                                    ?.takeIf { it > 0L }
                                current.completePendingReconnect().withAssistantToolEvent(
                                    event = event,
                                    routeIntoReasoning = reasoningTraceToolRoutingEnabled,
                                    nowMillis = now,
                                    nowUptimeMillis = uptime,
                                    timelineOrder = existingTimelineOrder ?: reasoningTracker.nextTimelineOrder(),
                                ).copy(
                                    status = "",
                                    statusDetail = "",
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
                                current.completePendingReconnect().withStartedAssistantTool(
                                    call = call,
                                    startedAtMillis = now,
                                    timelineOrder = reasoningTracker.nextTimelineOrder(),
                                    routeIntoReasoning = reasoningTraceToolRoutingEnabled,
                                ).copy(
                                    status = "",
                                    statusDetail = "",
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
                            if (!shouldApplySharedTurnEvent(target.job, runningJob)) return@runTurn
                            status?.text?.takeIf(String::isNotBlank)
                                ?.let { backgroundLeases[target.id]?.update(it) }
                            target.messages.updateMessage(assistantId) { current ->
                                current.withStreamingStatus(
                                    text = status?.text.orEmpty(),
                                    detail = status?.detail.orEmpty(),
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
                                    captureIosMessageSent(config, target, pending.attachments.size, false, "steer")
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
                        completedPiTurnResult = result
                        analyticsOutcome = if (result.errorMessage.isBlank()) "success" else "failure"
                        val completedAt = platformCurrentTimeMillis()
                        val resolvedUsage = if (result.usageAvailable) result.usage else estimatedUsage
                        target.messages.updateMessage(assistantId) { current ->
                            if (reasoningEffort == "off") current else current.withAssistantResultFallback(result)
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
                                completed.withSharedRequestFailure(result.errorMessage)
                            } else {
                                completed.withAssistantTextResultFallback(result)
                            }
                            val visibleFinalized = if (reasoningEffort == "off") {
                                finalized.withoutSharedAssistantReasoning()
                            } else {
                                finalized
                            }
                            val responseBlocks = visibleFinalized.responseBlocks
                            val hasAgentWork = responseBlocks.any {
                                it is SharedAssistantResponseBlock.Reasoning ||
                                    it is SharedAssistantResponseBlock.ToolGroup
                            }
                            visibleFinalized.copy(
                                reasoningText = if (reasoningEffort == "off") {
                                    ""
                                } else {
                                    current.reasoningText.ifBlank { result.reasoningText }
                                },
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
                                thoughtDurationMillis = if (reasoningEffort == "off") {
                                    0L
                                } else if (hasAgentWork) {
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
                        awaitingFollowUpTour = false
                    },
                    onFailure = { error ->
                        if (error is CancellationException && error !is TimeoutCancellationException) {
                            throw error
                        }
                        analyticsOutcome = "failure"
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
                                .withSharedRequestFailure(sharedFailureMessage(error)).copy(
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
                completedPiTurnResult?.let { result ->
                    historyStore?.upsertAgentSessionMetadata(
                        chatSessionId = target.id,
                        piSessionId = result.piSessionId,
                        jsonlPath = result.piSessionFile,
                        runtime = result.piRuntime,
                    )
                    historyStore?.upsertAgentMessageRefs(
                        chatSessionId = target.id,
                        aetherMessageIds = listOf(userMessage.id, assistantId),
                        piEntryIds = result.piEntryIds,
                    )
                }
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
                val nextIndex = target.queuedTurns.nextSharedQueuedTurnIndex()
                target.job = null
                if (nextIndex >= 0) {
                    val next = target.queuedTurns.removeAt(nextIndex)
                    startChatTurn(next.text, next.attachments, target = target, submissionType = "queue")
                } else {
                    persistSession(target)
                }
                } finally {
                    val analyticsMessage = target.messages.lastOrNull { it.id == assistantId }
                    val analyticsProperties = iosTurnAnalyticsProperties(
                        analyticsMessage, analyticsOutcome, platformCurrentTimeMillis() - turnStartedAt,
                        analyticsInputCount, analyticsUserCount,
                    )
                    IosAnalytics.capture("conversation turn completed", analyticsProperties)
                    if (analyticsMessage?.usage != null) IosAnalytics.capture("tokens used", analyticsProperties)
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
            IosAnalytics.capture("conversation started")
            currentSession.clearComposerDraft()
            val inheritedModelKey = resolveSharedConversationModelKey(
                selectedModelKey = currentSession.selectedModelKey,
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
                selectedModelKey = inheritedModelKey,
            )
            currentSession = state
            sessionId = state.id
            appScope.launch {
                historyStore?.setCurrentSession(state.id)
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
                    val pi = bridgeClient.exportSessionJsonl(state.id)
                    val piPath = pi["exported_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val piJsonl = piPath.takeIf(String::isNotBlank)
                        ?.let { path -> runCatching { runtime.fileSystem.read(path).decodeToString() }.getOrNull() }
                    val exported = Json.parseToJsonElement(json).jsonObject.toMutableMap().apply {
                        put("piSession", buildJsonObject {
                            put("sessionId", state.id)
                            put("jsonlPath", piPath)
                            put("jsonl", piJsonl.orEmpty())
                        })
                    }
                    platformServices.exportFile(
                        fileName,
                        "application/json",
                        JsonObject(exported).toString().encodeToByteArray(),
                    )
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
                    "app.getState", "state.get" -> withContext(Dispatchers.Main) {
                        extensionContext()
                    }
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
                    "app.appendCustomMessage" -> withContext(Dispatchers.Main) {
                        val type = args["type"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                        require(type.isNotBlank()) { "Custom messages require a type." }
                        val payload = args["payload"] as? JsonObject ?: JsonObject(emptyMap())
                        val text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        currentSession.messages += SharedChatMessage(
                            text = text,
                            fromUser = false,
                            customType = type,
                            customPayloadJson = payload.toString(),
                            assistantActionsHidden = true,
                        )
                        persistSession(currentSession)
                        buildJsonObject { put("appended", true); put("type", type) }
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
                        val runningJob = target.job
                        target.job = null
                        runningJob?.cancel()
                        target.streamingStatus = ""
                        target.queuedTurns.clear()
                        val completedAt = platformCurrentTimeMillis()
                        target.messages.lastOrNull { !it.fromUser && it.isStreaming }?.let { pending ->
                            val finalized = pending.finalizeSharedInterruptedAssistantWork(
                                status = chatStoppedStatus,
                                preserveStatus = true,
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
                        if (screen.equals("settings", true)) openSettings() else route = SharedRoute.Chat
                        buildJsonObject { put("opened", screen) }
                    }
                    "app.notify" -> withContext(Dispatchers.Main) {
                        transientMessage = args["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        buildJsonObject { put("notified", transientMessage.isNotBlank()) }
                    }
                    "settings.get" -> withContext(Dispatchers.Main) {
                        buildJsonObject {
                            put("system_prompt", sharedAppSettings.systemPrompt)
                            put("reasoning_effort", sharedAppSettings.reasoningEffort)
                            put("theme", sharedAppSettings.themeMode.storageValue)
                            put("language", sharedAppSettings.language.storageValue)
                            put("provider_configs", JsonArray(providerConfigs.map { it.toJsonObject() }))
                        }
                    }
                    "settings.patch" -> withContext(Dispatchers.Main) {
                        args["system_prompt"]?.jsonPrimitive?.contentOrNull?.let {
                            sharedAppSettings = sharedAppSettings.copy(systemPrompt = it)
                        }
                        args["reasoning_effort"]?.jsonPrimitive?.contentOrNull?.let {
                            sharedAppSettings = sharedAppSettings.copy(reasoningEffort = it)
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

        val extensionManager = remember(extensionBridgeClient) {
            SharedAetherExtensionManager(extensionBridgeClient, ::handleSharedExtensionHostCall)
        }
        val nativePiExtensionsController = remember(
            extensionBridgeClient,
            bridgeClient,
            extensionManager,
            extensionStateStore,
            runtime,
            platformServices,
        ) {
            NativePiExtensionsController(
                extensionBridgeClient = extensionBridgeClient,
                agentBridgeClient = bridgeClient,
                extensionManager = extensionManager,
                extensionStateStore = extensionStateStore,
                runtime = runtime,
                platformServices = platformServices,
            )
        }
        var nativePiExtensionsState by remember { mutableStateOf(NativePiExtensionsState()) }
        var extensionStartupFinished by remember(extensionManager) { mutableStateOf(false) }
        agentExtensionSettingsAccess.readHandler = {
            val current = withContext(Dispatchers.Main) {
                extensionSnapshot.takeIf { extensionSnapshotResolved }
            }
            current ?: extensionManager.refresh(extensionContext()).also { refreshed ->
                withContext(Dispatchers.Main) {
                    extensionSnapshot = refreshed
                    extensionSnapshotResolved = true
                }
            }
        }
        agentExtensionSettingsAccess.updateHandler = { extensionId, settingsId, settingId, value ->
            extensionManager.invokeAction(
                extensionId = extensionId,
                action = "settings:$settingsId:$settingId",
                args = buildJsonObject {
                    put("setting", settingId)
                    put("value", value)
                },
                context = withContext(Dispatchers.Main) { extensionContext() },
            ).also { updated ->
                withContext(Dispatchers.Main) {
                    extensionSnapshot = updated
                    extensionSnapshotResolved = true
                }
            }
        }
        val piExtensionUiRequest by extensionManager.piUiRequest.collectAsState()
        val extensionDraftRefreshJob = remember(extensionManager) { SharedNonSnapshotJobSlot() }

        fun scheduleExtensionDraftRefresh() {
            if (!capabilities.scriptExtensions || route == SharedRoute.Onboarding) return
            extensionDraftRefreshJob.job?.cancel()
            extensionDraftRefreshJob.job = appScope.launch {
                kotlinx.coroutines.delay(250)
                runSharedAppCatching { extensionManager.refresh(extensionContext()) }
                    .onSuccess {
                        extensionSnapshot = it
                        extensionSnapshotResolved = true
                    }
            }
        }

        DisposableEffect(extensionManager) {
            onDispose { extensionDraftRefreshJob.job?.cancel() }
        }

        LaunchedEffect(extensionManager) {
            extensionManagerRef = extensionManager
        }

        LaunchedEffect(
            extensionManager,
            capabilities.scriptExtensions,
            route,
            extensionStartupFinished,
        ) {
            if (!capabilities.scriptExtensions || route == SharedRoute.Onboarding ||
                !extensionStartupFinished
            ) {
                return@LaunchedEffect
            }
            while (true) {
                try {
                    extensionManager.subscribe {
                        val context = withContext(Dispatchers.Main) { extensionContext() }
                        val refreshed = extensionManager.refresh(context)
                        withContext(Dispatchers.Main) {
                            extensionSnapshot = refreshed
                            extensionSnapshotResolved = true
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    SharedDiagnosticLogger.event(
                        category = "aether_extension",
                        event = "subscription_failed",
                        level = "warn",
                        details = mapOf("error" to failure.message.orEmpty()),
                    )
                    kotlinx.coroutines.delay(2_000)
                }
            }
        }

        LaunchedEffect(
            startupResolved,
            extensionManager,
            nativePiExtensionsController,
            capabilities.scriptExtensions,
            route,
        ) {
            if (!startupResolved || !capabilities.scriptExtensions || route == SharedRoute.Onboarding) {
                return@LaunchedEffect
            }
            if (!extensionRuntimeReady &&
                !runSharedAppCatching { runtime.isReady() }.getOrDefault(false)
            ) {
                return@LaunchedEffect
            }

            // Settings summaries and app-extension registrations share this app-level startup.
            // Neither should depend on visiting the Pi Extensions settings screen.
            val context = extensionContext()
            coroutineScope {
                val installedRequest = async {
                    nativePiExtensionsController.refreshInstalled(nativePiExtensionsState)
                }
                val registrationRequest = async {
                    runSharedAppCatching { extensionManager.reload(context) }
                }
                nativePiExtensionsState = installedRequest.await()
                registrationRequest.await().onSuccess {
                    extensionSnapshot = it
                    extensionSnapshotResolved = true
                }
            }
            extensionStartupFinished = true
        }

        LaunchedEffect(extensionStartupFinished, bridgeClient) {
            if (!extensionStartupFinished) return@LaunchedEffect
            kotlinx.coroutines.delay(250)
            runSharedAppCatching { bridgeClient.ping() }
        }

        LaunchedEffect(extensionManager) {
            extensionManager.notifications.collect { notification ->
                transientMessage = notification.message
            }
        }

        LaunchedEffect(
            route,
            currentSession.id,
            currentSession.isWorking,
            currentSession.selectedModelKey,
            currentSession.messages.size,
            capabilities.scriptExtensions,
            extensionStartupFinished,
        ) {
            if (capabilities.scriptExtensions && route != SharedRoute.Onboarding &&
                extensionStartupFinished
            ) {
                runSharedAppCatching { extensionManager.refresh(extensionContext()) }
                    .onSuccess {
                        extensionSnapshot = it
                        extensionSnapshotResolved = true
                    }
            }
        }

        val extensionController = SharedAetherExtensionUiController(
            snapshot = extensionSnapshot,
            onAction = { extensionId, action, args ->
                appScope.launch {
                    runSharedAppCatching {
                        extensionManager.invokeAction(extensionId, action, args, extensionContext())
                    }
                        .onSuccess {
                            extensionSnapshot = it
                            extensionSnapshotResolved = true
                        }
                }
            },
        )
        val nativeSettingsSnapshot = buildNativeSettingsSnapshot(
            settings = sharedAppSettings,
            providerConfigs = providerConfigs,
            installedSkills = installedSkills,
            extensionSnapshot = extensionSnapshot,
            capabilities = capabilities,
            statistics = nativeStatisticsReport,
            providerModels = nativeProviderModels,
            providerOperation = nativeProviderOperation,
            providerCompletedRequestId = nativeProviderCompletedRequestId,
            providerAuthSessionId = nativeProviderAuthSessionId,
            providerError = nativeProviderError,
            providerAuthState = nativeProviderAuthState,
            operationMessage = nativeOperationMessage,
            operationError = nativeOperationError,
            operation = nativeOperation,
            piExtensions = nativePiExtensionsState,
            alpine = nativeAlpineState,
        )
        SideEffect {
            nativeSettingsHost?.publishSnapshot(
                nativeSettingsSnapshot,
            )
        }
        DisposableEffect(nativeSettingsHost, extensionManager, skillManager) {
            nativeSettingsHost?.setCommandHandler(object : NativeSettingsCommandHandler {
                override fun handle(command: String, payloadJson: String) {
                    val payload = runCatching {
                        Json.parseToJsonElement(payloadJson) as? JsonObject
                    }.getOrNull() ?: JsonObject(emptyMap())
                    when (command) {
                        "update_settings" -> persistResolvedAppSettings(
                            sharedAppSettings.withNativeSettingsPatch(payload),
                        )
                        "provider_upsert" -> parseProviderConfigs("[$payloadJson]")
                            .firstOrNull()
                            ?.let(::upsertProviderConfig)
                        "provider_enabled" -> setProviderEnabled(
                            payload.nativeString("id"),
                            payload.nativeBoolean("enabled") ?: false,
                        )
                        "provider_remove" -> removeProviderConfig(payload.nativeString("id"))
                        "provider_fetch_models" -> appScope.launch {
                            val config = parseProviderConfigs("[$payloadJson]").firstOrNull()
                                ?: return@launch
                            nativeProviderOperation = "fetch_models:${config.id}"
                            nativeProviderError = ""
                            nativeProviderModels = nativeProviderModels - config.id
                            try {
                                val result = modelCatalogClient.fetchModels(config)
                                nativeProviderModels = nativeProviderModels + (config.id to result.models)
                                nativeProviderError = result.error.orEmpty()
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: Throwable) {
                                nativeProviderError = failure.sharedUserFacingMessage()
                            } finally {
                                nativeProviderOperation = ""
                                nativeProviderCompletedRequestId = payload.nativeString("requestId")
                            }
                        }
                        "provider_login" -> {
                            nativeProviderAuthJob?.cancel()
                            val sessionId = payload.nativeString("sessionId")
                            nativeProviderAuthSessionId = sessionId
                            nativeAuthenticationCallback = ""
                            nativeProviderAuthJob = appScope.launch {
                                val configId = payload.nativeString("id")
                                val providerId = payload.nativeString("providerId")
                                val authMethod = ProviderAuthMethod.fromStorage(
                                    payload.nativeString("authMethod"),
                                )
                                if (providerId.isBlank() || authMethod == ProviderAuthMethod.Ambient) {
                                    return@launch
                                }
                                nativeProviderAuthState = PiProviderAuthState(
                                    providerId = providerId,
                                    authMethod = authMethod,
                                    isRunning = true,
                                    statusMessage = if (authMethod == ProviderAuthMethod.OAuth) {
                                        "Waiting for authorization."
                                    } else {
                                        "Waiting for credentials."
                                    },
                                )
                                runSharedAppCatching {
                                    bridgeClient.loginProvider(
                                        providerConfigId = configId,
                                        providerId = providerId,
                                        authMethod = authMethod.storageValue,
                                        oauthFlow = payload.nativeString("oauthFlow"),
                                    ) { event, eventPayload ->
                                        if (nativeProviderAuthSessionId != sessionId) return@loginProvider
                                        nativeProviderAuthState = nativeProviderAuthState.withBridgeAuthEvent(
                                            event = event,
                                            payload = eventPayload,
                                            completeAuthorizationMessage = "Complete authorization in your browser.",
                                            enterDeviceCodeMessage = "Enter the device code in your browser.",
                                        )
                                    }
                                }.fold(
                                    onSuccess = { result ->
                                        if (nativeProviderAuthSessionId != sessionId) return@fold
                                        nativeProviderAuthState = nativeProviderAuthState.copy(
                                            isRunning = false,
                                            prompt = null,
                                            authorizationUrl = "",
                                            verificationUrl = "",
                                            deviceCode = "",
                                            apiKey = result.string("api_key"),
                                            oauthCredentialJson = (result["oauth_credential"] as? JsonObject)
                                                ?.toString().orEmpty(),
                                            providerEnvironmentVariables = result.toPiProviderEnvironmentVariables(),
                                            statusMessage = "Authentication completed.",
                                            errorMessage = "",
                                        )
                                    },
                                    onFailure = { failure ->
                                        if (failure !is CancellationException && nativeProviderAuthSessionId == sessionId) {
                                            nativeProviderAuthState = nativeProviderAuthState.copy(
                                                isRunning = false,
                                                prompt = null,
                                                statusMessage = "",
                                                errorMessage = failure.sharedUserFacingMessage(),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        "provider_auth_prompt" -> appScope.launch {
                            val sessionId = payload.nativeString("sessionId")
                            val promptId = payload.nativeString("promptId")
                            if (sessionId != nativeProviderAuthSessionId || promptId != nativeProviderAuthState.prompt?.id) return@launch
                            runSharedAppCatching {
                                bridgeClient.submitAuthPrompt(
                                    promptId = payload.nativeString("promptId"),
                                    value = payload.nativeString("value"),
                                    cancelled = payload.nativeBoolean("cancelled") ?: false,
                                )
                            }.onSuccess {
                                if (sessionId == nativeProviderAuthSessionId && promptId == nativeProviderAuthState.prompt?.id) {
                                    nativeProviderAuthState = nativeProviderAuthState.copy(prompt = null)
                                }
                            }.onFailure { failure ->
                                if (sessionId == nativeProviderAuthSessionId) {
                                    nativeProviderAuthState = nativeProviderAuthState.copy(
                                        errorMessage = failure.sharedUserFacingMessage(),
                                    )
                                }
                            }
                        }
                        "provider_open_auth_url" -> {
                            val sessionId = nativeProviderAuthSessionId
                            val url = nativeProviderAuthState.authorizationUrl
                            if (url.isNotBlank()) {
                                val opened = platformServices.openAuthenticationUrl(
                                    url = url,
                                    onCallback = { callback ->
                                        if (sessionId == nativeProviderAuthSessionId) {
                                            appScope.launch { nativeAuthenticationCallback = callback }
                                        }
                                    },
                                    onCancelled = {
                                        nativeProviderAuthState.prompt?.id?.takeIf { sessionId == nativeProviderAuthSessionId }
                                            ?.takeIf(String::isNotBlank)
                                            ?.let { promptId ->
                                                appScope.launch {
                                                    runSharedAppCatching {
                                                        bridgeClient.submitAuthPrompt(
                                                            promptId,
                                                            "",
                                                            true,
                                                        )
                                                    }
                                                }
                                            }
                                    },
                                )
                                if (!opened) platformServices.openUrl(url)
                            }
                        }
                        "provider_clear_auth" -> {
                            if (payload.nativeString("sessionId") == nativeProviderAuthSessionId) {
                                val promptId = nativeProviderAuthState.prompt?.id
                                nativeProviderAuthSessionId = ""
                                nativeAuthenticationCallback = ""
                                nativeProviderAuthJob?.cancel()
                                nativeProviderAuthJob = null
                                nativeProviderAuthState = PiProviderAuthState()
                                if (!promptId.isNullOrBlank()) appScope.launch {
                                    runSharedAppCatching { bridgeClient.submitAuthPrompt(promptId, "", true) }
                                }
                            }
                        }
                        "clear_operation_status" -> {
                            nativeOperationMessage = ""
                            nativeOperationError = ""
                        }
                        "skill_enabled" -> appScope.launch {
                            nativeOperation = "skill_enabled"
                            nativeOperationError = ""
                            runSharedAppCatching {
                                skillManager.setEnabled(
                                    payload.nativeString("id"),
                                    payload.nativeBoolean("enabled") ?: false,
                                )
                                installedSkills.clear()
                                installedSkills.addAll(skillManager.list())
                                retainEnabledSkillSelections(
                                    installedSkills.filter(SharedInstalledSkill::isEnabled)
                                        .map(SharedInstalledSkill::id)
                                        .toSet(),
                                )
                            }.onFailure { nativeOperationError = it.sharedUserFacingMessage() }
                            nativeOperation = ""
                        }
                        "skill_remove" -> appScope.launch {
                            nativeOperation = "skill_remove"
                            nativeOperationError = ""
                            runSharedAppCatching {
                                skillManager.remove(payload.nativeString("id"))
                                val updated = skillManager.list()
                                installedSkills.clear()
                                installedSkills.addAll(updated)
                                retainEnabledSkillSelections(
                                    updated.filter(SharedInstalledSkill::isEnabled)
                                        .map(SharedInstalledSkill::id)
                                        .toSet(),
                                )
                                sessionStates.values.filterNot(SharedSessionUiState::isDraft)
                                    .forEach { state -> bridgeClient.reloadSession(state.id) }
                            }.onSuccess {
                                IosAnalytics.capture("skill removed", mapOf("skill_id" to payload.nativeString("id")))
                                nativeOperationMessage = "Skill removed."
                            }
                                .onFailure { nativeOperationError = it.sharedUserFacingMessage() }
                            nativeOperation = ""
                        }
                        "skill_install_url" -> appScope.launch {
                            nativeOperation = "skill_install"
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val installed = skillManager.installRemote(payload.nativeString("url"))
                                IosAnalytics.capture("skill installed", mapOf("skill_id" to installed.id, "skill_name" to installed.name))
                                val updated = skillManager.list()
                                installedSkills.clear()
                                installedSkills.addAll(updated)
                                sessionStates.values.filterNot(SharedSessionUiState::isDraft)
                                    .forEach { state -> bridgeClient.reloadSession(state.id) }
                            }.onSuccess { nativeOperationMessage = "Skill installed." }
                                .onFailure { nativeOperationError = it.sharedUserFacingMessage() }
                            nativeOperation = ""
                        }
                        "skill_install_directory" -> appScope.launch {
                            nativeOperation = "skill_install"
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val picked = platformServices.pickDirectory() ?: return@runSharedAppCatching
                                val installed = skillManager.installDirectoryEntries(
                                    sourceLabel = picked.name,
                                    entries = picked.files.map { file ->
                                        SharedSkillDirectoryEntry(file.relativePath, file.bytes)
                                    },
                                )
                                IosAnalytics.capture("skill installed", mapOf("skill_id" to installed.id, "skill_name" to installed.name))
                                val updated = skillManager.list()
                                installedSkills.clear()
                                installedSkills.addAll(updated)
                                sessionStates.values.filterNot(SharedSessionUiState::isDraft)
                                    .forEach { state -> bridgeClient.reloadSession(state.id) }
                            }.onSuccess { nativeOperationMessage = "Skill installed." }
                                .onFailure { nativeOperationError = it.sharedUserFacingMessage() }
                            nativeOperation = ""
                        }
                        "skill_install_zip" -> appScope.launch {
                            nativeOperation = "skill_install"
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val picked = platformServices.pickFile(false) ?: return@runSharedAppCatching
                                val archive = "${runtime.workspaceRoot}/.skill-${platformRandomUuid()}.zip"
                                runtime.fileSystem.write(archive, picked.bytes)
                                try {
                                    skillManager.installArchive(archive, sourceLabel = picked.name)
                                } finally {
                                    withContext(NonCancellable) {
                                        runCatching { runtime.fileSystem.remove(archive) }
                                    }
                                }
                                val updated = skillManager.list()
                                installedSkills.clear()
                                installedSkills.addAll(updated)
                                sessionStates.values.filterNot(SharedSessionUiState::isDraft)
                                    .forEach { state -> bridgeClient.reloadSession(state.id) }
                            }.onSuccess { nativeOperationMessage = "Skill installed." }
                                .onFailure { nativeOperationError = it.sharedUserFacingMessage() }
                            nativeOperation = ""
                        }
                        "extension_setting" -> appScope.launch {
                            val value = payload["value"] ?: JsonNull
                            extensionManager.invokeAction(
                                extensionId = payload.nativeString("extension_id"),
                                action = "settings:${payload.nativeString("settings_id")}:${payload.nativeString("setting_id")}",
                                args = buildJsonObject {
                                    put("setting", payload.nativeString("setting_id"))
                                    put("value", value)
                                },
                                context = extensionContext(),
                            ).also { updated ->
                                extensionSnapshot = updated
                                extensionSnapshotResolved = true
                            }
                        }
                        "extension_action" -> appScope.launch {
                            extensionManager.invokeAction(
                                extensionId = payload.nativeString("extension_id"),
                                action = payload.nativeString("action"),
                                args = payload["args"] as? JsonObject ?: JsonObject(emptyMap()),
                                context = extensionContext(),
                            ).also { updated ->
                                extensionSnapshot = updated
                                extensionSnapshotResolved = true
                            }
                        }
                        "pi_extensions_refresh" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "refresh",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.refresh(
                                nativePiExtensionsState,
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "pi_extensions_discover" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "catalog",
                                catalogError = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.refreshCatalog(
                                nativePiExtensionsState,
                            )
                        }
                        "pi_extension_details" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "details",
                                details = null,
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.fetchDetails(
                                nativePiExtensionsState,
                                payload.nativeString("source"),
                            )
                        }
                        "pi_extension_install" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "install",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.install(
                                nativePiExtensionsState,
                                payload.nativeString("source"),
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "pi_extension_update" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "update",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.update(
                                nativePiExtensionsState,
                                payload.nativeString("id"),
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "pi_extension_remove" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "remove",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.remove(
                                nativePiExtensionsState,
                                payload.nativeString("id"),
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "pi_extension_enabled" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "enabled",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.setEnabled(
                                nativePiExtensionsState,
                                payload.nativeString("id"),
                                payload.nativeBoolean("enabled") ?: false,
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "pi_extension_import" -> appScope.launch {
                            nativePiExtensionsState = nativePiExtensionsState.copy(
                                operation = "import",
                                message = "",
                                error = "",
                            )
                            nativePiExtensionsState = nativePiExtensionsController.import(
                                nativePiExtensionsState,
                            )
                            extensionSnapshot = extensionManager.snapshot
                            extensionSnapshotResolved = true
                        }
                        "alpine_refresh" -> appScope.launch {
                            nativeAlpineState = nativeAlpineState.copy(operation = "refresh", detail = "")
                            val result = nativeAlpineController.refresh(sharedAppSettings)
                            nativeAlpineState = result.state
                            if (result.settings != sharedAppSettings) persistResolvedAppSettings(result.settings)
                        }
                        "alpine_initialize" -> appScope.launch {
                            nativeAlpineState = nativeAlpineState.copy(operation = "initialize", detail = "")
                            val result = nativeAlpineController.initialize(sharedAppSettings)
                            nativeAlpineState = result.state
                            if (result.settings != sharedAppSettings) persistResolvedAppSettings(result.settings)
                        }
                        "alpine_reset" -> appScope.launch {
                            nativeAlpineState = nativeAlpineState.copy(operation = "reset", detail = "")
                            val result = nativeAlpineController.reset(sharedAppSettings)
                            nativeAlpineState = result.state
                            if (result.settings != sharedAppSettings) persistResolvedAppSettings(result.settings)
                        }
                        "alpine_set_default" -> {
                            persistResolvedAppSettings(
                                sharedAppSettings.copy(
                                    defaultRuntimeId = LocalRuntimeId.Alpine,
                                    enabledRuntimeIds = sharedAppSettings.enabledRuntimeIds + LocalRuntimeId.Alpine,
                                )
                            )
                        }
                        "alpine_install_profile" -> appScope.launch {
                            val profileId = payload.nativeString("profileId")
                            nativeAlpineState = nativeAlpineState.copy(
                                operation = "install:$profileId",
                                detail = "",
                                progress = "Preparing packages...",
                            )
                            val result = nativeAlpineController.installProfile(
                                settings = sharedAppSettings,
                                profileId = profileId,
                                onProgress = { progress ->
                                    nativeAlpineState = nativeAlpineState.copy(progress = progress)
                                },
                            )
                            nativeAlpineState = result.state
                            if (result.settings != sharedAppSettings) persistResolvedAppSettings(result.settings)
                        }
                        "alpine_open_files" -> platformServices.openAlpineFileManager()
                        "refresh_statistics" -> appScope.launch {
                            nativeStatisticsReport = if (historyStore != null) {
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
                        }
                        "developer_clear_developer_role_fallback" -> {
                            val configId = payload.nativeString("providerConfigId")
                            commitProviderConfigs(
                                providerConfigs.map { config ->
                                    if (config.id == configId) {
                                        config.copy(
                                            developerRoleUnsupported = false,
                                            updatedAtMillis = platformCurrentTimeMillis(),
                                        )
                                    } else {
                                        config
                                    }
                                }
                            )
                        }
                        "developer_export_data" -> appScope.launch {
                            nativeOperationMessage = ""
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val manager = checkNotNull(appDataManager) {
                                    "App data storage is unavailable."
                                }
                                settingsStore?.saveGeneralSettings(sharedAppSettings)
                                settingsStore?.saveProviders(
                                    providerConfigs.toList(),
                                    providerConfig?.id.orEmpty(),
                                )
                                persistSession()
                                historyStore?.setCurrentSession(sessionId)
                                val data = manager.exportJson().encodeToByteArray()
                                platformServices.exportFile(
                                    "aether-backup.json",
                                    "application/json",
                                    data,
                                )
                            }.onSuccess { exported ->
                                if (exported == true) nativeOperationMessage = "App data exported."
                            }.onFailure { failure ->
                                nativeOperationError = failure.sharedUserFacingMessage()
                            }
                        }
                        "developer_import_data" -> appScope.launch {
                            nativeOperationMessage = ""
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val picked = platformServices.pickFile(false)
                                    ?: return@runSharedAppCatching null
                                val manager = checkNotNull(appDataManager) {
                                    "App data storage is unavailable."
                                }
                                manager.restoreJson(picked.bytes.decodeToString())
                            }.onSuccess { restored ->
                                if (restored != null) {
                                    val persisted = restored.persistedSettings
                                    sharedAppSettings = persisted.appSettings
                                    providerConfigs.clear()
                                    providerConfigs.addAll(persisted.providerConfigs)
                                    providerConfig = persisted.activeProviderConfig
                                    installedSkills.clear()
                                    installedSkills.addAll(restored.installedSkills)
                                    mcpServers.clear()
                                    sessionStates.clear()
                                    sessions.clear()
                                    restored.sessions.forEach { persistedSession ->
                                        val state = persistedSession.toSharedSessionUiState()
                                        sessionStates[state.id] = state
                                        sessions += SharedConversationSummary(state.id, state.title)
                                    }
                                    val restoredCurrent = restored.currentSessionId
                                        ?.takeUnless { it == SharedDraftSessionId }
                                        ?.let(sessionStates::get)
                                        ?: SharedSessionUiState(
                                            id = SharedDraftSessionId,
                                            isDraft = true,
                                            selectedModelKey = persisted.appSettings.defaultChatModelKey,
                                        )
                                    currentSession = restoredCurrent
                                    sessionId = restoredCurrent.id
                                    historyStore?.setCurrentSession(restoredCurrent.id)
                                    chromeEnabled = false
                                    chromeManager.enabled = false
                                    appScope.launch {
                                        runSharedAppCatching {
                                            extensionManager.refresh(extensionContext())
                                        }.onSuccess { refreshed ->
                                            extensionSnapshot = refreshed
                                            extensionSnapshotResolved = true
                                        }
                                    }
                                    nativeOperationMessage = "App data imported."
                                }
                            }.onFailure { failure ->
                                nativeOperationError = failure.sharedUserFacingMessage()
                            }
                        }
                        "developer_export_logs" -> appScope.launch {
                            nativeOperationMessage = ""
                            nativeOperationError = ""
                            runSharedAppCatching {
                                val text = buildSharedDiagnosticLogText(
                                    appVersion = platformAppVersion(),
                                    route = route,
                                    currentSession = currentSession,
                                    sessionStates = sessionStates.values,
                                    providerConfigs = providerConfigs,
                                    installedSkillCount = installedSkills.size,
                                    settings = sharedAppSettings,
                                )
                                platformServices.exportFile(
                                    "aether-diagnostics.txt",
                                    "text/plain",
                                    text.encodeToByteArray(),
                                )
                            }.onSuccess { exported ->
                                if (exported == true) nativeOperationMessage = "Diagnostic logs exported."
                            }.onFailure { failure ->
                                nativeOperationError = failure.sharedUserFacingMessage()
                            }
                        }
                        "developer_replay_alpine" -> {
                            alpineSetupPreviewVisible = true
                        }
                        "developer_replay_follow_up" -> {
                            onboardingReplayMode = true
                            onboardingEntryStage = OnboardingStage.Runtime
                            route = SharedRoute.Onboarding
                        }
                    }
                }
            })
            onDispose { nativeSettingsHost?.setCommandHandler(null) }
        }
        val pauseBeforeDeletingSessionMessage =
            stringResource(Res.string.message_pause_before_deleting_session)

        SharedAetherExtensionUiProvider(extensionController) {
        Box(Modifier.fillMaxSize()) {
        SharedAetherExtensionComponentHost(
            target = SharedExtensionComponentAppContent,
            modifier = Modifier.fillMaxSize(),
        ) {
        BoxWithConstraints {
        val useTabletLayout = shouldUseSharedTabletLayout(
            supportsTabletLayout = capabilities.supportsTabletLayout,
            availableWidthDp = maxWidth.value,
        )
        AnimatedContent(
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
                    platformServices = platformServices,
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
                        val returnToSettings = onboardingReplayMode
                        onboardingReplayMode = false
                        onboardingEntryStage = OnboardingStage.Landing
                        if (returnToSettings) openSettings() else route = SharedRoute.Chat
                    },
                    onComplete = { configured ->
                        val enabledConfig = configured.copy(isEnabled = true)
                        val onboardingProperties = mapOf(
                            "section" to "initial",
                            "provider" to PiProviderCatalog.resolve(enabledConfig.piProviderId).displayName,
                            "provider_id" to enabledConfig.id,
                        )
                        IosAnalytics.capture("onboarding completed", onboardingProperties)
                        IosAnalytics.capture("onboarding initial completed", onboardingProperties)
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
                )
                SharedRoute.Chat -> SharedAetherExtensionComponentHost(
                    target = SharedExtensionComponentChatScreen,
                    modifier = Modifier.fillMaxSize(),
                ) {
                CompositionLocalProvider(LocalShowcaseControls provides if (showcaseEnabled && com.zhousl.aether.data.ShowcaseCatalog.isSession(sessionId)) ShowcaseControls(
                    playing = currentSession.isWorking,
                    paused = sessionId in showcasePausedSessions,
                    speed = showcaseSpeed,
                    onReplay = { replayShowcase() },
                    onRestore = { replayShowcase(restoreOnly = true) },
                    onPause = {
                        showcasePlayers[sessionId]?.let { player ->
                            player.paused = !player.paused
                            showcasePausedSessions = if (player.paused) {
                                showcasePausedSessions + sessionId
                            } else {
                                showcasePausedSessions - sessionId
                            }
                        }
                    },
                    onSpeed = { speed -> showcaseSpeed = speed; showcasePlayers[sessionId]?.speed = speed },
                ) else null) {
                Box(Modifier.fillMaxSize()) {
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
                        val selectionChanged = if (selected) {
                            if (skillId !in selectedSkillIds) {
                                selectedSkillIds += skillId
                                true
                            } else {
                                false
                            }
                        } else {
                            selectedSkillIds.remove(skillId)
                        }
                        if (!selected) {
                            currentSession.activeSkills.removeAll {
                                it.skillId !in currentSession.selectedSkillIds
                            }
                        }
                        val settingsToSave = if (selectionChanged && currentSession.isDraft) {
                            sharedAppSettings.copy(
                                defaultSelectedSkillIds = selectedSkillIds.toList(),
                            ).also { sharedAppSettings = it }
                        } else {
                            null
                        }
                        appScope.launch {
                            settingsToSave?.let { settingsStore?.saveGeneralSettings(it) }
                            persistSession()
                        }
                    },
                    mcpServers = mcpServers.filter { it.enabled },
                    activeMcpServerIds = activeMcpServerIds,
                    onMcpServerSelected = { _, _ -> },
                    chromeAvailable = capabilities.alpineChrome,
                    chromeEnabled = chromeEnabled,
                    chromeManager = chromeManager,
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
                            piBranchMessageId = plan.piBranchMessageId,
                            resetPiBranchWhenMissing = true,
                        )
                    },
                    onRetryUserMessage = { messageId ->
                        if (currentSession.isWorking) return@SharedChatScreen
                        val original = messages.firstOrNull { it.id == messageId && it.fromUser }
                            ?: return@SharedChatScreen
                        val originalIndex = messages.indexOfFirst { it.id == messageId }
                        val piBranchMessageId = messages.take(originalIndex).lastOrNull()?.id
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
                            piBranchMessageId = piBranchMessageId,
                            resetPiBranchWhenMissing = true,
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
                        val runningJob = currentSession.job
                        currentSession.job = null
                        runningJob?.cancel()
                        currentSession.streamingStatus = ""
                        currentSession.queuedTurns.clear()
                        messages.lastOrNull { !it.fromUser && it.isStreaming }?.let { pending ->
                            val finalized = pending.finalizeSharedInterruptedAssistantWork(
                                status = chatStoppedStatus,
                                preserveStatus = true,
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
                            IosAnalytics.capture("conversation deleted")
                            removeSharedUnreferencedWorkspaceFiles(runtime, unreferencedPaths)
                        }
                    },
                    onExportSession = ::exportSession,
                    onOpenSettings = ::openSettings,
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
                }
                }
                }
            }
        }
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
        if (!startupResolved) {
            Box(Modifier.fillMaxSize().background(AetherBackground).testTag("aether-startup-loading"))
        } else if (!sharedAppSettings.privacyPolicyAccepted && !showcaseEnabled) {
            SharedPrivacyPolicyConsentDialog(
                onOpenPolicy = {
                    if (!platformServices.openUrl(AetherPrivacyPolicyUrl)) {
                        transientMessage = unableToOpenLinkMessage
                    }
                },
                onAccept = {
                    appScope.launch {
                        settingsStore?.acceptPrivacyPolicy()
                        IosAnalytics.acceptConsent()
                        sharedAppSettings = sharedAppSettings.copy(privacyPolicyAccepted = true)
                    }
                },
                onDecline = { platformServices.terminateApplication() },
            )
        } else if (alpineSetupPreviewVisible) {
            RuntimeSetupStep(
                runtime = runtime,
                bridgeClient = bridgeClient,
                onBack = { alpineSetupPreviewVisible = false },
                onClose = { alpineSetupPreviewVisible = false },
                onContinue = { alpineSetupPreviewVisible = false },
            )
        }
        piExtensionUiRequest?.let { request ->
            SharedPiExtensionUiDialog(
                request = request,
                onResult = { value ->
                    appScope.launch {
                        extensionManager.respondToPiExtensionUiRequest(request.callId, value)
                    }
                },
            )
        }
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
private fun SharedPiExtensionUiDialog(
    request: SharedPiExtensionUiRequest,
    onResult: (JsonPrimitive?) -> Unit,
) {
    var input by remember(request.callId) { mutableStateOf("") }
    val dismissValue = if (request.method == "pi_extension_confirm") JsonPrimitive(false) else null
    AlertDialog(
        onDismissRequest = { onResult(dismissValue) },
        containerColor = AetherSurface,
        titleContentColor = AetherOnSurface,
        textContentColor = AetherOnSurfaceVariant,
        title = { Text(request.title) },
        text = {
            when (request.method) {
                "pi_extension_select" -> Column {
                    request.options.forEach { option ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onResult(JsonPrimitive(option)) },
                        ) {
                            Text(option, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                "pi_extension_input" -> OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = request.placeholder.takeIf(String::isNotBlank)?.let { placeholder ->
                        { Text(placeholder) }
                    },
                    singleLine = true,
                )

                else -> Text(request.message)
            }
        },
        confirmButton = {
            if (request.method != "pi_extension_select") {
                TextButton(
                    onClick = {
                        onResult(
                            if (request.method == "pi_extension_confirm") {
                                JsonPrimitive(true)
                            } else {
                                JsonPrimitive(input)
                            },
                        )
                    },
                ) {
                    Text(stringResource(Res.string.common_done))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(dismissValue) }) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
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
                withStyle(SpanStyle(color = Color(0xFF3B82F6))) { append(policyText) }
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
        appendLine()
        appendLine("settingsSummary:")
        appendLine(buildSharedSettingsDiagnosticSummary(settings).toSharedDiagnosticText())
        appendLine()
        appendLine("providerConfigsSummary:")
        appendLine(buildSharedProviderConfigsDiagnosticSummary(providerConfigs).toSharedDiagnosticText())
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
            is SharedAssistantResponseBlock.Status -> Unit
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
    platformServices: PlatformServices,
    existingProviderConfig: LlmProviderConfig?,
    replayMode: Boolean,
    onTransientMessage: (String) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onComplete: (LlmProviderConfig) -> Unit,
    initialStage: OnboardingStage = OnboardingStage.Landing,
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
    val oauthWaitingMessage = "Waiting for authorization."
    val credentialsWaitingMessage = "Waiting for credentials."
    val oauthConnectedMessage = "Connected with OAuth."
    val apiKeyConfiguredMessage = "API key configured."
    val completeAuthorizationMessage = "Complete authorization in your browser."
    val enterDeviceCodeMessage = "Enter the device code in your browser."
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
                        val result = modelCatalogClient.fetchModels(config)
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
                                failure.message.orEmpty().ifBlank { "Unknown error." },
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
        if (retryKey == 0 && !installed) return@LaunchedEffect
        if (installed) running = true

        try {
            if (!installed) {
                progress = RuntimeSetupProgress(
                    phase = RuntimePhaseCheckingAlpine,
                    output = progress.output,
                )
                runtime.initialize { update ->
                    progress = update.copy(phase = normalizeRuntimeSetupPhase(update.phase))
                }
                alpineReady = true
            }
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
                        ready -> Res.string.continue_label
                        error.isNotBlank() -> Res.string.retry_label
                        retryKey == 0 -> Res.string.settings_initialize
                        else -> Res.string.onboarding_pi_setup_working
                    },
                ),
                onPrimary = if (ready) onContinue else ({ retryKey += 1 }),
                primaryEnabled = !running,
                primaryLoading = running,
                secondaryLabel = stringResource(
                    if (ready) Res.string.common_refresh else Res.string.back_label,
                ),
                onSecondary = if (ready) ({ retryKey += 1 }) else onBack,
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
    var organicFraction by remember { mutableStateOf(0f) }
    var previousError by remember { mutableStateOf("") }
    val phaseFraction = currentStep / 5f
    LaunchedEffect(ready, error, progress.phase) {
        val restarting = previousError.isNotBlank() && error.isBlank()
        previousError = error
        if (restarting) organicFraction = phaseFraction
        organicFraction = maxOf(organicFraction, phaseFraction)
        if (ready) {
            organicFraction = 1f
        } else if (error.isBlank()) {
            while (true) {
                kotlinx.coroutines.delay(RuntimeSetupProgressTickMillis)
                val remaining = 0.94f - organicFraction
                if (remaining > 0f) {
                    organicFraction = (
                        organicFraction + (remaining * 0.018f).coerceIn(0.001f, 0.006f)
                    ).coerceAtMost(0.94f)
                }
            }
        }
    }
    val animatedFraction by animateFloatAsState(
        targetValue = organicFraction,
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
            if (organicFraction > 0f) {
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

@Composable
private fun SharedSlashCommandSuggestionRow(
    suggestion: SlashCommandSuggestion,
    detail: String,
    input: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = when (suggestion.icon) {
                SlashCommandIcon.Skill -> Icons.Rounded.AutoAwesome
                SlashCommandIcon.Extension -> Icons.Rounded.Extension
                SlashCommandIcon.Command -> Icons.Rounded.Compress
            },
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = slashHighlightedName(suggestion.command, input),
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
        Text(
            text = detail,
            color = AetherOnSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
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
    chromeManager: SharedChromeManager,
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    val reduceMotion = LocalReduceMotion.current
    val browserDisplayState by chromeManager.displayState.collectAsState()
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
    val timelineTargets = remember(visibleMessages) {
        buildSharedConversationTimelineTargets(visibleMessages)
    }
    val currentTimelineIndex by remember(listState, timelineTargets) {
        derivedStateOf {
            if (timelineTargets.isEmpty()) {
                0
            } else {
                val layout = listState.layoutInfo
                val firstVisibleIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
                val probe = layout.viewportStartOffset +
                    (layout.viewportEndOffset - layout.viewportStartOffset) * 0.28f
                timelineTargets.indexOfLast { target ->
                    target.listItemIndex < firstVisibleIndex ||
                        layout.visibleItemsInfo.firstOrNull { it.index == target.listItemIndex }
                            ?.let { it.offset <= probe } == true
                }.coerceAtLeast(0)
            }
        }
    }
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
    val conversationContentKey = remember(
        visibleMessages,
        pendingTurns,
        streamingStatus,
        browserDisplayState.lastUpdatedMillis,
    ) {
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
            append("|browser:")
            append(browserDisplayState.lastUpdatedMillis)
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
        imeBottom,
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
                },
            )
        },
    ) {
        Scaffold(
            // Resize only the conversation pane, using the platform's IME animation.
            modifier = Modifier.fillMaxSize().imePadding(),
            containerColor = AetherBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AetherBackgroundGradientTop, AetherBackground, AetherSurface))
                ).padding(innerPadding).dismissKeyboardOnBackgroundTap(dismissKeyboard),
            ) {
                if (visibleMessages.isEmpty()) {
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
                            bottom = composerBodyHeight + 28.dp,
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
                            val browserTools = message.sharedBrowserTools()
                            val browserReplayFrames = message.sharedBrowserReplayFrames()
                            val storedBrowserState = browserTools.asReversed()
                                .asSequence()
                                .map(SharedChatToolInvocation::sharedStoredBrowserDisplayState)
                                .firstOrNull { state ->
                                    state.previewPath.isNotBlank() || state.screenshotBase64.isNotBlank()
                                }
                            val browserState = if (message.isStreaming) {
                                browserDisplayState
                            } else {
                                storedBrowserState
                                    ?: SharedBrowserDisplayState()
                            }
                            val showBrowserCard = browserTools.isNotEmpty() && (
                                message.isStreaming ||
                                    browserState.previewPath.isNotBlank() ||
                                    browserState.screenshotBase64.isNotBlank()
                                )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (showBrowserCard) {
                                    SharedBrowserPreviewCard(
                                        displayState = browserState,
                                        tool = browserTools.last(),
                                        runtime = runtime,
                                        manager = chromeManager,
                                        isLive = message.isStreaming && browserDisplayState.isActive,
                                        replayFrames = browserReplayFrames,
                                        overlayText = message.sharedBrowserOverlayText(),
                                    )
                                }
                                SharedConversationMessage(
                                    message = if (showBrowserCard) message.withoutSharedBrowserTools() else message,
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
                    onSend = { attachments ->
                        onSend(attachments)
                        dismissKeyboard()
                    },
                    isSending = isSending,
                    onStop = onStop,
                    onQueueFollowUp = { attachments ->
                        onQueueFollowUp(attachments)
                        dismissKeyboard()
                    },
                    onSteerFollowUp = { attachments ->
                        onSteerFollowUp(attachments)
                        dismissKeyboard()
                    },
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
                ConversationTimeline(
                    entries = timelineTargets.map(SharedConversationTimelineTarget::entry),
                    currentIndex = currentTimelineIndex,
                    modifier = Modifier.fillMaxSize().padding(
                        top = topBarBodyHeight + 8.dp,
                        bottom = composerBodyHeight + 30.dp,
                    ),
                    onNavigate = { timelineIndex ->
                        timelineTargets.getOrNull(timelineIndex)?.let { target ->
                            shouldAutoFollow = false
                            scope.launch {
                                listState.scrollToItem(target.listItemIndex)
                            }
                        }
                    },
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

private data class SharedConversationTimelineTarget(
    val listItemIndex: Int,
    val entry: ConversationTimelineEntry,
)

private fun buildSharedConversationTimelineTargets(
    messages: List<SharedChatMessage>,
): List<SharedConversationTimelineTarget> = buildList {
    messages.forEachIndexed { messageIndex, message ->
        if (!message.fromUser || message.displayKind != SharedMessageDisplayKind.Standard) {
            return@forEachIndexed
        }
        val assistantPreview = buildString {
            var followingIndex = messageIndex + 1
            while (followingIndex < messages.size && !messages[followingIndex].fromUser) {
                val following = messages[followingIndex]
                if (
                    following.displayKind == SharedMessageDisplayKind.Standard &&
                    following.text.isNotBlank()
                ) {
                    if (isNotEmpty()) append("\n")
                    append(following.text.trim())
                }
                followingIndex += 1
            }
        }
        add(
            SharedConversationTimelineTarget(
                listItemIndex = messageIndex + 1,
                entry = ConversationTimelineEntry(
                    key = message.id,
                    userPreview = message.text.trim().ifBlank {
                        message.attachments.joinToString(", ") { it.name }
                    },
                    assistantPreview = assistantPreview,
                ),
            )
        )
    }
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

    LaunchedEffect(selectedModelKey) {
        menuSelectedModelKey = selectedModelKey
    }
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
        Box(
            modifier = Modifier
                .height(38.dp)
                .onGloballyPositioned { coordinates ->
                    anchorHeightPx = coordinates.boundsInWindow().height.toInt()
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 4.dp)
                    .blur(14.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ControlShadow),
            )
            Row(
                modifier = Modifier
                    .height(38.dp)
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
    var fieldValue by remember(sessionKey) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) fieldValue = TextFieldValue(value, TextRange(value.length))
    }
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
    val extensionUiController = LocalSharedAetherExtensionUiController.current
    val hasExtensionActionTray = extensionUiController
        ?.snapshot
        ?.componentsAt(SharedExtensionComponentChatComposerActionTray)
        .orEmpty()
        .isNotEmpty()
    val hasComposerActionTray = selectedSkills.isNotEmpty() || selectedMcpServers.isNotEmpty() ||
        chromeEnabled || hasExtensionActionTray
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
    val slashSuggestions = remember(fieldValue.text) {
        slashCommandSuggestions(fieldValue.text)
    }
    fun applySlashSuggestion(command: String) {
        val typedLength = fieldValue.text.drop(1).takeWhile { !it.isWhitespace() }.length
        val replaceEnd = (1 + typedLength).coerceAtMost(fieldValue.text.length)
        val suffix = fieldValue.text.substring(replaceEnd)
        val needsSpace = suffix.isEmpty() && slashSuggestions.firstOrNull { it.command == command }?.argumentHint?.isNotBlank() == true
        val replacement = command + if (needsSpace) " " else ""
        val next = replacement + suffix
        fieldValue = TextFieldValue(next, TextRange(replacement.length))
        onValueChange(next)
    }
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
        animationSpec = tween(durationMillis = 260, easing = SharedConversationMotionEasing),
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
                    visible = attachments.isEmpty() && slashSuggestions.isNotEmpty(),
                    enter = fadeIn(tween(160, easing = SharedConversationMotionEasing)) +
                        slideInVertically(tween(220, easing = SharedConversationMotionEasing)) { it / 3 },
                    exit = fadeOut(tween(120, easing = SharedConversationMotionEasing)) +
                        slideOutVertically(tween(180, easing = SharedConversationMotionEasing)) { it / 3 },
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AetherSurfaceHigh.copy(alpha = 0.98f))
                            .padding(vertical = 4.dp),
                    ) {
                        items(slashSuggestions, key = { it.command }) { suggestion ->
                            SharedSlashCommandSuggestionRow(
                                suggestion = suggestion,
                                detail = if (suggestion.command == SharedCompactCommand) {
                                    compactSuggestionText
                                } else {
                                    suggestion.description
                                },
                                input = fieldValue.text,
                                onClick = { applySlashSuggestion(suggestion.command) },
                            )
                        }
                    }
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
                                    ambientColor = AetherScrim,
                                    spotColor = AetherScrim,
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
                                SharedAetherExtensionComponentHost(
                                    target = SharedExtensionComponentChatComposerActionTray,
                                    modifier = Modifier.fillMaxWidth(),
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
                                        value = fieldValue,
                                        onValueChange = { next ->
                                            fieldValue = next
                                            onValueChange(next.text)
                                        },
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
                                    ambientColor = AetherScrim,
                                    spotColor = AetherScrim,
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
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(ComposerPurple).clickable(onClick = onClick),
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
        modifier = Modifier.size(38.dp).clip(CircleShape)
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
    val enterTransition = if (reduceMotion) fadeIn(tween(80)) else fadeIn(tween(160, easing = SharedConversationMotionEasing)) + scaleIn(
        initialScale = 0.92f, animationSpec = tween(180, easing = SharedConversationMotionEasing)) + slideInVertically(
        animationSpec = tween(180, easing = SharedConversationMotionEasing), initialOffsetY = { it / 10 })
    val exitTransition = if (reduceMotion) fadeOut(tween(60)) else fadeOut(tween(120, easing = SharedConversationMotionEasing)) + scaleOut(
        targetScale = 0.96f, animationSpec = tween(140, easing = SharedConversationMotionEasing))
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
                        iconTint = Color(0xFF8D6C2F),
                        iconContainerColor = Color(0xFFFFF3DE),
                        onClick = onSteer,
                    )
                    SharedComposerPlusMenuRow(
                        title = stringResource(Res.string.branch_queue_next_turn),
                        icon = Icons.Rounded.ArrowUpward,
                        iconTint = Color(0xFF2F6DA3),
                        iconContainerColor = Color(0xFFEAF2FF),
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
    val extensionUiController = LocalSharedAetherExtensionUiController.current
    val enterTransition = if (reduceMotion) fadeIn(tween(80)) else fadeIn(tween(160, easing = SharedConversationMotionEasing)) + scaleIn(
        initialScale = 0.92f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(220, easing = SharedConversationMotionEasing)) + slideInVertically(
        animationSpec = tween(240, easing = SharedConversationMotionEasing), initialOffsetY = { it / 10 })
    val exitTransition = if (reduceMotion) fadeOut(tween(60)) else fadeOut(tween(120, easing = SharedConversationMotionEasing)) + scaleOut(
        targetScale = 0.96f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(160, easing = SharedConversationMotionEasing)) + slideOutVertically(
        animationSpec = tween(180, easing = SharedConversationMotionEasing), targetOffsetY = { it / 12 })
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
                        SharedAetherExtensionComponentHost(
                            target = SharedExtensionComponentChatComposerSkillPicker,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            availableSkills.forEach { skill ->
                                val selected = skill.id in selectedSkillIds
                                SharedComposerPlusMenuRow(
                                    title = skill.sharedQuickActionLabel(),
                                    icon = skill.sharedComposerIcon(),
                                    iconTint = skill.sharedComposerIconTint(),
                                    selected = selected,
                                    onClick = {
                                        onDismiss()
                                        onSkillSelected(skill.id, !selected)
                                    },
                                )
                            }
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
                        LocalSharedAetherExtensionUiController.current
                            ?.snapshot
                            ?.composerMenuItems
                            .orEmpty()
                            .forEach { item ->
                                SharedComposerPlusMenuRow(
                                    title = item.title,
                                    icon = Icons.Rounded.Extension,
                                    iconTint = AetherPrimary,
                                    selected = item.selected,
                                    onClick = {
                                        onDismiss()
                                        extensionUiController?.onAction?.invoke(
                                            item.extensionId,
                                            item.action.ifBlank { item.localId },
                                            item.args,
                                        )
                                    },
                                )
                            }
                        if (extensionUiController
                                ?.snapshot
                                ?.surfacesAt(SharedExtensionSlotChatComposerPlusMenu)
                                .orEmpty()
                                .isNotEmpty()
                        ) {
                            Spacer(Modifier.height(6.dp))
                            SharedAetherExtensionSlot(SharedExtensionSlotChatComposerPlusMenu)
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
                icon = skill.sharedComposerIcon(),
                iconTint = skill.sharedComposerIconTint(),
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

private fun SharedInstalledSkill.sharedComposerIcon(): ImageVector =
    if (id == CreateExtensionSkillId) Icons.Rounded.LibraryAdd else Icons.Rounded.Extension

private fun SharedInstalledSkill.sharedComposerIconTint(): Color =
    if (id == CreateExtensionSkillId) AetherPrimary else Color(0xFF9C6B2F)

@Composable
private fun SharedComposerActionChip(
    label: String,
    icon: ImageVector,
    iconTint: Color = Color(0xFF4F8CFF),
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE8F1FF)).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF2E6FD5),
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
                tint = Color(0xFF4F8CFF),
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
            val completedChunk = block.trace.chunks.firstOrNull { it.id == chunkId }
            val completedOrder = completedChunk?.timelineOrder?.takeIf { it > 0L }
                ?: completedChunk?.createdAtMillis?.takeIf { it > 0L }
                ?: Long.MAX_VALUE
            val hasNewerTimelineItem = block.trace.toolInvocations.any { tool ->
                (tool.timelineOrder.takeIf { it > 0L }
                    ?: tool.startedAtMillis.takeIf { it > 0L }
                    ?: Long.MIN_VALUE) > completedOrder
            } || block.trace.chunks.any { chunk ->
                chunk.id != chunkId && (
                    chunk.timelineOrder.takeIf { it > 0L }
                        ?: chunk.createdAtMillis.takeIf { it > 0L }
                        ?: Long.MIN_VALUE
                    ) > completedOrder
            }
            block.copy(
                trace = block.trace.copy(
                    chunks = block.trace.chunks.map { chunk ->
                        if (chunk.id == chunkId) {
                            chunk.copy(title = title, detail = detail, isPending = false)
                        } else {
                            chunk
                        }
                    },
                    latestStatusText = if (hasNewerTimelineItem) {
                        block.trace.latestStatusText
                    } else {
                        detail.ifBlank { title }
                    },
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
    routeIntoReasoning: Boolean = false,
): SharedChatMessage = withAssistantToolEvent(
    event = SharedPiToolEvent(
        id = call.id,
        name = call.name,
        argumentsJson = call.arguments.toString(),
        isRunning = true,
    ),
    routeIntoReasoning = routeIntoReasoning,
    nowMillis = startedAtMillis,
    nowUptimeMillis = startedAtUptimeMillis,
    timelineOrder = timelineOrder,
)

internal fun SharedChatMessage.withAssistantToolEvent(
    event: SharedPiToolEvent,
    routeIntoReasoning: Boolean,
    nowMillis: Long = platformCurrentTimeMillis(),
    nowUptimeMillis: Long = platformUptimeMillis(),
    timelineOrder: Long = nowMillis,
): SharedChatMessage {
    val existing = tools.firstOrNull { it.id == event.id }
    val normalized = existing.withSharedPiToolEvent(event, nowMillis, nowUptimeMillis, timelineOrder)
    val updatedTools = tools.upsertSharedTool(normalized)
    val existingReasoningIndex = responseBlocks.indexOfFirst { block ->
        block is SharedAssistantResponseBlock.Reasoning &&
            block.trace.toolInvocations.any { it.id == event.id }
    }
    if (existingReasoningIndex >= 0) {
        val block = responseBlocks[existingReasoningIndex] as SharedAssistantResponseBlock.Reasoning
        return copy(
            tools = updatedTools,
            responseBlocks = responseBlocks.toMutableList().apply {
                this[existingReasoningIndex] = block.copy(
                    trace = block.trace.copy(
                        toolInvocations = block.trace.toolInvocations.upsertSharedTool(normalized),
                        latestStatusText = sharedReasoningToolStatus(normalized),
                    ),
                )
            },
        )
    }
    val existingToolGroupIndex = responseBlocks.indexOfFirst { block ->
        block is SharedAssistantResponseBlock.ToolGroup && block.tools.any { it.id == event.id }
    }
    if (existingToolGroupIndex >= 0) {
        val block = responseBlocks[existingToolGroupIndex] as SharedAssistantResponseBlock.ToolGroup
        return copy(
            tools = updatedTools,
            responseBlocks = responseBlocks.toMutableList().apply {
                this[existingToolGroupIndex] = block.copy(tools = block.tools.upsertSharedTool(normalized))
            },
        )
    }

    val activeReasoningIndex = responseBlocks.indexOfLast { block ->
        block is SharedAssistantResponseBlock.Reasoning && block.trace.completedAtMillis == null
    }
    val shouldRouteIntoReasoning = activeReasoningIndex >= 0 || routeIntoReasoning ||
        responseBlocks.any { it is SharedAssistantResponseBlock.Reasoning }
    val updatedBlocks = when {
        activeReasoningIndex >= 0 -> {
            val block = responseBlocks[activeReasoningIndex] as SharedAssistantResponseBlock.Reasoning
            responseBlocks.toMutableList().apply {
                this[activeReasoningIndex] = block.copy(
                    trace = block.trace.copy(
                        toolInvocations = block.trace.toolInvocations + normalized,
                        latestStatusText = sharedReasoningToolStatus(normalized),
                    ),
                )
            }
        }
        shouldRouteIntoReasoning -> {
            val blockId = platformRandomUuid()
            responseBlocks + SharedAssistantResponseBlock.Reasoning(
                id = blockId,
                trace = SharedReasoningTrace(
                    id = blockId,
                    toolInvocations = listOf(normalized),
                    latestStatusText = sharedReasoningToolStatus(normalized),
                    startedAtMillis = nowMillis,
                ),
            )
        }
        responseBlocks.lastOrNull() is SharedAssistantResponseBlock.ToolGroup -> {
            responseBlocks.dropLast(1) +
                (responseBlocks.last() as SharedAssistantResponseBlock.ToolGroup).let { block ->
                    block.copy(tools = block.tools + normalized)
                }
        }
        else -> responseBlocks + SharedAssistantResponseBlock.ToolGroup(
            platformRandomUuid(),
            listOf(normalized),
        )
    }
    return copy(tools = updatedTools, responseBlocks = updatedBlocks)
}

internal fun SharedChatMessage.withFinishedAssistantTool(
    toolId: String,
    result: SharedHostToolResult,
    completedAtMillis: Long = platformCurrentTimeMillis(),
    completedAtUptimeMillis: Long = platformUptimeMillis(),
): SharedChatMessage {
    val existing = tools.firstOrNull { it.id == toolId } ?: return this
    return withAssistantToolEvent(
        event = SharedPiToolEvent(
            id = existing.id,
            name = existing.name,
            argumentsJson = existing.argumentsJson,
            outputJson = result.outputJson,
            isRunning = false,
            isError = result.isError,
        ),
        routeIntoReasoning = false,
        nowMillis = completedAtMillis,
        nowUptimeMillis = completedAtUptimeMillis,
        timelineOrder = existing.timelineOrder,
    )
}

private fun SharedChatToolInvocation?.withSharedPiToolEvent(
    event: SharedPiToolEvent,
    nowMillis: Long,
    nowUptimeMillis: Long,
    timelineOrder: Long,
): SharedChatToolInvocation {
    val previous = this
    val argumentsJson = event.argumentsJson.takeUnless { it.isBlank() || (it == "{}" && previous != null) }
        ?: previous?.argumentsJson.orEmpty().ifBlank { "{}" }
    val outputJson = event.outputJson ?: previous?.outputJson.orEmpty()
    val parsedArguments = runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull()
    return SharedChatToolInvocation(
        id = event.id,
        name = event.name.takeUnless { it == "tool_call" && previous != null } ?: previous?.name.orEmpty(),
        summary = parsedArguments?.toolSummary().orEmpty(),
        output = outputJson.toolOutputSummary(),
        argumentsJson = argumentsJson,
        outputJson = outputJson,
        isRunning = event.isRunning,
        isError = event.isError,
        startedAtUptimeMillis = previous?.startedAtUptimeMillis?.takeIf { it > 0L } ?: nowUptimeMillis,
        completedAtUptimeMillis = if (event.isRunning) null else {
            previous?.completedAtUptimeMillis ?: nowUptimeMillis
        },
        startedAtMillis = previous?.startedAtMillis?.takeIf { it > 0L } ?: nowMillis,
        completedAtMillis = if (event.isRunning) null else previous?.completedAtMillis ?: nowMillis,
        timelineOrder = previous?.timelineOrder?.takeIf { it > 0L } ?: timelineOrder,
    )
}

private fun List<SharedChatToolInvocation>.upsertSharedTool(
    tool: SharedChatToolInvocation,
): List<SharedChatToolInvocation> {
    val index = indexOfFirst { it.id == tool.id }
    return if (index < 0) this + tool else toMutableList().apply { this[index] = tool }
}

internal fun LlmProviderConfig.supportsSharedVisibleReasoningTrace(): Boolean =
    baseUrl.contains("deepseek", ignoreCase = true) ||
        baseUrl.contains("openrouter", ignoreCase = true) ||
        baseUrl.contains("requesty", ignoreCase = true) ||
        modelId.contains("deepseek", ignoreCase = true) ||
        modelId.contains("openrouter", ignoreCase = true)

internal fun sharedReasoningToolStatus(tool: SharedChatToolInvocation): String {
    val args = runCatching { Json.parseToJsonElement(tool.argumentsJson) as? JsonObject }.getOrNull()
    val running = tool.isRunning
    fun action(runningVerb: String, completedVerb: String, subject: String, fallback: String): String {
        val verb = if (running) runningVerb else completedVerb
        val value = subject.trim().take(96)
        return "$verb ${value.ifBlank { fallback }}"
    }
    return when (tool.name.lowercase()) {
        "bash" -> if (running) "Executing bash command" else "Executed bash command"
        "read" -> if (running) "Reading file" else "Read file"
        "edit" -> if (running) "Editing file" else "Edited file"
        "write" -> if (running) "Writing file" else "Wrote file"
        "grep" -> if (running) "Searching files" else "Searched files"
        "find" -> if (running) "Finding files" else "Found files"
        "ls" -> if (running) "Listing files" else "Listed files"
        "aether_config_get" -> action(
            "Reading", "Read",
            (args?.get("categories") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                ?.joinToString(", ").orEmpty(),
            "Aether settings",
        )
        "aether_config_set" -> action("Updating", "Updated", args?.string("category").orEmpty(), "Aether settings")
        "aether_skill_manage" -> when (args?.string("action").orEmpty().lowercase()) {
            "install_remote" -> action("Installing", "Installed", args?.string("url").orEmpty(), "Agent Skills")
            "remove" -> action("Removing", "Removed", args?.string("skill_id").orEmpty().ifBlank { args?.string("skillId").orEmpty() }, "Agent Skills")
            "set_enabled" -> action("Updating", "Updated", args?.string("skill_id").orEmpty().ifBlank { args?.string("skillId").orEmpty() }, "Agent Skills")
            else -> if (running) "Reading Agent Skills" else "Read Agent Skills"
        }
        "aether_mcp_manage" -> when (args?.string("action").orEmpty().lowercase()) {
            "upsert_streamable_http", "upsert_stdio" -> action("Saving", "Saved", args?.string("display_name").orEmpty().ifBlank { args?.string("displayName").orEmpty() }, "MCP servers")
            "remove" -> action("Removing", "Removed", args?.string("server_id").orEmpty().ifBlank { args?.string("serverId").orEmpty() }, "MCP servers")
            "set_enabled" -> action("Updating", "Updated", args?.string("server_id").orEmpty().ifBlank { args?.string("serverId").orEmpty() }, "MCP servers")
            else -> if (running) "Reading MCP servers" else "Read MCP servers"
        }
        "aether_termux_manage" -> when (args?.string("action").orEmpty().lowercase()) {
            "configure_root_access" -> if (running) "Configuring Termux root access" else "Configured Termux root access"
            "inspect_root_setup" -> if (running) "Checking Root setup" else "Checked Root setup"
            else -> if (running) "Checking Termux setup" else "Checked Termux setup"
        }
        "aether_agent_mode_manage" -> when (args?.string("action").orEmpty().lowercase()) {
            "set_authorization" -> if (running) "Updating Agent Mode authorization" else "Updated Agent Mode authorization"
            "request_shizuku_permission" -> if (running) "Requesting Shizuku permission" else "Requested Shizuku permission"
            "stop_display" -> if (running) "Stopping Agent Mode display" else "Stopped Agent Mode display"
            "refresh_displays" -> if (running) "Refreshing Agent Mode displays" else "Refreshed Agent Mode displays"
            else -> if (running) "Checking Agent Mode authorization" else "Checked Agent Mode authorization"
        }
        "aether_developer_manage" -> if (running) "Reading Aether diagnostics" else "Read Aether diagnostics"
        else -> if (running) "Using ${tool.name}" else "Used ${tool.name}"
    }
}

internal fun SharedChatMessage.withAssistantResultFallback(
    result: com.zhousl.aether.data.pi.SharedPiTurnResult,
): SharedChatMessage {
    if (reasoningText.isNotBlank() || result.reasoningText.isBlank()) return this
    val blockId = platformRandomUuid()
    val reasoningBlock = SharedAssistantResponseBlock.Reasoning(
        id = blockId,
        trace = SharedReasoningTrace(
            id = blockId,
            rawText = result.reasoningText,
            startedAtMillis = platformCurrentTimeMillis(),
        ),
    )
    val firstTextIndex = responseBlocks.indexOfFirst { it is SharedAssistantResponseBlock.Text }
    val blocks = if (firstTextIndex >= 0) {
        responseBlocks.toMutableList().apply { add(firstTextIndex, reasoningBlock) }
    } else {
        responseBlocks + reasoningBlock
    }
    return copy(reasoningText = result.reasoningText, responseBlocks = blocks)
}

internal fun SharedChatMessage.withoutSharedAssistantReasoning(): SharedChatMessage {
    val finalText = responseBlocks.filterIsInstance<SharedAssistantResponseBlock.Text>()
        .lastOrNull { it.text.isNotBlank() }
    val workBlocks = responseBlocks.flatMap { block ->
        when (block) {
            is SharedAssistantResponseBlock.Text -> emptyList()
            is SharedAssistantResponseBlock.Reasoning -> block.trace.toolInvocations
                .takeIf { it.isNotEmpty() }
                ?.let { tools ->
                    listOf(SharedAssistantResponseBlock.ToolGroup(block.id, tools))
                }
                .orEmpty()
            is SharedAssistantResponseBlock.Status,
            is SharedAssistantResponseBlock.ToolGroup -> listOf(block)
        }
    }
    return copy(
        text = finalText?.text ?: text,
        reasoningText = "",
        responseBlocks = workBlocks + listOfNotNull(finalText),
    )
}

internal fun List<SharedAssistantResponseBlock>.normalizeSharedFinalReasoningOrder(): List<SharedAssistantResponseBlock> =
    if (size == 2 && this[0] is SharedAssistantResponseBlock.Text &&
        this[1] is SharedAssistantResponseBlock.Reasoning
    ) {
        listOf(this[1], this[0])
    } else {
        this
    }

internal fun List<SharedAssistantResponseBlock>.removeLeakedSharedReasoning(
    persistedReasoningText: String,
): List<SharedAssistantResponseBlock> {
    if (persistedReasoningText.isNotBlank()) return this
    return flatMap { block ->
        if (block !is SharedAssistantResponseBlock.Reasoning) {
            listOf(block)
        } else {
            block.trace.toolInvocations
                .takeIf { it.isNotEmpty() }
                ?.let { tools ->
                    listOf(SharedAssistantResponseBlock.ToolGroup(block.id, tools))
                }
                .orEmpty()
        }
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

internal fun SharedChatMessage.withSharedRequestFailure(message: String): SharedChatMessage {
    val detail = message.trim().ifBlank { "Unknown error" }
    val failureText = "Request failed: $detail"
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
    preserveStatus: Boolean = false,
    completedAtMillis: Long = platformCurrentTimeMillis(),
): SharedChatMessage {
    val hadNoText = text.isBlank()
    val completedAtUptimeMillis = platformUptimeMillis()
    val hasVisibleWorkBeforeStatus = text.isNotBlank() || fallbackText.isNotBlank() ||
        responseBlocks.isNotEmpty() || tools.isNotEmpty()
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
        status = when {
            preserveStatus -> completedSharedReconnectStatus(this.status)
            hasVisibleWorkBeforeStatus -> status
            else -> ""
        },
        statusDetail = if (preserveStatus && this.status.isNotBlank()) this.statusDetail else "",
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
                is SharedAssistantResponseBlock.Status -> block.copy(
                    text = completedReconnectStatus(block.text),
                )
                else -> block
            }
        },
    )
}

internal fun completedSharedReconnectStatus(status: String): String = completedReconnectStatus(status)

private fun SharedChatToolInvocation.isSharedInterruptedToolInvocation(): Boolean {
    if (isRunning) return true
    if (!name.equals("bash", ignoreCase = true)) return false
    val output = runCatching { Json.parseToJsonElement(outputJson) as? JsonObject }.getOrNull() ?: return false
    val status = output["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return status == "running" || status == "launching"
}

internal fun SharedChatMessage.hasSharedVisibleAssistantWork(): Boolean =
    text.isNotBlank() || status.isNotBlank() || responseBlocks.any { block ->
        when (block) {
            is SharedAssistantResponseBlock.Text -> block.text.isNotBlank()
            is SharedAssistantResponseBlock.Reasoning -> true
            is SharedAssistantResponseBlock.ToolGroup -> block.tools.isNotEmpty()
            is SharedAssistantResponseBlock.Status -> block.text.isNotBlank()
        }
        } || tools.isNotEmpty()

internal fun shouldApplySharedTurnEvent(activeJob: Job?, runningJob: Job?): Boolean =
    activeJob != null && activeJob === runningJob

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

internal fun SharedChatMessage.interruptedByBackgroundExpiration(status: String = "Interrupted"): SharedChatMessage =
    finalizeSharedInterruptedAssistantWork(
        status = status,
        fallbackText = "This response was interrupted when iOS background time expired. Retry the message to continue.",
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
        status = status,
        statusDetail = statusDetail,
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
                is SharedAssistantResponseBlock.Status -> PersistedAssistantResponseBlock(
                    id = block.id,
                    type = PersistedAssistantResponseBlockType.Status,
                    text = block.text,
                    statusDetail = block.detail,
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
        customType = customType,
        customPayloadJson = customPayloadJson,
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
            PersistedAssistantResponseBlockType.Status -> SharedAssistantResponseBlock.Status(
                id = block.id,
                text = block.text,
                detail = block.statusDetail,
            )
        }
    }.removeLeakedSharedReasoning(reasoningText)
        .normalizeSharedFinalReasoningOrder()
        .ifEmpty {
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
    status = status,
    statusDetail = statusDetail,
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
        customType = customType,
        customPayloadJson = customPayloadJson,
    thoughtDurationMillis = thoughtDurationMillis.takeIf {
        restoredBlocks.any { block ->
            block is SharedAssistantResponseBlock.Reasoning ||
                block is SharedAssistantResponseBlock.ToolGroup
        }
    } ?: 0L,
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

internal fun buildNativeSettingsSnapshot(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
    installedSkills: List<SharedInstalledSkill>,
    extensionSnapshot: SharedAetherExtensionSnapshot,
    capabilities: PlatformCapabilities,
    statistics: com.zhousl.aether.data.SharedUsageStatisticsReport =
        com.zhousl.aether.data.SharedUsageStatisticsReport(),
    providerModels: Map<String, List<String>> = emptyMap(),
    providerOperation: String = "",
    providerCompletedRequestId: String = "",
    providerAuthSessionId: String = "",
    providerError: String = "",
    providerAuthState: PiProviderAuthState = PiProviderAuthState(),
    operationMessage: String = "",
    operationError: String = "",
    operation: String = "",
    piExtensions: NativePiExtensionsState = NativePiExtensionsState(),
    alpine: NativeAlpineSettingsState = NativeAlpineSettingsState(
        ready = settings.alpineSetupCompleted,
        issue = if (settings.alpineSetupCompleted) "ready" else "not_installed",
    ),
): String = buildJsonObject {
    val serializedSettings = Json.parseToJsonElement(serializeAppSettings(settings)) as JsonObject
    put("settings", JsonObject(serializedSettings + mapOf(
        "language" to JsonPrimitive(settings.language.storageValue),
        "themeMode" to JsonPrimitive(settings.themeMode.storageValue),
    )))
    put("providers", Json.parseToJsonElement(serializeProviderConfigs(providerConfigs)))
    put("providerCatalog", buildJsonArray {
        PiProviderCatalog.providers.forEach { definition ->
            add(buildJsonObject {
                put("id", definition.id)
                put("displayName", definition.displayName)
                put("defaultBaseUrl", definition.defaultBaseUrl)
                put("defaultModelId", definition.defaultModelId)
                put("supportsApiKey", definition.supportsApiKey)
                put("supportsInteractiveApiKey", definition.supportsInteractiveApiKey)
                put("supportsOAuth", definition.supportsOAuth)
                put("supportsAmbientAuth", definition.supportsAmbientAuth)
                put("requiresBaseUrl", definition.requiresBaseUrl)
                put("supportsCustomBaseUrl", definition.supportsCustomBaseUrl)
                put("isBuiltIn", definition.isBuiltIn)
                put("category", definition.category)
            })
        }
    })
    val providerModelOptions = providerConfigs.availableModelOptions()
    put("modelOptions", buildJsonArray {
        providerModelOptions.forEach { option ->
            add(buildJsonObject {
                put("key", option.key)
                put("providerConfigId", option.providerConfigId)
                put("providerName", option.providerName)
                put("modelId", option.modelId)
                put("fullLabel", option.fullLabel)
                put("chatLabel", option.chatLabel)
            })
        }
    })
    put("automaticModels", buildJsonObject {
        AutomaticModelPurpose.entries.forEach { purpose ->
            val key = providerModelOptions.resolveAutomaticModelKey(purpose)
            put(purpose.name.lowercase(), buildJsonObject {
                put("key", key)
                put("label", providerModelOptions.findModelOption(key)?.fullLabel.orEmpty())
            })
        }
    })
    put("providerModels", buildJsonObject {
        providerModels.forEach { (id, models) ->
            put(id, buildJsonArray { models.forEach { add(JsonPrimitive(it)) } })
        }
    })
    put("providerOperation", providerOperation)
    put("providerCompletedRequestId", providerCompletedRequestId)
    put("providerError", providerError)
    put("providerAuth", buildJsonObject {
        put("sessionId", providerAuthSessionId)
        put("providerId", providerAuthState.providerId)
        put("authMethod", providerAuthState.authMethod.storageValue)
        put("isRunning", providerAuthState.isRunning)
        put("statusMessage", providerAuthState.statusMessage)
        put("authorizationUrl", providerAuthState.authorizationUrl)
        put("deviceCode", providerAuthState.deviceCode)
        put("verificationUrl", providerAuthState.verificationUrl)
        put("apiKey", providerAuthState.apiKey)
        put("oauthCredentialJson", providerAuthState.oauthCredentialJson)
        put("providerEnvironmentVariables", buildJsonArray {
            providerAuthState.providerEnvironmentVariables.forEach { variable ->
                add(buildJsonObject {
                    put("name", variable.name)
                    put("value", variable.value)
                })
            }
        })
        put("errorMessage", providerAuthState.errorMessage)
        providerAuthState.prompt?.let { prompt ->
            put("prompt", buildJsonObject {
                put("id", prompt.id)
                put("type", prompt.type)
                put("message", prompt.message)
                put("placeholder", prompt.placeholder)
                put("options", buildJsonArray {
                    prompt.options.forEach { option ->
                        add(buildJsonObject {
                            put("id", option.id)
                            put("label", option.label)
                            put("description", option.description)
                        })
                    }
                })
            })
        }
    })
    put("operationMessage", operationMessage)
    put("operationError", operationError)
    put("operation", operation)
    put("skills", buildJsonArray {
        installedSkills.sortedBy { it.name.lowercase() }.forEach { skill ->
            add(buildJsonObject {
                put("id", skill.id)
                put("name", skill.name)
                put("description", skill.description)
                put("enabled", skill.isEnabled)
                put("source", skill.source)
                put("compatibility", skill.compatibility)
                put("guestPath", skill.guestPath)
                put("license", skill.license)
                put("resourceCount", skill.resourceCount)
                put("allowedTools", buildJsonArray {
                    skill.allowedTools.forEach { add(JsonPrimitive(it)) }
                })
            })
        }
    })
    // Script extension settings are schema driven and remain available to SwiftUI. Aether Native
    // surfaces/components are intentionally not exported because they are platform UI code.
    put("extensionSettings", buildJsonArray {
        extensionSnapshot.settings.sortedBy { it.order }.forEach { page ->
            add(buildJsonObject {
                put("id", page.id)
                put("settingsId", page.localId)
                put("extensionId", page.extensionId)
                put("extensionName", page.extensionName)
                put("title", page.title)
                put("subtitle", page.subtitle)
                put("icon", page.icon)
                put("trailingIcon", page.trailingIcon)
                put("trailingAction", page.trailingAction)
                put("trailingCategory", page.trailingCategory)
                put("trailingArgs", page.trailingArgs)
                put("sections", JsonArray(page.sections))
                put("categories", buildJsonArray {
                    page.categories.sortedBy { it.order }.forEach { category ->
                        add(buildJsonObject {
                            put("id", category.id)
                            put("title", category.title)
                            put("subtitle", category.subtitle)
                            put("icon", category.icon)
                            put("trailingIcon", category.trailingIcon)
                            put("trailingAction", category.trailingAction)
                            put("trailingCategory", category.trailingCategory)
                            put("trailingArgs", category.trailingArgs)
                            put("hidden", category.hidden)
                            put("sections", JsonArray(category.sections))
                        })
                    }
                })
            })
        }
    })
    put("capabilities", buildJsonObject {
        put("persistentBackground", capabilities.persistentBackground)
        put("localNotifications", capabilities.localNotifications)
        put("alpine", capabilities.alpine)
        put("alpineChrome", capabilities.alpineChrome)
        put("scriptExtensions", capabilities.scriptExtensions)
    })
    put("extensionCount", extensionSnapshot.extensions.size)
    put("piExtensions", buildJsonObject {
        put("operation", piExtensions.operation)
        put("message", piExtensions.message)
        put("error", piExtensions.error)
        put("installedError", piExtensions.installedError)
        put("catalogError", piExtensions.catalogError)
        put("installed", buildJsonArray {
            piExtensions.installed.forEach { extension ->
                add(buildJsonObject {
                    put("id", extension.id)
                    put("source", extension.source)
                    put("name", extension.name)
                    put("version", extension.version)
                    put("description", extension.description)
                    put("installedPath", extension.installedPath)
                    put("extensionCount", extension.extensionCount)
                    put("aetherExtensionCount", extension.aetherExtensionCount)
                    put("skillCount", extension.skillCount)
                    put("promptCount", extension.promptCount)
                    put("themeCount", extension.themeCount)
                    put("isEnabled", extension.isEnabled)
                    put("kind", extension.kind.name.lowercase())
                })
            }
        })
        put("catalog", buildJsonArray {
            piExtensions.catalog.forEach { entry ->
                add(buildJsonObject {
                    put("name", entry.name)
                    put("source", entry.source)
                    put("description", entry.description)
                    put("author", entry.author)
                    put("monthlyDownloads", entry.monthlyDownloads)
                    put("packageUrl", entry.packageUrl)
                    put("npmUrl", entry.npmUrl)
                    put("repositoryUrl", entry.repositoryUrl)
                    put("types", buildJsonArray { entry.types.forEach { add(JsonPrimitive(it)) } })
                    put("compatibilityIssue", entry.compatibilityIssue?.name?.lowercase().orEmpty())
                })
            }
        })
        piExtensions.details?.let { details ->
            put("details", buildJsonObject {
                put("source", details.source)
                put("name", details.name)
                put("description", details.description)
                put("version", details.version)
                put("published", details.published)
                put("downloads", details.downloads)
                put("author", details.author)
                put("license", details.license)
                put("size", details.size)
                put("dependencies", details.dependencies)
                put("types", buildJsonArray { details.types.forEach { add(JsonPrimitive(it)) } })
                put("manifestJson", details.manifestJson)
                put("readmeMarkdown", details.readmeMarkdown)
                put("npmUrl", details.npmUrl)
                put("repositoryUrl", details.repositoryUrl)
                put("compatibilityIssue", details.compatibilityIssue?.name?.lowercase().orEmpty())
            })
        }
    })
    put("alpine", buildJsonObject {
        put("ready", alpine.ready)
        put("issue", alpine.issue)
        put("detail", alpine.detail)
        put("operation", alpine.operation)
        put("progress", alpine.progress)
        put("isDefault", settings.defaultRuntimeId == LocalRuntimeId.Alpine)
        put("profiles", buildJsonArray {
            listOf("python", "node", "git_search", "ssh").forEach { id ->
                val profile = settings.alpinePackageProfiles[id]
                add(buildJsonObject {
                    put("id", id)
                    put("installed", profile?.installed == true)
                    put("installedAtMillis", profile?.installedAtMillis ?: 0L)
                    put("error", profile?.lastError.orEmpty())
                })
            }
        })
    })
    put("statistics", buildJsonObject {
        put("totalTokens", statistics.totalTokens)
        put("inputTokens", statistics.inputTokens)
        put("outputTokens", statistics.outputTokens)
        put("reasoningTokens", statistics.reasoningTokens)
        put("cachedInputTokens", statistics.cachedInputTokens)
        put("sessionCount", statistics.sessionCount)
        put("messageCount", statistics.messageCount)
        put("turnCount", statistics.turnCount)
        statistics.averageOutputTokensPerSecond?.let { put("averageOutputTokensPerSecond", it) }
        statistics.averageFirstTokenLatencyMillis?.let { put("averageFirstTokenLatencyMillis", it) }
        statistics.largestTurnTokens?.let { put("largestTurnTokens", it) }
        statistics.averageTurnTokens?.let { put("averageTurnTokens", it) }
        statistics.peakDay?.let { peak ->
            put("peakDay", buildJsonObject {
                put("label", peak.label)
                put("tokens", peak.tokens)
            })
        }
        put("recentDailyTokenUsage", buildJsonArray {
            statistics.recentDailyTokenUsage.forEach { day ->
                add(buildJsonObject {
                    put("id", day.key)
                    put("label", day.label)
                    put("shortLabel", day.shortLabel)
                    put("tokens", day.tokens)
                })
            }
        })
        put("allDailyTokenUsage", buildJsonArray {
            statistics.allDailyTokenUsage.forEach { day ->
                add(buildJsonObject {
                    put("id", day.key)
                    put("label", day.label)
                    put("shortLabel", day.shortLabel)
                    put("tokens", day.tokens)
                })
            }
        })
        put("recentSpeedSamples", buildJsonArray {
            statistics.recentSpeedSamples.takeLast(12).forEachIndexed { index, sample ->
                add(buildJsonObject {
                    put("id", "${sample.timestampMillis}:$index")
                    put("label", sample.label)
                    put("shortLabel", sample.shortLabel)
                    put("tokensPerSecond", sample.tokensPerSecond)
                    put("timestampMillis", sample.timestampMillis)
                })
            }
        })
    })
    put("appVersion", platformAppVersion())
}
.toString()

internal fun AppSettings.withNativeSettingsPatch(patch: JsonObject): AppSettings = copy(
    language = patch.nativeStringOrNull("language")
        ?.let(com.zhousl.aether.data.AppLanguage::fromStorage) ?: language,
    themeMode = patch.nativeStringOrNull("themeMode")
        ?.let(com.zhousl.aether.data.AppThemeMode::fromStorage) ?: themeMode,
    systemPrompt = patch.nativeStringOrNull("systemPrompt") ?: systemPrompt,
    llmInactivityReconnectTimeoutSeconds = patch.nativeInt("llmInactivityReconnectTimeoutSeconds")
        ?.let(::normalizeLlmInactivityReconnectTimeoutSeconds)
        ?: llmInactivityReconnectTimeoutSeconds,
    keepTasksRunningInBackground = patch.nativeBoolean("keepTasksRunningInBackground")
        ?: keepTasksRunningInBackground,
    notifyOnTaskCompletion = patch.nativeBoolean("notifyOnTaskCompletion")
        ?: notifyOnTaskCompletion,
    autoCleanOldCommandHistory = patch.nativeBoolean("autoCleanOldCommandHistory")
        ?: autoCleanOldCommandHistory,
    oldCommandHistoryRetentionHours = patch.nativeInt("oldCommandHistoryRetentionHours")
        ?.let(::normalizeOldCommandHistoryRetentionHours)
        ?: oldCommandHistoryRetentionHours,
    defaultChatModelKey = patch.nativeStringOrNull("defaultChatModelKey") ?: defaultChatModelKey,
    defaultTitleModelKey = patch.nativeStringOrNull("defaultTitleModelKey") ?: defaultTitleModelKey,
    defaultNamingModelKey = patch.nativeStringOrNull("defaultNamingModelKey") ?: defaultNamingModelKey,
    defaultCompactingModelKey = patch.nativeStringOrNull("defaultCompactingModelKey")
        ?: defaultCompactingModelKey,
)

private fun JsonObject.nativeString(name: String): String = nativeStringOrNull(name).orEmpty()

private fun JsonObject.nativeStringOrNull(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.nativeBoolean(name: String): Boolean? =
    nativeStringOrNull(name)?.toBooleanStrictOrNull()

private fun JsonObject.nativeInt(name: String): Int? = nativeStringOrNull(name)?.toIntOrNull()

private fun JsonObject.nativeLong(name: String): Long? = nativeStringOrNull(name)?.toLongOrNull()

private fun JsonObject.nativeStringList(name: String): List<String> =
    (get(name) as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull
    }

private fun JsonObject.nativeStringMap(name: String): Map<String, String> =
    (get(name) as? JsonObject).orEmpty().mapNotNull { (key, element) ->
        (element as? JsonPrimitive)?.contentOrNull?.let { key to it }
    }.toMap()
