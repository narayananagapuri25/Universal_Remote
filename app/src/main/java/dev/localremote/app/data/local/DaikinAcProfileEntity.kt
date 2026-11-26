package dev.localremote.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally saved desired settings for a Daikin transport-family profile.
 *
 * The selected transport is a user choice, not proof of physical compatibility.
 * Loading a profile must require the user to reconfirm the original remote family.
 */
@Entity(tableName = "daikin_ac_profiles")
data class DaikinAcProfileEntity(
    @PrimaryKey val profileId: String,
    val displayName: String,
    val power: Boolean,
    val mode: String,
    val temperatureCelsius: Float,
    val fanSpeed: String,
    val verticalSwing: Boolean,
    val horizontalSwing: Boolean,
    val intelligentEye: Boolean,
    val econo: Boolean,
    val outdoorQuiet: Boolean,
    val powerful: Boolean,
    val weeklyScheduleActive: Boolean,
    val transportVariant: String,
    val updatedAtEpochMillis: Long
)
