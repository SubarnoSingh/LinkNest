package com.linknest.app.data.repository

import androidx.room.withTransaction
import com.linknest.app.data.local.BookmarkEntity
import com.linknest.app.data.local.BookmarkTagCrossRef
import com.linknest.app.data.local.GraphEdgeEntity
import com.linknest.app.data.local.LinkNestDatabase
import com.linknest.app.data.local.NoteEntity
import com.linknest.app.data.local.NoteTagCrossRef
import com.linknest.app.data.local.TagEntity
import com.linknest.app.data.metadata.PageMetadata
import com.linknest.app.domain.Bookmark
import com.linknest.app.domain.GraphEdge
import com.linknest.app.domain.GraphEdgeType
import com.linknest.app.domain.Note
import com.linknest.app.domain.NodeKey
import com.linknest.app.domain.ResourceType
import com.linknest.app.domain.UrlUtils
import com.linknest.app.domain.parseTags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class BookmarkDraft(
    val id: Long? = null,
    val url: String,
    val title: String,
    val description: String,
    val userNote: String,
    val tags: String
)

data class NoteDraft(
    val id: Long? = null,
    val title: String,
    val body: String,
    val tags: String
)

sealed interface SaveBookmarkResult {
    data class Saved(val bookmark: Bookmark) : SaveBookmarkResult
    data class Duplicate(val existing: Bookmark) : SaveBookmarkResult
    data class Invalid(val message: String) : SaveBookmarkResult
}

sealed interface SaveNoteResult {
    data class Saved(val note: Note) : SaveNoteResult
    data class Invalid(val message: String) : SaveNoteResult
}

data class ContentSnapshot(
    val bookmarks: List<Bookmark>,
    val notes: List<Note>,
    val explicitEdges: List<GraphEdge>
)

