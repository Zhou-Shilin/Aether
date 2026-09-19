package com.zhousl.aether.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.zhousl.aether.BuildConfig
import com.zhousl.aether.data.AetherDiagnosticLogger
import com.zhousl.aether.data.DefaultOldCommandHistoryRetentionHours
import com.zhousl.aether.data.MaxOldCommandHistoryRetentionHours
import com.zhousl.aether.data.MinOldCommandHistoryRetentionHours
import com.zhousl.aether.data.TermuxEnvironmentVariable
import com.zhousl.aether.data.normalizeTermuxEnvironmentVariables
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val ManagedCommandWatchWindowSeconds = 45
private const val RuntimeOperationPollIntervalMillis = 250L
private const val DefaultManagedLogTailBytes = 12 * 1024
private const val MaxManagedLogTailBytes = 64 * 1024
private const val InternalCommandTimeoutMillis = 15_000L
private const val SharedWorkspaceRoot = "${TermuxContract.HomeDirectory}/.aether/workspace"
private const val SessionWorkspaceRoot = "${TermuxContract.HomeDirectory}/.aether/workspaces"
private const val TermuxLogTag = "AetherTermux"
private val EnableTermuxLogging: Boolean
    get() = BuildConfig.DEBUG
private const val EarlyManagedLaunchGraceMillis = 5_000L
private const val ManagedBashRunPruneThrottleMillis = 60 * 60 * 1000L

enum class TermuxSetupIssue {
    Ready,
    NotInstalled,
    PermissionMissing,
    ExternalAppsDisabled,
    DispatchFailed,
}

data class TermuxSetupState(
    val issue: TermuxSetupIssue = TermuxSetupIssue.Ready,
    val detail: String = "",
    val previouslyConfigured: Boolean = false,
) {
    val isReady: Boolean
        get() = issue == TermuxSetupIssue.Ready
}

private data class ManagedLogChunk(
    val data: ByteArray,
    val running: Boolean,
    val exitCode: Int?,
)

