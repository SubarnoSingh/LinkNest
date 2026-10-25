package com.linknest.app.data.metadata

import com.linknest.app.domain.UrlUtils
import java.net.URI
import java.util.Locale

object HtmlMetadataParser {
    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_DESCRIPTION_LENGTH = 500
    private const val MAX_URL_LENGTH = 2_048

    private val metaTagRegex = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val linkTagRegex = Regex("<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val titleRegex = Regex("<title\\b[^>]*>(.*?)</title\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attributeRegex = Regex("([a-zA-Z_:][a-zA-Z0-9_.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

    fun parse(html: String, documentUrl: URI): PageMetadata {
        val meta = mutableMapOf<String, String>()
        metaTagRegex.findAll(html.take(512 * 1024)).forEach { match ->
            val attributes = attributes(match.value)
            val key = (attributes["property"] ?: attributes["name"])
                ?.let(::decodeHtml)
                ?.trim()
                ?.lowercase(Locale.US)
                ?: return@forEach
            val value = attributes["content"]?.let(::decodeHtml)?.let(::cleanText) ?: return@forEach
            if (value.isNotBlank() && key !in meta) meta[key] = value
        }

        val documentTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)
            ?.let(::decodeHtml)
            ?.replace(Regex("<[^>]+>"), " ")
            ?.let(::cleanText)

        val title = firstNonBlank(
            meta["og:title"],
            meta["twitter:title"],
            documentTitle,
            documentUrl.host?.trim('[', ']')
        )?.take(MAX_TITLE_LENGTH)

        val description = firstNonBlank(
            meta["og:description"],
            meta["twitter:description"],
            meta["description"]
        )?.take(MAX_DESCRIPTION_LENGTH)

        var canonicalUrl: String? = null
        var faviconUrl: String? = null
        linkTagRegex.findAll(html.take(512 * 1024)).forEach { match ->
            val attributes = attributes(match.value)
            val rel = attributes["rel"]?.let(::decodeHtml)?.lowercase(Locale.US).orEmpty()
            val relTokens = rel.split(Regex("\\s+")).filter { it.isNotBlank() }
            val href = attributes["href"]?.let(::decodeHtml)?.trim().orEmpty()
            if (href.isBlank()) return@forEach
            if (canonicalUrl == null && relTokens.contains("canonical")) {
                canonicalUrl = resolvePublicWebUrl(documentUrl, href)
            }
            if (faviconUrl == null && relTokens.any {
                    it == "icon" ||
                        it == "shortcut" ||
                        it == "apple-touch-icon" ||
                        it == "apple-touch-icon-precomposed" ||
                        it == "mask-icon" ||
                        it == "fluid-icon"
                }
            ) {
                faviconUrl = resolvePublicWebUrl(documentUrl, href)
            }
        }

        return PageMetadata(
            title = title,
            description = description,
            canonicalUrl = canonicalUrl,
            faviconUrl = faviconUrl
        )
    }

    private fun attributes(tag: String): Map<String, String> = buildMap {
        attributeRegex.findAll(tag).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.US)
            val value = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
            if (key !in this) put(key, value)
        }
    }

    private fun resolvePublicWebUrl(base: URI, value: String): String? {
        if (value.length > MAX_URL_LENGTH) return null
        val resolved = runCatching { base.resolve(value) }.getOrNull() ?: return null
        return UrlUtils.normalize(resolved.toString())?.canonical
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun cleanText(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeHtml(value: String): String {
        val named = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " "
        )
        return Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]+);?").replace(value) { match ->
            val token = match.groupValues[1]
            when {
                token.startsWith("#x", ignoreCase = true) -> token.substring(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
                token.startsWith("#") -> token.substring(1).toIntOrNull()?.let(::codePoint) ?: match.value
                else -> named[token.lowercase(Locale.US)] ?: match.value
            }
        }
    }

    private fun codePoint(value: Int): String = runCatching { String(Character.toChars(value)) }.getOrDefault("")
}