class LinkNestRepository(
    private val database: LinkNestDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val bookmarkDao = database.bookmarkDao()
    private val noteDao = database.noteDao()
    private val tagDao = database.tagDao()
    private val bookmarkTagDao = database.bookmarkTagDao()
    private val noteTagDao = database.noteTagDao()
    private val graphEdgeDao = database.graphEdgeDao()

    private val bookmarkRecords: Flow<List<Bookmark>> = combine(
        bookmarkDao.observeAll(),
        tagDao.observeAll(),
        bookmarkTagDao.observeAll()
    ) { bookmarks, tags, refs ->
        val tagsById = tags.associateBy { it.id }
        val tagsByBookmark = refs.groupBy { it.bookmarkId }
        bookmarks.map { entity ->
            entity.toDomain(
                tagsByBookmark[entity.id].orEmpty().mapNotNull { tagsById[it.tagId]?.name }.sorted()
            )
        }
    }

    private val noteRecords: Flow<List<Note>> = combine(
        noteDao.observeAll(),
        tagDao.observeAll(),
        noteTagDao.observeAll()
    ) { notes, tags, refs ->
        val tagsById = tags.associateBy { it.id }
        val tagsByNote = refs.groupBy { it.noteId }
        notes.map { entity ->
            entity.toDomain(
                tagsByNote[entity.id].orEmpty().mapNotNull { tagsById[it.tagId]?.name }.sorted()
            )
        }
    }

    val content: Flow<ContentSnapshot> = combine(
        bookmarkRecords,
        noteRecords,
        graphEdgeDao.observeAll()
    ) { bookmarks, notes, edges ->
        ContentSnapshot(
            bookmarks = bookmarks,
            notes = notes,
            explicitEdges = edges.map { it.toDomain() }
        )
    }

    suspend fun saveBookmark(draft: BookmarkDraft): SaveBookmarkResult = withContext(ioDispatcher) {
        val normalized = UrlUtils.normalize(draft.url)
            ?: return@withContext SaveBookmarkResult.Invalid("Enter a valid http or https URL.")

        database.withTransaction {
            val existing = bookmarkDao.findByUrl(normalized.canonical)
            if (existing != null && existing.id != draft.id) {
                return@withTransaction SaveBookmarkResult.Duplicate(
                    existing.toDomain(loadTagsForBookmark(existing.id))
                )
            }

            val now = System.currentTimeMillis()
            val previous = draft.id?.let { bookmarkDao.findById(it) }
            val urlChanged = previous != null && previous.url != normalized.canonical
            val entity = BookmarkEntity(
                id = draft.id ?: 0,
                url = normalized.canonical,
                originalUrl = normalized.original,
                title = draft.title.trim().ifBlank { normalized.domain },
                description = draft.description.trim(),
                userNote = draft.userNote.trim(),
                domain = normalized.domain,
                resourceType = UrlUtils.resourceTypeFor(normalized.canonical),
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
                isFavorite = previous?.isFavorite ?: false,
                titleUserEdited = previous?.titleUserEdited ?: draft.title.trim().isNotBlank(),
                descriptionUserEdited = previous?.descriptionUserEdited ?: draft.description.trim().isNotBlank(),
                canonicalUrl = previous?.canonicalUrl?.takeUnless { urlChanged },
                faviconUrl = previous?.faviconUrl?.takeUnless { urlChanged }
            )

            val title = draft.title.trim().ifBlank { normalized.domain }
            val description = draft.description.trim()
            val updatedEntity = entity.copy(
                title = title,
                description = description,
                titleUserEdited = previous?.titleUserEdited == true || (previous == null && draft.title.trim().isNotBlank()) || (previous != null && title != previous.title),
                descriptionUserEdited = previous?.descriptionUserEdited == true || (previous == null && draft.description.trim().isNotBlank()) || (previous != null && description != previous.description)
            )

            val savedId = if (updatedEntity.id == 0L) {
                val insertedId = bookmarkDao.insert(updatedEntity)
                if (insertedId == -1L) {
                    val raced = bookmarkDao.findByUrl(normalized.canonical)
                        ?: return@withTransaction SaveBookmarkResult.Invalid("Unable to save this link.")
                    return@withTransaction SaveBookmarkResult.Duplicate(
                        raced.toDomain(loadTagsForBookmark(raced.id))
                    )
                }
                insertedId
            } else {
                val updatedRows = bookmarkDao.update(updatedEntity)
                if (updatedRows == 0) return@withTransaction SaveBookmarkResult.Invalid("That link no longer exists.")
                updatedEntity.id
            }

            syncBookmarkTags(savedId, parseTags(draft.tags))
            val saved = bookmarkDao.findById(savedId)
                ?: return@withTransaction SaveBookmarkResult.Invalid("Unable to save this link.")
            SaveBookmarkResult.Saved(saved.toDomain(loadTagsForBookmark(saved.id)))
        }
    }

    suspend fun toggleFavorite(bookmarkId: Long) = withContext(ioDispatcher) {
        bookmarkDao.toggleFavorite(bookmarkId, System.currentTimeMillis())
    }

    suspend fun applyPageMetadata(bookmarkId: Long, expectedUpdatedAt: Long, metadata: PageMetadata): Boolean = withContext(ioDispatcher) {
        database.withTransaction {
            val current = bookmarkDao.findById(bookmarkId) ?: return@withTransaction false
            if (current.updatedAt != expectedUpdatedAt) return@withTransaction false
            val title = if (!current.titleUserEdited && !metadata.title.isNullOrBlank()) metadata.title else current.title
            val description = if (!current.descriptionUserEdited && !metadata.description.isNullOrBlank()) metadata.description else current.description
            val canonicalUrl = metadata.canonicalUrl?.takeIf { it.isNotBlank() } ?: current.canonicalUrl
            val faviconUrl = metadata.faviconUrl?.takeIf { it.isNotBlank() } ?: current.faviconUrl
            if (title == current.title && description == current.description &&
                canonicalUrl == current.canonicalUrl && faviconUrl == current.faviconUrl
            ) return@withTransaction true
            val updated = current.copy(
                title = title,
                description = description,
                canonicalUrl = canonicalUrl,
                faviconUrl = faviconUrl,
                updatedAt = System.currentTimeMillis()
            )
            bookmarkDao.update(updated) > 0
        }
    }

    suspend fun deleteBookmark(bookmarkId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            bookmarkDao.findById(bookmarkId)?.let { bookmarkDao.delete(it) }
            graphEdgeDao.deleteForNode(NodeKey.bookmark(bookmarkId))
            tagDao.deleteUnused()
        }
    }

    suspend fun saveNote(draft: NoteDraft): SaveNoteResult = withContext(ioDispatcher) {
        val title = draft.title.trim()
        if (title.isBlank() && draft.body.trim().isBlank()) {
            return@withContext SaveNoteResult.Invalid("Add a title or some note content first.")
        }
        database.withTransaction {
            val now = System.currentTimeMillis()
            val previous = draft.id?.takeIf { it > 0L }?.let { noteDao.findById(it) }
            val entity = NoteEntity(
                id = draft.id?.takeIf { it > 0L } ?: 0,
                title = title.ifBlank { "Untitled note" },
                body = draft.body.trim(),
                createdAt = previous?.createdAt ?: now,
                updatedAt = now
            )
            val savedId = if (entity.id == 0L) {
                val insertedId = noteDao.insert(entity)
                if (insertedId == -1L) return@withTransaction SaveNoteResult.Invalid("Unable to save this note.")
                insertedId
            } else {
                val updatedRows = noteDao.update(entity)
                if (updatedRows == 0) return@withTransaction SaveNoteResult.Invalid("That note no longer exists.")
                entity.id
            }
            syncNoteTags(savedId, parseTags(draft.tags))
            val saved = noteDao.findById(savedId)
                ?: return@withTransaction SaveNoteResult.Invalid("Unable to save this note.")
            SaveNoteResult.Saved(saved.toDomain(loadTagsForNote(saved.id)))
        }
    }

    suspend fun deleteNote(noteId: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            noteDao.findById(noteId)?.let { noteDao.delete(it) }
            graphEdgeDao.deleteForNode(NodeKey.note(noteId))
            tagDao.deleteUnused()
        }
    }

    suspend fun addExplicitEdge(first: String, second: String): Result<Unit> = withContext(ioDispatcher) {
        if (first == second) return@withContext Result.failure(IllegalArgumentException("A node cannot connect to itself."))
        if (!nodeExists(first) || !nodeExists(second)) {
            return@withContext Result.failure(IllegalArgumentException("That item no longer exists."))
        }
        val (source, target) = canonicalNodes(first, second)
        if (graphEdgeDao.find(source, target) == null) {
            graphEdgeDao.insert(
                GraphEdgeEntity(
                    sourceNodeKey = source,
                    targetNodeKey = target,
                    relationshipType = GraphEdgeType.EXPLICIT.name,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        Result.success(Unit)
    }

    suspend fun removeExplicitEdge(first: String, second: String) = withContext(ioDispatcher) {
        val (source, target) = canonicalNodes(first, second)
        graphEdgeDao.delete(source, target)
    }

    private suspend fun nodeExists(key: String): Boolean = when {
        NodeKey.isBookmark(key) -> bookmarkDao.findById(NodeKey.id(key)) != null
        NodeKey.isNote(key) -> noteDao.findById(NodeKey.id(key)) != null
        else -> false
    }

    private fun canonicalNodes(first: String, second: String): Pair<String, String> =
        if (first < second) first to second else second to first

    private suspend fun syncBookmarkTags(bookmarkId: Long, names: List<String>) {
        bookmarkTagDao.deleteForBookmark(bookmarkId)
        if (names.isNotEmpty()) {
            tagDao.insertAll(names.map { TagEntity(name = it) })
            bookmarkTagDao.insertAll(tagDao.findByNames(names).map { BookmarkTagCrossRef(bookmarkId, it.id) })
        }
        tagDao.deleteUnused()
    }

    private suspend fun syncNoteTags(noteId: Long, names: List<String>) {
        noteTagDao.deleteForNote(noteId)
        if (names.isNotEmpty()) {
            tagDao.insertAll(names.map { TagEntity(name = it) })
            noteTagDao.insertAll(tagDao.findByNames(names).map { NoteTagCrossRef(noteId, it.id) })
        }
        tagDao.deleteUnused()
    }

    private suspend fun loadTagsForBookmark(id: Long): List<String> {
        return bookmarkTagDao.findTagsForBookmark(id).map { it.name }
    }

    private suspend fun loadTagsForNote(id: Long): List<String> = noteTagDao.findTagsForNote(id).map { it.name }

    private fun BookmarkEntity.toDomain(tags: List<String>) = Bookmark(
        id = id,
        url = url,
        originalUrl = originalUrl,
        title = title,
        description = description,
        userNote = userNote,
        domain = domain,
        resourceType = resourceType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        tags = tags,
        canonicalUrl = canonicalUrl,
        faviconUrl = faviconUrl
    )

    private fun NoteEntity.toDomain(tags: List<String>) = Note(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        tags = tags
    )

    private fun GraphEdgeEntity.toDomain() = GraphEdge(
        source = sourceNodeKey,
        target = targetNodeKey,
        type = runCatching { GraphEdgeType.valueOf(relationshipType) }.getOrDefault(GraphEdgeType.EXPLICIT)
    )
}
