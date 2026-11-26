package dev.localremote.app.connectivity.infrared.protocol.ac

import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrSignalValidator
import java.time.LocalDateTime

/**
 * Independent Kotlin implementation of the documented/reverse-engineered
 * Daikin ARC466A9 (three-frame) and ARC480A11 (one-frame) IR formats.
 *
 * Protocol reference details and provenance are recorded in
 * docs/AC_PROTOCOL_IMPLEMENTATION.md. No third-party source code is copied.
 */
object DaikinAcEncoder {
    const val CARRIER_FREQUENCY_HZ = 38_000

    private const val MARK_US = 454
    private const val ZERO_SPACE_US = 413
    private const val ONE_SPACE_US = 1_280
    private const val LEADER_MARK_US = 3_500
    private const val LEADER_SPACE_US = 1_700
    private const val PRE_LEADER_GAP_US = 25_000
    private const val INTER_FRAME_GAP_US = 35_000
    private const val TRAILING_GAP_US = 30_000

    private const val HEADER_0 = 0x11
    private const val HEADER_1 = 0xDA
    private const val HEADER_2 = 0x27

    private const val THREE_FRAME_TIMER_DISABLED = 0x600

    fun encode(
        state: DaikinAcState,
        clock: LocalDateTime = LocalDateTime.now()
    ): IrSignal {
        validateState(state)

        val stateFrame = buildStateFrame(state)
        val timings = when (state.variant) {
            DaikinAcTransportVariant.THREE_FRAME -> buildThreeFrameTimings(
                stateFrame = stateFrame,
                clockFrame = buildClockFrame(clock),
                identityFrame = buildIdentityFrame(
                    comfortFan = state.fanSpeed == DaikinAcFanSpeed.COMFORT &&
                        !state.powerful
                )
            )
            DaikinAcTransportVariant.ONE_FRAME -> buildOneFrameTimings(stateFrame)
        }

        val signal = IrSignal(
            carrierFrequencyHz = CARRIER_FREQUENCY_HZ,
            patternMicros = timings
        )
        val validation = IrSignalValidator.validate(signal)
        require(validation == null) {
            "Encoded Daikin signal failed waveform validation: $validation"
        }
        return signal
    }

    internal fun buildStateFrame(state: DaikinAcState): ByteArray {
        validateState(state)

        val isThreeFrame = state.variant == DaikinAcTransportVariant.THREE_FRAME
        val effectiveFan = if (state.powerful && state.fanSpeed == DaikinAcFanSpeed.COMFORT) {
            DaikinAcFanSpeed.AUTO
        } else {
            state.fanSpeed
        }
        val effectiveEcono = state.econo && !state.powerful
        val effectiveOutdoorQuiet = state.outdoorQuiet && !state.powerful

        val bytes = ByteArray(19)
        bytes[0] = HEADER_0.toByte()
        bytes[1] = HEADER_1.toByte()
        bytes[2] = HEADER_2.toByte()
        bytes[3] = 0x00
        bytes[4] = 0x00

        // Byte 5: mode nibble, transport bit, timer enable bits (not set yet), power.
        bytes[5] = (
            (state.mode.wireCode shl 4) or
                (if (isThreeFrame) 0x08 else 0x00) or
                (if (state.power) 0x01 else 0x00)
            ).toByte()

        // DRY has a fixed no-setpoint marker. Other supported modes carry °C × 2.
        val temperatureWire = if (state.mode == DaikinAcMode.DRY) {
            0xC0
        } else {
            (state.temperatureCelsius * 2f).toInt()
        }
        bytes[6] = temperatureWire.toByte()

        // Byte 8: fan nibble and vertical-swing nibble.
        bytes[8] = (
            (effectiveFan.wireCode shl 4) or
                (if (state.verticalSwing) 0x0F else 0x00)
            ).toByte()

        // Byte 9: horizontal-swing nibble.
        bytes[9] = if (state.horizontalSwing) 0x0F else 0x00

        // Timers are disabled in this first state encoder. The sentinel differs
        // by transport variant and preserves the protocol's default state.
        if (isThreeFrame) {
            bytes[10] = 0x00
            bytes[11] = 0x06
            bytes[12] = 0x60
        }

        // Byte 13: Powerful bit 0, outdoor quiet bit 5.
        bytes[13] = (
            (if (state.powerful) 0x01 else 0x00) or
                (if (effectiveOutdoorQuiet) 0x20 else 0x00)
            ).toByte()

        // Byte 15 is a remote-ID region that differs by transport.
        bytes[15] = if (isThreeFrame) 0xC1.toByte() else 0xC5.toByte()

        // Byte 16: weekly-timer disable, Econo, Intelligent Eye.
        val weeklyDisabled = isThreeFrame && !state.weeklyScheduleActive
        bytes[16] = (
            (if (weeklyDisabled) 0x80 else 0x00) or
                (if (effectiveEcono) 0x04 else 0x00) or
                (if (state.intelligentEye) 0x02 else 0x00)
            ).toByte()

        bytes[18] = checksum(bytes, 18).toByte()
        return bytes
    }

