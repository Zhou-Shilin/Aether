package com.zhousl.aether.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.util.Base64
import com.zhousl.aether.HostedAppActivity
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Generic local static-app host. No localhost listener, daemon, Python/Node app
 * server, browser installation, credential export or provider login is involved.
 * Script callers are trusted extensions. Web callers get a separate origin-checked
 * request/reply channel: scoped data files and reviewed capabilities, with no
 * install, source-folder or host-configuration authority.
 */
class HostedApps(private val context: Context, kernel: AetherModKernel, documents: SharedDocuments) {
    private val fileAccess = HostedFileAccess(context, documents)
    private val root = File(context.filesDir, "hosted-apps-v1")
    private val installLock = Mutex()
    private val uploads = mutableMapOf<String, Upload>()
    // Initialized only when a hosted app is requested, never for ordinary Aether use.
    private var webViewStartup: CompletableDeferred<Unit>? = null
    @androidx.annotation.MainThread
    fun prepareWebView(): CompletableDeferred<Unit> {
        webViewStartup?.let { return it }
        val ready = CompletableDeferred<Unit>()
        webViewStartup = ready
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            androidx.webkit.WebViewCompat.startUpWebView(context,
                androidx.webkit.WebViewStartUpConfig.Builder(executor).build(),
                object : androidx.webkit.WebViewOutcomeReceiver<androidx.webkit.WebViewStartUpResult, androidx.webkit.WebViewStartupException> {
                    override fun onResult(result: androidx.webkit.WebViewStartUpResult) {
                        executor.shutdown()
                        ready.complete(Unit)
                    }
                    override fun onError(error: androidx.webkit.WebViewStartupException) {
                        executor.shutdown()
                        ready.completeExceptionally(error)
                    }
                })
        } catch (error: Exception) { executor.shutdown(); ready.completeExceptionally(error) }
        return ready
    }
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val windows = ConcurrentHashMap<String, Int>()
    private val closedWindows = ConcurrentHashMap.newKeySet<String>()
    fun windowApp(id: String): HostedApp? = if (enabled() && id !in closedWindows) read(id) else null
    fun resumeWindow(id: String) {
        if (enabled() && read(id) != null && closedWindows.remove(id)) _revision.value++
    }
    fun attach(app: HostedApp): () -> Unit {
        val key = "${app.id}:${app.version}"
        windows.merge(key, 1, Int::plus)
        return {
            windows.computeIfPresent(key) { _, count -> if (count > 1) count - 1 else null }
            if (windows.keys.none { it.startsWith("${app.id}:") }) fileAccess.invalidate(app.id)
        }
    }
    @Volatile private var events: (suspend (String, JSONObject) -> Unit)? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val preferences = context.getSharedPreferences("hosted-apps", Context.MODE_PRIVATE)
    private fun enabled() = preferences.getBoolean("enabled", true)

    private data class Upload(val appId: String, val metadata: JSONObject, val file: File, val bytes: Long, val digest: String, val created: Long)

    init {
        root.mkdirs()
        context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("hosted-export-") && it.name.endsWith(".tmp") }.forEach { it.delete() }
        root.listFiles().orEmpty().filter { it.name.startsWith(".upload-") }.forEach { it.deleteRecursively() }
        kernel.services.register(
            id = "hosted_apps", owner = "aether-core", priority = -10_000,
            description = "Host local static assets with origin-bound extension requests.",
            methods = listOf("capabilities", "list", "begin", "chunk", "commit", "cancel", "open", "close", "pin", "reply", "setEnabled", "configureData").map {
                AetherModServiceMethod(it, mutatesState = it !in setOf("capabilities", "list"))
            },
            handler = { method, args -> invoke(method, args) },
        )
    }

    fun bindEvents(handler: suspend (String, JSONObject) -> Unit): () -> Unit {
        events = handler
        return { if (events === handler) { events = null; pending.values.forEach { it.cancel() }; pending.clear() } }
    }
    suspend fun extensionRequest(app: HostedApp, method: String, args: JSONObject): JSONObject {
        require(enabled() && windows.containsKey("${app.id}:${app.version}") && read(app.id)?.version == app.version) { "This hosted app is no longer active." }
        require(Regex("^[a-z][a-zA-Z0-9_.-]{0,79}$").matches(method))
        val emit = events ?: error("The extension bridge is not ready.")
        require(pending.size < 16)
        val id = UUID.randomUUID().toString(); val result = CompletableDeferred<JSONObject>(); pending[id] = result
        try {
            emit("hosted_apps.request", JSONObject().put("requestId", id).put("app", app.json()).put("method", method).put("args", args))
            return withTimeout(125_000) { result.await() }
        } finally {
            pending.remove(id)
            if (!result.isCompleted) withContext(NonCancellable) { runCatching { emit("hosted_apps.cancel", JSONObject().put("requestId", id)) } }
        }
    }

    suspend fun invoke(method: String, args: JSONObject): JSONObject = when (method) {
        "capabilities" -> JSONObject().put("supported", androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER) && enabled()).put("protocol", 1).put("extensionGateway", 1).put("enabled", enabled())
            .put("reason", "Requires an origin-checked Android System WebView bridge; no insecure fallback is enabled.")
            .put("documentWindows", 1).put("nativeUiVersion", 1).put("directFiles", 1).put("staticAssets", true).put("nativeAi", false).put("extensionCapabilities", true).put("browserInstallRequired", false)
            .put("pinConfirmationRequired", true)
        "configureData" -> fileAccess.configure(validId(args.getString("id")), args.optString("tree"), args.getBoolean("enabled"))
        "reply" -> {
            val reply = pending[args.getString("requestId")]
            if (reply != null) {
                if (args.optBoolean("ok")) reply.complete(args.optJSONObject("result") ?: JSONObject())
                else reply.completeExceptionally(IllegalArgumentException(args.optString("error", "Extension request failed.")))
            }
            JSONObject().put("accepted", reply != null)
        }
        "setEnabled" -> { preferences.edit().putBoolean("enabled", args.getBoolean("enabled")).apply(); close(); JSONObject().put("enabled", enabled()) }
        "list" -> withContext(Dispatchers.IO) { JSONObject().put("apps", JSONArray(list().map { it.json() })) }
        "open" -> withContext(Dispatchers.Main.immediate) { require(enabled()) { "Enable native hosting in Apps first." }; val app = read(validId(args.getString("id"))) ?: error("Hosted app is not installed."); closedWindows.remove(app.id); _revision.value++; prepareWebView(); context.startActivity(HostedAppActivity.intent(context, app)); updatePinnedShortcut(app); app.json() }
        "close" -> { close(); JSONObject().put("ok", true) }
        "pin" -> withContext(Dispatchers.Main.immediate) { pin(validId(args.getString("id"))) }
        "begin", "chunk", "commit", "cancel" -> withContext(Dispatchers.IO) { installLock.withLock { install(method, args) } }
        else -> error("Unknown hosted_apps method.")
    }

    fun close() { closedWindows.addAll(windows.keys.map { it.substringBefore(':') }); windows.clear(); fileAccess.invalidate(); _revision.value++ }
    suspend fun fileRequest(app: HostedApp, method: String, args: JSONObject): JSONObject {
        require(enabled() && windows.containsKey("${app.id}:${app.version}") && read(app.id)?.version == app.version) { "This hosted app is no longer active." }
        return fileAccess.invoke(app.id, method, args)
    }
    fun accepts(urlHost: String?): Boolean = urlHost?.endsWith(".apps.aether.invalid") == true
    fun appForHost(host: String?): HostedApp? {
        if (!accepts(host)) return null
        val id = host!!.removeSuffix(".apps.aether.invalid")
        return if (ID.matches(id)) read(id) else null
    }
    fun read(id: String): HostedApp? = runCatching {
        validId(id)
        val record = JSONObject(File(root, "$id/record.json").readText())
        val version = validVersion(record.getString("version"))
        val directory = File(root, "$id/assets/$version").canonicalFile
        require(File(directory, "index.html").isFile)
        HostedApp(id, record.getString("label"), version, directory)
    }.getOrNull()
    private fun list(): List<HostedApp> = root.listFiles().orEmpty().filter { ID.matches(it.name) }.mapNotNull { read(it.name) }
    private fun writeRecord(id: String, record: JSONObject) {
        val directory = File(root, id).apply { mkdirs() }
        val stage = File(directory, ".record-${UUID.randomUUID()}")
        stage.writeText(record.toString(2))
        require(stage.renameTo(File(directory, "record.json"))) { "Could not atomically save hosted app metadata." }
    }

    private fun install(method: String, args: JSONObject): JSONObject {
        val time = System.currentTimeMillis()
        uploads.entries.removeAll { (_, upload) ->
            if (time - upload.created > 10 * 60_000) { upload.file.parentFile?.deleteRecursively(); true } else false
        }
        if (method == "begin") {
            val id = validId(args.getString("id")); val version = validVersion(args.getString("version"))
            require(read(id) != null || list().size < 16) { "Hosted app count limit is 16; no app/data was evicted." }
            val label = args.getString("label").trim(); require(label.isNotEmpty() && label.length <= 64)
            val bytes = args.getLong("bytes"); require(bytes in 1..40L * 1024 * 1024)
            val digest = args.getString("sha256"); require(Regex("^[a-f0-9]{64}$").matches(digest))
            require(uploads.size < 2 && uploads.values.none { it.appId == id }) { "Another installation is in progress." }
            val token = UUID.randomUUID().toString()
            val directory = File(root, ".upload-$token").apply { mkdirs() }
            val metadata = JSONObject().put("id", id).put("version", version).put("label", label)
            uploads[token] = Upload(id, metadata, File(directory, "assets.zip"), bytes, digest, time)
            return JSONObject().put("upload", token).put("chunkBytes", 65536)
        }
        val token = args.getString("upload"); val upload = uploads[token] ?: error("Installation expired or is unknown.")
        if (method == "cancel") {
            uploads.remove(token); upload.file.parentFile?.deleteRecursively(); return JSONObject().put("cancelled", true)
        }
        if (method == "chunk") {
            val encoded = args.getString("base64"); require(encoded.length <= 90_000)
            val chunk = Base64.decode(encoded, Base64.NO_WRAP)
            require(chunk.size in 1..65536 && args.getLong("offset") == upload.file.length()) { "Out-of-order upload refused." }
            require(upload.file.length() + chunk.size <= upload.bytes)
            upload.file.appendBytes(chunk)
            return JSONObject().put("offset", upload.file.length())
        }
        require(method == "commit" && upload.file.length() == upload.bytes) { "Asset upload is incomplete." }
        val hash = MessageDigest.getInstance("SHA-256")
        upload.file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) } }
        require(hash.digest().joinToString("") { "%02x".format(it) } == upload.digest) { "Asset archive hash mismatch." }
        val staging = File(upload.file.parentFile, "content").apply { mkdirs() }.canonicalFile
        var entries = 0; var total = 0L; val names = mutableSetOf<String>()
        ZipInputStream(upload.file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 2048 && entry.name.length <= 512)
                val name = entry.name
                require(!name.startsWith("/") && !name.contains('\\') && !name.contains('\u0000') && !name.contains(':') && name.split('/').none { it == ".." || it == "." }) { "Unsafe archive path." }
                require(names.add(name)) { "Duplicate archive entry." }
                val output = File(staging, name).canonicalFile
                require(output.path.startsWith(staging.path + File.separator))
                if (entry.isDirectory) { output.mkdirs(); continue }
                output.parentFile?.mkdirs(); var fileBytes = 0L
                output.outputStream().use { out ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = zip.read(buffer); if (count < 0) break
                        fileBytes += count; total += count
                        require(fileBytes <= 12L * 1024 * 1024 && total <= 32L * 1024 * 1024) { "Static asset memory/disk budget exceeded." }
                        out.write(buffer, 0, count)
                    }
                }
            }
        }
        require(File(staging, "index.html").isFile) { "Static app index.html is missing." }
        val version = upload.metadata.getString("version")
        val destination = File(root, "${upload.appId}/assets/$version")
        if (destination.exists()) {
            // Same immutable version: never replace possibly open files in place.
            val stagedFiles = staging.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(staging).path }
            val existingFiles = destination.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(destination).path }
            require(stagedFiles.keys == existingFiles.keys && stagedFiles.all { (name, file) ->
                val other = existingFiles.getValue(name)
                file.length() == other.length() && file.inputStream().use { a -> other.inputStream().use { b ->
                    val first = ByteArray(65536); val second = ByteArray(65536)
                    var same = true
                    while (true) { val n = a.read(first); val m = b.read(second); if (n != m) { same = false; break }; if (n < 0) break; if (!(0 until n).all { first[it] == second[it] }) { same = false; break } }
                    same
                } }
            }) { "A version cannot be reused for different asset bytes." }
        } else { destination.parentFile?.mkdirs(); require(staging.renameTo(destination)) }
        fileAccess.invalidate(upload.appId)
        writeRecord(upload.appId, upload.metadata)
        _revision.value++
        uploads.remove(token); upload.file.parentFile?.deleteRecursively()
        // Keep at most current and one previous immutable asset version. No browser data is touched.
        destination.parentFile?.listFiles().orEmpty().filter { it.isDirectory && it.name != version }
            .sortedByDescending { it.lastModified() }.drop(1).forEach { it.deleteRecursively() }
        return read(upload.appId)!!.json().put("installed", true).put("dataCleared", false)
    }

    fun pin(id: String): JSONObject {
        val app = read(id) ?: error("Hosted app is not installed.")
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager?.isRequestPinShortcutSupported != true) return JSONObject().put("requested", false).put("reason", "Launcher pinning is unavailable; the app remains in Apps.")
        val intent = HostedAppActivity.intent(context, app)
        val bitmapFile = File(app.assets, "icon-192.png")
        var icon = Icon.createWithResource(context, context.applicationInfo.icon)
        if (bitmapFile.isFile && bitmapFile.length() <= 1024 * 1024) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(bitmapFile.path, bounds)
            if (bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) {
                BitmapFactory.decodeFile(bitmapFile.path, BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 192) })?.let { icon = Icon.createWithBitmap(it) }
            }
        }
        val shortcut = ShortcutInfo.Builder(context, "hosted.${app.id}").setShortLabel(app.label).setLongLabel(app.label).setIcon(icon).setIntent(intent).build()
        return JSONObject().put("requested", manager.requestPinShortcut(shortcut, null)).put("confirmationRequired", true)
    }
    private fun updatePinnedShortcut(app: HostedApp) {
        val shortcuts = context.getSystemService(ShortcutManager::class.java)
        if (shortcuts?.pinnedShortcuts?.any { it.id == "hosted.${app.id}" } == true) {
            shortcuts.updateShortcuts(listOf(ShortcutInfo.Builder(context, "hosted.${app.id}")
                .setShortLabel(app.label).setLongLabel(app.label)
                .setIntent(HostedAppActivity.intent(context, app)).build()))
        }
    }

    companion object {
        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val VERSION = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$")
        fun validId(value: String): String = value.also { require(ID.matches(it)) { "Invalid hosted app ID." } }
        private fun validVersion(value: String): String = value.also { require(VERSION.matches(it) && it != "." && it != "..") }
    }
}

data class HostedApp(val id: String, val label: String, val version: String, val assets: File) {
    val origin: String get() = "https://$id.apps.aether.invalid"
    fun json(): JSONObject = JSONObject().put("id", id).put("label", label).put("version", version).put("origin", origin)
}
