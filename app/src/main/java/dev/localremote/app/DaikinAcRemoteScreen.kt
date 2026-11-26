package dev.localremote.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.localremote.app.connectivity.infrared.IrHardwareStatus
import dev.localremote.app.connectivity.infrared.IrTransmitResult
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcCommandDispatcher
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcFanSpeed
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcMode
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcRemotePolicy
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcState
import dev.localremote.app.connectivity.infrared.protocol.ac.DaikinAcTransportVariant
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun DaikinAcRemoteScreen(
    irHardware: IrHardwareStatus?,
    dispatcher: DaikinAcCommandDispatcher
) {
    val scope = rememberCoroutineScope()

    var variantName by rememberSaveable { mutableStateOf<String?>(null) }
    var familyConfirmed by rememberSaveable { mutableStateOf(false) }
    var powerOn by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(DaikinAcMode.COOL) }
    var temperature by rememberSaveable { mutableStateOf(24.0f) }
    var fanSpeed by rememberSaveable { mutableStateOf(DaikinAcFanSpeed.AUTO) }
    var verticalSwing by rememberSaveable { mutableStateOf(false) }
    var horizontalSwing by rememberSaveable { mutableStateOf(false) }
    var intelligentEye by rememberSaveable { mutableStateOf(false) }
    var econo by rememberSaveable { mutableStateOf(false) }
    var outdoorQuiet by rememberSaveable { mutableStateOf(false) }
    var powerful by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val variant = variantName?.let { DaikinAcTransportVariant.valueOf(it) }
    val temperatureRange = temperatureRangeFor(mode)
    val sendBlockReason = DaikinAcRemotePolicy.sendBlockReason(
        hardware = irHardware,
        variant = variant,
        familyConfirmed = familyConfirmed,
        fanSpeed = fanSpeed,
        verticalSwing = verticalSwing
    )
    val canSend = !sending && sendBlockReason == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Experimental Daikin AC control",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "This screen sends a complete AC state frame. It is only for the documented ARC466A9-style and ARC480A11-style IR families; it is not a universal Daikin profile and has not been physically tested by OneRemote.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ControlSection("1. Match your original remote family") {
            Text(
                "Select the transport only when the model marking on your original remote or appliance documentation matches the family below.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = variant == DaikinAcTransportVariant.THREE_FRAME,
                    onClick = {
                        variantName = DaikinAcTransportVariant.THREE_FRAME.name
                        familyConfirmed = false
                        feedback = null
                    },
                    label = { Text("ARC466A9 · 3 frames") }
                )
                FilterChip(
                    selected = variant == DaikinAcTransportVariant.ONE_FRAME,
                    onClick = {
                        variantName = DaikinAcTransportVariant.ONE_FRAME.name
                        familyConfirmed = false
                        if (fanSpeed == DaikinAcFanSpeed.COMFORT) {
                            fanSpeed = DaikinAcFanSpeed.AUTO
                        }
                        feedback = null
                    },
                    label = { Text("ARC480A11 · 1 frame") }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = familyConfirmed,
                    onCheckedChange = {
                        familyConfirmed = it
                        feedback = null
                    },
                    enabled = variant != null
                )
                Text(
                    "I have checked the original remote/model marking and selected the matching family. I understand compatibility is not confirmed.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        ControlSection("2. Power and operating mode") {
            Text("Power", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = powerOn,
                    onClick = { powerOn = true },
                    label = { Text("On") }
                )
                FilterChip(
                    selected = !powerOn,
                    onClick = { powerOn = false },
                    label = { Text("Off") }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DaikinAcMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = {
                            mode = option
                            temperatureRangeFor(option)?.let { range ->
                                temperature = temperature.coerceIn(range.start, range.endInclusive)
                            }
                            feedback = null
                        },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        ControlSection("3. Temperature") {
            if (temperatureRange == null) {
                Text(
                    "Dry mode uses the protocol's fixed no-setpoint value. The displayed temperature is not sent in this mode.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "${temperature.formatHalfDegree()} °C",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = temperature.coerceIn(temperatureRange.start, temperatureRange.endInclusive),
                    onValueChange = {
                        temperature = (it * 2f).roundToInt() / 2f
                        feedback = null
                    },
                    valueRange = temperatureRange,
                    steps = (((temperatureRange.endInclusive - temperatureRange.start) * 2f)
                        .roundToInt() - 1).coerceAtLeast(0)
                )
                Text(
                    "Adjusts in 0.5 °C increments. Allowed range for this mode: ${temperatureRange.start}–${temperatureRange.endInclusive} °C.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ControlSection("4. Fan speed") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fanOptions = buildList {
                    addAll(
                        listOf(
                            DaikinAcFanSpeed.AUTO,
                            DaikinAcFanSpeed.LEVEL_1,
                            DaikinAcFanSpeed.LEVEL_2,
                            DaikinAcFanSpeed.LEVEL_3,
                            DaikinAcFanSpeed.LEVEL_4,
                            DaikinAcFanSpeed.LEVEL_5,
                            DaikinAcFanSpeed.NIGHT
                        )
                    )
                    if (variant == DaikinAcTransportVariant.THREE_FRAME) {
                        add(DaikinAcFanSpeed.COMFORT)
                    }
                }
                fanOptions.forEach { option ->
                    FilterChip(
                        selected = fanSpeed == option,
                        onClick = {
                            fanSpeed = option
                            feedback = null
                        },
                        enabled = option != DaikinAcFanSpeed.COMFORT || !verticalSwing,
                        label = {
                            Text(
                                when (option) {
                                    DaikinAcFanSpeed.AUTO -> "Auto"
                                    DaikinAcFanSpeed.NIGHT -> "Night"
                                    DaikinAcFanSpeed.COMFORT -> "Comfort"
                                    else -> "Level ${option.wireCode - 2}"
                                }
                            )
                        }
                    )
                }
            }
            if (fanSpeed == DaikinAcFanSpeed.COMFORT) {
                Text(
                    "Comfort fan requires the three-frame family and vertical swing turned off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        ControlSection("5. Swing and comfort features") {
            SwitchSetting(
                title = "Vertical swing",
                checked = verticalSwing,
                enabled = fanSpeed != DaikinAcFanSpeed.COMFORT,
                onCheckedChange = {
                    verticalSwing = it
                    feedback = null
                }
            )
            SwitchSetting("Horizontal swing", horizontalSwing, true) {
                horizontalSwing = it
                feedback = null
            }
            SwitchSetting("Intelligent Eye", intelligentEye, true) {
                intelligentEye = it
                feedback = null
            }
            SwitchSetting("Econo", econo, true) {
                econo = it
                feedback = null
            }
            SwitchSetting("Outdoor Quiet", outdoorQuiet, true) {
                outdoorQuiet = it
                feedback = null
            }
            SwitchSetting("Powerful", powerful, true) {
                powerful = it
                feedback = null
            }
            if (powerful && (econo || outdoorQuiet)) {
                Text(
                    "Powerful mode takes precedence: Econo and Outdoor Quiet are suppressed in the transmitted state while Powerful is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (powerful && fanSpeed == DaikinAcFanSpeed.COMFORT) {
                Text(
                    "Powerful mode downgrades Comfort fan to Auto in the transmitted state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        ControlSection("Send complete state") {
            Text(
                "Sending replaces the AC's current settings with all values shown above. One tap sends one frame sequence; OneRemote never retries automatically.",
                style = MaterialTheme.typography.bodySmall
            )
            if (!sending && !sendBlockReason.isNullOrBlank()) {
                Text(
                    sendBlockReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    val selectedVariant = variant ?: return@Button
                    val targetState = DaikinAcState(
                        power = powerOn,
                        mode = mode,
                        temperatureCelsius = temperature,
                        fanSpeed = fanSpeed,
                        verticalSwing = verticalSwing,
                        horizontalSwing = horizontalSwing,
                        intelligentEye = intelligentEye,
                        econo = econo,
                        outdoorQuiet = outdoorQuiet,
                        powerful = powerful,
                        weeklyScheduleActive = false,
                        variant = selectedVariant
                    )
                    sending = true
                    feedback = null
                    scope.launch {
                        val result = runCatching { dispatcher.sendState(targetState) }
                        feedback = result.fold(
                            onSuccess = ::daikinResultMessage,
                            onFailure = { it.message ?: "The transmission failed." }
                        )
                        sending = false
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (sending) "Sending…" else "Send AC state once")
            }
            if (!feedback.isNullOrBlank()) {
                Text(
                    feedback.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "A successful Android send only means the phone's transmit API returned. It does not confirm that the AC received or applied the settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun temperatureRangeFor(mode: DaikinAcMode): ClosedFloatingPointRange<Float>? =
    when (mode) {
        DaikinAcMode.AUTO -> 18.0f..30.0f
        DaikinAcMode.COOL -> 18.0f..32.0f
        DaikinAcMode.HEAT -> 10.0f..30.0f
        DaikinAcMode.DRY -> null
        DaikinAcMode.FAN -> 18.0f..32.0f
    }

private fun Float.formatHalfDegree(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

private fun daikinResultMessage(result: IrTransmitResult): String = when (result) {
    is IrTransmitResult.Sent ->
        "IR signal sent once (${result.durationMicros} µs). AC state is unconfirmed."
    IrTransmitResult.NoTransmitter -> "No compatible IR transmitter is available."
    is IrTransmitResult.UnsupportedFrequency ->
        "38 kHz is not within the phone's reported carrier ranges. No signal was sent."
    is IrTransmitResult.Rejected -> "Command rejected: ${result.reason}"
    is IrTransmitResult.Failed -> "Transmission failed: ${result.reason}"
}
