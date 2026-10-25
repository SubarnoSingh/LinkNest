package com.linknest.app.data.metadata

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

class UrlConnectionMetadataTransport : MetadataHttpTransport {
    override fun get(url: URI, connectTimeoutMs: Int, readTimeoutMs: Int, maxBytes: Int): MetadataHttpResponse {
        val connection = (url.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "text/html, application/xhtml+xml;q=0.9")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "LinkNest/1.0 (+offline-first bookmark metadata)")
        }
        return try {
            val status = connection.responseCode
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.orEmpty() }
                .mapValues { it.value.firstOrNull().orEmpty() }
            val body = if (status in 200..299) readBounded(connection.inputStream, maxBytes) else ByteArray(0)
            MetadataHttpResponse(status, headers, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                total += count
                if (total > maxBytes) throw MetadataFetchException("The webpage response is too large.")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
