package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SharedAetherExtensionsTest {
    @Test
    fun parsesAndOrdersScriptExtensionRegistrations() {
        val payload = Json.parseToJsonElement(
            """
            {
              "api_version": 2,
              "version": 8,
              "extensions": [{"id":"demo","name":"Demo","path":"/demo"}],
              "surfaces": [
                {"id":"second","extension_id":"demo","extension_name":"Demo","slot":"chat.top","order":20,"tree":{"type":"text","text":"B"}},
                {"id":"first","extension_id":"demo","extension_name":"Demo","slot":"chat.top","order":10,"tree":{"type":"text","text":"A"}}
              ],
              "components": [{"id":"wrap","extension_id":"demo","extension_name":"Demo","target":"chat.screen","mode":"wrap","order":0,"tree":{"type":"next"}}],
              "pages": [{"id":"demo:home","local_id":"home","extension_id":"demo","extension_name":"Demo","title":"Home","subtitle":"","icon":"home","order":0,"tree":[]}],
              "event_names": ["chat.opened"],
              "errors": []
            }
            """.trimIndent()
        ).jsonObject
        val snapshot = parseSharedAetherExtensionSnapshot(payload)
        assertEquals(listOf("first", "second"), snapshot.surfacesAt("chat.top").map { it.id })
        assertEquals("wrap", snapshot.componentsAt("chat.screen").single().mode)
        assertEquals("demo:home", snapshot.pages.single().id)
        assertEquals(setOf("chat.opened"), snapshot.eventNames)
    }
}
