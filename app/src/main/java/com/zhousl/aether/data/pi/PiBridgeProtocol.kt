package com.zhousl.aether.data.pi

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
        writer.write("{\"id\":")
        writer.writeJsonString(id)
        writer.write(",\"type\":")
        writer.writeJsonString(type)
        writer.write(",\"payload\":")
        writer.writeJsonValue(payload)
        writer.write("}")
    }
}

private fun Writer.writeJsonValue(value: Any?) {
    when (value) {
        null, JSONObject.NULL -> write("null")
        is JSONObject -> {
            write("{")
            val keys = value.keys()
            var first = true
            while (keys.hasNext()) {
                val key = keys.next()
                if (first) {
                    first = false
                } else {
                    write(",")
                }
                writeJsonString(key)
                write(":")
                writeJsonValue(value.opt(key))
            }
            write("}")
        }
        is JSONArray -> {
            write("[")
            for (index in 0 until value.length()) {
                if (index > 0) write(",")
                writeJsonValue(value.opt(index))
            }
            write("]")
        }
        is String -> writeJsonString(value)
        is Number -> write(JSONObject.numberToString(value))
        is Boolean -> write(value.toString())
        else -> writeJsonString(value.toString())
    }
}

private fun Writer.writeJsonString(value: String) {
    write("\"")
    var segmentStart = 0
    value.forEachIndexed { index, character ->
        val replacement = when (character) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\t' -> "\\t"
            '\b' -> "\\b"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\u000c' -> "\\f"
            '\u2028' -> "\\u2028"
            '\u2029' -> "\\u2029"
            else -> null
        }
        if (replacement != null || character <= '\u001f') {
            if (segmentStart < index) {
                write(value, segmentStart, index - segmentStart)
            }
            if (replacement != null) {
                write(replacement)
            } else {
                write("\\u00")
                write(JSON_HEX_DIGITS[character.code shr 4].code)
                write(JSON_HEX_DIGITS[character.code and 0x0f].code)
            }
            segmentStart = index + 1
        }
    }
    if (segmentStart < value.length) {
        write(value, segmentStart, value.length - segmentStart)
    }
    write("\"")
}

private const val JSON_HEX_DIGITS = "0123456789abcdef"

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
