package com.zhousl.aether.data.pi

internal data class SharedConvertedWebContent(
    val title: String,
    val markdown: String,
)

internal fun convertSharedWebResponseToMarkdown(
    body: String,
    contentType: String,
    finalUrl: String,
): SharedConvertedWebContent {
    val trimmed = body.trim()
    if (!isSharedHtmlContent(contentType, trimmed)) {
        val markdown = if (isSharedStructuredText(contentType, trimmed)) {
            "```\n$trimmed\n```"
        } else {
            trimmed
        }
        return SharedConvertedWebContent(title = "", markdown = markdown)
    }

    val document = SharedHtmlParser(trimmed).parse()
    val title = document.descendants("title")
        .firstOrNull()
        ?.plainText()
        .orEmpty()
        .trim()
    val candidates = document.descendants().filter { node ->
        node.isContentCandidate() && node.plainText().length >= 120
    }
    val root = candidates.maxByOrNull { it.plainText().length }
        ?: document.descendants("body").firstOrNull()
        ?: document
    val bodyMarkdown = SharedMarkdownWriter(finalUrl).render(root)
    val markdown = when {
        title.isBlank() -> bodyMarkdown
        bodyMarkdown.startsWith("# ") -> bodyMarkdown
        else -> "# $title\n\n$bodyMarkdown".trim()
    }
    return SharedConvertedWebContent(title = title, markdown = markdown)
}

internal fun normalizeSharedMarkdown(markdown: String): String = markdown
    .replace("\r\n", "\n")
    .replace('\u00A0', ' ')
    .replace(Regex("[ \\t]+\n"), "\n")
    .replace(Regex("\n{4,}"), "\n\n\n")
    .trim()

internal fun truncateSharedMarkdown(markdown: String, maxChars: Int): String {
    if (markdown.length <= maxChars) return markdown
    val candidate = markdown.substring(0, maxChars)
    val lastBreak = candidate.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
    val cutoff = if (lastBreak >= maxChars / 2) lastBreak else maxChars
    return candidate.substring(0, cutoff).trimEnd() + "\n\n...[truncated]"
}

private fun isSharedHtmlContent(contentType: String, body: String): Boolean {
    val leading = body.trimStart()
    return contentType.contains("html", ignoreCase = true) ||
        leading.startsWith("<!DOCTYPE", ignoreCase = true) ||
        leading.startsWith("<html", ignoreCase = true) ||
        leading.startsWith("<body", ignoreCase = true)
}

private fun isSharedStructuredText(contentType: String, body: String): Boolean {
    val leading = body.trimStart()
    return contentType.contains("json", ignoreCase = true) ||
        contentType.contains("xml", ignoreCase = true) ||
        leading.startsWith("{") ||
        leading.startsWith("[") ||
        leading.startsWith("<?xml", ignoreCase = true)
}

