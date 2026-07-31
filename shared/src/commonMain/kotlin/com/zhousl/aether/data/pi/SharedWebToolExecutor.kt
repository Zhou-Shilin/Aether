package com.zhousl.aether.data.pi

import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.normalizeTavilyBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val DefaultFetchLimit = 20_000
private const val MinimumFetchLimit = 500
private const val MaximumFetchLimit = 100_000
private const val DefaultWebUserAgent =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
private const val DefaultWebAccept =
    "text/html,application/xhtml+xml,text/markdown,text/plain,application/xml;q=0.9,*/*;q=0.8"

class SharedWebToolExecutor(
    private val settings: () -> AppSettings,
    engine: HttpClientEngine? = null,
) : SharedHostToolExecutor {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = if (engine == null) HttpClient {
        install(ContentNegotiation) { json(json) }
    } else HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    override val definitions: JsonArray = buildJsonArray {
        add(webToolDefinition(
            name = "fetch_web_url",
            description = "Fetch a specific HTTP or HTTPS URL and return the page content converted to Markdown. Use this when the user gives you a URL or you need the contents of one page.",
            required = listOf("url"),
            properties = mapOf(
                "url" to webStringSchema("The HTTP or HTTPS URL to fetch."),
                "max_chars" to webIntegerSchema("Optional maximum number of Markdown characters to return."),
                "maxChars" to webIntegerSchema("Alias of max_chars."),
            ),
        ))
        add(webToolDefinition(
            name = "tavily_search",
            description = "Search the public web with Tavily. Requires a Tavily API key in Settings > Web Tools. Use this for web discovery or current online information.",
            required = listOf("query"),
            properties = mapOf(
                "query" to webStringSchema("The search query to execute."),
                "topic" to webStringSchema("Optional search topic: general, news, or finance."),
                "search_depth" to webStringSchema("Optional search depth: basic, advanced, fast, or ultra-fast."),
                "max_results" to webIntegerSchema("Optional maximum number of results to return, between 1 and 20."),
                "time_range" to webStringSchema("Optional recency filter, such as day, week, month, or year. Do not combine this with start_date or end_date."),
                "include_answer" to webBooleanSchema("Whether Tavily should include a synthesized answer."),
                "include_raw_content" to webBooleanSchema("Whether each result should include raw page content in Markdown."),
                "include_domains" to webStringArraySchema("Optional list of domains to include."),
                "exclude_domains" to webStringArraySchema("Optional list of domains to exclude."),
                "country" to webStringSchema("Optional lowercase Tavily country value for localized general search, such as united states or china. Leave null when unsure."),
                "start_date" to webStringSchema("Optional start date in YYYY-MM-DD format. Do not combine this with time_range."),
                "end_date" to webStringSchema("Optional end date in YYYY-MM-DD format. Do not combine this with time_range."),
            ),
        ))
    }

    override suspend fun execute(name: String, arguments: JsonObject): SharedHostToolResult = try {
        when (name) {
            "fetch_web_url", "web_fetch" -> fetch(arguments)
            "tavily_search", "web_search" -> search(arguments)
            else -> error("Unsupported web tool: $name")
        }
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (failure: Throwable) {
        webToolFailure(failure, "Web tool failed.")
    }

    private suspend fun fetch(arguments: JsonObject): SharedHostToolResult {
        val rawUrl = arguments.string("url").trim()
        if (rawUrl.isBlank()) {
            return webToolFailure("Missing required 'url' argument.")
        }
        return try {
            val url = normalizedHttpUrl(rawUrl)
            val limit = arguments.intValue("max_chars", "maxChars")
                .let { it ?: DefaultFetchLimit }
                .coerceIn(MinimumFetchLimit, MaximumFetchLimit)
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, DefaultWebUserAgent)
                header(HttpHeaders.Accept, DefaultWebAccept)
            }
            val body = response.body<String>()
            check(response.status.isSuccess()) { "HTTP ${response.status.value} while fetching $url." }
            val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
            val finalUrl = response.call.request.url.toString()
            val converted = convertSharedWebResponseToMarkdown(body, contentType, finalUrl)
            val normalized = normalizeSharedMarkdown(converted.markdown)
            val output = truncateSharedMarkdown(normalized, limit)
            val truncated = output.length < normalized.length
            SharedHostToolResult(buildJsonObject {
                put("ok", true)
                put("request_url", url)
                put("final_url", finalUrl)
                put("title", converted.title)
                put("content_type", contentType)
                put("markdown", output)
                put("truncated", truncated)
                put(
                    "stdout",
                    "Fetched ${converted.title.ifBlank { finalUrl }}" +
                        if (truncated) " (truncated)" else "",
                )
            }.toString())
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (failure: Throwable) {
            webToolFailure(failure, "Couldn't fetch the URL.", "url" to rawUrl)
        }
    }

    private suspend fun search(arguments: JsonObject): SharedHostToolResult {
        val current = settings()
        val apiKey = current.tavilyApiKey.trim()
        if (apiKey.isBlank()) {
            return webToolFailure(
                "Tavily API key is not configured. Add it in Settings > Web Tools before using tavily_search.",
            )
        }
        val query = arguments.string("query").trim()
        if (query.isBlank()) {
            return webToolFailure("Missing required 'query' argument.")
        }
        return try {
            val endpoint = tavilySearchEndpoint(current.tavilyBaseUrl)
            val response = client.post(endpoint) {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("query", query)
                    put("topic", arguments.stringValue("topic").ifBlank { "general" })
                    put("search_depth", arguments.stringValue("search_depth", "searchDepth").ifBlank { "basic" })
                    put("max_results", (arguments.intValue("max_results", "maxResults") ?: 5).coerceIn(1, 20))
                    put(
                        "include_answer",
                        if (arguments.booleanValue("include_answer", "includeAnswer") ?: true) {
                            JsonPrimitive("basic")
                        } else {
                            JsonPrimitive(false)
                        },
                    )
                    put(
                        "include_raw_content",
                        if (arguments.booleanValue("include_raw_content", "includeRawContent") ?: false) {
                            JsonPrimitive("markdown")
                        } else {
                            JsonPrimitive(false)
                        },
                    )
                    put("include_favicon", true)
                    put("include_usage", true)
                    arguments.stringValue("time_range", "timeRange").takeIf(String::isNotBlank)
                        ?.let { put("time_range", it) }
                    arguments.stringValue("country").takeIf(String::isNotBlank)
                        ?.let { put("country", it) }
                    arguments.stringValue("start_date", "startDate").takeIf(String::isNotBlank)
                        ?.let { put("start_date", it) }
                    arguments.stringValue("end_date", "endDate").takeIf(String::isNotBlank)
                        ?.let { put("end_date", it) }
                    arguments.stringArrayValue("include_domains", "includeDomains")
                        .takeIf(List<String>::isNotEmpty)
                        ?.let { domains ->
                            put("include_domains", buildJsonArray {
                                domains.forEach { add(JsonPrimitive(it)) }
                            })
                        }
                    arguments.stringArrayValue("exclude_domains", "excludeDomains")
                        .takeIf(List<String>::isNotEmpty)
                        ?.let { domains ->
                            put("exclude_domains", buildJsonArray {
                                domains.forEach { add(JsonPrimitive(it)) }
                            })
                        }
                })
            }
            val body = response.body<String>()
            val payload = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            if (!response.status.isSuccess()) {
                val message = payload?.string("detail").orEmpty()
                    .ifBlank { payload?.string("message").orEmpty() }
                    .ifBlank { "HTTP ${response.status.value} from Tavily." }
                error(message)
            }
            val result = payload ?: error("Tavily returned non-JSON content.")
            SharedHostToolResult(JsonObject(result + mapOf(
                "ok" to JsonPrimitive(true),
                "stdout" to JsonPrimitive(buildTavilySearchSummary(result)),
            )).toString())
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (failure: Throwable) {
            webToolFailure(failure, "Tavily search failed.", "query" to query)
        }
    }
}

