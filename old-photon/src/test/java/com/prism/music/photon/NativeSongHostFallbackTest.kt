package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NativeSongHostFallbackTest {
    private val cfg = """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.test"}}});"""
    private fun html(id: String) = cfg + """var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":false}},"contents":{"playlistVideoListRenderer":{"playlistId":"$id","contents":[{"playlistVideoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Native song"}}},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"songs-next"}}}}]}}};"""
    @Test fun exactNativeListCanUseExistingYouTubeOriginAndContinueThere() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request(); requests += req
            val music = req.url.host == "music.youtube.com"
            val body = if (music) "Unavailable" else if (req.method == "GET") html("LM")
                else """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[{"playlistVideoRenderer":{"videoId":"otherVideo1","title":{"simpleText":"Second song"}}}]}}]}"""
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(if (music) 403 else 200).message("Response")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        val api = YouTubeAccountLibraryApi(client)
        val session = YouTubeWebSession(2, 7, "SAPISID=main-only", "", "SAPISID=music-only")
        val first = api.likedSongs(session)
        assertTrue(first.nativeSongsOnMain)
        assertTrue(first.items.single().video!!.music)
        assertEquals("songs-next", first.continuation)
        val next = api.likedSongs(session, first)
        assertTrue(next.nativeSongsOnMain)
        assertEquals("otherVideo1", next.items.single().id)
        assertTrue(requests.filter { it.url.host == "www.youtube.com" }.all { it.header("Cookie") == "SAPISID=main-only" })
        assertEquals("LM", requests[1].url.queryParameter("list"))
        assertTrue(requests.none { it.url.queryParameter("list") == "LL" || it.url.encodedPath.startsWith("/watch") })
    }
    @Test fun unrelatedPlaylistCannotSubstituteForNativeSongs() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html("PL_wrong").toResponseBody("text/html".toMediaType())).build()
        }.build()
        try {
            YouTubeAccountLibraryApi(client).likedSongs(YouTubeWebSession(0, 1, "SAPISID=main", "", "SAPISID=music"))
            fail("Must reject a different playlist on both origins")
        } catch (_: IOException) { }
    }
}
