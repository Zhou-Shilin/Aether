package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.ChatMessageSummaryEntity
import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.MessageAuthor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositorySerializationTest {
    @Test
    fun serializationKeepsInlineImageBytesAndWorkspacePath() {
        val serialized = serializeChatSessions(
            listOf(
                ChatSession(
                    id = "session-1",
                    title = "Image",
                    preview = "Image",
                    messages = listOf(
                        ChatMessage(
                            id = "user-1",
                            author = MessageAuthor.User,
                            text = "see attached",
                            attachments = listOf(
                                ChatAttachment(
                                    id = "attachment-1",
                                    uri = "content://image",
                                    name = "image.png",
                                    mimeType = "image/png",
                                    sizeBytes = 1_024,
                                    kind = AttachmentKind.Image,
                                    workspacePath = "/workspace/image.png",
                                    inlineBase64 = "a".repeat(120_000),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        val attachment = JSONArray(serialized)
            .getJSONObject(0)
            .getJSONArray("messages")
            .getJSONObject(0)
            .getJSONArray("attachments")
            .getJSONObject(0)

        assertEquals("a".repeat(120_000), attachment.getString("inlineBase64"))
        assertEquals("/workspace/image.png", attachment.getString("workspacePath"))
    }

    @Test
    fun serializationKeepsLargeToolOutputJson() {
        val serialized = serializeChatSessions(
            listOf(
                ChatSession(
                    id = "session-1",
                    title = "Tool",
                    preview = "Tool",
                    messages = listOf(
                        ChatMessage(
                            id = "agent-1",
                            author = MessageAuthor.Agent,
                            text = "",
                            toolInvocations = listOf(
                                ChatToolInvocation(
                                    id = "tool-1",
                                    toolName = "bash",
                                    argumentsJson = JSONObject()
                                        .put("command", "yes")
                                        .toString(),
                                    outputJson = JSONObject()
                                        .put("ok", true)
                                        .put("stdout", "x".repeat(140_000))
                                        .toString(),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        val outputJson = JSONArray(serialized)
            .getJSONObject(0)
            .getJSONArray("messages")
            .getJSONObject(0)
            .getJSONArray("toolInvocations")
            .getJSONObject(0)
            .getString("outputJson")
        val output = JSONObject(outputJson)

        assertTrue(output.getBoolean("ok"))
        assertEquals("x".repeat(140_000), output.getString("stdout"))
    }

    @Test
    fun summaryMappingDoesNotReadLegacyMessageJsonPayload() {
        val message = ChatMessageEntityMapper.summaryToChatMessage(
            ChatMessageSummaryEntity(
                sessionId = "session-1",
                id = "agent-1",
                position = 0,
                author = MessageAuthor.Agent.name,
                text = "Recovered from typed columns",
                createdAtMillis = 123L,
            )
        )

        assertEquals("agent-1", message.id)
        assertEquals(MessageAuthor.Agent, message.author)
        assertEquals("Recovered from typed columns", message.text)
        assertEquals(123L, message.createdAtMillis)
        assertTrue(message.toolInvocations.isEmpty())
        assertTrue(message.providerPayloadJson.isNullOrBlank())
    }

    @Test
    fun migrationParseResultMarksCorruptedJsonAsRecoverable() {
        val result = parseChatSessionsForMigration("{not-valid-json")

        assertTrue(result.recoveredFromCorruption)
        assertEquals(1, result.sessions.size)
        assertTrue(result.sessions.first().id.startsWith("corrupt-chat-state-"))
    }

    @Test
    fun migrationParseResultKeepsValidJsonSuccessful() {
        val result = parseChatSessionsForMigration(
            """
                [{"id":"session-1","title":"First","preview":"First","messages":[]}]
            """.trimIndent()
        )

        assertFalse(result.recoveredFromCorruption)
        assertEquals("session-1", result.sessions.single().id)
    }

    @Test
    fun migrationKeepsLegacyCurrentSessionWhenItExists() {
        val sessions = listOf(
            ChatSession(id = "session-1", title = "First", preview = "First", messages = emptyList()),
            ChatSession(id = "session-2", title = "Second", preview = "Second", messages = emptyList()),
        )

        val currentSessionId = resolveLegacyCurrentSessionIdForMigration(
            legacyCurrentSessionId = "session-2",
            legacySessions = sessions,
        )

        assertEquals("session-2", currentSessionId)
    }

    @Test
    fun migrationFallsBackToFirstSessionWhenLegacyCurrentSessionIsAbsent() {
        val sessions = listOf(
            ChatSession(id = "session-1", title = "First", preview = "First", messages = emptyList()),
            ChatSession(id = "session-2", title = "Second", preview = "Second", messages = emptyList()),
        )

        val currentSessionId = resolveLegacyCurrentSessionIdForMigration(
            legacyCurrentSessionId = null,
            legacySessions = sessions,
        )

        assertEquals("session-1", currentSessionId)
    }

    @Test
    fun migrationFallsBackToFirstSessionWhenLegacyCurrentSessionIsMissing() {
        val sessions = listOf(
            ChatSession(id = "session-1", title = "First", preview = "First", messages = emptyList()),
            ChatSession(id = "session-2", title = "Second", preview = "Second", messages = emptyList()),
        )

        val currentSessionId = resolveLegacyCurrentSessionIdForMigration(
            legacyCurrentSessionId = "missing-session",
            legacySessions = sessions,
        )

        assertEquals("session-1", currentSessionId)
    }
}