private fun webToolDefinition(
    name: String,
    description: String,
    required: List<String>,
    properties: Map<String, JsonObject>,
): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("parameters", buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        put("additionalProperties", false)
    })
    put("execution_mode", "parallel")
}

private fun webStringSchema(description: String): JsonObject = webPrimitiveSchema("string", description)
private fun webIntegerSchema(description: String): JsonObject = webPrimitiveSchema("integer", description)
private fun webBooleanSchema(description: String): JsonObject = webPrimitiveSchema("boolean", description)

private fun webPrimitiveSchema(type: String, description: String): JsonObject = buildJsonObject {
    put("type", type)
    put("description", description)
}

private fun webStringArraySchema(description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("items", buildJsonObject { put("type", "string") })
    put("description", description)
}

private fun webToolFailure(
    failure: Throwable,
    fallbackMessage: String,
    vararg context: Pair<String, String>,
): SharedHostToolResult {
    if (failure is CancellationException) throw failure
    return webToolFailure(failure.message ?: fallbackMessage, *context)
}

private fun webToolFailure(
    message: String,
    vararg context: Pair<String, String>,
): SharedHostToolResult = SharedHostToolResult(
    outputJson = buildJsonObject {
        put("ok", false)
        context.forEach { (key, value) -> put(key, value) }
        put("errmsg", message)
    }.toString(),
    isError = true,
)

