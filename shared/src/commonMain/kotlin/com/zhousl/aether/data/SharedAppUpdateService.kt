package com.zhousl.aether.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AetherBundleId = "com.baimoqilin.aether"
const val AetherAppStoreFallbackUrl = "https://apps.apple.com/us/search?term=Aether"

data class SharedAppUpdateStatus(
    val installedVersion: String,
    val storeVersion: String = "",
    val storeUrl: String = "",
    val isUpdateAvailable: Boolean = false,
    val isPublished: Boolean = false,
)

class SharedAppUpdateService(engine: HttpClientEngine? = null) {
    private val client = if (engine == null) HttpClient() else HttpClient(engine)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(installedVersion: String): SharedAppUpdateStatus {
        val response = client.get(
            "https://itunes.apple.com/lookup?bundleId=$AetherBundleId",
        )
        check(response.status.isSuccess()) {
            "App Store lookup failed with HTTP ${response.status.value}."
        }
        val result = json.decodeFromString<AppStoreLookupResponse>(response.body<String>())
            .results
            .firstOrNull()
        return SharedAppUpdateStatus(
            installedVersion = installedVersion,
            storeVersion = result?.version.orEmpty(),
            storeUrl = result?.trackViewUrl.orEmpty(),
            isUpdateAvailable = result?.version?.let {
                compareVersions(it, installedVersion) > 0
            } ?: false,
            isPublished = result != null,
        )
    }
}

@Serializable
private data class AppStoreLookupResponse(
    @SerialName("resultCount") val resultCount: Int = 0,
    val results: List<AppStoreLookupResult> = emptyList(),
)

@Serializable
private data class AppStoreLookupResult(
    val version: String = "",
    val trackViewUrl: String = "",
)

internal fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '+')
    val rightParts = right.split('.', '-', '+')
    val size = maxOf(leftParts.size, rightParts.size)
    repeat(size) { index ->
        val leftValue = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val rightValue = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (leftValue != rightValue) return leftValue.compareTo(rightValue)
    }
    return 0
}

