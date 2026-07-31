package com.zhousl.aether.data

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val DefaultFetchMarkdownChars = 20_000
private const val MinFetchMarkdownChars = 500
private const val MaxFetchMarkdownChars = 100_000
private const val DefaultUserAgent =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
data class FetchedWebPage(
    val requestUrl: String,
    val finalUrl: String,
    val title: String,
    val contentType: String,
    val markdown: String,
    val wasTruncated: Boolean,
)

data class TavilySearchRequest(
    val query: String,
    val topic: String = "general",
    val searchDepth: String = "basic",
    val maxResults: Int = 5,
    val timeRange: String? = null,
    val includeAnswer: Boolean = true,
    val includeRawContent: Boolean = false,
    val includeFavicon: Boolean = true,
    val includeDomains: List<String> = emptyList(),
    val excludeDomains: List<String> = emptyList(),
    val country: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

class WebToolsClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val tavilyBaseUrl: String = DefaultTavilyBaseUrl,
) {
    private val htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build()

    suspend fun fetchUrlAsMarkdown(
        url: String,
        maxChars: Int = DefaultFetchMarkdownChars,
    ): Result<FetchedWebPage> = runCatching {
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeUrl(url)
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", DefaultUserAgent)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,text/markdown,text/plain,application/xml;q=0.9,*/*;q=0.8",
                )
                .build()

            httpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val finalUrl = response.request.url.toString()
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} while fetching $normalizedUrl.")
                }

                val markdown = convertResponseToMarkdown(
                    bodyString = bodyString,
                    contentType = contentType,
                    finalUrl = finalUrl,
                )
                val normalizedMarkdown = normalizeMarkdown(markdown)
                val boundedMaxChars = maxChars.coerceIn(MinFetchMarkdownChars, MaxFetchMarkdownChars)
                val truncatedMarkdown = normalizedMarkdown.truncateAtWordBoundary(boundedMaxChars)

                FetchedWebPage(
                    requestUrl = normalizedUrl,
                    finalUrl = finalUrl,
                    title = extractTitle(bodyString, contentType),
                    contentType = contentType,
                    markdown = truncatedMarkdown,
                    wasTruncated = truncatedMarkdown.length < normalizedMarkdown.length,
                )
            }
        }
    }

    suspend fun searchTavily(
        apiKey: String,
        request: TavilySearchRequest,
        baseUrl: String = tavilyBaseUrl,
    ): Result<JSONObject> = runCatching {
        withContext(Dispatchers.IO) {
            val trimmedApiKey = apiKey.trim()
            if (trimmedApiKey.isBlank()) {
                error("Tavily API key is not configured.")
            }

            val payload = JSONObject().apply {
                put("query", request.query.trim())
                put("topic", request.topic)
                put("search_depth", request.searchDepth)
                put("max_results", request.maxResults.coerceIn(1, 20))
                put("include_answer", if (request.includeAnswer) "basic" else false)
                put("include_raw_content", if (request.includeRawContent) "markdown" else false)
                put("include_favicon", request.includeFavicon)
                put("include_usage", true)
                request.timeRange?.takeIf { it.isNotBlank() }?.let { put("time_range", it) }
                request.country?.takeIf { it.isNotBlank() }?.let { put("country", it) }
                request.startDate?.takeIf { it.isNotBlank() }?.let { put("start_date", it) }
                request.endDate?.takeIf { it.isNotBlank() }?.let { put("end_date", it) }
                if (request.includeDomains.isNotEmpty()) {
                    put(
                        "include_domains",
                        JSONArray().apply { request.includeDomains.forEach(::put) },
                    )
                }
                if (request.excludeDomains.isNotEmpty()) {
                    put(
                        "exclude_domains",
                        JSONArray().apply { request.excludeDomains.forEach(::put) },
                    )
                }
            }

            val searchEndpoint = buildEndpoint(baseUrl, "search")
            val httpRequest = Request.Builder()
                .url(searchEndpoint)
                .header("Authorization", "Bearer $trimmedApiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JsonMediaType))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val json = bodyString.toJsonObjectOrNull()
                if (!response.isSuccessful) {
                    val detail = json?.optString("detail").orEmpty()
                        .ifBlank { json?.optString("message").orEmpty() }
                        .ifBlank { "HTTP ${response.code} from Tavily." }
                    error(detail)
                }
                json ?: error("Tavily returned non-JSON content.")
            }
        }
    }

    private fun buildEndpoint(
        baseUrl: String,
        pathSegment: String,
    ): String {
        val parsedBaseUrl = baseUrl.trim().toHttpUrlOrNull()
            ?: error("Tavily base URL is invalid.")
        val pathSegments = parsedBaseUrl.pathSegments.filter { it.isNotBlank() }
        val builder = parsedBaseUrl.newBuilder()
        if (pathSegments.lastOrNull() == pathSegment) {
            return builder.build().toString()
        }
        return builder
            .addPathSegments(pathSegment)
            .build()
            .toString()
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) error("URL is required.")
        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val httpUrl = candidate.toHttpUrlOrNull()
            ?: error("URL must be an absolute HTTP or HTTPS URL.")
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            error("URL must use HTTP or HTTPS.")
        }
        return httpUrl.toString()
    }

    private fun convertResponseToMarkdown(
        bodyString: String,
        contentType: String,
        finalUrl: String,
    ): String {
        val trimmedBody = bodyString.trim()
        return when {
            isHtmlContent(contentType, trimmedBody) -> convertHtmlToMarkdown(trimmedBody, finalUrl)
            isStructuredTextContent(contentType, trimmedBody) -> {
                buildString {
                    append("```")
                    append('\n')
                    append(trimmedBody)
                    append('\n')
                    append("```")
                }
            }
            else -> trimmedBody
        }
    }

    private fun convertHtmlToMarkdown(
        html: String,
        finalUrl: String,
    ): String {
        val document = Jsoup.parse(html, finalUrl).apply {
            outputSettings().prettyPrint(false)
        }
        absolutizeDocumentUrls(document)

        val selectedRoot = selectContentRoot(document)
        val cleanedRoot = selectedRoot.clone().apply {
            select(
                "script,style,noscript,svg,canvas,iframe,form,input,button,nav,footer,aside," +
                    ".sidebar,.breadcrumbs,.advertisement,.ads,.social-share,[aria-hidden=true]",
            ).remove()
        }

        val title = document.title().trim()
        val markdownBody = htmlToMarkdownConverter.convert(cleanedRoot.outerHtml()).trim()
        if (title.isBlank()) {
            return markdownBody
        }
        if (markdownBody.startsWith("# ")) {
            return markdownBody
        }
        return "# $title\n\n$markdownBody".trim()
    }

    private fun selectContentRoot(document: Document): Element {
        val candidates = buildList {
            addAll(document.select("main article"))
            addAll(document.select("article"))
            addAll(document.select("main"))
            addAll(document.select("[role=main]"))
            addAll(document.select("#content"))
            addAll(document.select(".content"))
            addAll(document.select("#main-content"))
            addAll(document.select(".main-content"))
        }.filter { it.text().length >= 120 }

        return candidates.maxByOrNull { it.text().length } ?: document.body()
    }

    private fun absolutizeDocumentUrls(document: Document) {
        document.select("a[href]").forEach { anchor ->
            val absoluteUrl = anchor.absUrl("href")
            if (absoluteUrl.isNotBlank()) {
                anchor.attr("href", absoluteUrl)
            }
        }
        document.select("img[src]").forEach { image ->
            val absoluteUrl = image.absUrl("src")
            if (absoluteUrl.isNotBlank()) {
                image.attr("src", absoluteUrl)
            }
        }
    }

    private fun extractTitle(
        bodyString: String,
        contentType: String,
    ): String = when {
        isHtmlContent(contentType, bodyString) -> Jsoup.parse(bodyString).title().trim()
        else -> ""
    }

    private fun isHtmlContent(
        contentType: String,
        bodyString: String,
    ): Boolean {
        val trimmedBody = bodyString.trimStart()
        return contentType.contains("html", ignoreCase = true) ||
            trimmedBody.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmedBody.startsWith("<html", ignoreCase = true) ||
            trimmedBody.startsWith("<body", ignoreCase = true)
    }

    private fun isStructuredTextContent(
        contentType: String,
        bodyString: String,
    ): Boolean {
        val trimmedBody = bodyString.trimStart()
        return contentType.contains("json", ignoreCase = true) ||
            contentType.contains("xml", ignoreCase = true) ||
            trimmedBody.startsWith("{") ||
            trimmedBody.startsWith("[") ||
            trimmedBody.startsWith("<?xml", ignoreCase = true)
    }

    private fun normalizeMarkdown(markdown: String): String =
        markdown
            .replace("\r\n", "\n")
            .replace('\u00A0', ' ')
            .replace(Regex("\n{4,}"), "\n\n\n")
            .trim()

    private fun String.truncateAtWordBoundary(maxChars: Int): String {
        if (length <= maxChars) return this
        val candidate = substring(0, maxChars)
        val lastBreak = candidate.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
        val safeCutoff = if (lastBreak >= maxChars / 2) lastBreak else maxChars
        return candidate.substring(0, safeCutoff).trimEnd() + "\n\n...[truncated]"
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching {
        JSONObject(this)
    }.getOrNull()

    private companion object {
        val JsonMediaType = "application/json".toMediaType()
    }
}
