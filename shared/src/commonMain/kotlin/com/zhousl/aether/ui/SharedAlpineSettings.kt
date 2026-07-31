package com.zhousl.aether.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.LocalRuntimeId
import com.zhousl.aether.data.PackageProfileState
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeProcessSignal
import com.zhousl.aether.runtime.RuntimeProcessSpec
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

private data class SharedAlpineProfileDefinition(
    val id: String,
    val title: @Composable () -> String,
    val subtitle: String,
    val packages: String,
    val verificationCommand: String,
)

private data class SharedAlpineInstallProgress(
    val activity: SharedAlpineInstallActivity = SharedAlpineInstallActivity.Downloading,
    val bytesPerSecond: Long = 0L,
    val progressPercent: Int? = null,
)

private enum class SharedAlpineInstallActivity { Downloading, Installing }

private enum class SharedAlpineSetupIssue { Ready, NotInstalled, Failed }

private class SharedAlpinePackageProgressTracker {
    private val outputTail = StringBuilder()
    private var activity = SharedAlpineInstallActivity.Downloading
    private var bytesPerSecond = 0L
    private var progressPercent: Int? = null

    fun onOutput(output: String): SharedAlpineInstallProgress {
        outputTail.append(output)
        if (outputTail.length > MaxTrackedOutputChars) {
            val retainedTail = outputTail.substring(outputTail.length - MaxTrackedOutputChars)
            outputTail.clear()
            outputTail.append(retainedTail)
        }
        ApkInstallProgressRegex.findAll(outputTail).lastOrNull()?.let { match ->
            val completed = match.groupValues[1].toIntOrNull() ?: 0
            val total = match.groupValues[2].toIntOrNull() ?: 0
            activity = SharedAlpineInstallActivity.Installing
            progressPercent = if (total > 0) {
                (completed * 100 / total).coerceIn(0, 100)
            } else {
                null
            }
        }
        return snapshot()
    }

    fun onRate(rate: Long): SharedAlpineInstallProgress {
        bytesPerSecond = rate.coerceAtLeast(0L)
        return snapshot()
    }

    private fun snapshot(): SharedAlpineInstallProgress = SharedAlpineInstallProgress(
        activity = activity,
        bytesPerSecond = bytesPerSecond,
        progressPercent = progressPercent,
    )

