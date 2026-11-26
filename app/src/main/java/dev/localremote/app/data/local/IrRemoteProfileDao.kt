package dev.localremote.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class IrRemoteProfileWithButtons(
    @Embedded val profile: IrRemoteProfileEntity,
    @Relation(
        parentColumn = "profileId",
        entityColumn = "profileId"
    )
    val buttons: List<IrRemoteButtonEntity>
)

@Dao
interface IrRemoteProfileDao {
    @Upsert
    suspend fun upsertProfile(profile: IrRemoteProfileEntity)

    @Upsert
    suspend fun upsertButtons(buttons: List<IrRemoteButtonEntity>)

    @Query("DELETE FROM ir_remote_buttons WHERE profileId = :profileId")
    suspend fun deleteButtonsForProfile(profileId: String)

    @Query("DELETE FROM ir_remote_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Upsert
    suspend fun upsertSavedDevice(device: SavedDeviceEntity)

    @Query("DELETE FROM saved_devices WHERE id = :deviceId")
    suspend fun deleteSavedDevice(deviceId: String)

    @Transaction
    suspend fun saveProfileAsDevice(
        profile: IrRemoteProfileEntity,
        buttons: List<IrRemoteButtonEntity>,
        device: SavedDeviceEntity
    ) {
        upsertProfile(profile)
        deleteButtonsForProfile(profile.profileId)
        if (buttons.isNotEmpty()) upsertButtons(buttons)
        upsertSavedDevice(device)
    }

    @Transaction
    suspend fun deleteProfileAndDevice(profileId: String, deviceId: String) {
        deleteProfile(profileId)
        deleteSavedDevice(deviceId)
    }

    @Transaction
    @Query("SELECT * FROM ir_remote_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileWithButtons(profileId: String): IrRemoteProfileWithButtons?

    @Query(
        """
        SELECT * FROM ir_remote_profiles
        ORDER BY displayName COLLATE NOCASE ASC
        """
    )
    fun observeProfiles(): Flow<List<IrRemoteProfileEntity>>
}
