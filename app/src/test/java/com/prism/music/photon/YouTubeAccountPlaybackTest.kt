package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class YouTubeAccountPlaybackTest {
    private val context = """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.test"}}});</script>"""
    private fun control(command: String, token: String = "test-action") = """{"buttonRenderer":{"serviceEndpoint":{"feedbackEndpoint":{"feedbackToken":"$token","actions":[{"localWatchHistoryCommand":{"localWatchHistoryCommandType":"LOCAL_WATCH_HISTORY_COMMAND_TYPE_$command"}}]}}}}"""
    private fun history(controls: String, signedOut: Boolean = false) = context + """<script>var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":$signedOut}},"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"browseFeedActionsRenderer":{"contents":[$controls]}}}}};</script>"""
    private fun player(url: String, id: String = "dQw4w9WgXcQ") = context + """var ytInitialPlayerResponse = {"videoDetails":{"videoId":"$id"},"playabilityStatus":{"status":"OK"},"playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"$url"}}};"""

    @Test fun pauseActionMeansHistoryCurrentlyEnabled() {
        val result = YouTubeHistoryParser.parse(history(control("PAUSE")))
        assertEquals(GoogleHistoryStatus.ENABLED, result.status)
        assertEquals("test-action", result.actionToken)
        assertNotNull(result.context)
    }
    @Test fun resumeActionMeansHistoryCurrentlyPaused() {
        assertEquals(GoogleHistoryStatus.PAUSED, YouTubeHistoryParser.parse(history(control("RESUME"))).status)
    }
    @Test fun plainEnglishTextCannotInventAnEnabledState() {
        val result = YouTubeHistoryParser.parse(history("""{"buttonRenderer":{"text":{"simpleText":"Pause watch history"}}}"""))
        assertEquals(GoogleHistoryStatus.UNKNOWN, result.status)
        assertNull(result.actionToken)
    }
    @Test fun conflictingCommandsFailClosed() {
        assertEquals(GoogleHistoryStatus.UNKNOWN, YouTubeHistoryParser.parse(history(control("PAUSE") + "," + control("RESUME"))).status)
    }
    @Test fun signedOutHistoryCannotAuthorizeTracking() {
        try { YouTubeHistoryParser.parse(history(control("PAUSE"), true)); fail("Expected signed-out rejection") } catch (_: IOException) { }
    }
    @Test fun unrelatedVideoTextOrCommandsAreNotHistoryControls() {
        val html = context + """var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":false}},"contents":{"richGridRenderer":{"contents":[${control("PAUSE")}]}}};"""
        assertEquals(GoogleHistoryStatus.UNKNOWN, YouTubeHistoryParser.parse(html).status)
    }
    @Test fun unknownPausedStaleAndWrongAccountAllBlockLocalTracking() {
        val library = PhotonLibrary(youtubeHome = true, youtubeSessionRevision = 7)
        for (status in listOf(GoogleHistoryStatus.UNKNOWN, GoogleHistoryStatus.PAUSED))
            assertTrue(AccountHistoryPolicy.localPaused(library, GoogleHistoryState(status, checkedAtMs = 10, revision = 7), 20))
        val active = GoogleHistoryState(GoogleHistoryStatus.ENABLED, checkedAtMs = 10, revision = 7)
        assertFalse(AccountHistoryPolicy.localPaused(library, active, 20))
        assertTrue(AccountHistoryPolicy.localPaused(library, active, 30_010))
        assertTrue(AccountHistoryPolicy.localPaused(library.copy(youtubeSessionRevision = 8), active, 20))
        assertTrue(AccountHistoryPolicy.localPaused(library.copy(youtubeSessionReady = false), active, 20))
        assertTrue(AccountHistoryPolicy.localPaused(library.copy(historyPaused = true), active, 20))
    }
    @Test fun disconnectedLocalTrackingRemainsIndependent() {
        assertFalse(AccountHistoryPolicy.localPaused(PhotonLibrary(), GoogleHistoryState(), 500))
        assertTrue(AccountHistoryPolicy.localPaused(PhotonLibrary(historyPaused = true), GoogleHistoryState(), 500))
    }
    @Test fun reportsRequireFifteenSecondsOfRealPlaybackAndAreAttemptedOnce() {
        val gate = PlaybackHistoryGate()
        assertNull(gate.tick("view", 0, true, true))
        assertNull(gate.tick("view", 5_000, true, true))
        assertNull(gate.tick("view", 10_000, true, true))
        assertEquals(15_000L, gate.tick("view", 15_000, true, true))
        assertNull(gate.tick("view", 30_000, true, true))
        assertNull(gate.tick("view", 35_000, false, false))
        assertNull(gate.tick("view", 50_000, true, true))
    }
    @Test fun pausedTimeAndSkippedEligibilityAreNotRetroactivelyUploaded() {
        val gate = PlaybackHistoryGate()
        gate.tick("view", 0, true, false)
        assertNull(gate.tick("view", 100_000, true, true))
        assertNull(gate.tick("view", 105_000, false, true))
        assertNull(gate.tick("view", 200_000, true, true))
        assertNull(gate.tick("view", 205_000, true, true))
        assertEquals(15_000L, gate.tick("view", 210_000, true, true))
    }
    @Test fun newViewOrAccountStartsANewCounterButQualityKeyDoesNot() {
        val gate = PlaybackHistoryGate()
        gate.tick("id:open:7", 0, true, true)
        gate.tick("id:open:7", 10_000, true, true)
        assertNull(gate.tick("id:newopen:8", 15_000, true, true))
        assertNull(gate.tick("id:newopen:8", 25_000, true, true))
        assertEquals(15_000L, gate.tick("id:newopen:8", 30_000, true, true))
    }
    @Test fun anOfflineOrMissingViewNeverReports() {
        val gate = PlaybackHistoryGate()
        gate.tick(null, 0, true, true)
        assertNull(gate.tick(null, 60_000, true, true))
    }
    @Test fun elapsedTimeGapsAreBoundedInsteadOfInventingWatchtime() {
        val gate = PlaybackHistoryGate()
        gate.tick("view", 0, true, true)
        assertNull(gate.tick("view", 90_000, true, true))
        assertEquals(15_000L, gate.tick("view", 95_000, true, true))
    }
    @Test fun trackingMustMatchActualPlayingVideoAndAllowedHttpsEndpoint() {
        val (url, _) = YouTubePlaybackUrls.forVideo(player("https://s.youtube.com/api/stats/playback?docid=dQw4w9WgXcQ"), "dQw4w9WgXcQ", 15_000)
        assertEquals("dQw4w9WgXcQ", url.queryParameter("docid"))
        assertEquals("15.0", url.queryParameter("rt"))
        assertEquals(16, url.queryParameter("cpn")!!.length)
        for (bad in listOf("https://s.youtube.com.attacker.invalid/api/stats/playback?docid=dQw4w9WgXcQ", "http://s.youtube.com/api/stats/playback?docid=dQw4w9WgXcQ", "https://s.youtube.com/api/stats/watchtime?docid=dQw4w9WgXcQ", "https://s.youtube.com/api/stats/playback?docid=abcdefghijk")) {
            try { YouTubePlaybackUrls.forVideo(player(bad), "dQw4w9WgXcQ", 15_000); fail("Expected rejected endpoint") } catch (_: IOException) { }
        }
    }
    @Test fun mismatchedPlayerIdentityCannotCreateHistoryReport() {
        try { YouTubePlaybackUrls.forVideo(player("https://s.youtube.com/api/stats/playback?docid=dQw4w9WgXcQ", "abcdefghijk"), "dQw4w9WgXcQ", 15_000); fail("Expected mismatched video") }
        catch (_: IOException) { }
    }
    @Test fun continuationParserUsesOnlyReturnedBatchesAndTokens() {
        val root = Json.parseToJsonElement("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Next video"}}},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-token"}}}}]}}]}""").jsonObject
        val page = YouTubeHomeParser.continuation(root)
        assertEquals("abcdefghijk", page.videos.single().id)
        assertEquals("next-token", page.continuation)
    }
    @Test fun continuationDoesNotConvertSignedOutResponsesIntoPersonalFeed() {
        try { YouTubeHomeParser.continuation(Json.parseToJsonElement("""{"responseContext":{"mainAppWebResponseContext":{"loggedOut":true}},"onResponseReceivedActions":[]}""").jsonObject); fail("Expected expired session") }
        catch (_: IOException) { }
    }
    @Test fun authenticatedContinuationUsesFixedEndpointAndServerContext() = runBlocking {
        var count = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            val request = chain.request()
            assertEquals("https://www.youtube.com/youtubei/v1/browse", request.url.toString())
            assertEquals("2", request.header("X-Goog-AuthUser"))
            assertTrue(request.header("Authorization")!!.startsWith("SAPISIDHASH "))
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("cursor", body["continuation"]!!.jsonPrimitive.content)
            assertEquals("WEB", body["context"]!!.jsonObject["client"]!!.jsonObject["clientName"]!!.jsonPrimitive.content)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Test")
                .body("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[]}}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val ctx = YouTubeWebBootstrap.context(context)!!
        val result = YouTubeHomeApi(client).more(YouTubeWebSession(2, 7, "SAPISID=test-only-not-a-real-session", ""), YouTubeHomePage(emptyList(), continuation = "cursor", context = ctx))
        assertTrue(result.videos.isEmpty())
        assertEquals(1, count)
    }
    @Test fun sessionToStringCannotExposeCredentials() {
        assertFalse(YouTubeWebSession(0, 1, "test-private-value", "test-stats-value").toString().contains("test-private"))
    }
}
