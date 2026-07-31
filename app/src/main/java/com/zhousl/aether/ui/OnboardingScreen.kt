package com.zhousl.aether.ui

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zhousl.aether.data.AgentModeAuthorizationMethod
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.PiProviderDefinition
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.automaticModelPriority
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.data.RootSetupIssue
import com.zhousl.aether.data.RootSetupState
import com.zhousl.aether.runtime.LocalRuntimeIssue
import com.zhousl.aether.runtime.LocalRuntimeSetupState
import com.zhousl.aether.data.pi.PiCoreSetupPhase
import com.zhousl.aether.data.pi.PiCoreSetupState
import com.zhousl.aether.data.pi.PiCoreSetupActivity
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.termux.TermuxSetupIssue
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.termux.TermuxContract
import com.zhousl.aether.R
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val StepFadeDuration = 560
private const val MessageTravelDuration = 1_520
private const val ContentFadeDuration = 920
private const val MessageSettleDelayMillis = 800L
private const val MessageMinDurationMillis = 1_000L
private const val MessageMaxDurationMillis = 3_300L

private val TourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val InitialOnboardingSteps = listOf(
    OnboardingStep.Landing,
    OnboardingStep.AlpineSetup,
    OnboardingStep.ProviderSetup,
)
private val FollowUpOnboardingSteps = listOf(
    OnboardingStep.LocalRuntimeChoice,
    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
    OnboardingStep.TavilySetup,
)

private val TourBackground: Color
    get() = AetherBackground
private val TourTextPrimary: Color
    get() = AetherOnSurface
private val TourTextSecondary: Color
    get() = AetherOnSurfaceVariant
private val TourTextTertiary: Color
    get() = AetherOnSurfaceVariant.copy(alpha = 0.72f)
private val TourDivider: Color
    get() = AetherOutlineSoft
private val TourSurface: Color
    get() = AetherSurfaceHigh
private val TourButton: Color
    get() = Color.Black
private val TourBlue: Color
    get() = AetherPrimary
private val TourGreen: Color
    get() = AetherSecondary
private val TourGold: Color
    get() = AetherTertiary
private val TourPurple: Color
    get() = AetherPrimary


private enum class ProviderTourStage {
    PickAuthentication,
    PickProvider,
    Credentials,
    Model,
}

