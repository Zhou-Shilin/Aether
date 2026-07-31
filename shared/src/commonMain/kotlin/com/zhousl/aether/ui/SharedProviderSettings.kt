package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AutomaticModelPurpose
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderCatalog
import com.zhousl.aether.data.ProviderAuthMethod
import com.zhousl.aether.data.ProviderModelOption
import com.zhousl.aether.data.SharedProviderModelCatalogClient
import com.zhousl.aether.data.availableModelOptions
import com.zhousl.aether.data.availableModels
import com.zhousl.aether.data.enabledModels
import com.zhousl.aether.data.findModelOption
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.data.resolveAutomaticModelKey
import com.zhousl.aether.data.sortedForAutomaticModelPurpose
import com.zhousl.aether.data.pi.PiProviderAuthState
import com.zhousl.aether.data.pi.toPiOAuthPrompt
import com.zhousl.aether.data.pi.toPiProviderEnvironmentVariables
import com.zhousl.aether.runtime.SharedPiBridgeClient
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

private enum class SharedProviderSettingsPage {
    Providers,
    DefaultModels,
    DefaultChatModel,
    DefaultTitleModel,
    DefaultNamingModel,
    DefaultCompactingModel,
    AddProvider,
    EditProvider,
}

private fun Throwable.sharedProviderUserFacingMessage(): String =
    message?.trim().takeUnless { it.isNullOrBlank() }
        ?: this::class.simpleName.orEmpty().ifBlank { "Unknown error." }

private fun SharedProviderSettingsPage.depth(): Int = when (this) {
    SharedProviderSettingsPage.Providers -> 0
    SharedProviderSettingsPage.DefaultModels,
    SharedProviderSettingsPage.AddProvider,
    SharedProviderSettingsPage.EditProvider -> 1
    SharedProviderSettingsPage.DefaultChatModel,
    SharedProviderSettingsPage.DefaultTitleModel,
    SharedProviderSettingsPage.DefaultNamingModel,
    SharedProviderSettingsPage.DefaultCompactingModel -> 2
}

internal fun resolveSharedActiveProviderConfigId(
    providerConfigs: List<LlmProviderConfig>,
    preferredActiveConfigId: String,
): String = preferredActiveConfigId.takeIf { configId ->
    providerConfigs.any { it.id == configId && it.isEnabled }
} ?: providerConfigs.firstOrNull { it.isEnabled }?.id
    ?: providerConfigs.firstOrNull()?.id.orEmpty()

