package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter
import java.time.LocalDateTime

/**
 * Sends one complete Daikin state frame. Every call is a state-setting
 * transmission; this dispatcher never retries automatically.
 *
 * A successful Sent result confirms Android accepted the transmission call,
 * not that the air conditioner received or applied the state.
 */
class DaikinAcCommandDispatcher(
    private val transmitter: IrTransmitter
) {
    suspend fun sendState(
        state: DaikinAcState,
        clock: LocalDateTime = LocalDateTime.now()
    ): IrTransmitResult {
        val signal = try {
            DaikinAcEncoder.encode(state, clock)
        } catch (invalid: IllegalArgumentException) {
            return IrTransmitResult.Rejected(
                invalid.message ?: "The requested Daikin state is invalid."
            )
        }

        return transmitter.transmit(signal)
    }
}