class TermuxBashTool(
    private val context: Context,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    private val environmentVariables = AtomicReference<List<TermuxEnvironmentVariable>>(emptyList())
    private val autoCleanOldCommandHistory = AtomicReference(true)
    private val oldCommandHistoryRetentionHours = AtomicInteger(DefaultOldCommandHistoryRetentionHours)
    private val lastManagedBashRunPruneAtMillis = AtomicLong(0L)

    fun setEnvironmentVariables(variables: List<TermuxEnvironmentVariable>) {
        environmentVariables.set(normalizeTermuxEnvironmentVariables(variables))
    }

    fun setManagedBashRunCleanupPolicy(
        enabled: Boolean,
        retentionHours: Int,
    ) {
        autoCleanOldCommandHistory.set(enabled)
        oldCommandHistoryRetentionHours.set(
            retentionHours.coerceIn(
                minimumValue = MinOldCommandHistoryRetentionHours,
                maximumValue = MaxOldCommandHistoryRetentionHours,
            )
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun setRootBackgroundLaunchEnabled(enabled: Boolean) {
        // Root setup can configure Termux files, but command dispatch should not
        // auto-launch Termux when it is not already running in the background.
    }

    suspend fun inspectSetup(): TermuxSetupState = withContext(Dispatchers.IO) {
        if (!isTermuxInstalled()) {
            return@withContext TermuxSetupState(
                issue = TermuxSetupIssue.NotInstalled,
                detail = "Install Termux before using bash tools.",
            )
        }

        if (!hasRunCommandPermission()) {
            return@withContext TermuxSetupState(
                issue = TermuxSetupIssue.PermissionMissing,
                detail = "Grant 'Run commands in Termux environment' to Aether.",
            )
        }

        val probeResult = dispatchCommand(
            command = "printf '__aether_termux_ready__'",
            workingDirectory = TermuxContract.HomeDirectory,
            awaitTimeoutMillis = 4000,
        )
        val json = runCatching { JSONObject(probeResult) }.getOrNull()
            ?: return@withContext TermuxSetupState(
                issue = TermuxSetupIssue.DispatchFailed,
                detail = "Termux returned an unreadable setup probe result.",
            )

        when {
            json.optBoolean("ok") &&
                json.optString("stdout").contains("__aether_termux_ready__") -> {
                TermuxSetupState(TermuxSetupIssue.Ready)
            }

            json.optString("hint").contains("allow-external-apps", ignoreCase = true) ||
                json.optString("errmsg").contains("allow-external-apps", ignoreCase = true) ||
                json.optString("errmsg").contains("rejected", ignoreCase = true) -> {
                TermuxSetupState(
                    issue = TermuxSetupIssue.ExternalAppsDisabled,
                    detail = "In Termux, paste the setup command to enable allow-external-apps, then return to Aether.",
                )
            }

            else -> {
                TermuxSetupState(
                    issue = TermuxSetupIssue.DispatchFailed,
                    detail = json.optString("errmsg").ifBlank {
                        json.optString("hint").ifBlank { "Termux did not accept the setup probe." }
                    },
                )
            }
        }
    }

    suspend fun execute(
        argumentsJson: String,
        onProgress: (suspend (String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrElse {
            return@withContext buildInvalidArgumentsResult("Arguments were not valid JSON.")
        }

        val command = arguments.optString("command").trim()
        val workingDirectory = normalizeTermuxPath(
            arguments.optString("working_directory").trim()
                .ifBlank { arguments.optString("workingDirectory").trim() }
                .ifBlank { TermuxContract.HomeDirectory }
        )

        if (command.isBlank()) {
            return@withContext buildInvalidArgumentsResult("Missing required 'command' argument.")
        }

        val runId = TermuxManagedRuns.nextRunId()
        prepareWorkingDirectoryIfNeeded(workingDirectory)?.let { raw ->
            return@withContext buildManagedToolError(
                command = command,
                workingDirectory = workingDirectory,
                runId = runId,
                message = raw.optString("errmsg").ifBlank {
                    "Couldn't prepare the workspace directory before running the bash command."
                },
                hint = raw.optString("hint"),
                stdout = raw.optString("stdout"),
                stderr = raw.optString("stderr"),
            )
        }
        maybePruneExpiredManagedBashRuns()
        try {
            val startedAtMillis = System.currentTimeMillis()
            val initialResult = executeManagedScript(
                script = buildLaunchManagedCommandScript(
                    runId = runId,
                    command = command,
                    workingDirectory = workingDirectory,
                    tailBytes = DefaultManagedLogTailBytes,
                ),
                commandFallback = command,
                workingDirectoryFallback = workingDirectory,
                runIdFallback = runId,
            )
            awaitManagedCommandWatchWindow(
                initialResult = initialResult,
                runId = runId,
                startedAtMillis = startedAtMillis,
                onProgress = onProgress,
            )
        } catch (cancellationException: CancellationException) {
            withContext(NonCancellable) {
                runCatching { killExecutionByRunId(runId) }
            }
            throw cancellationException
        }
    }

    suspend fun executeStreaming(
        command: String,
        workingDirectory: String,
        timeoutSeconds: Double? = null,
        onData: suspend (ByteArray) -> Unit,
    ): Int? = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Missing bash command." }
        require(timeoutSeconds == null || timeoutSeconds.isFinite() && timeoutSeconds > 0.0) {
            "timeout must be a positive number of seconds."
        }
        val normalizedWorkingDirectory = normalizeTermuxPath(workingDirectory)
        prepareWorkingDirectoryIfNeeded(normalizedWorkingDirectory)?.let { setup ->
            error(setup.optString("errmsg").ifBlank { "Couldn't prepare the Termux working directory." })
        }
        val runId = TermuxManagedRuns.nextRunId()
        var offset = 0L
        var finalExitCode: Int? = null
        val startedAtNanos = System.nanoTime()
        try {
            val launched = executeManagedScript(
                script = buildLaunchManagedCommandScript(
                    runId = runId,
                    command = command,
                    workingDirectory = normalizedWorkingDirectory,
                    tailBytes = 1,
                    watchSeconds = 0,
                ),
                commandFallback = command,
                workingDirectoryFallback = normalizedWorkingDirectory,
                runIdFallback = runId,
            )
            val launchJson = JSONObject(launched)
            if (!launchJson.optBoolean("ok") && !launchJson.optBoolean("running")) {
                error(launchJson.optString("errmsg").ifBlank { "Failed to launch Termux bash command." })
            }
            while (true) {
                val snapshot = readManagedLogChunk(runId, offset)
                val chunk = snapshot.data
                if (chunk.isNotEmpty()) {
                    onData(chunk)
                    offset += chunk.size
                }
                if (!snapshot.running) {
                    finalExitCode = snapshot.exitCode
                    break
                }
                if (timeoutSeconds != null) {
                    val elapsedSeconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
                    if (elapsedSeconds >= timeoutSeconds) {
                        withContext(NonCancellable) { killExecutionByRunId(runId) }
                        error("timeout:$timeoutSeconds")
                    }
                }
                delay(RuntimeOperationPollIntervalMillis)
            }
            finalExitCode
        } catch (cancellationException: CancellationException) {
            withContext(NonCancellable) { runCatching { killExecutionByRunId(runId) } }
            throw cancellationException
        }
    }


    private suspend fun maybePruneExpiredManagedBashRuns() {
        if (!autoCleanOldCommandHistory.get()) return
        val nowMillis = System.currentTimeMillis()
        val previousPruneMillis = lastManagedBashRunPruneAtMillis.get()
        if (nowMillis - previousPruneMillis < ManagedBashRunPruneThrottleMillis) return
        if (!lastManagedBashRunPruneAtMillis.compareAndSet(previousPruneMillis, nowMillis)) return

        val retentionMillis = oldCommandHistoryRetentionHours.get() * 60L * 60L * 1000L
        val raw = try {
            JSONObject(
                dispatchCommand(
                    command = buildPruneExpiredManagedBashRunsScript(retentionMillis),
                    workingDirectory = TermuxContract.HomeDirectory,
                    awaitTimeoutMillis = InternalCommandTimeoutMillis,
                )
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            lastManagedBashRunPruneAtMillis.set(0L)
            diagnosticLogger.exception(
                category = "storage",
                event = "old_command_history_cleanup_failed",
                throwable = throwable,
                level = "warn",
            )
            return
        }

        if (raw.optBoolean("ok")) {
            val values = parseKeyValueOutput(raw.optString("stdout"))
            diagnosticLogger.event(
                category = "storage",
                event = "old_command_history_cleanup_completed",
                details = mapOf(
                    "deleted_count" to (values["expired_bash_runs_deleted"] ?: "0"),
                    "retention_hours" to oldCommandHistoryRetentionHours.get(),
                ),
            )
        } else {
            lastManagedBashRunPruneAtMillis.set(0L)
            diagnosticLogger.event(
                category = "storage",
                event = "old_command_history_cleanup_failed",
                level = "warn",
                details = mapOf(
                    "message" to raw.optString("errmsg").ifBlank { raw.optString("stderr") },
                    "retention_hours" to oldCommandHistoryRetentionHours.get(),
                ),
            )
        }
    }

    suspend fun fetchExecution(argumentsJson: String): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrElse {
            return@withContext buildInvalidArgumentsResult("Arguments were not valid JSON.")
        }
        val runId = arguments.optString("run_id").trim()
            .ifBlank { arguments.optString("runId").trim() }
        val tailBytes = resolveTailBytes(arguments)

        if (!isValidRunId(runId)) {
            return@withContext buildInvalidArgumentsResult("Missing or invalid 'run_id' argument.")
        }

        executeManagedScript(
            script = buildInspectManagedCommandScript(
                runId = runId,
                tailBytes = tailBytes,
            ),
            runIdFallback = runId,
        )
    }

    suspend fun killExecution(argumentsJson: String): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrElse {
            return@withContext buildInvalidArgumentsResult("Arguments were not valid JSON.")
        }
        val runId = arguments.optString("run_id").trim()
            .ifBlank { arguments.optString("runId").trim() }
        val tailBytes = resolveTailBytes(arguments)

        if (!isValidRunId(runId)) {
            return@withContext buildInvalidArgumentsResult("Missing or invalid 'run_id' argument.")
        }

        executeManagedScript(
            script = buildKillManagedCommandScript(
                runId = runId,
                tailBytes = tailBytes,
            ),
            runIdFallback = runId,
        )
    }

    suspend fun killExecutionByRunId(
        runId: String,
        tailBytes: Int = DefaultManagedLogTailBytes,
    ): String = withContext(Dispatchers.IO) {
        if (!isValidRunId(runId)) {
            return@withContext buildInvalidArgumentsResult("Missing or invalid run id.")
        }

        executeManagedScript(
            script = buildKillManagedCommandScript(
                runId = runId,
                tailBytes = tailBytes.coerceIn(1, MaxManagedLogTailBytes),
            ),
            runIdFallback = runId,
        )
    }

    suspend fun executeCommand(
        command: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
        awaitTimeoutMillis: Long = InternalCommandTimeoutMillis,
    ): String = withContext(Dispatchers.IO) {
        val normalizedWorkingDirectory = normalizeTermuxPath(workingDirectory)
        prepareWorkingDirectoryIfNeeded(normalizedWorkingDirectory)?.let { raw ->
            return@withContext buildSetupErrorResult(
                command = command,
                workingDirectory = normalizedWorkingDirectory,
                message = raw.optString("errmsg").ifBlank {
                    "Couldn't prepare the workspace directory before running the command."
                },
                hint = raw.optString("hint"),
            )
        }
        dispatchCommand(
            command = command,
            workingDirectory = normalizedWorkingDirectory,
            awaitTimeoutMillis = awaitTimeoutMillis,
        )
    }

    private suspend fun executeManagedScript(
        script: String,
        commandFallback: String = "",
        workingDirectoryFallback: String = "",
        runIdFallback: String = "",
    ): String {
        val raw = try {
            JSONObject(
                dispatchCommand(
                    command = script,
                    workingDirectory = TermuxContract.HomeDirectory,
                )
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: Throwable) {
            null
        }
            ?: return buildManagedToolError(
                command = commandFallback,
                workingDirectory = workingDirectoryFallback,
                runId = runIdFallback,
                message = "Termux returned unreadable command output.",
            )

        if (!raw.optBoolean("ok")) {
            return buildManagedToolError(
                command = commandFallback,
                workingDirectory = workingDirectoryFallback,
                runId = runIdFallback,
                message = raw.optString("errmsg").ifBlank { "Failed to dispatch the managed bash command." },
                hint = raw.optString("hint"),
                stdout = raw.optString("stdout"),
                stderr = raw.optString("stderr"),
            )
        }

        val values = parseKeyValueOutput(raw.optString("stdout"))
        if (values.isEmpty()) {
            return buildManagedToolError(
                command = commandFallback,
                workingDirectory = workingDirectoryFallback,
                runId = runIdFallback,
                message = "Termux returned an unreadable managed command payload.",
                stdout = raw.optString("stdout"),
                stderr = raw.optString("stderr"),
            )
        }

        return buildManagedCommandResult(
            values = values,
            fallbackCommand = commandFallback,
            fallbackWorkingDirectory = workingDirectoryFallback,
            fallbackRunId = runIdFallback,
        )
    }

    private suspend fun readManagedLogChunk(
        runId: String,
        offset: Long,
    ): ManagedLogChunk {
        val raw = JSONObject(
            dispatchCommand(
                command = buildReadManagedLogScript(runId, offset),
                workingDirectory = TermuxContract.HomeDirectory,
                awaitTimeoutMillis = InternalCommandTimeoutMillis,
            )
        )
        check(raw.optBoolean("ok")) {
            raw.optString("errmsg").ifBlank { "Failed to read Termux bash output." }
        }
        val values = parseKeyValueOutput(raw.optString("stdout"))
        check(values["exists"].toBoolean()) { "The managed Termux bash process disappeared." }
        val data = values["data_b64"].orEmpty().takeIf(String::isNotBlank)?.let {
            Base64.getDecoder().decode(it)
        } ?: ByteArray(0)
        val totalBytes = values["total_bytes"]?.toLongOrNull() ?: offset + data.size
        val nextOffset = offset + data.size
        val state = values["status"].orEmpty()
        return ManagedLogChunk(
            data = data,
            running = state == "running" || state == "launching" || nextOffset < totalBytes,
            exitCode = values["exit_code"]?.toIntOrNull(),
        )
    }

    private suspend fun awaitManagedCommandWatchWindow(
        initialResult: String,
        runId: String,
        startedAtMillis: Long,
        onProgress: (suspend (String) -> Unit)? = null,
    ): String {
        var currentResult = initialResult
        emitManagedCommandProgress(currentResult, onProgress)
        while (true) {
            val json = runCatching { JSONObject(currentResult) }.getOrNull()
                ?: return currentResult

            val elapsedMillis = System.currentTimeMillis() - startedAtMillis
            val remainingMillis = ManagedCommandWatchWindowSeconds * 1_000L - elapsedMillis
            if (remainingMillis <= 0L) {
                logTermux("managed watch deadline run_id=$runId elapsed_ms=$elapsedMillis")
                return currentResult
            }

            val shouldPoll = json.optBoolean("running") ||
                isTransientManagedLaunchResult(
                    json = json,
                    runId = runId,
                    elapsedMillis = elapsedMillis,
                )
            if (!shouldPoll) {
                logTermux(
                    "managed watch done run_id=$runId status=${json.optString("status")} " +
                        "elapsed_ms=$elapsedMillis",
                )
                return currentResult
            }

            logTermux(
                "managed watch poll run_id=$runId status=${json.optString("status")} " +
                    "elapsed_ms=$elapsedMillis",
            )
            delay(remainingMillis.coerceAtMost(1_000L))
            currentResult = executeManagedScript(
                script = buildInspectManagedCommandScript(
                    runId = runId,
                    tailBytes = DefaultManagedLogTailBytes,
                ),
                runIdFallback = runId,
            )
            emitManagedCommandProgress(currentResult, onProgress)
        }
    }

    private suspend fun emitManagedCommandProgress(
        result: String,
        onProgress: (suspend (String) -> Unit)?,
    ) {
        if (onProgress == null) return
        val json = runCatching { JSONObject(result) }.getOrNull() ?: return
        if (!json.optBoolean("running")) return
        onProgress(result)
    }

    private fun isTransientManagedLaunchResult(
        json: JSONObject,
        runId: String,
        elapsedMillis: Long,
    ): Boolean {
        if (elapsedMillis > EarlyManagedLaunchGraceMillis) return false
        if (json.optString("run_id") != runId) return false
        return json.optString("status") == "error" &&
            json.optString("errmsg")
                .contains("unreadable managed command payload", ignoreCase = true)
    }

    private suspend fun dispatchCommand(
        command: String,
        workingDirectory: String,
        awaitTimeoutMillis: Long? = null,
    ): String {
        val startedAt = System.currentTimeMillis()
        logTermux(
            "dispatch start workdir=$workingDirectory timeout_ms=${
                awaitTimeoutMillis ?: -1L
            } command=${summarizeCommand(command)}",
        )
        if (!isTermuxInstalled()) {
            return buildSetupErrorResult(
                command = command,
                workingDirectory = workingDirectory,
                message = "Termux is not installed.",
                hint = "Install the Termux app (package com.termux) first.",
            )
        }

        if (!hasRunCommandPermission()) {
            return buildSetupErrorResult(
                command = command,
                workingDirectory = workingDirectory,
                message = "Aether does not have Termux RUN_COMMAND permission.",
                hint = "Grant 'Run commands in Termux environment' to Aether in Android Settings.",
            )
        }

        return dispatchCommandToTermuxService(
            command = command,
            workingDirectory = workingDirectory,
            awaitTimeoutMillis = awaitTimeoutMillis,
            startedAt = startedAt,
        )
    }

    private suspend fun dispatchCommandToTermuxService(
        command: String,
        workingDirectory: String,
        awaitTimeoutMillis: Long?,
        startedAt: Long,
    ): String {
        val executionId = TermuxPendingResults.nextExecutionId()
        val deferred = TermuxPendingResults.register(executionId)
        val appContext = context.applicationContext
        val resultIntent = Intent(appContext, TermuxResultReceiver::class.java)
            .putExtra(TermuxContract.ExecutionIdExtra, executionId)
        val flags = PendingIntent.FLAG_ONE_SHOT or
            PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            executionId,
            resultIntent,
            flags,
        )

        val request = Intent().apply {
            component = ComponentName(
                TermuxContract.PackageName,
                TermuxContract.RunCommandService,
            )
            action = TermuxContract.RunCommandAction
            putExtra(TermuxContract.RunCommandPathExtra, TermuxContract.BashPath)
            putExtra(TermuxContract.RunCommandArgumentsExtra, arrayOf("-s"))
            putExtra(
                TermuxContract.RunCommandStdinExtra,
                buildTermuxDispatchScript(command, environmentVariables.get()),
            )
            putExtra(TermuxContract.RunCommandWorkdirExtra, workingDirectory)
            putExtra(TermuxContract.RunCommandBackgroundExtra, true)
            putExtra(TermuxContract.RunCommandPendingIntentExtra, pendingIntent)
            putExtra(TermuxContract.RunCommandLabelExtra, "Aether bash")
            putExtra(
                TermuxContract.RunCommandDescriptionExtra,
                "Runs a bash command requested by the Aether Android agent.",
            )
        }

        return try {
            val startResult = startTermuxRunCommandService(request)
            if (startResult == null) {
                TermuxPendingResults.remove(executionId)
                logTermux(
                    "dispatch rejected duration_ms=${System.currentTimeMillis() - startedAt} " +
                        "command=${summarizeCommand(command)}",
                )
                buildSetupErrorResult(
                    command = command,
                    workingDirectory = workingDirectory,
                    message = "Termux rejected the RUN_COMMAND request.",
                    hint = "Paste the Aether Termux setup command inside Termux to enable allow-external-apps.",
                )
            } else {
                val result = if (awaitTimeoutMillis == null) {
                    deferred.await()
                } else {
                    withTimeoutOrNull(awaitTimeoutMillis) { deferred.await() }
                        ?: run {
                            TermuxPendingResults.remove(executionId)
                            logTermux(
                                "dispatch timeout duration_ms=${System.currentTimeMillis() - startedAt} " +
                                    "command=${summarizeCommand(command)}",
                            )
                            return buildSetupErrorResult(
                                command = command,
                                workingDirectory = workingDirectory,
                                message = "Timed out waiting for Termux to reply.",
                                hint = "Open Termux once, paste the Aether Termux setup command, then refresh.",
                            )
                        }
                }
                logTermux(
                    "dispatch result duration_ms=${System.currentTimeMillis() - startedAt} " +
                        "exit_code=${result.exitCode} err=${result.err} " +
                        "command=${summarizeCommand(command)}",
                )
                buildCommandResult(
                    command = command,
                    workingDirectory = workingDirectory,
                    durationMillis = System.currentTimeMillis() - startedAt,
                    result = result,
                )
            }
        } catch (cancellationException: CancellationException) {
            TermuxPendingResults.remove(executionId)
            logTermux(
                "dispatch cancelled duration_ms=${System.currentTimeMillis() - startedAt} " +
                    "command=${summarizeCommand(command)}",
            )
            throw cancellationException
        } catch (throwable: Throwable) {
            TermuxPendingResults.remove(executionId)
            logTermux(
                "dispatch failure duration_ms=${System.currentTimeMillis() - startedAt} " +
                    "message=${throwable.message.orEmpty()} command=${summarizeCommand(command)}",
            )
            buildSetupErrorResult(
                command = command,
                workingDirectory = workingDirectory,
                message = throwable.message ?: "Failed to dispatch command to Termux.",
                hint = "Check Termux permission, allow-external-apps, and battery restrictions.",
            )
        }
    }

    private fun startTermuxRunCommandService(request: Intent): ComponentName? {
        return try {
            ContextCompat.startForegroundService(context, request)
        } catch (throwable: Throwable) {
            logTermux("foreground service start failed message=${throwable.message.orEmpty()}")
            context.startService(request)
        }
    }

    @Suppress("DEPRECATION")
    private fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TermuxContract.PackageName, 0)
        true
    }.getOrDefault(false)

    private fun hasRunCommandPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            TermuxContract.RunCommandPermission,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun resolveTailBytes(arguments: JSONObject): Int {
        val requested = arguments.takeIf { it.has("tail_bytes") }?.optInt("tail_bytes")
            ?: arguments.takeIf { it.has("tailBytes") }?.optInt("tailBytes")
            ?: DefaultManagedLogTailBytes
        return requested.coerceIn(1, MaxManagedLogTailBytes)
    }

    private suspend fun prepareWorkingDirectoryIfNeeded(
        workingDirectory: String,
    ): JSONObject? {
        val normalizedWorkingDirectory = normalizeTermuxPath(workingDirectory)
        if (!shouldPrepareTermuxWorkingDirectory(normalizedWorkingDirectory)) return null

        val raw = try {
            JSONObject(
                dispatchCommand(
                    command = buildEnsureDirectoryScript(normalizedWorkingDirectory),
                    workingDirectory = TermuxContract.HomeDirectory,
                )
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: Throwable) {
            JSONObject().apply {
                put("ok", false)
                put("errmsg", "Termux returned unreadable output while preparing the workspace directory.")
            }
        }

        return raw.takeUnless { it.optBoolean("ok") }
    }

    private fun normalizeTermuxPath(path: String): String = when {
        path == "~" -> TermuxContract.HomeDirectory
        path.startsWith("~/") -> TermuxContract.HomeDirectory + path.removePrefix("~")
        else -> path
    }

    private fun buildEnsureDirectoryScript(
        workingDirectory: String,
    ): String = buildString {
        appendLine("set -euo pipefail")
        appendLine("path=\"\$(printf '%s' '${encodeBase64(workingDirectory)}' | base64 -d)\"")
        appendLine("mkdir -p \"\$path\"")
    }

    private fun isValidRunId(runId: String): Boolean =
        runId.isNotBlank() && runId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun buildManagedCommandResult(
        values: Map<String, String>,
        fallbackCommand: String,
        fallbackWorkingDirectory: String,
        fallbackRunId: String,
    ): String {
        if (!values["exists"].toBoolean()) {
            return JSONObject().apply {
                put("ok", false)
                put("status", "not_found")
                put("running", false)
                put("completed", true)
                put("run_id", fallbackRunId.ifBlank { values["run_id"].orEmpty() })
                put("command", fallbackCommand)
                put("working_directory", fallbackWorkingDirectory)
                put("stdout", "")
                put("stderr", "")
                put("exit_code", JSONObject.NULL)
                put("err", -1)
                put("errmsg", "No managed bash run was found for this run_id.")
            }.toString()
        }

        val command = decodeBase64(values["command_b64"].orEmpty()).ifBlank { fallbackCommand }
        val workingDirectory = decodeBase64(values["working_directory_b64"].orEmpty())
            .ifBlank { fallbackWorkingDirectory }
        val runId = values["run_id"].orEmpty().ifBlank { fallbackRunId }
        val status = values["status"].orEmpty().ifBlank { "unknown" }
        val stdout = decodeBase64(values["stdout_b64"].orEmpty())
        val stderr = decodeBase64(values["stderr_b64"].orEmpty())
        val stdoutBytes = values["stdout_bytes"]?.toLongOrNull()
            ?: stdout.toByteArray(Charsets.UTF_8).size.toLong()
        val stderrBytes = values["stderr_bytes"]?.toLongOrNull()
            ?: stderr.toByteArray(Charsets.UTF_8).size.toLong()
        val stdoutTruncated = values["stdout_truncated"].toBoolean()
        val stderrTruncated = values["stderr_truncated"].toBoolean()
        val durationMillis = values["duration_ms"]?.toLongOrNull() ?: 0L
        val pid = values["pid"]?.toIntOrNull()
        val childPid = values["child_pid"]?.toIntOrNull()
        val exitCode = values["exit_code"]?.toIntOrNull()
        val running = status == "running" || status == "launching"
        val completed = status in setOf("completed", "failed", "cancelled", "killed")
        val message = when (status) {
            "running" -> ""
            "launching" -> "Command is still starting."
            "completed" -> ""
            "failed" -> if (exitCode != null) {
                "Command exited with code $exitCode."
            } else {
                "Command failed."
            }
            "cancelled",
            "killed" -> "Command was stopped."
            else -> "Command status is unavailable."
        }
        val hint = when (status) {
            "running",
            "launching" -> "Command is still running."
            else -> ""
        }
        val ok = when (status) {
            "running",
            "launching" -> true
            "completed" -> exitCode == null || exitCode == 0
            else -> false
        }

        return JSONObject().apply {
            put("ok", ok)
            put("status", status)
            put("running", running)
            put("completed", completed)
            put("run_id", runId)
            put("command", command)
            put("working_directory", workingDirectory)
            put("duration_ms", durationMillis.coerceAtLeast(0L))
            put("stdout", stdout)
            put("stderr", stderr)
            put("stdout_bytes", stdoutBytes)
            put("stderr_bytes", stderrBytes)
            put("stdout_truncated", stdoutTruncated)
            put("stderr_truncated", stderrTruncated)
            if (pid != null) {
                put("pid", pid)
            }
            if (childPid != null) {
                put("child_pid", childPid)
            }
            if (exitCode != null) {
                put("exit_code", exitCode)
            } else {
                put("exit_code", JSONObject.NULL)
            }
            put("err", -1)
            put("errmsg", message)
            if (hint.isNotBlank()) {
                put("hint", hint)
            }
        }.toString()
    }

    private fun buildManagedToolError(
        command: String,
        workingDirectory: String,
        runId: String,
        message: String,
        hint: String = "",
        stdout: String = "",
        stderr: String = "",
    ): String = JSONObject().apply {
        put("ok", false)
        put("status", "error")
        put("running", false)
        put("completed", true)
        put("run_id", runId)
        put("command", command)
        put("working_directory", workingDirectory)
        put("stdout", stdout)
        put("stderr", stderr)
        put("exit_code", JSONObject.NULL)
        put("err", -1)
        put("errmsg", message)
        if (hint.isNotBlank()) {
            put("hint", hint)
        }
    }.toString()


    private fun buildPruneExpiredManagedBashRunsScript(retentionMillis: Long): String = buildString {
        appendManagedShellPreamble(this)
        appendLine("read_trimmed_file() {")
        appendLine("  local file_path=\"\$1\"")
        appendLine("  if [ ! -f \"\$file_path\" ]; then")
        appendLine("    printf ''")
        appendLine("    return 0")
        appendLine("  fi")
        appendLine("  tr -d '[:space:]' < \"\$file_path\"")
        appendLine("}")
        appendLine("bash_runs_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}'")
        appendLine("bash_runs_deleted=0")
        appendLine("bash_runs_retention_ms=${retentionMillis.coerceAtLeast(1L)}")
        appendLine("now_ms=\"\$(date +%s%3N)\"")
        appendLine("if [ -d \"\$bash_runs_dir\" ]; then")
        appendLine("  for run_dir in \"\$bash_runs_dir\"/*; do")
        appendLine("    [ -d \"\$run_dir\" ] || continue")
        appendLine("    state=\"\$(read_trimmed_file \"\$run_dir/state\")\"")
        appendLine("    case \"\$state\" in")
        appendLine("      completed|failed|cancelled|killed) ;;")
        appendLine("      *) continue ;;")
        appendLine("    esac")
        appendLine("    finished_at=\"\$(read_trimmed_file \"\$run_dir/finished_at\")\"")
        appendLine("    case \"\$finished_at\" in")
        appendLine("      ''|*[!0-9]*)")
        appendLine("        modified_seconds=\"\$(stat -c %Y \"\$run_dir\" 2>/dev/null || printf '0')\"")
        appendLine("        finished_at=\$((modified_seconds * 1000))")
        appendLine("        ;;")
        appendLine("    esac")
        appendLine("    age_ms=\$((now_ms - finished_at))")
        appendLine("    if [ \"\$age_ms\" -ge \"\$bash_runs_retention_ms\" ]; then")
        appendLine("      rm -rf -- \"\$run_dir\"")
        appendLine("      bash_runs_deleted=\$((bash_runs_deleted + 1))")
        appendLine("    fi")
        appendLine("  done")
        appendLine("fi")
        appendLine("emit_kv expired_bash_runs_deleted \"\$bash_runs_deleted\"")
    }

    private fun buildLaunchManagedCommandScript(
        runId: String,
        command: String,
        workingDirectory: String,
        tailBytes: Int,
        watchSeconds: Int = ManagedCommandWatchWindowSeconds,
    ): String = buildString {
        appendManagedShellPreamble(this)
        appendLine("run_id='${escapeForSingleQuoted(runId)}'")
        appendLine("run_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}/$runId'")
        appendLine("tail_bytes=$tailBytes")
        appendCommonManagedPaths(this)
        appendSnapshotHelpers(this)
        appendLine("mkdir -p \"\$run_dir\"")
        appendLine("printf '%s' '${encodeBase64(command)}' > \"\$command_meta_path\"")
        appendLine("printf '%s' '${encodeBase64(workingDirectory)}' > \"\$working_directory_meta_path\"")
        appendLine("printf '%s' \"\$(decode_b64 '${encodeBase64(command)}')\" > \"\$command_path\"")
        appendLine("chmod 700 \"\$command_path\"")
        appendLine(": > \"\$stdout_path\"")
        appendLine(": > \"\$stderr_path\"")
        appendLine("printf '%s' 'launching' > \"\$state_path\"")
        appendLine("rm -f \"\$pid_path\" \"\$child_pid_path\" \"\$exit_code_path\" \"\$finished_path\"")
        appendLine("cat > \"\$runner_path\" <<'AETHER_RUNNER'")
        appendRunnerScript(
            builder = this,
            runId = runId,
            workingDirectory = workingDirectory,
        )
        appendLine("AETHER_RUNNER")
        appendLine("chmod 700 \"\$runner_path\"")
        appendLine("nohup \"\$runner_path\" >/dev/null 2>&1 </dev/null &")
        appendLine("printf '%s' \"\$!\" > \"\$pid_path\"")
        appendLine("elapsed=0")
        appendLine("while [ \"\$elapsed\" -lt ${watchSeconds.coerceAtLeast(0)} ]; do")
        appendLine("  state=\"\$(cat \"\$state_path\" 2>/dev/null || printf 'launching')\"")
        appendLine("  if [ \"\$state\" != 'launching' ] && [ \"\$state\" != 'running' ]; then")
        appendLine("    break")
        appendLine("  fi")
        appendLine("  sleep 1")
        appendLine("  elapsed=\$((elapsed + 1))")
        appendLine("done")
        appendLine("emit_managed_snapshot")
    }

    private fun buildInspectManagedCommandScript(
        runId: String,
        tailBytes: Int,
    ): String = buildString {
        appendManagedShellPreamble(this)
        appendLine("run_id='${escapeForSingleQuoted(runId)}'")
        appendLine("run_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}/$runId'")
        appendLine("tail_bytes=$tailBytes")
        appendCommonManagedPaths(this)
        appendSnapshotHelpers(this)
        appendLine("emit_managed_snapshot")
    }

    private fun buildReadManagedLogScript(
        runId: String,
        offset: Long,
    ): String = buildString {
        appendManagedShellPreamble(this)
        appendLine("run_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}/$runId'")
        appendLine("offset=${offset.coerceAtLeast(0L)}")
        appendCommonManagedPaths(this)
        appendLine("if [ ! -d \"\$run_dir\" ]; then")
        appendLine("  emit_kv exists false")
        appendLine("  exit 0")
        appendLine("fi")
        appendLine("total_bytes=\"\$(file_bytes \"\$stdout_path\")\"")
        appendLine("emit_kv exists true")
        appendLine("emit_kv status \"\$(cat \"\$state_path\" 2>/dev/null || printf 'unknown')\"")
        appendLine("emit_kv exit_code \"\$(read_file_trimmed \"\$exit_code_path\")\"")
        appendLine("emit_kv total_bytes \"\$total_bytes\"")
        appendLine("if [ \"\$offset\" -lt \"\$total_bytes\" ]; then")
        appendLine("  data_b64=\"\$(tail -c +\$((offset + 1)) -- \"\$stdout_path\" | head -c 65536 | base64 | tr -d '\\n')\"")
        appendLine("else")
        appendLine("  data_b64=''")
        appendLine("fi")
        appendLine("emit_kv data_b64 \"\$data_b64\"")
    }

    private fun buildKillManagedCommandScript(
        runId: String,
        tailBytes: Int,
    ): String = buildString {
        appendManagedShellPreamble(this)
        appendLine("run_id='${escapeForSingleQuoted(runId)}'")
        appendLine("run_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}/$runId'")
        appendLine("tail_bytes=$tailBytes")
        appendCommonManagedPaths(this)
        appendSnapshotHelpers(this)
        appendLine("if [ ! -d \"\$run_dir\" ]; then")
        appendLine("  emit_kv exists false")
        appendLine("  emit_kv run_id \"\$run_id\"")
        appendLine("  exit 0")
        appendLine("fi")
        appendLine("state=\"\$(cat \"\$state_path\" 2>/dev/null || printf 'unknown')\"")
        appendLine("if [ \"\$state\" = 'running' ] || [ \"\$state\" = 'launching' ]; then")
        appendLine("  child_pid=\"\$(read_file_trimmed \"\$child_pid_path\")\"")
        appendLine("  runner_pid=\"\$(read_file_trimmed \"\$pid_path\")\"")
        appendLine("  kill_pid_tree \"\$child_pid\" TERM")
        appendLine("  kill_pid_tree \"\$runner_pid\" TERM")
        appendLine("  sleep 1")
        appendLine("  kill_pid_tree \"\$child_pid\" KILL")
        appendLine("  kill_pid_tree \"\$runner_pid\" KILL")
        appendLine("  printf '%s' 'cancelled' > \"\$state_path\"")
        appendLine("  printf '%s' '143' > \"\$exit_code_path\"")
        appendLine("  now_ms > \"\$finished_path\"")
        appendLine("fi")
        appendLine("emit_managed_snapshot")
    }

    private fun appendManagedShellPreamble(builder: StringBuilder) {
        builder.appendLine("set -euo pipefail")
        builder.appendLine("decode_b64() {")
        builder.appendLine("  printf '%s' \"\$1\" | base64 -d")
        builder.appendLine("}")
        builder.appendLine("emit_kv() {")
        builder.appendLine("  printf '%s=%s\\n' \"\$1\" \"\$2\"")
        builder.appendLine("}")
        builder.appendLine("read_file_trimmed() {")
        builder.appendLine("  local file_path=\"\$1\"")
        builder.appendLine("  if [ ! -f \"\$file_path\" ]; then")
        builder.appendLine("    printf ''")
        builder.appendLine("    return 0")
        builder.appendLine("  fi")
        builder.appendLine("  tr -d '[:space:]' < \"\$file_path\"")
        builder.appendLine("}")
        builder.appendLine("file_bytes() {")
        builder.appendLine("  local file_path=\"\$1\"")
        builder.appendLine("  if [ ! -f \"\$file_path\" ]; then")
        builder.appendLine("    printf '0'")
        builder.appendLine("    return 0")
        builder.appendLine("  fi")
        builder.appendLine("  wc -c < \"\$file_path\" | tr -d '[:space:]'")
        builder.appendLine("}")
        builder.appendLine("tail_file_b64() {")
        builder.appendLine("  local file_path=\"\$1\"")
        builder.appendLine("  local max_bytes=\"\$2\"")
        builder.appendLine("  if [ ! -f \"\$file_path\" ]; then")
        builder.appendLine("    printf ''")
        builder.appendLine("    return 0")
        builder.appendLine("  fi")
        builder.appendLine("  local value")
        builder.appendLine("  value=\"\$(tail -c \"\$max_bytes\" -- \"\$file_path\" 2>/dev/null; printf '\\037')\"")
        builder.appendLine("  value=\"\${value%\$'\\037'}\"")
        builder.appendLine("  printf '%s' \"\$value\" | base64 | tr -d '\\n'")
        builder.appendLine("}")
        builder.appendLine("now_ms() {")
        builder.appendLine("  date +%s%3N")
        builder.appendLine("}")
        builder.appendLine("kill_pid_tree() {")
        builder.appendLine("  local pid=\"\$1\"")
        builder.appendLine("  local signal=\"\$2\"")
        builder.appendLine("  if [ -z \"\$pid\" ]; then")
        builder.appendLine("    return 0")
        builder.appendLine("  fi")
        builder.appendLine("  if command -v pkill >/dev/null 2>&1; then")
        builder.appendLine("    pkill -\"\$signal\" -P \"\$pid\" 2>/dev/null || true")
        builder.appendLine("  fi")
        builder.appendLine("  kill -\"\$signal\" \"\$pid\" 2>/dev/null || true")
        builder.appendLine("}")
    }

    private fun appendCommonManagedPaths(builder: StringBuilder) {
        builder.appendLine("command_path=\"\$run_dir/command.sh\"")
        builder.appendLine("runner_path=\"\$run_dir/runner.sh\"")
        builder.appendLine("command_meta_path=\"\$run_dir/command.b64\"")
        builder.appendLine("working_directory_meta_path=\"\$run_dir/working_directory.b64\"")
        builder.appendLine("stdout_path=\"\$run_dir/combined.log\"")
        builder.appendLine("stderr_path=\"\$run_dir/stderr.log\"")
        builder.appendLine("state_path=\"\$run_dir/state\"")
        builder.appendLine("pid_path=\"\$run_dir/pid\"")
        builder.appendLine("child_pid_path=\"\$run_dir/child_pid\"")
        builder.appendLine("exit_code_path=\"\$run_dir/exit_code\"")
        builder.appendLine("started_path=\"\$run_dir/started_at\"")
        builder.appendLine("finished_path=\"\$run_dir/finished_at\"")
    }

    private fun appendSnapshotHelpers(builder: StringBuilder) {
        builder.appendLine("emit_managed_snapshot() {")
        builder.appendLine("  if [ ! -d \"\$run_dir\" ]; then")
        builder.appendLine("    emit_kv exists false")
        builder.appendLine("    emit_kv run_id \"\$run_id\"")
        builder.appendLine("    return 0")
        builder.appendLine("  fi")
        builder.appendLine("  command_b64=\"\$(read_file_trimmed \"\$command_meta_path\")\"")
        builder.appendLine("  working_directory_b64=\"\$(read_file_trimmed \"\$working_directory_meta_path\")\"")
        builder.appendLine("  status=\"\$(cat \"\$state_path\" 2>/dev/null || printf 'unknown')\"")
        builder.appendLine("  stdout_bytes=\"\$(file_bytes \"\$stdout_path\")\"")
        builder.appendLine("  stderr_bytes=\"\$(file_bytes \"\$stderr_path\")\"")
        builder.appendLine("  stdout_truncated=false")
        builder.appendLine("  stderr_truncated=false")
        builder.appendLine("  if [ \"\$stdout_bytes\" -gt \"\$tail_bytes\" ]; then")
        builder.appendLine("    stdout_truncated=true")
        builder.appendLine("  fi")
        builder.appendLine("  if [ \"\$stderr_bytes\" -gt \"\$tail_bytes\" ]; then")
        builder.appendLine("    stderr_truncated=true")
        builder.appendLine("  fi")
        builder.appendLine("  started_at=\"\$(read_file_trimmed \"\$started_path\")\"")
        builder.appendLine("  finished_at=\"\$(read_file_trimmed \"\$finished_path\")\"")
        builder.appendLine("  if [ -n \"\$started_at\" ]; then")
        builder.appendLine("    if [ -n \"\$finished_at\" ]; then")
        builder.appendLine("      duration_ms=\$((finished_at - started_at))")
        builder.appendLine("    else")
        builder.appendLine("      duration_ms=\$((\$(now_ms) - started_at))")
        builder.appendLine("    fi")
        builder.appendLine("  else")
        builder.appendLine("    duration_ms=0")
        builder.appendLine("  fi")
        builder.appendLine("  emit_kv exists true")
        builder.appendLine("  emit_kv run_id \"\$run_id\"")
        builder.appendLine("  emit_kv command_b64 \"\$command_b64\"")
        builder.appendLine("  emit_kv working_directory_b64 \"\$working_directory_b64\"")
        builder.appendLine("  emit_kv status \"\$status\"")
        builder.appendLine("  emit_kv pid \"\$(read_file_trimmed \"\$pid_path\")\"")
        builder.appendLine("  emit_kv child_pid \"\$(read_file_trimmed \"\$child_pid_path\")\"")
        builder.appendLine("  emit_kv exit_code \"\$(read_file_trimmed \"\$exit_code_path\")\"")
        builder.appendLine("  emit_kv stdout_b64 \"\$(tail_file_b64 \"\$stdout_path\" \"\$tail_bytes\")\"")
        builder.appendLine("  emit_kv stderr_b64 \"\$(tail_file_b64 \"\$stderr_path\" \"\$tail_bytes\")\"")
        builder.appendLine("  emit_kv stdout_bytes \"\$stdout_bytes\"")
        builder.appendLine("  emit_kv stderr_bytes \"\$stderr_bytes\"")
        builder.appendLine("  emit_kv stdout_truncated \"\$stdout_truncated\"")
        builder.appendLine("  emit_kv stderr_truncated \"\$stderr_truncated\"")
        builder.appendLine("  emit_kv duration_ms \"\$duration_ms\"")
        builder.appendLine("}")
    }

    private fun appendRunnerScript(
        builder: StringBuilder,
        runId: String,
        workingDirectory: String,
    ) {
        builder.appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        builder.appendLine("set -euo pipefail")
        appendTermuxShellEnvironment(
            builder = builder,
            includeShellStartupPath = true,
            environmentVariables = environmentVariables.get(),
        )
        builder.appendLine("now_ms() {")
        builder.appendLine("  date +%s%3N")
        builder.appendLine("}")
        builder.appendLine("run_dir='${escapeForSingleQuoted(TermuxContract.ManagedCommandsDirectory)}/$runId'")
        builder.appendLine("working_directory=\"\$(printf '%s' '${encodeBase64(workingDirectory)}' | base64 -d)\"")
        builder.appendLine("stdout_path=\"\$run_dir/combined.log\"")
        builder.appendLine("stderr_path=\"\$run_dir/stderr.log\"")
        builder.appendLine("state_path=\"\$run_dir/state\"")
        builder.appendLine("child_pid_path=\"\$run_dir/child_pid\"")
        builder.appendLine("exit_code_path=\"\$run_dir/exit_code\"")
        builder.appendLine("started_path=\"\$run_dir/started_at\"")
        builder.appendLine("finished_path=\"\$run_dir/finished_at\"")
        builder.appendLine("command_path=\"\$run_dir/command.sh\"")
        builder.appendLine("printf '%s' 'running' > \"\$state_path\"")
        builder.appendLine("now_ms > \"\$started_path\"")
        builder.appendLine("if ! cd \"\$working_directory\"; then")
        builder.appendLine("  printf 'Failed to enter working directory: %s\\n' \"\$working_directory\" > \"\$stderr_path\"")
        builder.appendLine("  printf '%s' 'failed' > \"\$state_path\"")
        builder.appendLine("  printf '%s' '1' > \"\$exit_code_path\"")
        builder.appendLine("  now_ms > \"\$finished_path\"")
        builder.appendLine("  exit 1")
        builder.appendLine("fi")
        builder.appendLine("bash \"\$command_path\" > \"\$stdout_path\" 2>&1 &")
        builder.appendLine("child_pid=\$!")
        builder.appendLine("printf '%s' \"\$child_pid\" > \"\$child_pid_path\"")
        builder.appendLine("set +e")
        builder.appendLine("wait \"\$child_pid\"")
        builder.appendLine("exit_code=\$?")
        builder.appendLine("set -e")
        builder.appendLine("current_state=\"\$(cat \"\$state_path\" 2>/dev/null || printf '')\"")
        builder.appendLine("if [ \"\$current_state\" = 'cancelled' ] || [ \"\$current_state\" = 'killed' ]; then")
        builder.appendLine("  if [ ! -f \"\$exit_code_path\" ]; then")
        builder.appendLine("    printf '%s' \"\$exit_code\" > \"\$exit_code_path\"")
        builder.appendLine("  fi")
        builder.appendLine("else")
        builder.appendLine("  printf '%s' \"\$exit_code\" > \"\$exit_code_path\"")
        builder.appendLine("  if [ \"\$exit_code\" -eq 0 ]; then")
        builder.appendLine("    printf '%s' 'completed' > \"\$state_path\"")
        builder.appendLine("  else")
        builder.appendLine("    printf '%s' 'failed' > \"\$state_path\"")
        builder.appendLine("  fi")
        builder.appendLine("fi")
        builder.appendLine("if [ ! -f \"\$finished_path\" ]; then")
        builder.appendLine("  now_ms > \"\$finished_path\"")
        builder.appendLine("fi")
        builder.appendLine("exit \"\$exit_code\"")
    }

    private fun buildCommandResult(
        command: String,
        workingDirectory: String,
        durationMillis: Long,
        result: TermuxCommandResult,
    ): String = JSONObject().apply {
        put("ok", result.err == -1 && result.exitCode == 0)
        put("command", command)
        put("working_directory", workingDirectory)
        put("duration_ms", durationMillis.coerceAtLeast(0L))
        put("stdout", result.stdout)
        put("stderr", result.stderr)
        put("exit_code", result.exitCode)
        put("err", result.err)
        put("errmsg", result.errmsg)
    }.toString()

    private fun buildSetupErrorResult(
        command: String,
        workingDirectory: String,
        message: String,
        hint: String,
    ): String = JSONObject().apply {
        put("ok", false)
        put("command", command)
        put("working_directory", workingDirectory)
        put("stdout", "")
        put("stderr", "")
        put("exit_code", -1)
        put("err", -1)
        put("errmsg", message)
        put("hint", hint)
    }.toString()

    private fun buildInvalidArgumentsResult(message: String): String = JSONObject().apply {
        put("ok", false)
        put("errmsg", message)
    }.toString()

    private fun parseKeyValueOutput(stdout: String): Map<String, String> = buildMap {
        stdout.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) return@forEach
                put(
                    line.substring(0, separatorIndex),
                    line.substring(separatorIndex + 1),
                )
            }
    }

    private fun encodeBase64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeBase64(value: String): String =
        if (value.isBlank()) {
            ""
        } else {
            runCatching { String(Base64.getDecoder().decode(value), Charsets.UTF_8) }
                .getOrDefault("")
        }

    private fun escapeForSingleQuoted(value: String): String =
        value.replace("'", "'\"'\"'")

    private fun summarizeCommand(command: String): String =
        command
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(120)

