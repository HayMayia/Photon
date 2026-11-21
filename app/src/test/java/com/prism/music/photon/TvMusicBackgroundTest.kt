package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TvMusicBackgroundTest {
    @Test fun existingAndNewInstallsDefaultTvMusicOffWithoutChangingPhoneDefaults() {
        val settings = Json.decodeFromString<PhotonLibrary>("{}")
        assertFalse(settings.tvMusicBackground)
        assertTrue(settings.musicBackground)
        assertFalse(settings.videoBackground)
    }
    @Test fun tvOptInIsIndependentAndOnlyEnablesMusic() {
        assertTrue(MusicPlaybackPolicy.backgroundAllowed(true, false, false, true, true))
        assertFalse(MusicPlaybackPolicy.backgroundAllowed(false, true, true, true, true))
        assertFalse(MusicPlaybackPolicy.backgroundAllowed(true, true, true, true, false))
        assertFalse(MusicPlaybackPolicy.backgroundAllowed(true, false, false, false, true))
        val settings = PhotonLibrary(tvMusicBackground = true, musicBackground = false)
        assertEquals(settings, Json.decodeFromString<PhotonLibrary>(Json.encodeToString(settings)))
    }
}
