package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhousl.aether.R
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.LlmCustomHeader
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.PiProviderDefinition
import com.zhousl.aether.data.PiProviderEnvironmentVariable
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.defaultAuthMethod
import com.zhousl.aether.data.isValidProviderId
import com.zhousl.aether.data.normalizeLlmUserAgent
import com.zhousl.aether.data.sanitizeProviderId
import com.zhousl.aether.data.pi.PiOAuthPrompt
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import org.json.JSONObject
import java.util.UUID

private val ProviderFormPrimary = Color(0xFF5C5C5C)
private val ProviderWizardEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val InteractiveCredentialProviderIds = setOf(
    "cloudflare-ai-gateway",
    "cloudflare-workers-ai",
)
private const val OAuthFlowBrowser = "browser"
private const val OAuthFlowDeviceCode = "device_code"

@Stable
class ProviderFormState internal constructor(
    private val existingConfig: LlmProviderConfig?,
    providerId: String,
    name: String,
    piProviderId: String,
    authMethodStorageValue: String,
    apiKey: String,
    oauthCredentialJson: String,
    providerEnvironmentVariables: List<PiProviderEnvironmentVariable>,
    baseUrl: String,
    modelId: String,
    userAgent: String,
    customHeaders: List<LlmCustomHeader>,
    cachedModels: List<String>,
    enabledModelIds: List<String>,
    providerIdManuallyEdited: Boolean = existingConfig != null,
    lastAutoGeneratedProviderId: String = providerId,
) {
    var providerId by mutableStateOf(providerId)
    var name by mutableStateOf(name)
    var piProviderId by mutableStateOf(piProviderId)
    var authMethodStorageValue by mutableStateOf(authMethodStorageValue)
    var apiKey by mutableStateOf(apiKey)
    var oauthCredentialJson by mutableStateOf(oauthCredentialJson)
    var providerEnvironmentVariables by mutableStateOf(providerEnvironmentVariables)
    var baseUrl by mutableStateOf(baseUrl)
    var modelId by mutableStateOf(modelId)
    var userAgent by mutableStateOf(userAgent)
    var customHeaders by mutableStateOf(customHeaders)
    var cachedModels by mutableStateOf(cachedModels)
    var enabledModelIds by mutableStateOf(enabledModelIds)
    var isFetchingModelsLocally by mutableStateOf(false)
    private var providerIdManuallyEdited by mutableStateOf(providerIdManuallyEdited)
    private var lastAutoGeneratedProviderId by mutableStateOf(lastAutoGeneratedProviderId)

    val selectedDefinition: PiProviderDefinition
        get() = PiProviderCatalog.resolve(piProviderId)

    val authMethod: ProviderAuthMethod
        get() = ProviderAuthMethod.fromStorage(
            authMethodStorageValue,
            selectedDefinition.defaultAuthMethod(),
        )

    val allModels: List<String>
        get() = normalizeModelIds(cachedModels + manualModelIds)

    private val manualModelIds: List<String>
        get() = parseManualModelIds(modelId)

    val effectiveModelId: String
        get() = enabledModelIds.firstOrNull()
            ?: manualModelIds.firstOrNull()
            ?: allModels.firstOrNull().orEmpty()

    val isCurrentlyEnabled: Boolean
        get() = existingConfig?.isEnabled ?: true

    val isProviderIdManuallyEdited: Boolean
        get() = providerIdManuallyEdited

    val currentAutoGeneratedProviderId: String
        get() = lastAutoGeneratedProviderId

    fun updateName(value: String) {
        name = value
        updateAutoGeneratedProviderId()
    }

    fun setProviderIdFromUser(value: String) {
        providerId = value
        providerIdManuallyEdited = value.isNotBlank() && value != lastAutoGeneratedProviderId
    }

    fun isValid(existingProviderIds: Set<String>): Boolean {
        val trimmedProviderId = providerId.trim()
        val providerIdAvailable = trimmedProviderId !in (existingProviderIds - setOf(existingConfig?.providerId.orEmpty()))
        return providerIdAvailable &&
            isValidProviderId(trimmedProviderId) &&
            buildConfig().let { config ->
                val definition = PiProviderCatalog.resolve(config.piProviderId)
                (!definition.requiresBaseUrl || config.baseUrl.isNotBlank()) &&
                    when (config.authMethod) {
                        ProviderAuthMethod.ApiKey ->
                            !definition.supportsApiKey ||
                                config.apiKey.isNotBlank() ||
                                !definition.isBuiltIn

                        ProviderAuthMethod.OAuth ->
                            definition.supportsOAuth && config.oauthCredentialJson.isNotBlank()

                        ProviderAuthMethod.Ambient -> definition.supportsAmbientAuth
                    }
            }
    }

    fun applyProviderDefaults(definition: PiProviderDefinition) {
        val previousDefinition = selectedDefinition
        piProviderId = definition.id
        authMethodStorageValue = definition.defaultAuthMethod().storageValue
        oauthCredentialJson = ""
        if (
            baseUrl.isBlank() ||
            baseUrl.trim() == previousDefinition.defaultBaseUrl
        ) {
            baseUrl = definition.defaultBaseUrl
        }
        modelId = ""
        cachedModels = emptyList()
        enabledModelIds = emptyList()
        if (!providerIdManuallyEdited) {
            updateAutoGeneratedProviderId()
        } else if (providerId.isBlank() || providerId.trim() == previousDefinition.id) {
            providerId = definition.id.sanitizeProviderId()
        }
    }

    fun setAuthMethod(method: ProviderAuthMethod) {
        authMethodStorageValue = method.storageValue
    }

    fun ensureAvailableProviderId(existingProviderIds: Set<String>) {
        if (providerIdManuallyEdited || providerId.trim() !in existingProviderIds) return
        val baseId = selectedDefinition.id.sanitizeProviderId().ifBlank { "provider" }
        var suffix = 2
        var candidate = "${baseId}_$suffix"
        while (candidate in existingProviderIds) {
            suffix += 1
            candidate = "${baseId}_$suffix"
        }
        providerId = candidate
        lastAutoGeneratedProviderId = candidate
    }

    fun isAuthenticationConfigured(): Boolean {
        val config = buildConfig()
        val definition = PiProviderCatalog.resolve(config.piProviderId)
        if ((definition.requiresBaseUrl || !definition.isBuiltIn) && config.baseUrl.isBlank()) {
            return false
        }
        return when (config.authMethod) {
            ProviderAuthMethod.ApiKey ->
                !definition.supportsApiKey ||
                    config.apiKey.isNotBlank() ||
                    !definition.isBuiltIn

            ProviderAuthMethod.OAuth ->
                definition.supportsOAuth && config.oauthCredentialJson.isNotBlank()

            ProviderAuthMethod.Ambient -> definition.supportsAmbientAuth
        }
    }

    private fun updateAutoGeneratedProviderId() {
        if (providerIdManuallyEdited) return
        val generatedProviderId = name.sanitizeProviderId()
            .ifBlank { selectedDefinition.id.sanitizeProviderId() }
        providerId = generatedProviderId
        lastAutoGeneratedProviderId = generatedProviderId
    }

    fun setModelEnabled(
        model: String,
        enabled: Boolean,
    ) {
        val normalizedModel = model.trim()
        enabledModelIds = if (enabled) {
            normalizeModelIds(enabledModelIds + normalizedModel)
        } else {
            enabledModelIds.filterNot { it == normalizedModel }
        }
    }

    fun setAllModelsEnabled(enabled: Boolean) {
        enabledModelIds = if (enabled) allModels else emptyList()
    }

    fun applyFetchedModels(models: List<String>) {
        val normalizedCurrent = allModels.toSet()
        val normalizedModels = models
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        cachedModels = normalizedModels
        val availableModels = normalizeModelIds(normalizedModels + manualModelIds)
        val newlyFetchedModels = normalizedModels.filter { model -> !normalizedCurrent.contains(model) }
        enabledModelIds = normalizeModelIds(enabledModelIds + newlyFetchedModels)
            .filter(availableModels::contains)
    }

    fun addCustomHeader() {
        customHeaders = customHeaders + LlmCustomHeader("", "")
    }

    fun updateCustomHeader(
        index: Int,
        name: String = customHeaders.getOrNull(index)?.name.orEmpty(),
        value: String = customHeaders.getOrNull(index)?.value.orEmpty(),
    ) {
        customHeaders = customHeaders.mapIndexed { headerIndex, header ->
            if (headerIndex == index) {
                header.copy(name = name, value = value)
            } else {
                header
            }
        }
    }

    fun removeCustomHeader(index: Int) {
        customHeaders = customHeaders.filterIndexed { headerIndex, _ -> headerIndex != index }
    }

    fun addProviderEnvironmentVariable() {
        providerEnvironmentVariables = providerEnvironmentVariables +
            PiProviderEnvironmentVariable("", "")
    }

    fun updateProviderEnvironmentVariable(
        index: Int,
        name: String = providerEnvironmentVariables.getOrNull(index)?.name.orEmpty(),
        value: String = providerEnvironmentVariables.getOrNull(index)?.value.orEmpty(),
    ) {
        providerEnvironmentVariables = providerEnvironmentVariables.mapIndexed { variableIndex, variable ->
            if (variableIndex == index) {
                variable.copy(name = name, value = value)
            } else {
                variable
            }
        }
    }

    fun removeProviderEnvironmentVariable(index: Int) {
        providerEnvironmentVariables = providerEnvironmentVariables
            .filterIndexed { variableIndex, _ -> variableIndex != index }
    }

    fun buildConfig(): LlmProviderConfig = LlmProviderConfig(
        id = existingConfig?.id ?: UUID.randomUUID().toString(),
        providerId = providerId.trim().sanitizeProviderId(),
        name = name.trim().ifBlank { selectedDefinition.displayName },
        piProviderId = selectedDefinition.id,
        authMethod = authMethod,
        apiKey = apiKey.trim(),
        oauthCredentialJson = oauthCredentialJson,
        providerEnvironmentVariables = providerEnvironmentVariables
            .map { variable ->
                PiProviderEnvironmentVariable(variable.name.trim(), variable.value)
            }
            .filter { variable -> variable.name.isNotBlank() },
        baseUrl = baseUrl.trim(),
        modelId = effectiveModelId,
        manualModelIds = manualModelIds,
        userAgent = normalizeLlmUserAgent(userAgent),
        customHeaders = customHeaders
            .map { header -> LlmCustomHeader(header.name.trim(), header.value) }
            .filter { header ->
                header.name.isNotBlank() &&
                    !header.name.equals("User-Agent", ignoreCase = true)
            },
        cachedModels = normalizeModelIds(cachedModels),
        enabledModelIds = enabledModelIds
            .map(String::trim)
            .filter { it.isNotEmpty() && allModels.contains(it) }
            .distinct(),
        isEnabled = existingConfig?.isEnabled ?: true,
        createdAtMillis = existingConfig?.createdAtMillis ?: System.currentTimeMillis(),
    )

    companion object {
        fun fromConfig(existingConfig: LlmProviderConfig?): ProviderFormState {
            val initialDefinition = PiProviderCatalog.resolve(existingConfig?.piProviderId ?: "openai")
            val initialModelId = existingConfig?.modelId.orEmpty()
            val initialManualModels = existingConfig?.manualModelIds
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(initialModelId).filter(String::isNotBlank)
            val initialModels = (existingConfig?.cachedModels.orEmpty() + initialManualModels)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            val initialEnabledModels = if (existingConfig != null) {
                existingConfig.enabledModelIds
                    .map(String::trim)
                    .filter { it.isNotEmpty() && initialModels.contains(it) }
                    .distinct()
            } else {
                emptyList()
            }
            return ProviderFormState(
                existingConfig = existingConfig,
                providerId = existingConfig?.providerId ?: initialDefinition.id.sanitizeProviderId(),
                name = existingConfig?.name.orEmpty(),
                piProviderId = existingConfig?.piProviderId ?: initialDefinition.id,
                authMethodStorageValue = existingConfig?.authMethod?.storageValue
                    ?: initialDefinition.defaultAuthMethod().storageValue,
                apiKey = existingConfig?.apiKey.orEmpty(),
                oauthCredentialJson = existingConfig?.oauthCredentialJson.orEmpty(),
                providerEnvironmentVariables = existingConfig?.providerEnvironmentVariables.orEmpty(),
                baseUrl = existingConfig?.baseUrl ?: initialDefinition.defaultBaseUrl,
                modelId = initialManualModels.joinToString("\n"),
                userAgent = normalizeLlmUserAgent(existingConfig?.userAgent),
                customHeaders = existingConfig?.customHeaders.orEmpty(),
                cachedModels = existingConfig?.cachedModels.orEmpty(),
                enabledModelIds = initialEnabledModels,
            )
        }
    }
}

