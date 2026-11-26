package dev.localremote.app.core.model

enum class CommandSemantics {
    IDEMPOTENT,
    NON_IDEMPOTENT,
    UNKNOWN
}

enum class PowerAction {
    POWER_ON,
    POWER_OFF,
    POWER_TOGGLE
}

data class RemoteCommand(
    val deviceId: String,
    val actionId: String,
    val semantics: CommandSemantics
)

object CommandSafetyPolicy {
    /**
     * Baseline policy: all retries are user-initiated, never automatic.
     */
    fun mayAutomaticallyRetry(
        @Suppress("UNUSED_PARAMETER") command: RemoteCommand
    ): Boolean = false

    /**
     * Prefer explicit power actions. Toggle is valid only when the selected
     * device protocol has no distinct On/Off command.
     */
    fun isPowerActionSupported(
        action: PowerAction,
        protocolHasDiscretePowerCommands: Boolean
    ): Boolean = when (action) {
        PowerAction.POWER_ON, PowerAction.POWER_OFF -> protocolHasDiscretePowerCommands
        PowerAction.POWER_TOGGLE -> !protocolHasDiscretePowerCommands
    }
}