private class SharedHtmlNode(
    val tag: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val text: String? = null,
) {
    val children = mutableListOf<SharedHtmlNode>()

    fun descendants(requiredTag: String? = null): List<SharedHtmlNode> = buildList {
        children.forEach { child ->
            if (requiredTag == null || child.tag == requiredTag) add(child)
            addAll(child.descendants(requiredTag))
        }
    }

    fun plainText(): String = if (text != null) {
        decodeSharedHtmlEntities(text)
    } else {
        children.joinToString(" ") { it.plainText() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isContentCandidate(): Boolean {
        if (tag == "article" || tag == "main") return true
        if (attributes["role"]?.equals("main", ignoreCase = true) == true) return true
        val id = attributes["id"].orEmpty().lowercase()
        val classes = attributes["class"].orEmpty().lowercase().split(Regex("\\s+")).toSet()
        return id in setOf("content", "main-content") ||
            classes.any { it in setOf("content", "main-content") }
    }
}

private class SharedHtmlParser(private val html: String) {
    fun parse(): SharedHtmlNode {
        val root = SharedHtmlNode(tag = "document")
        val stack = mutableListOf(root)
        var cursor = 0
        while (cursor < html.length) {
            val open = html.indexOf('<', cursor)
            if (open < 0) {
                addText(stack.last(), html.substring(cursor))
                break
            }
            if (open > cursor) addText(stack.last(), html.substring(cursor, open))
            if (html.startsWith("<!--", open)) {
                val close = html.indexOf("-->", open + 4)
                cursor = if (close < 0) html.length else close + 3
                continue
            }
            val close = findSharedTagEnd(open + 1)
            if (close < 0) {
                addText(stack.last(), html.substring(open))
                break
            }
            val token = html.substring(open + 1, close).trim()
            cursor = close + 1
            if (token.isBlank() || token.startsWith("!") || token.startsWith("?")) continue
            if (token.startsWith("/")) {
                val tag = token.drop(1).takeWhile { !it.isWhitespace() }.lowercase()
                val matchingIndex = stack.indexOfLast { it.tag == tag }
                if (matchingIndex > 0) {
                    while (stack.lastIndex >= matchingIndex) stack.removeAt(stack.lastIndex)
                }
                continue
            }

            val selfClosing = token.endsWith("/")
            val content = token.removeSuffix("/").trimEnd()
            val tag = content.takeWhile { !it.isWhitespace() }.lowercase()
            if (tag.isBlank()) continue
            val attributes = parseSharedHtmlAttributes(content.drop(tag.length))
            val node = SharedHtmlNode(tag = tag, attributes = attributes)
            stack.last().children += node
            if (!selfClosing && tag !in SharedVoidHtmlTags) stack += node
        }
        return root
    }

    private fun findSharedTagEnd(start: Int): Int {
        var quote: Char? = null
        var index = start
        while (index < html.length) {
            val char = html[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '\'' || char == '"') {
                quote = char
            } else if (char == '>') {
                return index
            }
            index += 1
        }
        return -1
    }

    private fun addText(parent: SharedHtmlNode, value: String) {
        if (value.isNotEmpty()) parent.children += SharedHtmlNode(text = value)
    }
}

private class SharedMarkdownWriter(private val baseUrl: String) {
    fun render(root: SharedHtmlNode): String = normalizeSharedMarkdown(renderNode(root).trim())

    private fun renderNode(node: SharedHtmlNode): String {
        node.text?.let { return normalizeInlineText(decodeSharedHtmlEntities(it)) }
        if (node.isExcluded()) return ""
        val childContent = node.children.joinToString(separator = "") { renderNode(it) }
        return when (node.tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = node.tag.drop(1).toIntOrNull() ?: 1
                "\n\n${"#".repeat(level)} ${childContent.trim()}\n\n"
            }
            "p", "div", "section", "article", "main", "header", "table", "thead", "tbody", "tfoot", "tr" ->
                "\n\n${childContent.trim()}\n\n"
            "br" -> "\n"
            "hr" -> "\n\n---\n\n"
            "li" -> "\n- ${childContent.trim()}"
            "ul", "ol", "dl" -> "\n${childContent.trim()}\n"
            "dt" -> "\n**${childContent.trim()}**\n"
            "dd" -> "\n${childContent.trim()}\n"
            "blockquote" -> childContent.trim().lines().joinToString("\n", prefix = "\n\n", postfix = "\n\n") {
                "> $it"
            }
            "pre" -> "\n\n```\n${node.plainText().trim()}\n```\n\n"
            "code" -> "`${childContent.trim()}`"
            "strong", "b" -> "**${childContent.trim()}**"
            "em", "i" -> "*${childContent.trim()}*"
            "a" -> renderLink(node, childContent)
            "img" -> renderImage(node)
            "th", "td" -> " ${childContent.trim()} |"
            else -> childContent
        }
    }

    private fun renderLink(node: SharedHtmlNode, content: String): String {
        val label = content.trim()
        val href = node.attributes["href"].orEmpty().trim()
        if (href.isBlank()) return label
        return "[$label](${resolveSharedWebUrl(baseUrl, decodeSharedHtmlEntities(href))})"
    }

    private fun renderImage(node: SharedHtmlNode): String {
        val src = node.attributes["src"].orEmpty().trim()
        if (src.isBlank()) return ""
        val alt = decodeSharedHtmlEntities(node.attributes["alt"].orEmpty()).trim()
        return "![$alt](${resolveSharedWebUrl(baseUrl, decodeSharedHtmlEntities(src))})"
    }

    private fun SharedHtmlNode.isExcluded(): Boolean {
        if (tag in SharedExcludedHtmlTags) return true
        if (attributes["aria-hidden"]?.equals("true", ignoreCase = true) == true) return true
        val classes = attributes["class"].orEmpty().lowercase().split(Regex("\\s+")).toSet()
        return classes.any { it in SharedExcludedHtmlClasses }
    }
}

private fun normalizeInlineText(value: String): String = value
    .replace(Regex("\\s+"), " ")

private fun parseSharedHtmlAttributes(source: String): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    var index = 0
    while (index < source.length) {
        while (index < source.length && source[index].isWhitespace()) index += 1
        if (index >= source.length) break
        val nameStart = index
        while (index < source.length && !source[index].isWhitespace() && source[index] != '=') index += 1
        val name = source.substring(nameStart, index).lowercase()
        while (index < source.length && source[index].isWhitespace()) index += 1
        if (index >= source.length || source[index] != '=') {
            if (name.isNotBlank()) attributes[name] = ""
            continue
        }
        index += 1
        while (index < source.length && source[index].isWhitespace()) index += 1
        val value = if (index < source.length && (source[index] == '\'' || source[index] == '"')) {
            val quote = source[index++]
            val valueStart = index
            while (index < source.length && source[index] != quote) index += 1
            source.substring(valueStart, index).also { if (index < source.length) index += 1 }
        } else {
            val valueStart = index
            while (index < source.length && !source[index].isWhitespace()) index += 1
            source.substring(valueStart, index)
        }
        if (name.isNotBlank()) attributes[name] = value
    }
    return attributes
}

