package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.core.model.DeviceCategory
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class IrRemoteProfileValidatorTest {

    @Test
    fun acceptsUnverifiedCustomProfileWithoutInventingCompatibility() {
        val profile = profile(
            evidence = IrCommandEvidence(
                status = ProfileVerificationStatus.UNVERIFIED
            )
        )

        assertTrue(IrRemoteProfileValidator.validate(profile).isEmpty())
    }

    @Test
    fun rejectsProtocolVerifiedButtonWithoutSourceReference() {
        val profile = profile(
            evidence = IrCommandEvidence(
                status = ProfileVerificationStatus.PROTOCOL_VERIFIED
            )
        )

        assertTrue(
            IrRemoteProfileValidator.validate(profile)
                .any { it.contains("no source reference") }
        )
    }

    @Test
    fun acceptsProtocolVerifiedButtonWithEvidenceReference() {
        val profile = profile(
            evidence = IrCommandEvidence(
                status = ProfileVerificationStatus.PROTOCOL_VERIFIED,
                sourceReference = "NEC protocol timing reference"
            )
        )

        assertTrue(IrRemoteProfileValidator.validate(profile).isEmpty())
    }

    @Test
    fun physicallyTestedButtonRequiresTestRecord() {
        val profile = profile(
            evidence = IrCommandEvidence(
                status = ProfileVerificationStatus.PHYSICALLY_TESTED,
                sourceReference = "Original remote capture"
            )
        )

        assertTrue(
            IrRemoteProfileValidator.validate(profile)
                .any { it.contains("no physical test record") }
        )
    }

    @Test
    fun acceptsPhysicallyTestedButtonWithEvidenceAndRecord() {
        val profile = profile(
            evidence = IrCommandEvidence(
                status = ProfileVerificationStatus.PHYSICALLY_TESTED,
                sourceReference = "Original remote capture",
                sourceDataLicenseSpdx = "CC0-1.0",
                physicalTestRecordId = "test-device-2026-001"
            )
        )

        assertTrue(IrRemoteProfileValidator.validate(profile).isEmpty())
    }

    @Test
    fun acceptsValidRawWaveformButton() {
        val profile = IrRemoteProfile(
            profileId = "custom-ir",
            category = DeviceCategory.TELEVISION,
            brand = "User created",
            model = null,
            displayName = "Custom TV Remote",
            buttons = listOf(
                IrRemoteButton(
                    id = "power",
                    label = "Power",
                    command = IrCommandSpec.Raw(
                        dev.localremote.app.connectivity.infrared.IrSignal(
                            38_000,
                            listOf(900, 450, 560, 560, 560)
                        )
                    )
                )
            )
        )

        assertTrue(IrRemoteProfileValidator.validate(profile).isEmpty())
    }

    @Test
    fun rejectsMalformedRawWaveformButton() {
        val profile = IrRemoteProfile(
            profileId = "custom-ir",
            category = DeviceCategory.TELEVISION,
            brand = "User created",
            model = null,
            displayName = "Custom TV Remote",
            buttons = listOf(
                IrRemoteButton(
                    id = "power",
                    label = "Power",
                    command = IrCommandSpec.Raw(
                        dev.localremote.app.connectivity.infrared.IrSignal(
                            38_000,
                            listOf(900, 0, 560)
                        )
                    )
                )
            )
        )

        assertTrue(
            IrRemoteProfileValidator.validate(profile)
                .any { it.contains("invalid raw IR signal") }
        )
    }

    @Test
    fun rejectsDuplicateButtonIds() {
        val button = IrRemoteButton(
            id = "power",
            label = "Power",
            command = IrCommandSpec.Protocol(IrProtocolCommand.Nec(address = 1, command = 2))
        )
        val profile = IrRemoteProfile(
            profileId = "test-tv",
            category = DeviceCategory.TELEVISION,
            brand = "Example",
            model = "Model A",
            displayName = "Example TV",
            buttons = listOf(button, button.copy(label = "Power alternate"))
        )

        assertFalse(IrRemoteProfileValidator.validate(profile).isEmpty())
        assertTrue(
            IrRemoteProfileValidator.validate(profile)
                .any { it.contains("Button IDs must be unique") }
        )
    }

    private fun profile(evidence: IrCommandEvidence): IrRemoteProfile =
        IrRemoteProfile(
            profileId = "test-tv",
            category = DeviceCategory.TELEVISION,
            brand = "Example",
            model = "Model A",
            displayName = "Example TV",
            buttons = listOf(
                IrRemoteButton(
                    id = "power",
                    label = "Power",
                    command = IrCommandSpec.Protocol(
                        IrProtocolCommand.Nec(address = 1, command = 2)
                    ),
                    evidence = evidence
                )
            )
        )
}
