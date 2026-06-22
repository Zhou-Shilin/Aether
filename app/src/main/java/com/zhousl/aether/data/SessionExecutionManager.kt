package com.zhousl.aether.data

import android.app.Application
import android.os.SystemClock
import com.zhousl.aether.AetherForegroundService
import com.zhousl.aether.AetherNotificationController
import com.zhousl.aether.AppForegroundTracker
import com.zhousl.aether.runtime.RuntimeRouter
import com.zhousl.aether.runtime.RuntimeShellTool
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.AssistantResponseBlock
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.ChatUsageStatistics
import com.zhousl.aether.ui.MessageDisplayKind
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.ReasoningSummaryChunk
import com.zhousl.aether.ui.ReasoningTrace
import com.zhousl.aether.ui.syncActiveBranches
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val ReasoningInitialSummaryTokenThreshold = 100
private const val ReasoningTimedSummaryIntervalMillis = 5_000L
private const val ReasoningSummaryMaxInputChars = 8_000
private const val ReasoningSummaryTitleMaxChars = 120
private const val ReasoningSummaryDetailMaxChars = 520
private const val ReasoningSummarySystemPrompt =
    "You write concise user-visible progress summaries for assistant reasoning. Use a consistent first-person planning style, and never quote long private reasoning verbatim."

enum class SessionFollowUpMode {
    Queue,
    Steer,
}

enum class SessionTurnOutcome {
    Success,
    ValidationError,
    Failure,
    Neutral,
}

data class PendingSessionInput(
    val id: String,
    val mode: SessionFollowUpMode,
    val preview: String,
    val attachmentCount: Int,
)

data class SessionExecutionState(
    val sessionId: String,
    val isRunning: Boolean = false,
    val pendingToolInvocations: List<ChatToolInvocation> = emptyList(),
    val pendingResponseBlocks: List<AssistantResponseBlock> = emptyList(),
    val pendingAssistantText: String = "",
    val pendingStatusText: String = "",
    val pendingStatusDetail: String = "",
    val pendingInputs: List<PendingSessionInput> = emptyList(),
    val activeTurnStartedAtMillis: Long? = null,
)

data class SessionTurnRequest(
    val sessionId: String,
    val settings: AppSettings,
    val requestMessages: List<ChatMessage>,
    val selectedSkillIds: List<String>,
    val activeSkills: List<ActiveSkillContext>,
    val activeMcpServerIds: List<String>,
    val agentModeEnabled: Boolean,
    val providerConfigs: List<LlmProviderConfig> = emptyList(),
)

data class SessionTurnEvent(
    val sessionId: String,
    val outcome: SessionTurnOutcome,
    val toolCallCount: Int = 0,
    val distinctToolCount: Int = 0,
    val toolNames: List<String> = emptyList(),
    val durationMillis: Long? = null,
    val tokenUsage: LlmTokenUsage? = null,
    val tokenUsageSource: String = "unavailable",
    val inputMessageCount: Int = 0,
    val userMessageCount: Int = 0,
)

private enum class ReasoningCompletionTrigger {
    BodyStarted,
    TurnFinished,
}

private data class ReasoningSummary(
    val title: String,
    val detail: String,
)

