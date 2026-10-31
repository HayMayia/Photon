package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PlaybackPoliciesTest {
    @Test fun rightDoubleTapMovesExactlyTenSeconds() { assertEquals(25_000L, VideoSeek.target(15_000, 100_000, true)) }
    @Test fun leftDoubleTapRewindsExactlyTenSeconds() { assertEquals(5_000L, VideoSeek.target(15_000, 100_000, false)) }
    @Test fun seekingClampsAtBothEnds() {
        assertEquals(0L, VideoSeek.target(3_000, 100_000, false))
        assertEquals(100_000L, VideoSeek.target(96_000, 100_000, true))
    }
    @Test fun unknownDurationDoesNotBecomeNegativeSeek() {
        assertEquals(30_000L, VideoSeek.target(20_000, -9223372036854775807L, true))
        assertEquals(0L, VideoSeek.target(0, -1, false))
    }
    @Test fun cacheExpiresEvenWhenFrequentlyRead() {
        var now = 0L
        val cache = ExpiringVideoCache<String>(2, 120_000) { now }
        cache.put("video", "signed-url")
        now = 119_999
        assertEquals("signed-url", cache.get("video"))
        now = 120_000
        assertNull(cache.get("video"))
    }
    @Test fun cacheEvictsLeastRecentlyUsedVideo() {
        val cache = ExpiringVideoCache<String>(2, 100) { 0L }
        cache.put("a", "first"); cache.put("b", "second")
        assertEquals("first", cache.get("a"))
        cache.put("c", "third")
        assertNull(cache.get("b"))
        assertEquals("first", cache.get("a"))
        assertEquals("third", cache.get("c"))
    }
    @Test fun retryCanInvalidateStaleSignedUrls() {
        val cache = ExpiringVideoCache<String>(2, 100) { 0L }
        cache.put("a", "old"); cache.remove("a")
        assertNull(cache.get("a"))
        cache.put("a", "fresh")
        assertEquals("fresh", cache.get("a"))
    }
    @Test fun cachedDetailsAreNeverMixedBetweenVideos() {
        val cache = ExpiringVideoCache<String>(2, 100) { 0L }
        cache.put("videoA", "detailsA")
        assertNull(cache.get("videoB"))
    }
}
