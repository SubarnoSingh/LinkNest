package com.linknest.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linknest.app.data.local.BookmarkEntity
import com.linknest.app.data.local.BookmarkTagCrossRef
import com.linknest.app.data.local.GraphEdgeEntity
import com.linknest.app.data.local.LinkNestDatabase
import com.linknest.app.data.local.NoteEntity
import com.linknest.app.data.local.NoteTagCrossRef
import com.linknest.app.data.local.TagEntity
import com.linknest.app.data.repository.BookmarkDraft
import com.linknest.app.data.repository.LinkNestRepository
import com.linknest.app.data.repository.NoteDraft
import com.linknest.app.data.repository.SaveBookmarkResult
import com.linknest.app.data.repository.SaveNoteResult
import com.linknest.app.domain.ResourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class LinkNestBackupServiceTest {
    private lateinit var database: LinkNestDatabase
    private lateinit var repository: LinkNestRepository
    private lateinit var service: LinkNestBackupService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LinkNestDatabase::class.java).build()
        repository = LinkNestRepository(database)
        service = LinkNestBackupService(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportsEmptyDatabaseAndRestoresIt() = runBlocking {
        val output = ByteArrayOutputStream()
        val summary = service.writeExport(output)

        assertEquals(0, summary.itemCount)
        assertEquals(emptySnapshot(), LinkNestBackupCodec.parse(output.toByteArray()).snapshot)
    }

    @Test
    fun restoresValidBackupIntoEmptyDatabaseWithAllRelationships() = runBlocking {
        val snapshot = replacementSnapshot()

        service.restore(ByteArrayInputStream(LinkNestBackupCodec.encode(snapshot).toByteArray()))

        assertEquals(snapshot, readSnapshot())
        val content = repository.content.first()
        assertEquals(1, content.bookmarks.size)
        assertEquals(1, content.notes.size)
        assertEquals(1, content.explicitEdges.size)
        assertEquals(listOf("backup"), content.bookmarks.single().tags)
        assertEquals(listOf("unicode-日本"), content.notes.single().tags)
    }

    @Test
    fun restoreReplacesExistingDataAndPreservesMetadataFavoriteAndFlags() = runBlocking {
        val savedBookmark = repository.saveBookmark(
            BookmarkDraft(
                url = "https://example.com/restore",
                title = "",
                description = "",
                userNote = "Keep this note",
                tags = "backup"
            )
        ) as SaveBookmarkResult.Saved
        repository.toggleFavorite(savedBookmark.bookmark.id)
        val savedNote = repository.saveNote(NoteDraft(title = "Context", body = "Body\n第二行", tags = "unicode-日本")) as SaveNoteResult.Saved
        val current = repository.content.first().bookmarks.single()
        repository.applyPageMetadata(
            current.id,
            current.updatedAt,
            com.linknest.app.data.metadata.PageMetadata(
                title = "Fetched title",
                description = "Fetched description",
                canonicalUrl = "https://example.com/canonical",
                faviconUrl = "https://example.com/favicon.ico"
            )
        )
        repository.addExplicitEdge("bookmark:${current.id}", "note:${savedNote.note.id}")
        val exported = ByteArrayOutputStream().also { service.writeExport(it) }.toByteArray()

        repository.saveBookmark(BookmarkDraft(url = "https://example.org/extra", title = "Extra", description = "", userNote = "", tags = "extra"))
        repository.saveNote(NoteDraft(title = "Extra note", body = "Extra", tags = "extra"))
        assertTrue(repository.content.first().bookmarks.size > 1)

        service.restore(ByteArrayInputStream(exported))

        val restored = readSnapshot()
        val exportedSnapshot = LinkNestBackupCodec.parse(exported).snapshot
        assertEquals(exportedSnapshot, restored)
        assertEquals(true, restored.bookmarks.single().isFavorite)
        assertEquals("https://example.com/canonical", restored.bookmarks.single().canonicalUrl)
        assertEquals(false, restored.bookmarks.single().descriptionUserEdited)
        assertEquals(1, repository.content.first().bookmarks.size)
        assertEquals(1, repository.content.first().notes.size)

        repository.toggleFavorite(restored.bookmarks.single().id)
        val addedBookmark = repository.saveBookmark(
            BookmarkDraft(url = "https://example.org/after-restore", title = "After restore", description = "", userNote = "", tags = "after")
        ) as SaveBookmarkResult.Saved
        val addedNote = repository.saveNote(NoteDraft(title = "After restore note", body = "Works", tags = "after")) as SaveNoteResult.Saved
        assertTrue(repository.addExplicitEdge("bookmark:${addedBookmark.bookmark.id}", "note:${addedNote.note.id}").isSuccess)
        assertEquals(false, repository.content.first().bookmarks.first { it.id == restored.bookmarks.single().id }.isFavorite)
    }

    @Test
    fun failedRestoreRollsBackTheOriginalDatabase() = runBlocking {
        repository.saveBookmark(BookmarkDraft(url = "https://example.com/original", title = "Original", description = "", userNote = "", tags = "original"))
        repository.saveNote(NoteDraft(title = "Original note", body = "Original", tags = "original"))
        val before = readSnapshot()
        val replacement = replacementSnapshot()
        val failingService = LinkNestBackupService(
            database = database,
            restoreFailureInjector = RestoreFailureInjector { throw IllegalStateException("injected restore failure") }
        )

        try {
            failingService.restore(ByteArrayInputStream(LinkNestBackupCodec.encode(replacement).toByteArray()))
            throw AssertionError("Expected the injected restore failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(before, readSnapshot())
    }

    @Test
    fun exportRestoreExportDoesNotLoseData() = runBlocking {
        service.restore(ByteArrayInputStream(LinkNestBackupCodec.encode(replacementSnapshot()).toByteArray()))
        val first = ByteArrayOutputStream().also { service.writeExport(it) }.toByteArray()

        service.restore(ByteArrayInputStream(first))
        val second = ByteArrayOutputStream().also { service.writeExport(it) }.toByteArray()

        assertEquals(
            LinkNestBackupCodec.parse(first).snapshot,
            LinkNestBackupCodec.parse(second).snapshot
        )
    }

    private suspend fun readSnapshot(): LinkNestBackupSnapshot = database.withTransaction {
        LinkNestBackupSnapshot(
            bookmarks = database.bookmarkDao().findAll(),
            notes = database.noteDao().findAll(),
            tags = database.tagDao().findAll(),
            bookmarkTags = database.bookmarkTagDao().findAll(),
            noteTags = database.noteTagDao().findAll(),
            graphEdges = database.graphEdgeDao().findAll()
        )
    }

    private fun emptySnapshot() = LinkNestBackupSnapshot(
        bookmarks = emptyList(),
        notes = emptyList(),
        tags = emptyList(),
        bookmarkTags = emptyList(),
        noteTags = emptyList(),
        graphEdges = emptyList()
    )

    private fun replacementSnapshot() = LinkNestBackupSnapshot(
        bookmarks = listOf(
            BookmarkEntity(
                id = 41L,
                url = "https://example.com/restored",
                originalUrl = "https://example.com/restored?source=backup",
                title = "Restored bookmark",
                description = "Restored description",
                userNote = "A restored note",
                domain = "example.com",
                resourceType = ResourceType.ARTICLE,
                createdAt = 10L,
                updatedAt = 20L,
                isFavorite = true,
                titleUserEdited = true,
                descriptionUserEdited = false,
                canonicalUrl = "https://example.com/canonical",
                faviconUrl = "https://example.com/favicon.ico"
            )
        ),
        notes = listOf(
            NoteEntity(id = 42L, title = "Restored note", body = "Unicode 日本\nSecond line", createdAt = 30L, updatedAt = 40L)
        ),
        tags = listOf(
            TagEntity(id = 51L, name = "backup"),
            TagEntity(id = 52L, name = "unicode-日本")
        ),
        bookmarkTags = listOf(BookmarkTagCrossRef(bookmarkId = 41L, tagId = 51L)),
        noteTags = listOf(NoteTagCrossRef(noteId = 42L, tagId = 52L)),
        graphEdges = listOf(
            GraphEdgeEntity(
                id = 61L,
                sourceNodeKey = "bookmark:41",
                targetNodeKey = "note:42",
                relationshipType = "EXPLICIT",
                createdAt = 50L
            )
        )
    )
}
