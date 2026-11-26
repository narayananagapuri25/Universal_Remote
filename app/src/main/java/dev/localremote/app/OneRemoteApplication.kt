package dev.localremote.app

import android.app.Application
import androidx.room.Room
import dev.localremote.app.data.local.OneRemoteDatabase

class OneRemoteApplication : Application() {
    val database: OneRemoteDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            OneRemoteDatabase::class.java,
            "oneremote-local.db"
        ).build()
    }
}
