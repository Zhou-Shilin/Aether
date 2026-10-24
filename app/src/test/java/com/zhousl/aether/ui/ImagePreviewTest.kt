package com.zhousl.aether.ui

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImagePreviewTest {
    @Test
    fun revokedPhotoPickerUriFallsBackToDurableWorkspaceCopy() = runTest {
        val original = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            original.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        original.recycle()
        var readWorkspace = false
        val preview = decodeAttachmentBitmap(
            attachment = ChatAttachment(
                id = "photo", uri = "content://expired/photo", name = "photo.png",
                mimeType = "image/png", sizeBytes = bytes.size.toLong(), kind = AttachmentKind.Image,
                workspacePath = "/root/workspace/photo.png",
            ),
            maxSize = 600,
            openInputStream = { throw FileNotFoundException("Permission expired") },
            readWorkspaceBytes = { readWorkspace = true; bytes },
        )
        assertTrue(readWorkspace)
        assertNotNull(preview)
        assertTrue(preview!!.width == 80 && preview.height == 40)
    }

    @Test
    fun compressedLargeImageIsDownsampledBeforeDisplay() {
        val original = Bitmap.createBitmap(4096, 256, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            original.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        original.recycle()
        val preview = decodeMarkdownBitmap(bytes)
        assertNotNull(preview)
        assertTrue(preview!!.width <= 2048)
        assertTrue(preview.height <= 2048)
    }
}
