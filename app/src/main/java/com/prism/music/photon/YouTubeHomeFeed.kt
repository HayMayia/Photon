package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Unofficial web-feed integration, separate from Google's supported comment OAuth API. */
data class YouTubeHomePage(val videos: List<Video>, val message: String? = null, val continuation: String? = null, val context: JsonObject? = null)
data class YouTubeHomeState(val busy: Boolean = false, val page: YouTubeHomePage? = null, val error: String? = null, val loadingMore: Boolean = false, val autoMore: Boolean = true)

class YouTubeHomeApi(client: OkHttpClient = OkHttpClient()) {
    private val transport = YouTubeWebTransport(client)
    suspend fun home(cookie: String, accountIndex: Int = 0): YouTubeHomePage = home(YouTubeWebSession(accountIndex, 0, cookie, ""))
    suspend fun home(session: YouTubeWebSession): YouTubeHomePage = withContext(Dispatchers.IO) {
        val html = transport.html("/", session)
        YouTubeHomeParser.parse(html).copy(context = YouTubeWebBootstrap.context(html))
    }
    suspend fun subscriptions(session: YouTubeWebSession): YouTubeHomePage = withContext(Dispatchers.IO) {
        val html = transport.html("/feed/subscriptions", session)
        YouTubeHomeParser.parse(html).copy(context = YouTubeWebBootstrap.context(html))
    }
    suspend fun more(session: YouTubeWebSession, page: YouTubeHomePage): YouTubeHomePage = withContext(Dispatchers.IO) {
        val token = page.continuation?.takeIf { it.length in 1..32768 } ?: throw IOException("No further Home page")
        val context = page.context ?: throw IOException("Refresh Home to obtain a web context")
        val response = transport.post("browse", context, buildJsonObject { put("continuation", token) }, session)
        YouTubeHomeParser.continuation(response).copy(context = context)
    }
}

/** Only server Home content is used. Never infers a Google history setting from an empty result. */
object YouTubeHomeParser {
    fun parse(html: String): YouTubeHomePage {
        val initial = extractInitialData(html)
        val root = runCatching { Json.parseToJsonElement(initial).jsonObject }.getOrNull()
            ?: throw IOException("YouTube Home's format changed. No generated feed was substituted.")
        val loggedOut = root["responseContext"]?.jsonObject?.get("mainAppWebResponseContext")?.jsonObject
            ?.get("loggedOut")?.jsonPrimitive?.booleanOrNull
        val loggedInConfig = Regex("\"LOGGED_IN\"\\s*:\\s*true").containsMatchIn(html.substringBefore("var ytInitialData"))
        if (loggedOut != false && !(loggedOut == null && loggedInConfig))
            throw IOException("YouTube did not confirm a signed-in web session. Connect YouTube Home separately from comment authorization.")
        val contents = root["contents"] ?: throw IOException("YouTube returned no recognizable Home content. No fallback feed was substituted.")
        val container = contents as? JsonObject ?: throw IOException("Unrecognized YouTube Home layout. No fallback feed was substituted.")
        val primary = (container["twoColumnBrowseResultsRenderer"] as? JsonObject)?.get("tabs")
            ?: (container["singleColumnBrowseResultsRenderer"] as? JsonObject)?.get("tabs")
            ?: container["richGridRenderer"] ?: container["sectionListRenderer"]
            ?: throw IOException("YouTube Home layout changed. No fallback feed was substituted.")
        return cards(primary)
    }

    fun continuation(root: JsonObject): YouTubeHomePage {
        val loggedOut = ((root["responseContext"] as? JsonObject)?.get("mainAppWebResponseContext") as? JsonObject)?.get("loggedOut")?.jsonPrimitive?.booleanOrNull
        if (loggedOut == true || root.containsKey("error")) throw IOException("YouTube session expired")
        val actions = JsonArray(listOfNotNull(root["onResponseReceivedActions"], root["onResponseReceivedEndpoints"]))
        if (actions.isEmpty()) throw IOException("Home continuation format changed")
        val batches = mutableListOf<JsonElement>()
        fun find(node: JsonElement) {
            when (node) {
                is JsonObject -> { (node["appendContinuationItemsAction"] as? JsonObject)?.get("continuationItems")?.let { batches += it }; node.values.forEach(::find) }
                is JsonArray -> node.forEach(::find)
                else -> Unit
            }
        }
        find(actions)
        if (batches.isEmpty()) throw IOException("No recognized Home continuation response")
        return cards(JsonArray(batches))
    }

