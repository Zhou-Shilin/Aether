package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.zhousl.aether.data.pi.PiKernelBridge
import com.zhousl.aether.runtime.AlpineRuntime
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

private const val PiPackagesUrl = "https://pi.dev/packages"
private const val AetherExtensionGuestDirectory = "/root/.aether/extensions"
private const val PiUserExtensionGuestDirectory = "/root/.pi/agent/extensions"
private const val MaxExtensionArchiveBytes = 32L * 1024L * 1024L
private const val MaxExtensionExtractedBytes = 128L * 1024L * 1024L
private const val MaxExtensionEntryBytes = 16L * 1024L * 1024L
private const val MaxExtensionZipEntries = 4096
private const val MaxExtensionBackupEntries = 8192
private const val ExtensionDependencyInstallTimeoutMillis = 5L * 60L * 1000L
private val ExtensionFilePattern = Regex(""".*\.(?:[cm]?[jt]s)$""", RegexOption.IGNORE_CASE)
private val ExtensionIndexNames = setOf(
    "index.ts",
    "index.js",
    "index.mts",
    "index.mjs",
    "index.cts",
    "index.cjs",
)

enum class PiExtensionInstallKind {
    Package,
    Imported,
}

data class InstalledPiExtension(
    val id: String,
    val name: String,
    val source: String,
    val version: String = "",
    val description: String = "",
    val installedPath: String = "",
    val extensionCount: Int = 0,
    val aetherExtensionCount: Int = 0,
    val nativeEntrypointCount: Int = 0,
    val skillCount: Int = 0,
    val promptCount: Int = 0,
    val themeCount: Int = 0,
    val isEnabled: Boolean = true,
    val kind: PiExtensionInstallKind,
)

enum class PiPackageCompatibilityIssue {
    InteractiveUi,
    Theme,
    Prompt,
    Platform,
}

data class PiExtensionCatalogEntry(
    val name: String,
    val source: String,
    val description: String,
    val author: String,
    val monthlyDownloads: Long,
    val packageUrl: String,
    val npmUrl: String,
    val repositoryUrl: String,
    val types: List<String> = emptyList(),
    val compatibilityIssue: PiPackageCompatibilityIssue? = null,
)

data class PiPackageDetails(
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
    val packageUrl: String,
    val npmUrl: String,
    val repositoryUrl: String,
    val compatibilityIssue: PiPackageCompatibilityIssue?,
)