@Composable
fun OnboardingScreen(
    initialStep: OnboardingStep,
    replayMode: Boolean,
    existingProviderConfig: LlmProviderConfig?,
    isFetchingModels: Boolean,
    providerAuthState: PiProviderAuthState,
    piCoreSetupState: PiCoreSetupState,
    termuxSetupState: TermuxSetupState,
    alpineSetupState: LocalRuntimeSetupState,
    rootSetupState: RootSetupState,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    tavilyApiKey: String,
    setupPreviewMode: Boolean = false,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitProviderAuthPrompt: (String, String, Boolean) -> Unit,
    onClearProviderAuthState: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onCompleteProviderSetup: (LlmProviderConfig) -> Unit,
    onSaveTavilyApiKey: (String) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onInitializeAlpineRuntime: () -> Unit,
    onRefreshAlpineSetup: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
    onSaveAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onCompleteFollowUp: () -> Unit,
) {

    var currentStep by rememberSaveable(initialStep, replayMode) {
        mutableStateOf(initialStep)
    }
    var tavilyApiKeyValue by rememberSaveable(initialStep, replayMode, tavilyApiKey) {
        mutableStateOf(tavilyApiKey)
    }
    val formState = rememberProviderFormState(existingProviderConfig)
    var selectedRuntimePath by rememberSaveable(initialStep, replayMode) {
        mutableStateOf<OnboardingStep?>(null)
    }
    val isInitialFlow = initialStep == OnboardingStep.Landing ||
        initialStep == OnboardingStep.ProviderSetup ||
        initialStep == OnboardingStep.AlpineSetup
    val steps = remember(initialStep) {
        if (isInitialFlow) InitialOnboardingSteps else FollowUpOnboardingSteps
    }

    fun indexOf(step: OnboardingStep): Int = steps.indexOf(step).coerceAtLeast(0)
    fun continueAfterLocalAccessSetup() {
        currentStep = OnboardingStep.TavilySetup
    }
    fun continueAfterTermuxStep() {
        currentStep = if (termuxSetupState.isReady) {
            OnboardingStep.AgentModeAuthorization
        } else {
            OnboardingStep.TavilySetup
        }
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = StepFadeDuration,
                    delayMillis = 180,
                    easing = TourEasing,
                )
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = TourEasing,
                )
            )
        },
        label = "onboarding_step_transition",
    ) { step ->
        val stepIndex = indexOf(step) + 1
        when (step) {
            OnboardingStep.Landing -> LandingStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                onPrimary = { currentStep = OnboardingStep.AlpineSetup },
                onSecondary = if (replayMode) onClose else onSkip,
            )

            OnboardingStep.ProviderSetup -> ProviderSetupStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                formState = formState,
                isFetchingModels = isFetchingModels,
                onFetchModels = onFetchModels,
                authState = providerAuthState,
                onStartProviderLogin = onStartProviderLogin,
                onSubmitAuthPrompt = onSubmitProviderAuthPrompt,
                onClearAuthState = onClearProviderAuthState,
                onExit = if (replayMode) onClose else onSkip,
                onClose = if (replayMode) onClose else onSkip,
                onReturnToLanding = { currentStep = OnboardingStep.Landing },
                onComplete = { onCompleteProviderSetup(formState.buildConfig()) },
            )

            OnboardingStep.LocalRuntimeChoice -> LocalRuntimeChoiceStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                onClose = onClose,
                onConfigure = {
                    selectedRuntimePath = OnboardingStep.TermuxSetup
                    currentStep = OnboardingStep.TermuxSetup
                },
                onSkip = {
                    selectedRuntimePath = null
                    currentStep = OnboardingStep.TavilySetup
                },
            )

            OnboardingStep.AlpineSetup -> AlpineRuntimeStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                setupState = alpineSetupState,
                piCoreSetupState = piCoreSetupState,
                required = isInitialFlow,
                onBack = {
                    if (setupPreviewMode) {
                        onClose()
                    } else {
                        currentStep = if (isInitialFlow) {
                        OnboardingStep.Landing
                        } else {
                            OnboardingStep.LocalRuntimeChoice
                        }
                    }
                },
                onClose = onClose,
                onInitialize = onInitializeAlpineRuntime,
                onRefresh = onRefreshAlpineSetup,
                onContinue = {
                    if (setupPreviewMode) {
                        onClose()
                    } else {
                        currentStep = if (isInitialFlow) {
                            OnboardingStep.ProviderSetup
                        } else {
                            OnboardingStep.TavilySetup
                        }
                    }
                },
            )

            OnboardingStep.TermuxSetup -> TermuxStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                setupState = termuxSetupState,
                rootSetupState = rootSetupState,
                onClose = onClose,
                onBack = { currentStep = OnboardingStep.LocalRuntimeChoice },
                onContinue = ::continueAfterTermuxStep,
                onRootConfigured = ::continueAfterLocalAccessSetup,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
                onRefreshRootSetup = onRefreshRootSetup,
                onConfigureWithRoot = onConfigureWithRoot,
            )

            OnboardingStep.AgentModeAuthorization -> AgentModeAuthorizationStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                initialMethod = agentModeAuthorizationMethod,
                onBack = { currentStep = OnboardingStep.TermuxSetup },
                onClose = onClose,
                onContinue = { enabled, method ->
                    onSaveAgentModeAuthorization(enabled, method)
                    currentStep = OnboardingStep.TavilySetup
                },
            )

            OnboardingStep.TavilySetup -> TavilyStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                value = tavilyApiKeyValue,
                onValueChange = { tavilyApiKeyValue = it },
                onBack = {
                    currentStep = if (selectedRuntimePath == OnboardingStep.TermuxSetup && termuxSetupState.isReady) {
                        OnboardingStep.AgentModeAuthorization
                    } else if (selectedRuntimePath == OnboardingStep.TermuxSetup) {
                        OnboardingStep.TermuxSetup
                    } else {
                        OnboardingStep.LocalRuntimeChoice
                    }
                },
                onClose = onClose,
                onContinue = {
                    val trimmed = tavilyApiKeyValue.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveTavilyApiKey(trimmed)
                    }
                    onCompleteFollowUp()
                },
            )
        }
    }
}

@Composable
private fun LandingStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    OnboardingLandingStep(
        stepIndex = stepIndex,
        stepCount = stepCount,
        replayMode = replayMode,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
    )
}

@Composable
private fun TourChromeBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TourBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            StepTopBar(
                stepIndex = stepIndex,
                stepCount = stepCount,
                onBack = onBack,
                topRightLabel = topRightLabel,
                onTopRight = onTopRight,
            )
        }
    }
}

@Composable
private fun ConversationStepPage(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
    isExiting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = onBack,
        topRightLabel = topRightLabel,
        onTopRight = onTopRight,
        isExiting = isExiting,
        content = content,
    )
}

