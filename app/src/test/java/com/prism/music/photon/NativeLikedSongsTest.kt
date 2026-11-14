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
    private fun shelf(next: Boolean = false) = buildJsonObject {
        put("contents", buildJsonObject {
            put("musicPlaylistShelfRenderer", buildJsonObject {
                put("playlistId", "LM")
                put("contents", JsonArray(listOf(Json.parseToJsonElement(row))))
                if (next) put("continuations", JsonArray(listOf(buildJsonObject {
                    put("nextContinuationData", buildJsonObject { put("continuation", "next-songs") })
                })))
            })
        })
    }.toString()
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
    @Test fun musicBootstrapDoesNotNeedOptionalWebLoginFlag() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = if (requests.size == 1)
                """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.test"}}});"""
                else shelf()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val page = YouTubeAccountLibraryApi(client).likedSongs(YouTubeWebSession(0, 1, "SAPISID=main", "", "SAPISID=music"))
        assertEquals("abcdefghijk", page.items.single().id)
        assertEquals("/playlist", requests.first().url.encodedPath)
        assertEquals("LM", requests.first().url.queryParameter("list"))
        assertEquals(2, requests.size)
    }
    @Test fun unavailableRowsDoNotEraseReadableSongs() {
        val unavailable = """{"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Unavailable song"}]}}}]}}"""
        val root = Json.parseToJsonElement(shelf().replace(row, "$row,$unavailable")).jsonObject
        val page = YouTubeLikedSongs.parse(root)
        assertEquals(listOf("abcdefghijk"), page.items.map { it.id })
        assertEquals(1, page.unsupportedRows)
    }
    @Test fun endpointContinuationsAreSupportedAndKeepMusicFlag() {
        val root = buildJsonObject { put("onResponseReceivedEndpoints", buildJsonArray {
            add(buildJsonObject { put("appendContinuationItemsAction", buildJsonObject {
                put("continuationItems", JsonArray(listOf(Json.parseToJsonElement(row))))
            }) })
        }) }
        assertTrue(YouTubeLikedSongs.parse(root, continuation = true).items.single().video!!.music)
    }
    @Test fun inlineNativeListAvoidsAnotherBrowseRequest() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val html = """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.test"}}});var ytInitialData = ${shelf()};"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html.toResponseBody("text/html".toMediaType())).build()
        }.build()
        val page = YouTubeAccountLibraryApi(client).likedSongs(YouTubeWebSession(0, 1, "SAPISID=main", "", "SAPISID=music"))
        assertEquals("abcdefghijk", page.items.single().id)
        assertEquals(1, calls)
    }
    @Test fun sameHostRedirectKeepsSelectedAccountAndMusicCookies() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (requests.size == 1) 302 else 200).message("OK")
                .apply { if (requests.size == 1) header("Location", "/playlist?list=LM&authuser=0") }
                .body("page".toResponseBody("text/html".toMediaType())).build()
        }.build()
        YouTubeWebTransport(client, music = true).html("/playlist?list=LM", YouTubeWebSession(2, 1, "SAPISID=main", "", "SAPISID=music"))
        assertEquals(2, requests.size)
        assertEquals("2", requests.last().url.queryParameter("authuser"))
        assertTrue(requests.all { it.header("Cookie") == "SAPISID=music" })
    }
    @Test fun crossHostRedirectNeverReceivesCookies() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
                .header("Location", "https://accounts.google.com/")
                .body("".toResponseBody("text/html".toMediaType())).build()
        }.build()
        try {
            YouTubeWebTransport(client, music = true).html("/", YouTubeWebSession(0, 1, "", "", "SAPISID=music"))
            fail("Cross-host navigation must be rejected")
        } catch (_: IOException) { }
        assertEquals(1, calls)
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
