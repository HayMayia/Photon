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
    @Test fun absoluteShortCommandAndExplicitPlayerFlagsAreBoundToTheVideo() {
        val command = """<script>var ytInitialData={"commandMetadata":{"webCommandMetadata":{"url":"https://www.youtube.com/shorts/abcdefghijk"}}};</script>"""
        assertEquals(true, ShortsPresentation.fromHtml("abcdefghijk", command)?.first)
        val player = """<script>var ytInitialPlayerResponse={"videoDetails":{"videoId":"abcdefghijk","isShorts":true}};</script>"""
        assertEquals(true, ShortsPresentation.fromHtml("abcdefghijk", player)?.first)
        assertNull(ShortsPresentation.fromHtml("zzzzzzzzzzz", player))
    }
    @Test fun protocolRelativeAvatarsBecomeTrustedHttpsUrls() {
        assertEquals("https://yt3.ggpht.com/photo", ShortsPresentation.safeArtwork("//yt3.ggpht.com/photo"))
        assertEquals("", ShortsPresentation.safeArtwork("//evil.example/photo"))
    }

}
