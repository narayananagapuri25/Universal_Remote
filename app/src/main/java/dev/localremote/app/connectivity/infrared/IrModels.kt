package dev.localremote.app.connectivity.infrared

/**
 * Inclusive carrier-frequency range reported by an actual transmitter.
 * Frequencies are expressed in Hertz.
 */
data class IrFrequencyRange(
    val minHz: Int,
    val maxHz: Int
) {
    init {
        require(minHz > 0) { "Minimum carrier frequency must be positive." }
        require(maxHz >= minHz) { "Maximum carrier frequency must not be below minimum." }
    }

    fun contains(frequencyHz: Int): Boolean =
        frequencyHz in minHz..maxHz
}

data class IrHardwareStatus(
    val transmitterAvailable: Boolean,
    val supportedCarrierRanges: List<IrFrequencyRange>,
    val diagnostic: String? = null
) {
    fun supportsCarrierFrequency(frequencyHz: Int): Boolean =
        transmitterAvailable && supportedCarrierRanges.any { it.contains(frequencyHz) }
}

/**
 * Consumer IR patterns start with an ON/mark duration and alternate
 * ON/OFF durations, measured in microseconds.
 */
class IrSignal(
    val carrierFrequencyHz: Int,
    patternMicros: List<Int>
) {
    /**
     * Take a defensive snapshot so a caller cannot mutate a queued waveform
     * after validation and before the platform dispatch.
     */
    val patternMicros: List<Int> = patternMicros.toList()

    val totalDurationMicros: Long
        get() = patternMicros.sumOf { it.toLong() }
}

object IrSignalValidator {
    const val MAX_PATTERN_SEGMENTS = 4_096
    const val MAX_PATTERN_DURATION_MICROS = 1_999_999L

    fun validate(signal: IrSignal): String? {
        if (signal.carrierFrequencyHz <= 0) {
            return "Carrier frequency must be positive."
        }
        if (signal.patternMicros.size < 2) {
            return "An IR pattern must contain at least one mark and one space."
        }
        if (signal.patternMicros.size > MAX_PATTERN_SEGMENTS) {
            return "IR pattern contains too many segments."
        }
        if (signal.patternMicros.any { it <= 0 }) {
            return "IR pattern durations must all be positive."
        }

        val duration = signal.totalDurationMicros
        if (duration > MAX_PATTERN_DURATION_MICROS) {
            return "IR pattern must be shorter than two seconds."
        }
        return null
    }
}

sealed interface IrTransmitResult {
    data class Sent(val durationMicros: Long) : IrTransmitResult
    data object NoTransmitter : IrTransmitResult
    data class UnsupportedFrequency(
        val requestedHz: Int,
        val supportedRanges: List<IrFrequencyRange>
    ) : IrTransmitResult
    data class Rejected(val reason: String) : IrTransmitResult
    data class Failed(val reason: String) : IrTransmitResult
}
