package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.protocol.RemoteControlLayoutValidator
import dev.localremote.app.connectivity.infrared.protocol.RemoteControlMapping
import dev.localremote.app.connectivity.infrared.protocol.RemoteControlRole
import dev.localremote.app.core.model.DeviceCategory

object RemoteControlMappingMapper {
    fun toEntities(
        profileId: String,
        category: DeviceCategory,
        availableButtonIds: Set<String>,
        mappings: List<RemoteControlMapping>
    ): List<IrRemoteControlMappingEntity> {
        require(profileId.isNotBlank()) { "Remote profile ID cannot be blank." }
        val errors = RemoteControlLayoutValidator.validate(
            category = category,
            availableButtonIds = availableButtonIds,
            mappings = mappings
        )
        require(errors.isEmpty()) {
            "Cannot save remote layout: ${errors.joinToString("; ")}"
        }

        return mappings.map { mapping ->
            IrRemoteControlMappingEntity(
                profileId = profileId,
                role = mapping.role.name,
                buttonId = mapping.buttonId
            )
        }
    }

    fun fromEntities(
        category: DeviceCategory,
        availableButtonIds: Set<String>,
        entities: List<IrRemoteControlMappingEntity>
    ): List<RemoteControlMapping> {
        val mappings = entities.map { entity ->
            val role = runCatching {
                RemoteControlRole.valueOf(entity.role)
            }.getOrElse {
                throw IllegalArgumentException("Saved remote layout contains an unknown control role.")
            }
            RemoteControlMapping(role, entity.buttonId)
        }

        val errors = RemoteControlLayoutValidator.validate(
            category = category,
            availableButtonIds = availableButtonIds,
            mappings = mappings
        )
        require(errors.isEmpty()) {
            "Saved remote layout failed validation: ${errors.joinToString("; ")}"
        }
        return mappings
    }
}
