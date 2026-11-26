package dev.localremote.app.core.model

enum class VolumeTarget {
    TELEVISION,
    SET_TOP_BOX
}

data class VolumeCapabilities(
    val televisionVolumeSupported: Boolean,
    val setTopBoxVolumeSupported: Boolean
) {
    fun supports(target: VolumeTarget): Boolean = when (target) {
        VolumeTarget.TELEVISION -> televisionVolumeSupported
        VolumeTarget.SET_TOP_BOX -> setTopBoxVolumeSupported
    }
}

object VolumeRoutingPolicy {
    /**
     * A valid saved preference wins. Otherwise prefer TV volume, the typical
     * set-top-box remote behaviour. Return null if neither route is verified.
     */
    fun recommend(
        capabilities: VolumeCapabilities,
        savedPreference: VolumeTarget? = null
    ): VolumeTarget? {
        if (savedPreference != null && capabilities.supports(savedPreference)) {
            return savedPreference
        }

        return when {
            capabilities.televisionVolumeSupported -> VolumeTarget.TELEVISION
            capabilities.setTopBoxVolumeSupported -> VolumeTarget.SET_TOP_BOX
            else -> null
        }
    }
}
