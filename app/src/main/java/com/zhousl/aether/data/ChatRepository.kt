package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.AttachmentWorkspaceState
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatBranchGroup
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.ChatUsageStatistics
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.MessageDisplayKind
import com.zhousl.aether.ui.ReasoningSummaryChunk
import com.zhousl.aether.ui.ReasoningTrace
import com.zhousl.aether.ui.syncActiveBranches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val DraftSessionId = "draft"
private const val PersistedReasoningRawTextMaxChars = 12_000
private const val PersistedProviderPayloadJsonMaxChars = 120_000
private const val PersistedToolArgumentsJsonMaxChars = 24_000
private const val PersistedToolOutputJsonMaxChars = 72_000
private const val PersistedReasoningSummaryRawTextMaxChars = 4_000
private const val PersistedJsonStringValueMaxChars = 16_000

private val Context.chatDataStore by preferencesDataStore(name = "aether_chats")

data class PersistedChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = DraftSessionId,
)

class ChatRepository(
    private val context: Context,
) {
    val chatState: Flow<PersistedChatState> = context.chatDataStore.data.map { preferences ->
        val sessions = parseChatSessions(preferences[SESSIONS_JSON].orEmpty())
        val storedSessionId = preferences[CURRENT_SESSION_ID] ?: DraftSessionId
        PersistedChatState(
            sessions = sessions,
            currentSessionId = storedSessionId
                .takeIf { id -> id == DraftSessionId || sessions.any { it.id == id } }
                ?: sessions.firstOrNull()?.id
                ?: DraftSessionId,
        )
    }

    suspend fun updateChatState(
        sessions: List<ChatSession>,
        currentSessionId: String,
    ) {
        context.chatDataStore.edit {
            it[SESSIONS_JSON] = serializeChatSessions(sessions)
            it[CURRENT_SESSION_ID] = currentSessionId
        }
    }

    private companion object {
        val SESSIONS_JSON = stringPreferencesKey("sessions_json")
        val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
    }
}

internal fun parseChatSessions(rawValue: String): List<ChatSession> {
    if (rawValue.isBlank()) return emptyList()

    return runCatching {
        val sessions = JSONArray(rawValue)
        buildList {
            for (sessionIndex in 0 until sessions.length()) {
                val session = sessions.optJSONObject(sessionIndex) ?: continue
                add(
                    ChatSession(
                        id = session.optString("id").ifBlank { "session-$sessionIndex" },
                        title = session.optString("title"),
                        preview = session.optString("preview"),
                        hasCustomTitle = session.optBoolean("hasCustomTitle", false),
                        messages = parseMessages(session.optJSONArray("messages")),
                        selectedSkillIds = parseStringList(session.optJSONArray("selectedSkillIds")).ifEmpty {
                            parseActiveSkillContexts(session.optString("activeSkillsJson")).map { it.skillId }
                        },
                        activeSkills = parseActiveSkillContexts(session.optString("activeSkillsJson")),
                        activeMcpServerIds = parseStringList(session.optJSONArray("activeMcpServerIds")),
                        agentModeEnabled = session.optBoolean("agentModeEnabled", false),
                        selectedModelKey = session.optString("selectedModelKey"),
                    )
                )
            }
        }
    }.getOrElse { throwable ->
        listOf(corruptedChatStateSession(rawValue, throwable))
    }
}

private fun corruptedChatStateSession(
    rawValue: String,
    throwable: Throwable,
): ChatSession = ChatSession(
    id = "corrupt-chat-state-${rawValue.hashCode()}",
    title = "Chat storage needs recovery",
    preview = "Stored chat data could not be parsed.",
    hasCustomTitle = true,
    messages = listOf(
        ChatMessage(
            id = "agent-corrupt-chat-state-${rawValue.hashCode()}",
            author = MessageAuthor.Agent,
            text = "Aether could not read the stored chat history (${throwable.javaClass.simpleName}). " +
                "The app is showing this recovery placeholder instead of hiding the conversation list.",
            createdAtMillis = 0L,
        )
    ),
)

internal fun serializeChatSessions(sessions: List<ChatSession>): String =
    JSONArray().apply {
        sessions.forEach { session ->
            put(session.toJson())
        }
    }.toString()

