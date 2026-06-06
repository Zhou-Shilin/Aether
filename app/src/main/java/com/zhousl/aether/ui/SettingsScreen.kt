package com.zhousl.aether.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zhousl.aether.BuildConfig
import com.zhousl.aether.R
import java.util.Locale
import com.zhousl.aether.data.AetherPrivacyPolicyUrl
import com.zhousl.aether.data.AetherWebsiteUrl
import com.zhousl.aether.data.AgentModeAuthorizationIssue
import com.zhousl.aether.data.AgentModeAuthorizationMethod
import com.zhousl.aether.data.AgentModeAuthorizationState
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.AgentWorkspaceMode
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AppThemeMode
import com.zhousl.aether.data.LlmProvider
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.LocalRuntimeId
import com.zhousl.aether.data.McpServerTestOperation
import com.zhousl.aether.data.PackageProfileState
import com.zhousl.aether.data.ProviderModelOption
import com.zhousl.aether.data.RootSetupIssue
import com.zhousl.aether.data.RootSetupState
import com.zhousl.aether.data.ScheduledTask
import com.zhousl.aether.data.ScheduledTaskCreator
import com.zhousl.aether.data.ScheduledTaskSchedule
import com.zhousl.aether.data.TermuxEnvironmentVariable
import com.zhousl.aether.data.formatScheduledTaskTime
import com.zhousl.aether.data.summary
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.availableModels
import com.zhousl.aether.data.enabledModels
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.normalizeTavilyBaseUrl
import com.zhousl.aether.data.quickActionLabel
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.runtime.LocalRuntimeIssue
import com.zhousl.aether.runtime.LocalRuntimeSetupState
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Page enum - drives the local in-composable navigation
// -----------------------------------------------------------------------------

private enum class SettingsPage {
    Hub,
    General,
    Providers,
    DefaultModels,
    DefaultChatModel,
    DefaultTitleModel,
    DefaultNamingModel,
    DefaultCompactingModel,
    AddProvider,
    EditProvider,
    Personalization,
    WebTools,
    Reliability,
    Skills,
    AddSkill,
    McpServers,
    AddMcpServer,
    EditMcpServer,
    ScheduledTasks,
    AddScheduledTask,
    EditScheduledTask,
    Termux,
    Alpine,
    AlpineTerminal,
    RuntimeDefaults,
    AgentMode,
    RootSetupProgress,
    Developer,
    About,
}

private const val SettingsAutoRefreshIntervalMillis = 5_000L

private fun SettingsPage.depth(): Int = when (this) {
    SettingsPage.Hub -> 0
    SettingsPage.General,
    SettingsPage.Providers,
    SettingsPage.Personalization,
    SettingsPage.WebTools,
    SettingsPage.Reliability,
    SettingsPage.Skills,
    SettingsPage.McpServers,
    SettingsPage.ScheduledTasks,
    SettingsPage.Termux,
    SettingsPage.Alpine,
    SettingsPage.AlpineTerminal,
    SettingsPage.RuntimeDefaults,
    SettingsPage.AgentMode,
    SettingsPage.Developer,
    SettingsPage.About -> 1
    SettingsPage.DefaultModels,
    SettingsPage.AddProvider,
    SettingsPage.EditProvider,
    SettingsPage.AddSkill,
    SettingsPage.AddMcpServer,
    SettingsPage.EditMcpServer,
    SettingsPage.AddScheduledTask,
    SettingsPage.EditScheduledTask,
    SettingsPage.RootSetupProgress -> 2
    SettingsPage.DefaultChatModel,
    SettingsPage.DefaultTitleModel,
    SettingsPage.DefaultNamingModel,
    SettingsPage.DefaultCompactingModel -> 3
}

private fun SettingsPage.toRootSetupProgressReturnPage(): RootSetupProgressReturnPage =
    when (this) {
        SettingsPage.AgentMode -> RootSetupProgressReturnPage.AgentMode
        else -> RootSetupProgressReturnPage.Termux
    }

private fun RootSetupProgressReturnPage.toSettingsPage(): SettingsPage =
    when (this) {
        RootSetupProgressReturnPage.Termux -> SettingsPage.Termux
        RootSetupProgressReturnPage.AgentMode -> SettingsPage.AgentMode
    }

private fun formatTaskMinute(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 1_439)
    return "%02d:%02d".format(Locale.US, normalized / 60, normalized % 60)
}

private fun parseTaskMinute(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun parseTaskTimes(value: String): List<Int> =
    value.split(',', ';', '\n')
        .mapNotNull(::parseTaskMinute)
        .distinct()
        .sorted()

private fun parseTaskDays(value: String): List<Int> =
    value.split(',', ';', ' ')
        .mapNotNull { raw ->
            val normalized = raw.trim().lowercase(Locale.US)
            when (normalized.take(3)) {
                "mon" -> 1
                "tue" -> 2
                "wed" -> 3
                "thu" -> 4
                "fri" -> 5
                "sat" -> 6
                "sun" -> 7
                else -> normalized.toIntOrNull()
            }?.takeIf { it in 1..7 }
        }
        .distinct()
        .sorted()

// -----------------------------------------------------------------------------
// Animation constants
// -----------------------------------------------------------------------------

private const val PageTransitionDuration = 320
private val PageTransitionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val SettingsTopFadeHeight = 40.dp

private fun tr(strings: AetherStrings, english: String, chinese: String): String =
    if (strings.appLanguage == AppLanguage.SimplifiedChinese) chinese else english

private fun settingsTopOverlayBodyGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.96f),
        0.18f to AetherBackground.copy(alpha = 0.86f),
        0.42f to AetherBackground.copy(alpha = 0.48f),
        0.72f to AetherBackground.copy(alpha = 0.22f),
        1.0f to AetherBackground.copy(alpha = 0.12f),
    )
)

private fun settingsTopOverlayTailGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.12f),
        0.42f to AetherBackground.copy(alpha = 0.05f),
        1.0f to Color.Transparent,
    )
)

