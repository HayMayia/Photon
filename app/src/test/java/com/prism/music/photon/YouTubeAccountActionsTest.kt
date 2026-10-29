package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class YouTubeAccountActionsTest {
    private val id = "abcdefghijk"
    private val channel = "UCabcdefghijklmnopqrstuv"
    private val session get() = YouTubeWebSession(2, 9, "SAPISID=synthetic-root", "SAPISID=synthetic-stats")
    private val cfg = """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.test"}}});"""
    private fun doc(body: String = "", out: Boolean = false) = cfg + """var ytInitialData={"responseContext":{"mainAppWebResponseContext":{"loggedOut":$out}},"contents":{},"control":[$body]};var ytInitialPlayerResponse={"videoDetails":{"videoId":"$id"}};"""
    private fun like(liked: Boolean) = doc("""{"likeButtonRenderer":{"target":{"videoId":"$id"},"likeStatus":"${if (liked) "LIKE" else "INDIFFERENT"}"}}""")
    private fun sub(on: Boolean) = doc("""{"subscribeButtonRenderer":{"channelId":"$channel","subscribed":$on}}""")
    private fun playlist(present: Boolean) = doc(if (present) """{"playlistVideoRenderer":{"videoId":"$id","setVideoId":"synthetic-entry-id","title":{"simpleText":"Video"}}}""" else "")
    private fun api(handler: (Request) -> String) = YouTubeAccountActionsApi(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(handler(chain.request()).toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun body(request: Request): JsonObject { val buffer = okio.Buffer(); request.body!!.writeTo(buffer); return Json.parseToJsonElement(buffer.readUtf8()).jsonObject }

    @Test fun modernEntityKeysAreBoundToPrimaryVideoNotRelatedButtons() {
        val root = Json.parseToJsonElement("""{"videoPrimaryInfoRenderer":{"likeButtonViewModel":{"likeStatusEntityKey":"current"}},"mutations":[{"likeStatusEntity":{"key":"other","likeStatus":"INDIFFERENT"}},{"likeStatusEntity":{"key":"current","likeStatus":"LIKE"}}]}""").jsonObject
        assertEquals(true, GoogleAccountControls.liked(root, id))
        val unrelated = Json.parseToJsonElement("""{"likeButtonViewModel":{"likeStatusEntityKey":"other"},"likeStatusEntity":{"key":"other","likeStatus":"LIKE"}}""").jsonObject
        assertNull(GoogleAccountControls.liked(unrelated, id))
    }
    @Test fun modernLikeMutationIsVerifiedThroughItsLinkedEntity() = runBlocking {
        var liked = false
        var writes = 0
        val service = api { request ->
            if (request.method == "POST") { writes++; liked = true; "{}" }
            else doc("""{"videoPrimaryInfoRenderer":{"likeButtonViewModel":{"likeStatusEntityKey":"current"}}},{"likeStatusEntity":{"key":"current","likeStatus":"${if (liked) "LIKE" else "INDIFFERENT"}"}}""")
        }
        assertEquals(true, service.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { true }.liked)
        assertEquals(1, writes)
    }
    @Test fun authenticatedShortsMetadataUsesOnlyThePinnedReadonlyRoute() = runBlocking {
        val service = api { request ->
            assertEquals("GET", request.method)
            assertEquals("www.youtube.com", request.url.host)
            assertEquals("/shorts/$id", request.url.encodedPath)
            assertEquals("2", request.url.queryParameter("authuser"))
            """<link rel="canonical" href="https://www.youtube.com/shorts/$id">"""
        }
        assertEquals(true, service.presentation(id, session)?.first)
    }
    @Test fun readingExistingLikeStateNeverSendsAMutation() = runBlocking {
        var reads = 0
        val service = api { request ->
            reads++
            assertEquals("GET", request.method)
            assertEquals("/watch", request.url.encodedPath)
            like(true)
        }
        assertEquals(true, service.likeState(id, session))
        assertEquals(1, reads)
    }
    @Test fun likeWritesGoogleAndVerifiesFreshState() = runBlocking {
        var liked = false; var writes = 0
        val api = api { req -> if (req.method == "POST") {
            assertEquals("/youtubei/v1/like/like", req.url.encodedPath)
            assertEquals(id, body(req)["target"]!!.jsonObject["videoId"]!!.jsonPrimitive.content)
            assertEquals("2", req.header("X-Goog-AuthUser")); assertTrue(req.header("Authorization")!!.startsWith("SAPISIDHASH "))
            writes++; liked = true; "{}"
        } else like(liked) }
        assertEquals(true, api.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { true }.liked)
        assertEquals(1, writes)
    }
    @Test fun explicitLikeDoesNotAccidentallyUnlikeAnAlreadyLikedVideo() = runBlocking {
        var writes = 0
        val api = api { req -> if (req.method == "POST") writes++; like(true) }
        assertEquals(true, api.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { true }.liked)
        assertEquals(0, writes)
    }
    @Test fun unlikeUsesRemoveEndpointAndReadback() = runBlocking {
        var liked = true
        val api = api { req -> if (req.method == "POST") { assertEquals("/youtubei/v1/like/removelike", req.url.encodedPath); liked = false; "{}" } else like(liked) }
        assertEquals(false, api.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = false), session) { true }.liked)
    }
    @Test fun unknownLikeStateSendsNothing() = runBlocking {
        var writes = 0
        try { api { req -> if (req.method == "POST") writes++; doc() }.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { true }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals(0, writes)
    }
    @Test fun modernLikeEntityIsReadStructurally() {
        assertNull(GoogleAccountControls.liked(Json.parseToJsonElement("""{"likeButtonViewModel":{"likeStatusEntity":{"likeStatus":"LIKE"}}}""").jsonObject, id))
        assertEquals(true, GoogleAccountControls.liked(Json.parseToJsonElement("""{"videoPrimaryInfoRenderer":{"likeButtonViewModel":{"likeStatusEntity":{"likeStatus":"LIKE"}}}}""").jsonObject, id))
    }
    @Test fun wrongVideoOrConflictingControlsFailClosed() {
        val root = YouTubeWebBootstrap.assigned(like(true), "ytInitialData")
        assertNull(GoogleAccountControls.liked(root, "dQw4w9WgXcQ"))
        val conflict = Json.parseToJsonElement("""{"a":${root},"b":{"likeButtonRenderer":{"target":{"videoId":"$id"},"likeStatus":"INDIFFERENT"}}}""").jsonObject
        assertNull(GoogleAccountControls.liked(conflict, id))
    }
    @Test fun subscriptionWritesGoogleAndReadsBack() = runBlocking {
        var subscribed = false; var writes = 0
        val api = api { req -> if (req.method == "POST") {
            assertEquals("/youtubei/v1/subscription/subscribe", req.url.encodedPath)
            assertEquals(channel, body(req)["channelIds"]!!.jsonArray.single().jsonPrimitive.content)
            writes++; subscribed = true; "{}"
        } else sub(subscribed) }
        assertEquals(true, api.execute(GoogleChange(GoogleChangeKind.SUBSCRIBE, channel, "Channel", desired = true), session) { true }.subscribed)
        assertEquals(1, writes)
    }
    @Test fun unsubscribeIsExplicitAndVerified() = runBlocking {
        var subscribed = true
        val api = api { req -> if (req.method == "POST") { assertEquals("/youtubei/v1/subscription/unsubscribe", req.url.encodedPath); subscribed = false; "{}" } else sub(subscribed) }
        assertEquals(false, api.execute(GoogleChange(GoogleChangeKind.SUBSCRIBE, channel, "Channel", desired = false), session) { true }.subscribed)
    }
    @Test fun newPlaylistIsPrivateAndCreatedOnGoogleWithInitialVideo() = runBlocking {
        val video = Video(id, "Video", "Creator")
        var writes = 0
        val api = api { req -> if (req.method == "POST") {
            assertEquals("/youtubei/v1/playlist/create", req.url.encodedPath)
            assertEquals("PRIVATE", body(req)["privacyStatus"]!!.jsonPrimitive.content)
            assertEquals(id, body(req)["videoIds"]!!.jsonArray.single().jsonPrimitive.content)
            writes++; """{"playlistId":"PL_created"}"""
        } else if (req.url.encodedPath == "/playlist") playlist(true) else doc() }
        assertTrue(api.execute(GoogleChange(GoogleChangeKind.CREATE_PLAYLIST, "", "My playlist", video), session) { true }.message.contains("new private playlist"))
        assertEquals(1, writes)
    }
    @Test fun addToPlaylistSendsRealEditAndVerifiesReturnedRows() = runBlocking {
        var present = false
        val api = api { req -> if (req.method == "POST") {
            assertEquals("/youtubei/v1/browse/edit_playlist", req.url.encodedPath)
            assertEquals("ACTION_ADD_VIDEO", body(req)["actions"]!!.jsonArray.single().jsonObject["action"]!!.jsonPrimitive.content)
            present = true; "{}"
        } else playlist(present) }
        assertTrue(api.execute(GoogleChange(GoogleChangeKind.ADD_VIDEO, "PL_existing", "Video", Video(id, "Video", "Creator")), session) { true }.message.contains("verified"))
    }
    @Test fun removeUsesServerEntryIdNotJustVideoId() = runBlocking {
        var present = true
        val api = api { req -> if (req.method == "POST") {
            assertEquals("synthetic-entry-id", body(req)["actions"]!!.jsonArray.single().jsonObject["setVideoId"]!!.jsonPrimitive.content)
            present = false; "{}"
        } else playlist(present) }
        assertTrue(api.execute(GoogleChange(GoogleChangeKind.REMOVE_VIDEO, "PL_existing", "Video", Video(id, "Video", "Creator")), session) { true }.message.contains("removal"))
    }
    @Test fun pausedOrSwitchedPermissionBlocksMutationBeforeSend() = runBlocking {
        var allowed = true; var writes = 0
        val api = api { req -> if (req.method == "POST") writes++; allowed = false; like(false) }
        try { api.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { allowed }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals(0, writes)
    }
    @Test fun ambiguousMutationIsNotRetriedOrReportedVerified() = runBlocking {
        var writes = 0
        val api = api { req -> if (req.method == "POST") { writes++; "{}" } else like(false) }
        try { api.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video", desired = true), session) { true }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals(1, writes)
    }
    @Test fun signedOutStateCannotMutate() = runBlocking {
        var writes = 0
        try { api { req -> if (req.method == "POST") writes++; doc(out = true) }.execute(GoogleChange(GoogleChangeKind.LIKE, id, "Video"), session) { true }; fail() }
        catch (_: java.io.IOException) { }
        assertEquals(0, writes)
    }
    @Test fun specialListsCannotBeDeleted() = runBlocking {
        var requests = 0
        try { api { requests++; doc() }.execute(GoogleChange(GoogleChangeKind.DELETE_PLAYLIST, "LL", "Liked videos"), session) { true }; fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, requests)
    }
}
