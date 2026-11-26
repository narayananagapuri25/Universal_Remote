package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.protocol.IrCommandSpec
import dev.localremote.app.connectivity.infrared.protocol.IrProtocolCommand
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteButton
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfile
import dev.localremote.app.connectivity.infrared.protocol.SonySircVariant
import dev.localremote.app.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrRemoteProfileMapperTest {

    @Test
    fun mapsSupportedProtocolAndRawButtonsToAndFromRoomRecords() {
        val profile = IrRemoteProfile(
            profileId = "test-profile",
            category = DeviceCategory.TELEVISION,
            brand = "Example",
            model = "Model-X",
            displayName = "Example Living Room TV",
            buttons = listOf(
                protocolButton("nec", IrProtocolCommand.Nec(0x18, 0x08)),
                protocolButton("nec-ext", IrProtocolCommand.NecExtended(0x87EE, 0xA05D)),
                protocolButton("samsung", IrProtocolCommand.Samsung32(0x07, 0x0F)),
                protocolButton("rc5", IrProtocolCommand.PhilipsRc5(0x05, 0x35)),
                protocolButton("rc5x", IrProtocolCommand.PhilipsRc5(0x12, 0x65, true)),
                protocolButton(
                    "sirc12",
                    IrProtocolCommand.SonySirc(SonySircVariant.SIRC_12, 1, 0x15)
                ),
                protocolButton(
                    "sirc15",
                    IrProtocolCommand.SonySirc(SonySircVariant.SIRC_15, 0xA5, 0x35)
                ),
                protocolButton(
                    "sirc20",
                    IrProtocolCommand.SonySirc(SonySircVariant.SIRC_20, 5, 0x35, 0x95)
                ),
                IrRemoteButton(
                    id = "raw",
                    label = "Learned",
                    command = IrCommandSpec.Raw(
                        IrSignal(38_000, listOf(900, 450, 560, 560, 560), 0.5f)
                    )
                )
            )
        )

        val records = IrRemoteProfileMapper.toRecords(profile, createdAtEpochMillis = 1234L)
        val restored = IrRemoteProfileMapper.fromRecords(
            IrRemoteProfileWithButtons(records.profile, records.buttons)
        )

        assertEquals(profile.profileId, restored.profileId)
        assertEquals(profile.category, restored.category)
        assertEquals(profile.brand, restored.brand)
        assertEquals(profile.model, restored.model)
        assertEquals(profile.displayName, restored.displayName)
        assertEquals(profile.buttons.map { it.id }, restored.buttons.map { it.id })

        profile.buttons.dropLast(1).zip(restored.buttons.dropLast(1)).forEach { (original, saved) ->
            assertEquals(original.label, saved.label)
            assertEquals(original.command, saved.command)
        }

        val originalRaw = (profile.buttons.last().command as IrCommandSpec.Raw).signal
        val restoredRaw = (restored.buttons.last().command as IrCommandSpec.Raw).signal
        assertEquals(originalRaw.carrierFrequencyHz, restoredRaw.carrierFrequencyHz)
        assertEquals(originalRaw.dutyCycle, restoredRaw.dutyCycle)
        assertEquals(originalRaw.patternMicros, restoredRaw.patternMicros)
    }

    @Test
    fun mapperRejectsInvalidProfileBeforePersistence() {
        val duplicate = protocolButton("power", IrProtocolCommand.Nec(1, 2))
        val profile = IrRemoteProfile(
            profileId = "invalid-profile",
            category = DeviceCategory.TELEVISION,
            brand = "Example",
            model = null,
            displayName = "Invalid",
            buttons = listOf(duplicate, duplicate.copy(label = "Duplicate"))
        )

        val failure = runCatching {
            IrRemoteProfileMapper.toRecords(profile, 1L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun protocolButton(
        id: String,
        command: IrProtocolCommand
    ) = IrRemoteButton(
        id = id,
        label = id,
        command = IrCommandSpec.Protocol(command)
    )
}
