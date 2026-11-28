package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MusicRecoveryPolicyTest {
    private val song = Video("abcdefghijk", "Song", "Artist", music = true)
    @Test fun overrideDefaultsOnForExistingInstallations() {
        val library = Json.decodeFromString<PhotonLibrary>("{}")
        assertTrue(library.musicOverride)
        assertTrue(MusicRecoveryPolicy.allowed(song, library, false))
    }
    @Test fun optOutAndBackgroundPreferencesAreRespected() {
        assertFalse(MusicRecoveryPolicy.allowed(song, PhotonLibrary(musicOverride = false), false))
        assertFalse(MusicRecoveryPolicy.allowed(song, PhotonLibrary(musicBackground = false), false))
        assertFalse(MusicRecoveryPolicy.allowed(song, PhotonLibrary(), true))
        assertTrue(MusicRecoveryPolicy.allowed(song, PhotonLibrary(tvMusicBackground = true), true))
        assertFalse(MusicRecoveryPolicy.allowed(song.copy(music = false), PhotonLibrary(videoBackground = true), false))
        assertFalse(MusicRecoveryPolicy.allowed(null, PhotonLibrary(), false))
    }
    @Test fun resumeNeverRestartsAtAnEndedOrNegativePosition() {
        assertEquals(0L, MusicRecoveryPolicy.position(99_000, 100_000))
        assertEquals(0L, MusicRecoveryPolicy.position(-1, 100_000))
        assertEquals(30_000L, MusicRecoveryPolicy.position(30_000, 100_000))
    }
}