// -----------------------------------------------------------------------------
// Root composable - drop-in replacement for the old SettingsScreen in AetherApp
// -----------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    modelId: String,
    systemPrompt: String,
    tavilyApiKey: String,
    tavilyBaseUrl: String,
    llmInactivityReconnectTimeoutSeconds: Int,
    keepTasksRunningInBackground: Boolean,
    notifyOnTaskCompletion: Boolean,
    agentWorkspaceMode: AgentWorkspaceMode,
    termuxLiveOutputEnabled: Boolean,
    termuxEnvironmentVariables: List<TermuxEnvironmentVariable>,
    agentModeAuthorizationEnabled: Boolean,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    agentModeAuthorizationState: AgentModeAuthorizationState,
    rootSetupState: RootSetupState,
    rootSetupProgressReturnPage: RootSetupProgressReturnPage?,
    language: AppLanguage,
    themeMode: AppThemeMode,
    defaultChatModelKey: String,
    defaultTitleModelKey: String,
    defaultNamingModelKey: String,
    defaultCompactingModelKey: String,
    agentModeDisplayState: AgentModeDisplayState,
    providerConfigs: List<LlmProviderConfig>,
    scheduledTasks: List<ScheduledTask>,
    termuxSetupState: TermuxSetupState,
    alpineSetupState: LocalRuntimeSetupState,
    enabledRuntimeIds: Set<LocalRuntimeId>,
    defaultRuntimeId: LocalRuntimeId?,
    alpinePackageProfiles: Map<String, PackageProfileState>,
    alpineTerminal: AlpineTerminalUiState,
    developerTermuxReadyOverride: Boolean?,
    installedSkills: List<com.zhousl.aether.data.InstalledSkill>,
    mcpServers: List<com.zhousl.aether.data.McpServerConfig>,
    isFetchingModels: Boolean,
    appUpdate: AppUpdateUiState,
    onSave: (
        LlmProvider,
        String,
        String,
        String,
        String,
        String,
        String,
        Int,
        Boolean,
        Boolean,
        AgentWorkspaceMode,
        Boolean,
        List<TermuxEnvironmentVariable>,
        Boolean,
        AgentModeAuthorizationMethod,
        AppLanguage,
        AppThemeMode,
        String,
        String,
        String,
        String,
    ) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onUpsertProviderConfig: (LlmProviderConfig) -> Unit,
    onRemoveProviderConfig: (String) -> Unit,
    onSetProviderEnabled: (String, Boolean) -> Unit,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onImportSkillFolder: () -> Unit,
    onImportSkillZip: ((Boolean) -> Unit) -> Unit,
    onInstallSkillUrl: (String, (Boolean) -> Unit) -> Unit,
    onToggleSkillEnabled: (String, Boolean) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onSaveHttpMcpServer: (String?, String, String, String) -> Unit,
    onSaveStdIoMcpServer: (String?, String, String, String, String, String, LocalRuntimeId?) -> Unit,
    onToggleMcpServerEnabled: (String, Boolean) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onTestMcpServer: (String, McpServerTestOperation, (String) -> Unit) -> Unit,
    onSaveScheduledTask: (String?, String, String, ScheduledTaskSchedule, Boolean) -> Unit,
    onToggleScheduledTaskEnabled: (String, Boolean) -> Unit,
    onRemoveScheduledTask: (String) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onImportAppData: () -> Unit,
    onExportAppData: () -> Unit,
    onExportLogs: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onInitializeAlpineRuntime: () -> Unit,
    onResetAlpineRuntime: () -> Unit,
    onRefreshAlpineSetup: () -> Unit,
    onInstallAlpinePackageProfile: (String) -> Unit,
    onOpenAlpineTerminal: () -> Unit,
    onCloseAlpineTerminal: () -> Unit,
    onSendAlpineTerminalControl: (String) -> Unit,
    onResizeAlpineTerminal: (Int, Int) -> Unit,
    onClearAlpineTerminalOutput: () -> Unit,
    onSetDefaultRuntime: (LocalRuntimeId) -> Unit,
    onRefreshRootSetup: () -> Unit,
    onStartRootSetupFromSettings: (RootSetupProgressReturnPage) -> Unit,
    onDismissRootSetupProgress: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRefreshAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onOpenShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onReplayFollowUpOnboarding: () -> Unit,
    onStopAgentModeDisplay: () -> Unit,
    onRefreshAgentModeDisplays: (AgentModeAuthorizationMethod) -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onForceUpdateCheckForTesting: () -> Unit,
    onSetDeveloperTermuxReadyOverride: (Boolean) -> Unit,
    onDownloadAndInstallUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Mutable field values - survive recomposition & config changes
    var systemPromptValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(systemPrompt))
    }
    var tavilyApiKeyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tavilyApiKey))
    }
    var tavilyBaseUrlValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tavilyBaseUrl))
    }
    var llmInactivityReconnectTimeoutValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(llmInactivityReconnectTimeoutSeconds.toString()))
    }
    var keepTasksRunningInBackgroundValue by rememberSaveable {
        mutableStateOf(keepTasksRunningInBackground)
    }
    var notifyOnTaskCompletionValue by rememberSaveable {
        mutableStateOf(notifyOnTaskCompletion)
    }
    var agentWorkspaceModeValue by rememberSaveable {
        mutableStateOf(agentWorkspaceMode)
    }
    var termuxLiveOutputEnabledValue by rememberSaveable {
        mutableStateOf(termuxLiveOutputEnabled)
    }
    var termuxEnvironmentVariablesValue by rememberSaveable {
        mutableStateOf(termuxEnvironmentVariables)
    }
    var agentModeAuthorizationEnabledValue by rememberSaveable {
        mutableStateOf(agentModeAuthorizationEnabled)
    }
    var agentModeAuthorizationMethodValue by rememberSaveable {
        mutableStateOf(agentModeAuthorizationMethod)
    }
    var languageValue by rememberSaveable {
        mutableStateOf(language)
    }
    var themeModeValue by rememberSaveable {
        mutableStateOf(themeMode)
    }
    var defaultChatModelKeyValue by rememberSaveable { mutableStateOf(defaultChatModelKey) }
    var defaultTitleModelKeyValue by rememberSaveable { mutableStateOf(defaultTitleModelKey) }
    var defaultNamingModelKeyValue by rememberSaveable { mutableStateOf(defaultNamingModelKey) }
    var defaultCompactingModelKeyValue by rememberSaveable { mutableStateOf(defaultCompactingModelKey) }
    val strings = remember(languageValue) { aetherStringsFor(languageValue) }
    val enabledModelOptions = remember(providerConfigs) { providerConfigs.availableModelOptions() }

    // Track which provider is being edited
    var editingProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingMcpServerId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingScheduledTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastObservedRootSetupIssue by rememberSaveable { mutableStateOf(rootSetupState.issue) }

    LaunchedEffect(rootSetupState.issue) {
        if (
            rootSetupState.issue == RootSetupIssue.Ready &&
            lastObservedRootSetupIssue != RootSetupIssue.Ready
        ) {
            agentModeAuthorizationEnabledValue = true
            agentModeAuthorizationMethodValue = AgentModeAuthorizationMethod.Root
        }
        lastObservedRootSetupIssue = rootSetupState.issue
    }

    fun persistAndExit() {
        val compatibilityOption = enabledModelOptions.firstOrNull()
        onSave(
            compatibilityOption?.providerType ?: provider,
            compatibilityOption?.apiKey ?: apiKey,
            compatibilityOption?.baseUrl ?: baseUrl,
            compatibilityOption?.modelId ?: modelId,
            systemPromptValue.text,
            tavilyApiKeyValue.text,
            normalizeTavilyBaseUrl(tavilyBaseUrlValue.text),
            normalizeLlmInactivityReconnectTimeoutSeconds(
                llmInactivityReconnectTimeoutValue.text.trim().toIntOrNull()
            ),
            keepTasksRunningInBackgroundValue,
            notifyOnTaskCompletionValue,
            agentWorkspaceModeValue,
            termuxLiveOutputEnabledValue,
            termuxEnvironmentVariablesValue,
            agentModeAuthorizationEnabledValue,
            agentModeAuthorizationMethodValue,
            languageValue,
            themeModeValue,
            defaultChatModelKeyValue,
            defaultTitleModelKeyValue,
            defaultNamingModelKeyValue,
            defaultCompactingModelKeyValue,
        )
        onBack()
    }

    fun persistAndReplayOnboarding() {
        val compatibilityOption = enabledModelOptions.firstOrNull()
        onSave(
            compatibilityOption?.providerType ?: provider,
            compatibilityOption?.apiKey ?: apiKey,
            compatibilityOption?.baseUrl ?: baseUrl,
            compatibilityOption?.modelId ?: modelId,
            systemPromptValue.text,
            tavilyApiKeyValue.text,
            normalizeTavilyBaseUrl(tavilyBaseUrlValue.text),
            normalizeLlmInactivityReconnectTimeoutSeconds(
                llmInactivityReconnectTimeoutValue.text.trim().toIntOrNull()
            ),
            keepTasksRunningInBackgroundValue,
            notifyOnTaskCompletionValue,
            agentWorkspaceModeValue,
            termuxLiveOutputEnabledValue,
            termuxEnvironmentVariablesValue,
            agentModeAuthorizationEnabledValue,
            agentModeAuthorizationMethodValue,
            languageValue,
            themeModeValue,
            defaultChatModelKeyValue,
            defaultTitleModelKeyValue,
            defaultNamingModelKeyValue,
            defaultCompactingModelKeyValue,
        )
        onReplayOnboarding()
    }

    fun persistAndReplayFollowUpOnboarding() {
        val compatibilityOption = enabledModelOptions.firstOrNull()
        onSave(
            compatibilityOption?.providerType ?: provider,
            compatibilityOption?.apiKey ?: apiKey,
            compatibilityOption?.baseUrl ?: baseUrl,
            compatibilityOption?.modelId ?: modelId,
            systemPromptValue.text,
            tavilyApiKeyValue.text,
            normalizeTavilyBaseUrl(tavilyBaseUrlValue.text),
            normalizeLlmInactivityReconnectTimeoutSeconds(
                llmInactivityReconnectTimeoutValue.text.trim().toIntOrNull()
            ),
            keepTasksRunningInBackgroundValue,
            notifyOnTaskCompletionValue,
            agentWorkspaceModeValue,
            termuxLiveOutputEnabledValue,
            termuxEnvironmentVariablesValue,
            agentModeAuthorizationEnabledValue,
            agentModeAuthorizationMethodValue,
            languageValue,
            themeModeValue,
            defaultChatModelKeyValue,
            defaultTitleModelKeyValue,
            defaultNamingModelKeyValue,
            defaultCompactingModelKeyValue,
        )
        onReplayFollowUpOnboarding()
    }

    // Local page navigation
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Hub.name) }
    val page = SettingsPage.valueOf(currentPage)
    var rootSetupReturnPage by rememberSaveable { mutableStateOf(SettingsPage.Termux.name) }

    fun rootSetupReturnPageValue(): SettingsPage =
        runCatching { SettingsPage.valueOf(rootSetupReturnPage) }
            .getOrDefault(SettingsPage.Termux)

    fun openRootSetupProgress(returnPage: SettingsPage) {
        rootSetupReturnPage = returnPage.name
        currentPage = SettingsPage.RootSetupProgress.name
        onStartRootSetupFromSettings(returnPage.toRootSetupProgressReturnPage())
    }

    fun closeRootSetupProgress() {
        onDismissRootSetupProgress()
        currentPage = rootSetupReturnPageValue().name
    }

    fun runRootSetupAgain() {
        onStartRootSetupFromSettings(rootSetupReturnPageValue().toRootSetupProgressReturnPage())
    }

    LaunchedEffect(rootSetupProgressReturnPage) {
        val returnPage = rootSetupProgressReturnPage ?: return@LaunchedEffect
        rootSetupReturnPage = returnPage.toSettingsPage().name
        currentPage = SettingsPage.RootSetupProgress.name
    }

    // Determine parent page for back navigation
    fun parentPage(): SettingsPage = when (page) {
        SettingsPage.DefaultModels -> SettingsPage.Providers
        SettingsPage.DefaultChatModel,
        SettingsPage.DefaultTitleModel,
        SettingsPage.DefaultNamingModel,
        SettingsPage.DefaultCompactingModel -> SettingsPage.DefaultModels
        SettingsPage.AddProvider, SettingsPage.EditProvider -> SettingsPage.Providers
        SettingsPage.AddSkill -> SettingsPage.Skills
        SettingsPage.AddMcpServer, SettingsPage.EditMcpServer -> SettingsPage.McpServers
        SettingsPage.AddScheduledTask, SettingsPage.EditScheduledTask -> SettingsPage.ScheduledTasks
        SettingsPage.AlpineTerminal -> SettingsPage.Alpine
        SettingsPage.RootSetupProgress -> rootSetupReturnPageValue()
        else -> SettingsPage.Hub
    }

    BackHandler {
        when (page) {
            SettingsPage.Hub -> persistAndExit()
            SettingsPage.RootSetupProgress -> closeRootSetupProgress()
            else -> currentPage = parentPage().name
        }
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val isForward = targetState.depth() > initialState.depth()
            val enterSlide = slideInHorizontally(
                animationSpec = tween(PageTransitionDuration, easing = PageTransitionEasing),
                initialOffsetX = { if (isForward) it / 3 else -it / 3 },
            ) + fadeIn(tween(PageTransitionDuration, easing = PageTransitionEasing))
            val exitSlide = slideOutHorizontally(
                animationSpec = tween(PageTransitionDuration, easing = PageTransitionEasing),
                targetOffsetX = { if (isForward) -it / 3 else it / 3 },
            ) + fadeOut(tween(PageTransitionDuration, easing = PageTransitionEasing))
            enterSlide togetherWith exitSlide
        },
        label = "settings_page_transition",
    ) { targetPage ->
        when (targetPage) {
            SettingsPage.Hub -> SettingsHub(
                strings = strings,
                generalSettingsSummary = strings.generalSettingsSummary(
                    language = languageValue,
                    themeMode = themeModeValue,
                ),
                activeProviderName = providerConfigs.count { it.isEnabled }.let { enabledCount ->
                    when {
                        enabledCount > 1 -> tr(strings, "$enabledCount providers enabled", "已启用 $enabledCount 个 Provider")
                        enabledCount == 1 -> providerConfigs.firstOrNull { it.isEnabled }?.name.orEmpty()
                        enabledModelOptions.isNotEmpty() -> enabledModelOptions.first().fullLabel
                        else -> provider.displayName
                    }
                },
                systemPromptSnippet = systemPromptValue.text.take(60),
                tavilyConfigured = tavilyApiKeyValue.text.isNotBlank(),
                reliabilitySummary = buildString {
                    append(
                        "Reconnect after ${
                            normalizeLlmInactivityReconnectTimeoutSeconds(
                                llmInactivityReconnectTimeoutValue.text.trim().toIntOrNull()
                            )
                        }s"
                    )
                    append(" · ")
                    append(
                        if (keepTasksRunningInBackgroundValue) {
                            "Background runs on"
                        } else {
                            "Background runs off"
                        }
                    )
                },
                termuxReady = termuxSetupState.isReady,
                alpineReady = alpineSetupState.isReady,
                defaultRuntimeId = defaultRuntimeId,
                showRuntimeDefaults = termuxSetupState.isReady && alpineSetupState.isReady,
                skillCount = installedSkills.size,
                mcpServerCount = mcpServers.size,
                scheduledTaskCount = scheduledTasks.size,
                onReplayOnboarding = ::persistAndReplayOnboarding,
                onNavigate = { page ->
                    if (page == SettingsPage.AgentMode && !termuxSetupState.isReady) {
                        Toast.makeText(
                            context,
                            tr(strings, "Complete Termux setup before configuring Agent Mode.", "请先完成 Termux 设置，再配置 Agent 模式。"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        currentPage = page.name
                    }
                },
                onBack = ::persistAndExit,
            )

            SettingsPage.General -> GeneralSettingsPageV2(
                strings = strings,
                selectedLanguage = languageValue,
                onLanguageSelected = {
                    languageValue = it
                    onUpdateLanguage(it)
                },
                selectedThemeMode = themeModeValue,
                onThemeModeSelected = {
                    themeModeValue = it
                    onUpdateThemeMode(it)
                },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Providers -> ProvidersListPage(
                providerConfigs = providerConfigs,
                onSetProviderEnabled = onSetProviderEnabled,
                onOpenDefaultModels = { currentPage = SettingsPage.DefaultModels.name },
                onEdit = { id ->
                    editingProviderId = id
                    currentPage = SettingsPage.EditProvider.name
                },
                onRemove = onRemoveProviderConfig,
                onAddNew = { currentPage = SettingsPage.AddProvider.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.DefaultModels -> DefaultModelsPage(
                modelOptions = enabledModelOptions,
                defaultChatModelKey = defaultChatModelKeyValue,
                defaultTitleModelKey = defaultTitleModelKeyValue,
                defaultNamingModelKey = defaultNamingModelKeyValue,
                defaultCompactingModelKey = defaultCompactingModelKeyValue,
                onOpenDefaultChatModel = { currentPage = SettingsPage.DefaultChatModel.name },
                onOpenDefaultTitleModel = { currentPage = SettingsPage.DefaultTitleModel.name },
                onOpenDefaultNamingModel = { currentPage = SettingsPage.DefaultNamingModel.name },
                onOpenDefaultCompactingModel = { currentPage = SettingsPage.DefaultCompactingModel.name },
                onBack = { currentPage = SettingsPage.Providers.name },
            )

            SettingsPage.DefaultChatModel -> ModelSelectionListPage(
                title = tr(strings, "Default Chat Model", "默认聊天模型"),
                subtitle = tr(strings, "Used for new chats and when a conversation has not selected a model yet.", "用于新建聊天，以及当前会话尚未单独选择模型时。"),
                selectedKey = defaultChatModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
                )?.fullLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                    ?: tr(strings, "Automatic", "自动选择"),
                automaticSubtitle = tr(strings, "Prioritize the SOTA models", "优先选择前沿模型"),
                onSelected = { defaultChatModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultTitleModel -> ModelSelectionListPage(
                title = tr(strings, "Default Title Model", "默认标题模型"),
                subtitle = tr(strings, "Used when Aether automatically generates conversation titles.", "用于 Aether 自动生成会话标题。"),
                selectedKey = defaultTitleModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Title)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                    ?: tr(strings, "Automatic", "自动选择"),
                automaticSubtitle = tr(strings, "Prioritize the SOTA models", "优先选择前沿模型"),
                onSelected = { defaultTitleModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultNamingModel -> ModelSelectionListPage(
                title = tr(strings, "Default Naming Model", "默认命名模型"),
                subtitle = tr(strings, "Used for automatic naming flows such as Agent Skills and MCP labels.", "用于 Agent Skills、MCP 等自动命名流程。"),
                selectedKey = defaultNamingModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Naming)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                    ?: tr(strings, "Automatic", "自动选择"),
                automaticSubtitle = tr(strings, "Prioritize the SOTA models", "优先选择前沿模型"),
                onSelected = { defaultNamingModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultCompactingModel -> ModelSelectionListPage(
                title = tr(strings, "Default Compacting Model", "默认压缩模型"),
                subtitle = tr(strings, "Used when /compact summarizes the current conversation.", "用于 /compact 总结当前会话。"),
                selectedKey = defaultCompactingModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Compacting)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                    ?: tr(strings, "Automatic", "自动选择"),
                automaticSubtitle = tr(strings, "Prioritize efficient summary models", "优先选择高效总结模型"),
                onSelected = { defaultCompactingModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.AddProvider -> ProviderEditPage(
                existingConfig = null,
                existingProviderIds = providerConfigs.map { it.providerId }.toSet(),
                isFetchingModels = isFetchingModels,
                onSave = { config ->
                    onUpsertProviderConfig(config)
                    currentPage = SettingsPage.Providers.name
                },
                onModelEnabledChange = {},
                onFetchModels = onFetchModels,
                onBack = { currentPage = SettingsPage.Providers.name },
            )

            SettingsPage.EditProvider -> {
                val configToEdit = providerConfigs.firstOrNull { it.id == editingProviderId }
                ProviderEditPage(
                    existingConfig = configToEdit,
                    existingProviderIds = providerConfigs.map { it.providerId }.toSet(),
                    isFetchingModels = isFetchingModels,
                    onSave = { config ->
                        onUpsertProviderConfig(config)
                        currentPage = SettingsPage.Providers.name
                    },
                    onModelEnabledChange = onUpsertProviderConfig,
                    onFetchModels = onFetchModels,
                    onBack = { currentPage = SettingsPage.Providers.name },
                )
            }

            SettingsPage.Personalization -> PersonalizationPage(
                title = strings.personalization,
                systemPromptValue = systemPromptValue,
                onSystemPromptChanged = { systemPromptValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.WebTools -> WebToolsPage(
                title = strings.webTools,
                tavilyApiKeyValue = tavilyApiKeyValue,
                onTavilyApiKeyChanged = { tavilyApiKeyValue = it },
                tavilyBaseUrlValue = tavilyBaseUrlValue,
                onTavilyBaseUrlChanged = { tavilyBaseUrlValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Reliability -> ReliabilityPage(
                title = strings.reliability,
                llmInactivityReconnectTimeoutValue = llmInactivityReconnectTimeoutValue,
                onLlmInactivityReconnectTimeoutChanged = { llmInactivityReconnectTimeoutValue = it },
                keepTasksRunningInBackground = keepTasksRunningInBackgroundValue,
                onKeepTasksRunningInBackgroundChanged = { keepTasksRunningInBackgroundValue = it },
                notifyOnTaskCompletion = notifyOnTaskCompletionValue,
                onNotifyOnTaskCompletionChanged = { notifyOnTaskCompletionValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Skills -> SkillsListPage(
                title = strings.agentSkills,
                installedSkills = installedSkills,
                onToggleSkillEnabled = onToggleSkillEnabled,
                onRemoveSkill = onRemoveSkill,
                onAddNew = { currentPage = SettingsPage.AddSkill.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AddSkill -> AddSkillPage(
                title = strings.agentSkills,
                onImportSkillFolder = onImportSkillFolder,
                onImportSkillZip = { callback ->
                    onImportSkillZip { success ->
                        callback(success)
                        if (success) currentPage = SettingsPage.Skills.name
                    }
                },
                onInstallSkillUrl = { url, callback ->
                    onInstallSkillUrl(url) { success ->
                        callback(success)
                        if (success) currentPage = SettingsPage.Skills.name
                    }
                },
                onBack = { currentPage = SettingsPage.Skills.name },
            )

            SettingsPage.McpServers -> McpServersListPage(
                title = strings.mcpServers,
                mcpServers = mcpServers,
                onToggleMcpServerEnabled = onToggleMcpServerEnabled,
                onRemoveMcpServer = onRemoveMcpServer,
                onTestMcpServer = onTestMcpServer,
                onEdit = { serverId ->
                    editingMcpServerId = serverId
                    currentPage = SettingsPage.EditMcpServer.name
                },
                onAddNew = { currentPage = SettingsPage.AddMcpServer.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AddMcpServer -> AddMcpServerPage(
                title = strings.mcpServers,
                existingServer = null,
                onSaveHttpMcpServer = { serverId, name, url, headers ->
                    onSaveHttpMcpServer(serverId, name, url, headers)
                    currentPage = SettingsPage.McpServers.name
                },
                onSaveStdIoMcpServer = { serverId, name, cmd, args, wd, env, runtimeEnvironment ->
                    onSaveStdIoMcpServer(serverId, name, cmd, args, wd, env, runtimeEnvironment)
                    currentPage = SettingsPage.McpServers.name
                },
                onBack = { currentPage = SettingsPage.McpServers.name },
            )

            SettingsPage.EditMcpServer -> AddMcpServerPage(
                title = strings.mcpServers,
                existingServer = mcpServers.firstOrNull { it.id == editingMcpServerId },
                onSaveHttpMcpServer = { serverId, name, url, headers ->
                    onSaveHttpMcpServer(serverId, name, url, headers)
                    currentPage = SettingsPage.McpServers.name
                },
                onSaveStdIoMcpServer = { serverId, name, cmd, args, wd, env, runtimeEnvironment ->
                    onSaveStdIoMcpServer(serverId, name, cmd, args, wd, env, runtimeEnvironment)
                    currentPage = SettingsPage.McpServers.name
                },
                onBack = { currentPage = SettingsPage.McpServers.name },
            )

            SettingsPage.ScheduledTasks -> ScheduledTasksPage(
                tasks = scheduledTasks,
                onToggleEnabled = onToggleScheduledTaskEnabled,
                onRemove = onRemoveScheduledTask,
                onEdit = { taskId ->
                    editingScheduledTaskId = taskId
                    currentPage = SettingsPage.EditScheduledTask.name
                },
                onAddNew = { currentPage = SettingsPage.AddScheduledTask.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AddScheduledTask -> ScheduledTaskEditPage(
                existingTask = null,
                onSave = { name, prompt, schedule, enabled ->
                    onSaveScheduledTask(null, name, prompt, schedule, enabled)
                    currentPage = SettingsPage.ScheduledTasks.name
                },
                onBack = { currentPage = SettingsPage.ScheduledTasks.name },
            )

            SettingsPage.EditScheduledTask -> ScheduledTaskEditPage(
                existingTask = scheduledTasks.firstOrNull { it.id == editingScheduledTaskId },
                onSave = { name, prompt, schedule, enabled ->
                    onSaveScheduledTask(editingScheduledTaskId, name, prompt, schedule, enabled)
                    currentPage = SettingsPage.ScheduledTasks.name
                },
                onBack = { currentPage = SettingsPage.ScheduledTasks.name },
            )

            SettingsPage.Termux -> TermuxSettingsPage(
                title = strings.termux,
                termuxSetupState = termuxSetupState,
                rootSetupState = rootSetupState,
                selectedWorkspaceMode = agentWorkspaceModeValue,
                liveOutputEnabled = termuxLiveOutputEnabledValue,
                environmentVariables = termuxEnvironmentVariablesValue,
                onWorkspaceModeSelected = { agentWorkspaceModeValue = it },
                onLiveOutputEnabledChanged = { termuxLiveOutputEnabledValue = it },
                onEnvironmentVariablesChanged = { termuxEnvironmentVariablesValue = it },
                onRequestTermuxPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefreshTermuxSetup = onRefreshTermuxSetup,
                onRefreshRootSetup = onRefreshRootSetup,
                onConfigureWithRoot = { openRootSetupProgress(SettingsPage.Termux) },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Alpine -> AlpineSettingsPage(
                title = "Alpine",
                setupState = alpineSetupState,
                packageProfiles = alpinePackageProfiles,
                isDefaultRuntime = defaultRuntimeId == LocalRuntimeId.Alpine,
                onInitialize = onInitializeAlpineRuntime,
                onReset = onResetAlpineRuntime,
                onRefresh = onRefreshAlpineSetup,
                onInstallPackageProfile = { profileId ->
                    currentPage = SettingsPage.AlpineTerminal.name
                    onOpenAlpineTerminal()
                    onInstallAlpinePackageProfile(profileId)
                },
                onOpenTerminal = {
                    currentPage = SettingsPage.AlpineTerminal.name
                    onOpenAlpineTerminal()
                },
                onSetDefault = { onSetDefaultRuntime(LocalRuntimeId.Alpine) },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AlpineTerminal -> AlpineTerminalPage(
                terminal = alpineTerminal,
                onSendControl = onSendAlpineTerminalControl,
                onResize = onResizeAlpineTerminal,
                onClear = onClearAlpineTerminalOutput,
                onBack = {
                    onCloseAlpineTerminal()
                    currentPage = SettingsPage.Alpine.name
                },
            )

            SettingsPage.RuntimeDefaults -> RuntimeDefaultsPage(
                title = tr(strings, "Runtime Defaults", "运行环境默认值"),
                termuxReady = termuxSetupState.isReady,
                alpineReady = alpineSetupState.isReady,
                enabledRuntimeIds = enabledRuntimeIds,
                defaultRuntimeId = defaultRuntimeId,
                onSetDefaultRuntime = onSetDefaultRuntime,
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AgentMode -> AgentModeSettingsPage(
                title = strings.agentMode,
                termuxSetupState = termuxSetupState,
                agentModeAuthorizationEnabled = agentModeAuthorizationEnabledValue,
                agentModeAuthorizationMethod = agentModeAuthorizationMethodValue,
                agentModeAuthorizationState = agentModeAuthorizationState,
                rootSetupState = rootSetupState,
                onAgentModeAuthorizationEnabledChanged = { agentModeAuthorizationEnabledValue = it },
                onAgentModeAuthorizationMethodChanged = { agentModeAuthorizationMethodValue = it },
                agentModeDisplayState = agentModeDisplayState,
                onRequestShizukuPermission = onRequestShizukuPermission,
                onRefreshAgentModeAuthorization = onRefreshAgentModeAuthorization,
                onOpenShizuku = onOpenShizuku,
                onInstallShizuku = onInstallShizuku,
                onRequestTermuxPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefreshTermuxSetup = onRefreshTermuxSetup,
                onRefreshRootSetup = onRefreshRootSetup,
                onConfigureWithRoot = { openRootSetupProgress(SettingsPage.AgentMode) },
                onStopAgentModeDisplay = onStopAgentModeDisplay,
                onRefreshAgentModeDisplays = onRefreshAgentModeDisplays,
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.RootSetupProgress -> RootSetupProgressPage(
                rootSetupState = rootSetupState,
                termuxSetupState = termuxSetupState,
                agentModeAuthorizationEnabled = agentModeAuthorizationEnabledValue,
                agentModeAuthorizationMethod = agentModeAuthorizationMethodValue,
                agentModeAuthorizationState = agentModeAuthorizationState,
                onRunRootSetup = ::runRootSetupAgain,
                onBack = ::closeRootSetupProgress,
            )

            SettingsPage.Developer -> DeveloperSettingsPage(
                title = strings.developerSettings,
                onReplayFollowUpOnboarding = ::persistAndReplayFollowUpOnboarding,
                onImportAppData = onImportAppData,
                onExportAppData = onExportAppData,
                onExportLogs = onExportLogs,
                onForceUpdateCheckForTesting = onForceUpdateCheckForTesting,
                termuxReadyForTesting = developerTermuxReadyOverride ?: termuxSetupState.isReady,
                onTermuxReadyForTestingChanged = onSetDeveloperTermuxReadyOverride,
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.About -> AboutPage(
                title = strings.about,
                appUpdate = appUpdate,
                onOpenWebsite = onOpenWebsite,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                onBack = { currentPage = SettingsPage.Hub.name },
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Hub
// -----------------------------------------------------------------------------

@Composable
private fun SettingsHub(
    strings: AetherStrings,
    generalSettingsSummary: String,
    activeProviderName: String,
    systemPromptSnippet: String,
    tavilyConfigured: Boolean,
    reliabilitySummary: String,
    termuxReady: Boolean,
    alpineReady: Boolean,
    defaultRuntimeId: LocalRuntimeId?,
    showRuntimeDefaults: Boolean,
    skillCount: Int,
    mcpServerCount: Int,
    scheduledTaskCount: Int,
    onReplayOnboarding: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    var topBarBodyHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fallbackTopBarBodyHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 68.dp
    }
    val topBarBodyHeight = with(density) {
        if (topBarBodyHeightPx > 0) topBarBodyHeightPx.toDp() else fallbackTopBarBodyHeight
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topBarBodyHeight)
                    .padding(horizontal = 20.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Spacer(Modifier.height(6.dp))
                SettingsCardGroup {
                    SettingsNavRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = strings.generalSettings,
                        subtitle = generalSettingsSummary.ifBlank { strings.generalSettingsHubHint },
                        onClick = { onNavigate(SettingsPage.General) },
                    )
                }

                Spacer(Modifier.height(16.dp))

            // Configuration card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Cloud,
                    title = strings.modelProviders,
                    subtitle = activeProviderName,
                    onClick = { onNavigate(SettingsPage.Providers) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Person,
                    title = strings.personalization,
                    subtitle = systemPromptSnippet.ifBlank { strings.customInstructions },
                    onClick = { onNavigate(SettingsPage.Personalization) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Link,
                    title = strings.webTools,
                    subtitle = if (tavilyConfigured) {
                        strings.tavilyConfigured
                    } else {
                        strings.tavilyNotConfigured
                    },
                    onClick = { onNavigate(SettingsPage.WebTools) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Refresh,
                    title = strings.reliability,
                    subtitle = reliabilitySummary,
                    onClick = { onNavigate(SettingsPage.Reliability) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Extensions card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Extension,
                    title = strings.agentSkills,
                    subtitle = strings.skillCountSummary(skillCount),
                    onClick = { onNavigate(SettingsPage.Skills) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = strings.mcpServers,
                    subtitle = strings.serverCountSummary(mcpServerCount),
                    onClick = { onNavigate(SettingsPage.McpServers) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Schedule,
                    title = tr(strings, "Scheduled Tasks", "定时任务"),
                    subtitle = if (scheduledTaskCount == 0) {
                        tr(strings, "No scheduled tasks", "暂无定时任务")
                    } else {
                        tr(strings, "$scheduledTaskCount configured", "已配置 $scheduledTaskCount 个")
                    },
                    onClick = { onNavigate(SettingsPage.ScheduledTasks) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Terminal,
                    title = strings.termux,
                    subtitle = if (termuxReady) strings.connected else strings.setupRequired,
                    onClick = { onNavigate(SettingsPage.Termux) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = "Alpine",
                    subtitle = if (alpineReady) {
                        tr(strings, "Built-in Linux VM ready", "内置 Linux 虚拟机已就绪")
                    } else {
                        tr(strings, "Built-in Linux VM setup", "内置 Linux 虚拟机配置")
                    },
                    onClick = { onNavigate(SettingsPage.Alpine) },
                )
                if (showRuntimeDefaults) {
                    CardDivider()
                    SettingsNavRow(
                        icon = Icons.Rounded.Check,
                        title = tr(strings, "Runtime Defaults", "运行环境默认值"),
                        subtitle = defaultRuntimeId?.displayName
                            ?: tr(strings, "Choose the default local runtime", "选择默认本地运行环境"),
                        onClick = { onNavigate(SettingsPage.RuntimeDefaults) },
                    )
                }
                CardDivider()
                SettingsNavRow(
                    icon = LucideIcons.MousePointer2,
                    title = strings.agentMode,
                    subtitle = if (termuxReady) {
                        strings.agentModeSubtitle
                    } else {
                        tr(strings, "Requires Termux setup", "Requires Termux setup")
                    },
                    enabled = termuxReady,
                    onClick = { onNavigate(SettingsPage.AgentMode) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // About card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = strings.getStartedTour,
                    subtitle = strings.getStartedTourSubtitle,
                    onClick = onReplayOnboarding,
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = strings.developerSettings,
                    subtitle = strings.developerSettingsSubtitle,
                    onClick = { onNavigate(SettingsPage.Developer) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = strings.about,
                    subtitle = "Release ${BuildConfig.VERSION_NAME}",
                    onClick = { onNavigate(SettingsPage.About) },
                )
            }

            Spacer(Modifier.height(32.dp))
            }

            SettingsTopBarOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                title = strings.settings,
                onBack = onBack,
                onBodyHeightChanged = { topBarBodyHeightPx = it },
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Providers List Page (Multi-Provider)
// -----------------------------------------------------------------------------

@Composable
private fun GeneralSettingsPage(
    strings: AetherStrings,
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SubPageScaffold(title = strings.generalSettings, onBack = onBack) {
        SettingsCardGroup {
            Text(
                text = strings.language,
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.languageDescription,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLanguage.entries.forEach { option ->
                    SettingsChoiceRow(
                        title = strings.languageDisplayName(option),
                        subtitle = if (option == AppLanguage.English) {
                            "English interface"
                        } else {
                            "Simplified Chinese interface"
                        },
                        selected = option == selectedLanguage,
                        onClick = { onLanguageSelected(option) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Text(
                text = strings.theme,
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.themeDescription,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemeMode.entries.forEach { option ->
                    SettingsChoiceRow(
                        title = strings.themeDisplayName(option),
                        subtitle = if (option == AppThemeMode.Light) {
                            strings.lightThemeSubtitle
                        } else {
                            strings.darkThemeSubtitle
                        },
                        selected = option == selectedThemeMode,
                        onClick = { onThemeModeSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsPageV2(
    strings: AetherStrings,
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SubPageScaffold(title = strings.generalSettings, onBack = onBack) {
        SettingsCardGroup {
            SelectionDropdownField(
                label = strings.language,
                supportingText = strings.languageDescription,
                selectedLabel = strings.languageDisplayName(selectedLanguage),
                options = AppLanguage.entries.map { option ->
                    SelectionOption(
                        key = option.storageValue,
                        title = strings.languageDisplayName(option),
                        subtitle = if (option == AppLanguage.English) {
                            "English interface"
                        } else {
                            "Simplified Chinese interface"
                        },
                        selected = option == selectedLanguage,
                        onClick = { onLanguageSelected(option) },
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            SelectionDropdownField(
                label = strings.theme,
                supportingText = strings.themeDescription,
                selectedLabel = strings.themeDisplayName(selectedThemeMode),
                options = AppThemeMode.entries.map { option ->
                    SelectionOption(
                        key = option.storageValue,
                        title = strings.themeDisplayName(option),
                        subtitle = strings.themeSubtitle(option),
                        selected = option == selectedThemeMode,
                        onClick = { onThemeModeSelected(option) },
                    )
                },
            )
        }
    }
}

@Composable
private fun TermuxEnvironmentVariablesSection(
    variables: List<TermuxEnvironmentVariable>,
    onVariablesChanged: (List<TermuxEnvironmentVariable>) -> Unit,
) {
    val strings = rememberAetherStrings()
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr(strings, "Environment variables", "环境变量"),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr(
                    strings,
                    "Injected into every Termux bash command, for example HTTP_PROXY or HTTPS_PROXY.",
                    "每次运行 Termux bash 命令时都会注入，例如 HTTP_PROXY 或 HTTPS_PROXY。",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            val rows = if (variables.isEmpty()) {
                listOf(TermuxEnvironmentVariable("", ""))
            } else {
                variables
            }
            fun commitRows(updatedRows: List<TermuxEnvironmentVariable>) {
                onVariablesChanged(
                    updatedRows.filter { it.name.isNotBlank() || it.value.isNotBlank() }
                )
            }
            rows.forEachIndexed { index, variable ->
                var nameValue by rememberSaveable(index, variable.name, stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue(variable.name))
                }
                var valueValue by rememberSaveable(index, variable.value, stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue(variable.value))
                }
                fun commitRow(name: String = nameValue.text, value: String = valueValue.text) {
                    val updated = rows.toMutableList()
                    updated[index] = TermuxEnvironmentVariable(name, value)
                    commitRows(updated)
                }

                ChatGptTextField(
                    label = tr(strings, "Name", "名称"),
                    value = nameValue,
                    onValueChange = {
                        nameValue = it
                        commitRow(name = it.text)
                    },
                )
                ChatGptTextField(
                    label = tr(strings, "Value", "值"),
                    value = valueValue,
                    onValueChange = {
                        valueValue = it
                        commitRow(value = it.text)
                    },
                )
                if (variable.name.isNotBlank() || variable.value.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    SettingsSubtleActionButton(
                        label = tr(strings, "Remove variable", "删除变量"),
                        onClick = {
                            commitRows(rows.filterIndexed { rowIndex, _ -> rowIndex != index })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            SettingsSubtleActionButton(
                label = tr(strings, "Add variable", "添加变量"),
                onClick = { onVariablesChanged(variables + TermuxEnvironmentVariable("", "")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProvidersListPage(
    providerConfigs: List<LlmProviderConfig>,
    onSetProviderEnabled: (String, Boolean) -> Unit,
    onOpenDefaultModels: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddNew: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = strings.modelProviders,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        if (providerConfigs.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr(strings, "No providers configured", "未配置模型提供方"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(strings, "Add a provider to connect to an LLM API.", "Add a provider to connect to an LLM API."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = tr(strings, "Add Provider", "Add Provider"),
                        onClick = onAddNew,
                    )
                }
            }
        } else {
            providerConfigs.forEach { config ->
                ProviderCard(
                    config = config,
                    onEnabledChange = { enabled -> onSetProviderEnabled(config.id, enabled) },
                    onEdit = { onEdit(config.id) },
                    onRemove = { onRemove(config.id) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsCardGroup {
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = tr(strings, "Default Models", "默认模型"),
                subtitle = tr(strings, "Choose dedicated defaults for chat, titles, naming, and compaction.", "为聊天、标题、命名和压缩分别设置默认模型。"),
                onClick = onOpenDefaultModels,
            )
        }
    }
}

@Composable
private fun DefaultModelsPage(
    modelOptions: List<ProviderModelOption>,
    defaultChatModelKey: String,
    defaultTitleModelKey: String,
    defaultNamingModelKey: String,
    defaultCompactingModelKey: String,
    onOpenDefaultChatModel: () -> Unit,
    onOpenDefaultTitleModel: () -> Unit,
    onOpenDefaultNamingModel: () -> Unit,
    onOpenDefaultCompactingModel: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val automaticChatLabel = modelOptions.findModelOption(
        modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
    )?.fullLabel
    val automaticTitleLabel = modelOptions.findModelOption(
        modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Title)
            .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
    )?.fullLabel
    val automaticNamingLabel = modelOptions.findModelOption(
        modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Naming)
            .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
    )?.fullLabel
    val automaticCompactingLabel = modelOptions.findModelOption(
        modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Compacting)
            .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
    )?.fullLabel
    SubPageScaffold(
        title = tr(strings, "Default Models", "默认模型"),
        onBack = onBack,
    ) {
        SettingsCardGroup {
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = tr(strings, "Default Chat Model", "默认聊天模型"),
                subtitle = if (defaultChatModelKey.isBlank()) {
                    automaticChatLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                        ?: tr(strings, "Automatic", "自动选择")
                } else {
                    modelOptions.findModelOption(defaultChatModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultChatModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Edit,
                title = tr(strings, "Default Title Model", "默认标题模型"),
                subtitle = if (defaultTitleModelKey.isBlank()) {
                    automaticTitleLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                        ?: tr(strings, "Automatic", "自动选择")
                } else {
                    modelOptions.findModelOption(defaultTitleModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultTitleModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Person,
                title = tr(strings, "Default Naming Model", "默认命名模型"),
                subtitle = if (defaultNamingModelKey.isBlank()) {
                    automaticNamingLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                        ?: tr(strings, "Automatic", "自动选择")
                } else {
                    modelOptions.findModelOption(defaultNamingModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultNamingModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = tr(strings, "Default Compacting Model", "默认压缩模型"),
                subtitle = if (defaultCompactingModelKey.isBlank()) {
                    automaticCompactingLabel?.let { tr(strings, "Automatic · $it", "自动选择 · $it") }
                        ?: tr(strings, "Automatic", "自动选择")
                } else {
                    modelOptions.findModelOption(defaultCompactingModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultCompactingModel,
            )
        }
    }
}

@Composable
private fun ProviderCard(
    config: LlmProviderConfig,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val availableModels = config.availableModels()
    val enabledModels = config.enabledModels()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = config.isEnabled,
            onCheckedChange = { enabled -> onEnabledChange(enabled) },
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${config.providerType.displayName} · ${config.providerId}",
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tr(
                    strings,
                    "${enabledModels.size} of ${availableModels.size} models enabled",
                    "已启用 ${enabledModels.size}/${availableModels.size} 个模型",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = tr(strings, "Edit", "编辑"),
                tint = AetherOnSurfaceVariant,
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = tr(strings, "Remove", "移除"),
                tint = Color(0xFFD25757),
            )
        }
    }
}

@Composable
private fun ModelSelectionListPage(
    title: String,
    subtitle: String,
    selectedKey: String,
    options: List<ProviderModelOption>,
    automaticLabel: String,
    automaticSubtitle: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val selectedOption = options.findModelOption(selectedKey)

    SubPageScaffold(
        title = title,
        onBack = onBack,
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        if (options.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tr(strings, "No enabled models available", "没有可用的已启用模型"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Text(
                        text = tr(strings, "Enable at least one provider model first, then return here to choose a default.", "请先启用至少一个 Provider 模型，然后再回来选择默认模型。"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
            return@SubPageScaffold
        }

        SettingsCardGroup {
            ModelSelectionListRow(
                title = automaticLabel,
                subtitle = automaticSubtitle,
                selected = selectedOption == null,
                onClick = { onSelected("") },
            )
            options.forEach { option ->
                CardDivider()
                ModelSelectionListRow(
                    title = option.fullLabel,
                    subtitle = option.providerName,
                    selected = option.key == selectedOption?.key,
                    onClick = { onSelected(option.key) },
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AetherBackground.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AetherOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = if (selected) AetherPrimary else Color.Transparent,
            modifier = Modifier.size(22.dp),
        )
    }
}

// -----------------------------------------------------------------------------
// Provider Edit Page (Add/Edit)
// -----------------------------------------------------------------------------

@Composable
private fun ProviderEditPage(
    existingConfig: LlmProviderConfig?,
    existingProviderIds: Set<String>,
    isFetchingModels: Boolean,
    onSave: (LlmProviderConfig) -> Unit,
    onModelEnabledChange: (LlmProviderConfig) -> Unit,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val isNew = existingConfig == null
    val formState = rememberProviderFormState(existingConfig)

    SubPageScaffold(
        title = if (isNew) tr(strings, "Add Provider", "添加 Provider") else tr(strings, "Edit Provider", "编辑 Provider"),
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        trailingEnabled = formState.isValid(existingProviderIds),
        onTrailingAction = {
            onSave(formState.buildConfig())
            onBack()
        },
    ) {
        ProviderConfigurationForm(
            state = formState,
            existingProviderIds = existingProviderIds,
            isFetchingModels = isFetchingModels,
            onFetchModels = onFetchModels,
            onModelEnabledChange = onModelEnabledChange,
        )
    }
}
// -----------------------------------------------------------------------------
// Personalization sub-page
// -----------------------------------------------------------------------------

@Composable
private fun PersonalizationPage(
    title: String,
    systemPromptValue: TextFieldValue,
    onSystemPromptChanged: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        SettingsCardGroup {
            ChatGptTextField(
                label = strings.customInstructions,
                value = systemPromptValue,
                onValueChange = onSystemPromptChanged,
                minLines = 8,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = tr(
                strings,
                "Supports {{current_datetime}}, {{current_date}}, {{current_time}}, {{timezone}}, and {{unix_timestamp}}.",
                "支持 {{current_datetime}}、{{current_date}}、{{current_time}}、{{timezone}} 和 {{unix_timestamp}}。",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// -----------------------------------------------------------------------------
// Skills List Page (Refactored)
// -----------------------------------------------------------------------------

@Composable
private fun ReliabilityPage(
    title: String,
    llmInactivityReconnectTimeoutValue: TextFieldValue,
    onLlmInactivityReconnectTimeoutChanged: (TextFieldValue) -> Unit,
    keepTasksRunningInBackground: Boolean,
    onKeepTasksRunningInBackgroundChanged: (Boolean) -> Unit,
    notifyOnTaskCompletion: Boolean,
    onNotifyOnTaskCompletionChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        Text(
            text = tr(strings, "Multitasking", "后台运行"),
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsToggleRow(
                    title = tr(strings, "Keep tasks running in background", "Keep tasks running in background"),
                    subtitle = tr(strings, "Uses an Android foreground service so active chats can keep working after you leave Aether.", "Uses an Android foreground service so active chats can keep working after you leave Aether."),
                    checked = keepTasksRunningInBackground,
                    onCheckedChange = onKeepTasksRunningInBackgroundChanged,
                )
                Spacer(Modifier.height(4.dp))
                SettingsToggleRow(
                    title = tr(strings, "Notify when background tasks finish", "后台任务结束时通知"),
                    subtitle = tr(strings, "Shows a completion alert when a run ends while Aether is not on screen.", "Shows a completion alert when a run ends while Aether is not on screen."),
                    checked = notifyOnTaskCompletion,
                    onCheckedChange = onNotifyOnTaskCompletionChanged,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = tr(strings, "Reconnect", "重连"),
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            ChatGptTextField(
                label = tr(strings, "Reconnect after idle seconds", "空闲多少秒后重连"),
                value = llmInactivityReconnectTimeoutValue,
                onValueChange = {
                    val digitsOnly = it.text.filter(Char::isDigit)
                    onLlmInactivityReconnectTimeoutChanged(
                        it.copy(
                            text = digitsOnly,
                            selection = androidx.compose.ui.text.TextRange(digitsOnly.length),
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = tr(strings, "If a request produces no response activity at all for this many seconds, Aether cancels that attempt and reconnects with backoff. Range: 30-3600 seconds.", "If a request produces no response activity at all for this many seconds, Aether cancels that attempt and reconnects with backoff. Range: 30-3600 seconds."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun WebToolsPage(
    title: String,
    tavilyApiKeyValue: TextFieldValue,
    onTavilyApiKeyChanged: (TextFieldValue) -> Unit,
    tavilyBaseUrlValue: TextFieldValue,
    onTavilyBaseUrlChanged: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        SettingsCardGroup {
            ChatGptTextField(
                label = tr(strings, "Tavily API Key", "Tavily API 密钥"),
                value = tavilyApiKeyValue,
                onValueChange = onTavilyApiKeyChanged,
            )
            CardDivider()
            ChatGptTextField(
                label = tr(strings, "Tavily Base URL", "Tavily Base URL"),
                value = tavilyBaseUrlValue,
                onValueChange = onTavilyBaseUrlChanged,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = tr(strings, "fetch_web_url works without extra setup. tavily_search uses this API key and Base URL; leave the URL blank to use the official Tavily endpoint.", "fetch_web_url 无需额外配置。tavily_search 使用这里的 API Key 和 Base URL；留空则使用 Tavily 官方端点。"),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SkillsListPage(
    title: String,
    installedSkills: List<com.zhousl.aether.data.InstalledSkill>,
    onToggleSkillEnabled: (String, Boolean) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onAddNew: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = tr(strings, "Manage installed skills and keep only the bundles you want Aether to use in chat.", "Manage installed skills and keep only the bundles you want Aether to use in chat."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (installedSkills.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr(strings, "No skills installed", "No skills installed"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(strings, "Import skills from a folder, zip, or remote URL.", "Import skills from a folder, zip, or remote URL."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = tr(strings, "Add Skill", "Add Skill"),
                        onClick = onAddNew,
                    )
                }
            }
        } else {
            installedSkills.forEach { skill ->
                SkillCard(
                    skill = skill,
                    onToggleEnabled = { enabled -> onToggleSkillEnabled(skill.id, enabled) },
                    onRemove = { onRemoveSkill(skill.id) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: com.zhousl.aether.data.InstalledSkill,
    onToggleEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var expanded by rememberSaveable(skill.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                if (skill.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionPreviewPill(label = skill.quickActionLabel())
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ArrowDropDown else Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = if (expanded) tr(strings, "Collapse", "收起") else tr(strings, "Expand", "展开"),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = tr(strings, "Remove", "移除"),
                    tint = Color(0xFFD25757),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsToggleRow(
            title = "",
            subtitle = "",
            checked = skill.isEnabled,
            onCheckedChange = onToggleEnabled,
        )
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(14.dp))
            DetailLine(tr(strings, "Skill ID", "技能 ID"), skill.id)
            DetailLine(tr(strings, "Files", "Files"), "${skill.resourceEntries.size}")
            DetailLine(tr(strings, "Allowed tools", "Allowed tools"), skill.allowedTools.ifEmpty { listOf(tr(strings, "Any", "Any")) }.joinToString(", "))
            if (skill.compatibility.isNotBlank()) {
                DetailLine(tr(strings, "Compatibility", "Compatibility"), skill.compatibility)
            }
            if (skill.source.label.isNotBlank()) {
                DetailLine(tr(strings, "Source", "来源"), skill.source.label)
            }
            DetailLine(tr(strings, "Path", "路径"), skill.skillRootPath)
        }
    }
}

// -----------------------------------------------------------------------------
// Add Skill Page
// -----------------------------------------------------------------------------

@Composable
private fun AddSkillPage(
    title: String,
    onImportSkillFolder: () -> Unit,
    onImportSkillZip: ((Boolean) -> Unit) -> Unit,
    onInstallSkillUrl: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var skillUrlValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isInstalling by remember { mutableStateOf(false) }
    val strings = rememberAetherStrings()

    val tabOptions = listOf(
        tr(strings, "Folder", "Folder"),
        "Zip",
        "URL",
    )

    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = tr(strings, "Import Agent Skills from a local folder, zip file, or remote URL.", "Import Agent Skills from a local folder, zip file, or remote URL."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Segmented button row
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            tabOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabOptions.size),
                    onClick = { selectedTab = index },
                    selected = selectedTab == index,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AetherPrimary,
                        activeContentColor = Color.White,
                        inactiveContainerColor = AetherSurfaceHigh,
                        inactiveContentColor = AetherOnSurface,
                    ),
                ) {
                    Text(label)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (selectedTab) {
            0 -> {
                // Folder import
                SettingsCardGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = AetherOnSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr(strings, "Select a folder containing SKILL.md", "选择包含 SKILL.md 的文件夹"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SettingsActionButton(
                            label = tr(strings, "Choose Folder", "Choose Folder"),
                            onClick = {
                                onImportSkillFolder()
                                // Will navigate back via callback on success
                            },
                        )
                    }
                }
            }

            1 -> {
                // Zip import
                SettingsCardGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = AetherOnSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr(strings, "Select a .zip file containing SKILL.md", "选择包含 SKILL.md 的 .zip 文件"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SettingsActionButton(
                            label = tr(strings, "Choose Zip", "选择 Zip"),
                            onClick = {
                                onImportSkillZip {}
                            },
                        )
                    }
                }
            }

            2 -> {
                // URL import
                SettingsCardGroup {
                    ChatGptTextField(
                        label = tr(strings, "Remote skill URL", "远程技能 URL"),
                        value = skillUrlValue,
                        onValueChange = { skillUrlValue = it },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = tr(strings, "GitHub repo/tree URLs and direct zip links are supported.", "GitHub repo/tree URLs and direct zip links are supported."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(16.dp))

                if (isInstalling) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = AetherPrimary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(tr(strings, "Installing...", "安装中..."), color = AetherOnSurfaceVariant)
                    }
                } else {
                    SettingsActionButton(
                        label = tr(strings, "Install from URL", "从 URL 安装"),
                        onClick = {
                            if (skillUrlValue.text.isNotBlank()) {
                                isInstalling = true
                                onInstallSkillUrl(skillUrlValue.text) { success ->
                                    isInstalling = false
                                    if (success) {
                                        skillUrlValue = TextFieldValue("")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// MCP Servers List Page (Refactored)
// -----------------------------------------------------------------------------

@Composable
private fun McpServersListPage(
    title: String,
    mcpServers: List<com.zhousl.aether.data.McpServerConfig>,
    onToggleMcpServerEnabled: (String, Boolean) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onTestMcpServer: (String, McpServerTestOperation, (String) -> Unit) -> Unit,
    onEdit: (String) -> Unit,
    onAddNew: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var testResultText by rememberSaveable { mutableStateOf("") }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = tr(strings, "Manage MCP servers, inspect each transport config, and keep only the connections you want active.", "Manage MCP servers, inspect each transport config, and keep only the connections you want active."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (mcpServers.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr(strings, "No MCP servers", "No MCP servers"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(strings, "Add HTTP or stdio servers to extend capabilities.", "Add HTTP or stdio servers to extend capabilities."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = tr(strings, "Add Server", "Add Server"),
                        onClick = onAddNew,
                    )
                }
            }
        } else {
            mcpServers.forEach { server ->
                McpServerCard(
                    server = server,
                    onToggleEnabled = { enabled -> onToggleMcpServerEnabled(server.id, enabled) },
                    onEdit = { onEdit(server.id) },
                    onRemove = { onRemoveMcpServer(server.id) },
                    onTest = { operation ->
                        onTestMcpServer(server.id, operation) { result ->
                            testResultText = result
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (testResultText.isNotBlank()) {
                SettingsCardGroup {
                    Text(
                        text = testResultText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: com.zhousl.aether.data.McpServerConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTest: (McpServerTestOperation) -> Unit,
) {
    val strings = rememberAetherStrings()
    var expanded by rememberSaveable(server.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = server.transport.transportType.storageValue.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ActionPreviewPill(label = server.quickActionLabel())
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ArrowDropDown else Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = if (expanded) tr(strings, "Collapse", "收起") else tr(strings, "Expand", "展开"),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = tr(strings, "Edit", "编辑"),
                    tint = AetherOnSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = tr(strings, "Remove", "移除"),
                    tint = Color(0xFFD25757),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsToggleRow(
            title = "",
            subtitle = "",
            checked = server.isEnabled,
            onCheckedChange = onToggleEnabled,
        )
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSubtleActionButton(
                    label = tr(strings, "Tools", "工具"),
                    onClick = { onTest(McpServerTestOperation.ListTools) },
                    modifier = Modifier.weight(1f),
                )
                SettingsSubtleActionButton(
                    label = tr(strings, "Resources", "资源"),
                    onClick = { onTest(McpServerTestOperation.ListResources) },
                    modifier = Modifier.weight(1f),
                )
                SettingsSubtleActionButton(
                    label = tr(strings, "Prompts", "提示词"),
                    onClick = { onTest(McpServerTestOperation.ListPrompts) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            DetailLine(tr(strings, "Server ID", "服务器 ID"), server.id)
            DetailLine(tr(strings, "Quick action", "快捷操作"), server.quickActionLabel())
            DetailLine(tr(strings, "Transport", "传输方式"), server.transport.transportType.storageValue.uppercase())
            when (val transport = server.transport) {
                is com.zhousl.aether.data.McpTransportConfig.StreamableHttp -> {
                    DetailLine("URL", transport.url)
                    DetailLine(tr(strings, "Headers", "Headers"), transport.headers.size.toString())
                }

                is com.zhousl.aether.data.McpTransportConfig.StdIo -> {
                    DetailLine(tr(strings, "Command", "命令"), transport.command)
                    if (transport.workingDirectory.isNotBlank()) {
                        DetailLine(tr(strings, "Working dir", "工作目录"), transport.workingDirectory)
                    }
                    DetailLine(tr(strings, "Environment", "环境变量"), transport.environment.size.toString())
                }
            }
            DetailLine(tr(strings, "Connect timeout", "连接超时"), "${server.connectTimeoutMillis} ms")
            DetailLine(tr(strings, "Request timeout", "请求超时"), "${server.requestTimeoutMillis} ms")
        }
    }
}

// -----------------------------------------------------------------------------
// Scheduled Tasks
// -----------------------------------------------------------------------------

@Composable
private fun ScheduledTasksPage(
    tasks: List<ScheduledTask>,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAddNew: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(
        title = tr(strings, "Scheduled Tasks", "定时任务"),
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = tr(
                strings,
                "Aether wakes at the next matching time and runs the task prompt as an automated Agent turn.",
                "Aether 会在匹配的时间唤起，并把任务提示词作为一次自动 Agent 运行。",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tr(strings, "No scheduled tasks", "暂无定时任务"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = tr(strings, "Create one manually, or ask the Agent to create one with its scheduling tool.", "可以手动创建，也可以让 Agent 通过定时任务工具创建。"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = tr(strings, "Add Task", "新建任务"),
                        onClick = onAddNew,
                    )
                }
            }
        } else {
            tasks.sortedWith(compareBy<ScheduledTask> { it.nextRunAtMillis ?: Long.MAX_VALUE }.thenBy { it.name })
                .forEach { task ->
                    ScheduledTaskCard(
                        task = task,
                        onToggleEnabled = { enabled -> onToggleEnabled(task.id, enabled) },
                        onEdit = { onEdit(task.id) },
                        onRemove = { onRemove(task.id) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
        }
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.schedule.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = strings.editMessage,
                    tint = AetherOnSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = strings.delete,
                    tint = Color(0xFFD25757),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = task.prompt.take(160),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.nextRunAtMillis?.formatScheduledTaskTime()
                        ?.let { tr(strings, "Next run: $it", "下次运行：$it") }
                        ?: tr(strings, "No next run scheduled", "未安排下次运行"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Text(
                    text = if (task.createdBy == ScheduledTaskCreator.Agent) {
                        tr(strings, "Created by Agent", "由 Agent 创建")
                    } else {
                        tr(strings, "Created manually", "手动创建")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                )
            }
            Switch(
                checked = task.isEnabled,
                onCheckedChange = onToggleEnabled,
            )
        }
    }
}

@Composable
private fun ScheduledTaskEditPage(
    existingTask: ScheduledTask?,
    onSave: (String, String, ScheduledTaskSchedule, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val existingInterval = existingTask?.schedule as? ScheduledTaskSchedule.Interval
    val existingDaily = existingTask?.schedule as? ScheduledTaskSchedule.Daily
    val existingWeekly = existingTask?.schedule as? ScheduledTaskSchedule.Weekly
    var nameValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingTask?.name.orEmpty()))
    }
    var promptValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingTask?.prompt.orEmpty()))
    }
    var enabledValue by rememberSaveable(existingTask?.id) {
        mutableStateOf(existingTask?.isEnabled ?: true)
    }
    var scheduleMode by rememberSaveable(existingTask?.id) {
        mutableIntStateOf(
            when (existingTask?.schedule) {
                is ScheduledTaskSchedule.Daily -> 1
                is ScheduledTaskSchedule.Weekly -> 2
                else -> 0
            }
        )
    }
    var intervalMinutesValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(((existingInterval?.intervalMillis ?: 60L * 60L * 1000L) / 60_000L).toString()))
    }
    var activeStartValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingInterval?.activeStartMinuteOfDay?.let(::formatTaskMinute).orEmpty()))
    }
    var activeEndValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingInterval?.activeEndMinuteOfDay?.let(::formatTaskMinute).orEmpty()))
    }
    var dailyTimesValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingDaily?.timesMinutesOfDay?.joinToString(",") { formatTaskMinute(it) } ?: "09:00"))
    }
    var weeklyDaysValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingWeekly?.daysOfWeek?.joinToString(",") ?: "1"))
    }
    var weeklyTimeValue by rememberSaveable(existingTask?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingWeekly?.minuteOfDay?.let(::formatTaskMinute) ?: "09:00"))
    }

    fun buildSchedule(): ScheduledTaskSchedule? = when (scheduleMode) {
        0 -> ScheduledTaskSchedule.Interval(
            intervalMillis = (intervalMinutesValue.text.trim().toLongOrNull() ?: 60L).coerceAtLeast(1L) * 60_000L,
            activeStartMinuteOfDay = parseTaskMinute(activeStartValue.text),
            activeEndMinuteOfDay = parseTaskMinute(activeEndValue.text),
        )
        1 -> parseTaskTimes(dailyTimesValue.text).takeIf { it.isNotEmpty() }?.let { times ->
            ScheduledTaskSchedule.Daily(timesMinutesOfDay = times)
        }
        else -> {
            val days = parseTaskDays(weeklyDaysValue.text)
            val time = parseTaskMinute(weeklyTimeValue.text)
            if (days.isEmpty() || time == null) null else ScheduledTaskSchedule.Weekly(days, time)
        }
    }

    SubPageScaffold(
        title = if (existingTask == null) {
            tr(strings, "Add Scheduled Task", "新建定时任务")
        } else {
            tr(strings, "Edit Scheduled Task", "编辑定时任务")
        },
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        trailingEnabled = nameValue.text.isNotBlank() && promptValue.text.isNotBlank(),
        onTrailingAction = {
            buildSchedule()?.let { schedule ->
                onSave(nameValue.text, promptValue.text, schedule, enabledValue)
            }
        },
    ) {
        SettingsCardGroup {
            ChatGptTextField(
                label = tr(strings, "Name", "名称"),
                value = nameValue,
                onValueChange = { nameValue = it },
            )
            CardDivider()
            ChatGptTextField(
                label = tr(strings, "Prompt", "提示词"),
                value = promptValue,
                minLines = 4,
                onValueChange = { promptValue = it },
            )
        }
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsToggleRow(
                    title = tr(strings, "Enabled", "启用"),
                    subtitle = tr(strings, "Disabled tasks stay saved but do not wake Aether.", "关闭后任务仍会保存，但不会唤起 Aether。"),
                    checked = enabledValue,
                    onCheckedChange = { enabledValue = it },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                tr(strings, "Interval", "间隔"),
                tr(strings, "Daily", "每天"),
                tr(strings, "Weekly", "每周"),
            ).forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    onClick = { scheduleMode = index },
                    selected = scheduleMode == index,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AetherPrimary,
                        activeContentColor = Color.White,
                        inactiveContainerColor = AetherSurfaceHigh,
                        inactiveContentColor = AetherOnSurface,
                    ),
                ) {
                    Text(label)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            when (scheduleMode) {
                0 -> {
                    ChatGptTextField(
                        label = tr(strings, "Every N minutes", "每隔多少分钟"),
                        value = intervalMinutesValue,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { intervalMinutesValue = it.copy(text = it.text.filter(Char::isDigit)) },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = tr(strings, "Active start HH:mm", "开始时间 HH:mm"),
                        value = activeStartValue,
                        onValueChange = { activeStartValue = it },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = tr(strings, "Active end HH:mm", "结束时间 HH:mm"),
                        value = activeEndValue,
                        onValueChange = { activeEndValue = it },
                    )
                }
                1 -> ChatGptTextField(
                    label = tr(strings, "Times HH:mm, comma separated", "时间 HH:mm，用逗号分隔"),
                    value = dailyTimesValue,
                    onValueChange = { dailyTimesValue = it },
                )
                else -> {
                    ChatGptTextField(
                        label = tr(strings, "Days 1-7 or mon,tue", "星期 1-7 或 mon,tue"),
                        value = weeklyDaysValue,
                        onValueChange = { weeklyDaysValue = it },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = tr(strings, "Time HH:mm", "时间 HH:mm"),
                        value = weeklyTimeValue,
                        onValueChange = { weeklyTimeValue = it },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tr(
                strings,
                "Interval windows are optional. Leave start and end blank to run all day.",
                "间隔任务的时间窗口是可选的。开始和结束留空则全天运行。",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// -----------------------------------------------------------------------------
// Add MCP Server Page
// -----------------------------------------------------------------------------

@Composable
private fun AddMcpServerPage(
    title: String,
    existingServer: com.zhousl.aether.data.McpServerConfig?,
    onSaveHttpMcpServer: (String?, String, String, String) -> Unit,
    onSaveStdIoMcpServer: (String?, String, String, String, String, String, LocalRuntimeId?) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val isEditing = existingServer != null
    val existingHttpTransport = existingServer?.transport as? com.zhousl.aether.data.McpTransportConfig.StreamableHttp
    val existingStdIoTransport = existingServer?.transport as? com.zhousl.aether.data.McpTransportConfig.StdIo
    var selectedTab by rememberSaveable(existingServer?.id) {
        mutableIntStateOf(if (existingStdIoTransport != null) 1 else 0)
    }

    var httpServerNameValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingServer?.displayName.orEmpty()))
    }
    var httpServerUrlValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingHttpTransport?.url.orEmpty()))
    }
    var httpHeadersValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                existingHttpTransport?.headers
                    ?.joinToString("\n") { header -> "${header.key}=${header.value}" }
                    .orEmpty()
            )
        )
    }

    var stdioServerNameValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingServer?.displayName.orEmpty()))
    }
    var stdioCommandValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingStdIoTransport?.command.orEmpty()))
    }
    var stdioArgumentsValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingStdIoTransport?.arguments?.joinToString(" ").orEmpty()))
    }
    var stdioWorkingDirectoryValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingStdIoTransport?.workingDirectory.orEmpty()))
    }
    var stdioEnvValue by rememberSaveable(existingServer?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                existingStdIoTransport?.environment
                    ?.joinToString("\n") { env -> "${env.key}=${env.value}" }
                    .orEmpty()
            )
        )
    }
    var stdioRuntimeEnvironment by rememberSaveable(existingServer?.id) {
        mutableStateOf(existingStdIoTransport?.runtimeEnvironment)
    }

    val tabOptions = listOf("HTTP", tr(strings, "Stdio", "标准输入输出"))

    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = if (isEditing) {
                tr(strings, "Update the transport config and quick action source for this MCP server.", "Update the transport config and quick action source for this MCP server.")
            } else {
                tr(strings, "Add an HTTP server for remote APIs or a stdio server for local Termux processes.", "Add an HTTP server for remote APIs or a stdio server for local Termux processes.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Segmented button row
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            tabOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabOptions.size),
                    onClick = { selectedTab = index },
                    selected = selectedTab == index,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AetherPrimary,
                        activeContentColor = Color.White,
                        inactiveContainerColor = AetherSurfaceHigh,
                        inactiveContentColor = AetherOnSurface,
                    ),
                ) {
                    Text(label)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (selectedTab) {
            0 -> {
                // HTTP server
                SettingsCardGroup {
                    ChatGptTextField(tr(strings, "Server name", "Server name"), httpServerNameValue) { httpServerNameValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Server URL", "服务器 URL"), httpServerUrlValue) { httpServerUrlValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Headers", "Headers"), httpHeadersValue, minLines = 2) { httpHeadersValue = it }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(strings, "Optional headers, one KEY=VALUE per line.", "Optional headers, one KEY=VALUE per line."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SettingsActionButton(
                    label = if (isEditing) tr(strings, "Save HTTP Server", "Save HTTP Server") else tr(strings, "Add HTTP Server", "Add HTTP Server"),
                    onClick = {
                        if (httpServerNameValue.text.isNotBlank() && httpServerUrlValue.text.isNotBlank()) {
                            onSaveHttpMcpServer(
                                existingServer?.id,
                                httpServerNameValue.text,
                                httpServerUrlValue.text,
                                httpHeadersValue.text,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            1 -> {
                // Stdio server
                SettingsCardGroup {
                    ChatGptTextField(tr(strings, "Server name", "Server name"), stdioServerNameValue) { stdioServerNameValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Command", "命令"), stdioCommandValue, minLines = 2) { stdioCommandValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Arguments", "参数"), stdioArgumentsValue, minLines = 2) { stdioArgumentsValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Working directory", "工作目录"), stdioWorkingDirectoryValue) { stdioWorkingDirectoryValue = it }
                    CardDivider()
                    ChatGptTextField(tr(strings, "Environment", "环境变量"), stdioEnvValue, minLines = 2) { stdioEnvValue = it }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(strings, "Optional environment variables, one KEY=VALUE per line.", "Optional environment variables, one KEY=VALUE per line."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SettingsCardGroup {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr(strings, "Runtime environment", "运行环境"),
                            style = MaterialTheme.typography.labelLarge,
                            color = AetherOnSurface,
                        )
                        Text(
                            text = tr(
                                strings,
                                "Leave this as default to follow the user's runtime default.",
                                "保持默认时，会跟随用户的默认运行环境。",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                        SettingsChoiceRow(
                            title = tr(strings, "Default runtime", "默认运行环境"),
                            subtitle = tr(strings, "Use Settings > Runtime Defaults.", "使用 设置 > 运行环境默认值。"),
                            selected = stdioRuntimeEnvironment == null,
                            onClick = { stdioRuntimeEnvironment = null },
                        )
                        SettingsChoiceRow(
                            title = "Termux",
                            subtitle = tr(strings, "Run this stdio server through Termux.", "通过 Termux 运行这个 stdio server。"),
                            selected = stdioRuntimeEnvironment == LocalRuntimeId.Termux,
                            onClick = { stdioRuntimeEnvironment = LocalRuntimeId.Termux },
                        )
                        SettingsChoiceRow(
                            title = "Alpine",
                            subtitle = tr(strings, "Run this stdio server inside Alpine.", "在 Alpine 中运行这个 stdio server。"),
                            selected = stdioRuntimeEnvironment == LocalRuntimeId.Alpine,
                            onClick = { stdioRuntimeEnvironment = LocalRuntimeId.Alpine },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SettingsActionButton(
                    label = if (isEditing) tr(strings, "Save Stdio Server", "Save Stdio Server") else tr(strings, "Add Stdio Server", "Add Stdio Server"),
                    onClick = {
                        if (stdioServerNameValue.text.isNotBlank() && stdioCommandValue.text.isNotBlank()) {
                            onSaveStdIoMcpServer(
                                existingServer?.id,
                                stdioServerNameValue.text,
                                stdioCommandValue.text,
                                stdioArgumentsValue.text,
                                stdioWorkingDirectoryValue.text,
                                stdioEnvValue.text,
                                stdioRuntimeEnvironment,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Termux sub-page
// -----------------------------------------------------------------------------

@Composable
private fun TermuxSettingsPage(
    title: String,
    termuxSetupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    selectedWorkspaceMode: AgentWorkspaceMode,
    liveOutputEnabled: Boolean,
    environmentVariables: List<TermuxEnvironmentVariable>,
    onWorkspaceModeSelected: (AgentWorkspaceMode) -> Unit,
    onLiveOutputEnabledChanged: (Boolean) -> Unit,
    onEnvironmentVariablesChanged: (List<TermuxEnvironmentVariable>) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var showAlreadyConfiguredDialog by rememberSaveable { mutableStateOf(false) }

    fun requestRootSetup() {
        if (termuxSetupState.isReady && !rootSetupState.isRunning) {
            showAlreadyConfiguredDialog = true
        } else {
            onConfigureWithRoot()
        }
    }

    if (showAlreadyConfiguredDialog) {
        RootSetupAlreadyConfiguredDialog(
            title = tr(strings, "Termux is already configured", "Termux 已配置完成"),
            body = tr(
                strings,
                "Termux command access is already working. You do not need to run Root automatic setup again.",
                "Termux 命令访问已经正常，不需要再次执行 Root 自动配置。",
            ),
            onDismiss = { showAlreadyConfiguredDialog = false },
            onContinue = {
                showAlreadyConfiguredDialog = false
                onConfigureWithRoot()
            },
        )
    }

    LaunchedEffect(Unit) {
        onRefreshRootSetup()
        onRefreshTermuxSetup()
        while (true) {
            delay(SettingsAutoRefreshIntervalMillis)
            onRefreshTermuxSetup()
        }
    }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Refresh,
        onTrailingAction = onRefreshTermuxSetup,
    ) {
        Text(
            text = tr(strings, "Aether runs bash through Termux. Finish setup here so tool calls work for every user without manual adb steps.", "Aether runs bash through Termux. Finish setup here so tool calls work for every user without manual adb steps."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        WorkspaceModeSettingsSection(
            strings = strings,
            selectedWorkspaceMode = selectedWorkspaceMode,
            onWorkspaceModeSelected = onWorkspaceModeSelected,
        )

        Spacer(Modifier.height(16.dp))

        TermuxLiveOutputSettingsSection(
            strings = strings,
            liveOutputEnabled = liveOutputEnabled,
            onLiveOutputEnabledChanged = onLiveOutputEnabledChanged,
        )

        Spacer(Modifier.height(16.dp))

        TermuxEnvironmentVariablesSection(
            variables = environmentVariables,
            onVariablesChanged = onEnvironmentVariablesChanged,
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            RootSetupSettingsSection(
                title = tr(strings, "Root automatic setup", "Root 自动配置"),
                rootSetupState = rootSetupState,
                body = rootSetupSettingsBody(rootSetupState, strings),
                onConfigureWithRoot = ::requestRootSetup,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (termuxSetupState.isReady) {
            SettingsCardGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(tr(strings, "Termux is connected", "Termux is connected"), style = MaterialTheme.typography.labelLarge, color = AetherOnSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(strings, "Permission is granted and the setup probe succeeded.", "Permission is granted and the setup probe succeeded."),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
        } else {
            TermuxSetupNotice(
                setupState = termuxSetupState,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
                showRefreshAction = false,
            )
        }
    }
}

@Composable
private fun TermuxLiveOutputSettingsSection(
    strings: AetherStrings,
    liveOutputEnabled: Boolean,
    onLiveOutputEnabledChanged: (Boolean) -> Unit,
) {
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsToggleRow(
                title = tr(strings, "Live command output", "实时命令输出"),
                subtitle = tr(
                    strings,
                    "Show running bash stdout and stderr snapshots in the tool card while Termux commands are still active.",
                    "在 Termux 命令仍在运行时，在工具卡片中显示 bash stdout 和 stderr 快照。",
                ),
                checked = liveOutputEnabled,
                onCheckedChange = onLiveOutputEnabledChanged,
            )
        }
    }
}

@Composable
private fun AlpineSettingsPage(
    title: String,
    setupState: LocalRuntimeSetupState,
    packageProfiles: Map<String, PackageProfileState>,
    isDefaultRuntime: Boolean,
    onInitialize: () -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
    onInstallPackageProfile: (String) -> Unit,
    onOpenTerminal: () -> Unit,
    onSetDefault: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    LaunchedEffect(Unit) {
        onRefresh()
    }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Terminal,
        trailingEnabled = setupState.isReady,
        trailingContentDescription = tr(strings, "Open Alpine terminal", "打开 Alpine 终端"),
        onTrailingAction = onOpenTerminal,
    ) {
        Text(
            text = tr(
                strings,
                "Aether's built-in Linux environment is app-private and independent from Termux. Use it for shell scripts, development tools, and stdio MCP servers that do not need Android-side control.",
                "Aether 的内置 Linux 环境位于应用私有目录，和 Termux 相互独立。它适合 shell 脚本、开发工具，以及不需要操作手机侧能力的 stdio MCP。",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Runtime status", "运行环境状态"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = alpineSetupStatusText(setupState, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                if (setupState.detail.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = setupState.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsActionButton(
                        label = when (setupState.issue) {
                            LocalRuntimeIssue.Ready -> tr(strings, "Ready", "已就绪")
                            else -> tr(strings, "Initialize", "初始化")
                        },
                        onClick = onInitialize,
                        modifier = Modifier.weight(1f),
                        enabled = !setupState.isReady,
                    )
                    SettingsSubtleActionButton(
                        label = strings.refresh,
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Reset Alpine data", "重置 Alpine 数据"),
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (setupState.isReady && !isDefaultRuntime) {
                    Spacer(Modifier.height(10.dp))
                    SettingsActionButton(
                        label = tr(strings, "Use as default runtime", "设为默认运行环境"),
                        onClick = onSetDefault,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Environment presets", "快捷环境配置"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(
                        strings,
                        "Install only the toolchains you need. Each preset can be installed and tracked independently.",
                        "按需安装工具链。每个预设都可以独立安装和跟踪状态。",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AlpineProfileRow(
                    title = tr(strings, "Python environment", "Python 环境"),
                    subtitle = "python3, pip, virtualenv",
                    profileState = packageProfiles["python"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("python") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = tr(strings, "Node.js/npm environment", "Node.js/npm 环境"),
                    subtitle = "nodejs, npm",
                    profileState = packageProfiles["node"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("node") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = tr(strings, "Git and ripgrep tools", "Git 与 ripgrep 工具"),
                    subtitle = "git, ripgrep",
                    profileState = packageProfiles["git_search"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("git_search") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = tr(strings, "SSH tools", "SSH 工具"),
                    subtitle = "openssh-client",
                    profileState = packageProfiles["ssh"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("ssh") },
                )
            }
        }
    }
}

@Composable
private fun AlpineProfileRow(
    title: String,
    subtitle: String,
    profileState: PackageProfileState?,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val isInstalling = profileState?.lastError == "Installing..."
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            if (!profileState?.lastError.isNullOrBlank() && !isInstalling) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profileState.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SettingsSubtleActionButton(
            label = when {
                isInstalling -> tr(strings, "Installing", "安装中")
                profileState?.installed == true -> tr(strings, "Installed", "已安装")
                else -> strings.install
            },
            onClick = onInstall,
            enabled = enabled && profileState?.installed != true && !isInstalling,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlpineTerminalPage(
    terminal: AlpineTerminalUiState,
    onSendControl: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var terminalFontSizeSp by rememberSaveable { mutableIntStateOf(DefaultTerminalFontSizeSp) }
    val fontSize = terminalFontSizeSp.sp
    val lineHeight = (terminalFontSizeSp + 5).sp
    val terminalStyle = androidx.compose.ui.text.TextStyle(
        color = Color(0xFFD7FFD9),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    )
    val terminalContentPaddingPx = with(density) { 12.dp.toPx() }
    val approximateCharacterWidthPx = with(density) { (fontSize.value * 0.62f).sp.toPx().coerceAtLeast(1f) }
    val approximateLineHeightPx = with(density) { lineHeight.toPx().coerceAtLeast(1f) }
    var measuredCharacterWidthPx by remember { mutableStateOf(0f) }
    var measuredLineHeightPx by remember { mutableStateOf(0f) }
    val characterWidthPx = measuredCharacterWidthPx.takeIf { it > 0f } ?: approximateCharacterWidthPx
    val lineHeightPx = measuredLineHeightPx.takeIf { it > 0f } ?: approximateLineHeightPx
    var terminalTextLayoutResult by remember {
        mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
    }
    var terminalViewportSize by remember { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }
    var imeCaptureValue by remember {
        mutableStateOf(terminalImeFieldValue())
    }
    var ctrlDown by rememberSaveable { mutableStateOf(false) }
    var altDown by rememberSaveable { mutableStateOf(false) }
    var fnDown by rememberSaveable { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var urlSelectionExpanded by remember { mutableStateOf(false) }
    var textSelectionExpanded by remember { mutableStateOf(false) }
    var inlineSelectionMode by rememberSaveable { mutableStateOf(false) }
    var mouseDownCell by remember { mutableStateOf<TerminalMouseCell?>(null) }
    var transcriptTapCell by remember { mutableStateOf<TerminalTextCell?>(null) }
    var followTerminalOutput by rememberSaveable { mutableStateOf(true) }
    val terminalPrefixText = buildString {
        if (terminal.isStarting) append("[Aether] Starting Alpine terminal...\n")
        if (terminal.error.isNotBlank()) append("[Aether] ${terminal.error}\n")
    }
    val terminalOutputDisplayText = terminal.styledOutput.text.ifEmpty { "aether-alpine:/root# " }
    val terminalDisplayText = buildString {
        append(terminalPrefixText)
        append(terminalOutputDisplayText)
    }
    val terminalDisplayStyledText = remember(
        terminalPrefixText,
        terminalOutputDisplayText,
        terminal.styledOutput,
    ) {
        buildTerminalAnnotatedOutput(
            prefix = terminalPrefixText,
            styledOutput = terminal.styledOutput,
            fallbackOutput = terminalOutputDisplayText,
        )
    }
    val terminalPrefixLineCount = terminalPrefixText.count { it == '\n' }
    val terminalCursorVisualPosition = terminalTextLayoutResult?.let { layoutResult ->
        terminalCursorVisualPosition(
            layoutResult = layoutResult,
            text = terminalDisplayStyledText.text,
            row = terminalPrefixLineCount + terminal.cursorRow,
            column = terminal.cursorColumn,
            characterWidthPx = characterWidthPx,
        )
    }
    val terminalUrls = remember(terminal.output) {
        extractTerminalUrls(terminal.output).asReversed()
    }
    LaunchedEffect(terminal.output, followTerminalOutput) {
        if (followTerminalOutput) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        followTerminalOutput = shouldFollowTerminalOutput(scrollState.value, scrollState.maxValue)
    }
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }
    LaunchedEffect(terminalViewportSize, characterWidthPx, lineHeightPx) {
        terminalViewportSize?.let { size ->
            val columns = ((size.width - terminalContentPaddingPx * 2) / characterWidthPx)
                .toInt()
                .coerceAtLeast(20)
            val rows = ((size.height - terminalContentPaddingPx * 2) / lineHeightPx)
                .toInt()
                .coerceAtLeast(6)
            onResize(rows, columns)
        }
    }
    BackHandler {
        if (inlineSelectionMode) {
            inlineSelectionMode = false
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            onBack()
        }
    }
    fun focusTerminal() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    fun sendText(text: String) {
        if (text.isEmpty()) return
        onSendControl(
            buildTerminalInputSequence(
                text = text.replace("\n", "\r"),
                ctrlDown = ctrlDown,
                altDown = altDown,
                fnDown = fnDown,
            )
        )
        ctrlDown = false
        altDown = false
        fnDown = false
    }
    fun sendImeInput(sequence: String) {
        if (sequence.isEmpty()) return
        if (sequence == TerminalBackspaceSequence) {
            onSendControl(sequence)
        } else {
            sendText(sequence)
        }
    }
    fun pasteClipboard() {
        clipboardManager.getText()?.text?.takeIf(String::isNotEmpty)?.let { text ->
            if (terminal.bracketedPasteEnabled) {
                onSendControl(buildTerminalPasteSequence(text, bracketedPasteEnabled = true))
            } else {
                sendText(text)
            }
        }
        focusTerminal()
    }
    fun copyOutput() {
        terminal.output.takeIf(String::isNotBlank)?.let { output ->
            clipboardManager.setText(AnnotatedString(output))
        }
        focusTerminal()
    }
    fun copyTerminalUrl(url: String) {
        clipboardManager.setText(AnnotatedString(url))
        Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
        focusTerminal()
    }
    fun openTerminalUrl(url: String) {
        openExternalTerminalUrl(context, url)
        focusTerminal()
    }
    fun showUrlSelection() {
        if (terminalUrls.isNotEmpty()) {
            urlSelectionExpanded = true
        } else {
            Toast.makeText(context, "No URLs found", Toast.LENGTH_SHORT).show()
            focusTerminal()
        }
    }
    fun showTextSelection() {
        if (terminal.output.isNotBlank()) {
            textSelectionExpanded = true
        } else {
            Toast.makeText(context, "No output to select", Toast.LENGTH_SHORT).show()
            focusTerminal()
        }
    }
    fun enterInlineSelectionMode() {
        if (terminalDisplayText.isNotBlank()) {
            inlineSelectionMode = true
            keyboardController?.hide()
        }
    }
    fun handleTerminalLongClick() {
        val cell = transcriptTapCell
        if (cell != null &&
            isTerminalTextCellOccupied(
                text = terminalDisplayText,
                column = cell.column,
                row = cell.row,
            )
        ) {
            enterInlineSelectionMode()
        } else {
            contextMenuExpanded = true
            keyboardController?.hide()
        }
    }
    fun updateTerminalTextMetrics(layoutResult: androidx.compose.ui.text.TextLayoutResult) {
        terminalTextLayoutResult = layoutResult
        val sampleOffset = terminalDisplayStyledText.text.indexOfFirst { char ->
            char != '\n' && char.code in 33..126
        }.takeIf { it >= 0 } ?: terminalDisplayStyledText.text.indexOfFirst { it != '\n' }
        if (sampleOffset >= 0 && sampleOffset < terminalDisplayStyledText.text.length) {
            val nextOffset = (sampleOffset + 1).coerceAtMost(terminalDisplayStyledText.text.length)
            val width = if (nextOffset > sampleOffset) {
                layoutResult.getCursorRect(nextOffset).left - layoutResult.getCursorRect(sampleOffset).left
            } else {
                0f
            }.takeIf { it > 0f } ?: layoutResult.getBoundingBox(sampleOffset).width
            if (width > 0f) measuredCharacterWidthPx = width
        }
        if (layoutResult.lineCount > 0) {
            val height = layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)
            if (height > 0f) measuredLineHeightPx = height
        }
    }
    fun changeTerminalFontSize(increase: Boolean) {
        terminalFontSizeSp = nextTerminalFontSize(
            current = terminalFontSizeSp,
            increase = increase,
        )
        focusTerminal()
    }
    fun sendShortcut(shortcut: TerminalShortcut) {
        when (shortcut.toggle) {
            TerminalModifier.Ctrl -> ctrlDown = !ctrlDown
            TerminalModifier.Alt -> altDown = !altDown
            TerminalModifier.Fn -> fnDown = !fnDown
            null -> Unit
        }
        shortcut.resolveSequence(terminal.applicationCursorKeysEnabled)?.let { sequence ->
            onSendControl(sequence)
            ctrlDown = false
            altDown = false
            fnDown = false
        }
        focusTerminal()
    }
    fun terminalMouseCell(
        x: Float,
        y: Float,
    ): TerminalMouseCell {
        val column = ((x - terminalContentPaddingPx) / characterWidthPx).toInt().coerceIn(0, terminal.columns - 1)
        val row = ((y - terminalContentPaddingPx) / lineHeightPx).toInt().coerceIn(0, terminal.rows - 1)
        return TerminalMouseCell(column = column, row = row)
    }
    fun terminalTextCell(
        x: Float,
        y: Float,
    ): TerminalTextCell {
        val column = ((x - terminalContentPaddingPx) / characterWidthPx)
            .toInt()
            .coerceAtLeast(0)
        val row = ((y - terminalContentPaddingPx + scrollState.value) / lineHeightPx)
            .toInt()
            .coerceAtLeast(0)
        return TerminalTextCell(column = column, row = row)
    }
    fun sendMouseEvent(
        cell: TerminalMouseCell,
        event: TerminalMouseEvent,
    ) {
        if (terminal.mouseTrackingMode == TerminalMouseTrackingMode.None) return
        buildTerminalMouseSequence(
            event = event,
            column = cell.column,
            row = cell.row,
            protocol = terminal.mouseProtocol,
        )?.let(onSendControl)
    }
    fun sendScrollRows(
        rowsDown: Int,
        cell: TerminalMouseCell,
    ) {
        if (rowsDown == 0) return
        val up = rowsDown < 0
        repeat(kotlin.math.abs(rowsDown).coerceAtMost(24)) {
            if (terminal.mouseTrackingMode != TerminalMouseTrackingMode.None) {
                sendMouseEvent(
                    cell = cell,
                    event = if (up) TerminalMouseEvent.WheelUp else TerminalMouseEvent.WheelDown,
                )
            } else if (terminal.alternateScreenEnabled) {
                onSendControl(if (up) "\u001B[A" else "\u001B[B")
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TerminalIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Text(
                    text = terminal.title.ifBlank { "Alpine" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TerminalTextButton(label = "Clear", onClick = onClear)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF050505))
                    .then(
                        if (inlineSelectionMode) {
                            Modifier
                        } else {
                            Modifier.combinedClickable(
                                onClick = ::focusTerminal,
                                onLongClick = ::handleTerminalLongClick,
                            )
                        }
                    )
                    .pointerInput(
                        terminal.mouseTrackingMode,
                        terminal.mouseProtocol,
                        terminal.alternateScreenEnabled,
                        terminal.rows,
                        terminal.columns,
                        terminalDisplayText,
                        scrollState.value,
                        inlineSelectionMode,
                    ) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (inlineSelectionMode) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val cell = terminalMouseCell(change.position.x, change.position.y)
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        focusTerminal()
                                        if (terminal.mouseTrackingMode != TerminalMouseTrackingMode.None) {
                                            mouseDownCell = cell
                                            sendMouseEvent(cell, TerminalMouseEvent.PrimaryDown)
                                            change.consume()
                                        } else {
                                            transcriptTapCell = terminalTextCell(change.position.x, change.position.y)
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        if (terminal.mouseTrackingMode != TerminalMouseTrackingMode.None &&
                                            terminal.mouseTrackingMode != TerminalMouseTrackingMode.Click &&
                                            change.pressed
                                        ) {
                                            if (cell != mouseDownCell ||
                                                terminal.mouseTrackingMode == TerminalMouseTrackingMode.Any
                                            ) {
                                                mouseDownCell = cell
                                                sendMouseEvent(cell, TerminalMouseEvent.PrimaryDrag)
                                                change.consume()
                                            }
                                        } else if (change.pressed &&
                                            terminalTextCell(change.position.x, change.position.y) != transcriptTapCell
                                        ) {
                                            transcriptTapCell = null
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        if (terminal.mouseTrackingMode != TerminalMouseTrackingMode.None) {
                                            sendMouseEvent(cell, TerminalMouseEvent.PrimaryUp)
                                            change.consume()
                                        } else {
                                            val tapCell = transcriptTapCell
                                            val releaseCell = terminalTextCell(change.position.x, change.position.y)
                                            if (tapCell != null && tapCell == releaseCell) {
                                                findTerminalUrlAtCell(
                                                    text = terminalDisplayText,
                                                    column = tapCell.column,
                                                    row = tapCell.row,
                                                )?.let { url ->
                                                    openTerminalUrl(url)
                                                    change.consume()
                                                }
                                            }
                                        }
                                        mouseDownCell = null
                                        transcriptTapCell = null
                                    }
                                    PointerEventType.Scroll -> {
                                        transcriptTapCell = null
                                        val rowsDown = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                                            .toInt()
                                            .coerceIn(-6, 6)
                                        if (terminal.mouseTrackingMode != TerminalMouseTrackingMode.None ||
                                            terminal.alternateScreenEnabled
                                        ) {
                                            sendScrollRows(rowsDown, cell)
                                            change.consume()
                                        } else {
                                            focusRequester.requestFocus()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        handleTerminalKeyEvent(
                            event = event,
                            ctrlDown = ctrlDown,
                            altDown = altDown,
                            fnDown = fnDown,
                            applicationCursorKeysEnabled = terminal.applicationCursorKeysEnabled,
                            applicationKeypadEnabled = terminal.applicationKeypadEnabled,
                            onSendControl = onSendControl,
                            onPaste = ::pasteClipboard,
                            onShowUrls = ::showUrlSelection,
                            onChangeFontSize = ::changeTerminalFontSize,
                            onToggleKeyboard = ::focusTerminal,
                            onToggleCtrl = { ctrlDown = !ctrlDown },
                            onToggleAlt = { altDown = !altDown },
                            onToggleFn = { fnDown = !fnDown },
                        )
                    }
                    .onFocusChanged { focusState ->
                        if (terminal.focusEventsEnabled) {
                            onSendControl(if (focusState.hasFocus) "\u001B[I" else "\u001B[O")
                        }
                    }
                    .onSizeChanged { size -> terminalViewportSize = size },
            ) {
                if (inlineSelectionMode) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = terminalDisplayStyledText,
                            style = terminalStyle,
                            onTextLayout = ::updateTerminalTextMetrics,
                        )
                    }
                } else {
                    Text(
                        text = terminalDisplayStyledText,
                        style = terminalStyle,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        onTextLayout = ::updateTerminalTextMetrics,
                    )
                }
                if (terminal.cursorVisible && !inlineSelectionMode) {
                    val cursorLeftPx = terminalCursorVisualPosition?.x
                        ?.let { terminalContentPaddingPx + it }
                        ?: (terminalContentPaddingPx + terminal.cursorColumn * characterWidthPx)
                    val cursorTopPx = terminalCursorVisualPosition?.y
                        ?.let { terminalContentPaddingPx + it }
                        ?: (terminalContentPaddingPx + terminal.cursorRow * lineHeightPx)
                    val cursorWidth = when (terminal.cursorStyle) {
                        TerminalCursorStyle.Block -> with(density) { characterWidthPx.toDp() }
                        TerminalCursorStyle.Underline -> with(density) { characterWidthPx.toDp() }
                        TerminalCursorStyle.Bar -> 2.dp
                    }
                    val cursorHeight = when (terminal.cursorStyle) {
                        TerminalCursorStyle.Block -> with(density) { lineHeightPx.toDp() }
                        TerminalCursorStyle.Underline -> 2.dp
                        TerminalCursorStyle.Bar -> with(density) { lineHeightPx.toDp() }
                    }
                    val cursorYOffset = when (terminal.cursorStyle) {
                        TerminalCursorStyle.Underline -> lineHeightPx - with(density) { cursorHeight.toPx() }
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    x = cursorLeftPx.toInt(),
                                    y = (cursorTopPx - scrollState.value + cursorYOffset).toInt(),
                                )
                            }
                            .width(cursorWidth)
                            .height(cursorHeight)
                            .background(Color(0x55D7FFD9)),
                    )
                }
                if (inlineSelectionMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xDD171717))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TerminalTextButton(
                            label = "Copy all",
                            onClick = {
                                copyOutput()
                                inlineSelectionMode = false
                            },
                        )
                        TerminalTextButton(
                            label = "Done",
                            onClick = {
                                inlineSelectionMode = false
                                focusTerminal()
                            },
                        )
                    }
                }
                DropdownMenu(
                    expanded = contextMenuExpanded,
                    onDismissRequest = { contextMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFF171717)),
                ) {
                    DropdownMenuItem(
                        text = { Text("Paste", color = Color(0xFFE8F5E9)) },
                        onClick = {
                            contextMenuExpanded = false
                            pasteClipboard()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy output", color = Color(0xFFE8F5E9)) },
                        onClick = {
                            contextMenuExpanded = false
                            copyOutput()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Select output", color = Color(0xFFE8F5E9)) },
                        onClick = {
                            contextMenuExpanded = false
                            showTextSelection()
                        },
                    )
                    if (terminalUrls.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Open latest URL", color = Color(0xFFE8F5E9)) },
                            onClick = {
                                contextMenuExpanded = false
                                openTerminalUrl(terminalUrls.first())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy latest URL", color = Color(0xFFE8F5E9)) },
                            onClick = {
                                contextMenuExpanded = false
                                copyTerminalUrl(terminalUrls.first())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Select URL", color = Color(0xFFE8F5E9)) },
                            onClick = {
                                contextMenuExpanded = false
                                showUrlSelection()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Clear", color = Color(0xFFE8F5E9)) },
                        onClick = {
                            contextMenuExpanded = false
                            onClear()
                            focusTerminal()
                        },
                    )
                }
                BasicTextField(
                    value = imeCaptureValue,
                    onValueChange = { value ->
                        val sequence = terminalImeInputSequence(value.text)
                        if (sequence.isNotEmpty()) {
                            sendImeInput(sequence)
                        }
                        imeCaptureValue = terminalImeFieldValue()
                    },
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            handleTerminalKeyEvent(
                                event = event,
                                ctrlDown = ctrlDown,
                                altDown = altDown,
                                fnDown = fnDown,
                                applicationCursorKeysEnabled = terminal.applicationCursorKeysEnabled,
                                applicationKeypadEnabled = terminal.applicationKeypadEnabled,
                                onSendControl = onSendControl,
                                onPaste = ::pasteClipboard,
                                onShowUrls = ::showUrlSelection,
                                onChangeFontSize = ::changeTerminalFontSize,
                                onToggleKeyboard = ::focusTerminal,
                                onToggleCtrl = { ctrlDown = !ctrlDown },
                                onToggleAlt = { altDown = !altDown },
                                onToggleFn = { fnDown = !fnDown },
                            )
                        }
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester)
                        .align(Alignment.BottomStart),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                    keyboardActions = KeyboardActions(),
                    singleLine = false,
                )
            }
            if (urlSelectionExpanded) {
                TerminalUrlSelectionDialog(
                    urls = terminalUrls,
                    onDismiss = {
                        urlSelectionExpanded = false
                        focusTerminal()
                    },
                    onCopyUrl = { url ->
                        urlSelectionExpanded = false
                        copyTerminalUrl(url)
                    },
                    onOpenUrl = { url ->
                        urlSelectionExpanded = false
                        openTerminalUrl(url)
                    },
                )
            }
            if (textSelectionExpanded) {
                TerminalTextSelectionDialog(
                    text = terminal.output,
                    onDismiss = {
                        textSelectionExpanded = false
                        focusTerminal()
                    },
                    onCopyAll = {
                        textSelectionExpanded = false
                        copyOutput()
                    },
                )
            }

            AlpineTerminalShortcutBar(
                ctrlDown = ctrlDown,
                altDown = altDown,
                fnDown = fnDown,
                onShortcut = ::sendShortcut,
                onPaste = ::pasteClipboard,
                onFocusTerminal = ::focusTerminal,
                onChangeFontSize = ::changeTerminalFontSize,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalUrlSelectionDialog(
    urls: List<String>,
    onDismiss: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .shadow(22.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171717))
                .padding(18.dp),
        ) {
            Text(
                text = "Terminal URLs",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE8F5E9),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                urls.forEach { url ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0B0B0B))
                            .combinedClickable(
                                onClick = { onCopyUrl(url) },
                                onLongClick = { onOpenUrl(url) },
                            )
                            .padding(12.dp),
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE8F5E9),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TerminalTextButton(label = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TerminalTextSelectionDialog(
    text: String,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .shadow(22.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171717))
                .padding(18.dp),
        ) {
            Text(
                text = "Select output",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE8F5E9),
            )
            Spacer(Modifier.height(10.dp))
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF050505))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = text,
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFD7FFD9),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TerminalTextButton(label = "Copy all", onClick = onCopyAll)
                TerminalTextButton(label = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AlpineTerminalShortcutBar(
    ctrlDown: Boolean,
    altDown: Boolean,
    fnDown: Boolean,
    onShortcut: (TerminalShortcut) -> Unit,
    onPaste: () -> Unit,
    onFocusTerminal: () -> Unit,
    onChangeFontSize: (Boolean) -> Unit,
) {
    val shortcuts = remember {
        listOf(
            TerminalShortcut("Keyboard"),
            TerminalShortcut("Paste"),
            TerminalShortcut("Ctrl", toggle = TerminalModifier.Ctrl),
            TerminalShortcut("Alt", toggle = TerminalModifier.Alt),
            TerminalShortcut("Fn", toggle = TerminalModifier.Fn),
            TerminalShortcut("Esc", "\u001B"),
            TerminalShortcut("Tab", "\t"),
            TerminalShortcut("Enter", "\r"),
            TerminalShortcut("↑", key = TerminalKey.ArrowUp),
            TerminalShortcut("↓", key = TerminalKey.ArrowDown),
            TerminalShortcut("←", key = TerminalKey.ArrowLeft),
            TerminalShortcut("→", key = TerminalKey.ArrowRight),
            TerminalShortcut("PgUp", "\u001B[5~"),
            TerminalShortcut("PgDn", "\u001B[6~"),
            TerminalShortcut("Home", "\u001B[H"),
            TerminalShortcut("End", "\u001B[F"),
            TerminalShortcut("Del", "\u001B[3~"),
            TerminalShortcut("Ins", "\u001B[2~"),
            TerminalShortcut("Ctrl+C", "\u0003"),
            TerminalShortcut("Ctrl+D", "\u0004"),
            TerminalShortcut("Ctrl+Z", "\u001A"),
            TerminalShortcut("A-", role = TerminalShortcutRole.FontSmaller),
            TerminalShortcut("A+", role = TerminalShortcutRole.FontLarger),
            TerminalShortcut("F1", "\u001BOP"),
            TerminalShortcut("F2", "\u001BOQ"),
            TerminalShortcut("F3", "\u001BOR"),
            TerminalShortcut("F4", "\u001BOS"),
            TerminalShortcut("/", "/"),
            TerminalShortcut("-", "-"),
            TerminalShortcut("|", "|"),
            TerminalShortcut("~", "~"),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shortcuts.forEach { shortcut ->
            val active = when (shortcut.toggle) {
                TerminalModifier.Ctrl -> ctrlDown
                TerminalModifier.Alt -> altDown
                TerminalModifier.Fn -> fnDown
                null -> false
            }
            TerminalTextButton(
                label = shortcut.label,
                active = active,
                onClick = {
                    when (shortcut.role) {
                        TerminalShortcutRole.Keyboard -> onFocusTerminal()
                        TerminalShortcutRole.Paste -> onPaste()
                        TerminalShortcutRole.FontSmaller -> onChangeFontSize(false)
                        TerminalShortcutRole.FontLarger -> onChangeFontSize(true)
                        TerminalShortcutRole.Sequence -> onShortcut(shortcut)
                    }
                },
            )
        }
    }
}

private fun terminalImeFieldValue(): TextFieldValue =
    TextFieldValue(
        text = TerminalImeSentinel,
        selection = TextRange(TerminalImeSentinel.length),
    )

internal fun terminalImeInputSequence(text: String): String = when {
    text.isEmpty() -> TerminalBackspaceSequence
    text == TerminalImeSentinel -> ""
    text.startsWith(TerminalImeSentinel) -> text.removePrefix(TerminalImeSentinel)
    else -> text.replace(TerminalImeSentinel, "")
}

private fun terminalCursorVisualPosition(
    layoutResult: androidx.compose.ui.text.TextLayoutResult,
    text: String,
    row: Int,
    column: Int,
    characterWidthPx: Float,
): TerminalCursorVisualPosition? {
    if (layoutResult.lineCount <= 0) return null
    val lineIndex = row.coerceIn(0, layoutResult.lineCount - 1)
    val lineStart = layoutResult.getLineStart(lineIndex).coerceIn(0, text.length)
    val lineEnd = layoutResult.getLineEnd(lineIndex, visibleEnd = false).coerceIn(lineStart, text.length)
    val lineText = text.substring(lineStart, lineEnd)
    val renderedCellWidth = terminalRenderedCellWidth(lineText)
    if (column > renderedCellWidth) {
        val lineEndX = layoutResult.getCursorRect(lineEnd).left
        val fallbackX = lineEndX + (column - renderedCellWidth) * characterWidthPx
        return TerminalCursorVisualPosition(
            x = fallbackX,
            y = layoutResult.getLineTop(lineIndex),
        )
    }
    val textColumnOffset = terminalTextOffsetForCellColumn(lineText, column)
    val offset = (lineStart + textColumnOffset).coerceIn(lineStart, lineEnd)
    val rect = layoutResult.getCursorRect(offset)
    val fallbackX = layoutResult.getCursorRect(lineStart).left + column.coerceAtLeast(0) * characterWidthPx
    return TerminalCursorVisualPosition(
        x = rect.left.takeIf { it >= 0f } ?: fallbackX,
        y = layoutResult.getLineTop(lineIndex),
    )
}

private fun terminalTextOffsetForCellColumn(
    line: String,
    column: Int,
): Int {
    if (column <= 0) return 0
    var cellColumn = 0
    var index = 0
    while (index < line.length && cellColumn < column) {
        val codePoint = line.codePointAt(index)
        cellColumn += terminalCellWidthForCodePoint(codePoint)
        index += Character.charCount(codePoint)
    }
    return index
}

internal fun terminalRenderedCellWidth(line: String): Int {
    var width = 0
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        width += terminalCellWidthForCodePoint(codePoint)
        index += Character.charCount(codePoint)
    }
    return width
}

internal fun isTerminalTextCellOccupied(
    text: String,
    column: Int,
    row: Int,
): Boolean {
    val line = text.lineSequence().drop(row.coerceAtLeast(0)).firstOrNull() ?: return false
    val offset = terminalTextOffsetForCellColumn(line, column)
    return offset < line.length && !line[offset].isWhitespace()
}

private fun terminalCellWidthForCodePoint(codePoint: Int): Int {
    val type = Character.getType(codePoint)
    return when {
        codePoint == 0 ||
            codePoint == 0x034F ||
            codePoint in 0x200B..0x200F ||
            codePoint == 0x2028 ||
            codePoint == 0x2029 ||
            codePoint in 0x202A..0x202E ||
            codePoint in 0x2060..0x2063 ||
            codePoint < 32 ||
            codePoint in 0x7F until 0xA0 ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() -> 0
        isTerminalWideCodePoint(codePoint) -> 2
        else -> 1
    }
}

private fun isTerminalWideCodePoint(codePoint: Int): Boolean =
    codePoint in 0x1100..0x115F ||
        codePoint in 0x2329..0x232A ||
        codePoint in 0x2E80..0xA4CF ||
        codePoint in 0xAC00..0xD7A3 ||
        codePoint in 0xF900..0xFAFF ||
        codePoint in 0xFE10..0xFE19 ||
        codePoint in 0xFE30..0xFE6F ||
        codePoint in 0xFF00..0xFF60 ||
        codePoint in 0xFFE0..0xFFE6 ||
        codePoint in 0x1F300..0x1FAFF ||
        codePoint in 0x20000..0x3FFFD

private data class TerminalCursorVisualPosition(
    val x: Float,
    val y: Float,
)

private fun openExternalTerminalUrl(
    context: android.content.Context,
    url: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launchResult = runCatching {
        context.startActivity(intent)
    }
    if (launchResult.isSuccess) return

    val failure = launchResult.exceptionOrNull()
    if (failure is ActivityNotFoundException || failure is SecurityException) {
        Toast.makeText(context, "Unable to open that link", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Unable to open that link", Toast.LENGTH_SHORT).show()
    }
}

private fun handleTerminalKeyEvent(
    event: KeyEvent,
    ctrlDown: Boolean,
    altDown: Boolean,
    fnDown: Boolean,
    applicationCursorKeysEnabled: Boolean,
    applicationKeypadEnabled: Boolean,
    onSendControl: (String) -> Unit,
    onPaste: () -> Unit,
    onShowUrls: () -> Unit,
    onChangeFontSize: (Boolean) -> Unit,
    onToggleKeyboard: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleFn: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if ((event.isCtrlPressed || ctrlDown) && event.isAltPressed) {
        when (event.key) {
            Key.K -> {
                onToggleKeyboard()
                return true
            }
            Key.V -> {
                onPaste()
                return true
            }
            Key.U -> {
                onShowUrls()
                return true
            }
            Key.Minus -> {
                onChangeFontSize(false)
                return true
            }
            else -> Unit
        }
        if (isTerminalFontIncreaseKey(event.key)) {
            onChangeFontSize(true)
            return true
        }
    }
    if (event.key == Key.VolumeDown) {
        onToggleCtrl()
        return true
    }
    if (event.key == Key.VolumeUp) {
        onToggleFn()
        return true
    }
    if ((event.isCtrlPressed || ctrlDown) && event.key == Key.Spacebar) {
        onSendControl("\u0000")
        return true
    }
    if ((event.isAltPressed || altDown) && event.key == Key.DirectionLeft) {
        onSendControl("\u001Bb")
        return true
    }
    if ((event.isAltPressed || altDown) && event.key == Key.DirectionRight) {
        onSendControl("\u001Bf")
        return true
    }
    if (fnDown) {
        terminalFnSequence(event.key)?.let { sequence ->
            onSendControl(sequence)
            onToggleFn()
            return true
        }
    }
    val sequence = terminalSpecialKeySequence(
        key = event.key,
        shift = event.isShiftPressed,
        alt = event.isAltPressed || altDown,
        ctrl = event.isCtrlPressed || ctrlDown,
        applicationCursorKeysEnabled = applicationCursorKeysEnabled,
        applicationKeypadEnabled = applicationKeypadEnabled,
    ) ?: return false
    onSendControl(sequence)
    if (ctrlDown) onToggleCtrl()
    if (altDown) onToggleAlt()
    return true
}

internal fun terminalSpecialKeySequence(
    key: Key,
    shift: Boolean,
    alt: Boolean,
    ctrl: Boolean,
    applicationCursorKeysEnabled: Boolean,
    applicationKeypadEnabled: Boolean,
): String? {
    if (shift && key == Key.Tab) return "\u001B[Z"
    if (ctrl && key == Key.Spacebar) return "\u0000"
    if (alt && key == Key.DirectionLeft && !shift && !ctrl) return "\u001Bb"
    if (alt && key == Key.DirectionRight && !shift && !ctrl) return "\u001Bf"
    val modifier = terminalKeyModifier(shift = shift, alt = alt, ctrl = ctrl)
    val sequence = when (key) {
        Key.Enter -> if (alt) "\u001B\r" else "\r"
        Key.NumPadEnter -> if (applicationKeypadEnabled) {
            terminalApplicationKeypadSequence('M', modifier)
        } else {
            "\n"
        }
        Key.Backspace -> (if (alt) "\u001B" else "") + if (ctrl) "\b" else "\u007F"
        Key.Tab -> "\t"
        Key.DirectionUp -> terminalCursorKeySequence(TerminalKey.ArrowUp, applicationCursorKeysEnabled, modifier)
        Key.DirectionDown -> terminalCursorKeySequence(TerminalKey.ArrowDown, applicationCursorKeysEnabled, modifier)
        Key.DirectionLeft -> terminalCursorKeySequence(TerminalKey.ArrowLeft, applicationCursorKeysEnabled, modifier)
        Key.DirectionRight -> terminalCursorKeySequence(TerminalKey.ArrowRight, applicationCursorKeysEnabled, modifier)
        Key.Escape -> "\u001B"
        Key.MoveHome -> terminalCursorKeySequence(TerminalKey.Home, applicationCursorKeysEnabled, modifier)
        Key.MoveEnd -> terminalCursorKeySequence(TerminalKey.End, applicationCursorKeysEnabled, modifier)
        Key.PageUp -> terminalTildeKeySequence(5, modifier)
        Key.PageDown -> terminalTildeKeySequence(6, modifier)
        Key.Delete -> terminalTildeKeySequence(3, modifier)
        Key.Insert -> terminalTildeKeySequence(2, modifier)
        Key.F1 -> terminalFunctionKeySequence(1, modifier)
        Key.F2 -> terminalFunctionKeySequence(2, modifier)
        Key.F3 -> terminalFunctionKeySequence(3, modifier)
        Key.F4 -> terminalFunctionKeySequence(4, modifier)
        Key.F5 -> terminalFunctionKeySequence(5, modifier)
        Key.F6 -> terminalFunctionKeySequence(6, modifier)
        Key.F7 -> terminalFunctionKeySequence(7, modifier)
        Key.F8 -> terminalFunctionKeySequence(8, modifier)
        Key.F9 -> terminalFunctionKeySequence(9, modifier)
        Key.F10 -> terminalFunctionKeySequence(10, modifier)
        Key.F11 -> terminalFunctionKeySequence(11, modifier)
        Key.F12 -> terminalFunctionKeySequence(12, modifier)
        else -> terminalKeypadSequence(key, applicationKeypadEnabled, modifier)
    } ?: return null
    return when {
        ctrl -> controlSequenceForKey(key) ?: sequence
        alt && modifier == null -> "\u001B$sequence"
        else -> sequence
    }
}

private const val TerminalImeSentinel = "x"
private const val TerminalBackspaceSequence = "\u007F"
private const val MinTerminalFontSizeSp = 10
private const val DefaultTerminalFontSizeSp = 13
private const val MaxTerminalFontSizeSp = 24
private const val TerminalAutoFollowBottomThresholdPx = 24

private val TerminalUrlRegex = Regex("""(?i)\b(?:https?|ftp|file)://[^\s<>"']+""")

private val TerminalUrlTrailingChars = charArrayOf(
    '.', ',', ';', ':', '!', '?', '"', '\'',
)

private val TerminalUrlTrailingPairs = mapOf(
    ')' to '(',
    ']' to '[',
    '}' to '{',
)

private val TerminalFontIncreaseKeys: Set<Key> =
    listOf("Plus", "Equals", "NumPadAdd", "NumpadAdd").mapNotNull(::composeKeyByName).toSet()

private val TerminalKeypadKeys: Map<Key, Char> =
    mapOf(
        "NumPad0" to '0',
        "Numpad0" to '0',
        "NumPad1" to '1',
        "Numpad1" to '1',
        "NumPad2" to '2',
        "Numpad2" to '2',
        "NumPad3" to '3',
        "Numpad3" to '3',
        "NumPad4" to '4',
        "Numpad4" to '4',
        "NumPad5" to '5',
        "Numpad5" to '5',
        "NumPad6" to '6',
        "Numpad6" to '6',
        "NumPad7" to '7',
        "Numpad7" to '7',
        "NumPad8" to '8',
        "Numpad8" to '8',
        "NumPad9" to '9',
        "Numpad9" to '9',
        "NumPadAdd" to '+',
        "NumpadAdd" to '+',
        "NumPadSubtract" to '-',
        "NumpadSubtract" to '-',
        "NumPadMultiply" to '*',
        "NumpadMultiply" to '*',
        "NumPadDivide" to '/',
        "NumpadDivide" to '/',
        "NumPadDot" to '.',
        "NumpadDot" to '.',
        "NumPadDecimal" to '.',
        "NumpadDecimal" to '.',
        "NumPadEquals" to '=',
        "NumpadEquals" to '=',
    ).mapNotNull { (name, value) ->
        composeKeyByName(name)?.let { key -> key to value }
    }.toMap()

private data class TerminalShortcut(
    val label: String,
    val sequence: String? = null,
    val key: TerminalKey? = null,
    val toggle: TerminalModifier? = null,
    val role: TerminalShortcutRole = when (label) {
        "Keyboard" -> TerminalShortcutRole.Keyboard
        "Paste" -> TerminalShortcutRole.Paste
        else -> TerminalShortcutRole.Sequence
    },
) {
    fun resolveSequence(applicationCursorKeysEnabled: Boolean): String? =
        sequence ?: key?.let { terminalCursorKeySequence(it, applicationCursorKeysEnabled) }
}

internal enum class TerminalKey {
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    Home,
    End,
}

private enum class TerminalShortcutRole {
    Sequence,
    Keyboard,
    Paste,
    FontSmaller,
    FontLarger,
}

private enum class TerminalModifier {
    Ctrl,
    Alt,
    Fn,
}

private data class TerminalMouseCell(
    val column: Int,
    val row: Int,
)

private data class TerminalTextCell(
    val column: Int,
    val row: Int,
)

internal enum class TerminalMouseEvent {
    PrimaryDown,
    PrimaryUp,
    PrimaryDrag,
    WheelUp,
    WheelDown,
}

internal fun buildTerminalInputSequence(
    text: String,
    ctrlDown: Boolean,
    altDown: Boolean,
    fnDown: Boolean,
): String = buildString {
    text.codePoints().forEach { codePoint ->
        val sequence = when {
            fnDown -> terminalFnSequence(codePoint) ?: String(Character.toChars(codePoint))
            ctrlDown -> controlSequenceForCodePoint(codePoint)
                ?: String(Character.toChars(codePoint))
            else -> String(Character.toChars(codePoint))
        }
        if (altDown) append('\u001B')
        append(sequence)
    }
}

internal fun buildTerminalPasteSequence(
    text: String,
    bracketedPasteEnabled: Boolean,
): String {
    val normalizedText = text
        .filterNot { it == '\u001B' || it in '\u0080'..'\u009F' }
        .replace(Regex("\r?\n"), "\r")
    return if (bracketedPasteEnabled) {
        "\u001B[200~$normalizedText\u001B[201~"
    } else {
        normalizedText
    }
}

internal fun buildTerminalAnnotatedOutput(
    prefix: String,
    styledOutput: TerminalStyledText,
    fallbackOutput: String,
): AnnotatedString {
    val outputText = styledOutput.text.ifEmpty { fallbackOutput }
    return AnnotatedString.Builder().apply {
        append(prefix)
        val outputStart = length
        append(outputText)
        if (styledOutput.text.isNotEmpty()) {
            styledOutput.ranges.forEach { range ->
                addStyle(
                    style = terminalSpanStyle(range.style),
                    start = outputStart + range.start,
                    end = outputStart + range.end,
                )
            }
        }
    }.toAnnotatedString()
}

internal fun extractTerminalUrls(text: String): List<String> {
    val urls = LinkedHashSet<String>()
    TerminalUrlRegex.findAll(text).forEach { match ->
        val url = trimTerminalUrl(match.value)
        if (url.isNotBlank()) {
            urls += url
        }
    }
    return urls.toList()
}

internal fun findTerminalUrlAtCell(
    text: String,
    column: Int,
    row: Int,
): String? {
    val line = text.lineSequence().drop(row.coerceAtLeast(0)).firstOrNull() ?: return null
    val normalizedColumn = column.coerceAtLeast(0)
    TerminalUrlRegex.findAll(line).forEach { match ->
        val url = trimTerminalUrl(match.value)
        if (url.isEmpty()) return@forEach
        val start = match.range.first
        val endExclusive = start + url.length
        if (normalizedColumn in start until endExclusive) {
            return url
        }
    }
    return null
}

internal fun nextTerminalFontSize(
    current: Int,
    increase: Boolean,
): Int =
    (current + if (increase) 1 else -1)
        .coerceIn(MinTerminalFontSizeSp, MaxTerminalFontSizeSp)

internal fun shouldFollowTerminalOutput(
    scrollValue: Int,
    maxScrollValue: Int,
): Boolean =
    maxScrollValue <= 0 || maxScrollValue - scrollValue <= TerminalAutoFollowBottomThresholdPx

internal fun isTerminalFontIncreaseKey(key: Key): Boolean =
    key in TerminalFontIncreaseKeys

internal fun terminalCursorKeySequence(
    key: TerminalKey,
    applicationCursorKeysEnabled: Boolean,
    modifier: Int? = null,
): String {
    val final = when (key) {
        TerminalKey.ArrowUp -> 'A'
        TerminalKey.ArrowDown -> 'B'
        TerminalKey.ArrowRight -> 'C'
        TerminalKey.ArrowLeft -> 'D'
        TerminalKey.Home -> 'H'
        TerminalKey.End -> 'F'
    }
    if (modifier != null) return "\u001B[1;${modifier}$final"
    return if (applicationCursorKeysEnabled) {
        "\u001BO$final"
    } else {
        "\u001B[$final"
    }
}

internal fun terminalKeyModifier(
    shift: Boolean,
    alt: Boolean,
    ctrl: Boolean,
): Int? {
    var modifier = 1
    if (shift) modifier += 1
    if (alt) modifier += 2
    if (ctrl) modifier += 4
    return modifier.takeIf { it > 1 }
}

internal fun terminalTildeKeySequence(
    number: Int,
    modifier: Int?,
): String =
    if (modifier == null) {
        "\u001B[${number}~"
    } else {
        "\u001B[${number};${modifier}~"
    }

internal fun terminalFunctionKeySequence(
    number: Int,
    modifier: Int?,
): String? {
    val final = when (number) {
        1 -> 'P'
        2 -> 'Q'
        3 -> 'R'
        4 -> 'S'
        else -> null
    }
    if (final != null) {
        return if (modifier == null) "\u001BO$final" else "\u001B[1;${modifier}$final"
    }
    val tildeNumber = when (number) {
        5 -> 15
        6 -> 17
        7 -> 18
        8 -> 19
        9 -> 20
        10 -> 21
        11 -> 23
        12 -> 24
        else -> return null
    }
    return terminalTildeKeySequence(tildeNumber, modifier)
}

internal fun terminalKeypadSequence(
    character: Char,
    applicationKeypadEnabled: Boolean,
    modifier: Int? = null,
): String? {
    if (!applicationKeypadEnabled) return character.toString()
    val final = when (character) {
        '0' -> 'p'
        '1' -> 'q'
        '2' -> 'r'
        '3' -> 's'
        '4' -> 't'
        '5' -> 'u'
        '6' -> 'v'
        '7' -> 'w'
        '8' -> 'x'
        '9' -> 'y'
        '.' -> 'n'
        '/' -> 'o'
        '*' -> 'j'
        '+' -> 'k'
        '-' -> 'm'
        '=' -> 'X'
        else -> return character.toString()
    }
    return terminalApplicationKeypadSequence(final, modifier)
}

private fun terminalApplicationKeypadSequence(
    final: Char,
    modifier: Int?,
): String =
    if (modifier == null) {
        "\u001BO$final"
    } else {
        "\u001BO;${modifier}$final"
    }

private fun terminalKeypadSequence(
    key: Key,
    applicationKeypadEnabled: Boolean,
    modifier: Int?,
): String? =
    TerminalKeypadKeys[key]?.let { character ->
        terminalKeypadSequence(character, applicationKeypadEnabled, modifier)
    }

private fun composeKeyByName(name: String): Key? =
    runCatching {
        Key::class.java.getField(name).get(null) as? Key
    }.getOrNull()

private fun terminalSpanStyle(style: TerminalTextStyle): SpanStyle {
    val foreground = style.foregroundColor?.let { Color(it) }
    val background = style.backgroundColor?.let { Color(it) }
    val textDecoration = terminalTextDecoration(style)
    return if (style.invisible) {
        SpanStyle(
            color = Color.Transparent,
            background = background ?: Color.Unspecified,
            fontWeight = if (style.bold) FontWeight.Bold else null,
            fontStyle = if (style.italic) FontStyle.Italic else null,
            textDecoration = textDecoration,
        )
    } else if (style.inverse) {
        SpanStyle(
            color = background ?: Color(0xFF050505),
            background = foreground ?: Color(0xFFD7FFD9),
            fontWeight = if (style.bold) FontWeight.Bold else null,
            fontStyle = if (style.italic) FontStyle.Italic else null,
            textDecoration = textDecoration,
        )
    } else {
        SpanStyle(
            color = foreground ?: if (style.dim) Color(0xFF7CA982) else Color.Unspecified,
            background = background ?: Color.Unspecified,
            fontWeight = if (style.bold) FontWeight.Bold else null,
            fontStyle = if (style.italic) FontStyle.Italic else null,
            textDecoration = textDecoration,
        )
    }
}

private fun terminalTextDecoration(style: TerminalTextStyle): TextDecoration? = when {
    style.underline && style.strikethrough -> TextDecoration.combine(
        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
    )
    style.underline -> TextDecoration.Underline
    style.strikethrough -> TextDecoration.LineThrough
    else -> null
}

private fun trimTerminalUrl(rawUrl: String): String {
    var url = rawUrl
    while (url.isNotEmpty()) {
        val last = url.last()
        when {
            TerminalUrlTrailingChars.contains(last) -> url = url.dropLast(1)
            TerminalUrlTrailingPairs[last]?.let { opening ->
                url.count { it == last } > url.count { it == opening }
            } == true -> url = url.dropLast(1)
            else -> return url
        }
    }
    return url
}

internal fun buildTerminalMouseSequence(
    event: TerminalMouseEvent,
    column: Int,
    row: Int,
    protocol: TerminalMouseProtocol,
): String? {
    val buttonCode = when (event) {
        TerminalMouseEvent.PrimaryDown,
        TerminalMouseEvent.PrimaryUp -> 0
        TerminalMouseEvent.PrimaryDrag -> 32
        TerminalMouseEvent.WheelUp -> 64
        TerminalMouseEvent.WheelDown -> 65
    }
    val x = column.coerceAtLeast(0) + 1
    val y = row.coerceAtLeast(0) + 1
    return when (protocol) {
        TerminalMouseProtocol.Sgr -> {
            val suffix = if (event == TerminalMouseEvent.PrimaryUp) 'm' else 'M'
            "\u001B[<$buttonCode;$x;${y}$suffix"
        }
        TerminalMouseProtocol.Normal -> {
            val normalButtonCode = if (event == TerminalMouseEvent.PrimaryUp) 3 else buttonCode
            if (x > 255 - 32 || y > 255 - 32) return null
            buildString {
                append("\u001B[M")
                append((normalButtonCode + 32).toChar())
                append((x + 32).toChar())
                append((y + 32).toChar())
            }
        }
    }
}

private fun controlSequenceForKey(key: Key): String? = when (key) {
    Key.A -> "\u0001"
    Key.B -> "\u0002"
    Key.C -> "\u0003"
    Key.D -> "\u0004"
    Key.E -> "\u0005"
    Key.F -> "\u0006"
    Key.G -> "\u0007"
    Key.H -> "\b"
    Key.I -> "\t"
    Key.J -> "\n"
    Key.K -> "\u000B"
    Key.L -> "\u000C"
    Key.M -> "\r"
    Key.N -> "\u000E"
    Key.O -> "\u000F"
    Key.P -> "\u0010"
    Key.Q -> "\u0011"
    Key.R -> "\u0012"
    Key.S -> "\u0013"
    Key.T -> "\u0014"
    Key.U -> "\u0015"
    Key.V -> "\u0016"
    Key.W -> "\u0017"
    Key.X -> "\u0018"
    Key.Y -> "\u0019"
    Key.Z -> "\u001A"
    Key.LeftBracket -> "\u001B"
    Key.Backslash -> "\u001C"
    Key.RightBracket -> "\u001D"
    Key.Minus -> "\u001F"
    else -> null
}

internal fun controlSequenceForCodePoint(codePoint: Int): String? {
    val lower = Character.toLowerCase(codePoint)
    return when {
        lower in 'a'.code..'z'.code -> ((lower - 'a'.code + 1).toChar()).toString()
        codePoint == ' '.code -> "\u0000"
        codePoint == '['.code -> "\u001B"
        codePoint == '\\'.code -> "\u001C"
        codePoint == ']'.code -> "\u001D"
        codePoint == '^'.code -> "\u001E"
        codePoint == '_'.code || codePoint == '-'.code -> "\u001F"
        else -> null
    }
}

private fun terminalFnSequence(key: Key): String? = when (key) {
    Key.W -> terminalFnSequence('w'.code)
    Key.A -> terminalFnSequence('a'.code)
    Key.S -> terminalFnSequence('s'.code)
    Key.D -> terminalFnSequence('d'.code)
    Key.P -> terminalFnSequence('p'.code)
    Key.N -> terminalFnSequence('n'.code)
    Key.T -> terminalFnSequence('t'.code)
    Key.I -> terminalFnSequence('i'.code)
    Key.H -> terminalFnSequence('h'.code)
    Key.U -> terminalFnSequence('u'.code)
    Key.L -> terminalFnSequence('l'.code)
    Key.E -> terminalFnSequence('e'.code)
    Key.B -> terminalFnSequence('b'.code)
    Key.F -> terminalFnSequence('f'.code)
    Key.X -> terminalFnSequence('x'.code)
    Key.One -> terminalFnSequence('1'.code)
    Key.Two -> terminalFnSequence('2'.code)
    Key.Three -> terminalFnSequence('3'.code)
    Key.Four -> terminalFnSequence('4'.code)
    Key.Five -> terminalFnSequence('5'.code)
    Key.Six -> terminalFnSequence('6'.code)
    Key.Seven -> terminalFnSequence('7'.code)
    Key.Eight -> terminalFnSequence('8'.code)
    Key.Nine -> terminalFnSequence('9'.code)
    Key.Zero -> terminalFnSequence('0'.code)
    else -> null
}

internal fun terminalFnSequence(codePoint: Int): String? = when (Character.toLowerCase(codePoint)) {
    'w'.code -> "\u001B[A"
    'a'.code -> "\u001B[D"
    's'.code -> "\u001B[B"
    'd'.code -> "\u001B[C"
    'p'.code -> "\u001B[5~"
    'n'.code -> "\u001B[6~"
    't'.code -> "\t"
    'i'.code -> "\u001B[2~"
    'h'.code -> "~"
    'u'.code -> "_"
    'l'.code -> "|"
    'e'.code -> "\u001B"
    'b'.code -> "\u001Bb"
    'f'.code -> "\u001Bf"
    'x'.code -> "\u001Bx"
    '1'.code -> "\u001BOP"
    '2'.code -> "\u001BOQ"
    '3'.code -> "\u001BOR"
    '4'.code -> "\u001BOS"
    '5'.code -> "\u001B[15~"
    '6'.code -> "\u001B[17~"
    '7'.code -> "\u001B[18~"
    '8'.code -> "\u001B[19~"
    '9'.code -> "\u001B[20~"
    '0'.code -> "\u001B[21~"
    else -> null
}

@Composable
private fun TerminalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF171717))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFFE8F5E9))
    }
}

@Composable
private fun TerminalTextButton(
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    !enabled -> Color(0xFF0B0B0B)
                    active -> Color(0xFF245A36)
                    else -> Color(0xFF171717)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFFE8F5E9) else Color(0xFF6B7280),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RuntimeDefaultsPage(
    title: String,
    termuxReady: Boolean,
    alpineReady: Boolean,
    enabledRuntimeIds: Set<LocalRuntimeId>,
    defaultRuntimeId: LocalRuntimeId?,
    onSetDefaultRuntime: (LocalRuntimeId) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = tr(
                strings,
                "Choose where tools run when a request omits the environment parameter. Agent calls can still explicitly select Termux or Alpine.",
                "选择工具未指定 environment 参数时使用的运行环境。Agent 仍然可以显式选择 Termux 或 Alpine。",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChoiceRow(
                    title = "Termux",
                    subtitle = if (termuxReady) {
                        tr(strings, "Android-side command bridge and Agent Mode workspace.", "手机侧命令桥接与 Agent Mode workspace。")
                    } else {
                        tr(strings, "Configure Termux before selecting it as default.", "先配置 Termux，才能设为默认运行环境。")
                    },
                    selected = defaultRuntimeId == LocalRuntimeId.Termux,
                    onClick = {
                        if (termuxReady || LocalRuntimeId.Termux in enabledRuntimeIds) {
                            onSetDefaultRuntime(LocalRuntimeId.Termux)
                        }
                    },
                )
                SettingsChoiceRow(
                    title = "Alpine",
                    subtitle = if (alpineReady) {
                        tr(strings, "Built-in app-private Linux VM for development tools.", "应用私有的内置 Linux 虚拟机，适合开发工具。")
                    } else {
                        tr(strings, "Initialize Alpine before selecting it as default.", "先初始化 Alpine，才能设为默认运行环境。")
                    },
                    selected = defaultRuntimeId == LocalRuntimeId.Alpine,
                    onClick = {
                        if (alpineReady || LocalRuntimeId.Alpine in enabledRuntimeIds) {
                            onSetDefaultRuntime(LocalRuntimeId.Alpine)
                        }
                    },
                )
            }
        }
    }
}

private fun alpineSetupStatusText(
    setupState: LocalRuntimeSetupState,
    strings: AetherStrings,
): String = when (setupState.issue) {
    LocalRuntimeIssue.Ready -> tr(strings, "Alpine is ready.", "Alpine 已就绪。")
    LocalRuntimeIssue.NotConfigured,
    LocalRuntimeIssue.NotInstalled -> tr(strings, "Alpine is not installed yet.", "Alpine 尚未安装。")
    LocalRuntimeIssue.UnsupportedAbi -> tr(strings, "This device ABI is not supported by Alpine v1.", "当前设备 ABI 不受 Alpine v1 支持。")
    LocalRuntimeIssue.MissingAssets -> tr(strings, "This build does not include Alpine runtime assets yet.", "当前构建尚未包含 Alpine 运行环境资源。")
    LocalRuntimeIssue.Failed -> tr(strings, "Alpine needs repair or reset.", "Alpine 需要修复或重置。")
    LocalRuntimeIssue.PermissionMissing,
    LocalRuntimeIssue.ExternalAppsDisabled,
    LocalRuntimeIssue.DispatchFailed -> tr(strings, "Alpine is not ready.", "Alpine 尚未就绪。")
}

@Composable
private fun WorkspaceModeSettingsSection(
    strings: AetherStrings,
    selectedWorkspaceMode: AgentWorkspaceMode,
    onWorkspaceModeSelected: (AgentWorkspaceMode) -> Unit,
) {
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strings.workspaceMode,
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.workspaceModeDescription,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChoiceRow(
                    title = tr(strings, "Single Workspace", "单一 Workspace"),
                    subtitle = tr(
                        strings,
                        "All sessions start in ~/.aether/workspace and uploads are kept under uploads/.",
                        "所有 Session 默认从 ~/.aether/workspace 开始，上传文件保存在 uploads/ 下。",
                    ),
                    selected = selectedWorkspaceMode == AgentWorkspaceMode.Shared,
                    onClick = { onWorkspaceModeSelected(AgentWorkspaceMode.Shared) },
                )
                SettingsChoiceRow(
                    title = tr(strings, "Independent Workspaces", "独立 Workspace"),
                    subtitle = tr(
                        strings,
                        "Each session keeps using its own directory under ~/.aether/workspaces/.",
                        "每个 Session 继续使用 ~/.aether/workspaces/ 下的独立目录。",
                    ),
                    selected = selectedWorkspaceMode == AgentWorkspaceMode.PerSession,
                    onClick = { onWorkspaceModeSelected(AgentWorkspaceMode.PerSession) },
                )
            }
        }
    }
}

@Composable
private fun AgentModeSettingsPage(
    title: String,
    termuxSetupState: TermuxSetupState,
    agentModeAuthorizationEnabled: Boolean,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    agentModeAuthorizationState: AgentModeAuthorizationState,
    rootSetupState: RootSetupState,
    onAgentModeAuthorizationEnabledChanged: (Boolean) -> Unit,
    onAgentModeAuthorizationMethodChanged: (AgentModeAuthorizationMethod) -> Unit,
    agentModeDisplayState: AgentModeDisplayState,
    onRequestShizukuPermission: () -> Unit,
    onRefreshAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onOpenShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
    onStopAgentModeDisplay: () -> Unit,
    onRefreshAgentModeDisplays: (AgentModeAuthorizationMethod) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var showAlreadyConfiguredDialog by rememberSaveable { mutableStateOf(false) }
    val agentModeConfigured = agentModeAuthorizationEnabled && agentModeAuthorizationState.isReady

    fun requestRootSetup() {
        if (agentModeConfigured && !rootSetupState.isRunning) {
            showAlreadyConfiguredDialog = true
        } else {
            onConfigureWithRoot()
        }
    }

    fun refreshAgentModeStatus() {
        onRefreshAgentModeAuthorization(
            agentModeAuthorizationEnabled,
            agentModeAuthorizationMethod,
        )
        onRefreshAgentModeDisplays(agentModeAuthorizationMethod)
    }

    if (!termuxSetupState.isReady) {
        LaunchedEffect(Unit) {
            onRefreshTermuxSetup()
        }
        SubPageScaffold(
            title = title,
            onBack = onBack,
            trailingIcon = Icons.Rounded.Refresh,
            onTrailingAction = onRefreshTermuxSetup,
        ) {
            SettingsCardGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = tr(strings, "Agent Mode is unavailable", "Agent Mode is unavailable"),
                        style = MaterialTheme.typography.labelLarge,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tr(
                            strings,
                            "Configure Termux first. Agent Mode uses the Termux bridge for its workspace and screenshot flow, so authorization cannot be enabled until Termux is ready.",
                            "Configure Termux first. Agent Mode uses the Termux bridge for its workspace and screenshot flow, so authorization cannot be enabled until Termux is ready.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TermuxSetupNotice(
                setupState = termuxSetupState,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
                showRefreshAction = true,
            )
        }
        return
    }
    if (showAlreadyConfiguredDialog) {
        RootSetupAlreadyConfiguredDialog(
            title = tr(strings, "Agent Mode is already configured", "Agent Mode 已配置完成"),
            body = if (agentModeAuthorizationMethod == AgentModeAuthorizationMethod.Root) {
                tr(
                    strings,
                    "Root Agent Mode authorization is already ready. You do not need to run Root automatic setup again.",
                    "Root Agent Mode 授权已经正常，不需要再次执行 Root 自动配置。",
                )
            } else {
                tr(
                    strings,
                    "Agent Mode authorization is already ready. Root automatic setup is not required; continuing will reconfigure Agent Mode to Root.",
                    "Agent Mode 授权已经正常，不需要执行 Root 自动配置；继续后会将 Agent Mode 重新配置为 Root。",
                )
            },
            onDismiss = { showAlreadyConfiguredDialog = false },
            onContinue = {
                showAlreadyConfiguredDialog = false
                onConfigureWithRoot()
            },
        )
    }

    LaunchedEffect(agentModeAuthorizationEnabled, agentModeAuthorizationMethod) {
        onRefreshRootSetup()
        refreshAgentModeStatus()
        while (true) {
            delay(SettingsAutoRefreshIntervalMillis)
            refreshAgentModeStatus()
        }
    }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Refresh,
        onTrailingAction = ::refreshAgentModeStatus,
    ) {
        Text(
            text = tr(strings, "Authorize isolated virtual-display tools with Shizuku or Root. Skip this on devices without either option.", "Authorize isolated virtual-display tools with Shizuku or Root. Skip this on devices without either option."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = tr(strings, "Agent Mode authorization", "Agent 模式授权"),
                    subtitle = tr(strings, "Enables isolated virtual-display tools. Requires Shizuku or Root.", "Enables isolated virtual-display tools. Requires Shizuku or Root."),
                    checked = agentModeAuthorizationEnabled,
                    onCheckedChange = onAgentModeAuthorizationEnabledChanged,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = tr(strings, "Authorization method", "授权方式"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentModeAuthorizationMethod.entries.forEach { method ->
                        SettingsChoiceRow(
                            title = method.displayName,
                            subtitle = when (method) {
                                AgentModeAuthorizationMethod.Shizuku -> tr(strings, "Uses an elevated Shizuku service.", "Uses an elevated Shizuku service.")
                                AgentModeAuthorizationMethod.Root -> tr(strings, "Uses root shell for privileged input.", "Uses root shell for privileged input.")
                            },
                            selected = agentModeAuthorizationMethod == method,
                            onClick = { onAgentModeAuthorizationMethodChanged(method) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tr(strings, "Shizuku mode creates the display from a Shizuku user service so apps can render off the main screen. Root mode remains experimental.", "Shizuku mode creates the display from a Shizuku user service so apps can render off the main screen. Root mode remains experimental."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                AgentModeAuthorizationNotice(
                    enabled = agentModeAuthorizationEnabled,
                    method = agentModeAuthorizationMethod,
                    state = agentModeAuthorizationState,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onOpenShizuku = onOpenShizuku,
                    onInstallShizuku = onInstallShizuku,
                )
                Spacer(Modifier.height(12.dp))
                RootSetupSettingsSection(
                    title = tr(strings, "Root automatic setup", "Root 自动配置"),
                    rootSetupState = rootSetupState,
                    body = tr(
                        strings,
                        "Use su to select Root mode and prepare local device control automatically.",
                        "使用 su 自动选择 Root 模式，并准备本地设备控制。",
                    ),
                    onConfigureWithRoot = ::requestRootSetup,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Virtual Display", "虚拟显示"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (agentModeDisplayState.isActive) {
                        tr(strings, "Display ${agentModeDisplayState.displayId ?: "-"} is active at ${agentModeDisplayState.width} x ${agentModeDisplayState.height}.", "Display ${agentModeDisplayState.displayId ?: "-"} is active at ${agentModeDisplayState.width} x ${agentModeDisplayState.height}.")
                    } else {
                        tr(strings, "No Agent Mode virtual display is active.", "No Agent Mode virtual display is active.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                if (agentModeDisplayState.latestWorkspacePath.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = agentModeDisplayState.latestWorkspacePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = tr(strings, "Visible displays", "可见显示"),
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (agentModeDisplayState.displays.isEmpty()) {
                    Text(
                        text = tr(strings, "No displays are currently visible to Aether.", "No displays are currently visible to Aether."),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        agentModeDisplayState.displays.forEach { display ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                if (display.isAetherDisplay) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                                } else {
                                    AetherBackground
                                }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr(strings, "Display ${display.displayId}", "显示 ${display.displayId}"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AetherOnSurface,
                                    )
                                    Text(
                                        text = listOf(
                                            display.name.ifBlank { tr(strings, "Unnamed", "Unnamed") },
                                            "${display.width} x ${display.height}",
                                            if (display.isAetherDisplay) "Aether" else "",
                                        ).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AetherOnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SettingsActionButton(
                    label = tr(strings, "Stop virtual display", "停止虚拟显示"),
                    onClick = onStopAgentModeDisplay,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = agentModeDisplayState.isActive,
                )
            }
        }
    }
}

@Composable
private fun RootSetupAlreadyConfiguredDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .shadow(22.dp, RoundedCornerShape(28.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(28.dp))
                .background(AetherSurfaceHigh)
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSubtleActionButton(
                    label = tr(strings, "Cancel", "取消"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = tr(strings, "Continue", "继续"),
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class RootSetupProgressStepStatus {
    Pending,
    Active,
    Complete,
    Attention,
}

@Composable
private fun RootSetupProgressPage(
    rootSetupState: RootSetupState,
    termuxSetupState: TermuxSetupState,
    agentModeAuthorizationEnabled: Boolean,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    agentModeAuthorizationState: AgentModeAuthorizationState,
    onRunRootSetup: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val rootAgentModeReady = agentModeAuthorizationEnabled &&
        agentModeAuthorizationMethod == AgentModeAuthorizationMethod.Root &&
        agentModeAuthorizationState.isReady
    val rootStepStatus = when {
        rootSetupState.isReady || rootSetupState.rootAvailable -> RootSetupProgressStepStatus.Complete
        rootSetupState.isRunning -> RootSetupProgressStepStatus.Active
        rootSetupState.issue == RootSetupIssue.Unavailable -> RootSetupProgressStepStatus.Attention
        else -> RootSetupProgressStepStatus.Pending
    }
    val termuxStepStatus = when {
        rootSetupState.isReady || termuxSetupState.isReady -> RootSetupProgressStepStatus.Complete
        rootSetupState.isRunning -> RootSetupProgressStepStatus.Active
        rootSetupState.issue == RootSetupIssue.TermuxNotInstalled ||
            rootSetupState.issue == RootSetupIssue.Failed -> RootSetupProgressStepStatus.Attention
        else -> RootSetupProgressStepStatus.Pending
    }
    val agentModeStepStatus = when {
        rootSetupState.isReady || rootAgentModeReady -> RootSetupProgressStepStatus.Complete
        rootSetupState.isRunning && termuxStepStatus == RootSetupProgressStepStatus.Complete ->
            RootSetupProgressStepStatus.Active
        rootSetupState.isRunning -> RootSetupProgressStepStatus.Pending
        rootSetupState.issue == RootSetupIssue.PermissionDenied ||
            rootSetupState.issue == RootSetupIssue.Failed -> RootSetupProgressStepStatus.Attention
        else -> RootSetupProgressStepStatus.Pending
    }

    SubPageScaffold(
        title = tr(strings, "Root automatic setup", "Root 自动配置"),
        onBack = onBack,
    ) {
        SettingsCardGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(rootSetupProgressAccent(rootSetupState.issue).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (rootSetupState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 3.dp,
                            color = AetherPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = if (rootSetupState.isReady) Icons.Rounded.Check else Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = rootSetupProgressAccent(rootSetupState.issue),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = rootSetupProgressTitle(rootSetupState.issue, strings),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = rootSetupProgressBody(rootSetupState.issue, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                if (rootSetupState.detail.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = rootSetupState.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RootSetupProgressStep(
                    title = tr(strings, "Root access", "Root 访问"),
                    subtitle = tr(strings, "Detect su and wait for authorization.", "检测 su 并等待授权。"),
                    status = rootStepStatus,
                )
                RootSetupProgressStep(
                    title = tr(strings, "Termux command access", "Termux 命令访问"),
                    subtitle = tr(strings, "Enable external apps and grant command permission.", "启用外部应用并授予命令权限。"),
                    status = termuxStepStatus,
                )
                RootSetupProgressStep(
                    title = tr(strings, "Root Agent Mode", "Root Agent Mode"),
                    subtitle = tr(strings, "Select Root authorization for local device control.", "为本地设备控制选择 Root 授权。"),
                    status = agentModeStepStatus,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            rootSetupState.isRunning -> SettingsSubtleActionButton(
                label = tr(strings, "Back", "返回"),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )

            rootSetupState.isReady -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSubtleActionButton(
                    label = tr(strings, "Run again", "重新执行"),
                    onClick = onRunRootSetup,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = tr(strings, "Done", "完成"),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSubtleActionButton(
                    label = tr(strings, "Back", "返回"),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = tr(strings, "Try again", "重试"),
                    onClick = onRunRootSetup,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RootSetupProgressStep(
    title: String,
    subtitle: String,
    status: RootSetupProgressStepStatus,
) {
    val (containerColor, contentColor) = when (status) {
        RootSetupProgressStepStatus.Complete -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) to AetherPrimary
        RootSetupProgressStepStatus.Active -> AetherPrimary.copy(alpha = 0.16f) to AetherPrimary
        RootSetupProgressStepStatus.Attention -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.64f) to MaterialTheme.colorScheme.error
        RootSetupProgressStepStatus.Pending -> AetherSurface to AetherOnSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            if (status == RootSetupProgressStepStatus.Active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(
                    imageVector = when (status) {
                        RootSetupProgressStepStatus.Complete -> Icons.Rounded.Check
                        RootSetupProgressStepStatus.Attention -> Icons.Rounded.Info
                        RootSetupProgressStepStatus.Pending,
                        RootSetupProgressStepStatus.Active -> Icons.Rounded.Refresh
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rootSetupProgressAccent(issue: RootSetupIssue): Color = when (issue) {
    RootSetupIssue.Ready -> AetherPrimary
    RootSetupIssue.Running,
    RootSetupIssue.Available -> AetherPrimary
    RootSetupIssue.PermissionDenied,
    RootSetupIssue.TermuxNotInstalled,
    RootSetupIssue.Failed -> MaterialTheme.colorScheme.error
    RootSetupIssue.Unknown,
    RootSetupIssue.Unavailable -> AetherOnSurfaceVariant
}

private fun rootSetupProgressTitle(
    issue: RootSetupIssue,
    strings: AetherStrings,
): String = when (issue) {
    RootSetupIssue.Running -> tr(strings, "Configuring local access", "正在配置本地访问")
    RootSetupIssue.Ready -> tr(strings, "Root setup completed", "Root 自动配置已完成")
    RootSetupIssue.Available -> tr(strings, "Ready to start", "准备开始")
    RootSetupIssue.Unavailable -> tr(strings, "Root is unavailable", "Root 不可用")
    RootSetupIssue.PermissionDenied -> tr(strings, "Root authorization was not granted", "未获得 Root 授权")
    RootSetupIssue.TermuxNotInstalled -> tr(strings, "Termux is required", "需要安装 Termux")
    RootSetupIssue.Failed -> tr(strings, "Setup did not complete", "配置未完成")
    RootSetupIssue.Unknown -> tr(strings, "Preparing setup", "正在准备配置")
}

private fun rootSetupProgressBody(
    issue: RootSetupIssue,
    strings: AetherStrings,
): String = when (issue) {
    RootSetupIssue.Running -> tr(
        strings,
        "Root access is available. Aether is enabling Termux command access and selecting Root Agent Mode.",
        "Root \u8bbf\u95ee\u5df2\u53ef\u7528\u3002Aether \u6b63\u5728\u542f\u7528 Termux \u547d\u4ee4\u8bbf\u95ee\uff0c\u5e76\u9009\u62e9 Root Agent Mode\u3002",
    )

    RootSetupIssue.Ready -> tr(
        strings,
        "Termux command access and Root Agent Mode authorization are ready.",
        "Termux \u547d\u4ee4\u8bbf\u95ee\u548c Root Agent Mode \u6388\u6743\u5df2\u7ecf\u5c31\u7eea\u3002",
    )

    RootSetupIssue.Available,
    RootSetupIssue.Unknown -> tr(
        strings,
        "Start setup to grant su and finish the local access configuration.",
        "开始配置后会请求 su，并完成本地访问配置。",
    )

    RootSetupIssue.Unavailable -> tr(
        strings,
        "No su binary was detected on this device.",
        "当前设备未检测到 su。",
    )

    RootSetupIssue.PermissionDenied -> tr(
        strings,
        "Grant su to Aether, then try Root automatic setup again.",
        "请授予 Aether su 权限后重试 Root 自动配置。",
    )

    RootSetupIssue.TermuxNotInstalled -> tr(
        strings,
        "Install Termux first, then return to this setup.",
        "请先安装 Termux，然后返回继续配置。",
    )

    RootSetupIssue.Failed -> tr(
        strings,
        "The setup command finished with an error. Review the detail above and retry when ready.",
        "配置命令返回错误。请查看上方详情后重试。",
    )
}

@Composable
private fun RootSetupSettingsSection(
    title: String,
    rootSetupState: RootSetupState,
    body: String,
    onConfigureWithRoot: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = AetherOnSurface,
            )
            if (rootSetupState.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AetherPrimary,
                )
            }
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        if (rootSetupState.detail.isNotBlank()) {
            Text(
                text = rootSetupState.detail,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SettingsSubtleActionButton(
            label = when (rootSetupState.issue) {
                RootSetupIssue.Running -> tr(strings, "Configuring...", "正在配置...")
                RootSetupIssue.Ready -> tr(strings, "Run Root setup again", "重新执行 Root 配置")
                RootSetupIssue.Unavailable -> tr(strings, "Try Root setup", "尝试 Root 配置")
                else -> tr(strings, "Configure with Root", "使用 Root 配置")
            },
            onClick = onConfigureWithRoot,
            modifier = Modifier.fillMaxWidth(),
            enabled = !rootSetupState.isRunning,
        )
    }
}

private fun rootSetupSettingsBody(
    rootSetupState: RootSetupState,
    strings: AetherStrings,
): String = when (rootSetupState.issue) {
    RootSetupIssue.Ready -> tr(
        strings,
        "Aether can silently wake Termux in the background with Root when Termux is not already running.",
        "Termux \u672a\u8fd0\u884c\u65f6\uff0cAether \u53ef\u4ee5\u901a\u8fc7 Root \u5728\u540e\u53f0\u9759\u9ed8\u5524\u8d77 Termux\u3002",
    )

    RootSetupIssue.Available,
    RootSetupIssue.Running -> tr(
        strings,
        "Aether can use su to enable Termux command access and background wake-up.",
        "Aether \u53ef\u4ee5\u901a\u8fc7 su \u542f\u7528 Termux \u547d\u4ee4\u8bbf\u95ee\u548c\u540e\u53f0\u5524\u8d77\u80fd\u529b\u3002",
    )

    RootSetupIssue.Unavailable,
    RootSetupIssue.Unknown -> tr(
        strings,
        "On rooted devices, this avoids the manual Termux permission and settings flow.",
        "在已 Root 设备上，这可以避免手动配置 Termux 权限和设置。",
    )

    RootSetupIssue.PermissionDenied,
    RootSetupIssue.TermuxNotInstalled,
    RootSetupIssue.Failed -> tr(
        strings,
        "Retry after granting su to Aether, or continue with the manual setup actions below.",
        "授予 Aether su 后重试，或继续使用下方的手动配置操作。",
    )
}

@Composable
private fun AgentModeAuthorizationNotice(
    enabled: Boolean,
    method: AgentModeAuthorizationMethod,
    state: AgentModeAuthorizationState,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val statusText = when {
        enabled && method == AgentModeAuthorizationMethod.Root -> when (state.issue) {
            AgentModeAuthorizationIssue.Ready -> tr(strings, "Root authorization is ready.", "Root authorization is ready.")
            AgentModeAuthorizationIssue.RootUnavailable -> tr(strings, "No su binary was detected on this device.", "No su binary was detected on this device.")
            AgentModeAuthorizationIssue.RootPermissionMissing -> tr(strings, "Grant su to Aether, then refresh this status.", "Grant su to Aether, then refresh this status.")
            AgentModeAuthorizationIssue.RootPermissionDenied -> tr(strings, "Root authorization was denied. Grant su to Aether, then refresh this status.", "Root authorization was denied. Grant su to Aether, then refresh this status.")
            AgentModeAuthorizationIssue.Error -> state.detail.ifBlank {
                tr(strings, "Unable to inspect Root status.", "Unable to inspect Root status.")
            }
            else -> state.detail.ifBlank {
                tr(strings, "Refresh Root status before using Agent Mode.", "Refresh Root status before using Agent Mode.")
            }
        }
        !enabled -> tr(strings, "Agent Mode authorization is off.", "Agent 模式授权已关闭。")
        method == AgentModeAuthorizationMethod.Root -> tr(strings, "Root mode will request su when Agent Mode starts.", "Root 模式会在启动 Agent 模式时请求 su。")
        else -> when (state.issue) {
            AgentModeAuthorizationIssue.Ready -> tr(strings, "Shizuku is running and Aether is authorized.", "Shizuku 正在运行，Aether 已获得授权。")
            AgentModeAuthorizationIssue.ShizukuNotInstalled -> tr(strings, "Install Shizuku before using Shizuku mode.", "使用 Shizuku 模式前，请先安装 Shizuku。")
            AgentModeAuthorizationIssue.ShizukuNotRunning -> tr(strings, "Start Shizuku, then refresh this status.", "先启动 Shizuku，然后刷新此状态。")
            AgentModeAuthorizationIssue.ShizukuPermissionMissing -> tr(strings, "Grant Aether permission in Shizuku before using Agent Mode.", "使用 Agent 模式前，请先在 Shizuku 中授予 Aether 权限。")
            AgentModeAuthorizationIssue.ShizukuPermissionDenied -> tr(strings, "Shizuku permission was denied. Request it again or enable Aether inside Shizuku.", "Shizuku 权限被拒绝。请重新请求授权，或在 Shizuku 中启用 Aether。")
            AgentModeAuthorizationIssue.Disabled -> tr(strings, "Save Agent Mode settings, then refresh Shizuku status.", "保存 Agent 模式设置后，再刷新 Shizuku 状态。")
            AgentModeAuthorizationIssue.RootUnavailable,
            AgentModeAuthorizationIssue.RootPermissionMissing,
            AgentModeAuthorizationIssue.RootPermissionDenied -> state.detail.ifBlank {
                tr(strings, "Switch to Root mode to inspect Root status.", "Switch to Root mode to inspect Root status.")
            }
            AgentModeAuthorizationIssue.Error -> state.detail.ifBlank {
                tr(strings, "Unable to inspect Shizuku status.", "无法检查 Shizuku 状态。")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = tr(strings, "Authorization status", "授权状态"),
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
        )
        Text(
            text = if (state.issue == AgentModeAuthorizationIssue.Error && state.detail.isNotBlank()) {
                state.detail
            } else {
                statusText
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )

        when {
            !enabled || method == AgentModeAuthorizationMethod.Root -> Unit

            state.issue == AgentModeAuthorizationIssue.ShizukuNotInstalled -> {
                SettingsSubtleActionButton(
                    label = tr(strings, "Install Shizuku", "安装 Shizuku"),
                    onClick = onInstallShizuku,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.issue == AgentModeAuthorizationIssue.ShizukuNotRunning -> {
                SettingsSubtleActionButton(
                    label = tr(strings, "Open Shizuku", "打开 Shizuku"),
                    onClick = onOpenShizuku,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.issue == AgentModeAuthorizationIssue.ShizukuPermissionMissing ||
                state.issue == AgentModeAuthorizationIssue.ShizukuPermissionDenied -> {
                SettingsSubtleActionButton(
                    label = tr(strings, "Grant access", "授予权限"),
                    onClick = onRequestShizukuPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun DeveloperSettingsPage(
    title: String,
    onReplayFollowUpOnboarding: () -> Unit,
    onImportAppData: () -> Unit,
    onExportAppData: () -> Unit,
    onExportLogs: () -> Unit,
    onForceUpdateCheckForTesting: () -> Unit,
    termuxReadyForTesting: Boolean,
    onTermuxReadyForTestingChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = tr(strings, "Developer-only tools and replay controls.", "Developer-only tools and replay controls."),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "App Data", "应用数据"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(strings, "Import or export the complete local Aether data set as JSON.", "Import or export the complete local Aether data set as JSON."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Import app data", "导入应用数据"),
                    onClick = onImportAppData,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Export app data", "导出应用数据"),
                    onClick = onExportAppData,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Logs", "日志"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(strings, "Export recent Aether logcat output and diagnostic metadata.", "导出近期 Aether logcat 输出和诊断信息。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Export logs", "导出日志"),
                    onClick = onExportLogs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Update testing", "Update testing"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(
                        strings,
                        "Fetch the latest GitHub Release and show the update prompt even when the installed version is current.",
                        "Fetch the latest GitHub Release and show the update prompt even when the installed version is current.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Force update prompt", "Force update prompt"),
                    onClick = onForceUpdateCheckForTesting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Termux readiness override", "Termux readiness override"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(
                        strings,
                        "Testing switch for Agent Mode gating. On treats Termux as ready; off treats it as not ready.",
                        "Testing switch for Agent Mode gating. On treats Termux as ready; off treats it as not ready.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsToggleRow(
                    title = tr(strings, "Treat Termux as ready", "Treat Termux as ready"),
                    subtitle = tr(strings, "On = ready. Off = not ready.", "On = ready. Off = not ready."),
                    checked = termuxReadyForTesting,
                    onCheckedChange = onTermuxReadyForTestingChanged,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(strings, "Replay Follow-up Tour", "重播后续引导"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(strings, "Starts from Termux, then goes through Agent Mode, Tavily, Skills, and MCP.", "Starts from Termux, then goes through Agent Mode, Tavily, Skills, and MCP."),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = tr(strings, "Replay second part", "重播第二部分"),
                    onClick = onReplayFollowUpOnboarding,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Shared building blocks
// -----------------------------------------------------------------------------

// Sub-page scaffold

@Composable
private fun AboutPage(
    title: String,
    appUpdate: AppUpdateUiState,
    onOpenWebsite: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val updateSubtitle = when {
        appUpdate.isDownloading -> appUpdate.downloadProgress?.let { progress ->
            tr(strings, "Downloading ${(progress * 100).toInt()}%", "Downloading ${(progress * 100).toInt()}%")
        } ?: tr(strings, "Downloading update", "Downloading update")
        appUpdate.isChecking -> tr(strings, "Checking GitHub Releases", "Checking GitHub Releases")
        appUpdate.availableRelease != null -> tr(
            strings,
            "Aether ${appUpdate.availableRelease.versionName} is available",
            "Aether ${appUpdate.availableRelease.versionName} is available",
        )
        else -> tr(strings, "Check GitHub Releases for a newer APK", "Check GitHub Releases for a newer APK")
    }
    val releaseLabel = tr(strings, "Release ${BuildConfig.VERSION_NAME}", "版本 ${BuildConfig.VERSION_NAME}")
    SubPageScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.aether_mark),
                contentDescription = tr(strings, "Aether logo", "Aether 标志"),
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Aether",
                style = MaterialTheme.typography.titleLarge,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = releaseLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsCardGroup {
            AboutInfoRow(label = tr(strings, "Author", "Author"), value = "Zhou-Shilin")
            CardDivider()
            AboutInfoRow(label = tr(strings, "Version", "版本"), value = releaseLabel)
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Refresh,
                title = tr(strings, "Check for updates", "Check for updates"),
                subtitle = updateSubtitle,
                onClick = onCheckForUpdates,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = tr(strings, "Website", "网站"),
                subtitle = AetherWebsiteUrl.removePrefix("https://"),
                onClick = onOpenWebsite,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = tr(strings, "Privacy Policy", "隐私政策"),
                subtitle = AetherPrivacyPolicyUrl.removePrefix("https://"),
                onClick = onOpenPrivacyPolicy,
            )
        }

        if (appUpdate.availableRelease != null) {
            Spacer(Modifier.height(16.dp))
            SettingsActionButton(
                label = if (appUpdate.isDownloading) {
                    updateSubtitle
                } else {
                    tr(strings, "Download and install", "Download and install")
                },
                onClick = onDownloadAndInstallUpdate,
                enabled = !appUpdate.isDownloading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingEnabled: Boolean = true,
    trailingContentDescription: String = title,
    onTrailingAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var topBarBodyHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val fallbackTopBarBodyHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 68.dp
    }
    val topBarBodyHeight = with(density) {
        if (topBarBodyHeightPx > 0) topBarBodyHeightPx.toDp() else fallbackTopBarBodyHeight
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topBarBodyHeight)
                    .padding(horizontal = 20.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Spacer(Modifier.height(6.dp))
                content()
                Spacer(Modifier.height(32.dp))
            }

            SettingsTopBarOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                title = title,
                onBack = onBack,
                trailingIcon = trailingIcon,
                trailingEnabled = trailingEnabled,
                trailingContentDescription = trailingContentDescription,
                onTrailingAction = onTrailingAction,
                onBodyHeightChanged = { topBarBodyHeightPx = it },
            )
        }
    }
}

// Top bar

@Composable
private fun SettingsTopBarOverlay(
    modifier: Modifier = Modifier,
    title: String,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingEnabled: Boolean = true,
    trailingContentDescription: String = title,
    onTrailingAction: (() -> Unit)? = null,
    onBodyHeightChanged: (Int) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(settingsTopOverlayBodyGradient())
                .onSizeChanged { onBodyHeightChanged(it.height) },
        ) {
            SettingsTopBar(
                title = title,
                onBack = onBack,
                trailingIcon = trailingIcon,
                trailingEnabled = trailingEnabled,
                trailingContentDescription = trailingContentDescription,
                onTrailingAction = onTrailingAction,
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(SettingsTopFadeHeight)
                .background(settingsTopOverlayTailGradient())
        )
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingEnabled: Boolean = true,
    trailingContentDescription: String = title,
    onTrailingAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCircleButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AetherOnSurface,
        )
        if (trailingIcon != null && onTrailingAction != null) {
            SettingsCircleButton(
                icon = trailingIcon,
                contentDescription = trailingContentDescription,
                enabled = trailingEnabled,
                onClick = onTrailingAction,
            )
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun SettingsCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(10.dp, RoundedCornerShape(50), ambientColor = AetherScrim, spotColor = AetherScrim)
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
