package com.zhousl.aether.runtime

import kotlinx.coroutines.flow.Flow

enum class RuntimeProcessSignal {
    Interrupt,
    Terminate,
    Kill,
}

data class RuntimeProcessSpec(
    val executable: String,
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String = "/root",
    val redirectErrorStream: Boolean = false,
    val interactiveTerminal: Boolean = false,
    val remoteDebuggingPipe: Boolean = false,
)

data class RuntimeProcessExit(
    val exitCode: Int,
    val signal: RuntimeProcessSignal? = null,
    val signalNumber: Int = 0,
)

interface RuntimeProcess {
    val pid: Int
    val stdout: Flow<ByteArray>
    val stderr: Flow<ByteArray>

    suspend fun writeStdin(bytes: ByteArray)
    suspend fun closeStdin()
    suspend fun awaitExit(): RuntimeProcessExit
    suspend fun signal(signal: RuntimeProcessSignal)
    suspend fun resize(columns: Int, rows: Int) = Unit
}

class RuntimeProcessStdinException(
    val processId: Int,
    message: String = "Runtime process $processId rejected stdin.",
) : IllegalStateException(message)

interface RuntimeFileSystem {
    suspend fun exists(path: String): Boolean
    suspend fun createDirectories(path: String)
    suspend fun read(path: String): ByteArray
    suspend fun read(path: String, maximumBytes: Long): ByteArray {
        require(maximumBytes >= 0) { "maximumBytes must not be negative." }
        return read(path).also { bytes ->
            require(bytes.size.toLong() <= maximumBytes) { "File exceeds the allowed size." }
        }
    }
    suspend fun readPrefix(path: String, maximumBytes: Long): ByteArray {
        require(maximumBytes >= 0) { "maximumBytes must not be negative." }
        if (maximumBytes == 0L) return ByteArray(0)
        val bytes = read(path)
        return bytes.copyOf(minOf(bytes.size.toLong(), maximumBytes).toInt())
    }
    suspend fun write(path: String, content: ByteArray, executable: Boolean = false)
    suspend fun writeWithProgress(
        path: String,
        content: ByteArray,
        executable: Boolean = false,
        onProgress: (Long) -> Unit,
    ) {
        write(path, content, executable)
        onProgress(content.size.toLong())
    }
    suspend fun remove(path: String, recursive: Boolean = false)
    suspend fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean = false)
}

/**
 * Runtime contract used by shared code during the incremental migration.
 *
 * The Android app still owns a legacy `LocalRuntime` with a different API.
 * Keeping a distinct name avoids publishing two classes with the same JVM
 * name while Android call sites are moved over in later migration slices.
 */
interface MultiplatformLocalRuntime {
    val homeDirectory: String
    val workspaceRoot: String
    val fileSystem: RuntimeFileSystem

    suspend fun isReady(): Boolean = fileSystem.exists("/etc/alpine-release")
    suspend fun initialize(onProgress: (RuntimeSetupProgress) -> Unit = {})
    suspend fun reset() {
        throw UnsupportedOperationException("This runtime cannot be reset.")
    }
    suspend fun startProcess(spec: RuntimeProcessSpec): RuntimeProcess
}

data class RuntimeSetupProgress(
    val phase: String,
    val detail: String = "",
    val fraction: Float? = null,
    val output: String = "",
)

interface PiBridgeTransport {
    suspend fun start(): RuntimeProcess
    suspend fun stop()
}
