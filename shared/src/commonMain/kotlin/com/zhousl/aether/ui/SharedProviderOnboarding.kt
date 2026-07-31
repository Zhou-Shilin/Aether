package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.PiProviderDefinition
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.automaticModelPriority
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val SharedProviderContentFadeDuration = 920
private val SharedProviderTourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

private val SharedProviderTourTextPrimary: Color
    get() = AetherOnSurface
private val SharedProviderTourTextSecondary: Color
    get() = AetherOnSurfaceVariant
private val SharedProviderTourTextTertiary: Color
    get() = AetherOnSurfaceVariant.copy(alpha = 0.72f)
private val SharedProviderTourSurface: Color
    get() = AetherSurfaceHigh
private val SharedProviderTourButton: Color
    get() = Color.Black
private val SharedProviderTourGreen: Color
    get() = AetherSecondary

private enum class SharedProviderTourStage {
    PickAuthentication,
    PickProvider,
    Credentials,
    Model,
}

/**
 * Provider setup used by the initial shared onboarding flow. Its stages and
 * navigation intentionally mirror Android's ProviderSetupStep.
 */
@Composable
fun SharedProviderOnboardingStep(
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
    onTimelineStepSelected: (OnboardingTimelineStep) -> Unit = {},
) {
    var stage by rememberSaveable(stepIndex, replayMode) {
        mutableStateOf(SharedProviderTourStage.PickAuthentication)
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
        SharedProviderTourStage.PickAuthentication ->
            stringResource(Res.string.onboarding_provider_auth_message)
        SharedProviderTourStage.PickProvider ->
            stringResource(Res.string.onboarding_provider_pick_message)
        SharedProviderTourStage.Credentials ->
            stringResource(Res.string.onboarding_provider_credentials_message)
        SharedProviderTourStage.Model ->
            stringResource(Res.string.onboarding_provider_model_message)
    }
    val backAction: (() -> Unit)? = when (stage) {
        SharedProviderTourStage.PickAuthentication -> onReturnToLanding
        SharedProviderTourStage.PickProvider -> {
            { stage = SharedProviderTourStage.PickAuthentication }
        }
        SharedProviderTourStage.Credentials -> {
            { stage = SharedProviderTourStage.PickProvider }
        }
        SharedProviderTourStage.Model -> {
            { stage = SharedProviderTourStage.Credentials }
        }
    }

    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(320)
            onComplete()
        }
    }

    val providerPickerContent: @Composable ColumnScope.() -> Unit = {
        SharedProviderPickerContent(
            providerSearch = providerSearch,
            onProviderSearchChange = { providerSearch = it },
            providerChoices = providerChoices,
            onProviderSelected = { provider ->
                onClearAuthState()
                formState.applyProviderDefaults(provider)
                formState.setAuthMethod(selectedAuthMethod)
                stage = SharedProviderTourStage.Credentials
            },
        )
    }
    val credentialsContent: @Composable ColumnScope.() -> Unit = {
        SharedProviderCredentialsContent(
            formState = formState,
            definition = definition,
            authState = authState,
            isLoadingModels = isLoadingModels,
            canContinue = canContinueFromCredentials,
            onStartProviderLogin = onStartProviderLogin,
            onSubmitAuthPrompt = onSubmitAuthPrompt,
            onClearAuthState = onClearAuthState,
            onContinue = {
                formState.isFetchingModelsLocally = true
                onFetchModels(formState.buildConfig()) { models ->
                    val ordered = prioritizedSharedProviderModelOptions(
                        piProviderId = definition.id,
                        cachedModels = models,
                    )
                    formState.cachedModels = models
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinctBy { it.lowercase() }
                    formState.enabledModelIds = ordered
                    formState.modelId = ordered.firstOrNull().orEmpty()
                    customModelValue = TextFieldValue()
                    formState.isFetchingModelsLocally = false
                    stage = SharedProviderTourStage.Model
                }
            },
        )
    }
    val timelineSpec = OnboardingTimelineSpec(
        activeStep = OnboardingTimelineStep.Provider,
        providerSubstep = stage.ordinal,
        onStepSelected = onTimelineStepSelected,
        onProviderSubstepSelected = { selected ->
            SharedProviderTourStage.entries.getOrNull(selected)?.let { stage = it }
        },
    )

    OnboardingConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = backAction,
        topRightLabel = if (replayMode) {
            stringResource(Res.string.common_close)
        } else {
            stringResource(Res.string.common_skip)
        },
        onTopRight = if (replayMode) onClose else onExit,
        isExiting = isFinishing,
        timelineSpec = timelineSpec,
        widePrimaryMessage = if (stage == SharedProviderTourStage.Credentials) {
            stringResource(Res.string.onboarding_provider_pick_message)
        } else {
            null
        },
        widePrimaryContent = if (stage == SharedProviderTourStage.Credentials) {
            providerPickerContent
        } else {
            null
        },
        wideAuxiliaryVisible = stage == SharedProviderTourStage.Credentials,
        wideAuxiliaryContent = {
            SharedProviderConfigurationPane(
                title = stringResource(
                    Res.string.onboarding_provider_configuration_title,
                    definition.displayName,
                ),
                content = credentialsContent,
            )
        },
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = SharedProviderContentFadeDuration,
                        delayMillis = 160,
                        easing = SharedProviderTourEasing,
                    ),
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 160,
                        easing = SharedProviderTourEasing,
                    ),
                )
            },
            label = "provider_stage_transition",
        ) { currentStage ->
            when (currentStage) {
                SharedProviderTourStage.PickAuthentication -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = stringResource(Res.string.provider_add_subscription),
                            subtitle = stringResource(Res.string.provider_add_subscription_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.OAuth.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.OAuth)
                                stage = SharedProviderTourStage.PickProvider
                            },
                        )
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.Key,
                            title = stringResource(Res.string.provider_add_api_key),
                            subtitle = stringResource(Res.string.provider_add_api_key_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.ApiKey.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.ApiKey)
                                stage = SharedProviderTourStage.PickProvider
                            },
                        )
                        ProviderWizardChoiceRow(
                            icon = Icons.Rounded.Cloud,
                            title = stringResource(Res.string.provider_add_environment),
                            subtitle = stringResource(Res.string.provider_add_environment_description),
                            onClick = {
                                selectedAuthMethodName = ProviderAuthMethod.Ambient.name
                                providerSearch = ""
                                formState.setAuthMethod(ProviderAuthMethod.Ambient)
                                stage = SharedProviderTourStage.PickProvider
                            },
                        )
                    }
                }

                SharedProviderTourStage.PickProvider -> {
                    providerPickerContent()
                }

                SharedProviderTourStage.Credentials -> {
                    credentialsContent()
                }

                SharedProviderTourStage.Model -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_best_models_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = SharedProviderTourTextSecondary,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            modelChoices.take(8).forEach { model ->
                                SharedProviderModelOptionButton(
                                    label = model,
                                    selected = formState.modelId.trim().equals(model, ignoreCase = true),
                                    onClick = {
                                        formState.modelId = model
                                        customModelValue = TextFieldValue()
                                        formState.enabledModelIds =
                                            (listOf(model) + formState.enabledModelIds)
                                                .map(String::trim)
                                                .filter(String::isNotEmpty)
                                                .distinct()
                                    },
                                )
                            }
                        }
                        SharedProviderMinimalTextFieldValueInput(
                            label = stringResource(Res.string.onboarding_model),
                            value = customModelValue,
                            placeholder = stringResource(Res.string.onboarding_or_type_your_own_model),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { value ->
                                customModelValue = value
                                formState.modelId = value.text
                                val trimmed = value.text.trim()
                                if (trimmed.isNotEmpty()) {
                                    formState.enabledModelIds =
                                        (listOf(trimmed) + formState.enabledModelIds)
                                            .map(String::trim)
                                            .filter(String::isNotEmpty)
                                            .distinct()
                                }
                            },
                        )
                        OnboardingPrimaryActionButton(
                            label = stringResource(Res.string.common_start_chat),
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
private fun SharedProviderPickerContent(
    providerSearch: String,
    onProviderSearchChange: (String) -> Unit,
    providerChoices: List<PiProviderDefinition>,
    onProviderSelected: (PiProviderDefinition) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharedProviderMinimalInputField(
            label = stringResource(Res.string.common_search),
            value = providerSearch,
            placeholder = stringResource(Res.string.onboarding_provider_search_placeholder),
            onValueChange = onProviderSearchChange,
        )
        providerChoices.forEach { provider ->
            SharedProviderStageButton(
                label = provider.displayName,
                subtitle = "${provider.category} · ${provider.id}",
                provider = provider,
                onClick = { onProviderSelected(provider) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.onboarding_change_later_settings),
            style = MaterialTheme.typography.bodySmall,
            color = SharedProviderTourTextSecondary,
        )
    }
}

@Composable
private fun SharedProviderCredentialsContent(
    formState: ProviderFormState,
    definition: PiProviderDefinition,
    authState: PiProviderAuthState,
    isLoadingModels: Boolean,
    canContinue: Boolean,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit,
    onClearAuthState: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_using_pi_provider, definition.displayName),
            style = MaterialTheme.typography.labelMedium,
            color = SharedProviderTourGreen,
        )
        ProviderAuthenticationSetup(
            state = formState,
            authState = authState,
            onStartProviderLogin = onStartProviderLogin,
            onSubmitAuthPrompt = onSubmitAuthPrompt,
            onClearAuthState = onClearAuthState,
            cardColor = SharedProviderTourSurface,
        )
        OnboardingPrimaryActionButton(
            label = if (isLoadingModels) {
                stringResource(Res.string.onboarding_loading_models)
            } else {
                stringResource(Res.string.common_next)
            },
            enabled = canContinue && !isLoadingModels,
            onClick = onContinue,
            isLoading = isLoadingModels,
        )
    }
}

