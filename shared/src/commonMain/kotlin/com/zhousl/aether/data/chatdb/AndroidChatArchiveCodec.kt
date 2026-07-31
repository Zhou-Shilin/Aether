package com.zhousl.aether.data.chatdb

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal fun encodeAndroidChatSessions(sessions: List<PersistedChatSession>): JsonArray =
    buildJsonArray { sessions.forEach { add(it.toAndroidChatSessionJson()) } }

internal fun decodeAndroidChatSessions(value: JsonArray): List<PersistedChatSession> =
    runCatching {
        value.mapIndexed { index, element ->
            checkNotNull(element as? JsonObject) { "Invalid chat session at index $index" }
                .toPersistedChatSession(index)
        }
    }.getOrElse { failure ->
        listOf(corruptedAndroidChatStateSession(value.toString(), failure))
    }

private fun corruptedAndroidChatStateSession(
    rawValue: String,
    failure: Throwable,
): PersistedChatSession {
    val rawHash = rawValue.hashCode()
    return PersistedChatSession(
        id = "corrupt-chat-state-$rawHash",
        title = "Chat storage needs recovery",
        preview = "Stored chat data could not be parsed.",
        hasCustomTitle = true,
        messages = listOf(
            PersistedChatMessage(
                id = "agent-corrupt-chat-state-$rawHash",
                text = "Aether could not read the stored chat history " +
                    "(${failure::class.simpleName.orEmpty().ifBlank { "Throwable" }}). " +
                    "The app is showing this recovery placeholder instead of hiding the conversation list.",
                fromUser = false,
                createdAtMillis = 0L,
                providerPayloadJson = rawValue,
            ),
        ),
    )
}

internal fun PersistedChatSession.toAndroidChatSessionJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("title", title)
    put("preview", preview)
    put("hasCustomTitle", hasCustomTitle)
    put("agentModeEnabled", false)
    put("chromeEnabled", chromeEnabled)
    put("selectedModelKey", selectedModelKey)
    put("selectedSkillIds", buildJsonArray { selectedSkillIds.forEach { add(JsonPrimitive(it)) } })
    put("messages", buildJsonArray {
        messages.forEach { message ->
            message.toAndroidChatMessageJson().forEach(::add)
        }
    })
    put("activeSkillsJson", encodeSharedActiveSkillContexts(activeSkills))
    put("activeMcpServerIds", buildJsonArray { activeMcpServerIds.forEach { add(JsonPrimitive(it)) } })
}

private fun PersistedChatMessage.toAndroidChatMessageJson(): List<JsonObject> {
    if (fromUser) return listOf(toAndroidChatMessageJson(block = null, blockIndex = 0, blockCount = 1))

    val blocks = responseBlocks.ifEmpty {
        buildList {
            reasoningText.takeIf(String::isNotBlank)?.let { text ->
                add(
                    PersistedAssistantResponseBlock(
                        id = "$id-reasoning",
                        type = PersistedAssistantResponseBlockType.Reasoning,
                        text = text,
                        reasoningTrace = PersistedReasoningTrace(
                            id = "$id-reasoning",
                            rawText = text,
                            startedAtMillis = createdAtMillis,
                            completedAtMillis = completedAtMillis,
                        ),
                    ),
                )
            }
            if (tools.isNotEmpty()) {
                add(
                    PersistedAssistantResponseBlock(
                        id = "$id-tools",
                        type = PersistedAssistantResponseBlockType.ToolGroup,
                        tools = tools,
                    ),
                )
            }
            text.takeIf(String::isNotBlank)?.let { value ->
                add(
                    PersistedAssistantResponseBlock(
                        id = "$id-text",
                        type = PersistedAssistantResponseBlockType.Text,
                        text = value,
                    ),
                )
            }
        }
    }
    if (blocks.isEmpty()) return listOf(toAndroidChatMessageJson(null, 0, 1))
    return blocks.mapIndexed { index, block ->
        toAndroidChatMessageJson(block, index, blocks.size)
    }
}

