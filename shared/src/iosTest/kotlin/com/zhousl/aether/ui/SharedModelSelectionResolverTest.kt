package com.zhousl.aether.ui

import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.resolveAutomaticModelKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModelSelectionResolverTest {
    @Test
    fun adjacentReasoningBlocksWithoutCommentaryMergeTogether() {
        val firstTool = SharedChatToolInvocation("tool-1", "bash", "{}", isRunning = false)
        val secondTool = SharedChatToolInvocation("tool-2", "bash", "{}", isRunning = false)
        val blocks = listOf(
            SharedAssistantResponseBlock.Reasoning(
                "reasoning-1",
                SharedReasoningTrace("trace-1", rawText = "first", toolInvocations = listOf(firstTool), startedAtMillis = 10, completedAtMillis = 20),
            ),
            SharedAssistantResponseBlock.ToolGroup("tools", listOf(firstTool, secondTool)),
            SharedAssistantResponseBlock.Reasoning(
                "reasoning-2",
                SharedReasoningTrace("trace-2", rawText = "second", toolInvocations = listOf(secondTool), startedAtMillis = 21, completedAtMillis = 30),
            ),
        )

        val merged = mergeAdjacentSharedReasoningWorkBlocks(blocks)

        assertEquals(1, merged.size)
        val trace = (merged.single() as SharedAssistantResponseBlock.Reasoning).trace
        assertEquals("first\nsecond", trace.rawText)
        assertEquals(listOf("tool-1", "tool-2"), trace.toolInvocations.map { it.id })
        assertEquals(10L, trace.startedAtMillis)
        assertEquals(30L, trace.completedAtMillis)
    }

    @Test
    fun adjacentToolAndReasoningBlocksWithoutCommentaryMergeTogether() {
        val tool = SharedChatToolInvocation(
            id = "tool-1",
            name = "bash",
            summary = "Restarting Minecraft server",
            startedAtMillis = 10,
            completedAtMillis = 20,
            isRunning = false,
        )
        val reasoning = SharedAssistantResponseBlock.Reasoning(
            "reasoning-1",
            SharedReasoningTrace("trace-1", rawText = "Checking the server", startedAtMillis = 21),
        )

        val merged = mergeAdjacentSharedReasoningWorkBlocks(
            listOf(SharedAssistantResponseBlock.ToolGroup("tools", listOf(tool)), reasoning),
        )

        assertEquals(1, merged.size)
        val trace = (merged.single() as SharedAssistantResponseBlock.Reasoning).trace
        assertEquals("Checking the server", trace.rawText)
        assertEquals(listOf("tool-1"), trace.toolInvocations.map { it.id })
        assertEquals(10L, trace.startedAtMillis)
        assertEquals("Executed bash command", trace.latestStatusText)
    }

    @Test
    fun commentarySeparatesReasoningBlocks() {
        val first = SharedAssistantResponseBlock.Reasoning("one", SharedReasoningTrace("trace-1", rawText = "one"))
        val commentary = SharedAssistantResponseBlock.Text("text", "Updating the server now.")
        val second = SharedAssistantResponseBlock.Reasoning("two", SharedReasoningTrace("trace-2", rawText = "two"))

        assertEquals(listOf(first, commentary, second), mergeAdjacentSharedReasoningWorkBlocks(listOf(first, commentary, second)))
    }

    @Test
    fun auxiliaryModelUsesPurposeRankingAndHonorsValidStoredKey() {
        val options = listOf(
            modelConfig("provider-1", "gpt-5.6-sol"),
            modelConfig("provider-2", "gemini-3.1-flash-lite"),
        ).availableModelOptions()
        val chatKey = options.single { it.modelId == "gpt-5.6-sol" }.key
        val titleKey = options.single { it.modelId == "gemini-3.1-flash-lite" }.key

        assertEquals(
            titleKey,
            resolveSharedStoredOrAutomaticModelKey(
                storedKey = "missing-key",
                options = options,
                purpose = AutomaticModelPurpose.Title,
                fallbackPurpose = AutomaticModelPurpose.Chat,
            ),
        )
        assertEquals(
            chatKey,
            resolveSharedStoredOrAutomaticModelKey(
                storedKey = chatKey,
                options = options,
                purpose = AutomaticModelPurpose.Title,
                fallbackPurpose = AutomaticModelPurpose.Chat,
            ),
        )
    }

    @Test
    fun providerResolutionMatchesAndroidLegacyAndFallbackPriority() {
        val configs = listOf(
            modelConfig("provider-1", "model-a"),
            modelConfig("provider-2", "model-b"),
        )
        val options = configs.availableModelOptions()
        val fallbackKey = options.single { it.modelId == "model-a" }.key
        val fullLabel = options.single { it.modelId == "model-b" }.fullLabel

        assertEquals(
            "provider-2",
            resolveSharedProviderForModel(
                providerConfigs = configs,
                baseConfig = configs.first(),
                preferredKey = "model-b",
                fallbackKey = fallbackKey,
            )?.id,
        )
        assertEquals(
            "provider-2",
            resolveSharedProviderForModel(
                providerConfigs = configs,
                baseConfig = configs.first(),
                preferredKey = fullLabel,
                fallbackKey = fallbackKey,
            )?.id,
        )
    }

    @Test
    fun providerResolutionUsesFirstOptionBeforeBaseConfig() {
        val configs = listOf(
            modelConfig("provider-1", "model-a"),
            modelConfig("provider-2", "model-b"),
        )

        assertEquals(
            "provider-1",
            resolveSharedProviderForModel(
                providerConfigs = configs,
                baseConfig = configs.last(),
                preferredKey = "",
            )?.id,
        )
    }

    @Test
    fun conversationSelectionFallsBackFromStaleSessionKeyLikeAndroid() {
        val configs = listOf(
            modelConfig("provider-1", "gpt-5.6-sol"),
            modelConfig("provider-2", "gemini-3.1-flash-lite"),
        )
        val options = configs.availableModelOptions()
        val explicitDefault = options.single { it.modelId == "gemini-3.1-flash-lite" }.key

        assertEquals(
            explicitDefault,
            resolveSharedConversationModelKey(
                selectedModelKey = "removed-provider:model",
                defaultChatModelKey = explicitDefault,
                options = options,
            ),
        )
        assertEquals(
            options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat),
            resolveSharedConversationModelKey(
                selectedModelKey = "removed-provider:model",
                defaultChatModelKey = "removed-default:model",
                options = options,
            ),
        )
    }
}

private fun modelConfig(id: String, modelId: String) = LlmProviderConfig(
    id = id,
    providerId = id,
    name = id,
    piProviderId = "openai",
    apiKey = "key",
    baseUrl = "https://example.test/v1",
    modelId = modelId,
    cachedModels = listOf(modelId),
    enabledModelIds = listOf(modelId),
)
