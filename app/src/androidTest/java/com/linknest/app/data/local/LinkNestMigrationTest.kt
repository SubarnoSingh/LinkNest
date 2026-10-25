package com.linknest.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linknest.app.data.metadata.PageMetadata
import com.linknest.app.data.repository.LinkNestRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkNestMigrationTest {
    private lateinit var context: Context
    private val databaseName = "linknest-migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun v1BookmarkSchemaMigratesWithSafeMetadataDefaults() = runBlocking {
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT NOT NULL, originalUrl TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, userNote TEXT NOT NULL, domain TEXT NOT NULL, resourceType TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isFavorite INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
        legacy.execSQL("CREATE TABLE bookmark_tags (bookmarkId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(bookmarkId, tagId), FOREIGN KEY(bookmarkId) REFERENCES bookmarks(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        legacy.execSQL("CREATE TABLE note_tags (noteId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(noteId, tagId), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        legacy.execSQL("CREATE TABLE graph_edges (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceNodeKey TEXT NOT NULL, targetNodeKey TEXT NOT NULL, relationshipType TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE UNIQUE INDEX index_bookmarks_url ON bookmarks(url)")
        legacy.execSQL("CREATE INDEX index_bookmarks_updatedAt ON bookmarks(updatedAt)")
        legacy.execSQL("CREATE INDEX index_notes_updatedAt ON notes(updatedAt)")
        legacy.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")
        legacy.execSQL("CREATE INDEX index_bookmark_tags_tagId ON bookmark_tags(tagId)")
        legacy.execSQL("CREATE INDEX index_note_tags_tagId ON note_tags(tagId)")
        legacy.execSQL("CREATE UNIQUE INDEX index_graph_edges_sourceNodeKey_targetNodeKey ON graph_edges(sourceNodeKey, targetNodeKey)")
        legacy.execSQL("CREATE INDEX index_graph_edges_sourceNodeKey ON graph_edges(sourceNodeKey)")
        legacy.execSQL("CREATE INDEX index_graph_edges_targetNodeKey ON graph_edges(targetNodeKey)")
        legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        legacy.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'legacy-linknest-v1')")
        legacy.execSQL("INSERT INTO bookmarks(url, originalUrl, title, description, userNote, domain, resourceType, createdAt, updatedAt, isFavorite) VALUES ('https://example.com/legacy', 'https://example.com/legacy', 'Legacy', '', '', 'example.com', 'ARTICLE', 1, 1, 1)")
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, LinkNestDatabase::class.java, databaseName)
            .addMigrations(LinkNestDatabase.MIGRATION_1_2)
            .build()
        val repository = LinkNestRepository(migrated)
        val bookmark = repository.content.first().bookmarks.single()

        assertEquals("Legacy", bookmark.title)
        assertTrue(bookmark.isFavorite)
        assertEquals(null, bookmark.canonicalUrl)
        assertEquals(null, bookmark.faviconUrl)

        assertTrue(
            repository.applyPageMetadata(
                bookmark.id,
                bookmark.updatedAt,
                PageMetadata("Fetched title", "Fetched description", "https://example.com/canonical")
            )
        )
        val protected = repository.content.first().bookmarks.single()
        assertEquals("Legacy", protected.title)
        assertEquals("", protected.description)
        assertEquals("https://example.com/canonical", protected.canonicalUrl)

        val columns = migrated.openHelper.writableDatabase.query("PRAGMA table_info(bookmarks)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue("titleUserEdited" in columns)
        assertTrue("descriptionUserEdited" in columns)
        assertTrue("canonicalUrl" in columns)
        assertTrue("faviconUrl" in columns)
        migrated.openHelper.writableDatabase.query("SELECT titleUserEdited, descriptionUserEdited FROM bookmarks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }
}
