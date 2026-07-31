package com.zhousl.aether.data.pi

import com.zhousl.aether.data.PiProviderEnvironmentVariable
import com.zhousl.aether.data.ProviderAuthMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PiOAuthPromptOption(
    val id: String,
    val label: String,
    val description: String = "",
)

data class PiOAuthPrompt(
    val id: String,
    val type: String,
    val message: String,
    val placeholder: String = "",
    val options: List<PiOAuthPromptOption> = emptyList(),
)

data class PiProviderAuthState(
    val providerId: String = "",
    val authMethod: ProviderAuthMethod = ProviderAuthMethod.ApiKey,
    val isRunning: Boolean = false,
    val statusMessage: String = "",
    val authorizationUrl: String = "",
    val deviceCode: String = "",
    val verificationUrl: String = "",
    val prompt: PiOAuthPrompt? = null,
    val apiKey: String = "",
    val oauthCredentialJson: String = "",
    val providerEnvironmentVariables: List<PiProviderEnvironmentVariable> = emptyList(),
    val errorMessage: String = "",
)

enum class PiCoreSetupPhase(
    val step: Int,
) {
    Idle(step = 0),
    CheckingAlpine(step = 1),
    CheckingNode(step = 2),
    InstallingNode(step = 2),
    PreparingBridge(step = 3),
    StartingBridge(step = 4),
    VerifyingBridge(step = 5),
    Ready(step = 5),
    Failed(step = 0),
}

enum class PiCoreSetupActivity {
    None,
    Extracting,
    Downloading,
}

data class PiCoreSetupUpdate(
    val phase: PiCoreSetupPhase,
    val activity: PiCoreSetupActivity = PiCoreSetupActivity.None,
    val bytesPerSecond: Long = 0L,
    val output: String = "",
)

data class PiCoreSetupState(
    val isChecking: Boolean = false,
    val isReady: Boolean = false,
    val phase: PiCoreSetupPhase = PiCoreSetupPhase.Idle,
    val failedAtPhase: PiCoreSetupPhase = PiCoreSetupPhase.Idle,
    val detail: String = "",
    val nodeVersion: String = "",
    val bridgeVersion: String = "",
    val activity: PiCoreSetupActivity = PiCoreSetupActivity.None,
    val bytesPerSecond: Long = 0L,
    val output: String = "",
)

fun JsonObject.toPiOAuthPrompt(): PiOAuthPrompt = PiOAuthPrompt(
    id = string("prompt_id"),
    type = string("prompt_type"),
    message = string("message"),
    placeholder = string("placeholder"),
    options = (get("options") as? JsonArray).toPiOAuthPromptOptions(),
)

fun JsonObject.toPiProviderEnvironmentVariables(): List<PiProviderEnvironmentVariable> =
    (get("provider_env") as? JsonObject)?.let { environment ->
        environment.keys
            .mapNotNull { name ->
                name.trim()
                    .takeIf(String::isNotEmpty)
                    ?.let { normalizedName ->
                        PiProviderEnvironmentVariable(
                            name = normalizedName,
                            value = environment.string(name),
                        )
                    }
            }
            .toList()
    }.orEmpty()

private fun JsonArray?.toPiOAuthPromptOptions(): List<PiOAuthPromptOption> {
    if (this == null) return emptyList()
    return buildList {
        this@toPiOAuthPromptOptions.forEach { element ->
            val option = element as? JsonObject ?: return@forEach
            add(
                PiOAuthPromptOption(
                    id = option.string("id"),
                    label = option.string("label"),
                    description = option.string("description"),
                )
            )
        }
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
