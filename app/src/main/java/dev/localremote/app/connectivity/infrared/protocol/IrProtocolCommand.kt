package dev.localremote.app.connectivity.infrared.protocol

enum class IrProtocolId {
    NEC,
    NEC_EXTENDED,
    SAMSUNG32,
    SONY_SIRC_12,
    SONY_SIRC_15,
    SONY_SIRC_20
}

enum class SonySircVariant(
    val bitCount: Int,
    val addressBitCount: Int
) {
    SIRC_12(bitCount = 12, addressBitCount = 5),
    SIRC_15(bitCount = 15, addressBitCount = 8),
    SIRC_20(bitCount = 20, addressBitCount = 5)
}

/**
 * Typed protocol parameters. These are command values, not raw hexadecimal
 * database entries. Model-specific profile mappings provide these values.
 */
sealed interface IrProtocolCommand {
    val protocolId: IrProtocolId

    data class Nec(
        val address: Int,
        val command: Int
    ) : IrProtocolCommand {
        override val protocolId = IrProtocolId.NEC
    }

    /**
     * Flipper NECext stores independent 16-bit address and 16-bit command
     * fields (not the legacy 8-bit NEC inverse-byte layout).
     */
    data class NecExtended(
        val address: Int,
        val command: Int
    ) : IrProtocolCommand {
        override val protocolId = IrProtocolId.NEC_EXTENDED
    }

    data class Samsung32(
        val address: Int,
        val command: Int
    ) : IrProtocolCommand {
        override val protocolId = IrProtocolId.SAMSUNG32
    }

    data class SonySirc(
        val variant: SonySircVariant,
        val address: Int,
        val command: Int,
        /**
         * SIRC20 has an additional 8-bit field after its 5-bit address.
         * Must be null for SIRC12 and SIRC15.
         */
        val extendedData: Int? = null
    ) : IrProtocolCommand {
        override val protocolId: IrProtocolId = when (variant) {
            SonySircVariant.SIRC_12 -> IrProtocolId.SONY_SIRC_12
            SonySircVariant.SIRC_15 -> IrProtocolId.SONY_SIRC_15
            SonySircVariant.SIRC_20 -> IrProtocolId.SONY_SIRC_20
        }
    }
}


/**
 * A saved remote button can use a decoded protocol command or a raw waveform
 * captured/imported from a compatible external receiver/database.
 */
sealed interface IrCommandSpec {
    data class Protocol(
        val command: IrProtocolCommand
    ) : IrCommandSpec

    data class Raw(
        val signal: dev.localremote.app.connectivity.infrared.IrSignal
    ) : IrCommandSpec
}
