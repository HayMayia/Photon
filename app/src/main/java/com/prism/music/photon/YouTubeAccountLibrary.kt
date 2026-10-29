package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.io.IOException

enum class AccountShelf(val label: String, val path: String) {
    HISTORY("History", "/feed/history"), PLAYLISTS("Playlists", "/feed/playlists"),
    LIKED("Liked videos", "/playlist?list=LL"), CHANNELS("Channels", "/feed/channels")
}
enum class AccountEntryKind { VIDEO, PLAYLIST, CHANNEL }
data class AccountEntry(val id: String, val title: String, val kind: AccountEntryKind, val video: Video? = null)
data class AccountLibraryPage(val items: List<AccountEntry>, val continuation: String? = null, val context: JsonObject? = null, val message: String? = null)
data class AccountLibraryState(val shelf: AccountShelf = AccountShelf.HISTORY, val playlist: String? = null, val title: String = "History",
    val page: AccountLibraryPage? = null, val busy: Boolean = false, val moreBusy: Boolean = false, val error: String? = null)

internal class YouTubeAccountLibraryApi(client: OkHttpClient = OkHttpClient()) {
    private val transport = YouTubeWebTransport(client)
    suspend fun load(session: YouTubeWebSession, shelf: AccountShelf, playlist: String? = null): AccountLibraryPage = withContext(Dispatchers.IO) {
        require(playlist == null || playlist.matches(Regex("[A-Za-z0-9_-]{2,150}")))
        val html = transport.html(playlist?.let { "/playlist?list=$it" } ?: shelf.path, session)
        val root = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(root, html)) throw IOException("YouTube did not confirm the account library login")
        val primary = root["contents"] ?: throw IOException("Unrecognized account library page")
        YouTubeAccountLibraryParser.parse(primary, if (playlist != null) AccountEntryKind.VIDEO else when (shelf) {
            AccountShelf.PLAYLISTS -> AccountEntryKind.PLAYLIST
            AccountShelf.CHANNELS -> AccountEntryKind.CHANNEL
            else -> AccountEntryKind.VIDEO
        }).copy(context = YouTubeWebBootstrap.context(html))
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
        fun text(value: JsonElement?): String = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonObject -> text(value["simpleText"] ?: value["content"]).ifBlank {
                (value["runs"] as? JsonArray)?.joinToString("") { text((it as? JsonObject)?.get("text")) }.orEmpty()
            }
            else -> ""
        }
        fun addCollection(item: JsonObject, idKey: String, entryKind: AccountEntryKind) {
            val id = text(item[idKey]); val title = text(item["title"])
            val valid = if (entryKind == AccountEntryKind.CHANNEL) id.matches(Regex("UC[A-Za-z0-9_-]{20,30}")) else id.matches(Regex("[A-Za-z0-9_-]{2,150}"))
            if (kind == entryKind && valid && title.isNotBlank()) rows.putIfAbsent(id, AccountEntry(id, title, entryKind))
        }
        fun walk(node: JsonElement, depth: Int = 0) {
            if (depth > 70 || rows.size >= 250) return
            when (node) {
                is JsonArray -> node.forEach { walk(it, depth + 1) }
                is JsonObject -> {
                    if (node["selected"]?.jsonPrimitive?.booleanOrNull == false) return
                    (node["continuationItemRenderer"] as? JsonObject)?.let {
                        val command = ((it["continuationEndpoint"] as? JsonObject)?.get("continuationCommand") as? JsonObject)
                        text(command?.get("token")).takeIf { it.length in 1..32768 }?.let { value -> token = value }
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
                            addCollection(buildJsonObject { put(if (kind == AccountEntryKind.CHANNEL) "channelId" else "playlistId", text(lockup["contentId"])); put("title", text(metadata?.get("title"))) }, if (kind == AccountEntryKind.CHANNEL) "channelId" else "playlistId", kind)
                        }
                        return
                    }
                    val videoKey = listOf("videoRenderer", "gridVideoRenderer", "videoWithContextRenderer", "playlistVideoRenderer", "playlistPanelVideoRenderer", "reelItemRenderer").firstOrNull { node[it] is JsonObject }
                    if (kind == AccountEntryKind.VIDEO && (videoKey != null || lockup != null || node["shortsLockupViewModel"] != null)) {
                        // Playlist rows use the same title/byline/thumbnail fields as ordinary video renderers.
                        val source = if (videoKey != null && videoKey != "reelItemRenderer") buildJsonObject { put("videoRenderer", node[videoKey]!!) } else node
                        YouTubeHomeParser.cards(source).videos.forEach { video -> rows.putIfAbsent(video.id, AccountEntry(video.id, video.title, kind, video)) }
                        return
                    }
                    for (key in listOf("messageRenderer", "backgroundPromoRenderer", "feedNudgeRenderer")) (node[key] as? JsonObject)?.let { msg ->
                        listOf("title", "text", "bodyText", "body", "subtitle").map { text(msg[it]) }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }?.let(messages::add)
                    }
                    node.filterKeys { it !in setOf("secondaryContents", "sidebar", "header", "menu", "navigationEndpoint") }.values.forEach { walk(it, depth + 1) }
                }
                else -> Unit
            }
        }
        walk(primary)
        return AccountLibraryPage(rows.values.toList(), token, message = messages.joinToString("\n").ifBlank {
            if (rows.isEmpty()) "YouTube returned no supported items for this account list. It may be empty or its layout may be unsupported; local items were not substituted." else ""
        }.takeIf { it.isNotBlank() })
    }
}