internal fun parsePiPackageCatalog(html: String): List<PiExtensionCatalogEntry> {
    val document = Jsoup.parse(html, PiPackagesUrl)
    return document
        .select("article[data-package-card]")
        .mapNotNull { card ->
            val declaredTypes = card.attr("data-package-types")
                .split(',', ' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val badgeTypes = card.select(".packages-badges [data-type]")
                .map { it.attr("data-type").trim() }
                .filter { it.isNotBlank() && !it.equals("package", ignoreCase = true) }
            val types = (declaredTypes + badgeTypes)
                .distinctBy { it.lowercase(Locale.US) }
            val name = card.attr("data-package-name").trim()
            val installCommand = card.selectFirst("[data-copy-text^=\"pi install \"]")
                ?.attr("data-copy-text")
                ?.trim()
                .orEmpty()
            val source = installCommand.removePrefix("pi install ").trim()
            if (name.isBlank() || !source.startsWith("npm:")) {
                return@mapNotNull null
            }
            val metadata = card.select(".packages-meta span")
            val packagePath = card.selectFirst("[data-package-path]")
                ?.attr("data-package-path")
                .orEmpty()
            val links = card.select(".packages-links a[href]")
            val description = card.selectFirst(".packages-desc")?.text().orEmpty()
            val searchText = card.attr("data-package-search")
            PiExtensionCatalogEntry(
                name = name,
                source = source,
                description = description,
                author = metadata.firstOrNull()?.text().orEmpty(),
                monthlyDownloads = card.attr("data-package-downloads").toLongOrNull() ?: 0L,
                packageUrl = if (packagePath.isBlank()) "" else "https://pi.dev$packagePath",
                npmUrl = links.firstOrNull { it.attr("href").contains("npmjs.com/package/") }
                    ?.attr("href")
                    .orEmpty(),
                repositoryUrl = links.firstOrNull {
                    val href = it.attr("href")
                    href.contains("github.com/") && !href.contains("/issues/new")
                }?.attr("href").orEmpty(),
                types = types,
                compatibilityIssue = detectPiPackageCompatibility(
                    name = name,
                    description = "$description $searchText",
                    types = types,
                ),
            )
        }
        .distinctBy(PiExtensionCatalogEntry::source)
        .sortedWith(
            compareByDescending<PiExtensionCatalogEntry> { it.monthlyDownloads }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun parsePiPackageDetails(
    html: String,
    packageUrl: String,
): PiPackageDetails {
    val document = Jsoup.parse(html, packageUrl)
    val definitionValues = buildMap {
        val terms = document.select(".detail-grid dt")
        val definitions = document.select(".detail-grid dd")
        terms.forEachIndexed { index, term ->
            val key = term.text().trim().lowercase(Locale.US)
            if (key.isNotBlank()) {
                put(key, definitions.getOrNull(index)?.text().orEmpty())
            }
        }
    }
    val installCommand = document.selectFirst("[data-copy-text^=\"pi install \"]")
        ?.attr("data-copy-text")
        ?.trim()
        .orEmpty()
    val source = installCommand.removePrefix("pi install ").trim()
    val types = document.select(".packages-badges [data-type]")
        .map { it.attr("data-type").trim() }
        .filter { it.isNotBlank() && !it.equals("package", ignoreCase = true) }
        .ifEmpty {
            definitionValues["types"]
                .orEmpty()
                .split(',', ' ')
                .map(String::trim)
                .filter(String::isNotBlank)
        }
        .distinctBy { it.lowercase(Locale.US) }
    val links = document.select(".packages-detail-links a[href]")
    val readmeRoot = document.selectFirst(".packages-readme")
    readmeRoot?.select("[href]")?.forEach { element ->
        element.absUrl("href").takeIf(String::isNotBlank)?.let { element.attr("href", it) }
    }
    readmeRoot?.select("[src]")?.forEach { element ->
        element.absUrl("src").takeIf(String::isNotBlank)?.let { element.attr("src", it) }
    }
    val readmeMarkdown = readmeRoot
        ?.let { FlexmarkHtmlConverter.builder().build().convert(it.outerHtml()).trim() }
        .orEmpty()
    val description = document.selectFirst(".content-description")?.text().orEmpty()
    val name = document.selectFirst(".content-title")?.text().orEmpty()
        .ifBlank { definitionValues["package"].orEmpty() }
    val manifestJson = document.selectFirst(".raw-data-panel")?.text().orEmpty()
    return PiPackageDetails(
        source = source,
        name = name,
        description = description,
        version = definitionValues["version"].orEmpty(),
        published = definitionValues["published"].orEmpty(),
        downloads = definitionValues["downloads"].orEmpty(),
        author = definitionValues["author"].orEmpty(),
        license = definitionValues["license"].orEmpty(),
        size = definitionValues["size"].orEmpty(),
        dependencies = definitionValues["dependencies"].orEmpty(),
        types = types,
        manifestJson = manifestJson,
        readmeMarkdown = readmeMarkdown,
        packageUrl = packageUrl,
        npmUrl = links.firstOrNull { it.attr("href").contains("npmjs.com/package/") }
            ?.attr("href")
            .orEmpty(),
        repositoryUrl = links.firstOrNull {
            val href = it.attr("href")
            href.contains("github.com/") && !href.contains("/issues/new")
        }?.attr("href").orEmpty(),
        compatibilityIssue = detectPiPackageCompatibility(
            name = name,
            description = description,
            types = types,
            readmeMarkdown = readmeMarkdown,
            manifestJson = manifestJson,
        ),
    )
}

internal fun detectPiPackageCompatibility(
    name: String,
    description: String,
    types: List<String>,
    readmeMarkdown: String = "",
    manifestJson: String = "",
): PiPackageCompatibilityIssue? {
    val normalizedTypes = types.map { it.lowercase(Locale.US) }.toSet()
    val text = listOf(name, description, readmeMarkdown, manifestJson)
        .joinToString(" ")
        .lowercase(Locale.US)
    val interactiveUiSignals = listOf(
        "interactive tui",
        "terminal ui",
        "live overlay",
        "status bar",
        "powerline footer",
        "custom footer",
        "custom header",
        "keyboard shortcut",
        "clickable tui",
        "tui click",
        "tui overlay",
        "plan review with annotations",
        "structured questionnaire",
        "webview window",
        "local browser ui",
        "micro-ui",
        "ctx.ui",
        "registershortcut",
    )
    if (
        interactiveUiSignals.any(text::contains) ||
        Regex("""\btui\b""").containsMatchIn(text)
    ) {
        return PiPackageCompatibilityIssue.InteractiveUi
    }
    if ("theme" in normalizedTypes) {
        return PiPackageCompatibilityIssue.Theme
    }
    val platformSignals = listOf(
        "macos only",
        "windows only",
        "darwin only",
        "requires macos",
        "requires windows",
        "x64 only",
        "amd64 only",
    )
    if (platformSignals.any(text::contains)) {
        return PiPackageCompatibilityIssue.Platform
    }
    if ("prompt" in normalizedTypes && normalizedTypes.none { it == "extension" || it == "skill" }) {
        return PiPackageCompatibilityIssue.Prompt
    }
    return null
}

class PiExtensionManager(
    context: Context,
    private val alpineRuntime: AlpineRuntime,
    private val piKernelBridge: PiKernelBridge,
    private val skillManager: AgentSkillManager,
    private val stateRepository: PiExtensionStateRepository,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val appContext = context.applicationContext

    suspend fun listImported(): Result<List<InstalledPiExtension>> = withContext(Dispatchers.IO) {
        runCatching {
            val disabledIds = stateRepository.disabledExtensionIds.first()
            listImportedExtensions()
                .map { extension ->
                    extension.copy(isEnabled = extension.id !in disabledIds)
                }
                .distinctBy(InstalledPiExtension::id)
                .sortedBy { it.name.lowercase(Locale.US) }
        }
    }

    suspend fun fetchCatalog(): Result<List<PiExtensionCatalogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(PiPackagesUrl)
                .header("Accept", "text/html")
                .header("User-Agent", "Aether-Android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Pi package catalog failed with HTTP ${response.code}.")
                }
                val html = response.body?.string() ?: error("Pi package catalog returned an empty body.")
                parsePiPackageCatalog(html)
            }
        }
    }

    suspend fun fetchPackageDetails(
        entry: PiExtensionCatalogEntry,
    ): Result<PiPackageDetails> = withContext(Dispatchers.IO) {
        runCatching {
            require(entry.packageUrl.startsWith("https://pi.dev/packages/")) {
                "Package details must come from pi.dev."
            }
            val request = Request.Builder()
                .url(entry.packageUrl)
                .header("Accept", "text/html")
                .header("User-Agent", "Aether-Android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Pi package details failed with HTTP ${response.code}.")
                }
                val html = response.body?.string()
                    ?: error("Pi package details returned an empty body.")
                parsePiPackageDetails(html, entry.packageUrl).let { details ->
                    details.copy(
                        source = details.source.ifBlank { entry.source },
                        name = details.name.ifBlank { entry.name },
                        description = details.description.ifBlank { entry.description },
                        npmUrl = details.npmUrl.ifBlank { entry.npmUrl },
                        repositoryUrl = details.repositoryUrl.ifBlank { entry.repositoryUrl },
                        compatibilityIssue = details.compatibilityIssue ?: entry.compatibilityIssue,
                    )
                }
            }
        }
    }

    suspend fun listInstalled(): Result<List<InstalledPiExtension>> = withContext(Dispatchers.IO) {
        runCatching {
            val disabledIds = stateRepository.disabledExtensionIds.first()
            val packageResponse = piKernelBridge.listExtensionPackages()
            val packages = packageResponse.optJSONArray("packages")
            val packageSkills = mutableListOf<PiPackageSkillSource>()
            val installedPackages = buildList {
                if (packages != null) {
                    for (index in 0 until packages.length()) {
                        val item = packages.optJSONObject(index) ?: continue
                        val source = item.optString("source").trim()
                        if (source.isBlank()) continue
                        add(
                            InstalledPiExtension(
                                id = "package:$source",
                                name = item.optString("name").ifBlank {
                                    source.removePrefix("npm:")
                                },
                                source = source,
                                version = item.optString("version"),
                                description = item.optString("description"),
                                installedPath = item.optString("installed_path"),
                                extensionCount = item.optInt("extension_count"),
                                aetherExtensionCount = item.optInt("aether_extension_count"),
                                nativeEntrypointCount = item.optInt("native_entrypoint_count"),
                                skillCount = item.optInt("skill_count"),
                                promptCount = item.optInt("prompt_count"),
                                themeCount = item.optInt("theme_count"),
                                kind = PiExtensionInstallKind.Package,
                            )
                        )
                        val skillPaths = item.optJSONArray("skill_paths")
                        if (skillPaths != null) {
                            for (skillIndex in 0 until skillPaths.length()) {
                                val guestPath = skillPaths.optString(skillIndex).trim()
                                if (guestPath.isBlank()) continue
                                val hostPath = alpineRuntime.resolveGuestPath(guestPath)
                                if (!hostPath.exists()) continue
                                packageSkills += PiPackageSkillSource(
                                    packageSource = source,
                                    packageName = item.optString("name").ifBlank {
                                        source.removePrefix("npm:")
                                    },
                                    guestPath = guestPath,
                                    hostPath = hostPath,
                                )
                            }
                        }
                    }
                }
            }
            skillManager.syncPiPackageSkills(packageSkills).getOrThrow()
            val disabledOptions = stateRepository.loadOptions()
            (installedPackages + listImportedExtensions())
                .map { extension ->
                    val baseName = extension.installedPath.substringAfterLast('/')
                    val isExplicitlyDisabled = extension.id in disabledIds ||
                        extension.installedPath in disabledOptions.disabledExtensionPaths ||
                        disabledIds.any { it.substringAfterLast('/') == baseName } ||
                        disabledOptions.disabledExtensionPaths.any { it.substringAfterLast('/') == baseName }
                    extension.copy(isEnabled = !isExplicitlyDisabled)
                }
                .distinctBy(InstalledPiExtension::id)
                .sortedBy { it.name.lowercase(Locale.US) }
        }
    }

    suspend fun installPackage(source: String, onProgress: suspend (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = piKernelBridge.installExtensionPackage(
                source,
                stateRepository.loadOptions(),
                onProgress,
            )
            requireExtensionReloadSucceeded(response.optJSONObject("reload"))
            Unit
        }
    }

    suspend fun updatePackage(source: String, onProgress: suspend (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = piKernelBridge.updateExtensionPackage(
                source,
                stateRepository.loadOptions(),
                onProgress,
            )
            requireExtensionReloadSucceeded(response.optJSONObject("reload"))
            Unit
        }
    }

    suspend fun remove(extension: InstalledPiExtension): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stateRepository.setEnabled(extension.id, enabled = true)
            when (extension.kind) {
                PiExtensionInstallKind.Package -> {
                    val response = piKernelBridge.removeExtensionPackage(
                        extension.source,
                        stateRepository.loadOptions(),
                    )
                    require(response.optBoolean("removed")) {
                        "No installed Pi extension matched ${extension.source}."
                    }
                    requireExtensionReloadSucceeded(
                        piKernelBridge.reloadAllExtensions(stateRepository.loadOptions())
                    )
                }

                PiExtensionInstallKind.Imported -> {
                    removeImportedExtension(extension.installedPath)
                    requireExtensionReloadSucceeded(
                        piKernelBridge.reloadAllExtensions(stateRepository.loadOptions())
                    )
                }
            }
            Unit
        }
    }

    suspend fun setEnabled(
        extension: InstalledPiExtension,
        enabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stateRepository.setEnabled(extension.id, enabled)
            requireExtensionReloadSucceeded(
                piKernelBridge.reloadAllExtensions(stateRepository.loadOptions())
            )
            Unit
        }
    }

    suspend fun loadOptions(): PiExtensionLoadOptions = stateRepository.loadOptions()

    suspend fun exportArchive(): JSONObject = withContext(Dispatchers.IO) {
        val packageSources = piKernelBridge.listExtensionPackages()
            .optJSONArray("packages")
            .toJsonObjects()
            .mapNotNull { item -> item.optString("source").trim().takeIf(String::isNotBlank) }
            .distinct()
        val loadOptions = stateRepository.loadOptions()
        var totalBytes = 0L
        var totalEntries = 0
        val bundles = JSONArray()
        extensionArchiveRoots().forEach { (guestRoot, hostRoot) ->
            hostRoot.listFiles().orEmpty()
                .filterNot { it.name.startsWith(".aether-import-") }
                .sortedBy(File::getName)
                .forEach { entry ->
                    validateExtensionArchiveName(entry.name)
                    require(!Files.isSymbolicLink(entry.toPath())) {
                        "Extensions backup does not support symbolic links."
                    }
                    val files = if (entry.isFile) {
                        listOf(entry)
                    } else {
                        require(entry.isDirectory) { "Unsupported Extension entry: ${entry.name}" }
                        entry.walkTopDown().onEnter { directory ->
                            if (directory != entry && directory.name == "node_modules") {
                                return@onEnter false
                            }
                            require(!Files.isSymbolicLink(directory.toPath())) {
                                "Extensions backup does not support symbolic links."
                            }
                            true
                        }.filter(File::isFile).toList().also { descendants ->
                            require(descendants.none { Files.isSymbolicLink(it.toPath()) }) {
                                "Extensions backup does not support symbolic links."
                            }
                        }
                    }
                    if (files.isEmpty()) return@forEach
                    val encodedFiles = JSONArray()
                    files.sortedBy(File::getPath).forEach { file ->
                        val relativePath = if (entry.isFile) entry.name else file.relativeTo(entry).invariantSeparatorsPath
                        validateExtensionArchiveRelativePath(relativePath)
                        require(file.length() <= MaxExtensionEntryBytes) {
                            "Extension file is too large: $relativePath"
                        }
                        val bytes = file.readBytes()
                        require(bytes.size.toLong() <= MaxExtensionEntryBytes) {
                            "Extension file is too large: $relativePath"
                        }
                        totalBytes += bytes.size
                        require(totalBytes <= MaxExtensionExtractedBytes) {
                            "Extensions backup is too large."
                        }
                        totalEntries += 1
                        require(totalEntries <= MaxExtensionBackupEntries) {
                            "Extensions backup contains too many files."
                        }
                        encodedFiles.put(JSONObject().apply {
                            put("path", relativePath)
                            put("dataBase64", Base64.getEncoder().encodeToString(bytes))
                            put("executable", file.canExecute())
                        })
                    }
                    bundles.put(JSONObject().apply {
                        put("root", guestRoot)
                        put("name", entry.name)
                        put("singleFile", entry.isFile)
                        put("files", encodedFiles)
                    })
                }
        }
        JSONObject().apply {
            put("packageSources", JSONArray(packageSources))
            put("importedBundles", bundles)
            put("disabledExtensionPaths", JSONArray(loadOptions.disabledExtensionPaths.sorted()))
            put("disabledPackageSources", JSONArray(loadOptions.disabledPackageSources.sorted()))
        }
    }

    suspend fun restoreArchive(value: JSONObject) = withContext(Dispatchers.IO) {
        val archive = decodeExtensionArchive(value)
        val currentSources = piKernelBridge.listExtensionPackages()
            .optJSONArray("packages")
            .toJsonObjects()
            .mapNotNull { item -> item.optString("source").trim().takeIf(String::isNotBlank) }
            .toSet()
        val roots = extensionArchiveRoots().toMap()
        roots.values.forEach { root ->
            root.listFiles().orEmpty().forEach { entry ->
                val removed = if (Files.isSymbolicLink(entry.toPath())) entry.delete() else entry.deleteRecursively()
                require(removed) { "Unable to clear Extensions before restore." }
            }
        }
        archive.bundles.forEach { bundle ->
            val root = checkNotNull(roots[bundle.root]) { "Unsupported Extension root: ${bundle.root}" }
            bundle.files.forEach { archivedFile ->
                val relativeTarget = if (bundle.singleFile) bundle.name else "${bundle.name}/${archivedFile.path}"
                val target = File(root, relativeTarget).canonicalFile
                require(target.path.startsWith(root.canonicalPath + File.separator)) {
                    "Extension archive entry escaped the destination directory."
                }
                require(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) {
                    "Unable to prepare Extension restore destination."
                }
                target.writeBytes(archivedFile.data)
                if (archivedFile.executable) {
                    require(target.setExecutable(true, false)) {
                        "Unable to restore executable permission: ${archivedFile.path}"
                    }
                }
            }
            if (bundle.root == AetherExtensionGuestDirectory) {
                alpineRuntime.clearPreinstalledExtensionRemoved(bundle.name)
            }
        }
        archive.bundles.filterNot(DecodedExtensionBundle::singleFile).forEach { bundle ->
            val root = checkNotNull(roots[bundle.root])
            installImportedPackageDependencies(
                packageRoot = File(root, bundle.name),
                guestDirectory = "${bundle.root}/${bundle.name}",
            )
        }
        val disabledIds = buildSet {
            archive.disabledPackageSources.forEach { add("package:$it") }
            archive.disabledExtensionPaths.forEach { path ->
                val scope = if (path.startsWith(PiUserExtensionGuestDirectory)) "pi" else "aether"
                add("import:$scope:$path")
            }
        }
        stateRepository.replaceDisabledExtensionIds(disabledIds)
        val loadOptions = stateRepository.loadOptions()
        currentSources.filterNot(archive.packageSources::contains).forEach { source ->
            piKernelBridge.removeExtensionPackage(source, loadOptions)
        }
        archive.packageSources.filterNot(currentSources::contains).forEach { source ->
            piKernelBridge.installExtensionPackage(source, loadOptions)
        }
        requireExtensionReloadSucceeded(piKernelBridge.reloadAllExtensions(loadOptions))
    }

    suspend fun importFromUri(uri: Uri): Result<InstalledPiExtension> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri)
            val importRoot = alpineRuntime.ensureGuestDirectory(AetherExtensionGuestDirectory)
            val transaction = if (displayName.endsWith(".zip", ignoreCase = true)) {
                importZip(uri, displayName, importRoot)
            } else {
                importExtensionFile(uri, displayName, importRoot)
            }
            try {
                requireExtensionReloadSucceeded(
                    piKernelBridge.reloadAllExtensions(stateRepository.loadOptions())
                )
                val installed = importedExtension(transaction.destination, "aether")
                    ?: error(
                        "The imported source did not contain a loadable Aether or Pi extension."
                    )
                transaction.commit()
                alpineRuntime.clearPreinstalledExtensionRemoved(transaction.destination.name)
                installed
            } catch (throwable: Throwable) {
                val rollbackFailure = runCatching {
                    transaction.rollback()
                }.exceptionOrNull()
                rollbackFailure?.let(throwable::addSuppressed)
                if (rollbackFailure == null) {
                    runCatching {
                        requireExtensionReloadSucceeded(
                            piKernelBridge.reloadAllExtensions(stateRepository.loadOptions())
                        )
                    }.exceptionOrNull()?.let(throwable::addSuppressed)
                }
                throw throwable
            }
        }
    }

    private fun listImportedExtensions(): List<InstalledPiExtension> {
        runCatching { alpineRuntime.installPreinstalledExtensionsSync() }
        val roots = listOf(
            "aether" to alpineRuntime.resolveManagedGuestPath(AetherExtensionGuestDirectory),
            "pi" to alpineRuntime.resolveManagedGuestPath(PiUserExtensionGuestDirectory),
        )
        return roots.flatMap { (scope, root) ->
            root.listFiles()
                .orEmpty()
                .filterNot { it.name.startsWith(".aether-import-") }
                .mapNotNull { importedExtension(it, scope) }
        }
    }

    private fun importedExtension(file: File, scope: String): InstalledPiExtension? {
        val manifest = if (file.isDirectory) {
            runCatching {
                File(file, "package.json").takeIf(File::isFile)
                    ?.readText(Charsets.UTF_8)
                    ?.let(::JSONObject)
            }.getOrNull()
        } else {
            null
        }
        val piEntryCount = when {
            file.isFile && ExtensionFilePattern.matches(file.name) -> 1
            file.isDirectory -> packageExtensionEntries(file, "pi").size
            else -> 0
        }
        val aetherEntryCount = if (file.isDirectory) {
            packageExtensionEntries(file, "aether").size
        } else {
            0
        }
        val nativeEntrypointCount = manifest.nativeEntrypoints().size
        if (
            piEntryCount == 0 &&
            aetherEntryCount == 0 &&
            nativeEntrypointCount == 0
        ) return null
        val guestRoot = if (scope == "pi") {
            PiUserExtensionGuestDirectory
        } else {
            AetherExtensionGuestDirectory
        }
        return InstalledPiExtension(
            id = "import:$scope:$guestRoot/${file.name}",
            name = manifest?.optString("name").orEmpty().ifBlank { file.nameWithoutExtension },
            source = if (scope == "pi") "Pi user directory" else "Imported",
            version = manifest?.optString("version").orEmpty(),
            description = manifest?.optString("description").orEmpty(),
            installedPath = file.canonicalPath,
            extensionCount = piEntryCount,
            aetherExtensionCount = aetherEntryCount,
            nativeEntrypointCount = nativeEntrypointCount,
            kind = PiExtensionInstallKind.Imported,
        )
    }

    private fun packageExtensionEntries(
        directory: File,
        namespace: String = "pi",
    ): List<File> {
        val manifest = runCatching {
            File(directory, "package.json").takeIf(File::isFile)
                ?.readText(Charsets.UTF_8)
                ?.let(::JSONObject)
        }.getOrNull()
        val configuredEntries = manifest
            ?.optJSONObject(namespace)
            ?.optJSONArray("extensions")
            ?.let { extensions ->
                buildList {
                    for (index in 0 until extensions.length()) {
                        val relativePath = extensions.optString(index).trim()
                        if (relativePath.isNotBlank()) {
                            add(File(directory, relativePath))
                        }
                    }
                }
            }
            .orEmpty()
            .filter(File::isFile)
        if (configuredEntries.isNotEmpty()) return configuredEntries
        if (namespace != "pi" || manifest?.optJSONObject("aether") != null) {
            return emptyList()
        }
        return ExtensionIndexNames
            .map { File(directory, it) }
            .filter(File::isFile)
    }

    private fun importExtensionFile(
        uri: Uri,
        displayName: String,
        importRoot: File,
    ): ImportedExtensionTransaction {
        require(ExtensionFilePattern.matches(displayName)) {
            "Choose a Pi extension JavaScript/TypeScript file or a .zip package."
        }
        val safeName = sanitizeFileName(displayName)
        val staging = File(importRoot, ".aether-import-${UUID.randomUUID()}-$safeName")
        val destination = File(importRoot, safeName)
        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open the selected extension file." }
                staging.outputStream().use { output ->
                    copyWithLimit(input, output, MaxExtensionEntryBytes)
                }
            }
            return replaceImportedExtension(staging, destination)
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun importZip(
        uri: Uri,
        displayName: String,
        importRoot: File,
    ): ImportedExtensionTransaction {
        val extractionRoot = File(importRoot, ".aether-import-${UUID.randomUUID()}").apply {
            require(mkdirs()) { "Unable to prepare extension import." }
        }
        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open the selected extension archive." }
                extractZip(input, extractionRoot)
            }
            val packageRoot = locatePackageRoot(extractionRoot)
            val manifestName = runCatching {
                File(packageRoot, "package.json").takeIf(File::isFile)
                    ?.readText(Charsets.UTF_8)
                    ?.let(::JSONObject)
                    ?.optString("name")
            }.getOrNull().orEmpty()
            val destinationName = sanitizeDirectoryName(
                manifestName.ifBlank { displayName.substringBeforeLast('.') }
            )
            val staging = File(importRoot, ".aether-import-${UUID.randomUUID()}-$destinationName")
            try {
                require(packageRoot.copyRecursively(staging, overwrite = false)) {
                    "Unable to stage the imported Extension package."
                }
                installImportedPackageDependencies(
                    packageRoot = staging,
                    guestDirectory = "$AetherExtensionGuestDirectory/${staging.name}",
                )
                val destination = File(importRoot, destinationName)
                return replaceImportedExtension(staging, destination)
            } finally {
                staging.deleteRecursively()
            }
        } finally {
            extractionRoot.deleteRecursively()
        }
    }

    private suspend fun installImportedPackageDependencies(
        packageRoot: File,
        guestDirectory: String,
    ) {
        val plan = npmInstallPlanForPackage(packageRoot) ?: return
        val result = JSONObject(
            alpineRuntime.executeCommand(
                command = plan.command,
                workingDirectory = guestDirectory,
                awaitTimeoutMillis = ExtensionDependencyInstallTimeoutMillis,
            )
        )
        require(result.optBoolean("ok")) {
            result.optString("stderr").trim()
                .ifBlank { result.optString("stdout").trim() }
                .ifBlank { result.optString("errmsg").trim() }
                .ifBlank { "Unable to install Extension npm dependencies." }
        }
    }

    private fun extractZip(
        input: InputStream,
        destinationRoot: File,
    ) {
        val canonicalRoot = destinationRoot.canonicalFile
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(CountingLimitedInputStream(input, MaxExtensionArchiveBytes)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MaxExtensionZipEntries) {
                    "Extension archive contains too many files."
                }
                val relativePath = entry.name.replace('\\', '/').trimStart('/')
                require(
                    relativePath.isNotBlank() &&
                        relativePath.split('/').none { it == ".." }
                ) {
                    "Extension archive contains an unsafe path."
                }
                val target = File(canonicalRoot, relativePath).canonicalFile
                require(
                    target.path == canonicalRoot.path ||
                        target.path.startsWith(canonicalRoot.path + File.separator)
                ) {
                    "Extension archive entry escaped the destination directory."
                }
                if (entry.isDirectory) {
                    require(target.mkdirs() || target.isDirectory) {
                        "Unable to create extension archive directory."
                    }
                } else {
                    val parent = requireNotNull(target.parentFile)
                    require(parent.mkdirs() || parent.isDirectory) {
                        "Unable to create extension archive directory."
                    }
                    target.outputStream().use { output ->
                        val copied = copyWithLimit(zipInput, output, MaxExtensionEntryBytes)
                        totalBytes += copied
                        require(totalBytes <= MaxExtensionExtractedBytes) {
                            "Extension archive expands to too much data."
                        }
                    }
                }
                zipInput.closeEntry()
            }
        }
    }

    private fun locatePackageRoot(extractionRoot: File): File {
        if (packageContainsExtensions(extractionRoot)) return extractionRoot
        val candidates = extractionRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name != "__MACOSX" }
        val candidate = candidates.singleOrNull()
            ?.takeIf(::packageContainsExtensions)
        return candidate ?: error(
            "The archive must contain package.json with pi.extensions, aether.extensions, or aether.native, or an index extension file."
        )
    }

    private fun packageContainsExtensions(directory: File): Boolean =
        packageExtensionEntries(directory, "pi").isNotEmpty() ||
            packageExtensionEntries(directory, "aether").isNotEmpty() ||
            runCatching {
                File(directory, "package.json")
                    .takeIf(File::isFile)
                    ?.readText(Charsets.UTF_8)
                    ?.let(::JSONObject)
                    .nativeEntrypoints()
                    .isNotEmpty()
            }.getOrDefault(false)

    private fun replaceImportedExtension(
        staging: File,
        destination: File,
    ): ImportedExtensionTransaction {
        val backup = destination
            .takeIf(File::exists)
            ?.let {
                File(
                    requireNotNull(destination.parentFile),
                    ".aether-import-backup-${UUID.randomUUID()}-${destination.name}",
                ).also { backup ->
                    moveImportedExtension(destination, backup)
                }
            }
        return try {
            moveImportedExtension(staging, destination)
            ImportedExtensionTransaction(
                destination = destination,
                backup = backup,
            )
        } catch (throwable: Throwable) {
            destination.deleteRecursively()
            backup?.takeIf(File::exists)?.let { moveImportedExtension(it, destination) }
            throw throwable
        }
    }

    private suspend fun removeImportedExtension(installedPath: String) {
        val target = File(installedPath).canonicalFile
        val allowedRoots = listOf(
            alpineRuntime.ensureGuestDirectory(AetherExtensionGuestDirectory).canonicalFile,
            alpineRuntime.ensureGuestDirectory(PiUserExtensionGuestDirectory).canonicalFile,
        )
        require(allowedRoots.any { root -> target.parentFile == root }) {
            "Refusing to remove an extension outside a managed import directory."
        }
        require(target.exists()) { "The imported extension no longer exists." }
        require(target.deleteRecursively()) { "Unable to remove the imported extension." }
        if (target.parentFile == allowedRoots.first()) {
            alpineRuntime.markPreinstalledExtensionRemoved(target.name)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val fromCursor = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return fromCursor
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
                .ifBlank { "extension.ts" }
    }

    private suspend fun extensionArchiveRoots(): List<Pair<String, File>> = listOf(
        AetherExtensionGuestDirectory to alpineRuntime.ensureGuestDirectory(AetherExtensionGuestDirectory).canonicalFile,
        PiUserExtensionGuestDirectory to alpineRuntime.ensureGuestDirectory(PiUserExtensionGuestDirectory).canonicalFile,
    )
}

