package com.linknest.app.data.metadata

import com.linknest.app.domain.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.charset.Charset

class DefaultPageMetadataFetcher(
    private val transport: MetadataHttpTransport = UrlConnectionMetadataTransport(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxRedirects: Int = 3,
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 5_000,
    private val maxResponseBytes: Int = 512 * 1024,
    private val securityPolicy: MetadataSecurityPolicy = MetadataSecurityPolicy()
) : PageMetadataFetcher {
    override suspend fun fetch(url: String): Result<PageMetadata> = withContext(ioDispatcher) {
        try {
            val normalized = UrlUtils.normalize(url)
                ?: return@withContext Result.failure(MetadataFetchException("The URL is invalid."))
            var current = URI(normalized.canonical)
            repeat(maxRedirects + 1) { redirectIndex ->
                securityPolicy.validate(current).getOrElse { return@withContext Result.failure(it) }
                val response = transport.get(current, connectTimeoutMs, readTimeoutMs, maxResponseBytes)
                if (response.statusCode in 300..399) {
                    if (redirectIndex == maxRedirects) {
                        return@withContext Result.failure(MetadataFetchException("Too many redirects."))
                    }
                    val location = response.header("Location")
                        ?: return@withContext Result.failure(MetadataFetchException("The webpage redirect was missing a destination."))
                    current = runCatching { current.resolve(location) }.getOrElse {
                        return@withContext Result.failure(MetadataFetchException("The webpage redirect was invalid."))
                    }
                    return@repeat
                }
                if (response.statusCode !in 200..299) {
                    return@withContext Result.failure(MetadataFetchException("The webpage returned HTTP ${response.statusCode}."))
                }
                if (response.body.size > maxResponseBytes) {
                    return@withContext Result.failure(MetadataFetchException("The webpage response is too large."))
                }
                val contentType = response.header("Content-Type")
                if (contentType != null && !contentType.contains("text/html", true) && !contentType.contains("application/xhtml+xml", true)) {
                    return@withContext Result.failure(MetadataFetchException("The response is not HTML."))
                }
                val charset = charsetFrom(contentType)
                val html = response.body.toString(charset)
                return@withContext Result.success(HtmlMetadataParser.parse(html, current))
            }
            Result.failure(MetadataFetchException("The webpage could not be loaded."))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(MetadataFetchException(error.message ?: "The webpage could not be loaded."))
        }
    }

    private fun charsetFrom(contentType: String?): Charset {
        val name = contentType?.let { Regex("charset\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
        return runCatching { if (name.isBlank()) Charsets.UTF_8 else Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
}
