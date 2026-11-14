package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EarlyShortsRendererTest {
    private fun node(json: String) = Json.parseToJsonElement(json).jsonObject
    @Test fun provesMatchingReelAndShortsRouteBeforeMetadata() {
        assertTrue(ShortsPresentation.fromRenderer(node("""{"navigationEndpoint":{"reelWatchEndpoint":{"videoId":"abcdefghijk"}}}"""), "abcdefghijk"))
        assertTrue(ShortsPresentation.fromRenderer(node("""{"navigationEndpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/shorts/abcdefghijk"}}}}"""), "abcdefghijk"))
        assertFalse(ShortsPresentation.fromRenderer(node("""{"navigationEndpoint":{"reelWatchEndpoint":{"videoId":"otherVideo1"}}}"""), "abcdefghijk"))
    }
    @Test fun provesExplicitBadgeButNeverDurationPortraitOrTitle() {
        assertTrue(ShortsPresentation.fromRenderer(node("""{"thumbnailOverlays":[{"thumbnailOverlayTimeStatusRenderer":{"style":"SHORTS"}}]}"""), "abcdefghijk"))
        assertFalse(ShortsPresentation.fromRenderer(node("""{"title":"Shorts","lengthSeconds":"12","thumbnail":{"width":480,"height":854}}"""), "abcdefghijk"))
    }
    @Test fun parsesModernVideoLockupWithEarlyShortsProof() {
        val root = node("""{"lockupViewModel":{"contentId":"abcdefghijk","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"title":{"content":"Actual title"}}},"contentImage":{"thumbnailViewModel":{"sources":[{"url":"https://i.ytimg.com/portrait.jpg","width":480,"height":854}]}},"rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"abcdefghijk"}}}}}}}""")
        val video = YouTubeHomeParser.cards(root).videos.single()
        assertEquals("Actual title", video.title); assertTrue(video.short)
        assertEquals("https://i.ytimg.com/portrait.jpg", video.thumbnail)
    }
    @Test fun modernOrdinaryVideoDoesNotBecomeShortFromPortrait() {
        val root = node("""{"lockupViewModel":{"contentId":"abcdefghijk","contentType":"LOCKUP_CONTENT_TYPE_VIDEO","metadata":{"lockupMetadataViewModel":{"title":{"content":"Trailer"}}},"contentImage":{"thumbnailViewModel":{"sources":[{"url":"https://i.ytimg.com/portrait.jpg","width":480,"height":854}]}}}}""")
        assertFalse(YouTubeHomeParser.cards(root).videos.single().short)
    }
    @Test fun playlistRendererKeepsEarlyShortsProof() {
        val root = node("""{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Actual Short"},"navigationEndpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/shorts/abcdefghijk"}}}}}""")
        assertTrue(YouTubeHomeParser.cards(root).videos.single().short)
    }
}