@Composable
private fun ProviderSetupStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    formState: ProviderFormState,
    isFetchingModels: Boolean,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    authState: PiProviderAuthState,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit,
    onClearAuthState: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    onReturnToLanding: () -> Unit,
    onComplete: () -> Unit,
) {

    var stage by rememberSaveable(stepIndex, replayMode) {
        mutableStateOf(ProviderTourStage.PickAuthentication)
    }
    var selectedAuthMethodName by rememberSaveable(stepIndex, replayMode) {
        mutableStateOf(ProviderAuthMethod.ApiKey.name)
    }
    var isFinishing by rememberSaveable(stepIndex, replayMode) { mutableStateOf(false) }
    var providerSearch by rememberSaveable(stepIndex, replayMode) { mutableStateOf("") }
    var customModelValue by rememberSaveable(
        stepIndex,
        replayMode,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(TextFieldValue())
    }
    val selectedAuthMethod = ProviderAuthMethod.valueOf(selectedAuthMethodName)
    val definition = formState.selectedDefinition
    val isLoadingModels = formState.isFetchingModelsLocally || isFetchingModels
    val modelChoices = remember(definition.id, formState.cachedModels, formState.modelId) {
        (formState.cachedModels + formState.modelId)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    val providerChoices = remember(providerSearch, selectedAuthMethod) {
        val query = providerSearch.trim().lowercase()
        PiProviderCatalog.providers.filter { provider ->
            val supportsMethod = when (selectedAuthMethod) {
                ProviderAuthMethod.ApiKey -> provider.supportsApiKey
                ProviderAuthMethod.OAuth -> provider.supportsOAuth
                ProviderAuthMethod.Ambient -> provider.supportsAmbientAuth
            }
            supportsMethod && (
                query.isBlank() ||
                    provider.displayName.lowercase().contains(query) ||
                    provider.id.lowercase().contains(query) ||
                    provider.category.lowercase().contains(query)
                )
        }
    }
    val canContinueFromCredentials = formState.isAuthenticationConfigured()

    val message = when (stage) {
        ProviderTourStage.PickAuthentication -> stringResource(R.string.onboarding_provider_auth_message)
        ProviderTourStage.PickProvider -> stringResource(R.string.onboarding_provider_pick_message)
        ProviderTourStage.Credentials -> stringResource(R.string.onboarding_provider_credentials_message)
        ProviderTourStage.Model -> stringResource(R.string.onboarding_provider_model_message)
    }
    val backAction: (() -> Unit)? = when (stage) {
        ProviderTourStage.PickAuthentication -> onReturnToLanding
        ProviderTourStage.PickProvider -> { { stage = ProviderTourStage.PickAuthentication } }
        ProviderTourStage.Credentials -> { { stage = ProviderTourStage.PickProvider } }
        ProviderTourStage.Model -> { { stage = ProviderTourStage.Credentials } }
    }

    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(320)
            onComplete()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = backAction,
        topRightLabel = if (replayMode) stringResource(R.string.common_close) else stringResource(R.string.common_skip),
        onTopRight = if (replayMode) onClose else onExit,
        isExiting = isFinishing,
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = ContentFadeDuration,
                        delayMillis = 160,
                        easing = TourEasing,
                    )
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 160,
                        easing = TourEasing,
                    )
                )
            },
            label = "provider_stage_transition",
        ) { currentStage ->
            when (currentStage) {
                ProviderTourStage.PickAuthentication -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = stringResource(R.string.provider_add_subscription),
                            subtitle = stringResource(R.string.provider_add_subscription_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.OAuth.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.OAuth)
                                stage = ProviderTourStage.PickProvider
                            },
                        )
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.Key,
                            title = stringResource(R.string.provider_add_api_key),
                            subtitle = stringResource(R.string.provider_add_api_key_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.ApiKey.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.ApiKey)
                                stage = ProviderTourStage.PickProvider
                            },
                        )
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.Cloud,
                            title = stringResource(R.string.provider_add_environment),
                            subtitle = stringResource(R.string.provider_add_environment_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.Ambient.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.Ambient)
                                stage = ProviderTourStage.PickProvider
                            },
                        )
                    }
                }

                ProviderTourStage.PickProvider -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MinimalInputField(
                            label = stringResource(R.string.common_search),
                            value = providerSearch,
                            placeholder = stringResource(R.string.onboarding_provider_search_placeholder),
                            onValueChange = { providerSearch = it },
                        )
                        providerChoices.forEach { provider ->
                            ProviderStageButton(
                                label = provider.displayName,
                                subtitle = "${provider.category} · ${provider.id}",
                                provider = provider,
                                onClick = {
                                    onClearAuthState()
                                    formState.applyProviderDefaults(provider)
                                    formState.setAuthMethod(selectedAuthMethod)
                                    stage = ProviderTourStage.Credentials
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.onboarding_change_later_settings),
                            style = MaterialTheme.typography.bodySmall,
                            color = TourTextSecondary,
                        )
                    }
                }

                ProviderTourStage.Credentials -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        TinyLabel(
                            text = stringResource(
                                R.string.onboarding_using_pi_provider,
                                definition.displayName,
                            ),
                            color = TourGreen,
                        )
                        ProviderAuthenticationSetup(
                            state = formState,
                            authState = authState,
                            onStartProviderLogin = onStartProviderLogin,
                            onSubmitAuthPrompt = onSubmitAuthPrompt,
                            onClearAuthState = onClearAuthState,
                            cardColor = TourSurface,
                        )
                        PrimaryActionButton(
                            label = if (isLoadingModels) stringResource(R.string.onboarding_loading_models) else stringResource(R.string.common_next),
                            enabled = canContinueFromCredentials && !isLoadingModels,
                            onClick = {
                                formState.isFetchingModelsLocally = true
                                onFetchModels(formState.buildConfig()) { models ->
                                    val ordered = prioritizedModelOptions(
                                        piProviderId = definition.id,
                                        cachedModels = models,
                                    )
                                    formState.cachedModels = models
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                        .distinctBy { it.lowercase() }
                                    formState.enabledModelIds = ordered
                                    if (ordered.isNotEmpty()) {
                                        formState.modelId = ordered.first()
                                    } else {
                                        formState.modelId = ""
                                    }
                                    customModelValue = TextFieldValue()
                                    formState.isFetchingModelsLocally = false
                                    stage = ProviderTourStage.Model
                                }
                            },
                            isLoading = isLoadingModels,
                        )
                    }
                }

                ProviderTourStage.Model -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_best_models_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = TourTextSecondary,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            modelChoices.take(8).forEach { model ->
                                ModelOptionButton(
                                    label = model,
                                    selected = formState.modelId.trim().equals(model, ignoreCase = true),
                                    onClick = {
                                        formState.modelId = model
                                        customModelValue = TextFieldValue()
                                        formState.enabledModelIds = (listOf(model) + formState.enabledModelIds)
                                            .map(String::trim)
                                            .filter(String::isNotEmpty)
                                            .distinct()
                                    },
                                )
                            }
                        }
                        MinimalTextFieldValueInput(
                            label = stringResource(R.string.onboarding_model),
                            value = customModelValue,
                            placeholder = stringResource(R.string.onboarding_or_type_your_own_model),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { value ->
                                customModelValue = value
                                formState.modelId = value.text
                                val trimmed = value.text.trim()
                                if (trimmed.isNotEmpty()) {
                                    formState.enabledModelIds = (listOf(trimmed) + formState.enabledModelIds)
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                        .distinct()
                                }
                            },
                        )
                        PrimaryActionButton(
                            label = stringResource(R.string.common_start_chat),
                            enabled = formState.isValid(emptySet()),
                            onClick = { isFinishing = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalRuntimeChoiceStep(
    stepIndex: Int,
    stepCount: Int,
    onClose: () -> Unit,
    onConfigure: () -> Unit,
    onSkip: () -> Unit,
) {
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_local_runtime_choice_message),
        onBack = null,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.Terminal,
                accent = TourGreen,
                title = stringResource(R.string.onboarding_termux_agent_mode_title),
                body = stringResource(R.string.onboarding_termux_agent_mode_subtitle),
            )
            TourActionRow(
                primaryLabel = stringResource(R.string.onboarding_configure_termux_agent_mode),
                onPrimary = onConfigure,
                secondaryLabel = stringResource(R.string.onboarding_not_now),
                onSecondary = onSkip,
            )
        }
    }
}

