package com.zhousl.aether.data.pi

import android.util.JsonWriter
import java.io.StringWriter
import java.io.Writer
import org.json.JSONArray
import org.json.JSONObject

data class PiBridgeFrame(
    val type: String,
    val id: String,
    val ok: Boolean,
    val event: String = "",
    val payload: JSONObject = JSONObject(),
    val error: PiBridgeError? = null,
) {
    companion object {
        fun parse(line: String): Result<PiBridgeFrame> = runCatching {
            val json = JSONObject(line)
            PiBridgeFrame(
                type = json.optString("type"),
                id = json.optString("id"),
                ok = json.optBoolean("ok", json.optString("type") != "error"),
                event = json.optString("event"),
                payload = json.optJSONObject("payload") ?: JSONObject(),
                error = json.optJSONObject("error")?.let {
                    PiBridgeError(
                        code = it.optString("code"),
                        message = it.optString("message"),
                    )
                },
            )
        }
    }
}

data class PiBridgeError(
    val code: String,
    val message: String,
)

data class PiBridgeRequest(
    val id: String,
    val type: String,
    val payload: JSONObject = JSONObject(),
) {
    fun toJsonLine(): String {
        val writer = StringWriter()
        writeJson(writer)
        return writer.toString()
    }

    fun writeJsonLine(writer: Writer) {
        writeJson(writer)
        writer.write("\n")
    }

    private fun writeJson(writer: Writer) {
        JsonWriter(writer).apply {
            beginObject()
            name("id").value(id)
            name("type").value(type)
            name("payload")
            writeJsonValue(payload)
            endObject()
            flush()
        }
    }
}

private fun JsonWriter.writeJsonValue(value: Any?) {
    when (value) {
        null, JSONObject.NULL -> nullValue()
        is JSONObject -> {
            beginObject()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                name(key)
                writeJsonValue(value.opt(key))
            }
            endObject()
        }
        is JSONArray -> {
            beginArray()
            for (index in 0 until value.length()) {
                writeJsonValue(value.opt(index))
            }
            endArray()
        }
        is String -> value(value)
        is Number -> value(value)
        is Boolean -> value(value)
        else -> value(value.toString())
    }
}

class PiJsonlParser(
    private val onFrame: (PiBridgeFrame) -> Unit,
    private val onInvalidLine: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val buffer = StringBuilder()

    fun accept(chunk: String) {
        buffer.append(chunk)
        while (true) {
            val newlineIndex = buffer.indexOf('\n')
            if (newlineIndex < 0) return
            val line = buffer.substring(0, newlineIndex).trimEnd('\r')
            buffer.delete(0, newlineIndex + 1)
            if (line.isBlank()) continue
            PiBridgeFrame.parse(line).fold(
                onSuccess = onFrame,
                onFailure = { onInvalidLine(line, it) },
            )
        }
    }

    fun flush() {
        val line = buffer.toString().trimEnd('\r')
        buffer.clear()
        if (line.isBlank()) return
        PiBridgeFrame.parse(line).fold(
            onSuccess = onFrame,
            onFailure = { onInvalidLine(line, it) },
        )
    }
}

class PiBridgeException(
    message: String,
    val code: String = "pi_bridge_error",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
