package com.zhousl.aether.runtime

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class IosAlpineRuntime(
    private val host: NativeRuntimeHost,
) : MultiplatformLocalRuntime {
    override val homeDirectory: String = "/root"
    override val workspaceRoot: String = "/workspace"
    override val fileSystem: RuntimeFileSystem = IosRuntimeFileSystem(host)

    override suspend fun isReady(): Boolean = suspendCancellableCoroutine { continuation ->
        host.isRuntimeReady(object : NativeBooleanResultListener {
            override fun onSuccess(value: Boolean) = continuation.resume(value)
            override fun onError(message: String) = continuation.resumeFailure(message)
        })
    }

    override suspend fun initialize(onProgress: (RuntimeSetupProgress) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            var output = ""
            var latest = RuntimeSetupProgress("idle")

            fun appendOutput(text: String, notify: Boolean = true) {
                output = (output + text).takeLast(MaxSetupOutputCharacters)
                if (notify) {
                    latest = latest.copy(output = output)
                    onProgress(latest)
                }
            }

            host.initialize(object : NativeRuntimeInitializationListener {
                override fun onProgress(phase: String, detail: String, fraction: Double) {
                    val percentage = (fraction * 100).toInt().coerceIn(0, 100)
                    appendOutput("[$percentage%] $detail\n", notify = false)
                    latest = RuntimeSetupProgress(
                        phase = phase,
                        detail = detail,
                        fraction = fraction.toFloat(),
                        output = output,
                    )
                    onProgress(latest)
                }

                override fun onOutput(text: String) = appendOutput(text)

                override fun onReady() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(message: String) {
                    appendOutput("[error] $message\n")
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(message))
                    }
                }
            })
        }
    }

    override suspend fun reset() = suspendCancellableCoroutine { continuation ->
        host.resetRuntime(object : NativeUnitResultListener {
            override fun onSuccess() = continuation.resume(Unit)
            override fun onError(message: String) = continuation.resumeFailure(message)
        })
    }

    private companion object {
        const val MaxSetupOutputCharacters = 120_000
        const val RuntimeStderrBufferChunks = 64
    }

    override suspend fun startProcess(spec: RuntimeProcessSpec): RuntimeProcess {
        val stdout = Channel<ByteArray>(Channel.UNLIMITED)
        val stderr = Channel<ByteArray>(
            capacity = RuntimeStderrBufferChunks,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val exit = CompletableDeferred<RuntimeProcessExit>()
        val listener = object : NativeRuntimeProcessListener {
            override fun onStdout(bytes: ByteArray) {
                stdout.trySend(bytes)
            }

            override fun onStderr(bytes: ByteArray) {
                stderr.trySend(bytes)
            }

            override fun onExit(exitCode: Int, signal: Int) {
                stdout.close()
                stderr.close()
                exit.complete(RuntimeProcessExit(exitCode, signal.toRuntimeSignal(), signal))
            }
        }
        val processId = host.startProcess(
            executable = spec.executable,
            arguments = spec.arguments,
            environment = spec.environment,
            workingDirectory = spec.workingDirectory,
            redirectErrorStream = spec.redirectErrorStream,
            interactiveTerminal = spec.interactiveTerminal,
            remoteDebuggingPipe = spec.remoteDebuggingPipe,
            listener = listener,
        )
        check(processId >= 0) { "Unable to start Alpine process: $processId" }
        return IosRuntimeProcess(
            processId = processId,
            host = host,
            stdout = stdout.receiveAsFlow(),
            stderr = stderr.receiveAsFlow(),
            exit = exit,
        )
    }
}

