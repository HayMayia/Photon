package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.io.IOException

enum class AccountShelf(val label: String, val path: String) {
    HISTORY("History", "/feed/history"), PLAYLISTS("Playlists", "/feed/playlists"),
    LIKED("Liked videos", "/playlist?list=LL"), LIKED_MUSIC("Liked Music", "/playlist?list=LL"), CHANNELS("Channels", "/feed/channels")
}
enum class AccountEntryKind { VIDEO, PLAYLIST, CHANNEL }
data class AccountEntry(val id: String, val title: String, val kind: AccountEntryKind, val video: Video? = null, val artwork: String = "", val layoutShort: Boolean? = null)
data class AccountLibraryPage(val items: List<AccountEntry>, val continuation: String? = null, val context: JsonObject? = null, val message: String? = null, val unsupportedRows: Int = 0)
data class AccountLibraryState(val shelf: AccountShelf = AccountShelf.HISTORY, val playlist: String? = null, val title: String = "History",
    val page: AccountLibraryPage? = null, val busy: Boolean = false, val moreBusy: Boolean = false, val error: String? = null, val generation: Long = 0)

internal class YouTubeAccountLibraryApi(client: OkHttpClient = OkHttpClient()) {
    private val transport = YouTubeWebTransport(client)
    private val musicTransport = YouTubeWebTransport(client, music = true)
    suspend fun likedSongs(session: YouTubeWebSession, previous: AccountLibraryPage? = null): AccountLibraryPage = withContext(Dispatchers.IO) {
        var bootstrap: JsonObject? = null
        val context = previous?.context ?: run {
            val html = musicTransport.html("/playlist?list=LM", session)
            bootstrap = runCatching { YouTubeWebBootstrap.assigned(html, "ytInitialData") }.getOrNull()
            YouTubeWebBootstrap.context(html, music = true) ?: throw IOException("YouTube Music context unavailable")
        }
        // Some accounts receive the complete private list in HTML; use it rather than
        // discarding it and depending on another browse request succeeding.
        bootstrap?.let { initial ->
            runCatching { YouTubeLikedSongs.parse(initial) }.getOrNull()?.let { return@withContext it.copy(context = context) }
        }
        val body = buildJsonObject {
            if (previous == null) put("browseId", "VLLM")
            else put("continuation", previous.continuation ?: throw IOException("No more liked songs"))
        }
        val response = musicTransport.post("browse", context, body, session)
        YouTubeLikedSongs.parse(response, continuation = previous != null).copy(context = context)
    }
    private val metadataTransport = YouTubeWebTransport(client, timeoutSeconds = 6)
    suspend fun load(session: YouTubeWebSession, shelf: AccountShelf, playlist: String? = null): AccountLibraryPage = withContext(Dispatchers.IO) {
        require(playlist == null || playlist.matches(Regex("[A-Za-z0-9_-]{2,150}")))
        val html = transport.html(playlist?.let { "/playlist?list=$it" } ?: shelf.path, session)
        val root = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(root, html)) throw IOException("YouTube did not confirm the account library login")
        val context = YouTubeWebBootstrap.context(html)
        val list = playlist ?: if (shelf in listOf(AccountShelf.LIKED, AccountShelf.LIKED_MUSIC)) "LL" else null
        val primary = if (list != null) {
            PlaylistContents.primary(root, list) ?: run {
                // Some bootstrap pages expose a preview only. Hydrate the exact playlist, not Home/related cards.
                val response = transport.post("browse", context ?: throw IOException("Playlist context unavailable"),
                    buildJsonObject { put("browseId", "VL$list") }, session)
                PlaylistContents.primary(response, list, requestBound = true) ?: throw IOException("The requested playlist's entries were not returned")
            }
        } else root["contents"] ?: throw IOException("Unrecognized account library page")
        YouTubeAccountLibraryParser.parse(primary, if (playlist != null) AccountEntryKind.VIDEO else when (shelf) {
            AccountShelf.PLAYLISTS -> AccountEntryKind.PLAYLIST
            AccountShelf.CHANNELS -> AccountEntryKind.CHANNEL
            else -> AccountEntryKind.VIDEO
        }).copy(context = context)
    }
    suspend fun musicCategory(session: YouTubeWebSession, video: Video): Boolean? = withContext(Dispatchers.IO) {
        require(video.id.matches(Regex("[A-Za-z0-9_-]{11}")))
        // Read only metadata with the same pinned Google account; no format/stream lookup,
        // playlist mutations, playback reports, or separate YouTube Music login.
        val html = metadataTransport.html("/watch?v=${video.id}", session)
        val initial = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(initial, html)) throw IOException("Account metadata session expired")
        val player = runCatching { YouTubeWebBootstrap.assigned(html, "ytInitialPlayerResponse") }.getOrNull()
        player?.let { YouTubeMusicCategory.parse(it, video.id) } ?: YouTubeMusicEvidence.fromRenderer(initial, video.id)
    }
    suspend fun more(session: YouTubeWebSession, page: AccountLibraryPage, kind: AccountEntryKind): AccountLibraryPage = withContext(Dispatchers.IO) {
        val token = page.continuation?.takeIf { it.length in 1..32768 } ?: throw IOException("No further account page")
        val context = page.context ?: throw IOException("Refresh the account list first")
        val root = transport.post("browse", context, buildJsonObject { put("continuation", token) }, session)
        if (root.containsKey("error") || ((root["responseContext"] as? JsonObject)?.get("mainAppWebResponseContext") as? JsonObject)?.get("loggedOut")?.jsonPrimitive?.booleanOrNull == true)
            throw IOException("Account library session expired")
        val batches = listOfNotNull(root["onResponseReceivedActions"], root["onResponseReceivedEndpoints"], root["continuationContents"])
        if (batches.isEmpty()) throw IOException("Unrecognized account continuation")
        YouTubeAccountLibraryParser.parse(JsonArray(batches), kind).copy(context = context)
    }
}

