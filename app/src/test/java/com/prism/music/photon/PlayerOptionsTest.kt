package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class PlayerOptionsTest {
    @Test fun allQuarterStepPresetsAndFineRangeAreAvailable() {
        assertEquals(40, PlayerOptions.speeds.size)
        assertEquals(.25f, PlayerOptions.speeds.first())
        assertEquals(10f, PlayerOptions.speeds.last())
        assertTrue(PlayerOptions.speeds.containsAll(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 9.75f)))
        assertEquals(.1f, PlayerOptions.speed(0f))
        assertEquals(10f, PlayerOptions.speed(12f))
        assertEquals(1f, PlayerOptions.speed(Float.NaN))
    }
    @Test fun requestedSubtitleSizesAndFineClamping() {
        assertEquals(listOf(25, 50, 75, 100, 125, 150, 175, 200), PlayerOptions.subtitleSizes)
        assertEquals(87, PlayerOptions.subtitleSize(87))
        assertEquals(25, PlayerOptions.subtitleSize(0))
        assertEquals(200, PlayerOptions.subtitleSize(500))
    }
    @Test fun tvBackCanRevealDetailsDespitePermanentLandscape() {
        assertTrue(PlayerOptions.fullscreen(true, true, true, true, false, false))
        assertFalse(PlayerOptions.fullscreen(true, false, true, true, false, false))
        assertTrue(PlayerOptions.fullscreen(false, false, true, true, false, false))
        assertFalse(PlayerOptions.fullscreen(true, true, true, false, false, false))
        assertFalse(PlayerOptions.fullscreen(true, true, true, true, true, false))
    }
    @Test fun existingInstallsHaveSafeDefaultsAndNewSettingsRoundTrip() {
        val old = Json.decodeFromString<PhotonLibrary>("{}")
        assertEquals(1f, old.playbackSpeed)
        assertEquals(100, old.captionSizePercent)
        assertFalse(old.repeatVideo)
        val changed = old.copy(playbackSpeed = 10f, captionSizePercent = 137, captionLanguage = "hi", captionStyle = "Yellow on black", repeatVideo = true, captionsEnabled = false)
        assertEquals(changed, Json.decodeFromString<PhotonLibrary>(Json.encodeToString(changed)))
    }
}
