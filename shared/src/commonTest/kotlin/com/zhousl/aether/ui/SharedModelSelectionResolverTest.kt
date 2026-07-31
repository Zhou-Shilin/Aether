package com.zhousl.aether.ui

import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.resolveAutomaticModelKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModelSelectionResolverTest {
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