private fun logTermux(message: String) {
    diagnosticLogger.event(
        category = "termux",
        event = "trace",
        details = mapOf("message" to message),
    )
    if (EnableTermuxLogging) {
        Log.d(TermuxLogTag, message)
    }
}
}

internal data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val err: Int,
    val errmsg: String,
)

internal object TermuxPendingResults {
    private val nextId = AtomicInteger(1000)
    private val pending = LinkedHashMap<Int, CompletableDeferred<TermuxCommandResult>>()

    fun nextExecutionId(): Int = nextId.incrementAndGet()

    fun register(executionId: Int): CompletableDeferred<TermuxCommandResult> {
        val deferred = CompletableDeferred<TermuxCommandResult>()
        synchronized(pending) {
            pending[executionId] = deferred
        }
        deferred.invokeOnCompletion {
            synchronized(pending) {
                pending.remove(executionId)
            }
        }
        return deferred
    }

    fun complete(
        executionId: Int,
        result: TermuxCommandResult,
    ) {
        synchronized(pending) {
            pending.remove(executionId)?.complete(result)
        }
    }

    fun remove(executionId: Int) {
        synchronized(pending) {
            pending.remove(executionId)?.cancel()
        }
    }
}

internal object TermuxManagedRuns {
    private val nextId = AtomicInteger(1)

