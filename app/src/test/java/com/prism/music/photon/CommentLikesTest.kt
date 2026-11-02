package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CommentLikesTest {
    private fun fixture(id: String = "comment-1", liked: Boolean = false, token: String = "server-action") = Json.parseToJsonElement("""{"commentRenderer":{"commentId":"$id","actionButtons":{"commentActionButtonsRenderer":{"likeButton":{"toggleButtonRenderer":{"isToggled":$liked,"defaultServiceEndpoint":{"performCommentActionEndpoint":{"action":"$token"}}}}}}}}""").jsonObject
    @Test fun likeActionIsBoundToTheExactComment() {
        assertEquals("server-action", YouTubeCommentLikes.control(fixture(), "comment-1")?.action)
        assertNull(YouTubeCommentLikes.control(fixture(), "different-comment"))
    }
    @Test fun alreadyLikedCommentDoesNotNeedAnotherWrite() { assertTrue(YouTubeCommentLikes.control(fixture(liked = true), "comment-1")!!.liked) }
    @Test fun missingActionIsNotFabricated() { assertNull(YouTubeCommentLikes.control(fixture(token = ""), "comment-1")) }
    @Test fun steadyPlaybackHasRecoveryHeadroomWithoutSlowingFirstFrame() {
        assertEquals(750, StartupPolicy.START_BUFFER_MS)
        assertTrue(StartupPolicy.REBUFFER_MS >= 5000)
        assertTrue(StartupPolicy.MIN_BUFFER_MS >= 45000)
        assertTrue(StartupPolicy.MAX_BUFFER_MS >= 120000)
    }
}
