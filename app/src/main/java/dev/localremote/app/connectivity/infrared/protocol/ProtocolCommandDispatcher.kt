package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter

/**
 * Encodes one verified profile command and requests one transmit.
 * This class deliberately has no retry loop and does not infer appliance state.
 */
class ProtocolCommandDispatcher(
    private val transmitter: IrTransmitter
) {
    suspend fun dispatch(command: IrProtocolCommand): IrTransmitResult {
        val signal = try {
            IrProtocolEncoder.encode(command)
        } catch (invalid: IllegalArgumentException) {
            return IrTransmitResult.Rejected(
                invalid.message ?: "The selected protocol parameters are invalid."
            )
        } catch (missing: IllegalStateException) {
            return IrTransmitResult.Rejected(
                missing.message ?: "The selected protocol parameters are incomplete."
            )
        }

        return transmitter.transmit(signal)
    }
}
