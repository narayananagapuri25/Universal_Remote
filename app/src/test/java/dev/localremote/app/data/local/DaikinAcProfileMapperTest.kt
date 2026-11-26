package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcFanSpeed
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcMode
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcState
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcTransportVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaikinAcProfileMapperTest {

    @Test
    fun roundTripsCompleteThreeFrameStateWithoutMarkingItVerified() {
        val state = DaikinAcState(
            power = false,
            mode = DaikinAcMode.HEAT,
            temperatureCelsius = 23.5f,
            fanSpeed = DaikinAcFanSpeed.LEVEL_3,
            verticalSwing = true,
            horizontalSwing = true,
            intelligentEye = true,
            econo = true,
            outdoorQuiet = true,
            powerful = false,
            weeklyScheduleActive = true,
            variant = DaikinAcTransportVariant.THREE_FRAME
        )

        val entity = DaikinAcProfileMapper.toEntity(
            profileId = "living-room",
            displayName = "  Living room Daikin  ",
            state = state,
            updatedAtEpochMillis = 1234L
        )
        val restored = DaikinAcProfileMapper.toState(entity)

        assertEquals("Living room Daikin", entity.displayName)
        assertEquals("living-room", entity.profileId)
        assertEquals(1234L, entity.updatedAtEpochMillis)
        assertEquals(state, restored)
        assertFalse(entity.mode.isBlank())
        assertTrue(entity.transportVariant == DaikinAcTransportVariant.THREE_FRAME.name)
    }

    @Test
    fun roundTripsOneFrameStateAndAllFeatureFlags() {
        val state = DaikinAcState(
            power = true,
            mode = DaikinAcMode.COOL,
            temperatureCelsius = 26.5f,
            fanSpeed = DaikinAcFanSpeed.NIGHT,
            horizontalSwing = true,
            intelligentEye = true,
            variant = DaikinAcTransportVariant.ONE_FRAME
        )

        val restored = DaikinAcProfileMapper.toState(
            DaikinAcProfileMapper.toEntity("bedroom", "Bedroom AC", state, 99L)
        )

        assertEquals(state, restored)
    }

    @Test
    fun rejectsBlankOrOversizedNamesAndInvalidState() {
        val valid = DaikinAcState()

        assertTrue(runCatching {
            DaikinAcProfileMapper.toEntity("id", "  ", valid, 1L)
        }.exceptionOrNull() is IllegalArgumentException)

        assertTrue(runCatching {
            DaikinAcProfileMapper.toEntity(
                "id",
                "x".repeat(DaikinAcProfileMapper.MAX_DISPLAY_NAME_LENGTH + 1),
                valid,
                1L
            )
        }.exceptionOrNull() is IllegalArgumentException)

        assertTrue(runCatching {
            DaikinAcProfileMapper.toEntity(
                "id",
                "Invalid temperature",
                valid.copy(temperatureCelsius = 23.3f),
                1L
            )
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsUnknownPersistedEnumInsteadOfSilentlyGuessing() {
        val entity = DaikinAcProfileMapper.toEntity(
            "id",
            "Office AC",
            DaikinAcState(),
            1L
        ).copy(mode = "UNKNOWN_MODE")

        val failure = runCatching { DaikinAcProfileMapper.toState(entity) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            "Saved Daikin profile has an unknown mode.",
            failure?.message
        )
    }
}