private fun PersistedChatMessage.toAndroidChatMessageJson(
    block: PersistedAssistantResponseBlock?,
    blockIndex: Int,
    blockCount: Int,
): JsonObject = buildJsonObject {
    val isLastBlock = blockIndex == blockCount - 1
    val blockId = block?.id.orEmpty().ifBlank {
        if (blockCount == 1) id else "$id-$blockIndex"
    }
    put("id", blockId)
    put("author", if (fromUser) "User" else "Agent")
    put("text", if (block?.type == PersistedAssistantResponseBlockType.Text) block.text else if (block == null) text else "")
    if (createdAtMillis > 0L) put("createdAtMillis", createdAtMillis + blockIndex)

    val hasReasoningBlock = responseBlocks.any { it.type == PersistedAssistantResponseBlockType.Reasoning }
    if (isLastBlock && !hasReasoningBlock && thoughtDurationMillis > 0L) {
        put("thoughtDurationMillis", thoughtDurationMillis)
    }
    if (block?.type == PersistedAssistantResponseBlockType.Reasoning) {
        val trace = block.reasoningTrace ?: PersistedReasoningTrace(
            id = blockId,
            rawText = block.text,
            startedAtMillis = createdAtMillis,
            completedAtMillis = completedAtMillis,
        )
        put("reasoningTrace", trace.toAndroidReasoningTraceJson())
    }
    if (!fromUser) {
        put(
            "responseGroupId",
            responseGroupId.ifBlank { "agent-group-${createdAtMillis.coerceAtLeast(0L)}-$id" },
        )
    }
    if (assistantActionsHidden) put("assistantActionsHidden", true)
    if (displayKind != PersistedMessageDisplayKind.Standard) put("displayKind", displayKind.name)
    if (isLastBlock) usage?.let { put("usageStatistics", toAndroidUsageJson(it)) }
    if ((fromUser || isLastBlock) && providerPayloadJson.isNotBlank()) {
        put("providerPayloadJson", providerPayloadJson)
    }

    val blockTools = when (block?.type) {
        PersistedAssistantResponseBlockType.Reasoning ->
            block.reasoningTrace?.toolInvocations.orEmpty().ifEmpty { block.tools }
        PersistedAssistantResponseBlockType.ToolGroup -> block.tools
        else -> if (block == null) tools else emptyList()
    }
    put("toolInvocations", buildJsonArray { blockTools.forEach { add(it.toAndroidToolJson()) } })
    put("attachments", buildJsonArray {
        if (fromUser || isLastBlock) attachments.forEach { add(it.toAndroidAttachmentJson()) }
    })
    if (fromUser && userBranches.size > 1) {
        val selectedIndex = selectedUserBranchIndex.coerceIn(0, userBranches.lastIndex)
        put("branchGroup", buildJsonObject {
            put("selectedIndex", selectedIndex)
            put("branches", buildJsonArray {
                userBranches.forEach { branch ->
                    add(buildJsonArray {
                        branch.forEach { message -> message.toAndroidChatMessageJson().forEach(::add) }
                    })
                }
            })
        })
    }
}

private fun PersistedReasoningTrace.toAndroidReasoningTraceJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("rawText", if (chunks.any { it.title.isNotBlank() || it.detail.isNotBlank() }) "" else rawText)
    put("latestStatusText", latestStatusText)
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("chunks", buildJsonArray {
        chunks.forEach { chunk ->
            add(buildJsonObject {
                put("id", chunk.id)
                put("title", chunk.title)
                put("detail", chunk.detail)
                put("rawText", if (chunk.title.isNotBlank() || chunk.detail.isNotBlank()) "" else chunk.rawText)
                put("isPending", chunk.isPending)
                put("createdAtMillis", chunk.createdAtMillis)
                put("timelineOrder", chunk.timelineOrder)
            })
        }
    })
    put("toolInvocations", buildJsonArray { toolInvocations.forEach { add(it.toAndroidToolJson()) } })
}

private fun PersistedChatTool.toAndroidToolJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("toolName", name)
    put("argumentsJson", argumentsJson)
    put("outputJson", outputJson.ifBlank { output })
    put("isRunning", isRunning)
    put("startedAtUptimeMillis", startedAtUptimeMillis)
    completedAtUptimeMillis?.let { put("completedAtUptimeMillis", it) }
    put("startedAtMillis", startedAtMillis)
    completedAtMillis?.let { put("completedAtMillis", it) }
    put("timelineOrder", timelineOrder)
}

private fun PersistedChatAttachment.toAndroidAttachmentJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("uri", sourceIdentifier)
    put("name", name)
    put("mimeType", mimeType)
    put("kind", if (mimeType.startsWith("image/")) "Image" else "File")
    put("workspacePath", workspacePath)
    put("sizeBytes", sizeBytes)
    inlineBase64.takeIf(String::isNotBlank)?.let { put("inlineBase64", it) }
}

