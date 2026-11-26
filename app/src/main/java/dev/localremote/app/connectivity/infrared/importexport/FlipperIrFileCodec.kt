package dev.localremote.app.connectivity.infrared.importexport

import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.IrSignalValidator
import dev.localremote.app.connectivity.infrared.protocol.IrCommandEvidence
import dev.localremote.app.connectivity.infrared.protocol.IrCommandSpec
import dev.localremote.app.connectivity.infrared.protocol.IrProtocolCommand
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteButton
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfile
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfileValidator
import dev.localremote.app.connectivity.infrared.protocol.ProfileVerificationStatus
import dev.localremote.app.connectivity.infrared.protocol.SonySircVariant
import dev.localremote.app.core.model.DeviceCategory
import java.util.Locale

/**
 * Bounded reader/writer for Flipper Zero version-1 .ir remote files.
 *
 * This implements the documented Filetype: IR signals file format, not the
 * separate IR library format or .irtest format. Parsed protocol types not
 * supported by OneRemote yet are rejected with a clear error. Raw entries
 * remain importable even when their protocol is unknown.
 */
object FlipperIrFileCodec {
    const val MAX_FILE_CHARS = 256 * 1024
    const val MAX_BUTTONS = 128
    const val MAX_LINE_CHARS = 8_192
    const val MAX_RAW_TIMINGS = 1_024
    const val MAX_BUTTON_NAME_CHARS = 23

    private const val FILE_HEADER = "Filetype: IR signals file"
    private const val FILE_VERSION = "Version: 1"

    data class ImportMetadata(
        val profileId: String,
        val category: DeviceCategory,
        val brand: String,
        val model: String?,
        val displayName: String
    )

    sealed interface ImportResult {
        data class Success(val profile: IrRemoteProfile) : ImportResult
        data class Failure(val errors: List<String>) : ImportResult
    }

    sealed interface ExportResult {
        data class Success(
            val content: String,
            val warnings: List<String>
        ) : ExportResult

        data class Failure(val errors: List<String>) : ExportResult
    }

    private data class ButtonBlock(
        val lineNumber: Int,
        val fields: LinkedHashMap<String, String>
    )

    fun importFile(content: String, metadata: ImportMetadata): ImportResult {
        if (content.length > MAX_FILE_CHARS) {
            return ImportResult.Failure(listOf("File exceeds the $MAX_FILE_CHARS character limit."))
        }
        if (content.isBlank()) {
            return ImportResult.Failure(listOf("The .ir file is empty."))
        }

        val normalized = content.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalized.split('\n')
        if (lines.size < 2 ||
            lines[0].trim() != FILE_HEADER ||
            lines[1].trim() != FILE_VERSION
        ) {
            return ImportResult.Failure(
                listOf("Expected '$FILE_HEADER' and '$FILE_VERSION' at the start of the file.")
            )
        }

        val blocks = mutableListOf<ButtonBlock>()
        val errors = mutableListOf<String>()
        var fields = LinkedHashMap<String, String>()
        var blockStartLine = 3

        fun flushBlock() {
            if (fields.isNotEmpty()) {
                blocks += ButtonBlock(blockStartLine, fields)
                fields = LinkedHashMap()
            }
        }

        for (index in 2 until lines.size) {
            val lineNumber = index + 1
            val rawLine = lines[index]
            if (rawLine.length > MAX_LINE_CHARS) {
                errors += "Line $lineNumber exceeds the $MAX_LINE_CHARS character limit."
                continue
            }

            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#")) {
                flushBlock()
                blockStartLine = lineNumber + 1
                continue
            }

            val colon = trimmed.indexOf(':')
            if (colon <= 0) {
                errors += "Line $lineNumber is not a valid field (expected key: value)."
                continue
            }

            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            if (!key.matches(Regex("[a-z_]+"))) {
                errors += "Line $lineNumber has an invalid field name."
                continue
            }
            if (value.isEmpty()) {
                errors += "Line $lineNumber has an empty value for '$key'."
                continue
            }
            if (fields.putIfAbsent(key, value) != null) {
                errors += "Line $lineNumber duplicates field '$key' in the same button."
            } else if (fields.size == 1) {
                blockStartLine = lineNumber
            }
        }
        flushBlock()