@Composable
private fun SharedProviderConfigurationPane(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(SharedProviderTourSurface.copy(alpha = 0.52f))
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 34.dp, top = 34.dp, end = 34.dp, bottom = 28.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = SharedProviderTourTextPrimary,
        )
        Spacer(modifier = Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun SharedProviderStageButton(
    label: String,
    subtitle: String,
    provider: PiProviderDefinition,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SharedProviderTourSurface,
            contentColor = SharedProviderTourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProviderBrandIconBadge(provider = provider)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = SharedProviderTourTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SharedProviderTourTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SharedProviderModelOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) SharedProviderTourButton else SharedProviderTourSurface,
            contentColor = if (selected) Color.White else SharedProviderTourTextPrimary,
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
private fun SharedProviderMinimalInputField(
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
            color = SharedProviderTourTextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SharedProviderTourSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .sharedProviderBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = SharedProviderTourTextPrimary),
            cursorBrush = SolidColor(SharedProviderTourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SharedProviderTourTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SharedProviderMinimalTextFieldValueInput(
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
            color = SharedProviderTourTextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SharedProviderTourSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .sharedProviderBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = SharedProviderTourTextPrimary),
            cursorBrush = SolidColor(SharedProviderTourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.text.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SharedProviderTourTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.sharedProviderBringIntoViewOnFocus(): Modifier = composed {
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

internal fun prioritizedSharedProviderModelOptions(
    piProviderId: String?,
    cachedModels: List<String>,
): List<String> {
    val fetchedModels = cachedModels
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
    val orderedModels = fetchedModels
        .sortedWith(
            compareBy<String> { sharedProviderPreferredModelRank(it) }
                .thenBy { sharedProviderModelRank(piProviderId, it) }
                .thenBy { it.lowercase() },
        )
    return orderedModels.withSharedAutomaticChatModelFirst(piProviderId)
}

private fun sharedProviderPreferredModelRank(model: String): Int =
    automaticModelPriority(model, AutomaticModelPurpose.Chat) ?: Int.MAX_VALUE

private fun List<String>.withSharedAutomaticChatModelFirst(
    piProviderId: String?,
): List<String> {
    if (isEmpty() || piProviderId == null) return this
    val definition = PiProviderCatalog.resolve(piProviderId)
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
        options.resolveAutomaticModelKey(AutomaticModelPurpose.Chat),
    )?.modelId ?: return this
    if (sharedProviderPreferredModelRank(automaticModel) > sharedProviderPreferredModelRank(first())) {
        return this
    }
    return (listOf(automaticModel) + filterNot { it.equals(automaticModel, ignoreCase = true) })
        .distinctBy { it.lowercase() }
}

private fun sharedProviderModelRank(
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
