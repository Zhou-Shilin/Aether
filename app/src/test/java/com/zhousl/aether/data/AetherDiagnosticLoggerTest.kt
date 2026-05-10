package com.zhousl.aether.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherDiagnosticLoggerTest {
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
