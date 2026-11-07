package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/** Explicit document export only. Temporary private bytes never enter releases,
 * credentials, source folders or browser databases. One bounded transfer/view. */
class DocumentExports(private val context: Context, private val choose: suspend (String) -> Uri?) {
    private val mutex = Mutex()
    private var file: File? = null
    private var token = ""
    private var size = 0L
    private var name = ""
    suspend fun invoke(method: String, args: JSONObject): JSONObject = mutex.withLock {
        when (method) {
            "begin" -> {
                require(file == null) { "Another export is pending." }
                val requested = args.getString("name")
                require(requested.isNotBlank() && requested.length <= 200 && requested.none { it == '/' || it == '\\' || it.code < 32 }) { "Invalid export filename." }
                size = args.getLong("bytes"); require(size in 0..32L * 1024 * 1024) { "Export limit is 32 MiB." }
                name = requested; token = UUID.randomUUID().toString()
                file = withContext(Dispatchers.IO) { File.createTempFile("hosted-export-", ".tmp", context.cacheDir) }
                JSONObject().put("transfer", token)
            }
            "chunk" -> {
                require(args.getString("transfer") == token && file != null)
                val encoded = args.getString("base64"); require(encoded.length <= 65536)
                val bytes = Base64.decode(encoded, Base64.NO_WRAP); require(bytes.size <= 49152)
                val target = file!!
                withContext(Dispatchers.IO) {
                    require(target.length() == args.getLong("offset") && target.length() + bytes.size <= size)
                    target.appendBytes(bytes)
                }
                JSONObject().put("offset", target.length())
            }
            "finish" -> {
                require(args.getString("transfer") == token && file?.length() == size)
                try {
                    val uri = choose(name)
                    if (uri == null) JSONObject().put("saved", false).put("cancelled", true)
                    else {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                                file!!.inputStream().use { input ->
                                    val buffer = ByteArray(65536)
                                    while (true) { coroutineContext.ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                                }
                            } ?: error("Could not open the selected export destination.")
                        }
                        JSONObject().put("saved", true)
                    }
                } finally { close() }
            }
            "cancel" -> { require(args.getString("transfer") == token); close(); JSONObject().put("cancelled", true) }
            else -> error("Unsupported export operation.")
        }
    }
    fun close() { file?.delete(); file = null; token = "" }
}
