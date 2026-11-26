package dev.localremote.app.connectivity.androidtv

import java.net.URI

/** Fixed HTTPS links; app launch is best-effort and requires a matching TV app/intent filter. */
enum class AndroidTvAppShortcut(
    val label: String,
    val appLink: String
) {
    YOUTUBE("YouTube", "https://www.youtube.com/"),
    NETFLIX("Netflix", "https://www.netflix.com/"),
    PRIME_VIDEO("Prime Video", "https://app.primevideo.com/")
}

internal object AndroidTvAppLinkLaunchEncoder {
    private val allowedHosts = setOf(
        "www.youtube.com",
        "www.netflix.com",
        "app.primevideo.com"
    )

    /** Encodes the RemoteAppLinkLaunchRequest payload; field 90 is its RemoteMessage envelope. */
    fun encode(shortcut: AndroidTvAppShortcut): ByteArray {
        val uri = URI(shortcut.appLink)
        require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
            "TV app shortcuts must use HTTPS links."
        }
        val host = uri.host?.lowercase()
        require(host != null && host in allowedHosts && uri.userInfo == null && uri.port in setOf(-1, 443)) {
            "TV app shortcut link is not on the approved host list."
        }
        require(shortcut.appLink.length <= MAX_LINK_LENGTH) {
            "TV app shortcut link is too long."
        }
        return ProtoWriter().string(1, shortcut.appLink).toByteArray()
    }

    fun encodeRemoteMessage(shortcut: AndroidTvAppShortcut): ByteArray =
        ProtoWriter().bytes(90, encode(shortcut)).toByteArray()

    private const val MAX_LINK_LENGTH = 512
}