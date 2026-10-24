package com.zhousl.aether.data

import java.io.OutputStream

internal fun writeExportDocument(text: String, openOutputStream: (String) -> OutputStream?): Boolean =
    runCatching {
        // Cloud and pipe-backed providers may only accept "w" and may not support fsync.
        val stream = runCatching { openOutputStream("rwt") }.getOrNull()
            ?: openOutputStream("w")
            ?: return false
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        true
    }.getOrDefault(false)