        if (blocks.isEmpty()) {
            errors += "The .ir file does not contain any button blocks."
        }
        if (blocks.size > MAX_BUTTONS) {
            errors += "The .ir file contains more than $MAX_BUTTONS buttons."
        }
        if (errors.isNotEmpty()) return ImportResult.Failure(errors)

        val buttons = mutableListOf<IrRemoteButton>()
        blocks.take(MAX_BUTTONS).forEachIndexed { index, block ->
            when (val parsed = parseButton(block, index)) {
                is ButtonParseResult.Success -> buttons += parsed.button
                is ButtonParseResult.Failure -> errors += parsed.errors
            }
        }
        if (errors.isNotEmpty()) return ImportResult.Failure(errors)

        val profile = IrRemoteProfile(
            profileId = metadata.profileId,
            category = metadata.category,
            brand = metadata.brand,
            model = metadata.model,
            displayName = metadata.displayName,
            buttons = buttons
        )
        errors += IrRemoteProfileValidator.validate(profile)
        return if (errors.isEmpty()) {
            ImportResult.Success(profile)
        } else {
            ImportResult.Failure(errors)
        }
    }

    fun exportFile(profile: IrRemoteProfile): ExportResult {
        val errors = IrRemoteProfileValidator.validate(profile).toMutableList()
        if (profile.buttons.size > MAX_BUTTONS) {
            errors += "A Flipper .ir file may contain at most $MAX_BUTTONS buttons in this importer/exporter."
        }
        if (errors.isNotEmpty()) return ExportResult.Failure(errors)

        val warnings = mutableListOf(
            "Flipper .ir does not store OneRemote brand/model metadata or per-button verification evidence; those details are not exported."
        )
        val output = StringBuilder()
            .append(FILE_HEADER).append('\n')
            .append(FILE_VERSION).append("\n#\n")

        profile.buttons.forEachIndexed { index, button ->
            if (!isPrintableButtonName(button.label)) {
                errors += "Button ${index + 1} label must contain 1–$MAX_BUTTON_NAME_CHARS printable ASCII characters."
                return@forEachIndexed
            }

            output.append("name: ").append(button.label).append('\n')
            when (val spec = button.command) {
                is IrCommandSpec.Protocol -> {
                    val fields = encodeParsed(spec.command)
                    if (fields == null) {
                        errors += "Button '${button.label}' uses a protocol unsupported by the Flipper .ir parsed format."
                    } else {
                        output.append("type: parsed\n")
                        output.append("protocol: ").append(fields.first).append('\n')
                        output.append("address: ").append(formatLittleEndian4(fields.second)).append('\n')
                        output.append("command: ").append(formatLittleEndian4(fields.third)).append('\n')
                    }
                }

                is IrCommandSpec.Raw -> {
                    val signal = spec.signal
                    val validation = IrSignalValidator.validate(signal)
                    if (validation != null) {
                        errors += "Button '${button.label}' has an invalid raw waveform: $validation"
                    } else if (signal.patternMicros.size > MAX_RAW_TIMINGS) {
                        errors += "Button '${button.label}' exceeds the Flipper raw timing limit of $MAX_RAW_TIMINGS."
                    } else {
                        output.append("type: raw\n")
                        output.append("frequency: ").append(signal.carrierFrequencyHz).append('\n')
                        output.append("duty_cycle: ").append(
                            String.format(Locale.US, "%.6f", signal.dutyCycle)
                        ).append('\n')
                        output.append("data: ").append(signal.patternMicros.joinToString(" ")).append('\n')
                    }
                }
            }
            output.append("#\n")
        }

        if (errors.isNotEmpty()) return ExportResult.Failure(errors)
        if (output.length > MAX_FILE_CHARS) {
            return ExportResult.Failure(listOf("Export exceeds the $MAX_FILE_CHARS character limit."))
        }
        return ExportResult.Success(output.toString(), warnings)
    }

    private sealed interface ButtonParseResult {
        data class Success(val button: IrRemoteButton) : ButtonParseResult
        data class Failure(val errors: List<String>) : ButtonParseResult
    }

    private fun parseButton(block: ButtonBlock, index: Int): ButtonParseResult {
        val fields = block.fields
        val name = fields["name"]
        val type = fields["type"]
        val errors = mutableListOf<String>()
        val label = name.orEmpty()

        if (name == null) errors += "Line ${block.lineNumber}: button ${index + 1} is missing 'name'."
        else if (!isPrintableButtonName(name)) {
            errors += "Line ${block.lineNumber}: button name must contain 1–$MAX_BUTTON_NAME_CHARS printable ASCII characters."
        }
        if (type == null) errors += "Line ${block.lineNumber}: button '${label}' is missing 'type'."
        if (errors.isNotEmpty()) return ButtonParseResult.Failure(errors)

        val commandResult = when (type) {
            "parsed" -> parseParsedCommand(fields, block.lineNumber, label)
            "raw" -> parseRawSignal(fields, block.lineNumber, label)
            else -> ButtonParseResult.Failure(
                listOf("Line ${block.lineNumber}: button '${label}' has unsupported type '$type'.")
            )
        }
        if (commandResult !is CommandParseResult.Success) {
            return ButtonParseResult.Failure((commandResult as CommandParseResult.Failure).errors)
        }

        val allowedFields = when (type) {
            "parsed" -> setOf("name", "type", "protocol", "address", "command")
            "raw" -> setOf("name", "type", "frequency", "duty_cycle", "data")
            else -> emptySet()
        }
        val unknown = fields.keys - allowedFields
        if (unknown.isNotEmpty()) {
            return ButtonParseResult.Failure(
                listOf("Line ${block.lineNumber}: button '${label}' contains unsupported fields: ${unknown.joinToString()}.")
            )
        }

        return ButtonParseResult.Success(
            IrRemoteButton(
                id = "imported-button-${index + 1}",
                label = label,
                command = commandResult.command,
                evidence = IrCommandEvidence(
                    status = ProfileVerificationStatus.UNVERIFIED,
                    sourceReference = "Imported from Flipper-compatible .ir file"
                )
            )
        )
    }

    private sealed interface CommandParseResult {
        data class Success(val command: IrCommandSpec) : CommandParseResult
        data class Failure(val errors: List<String>) : CommandParseResult
    }

    private fun parseParsedCommand(
        fields: Map<String, String>,
        lineNumber: Int,
        name: String
    ): CommandParseResult {
        val protocol = fields["protocol"]
            ?: return CommandParseResult.Failure(listOf("Line $lineNumber: '$name' is missing 'protocol'."))
        val addressBytes = parseHexBytes(fields["address"], 4)
            ?: return CommandParseResult.Failure(listOf("Line $lineNumber: '$name' has an invalid four-byte address."))
        val commandBytes = parseHexBytes(fields["command"], 4)
            ?: return CommandParseResult.Failure(listOf("Line $lineNumber: '$name' has an invalid four-byte command."))

        val address = littleEndianValue(addressBytes)
        val command = littleEndianValue(commandBytes)

        val parsed = try {
            when (protocol) {
                "NEC" -> {
                    require(address <= 0xFF && command <= 0xFF) {
                        "NEC address and command must be 8-bit values."
                    }
                    IrProtocolCommand.Nec(address.toInt(), command.toInt())
                }
                "NECext" -> {
                    require(address <= 0xFFFF && command <= 0xFFFF) {
                        "NECext address and command must be 16-bit values."
                    }
                    IrProtocolCommand.NecExtended(address.toInt(), command.toInt())
                }
                "Samsung32" -> {
                    require(address <= 0xFF && command <= 0xFF) {
                        "Samsung32 address and command must be 8-bit values."
                    }
                    IrProtocolCommand.Samsung32(address.toInt(), command.toInt())
                }
                "SIRC" -> {
                    require(address <= 0x1F && command <= 0x7F) {
                        "SIRC address must be 5-bit and command must be 7-bit."
                    }
                    IrProtocolCommand.SonySirc(
                        SonySircVariant.SIRC_12,
                        address.toInt(),
                        command.toInt()
                    )
                }
                "SIRC15" -> {
                    require(address <= 0xFF && command <= 0x7F) {
                        "SIRC15 address must be 8-bit and command must be 7-bit."
                    }
                    IrProtocolCommand.SonySirc(
                        SonySircVariant.SIRC_15,
                        address.toInt(),
                        command.toInt()
                    )
                }
                "SIRC20" -> {
                    require(address <= 0x1FFF && command <= 0x7F) {
                        "SIRC20 address must be 13-bit and command must be 7-bit."
                    }
                    IrProtocolCommand.SonySirc(
                        variant = SonySircVariant.SIRC_20,
                        address = (address and 0x1F).toInt(),
                        command = command.toInt(),
                        extendedData = ((address ushr 5) and 0xFF).toInt()
                    )
                }
                else -> error("Parsed protocol '$protocol' is not supported yet. Export or re-learn this button as raw data.")
            }
        } catch (failure: IllegalArgumentException) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' protocol fields are invalid: ${failure.message}")
            )
        } catch (failure: IllegalStateException) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name': ${failure.message}")
            )
        }

        return CommandParseResult.Success(IrCommandSpec.Protocol(parsed))
    }

    private fun parseRawSignal(
        fields: Map<String, String>,
        lineNumber: Int,
        name: String
    ): CommandParseResult {
        val frequency = fields["frequency"]?.toLongOrNull()
        if (frequency == null || frequency !in 10_000L..1_000_000L) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' frequency must be between 10000 and 1000000 Hz.")
            )
        }

        val dutyCycle = fields["duty_cycle"]?.toFloatOrNull()
        if (dutyCycle == null || !dutyCycle.isFinite() || dutyCycle <= 0f || dutyCycle > 1f) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' duty_cycle must be greater than 0 and at most 1.")
            )
        }

        val rawData = fields["data"] ?: return CommandParseResult.Failure(
            listOf("Line $lineNumber: '$name' is missing 'data'.")
        )
        val tokens = rawData.split(Regex("\\s+"))
        if (tokens.size < 2 || tokens.size > MAX_RAW_TIMINGS) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' must contain 2–$MAX_RAW_TIMINGS raw timings.")
            )
        }

        val timings = tokens.map { token ->
            token.toLongOrNull()?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
        }
        if (timings.any { it == null }) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' raw timings must be positive 32-bit integers.")
            )
        }

        val signal = IrSignal(
            carrierFrequencyHz = frequency.toInt(),
            patternMicros = timings.filterNotNull(),
            dutyCycle = dutyCycle
        )
        val validation = IrSignalValidator.validate(signal)
        if (validation != null) {
            return CommandParseResult.Failure(
                listOf("Line $lineNumber: '$name' raw waveform is invalid: $validation")
            )
        }
        return CommandParseResult.Success(IrCommandSpec.Raw(signal))
    }

    private fun isPrintableButtonName(value: String): Boolean =
        value.length in 1..MAX_BUTTON_NAME_CHARS &&
            value.all { it.code in 0x20..0x7E }

    private fun parseHexBytes(value: String?, expectedCount: Int): List<Int>? {
        if (value == null) return null
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.size != expectedCount || parts.any { !it.matches(Regex("[0-9A-Fa-f]{2}")) }) {
            return null
        }
        return parts.map { it.toInt(16) }
    }

    private fun littleEndianValue(bytes: List<Int>): Long =
        bytes.withIndex().fold(0L) { total, (index, byte) ->
            total or (byte.toLong() shl (index * 8))
        }

    private fun formatLittleEndian4(value: Int): String =
        (0..3).joinToString(" ") { byteIndex ->
            "%02X".format(Locale.US, (value ushr (byteIndex * 8)) and 0xFF)
        }

    private data class ParsedFields(
        val first: String,
        val second: Int,
        val third: Int
    )

    private fun encodeParsed(command: IrProtocolCommand): ParsedFields? = when (command) {
        is IrProtocolCommand.Nec ->
            ParsedFields("NEC", command.address, command.command)
        is IrProtocolCommand.NecExtended ->
            ParsedFields("NECext", command.address, command.command)
        is IrProtocolCommand.Samsung32 ->
            ParsedFields("Samsung32", command.address, command.command)
        is IrProtocolCommand.SonySirc -> {
            val address = when (command.variant) {
                SonySircVariant.SIRC_12, SonySircVariant.SIRC_15 -> command.address
                SonySircVariant.SIRC_20 -> command.address or ((command.extendedData ?: return null) shl 5)
            }
            val protocol = when (command.variant) {
                SonySircVariant.SIRC_12 -> "SIRC"
                SonySircVariant.SIRC_15 -> "SIRC15"
                SonySircVariant.SIRC_20 -> "SIRC20"
            }
            ParsedFields(protocol, address, command.command)
        }
    }
}
