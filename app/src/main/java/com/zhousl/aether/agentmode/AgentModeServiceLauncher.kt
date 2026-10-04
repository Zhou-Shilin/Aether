package com.zhousl.aether.agentmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.Process as AndroidProcess
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

private const val ProcessPollIntervalMillis = 200L
private const val MaxErrorOutputChars = 4_000

/** A running Agent Mode service process and the binder it published. */
internal class AgentModeServiceHandle(
    val service: IAetherAgentModeService,
    private val process: Process,
) {
    fun destroy() {
        // destroy() exits the remote process, so the call itself ends in a DeadObjectException.
        runCatching { service.destroy() }
        runCatching { process.destroy() }
    }
}

/**
 * Starts [AgentModeServiceStarter] in a privileged process and waits for its binder. Shizuku and
 * root share this path; they differ only in how the `app_process` command is executed.
 */
internal object AgentModeServiceLauncher {
    /**
     * Held for the lifetime of the Aether process. The service links to its death, so the
     * privileged process exits together with Aether.
     */
    private val clientToken = Binder()

    suspend fun launch(
        context: Context,
        timeoutMillis: Long,
        startProcess: (command: List<String>) -> Process,
    ): AgentModeServiceHandle {
        val packageName = context.packageName
        val token = UUID.randomUUID().toString()
        val received = CompletableDeferred<IAetherAgentModeService>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getStringExtra(AgentModeServiceProtocol.ExtraToken) != token) return
                val binder = intent.extras?.getBinder(AgentModeServiceProtocol.ExtraBinder)
                val service = IAetherAgentModeService.Stub.asInterface(binder)
                if (service == null) {
                    received.completeExceptionally(
                        IllegalStateException("Agent Mode service returned an invalid binder.")
                    )
                } else {
                    received.complete(service)
                }
            }
        }
        val worker = HandlerThread("AgentModeServiceLauncher").apply { start() }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AgentModeServiceProtocol.readyAction(packageName)),
            AgentModeServiceProtocol.SenderPermission,
            Handler(worker.looper),
            // Shell (Shizuku) is a different UID, so the receiver must be exported; the sender
            // permission and the per-launch token keep other apps out.
            ContextCompat.RECEIVER_EXPORTED,
        )
        var process: Process? = null
        val output = mutableListOf<ProcessOutputCollector>()
        fun describeOutput() = output.joinToString("") { it.describe() }
        try {
            val started = startProcess(command(context, token))
            process = started
            // Drain both streams: stderr explains a failed start, and an undrained stdout pipe
            // would eventually block the service process.
            output += ProcessOutputCollector(started.errorStream).also { it.start() }
            output += ProcessOutputCollector(started.inputStream).also { it.start() }
            val service = withTimeout(timeoutMillis) {
                while (!received.isCompleted) {
                    if (!started.isAlive) {
                        delay(ProcessPollIntervalMillis)
                        if (received.isCompleted) break
                        error(
                            "Agent Mode service process exited with code " +
                                "${runCatching { started.exitValue() }.getOrDefault(-1)}." +
                                describeOutput()
                        )
                    }
                    delay(ProcessPollIntervalMillis)
                }
                received.await()
            }
            service.linkClient(clientToken)
            return AgentModeServiceHandle(service, started)
        } catch (throwable: Throwable) {
            runCatching { process?.destroy() }
            if (throwable is TimeoutCancellationException) {
                throw IllegalStateException(
                    "Timed out starting Agent Mode service after $timeoutMillis ms." + describeOutput(),
                    throwable,
                )
            }
            throw throwable
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            worker.quitSafely()
        }
    }

    private fun command(context: Context, token: String): List<String> = listOf(
        "/system/bin/app_process",
        "-Djava.class.path=${context.applicationInfo.sourceDir}",
        "/system/bin",
        "--nice-name=${context.packageName}:agentmode",
        AgentModeServiceStarter::class.java.name,
        "--package=${context.packageName}",
        "--user=${AndroidProcess.myUid() / AgentModeServiceProtocol.PerUserRange}",
        "--token=$token",
    )

    /** Runs [command] through Shizuku's remote process API, as the Shizuku server's UID. */
    fun startShizukuProcess(command: List<String>): Process {
        val shizuku = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            ?: error("Shizuku is not running.")
        return ShizukuProcess(shizuku.newProcess(command.toTypedArray(), null, null))
    }

    /** Runs [command] in a root shell started with [suPath]. */
    fun startRootProcess(suPath: String, command: List<String>): Process {
        val process = ProcessBuilder(suPath).start()
        process.outputStream.bufferedWriter().use { writer ->
            writer.write("exec " + command.joinToString(" ") { shellQuote(it) })
            writer.newLine()
        }
        return process
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

/** Drains one output stream of the service process, keeping the start of it for diagnostics. */
private class ProcessOutputCollector(private val stream: InputStream) : Thread("AgentModeServiceOutput") {
    private val buffer = StringBuilder()

    init {
        isDaemon = true
    }

    override fun run() {
        runCatching {
            stream.bufferedReader().forEachLine { line ->
                synchronized(buffer) {
                    if (buffer.length < MaxErrorOutputChars) buffer.appendLine(line)
                }
            }
        }
    }

    fun describe(): String {
        val text = synchronized(buffer) { buffer.toString().trim() }
        return if (text.isEmpty()) "" else " Process output: ${text.take(MaxErrorOutputChars)}"
    }
}

/** [Process] view of a Shizuku [IRemoteProcess]. */
private class ShizukuProcess(private val remote: IRemoteProcess) : Process() {
    override fun getOutputStream(): OutputStream =
        ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)

    override fun getInputStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)

    override fun getErrorStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)

    override fun waitFor(): Int = remote.waitFor()

    override fun exitValue(): Int = remote.exitValue()

    override fun isAlive(): Boolean = runCatching { remote.alive() }.getOrDefault(false)

    override fun destroy() {
        remote.destroy()
    }
}
