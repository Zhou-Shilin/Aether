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
import com.zhousl.aether.runtime.PiBridgeRequestException
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherSettingsBackground
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

private const val SharedPiPackagesUrl = "https://pi.dev/packages"
private const val SharedExtensionImportLimitBytes = 32 * 1024 * 1024
private const val SharedExtensionExtractedLimitBytes = 128L * 1024L * 1024L
private const val SharedExtensionSingleEntryLimitBytes = 16L * 1024L * 1024L
private const val SharedExtensionEntryLimit = 4096
private val SharedExtensionFileSuffixes = setOf("js", "ts", "mjs", "mts", "cjs", "cts")
private val SharedExtensionIndexNames = SharedExtensionFileSuffixes.map { "index.$it" }

internal enum class SharedExtensionInstallKind {
    Package,
    Imported,
}

internal data class SharedInstalledExtension(
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

internal enum class SharedPiCompatibilityIssue {
    InteractiveUi,
    Theme,
    Prompt,
    Platform,
}

internal data class SharedPiCatalogEntry(
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

internal data class SharedPiPackageDetails(
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

internal class SharedPiExtensionCatalogClient {
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

internal fun parseSharedPiPackageCatalog(html: String): List<SharedPiCatalogEntry> =
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

internal fun parseSharedInstalledPackages(payload: JsonObject): List<SharedInstalledExtension> =
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
    reload: suspend (reloadAgentSessions: Boolean) -> Unit,
    onDeferredDependencyInstall: (String, JsonObject, Boolean) -> Unit = { _, _, _ -> },
): String {
    val suffix = picked.name.substringAfterLast('.', "").lowercase()
    require(suffix == "zip" || suffix in SharedExtensionFileSuffixes) {
        "Choose a Pi extension JavaScript/TypeScript file or a .zip package."
    }
    return if (suffix == "zip") {
        require(picked.bytes.size <= SharedExtensionImportLimitBytes) {
            "Extension archive is too large."
        }
        importSharedExtensionZip(runtime, picked, reload, onDeferredDependencyInstall)
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
    reload: suspend (reloadAgentSessions: Boolean) -> Unit,
): String {
    val root = sharedExtensionImportRoot(runtime)
    runtime.fileSystem.createDirectories(root)
    val fileName = sanitizeSharedExtensionFileName(picked.name)
    val destination = "$root/$fileName"
    val staging = "$root/.aether-import-${platformRandomUuid()}-$fileName"
    val backup = "$root/.aether-backup-${platformRandomUuid()}-$fileName"
    runtime.fileSystem.write(staging, picked.bytes)
    replaceSharedImportedPath(
        runtime = runtime,
        staging = staging,
        destination = destination,
        backup = backup,
        reload = { reload(true) },
    )
    return fileName.substringBeforeLast('.', fileName)
}

private suspend fun importSharedExtensionZip(
    runtime: MultiplatformLocalRuntime,
    picked: PlatformPickedFile,
    reload: suspend (reloadAgentSessions: Boolean) -> Unit,
    onDeferredDependencyInstall: (String, JsonObject, Boolean) -> Unit,
): String {
    val root = sharedExtensionImportRoot(runtime)
    runtime.fileSystem.createDirectories(root)
    val token = platformRandomUuid()
    val archive = "$root/.aether-import-$token.zip"
    val extractionRoot = "$root/.aether-import-$token"
    runtime.fileSystem.write(archive, picked.bytes)
    runtime.fileSystem.createDirectories(extractionRoot)
    var extractionRootMoved = false
    return try {
        val listResult = runSharedExtensionShell(
            runtime,
            "command -v unzip >/dev/null 2>&1 || apk add --no-cache unzip >/dev/null; " +
                "unzip -l -qq ${archive.sharedShellQuote()}",
        )
        check(listResult.exitCode == 0) {
            listResult.stderr.ifBlank { "Unable to inspect the extension archive." }
        }
        val archiveEntries = parseSharedUnzipListing(listResult.stdout)
        val entries = archiveEntries.map { it.path }
        require(entries.size <= SharedExtensionEntryLimit) { "The extension archive contains too many files." }
        require(entries.all(::isSafeSharedArchiveEntry)) {
            "The extension archive contains an unsafe path."
        }
        require(archiveEntries.all { it.size <= SharedExtensionSingleEntryLimitBytes }) {
            "The extension archive contains a file larger than 16 MB."
        }
        require(archiveEntries.sumOf { it.size } <= SharedExtensionExtractedLimitBytes) {
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
        val containsPiExtension = sharedManifestExtensionEntryCount(runtime, packageRoot, manifest, "pi") > 0
        val containsAetherExtension = sharedManifestExtensionEntryCount(runtime, packageRoot, manifest, "aether") > 0
        val deferDependencyInstall = hasSharedExtensionDependencies(manifest)
        if (!deferDependencyInstall) {
            installSharedExtensionDependencies(runtime, packageRoot, manifest)
        }
        replaceSharedImportedPath(
            runtime = runtime,
            staging = packageRoot,
            destination = destination,
            backup = backup,
            reload = {
                if (!deferDependencyInstall) reload(containsPiExtension)
            },
        )
        extractionRootMoved = packageRoot == extractionRoot
        if (deferDependencyInstall) {
            onDeferredDependencyInstall(destination, requireNotNull(manifest), containsPiExtension)
        }
        packageName
    } finally {
        withContext(NonCancellable) {
            runCatching { runtime.fileSystem.remove(archive) }
            if (!extractionRootMoved) {
                runCatching { runtime.fileSystem.remove(extractionRoot, recursive = true) }
            }
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
    if (manifest == null) return
    if (!hasSharedExtensionDependencies(manifest)) return
    val completionMarker = "$packageRoot/node_modules/.aether-install-complete"
    if (runtime.fileSystem.exists(completionMarker)) return
    val command = sharedExtensionNpmInstallCommand(
        hasLockfile = runtime.fileSystem.exists("$packageRoot/package-lock.json"),
    )
    val result = runSharedExtensionShell(runtime, command, workingDirectory = packageRoot)
    check(result.exitCode == 0) {
        result.stderr.ifBlank { result.stdout }.ifBlank { "Unable to install extension dependencies." }
    }
    runtime.fileSystem.write(completionMarker, ByteArray(0))
}

private fun hasSharedExtensionDependencies(manifest: JsonObject?): Boolean =
    manifest != null && listOf("dependencies", "optionalDependencies", "peerDependencies")
        .any { (manifest[it] as? JsonObject)?.isNotEmpty() == true }

internal fun sharedExtensionNpmInstallCommand(hasLockfile: Boolean): String =
    "${if (hasLockfile) "npm ci" else "npm install"} " +
        "--omit=dev --omit=optional --legacy-peer-deps --no-audit --no-fund --prefer-offline"

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
        val marker = sharedRemovedPreinstalledExtensionMarker(runtime, destination)
        if (marker != null && runtime.fileSystem.exists(marker)) {
            runtime.fileSystem.remove(marker)
        }
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

internal suspend fun removeSharedImportedExtension(
    runtime: MultiplatformLocalRuntime,
    installedPath: String,
) {
    val root = sharedExtensionRoots(runtime).map { it.second.trimEnd('/') + "/" }
        .firstOrNull { candidate ->
            installedPath.startsWith(candidate) &&
                installedPath.removePrefix(candidate).none { it == '/' }
        }
    require(root != null) {
        "Refusing to remove an extension outside the managed import directory."
    }
    require(runtime.fileSystem.exists(installedPath)) { "The imported extension no longer exists." }
    runtime.fileSystem.remove(installedPath, recursive = true)
    sharedRemovedPreinstalledExtensionMarker(runtime, installedPath)?.let { marker ->
        runtime.fileSystem.createDirectories(marker.substringBeforeLast('/'))
        runtime.fileSystem.write(marker, "removed\n".encodeToByteArray())
    }
}

private fun sharedRemovedPreinstalledExtensionMarker(
    runtime: MultiplatformLocalRuntime,
    installedPath: String,
): String? {
    val root = sharedExtensionImportRoot(runtime).trimEnd('/')
    val name = installedPath.removePrefix("$root/")
    if (name.isBlank() || '/' in name || installedPath != "$root/$name") return null
    return runtime.homeDirectory.trimEnd('/') + "/.aether/.removed-preinstalled-extensions/$name"
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

internal data class SharedUnzipEntry(
    val size: Long,
    val path: String,
)

private val SharedUnzipListingLine = Regex("""^\s*(\d+)\s+\d[\d-]+\s+\d{2}:\d{2}\s+(.+?)\s*$""")

internal fun parseSharedUnzipListing(output: String): List<SharedUnzipEntry> =
    output.lineSequence().filter(String::isNotBlank).map { line ->
        val match = requireNotNull(SharedUnzipListingLine.find(line)) {
            "Unable to validate the extension archive listing."
        }
        SharedUnzipEntry(
            size = requireNotNull(match.groupValues[1].toLongOrNull()) {
                "Unable to validate the extension archive size."
            },
            path = match.groupValues[2].trim(),
        )
    }.toList()

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

internal data class NativePiExtensionsState(
    val installed: List<SharedInstalledExtension> = emptyList(),
    val catalog: List<SharedPiCatalogEntry> = emptyList(),
    val details: SharedPiPackageDetails? = null,
    val operation: String = "",
    val operationPhase: String = "",
    val message: String = "",
    val error: String = "",
    val installedError: String = "",
    val catalogError: String = "",
)

internal class NativePiExtensionsController(
    private val extensionBridgeClient: SharedPiBridgeClient,
    private val agentBridgeClient: SharedPiBridgeClient,
    private val extensionManager: SharedAetherExtensionManager,
    private val extensionStateStore: SharedExtensionStateStore,
    private val runtime: MultiplatformLocalRuntime,
    private val platformServices: PlatformServices,
) {
    private val catalogClient = SharedPiExtensionCatalogClient()

    suspend fun refreshInstalled(
        previous: NativePiExtensionsState,
    ): NativePiExtensionsState = try {
        previous.copy(
            installed = loadInstalled(),
            installedError = "",
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        previous.copy(installedError = failure.sharedExtensionMessage())
    }

    suspend fun refreshCatalog(
        previous: NativePiExtensionsState,
    ): NativePiExtensionsState = try {
        val catalog = catalogClient.fetchCatalog()
        check(catalog.isNotEmpty()) {
            "Pi package catalog did not contain any installable extensions."
        }
        previous.copy(
            catalog = catalog,
            operation = "",
            catalogError = "",
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        previous.copy(
            operation = "",
            catalogError = failure.sharedExtensionMessage(),
        )
    }

    suspend fun refresh(previous: NativePiExtensionsState): NativePiExtensionsState {
        var state = previous.copy(
            message = "",
            error = "",
            installedError = "",
            catalogError = "",
        )
        state = refreshInstalled(state)
        try {
            extensionManager.refresh()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            state = state.copy(installedError = failure.sharedExtensionMessage())
        }
        return refreshCatalog(state)
    }

    suspend fun fetchDetails(
        previous: NativePiExtensionsState,
        source: String,
    ): NativePiExtensionsState {
        val entry = previous.catalog.firstOrNull { it.source == source }
            ?: return previous.copy(operation = "", error = "Package was not found in the catalog.")
        return try {
            previous.copy(
                details = catalogClient.fetchDetails(entry),
                operation = "",
                error = "",
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            previous.copy(operation = "", error = failure.sharedExtensionMessage())
        }
    }

    suspend fun install(
        previous: NativePiExtensionsState,
        source: String,
        onProgress: suspend (String) -> Unit = {},
    ): NativePiExtensionsState = operate(previous, "Extension installed.") {
        extensionBridgeClient.installExtensionPackage(source, onProgress)
        onProgress("reloading")
        reloadAgentExtensionsIfRunning()
        extensionManager.reload()
    }

    suspend fun update(
        previous: NativePiExtensionsState,
        id: String,
        onProgress: suspend (String) -> Unit = {},
    ): NativePiExtensionsState {
        val extension = previous.installed.firstOrNull { it.id == id }
            ?: return previous.copy(operation = "", error = "Installed extension was not found.")
        return operate(previous, "Extension updated.") {
            require(extension.kind == SharedExtensionInstallKind.Package) {
                "Imported extensions must be replaced by importing them again."
            }
            extensionBridgeClient.updateExtensionPackage(extension.source, onProgress)
            onProgress("reloading")
            reloadAgentExtensionsIfRunning()
            extensionManager.reload()
        }
    }

    suspend fun remove(
        previous: NativePiExtensionsState,
        id: String,
    ): NativePiExtensionsState {
        val extension = previous.installed.firstOrNull { it.id == id }
            ?: return previous.copy(operation = "", error = "Installed extension was not found.")
        return operate(previous, "Extension removed.") {
            when (extension.kind) {
                SharedExtensionInstallKind.Package -> {
                    val response = extensionBridgeClient.removeExtensionPackage(extension.source)
                    check(response["removed"]?.jsonPrimitive?.booleanOrNull == true) {
                        "No installed extension matched ${extension.source}."
                    }
                    extensionStateStore.removePackage(extension.source)
                }
                SharedExtensionInstallKind.Imported -> {
                    removeSharedImportedExtension(runtime, extension.installedPath)
                    extensionStateStore.removeImportedExtension(extension.installedPath)
                }
            }
            reloadAgentExtensionsIfRunning()
            extensionManager.refresh()
        }
    }

    suspend fun setEnabled(
        previous: NativePiExtensionsState,
        id: String,
        enabled: Boolean,
    ): NativePiExtensionsState {
        val extension = previous.installed.firstOrNull { it.id == id }
            ?: return previous.copy(operation = "", error = "Installed extension was not found.")
        return operate(previous, "Extension settings updated.") {
            when (extension.kind) {
                SharedExtensionInstallKind.Package ->
                    extensionStateStore.setPackageEnabled(extension.source, enabled)
                SharedExtensionInstallKind.Imported ->
                    extensionStateStore.setImportedExtensionEnabled(extension.installedPath, enabled)
            }
            reloadAgentExtensionsIfRunning()
            extensionManager.reload()
        }
    }

    suspend fun import(previous: NativePiExtensionsState): NativePiExtensionsState {
        val picked = platformServices.pickFile(imagesOnly = false)
            ?: return previous.copy(operation = "")
        var deferred: Triple<String, JsonObject, Boolean>? = null
        return operate(previous, "Extension imported.") {
            importSharedExtension(
                runtime = runtime,
                picked = picked,
                reload = { reloadSessions ->
                    if (reloadSessions) reloadAgentExtensionsIfRunning()
                    extensionManager.reload()
                },
                onDeferredDependencyInstall = { path, manifest, containsPi ->
                    deferred = Triple(path, manifest, containsPi)
                },
            )
            deferred?.let { (path, manifest, containsPi) ->
                installSharedExtensionDependencies(runtime, path, manifest)
                if (containsPi) reloadAgentExtensionsIfRunning()
                extensionManager.reload()
            }
        }
    }

    private suspend fun operate(
        previous: NativePiExtensionsState,
        successMessage: String,
        operation: suspend () -> Unit,
    ): NativePiExtensionsState = try {
        operation()
        previous.copy(
            installed = loadInstalled(),
            operation = "",
            message = successMessage,
            error = "",
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Throwable) {
        // Installation may have completed before a reload failed. Reconcile the
        // durable package list even when the operation reports an error.
        refreshInstalled(previous).copy(operation = "", operationPhase = "", message = "", error = failure.sharedExtensionMessage())
    }

    private suspend fun reloadAgentExtensionsIfRunning() {
        try {
            agentBridgeClient.reloadAllExtensions(
                startIfNeeded = false,
                reloadAetherExtensions = false,
            )
        } catch (failure: PiBridgeRequestException) {
            if (failure.code != "pi_bridge_not_running") throw failure
        }
    }

    private suspend fun loadInstalled(): List<SharedInstalledExtension> = coroutineScope {
        val packagesRequest = async { extensionBridgeClient.listExtensionPackages() }
        val importsRequest = async { listSharedImportedExtensions(runtime) }
        val optionsRequest = async { extensionStateStore.load() }
        val options = optionsRequest.await()
        val packages = parseSharedInstalledPackages(packagesRequest.await())
            .map { extension ->
                extension.copy(isEnabled = extension.source !in options.disabledPackageSources)
            }
        val imports = importsRequest.await()
            .filter { it.extensionCount > 0 }
            .map { extension ->
                val baseName = extension.installedPath.substringAfterLast('/')
                val disabled = extension.installedPath in options.disabledExtensionPaths ||
                    options.disabledExtensionPaths.any { it.substringAfterLast('/') == baseName }
                extension.copy(isEnabled = !disabled)
            }
        (packages + imports).sortedBy { it.name.lowercase() }
    }
}

private fun Throwable.sharedExtensionMessage(): String =
    message?.trim().takeUnless { it.isNullOrBlank() }
        ?: this::class.simpleName.orEmpty().ifBlank { "Extension operation failed." }
