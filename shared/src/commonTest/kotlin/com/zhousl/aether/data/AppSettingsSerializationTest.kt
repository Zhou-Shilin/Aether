package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsSerializationTest {
    @Test
    fun completeSettingsRoundTrip() {
        val settings = AppSettings(
            piProviderId = "anthropic",
            providerAuthMethod = ProviderAuthMethod.OAuth,
            oauthCredentialJson = "{\"access\":\"token\"}",
            providerEnvironmentVariables = listOf(PiProviderEnvironmentVariable("REGION", "test")),
            customHeaders = listOf(LlmCustomHeader("X-Test", "value")),
            reasoningEffort = "high",
            systemPrompt = "shared prompt",
            tavilyApiKey = "tavily",
            keepTasksRunningInBackground = false,
            notifyOnTaskCompletion = false,
            agentWorkspaceMode = AgentWorkspaceMode.PerSession,
            enabledRuntimeIds = setOf(LocalRuntimeId.Alpine),
            defaultRuntimeId = LocalRuntimeId.Alpine,
            alpinePackageProfiles = mapOf("chrome" to PackageProfileState("chrome", installed = true)),
            alpineEnvironmentVariables = listOf(AlpineEnvironmentVariable("A", "B")),
            language = AppLanguage.SimplifiedChinese,
            themeMode = AppThemeMode.Dark,
            defaultSelectedSkillIds = listOf("example"),
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
    fun thinkingCatalogCacheRoundTripPreservesResolvedEmptyModels() {
        val cache = SharedThinkingCatalogCache(
            levelsByProviderModel = mapOf(
                "openai/gpt-5" to listOf("off", "medium", "high"),
                "anthropic/claude-haiku" to emptyList(),
            ),
            clampsByProviderModel = mapOf(
                "openai/gpt-5" to mapOf("max" to "high"),
            ),
        )

        assertEquals(cache, parseSharedThinkingCatalogCache(serializeSharedThinkingCatalogCache(cache)))
    }
}