@Composable
private fun LocalRuntimePathButton(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TourTextPrimary)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlpineRuntimeStep(
    stepIndex: Int,
    stepCount: Int,
    setupState: LocalRuntimeSetupState,
    piCoreSetupState: PiCoreSetupState,
    required: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onInitialize: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_alpine_runtime_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.Code,
                accent = when (setupState.issue) {
                    LocalRuntimeIssue.Ready -> TourGreen
                    LocalRuntimeIssue.UnsupportedAbi,
                    LocalRuntimeIssue.MissingAssets,
                    LocalRuntimeIssue.Failed -> TourGold
                    else -> TourBlue
                },
                title = "Alpine",
                body = alpineTourStatusText(setupState),
            )
            if (!setupState.isReady && setupState.detail.isNotBlank()) {
                Text(
                    text = setupState.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                )
            }
            if (
                piCoreSetupState.isChecking ||
                piCoreSetupState.output.isNotBlank() ||
                setupState.isReady && (required || piCoreSetupState.phase != PiCoreSetupPhase.Idle)
            ) {
                PiCoreSetupProgress(piCoreSetupState)
            }
            when (setupState.issue) {
                LocalRuntimeIssue.Ready -> if (!required || piCoreSetupState.isReady) {
                    TourActionRow(
                        primaryLabel = stringResource(R.string.common_continue),
                        onPrimary = onContinue,
                        secondaryLabel = stringResource(R.string.common_refresh),
                        onSecondary = onRefresh,
                    )
                } else {
                    TourActionRow(
                        primaryLabel = if (piCoreSetupState.isChecking) {
                            stringResource(R.string.onboarding_pi_setup_working)
                        } else stringResource(R.string.common_retry),
                        onPrimary = onRefresh,
                        primaryEnabled = !piCoreSetupState.isChecking,
                        primaryLoading = piCoreSetupState.isChecking,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = onBack,
                    )
                }

                LocalRuntimeIssue.UnsupportedAbi,
                LocalRuntimeIssue.MissingAssets,
                LocalRuntimeIssue.Failed -> if (required) {
                    TourActionRow(
                        primaryLabel = stringResource(R.string.common_refresh),
                        onPrimary = onRefresh,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = onBack,
                    )
                } else {
                    TourActionRow(
                        primaryLabel = stringResource(R.string.common_refresh),
                        onPrimary = onRefresh,
                        secondaryLabel = stringResource(R.string.common_skip),
                        onSecondary = onContinue,
                    )
                }

                else -> if (required) {
                    TourActionRow(
                        primaryLabel = if (piCoreSetupState.isChecking) {
                            stringResource(R.string.onboarding_pi_setup_working)
                        } else {
                            stringResource(R.string.settings_initialize)
                        },
                        onPrimary = if (piCoreSetupState.isChecking) onRefresh else onInitialize,
                        primaryEnabled = !piCoreSetupState.isChecking,
                        primaryLoading = piCoreSetupState.isChecking,
                        secondaryLabel = stringResource(R.string.common_back),
                        onSecondary = onBack,
                    )
                } else {
                    TourActionRow(
                        primaryLabel = stringResource(R.string.settings_initialize),
                        onPrimary = onInitialize,
                        secondaryLabel = stringResource(R.string.common_skip),
                        onSecondary = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PiCoreSetupProgress(
    setupState: PiCoreSetupState,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val stepCount = 5
    val currentStep = if (setupState.phase == PiCoreSetupPhase.Failed) {
        setupState.failedAtPhase.step
    } else {
        setupState.phase.step
    }.coerceIn(0, stepCount)
    val animatedProgress by animateFloatAsState(
        targetValue = currentStep.toFloat() / stepCount,
        animationSpec = tween(
            durationMillis = 700,
            easing = TourEasing,
        ),
        label = "pi_core_setup_progress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TourSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = piCoreSetupStatusText(setupState),
            style = MaterialTheme.typography.bodyMedium,
            color = if (setupState.phase == PiCoreSetupPhase.Failed) TourGold else TourTextPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                Text(
                    text = stringResource(
                        R.string.onboarding_pi_setup_step,
                        currentStep,
                        stepCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TourTextSecondary,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                setupRateText(setupState)?.let { rate ->
                    Text(
                        text = rate,
                        style = MaterialTheme.typography.labelSmall,
                        color = TourTextSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_pi_setup_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = TourTextSecondary,
                    modifier = Modifier
                        .clickable { showDetails = true }
                        .padding(vertical = 4.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TourDivider),
        ) {
            if (currentStep > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (setupState.isReady) TourGreen else TourBlue),
                )
            }
        }
        if (setupState.phase == PiCoreSetupPhase.InstallingNode) {
            Text(
                text = stringResource(R.string.onboarding_pi_setup_node_wait_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TourTextSecondary,
            )
        } else if (setupState.phase == PiCoreSetupPhase.Failed && setupState.detail.isNotBlank()) {
            Text(
                text = setupState.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TourTextSecondary,
            )
        }
    }
    if (showDetails) {
        SetupDetailsDialog(
            output = setupState.output,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun SetupDetailsDialog(
    output: String,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(output) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = TourSurface,
            contentColor = TourTextPrimary,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_pi_setup_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TourTextPrimary,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = TourTextSecondary,
                        )
                    }
                }
                val terminalOutput = output.ifBlank {
                    stringResource(R.string.onboarding_pi_setup_waiting_for_output)
                }
                SyntaxHighlightedCodeBlock(
                    label = stringResource(R.string.onboarding_pi_setup_output),
                    content = remember(terminalOutput) {
                        highlightTerminalTranscript(terminalOutput)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    maxHeight = 420.dp,
                    scrollState = scrollState,
                )
            }
        }
    }
}

private fun setupRateText(setupState: PiCoreSetupState): String? {
    if (setupState.bytesPerSecond <= 0L) return null
    if (
        setupState.activity != PiCoreSetupActivity.Extracting &&
        setupState.activity != PiCoreSetupActivity.Downloading
    ) {
        return null
    }
    val bytesPerSecond = setupState.bytesPerSecond.toDouble()
    return when {
        bytesPerSecond >= 1024.0 * 1024.0 ->
            String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024.0 ->
            String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
    }
}

@Composable
private fun piCoreSetupStatusText(
    setupState: PiCoreSetupState,
): String = when (setupState.phase) {
    PiCoreSetupPhase.Idle -> stringResource(R.string.onboarding_pi_setup_pending)
    PiCoreSetupPhase.CheckingAlpine -> stringResource(R.string.onboarding_pi_setup_checking_alpine)
    PiCoreSetupPhase.CheckingNode -> stringResource(R.string.onboarding_pi_setup_checking_node)
    PiCoreSetupPhase.InstallingNode -> stringResource(R.string.onboarding_pi_setup_installing_node)
    PiCoreSetupPhase.PreparingBridge -> stringResource(R.string.onboarding_pi_setup_preparing_bridge)
    PiCoreSetupPhase.StartingBridge -> stringResource(R.string.onboarding_pi_setup_starting_bridge)
    PiCoreSetupPhase.VerifyingBridge -> stringResource(R.string.onboarding_pi_setup_verifying_bridge)
    PiCoreSetupPhase.Ready -> stringResource(
        R.string.onboarding_pi_setup_ready,
        setupState.nodeVersion.ifBlank { "-" },
    )
    PiCoreSetupPhase.Failed -> stringResource(R.string.onboarding_pi_setup_failed)
}

@Composable
private fun alpineTourStatusText(
    setupState: LocalRuntimeSetupState,
): String = when (setupState.issue) {
    LocalRuntimeIssue.Ready -> stringResource(R.string.onboarding_alpine_status_ready)
    LocalRuntimeIssue.NotConfigured,
    LocalRuntimeIssue.NotInstalled -> stringResource(R.string.onboarding_alpine_status_not_installed)
    LocalRuntimeIssue.UnsupportedAbi -> stringResource(R.string.onboarding_alpine_status_unsupported_abi)
    LocalRuntimeIssue.MissingAssets -> stringResource(R.string.onboarding_alpine_status_missing_assets)
    LocalRuntimeIssue.Failed -> stringResource(R.string.onboarding_alpine_status_failed)
    LocalRuntimeIssue.PermissionMissing,
    LocalRuntimeIssue.ExternalAppsDisabled,
    LocalRuntimeIssue.DispatchFailed -> stringResource(R.string.onboarding_alpine_status_not_ready)
}

@Composable
private fun TermuxStep(
    stepIndex: Int,
    stepCount: Int,
    setupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRootConfigured: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val setupCommandCopiedLabel = stringResource(R.string.onboarding_termux_setup_command_copied)
    var shouldAutoContinue by rememberSaveable(stepIndex) { mutableStateOf(!setupState.isReady) }
    var showRootSetupPrompt by rememberSaveable(stepIndex) { mutableStateOf(true) }
    fun copyTermuxSetupCommand() {
        clipboardManager.setText(AnnotatedString(TermuxContract.ExternalAppsSetupCommand))
        Toast.makeText(
            context,
            setupCommandCopiedLabel,
            Toast.LENGTH_SHORT,
        ).show()
    }
    fun copyTermuxSetupCommandAndOpenTermux() {
        copyTermuxSetupCommand()
        onOpenTermux()
    }

    LaunchedEffect(stepIndex) {
        onRefreshRootSetup()
    }
    LaunchedEffect(showRootSetupPrompt, rootSetupState.isReady) {
        if (showRootSetupPrompt && rootSetupState.isReady) {
            delay(820)
            onRootConfigured()
        }
    }
    LaunchedEffect(showRootSetupPrompt, setupState.isReady) {
        if (!showRootSetupPrompt && shouldAutoContinue && setupState.isReady) {
            shouldAutoContinue = false
            delay(820)
            onContinue()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
message = stringResource(R.string.onboarding_termux_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (showRootSetupPrompt) {
                RootSetupPrompt(
                    rootSetupState = rootSetupState,
                    onUseRoot = onConfigureWithRoot,
                    onContinueManual = { showRootSetupPrompt = false },
                    onRootConfigured = onRootConfigured,
                    onInstallTermux = onInstallTermux,
                )
            } else {
                StepLead(
                    icon = Icons.Rounded.Terminal,
                    accent = termuxStatusColor(setupState.issue),
                    title = stringResource(R.string.settings_termux),
                    body = termuxStatusSentence(setupState),
                )
                when (setupState.issue) {
                    TermuxSetupIssue.Ready -> TourActionRow(
                        primaryLabel = stringResource(R.string.common_continue),
                        onPrimary = onContinue,
                        secondaryLabel = stringResource(R.string.common_refresh),
                        onSecondary = onRefresh,
                    )

                    TermuxSetupIssue.NotInstalled -> TourActionRow(
                        primaryLabel = stringResource(R.string.common_install),
                        onPrimary = onInstallTermux,
                        secondaryLabel = stringResource(R.string.common_skip),
                        onSecondary = onContinue,
                    )

                    TermuxSetupIssue.PermissionMissing -> {
                        TourActionRow(
                            primaryLabel = stringResource(R.string.common_grant_access),
                            onPrimary = onRequestPermission,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                        SecondaryTextAction(label = stringResource(R.string.onboarding_app_settings), onClick = onOpenAppPermissions)
                    }

                    TermuxSetupIssue.ExternalAppsDisabled -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) stringResource(R.string.common_open) else stringResource(R.string.onboarding_copy_and_open_termux),
                            onPrimary = if (setupState.previouslyConfigured) onOpenTermux else ::copyTermuxSetupCommandAndOpenTermux,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                    }

                    TermuxSetupIssue.DispatchFailed -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) stringResource(R.string.common_open) else stringResource(R.string.common_open_termux),
                            onPrimary = onOpenTermux,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                        if (!setupState.previouslyConfigured) {
                            SecondaryTextAction(label = stringResource(R.string.onboarding_copy_setup_command), onClick = ::copyTermuxSetupCommand)
                            SecondaryTextAction(label = stringResource(R.string.onboarding_termux_settings), onClick = onOpenTermuxSettings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootSetupPrompt(
    rootSetupState: RootSetupState,
    onUseRoot: () -> Unit,
    onContinueManual: () -> Unit,
    onRootConfigured: () -> Unit,
    onInstallTermux: () -> Unit,
) {

    StepLead(
        icon = Icons.Rounded.VerifiedUser,
        accent = when (rootSetupState.issue) {
            RootSetupIssue.Ready -> TourGreen
            RootSetupIssue.Available,
            RootSetupIssue.Running -> TourBlue
            RootSetupIssue.PermissionDenied,
            RootSetupIssue.TermuxNotInstalled,
            RootSetupIssue.Failed -> TourGold
            RootSetupIssue.Unknown,
            RootSetupIssue.Unavailable -> TourTextSecondary
        },
        title = stringResource(R.string.onboarding_root_shortcut),
        body = rootSetupPromptBody(rootSetupState),
    )

    when (rootSetupState.issue) {
        RootSetupIssue.Running -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = TourBlue,
                )
                Text(
                    text = stringResource(R.string.onboarding_waiting_root_authorization),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TourTextSecondary,
                )
            }
            SecondaryTextAction(
                label = stringResource(R.string.onboarding_continue_manual_setup),
                onClick = onContinueManual,
            )
        }

        RootSetupIssue.Ready -> TourActionRow(
            primaryLabel = stringResource(R.string.common_continue),
            onPrimary = onRootConfigured,
            secondaryLabel = stringResource(R.string.onboarding_manual_setup),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.TermuxNotInstalled -> TourActionRow(
            primaryLabel = stringResource(R.string.common_install),
            onPrimary = onInstallTermux,
            secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.Available -> {
            TourActionRow(
                primaryLabel = stringResource(R.string.onboarding_use_root_setup),
                onPrimary = onUseRoot,
                secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
                onSecondary = onContinueManual,
            )
        }

        RootSetupIssue.Unknown,
        RootSetupIssue.Unavailable,
        RootSetupIssue.PermissionDenied,
        RootSetupIssue.Failed -> TourActionRow(
            primaryLabel = stringResource(R.string.onboarding_use_root_setup),
            onPrimary = onUseRoot,
            secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
            onSecondary = onContinueManual,
        )
    }
}

@Composable
private fun rootSetupPromptBody(rootSetupState: RootSetupState): String = when (rootSetupState.issue) {
    RootSetupIssue.Unknown -> stringResource(R.string.onboarding_root_body_unknown)
    RootSetupIssue.Available -> stringResource(R.string.onboarding_root_body_available)
    RootSetupIssue.Running -> stringResource(R.string.onboarding_root_body_running)
    RootSetupIssue.Ready -> stringResource(R.string.onboarding_root_body_ready)
    RootSetupIssue.Unavailable -> stringResource(R.string.onboarding_root_body_unavailable)
    RootSetupIssue.PermissionDenied -> rootSetupState.detail.ifBlank {
        stringResource(R.string.onboarding_root_body_permission_denied)
    }
    RootSetupIssue.TermuxNotInstalled -> stringResource(R.string.onboarding_root_body_termux_not_installed)
    RootSetupIssue.Failed -> rootSetupState.detail.ifBlank {
        stringResource(R.string.onboarding_root_body_failed)
    }
}

@Composable
private fun AgentModeAuthorizationStep(
    stepIndex: Int,
    stepCount: Int,
    initialMethod: AgentModeAuthorizationMethod,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: (Boolean, AgentModeAuthorizationMethod) -> Unit,
) {

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_agent_mode_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.SmartToy,
                accent = TourGreen,
                title = stringResource(R.string.settings_agent_mode),
                body = stringResource(R.string.onboarding_agent_mode_choose_method),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AgentModeStageButton(
                    label = "Shizuku",
                    subtitle = stringResource(R.string.onboarding_agent_mode_shizuku_subtitle),
                    drawableRes = R.drawable.shizuku_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Shizuku) },
                )
                AgentModeStageButton(
                    label = "Root",
                    subtitle = stringResource(R.string.onboarding_agent_mode_root_subtitle),
                    drawableRes = R.drawable.root_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Root) },
                )
                AgentModeStageButton(
                    label = stringResource(R.string.common_skip),
                    subtitle = stringResource(R.string.onboarding_agent_mode_skip_subtitle),
                    drawableRes = R.drawable.skip_mark,
                    onClick = { onContinue(false, initialMethod) },
                )
            }
        }
    }
}

@Composable
private fun TavilyStep(
    stepIndex: Int,
    stepCount: Int,
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_tavily_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandStepLead(
                drawableRes = R.drawable.tavily_mark,
                title = "Tavily",
                body = stringResource(R.string.onboarding_tavily_optional_body),
            )
            MinimalInputField(
                label = stringResource(R.string.onboarding_api_key),
                value = value,
                placeholder = stringResource(R.string.onboarding_paste_it_here),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isSecret = true,
                onValueChange = onValueChange,
            )
            PrimaryActionButton(
                label = stringResource(R.string.common_done),
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun rememberStepContentVisible(
    key: Any,
    message: String,
): Boolean {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, message) {
        visible = false
        delay(messageRevealDuration(message) + MessageSettleDelayMillis)
        visible = true
    }
    return visible
}

@Composable
private fun StepTopBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TourTextPrimary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index + 1 == stepIndex) 20.dp else 7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (index + 1 == stepIndex) TourTextPrimary else TourDivider),
                )
            }
        }

        TextButton(onClick = onTopRight) {
            Text(
                text = topRightLabel,
                color = TourTextSecondary,
            )
        }
    }
}

