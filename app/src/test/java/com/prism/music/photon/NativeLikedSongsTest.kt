package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NativeLikedSongsTest {
    private val row = """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Liked song"}]}}}]}}"""
    private fun shelf(next: Boolean = false) = """{"contents":{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[$row]${if(next) ",\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"next-songs\"}}]" else ""}}}"""
    @Test fun readsNativeSongsAndContinuationWithoutAnyWatchMetadata() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = when (requests.size) {
                1 -> """ytcfg.set({"LOGGED_IN":true,"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.test"}}});"""
                2 -> shelf(true)
                else -> """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[$row]}}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = YouTubeAccountLibraryApi(client)
        val session = YouTubeWebSession(2, 7, "SAPISID=main-only", "", "SAPISID=music-only")
        val first = api.likedSongs(session)
        assertEquals("next-songs", first.continuation)
        assertTrue(first.items.single().video!!.music)
        assertNull(api.likedSongs(session, first).continuation)
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.url.host == "music.youtube.com" && it.header("Cookie") == "SAPISID=music-only" })
        assertEquals("2", requests[1].header("X-Goog-AuthUser"))
        assertEquals("67", requests[1].header("X-YouTube-Client-Name"))
        assertEquals("https://music.youtube.com", requests[1].header("Origin"))
        val payload = okio.Buffer().also { requests[1].body!!.writeTo(it) }.readUtf8()
        assertEquals("VLLM", Json.parseToJsonElement(payload).jsonObject["browseId"]!!.jsonPrimitive.content)
        assertTrue(requests.none { it.url.encodedPath.startsWith("/watch") })
    }
    @Test fun unrelatedRecommendationsCannotEnterLikedSongs() {
        val source = Json.parseToJsonElement(shelf()).jsonObject
        assertEquals(listOf("abcdefghijk"), YouTubeLikedSongs.parse(source).items.map { it.id })
        try { YouTubeLikedSongs.parse(Json.parseToJsonElement("""{"contents":{"musicShelfRenderer":{"contents":[$row]}}}""").jsonObject); fail("Must reject suggestions") }
        catch (_: IOException) { }
        try { YouTubeLikedSongs.parse(Json.parseToJsonElement(shelf().replace("\"LM\"", "\"PLother\"")).jsonObject); fail("Must reject another playlist") }
        catch (_: IOException) { }
    }
    @Test fun nativeSnapshotNeedsNoClassifierMapAndOldSnapshotsRequireMigration() {
        val snapshot = LikedMusicSnapshot(7, listOf(Video("abcdefghijk", "Song", "Creator", music = true)), schemaVersion = 3)
        assertEquals(snapshot.videos, MusicClassificationPolicy.verified(snapshot, 7))
        assertTrue(MusicClassificationPolicy.verified(snapshot, 8).isEmpty())
        assertTrue(LikedMusicFreshness.needsRefresh(snapshot.copy(schemaVersion = 2), 1))
    }
}