@Composable
internal fun SharedProviderSettingsDetail(
    providerConfigs: List<LlmProviderConfig>,
    appSettings: AppSettings,
    bridgeClient: SharedPiBridgeClient,
    onUpsertProvider: (LlmProviderConfig) -> Unit,
    onSetProviderEnabled: (String, Boolean) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSettingsSaved: (AppSettings) -> Unit,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(SharedProviderSettingsPage.Providers.name) }
    var editingProviderConfigId by rememberSaveable { mutableStateOf("") }
    val page = SharedProviderSettingsPage.valueOf(pageName)
    val modelOptions = providerConfigs.availableModelOptions()

    fun show(page: SharedProviderSettingsPage) {
        pageName = page.name
    }

    SharedSettingsPageTransition(
        targetState = page,
        depth = SharedProviderSettingsPage::depth,
        label = "provider_settings_page_transition",
    ) { currentPage ->
        when (currentPage) {
        SharedProviderSettingsPage.Providers -> SharedProvidersListPage(
            providerConfigs = providerConfigs,
            onSetProviderEnabled = onSetProviderEnabled,
            onOpenDefaultModels = { show(SharedProviderSettingsPage.DefaultModels) },
            onEdit = { configId ->
                editingProviderConfigId = configId
                show(SharedProviderSettingsPage.EditProvider)
            },
            onRemove = onRemoveProvider,
            onAddNew = { show(SharedProviderSettingsPage.AddProvider) },
            onBack = onBack,
        )

        SharedProviderSettingsPage.DefaultModels -> SharedDefaultModelsPage(
            modelOptions = modelOptions,
            appSettings = appSettings,
            onOpenDefaultChatModel = { show(SharedProviderSettingsPage.DefaultChatModel) },
            onOpenDefaultTitleModel = { show(SharedProviderSettingsPage.DefaultTitleModel) },
            onOpenDefaultNamingModel = { show(SharedProviderSettingsPage.DefaultNamingModel) },
            onOpenDefaultCompactingModel = { show(SharedProviderSettingsPage.DefaultCompactingModel) },
            onBack = { show(SharedProviderSettingsPage.Providers) },
        )

        SharedProviderSettingsPage.DefaultChatModel -> SharedModelSelectionListPage(
            title = stringResource(Res.string.settings_default_chat_model),
            subtitle = stringResource(Res.string.settings_default_chat_model_subtitle),
            selectedKey = appSettings.defaultChatModelKey,
            options = modelOptions,
            purpose = AutomaticModelPurpose.Chat,
            automaticModelKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat),
            onSelected = { onSettingsSaved(appSettings.copy(defaultChatModelKey = it)) },
            onBack = { show(SharedProviderSettingsPage.DefaultModels) },
        )

        SharedProviderSettingsPage.DefaultTitleModel -> SharedModelSelectionListPage(
            title = stringResource(Res.string.settings_default_title_model),
            subtitle = stringResource(Res.string.settings_default_title_model_subtitle),
            selectedKey = appSettings.defaultTitleModelKey,
            options = modelOptions,
            purpose = AutomaticModelPurpose.Title,
            automaticModelKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Title)
                .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
            onSelected = { onSettingsSaved(appSettings.copy(defaultTitleModelKey = it)) },
            onBack = { show(SharedProviderSettingsPage.DefaultModels) },
        )

        SharedProviderSettingsPage.DefaultNamingModel -> SharedModelSelectionListPage(
            title = stringResource(Res.string.settings_default_naming_model),
            subtitle = stringResource(Res.string.settings_default_naming_model_subtitle),
            selectedKey = appSettings.defaultNamingModelKey,
            options = modelOptions,
            purpose = AutomaticModelPurpose.Naming,
            automaticModelKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Naming)
                .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
            onSelected = { onSettingsSaved(appSettings.copy(defaultNamingModelKey = it)) },
            onBack = { show(SharedProviderSettingsPage.DefaultModels) },
        )

        SharedProviderSettingsPage.DefaultCompactingModel -> SharedModelSelectionListPage(
            title = stringResource(Res.string.settings_default_compacting_model),
            subtitle = stringResource(Res.string.settings_default_compacting_model_subtitle),
            selectedKey = appSettings.defaultCompactingModelKey,
            options = modelOptions,
            purpose = AutomaticModelPurpose.Compacting,
            automaticModelKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Compacting)
                .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
            onSelected = { onSettingsSaved(appSettings.copy(defaultCompactingModelKey = it)) },
            onBack = { show(SharedProviderSettingsPage.DefaultModels) },
        )

        SharedProviderSettingsPage.AddProvider -> SharedProviderEditPage(
            existingConfig = null,
            existingProviderIds = providerConfigs.map(LlmProviderConfig::providerId).toSet(),
            bridgeClient = bridgeClient,
            onTransientMessage = onTransientMessage,
            onSave = {
                onUpsertProvider(it)
                show(SharedProviderSettingsPage.Providers)
            },
            onModelEnabledChange = onUpsertProvider,
            onBack = { show(SharedProviderSettingsPage.Providers) },
        )

            SharedProviderSettingsPage.EditProvider -> SharedProviderEditPage(
                existingConfig = providerConfigs.firstOrNull { it.id == editingProviderConfigId },
                existingProviderIds = providerConfigs.map(LlmProviderConfig::providerId).toSet(),
                bridgeClient = bridgeClient,
                onTransientMessage = onTransientMessage,
                onSave = {
                    onUpsertProvider(it)
                    show(SharedProviderSettingsPage.Providers)
                },
                onModelEnabledChange = onUpsertProvider,
                onBack = { show(SharedProviderSettingsPage.Providers) },
            )
        }
    }
}

