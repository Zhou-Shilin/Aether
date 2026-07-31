package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AetherAppStoreFallbackUrl
import com.zhousl.aether.data.AetherPrivacyPolicyUrl
import com.zhousl.aether.data.AetherWebsiteUrl
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.SharedAppDataRestoreResult
import com.zhousl.aether.data.SharedAppUpdateService
import com.zhousl.aether.data.SharedAppUpdateStatus
import com.zhousl.aether.data.SharedDailyTokenUsage
import com.zhousl.aether.data.SharedDiagnosticLogger
import com.zhousl.aether.data.SharedSpeedSample
import com.zhousl.aether.data.SharedUsageStatisticsReport
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.normalizeOldCommandHistoryRetentionHours
import com.zhousl.aether.data.normalizeTavilyBaseUrl
import com.zhousl.aether.platform.PlatformCapabilities
import com.zhousl.aether.platform.PlatformServices
import com.zhousl.aether.platform.platformAppVersion
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun Throwable.sharedUserFacingMessage(): String =
    message?.trim().takeUnless { it.isNullOrBlank() }
        ?: this::class.simpleName?.takeIf(String::isNotBlank)
        ?: "Unknown error"

private suspend fun <T> runSharedSettingsCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    Result.failure(failure)
}

private val SharedStatisticsInputColor = Color(0xFF5D7CFF)
private val SharedStatisticsOutputColor = Color(0xFF7B68EE)
private val SharedStatisticsReasoningColor = Color(0xFFA9B8FF)
private val SharedStatisticsNeutralChartColor = Color(0xFFDCE4FF)

