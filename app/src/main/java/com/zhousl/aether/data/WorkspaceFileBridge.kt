package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxContract
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val MaxWorkspaceDownloadBytes = 32 * 1024 * 1024
private const val WorkspaceImportChunkBytes = 48 * 1024
private const val WorkspaceTransferChunkBytes = 6 * 1024
private const val WorkspaceUploadChunkChars = 64 * 1024
private const val MaxAnalyzeInlineBytes = 5 * 1024 * 1024
private const val WorkspaceImportProgressIntervalMillis = 500L
private const val WorkspaceHttpUploadBufferBytes = 256 * 1024
private const val WorkspaceHttpUploadTimeoutMillis = 60 * 60 * 1000L
private const val SharedWorkspaceBaseDirectoryName = ".aether/workspace"
private const val PerSessionWorkspaceBaseDirectoryName = ".aether/workspaces"
private const val RootReadTimeoutMillis = 20_000L

class WorkspaceFileBridge(
    private val context: Context,
    private val bashTool: TermuxBashTool = TermuxBashTool(context),
) {
    fun workspaceDirectory(
        sessionId: String,
        mode: AgentWorkspaceMode = AgentWorkspaceMode.Shared,
    ): String = when (mode) {
        AgentWorkspaceMode.Shared ->
            "${TermuxContract.HomeDirectory}/$SharedWorkspaceBaseDirectoryName"

        AgentWorkspaceMode.PerSession ->
            "${TermuxContract.HomeDirectory}/$PerSessionWorkspaceBaseDirectoryName/$sessionId"
    }

    fun workspaceUploadsDirectory(
        sessionId: String,
        mode: AgentWorkspaceMode = AgentWorkspaceMode.Shared,
    ): String = "${workspaceDirectory(sessionId, mode)}/uploads"

    suspend fun importAttachmentToWorkspace(
        sourceUri: Uri,
        sessionId: String,
        attachmentId: String,
        displayName: String,
        mode: AgentWorkspaceMode = AgentWorkspaceMode.Shared,
        onProgress: (WorkspaceImportProgress) -> Unit = {},
    ): Result<ImportedWorkspaceFile> = runCatching {
        val safeFileName = buildWorkspaceFileName(
            attachmentId = attachmentId,
            displayName = displayName,
        )
        val destinationPath = "${workspaceUploadsDirectory(sessionId, mode)}/$safeFileName"
        var inlineBytes = ByteArray(0)
        val bytesCopied = copyUriToWorkspace(
            sourceUri = sourceUri,
            absolutePath = destinationPath,
            onProgress = onProgress,
            onInlineBytes = { inlineBytes = it },
        )

        ImportedWorkspaceFile(
            absolutePath = destinationPath,
            bytesCopied = bytesCopied,
            inlineBytes = inlineBytes,
        )
    }

    suspend fun hasLegacySessionWorkspaces(): Boolean = withContext(Dispatchers.IO) {
        val legacyRootPath = "${TermuxContract.HomeDirectory}/$PerSessionWorkspaceBaseDirectoryName"
        val command = buildString {
            appendCommonShellPreamble(this)
            appendLine("legacy_root=\"\$(decode_b64 '${encodeBase64(legacyRootPath)}')\"")
            appendLine("if [ -d \"\$legacy_root\" ] && find \"\$legacy_root\" -mindepth 1 -maxdepth 1 2>/dev/null | read -r _; then")
            appendLine("  emit_kv legacy true")
            appendLine("else")
            appendLine("  emit_kv legacy false")
            appendLine("fi")
        }
        val raw = JSONObject(bashTool.executeCommand(command))
        if (!raw.optBoolean("ok")) return@withContext false
        parseStructuredStdout(raw.optString("stdout"))["legacy"] == "true"
    }

    suspend fun deleteSessionRuntimeData(
        sessionId: String,
        sharedWorkspaceFilePaths: Collection<String> = emptyList(),
    ): Result<Unit> = deleteSessionsRuntimeData(
        sessionIds = listOf(sessionId),
        sharedWorkspaceFilePaths = sharedWorkspaceFilePaths,
    )

    private suspend fun deletePathUnderRoot(
        path: String,
        rootPath: String,
        targetLabel: String,
        skipUnsafePath: Boolean = false,
        requireFile: Boolean = false,
    ): Result<Unit> = runCatching {
        val command = buildString {
            appendCommonShellPreamble(this)
            appendSafePathUnderRoot(
                builder = this,
                variableName = "delete_path",
                path = path,
                rootPath = rootPath,
                skipUnsafePath = skipUnsafePath,
            )
            if (requireFile) {
                appendLine("if [ -d \"\$delete_path\" ]; then")
                appendLine("  emit_kv error 'refusing_directory_delete'")
                appendLine("  exit 1")
                appendLine("fi")
                appendLine("if [ -e \"\$delete_path\" ] && [ ! -f \"\$delete_path\" ]; then")
                appendLine("  emit_kv error 'refusing_non_file_delete'")
                appendLine("  exit 1")
                appendLine("fi")
            }
            appendLine("path_deleted=false")
            appendLine("if [ -e \"\$delete_path\" ]; then")
            appendLine("  rm -rf -- \"\$delete_path\"")
            appendLine("  path_deleted=true")
            appendLine("fi")
            appendLine("emit_kv path_deleted \"\$path_deleted\"")
        }
        val raw = JSONObject(bashTool.executeCommand(command, awaitTimeoutMillis = 60_000L))
        if (!raw.optBoolean("ok")) {
            error(raw.optString("errmsg").ifBlank { raw.optString("stderr").ifBlank { "Couldn't delete $targetLabel." } })
        }
    }

    private suspend fun deleteSessionWorkspace(sessionId: String): Result<Unit> = deletePathUnderRoot(
        path = sessionWorkspaceDirectory(sessionId),
        rootPath = perSessionWorkspacesRootDirectory(),
        targetLabel = "session workspace",
    )

    private suspend fun deleteSharedWorkspaceFile(path: String): Result<Unit> = deletePathUnderRoot(
        path = path,
        rootPath = sharedWorkspaceUploadsDirectory(),
        targetLabel = "shared workspace file",
        skipUnsafePath = true,
        requireFile = true,
    )

    suspend fun deleteSessionsRuntimeData(
        sessionIds: Collection<String>,
        sharedWorkspaceFilePaths: Collection<String> = emptyList(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val failures = mutableListOf<String>()
            val uniqueSessionIds = sessionIds
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            val uniqueSharedWorkspaceFilePaths = sharedWorkspaceFilePaths
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()

            uniqueSessionIds.forEach { sessionId ->
                deleteSessionWorkspace(sessionId = sessionId).onFailure { error ->
                    failures += "workspace $sessionId: ${error.message.orEmpty()}"
                }
            }
            uniqueSharedWorkspaceFilePaths.forEach { path ->
                deleteSharedWorkspaceFile(path = path).onFailure { error ->
                    failures += "shared file $path: ${error.message.orEmpty()}"
                }
            }
            if (failures.isNotEmpty()) {
                error(
                    "Failed to clean up ${failures.size} runtime item(s): " +
                        failures.joinToString(" | ")
                )
            }
        }
    }

    private fun sessionWorkspaceDirectory(sessionId: String): String = workspaceDirectory(
        sessionId = sessionId,
        mode = AgentWorkspaceMode.PerSession,
    )

    private fun perSessionWorkspacesRootDirectory(): String =
        "${TermuxContract.HomeDirectory}/$PerSessionWorkspaceBaseDirectoryName"

    private fun sharedWorkspaceUploadsDirectory(): String =
        "${TermuxContract.HomeDirectory}/$SharedWorkspaceBaseDirectoryName/uploads"

    private fun appendSafePathUnderRoot(
        builder: StringBuilder,
        variableName: String,
        path: String,
        rootPath: String,
        skipUnsafePath: Boolean = false,
    ) {
        builder.appendLine("$variableName=\"\$(decode_b64 '${encodeBase64(path)}')\"")
        builder.appendLine("delete_root=\"\$(decode_b64 '${encodeBase64(rootPath)}')\"")
        builder.appendLine("${variableName}_real=\"\$(realpath -m \"\$$variableName\")\"")
        builder.appendLine("delete_root_real=\"\$(realpath -m \"\$delete_root\")\"")
        builder.appendLine("if [ \"\$${variableName}_real\" = \"\$delete_root_real\" ]; then")
        if (skipUnsafePath) {
            builder.appendLine("  emit_kv path_skipped true")
            builder.appendLine("  exit 0")
        } else {
            builder.appendLine("  emit_kv error 'refusing_delete_root_delete'")
            builder.appendLine("  exit 1")
        }
        builder.appendLine("fi")
        builder.appendLine("case \"\$${variableName}_real\" in")
        builder.appendLine("  \"\$delete_root_real\"/*) ;;")
        if (skipUnsafePath) {
            builder.appendLine("  *) emit_kv path_skipped true; exit 0 ;;")
        } else {
            builder.appendLine("  *) emit_kv error 'refusing_unsafe_delete_path'; exit 1 ;;")
        }
        builder.appendLine("esac")
        builder.appendLine("$variableName=\"\$${variableName}_real\"")
    }

    suspend fun readWorkspaceFile(
        path: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
        byteLimit: Int,
        allowedRoot: String? = null,
    ): Result<WorkspaceFilePayload> = runCatching {
        require(byteLimit > 0) { "byteLimit must be greater than 0." }

        val absolutePath = resolveTermuxPath(
            path = path,
            workingDirectory = workingDirectory,
        )
        val output = ByteArrayOutputStream()
        var expectedSizeBytes: Long? = null
        var offsetBytes = 0L

        while (expectedSizeBytes == null || offsetBytes < expectedSizeBytes) {
            val remainingBudget = byteLimit.toLong() - offsetBytes
            if (remainingBudget <= 0L) {
                error("File is larger than ${formatBytes(byteLimit.toLong())}: $absolutePath")
            }

            val chunk = readWorkspaceFileChunk(
                absolutePath = absolutePath,
                offsetBytes = offsetBytes,
                byteLimit = minOf(
                    WorkspaceTransferChunkBytes,
                    remainingBudget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
                ),
                maxTotalBytes = byteLimit,
                allowedRoot = allowedRoot,
            ).getOrThrow()

            expectedSizeBytes = chunk.sizeBytes
            if (expectedSizeBytes == 0L) {
                break
            }
            if (chunk.bytes.isEmpty()) {
                error("Couldn't read file data from $absolutePath.")
            }

            output.write(chunk.bytes)
            offsetBytes += chunk.bytes.size
        }

        val sizeBytes = expectedSizeBytes ?: 0L
        val bytes = output.toByteArray()
        if (bytes.size.toLong() != sizeBytes) {
            error("Workspace read returned ${bytes.size} bytes, expected $sizeBytes.")
        }

        WorkspaceFilePayload(
            absolutePath = absolutePath,
            bytes = bytes,
            sizeBytes = sizeBytes,
        )
    }

    suspend fun readRootImageFile(
        path: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
        byteLimit: Int,
    ): Result<WorkspaceFilePayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(byteLimit > 0) { "byteLimit must be greater than 0." }
            val absolutePath = resolveTermuxPath(
                path = path,
                workingDirectory = workingDirectory,
            )
            val command = buildRootReadFileCommand(
                absolutePath = absolutePath,
                byteLimit = byteLimit,
            )
            val result = runRootCommand(command)
            if (result.exitCode != 0 || result.timedOut) {
                error(
                    result.combinedOutput().ifBlank {
                        if (result.timedOut) {
                            "Timed out reading image with root."
                        } else {
                            "Couldn't read image with root."
                        }
                    }
                )
            }
            val values = parseStructuredStdout(result.stdout)
            val sizeBytes = values["size_bytes"]?.toLongOrNull()
                ?: error("Root image read didn't report the file size.")
            val contentBase64 = values["content_b64"].orEmpty()
            val bytes = if (contentBase64.isBlank()) {
                ByteArray(0)
            } else {
                Base64.getDecoder().decode(contentBase64)
            }
            if (bytes.size.toLong() != sizeBytes) {
                error("Root image read returned ${bytes.size} bytes, expected $sizeBytes.")
            }
            WorkspaceFilePayload(
                absolutePath = absolutePath,
                bytes = bytes,
                sizeBytes = sizeBytes,
            )
        }
    }

    suspend fun saveWorkspaceFileToDocument(
        path: String,
        destinationUri: Uri,
        byteLimit: Int = MaxWorkspaceDownloadBytes,
    ): Boolean = runCatching {
        require(byteLimit > 0) { "byteLimit must be greater than 0." }

        val absolutePath = resolveTermuxPath(path = path)
        val resolver = context.contentResolver
        val didWrite = resolver.openOutputStream(destinationUri, "w")?.use { output ->
            var expectedSizeBytes: Long? = null
            var bytesWritten = 0L

            while (expectedSizeBytes == null || bytesWritten < expectedSizeBytes) {
                val remainingBudget = (byteLimit.toLong() - bytesWritten).coerceAtLeast(0L)
                val chunk = readWorkspaceFileChunk(
                    absolutePath = absolutePath,
                    offsetBytes = bytesWritten,
                    byteLimit = minOf(
                        WorkspaceTransferChunkBytes,
                        remainingBudget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
                    ),
                    maxTotalBytes = byteLimit,
                ).getOrThrow()

                expectedSizeBytes = chunk.sizeBytes
                if (expectedSizeBytes == 0L) {
                    break
                }
                if (chunk.bytes.isEmpty()) {
                    error("Couldn't read file data from $absolutePath.")
                }

                output.write(chunk.bytes)
                output.flush()
                bytesWritten += chunk.bytes.size
            }

            true
        } ?: false
        if (!didWrite) {
            runCatching { resolver.delete(destinationUri, null, null) }
        }
        didWrite
    }.getOrDefault(false)

    fun resolveWorkspaceDownloadName(rawLink: String): String {
        val normalizedPath = resolveLinkPath(rawLink)
        return normalizedPath.substringAfterLast('/').ifBlank { "download" }
    }

    fun resolveLinkPath(rawLink: String): String =
        resolveTermuxPath(
            path = normalizeFileLink(rawLink),
            workingDirectory = TermuxContract.HomeDirectory,
        )

    fun resolveTermuxPath(
        path: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
    ): String {
        val trimmedPath = path.trim()
        val normalizedWorkingDirectory = normalizeHomePrefix(workingDirectory.trim())
            .ifBlank { TermuxContract.HomeDirectory }

        return when {
            trimmedPath.isBlank() -> normalizedWorkingDirectory
            trimmedPath == "~" -> TermuxContract.HomeDirectory
            trimmedPath.startsWith("~/") -> TermuxContract.HomeDirectory + trimmedPath.removePrefix("~")
            trimmedPath.startsWith("/") -> Paths.get(trimmedPath).normalize().toString()
            else -> Paths.get(normalizedWorkingDirectory).resolve(trimmedPath).normalize().toString()
        }
    }

    fun guessMimeType(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    }

    suspend fun writeWorkspaceBytes(
        absolutePath: String,
        bytes: ByteArray,
    ): Result<Long> = runCatching {
        val pathBase64 = encodeBase64(absolutePath)
        executeUploadCommand(
            command = buildInitWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't prepare $absolutePath in the workspace.",
        )

        val contentBase64 = Base64.getEncoder().encodeToString(bytes)
        contentBase64.chunked(WorkspaceUploadChunkChars).forEach { chunk ->
            executeUploadCommand(
                command = buildAppendWorkspaceUploadChunkCommand(
                    pathBase64 = pathBase64,
                    chunk = chunk,
                ),
                fallbackMessage = "Couldn't append a file chunk for $absolutePath in the workspace.",
            )
        }

        val rawResult = executeUploadCommand(
            command = buildFinalizeWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't finalize $absolutePath in the workspace.",
        )
        val values = parseStructuredStdout(rawResult.optString("stdout"))
        values["bytes_written"]?.toLongOrNull() ?: bytes.size.toLong()
    }

    private suspend fun copyUriToWorkspace(
        sourceUri: Uri,
        absolutePath: String,
        onProgress: (WorkspaceImportProgress) -> Unit,
        onInlineBytes: (ByteArray) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        copyUriToWorkspaceOverHttp(
            sourceUri = sourceUri,
            absolutePath = absolutePath,
            onProgress = onProgress,
            onInlineBytes = onInlineBytes,
        ).getOrElse {
            copyUriToWorkspaceOverBase64(
                sourceUri = sourceUri,
                absolutePath = absolutePath,
                onProgress = onProgress,
                onInlineBytes = onInlineBytes,
            )
        }
    }

    private suspend fun copyUriToWorkspaceOverHttp(
        sourceUri: Uri,
        absolutePath: String,
        onProgress: (WorkspaceImportProgress) -> Unit,
        onInlineBytes: (ByteArray) -> Unit,
    ): Result<Long> = runCatching {
        val uploadServer = WorkspaceHttpUploadServer(
            context = context,
            sourceUri = sourceUri,
            onProgress = onProgress,
        )
        uploadServer.use { server ->
            val command = buildHttpWorkspaceUploadCommand(
                absolutePath = absolutePath,
                url = server.url,
            )
            val rawResult = executeUploadCommand(
                command = command,
                fallbackMessage = "Couldn't stream the selected file into $absolutePath.",
                awaitTimeoutMillis = WorkspaceHttpUploadTimeoutMillis,
            )
            val serverBytes = server.awaitBytesServed()
            if (serverBytes <= MaxAnalyzeInlineBytes) {
                onInlineBytes(server.inlineBytes())
            }
            val values = parseStructuredStdout(rawResult.optString("stdout"))
            values["bytes_written"]?.toLongOrNull() ?: serverBytes
        }
    }

    private suspend fun copyUriToWorkspaceOverBase64(
        sourceUri: Uri,
        absolutePath: String,
        onProgress: (WorkspaceImportProgress) -> Unit,
        onInlineBytes: (ByteArray) -> Unit,
    ): Long {
        val pathBase64 = encodeBase64(absolutePath)
        executeUploadCommand(
            command = buildInitWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't prepare $absolutePath in the workspace.",
        )

        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: error("Couldn't read the selected file.")
        val startedAtMillis = System.currentTimeMillis()
        var lastProgressAtMillis = 0L
        var bytesCopied = 0L
        val inlineBuffer = ByteArrayOutputStream()

        fun maybeEmitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastProgressAtMillis < WorkspaceImportProgressIntervalMillis) return
            val elapsedMillis = (now - startedAtMillis).coerceAtLeast(1L)
            onProgress(
                WorkspaceImportProgress(
                    bytesCopied = bytesCopied,
                    bytesPerSecond = bytesCopied * 1_000L / elapsedMillis,
                )
            )
            lastProgressAtMillis = now
        }

        inputStream.use { stream ->
            val buffer = ByteArray(WorkspaceImportChunkBytes)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                val contentBase64 = Base64.getEncoder().encodeToString(
                    if (read == buffer.size) buffer else buffer.copyOf(read)
                )
                contentBase64.chunked(WorkspaceUploadChunkChars).forEach { chunk ->
                    executeUploadCommand(
                        command = buildAppendWorkspaceUploadChunkCommand(
                            pathBase64 = pathBase64,
                            chunk = chunk,
                        ),
                        fallbackMessage = "Couldn't append a file chunk for $absolutePath in the workspace.",
                    )
                }
                bytesCopied += read.toLong()
                if (inlineBuffer.size() <= MaxAnalyzeInlineBytes) {
                    val remaining = MaxAnalyzeInlineBytes - inlineBuffer.size()
                    if (remaining > 0) {
                        inlineBuffer.write(buffer, 0, minOf(read, remaining))
                    }
                }
                maybeEmitProgress()
            }
        }
        if (bytesCopied <= MaxAnalyzeInlineBytes) {
            onInlineBytes(inlineBuffer.toByteArray())
        }

        val rawResult = executeUploadCommand(
            command = buildFinalizeWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't finalize $absolutePath in the workspace.",
        )
        val values = parseStructuredStdout(rawResult.optString("stdout"))
        val bytesWritten = values["bytes_written"]?.toLongOrNull()
            ?: error("Couldn't determine how many bytes were written to $absolutePath.")
        bytesCopied = bytesWritten
        maybeEmitProgress(force = true)
        return bytesWritten
    }

    private suspend fun executeUploadCommand(
        command: String,
        fallbackMessage: String,
        awaitTimeoutMillis: Long = 15_000L,
    ): JSONObject {
        val rawResult = JSONObject(
            bashTool.executeCommand(
                command = command,
                awaitTimeoutMillis = awaitTimeoutMillis,
            )
        )
        ensureBashSuccess(
            rawResult = rawResult,
            fallbackMessage = fallbackMessage,
        )
        return rawResult
    }

    private fun buildWorkspaceFileName(
        attachmentId: String,
        displayName: String,
    ): String {
        val trimmedName = displayName.trim().ifBlank { "attachment" }
        val dotIndex = trimmedName.lastIndexOf('.')
        val stem = if (dotIndex > 0) trimmedName.substring(0, dotIndex) else trimmedName
        val extension = if (dotIndex > 0 && dotIndex < trimmedName.lastIndex) {
            trimmedName.substring(dotIndex)
        } else {
            ""
        }
        val safeStem = sanitizePathSegment(stem).ifBlank { "attachment" }
        val safeExtension = sanitizeExtension(extension)
        val suffix = attachmentId.substringAfter("attachment-", attachmentId)
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .takeLast(12)
            .ifBlank { System.currentTimeMillis().toString() }
        return "${safeStem}_$suffix$safeExtension"
    }

    private fun sanitizePathSegment(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(80)

    private fun sanitizeExtension(value: String): String {
        if (!value.startsWith('.')) return ""
        val sanitized = value.drop(1).replace(Regex("[^a-zA-Z0-9]"), "").take(12)
        return if (sanitized.isBlank()) "" else ".$sanitized"
    }

    private fun normalizeFileLink(rawLink: String): String {
        val trimmed = rawLink.trim()
        if (trimmed.isBlank()) return ""

        val withoutScheme = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.substring("file://".length)
        } else {
            trimmed
        }
        val withoutLocalhost = when {
            withoutScheme.startsWith("localhost/") -> "/" + withoutScheme.removePrefix("localhost/")
            withoutScheme == "localhost" -> "/"
            else -> withoutScheme
        }
        val decodedPath = URLDecoder.decode(withoutLocalhost, Charsets.UTF_8.name()).trim()
        return decodedPath.replace(Regex(":(\\d+)$"), "")
    }

    private fun normalizeHomePrefix(path: String): String = when {
        path == "~" -> TermuxContract.HomeDirectory
        path.startsWith("~/") -> TermuxContract.HomeDirectory + path.removePrefix("~")
        else -> path
    }

    private fun parseStructuredStdout(stdout: String): Map<String, String> = buildMap {
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

    private fun ensureBashSuccess(
        rawResult: JSONObject,
        fallbackMessage: String,
    ) {
        if (rawResult.optBoolean("ok")) return

        error(
            rawResult.optString("errmsg")
                .ifBlank { rawResult.optString("stderr") }
                .ifBlank { rawResult.optString("hint") }
                .ifBlank { fallbackMessage }
        )
    }

    private fun buildReadWorkspaceFileCommand(
        absolutePath: String,
        byteLimit: Int,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        appendLine("byte_limit=$byteLimit")
        appendLine("if [ ! -e \"\$path\" ]; then")
        appendLine("  printf 'File not found: %s\\n' \"\$path\" >&2")
        appendLine("  exit 20")
        appendLine("fi")
        appendLine("if [ ! -f \"\$path\" ]; then")
        appendLine("  printf 'Path is not a file: %s\\n' \"\$path\" >&2")
        appendLine("  exit 21")
        appendLine("fi")
        appendLine("size_bytes=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("if [ \"\$size_bytes\" -gt \"\$byte_limit\" ]; then")
        appendLine("  printf 'File is larger than %s bytes: %s\\n' \"\$byte_limit\" \"\$path\" >&2")
        appendLine("  exit 22")
        appendLine("fi")
        appendLine("content_b64=\$(base64 < \"\$path\" | tr -d '\\n')")
        appendLine("emit_kv size_bytes \"\$size_bytes\"")
        appendLine("emit_kv content_b64 \"\$content_b64\"")
    }

    private fun buildRootReadFileCommand(
        absolutePath: String,
        byteLimit: Int,
    ): String = buildString {
        appendRootShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        appendLine("byte_limit=$byteLimit")
        appendLine("if [ ! -e \"\$path\" ]; then")
        appendLine("  printf 'File not found: %s\\n' \"\$path\" >&2")
        appendLine("  exit 20")
        appendLine("fi")
        appendLine("if [ ! -f \"\$path\" ]; then")
        appendLine("  printf 'Path is not a file: %s\\n' \"\$path\" >&2")
        appendLine("  exit 21")
        appendLine("fi")
        appendLine("size_bytes=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("if [ \"\$size_bytes\" -gt \"\$byte_limit\" ]; then")
        appendLine("  printf 'Image is larger than %s bytes: %s\\n' \"\$byte_limit\" \"\$path\" >&2")
        appendLine("  exit 22")
        appendLine("fi")
        appendLine("content_b64=\$(base64 < \"\$path\" | tr -d '\\n')")
        appendLine("emit_kv size_bytes \"\$size_bytes\"")
        appendLine("emit_kv content_b64 \"\$content_b64\"")
    }

    private fun runRootCommand(
        command: String,
    ): RootReadCommandResult {
        val process = runCatching {
            ProcessBuilder("su", "-c", command).start()
        }.getOrElse { throwable ->
            return RootReadCommandResult(
                exitCode = -1,
                launchError = throwable.message.orEmpty(),
            )
        }
        var stdout = ""
        var stderr = ""
        val stdoutThread = thread(start = true, name = "aether-root-read-stdout") {
            stdout = runCatching { process.inputStream.bufferedReader().readText() }
                .getOrDefault("")
        }
        val stderrThread = thread(start = true, name = "aether-root-read-stderr") {
            stderr = runCatching { process.errorStream.bufferedReader().readText() }
                .getOrDefault("")
        }
        val finished = process.waitFor(RootReadTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroy() }
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                runCatching { process.destroyForcibly() }
            }
        }
        stdoutThread.join(1_000)
        stderrThread.join(1_000)
        return RootReadCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout,
            stderr = stderr,
            timedOut = !finished,
        )
    }

    private suspend fun readWorkspaceFileChunk(
        absolutePath: String,
        offsetBytes: Long,
        byteLimit: Int,
        maxTotalBytes: Int,
        allowedRoot: String? = null,
    ): Result<WorkspaceFilePayload> = runCatching {
        require(offsetBytes >= 0L) { "offsetBytes must not be negative." }
        require(byteLimit > 0) { "byteLimit must be greater than 0." }
        require(maxTotalBytes > 0) { "maxTotalBytes must be greater than 0." }

        val command = buildReadWorkspaceFileChunkCommand(
            absolutePath = absolutePath,
            offsetBytes = offsetBytes,
            byteLimit = byteLimit,
            maxTotalBytes = maxTotalBytes,
            allowedRoot = allowedRoot,
        )
        val rawResult = JSONObject(bashTool.executeCommand(command))
        ensureBashSuccess(
            rawResult = rawResult,
            fallbackMessage = "Couldn't read $absolutePath from the workspace.",
        )

        val values = parseStructuredStdout(rawResult.optString("stdout"))
        val sizeBytes = values["size_bytes"]?.toLongOrNull()
            ?: error(
                "Workspace read didn't report the file size. " +
                    "stdout_bytes=${rawResult.optString("stdout").toByteArray(Charsets.UTF_8).size}; " +
                    "stderr=${rawResult.optString("stderr").take(160)}"
            )
        val chunkSize = values["chunk_size"]?.toIntOrNull() ?: 0
        val contentBase64 = values["content_b64"].orEmpty()
        val bytes = if (contentBase64.isBlank()) {
            ByteArray(0)
        } else {
            Base64.getDecoder().decode(contentBase64)
        }

        if (chunkSize > 0 && bytes.isEmpty()) {
            error("Workspace read returned an empty data chunk.")
        }
        if (chunkSize != bytes.size) {
            error("Workspace read returned ${bytes.size} bytes, expected $chunkSize.")
        }

        WorkspaceFilePayload(
            absolutePath = absolutePath,
            bytes = bytes,
            sizeBytes = sizeBytes,
        )
    }

    private fun buildReadWorkspaceFileChunkCommand(
        absolutePath: String,
        offsetBytes: Long,
        byteLimit: Int,
        maxTotalBytes: Int,
        allowedRoot: String? = null,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        if (!allowedRoot.isNullOrBlank()) {
            appendLine("allowed_root=\"\$(decode_b64 '${encodeBase64(allowedRoot)}')\"")
            appendLine("allowed_root_real=\"\$(realpath -e \"\$allowed_root\" 2>/dev/null || true)\"")
            appendLine("path_real=\"\$(realpath -e \"\$path\" 2>/dev/null || true)\"")
            appendLine("if [ -z \"\$allowed_root_real\" ] || [ -z \"\$path_real\" ]; then")
            appendLine("  printf 'File or workspace root could not be resolved.\\n' >&2")
            appendLine("  exit 19")
            appendLine("fi")
            appendLine("case \"\$path_real\" in")
            appendLine("  \"\$allowed_root_real\"/*) path=\"\$path_real\" ;;")
            appendLine("  *) printf 'Path is outside the current workspace.\\n' >&2; exit 19 ;;")
            appendLine("esac")
        }
        appendLine("offset_bytes=$offsetBytes")
        appendLine("chunk_limit=$byteLimit")
        appendLine("max_total_bytes=$maxTotalBytes")
        appendLine("if [ ! -e \"\$path\" ]; then")
        appendLine("  printf 'File not found: %s\\n' \"\$path\" >&2")
        appendLine("  exit 20")
        appendLine("fi")
        appendLine("if [ ! -f \"\$path\" ]; then")
        appendLine("  printf 'Path is not a file: %s\\n' \"\$path\" >&2")
        appendLine("  exit 21")
        appendLine("fi")
        appendLine("size_bytes=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("if [ \"\$size_bytes\" -gt \"\$max_total_bytes\" ]; then")
        appendLine("  printf 'File is larger than %s bytes: %s\\n' \"\$max_total_bytes\" \"\$path\" >&2")
        appendLine("  exit 22")
        appendLine("fi")
        appendLine("chunk_size=0")
        appendLine("content_b64=''")
        appendLine("if [ \"\$offset_bytes\" -lt \"\$size_bytes\" ]; then")
        appendLine("  remaining=\$((size_bytes - offset_bytes))")
        appendLine("  chunk_size=\$chunk_limit")
        appendLine("  if [ \"\$remaining\" -lt \"\$chunk_size\" ]; then")
        appendLine("    chunk_size=\$remaining")
        appendLine("  fi")
        appendLine("  content_b64=\$( (tail -c \"+\$((offset_bytes + 1))\" -- \"\$path\" | head -c \"\$chunk_size\" || true) | base64 | tr -d '\\n')")
        appendLine("fi")
        appendLine("emit_kv content_b64 \"\$content_b64\"")
        appendLine("emit_kv size_bytes \"\$size_bytes\"")
        appendLine("emit_kv chunk_size \"\$chunk_size\"")
    }

    private fun buildInitWorkspaceUploadCommand(
        pathBase64: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("parent_dir=\$(dirname -- \"\$path\")")
        appendLine("mkdir -p \"\$parent_dir\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("tmp_path=\"\${path}.aether-tmp\"")
        appendLine("rm -f \"\$tmp_b64\" \"\$tmp_path\"")
        appendLine(": > \"\$tmp_b64\"")
        appendLine("emit_kv stage initialized")
    }

    private fun buildAppendWorkspaceUploadChunkCommand(
        pathBase64: String,
        chunk: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("printf '%s' '$chunk' >> \"\$tmp_b64\"")
        appendLine("emit_kv stage appended")
    }

    private fun buildHttpWorkspaceUploadCommand(
        absolutePath: String,
        url: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        appendLine("url=\"\$(decode_b64 '${encodeBase64(url)}')\"")
        appendLine("parent_dir=\$(dirname -- \"\$path\")")
        appendLine("tmp_path=\"\${path}.aether-tmp\"")
        appendLine("mkdir -p \"\$parent_dir\"")
        appendLine("rm -f \"\$tmp_path\"")
        appendLine("trap 'rm -f \"\$tmp_path\"' EXIT")
        appendLine("if command -v curl >/dev/null 2>&1; then")
        appendLine("  curl -fsSL --connect-timeout 10 --retry 1 --output \"\$tmp_path\" \"\$url\"")
        appendLine("elif command -v wget >/dev/null 2>&1; then")
        appendLine("  wget -q -O \"\$tmp_path\" \"\$url\"")
        appendLine("elif command -v python3 >/dev/null 2>&1; then")
        appendLine("  python3 - \"\$url\" \"\$tmp_path\" <<'PY'")
        appendLine("import shutil, sys, urllib.request")
        appendLine("with urllib.request.urlopen(sys.argv[1], timeout=10) as r, open(sys.argv[2], 'wb') as f:")
        appendLine("    shutil.copyfileobj(r, f, 1024 * 1024)")
        appendLine("PY")
        appendLine("elif command -v python >/dev/null 2>&1; then")
        appendLine("  python - \"\$url\" \"\$tmp_path\" <<'PY'")
        appendLine("import shutil, sys")
        appendLine("try:")
        appendLine("    from urllib.request import urlopen")
        appendLine("except ImportError:")
        appendLine("    from urllib2 import urlopen")
        appendLine("with urlopen(sys.argv[1], timeout=10) as r:")
        appendLine("    with open(sys.argv[2], 'wb') as f:")
        appendLine("        shutil.copyfileobj(r, f, 1024 * 1024)")
        appendLine("PY")
        appendLine("else")
        appendLine("  echo 'No curl, wget, or python is available for workspace upload.' >&2")
        appendLine("  exit 127")
        appendLine("fi")
        appendLine("mv \"\$tmp_path\" \"\$path\"")
        appendLine("trap - EXIT")
        appendLine("bytes_written=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("emit_kv bytes_written \"\$bytes_written\"")
    }

    private fun buildFinalizeWorkspaceUploadCommand(
        pathBase64: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("tmp_path=\"\${path}.aether-tmp\"")
        appendLine("trap 'rm -f \"\$tmp_path\" \"\$tmp_b64\"' EXIT")
        appendLine("base64 -d < \"\$tmp_b64\" > \"\$tmp_path\"")
        appendLine("mv \"\$tmp_path\" \"\$path\"")
        appendLine("bytes_written=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("rm -f \"\$tmp_b64\"")
        appendLine("trap - EXIT")
        appendLine("emit_kv bytes_written \"\$bytes_written\"")
    }

    private fun encodeBase64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun appendCommonShellPreamble(builder: StringBuilder) {
        builder.appendLine("set -euo pipefail")
        builder.appendLine("decode_b64() {")
        builder.appendLine("  printf '%s' \"\$1\" | base64 -d")
        builder.appendLine("}")
        builder.appendLine("emit_kv() {")
        builder.appendLine("  printf '%s=%s\\n' \"\$1\" \"\$2\"")
        builder.appendLine("}")
    }

    private fun appendRootShellPreamble(builder: StringBuilder) {
        builder.appendLine("set -eu")
        builder.appendLine("decode_b64() {")
        builder.appendLine("  printf '%s' \"\$1\" | base64 -d")
        builder.appendLine("}")
        builder.appendLine("emit_kv() {")
        builder.appendLine("  printf '%s=%s\\n' \"\$1\" \"\$2\"")
        builder.appendLine("}")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

private data class RootReadCommandResult(
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

data class ImportedWorkspaceFile(
    val absolutePath: String,
    val bytesCopied: Long,
    val inlineBytes: ByteArray = ByteArray(0),
)

data class WorkspaceImportProgress(
    val bytesCopied: Long,
    val bytesPerSecond: Long,
)

private class WorkspaceHttpUploadServer(
    private val context: Context,
    private val sourceUri: Uri,
    private val onProgress: (WorkspaceImportProgress) -> Unit,
) : AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val result = AtomicReference<Result<Long>?>(null)
    private val inlineBuffer = ByteArrayOutputStream()
    private val worker = thread(
        name = "aether-workspace-upload",
        isDaemon = true,
        start = true,
    ) {
        result.set(runCatching { serveOneRequest() })
        runCatching { serverSocket.close() }
    }

    val url: String =
        "http://127.0.0.1:${serverSocket.localPort}/upload/$token"

    fun awaitBytesServed(): Long {
        worker.join()
        return result.get()?.getOrThrow()
            ?: error("Workspace upload server stopped without a result.")
    }

    fun inlineBytes(): ByteArray = inlineBuffer.toByteArray()

    override fun close() {
        runCatching { serverSocket.close() }
        if (worker.isAlive) {
            worker.join(1000)
        }
    }

    private fun serveOneRequest(): Long {
        serverSocket.soTimeout = 30_000
        serverSocket.accept().use { socket ->
            socket.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine().orEmpty()
            val expectedPath = "/upload/$token"
            val requestedPath = requestLine.split(' ').getOrNull(1).orEmpty()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val output = BufferedOutputStream(socket.getOutputStream())
            if (!requestLine.startsWith("GET ") || requestedPath != expectedPath) {
                output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
                output.flush()
                error("Unexpected workspace upload request.")
            }

            val input = context.contentResolver.openInputStream(sourceUri)
                ?: error("Couldn't read the selected file.")
            val startedAtMillis = System.currentTimeMillis()
            var lastProgressAtMillis = 0L
            var bytesCopied = 0L

            fun maybeEmitProgress(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastProgressAtMillis < WorkspaceImportProgressIntervalMillis) return
                val elapsedMillis = (now - startedAtMillis).coerceAtLeast(1L)
                onProgress(
                    WorkspaceImportProgress(
                        bytesCopied = bytesCopied,
                        bytesPerSecond = bytesCopied * 1_000L / elapsedMillis,
                    )
                )
                lastProgressAtMillis = now
            }

            output.write("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            input.use { stream ->
                val buffer = ByteArray(WorkspaceHttpUploadBufferBytes)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesCopied += read.toLong()
                    if (inlineBuffer.size() <= MaxAnalyzeInlineBytes) {
                        val remaining = MaxAnalyzeInlineBytes - inlineBuffer.size()
                        if (remaining > 0) {
                            inlineBuffer.write(buffer, 0, minOf(read, remaining))
                        }
                    }
                    maybeEmitProgress()
                }
            }
            output.flush()
            maybeEmitProgress(force = true)
            return bytesCopied
        }
    }
}

data class WorkspaceFilePayload(
    val absolutePath: String,
    val bytes: ByteArray,
    val sizeBytes: Long,
)