private fun PersistedChatMessage.toAndroidUsageJson(usage: PersistedChatUsage): JsonObject = buildJsonObject {
    if (usage.inputTokensAvailable) put("inputTokens", usage.inputTokens)
    if (usage.outputTokensAvailable) put("outputTokens", usage.outputTokens)
    if (usage.totalTokensAvailable) put("totalTokens", usage.totalTokens)
    if (usage.reasoningTokensAvailable) put("reasoningTokens", usage.reasoningTokens)
    if (usage.cachedInputTokensAvailable) put("cachedInputTokens", usage.cachedInputTokens)
    put("requestCount", usage.requestCount.coerceAtLeast(1))
    put("tokenUsageSource", tokenUsageSource)
    if (createdAtMillis > 0L) put("startedAtMillis", createdAtMillis)
    val firstTokenAt = firstTokenLatencyMillis?.let { latency ->
        createdAtMillis.takeIf { it > 0L }?.plus(latency.coerceAtLeast(0L))
    }
    firstTokenAt?.let { put("firstTokenAtMillis", it) }
    val resolvedCompletedAt = completedAtMillis ?: firstTokenAt?.plus(responseDurationMillis.coerceAtLeast(0L))
    resolvedCompletedAt?.takeIf { it > 0L }?.let { put("completedAtMillis", it) }
}

private data class AndroidArchiveMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val createdAtMillis: Long,
    val thoughtDurationMillis: Long?,
    val reasoningTrace: PersistedReasoningTrace?,
    val tools: List<PersistedChatTool>,
    val attachments: List<PersistedChatAttachment>,
    val responseGroupId: String,
    val assistantActionsHidden: Boolean,
    val displayKind: PersistedMessageDisplayKind,
    val usage: PersistedChatUsage?,
    val startedAtMillis: Long,
    val firstTokenAtMillis: Long?,
    val completedAtMillis: Long?,
    val tokenUsageSource: String,
    val providerPayloadJson: String,
    val userBranches: List<List<PersistedChatMessage>>,
    val selectedUserBranchIndex: Int,
)

private fun JsonObject.toPersistedChatSession(index: Int): PersistedChatSession {
    val rawMessages = optionalStrictArray("messages", "chat session at index $index")
        .orEmpty().mapIndexed { messageIndex, element ->
            checkNotNull(element as? JsonObject) { "Invalid chat message at index $messageIndex" }
                .toAndroidArchiveMessage(messageIndex)
    }
    return PersistedChatSession(
        id = string("id").ifBlank { "session-$index" },
        title = string("title"),
        preview = string("preview"),
        messages = rawMessages.coalesceAndroidAssistantGroups(),
        hasCustomTitle = boolean("hasCustomTitle"),
        selectedSkillIds = stringList("selectedSkillIds"),
        activeSkills = decodeSharedActiveSkillContexts(string("activeSkillsJson")),
        activeMcpServerIds = stringList("activeMcpServerIds"),
        chromeEnabled = boolean("chromeEnabled"),
        selectedModelKey = string("selectedModelKey"),
    )
}

private fun List<AndroidArchiveMessage>.coalesceAndroidAssistantGroups(): List<PersistedChatMessage> = buildList {
    var index = 0
    while (index < this@coalesceAndroidAssistantGroups.size) {
        val message = this@coalesceAndroidAssistantGroups[index]
        if (message.fromUser || message.responseGroupId.isBlank()) {
            add(message.toPersistedChatMessage())
            index += 1
            continue
        }
        var end = index + 1
        while (end < this@coalesceAndroidAssistantGroups.size) {
            val next = this@coalesceAndroidAssistantGroups[end]
            if (next.fromUser || next.responseGroupId != message.responseGroupId) break
            end += 1
        }
        add(this@coalesceAndroidAssistantGroups.subList(index, end).toPersistedAssistantGroup())
        index = end
    }
}

private fun AndroidArchiveMessage.toPersistedChatMessage(): PersistedChatMessage = PersistedChatMessage(
    id = id,
    text = text,
    fromUser = fromUser,
    tools = tools,
    responseBlocks = toResponseBlocks(),
    attachments = attachments,
    usage = usage,
    responseGroupId = responseGroupId,
    createdAtMillis = createdAtMillis,
    completedAtMillis = completedAtMillis,
    thoughtDurationMillis = thoughtDurationMillis ?: 0L,
    responseDurationMillis = outputDurationMillis(),
    firstTokenLatencyMillis = firstTokenLatencyMillis(),
    tokenUsageSource = tokenUsageSource,
    providerPayloadJson = providerPayloadJson,
    assistantActionsHidden = assistantActionsHidden,
    displayKind = displayKind,
    userBranches = userBranches,
    selectedUserBranchIndex = selectedUserBranchIndex,
)

