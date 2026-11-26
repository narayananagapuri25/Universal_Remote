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
            IrProtocolCommand.NecExtended(address = 0x1234, command = 0xA5)
        )

        assertEquals(38_000, signal.carrierFrequencyHz)
        assertEquals(67, signal.patternMicros.size)
        // Least significant address byte is 0x34; its first bit is zero.
        assertEquals(listOf(560, 560), signal.patternMicros.subList(2, 4))
        assertEquals(null, IrSignalValidator.validate(signal))
    }

    @Test
    fun necExtendedRejectsLegacyAddressEncodedAsInversePair() {
        val error = runCatching {
            IrProtocolEncoder.encode(
                IrProtocolCommand.NecExtended(address = 0x34CB, command = 0x10)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
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
        assertEquals(27, signal.patternMicros.size)
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

        assertEquals(33, sirc15.patternMicros.size)
        assertEquals(43, sirc20.patternMicros.size)
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
    fun allEncodedSignalsStayWithinAndroidPatternLimit() {
        val signals = listOf(
            IrProtocolEncoder.encode(IrProtocolCommand.Nec(0x00, 0x45)),
            IrProtocolEncoder.encode(IrProtocolCommand.NecExtended(0x1234, 0x45)),
            IrProtocolEncoder.encode(IrProtocolCommand.Samsung32(0xE0, 0x40)),
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
