package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaylistShortcutPolicyTest {
    @Test fun unassignedRequiresChooserAndSelectedPlaylistPersists() {
        assertFalse(PlaylistShortcutPolicy.selected(PhotonLibrary()))
        val settings = PhotonLibrary(youtubeHome = true, youtubeSessionReady = true, youtubeSessionRevision = 7,
            playlistShortcutId = "PL_my_music", playlistShortcutTitle = "My music", playlistShortcutRevision = 7)
        val restored = Json.decodeFromString<PhotonLibrary>(Json.encodeToString(settings))
        assertTrue(PlaylistShortcutPolicy.selected(restored))
        assertEquals("My music", restored.playlistShortcutTitle)
    }
    @Test fun otherAccountsSignedOutAndUntrustedIdsCannotOpenAnOldShortcut() {
        val selected = PhotonLibrary(youtubeHome = true, youtubeSessionRevision = 7,
            playlistShortcutId = "PL_native", playlistShortcutRevision = 7)
        assertFalse(PlaylistShortcutPolicy.selected(selected.copy(youtubeSessionRevision = 8)))
        assertFalse(PlaylistShortcutPolicy.selected(selected.copy(youtubeSessionReady = false)))
        assertFalse(PlaylistShortcutPolicy.selected(selected.copy(youtubeHome = false)))
        assertFalse(PlaylistShortcutPolicy.selected(selected.copy(playlistShortcutId = "https://example.test")))
        assertFalse(PlaylistShortcutPolicy.selected(selected.copy(playlistShortcutId = "")))
    }
}
