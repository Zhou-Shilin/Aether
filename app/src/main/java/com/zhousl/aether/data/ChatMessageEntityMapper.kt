package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.ChatMessageEntity
import com.zhousl.aether.data.chatdb.ChatMessageSummaryEntity
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.MessageDisplayKind
import org.json.JSONObject

internal const val CurrentMessageSchemaVersion = 2

internal object ChatMessageEntityMapper {
    fun toEntity(
        sessionId: String,
        position: Int,
        message: ChatMessage,
    ): ChatMessageEntity {
        val messageJson = message.toJson().toString()
        return ChatMessageEntity(
            sessionId = sessionId,
            id = message.id,
            position = position,
            messageJson = messageJson,
            author = message.author.name,
            text = message.text,
            createdAtMillis = message.createdAtMillis.takeIf { it > 0L },
            responseGroupId = message.responseGroupId,
            displayKind = message.displayKind.name,
            messageSchemaVersion = CurrentMessageSchemaVersion,
            hasUsageStatistics = message.usageStatistics != null,
        )
    }

    fun toChatMessage(
        entity: ChatMessageEntity,
        messageIndex: Int,
    ): ChatMessage = runCatching {
        // Keep messageJson as the compatibility source of truth during the typed-column transition.
        parseMessage(JSONObject(entity.messageJson), messageIndex)
    }.getOrElse { throwable ->
        ChatMessage(
            id = entity.id,
            author = MessageAuthor.entries.firstOrNull { it.name == entity.author } ?: MessageAuthor.Agent,
            text = entity.text.ifBlank {
                "Aether could not render a stored message (${throwable.javaClass.simpleName}). " +
                    "The raw stored JSON is attached to the message payload for recovery."
            },
            createdAtMillis = entity.createdAtMillis ?: timestampFromMessageId(entity.id),
            responseGroupId = entity.responseGroupId,
            providerPayloadJson = entity.messageJson,
            displayKind = entity.displayKind.toMessageDisplayKind(),
        )
    }

    fun summaryToChatMessage(entity: ChatMessageSummaryEntity): ChatMessage = ChatMessage(
        id = entity.id,
        author = MessageAuthor.entries.firstOrNull { it.name == entity.author } ?: MessageAuthor.Agent,
        text = entity.text,
        createdAtMillis = entity.createdAtMillis ?: timestampFromMessageId(entity.id),
        responseGroupId = entity.responseGroupId,
        displayKind = entity.displayKind.toMessageDisplayKind(),
    )

    private fun String?.toMessageDisplayKind(): MessageDisplayKind =
        MessageDisplayKind.entries.firstOrNull { it.name == this } ?: MessageDisplayKind.Standard
}
