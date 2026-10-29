package com.prism.music.photon

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.SecureRandom

/** Unknown is a real state, not an alias for enabled. Never persist server state as a user preference. */
enum class GoogleHistoryStatus { ENABLED, PAUSED, UNKNOWN }
data class GoogleHistoryState(val status: GoogleHistoryStatus = GoogleHistoryStatus.UNKNOWN, val checking: Boolean = false,
    val checkedAtMs: Long = 0, val revision: Long = -1, val message: String? = null)
internal class HistoryPage(val status: GoogleHistoryStatus, val context: JsonObject?, val actionToken: String?) {
    override fun toString() = "HistoryPage(redacted)"
}
internal object YouTubeHistoryParser {
    fun parse(html: String): HistoryPage {
        val root = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(root, html)) throw IOException("YouTube history session is not verified")
        val contents = root["contents"] as? JsonObject
        val secondary = (contents?.get("twoColumnBrowseResultsRenderer") as? JsonObject)?.get("secondaryContents") as? JsonObject
        val controls = secondary?.get("browseFeedActionsRenderer") as? JsonObject
        val actions = mutableListOf<Pair<GoogleHistoryStatus, String?>>()
        fun visit(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    // Only inspect actual sidebar watch-history buttons, not video titles or arbitrary page text.
                    val button = node["buttonRenderer"] as? JsonObject
                    if (button != null) {
                        val commands = mutableSetOf<String>(); val tokens = mutableSetOf<String>()
                        fun inspect(value: JsonElement) {
                            when (value) {
                                is JsonObject -> {
                                    (value["localWatchHistoryCommand"] as? JsonObject)?.get("localWatchHistoryCommandType")?.jsonPrimitive?.contentOrNull?.let { commands += it }
                                    (value["feedbackEndpoint"] as? JsonObject)?.get("feedbackToken")?.jsonPrimitive?.contentOrNull?.takeIf { it.length in 1..32768 }?.let { tokens += it }
                                    value.values.forEach(::inspect)
                                }
                                is JsonArray -> value.forEach(::inspect)
                                else -> Unit
                            }
                        }
                        inspect(button)
                        val command = commands.singleOrNull()
                        val status = when (command) {
                            "LOCAL_WATCH_HISTORY_COMMAND_TYPE_PAUSE" -> GoogleHistoryStatus.ENABLED
                            "LOCAL_WATCH_HISTORY_COMMAND_TYPE_RESUME" -> GoogleHistoryStatus.PAUSED
                            else -> null
                        }
                        if (status != null) actions += status to tokens.singleOrNull()
                        return
                    }
                    node.values.forEach(::visit)
                }
                is JsonArray -> node.forEach(::visit)
                else -> Unit
            }
        }
        if (controls != null) visit(controls)
        val statuses = actions.map { it.first }.toSet()
        val status = statuses.singleOrNull() ?: GoogleHistoryStatus.UNKNOWN
        val token = actions.filter { it.first == status }.mapNotNull { it.second }.toSet().singleOrNull()
        return HistoryPage(status, YouTubeWebBootstrap.context(html), token)
    }
}

