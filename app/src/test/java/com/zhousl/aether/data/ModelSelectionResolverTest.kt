package com.zhousl.aether.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSelectionResolverTest {
    @Test
    fun automaticChatModelUsesConfiguredPriorityOrder() {
        val models = listOf(
            "muse-spark-1.1",
            "glm-5.2",
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "kimi-k3",
            "gemini-3.1-pro",
            "grok-4.5",
            "gemini-3.5-flash",
            "claude-sonnet-5",
            "claude-opus-4.8",
            "gpt-5.6-terra",
            "gpt-5.6-sol",
            "claude-fable-5",
        )
        val configs = models.mapIndexed { index, modelId ->
            providerConfig(
                id = "provider-$index",
                providerId = "provider-$index",
                baseUrl = "https://provider-$index.example/v1",
                modelId = modelId,
            )
        }
        val options = configs.availableModelOptions()

        assertEquals(
            "claude-fable-5",
            options.findModelOption(
                options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
            )?.modelId,
        )
        assertEquals(models.reversed(), options.sortedForAutomaticModelPurpose(
            AutomaticModelPurpose.Chat
        ).map { it.modelId })
    }

    @Test
    fun automaticTitleAndNamingModelsUseEfficientPriorityAndWildcardFallback() {
        val models = listOf(
            "vendor-fast-mini",
            "claude-4.5-haiku",
            "gpt-5.4-mini",
            "gpt-5.6-luna",
            "gemini-3.1-flash-lite",
        )
        val options = models.mapIndexed { index, modelId ->
            providerConfig(
                id = "provider-$index",
                providerId = "provider-$index",
                baseUrl = "https://provider-$index.example/v1",
                modelId = modelId,
            )
        }.availableModelOptions()

        val expectedOrder = models.reversed()
        assertEquals(
            expectedOrder,
            options.sortedForAutomaticModelPurpose(AutomaticModelPurpose.Title).map { it.modelId },
        )
        assertEquals(
            expectedOrder,
            options.sortedForAutomaticModelPurpose(AutomaticModelPurpose.Naming).map { it.modelId },
        )
    }

    @Test
    fun disabledAuxiliaryModelsDoNotFallBackToAutomaticSelection() {
        val configs = listOf(
            providerConfig(
                id = "provider-1",
                providerId = "provider-1",
                baseUrl = "https://provider-1.example/v1",
                modelId = "gpt-5.6-sol",
            ),
        )
        val settings = AppSettings(
            defaultTitleModelKey = DisabledAuxiliaryModelKey,
            defaultNamingModelKey = DisabledAuxiliaryModelKey,
        )

        assertEquals(DisabledAuxiliaryModelKey, resolveDefaultTitleModelKey(settings, configs))
        assertEquals(DisabledAuxiliaryModelKey, resolveDefaultNamingModelKey(settings, configs))
    }

    @Test
    fun explicitOnboardingModelOverridesAutomaticChatPreference() {
        val config = providerConfig(
            id = "tour-provider",
            providerId = "tour",
            baseUrl = "https://tour.example/v1",
            modelId = "user-selected-model",
        ).copy(
            cachedModels = listOf("gpt-5.5", "user-selected-model"),
            enabledModelIds = listOf("gpt-5.5", "user-selected-model"),
        )

        val updated = AppSettings().withExplicitDefaultChatModel(config)

        assertEquals("user-selected-model", updated.modelId)
        assertEquals(
            buildModelOptionKey(config.id, "user-selected-model"),
            updated.defaultChatModelKey,
        )
    }

    @Test
    fun resolveModelSettingsUsesSessionPreferredModelBeforeDefault() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val sessionConfig = providerConfig(
            id = "session-provider",
            providerId = "session",
            baseUrl = "https://session.example/v1",
            modelId = "claude-sonnet-4-5",
        )
        val providerConfigs = listOf(defaultConfig, sessionConfig)
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = providerConfigs,
            preferredModelKey = buildModelOptionKey(sessionConfig.id, sessionConfig.modelId),
            fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
        )

        assertEquals("https://session.example/v1", resolved.baseUrl)
        assertEquals("claude-sonnet-4-5", resolved.modelId)
    }

    @Test
    fun resolveModelSettingsFallsBackToDefaultWhenPreferredMissing() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = listOf(defaultConfig),
            preferredModelKey = "missing-provider::missing-model",
            fallbackModelKey = resolveDefaultChatModelKey(settings, listOf(defaultConfig)),
        )

        assertEquals("https://default.example/v1", resolved.baseUrl)
        assertEquals("gpt-5.4", resolved.modelId)
    }

    @Test
    fun resolveDefaultCompactingModelPrefersSummaryOptimizedAutomaticModel() {
        val chatConfig = providerConfig(
            id = "chat-provider",
            providerId = "chat",
            baseUrl = "https://chat.example/v1",
            modelId = "gpt-5.4",
        )
        val compactConfig = providerConfig(
            id = "compact-provider",
            providerId = "compact",
            baseUrl = "https://compact.example/v1",
            modelId = "gemini-3.5-flash",
        )

        val resolved = resolveDefaultCompactingModelKey(
            settings = AppSettings(),
            providerConfigs = listOf(chatConfig, compactConfig),
        )

        assertEquals(buildModelOptionKey(compactConfig.id, compactConfig.modelId), resolved)
    }

    @Test
    fun resolveDefaultCompactingModelUsesConfiguredPriorityAcrossProviders() {
        val compactingConfig = providerConfig(
            id = "compacting-provider",
            providerId = "compacting",
            baseUrl = "https://compacting.example/v1",
            modelId = "gemini-3.5-flash",
        )
        val openAiResponsesConfig = providerConfig(
            id = "openai-responses-provider",
            providerId = "openai",
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.6-luna",
            piProviderId = "openai",
        )

        val resolved = resolveDefaultCompactingModelKey(
            settings = AppSettings(),
            providerConfigs = listOf(compactingConfig, openAiResponsesConfig),
        )

        assertEquals(buildModelOptionKey(compactingConfig.id, compactingConfig.modelId), resolved)
    }

    private fun providerConfig(
        id: String,
        providerId: String,
        baseUrl: String,
        modelId: String,
        piProviderId: String = "openai-compatible",
    ): LlmProviderConfig = LlmProviderConfig(
        id = id,
        providerId = providerId,
        name = providerId,
        piProviderId = piProviderId,
        apiKey = "test-key",
        baseUrl = baseUrl,
        modelId = modelId,
    )
}
