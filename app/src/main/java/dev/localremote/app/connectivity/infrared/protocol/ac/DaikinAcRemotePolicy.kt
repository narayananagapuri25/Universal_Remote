package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrHardwareStatus

/**
 * Single source of truth for deciding whether the experimental Daikin screen
 * is allowed to send. No network/device-model inference is performed here.
 */
object DaikinAcRemotePolicy {
    fun sendBlockReason(
        hardware: IrHardwareStatus?,
        variant: DaikinAcTransportVariant?,
        familyConfirmed: Boolean,
        fanSpeed: DaikinAcFanSpeed,
        verticalSwing: Boolean
    ): String? {
        if (variant == null) return "Select the transport family that matches your original remote."
        if (!familyConfirmed) return "Confirm that you checked the original remote/model marking."
        if (hardware == null) return "Phone IR capability is still being checked."
        if (!hardware.transmitterAvailable) return "No built-in IR transmitter is available on this phone."
        if (!hardware.supportsCarrierFrequency(38_000)) {
            return "This phone does not report support for the required 38 kHz carrier."
        }
        if (fanSpeed == DaikinAcFanSpeed.COMFORT) {
            if (variant != DaikinAcTransportVariant.THREE_FRAME) {
                return "Comfort fan is only supported by the three-frame transport."
            }
            if (verticalSwing) {
                return "Turn vertical swing off before using Comfort fan."
            }
        }
        return null
    }

    fun canSend(
        hardware: IrHardwareStatus?,
        variant: DaikinAcTransportVariant?,
        familyConfirmed: Boolean,
        fanSpeed: DaikinAcFanSpeed,
        verticalSwing: Boolean,
        sending: Boolean
    ): Boolean = !sending && sendBlockReason(
        hardware = hardware,
        variant = variant,
        familyConfirmed = familyConfirmed,
        fanSpeed = fanSpeed,
        verticalSwing = verticalSwing
    ) == null
}
