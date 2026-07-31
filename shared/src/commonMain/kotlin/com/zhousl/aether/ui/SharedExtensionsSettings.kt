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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zhousl.aether.data.SharedAetherExtensionError
import com.zhousl.aether.data.SharedAetherExtensionManager
import com.zhousl.aether.data.SharedAetherExtensionSnapshot
import com.zhousl.aether.data.SharedExtensionStateStore
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.platform.PlatformPickedFile
import com.zhousl.aether.platform.PlatformServices
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.runtime.RuntimeProcessSpec
import com.zhousl.aether.runtime.SharedPiBridgeClient
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.stringResource

private const val SharedPiPackagesUrl = "https://pi.dev/packages"
private const val SharedExtensionImportLimitBytes = 32 * 1024 * 1024
private const val SharedExtensionExtractedLimitBytes = 128L * 1024L * 1024L
private const val SharedExtensionSingleEntryLimitBytes = 16L * 1024L * 1024L
private const val SharedExtensionEntryLimit = 4096
private val SharedExtensionFileSuffixes = setOf("js", "ts", "mjs", "mts", "cjs", "cts")
private val SharedExtensionIndexNames = SharedExtensionFileSuffixes.map { "index.$it" }

private enum class SharedExtensionInstallKind {
    Package,
    Imported,
}

private data class SharedInstalledExtension(
    val id: String,
    val source: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    val installedPath: String = "",
    val extensionCount: Int = 0,
    val aetherExtensionCount: Int = 0,
    val skillCount: Int = 0,
    val promptCount: Int = 0,
    val themeCount: Int = 0,
    val isEnabled: Boolean = true,
    val kind: SharedExtensionInstallKind,
)

private enum class SharedPiCompatibilityIssue {
    InteractiveUi,
    Theme,
    Prompt,
    Platform,
}

private data class SharedPiCatalogEntry(
    val name: String,
    val source: String,
    val description: String,
    val author: String,
    val monthlyDownloads: Long,
    val packageUrl: String,
    val npmUrl: String,
    val repositoryUrl: String,
    val types: List<String>,
    val compatibilityIssue: SharedPiCompatibilityIssue?,
)

private data class SharedPiPackageDetails(
    val source: String,
    val name: String,
    val description: String,
    val version: String,
    val published: String,
    val downloads: String,
    val author: String,
    val license: String,
    val size: String,
    val dependencies: String,
    val types: List<String>,
    val manifestJson: String,
    val readmeMarkdown: String,
    val npmUrl: String,
    val repositoryUrl: String,
    val compatibilityIssue: SharedPiCompatibilityIssue?,
)

private class SharedPiExtensionCatalogClient {
    private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun fetchCatalog(): List<SharedPiCatalogEntry> =
        parseSharedPiPackageCatalog(
            fetch(
                url = SharedPiPackagesUrl,
                requestName = "Pi package catalog",
                emptyBodyMessage = "Pi package catalog returned an empty body.",
            )
        )

    suspend fun fetchDetails(entry: SharedPiCatalogEntry): SharedPiPackageDetails {
        require(entry.packageUrl.startsWith("https://pi.dev/packages/")) {
            "Package details must come from pi.dev."
        }
        val parsed = parseSharedPiPackageDetails(
            fetch(
                url = entry.packageUrl,
                requestName = "Pi package details",
                emptyBodyMessage = "Pi package details returned an empty body.",
            ),
            entry.packageUrl,
        )
        return parsed.copy(
            source = parsed.source.ifBlank { entry.source },
            name = parsed.name.ifBlank { entry.name },
            description = parsed.description.ifBlank { entry.description },
            npmUrl = parsed.npmUrl.ifBlank { entry.npmUrl },
            repositoryUrl = parsed.repositoryUrl.ifBlank { entry.repositoryUrl },
            compatibilityIssue = parsed.compatibilityIssue ?: entry.compatibilityIssue,
        )
    }

    fun close() = client.close()

    private suspend fun fetch(
        url: String,
        requestName: String,
        emptyBodyMessage: String,
    ): String {
        val response = client.get(url) {
            header(HttpHeaders.Accept, "text/html")
            header(HttpHeaders.UserAgent, "Aether-Android")
        }
        check(response.status.isSuccess()) {
            "$requestName failed with HTTP ${response.status.value}."
        }
        return response.bodyAsText().takeIf(String::isNotBlank)
            ?: error(emptyBodyMessage)
    }
}

