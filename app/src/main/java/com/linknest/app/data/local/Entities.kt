package com.linknest.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.linknest.app.domain.ResourceType

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true), Index(value = ["updatedAt"])]
)
data class BookmarkEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val titleUserEdited: Boolean = false,
    val descriptionUserEdited: Boolean = false,
    val canonicalUrl: String? = null,
    val faviconUrl: String? = null
)

@Entity(
    tableName = "notes",
    indices = [Index(value = ["updatedAt"])]
)
data class NoteEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "bookmark_tags",
    primaryKeys = ["bookmarkId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = BookmarkEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookmarkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class BookmarkTagCrossRef(val bookmarkId: Long, val tagId: Long)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class NoteTagCrossRef(val noteId: Long, val tagId: Long)

@Entity(
    tableName = "graph_edges",
    indices = [
        Index(value = ["sourceNodeKey", "targetNodeKey"], unique = true),
        Index(value = ["sourceNodeKey"]),
        Index(value = ["targetNodeKey"])
    ]
)
data class GraphEdgeEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceNodeKey: String,
    val targetNodeKey: String,
    val relationshipType: String,
    val createdAt: Long
)