    internal fun buildIdentityFrame(comfortFan: Boolean): ByteArray {
        val bytes = byteArrayOf(
            0x11,
            0xDA.toByte(),
            0x27,
            0x00,
            0xC5.toByte(),
            0x10,
            (if (comfortFan) 0x10 else 0x00).toByte(),
            0x00
        )
        bytes[7] = checksum(bytes, 7).toByte()
        return bytes
    }

    internal fun buildClockFrame(clock: LocalDateTime): ByteArray {
        val minutesSinceMidnight = clock.hour * 60 + clock.minute
        // Protocol day encoding: Sunday=1, Monday=2, ..., Saturday=7.
        val protocolDay = (clock.dayOfWeek.value % 7) + 1
        val bytes = byteArrayOf(
            0x11,
            0xDA.toByte(),
            0x27,
            0x00,
            0x42,
            (minutesSinceMidnight and 0xFF).toByte(),
            ((protocolDay shl 3) or (minutesSinceMidnight ushr 8)).toByte(),
            0x00
        )
        bytes[7] = checksum(bytes, 7).toByte()
        return bytes
    }

    private fun buildThreeFrameTimings(
        stateFrame: ByteArray,
        clockFrame: ByteArray,
        identityFrame: ByteArray
    ): List<Int> = buildList {
        addPreamble(this)
        appendFrame(this, identityFrame, PRE_LEADER_GAP_US)
        appendFrame(this, clockFrame, INTER_FRAME_GAP_US)
        appendFrame(this, stateFrame, INTER_FRAME_GAP_US)
        add(TRAILING_GAP_US)
    }

    private fun buildOneFrameTimings(stateFrame: ByteArray): List<Int> = buildList {
        addPreamble(this)
        appendFrame(this, stateFrame, PRE_LEADER_GAP_US)
        add(TRAILING_GAP_US)
    }

    private fun addPreamble(target: MutableList<Int>) {
        repeat(5) {
            target += MARK_US
            target += ZERO_SPACE_US
        }
        target += MARK_US
    }

    private fun appendFrame(
        target: MutableList<Int>,
        frame: ByteArray,
        precedingGapMicros: Int
    ) {
        target += precedingGapMicros
        target += LEADER_MARK_US
        target += LEADER_SPACE_US
        frame.forEach { byte ->
            for (bit in 0 until 8) {
                val one = ((byte.toInt() ushr bit) and 1) != 0
                target += MARK_US
                target += if (one) ONE_SPACE_US else ZERO_SPACE_US
            }
        }
        target += MARK_US
    }

    private fun checksum(bytes: ByteArray, count: Int): Int {
        var total = 0
        for (index in 0 until count) {
            total = (total + (bytes[index].toInt() and 0xFF)) and 0xFF
        }
        return total
    }

    private fun validateState(state: DaikinAcState) {
        val temperature = state.temperatureCelsius
        require(temperature.isFinite()) { "Daikin temperature must be finite." }
        require((temperature * 2f) % 1f == 0f) {
            "Daikin temperature must use 0.5°C increments."
        }

        val range = when (state.mode) {
            DaikinAcMode.AUTO -> 18.0f..30.0f
            DaikinAcMode.COOL -> 18.0f..32.0f
            DaikinAcMode.HEAT -> 10.0f..30.0f
            DaikinAcMode.DRY -> null // The protocol uses a fixed no-setpoint marker.
            DaikinAcMode.FAN -> 18.0f..32.0f
        }
        if (range != null) {
            require(temperature in range) {
                "Temperature for ${state.mode.name.lowercase()} mode must be between ${range.start}°C and ${range.endInclusive}°C."
            }
        }

        if (state.fanSpeed == DaikinAcFanSpeed.COMFORT && !state.powerful) {
            require(state.variant == DaikinAcTransportVariant.THREE_FRAME) {
                "Comfort fan mode requires the three-frame ARC466A9 transport."
            }
            require(!state.verticalSwing) {
                "Daikin Comfort fan and vertical swing cannot be enabled together."
            }
        }
    }
}
