package com.prism.music.photon

import kotlinx.coroutines.runBlocking
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
        val result = YouTubeHomeParser.parse(page("""{"lockupViewModel":{"contentId":"abcdefghijk","metadata":{"lockupMetadataViewModel":{"title":{"content":"A real recommendation"}}}}}"""))
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
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("https", chain.request().url.scheme)
            assertEquals("www.youtube.com", chain.request().url.host)
            assertEquals("2", chain.request().url.queryParameter("authuser"))
            assertEquals("2", chain.request().header("X-Goog-AuthUser"))
            assertEquals("unit-test-session", chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Test")
                .body(page(video).toResponseBody("text/html".toMediaType())).build()
        }.build()
        assertEquals(1, YouTubeHomeApi(http).home("unit-test-session", 2).videos.size)
        assertEquals(1, calls)
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
