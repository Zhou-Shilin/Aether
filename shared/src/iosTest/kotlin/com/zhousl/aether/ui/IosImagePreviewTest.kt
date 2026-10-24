package com.zhousl.aether.ui

import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosImagePreviewTest {
    @Test
    fun largeCameraImageIsDownsampledForAttachmentCards() = runTest {
        val surface = Surface.makeRasterN32Premul(4096, 2048)
        val bytes = try {
            val image = surface.makeImageSnapshot()
            try {
                val data = image.encodeToData()!!
                try { data.bytes } finally { data.close() }
            } finally {
                image.close()
            }
        } finally {
            surface.close()
        }
        val preview = decodeIosImagePreview(bytes, 600)
        assertEquals(600, preview.width)
        assertEquals(300, preview.height)
    }

    @Test
    fun invalidImageReturnsAnErrorWithoutAllocatingAPreview() = runTest {
        assertFailsWith<IllegalStateException> { decodeIosImagePreview("invalid".encodeToByteArray()) }
    }
}
