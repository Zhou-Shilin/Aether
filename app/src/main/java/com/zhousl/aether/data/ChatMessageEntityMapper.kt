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
    ): ChatMessageEntity = toEntity(
        sessionId = sessionId,
        position = position,
        message = message,
        messageJson = message.toJson().toString(),
    )

    fun toEntity(
        sessionId: String,
        position: Int,
        message: ChatMessage,
        messageJson: String,
    ): ChatMessageEntity = ChatMessageEntity(
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
        isIncomplete = message.isIncomplete,
    )

    fun toChatMessage(
        entity: ChatMessageEntity,
        messageIndex: Int,
    ): ChatMessage = runCatching {
        // The typed column is authoritative while messageJson remains a compatibility payload.
        parseMessage(JSONObject(entity.messageJson), messageIndex).withStoredIncompleteFlag(entity.isIncomplete)
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
            isIncomplete = entity.isIncomplete,
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
        isIncomplete = entity.isIncomplete,
        displayKind = entity.displayKind.toMessageDisplayKind(),
    )

    private fun ChatMessage.withStoredIncompleteFlag(isIncomplete: Boolean): ChatMessage =
        if (isIncomplete != this.isIncomplete) copy(isIncomplete = isIncomplete) else this

    private fun String?.toMessageDisplayKind(): MessageDisplayKind =
        MessageDisplayKind.entries.firstOrNull { it.name == this } ?: MessageDisplayKind.Standard
}