    internal fun cards(primary: JsonElement): YouTubeHomePage {
        val videos = linkedMapOf<String, Video>()
        val messages = mutableListOf<String>()
        var next: String? = null
        fun text(value: JsonElement?): String = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonObject -> value["simpleText"]?.jsonPrimitive?.contentOrNull
                ?: value["content"]?.jsonPrimitive?.contentOrNull
                ?: value["runs"]?.jsonArray?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
            else -> ""
        }
        fun visit(node: JsonElement, depth: Int = 0) {
            if (depth > 70 || videos.size > 200) return
            when (node) {
                is JsonArray -> node.forEach { visit(it, depth + 1) }
                is JsonObject -> {
                    // Do not mine the sidebar, other tabs, account menus or player suggestions for a fake Home fallback.
                    if (node["selected"]?.jsonPrimitive?.booleanOrNull == false) return
                    (node["continuationItemRenderer"] as? JsonObject)?.let { continuation ->
                        val endpoint = continuation["continuationEndpoint"] as? JsonObject
                        val command = endpoint?.get("continuationCommand") as? JsonObject
                        command?.get("token")?.jsonPrimitive?.contentOrNull?.takeIf { it.length in 1..32768 }?.let { next = it }
                    }
                    for (key in listOf("videoRenderer", "gridVideoRenderer", "videoWithContextRenderer", "reelItemRenderer")) {
                        val item = node[key] as? JsonObject ?: continue
                        val id = item["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val title = text(item["title"] ?: item["headline"])
                        if (!Regex("[A-Za-z0-9_-]{11}").matches(id) || title.isBlank()) continue
                        val short = key == "reelItemRenderer" || (item["navigationEndpoint"] as? JsonObject)?.containsKey("reelWatchEndpoint") == true ||
                            (item["thumbnailOverlays"] as? JsonArray)?.any { ((it as? JsonObject)?.get("thumbnailOverlayTimeStatusRenderer") as? JsonObject)?.get("style")?.jsonPrimitive?.contentOrNull == "SHORTS" } == true
                        val byline = item["ownerText"] ?: item["shortBylineText"] ?: item["longBylineText"]
                        val channel = text(byline)
                        val channelId = runCatching { byline?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.content }.getOrNull()
                        val thumbnail = runCatching { item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content }.getOrNull().orEmpty()
                        val safeThumbnail = thumbnail.takeIf { candidate -> runCatching {
                            val uri = java.net.URI(candidate)
                            uri.scheme == "https" && uri.host in setOf("i.ytimg.com", "img.youtube.com") && uri.userInfo == null
                        }.getOrDefault(false) }.orEmpty()
                        val parts = text(item["lengthText"]).split(":").mapNotNull { it.toLongOrNull() }
                        val duration = parts.takeIf { it.isNotEmpty() && it.size <= 3 }?.fold(0L) { total, part -> total * 60 + part } ?: 0L
                        videos.putIfAbsent(id, Video(id, title, channel, safeThumbnail, duration,
                            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" }.orEmpty(), short = short))
                    }
                    (node["shortsLockupViewModel"] as? JsonObject)?.let { short ->
                        val command = ((short["onTap"] as? JsonObject)?.get("innertubeCommand") as? JsonObject)?.get("reelWatchEndpoint") as? JsonObject
                        val id = command?.get("videoId")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val title = text((short["overlayMetadata"] as? JsonObject)?.get("primaryText"))
                        val thumbnail = (((short["thumbnail"] as? JsonObject)?.get("sources") as? JsonArray)?.lastOrNull() as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val safe = thumbnail.takeIf { candidate -> runCatching { val uri = java.net.URI(candidate); uri.scheme == "https" && uri.host in setOf("i.ytimg.com", "img.youtube.com") && uri.userInfo == null }.getOrDefault(false) }.orEmpty()
                        if (Regex("[A-Za-z0-9_-]{11}").matches(id) && title.isNotBlank()) videos.putIfAbsent(id, Video(id, title, "YouTube", safe, short = true))
                    }
                    val lockup = node["lockupViewModel"] as? JsonObject
                    if (lockup != null) {
                        val id = lockup["contentId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val metadata = lockup["metadata"]?.jsonObject?.get("lockupMetadataViewModel")?.jsonObject
                        val title = text(metadata?.get("title"))
                        if (Regex("[A-Za-z0-9_-]{11}").matches(id) && title.isNotBlank())
                            videos.putIfAbsent(id, Video(id, title, "YouTube", "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                    }
                    for (key in listOf("messageRenderer", "backgroundPromoRenderer", "feedNudgeRenderer")) {
                        val message = node[key] as? JsonObject ?: continue
                        val rendered = listOf("title", "text", "bodyText", "body", "subtitle").map { text(message[it]) }.filter { it.isNotBlank() }.joinToString("\n")
                        if (rendered.isNotBlank()) messages += rendered
                    }
                    node.values.forEach { visit(it, depth + 1) }
                }
                else -> Unit
            }
        }
        visit(primary)
        return YouTubeHomePage(videos.values.toList(), messages.distinct().joinToString("\n").takeIf { it.isNotBlank() }
            ?: if (videos.isEmpty()) "YouTube returned no supported Home video cards. Your history/recommendation settings have not been changed, and the app has not filled this with search results." else null, continuation = next)
    }

    fun extractInitialData(html: String): String {
        val assignment = Regex("(?:var\\s+)?ytInitialData\\s*=\\s*\\{|window\\[\"ytInitialData\"\\]\\s*=\\s*\\{").find(html)
            ?: throw IOException("YouTube Home data is unavailable. Reconnect to check consent/sign-in. No fallback feed was substituted.")
        val start = html.indexOf('{', assignment.range.first)
        var depth = 0; var quoted = false; var escaped = false
        for (index in start until html.length) {
            val character = html[index]
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return html.substring(start, index + 1) }
            }
        }
        throw IOException("YouTube Home data was incomplete. Please retry.")
    }
}
