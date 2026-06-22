package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val GithubLatestReleaseUrl =
    "https://api.github.com/repos/Zhou-Shilin/Aether/releases/latest"
private const val GithubLatestNightlyRunUrl =
    "https://api.github.com/repos/Zhou-Shilin/Aether/actions/workflows/build-debug-apk.yml/runs?branch=main&status=success&per_page=1"
private const val NightlyArtifactDownloadUrl =
    "https://nightly.link/Zhou-Shilin/Aether/workflows/build-debug-apk.yml/main/Aether-nightly.zip"
private const val ApkMimeType = "application/vnd.android.package-archive"
private const val ZipMimeType = "application/zip"

data class AppUpdateRelease(
    val versionName: String,
    val tagName: String,
    val releaseUrl: String,
    val apkFileName: String,
    val apkDownloadUrl: String,
    val isZipArchive: Boolean = false,
)

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchLatestRelease(): AppUpdateRelease {
        val request = Request.Builder()
            .url(GithubLatestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Aether-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GitHub returned HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val releaseJson = JSONObject(body)
            val tagName = releaseJson.optString("tag_name").trim()
            val versionName = tagName.versionCore().ifBlank {
                releaseJson.optString("name").versionCore()
            }
            if (versionName.isBlank()) {
                error("Latest release did not include a version.")
            }

            val apkAsset = releaseJson.optJSONArray("assets")
                ?.let { assets ->
                    buildList {
                        for (index in 0 until assets.length()) {
                            assets.optJSONObject(index)?.let(::add)
                        }
                    }
                }
                ?.firstOrNull { asset ->
                    val name = asset.optString("name").trim()
                    name.endsWith(".apk", ignoreCase = true) ||
                        asset.optString("content_type").equals(ApkMimeType, ignoreCase = true)
                }
                ?: error("Latest release does not include an APK asset.")

            return AppUpdateRelease(
                versionName = versionName,
                tagName = tagName.ifBlank { versionName },
                releaseUrl = releaseJson.optString("html_url").trim(),
                apkFileName = apkAsset.optString("name").trim().ifBlank {
                    "Aether-$versionName.apk"
                },
                apkDownloadUrl = apkAsset.optString("browser_download_url").trim().ifBlank {
                    error("APK asset did not include a download URL.")
                },
            )
        }
    }

    suspend fun fetchLatestNightly(): AppUpdateRelease {
        val request = Request.Builder()
            .url(GithubLatestNightlyRunUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Aether-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GitHub returned HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val run = JSONObject(body)
                .optJSONArray("workflow_runs")
                ?.optJSONObject(0)
                ?: error("No successful Nightly workflow run was found.")
            val headSha = run.optString("head_sha").trim()
            if (headSha.isBlank()) {
                error("Latest Nightly workflow run did not include a commit hash.")
            }
            val shortSha = headSha.take(12)
            return AppUpdateRelease(
                versionName = "Nightly $shortSha",
                tagName = shortSha,
                releaseUrl = run.optString("html_url").trim(),
                apkFileName = "Aether-nightly-$shortSha.apk",
                apkDownloadUrl = NightlyArtifactDownloadUrl,
                isZipArchive = true,
            )
        }
    }

    suspend fun downloadApk(
        release: AppUpdateRelease,
        onProgress: (Float?) -> Unit,
    ): Uri {
        val request = Request.Builder()
            .url(release.apkDownloadUrl)
            .header("Accept", if (release.isZipArchive) ZipMimeType else ApkMimeType)
            .header("User-Agent", "Aether-Android")
            .build()

        val updatesDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val outputFile = File(updatesDirectory, release.apkFileName.sanitizeFileName())

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download failed with HTTP ${response.code}")
            }
            val body = response.body ?: error("Download returned an empty response.")
            val contentLength = body.contentLength()
            if (release.isZipArchive) {
                body.byteStream().use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                outputFile.outputStream().use { output ->
                                    zip.copyTo(output)
                                }
                                zip.closeEntry()
                                onProgress(1f)
                                return@use
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    error("Nightly artifact did not include an APK.")
                }
            } else {
                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            totalBytes += read
                            onProgress(
                                if (contentLength > 0) {
                                    (totalBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile,
        )
    }
}

fun isNightlyUpdateNewer(
    remoteVersion: String,
    currentVersion: String,
): Boolean {
    val remoteHash = remoteVersion.nightlyHash()
    val currentHash = currentVersion.nightlyHash()
    return remoteHash.isNotBlank() && remoteHash != currentHash
}

fun isVersionNewer(
    remoteVersion: String,
    currentVersion: String,
): Boolean {
    val remoteParts = remoteVersion.versionCore().numericVersionParts()
    val currentParts = currentVersion.versionCore().numericVersionParts()
    val maxSize = maxOf(remoteParts.size, currentParts.size)
    for (index in 0 until maxSize) {
        val remotePart = remoteParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (remotePart != currentPart) {
            return remotePart > currentPart
        }
    }
    return false
}

private fun String.versionCore(): String =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('+')
        .substringBefore('-')
        .trim()

private fun String.nightlyHash(): String =
    trim()
        .substringAfter("Nightly", "")
        .trim()
        .substringBefore(' ')
        .take(12)

private fun String.numericVersionParts(): List<Int> =
    split(Regex("[^0-9]+"))
        .mapNotNull { it.toIntOrNull() }

private fun String.sanitizeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .ifBlank { "Aether-update.apk" }
        .let { value ->
            if (value.lowercase(Locale.US).endsWith(".apk")) value else "$value.apk"
        }