    private companion object {
        const val MaxTrackedOutputChars = 8_192
        val ApkInstallProgressRegex = Regex(
            """\((\d+)/(\d+)\)\s+(?:Installing|Upgrading|Downgrading|Replacing)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}

private suspend fun sampleSharedAlpineDownloadRate(
    runtime: MultiplatformLocalRuntime,
    onRate: (Long) -> Unit,
) {
    var previousBytes = readSharedAlpineReceivedBytes(runtime) ?: return
    var previousAtMillis = com.zhousl.aether.data.platformCurrentTimeMillis()
    while (currentCoroutineContext().isActive) {
        delay(5_000L)
        val currentBytes = readSharedAlpineReceivedBytes(runtime) ?: continue
        val now = com.zhousl.aether.data.platformCurrentTimeMillis()
        val elapsed = now - previousAtMillis
        if (currentBytes >= previousBytes && elapsed > 0L) {
            onRate((currentBytes - previousBytes) * 1_000L / elapsed)
        }
        previousBytes = currentBytes
        previousAtMillis = now
    }
}

private suspend fun readSharedAlpineReceivedBytes(runtime: MultiplatformLocalRuntime): Long? =
    withTimeoutOrNull(5_000L) {
        coroutineScope {
            val process = runtime.startProcess(
                RuntimeProcessSpec(
                    executable = "/bin/sh",
                    arguments = listOf("-lc", "cat /proc/net/dev"),
                    environment = mapOf("HOME" to runtime.homeDirectory),
                    workingDirectory = runtime.homeDirectory,
                    redirectErrorStream = true,
                )
            )
            var processExited = false
            try {
                process.closeStdin()
                val stdout = async { process.stdout.toList().sharedAlpineFlattenBytes().decodeToString() }
                val exit = process.awaitExit()
                processExited = true
                val output = stdout.await()
                if (exit.exitCode == 0) parseSharedAlpineReceivedBytes(output) else null
            } finally {
                if (!processExited) {
                    withContext(NonCancellable) {
                        runCatching { process.signal(RuntimeProcessSignal.Kill) }
                    }
                }
            }
        }
    }

private suspend fun verifySharedAlpineProfile(
    runtime: MultiplatformLocalRuntime,
    profile: SharedAlpineProfileDefinition,
): Boolean = withTimeoutOrNull(30_000L) {
    coroutineScope {
        val process = runtime.startProcess(
            RuntimeProcessSpec(
                executable = "/bin/sh",
                arguments = listOf("-lc", profile.verificationCommand),
                environment = mapOf("HOME" to runtime.homeDirectory),
                workingDirectory = runtime.homeDirectory,
                redirectErrorStream = true,
            )
        )
        var processExited = false
        try {
            process.closeStdin()
            val output = async { process.stdout.toList() }
            val exit = process.awaitExit()
            processExited = true
            output.await()
            exit.exitCode == 0
        } finally {
            if (!processExited) {
                withContext(NonCancellable) {
                    runCatching { process.signal(RuntimeProcessSignal.Kill) }
                }
            }
        }
    }
} ?: false

private fun parseSharedAlpineReceivedBytes(output: String): Long? {
    val values = output.lineSequence().mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        val interfaceName = line.substring(0, separator).trim()
        if (interfaceName == "lo") return@mapNotNull null
        line.substring(separator + 1).trim().split(Regex("""\s+"""))
            .firstOrNull()?.toLongOrNull()
    }.toList()
    return values.takeIf { it.isNotEmpty() }?.sum()
}

private fun List<ByteArray>.sharedAlpineFlattenBytes(): ByteArray {
    val output = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { bytes ->
        bytes.copyInto(output, destinationOffset = offset)
        offset += bytes.size
    }
    return output
}

private fun formatSharedAlpineTransferRate(bytesPerSecond: Long): String {
    val rate = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        rate >= 1_048_576.0 -> "${formatSharedDecimal(rate / 1_048_576.0)} MB/s"
        rate >= 1_024.0 -> "${(rate / 1_024.0).roundToLong()} KB/s"
        else -> "${rate.roundToLong()} B/s"
    }
}

@Composable
internal fun SharedAlpineSettingsDetailPage(
    runtime: MultiplatformLocalRuntime,
    settings: AppSettings,
    onSettingsSaved: (AppSettings) -> Unit,
    onResetSettingsSaved: suspend (AppSettings) -> Unit,
    onOpenTerminal: () -> Unit,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestSettings by rememberUpdatedState(settings)
    val profileStateCache = remember { settings.alpinePackageProfiles.toMutableMap() }
    var ready by remember { mutableStateOf(settings.alpineSetupCompleted) }
    var setupIssue by remember {
        mutableStateOf(
            if (settings.alpineSetupCompleted) SharedAlpineSetupIssue.Ready
            else SharedAlpineSetupIssue.NotInstalled,
        )
    }
    var detail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val installProgress = remember { mutableStateMapOf<String, SharedAlpineInstallProgress>() }
    val rootFileSystemUnavailableMessage = stringResource(Res.string.settings_alpine_rootfs_unavailable)
    val profiles = listOf(
        SharedAlpineProfileDefinition(
            id = "python",
            title = { stringResource(Res.string.settings_python_environment) },
            subtitle = "python3, pip, virtualenv",
            packages = "python3 py3-pip py3-virtualenv",
            verificationCommand = "python3 --version && pip3 --version && virtualenv --version",
        ),
        SharedAlpineProfileDefinition(
            id = "node",
            title = { stringResource(Res.string.settings_node_environment) },
            subtitle = "nodejs, npm",
            packages = "nodejs npm",
            verificationCommand = "node --version && npm --version",
        ),
        SharedAlpineProfileDefinition(
            id = "git_search",
            title = { stringResource(Res.string.settings_git_ripgrep_tools) },
            subtitle = "git, ripgrep",
            packages = "git ripgrep",
            verificationCommand = "git --version && rg --version",
        ),
        SharedAlpineProfileDefinition(
            id = "ssh",
            title = { stringResource(Res.string.settings_ssh_tools) },
            subtitle = "openssh-client",
            packages = "openssh-client",
            verificationCommand = "ssh -V",
        ),
    )

    fun saveReadyState(isReady: Boolean) {
        ready = isReady
        val current = latestSettings
        onSettingsSaved(
            current.copy(
                alpineSetupCompleted = isReady,
                enabledRuntimeIds = if (isReady) {
                    current.enabledRuntimeIds + LocalRuntimeId.Alpine
                } else {
                    current.enabledRuntimeIds - LocalRuntimeId.Alpine
                },
                defaultRuntimeId = when {
                    isReady && current.defaultRuntimeId == null -> LocalRuntimeId.Alpine
                    !isReady && current.defaultRuntimeId == LocalRuntimeId.Alpine -> null
                    else -> current.defaultRuntimeId
                },
            )
        )
    }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val installed = runtime.isReady()
                ready = installed
                setupIssue = if (installed) {
                    SharedAlpineSetupIssue.Ready
                } else {
                    SharedAlpineSetupIssue.NotInstalled
                }
                detail = if (installed) "" else rootFileSystemUnavailableMessage
                val current = latestSettings
                var updated = current
                if (installed != current.alpineSetupCompleted) {
                    updated = current.copy(
                        alpineSetupCompleted = installed,
                        enabledRuntimeIds = if (installed) {
                            current.enabledRuntimeIds + LocalRuntimeId.Alpine
                        } else {
                            current.enabledRuntimeIds - LocalRuntimeId.Alpine
                        },
                        defaultRuntimeId = when {
                            installed && current.defaultRuntimeId == null -> LocalRuntimeId.Alpine
                            !installed && current.defaultRuntimeId == LocalRuntimeId.Alpine -> null
                            else -> current.defaultRuntimeId
                        },
                    )
                }
                if (installed) {
                    profiles.forEach { profile ->
                        val state = current.alpinePackageProfiles[profile.id] ?: return@forEach
                        if (state.installed && !verifySharedAlpineProfile(runtime, profile)) {
                            profileStateCache[profile.id] = state.copy(
                                installed = false,
                                installedAtMillis = 0L,
                                lastError = "",
                            )
                        }
                    }
                    val verifiedProfiles = profileStateCache.toMap()
                    if (verifiedProfiles != current.alpinePackageProfiles) {
                        updated = updated.copy(alpinePackageProfiles = verifiedProfiles)
                    }
                }
                if (updated != current) onSettingsSaved(updated)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ready = false
                setupIssue = SharedAlpineSetupIssue.Failed
                detail = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }

    fun initialize() {
        if (busy) return
        busy = true
        detail = ""
        scope.launch {
            try {
                runtime.initialize()
                saveReadyState(true)
                setupIssue = SharedAlpineSetupIssue.Ready
                detail = ""
                onTransientMessage("Alpine runtime is ready.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ready = false
                setupIssue = SharedAlpineSetupIssue.Failed
                detail = error.message.orEmpty().ifBlank { "Failed to install Alpine runtime." }
                onTransientMessage(detail)
            } finally {
                busy = false
            }
        }
    }

    fun installProfile(profile: SharedAlpineProfileDefinition) {
        if (!ready || profile.id in installProgress) return
        installProgress[profile.id] = SharedAlpineInstallProgress()
        scope.launch {
            val result = try {
                withTimeout(10 * 60 * 1_000L) {
                    val tracker = SharedAlpinePackageProgressTracker()
                    val output = StringBuilder()
                    val process = runtime.startProcess(
                        RuntimeProcessSpec(
                            executable = "/bin/sh",
                            arguments = listOf("-lc", "apk add --no-cache --no-chown ${profile.packages}"),
                            environment = mapOf("HOME" to runtime.homeDirectory),
                            workingDirectory = runtime.homeDirectory,
                            redirectErrorStream = true,
                        )
                    )
                    var processExited = false
                    var rateSampler: kotlinx.coroutines.Job? = null
                    try {
                        process.closeStdin()
                        val stdout = async {
                            process.stdout.collect { bytes ->
                                val chunk = bytes.decodeToString()
                                output.append(chunk)
                                installProgress[profile.id] = tracker.onOutput(chunk)
                            }
                        }
                        rateSampler = launch {
                            sampleSharedAlpineDownloadRate(runtime) { bytesPerSecond ->
                                installProgress[profile.id] = tracker.onRate(bytesPerSecond)
                            }
                        }
                        val exit = process.awaitExit()
                        processExited = true
                        rateSampler.cancelAndJoin()
                        stdout.await()
                        check(exit.exitCode == 0 || verifySharedAlpineProfile(runtime, profile)) {
                            output.toString().trim().takeLast(1_000).ifBlank {
                                "Alpine package installation exited with ${exit.exitCode}."
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            rateSampler?.cancelAndJoin()
                            if (!processExited) runCatching { process.signal(RuntimeProcessSignal.Kill) }
                        }
                    }
                }
                Result.success(Unit)
            } catch (error: CancellationException) {
                installProgress.remove(profile.id)
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            val now = com.zhousl.aether.data.platformCurrentTimeMillis()
            val resultDetail = if (result.isSuccess) {
                "Installed Alpine profile ${profile.id}."
            } else {
                result.exceptionOrNull()?.message.orEmpty().ifBlank { "Install failed." }
            }
            profileStateCache[profile.id] = PackageProfileState(
                profileId = profile.id,
                installed = result.isSuccess,
                installedAtMillis = if (result.isSuccess) now else 0,
                lastError = if (result.isSuccess) "" else resultDetail,
            )
            onSettingsSaved(latestSettings.copy(alpinePackageProfiles = profileStateCache.toMap()))
            installProgress.remove(profile.id)
            onTransientMessage(resultDetail)
        }
    }

    fun reset() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                runtime.reset()
                ready = false
                setupIssue = SharedAlpineSetupIssue.NotInstalled
                detail = ""
                profileStateCache.clear()
                val current = latestSettings
                val remainingRuntimeIds = current.enabledRuntimeIds - LocalRuntimeId.Alpine
                onResetSettingsSaved(
                    current.copy(
                        alpineSetupCompleted = false,
                        alpinePackageProfiles = emptyMap(),
                        enabledRuntimeIds = remainingRuntimeIds,
                        defaultRuntimeId = if (current.defaultRuntimeId == LocalRuntimeId.Alpine) {
                            remainingRuntimeIds.firstOrNull()
                        } else {
                            current.defaultRuntimeId
                        },
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                detail = error.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Box(Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.settings_alpine_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsCardGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(Res.string.settings_runtime_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            when (setupIssue) {
                                SharedAlpineSetupIssue.Ready -> Res.string.settings_alpine_status_ready
                                SharedAlpineSetupIssue.NotInstalled ->
                                    Res.string.settings_alpine_status_not_installed
                                SharedAlpineSetupIssue.Failed -> Res.string.settings_alpine_status_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SharedSettingsActionButton(
                            label = stringResource(
                                if (ready) Res.string.settings_ready else Res.string.settings_initialize,
                            ),
                            onClick = ::initialize,
                            modifier = Modifier.weight(1f),
                            enabled = !ready,
                        )
                        SharedSettingsSubtleActionButton(
                            label = stringResource(Res.string.common_refresh),
                            onClick = ::refresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SharedSettingsSubtleActionButton(
                        label = stringResource(Res.string.settings_reset_alpine_data),
                        onClick = ::reset,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ready && settings.defaultRuntimeId != LocalRuntimeId.Alpine) {
                        Spacer(Modifier.height(10.dp))
                        SharedSettingsActionButton(
                            label = stringResource(Res.string.settings_use_as_default_runtime),
                            onClick = {
                                onSettingsSaved(
                                    latestSettings.copy(
                                        defaultRuntimeId = LocalRuntimeId.Alpine,
                                        enabledRuntimeIds = latestSettings.enabledRuntimeIds + LocalRuntimeId.Alpine,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            SettingsCardGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(Res.string.settings_environment_presets),
                        style = MaterialTheme.typography.labelLarge,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(Res.string.settings_environment_presets_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    profiles.forEachIndexed { index, profile ->
                        SharedAlpineProfileRow(
                            title = profile.title(),
                            subtitle = profile.subtitle,
                            state = settings.alpinePackageProfiles[profile.id],
                            progress = installProgress[profile.id],
                            enabled = ready,
                            onInstall = { installProfile(profile) },
                        )
                        if (index != profiles.lastIndex) CardDivider()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        SettingsTopBar(
            title = "Alpine",
            onBack = onBack,
            trailingIcon = Icons.Rounded.Terminal,
            trailingEnabled = ready,
            trailingContentDescription = stringResource(Res.string.settings_open_terminal),
            onTrailingAction = onOpenTerminal,
        )
    }
}

@Composable
private fun SharedAlpineProfileRow(
    title: String,
    subtitle: String,
    state: PackageProfileState?,
    progress: SharedAlpineInstallProgress?,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val progressValue = when (progress?.activity) {
        SharedAlpineInstallActivity.Installing -> "${progress.progressPercent ?: 0}%"
        SharedAlpineInstallActivity.Downloading -> formatSharedAlpineTransferRate(progress.bytesPerSecond)
        null -> ""
    }
    val progressDetail = when (progress?.activity) {
        SharedAlpineInstallActivity.Installing ->
            stringResource(Res.string.settings_profile_installing_percent, progressValue)
        SharedAlpineInstallActivity.Downloading ->
            stringResource(Res.string.settings_profile_downloading_rate, progressValue)
        null -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            val persistedError = state?.lastError
                ?.takeUnless { it == "Installing..." }
                .orEmpty()
            val status = progressDetail.ifBlank { persistedError }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress != null) AetherPrimary else AetherOnSurfaceVariant,
                    maxLines = if (progress != null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SharedSettingsSubtleActionButton(
            label = when {
                progress != null -> progressValue
                state?.installed == true -> stringResource(Res.string.settings_installed)
                else -> stringResource(Res.string.common_install)
            },
            onClick = onInstall,
            enabled = enabled && progress == null && state?.installed != true,
        )
    }
}
