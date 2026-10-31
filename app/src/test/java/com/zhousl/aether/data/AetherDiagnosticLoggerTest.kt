package com.zhousl.aether.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherDiagnosticLoggerTest {
    @Test
    fun stackTracesKeepDeepFramesAndStillRedactSecrets() {
        val stack = "token=private-value\n" + "    at example.Frame.method(Frame.kt:10)\n".repeat(120)
        val sanitized = DiagnosticRedactor.sanitizeMap(
            mapOf("exception_stack" to stack, "stackTrace" to stack, "stack_trace" to stack, "message" to stack),
        )
        for (key in listOf("exception_stack", "stackTrace", "stack_trace")) {
            val value = sanitized.getString(key)
            assertTrue(value.length > 700)
            assertTrue(value.length <= 5_000)
            assertFalse(value.contains("private-value"))
        }
        val key = "message"
        assertTrue((sanitized.getString(key)).length <= 700)
        assertTrue(DiagnosticRedactor.sanitizeString(stack.repeat(2), 5_000).length <= 5_000)
    }

    @Test
    fun redactorRemovesSensitiveValuesAndLargeContent() {
        val sanitized = DiagnosticRedactor.sanitizeMap(
            mapOf(
                "apiKey" to "sk-secret-value",
                "authorization" to "Bearer abc123",
                "message" to "failed with Authorization: Bearer token-value and api_key=another-secret",
                "screenshot_base64" to "abcd".repeat(200),
                "nested" to JSONObject().put("password", "hunter2").put("status", "failed"),
            )
        )

        assertEquals("[REDACTED]", sanitized.getString("apiKey"))
        assertEquals("[REDACTED]", sanitized.getString("authorization"))
        assertFalse(sanitized.toString().contains("sk-secret-value"))
        assertFalse(sanitized.toString().contains("token-value"))
        assertFalse(sanitized.toString().contains("another-secret"))
        assertFalse(sanitized.toString().contains("hunter2"))
        assertTrue(sanitized.getString("screenshot_base64").startsWith("[OMITTED"))
        assertEquals("failed", sanitized.getJSONObject("nested").getString("status"))
    }

    @Test
    fun sanitizedBaseUrlDropsQuerySecrets() {
        val sanitized = DiagnosticRedactor.sanitizedBaseUrl(
            "https://example.com/v1/chat/completions?api_key=secret-token"
        )

        assertEquals("https://example.com/v1/chat/completions", sanitized)
    }
}
