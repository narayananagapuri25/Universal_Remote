package dev.localremote.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Upsert
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

    @Query(
        """
        SELECT * FROM ir_remote_control_mappings
        WHERE profileId = :profileId
        ORDER BY role ASC
        """
    )
    fun observeControlMappings(profileId: String): Flow<List<IrRemoteControlMappingEntity>>

    @Upsert
    suspend fun upsertControlMappings(mappings: List<IrRemoteControlMappingEntity>)

    @Query("DELETE FROM ir_remote_control_mappings WHERE profileId = :profileId")
    suspend fun deleteControlMappings(profileId: String)

    @Transaction
    suspend fun replaceControlMappings(
        profileId: String,
        mappings: List<IrRemoteControlMappingEntity>
    ) {
        require(mappings.all { it.profileId == profileId }) {
            "All control mappings must belong to the same remote profile."
        }
        require(mappings.map { it.role }.distinct().size == mappings.size) {
            "Each control role can be assigned only once."
        }
        deleteControlMappings(profileId)
        if (mappings.isNotEmpty()) upsertControlMappings(mappings)
    }

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
