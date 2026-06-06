package com.zhousl.aether.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    @Test
    fun freshInstallLaunchesOnboarding() {
        val settings = AppSettings()

        assertTrue(settings.shouldLaunchOnboarding())
        assertFalse(settings.isOnboardingComplete())
    }

    @Test
    fun seenButIncompleteSetupDoesNotRelaunchOnboarding() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = 0,
        )

        assertFalse(settings.shouldLaunchOnboarding())
    }

    @Test
    fun completedOnboardingSuppressesFurtherCompletionTriggers() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = CurrentOnboardingVersion,
        )

        assertTrue(settings.isOnboardingComplete())
        assertFalse(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = true,
            )
        )
    }

    @Test
    fun providerValidationRequiresApiKeyForOfficialOpenAiAndVertexEndpoints() {
        assertFalse(
            isProviderSetupValid(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "",
                baseUrl = "https://api.openai.com/v1",
                modelId = "gpt-5.4",
            )
        )
        assertFalse(
            isProviderSetupValid(
                provider = LlmProvider.VertexExpress,
                apiKey = "",
                baseUrl = "https://aiplatform.googleapis.com/v1",
                modelId = "gemini-2.5-flash",
            )
        )
        assertTrue(
            isProviderSetupValid(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "",
                baseUrl = "http://10.0.2.2:11434/v1",
                modelId = "local-model",
            )
        )
    }

    @Test
    fun followUpTourCardRequiresWaitingStateAndSuccessfulReply() {
        assertTrue(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = true,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = false,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = true,
                isSuccessfulAssistantReply = false,
            )
        )
    }

    @Test
    fun freshInstallStartsWithNoEnabledRuntime() {
        val settings = AppSettings()

        assertTrue(settings.enabledRuntimeIds.isEmpty())
        assertTrue(settings.defaultRuntimeId == null)
    }

    @Test
    fun enablingSecondRuntimeDoesNotOverrideExistingDefaultWhenRequested() {
        val termuxDefault = AppSettings(
            enabledRuntimeIds = setOf(LocalRuntimeId.Termux),
            defaultRuntimeId = LocalRuntimeId.Termux,
            termuxSetupCompleted = true,
        )

        val withAlpine = termuxDefault.enableRuntimeForTest(
            runtimeId = LocalRuntimeId.Alpine,
            makeDefault = false,
        )

        assertTrue(LocalRuntimeId.Termux in withAlpine.enabledRuntimeIds)
        assertTrue(LocalRuntimeId.Alpine in withAlpine.enabledRuntimeIds)
        assertTrue(withAlpine.defaultRuntimeId == LocalRuntimeId.Termux)
    }

    @Test
    fun onlySuccessfulReplyCompletesIncompleteOnboarding() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = 0,
        )

        assertTrue(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = false,
            )
        )
    }
}

private fun AppSettings.enableRuntimeForTest(
    runtimeId: LocalRuntimeId,
    makeDefault: Boolean,
): AppSettings {
    val enabled = enabledRuntimeIds + runtimeId
    return copy(
        termuxSetupCompleted = termuxSetupCompleted || runtimeId == LocalRuntimeId.Termux,
        alpineSetupCompleted = alpineSetupCompleted || runtimeId == LocalRuntimeId.Alpine,
        enabledRuntimeIds = enabled,
        defaultRuntimeId = if (makeDefault) runtimeId else defaultRuntimeId,
    )
}
