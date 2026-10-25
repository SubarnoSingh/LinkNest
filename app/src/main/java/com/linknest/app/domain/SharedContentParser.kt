package com.linknest.app.domain

data class SharedContent(
    val url: String?,
    val rawText: String,
    val suggestedTitle: String,
    val note: String,
    val additionalUrlCount: Int
)

object SharedContentParser {
    private val urlRegex = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val sentencePunctuation = setOf('.', ',', ';', ':', '!', '?')

    private data class Candidate(
        val raw: String,
        val normalized: NormalizedUrl
    )

    fun parse(text: String?, viewUrl: String? = null): SharedContent? {
        val raw = text.orEmpty().trim()
        val candidates = buildList {
            if (!viewUrl.isNullOrBlank()) add(viewUrl.trim())
            addAll(urlRegex.findAll(raw).map { it.value })
        }
        val normalized = candidates.mapNotNull { rawCandidate ->
            val cleaned = cleanCandidate(rawCandidate)
            UrlUtils.normalize(cleaned)?.let { Candidate(raw = rawCandidate, normalized = it) }
        }.distinctBy { it.normalized.canonical }
        val primary = normalized.firstOrNull()
        if (primary == null && raw.isBlank()) return null

        val titleContext = candidates.fold(raw) { text, candidate -> text.replace(candidate, "") }
        val suggestedTitle = primary?.let { cleanWhitespace(titleContext).take(120) }
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        return SharedContent(
            url = primary?.normalized?.original,
            rawText = raw,
            suggestedTitle = suggestedTitle,
            note = if (primary != null) UrlUtils.stripUrlFromText(raw, primary.raw) else raw,
            additionalUrlCount = (normalized.size - 1).coerceAtLeast(0)
        )
    }

    private fun cleanCandidate(candidate: String): String {
        var cleaned = candidate.trim()
        while (cleaned.isNotEmpty() && cleaned.last() in setOf(')', ']', '}')) {
            val closing = cleaned.last()
            val opening = when (closing) {
                ')' -> '('
                ']' -> '['
                else -> '{'
            }
            if (cleaned.count { it == closing } <= cleaned.count { it == opening }) break
            cleaned = cleaned.dropLast(1)
        }
        val hasQueryOrFragment = cleaned.contains('?') || cleaned.contains('#')
        if (!hasQueryOrFragment) {
            while (cleaned.isNotEmpty() && cleaned.last() in sentencePunctuation) {
                cleaned = cleaned.dropLast(1)
            }
        }
        return cleaned
    }

    private fun cleanWhitespace(value: String): String = value
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