@Composable
fun rememberProviderFormState(
    existingConfig: LlmProviderConfig?,
): ProviderFormState = rememberSaveable(
    existingConfig?.id,
    saver = providerFormStateSaver(existingConfig),
) {
    ProviderFormState.fromConfig(existingConfig)
}

@Composable
fun ProviderConfigurationForm(
    state: ProviderFormState,
    existingProviderIds: Set<String>,
    isFetchingModels: Boolean,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onModelEnabledChange: (LlmProviderConfig) -> Unit = {},
    authState: PiProviderAuthState = PiProviderAuthState(),
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit = { _, _, _, _ -> },
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onClearAuthState: () -> Unit = {},
    modifier: Modifier = Modifier,
    cardColor: Color = AetherSurfaceHigh,
) {
    val selectedDefinition = state.selectedDefinition
    val clipboardManager = LocalClipboardManager.current
    val relevantAuthState = authState.takeIf {
        it.providerId == selectedDefinition.id && it.authMethod == state.authMethod
    }
    ProviderAuthStateEffects(state, relevantAuthState)
    val providerIdAlreadyUsed = state.providerId.trim() in (existingProviderIds - setOf(state.buildConfig().providerId))
    val providerIdError = when {
        state.providerId.isBlank() -> stringResource(R.string.provider_form_provider_id_required)
        !isValidProviderId(state.providerId.trim()) -> stringResource(R.string.provider_form_provider_id_invalid)
        providerIdAlreadyUsed -> stringResource(R.string.provider_form_provider_id_in_use)
        else -> ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderFormCard(cardColor = cardColor) {
            ProviderFormTextField(
                label = stringResource(R.string.provider_form_provider_name),
                value = state.name,
                onValueChange = state::updateName,
            )
            ProviderFormDivider()
            ProviderFormTextField(
                label = stringResource(R.string.provider_form_provider_id),
                value = state.providerId,
                onValueChange = state::setProviderIdFromUser,
            )
        }

        if (providerIdError.isNotBlank()) {
            Text(
                text = providerIdError,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD25757),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        ProviderFormCard(cardColor = cardColor) {
            ProviderFormDropdownField(
                label = stringResource(R.string.provider_form_provider),
                selectedValue = selectedDefinition.displayName,
                options = PiProviderCatalog.providers,
                onSelected = state::applyProviderDefaults,
            )
        }

        Text(
            text = if (selectedDefinition.isBuiltIn) {
                stringResource(R.string.provider_form_pi_builtin_description)
            } else {
                stringResource(R.string.provider_form_pi_custom_description)
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        ProviderFormCard(cardColor = cardColor) {
            ProviderAuthMethodField(
                definition = selectedDefinition,
                selectedMethod = state.authMethod,
                onSelected = state::setAuthMethod,
            )
        }

        when (state.authMethod) {
            ProviderAuthMethod.ApiKey -> ProviderFormCard(cardColor = cardColor) {
                ProviderFormTextField(
                    label = stringResource(R.string.provider_form_api_key),
                    value = state.apiKey,
                    onValueChange = { state.apiKey = it },
                    isSecret = true,
                )
            }

            ProviderAuthMethod.OAuth -> ProviderOAuthField(
                definition = selectedDefinition,
                credentialJson = state.oauthCredentialJson,
                oauthState = relevantAuthState,
                onStartOAuthLogin = { flow ->
                    onClearAuthState()
                    onStartProviderLogin(state.buildConfig().id, selectedDefinition.id, ProviderAuthMethod.OAuth, flow)
                },
                onDisconnect = {
                    state.oauthCredentialJson = ""
                    onClearAuthState()
                },
                onCopyDeviceCode = { code ->
                    clipboardManager.setText(AnnotatedString(code))
                },
                cardColor = cardColor,
            )

            ProviderAuthMethod.Ambient -> Text(
                text = stringResource(R.string.provider_form_ambient_auth_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        ProviderFormCard(cardColor = cardColor) {
            ProviderBaseUrlField(
                state = state,
                definition = selectedDefinition,
            )
            ProviderFormDivider()
            ProviderFormTextField(
                label = stringResource(R.string.provider_form_manual_model_ids),
                value = state.modelId,
                onValueChange = { state.modelId = it },
            )
            ProviderFormDivider()
            ProviderModelListField(
                models = state.allModels,
                enabledModelIds = state.enabledModelIds,
                isFetchingModels = state.isFetchingModelsLocally || isFetchingModels,
                onToggleModel = { model, enabled ->
                    state.setModelEnabled(model, enabled)
                    onModelEnabledChange(state.buildConfig())
                },
                onSetAllModelsEnabled = { enabled ->
                    state.setAllModelsEnabled(enabled)
                    onModelEnabledChange(state.buildConfig())
                },
                onFetchModels = {
                    state.isFetchingModelsLocally = true
                    onFetchModels(state.buildConfig()) { models ->
                        state.applyFetchedModels(models)
                        state.isFetchingModelsLocally = false
                    }
                },
            )
            ProviderFormDivider()
            ProviderEnvironmentVariablesField(
                variables = state.providerEnvironmentVariables,
                onAddVariable = state::addProviderEnvironmentVariable,
                onUpdateVariable = state::updateProviderEnvironmentVariable,
                onRemoveVariable = state::removeProviderEnvironmentVariable,
            )
            ProviderFormDivider()
            ProviderFormTextField(
                label = stringResource(R.string.provider_form_user_agent),
                value = state.userAgent,
                onValueChange = { state.userAgent = it },
            )
            ProviderFormDivider()
            ProviderCustomHeadersField(
                headers = state.customHeaders,
                onAddHeader = state::addCustomHeader,
                onUpdateHeader = state::updateCustomHeader,
                onRemoveHeader = state::removeCustomHeader,
            )
        }

        Text(
            text = if (
                state.authMethod == ProviderAuthMethod.ApiKey &&
                selectedDefinition.supportsApiKey
            ) {
                stringResource(R.string.provider_form_model_picker_api_key_hint)
            } else {
                stringResource(R.string.provider_form_model_picker_refresh_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    ProviderAuthPromptDialog(
        prompt = relevantAuthState?.prompt,
        onSubmitPrompt = onSubmitAuthPrompt,
    )
}

@Composable
private fun ProviderAuthStateEffects(
    state: ProviderFormState,
    authState: PiProviderAuthState?,
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(authState?.authorizationUrl) {
        authState?.authorizationUrl
            ?.takeIf(String::isNotBlank)
            ?.let { url -> runCatching { uriHandler.openUri(url) } }
    }
    LaunchedEffect(authState?.verificationUrl) {
        authState?.verificationUrl
            ?.takeIf(String::isNotBlank)
            ?.let { url -> runCatching { uriHandler.openUri(url) } }
    }
    LaunchedEffect(
        authState?.authMethod,
        authState?.oauthCredentialJson,
        authState?.apiKey,
        authState?.providerEnvironmentVariables,
    ) {
        authState?.let { applyProviderAuthResult(state, it) }
    }
}

internal fun applyProviderAuthResult(
    state: ProviderFormState,
    authState: PiProviderAuthState,
) {
    when (authState.authMethod) {
        ProviderAuthMethod.OAuth -> authState.oauthCredentialJson
            .takeIf(String::isNotBlank)
            ?.let { credential ->
                state.oauthCredentialJson = credential
                state.setAuthMethod(ProviderAuthMethod.OAuth)
            }

        ProviderAuthMethod.ApiKey -> {
            authState.apiKey.takeIf(String::isNotBlank)?.let { state.apiKey = it }
            if (authState.providerEnvironmentVariables.isNotEmpty()) {
                state.providerEnvironmentVariables = authState.providerEnvironmentVariables
            }
        }

        ProviderAuthMethod.Ambient -> Unit
    }
}

@Composable
fun ProviderAuthenticationSetup(
    state: ProviderFormState,
    authState: PiProviderAuthState,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit,
    onClearAuthState: () -> Unit,
    cardColor: Color = AetherSurfaceHigh,
    modifier: Modifier = Modifier,
) {
    val definition = state.selectedDefinition
    val relevantAuthState = authState.takeIf {
        it.providerId == definition.id && it.authMethod == state.authMethod
    }
    val clipboardManager = LocalClipboardManager.current
    ProviderAuthStateEffects(state, relevantAuthState)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                R.string.provider_add_configure_provider,
                definition.displayName,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AetherOnSurface,
        )
        Text(
            text = when (state.authMethod) {
                ProviderAuthMethod.ApiKey -> stringResource(R.string.provider_add_api_key_guidance)
                ProviderAuthMethod.OAuth -> stringResource(R.string.provider_add_oauth_guidance)
                ProviderAuthMethod.Ambient -> stringResource(R.string.provider_form_ambient_auth_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
        )

        when (state.authMethod) {
            ProviderAuthMethod.ApiKey -> {
                if (definition.id in InteractiveCredentialProviderIds) {
                    ProviderApiKeyLoginField(
                        definition = definition,
                        configured = state.apiKey.isNotBlank(),
                        authState = relevantAuthState,
                        onStartLogin = {
                            onClearAuthState()
                            onStartProviderLogin(state.buildConfig().id, definition.id, ProviderAuthMethod.ApiKey, "")
                        },
                        onDisconnect = {
                            state.apiKey = ""
                            state.providerEnvironmentVariables = emptyList()
                            onClearAuthState()
                        },
                        cardColor = cardColor,
                    )
                } else {
                    ProviderFormCard(cardColor = cardColor) {
                        ProviderFormTextField(
                            label = stringResource(
                                R.string.provider_add_provider_api_key,
                                definition.displayName,
                            ),
                            value = state.apiKey,
                            onValueChange = { state.apiKey = it },
                            isSecret = true,
                        )
                        if (definition.supportsCustomBaseUrl) {
                            ProviderFormDivider()
                            ProviderBaseUrlField(
                                state = state,
                                definition = definition,
                                resetToDefaultWhenBlank = true,
                            )
                        }
                    }
                }
            }

            ProviderAuthMethod.OAuth -> {
                ProviderOAuthField(
                    definition = definition,
                    credentialJson = state.oauthCredentialJson,
                    oauthState = relevantAuthState,
                    onStartOAuthLogin = { flow ->
                        onClearAuthState()
                        onStartProviderLogin(state.buildConfig().id, definition.id, ProviderAuthMethod.OAuth, flow)
                    },
                    onDisconnect = {
                        state.oauthCredentialJson = ""
                        onClearAuthState()
                    },
                    onCopyDeviceCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                    },
                    cardColor = cardColor,
                )
                if (definition.supportsCustomBaseUrl) {
                    ProviderFormCard(cardColor = cardColor) {
                        ProviderBaseUrlField(
                            state = state,
                            definition = definition,
                            resetToDefaultWhenBlank = true,
                        )
                    }
                }
            }

            ProviderAuthMethod.Ambient -> ProviderFormCard(cardColor = cardColor) {
                ProviderEnvironmentVariablesField(
                    variables = state.providerEnvironmentVariables,
                    onAddVariable = state::addProviderEnvironmentVariable,
                    onUpdateVariable = state::updateProviderEnvironmentVariable,
                    onRemoveVariable = state::removeProviderEnvironmentVariable,
                )
            }
        }

        if (definition.requiresBaseUrl || (!definition.isBuiltIn && !definition.supportsCustomBaseUrl)) {
            ProviderFormCard(cardColor = cardColor) {
                ProviderBaseUrlField(
                    state = state,
                    definition = definition,
                )
            }
        }
    }

    ProviderAuthPromptDialog(
        prompt = relevantAuthState?.prompt,
        onSubmitPrompt = onSubmitAuthPrompt,
    )
}

@Composable
private fun ProviderApiKeyLoginField(
    definition: PiProviderDefinition,
    configured: Boolean,
    authState: PiProviderAuthState?,
    onStartLogin: () -> Unit,
    onDisconnect: () -> Unit,
    cardColor: Color,
) {
    ProviderFormCard(cardColor = cardColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.provider_add_provider_api_key,
                    definition.displayName,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Text(
                text = when {
                    authState?.errorMessage?.isNotBlank() == true -> authState.errorMessage
                    authState?.statusMessage?.isNotBlank() == true -> authState.statusMessage
                    configured -> stringResource(R.string.provider_add_api_key_configured)
                    else -> stringResource(R.string.provider_add_api_key_not_configured)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (authState?.errorMessage?.isNotBlank() == true) {
                    Color(0xFFD25757)
                } else {
                    AetherOnSurface
                },
            )
            ProviderActionRow(
                icon = Icons.Rounded.Key,
                label = when {
                    authState?.isRunning == true -> stringResource(R.string.provider_add_waiting_for_input)
                    configured -> stringResource(R.string.provider_add_reconfigure_api_key)
                    else -> stringResource(R.string.provider_add_configure_credentials)
                },
                onClick = onStartLogin,
                enabled = authState?.isRunning != true,
            )
            if (configured) {
                ProviderActionRow(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.provider_add_remove_api_key),
                    onClick = onDisconnect,
                    enabled = true,
                    trailingIcon = null,
                )
            }
        }
    }
}

@Composable
private fun ProviderAuthPromptDialog(
    prompt: PiOAuthPrompt?,
    onSubmitPrompt: (String, String, Boolean) -> Unit,
) {
    if (prompt == null) return
    var promptValue by rememberSaveable(prompt.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            onSubmitPrompt(prompt.id, "", true)
        },
        containerColor = AetherSurface,
        title = {
            Text(
                text = stringResource(R.string.provider_form_oauth_prompt_title),
                color = AetherOnSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (prompt.type != "manual_code") {
                    Text(prompt.message, color = AetherOnSurface)
                }
                if (prompt.options.isNotEmpty()) {
                    prompt.options.forEach { option ->
                        ProviderModelListActionButton(
                            label = option.label,
                            onClick = { onSubmitPrompt(prompt.id, option.id, false) },
                            enabled = true,
                        )
                        option.description.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = AetherOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    ProviderFormTextField(
                        label = prompt.placeholder.ifBlank {
                            stringResource(R.string.provider_form_oauth_prompt_value)
                        },
                        value = promptValue,
                        onValueChange = { promptValue = it },
                        isSecret = prompt.type == "secret",
                        showLabel = prompt.type != "manual_code",
                    )
                }
            }
        },
        confirmButton = {
            if (prompt.options.isEmpty()) {
                TextButton(
                    onClick = { onSubmitPrompt(prompt.id, promptValue, false) },
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onSubmitPrompt(prompt.id, "", true) },
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private enum class AddProviderStage {
    Authentication,
    Provider,
    Credentials,
    Models,
}

@Composable
fun AddProviderWizard(
    state: ProviderFormState,
    existingProviderIds: Set<String>,
    isFetchingModels: Boolean,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    authState: PiProviderAuthState,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit,
    onClearAuthState: () -> Unit,
    onSave: (LlmProviderConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stageName by rememberSaveable { mutableStateOf(AddProviderStage.Authentication.name) }
    var selectedAuthMethodName by rememberSaveable {
        mutableStateOf(ProviderAuthMethod.ApiKey.name)
    }
    var providerSearch by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val stage = AddProviderStage.valueOf(stageName)
    val selectedAuthMethod = ProviderAuthMethod.valueOf(selectedAuthMethodName)
    val matchingProviders = remember(providerSearch, selectedAuthMethod) {
        val query = providerSearch.trim().lowercase()
        PiProviderCatalog.providers.filter { provider ->
            val supportsMethod = when (selectedAuthMethod) {
                ProviderAuthMethod.ApiKey -> provider.supportsInteractiveApiKey
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
    val isLoadingModels = state.isFetchingModelsLocally || isFetchingModels

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                val movingForward = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(
                    animationSpec = tween(340, easing = ProviderWizardEasing),
                    initialOffsetX = { width -> if (movingForward) width / 2 else -width / 2 },
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = 80,
                        easing = ProviderWizardEasing,
                    ),
                )
                val exit = slideOutHorizontally(
                    animationSpec = tween(260, easing = ProviderWizardEasing),
                    targetOffsetX = { width -> if (movingForward) -width / 2 else width / 2 },
                ) + fadeOut(animationSpec = tween(110, easing = ProviderWizardEasing))
                enter togetherWith exit
            },
            label = "add_provider_stage",
        ) { animatedStage ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(
                            R.string.provider_add_step,
                            animatedStage.ordinal + 1,
                            AddProviderStage.entries.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (animatedStage) {
                            AddProviderStage.Authentication ->
                                stringResource(R.string.provider_add_choose_auth_title)
                            AddProviderStage.Provider ->
                                stringResource(R.string.provider_add_choose_provider_title)
                            AddProviderStage.Credentials ->
                                stringResource(R.string.provider_add_credentials_title)
                            AddProviderStage.Models ->
                                stringResource(R.string.provider_add_models_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AetherOnSurface,
                    )
                }
                when (animatedStage) {
            AddProviderStage.Authentication -> {
                Text(
                    text = stringResource(R.string.provider_add_choose_auth_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
                ProviderWizardChoiceRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.provider_add_subscription),
                    subtitle = stringResource(R.string.provider_add_subscription_description),
                    onClick = {
                        selectedAuthMethodName = ProviderAuthMethod.OAuth.name
                        providerSearch = ""
                        state.setAuthMethod(ProviderAuthMethod.OAuth)
                        stageName = AddProviderStage.Provider.name
                    },
                )
                ProviderWizardChoiceRow(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.provider_add_api_key),
                    subtitle = stringResource(R.string.provider_add_api_key_description),
                    onClick = {
                        selectedAuthMethodName = ProviderAuthMethod.ApiKey.name
                        providerSearch = ""
                        state.setAuthMethod(ProviderAuthMethod.ApiKey)
                        stageName = AddProviderStage.Provider.name
                    },
                )
                ProviderWizardChoiceRow(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.provider_add_environment),
                    subtitle = stringResource(R.string.provider_add_environment_description),
                    onClick = {
                        selectedAuthMethodName = ProviderAuthMethod.Ambient.name
                        providerSearch = ""
                        state.setAuthMethod(ProviderAuthMethod.Ambient)
                        stageName = AddProviderStage.Provider.name
                    },
                )
            }

            AddProviderStage.Provider -> {
                Text(
                    text = providerAuthMethodDescription(selectedAuthMethod),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
                ProviderWizardSearchField(
                    value = providerSearch,
                    onValueChange = { providerSearch = it },
                )
                if (matchingProviders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.provider_add_no_matching_providers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    matchingProviders
                        .groupBy(PiProviderDefinition::category)
                        .forEach { (category, providers) ->
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                color = AetherOnSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            providers.forEach { provider ->
                                ProviderWizardChoiceRow(
                                    title = provider.displayName,
                                    subtitle = provider.id,
                                    provider = provider,
                                    onClick = {
                                        onClearAuthState()
                                        state.applyProviderDefaults(provider)
                                        state.setAuthMethod(selectedAuthMethod)
                                        state.ensureAvailableProviderId(existingProviderIds)
                                        stageName = AddProviderStage.Credentials.name
                                    },
                                )
                            }
                        }
                }
                ProviderWizardSecondaryButton(
                    label = stringResource(R.string.common_back),
                    onClick = { stageName = AddProviderStage.Authentication.name },
                )
            }

            AddProviderStage.Credentials -> {
                ProviderAuthenticationSetup(
                    state = state,
                    authState = authState,
                    onStartProviderLogin = onStartProviderLogin,
                    onSubmitAuthPrompt = onSubmitAuthPrompt,
                    onClearAuthState = onClearAuthState,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProviderWizardSecondaryButton(
                        label = stringResource(R.string.common_back),
                        onClick = {
                            onClearAuthState()
                            stageName = AddProviderStage.Provider.name
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ProviderWizardPrimaryButton(
                        label = if (isLoadingModels) {
                            stringResource(R.string.onboarding_loading_models)
                        } else {
                            stringResource(R.string.common_continue)
                        },
                        enabled = state.isAuthenticationConfigured() && !isLoadingModels,
                        isLoading = isLoadingModels,
                        onClick = {
                            state.isFetchingModelsLocally = true
                            onFetchModels(state.buildConfig()) { models ->
                                state.applyFetchedModels(models)
                                state.isFetchingModelsLocally = false
                                stageName = AddProviderStage.Models.name
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AddProviderStage.Models -> {
                Text(
                    text = stringResource(
                        R.string.provider_add_models_description,
                        state.selectedDefinition.displayName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
                ProviderFormCard(cardColor = AetherSurfaceHigh) {
                    ProviderFormTextField(
                        label = stringResource(R.string.provider_form_manual_model_ids),
                        value = state.modelId,
                        onValueChange = { state.modelId = it },
                    )
                    ProviderFormDivider()
                    ProviderModelListField(
                        models = state.allModels,
                        enabledModelIds = state.enabledModelIds,
                        isFetchingModels = isLoadingModels,
                        onToggleModel = state::setModelEnabled,
                        onSetAllModelsEnabled = state::setAllModelsEnabled,
                        onFetchModels = {
                            state.isFetchingModelsLocally = true
                            onFetchModels(state.buildConfig()) { models ->
                                state.applyFetchedModels(models)
                                state.isFetchingModelsLocally = false
                            }
                        },
                    )
                }
                ProviderWizardSecondaryButton(
                    label = if (showAdvanced) {
                        stringResource(R.string.provider_add_hide_advanced)
                    } else {
                        stringResource(R.string.provider_add_show_advanced)
                    },
                    onClick = { showAdvanced = !showAdvanced },
                )
                if (showAdvanced) {
                    ProviderFormCard(cardColor = AetherSurfaceHigh) {
                        ProviderFormTextField(
                            label = stringResource(R.string.provider_form_provider_name),
                            value = state.name,
                            onValueChange = state::updateName,
                        )
                        ProviderFormDivider()
                        ProviderFormTextField(
                            label = stringResource(R.string.provider_form_provider_id),
                            value = state.providerId,
                            onValueChange = state::setProviderIdFromUser,
                        )
                        if (!state.selectedDefinition.requiresBaseUrl && state.selectedDefinition.isBuiltIn) {
                            ProviderFormDivider()
                            ProviderBaseUrlField(
                                state = state,
                                definition = state.selectedDefinition,
                            )
                        }
                        ProviderFormDivider()
                        ProviderEnvironmentVariablesField(
                            variables = state.providerEnvironmentVariables,
                            onAddVariable = state::addProviderEnvironmentVariable,
                            onUpdateVariable = state::updateProviderEnvironmentVariable,
                            onRemoveVariable = state::removeProviderEnvironmentVariable,
                        )
                        ProviderFormDivider()
                        ProviderFormTextField(
                            label = stringResource(R.string.provider_form_user_agent),
                            value = state.userAgent,
                            onValueChange = { state.userAgent = it },
                        )
                        ProviderFormDivider()
                        ProviderCustomHeadersField(
                            headers = state.customHeaders,
                            onAddHeader = state::addCustomHeader,
                            onUpdateHeader = state::updateCustomHeader,
                            onRemoveHeader = state::removeCustomHeader,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProviderWizardSecondaryButton(
                        label = stringResource(R.string.common_back),
                        onClick = { stageName = AddProviderStage.Credentials.name },
                        modifier = Modifier.weight(1f),
                    )
                    ProviderWizardPrimaryButton(
                        label = stringResource(R.string.common_save),
                        enabled = state.isValid(existingProviderIds),
                        onClick = { onSave(state.buildConfig()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
                }
            }
        }
    }
}

@Composable
private fun providerAuthMethodDescription(method: ProviderAuthMethod): String = when (method) {
    ProviderAuthMethod.OAuth -> stringResource(R.string.provider_add_subscription_provider_description)
    ProviderAuthMethod.ApiKey -> stringResource(R.string.provider_add_api_key_provider_description)
    ProviderAuthMethod.Ambient -> stringResource(R.string.provider_add_environment_provider_description)
}

@Composable
internal fun ProviderWizardChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    provider: PiProviderDefinition? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AetherSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (provider != null) {
            ProviderBrandIconBadge(
                provider = provider,
                badgeSize = 36.dp,
                iconSize = 23.dp,
                cornerRadius = 8.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AetherPrimary.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AetherPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AetherOnSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProviderWizardSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(ProviderFormPrimary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.provider_add_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ProviderWizardPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) AetherPrimary else AetherSurfaceHigh.copy(alpha = 0.65f)
            )
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = AetherOnPrimary,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) AetherOnPrimary else AetherOnSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ProviderWizardSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = AetherOnSurface,
        )
    }
}

private fun providerFormStateSaver(
    existingConfig: LlmProviderConfig?,
): Saver<ProviderFormState, Any> = listSaver(
    save = { state ->
        listOf(
            state.providerId,
            state.name,
            state.piProviderId,
            state.authMethodStorageValue,
            state.providerEnvironmentVariables.map { listOf(it.name, it.value) },
            state.baseUrl,
            state.modelId,
            state.userAgent,
            state.customHeaders.map { listOf(it.name, it.value) },
            state.cachedModels,
            state.enabledModelIds,
            state.isProviderIdManuallyEdited,
            state.currentAutoGeneratedProviderId,
        )
    },
    restore = { restored ->
        val hasUserAgent = restored.size >= 13
        val customHeadersIndex = if (hasUserAgent) 8 else 7
        val cachedModelsIndex = if (hasUserAgent) 9 else 8
        val enabledModelIdsIndex = if (hasUserAgent) 10 else 9
        val manuallyEditedIndex = if (hasUserAgent) 11 else 10
        val autoProviderIdIndex = if (hasUserAgent) 12 else 11
        val restoredEnvironmentVariables =
            (restored[4] as List<*>).mapNotNull { item ->
                val values = item as? List<*> ?: return@mapNotNull null
                PiProviderEnvironmentVariable(
                    name = values.getOrNull(0) as? String ?: "",
                    value = values.getOrNull(1) as? String ?: "",
                )
            }
        val restoredCustomHeaders = (restored[customHeadersIndex] as List<*>)
                .mapNotNull { item ->
                    val values = item as? List<*> ?: return@mapNotNull null
                    LlmCustomHeader(
                        name = values.getOrNull(0) as? String ?: "",
                        value = values.getOrNull(1) as? String ?: "",
                    )
                }
        ProviderFormState(
            existingConfig = existingConfig,
            providerId = restored[0] as String,
            name = restored[1] as String,
            piProviderId = restored[2] as String,
            authMethodStorageValue = restored[3] as String,
            apiKey = existingConfig?.apiKey.orEmpty(),
            oauthCredentialJson = existingConfig?.oauthCredentialJson.orEmpty(),
            providerEnvironmentVariables = restoredEnvironmentVariables,
            baseUrl = restored[5] as String,
            modelId = restored[6] as String,
            userAgent = if (hasUserAgent) {
                restored[7] as String
            } else {
                normalizeLlmUserAgent(existingConfig?.userAgent)
            },
            customHeaders = restoredCustomHeaders,
            cachedModels = restored[cachedModelsIndex] as List<String>,
            enabledModelIds = restored[enabledModelIdsIndex] as List<String>,
            providerIdManuallyEdited = restored.getOrNull(manuallyEditedIndex) as? Boolean
                ?: (existingConfig != null),
            lastAutoGeneratedProviderId = restored.getOrNull(autoProviderIdIndex) as? String
                ?: restored[0] as String,
        )
    },
)

internal fun parseManualModelIds(value: String): List<String> =
    normalizeModelIds(value.split('\n', ',', ';').map(String::trim))

private fun normalizeModelIds(values: List<String>): List<String> =
    values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

@Composable
private fun ProviderFormCard(
    cardColor: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(8.dp)),
    ) {
        content()
    }
}

@Composable
private fun ProviderFormDivider() {
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ProviderBaseUrlField(
    state: ProviderFormState,
    definition: PiProviderDefinition,
    resetToDefaultWhenBlank: Boolean = false,
) {
    ProviderFormTextField(
        label = stringResource(R.string.provider_form_base_url),
        value = state.baseUrl,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        onValueChange = { value ->
            state.baseUrl = if (resetToDefaultWhenBlank) {
                value.ifBlank { definition.defaultBaseUrl }
            } else {
                value
            }
        },
    )
}

@Composable
private fun ProviderFormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isSecret: Boolean = false,
    showLabel: Boolean = true,
) {
    var fieldValue by rememberSaveable(label, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    var passwordVisible by rememberSaveable(label) { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(ProviderFormPrimary),
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isSecret && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
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
                                tint = AetherOnSurfaceVariant,
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
private fun ProviderFormDropdownField(
    label: String,
    selectedValue: String,
    options: List<PiProviderDefinition>,
    onSelected: (PiProviderDefinition) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = stringResource(R.string.provider_form_choose_provider),
                tint = AetherOnSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AetherSurface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayName, color = AetherOnSurface)
                            Text(
                                option.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = AetherOnSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (option.displayName == selectedValue) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = AetherPrimary) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderAuthMethodField(
    definition: PiProviderDefinition,
    selectedMethod: ProviderAuthMethod,
    onSelected: (ProviderAuthMethod) -> Unit,
) {
    val methods = buildList {
        if (definition.supportsInteractiveApiKey) add(ProviderAuthMethod.ApiKey)
        if (definition.supportsOAuth) add(ProviderAuthMethod.OAuth)
        if (definition.supportsAmbientAuth) add(ProviderAuthMethod.Ambient)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.provider_form_authentication),
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            methods.forEach { method ->
                val label = when (method) {
                    ProviderAuthMethod.ApiKey -> stringResource(R.string.provider_form_auth_api_key)
                    ProviderAuthMethod.OAuth -> stringResource(R.string.provider_form_auth_oauth)
                    ProviderAuthMethod.Ambient -> stringResource(R.string.provider_form_auth_ambient)
                }
                ProviderModelListActionButton(
                    label = label,
                    onClick = { onSelected(method) },
                    enabled = method != selectedMethod,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProviderOAuthField(
    definition: PiProviderDefinition,
    credentialJson: String,
    oauthState: PiProviderAuthState?,
    onStartOAuthLogin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onCopyDeviceCode: (String) -> Unit,
    cardColor: Color,
) {
    val accountLabel = remember(credentialJson) { oauthAccountLabel(credentialJson) }
    val loginOptions = when (definition.id) {
        "openai-codex" -> listOf(
            Triple(
                Icons.Rounded.Language,
                R.string.provider_form_oauth_browser_login,
                OAuthFlowBrowser,
            ),
            Triple(
                Icons.Rounded.Key,
                R.string.provider_form_oauth_device_code_login,
                OAuthFlowDeviceCode,
            ),
        )

        "github-copilot" -> listOf(
            Triple(
                Icons.Rounded.Key,
                R.string.provider_form_oauth_device_code_login,
                OAuthFlowDeviceCode,
            )
        )

        else -> listOf(
            Triple(
                Icons.Rounded.Language,
                R.string.provider_form_oauth_browser_login,
                OAuthFlowBrowser,
            )
        )
    }
    ProviderFormCard(cardColor = cardColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.provider_form_oauth_title, definition.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
            Text(
                text = when {
                    oauthState?.errorMessage?.isNotBlank() == true -> oauthState.errorMessage
                    oauthState?.isRunning == true && oauthState.statusMessage.isNotBlank() ->
                        oauthState.statusMessage
                    credentialJson.isNotBlank() && accountLabel.isNotBlank() ->
                        stringResource(R.string.provider_form_oauth_connected_as, accountLabel)
                    credentialJson.isNotBlank() ->
                        stringResource(R.string.provider_form_oauth_connected)
                    else -> stringResource(R.string.provider_form_oauth_not_connected)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (oauthState?.errorMessage?.isNotBlank() == true) {
                    Color(0xFFD25757)
                } else {
                    AetherOnSurface
                },
            )
            oauthState?.deviceCode?.takeIf(String::isNotBlank)?.let { deviceCode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(AetherSurface)
                        .clickable { onCopyDeviceCode(deviceCode) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deviceCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Text(
                        text = stringResource(R.string.common_copy),
                        style = MaterialTheme.typography.labelLarge,
                        color = ProviderFormPrimary,
                    )
                }
            }
            loginOptions.forEach { (icon, labelResource, flow) ->
                ProviderActionRow(
                    icon = icon,
                    label = if (oauthState?.isRunning == true) {
                        stringResource(R.string.provider_form_oauth_waiting)
                    } else {
                        stringResource(labelResource)
                    },
                    onClick = { onStartOAuthLogin(flow) },
                    enabled = oauthState?.isRunning != true,
                )
            }
            if (credentialJson.isNotBlank()) {
                ProviderActionRow(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.provider_form_oauth_disconnect),
                    onClick = onDisconnect,
                    enabled = true,
                    trailingIcon = null,
                )
            }
        }
    }
}

internal fun oauthAccountLabel(credentialJson: String): String =
    runCatching {
        val credential = JSONObject(credentialJson)
        listOf("email", "accountName", "username", "login", "accountId")
            .firstNotNullOfOrNull { key ->
                credential.optString(key).trim().takeIf(String::isNotBlank)
            }
    }.getOrNull().orEmpty()

@Composable
private fun ProviderActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    trailingIcon: ImageVector? = Icons.Rounded.ArrowForward,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AetherSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) ProviderFormPrimary else AetherOnSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) AetherOnSurface else AetherOnSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f),
        )
        trailingIcon?.let { iconVector ->
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProviderModelListField(
    models: List<String>,
    enabledModelIds: List<String>,
    isFetchingModels: Boolean,
    onToggleModel: (String, Boolean) -> Unit,
    onSetAllModelsEnabled: (Boolean) -> Unit,
    onFetchModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.provider_form_model_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (models.isEmpty()) {
                        stringResource(R.string.provider_form_no_models_loaded)
                    } else {
                        stringResource(R.string.provider_form_models_enabled_count, enabledModelIds.size, models.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurface,
                )
            }
            if (isFetchingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = ProviderFormPrimary,
                )
            } else {
                IconButton(onClick = onFetchModels) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.provider_form_fetch_models),
                        tint = ProviderFormPrimary,
                    )
                }
            }
        }

        if (models.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProviderModelListActionButton(
                    label = stringResource(R.string.provider_form_select_all_models),
                    onClick = { onSetAllModelsEnabled(true) },
                    enabled = enabledModelIds.size < models.size,
                    modifier = Modifier.weight(1f),
                )
                ProviderModelListActionButton(
                    label = stringResource(R.string.provider_form_clear_all_models),
                    onClick = { onSetAllModelsEnabled(false) },
                    enabled = enabledModelIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleModel(model, !enabledModelIds.contains(model)) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = enabledModelIds.contains(model),
                            onCheckedChange = { checked -> onToggleModel(model, checked) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderModelListActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) {
                    ProviderFormPrimary.copy(alpha = 0.10f)
                } else {
                    AetherSurfaceHigh.copy(alpha = 0.55f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) ProviderFormPrimary else AetherOnSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ProviderCustomHeadersField(
    headers: List<LlmCustomHeader>,
    onAddHeader: () -> Unit,
    onUpdateHeader: (Int, String, String) -> Unit,
    onRemoveHeader: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.provider_form_custom_request_headers),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (headers.isEmpty()) stringResource(R.string.common_optional) else stringResource(R.string.provider_form_headers_configured_count, headers.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurface,
                )
            }
            IconButton(onClick = onAddHeader) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.provider_form_add_header),
                    tint = ProviderFormPrimary,
                )
            }
        }

        if (headers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                headers.forEachIndexed { index, header ->
                    ProviderCustomHeaderRow(
                        header = header,
                        onNameChange = { name -> onUpdateHeader(index, name, header.value) },
                        onValueChange = { value -> onUpdateHeader(index, header.name, value) },
                        onRemove = { onRemoveHeader(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderEnvironmentVariablesField(
    variables: List<PiProviderEnvironmentVariable>,
    onAddVariable: () -> Unit,
    onUpdateVariable: (Int, String, String) -> Unit,
    onRemoveVariable: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.provider_form_provider_environment),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (variables.isEmpty()) {
                        stringResource(R.string.common_optional)
                    } else {
                        stringResource(R.string.provider_form_environment_configured_count, variables.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurface,
                )
            }
            IconButton(onClick = onAddVariable) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.provider_form_add_environment),
                    tint = ProviderFormPrimary,
                )
            }
        }

        if (variables.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                variables.forEachIndexed { index, variable ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProviderFormTextField(
                            label = stringResource(R.string.provider_form_environment_name),
                            value = variable.name,
                            onValueChange = { name ->
                                onUpdateVariable(index, name, variable.value)
                            },
                            modifier = Modifier.weight(0.48f),
                        )
                        ProviderFormTextField(
                            label = stringResource(R.string.provider_form_environment_value),
                            value = variable.value,
                            onValueChange = { value ->
                                onUpdateVariable(index, variable.name, value)
                            },
                            modifier = Modifier.weight(0.52f),
                        )
                        IconButton(onClick = { onRemoveVariable(index) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.provider_form_remove_environment),
                                tint = AetherOnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCustomHeaderRow(
    header: LlmCustomHeader,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderFormTextField(
            label = stringResource(R.string.provider_form_header_name),
            value = header.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(0.42f),
        )
        ProviderFormTextField(
            label = stringResource(R.string.provider_form_header_value),
            value = header.value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(0.58f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.provider_form_remove_header),
                tint = AetherOnSurfaceVariant,
            )
        }
    }
}
