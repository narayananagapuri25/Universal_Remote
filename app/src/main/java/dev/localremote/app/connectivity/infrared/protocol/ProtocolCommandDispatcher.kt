package dev.localremote.app.connectivity.infrared.protocol

import android.content.SharedPreferences
import dev.localremote.app.connectivity.infrared.IrSignalValidator
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Encodes one protocol command or validates one raw waveform and requests
 * exactly one transmit. There is no retry loop and no inferred appliance state.
 *
 * RC5/RC5X toggle state is committed before dispatch and scoped to a saved
 * profile. This preserves sequence state across app restarts and uncertain
 * transmit outcomes.
 */
class ProtocolCommandDispatcher(
    private val transmitter: IrTransmitter,
    private val togglePreferences: SharedPreferences? = null
) {
    private val dispatchLock = Mutex()
    private val transientToggleState = mutableMapOf<String, Int>()

    suspend fun dispatch(command: IrProtocolCommand): IrTransmitResult =
        dispatch(DEFAULT_PROFILE_ID, IrCommandSpec.Protocol(command))

    suspend fun dispatch(command: IrCommandSpec): IrTransmitResult =
        dispatch(DEFAULT_PROFILE_ID, command)

    suspend fun dispatch(profileId: String, command: IrCommandSpec): IrTransmitResult =
        dispatchLock.withLock {
            val stableProfileId = profileId.ifBlank { DEFAULT_PROFILE_ID }
            var rc5Toggle: Int? = null
            val signal = when (command) {
                is IrCommandSpec.Protocol -> try {
                    val rc5 = command.command as? IrProtocolCommand.PhilipsRc5
                    val toggle = if (rc5 != null) currentRc5Toggle(stableProfileId) else 0
                    rc5Toggle = if (rc5 != null) toggle else null
                    IrProtocolEncoder.encode(command.command, toggle)
                } catch (invalid: IllegalArgumentException) {
                    return@withLock IrTransmitResult.Rejected(
                        invalid.message ?: "The selected protocol parameters are invalid."
                    )
                } catch (missing: IllegalStateException) {
                    return@withLock IrTransmitResult.Rejected(
                        missing.message ?: "The selected protocol parameters are incomplete."
                    )
                }

                is IrCommandSpec.Raw -> {
                    IrSignalValidator.validate(command.signal)?.let { issue ->
                        return@withLock IrTransmitResult.Rejected(issue)
                    }
                    command.signal
                }
            }

            if (rc5Toggle != null && !advanceRc5Toggle(stableProfileId, rc5Toggle!!)) {
                return@withLock IrTransmitResult.Rejected(
                    "OneRemote could not save the RC5 key sequence state. No signal was sent."
                )
            }
            transmitter.transmit(signal)
        }

    private fun currentRc5Toggle(profileId: String): Int {
        val key = RC5_TOGGLE_PREFIX + profileId
        return togglePreferences?.getInt(key, 0) ?: transientToggleState[profileId] ?: 0
    }

    private fun advanceRc5Toggle(profileId: String, current: Int): Boolean {
        val next = current xor 1
        val key = RC5_TOGGLE_PREFIX + profileId
        val preferences = togglePreferences
        if (preferences != null) return preferences.edit().putInt(key, next).commit()
        transientToggleState[profileId] = next
        return true
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = "default"
        const val RC5_TOGGLE_PREFIX = "rc5_toggle:"
    }
}
