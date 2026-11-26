package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlLayoutValidatorTest {
    private val available = setOf("power-code", "up-code", "ok-code", "volume-code")

    @Test
    fun acceptsSparseUserConfiguredTelevisionLayout() {
        val mappings = listOf(
            RemoteControlMapping(RemoteControlRole.POWER, "power-code"),
            RemoteControlMapping(RemoteControlRole.UP, "up-code"),
            RemoteControlMapping(RemoteControlRole.OK, "ok-code")
        )

        assertTrue(
            RemoteControlLayoutValidator.validate(
                DeviceCategory.TELEVISION,
                available,
                mappings
            ).isEmpty()
        )
    }

    @Test
    fun acceptsSetTopBoxSpecificGuideAndFavouriteRoles() {
        val mappings = listOf(
            RemoteControlMapping(RemoteControlRole.GUIDE, "ok-code"),
            RemoteControlMapping(RemoteControlRole.FAVOURITES, "up-code")
        )

        assertTrue(
            RemoteControlLayoutValidator.validate(
                DeviceCategory.SET_TOP_BOX,
                available,
                mappings
            ).isEmpty()
        )
    }

    @Test
    fun rejectsRoleNotAvailableForCategory() {
        val errors = RemoteControlLayoutValidator.validate(
            DeviceCategory.TELEVISION,
            available,
            listOf(RemoteControlMapping(RemoteControlRole.GUIDE, "ok-code"))
        )

        assertTrue(errors.any { it.contains("not available") })
    }

    @Test
    fun rejectsMappingsToMissingOrBlankButtonIds() {
        val errors = RemoteControlLayoutValidator.validate(
            DeviceCategory.TELEVISION,
            available,
            listOf(
                RemoteControlMapping(RemoteControlRole.POWER, "missing"),
                RemoteControlMapping(RemoteControlRole.OK, "")
            )
        )

        assertEquals(2, errors.size)
        assertTrue(errors.all { it.contains("not in this profile") })
    }

    @Test
    fun rejectsDuplicateRoleMappings() {
        val errors = RemoteControlLayoutValidator.validate(
            DeviceCategory.TELEVISION,
            available,
            listOf(
                RemoteControlMapping(RemoteControlRole.POWER, "power-code"),
                RemoteControlMapping(RemoteControlRole.POWER, "ok-code")
            )
        )

        assertTrue(errors.any { it.contains("assigned only once") })
    }

    @Test
    fun rejectsAirConditionerSemanticLayout() {
        val errors = RemoteControlLayoutValidator.validate(
            DeviceCategory.AIR_CONDITIONER,
            available,
            listOf(RemoteControlMapping(RemoteControlRole.POWER, "power-code"))
        )

        assertTrue(errors.any { it.contains("cannot be assigned") })
    }
}
