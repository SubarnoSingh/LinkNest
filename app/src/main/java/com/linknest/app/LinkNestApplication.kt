package com.linknest.app

import android.app.Application
import androidx.room.Room
import com.linknest.app.data.local.LinkNestDatabase
import com.linknest.app.data.backup.LinkNestBackupService
import com.linknest.app.data.metadata.DefaultPageMetadataFetcher
import com.linknest.app.data.metadata.PageMetadataFetcher
import com.linknest.app.data.repository.LinkNestRepository

class LinkNestApplication : Application() {
    val database: LinkNestDatabase by lazy {
        Room.databaseBuilder(this, LinkNestDatabase::class.java, "linknest.db")
            .addMigrations(LinkNestDatabase.MIGRATION_1_2)
            .build()
    }

    val repository: LinkNestRepository by lazy { LinkNestRepository(database) }
    val metadataFetcher: PageMetadataFetcher by lazy { DefaultPageMetadataFetcher() }
    val backupService: LinkNestBackupService by lazy { LinkNestBackupService(database) }
}