private fun normalizedHttpUrl(value: String): String {
    val candidate = value.trim().let { if ("://" in it) it else "https://$it" }
    val url = Url(candidate)
    require(url.protocol.name == "http" || url.protocol.name == "https") {
        "URL must use HTTP or HTTPS."
    }
    require(url.host.isNotBlank()) { "URL must include a host." }
    return url.toString()
}

internal fun tavilySearchEndpoint(baseUrl: String): String {
    val normalized = normalizeTavilyBaseUrl(baseUrl).trimEnd('/')
    return if (normalized.endsWith("/search")) normalized else "$normalized/search"
}

private fun buildTavilySearchSummary(response: JsonObject): String = buildString {
    val answer = response.string("answer").trim()
    if (answer.isNotBlank()) append(answer)

    val results = response["results"] as? JsonArray ?: JsonArray(emptyList())
    if (results.isNotEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append("Top results:")
        results.take(5).forEachIndexed { index, element ->
            val result = element as? JsonObject ?: return@forEachIndexed
            val url = result.string("url").trim()
            append("\n")
            append(index + 1)
            append(". ")
            append(result.string("title").ifBlank { url })
            if (url.isNotBlank()) {
                append(" - ")
                append(url)
            }
            val snippet = result.string("content").trim()
            if (snippet.isNotBlank()) {
                append("\n")
                append(snippet.take(280))
            }
        }
    }
}

private fun JsonObject.string(name: String): String = get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.stringValue(primaryKey: String, aliasKey: String? = null): String {
    val primary = string(primaryKey).trim()
    if (primary.isNotBlank()) return primary
    return aliasKey?.let { string(it).trim() }.orEmpty()
}

private fun JsonObject.intValue(primaryKey: String, aliasKey: String? = null): Int? =
    get(primaryKey)?.jsonPrimitive?.intOrNull
        ?: aliasKey?.let { get(it)?.jsonPrimitive?.intOrNull }

private fun JsonObject.booleanValue(primaryKey: String, aliasKey: String? = null): Boolean? =
    get(primaryKey)?.jsonPrimitive?.booleanOrNull
        ?: aliasKey?.let { get(it)?.jsonPrimitive?.booleanOrNull }

private fun JsonObject.stringArrayValue(primaryKey: String, aliasKey: String? = null): List<String> {
    val value: JsonElement? = get(primaryKey) ?: aliasKey?.let(::get)
    return (value as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
}
