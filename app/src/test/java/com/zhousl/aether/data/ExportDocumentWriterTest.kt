package com.zhousl.aether.data

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDocumentWriterTest {
    @Test
    fun providerWithoutReadWriteModeCanExportLongUnicodeConversation() {
        val output = ByteArrayOutputStream()
        val text = "你好 🌏\n".repeat(40_000)
        val modes = mutableListOf<String>()
        assertTrue(writeExportDocument(text) { mode ->
            modes += mode
            if (mode == "rwt") throw FileNotFoundException("Mode not supported")
            output
        })
        assertEquals(listOf("rwt", "w"), modes)
        assertEquals(text, output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun nullReadWriteStreamFallsBackToWriteMode() {
        val output = ByteArrayOutputStream()
        assertTrue(writeExportDocument("conversation") { mode -> if (mode == "w") output else null })
        assertEquals("conversation", output.toString())
    }

    @Test
    fun inaccessibleDestinationReturnsFailure() {
        assertFalse(writeExportDocument("conversation") { throw FileNotFoundException("Permission revoked") })
    }
}
