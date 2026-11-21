package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassSettingsTest {
    @Test fun existingInstallsReceiveGlassAndCanPersistOptOut() {
        assertTrue(Json.decodeFromString<PhotonLibrary>("{}").liquidGlass)
        val settings = PhotonLibrary(liquidGlass = false, tvMusicBackground = true)
        val restored = Json.decodeFromString<PhotonLibrary>(Json.encodeToString(settings))
        assertFalse(restored.liquidGlass)
        assertTrue(restored.tvMusicBackground)
        assertEquals(settings, restored)
    }
}