private data class DecodedExtensionArchive(
    val packageSources: Set<String>,
    val bundles: List<DecodedExtensionBundle>,
    val disabledExtensionPaths: Set<String>,
    val disabledPackageSources: Set<String>,
)

private data class DecodedExtensionBundle(
    val root: String,
    val name: String,
    val singleFile: Boolean,
    val files: List<DecodedExtensionFile>,
)

private data class DecodedExtensionFile(
    val path: String,
    val data: ByteArray,
    val executable: Boolean,
)

private fun decodeExtensionArchive(value: JSONObject): DecodedExtensionArchive {
    val packageSources = value.optJSONArray("packageSources").toStringListStrict("Extension package source")
    val disabledPaths = value.optJSONArray("disabledExtensionPaths")
        .toStringListStrict("Disabled Extension path")
        .map(::normalizeImportedExtensionPath)
        .toSet()
    require(disabledPaths.all { path ->
        path.startsWith("$AetherExtensionGuestDirectory/") ||
            path.startsWith("$PiUserExtensionGuestDirectory/")
    }) { "Extensions backup contains an unsupported disabled path." }
    val disabledSources = value.optJSONArray("disabledPackageSources")
        .toStringListStrict("Disabled Extension package source")
    val bundleKeys = mutableSetOf<String>()
    var totalBytes = 0L
    var totalEntries = 0
    val bundlesArray = value.optJSONArray("importedBundles") ?: JSONArray()
    require(bundlesArray.length() <= MaxExtensionBackupEntries) {
        "Extensions backup contains too many bundles."
    }
    val bundles = buildList {
        for (bundleIndex in 0 until bundlesArray.length()) {
            val bundle = bundlesArray.optJSONObject(bundleIndex)
                ?: throw IllegalArgumentException("Extensions backup contains an invalid bundle.")
            val root = bundle.optString("root")
            require(root == AetherExtensionGuestDirectory || root == PiUserExtensionGuestDirectory) {
                "Unsupported Extension root: $root"
            }
            val name = bundle.optString("name")
            validateExtensionArchiveName(name)
            require(bundleKeys.add("$root/$name")) {
                "Extensions backup contains duplicate bundles: $name"
            }
            val singleFile = bundle.optBoolean("singleFile")
            val filesArray = bundle.optJSONArray("files")
                ?: throw IllegalArgumentException("Extension bundle is missing files: $name")
            require(filesArray.length() > 0) { "Extension bundle is empty: $name" }
            require(!singleFile || filesArray.length() == 1) {
                "Single-file Extension bundle contains multiple files: $name"
            }
            val paths = mutableSetOf<String>()
            val files = buildList {
                for (fileIndex in 0 until filesArray.length()) {
                    val file = filesArray.optJSONObject(fileIndex)
                        ?: throw IllegalArgumentException("Extension bundle contains an invalid file: $name")
                    val path = file.optString("path")
                    validateExtensionArchiveRelativePath(path)
                    require(paths.add(path.replace('\\', '/'))) {
                        "Extension bundle contains duplicate files: $path"
                    }
                    val bytes = runCatching { Base64.getDecoder().decode(file.optString("dataBase64")) }
                        .getOrElse { throw IllegalArgumentException("Extension file is not valid Base64: $path", it) }
                    require(bytes.size.toLong() <= MaxExtensionEntryBytes) {
                        "Extension file is too large: $path"
                    }
                    totalBytes += bytes.size
                    require(totalBytes <= MaxExtensionExtractedBytes) { "Extensions backup is too large." }
                    totalEntries += 1
                    require(totalEntries <= MaxExtensionBackupEntries) {
                        "Extensions backup contains too many files."
                    }
                    add(DecodedExtensionFile(path, bytes, file.optBoolean("executable")))
                }
            }
            add(DecodedExtensionBundle(root, name, singleFile, files))
        }
    }
    return DecodedExtensionArchive(
        packageSources = packageSources,
        bundles = bundles,
        disabledExtensionPaths = disabledPaths,
        disabledPackageSources = disabledSources,
    )
}

