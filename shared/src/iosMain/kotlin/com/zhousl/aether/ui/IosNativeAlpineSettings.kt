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
import androidx.compose.material.icons.rounded.Folder
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
import com.zhousl.aether.runtime.PiNodeRuntimeSetup
import com.zhousl.aether.runtime.RuntimeProcessSignal
import com.zhousl.aether.runtime.RuntimeProcessSpec
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherSettingsBackground
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
    verificationCommand: String,
): Boolean = withTimeoutOrNull(30_000L) {
    coroutineScope {
        val process = runtime.startProcess(
            RuntimeProcessSpec(
                executable = "/bin/sh",
                arguments = listOf("-lc", verificationCommand),
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

internal data class NativeAlpineProfile(
    val id: String,
    val packages: String,
    val verificationCommand: String,
)

internal data class NativeAlpineSettingsState(
    val ready: Boolean = false,
    val issue: String = "not_installed",
    val detail: String = "",
    val operation: String = "",
    val progress: String = "",
)

internal data class NativeAlpineResult(
    val state: NativeAlpineSettingsState,
    val settings: AppSettings,
)

internal class NativeAlpineSettingsController(
    private val runtime: MultiplatformLocalRuntime,
) {
    val profiles = listOf(
        NativeAlpineProfile("python", "python3 py3-pip py3-virtualenv", "python3 --version && pip3 --version && virtualenv --version"),
        NativeAlpineProfile("node", "nodejs npm", PiNodeRuntimeSetup.verificationCommand),
        NativeAlpineProfile("git_search", "git ripgrep", "git --version && rg --version"),
        NativeAlpineProfile("ssh", "openssh-client", "ssh -V"),
    )

    suspend fun refresh(settings: AppSettings): NativeAlpineResult = try {
        val ready = runtime.isReady()
        val verifiedProfiles = if (ready) {
            settings.alpinePackageProfiles.mapValues { (id, state) ->
                val profile = profiles.firstOrNull { it.id == id }
                if (state.installed && profile != null &&
                    !verifySharedAlpineProfile(runtime, profile.verificationCommand)
                ) {
                    state.copy(installed = false, installedAtMillis = 0L, lastError = "")
                } else {
                    state
                }
            }
        } else {
            settings.alpinePackageProfiles
        }
        val updated = settings.copy(
            alpineSetupCompleted = ready,
            alpinePackageProfiles = verifiedProfiles,
            enabledRuntimeIds = if (ready) {
                settings.enabledRuntimeIds + LocalRuntimeId.Alpine
            } else {
                settings.enabledRuntimeIds - LocalRuntimeId.Alpine
            },
            defaultRuntimeId = if (!ready && settings.defaultRuntimeId == LocalRuntimeId.Alpine) {
                null
            } else {
                settings.defaultRuntimeId
            },
        )
        NativeAlpineResult(
            state = NativeAlpineSettingsState(
                ready = ready,
                issue = if (ready) "ready" else "not_installed",
                detail = if (ready) "" else "The Alpine root filesystem is not initialized.",
            ),
            settings = updated,
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        NativeAlpineResult(
            state = NativeAlpineSettingsState(
                issue = "failed",
                detail = failure.message.orEmpty().ifBlank { "Unable to inspect Alpine." },
            ),
            settings = settings,
        )
    }

    suspend fun initialize(settings: AppSettings): NativeAlpineResult = try {
        runtime.initialize()
        val updated = settings.copy(
            alpineSetupCompleted = true,
            enabledRuntimeIds = settings.enabledRuntimeIds + LocalRuntimeId.Alpine,
            defaultRuntimeId = settings.defaultRuntimeId ?: LocalRuntimeId.Alpine,
        )
        NativeAlpineResult(NativeAlpineSettingsState(ready = true, issue = "ready"), updated)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        NativeAlpineResult(
            NativeAlpineSettingsState(
                issue = "failed",
                detail = failure.message.orEmpty().ifBlank { "Failed to initialize Alpine." },
            ),
            settings,
        )
    }

    suspend fun reset(settings: AppSettings): NativeAlpineResult = try {
        runtime.reset()
        val remaining = settings.enabledRuntimeIds - LocalRuntimeId.Alpine
        val updated = settings.copy(
            alpineSetupCompleted = false,
            alpinePackageProfiles = emptyMap(),
            enabledRuntimeIds = remaining,
            defaultRuntimeId = if (settings.defaultRuntimeId == LocalRuntimeId.Alpine) {
                remaining.firstOrNull()
            } else {
                settings.defaultRuntimeId
            },
        )
        NativeAlpineResult(NativeAlpineSettingsState(), updated)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        NativeAlpineResult(
            NativeAlpineSettingsState(
                ready = settings.alpineSetupCompleted,
                issue = "failed",
                detail = failure.message.orEmpty().ifBlank { "Failed to reset Alpine." },
            ),
            settings,
        )
    }

    suspend fun installProfile(
        settings: AppSettings,
        profileId: String,
        onProgress: (String) -> Unit,
    ): NativeAlpineResult {
        val profile = profiles.firstOrNull { it.id == profileId }
            ?: return NativeAlpineResult(
                NativeAlpineSettingsState(true, "failed", "Unknown Alpine environment profile."),
                settings,
            )
        return try {
            check(runtime.isReady()) { "Initialize Alpine before installing an environment." }
            withTimeout(10 * 60 * 1_000L) {
                coroutineScope {
                    val output = StringBuilder()
                    val process = runtime.startProcess(
                        RuntimeProcessSpec(
                            executable = "/bin/sh",
                            arguments = listOf(
                                "-lc",
                                if (profile.id == "node") PiNodeRuntimeSetup.installCommand(noChown = false)
                                else "apk add --no-cache --no-chown ${profile.packages}",
                            ),
                            environment = mapOf("HOME" to runtime.homeDirectory),
                            workingDirectory = runtime.homeDirectory,
                            redirectErrorStream = true,
                        )
                    )
                    var exited = false
                    try {
                        process.closeStdin()
                        val stdout = async {
                            process.stdout.collect { bytes ->
                                val text = bytes.decodeToString()
                                output.append(text)
                                text.lineSequence().lastOrNull { it.isNotBlank() }
                                    ?.takeLast(160)?.let(onProgress)
                            }
                        }
                        val exit = process.awaitExit()
                        exited = true
                        stdout.await()
                        check(verifySharedAlpineProfile(runtime, profile.verificationCommand)) {
                            output.toString().trim().takeLast(1_000).ifBlank {
                                "Alpine package installation exited with ${exit.exitCode}."
                            }
                        }
                    } finally {
                        if (!exited) withContext(NonCancellable) {
                            runCatching { process.signal(RuntimeProcessSignal.Kill) }
                        }
                    }
                }
            }
            val updatedProfiles = settings.alpinePackageProfiles + (
                profile.id to PackageProfileState(
                    profileId = profile.id,
                    installed = true,
                    installedAtMillis = com.zhousl.aether.data.platformCurrentTimeMillis(),
                )
            )
            NativeAlpineResult(
                NativeAlpineSettingsState(ready = true, issue = "ready"),
                settings.copy(alpinePackageProfiles = updatedProfiles),
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            val message = failure.message.orEmpty().ifBlank { "Failed to install Alpine environment." }
            val updatedProfiles = settings.alpinePackageProfiles + (
                profile.id to PackageProfileState(profile.id, lastError = message)
            )
            NativeAlpineResult(
                NativeAlpineSettingsState(ready = true, issue = "ready", detail = message),
                settings.copy(alpinePackageProfiles = updatedProfiles),
            )
        }
    }
}
