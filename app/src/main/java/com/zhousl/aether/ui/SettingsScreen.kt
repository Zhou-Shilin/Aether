package com.zhousl.aether.ui

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
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
import com.zhousl.aether.runtime.AlpineTerminalLaunchSpec
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherTertiary
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
    Statistics,
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
    SettingsPage.RuntimeDefaults,
    SettingsPage.AgentMode,
    SettingsPage.Statistics,
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
    SettingsPage.AlpineTerminal,
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



@Composable
private fun settingsLanguageDisplayName(language: AppLanguage): String = when (language) {
    AppLanguage.English -> stringResource(R.string.language_english)
    AppLanguage.SimplifiedChinese -> stringResource(R.string.language_simplified_chinese)
}

@Composable
private fun settingsLanguageSubtitle(language: AppLanguage): String = when (language) {
    AppLanguage.English -> stringResource(R.string.settings_language_english_interface)
    AppLanguage.SimplifiedChinese -> stringResource(R.string.settings_language_simplified_chinese_interface)
}

@Composable
private fun settingsThemeDisplayName(themeMode: AppThemeMode): String = when (themeMode) {
    AppThemeMode.System -> stringResource(R.string.theme_system)
    AppThemeMode.Light -> stringResource(R.string.theme_light)
    AppThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
private fun settingsThemeSubtitle(themeMode: AppThemeMode): String = when (themeMode) {
    AppThemeMode.System -> stringResource(R.string.settings_system_theme_subtitle)
    AppThemeMode.Light -> stringResource(R.string.settings_light_theme_subtitle)
    AppThemeMode.Dark -> stringResource(R.string.settings_dark_theme_subtitle)
}

@Composable
private fun settingsGeneralSummary(language: AppLanguage, themeMode: AppThemeMode): String =
    stringResource(
        R.string.settings_general_summary,
        settingsLanguageDisplayName(language),
        settingsThemeDisplayName(themeMode),
    )

@Composable
private fun settingsEnabledProvidersSummary(enabledCount: Int): String =
    stringResource(R.string.settings_enabled_providers_count, enabledCount)

@Composable
private fun settingsReleaseSummary(versionName: String): String =
    stringResource(R.string.settings_release_summary, versionName)

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
    sessions: List<ChatSession>,
    scheduledTasks: List<ScheduledTask>,
    termuxSetupState: TermuxSetupState,
    alpineSetupState: LocalRuntimeSetupState,
    enabledRuntimeIds: Set<LocalRuntimeId>,
    defaultRuntimeId: LocalRuntimeId?,
    alpinePackageProfiles: Map<String, PackageProfileState>,
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
    onCreateAlpineTerminalLaunchSpec: suspend () -> Result<AlpineTerminalLaunchSpec>,
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
    val agentModeRequiresTermuxToastLabel = stringResource(R.string.settings_agent_mode_requires_termux_toast)
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
    LaunchedEffect(language) {
        languageValue = language
    }
    var defaultChatModelKeyValue by rememberSaveable { mutableStateOf(defaultChatModelKey) }
    var defaultTitleModelKeyValue by rememberSaveable { mutableStateOf(defaultTitleModelKey) }
    var defaultNamingModelKeyValue by rememberSaveable { mutableStateOf(defaultNamingModelKey) }
    var defaultCompactingModelKeyValue by rememberSaveable { mutableStateOf(defaultCompactingModelKey) }

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
                generalSettingsSummary = settingsGeneralSummary(
                    language = languageValue,
                    themeMode = themeModeValue,
                ),
                activeProviderName = providerConfigs.count { it.isEnabled }.let { enabledCount ->
                    when {
                        enabledCount > 1 -> settingsEnabledProvidersSummary(enabledCount)
                        enabledCount == 1 -> providerConfigs.firstOrNull { it.isEnabled }?.name.orEmpty()
                        enabledModelOptions.isNotEmpty() -> enabledModelOptions.first().fullLabel
                        else -> provider.displayName
                    }
                },
                systemPromptSnippet = systemPromptValue.text.take(60),
                tavilyConfigured = tavilyApiKeyValue.text.isNotBlank(),
                reliabilitySummary = buildString {
                    append(
                        stringResource(
                            R.string.settings_reconnect_after_seconds,
                            normalizeLlmInactivityReconnectTimeoutSeconds(
                                llmInactivityReconnectTimeoutValue.text.trim().toIntOrNull()
                            ),
                        )
                    )
                    append(" · ")
                    append(
                        stringResource(
                            if (keepTasksRunningInBackgroundValue) {
                                R.string.settings_background_runs_on
                            } else {
                                R.string.settings_background_runs_off
                            },
                        )
                    )
                },
                termuxReady = termuxSetupState.isReady,
                alpineReady = alpineSetupState.isReady,
                defaultRuntimeId = defaultRuntimeId,
                showRuntimeDefaults = termuxSetupState.isReady && alpineSetupState.isReady,
                skillCount = installedSkills.size,
                mcpServerCount = mcpServers.size,
                scheduledTaskCount = scheduledTasks.size,
                statisticsSummary = buildSettingsStatisticsSummary(sessions),
                onReplayOnboarding = ::persistAndReplayOnboarding,
                onNavigate = { page ->
                    if (page == SettingsPage.AgentMode && !termuxSetupState.isReady) {
                        Toast.makeText(
                            context,
                            agentModeRequiresTermuxToastLabel,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        currentPage = page.name
                    }
                },
                onBack = ::persistAndExit,
            )

            SettingsPage.General -> GeneralSettingsPageV2(
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
                title = stringResource(R.string.settings_default_chat_model),
                subtitle = stringResource(R.string.settings_default_chat_model_subtitle),
                selectedKey = defaultChatModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
                )?.fullLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                    ?: stringResource(R.string.settings_automatic_model),
                automaticSubtitle = stringResource(R.string.settings_prioritize_sota_models),
                onSelected = { defaultChatModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultTitleModel -> ModelSelectionListPage(
                title = stringResource(R.string.settings_default_title_model),
                subtitle = stringResource(R.string.settings_default_title_model_subtitle),
                selectedKey = defaultTitleModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Title)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                    ?: stringResource(R.string.settings_automatic_model),
                automaticSubtitle = stringResource(R.string.settings_prioritize_sota_models),
                onSelected = { defaultTitleModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultNamingModel -> ModelSelectionListPage(
                title = stringResource(R.string.settings_default_naming_model),
                subtitle = stringResource(R.string.settings_default_naming_model_subtitle),
                selectedKey = defaultNamingModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Naming)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                    ?: stringResource(R.string.settings_automatic_model),
                automaticSubtitle = stringResource(R.string.settings_prioritize_sota_models),
                onSelected = { defaultNamingModelKeyValue = it },
                onBack = { currentPage = SettingsPage.DefaultModels.name },
            )

            SettingsPage.DefaultCompactingModel -> ModelSelectionListPage(
                title = stringResource(R.string.settings_default_compacting_model),
                subtitle = stringResource(R.string.settings_default_compacting_model_subtitle),
                selectedKey = defaultCompactingModelKeyValue,
                options = enabledModelOptions,
                automaticLabel = enabledModelOptions.findModelOption(
                    enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Compacting)
                        .ifBlank { enabledModelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) }
                )?.fullLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                    ?: stringResource(R.string.settings_automatic_model),
                automaticSubtitle = stringResource(R.string.settings_prioritize_efficient_summary_models),
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
                title = stringResource(R.string.settings_personalization),
                systemPromptValue = systemPromptValue,
                onSystemPromptChanged = { systemPromptValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.WebTools -> WebToolsPage(
                title = stringResource(R.string.settings_web_tools),
                tavilyApiKeyValue = tavilyApiKeyValue,
                onTavilyApiKeyChanged = { tavilyApiKeyValue = it },
                tavilyBaseUrlValue = tavilyBaseUrlValue,
                onTavilyBaseUrlChanged = { tavilyBaseUrlValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Reliability -> ReliabilityPage(
                title = stringResource(R.string.settings_reliability),
                llmInactivityReconnectTimeoutValue = llmInactivityReconnectTimeoutValue,
                onLlmInactivityReconnectTimeoutChanged = { llmInactivityReconnectTimeoutValue = it },
                keepTasksRunningInBackground = keepTasksRunningInBackgroundValue,
                onKeepTasksRunningInBackgroundChanged = { keepTasksRunningInBackgroundValue = it },
                notifyOnTaskCompletion = notifyOnTaskCompletionValue,
                onNotifyOnTaskCompletionChanged = { notifyOnTaskCompletionValue = it },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.Skills -> SkillsListPage(
                title = stringResource(R.string.settings_agent_skills),
                installedSkills = installedSkills,
                onToggleSkillEnabled = onToggleSkillEnabled,
                onRemoveSkill = onRemoveSkill,
                onAddNew = { currentPage = SettingsPage.AddSkill.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AddSkill -> AddSkillPage(
                title = stringResource(R.string.settings_agent_skills),
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
                title = stringResource(R.string.settings_mcp_servers),
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
                title = stringResource(R.string.settings_mcp_servers),
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
                title = stringResource(R.string.settings_mcp_servers),
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
                title = stringResource(R.string.settings_termux),
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
                onInstallPackageProfile = onInstallAlpinePackageProfile,
                onSetDefault = { onSetDefaultRuntime(LocalRuntimeId.Alpine) },
                onOpenTerminal = { currentPage = SettingsPage.AlpineTerminal.name },
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AlpineTerminal -> AlpineTerminalScreen(
                createLaunchSpec = onCreateAlpineTerminalLaunchSpec,
                onBack = { currentPage = SettingsPage.Alpine.name },
            )

            SettingsPage.RuntimeDefaults -> RuntimeDefaultsPage(
                title = stringResource(R.string.settings_runtime_defaults),
                termuxReady = termuxSetupState.isReady,
                alpineReady = alpineSetupState.isReady,
                enabledRuntimeIds = enabledRuntimeIds,
                defaultRuntimeId = defaultRuntimeId,
                onSetDefaultRuntime = onSetDefaultRuntime,
                onBack = { currentPage = SettingsPage.Hub.name },
            )

            SettingsPage.AgentMode -> AgentModeSettingsPage(
                title = stringResource(R.string.settings_agent_mode),
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

            SettingsPage.Statistics -> StatisticsSettingsPage(
                title = stringResource(R.string.settings_statistics),
                sessions = sessions,
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
                title = stringResource(R.string.settings_developer),
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
                title = stringResource(R.string.settings_about),
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
    statisticsSummary: String,
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
                        title = stringResource(R.string.settings_general),
                        subtitle = generalSettingsSummary.ifBlank { stringResource(R.string.settings_general_hint) },
                        onClick = { onNavigate(SettingsPage.General) },
                    )
                }

                Spacer(Modifier.height(16.dp))

            // Configuration card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.settings_model_providers),
                    subtitle = activeProviderName,
                    onClick = { onNavigate(SettingsPage.Providers) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.settings_personalization),
                    subtitle = systemPromptSnippet.ifBlank { stringResource(R.string.settings_custom_instructions) },
                    onClick = { onNavigate(SettingsPage.Personalization) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.settings_web_tools),
                    subtitle = if (tavilyConfigured) {
                        stringResource(R.string.settings_tavily_configured)
                    } else {
                        stringResource(R.string.settings_tavily_not_configured)
                    },
                    onClick = { onNavigate(SettingsPage.WebTools) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Refresh,
                    title = stringResource(R.string.settings_reliability),
                    subtitle = reliabilitySummary,
                    onClick = { onNavigate(SettingsPage.Reliability) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Extensions card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Extension,
                    title = stringResource(R.string.settings_agent_skills),
                    subtitle = stringResource(R.string.settings_skills_count_configured, skillCount),
                    onClick = { onNavigate(SettingsPage.Skills) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.settings_mcp_servers),
                    subtitle = stringResource(R.string.settings_mcp_server_count_summary, mcpServerCount),
                    onClick = { onNavigate(SettingsPage.McpServers) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.settings_scheduled_tasks),
                    subtitle = stringResource(R.string.settings_scheduled_tasks_count_configured, scheduledTaskCount),
                    onClick = { onNavigate(SettingsPage.ScheduledTasks) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Terminal,
                    title = stringResource(R.string.settings_termux),
                    subtitle = if (termuxReady) stringResource(R.string.settings_connected) else stringResource(R.string.settings_setup_required),
                    onClick = { onNavigate(SettingsPage.Termux) },
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = "Alpine",
                    subtitle = if (alpineReady) { stringResource(R.string.settings_alpine_subtitle_ready) } else { stringResource(R.string.settings_alpine_subtitle_setup) },
                    onClick = { onNavigate(SettingsPage.Alpine) },
                )
                if (showRuntimeDefaults) {
                    CardDivider()
                    SettingsNavRow(
                        icon = Icons.Rounded.Check,
                        title = stringResource(R.string.settings_runtime_defaults),
                        subtitle = defaultRuntimeId?.displayName ?: stringResource(R.string.settings_runtime_defaults_hint),
                        onClick = { onNavigate(SettingsPage.RuntimeDefaults) },
                    )
                }
                CardDivider()
                SettingsNavRow(
                    icon = LucideIcons.MousePointer2,
                    title = stringResource(R.string.settings_agent_mode),
                    subtitle = if (termuxReady) {
                        stringResource(R.string.settings_agent_mode_subtitle)
                    } else {
                        stringResource(R.string.settings_requires_termux_setup)
                    },
                    enabled = termuxReady,
                    onClick = { onNavigate(SettingsPage.AgentMode) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsCardGroup {
                SettingsNavRow(
                    icon = LucideIcons.ChartNoAxesColumn,
                    title = stringResource(R.string.settings_statistics),
                    subtitle = statisticsSummary.ifBlank { stringResource(R.string.settings_statistics_empty) },
                    onClick = { onNavigate(SettingsPage.Statistics) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // About card
            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_get_started_tour),
                    subtitle = stringResource(R.string.settings_get_started_tour_subtitle),
                    onClick = onReplayOnboarding,
                )
                CardDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.settings_developer),
                    subtitle = stringResource(R.string.settings_developer_subtitle),
                    onClick = { onNavigate(SettingsPage.Developer) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsCardGroup {
                SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = settingsReleaseSummary(BuildConfig.VERSION_NAME),
                    onClick = { onNavigate(SettingsPage.About) },
                )
            }

            Spacer(Modifier.height(32.dp))
            }

            SettingsTopBarOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                onBodyHeightChanged = { topBarBodyHeightPx = it },
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Statistics
// -----------------------------------------------------------------------------

@Composable
private fun StatisticsSettingsPage(
    title: String,
    sessions: List<ChatSession>,
    onBack: () -> Unit,
) {
    val report = remember(sessions) { buildUsageStatisticsReport(sessions) }
    SubPageScaffold(title = title, onBack = onBack) {
        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.statistics_overview),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatisticsMetricTile(
                        label = stringResource(R.string.statistics_total_tokens),
                        value = formatSettingsTokenCount(report.totalTokens),
                        modifier = Modifier.weight(1f),
                    )
                    StatisticsMetricTile(
                        label = stringResource(R.string.statistics_sessions),
                        value = report.sessionCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatisticsMetricTile(
                        label = stringResource(R.string.statistics_average_speed),
                        value = report.averageOutputTokensPerSecond?.let(::formatSettingsTokenRate)
                            ?: stringResource(R.string.statistics_unavailable),
                        modifier = Modifier.weight(1f),
                    )
                    StatisticsMetricTile(
                        label = stringResource(R.string.statistics_average_latency),
                        value = report.averageFirstTokenLatencyMillis?.let(::formatSettingsDuration)
                            ?: stringResource(R.string.statistics_unavailable),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            StatisticsChartSection(
                title = stringResource(R.string.statistics_daily_token_usage),
                subtitle = stringResource(R.string.statistics_recent_7_days),
            ) {
                TokenBarChart(points = report.dailyTokenUsage.takeLast(7))
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            StatisticsChartSection(
                title = stringResource(R.string.statistics_recent_token_usage),
                subtitle = stringResource(R.string.statistics_recent_14_days),
            ) {
                TokenLineChart(points = report.dailyTokenUsage.takeLast(14))
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.statistics_historical_token_usage),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                HistoryPeakRow(
                    label = stringResource(R.string.statistics_peak_day),
                    value = report.peakDay?.let { "${it.label} · ${formatSettingsTokenCount(it.tokens)}" }
                        ?: stringResource(R.string.statistics_unavailable),
                )
                HistoryPeakRow(
                    label = stringResource(R.string.statistics_largest_turn),
                    value = report.largestTurnTokens?.let(::formatSettingsTokenCount)
                        ?: stringResource(R.string.statistics_unavailable),
                )
                HistoryPeakRow(
                    label = stringResource(R.string.statistics_recorded_turns),
                    value = report.turnCount.toString(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.statistics_token_mix),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TokenMixPieChart(
                        inputTokens = report.inputTokens,
                        outputTokens = report.outputTokens,
                        reasoningTokens = report.reasoningTokens,
                        modifier = Modifier.size(132.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenMixLegend(stringResource(R.string.statistics_input), AetherPrimary, report.inputTokens)
                        TokenMixLegend(stringResource(R.string.statistics_output), AetherSecondary, report.outputTokens)
                        TokenMixLegend(stringResource(R.string.statistics_reasoning), AetherTertiary, report.reasoningTokens)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            StatisticsChartSection(
                title = stringResource(R.string.statistics_speed),
                subtitle = stringResource(R.string.statistics_speed_subtitle),
            ) {
                SpeedBarChart(points = report.recentSpeedSamples.takeLast(12))
            }
        }
    }
}

@Composable
private fun StatisticsMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatisticsChartSection(
    title: String,
    subtitle: String,
    chart: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AetherOnSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        chart()
    }
}

@Composable
private fun TokenBarChart(points: List<DailyTokenUsage>) {
    val maxTokens = points.maxOfOrNull { it.tokens }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val fraction = point.tokens.toFloat() / maxTokens
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = formatSettingsTokenCount(point.tokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((18 + 96 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(AetherPrimary.copy(alpha = 0.18f + 0.44f * fraction)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = point.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TokenLineChart(points: List<DailyTokenUsage>) {
    val lineColor = AetherPrimary
    val fillColor = AetherPrimary.copy(alpha = 0.12f)
    val labelColor = AetherOnSurfaceVariant
    val maxTokens = points.maxOfOrNull { it.tokens }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AetherSurfaceHigh)
                .padding(12.dp),
        ) {
            if (points.isEmpty()) return@Canvas
            val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
            val coordinates = points.mapIndexed { index, point ->
                val x = if (points.size <= 1) size.width / 2f else step * index
                val y = size.height - (point.tokens.toFloat() / maxTokens) * size.height
                androidx.compose.ui.geometry.Offset(x, y)
            }
            for (index in 0 until coordinates.lastIndex) {
                drawLine(
                    color = lineColor,
                    start = coordinates[index],
                    end = coordinates[index + 1],
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }
            coordinates.forEach { point ->
                drawCircle(color = fillColor, radius = 12f, center = point)
                drawCircle(color = lineColor, radius = 5f, center = point)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.firstOrNull()?.let {
                Text(it.shortLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            points.lastOrNull()?.let {
                Text(it.shortLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

@Composable
private fun TokenMixPieChart(
    inputTokens: Long,
    outputTokens: Long,
    reasoningTokens: Long,
    modifier: Modifier = Modifier,
) {
    val values = listOf(inputTokens, outputTokens, reasoningTokens)
    val colors = listOf(AetherPrimary, AetherSecondary, AetherTertiary)
    val total = values.sum().coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value / total
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Round),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun SpeedBarChart(points: List<Double>) {
    val maxSpeed = points.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.ifEmpty { listOf(0.0) }.forEach { speed ->
            val fraction = (speed / maxSpeed).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((18 + 92 * fraction).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AetherSecondary.copy(alpha = 0.18f + 0.48f * fraction)),
            )
        }
    }
}

@Composable
private fun TokenMixLegend(
    label: String,
    color: Color,
    tokens: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Text(
            text = formatSettingsTokenCount(tokens),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun HistoryPeakRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun buildSettingsStatisticsSummary(sessions: List<ChatSession>): String {
    val report = buildUsageStatisticsReport(sessions)
    return if (report.turnCount == 0) {
        ""
    } else {
        stringResource(
            R.string.settings_statistics_summary,
            formatSettingsTokenCount(report.totalTokens),
            report.turnCount,
        )
    }
}

private fun buildUsageStatisticsReport(sessions: List<ChatSession>): UsageStatisticsReport {
    val usageMessages = sessions.flatMap { it.messages }.filter { it.usageStatistics != null }
    val stats = usageMessages.mapNotNull { it.usageStatistics }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val daily = (13 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        DailyTokenUsage(
            date = date,
            label = "${date.monthValue}/${date.dayOfMonth}",
            shortLabel = date.dayOfMonth.toString(),
            tokens = stats
                .filter { stat ->
                    val millis = stat.completedAtMillis.takeIf { it > 0L } ?: stat.startedAtMillis
                    millis > 0L && Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == date
                }
                .sumOf { it.totalTokens ?: 0L },
        )
    }
    val speeds = stats.mapNotNull { it.outputTokensPerSecond }
    val latencies = stats.mapNotNull { it.firstTokenLatencyMillis }
    return UsageStatisticsReport(
        totalTokens = stats.sumOf { it.totalTokens ?: 0L },
        inputTokens = stats.sumOf { it.inputTokens ?: 0L },
        outputTokens = stats.sumOf { it.outputTokens ?: 0L },
        reasoningTokens = stats.sumOf { it.reasoningTokens ?: 0L },
        sessionCount = sessions.count { session -> session.messages.any { it.usageStatistics != null } },
        turnCount = stats.size,
        dailyTokenUsage = daily,
        peakDay = daily.maxByOrNull { it.tokens }?.takeIf { it.tokens > 0L },
        largestTurnTokens = stats.mapNotNull { it.totalTokens }.maxOrNull(),
        averageOutputTokensPerSecond = speeds.takeIf { it.isNotEmpty() }?.average(),
        averageFirstTokenLatencyMillis = latencies.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong(),
        recentSpeedSamples = speeds,
    )
}

private fun formatSettingsTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000L -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

private fun formatSettingsTokenRate(tokensPerSecond: Double): String =
    String.format(Locale.US, "%.1f tok/s", tokensPerSecond)

private fun formatSettingsDuration(millis: Long): String =
    if (millis >= 1_000L) {
        String.format(Locale.US, "%.2fs", millis / 1000.0)
    } else {
        "${millis}ms"
    }

private data class UsageStatisticsReport(
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val sessionCount: Int,
    val turnCount: Int,
    val dailyTokenUsage: List<DailyTokenUsage>,
    val peakDay: DailyTokenUsage?,
    val largestTurnTokens: Long?,
    val averageOutputTokensPerSecond: Double?,
    val averageFirstTokenLatencyMillis: Long?,
    val recentSpeedSamples: List<Double>,
)

private data class DailyTokenUsage(
    val date: LocalDate,
    val label: String,
    val shortLabel: String,
    val tokens: Long,
)

// -----------------------------------------------------------------------------
// Providers List Page (Multi-Provider)
// -----------------------------------------------------------------------------

@Composable
private fun GeneralSettingsPage(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SubPageScaffold(title = stringResource(R.string.settings_general), onBack = onBack) {
        SettingsCardGroup {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_language_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLanguage.entries.forEach { option ->
                    SettingsChoiceRow(
                        title = settingsLanguageDisplayName(option),
                        subtitle = if (option == AppLanguage.English) {
                            stringResource(R.string.settings_language_english_interface)
                        } else {
                            stringResource(R.string.settings_language_simplified_chinese_interface)
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
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_theme_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppThemeMode.entries.forEach { option ->
                    SettingsChoiceRow(
                        title = settingsThemeDisplayName(option),
                        subtitle = if (option == AppThemeMode.Light) {
                            stringResource(R.string.settings_light_theme_subtitle)
                        } else {
                            stringResource(R.string.settings_dark_theme_subtitle)
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
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SubPageScaffold(title = stringResource(R.string.settings_general), onBack = onBack) {
        SettingsCardGroup {
            SelectionDropdownField(
                label = stringResource(R.string.settings_language),
                supportingText = stringResource(R.string.settings_language_description),
                selectedLabel = settingsLanguageDisplayName(selectedLanguage),
                options = AppLanguage.entries.map { option ->
                    SelectionOption(
                        key = option.storageValue,
                        title = settingsLanguageDisplayName(option),
                        subtitle = settingsLanguageSubtitle(option),
                        selected = option == selectedLanguage,
                        onClick = { onLanguageSelected(option) },
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            SelectionDropdownField(
                label = stringResource(R.string.settings_theme),
                supportingText = stringResource(R.string.settings_theme_description),
                selectedLabel = settingsThemeDisplayName(selectedThemeMode),
                options = AppThemeMode.entries.map { option ->
                    SelectionOption(
                        key = option.storageValue,
                        title = settingsThemeDisplayName(option),
                        subtitle = settingsThemeSubtitle(option),
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
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_environment_variables),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_environment_variables_description),
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
                    label = stringResource(R.string.settings_variable_name),
                    value = nameValue,
                    onValueChange = {
                        nameValue = it
                        commitRow(name = it.text)
                    },
                )
                ChatGptTextField(
                    label = stringResource(R.string.settings_variable_value),
                    value = valueValue,
                    onValueChange = {
                        valueValue = it
                        commitRow(value = it.text)
                    },
                )
                if (variable.name.isNotBlank() || variable.value.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    SettingsSubtleActionButton(
                        label = stringResource(R.string.settings_remove_variable),
                        onClick = {
                            commitRows(rows.filterIndexed { rowIndex, _ -> rowIndex != index })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            SettingsSubtleActionButton(
                label = stringResource(R.string.settings_add_variable),
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
    SubPageScaffold(
        title = stringResource(R.string.settings_model_providers),
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
                        stringResource(R.string.settings_no_providers_configured),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_add_provider_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = stringResource(R.string.settings_add_provider),
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
                title = stringResource(R.string.settings_default_models),
                subtitle = stringResource(R.string.settings_default_models_subtitle),
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
        title = stringResource(R.string.settings_default_models),
        onBack = onBack,
    ) {
        SettingsCardGroup {
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.settings_default_chat_model),
                subtitle = if (defaultChatModelKey.isBlank()) {
                    automaticChatLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                        ?: stringResource(R.string.settings_automatic_model)
                } else {
                    modelOptions.findModelOption(defaultChatModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultChatModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.settings_default_title_model),
                subtitle = if (defaultTitleModelKey.isBlank()) {
                    automaticTitleLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                        ?: stringResource(R.string.settings_automatic_model)
                } else {
                    modelOptions.findModelOption(defaultTitleModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultTitleModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.settings_default_naming_model),
                subtitle = if (defaultNamingModelKey.isBlank()) {
                    automaticNamingLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                        ?: stringResource(R.string.settings_automatic_model)
                } else {
                    modelOptions.findModelOption(defaultNamingModelKey)?.fullLabel.orEmpty()
                },
                onClick = onOpenDefaultNamingModel,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.settings_default_compacting_model),
                subtitle = if (defaultCompactingModelKey.isBlank()) {
                    automaticCompactingLabel?.let { stringResource(R.string.settings_automatic_model_with_name, it) }
                        ?: stringResource(R.string.settings_automatic_model)
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
                text = stringResource(R.string.settings_provider_models_enabled_count, enabledModels.size, availableModels.size),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = AetherOnSurfaceVariant,
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_remove),
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
    val selectedOption = options.findModelOption(selectedKey)
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val trimmedSearchQuery = searchQuery.text.trim()
    val filteredOptions = remember(options, trimmedSearchQuery) {
        if (trimmedSearchQuery.isBlank()) {
            options
        } else {
            val needle = trimmedSearchQuery.lowercase()
            options.filter { option ->
                option.fullLabel.contains(needle, ignoreCase = true) ||
                    option.modelId.contains(needle, ignoreCase = true) ||
                    option.providerName.contains(needle, ignoreCase = true) ||
                    option.providerId.contains(needle, ignoreCase = true) ||
                    option.providerType.displayName.contains(needle, ignoreCase = true)
            }
        }
    }

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
                        text = stringResource(R.string.settings_no_enabled_models_available),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_enable_provider_model_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
            return@SubPageScaffold
        }

        SettingsCardGroup {
            ModelSelectionSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
            )
        }
        Spacer(Modifier.height(12.dp))

        SettingsCardGroup {
            ModelSelectionListRow(
                title = automaticLabel,
                subtitle = automaticSubtitle,
                selected = selectedOption == null,
                onClick = { onSelected("") },
            )
            filteredOptions.forEach { option ->
                CardDivider()
                ModelSelectionListRow(
                    title = option.fullLabel,
                    subtitle = option.providerName,
                    selected = option.key == selectedOption?.key,
                    onClick = { onSelected(option.key) },
                )
            }
            if (filteredOptions.isEmpty()) {
                CardDivider()
                Text(
                    text = stringResource(R.string.settings_no_models_match_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .settingsBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(AetherPrimary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_models),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
            },
        )
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
    val isNew = existingConfig == null
    val formState = rememberProviderFormState(existingConfig)

    SubPageScaffold(
        title = if (isNew) stringResource(R.string.settings_add_provider) else stringResource(R.string.settings_edit_provider),
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
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        SettingsCardGroup {
            ChatGptTextField(
                label = stringResource(R.string.settings_custom_instructions),
                value = systemPromptValue,
                onValueChange = onSystemPromptChanged,
                minLines = 8,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_custom_instructions_variables_hint),
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
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        Text(
            text = stringResource(R.string.settings_multitasking),
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
                    title = stringResource(R.string.settings_keep_tasks_running_background),
                    subtitle = stringResource(R.string.settings_keep_tasks_running_background_subtitle),
                    checked = keepTasksRunningInBackground,
                    onCheckedChange = onKeepTasksRunningInBackgroundChanged,
                )
                Spacer(Modifier.height(4.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_notify_background_tasks_finish),
                    subtitle = stringResource(R.string.settings_notify_background_tasks_finish_subtitle),
                    checked = notifyOnTaskCompletion,
                    onCheckedChange = onNotifyOnTaskCompletionChanged,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_reconnect),
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            ChatGptTextField(
                label = stringResource(R.string.settings_reconnect_after_idle_seconds),
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
            text = stringResource(R.string.settings_reconnect_after_idle_description),
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
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = onBack,
    ) {
        SettingsCardGroup {
            ChatGptTextField(
                label = stringResource(R.string.settings_tavily_api_key),
                value = tavilyApiKeyValue,
                onValueChange = onTavilyApiKeyChanged,
            )
            CardDivider()
            ChatGptTextField(
                label = stringResource(R.string.settings_tavily_base_url),
                value = tavilyBaseUrlValue,
                onValueChange = onTavilyBaseUrlChanged,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_web_tools_description),
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
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = stringResource(R.string.settings_skills_description),
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
                        stringResource(R.string.settings_no_skills_installed),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_import_skills_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = stringResource(R.string.settings_add_skill),
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
                    contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_remove),
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
            DetailLine(stringResource(R.string.settings_skill_id), skill.id)
            DetailLine(stringResource(R.string.settings_skill_files), "${skill.resourceEntries.size}")
            DetailLine(stringResource(R.string.settings_skill_allowed_tools), skill.allowedTools.ifEmpty { listOf(stringResource(R.string.settings_any)) }.joinToString(", "))
            if (skill.compatibility.isNotBlank()) {
                DetailLine(stringResource(R.string.settings_skill_compatibility), skill.compatibility)
            }
            if (skill.source.label.isNotBlank()) {
                DetailLine(stringResource(R.string.settings_skill_source), skill.source.label)
            }
            DetailLine(stringResource(R.string.settings_skill_path), skill.skillRootPath)
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

    val tabOptions = listOf(
        stringResource(R.string.settings_skill_source_folder),
        "Zip",
        "URL",
    )

    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_add_skill_description),
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
                            stringResource(R.string.settings_select_skill_folder_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SettingsActionButton(
                            label = stringResource(R.string.settings_choose_folder),
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
                            stringResource(R.string.settings_select_skill_zip_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SettingsActionButton(
                            label = stringResource(R.string.settings_choose_zip),
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
                        label = stringResource(R.string.settings_remote_skill_url),
                        value = skillUrlValue,
                        onValueChange = { skillUrlValue = it },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_remote_skill_url_description),
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
                        Text(stringResource(R.string.settings_installing), color = AetherOnSurfaceVariant)
                    }
                } else {
                    SettingsActionButton(
                        label = stringResource(R.string.settings_install_from_url),
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
    var testResultText by rememberSaveable { mutableStateOf("") }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = stringResource(R.string.settings_mcp_servers_description),
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
                        stringResource(R.string.settings_no_mcp_servers),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_add_mcp_server_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = stringResource(R.string.settings_add_server),
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
                    contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = AetherOnSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_remove),
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
                    label = stringResource(R.string.settings_mcp_tools),
                    onClick = { onTest(McpServerTestOperation.ListTools) },
                    modifier = Modifier.weight(1f),
                )
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_mcp_resources),
                    onClick = { onTest(McpServerTestOperation.ListResources) },
                    modifier = Modifier.weight(1f),
                )
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_mcp_prompts),
                    onClick = { onTest(McpServerTestOperation.ListPrompts) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            DetailLine(stringResource(R.string.settings_server_id), server.id)
            DetailLine(stringResource(R.string.settings_quick_action), server.quickActionLabel())
            DetailLine(stringResource(R.string.settings_transport), server.transport.transportType.storageValue.uppercase())
            when (val transport = server.transport) {
                is com.zhousl.aether.data.McpTransportConfig.StreamableHttp -> {
                    DetailLine("URL", transport.url)
                    DetailLine(stringResource(R.string.settings_headers), transport.headers.size.toString())
                }

                is com.zhousl.aether.data.McpTransportConfig.StdIo -> {
                    DetailLine(stringResource(R.string.settings_command), transport.command)
                    if (transport.workingDirectory.isNotBlank()) {
                        DetailLine(stringResource(R.string.settings_working_dir), transport.workingDirectory)
                    }
                    DetailLine(stringResource(R.string.settings_environment), transport.environment.size.toString())
                }
            }
            DetailLine(stringResource(R.string.settings_connect_timeout), "${server.connectTimeoutMillis} ms")
            DetailLine(stringResource(R.string.settings_request_timeout), "${server.requestTimeoutMillis} ms")
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
    SubPageScaffold(
        title = stringResource(R.string.settings_scheduled_tasks),
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        onTrailingAction = onAddNew,
    ) {
        Text(
            text = stringResource(R.string.settings_scheduled_tasks_description),
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
                        text = stringResource(R.string.settings_no_scheduled_tasks),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_create_scheduled_task_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsActionButton(
                        label = stringResource(R.string.settings_add_task),
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
                    contentDescription = stringResource(R.string.action_edit),
                    tint = AetherOnSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
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
                        ?.let { stringResource(R.string.settings_next_run, it) }
                        ?: stringResource(R.string.settings_no_next_run_scheduled),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Text(
                    text = if (task.createdBy == ScheduledTaskCreator.Agent) {
                        stringResource(R.string.settings_created_by_agent)
                    } else {
                        stringResource(R.string.settings_created_manually)
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
            stringResource(R.string.settings_add_scheduled_task)
        } else {
            stringResource(R.string.settings_edit_scheduled_task)
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
                label = stringResource(R.string.settings_name),
                value = nameValue,
                onValueChange = { nameValue = it },
            )
            CardDivider()
            ChatGptTextField(
                label = stringResource(R.string.settings_prompt),
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
                    title = stringResource(R.string.settings_enabled),
                    subtitle = stringResource(R.string.settings_disabled_tasks_saved_subtitle),
                    checked = enabledValue,
                    onCheckedChange = { enabledValue = it },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                stringResource(R.string.settings_schedule_interval),
                stringResource(R.string.settings_schedule_daily),
                stringResource(R.string.settings_schedule_weekly),
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
                        label = stringResource(R.string.settings_every_n_minutes),
                        value = intervalMinutesValue,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { intervalMinutesValue = it.copy(text = it.text.filter(Char::isDigit)) },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = stringResource(R.string.settings_active_start_time),
                        value = activeStartValue,
                        onValueChange = { activeStartValue = it },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = stringResource(R.string.settings_active_end_time),
                        value = activeEndValue,
                        onValueChange = { activeEndValue = it },
                    )
                }
                1 -> ChatGptTextField(
                    label = stringResource(R.string.settings_times_comma_separated),
                    value = dailyTimesValue,
                    onValueChange = { dailyTimesValue = it },
                )
                else -> {
                    ChatGptTextField(
                        label = stringResource(R.string.settings_days_of_week_input),
                        value = weeklyDaysValue,
                        onValueChange = { weeklyDaysValue = it },
                    )
                    CardDivider()
                    ChatGptTextField(
                        label = stringResource(R.string.settings_time_hhmm),
                        value = weeklyTimeValue,
                        onValueChange = { weeklyTimeValue = it },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_interval_window_hint),
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

    val tabOptions = listOf("HTTP", stringResource(R.string.settings_stdio))

    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = if (isEditing) {
                stringResource(R.string.settings_update_mcp_server_description)
            } else {
                stringResource(R.string.settings_add_mcp_server_page_description)
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
                    ChatGptTextField(stringResource(R.string.settings_server_name), httpServerNameValue) { httpServerNameValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_server_url), httpServerUrlValue) { httpServerUrlValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_headers), httpHeadersValue, minLines = 2) { httpHeadersValue = it }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_optional_headers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SettingsActionButton(
                    label = if (isEditing) stringResource(R.string.settings_save_http_server) else stringResource(R.string.settings_add_http_server),
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
                    ChatGptTextField(stringResource(R.string.settings_server_name), stdioServerNameValue) { stdioServerNameValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_command), stdioCommandValue, minLines = 2) { stdioCommandValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_arguments), stdioArgumentsValue, minLines = 2) { stdioArgumentsValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_working_directory), stdioWorkingDirectoryValue) { stdioWorkingDirectoryValue = it }
                    CardDivider()
                    ChatGptTextField(stringResource(R.string.settings_environment), stdioEnvValue, minLines = 2) { stdioEnvValue = it }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_optional_environment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SettingsCardGroup {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_runtime_environment),
                            style = MaterialTheme.typography.labelLarge,
                            color = AetherOnSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_runtime_environment_default_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_default_runtime),
                            subtitle = stringResource(R.string.settings_default_runtime_help),
                            selected = stdioRuntimeEnvironment == null,
                            onClick = { stdioRuntimeEnvironment = null },
                        )
                        SettingsChoiceRow(
                            title = "Termux",
                            subtitle = stringResource(R.string.settings_runtime_termux_stdio_subtitle),
                            selected = stdioRuntimeEnvironment == LocalRuntimeId.Termux,
                            onClick = { stdioRuntimeEnvironment = LocalRuntimeId.Termux },
                        )
                        SettingsChoiceRow(
                            title = "Alpine",
                            subtitle = stringResource(R.string.settings_runtime_alpine_stdio_subtitle),
                            selected = stdioRuntimeEnvironment == LocalRuntimeId.Alpine,
                            onClick = { stdioRuntimeEnvironment = LocalRuntimeId.Alpine },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SettingsActionButton(
                    label = if (isEditing) stringResource(R.string.settings_save_stdio_server) else stringResource(R.string.settings_add_stdio_server),
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
            title = stringResource(R.string.settings_termux_already_configured_title),
            body = stringResource(R.string.settings_termux_already_configured_body),
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
            text = stringResource(R.string.settings_termux_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        WorkspaceModeSettingsSection(
            selectedWorkspaceMode = selectedWorkspaceMode,
            onWorkspaceModeSelected = onWorkspaceModeSelected,
        )

        Spacer(Modifier.height(16.dp))

        TermuxLiveOutputSettingsSection(
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
                title = stringResource(R.string.settings_root_automatic_setup),
                rootSetupState = rootSetupState,
                body = rootSetupSettingsBody(rootSetupState),
                onConfigureWithRoot = ::requestRootSetup,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (termuxSetupState.isReady) {
            SettingsCardGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_termux_connected), style = MaterialTheme.typography.labelLarge, color = AetherOnSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_termux_connected_description),
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
    liveOutputEnabled: Boolean,
    onLiveOutputEnabledChanged: (Boolean) -> Unit,
) {
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_live_command_output),
                subtitle = stringResource(R.string.settings_live_command_output_description),
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
    onSetDefault: () -> Unit,
    onOpenTerminal: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        trailingIcon = Icons.Rounded.Terminal,
        trailingEnabled = setupState.isReady,
        trailingContentDescription = stringResource(R.string.settings_open_terminal),
        onTrailingAction = onOpenTerminal,
    ) {
        Text(
            text = stringResource(R.string.settings_alpine_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_runtime_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = alpineSetupStatusText(setupState),
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
                            LocalRuntimeIssue.Ready -> stringResource(R.string.settings_ready)
                            else -> stringResource(R.string.settings_initialize)
                        },
                        onClick = onInitialize,
                        modifier = Modifier.weight(1f),
                        enabled = !setupState.isReady,
                    )
                    SettingsSubtleActionButton(
                        label = stringResource(R.string.common_refresh),
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_reset_alpine_data),
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (setupState.isReady && !isDefaultRuntime) {
                    Spacer(Modifier.height(10.dp))
                    SettingsActionButton(
                        label = stringResource(R.string.settings_use_as_default_runtime),
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
                    text = stringResource(R.string.settings_environment_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_environment_presets_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AlpineProfileRow(
                    title = stringResource(R.string.settings_python_environment),
                    subtitle = "python3, pip, virtualenv",
                    profileState = packageProfiles["python"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("python") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = stringResource(R.string.settings_node_environment),
                    subtitle = "nodejs, npm",
                    profileState = packageProfiles["node"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("node") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = stringResource(R.string.settings_git_ripgrep_tools),
                    subtitle = "git, ripgrep",
                    profileState = packageProfiles["git_search"],
                    enabled = setupState.isReady,
                    onInstall = { onInstallPackageProfile("git_search") },
                )
                CardDivider()
                AlpineProfileRow(
                    title = stringResource(R.string.settings_ssh_tools),
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
                isInstalling -> stringResource(R.string.settings_installing)
                profileState?.installed == true -> stringResource(R.string.settings_installed)
                else -> stringResource(R.string.common_install)
            },
            onClick = onInstall,
            enabled = enabled && profileState?.installed != true && !isInstalling,
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
    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_runtime_defaults_description),
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
                        stringResource(R.string.settings_runtime_termux_ready_subtitle)
                    } else {
                        stringResource(R.string.settings_runtime_termux_unavailable_subtitle)
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
                        stringResource(R.string.settings_runtime_alpine_ready_subtitle)
                    } else {
                        stringResource(R.string.settings_runtime_alpine_unavailable_subtitle)
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

@Composable
private fun alpineSetupStatusText(
    setupState: LocalRuntimeSetupState,
): String = when (setupState.issue) {
    LocalRuntimeIssue.Ready -> stringResource(R.string.settings_alpine_status_ready)
    LocalRuntimeIssue.NotConfigured,
    LocalRuntimeIssue.NotInstalled -> stringResource(R.string.settings_alpine_status_not_installed)
    LocalRuntimeIssue.UnsupportedAbi -> stringResource(R.string.settings_alpine_status_unsupported_abi)
    LocalRuntimeIssue.MissingAssets -> stringResource(R.string.settings_alpine_status_missing_assets)
    LocalRuntimeIssue.Failed -> stringResource(R.string.settings_alpine_status_failed)
    LocalRuntimeIssue.PermissionMissing,
    LocalRuntimeIssue.ExternalAppsDisabled,
    LocalRuntimeIssue.DispatchFailed -> stringResource(R.string.settings_alpine_status_not_ready)
}

@Composable
private fun WorkspaceModeSettingsSection(
    selectedWorkspaceMode: AgentWorkspaceMode,
    onWorkspaceModeSelected: (AgentWorkspaceMode) -> Unit,
) {
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_workspace_mode),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_workspace_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_single_workspace),
                    subtitle = stringResource(R.string.settings_single_workspace_description),
                    selected = selectedWorkspaceMode == AgentWorkspaceMode.Shared,
                    onClick = { onWorkspaceModeSelected(AgentWorkspaceMode.Shared) },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_independent_workspaces),
                    subtitle = stringResource(R.string.settings_independent_workspaces_description),
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
                        text = stringResource(R.string.settings_agent_mode_unavailable),
                        style = MaterialTheme.typography.labelLarge,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_agent_mode_unavailable_body),
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
            title = stringResource(R.string.settings_agent_mode_already_configured),
            body = if (agentModeAuthorizationMethod == AgentModeAuthorizationMethod.Root) {
                stringResource(R.string.settings_agent_mode_root_already_configured_body)
            } else {
                stringResource(R.string.settings_agent_mode_already_configured_body)
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
            text = stringResource(R.string.settings_agent_mode_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_agent_mode_authorization),
                    subtitle = stringResource(R.string.settings_agent_mode_authorization_subtitle),
                    checked = agentModeAuthorizationEnabled,
                    onCheckedChange = onAgentModeAuthorizationEnabledChanged,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_authorization_method),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentModeAuthorizationMethod.entries.forEach { method ->
                        SettingsChoiceRow(
                            title = method.displayName,
                            subtitle = when (method) {
                                AgentModeAuthorizationMethod.Shizuku -> stringResource(R.string.settings_agent_mode_method_shizuku_subtitle)
                                AgentModeAuthorizationMethod.Root -> stringResource(R.string.settings_agent_mode_method_root_subtitle)
                            },
                            selected = agentModeAuthorizationMethod == method,
                            onClick = { onAgentModeAuthorizationMethodChanged(method) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_agent_mode_method_description),
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
                    title = stringResource(R.string.settings_root_automatic_setup),
                    rootSetupState = rootSetupState,
                    body = stringResource(R.string.settings_agent_mode_root_setup_body),
                    onConfigureWithRoot = ::requestRootSetup,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_virtual_display),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (agentModeDisplayState.isActive) {
                        stringResource(
                            R.string.settings_agent_mode_display_active,
                            agentModeDisplayState.displayId ?: "-",
                            agentModeDisplayState.width,
                            agentModeDisplayState.height,
                        )
                    } else {
                        stringResource(R.string.settings_agent_mode_no_virtual_display)
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
                    text = stringResource(R.string.settings_visible_displays),
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (agentModeDisplayState.displays.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_visible_displays),
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
                                        text = stringResource(R.string.agent_mode_display_id, display.displayId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AetherOnSurface,
                                    )
                                    Text(
                                        text = listOf(
                                            display.name.ifBlank { stringResource(R.string.settings_unnamed_display) },
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
                    label = stringResource(R.string.settings_stop_virtual_display),
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
                    label = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = stringResource(R.string.action_continue),
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
        title = stringResource(R.string.settings_root_automatic_setup),
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
                    text = rootSetupProgressTitle(rootSetupState.issue),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = rootSetupProgressBody(rootSetupState.issue),
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
                    title = stringResource(R.string.settings_root_setup_step_root_access),
                    subtitle = stringResource(R.string.settings_root_setup_step_root_access_subtitle),
                    status = rootStepStatus,
                )
                RootSetupProgressStep(
                    title = stringResource(R.string.settings_root_setup_step_termux_access),
                    subtitle = stringResource(R.string.settings_root_setup_step_termux_access_subtitle),
                    status = termuxStepStatus,
                )
                RootSetupProgressStep(
                    title = stringResource(R.string.settings_root_setup_step_agent_mode),
                    subtitle = stringResource(R.string.settings_root_setup_step_agent_mode_subtitle),
                    status = agentModeStepStatus,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            rootSetupState.isRunning -> SettingsSubtleActionButton(
                label = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )

            rootSetupState.isReady -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_run_again),
                    onClick = onRunRootSetup,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = stringResource(R.string.action_done),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsSubtleActionButton(
                    label = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                SettingsActionButton(
                    label = stringResource(R.string.action_try_again),
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

@Composable
private fun rootSetupProgressTitle(
    issue: RootSetupIssue,
): String = when (issue) {
RootSetupIssue.Running -> stringResource(R.string.settings_root_setup_configuring)
    RootSetupIssue.Ready -> stringResource(R.string.settings_root_setup_completed)
    RootSetupIssue.Available -> stringResource(R.string.settings_root_setup_ready_to_start)
    RootSetupIssue.Unavailable -> stringResource(R.string.settings_root_setup_root_unavailable)
    RootSetupIssue.PermissionDenied -> stringResource(R.string.settings_root_setup_permission_denied)
    RootSetupIssue.TermuxNotInstalled -> stringResource(R.string.settings_root_setup_termux_required)
    RootSetupIssue.Failed -> stringResource(R.string.settings_root_setup_failed)
    RootSetupIssue.Unknown -> stringResource(R.string.settings_root_setup_preparing)
}

@Composable
private fun rootSetupProgressBody(
    issue: RootSetupIssue,
): String = when (issue) {
RootSetupIssue.Running -> stringResource(R.string.settings_root_setup_progress_body_running)
    RootSetupIssue.Ready -> stringResource(R.string.settings_root_setup_progress_body_ready)
    RootSetupIssue.Available,
    RootSetupIssue.Unknown -> stringResource(R.string.settings_root_setup_progress_body_available)
    RootSetupIssue.Unavailable -> stringResource(R.string.settings_root_setup_progress_body_unavailable)
    RootSetupIssue.PermissionDenied -> stringResource(R.string.settings_root_setup_progress_body_permission_denied)
    RootSetupIssue.TermuxNotInstalled -> stringResource(R.string.settings_root_setup_progress_body_termux_not_installed)
    RootSetupIssue.Failed -> stringResource(R.string.settings_root_setup_progress_body_failed)
}
@Composable
private fun RootSetupSettingsSection(
    title: String,
    rootSetupState: RootSetupState,
    body: String,
    onConfigureWithRoot: () -> Unit,
) {
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
                RootSetupIssue.Running -> stringResource(R.string.settings_configuring)
                RootSetupIssue.Ready -> stringResource(R.string.settings_run_root_setup_again)
                RootSetupIssue.Unavailable -> stringResource(R.string.settings_try_root_setup)
                else -> stringResource(R.string.settings_configure_with_root)
            },
            onClick = onConfigureWithRoot,
            modifier = Modifier.fillMaxWidth(),
            enabled = !rootSetupState.isRunning,
        )
    }
}

@Composable
private fun rootSetupSettingsBody(
    rootSetupState: RootSetupState,
): String = when (rootSetupState.issue) {
    RootSetupIssue.Ready -> stringResource(R.string.settings_root_setup_body_ready)

    RootSetupIssue.Available,
    RootSetupIssue.Running -> stringResource(R.string.settings_root_setup_body_available)

    RootSetupIssue.Unavailable,
    RootSetupIssue.Unknown -> stringResource(R.string.settings_root_setup_body_unavailable)

    RootSetupIssue.PermissionDenied,
    RootSetupIssue.TermuxNotInstalled,
    RootSetupIssue.Failed -> stringResource(R.string.settings_root_setup_body_failed)
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
    val statusText = when {
        enabled && method == AgentModeAuthorizationMethod.Root -> when (state.issue) {
            AgentModeAuthorizationIssue.Ready -> stringResource(R.string.settings_root_authorization_ready)
            AgentModeAuthorizationIssue.RootUnavailable -> stringResource(R.string.settings_root_authorization_no_su)
            AgentModeAuthorizationIssue.RootPermissionMissing -> stringResource(R.string.settings_root_authorization_grant_su)
            AgentModeAuthorizationIssue.RootPermissionDenied -> stringResource(R.string.settings_root_authorization_denied)
            AgentModeAuthorizationIssue.Error -> state.detail.ifBlank {
                stringResource(R.string.settings_root_authorization_inspect_failed)
            }
            else -> state.detail.ifBlank {
                stringResource(R.string.settings_root_authorization_refresh_first)
            }
        }
        !enabled -> stringResource(R.string.settings_agent_mode_authorization_off)
        method == AgentModeAuthorizationMethod.Root -> stringResource(R.string.settings_agent_mode_root_requests_su)
        else -> when (state.issue) {
            AgentModeAuthorizationIssue.Ready -> stringResource(R.string.settings_shizuku_authorized)
            AgentModeAuthorizationIssue.ShizukuNotInstalled -> stringResource(R.string.settings_shizuku_install_first)
            AgentModeAuthorizationIssue.ShizukuNotRunning -> stringResource(R.string.settings_shizuku_start_first)
            AgentModeAuthorizationIssue.ShizukuPermissionMissing -> stringResource(R.string.settings_shizuku_grant_permission)
            AgentModeAuthorizationIssue.ShizukuPermissionDenied -> stringResource(R.string.settings_shizuku_permission_denied)
            AgentModeAuthorizationIssue.Disabled -> stringResource(R.string.settings_shizuku_save_then_refresh)
            AgentModeAuthorizationIssue.RootUnavailable,
            AgentModeAuthorizationIssue.RootPermissionMissing,
            AgentModeAuthorizationIssue.RootPermissionDenied -> state.detail.ifBlank {
                stringResource(R.string.settings_switch_to_root_to_inspect)
            }
            AgentModeAuthorizationIssue.Error -> state.detail.ifBlank {
                stringResource(R.string.settings_shizuku_inspect_failed)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.settings_authorization_status),
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
                    label = stringResource(R.string.settings_install_shizuku),
                    onClick = onInstallShizuku,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.issue == AgentModeAuthorizationIssue.ShizukuNotRunning -> {
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_open_shizuku),
                    onClick = onOpenShizuku,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.issue == AgentModeAuthorizationIssue.ShizukuPermissionMissing ||
                state.issue == AgentModeAuthorizationIssue.ShizukuPermissionDenied -> {
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_grant_access),
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
    SubPageScaffold(title = title, onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_developer_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_app_data),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_app_data_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_import_app_data),
                    onClick = onImportAppData,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_export_app_data),
                    onClick = onExportAppData,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_logs),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_logs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_export_logs),
                    onClick = onExportLogs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_update_testing),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_update_testing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_force_update_prompt),
                    onClick = onForceUpdateCheckForTesting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_termux_readiness_override),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_termux_readiness_override_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_treat_termux_as_ready),
                    subtitle = stringResource(R.string.settings_termux_ready_toggle_subtitle),
                    checked = termuxReadyForTesting,
                    onCheckedChange = onTermuxReadyForTestingChanged,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_replay_follow_up_tour),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_replay_follow_up_tour_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SettingsSubtleActionButton(
                    label = stringResource(R.string.settings_replay_second_part),
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
    val updateSubtitle = when {
        appUpdate.isDownloading -> appUpdate.downloadProgress?.let { progress ->
            stringResource(R.string.settings_update_downloading_percent, (progress * 100).toInt())
        } ?: stringResource(R.string.settings_update_downloading)
        appUpdate.isChecking -> stringResource(R.string.settings_update_checking_github_releases)
        appUpdate.availableRelease != null -> stringResource(R.string.settings_update_available, appUpdate.availableRelease.versionName)
        else -> stringResource(R.string.settings_update_check_newer_apk)
    }
    val releaseLabel = stringResource(R.string.settings_release_summary, BuildConfig.VERSION_NAME)
    SubPageScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.aether_mark),
                contentDescription = stringResource(R.string.settings_aether_logo),
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
            AboutInfoRow(label = stringResource(R.string.settings_author), value = "Zhou-Shilin")
            CardDivider()
            AboutInfoRow(label = stringResource(R.string.settings_version), value = releaseLabel)
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.settings_check_for_updates),
                subtitle = updateSubtitle,
                onClick = onCheckForUpdates,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.settings_website),
                subtitle = AetherWebsiteUrl.removePrefix("https://"),
                onClick = onOpenWebsite,
            )
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.settings_privacy_policy),
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
                    stringResource(R.string.settings_download_and_install)
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
            contentDescription = stringResource(R.string.common_back),
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
