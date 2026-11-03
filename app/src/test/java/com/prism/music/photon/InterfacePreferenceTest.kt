package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class InterfacePreferenceTest {
    @Test fun oldInstallationsKeepPrismUntilTheyChooseToSwitch() {
        val library = Json.decodeFromString<PhotonLibrary>("""{"dark":true,"youtubeHome":true,"youtubeSessionRevision":42}""")
        assertFalse(library.youtubeLayout)
        assertTrue(library.youtubeHome)
        assertEquals(42L, library.youtubeSessionRevision)
    }
    @Test fun interfaceRoundTripDoesNotResetAccountPrivacyOrDownloads() {
        val before = PhotonLibrary(youtubeHome = true, youtubeWebUser = 2, youtubeSessionRevision = 42,
            accountHistorySync = true, includeShortPlays = true, historyPaused = true,
            downloads = listOf(SavedDownload(Video("abcdefghijk", "Example", "Channel", short = true), "/private/example.mp4", 1080, 100)))
        val switched = before.copy(youtubeLayout = true)
        val restored = Json.decodeFromString<PhotonLibrary>(Json.encodeToString(switched))
        assertTrue(restored.youtubeLayout)
        assertEquals(before, restored.copy(youtubeLayout = false))
    }
}