internal fun ChatSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("preview", preview)
    put("hasCustomTitle", hasCustomTitle)
    put("agentModeEnabled", agentModeEnabled)
    put("selectedModelKey", selectedModelKey)
    put("selectedSkillIds", JSONArray().apply { selectedSkillIds.forEach(::put) })
    put("messages", JSONArray().apply { syncActiveBranches(messages).forEach { put(it.toJson()) } })
    put("activeSkillsJson", serializeActiveSkillContexts(activeSkills))
    put("activeMcpServerIds", JSONArray().apply { activeMcpServerIds.forEach(::put) })
}

private fun parseMessages(messages: JSONArray?): List<ChatMessage> {
    if (messages == null) return emptyList()

    return buildList {
        for (messageIndex in 0 until messages.length()) {
            val message = messages.optJSONObject(messageIndex) ?: continue
            add(
                ChatMessage(
                    id = message.optString("id").ifBlank { "message-$messageIndex" },
                    author = if (message.optString("author") == MessageAuthor.User.name) {
                        MessageAuthor.User
                    } else {
                        MessageAuthor.Agent
                    },
                    text = message.optString("text"),
                    createdAtMillis = message.optLong("createdAtMillis").takeIf { it > 0L }
                        ?: timestampFromMessageId(message.optString("id")),
                    attachments = parseAttachments(message.optJSONArray("attachments")),
                    toolInvocations = parseToolInvocations(message.optJSONArray("toolInvocations")),
                    thoughtDurationMillis = if (message.has("thoughtDurationMillis")) {
                        message.optLong("thoughtDurationMillis")
                    } else {
                        null
                    },
                    reasoningTrace = parseReasoningTrace(message.optJSONObject("reasoningTrace")),
                    branchGroup = parseBranchGroup(message.optJSONObject("branchGroup")),
                    responseGroupId = message.optString("responseGroupId").ifBlank { null },
                    assistantActionsHidden = message.optBoolean("assistantActionsHidden"),
                    providerPayloadJson = message.optString("providerPayloadJson"),
                    displayKind = MessageDisplayKind.entries.firstOrNull {
                        it.name == message.optString("displayKind")
                    } ?: MessageDisplayKind.Standard,
                    usageStatistics = parseUsageStatistics(message.optJSONObject("usageStatistics")),
                )
            )
        }
    }
}

private fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("author", author.name)
    put("text", text)
    if (createdAtMillis > 0L) {
        put("createdAtMillis", createdAtMillis)
    }
    thoughtDurationMillis?.let { put("thoughtDurationMillis", it) }
    reasoningTrace?.let { put("reasoningTrace", it.toJson()) }
    branchGroup?.let { put("branchGroup", it.toJson()) }
    responseGroupId?.let { put("responseGroupId", it) }
    if (assistantActionsHidden) {
        put("assistantActionsHidden", true)
    }
    truncatePersistedText(providerPayloadJson, PersistedProviderPayloadJsonMaxChars)
        .takeIf { it.isNotBlank() }
        ?.let { put("providerPayloadJson", it) }
    if (displayKind != MessageDisplayKind.Standard) {
        put("displayKind", displayKind.name)
    }
    usageStatistics?.let { put("usageStatistics", it.toJson()) }
    put("toolInvocations", JSONArray().apply { toolInvocations.forEach { put(it.toJson()) } })
    put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
}

private fun parseUsageStatistics(json: JSONObject?): ChatUsageStatistics? {
    if (json == null) return null
    return ChatUsageStatistics(
        inputTokens = json.optionalLong("inputTokens"),
        outputTokens = json.optionalLong("outputTokens"),
        totalTokens = json.optionalLong("totalTokens"),
        reasoningTokens = json.optionalLong("reasoningTokens"),
        cachedInputTokens = json.optionalLong("cachedInputTokens"),
        requestCount = json.optInt("requestCount", 1).coerceAtLeast(1),
        tokenUsageSource = json.optString("tokenUsageSource").ifBlank { "unavailable" },
        startedAtMillis = json.optLong("startedAtMillis"),
        firstTokenAtMillis = json.optionalLong("firstTokenAtMillis"),
        completedAtMillis = json.optLong("completedAtMillis"),
    )
}

