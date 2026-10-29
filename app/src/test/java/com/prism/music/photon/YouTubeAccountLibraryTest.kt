package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class YouTubeAccountLibraryTest {
    private val video = """{"playlistVideoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Account video"},"shortBylineText":{"simpleText":"Creator"}}}"""
    private val playlist = """{"playlistRenderer":{"playlistId":"PL_account_list","title":{"simpleText":"My private list"}}}"""
    private val channel = """{"channelRenderer":{"channelId":"UCabcdefghijklmnopqrstuv","title":{"simpleText":"Subscribed creator"}}}"""
    private fun parse(source: String, kind: AccountEntryKind = AccountEntryKind.VIDEO) = YouTubeAccountLibraryParser.parse(Json.parseToJsonElement(source), kind)
    private val cfg = """ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"2.test"}}});"""
    private fun html(body: String, loggedOut: Boolean = false) = cfg + """var ytInitialData = {"responseContext":{"mainAppWebResponseContext":{"loggedOut":$loggedOut}},"contents":{"sectionListRenderer":{"contents":[$body]}}};"""
    private fun api(handler: (Request) -> String) = YouTubeAccountLibraryApi(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(handler(chain.request()).toResponseBody("text/html".toMediaType())).build()
    }.build())
    private val session get() = YouTubeWebSession(2, 8, "SAPISID=synthetic-root", "SAPISID=synthetic-stats")

    @Test fun previouslyWatchedAndLikedPlaylistRowsAreRead() {
        val page = parse("[$video]")
        assertEquals(listOf("abcdefghijk"), page.items.map { it.id })
        assertEquals("Creator", page.items.single().video!!.channel)
    }
    @Test fun accountPlaylistTitlesAndIdsAreNotLocalBookmarks() {
        val page = parse("[$playlist]", AccountEntryKind.PLAYLIST)
        assertEquals("PL_account_list", page.items.single().id)
        assertEquals("My private list", page.items.single().title)
        assertNull(page.items.single().video)
    }
    @Test fun subscribedChannelsHaveAnExplicitEntryKind() {
        assertEquals(AccountEntryKind.CHANNEL, parse(channel, AccountEntryKind.CHANNEL).items.single().kind)
    }
    @Test fun invalidChannelIdDoesNotProduceArbitraryNavigation() {
        assertTrue(parse(channel.replace("UCabcdefghijklmnopqrstuv", "https://evil.test/"), AccountEntryKind.CHANNEL).items.isEmpty())
    }
    @Test fun kindsDoNotCrossFillEachOther() {
        assertTrue(parse("[$video,$playlist]", AccountEntryKind.CHANNEL).items.isEmpty())
        assertEquals(1, parse("[$video,$playlist]", AccountEntryKind.PLAYLIST).items.size)
    }
    @Test fun rowsKeepOrderAndDeduplicate() {
        val other = video.replace("abcdefghijk", "dQw4w9WgXcQ")
        assertEquals(listOf("abcdefghijk", "dQw4w9WgXcQ"), parse("[$video,$other,$video]").items.map { it.id })
    }
    @Test fun sidebarAndUnselectedTabsCannotBecomeHistory() {
        assertTrue(parse("""{"secondaryContents":$video,"tabRenderer":{"selected":false,"content":$video}}""").items.isEmpty())
    }
    @Test fun serverHistoryPauseMessageIsRetained() {
        val page = parse("""{"messageRenderer":{"text":{"simpleText":"Your watch history is paused"}}}""")
        assertTrue(page.message!!.contains("paused")); assertTrue(page.items.isEmpty())
    }
    @Test fun emptyOrUnsupportedLayoutDoesNotInventAccountData() {
        assertTrue(parse("{}").items.isEmpty()); assertTrue(parse("{}").message!!.contains("unsupported"))
    }
    @Test fun modernPlaylistLockupIsRead() {
        val node = """{"lockupViewModel":{"contentId":"PL_modern_list","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Modern list"}}}}}"""
        assertEquals("Modern list", parse(node, AccountEntryKind.PLAYLIST).items.single().title)
    }
    @Test fun continuationTokenIsKeptForAnotherAccountPage() {
        val node = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"synthetic-next"}}}}"""
        assertEquals("synthetic-next", parse("[$video,$node]").continuation)
    }
    @Test fun historyUsesSelectedAccountAndOnlyYoutubeHost() = runBlocking {
        val api = api { request ->
            assertEquals("www.youtube.com", request.url.host)
            assertEquals("/feed/history", request.url.encodedPath)
            assertEquals("2", request.url.queryParameter("authuser"))
            assertEquals("SAPISID=synthetic-root", request.header("Cookie"))
            html(video)
        }
        assertEquals(1, api.load(session, AccountShelf.HISTORY).items.size)
    }
    @Test fun likedVideosUseRealLLListNotLocalSavedItems() = runBlocking {
        val api = api { request -> assertEquals("LL", request.url.queryParameter("list")); html(video) }
        assertEquals(1, api.load(session, AccountShelf.LIKED).items.size)
    }
    @Test fun privatePlaylistReadsUseSameAuthenticatedSession() = runBlocking {
        val api = api { request -> assertEquals("PL_account_list", request.url.queryParameter("list")); html(video) }
        assertEquals(1, api.load(session, AccountShelf.PLAYLISTS, "PL_account_list").items.size)
    }
    @Test fun signedOutLibraryIsRejectedRatherThanShownAsAccountData() = runBlocking {
        try { api { html(video, loggedOut = true) }.load(session, AccountShelf.HISTORY); fail("Expected signed-out rejection") }
        catch (_: java.io.IOException) { }
    }
    @Test fun playlistIdCannotChangeTheEndpointOrQuery() = runBlocking {
        var requests = 0
        try { api { requests++; html(video) }.load(session, AccountShelf.PLAYLISTS, "LL&authuser=0"); fail("Expected invalid ID rejection") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, requests)
    }
}