@Composable
private fun StreamingStepMessage(
    playKey: Any,
    text: String,
) {
    var revealed by remember(playKey, text) { mutableStateOf("") }
    LaunchedEffect(playKey, text) {
        revealed = ""
        splitRevealUnits(text).forEach { unit ->
            delay(revealUnitDelay(unit))
            revealed += unit
        }
    }
    Text(
        text = revealed.ifEmpty { " " },
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
        color = TourTextPrimary,
    )
}

@Composable
private fun StepLead(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
) {
    OnboardingStepLead(icon = icon, accent = accent, title = title, body = body)
}

@Composable
private fun BrandStepLead(
    @DrawableRes drawableRes: Int,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMarkBadge(drawableRes = drawableRes)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TourTextPrimary,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TourTextSecondary,
        )
    }
}

@Composable
private fun TinyLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun ProviderStageButton(
    label: String,
    subtitle: String,
    provider: PiProviderDefinition,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProviderBrandBadge(provider = provider)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TourTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProviderBrandBadge(
    provider: PiProviderDefinition,
) {
    ProviderBrandIconBadge(provider = provider)
}

@Composable
private fun BrandMarkBadge(
    @DrawableRes drawableRes: Int,
) {
    val imageSize = if (drawableRes == R.drawable.openai_mark) 30.dp else 32.dp
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
    }
}

