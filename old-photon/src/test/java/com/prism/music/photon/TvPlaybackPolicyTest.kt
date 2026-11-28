package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TvPlaybackPolicyTest {
    private val a = Video("aaaaaaaaaaa", "Subscribed Short", "Subscribed creator", short = true)
    private val b = Video("bbbbbbbbbbb", "Subscribed Short 2", "Subscribed creator", short = true)
    private val random = Video("randomvideo", "Unrelated", "Other creator")
    @Test fun missingCurrentNeverDefaultsToFirstOrSecondVideo() {
        assertNull(TvPlaybackPolicy.adjacent(listOf(a,b), "missing", 1))
        assertNull(TvPlaybackPolicy.adjacent(listOf(a,b), a.id, -1))
        assertEquals(b, TvPlaybackPolicy.adjacent(listOf(a,random,b), a.id, 1))
        assertNull(TvPlaybackPolicy.adjacent(listOf(a,b), b.id, 1))
    }
    @Test fun subscriptionsAndShortsNeverAutoAdvanceAndNeverMixInRelated() {
        val source = PlaybackQueueState(listOf(a,b), a.id, "feed:subscriptions", related = listOf(random), recommendationsAllowed = false)
        assertFalse(TvPlaybackPolicy.autoAdvance(source, true))
        assertFalse(TvPlaybackPolicy.autoAdvance(source.copy(source = null), true))
        assertEquals(b, source.next())
        assertNull(source.move(b, listOf(random)).next())
        assertFalse(TvPlaybackPolicy.playlist(source.source))
    }
    @Test fun recommendationPreferenceSurvivesPersistenceAndQueueChanges() {
        val state = PlaybackQueueState(listOf(random), random.id, related = listOf(a), recommendationsAllowed = false)
        val restored = Json.decodeFromString<PlaybackQueueState>(Json.encodeToString(state))
        assertNull(restored.next()); assertTrue(restored.upcoming().isEmpty())
        assertFalse(TvPlaybackPolicy.autoAdvance(restored, false))
        assertTrue(restored.move(random, listOf(b)).related.isEmpty())
    }
}
