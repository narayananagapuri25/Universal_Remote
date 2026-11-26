package dev.localremote.app.platform.infrared

import dev.localremote.app.connectivity.infrared.IrFrequencyRange
import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInIrTransmitterTest {

    @Test
    fun inspectionReportsNoBuiltInEmitter() = runBlocking {
        val driver = FakeConsumerIrDriver(hasEmitter = false)
        val status = BuiltInIrTransmitter(driver, Dispatchers.Unconfined).inspect()

        assertFalse(status.transmitterAvailable)
        assertTrue(status.supportedCarrierRanges.isEmpty())
    }

    @Test
    fun inspectionReportsHardwareAndSupportedRanges() = runBlocking {
        val driver = FakeConsumerIrDriver(
            hasEmitter = true,
            ranges = listOf(IrFrequencyRange(30_000, 40_000))
        )
        val status = BuiltInIrTransmitter(driver, Dispatchers.Unconfined).inspect()

        assertTrue(status.transmitterAvailable)
        assertTrue(status.supportsCarrierFrequency(38_000))
    }

    @Test
    fun refusesTransmissionWhenNoEmitterExists() = runBlocking {
        val driver = FakeConsumerIrDriver(hasEmitter = false)
        val result = BuiltInIrTransmitter(driver, Dispatchers.Unconfined)
            .transmit(validSignal())

        assertEquals(IrTransmitResult.NoTransmitter, result)
        assertFalse(driver.transmitCalled)
    }

    @Test
    fun refusesFrequencyOutsideHardwareRange() = runBlocking {
        val driver = FakeConsumerIrDriver(
            hasEmitter = true,
            ranges = listOf(IrFrequencyRange(36_000, 40_000))
        )
        val result = BuiltInIrTransmitter(driver, Dispatchers.Unconfined)
            .transmit(validSignal(carrierFrequencyHz = 56_000))

        assertTrue(result is IrTransmitResult.UnsupportedFrequency)
        assertFalse(driver.transmitCalled)
    }

    @Test
    fun refusesTransmissionWhenPlatformDoesNotReportFrequencyRanges() = runBlocking {
        val driver = FakeConsumerIrDriver(hasEmitter = true, ranges = null)
        val result = BuiltInIrTransmitter(driver, Dispatchers.Unconfined)
            .transmit(validSignal())

        assertTrue(result is IrTransmitResult.Failed)
        assertFalse(driver.transmitCalled)
    }

    @Test
    fun sendsOnlyWhenSignalAndHardwareAreValid() = runBlocking {
        val driver = FakeConsumerIrDriver(
            hasEmitter = true,
            ranges = listOf(IrFrequencyRange(36_000, 40_000))
        )
        val signal = validSignal()
        val result = BuiltInIrTransmitter(driver, Dispatchers.Unconfined).transmit(signal)

        assertEquals(IrTransmitResult.Sent(signal.totalDurationMicros), result)
        assertTrue(driver.transmitCalled)
        assertEquals(38_000, driver.lastCarrierFrequencyHz)
        assertEquals(signal.patternMicros.toIntArray().toList(), driver.lastPattern?.toList())
    }

    @Test
    fun malformedSignalIsRejectedBeforeHardwareAccess() = runBlocking {
        val driver = FakeConsumerIrDriver(
            hasEmitter = true,
            ranges = listOf(IrFrequencyRange(36_000, 40_000))
        )
        val result = BuiltInIrTransmitter(driver, Dispatchers.Unconfined)
            .transmit(IrSignal(38_000, listOf(100, 0, 100)))

        assertTrue(result is IrTransmitResult.Rejected)
        assertFalse(driver.transmitCalled)
        assertFalse(driver.hasEmitterCalled)
    }

    private fun validSignal(carrierFrequencyHz: Int = 38_000) = IrSignal(
        carrierFrequencyHz = carrierFrequencyHz,
        patternMicros = listOf(9_000, 4_500, 560, 560)
    )

    private class FakeConsumerIrDriver(
        private val hasEmitter: Boolean,
        private val ranges: List<IrFrequencyRange>? = emptyList()
    ) : ConsumerIrDriver {
        var hasEmitterCalled = false
        var transmitCalled = false
        var lastCarrierFrequencyHz: Int? = null
        var lastPattern: IntArray? = null

        override fun hasEmitter(): Boolean {
            hasEmitterCalled = true
            return hasEmitter
        }

        override fun getCarrierFrequencies(): List<IrFrequencyRange>? = ranges

        override fun transmit(carrierFrequencyHz: Int, patternMicros: IntArray) {
            transmitCalled = true
            lastCarrierFrequencyHz = carrierFrequencyHz
            lastPattern = patternMicros
        }
    }
}
