package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class YouTubeHomeTest {
    private val video = """{"videoRenderer":{"videoId":"dQw4w9WgXcQ","title":{"runs":[{"text":"Server {title} and \\\"quotes\\\""}]},"shortBylineText":{"runs":[{"text":"Creator","navigationEndpoint":{"browseEndpoint":{"browseId":"UC_target"}}}]},"lengthText":{"simpleText":"3:42"},"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"}]}}}"""
    private fun page(content: String, loggedOut: Boolean = false) = """<script>var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":$loggedOut}},"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[$content]}}}}]}}};</script>"""

    @Test fun realHomeCardsKeepServerOrderAndDeduplicate() {
        val second = video.replace("dQw4w9WgXcQ", "abcdefghijk")
        val result = YouTubeHomeParser.parse(page("$video,$second,$video"))
        assertEquals(listOf("dQw4w9WgXcQ", "abcdefghijk"), result.videos.map { it.id })
        assertEquals("Creator", result.videos.first().channel)
        assertEquals(222L, result.videos.first().duration)
        assertNull(result.message)
    }
    @Test fun serverPausedMessageIsPreservedWithoutInventingVideos() {
        val result = YouTubeHomeParser.parse(page("""{"feedNudgeRenderer":{"title":{"simpleText":"Your watch history is off"},"subtitle":{"simpleText":"Turn on history to get recommendations"}}}"""))
        assertTrue(result.videos.isEmpty())
        assertTrue(result.message!!.contains("Your watch history is off"))
    }
    @Test fun emptyFeedDoesNotPretendToKnowHistoryPauseSetting() {
        val result = YouTubeHomeParser.parse(page(""))
        assertTrue(result.videos.isEmpty())
        assertTrue(result.message!!.contains("no supported Home video cards"))
        assertFalse(result.message!!.contains("history is paused"))
    }
    @Test fun signedOutRecommendationsAreNotPassedOffAsPersonalized() {
        try { YouTubeHomeParser.parse(page(video, loggedOut = true)); fail("Expected signed-out rejection") }
        catch (error: IOException) { assertTrue(error.message!!.contains("signed-in")) }
    }
    @Test fun unknownLoginStateFailsClosed() {
        try { YouTubeHomeParser.parse("var ytInitialData = {\"contents\":{\"richGridRenderer\":{}}};"); fail("Expected unknown login rejection") }
        catch (_: IOException) { }
    }
    @Test fun braceScannerDoesNotTruncateQuotedBraces() {
        val value = """{"a":"} and { and \\\"quote\\\"","nested":{"b":1}}"""
        assertEquals(value, YouTubeHomeParser.extractInitialData("<script>window[\"ytInitialData\"] = $value;</script>"))
    }
    @Test fun unknownOrIncompleteHtmlDoesNotCreateFakeFeed() {
        for (html in listOf("<html>consent</html>", "var ytInitialData = {\"a\":1")) {
            try { YouTubeHomeParser.parse(html); fail("Expected unavailable page") } catch (_: IOException) { }
        }
    }
    @Test fun modernLockupCardsRemainNativeVideoLinks() {
        val result = YouTubeHomeParser.parse(page("""{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"abcdefghijk","metadata":{"lockupMetadataViewModel":{"title":{"content":"A real recommendation"}}}}}"""))
        assertEquals("abcdefghijk", result.videos.single().id)
        assertEquals("A real recommendation", result.videos.single().title)
    }
    @Test fun invalidVideoIdsAndUnsafeThumbnailUrlsAreNotUsed() {
        assertTrue(YouTubeHomeParser.parse(page(video.replace("dQw4w9WgXcQ", "invalid"))).videos.isEmpty())
        val result = YouTubeHomeParser.parse(page(video.replace("https://i.ytimg.com", "https://attacker.invalid")))
        assertEquals("", result.videos.single().thumbnail)
    }
    @Test fun browserNavigationDoesNotTrustSuffixesOrNonHttps() {
        assertTrue(YouTubeSessionNavigation.allowed("https://accounts.google.com/ServiceLogin"))
        assertTrue(YouTubeSessionNavigation.allowed("https://www.youtube.com/"))
        for (url in listOf("https://accounts.google.com.evil.test/", "http://www.youtube.com/", "file:///etc/passwd", "https://evil.test@www.youtube.com/", "https://www.youtube.com:444/"))
            assertFalse(url, YouTubeSessionNavigation.allowed(url))
    }
    @Test fun cookieIsSentOnlyToFixedHomeEndpointAndSelectedAccount() = runBlocking {
        var calls = 0
        val cookie = "SAPISID=unit-test-session"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            assertEquals("https", request.url.scheme)
            assertEquals("www.youtube.com", request.url.host)
            assertEquals("2", request.header("X-Goog-AuthUser"))
            assertEquals(cookie, request.header("Cookie"))
            val response = if (request.method == "GET") {
                assertEquals("2", request.url.queryParameter("authuser"))
                """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.20261102.00.00"}}});</script>""" + page(video)
            } else {
                assertEquals("/youtubei/v1/browse", request.url.encodedPath)
                assertTrue(request.header("Authorization")!!.startsWith("SAPISIDHASH "))
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                assertEquals("FEwhat_to_watch", body["browseId"]!!.jsonPrimitive.content)
                YouTubeWebBootstrap.assigned(page(video.replace("dQw4w9WgXcQ", "abcdefghijk")), "ytInitialData").toString()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Test")
                .body(response.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        // Only the signed browse response, never the bootstrap recommendation, becomes Home.
        assertEquals(listOf("abcdefghijk"), YouTubeHomeApi(http).home(cookie, 2).videos.map { it.id })
        assertEquals(2, calls)
    }

    @Test fun redirectIsNotFollowedWithSessionCookies() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
                .header("Location", "https://attacker.invalid/").body("".toResponseBody()).build()
        }.build()
        try { YouTubeHomeApi(http).home("unit-test-session"); fail("Expected blocked redirect") } catch (_: IOException) { }
        assertEquals(1, calls)
    }
}
