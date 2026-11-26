package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.IrSignal
import dev.localremote.app.connectivity.infrared.protocol.IrCommandEvidence
import dev.localremote.app.connectivity.infrared.protocol.IrCommandSpec
import dev.localremote.app.connectivity.infrared.protocol.IrProtocolCommand
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteButton
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfile
import dev.localremote.app.connectivity.infrared.protocol.IrRemoteProfileValidator
import dev.localremote.app.connectivity.infrared.protocol.ProfileVerificationStatus
import dev.localremote.app.connectivity.infrared.protocol.SonySircVariant
import dev.localremote.app.core.model.DeviceCategory

object IrRemoteProfileMapper {

    data class Records(
        val profile: IrRemoteProfileEntity,
        val buttons: List<IrRemoteButtonEntity>
    )

    fun toRecords(
        profile: IrRemoteProfile,
        createdAtEpochMillis: Long
    ): Records {
        val validationErrors = IrRemoteProfileValidator.validate(profile)
        require(validationErrors.isEmpty()) {
            "Cannot persist invalid IR profile: ${validationErrors.joinToString("; ")}"
        }

        val profileEntity = IrRemoteProfileEntity(
            profileId = profile.profileId,
            category = profile.category.name,
            brand = profile.brand,
            model = profile.model,
            displayName = profile.displayName,
            createdAtEpochMillis = createdAtEpochMillis
        )

        val buttonEntities = profile.buttons.map { button ->
            val commandFields = when (val command = button.command) {
                is IrCommandSpec.Protocol -> protocolFields(command.command)
                is IrCommandSpec.Raw -> mapOf(
                    "commandType" to "RAW",
                    "rawCarrierFrequencyHz" to command.signal.carrierFrequencyHz,
                    "rawDutyCycle" to command.signal.dutyCycle,
                    "rawPatternMicros" to command.signal.patternMicros.joinToString(",")
                )
            }

            IrRemoteButtonEntity(
                profileId = profile.profileId,
                buttonId = button.id,
                label = button.label,
                commandType = commandFields["commandType"] as String,
                protocolName = commandFields["protocolName"] as? String,
                protocolVariant = commandFields["protocolVariant"] as? String,
                addressValue = commandFields["addressValue"] as? Int,
                commandValue = commandFields["commandValue"] as? Int,
                extendedDataValue = commandFields["extendedDataValue"] as? Int,
                rawCarrierFrequencyHz = commandFields["rawCarrierFrequencyHz"] as? Int,
                rawDutyCycle = commandFields["rawDutyCycle"] as? Float,
                rawPatternMicros = commandFields["rawPatternMicros"] as? String,
                repeatWhileHeld = button.repeatWhileHeld,
                verificationStatus = button.evidence.status.name,
                sourceReference = button.evidence.sourceReference,
                sourceDataLicenseSpdx = button.evidence.sourceDataLicenseSpdx,
                physicalTestRecordId = button.evidence.physicalTestRecordId
            )
        }

        return Records(profileEntity, buttonEntities)
    }

    fun fromRecords(records: IrRemoteProfileWithButtons): IrRemoteProfile {
        val profile = records.profile
        val category = runCatching { DeviceCategory.valueOf(profile.category) }
            .getOrElse { error("Stored profile has an unknown device category.") }

        val buttons = records.buttons.map { entity ->
            val command = when (entity.commandType) {
                "PROTOCOL" -> IrCommandSpec.Protocol(
                    protocolFromEntity(entity)
                )
                "RAW" -> {
                    val frequency = requireNotNull(entity.rawCarrierFrequencyHz) {
                        "Stored raw button is missing its carrier frequency."
                    }
                    val duty = requireNotNull(entity.rawDutyCycle) {
                        "Stored raw button is missing its duty-cycle metadata."
                    }
                    val pattern = requireNotNull(entity.rawPatternMicros) {
                        "Stored raw button is missing its timing pattern."
                    }.split(',').map { token ->
                        token.toIntOrNull()
                            ?: error("Stored raw button contains an invalid timing value.")
                    }
                    IrCommandSpec.Raw(IrSignal(frequency, pattern, duty))
                }
                else -> error("Stored IR button has an unknown command type.")
            }

            val status = runCatching {
                ProfileVerificationStatus.valueOf(entity.verificationStatus)
            }.getOrElse { ProfileVerificationStatus.UNVERIFIED }

            IrRemoteButton(
                id = entity.buttonId,
                label = entity.label,
                command = command,
                repeatWhileHeld = entity.repeatWhileHeld,
                evidence = IrCommandEvidence(
                    status = status,
                    sourceReference = entity.sourceReference,
                    sourceDataLicenseSpdx = entity.sourceDataLicenseSpdx,
                    physicalTestRecordId = entity.physicalTestRecordId
                )
            )
        }

        val result = IrRemoteProfile(
            profileId = profile.profileId,
            category = category,
            brand = profile.brand,
            model = profile.model,
            displayName = profile.displayName,
            buttons = buttons
        )
        val errors = IrRemoteProfileValidator.validate(result)
        require(errors.isEmpty()) {
            "Stored IR profile failed validation: ${errors.joinToString("; ")}"
        }
        return result
    }

    private fun protocolFields(command: IrProtocolCommand): Map<String, Any> = when (command) {
        is IrProtocolCommand.Nec -> mapOf(
            "commandType" to "PROTOCOL",
            "protocolName" to "NEC",
            "addressValue" to command.address,
            "commandValue" to command.command
        )
        is IrProtocolCommand.NecExtended -> mapOf(
            "commandType" to "PROTOCOL",
            "protocolName" to "NEC_EXTENDED",
            "addressValue" to command.address,
            "commandValue" to command.command
        )
        is IrProtocolCommand.Samsung32 -> mapOf(
            "commandType" to "PROTOCOL",
            "protocolName" to "SAMSUNG32",
            "addressValue" to command.address,
            "commandValue" to command.command
        )
        is IrProtocolCommand.SonySirc -> mapOf(
            "commandType" to "PROTOCOL",
            "protocolName" to "SONY_SIRC",
            "protocolVariant" to command.variant.name,
            "addressValue" to command.address,
            "commandValue" to command.command
        ) + (command.extendedData?.let { mapOf("extendedDataValue" to it) } ?: emptyMap())
    }

    private fun protocolFromEntity(entity: IrRemoteButtonEntity): IrProtocolCommand {
        val address = requireNotNull(entity.addressValue) {
            "Stored protocol button is missing its address."
        }
        val command = requireNotNull(entity.commandValue) {
            "Stored protocol button is missing its command."
        }

        return when (entity.protocolName) {
            "NEC" -> IrProtocolCommand.Nec(address, command)
            "NEC_EXTENDED" -> IrProtocolCommand.NecExtended(address, command)
            "SAMSUNG32" -> IrProtocolCommand.Samsung32(address, command)
            "SONY_SIRC" -> {
                val variant = runCatching {
                    SonySircVariant.valueOf(
                        requireNotNull(entity.protocolVariant) {
                            "Stored Sony SIRC button is missing its variant."
                        }
                    )
                }.getOrElse { error("Stored Sony SIRC button has an unknown variant.") }
                IrProtocolCommand.SonySirc(
                    variant = variant,
                    address = address,
                    command = command,
                    extendedData = entity.extendedDataValue
                )
            }
            else -> error("Stored IR profile uses an unsupported protocol.")
        }
    }
}