private fun List<AndroidArchiveMessage>.toPersistedAssistantGroup(): PersistedChatMessage {
    val first = first()
    val metrics = lastOrNull { it.usage != null } ?: last()
    val blocks = flatMap(AndroidArchiveMessage::toResponseBlocks)
    val groupTools = flatMap(AndroidArchiveMessage::tools).distinctBy(PersistedChatTool::id)
    val groupAttachments = flatMap(AndroidArchiveMessage::attachments)
    return PersistedChatMessage(
        id = first.id,
        text = map(AndroidArchiveMessage::text).filter(String::isNotBlank).joinToString("\n\n"),
        fromUser = false,
        reasoningText = mapNotNull { it.reasoningTrace?.rawText?.takeIf(String::isNotBlank) }.joinToString("\n\n"),
        tools = groupTools,
        responseBlocks = blocks,
        attachments = groupAttachments,
        usage = metrics.usage,
        responseGroupId = first.responseGroupId,
        createdAtMillis = first.createdAtMillis,
        completedAtMillis = metrics.completedAtMillis,
        thoughtDurationMillis = mapNotNull(AndroidArchiveMessage::thoughtDurationMillis).lastOrNull() ?: 0L,
        responseDurationMillis = metrics.outputDurationMillis(),
        firstTokenLatencyMillis = metrics.firstTokenLatencyMillis(),
        tokenUsageSource = metrics.tokenUsageSource,
        providerPayloadJson = metrics.providerPayloadJson,
        assistantActionsHidden = any(AndroidArchiveMessage::assistantActionsHidden),
        displayKind = first.displayKind,
    )
}

private fun AndroidArchiveMessage.toResponseBlocks(): List<PersistedAssistantResponseBlock> = when {
    reasoningTrace != null -> listOf(
        PersistedAssistantResponseBlock(
            id = reasoningTrace.id.ifBlank { id },
            type = PersistedAssistantResponseBlockType.Reasoning,
            text = reasoningTrace.rawText,
            tools = tools,
            reasoningTrace = reasoningTrace,
        ),
    )
    tools.isNotEmpty() -> listOf(
        PersistedAssistantResponseBlock(
            id = id,
            type = PersistedAssistantResponseBlockType.ToolGroup,
            tools = tools,
        ),
    )
    text.isNotBlank() && !fromUser -> listOf(
        PersistedAssistantResponseBlock(
            id = id,
            type = PersistedAssistantResponseBlockType.Text,
            text = text,
        ),
    )
    else -> emptyList()
}

private fun AndroidArchiveMessage.firstTokenLatencyMillis(): Long? =
    firstTokenAtMillis?.takeIf { startedAtMillis > 0L }
        ?.let { (it - startedAtMillis).coerceAtLeast(0L) }

private fun AndroidArchiveMessage.outputDurationMillis(): Long {
    val start = firstTokenAtMillis ?: startedAtMillis.takeIf { it > 0L } ?: return 0L
    val end = completedAtMillis ?: return 0L
    return (end - start).coerceAtLeast(0L)
}

private fun JsonObject.toAndroidArchiveMessage(index: Int): AndroidArchiveMessage {
    val usageObject = this["usageStatistics"] as? JsonObject
    val branchObject = this["branchGroup"] as? JsonObject
    val rawBranches = branchObject?.array("branches").orEmpty().mapNotNull { branch ->
        (branch as? JsonArray)?.mapIndexed { branchIndex, element ->
            checkNotNull(element as? JsonObject) { "Invalid chat message at index $branchIndex" }
                .toAndroidArchiveMessage(branchIndex)
        }?.coalesceAndroidAssistantGroups()
    }.filter(List<PersistedChatMessage>::isNotEmpty)
    val selectedBranch = branchObject?.long("selectedIndex")?.toInt()
        ?.coerceIn(0, rawBranches.lastIndex.coerceAtLeast(0)) ?: 0
    return AndroidArchiveMessage(
        id = string("id").ifBlank { "message-$index" },
        fromUser = string("author") == "User",
        text = string("text"),
        createdAtMillis = long("createdAtMillis") ?: 0L,
        thoughtDurationMillis = long("thoughtDurationMillis"),
        reasoningTrace = (this["reasoningTrace"] as? JsonObject)?.toPersistedReasoningTrace(),
        tools = array("toolInvocations").orEmpty().mapNotNull { (it as? JsonObject)?.toPersistedChatTool() },
        attachments = array("attachments").orEmpty().mapIndexedNotNull { attachmentIndex, element ->
            (element as? JsonObject)?.toPersistedChatAttachment(attachmentIndex)
        },
        responseGroupId = string("responseGroupId"),
        assistantActionsHidden = boolean("assistantActionsHidden"),
        displayKind = PersistedMessageDisplayKind.entries.firstOrNull { it.name == string("displayKind") }
            ?: PersistedMessageDisplayKind.Standard,
        usage = usageObject?.toPersistedChatUsage(),
        startedAtMillis = usageObject?.long("startedAtMillis") ?: 0L,
        firstTokenAtMillis = usageObject?.long("firstTokenAtMillis"),
        completedAtMillis = usageObject?.long("completedAtMillis"),
        tokenUsageSource = usageObject?.string("tokenUsageSource").orEmpty().ifBlank { "unavailable" },
        providerPayloadJson = string("providerPayloadJson"),
        userBranches = rawBranches,
        selectedUserBranchIndex = selectedBranch,
    )
}

