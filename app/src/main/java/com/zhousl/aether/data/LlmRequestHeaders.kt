package com.zhousl.aether.data

import com.zhousl.aether.BuildConfig
import java.net.HttpURLConnection

private val HttpHeaderNamePattern = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

internal fun String.isSafeHttpHeaderValue(): Boolean = '\r' !in this && '\n' !in this

internal val AetherLlmUserAgent: String
    get() = "Aether/${BuildConfig.VERSION_NAME} (Android)"

internal fun HttpURLConnection.applyProviderLlmHeaders(
    config: LlmProviderConfig,
) {
    config.customHeaders.normalizedLlmHeaders()
        .filterNot { header -> header.name.equals("User-Agent", ignoreCase = true) }
        .forEach { header -> setRequestProperty(header.name, header.value) }
    when (config.userAgentMode) {
        ProviderUserAgentMode.Aether -> setRequestProperty("User-Agent", AetherLlmUserAgent)
        ProviderUserAgentMode.Custom -> config.customUserAgent?.trim().orEmpty()
            .takeIf { value -> value.isNotBlank() && value.isSafeHttpHeaderValue() }
            ?.let { value -> setRequestProperty("User-Agent", value) }
        ProviderUserAgentMode.Default -> Unit
    }
}

internal fun List<LlmCustomHeader>.normalizedLlmHeaders(): List<LlmCustomHeader> =
    map { header -> LlmCustomHeader(header.name.trim(), header.value) }
        .filter { header ->
            header.name.isNotBlank() &&
                HttpHeaderNamePattern.matches(header.name) &&
                header.value.isSafeHttpHeaderValue()
        }
        .distinctBy { header -> header.name.lowercase() }
