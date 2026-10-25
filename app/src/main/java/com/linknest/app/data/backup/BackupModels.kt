package com.linknest.app.data.backup

import com.linknest.app.data.local.BookmarkEntity
import com.linknest.app.data.local.BookmarkTagCrossRef
import com.linknest.app.data.local.GraphEdgeEntity
import com.linknest.app.data.local.NoteEntity
import com.linknest.app.data.local.NoteTagCrossRef
import com.linknest.app.data.local.TagEntity

/**
 * Versioned, replace-all backup format for LinkNest.
 *
 * The top-level JSON `format` value is stable and `version` is increased only when
 * the serialized contract changes incompatibly. Unknown versions and unknown fields
 * are rejected rather than silently discarded. Version 1 stores every persisted Room
 * record and relationship needed to reconstruct the local library.
 */
object LinkNestBackupFormat {
    const val FORMAT_IDENTIFIER = "com.linknest.app.backup"
    const val VERSION = 1

    const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    const val MAX_RECORD_COUNT = 100_000
    const val MAX_URL_LENGTH = 8_192
    const val MAX_TEXT_LENGTH = 1_000_000
    const val MAX_TAG_NAME_LENGTH = 256
    const val MAX_NODE_KEY_LENGTH = 128
}

data class LinkNestBackupSnapshot(
    val bookmarks: List<BookmarkEntity>,
    val notes: List<NoteEntity>,
    val tags: List<TagEntity>,
    val bookmarkTags: List<BookmarkTagCrossRef>,
    val noteTags: List<NoteTagCrossRef>,
    val graphEdges: List<GraphEdgeEntity>
)

data class ParsedLinkNestBackup(
    val exportedAt: Long,
    val snapshot: LinkNestBackupSnapshot
)

data class BackupSummary(
    val bookmarkCount: Int,
    val noteCount: Int,
    val tagCount: Int,
    val explicitEdgeCount: Int
) {
    val itemCount: Int get() = bookmarkCount + noteCount

    companion object {
        fun from(snapshot: LinkNestBackupSnapshot): BackupSummary = BackupSummary(
            bookmarkCount = snapshot.bookmarks.size,
            noteCount = snapshot.notes.size,
            tagCount = snapshot.tags.size,
            explicitEdgeCount = snapshot.graphEdges.size
        )
    }
}

class BackupValidationException(message: String) : Exception(message)

class BackupIoException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun interface RestoreFailureInjector {
    fun afterBaseRecordsInserted()
}
