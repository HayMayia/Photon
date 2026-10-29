package com.prism.music.photon

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

const val YOUTUBE_COMMENT_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"

data class YouTubeIdentity(val channelId: String, val name: String)
data class AccountState(val busy: Boolean = false, val identity: YouTubeIdentity? = null, val error: String? = null)
data class PublishedComment(val videoId: String, val comment: VideoComment)
class YouTubeApiFailure(val status: Int, val reason: String) : IOException(YouTubeErrors.message(status, reason))

object YouTubeErrors {
    fun message(status: Int, reason: String): String = when {
        status == 401 -> "Your Google authorization expired. Reconnect your account; your draft has been kept."
        reason == "commentsDisabled" -> "The creator has disabled comments on this video."
        reason == "youtubeSignupRequired" || reason == "channelNotFound" -> "Create a YouTube channel for this Google account on YouTube, then reconnect."
        reason == "accessNotConfigured" || reason == "SERVICE_DISABLED" -> "Enable YouTube Data API v3 in the Google Cloud project used for this app. See Google sign-in setup."
        reason == "quotaExceeded" || reason == "dailyLimitExceeded" -> "The Google Cloud project's YouTube API quota is exhausted. Your draft has been kept."
        reason == "insufficientPermissions" -> "YouTube commenting permission was not granted. Reconnect and review Google's consent screen."
        reason == "forbidden" -> "YouTube refused this action for the selected account or video. Your draft has been kept."
        reason == "commentTextRequired" || reason == "invalidCommentText" -> "YouTube rejected the comment text. Edit your draft and try again."
        status == 404 -> "YouTube could not find this video or resource."
        else -> "YouTube API refused the request (HTTP $status). Your draft has been kept."
    }
}

