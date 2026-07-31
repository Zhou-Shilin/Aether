package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedDiagnosticLoggerTest {
    @Test
    fun diagnosticDetailsUseAndroidCompatibleRedactionRules() {
        val sanitized = SharedDiagnosticRedactor.sanitizeMap(
            mapOf(
                "apiKey" to "plain-secret",
                "message" to "request failed token=inline-secret",
                "requestBody" to "large request contents",
                "nested" to mapOf("Authorization" to "Bearer nested-secret"),
                "attempt" to 3,
            ),
        )

        assertEquals("[REDACTED]", sanitized["apiKey"]?.jsonPrimitive?.content)
        assertEquals(
            "request failed token=[REDACTED]",
            sanitized["message"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "[OMITTED content_chars=22]",
            sanitized["requestBody"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "[REDACTED]",
            sanitized["nested"]?.jsonObject?.get("Authorization")?.jsonPrimitive?.content,
        )
        assertEquals(3, sanitized["attempt"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun diagnosticBaseUrlsOmitCredentialsQueryAndFragment() {
        val sanitized = SharedDiagnosticRedactor.sanitizedBaseUrl(
            "https://user:password@example.test:8443/v1/models?api_key=secret#response",
        )

        assertEquals("https://example.test:8443/v1/models", sanitized)
        assertFalse("password" in sanitized)
        assertFalse("secret" in sanitized)
        assertTrue(sanitized.startsWith("https://"))
    }
}
