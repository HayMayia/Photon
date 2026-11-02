package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LikedMusicCacheTest {
    @Test fun readOnlyAccountSnapshotSurvivesSerialization() {
        val state = LikedMusicSnapshot(42, listOf(Video("abcdefghijk", "Music", "Creator", music = true)), 123456)
        val restored = Json.decodeFromString<LikedMusicSnapshot>(Json.encodeToString(state))
        assertEquals(state.videos, restored.videos)
        assertEquals(42L, restored.revision)
        assertEquals(123456L, restored.updatedAt)
        assertFalse(Json.encodeToString(PhotonLibrary()).contains("likedMusicCache"))
    }
    @Test fun completingAnOlderScanCannotLoseAConfirmedNewLikeOrRestoreAnUnlike() {
        val old = Video("abcdefghijk", "Old", "Creator", music = true)
        val new = Video("otherVideo1", "New", "Creator", music = true)
        val changes = listOf(LikedMusicSnapshotUpdates.Change(2, new, true), LikedMusicSnapshotUpdates.Change(3, old, false))
        assertEquals(listOf(new), LikedMusicSnapshotUpdates.merge(listOf(old), changes, 1))
        assertEquals(listOf(old), LikedMusicSnapshotUpdates.merge(listOf(old), changes, 3))
    }
    @Test fun olderInstallsStartWithoutPretendingToHaveAnAccountSnapshot() {
        val old = Json.decodeFromString<LikedMusicSnapshot>("{}")
        assertEquals(-1L, old.revision)
        assertTrue(old.videos.isEmpty())
    }
}
