package com.zhousl.aether.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RuntimeFileSystemTest {
    private val fileSystem = object : RuntimeFileSystem {
        override suspend fun exists(path: String): Boolean = true
        override suspend fun createDirectories(path: String) = Unit
        override suspend fun read(path: String): ByteArray = byteArrayOf(1, 2, 3, 4)
        override suspend fun write(path: String, content: ByteArray, executable: Boolean) = Unit
        override suspend fun remove(path: String, recursive: Boolean) = Unit
        override suspend fun bindHostDirectory(hostPath: String, guestPath: String, readOnly: Boolean) = Unit
    }

    @Test
    fun defaultReadPrefixTruncatesWithoutRejectingTheFile() = runTest {
        assertContentEquals(byteArrayOf(1, 2), fileSystem.readPrefix("/file", 2))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), fileSystem.readPrefix("/file", 20))
    }

    @Test
    fun defaultReadPrefixSupportsZeroAndRejectsNegativeLimits() = runTest {
        assertContentEquals(byteArrayOf(), fileSystem.readPrefix("/file", 0))
        assertFailsWith<IllegalArgumentException> { fileSystem.readPrefix("/file", -1) }
    }
}
