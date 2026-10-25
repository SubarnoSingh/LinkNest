package com.linknest.app.domain

enum class ResourceType(val label: String) {
    ARTICLE("Article"),
    VIDEO("Video"),
    REPOSITORY("Repository"),
    OTHER("Link")
}

data class Bookmark(
    val id: Long,
    val url: String,
    val originalUrl: String,
    val title: String,
    val description: String,
    val userNote: String,
    val domain: String,
    val resourceType: ResourceType,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val tags: List<String>,
    val canonicalUrl: String? = null,
    val faviconUrl: String? = null
)

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String>
)

sealed interface LibraryItem {
    val key: String
    val title: String
    val tags: List<String>
    val updatedAt: Long

    data class BookmarkItem(val bookmark: Bookmark) : LibraryItem {
        override val key: String = NodeKey.bookmark(bookmark.id)
        override val title: String = bookmark.title
        override val tags: List<String> = bookmark.tags
        override val updatedAt: Long = bookmark.updatedAt
    }

    data class NoteItem(val note: Note) : LibraryItem {
        override val key: String = NodeKey.note(note.id)
        override val title: String = note.title
        override val tags: List<String> = note.tags
        override val updatedAt: Long = note.updatedAt
    }
}

enum class GraphNodeType {
    BOOKMARK,
    NOTE
}

data class GraphNode(
    val key: String,
    val title: String,
    val nodeType: GraphNodeType,
    val subtitle: String,
    val tags: List<String>,
    val bookmark: Bookmark? = null,
    val note: Note? = null
)

enum class GraphEdgeType(val label: String) {
    EXPLICIT("Related"),
    SHARED_TAG("Shared tag")
}

data class GraphEdge(
    val source: String,
    val target: String,
    val type: GraphEdgeType,
    val label: String? = null
)

data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

object NodeKey {
    fun bookmark(id: Long): String = "bookmark:$id"
    fun note(id: Long): String = "note:$id"
    fun isBookmark(key: String): Boolean = key.startsWith("bookmark:")
    fun isNote(key: String): Boolean = key.startsWith("note:")
    fun id(key: String): Long = key.substringAfter(':').toLongOrNull() ?: -1L
}

fun parseTags(raw: String): List<String> = raw
    .split(',', '\n', ' ', '\t')
    .asSequence()
    .map { it.trim().removePrefix("#").lowercase() }
    .filter { it.isNotBlank() }
    .distinct()
    .take(20)
    .toList()

fun tagsAsText(tags: List<String>): String = tags.joinToString(", ")
