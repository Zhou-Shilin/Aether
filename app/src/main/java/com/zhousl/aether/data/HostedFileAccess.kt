package com.zhousl.aether.data

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Generic app-scoped Android file access. Record formats and transactions live in
 * the web SDK. Only trusted extensions can configure a previously reviewed grant. */
class HostedFileAccess(private val context: Context, private val documents: SharedDocuments) {
    private val lock = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicLong()
    private val generations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun generation(appId: String) = generations.getOrPut(appId) { sequence.incrementAndGet() }
    private data class Write(val path: String, val bytes: Long)
    private data class Session(val appId: String, val tree: String, val identity: String, val generation: Long,
        var touched: Long = SystemClock.elapsedRealtime(), val readers: MutableSet<String> = mutableSetOf(), val writes: MutableMap<String, Write> = mutableMapOf())
    private data class SpaceCache(
        val tree: String,
        val generation: Long,
        val fileSizes: MutableMap<String, Long> = mutableMapOf(),
        val directories: MutableSet<String> = mutableSetOf(),
        var totalBytes: Long = 0L,
    ) {
        val nodeCount: Int get() = fileSizes.size + directories.size
    }

    private val sessions = mutableMapOf<String, Session>()
    private val spaceCaches = mutableMapOf<String, SpaceCache>()
    private fun file(id: String) = File(context.filesDir, "hosted-apps-v1/${HostedApps.validId(id)}/data-access.json")
    private fun grant(id: String): JSONObject {
        val source = file(id)
        require(source.isFile && source.length() <= 8192) { "Open this app from Apps to bind its reviewed shared-folder access." }
        return JSONObject(source.readText()).also { require(it.optBoolean("enabled")) { "Shared data access is disabled for this app." } }
    }
    private fun identity(tree: String) = MessageDigest.getInstance("SHA-256").digest(tree.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun path(appId: String, value: String): String {
        require(value.startsWith("$appId/data/") && value.length <= 2048 && !value.contains('\\') &&
            value.split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.length <= 200 && it.none { ch -> ch.code < 32 } }) { "File is outside this app's data folder." }
        return value
    }
    private suspend fun call(session: Session, method: String, args: JSONObject) = documents.invoke(method, JSONObject(args.toString()).put("tree", session.tree))
    private suspend fun release(session: Session) {
        session.readers.forEach { runCatching { call(session, "closeRead", JSONObject().put("reader", it)) } }
        session.writes.keys.forEach { runCatching { call(session, "cancel", JSONObject().put("transfer", it)) } }
        session.readers.clear(); session.writes.clear()
    }
    fun invalidate(appId: String? = null) {
        val before = sequence.incrementAndGet()
        if (appId != null) generations[appId] = before
        else generations.replaceAll { _, _ -> before }
        cleanupScope.launch { lock.withLock {
            if (appId != null) spaceCaches.remove(appId) else spaceCaches.clear()
            val expired = sessions.filterValues { (appId == null || it.appId == appId) && it.generation < before }.keys.toList()
            expired.forEach { sessions.remove(it)?.let { session -> release(session) } }
        } }
    }
    suspend fun clearGrant(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        HostedApps.validId(id)
        generations[id] = sequence.incrementAndGet()
        spaceCaches.remove(id)
        val obsolete = sessions.filterValues { it.appId == id }.keys.toList()
        obsolete.forEach { sessions.remove(it)?.let { session -> release(session) } }
        file(id).delete()
    } }
    suspend fun configure(id: String, tree: String, enabled: Boolean): JSONObject = withContext(Dispatchers.IO) { lock.withLock {
        HostedApps.validId(id)
        require(tree.length <= 4096 && (!enabled || android.net.Uri.parse(tree).scheme == "content"))
        if (enabled) checkManifest(id, tree)
        val target = file(id); target.parentFile!!.mkdirs()
        val staged = File(target.parentFile, ".data-access-${UUID.randomUUID()}")
        staged.writeText(JSONObject().put("tree", tree).put("enabled", enabled).toString())
        require(staged.renameTo(target)) { "Could not save the app data grant." }
        spaceCaches.remove(id)
        val obsolete = sessions.filterValues { it.appId == id && (!enabled || it.tree != tree) }.keys.toList()
        obsolete.forEach { sessions.remove(it)?.let { session -> release(session) } }
        JSONObject().put("configured", true)
    } }
    private suspend fun checkManifest(id: String, tree: String) {
        val record = documents.invoke("readFile", JSONObject().put("tree", tree).put("path", "$id/web-app.json").put("maximum", 65536))
        require(record.optBoolean("exists")) { "Shared project manifest is missing." }
        val manifest = JSONObject(String(android.util.Base64.decode(record.getString("base64"), android.util.Base64.NO_WRAP), Charsets.UTF_8))
        require(manifest.optString("id") == id && manifest.optString("format") == "aether-web-app" && manifest.optInt("version") == 1 && manifest.optJSONObject("capabilities")?.optBoolean("storage") == true) { "The shared project no longer requests data access." }
    }

    /** Populates the physical space cache once per (appId, tree, generation) and updates
     * it incrementally on subsequent commits/removals instead of walking SAF on every write. */
    private suspend fun ensureSpaceCache(session: Session): SpaceCache {
        val existing = spaceCaches[session.appId]
        if (existing != null && existing.tree == session.tree && existing.generation == session.generation) {
            return existing
        }
        val cache = SpaceCache(tree = session.tree, generation = session.generation)
        suspend fun walk(prefix: String) {
            require(prefix.count { it == '/' } < 64) { "App data directory is too deep." }
            var offset = 0
            do {
                val page = call(session, "list", JSONObject().put("path", prefix).put("offset", offset))
                val entries = page.optJSONArray("entries")
                if (entries != null) for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    require(cache.nodeCount < MAX_DATA_NODES) { "App data file budget reached; nothing was deleted." }
                    val name = entry.getString("name"); val next = path(session.appId, "$prefix/$name")
                    if (entry.optBoolean("directory")) {
                        cache.directories.add(next)
                        walk(next)
                    } else {
                        val size = entry.getLong("bytes").coerceAtLeast(0L)
                        cache.fileSizes[next] = size
                        cache.totalBytes += size
                    }
                    require(cache.totalBytes <= MAX_DATA_BYTES) { "App data size budget reached; nothing was deleted." }
                }
                offset += 100
            } while (page.optBoolean("more"))
        }
        walk("${session.appId}/data")
        spaceCaches[session.appId] = cache
        return cache
    }

    private fun parentDirectories(appId: String, filePath: String): List<String> {
        val dataRoot = "$appId/data"
        val segments = filePath.split('/')
        if (segments.size <= 3) return emptyList()
        val result = mutableListOf<String>()
        var current = dataRoot
        for (i in 2 until segments.lastIndex) {
            current = "$current/${segments[i]}"
            result.add(current)
        }
        return result
    }

    private suspend fun checkSpace(session: Session, write: Write) {
        val cache = ensureSpaceCache(session)
        val previousSize = cache.fileSizes[write.path] ?: 0L
        val projectedBytes = (cache.totalBytes - previousSize) + write.bytes
        require(projectedBytes <= MAX_DATA_BYTES) { "App data size budget reached; nothing was deleted." }
        val newFileCount = if (cache.fileSizes.containsKey(write.path)) 0 else 1
        val newDirs = parentDirectories(session.appId, write.path).count { it !in cache.directories }
        require(cache.nodeCount + newFileCount + newDirs <= MAX_DATA_NODES) { "App data file budget reached; nothing was deleted." }
    }

    private fun recordCommitInCache(session: Session, write: Write) {
        val cache = spaceCaches[session.appId] ?: return
        if (cache.tree != session.tree || cache.generation != session.generation) return
        val previousSize = cache.fileSizes.put(write.path, write.bytes) ?: 0L
        cache.totalBytes = (cache.totalBytes - previousSize + write.bytes).coerceAtLeast(0L)
        cache.directories.addAll(parentDirectories(session.appId, write.path))
    }

    private fun recordRemoveInCache(session: Session, removedPath: String) {
        val cache = spaceCaches[session.appId] ?: return
        if (cache.tree != session.tree || cache.generation != session.generation) return
        val previousSize = cache.fileSizes.remove(removedPath)
        if (previousSize != null) {
            cache.totalBytes = (cache.totalBytes - previousSize).coerceAtLeast(0L)
        } else {
            cache.directories.remove(removedPath)
        }
    }

    suspend fun invoke(appId: String, method: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) { lock.withLock {
        val expired = sessions.filterValues { SystemClock.elapsedRealtime() - it.touched > 120000 }.keys.toList()
        expired.forEach { sessions.remove(it)?.let { session -> release(session) } }
        if (method == "begin") {
            require(sessions.size < 40) { "Too many app file sessions." }
            val access = grant(appId); val tree = access.getString("tree"); checkManifest(appId, tree)
            val token = UUID.randomUUID().toString(); val rootIdentity = identity(tree)
            sessions[token] = Session(appId, tree, rootIdentity, generation(appId))
            return@withLock JSONObject().put("session", token).put("appId", appId).put("rootIdentity", rootIdentity)
        }
        val token = args.getString("session"); val session = sessions[token]
        if (method == "end" && session == null) return@withLock JSONObject().put("ended", true)
        require(session != null && session.appId == appId) { "Unknown or expired app file session." }
        if (method == "end") { sessions.remove(token); release(session); return@withLock JSONObject().put("ended", true) }
        require(session.generation == generation(appId) && grant(appId).getString("tree") == session.tree) { "App data grant changed; retry the action." }
        session.touched = SystemClock.elapsedRealtime()
        val body = JSONObject(args.toString()).apply { remove("session"); remove("tree") }
        when (method) {
            "stat", "readFile", "openRead", "beginWrite", "remove" -> path(appId, body.getString("path"))
            "readNext", "closeRead" -> {
                if (method == "closeRead" && body.getString("reader") !in session.readers) return@withLock JSONObject().put("closed", true)
                require(body.getString("reader") in session.readers) { "Reader belongs to another file session." }
            }
            "chunk", "commit", "cancel" -> {
                if (method == "cancel" && !session.writes.containsKey(body.getString("transfer"))) return@withLock JSONObject().put("cancelled", true)
                require(session.writes.containsKey(body.getString("transfer"))) { "Write belongs to another file session." }
            }
            else -> error("Unsupported app file operation.")
        }
        if (method == "beginWrite") require(body.getLong("bytes") in 0..32L * 1024 * 1024)
        val pendingWrite = if (method == "commit") session.writes.getValue(body.getString("transfer")) else null
        if (pendingWrite != null) checkSpace(session, pendingWrite)
        val result = call(session, method, body)
        when (method) {
            "openRead" -> session.readers.add(result.getString("reader"))
            "closeRead" -> session.readers.remove(body.getString("reader"))
            "beginWrite" -> session.writes[result.getString("transfer")] = Write(body.getString("path"), body.getLong("bytes"))
            "commit" -> {
                session.writes.remove(body.getString("transfer"))
                if (pendingWrite != null) recordCommitInCache(session, pendingWrite)
            }
            "cancel" -> session.writes.remove(body.getString("transfer"))
            "remove" -> recordRemoveInCache(session, body.getString("path"))
        }
        result
    } }

    companion object {
        private const val MAX_DATA_NODES = 4096
        private const val MAX_DATA_BYTES = 256L * 1048576
    }
}
