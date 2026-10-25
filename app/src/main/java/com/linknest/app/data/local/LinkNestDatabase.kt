package com.linknest.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        NoteEntity::class,
        TagEntity::class,
        BookmarkTagCrossRef::class,
        NoteTagCrossRef::class,
        GraphEdgeEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class LinkNestDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkTagDao(): BookmarkTagDao
    abstract fun noteTagDao(): NoteTagDao
    abstract fun graphEdgeDao(): GraphEdgeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing v1 rows have no provenance. Be conservative and protect their content.
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN titleUserEdited INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN descriptionUserEdited INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN canonicalUrl TEXT")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN faviconUrl TEXT")
            }
        }
    }
}
