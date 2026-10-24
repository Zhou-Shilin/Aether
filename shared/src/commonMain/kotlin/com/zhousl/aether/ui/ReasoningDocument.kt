package com.zhousl.aether.ui

data class ReasoningRawSection(val id: String, val text: String)
data class ReasoningRawRange(val start: Int, val end: Int)
data class ReasoningRawDocument(val text: String, val ranges: Map<String, ReasoningRawRange>)

/** Match in stream order so repeated excerpts still navigate to their own occurrence. */
fun reasoningRawDocument(rawText: String, sections: List<ReasoningRawSection>): ReasoningRawDocument {
    val text = rawText.ifBlank { sections.joinToString("\n\n") { it.text } }
    var cursor = 0
    val ranges = buildMap {
        sections.forEach { section ->
            val excerpt = section.text.trim()
            if (excerpt.isNotEmpty()) {
                val start = text.indexOf(excerpt, startIndex = cursor)
                if (start >= 0) {
                    val end = start + excerpt.length
                    put(section.id, ReasoningRawRange(start, end))
                    cursor = end
                }
            }
        }
    }
    return ReasoningRawDocument(text, ranges)
}
