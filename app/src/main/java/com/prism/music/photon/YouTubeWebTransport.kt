package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Ephemeral, account-pinned credentials. Never persisted or included in toString/diagnostics. */
class YouTubeWebSession(val account: Int, val revision: Long, private val youtubeCookie: String, private val statsCookie: String) {
    init { require(account in 0..9); require(listOf(youtubeCookie, statsCookie).none { it.contains('\r') || it.contains('\n') }) }
    internal fun cookie(host: String): String = when (host) { "www.youtube.com" -> youtubeCookie; "s.youtube.com" -> statsCookie; else -> throw IOException("Untrusted YouTube host") }
    override fun toString() = "YouTubeWebSession(redacted)"
}

internal class YouTubeWebTransport(client: OkHttpClient = OkHttpClient()) {
    private class NoReplay(val permitted: () -> Boolean) { val sent = AtomicBoolean(false) }
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val guard = chain.request().tag(NoReplay::class.java) ?: throw IOException("Missing request replay guard")
            if (!guard.permitted()) throw IOException("Account request permission was revoked")
            if (!guard.sent.compareAndSet(false, true)) throw IOException("Automatic account-request replay blocked")
            chain.proceed(chain.request())
        }
        .callTimeout(30, TimeUnit.SECONDS).build()
    suspend fun html(path: String, session: YouTubeWebSession): String = withContext(Dispatchers.IO) {
        require(path == "/" || path == "/feed/history" || path.startsWith("/watch?v=") && Regex("/watch\\?v=[A-Za-z0-9_-]{11}").matches(path))
        val url = "https://www.youtube.com$path".toHttpUrl().newBuilder().addQueryParameter("authuser", session.account.toString()).addQueryParameter("hl", "en").build()
        execute(builder(url, session).get().build())
    }
    suspend fun post(endpoint: String, context: JsonObject, body: JsonObject, session: YouTubeWebSession, permitted: () -> Boolean = { true }): JsonObject = withContext(Dispatchers.IO) {
        require(endpoint in setOf("browse", "feedback"))
        val payload = JsonObject(body + ("context" to context))
        val request = builder("https://www.youtube.com/youtubei/v1/$endpoint".toHttpUrl(), session, context, permitted)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val response = execute(request)
        runCatching { Json.parseToJsonElement(response).jsonObject }.getOrElse { throw IOException("YouTube returned an unreadable account response") }
    }
    suspend fun playback(url: HttpUrl, session: YouTubeWebSession, context: JsonObject, permitted: () -> Boolean): Unit = withContext(Dispatchers.IO) {
        require(YouTubePlaybackUrls.allowed(url))
        http.newCall(builder(url, session, context, permitted).get().build()).execute().use {
            if (!it.isSuccessful) throw IOException("YouTube did not accept the playback report")
        }
    }
    private fun builder(url: HttpUrl, session: YouTubeWebSession, context: JsonObject? = null, permitted: () -> Boolean = { true }): Request.Builder {
        require(url.scheme == "https" && url.host in setOf("www.youtube.com", "s.youtube.com") && url.port == 443 && url.username.isEmpty() && url.password.isEmpty())
        val cookie = session.cookie(url.host)
        if (cookie.isBlank()) throw IOException("YouTube web session is unavailable")
        return Request.Builder().url(url).tag(NoReplay::class.java, NoReplay(permitted)).header("Cookie", cookie).header("X-Goog-AuthUser", session.account.toString())
            .header("Accept-Language", "en-US,en;q=0.8").header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .apply {
                if (context != null) {
                    val client = context["client"] as? JsonObject ?: throw IOException("Missing YouTube web context")
                    val version = client["clientVersion"]?.jsonPrimitive?.content.orEmpty()
                    require(version.matches(Regex("[A-Za-z0-9._-]{1,100}")))
                    header("Origin", "https://www.youtube.com"); header("Referer", "https://www.youtube.com/")
                    header("X-Origin", "https://www.youtube.com"); header("X-YouTube-Client-Name", "1"); header("X-YouTube-Client-Version", version)
                    val sid = cookie.split(';').mapNotNull { part -> part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it).trim() to part.substring(it + 1).trim() } }.toMap()
                    val secret = sid["SAPISID"]?.takeIf { it.isNotBlank() } ?: sid["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() } ?: throw IOException("YouTube session authorization is unavailable")
                    val now = System.currentTimeMillis() / 1000
                    val digest = MessageDigest.getInstance("SHA-1").digest("$now $secret https://www.youtube.com".toByteArray()).joinToString("") { "%02x".format(it) }
                    header("Authorization", "SAPISIDHASH ${now}_$digest")
                    (context["user"] as? JsonObject)?.get("onBehalfOfUser")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.matches(Regex("[0-9]{1,80}")) }?.let { header("X-Goog-PageId", it) }
                }
            }
    }
    private fun execute(request: Request): String = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("YouTube account request failed; reconnect or retry explicitly")
        val output = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                if (output.size() + count > 8 * 1024 * 1024) throw IOException("YouTube response exceeded the size limit")
                output.write(buffer, 0, count)
            }
        }
        output.toString(Charsets.UTF_8.name())
    }
}

internal object YouTubeWebBootstrap {
    fun assigned(html: String, name: String): JsonObject {
        val marker = Regex("(?:var\\s+)?${Regex.escape(name)}\\s*=\\s*\\{|window\\[\"${Regex.escape(name)}\"\\]\\s*=\\s*\\{").find(html)
            ?: throw IOException("YouTube account page data is unavailable")
        return objectAt(html, html.indexOf('{', marker.range.first))
    }
    private fun objectAt(text: String, start: Int): JsonObject {
        var depth = 0; var quote = false; var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (quote) { if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') quote = false }
            else when (c) { '"' -> quote = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) return Json.parseToJsonElement(text.substring(start, i + 1)).jsonObject } }
        }
        throw IOException("Incomplete YouTube account page")
    }
    fun context(html: String): JsonObject? {
        for (match in Regex("ytcfg\\.set\\(\\s*\\{").findAll(html)) {
            val cfg = runCatching { objectAt(html, html.indexOf('{', match.range.first)) }.getOrNull() ?: continue
            val context = cfg["INNERTUBE_CONTEXT"] as? JsonObject ?: continue
            val client = context["client"] as? JsonObject ?: continue
            if (client["clientName"]?.jsonPrimitive?.content == "WEB" && !client["clientVersion"]?.jsonPrimitive?.content.isNullOrBlank()) return context
        }
        return null
    }
    fun signedIn(root: JsonObject, html: String = ""): Boolean {
        val loggedOut = ((root["responseContext"] as? JsonObject)?.get("mainAppWebResponseContext") as? JsonObject)?.get("loggedOut")?.jsonPrimitive?.booleanOrNull
        return loggedOut == false || loggedOut == null && Regex("\"LOGGED_IN\"\\s*:\\s*true").containsMatchIn(html.substringBefore("var ytInitialData"))
    }
}

