package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PresentationPoliciesTest {
    @Test fun shortsArePortraitAndRegularVideosLandscape() {
        assertEquals(9f / 16f, VideoArtworkShape.ratio(true), 0f)
        assertEquals(16f / 9f, VideoArtworkShape.ratio(false), 0f)
        assertTrue(VideoArtworkShape.rowWidth(true) < VideoArtworkShape.rowWidth(false))
    }
    @Test fun validDraftEnablesAuthorizationOrReviewWithoutACommentGrantYet() {
        assertTrue(CommentComposerPolicy.canReview("My comment", false, false, false))
        assertNull(CommentComposerPolicy.reason("My comment", false, false, false))
    }
    @Test fun exactCommentLimitAndWhitespaceMatchThePostingApi() {
        assertTrue(CommentComposerPolicy.validDraft("x".repeat(10_000)))
        assertTrue(CommentComposerPolicy.validDraft("  " + "x".repeat(10_000) + "  "))
        assertFalse(CommentComposerPolicy.validDraft("x".repeat(10_001)))
        assertFalse(CommentComposerPolicy.validDraft("  \n  "))
    }
    @Test fun busyAndDisabledCommentsRemainProtectedWithAnExplanation() {
        for (flags in listOf(Triple(true, false, false), Triple(false, true, false), Triple(false, false, true))) {
            assertFalse(CommentComposerPolicy.canReview("Valid", flags.first, flags.second, flags.third))
            assertNotNull(CommentComposerPolicy.reason("Valid", flags.first, flags.second, flags.third))
        }
    }
    @Test fun explicitShortsRendererMetadataSurvivesParsing() {
        val root = Json.parseToJsonElement("""{"reelItemRenderer":{"videoId":"abcdefghijk","headline":{"simpleText":"Short"},"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/abcdefghijk/portrait.jpg"}]}}}""")
        val video = YouTubeHomeParser.cards(root).videos.single()
        assertTrue(video.short)
        assertTrue(video.thumbnail.endsWith("portrait.jpg"))
    }
    @Test fun modernShortsUseTheReturnedPortraitSource() {
        val root = Json.parseToJsonElement("""{"shortsLockupViewModel":{"onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"abcdefghijk"}}},"overlayMetadata":{"primaryText":{"content":"Short"}},"thumbnail":{"sources":[{"url":"https://i.ytimg.com/vi/abcdefghijk/portrait.jpg"}]}}}""")
        assertTrue(YouTubeHomeParser.cards(root).videos.single().short)
    }
    @Test fun shortDurationAloneDoesNotMakeAVideoAShort() {
        val root = Json.parseToJsonElement("""{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Short regular video"},"lengthText":{"simpleText":"0:10"}}}""")
        assertFalse(YouTubeHomeParser.cards(root).videos.single().short)
    }
}
