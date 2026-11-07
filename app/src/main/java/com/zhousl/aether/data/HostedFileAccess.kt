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
    private val sessions = mutableMapOf<String, Session>()
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
            val expired = sessions.filterValues { (appId == null || it.appId == appId) && it.generation < before }.keys.toList()
            expired.forEach { sessions.remove(it)?.let { session -> release(session) } }
        } }
    }
    suspend fun configure(id: String, tree: String, enabled: Boolean): JSONObject = withContext(Dispatchers.IO) { lock.withLock {
        HostedApps.validId(id)
        require(tree.length <= 4096 && (!enabled || android.net.Uri.parse(tree).scheme == "content"))
        if (enabled) checkManifest(id, tree)
        val target = file(id); target.parentFile!!.mkdirs()
        val staged = File(target.parentFile, ".data-access-${UUID.randomUUID()}")
        staged.writeText(JSONObject().put("tree", tree).put("enabled", enabled).toString())
        require(staged.renameTo(target)) { "Could not save the app data grant." }
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
    /** A generous physical ceiling supplements the SDK's stricter logical quotas.
     * It allows safe staged replacements without deleting the authoritative old record. */
    private suspend fun checkSpace(session: Session, write: Write) {
        var bytes = 0L; var nodes = 0
        suspend fun walk(prefix: String) {
            require(prefix.count { it == '/' } < 64) { "App data directory is too deep." }
            var offset = 0
            do {
                val page = call(session, "list", JSONObject().put("path", prefix).put("offset", offset))
                val entries = page.optJSONArray("entries")
                if (entries != null) for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index); require(++nodes <= 4096) { "App data file budget reached; nothing was deleted." }
                    val name = entry.getString("name"); val next = path(session.appId, "$prefix/$name")
                    if (entry.optBoolean("directory")) walk(next)
                    else if (next != write.path) bytes += entry.getLong("bytes")
                    require(bytes + write.bytes <= 256L * 1048576) { "App data size budget reached; nothing was deleted." }
                }
                offset += 100
            } while (page.optBoolean("more"))
        }
        walk("${session.appId}/data")
        require(bytes + write.bytes <= 256L * 1048576)
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
        if (method == "beginWrite") require(body.getLong("bytes") in 0..32L * 1048576)
        if (method == "commit") checkSpace(session, session.writes.getValue(body.getString("transfer")))
        val result = call(session, method, body)
        when (method) {
            "openRead" -> session.readers.add(result.getString("reader"))
            "closeRead" -> session.readers.remove(body.getString("reader"))
            "beginWrite" -> session.writes[result.getString("transfer")] = Write(body.getString("path"), body.getLong("bytes"))
            "commit", "cancel" -> session.writes.remove(body.getString("transfer"))
        }
        result
    } }
}
