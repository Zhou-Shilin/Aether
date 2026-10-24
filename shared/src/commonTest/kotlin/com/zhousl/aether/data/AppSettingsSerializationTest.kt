package com.zhousl.aether.data

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsSerializationTest {
    @Test
    fun providerServiceTierSurvivesStorageAndModelSelection() {
        val provider = LlmProviderConfig(
            id = "tier-provider", providerId = "custom", name = "Custom",
            piProviderId = "openai-compatible", apiKey = "test",
            baseUrl = "https://example.com/v1", modelId = "test-model",
            serviceTier = "ultrafast",
        )
        val restored = parseProviderConfigs("[${provider.toJsonObject()}]").single()
        assertEquals("ultrafast", restored.serviceTier)
        val option = listOf(restored).availableModelOptions().single()
        assertEquals("ultrafast", AppSettings().withModelOption(option).serviceTier)
        val legacy = JsonObject(provider.toJsonObject().filterKeys { it != "serviceTier" })
        assertEquals("", parseProviderConfigs("[$legacy]").single().serviceTier)
    }

    @Test
    fun phoneUseDefaultsToAccessibilityAndPreservesExplicitVirtualDisplayChoices() {
        assertEquals(AgentModeAuthorizationMethod.Accessibility, AppSettings().agentModeAuthorizationMethod)
        assertEquals(AgentModeAuthorizationMethod.Accessibility, parseAppSettings("{}").agentModeAuthorizationMethod)
        assertEquals(AgentModeAuthorizationMethod.Accessibility, AgentModeAuthorizationMethod.fromStorage("unknown"))
        for (method in AgentModeAuthorizationMethod.entries) {
            val settings = AppSettings(agentModeAuthorizationEnabled = true, agentModeAuthorizationMethod = method)
            assertEquals(method, parseAppSettings(serializeAppSettings(settings)).agentModeAuthorizationMethod)
            assertEquals(method, AgentModeAuthorizationMethod.fromStorage(method.storageValue))
        }
    }

    @Test
    fun phoneUseDefaultsAndExplicitPreferencesSurviveSerialization() {
        val defaults = parseAppSettings("{}")
        assertEquals(true, defaults.agentModeAuthorizationEnabled)
        assertEquals(false, defaults.phoneUseVirtualDisplayEnabled)
        assertEquals(AgentModeDisplayOrientation.Device, defaults.agentModeDisplayOrientation)
        assertEquals(AgentModeDisplayOrientation.Device, AgentModeDisplayOrientation.fromStorage("unknown"))
        assertEquals(true, defaults.phoneUseAllowStrategySwitch)
        assertEquals(PhoneUseInputStrategy.ElementTreeFirst, defaults.phoneUseInputStrategy)
        assertEquals(PhoneUseOutputStrategy.ElementFirst, defaults.phoneUseOutputStrategy)
        val settings = defaults.copy(
            agentModeAuthorizationEnabled = false,
            agentModeAuthorizationMethod = AgentModeAuthorizationMethod.Shizuku,
            phoneUseInputStrategy = PhoneUseInputStrategy.ScreenshotFirst,
            phoneUseOutputStrategy = PhoneUseOutputStrategy.GestureFirst,
            phoneUseAllowStrategySwitch = false,
            phoneUseVirtualDisplayEnabled = true,
            agentModeDisplayOrientation = AgentModeDisplayOrientation.Landscape,
        )
        assertEquals(settings, parseAppSettings(serializeAppSettings(settings)))
        assertEquals(AgentModeAuthorizationMethod.Shizuku, settings.phoneUseAuthorizationMethod)
        assertEquals(AgentModeAuthorizationMethod.Accessibility,
            settings.copy(phoneUseVirtualDisplayEnabled = false).phoneUseAuthorizationMethod)
    }

    @Test
    fun completeSettingsRoundTrip() {
        val settings = AppSettings(
            piProviderId = "anthropic",
            providerAuthMethod = ProviderAuthMethod.OAuth,
            oauthCredentialJson = "{\"access\":\"token\"}",
            providerEnvironmentVariables = listOf(PiProviderEnvironmentVariable("REGION", "test")),
            customHeaders = listOf(LlmCustomHeader("X-Test", "value")),
            serviceTier = "ultrafast",
            reasoningEffort = "high",
            systemPrompt = "shared prompt",
            keepTasksRunningInBackground = false,
            notifyOnTaskCompletion = false,
            agentWorkspaceMode = AgentWorkspaceMode.PerSession,
            enabledRuntimeIds = setOf(LocalRuntimeId.Alpine),
            defaultRuntimeId = LocalRuntimeId.Alpine,
            alpinePackageProfiles = mapOf("chrome" to PackageProfileState("chrome", installed = true)),
            alpineEnvironmentVariables = listOf(AlpineEnvironmentVariable("A", "B")),
            language = AppLanguage.SimplifiedChinese,
            themeMode = AppThemeMode.Dark,
            defaultChatModelKey = "provider/chat-model",
            defaultTitleModelKey = "provider/title-model",
            defaultNamingModelKey = "provider/naming-model",
            defaultCompactingModelKey = "provider/compacting-model",
            defaultSelectedSkillIds = listOf("review", "research"),
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = CurrentOnboardingVersion,
            privacyPolicyAccepted = true,
            lastUpdateCheckAtMillis = 1234L,
        )

        assertEquals(settings, parseAppSettings(serializeAppSettings(settings)))
    }

    @Test
    fun unknownFieldsRemainForwardCompatible() {
        val parsed = parseAppSettings("""{"themeMode":"Dark","futureValue":42}""")

        assertEquals(AppThemeMode.Dark, parsed.themeMode)
        assertEquals(DefaultReasoningEffort, parsed.reasoningEffort)
    }

    @Test
    fun acceptedPrivacyPolicyCannotBeOverwrittenByStaleSettings() {
        assertEquals(true, privacyPolicyAccepted(persisted = true, requested = false))
        assertEquals(true, privacyPolicyAccepted(persisted = false, requested = true))
        assertEquals(false, privacyPolicyAccepted(persisted = false, requested = false))
    }

    @Test
    fun thinkingCatalogCacheRoundTripPreservesResolvedEmptyModels() {
        val cache = SharedThinkingCatalogCache(
            source = ModelsDevThinkingCatalogSource,
            levelsByProviderModel = mapOf(
                "openai/gpt-5" to listOf("off", "medium", "high"),
                "anthropic/claude-haiku" to emptyList(),
            ),
            clampsByProviderModel = mapOf(
                "openai/gpt-5" to mapOf("max" to "high"),
            ),
            reasoningModels = setOf("openai/gpt-5"),
        )

        assertEquals(cache, parseSharedThinkingCatalogCache(serializeSharedThinkingCatalogCache(cache)))
    }

    @Test
    fun legacyThinkingCatalogCacheHasNoModelsDevSource() {
        val cache = parseSharedThinkingCatalogCache(
            """{"levelsByProviderModel":{"openai/gpt-5":["high"]}}""",
        )

        assertEquals("", cache.source)
    }

    @Test
    fun russianLanguageSerializationAndTagResolution() {
        assertEquals(AppLanguage.Russian, appLanguageForTag("ru"))
        assertEquals(AppLanguage.Russian, appLanguageForTag("ru-RU"))
        assertEquals(AppLanguage.Russian, AppLanguage.fromStorage("ru"))

        val settings = AppSettings(language = AppLanguage.Russian)
        val serialized = serializeAppSettings(settings)
        val parsed = parseAppSettings(serialized)
        assertEquals(AppLanguage.Russian, parsed.language)
    }
}
