package com.linknest.app.domain

import java.net.URI
import java.util.Locale

data class NormalizedUrl(
    val canonical: String,
    val original: String,
    val domain: String
)

object UrlUtils {
    fun normalize(input: String): NormalizedUrl? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = parsed.host?.lowercase(Locale.US)?.removeSuffix(".") ?: return null
        if (host.isBlank() || parsed.userInfo != null) return null

        val port = when {
            parsed.port == -1 -> ""
            scheme == "http" && parsed.port == 80 -> ""
            scheme == "https" && parsed.port == 443 -> ""
            else -> ":${parsed.port}"
        }
        val path = parsed.rawPath.orEmpty().ifBlank { "/" }
        val query = parsed.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = parsed.rawFragment?.let { "#$it" }.orEmpty()
        val canonical = "$scheme://$host$port$path$query$fragment"
        return NormalizedUrl(canonical = canonical, original = candidate, domain = host)
    }

    fun domainOf(url: String): String = normalize(url)?.domain.orEmpty()

    fun resourceTypeFor(url: String): ResourceType {
        val host = domainOf(url)
        return when {
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" -> ResourceType.VIDEO
            host == "github.com" || host.endsWith(".github.com") ||
                host == "gitlab.com" || host.endsWith(".gitlab.com") -> ResourceType.REPOSITORY
            else -> ResourceType.ARTICLE
        }
    }

    fun stripUrlFromText(text: String, url: String): String = text
        .replace(url, "", ignoreCase = false)
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