    fun nextRunId(): String = "run-${System.currentTimeMillis()}-${nextId.getAndIncrement()}"
}

private fun buildTermuxDispatchScript(
    command: String,
    environmentVariables: List<TermuxEnvironmentVariable>,
): String = buildString {
    appendTermuxShellEnvironment(
        builder = this,
        includeShellStartupPath = false,
        environmentVariables = environmentVariables,
    )
    appendLine(command)
}

internal fun appendTermuxShellEnvironment(
    builder: StringBuilder,
    includeShellStartupPath: Boolean,
    environmentVariables: List<TermuxEnvironmentVariable> = emptyList(),
) {
    builder.appendLine("export PREFIX='${TermuxContract.PrefixDirectory}'")
    builder.appendLine("export HOME='${TermuxContract.HomeDirectory}'")
    builder.appendLine("export TMPDIR=\"\$PREFIX/tmp\"")
    builder.appendLine("export PATH=\"\${PATH:-}\"")
    builder.appendLine("aether_dedupe_path() {")
    builder.appendLine("  local old_path=\"\$PATH\"")
    builder.appendLine("  local entry")
    builder.appendLine("  PATH=''")
    builder.appendLine("  while [ -n \"\$old_path\" ]; do")
    builder.appendLine("    entry=\"\${old_path%%:*}\"")
    builder.appendLine("    if [ \"\$old_path\" = \"\$entry\" ]; then")
    builder.appendLine("      old_path=''")
    builder.appendLine("    else")
    builder.appendLine("      old_path=\"\${old_path#*:}\"")
    builder.appendLine("    fi")
    builder.appendLine("    [ -n \"\$entry\" ] || continue")
    builder.appendLine("    case \":\$PATH:\" in")
    builder.appendLine("      *\":\$entry:\"*) ;;")
    builder.appendLine("      *) PATH=\"\${PATH:+\$PATH:}\$entry\" ;;")
    builder.appendLine("    esac")
    builder.appendLine("  done")
    builder.appendLine("}")
    builder.appendLine("PATH=\"\$HOME/.local/bin:\$HOME/bin:\$HOME/.cargo/bin:\$HOME/go/bin:\$PREFIX/bin:\$PREFIX/bin/applets:\$PATH\"")
    builder.appendLine("aether_dedupe_path")
    if (includeShellStartupPath) {
        builder.appendLine("aether_capture_shell_path() {")
        builder.appendLine("  local mode=\"\$1\"")
        builder.appendLine("  local captured=''")
        builder.appendLine("  local captured_path=''")
        builder.appendLine("  command -v timeout >/dev/null 2>&1 || return 0")
        builder.appendLine("  captured=\"\$(timeout 3 \"\$PREFIX/bin/bash\" \"\$mode\" 'printf \"\\036%s\" \"\$PATH\"' 2>/dev/null || true)\"")
        builder.appendLine("  case \"\$captured\" in")
        builder.appendLine("    *\$'\\036'*) captured_path=\"\${captured##*\$'\\036'}\" ;;")
        builder.appendLine("    *) captured_path='' ;;")
        builder.appendLine("  esac")
        builder.appendLine("  [ -n \"\$captured_path\" ] || return 0")
        builder.appendLine("  PATH=\"\$captured_path:\$PATH\"")
        builder.appendLine("  aether_dedupe_path")
        builder.appendLine("}")
        builder.appendLine("aether_capture_shell_path -lc")
        builder.appendLine("aether_capture_shell_path -ic")
        builder.appendLine("unset -f aether_capture_shell_path")
    }
    builder.appendLine("export PATH")
    normalizeTermuxEnvironmentVariables(environmentVariables).forEach { variable ->
        builder.appendLine("export ${variable.name}='${escapeTermuxEnvValue(variable.value)}'")
    }
    builder.appendLine("unset -f aether_dedupe_path")
}