@Composable
private fun AgentModeStageButton(
    label: String,
    subtitle: String,
    @DrawableRes drawableRes: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMarkBadge(drawableRes = drawableRes)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TourTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModelOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) TourButton else TourSurface,
            contentColor = if (selected) Color.White else TourTextPrimary,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun MinimalInputField(
    label: String,
    value: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isSecret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    var passwordVisible by rememberSaveable(label) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TourTextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TourSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .tourBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TourTextPrimary),
            cursorBrush = SolidColor(TourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isSecret && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TourTextTertiary,
                            )
                        }
                        innerTextField()
                    }
                    if (isSecret) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = stringResource(
                                    if (passwordVisible) {
                                        R.string.common_hide_password
                                    } else {
                                        R.string.common_show_password
                                    }
                                ),
                                tint = TourTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun MinimalTextFieldValueInput(
    label: String,
    value: TextFieldValue,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (TextFieldValue) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TourTextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TourSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .tourBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TourTextPrimary),
            cursorBrush = SolidColor(TourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.text.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TourTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tourBringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
    isLoading: Boolean = false,
) {
    OnboardingPrimaryActionButton(
        label = label,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        isLoading = isLoading,
    )
}

@Composable
private fun TourActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
) {
    OnboardingActionRow(
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        primaryEnabled = primaryEnabled,
        primaryLoading = primaryLoading,
    )
}

