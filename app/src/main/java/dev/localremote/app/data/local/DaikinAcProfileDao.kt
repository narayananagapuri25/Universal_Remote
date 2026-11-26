package dev.localremote.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DaikinAcProfileDao {
    @Query(
        """
        SELECT * FROM daikin_ac_profiles
        ORDER BY updatedAtEpochMillis DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<DaikinAcProfileEntity>>

    @Query("SELECT * FROM daikin_ac_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getById(profileId: String): DaikinAcProfileEntity?

    @Upsert
    suspend fun upsert(profile: DaikinAcProfileEntity)

    @Query("DELETE FROM daikin_ac_profiles WHERE profileId = :profileId")
    suspend fun deleteById(profileId: String)
}
