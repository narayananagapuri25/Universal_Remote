package dev.localremote.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeviceDao {
    @Query(
        """
        SELECT * FROM saved_devices
        ORDER BY isFavorite DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<SavedDeviceEntity>>

    @Upsert
    suspend fun upsert(device: SavedDeviceEntity)

    @Query("DELETE FROM saved_devices WHERE id = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("UPDATE saved_devices SET isFavorite = :favorite WHERE id = :deviceId")
    suspend fun setFavorite(deviceId: String, favorite: Boolean)
}
