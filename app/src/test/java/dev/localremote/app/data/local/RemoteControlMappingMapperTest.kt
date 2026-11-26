package dev.localremote.app.data.local

import dev.localremote.app.connectivity.infrared.protocol.RemoteControlMapping
import dev.localremote.app.connectivity.infrared.protocol.RemoteControlRole
import dev.localremote.app.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlMappingMapperTest {
    private val buttonIds = setOf("power", "ok", "guide", "up")

    @Test
    fun roundTripsUserAssignedTelevisionControls() {
        val mappings = listOf(
            RemoteControlMapping(RemoteControlRole.POWER, "power"),
            RemoteControlMapping(RemoteControlRole.OK, "ok"),
            RemoteControlMapping(RemoteControlRole.UP, "up")
        )

        val entities = RemoteControlMappingMapper.toEntities(
            "tv-profile",
            DeviceCategory.TELEVISION,
            buttonIds,
            mappings
        )
        val restored = RemoteControlMappingMapper.fromEntities(
            DeviceCategory.TELEVISION,
            buttonIds,
            entities
        )

        assertEquals(mappings, restored)
        assertTrue(entities.all { it.profileId == "tv-profile" })
    }

    @Test
    fun roundTripsSetTopBoxGuideMapping() {
        val mapping = RemoteControlMapping(RemoteControlRole.GUIDE, "guide")
        val entity = RemoteControlMappingMapper.toEntities(
            "stb-profile",
            DeviceCategory.SET_TOP_BOX,
            buttonIds,
            listOf(mapping)
        ).single()

        assertEquals("GUIDE", entity.role)
        assertEquals(
            listOf(mapping),
            RemoteControlMappingMapper.fromEntities(
                DeviceCategory.SET_TOP_BOX,
                buttonIds,
                listOf(entity)
            )
        )
    }

    @Test
    fun refusesToSaveUnknownCommandButtonId() {
        val failure = runCatching {
            RemoteControlMappingMapper.toEntities(
                "tv-profile",
                DeviceCategory.TELEVISION,
                buttonIds,
                listOf(RemoteControlMapping(RemoteControlRole.POWER, "not-imported"))
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun refusesUnknownPersistedRoleInsteadOfGuessing() {
        val bad = IrRemoteControlMappingEntity(
            profileId = "tv-profile",
            role = "PANIC",
            buttonId = "power"
        )

        val failure = runCatching {
            RemoteControlMappingMapper.fromEntities(
                DeviceCategory.TELEVISION,
                buttonIds,
                listOf(bad)
            )
        }.exceptionOrNull()

        assertEquals(
            "Saved remote layout contains an unknown control role.",
            failure?.message
        )
    }
}