internal object YouTubePlaybackUrls {
    fun allowed(url: HttpUrl): Boolean = url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.host in setOf("www.youtube.com", "s.youtube.com") && url.encodedPath == "/api/stats/playback"
    fun forVideo(html: String, videoId: String, watchedMs: Long): Pair<HttpUrl, JsonObject> {
        require(watchedMs >= 15_000 && Regex("[A-Za-z0-9_-]{11}").matches(videoId))
        val player = YouTubeWebBootstrap.assigned(html, "ytInitialPlayerResponse")
        if ((player["videoDetails"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.content != videoId ||
            (player["playabilityStatus"] as? JsonObject)?.get("status")?.jsonPrimitive?.content != "OK") throw IOException("YouTube did not verify the playing video")
        val base = ((player["playbackTracking"] as? JsonObject)?.get("videostatsPlaybackUrl") as? JsonObject)?.get("baseUrl")?.jsonPrimitive?.content
            ?: throw IOException("YouTube supplied no playback-history endpoint")
        val url = base.toHttpUrl()
        if (!allowed(url) || url.queryParameter("docid") != videoId) throw IOException("Rejected untrusted or mismatched playback-history endpoint")
        val context = YouTubeWebBootstrap.context(html) ?: throw IOException("Missing authenticated player context")
        val version = (context["client"] as? JsonObject)?.get("clientVersion")?.jsonPrimitive?.content ?: throw IOException("Missing player version")
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val random = SecureRandom()
        val cpn = (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return url.newBuilder().setQueryParameter("ver", "2").setQueryParameter("c", "WEB")
            .setQueryParameter("cver", version).setQueryParameter("cpn", cpn)
            .setQueryParameter("rt", (watchedMs / 1000.0).toString()).build() to context
    }
}

/** Pure policy used by both tracking and tests. Fresh pause evidence is mandatory. */
internal object AccountHistoryPolicy {
    const val FRESH_MS = 30_000L
    fun active(state: GoogleHistoryState, revision: Long, now: Long): Boolean = state.revision == revision &&
        state.status == GoogleHistoryStatus.ENABLED && now - state.checkedAtMs in 0 until FRESH_MS
    fun localPaused(library: PhotonLibrary, state: GoogleHistoryState, now: Long): Boolean = library.historyPaused ||
        library.youtubeHome && (!library.youtubeSessionReady || !active(state, library.youtubeSessionRevision, now))
}

/** Actual wall-time in isPlaying only. Quality changes don't create extra views; seeks don't add distance. */
internal class PlaybackHistoryGate {
    private var key: String? = null
    private var previous = 0L
    private var playing = false
    private var accumulated = 0L
    private var attempted = false
    fun tick(viewKey: String?, now: Long, isPlaying: Boolean, eligible: Boolean): Long? {
        if (viewKey != key) { key = viewKey; previous = now; playing = false; accumulated = 0; attempted = false }
        if (eligible && playing && viewKey != null) accumulated += (now - previous).coerceIn(0, 10_000)
        previous = now
        playing = isPlaying && eligible
        if (!eligible) accumulated = 0
        if (eligible && accumulated >= 15_000 && !attempted) { attempted = true; return accumulated }
        return null
    }
}

class YouTubeHistoryCoordinator(private val store: PhotonStore, client: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val sessionProvider: (suspend () -> YouTubeWebSession)? = null) {
    private val transport = YouTubeWebTransport(client)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(GoogleHistoryState())
    val state: StateFlow<GoogleHistoryState> = mutable
    fun invalidate() { mutable.value = GoogleHistoryState() }
    private fun now() = clock()
    fun locallyPaused(): Boolean = AccountHistoryPolicy.localPaused(store.state.value, state.value, now())
    suspend fun session(): YouTubeWebSession {
        sessionProvider?.let { return it() }
        return withContext(Dispatchers.Main) {
        val library = store.state.value
        check(library.youtubeHome && library.youtubeSessionReady) { "Reconnect the YouTube website session" }
        val cookies = CookieManager.getInstance()
        YouTubeWebSession(library.youtubeWebUser, library.youtubeSessionRevision,
            cookies.getCookie("https://www.youtube.com").orEmpty(), cookies.getCookie("https://s.youtube.com").orEmpty())
    }
    }
    private fun same(session: YouTubeWebSession): Boolean = store.state.value.let {
        it.youtubeHome && it.youtubeSessionReady && it.youtubeSessionRevision == session.revision && it.youtubeWebUser == session.account
    }
    private suspend fun read(session: YouTubeWebSession): HistoryPage {
        val page = YouTubeHistoryParser.parse(transport.html("/feed/history", session))
        if (same(session)) mutable.value = GoogleHistoryState(page.status, checkedAtMs = now(), revision = session.revision,
            message = if (page.status == GoogleHistoryStatus.UNKNOWN) "History controls were not recognized. Local/account tracking is blocked." else null)
        return page
    }
    suspend fun refresh() = mutex.withLock {
        var captured: YouTubeWebSession? = null
        try {
            captured = session()
            mutable.value = state.value.copy(checking = true)
            read(captured)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (captured != null && same(captured)) mutable.value = GoogleHistoryState(revision = captured.revision, message = "History check interrupted; tracking is blocked.")
            throw cancelled
        }
        catch (_: Exception) { if (captured == null || same(captured)) mutable.value = GoogleHistoryState(message = "Could not verify Google's history setting. Tracking is blocked until refreshed.") }
    }
    suspend fun changePause(paused: Boolean) = mutex.withLock {
        val session = session()
        try {
            // Pause immediately stops local/reporting work while the change is in flight.
            mutable.value = GoogleHistoryState(checking = true, revision = session.revision)
            val before = read(session)
            val desired = if (paused) GoogleHistoryStatus.PAUSED else GoogleHistoryStatus.ENABLED
            if (before.status == desired) return@withLock
            if (before.status == GoogleHistoryStatus.UNKNOWN) throw IOException("History state unknown")
            val token = before.actionToken ?: throw IOException("No verified history control")
            val context = before.context ?: throw IOException("Missing history context")
            mutable.value = GoogleHistoryState(checking = true, revision = session.revision)
            if (!same(session)) return@withLock
            transport.post("feedback", context, buildJsonObject { putJsonArray("feedbackTokens") { add(token) } }, session)
            val after = read(session)
            if (after.status != desired) throw IOException("Google did not confirm the history-setting change")
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (same(session)) mutable.value = GoogleHistoryState(revision = session.revision,
            message = "History change was not confirmed. Refresh before retrying; no automatic change retry was sent.") }
    }
    suspend fun report(videoId: String, watchedMs: Long, revision: Long) = mutex.withLock {
        if (watchedMs < 15_000 || !Regex("[A-Za-z0-9_-]{11}").matches(videoId)) return@withLock
        val session = session()
        fun permitted() = same(session) && session.revision == revision && store.state.value.accountHistorySync && !store.state.value.historyPaused
        if (!permitted()) return@withLock
        try {
            val before = read(session) // No stale assumption before an account mutation.
            if (before.status != GoogleHistoryStatus.ENABLED || !permitted()) return@withLock
            val watch = transport.html("/watch?v=$videoId", session)
            val (url, context) = YouTubePlaybackUrls.forVideo(watch, videoId, watchedMs)
            if (!permitted() || !AccountHistoryPolicy.active(state.value, revision, now())) return@withLock
            transport.playback(url, session, context) // One real-play registration, never a watchtime/engagement fabrication.
            if (same(session)) mutable.value = state.value.copy(message = "Playback report accepted by YouTube. History/recommendation updates may take time; verify on YouTube.")
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (same(session)) mutable.value = GoogleHistoryState(revision = revision,
            message = "Playback report could not be confirmed. No automatic retry or offline replay will be sent.") }
    }
}
