package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.connectivity.infrared.IrSignalValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolEncoderTest {

    @Test
    fun necEncodesLsbFirstAddressAndInvertedCommand() {
        val signal = IrProtocolEncoder.encode(
            IrProtocolCommand.Nec(address = 0x00, command = 0x45)
        )

        assertEquals(38_000, signal.carrierFrequencyHz)
        assertEquals(67, signal.patternMicros.size)
        assertEquals(listOf(9_000, 4_500, 560, 560), signal.patternMicros.take(4))
        // Address 0x00 is eight zero bits, then inverse address 0xFF starts with ones.
        assertEquals(listOf(560, 560, 560, 560), signal.patternMicros.subList(2, 6))
        assertEquals(560, signal.patternMicros.last())
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun necExtendedEncodesSixteenBitAddressWithoutAddressComplement() {
        val signal = IrProtocolEncoder.encode(
            IrProtocolCommand.NecExtended(address = 0x87EE, command = 0xA05D)
        )

        assertEquals(38_000, signal.carrierFrequencyHz)
        assertEquals(67, signal.patternMicros.size)
        // Flipper .ir stores both 16-bit payload values little-endian:
        // address EE 87 00 00, command 5D A0 00 00.
        val bitSpaces = signal.patternMicros.subList(3, 67)
            .filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals(
            listOf(
                // Address EE 87, LSB first.
                560, 1_690, 1_690, 1_690, 560, 1_690, 1_690, 1_690,
                1_690, 1_690, 1_690, 560, 560, 560, 560, 1_690,
                // Command 5D A0, LSB first.
                1_690, 560, 1_690, 1_690, 1_690, 560, 1_690, 560,
                560, 560, 560, 560, 560, 1_690, 560, 1_690
            ),
            bitSpaces
        )
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun necExtendedAcceptsAnAddressWhoseBytesHappenToBeAnInversePair() {
        val signal = IrProtocolEncoder.encode(
            IrProtocolCommand.NecExtended(address = 0x34CB, command = 0xA055)
        )

        assertEquals(67, signal.patternMicros.size)
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun necExtendedRejectsPayloadFieldsWiderThanSixteenBits() {
        val addressError = runCatching {
            IrProtocolEncoder.encode(IrProtocolCommand.NecExtended(0x1_0000, 0x1234))
        }.exceptionOrNull()
        val commandError = runCatching {
            IrProtocolEncoder.encode(IrProtocolCommand.NecExtended(0x1234, 0x1_0000))
        }.exceptionOrNull()

        assertTrue(addressError is IllegalArgumentException)
        assertTrue(commandError is IllegalArgumentException)
    }

    @Test
    fun samsung32EncodesAddressTwiceThenCommandAndInverse() {
        val signal = IrProtocolEncoder.encode(
            IrProtocolCommand.Samsung32(address = 0xE0, command = 0x40)
        )

        assertEquals(38_000, signal.carrierFrequencyHz)
        assertEquals(67, signal.patternMicros.size)
        assertEquals(listOf(4_500, 4_500), signal.patternMicros.take(2))
        // Address 0xE0 is transmitted LSB-first: 0,0,0,0,0,1,1,1.
        assertEquals(
            listOf(
                560, 560, 560, 560, 560, 560, 560, 560,
                560, 560, 560, 1_690, 560, 1_690, 560, 1_690
            ),
            signal.patternMicros.subList(2, 18)
        )
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun sonySirc12EncodesCommandBeforeAddressLsbFirst() {
        val signal = IrProtocolEncoder.encode(
            IrProtocolCommand.SonySirc(
                variant = SonySircVariant.SIRC_12,
                address = 1,
                command = 19
            )
        )

        assertEquals(40_000, signal.carrierFrequencyHz)
        assertEquals(26, signal.patternMicros.size)
        assertEquals(listOf(2_400, 600), signal.patternMicros.take(2))
        // Command 19 has its least-significant bit set.
        assertEquals(listOf(1_200, 600), signal.patternMicros.subList(2, 4))
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun sonySirc15And20ProduceExpectedFrameLengths() {
        val sirc15 = IrProtocolEncoder.encode(
            IrProtocolCommand.SonySirc(
                variant = SonySircVariant.SIRC_15,
                address = 0xA5,
                command = 0x35
            )
        )
        val sirc20 = IrProtocolEncoder.encode(
            IrProtocolCommand.SonySirc(
                variant = SonySircVariant.SIRC_20,
                address = 0x12,
                command = 0x35,
                extendedData = 0xC3
            )
        )

        assertEquals(32, sirc15.patternMicros.size)
        assertEquals(42, sirc20.patternMicros.size)
        assertEquals(null, IrSignalValidator.validate(sirc15))
        assertEquals(null, IrSignalValidator.validate(sirc20))
    }

    @Test
    fun sonySircRejectsExtendedFieldForNonExtendedVariant() {
        val error = runCatching {
            IrProtocolEncoder.encode(
                IrProtocolCommand.SonySirc(
                    variant = SonySircVariant.SIRC_12,
                    address = 1,
                    command = 1,
                    extendedData = 0x10
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun sonySirc20RequiresExtendedField() {
        val error = runCatching {
            IrProtocolEncoder.encode(
                IrProtocolCommand.SonySirc(
                    variant = SonySircVariant.SIRC_20,
                    address = 1,
                    command = 1
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }


    @Test
    fun rc5AndRc5xEncodeManchesterBitsAndToggleBit() {
        val standard = IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x05, 0x35), 0)
        val standardToggle = IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x05, 0x35), 1)
        val extended = IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x12, 0x65, true), 1)
        assertEquals(36_000, standard.carrierFrequencyHz)
        assertEquals(49_003L, standard.totalDurationMicros)
        assertEquals(listOf(1, 1, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1), decodeRc5Bits(standard))
        assertEquals(1, decodeRc5Bits(standardToggle)[2])
        assertEquals(listOf(1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1), decodeRc5Bits(extended))
        assertEquals(null, IrSignalValidator.validate(standard))
        assertEquals(null, IrSignalValidator.validate(extended))
    }

    @Test
    fun rc5RejectsInvalidToggleAddressAndCommandWidths() {
        val failures = listOf(
            runCatching { IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(1, 1), 2) }.exceptionOrNull(),
            runCatching { IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x20, 1)) }.exceptionOrNull(),
            runCatching { IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(1, 0x40)) }.exceptionOrNull(),
            runCatching { IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(1, 0x80, true)) }.exceptionOrNull()
        )
        assertTrue(failures.all { it is IllegalArgumentException })
    }

    private fun decodeRc5Bits(signal: dev.localremote.app.connectivity.infrared.IrSignal): List<Int> {
        val durations = signal.patternMicros.toMutableList()
        val last = durations.lastIndex
        durations[last] -= 25_000
        if (durations[last] == 0) durations.removeAt(last)
        val cells = mutableListOf<Boolean>()
        var mark = true
        durations.forEach { duration ->
            assertEquals("RC5 half-cell units", 0, duration % 889)
            repeat(duration / 889) { cells += mark }
            mark = !mark
        }
        val fullCells = listOf(false) + cells
        assertEquals(28, fullCells.size)
        return (0 until 14).map { index ->
            when (fullCells.subList(index * 2, index * 2 + 2)) {
                listOf(true, false) -> 0
                listOf(false, true) -> 1
                else -> error("Invalid RC5 Manchester bit.")
            }
        }
    }

    @Test
    fun allEncodedSignalsStayWithinAndroidPatternLimit() {
        val signals = listOf(
            IrProtocolEncoder.encode(IrProtocolCommand.Nec(0x00, 0x45)),
            IrProtocolEncoder.encode(IrProtocolCommand.NecExtended(0x1234, 0x45)),
            IrProtocolEncoder.encode(IrProtocolCommand.Samsung32(0xE0, 0x40)),
            IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x05, 0x35)),
            IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0x12, 0x65, true)),
            IrProtocolEncoder.encode(
                IrProtocolCommand.SonySirc(SonySircVariant.SIRC_12, 1, 19)
            ),
            IrProtocolEncoder.encode(
                IrProtocolCommand.SonySirc(SonySircVariant.SIRC_15, 0xA5, 0x35)
            ),
            IrProtocolEncoder.encode(
                IrProtocolCommand.SonySirc(SonySircVariant.SIRC_20, 0x12, 0x35, 0xC3)
            )
        )

        assertTrue(signals.all { it.totalDurationMicros < 2_000_000 })
        assertFalse(signals.any { IrSignalValidator.validate(it) != null })
    }
}
