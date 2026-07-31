package com.zhousl.aether.channel

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MaxChannelInboundFileBytes = 32L * 1024L * 1024L
private const val ChannelStagingMaxAgeMillis = 24L * 60L * 60L * 1_000L

/** Stores expiring platform downloads outside model/runtime credentials. */
class ChannelInboundFileStore(
    private val root: File = File(
        System.getProperty("java.io.tmpdir") ?: ".",
        "aether-channel-inbox",
    ),
) {
    init {
        root.mkdirs()
        cleanupExpired()
    }

    suspend fun readBytes(
        declaredSize: Long? = null,
        input: () -> InputStream,
    ): ByteArray = withContext(Dispatchers.IO) {
        declaredSize?.let {
            require(it in 0..MaxChannelInboundFileBytes) {
                "Channel attachment exceeds the 32 MB limit"
            }
        }
        input().use { source ->
            ByteArrayOutputStream(
                declaredSize?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
                    ?: DEFAULT_BUFFER_SIZE,
            ).use { destination ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    copied += read
                    require(copied <= MaxChannelInboundFileBytes) {
                        "Channel attachment exceeds the 32 MB limit"
                    }
                    destination.write(buffer, 0, read)
                }
                destination.toByteArray()
            }
        }
    }

    suspend fun save(
        channel: ChannelKind,
        messageId: String,
        name: String,
        mimeType: String,
        kind: ChannelFileKind,
        declaredSize: Long? = null,
        input: () -> InputStream,
    ): ChannelIncomingAttachment = withContext(Dispatchers.IO) {
        declaredSize?.let {
            require(it in 0..MaxChannelInboundFileBytes) {
                "Channel attachment exceeds the 32 MB limit"
            }
        }
        val safeName = safeFileName(name, kind)
        val id = "${channel.storageValue}-${UUID.randomUUID()}"
        val directory = File(root, channel.storageValue).apply { mkdirs() }
        val target = File(directory, "${id.takeLast(36)}-$safeName")
        val temporary = File(directory, ".${target.name}.part")
        var copied = 0L
        try {
            input().use { source ->
                temporary.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        copied += read
                        require(copied <= MaxChannelInboundFileBytes) {
                            "Channel attachment exceeds the 32 MB limit"
                        }
                        destination.write(buffer, 0, read)
                    }
                }
            }
            require(copied > 0L) { "Channel attachment is empty" }
            check(temporary.renameTo(target)) { "Could not finish the channel attachment download" }
            ChannelIncomingAttachment(
                id = "$id-${messageId.hashCode().toUInt().toString(16)}",
                name = safeName,
                mimeType = mimeType.substringBefore(';').trim().ifBlank {
                    URLConnection.guessContentTypeFromName(safeName) ?: "application/octet-stream"
                },
                kind = kind,
                localPath = target.absolutePath,
                sizeBytes = copied,
            )
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    private fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - ChannelStagingMaxAgeMillis
        root.walkBottomUp()
            .filter { it != root && (it.isDirectory || it.lastModified() < cutoff) }
            .forEach { file ->
                if (file.isDirectory) {
                    if (file.listFiles().isNullOrEmpty()) file.delete()
                } else {
                    file.delete()
                }
            }
    }

    private fun safeFileName(name: String, kind: ChannelFileKind): String {
        val fallback = when (kind) {
            ChannelFileKind.Image -> "image.jpg"
            ChannelFileKind.Audio -> "audio.opus"
            ChannelFileKind.Video -> "video.mp4"
            ChannelFileKind.File -> "file.bin"
        }
        return name.trim()
            .replace(Regex("[\\u0000-\\u001f/\\\\:]"), "_")
            .trim('.', ' ')
            .take(160)
            .ifBlank { fallback }
    }
}
