package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfile
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfileValidator
import kotlinx.coroutines.flow.Flow

class SavedIrProfileRepository(
    private val profileDao: IrRemoteProfileDao
) {
    fun observeProfiles(): Flow<List<IrRemoteProfileEntity>> =
        profileDao.observeProfiles()

    suspend fun saveAsDevice(
        profile: IrRemoteProfile,
        createdAtEpochMillis: Long
    ): String {
        val errors = IrRemoteProfileValidator.validate(profile)
        require(errors.isEmpty()) {
            "Cannot save this remote: ${errors.joinToString("; ")}"
        }

        val records = IrRemoteProfileMapper.toRecords(profile, createdAtEpochMillis)
        val deviceId = deviceIdFor(profile.profileId)
        val device = SavedDeviceEntity(
            id = deviceId,
            category = profile.category.name,
            manufacturer = profile.brand,
            model = profile.model,
            displayName = profile.displayName,
            profileId = profile.profileId,
            isFavorite = false,
            connectionState = "SAVED_PROFILE",
            createdAtEpochMillis = createdAtEpochMillis
        )

        profileDao.saveProfileAsDevice(
            profile = records.profile,
            buttons = records.buttons,
            device = device
        )
        return deviceId
    }

    suspend fun loadProfile(profileId: String): IrRemoteProfile? =
        profileDao.getProfileWithButtons(profileId)?.let(IrRemoteProfileMapper::fromRecords)

    suspend fun deleteProfile(profileId: String) {
        profileDao.deleteProfileAndDevice(
            profileId = profileId,
            deviceId = deviceIdFor(profileId)
        )
    }

    companion object {
        fun deviceIdFor(profileId: String): String = "ir-profile:$profileId"
    }
}
