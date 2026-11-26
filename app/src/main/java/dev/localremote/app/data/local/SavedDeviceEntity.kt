package dev.localremote.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey val id: String,
    val category: String,
    val manufacturer: String?,
    val model: String?,
    val displayName: String,
    val profileId: String?,
    val isFavorite: Boolean = false,
    val connectionState: String = "SAVED_OFFLINE",
    val createdAtEpochMillis: Long
)
