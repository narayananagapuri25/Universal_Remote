package dev.localremote.app.connectivity.infrared.importexport

import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.protocol.IrCommandSpec
import dev.localremote.app.connectivity.infrared.protocol.IrProtocolCommand
import dev.localremote.app.connectivity.infrared.protocol.ProfileVerificationStatus
import dev.localremote.app.connectivity.infrared.protocol.SonySircVariant
import dev.localremote.app.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperIrFileCodecTest {

    private val metadata = FlipperIrFileCodec.ImportMetadata(
        profileId = "flipper-import",
        category = DeviceCategory.TELEVISION,
        brand = "User import",
        model = null,
        displayName = "Imported remote"
    )

    @Test
    fun importsParsedNecSamsungAndSonyCommandsAsUnverified() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 18 00 00 00
            command: 08 00 00 00
            #
            name: Mute
            type: parsed
            protocol: Samsung32
            address: 07 00 00 00
            command: 0F 00 00 00
            #
            name: Input
            type: parsed
            protocol: SIRC
            address: 01 00 00 00
            command: 15 00 00 00
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Success)
        val profile = (result as FlipperIrFileCodec.ImportResult.Success).profile

        assertEquals(3, profile.buttons.size)
        assertEquals(ProfileVerificationStatus.UNVERIFIED, profile.buttons.first().evidence.status)
        assertEquals(
            IrProtocolCommand.Nec(0x18, 0x08),
            (profile.buttons[0].command as IrCommandSpec.Protocol).command
        )
        assertEquals(
            IrProtocolCommand.Samsung32(0x07, 0x0F),
            (profile.buttons[1].command as IrCommandSpec.Protocol).command
        )
        assertEquals(
            IrProtocolCommand.SonySirc(SonySircVariant.SIRC_12, 1, 0x15),
            (profile.buttons[2].command as IrCommandSpec.Protocol).command
        )
    }

    @Test
    fun importsNecExtendedAddressAndCommandAsIndependent16BitValues() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NECext
            address: EE 87 00 00
            command: 5D A0 00 00
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Success)
        val command = ((result as FlipperIrFileCodec.ImportResult.Success)
            .profile.buttons.single().command as IrCommandSpec.Protocol).command

        assertEquals(IrProtocolCommand.NecExtended(0x87EE, 0xA05D), command)
    }

    @Test
    fun importsSonySirc20Packed13BitAddress() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: SIRC20
            address: A5 12 00 00
            command: 35 00 00 00
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Success)
        val command = ((result as FlipperIrFileCodec.ImportResult.Success)
            .profile.buttons.single().command as IrCommandSpec.Protocol).command

        assertEquals(
            IrProtocolCommand.SonySirc(
                variant = SonySircVariant.SIRC_20,
                address = 0x05,
                command = 0x35,
                extendedData = 0x12A5 ushr 5
            ),
            command
        )
    }

    @Test
    fun importsRawWaveformAndPreservesDutyCycleMetadata() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: raw
            frequency: 38000
            duty_cycle: 0.500000
            data: 2100 700 700 700 700 700 700 1400
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Success)
        val signal = ((result as FlipperIrFileCodec.ImportResult.Success)
            .profile.buttons.single().command as IrCommandSpec.Raw).signal

        assertEquals(38_000, signal.carrierFrequencyHz)
        assertEquals(0.5f, signal.dutyCycle)
        assertEquals(listOf(2100, 700, 700, 700, 700, 700, 700, 1400), signal.patternMicros)
    }


    @Test
    fun importsAndExportsParsedRc5AndRc5xCommands() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: RC5
            address: 05 00 00 00
            command: 35 00 00 00
            #
            name: Guide
            type: parsed
            protocol: RC5X
            address: 12 00 00 00
            command: 65 00 00 00
        """.trimIndent()
        val imported = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(imported is FlipperIrFileCodec.ImportResult.Success)
        val profile = (imported as FlipperIrFileCodec.ImportResult.Success).profile
        assertEquals(IrProtocolCommand.PhilipsRc5(0x05, 0x35),
            (profile.buttons[0].command as IrCommandSpec.Protocol).command)
        assertEquals(IrProtocolCommand.PhilipsRc5(0x12, 0x65, true),
            (profile.buttons[1].command as IrCommandSpec.Protocol).command)

        val exported = FlipperIrFileCodec.exportFile(profile)
        assertTrue(exported is FlipperIrFileCodec.ExportResult.Success)
        val output = (exported as FlipperIrFileCodec.ExportResult.Success).content
        assertTrue(output.contains("protocol: RC5\n"))
        assertTrue(output.contains("protocol: RC5X\n"))
    }

    @Test
    fun rejectsUnknownParsedProtocolInsteadOfGuessing() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: Kaseikyo
            address: 01 00 00 00
            command: 01 00 00 00
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Failure)
        assertTrue((result as FlipperIrFileCodec.ImportResult.Failure).errors
            .any { it.contains("not supported yet") })
    }

    @Test
    fun rejectsDuplicateFieldsAndUnknownKeys() {
        val duplicate = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            protocol: NEC
            address: 18 00 00 00
            command: 08 00 00 00
        """.trimIndent()

        val unknown = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 18 00 00 00
            command: 08 00 00 00
            executable: true
        """.trimIndent()

        assertTrue(FlipperIrFileCodec.importFile(duplicate, metadata) is FlipperIrFileCodec.ImportResult.Failure)
        assertTrue(FlipperIrFileCodec.importFile(unknown, metadata) is FlipperIrFileCodec.ImportResult.Failure)
    }

    @Test
    fun rejectsRawTimingCountOverFlipperLimit() {
        val data = List(FlipperIrFileCodec.MAX_RAW_TIMINGS + 1) { "500" }.joinToString(" ")
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: $data
        """.trimIndent()

        val result = FlipperIrFileCodec.importFile(content, metadata)
        assertTrue(result is FlipperIrFileCodec.ImportResult.Failure)
    }

    @Test
    fun roundTripsSupportedParsedAndRawCommands() {
        val source = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NECext
            address: EE 87 00 00
            command: 5D A0 00 00
            #
            name: Learned
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 900 450 560 560 560
        """.trimIndent()

        val imported = FlipperIrFileCodec.importFile(source, metadata)
        assertTrue(imported is FlipperIrFileCodec.ImportResult.Success)
        val profile = (imported as FlipperIrFileCodec.ImportResult.Success).profile

        val exported = FlipperIrFileCodec.exportFile(profile)
        assertTrue(exported is FlipperIrFileCodec.ExportResult.Success)
        val file = (exported as FlipperIrFileCodec.ExportResult.Success).content
        val roundTripped = FlipperIrFileCodec.importFile(file, metadata)
        assertTrue(roundTripped is FlipperIrFileCodec.ImportResult.Success)

        val commands = (roundTripped as FlipperIrFileCodec.ImportResult.Success).profile.buttons
        assertEquals(
            IrProtocolCommand.NecExtended(0x87EE, 0xA05D),
            (commands[0].command as IrCommandSpec.Protocol).command
        )
        val raw = (commands[1].command as IrCommandSpec.Raw).signal
        assertEquals(0.33f, raw.dutyCycle)
        assertEquals(listOf(900, 450, 560, 560, 560), raw.patternMicros)
    }

    @Test
    fun rejectsFileLargerThanInputLimit() {
        val oversized = "x".repeat(FlipperIrFileCodec.MAX_FILE_CHARS + 1)
        assertTrue(FlipperIrFileCodec.importFile(oversized, metadata) is FlipperIrFileCodec.ImportResult.Failure)
    }

    @Test
    fun rejectsNonAsciiOrOverlongButtonName() {
        val content = """
            Filetype: IR signals file
            Version: 1
            #
            name: 开灯
            type: parsed
            protocol: NEC
            address: 18 00 00 00
            command: 08 00 00 00
        """.trimIndent()

        assertTrue(FlipperIrFileCodec.importFile(content, metadata) is FlipperIrFileCodec.ImportResult.Failure)
    }
}