@Composable
private fun SharedProvidersListPage(
    providerConfigs: List<LlmProviderConfig>,
    onSetProviderEnabled: (String, Boolean) -> Unit,
    onOpenDefaultModels: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddNew: () -> Unit,
    onBack: () -> Unit,
) {
    SharedProviderPageScaffold(
        title = stringResource(Res.string.settings_model_providers),
        onBack = onBack,
        trailingIcon = Icons.Rounded.Add,
        trailingContentDescription = stringResource(Res.string.settings_add_provider),
        onTrailingAction = onAddNew,
    ) {
        if (providerConfigs.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_no_providers_configured),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.settings_add_provider_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                    SharedSettingsActionButton(
                        label = stringResource(Res.string.settings_add_provider),
                        onClick = onAddNew,
                    )
                }
            }
        } else {
            providerConfigs.forEach { config ->
                SharedProviderCard(
                    config = config,
                    onEnabledChange = { onSetProviderEnabled(config.id, it) },
                    onEdit = { onEdit(config.id) },
                    onRemove = { onRemove(config.id) },
                )
                Spacer(Modifier.size(12.dp))
            }
        }

        Spacer(Modifier.size(8.dp))
        SettingsCardGroup {
            SettingsNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(Res.string.settings_default_models),
                subtitle = stringResource(Res.string.settings_default_models_subtitle),
                onClick = onOpenDefaultModels,
            )
        }
    }
}

@Composable
private fun SharedProviderCard(
    config: LlmProviderConfig,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val availableModels = config.availableModels()
    val enabledModels = config.enabledModels()
    val provider = PiProviderCatalog.resolve(config.piProviderId)

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = config.isEnabled, onCheckedChange = onEnabledChange)
        Spacer(Modifier.width(10.dp))
        ProviderBrandIconBadge(
            provider = provider,
            badgeSize = 40.dp,
            iconSize = 25.dp,
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = "${provider.displayName} · ${config.providerId}",
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(
                    Res.string.settings_provider_models_enabled_count,
                    enabledModels.size,
                    availableModels.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(Res.string.action_edit),
                tint = AetherOnSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(Res.string.action_remove),
                tint = Color(0xFFD25757),
            )
        }
    }
}

@Composable
private fun SharedDefaultModelsPage(
    modelOptions: List<ProviderModelOption>,
    appSettings: AppSettings,
    onOpenDefaultChatModel: () -> Unit,
    onOpenDefaultTitleModel: () -> Unit,
    onOpenDefaultNamingModel: () -> Unit,
    onOpenDefaultCompactingModel: () -> Unit,
    onBack: () -> Unit,
) {
    SharedProviderPageScaffold(
        title = stringResource(Res.string.settings_default_models),
        onBack = onBack,
    ) {
        SettingsCardGroup {
            SharedDefaultModelNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(Res.string.settings_default_chat_model),
                selectedKey = appSettings.defaultChatModelKey,
                automaticKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat),
                modelOptions = modelOptions,
                onClick = onOpenDefaultChatModel,
            )
            CardDivider()
            SharedDefaultModelNavRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(Res.string.settings_default_title_model),
                selectedKey = appSettings.defaultTitleModelKey,
                automaticKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Title)
                    .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
                modelOptions = modelOptions,
                onClick = onOpenDefaultTitleModel,
            )
            CardDivider()
            SharedDefaultModelNavRow(
                icon = Icons.Rounded.Person,
                title = stringResource(Res.string.settings_default_naming_model),
                selectedKey = appSettings.defaultNamingModelKey,
                automaticKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Naming)
                    .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
                modelOptions = modelOptions,
                onClick = onOpenDefaultNamingModel,
            )
            CardDivider()
            SharedDefaultModelNavRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(Res.string.settings_default_compacting_model),
                selectedKey = appSettings.defaultCompactingModelKey,
                automaticKey = modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Compacting)
                    .ifBlank { modelOptions.resolveAutomaticModelKey(AutomaticModelPurpose.Chat) },
                modelOptions = modelOptions,
                onClick = onOpenDefaultCompactingModel,
            )
        }
    }
}

