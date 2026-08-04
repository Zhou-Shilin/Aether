package com.zhousl.aether.ui

import com.zhousl.aether.data.ActiveSkillContext
import com.zhousl.aether.data.AgentModeAuthorizationState
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AppUpdateRelease
import com.zhousl.aether.data.ChatUsageStatisticsSnapshot
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.McpServerConfig
import com.zhousl.aether.data.InstalledPiExtension
import com.zhousl.aether.data.PiExtensionCatalogEntry
import com.zhousl.aether.data.PiPackageDetails
import com.zhousl.aether.data.RootSetupState
import com.zhousl.aether.data.ScheduledTask
import com.zhousl.aether.data.SessionExecutionState
import com.zhousl.aether.data.pi.PiCoreSetupState
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.runtime.AlpineSetupProgress
import com.zhousl.aether.runtime.LocalRuntimeSetupState
import com.zhousl.aether.termux.TermuxSetupState

internal const val DraftSessionId = "draft"

enum class AppScreen {
    Onboarding,
    Chat,
    Settings,
}

enum class OnboardingStep {
    Landing,
    ProviderSetup,
    TermuxSetup,
    LocalRuntimeChoice,
    AlpineSetup,
    AgentModeAuthorization,
    TavilySetup,
}

enum class RootSetupProgressReturnPage {
    Termux,
    AgentMode,
}

enum class MessageAuthor {
    User,
    Agent,
}

enum class MessageDisplayKind {
    Standard,
    HiddenContext,
    CompactStatus,
}

enum class AttachmentKind {
    Image,
    File;

    companion object {
        fun fromStored(
            value: String,
            mimeType: String,
        ): AttachmentKind = when {
            value == Image.name -> Image
            value == File.name -> File
            mimeType.startsWith("image/") -> Image
            else -> File
        }
    }
}

enum class AttachmentWorkspaceState {
    Pending,
    Ready,
    Failed,
}

data class ChatAttachment(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val kind: AttachmentKind,
    val workspacePath: String = "",
    val workspaceState: AttachmentWorkspaceState = AttachmentWorkspaceState.Ready,
    val workspaceError: String = "",
    val workspaceBytesCopied: Long = 0L,
    val workspaceBytesPerSecond: Long = 0L,
    val inlineBase64: String = "",
)

data class ChatToolInvocation(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
    val outputJson: String = "",
    val isRunning: Boolean = false,
    val startedAtUptimeMillis: Long = 0L,
    val completedAtUptimeMillis: Long? = null,
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    val timelineOrder: Long = 0L,
)

data class ChatUsageStatistics(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val requestCount: Int = 1,
    val tokenUsageSource: String = "unavailable",
    val startedAtMillis: Long = 0L,
    val firstTokenAtMillis: Long? = null,
    val completedAtMillis: Long = 0L,
) {
    val firstTokenLatencyMillis: Long?
        get() = firstTokenAtMillis?.let { firstToken ->
            if (startedAtMillis > 0L) (firstToken - startedAtMillis).coerceAtLeast(0L) else null
        }

    val outputTokensPerSecond: Double?
        get() {
            val output = outputTokens ?: return null
            val outputStartedAt = firstTokenAtMillis ?: startedAtMillis.takeIf { it > 0L } ?: return null
            if (completedAtMillis <= outputStartedAt) return null
            val seconds = (completedAtMillis - outputStartedAt) / 1000.0
            if (seconds <= 0.0) return null
            return output / seconds
        }
}

data class ReasoningSummaryChunk(
    val id: String,
    val title: String = "",
    val detail: String = "",
    val rawText: String = "",
    val isPending: Boolean = false,
    val createdAtMillis: Long = 0L,
    val timelineOrder: Long = 0L,
)

data class ReasoningTrace(
    val id: String,
    val rawText: String = "",
    val chunks: List<ReasoningSummaryChunk> = emptyList(),
    val toolInvocations: List<ChatToolInvocation> = emptyList(),
    val latestStatusText: String = "",
    val startedAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
) {
    val hasSummary: Boolean
        get() = chunks.any { it.title.isNotBlank() || it.detail.isNotBlank() }

    val hasTimelineContent: Boolean
        get() = chunks.isNotEmpty() || toolInvocations.isNotEmpty()
}

sealed interface AssistantResponseBlock {
    val id: String

    data class Text(
        override val id: String,
        val text: String,
    ) : AssistantResponseBlock

    data class ToolGroup(
        override val id: String,
        val toolInvocations: List<ChatToolInvocation>,
    ) : AssistantResponseBlock

    data class Reasoning(
        override val id: String,
        val trace: ReasoningTrace,
    ) : AssistantResponseBlock
}

data class ChatMessage(
    val id: String,
    val author: MessageAuthor,
    val text: String,
    val createdAtMillis: Long = 0L,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolInvocations: List<ChatToolInvocation> = emptyList(),
    val thoughtDurationMillis: Long? = null,
    val reasoningTrace: ReasoningTrace? = null,
    val branchGroup: ChatBranchGroup? = null,
    val responseGroupId: String? = null,
    val assistantActionsHidden: Boolean = false,
    val providerPayloadJson: String = "",
    val displayKind: MessageDisplayKind = MessageDisplayKind.Standard,
    val usageStatistics: ChatUsageStatistics? = null,
)

