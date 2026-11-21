package com.zhousl.aether.ui

import com.zhousl.aether.data.WorkspaceFileBridge
import com.zhousl.aether.termux.TermuxContract
import java.io.File
import java.net.URLDecoder

internal fun defaultMarkdownImageLayout(): MarkdownMediaLayout = MarkdownMediaLayout(
    minHeightDp = 1,
    maxHeightDp = DefaultImageMaxHeightDp,
    fit = MarkdownMediaFit.Contain,
)

internal fun defaultMarkdownMermaidLayout(): MarkdownMediaLayout = MarkdownMediaLayout(
    minHeightDp = DefaultMermaidMinHeightDp,
    maxHeightDp = DefaultMermaidMaxHeightDp,
    scroll = true,
)

internal fun buildMarkdownImageOriginalLinkTarget(
    rawUrl: String,
    workspaceFileBridge: WorkspaceFileBridge,
    workspaceDirectory: String?,
): String? {
    val normalizedUrl = normalizeMarkdownImageUrl(rawUrl) ?: return null
    return when {
        normalizedUrl.startsWith("http://", ignoreCase = true) ||
            normalizedUrl.startsWith("https://", ignoreCase = true) -> normalizedUrl

        normalizedUrl.startsWith("data:", ignoreCase = true) ||
            normalizedUrl.startsWith("content://", ignoreCase = true) -> null

        else -> {
            val workingDirectory = workspaceDirectory
                ?.trim()
                ?.ifBlank { TermuxContract.HomeDirectory }
                ?: TermuxContract.HomeDirectory
            val resolvedPath = if (normalizedUrl.startsWith("file://", ignoreCase = true)) {
                workspaceFileBridge.resolveLinkPath(normalizedUrl)
            } else {
                workspaceFileBridge.resolveTermuxPath(
                    path = normalizedUrl,
                    workingDirectory = workingDirectory,
                )
            }
            buildAssistantLocalFileLink(resolvedPath)
        }
    }
}

private val markdownImagePattern = Regex("^!\\[(.*?)]\\((.+)\\)(?:\\{(.*)\\})?$")
private val markdownCodeFenceHeaderPattern = Regex("^```\\s*([^\\s{]+)?\\s*(?:\\{(.*)\\})?\\s*$")
private val markdownAttributePattern =
    Regex("""([A-Za-z][A-Za-z0-9_-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\s]+)))?""")
internal fun parseMarkdownImage(text: String): MarkdownImageSpec? {
    val match = markdownImagePattern.matchEntire(text) ?: return null
    val normalizedUrl = normalizeMarkdownImageUrl(match.groupValues[2]) ?: return null
    val attributes = parseMarkdownAttributes(match.groupValues.getOrNull(3).orEmpty())
    return MarkdownImageSpec(
        altText = match.groupValues[1].trim(),
        url = normalizedUrl,
        layout = parseMarkdownMediaLayout(
            attributes = attributes,
            defaults = defaultMarkdownImageLayout(),
        ),
    )
}

internal fun parseMarkdownImageSequence(text: String): List<MarkdownImageSpec>? {
    val images = mutableListOf<MarkdownImageSpec>()
    var index = 0

    while (index < text.length) {
        while (index < text.length && text[index].isWhitespace()) index++
        if (index >= text.length) break

        val token = parseMarkdownImageToken(text, index) ?: return null
        images += token.image
        index = token.endExclusive
    }

    return images.takeIf(List<MarkdownImageSpec>::isNotEmpty)
}

private data class MarkdownImageToken(
    val image: MarkdownImageSpec,
    val endExclusive: Int,
)

private fun parseMarkdownImageToken(
    text: String,
    startIndex: Int,
): MarkdownImageToken? {
    if (text.startsWith("[![", startIndex)) {
        val inner = parseDirectMarkdownImageToken(text, startIndex + 1) ?: return null
        if (inner.endExclusive >= text.length || text[inner.endExclusive] != ']') return null
        val destinationStart = inner.endExclusive + 1
        if (destinationStart >= text.length || text[destinationStart] != '(') return null
        val outerEnd = findMarkdownDestinationEnd(text, destinationStart) ?: return null
        return MarkdownImageToken(inner.image, outerEnd + 1)
    }

    return parseDirectMarkdownImageToken(text, startIndex)
}

private fun parseDirectMarkdownImageToken(
    text: String,
    startIndex: Int,
): MarkdownImageToken? {
    if (!text.startsWith("![", startIndex)) return null
    val destinationMarker = text.indexOf("](", startIndex + 2)
    if (destinationMarker < 0) return null
    val destinationStart = destinationMarker + 1
    val destinationEnd = findMarkdownDestinationEnd(text, destinationStart) ?: return null

    var endExclusive = destinationEnd + 1
    if (endExclusive < text.length && text[endExclusive] == '{') {
        val attributesEnd = text.indexOf('}', endExclusive + 1)
        if (attributesEnd < 0) return null
        endExclusive = attributesEnd + 1
    }

    val image = parseMarkdownImage(text.substring(startIndex, endExclusive)) ?: return null
    return MarkdownImageToken(image, endExclusive)
}

private fun findMarkdownDestinationEnd(
    text: String,
    openingParenthesis: Int,
): Int? {
    if (openingParenthesis !in text.indices || text[openingParenthesis] != '(') return null

    var nestedParentheses = 0
    var index = openingParenthesis + 1
    while (index < text.length) {
        val character = text[index]
        if (character == '\\' && index + 1 < text.length) {
            index += 2
            continue
        }
        when (character) {
            '(' -> nestedParentheses++
            ')' -> {
                if (nestedParentheses == 0) return index
                nestedParentheses--
            }
        }
        index++
    }
    return null
}

