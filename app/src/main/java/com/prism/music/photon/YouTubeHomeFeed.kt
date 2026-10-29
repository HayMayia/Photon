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
data class YouTubeHomePage(val videos: List<Video>, val message: String? = null)
data class YouTubeHomeState(val busy: Boolean = false, val page: YouTubeHomePage? = null, val error: String? = null)

class YouTubeHomeApi(client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    suspend fun home(cookie: String, accountIndex: Int = 0): YouTubeHomePage = withContext(Dispatchers.IO) {
        if (cookie.isBlank() || cookie.contains('\r') || cookie.contains('\n')) throw IOException("Connect a YouTube web session first.")
        require(accountIndex in 0..9) { "Invalid YouTube web account index" }
        val request = Request.Builder().url("https://www.youtube.com/?authuser=$accountIndex")
            .header("X-Goog-AuthUser", accountIndex.toString())
            .header("Cookie", cookie)
            // Desktop layout for parsing YouTube's returned Home cards; never used for Google login.
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.8").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube Home could not load. Reconnect to review sign-in or consent; no local recommendations were substituted.")
            val output = ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > 8 * 1024 * 1024) throw IOException("YouTube Home response was too large. No fallback feed was substituted.")
                    output.write(buffer, 0, read)
                }
            }
            YouTubeHomeParser.parse(output.toString(Charsets.UTF_8.name()))
        }
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
        val videos = linkedMapOf<String, Video>()
        val messages = mutableListOf<String>()
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
                    for (key in listOf("videoRenderer", "gridVideoRenderer", "videoWithContextRenderer")) {
                        val item = node[key] as? JsonObject ?: continue
                        val id = item["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val title = text(item["title"] ?: item["headline"])
                        if (!Regex("[A-Za-z0-9_-]{11}").matches(id) || title.isBlank()) continue
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
                            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" }.orEmpty()))
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
        val container = contents as? JsonObject ?: throw IOException("Unrecognized YouTube Home layout. No fallback feed was substituted.")
        val primary = (container["twoColumnBrowseResultsRenderer"] as? JsonObject)?.get("tabs")
            ?: (container["singleColumnBrowseResultsRenderer"] as? JsonObject)?.get("tabs")
            ?: container["richGridRenderer"] ?: container["sectionListRenderer"]
            ?: throw IOException("YouTube Home layout changed. No fallback feed was substituted.")
        visit(primary)
        return YouTubeHomePage(videos.values.toList(), messages.distinct().joinToString("\n").takeIf { it.isNotBlank() }
            ?: if (videos.isEmpty()) "YouTube returned no supported Home video cards. Your history/recommendation settings have not been changed, and Photon has not filled this with search results." else null)
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
