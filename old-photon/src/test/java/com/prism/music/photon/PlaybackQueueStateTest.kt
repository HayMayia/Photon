package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueStateTest {
    private val a = Video("aaaaaaaaaaa", "A", "Artist", music = true)
    private val b = Video("bbbbbbbbbbb", "B", "Artist", music = true)
    private val c = Video("ccccccccccc", "C", "Artist", music = true)
    private val random = Video("randomvideo", "Not liked", "Other")
    @Test fun likedMusicNeverFallsThroughToRecommendationsEvenAfterRecreation() {
        val state = PlaybackQueueState(listOf(a,b,c), b.id, "account:7:liked-music", 7, listOf(random))
        val restored = Json.decodeFromString<PlaybackQueueState>(Json.encodeToString(state))
        assertEquals(c, restored.next())
        assertEquals(a, restored.previous())
        assertEquals(listOf(c), restored.upcoming())
        val end = restored.move(c, listOf(random))
        assertNull(end.next())
        assertEquals(b, end.previous())
        assertTrue(end.upcoming().isEmpty())
    }
    @Test fun previousSelectsThePreviousVideoNotTheStartOfTheCurrentOne() {
        val state = PlaybackQueueState(listOf(a,b,c), c.id, "playlist")
        assertEquals(b, state.previous())
        assertEquals(a, state.move(b, emptyList()).previous())
        assertNull(state.move(a, emptyList()).previous())
    }
    @Test fun recommendationsMaintainBackAndForwardHistory() {
        val start = PlaybackQueueState(listOf(a), a.id, related = listOf(a,b,c))
        assertEquals(b, start.next())
        val second = start.move(b, listOf(c))
        assertEquals(a, second.previous())
        val back = second.move(a, listOf(random))
        assertEquals(b, back.next())
    }
    @Test fun missingCurrentPlaylistItemNeverFallsThroughToRandomRecommendations() {
        val state = PlaybackQueueState(listOf(a,b), c.id, "liked-music", related = listOf(random))
        assertNull(state.next()); assertNull(state.previous())
    }
}
