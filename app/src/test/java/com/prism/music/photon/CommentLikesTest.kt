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
    @Test fun modernToolbarMustMatchTheCommentsOwnEntityKeys() {
        fun modern(surface: String) = Json.parseToJsonElement("""{"commentViewModel":{"commentId":"modern-1","toolbarSurfaceKey":"surface-1","toolbarStateKey":"state-1"},"mutations":[{"entityKey":"$surface","payload":{"engagementToolbarSurfaceEntityPayload":{"likeCommand":{"innertubeCommand":{"performCommentActionEndpoint":{"action":"modern-token"}}}}}},{"payload":{"engagementToolbarStateEntityPayload":{"key":"state-1","likeState":"TOOLBAR_LIKE_STATE_INDIFFERENT"}}}]}""").jsonObject
        assertEquals("modern-token", YouTubeCommentLikes.control(modern("surface-1"), "modern-1")?.action)
        assertNull(YouTubeCommentLikes.control(modern("unrelated-surface"), "modern-1"))
        assertNull(YouTubeCommentLikes.control(modern("surface-1"), "different-comment"))
    }
    @Test fun steadyPlaybackHasRecoveryHeadroomWithoutSlowingFirstFrame() {
        assertEquals(750, StartupPolicy.START_BUFFER_MS)
        assertTrue(StartupPolicy.REBUFFER_MS >= 5000)
        assertTrue(StartupPolicy.MIN_BUFFER_MS >= 45000)
        assertTrue(StartupPolicy.MAX_BUFFER_MS >= 120000)
    }
}
