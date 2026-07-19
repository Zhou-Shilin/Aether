package com.zhousl.aether.runtime

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import com.zhousl.aether.data.AetherDiagnosticLogger
import com.zhousl.aether.data.AlpineEnvironmentVariable
import com.zhousl.aether.data.LocalRuntimeId
import com.zhousl.aether.data.normalizeAlpineEnvironmentVariables
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val AlpineWatchWindowMillis = 45_000L
private const val AlpineDefaultTailBytes = 12 * 1024
private const val AlpineMaxTailBytes = 64 * 1024
private const val AlpineNetworkRateWindowMillis = 5_000L
private const val AlpineAssetRoot = "runtimes/alpine/arm64-v8a"
private const val AlpineHostLinker = "/system/bin/linker64"
private val AlpineRootfsAssetCandidates = listOf(
    RootfsAsset("$AlpineAssetRoot/rootfs.tar.gz", compressed = true),
    RootfsAsset("$AlpineAssetRoot/rootfs.tar", compressed = false),
)

class AlpineRuntime(
    context: Context,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) : LocalRuntime {
    private val appContext = context.applicationContext
    private val runtimeRoot = File(appContext.filesDir, "runtimes/alpine")
    private val rootfsDir = File(runtimeRoot, "rootfs")
    private val workspaceDir = File(runtimeRoot, "workspace")
    private val hostBinDir = File(runtimeRoot, "bin")
    private val hostLibDir = File(runtimeRoot, "lib")
    private val hostTmpDir = File(runtimeRoot, "tmp")
    private val loaderDir = File(runtimeRoot, "libexec/proot")
    private val loaderFile = File(loaderDir, "loader")
    private val prootFile = File(hostBinDir, "proot")
    private val libTallocFile = File(hostLibDir, "libtalloc.so.2")
    private val runs = ConcurrentHashMap<String, AlpineRun>()
    private val nextRunId = AtomicInteger(1)
    @Volatile
    private var environmentVariables: List<AlpineEnvironmentVariable> = emptyList()

    override val id: LocalRuntimeId = LocalRuntimeId.Alpine
    override val displayName: String = "Alpine"
    override val homeDirectory: String = "/root"
    override val workspaceRoot: String = "/workspace"
    override val managedCommandsDirectory: String = "/root/.aether/bash-runs"

    internal fun resolveWorkspaceHostPath(
        path: String,
        workingDirectory: String = workspaceRoot,
    ): AlpineWorkspaceHostPath? {
        val normalizedWorkingDirectory = normalizePath(workingDirectory.trim())
            .ifBlank { workspaceRoot }
        val normalizedGuestPath = when {
            path.isBlank() -> normalizedWorkingDirectory
            path.startsWith("/") -> java.nio.file.Paths.get(path).normalize().toString()
            else -> java.nio.file.Paths.get(normalizedWorkingDirectory).resolve(path).normalize().toString()
        }
        if (
            normalizedGuestPath != workspaceRoot &&
            !normalizedGuestPath.startsWith("$workspaceRoot/")
        ) {
            return null
        }
        val relativePath = normalizedGuestPath.removePrefix(workspaceRoot).trimStart('/')
        val canonicalWorkspace = workspaceDir.canonicalFile
        val hostFile = File(canonicalWorkspace, relativePath).canonicalFile
        if (
            hostFile != canonicalWorkspace &&
            !hostFile.path.startsWith("${canonicalWorkspace.path}${File.separator}")
        ) {
            return null
        }
        return AlpineWorkspaceHostPath(
            guestPath = normalizedGuestPath,
            hostFile = hostFile,
        )
    }

    fun setEnvironmentVariables(variables: List<AlpineEnvironmentVariable>) {
        environmentVariables = normalizeAlpineEnvironmentVariables(variables)
    }

    suspend fun initialize(
        onProgress: (AlpineSetupProgress) -> Unit = {},
    ): LocalRuntimeSetupState = withContext(Dispatchers.IO) {
        inspectSetup().let { state ->
            if (state.issue != LocalRuntimeIssue.NotInstalled &&
                state.issue != LocalRuntimeIssue.MissingAssets
            ) {
                return@withContext state
            }
        }
        if (!isSupportedAbi()) return@withContext unsupportedAbiState()
        if (!hasBundledRuntimeAssets()) {
            return@withContext LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.MissingAssets,
                detail = "Alpine runtime assets are not bundled in this build.",
            )
        }
        runCatching {
            installFromAssets(onProgress)
        }.fold(
            onSuccess = {
                inspectSetup().let { state ->
                    if (state.isReady) {
                        state.copy(detail = "Alpine runtime is ready.")
                    } else {
                        state
                    }
                }
            },
            onFailure = { throwable ->
                LocalRuntimeSetupState(
                    runtimeId = id,
                    issue = LocalRuntimeIssue.Failed,
                    detail = throwable.message ?: "Failed to install Alpine runtime.",
                )
            },
        )
    }

    suspend fun reset(): LocalRuntimeSetupState = withContext(Dispatchers.IO) {
        runtimeRoot.deleteRecursively()
        LocalRuntimeSetupState(
            runtimeId = id,
            issue = LocalRuntimeIssue.NotInstalled,
            detail = "Alpine runtime data was reset.",
        )
    }

    suspend fun createTerminalLaunchSpec(): AlpineTerminalLaunchSpec = withContext(Dispatchers.IO) {
        requireReady()
        ensureWorkspace()
        ensureGuestNetworkConfig()
        val command = buildAlpineInteractiveCommand()
        AlpineTerminalLaunchSpec(
            executable = command.first(),
            arguments = command.toTypedArray(),
            environment = buildAlpineProcessEnvironment()
                .map { (key, value) -> "$key=$value" }
                .toTypedArray(),
            workingDirectory = runtimeRoot.absolutePath,
        )
    }

    suspend fun installAsset(
        assetPath: String,
        guestPath: String,
        executable: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        requireReady()
        val normalizedGuestPath = normalizePath(guestPath)
        val target = guestPathToHostFile(normalizedGuestPath)
        copyAsset(assetPath, target, executable)
        target
    }

    internal suspend fun ensureGuestDirectory(guestPath: String): File = withContext(Dispatchers.IO) {
        requireReady()
        guestPathToHostFile(normalizePath(guestPath)).apply {
            require(mkdirs() || isDirectory) {
                "Unable to create Alpine directory: $guestPath"
            }
        }
    }

    internal suspend fun resolveGuestPath(guestPath: String): File = withContext(Dispatchers.IO) {
        requireReady()
        guestPathToHostFile(normalizePath(guestPath))
    }

    internal fun resolveManagedGuestPath(guestPath: String): File =
        guestPathToHostFile(normalizePath(guestPath))

    suspend fun startManagedProcess(
        command: String,
        workingDirectory: String = homeDirectory,
        redirectErrorStream: Boolean = false,
    ): Process = withContext(Dispatchers.IO) {
        val normalizedWorkingDirectory = normalizePath(workingDirectory)
        requireReady()
        ensureWorkspace()
        ensureGuestNetworkConfig()
        buildAlpineProcess(command, normalizedWorkingDirectory)
            .redirectErrorStream(redirectErrorStream)
            .start()
    }

    private suspend fun requireReady() {
        val setup = inspectSetup()
        if (!setup.isReady) {
            error(setup.detail.ifBlank { "Alpine runtime is not ready." })
        }
    }

    suspend fun installPackageProfile(
        profileId: String,
        onProgress: (AlpineSetupProgress) -> Unit = {},
    ): LocalRuntimeSetupState {
        val packages = AlpinePackageProfiles[profileId]
            ?: return LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Unknown Alpine package profile: $profileId",
            )
        val setup = inspectSetup()
        if (!setup.isReady) return setup
        val command = packageProfileInstallCommand(profileId)
            ?: return LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Unknown Alpine package profile: $profileId",
            )
        val progressTracker = AlpinePackageInstallProgressTracker()
        onProgress(progressTracker.onOutput("\$ $command\n"))
        val rateSampler = NetworkRateSampler { bytesPerSecond ->
            onProgress(progressTracker.onRate(bytesPerSecond))
        }
        rateSampler.start()
        val result = try {
            JSONObject(
                executeCommand(
                    command = command,
                    workingDirectory = homeDirectory,
                    awaitTimeoutMillis = 10 * 60 * 1000L,
                    onOutput = { output ->
                        onProgress(progressTracker.onOutput(output))
                    },
                )
            )
        } finally {
            rateSampler.stop()
        }
        return if (result.optBoolean("ok") || verifyPackageProfile(profileId)) {
            LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Ready,
                detail = "Installed Alpine profile $profileId.",
            )
        } else {
            LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = result.optString("errmsg").ifBlank { result.optString("stderr") },
            )
        }
    }

    fun packageProfileInstallCommand(profileId: String): String? {
        val packages = AlpinePackageProfiles[profileId] ?: return null
        return "apk add --no-cache --no-chown ${packages.joinToString(" ")}"
    }

    suspend fun isPackageProfileInstalled(profileId: String): Boolean =
        verifyPackageProfile(profileId)

    override suspend fun inspectSetup(): LocalRuntimeSetupState = withContext(Dispatchers.IO) {
        when {
            !isSupportedAbi() -> unsupportedAbiState()
            !hasBundledRuntimeAssets() -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.MissingAssets,
                detail = "This build does not include Alpine proot/rootfs assets.",
            )
            !rootfsDir.isDirectory -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.NotInstalled,
                detail = "Alpine rootfs is not installed.",
            )
            !prootFile.isFile || !loaderFile.isFile || !libTallocFile.isFile -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Alpine host runtime is incomplete.",
            )
            !File(AlpineHostLinker).isFile -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Android dynamic linker is unavailable: $AlpineHostLinker",
            )
            !File(rootfsDir, "bin/sh").existsNoFollow() -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Alpine rootfs is incomplete: /bin/sh is missing.",
            )
            !File(rootfsDir, "etc/alpine-release").existsNoFollow() -> LocalRuntimeSetupState(
                runtimeId = id,
                issue = LocalRuntimeIssue.Failed,
                detail = "Alpine rootfs is incomplete: /etc/alpine-release is missing.",
            )
            else -> LocalRuntimeSetupState(LocalRuntimeId.Alpine, LocalRuntimeIssue.Ready)
        }
    }

    override suspend fun execute(
        argumentsJson: String,
        onProgress: (suspend (String) -> Unit)?,
    ): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return@withContext invalidArguments("Arguments were not valid JSON.")
        val command = arguments.optString("command").trim()
        val workingDirectory = normalizePath(
            arguments.optString("working_directory").trim()
                .ifBlank { arguments.optString("workingDirectory").trim() }
                .ifBlank { homeDirectory }
        )
        if (command.isBlank()) return@withContext invalidArguments("Missing required 'command' argument.")

        val setup = inspectSetup()
        if (!setup.isReady) return@withContext setupError(command, workingDirectory, setup)

        ensureWorkspace()
        ensureGuestNetworkConfig()
        val runId = nextRunId()
        val runDir = File(runtimeRoot, "runs/$runId").apply { mkdirs() }
        val stdoutFile = File(runDir, "stdout.log")
        val stderrFile = File(runDir, "stderr.log")
        val run = AlpineRun(
            runId = runId,
            command = command,
            workingDirectory = workingDirectory,
            startedAtMillis = System.currentTimeMillis(),
            stdoutFile = stdoutFile,
            stderrFile = stderrFile,
        )
        runs[runId] = run

        val process = runCatching {
            buildAlpineProcess(command, workingDirectory)
                .redirectOutput(stdoutFile)
                .redirectError(stderrFile)
                .start()
        }.getOrElse { throwable ->
            runs.remove(runId)
            return@withContext commandError(
                command = command,
                workingDirectory = workingDirectory,
                runId = runId,
                message = throwable.message ?: "Failed to start Alpine command.",
            )
        }
        run.process = process
        AlpineRunReaper.watch(run, runs)

        try {
            val deadline = System.currentTimeMillis() + AlpineWatchWindowMillis
            while (System.currentTimeMillis() < deadline && process.isAlive) {
                delay(1_000L)
                onProgress?.invoke(snapshot(run, AlpineDefaultTailBytes))
            }
            snapshot(run, AlpineDefaultTailBytes)
        } catch (cancellationException: CancellationException) {
            runCatching { killExecutionByRunId(runId) }
            throw cancellationException
        }
    }

    override suspend fun fetchExecution(argumentsJson: String): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return@withContext invalidArguments("Arguments were not valid JSON.")
        val runId = arguments.optString("run_id").trim()
            .ifBlank { arguments.optString("runId").trim() }
        if (runId.isBlank()) return@withContext invalidArguments("Missing required 'run_id' argument.")
        val tailBytes = resolveTailBytes(arguments)
        runs[runId]?.let { return@withContext snapshot(it, tailBytes) }
        invalidArguments("Unknown Alpine run_id: $runId")
    }

    override suspend fun killExecution(argumentsJson: String): String = withContext(Dispatchers.IO) {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return@withContext invalidArguments("Arguments were not valid JSON.")
        val runId = arguments.optString("run_id").trim()
            .ifBlank { arguments.optString("runId").trim() }
        val tailBytes = resolveTailBytes(arguments)
        killExecutionByRunId(runId, tailBytes)
    }

    override suspend fun killExecutionByRunId(
        runId: String,
        tailBytes: Int,
    ): String = withContext(Dispatchers.IO) {
        if (runId.isBlank()) return@withContext invalidArguments("Missing required run id.")
        val run = runs[runId] ?: return@withContext invalidArguments("Unknown Alpine run_id: $runId")
        run.process?.let { process ->
            if (process.isAlive) {
                run.cancelled = true
                runCatching { process.destroy() }
                if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                    runCatching { process.destroyForcibly() }
                }
            }
        }
        snapshot(run, tailBytes)
    }

    override suspend fun executeCommand(
        command: String,
        workingDirectory: String,
        awaitTimeoutMillis: Long,
    ): String = executeCommand(command, workingDirectory, awaitTimeoutMillis, null)

    private suspend fun executeCommand(
        command: String,
        workingDirectory: String,
        awaitTimeoutMillis: Long,
        onOutput: ((String) -> Unit)?,
    ): String = withContext(Dispatchers.IO) {
        val normalizedWorkingDirectory = normalizePath(workingDirectory)
        val setup = inspectSetup()
        if (!setup.isReady) return@withContext setupError(command, normalizedWorkingDirectory, setup)
        ensureWorkspace()
        ensureGuestNetworkConfig()
        val startedAtMillis = System.currentTimeMillis()
        val stdoutFile = File.createTempFile("aether-alpine-stdout", ".log", runtimeRoot)
        val stderrFile = File.createTempFile("aether-alpine-stderr", ".log", runtimeRoot)
        val streamedStdout = StringBuilder()
        val streamedStderr = StringBuilder()
        val process = runCatching {
            buildAlpineProcess(command, normalizedWorkingDirectory).apply {
                if (onOutput == null) {
                    redirectOutput(stdoutFile)
                    redirectError(stderrFile)
                }
            }.start()
        }.getOrElse { throwable ->
            return@withContext commandError(
                command = command,
                workingDirectory = normalizedWorkingDirectory,
                message = throwable.message ?: "Failed to start Alpine command.",
            )
        }
        val outputThreads = if (onOutput != null) {
            listOf(
                streamProcessOutput(process.inputStream, streamedStdout, onOutput),
                streamProcessOutput(process.errorStream, streamedStderr, onOutput),
            )
        } else {
            emptyList()
        }
        val finished = process.waitFor(awaitTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroy() }
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                runCatching { process.destroyForcibly() }
            }
        }
        outputThreads.forEach { thread -> runCatching { thread.join(500L) } }
        val stdout = if (onOutput == null) stdoutFile.readTextSafe() else streamedStdout.toString()
        val stderr = if (onOutput == null) stderrFile.readTextSafe() else streamedStderr.toString()
        stdoutFile.delete()
        stderrFile.delete()
        JSONObject().apply {
            put("ok", finished && process.exitValueSafe() == 0)
            put("command", command)
            put("working_directory", normalizedWorkingDirectory)
            put("duration_ms", System.currentTimeMillis() - startedAtMillis)
            put("stdout", stdout)
            put("stderr", stderr)
            put("exit_code", if (finished) process.exitValueSafe() else -1)
            put("err", if (finished) -1 else -2)
            put("errmsg", if (finished) "" else "Timed out waiting for Alpine to reply.")
        }.toString()
    }

    private fun streamProcessOutput(
        stream: InputStream,
        sink: StringBuilder,
        onOutput: (String) -> Unit,
    ): Thread = Thread(
        {
            val buffer = ByteArray(4096)
            runCatching {
                stream.use {
                    while (true) {
                        val read = it.read(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            val chunk = String(buffer, 0, read, Charsets.UTF_8)
                            synchronized(sink) {
                                sink.append(chunk)
                                if (sink.length > 64_000) {
                                    sink.delete(0, sink.length - 64_000)
                                }
                            }
                            onOutput(chunk)
                        }
                    }
                }
            }
        },
        "aether-alpine-command-output",
    ).apply {
        isDaemon = true
        start()
    }

    private fun buildAlpineProcess(
        command: String,
        workingDirectory: String,
    ): ProcessBuilder {
        val prootCommand = listOf(
            AlpineHostLinker,
            prootFile.absolutePath,
            "-0",
            "-r",
            rootfsDir.absolutePath,
            "-b",
            "${workspaceDir.absolutePath}:/workspace",
            "-b",
            "/dev",
            "-b",
            "/proc",
            "-b",
            "/sys",
            "-w",
            workingDirectory,
            "/bin/sh",
            "-lc",
            command,
        )
        return ProcessBuilder(prootCommand).apply {
            configureAlpineProcessEnvironment()
        }
    }

    private fun buildAlpineInteractiveProcess(): ProcessBuilder {
        val prootCommand = buildAlpineInteractiveCommand()
        return ProcessBuilder(prootCommand).apply {
            configureAlpineProcessEnvironment()
            environment()["PS1"] = "aether-alpine:\\w# "
            environment()["TERM"] = "xterm-256color"
        }
    }

    private fun buildAlpineInteractiveCommand(): List<String> =
        listOf(
            AlpineHostLinker,
            prootFile.absolutePath,
            "-0",
            "-r",
            rootfsDir.absolutePath,
            "-b",
            "${workspaceDir.absolutePath}:/workspace",
            "-b",
            "/dev",
            "-b",
            "/proc",
            "-b",
            "/sys",
            "-w",
            homeDirectory,
            "/bin/sh",
            "-i",
        )

    private fun ProcessBuilder.configureAlpineProcessEnvironment() {
        directory(runtimeRoot)
        environment().putAll(buildAlpineProcessEnvironment())
    }

    private fun buildAlpineProcessEnvironment(): Map<String, String> =
        buildMap {
            put("HOME", homeDirectory)
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("AETHER_RUNTIME", "alpine")
            put("AETHER_HOST_WORKSPACE", workspaceDir.absolutePath)
            put("PROOT_ROOTFS", rootfsDir.absolutePath)
            put("PROOT_BIN", prootFile.absolutePath)
            put("PROOT_LOADER", loaderFile.absolutePath)
            put("PROOT_TMP_DIR", hostTmpDir.absolutePath)
            put("LD_LIBRARY_PATH", hostLibDir.absolutePath)
            put("PS1", "aether-alpine:\\w# ")
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
        }.toMutableMap().also { environment ->
        environmentVariables.forEach { variable ->
            environment[variable.name] = variable.value
        }
    }

    private fun hasBundledRuntimeAssets(): Boolean =
        assetExists("$AlpineAssetRoot/proot.bin") &&
            assetExists("$AlpineAssetRoot/loader.bin") &&
            assetExists("$AlpineAssetRoot/libtalloc.so.2") &&
            AlpineRootfsAssetCandidates.any { assetExists(it.path) }

    private fun assetExists(path: String): Boolean =
        runCatching {
            appContext.assets.open(path).use { true }
        }.getOrDefault(false)

    private fun installFromAssets(
        onProgress: (AlpineSetupProgress) -> Unit,
    ) {
        onProgress(AlpineSetupProgress(output = "Preparing Alpine runtime files...\n"))
        val stagingRoot = File(runtimeRoot.parentFile, "alpine-installing").apply {
            deleteRecursively()
            mkdirs()
        }
        val stagingBin = File(stagingRoot, "bin").apply { mkdirs() }
        val stagingLib = File(stagingRoot, "lib").apply { mkdirs() }
        val stagingLoader = File(stagingRoot, "libexec/proot").apply { mkdirs() }
        val stagingRootfs = File(stagingRoot, "rootfs").apply { mkdirs() }
        File(stagingRoot, "workspace").mkdirs()
        File(stagingRoot, "tmp").mkdirs()

        onProgress(AlpineSetupProgress(output = "Copying proot runtime...\n"))
        copyAsset("$AlpineAssetRoot/proot.bin", File(stagingBin, "proot"), executable = true)
        copyAsset("$AlpineAssetRoot/loader.bin", File(stagingLoader, "loader"), executable = true)
        copyAsset("$AlpineAssetRoot/libtalloc.so.2", File(stagingLib, "libtalloc.so.2"), executable = false)
        val rootfsAsset = AlpineRootfsAssetCandidates.firstOrNull { assetExists(it.path) }
            ?: error("Alpine rootfs asset is missing.")
        onProgress(
            AlpineSetupProgress(
                activity = AlpineSetupActivity.Extracting,
                output = "Extracting ${rootfsAsset.path}...\n",
            )
        )
        appContext.assets.open(rootfsAsset.path).use { stream ->
            if (rootfsAsset.compressed) {
                extractTarGz(stream, stagingRootfs, onProgress)
            } else {
                extractTar(stream, stagingRootfs, onProgress)
            }
        }
        File(stagingRoot, ".installed-version").writeText("alpine-3.23.4-aarch64\n")

        runtimeRoot.deleteRecursively()
        if (!stagingRoot.renameTo(runtimeRoot)) {
            copyDirectory(stagingRoot, runtimeRoot)
            stagingRoot.deleteRecursively()
        }
        ensureWorkspace()
        ensureGuestNetworkConfig()
        onProgress(AlpineSetupProgress(output = "Alpine runtime files are ready.\n"))
    }

    private fun copyAsset(
        assetPath: String,
        target: File,
        executable: Boolean,
    ) {
        target.parentFile?.mkdirs()
        appContext.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setReadable(true, true)
        target.setWritable(true, true)
        if (executable) target.setExecutable(true, true)
    }

    private fun guestPathToHostFile(guestPath: String): File {
        val relativePath = guestPath.trim().trimStart('/')
        require(relativePath.isNotBlank()) { "Guest path must not be blank." }
        val target = File(rootfsDir, relativePath).canonicalFile
        val canonicalRoot = rootfsDir.canonicalFile
        require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
            "Refusing to write outside Alpine rootfs: $guestPath"
        }
        return target
    }

    private fun extractTarGz(
        stream: InputStream,
        targetDirectory: File,
        onProgress: (AlpineSetupProgress) -> Unit,
    ) {
        GZIPInputStream(stream).use { gzip -> extractTar(gzip, targetDirectory, onProgress) }
    }

    private fun extractTar(
        stream: InputStream,
        targetDirectory: File,
        onProgress: (AlpineSetupProgress) -> Unit,
    ) {
        val progressReporter = ExtractionProgressReporter(onProgress)
        stream.use { tar ->
            val header = ByteArray(512)
            while (true) {
                val read = tar.readFullyOrEnd(header)
                if (read == 0) break
                if (read < 512) error("Invalid Alpine rootfs tar header.")
                if (header.all { it.toInt() == 0 }) break

                val name = header.tarString(0, 100)
                val prefix = header.tarString(345, 155)
                val path = listOf(prefix, name)
                    .filter(String::isNotBlank)
                    .joinToString("/")
                    .trimStart('/')
                val size = header.tarOctal(124, 12)
                val mode = header.tarOctal(100, 8).toInt()
                val type = header[156].toInt().toChar()
                val linkName = header.tarString(157, 100)
                val target = File(targetDirectory, path).canonicalFile
                val canonicalRoot = targetDirectory.canonicalFile
                if (!target.path.startsWith(canonicalRoot.path)) {
                    error("Refusing to extract path outside Alpine rootfs: $path")
                }

                when (type) {
                    '0', '\u0000' -> {
                        progressReporter.onEntry(path)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            tar.copyExactlyTo(output, size, progressReporter::onBytes)
                        }
                        target.applyTarMode(mode)
                    }
                    '5' -> {
                        target.mkdirs()
                        target.applyTarMode(mode)
                    }
                    '2' -> {
                        target.parentFile?.mkdirs()
                        runCatching {
                            java.nio.file.Files.deleteIfExists(target.toPath())
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(linkName),
                            )
                        }.onFailure {
                            File(target.parentFile, target.name).writeText(linkName)
                        }
                    }
                    else -> {
                        tar.skipExactly(size)
                    }
                }
                tar.skipPadding(size)
            }
        }
        progressReporter.finish()
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) return offset
            offset += read
        }
        return offset
    }

    private fun InputStream.copyExactlyTo(
        output: java.io.OutputStream,
        byteCount: Long,
        onBytesCopied: (Int) -> Unit = {},
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = byteCount
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) error("Unexpected end of Alpine rootfs tar entry.")
            output.write(buffer, 0, read)
            onBytesCopied(read)
            remaining -= read
        }
    }

    private fun InputStream.skipExactly(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) error("Unexpected end of Alpine rootfs tar entry.")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun InputStream.skipPadding(size: Long) {
        val padding = (512 - (size % 512)) % 512
        if (padding > 0) skipExactly(padding)
    }

    private fun ByteArray.tarString(
        offset: Int,
        length: Int,
    ): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it].toInt() == 0 }
            ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun ByteArray.tarOctal(
        offset: Int,
        length: Int,
    ): Long =
        tarString(offset, length).trim().ifBlank { "0" }.toLong(8)

    private fun File.applyTarMode(mode: Int) {
        setReadable(true, mode and 0b100_000_000 == 0)
        setWritable(true, mode and 0b010_000_000 == 0)
        if (mode and 0b001_000_000 != 0) setExecutable(true, false)
    }

    private fun copyDirectory(
        source: File,
        target: File,
    ) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyDirectory(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun isSupportedAbi(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    private fun unsupportedAbiState(): LocalRuntimeSetupState =
        LocalRuntimeSetupState(
            runtimeId = id,
            issue = LocalRuntimeIssue.UnsupportedAbi,
            detail = "Alpine runtime currently supports arm64-v8a devices only. Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}",
        )

    private fun ensureWorkspace() {
        runtimeRoot.mkdirs()
        workspaceDir.mkdirs()
        hostTmpDir.mkdirs()
    }

    private fun ensureGuestNetworkConfig() {
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        if (!resolvConf.isFile || resolvConf.length() == 0L) {
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText(
                """
                nameserver 1.1.1.1
                nameserver 8.8.8.8
                options timeout:2 attempts:2
                """.trimIndent() + "\n"
            )
            resolvConf.setReadable(true, false)
            resolvConf.setWritable(true, true)
        }
        val apkWorld = File(rootfsDir, "lib/apk/db/world")
        if (!apkWorld.exists()) {
            apkWorld.parentFile?.mkdirs()
            apkWorld.writeText("")
            apkWorld.setReadable(true, false)
            apkWorld.setWritable(true, true)
        }
    }

    private suspend fun verifyPackageProfile(profileId: String): Boolean {
        val command = when (profileId) {
            "python" -> "python3 --version && pip3 --version && virtualenv --version"
            "node" -> "node --version && npm --version"
            "git_search" -> "git --version && rg --version"
            "ssh" -> "ssh -V"
            "chrome" ->
                "(chromium-browser --version || chromium --version) && " +
                    "command -v Xvnc && command -v openbox && " +
                    "command -v xprop && command -v websockify && test -d /usr/share/novnc"
            else -> return false
        }
        val result = JSONObject(executeCommand(command, homeDirectory, 30_000L))
        return result.optBoolean("ok")
    }

    private fun nextRunId(): String =
        "run-${System.currentTimeMillis()}-${nextRunId.getAndIncrement()}-${UUID.randomUUID().toString().take(8)}"

    private fun snapshot(
        run: AlpineRun,
        tailBytes: Int,
    ): String {
        val process = run.process
        val running = process?.isAlive == true
        val exitCode = if (!running && process != null) process.exitValueSafe() else JSONObject.NULL
        val status = when {
            running -> "running"
            run.cancelled -> "cancelled"
            exitCode == 0 -> "completed"
            else -> "failed"
        }
        return JSONObject().apply {
            put("ok", status == "completed" || status == "running")
            put("runtime", id.storageValue)
            put("run_id", run.runId)
            put("command", run.command)
            put("working_directory", run.workingDirectory)
            put("status", status)
            put("running", running)
            put("completed", !running)
            put("stdout", run.stdoutFile.tailText(tailBytes))
            put("stderr", run.stderrFile.tailText(tailBytes))
            put("stdout_bytes", run.stdoutFile.lengthSafe())
            put("stderr_bytes", run.stderrFile.lengthSafe())
            put("exit_code", exitCode)
            put("err", -1)
            put("duration_ms", System.currentTimeMillis() - run.startedAtMillis)
            if (status == "failed") put("errmsg", "Alpine command failed.")
            if (status == "cancelled") put("errmsg", "Stopped by user.")
        }.toString()
    }

    private fun setupError(
        command: String,
        workingDirectory: String,
        setup: LocalRuntimeSetupState,
    ): String = JSONObject().apply {
        put("ok", false)
        put("runtime", id.storageValue)
        put("command", command)
        put("working_directory", workingDirectory)
        put("stdout", "")
        put("stderr", "")
        put("exit_code", -1)
        put("err", -1)
        put("errmsg", setup.detail.ifBlank { "Alpine runtime is not ready." })
        put("setup_issue", setup.issue.name)
    }.toString()

    private fun commandError(
        command: String,
        workingDirectory: String,
        runId: String = "",
        message: String,
    ): String = JSONObject().apply {
        put("ok", false)
        put("runtime", id.storageValue)
        if (runId.isNotBlank()) put("run_id", runId)
        put("command", command)
        put("working_directory", workingDirectory)
        put("stdout", "")
        put("stderr", "")
        put("exit_code", -1)
        put("err", -1)
        put("errmsg", message)
    }.toString()

    private fun invalidArguments(message: String): String =
        JSONObject().put("ok", false).put("errmsg", message).toString()

    private fun resolveTailBytes(arguments: JSONObject): Int =
        arguments.optInt("tail_bytes", arguments.optInt("tailBytes", AlpineDefaultTailBytes))
            .coerceIn(1, AlpineMaxTailBytes)

    private fun File.tailText(maxBytes: Int): String {
        if (!isFile) return ""
        val bytes = readBytes()
        val start = (bytes.size - maxBytes).coerceAtLeast(0)
        return String(bytes.copyOfRange(start, bytes.size), Charsets.UTF_8)
    }

    private fun File.readTextSafe(): String =
        runCatching { readText() }.getOrDefault("")

    private fun File.lengthSafe(): Long =
        runCatching { length() }.getOrDefault(0L)

    private fun File.existsNoFollow(): Boolean =
        Files.exists(toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun Process.exitValueSafe(): Int =
        runCatching { exitValue() }.getOrDefault(-1)

    private data class AlpineRun(
        val runId: String,
        val command: String,
        val workingDirectory: String,
        val startedAtMillis: Long,
        val stdoutFile: File,
        val stderrFile: File,
        @Volatile var process: Process? = null,
        @Volatile var cancelled: Boolean = false,
    )

    private object AlpineRunReaper {
        fun watch(
            run: AlpineRun,
            runs: ConcurrentHashMap<String, AlpineRun>,
        ) {
            Thread(
                {
                    runCatching { run.process?.waitFor() }
                    Thread.sleep(5 * 60 * 1000L)
                    if (run.process?.isAlive != true) {
                        runs.remove(run.runId, run)
                    }
                },
                "aether-alpine-run-${run.runId}",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    companion object {
        val AlpinePackageProfiles: Map<String, List<String>> = mapOf(
            "python" to listOf("python3", "py3-pip", "py3-virtualenv"),
            "node" to listOf("nodejs", "npm"),
            "git_search" to listOf("git", "ripgrep"),
            "ssh" to listOf("openssh-client"),
            "chrome" to listOf(
                "chromium",
                "font-noto",
                "font-noto-cjk",
                "openbox",
                "tigervnc",
                "xprop",
                "novnc",
                "websockify",
            ),
        )
    }

    private class ExtractionProgressReporter(
        private val onProgress: (AlpineSetupProgress) -> Unit,
    ) {
        private var totalBytes = 0L
        private var sampledBytes = 0L
        private var sampledAtMillis = System.currentTimeMillis()
        private var lastEntryAtMillis = 0L

        fun onEntry(path: String) {
            val now = System.currentTimeMillis()
            if (lastEntryAtMillis == 0L || now - lastEntryAtMillis >= 120L) {
                lastEntryAtMillis = now
                onProgress(
                    AlpineSetupProgress(
                        activity = AlpineSetupActivity.Extracting,
                        output = "Extracting $path\n",
                    )
                )
            }
        }

        fun onBytes(byteCount: Int) {
            totalBytes += byteCount
            val now = System.currentTimeMillis()
            val elapsed = now - sampledAtMillis
            if (elapsed < 400L) return
            val bytesPerSecond = ((totalBytes - sampledBytes) * 1_000L / elapsed).coerceAtLeast(0L)
            sampledBytes = totalBytes
            sampledAtMillis = now
            onProgress(
                AlpineSetupProgress(
                    activity = AlpineSetupActivity.Extracting,
                    bytesPerSecond = bytesPerSecond,
                )
            )
        }

        fun finish() {
            val megabytes = totalBytes / (1024L * 1024L)
            onProgress(
                AlpineSetupProgress(
                    activity = AlpineSetupActivity.Extracting,
                    output = "Finished extracting the Alpine root filesystem ($megabytes MB).\n",
                )
            )
        }
    }

    private class NetworkRateSampler(
        private val onRate: (Long) -> Unit,
    ) {
        @Volatile
        private var running = false
        private var thread: Thread? = null

        fun start() {
            val initialBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            if (initialBytes == TrafficStats.UNSUPPORTED.toLong()) return
            running = true
            thread = Thread(
                {
                    var previousBytes = initialBytes
                    var previousAtMillis = System.currentTimeMillis()
                    runCatching {
                        while (running) {
                            Thread.sleep(AlpineNetworkRateWindowMillis)
                            val now = System.currentTimeMillis()
                            val currentBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid())
                            val elapsed = now - previousAtMillis
                            if (currentBytes >= previousBytes && elapsed > 0L) {
                                onRate((currentBytes - previousBytes) * 1_000L / elapsed)
                            }
                            previousBytes = currentBytes
                            previousAtMillis = now
                        }
                    }
                },
                "aether-alpine-download-rate",
            ).apply {
                isDaemon = true
                start()
            }
        }

        fun stop() {
            running = false
            thread?.interrupt()
            thread = null
        }
    }
}

private data class RootfsAsset(
    val path: String,
    val compressed: Boolean,
)

data class AlpineTerminalLaunchSpec(
    val executable: String,
    val arguments: Array<String>,
    val environment: Array<String>,
    val workingDirectory: String,
)

enum class AlpineSetupActivity {
    None,
    Extracting,
    Downloading,
    Installing,
}

data class AlpineSetupProgress(
    val activity: AlpineSetupActivity = AlpineSetupActivity.None,
    val bytesPerSecond: Long = 0L,
    val progressPercent: Int? = null,
    val output: String = "",
)

internal class AlpinePackageInstallProgressTracker {
    private val outputTail = StringBuilder()
    private var activity = AlpineSetupActivity.Downloading
    private var bytesPerSecond = 0L
    private var progressPercent: Int? = null

    @Synchronized
    fun onOutput(output: String): AlpineSetupProgress {
        outputTail.append(output)
        if (outputTail.length > MaxTrackedOutputChars) {
            outputTail.delete(0, outputTail.length - MaxTrackedOutputChars)
        }
        ApkInstallProgressRegex.findAll(outputTail).lastOrNull()?.let { match ->
            val completed = match.groupValues[1].toIntOrNull() ?: 0
            val total = match.groupValues[2].toIntOrNull() ?: 0
            activity = AlpineSetupActivity.Installing
            progressPercent = if (total > 0) {
                (completed * 100 / total).coerceIn(0, 100)
            } else {
                null
            }
        }
        return snapshot(output)
    }

    @Synchronized
    fun onRate(rate: Long): AlpineSetupProgress {
        bytesPerSecond = rate.coerceAtLeast(0L)
        return snapshot()
    }

    private fun snapshot(output: String = ""): AlpineSetupProgress =
        AlpineSetupProgress(
            activity = activity,
            bytesPerSecond = bytesPerSecond,
            progressPercent = progressPercent,
            output = output,
        )

    private companion object {
        const val MaxTrackedOutputChars = 8_192
        val ApkInstallProgressRegex = Regex(
            """\((\d+)/(\d+)\)\s+(?:Installing|Upgrading|Downgrading|Replacing)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}

internal data class AlpineWorkspaceHostPath(
    val guestPath: String,
    val hostFile: File,
)
