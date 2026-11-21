package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class LikedMusicProgressTest {
    @Test fun newSongsDoNotStayBehindOldCacheWhileUnknownRowsRemain() {
        val old = Video("oldVideo001", "Old", "Creator", music = true)
        val new = Video("newVideo001", "New", "Creator", music = true)
        assertEquals(listOf(new, old), LikedMusicHeadUpdates.progressive(listOf(old), listOf(new)))
        assertEquals(listOf(new, old), LikedMusicHeadUpdates.progressive(listOf(old, new), listOf(new)))
    }
    @Test fun accountRefreshDoesNotInvalidateClassificationEverySixHours() {
        val snapshot = LikedMusicSnapshot(7, updatedAt = 100, schemaVersion = 2, scanComplete = true,
            classifications = mapOf("abcdefghijk" to MusicClassification(false, 100)))
        val later = 100 + 7 * 60 * 60_000L
        assertTrue(LikedMusicFreshness.needsRefresh(snapshot, later))
        assertEquals(false, MusicClassificationPolicy.verdict(snapshot, 7, "abcdefghijk", later))
        assertNull(MusicClassificationPolicy.verdict(snapshot, 8, "abcdefghijk", later))
    }
    @Test fun multipleHeadPagesKeepNewestSourceOrderAndOlderSongs() {
        val videos = (1..4).map { Video("video$it", "Song $it", "Creator", music = true) }
        val first = LikedMusicHeadUpdates.merge(listOf(videos[3]), listOf(videos[0]), listOf("video1", "video2"))
        assertEquals(videos, LikedMusicHeadUpdates.merge(first, listOf(videos[1], videos[2]), listOf("video1", "video2", "video3")))
    }
}
