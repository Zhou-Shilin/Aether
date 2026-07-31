package com.zhousl.aether.runtime

/**
 * Callback-only ABI implemented by the iOS host. Keeping coroutines out of this
 * boundary makes the generated Objective-C/Swift API stable and easy to test.
 */
interface NativeRuntimeHost {
    fun isRuntimeReady(listener: NativeBooleanResultListener)
    fun initialize(listener: NativeRuntimeInitializationListener)
    fun resetRuntime(listener: NativeUnitResultListener)
    fun startProcess(
        executable: String,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDirectory: String,
        redirectErrorStream: Boolean,
        interactiveTerminal: Boolean,
        remoteDebuggingPipe: Boolean,
        listener: NativeRuntimeProcessListener,
    ): Long
    fun writeStdin(processId: Long, bytes: ByteArray): Boolean
    fun closeStdin(processId: Long)
    fun signal(processId: Long, signal: Int)
    fun resizeTerminal(processId: Long, columns: Int, rows: Int)
    fun beginBackgroundExecution(name: String, listener: NativeBackgroundExecutionListener): String
    fun updateBackgroundExecution(identifier: String, detail: String)
    fun endBackgroundExecution(identifier: String, success: Boolean)

    fun fileExists(path: String, listener: NativeBooleanResultListener)
    fun createDirectories(path: String, listener: NativeUnitResultListener)
    fun readFile(path: String, listener: NativeBytesResultListener)
    fun readFile(path: String, maximumBytes: Long, listener: NativeBytesResultListener)
    fun readFilePrefix(path: String, maximumBytes: Long, listener: NativeBytesResultListener)
    fun writeFile(path: String, bytes: ByteArray, executable: Boolean, listener: NativeUnitResultListener)
    fun writeFileWithProgress(
        path: String,
        bytes: ByteArray,
        executable: Boolean,
        listener: NativeFileWriteListener,
    )
    fun remove(path: String, recursive: Boolean, listener: NativeUnitResultListener)
    fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean, listener: NativeUnitResultListener)
    fun pickFile(imagesOnly: Boolean, listener: NativePickedFileListener)
    fun pickFiles(imagesOnly: Boolean, listener: NativePickedFilesListener)
    fun pickDirectory(listener: NativePickedDirectoryListener)
    fun exportFile(name: String, mimeType: String, bytes: ByteArray, listener: NativeFileExportListener)
    fun copyText(text: String): Boolean
    fun shareText(title: String, text: String): Boolean
    fun shareFile(name: String, mimeType: String, bytes: ByteArray): Boolean
    fun previewFile(name: String, mimeType: String, bytes: ByteArray): Boolean
    fun openUrl(url: String): Boolean
    fun terminateApplication(): Boolean
}

interface NativeRuntimeInitializationListener {
    fun onProgress(phase: String, detail: String, fraction: Double)
    fun onOutput(text: String)
    fun onReady()
    fun onError(message: String)
}

interface NativeRuntimeProcessListener {
    fun onStdout(bytes: ByteArray)
    fun onStderr(bytes: ByteArray)
    fun onExit(exitCode: Int, signal: Int)
}

interface NativeBackgroundExecutionListener {
    fun onExpired()
}

interface NativeBooleanResultListener {
    fun onSuccess(value: Boolean)
    fun onError(message: String)
}

interface NativeBytesResultListener {
    fun onSuccess(value: ByteArray)
    fun onError(message: String)
}

interface NativeUnitResultListener {
    fun onSuccess()
    fun onError(message: String)
}

interface NativeFileWriteListener {
    fun onProgress(bytesCopied: Long)
    fun onSuccess()
    fun onError(message: String)
}

interface NativePickedFileListener {
    fun onSelected(name: String, mimeType: String, bytes: ByteArray)
    fun onCancelled()
    fun onError(message: String)
}

interface NativePickedFilesListener {
    fun onSelected(name: String, mimeType: String, bytes: ByteArray)
    fun onCompleted()
    fun onCancelled()
    fun onError(message: String)
}

interface NativePickedDirectoryListener {
    fun onSelected(relativePath: String, mimeType: String, bytes: ByteArray)
    fun onCompleted(name: String)
    fun onCancelled()
    fun onError(message: String)
}

interface NativeFileExportListener {
    fun onCompleted()
    fun onCancelled()
    fun onError(message: String)
}