/** Fixed official endpoints; credentials are attached only to googleapis.com, with redirects/retries disabled. */
class YouTubeCommentApi(client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun identity(token: String): YouTubeIdentity = withContext(Dispatchers.IO) {
        val root = execute(Request.Builder().url("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true")
            .header("Authorization", "Bearer $token").get().build())
        val channel = root["items"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw YouTubeApiFailure(403, "youtubeSignupRequired")
        YouTubeIdentity(channel.getValue("id").jsonPrimitive.content,
            channel["snippet"]?.jsonObject?.get("title")?.jsonPrimitive?.content ?: "YouTube channel")
    }

    suspend fun post(token: String, videoId: String, text: String): PublishedComment = withContext(Dispatchers.IO) {
        // Validate before any I/O, then obtain the target video's channel (not the posting user's channel).
        commentBody(videoId, text, "validation")
        val video = execute(Request.Builder().url("https://www.googleapis.com/youtube/v3/videos?part=snippet&id=$videoId")
            .header("Authorization", "Bearer $token").get().build())["items"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: throw YouTubeApiFailure(404, "videoNotFound")
        check(video["id"]?.jsonPrimitive?.content == videoId) { "YouTube did not confirm the target video" }
        val targetChannel = video["snippet"]?.jsonObject?.get("channelId")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() } ?: throw IOException("Could not verify the video's channel. No comment was submitted.")
        val body = commentBody(videoId, text, targetChannel)
        val root = execute(Request.Builder().url("https://www.googleapis.com/youtube/v3/commentThreads?part=snippet")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        val top = root["snippet"]?.jsonObject?.get("topLevelComment")?.jsonObject
            ?: throw IOException("YouTube's response did not confirm the comment. Check YouTube before trying again to avoid a duplicate.")
        val id = top["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw IOException("YouTube's response did not confirm a comment ID. Check YouTube before trying again.")
        val snippet = top.getValue("snippet").jsonObject
        PublishedComment(videoId, VideoComment(id, snippet["authorDisplayName"]?.jsonPrimitive?.content ?: "Your YouTube channel",
            snippet["textOriginal"]?.jsonPrimitive?.content ?: snippet["textDisplay"]?.jsonPrimitive?.content ?: text.trim(),
            snippet["likeCount"]?.jsonPrimitive?.intOrNull ?: 0, 0, null, html = snippet["textOriginal"] == null && snippet["textDisplay"] != null))
    }

    private fun execute(request: Request): JsonObject = http.newCall(request).execute().use { response ->
        val body = response.body.string()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (!response.isSuccessful) {
            val error = root?.get("error") as? JsonObject
            val reason = runCatching { error?.get("errors")?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.content }.getOrNull().orEmpty()
            throw YouTubeApiFailure(response.code, reason)
        }
        root ?: throw IOException("YouTube returned an unreadable response. Check YouTube before retrying a post.")
    }

    companion object {
        fun commentBody(videoId: String, text: String, targetChannelId: String): JsonObject {
            require(Regex("[A-Za-z0-9_-]{11}").matches(videoId)) { "Invalid YouTube video" }
            require(targetChannelId.isNotBlank()) { "Missing target video channel" }
            val clean = text.trim()
            require(clean.isNotEmpty()) { "Write a comment first" }
            require(clean.length <= 10_000) { "Keep the comment under 10,000 characters" }
            return buildJsonObject { putJsonObject("snippet") {
                put("videoId", videoId)
                put("channelId", targetChannelId)
                putJsonObject("topLevelComment") { putJsonObject("snippet") { put("textOriginal", clean) } }
            } }
        }
    }
}

/** OAuth tokens remain in memory only: never in preferences, logs, backups, bundles or test reports. */
class YouTubeAccount(private val api: YouTubeCommentApi = YouTubeCommentApi(), private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val mutable = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = mutable
    private var token: String? = null
    private var authorizedAt = 0L
    private var generation = 0L
    private var posting = false

    @Synchronized fun begin(): Long? {
        if (mutable.value.busy || posting) return null
        generation++
        token = null
        mutable.value = AccountState(busy = true)
        return generation
    }
    @Synchronized fun current(id: Long): Boolean = id == generation && mutable.value.busy
    @Synchronized fun fail(id: Long, message: String) {
        if (id != generation) return
        token = null
        mutable.value = AccountState(error = message)
    }
    suspend fun accept(id: Long, accessToken: String, scopes: List<String>) {
        if (!current(id)) return
        if (accessToken.isBlank() || YOUTUBE_COMMENT_SCOPE !in scopes) {
            fail(id, "Google did not grant YouTube commenting permission. No comment was submitted."); return
        }
        try {
            val identity = api.identity(accessToken)
            synchronized(this) {
                if (!current(id)) return
                token = accessToken; authorizedAt = clock()
                mutable.value = AccountState(identity = identity)
            }
        } catch (cancelled: CancellationException) { fail(id, "Sign-in was interrupted. Please reconnect."); throw cancelled }
        catch (error: YouTubeApiFailure) { fail(id, error.message ?: "YouTube authorization failed") }
        catch (_: Exception) { fail(id, "Google authorized the request, but the app could not verify your YouTube channel. Check your connection/API setup and reconnect.") }
    }
    @Synchronized fun disconnect() {
        generation++; token = null; authorizedAt = 0
        mutable.value = AccountState()
    }
    suspend fun post(videoId: String, text: String): PublishedComment {
        val session: Pair<Long, String> = synchronized(this) {
            check(!posting) { "A comment is already being submitted" }
            val value = token
            if (value == null || mutable.value.identity == null || clock() - authorizedAt > 50 * 60_000) {
                disconnect()
                mutable.value = AccountState(error = "Reconnect Google to comment. Your draft has been kept.")
                throw IllegalStateException("Reconnect Google to comment. Your draft has been kept.")
            }
            posting = true
            generation to value
        }
        try { return api.post(session.second, videoId, text) }
        catch (error: YouTubeApiFailure) {
            if (error.status == 401) synchronized(this) { if (generation == session.first) disconnect() }
            throw error
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) {
            throw IOException("Posting could not be confirmed. Your draft has been kept. Check YouTube before retrying, since a comment may have reached the server.")
        } finally { synchronized(this) { posting = false } }
    }
}
