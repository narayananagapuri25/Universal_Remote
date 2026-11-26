package dev.localremote.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeRoutingPolicyTest {
    @Test
    fun savedPreferenceWinsWhenCapabilityExists() {
        val result = VolumeRoutingPolicy.recommend(
            VolumeCapabilities(true, true),
            savedPreference = VolumeTarget.SET_TOP_BOX
        )
        assertEquals(VolumeTarget.SET_TOP_BOX, result)
    }

    @Test
    fun defaultsToTelevisionWhenBothTargetsAreAvailable() {
        val result = VolumeRoutingPolicy.recommend(VolumeCapabilities(true, true))
        assertEquals(VolumeTarget.TELEVISION, result)
    }

    @Test
    fun fallsBackToSetTopBoxWhenTelevisionVolumeIsUnavailable() {
        val result = VolumeRoutingPolicy.recommend(
            VolumeCapabilities(false, true),
            savedPreference = VolumeTarget.TELEVISION
        )
        assertEquals(VolumeTarget.SET_TOP_BOX, result)
    }

    @Test
    fun returnsNoRouteWhenNeitherTargetIsVerified() {
        val result = VolumeRoutingPolicy.recommend(VolumeCapabilities(false, false))
        assertNull(result)
    }
}