@Composable
internal fun SharedPersonalizationSettingsDetail(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var systemPrompt by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    ReportSharedSettingsUnsavedChanges(systemPrompt != settings.systemPrompt)
    fun persistAndBack() {
        val updated = settings.copy(systemPrompt = systemPrompt)
        if (updated != settings) onSave(updated)
        onBack()
    }
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_personalization),
        onBack = ::persistAndBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = ::persistAndBack,
    ) {
        SettingsCardGroup {
            SharedSettingsTextField(
                label = stringResource(Res.string.settings_custom_instructions),
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                minLines = 8,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_custom_instructions_variables_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun SharedWebToolsSettingsDetail(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var apiKey by remember(settings.tavilyApiKey) { mutableStateOf(settings.tavilyApiKey) }
    var baseUrl by remember(settings.tavilyBaseUrl) { mutableStateOf(settings.tavilyBaseUrl) }
    ReportSharedSettingsUnsavedChanges(
        apiKey != settings.tavilyApiKey || normalizeTavilyBaseUrl(baseUrl) != settings.tavilyBaseUrl,
    )
    fun persistAndBack() {
        val updated = settings.copy(
            tavilyApiKey = apiKey,
            tavilyBaseUrl = normalizeTavilyBaseUrl(baseUrl),
        )
        if (updated != settings) onSave(updated)
        onBack()
    }
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_web_tools),
        onBack = ::persistAndBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = ::persistAndBack,
    ) {
        SettingsCardGroup {
            SharedSettingsTextField(
                label = stringResource(Res.string.settings_tavily_api_key),
                value = apiKey,
                onValueChange = { apiKey = it },
                keyboardType = KeyboardType.Password,
                secret = true,
            )
            CardDivider()
            SharedSettingsTextField(
                label = stringResource(Res.string.settings_tavily_base_url),
                value = baseUrl,
                onValueChange = { baseUrl = it },
                keyboardType = KeyboardType.Uri,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_web_tools_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun SharedReliabilitySettingsDetail(
    settings: AppSettings,
    capabilities: PlatformCapabilities,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var reconnectSeconds by remember(settings.llmInactivityReconnectTimeoutSeconds) {
        mutableStateOf(settings.llmInactivityReconnectTimeoutSeconds.toString())
    }
    var keepBackground by remember(settings.keepTasksRunningInBackground) {
        mutableStateOf(settings.keepTasksRunningInBackground)
    }
    var notify by remember(settings.notifyOnTaskCompletion) {
        mutableStateOf(settings.notifyOnTaskCompletion)
    }
    fun currentSettings(): AppSettings = settings.copy(
        llmInactivityReconnectTimeoutSeconds =
            normalizeLlmInactivityReconnectTimeoutSeconds(reconnectSeconds.toIntOrNull()),
        keepTasksRunningInBackground =
            if (capabilities.persistentBackground) keepBackground else settings.keepTasksRunningInBackground,
        notifyOnTaskCompletion =
            if (capabilities.persistentBackground) notify else settings.notifyOnTaskCompletion,
    )
    ReportSharedSettingsUnsavedChanges(currentSettings() != settings)
    fun persistAndBack() {
        val updated = currentSettings()
        if (updated != settings) onSave(updated)
        onBack()
    }
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_reliability),
        onBack = ::persistAndBack,
        trailingIcon = Icons.Rounded.Check,
        onTrailingAction = ::persistAndBack,
    ) {
        if (capabilities.persistentBackground) {
            Text(
                stringResource(Res.string.settings_multitasking),
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
                    SharedSettingsToggle(
                        title = stringResource(Res.string.settings_keep_tasks_running_background),
                        subtitle = stringResource(Res.string.settings_keep_tasks_running_background_subtitle),
                        checked = keepBackground,
                        onCheckedChange = { keepBackground = it },
                    )
                    Spacer(Modifier.height(4.dp))
                    SharedSettingsToggle(
                        title = stringResource(Res.string.settings_notify_background_tasks_finish),
                        subtitle = stringResource(Res.string.settings_notify_background_tasks_finish_subtitle),
                        checked = notify,
                        onCheckedChange = { notify = it },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            stringResource(Res.string.settings_reconnect),
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCardGroup {
            SharedSettingsTextField(
                label = stringResource(Res.string.settings_reconnect_after_idle_seconds),
                value = reconnectSeconds,
                onValueChange = { reconnectSeconds = it.filter(Char::isDigit) },
                keyboardType = KeyboardType.Number,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_reconnect_after_idle_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun SharedStatisticsSettingsDetail(
    report: SharedUsageStatisticsReport,
    onBack: () -> Unit,
) {
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_statistics),
        onBack = onBack,
    ) {
        val current = report
        SettingsCardGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(Res.string.statistics_overview),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SharedMetricTile(
                        stringResource(Res.string.statistics_total_tokens),
                        formatTokenCount(current.totalTokens),
                        Modifier.weight(1f),
                    )
                    SharedMetricTile(
                        stringResource(Res.string.statistics_sessions),
                        current.sessionCount.toString(),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SharedMetricTile(
                        stringResource(Res.string.statistics_average_speed),
                        current.averageOutputTokensPerSecond?.let(::formatTokenRate)
                            ?: stringResource(Res.string.statistics_unavailable),
                        Modifier.weight(1f),
                    )
                    SharedMetricTile(
                        stringResource(Res.string.statistics_average_latency),
                        current.averageFirstTokenLatencyMillis?.let(::formatDuration)
                            ?: stringResource(Res.string.statistics_unavailable),
                        Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SharedStatisticsChartCard(
            title = stringResource(Res.string.statistics_daily_token_usage),
            subtitle = stringResource(Res.string.statistics_recent_7_days),
        ) {
            SharedTokenBarChart(current.recentDailyTokenUsage.takeLast(7))
        }
        Spacer(Modifier.height(16.dp))
        SharedStatisticsChartCard(
            title = stringResource(Res.string.statistics_recent_token_usage),
            subtitle = stringResource(Res.string.statistics_recent_14_days),
        ) {
            SharedTokenLineChart(current.recentDailyTokenUsage.takeLast(14))
        }
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(Res.string.statistics_historical_token_usage),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                SharedHistoryPeakRow(
                    stringResource(Res.string.statistics_peak_day),
                    current.peakDay?.let { "${it.label} · ${formatTokenCount(it.tokens)}" }
                        ?: stringResource(Res.string.statistics_unavailable),
                )
                SharedHistoryPeakRow(
                    stringResource(Res.string.statistics_largest_turn),
                    current.largestTurnTokens?.let(::formatTokenCount)
                        ?: stringResource(Res.string.statistics_unavailable),
                )
                SharedHistoryPeakRow(
                    stringResource(Res.string.statistics_recorded_turns),
                    current.turnCount.toString(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(Res.string.statistics_token_mix),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    SharedTokenMixPieChart(
                        inputTokens = current.inputTokens,
                        outputTokens = current.outputTokens,
                        reasoningTokens = current.reasoningTokens,
                        modifier = Modifier.size(112.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SharedTokenMixLegend(
                            stringResource(Res.string.statistics_input),
                            SharedStatisticsInputColor,
                            current.inputTokens,
                        )
                        SharedTokenMixLegend(
                            stringResource(Res.string.statistics_output),
                            SharedStatisticsOutputColor,
                            current.outputTokens,
                        )
                        SharedTokenMixLegend(
                            stringResource(Res.string.statistics_reasoning),
                            SharedStatisticsReasoningColor,
                            current.reasoningTokens,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SharedStatisticsChartCard(
            title = stringResource(Res.string.statistics_speed),
            subtitle = stringResource(Res.string.statistics_speed_subtitle),
        ) {
            SharedSpeedBarChart(current.recentSpeedSamples.takeLast(12))
        }
    }
}

@Composable
internal fun SharedDeveloperSettingsDetail(
    settings: AppSettings,
    platformServices: PlatformServices,
    onSave: (AppSettings) -> Unit,
    onImportAppData: suspend (String) -> SharedAppDataRestoreResult,
    onExportAppData: suspend () -> String,
    onReplayFollowUpOnboarding: (() -> Unit)? = null,
    onReplayAlpineSetupPreview: () -> Unit = {},
    onExportLogs: (suspend () -> String)? = null,
    /** Compatibility for callers that still route this action through the old name. */
    onReplayOnboarding: (() -> Unit)? = null,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var autoClean by remember(settings.autoCleanOldCommandHistory) {
        mutableStateOf(settings.autoCleanOldCommandHistory)
    }
    var retention by remember(settings.oldCommandHistoryRetentionHours) {
        mutableStateOf(settings.oldCommandHistoryRetentionHours.toString())
    }
    var showExportWarning by remember { mutableStateOf(false) }
    val exportFileName = stringResource(Res.string.settings_app_data_export_filename)
    val replayFollowUp = onReplayFollowUpOnboarding ?: onReplayOnboarding ?: {}
    val importedMessage = stringResource(Res.string.message_app_data_imported)
    val exportedMessage = stringResource(Res.string.message_app_data_exported)
    val exportFailedMessage = stringResource(Res.string.message_app_data_export_failed)
    val logsExportedMessage = stringResource(Res.string.message_logs_exported)
    val logsExportFailedMessage = stringResource(Res.string.message_logs_export_failed)
    val importFailurePlaceholder = "{import_error}"
    val importFailedTemplate = stringResource(
        Res.string.message_app_data_import_failed,
        importFailurePlaceholder,
    )
    fun importFailedMessage(failure: Throwable): String = importFailedTemplate.replace(
        importFailurePlaceholder,
        failure.sharedUserFacingMessage(),
    )
    fun currentSettings(): AppSettings = settings.copy(
        autoCleanOldCommandHistory = autoClean,
        oldCommandHistoryRetentionHours =
            normalizeOldCommandHistoryRetentionHours(retention.toIntOrNull()),
    )
    ReportSharedSettingsUnsavedChanges(currentSettings() != settings)
    fun persistSettings() {
        val updated = currentSettings()
        if (updated != settings) onSave(updated)
    }
    fun persistAndBack() {
        persistSettings()
        onBack()
    }
    fun importSettings() {
        scope.launch {
            val fileResult = runSharedSettingsCatching { platformServices.pickFile() }
            if (fileResult.isFailure) {
                fileResult.exceptionOrNull()?.let { onTransientMessage(importFailedMessage(it)) }
                return@launch
            }
            val file = fileResult.getOrNull() ?: return@launch
            runSharedSettingsCatching { onImportAppData(file.bytes.decodeToString()) }.onSuccess { restored ->
                val imported = restored.persistedSettings.appSettings
                autoClean = imported.autoCleanOldCommandHistory
                retention = imported.oldCommandHistoryRetentionHours.toString()
                onTransientMessage(importedMessage)
            }.onFailure { failure ->
                onTransientMessage(importFailedMessage(failure))
            }
        }
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            containerColor = AetherSurface,
            titleContentColor = AetherOnSurface,
            textContentColor = AetherOnSurfaceVariant,
            title = { Text(stringResource(Res.string.settings_export_app_data_warning_title)) },
            text = { Text(stringResource(Res.string.settings_export_app_data_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportWarning = false
                        scope.launch {
                            val exportResult = runSharedSettingsCatching {
                                val json = onExportAppData()
                                platformServices.exportFile(
                                        name = exportFileName,
                                        mimeType = "application/json",
                                        bytes = json.encodeToByteArray(),
                                    )
                            }
                            when {
                                exportResult.isFailure -> onTransientMessage(exportFailedMessage)
                                exportResult.getOrNull() == true -> onTransientMessage(exportedMessage)
                                exportResult.getOrNull() == false -> onTransientMessage(exportFailedMessage)
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.common_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_developer),
        onBack = ::persistAndBack,
    ) {
        Text(
            text = stringResource(Res.string.settings_developer_description),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        SettingsCardGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.settings_app_data),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_app_data_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_import_app_data),
                    onClick = ::importSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_export_app_data),
                    onClick = { showExportWarning = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsCardGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.settings_replay_alpine_setup_preview),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_replay_alpine_setup_preview_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_replay_setup_preview),
                    onClick = onReplayAlpineSetupPreview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsCardGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.settings_logs),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_logs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_export_logs),
                    onClick = {
                        scope.launch {
                            val exportResult = runSharedSettingsCatching {
                                val text = onExportLogs?.invoke() ?: error("Log export is unavailable.")
                                platformServices.exportFile(
                                    name = "aether-logs.txt",
                                    mimeType = "text/plain",
                                    bytes = text.encodeToByteArray(),
                                )
                            }
                            val didExport = exportResult.getOrNull()
                            SharedDiagnosticLogger.event(
                                category = "export",
                                event = when {
                                    exportResult.isFailure -> "diagnostic_export_failed"
                                    didExport == true -> "diagnostic_export_end"
                                    didExport == false -> "diagnostic_export_failed"
                                    else -> "diagnostic_export_cancelled"
                                },
                                level = if (exportResult.isFailure || didExport == false) "warn" else "info",
                            )
                            when {
                                exportResult.isFailure -> onTransientMessage(logsExportFailedMessage)
                                didExport == true -> onTransientMessage(logsExportedMessage)
                                didExport == false -> onTransientMessage(logsExportFailedMessage)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsCardGroup {
            Column(Modifier.padding(16.dp)) {
                SharedSettingsToggle(
                    title = stringResource(Res.string.settings_old_command_history_retention_hours),
                    subtitle = stringResource(Res.string.settings_old_command_history_retention_hours_description),
                    checked = autoClean,
                    onCheckedChange = { autoClean = it },
                )
                AnimatedVisibility(visible = autoClean) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        SharedSettingsTextField(
                            label = stringResource(Res.string.settings_old_command_history_retention_hours_value),
                            value = retention,
                            onValueChange = { retention = it.filter(Char::isDigit) },
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.settings_old_command_history_retention_hours_value_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsCardGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.settings_replay_follow_up_tour),
                    style = MaterialTheme.typography.labelLarge,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.settings_replay_follow_up_tour_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_replay_second_part),
                    onClick = {
                        persistSettings()
                        replayFollowUp()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun SharedAboutSettingsDetail(
    platformServices: PlatformServices,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val installedVersion = remember { platformAppVersion() }
    val service = remember { SharedAppUpdateService() }
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<SharedAppUpdateStatus?>(null) }
    var checkFailed by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val unableToOpenLinkMessage = stringResource(Res.string.app_unable_to_open_link)
    val upToDateMessage = stringResource(Res.string.message_aether_up_to_date)
    val updateFailurePlaceholder = "{update_error}"
    val updateFailureTemplate = stringResource(
        Res.string.message_update_check_failed,
        updateFailurePlaceholder,
    )
    fun openUrl(url: String) {
        if (!platformServices.openUrl(url)) onTransientMessage(unableToOpenLinkMessage)
    }
    fun openAppStore() {
        openUrl(update?.storeUrl.orEmpty().ifBlank { AetherAppStoreFallbackUrl })
    }
    val releaseLabel = stringResource(Res.string.settings_release_summary, installedVersion)
    val updateSubtitle = when {
        checking -> stringResource(Res.string.settings_app_store_checking)
        checkFailed -> stringResource(Res.string.settings_update_check_failed_short)
        update?.isUpdateAvailable == true ->
            stringResource(Res.string.settings_app_store_update_available, update?.storeVersion.orEmpty())
        update?.isPublished == true -> stringResource(Res.string.settings_app_store_current)
        update != null -> stringResource(Res.string.settings_app_store_unpublished)
        else -> stringResource(Res.string.settings_check_for_updates)
    }
    if (showUpdateDialog && update?.isUpdateAvailable == true) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = AetherSurface,
            titleContentColor = AetherOnSurface,
            textContentColor = AetherOnSurfaceVariant,
            title = { Text(stringResource(Res.string.app_update_available)) },
            text = {
                Text(
                    stringResource(
                        Res.string.settings_app_store_update_available,
                        update?.storeVersion.orEmpty(),
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        openAppStore()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AetherPrimary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(Res.string.settings_open_app_store))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(
                        stringResource(Res.string.common_later),
                        color = AetherOnSurfaceVariant,
                    )
                }
            },
        )
    }
    SharedSettingsDetailScaffold(
        title = stringResource(Res.string.settings_about),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.aether_mark),
                contentDescription = stringResource(Res.string.settings_aether_logo),
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge, color = AetherOnSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                releaseLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        SettingsCardGroup {
            SharedValueRow(
                stringResource(Res.string.settings_author),
                stringResource(Res.string.settings_author_name),
            )
            CardDivider()
            SharedValueRow(stringResource(Res.string.settings_version), releaseLabel)
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Refresh,
                title = stringResource(Res.string.settings_check_for_updates),
                subtitle = updateSubtitle,
                enabled = !checking,
            ) {
                checking = true
                checkFailed = false
                showUpdateDialog = false
                scope.launch {
                    runSharedSettingsCatching { service.check(installedVersion) }
                        .onSuccess { result ->
                            update = result
                            showUpdateDialog = result.isUpdateAvailable
                            if (!result.isUpdateAvailable) onTransientMessage(upToDateMessage)
                        }
                        .onFailure { failure ->
                            checkFailed = true
                            onTransientMessage(
                                updateFailureTemplate.replace(
                                    updateFailurePlaceholder,
                                    failure.sharedUserFacingMessage(),
                                )
                            )
                        }
                    checking = false
                }
            }
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = stringResource(Res.string.settings_website),
                subtitle = AetherWebsiteUrl.removePrefix("https://"),
            ) { openUrl(AetherWebsiteUrl) }
            CardDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Link,
                title = stringResource(Res.string.settings_privacy_policy),
                subtitle = AetherPrivacyPolicyUrl.removePrefix("https://"),
            ) { openUrl(AetherPrivacyPolicyUrl) }
        }
        if (update?.isUpdateAvailable == true) {
            Spacer(Modifier.height(16.dp))
            SharedSettingsActionButton(
                label = stringResource(Res.string.settings_open_app_store),
                onClick = ::openAppStore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SharedSettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    onTrailingAction: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(
            title = title,
            onBack = onBack,
            trailingIcon = trailingIcon,
            trailingContentDescription = title,
            onTrailingAction = onTrailingAction,
        )
    }
}

@Composable
private fun SharedSettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    minLines: Int = 1,
) {
    var passwordVisible by rememberSaveable(label) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().sharedSettingsBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(AetherPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (secret && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            minLines = minLines,
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        field()
                    }
                    if (secret) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) {
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
                }
            },
        )
    }
}

@Composable
private fun SharedSettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurface)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SharedSettingsAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
    }
}

@Composable
private fun SharedSettingsSubtleActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AetherSurface,
            contentColor = AetherOnSurface,
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
private fun SharedStatisticsChartCard(
    title: String,
    subtitle: String,
    chart: @Composable () -> Unit,
) {
    SettingsCardGroup {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AetherOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            chart()
        }
    }
}

@Composable
private fun SharedTokenBarChart(
    points: List<SharedDailyTokenUsage>,
) {
    val maxTokens = points.maxOfOrNull { it.tokens }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(164.dp),
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
                    formatTokenCount(point.tokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((18 + 96 * fraction).dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 6.dp,
                            ),
                        )
                        .background(AetherPrimary.copy(alpha = 0.18f + 0.44f * fraction)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    point.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun SharedTokenLineChart(points: List<SharedDailyTokenUsage>) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val selectedPoint = selectedIndex?.let(points::getOrNull)
    val lineColor = SharedStatisticsInputColor
    val fillColor = SharedStatisticsInputColor.copy(alpha = 0.12f)
    val maxTokens = points.maxOfOrNull { it.tokens }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AetherSurfaceHigh)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val horizontalPadding = 24f
                        val chartWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                        val progress = ((offset.x - horizontalPadding) / chartWidth).coerceIn(0f, 1f)
                        selectedIndex = (progress * (points.size - 1)).roundToInt()
                            .coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            val chartPadding = 12.dp
            val chartWidth = maxWidth - chartPadding * 2
            val chartHeight = 126.dp
            Canvas(
                modifier = Modifier.fillMaxSize().padding(chartPadding),
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
                coordinates.forEachIndexed { index, point ->
                    val selected = selectedIndex == index
                    drawCircle(
                        color = fillColor,
                        radius = if (selected) 17f else 12f,
                        center = point,
                    )
                    drawCircle(
                        color = if (selected) SharedStatisticsOutputColor else lineColor,
                        radius = if (selected) 7f else 5f,
                        center = point,
                    )
                }
            }
            selectedPoint?.let { point ->
                val selectedX = if (points.size <= 1) {
                    maxWidth / 2
                } else {
                    chartPadding + chartWidth * (selectedIndex!!.toFloat() / points.lastIndex)
                }
                val selectedY = chartPadding + chartHeight *
                    (1f - point.tokens.toFloat() / maxTokens)
                val tooltipWidth = 116.dp
                val tooltipX = (selectedX - tooltipWidth / 2)
                    .coerceIn(4.dp, (maxWidth - tooltipWidth - 4.dp).coerceAtLeast(4.dp))
                val tooltipY = (selectedY - 34.dp).coerceAtLeast(4.dp)
                Box(
                    modifier = Modifier
                        .offset(x = tooltipX, y = tooltipY)
                        .width(tooltipWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AetherSurface.copy(alpha = 0.96f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            Res.string.statistics_selected_day_tokens,
                            point.label,
                            formatTokenCount(point.tokens),
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = AetherOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.firstOrNull()?.let {
                Text(it.shortLabel, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant)
            }
            points.lastOrNull()?.let {
                Text(it.shortLabel, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedSpeedBarChart(points: List<SharedSpeedSample>) {
    val visiblePoints = points.takeLast(7)
    if (visiblePoints.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(132.dp)
                .clip(RoundedCornerShape(18.dp)).background(AetherSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.statistics_no_speed_samples),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
        return
    }
    val maxSpeed = visiblePoints.maxOfOrNull { it.tokensPerSecond }?.coerceAtLeast(1.0) ?: 1.0
    Row(
        modifier = Modifier.fillMaxWidth().height(164.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        visiblePoints.forEach { point ->
            val fraction = (point.tokensPerSecond / maxSpeed).toFloat()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    formatTokenRate(point.tokensPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height((18 + 96 * fraction).dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 6.dp,
                            ),
                        )
                        .background(AetherPrimary.copy(alpha = 0.18f + 0.44f * fraction)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    point.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SharedTokenMixPieChart(
    inputTokens: Long,
    outputTokens: Long,
    reasoningTokens: Long,
    modifier: Modifier = Modifier,
) {
    val values = listOf(inputTokens, outputTokens, reasoningTokens)
    val colors = listOf(
        SharedStatisticsInputColor,
        SharedStatisticsOutputColor,
        SharedStatisticsReasoningColor,
    )
    val total = values.sum()
    Canvas(modifier = modifier) {
        if (total <= 0L) {
            drawArc(
                color = SharedStatisticsNeutralChartColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.20f, cap = StrokeCap.Butt),
            )
            return@Canvas
        }
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            if (value <= 0L) return@forEachIndexed
            val sweep = 360f * value / total
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.20f, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun SharedTokenMixLegend(label: String, color: Color, tokens: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Text(
            formatTokenCount(tokens),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun SharedHistoryPeakRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun SharedMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SharedValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
        )
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> formatOneDecimal(tokens, 1_000_000) + "M"
    tokens >= 1_000 -> formatOneDecimal(tokens, 1_000) + "K"
    else -> tokens.toString()
}

private fun formatTokenRate(tokensPerSecond: Double): String {
    val tenths = (tokensPerSecond * 10.0).roundToLong()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)} tok/s"
}

private fun formatDuration(millis: Long): String = if (millis >= 1_000L) {
    val hundredths = (millis / 10.0).roundToLong()
    "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}s"
} else {
    "${millis}ms"
}

private fun formatOneDecimal(value: Long, unit: Long): String {
    val tenths = (value.toDouble() / unit.toDouble() * 10.0).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}
