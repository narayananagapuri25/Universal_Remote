package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcEncoder
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcFanSpeed
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcMode
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcState
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcTransportVariant

/**
 * Converts saved local records to and from the validated Daikin state model.
 *
 * A profile stores desired settings and a selected transport family only. It
 * never stores or creates a "physically verified" compatibility claim.
 */
object DaikinAcProfileMapper {
    const val MAX_DISPLAY_NAME_LENGTH = 60

    fun toEntity(
        profileId: String,
        displayName: String,
        state: DaikinAcState,
        updatedAtEpochMillis: Long
    ): DaikinAcProfileEntity {
        val safeId = profileId.trim()
        val safeName = displayName.trim()
        require(safeId.isNotEmpty()) { "Profile ID cannot be empty." }
        require(safeName.isNotEmpty()) { "Enter a name for this AC profile." }
        require(safeName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "Profile name must be $MAX_DISPLAY_NAME_LENGTH characters or fewer."
        }
        require(updatedAtEpochMillis >= 0L) { "Profile timestamp cannot be negative." }

        // Reuse the encoder's state validation so invalid settings cannot be persisted.
        DaikinAcEncoder.encode(state)

        return DaikinAcProfileEntity(
            profileId = safeId,
            displayName = safeName,
            power = state.power,
            mode = state.mode.name,
            temperatureCelsius = state.temperatureCelsius,
            fanSpeed = state.fanSpeed.name,
            verticalSwing = state.verticalSwing,
            horizontalSwing = state.horizontalSwing,
            intelligentEye = state.intelligentEye,
            econo = state.econo,
            outdoorQuiet = state.outdoorQuiet,
            powerful = state.powerful,
            weeklyScheduleActive = state.weeklyScheduleActive,
            transportVariant = state.variant.name,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    fun toState(entity: DaikinAcProfileEntity): DaikinAcState {
        val state = DaikinAcState(
            power = entity.power,
            mode = parseEnum<DaikinAcMode>(entity.mode, "mode"),
            temperatureCelsius = entity.temperatureCelsius,
            fanSpeed = parseEnum<DaikinAcFanSpeed>(entity.fanSpeed, "fan speed"),
            verticalSwing = entity.verticalSwing,
            horizontalSwing = entity.horizontalSwing,
            intelligentEye = entity.intelligentEye,
            econo = entity.econo,
            outdoorQuiet = entity.outdoorQuiet,
            powerful = entity.powerful,
            weeklyScheduleActive = entity.weeklyScheduleActive,
            variant = parseEnum<DaikinAcTransportVariant>(
                entity.transportVariant,
                "transport family"
            )
        )
        DaikinAcEncoder.encode(state)
        return state
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, field: String): T =
        runCatching { enumValueOf<T>(value) }
            .getOrElse { throw IllegalArgumentException("Saved Daikin profile has an unknown $field.") }
}