private fun ChatUsageStatistics.toJson(): JSONObject = JSONObject().apply {
    inputTokens?.let { put("inputTokens", it) }
    outputTokens?.let { put("outputTokens", it) }
    totalTokens?.let { put("totalTokens", it) }
    reasoningTokens?.let { put("reasoningTokens", it) }
    cachedInputTokens?.let { put("cachedInputTokens", it) }
    put("requestCount", requestCount)
    put("tokenUsageSource", tokenUsageSource)
    if (startedAtMillis > 0L) put("startedAtMillis", startedAtMillis)
    firstTokenAtMillis?.let { put("firstTokenAtMillis", it) }
    if (completedAtMillis > 0L) put("completedAtMillis", completedAtMillis)
}

private fun JSONObject.optionalLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private fun parseBranchGroup(json: JSONObject?): ChatBranchGroup? {
    if (json == null) return null
    val branchesJson = json.optJSONArray("branches") ?: return null
    val branches = buildList {
        for (index in 0 until branchesJson.length()) {
            add(parseMessages(branchesJson.optJSONArray(index)))
        }
    }.filter { it.isNotEmpty() }
    if (branches.size <= 1) return null
    return ChatBranchGroup(
        branches = branches,
        selectedIndex = json.optInt("selectedIndex", 0).coerceIn(0, branches.lastIndex),
    )
}

private fun ChatBranchGroup.toJson(): JSONObject = JSONObject().apply {
    val safeSelectedIndex = selectedIndex.coerceIn(0, branches.lastIndex.coerceAtLeast(0))
    put("selectedIndex", safeSelectedIndex)
    put(
        "branches",
        JSONArray().apply {
            branches.forEach { branch ->
                put(JSONArray().apply { branch.forEach { put(it.toJson()) } })
            }
        },
    )
}

private fun parseAttachments(attachments: JSONArray?): List<ChatAttachment> {
    if (attachments == null) return emptyList()

    return buildList {
        for (attachmentIndex in 0 until attachments.length()) {
            val attachment = attachments.optJSONObject(attachmentIndex) ?: continue
            val mimeType = attachment.optString("mimeType")
            val workspacePath = attachment.optString("workspacePath")
            add(
                ChatAttachment(
                    id = attachment.optString("id").ifBlank { "attachment-$attachmentIndex" },
                    uri = attachment.optString("uri"),
                    name = attachment.optString("name").ifBlank { "Attachment ${attachmentIndex + 1}" },
                    mimeType = mimeType,
                    sizeBytes = if (attachment.has("sizeBytes")) attachment.optLong("sizeBytes") else null,
                    kind = AttachmentKind.fromStored(
                        value = attachment.optString("kind"),
                        mimeType = mimeType,
                    ),
                    workspacePath = workspacePath,
                    workspaceState = if (workspacePath.isBlank()) {
                        AttachmentWorkspaceState.Failed
                    } else {
                        AttachmentWorkspaceState.Ready
                    },
                    workspaceError = if (workspacePath.isBlank()) {
                        "This attachment is missing its workspace copy."
                    } else {
                        ""
                    },
                    inlineBase64 = attachment.optString("inlineBase64"),
                )
            )
        }
    }
}

private fun ChatAttachment.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("uri", uri)
    put("name", name)
    put("mimeType", mimeType)
    put("kind", kind.name)
    put("workspacePath", workspacePath)
    sizeBytes?.let { put("sizeBytes", it) }
}

private fun parseReasoningTrace(json: JSONObject?): ReasoningTrace? {
    if (json == null) return null
    val id = json.optString("id").ifBlank { "reasoning-${json.optString("startedAtMillis")}" }
    return ReasoningTrace(
        id = id,
        rawText = json.optString("rawText"),
        chunks = parseReasoningSummaryChunks(json.optJSONArray("chunks")),
        toolInvocations = parseToolInvocations(json.optJSONArray("toolInvocations")),
        latestStatusText = json.optString("latestStatusText"),
        startedAtMillis = json.optLong("startedAtMillis"),
        completedAtMillis = if (json.has("completedAtMillis")) {
            json.optLong("completedAtMillis")
        } else {
            null
        },
    )
}

private fun ReasoningTrace.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("rawText", if (hasSummary) "" else truncatePersistedText(rawText, PersistedReasoningRawTextMaxChars))
    put("latestStatusText", latestStatusText)
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("chunks", JSONArray().apply { chunks.forEach { put(it.toJson()) } })
    put("toolInvocations", JSONArray().apply { toolInvocations.forEach { put(it.toJson()) } })
}

