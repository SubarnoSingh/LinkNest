package com.linknest.app.data.backup

import com.linknest.app.data.local.BookmarkEntity
import com.linknest.app.data.local.BookmarkTagCrossRef
import com.linknest.app.data.local.GraphEdgeEntity
import com.linknest.app.data.local.NoteEntity
import com.linknest.app.data.local.NoteTagCrossRef
import com.linknest.app.data.local.TagEntity
import com.linknest.app.domain.ResourceType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    @Test
    fun emptyDatabaseBackupRoundTrips() {
        val snapshot = emptySnapshot()

        val parsed = LinkNestBackupCodec.parse(LinkNestBackupCodec.encode(snapshot, exportedAt = 123L).toByteArray())

        assertEquals(123L, parsed.exportedAt)
        assertEquals(snapshot, parsed.snapshot)
    }

    @Test
    fun preservesMetadataRelationshipsUnicodeAndMultilineText() {
        val snapshot = populatedSnapshot()

        val first = LinkNestBackupCodec.parse(LinkNestBackupCodec.encode(snapshot, exportedAt = 456L).toByteArray())
        val second = LinkNestBackupCodec.parse(LinkNestBackupCodec.encode(first.snapshot, exportedAt = 789L).toByteArray())

        assertEquals(snapshot, first.snapshot)
        assertEquals(snapshot, second.snapshot)
    }

    @Test
    fun rejectsMalformedMissingFutureAndUnknownFields() {
        assertRejects("{")

        val missing = JSONObject(LinkNestBackupCodec.encode(emptySnapshot())).apply { remove("notes") }
        assertRejects(missing.toString())

        val future = JSONObject(LinkNestBackupCodec.encode(emptySnapshot())).apply { put("version", 2) }
        assertRejects(future.toString())

        val unknown = JSONObject(LinkNestBackupCodec.encode(emptySnapshot())).apply { put("unexpected", true) }
        assertRejects(unknown.toString())
    }

    @Test
    fun rejectsDuplicateIdsDanglingReferencesAndInvalidGraphEdges() {
        val duplicateRoot = JSONObject(LinkNestBackupCodec.encode(populatedSnapshot()))
        val bookmarks = duplicateRoot.getJSONArray("bookmarks")
        bookmarks.put(bookmarks.getJSONObject(0))
        assertRejects(duplicateRoot.toString())

        val dangling = populatedSnapshot().copy(
            bookmarkTags = listOf(BookmarkTagCrossRef(bookmarkId = 999L, tagId = 3L))
        )
        assertRejects(LinkNestBackupCodec.encode(dangling))

        val selfLink = populatedSnapshot().copy(
            graphEdges = listOf(
                GraphEdgeEntity(
                    id = 21L,
                    sourceNodeKey = "bookmark:7",
                    targetNodeKey = "bookmark:7",
                    relationshipType = "EXPLICIT",
                    createdAt = 100L
                )
            )
        )
        assertRejects(LinkNestBackupCodec.encode(selfLink))
    }

    private fun assertRejects(json: String) {
        try {
            LinkNestBackupCodec.parse(json.toByteArray())
            fail("Expected backup validation to fail")
        } catch (_: BackupValidationException) {
            // Expected.
        }
    }

    private fun emptySnapshot() = LinkNestBackupSnapshot(
        bookmarks = emptyList(),
        notes = emptyList(),
        tags = emptyList(),
        bookmarkTags = emptyList(),
        noteTags = emptyList(),
        graphEdges = emptyList()
    )

    private fun populatedSnapshot() = LinkNestBackupSnapshot(
        bookmarks = listOf(
            BookmarkEntity(
                id = 7L,
                url = "https://example.com/library",
                originalUrl = "https://example.com/library?from=share",
                title = "Café — 日本語",
                description = "A description with punctuation: yes!",
                userNote = "Line one\nLine two\n第三行",
                domain = "example.com",
                resourceType = ResourceType.ARTICLE,
                createdAt = 100L,
                updatedAt = 200L,
                isFavorite = true,
                titleUserEdited = false,
                descriptionUserEdited = true,
                canonicalUrl = "https://example.com/canonical",
                faviconUrl = "https://example.com/favicon.ico"
            )
        ),
        notes = listOf(
            NoteEntity(
                id = 9L,
                title = "Résumé / 계획",
                body = "First line\n\nIndented line\twith punctuation — ✓",
                createdAt = 300L,
                updatedAt = 400L
            )
        ),
        tags = listOf(
            TagEntity(id = 3L, name = "research"),
            TagEntity(id = 4L, name = "unicode-日本")
        ),
        bookmarkTags = listOf(BookmarkTagCrossRef(bookmarkId = 7L, tagId = 3L)),
        noteTags = listOf(NoteTagCrossRef(noteId = 9L, tagId = 4L)),
        graphEdges = listOf(
            GraphEdgeEntity(
                id = 21L,
                sourceNodeKey = "bookmark:7",
                targetNodeKey = "note:9",
                relationshipType = "EXPLICIT",
                createdAt = 500L
            )
        )
    )
}
