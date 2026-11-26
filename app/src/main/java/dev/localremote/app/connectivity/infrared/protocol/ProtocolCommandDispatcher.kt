package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.connectivity.infrared.IrSignalValidator
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter

/**
 * Encodes one protocol command or validates one raw learned/imported waveform
 * and requests exactly one transmit. There is no retry loop and no inferred
 * appliance state.
 */
class ProtocolCommandDispatcher(
    private val transmitter: IrTransmitter
) {
    suspend fun dispatch(command: IrProtocolCommand): IrTransmitResult =
        dispatch(IrCommandSpec.Protocol(command))

    suspend fun dispatch(command: IrCommandSpec): IrTransmitResult {
        val signal = when (command) {
            is IrCommandSpec.Protocol -> try {
                IrProtocolEncoder.encode(command.command)
            } catch (invalid: IllegalArgumentException) {
                return IrTransmitResult.Rejected(
                    invalid.message ?: "The selected protocol parameters are invalid."
                )
            } catch (missing: IllegalStateException) {
                return IrTransmitResult.Rejected(
                    missing.message ?: "The selected protocol parameters are incomplete."
                )
            }

            is IrCommandSpec.Raw -> {
                IrSignalValidator.validate(command.signal)?.let { issue ->
                    return IrTransmitResult.Rejected(issue)
                }
                command.signal
            }
        }

        return transmitter.transmit(signal)
    }
}