private fun parseSharedPiPackageCatalog(html: String): List<SharedPiCatalogEntry> =
    Regex(
        """<article\b(?=[^>]*\bdata-package-card\b)([^>]*)>(.*?)</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).findAll(html).mapNotNull { match ->
        val attributes = match.groupValues[1]
        val body = match.groupValues[2]
        val name = attributes.sharedHtmlAttribute("data-package-name").trim()
        val source = body.sharedHtmlAttributes("data-copy-text")
            .firstOrNull { it.startsWith("pi install ") }
            .orEmpty().removePrefix("pi install ").trim()
        if (name.isBlank() || !source.startsWith("npm:")) return@mapNotNull null

        val declaredTypes = attributes.sharedHtmlAttribute("data-package-types")
            .split(',', ' ').map(String::trim).filter(String::isNotBlank)
        val badgeTypes = body.sharedElementBodyWithClass("packages-badges")
            .sharedHtmlAttributes("data-type")
            .filterNot { it.equals("package", ignoreCase = true) }
        val types = (declaredTypes + badgeTypes).distinctBy(String::lowercase)
        val description = body.sharedElementTextWithClass("packages-desc")
        val metadata = body.sharedElementBodyWithClass("packages-meta")
            .sharedElementTexts("span")
        val packagePath = body.sharedHtmlAttributeAnywhere("data-package-path")
        val links = body.sharedElementBodyWithClass("packages-links").sharedHtmlAttributes("href")
        val searchText = attributes.sharedHtmlAttribute("data-package-search")
        SharedPiCatalogEntry(
            name = name,
            source = source,
            description = description,
            author = metadata.firstOrNull().orEmpty(),
            monthlyDownloads = attributes.sharedHtmlAttribute("data-package-downloads")
                .toLongOrNull() ?: 0L,
            packageUrl = if (packagePath.isBlank()) "" else "https://pi.dev$packagePath",
            npmUrl = links.firstOrNull { it.contains("npmjs.com/package/") }.orEmpty(),
            repositoryUrl = links.firstOrNull {
                it.contains("github.com/") && !it.contains("/issues/new")
            }.orEmpty(),
            types = types,
            compatibilityIssue = detectSharedPiCompatibility(
                name = name,
                description = "$description $searchText",
                types = types,
            ),
        )
    }
        .distinctBy(SharedPiCatalogEntry::source)
        .sortedWith(
            compareByDescending<SharedPiCatalogEntry> { it.monthlyDownloads }
                .thenBy { it.name.lowercase() }
        )
        .toList()

private fun parseSharedPiPackageDetails(
    html: String,
    packageUrl: String = "https://pi.dev",
): SharedPiPackageDetails {
    val detailGrid = html.sharedElementBodyWithClass("detail-grid")
    val terms = detailGrid.sharedElementTexts("dt")
    val definitions = detailGrid.sharedElementTexts("dd")
    val values = terms.mapIndexedNotNull { index, term ->
        term.trim().lowercase().takeIf(String::isNotBlank)?.let { it to definitions.getOrNull(index).orEmpty() }
    }.toMap()
    val source = html.sharedHtmlAttributes("data-copy-text")
        .firstOrNull { it.startsWith("pi install ") }
        .orEmpty().removePrefix("pi install ").trim()
    val types = html.sharedElementBodyWithClass("packages-badges")
        .sharedHtmlAttributes("data-type")
        .filterNot { it.equals("package", ignoreCase = true) }
        .distinctBy(String::lowercase)
        .ifEmpty {
            values["types"].orEmpty().split(',', ' ').map(String::trim).filter(String::isNotBlank)
        }
    val links = html.sharedElementBodyWithClass("packages-detail-links").sharedHtmlAttributes("href")
    val name = html.sharedElementTextWithClass("content-title")
        .ifBlank { values["package"].orEmpty() }
    val description = html.sharedElementTextWithClass("content-description")
    val manifest = html.sharedElementTextWithClass("raw-data-panel")
    val readme = html.sharedElementBodyWithClass("packages-readme")
        .sharedHtmlToMarkdown(packageUrl)
    return SharedPiPackageDetails(
        source = source,
        name = name,
        description = description,
        version = values["version"].orEmpty(),
        published = values["published"].orEmpty(),
        downloads = values["downloads"].orEmpty(),
        author = values["author"].orEmpty(),
        license = values["license"].orEmpty(),
        size = values["size"].orEmpty(),
        dependencies = values["dependencies"].orEmpty(),
        types = types,
        manifestJson = manifest,
        readmeMarkdown = readme,
        npmUrl = links.firstOrNull { it.contains("npmjs.com/package/") }.orEmpty(),
        repositoryUrl = links.firstOrNull {
            it.contains("github.com/") && !it.contains("/issues/new")
        }.orEmpty(),
        compatibilityIssue = detectSharedPiCompatibility(
            name = name,
            description = description,
            types = types,
            details = "$readme $manifest",
        ),
    )
}

private fun detectSharedPiCompatibility(
    name: String,
    description: String,
    types: List<String>,
    details: String = "",
): SharedPiCompatibilityIssue? {
    val normalizedTypes = types.map(String::lowercase).toSet()
    val text = "$name $description $details".lowercase()
    val interactiveSignals = listOf(
        "interactive tui", "terminal ui", "live overlay", "status bar", "powerline footer",
        "custom footer", "custom header", "keyboard shortcut", "clickable tui", "tui click",
        "tui overlay",
        "plan review with annotations", "structured questionnaire", "webview window",
        "local browser ui", "micro-ui", "ctx.ui", "registershortcut",
    )
    if (interactiveSignals.any(text::contains) || Regex("""\btui\b""").containsMatchIn(text)) {
        return SharedPiCompatibilityIssue.InteractiveUi
    }
    if ("theme" in normalizedTypes) return SharedPiCompatibilityIssue.Theme
    val platformSignals = listOf(
        "macos only", "windows only", "darwin only", "requires macos", "requires windows",
        "x64 only", "amd64 only",
    )
    if (platformSignals.any(text::contains)) return SharedPiCompatibilityIssue.Platform
    if ("prompt" in normalizedTypes && normalizedTypes.none { it == "extension" || it == "skill" }) {
        return SharedPiCompatibilityIssue.Prompt
    }
    return null
}

private fun String.sharedHtmlAttribute(name: String): String {
    val escaped = Regex.escape(name)
    val quoted = Regex("""(?:^|\s)$escaped\s*=\s*([\"'])(.*?)\1""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(2)
    if (quoted != null) return quoted.sharedDecodeHtmlEntities()
    return Regex("""(?:^|\s)$escaped\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1).orEmpty().sharedDecodeHtmlEntities()
}

private fun String.sharedHtmlAttributeAnywhere(name: String): String {
    val escaped = Regex.escape(name)
    val quoted = Regex("""\b$escaped\s*=\s*([\"'])(.*?)\1""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(2)
    if (quoted != null) return quoted.sharedDecodeHtmlEntities()
    return Regex("""\b$escaped\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1).orEmpty().sharedDecodeHtmlEntities()
}

private fun String.sharedHtmlAttributes(name: String): List<String> {
    val escaped = Regex.escape(name)
    val quoted = Regex("""\b$escaped\s*=\s*([\"'])(.*?)\1""", RegexOption.IGNORE_CASE)
        .findAll(this).map { it.groupValues[2].sharedDecodeHtmlEntities() }.toList()
    if (quoted.isNotEmpty()) return quoted
    return Regex("""\b$escaped\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE)
        .findAll(this).map { it.groupValues[1].sharedDecodeHtmlEntities() }.toList()
}

private fun String.sharedElementBodyWithClass(className: String): String {
    val escaped = Regex.escape(className)
    val opening = Regex(
        """<([a-z][a-z0-9:-]*)\b(?=[^>]*\bclass\s*=\s*[\"'][^\"']*(?<![a-z0-9_-])$escaped(?![a-z0-9_-])[^\"']*[\"'])[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(this) ?: return ""
    val tag = opening.groupValues[1]
    val tagPattern = Regex(
        """</?${Regex.escape(tag)}\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    var depth = 1
    for (token in tagPattern.findAll(this, opening.range.last + 1)) {
        when {
            token.value.startsWith("</") -> depth -= 1
            !token.value.trimEnd().endsWith("/>") -> depth += 1
        }
        if (depth == 0) return substring(opening.range.last + 1, token.range.first)
    }
    return ""
}

private fun String.sharedElementTextWithClass(className: String): String =
    sharedElementBodyWithClass(className).sharedHtmlToText()

private fun String.sharedElementTexts(tag: String): List<String> {
    val escaped = Regex.escape(tag)
    return Regex(
        """<$escaped\b[^>]*>(.*?)</$escaped>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).findAll(this).map { it.groupValues[1].sharedHtmlToText() }.toList()
}

private fun String.sharedHtmlToText(): String =
    replace(Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</\s*(p|div|li|h[1-6]|tr)\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .sharedDecodeHtmlEntities()
        .lines().joinToString("\n") { it.trim() }.trim()

internal fun String.sharedHtmlToMarkdown(baseUrl: String): String {
    val output = StringBuilder()
    val linkTargets = mutableListOf<String>()
    var sourceIndex = 0
    var preformattedDepth = 0

    fun appendBreak(lines: Int = 2) {
        while (output.isNotEmpty() && output.last() == ' ') output.deleteAt(output.lastIndex)
        val existing = output.takeLastWhile { it == '\n' }.length
        repeat((lines - existing).coerceAtLeast(0)) { output.append('\n') }
    }

    fun appendText(raw: String) {
        val decoded = raw.sharedDecodeHtmlEntities()
        if (preformattedDepth > 0) {
            output.append(decoded)
            return
        }
        val normalized = decoded.replace(Regex("""\s+"""), " ")
        if (normalized.isBlank()) return
        if (
            output.isNotEmpty() &&
            !output.last().isWhitespace() &&
            normalized.firstOrNull()?.isWhitespace() == true
        ) {
            output.append(' ')
        }
        output.append(normalized.trim())
        if (decoded.lastOrNull()?.isWhitespace() == true) output.append(' ')
    }

    SharedHtmlTagRegex.findAll(this).forEach { match ->
        appendText(substring(sourceIndex, match.range.first))
        sourceIndex = match.range.last + 1
        val tag = match.value
        if (tag.startsWith("<!--")) return@forEach
        val closing = tag.startsWith("</")
        val name = tag.removePrefix("<").removePrefix("/")
            .trimStart().takeWhile { it.isLetterOrDigit() }.lowercase()
        if (name.isBlank()) return@forEach

        if (!closing) {
            when (name) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    appendBreak()
                    output.append("#".repeat(name.last().digitToInt())).append(' ')
                }
                "p", "div", "section", "article", "table", "tr" -> appendBreak()
                "br" -> appendBreak(1)
                "hr" -> {
                    appendBreak()
                    output.append("---")
                    appendBreak()
                }
                "ul", "ol" -> appendBreak()
                "li" -> {
                    appendBreak(1)
                    output.append("- ")
                }
                "blockquote" -> {
                    appendBreak()
                    output.append("> ")
                }
                "strong", "b" -> output.append("**")
                "em", "i" -> output.append('*')
                "del", "s" -> output.append("~~")
                "pre" -> {
                    appendBreak()
                    output.append("```\n")
                    preformattedDepth += 1
                }
                "code" -> if (preformattedDepth == 0) output.append('`')
                "a" -> {
                    val target = tag.sharedHtmlAttributeAnywhere("href")
                        .let { resolveSharedHtmlUrl(it, baseUrl) }
                    linkTargets += target
                    output.append('[')
                }
                "img" -> {
                    val alt = tag.sharedHtmlAttributeAnywhere("alt")
                    val source = tag.sharedHtmlAttributeAnywhere("src")
                        .let { resolveSharedHtmlUrl(it, baseUrl) }
                    if (source.isNotBlank()) output.append("![$alt]($source)")
                }
                "th", "td" -> if (output.isNotEmpty() && output.last() != '\n') output.append(" | ")
            }
        } else {
            when (name) {
                "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "section", "article",
                "ul", "ol", "blockquote", "table", "tr" -> appendBreak()
                "li" -> appendBreak(1)
                "strong", "b" -> output.append("**")
                "em", "i" -> output.append('*')
                "del", "s" -> output.append("~~")
                "pre" -> {
                    preformattedDepth = (preformattedDepth - 1).coerceAtLeast(0)
                    appendBreak(1)
                    output.append("```")
                    appendBreak()
                }
                "code" -> if (preformattedDepth == 0) output.append('`')
                "a" -> {
                    val target = linkTargets.removeLastOrNull().orEmpty()
                    output.append("]($target)")
                }
            }
        }
    }
    appendText(substring(sourceIndex))
    return output.toString()
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun resolveSharedHtmlUrl(rawUrl: String, baseUrl: String): String {
    val value = rawUrl.trim()
    if (value.isBlank() || value.startsWith('#') || "://" in value || value.startsWith("data:")) {
        return value
    }
    if (value.startsWith("//")) return "https:$value"
    val schemeEnd = baseUrl.indexOf("://")
    val origin = if (schemeEnd >= 0) {
        val pathStart = baseUrl.indexOf('/', schemeEnd + 3)
        if (pathStart >= 0) baseUrl.substring(0, pathStart) else baseUrl.trimEnd('/')
    } else {
        "https://pi.dev"
    }
    if (value.startsWith('/')) return origin + value
    return baseUrl.substringBeforeLast('/', baseUrl).trimEnd('/') + "/" + value
}

private val SharedHtmlTagRegex = Regex(
    """<!--[\s\S]*?-->|<[^>]+>""",
    RegexOption.IGNORE_CASE,
)

private fun String.sharedDecodeHtmlEntities(): String =
    replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

private fun parseSharedInstalledPackages(payload: JsonObject): List<SharedInstalledExtension> =
    (payload["packages"] as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val source = item.sharedString("source").trim()
        if (source.isBlank()) return@mapNotNull null
        SharedInstalledExtension(
            id = "package:$source",
            source = source,
            name = item.sharedString("name").ifBlank { source.removePrefix("npm:") },
            version = item.sharedString("version"),
            description = item.sharedString("description"),
            installedPath = item.sharedString("installed_path"),
            extensionCount = item.sharedInt("extension_count"),
            aetherExtensionCount = item.sharedInt("aether_extension_count"),
            skillCount = item.sharedInt("skill_count"),
            promptCount = item.sharedInt("prompt_count"),
            themeCount = item.sharedInt("theme_count"),
            kind = SharedExtensionInstallKind.Package,
        )
    }

private suspend fun listSharedImportedExtensions(
    runtime: MultiplatformLocalRuntime,
): List<SharedInstalledExtension> {
    val extensions = mutableListOf<SharedInstalledExtension>()
    for ((scope, root) in sharedExtensionRoots(runtime)) {
        runtime.fileSystem.createDirectories(root)
        val listing = runSharedExtensionShell(
            runtime = runtime,
            command = """
                for entry in ${root.sharedShellQuote()}/*; do
                  [ -e "${'$'}entry" ] || continue
                  name=${'$'}{entry##*/}
                  case "${'$'}name" in .aether-import-*|.aether-backup-*) continue ;; esac
                  if [ -d "${'$'}entry" ]; then
                    printf 'd\t%s\n' "${'$'}entry"
                  else
                    printf 'f\t%s\n' "${'$'}entry"
                  fi
                done
            """.trimIndent(),
        )
        check(listing.exitCode == 0) { listing.stderr.ifBlank { "Unable to list imported extensions." } }
        listing.stdout.lines().mapNotNullTo(extensions) { line ->
            val kind = line.substringBefore('\t')
            val path = line.substringAfter('\t', "").trim()
            if (path.isBlank()) return@mapNotNullTo null
            sharedImportedExtension(runtime, path, kind == "d", scope)
        }
    }
    return extensions.distinctBy(SharedInstalledExtension::id).sortedBy { it.name.lowercase() }
}

private suspend fun sharedImportedExtension(
    runtime: MultiplatformLocalRuntime,
    path: String,
    directory: Boolean,
    scope: String,
): SharedInstalledExtension? {
    val manifest = if (directory) readSharedExtensionManifest(runtime, path) else null
    val piCount = when {
        !directory && path.substringAfterLast('.', "").lowercase() in SharedExtensionFileSuffixes -> 1
        directory -> sharedManifestExtensionEntryCount(runtime, path, manifest, "pi")
        else -> 0
    }
    val aetherCount = if (directory) {
        sharedManifestExtensionEntryCount(runtime, path, manifest, "aether")
    } else {
        0
    }
    if (piCount == 0 && aetherCount == 0) return null
    val fileName = path.substringAfterLast('/')
    return SharedInstalledExtension(
        id = "import:$scope:$path",
        source = if (scope == "pi") "Pi user directory" else "Imported",
        name = manifest?.sharedString("name").orEmpty()
            .ifBlank { fileName.substringBeforeLast('.', fileName) },
        version = manifest?.sharedString("version").orEmpty(),
        description = manifest?.sharedString("description").orEmpty(),
        installedPath = path,
        extensionCount = piCount,
        aetherExtensionCount = aetherCount,
        kind = SharedExtensionInstallKind.Imported,
    )
}

private suspend fun importSharedExtension(
    runtime: MultiplatformLocalRuntime,
    picked: PlatformPickedFile,
    reload: suspend () -> Unit,
): String {
    val suffix = picked.name.substringAfterLast('.', "").lowercase()
    require(suffix == "zip" || suffix in SharedExtensionFileSuffixes) {
        "Choose a Pi extension JavaScript/TypeScript file or a .zip package."
    }
    return if (suffix == "zip") {
        require(picked.bytes.size <= SharedExtensionImportLimitBytes) {
            "Extension archive is too large."
        }
        importSharedExtensionZip(runtime, picked, reload)
    } else {
        require(picked.bytes.size.toLong() <= SharedExtensionSingleEntryLimitBytes) {
            "Extension file is too large."
        }
        importSharedExtensionFile(runtime, picked, reload)
    }
}

private suspend fun importSharedExtensionFile(
    runtime: MultiplatformLocalRuntime,
    picked: PlatformPickedFile,
    reload: suspend () -> Unit,
): String {
    val root = sharedExtensionImportRoot(runtime)
    runtime.fileSystem.createDirectories(root)
    val fileName = sanitizeSharedExtensionFileName(picked.name)
    val destination = "$root/$fileName"
    val staging = "$root/.aether-import-${platformRandomUuid()}-$fileName"
    val backup = "$root/.aether-backup-${platformRandomUuid()}-$fileName"
    runtime.fileSystem.write(staging, picked.bytes)
    replaceSharedImportedPath(runtime, staging, destination, backup, reload)
    return fileName.substringBeforeLast('.', fileName)
}

private suspend fun importSharedExtensionZip(
    runtime: MultiplatformLocalRuntime,
    picked: PlatformPickedFile,
    reload: suspend () -> Unit,
): String {
    val root = sharedExtensionImportRoot(runtime)
    runtime.fileSystem.createDirectories(root)
    val token = platformRandomUuid()
    val archive = "$root/.aether-import-$token.zip"
    val extractionRoot = "$root/.aether-import-$token"
    runtime.fileSystem.write(archive, picked.bytes)
    runtime.fileSystem.createDirectories(extractionRoot)
    return try {
        val listResult = runSharedExtensionShell(
            runtime,
            "command -v unzip >/dev/null 2>&1 || apk add --no-cache unzip >/dev/null; " +
                "unzip -Z1 ${archive.sharedShellQuote()}",
        )
        check(listResult.exitCode == 0) {
            listResult.stderr.ifBlank { "Unable to inspect the extension archive." }
        }
        val entries = listResult.stdout.lines().filter(String::isNotBlank)
        require(entries.size <= SharedExtensionEntryLimit) { "The extension archive contains too many files." }
        require(entries.all(::isSafeSharedArchiveEntry)) {
            "The extension archive contains an unsafe path."
        }
        val sizeResult = runSharedExtensionShell(runtime, "unzip -l ${archive.sharedShellQuote()}")
        check(sizeResult.exitCode == 0) {
            sizeResult.stderr.ifBlank { "Unable to inspect the extension archive." }
        }
        val uncompressedSizes = sizeResult.stdout.lines().mapNotNull { line ->
            Regex("""^\s*(\d+)\s+\d[\d-]+\s+\d{2}:\d{2}\s+""")
                .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
        require(uncompressedSizes.isNotEmpty() || entries.isEmpty()) {
            "Unable to validate the extension archive sizes."
        }
        require(uncompressedSizes.all { it <= SharedExtensionSingleEntryLimitBytes }) {
            "The extension archive contains a file larger than 16 MB."
        }
        require(uncompressedSizes.sum() <= SharedExtensionExtractedLimitBytes) {
            "The extension archive expands to more than 128 MB."
        }
        val extractResult = runSharedExtensionShell(
            runtime,
            "unzip -q ${archive.sharedShellQuote()} -d ${extractionRoot.sharedShellQuote()}",
        )
        check(extractResult.exitCode == 0) {
            extractResult.stderr.ifBlank { "Unable to extract the extension archive." }
        }
        val packageRoot = locateSharedExtensionPackageRoot(runtime, extractionRoot)
        val manifest = readSharedExtensionManifest(runtime, packageRoot)
        val packageName = manifest?.sharedString("name").orEmpty()
            .ifBlank { picked.name.substringBeforeLast('.', "extension") }
        val destinationName = sanitizeSharedExtensionDirectoryName(packageName)
        val destination = "$root/$destinationName"
        val backup = "$root/.aether-backup-${platformRandomUuid()}-$destinationName"
        installSharedExtensionDependencies(runtime, packageRoot, manifest)
        replaceSharedImportedPath(runtime, packageRoot, destination, backup, reload)
        packageName
    } finally {
        withContext(NonCancellable) {
            runCatching { runtime.fileSystem.remove(archive) }
            runCatching { runtime.fileSystem.remove(extractionRoot, recursive = true) }
        }
    }
}

private suspend fun locateSharedExtensionPackageRoot(
    runtime: MultiplatformLocalRuntime,
    extractionRoot: String,
): String {
    if (isSharedExtensionPackageRoot(runtime, extractionRoot)) return extractionRoot
    val result = runSharedExtensionShell(
        runtime,
        "find ${extractionRoot.sharedShellQuote()} -mindepth 1 -maxdepth 1 -type d ! -name __MACOSX -print",
    )
    check(result.exitCode == 0) { result.stderr.ifBlank { "Unable to inspect the extension archive." } }
    val candidates = result.stdout.lines().filter(String::isNotBlank)
        .filter { isSharedExtensionPackageRoot(runtime, it) }
    return candidates.singleOrNull()
        ?: error("The archive must contain a script extension package or an index extension file.")
}

private suspend fun isSharedExtensionPackageRoot(
    runtime: MultiplatformLocalRuntime,
    path: String,
): Boolean {
    val manifest = readSharedExtensionManifest(runtime, path)
    if (
        sharedManifestExtensionEntryCount(runtime, path, manifest, "pi") > 0 ||
        sharedManifestExtensionEntryCount(runtime, path, manifest, "aether") > 0
    ) {
        return true
    }
    return false
}

private suspend fun installSharedExtensionDependencies(
    runtime: MultiplatformLocalRuntime,
    packageRoot: String,
    manifest: JsonObject?,
) {
    if (manifest == null || runtime.fileSystem.exists("$packageRoot/node_modules")) return
    val hasDependencies = listOf("dependencies", "optionalDependencies", "peerDependencies")
        .any { (manifest[it] as? JsonObject)?.isNotEmpty() == true }
    if (!hasDependencies) return
    val command = if (runtime.fileSystem.exists("$packageRoot/package-lock.json")) {
        "npm ci --omit=dev --no-audit --no-fund"
    } else {
        "npm install --omit=dev --no-audit --no-fund"
    }
    val result = runSharedExtensionShell(runtime, command, workingDirectory = packageRoot)
    check(result.exitCode == 0) {
        result.stderr.ifBlank { result.stdout }.ifBlank { "Unable to install extension dependencies." }
    }
}

private suspend fun replaceSharedImportedPath(
    runtime: MultiplatformLocalRuntime,
    staging: String,
    destination: String,
    backup: String,
    reload: suspend () -> Unit,
) {
    val hadExisting = runtime.fileSystem.exists(destination)
    if (hadExisting) {
        val backupResult = runSharedExtensionShell(
            runtime,
            "mv ${destination.sharedShellQuote()} ${backup.sharedShellQuote()}",
        )
        check(backupResult.exitCode == 0) { backupResult.stderr.ifBlank { "Unable to back up the extension." } }
    }
    try {
        val moveResult = runSharedExtensionShell(
            runtime,
            "mv ${staging.sharedShellQuote()} ${destination.sharedShellQuote()}",
        )
        check(moveResult.exitCode == 0) { moveResult.stderr.ifBlank { "Unable to store the extension." } }
        reload()
        if (hadExisting) runtime.fileSystem.remove(backup, recursive = true)
    } catch (error: Throwable) {
        withContext(NonCancellable) {
            runCatching { runtime.fileSystem.remove(destination, recursive = true) }
            if (hadExisting) {
                runCatching {
                    val restore = runSharedExtensionShell(
                        runtime,
                        "mv ${backup.sharedShellQuote()} ${destination.sharedShellQuote()}",
                    )
                    check(restore.exitCode == 0) { restore.stderr.ifBlank { "Unable to restore the extension." } }
                }
            }
            runCatching { reload() }
        }
        throw error
    } finally {
        withContext(NonCancellable) {
            runCatching { runtime.fileSystem.remove(staging, recursive = true) }
            runCatching { runtime.fileSystem.remove(backup, recursive = true) }
        }
    }
}

private suspend fun removeSharedImportedExtension(
    runtime: MultiplatformLocalRuntime,
    extension: SharedInstalledExtension,
    reload: suspend () -> Unit,
) {
    val root = sharedExtensionRoots(runtime).map { it.second.trimEnd('/') + "/" }
        .firstOrNull { candidate ->
            extension.installedPath.startsWith(candidate) &&
                extension.installedPath.removePrefix(candidate).none { it == '/' }
        }
    require(extension.kind == SharedExtensionInstallKind.Imported && root != null) {
        "Refusing to remove an extension outside the managed import directory."
    }
    val backup = root + ".aether-backup-${platformRandomUuid()}-" + extension.installedPath.substringAfterLast('/')
    val moveResult = runSharedExtensionShell(
        runtime,
        "mv ${extension.installedPath.sharedShellQuote()} ${backup.sharedShellQuote()}",
    )
    check(moveResult.exitCode == 0) { moveResult.stderr.ifBlank { "Unable to remove the extension." } }
    try {
        reload()
        runtime.fileSystem.remove(backup, recursive = true)
    } catch (error: Throwable) {
        withContext(NonCancellable) {
            runCatching {
                val restore = runSharedExtensionShell(
                    runtime,
                    "mv ${backup.sharedShellQuote()} ${extension.installedPath.sharedShellQuote()}",
                )
                check(restore.exitCode == 0)
            }
            runCatching { reload() }
        }
        throw error
    }
}

private fun sharedExtensionImportRoot(runtime: MultiplatformLocalRuntime): String =
    runtime.homeDirectory.trimEnd('/') + "/.aether/extensions"

private fun sharedExtensionRoots(runtime: MultiplatformLocalRuntime): List<Pair<String, String>> = listOf(
    "aether" to sharedExtensionImportRoot(runtime),
    "pi" to runtime.homeDirectory.trimEnd('/') + "/.pi/agent/extensions",
)

private suspend fun readSharedExtensionManifest(
    runtime: MultiplatformLocalRuntime,
    directory: String,
): JsonObject? {
    val path = "$directory/package.json"
    if (!runtime.fileSystem.exists(path)) return null
    return runSharedExtensionCatching {
        Json.parseToJsonElement(runtime.fileSystem.read(path).decodeToString()).jsonObject
    }
        .getOrNull()
}

private suspend fun <T> runSharedExtensionCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    Result.failure(failure)
}

private suspend fun sharedManifestExtensionEntryCount(
    runtime: MultiplatformLocalRuntime,
    directory: String,
    manifest: JsonObject?,
    namespace: String,
): Int {
    val configuredEntries = ((manifest?.get(namespace) as? JsonObject)?.get("extensions") as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .count { relativePath ->
            val result = runSharedExtensionShell(
                runtime,
                "test -f ${(directory.trimEnd('/') + "/" + relativePath).sharedShellQuote()}",
            )
            result.exitCode == 0
        }
    if (configuredEntries > 0) return configuredEntries
    if (namespace != "pi" || manifest?.get("aether") is JsonObject) return 0
    return SharedExtensionIndexNames.count { fileName ->
        val result = runSharedExtensionShell(
            runtime,
            "test -f ${(directory.trimEnd('/') + "/" + fileName).sharedShellQuote()}",
        )
        result.exitCode == 0
    }
}

private fun JsonObject.sharedString(name: String): String =
    (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.sharedInt(name: String): Int =
    (get(name) as? JsonPrimitive)?.intOrNull ?: 0

private fun sanitizeSharedExtensionFileName(raw: String): String {
    val suffix = raw.substringAfterLast('.', "").lowercase()
    require(suffix in SharedExtensionFileSuffixes) { "Unsupported extension file type." }
    val stem = raw.substringBeforeLast('.', "extension").lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.').ifBlank { "extension" }
    return "$stem.$suffix"
}

private fun sanitizeSharedExtensionDirectoryName(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.').ifBlank { "extension-${platformRandomUuid()}" }

private fun isSafeSharedArchiveEntry(raw: String): Boolean {
    if (raw.isBlank() || raw.startsWith('/') || raw.startsWith('\\') || raw.any { it.code < 32 }) return false
    val normalized = raw.replace('\\', '/').trimEnd('/')
    if (normalized.isBlank()) return false
    if (normalized.substringBefore('/').contains(':')) return false
    return normalized.split('/').none { it == ".." || it.isEmpty() }
}

private data class SharedExtensionShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private suspend fun runSharedExtensionShell(
    runtime: MultiplatformLocalRuntime,
    command: String,
    workingDirectory: String = runtime.homeDirectory,
): SharedExtensionShellResult = coroutineScope {
    val process = runtime.startProcess(
        RuntimeProcessSpec(
            executable = "/bin/sh",
            arguments = listOf("-lc", command),
            environment = mapOf("HOME" to runtime.homeDirectory),
            workingDirectory = workingDirectory,
        )
    )
    var completed = false
    try {
        process.closeStdin()
        val stdout = async { process.stdout.toList().sharedFlattenBytes().decodeToString() }
        val stderr = async { process.stderr.toList().sharedFlattenBytes().decodeToString() }
        val exit = process.awaitExit()
        val result = SharedExtensionShellResult(exit.exitCode, stdout.await().trim(), stderr.await().trim())
        completed = true
        result
    } finally {
        if (!completed) {
            withContext(NonCancellable) {
                runCatching { process.signal(com.zhousl.aether.runtime.RuntimeProcessSignal.Kill) }
            }
        }
    }
}

private fun List<ByteArray>.sharedFlattenBytes(): ByteArray {
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { bytes ->
        bytes.copyInto(result, destinationOffset = offset)
        offset += bytes.size
    }
    return result
}

private fun String.sharedShellQuote(): String = "'" + replace("'", "'\"'\"'") + "'"

@Composable
internal fun SharedExtensionsSettingsDetail(
    bridgeClient: SharedPiBridgeClient,
    extensionManager: SharedAetherExtensionManager,
    extensionStateStore: SharedExtensionStateStore,
    runtime: MultiplatformLocalRuntime,
    platformServices: PlatformServices,
    onSnapshotChanged: (SharedAetherExtensionSnapshot) -> Unit,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val catalogClient = remember { SharedPiExtensionCatalogClient() }
    val controllerSnapshot = LocalSharedAetherExtensionUiController.current?.snapshot
    var snapshot by remember { mutableStateOf(controllerSnapshot ?: extensionManager.snapshot) }
    var installedPackages by remember { mutableStateOf(emptyList<SharedInstalledExtension>()) }
    var importedExtensions by remember { mutableStateOf(emptyList<SharedInstalledExtension>()) }
    var catalog by remember { mutableStateOf(emptyList<SharedPiCatalogEntry>()) }
    var catalogError by remember { mutableStateOf("") }
    var operationKey by remember { mutableStateOf("") }
    var catalogLoading by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCatalogSource by rememberSaveable { mutableStateOf("") }
    var selectedDetails by remember { mutableStateOf<SharedPiPackageDetails?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf("") }
    var detailsReloadToken by remember { mutableIntStateOf(0) }
    var importedExtensionName by remember { mutableStateOf("") }

    val updatedMessage = stringResource(Res.string.message_pi_extension_updated)
    val importedNamePlaceholder = "{extension_name}"
    val importedMessageTemplate = stringResource(
        Res.string.message_pi_extension_imported,
        importedNamePlaceholder,
    )
    val errorPlaceholder = "{extension_error}"
    val operationFailedTemplate = stringResource(
        Res.string.message_pi_extension_operation_failed,
        errorPlaceholder,
    )
    DisposableEffect(catalogClient) {
        onDispose(catalogClient::close)
    }

    LaunchedEffect(controllerSnapshot?.version) {
        if (controllerSnapshot != null) snapshot = controllerSnapshot
    }

    suspend fun loadInstalledState() {
        coroutineScope {
            val packageRequest = async { bridgeClient.listExtensionPackages() }
            val importedRequest = async { listSharedImportedExtensions(runtime) }
            val optionsRequest = async { extensionStateStore.load() }
            val options = optionsRequest.await()
            installedPackages = parseSharedInstalledPackages(packageRequest.await()).map { extension ->
                extension.copy(isEnabled = extension.source !in options.disabledPackageSources)
            }
            importedExtensions = importedRequest.await().map { extension ->
                extension.copy(isEnabled = extension.installedPath !in options.disabledExtensionPaths)
            }
        }
    }

    suspend fun publishSnapshot(value: SharedAetherExtensionSnapshot) {
        snapshot = value
        onSnapshotChanged(value)
    }

    suspend fun refreshExtensionRuntime(reload: Boolean, reloadAgentSessions: Boolean = false) {
        val sessionErrors = if (reloadAgentSessions) {
            bridgeClient.reloadAllExtensions().sharedSessionReloadErrors()
        } else {
            emptyList()
        }
        val refreshed = if (reload) extensionManager.reload() else extensionManager.refresh()
        publishSnapshot(refreshed)
        val errors = sessionErrors + extensionManager.error.takeIf(String::isNotBlank).orEmpty()
        check(errors.isEmpty()) { errors.take(3).joinToString("; ") }
    }

    fun userFacingError(error: Throwable): String =
        error.message?.trim().takeUnless { it.isNullOrBlank() }
            ?: error::class.simpleName.orEmpty().ifBlank { "Error" }

    fun operationFailedMessage(error: Throwable): String =
        operationFailedTemplate.replace(errorPlaceholder, userFacingError(error))

    fun runOperation(
        key: String,
        successMessage: () -> String,
        operation: suspend () -> Boolean,
    ) {
        if (operationKey.isNotBlank()) return
        operationKey = key
        scope.launch {
            try {
                val changed = operation()
                if (changed) onTransientMessage(successMessage())
                runSharedExtensionCatching { loadInstalledState() }.onFailure { failure ->
                    onTransientMessage(operationFailedMessage(failure))
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                onTransientMessage(operationFailedMessage(failure))
            } finally {
                operationKey = ""
            }
        }
    }

    fun installPackage(source: String) {
        runOperation(source, { updatedMessage }) {
            val response = bridgeClient.installExtensionPackage(source)
            refreshExtensionRuntime(reload = true)
            val errors = (response["reload"] as? JsonObject)?.sharedSessionReloadErrors().orEmpty()
            check(errors.isEmpty()) { errors.take(3).joinToString("; ") }
            true
        }
    }

    fun updatePackage(extension: SharedInstalledExtension) {
        runOperation(extension.source, { updatedMessage }) {
            val response = bridgeClient.updateExtensionPackage(extension.source)
            refreshExtensionRuntime(reload = true)
            val errors = (response["reload"] as? JsonObject)?.sharedSessionReloadErrors().orEmpty()
            check(errors.isEmpty()) { errors.take(3).joinToString("; ") }
            true
        }
    }

    fun removeExtension(extension: SharedInstalledExtension) {
        runOperation(extension.id, { updatedMessage }) {
            when (extension.kind) {
                SharedExtensionInstallKind.Package -> {
                    val response = bridgeClient.removeExtensionPackage(extension.source)
                    extensionStateStore.removePackage(extension.source)
                    refreshExtensionRuntime(reload = true)
                    val errors = (response["reload"] as? JsonObject)?.sharedSessionReloadErrors().orEmpty()
                    check(errors.isEmpty()) { errors.take(3).joinToString("; ") }
                }
                SharedExtensionInstallKind.Imported -> {
                    removeSharedImportedExtension(
                        runtime = runtime,
                        extension = extension,
                        reload = { refreshExtensionRuntime(reload = true, reloadAgentSessions = true) },
                    )
                    extensionStateStore.removeImportedExtension(extension.installedPath)
                }
            }
            true
        }
    }

    fun setExtensionEnabled(extension: SharedInstalledExtension, enabled: Boolean) {
        runOperation(extension.id, { updatedMessage }) {
            when (extension.kind) {
                SharedExtensionInstallKind.Package ->
                    extensionStateStore.setPackageEnabled(extension.source, enabled)
                SharedExtensionInstallKind.Imported ->
                    extensionStateStore.setImportedExtensionEnabled(extension.installedPath, enabled)
            }
            refreshExtensionRuntime(reload = true, reloadAgentSessions = true)
            true
        }
    }

    fun importExtension() {
        runOperation(
            key = "import",
            successMessage = {
                importedMessageTemplate.replace(
                    importedNamePlaceholder,
                    importedExtensionName,
                )
            },
        ) {
            val picked = platformServices.pickFile(imagesOnly = false) ?: return@runOperation false
            importedExtensionName = importSharedExtension(
                runtime = runtime,
                picked = picked,
                reload = { refreshExtensionRuntime(reload = true, reloadAgentSessions = true) },
            )
            true
        }
    }

    fun refreshAll() {
        if (operationKey.isNotBlank()) return
        operationKey = "refresh"
        catalogLoading = true
        catalogError = ""
        scope.launch {
            val installedResult = runSharedExtensionCatching {
                loadInstalledState()
                refreshExtensionRuntime(reload = false)
            }
            val catalogResult = runSharedExtensionCatching { catalogClient.fetchCatalog() }
            installedResult.onFailure { onTransientMessage(operationFailedMessage(it)) }
            catalogResult.fold(
                onSuccess = { catalog = it },
                onFailure = { catalogError = userFacingError(it) },
            )
            catalogLoading = false
            operationKey = ""
        }
    }

    LaunchedEffect(bridgeClient, runtime) {
        runSharedExtensionCatching {
            loadInstalledState()
            refreshExtensionRuntime(reload = false)
        }.onFailure { onTransientMessage(operationFailedMessage(it)) }
    }

    LaunchedEffect(catalogClient) {
        catalogLoading = true
        runSharedExtensionCatching { catalogClient.fetchCatalog() }.fold(
            onSuccess = { catalog = it },
            onFailure = { catalogError = userFacingError(it) },
        )
        catalogLoading = false
    }

    val allInstalled = remember(installedPackages, importedExtensions) {
        (installedPackages + importedExtensions).sortedBy { it.name.lowercase() }
    }
    val installedSources = remember(installedPackages) { installedPackages.map { it.source }.toSet() }
    val selectedCatalog = catalog.firstOrNull { it.source == selectedCatalogSource }

    LaunchedEffect(selectedCatalogSource, detailsReloadToken) {
        selectedDetails = null
        detailsError = ""
        val entry = selectedCatalog ?: return@LaunchedEffect
        detailsLoading = true
        runSharedExtensionCatching { catalogClient.fetchDetails(entry) }.fold(
            onSuccess = { selectedDetails = it },
            onFailure = { detailsError = userFacingError(it) },
        )
        detailsLoading = false
    }

    val query = search.trim()
    val visibleCatalog = catalog.asSequence().filter { entry ->
        query.isBlank() || listOf(entry.name, entry.description, entry.author, entry.source)
            .any { it.contains(query, ignoreCase = true) }
    }.take(40).toList()

    SharedSettingsPageTransition(
        targetState = selectedCatalogSource,
        depth = { if (it.isBlank()) 0 else 1 },
        label = "extensions_settings_page_transition",
    ) { currentSource ->
        val currentCatalog = catalog.firstOrNull { it.source == currentSource }
        if (currentCatalog != null) {
            SharedExtensionCatalogDetail(
                entry = currentCatalog,
                details = selectedDetails,
                installed = currentCatalog.source in installedSources,
                loading = detailsLoading,
                error = detailsError,
                operating = operationKey == currentCatalog.source,
                onRetry = { detailsReloadToken += 1 },
                onInstall = { installPackage(currentCatalog.source) },
                onOpenUrl = platformServices::openUrl,
                onBack = { selectedCatalogSource = "" },
            )
        } else {
            Box(Modifier.fillMaxSize().background(AetherBackground)) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                        .navigationBarsPadding(),
                ) {
            Text(
                stringResource(Res.string.settings_pi_extensions_warning),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (extensionManager.error.isNotBlank() || snapshot.errors.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SharedExtensionErrorCard(extensionManager.error, snapshot.errors)
            }
            Spacer(Modifier.height(16.dp))
            SharedExtensionTabs(selectedTab = selectedTab, onSelected = { selectedTab = it })
            Spacer(Modifier.height(18.dp))
            when (selectedTab) {
                0 -> SharedExtensionDiscoverTab(
                    search = search,
                    onSearchChanged = { search = it },
                    catalog = visibleCatalog,
                    installedSources = installedSources,
                    loading = catalogLoading,
                    error = catalogError,
                    operationKey = operationKey,
                    onRetry = ::refreshAll,
                    onSelect = { selectedCatalogSource = it.source },
                )
                else -> SharedExtensionInstalledTab(
                    extensions = allInstalled,
                    operationKey = operationKey,
                    onImport = ::importExtension,
                    onSetEnabled = ::setExtensionEnabled,
                    onUpdate = ::updatePackage,
                    onRemove = ::removeExtension,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
                SettingsTopBar(
                    title = stringResource(Res.string.settings_pi_extensions),
                    onBack = onBack,
                    trailingIcon = Icons.Rounded.FileUpload,
                    trailingEnabled = operationKey.isBlank(),
                    trailingContentDescription = stringResource(Res.string.settings_import_extension),
                    onTrailingAction = ::importExtension,
                )
            }
        }
    }
}

private fun JsonObject.sharedSessionReloadErrors(): List<String> =
    (get("sessions") as? JsonArray).orEmpty().flatMap { sessionElement ->
        val session = sessionElement as? JsonObject ?: return@flatMap emptyList()
        val id = session.sharedString("session_id")
        (session["errors"] as? JsonArray).orEmpty().mapNotNull { errorElement ->
            val message = (errorElement as? JsonObject)?.sharedString("error")
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (id.isBlank()) message else "$id: $message"
        }
    }

@Composable
private fun SharedExtensionsTopBar(
    busy: Boolean,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AetherBackground).statusBarsPadding()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderCircleButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
                onClick = onBack,
                size = 38.dp,
                iconSize = 19.dp,
                containerColor = AetherSurface,
            )
            Text(
                stringResource(Res.string.extensions_title),
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            HeaderCircleButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(Res.string.shared_extensions_refresh_description),
                onClick = onRefresh,
                enabled = !busy,
                size = 38.dp,
                iconSize = 18.dp,
                containerColor = AetherSurface,
            )
            HeaderCircleButton(
                icon = Icons.Rounded.FileUpload,
                contentDescription = stringResource(Res.string.shared_extensions_import_description),
                onClick = onImport,
                enabled = !busy,
                size = 38.dp,
                iconSize = 18.dp,
                containerColor = AetherSurface,
            )
        }
        Spacer(
            Modifier.fillMaxWidth().height(28.dp).background(
                Brush.verticalGradient(listOf(AetherBackground, Color.Transparent))
            )
        )
    }
}

@Composable
private fun SharedExtensionRuntimeSummary(
    installed: List<SharedInstalledExtension>,
    snapshot: SharedAetherExtensionSnapshot,
    runtimeError: String,
    scriptExtensionsAvailable: Boolean,
) {
    val scriptEntries = installed.sumOf { it.extensionCount + it.aetherExtensionCount }
    SettingsCardGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (scriptExtensionsAvailable) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (scriptExtensionsAvailable) AetherPrimary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    if (scriptExtensionsAvailable) {
                        stringResource(Res.string.shared_extensions_runtime_enabled)
                    } else {
                        stringResource(Res.string.shared_extensions_script_runtime_unavailable)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = AetherOnSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(
                    Res.string.shared_extensions_runtime_summary,
                    installed.size,
                    scriptEntries,
                    snapshot.extensions.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
    }
    if (runtimeError.isNotBlank() || snapshot.errors.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SharedExtensionErrorCard(runtimeError, snapshot.errors)
    }
}

@Composable
private fun SharedExtensionErrorCard(
    runtimeError: String,
    errors: List<SharedAetherExtensionError>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(21.dp),
            )
            Text(
                stringResource(Res.string.settings_script_extension_errors_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AetherOnSurface,
            )
        }
        if (runtimeError.isNotBlank()) {
            Text(runtimeError, style = MaterialTheme.typography.bodySmall, color = AetherOnSurface)
        }
        errors.takeLast(5).forEach { error ->
            Text(
                stringResource(
                    Res.string.settings_script_extension_error_item,
                    error.phase,
                    error.extensionId.ifBlank { error.path },
                    error.message,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SharedExtensionTabs(
    selectedTab: Int,
    onSelected: (Int) -> Unit,
) {
    val labels = listOf(
        stringResource(Res.string.settings_extension_discover),
        stringResource(Res.string.settings_extension_installed),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                onClick = { onSelected(index) },
                selected = selectedTab == index,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AetherPrimary,
                    activeContentColor = AetherOnPrimary,
                    inactiveContainerColor = AetherSurfaceHigh,
                    inactiveContentColor = AetherOnSurface,
                ),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SharedExtensionDiscoverTab(
    search: String,
    onSearchChanged: (String) -> Unit,
    catalog: List<SharedPiCatalogEntry>,
    installedSources: Set<String>,
    loading: Boolean,
    error: String,
    operationKey: String,
    onRetry: () -> Unit,
    onSelect: (SharedPiCatalogEntry) -> Unit,
) {
    SettingsCardGroup {
        SharedExtensionSearchField(value = search, onValueChange = onSearchChanged)
    }
    Spacer(Modifier.height(14.dp))
    when {
        loading && catalog.isEmpty() -> SharedExtensionLoadingRow(
            stringResource(Res.string.settings_loading_extensions)
        )
        error.isNotBlank() && catalog.isEmpty() -> SettingsCardGroup {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                SharedSettingsActionButton(
                    label = stringResource(Res.string.action_retry),
                    onClick = onRetry,
                    enabled = operationKey.isBlank(),
                )
            }
        }
        catalog.isEmpty() -> Text(
            stringResource(Res.string.settings_no_extensions_found),
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
        )
        else -> catalog.forEach { entry ->
            SharedExtensionCatalogCard(
                entry = entry,
                installed = entry.source in installedSources,
                onClick = { onSelect(entry) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SharedExtensionCatalogCard(
    entry: SharedPiCatalogEntry,
    installed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AetherSurfaceHigh).clickable(onClick = onClick).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                val metadata = buildString {
                    append(entry.author)
                    if (entry.author.isNotBlank() && entry.monthlyDownloads > 0) append(" · ")
                    if (entry.monthlyDownloads > 0) {
                        append(formatSharedExtensionDownloads(entry.monthlyDownloads) + "/mo")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(metadata, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            }
            if (entry.compatibilityIssue != null) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = stringResource(Res.string.settings_package_may_be_incompatible),
                    tint = Color(0xFFFFB020),
                    modifier = Modifier.size(22.dp),
                )
            }
            if (installed) {
                Spacer(Modifier.width(8.dp))
                SharedActionPreviewPill(stringResource(Res.string.settings_extension_installed))
            }
        }
        if (entry.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                entry.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            entry.source,
            style = MaterialTheme.typography.labelMedium,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SharedExtensionInstalledTab(
    extensions: List<SharedInstalledExtension>,
    operationKey: String,
    onImport: () -> Unit,
    onSetEnabled: (SharedInstalledExtension, Boolean) -> Unit,
    onUpdate: (SharedInstalledExtension) -> Unit,
    onRemove: (SharedInstalledExtension) -> Unit,
) {
    when {
        extensions.isEmpty() -> SettingsCardGroup {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.settings_no_extensions_installed),
                    style = MaterialTheme.typography.titleMedium,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.settings_install_or_import_extension),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsActionButton(
                    label = stringResource(Res.string.settings_import_extension),
                    onClick = onImport,
                    enabled = operationKey.isBlank(),
                )
            }
        }
        else -> extensions.forEach { extension ->
            SharedInstalledExtensionCard(
                extension = extension,
                operating = operationKey == extension.id || operationKey == extension.source,
                actionsEnabled = operationKey.isBlank(),
                onSetEnabled = { enabled -> onSetEnabled(extension, enabled) },
                onUpdate = { onUpdate(extension) },
                onRemove = { onRemove(extension) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SharedInstalledExtensionCard(
    extension: SharedInstalledExtension,
    operating: Boolean,
    actionsEnabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AetherSurfaceHigh).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    extension.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                if (extension.version.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("v${extension.version}", style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (operating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AetherPrimary,
                    )
                }
                Switch(
                    checked = extension.isEnabled,
                    onCheckedChange = onSetEnabled,
                    enabled = actionsEnabled && !operating,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AetherOnPrimary,
                        checkedTrackColor = AetherPrimary,
                    ),
                )
            }
        }
        if (extension.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                extension.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val resources = listOfNotNull(
            extension.extensionCount.takeIf { it > 0 }?.let {
                stringResource(Res.string.settings_package_extensions_count, it)
            },
            extension.aetherExtensionCount.takeIf { it > 0 }?.let {
                stringResource(Res.string.settings_package_aether_extensions_count, it)
            },
            extension.skillCount.takeIf { it > 0 }?.let {
                stringResource(Res.string.settings_package_skills_count, it)
            },
            extension.promptCount.takeIf { it > 0 }?.let {
                stringResource(Res.string.settings_package_prompts_count, it)
            },
            extension.themeCount.takeIf { it > 0 }?.let {
                stringResource(Res.string.settings_package_themes_count, it)
            },
        )
        if (resources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                resources.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            extension.source,
            style = MaterialTheme.typography.labelMedium,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!operating) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (extension.kind == SharedExtensionInstallKind.Package) {
                    SharedSmallChipButton(
                        label = stringResource(Res.string.settings_update),
                        onClick = onUpdate,
                        enabled = actionsEnabled,
                    )
                }
                SharedSmallChipButton(
                    label = stringResource(Res.string.action_remove),
                    onClick = onRemove,
                    destructive = true,
                    enabled = actionsEnabled,
                )
            }
        }
    }
}

private fun SharedInstalledExtension.sharedErrors(
    snapshot: SharedAetherExtensionSnapshot,
): List<SharedAetherExtensionError> = snapshot.errors.filter { error ->
    installedPath.isNotBlank() && (
        error.path == installedPath || error.path.startsWith(installedPath.trimEnd('/') + "/")
    )
}

private fun SharedInstalledExtension.sharedLoadedEntries(
    snapshot: SharedAetherExtensionSnapshot,
): Int = snapshot.extensions.count { loaded ->
    installedPath.isNotBlank() && (
        loaded.path == installedPath || loaded.path.startsWith(installedPath.trimEnd('/') + "/")
    )
}

@Composable
private fun SharedExtensionLoadingRow(
    label: String,
    verticalPadding: Int = 32,
    indicatorSize: Int = 22,
    gap: Int = 12,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize.dp),
            strokeWidth = 2.dp,
            color = AetherPrimary,
        )
        Spacer(Modifier.width(gap.dp))
        Text(label, color = AetherOnSurfaceVariant)
    }
}

@Composable
private fun SharedExtensionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val label = stringResource(Res.string.settings_search_extensions)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().sharedSettingsBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(AetherPrimary),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

internal fun formatSharedExtensionDownloads(value: Long): String = when {
    value >= 1_000_000 -> "${formatSharedExtensionOneDecimal(value / 1_000_000.0)}M"
    value >= 1_000 -> "${formatSharedExtensionOneDecimal(value / 1_000.0)}K"
    else -> value.toString()
}

private fun formatSharedExtensionOneDecimal(value: Double): String {
    val tenths = kotlin.math.floor(value * 10.0 + 0.5).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun SharedExtensionCatalogDetail(
    entry: SharedPiCatalogEntry,
    details: SharedPiPackageDetails?,
    installed: Boolean,
    loading: Boolean,
    error: String,
    operating: Boolean,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenUrl: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val compatibility = details?.compatibilityIssue ?: entry.compatibilityIssue
    var showInstallWarning by rememberSaveable(entry.source) { mutableStateOf(false) }
    if (showInstallWarning && compatibility != null) {
        Dialog(onDismissRequest = { showInstallWarning = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .shadow(
                        22.dp,
                        RoundedCornerShape(28.dp),
                        ambientColor = AetherScrim,
                        spotColor = AetherScrim,
                    )
                    .clip(RoundedCornerShape(28.dp)).background(AetherSurfaceHigh).padding(20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFFB020),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        stringResource(Res.string.settings_package_install_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        Res.string.settings_package_install_warning_body,
                        entry.name,
                        sharedCompatibilityMessage(compatibility),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SharedSettingsSubtleActionButton(
                        label = stringResource(Res.string.action_cancel),
                        onClick = { showInstallWarning = false },
                        modifier = Modifier.weight(1f),
                    )
                    SharedSettingsActionButton(
                        label = stringResource(Res.string.settings_install_anyway),
                        onClick = {
                            showInstallWarning = false
                            onInstall()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    SharedExtensionDetailScaffold(
        title = entry.name,
        onBack = onBack,
    ) {
        when {
            loading && details == null -> SharedExtensionLoadingRow(
                label = stringResource(Res.string.settings_loading_package_details),
                verticalPadding = 40,
                indicatorSize = 24,
            )
            details == null -> SettingsCardGroup {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(
                        error.ifBlank { stringResource(Res.string.settings_package_details_unavailable) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    SharedSettingsActionButton(
                        label = stringResource(Res.string.action_retry),
                        onClick = onRetry,
                        enabled = !loading,
                    )
                    if (!installed) {
                        Spacer(Modifier.height(10.dp))
                        if (operating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = AetherPrimary,
                            )
                        } else {
                            SharedSettingsActionButton(
                                label = stringResource(Res.string.settings_install_package),
                                onClick = {
                                    if (compatibility == null) onInstall() else showInstallWarning = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    details.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                if (details.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        details.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AetherOnSurfaceVariant,
                    )
                }
                if (compatibility != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFB020).copy(alpha = 0.14f)).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFB020),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            sharedCompatibilityMessage(compatibility),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                when {
                    operating -> SharedExtensionLoadingRow(
                        label = stringResource(Res.string.settings_installing),
                        verticalPadding = 10,
                        gap = 10,
                    )
                    installed -> SharedActionPreviewPill(
                        stringResource(Res.string.settings_extension_installed),
                    )
                    else -> SharedSettingsActionButton(
                        label = stringResource(Res.string.settings_install_package),
                        onClick = {
                            if (compatibility == null) onInstall() else showInstallWarning = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))
                SettingsCardGroup {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_source), details.source)
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_version), details.version)
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_published), details.published)
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_downloads), details.downloads)
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_author), details.author)
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_license), details.license)
                        SharedExtensionDetailLine(
                            stringResource(Res.string.settings_package_types),
                            details.types.joinToString(", "),
                        )
                        SharedExtensionDetailLine(stringResource(Res.string.settings_package_size), details.size)
                        SharedExtensionDetailLine(
                            stringResource(Res.string.settings_package_dependencies),
                            details.dependencies,
                        )
                    }
                }
                val links = listOf(
                    stringResource(Res.string.settings_package_npm) to details.npmUrl,
                    stringResource(Res.string.settings_package_repository) to details.repositoryUrl,
                ).filter { it.second.isNotBlank() }
                if (links.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        links.forEach { (label, url) ->
                            SharedSmallChipButton(
                                label = label,
                                onClick = { onOpenUrl(url) },
                            )
                        }
                    }
                }
                if (details.readmeMarkdown.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(Res.string.settings_package_readme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AetherOnSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    SharedMarkdownContent(
                        content = details.readmeMarkdown,
                        runtime = null,
                        onOpenLink = { url -> onOpenUrl(url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedInstalledExtensionDetail(
    extension: SharedInstalledExtension,
    snapshot: SharedAetherExtensionSnapshot,
    operating: Boolean,
    operationStatus: String,
    operationFailed: Boolean,
    scriptExtensionsAvailable: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val errors = extension.sharedErrors(snapshot)
    val loadedEntries = extension.sharedLoadedEntries(snapshot)
    val hasRuntimeProblem = !scriptExtensionsAvailable || errors.isNotEmpty()
    SharedExtensionDetailScaffold(title = extension.name, onBack = onBack) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    hasRuntimeProblem -> Icons.Rounded.WarningAmber
                    extension.isEnabled -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.Info
                },
                contentDescription = null,
                tint = when {
                    hasRuntimeProblem -> MaterialTheme.colorScheme.error
                    extension.isEnabled -> AetherPrimary
                    else -> AetherOnSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !scriptExtensionsAvailable ->
                            stringResource(Res.string.shared_extensions_script_runtime_unavailable)
                        errors.isNotEmpty() ->
                            stringResource(Res.string.settings_script_extension_errors_title)
                        extension.isEnabled ->
                            stringResource(Res.string.shared_extensions_runtime_enabled)
                        else -> stringResource(Res.string.settings_extension_disabled)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = AetherOnSurface,
                )
            }
            Switch(
                checked = extension.isEnabled,
                onCheckedChange = onSetEnabled,
                enabled = scriptExtensionsAvailable && !operating,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AetherOnPrimary,
                    checkedTrackColor = AetherPrimary,
                ),
            )
        }
        if (extension.description.isNotBlank()) {
            Text(
                extension.description,
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurfaceVariant,
            )
        }
        SettingsCardGroup {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SharedExtensionDetailLine(
                    stringResource(Res.string.settings_package_source),
                    if (extension.kind == SharedExtensionInstallKind.Imported) {
                        stringResource(Res.string.shared_extensions_imported)
                    } else {
                        extension.source
                    },
                )
                SharedExtensionDetailLine(stringResource(Res.string.settings_package_version), extension.version)
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_installed_path),
                    extension.installedPath,
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_pi_entries_label),
                    extension.extensionCount.toString(),
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_aether_entries_label),
                    extension.aetherExtensionCount.toString(),
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_skills_label),
                    extension.skillCount.toString(),
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_prompts_label),
                    extension.promptCount.toString(),
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_themes_label),
                    extension.themeCount.toString(),
                )
                SharedExtensionDetailLine(
                    stringResource(Res.string.shared_extensions_loaded_entries_label),
                    loadedEntries.toString(),
                )
            }
        }
        if (errors.isNotEmpty()) SharedExtensionErrorCard("", errors)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (extension.kind == SharedExtensionInstallKind.Package) {
                Button(
                    onClick = onUpdate,
                    enabled = !operating,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AetherSurfaceHigher),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.settings_update), color = AetherOnSurface)
                }
            }
            Button(
                onClick = onRemove,
                enabled = !operating,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AetherSurfaceHigher),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.common_remove), color = MaterialTheme.colorScheme.error)
            }
        }
        if (operating) SharedExtensionLoadingRow(
            stringResource(Res.string.shared_extensions_operation_in_progress)
        )
        if (operationStatus.isNotBlank()) {
            Text(
                operationStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (operationFailed) MaterialTheme.colorScheme.error else AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SharedExtensionDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(title = title, onBack = onBack)
    }
}

@Composable
private fun SharedExtensionDetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun sharedCompatibilityMessage(issue: SharedPiCompatibilityIssue): String = when (issue) {
    SharedPiCompatibilityIssue.InteractiveUi ->
        stringResource(Res.string.settings_package_incompatible_interactive_ui)
    SharedPiCompatibilityIssue.Theme ->
        stringResource(Res.string.settings_package_incompatible_theme)
    SharedPiCompatibilityIssue.Prompt ->
        stringResource(Res.string.settings_package_incompatible_prompt)
    SharedPiCompatibilityIssue.Platform ->
        stringResource(Res.string.shared_extensions_platform_compatibility)
}

@Composable
private fun SharedRemoveExtensionDialog(
    extensionName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.shared_extensions_remove_title)) },
        text = { Text(stringResource(Res.string.shared_extensions_remove_body, extensionName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.common_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
