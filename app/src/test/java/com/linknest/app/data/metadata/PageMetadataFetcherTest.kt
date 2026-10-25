package com.linknest.app.data.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.URI

class PageMetadataFetcherTest {
    @Test
    fun fetchesHtmlAndFollowsRelativeRedirects() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://example.com/start" to MetadataHttpResponse(302, mapOf("Location" to "/page")),
                "https://example.com/page" to MetadataHttpResponse(
                    200,
                    mapOf("Content-Type" to "text/html; charset=UTF-8"),
                    "<title>Page title</title>".toByteArray()
                )
            )
        )
        val result = fetcher(transport).fetch("https://example.com/start")

        assertTrue(result.isSuccess)
        assertEquals("Page title", result.getOrThrow().title)
        assertEquals(listOf("https://example.com/start", "https://example.com/page"), transport.requests)
    }

    @Test
    fun rejectsInvalidPrivateAndUnsupportedResponses() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://example.com/file" to MetadataHttpResponse(200, mapOf("Content-Type" to "application/pdf")),
                "https://example.com/private" to MetadataHttpResponse(302, mapOf("Location" to "http://127.0.0.1/admin"))
            )
        )
        val fetcher = fetcher(transport)

        assertFalse(fetcher.fetch("ftp://example.com/file").isSuccess)
        assertFalse(fetcher.fetch("http://localhost/file").isSuccess)
        assertFalse(fetcher.fetch("https://example.com/file").isSuccess)
        assertFalse(fetcher.fetch("https://example.com/private").isSuccess)
    }

    @Test
    fun handlesFailuresOversizedResponsesAndRedirectLimits() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://example.com/large" to MetadataHttpResponse(200, mapOf("Content-Type" to "text/html"), ByteArray(64)),
                "https://example.com/a" to MetadataHttpResponse(302, mapOf("Location" to "/b")),
                "https://example.com/b" to MetadataHttpResponse(302, mapOf("Location" to "/c")),
                "https://example.com/c" to MetadataHttpResponse(302, mapOf("Location" to "/d"))
            ),
            failures = mapOf("https://example.com/fail" to IOException("offline"))
        )
        val fetcher = fetcher(transport, maxRedirects = 2, maxResponseBytes = 32)

        assertFalse(fetcher.fetch("https://example.com/large").isSuccess)
        assertFalse(fetcher.fetch("https://example.com/fail").isSuccess)
        assertFalse(fetcher.fetch("https://example.com/a").isSuccess)
    }

    @Test
    fun rejectsMappedIpv4DocumentationBenchmarkAndMixedDnsResults() {
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val policy = MetadataSecurityPolicy { host ->
            when (host) {
                "mapped-loopback" -> arrayOf(InetAddress.getByName("::ffff:127.0.0.1"))
                "mapped-private" -> arrayOf(InetAddress.getByName("::ffff:192.168.1.10"))
                "documentation" -> arrayOf(InetAddress.getByName("2001:db8::1"))
                "benchmark" -> arrayOf(InetAddress.getByName("2001:2::1"))
                "mixed" -> arrayOf(publicAddress, InetAddress.getByName("10.0.0.5"))
                else -> arrayOf(publicAddress)
            }
        }

        assertFalse(policy.validate(URI("https://mapped-loopback")).isSuccess)
        assertFalse(policy.validate(URI("https://mapped-private")).isSuccess)
        assertFalse(policy.validate(URI("https://documentation")).isSuccess)
        assertFalse(policy.validate(URI("https://benchmark")).isSuccess)
        assertFalse(policy.validate(URI("https://mixed")).isSuccess)
        assertTrue(policy.validate(URI("https://public.example")).isSuccess)
    }

    private class FakeTransport(
        private val responses: Map<String, MetadataHttpResponse>,
        private val failures: Map<String, Exception> = emptyMap()
    ) : MetadataHttpTransport {
        val requests = mutableListOf<String>()

        override fun get(url: URI, connectTimeoutMs: Int, readTimeoutMs: Int, maxBytes: Int): MetadataHttpResponse {
            requests += url.toString()
            failures[url.toString()]?.let { throw it }
            return responses[url.toString()] ?: MetadataHttpResponse(404, emptyMap())
        }
    }

    private fun fetcher(
        transport: MetadataHttpTransport,
        maxRedirects: Int = 3,
        maxResponseBytes: Int = 512 * 1024
    ) = DefaultPageMetadataFetcher(
        transport = transport,
        maxRedirects = maxRedirects,
        maxResponseBytes = maxResponseBytes,
        securityPolicy = MetadataSecurityPolicy { host ->
            if (host == "127.0.0.1") arrayOf(InetAddress.getByName(host))
            else arrayOf(InetAddress.getByName("93.184.216.34"))
        }
    )
}
