package com.zhousl.aether.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BridgeFrameCodecTest {
    @Test
    fun decodesFramesSplitAcrossChunks() {
        val codec = BridgeFrameCodec()
        assertEquals(emptyList(), codec.append("{\"id\":\"one".encodeToByteArray()))
        val frames = codec.append("\"}\n{\"id\":\"two\"}\n".encodeToByteArray())
        assertEquals(listOf("one", "two"), frames.map { it["id"].toString().trim('"') })
    }

    @Test
    fun encodesOneJsonLine() {
        val encoded = BridgeFrameCodec().encode(buildJsonObject { put("ok", true) }).decodeToString()
        assertEquals("{\"ok\":true}\n", encoded)
    }
}
