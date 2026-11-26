package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.connectivity.infrared.IrFrequencyRange
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCommandDispatcherTest {

    @Test
    fun dispatchEncodesAndSendsExactlyOnce() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = ProtocolCommandDispatcher(transmitter)

        val result = dispatcher.dispatch(
            IrProtocolCommand.Nec(address = 0x00, command = 0x45)
        )

        assertEquals(IrTransmitResult.Sent(67_980), result)
        assertEquals(1, transmitter.transmitCount)
        assertNotNull(transmitter.lastSignal)
        assertEquals(38_000, transmitter.lastSignal?.carrierFrequencyHz)
    }

    @Test
    fun rawWaveformIsValidatedAndDispatched() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = ProtocolCommandDispatcher(transmitter)
        val raw = IrCommandSpec.Raw(
            dev.localremote.app.connectivity.infrared.IrSignal(
                38_000,
                listOf(900, 450, 560, 560, 560)
            )
        )

        val result = dispatcher.dispatch(raw)

        assertEquals(IrTransmitResult.Sent(3_030), result)
        assertEquals(1, transmitter.transmitCount)
        assertEquals(raw.signal, transmitter.lastSignal)
    }

    @Test
    fun invalidRawWaveformIsRejectedBeforeTransmission() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = ProtocolCommandDispatcher(transmitter)
        val raw = IrCommandSpec.Raw(
            dev.localremote.app.connectivity.infrared.IrSignal(
                38_000,
                listOf(900, 0, 560)
            )
        )

        val result = dispatcher.dispatch(raw)

        assertTrue(result is IrTransmitResult.Rejected)
        assertEquals(0, transmitter.transmitCount)
    }

    @Test
    fun invalidProtocolParametersAreRejectedBeforeTransmission() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = ProtocolCommandDispatcher(transmitter)

        val result = dispatcher.dispatch(
            IrProtocolCommand.Nec(address = 0x1_0000, command = 0x45)
        )

        assertFalse(result is IrTransmitResult.Sent)
        assertEquals(0, transmitter.transmitCount)
    }

    private class FakeTransmitter : IrTransmitter {
        var transmitCount = 0
        var lastSignal: IrSignal? = null

        override suspend fun inspect() = IrHardwareStatus(
            transmitterAvailable = true,
            supportedCarrierRanges = listOf(IrFrequencyRange(36_000, 40_000))
        )

        override suspend fun transmit(signal: IrSignal): IrTransmitResult {
            transmitCount++
            lastSignal = signal
            return IrTransmitResult.Sent(signal.totalDurationMicros)
        }
    }
}
