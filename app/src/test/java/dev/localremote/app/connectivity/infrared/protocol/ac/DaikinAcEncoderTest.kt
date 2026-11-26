package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrSignalValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DaikinAcEncoderTest {

    @Test
    fun threeFrameDefaultStateMatchesGoldenStateBytes() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(
                power = true,
                mode = DaikinAcMode.COOL,
                temperatureCelsius = 21.0f,
                fanSpeed = DaikinAcFanSpeed.AUTO,
                variant = DaikinAcTransportVariant.THREE_FRAME
            )
        )

        assertEquals(
            listOf(
                0x11, 0xDA, 0x27, 0x00, 0x00,
                0x39, 0x2A, 0x00, 0xA0, 0x00, 0x00,
                0x06, 0x60, 0x00, 0x00, 0xC1, 0x80, 0x00, 0xBC
            ),
            bytes.map { it.toInt() and 0xFF }
        )
    }

    @Test
    fun oneFrameDefaultStateMatchesGoldenStateBytes() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(
                power = true,
                mode = DaikinAcMode.COOL,
                temperatureCelsius = 21.0f,
                fanSpeed = DaikinAcFanSpeed.AUTO,
                variant = DaikinAcTransportVariant.ONE_FRAME
            )
        )

        assertEquals(
            listOf(
                0x11, 0xDA, 0x27, 0x00, 0x00,
                0x31, 0x2A, 0x00, 0xA0, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0xC5, 0x00, 0x00, 0xD2
            ),
            bytes.map { it.toInt() and 0xFF }
        )
    }

    @Test
    fun stateChecksumIsSumModulo256() {
        listOf(
            DaikinAcState(),
            DaikinAcState(variant = DaikinAcTransportVariant.ONE_FRAME),
            DaikinAcState(mode = DaikinAcMode.HEAT, temperatureCelsius = 24.5f),
            DaikinAcState(mode = DaikinAcMode.DRY)
        ).forEach { state ->
            val bytes = DaikinAcEncoder.buildStateFrame(state)
            val expected = bytes.take(18).sumOf { it.toInt() and 0xFF } and 0xFF
            assertEquals(expected, bytes[18].toInt() and 0xFF)
        }
    }

    @Test
    fun modesAreEncodedInHighNibble() {
        val expected = mapOf(
            DaikinAcMode.AUTO to 0x0,
            DaikinAcMode.DRY to 0x2,
            DaikinAcMode.COOL to 0x3,
            DaikinAcMode.HEAT to 0x4,
            DaikinAcMode.FAN to 0x6
        )

        expected.forEach { (mode, wireCode) ->
            val bytes = DaikinAcEncoder.buildStateFrame(DaikinAcState(mode = mode))
            assertEquals(wireCode, (bytes[5].toInt() and 0xFF) ushr 4)
        }
    }

    @Test
    fun dryModeUsesFixedTemperatureMarker() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(mode = DaikinAcMode.DRY, temperatureCelsius = 21.0f)
        )
        assertEquals(0xC0, bytes[6].toInt() and 0xFF)
    }

    @Test
    fun halfDegreeTemperatureIsEncodedAsCelsiusTimesTwo() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(mode = DaikinAcMode.COOL, temperatureCelsius = 24.5f)
        )
        assertEquals(49, bytes[6].toInt() and 0xFF)
    }

    @Test
    fun rejectsTemperaturesOutsideModeSpecificRange() {
        val lowCool = runCatching {
            DaikinAcEncoder.encode(DaikinAcState(mode = DaikinAcMode.COOL, temperatureCelsius = 17.5f))
        }.exceptionOrNull()
        val highHeat = runCatching {
            DaikinAcEncoder.encode(DaikinAcState(mode = DaikinAcMode.HEAT, temperatureCelsius = 31.0f))
        }.exceptionOrNull()
        val lowAuto = runCatching {
            DaikinAcEncoder.encode(DaikinAcState(mode = DaikinAcMode.AUTO, temperatureCelsius = 17.0f))
        }.exceptionOrNull()

        assertTrue(lowCool is IllegalArgumentException)
        assertTrue(highHeat is IllegalArgumentException)
        assertTrue(lowAuto is IllegalArgumentException)
    }

    @Test
    fun rejectsTemperatureNotOnHalfDegreeBoundary() {
        val failure = runCatching {
            DaikinAcEncoder.encode(
                DaikinAcState(mode = DaikinAcMode.COOL, temperatureCelsius = 23.3f)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun swingAndFanSettingsUseDocumentedNibbles() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(
                fanSpeed = DaikinAcFanSpeed.LEVEL_3,
                verticalSwing = true,
                horizontalSwing = true
            )
        )

        assertEquals(0x5F, bytes[8].toInt() and 0xFF)
        assertEquals(0x0F, bytes[9].toInt() and 0xFF)
    }

    @Test
    fun powerfulModeClearsEconoAndOutdoorQuietButPreservesIntelligentEye() {
        val bytes = DaikinAcEncoder.buildStateFrame(
            DaikinAcState(
                powerful = true,
                econo = true,
                outdoorQuiet = true,
                intelligentEye = true,
                fanSpeed = DaikinAcFanSpeed.COMFORT
            )
        )

        assertEquals(0x01, bytes[13].toInt() and 0x01)
        assertEquals(0x00, bytes[13].toInt() and 0x20)
        assertEquals(0x00, bytes[16].toInt() and 0x04)
        assertEquals(0x02, bytes[16].toInt() and 0x02)
        assertEquals(0xA0, bytes[8].toInt() and 0xF0)
    }

    @Test
    fun comfortFanRequiresThreeFrameAndVerticalSwingOff() {
        val oneFrame = runCatching {
            DaikinAcEncoder.encode(
                DaikinAcState(
                    fanSpeed = DaikinAcFanSpeed.COMFORT,
                    variant = DaikinAcTransportVariant.ONE_FRAME
                )
            )
        }.exceptionOrNull()
        val swingConflict = runCatching {
            DaikinAcEncoder.encode(
                DaikinAcState(
                    fanSpeed = DaikinAcFanSpeed.COMFORT,
                    verticalSwing = true
                )
            )
        }.exceptionOrNull()

        assertTrue(oneFrame is IllegalArgumentException)
        assertTrue(swingConflict is IllegalArgumentException)
    }

    @Test
    fun threeFrameIdentityAndClockChecksumsAreValid() {
        val identity = DaikinAcEncoder.buildIdentityFrame(comfortFan = true)
        val clock = DaikinAcEncoder.buildClockFrame(
            LocalDateTime.of(2026, 6, 7, 13, 50)
        )

        assertEquals(8, identity.size)
        assertEquals(8, clock.size)
        assertEquals(identity.take(7).sumOf { it.toInt() and 0xFF } and 0xFF, identity[7].toInt() and 0xFF)
        assertEquals(clock.take(7).sumOf { it.toInt() and 0xFF } and 0xFF, clock[7].toInt() and 0xFF)
        assertEquals(0x10, identity[6].toInt() and 0xFF)
        assertEquals(0x3E, clock[5].toInt() and 0xFF)
        assertEquals(0x0B, clock[6].toInt() and 0xFF)
    }

    @Test
    fun threeFrameAndOneFrameProduceExpectedTimingLengths() {
        val three = DaikinAcEncoder.encode(
            DaikinAcState(variant = DaikinAcTransportVariant.THREE_FRAME),
            LocalDateTime.of(2026, 6, 7, 13, 50)
        )
        val one = DaikinAcEncoder.encode(
            DaikinAcState(variant = DaikinAcTransportVariant.ONE_FRAME),
            LocalDateTime.of(2026, 6, 7, 13, 50)
        )

        assertEquals(584, three.patternMicros.size)
        assertEquals(320, one.patternMicros.size)
        assertEquals(null, IrSignalValidator.validate(three))
        assertEquals(null, IrSignalValidator.validate(one))
        assertTrue(three.totalDurationMicros < 2_000_000)
        assertTrue(one.totalDurationMicros < 2_000_000)
    }

    @Test
    fun clockUsesSundayAsProtocolDayOne() {
        val sunday = DaikinAcEncoder.buildClockFrame(
            LocalDateTime.of(2026, 6, 7, 13, 50)
        )
        val monday = DaikinAcEncoder.buildClockFrame(
            LocalDateTime.of(2026, 6, 8, 13, 50)
        )

        assertEquals(1, (sunday[6].toInt() and 0xFF) ushr 3)
        assertEquals(2, (monday[6].toInt() and 0xFF) ushr 3)
    }
}