private fun decodeSharedHtmlEntities(value: String): String = SharedHtmlEntityRegex.replace(value) { match ->
    val entity = match.groupValues[1]
    when {
        entity.startsWith("#x", ignoreCase = true) ->
            entity.drop(2).toIntOrNull(16)?.takeIf { it in 0..0xffff }?.toChar()?.toString()
        entity.startsWith("#") ->
            entity.drop(1).toIntOrNull()?.takeIf { it in 0..0xffff }?.toChar()?.toString()
        else -> SharedNamedHtmlEntities[entity.lowercase()]
    } ?: match.value
}

private fun resolveSharedWebUrl(baseUrl: String, value: String): String {
    val url = value.trim()
    if (url.isBlank() || url.startsWith("#") || url.startsWith("data:", true) ||
        url.startsWith("mailto:", true) || url.startsWith("tel:", true)
    ) return url
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(url)) return url

    val base = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*:)?//([^/]+)(/[^?#]*)?").find(baseUrl)
        ?: return url
    val scheme = base.groupValues[1].ifBlank { "https:" }
    val authority = base.groupValues[2]
    if (url.startsWith("//")) return "$scheme$url"
    val root = "$scheme//$authority"
    if (url.startsWith("/")) return root + normalizeSharedUrlPath(url)
    if (url.startsWith("?")) return baseUrl.substringBefore('?').substringBefore('#') + url
    val basePath = base.groupValues.getOrNull(3).orEmpty().ifBlank { "/" }
    val directory = basePath.substringBeforeLast('/', "") + "/"
    return root + normalizeSharedUrlPath(directory + url)
}

private fun normalizeSharedUrlPath(value: String): String {
    val suffixIndex = value.indexOfAny(charArrayOf('?', '#'))
    val path = if (suffixIndex < 0) value else value.substring(0, suffixIndex)
    val suffix = if (suffixIndex < 0) "" else value.substring(suffixIndex)
    val segments = mutableListOf<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    return "/${segments.joinToString("/")}$suffix"
}

private val SharedVoidHtmlTags = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
)
private val SharedExcludedHtmlTags = setOf(
    "script", "style", "noscript", "svg", "canvas", "iframe", "form", "input", "button", "nav", "footer", "aside",
)
private val SharedExcludedHtmlClasses = setOf(
    "sidebar", "breadcrumbs", "advertisement", "ads", "social-share",
)
private val SharedHtmlEntityRegex = Regex("&([#a-zA-Z0-9]+);")
private val SharedNamedHtmlEntities = mapOf(
    "nbsp" to " ",
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
)