private fun JsonObject.toPersistedChatTool(): PersistedChatTool = PersistedChatTool(
    id = string("id"),
    name = string("toolName"),
    summary = "",
    output = string("outputJson"),
    argumentsJson = string("argumentsJson"),
    outputJson = string("outputJson"),
    isRunning = boolean("isRunning"),
    startedAtUptimeMillis = long("startedAtUptimeMillis") ?: 0L,
    completedAtUptimeMillis = long("completedAtUptimeMillis"),
    startedAtMillis = long("startedAtMillis") ?: 0L,
    completedAtMillis = long("completedAtMillis"),
    timelineOrder = long("timelineOrder") ?: 0L,
)

private fun JsonObject.toPersistedReasoningTrace(): PersistedReasoningTrace = PersistedReasoningTrace(
    id = string("id"),
    rawText = string("rawText"),
    chunks = array("chunks").orEmpty().mapIndexedNotNull { index, element ->
        val chunk = element as? JsonObject ?: return@mapIndexedNotNull null
        PersistedReasoningSummaryChunk(
            id = chunk.string("id").ifBlank { "reasoning-summary-$index" },
            title = chunk.string("title"),
            detail = chunk.string("detail"),
            rawText = chunk.string("rawText"),
            isPending = chunk.boolean("isPending"),
            createdAtMillis = chunk.long("createdAtMillis") ?: 0L,
            timelineOrder = chunk.long("timelineOrder") ?: 0L,
        )
    },
    toolInvocations = array("toolInvocations").orEmpty().mapNotNull {
        (it as? JsonObject)?.toPersistedChatTool()
    },
    latestStatusText = string("latestStatusText"),
    startedAtMillis = long("startedAtMillis") ?: 0L,
    completedAtMillis = long("completedAtMillis"),
)

private fun JsonObject.toPersistedChatAttachment(index: Int): PersistedChatAttachment = PersistedChatAttachment(
    id = string("id").ifBlank { "attachment-$index" },
    name = string("name").ifBlank { "Attachment ${index + 1}" },
    mimeType = string("mimeType"),
    workspacePath = string("workspacePath"),
    sizeBytes = long("sizeBytes") ?: 0L,
    inlineBase64 = string("inlineBase64"),
    sourceIdentifier = string("uri"),
)

private fun JsonObject.toPersistedChatUsage(): PersistedChatUsage = PersistedChatUsage(
    inputTokens = long("inputTokens") ?: 0L,
    outputTokens = long("outputTokens") ?: 0L,
    totalTokens = long("totalTokens") ?: 0L,
    reasoningTokens = long("reasoningTokens") ?: 0L,
    cachedInputTokens = long("cachedInputTokens") ?: 0L,
    inputTokensAvailable = "inputTokens" in this,
    outputTokensAvailable = "outputTokens" in this,
    totalTokensAvailable = "totalTokens" in this,
    reasoningTokensAvailable = "reasoningTokens" in this,
    cachedInputTokensAvailable = "cachedInputTokens" in this,
    requestCount = (long("requestCount") ?: 1L).coerceAtLeast(1L).toInt(),
)

private fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: false

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.optionalStrictArray(name: String, owner: String): JsonArray? {
    val value = this[name] ?: return null
    if (value is kotlinx.serialization.json.JsonNull) return null
    return checkNotNull(value as? JsonArray) { "Invalid $name array for $owner" }
}

private fun JsonObject.stringList(name: String): List<String> = array(name).orEmpty().mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}.distinct()
