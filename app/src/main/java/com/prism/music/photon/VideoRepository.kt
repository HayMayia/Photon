package com.prism.music.photon

import android.net.Uri
import com.prism.music.data.stream.NewPipeDownloader
import com.prism.music.data.stream.YouTubeStreamInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String = "",
    val duration: Long = 0,
    val views: Long = 0,
    val published: String = "",
) {
    val url get() = "https://www.youtube.com/watch?v=$id"
}

data class VideoQuality(
    val height: Int,
    val url: String,
    val audioUrl: String? = null,
    val mime: String = "video/mp4",
    val downloadable: Boolean = true,
) {
    val label get() = if (height == 0) "Live" else "${height}p"
}

data class VideoDetails(
    val video: Video,
    val description: String,
    val qualities: List<VideoQuality>,
    val related: List<Video>,
    val live: Boolean,
)

data class VideoPage(val videos: List<Video>, val next: Page?, val query: String)

/** The ordinary YouTube service, not YouTube Music. No API key or Photon server. */
class VideoRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    // Prism's range/POST transport also handles current YouTube video streams.
    val streamHttp: OkHttpClient = http.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .addInterceptor(YouTubeStreamInterceptor(http))
        .build()

    init {
        val country = Locale.getDefault().country.ifBlank { "IN" }
        NewPipe.init(NewPipeDownloader(http), Localization("en", country), ContentCountry(country))
    }

    suspend fun search(query: String, next: Page? = null): VideoPage = withContext(Dispatchers.IO) {
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("videos"), "")
        if (next == null) {
            val result = SearchInfo.getInfo(ServiceList.YouTube, handler)
            VideoPage(result.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::preview), result.nextPage, query)
        } else {
            val result = SearchInfo.getMoreItems(ServiceList.YouTube, handler, next)
            VideoPage(result.items.filterIsInstance<StreamInfoItem>().mapNotNull(::preview), result.nextPage, query)
        }
    }

    suspend fun details(id: String): VideoDetails = withContext(Dispatchers.IO) {
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Invalid YouTube video link" }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "audio/mp4" }
            .maxByOrNull { it.averageBitrate }?.content
        val qualities = buildList {
            if (live && !info.hlsUrl.isNullOrBlank()) {
                add(VideoQuality(0, info.hlsUrl, mime = "application/x-mpegURL", downloadable = false))
            }
            info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "video/mp4" }
                .forEach { add(VideoQuality(it.height, it.content)) }
            if (audio != null) info.videoOnlyStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "video/mp4" && (it.codec?.startsWith("avc") != false) }
                .forEach { add(VideoQuality(it.height, it.content, audio)) }
        }.sortedBy { it.audioUrl != null }.distinctBy { it.height }.sortedByDescending { it.height }
        check(qualities.isNotEmpty()) { "YouTube did not provide a compatible stream. It may require sign-in, be restricted, or be temporarily unavailable." }
        VideoDetails(
            Video(id, info.name, info.uploaderName, info.thumbnails.maxByOrNull { it.height }?.url.orEmpty(), info.duration, info.viewCount, info.textualUploadDate.orEmpty()),
            info.description?.content.orEmpty(), qualities,
            info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::preview), live,
        )
    }

    private fun preview(item: StreamInfoItem): Video? {
        val id = YouTubeLinks.videoId(item.url) ?: return null
        return Video(id, item.name, item.uploaderName.orEmpty(), item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
            item.duration, item.viewCount, item.textualUploadDate.orEmpty())
    }
}

/** Explicit host allowlist: a pasted arbitrary URL must never become a download request. */
object YouTubeLinks {
    fun videoId(text: String): String? {
        val candidate = Regex("https?://[^\\s<>]+").find(text.trim())?.value ?: text.trim()
        val uri = Uri.parse(candidate)
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.pathSegments
        val id = when (host) {
            "youtu.be" -> segments.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                uri.path == "/watch" -> uri.getQueryParameter("v")
                segments.firstOrNull() in listOf("shorts", "live", "embed") -> segments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }
}

fun durationLabel(seconds: Long): String {
    if (seconds < 0) return "LIVE"
    val hours = seconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

fun viewLabel(views: Long): String = when {
    views < 0 -> ""
    views >= 1_000_000 -> "%.1fM views".format(views / 1_000_000.0)
    views >= 1000 -> "%.1fK views".format(views / 1000.0)
    else -> "$views views"
}
