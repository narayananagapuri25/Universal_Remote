package dev.localremote.app.connectivity.infrared.protocol.ac

enum class DaikinAcMode(val wireCode: Int) {
    AUTO(0x0),
    DRY(0x2),
    COOL(0x3),
    HEAT(0x4),
    FAN(0x6)
}

enum class DaikinAcFanSpeed(val wireCode: Int) {
    LEVEL_1(0x3),
    LEVEL_2(0x4),
    LEVEL_3(0x5),
    LEVEL_4(0x6),
    LEVEL_5(0x7),
    AUTO(0xA),
    NIGHT(0xB),
    COMFORT(0xA)
}

enum class DaikinAcTransportVariant {
    /**
     * ARC466A9-style transport: preamble + identity + clock + state.
     * The clock is included in the frame but timers/weekly schedule programming
     * are not yet exposed by this state model.
     */
    THREE_FRAME,

    /**
     * ARC480A11-style transport: preamble + state only.
     */
    ONE_FRAME
}

/**
 * Absolute AC state. Every encode operation produces a complete state frame,
 * not an isolated "toggle temperature/mode" command.
 *
 * This initial profile models the reverse-engineered Daikin ARC466A9 and
 * ARC480A11 transport families. It is not a universal Daikin profile.
 */
data class DaikinAcState(
    val power: Boolean = true,
    val mode: DaikinAcMode = DaikinAcMode.COOL,
    val temperatureCelsius: Float = 21.0f,
    val fanSpeed: DaikinAcFanSpeed = DaikinAcFanSpeed.AUTO,
    val verticalSwing: Boolean = false,
    val horizontalSwing: Boolean = false,
    val intelligentEye: Boolean = false,
    val econo: Boolean = false,
    val outdoorQuiet: Boolean = false,
    val powerful: Boolean = false,
    val weeklyScheduleActive: Boolean = false,
    val variant: DaikinAcTransportVariant = DaikinAcTransportVariant.THREE_FRAME
)