private fun parseReasoningSummaryChunks(chunks: JSONArray?): List<ReasoningSummaryChunk> {
    if (chunks == null) return emptyList()
    return buildList {
        for (index in 0 until chunks.length()) {
            val chunk = chunks.optJSONObject(index) ?: continue
            add(
                ReasoningSummaryChunk(
                    id = chunk.optString("id").ifBlank { "reasoning-summary-$index" },
                    title = chunk.optString("title"),
                    detail = chunk.optString("detail"),
                    rawText = chunk.optString("rawText"),
                    isPending = chunk.optBoolean("isPending"),
                    createdAtMillis = chunk.optLong("createdAtMillis"),
                    timelineOrder = chunk.optLong("timelineOrder"),
                )
            )
        }
    }
}

private fun ReasoningSummaryChunk.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("detail", detail)
    put(
        "rawText",
        if (title.isNotBlank() || detail.isNotBlank()) {
            ""
        } else {
            truncatePersistedText(rawText, PersistedReasoningSummaryRawTextMaxChars)
        },
    )
    put("isPending", isPending)
    put("createdAtMillis", createdAtMillis)
    put("timelineOrder", timelineOrder)
}

private fun parseToolInvocations(toolInvocations: JSONArray?): List<ChatToolInvocation> {
    if (toolInvocations == null) return emptyList()

    return buildList {
        for (toolIndex in 0 until toolInvocations.length()) {
            val toolInvocation = toolInvocations.optJSONObject(toolIndex) ?: continue
            add(
                ChatToolInvocation(
                    id = toolInvocation.optString("id").ifBlank { "tool-$toolIndex" },
                    toolName = toolInvocation.optString("toolName"),
                    argumentsJson = toolInvocation.optString("argumentsJson"),
                    outputJson = toolInvocation.optString("outputJson"),
                    isRunning = toolInvocation.optBoolean("isRunning"),
                    startedAtUptimeMillis = toolInvocation.optLong("startedAtUptimeMillis"),
                    completedAtUptimeMillis = if (toolInvocation.has("completedAtUptimeMillis")) {
                        toolInvocation.optLong("completedAtUptimeMillis")
                    } else {
                        null
                    },
                    startedAtMillis = toolInvocation.optLong("startedAtMillis"),
                    completedAtMillis = if (toolInvocation.has("completedAtMillis")) {
                        toolInvocation.optLong("completedAtMillis")
                    } else {
                        null
                    },
                    timelineOrder = toolInvocation.optLong("timelineOrder"),
                )
            )
        }
    }
}

private fun ChatToolInvocation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("toolName", toolName)
    put("argumentsJson", truncatePersistedJson(argumentsJson, PersistedToolArgumentsJsonMaxChars))
    put("outputJson", truncatePersistedJson(outputJson, PersistedToolOutputJsonMaxChars))
    put("isRunning", isRunning)
    put("startedAtUptimeMillis", startedAtUptimeMillis)
    completedAtUptimeMillis?.let { put("completedAtUptimeMillis", it) }
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("timelineOrder", timelineOrder)
}

private fun parseStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

private fun timestampFromMessageId(messageId: String): Long {
    val timestamp = messageId.substringAfterLast('-', missingDelimiterValue = "")
    return timestamp.toLongOrNull()?.takeIf { it > 0L } ?: 0L
}

private fun truncatePersistedText(
    value: String,
    maxChars: Int,
): String {
    if (value.length <= maxChars) return value
    val omittedChars = value.length - maxChars
    return value.take(maxChars) + "\n\n[... $omittedChars characters omitted from persisted chat storage]"
}

private fun truncatePersistedJson(
    value: String,
    maxChars: Int,
): String {
    if (value.length <= maxChars) return value
    val parsed = runCatching { JSONObject(value) }.getOrNull()
    if (parsed != null) {
        val compactedText = compactPersistedJsonObject(parsed).toString()
        if (compactedText.length <= maxChars) return compactedText
    }
    return JSONObject().apply {
        put("ok", false)
        put("truncated", true)
        put("text", truncatePersistedText(value, maxChars))
    }.toString()
}

private fun compactPersistedJsonObject(json: JSONObject): JSONObject {
    val compacted = JSONObject()
    json.keys().forEach { key ->
        val value = json.opt(key)
        compacted.put(
            key,
            if (value is String) {
                truncatePersistedText(value, PersistedJsonStringValueMaxChars)
            } else {
                value
            },
        )
    }
    compacted.put("aetherPersistedOutputTruncated", true)
    return compacted
}
