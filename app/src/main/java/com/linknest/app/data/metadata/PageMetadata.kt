package com.linknest.app.data.metadata

data class PageMetadata(
    val title: String? = null,
    val description: String? = null,
    val canonicalUrl: String? = null,
    val faviconUrl: String? = null
)

data class MetadataHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray = ByteArray(0)
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
}

interface MetadataHttpTransport {
    @Throws(Exception::class)
    fun get(url: java.net.URI, connectTimeoutMs: Int, readTimeoutMs: Int, maxBytes: Int): MetadataHttpResponse
}

interface PageMetadataFetcher {
    suspend fun fetch(url: String): Result<PageMetadata>
}

class MetadataFetchException(message: String) : Exception(message)
