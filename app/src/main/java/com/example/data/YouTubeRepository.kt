package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class YouTubeRepository {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val relatedCache = ConcurrentHashMap<String, Pair<Long, List<YouTubeMediaItem>>>()

    private fun buildWebClientContext(): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20250301.00.00")
                put("hl", "en")
                put("gl", "US")
            }
        )
    }

    private fun buildMusicClientContext(): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20250301.01.00")
                put("hl", "en")
                put("gl", "US")
            }
        )
    }

    private fun extractCookieValue(cookieHeader: String, name: String): String? {
        return cookieHeader.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Computes standard YouTube first-party `SAPISIDHASH` header for authenticated YouTube/YT Music
     * InnerTube requests using cookies strictly on https://www.youtube.com or https://music.youtube.com.
     */
    private fun buildSapisidAuthorization(cookieHeader: String?, origin: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        val sapisid = extractCookieValue(cookieHeader, "SAPISID")
            ?: extractCookieValue(cookieHeader, "__Secure-3PAPISID")
            ?: extractCookieValue(cookieHeader, "__Secure-1PAPISID")
            ?: return null
        val epochSec = System.currentTimeMillis() / 1000L
        val raw = "$epochSec $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${epochSec}_$hex"
    }

    fun hasAuthenticatedYouTubeSession(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false
        return extractCookieValue(cookieHeader, "SAPISID") != null ||
            extractCookieValue(cookieHeader, "__Secure-3PAPISID") != null
    }

    private fun postInnerTube(
        endpoint: String,
        payload: JSONObject,
        isMusicHost: Boolean = false,
        cookieHeader: String? = null
    ): JSONObject {
        val origin = if (isMusicHost) "https://music.youtube.com" else "https://www.youtube.com"
        val builder = Request.Builder()
            .url("$origin/youtubei/v1/$endpoint?prettyPrint=false")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("X-Origin", origin)
            .header("X-YouTube-Client-Name", if (isMusicHost) "67" else "1")
            .header("X-YouTube-Client-Version", if (isMusicHost) "1.20250301.01.00" else "2.20250301.00.00")

        val authHeader = buildSapisidAuthorization(cookieHeader, origin)
        if (authHeader != null && !cookieHeader.isNullOrBlank()) {
            builder.header("Cookie", cookieHeader)
            builder.header("Authorization", authHeader)
            builder.header("X-Goog-AuthUser", "0")
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube request failed (${response.code})")
            }
            val bodyStr = response.body?.string().orEmpty()
            if (bodyStr.isBlank()) {
                throw IllegalStateException("Empty response from YouTube")
            }
            return JSONObject(bodyStr)
        }
    }

    /**
     * Imports real personalized feeds, subscriptions, and liked items directly from the user's
     * authenticated YouTube and YouTube Music session cookies.
     */
    suspend fun fetchAuthenticatedYouTubeImport(cookieHeader: String): Result<YouTubeAccountImportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!hasAuthenticatedYouTubeSession(cookieHeader)) {
                    throw IllegalStateException("No authenticated YouTube session found")
                }

                coroutineScope {
                    val accountDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "account/account_menu",
                                payload = JSONObject().put("context", buildWebClientContext()),
                                isMusicHost = false,
                                cookieHeader = cookieHeader
                            )
                            extractAccountHandle(root)
                        }.getOrDefault("")
                    }

                    val ytHomeDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "browse",
                                payload = JSONObject()
                                    .put("context", buildWebClientContext())
                                    .put("browseId", "FEwhat_to_watch"),
                                isMusicHost = false,
                                cookieHeader = cookieHeader
                            )
                            extractVideosRecursively(root, isMusicContext = false).take(24)
                        }.getOrDefault(emptyList())
                    }

                    val ytSubsDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "browse",
                                payload = JSONObject()
                                    .put("context", buildWebClientContext())
                                    .put("browseId", "FEsubscriptions"),
                                isMusicHost = false,
                                cookieHeader = cookieHeader
                            )
                            extractVideosRecursively(root, isMusicContext = false).take(24)
                        }.getOrDefault(emptyList())
                    }

                    val ytLikedDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "browse",
                                payload = JSONObject()
                                    .put("context", buildWebClientContext())
                                    .put("browseId", "VLLL"),
                                isMusicHost = false,
                                cookieHeader = cookieHeader
                            )
                            extractVideosRecursively(root, isMusicContext = false).take(20)
                        }.getOrDefault(emptyList())
                    }

                    val ytmHomeDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "browse",
                                payload = JSONObject()
                                    .put("context", buildMusicClientContext())
                                    .put("browseId", "FEmusic_home"),
                                isMusicHost = true,
                                cookieHeader = cookieHeader
                            )
                            extractVideosRecursively(root, isMusicContext = true).take(24)
                        }.getOrDefault(emptyList())
                    }

                    val ytmLikedDeferred = async {
                        runCatching {
                            val root = postInnerTube(
                                endpoint = "browse",
                                payload = JSONObject()
                                    .put("context", buildMusicClientContext())
                                    .put("browseId", "VLLM"),
                                isMusicHost = true,
                                cookieHeader = cookieHeader
                            )
                            extractVideosRecursively(root, isMusicContext = true).take(20)
                        }.getOrDefault(emptyList())
                    }

                    val handle = accountDeferred.await()
                    val ytHome = ytHomeDeferred.await()
                    val ytSubs = ytSubsDeferred.await()
                    val ytLiked = ytLikedDeferred.await()
                    val ytmHome = ytmHomeDeferred.await()
                    val ytmLiked = ytmLikedDeferred.await()

                    val channels = (ytSubs + ytHome + ytLiked)
                        .map { it.channelName.trim() }
                        .filter { it.isNotBlank() && !it.equals("YouTube", true) }
                        .distinct()
                        .take(18)

                    val artists = (ytmLiked + ytmHome)
                        .map { it.channelName.trim() }
                        .filter { it.isNotBlank() && !it.equals("YouTube", true) && !it.equals("YouTube Music", true) }
                        .distinct()
                        .take(18)

                    YouTubeAccountImportResult(
                        accountHandle = handle,
                        youtubeHomeItems = ytHome,
                        subscriptionItems = ytSubs,
                        likedVideos = ytLiked,
                        ytMusicHomeItems = ytmHome,
                        ytMusicLikedSongs = ytmLiked,
                        discoveredChannels = channels,
                        discoveredArtists = artists
                    )
                }
            }
        }

    /**
     * Fetches personalized YouTube Home feed adjusted to the user's live YouTube account,
     * imported channels, liked videos, and watch history.
     */
    suspend fun fetchPersonalizedYouTubeFeed(
        category: YouTubeCategory,
        profile: UserAccountProfile,
        likedItems: List<YouTubeMediaItem>,
        historyItems: List<YouTubeMediaItem>,
        cookieHeader: String? = null
    ): Result<List<YouTubeMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val hasSession = hasAuthenticatedYouTubeSession(cookieHeader)

            if (category == YouTubeCategory.FOR_YOU) {
                if (hasSession) {
                    val liveHome = runCatching {
                        val root = postInnerTube(
                            endpoint = "browse",
                            payload = JSONObject()
                                .put("context", buildWebClientContext())
                                .put("browseId", "FEwhat_to_watch"),
                            isMusicHost = false,
                            cookieHeader = cookieHeader
                        )
                        extractVideosRecursively(root, isMusicContext = false).take(24)
                    }.getOrNull()
                    if (!liveHome.isNullOrEmpty()) return@runCatching liveHome
                }

                val seeds = buildYouTubeSeedQueries(profile, likedItems, historyItems)
                if (seeds.isNotEmpty()) {
                    val blended = fetchBlendedQueries(seeds, isMusicContext = false)
                    if (blended.isNotEmpty()) return@runCatching blended
                }
            }

            if (category == YouTubeCategory.SUBSCRIPTIONS) {
                if (hasSession) {
                    val liveSubs = runCatching {
                        val root = postInnerTube(
                            endpoint = "browse",
                            payload = JSONObject()
                                .put("context", buildWebClientContext())
                                .put("browseId", "FEsubscriptions"),
                            isMusicHost = false,
                            cookieHeader = cookieHeader
                        )
                        extractVideosRecursively(root, isMusicContext = false).take(24)
                    }.getOrNull()
                    if (!liveSubs.isNullOrEmpty()) return@runCatching liveSubs
                }

                val channelSeeds = profile.subscribedChannels.take(5).map { "$it latest video" }
                if (channelSeeds.isNotEmpty()) {
                    val blended = fetchBlendedQueries(channelSeeds, isMusicContext = false)
                    if (blended.isNotEmpty()) return@runCatching blended
                }
            }

            searchInternal(
                query = category.searchQuery,
                isMusicContext = category == YouTubeCategory.MUSIC,
                params = if (category.liveOnly) PARAM_LIVE_ONLY else PARAM_VIDEOS_ONLY
            ).take(24)
        }
    }

    /**
     * Fetches personalized YouTube Music feed adjusted to the user's live YT Music account,
     * imported artists, liked songs, and music listening history.
     */
    suspend fun fetchPersonalizedMusicFeed(
        category: MusicCategory,
        profile: UserAccountProfile,
        likedItems: List<YouTubeMediaItem>,
        historyItems: List<YouTubeMediaItem>,
        cookieHeader: String? = null
    ): Result<List<YouTubeMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val hasSession = hasAuthenticatedYouTubeSession(cookieHeader)

            if (category == MusicCategory.FOR_YOU) {
                if (hasSession) {
                    val liveMusicHome = runCatching {
                        val root = postInnerTube(
                            endpoint = "browse",
                            payload = JSONObject()
                                .put("context", buildMusicClientContext())
                                .put("browseId", "FEmusic_home"),
                            isMusicHost = true,
                            cookieHeader = cookieHeader
                        )
                        extractVideosRecursively(root, isMusicContext = true).take(24)
                    }.getOrNull()
                    if (!liveMusicHome.isNullOrEmpty()) return@runCatching liveMusicHome
                }

                val artistSeeds = buildMusicSeedQueries(profile, likedItems, historyItems)
                if (artistSeeds.isNotEmpty()) {
                    val blended = fetchBlendedQueries(artistSeeds, isMusicContext = true)
                    if (blended.isNotEmpty()) return@runCatching blended
                }
            }

            if (category == MusicCategory.YOUR_MIX) {
                val mixSeeds = buildMusicSeedQueries(profile, likedItems, historyItems)
                    .map { "$it mix official audio" }
                if (mixSeeds.isNotEmpty()) {
                    val blended = fetchBlendedQueries(mixSeeds, isMusicContext = true)
                    if (blended.isNotEmpty()) return@runCatching blended
                }
            }

            searchInternal(
                query = category.searchQuery,
                isMusicContext = true,
                params = PARAM_VIDEOS_ONLY
            ).take(24)
        }
    }

    /**
     * Looks up a public YouTube handle (e.g. @MKBHD) or creator name to import real channel videos.
     */
    suspend fun importPublicHandleItems(handleOrName: String): Result<List<YouTubeMediaItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = handleOrName.trim().removePrefix("@").take(80)
                if (clean.isBlank()) return@runCatching emptyList()
                searchInternal(
                    query = clean,
                    isMusicContext = false,
                    params = PARAM_VIDEOS_ONLY
                ).take(12)
            }
        }

    private fun buildYouTubeSeedQueries(
        profile: UserAccountProfile,
        likedItems: List<YouTubeMediaItem>,
        historyItems: List<YouTubeMediaItem>
    ): List<String> {
        val channels = LinkedHashSet<String>()
        profile.subscribedChannels.forEach { channels.add(it) }
        if (profile.youtubeHandle.isNotBlank()) {
            channels.add(profile.youtubeHandle.removePrefix("@"))
        }
        likedItems.filter { !it.isMusic }.take(4).forEach { channels.add(it.channelName) }
        historyItems.filter { !it.isMusic }.take(3).forEach { channels.add(it.channelName) }
        return channels
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("YouTube", true) }
            .take(4)
            .map { "$it videos" }
    }

    private fun buildMusicSeedQueries(
        profile: UserAccountProfile,
        likedItems: List<YouTubeMediaItem>,
        historyItems: List<YouTubeMediaItem>
    ): List<String> {
        val artists = LinkedHashSet<String>()
        profile.favoriteArtists.forEach { artists.add(it) }
        likedItems.filter { it.isMusic }.take(4).forEach { artists.add(it.channelName) }
        historyItems.filter { it.isMusic }.take(3).forEach { artists.add(it.channelName) }
        return artists
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("YouTube", true) }
            .take(4)
            .map { "$it official audio song" }
    }

    private suspend fun fetchBlendedQueries(
        queries: List<String>,
        isMusicContext: Boolean
    ): List<YouTubeMediaItem> = coroutineScope {
        val batches = queries.map { q ->
            async {
                runCatching {
                    searchInternal(
                        query = q,
                        isMusicContext = isMusicContext,
                        params = PARAM_VIDEOS_ONLY
                    ).take(8)
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        // Interleave results across user's imported channels/artists for a rich YouTube-style feed
        val merged = mutableListOf<YouTubeMediaItem>()
        val seen = mutableSetOf<String>()
        val maxLen = batches.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (batch in batches) {
                val item = batch.getOrNull(i) ?: continue
                if (seen.add(item.videoId)) {
                    merged.add(item)
                }
            }
        }
        merged.take(24)
    }

    suspend fun search(
        query: String,
        mode: AppMode,
        filter: SearchFilter
    ): Result<List<YouTubeMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@runCatching emptyList()

            val effectiveQuery = when {
                filter == SearchFilter.MUSIC -> if (trimmed.contains("audio", true) || trimmed.contains("music", true)) trimmed else "$trimmed official audio"
                filter == SearchFilter.PODCASTS -> if (trimmed.contains("podcast", true)) trimmed else "$trimmed podcast"
                mode == AppMode.MUSIC && filter == SearchFilter.ALL -> {
                    if (trimmed.contains("official", true) || trimmed.contains("audio", true) || trimmed.contains("song", true)) trimmed else "$trimmed song"
                }
                else -> trimmed
            }

            val params = when (filter) {
                SearchFilter.LIVE -> PARAM_LIVE_ONLY
                else -> PARAM_VIDEOS_ONLY
            }

            searchInternal(
                query = effectiveQuery,
                isMusicContext = mode == AppMode.MUSIC || filter == SearchFilter.MUSIC,
                params = params
            ).take(24)
        }
    }

    suspend fun fetchRelatedVideos(videoId: String, isMusicContext: Boolean): Result<List<YouTubeMediaItem>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            relatedCache[videoId]?.let { (ts, items) ->
                if (now - ts < CACHE_TTL_MS && items.isNotEmpty()) {
                    return@withContext Result.success(items)
                }
            }
            runCatching {
                val payload = JSONObject().apply {
                    put("context", buildWebClientContext())
                    put("videoId", videoId)
                }
                val root = postInnerTube("next", payload)
                val items = extractVideosRecursively(root, isMusicContext = isMusicContext)
                    .filter { it.videoId != videoId }
                    .take(18)
                if (items.isNotEmpty()) {
                    relatedCache[videoId] = now to items
                }
                items
            }
        }

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val raw = response.body?.string().orEmpty()
                val outer = JSONArray(raw)
                val suggestions = outer.optJSONArray(1) ?: return@use emptyList()
                buildList {
                    for (i in 0 until suggestions.length().coerceAtMost(6)) {
                        val s = suggestions.optString(i).trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun searchInternal(
        query: String,
        isMusicContext: Boolean,
        params: String? = null
    ): List<YouTubeMediaItem> {
        val payload = JSONObject().apply {
            put("context", buildWebClientContext())
            put("query", query)
            if (!params.isNullOrBlank()) {
                put("params", params)
            }
        }
        val primary = runCatching {
            val root = postInnerTube("search", payload)
            extractVideosRecursively(root, isMusicContext = isMusicContext)
        }.getOrNull()

        if (!primary.isNullOrEmpty()) return primary

        if (!params.isNullOrBlank()) {
            val fallbackPayload = JSONObject().apply {
                put("context", buildWebClientContext())
                put("query", query)
            }
            val fallback = runCatching {
                val root = postInnerTube("search", fallbackPayload)
                extractVideosRecursively(root, isMusicContext = isMusicContext)
            }.getOrNull()
            if (!fallback.isNullOrEmpty()) return fallback
        }

        // Fallback to standard YouTube web search HTML ytInitialData if InnerTube search is throttled
        return runCatching {
            fetchWebSearchHtmlFallback(query, isMusicContext)
        }.getOrDefault(emptyList())
    }

    private fun fetchWebSearchHtmlFallback(query: String, isMusicContext: Boolean): List<YouTubeMediaItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://www.youtube.com/results?search_query=$encoded")
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            val marker = "var ytInitialData = "
            val start = html.indexOf(marker)
            if (start < 0) return emptyList()
            val jsonStart = start + marker.length
            val end = html.indexOf(";</script>", jsonStart)
            if (end <= jsonStart) return emptyList()
            val json = JSONObject(html.substring(jsonStart, end))
            return extractVideosRecursively(json, isMusicContext)
        }
    }

    private fun extractAccountHandle(root: Any?): String {
        var found = ""
        fun walk(node: Any?) {
            if (found.isNotBlank()) return
            when (node) {
                is JSONObject -> {
                    val header = node.optJSONObject("activeAccountHeaderRenderer")
                    if (header != null) {
                        val handle = extractText(header.optJSONObject("channelHandle")).trim()
                        val name = extractText(header.optJSONObject("accountName")).trim()
                        found = handle.ifBlank { name }
                        return
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        walk(node.opt(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) walk(node.opt(i))
                }
            }
        }
        walk(root)
        return found
    }

    private fun extractVideosRecursively(
        root: Any?,
        isMusicContext: Boolean
    ): List<YouTubeMediaItem> {
        val collected = mutableListOf<YouTubeMediaItem>()
        val seenIds = mutableSetOf<String>()

        fun addIfNew(item: YouTubeMediaItem?) {
            if (item != null && seenIds.add(item.videoId)) {
                collected.add(item)
            }
        }

        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val videoObj = node.optJSONObject("videoRenderer")
                        ?: node.optJSONObject("compactVideoRenderer")
                        ?: node.optJSONObject("gridVideoRenderer")
                        ?: node.optJSONObject("playlistVideoRenderer")
                    if (videoObj != null) {
                        addIfNew(parseVideoRenderer(videoObj, isMusicContext))
                    }

                    val musicRow = node.optJSONObject("musicResponsiveListItemRenderer")
                    if (musicRow != null) {
                        addIfNew(parseMusicResponsiveItem(musicRow))
                    }

                    val musicCard = node.optJSONObject("musicTwoRowItemRenderer")
                    if (musicCard != null) {
                        addIfNew(parseMusicTwoRowItem(musicCard))
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k != "videoRenderer" &&
                            k != "compactVideoRenderer" &&
                            k != "gridVideoRenderer" &&
                            k != "playlistVideoRenderer" &&
                            k != "musicResponsiveListItemRenderer" &&
                            k != "musicTwoRowItemRenderer"
                        ) {
                            walk(node.opt(k))
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))
                    }
                }
            }
        }

        walk(root)
        return collected
    }

    private fun parseVideoRenderer(obj: JSONObject, isMusicContext: Boolean): YouTubeMediaItem? {
        val videoId = obj.optString("videoId").trim()
        if (videoId.length < 6) return null

        val title = extractText(obj.optJSONObject("title")).trim()
        if (title.isEmpty()) return null

        val channel = extractText(obj.optJSONObject("ownerText"))
            .ifBlank { extractText(obj.optJSONObject("longBylineText")) }
            .ifBlank { extractText(obj.optJSONObject("shortBylineText")) }
            .ifBlank { "YouTube" }

        val durationText = extractText(obj.optJSONObject("lengthText")).trim()
        val isLive = durationText.isEmpty() && (
            hasLiveBadge(obj.optJSONArray("badges")) ||
                obj.optJSONObject("thumbnailOverlays")?.toString()?.contains("LIVE", true) == true
            )

        val viewCountText = extractText(obj.optJSONObject("shortViewCountText"))
            .ifBlank { extractText(obj.optJSONObject("viewCountText")) }
            .trim()

        val publishedText = extractText(obj.optJSONObject("publishedTimeText")).trim()

        val thumbUrl = pickBestThumbnail(obj.optJSONObject("thumbnail"))
            .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

        val avatarUrl = obj.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.let { pickBestThumbnail(it) }
            ?: obj.optJSONObject("channelThumbnail")
                ?.let { pickBestThumbnail(it) }
            ?: ""

        return YouTubeMediaItem(
            videoId = videoId,
            title = title,
            channelName = channel,
            durationText = if (isLive) "LIVE" else durationText,
            durationSec = if (isLive) 0 else parseDurationToSeconds(durationText),
            viewCountText = viewCountText,
            publishedTimeText = publishedText,
            thumbnailUrl = thumbUrl,
            channelAvatarUrl = avatarUrl,
            isLive = isLive,
            isMusic = isMusicContext
        )
    }

    private fun parseMusicResponsiveItem(obj: JSONObject): YouTubeMediaItem? {
        val videoId = obj.optJSONObject("playlistItemData")?.optString("videoId")?.trim()
            ?.takeIf { it.length >= 6 }
            ?: obj.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.trim()
            ?: return null

        if (videoId.length < 6) return null

        val flexCols = obj.optJSONArray("flexColumns") ?: return null
        val title = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractText(it) }
            ?.trim()
            .orEmpty()
        if (title.isEmpty()) return null

        val subtitleRaw = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractText(it) }
            ?.trim()
            .orEmpty()

        val artist = subtitleRaw.split("•", "·").firstOrNull()?.trim().orEmpty().ifBlank { "YouTube Music" }
        val durationText = subtitleRaw.split("•", "·")
            .lastOrNull { it.trim().contains(":") }
            ?.trim()
            .orEmpty()

        val thumbUrl = obj.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.let { pickBestThumbnail(it) }
            ?.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return YouTubeMediaItem(
            videoId = videoId,
            title = title,
            channelName = artist,
            durationText = durationText,
            durationSec = parseDurationToSeconds(durationText),
            thumbnailUrl = thumbUrl,
            isLive = false,
            isMusic = true
        )
    }

    private fun parseMusicTwoRowItem(obj: JSONObject): YouTubeMediaItem? {
        val videoId = obj.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.trim()
            ?: return null
        if (videoId.length < 6) return null

        val title = extractText(obj.optJSONObject("title")).trim()
        if (title.isEmpty()) return null

        val subtitle = extractText(obj.optJSONObject("subtitle")).trim()
        val artist = subtitle.split("•", "·").firstOrNull()?.trim().orEmpty().ifBlank { "YouTube Music" }

        val thumbUrl = obj.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.let { pickBestThumbnail(it) }
            ?.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return YouTubeMediaItem(
            videoId = videoId,
            title = title,
            channelName = artist,
            thumbnailUrl = thumbUrl,
            isLive = false,
            isMusic = true
        )
    }

    private fun extractText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val part = runs.optJSONObject(i)?.optString("text").orEmpty()
            sb.append(part)
        }
        return sb.toString()
    }

    private fun pickBestThumbnail(thumbnailObj: JSONObject?): String {
        val list = thumbnailObj?.optJSONArray("thumbnails") ?: return ""
        var bestUrl = ""
        var bestWidth = -1
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val width = item.optInt("width", 0)
            if (url.isNotBlank() && width >= bestWidth) {
                bestWidth = width
                bestUrl = if (url.startsWith("//")) "https:$url" else url
            }
        }
        return bestUrl
    }

    private fun hasLiveBadge(badges: JSONArray?): Boolean {
        if (badges == null) return false
        val text = badges.toString()
        return text.contains("BADGE_STYLE_TYPE_LIVE_NOW") || text.contains("\"LIVE\"")
    }

    companion object {
        private const val PARAM_VIDEOS_ONLY = "EgIQAQ=="
        private const val PARAM_LIVE_ONLY = "EgJAAQ=="
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
