package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.io.IOException

enum class GoogleChangeKind { LIKE, SUBSCRIBE, CREATE_PLAYLIST, ADD_VIDEO, REMOVE_VIDEO, DELETE_PLAYLIST }
data class GoogleChange(val kind: GoogleChangeKind, val id: String, val title: String, val video: Video? = null, val channel: Channel? = null, val desired: Boolean? = null)
data class GoogleChangeState(val pending: GoogleChange? = null, val revision: Long = -1, val busy: Boolean = false, val message: String? = null)
data class GoogleChangeResult(val message: String, val liked: Boolean? = null, val subscribed: Boolean? = null)

/** Structured controls only; never decides Google state from a local cache or an English button label. */
internal object GoogleAccountControls {
    fun objects(node: JsonElement, depth: Int = 0): Sequence<JsonObject> = sequence {
        if (depth > 70) return@sequence
        when (node) {
            is JsonObject -> { yield(node); for (value in node.values) yieldAll(objects(value, depth + 1)) }
            is JsonArray -> for (value in node) yieldAll(objects(value, depth + 1))
            else -> Unit
        }
    }
    fun liked(root: JsonObject, id: String): Boolean? {
        val states = mutableSetOf<Boolean>()
        for (node in objects(root)) {
            (node["likeButtonRenderer"] as? JsonObject)?.let { button ->
                if ((button["target"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull == id)
                    status(button["likeStatus"])?.let(states::add)
            }
            (node["likeButtonViewModel"] as? JsonObject)?.let { button ->
                if (objects(button).any { (it["target"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull == id })
                    ((button["likeStatusEntity"] as? JsonObject)?.get("likeStatus"))?.let { status(it)?.let(states::add) }
            }
            (node["videoPrimaryInfoRenderer"] as? JsonObject)?.let { primary ->
                for (part in objects(primary)) (part["likeButtonViewModel"] as? JsonObject)?.let { button ->
                    status((button["likeStatusEntity"] as? JsonObject)?.get("likeStatus"))?.let(states::add)
                }
            }
        }
        return states.singleOrNull()
    }
    private fun status(value: JsonElement?): Boolean? = when ((value as? JsonPrimitive)?.contentOrNull) {
        "LIKE" -> true
        "DISLIKE", "INDIFFERENT" -> false
        else -> null
    }
    fun subscribed(root: JsonObject, channelId: String): Boolean? {
        val states = mutableSetOf<Boolean>()
        for (node in objects(root)) for (key in listOf("subscribeButtonRenderer", "subscriptionStateEntity")) {
            val button = node[key] as? JsonObject ?: continue
            if (button["channelId"]?.jsonPrimitive?.contentOrNull == channelId)
                button["subscribed"]?.jsonPrimitive?.booleanOrNull?.let(states::add)
        }
        return states.singleOrNull()
    }
    fun playlistRow(root: JsonObject, videoId: String): JsonObject? = objects(root).mapNotNull { it["playlistVideoRenderer"] as? JsonObject }
        .firstOrNull { it["videoId"]?.jsonPrimitive?.contentOrNull == videoId }
}

internal class YouTubeAccountActionsApi(client: OkHttpClient = OkHttpClient()) {
    private val transport = YouTubeWebTransport(client)
    private data class Document(val root: JsonObject, val context: JsonObject, val html: String)
    private suspend fun read(path: String, session: YouTubeWebSession): Document {
        val html = transport.html(path, session)
        val root = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(root, html) || root.containsKey("error")) throw IOException("Could not verify the Google account")
        if (path.startsWith("/watch?v=")) {
            val player = YouTubeWebBootstrap.assigned(html, "ytInitialPlayerResponse")
            if ((player["videoDetails"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull != path.substringAfter("/watch?v=")) throw IOException("Mismatched account watch response")
        }
        val context = YouTubeWebBootstrap.context(html) ?: throw IOException("Missing account context")
        return Document(root, context, html)
    }
    suspend fun likeState(id: String, session: YouTubeWebSession): Boolean? = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        GoogleAccountControls.liked(read("/watch?v=$id", session).root, id)
    }
    suspend fun execute(change: GoogleChange, session: YouTubeWebSession, permitted: () -> Boolean): GoogleChangeResult = withContext(Dispatchers.IO) {
        fun checkPermission() { if (!permitted()) throw IOException("Account changed or request was revoked") }
        suspend fun send(endpoint: String, context: JsonObject, body: JsonObject): JsonObject {
            checkPermission()
            val result = transport.post(endpoint, context, body, session, permitted)
            if (result.containsKey("error") || result["status"]?.jsonPrimitive?.contentOrNull?.let { it != "STATUS_SUCCEEDED" } == true)
                throw IOException("YouTube did not confirm the account change")
            return result
        }
        suspend fun verify(path: String, predicate: (JsonObject) -> Boolean): Boolean {
            repeat(3) { attempt ->
                checkPermission()
                if (predicate(read(path, session).root)) return true
                if (attempt < 2) delay(1000L)
            }
            return false
        }
        checkPermission()
        when (change.kind) {
            GoogleChangeKind.LIKE -> {
                require(change.id.matches(Regex("[A-Za-z0-9_-]{11}")))
                val path = "/watch?v=${change.id}"
                val before = read(path, session)
                val player = YouTubeWebBootstrap.assigned(before.html, "ytInitialPlayerResponse")
                require((player["videoDetails"] as? JsonObject)?.get("videoId")?.jsonPrimitive?.contentOrNull == change.id)
                val old = GoogleAccountControls.liked(before.root, change.id) ?: throw IOException("YouTube's current like state could not be read; no change sent")
                val desired = change.desired ?: !old
                if (old == desired) return@withContext GoogleChangeResult("YouTube already has this like state; verified without another write.", liked = desired)
                send(if (desired) "like/like" else "like/removelike", before.context, buildJsonObject { putJsonObject("target") { put("videoId", change.id) } })
                checkPermission()
                if (!verify(path) { GoogleAccountControls.liked(it, change.id) == desired }) throw IOException("Like change could not be verified; check YouTube before retrying")
                GoogleChangeResult(if (desired) "Liked on YouTube, verified by a fresh read." else "Like removed on YouTube, verified by a fresh read.", liked = desired)
            }
            GoogleChangeKind.SUBSCRIBE -> {
                require(change.id.matches(Regex("UC[A-Za-z0-9_-]{20,30}")))
                val path = "/channel/${change.id}"
                val before = read(path, session)
                val old = GoogleAccountControls.subscribed(before.root, change.id) ?: throw IOException("YouTube's subscription state could not be read; no change sent")
                val desired = change.desired ?: !old
                if (old == desired) return@withContext GoogleChangeResult("YouTube already has this subscription state; verified without another write.", subscribed = desired)
                send(if (desired) "subscription/subscribe" else "subscription/unsubscribe", before.context, buildJsonObject { putJsonArray("channelIds") { add(change.id) } })
                checkPermission()
                if (!verify(path) { GoogleAccountControls.subscribed(it, change.id) == desired }) throw IOException("Subscription change could not be verified; check YouTube before retrying")
                GoogleChangeResult(if (desired) "Subscribed on YouTube, verified by a fresh read." else "Unsubscribed on YouTube, verified by a fresh read.", subscribed = desired)
            }
            GoogleChangeKind.CREATE_PLAYLIST -> {
                require(change.title.isNotBlank() && change.title.length <= 150)
                val home = read("/", session)
                val video = change.video
                require(video == null || video.id.matches(Regex("[A-Za-z0-9_-]{11}")))
                val response = send("playlist/create", home.context, buildJsonObject {
                    put("title", change.title); put("privacyStatus", "PRIVATE")
                    if (video != null) putJsonArray("videoIds") { add(video.id) }
                })
                val id = response["playlistId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{2,150}")) }
                    ?: throw IOException("Playlist creation was not confirmed; check YouTube before retrying")
                checkPermission()
                val created = read("/playlist?list=$id", session)
                if (video != null && GoogleAccountControls.playlistRow(created.root, video.id) == null) throw IOException("Created playlist contents could not be verified; check YouTube before retrying")
                GoogleChangeResult("YouTube returned a new private playlist ID and the playlist was read back. Refresh Playlists to see it.")
            }
            GoogleChangeKind.ADD_VIDEO, GoogleChangeKind.REMOVE_VIDEO -> {
                require(change.id.matches(Regex("[A-Za-z0-9_-]{2,150}")) && change.id !in setOf("LL", "WL"))
                val videoId = change.video?.id ?: throw IOException("Missing selected video")
                require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
                val path = "/playlist?list=${change.id}"
                val before = read(path, session)
                val remove = change.kind == GoogleChangeKind.REMOVE_VIDEO
                val row = GoogleAccountControls.playlistRow(before.root, videoId)
                val setId = (row?.get("setVideoId") ?: row?.get("playlistSetVideoId"))?.jsonPrimitive?.contentOrNull
                if (remove && setId.isNullOrBlank()) throw IOException("The selected playlist entry ID was not returned; no removal sent")
                if (!remove && row != null) return@withContext GoogleChangeResult("This video is already in the returned YouTube playlist; no duplicate was added.")
                send("browse/edit_playlist", before.context, buildJsonObject {
                    put("playlistId", change.id)
                    putJsonArray("actions") { add(buildJsonObject {
                        put("action", if (remove) "ACTION_REMOVE_VIDEO" else "ACTION_ADD_VIDEO")
                        if (remove) put("setVideoId", setId!!) else put("addedVideoId", videoId)
                    }) }
                })
                checkPermission()
                val after = read(path, session)
                val present = GoogleAccountControls.playlistRow(after.root, videoId) != null
                if (present == remove) throw IOException("Playlist edit could not be verified on the returned page; check YouTube before retrying")
                GoogleChangeResult("YouTube playlist ${if (remove) "removal" else "addition"} verified by a fresh read of its returned page.")
            }
            GoogleChangeKind.DELETE_PLAYLIST -> {
                require(change.id.matches(Regex("[A-Za-z0-9_-]{2,150}")) && change.id !in setOf("LL", "WL"))
                val before = read("/playlist?list=${change.id}", session)
                send("playlist/delete", before.context, buildJsonObject { put("playlistId", change.id) })
                // An HTTP success is not proof of deletion. Do not fabricate read-back when Google changes its error layout.
                GoogleChangeResult("YouTube accepted the playlist deletion request. Deletion is not independently verified; check official YouTube before retrying.")
            }
        }
    }
}
