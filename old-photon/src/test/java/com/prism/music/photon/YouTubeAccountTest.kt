package com.prism.music.photon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class YouTubeAccountTest {
    private val channel = """{"items":[{"id":"UC_verified","snippet":{"title":"My Channel"}}]}"""
    private val posted = """{"snippet":{"topLevelComment":{"id":"real-server-id","snippet":{"authorDisplayName":"My Channel","textOriginal":"Hello <world>","likeCount":0}}}}"""
    private fun api(videoBody: String = """{"items":[{"id":"dQw4w9WgXcQ","snippet":{"channelId":"UC_target"}}]}""", answer: (Request) -> Pair<Int, String>) = YouTubeCommentApi(OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (code, body) = if (request.url.encodedPath == "/youtube/v3/videos")
            200 to videoBody
        else answer(request)
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun bodyContainsTargetChannelVideoAndPlainText() {
        val root = YouTubeCommentApi.commentBody("dQw4w9WgXcQ", "  Hello <world>\n\"quoted\"  ", "UC_target")
        val snippet = root.getValue("snippet").jsonObject
        assertEquals("dQw4w9WgXcQ", snippet.getValue("videoId").jsonPrimitive.content)
        assertEquals("Hello <world>\n\"quoted\"", snippet.getValue("topLevelComment").jsonObject.getValue("snippet").jsonObject.getValue("textOriginal").jsonPrimitive.content)
        assertEquals("UC_target", snippet.getValue("channelId").jsonPrimitive.content)
    }
    @Test fun invalidInputRejectedBeforeNetwork() = runBlocking {
        var calls = 0
        val api = api { calls++; 200 to posted }
        for ((id, text) in listOf("bad" to "hello", "dQw4w9WgXcQ" to "  ", "dQw4w9WgXcQ" to "x".repeat(10001))) {
            try { api.post("test-token", id, text); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls)
    }
    @Test fun realPostUsesOfficialEndpointAndRequiresServerId() = runBlocking {
        val api = api { request ->
            assertEquals("www.googleapis.com", request.url.host)
            assertEquals("/youtube/v3/commentThreads", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("Bearer test-token", request.header("Authorization"))
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val snippet = Json.parseToJsonElement(buffer.readUtf8()).jsonObject.getValue("snippet").jsonObject
            assertEquals("UC_target", snippet.getValue("channelId").jsonPrimitive.content)
            assertTrue(snippet.toString().contains("textOriginal"))
            200 to posted
        }
        val result = api.post("test-token", "dQw4w9WgXcQ", "Hello <world>")
        assertEquals("real-server-id", result.comment.id)
        assertEquals("Hello <world>", result.comment.text)
        assertFalse(result.comment.html)
    }
    @Test fun missingTargetVideoNeverSendsPost() = runBlocking {
        var posts = 0
        try { api("""{"items":[]}""") { posts++; 200 to posted }.post("test-token", "dQw4w9WgXcQ", "Hello"); fail("Expected missing video") }
        catch (error: YouTubeApiFailure) { assertEquals(404, error.status) }
        assertEquals(0, posts)
    }
    @Test fun missingServerIdNeverBecomesLocalSuccess() = runBlocking {
        try { api { 200 to "{}" }.post("test-token", "dQw4w9WgXcQ", "Hello"); fail("Expected unconfirmed post") }
        catch (error: IOException) { assertTrue(error.message!!.contains("confirm")) }
    }
    @Test fun errorsAreSanitizedAndPostsNotRetried() = runBlocking {
        var calls = 0
        val api = api { calls++; 403 to """{"error":{"message":"private-server-details","errors":[{"reason":"commentsDisabled"}]}}""" }
        try { api.post("test-token", "dQw4w9WgXcQ", "Hello"); fail("Expected API error") }
        catch (error: YouTubeApiFailure) { assertTrue(error.message!!.contains("disabled")); assertFalse(error.message!!.contains("private-server")) }
        assertEquals(1, calls)
    }
    @Test fun redirectDoesNotCountAsSuccess() = runBlocking {
        try { api { 302 to "{}" }.post("test-token", "dQw4w9WgXcQ", "Hello"); fail("Expected redirect rejection") }
        catch (error: YouTubeApiFailure) { assertEquals(302, error.status) }
    }
    @Test fun missingScopeAndStaleCallbacksCannotConnect() = runBlocking {
        var calls = 0
        val account = YouTubeAccount(api { calls++; 200 to channel }, { 0L })
        val first = account.begin()!!
        account.accept(first, "test-token", emptyList())
        assertNull(account.state.value.identity)
        val second = account.begin()!!
        account.disconnect()
        account.accept(second, "test-token", listOf(YOUTUBE_COMMENT_SCOPE))
        assertNull(account.state.value.identity)
        assertEquals(0, calls)
    }
    @Test fun verifiedChannelRequiredAndDisconnectBlocksPosting() = runBlocking {
        var posts = 0
        val account = YouTubeAccount(api { if (it.method == "POST") { posts++; 200 to posted } else 200 to channel }, { 0L })
        account.accept(account.begin()!!, "test-token", listOf(YOUTUBE_COMMENT_SCOPE))
        assertEquals("UC_verified", account.state.value.identity?.channelId)
        assertEquals("real-server-id", account.post("dQw4w9WgXcQ", "Hello").comment.id)
        account.disconnect()
        try { account.post("dQw4w9WgXcQ", "Hello"); fail("Expected disconnected session") } catch (_: IllegalStateException) { }
        assertEquals(1, posts)
    }
    @Test fun expiredSessionsRequireFreshUserAuthorization() = runBlocking {
        var now = 0L
        var posts = 0
        val account = YouTubeAccount(api { if (it.method == "POST") posts++; 200 to channel }, { now })
        account.accept(account.begin()!!, "test-token", listOf(YOUTUBE_COMMENT_SCOPE))
        now = 51 * 60_000L
        try { account.post("dQw4w9WgXcQ", "Hello"); fail("Expected expired session") } catch (_: IllegalStateException) { }
        assertEquals(0, posts)
        assertNull(account.state.value.identity)
    }
    @Test fun unauthorizedResponseClearsSession() = runBlocking {
        val account = YouTubeAccount(api { if (it.method == "POST") 401 to "{}" else 200 to channel }, { 0L })
        account.accept(account.begin()!!, "test-token", listOf(YOUTUBE_COMMENT_SCOPE))
        try { account.post("dQw4w9WgXcQ", "Hello"); fail("Expected authorization error") } catch (_: YouTubeApiFailure) { }
        assertNull(account.state.value.identity)
    }
}