/** Reads only the returned primary list. Never mixes local items, other tabs or sidebar suggestions into it. */
internal object YouTubeAccountLibraryParser {
    fun parse(primary: JsonElement, kind: AccountEntryKind): AccountLibraryPage {
        val rows = linkedMapOf<String, AccountEntry>()
        val messages = linkedSetOf<String>()
        var token: String? = null
        var unsupportedRows = 0
        fun text(value: JsonElement?): String = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonObject -> text(value["simpleText"] ?: value["content"]).ifBlank {
                (value["runs"] as? JsonArray)?.joinToString("") { text((it as? JsonObject)?.get("text")) }.orEmpty()
            }
            else -> ""
        }
        fun addCollection(item: JsonObject, idKey: String, entryKind: AccountEntryKind) {
            val artwork = GoogleAccountControls.objects(item).mapNotNull { it["url"]?.jsonPrimitive?.contentOrNull }
                .map(ShortsPresentation::safeArtwork).firstOrNull { it.isNotBlank() }.orEmpty()
            val id = text(item[idKey]); val title = text(item["title"])
            val valid = if (entryKind == AccountEntryKind.CHANNEL) id.matches(Regex("UC[A-Za-z0-9_-]{20,30}")) else id.matches(Regex("[A-Za-z0-9_-]{2,150}"))
            if (kind == entryKind && valid && title.isNotBlank()) rows.putIfAbsent(id, AccountEntry(id, title, entryKind, artwork = artwork))
        }
        fun walk(node: JsonElement, depth: Int = 0, tab: Boolean = false) {
            if (depth > 70) return
            if (rows.size > 5000) throw IOException("Account page exceeded the supported size; it was not marked complete")
            when (node) {
                is JsonArray -> node.forEach { walk(it, depth + 1) }
                is JsonObject -> {
                    if (tab && node["selected"]?.jsonPrimitive?.booleanOrNull == false) return
                    (node["continuationItemRenderer"] as? JsonObject)?.let {
                        val command = ((it["continuationEndpoint"] as? JsonObject)?.get("continuationCommand") as? JsonObject)
                        text(command?.get("token")).takeIf { it.length in 1..32768 }?.let { value -> token = value }
                    }
                    (node["continuationCommand"] as? JsonObject)?.get("token")?.let { value ->
                        text(value).takeIf { it.length in 1..32768 }?.let { token = it }
                    }
                    (node["nextContinuationData"] as? JsonObject)?.let { text(it["continuation"]).takeIf { it.length in 1..32768 }?.let { value -> token = value } }
                    for (key in listOf("playlistRenderer", "gridPlaylistRenderer")) (node[key] as? JsonObject)?.let { addCollection(it, "playlistId", AccountEntryKind.PLAYLIST); return }
                    for (key in listOf("channelRenderer", "gridChannelRenderer")) (node[key] as? JsonObject)?.let { addCollection(it, "channelId", AccountEntryKind.CHANNEL); return }
                    val lockup = node["lockupViewModel"] as? JsonObject
                    if (lockup != null && kind != AccountEntryKind.VIDEO) {
                        val type = text(lockup["contentType"])
                        val expected = if (kind == AccountEntryKind.CHANNEL) "LOCKUP_CONTENT_TYPE_CHANNEL" else "LOCKUP_CONTENT_TYPE_PLAYLIST"
                        if (type == expected) {
                            val metadata = ((lockup["metadata"] as? JsonObject)?.get("lockupMetadataViewModel") as? JsonObject)
                            addCollection(buildJsonObject { put(if (kind == AccountEntryKind.CHANNEL) "channelId" else "playlistId", text(lockup["contentId"])); put("title", text(metadata?.get("title"))); lockup["contentImage"]?.let { put("contentImage", it) } }, if (kind == AccountEntryKind.CHANNEL) "channelId" else "playlistId", kind)
                        }
                        return
                    }
                    val musicRow = (node["musicResponsiveListItemRenderer"] ?: node["musicTwoRowItemRenderer"]) as? JsonObject
                    if (kind == AccountEntryKind.VIDEO && musicRow != null) {
                        val watch = (musicRow["navigationEndpoint"] as? JsonObject)?.get("watchEndpoint") as? JsonObject
                        val id = text((musicRow["playlistItemData"] as? JsonObject)?.get("videoId")).ifBlank { text(watch?.get("videoId")) }
                            .ifBlank { GoogleAccountControls.objects(musicRow["overlay"] ?: JsonObject(emptyMap()))
                                .mapNotNull { (it["watchEndpoint"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull }
                                .firstOrNull { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.orEmpty() }
                        val columns = musicRow["flexColumns"] as? JsonArray
                        fun column(index: Int) = ((columns?.getOrNull(index) as? JsonObject)?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject)?.get("text")
                        val title = text(musicRow["title"] ?: column(0))
                        if (id.matches(Regex("[A-Za-z0-9_-]{11}")) && title.isNotBlank()) {
                            val thumb = GoogleAccountControls.objects(musicRow["thumbnail"] ?: musicRow["thumbnailRenderer"] ?: JsonObject(emptyMap()))
                                .mapNotNull { it["thumbnails"] as? JsonArray }.firstOrNull()
                            val video = Video(id, title, text(musicRow["subtitle"] ?: column(1)), ThumbnailPolicy.select(thumb, false),
                                music = YouTubeMusicEvidence.fromRenderer(musicRow, id) == true)
                            rows.putIfAbsent(id, AccountEntry(id, title, kind, video))
                        } else unsupportedRows++
                        return
                    }
                    val videoKey = listOf("videoRenderer", "gridVideoRenderer", "videoWithContextRenderer", "playlistVideoRenderer", "playlistPanelVideoRenderer", "reelItemRenderer").firstOrNull { node[it] is JsonObject }
                    if (kind == AccountEntryKind.VIDEO && (videoKey != null || lockup != null || node["shortsLockupViewModel"] != null)) {
                        // Playlist rows use the same title/byline/thumbnail fields as ordinary video renderers.
                        val source = if (videoKey != null && videoKey != "reelItemRenderer") buildJsonObject { put("videoRenderer", node[videoKey]!!) }
                        else if (lockup != null && text(lockup["contentType"]).isBlank() &&
                            GoogleAccountControls.objects(lockup).any { (it["watchEndpoint"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull == text(lockup["contentId"]) }) {
                            buildJsonObject { put("lockupViewModel", JsonObject(lockup + ("contentType" to JsonPrimitive("LOCKUP_CONTENT_TYPE_VIDEO")))) }
                        } else node
                        val parsed = YouTubeHomeParser.cards(source).videos
                        if (parsed.isEmpty() && (videoKey != null || text(lockup?.get("contentType")) in setOf("", "LOCKUP_CONTENT_TYPE_VIDEO"))) unsupportedRows++
                        parsed.forEach { video ->
                            val proven = if (YouTubeMusicEvidence.fromRenderer(node, video.id) == true) video.copy(music = true) else video
                            rows.putIfAbsent(video.id, AccountEntry(video.id, video.title, kind, proven))
                        }
                        return
                    }
                    for (key in listOf("messageRenderer", "backgroundPromoRenderer", "feedNudgeRenderer")) (node[key] as? JsonObject)?.let { msg ->
                        listOf("title", "text", "bodyText", "body", "subtitle").map { text(msg[it]) }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }?.let(messages::add)
                    }
                    node.filterKeys { it !in setOf("secondaryContents", "sidebar", "header", "menu", "navigationEndpoint") }.forEach { (key, value) -> walk(value, depth + 1, key == "tabRenderer") }
                }
                else -> Unit
            }
        }
        walk(primary)
        return AccountLibraryPage(rows.values.toList(), token, unsupportedRows = unsupportedRows, message = messages.joinToString("\n").ifBlank {
            if (rows.isEmpty()) "YouTube returned no supported items for this account list. It may be empty or its layout may be unsupported; local items were not substituted." else ""
        }.takeIf { it.isNotBlank() })
    }
}


internal object YouTubeMusicCategory {
    fun parse(player: JsonObject, expectedId: String): Boolean? {
        val details = player["videoDetails"] as? JsonObject ?: return null
        if (details["videoId"]?.jsonPrimitive?.contentOrNull != expectedId) return null
        val renderer = (player["microformat"] as? JsonObject)?.get("playerMicroformatRenderer") as? JsonObject ?: return null
        val category = renderer["category"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return MusicPlaybackPolicy.isMusicCategory(category)
    }
}

/** Exact-ID music watch configuration is source proof, unlike titles/channel names. */
internal object YouTubeMusicEvidence {
    fun fromRenderer(node: JsonElement, expectedId: String): Boolean? {
        for (part in GoogleAccountControls.objects(node)) {
            val watch = part["watchEndpoint"] as? JsonObject ?: continue
            if (watch["videoId"]?.jsonPrimitive?.contentOrNull != expectedId) continue
            val supported = watch["watchEndpointMusicSupportedConfigs"] as? JsonObject ?: continue
            val config = supported["watchEndpointMusicConfig"] as? JsonObject ?: continue
            if (config["musicVideoType"]?.jsonPrimitive?.contentOrNull in setOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV")) return true
        }
        return null
    }
}

/** Only the native Liked Songs playlist shelf, never recommendations or all-video likes. */
internal object YouTubeLikedSongs {
    fun parse(root: JsonObject, continuation: Boolean = false): AccountLibraryPage {
        if (root.containsKey("error") || ((root["responseContext"] as? JsonObject)?.get("mainAppWebResponseContext") as? JsonObject)
                ?.get("loggedOut")?.jsonPrimitive?.booleanOrNull == true) throw IOException("YouTube Music account request rejected")
        val primary = GoogleAccountControls.objects(root["contents"] ?: root["continuationContents"] ?: JsonObject(emptyMap()))
            .mapNotNull { (it["musicPlaylistShelfRenderer"] ?: it["musicPlaylistShelfContinuation"]) as? JsonObject }.firstOrNull()
            ?: if (continuation) GoogleAccountControls.objects(root["onResponseReceivedActions"] ?: root["onResponseReceivedEndpoints"] ?: JsonObject(emptyMap()))
                .mapNotNull { it["appendContinuationItemsAction"] as? JsonObject }.firstOrNull()
                ?.let { buildJsonObject { put("contents", it["continuationItems"] ?: JsonArray(emptyList())) } }
                ?: throw IOException("YouTube Music did not return more liked songs")
            else throw IOException("YouTube Music did not return its Liked Songs shelf")
        val id = primary["playlistId"]?.jsonPrimitive?.contentOrNull
        if (id != null && id != "LM") throw IOException("A different music playlist was returned")
        val page = YouTubeAccountLibraryParser.parse(primary, AccountEntryKind.VIDEO)
        return page.copy(items = page.items.map { row -> row.copy(video = row.video?.copy(music = true)) })
    }
}
