package com.linknest.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linknest.app.data.local.LinkNestDatabase
import com.linknest.app.data.metadata.PageMetadata
import com.linknest.app.domain.NodeKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkNestRepositoryTest {
    private lateinit var database: LinkNestDatabase
    private lateinit var repository: LinkNestRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LinkNestDatabase::class.java).build()
        repository = LinkNestRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bookmarkPersistsTagsAndDuplicateUrlsAreRejected() = runBlocking {
        val first = repository.saveBookmark(
            BookmarkDraft(
                url = "example.com/learn",
                title = "Learn",
                description = "Description",
                userNote = "Remember this",
                tags = "Kotlin, android, kotlin"
            )
        )
        assertTrue(first is SaveBookmarkResult.Saved)

        val snapshot = repository.content.first()
        assertEquals(1, snapshot.bookmarks.size)
        assertEquals(listOf("android", "kotlin"), snapshot.bookmarks.single().tags)

        val duplicate = repository.saveBookmark(
            BookmarkDraft(
                url = "https://EXAMPLE.com/learn",
                title = "Another title",
                description = "",
                userNote = "",
                tags = ""
            )
        )
        assertTrue(duplicate is SaveBookmarkResult.Duplicate)
        assertEquals(1, repository.content.first().bookmarks.size)
    }

    @Test
    fun favoriteToggleAndEditsPreserveAndReplaceTagAssociations() = runBlocking {
        val saved = repository.saveBookmark(
            BookmarkDraft(
                url = "https://example.com/tags",
                title = "Tags",
                description = "",
                userNote = "",
                tags = "old, shared"
            )
        ) as SaveBookmarkResult.Saved

        repository.toggleFavorite(saved.bookmark.id)
        var bookmark = repository.content.first().bookmarks.single()
        assertTrue(bookmark.isFavorite)
        assertEquals(listOf("old", "shared"), bookmark.tags)

        repository.saveBookmark(
            BookmarkDraft(
                id = saved.bookmark.id,
                url = saved.bookmark.url,
                title = "Tags updated",
                description = "",
                userNote = "",
                tags = "new"
            )
        )
        bookmark = repository.content.first().bookmarks.single()
        assertEquals(listOf("new"), bookmark.tags)
        assertTrue(bookmark.isFavorite)
    }

    @Test
    fun changingBookmarkUrlClearsMetadataFromThePreviousPage() = runBlocking {
        val saved = repository.saveBookmark(
            BookmarkDraft(url = "https://example.com/old", title = "Old", description = "", userNote = "", tags = "")
        ) as SaveBookmarkResult.Saved
        val before = repository.content.first().bookmarks.single()
        assertTrue(repository.applyPageMetadata(before.id, before.updatedAt, PageMetadata(canonicalUrl = "https://example.com/old-canonical", faviconUrl = "https://example.com/old.ico")))

        repository.saveBookmark(
            BookmarkDraft(id = saved.bookmark.id, url = "https://example.com/new", title = "Old", description = "", userNote = "", tags = "")
        )

        val updated = repository.content.first().bookmarks.single()
        assertEquals("https://example.com/new", updated.url)
        assertEquals(null, updated.canonicalUrl)
        assertEquals(null, updated.faviconUrl)
    }

    @Test
    fun explicitEdgesRejectSelfLinksIgnoreDuplicatesAndCleanUpOnDelete() = runBlocking {
        val bookmarkResult = repository.saveBookmark(BookmarkDraft(url = "https://example.com", title = "Example", description = "", userNote = "", tags = ""))
        val noteResult = repository.saveNote(NoteDraft(title = "Thought", body = "Body", tags = "idea"))
        val bookmarkId = (bookmarkResult as SaveBookmarkResult.Saved).bookmark.id
        val noteId = (noteResult as SaveNoteResult.Saved).note.id
        val bookmarkKey = NodeKey.bookmark(bookmarkId)
        val noteKey = NodeKey.note(noteId)

        assertTrue(repository.addExplicitEdge(bookmarkKey, bookmarkKey).isFailure)
        assertTrue(repository.addExplicitEdge(bookmarkKey, noteKey).isSuccess)
        assertTrue(repository.addExplicitEdge(noteKey, bookmarkKey).isSuccess)
        assertEquals(1, repository.content.first().explicitEdges.size)

        repository.deleteBookmark(bookmarkId)
        assertTrue(repository.content.first().explicitEdges.isEmpty())
        assertEquals(0, repository.content.first().bookmarks.size)
        assertTrue(database.bookmarkTagDao().observeAll().first().isEmpty())

        repository.deleteNote(noteId)
        assertTrue(database.noteTagDao().observeAll().first().isEmpty())
        assertTrue(database.tagDao().observeAll().first().isEmpty())
    }

    @Test
    fun fileDatabaseSurvivesCloseAndReopen() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "linknest-persistence-test.db"
            context.deleteDatabase(databaseName)

            val firstDatabase = Room.databaseBuilder(context, LinkNestDatabase::class.java, databaseName).build()
            LinkNestRepository(firstDatabase).saveBookmark(
                BookmarkDraft(
                    url = "https://example.com/persisted",
                    title = "Persisted",
                    description = "",
                    userNote = "",
                    tags = "restart"
                )
            )
            firstDatabase.close()

            val reopenedDatabase = Room.databaseBuilder(context, LinkNestDatabase::class.java, databaseName).build()
            val reopenedSnapshot = LinkNestRepository(reopenedDatabase).content.first()
            assertEquals("Persisted", reopenedSnapshot.bookmarks.single().title)
            assertEquals(listOf("restart"), reopenedSnapshot.bookmarks.single().tags)
            reopenedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun metadataUpdatesPreserveUserDataAndExplicitEdges() = runBlocking {
        val bookmarkResult = repository.saveBookmark(
            BookmarkDraft(url = "https://example.com/metadata", title = "", description = "", userNote = "", tags = "android")
        ) as SaveBookmarkResult.Saved
        val noteResult = repository.saveNote(NoteDraft(title = "Context", body = "Body", tags = "idea")) as SaveNoteResult.Saved
        repository.toggleFavorite(bookmarkResult.bookmark.id)
        repository.addExplicitEdge(NodeKey.bookmark(bookmarkResult.bookmark.id), NodeKey.note(noteResult.note.id))
        val before = repository.content.first().bookmarks.single()

        assertTrue(repository.applyPageMetadata(before.id, before.updatedAt, PageMetadata("Fetched title", "Fetched description", "https://example.com/canonical", "https://example.com/favicon.ico")))
        val snapshot = repository.content.first()
        val updated = snapshot.bookmarks.single()
        assertEquals("Fetched title", updated.title)
        assertEquals("Fetched description", updated.description)
        assertTrue(updated.isFavorite)
        assertEquals(listOf("android"), updated.tags)
        assertEquals(1, snapshot.explicitEdges.size)
        assertEquals("https://example.com/canonical", updated.canonicalUrl)
    }

    @Test
    fun staleMetadataCannotOverwriteAUserEdit() = runBlocking {
        val saved = repository.saveBookmark(
            BookmarkDraft(url = "https://example.com/race", title = "", description = "", userNote = "", tags = "")
        ) as SaveBookmarkResult.Saved
        val original = repository.content.first().bookmarks.single()
        val edited = repository.saveBookmark(
            BookmarkDraft(url = original.url, id = original.id, title = "Manual title", description = "Manual description", userNote = "", tags = "")
        ) as SaveBookmarkResult.Saved

        assertFalse(repository.applyPageMetadata(original.id, original.updatedAt, PageMetadata("Stale title", "Stale description")))
        val current = repository.content.first().bookmarks.single()
        assertEquals(edited.bookmark.title, current.title)
        assertEquals(edited.bookmark.description, current.description)
    }
}
