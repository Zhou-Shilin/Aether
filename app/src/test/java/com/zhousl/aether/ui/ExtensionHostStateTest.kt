package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionHostStateTest {
    @Test
    fun summariesNeverReadMessagesOrSerializeInlineImages() {
        val unreadableHistory = object : AbstractList<ChatMessage>() {
            override val size = 309
            override fun get(index: Int): ChatMessage = error("History must not be read")
        }
        val session = ChatSession(
            id = "long-session", title = "Images", preview = "x".repeat(40_000),
            messages = unreadableHistory, messageCount = 309, lastMessageAtMillis = 123L,
        )
        val result = extensionSessionSummaries(listOf(session))
        val summary = result.getJSONObject(0)
        assertEquals(309, summary.getInt("message_count"))
        assertEquals(123L, summary.getLong("last_message_at_millis"))
        assertFalse(summary.has("messages"))
        assertTrue(result.toString().length < 5_000)
    }

    @Test
    fun imageHeavyHistoryDoesNotGrowHostSnapshotOrMutateStoredMessages() {
        val messages = List(309) { index ->
            ChatMessage(
                id = "message-$index", author = MessageAuthor.User, text = "Image $index",
                attachments = if (index < 54) listOf(ChatAttachment(
                    id = "image-$index", uri = "content://image/$index", name = "image.png",
                    mimeType = "image/png", sizeBytes = null, kind = AttachmentKind.Image,
                    inlineBase64 = "a".repeat(740_000),
                )) else emptyList(),
                providerPayloadJson = "raw provider data",
            )
        }
        val session = ChatSession(id = "images", title = "Images", preview = "Images", messages = messages)
        val compact = extensionSessionSummaries(listOf(session)).toString()
        val withoutHistory = extensionSessionSummaries(listOf(session.copy(messages = emptyList()))).toString()
        assertEquals(withoutHistory, compact)
        assertTrue(compact.length < 1_000)
        assertEquals(309, messages.size)
        assertEquals(740_000, messages.first().attachments.single().inlineBase64.length)
        assertFalse(compact.contains("raw provider data"))
    }

    @Test
    fun rootKeyMatchesNestedAndSlashPaths() {
        assertEquals("sessions", extensionStateRootKey(" /sessions/0/title/ "))
        assertEquals("settings", extensionStateRootKey("..settings.theme"))
        assertNull(extensionStateRootKey(" / "))
    }

    @Test
    fun onlyTaggedCustomMessagesReachRenderContext() {
        val ordinary = ChatMessage(id = "ordinary", author = MessageAuthor.Agent,
            text = "Normal answer", providerPayloadJson = "private provider payload")
        val custom = ChatMessage(id = "custom", author = MessageAuthor.Agent,
            text = "Force lab", assistantActionsHidden = true,
            providerPayloadJson = """{"aether_custom_type":"aether-visual","aether_custom_payload":{"id":"artifact"}}""")
        val result = extensionCustomMessages(listOf(ordinary, custom))
        assertEquals(1, result.length())
        assertEquals("custom", result.getJSONObject(0).getString("id"))
        assertEquals("artifact", result.getJSONObject(0).getJSONObject("payload").getString("id"))
        assertFalse(result.toString().contains("private provider payload"))
    }

    @Test
    fun piCustomDisplayMessageRetainsIdentityAndIgnoresHiddenEntries() {
        val shown = com.zhousl.aether.data.pi.parsePiDisplayMessage(
            """{"id":"pi-entry","type":"custom_message","customType":"aether-visual","display":true,"content":"Triangle","details":{"id":"artifact"}}""")!!
        assertEquals("pi-entry", shown.id)
        assertEquals("Triangle", shown.text)
        assertTrue(shown.payloadJson.contains("artifact"))
        assertNull(com.zhousl.aether.data.pi.parsePiDisplayMessage(
            """{"id":"hidden","type":"custom_message","customType":"internal","display":false}"""))
    }
}
