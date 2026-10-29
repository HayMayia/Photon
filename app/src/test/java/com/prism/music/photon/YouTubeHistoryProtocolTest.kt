package com.prism.music.photon

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class YouTubeHistoryProtocolTest {
    private lateinit var store: PhotonStore
    private val config = """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.test"}}});"""
    private fun history(command: String?) = config + """var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":false}},"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"browseFeedActionsRenderer":{"contents":[${if (command == null) "" else """{"buttonRenderer":{"serviceEndpoint":{"feedbackEndpoint":{"feedbackToken":"synthetic-action-token","actions":[{"localWatchHistoryCommand":{"localWatchHistoryCommandType":"LOCAL_WATCH_HISTORY_COMMAND_TYPE_$command"}}]}}}}"""}]}}}}};"""
    private val watch = config + """var ytInitialPlayerResponse = {"videoDetails":{"videoId":"dQw4w9WgXcQ"},"playabilityStatus":{"status":"OK"},"playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/api/stats/playback?docid=dQw4w9WgXcQ"}}};"""
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("photon_library", 0).edit().clear().commit()
        store = PhotonStore(context)
        store.update { it.copy(youtubeHome = true, youtubeSessionReady = true, youtubeSessionRevision = 7, accountHistorySync = true) }
    }
    private fun coordinator(handler: (Request) -> String): YouTubeHistoryCoordinator {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Test").body(body.toResponseBody()).build()
        }.build()
        return YouTubeHistoryCoordinator(store, client, { 10_000L }, { YouTubeWebSession(0, 7, "SAPISID=synthetic-www-cookie", "SAPISID=synthetic-stats-cookie") })
    }
    @Test fun pauseUsesFreshControlAndVerifiesResultWithoutDeletingHistory() = runBlocking {
        var paused = false; var writes = 0; var reads = 0
        val coordinator = coordinator { request ->
            if (request.method == "POST") {
                writes++
                assertEquals("/youtubei/v1/feedback", request.url.encodedPath)
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                val json = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                assertEquals(listOf("synthetic-action-token"), json["feedbackTokens"]!!.jsonArray.map { it.jsonPrimitive.content })
                assertEquals(setOf("context", "feedbackTokens"), json.keys)
                paused = true; "{}"
            } else { reads++; history(if (paused) "RESUME" else "PAUSE") }
        }
        coordinator.changePause(true)
        assertEquals(1, writes); assertEquals(2, reads)
        assertEquals(GoogleHistoryStatus.PAUSED, coordinator.state.value.status)
        assertTrue(coordinator.locallyPaused())
    }
    @Test fun unknownHistoryControlsNeverSendAPauseMutation() = runBlocking {
        var writes = 0
        val coordinator = coordinator { if (it.method == "POST") writes++; history(null) }
        coordinator.changePause(true)
        assertEquals(0, writes)
        assertEquals(GoogleHistoryStatus.UNKNOWN, coordinator.state.value.status)
    }
    @Test fun unconfirmedPauseDoesNotPretendSuccessOrAutomaticallyRetry() = runBlocking {
        var writes = 0
        val coordinator = coordinator { if (it.method == "POST") { writes++; "{}" } else history("PAUSE") }
        coordinator.changePause(true)
        assertEquals(1, writes)
        assertEquals(GoogleHistoryStatus.UNKNOWN, coordinator.state.value.status)
        assertTrue(coordinator.state.value.message!!.contains("not confirmed"))
    }
    @Test fun googlePausedHistoryBlocksPlaybackReportingBeforeWatchRequest() = runBlocking {
        var calls = 0
        val coordinator = coordinator { calls++; assertEquals("/feed/history", it.url.encodedPath); history("RESUME") }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        assertEquals(1, calls)
        assertEquals(GoogleHistoryStatus.PAUSED, coordinator.state.value.status)
    }
    @Test fun realReportPinsVideoAccountAndHostScopedCookie() = runBlocking {
        var reports = 0
        val coordinator = coordinator { request ->
            assertEquals("0", request.header("X-Goog-AuthUser"))
            when (request.url.encodedPath) {
                "/feed/history" -> history("PAUSE")
                "/watch" -> { assertEquals("dQw4w9WgXcQ", request.url.queryParameter("v")); watch }
                "/api/stats/playback" -> {
                    reports++
                    assertEquals("s.youtube.com", request.url.host)
                    assertEquals("SAPISID=synthetic-stats-cookie", request.header("Cookie"))
                    assertEquals("dQw4w9WgXcQ", request.url.queryParameter("docid")); ""
                }
                else -> throw AssertionError("Unexpected endpoint")
            }
        }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        assertEquals(1, reports)
        assertTrue(coordinator.state.value.message!!.contains("accepted"))
    }
    @Test fun accountSwitchDuringPlayerLookupCannotReportToNewAccount() = runBlocking {
        var reports = 0
        val coordinator = coordinator { request -> when (request.url.encodedPath) {
            "/feed/history" -> history("PAUSE")
            "/watch" -> { store.update { it.copy(youtubeSessionRevision = 8) }; watch }
            else -> { reports++; "" }
        } }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        assertEquals(0, reports)
    }
    @Test fun disabledConsentLocalPauseAndTooShortPlaybackSendNothing() = runBlocking {
        var requests = 0
        val coordinator = coordinator { requests++; throw AssertionError("Network not permitted") }
        coordinator.report("dQw4w9WgXcQ", 14_999, 7)
        store.update { it.copy(accountHistorySync = false) }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        store.update { it.copy(accountHistorySync = true, historyPaused = true) }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        assertEquals(0, requests)
    }
    @Test fun ambiguousReportFailureIsNotRetriedAndStateBecomesUnknown() = runBlocking {
        var attempts = 0
        val coordinator = coordinator { request -> when (request.url.encodedPath) {
            "/feed/history" -> history("PAUSE")
            "/watch" -> watch
            else -> { attempts++; throw IOException("synthetic network failure") }
        } }
        coordinator.report("dQw4w9WgXcQ", 15_000, 7)
        assertEquals(1, attempts)
        assertEquals(GoogleHistoryStatus.UNKNOWN, coordinator.state.value.status)
        assertTrue(coordinator.state.value.message!!.contains("No automatic retry"))
    }
}
