package com.linknest.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY id")
    suspend fun findAll(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun update(bookmark: BookmarkEntity): Int

    @Query("UPDATE bookmarks SET isFavorite = CASE WHEN isFavorite = 0 THEN 1 ELSE 0 END, updatedAt = :updatedAt WHERE id = :bookmarkId")
    suspend fun toggleFavorite(bookmarkId: Long, updatedAt: Long): Int

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun findAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity): Int

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun findAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun findByNames(names: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllForRestore(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM bookmark_tags UNION SELECT tagId FROM note_tags)")
    suspend fun deleteUnused()

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}

@Dao
interface BookmarkTagDao {
    @Query("SELECT * FROM bookmark_tags")
    fun observeAll(): Flow<List<BookmarkTagCrossRef>>

    @Query("SELECT * FROM bookmark_tags ORDER BY bookmarkId, tagId")
    suspend fun findAll(): List<BookmarkTagCrossRef>

    @Query("SELECT t.* FROM tags t INNER JOIN bookmark_tags bt ON t.id = bt.tagId WHERE bt.bookmarkId = :bookmarkId ORDER BY t.name")
    suspend fun findTagsForBookmark(bookmarkId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<BookmarkTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllForRestore(refs: List<BookmarkTagCrossRef>)

    @Query("DELETE FROM bookmark_tags WHERE bookmarkId = :bookmarkId")
    suspend fun deleteForBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmark_tags")
    suspend fun deleteAll()
}

@Dao
interface NoteTagDao {
    @Query("SELECT * FROM note_tags")
    fun observeAll(): Flow<List<NoteTagCrossRef>>

    @Query("SELECT * FROM note_tags ORDER BY noteId, tagId")
    suspend fun findAll(): List<NoteTagCrossRef>

    @Query("SELECT t.* FROM tags t INNER JOIN note_tags nt ON t.id = nt.tagId WHERE nt.noteId = :noteId ORDER BY t.name")
    suspend fun findTagsForNote(noteId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<NoteTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllForRestore(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM note_tags")
    suspend fun deleteAll()
}

@Dao
interface GraphEdgeDao {
    @Query("SELECT * FROM graph_edges ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GraphEdgeEntity>>

    @Query("SELECT * FROM graph_edges ORDER BY id")
    suspend fun findAll(): List<GraphEdgeEntity>

    @Query("SELECT * FROM graph_edges WHERE sourceNodeKey = :source AND targetNodeKey = :target LIMIT 1")
    suspend fun find(source: String, target: String): GraphEdgeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(edge: GraphEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllForRestore(edges: List<GraphEdgeEntity>)

    @Query("DELETE FROM graph_edges WHERE sourceNodeKey = :source AND targetNodeKey = :target")
    suspend fun delete(source: String, target: String)

    @Query("DELETE FROM graph_edges WHERE sourceNodeKey = :nodeKey OR targetNodeKey = :nodeKey")
    suspend fun deleteForNode(nodeKey: String)

    @Query("DELETE FROM graph_edges")
    suspend fun deleteAll()
}
