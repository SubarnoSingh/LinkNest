package com.linknest.app.data.backup

import androidx.room.withTransaction
import com.linknest.app.data.local.LinkNestDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class LinkNestBackupService(
    private val database: LinkNestDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val restoreFailureInjector: RestoreFailureInjector = RestoreFailureInjector { }
) {
    suspend fun writeExport(output: OutputStream): BackupSummary = withContext(ioDispatcher) {
        val snapshot = database.withTransaction {
            LinkNestBackupSnapshot(
                bookmarks = database.bookmarkDao().findAll(),
                notes = database.noteDao().findAll(),
                tags = database.tagDao().findAll(),
                bookmarkTags = database.bookmarkTagDao().findAll(),
                noteTags = database.noteTagDao().findAll(),
                graphEdges = database.graphEdgeDao().findAll()
            )
        }
        val json = LinkNestBackupCodec.encode(snapshot)
        val bytes = json.toByteArray(Charsets.UTF_8)
        // Validate our own output too, so unsupported/corrupt persisted values are never silently exported.
        LinkNestBackupCodec.parse(bytes)
        output.write(bytes)
        output.flush()
        BackupSummary.from(snapshot)
    }

    suspend fun restore(input: InputStream): BackupSummary = withContext(ioDispatcher) {
        // The complete file is parsed and validated before the transaction starts.
        val parsed = LinkNestBackupCodec.parse(input)
        val snapshot = parsed.snapshot
        database.withTransaction {
            database.graphEdgeDao().deleteAll()
            database.bookmarkTagDao().deleteAll()
            database.noteTagDao().deleteAll()
            database.bookmarkDao().deleteAll()
            database.noteDao().deleteAll()
            database.tagDao().deleteAll()

            if (snapshot.tags.isNotEmpty()) database.tagDao().insertAllForRestore(snapshot.tags)
            if (snapshot.bookmarks.isNotEmpty()) database.bookmarkDao().insertAll(snapshot.bookmarks)
            if (snapshot.notes.isNotEmpty()) database.noteDao().insertAll(snapshot.notes)
            restoreFailureInjector.afterBaseRecordsInserted()
            if (snapshot.bookmarkTags.isNotEmpty()) database.bookmarkTagDao().insertAllForRestore(snapshot.bookmarkTags)
            if (snapshot.noteTags.isNotEmpty()) database.noteTagDao().insertAllForRestore(snapshot.noteTags)
            if (snapshot.graphEdges.isNotEmpty()) database.graphEdgeDao().insertAllForRestore(snapshot.graphEdges)
        }
        BackupSummary.from(snapshot)
    }
}
