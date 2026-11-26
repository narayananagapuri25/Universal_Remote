package dev.localremote.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSafetyPolicyTest {
    @Test
    fun automaticRetryIsDisabledForIdempotentCommands() {
        val command = RemoteCommand("tv-1", "volume-up", CommandSemantics.IDEMPOTENT)
        assertFalse(CommandSafetyPolicy.mayAutomaticallyRetry(command))
    }

    @Test
    fun automaticRetryIsDisabledForUnknownCommands() {
        val command = RemoteCommand("ac-1", "power-toggle", CommandSemantics.UNKNOWN)
        assertFalse(CommandSafetyPolicy.mayAutomaticallyRetry(command))
    }

    @Test
    fun toggleIsOnlyAllowedWhenProtocolRequiresIt() {
        assertFalse(
            CommandSafetyPolicy.isPowerActionSupported(
                PowerAction.POWER_TOGGLE,
                protocolHasDiscretePowerCommands = true
            )
        )
        assertTrue(
            CommandSafetyPolicy.isPowerActionSupported(
                PowerAction.POWER_TOGGLE,
                protocolHasDiscretePowerCommands = false
            )
        )
    }
}
