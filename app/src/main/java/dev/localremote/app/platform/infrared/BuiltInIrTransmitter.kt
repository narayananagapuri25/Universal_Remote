package dev.localremote.app.platform.infrared

import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrSignalValidator
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BuiltInIrTransmitter(
    private val driver: ConsumerIrDriver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IrTransmitter {
    private val transmitMutex = Mutex()

    override suspend fun inspect(): IrHardwareStatus = withContext(ioDispatcher) {
        try {
            if (!driver.hasEmitter()) {
                IrHardwareStatus(
                    transmitterAvailable = false,
                    supportedCarrierRanges = emptyList(),
                    diagnostic = "This phone does not expose a built-in consumer IR transmitter."
                )
            } else {
                val ranges = driver.getCarrierFrequencies().orEmpty()
                IrHardwareStatus(
                    transmitterAvailable = true,
                    supportedCarrierRanges = ranges,
                    diagnostic = if (ranges.isEmpty()) {
                        "IR hardware was detected, but Android did not provide supported carrier-frequency ranges."
                    } else {
                        null
                    }
                )
            }
        } catch (security: SecurityException) {
            IrHardwareStatus(
                transmitterAvailable = false,
                supportedCarrierRanges = emptyList(),
                diagnostic = "Android denied access to the consumer IR service."
            )
        } catch (failure: RuntimeException) {
            IrHardwareStatus(
                transmitterAvailable = false,
                supportedCarrierRanges = emptyList(),
                diagnostic = "Consumer IR capability check failed: ${failure.message ?: "unknown platform error"}"
            )
        }
    }

    override suspend fun transmit(signal: IrSignal): IrTransmitResult =
        withContext(ioDispatcher) {
            IrSignalValidator.validate(signal)?.let {
                return@withContext IrTransmitResult.Rejected(it)
            }

            transmitMutex.withLock {
                try {
                    if (!driver.hasEmitter()) {
                        return@withLock IrTransmitResult.NoTransmitter
                    }

                    val ranges = driver.getCarrierFrequencies().orEmpty()
                    if (ranges.isEmpty()) {
                        return@withLock IrTransmitResult.Failed(
                            "Android did not report supported IR carrier frequencies; transmission was blocked."
                        )
                    }

                    if (ranges.none { it.contains(signal.carrierFrequencyHz) }) {
                        return@withLock IrTransmitResult.UnsupportedFrequency(
                            requestedHz = signal.carrierFrequencyHz,
                            supportedRanges = ranges
                        )
                    }

                    driver.transmit(
                        carrierFrequencyHz = signal.carrierFrequencyHz,
                        patternMicros = signal.patternMicros.toIntArray()
                    )
                    IrTransmitResult.Sent(signal.totalDurationMicros)
                } catch (security: SecurityException) {
                    IrTransmitResult.Failed("Android denied access to the consumer IR service.")
                } catch (failure: IllegalArgumentException) {
                    IrTransmitResult.Rejected(
                        failure.message ?: "Android rejected the IR signal parameters."
                    )
                } catch (failure: RuntimeException) {
                    IrTransmitResult.Failed(
                        failure.message ?: "The consumer IR transmitter failed."
                    )
                }
            }
        }
}
