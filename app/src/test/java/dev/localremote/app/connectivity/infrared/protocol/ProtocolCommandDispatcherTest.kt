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


    @Test
    fun rc5ToggleAlternatesPerSavedProfileAndAcrossDifferentButtons() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = ProtocolCommandDispatcher(transmitter)
        val power = IrCommandSpec.Protocol(IrProtocolCommand.PhilipsRc5(0, 0x0C))
        val volume = IrCommandSpec.Protocol(IrProtocolCommand.PhilipsRc5(0, 0x10))
        dispatcher.dispatch("living-room-tv", power)
        dispatcher.dispatch("living-room-tv", volume)
        dispatcher.dispatch("bedroom-tv", power)

        assertEquals(3, transmitter.transmittedSignals.size)
        assertEquals(IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0, 0x0C), 0).patternMicros,
            transmitter.transmittedSignals[0].patternMicros)
        assertEquals(IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0, 0x10), 1).patternMicros,
            transmitter.transmittedSignals[1].patternMicros)
        assertEquals(IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0, 0x0C), 0).patternMicros,
            transmitter.transmittedSignals[2].patternMicros)
    }

    @Test
    fun rc5ToggleAdvancesAfterUncertainTransmitFailure() = runBlocking {
        val transmitter = FakeTransmitter().apply { nextResult = IrTransmitResult.Failed("simulated") }
        val dispatcher = ProtocolCommandDispatcher(transmitter)
        val command = IrCommandSpec.Protocol(IrProtocolCommand.PhilipsRc5(0, 0x0C))
        dispatcher.dispatch("living-room-tv", command)
        transmitter.nextResult = IrTransmitResult.Sent(1)
        dispatcher.dispatch("living-room-tv", command)
        assertEquals(IrProtocolEncoder.encode(IrProtocolCommand.PhilipsRc5(0, 0x0C), 1).patternMicros,
            transmitter.transmittedSignals[1].patternMicros)
    }

    private class FakeTransmitter : IrTransmitter {
        var transmitCount = 0
        var lastSignal: IrSignal? = null
        val transmittedSignals = mutableListOf<IrSignal>()
        var nextResult: IrTransmitResult? = null

        override suspend fun inspect() = IrHardwareStatus(
            transmitterAvailable = true,
            supportedCarrierRanges = listOf(IrFrequencyRange(36_000, 40_000))
        )

        override suspend fun transmit(signal: IrSignal): IrTransmitResult {
            transmitCount++
            lastSignal = signal
            transmittedSignals += signal
            return nextResult ?: IrTransmitResult.Sent(signal.totalDurationMicros)
        }
    }
}