private class IosRuntimeProcess(
    processId: Long,
    private val host: NativeRuntimeHost,
    override val stdout: Flow<ByteArray>,
    override val stderr: Flow<ByteArray>,
    private val exit: CompletableDeferred<RuntimeProcessExit>,
) : RuntimeProcess {
    override val pid: Int = processId.toInt()

    override suspend fun writeStdin(bytes: ByteArray) {
        if (!host.writeStdin(pid.toLong(), bytes)) {
            throw RuntimeProcessStdinException(pid, "Alpine process $pid rejected stdin.")
        }
    }

    override suspend fun closeStdin() = host.closeStdin(pid.toLong())

    override suspend fun awaitExit(): RuntimeProcessExit = exit.await()

    override suspend fun signal(signal: RuntimeProcessSignal) = host.signal(pid.toLong(), signal.nativeValue)

    override suspend fun resize(columns: Int, rows: Int) {
        host.resizeTerminal(pid.toLong(), columns.coerceAtLeast(1), rows.coerceAtLeast(1))
    }
}

private class IosRuntimeFileSystem(
    private val host: NativeRuntimeHost,
) : RuntimeFileSystem {
    override suspend fun exists(path: String): Boolean = suspendCancellableCoroutine { continuation ->
        host.fileExists(path, object : NativeBooleanResultListener {
            override fun onSuccess(value: Boolean) = continuation.resume(value)
            override fun onError(message: String) = continuation.resumeFailure(message)
        })
    }

    override suspend fun createDirectories(path: String) = unitCall { listener ->
        host.createDirectories(path, listener)
    }

    override suspend fun read(path: String): ByteArray = suspendCancellableCoroutine { continuation ->
        host.readFile(path, object : NativeBytesResultListener {
            override fun onSuccess(value: ByteArray) = continuation.resume(value)
            override fun onError(message: String) = continuation.resumeFailure(message)
        })
    }

    override suspend fun read(path: String, maximumBytes: Long): ByteArray =
        suspendCancellableCoroutine { continuation ->
            host.readFile(path, maximumBytes, object : NativeBytesResultListener {
                override fun onSuccess(value: ByteArray) = continuation.resume(value)
                override fun onError(message: String) = continuation.resumeFailure(message)
            })
        }

    override suspend fun readPrefix(path: String, maximumBytes: Long): ByteArray =
        suspendCancellableCoroutine { continuation ->
            host.readFilePrefix(path, maximumBytes, object : NativeBytesResultListener {
                override fun onSuccess(value: ByteArray) = continuation.resume(value)
                override fun onError(message: String) = continuation.resumeFailure(message)
            })
        }

    override suspend fun write(path: String, content: ByteArray, executable: Boolean) = unitCall { listener ->
        host.writeFile(path, content, executable, listener)
    }

    override suspend fun writeWithProgress(
        path: String,
        content: ByteArray,
        executable: Boolean,
        onProgress: (Long) -> Unit,
    ) = suspendCancellableCoroutine { continuation ->
        host.writeFileWithProgress(path, content, executable, object : NativeFileWriteListener {
            override fun onProgress(bytesCopied: Long) = onProgress(bytesCopied)
            override fun onSuccess() = continuation.resume(Unit)
            override fun onError(message: String) = continuation.resumeFailure(message)
        })
    }

    override suspend fun remove(path: String, recursive: Boolean) = unitCall { listener ->
        host.remove(path, recursive, listener)
    }

    override suspend fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean) =
        unitCall { listener -> host.bindHostDirectory(hostPath, guestPath, readOnly, listener) }

    private suspend fun unitCall(block: (NativeUnitResultListener) -> Unit) =
        suspendCancellableCoroutine { continuation ->
            block(object : NativeUnitResultListener {
                override fun onSuccess() = continuation.resume(Unit)
                override fun onError(message: String) = continuation.resumeFailure(message)
            })
        }
}

private val RuntimeProcessSignal.nativeValue: Int
    get() = when (this) {
        RuntimeProcessSignal.Interrupt -> 2
        RuntimeProcessSignal.Terminate -> 15
        RuntimeProcessSignal.Kill -> 9
    }

private fun Int.toRuntimeSignal(): RuntimeProcessSignal? = when (this) {
    2 -> RuntimeProcessSignal.Interrupt
    15 -> RuntimeProcessSignal.Terminate
    9 -> RuntimeProcessSignal.Kill
    else -> null
}

private fun <T> kotlin.coroutines.Continuation<T>.resumeFailure(message: String) {
    resumeWithException(IllegalStateException(message))
}
