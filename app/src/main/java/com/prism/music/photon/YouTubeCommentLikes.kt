package com.prism.music.photon

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.io.IOException

/** Uses only Google's own comment-bound action; never fabricates an action token or local like. */
internal class YouTubeCommentLikes(client: OkHttpClient = OkHttpClient()) {
    private val transport = YouTubeWebTransport(client)
    suspend fun like(videoId: String, commentId: String, session: YouTubeWebSession, permitted: () -> Boolean): Boolean {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")) && commentId.length in 1..512)
        check(permitted())
        val html = transport.html("/watch?v=$videoId", session)
        var root = YouTubeWebBootstrap.assigned(html, "ytInitialData")
        if (!YouTubeWebBootstrap.signedIn(root, html)) throw IOException("Sign in with Google to like comments")
        val context = YouTubeWebBootstrap.context(html) ?: throw IOException("Comment controls unavailable")
        val seen = mutableSetOf<String>()
        repeat(12) {
            check(permitted())
            control(root, commentId)?.let { control ->
                if (control.liked) return true
                val response = transport.post("comment/perform_comment_action", context,
                    buildJsonObject { putJsonArray("actions") { add(control.action) } }, session, permitted)
                check(permitted())
                val results = response["actionResults"] as? JsonArray
                if (results?.isNotEmpty() != true || results.any { (it as? JsonObject)?.get("status")?.jsonPrimitive?.content != "STATUS_SUCCEEDED" })
                    throw IOException("Comment like was not confirmed. Check YouTube before retrying.")
                return true
            }
            // Only the comment section's continuation, not recommended video/feed continuations.
            val section = GoogleAccountControls.objects(root).firstOrNull {
                (it["itemSectionRenderer"] as? JsonObject)?.let { section ->
                    section["sectionIdentifier"]?.jsonPrimitive?.contentOrNull == "comment-item-section" || section["targetId"]?.jsonPrimitive?.contentOrNull == "comments-section"
                } == true
            }?.get("itemSectionRenderer") ?: root.takeIf { it.containsKey("onResponseReceivedEndpoints") }
            val token = section?.let { GoogleAccountControls.objects(it).mapNotNull { node ->
                val endpoint = (node["continuationEndpoint"] as? JsonObject)?.get("continuationCommand") as? JsonObject
                endpoint?.get("token")?.jsonPrimitive?.contentOrNull
            }.firstOrNull { value -> value !in seen && value.length in 1..32768 } }
                ?: throw IOException("YouTube didn't expose a like control for this comment")
            seen += token
            root = transport.post("next", context, buildJsonObject { put("continuation", token) }, session, permitted)
        }
        throw IOException("This comment's like control wasn't available in the returned pages")
    }
    internal data class Control(val action: String, val liked: Boolean)
    internal companion object {
        fun control(root: JsonObject, commentId: String): Control? {
            val modern = GoogleAccountControls.objects(root).mapNotNull { it["commentViewModel"] as? JsonObject }
                .firstOrNull { it["commentId"]?.jsonPrimitive?.contentOrNull == commentId }
            if (modern != null) {
                val surfaceKey = modern["toolbarSurfaceKey"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
                val stateKey = modern["toolbarStateKey"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
                val state = GoogleAccountControls.objects(root).mapNotNull { it["engagementToolbarStateEntityPayload"] as? JsonObject }
                    .firstOrNull { it["key"]?.jsonPrimitive?.contentOrNull == stateKey } ?: return null
                val likeState = state["likeState"]?.jsonPrimitive?.contentOrNull ?: return null
                if (likeState == "TOOLBAR_LIKE_STATE_LIKED") return Control("", true)
                if (likeState !in setOf("TOOLBAR_LIKE_STATE_INDIFFERENT", "TOOLBAR_LIKE_STATE_UNLIKED", "TOOLBAR_LIKE_STATE_DISLIKED")) return null
                val mutation = GoogleAccountControls.objects(root).firstOrNull { it["entityKey"]?.jsonPrimitive?.contentOrNull == surfaceKey } ?: return null
                val surface = (mutation["payload"] as? JsonObject)?.get("engagementToolbarSurfaceEntityPayload") as? JsonObject ?: return null
                val command = surface["likeCommand"] ?: return null
                val endpoint = GoogleAccountControls.objects(command).mapNotNull { it["performCommentActionEndpoint"] as? JsonObject }.firstOrNull() ?: return null
                val token = endpoint["action"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length in 1..32768 } ?: return null
                return Control(token, false)
            }
            val comment = GoogleAccountControls.objects(root).mapNotNull { it["commentRenderer"] as? JsonObject }
                .firstOrNull { it["commentId"]?.jsonPrimitive?.contentOrNull == commentId } ?: return null
            val button = (((comment["actionButtons"] as? JsonObject)?.get("commentActionButtonsRenderer") as? JsonObject)
                ?.get("likeButton") as? JsonObject)?.get("toggleButtonRenderer") as? JsonObject ?: return null
            val liked = button["isToggled"]?.jsonPrimitive?.booleanOrNull ?: return null
            if (liked) return Control("", true)
            val endpoint = ((button["defaultServiceEndpoint"] as? JsonObject)?.get("performCommentActionEndpoint") as? JsonObject)
                ?: return null
            val token = endpoint["action"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length in 1..32768 } ?: return null
            return Control(token, false)
        }
    }
}
