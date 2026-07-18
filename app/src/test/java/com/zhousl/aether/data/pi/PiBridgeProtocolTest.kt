package com.zhousl.aether.data.pi

import java.io.StringWriter
import java.io.Writer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiBridgeProtocolTest {
    @Test
    fun parserHandlesFragmentedJsonlAndCrlf() {
        val frames = mutableListOf<PiBridgeFrame>()
        val parser = PiJsonlParser(onFrame = frames::add)

        parser.accept("""{"type":"response","id":"a","ok":true,"payload":{"x":""")
        parser.accept("1}}\r\n")
        parser.accept("""{"type":"event","id":"a","event":"assistant_text_delta","payload":{"delta":"hi"}}""" + "\n")

        assertEquals(2, frames.size)
        assertEquals("response", frames[0].type)
        assertEquals("a", frames[0].id)
        assertTrue(frames[0].ok)
        assertEquals(1, frames[0].payload.optInt("x"))
        assertEquals("event", frames[1].type)
        assertEquals("assistant_text_delta", frames[1].event)
        assertEquals("hi", frames[1].payload.optString("delta"))
    }

    @Test
    fun parserReportsInvalidJsonAndContinues() {
        val frames = mutableListOf<PiBridgeFrame>()
        val invalidLines = mutableListOf<String>()
        val parser = PiJsonlParser(
            onFrame = frames::add,
            onInvalidLine = { line, _ -> invalidLines += line },
        )

        parser.accept("not-json\n")
        parser.accept("""{"type":"response","id":"b","ok":true,"payload":{}}""" + "\n")

        assertEquals(listOf("not-json"), invalidLines)
        assertEquals(1, frames.size)
        assertEquals("b", frames[0].id)
    }

    @Test
    fun requestSerializesSnakeCasePayload() {
        val line = PiBridgeRequest(
            id = "req",
            type = "complete_once",
            payload = fauxPiModelConfig(response = "done").toJson(),
        ).toJsonLine()

        assertTrue(line.contains("\"type\":\"complete_once\""))
        assertTrue(line.contains("\"pi_provider_id\":\"faux\""))
        assertTrue(line.contains("\"faux_response\":\"done\""))
    }

    @Test
    fun requestStreamsOneLfDelimitedJsonRecord() {
        val output = StringWriter()
        val escapedText = "\"\\\b\u000c\n\r\t\u0001\u2028\u2029"

        PiBridgeRequest(
            id = "req",
            type = "run_turn",
            payload = JSONObject()
                .put("text", "first\nsecond")
                .put("path", "workspace/file.png")
                .put("escaped", escapedText)
                .put(
                    "nested",
                    JSONObject()
                        .put("number", 3.5)
                        .put("boolean", true)
                        .put("null", JSONObject.NULL)
                        .put("array", JSONArray().put(1).put("two").put(JSONObject.NULL)),
                ),
        ).writeJsonLine(output)

        val line = output.toString()
        assertTrue(line.endsWith("\n"))
        assertEquals(1, line.count { it == '\n' })
        val request = JSONObject(line.dropLast(1))
        assertEquals("req", request.optString("id"))
        assertEquals("first\nsecond", request.getJSONObject("payload").optString("text"))
        assertEquals("workspace/file.png", request.getJSONObject("payload").optString("path"))
        assertEquals(escapedText, request.getJSONObject("payload").optString("escaped"))
        val nested = request.getJSONObject("payload").getJSONObject("nested")
        assertEquals(3.5, nested.optDouble("number"), 0.0)
        assertTrue(nested.optBoolean("boolean"))
        assertTrue(nested.isNull("null"))
        assertEquals(1, nested.getJSONArray("array").optInt(0))
        assertEquals("two", nested.getJSONArray("array").optString(1))
        assertTrue(nested.getJSONArray("array").isNull(2))
    }

    @Test
    fun requestStreamsLargePayloadWithoutRetainingOutput() {
        val payloadCharacters = 4 * 1024 * 1024
        val output = CountingWriter()

        PiBridgeRequest(
            id = "req",
            type = "run_turn",
            payload = JSONObject().put("data", "a".repeat(payloadCharacters)),
        ).writeJsonLine(output)

        assertTrue(output.charactersWritten > payloadCharacters)
        assertTrue(output.largestWrite <= payloadCharacters)
    }

    private class CountingWriter : Writer() {
        var charactersWritten = 0L
            private set
        var largestWrite = 0
            private set

        override fun write(buffer: CharArray, offset: Int, length: Int) {
            charactersWritten += length
            largestWrite = maxOf(largestWrite, length)
        }

        override fun write(value: String, offset: Int, length: Int) {
            charactersWritten += length
            largestWrite = maxOf(largestWrite, length)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
