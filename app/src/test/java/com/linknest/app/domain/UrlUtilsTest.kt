package com.linknest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun normalizesHostAndDefaultPortWithoutDroppingMeaningfulQuery() {
        val result = UrlUtils.normalize(" HTTPS://Example.COM:443/article?utm_source=one#section. ")

        requireNotNull(result)
        assertEquals("https://example.com/article?utm_source=one#section.", result.canonical)
        assertEquals("example.com", result.domain)
    }

    @Test
    fun canonicalizesDnsTrailingDotButPreservesPathPunctuation() {
        val result = UrlUtils.normalize("https://Example.COM./article.")

        requireNotNull(result)
        assertEquals("https://example.com/article.", result.canonical)
    }

    @Test
    fun defaultPortsAndTrailingSlashNormalizeButQueryAndFragmentRemainDistinct() {
        val base = UrlUtils.normalize("http://example.com:80")
        val slash = UrlUtils.normalize("http://EXAMPLE.com/")
        val query = UrlUtils.normalize("http://example.com/?view=full")
        val fragment = UrlUtils.normalize("http://example.com/#section")

        requireNotNull(base)
        requireNotNull(slash)
        requireNotNull(query)
        requireNotNull(fragment)
        assertEquals(base.canonical, slash.canonical)
        assertNotEquals(base.canonical, query.canonical)
        assertNotEquals(base.canonical, fragment.canonical)
    }

    @Test
    fun rejectsUnsupportedOrMalformedUrls() {
        assertNull(UrlUtils.normalize("not a url"))
        assertNull(UrlUtils.normalize("ftp://example.com/file"))
        assertNull(UrlUtils.normalize("https://user:password@example.com"))
    }

    @Test
    fun infersCommonResourceTypes() {
        assertEquals(ResourceType.VIDEO, UrlUtils.resourceTypeFor("https://youtu.be/abc"))
        assertEquals(ResourceType.REPOSITORY, UrlUtils.resourceTypeFor("github.com/org/repo"))
        assertEquals(ResourceType.ARTICLE, UrlUtils.resourceTypeFor("https://example.com/read"))
        assertTrue(UrlUtils.domainOf("example.com") == "example.com")
    }
}
