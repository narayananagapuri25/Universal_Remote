package dev.localremote.app.connectivity.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IrSignalValidatorTest {
    @Test
    fun acceptsValidAlternatingMarkAndSpacePattern() {
        val signal = IrSignal(
            carrierFrequencyHz = 38_000,
            patternMicros = listOf(9_000, 4_500, 560, 560)
        )

        assertNull(IrSignalValidator.validate(signal))
    }

    @Test
    fun signalTakesDefensiveCopyOfWaveform() {
        val source = mutableListOf(500, 500)
        val signal = IrSignal(38_000, source)

        source[0] = 0

        assertEquals(listOf(500, 500), signal.patternMicros)
    }

    @Test
    fun rejectsNonPositiveCarrierFrequency() {
        val signal = IrSignal(0, listOf(500, 500))
        assertEquals(
            "Carrier frequency must be positive.",
            IrSignalValidator.validate(signal)
        )
    }

    @Test
    fun acceptsPatternThatEndsWithMark() {
        val signal = IrSignal(38_000, listOf(500, 500, 600))
        assertNull(IrSignalValidator.validate(signal))
    }

    @Test
    fun rejectsZeroOrNegativeDurations() {
        val signal = IrSignal(38_000, listOf(500, 0))
        assertEquals(
            "IR pattern durations must all be positive.",
            IrSignalValidator.validate(signal)
        )
    }

    @Test
    fun rejectsPatternOfTwoSecondsOrLonger() {
        val signal = IrSignal(38_000, listOf(1_000_000, 1_000_000))
        assertEquals(
            "IR pattern must be shorter than two seconds.",
            IrSignalValidator.validate(signal)
        )
    }

    @Test
    fun acceptsPatternJustBelowTwoSeconds() {
        val signal = IrSignal(38_000, listOf(999_999, 999_999))
        assertNull(IrSignalValidator.validate(signal))
    }

    @Test
    fun rejectsExcessiveSegmentCount() {
        val signal = IrSignal(38_000, List(IrSignalValidator.MAX_PATTERN_SEGMENTS + 2) { 1 })
        assertEquals(
            "IR pattern contains too many segments.",
            IrSignalValidator.validate(signal)
        )
    }
}
