package com.zhousl.aether.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract as Docs
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Generic SAF adapter only. Project layout, records, builds and policies live in
 * the script extension. No broad storage permission or hard-coded shared path. */
class SharedDocuments(private val context: Context, kernel: AetherModKernel) {
    private val resolver = context.contentResolver
    private val journals = File(context.filesDir, "document-write-journals").apply { mkdirs() }
    private val lock = Mutex()
    private data class Read(val tree: String, val input: InputStream, var offset: Long = 0, var touched: Long = System.currentTimeMillis())
    private val reads = mutableMapOf<String, Read>()
    private val writes = mutableMapOf<String, Write>()
    private data class Write(val tree: String, val path: String, val expected: String?, val bytes: Long, val file: File)
    var choose: (suspend (Boolean) -> Uri?)? = null
    init {
        context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("shared-write-") && it.name.endsWith(".tmp") }.forEach { it.delete() }
        kernel.services.register("documents", "aether-core", priority = -10_000,
            description = "User-granted document trees; bounded IO and revision-checked file replacement.",
            methods = listOf("chooseTree", "chooseFile", "stat", "list", "read", "readFile", "openRead", "readNext", "closeRead", "beginWrite", "chunk", "commit", "cancel", "mkdir", "remove").map { AetherModServiceMethod(it) },
            handler = { method, args -> invoke(method, args) })
    }
    private fun granted(text: String): Uri {
        val uri = Uri.parse(text)
        require(uri.scheme == "content" && resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) { "Choose the project folder again to restore access." }
        return uri
    }
    private fun parts(path: String): List<String> {
        if (path.isEmpty()) return emptyList()
        require(path.length <= 2048 && !path.contains('\\') && !path.contains('\u0000'))
        return path.split('/').also { require(it.all { name -> name.isNotBlank() && name != "." && name != ".." && name.length <= 200 && name.none { ch -> ch.code < 32 } }) }
    }
    private fun children(tree: Uri, parent: Uri): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        resolver.query(Docs.buildChildDocumentsUriUsingTree(tree, Docs.getDocumentId(parent)), arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                require(result.size < 8192) { "Directory exceeds its 8192-entry bound." }
                result.add(JSONObject().put("uri", Docs.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString()).put("name", cursor.getString(1)).put("directory", cursor.getString(2) == Docs.Document.MIME_TYPE_DIR).put("bytes", cursor.getLong(3)))
            }
        }
        return result
    }
    private fun resolve(tree: Uri, path: String, create: Boolean = false): Uri? {
        var parent = Docs.buildDocumentUriUsingTree(tree, Docs.getTreeDocumentId(tree))
        for (name in parts(path)) {
            val matches = children(tree, parent).filter { it.getString("name") == name }
            require(matches.size <= 1) { "Ambiguous document name: $name" }
            parent = matches.firstOrNull()?.getString("uri")?.let(Uri::parse)
                ?: if (create) Docs.createDocument(resolver, parent, Docs.Document.MIME_TYPE_DIR, name) ?: error("Could not create directory.") else return null
        }
        return parent
    }
    private fun digest(uri: Uri): String = (if (uri.scheme == "file") File(uri.path!!).inputStream() else resolver.openInputStream(uri)!!).use { input ->
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); var total = 0L
        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 32L * 1024 * 1024); hash.update(buffer, 0, n) }
        hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun recover(treeText: String) {
        for (file in journals.listFiles().orEmpty().filter { it.extension == "json" }) {
            val value = JSONObject(file.readText())
            if (value.getString("tree") != treeText) continue
            val tree = granted(treeText); val pieces = parts(value.getString("path"))
            val parent = resolve(tree, pieces.dropLast(1).joinToString("/")) ?: error("Interrupted write parent is unavailable; no files were reset.")
            val entries = children(tree, parent)
            fun named(name: String): Uri? { val matches = entries.filter { it.getString("name") == name }; require(matches.size <= 1); return matches.firstOrNull()?.getString("uri")?.let(Uri::parse) }
            val target = named(pieces.last()); val old = named(value.getString("previous")); val stage = named(value.getString("stage"))
            val expected = value.optString("expected")
            if (target == null && old != null) {
                require(digest(old) == expected) { "Interrupted-write backup was edited; review it before recovery." }
                require(Docs.renameDocument(resolver, old, pieces.last()) != null)
            } else if (target != null && old != null && digest(target) == value.getString("next") && digest(old) == expected) {
                Docs.deleteDocument(resolver, old)
            } else if (target == null && expected.isNotEmpty()) error("An interrupted shared write needs recovery; existing project data was not reset.")
            if (stage != null && digest(stage) == value.getString("next")) Docs.deleteDocument(resolver, stage)
            file.delete()
        }
    }
    suspend fun invoke(method: String, args: JSONObject): JSONObject {
        if (method == "chooseTree" || method == "chooseFile") return withContext(Dispatchers.Main.immediate) {
            val uri = (choose ?: error("The native document picker is not ready."))(method == "chooseTree")
                ?: return@withContext JSONObject().put("cancelled", true)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (method == "chooseTree") Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            resolver.takePersistableUriPermission(uri, flags)
            JSONObject().put("uri", uri.toString())
        }
        return withContext(Dispatchers.IO) { lock.withLock {
            reads.entries.removeAll { (_, read) -> if (System.currentTimeMillis() - read.touched > 120_000) { runCatching { read.input.close() }; true } else false }
            if (method == "readNext" || method == "closeRead") {
                val token = args.getString("reader"); val read = reads[token]
                if (method == "closeRead") { if (read != null) { require(read.tree == args.getString("tree")); reads.remove(token); read.input.close() }; return@withLock JSONObject().put("closed", true) }
                require(read != null && read.tree == args.getString("tree")) { "Read session expired; reload the record." }
                granted(read.tree)
                require(args.getLong("offset") == read.offset) { "Read chunks must be sequential." }
                val buffer = ByteArray(args.optInt("limit", 262144).coerceIn(1, 262144)); var count = 0
                try {
                    while (count < buffer.size) { val n = read.input.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                    read.offset += count; read.touched = System.currentTimeMillis(); require(read.offset <= 32L * 1024 * 1024) { "Read exceeds its budget." }
                    if (count < buffer.size) { reads.remove(token); read.input.close() }
                    return@withLock JSONObject().put("base64", Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP)).put("bytes", count)
                } catch (error: Exception) { reads.remove(token); runCatching { read.input.close() }; throw error }
            }
            // Expire only owned incomplete private transfers, never shared projects.
            writes.entries.removeAll { (_, w) -> if (System.currentTimeMillis() - w.file.lastModified() > 600_000) { w.file.delete(); true } else false }
            val recoveryTree = if (args.has("tree")) args.getString("tree") else writes[args.optString("transfer")]?.tree
            if (recoveryTree != null) recover(recoveryTree)
            if (method in setOf("chunk", "commit", "cancel")) {
                val token = args.getString("transfer"); val w = writes[token] ?: error("Unknown/expired file transfer.")
                when (method) {
                    "cancel" -> { writes.remove(token); w.file.delete(); return@withLock JSONObject().put("cancelled", true) }
                    "chunk" -> {
                        val text = args.getString("base64"); require(text.length <= 90_000)
                        val bytes = Base64.decode(text, Base64.NO_WRAP)
                        require(bytes.size <= 65536 && args.getLong("offset") == w.file.length() && w.file.length() + bytes.size <= w.bytes)
                        w.file.appendBytes(bytes); return@withLock JSONObject().put("offset", w.file.length())
                    }
                    else -> {
                        require(w.file.length() == w.bytes)
                        val tree = granted(w.tree); val current = resolve(tree, w.path)
                        require((current?.let(::digest)) == w.expected) { "File changed externally; replacement stopped and its bytes were retained." }
                        val pieces = parts(w.path); val parent = resolve(tree, pieces.dropLast(1).joinToString("/"), true)!!
                        val staged = Docs.createDocument(resolver, parent, "application/octet-stream", ".aether-write-$token") ?: error("Could not stage shared file.")
                        var previous: Uri? = null; var installed = false
                        val journal = File(journals, "$token.json")
                        try {
                            resolver.openOutputStream(staged, "w")!!.use { output -> w.file.inputStream().use { it.copyTo(output, 65536) } }
                            require(digest(staged) == digest(Uri.fromFile(w.file))) { "Shared file write failed verification." }
                            journal.writeText(JSONObject().put("tree", w.tree).put("path", w.path).put("previous", ".aether-previous-$token").put("stage", ".aether-write-$token").put("expected", w.expected ?: "").put("next", digest(Uri.fromFile(w.file))).toString())
                            // Keep the previous bytes on the shared volume until the new
                            // document has its final name. Failed replacement restores them.
                            if (current != null) previous = Docs.renameDocument(resolver, current, ".aether-previous-$token") ?: error("Could not preserve previous shared file.")
                            val target = Docs.renameDocument(resolver, staged, pieces.last()) ?: error("Could not commit shared file.")
                            installed = true
                            // Failure to remove an old owned revision must not report a
                            // failed write after commit. An interrupted provider retains it.
                            previous?.let { runCatching { Docs.deleteDocument(resolver, it) } }
                            journal.delete(); writes.remove(token); w.file.delete()
                            return@withLock JSONObject().put("version", digest(target)).put("bytes", w.bytes)
                        } finally {
                            if (!installed) {
                                val restored = previous == null || runCatching { Docs.renameDocument(resolver, previous, pieces.last()) != null }.getOrDefault(false)
                                runCatching { Docs.deleteDocument(resolver, staged) }
                                if (restored) journal.delete()
                            }
                        }
                    }
                }
            }
            val tree = granted(args.getString("tree")); val path = args.optString("path"); val segments = parts(path)
            if (method == "beginWrite") {
                require(segments.isNotEmpty() && writes.size < 4)
                val count = args.getLong("bytes"); require(count in 0..32L * 1024 * 1024)
                val token = UUID.randomUUID().toString(); val expected = if (args.isNull("expected")) null else args.getString("expected")
                require(expected == null || Regex("[a-f0-9]{64}").matches(expected))
                writes[token] = Write(tree.toString(), path, expected, count, File.createTempFile("shared-write-", ".tmp", context.cacheDir))
                return@withLock JSONObject().put("transfer", token)
            }
            if (method == "mkdir") { resolve(tree, path, true); return@withLock JSONObject().put("ok", true) }
            val uri = if (Docs.isTreeUri(tree)) resolve(tree, path) else tree.also { require(path.isEmpty()) }
            if (uri == null) return@withLock JSONObject().put("exists", false)
            when (method) {
                "readFile" -> {
                    val maximum = args.optInt("maximum", 1048576)
                    require(maximum in 0..1048576) { "Small document budget exceeds 1 MiB." }
                    val bytes = (resolver.openInputStream(uri) ?: error("Could not open document.")).use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= maximum) { "File exceeds its read budget." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val version = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    require(digest(uri) == version) { "File changed during reading; reload before continuing." }
                    JSONObject().put("exists", true).put("bytes", bytes.size).put("version", version).apply {
                        if (bytes.size <= 65536) put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    }
                }
                "openRead" -> {
                    require(reads.size < 8) { "Too many open document readers." }
                    val token = UUID.randomUUID().toString()
                    reads[token] = Read(tree.toString(), resolver.openInputStream(uri) ?: error("Could not open document."))
                    JSONObject().put("reader", token)
                }
                "list" -> {
                    val entries = children(tree, uri).sortedBy { it.getString("name") }; val offset = args.optInt("offset").coerceAtLeast(0)
                    JSONObject().put("entries", JSONArray(entries.drop(offset).take(100))).put("more", offset + 100 < entries.size)
                }
                "stat" -> {
                    var bytes = 0L; var directory = false
                    resolver.query(uri, arrayOf(Docs.Document.COLUMN_SIZE, Docs.Document.COLUMN_MIME_TYPE), null, null, null)?.use { if (it.moveToFirst()) { bytes = it.getLong(0); directory = it.getString(1) == Docs.Document.MIME_TYPE_DIR } }
                    JSONObject().put("exists", true).put("directory", directory).put("bytes", bytes).apply { if (!directory && args.optBoolean("hash")) put("version", digest(uri)) }
                }
                "read" -> {
                    val offset = args.optLong("offset"); require(offset in 0..32L * 1024 * 1024)
                    val buffer = ByteArray(args.optInt("limit", 65536).coerceIn(1, 65536)); var count = 0
                    resolver.openInputStream(uri)!!.use { input -> var remaining = offset; while (remaining > 0) { val skipped = input.skip(remaining); require(skipped > 0) { "Read offset is outside the document." }; remaining -= skipped }; while (count < buffer.size) { val n = input.read(buffer, count, buffer.size - count); if (n < 0) break; count += n } }
                    JSONObject().put("base64", Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP)).put("bytes", count)
                }
                "remove" -> {
                    require(segments.isNotEmpty() && args.getString("expected") == digest(uri)) { "Removal requires the current file revision." }
                    require(Docs.deleteDocument(resolver, uri)); JSONObject().put("ok", true)
                }
                else -> error("Unknown document operation.")
            }
        } }
    }
}
