package com.prism.music.photon

import com.prism.music.data.prefs.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SketchHomeSettingsTest {
    @Test fun performanceModeIsOptionalAndDoesNotEraseGlassChoice() {
        assertFalse(Json.decodeFromString<PhotonLibrary>("{}").performanceMode)
        val library = PhotonLibrary(performanceMode = true, liquidGlass = true, tvMusicBackground = true)
        assertEquals(library, Json.decodeFromString<PhotonLibrary>(Json.encodeToString(library)))
        val effective = AppSettings(performanceMode = library.performanceMode, liquidGlass = library.liquidGlass).effective()
        assertFalse(effective.liquidGlass)
        assertFalse(effective.frostedGlass)
        assertTrue(effective.reduceMotion)
        assertTrue(library.liquidGlass)
        assertTrue(library.tvMusicBackground)
    }
    @Test fun greetingUsesTimeAndNeverInventsAnAccountName() {
        assertEquals("Good morning", homeGreeting(8))
        assertEquals("Good afternoon", homeGreeting(14))
        assertEquals("Good evening", homeGreeting(21))
    }
}
