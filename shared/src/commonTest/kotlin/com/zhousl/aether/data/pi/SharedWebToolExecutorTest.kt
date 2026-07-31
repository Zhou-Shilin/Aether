package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedWebToolExecutorTest {
    @Test
    fun fetchNormalizesUrlAndReturnsReadableHtml() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://example.com/page", request.url.toString())
            assertContains(request.headers[HttpHeaders.UserAgent].orEmpty(), "Android 15")
            assertContains(request.headers[HttpHeaders.Accept].orEmpty(), "text/markdown")
            respond(
                content = """
                    <html>
                      <head><title>Aether docs</title></head>
                      <body>
                        <nav>Navigation must be removed</nav>
                        <main>
                          <h1>Aether</h1>
                          <p>Shared page ${"content ".repeat(20)}</p>
                          <a href="../guide/start">Guide</a>
                          <img src="/images/logo.png" alt="Logo">
                          <script>ignore()</script>
                        </main>
                        <footer>Footer must be removed</footer>
                      </body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val executor = SharedWebToolExecutor({ AppSettings() }, engine)

        val result = executor.execute(
            "web_fetch",
            JsonObject(mapOf("url" to JsonPrimitive("example.com/page"))),
        )

        assertFalse(result.isError)
        assertContains(result.outputJson, "Aether")
        assertContains(result.outputJson, "Shared page")
        assertFalse(result.outputJson.contains("ignore()"))
        assertFalse(result.outputJson.contains("Navigation must be removed"))
        assertFalse(result.outputJson.contains("Footer must be removed"))
        assertContains(result.outputJson, "https://example.com/guide/start")
        assertContains(result.outputJson, "https://example.com/images/logo.png")
        assertFalse(result.outputJson.contains("\"content\":"))
    }

    @Test
    fun definitionsMatchAndroidDescriptionsAndSchemas() {
        val executor = SharedWebToolExecutor({ AppSettings() }, MockEngine { respond("{}") })
        val fetch = executor.definitions[0].jsonObject
        val search = executor.definitions[1].jsonObject

        assertContains(fetch["description"]?.jsonPrimitive?.content.orEmpty(), "Use this when")
        assertContains(search["description"]?.jsonPrimitive?.content.orEmpty(), "Use this for web discovery")
        val fetchParameters = fetch["parameters"]?.jsonObject
        assertEquals(false, fetchParameters?.get("additionalProperties")?.jsonPrimitive?.boolean)
        assertEquals(
            "The HTTP or HTTPS URL to fetch.",
            fetchParameters?.get("properties")?.jsonObject
                ?.get("url")?.jsonObject
                ?.get("description")?.jsonPrimitive?.content,
        )
        val searchProperties = search["parameters"]?.jsonObject?.get("properties")?.jsonObject
        assertEquals("array", searchProperties?.get("include_domains")?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals(
            "string",
            searchProperties?.get("include_domains")?.jsonObject
                ?.get("items")?.jsonObject
                ?.get("type")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun searchUsesConfiguredTavilyEndpointAndBearerToken() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://search.example/v1/search", request.url.toString())
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertContains(request.body.toString(), "Kotlin Multiplatform")
            respond(
                content = """
                    {
                      "answer":"Compose",
                      "results":[
                        {"title":"KMP guide","url":"https://example.com/kmp","content":"Shared Kotlin result"}
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val executor = SharedWebToolExecutor(
            settings = { AppSettings(tavilyApiKey = "secret", tavilyBaseUrl = "https://search.example/v1") },
            engine = engine,
        )

        val result = executor.execute(
            "web_search",
            JsonObject(mapOf("query" to JsonPrimitive("Kotlin Multiplatform"))),
        )

        assertFalse(result.isError)
        assertContains(result.outputJson, "Compose")
        assertContains(result.outputJson, "Top results:")
        assertContains(result.outputJson, "1. KMP guide - https://example.com/kmp")
        assertContains(result.outputJson, "Shared Kotlin result")
        assertContains(result.outputJson, "\"ok\":true")
    }

    @Test
    fun searchAcceptsAndroidCamelCaseAliasesAndHonorsBooleanOptions() = runTest {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                content = "{\"results\":[]}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val executor = SharedWebToolExecutor(
            settings = { AppSettings(tavilyApiKey = "secret") },
            engine = engine,
        )

        val result = executor.execute(
            "tavily_search",
            JsonObject(mapOf(
                "query" to JsonPrimitive("Aether"),
                "searchDepth" to JsonPrimitive("advanced"),
                "maxResults" to JsonPrimitive(12),
                "timeRange" to JsonPrimitive("week"),
                "includeAnswer" to JsonPrimitive(false),
                "includeRawContent" to JsonPrimitive(true),
                "includeDomains" to JsonArray(listOf(JsonPrimitive("example.com"))),
                "excludeDomains" to JsonArray(listOf(JsonPrimitive("spam.example"))),
                "startDate" to JsonPrimitive("2026-01-01"),
                "endDate" to JsonPrimitive("2026-01-31"),
            )),
        )

        assertFalse(result.isError)
        assertContains(requestBody, "\"search_depth\":\"advanced\"")
        assertContains(requestBody, "\"max_results\":12")
        assertContains(requestBody, "\"time_range\":\"week\"")
        assertContains(requestBody, "\"include_answer\":false")
        assertContains(requestBody, "\"include_raw_content\":\"markdown\"")
        assertContains(requestBody, "\"include_domains\":[\"example.com\"]")
        assertContains(requestBody, "\"exclude_domains\":[\"spam.example\"]")
        assertContains(requestBody, "\"start_date\":\"2026-01-01\"")
        assertContains(requestBody, "\"end_date\":\"2026-01-31\"")
    }

    @Test
    fun searchRejectsMissingApiKeyBeforeNetworkCall() = runTest {
        var called = false
        val engine = MockEngine {
            called = true
            respond("{}")
        }
        val executor = SharedWebToolExecutor({ AppSettings(tavilyApiKey = "") }, engine)

        val result = executor.execute(
            "web_search",
            JsonObject(mapOf("query" to JsonPrimitive("Aether"))),
        )

        assertTrue(result.isError)
        assertContains(result.outputJson, "Settings > Web Tools")
        assertContains(result.outputJson, "\"errmsg\":")
        assertFalse(result.outputJson.contains("\"error\":"))
        assertFalse(called)
    }

    @Test
    fun tavilyHttpFailureUsesMessageAndIncludesQuery() = runTest {
        val engine = MockEngine {
            respond(
                content = "{\"message\":\"Quota exhausted\"}",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val executor = SharedWebToolExecutor(
            settings = { AppSettings(tavilyApiKey = "secret") },
            engine = engine,
        )

        val result = executor.execute(
            "web_search",
            JsonObject(mapOf("query" to JsonPrimitive("Aether"))),
        )

        assertTrue(result.isError)
        assertContains(result.outputJson, "\"errmsg\":\"Quota exhausted\"")
        assertContains(result.outputJson, "\"query\":\"Aether\"")
    }

    @Test
    fun fetchFailureIncludesUrlAndUsesErrmsg() = runTest {
        val engine = MockEngine {
            respond("Unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
        val executor = SharedWebToolExecutor({ AppSettings() }, engine)

        val result = executor.execute(
            "fetch_web_url",
            JsonObject(mapOf("url" to JsonPrimitive("example.com/down"))),
        )

        assertTrue(result.isError)
        assertContains(result.outputJson, "\"url\":\"example.com/down\"")
        assertContains(result.outputJson, "\"errmsg\":")
        assertFalse(result.outputJson.contains("\"error\":"))
    }

    @Test
    fun cancellationIsNotConvertedIntoToolFailure() = runTest {
        val engine = MockEngine { throw CancellationException("cancelled") }
        val executor = SharedWebToolExecutor({ AppSettings() }, engine)

        val failure = assertFailsWith<CancellationException> {
            executor.execute(
                "fetch_web_url",
                JsonObject(mapOf("url" to JsonPrimitive("example.com"))),
            )
        }
        assertNotNull(failure.message)
    }
}
