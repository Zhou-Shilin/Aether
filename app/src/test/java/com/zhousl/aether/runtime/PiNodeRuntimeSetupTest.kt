package com.zhousl.aether.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiNodeRuntimeSetupTest {
    @Test
    fun successfulApkInstallWithMissingNodeRepairsFiles() = withSandbox { directory ->
        val result = install(directory, repairWorks = true)
        assertEquals(result.second, 0, result.first)
        assertTrue(File(directory, "calls").readText().contains("fix --no-cache --reinstall --depends nodejs npm"))
    }

    @Test
    fun brokenNodeAfterRepairIsStillAFailure() = withSandbox { directory ->
        val result = install(directory, repairWorks = false)
        assertTrue(result.second, result.first != 0)
        assertTrue(result.second.contains("node executable missing"))
    }

    @Test
    fun healthyInstallationDoesNotReinstallDependencies() = withSandbox { directory ->
        File(directory, "healthy").writeText("")
        val result = install(directory, repairWorks = true)
        assertEquals(result.second, 0, result.first)
        assertFalse(File(directory, "calls").readText().contains("fix "))
    }

    @Test
    fun apkFailureIsNotHiddenByExistingExecutables() = withSandbox { directory ->
        File(directory, "healthy").writeText("")
        val result = install(directory, repairWorks = true, apkExit = 7)
        assertEquals(result.second, 7, result.first)
        assertFalse(File(directory, "calls").readText().contains("fix "))
    }

    @Test
    fun iosInstallAlsoRepairsMissingExecutables() = withSandbox { directory ->
        val result = install(directory, repairWorks = true, noChown = false)
        assertEquals(result.second, 0, result.first)
        val calls = File(directory, "calls").readText()
        assertTrue(calls.contains("fix --no-cache --reinstall --depends nodejs npm"))
        assertFalse(calls.contains("--no-chown"))
    }

    private fun install(
        directory: File,
        repairWorks: Boolean,
        apkExit: Int = 0,
        noChown: Boolean = true,
    ): Pair<Int, String> {
        fun executable(name: String, script: String) {
            File(directory, name).apply {
                writeText("#!/bin/sh\n$script\n")
                setExecutable(true)
            }
        }
        executable("node", """
            if [ -f "${'$'}SANDBOX/healthy" ]; then echo v22.19.0; exit 0; fi
            echo 'node executable missing' >&2
            exit 127
        """.trimIndent())
        executable("npm", "echo 10.0.0")
        executable("apk", """
            echo "${'$'}*" >> "${'$'}SANDBOX/calls"
            if [ "${'$'}1" = fix ] && $repairWorks; then touch "${'$'}SANDBOX/healthy"; fi
            exit $apkExit
        """.trimIndent())
        val process = ProcessBuilder("/bin/sh", "-c", PiNodeRuntimeSetup.installCommand(noChown = noChown))
            .redirectErrorStream(true).apply {
                environment()["SANDBOX"] = directory.absolutePath
                environment()["PATH"] = "${directory.absolutePath}:/usr/bin:/bin"
            }.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }

    private fun withSandbox(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("aether-node-setup-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
