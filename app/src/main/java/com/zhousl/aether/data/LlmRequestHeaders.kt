package com.zhousl.aether.data

import okhttp3.Request
import java.net.HttpURLConnection

internal fun Request.Builder.applyAetherLlmHeaders(
    userAgent: String,
    customHeaders: List<LlmCustomHeader>,
): Request.Builder = apply {
    header("User-Agent", normalizeLlmUserAgent(userAgent))
    customHeaders.normalizedLlmHeaders().forEach { header ->
        header(header.name, header.value)
    }
}

internal fun HttpURLConnection.applyAetherLlmHeaders(
    userAgent: String,
    customHeaders: List<LlmCustomHeader>,
) {
    setRequestProperty("User-Agent", normalizeLlmUserAgent(userAgent))
    customHeaders.normalizedLlmHeaders().forEach { header ->
        setRequestProperty(header.name, header.value)
    }
}
