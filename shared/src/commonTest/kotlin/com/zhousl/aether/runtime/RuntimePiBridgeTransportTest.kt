package com.zhousl.aether.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class RuntimePiBridgeTransportTest {
    @Test
    fun startsBridgeOnceWithSharedRuntimeContract() = runTest {
        val runtime = FakeRuntime(bridgeInstalled = true)
        val transport = RuntimePiBridgeTransport(runtime, dispatcher = StandardTestDispatcher(testScheduler))

        val first = transport.start()
        val second = transport.start()

        assertSame(first, second)
        assertEquals(1, runtime.initializeCount)
        assertEquals("/usr/bin/node", runtime.lastSpec?.executable)
        assertEquals(listOf("/root/.aether/pi-bridge/bridge.mjs"), runtime.lastSpec?.arguments)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startsANewBridgeAfterThePreviousProcessExits() = runTest {
        val runtime = FakeRuntime(bridgeInstalled = true)
        val transport = RuntimePiBridgeTransport(runtime, dispatcher = StandardTestDispatcher(testScheduler))

        val first = transport.start() as FakeProcess
        first.completeExit()
        runCurrent()
        val second = transport.start()

        assertEquals(2, runtime.startCount)
        kotlin.test.assertNotSame(first, second)
    }

    @Test
    fun refusesToStartWhenBridgeAssetWasNotInstalled() = runTest {
        val transport = RuntimePiBridgeTransport(FakeRuntime(bridgeInstalled = false))

        assertFailsWith<IllegalStateException> { transport.start() }
    }
}

private class FakeRuntime(
    private val bridgeInstalled: Boolean,
) : MultiplatformLocalRuntime {
    override val homeDirectory = "/root"
    override val workspaceRoot = "/workspace"
    override val fileSystem: RuntimeFileSystem = object : RuntimeFileSystem {
        override suspend fun exists(path: String) = bridgeInstalled
        override suspend fun createDirectories(path: String) = Unit
        override suspend fun read(path: String) = ByteArray(0)
        override suspend fun write(path: String, content: ByteArray, executable: Boolean) = Unit
        override suspend fun remove(path: String, recursive: Boolean) = Unit
        override suspend fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean) = Unit
    }
    var initializeCount = 0
    var startCount = 0
    var lastSpec: RuntimeProcessSpec? = null

    override suspend fun initialize(onProgress: (RuntimeSetupProgress) -> Unit) {
        initializeCount++
    }

    override suspend fun startProcess(spec: RuntimeProcessSpec): RuntimeProcess {
        startCount++
        lastSpec = spec
        return FakeProcess()
    }
}

private class FakeProcess : RuntimeProcess {
    override val pid = 42
    override val stdout: Flow<ByteArray> = emptyFlow()
    override val stderr: Flow<ByteArray> = emptyFlow()
    private val exit = CompletableDeferred<RuntimeProcessExit>()

    fun completeExit() {
        exit.complete(RuntimeProcessExit(0))
    }

    override suspend fun writeStdin(bytes: ByteArray) = Unit
    override suspend fun closeStdin() = Unit
    override suspend fun awaitExit() = exit.await()
    override suspend fun signal(signal: RuntimeProcessSignal) = Unit
}
