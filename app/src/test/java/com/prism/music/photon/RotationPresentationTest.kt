package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class RotationPresentationTest {
    @Test fun physicalRotationUsesTheSameFullscreenLayoutAsTheButton() {
        assertTrue(PlayerLayoutPolicy.immersive(false, true, true, false, false))
        assertTrue(PlayerLayoutPolicy.immersive(true, false, true, false, false))
        assertFalse(PlayerLayoutPolicy.immersive(false, false, true, false, false))
    }
    @Test fun minimizedPipAndShortsDoNotForceTheWatchLayout() {
        assertFalse(PlayerLayoutPolicy.immersive(false, true, false, false, false))
        assertFalse(PlayerLayoutPolicy.immersive(true, true, true, true, false))
        assertFalse(PlayerLayoutPolicy.immersive(true, true, true, false, true))
    }
    @Test fun shortsWithWatchCanonicalAreRecognizedThroughTheMatchingReelCommand() {
        val id = "abcdefghijk"
        val html = """<head><link rel="canonical" href="https://www.youtube.com/watch?v=$id"></head><script>var ytInitialData={"endpoint":{"reelWatchEndpoint":{"videoId":"$id"}}};</script>"""
        assertEquals(true, ShortsPresentation.fromHtml(id, html)?.first)
    }
    @Test fun recommendedDifferentShortDoesNotClassifyTheCurrentVideo() {
        val html = """<head><link rel="canonical" href="https://www.youtube.com/watch?v=abcdefghijk"></head><script>var ytInitialData={"endpoint":{"reelWatchEndpoint":{"videoId":"zzzzzzzzzzz"}}};</script>"""
        assertEquals(false, ShortsPresentation.fromHtml("abcdefghijk", html)?.first)
    }
    @Test fun commandUrlAlsoIdentifiesPlaylistShorts() {
        val html = """<script>var ytInitialData={"commandMetadata":{"webCommandMetadata":{"url":"/shorts/abcdefghijk?feature=share"}}};</script>"""
        assertEquals(true, ShortsPresentation.fromHtml("abcdefghijk", html)?.first)
    }
}
