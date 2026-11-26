package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.connectivity.infrared.IrSignal

/**
 * Encodes documented protocol fields into Android's alternating mark/space
 * microsecond representation. No manufacturer code tables are embedded here.
 *
 * Timings should be validated against the supplied original-remote capture
 * before a model profile is labelled protocol verified or physically tested.
 */
object IrProtocolEncoder {
    private const val NEC_FREQUENCY_HZ = 38_000
    private const val SAMSUNG_FREQUENCY_HZ = 38_000
    private const val SONY_FREQUENCY_HZ = 40_000

    fun encode(command: IrProtocolCommand): IrSignal = when (command) {
        is IrProtocolCommand.Nec -> encodeNec(
            address = command.address,
            command = command.command,
            extendedAddress = false
        )
        is IrProtocolCommand.NecExtended -> encodeNec(
            address = command.address,
            command = command.command,
            extendedAddress = true
        )
        is IrProtocolCommand.Samsung32 -> encodeSamsung32(command)
        is IrProtocolCommand.SonySirc -> encodeSonySirc(command)
    }

    private fun encodeNec(
        address: Int,
        command: Int,
        extendedAddress: Boolean
    ): IrSignal {
        requireByte(command, "NEC command")
        if (extendedAddress) {
            requireRange(address, 0x0000, 0xFFFF, "NEC extended address")
            val low = address and 0xFF
            val high = (address ushr 8) and 0xFF
            require(high != (low xor 0xFF)) {
                "NEC extended address cannot be an 8-bit address followed by its inverse."
            }
        } else {
            requireByte(address, "NEC address")
        }

        val bytes = if (extendedAddress) {
            listOf(
                address and 0xFF,
                (address ushr 8) and 0xFF,
                command,
                command xor 0xFF
            )
        } else {
            listOf(
                address,
                address xor 0xFF,
                command,
                command xor 0xFF
            )
        }

        val pattern = mutableListOf(9_000, 4_500)
        bytes.forEach { appendByteLsbFirst(pattern, it, ::appendNecBit) }
        pattern += NEC_UNIT

        return IrSignal(NEC_FREQUENCY_HZ, pattern)
    }

    private fun encodeSamsung32(command: IrProtocolCommand.Samsung32): IrSignal {
        requireByte(command.address, "Samsung32 address")
        requireByte(command.command, "Samsung32 command")

        // Samsung32 transmits address, repeated address, command, inverted command.
        val bytes = listOf(
            command.address,
            command.address,
            command.command,
            command.command xor 0xFF
        )
        val pattern = mutableListOf(4_500, 4_500)
        bytes.forEach { appendByteLsbFirst(pattern, it, ::appendSamsungBit) }
        pattern += SAMSUNG_UNIT

        return IrSignal(SAMSUNG_FREQUENCY_HZ, pattern)
    }

    private fun encodeSonySirc(command: IrProtocolCommand.SonySirc): IrSignal {
        requireRange(command.command, 0, 0x7F, "Sony SIRC command")
        requireRange(
            command.address,
            0,
            (1 shl command.variant.addressBitCount) - 1,
            "Sony SIRC address"
        )

        val extendedData = command.extendedData
        when (command.variant) {
            SonySircVariant.SIRC_12,
            SonySircVariant.SIRC_15 -> require(extendedData == null) {
                "Sony SIRC12/15 do not have an extended data field."
            }
            SonySircVariant.SIRC_20 -> requireRange(
                requireNotNull(extendedData) {
                    "Sony SIRC20 requires an 8-bit extended data field."
                },
                0,
                0xFF,
                "Sony SIRC20 extended data"
            )
        }

        val pattern = mutableListOf(2_400, 600)
        appendBitsLsbFirst(pattern, command.command, 7, ::appendSonyBit)
        appendBitsLsbFirst(
            pattern,
            command.address,
            command.variant.addressBitCount,
            ::appendSonyBit
        )
        if (command.variant == SonySircVariant.SIRC_20) {
            appendBitsLsbFirst(
                pattern,
                requireNotNull(extendedData),
                8,
                ::appendSonyBit
            )
        }

        return IrSignal(SONY_FREQUENCY_HZ, pattern)
    }

    private fun appendByteLsbFirst(
        pattern: MutableList<Int>,
        value: Int,
        appendBit: (MutableList<Int>, Boolean) -> Unit
    ) {
        appendBitsLsbFirst(pattern, value, 8, appendBit)
    }

    private fun appendBitsLsbFirst(
        pattern: MutableList<Int>,
        value: Int,
        bitCount: Int,
        appendBit: (MutableList<Int>, Boolean) -> Unit
    ) {
        require(bitCount in 1..31)
        for (bit in 0 until bitCount) {
            appendBit(pattern, ((value ushr bit) and 1) != 0)
        }
    }

    private fun appendNecBit(pattern: MutableList<Int>, one: Boolean) {
        pattern += NEC_UNIT
        pattern += if (one) NEC_ONE_SPACE else NEC_ZERO_SPACE
    }

    private fun appendSamsungBit(pattern: MutableList<Int>, one: Boolean) {
        pattern += SAMSUNG_UNIT
        pattern += if (one) SAMSUNG_ONE_SPACE else SAMSUNG_ZERO_SPACE
    }

    private fun appendSonyBit(pattern: MutableList<Int>, one: Boolean) {
        pattern += if (one) SONY_ONE_MARK else SONY_ZERO_MARK
        pattern += SONY_SPACE
    }

    private fun requireByte(value: Int, label: String) =
        requireRange(value, 0, 0xFF, label)

    private fun requireRange(value: Int, min: Int, max: Int, label: String) {
        require(value in min..max) { "$label must be between $min and $max." }
    }

    private const val NEC_UNIT = 560
    private const val NEC_ZERO_SPACE = 560
    private const val NEC_ONE_SPACE = 1_690

    private const val SAMSUNG_UNIT = 560
    private const val SAMSUNG_ZERO_SPACE = 560
    private const val SAMSUNG_ONE_SPACE = 1_690

    private const val SONY_ZERO_MARK = 600
    private const val SONY_ONE_MARK = 1_200
    private const val SONY_SPACE = 600
}