private fun validateExtensionArchiveName(value: String) {
    require(value.isNotBlank() && '/' !in value && '\\' !in value && value != "." && value != "..") {
        "Invalid Extension name: $value"
    }
}

private fun validateExtensionArchiveRelativePath(value: String) {
    val normalized = value.replace('\\', '/')
    require(
        normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.split('/').none { it.isBlank() || it == "." || it == ".." }
    ) { "Invalid Extension path: $value" }
}

private fun JSONArray?.toJsonObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray?.toStringListStrict(label: String): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            require(value.isNotBlank()) { "$label is blank." }
            require(add(value)) { "Extensions backup contains a duplicate $label." }
        }
    }
}

internal fun requireExtensionReloadSucceeded(reload: JSONObject?) {
    if (reload == null || reload.optBoolean("succeeded", true)) return
    val messages = buildList {
        reload.optJSONObject("aether_reload")
            ?.optJSONArray("errors")
            ?.let { errors ->
                for (index in 0 until errors.length()) {
                    errors.optJSONObject(index)
                        ?.optString("error")
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
        reload.optJSONArray("sessions")?.let { sessions ->
            for (sessionIndex in 0 until sessions.length()) {
                val session = sessions.optJSONObject(sessionIndex) ?: continue
                val sessionId = session.optString("session_id")
                val errors = session.optJSONArray("errors") ?: continue
                for (errorIndex in 0 until errors.length()) {
                    val message = errors.optJSONObject(errorIndex)
                        ?.optString("error")
                        ?.takeIf(String::isNotBlank)
                        ?: continue
                    add(if (sessionId.isBlank()) message else "$sessionId: $message")
                }
            }
        }
    }.distinct()
    error(
        messages.take(3).joinToString("; ")
            .ifBlank { "Extension files changed, but one or more runtimes rejected the reload." }
    )
}

internal data class NpmInstallPlan(
    val command: String,
)

internal fun npmInstallPlanForPackage(packageRoot: File): NpmInstallPlan? {
    if (File(packageRoot, "node_modules").isDirectory) return null
    val manifest = runCatching {
        File(packageRoot, "package.json")
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.let(::JSONObject)
    }.getOrNull() ?: return null
    val dependencyKeys = listOf(
        "dependencies",
        "optionalDependencies",
        "peerDependencies",
    )
    val hasDependencies = dependencyKeys.any { key ->
        manifest.optJSONObject(key)?.length()?.let { it > 0 } == true
    }
    if (!hasDependencies) return null
    val installCommand = if (File(packageRoot, "package-lock.json").isFile) {
        "npm ci"
    } else {
        "npm install"
    }
    return NpmInstallPlan(
        command = "$installCommand --omit=dev --no-audit --no-fund",
    )
}

private class ImportedExtensionTransaction(
    val destination: File,
    private val backup: File?,
) {
    fun commit() {
        backup?.deleteRecursively()
    }

    fun rollback() {
        destination.deleteRecursively()
        backup?.takeIf(File::exists)?.let { moveImportedExtension(it, destination) }
    }
}

private fun moveImportedExtension(
    source: File,
    destination: File,
) {
    require(!destination.exists()) {
        "Unable to replace the existing Extension."
    }
    val moved = source.renameTo(destination) || runCatching {
        if (source.isDirectory) {
            require(source.copyRecursively(destination, overwrite = false))
            require(source.deleteRecursively())
        } else {
            source.copyTo(destination, overwrite = false)
            require(source.delete())
        }
        true
    }.getOrElse {
        destination.deleteRecursively()
        false
    }
    require(moved) {
        "Unable to store the imported Extension."
    }
}

private fun sanitizeFileName(raw: String): String {
    val extension = raw.substringAfterLast('.', "").lowercase(Locale.US)
    require(extension in setOf("ts", "js", "mts", "mjs", "cts", "cjs")) {
        "Unsupported Pi extension file type."
    }
    val stem = raw.substringBeforeLast('.')
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.')
        .ifBlank { "extension" }
    return "$stem.$extension"
}

private fun sanitizeDirectoryName(raw: String): String =
    raw.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.')
        .ifBlank { "extension-${UUID.randomUUID()}" }

private fun JSONObject?.nativeEntrypoints(): List<String> {
    val native = this
        ?.optJSONObject("aether")
        ?.optJSONObject("native")
        ?: return emptyList()
    val entries = native.optJSONArray("entrypoints")
    if (entries != null) {
        return buildList {
            for (index in 0 until entries.length()) {
                entries.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
    return native.optString("entrypoints")
        .trim()
        .ifBlank { native.optString("entrypoint").trim() }
        .trim()
        .takeIf(String::isNotBlank)
        ?.let(::listOf)
        .orEmpty()
}

private fun copyWithLimit(
    input: InputStream,
    output: java.io.OutputStream,
    limit: Long,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        copied += read
        require(copied <= limit) { "Extension file is too large." }
        output.write(buffer, 0, read)
    }
    return copied
}

private class CountingLimitedInputStream(
    input: InputStream,
    private val limit: Long,
) : java.io.FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) increment(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) increment(read)
        return read
    }

    private fun increment(amount: Int) {
        count += amount
        require(count <= limit) { "Extension archive is too large." }
    }
}
