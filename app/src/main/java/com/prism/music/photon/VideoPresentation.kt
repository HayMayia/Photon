package com.prism.music.photon

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jsoup.Jsoup
import kotlinx.serialization.json.*

/** Classification uses YouTube's canonical Shorts route, never duration or vertical dimensions. */
internal object ShortsPresentation {
    fun merge(source: Video, metadata: Video?): Video = if (metadata == null) source else source.copy(
        short = source.short || metadata.short,
        thumbnail = if (metadata.short) metadata.thumbnail.ifBlank { source.thumbnail } else source.thumbnail,
        channelAvatar = source.channelAvatar.ifBlank { metadata.channelAvatar })
    private fun shortsRoute(url: String, id: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.path == "/shorts/$id" && (url.startsWith("/") && !url.startsWith("//") ||
            uri.scheme == "https" && uri.host in setOf("www.youtube.com", "youtube.com") && uri.userInfo == null)
    }.getOrDefault(false)
    fun fromHtml(id: String, html: String): Pair<Boolean, String>? {
        val doc = Jsoup.parse(html.substringBefore("</head>") + "</head>")
        // Shorts often canonicalize to /watch. The matching reel command is stronger evidence.
        val initial = runCatching { YouTubeWebBootstrap.assigned(html, "ytInitialData") }.getOrNull()
        val reel = initial?.let { data -> GoogleAccountControls.objects(data).any { node ->
            ((node["reelWatchEndpoint"] as? JsonObject)?.get("videoId") as? JsonPrimitive)?.contentOrNull == id ||
                shortsRoute(((node["webCommandMetadata"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull.orEmpty(), id)
        } } == true
        val player = runCatching { YouTubeWebBootstrap.assigned(html, "ytInitialPlayerResponse") }.getOrNull()
        val details = player?.get("videoDetails") as? JsonObject
        val explicit = (details?.get("videoId") as? JsonPrimitive)?.contentOrNull == id &&
            listOf("isShort", "isShorts").any { (details?.get(it) as? JsonPrimitive)?.booleanOrNull == true }
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
        val image = safeArtwork(doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty())
        if (reel || explicit) return true to image
        val uri = runCatching { java.net.URI(canonical) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host !in setOf("www.youtube.com", "youtube.com") || uri.userInfo != null) return null
        val short = when {
            uri.path == "/shorts/$id" -> true
            uri.path == "/watch" && uri.rawQuery?.split('&')?.contains("v=$id") == true -> false
            else -> return null
        }
        return short to image
    }
    fun safeArtwork(url: String): String {
        val normalized = if (url.startsWith("//")) "https:$url" else url
        return normalized.takeIf { runCatching {
            val uri = java.net.URI(it)
            uri.scheme == "https" && uri.port in setOf(-1, 443) && uri.userInfo == null &&
                uri.host in setOf("i.ytimg.com", "img.youtube.com", "yt3.ggpht.com", "yt3.googleusercontent.com")
        }.getOrDefault(false) }.orEmpty()
    }
}

@Composable
internal fun resolvedVideo(source: Video): Video {
    val model: PhotonViewModel = viewModel()
    val metadata by model.videoPresentation.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    LaunchedEffect(source.id, library.youtubeSessionRevision, library.youtubeSessionReady) { model.resolveVideoPresentation(source) }
    return ShortsPresentation.merge(source, metadata[source.id])
}

@Composable
internal fun VideoHeart(video: Video) {
    val model: PhotonViewModel = viewModel()
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    LaunchedEffect(video.id, library.youtubeSessionRevision) { model.resolveVideoPresentation(video) }
    androidx.compose.material3.IconButton(onClick = { model.save(video) }) {
        androidx.compose.material3.Icon(if (likes[video.id] == true) com.prism.music.ui.theme.PrismIcons.HeartFilled else com.prism.music.ui.theme.PrismIcons.Heart,
            if (likes[video.id] == true) "Remove like on YouTube" else "Like on YouTube",
            tint = if (likes[video.id] == true) androidx.compose.ui.graphics.Color(0xFFFF3355) else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
