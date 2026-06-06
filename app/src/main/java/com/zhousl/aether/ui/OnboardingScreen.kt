package com.zhousl.aether.ui

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AgentModeAuthorizationMethod
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProvider
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.OfficialVertexPreviewModels
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.requiresApiKey
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.data.RootSetupIssue
import com.zhousl.aether.data.RootSetupState
import com.zhousl.aether.data.usesOfficialVertexEndpoint
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

private const val StepFadeDuration = 560
private const val MessageTravelDuration = 1_520
private const val ContentFadeDuration = 920
private const val MessageSettleDelayMillis = 800L
private const val MessageMinDurationMillis = 1_000L
private const val MessageMaxDurationMillis = 3_300L

private val TourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val InitialOnboardingSteps = listOf(OnboardingStep.Landing, OnboardingStep.ProviderSetup)
private val FollowUpOnboardingSteps = listOf(
    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
    OnboardingStep.TavilySetup,
    OnboardingStep.SkillsOverview,
    OnboardingStep.McpOverview,
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

private fun tr(strings: AetherStrings, english: String, chinese: String): String =
    if (strings.appLanguage == AppLanguage.SimplifiedChinese) chinese else english

private enum class ProviderTourStage {
    PickProvider,
    Credentials,
    Model,
}

private fun OnboardingStep.flowSteps(): List<OnboardingStep> = when (this) {
    OnboardingStep.Landing,
    OnboardingStep.ProviderSetup -> InitialOnboardingSteps

    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
    OnboardingStep.TavilySetup,
    OnboardingStep.SkillsOverview,
    OnboardingStep.McpOverview -> FollowUpOnboardingSteps
}

@Composable
fun OnboardingScreen(
    initialStep: OnboardingStep,
    replayMode: Boolean,
    existingProviderConfig: LlmProviderConfig?,
    isFetchingModels: Boolean,
    termuxSetupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    tavilyApiKey: String,
    installedSkillCount: Int,
    mcpServerCount: Int,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
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
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
    onSaveAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onCompleteFollowUp: () -> Unit,
    onExploreSettings: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var currentStep by rememberSaveable(initialStep, replayMode) {
        mutableStateOf(initialStep)
    }
    var tavilyApiKeyValue by rememberSaveable(initialStep, replayMode, tavilyApiKey) {
        mutableStateOf(tavilyApiKey)
    }
    val formState = rememberProviderFormState(existingProviderConfig)
    var selectedProvider by rememberSaveable(initialStep, replayMode, existingProviderConfig?.id) {
        mutableStateOf(existingProviderConfig?.providerType)
    }
    val steps = remember(initialStep) { initialStep.flowSteps() }

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
                onPrimary = { currentStep = OnboardingStep.ProviderSetup },
                onSecondary = if (replayMode) onClose else onSkip,
            )

            OnboardingStep.ProviderSetup -> ProviderSetupStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                selectedProvider = selectedProvider,
                formState = formState,
                isFetchingModels = isFetchingModels,
                onSelectProvider = { provider ->
                    selectedProvider = provider
                    formState.applyProviderDefaults(provider)
                },
                onFetchModels = onFetchModels,
                onExit = if (replayMode) onClose else onSkip,
                onClose = if (replayMode) onClose else onSkip,
                onReturnToLanding = { currentStep = OnboardingStep.Landing },
                onComplete = { onCompleteProviderSetup(formState.buildConfig()) },
            )

            OnboardingStep.TermuxSetup -> TermuxStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                setupState = termuxSetupState,
                rootSetupState = rootSetupState,
                onClose = onClose,
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
                    currentStep = if (termuxSetupState.isReady) {
                        OnboardingStep.AgentModeAuthorization
                    } else {
                        OnboardingStep.TermuxSetup
                    }
                },
                onClose = onClose,
                onContinue = {
                    val trimmed = tavilyApiKeyValue.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveTavilyApiKey(trimmed)
                    }
                    currentStep = OnboardingStep.SkillsOverview
                },
            )

            OnboardingStep.SkillsOverview -> SummaryStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                message = tr(strings, "Later, you can add Skills so Aether remembers reusable workflows you like.", "之后你可以添加技能，让 Aether 记住你喜欢的可复用工作流。"),
                title = tr(strings, "Skills", "技能"),
                icon = Icons.Rounded.Extension,
                accent = TourGold,
                lineOne = if (installedSkillCount == 0) {
                    tr(strings, "You do not need this now.", "现在不需要也没关系。")
                } else {
                    tr(strings, "$installedSkillCount Skills are already installed.", "已经安装了 $installedSkillCount 个技能。")
                },
                lineTwo = tr(strings, "Use them for prompts, checks, and templates.", "它们可以用来放提示词、检查项和模板。"),
                chips = listOf(tr(strings, "Prompts", "提示词"), tr(strings, "Checks", "检查项"), tr(strings, "Templates", "模板")),
                primaryLabel = strings.continueLabel,
                onPrimary = { currentStep = OnboardingStep.McpOverview },
                secondaryLabel = strings.back,
                onSecondary = { currentStep = OnboardingStep.TavilySetup },
                onClose = onClose,
            )

            OnboardingStep.McpOverview -> SummaryStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                message = tr(strings, "If you want live docs, search, or APIs later, you can connect MCP servers in Settings.", "如果之后你想接入实时文档、搜索或 API，可以在设置里连接 MCP 服务器。"),
                title = "MCP",
                icon = Icons.Rounded.Cloud,
                accent = TourBlue,
                lineOne = if (mcpServerCount == 0) {
                    tr(strings, "You can leave this for later.", "这一步也可以留到以后。")
                } else {
                    tr(strings, "$mcpServerCount MCP servers are already available.", "已经有 $mcpServerCount 个 MCP 服务器可用。")
                },
                lineTwo = tr(strings, "This is where Aether grows beyond local tools.", "这里是 Aether 超出本地工具能力的入口。"),
                chips = listOf(tr(strings, "Docs", "文档"), tr(strings, "Search", "搜索"), "APIs"),
                primaryLabel = strings.done,
                onPrimary = onCompleteFollowUp,
                secondaryLabel = tr(strings, "Open settings", "打开设置"),
                onSecondary = onExploreSettings,
                tertiaryLabel = strings.back,
                onTertiary = { currentStep = OnboardingStep.SkillsOverview },
                onClose = onClose,
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
    val strings = rememberAetherStrings()
    var visible by remember(stepIndex, replayMode) { mutableStateOf(false) }
    LaunchedEffect(stepIndex, replayMode) {
        delay(180)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TourBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            TourChromeBar(
                stepIndex = stepIndex,
                stepCount = stepCount,
                onBack = null,
                topRightLabel = if (replayMode) stringResource(R.string.common_close) else stringResource(R.string.common_skip),
                onTopRight = onSecondary,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, top = 4.dp, end = 28.dp, bottom = 20.dp),
            ) {
                Spacer(modifier = Modifier.weight(0.72f))
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = ContentFadeDuration,
                            easing = TourEasing,
                        )
                    ),
                    label = "landing_content",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.aether_mark),
                            contentDescription = stringResource(R.string.onboarding_aether_icon),
                            modifier = Modifier
                                .size(104.dp),
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "欢迎使用 Aether" else "Welcome to Aether",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TourTextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "在设备上运行、可与一切协作的智能体。" else "The on-device agent that works with everything.",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TourTextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1.28f))
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = ContentFadeDuration,
                            delayMillis = 220,
                            easing = TourEasing,
                        )
                    ),
                    label = "landing_actions",
                ) {
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TourButton,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.onboarding_get_started))
                    }
                }
            }
        }
    }
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
    val pageKey = remember(stepIndex, stepCount, message) { "$stepIndex/$stepCount:$message" }
    val contentVisible = rememberStepContentVisible(pageKey, message)
    val topPadding by animateDpAsState(
        targetValue = if (contentVisible) 56.dp else 168.dp,
        animationSpec = tween(
            durationMillis = MessageTravelDuration,
            easing = TourEasing,
        ),
        label = "tour_message_travel",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TourBackground),
    ) {
        AnimatedVisibility(
            visible = !isExiting,
            enter = fadeIn(animationSpec = tween(durationMillis = 0)),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 280,
                    easing = TourEasing,
                )
            ),
            label = "step_page_visibility",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                TourChromeBar(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    onBack = onBack,
                    topRightLabel = topRightLabel,
                    onTopRight = onTopRight,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, top = 12.dp, end = 28.dp, bottom = 20.dp),
                ) {
                    Spacer(modifier = Modifier.height(topPadding))
                    StreamingStepMessage(
                        playKey = pageKey,
                        text = message,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = ContentFadeDuration,
                                delayMillis = 180,
                                easing = TourEasing,
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
                        label = "step_content_fade",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            content = content,
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderSetupStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    selectedProvider: LlmProvider?,
    formState: ProviderFormState,
    isFetchingModels: Boolean,
    onSelectProvider: (LlmProvider) -> Unit,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    onReturnToLanding: () -> Unit,
    onComplete: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var stage by rememberSaveable(stepIndex, replayMode) { mutableStateOf(ProviderTourStage.PickProvider) }
    var isFinishing by rememberSaveable(stepIndex, replayMode) { mutableStateOf(false) }
    val provider = selectedProvider
    val isLoadingModels = formState.isFetchingModelsLocally || isFetchingModels
    val modelChoices = remember(provider, formState.baseUrl, formState.cachedModels) {
        prioritizedModelOptions(
            provider = provider,
            baseUrl = formState.baseUrl,
            cachedModels = formState.cachedModels,
        )
    }
    val canContinueFromCredentials = provider != null &&
        formState.baseUrl.trim().isNotBlank() &&
        (!provider.requiresApiKey(formState.baseUrl) || formState.apiKey.trim().isNotBlank())

    val message = when (stage) {
        ProviderTourStage.PickProvider -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "首先，我们来选择你的模型提供方。" else "First, let's choose your model provider."
        ProviderTourStage.Credentials -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "很好。填入密钥和基础 URL，然后我会获取模型。" else "Great. Add your key and base URL. I'll fetch the models after this."
        ProviderTourStage.Model -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "选好模型后，我们就可以直接进入聊天。" else "Pick the model you want, and then we can go straight into chat."
    }
    val backAction: (() -> Unit)? = when (stage) {
        ProviderTourStage.PickProvider -> onReturnToLanding
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
                ProviderTourStage.PickProvider -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ProviderStageButton(
                            label = "OpenAI Responses",
                            subtitle = "OpenAI Responses API",
                            provider = LlmProvider.OpenAiResponses,
                            onClick = {
                                onSelectProvider(LlmProvider.OpenAiResponses)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        ProviderStageButton(
                            label = "OpenAI Chat Com...",
                            subtitle = "OpenAI Chat Completions API",
                            provider = LlmProvider.OpenAiCompatible,
                            onClick = {
                                onSelectProvider(LlmProvider.OpenAiCompatible)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        ProviderStageButton(
                            label = "Vertex",
                            subtitle = "Google Cloud Vertex API",
                            provider = LlmProvider.VertexExpress,
                            onClick = {
                                onSelectProvider(LlmProvider.VertexExpress)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        ProviderStageButton(
                            label = "Anthropic",
                            subtitle = "Anthropic Messages API",
                            provider = LlmProvider.AnthropicMessages,
                            onClick = {
                                onSelectProvider(LlmProvider.AnthropicMessages)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "你以后可以在设置中更改。" else "You can change this later in Settings.",
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
                            text = when (provider) {
                                LlmProvider.OpenAiResponses -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在使用 OpenAI Responses" else "Using OpenAI Responses"
                                LlmProvider.OpenAiCompatible -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在使用 OpenAI Chat Completions" else "Using OpenAI Chat Completions"
                                LlmProvider.VertexExpress -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在使用 Vertex" else "Using Vertex"
                                LlmProvider.AnthropicMessages -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在使用 Anthropic" else "Using Anthropic"
                                null -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "选择请求格式" else "Choose a request format"
                            },
                            color = when (provider) {
                                LlmProvider.VertexExpress -> TourBlue
                                LlmProvider.AnthropicMessages -> TourPurple
                                else -> TourGreen
                            },
                        )
                        MinimalInputField(
                            label = stringResource(R.string.onboarding_api_key),
                            value = formState.apiKey,
                            placeholder = if (provider?.requiresApiKey(formState.baseUrl) == true) {
                                stringResource(R.string.onboarding_required_for_this_format)
                            } else {
                                stringResource(R.string.onboarding_optional)
                            },
                            onValueChange = { formState.apiKey = it },
                        )
                        MinimalInputField(
                            label = stringResource(R.string.onboarding_base_url),
                            value = formState.baseUrl,
                            placeholder = "https://...",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { formState.baseUrl = it },
                        )
                        PrimaryActionButton(
                            label = if (isLoadingModels) stringResource(R.string.onboarding_loading_models) else stringResource(R.string.common_next),
                            enabled = canContinueFromCredentials && !isLoadingModels,
                            onClick = {
                                formState.isFetchingModelsLocally = true
                                onFetchModels(formState.buildConfig()) { models ->
                                    val ordered = prioritizedModelOptions(
                                        provider = provider,
                                        baseUrl = formState.baseUrl,
                                        cachedModels = models,
                                    )
                                    formState.cachedModels = ordered
                                    formState.enabledModelIds = ordered
                                    if (ordered.isNotEmpty()) {
                                        formState.modelId = ordered.first()
                                    }
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
                            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "如果我能找到更合适的模型，我会把它们排在前面。" else "I'll place the best matches first when I can find them.",
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
                                        formState.enabledModelIds = (listOf(model) + formState.enabledModelIds)
                                            .map(String::trim)
                                            .filter(String::isNotEmpty)
                                            .distinct()
                                    },
                                )
                            }
                        }
                        MinimalInputField(
                            label = stringResource(R.string.onboarding_model),
                            value = if (modelChoices.any { it.equals(formState.modelId.trim(), ignoreCase = true) }) {
                                ""
                            } else {
                                formState.modelId
                            },
                            placeholder = stringResource(R.string.onboarding_or_type_your_own_model),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { value ->
                                formState.modelId = value
                                val trimmed = value.trim()
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
                            enabled = provider != null && formState.isValid(emptySet()),
                            onClick = { isFinishing = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxStep(
    stepIndex: Int,
    stepCount: Int,
    setupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    onClose: () -> Unit,
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
    val strings = rememberAetherStrings()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var shouldAutoContinue by rememberSaveable(stepIndex) { mutableStateOf(!setupState.isReady) }
    var showRootSetupPrompt by rememberSaveable(stepIndex) { mutableStateOf(true) }
    fun copyTermuxSetupCommand() {
        clipboardManager.setText(AnnotatedString(TermuxContract.ExternalAppsSetupCommand))
        Toast.makeText(
            context,
            if (strings.appLanguage == AppLanguage.SimplifiedChinese) "已复制 Termux 配置命令" else "Termux setup command copied",
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
        message = "Great. Now let’s give Aether access to your device so tools can run locally.",
        onBack = null,
        topRightLabel = strings.close,
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
                    title = strings.termux,
                    body = termuxStatusSentence(setupState, strings.appLanguage),
                )
                when (setupState.issue) {
                    TermuxSetupIssue.Ready -> TourActionRow(
                        primaryLabel = strings.continueLabel,
                        onPrimary = onContinue,
                        secondaryLabel = strings.refresh,
                        onSecondary = onRefresh,
                    )

                    TermuxSetupIssue.NotInstalled -> TourActionRow(
                        primaryLabel = strings.install,
                        onPrimary = onInstallTermux,
                        secondaryLabel = strings.skip,
                        onSecondary = onContinue,
                    )

                    TermuxSetupIssue.PermissionMissing -> {
                        TourActionRow(
                            primaryLabel = strings.grantAccess,
                            onPrimary = onRequestPermission,
                            secondaryLabel = strings.skip,
                            onSecondary = onContinue,
                        )
                        SecondaryTextAction(label = tr(strings, "App settings", "应用设置"), onClick = onOpenAppPermissions)
                    }

                    TermuxSetupIssue.ExternalAppsDisabled -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) tr(strings, "Open", "\u6253\u5f00") else tr(strings, "Copy and Open Termux", "复制并打开 Termux"),
                            onPrimary = if (setupState.previouslyConfigured) onOpenTermux else ::copyTermuxSetupCommandAndOpenTermux,
                            secondaryLabel = strings.skip,
                            onSecondary = onContinue,
                        )
                    }

                    TermuxSetupIssue.DispatchFailed -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) tr(strings, "Open", "\u6253\u5f00") else strings.openTermux,
                            onPrimary = onOpenTermux,
                            secondaryLabel = strings.skip,
                            onSecondary = onContinue,
                        )
                        if (!setupState.previouslyConfigured) {
                            SecondaryTextAction(label = tr(strings, "Copy setup command", "复制配置命令"), onClick = ::copyTermuxSetupCommand)
                            SecondaryTextAction(label = tr(strings, "Termux settings", "Termux 设置"), onClick = onOpenTermuxSettings)
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
    val strings = rememberAetherStrings()
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
        title = tr(strings, "Root shortcut", "Root 快捷配置"),
        body = rootSetupPromptBody(rootSetupState, strings),
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
                    text = tr(strings, "Waiting for root authorization...", "正在等待 Root 授权..."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TourTextSecondary,
                )
            }
            SecondaryTextAction(
                label = tr(strings, "Continue manual setup", "继续手动配置"),
                onClick = onContinueManual,
            )
        }

        RootSetupIssue.Ready -> TourActionRow(
            primaryLabel = strings.continueLabel,
            onPrimary = onRootConfigured,
            secondaryLabel = tr(strings, "Manual setup", "手动配置"),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.TermuxNotInstalled -> TourActionRow(
            primaryLabel = strings.install,
            onPrimary = onInstallTermux,
            secondaryLabel = tr(strings, "Continue manual setup", "继续手动配置"),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.Available -> {
            TourActionRow(
                primaryLabel = tr(strings, "Use Root setup", "使用 Root 配置"),
                onPrimary = onUseRoot,
                secondaryLabel = tr(strings, "Continue manual setup", "继续手动配置"),
                onSecondary = onContinueManual,
            )
        }

        RootSetupIssue.Unknown,
        RootSetupIssue.Unavailable,
        RootSetupIssue.PermissionDenied,
        RootSetupIssue.Failed -> TourActionRow(
            primaryLabel = tr(strings, "Use Root setup", "使用 Root 配置"),
            onPrimary = onUseRoot,
            secondaryLabel = tr(strings, "Continue manual setup", "继续手动配置"),
            onSecondary = onContinueManual,
        )
    }
}

private fun rootSetupPromptBody(
    rootSetupState: RootSetupState,
    strings: AetherStrings,
): String = when (rootSetupState.issue) {
    RootSetupIssue.Unknown -> tr(
        strings,
        "If this phone has Root, Aether can configure Termux and Agent Mode automatically.",
        "如果这台手机有 Root，Aether 可以自动配置 Termux 和 Agent Mode。",
    )

    RootSetupIssue.Available -> tr(
        strings,
        "Root is available. Grant su to Aether to skip the manual Termux and Agent Mode steps.",
        "已检测到 Root。授予 Aether su 后，可以跳过后续 Termux 和 Agent Mode 手动步骤。",
    )

    RootSetupIssue.Running -> tr(
        strings,
        "Aether is enabling Termux external apps, granting command permission, and selecting Root Agent Mode.",
        "Aether 正在启用 Termux 外部应用、授予命令权限，并选择 Root Agent Mode。",
    )

    RootSetupIssue.Ready -> tr(
        strings,
        "Root setup is complete. Termux and Agent Mode are ready.",
        "Root 配置已完成，Termux 和 Agent Mode 已就绪。",
    )

    RootSetupIssue.Unavailable -> tr(
        strings,
        "If you have Root, try the automatic setup. Otherwise continue with the existing manual flow.",
        "如果你有 Root，可以尝试自动配置；否则继续现有的手动流程。",
    )

    RootSetupIssue.PermissionDenied -> rootSetupState.detail.ifBlank {
        tr(strings, "Root access was denied or timed out.", "Root 权限被拒绝或请求超时。")
    }

    RootSetupIssue.TermuxNotInstalled -> tr(
        strings,
        "Install Termux first, then Aether can finish this setup with Root.",
        "请先安装 Termux，之后 Aether 可以通过 Root 完成配置。",
    )

    RootSetupIssue.Failed -> rootSetupState.detail.ifBlank {
        tr(strings, "Root setup did not complete. You can retry or continue manually.", "Root 配置未完成。你可以重试，或继续手动配置。")
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
    val strings = rememberAetherStrings()
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = tr(strings, "Agent Mode is optional. It needs Root or Shizuku to control an isolated Android display.", "Agent 模式是可选项。它需要 Root 或 Shizuku 来控制隔离的 Android 显示。"),
        onBack = onBack,
        topRightLabel = strings.close,
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.SmartToy,
                accent = TourGreen,
                title = strings.agentMode,
                body = tr(strings, "Choose an authorization method, or skip this for now.", "选择一种授权方式，或者暂时先跳过。"),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AgentModeStageButton(
                    label = "Shizuku",
                    subtitle = tr(strings, "Use the elevated Shizuku service when the app is installed.", "安装应用后使用提权的 Shizuku 服务。"),
                    drawableRes = R.drawable.shizuku_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Shizuku) },
                )
                AgentModeStageButton(
                    label = "Root",
                    subtitle = tr(strings, "Use a root shell for privileged input on rooted devices.", "在已 root 的设备上使用 root shell 进行特权输入。"),
                    drawableRes = R.drawable.root_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Root) },
                )
                AgentModeStageButton(
                    label = strings.skip,
                    subtitle = tr(strings, "Leave Agent Mode off and enable it later from Settings.", "先关闭 Agent 模式，之后再到设置中启用。"),
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
    val strings = rememberAetherStrings()
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = tr(strings, "If you want fresher web answers later, you can add search here.", "如果你之后想获得更新鲜的网页答案，可以在这里补上搜索能力。"),
        onBack = onBack,
        topRightLabel = strings.close,
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandStepLead(
                drawableRes = R.drawable.tavily_mark,
                title = "Tavily",
                body = tr(strings, "This is optional. URL fetch already works without it.", "这是可选项。即使不填，URL 抓取也已经可以使用。"),
            )
            MinimalInputField(
                label = tr(strings, "API key", "API 密钥"),
                value = value,
                placeholder = tr(strings, "Paste it here", "粘贴到这里"),
                onValueChange = onValueChange,
            )
            PrimaryActionButton(
                label = strings.continueLabel,
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun SummaryStep(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    title: String,
    icon: ImageVector,
    accent: Color,
    lineOne: String,
    lineTwo: String,
    chips: List<String>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val strings = rememberAetherStrings()
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = onTertiary,
        topRightLabel = strings.close,
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = icon,
                accent = accent,
                title = title,
                body = lineOne,
            )
            Text(
                text = lineTwo,
                style = MaterialTheme.typography.bodyMedium,
                color = TourTextSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chips.forEach { chip ->
                    PassiveChip(label = chip)
                }
            }
            PrimaryActionButton(
                label = primaryLabel,
                onClick = onPrimary,
            )
            if (secondaryLabel != null && onSecondary != null) {
                SecondaryTextAction(
                    label = secondaryLabel,
                    onClick = onSecondary,
                )
            }
            if (tertiaryLabel != null && onTertiary != null) {
                SecondaryTextAction(
                    label = tertiaryLabel,
                    onClick = onTertiary,
                    color = TourTextSecondary,
                )
            }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                )
            }
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
    provider: LlmProvider,
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
    provider: LlmProvider,
) {
    BrandMarkBadge(drawableRes = providerBrandDrawable(provider))
}

@DrawableRes
private fun providerBrandDrawable(provider: LlmProvider): Int = when (provider) {
    LlmProvider.OpenAiResponses -> R.drawable.openai_mark
    LlmProvider.OpenAiCompatible -> R.drawable.openai_mark
    LlmProvider.VertexExpress -> R.drawable.googlecloud_mark
    LlmProvider.AnthropicMessages -> R.drawable.anthropic_mark
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
private fun PassiveChip(
    label: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TourSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TourTextPrimary,
        )
    }
}

@Composable
private fun MinimalInputField(
    label: String,
    value: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
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
                .tourBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TourTextPrimary),
            cursorBrush = SolidColor(TourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                ) {
                    if (value.isBlank()) {
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourButton,
            contentColor = Color.White,
            disabledContainerColor = TourDivider,
            disabledContentColor = TourTextSecondary,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.8.dp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun TourActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryActionButton(
            label = primaryLabel,
            modifier = Modifier.weight(1f),
            onClick = onPrimary,
        )
        TextButton(
            onClick = onSecondary,
            modifier = Modifier.weight(0.62f),
        ) {
            Text(
                text = secondaryLabel,
                color = TourTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

private fun prioritizedModelOptions(
    provider: LlmProvider?,
    baseUrl: String,
    cachedModels: List<String>,
): List<String> {
    val fallback = when (provider) {
        LlmProvider.OpenAiResponses -> listOf(
            "gpt-5.5",
            "gpt-5.4",
            "gpt-5.4-mini",
        )

        LlmProvider.OpenAiCompatible -> listOf(
            "gpt-5.5",
            "gpt-5.4",
            "claude-4.6-sonnet",
            "gemini-3-flash",
            "gemini-3.1-pro",
        )

        LlmProvider.VertexExpress -> listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
        )

        LlmProvider.AnthropicMessages -> listOf(
            "claude-opus-4-5",
            "claude-sonnet-4-5",
            "claude-haiku-4-5",
        )

        null -> listOf(
            "gpt-5.5",
            "gpt-5.4",
            "gemini-3-flash",
            "gemini-3.1-pro",
            "claude-4.6-sonnet",
        )
    }
    val patchedModels = if (provider != null && usesOfficialVertexEndpoint(provider, baseUrl)) {
        OfficialVertexPreviewModels
    } else {
        emptyList()
    }
    val orderedModels = (cachedModels + patchedModels + fallback)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .sortedWith(
            compareBy<String> { preferredModelRank(it) }
                .thenBy { providerModelRank(provider, it) }
                .thenBy { it.lowercase() },
        )
    return orderedModels.withAutomaticChatModelFirst(provider, baseUrl)
}

private fun preferredModelRank(model: String): Int {
    val normalized = model.lowercase()
    return when {
        normalized.contains("gpt-5.5") -> 0
        normalized.contains("gpt-5.4") -> 1
        normalized.contains("gemini-3-flash") || normalized.contains("gemini 3 flash") -> 2
        normalized.contains("gemini-3.1-pro") || normalized.contains("gemini 3.1 pro") -> 3
        normalized.contains("claude-4.6-sonnet") || normalized.contains("claude 4.6 sonnet") -> 4
        else -> 10
    }
}

private fun List<String>.withAutomaticChatModelFirst(
    provider: LlmProvider?,
    baseUrl: String,
): List<String> {
    if (isEmpty() || provider == null) return this
    val onboardingConfig = LlmProviderConfig(
        id = "onboarding",
        providerId = provider.storageValue,
        name = provider.displayName,
        providerType = provider,
        apiKey = "",
        baseUrl = baseUrl,
        modelId = first(),
        cachedModels = this,
        enabledModelIds = this,
    )
    val options = listOf(onboardingConfig).availableModelOptions()
    val automaticModel = options.findModelOption(
        options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat)
    )?.modelId ?: return this
    return (listOf(automaticModel) + filterNot { it.equals(automaticModel, ignoreCase = true) })
        .distinctBy { it.lowercase() }
}

private fun providerModelRank(
    provider: LlmProvider?,
    model: String,
): Int = when (provider) {
    LlmProvider.OpenAiResponses -> when {
        model.lowercase().contains("gpt") -> 0
        else -> 5
    }

    LlmProvider.OpenAiCompatible -> when {
        model.lowercase().contains("gpt") -> 0
        model.lowercase().contains("claude") -> 1
        model.lowercase().contains("gemini") -> 2
        else -> 5
    }

    LlmProvider.VertexExpress -> when {
        model.lowercase().contains("gemini") -> 0
        else -> 5
    }

    LlmProvider.AnthropicMessages -> when {
        model.lowercase().contains("claude") -> 0
        else -> 5
    }

    null -> 5
}

private fun termuxStatusSentence(
    setupState: TermuxSetupState,
    appLanguage: AppLanguage,
): String = setupState.detail.ifBlank {
    when (setupState.issue) {
        TermuxSetupIssue.Ready -> if (appLanguage == AppLanguage.SimplifiedChinese) "本地工具已就绪。" else "Local tools are ready."
        TermuxSetupIssue.NotInstalled -> if (appLanguage == AppLanguage.SimplifiedChinese) "先安装 Termux，然后再回到这里。" else "Install Termux first, then come back here."
        TermuxSetupIssue.PermissionMissing -> if (appLanguage == AppLanguage.SimplifiedChinese) "在系统权限中授予“在 Termux 环境中运行命令”。" else "Grant the \"Run commands in Termux environment\" permission in Android settings."
        TermuxSetupIssue.ExternalAppsDisabled -> if (setupState.previouslyConfigured) {
            if (appLanguage == AppLanguage.SimplifiedChinese) {
                "Termux \u4f3c\u4e4e\u4e0d\u5728\u540e\u53f0\u8fd0\u884c\u3002\u6253\u5f00 Termux \u5e76\u4fdd\u6301\u5b83\u5728\u540e\u53f0\u8fd0\u884c\uff0c\u7136\u540e\u56de\u5230 Aether \u5237\u65b0\u72b6\u6001\u3002"
            } else {
                "Termux seems to be not running in the background. Open Termux and keep it running in the background, then return to Aether and refresh."
            }
        } else if (appLanguage == AppLanguage.SimplifiedChinese) "复制配置命令，在 Termux 中粘贴运行后再回来刷新。" else "Copy the setup command, paste it in Termux, then return and refresh."
        TermuxSetupIssue.DispatchFailed -> if (setupState.previouslyConfigured) {
            if (appLanguage == AppLanguage.SimplifiedChinese) {
                "Termux \u4f3c\u4e4e\u4e0d\u5728\u540e\u53f0\u8fd0\u884c\u3002\u6253\u5f00 Termux \u5e76\u4fdd\u6301\u5b83\u5728\u540e\u53f0\u8fd0\u884c\uff0c\u7136\u540e\u56de\u5230 Aether \u5237\u65b0\u72b6\u6001\u3002"
            } else {
                "Termux seems to be not running in the background. Open Termux and keep it running in the background, then return to Aether and refresh."
            }
        } else if (appLanguage == AppLanguage.SimplifiedChinese) "先打开一次 Termux，然后回到这里刷新。" else "Open Termux once, then refresh here."
    }
}

private fun termuxStatusColor(issue: TermuxSetupIssue): Color = when (issue) {
    TermuxSetupIssue.Ready -> TourGreen
    TermuxSetupIssue.NotInstalled -> TourGold
    TermuxSetupIssue.PermissionMissing -> TourGold
    TermuxSetupIssue.ExternalAppsDisabled -> TourPurple
    TermuxSetupIssue.DispatchFailed -> TourBlue
}
