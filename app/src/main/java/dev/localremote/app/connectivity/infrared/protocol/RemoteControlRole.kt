package dev.localremote.app.connectivity.infrared.protocol

import dev.localremote.app.core.model.DeviceCategory

/** A user-assigned role in a TV or set-top-box remote layout. */
enum class RemoteControlRole(val label: String, val group: Group) {
    POWER("Power", Group.SYSTEM),
    INPUT("Input / Source", Group.SYSTEM),
    HOME("Home", Group.SYSTEM),
    MENU("Menu", Group.SYSTEM),
    BACK("Back", Group.SYSTEM),
    GUIDE("Guide / EPG", Group.SYSTEM),
    INFO("Info", Group.SYSTEM),
    FAVOURITES("Favourites", Group.SYSTEM),

    UP("Up", Group.NAVIGATION),
    DOWN("Down", Group.NAVIGATION),
    LEFT("Left", Group.NAVIGATION),
    RIGHT("Right", Group.NAVIGATION),
    OK("OK / Enter", Group.NAVIGATION),

    VOLUME_UP("Volume +", Group.VOLUME),
    VOLUME_DOWN("Volume −", Group.VOLUME),
    MUTE("Mute", Group.VOLUME),
    CHANNEL_UP("Channel +", Group.CHANNEL),
    CHANNEL_DOWN("Channel −", Group.CHANNEL),

    DIGIT_0("0", Group.NUMERIC),
    DIGIT_1("1", Group.NUMERIC),
    DIGIT_2("2", Group.NUMERIC),
    DIGIT_3("3", Group.NUMERIC),
    DIGIT_4("4", Group.NUMERIC),
    DIGIT_5("5", Group.NUMERIC),
    DIGIT_6("6", Group.NUMERIC),
    DIGIT_7("7", Group.NUMERIC),
    DIGIT_8("8", Group.NUMERIC),
    DIGIT_9("9", Group.NUMERIC),

    REWIND("Rewind", Group.PLAYBACK),
    PLAY_PAUSE("Play / Pause", Group.PLAYBACK),
    STOP("Stop", Group.PLAYBACK),
    FAST_FORWARD("Fast forward", Group.PLAYBACK);

    enum class Group {
        SYSTEM,
        NAVIGATION,
        VOLUME,
        CHANNEL,
        NUMERIC,
        PLAYBACK
    }

    companion object {
        fun forCategory(category: DeviceCategory): List<RemoteControlRole> =
            when (category) {
                DeviceCategory.TELEVISION -> entries.filter {
                    it in TV_ROLES
                }
                DeviceCategory.SET_TOP_BOX -> entries.filter {
                    it in STB_ROLES
                }
                DeviceCategory.AIR_CONDITIONER -> emptyList()
            }

        private val TV_ROLES = setOf(
            POWER, INPUT, HOME, MENU, BACK,
            UP, DOWN, LEFT, RIGHT, OK,
            VOLUME_UP, VOLUME_DOWN, MUTE,
            CHANNEL_UP, CHANNEL_DOWN,
            DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
            DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
            REWIND, PLAY_PAUSE, STOP, FAST_FORWARD
        )

        private val STB_ROLES = setOf(
            POWER, HOME, MENU, BACK, GUIDE, INFO, FAVOURITES,
            UP, DOWN, LEFT, RIGHT, OK,
            VOLUME_UP, VOLUME_DOWN, MUTE,
            CHANNEL_UP, CHANNEL_DOWN,
            DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
            DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
            REWIND, PLAY_PAUSE, STOP, FAST_FORWARD
        )
    }
}

data class RemoteControlMapping(
    val role: RemoteControlRole,
    val buttonId: String
)

/**
 * Layout mapping is deliberately user-authored. It never infers which imported
 * command means Power, Volume or another action from a file label.
 */
object RemoteControlLayoutValidator {
    fun validate(
        category: DeviceCategory,
        availableButtonIds: Set<String>,
        mappings: List<RemoteControlMapping>
    ): List<String> {
        val errors = mutableListOf<String>()
        val supportedRoles = RemoteControlRole.forCategory(category).toSet()

        if (category == DeviceCategory.AIR_CONDITIONER) {
            errors += "Semantic TV/STB layouts cannot be assigned to an air conditioner."
        }

        if (mappings.map { it.role }.distinct().size != mappings.size) {
            errors += "Each remote control role can be assigned only once."
        }

        mappings.forEach { mapping ->
            if (mapping.role !in supportedRoles) {
                errors += "${mapping.role.label} is not available for this device category."
            }
            if (mapping.buttonId.isBlank() || mapping.buttonId !in availableButtonIds) {
                errors += "${mapping.role.label} refers to a command that is not in this profile."
            }
        }

        return errors.distinct()
    }
}