class SessionExecutionManager(
    private val application: Application,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val extensionsRepository: AgentExtensionsRepository,
    private val chatStateStore: ChatStateStore,
    private val bashTool: TermuxBashTool,
    private val runtimeRouter: RuntimeRouter,
    private val workspaceFileBridge: WorkspaceFileBridge,
    private val rootSetupController: RootSetupController,
    private val agentModeController: AgentModeController,
    private val skillManager: AgentSkillManager,
    private val scheduledTaskManager: ScheduledTaskManager,
    private val webToolsClient: WebToolsClient,
    private val notificationController: AetherNotificationController,
    private val appForegroundTracker: AppForegroundTracker,
    private val diagnosticLogger: AetherDiagnosticLogger = AetherDiagnosticLogger.NoOp,
) {
    private val currentSettings = MutableStateFlow(AppSettings())
    private val currentProviderConfigs = MutableStateFlow<List<LlmProviderConfig>>(emptyList())
    private val currentExtensionsState = MutableStateFlow(AgentExtensionsState())
    private val _executionStates = MutableStateFlow<Map<String, SessionExecutionState>>(emptyMap())
    private val _turnEvents = MutableSharedFlow<SessionTurnEvent>(extraBufferCapacity = 8)
    private val executionHandles = ConcurrentHashMap<String, SessionExecutionHandle>()
    private val queuedTurnRequestBuilder = QueuedTurnRequestBuilder(chatStateStore)

    val executionStates: StateFlow<Map<String, SessionExecutionState>> = _executionStates.asStateFlow()
    val turnEvents = _turnEvents.asSharedFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                currentSettings.value = settings
                if (
                    settings.keepTasksRunningInBackground &&
                    _executionStates.value.values.any { it.isRunning }
                ) {
                    ensureForegroundServiceRunning()
                }
            }
        }
        scope.launch {
            extensionsRepository.extensionState.collect { currentExtensionsState.value = it }
        }
        scope.launch {
            settingsRepository.providerConfigs.collect { currentProviderConfigs.value = it }
        }
    }

    fun isSessionRunning(sessionId: String): Boolean =
        _executionStates.value[sessionId]?.isRunning == true

    fun startTurn(request: SessionTurnRequest) {
        val handle = SessionExecutionHandle(sessionId = request.sessionId)
        if (executionHandles.putIfAbsent(request.sessionId, handle) != null) return

        val validationError = validateRequest(request)
        if (validationError != null) {
            diagnosticLogger.event(
                category = "session",
                event = "turn_validation_failed",
                level = "warn",
                sessionId = request.sessionId,
                details = mapOf("message" to validationError),
            )
            executionHandles.remove(request.sessionId, handle)
            val completion = appendAgentMessage(
                sessionId = request.sessionId,
                blocks = listOf(
                    AssistantResponseBlock.Text(
                        id = "agent-validation-${System.currentTimeMillis()}",
                        text = validationError,
                    )
                ),
                thoughtDurationMillis = null,
                outcome = SessionTurnOutcome.ValidationError,
            )
            _turnEvents.tryEmit(completion.toTurnEvent(request.sessionId))
            return
        }

        updateExecutionState(request.sessionId) {
            it.copy(
                sessionId = request.sessionId,
                isRunning = true,
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                pendingStatusDetail = "",
                activeTurnStartedAtMillis = System.currentTimeMillis(),
            )
        }
        diagnosticLogger.event(
            category = "session",
            event = "turn_queued",
            sessionId = request.sessionId,
            details = mapOf(
                "provider" to request.settings.provider.storageValue,
                "model" to request.settings.modelId,
                "base_url" to DiagnosticRedactor.sanitizedBaseUrl(request.settings.baseUrl),
                "request_message_count" to request.requestMessages.size,
                "selected_skill_count" to request.selectedSkillIds.size,
                "active_mcp_server_count" to request.activeMcpServerIds.size,
                "agent_mode_enabled" to request.agentModeEnabled,
            ),
        )

        handle.job = scope.launch {
            runSession(
                handle = handle,
                initialRequest = request,
            )
        }
    }

    fun submitFollowUp(
        sessionId: String,
        message: ChatMessage,
        mode: SessionFollowUpMode,
    ): Boolean {
        val handle = executionHandles[sessionId] ?: return false
        val pending = PendingEnvelope(
            id = "pending-${System.currentTimeMillis()}-${message.id}",
            mode = mode,
            message = message,
        )
        synchronized(handle.lock) {
            when (mode) {
                SessionFollowUpMode.Queue -> handle.queuedInputs += pending
                SessionFollowUpMode.Steer -> handle.steerInputs += pending
            }
        }
        updateExecutionState(sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs + pending.toUiState()
            )
        }
        return true
    }

    fun pauseSession(sessionId: String) {
        val handle = executionHandles[sessionId] ?: return
        if (handle.pauseRequested) return
        handle.pauseRequested = true
        val snapshot = _executionStates.value[sessionId]
        val runningRunIds = snapshot?.pendingToolInvocations?.let(::extractActiveManagedRunIds).orEmpty()
        val completion = finalizePausedTurn(
            handle = handle,
            snapshot = snapshot ?: SessionExecutionState(sessionId = sessionId),
        )
        scope.launch(Dispatchers.IO) {
            runCatching { chatStateStore.flush() }
        }
        handle.pauseFinalized = true
        executionHandles.remove(sessionId, handle)
        updateExecutionState(sessionId) {
            it.copy(
                sessionId = sessionId,
                isRunning = false,
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                pendingStatusDetail = "",
                pendingInputs = emptyList(),
                activeTurnStartedAtMillis = null,
            )
        }
        _turnEvents.tryEmit(completion.toTurnEvent(sessionId))
        handle.job?.cancel(CancellationException("Paused by user."))
        if (runningRunIds.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val shellTool = RuntimeShellTool(runtimeRouter)
                runningRunIds.forEach { runId ->
                    runCatching { shellTool.killByRunId(runId) }
                }
            }
        }
    }

    suspend fun startScheduledTask(task: ScheduledTask): Boolean {
        if (!task.isEnabled || task.prompt.isBlank()) return false
        val settings = settingsRepository.settings.first()
        val providerConfigs = settingsRepository.providerConfigs.first()
        val sessionId = task.defaultSessionId()
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = "scheduled-${task.id.take(8)}-$now",
            author = MessageAuthor.User,
            text = task.prompt,
            createdAtMillis = now,
        )

        if (isSessionRunning(sessionId)) {
            return submitFollowUp(
                sessionId = sessionId,
                message = userMessage,
                mode = SessionFollowUpMode.Queue,
            )
        }

        var selectedModelKey = ""
        var requestMessages: List<ChatMessage> = emptyList()
        var selectedSkillIds: List<String> = emptyList()
        var activeSkills: List<ActiveSkillContext> = emptyList()
        var activeMcpServerIds: List<String> = emptyList()
        var agentModeEnabled = false

        chatStateStore.updateAndFlush { persisted ->
            val updatedSessions = persisted.sessions.toMutableList()
            val existingIndex = updatedSessions.indexOfFirst { it.id == sessionId }
            val updatedSession = if (existingIndex >= 0) {
                val existing = updatedSessions.removeAt(existingIndex)
                existing.withDerivedMessages(existing.messages + userMessage)
            } else {
                ChatSession(
                    id = sessionId,
                    title = task.name.ifBlank { "Scheduled task" },
                    preview = userMessage.summaryText(),
                    hasCustomTitle = true,
                    messages = listOf(userMessage),
                    selectedModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
                )
            }
            selectedModelKey = updatedSession.selectedModelKey
            requestMessages = updatedSession.messages
            selectedSkillIds = updatedSession.selectedSkillIds
            activeSkills = updatedSession.activeSkills
            activeMcpServerIds = updatedSession.activeMcpServerIds
            agentModeEnabled = updatedSession.agentModeEnabled
            updatedSessions.add(0, updatedSession)
            persisted.copy(
                sessions = updatedSessions,
                currentSessionId = sessionId,
            )
        }

        startTurn(
            SessionTurnRequest(
                sessionId = sessionId,
                settings = resolveModelSettings(
                    baseSettings = settings,
                    providerConfigs = providerConfigs,
                    preferredModelKey = selectedModelKey,
                    fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
                ),
                requestMessages = requestMessages,
                selectedSkillIds = selectedSkillIds,
                activeSkills = activeSkills,
                activeMcpServerIds = activeMcpServerIds,
                agentModeEnabled = agentModeEnabled,
                providerConfigs = providerConfigs,
            )
        )
        return true
    }

    private suspend fun runSession(
        handle: SessionExecutionHandle,
        initialRequest: SessionTurnRequest,
    ) {
        var nextRequest: SessionTurnRequest? = initialRequest
        var lastCompletion: CompletionSummary? = null

        try {
            while (nextRequest != null && !handle.pauseRequested) {
                lastCompletion = executeTurn(
                    handle = handle,
                    request = nextRequest,
                )
                if (handle.pauseRequested) break
                promoteRemainingSteersToQueue(handle)
                if (handle.pauseRequested) break

                val nextQueued = pollNextQueuedInput(handle) ?: break
                nextRequest = buildQueuedTurnRequest(
                    sessionId = handle.sessionId,
                    queuedInput = nextQueued.message,
                )
            }
        } finally {
            clearPendingInputs(handle)
            if (executionHandles.remove(handle.sessionId, handle)) {
                updateExecutionState(handle.sessionId) {
                    it.copy(
                        sessionId = handle.sessionId,
                        isRunning = false,
                        pendingToolInvocations = emptyList(),
                        pendingResponseBlocks = emptyList(),
                        pendingAssistantText = "",
                        pendingStatusText = "",
                        pendingStatusDetail = "",
                        pendingInputs = emptyList(),
                        activeTurnStartedAtMillis = null,
                    )
                }
            }

            if (
                !handle.pauseRequested &&
                lastCompletion != null &&
                currentSettings.value.notifyOnTaskCompletion &&
                !appForegroundTracker.isForeground.value
            ) {
                notificationController.notifyCompletion(
                    sessionId = handle.sessionId,
                    sessionTitle = lastCompletion.sessionTitle,
                    summary = lastCompletion.summary,
                    failed = lastCompletion.outcome == SessionTurnOutcome.Failure,
                )
            }
        }
    }

    private suspend fun executeTurn(
        handle: SessionExecutionHandle,
        request: SessionTurnRequest,
    ): CompletionSummary {
        val turnStartedAtMillis = System.currentTimeMillis()
        val turnId = "turn-$turnStartedAtMillis"
        var firstAssistantTokenAtMillis: Long? = null
        diagnosticLogger.event(
            category = "session",
            event = "turn_start",
            sessionId = handle.sessionId,
            turnId = turnId,
            details = mapOf(
                "session_title" to resolveSessionTitle(handle.sessionId),
                "provider" to request.settings.provider.storageValue,
                "model" to request.settings.modelId,
                "base_url" to DiagnosticRedactor.sanitizedBaseUrl(request.settings.baseUrl),
            ),
        )
        val mcpClientManager = McpClientManager(
            runtimeRouter = runtimeRouter,
            settings = request.settings,
            diagnosticLogger = diagnosticLogger,
        )
        val agent = AetherAgent(
            client = OpenAiCompatibleClient(diagnosticLogger = diagnosticLogger),
            runtimeRouter = runtimeRouter,
            workspaceFileBridge = workspaceFileBridge,
            agentModeController = agentModeController,
            skillManager = skillManager,
            mcpClientManager = mcpClientManager,
            webToolsClient = webToolsClient,
            selfManagementTool = AetherSelfManagementTool(
                settingsRepository = settingsRepository,
                extensionsRepository = extensionsRepository,
                skillManager = skillManager,
                bashTool = bashTool,
                rootSetupController = rootSetupController,
                agentModeController = agentModeController,
                mcpClientManager = mcpClientManager,
                scheduledTaskManager = scheduledTaskManager,
                diagnosticLogger = diagnosticLogger,
            ),
            diagnosticLogger = diagnosticLogger,
            onParallelToolCallsUnsupported = settingsRepository::markParallelToolCallsUnsupported,
        )

        updateExecutionState(handle.sessionId) {
            it.copy(
                sessionId = handle.sessionId,
                isRunning = true,
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                pendingStatusDetail = "",
                activeTurnStartedAtMillis = turnStartedAtMillis,
            )
        }

        return try {
            var resolvedActiveSkills = resolveSelectedActiveSkills(
                selectedSkillIds = request.selectedSkillIds,
                existingActiveSkills = request.activeSkills,
            )
            var resolvedSelectedSkillIds = resolvedActiveSkills.map { it.skillId }
            val resolvedAvailableSkills = currentExtensionsState.value.installedSkills
                .filter { it.isEnabled }
                .sortedBy { it.name.lowercase() }
            val resolvedMcpServers = resolveSelectedMcpServers(request.activeMcpServerIds)
            val resolvedMcpServerIds = resolvedMcpServers.map { it.id }
            updateSessionSelections(
                sessionId = handle.sessionId,
                selectedSkillIds = resolvedSelectedSkillIds,
                activeSkills = resolvedActiveSkills,
                activeMcpServerIds = resolvedMcpServerIds,
            )

            val workspaceDirectory = workspaceFileBridge.workspaceDirectory(
                sessionId = handle.sessionId,
                mode = request.settings.agentWorkspaceMode,
            )
            val runtimeWorkspaceDirectory = runtimeRouter.runtimeWorkspaceDirectory(
                settings = request.settings,
                sharedTermuxWorkspace = workspaceDirectory,
            )
            diagnosticLogger.event(
                category = "mcp",
                event = "sync_start",
                sessionId = handle.sessionId,
                turnId = turnId,
                details = mapOf(
                    "server_count" to resolvedMcpServers.size,
                    "workspace_directory" to runtimeWorkspaceDirectory,
                ),
            )
            mcpClientManager.syncServers(
                servers = resolvedMcpServers,
                workspaceDirectory = runtimeWorkspaceDirectory,
            )
            diagnosticLogger.event(
                category = "mcp",
                event = "sync_end",
                sessionId = handle.sessionId,
                turnId = turnId,
                details = mapOf(
                    "server_count" to resolvedMcpServers.size,
                    "tool_binding_count" to mcpClientManager.toolBindings().size,
                ),
            )
            val reasoningTraceToolRoutingEnabled = request.settings.supportsVisibleReasoningTrace()
            val emitToolEvent: suspend (AgentToolEvent) -> Unit = { event ->
                if (!handle.pauseRequested) {
                    handleToolEvent(
                        handle = handle,
                        event = event,
                        reasoningTraceToolRoutingEnabled = reasoningTraceToolRoutingEnabled,
                    )
                }
            }

            val result = agent.runTurn(
                settings = request.settings,
                messages = buildRequestMessages(request.requestMessages),
                workspaceDirectory = runtimeWorkspaceDirectory,
                availableSkills = resolvedAvailableSkills,
                activeSkills = resolvedActiveSkills,
                mcpToolBindings = mcpClientManager.toolBindings(),
                agentModeEnabled = request.agentModeEnabled,
                providerConfigs = request.providerConfigs,
                onToolEvent = emitToolEvent,
                onToolProgress = if (request.settings.termuxLiveOutputEnabled) emitToolEvent else null,
                onAssistantReasoningDelta = { delta ->
                    if (handle.pauseRequested) return@runTurn
                    if (delta.isEmpty()) return@runTurn
                    handle.finishDirectReasoningSummaryChunk()
                    appendReasoningDelta(
                        handle = handle,
                        delta = delta,
                    )
                },
                onAssistantReasoningSummaryDelta = { delta ->
                    if (handle.pauseRequested) return@runTurn
                    if (delta.isEmpty()) return@runTurn
                    appendDirectReasoningSummaryDelta(
                        handle = handle,
                        delta = delta,
                    )
                },
                onAssistantTextDelta = { delta ->
                    if (handle.pauseRequested) return@runTurn
                    if (delta.isEmpty()) return@runTurn
                    if (firstAssistantTokenAtMillis == null) {
                        firstAssistantTokenAtMillis = System.currentTimeMillis()
                    }
                    handle.finishDirectReasoningSummaryChunk()
                    completeActiveReasoning(
                        handle = handle,
                        trigger = ReasoningCompletionTrigger.BodyStarted,
                    )
                    updateExecutionState(handle.sessionId) { current ->
                        val pendingResponseBlocks = appendAssistantResponseText(
                            blocks = current.pendingResponseBlocks,
                            delta = delta,
                        ) { handle.nextPendingBlockId("pending-text") }
                        current.copy(
                            pendingStatusText = "",
                            pendingStatusDetail = "",
                            pendingAssistantText = pendingTrailingAssistantText(pendingResponseBlocks),
                            pendingResponseBlocks = pendingResponseBlocks,
                        )
                    }
                },
                onAssistantTextReset = {
                    if (handle.pauseRequested) return@runTurn
                    updateExecutionState(handle.sessionId) { current ->
                        if (current.pendingAssistantText.isEmpty()) {
                            current
                        } else {
                            current.copy(pendingAssistantText = "")
                        }
                    }
                },
                onStreamingStatus = { status ->
                    if (handle.pauseRequested) return@runTurn
                    updateExecutionState(handle.sessionId) { current ->
                        current.copy(
                            pendingStatusText = status?.text.orEmpty(),
                            pendingStatusDetail = status?.detail.orEmpty(),
                        )
                    }
                },
                onSkillActivated = { activeSkill ->
                    if (handle.pauseRequested) return@runTurn
                    resolvedSelectedSkillIds = (resolvedSelectedSkillIds + activeSkill.skillId).distinct()
                    resolvedActiveSkills = upsertActiveSkillContext(resolvedActiveSkills, activeSkill)
                    updateSessionSelections(
                        sessionId = handle.sessionId,
                        selectedSkillIds = resolvedSelectedSkillIds,
                        activeSkills = resolvedActiveSkills,
                        activeMcpServerIds = resolvedMcpServerIds,
                    )
                },
                pollInjectedUserMessages = {
                    if (handle.pauseRequested) return@runTurn emptyList()
                    val drained = drainSteerInputs(handle)
                    if (drained.isNotEmpty()) {
                        appendSteerInterruptionMessages(handle, drained)
                    }
                    drained.map { buildSteerRequestMessage(it.message) }
                },
            )
            if (handle.pauseRequested) {
                return finalizePausedTurn(
                    handle = handle,
                    snapshot = _executionStates.value[handle.sessionId] ?: SessionExecutionState(sessionId = handle.sessionId),
                )
            }

            completeActiveReasoning(
                handle = handle,
                trigger = ReasoningCompletionTrigger.TurnFinished,
            )
            val thoughtDurationMillis = (System.currentTimeMillis() - turnStartedAtMillis).coerceAtLeast(0L)
            val turnCompletedAtMillis = System.currentTimeMillis()
            val estimatedTokenUsage = estimateRequestTokenUsage(request)
            val completion = result.fold(
                onSuccess = { turnResult ->
                    diagnosticLogger.event(
                        category = "session",
                        event = "turn_model_success",
                        sessionId = handle.sessionId,
                        turnId = turnId,
                        details = mapOf(
                            "reply_chars" to turnResult.assistantText.length,
                            "duration_millis" to thoughtDurationMillis,
                        ),
                    )
                    appendAgentMessage(
                        sessionId = handle.sessionId,
                        blocks = ensureAssistantResponseFinalText(
                            blocks = currentAssistantResponseBlocks(handle.sessionId),
                            finalText = turnResult.assistantText,
                        ) { handle.nextPendingBlockId("agent-text") },
                        thoughtDurationMillis = thoughtDurationMillis,
                        outcome = SessionTurnOutcome.Success,
                        tokenUsage = turnResult.tokenUsage ?: estimatedTokenUsage,
                        tokenUsageSource = if (turnResult.tokenUsage != null) "api" else "estimated",
                        turnStartedAtMillis = turnStartedAtMillis,
                        firstTokenAtMillis = firstAssistantTokenAtMillis,
                        turnCompletedAtMillis = turnCompletedAtMillis,
                        inputMessageCount = request.requestMessages.size,
                        userMessageCount = request.requestMessages.count { it.author == MessageAuthor.User },
                    )
                },
                onFailure = { throwable ->
                    diagnosticLogger.exception(
                        category = "session",
                        event = "turn_model_failed",
                        sessionId = handle.sessionId,
                        turnId = turnId,
                        throwable = throwable,
                        details = mapOf("duration_millis" to thoughtDurationMillis),
                    )
                    appendAgentMessage(
                        sessionId = handle.sessionId,
                        blocks = appendAssistantResponseText(
                            blocks = currentAssistantResponseBlocks(handle.sessionId),
                            delta = buildString {
                                if (currentAssistantResponseBlocks(handle.sessionId).lastOrNull() is AssistantResponseBlock.Text) {
                                    append("\n\n")
                                }
                                append("Request failed: ${formatFailureMessage(throwable)}")
                            },
                        ) { handle.nextPendingBlockId("agent-text") },
                        thoughtDurationMillis = thoughtDurationMillis,
                        outcome = SessionTurnOutcome.Failure,
                        tokenUsage = estimatedTokenUsage,
                        tokenUsageSource = "estimated",
                        turnStartedAtMillis = turnStartedAtMillis,
                        firstTokenAtMillis = firstAssistantTokenAtMillis,
                        turnCompletedAtMillis = System.currentTimeMillis(),
                        inputMessageCount = request.requestMessages.size,
                        userMessageCount = request.requestMessages.count { it.author == MessageAuthor.User },
                    )
                },
            )
            chatStateStore.flush()
            _turnEvents.tryEmit(completion.toTurnEvent(handle.sessionId))
            diagnosticLogger.event(
                category = "session",
                event = "turn_end",
                sessionId = handle.sessionId,
                turnId = turnId,
                level = if (completion.outcome == SessionTurnOutcome.Failure) "warn" else "info",
                details = mapOf(
                    "outcome" to completion.outcome.name,
                    "tool_call_count" to completion.toolCallCount,
                    "distinct_tool_count" to completion.distinctToolCount,
                    "duration_millis" to completion.durationMillis,
                ),
            )
            completion
        } catch (_: CancellationException) {
            diagnosticLogger.event(
                category = "session",
                event = "turn_cancelled",
                level = "warn",
                sessionId = handle.sessionId,
                turnId = turnId,
                details = mapOf("pause_finalized" to handle.pauseFinalized),
            )
            clearPendingInputs(handle)
            val completion = if (handle.pauseFinalized) {
                CompletionSummary(
                    sessionTitle = resolveSessionTitle(handle.sessionId),
                    summary = "",
                    outcome = SessionTurnOutcome.Neutral,
                    toolCallCount = 0,
                    distinctToolCount = 0,
                    toolNames = emptyList(),
                    durationMillis = null,
                )
            } else {
                finalizePausedTurn(
                    handle = handle,
                    snapshot = _executionStates.value[handle.sessionId] ?: SessionExecutionState(sessionId = handle.sessionId),
                )
            }
            if (!handle.pauseFinalized) {
                chatStateStore.flush()
                handle.pauseFinalized = true
                _turnEvents.tryEmit(completion.toTurnEvent(handle.sessionId))
            }
            completion
        } finally {
            mcpClientManager.snapshots().forEach { snapshot ->
                runCatching { mcpClientManager.disconnect(snapshot.config.id) }
            }
            if (executionHandles[handle.sessionId] === handle) {
                updateExecutionState(handle.sessionId) { current ->
                    current.copy(
                        pendingToolInvocations = emptyList(),
                        pendingResponseBlocks = emptyList(),
                        pendingAssistantText = "",
                        activeTurnStartedAtMillis = null,
                    )
                }
            }
        }
    }

    private fun buildQueuedTurnRequest(
        sessionId: String,
        queuedInput: ChatMessage,
    ): SessionTurnRequest? = queuedTurnRequestBuilder.build(
        sessionId = sessionId,
        queuedInput = queuedInput,
        baseSettings = currentSettings.value,
        providerConfigs = currentProviderConfigs.value,
    )

    private fun updateSessionSelections(
        sessionId: String,
        selectedSkillIds: List<String>,
        activeSkills: List<ActiveSkillContext>,
        activeMcpServerIds: List<String>,
    ) {
        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted
            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            updatedSessions.add(
                sessionIndex.coerceAtMost(updatedSessions.size),
                session.copy(
                    selectedSkillIds = selectedSkillIds,
                    activeSkills = activeSkills,
                    activeMcpServerIds = activeMcpServerIds,
                ),
            )
            persisted.copy(sessions = updatedSessions)
        }
    }

    private suspend fun resolveSelectedActiveSkills(
        selectedSkillIds: List<String>,
        existingActiveSkills: List<ActiveSkillContext>,
    ): List<ActiveSkillContext> {
        if (selectedSkillIds.isEmpty()) return emptyList()
        val installedSkillsById = currentExtensionsState.value.installedSkills
            .filter { it.isEnabled }
            .associateBy { it.id }
        return buildList {
            selectedSkillIds.distinct().forEach { skillId ->
                val installedSkill = installedSkillsById[skillId] ?: return@forEach
                val activeSkill = skillManager.buildActiveSkillContext(installedSkill)
                    .getOrElse { return@forEach }
                add(activeSkill)
            }
        }
    }

    private fun upsertActiveSkillContext(
        activeSkills: List<ActiveSkillContext>,
        activeSkill: ActiveSkillContext,
    ): List<ActiveSkillContext> {
        val existingIndex = activeSkills.indexOfFirst { it.skillId == activeSkill.skillId }
        if (existingIndex < 0) return activeSkills + activeSkill
        return activeSkills.toMutableList().apply {
            set(existingIndex, activeSkill)
        }
    }

    private fun resolveSelectedMcpServers(
        selectedServerIds: List<String>,
    ): List<McpServerConfig> {
        if (selectedServerIds.isEmpty()) return emptyList()
        val serversById = currentExtensionsState.value.mcpServers
            .filter { it.isEnabled }
            .associateBy { it.id }
        return selectedServerIds.distinct().mapNotNull(serversById::get)
    }

    private fun appendAgentMessage(
        sessionId: String,
        blocks: List<AssistantResponseBlock>,
        thoughtDurationMillis: Long?,
        outcome: SessionTurnOutcome,
        tokenUsage: LlmTokenUsage? = null,
        tokenUsageSource: String = "unavailable",
        turnStartedAtMillis: Long? = null,
        firstTokenAtMillis: Long? = null,
        turnCompletedAtMillis: Long? = null,
        inputMessageCount: Int = 0,
        userMessageCount: Int = 0,
    ): CompletionSummary {
        var sessionTitle = resolveSessionTitle(sessionId)
        var replySummary = blocks.lastOrNull()
            ?.let(::assistantResponseBlockSummaryText)
            .orEmpty()

        val normalizedBlocks = normalizeAssistantResponseBlocks(blocks)
        val appendedMessages = assistantMessagesForBlocks(
            normalizedBlocks = normalizedBlocks,
            thoughtDurationMillis = thoughtDurationMillis,
            assistantActionsHidden = false,
            usageStatistics = buildChatUsageStatistics(
                tokenUsage = tokenUsage,
                tokenUsageSource = tokenUsageSource,
                turnStartedAtMillis = turnStartedAtMillis,
                firstTokenAtMillis = firstTokenAtMillis,
                turnCompletedAtMillis = turnCompletedAtMillis,
            ),
        )

        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            val updatedSession = session.withDerivedMessages(
                session.messages + appendedMessages
            )
            sessionTitle = updatedSession.title
            replySummary = updatedSession.preview
            updatedSessions.add(0, updatedSession)
            persisted.copy(sessions = updatedSessions)
        }

        val toolInvocations = normalizedBlocks.toolInvocations()
        val toolNames = toolInvocations.map { it.toolName }.distinct()
        return CompletionSummary(
            sessionTitle = sessionTitle,
            summary = replySummary,
            outcome = outcome,
            toolCallCount = toolInvocations.size,
            distinctToolCount = toolNames.size,
            toolNames = toolNames,
            durationMillis = thoughtDurationMillis,
            tokenUsage = tokenUsage,
            tokenUsageSource = tokenUsageSource,
            inputMessageCount = inputMessageCount,
            userMessageCount = userMessageCount,
        )
    }

    private suspend fun handleToolEvent(
        handle: SessionExecutionHandle,
        event: AgentToolEvent,
        reasoningTraceToolRoutingEnabled: Boolean,
    ) {
        val nowUptime = SystemClock.uptimeMillis()
        val nowMillis = System.currentTimeMillis()
        val existingInvocation = _executionStates.value[handle.sessionId]
            ?.pendingToolInvocations
            ?.firstOrNull { it.id == event.id }
        var invocation = ChatToolInvocation(
            id = event.id,
            toolName = event.name,
            argumentsJson = event.argumentsJson,
            outputJson = event.outputJson.orEmpty(),
            isRunning = event.isRunning ?: (event.outputJson == null),
            startedAtUptimeMillis = existingInvocation?.startedAtUptimeMillis ?: nowUptime,
            completedAtUptimeMillis = if (event.isRunning == true || event.outputJson == null) {
                null
            } else {
                existingInvocation?.completedAtUptimeMillis ?: nowUptime
            },
            startedAtMillis = existingInvocation?.startedAtMillis ?: nowMillis,
            completedAtMillis = if (event.isRunning == true || event.outputJson == null) {
                null
            } else {
                existingInvocation?.completedAtMillis ?: nowMillis
            },
            timelineOrder = existingInvocation?.timelineOrder ?: 0L,
        )
        if (event.outputJson == null) {
            flushActiveReasoningSummary(
                handle = handle,
            )
            handle.finishDirectReasoningSummaryChunk()
        }
        val currentBlocks = _executionStates.value[handle.sessionId]?.pendingResponseBlocks.orEmpty()
        val shouldRouteToolIntoReasoning =
            handle.activeReasoningBlockId != null ||
                reasoningTraceToolRoutingEnabled ||
                currentBlocks.any { it is AssistantResponseBlock.Reasoning }
        var reasoningBlockId = handle.activeReasoningBlockId
        if (reasoningBlockId == null && shouldRouteToolIntoReasoning) {
            reasoningBlockId = handle.nextPendingBlockId("pending-reasoning")
            handle.startReasoningBlock(reasoningBlockId, nowMillis)
        }
        if (shouldRouteToolIntoReasoning && invocation.timelineOrder <= 0L) {
            invocation = invocation.copy(timelineOrder = handle.nextReasoningTimelineOrder())
        }
        updateExecutionState(handle.sessionId) { current ->
            val targetReasoningBlockId = reasoningBlockId
            val pendingToolInvocations = upsertToolInvocation(
                current.pendingToolInvocations,
                invocation,
            )
            val blocksWithReasoningTrace = if (
                targetReasoningBlockId != null &&
                current.pendingResponseBlocks.none { it is AssistantResponseBlock.Reasoning && it.id == targetReasoningBlockId }
            ) {
                current.pendingResponseBlocks + AssistantResponseBlock.Reasoning(
                    id = targetReasoningBlockId,
                    trace = ReasoningTrace(
                        id = targetReasoningBlockId,
                        latestStatusText = formatReasoningToolStatus(invocation),
                        startedAtMillis = nowMillis,
                    ),
                )
            } else {
                current.pendingResponseBlocks
            }
            val pendingResponseBlocks = upsertAssistantResponseToolInvocation(
                blocks = blocksWithReasoningTrace,
                toolInvocation = invocation,
                reasoningBlockId = targetReasoningBlockId,
            ) { handle.nextPendingBlockId("pending-tools") }
            current.copy(
                pendingToolInvocations = pendingToolInvocations,
                pendingResponseBlocks = pendingResponseBlocks,
            )
        }
    }

    private fun assistantMessagesForBlocks(
        normalizedBlocks: List<AssistantResponseBlock>,
        thoughtDurationMillis: Long?,
        assistantActionsHidden: Boolean,
        usageStatistics: ChatUsageStatistics? = null,
    ): List<ChatMessage> {
        val messageTimestamp = System.currentTimeMillis()
        val responseGroupId = "agent-group-$messageTimestamp"
        return normalizedBlocks.mapIndexedNotNull { index, block ->
            when (block) {
                is AssistantResponseBlock.Text -> {
                    if (block.text.isBlank()) {
                        null
                    } else {
                        ChatMessage(
                            id = "agent-${messageTimestamp + index}",
                            author = MessageAuthor.Agent,
                            text = block.text,
                            createdAtMillis = messageTimestamp + index,
                            responseGroupId = responseGroupId,
                            assistantActionsHidden = assistantActionsHidden,
                        )
                    }
                }

                is AssistantResponseBlock.ToolGroup -> {
                    if (block.toolInvocations.isEmpty()) {
                        null
                    } else {
                        ChatMessage(
                            id = "agent-${messageTimestamp + index}",
                            author = MessageAuthor.Agent,
                            text = "",
                            createdAtMillis = messageTimestamp + index,
                            toolInvocations = block.toolInvocations,
                            responseGroupId = responseGroupId,
                            assistantActionsHidden = assistantActionsHidden,
                        )
                    }
                }

                is AssistantResponseBlock.Reasoning -> {
                    ChatMessage(
                        id = "agent-${messageTimestamp + index}",
                        author = MessageAuthor.Agent,
                        text = "",
                        createdAtMillis = messageTimestamp + index,
                        toolInvocations = block.trace.toolInvocations,
                        reasoningTrace = block.trace,
                        responseGroupId = responseGroupId,
                        assistantActionsHidden = assistantActionsHidden,
                    )
                }
            }
        }.let { messages ->
            if (messages.isEmpty()) {
                emptyList()
            } else {
                messages.toMutableList().apply {
                    if (none { it.reasoningTrace != null }) {
                        val lastIndex = lastIndex
                        set(
                            lastIndex,
                            get(lastIndex).copy(
                                thoughtDurationMillis = thoughtDurationMillis,
                                usageStatistics = usageStatistics,
                            ),
                        )
                    } else {
                        val lastIndex = lastIndex
                        set(lastIndex, get(lastIndex).copy(usageStatistics = usageStatistics))
                    }
                }
            }
        }
    }

    private fun buildChatUsageStatistics(
        tokenUsage: LlmTokenUsage?,
        tokenUsageSource: String,
        turnStartedAtMillis: Long?,
        firstTokenAtMillis: Long?,
        turnCompletedAtMillis: Long?,
    ): ChatUsageStatistics? {
        if (
            tokenUsage == null &&
            turnStartedAtMillis == null &&
            firstTokenAtMillis == null &&
            turnCompletedAtMillis == null
        ) {
            return null
        }
        return ChatUsageStatistics(
            inputTokens = tokenUsage?.inputTokens,
            outputTokens = tokenUsage?.outputTokens,
            totalTokens = tokenUsage?.withMissingTotalResolved()?.totalTokens,
            reasoningTokens = tokenUsage?.reasoningTokens,
            cachedInputTokens = tokenUsage?.cachedInputTokens,
            requestCount = tokenUsage?.requestCount ?: 1,
            tokenUsageSource = tokenUsageSource,
            startedAtMillis = turnStartedAtMillis ?: 0L,
            firstTokenAtMillis = firstTokenAtMillis,
            completedAtMillis = turnCompletedAtMillis ?: 0L,
        )
    }

    private fun currentAssistantResponseBlocks(sessionId: String): List<AssistantResponseBlock> =
        _executionStates.value[sessionId]?.pendingResponseBlocks.orEmpty()

    private fun finalizePausedTurn(
        handle: SessionExecutionHandle,
        snapshot: SessionExecutionState,
    ): CompletionSummary {
        val finalizedToolInvocations = finalizeInterruptedToolInvocations(snapshot.pendingToolInvocations)
        val finalizedResponseBlocks = finalizeInterruptedAssistantResponseBlocks(snapshot.pendingResponseBlocks)
        val thoughtDurationMillis = snapshot.activeTurnStartedAtMillis
            ?.let { startedAt -> (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) }
        val blocks = finalizedResponseBlocks.ifEmpty {
            buildList {
                if (snapshot.pendingAssistantText.isNotBlank()) {
                    add(
                        AssistantResponseBlock.Text(
                            id = handle.nextPendingBlockId("agent-text"),
                            text = snapshot.pendingAssistantText,
                        )
                    )
                }
                if (finalizedToolInvocations.isNotEmpty()) {
                    add(
                        AssistantResponseBlock.ToolGroup(
                            id = handle.nextPendingBlockId("agent-tools"),
                            toolInvocations = finalizedToolInvocations,
                        )
                    )
                }
            }
        }
        return if (blocks.isEmpty()) {
            CompletionSummary(
                sessionTitle = resolveSessionTitle(handle.sessionId),
                summary = "",
                outcome = SessionTurnOutcome.Neutral,
                toolCallCount = 0,
                distinctToolCount = 0,
                toolNames = emptyList(),
                durationMillis = thoughtDurationMillis,
            )
        } else {
            appendAgentMessage(
                sessionId = handle.sessionId,
                blocks = blocks,
                thoughtDurationMillis = thoughtDurationMillis,
                outcome = SessionTurnOutcome.Neutral,
            )
        }
    }

    private fun appendSteerInterruptionMessages(
        handle: SessionExecutionHandle,
        drained: List<PendingEnvelope>,
    ) {
        if (drained.isEmpty()) return
        val snapshot = _executionStates.value[handle.sessionId]
            ?: SessionExecutionState(sessionId = handle.sessionId)
        val pendingBlocks = snapshot.pendingResponseBlocks.ifEmpty {
            buildList {
                if (snapshot.pendingAssistantText.isNotBlank()) {
                    add(
                        AssistantResponseBlock.Text(
                            id = handle.nextPendingBlockId("agent-text"),
                            text = snapshot.pendingAssistantText,
                        )
                    )
                }
                if (snapshot.pendingToolInvocations.isNotEmpty()) {
                    add(
                        AssistantResponseBlock.ToolGroup(
                            id = handle.nextPendingBlockId("agent-tools"),
                            toolInvocations = snapshot.pendingToolInvocations,
                        )
                    )
                }
            }
        }
        val interruptedAssistantMessages = assistantMessagesForBlocks(
            normalizedBlocks = normalizeAssistantResponseBlocks(pendingBlocks),
            thoughtDurationMillis = null,
            assistantActionsHidden = true,
        )
        val userMessages = drained.map { it.message }
        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == handle.sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            updatedSessions.add(
                0,
                session.withDerivedMessages(
                    syncActiveBranches(session.messages + interruptedAssistantMessages + userMessages)
                ),
            )
            persisted.copy(sessions = updatedSessions)
        }
        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                pendingStatusDetail = "",
            )
        }
    }

    private fun drainSteerInputs(
        handle: SessionExecutionHandle,
    ): List<PendingEnvelope> {
        val drained = synchronized(handle.lock) {
            buildList {
                while (handle.steerInputs.isNotEmpty()) {
                    add(handle.steerInputs.removeFirst())
                }
            }
        }
        if (drained.isEmpty()) return emptyList()

        val drainedIds = drained.map { it.id }.toSet()
        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.filterNot { drainedIds.contains(it.id) }
            )
        }
        return drained
    }

    private fun promoteRemainingSteersToQueue(
        handle: SessionExecutionHandle,
    ) {
        val movedIds = synchronized(handle.lock) {
            if (handle.steerInputs.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    while (handle.steerInputs.isNotEmpty()) {
                        val entry = handle.steerInputs.removeFirst()
                        handle.queuedInputs.addFirst(entry.copy(mode = SessionFollowUpMode.Queue))
                        add(entry.id)
                    }
                }
            }
        }
        if (movedIds.isEmpty()) return

        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.map { pending ->
                    if (movedIds.contains(pending.id)) {
                        pending.copy(mode = SessionFollowUpMode.Queue)
                    } else {
                        pending
                    }
                }
            )
        }
    }

    private fun pollNextQueuedInput(
        handle: SessionExecutionHandle,
    ): PendingEnvelope? {
        val next = synchronized(handle.lock) {
            if (handle.queuedInputs.isEmpty()) {
                null
            } else {
                handle.queuedInputs.removeFirst()
            }
        } ?: return null

        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.filterNot { it.id == next.id }
            )
        }
        return next
    }

    private fun clearPendingInputs(
        handle: SessionExecutionHandle,
    ) {
        synchronized(handle.lock) {
            handle.queuedInputs.clear()
            handle.steerInputs.clear()
        }
        if (executionHandles[handle.sessionId] !== handle) return
        updateExecutionState(handle.sessionId) { current ->
            current.copy(pendingInputs = emptyList())
        }
    }

    private fun updateExecutionState(
        sessionId: String,
        transform: (SessionExecutionState) -> SessionExecutionState,
    ) {
        _executionStates.update { states ->
            states.toMutableMap().apply {
                val current = get(sessionId) ?: SessionExecutionState(sessionId = sessionId)
                put(sessionId, transform(current))
            }
        }
        if (
            currentSettings.value.keepTasksRunningInBackground &&
            _executionStates.value.values.any { it.isRunning }
        ) {
            ensureForegroundServiceRunning()
        }
    }

    private fun ensureForegroundServiceRunning() {
        try {
            AetherForegroundService.ensureRunning(application)
        } catch (throwable: Throwable) {
            diagnosticLogger.exception(
                category = "session",
                event = "foreground_service_start_failed",
                throwable = throwable,
            )
        }
    }

    private fun validateSettings(settings: AppSettings): String? = when {
        settings.provider == LlmProvider.VertexExpress && settings.apiKey.isBlank() ->
            "API Key is required before sending with Vertex AI (Express Mode)."

        settings.baseUrl.isBlank() || settings.modelId.isBlank() ->
            "Base URL and Model ID are required before sending."

        else -> null
    }

    private fun validateRequest(request: SessionTurnRequest): String? = when {
        request.agentModeEnabled && !request.settings.agentModeAuthorizationEnabled ->
                "Agent Mode is selected, but authorization is disabled. Enable it in Settings > Agent Mode first."

        else -> validateSettings(request.settings)
    }

    private fun resolveSessionTitle(sessionId: String): String =
        chatStateStore.state.value.sessions.firstOrNull { it.id == sessionId }?.title.orEmpty()

    private fun buildRequestMessages(messages: List<ChatMessage>): List<LlmMessage> =
        messages.afterLatestCompactedContext()
            .filter { it.displayKind != MessageDisplayKind.CompactStatus }
            .map(::buildRequestMessage)

    private fun List<ChatMessage>.afterLatestCompactedContext(): List<ChatMessage> {
        val compactContextIndex = indexOfLast { it.displayKind == MessageDisplayKind.HiddenContext }
        return if (compactContextIndex >= 0) {
            drop(compactContextIndex)
        } else {
            this
        }
    }

    private fun buildRequestMessage(message: ChatMessage): LlmMessage {
        val parts = mutableListOf<LlmContentPart>()
        if (message.text.isNotBlank()) {
            parts += LlmTextPart(message.text)
        }
        message.attachments.forEach { attachment ->
            parts += buildWorkspaceAttachmentParts(attachment)
        }
        if (parts.isEmpty()) {
            parts += LlmTextPart("[Empty message]")
        }
        return LlmMessage(
            role = if (message.author == MessageAuthor.User) "user" else "assistant",
            contentParts = parts,
            providerPayload = parseJsonObject(message.providerPayloadJson),
        )
    }

    private fun buildSteerRequestMessage(message: ChatMessage): LlmMessage {
        val steerText = buildString {
            append(
                "The user sent this while you were already working. Treat it as supplemental context for the current task. " +
                    "Continue the ongoing work, do not restart just to acknowledge it, and only change course if the new note requires it."
            )
            if (message.text.isNotBlank()) {
                append("\n\nSupplemental user note:\n")
                append(message.text)
            } else if (message.attachments.isNotEmpty()) {
                append("\n\nThe user also attached additional files for the current task.")
            }
        }
        return buildRequestMessage(message.copy(text = steerText))
    }

    private fun buildWorkspaceAttachmentParts(
        attachment: ChatAttachment,
    ): List<LlmContentPart> {
        if (attachment.workspacePath.isBlank()) {
            return listOf(LlmTextPart(
                "Attached file '${attachment.name}' is missing a workspace path. Ask the user to re-upload it if you need to inspect the file."
            ))
        }

        val accessHint = if (attachment.kind == AttachmentKind.Image) {
            "This image was copied into the workspace and is also inserted into this model request when local bytes are available. Use analyze_image on this path for a focused second pass if needed."
        } else {
            "Inspect this file through read, grep, find, ls, or bash inside the workspace instead of assuming its contents."
        }

        val metadataPart = LlmTextPart(
            buildString {
                append("Workspace attachment:\n")
                append("Name: ${attachment.name}\n")
                append("Type: ${attachment.mimeType.ifBlank { "unknown" }}\n")
                attachment.sizeBytes?.let { append("Size: ${formatBytes(it)}\n") }
                append("Path: ${attachment.workspacePath}\n")
                append("This file was uploaded in the current session.\n")
                append(accessHint)
            }
        )
        val imagePart = attachment.takeIf {
            it.kind == AttachmentKind.Image &&
                it.mimeType.startsWith("image/") &&
                it.inlineBase64.isNotBlank()
        }?.let {
            LlmImagePart(
                mimeType = it.mimeType,
                base64Data = it.inlineBase64,
            )
        }
        return listOfNotNull(metadataPart, imagePart)
    }

    private fun buildProviderUserMessage(
        settings: AppSettings,
        text: String,
    ): JSONObject = when (settings.provider) {
        LlmProvider.OpenAiResponses -> JSONObject().apply {
            put("role", "user")
            put(
                "content",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "input_text")
                        put("text", text)
                    }
                )
            )
        }

        LlmProvider.OpenAiCompatible -> JSONObject().apply {
            put("role", "user")
            put("content", text)
        }

        LlmProvider.VertexExpress -> JSONObject().apply {
            put("role", "user")
            put(
                "parts",
                JSONArray().put(
                    JSONObject().apply {
                        put("text", text)
                    }
                )
            )
        }

        LlmProvider.AnthropicMessages -> JSONObject().apply {
            put("role", "user")
            put(
                "content",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "text")
                        put("text", text)
                    }
                )
            )
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun formatReasoningDurationLabel(trace: ReasoningTrace): String {
        val startedAt = trace.startedAtMillis.takeIf { it > 0L } ?: return "0s"
        val endedAt = trace.completedAtMillis ?: System.currentTimeMillis()
        val totalSeconds = ((endedAt - startedAt).coerceAtLeast(0L) + 500L) / 1000L
        return "${totalSeconds.coerceAtLeast(0L)}s"
    }

    private fun formatFailureMessage(throwable: Throwable): String {
        val message = throwable.message?.trim().orEmpty()
        if (message.isNotBlank()) return message
        return throwable.javaClass.simpleName.ifBlank { "Unknown error" }
    }

    private fun AppSettings.supportsVisibleReasoningTrace(): Boolean {
        if (provider != LlmProvider.OpenAiCompatible) return false
        return baseUrl.contains("deepseek", ignoreCase = true) ||
            baseUrl.contains("openrouter", ignoreCase = true) ||
            modelId.contains("deepseek", ignoreCase = true) ||
            modelId.contains("openrouter", ignoreCase = true)
    }

    private fun formatReasoningToolStatus(invocation: ChatToolInvocation): String {
        val arguments = parseJsonObject(invocation.argumentsJson)
        return when (invocation.toolName.lowercase()) {
            "fetch_web_url" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = "Fetching",
                completedVerb = "Fetched",
                subject = arguments?.optString("url").orEmpty(),
                fallback = "web page",
            )

            "tavily_search" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = "Searching",
                completedVerb = "Searched",
                subject = arguments?.optString("query").orEmpty(),
                fallback = "the web",
            )

            "bash" -> if (invocation.isRunning) "Executing bash command" else "Executed bash command"
            "fetch_bash_output" -> if (invocation.isRunning) "Fetching bash output" else "Fetched bash output"
            "kill_bash" -> if (invocation.isRunning) "Stopping bash command" else "Stopped bash command"
            "sleep" -> if (invocation.isRunning) "Waiting" else "Waited"
            "read" -> if (invocation.isRunning) "Reading file" else "Read file"
            "edit" -> if (invocation.isRunning) "Editing file" else "Edited file"
            "write" -> if (invocation.isRunning) "Writing file" else "Wrote file"
            "grep" -> if (invocation.isRunning) "Searching files" else "Searched files"
            "find" -> if (invocation.isRunning) "Finding files" else "Found files"
            "ls" -> if (invocation.isRunning) "Listing files" else "Listed files"
            "analyze_image" -> if (invocation.isRunning) "Analyzing image" else "Analyzed image"
            "aether_config_get" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = "Reading",
                completedVerb = "Read",
                subject = formatAetherReasoningCategories(arguments),
                fallback = "Aether settings",
            )

            "aether_config_set" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = "Updating",
                completedVerb = "Updated",
                subject = arguments?.optString("category").orEmpty(),
                fallback = "Aether settings",
            )

            "aether_skill_manage" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = aetherSkillReasoningVerb(arguments, running = true),
                completedVerb = aetherSkillReasoningVerb(arguments, running = false),
                subject = arguments?.optString("skill_id").orEmpty()
                    .ifBlank { arguments?.optString("skillId").orEmpty() }
                    .ifBlank { arguments?.optString("url").orEmpty() },
                fallback = "Agent Skills",
            )

            "aether_mcp_manage" -> formatReasoningToolAction(
                isRunning = invocation.isRunning,
                runningVerb = aetherMcpReasoningVerb(arguments, running = true),
                completedVerb = aetherMcpReasoningVerb(arguments, running = false),
                subject = arguments?.optString("server_id").orEmpty()
                    .ifBlank { arguments?.optString("serverId").orEmpty() }
                    .ifBlank { arguments?.optString("display_name").orEmpty() }
                    .ifBlank { arguments?.optString("displayName").orEmpty() },
                fallback = "MCP servers",
            )

            "aether_termux_manage" -> when (arguments?.optString("action").orEmpty().lowercase()) {
                "configure_root_access" -> if (invocation.isRunning) "Configuring Termux root access" else "Configured Termux root access"
                "inspect_root_setup" -> if (invocation.isRunning) "Checking Root setup" else "Checked Root setup"
                else -> if (invocation.isRunning) "Checking Termux setup" else "Checked Termux setup"
            }

            "aether_agent_mode_manage" -> when (arguments?.optString("action").orEmpty().lowercase()) {
                "set_authorization" -> if (invocation.isRunning) "Updating Agent Mode authorization" else "Updated Agent Mode authorization"
                "request_shizuku_permission" -> if (invocation.isRunning) "Requesting Shizuku permission" else "Requested Shizuku permission"
                "stop_display" -> if (invocation.isRunning) "Stopping Agent Mode display" else "Stopped Agent Mode display"
                "refresh_displays" -> if (invocation.isRunning) "Refreshing Agent Mode displays" else "Refreshed Agent Mode displays"
                else -> if (invocation.isRunning) "Checking Agent Mode authorization" else "Checked Agent Mode authorization"
            }

            "aether_developer_manage" -> if (invocation.isRunning) "Reading Aether diagnostics" else "Read Aether diagnostics"
            else -> if (invocation.isRunning) {
                "Using ${invocation.toolName}"
            } else {
                "Used ${invocation.toolName}"
            }
        }
    }

    private fun formatAetherReasoningCategories(arguments: JSONObject?): String {
        val categories = arguments?.optJSONArray("categories") ?: return ""
        return buildList {
            for (index in 0 until categories.length()) {
                val value = categories.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.joinToString(", ")
    }

    private fun aetherSkillReasoningVerb(
        arguments: JSONObject?,
        running: Boolean,
    ): String = when (arguments?.optString("action").orEmpty().lowercase()) {
        "install_remote" -> if (running) "Installing" else "Installed"
        "remove" -> if (running) "Removing" else "Removed"
        "set_enabled" -> if (running) "Updating" else "Updated"
        else -> if (running) "Reading" else "Read"
    }

    private fun aetherMcpReasoningVerb(
        arguments: JSONObject?,
        running: Boolean,
    ): String = when (arguments?.optString("action").orEmpty().lowercase()) {
        "upsert_streamable_http", "upsert_stdio" -> if (running) "Saving" else "Saved"
        "remove" -> if (running) "Removing" else "Removed"
        "set_enabled" -> if (running) "Updating" else "Updated"
        else -> if (running) "Reading" else "Read"
    }

    private fun formatReasoningToolAction(
        isRunning: Boolean,
        runningVerb: String,
        completedVerb: String,
        subject: String,
        fallback: String,
    ): String {
        val action = if (isRunning) runningVerb else completedVerb
        val normalizedSubject = subject.trim().take(96)
        return if (normalizedSubject.isBlank()) {
            "$action $fallback"
        } else {
            "$action $normalizedSubject"
        }
    }

    private fun upsertToolInvocation(
        invocations: List<ChatToolInvocation>,
        toolInvocation: ChatToolInvocation,
    ): List<ChatToolInvocation> {
        val nowUptime = SystemClock.uptimeMillis()
        val nowMillis = System.currentTimeMillis()
        val existingIndex = invocations.indexOfFirst { it.id == toolInvocation.id }
        val normalized = if (existingIndex < 0) {
            toolInvocation.copy(
                startedAtUptimeMillis = toolInvocation.startedAtUptimeMillis.takeIf { it > 0L } ?: nowUptime,
                completedAtUptimeMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtUptimeMillis ?: nowUptime
                },
                startedAtMillis = toolInvocation.startedAtMillis.takeIf { it > 0L } ?: nowMillis,
                completedAtMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtMillis ?: nowMillis
                },
            )
        } else {
            val existing = invocations[existingIndex]
            toolInvocation.copy(
                startedAtUptimeMillis = existing.startedAtUptimeMillis
                    .takeIf { it > 0L }
                    ?: toolInvocation.startedAtUptimeMillis.takeIf { it > 0L }
                    ?: nowUptime,
                completedAtUptimeMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtUptimeMillis
                        ?: existing.completedAtUptimeMillis
                        ?: nowUptime
                },
                startedAtMillis = existing.startedAtMillis
                    .takeIf { it > 0L }
                    ?: toolInvocation.startedAtMillis.takeIf { it > 0L }
                    ?: nowMillis,
                completedAtMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtMillis
                        ?: existing.completedAtMillis
                        ?: nowMillis
                },
                timelineOrder = existing.timelineOrder
                    .takeIf { it > 0L }
                    ?: toolInvocation.timelineOrder,
            )
        }
        return if (existingIndex < 0) {
            invocations + normalized
        } else {
            invocations.toMutableList().apply { set(existingIndex, normalized) }
        }
    }

    private fun appendAssistantResponseText(
        blocks: List<AssistantResponseBlock>,
        delta: String,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        if (delta.isEmpty()) return blocks
        val lastBlock = blocks.lastOrNull()
        return if (lastBlock is AssistantResponseBlock.Text) {
            blocks.toMutableList().apply {
                set(lastIndex, lastBlock.copy(text = lastBlock.text + delta))
            }
        } else {
            blocks + AssistantResponseBlock.Text(
                id = newBlockId(),
                text = delta,
            )
        }
    }

    private fun appendReasoningDelta(
        handle: SessionExecutionHandle,
        delta: String,
    ) {
        val now = System.currentTimeMillis()
        var activeBlockId = handle.activeReasoningBlockId
        updateExecutionState(handle.sessionId) { current ->
            val blocks = current.pendingResponseBlocks.toMutableList()
            val activeIndex = activeBlockId?.let { id ->
                blocks.indexOfFirst { it is AssistantResponseBlock.Reasoning && it.id == id }
            } ?: -1
            if (activeIndex >= 0) {
                val block = blocks[activeIndex] as AssistantResponseBlock.Reasoning
                blocks[activeIndex] = block.copy(
                    trace = block.trace.copy(rawText = block.trace.rawText + delta),
                )
            } else {
                val blockId = handle.nextPendingBlockId("pending-reasoning")
                activeBlockId = blockId
                handle.startReasoningBlock(blockId, now)
                blocks += AssistantResponseBlock.Reasoning(
                    id = blockId,
                    trace = ReasoningTrace(
                        id = blockId,
                        rawText = delta,
                        startedAtMillis = now,
                    ),
                )
            }
            current.copy(
                pendingStatusText = "",
                pendingStatusDetail = "",
                pendingResponseBlocks = blocks,
            )
        }

        val blockId = activeBlockId ?: return
        val trace = currentReasoningTrace(handle.sessionId, blockId) ?: return
        maybeSubmitReasoningSummary(
            handle = handle,
            trace = trace,
            forceRemaining = false,
        )
    }

    private fun appendDirectReasoningSummaryDelta(
        handle: SessionExecutionHandle,
        delta: String,
    ) {
        val now = System.currentTimeMillis()
        var activeBlockId = handle.activeReasoningBlockId
        updateExecutionState(handle.sessionId) { current ->
            val blocks = current.pendingResponseBlocks.toMutableList()
            val activeIndex = activeBlockId?.let { id ->
                blocks.indexOfFirst { it is AssistantResponseBlock.Reasoning && it.id == id }
            } ?: -1
            val blockIndex = if (activeIndex >= 0) {
                activeIndex
            } else {
                val blockId = handle.nextPendingBlockId("pending-reasoning")
                activeBlockId = blockId
                handle.startReasoningBlock(blockId, now)
                blocks += AssistantResponseBlock.Reasoning(
                    id = blockId,
                    trace = ReasoningTrace(
                        id = blockId,
                        startedAtMillis = now,
                    ),
                )
                blocks.lastIndex
            }

            val block = blocks[blockIndex] as AssistantResponseBlock.Reasoning
            val chunkId = handle.activeDirectReasoningSummaryChunkId
                ?.takeIf { id -> block.trace.chunks.any { it.id == id } }
                ?: handle.nextReasoningChunkId(block.id).also { newChunkId ->
                    handle.activeDirectReasoningSummaryChunkId = newChunkId
                }
            val existingChunk = block.trace.chunks.firstOrNull { it.id == chunkId }
            val updatedDetail = existingChunk?.detail.orEmpty() + delta
            val updatedChunks = if (existingChunk == null) {
                block.trace.chunks + ReasoningSummaryChunk(
                    id = chunkId,
                    title = "Reasoning",
                    detail = updatedDetail,
                    isPending = false,
                    createdAtMillis = now,
                    timelineOrder = handle.nextReasoningTimelineOrder(),
                )
            } else {
                block.trace.chunks.map { chunk ->
                    if (chunk.id == chunkId) {
                        chunk.copy(
                            detail = updatedDetail,
                            isPending = false,
                        )
                    } else {
                        chunk
                    }
                }
            }
            blocks[blockIndex] = block.copy(
                trace = block.trace.copy(
                    chunks = updatedChunks,
                    latestStatusText = updatedDetail,
                )
            )
            current.copy(
                pendingStatusText = "",
                pendingStatusDetail = "",
                pendingResponseBlocks = blocks,
            )
        }
    }

    private fun completeActiveReasoning(
        handle: SessionExecutionHandle,
        trigger: ReasoningCompletionTrigger,
    ) {
        val blockId = handle.activeReasoningBlockId ?: return
        val now = System.currentTimeMillis()
        val trace = currentReasoningTrace(handle.sessionId, blockId) ?: run {
            handle.finishReasoningBlock()
            return
        }
        if (trace.rawText.isNotBlank()) {
            maybeSubmitReasoningSummary(
                handle = handle,
                trace = trace,
                forceRemaining = true,
            )
        }
        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingResponseBlocks = current.pendingResponseBlocks.map { block ->
                    if (block is AssistantResponseBlock.Reasoning && block.id == blockId) {
                        block.copy(
                            trace = block.trace.copy(
                                completedAtMillis = block.trace.completedAtMillis ?: now,
                            )
                        )
                    } else {
                        block
                    }
                }
            )
        }
        handle.finishReasoningBlock()
    }

    private fun flushActiveReasoningSummary(
        handle: SessionExecutionHandle,
    ) {
        val blockId = handle.activeReasoningBlockId ?: return
        val trace = currentReasoningTrace(handle.sessionId, blockId) ?: return
        if (trace.rawText.isBlank()) return
        maybeSubmitReasoningSummary(
            handle = handle,
            trace = trace,
            forceRemaining = true,
        )
    }

    private fun maybeSubmitReasoningSummary(
        handle: SessionExecutionHandle,
        trace: ReasoningTrace,
        forceRemaining: Boolean,
    ) {
        val rawText = trace.rawText
        if (rawText.isBlank()) return

        if (!handle.reasoningFirstSummarySubmitted) {
            val tokenCount = approximateReasoningTokenCount(rawText)
            if (tokenCount >= ReasoningInitialSummaryTokenThreshold || forceRemaining) {
                val chunkText = if (tokenCount >= ReasoningInitialSummaryTokenThreshold) {
                    takeApproximateReasoningTokens(rawText, ReasoningInitialSummaryTokenThreshold)
                } else {
                    rawText
                }
                handle.reasoningFirstSummarySubmitted = true
                handle.reasoningLastSubmittedCharIndex = chunkText.length.coerceAtMost(rawText.length)
                handle.reasoningLastTimedSummaryAtMillis = System.currentTimeMillis()
                submitReasoningSummary(
                    handle = handle,
                    blockId = trace.id,
                    rawText = chunkText,
                )
            }
            return
        }

        val startIndex = handle.reasoningLastSubmittedCharIndex.coerceIn(0, rawText.length)
        if (startIndex >= rawText.length) return
        val now = System.currentTimeMillis()
        val elapsedMillis = now - handle.reasoningLastTimedSummaryAtMillis
        if (!forceRemaining && elapsedMillis < ReasoningTimedSummaryIntervalMillis) return

        val chunkText = rawText.substring(startIndex)
        if (chunkText.isBlank()) return
        handle.reasoningLastSubmittedCharIndex = rawText.length
        handle.reasoningLastTimedSummaryAtMillis = now
        submitReasoningSummary(
            handle = handle,
            blockId = trace.id,
            rawText = chunkText,
        )
    }

    private fun submitReasoningSummary(
        handle: SessionExecutionHandle,
        blockId: String,
        rawText: String,
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return
        val chunkId = handle.nextReasoningChunkId(blockId)
        val createdAtMillis = System.currentTimeMillis()
        val timelineOrder = handle.nextReasoningTimelineOrder()
        updateReasoningTrace(handle.sessionId, blockId) { trace ->
            trace.copy(
                chunks = trace.chunks + ReasoningSummaryChunk(
                    id = chunkId,
                    rawText = trimmed,
                    isPending = true,
                    createdAtMillis = createdAtMillis,
                    timelineOrder = timelineOrder,
                )
            )
        }

        scope.launch(Dispatchers.IO) {
            val summary = summarizeReasoningChunk(trimmed) ?: fallbackReasoningSummary(trimmed)
            updateReasoningTrace(handle.sessionId, blockId) { trace ->
                val latestStatusText = summary.detail.ifBlank { summary.title }
                trace.copy(
                    chunks = trace.chunks.map { chunk ->
                        if (chunk.id != chunkId) {
                            chunk
                        } else {
                            chunk.copy(
                                title = summary.title,
                                detail = summary.detail,
                                isPending = false,
                            )
                        }
                    },
                    latestStatusText = latestStatusText,
                )
            }
        }
    }

    private suspend fun summarizeReasoningChunk(
        rawText: String,
    ): ReasoningSummary? {
        val settings = currentSettings.value
        val providerConfigs = currentProviderConfigs.value
        val titleSettings = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = providerConfigs,
            preferredModelKey = resolveDefaultTitleModelKey(settings, providerConfigs),
            fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
        )
        if (!isProviderSetupValid(titleSettings.provider, titleSettings.apiKey, titleSettings.baseUrl, titleSettings.modelId)) {
            return null
        }
        val prompt = buildString {
            appendLine("Summarize this assistant reasoning excerpt for a user-visible thinking timeline.")
            appendLine("Return exactly two short paragraphs: first a concise title, then one detail paragraph.")
            appendLine("Title style: a short gerund or noun phrase about the purpose or outcome, without 'I', 'The assistant', or a tool-action headline.")
            appendLine("Detail style: natural first-person planning language. 'I need to...', 'I should...', 'I will...', and 'I am...' are all acceptable when they fit.")
            appendLine("Never write from a third-person assistant perspective such as 'The assistant is...' or 'The model is...'.")
            appendLine("Do not mention that this is a summary, do not add bullets, and do not invent context.")
            appendLine()
            appendLine("Use this style:")
            appendLine("Providing accurate and properly cited documentation")
            appendLine()
            appendLine("I need to make sure I include citations for all factual information, especially from official docs, since I haven't performed any live API tests. It's essential to clarify that my info is based on public documentation and mention the safety of returning raw reasoning in OpenRouter. I should avoid long CoT examples.")
            appendLine()
            appendLine("Reasoning excerpt:")
            append(rawText.take(ReasoningSummaryMaxInputChars))
        }
        val result = OpenAiCompatibleClient().createChatCompletion(
            settings = titleSettings,
            systemPrompt = ReasoningSummarySystemPrompt,
            conversation = listOf(buildProviderUserMessage(titleSettings, prompt)),
            disableReasoning = true,
        ).getOrNull()?.assistantText.orEmpty().trim()
        return parseReasoningSummary(result)
    }

    private fun fallbackReasoningSummary(rawText: String): ReasoningSummary {
        val compact = rawText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
        return ReasoningSummary(
            title = "Thinking through the next step",
            detail = compact
                .take(ReasoningSummaryDetailMaxChars)
                .ifBlank { "Preparing the next action." },
        )
    }

    private fun parseReasoningSummary(text: String): ReasoningSummary? {
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        if (lines.isEmpty()) return null
        val title = lines.first().trim('"').take(ReasoningSummaryTitleMaxChars)
        val detail = lines.drop(1)
            .joinToString(" ")
            .trim()
            .ifBlank { title }
            .take(ReasoningSummaryDetailMaxChars)
        return ReasoningSummary(title = title, detail = detail)
    }

    private fun currentReasoningTrace(
        sessionId: String,
        blockId: String,
    ): ReasoningTrace? = _executionStates.value[sessionId]
        ?.pendingResponseBlocks
        ?.firstOrNull { it is AssistantResponseBlock.Reasoning && it.id == blockId }
        ?.let { (it as AssistantResponseBlock.Reasoning).trace }

    private fun updateReasoningTrace(
        sessionId: String,
        blockId: String,
        transform: (ReasoningTrace) -> ReasoningTrace,
    ) {
        updateExecutionState(sessionId) { current ->
            current.copy(
                pendingResponseBlocks = current.pendingResponseBlocks.map { block ->
                    if (block is AssistantResponseBlock.Reasoning && block.id == blockId) {
                        block.copy(trace = transform(block.trace))
                    } else {
                        block
                    }
                }
            )
        }
        updatePersistedReasoningTrace(
            sessionId = sessionId,
            blockId = blockId,
            transform = transform,
        )
    }

    private fun updatePersistedReasoningTrace(
        sessionId: String,
        blockId: String,
        transform: (ReasoningTrace) -> ReasoningTrace,
    ) {
        val hasPersistedTrace = chatStateStore.state.value.sessions.any { session ->
            session.id == sessionId && session.messages.any { it.reasoningTrace?.id == blockId }
        }
        if (!hasPersistedTrace) return

        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val session = persisted.sessions[sessionIndex]
            var changed = false
            val updatedMessages = session.messages.map { message ->
                val trace = message.reasoningTrace
                if (trace == null || trace.id != blockId) {
                    message
                } else {
                    val updatedTrace = transform(trace)
                    if (updatedTrace == trace) {
                        message
                    } else {
                        changed = true
                        message.copy(
                            reasoningTrace = updatedTrace,
                            toolInvocations = updatedTrace.toolInvocations,
                        )
                    }
                }
            }
            if (!changed) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            updatedSessions[sessionIndex] = session.withDerivedMessages(updatedMessages)
            persisted.copy(sessions = updatedSessions)
        }
    }

    private fun upsertAssistantResponseToolInvocation(
        blocks: List<AssistantResponseBlock>,
        toolInvocation: ChatToolInvocation,
        reasoningBlockId: String?,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        val existingReasoningIndex = blocks.indexOfFirst { block ->
            block is AssistantResponseBlock.Reasoning &&
                block.trace.toolInvocations.any { it.id == toolInvocation.id }
        }
        if (existingReasoningIndex >= 0) {
            val reasoningBlock = blocks[existingReasoningIndex] as AssistantResponseBlock.Reasoning
            return blocks.toMutableList().apply {
                set(
                    existingReasoningIndex,
                    reasoningBlock.copy(
                        trace = reasoningBlock.trace.copy(
                            toolInvocations = upsertToolInvocation(
                                reasoningBlock.trace.toolInvocations,
                                toolInvocation,
                            ),
                            latestStatusText = formatReasoningToolStatus(toolInvocation),
                        ),
                    )
                )
            }
        }

        val reasoningIndex = reasoningBlockId?.let { id ->
            blocks.indexOfFirst { it is AssistantResponseBlock.Reasoning && it.id == id }
        } ?: -1
        if (reasoningIndex >= 0) {
            val reasoningBlock = blocks[reasoningIndex] as AssistantResponseBlock.Reasoning
            return blocks.toMutableList().apply {
                set(
                    reasoningIndex,
                    reasoningBlock.copy(
                        trace = reasoningBlock.trace.copy(
                            toolInvocations = upsertToolInvocation(
                                reasoningBlock.trace.toolInvocations,
                                toolInvocation,
                            ),
                            latestStatusText = formatReasoningToolStatus(toolInvocation),
                        ),
                    )
                )
            }
        }

        val existingIndex = blocks.indexOfFirst { block ->
            block is AssistantResponseBlock.ToolGroup &&
                block.toolInvocations.any { it.id == toolInvocation.id }
        }
        if (existingIndex >= 0) {
            val toolBlock = blocks[existingIndex] as AssistantResponseBlock.ToolGroup
            return blocks.toMutableList().apply {
                set(
                    existingIndex,
                    toolBlock.copy(
                        toolInvocations = upsertToolInvocation(toolBlock.toolInvocations, toolInvocation),
                    ),
                )
            }
        }

        val lastBlock = blocks.lastOrNull()
        return if (lastBlock is AssistantResponseBlock.ToolGroup) {
            blocks.toMutableList().apply {
                set(
                    lastIndex,
                    lastBlock.copy(
                        toolInvocations = upsertToolInvocation(lastBlock.toolInvocations, toolInvocation),
                    ),
                )
            }
        } else {
            blocks + AssistantResponseBlock.ToolGroup(
                id = newBlockId(),
                toolInvocations = upsertToolInvocation(emptyList(), toolInvocation),
            )
        }
    }

    private fun pendingTrailingAssistantText(
        blocks: List<AssistantResponseBlock>,
    ): String = (blocks.lastOrNull() as? AssistantResponseBlock.Text)?.text.orEmpty()

    private fun ensureAssistantResponseFinalText(
        blocks: List<AssistantResponseBlock>,
        finalText: String,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        if (finalText.isBlank()) return normalizeAssistantResponseBlocks(blocks)
        val normalized = normalizeAssistantResponseBlocks(blocks)
        val lastTextIndex = normalized.indexOfLast { it is AssistantResponseBlock.Text }
        if (lastTextIndex < 0) {
            return normalized + AssistantResponseBlock.Text(
                id = newBlockId(),
                text = finalText,
            )
        }
        val lastTextBlock = normalized[lastTextIndex] as AssistantResponseBlock.Text
        if (lastTextBlock.text == finalText) return normalized
        return normalized.toMutableList().apply {
            set(lastTextIndex, lastTextBlock.copy(text = finalText))
        }
    }

    private fun normalizeAssistantResponseBlocks(
        blocks: List<AssistantResponseBlock>,
    ): List<AssistantResponseBlock> = buildList {
        blocks.forEach { block ->
            when (block) {
                is AssistantResponseBlock.Text -> {
                    if (block.text.isBlank()) return@forEach
                    val previous = lastOrNull()
                    if (previous is AssistantResponseBlock.Text) {
                        removeAt(lastIndex)
                        add(previous.copy(text = previous.text + block.text))
                    } else {
                        add(block)
                    }
                }

                is AssistantResponseBlock.ToolGroup -> {
                    if (block.toolInvocations.isEmpty()) return@forEach
                    val previous = lastOrNull()
                    if (previous is AssistantResponseBlock.ToolGroup) {
                        removeAt(lastIndex)
                        add(
                            previous.copy(
                                toolInvocations = previous.toolInvocations + block.toolInvocations,
                            ),
                        )
                    } else {
                        add(block)
                    }
                }

                is AssistantResponseBlock.Reasoning -> {
                    if (
                        block.trace.rawText.isBlank() &&
                        block.trace.chunks.isEmpty() &&
                        block.trace.toolInvocations.isEmpty()
                    ) {
                        return@forEach
                    }
                    add(block)
                }
            }
        }
    }

    private fun List<AssistantResponseBlock>.toolInvocations(): List<ChatToolInvocation> =
        flatMap { block ->
            when (block) {
                is AssistantResponseBlock.ToolGroup -> block.toolInvocations
                is AssistantResponseBlock.Text -> emptyList()
                is AssistantResponseBlock.Reasoning -> block.trace.toolInvocations
            }
        }.distinctBy { it.id }

    private fun finalizeInterruptedAssistantResponseBlocks(
        blocks: List<AssistantResponseBlock>,
    ): List<AssistantResponseBlock> = normalizeAssistantResponseBlocks(
        blocks.map { block ->
            when (block) {
                is AssistantResponseBlock.Text -> block
                is AssistantResponseBlock.Reasoning -> block.copy(
                    trace = block.trace.copy(
                        toolInvocations = finalizeInterruptedToolInvocations(block.trace.toolInvocations),
                        completedAtMillis = block.trace.completedAtMillis ?: System.currentTimeMillis(),
                    )
                )
                is AssistantResponseBlock.ToolGroup -> block.copy(
                    toolInvocations = finalizeInterruptedToolInvocations(block.toolInvocations),
                )
            }
        }
    )

    private fun assistantResponseBlockSummaryText(
        block: AssistantResponseBlock,
    ): String = when (block) {
        is AssistantResponseBlock.Text -> block.text.trim()
        is AssistantResponseBlock.ToolGroup -> ChatMessage(
            id = block.id,
            author = MessageAuthor.Agent,
            text = "",
            toolInvocations = block.toolInvocations,
        ).summaryText()
        is AssistantResponseBlock.Reasoning -> block.trace.chunks.lastOrNull { chunk ->
            chunk.detail.isNotBlank() || chunk.title.isNotBlank()
        }?.let { chunk ->
            chunk.detail.ifBlank { chunk.title }
        } ?: "Thought for ${formatReasoningDurationLabel(block.trace)}"
    }

    private fun finalizeInterruptedToolInvocations(
        invocations: List<ChatToolInvocation>,
    ): List<ChatToolInvocation> = invocations.map { invocation ->
        if (!isInterruptedToolInvocation(invocation)) {
            invocation
        } else {
            invocation.copy(
                isRunning = false,
                outputJson = buildInterruptedToolOutput(invocation),
                completedAtUptimeMillis = invocation.completedAtUptimeMillis ?: SystemClock.uptimeMillis(),
                completedAtMillis = invocation.completedAtMillis ?: System.currentTimeMillis(),
            )
        }
    }

    private fun isInterruptedToolInvocation(invocation: ChatToolInvocation): Boolean {
        if (invocation.isRunning) return true
        if (invocation.toolName.lowercase() != "bash") return false
        val output = parseJsonObject(invocation.outputJson) ?: return false
        return output.optString("status") == "running" || output.optString("status") == "launching"
    }

    private fun buildInterruptedToolOutput(invocation: ChatToolInvocation): String {
        val output = parseJsonObject(invocation.outputJson) ?: JSONObject()
        output.put("ok", false)
        output.put("status", "cancelled")
        output.put("running", false)
        output.put("completed", true)
        if (!output.has("stdout")) output.put("stdout", "")
        if (!output.has("stderr")) output.put("stderr", "")
        if (!output.has("exit_code")) output.put("exit_code", 143)
        if (!output.has("err")) output.put("err", -1)
        output.put("errmsg", "Stopped by user.")
        return output.toString()
    }

    private fun extractActiveManagedRunIds(
        invocations: List<ChatToolInvocation>,
    ): List<String> = invocations.mapNotNull { invocation ->
        if (invocation.toolName.lowercase() != "bash") return@mapNotNull null
        val output = parseJsonObject(invocation.outputJson) ?: return@mapNotNull null
        val status = output.optString("status")
        if (status != "running" && status != "launching") {
            return@mapNotNull null
        }
        output.optString("run_id").trim().ifBlank { null }
    }.distinct()

    private fun parseJsonObject(rawValue: String): JSONObject? =
        if (rawValue.isBlank()) null else runCatching { JSONObject(rawValue) }.getOrNull()

    private fun approximateReasoningTokenCount(text: String): Int {
        var count = 0
        var inToken = false
        text.forEach { char ->
            when {
                char.isWhitespace() -> inToken = false
                isCjkReasoningChar(char) -> {
                    count += 1
                    inToken = false
                }
                !inToken -> {
                    count += 1
                    inToken = true
                }
            }
        }
        return count
    }

    private fun estimateRequestTokenUsage(request: SessionTurnRequest): LlmTokenUsage {
        val inputTokens = estimateChatMessagesTokens(request.requestMessages).toLong()
        return LlmTokenUsage(
            inputTokens = inputTokens,
            totalTokens = inputTokens,
            requestCount = 1,
        )
    }

    private fun estimateChatMessagesTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { message ->
            approximateReasoningTokenCount(message.text) +
                message.attachments.sumOf(::estimateAttachmentTokens)
        }

    private fun estimateAttachmentTokens(attachment: ChatAttachment): Int =
        approximateReasoningTokenCount(attachment.name) +
            when (attachment.kind) {
                AttachmentKind.Image -> 85
                AttachmentKind.File -> {
                    val sizeBytes = attachment.sizeBytes ?: 0L
                    if (sizeBytes > 0L) {
                        (sizeBytes / 4L).coerceAtMost(16_000L).toInt()
                    } else {
                        0
                    }
                }
            }

    private fun takeApproximateReasoningTokens(
        text: String,
        maxTokens: Int,
    ): String {
        if (maxTokens <= 0) return ""
        var count = 0
        var inToken = false
        for (index in text.indices) {
            val char = text[index]
            when {
                char.isWhitespace() -> inToken = false
                isCjkReasoningChar(char) -> {
                    count += 1
                    inToken = false
                }
                !inToken -> {
                    count += 1
                    inToken = true
                }
            }
            if (count >= maxTokens) {
                return text.substring(0, index + 1)
            }
        }
        return text
    }

    private fun isCjkReasoningChar(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private data class PendingEnvelope(
        val id: String,
        val mode: SessionFollowUpMode,
        val message: ChatMessage,
    ) {
        fun toUiState(): PendingSessionInput = PendingSessionInput(
            id = id,
            mode = mode,
            preview = message.summaryText().take(72),
            attachmentCount = message.attachments.size,
        )
    }

    private data class CompletionSummary(
        val sessionTitle: String,
        val summary: String,
        val outcome: SessionTurnOutcome,
        val toolCallCount: Int,
        val distinctToolCount: Int,
        val toolNames: List<String>,
        val durationMillis: Long?,
        val tokenUsage: LlmTokenUsage? = null,
        val tokenUsageSource: String = "unavailable",
        val inputMessageCount: Int = 0,
        val userMessageCount: Int = 0,
    ) {
        fun toTurnEvent(sessionId: String): SessionTurnEvent = SessionTurnEvent(
            sessionId = sessionId,
            outcome = outcome,
            toolCallCount = toolCallCount,
            distinctToolCount = distinctToolCount,
            toolNames = toolNames,
            durationMillis = durationMillis,
            tokenUsage = tokenUsage,
            tokenUsageSource = tokenUsageSource,
            inputMessageCount = inputMessageCount,
            userMessageCount = userMessageCount,
        )
    }

    private class SessionExecutionHandle(
        val sessionId: String,
    ) {
        val lock = Any()
        val queuedInputs = ArrayDeque<PendingEnvelope>()
        val steerInputs = ArrayDeque<PendingEnvelope>()
        private var pendingBlockCounter: Long = 0
        private var reasoningChunkCounter: Long = 0
        private var reasoningTimelineCounter: Long = 0
        var activeReasoningBlockId: String? = null
        var activeDirectReasoningSummaryChunkId: String? = null
        var reasoningFirstSummarySubmitted: Boolean = false
        var reasoningLastSubmittedCharIndex: Int = 0
        var reasoningLastTimedSummaryAtMillis: Long = 0L

        @Volatile
        var pauseRequested: Boolean = false

        @Volatile
        var pauseFinalized: Boolean = false

        @Volatile
        var job: Job? = null

        fun nextPendingBlockId(prefix: String): String {
            val nextId = pendingBlockCounter
            pendingBlockCounter += 1
            return "$prefix-$sessionId-$nextId"
        }

        fun nextReasoningChunkId(blockId: String): String {
            val nextId = reasoningChunkCounter
            reasoningChunkCounter += 1
            return "$blockId-summary-$nextId"
        }

        fun nextReasoningTimelineOrder(): Long {
            reasoningTimelineCounter += 1
            return reasoningTimelineCounter
        }

        fun startReasoningBlock(blockId: String, nowMillis: Long) {
            activeReasoningBlockId = blockId
            activeDirectReasoningSummaryChunkId = null
            reasoningFirstSummarySubmitted = false
            reasoningLastSubmittedCharIndex = 0
            reasoningLastTimedSummaryAtMillis = nowMillis
        }

        fun finishDirectReasoningSummaryChunk() {
            activeDirectReasoningSummaryChunkId = null
        }

        fun finishReasoningBlock() {
            activeReasoningBlockId = null
            activeDirectReasoningSummaryChunkId = null
            reasoningFirstSummarySubmitted = false
            reasoningLastSubmittedCharIndex = 0
            reasoningLastTimedSummaryAtMillis = 0L
        }
    }
}
