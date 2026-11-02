package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PlaylistQueuePolicyTest {
    private fun video(id: String) = Video(id, id, "Creator")
    @Test fun playlistUpNextStartsAfterTheSelectedEntryInOriginalOrder() {
        val queue = listOf(video("first"), video("second"), video("third"))
        assertEquals(queue.drop(1), PlaylistQueuePolicy.upcoming(queue, "first", true, listOf(video("unrelated"))))
        assertEquals(queue.drop(2), PlaylistQueuePolicy.upcoming(queue, "second", true, emptyList()))
    }
    @Test fun endOfPlaylistOrMissingSelectionNeverFallsThroughToRandomRecommendations() {
        val queue = listOf(video("last"))
        assertTrue(PlaylistQueuePolicy.upcoming(queue, "last", true, listOf(video("unrelated"))).isEmpty())
        assertTrue(PlaylistQueuePolicy.upcoming(queue, "missing", true, listOf(video("unrelated"))).isEmpty())
    }
    @Test fun continuationAppendsWithoutReorderingOrDuplicatingEntries() {
        val first = video("first"); val second = video("second"); val third = video("third")
        assertEquals(listOf(first, second, third), PlaylistQueuePolicy.append(listOf(first, second), listOf(second, third)))
    }
    @Test fun unrelatedVideoCanStillUseItsOwnRecommendations() {
        val related = listOf(video("related"))
        assertEquals(related, PlaylistQueuePolicy.upcoming(listOf(video("old-playlist")), "other", false, related))
        assertNotEquals(PlaylistQueuePolicy.key(1, "PL_same"), PlaylistQueuePolicy.key(2, "PL_same"))
    }
}
