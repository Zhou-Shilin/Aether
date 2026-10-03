package com.zhousl.aether.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Starts the same long-lived JavaScript bridge on every local runtime. */
class RuntimePiBridgeTransport(
    private val runtime: MultiplatformLocalRuntime,
    private val nodeExecutable: String = "/usr/bin/node",
    private val bridgePath: String = "/root/.aether/pi-bridge/bridge.mjs",
    // Extra VM flags for the Node process. Memory-capped on constrained
    // devices so a leaking agent session degrades instead of triggering
    // an OS-level jetsam kill of the whole app.
    private val nodeArguments: List<String> = emptyList(),
    private val shutdownTimeoutMillis: Long = 2_000,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PiBridgeTransport {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private var activeProcess: RuntimeProcess? = null
    private var exitMonitor: Job? = null

    override suspend fun start(): RuntimeProcess = mutex.withLock {
        activeProcess?.let { return@withLock it }

        check(runtime.isReady()) {
            "Initialize Alpine before starting the agent runtime."
        }
        // iOS still needs to activate an already-installed iSH runtime for this
        // process. The readiness check above prevents this call from installing
        // Alpine implicitly when agent startup is what reached the bridge.
        runtime.initialize()
        check(runtime.fileSystem.exists(bridgePath)) {
            "Pi Bridge is not installed at $bridgePath."
        }
        runtime.startProcess(
            RuntimeProcessSpec(
                executable = nodeExecutable,
                arguments = nodeArguments + listOf(bridgePath),
                environment = mapOf(
                    "HOME" to runtime.homeDirectory,
                    "AETHER_WORKSPACE" to runtime.workspaceRoot,
                    "NODE_COMPILE_CACHE" to "${runtime.homeDirectory}/.aether/node-compile-cache",
                ),
                workingDirectory = bridgePath.substringBeforeLast('/'),
            ),
        ).also { started ->
            activeProcess = started
            exitMonitor?.cancel()
            exitMonitor = scope.launch {
                runCatching { started.awaitExit() }
                mutex.withLock {
                    if (activeProcess === started) {
                        activeProcess = null
                        exitMonitor = null
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        val process = mutex.withLock {
            exitMonitor?.cancel()
            exitMonitor = null
            activeProcess.also { activeProcess = null }
        } ?: return

        process.closeStdin()
        process.signal(RuntimeProcessSignal.Terminate)
        val exited = withTimeoutOrNull(shutdownTimeoutMillis) {
            process.awaitExit()
        }
        if (exited == null) {
            process.signal(RuntimeProcessSignal.Kill)
            withTimeoutOrNull(shutdownTimeoutMillis) { process.awaitExit() }
        }
    }
}
