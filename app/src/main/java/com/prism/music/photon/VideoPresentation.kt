package com.prism.music.photon

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jsoup.Jsoup

/** Classification uses YouTube's canonical Shorts route, never duration or vertical dimensions. */
internal object ShortsPresentation {
    fun fromHtml(id: String, html: String): Pair<Boolean, String>? {
        val doc = Jsoup.parse(html)
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
        val uri = runCatching { java.net.URI(canonical) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host !in setOf("www.youtube.com", "youtube.com") || uri.userInfo != null) return null
        val short = when {
            uri.path == "/shorts/$id" -> true
            uri.path == "/watch" && uri.rawQuery?.split('&')?.contains("v=$id") == true -> false
            else -> return null
        }
        val image = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        return short to safeArtwork(image)
    }
    fun safeArtwork(url: String): String = url.takeIf { runCatching {
        val uri = java.net.URI(it)
        uri.scheme == "https" && uri.port in setOf(-1, 443) && uri.userInfo == null &&
            (uri.host in setOf("i.ytimg.com", "img.youtube.com", "yt3.ggpht.com", "yt3.googleusercontent.com"))
    }.getOrDefault(false) }.orEmpty()
}

@Composable
internal fun resolvedVideo(source: Video): Video {
    val model: PhotonViewModel = viewModel()
    val metadata by model.videoPresentation.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    LaunchedEffect(source.id, library.youtubeSessionRevision, library.youtubeSessionReady) { model.resolveVideoPresentation(source) }
    return metadata[source.id]?.let { source.copy(short = source.short || it.short, thumbnail = it.thumbnail.ifBlank { source.thumbnail }) } ?: source
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
