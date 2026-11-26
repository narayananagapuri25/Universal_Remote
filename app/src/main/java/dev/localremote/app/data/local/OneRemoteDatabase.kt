package dev.localremote.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedDeviceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class OneRemoteDatabase : RoomDatabase() {
    abstract fun savedDeviceDao(): SavedDeviceDao
}
