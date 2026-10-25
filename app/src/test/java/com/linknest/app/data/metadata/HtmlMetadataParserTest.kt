package com.linknest.app.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class HtmlMetadataParserTest {
    @Test
    fun prefersOpenGraphThenTwitterThenHtmlAndDecodesEntities() {
        val html = """
            <html><head>
              <title>Document &amp; title</title>
              <meta name="description" content="Standard description">
              <meta name="twitter:title" content="Twitter title">
              <meta property="og:title" content="OG &amp; title">
              <meta property="og:description" content="OG &#x27;description&#x27;">
              <link rel="canonical" href="/canonical">
              <link rel="icon" href="/favicon.ico">
            </head></html>
        """.trimIndent()

        val metadata = HtmlMetadataParser.parse(html, URI("https://example.com/articles/read"))

        assertEquals("OG & title", metadata.title)
        assertEquals("OG 'description'", metadata.description)
        assertEquals("https://example.com/canonical", metadata.canonicalUrl)
        assertEquals("https://example.com/favicon.ico", metadata.faviconUrl)
    }

    @Test
    fun fallsBackToTwitterThenHtmlAndRejectsEmptyValues() {
        val html = """
            <title>HTML title</title>
            <meta property="og:title" content="   ">
            <meta name="twitter:title" content="Twitter title">
            <meta property="og:description" content="">
            <meta name="twitter:description" content="Twitter description">
        """.trimIndent()

        val metadata = HtmlMetadataParser.parse(html, URI("https://example.com"))

        assertEquals("Twitter title", metadata.title)
        assertEquals("Twitter description", metadata.description)
    }

    @Test
    fun limitsLongValuesAndUsesHostWhenTitleMetadataIsMissing() {
        val longTitle = "x".repeat(300)
        val longDescription = "y".repeat(700)
        val html = "<meta property='og:title' content='$longTitle'><meta name='description' content='$longDescription'>"

        val metadata = HtmlMetadataParser.parse(html, URI("https://example.com"))

        assertEquals("x".repeat(200), metadata.title)
        assertEquals("y".repeat(500), metadata.description)
        assertNull(HtmlMetadataParser.parse("<html><head></head></html>", URI("https://example.com")).canonicalUrl)
    }

    @Test
    fun recognizesTokenizedIconRelationsWithoutTreatingArbitraryTextAsAnIcon() {
        val html = """
            <link rel="not-icon" href="/wrong.ico">
            <link rel="shortcut icon" href="/favicon.ico">
        """.trimIndent()

        val metadata = HtmlMetadataParser.parse(html, URI("https://example.com"))

        assertEquals("https://example.com/favicon.ico", metadata.faviconUrl)
    }
}
