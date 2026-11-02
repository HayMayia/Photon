package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LikedMusicCacheTest {
    @Test fun readOnlyAccountSnapshotSurvivesSerialization() {
        val state = PhotonLibrary(likedMusicCache = listOf(Video("abcdefghijk", "Music", "Creator", music = true)), likedMusicRevision = 42, likedMusicUpdatedAt = 123456)
        val restored = Json.decodeFromString<PhotonLibrary>(Json.encodeToString(state))
        assertEquals(state.likedMusicCache, restored.likedMusicCache)
        assertEquals(42L, restored.likedMusicRevision)
        assertEquals(123456L, restored.likedMusicUpdatedAt)
        assertTrue(restored.playlists.isEmpty())
    }
    @Test fun olderInstallsStartWithoutPretendingToHaveAnAccountSnapshot() {
        val old = Json.decodeFromString<PhotonLibrary>("{}")
        assertEquals(-1L, old.likedMusicRevision)
        assertTrue(old.likedMusicCache.isEmpty())
    }
}