@Composable
private fun SecondaryTextAction(
    label: String,
    onClick: () -> Unit,
    color: Color = TourTextPrimary,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = color,
        )
    }
}

private fun splitRevealUnits(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val units = mutableListOf<String>()
    val builder = StringBuilder()
    text.forEach { char ->
        builder.append(char)
        val shouldSplit = char == ' ' || char == '\n' || char == '.' || char == '!' || char == '?' || char == ','
        if (shouldSplit) {
            units += builder.toString()
            builder.clear()
        }
    }
    if (builder.isNotEmpty()) {
        units += builder.toString()
    }
    return units
}

private fun revealUnitDelay(unit: String): Long {
    val trimmed = unit.trim()
    if (trimmed.isEmpty()) return 18L
    if (trimmed.length == 1 && trimmed.first() in setOf('.', ',', '!', '?')) return 180L
    return (80L + trimmed.length * 18L).coerceIn(96L, 240L)
}

private fun messageRevealDuration(message: String): Long {
    val total = splitRevealUnits(message).sumOf(::revealUnitDelay)
    return total.coerceIn(MessageMinDurationMillis, MessageMaxDurationMillis)
}

internal fun prioritizedModelOptions(
    piProviderId: String?,
    cachedModels: List<String>,
): List<String> {
    val fetchedModels = cachedModels
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
    val orderedModels = fetchedModels
        .sortedWith(
            compareBy<String> { preferredModelRank(it) }
                .thenBy { providerModelRank(piProviderId, it) }
                .thenBy { it.lowercase() },
        )
    return orderedModels.withAutomaticChatModelFirst(piProviderId)
}

