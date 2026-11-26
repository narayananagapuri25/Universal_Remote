package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrFrequencyRange
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.IrTransmitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaikinAcCommandDispatcherTest {

    @Test
    fun sendsOneCompleteStateSignal() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = DaikinAcCommandDispatcher(transmitter)

        val result = dispatcher.sendState(
            DaikinAcState(
                power = true,
                mode = DaikinAcMode.COOL,
                temperatureCelsius = 24.5f,
                fanSpeed = DaikinAcFanSpeed.AUTO
            )
        )

        assertTrue(result is IrTransmitResult.Sent)
        assertEquals(1, transmitter.transmitCount)
        assertEquals(38_000, transmitter.lastSignal?.carrierFrequencyHz)
        assertEquals(584, transmitter.lastSignal?.patternMicros?.size)
    }

    @Test
    fun rejectsInvalidTemperatureBeforeTransmitterIsCalled() = runBlocking {
        val transmitter = FakeTransmitter()
        val dispatcher = DaikinAcCommandDispatcher(transmitter)

        val result = dispatcher.sendState(
            DaikinAcState(
                mode = DaikinAcMode.COOL,
                temperatureCelsius = 16.0f
            )
        )

        assertTrue(result is IrTransmitResult.Rejected)
        assertEquals(0, transmitter.transmitCount)
        assertFalse(transmitter.transmitCalled)
    }

    private class FakeTransmitter : IrTransmitter {
        var transmitCount = 0
        var transmitCalled = false
        var lastSignal: IrSignal? = null

        override suspend fun inspect() = IrHardwareStatus(
            transmitterAvailable = true,
            supportedCarrierRanges = listOf(IrFrequencyRange(36_000, 40_000))
        )

        override suspend fun transmit(signal: IrSignal): IrTransmitResult {
            transmitCount++
            transmitCalled = true
            lastSignal = signal
            return IrTransmitResult.Sent(signal.totalDurationMicros)
        }
    }
}
