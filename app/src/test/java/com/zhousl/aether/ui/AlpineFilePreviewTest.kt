package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlpineFilePreviewTest {
    @Test fun binaryDocumentsAndImagesNeverEnterTextEditor() {
        assertEquals(AlpinePreviewKind.Pdf, alpinePreviewKind("report.PDF", "%PDF-1.7".toByteArray()))
        assertEquals(AlpinePreviewKind.Pdf, alpinePreviewKind("report", "%PDF-1.7".toByteArray()))
        assertEquals(AlpinePreviewKind.Image, alpinePreviewKind("photo", byteArrayOf(-1, -40, -1, 0)))
        assertEquals(AlpinePreviewKind.Image, alpinePreviewKind("image.SVG", "<svg/>".toByteArray()))
        assertEquals(AlpinePreviewKind.Image, alpinePreviewKind("photo.HEIC", byteArrayOf(0)))
        assertEquals(AlpinePreviewKind.Media, alpinePreviewKind("recording.m4a", byteArrayOf(0)))
        assertEquals(AlpinePreviewKind.External, alpinePreviewKind("document.docx", "PK".toByteArray()))
        assertEquals(AlpinePreviewKind.External, alpinePreviewKind("unknown", byteArrayOf(0, 1, 2)))
        assertEquals(AlpinePreviewKind.Text, alpinePreviewKind("code.kt", "val text = \"你好\"".toByteArray()))
    }
}
