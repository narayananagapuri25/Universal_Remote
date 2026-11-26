package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrFrequencyRange
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaikinAcRemotePolicyTest {
    private val capablePhone = IrHardwareStatus(
        transmitterAvailable = true,
        supportedCarrierRanges = listOf(IrFrequencyRange(36_000, 40_000))
    )

    @Test
    fun sendBlockedUntilFamilyAndOriginalRemoteAreConfirmed() {
        assertEquals(
            "Select the transport family that matches your original remote.",
            DaikinAcRemotePolicy.sendBlockReason(
                capablePhone, null, true, DaikinAcFanSpeed.AUTO, false
            )
        )
        assertEquals(
            "Confirm that you checked the original remote/model marking.",
            DaikinAcRemotePolicy.sendBlockReason(
                capablePhone, DaikinAcTransportVariant.THREE_FRAME, false,
                DaikinAcFanSpeed.AUTO, false
            )
        )
    }

    @Test
    fun sendRequiresBuiltInTransmitterAndReported38KhzSupport() {
        val noEmitter = IrHardwareStatus(false, emptyList())
        assertFalse(
            DaikinAcRemotePolicy.canSend(
                noEmitter, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.AUTO, false, false
            )
        )

        val no38Khz = IrHardwareStatus(
            true,
            listOf(IrFrequencyRange(40_000, 42_000))
        )
        assertEquals(
            "This phone does not report support for the required 38 kHz carrier.",
            DaikinAcRemotePolicy.sendBlockReason(
                no38Khz, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.AUTO, false
            )
        )
    }

    @Test
    fun comfortFanRequiresThreeFrameTransportAndVerticalSwingOff() {
        assertEquals(
            "Comfort fan is only supported by the three-frame transport.",
            DaikinAcRemotePolicy.sendBlockReason(
                capablePhone, DaikinAcTransportVariant.ONE_FRAME, true,
                DaikinAcFanSpeed.COMFORT, false
            )
        )
        assertEquals(
            "Turn vertical swing off before using Comfort fan.",
            DaikinAcRemotePolicy.sendBlockReason(
                capablePhone, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.COMFORT, true
            )
        )
        assertNull(
            DaikinAcRemotePolicy.sendBlockReason(
                capablePhone, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.COMFORT, false
            )
        )
    }

    @Test
    fun eligibleConfigurationCanSendOnlyWhenNotAlreadySending() {
        assertTrue(
            DaikinAcRemotePolicy.canSend(
                capablePhone, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.AUTO, false, false
            )
        )
        assertFalse(
            DaikinAcRemotePolicy.canSend(
                capablePhone, DaikinAcTransportVariant.THREE_FRAME, true,
                DaikinAcFanSpeed.AUTO, false, true
            )
        )
    }
}