private fun escapeTermuxEnvValue(value: String): String =
    value.replace("'", "'\"'\"'")

internal fun shouldPrepareTermuxWorkingDirectory(
    workingDirectory: String,
): Boolean = workingDirectory == SharedWorkspaceRoot ||
    workingDirectory.startsWith("$SharedWorkspaceRoot/") ||
    workingDirectory == SessionWorkspaceRoot ||
    workingDirectory.startsWith("$SessionWorkspaceRoot/")

internal object TermuxContract {
    const val PackageName = "com.termux"
    const val RunCommandPermission = "com.termux.permission.RUN_COMMAND"
    const val RunCommandService = "com.termux.app.RunCommandService"
    const val RunCommandAction = "com.termux.RUN_COMMAND"
    const val RunCommandPathExtra = "com.termux.RUN_COMMAND_PATH"
    const val RunCommandArgumentsExtra = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val RunCommandStdinExtra = "com.termux.RUN_COMMAND_STDIN"
    const val RunCommandWorkdirExtra = "com.termux.RUN_COMMAND_WORKDIR"
    const val RunCommandBackgroundExtra = "com.termux.RUN_COMMAND_BACKGROUND"
    const val RunCommandPendingIntentExtra = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val RunCommandLabelExtra = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val RunCommandDescriptionExtra = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    const val TermuxSettingsActivity = "com.termux.app.activities.SettingsActivity"
    const val TermuxHomeActivity = "com.termux.HomeActivity"
    const val ResultBundleExtra = "result"
    const val ResultStdoutExtra = "stdout"
    const val ResultStderrExtra = "stderr"
    const val ResultExitCodeExtra = "exitCode"
    const val ResultErrExtra = "err"
    const val ResultErrmsgExtra = "errmsg"
    const val ExecutionIdExtra = "com.baimoqilin.aether.termux.EXECUTION_ID"
    const val PrefixDirectory = "/data/data/com.termux/files/usr"
    const val BashPath = "$PrefixDirectory/bin/bash"
    const val HomeDirectory = "/data/data/com.termux/files/home"
    const val ManagedCommandsDirectory = "$HomeDirectory/.aether/bash-runs"
    const val ExternalAppsSetupCommand =
        "mkdir -p ~/.termux && touch ~/.termux/termux.properties && if grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' ~/.termux/termux.properties; then sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' ~/.termux/termux.properties; else printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; fi && termux-reload-settings"
}
