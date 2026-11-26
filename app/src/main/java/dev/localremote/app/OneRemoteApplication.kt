package dev.localremote.app

import android.app.Application
import androidx.room.Room
import dev.localremote.app.data.local.OneRemoteDatabase
import dev.localremote.app.data.local.SavedIrProfileRepository

class OneRemoteApplication : Application() {
    val database: OneRemoteDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            OneRemoteDatabase::class.java,
            "oneremote-local.db"
        )
            .addMigrations(
                OneRemoteDatabase.MIGRATION_1_2,
                OneRemoteDatabase.MIGRATION_2_3
            )
            .build()
    }

    val irProfileRepository: SavedIrProfileRepository by lazy {
        SavedIrProfileRepository(database.irRemoteProfileDao())
    }
}
