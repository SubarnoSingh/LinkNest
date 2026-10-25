package com.linknest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentParserTest {
    @Test
    fun extractsPrimaryUrlAndPreservesContext() {
        val parsed = SharedContentParser.parse("Read this later: https://example.com/article and also https://example.org/other")

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("https://example.com/article", parsed.url)
        assertEquals(1, parsed.additionalUrlCount)
        assertEquals("Read this later: and also", parsed.suggestedTitle)
        assertTrue(parsed.note.contains("Read this later:"))
        assertTrue(parsed.note.contains("https://example.org/other"))
    }

    @Test
    fun removesSentencePunctuationButKeepsTerminalQueryPunctuation() {
        val sentence = SharedContentParser.parse("Read https://example.com/article.")
        val query = SharedContentParser.parse("Read https://example.com/article?x=1.")

        requireNotNull(sentence)
        requireNotNull(query)
        assertEquals("https://example.com/article", sentence.url)
        assertEquals("https://example.com/article?x=1.", query.url)
    }

    @Test
    fun returnsTextOnlyShareWithoutDiscardingIt() {
        val parsed = SharedContentParser.parse("A thought worth saving")

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertNull(parsed.url)
        assertEquals("A thought worth saving", parsed.note)
    }

    @Test
    fun handlesViewIntentAndEmptyShareSafely() {
        val view = SharedContentParser.parse(null, "https://example.com/from-view")
        val empty = SharedContentParser.parse("   ")

        requireNotNull(view)
        assertEquals("https://example.com/from-view", view.url)
        assertNull(empty)
    }
}