data class ChatSession(
    val id: String,
    val title: String,
    val preview: String,
    val hasCustomTitle: Boolean = false,
    val messages: List<ChatMessage>,
    val messageCount: Int = messages.size,
    val lastMessageAtMillis: Long? = messages.maxOfOrNull { it.createdAtMillis },
    val selectedSkillIds: List<String> = emptyList(),
    val activeSkills: List<ActiveSkillContext> = emptyList(),
    val activeMcpServerIds: List<String> = emptyList(),
    val agentModeEnabled: Boolean = false,
    val chromeEnabled: Boolean = false,
    val selectedModelKey: String = "",
)

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,
    val availableRelease: AppUpdateRelease? = null,
    val showAvailableDialog: Boolean = false,
    val pendingInstallUri: String = "",
)

data class AetherUiState(
    val currentScreen: AppScreen = AppScreen.Chat,
    val isStartupRouteResolved: Boolean = false,
    val isOnboardingReplay: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.Landing,
    val onboardingReturnScreen: AppScreen = AppScreen.Chat,
    val sessions: List<ChatSession> = emptyList(),
    val usageStatisticsSnapshots: List<ChatUsageStatisticsSnapshot> = emptyList(),
    val currentSessionId: String = DraftSessionId,
    val draftInput: String = "",
    val draftAttachments: List<ChatAttachment> = emptyList(),
    val draftSelectedModelKey: String = "",
    val draftSelectedSkillIds: List<String> = emptyList(),
    val draftSelectedMcpServerIds: List<String> = emptyList(),
    val draftAgentModeEnabled: Boolean = false,
    val draftChromeEnabled: Boolean = false,
    val draftWorkspaceId: String? = null,
    val editingSessionId: String? = null,
    val editingMessageId: String? = null,
    val settings: AppSettings = AppSettings(),
    val isSending: Boolean = false,
    val pendingResponseSessionId: String? = null,
    val pendingToolInvocations: List<ChatToolInvocation> = emptyList(),
    val pendingResponseBlocks: List<AssistantResponseBlock> = emptyList(),
    val pendingAssistantText: String = "",
    val pendingStatusText: String = "",
    val pendingStatusDetail: String = "",
    val compactingSessionId: String? = null,
    val sessionExecutionStates: Map<String, SessionExecutionState> = emptyMap(),
    val unviewedCompletedSessionIds: Set<String> = emptySet(),
    val termuxSetupState: TermuxSetupState = TermuxSetupState(),
    val alpineSetupState: LocalRuntimeSetupState = LocalRuntimeSetupState(
        runtimeId = com.zhousl.aether.data.LocalRuntimeId.Alpine,
    ),
    val alpinePackageInstallProgress: Map<String, AlpineSetupProgress> = emptyMap(),
    val developerTermuxReadyOverride: Boolean? = null,
    val rootSetupState: RootSetupState = RootSetupState(),
    val rootSetupProgressReturnPage: RootSetupProgressReturnPage? = null,
    val installedSkills: List<InstalledSkill> = emptyList(),
    val installedPiExtensions: List<InstalledPiExtension> = emptyList(),
    val piExtensionCatalog: List<PiExtensionCatalogEntry> = emptyList(),
    val isLoadingPiExtensions: Boolean = false,
    val piExtensionCatalogError: String = "",
    val piExtensionOperationSource: String = "",
    val selectedPiPackageDetails: PiPackageDetails? = null,
    val selectedPiPackageSource: String = "",
    val isLoadingPiPackageDetails: Boolean = false,
    val piPackageDetailsError: String = "",
    val mcpServers: List<McpServerConfig> = emptyList(),
    val scheduledTasks: List<ScheduledTask> = emptyList(),
    val providerConfigs: List<LlmProviderConfig> = emptyList(),
    val modelCatalogInfo: Map<String, com.zhousl.aether.data.ModelCatalogInfo> = emptyMap(),
    val thinkingLevelsByProviderModel: Map<String, List<String>> = emptyMap(),
    val thinkingLevelClampsByProviderModel: Map<String, Map<String, String>> = emptyMap(),
    val isFetchingModels: Boolean = false,
    val providerAuthState: PiProviderAuthState = PiProviderAuthState(),
    val piCoreSetupState: PiCoreSetupState = PiCoreSetupState(),
    val developerAlpineSetupPreviewState: PiCoreSetupState? = null,
    val showStarterPromptHint: Boolean = false,
    val awaitingFollowUpTour: Boolean = false,
    val showFollowUpTourCard: Boolean = false,
    val agentModeDisplayState: AgentModeDisplayState = AgentModeDisplayState(),
    val chromeDisplayState: AgentModeDisplayState = AgentModeDisplayState(),
    val agentModeAuthorizationState: AgentModeAuthorizationState = AgentModeAuthorizationState(),
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
)
