package com.zhousl.aether.data

fun normalizeSelectableModelKey(
    modelKey: String,
    options: List<ProviderModelOption>,
): String = if (options.any { it.key == modelKey }) modelKey else ""

fun resolveStoredOrAutomaticModelKey(
    modelKey: String,
    options: List<ProviderModelOption>,
    purpose: AutomaticModelPurpose,
    fallbackPurpose: AutomaticModelPurpose? = null,
    preferredAutomaticModelKey: String = "",
): String = normalizeSelectableModelKey(modelKey, options)
    .ifBlank {
        normalizeSelectableModelKey(preferredAutomaticModelKey, options).ifBlank {
            options.resolveAutomaticModelKey(purpose)
        }.ifBlank {
            fallbackPurpose?.let(options::resolveAutomaticModelKey).orEmpty()
        }
    }

fun resolveDefaultChatModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultChatModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveDefaultTitleModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    if (settings.defaultTitleModelKey == DisabledAuxiliaryModelKey) return DisabledAuxiliaryModelKey
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultTitleModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Title,
        fallbackPurpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveDefaultNamingModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    if (settings.defaultNamingModelKey == DisabledAuxiliaryModelKey) return DisabledAuxiliaryModelKey
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultNamingModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Naming,
        fallbackPurpose = AutomaticModelPurpose.Chat,
    )
}

fun resolveDefaultCompactingModelKey(
    settings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
): String {
    val options = providerConfigs.availableModelOptions()
    return resolveStoredOrAutomaticModelKey(
        modelKey = settings.defaultCompactingModelKey,
        options = options,
        purpose = AutomaticModelPurpose.Compacting,
        fallbackPurpose = AutomaticModelPurpose.Chat,
    )
}

fun AppSettings.withExplicitDefaultChatModel(
    providerConfig: LlmProviderConfig,
): AppSettings {
    val selectedModelId = providerConfig.modelId.trim()
    if (selectedModelId.isBlank()) return this
    val selectableConfig = providerConfig.copy(
        isEnabled = true,
        cachedModels = providerConfig.cachedModels + selectedModelId,
        enabledModelIds = providerConfig.enabledModelIds + selectedModelId,
    )
    val selectedOption = listOf(selectableConfig)
        .availableModelOptions()
        .firstOrNull { it.modelId == selectedModelId }
        ?: return this
    return withModelOption(selectedOption).copy(defaultChatModelKey = selectedOption.key)
}

fun resolveModelSettings(
    baseSettings: AppSettings,
    providerConfigs: List<LlmProviderConfig>,
    preferredModelKey: String,
    fallbackModelKey: String,
): AppSettings {
    val options = providerConfigs.availableModelOptions()
    val selectedOption = options.findModelOption(preferredModelKey)
        ?: options.firstOrNull { it.modelId == preferredModelKey }
        ?: options.firstOrNull { it.fullLabel == preferredModelKey }
        ?: options.findModelOption(fallbackModelKey)
        ?: options.firstOrNull()
    return selectedOption?.let(baseSettings::withModelOption) ?: baseSettings
}
