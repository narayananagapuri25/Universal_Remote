package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.core.model.DeviceCategory

enum class ProfileVerificationStatus {
    UNVERIFIED,
    PROTOCOL_VERIFIED,
    PHYSICALLY_TESTED
}

/**
 * Evidence is stored per command because a remote can be only partially
 * compatible. A successful protocol-vector check does not mean physical
 * operation on a particular appliance has been confirmed.
 */
data class IrCommandEvidence(
    val status: ProfileVerificationStatus = ProfileVerificationStatus.UNVERIFIED,
    val sourceReference: String? = null,
    val sourceDataLicenseSpdx: String? = null,
    val physicalTestRecordId: String? = null
)

data class IrRemoteButton(
    val id: String,
    val label: String,
    val command: IrProtocolCommand,
    val repeatWhileHeld: Boolean = false,
    val evidence: IrCommandEvidence = IrCommandEvidence()
)

data class IrRemoteProfile(
    val profileId: String,
    val category: DeviceCategory,
    val brand: String,
    val model: String?,
    val displayName: String,
    val buttons: List<IrRemoteButton>
) {
    val protocolIds: Set<IrProtocolId>
        get() = buttons.mapTo(linkedSetOf()) { it.command.protocolId }
}

object IrRemoteProfileValidator {
    /**
     * Returns all validation problems rather than stopping at the first.
     * The caller must block activation when the returned list is non-empty.
     */
    fun validate(profile: IrRemoteProfile): List<String> {
        val errors = mutableListOf<String>()

        if (profile.profileId.isBlank()) errors += "Profile ID cannot be blank."
        if (profile.brand.isBlank()) errors += "Brand cannot be blank."
        if (profile.displayName.isBlank()) errors += "Display name cannot be blank."
        if (profile.buttons.isEmpty()) errors += "A remote profile must contain at least one button."

        if (profile.buttons.map { it.id }.toSet().size != profile.buttons.size) {
            errors += "Button IDs must be unique within a profile."
        }

        profile.buttons.forEachIndexed { index, button ->
            val prefix = "Button ${index + 1}"

            if (button.id.isBlank()) errors += "$prefix has a blank ID."
            if (button.label.isBlank()) errors += "$prefix has a blank label."

            val evidence = button.evidence
            when (evidence.status) {
                ProfileVerificationStatus.UNVERIFIED -> Unit

                ProfileVerificationStatus.PROTOCOL_VERIFIED -> {
                    if (evidence.sourceReference.isNullOrBlank()) {
                        errors += "$prefix is protocol verified but has no source reference."
                    }
                }

                ProfileVerificationStatus.PHYSICALLY_TESTED -> {
                    if (evidence.sourceReference.isNullOrBlank()) {
                        errors += "$prefix is physically tested but has no protocol/source reference."
                    }
                    if (evidence.physicalTestRecordId.isNullOrBlank()) {
                        errors += "$prefix is physically tested but has no physical test record."
                    }
                }
            }
        }

        return errors
    }
}
