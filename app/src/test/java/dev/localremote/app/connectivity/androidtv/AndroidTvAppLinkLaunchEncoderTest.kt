package dev.localremote.app.connectivity.androidtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AndroidTvAppLinkLaunchEncoderTest {
    @Test
    fun encodesYoutubeShortcutAsRemoteMessageField90() {
        assertShortcut(AndroidTvAppShortcut.YOUTUBE, "https://www.youtube.com/")
    }

    @Test
    fun encodesNetflixShortcutAsRemoteMessageField90() {
        assertShortcut(AndroidTvAppShortcut.NETFLIX, "https://www.netflix.com/")
    }

    @Test
    fun encodesPrimeVideoShortcutAsRemoteMessageField90() {
        assertShortcut(AndroidTvAppShortcut.PRIME_VIDEO, "https://app.primevideo.com/")
    }

    @Test
    fun onlyTheThreeCuratedShortcutsAreExposed() {
        assertEquals(3, AndroidTvAppShortcut.entries.size)
        assertNotNull(AndroidTvAppShortcut.entries.firstOrNull { it.label == "YouTube" })
        assertNotNull(AndroidTvAppShortcut.entries.firstOrNull { it.label == "Netflix" })
        assertNotNull(AndroidTvAppShortcut.entries.firstOrNull { it.label == "Prime Video" })
    }

    private fun assertShortcut(shortcut: AndroidTvAppShortcut, expectedLink: String) {
        val message = ProtoReader.parse(AndroidTvAppLinkLaunchEncoder.encodeRemoteMessage(shortcut))
        val payload = checkNotNull(ProtoReader.firstBytes(message, 90))
        val request = ProtoReader.parse(payload)
        val encodedLink = checkNotNull(ProtoReader.firstBytes(request, 1))
        assertEquals(expectedLink, encodedLink.toString(Charsets.UTF_8))
    }
}