@Composable
private fun SharedDefaultModelNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedKey: String,
    automaticKey: String,
    modelOptions: List<ProviderModelOption>,
    onClick: () -> Unit,
) {
    val subtitle = if (selectedKey.isBlank()) {
        modelOptions.findModelOption(automaticKey)?.fullLabel?.let {
            stringResource(Res.string.settings_automatic_model_with_name, it)
        } ?: stringResource(Res.string.settings_automatic_model)
    } else {
        modelOptions.findModelOption(selectedKey)?.fullLabel.orEmpty()
    }
    SettingsNavRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick)
}

@Composable
private fun SharedModelSelectionListPage(
    title: String,
    subtitle: String,
    selectedKey: String,
    options: List<ProviderModelOption>,
    purpose: AutomaticModelPurpose,
    automaticModelKey: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val selectedOption = options.findModelOption(selectedKey)
    val sortedOptions = remember(options, purpose) { options.sortedForAutomaticModelPurpose(purpose) }
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val query = searchQuery.text.trim()
    val filteredOptions = remember(sortedOptions, query) {
        if (query.isBlank()) {
            sortedOptions
        } else {
            sortedOptions.filter { option ->
                option.fullLabel.contains(query, ignoreCase = true) ||
                    option.modelId.contains(query, ignoreCase = true) ||
                    option.providerName.contains(query, ignoreCase = true) ||
                    option.providerId.contains(query, ignoreCase = true) ||
                    PiProviderCatalog.resolve(option.piProviderId).displayName.contains(query, ignoreCase = true)
            }
        }
    }
    val automaticLabel = options.findModelOption(automaticModelKey)?.fullLabel?.let {
        stringResource(Res.string.settings_automatic_model_with_name, it)
    } ?: stringResource(Res.string.settings_automatic_model)

    SharedProviderPageScaffold(title = title, onBack = onBack) {
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
        Spacer(Modifier.size(14.dp))
        if (options.isEmpty()) {
            SettingsCardGroup {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.settings_no_enabled_models_available),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                    Text(
                        text = stringResource(Res.string.settings_enable_provider_model_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
            return@SharedProviderPageScaffold
        }

        SettingsCardGroup {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).sharedSettingsBringIntoViewOnFocus(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
                    cursorBrush = SolidColor(AetherPrimary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.text.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.settings_search_models),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        SettingsCardGroup {
            SharedModelSelectionRow(
                title = automaticLabel,
                subtitle = stringResource(
                    if (purpose == AutomaticModelPurpose.Compacting) {
                        Res.string.settings_prioritize_efficient_summary_models
                    } else {
                        Res.string.settings_prioritize_sota_models
                    },
                ),
                selected = selectedOption == null,
                onClick = { onSelected("") },
            )
            filteredOptions.forEach { option ->
                CardDivider()
                SharedModelSelectionRow(
                    title = option.fullLabel,
                    subtitle = option.providerName,
                    selected = option.key == selectedOption?.key,
                    onClick = { onSelected(option.key) },
                )
            }
            if (filteredOptions.isEmpty()) {
                CardDivider()
                Text(
                    text = stringResource(Res.string.settings_no_models_match_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedModelSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) AetherBackground.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AetherOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = if (selected) AetherPrimary else Color.Transparent,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SharedProviderEditPage(
    existingConfig: LlmProviderConfig?,
    existingProviderIds: Set<String>,
    bridgeClient: SharedPiBridgeClient,
    onTransientMessage: (String) -> Unit,
    onSave: (LlmProviderConfig) -> Unit,
    onModelEnabledChange: (LlmProviderConfig) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val formState = rememberProviderFormState(existingConfig)
    ReportSharedSettingsUnsavedChanges(
        existingConfig != null &&
            formState.buildConfig().copy(updatedAtMillis = existingConfig.updatedAtMillis) != existingConfig,
    )
    val modelCatalogClient = remember { SharedProviderModelCatalogClient() }
    var authState by remember(existingConfig?.id) { mutableStateOf(PiProviderAuthState()) }
    var authJob by remember(existingConfig?.id) { mutableStateOf<Job?>(null) }
    var fetchingModels by remember(existingConfig?.id) { mutableStateOf(false) }
    val oauthWaitingMessage = "Waiting for authorization."
    val credentialsWaitingMessage = "Waiting for credentials."
    val oauthConnectedMessage = "Connected with OAuth."
    val apiKeyConfiguredMessage = "API key configured."
    val fetchErrorPlaceholder = "{fetch_error}"
    val fetchModelsFailedTemplate = stringResource(
        Res.string.message_fetch_models_failed,
        fetchErrorPlaceholder,
    )

    DisposableEffect(existingConfig?.id) {
        onDispose { authJob?.cancel() }
    }

    fun fetchModels(config: LlmProviderConfig, callback: (List<String>) -> Unit) {
        fetchingModels = true
        scope.launch {
            try {
                val result = modelCatalogClient.fetchModels(config, bridgeClient::listProviders)
                callback(result.models)
                result.error?.let { error ->
                    onTransientMessage(
                        fetchModelsFailedTemplate.replace(
                            fetchErrorPlaceholder,
                            error.trim().ifBlank { "Unknown error." },
                        ),
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                callback(emptyList())
                onTransientMessage(
                    fetchModelsFailedTemplate.replace(
                        fetchErrorPlaceholder,
                        failure.sharedProviderUserFacingMessage(),
                    )
                )
            } finally {
                fetchingModels = false
            }
        }
    }

    fun startLogin(configId: String, providerId: String, authMethod: ProviderAuthMethod, oauthFlow: String) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isBlank() || authMethod == ProviderAuthMethod.Ambient) return
        authJob?.cancel()
        authState = PiProviderAuthState(
            providerId = normalizedProviderId,
            authMethod = authMethod,
            isRunning = true,
            statusMessage = if (authMethod == ProviderAuthMethod.OAuth) {
                oauthWaitingMessage
            } else {
                credentialsWaitingMessage
            },
        )
        authJob = scope.launch {
            runCatching {
                bridgeClient.loginProvider(
                    providerConfigId = configId,
                    providerId = normalizedProviderId,
                    authMethod = authMethod.storageValue,
                    oauthFlow = oauthFlow,
                ) { event, payload ->
                    if (
                        authState.providerId == normalizedProviderId &&
                        authState.authMethod == authMethod
                    ) {
                        authState = authState.withSharedProviderBridgeEvent(event, payload)
                    }
                }
            }.fold(
                onSuccess = { payload ->
                    if (
                        authState.providerId == normalizedProviderId &&
                        authState.authMethod == authMethod
                    ) {
                        authState = authState.copy(
                            isRunning = false,
                            prompt = null,
                            apiKey = payload.sharedProviderString("api_key"),
                            oauthCredentialJson = (payload["oauth_credential"] as? JsonObject)?.toString().orEmpty(),
                            providerEnvironmentVariables = payload.toPiProviderEnvironmentVariables(),
                            statusMessage = if (authMethod == ProviderAuthMethod.OAuth) {
                                oauthConnectedMessage
                            } else {
                                apiKeyConfiguredMessage
                            },
                            errorMessage = "",
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) return@fold
                    if (
                        authState.providerId == normalizedProviderId &&
                        authState.authMethod == authMethod
                    ) {
                        authState = authState.copy(
                            isRunning = false,
                            prompt = null,
                            statusMessage = "",
                            errorMessage = error.sharedProviderUserFacingMessage(),
                        )
                    }
                },
            )
        }
    }

    fun submitPrompt(promptId: String, value: String, cancelled: Boolean) {
        scope.launch {
            try {
                bridgeClient.submitAuthPrompt(promptId, value, cancelled)
                if (authState.prompt?.id == promptId) {
                    authState = authState.copy(prompt = null)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (authState.prompt?.id == promptId) {
                    authState = authState.copy(errorMessage = failure.sharedProviderUserFacingMessage())
                }
            }
        }
    }

    fun clearAuthState() {
        authJob?.cancel()
        authJob = null
        authState = PiProviderAuthState()
    }

    SharedProviderPageScaffold(
        title = stringResource(
            if (existingConfig == null) Res.string.settings_add_provider else Res.string.settings_edit_provider,
        ),
        onBack = onBack,
        trailingIcon = Icons.Rounded.Check.takeIf { existingConfig != null },
        trailingContentDescription = stringResource(Res.string.common_save),
        trailingEnabled = existingConfig == null || formState.isValid(existingProviderIds),
        onTrailingAction = if (existingConfig == null) null else {
            {
                onSave(formState.buildConfig().copy(updatedAtMillis = platformCurrentTimeMillis()))
            }
        },
    ) {
        if (existingConfig == null) {
            AddProviderWizard(
                state = formState,
                existingProviderIds = existingProviderIds,
                isFetchingModels = fetchingModels,
                onFetchModels = ::fetchModels,
                authState = authState,
                onStartProviderLogin = ::startLogin,
                onSubmitAuthPrompt = ::submitPrompt,
                onClearAuthState = ::clearAuthState,
                onSave = { onSave(it.copy(updatedAtMillis = platformCurrentTimeMillis())) },
            )
        } else {
            ProviderConfigurationForm(
                state = formState,
                existingProviderIds = existingProviderIds,
                isFetchingModels = fetchingModels,
                onFetchModels = ::fetchModels,
                onModelEnabledChange = {
                    onModelEnabledChange(it.copy(updatedAtMillis = platformCurrentTimeMillis()))
                },
                authState = authState,
                onStartProviderLogin = ::startLogin,
                onSubmitAuthPrompt = ::submitPrompt,
                onClearAuthState = ::clearAuthState,
            )
        }
    }
}

@Composable
private fun SharedProviderPageScaffold(
    title: String,
    onBack: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingContentDescription: String = "",
    trailingEnabled: Boolean = true,
    onTrailingAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    top = sharedSettingsContentTopPadding(),
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                )
                .imePadding().navigationBarsPadding(),
        ) {
            content()
        }
        SettingsTopBar(
            title = title,
            onBack = onBack,
            trailingIcon = trailingIcon.takeIf { onTrailingAction != null },
            trailingEnabled = trailingEnabled,
            trailingContentDescription = trailingContentDescription,
            onTrailingAction = onTrailingAction ?: {},
        )
    }
}

private fun PiProviderAuthState.withSharedProviderBridgeEvent(
    event: String,
    payload: JsonObject,
): PiProviderAuthState = when (event) {
    "auth_url" -> copy(
        authorizationUrl = payload.sharedProviderString("url"),
        statusMessage = payload.sharedProviderString("instructions").ifBlank {
            "Complete authorization in your browser."
        },
    )
    "auth_device_code" -> copy(
        deviceCode = payload.sharedProviderString("user_code"),
        verificationUrl = payload.sharedProviderString("verification_uri"),
        statusMessage = "Enter the device code in your browser.",
    )
    "auth_prompt" -> copy(
        prompt = payload.toPiOAuthPrompt(),
        statusMessage = payload.sharedProviderString("message"),
    )
    "auth_progress" -> copy(statusMessage = payload.sharedProviderString("message"))
    else -> this
}

private fun JsonObject.sharedProviderString(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
