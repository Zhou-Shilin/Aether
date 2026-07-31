package com.zhousl.aether.platform

data class PlatformPickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PlatformPickedDirectoryFile(
    val relativePath: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PlatformPickedDirectory(
    val name: String,
    val files: List<PlatformPickedDirectoryFile>,
)

interface PlatformServices {
    suspend fun pickFile(imagesOnly: Boolean = false): PlatformPickedFile?
    suspend fun pickFiles(imagesOnly: Boolean = false): List<PlatformPickedFile> =
        listOfNotNull(pickFile(imagesOnly))
    suspend fun pickDirectory(): PlatformPickedDirectory? = null
    /** Returns null when the user cancels the platform file picker. */
    suspend fun exportFile(name: String, mimeType: String, bytes: ByteArray): Boolean? = false
    fun copyText(text: String): Boolean
    fun shareText(title: String, text: String): Boolean
    fun shareFile(name: String, mimeType: String, bytes: ByteArray): Boolean = false
    fun previewFile(name: String, mimeType: String, bytes: ByteArray): Boolean = false
    fun openUrl(url: String): Boolean
    fun terminateApplication(): Boolean = false
}

object NoOpPlatformServices : PlatformServices {
    override suspend fun pickFile(imagesOnly: Boolean): PlatformPickedFile? = null
    override fun copyText(text: String): Boolean = false
    override fun shareText(title: String, text: String): Boolean = false
    override fun openUrl(url: String): Boolean = false
}