internal fun parseMarkdownCodeFenceHeader(
    line: String,
): MarkdownCodeFenceHeader {
    val match = markdownCodeFenceHeaderPattern.matchEntire(line.trim())
    val language = match?.groupValues?.getOrNull(1).orEmpty().trim()
    val attributes = parseMarkdownAttributes(match?.groupValues?.getOrNull(2).orEmpty())
    return MarkdownCodeFenceHeader(language = language, attributes = attributes)
}

internal fun parseMarkdownAttributes(
    rawAttributes: String,
): Map<String, String> {
    val trimmed = rawAttributes.trim()
        .removePrefix("{")
        .removeSuffix("}")
        .trim()
    if (trimmed.isBlank()) return emptyMap()

    val attributes = linkedMapOf<String, String>()
    markdownAttributePattern.findAll(trimmed).forEach { match ->
        val key = match.groupValues[1].trim().lowercase()
        if (key.isBlank()) return@forEach
        val rawValue = listOf(
            match.groupValues[2],
            match.groupValues[3],
            match.groupValues[4],
        ).firstOrNull { it.isNotEmpty() } ?: "true"
        attributes[key] = rawValue.trim()
    }
    return attributes
}

internal fun parseMarkdownMediaLayout(
    attributes: Map<String, String>,
    defaults: MarkdownMediaLayout,
): MarkdownMediaLayout {
    if (attributes.isEmpty()) return defaults

    fun attributeValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { attributes[it.lowercase()]?.trim()?.takeIf(String::isNotBlank) }

    val hasExplicitMaxHeight = attributeValue("max-height", "max_height", "maxheight") != null
    val showAll = attributeValue("show-all", "show_all", "showall", "full")
        ?.let(::parseMarkdownBoolean)
        ?: defaults.showAll
    val scroll = attributeValue("scroll")
        ?.let(::parseMarkdownBoolean)
        ?: defaults.scroll

    return MarkdownMediaLayout(
        width = attributeValue("width", "w")?.let(::parseMarkdownMediaWidth) ?: defaults.width,
        heightDp = attributeValue("height", "h")?.let(::parseMarkdownDp) ?: defaults.heightDp,
        minHeightDp = attributeValue("min-height", "min_height", "minheight")
            ?.let(::parseMarkdownDp)
            ?: defaults.minHeightDp,
        maxHeightDp = when {
            showAll && !hasExplicitMaxHeight -> null
            else -> attributeValue("max-height", "max_height", "maxheight")
                ?.let(::parseMarkdownDp)
                ?: defaults.maxHeightDp
        },
        fit = attributeValue("fit")?.let(::parseMarkdownMediaFit) ?: defaults.fit,
        scroll = if (showAll) false else scroll,
        showAll = showAll,
    )
}

internal fun normalizeMarkdownImageUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null

    val withoutTitle = extractMarkdownLinkDestination(trimmed).orEmpty()
    val normalized = withoutTitle
        .removeSurrounding("<", ">")
        .replace("&amp;", "&")
        .trim()
    return normalized.ifBlank { null }
}

internal fun extractMarkdownLinkDestination(rawDestination: String): String? {
    val trimmed = rawDestination.trim()
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith("<")) {
        val endIndex = trimmed.indexOf('>')
        if (endIndex > 1) {
            return trimmed.substring(1, endIndex).trim().ifBlank { null }
        }
    }

    val destination = StringBuilder()
    var nestedParentheses = 0
    var index = 0

    while (index < trimmed.length) {
        val character = trimmed[index]
        if (character == '\\' && index + 1 < trimmed.length) {
            destination.append(trimmed[index + 1])
            index += 2
            continue
        }
        if (character.isWhitespace() && nestedParentheses == 0) {
            break
        }
        when (character) {
            '(' -> nestedParentheses++
            ')' -> if (nestedParentheses > 0) {
                nestedParentheses--
            }
        }
        destination.append(character)
        index++
    }

    return destination.toString().trim().ifBlank { null }
}

private fun parseMarkdownMediaWidth(
    rawValue: String,
): MarkdownMediaWidth? {
    val trimmed = rawValue.trim().lowercase()
    if (trimmed.isBlank()) return null
    return when {
        trimmed == "full" -> null
        trimmed.endsWith("%") -> {
            val fraction = trimmed.removeSuffix("%").toFloatOrNull()?.div(100f) ?: return null
            MarkdownMediaWidth.Fraction(fraction.coerceIn(0.1f, 1f))
        }

        else -> parseMarkdownDp(trimmed)?.let(MarkdownMediaWidth::DpValue)
    }
}

private fun parseMarkdownDp(
    rawValue: String,
): Int? {
    val normalized = rawValue.trim().lowercase()
        .removeSuffix("dp")
        .removeSuffix("px")
        .trim()
    return normalized.toIntOrNull()?.takeIf { it > 0 }?.let(::clampMarkdownMediaDp)
}

private fun parseMarkdownBoolean(
    rawValue: String,
): Boolean? = when (rawValue.trim().lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}

private fun parseMarkdownMediaFit(
    rawValue: String,
): MarkdownMediaFit = when (rawValue.trim().lowercase()) {
    "cover" -> MarkdownMediaFit.Cover
    else -> MarkdownMediaFit.Contain
}

