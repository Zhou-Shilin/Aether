package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedDiagnosticLoggerTest {
    @Test
    fun stackTracesKeepDeepFramesAndStillRedactSecrets() {
        val stack = "token=private-value\n" + "    at example.Frame.method(Frame.kt:10)\n".repeat(120)
        val sanitized = SharedDiagnosticRedactor.sanitizeMap(
            mapOf("exception_stack" to stack, "stackTrace" to stack, "stack_trace" to stack, "message" to stack),
        )
        for (key in listOf("exception_stack", "stackTrace", "stack_trace")) {
            val value = sanitized[key]?.jsonPrimitive?.content.orEmpty()
            assertTrue(value.length > 700)
            assertTrue(value.length <= 5_000)
            assertFalse(value.contains("private-value"))
        }
        val key = "message"
        assertTrue((sanitized[key]?.jsonPrimitive?.content.orEmpty()).length <= 700)
        assertTrue(SharedDiagnosticRedactor.sanitizeString(stack.repeat(2), 5_000).length <= 5_000)
    }

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
