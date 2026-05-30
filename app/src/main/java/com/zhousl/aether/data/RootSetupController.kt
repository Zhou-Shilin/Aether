package com.zhousl.aether.data

import android.content.Context
import android.os.Build
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxContract
import com.zhousl.aether.termux.TermuxSetupIssue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RootProbeTimeoutMillis = 1_500L
private const val RootSetupTimeoutMillis = 180_000L
private const val TermuxBootstrapSharedLibrary = "libtermux-bootstrap.so"
private const val TermuxBootstrapSymlinksFile = "SYMLINKS.txt"
private const val RootSetupReadyMarker = "AETHER_ROOT_SETUP_READY"

enum class RootSetupIssue {
    Unknown,
    Available,
    Running,
    Ready,
    Unavailable,
    PermissionDenied,
    TermuxNotInstalled,
    Failed,
}

data class RootSetupState(
    val issue: RootSetupIssue = RootSetupIssue.Unknown,
    val detail: String = "",
    val rootAvailable: Boolean = false,
    val suPath: String = "",
    val didLaunchTermuxForBackground: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
) {
    val isReady: Boolean
        get() = issue == RootSetupIssue.Ready

    val isRunning: Boolean
        get() = issue == RootSetupIssue.Running
}

class RootSetupController(
    private val context: Context,
    private val bashTool: TermuxBashTool,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    suspend fun inspect(): RootSetupState = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath.isBlank()) {
            RootSetupState(
                issue = RootSetupIssue.Unavailable,
                detail = "No su binary was detected on this device.",
                rootAvailable = false,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        } else {
            RootSetupState(
                issue = RootSetupIssue.Available,
                detail = "Root appears to be available. Aether can request su to finish local setup automatically.",
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun configureLocalAccess(): RootSetupState = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath.isBlank()) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            return@withContext RootSetupState(
                issue = RootSetupIssue.Unavailable,
                detail = "No su binary was detected on this device.",
                rootAvailable = false,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }
        if (!isTermuxInstalled()) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            return@withContext RootSetupState(
                issue = RootSetupIssue.TermuxNotInstalled,
                detail = "Install Termux before using root automatic setup.",
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        diagnosticLogger.event(
            category = "root_setup",
            event = "configure_start",
            details = mapOf("su_path" to suPath),
        )

        val bootstrapArtifacts = prepareTermuxBootstrapArtifacts().getOrElse { throwable ->
            bashTool.setRootBackgroundLaunchEnabled(false)
            diagnosticLogger.exception(
                category = "root_setup",
                event = "bootstrap_prepare_failed",
                throwable = throwable,
                level = "warn",
            )
            return@withContext RootSetupState(
                issue = RootSetupIssue.Failed,
                detail = throwable.message ?: "Unable to prepare Termux bootstrap package.",
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        val commandResult = runProcess(
            command = listOf(
                suPath,
                "-c",
                buildTermuxRootSetupScript(
                    aetherPackageName = context.packageName,
                    bootstrapZipPath = bootstrapArtifacts.zip.absolutePath,
                    symlinkManifestPath = bootstrapArtifacts.symlinkManifest.absolutePath,
                ),
            ),
            timeoutMillis = RootSetupTimeoutMillis,
        )
        if (commandResult.timedOut || commandResult.exitCode != 0) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            val detail = commandResult.combinedOutput().ifBlank {
                if (commandResult.timedOut) {
                    "Root request timed out. Grant su to Aether, then try again."
                } else {
                    commandResult.launchError.ifBlank { "Root setup command failed." }
                }
            }
            diagnosticLogger.event(
                category = "root_setup",
                event = "configure_failed",
                level = "warn",
                details = mapOf(
                    "exit_code" to commandResult.exitCode,
                    "timed_out" to commandResult.timedOut,
                    "stdout" to commandResult.stdout,
                    "stderr" to commandResult.stderr,
                    "launch_error" to commandResult.launchError,
                ),
            )
            return@withContext RootSetupState(
                issue = if (looksLikeRootDenied(detail)) {
                    RootSetupIssue.PermissionDenied
                } else {
                    RootSetupIssue.Failed
                },
                detail = detail.take(1200),
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }
        if (!commandResult.stdout.contains(RootSetupReadyMarker)) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            val detail = commandResult.combinedOutput().ifBlank {
                "Root setup command finished without the ready marker."
            }
            diagnosticLogger.event(
                category = "root_setup",
                event = "ready_marker_missing",
                level = "warn",
                details = mapOf(
                    "stdout" to commandResult.stdout,
                    "stderr" to commandResult.stderr,
                    "launch_error" to commandResult.launchError,
                ),
            )
            return@withContext RootSetupState(
                issue = RootSetupIssue.Failed,
                detail = detail.take(1200),
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        val termuxProbe = bashTool.inspectSetup()
        if (!termuxProbe.isReady) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            val detail = "Root setup installed Termux runtime, but Termux command probe failed: " +
                termuxProbe.detail.ifBlank { termuxProbe.issue.name }
            diagnosticLogger.event(
                category = "root_setup",
                event = "termux_probe_failed",
                level = "warn",
                details = mapOf(
                    "termux_issue" to termuxProbe.issue.name,
                    "termux_detail" to termuxProbe.detail,
                    "root_stdout" to commandResult.stdout,
                    "root_stderr" to commandResult.stderr,
                ),
            )
            return@withContext RootSetupState(
                issue = if (termuxProbe.issue == TermuxSetupIssue.PermissionMissing) {
                    RootSetupIssue.PermissionDenied
                } else {
                    RootSetupIssue.Failed
                },
                detail = detail.take(1200),
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        bashTool.setRootBackgroundLaunchEnabled(true)
        diagnosticLogger.event(
            category = "root_setup",
            event = "configure_succeeded",
            details = mapOf(
                "stdout" to commandResult.stdout,
                "stderr" to commandResult.stderr,
                "termux_issue" to termuxProbe.issue.name,
            ),
        )
        RootSetupState(
            issue = RootSetupIssue.Ready,
            detail = "Root setup completed. Aether will keep Termux available in the background when needed.",
            rootAvailable = true,
            suPath = suPath,
            didLaunchTermuxForBackground = false,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TermuxContract.PackageName, 0)
        true
    }.getOrDefault(false)

    private fun prepareTermuxBootstrapArtifacts(): Result<TermuxBootstrapArtifacts> = runCatching {
        val packageManager = context.packageManager
        val termuxInfo = packageManager.getApplicationInfo(TermuxContract.PackageName, 0)
        val packageFiles = buildList {
            add(termuxInfo.sourceDir)
            termuxInfo.splitSourceDirs?.forEach(::add)
        }.filter(String::isNotBlank)

        val setupDir = File(context.cacheDir, "root-setup").apply { mkdirs() }
        val bootstrapZip = File(setupDir, "termux-bootstrap.zip")
        val symlinkManifest = File(setupDir, "termux-bootstrap-symlinks.tsv")

        for (packageFile in packageFiles) {
            ZipFile(packageFile).use { apk ->
                val entry = findBootstrapLibraryEntry(apk) ?: return@use
                val libraryBytes = apk.getInputStream(entry).use { it.readBytes() }
                val bootstrapBytes = extractEmbeddedZip(libraryBytes)
                    ?: error("Termux bootstrap library did not contain a bootstrap zip.")
                val symlinks = extractSymlinkManifest(bootstrapBytes)
                require(containsBootstrapBash(bootstrapBytes)) {
                    "Termux bootstrap zip is missing bin/bash."
                }

                bootstrapZip.outputStream().use { it.write(bootstrapBytes) }
                symlinkManifest.writeText(symlinks, Charsets.UTF_8)
                listOf(bootstrapZip, symlinkManifest).forEach { file ->
                    file.setReadable(true, false)
                    file.setWritable(true, true)
                }
                diagnosticLogger.event(
                    category = "root_setup",
                    event = "bootstrap_prepared",
                    details = mapOf(
                        "apk_entry" to entry.name,
                        "bootstrap_zip_bytes" to bootstrapZip.length(),
                        "symlink_manifest_bytes" to symlinkManifest.length(),
                    ),
                )
                return@runCatching TermuxBootstrapArtifacts(
                    zip = bootstrapZip,
                    symlinkManifest = symlinkManifest,
                )
            }
        }
        error("Termux bootstrap library was not found in the installed APK.")
    }

    private fun findBootstrapLibraryEntry(apk: ZipFile): ZipEntry? {
        Build.SUPPORTED_ABIS.forEach { abi ->
            apk.getEntry("lib/$abi/$TermuxBootstrapSharedLibrary")?.let { return it }
        }
        val entries = apk.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith("/$TermuxBootstrapSharedLibrary")) {
                return entry
            }
        }
        return null
    }

    private fun extractEmbeddedZip(libraryBytes: ByteArray): ByteArray? {
        val start = libraryBytes.indexOfSignature(0x50, 0x4b, 0x03, 0x04)
        if (start < 0) return null
        val endOfCentralDirectory = libraryBytes.lastIndexOfSignature(0x50, 0x4b, 0x05, 0x06)
        if (endOfCentralDirectory < start || endOfCentralDirectory + 22 > libraryBytes.size) return null
        val commentLength = (libraryBytes[endOfCentralDirectory + 20].toInt() and 0xff) or
            ((libraryBytes[endOfCentralDirectory + 21].toInt() and 0xff) shl 8)
        val end = endOfCentralDirectory + 22 + commentLength
        if (end > libraryBytes.size) return null
        return libraryBytes.copyOfRange(start, end)
    }

    private fun extractSymlinkManifest(bootstrapBytes: ByteArray): String {
        val symlinkText = ZipInputStream(ByteArrayInputStream(bootstrapBytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == TermuxBootstrapSymlinksFile }
                ?.let { zip.bufferedReader(Charsets.UTF_8).readText() }
        } ?: error("Termux bootstrap zip is missing $TermuxBootstrapSymlinksFile.")

        return buildString {
            symlinkText.lineSequence().forEach { line ->
                val separator = line.indexOf('\u2190')
                if (separator <= 0 || separator >= line.lastIndex) return@forEach
                val target = line.substring(0, separator)
                val path = line.substring(separator + 1)
                if (target.contains('\t') || path.contains('\t')) {
                    error("Termux bootstrap symlink contains an unsupported tab character.")
                }
                append(target)
                append('\t')
                append(path)
                append('\n')
            }
        }.also { manifest ->
            require(manifest.isNotBlank()) {
                "Termux bootstrap zip did not contain usable symlink metadata."
            }
        }
    }

    private fun containsBootstrapBash(bootstrapBytes: ByteArray): Boolean =
        ZipInputStream(ByteArrayInputStream(bootstrapBytes)).use { zip ->
            generateSequence { zip.nextEntry }.any { it.name == "bin/bash" }
        }

    private fun ByteArray.indexOfSignature(
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int,
    ): Int {
        for (index in 0..size - 4) {
            if (
                this[index].toInt() and 0xff == b0 &&
                this[index + 1].toInt() and 0xff == b1 &&
                this[index + 2].toInt() and 0xff == b2 &&
                this[index + 3].toInt() and 0xff == b3
            ) {
                return index
            }
        }
        return -1
    }

    private fun ByteArray.lastIndexOfSignature(
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int,
    ): Int {
        for (index in size - 4 downTo 0) {
            if (
                this[index].toInt() and 0xff == b0 &&
                this[index + 1].toInt() and 0xff == b1 &&
                this[index + 2].toInt() and 0xff == b2 &&
                this[index + 3].toInt() and 0xff == b3
            ) {
                return index
            }
        }
        return -1
    }

    private fun findSuPath(): String {
        val commonPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
        )
        commonPaths.firstOrNull { path ->
            File(path).let { it.exists() && it.canExecute() }
        }?.let { return it }

        val result = runProcess(
            command = listOf("sh", "-c", "command -v su 2>/dev/null || true"),
            timeoutMillis = RootProbeTimeoutMillis,
        )
        return result.stdout.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    private fun runProcess(
        command: List<String>,
        timeoutMillis: Long,
    ): RootCommandResult {
        val process = runCatching {
            ProcessBuilder(command).start()
        }.getOrElse { throwable ->
            return RootCommandResult(
                exitCode = -1,
                launchError = throwable.message.orEmpty(),
            )
        }

        val stdoutReader = process.inputStream.startReadingText()
        val stderrReader = process.errorStream.startReadingText()
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroy() }
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                runCatching { process.destroyForcibly() }
                process.waitFor(800, TimeUnit.MILLISECONDS)
            }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }

        val stdout = stdoutReader.awaitText()
        val stderr = stderrReader.awaitText()
        return RootCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout,
            stderr = stderr,
            timedOut = !finished,
        )
    }

    private fun buildTermuxRootSetupScript(
        aetherPackageName: String,
        bootstrapZipPath: String,
        symlinkManifestPath: String,
    ): String = """
        set -u
        termux_pkg='${TermuxContract.PackageName}'
        aether_pkg='${escapeForSingleQuoted(aetherPackageName)}'
        bootstrap_zip='${escapeForSingleQuoted(bootstrapZipPath)}'
        symlink_manifest='${escapeForSingleQuoted(symlinkManifestPath)}'
        run_command_permission='${TermuxContract.RunCommandPermission}'
        current_user="${'$'}(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || printf '0')"
        current_user="${'$'}(printf '%s' "${'$'}current_user" | tr -cd '0-9')"
        current_user="${'$'}{current_user:-0}"
        termux_data="${'$'}(
          cmd package dump "${'$'}termux_pkg" 2>/dev/null |
            sed -n 's/^[[:space:]]*dataDir=//p' |
            grep -v '^null${'$'}' |
            head -n 1
        )"
        if [ -z "${'$'}termux_data" ] || [ "${'$'}termux_data" = "null" ]; then
          termux_data="/data/user/${'$'}current_user/${'$'}termux_pkg"
        fi
        termux_home="${'$'}termux_data/files/home"
        termux_files="${'$'}termux_data/files"
        termux_prefix="${'$'}termux_data/files/usr"
        termux_staging_prefix="${'$'}termux_data/files/usr-staging"
        termux_bash="${'$'}termux_data/files/usr/bin/bash"
        props_dir="${'$'}termux_home/.termux"
        props="${'$'}props_dir/termux.properties"

        app_uid="${'$'}(
          cmd package list packages -U --user "${'$'}current_user" "${'$'}termux_pkg" 2>/dev/null |
            grep "^package:${'$'}termux_pkg " |
            sed -n 's/.*uid://p' |
            head -n 1 |
            sed 's/[^0-9].*//'
        )"
        if [ -z "${'$'}app_uid" ]; then
          app_uid="${'$'}(
            cmd package list packages -U "${'$'}termux_pkg" 2>/dev/null |
              grep "^package:${'$'}termux_pkg " |
              sed -n 's/.*uid://p' |
              head -n 1 |
              sed 's/[^0-9].*//'
          )"
        fi
        owner=""
        if [ -n "${'$'}app_uid" ]; then
          owner="${'$'}app_uid:${'$'}app_uid"
        fi

        repair_termux_data_directory() {
          [ -d "${'$'}termux_data" ] || return 0
          if [ -n "${'$'}owner" ]; then
            for path in "${'$'}termux_data" "${'$'}termux_files"; do
              [ -e "${'$'}path" ] && chown "${'$'}owner" "${'$'}path" 2>/dev/null || true
            done
          fi
          restorecon -RF "${'$'}termux_data" >/dev/null 2>&1 ||
            restorecon -R "${'$'}termux_data" >/dev/null 2>&1 ||
            true
        }

        ensure_termux_base_data_directory() {
          if [ ! -d "${'$'}termux_data" ]; then
            mkdir -p "${'$'}termux_data" || return 1
          fi
          if [ ! -d "${'$'}termux_files" ]; then
            mkdir -p "${'$'}termux_files" || return 1
          fi
          if [ -n "${'$'}owner" ]; then
            chown "${'$'}owner" "${'$'}termux_data" "${'$'}termux_files" 2>/dev/null || true
          fi
          chmod 700 "${'$'}termux_data" "${'$'}termux_files" 2>/dev/null || true
          repair_termux_data_directory
          return 0
        }

        ensure_termux_bash_runtime() {
          if [ -s "${'$'}termux_bash" ]; then
            if [ -n "${'$'}owner" ]; then
              chown -R "${'$'}owner" "${'$'}termux_prefix" 2>/dev/null || true
            fi
            chmod 700 "${'$'}termux_prefix" "${'$'}termux_prefix/bin" "${'$'}termux_bash" 2>/dev/null || true
            restorecon -RF "${'$'}termux_data" >/dev/null 2>&1 ||
              restorecon -R "${'$'}termux_data" >/dev/null 2>&1 ||
              true
            return 0
          fi

          ensure_termux_base_data_directory || return 1
          rm -rf "${'$'}termux_staging_prefix" 2>/dev/null || true
          if [ -e "${'$'}termux_prefix" ]; then
            rm -rf "${'$'}termux_prefix" 2>/dev/null || true
          fi
          mkdir -p "${'$'}termux_staging_prefix" || return 1
          repair_termux_data_directory

          printf '%s\n' 'AETHER_TERMUX_BOOTSTRAP_INSTALLING'
          if [ ! -r "${'$'}bootstrap_zip" ]; then
            printf 'Termux bootstrap zip is not readable: %s\n' "${'$'}bootstrap_zip" >&2
            return 1
          fi
          if [ ! -r "${'$'}symlink_manifest" ]; then
            printf 'Termux bootstrap symlink manifest is not readable: %s\n' "${'$'}symlink_manifest" >&2
            return 1
          fi
          unzip -q "${'$'}bootstrap_zip" -d "${'$'}termux_staging_prefix" || return 1
          if [ ! -f "${'$'}termux_staging_prefix/SYMLINKS.txt" ]; then
            printf '%s\n' 'Termux bootstrap zip is missing SYMLINKS.txt.' >&2
            return 1
          fi
          while IFS='	' read -r symlink_target symlink_path || [ -n "${'$'}symlink_target" ]; do
            [ -n "${'$'}symlink_target" ] && [ -n "${'$'}symlink_path" ] || continue
            full_symlink_path="${'$'}termux_staging_prefix/${'$'}symlink_path"
            mkdir -p "${'$'}(dirname "${'$'}full_symlink_path")" || return 1
            rm -f "${'$'}full_symlink_path" || return 1
            ln -s "${'$'}symlink_target" "${'$'}full_symlink_path" || return 1
          done < "${'$'}symlink_manifest"
          rm -f "${'$'}termux_staging_prefix/SYMLINKS.txt" 2>/dev/null || true
          find "${'$'}termux_staging_prefix/bin" -type f -exec chmod 700 {} + 2>/dev/null || true
          find "${'$'}termux_staging_prefix/libexec" -type f -exec chmod 700 {} + 2>/dev/null || true
          chmod 700 "${'$'}termux_staging_prefix/lib/apt/apt-helper" 2>/dev/null || true
          find "${'$'}termux_staging_prefix/lib/apt/methods" -type f -exec chmod 700 {} + 2>/dev/null || true
          mv "${'$'}termux_staging_prefix" "${'$'}termux_prefix" || return 1
          if [ -n "${'$'}owner" ]; then
            chown -R "${'$'}owner" "${'$'}termux_prefix" 2>/dev/null || true
          fi
          chmod 700 "${'$'}termux_prefix" "${'$'}termux_prefix/bin" "${'$'}termux_bash" 2>/dev/null || true
          restorecon -RF "${'$'}termux_data" >/dev/null 2>&1 ||
            restorecon -R "${'$'}termux_data" >/dev/null 2>&1 ||
            true
          if [ ! -s "${'$'}termux_bash" ]; then
            printf 'Termux bootstrap did not produce bash at %s\n' "${'$'}termux_bash" >&2
            ls -lZ "${'$'}termux_prefix/bin" "${'$'}termux_bash" 2>&1 >&2 || true
            return 1
          fi
          printf '%s\n' 'AETHER_TERMUX_BOOTSTRAP_INSTALLED'
        }

        if ! ensure_termux_bash_runtime; then
          printf '%s\n' 'Termux is installed but its bash runtime could not be bootstrapped with Root.' >&2
          printf 'termux_data=%s\ntermux_uid=%s\ntermux_owner=%s\nbootstrap_zip=%s\nsymlink_manifest=%s\n' "${'$'}termux_data" "${'$'}app_uid" "${'$'}owner" "${'$'}bootstrap_zip" "${'$'}symlink_manifest" >&2
          for path in "${'$'}termux_data" "${'$'}termux_files" "${'$'}termux_prefix" "${'$'}termux_staging_prefix"; do
            [ -e "${'$'}path" ] && ls -ldZ "${'$'}path" 2>&1 >&2 || true
          done
          for path in "${'$'}termux_bash" "${'$'}bootstrap_zip" "${'$'}symlink_manifest"; do
            [ -e "${'$'}path" ] && ls -lZ "${'$'}path" 2>&1 >&2 || true
          done
          exit 25
        fi

        mkdir -p "${'$'}termux_home" || exit 20
        if [ -n "${'$'}owner" ]; then
          chown "${'$'}owner" "${'$'}termux_home" 2>/dev/null || true
        fi
        chmod 700 "${'$'}termux_home" 2>/dev/null || true
        repair_termux_data_directory

        mkdir -p "${'$'}props_dir" || exit 21
        touch "${'$'}props" || exit 22
        if grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' "${'$'}props"; then
          sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' "${'$'}props" || exit 23
        else
          printf '\nallow-external-apps=true\n' >> "${'$'}props" || exit 24
        fi

        owner="${'$'}(stat -c '%u:%g' "${'$'}termux_home" 2>/dev/null || true)"
        if [ -n "${'$'}owner" ]; then
          chown "${'$'}owner" "${'$'}props_dir" "${'$'}props" 2>/dev/null || true
        fi
        chmod 700 "${'$'}props_dir" 2>/dev/null || true
        chmod 600 "${'$'}props" 2>/dev/null || true
        restorecon -R "${'$'}props_dir" >/dev/null 2>&1 || true

        pm grant --user "${'$'}current_user" "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          cmd package grant --user "${'$'}current_user" "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          pm grant "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          cmd package grant "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          true
        am broadcast --user "${'$'}current_user" -a com.termux.app.reload_style -p "${'$'}termux_pkg" >/dev/null 2>&1 || true
        echo AETHER_ROOT_SETUP_READY
    """.trimIndent()

    private fun escapeForSingleQuoted(value: String): String =
        value.replace("'", "'\"'\"'")

    private fun InputStream.startReadingText(): ProcessStreamReader {
        val output = StringBuffer()
        val thread = Thread(
            {
                runCatching {
                    bufferedReader().use { reader ->
                        output.append(reader.readText())
                    }
                }
            },
            "AetherRootSetupStreamReader",
        ).apply {
            isDaemon = true
            start()
        }
        return ProcessStreamReader(thread, output)
    }

    private fun ProcessStreamReader.awaitText(): String {
        runCatching { thread.join(800) }
        return output.toString()
    }

    private fun looksLikeRootDenied(value: String): Boolean {
        val normalized = value.lowercase()
        if ("aether_termux_bootstrap_launched" in normalized ||
            "termux is installed but its bash runtime" in normalized ||
            "termux_data=" in normalized ||
            "com.termux" in normalized
        ) {
            return false
        }
        return "denied" in normalized ||
            "permission" in normalized ||
            "not allowed" in normalized ||
            "su:" in normalized
    }
}

private data class ProcessStreamReader(
    val thread: Thread,
    val output: StringBuffer,
)

private data class TermuxBootstrapArtifacts(
    val zip: File,
    val symlinkManifest: File,
)

private data class RootCommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val launchError: String = "",
) {
    fun combinedOutput(): String = listOf(stdout, stderr, launchError)
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")
}