private fun preferredModelRank(model: String): Int {
    return automaticModelPriority(model, AutomaticModelPurpose.Chat) ?: Int.MAX_VALUE
}

private fun List<String>.withAutomaticChatModelFirst(
    piProviderId: String?,
): List<String> {
    if (isEmpty() || piProviderId == null) return this
    val definition = com.zhousl.aether.data.PiProviderCatalog.resolve(piProviderId)
    val onboardingConfig = LlmProviderConfig(
        id = "onboarding",
        providerId = definition.id,
        name = definition.displayName,
        piProviderId = definition.id,
        apiKey = "",
        baseUrl = definition.defaultBaseUrl,
        modelId = first(),
        cachedModels = this,
        enabledModelIds = this,
    )
    val options = listOf(onboardingConfig).availableModelOptions()
    val automaticModel = options.findModelOption(
        options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
    )?.modelId ?: return this
    if (preferredModelRank(automaticModel) > preferredModelRank(first())) return this
    return (listOf(automaticModel) + filterNot { it.equals(automaticModel, ignoreCase = true) })
        .distinctBy { it.lowercase() }
}

private fun providerModelRank(
    piProviderId: String?,
    model: String,
): Int = when (piProviderId) {
    "openai",
    "openai-codex" -> when {
        model.lowercase().contains("gpt") -> 0
        else -> 5
    }

    "anthropic" -> when {
        model.lowercase().contains("claude") -> 0
        else -> 5
    }

    "google",
    "google-vertex" -> when {
        model.lowercase().contains("gemini") -> 0
        else -> 5
    }

    else -> when {
        model.lowercase().contains("gpt") -> 0
        model.lowercase().contains("claude") -> 1
        model.lowercase().contains("gemini") -> 2
        else -> 5
    }
}

@Composable
private fun termuxStatusSentence(setupState: TermuxSetupState): String = when (setupState.issue) {
    TermuxSetupIssue.Ready -> stringResource(R.string.onboarding_termux_status_ready)
    TermuxSetupIssue.NotInstalled -> stringResource(R.string.onboarding_termux_status_not_installed)
    TermuxSetupIssue.PermissionMissing -> stringResource(R.string.onboarding_termux_status_permission_missing)
    TermuxSetupIssue.ExternalAppsDisabled -> {
        if (setupState.previouslyConfigured) {
            stringResource(R.string.onboarding_termux_status_external_apps_disabled_configured)
        } else {
            stringResource(R.string.onboarding_termux_status_external_apps_disabled)
        }
    }
    TermuxSetupIssue.DispatchFailed -> {
        if (setupState.previouslyConfigured) {
            stringResource(R.string.onboarding_termux_status_dispatch_failed_configured)
        } else {
            stringResource(R.string.onboarding_termux_status_dispatch_failed)
        }
    }
}

private fun termuxStatusColor(issue: TermuxSetupIssue): Color = when (issue) {
    TermuxSetupIssue.Ready -> TourGreen
    TermuxSetupIssue.NotInstalled -> TourGold
    TermuxSetupIssue.PermissionMissing -> TourGold
    TermuxSetupIssue.ExternalAppsDisabled -> TourPurple
    TermuxSetupIssue.DispatchFailed -> TourBlue
}
