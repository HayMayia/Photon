package com.prism.music.photon

import android.net.Uri
import com.prism.music.data.stream.NewPipeDownloader
import com.prism.music.data.stream.YouTubeStreamInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
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
    val channelUrl: String = "",
    val short: Boolean = false,
) {
    val url get() = "https://www.youtube.com/watch?v=$id"
}

data class VideoQuality(
    val height: Int,
    val url: String,
    val audioUrl: String? = null,
    val mime: String = "video/mp4",
    val downloadable: Boolean = true,
    val audioLabel: String = "Source default (language unverified)",
) {
    val label get() = if (height == 0) "Live" else "${height}p"
}

data class VideoDetails(
    val video: Video,
    val description: String,
    val qualities: List<VideoQuality>,
    val related: List<Video>,
    val live: Boolean,
    val channel: Channel? = null,
)

data class VideoPage(val videos: List<Video>, val next: Page?, val query: String)

/** The ordinary YouTube service, not YouTube Music. No API key or Photon server. */
class VideoRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    val artworkHttp: OkHttpClient = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
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
            VideoPage(result.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::preview), result.nextPage?.takeIf { Page.isValid(it) }, query)
        } else {
            val result = SearchInfo.getMoreItems(ServiceList.YouTube, handler, next)
            VideoPage(result.items.filterIsInstance<StreamInfoItem>().mapNotNull(::preview), result.nextPage?.takeIf { Page.isValid(it) }, query)
        }
    }

    suspend fun details(id: String): VideoDetails = withContext(Dispatchers.IO) {
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Invalid YouTube video link" }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val compatibleAudio = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "audio/mp4"
        }
        val index = OriginalAudioPolicy.pick(compatibleAudio.map { OriginalAudioPolicy.Track(it.audioTrackType?.toString(), it.averageBitrate) })
        val selectedAudio = index?.let { compatibleAudio[it] }
        val audio = selectedAudio?.content
        val original = selectedAudio?.audioTrackType?.toString() == "ORIGINAL"
        if (!live && audio == null && info.audioStreams.any { it.audioTrackType != null }) {
            error("No compatible original/unlabelled soundtrack is available. Photon will not automatically substitute a dubbed language.")
        }
        val audioLabel = if (original) "Original audio" else "Source default (language unverified)"
        val qualities = buildList {
            if (live && !info.hlsUrl.isNullOrBlank()) {
                add(VideoQuality(0, info.hlsUrl, mime = "application/x-mpegURL", downloadable = false))
            }
            info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "video/mp4" }
                .forEach { add(VideoQuality(it.height, it.content, audio, audioLabel = audioLabel)) }
            if (audio != null) info.videoOnlyStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format?.mimeType == "video/mp4" && (it.codec?.startsWith("avc") != false) }
                .forEach { add(VideoQuality(it.height, it.content, audio, audioLabel = audioLabel)) }
        }.distinctBy { it.height }.sortedByDescending { it.height }
        check(qualities.isNotEmpty()) { "YouTube did not provide a compatible stream. It may require sign-in, be restricted, or be temporarily unavailable." }
        VideoDetails(
            Video(id, info.name, info.uploaderName, info.thumbnails.maxByOrNull { it.height }?.url.orEmpty(), info.duration, info.viewCount, info.textualUploadDate.orEmpty(), info.uploaderUrl.orEmpty()),
            info.description?.content.orEmpty(), qualities,
            info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::preview).distinctBy { it.id }, live,
            info.uploaderUrl?.takeIf { it.isNotBlank() }?.let { Channel(it, info.uploaderName, info.uploaderAvatars.maxByOrNull { image -> image.height }?.url.orEmpty()) },
        )
    }

    suspend fun channel(url: String, next: Page? = null, shorts: Boolean = false): CollectionPage = withContext(Dispatchers.IO) {
        require(YouTubeLinks.channelUrl(url) != null) { "Invalid YouTube channel link" }
        val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(ServiceList.YouTube, url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(if (shorts) "shorts" else "videos") }
            ?: error(if (shorts) "This channel has no Shorts tab" else "This channel has no Videos tab")
        val channel = Channel(info.url, info.name, info.avatars.maxByOrNull { it.height }?.url.orEmpty(), info.subscriberCount)
        if (next == null) {
            val result = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(ServiceList.YouTube, tab)
            CollectionPage(info.name, info.description.orEmpty(), result.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::preview).distinctBy { it.id }, result.nextPage?.takeIf { Page.isValid(it) }, channel)
        } else {
            val result = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getMoreItems(ServiceList.YouTube, tab, next)
            CollectionPage(info.name, info.description.orEmpty(), result.items.filterIsInstance<StreamInfoItem>().mapNotNull(::preview).distinctBy { it.id }, result.nextPage?.takeIf { Page.isValid(it) }, channel)
        }
    }

    suspend fun playlist(url: String, next: Page? = null): CollectionPage = withContext(Dispatchers.IO) {
        val link = YouTubeLinks.playlistUrl(url) ?: error("Invalid YouTube playlist link")
        if (next == null) {
            val result = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(ServiceList.YouTube, link)
            CollectionPage(result.name, result.uploaderName.orEmpty(), result.relatedItems.mapNotNull(::preview).distinctBy { it.id }, result.nextPage?.takeIf { Page.isValid(it) })
        } else {
            val result = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(ServiceList.YouTube, link, next)
            CollectionPage("Playlist", "", result.items.mapNotNull(::preview).distinctBy { it.id }, result.nextPage?.takeIf { Page.isValid(it) })
        }
    }

    suspend fun comments(video: Video, next: Page? = null): CommentPage = withContext(Dispatchers.IO) {
        fun convert(item: org.schabi.newpipe.extractor.comments.CommentsInfoItem) = VideoComment(item.commentId?.takeIf { it.isNotBlank() } ?: "${item.uploaderName}:${item.commentText.content.hashCode()}", item.uploaderName.orEmpty(), item.commentText.content.orEmpty(), item.likeCount, item.replyCount, item.replies)
        if (next == null) {
            val result = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(ServiceList.YouTube, video.url) ?: error("Comments unavailable")
            CommentPage(result.relatedItems.map(::convert), result.nextPage?.takeIf { Page.isValid(it) }, result.isCommentsDisabled)
        } else {
            val result = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(ServiceList.YouTube, video.url, next)
            CommentPage(result.items.map(::convert), result.nextPage?.takeIf { Page.isValid(it) })
        }
    }

    suspend fun recommendations(library: PhotonLibrary): List<Video> = withContext(Dispatchers.IO) {
        val seeds = library.history.sortedByDescending { (library.progress[it.id]?.watchedMs ?: 0).coerceAtMost(600_000) + (if (library.saved.any { saved -> saved.id == it.id }) 120_000 else 0) }
            .take(3)
        val candidates = mutableListOf<Video>()
        for (seed in seeds) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try { candidates += details(seed.id).related } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { /* Another seed can still provide recommendations. */ }
        }
        RecommendationRank.rank(candidates, library)
    }

    private fun preview(item: StreamInfoItem): Video? {
        val id = YouTubeLinks.videoId(item.url) ?: return null
        return Video(id, item.name, item.uploaderName.orEmpty(), item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
            item.duration, item.viewCount, item.textualUploadDate.orEmpty(), item.uploaderUrl.orEmpty(), item.isShortFormContent)
    }
}

/** Explicit host allowlist: a pasted arbitrary URL must never become a download request. */
data class CollectionPage(val title: String, val description: String, val videos: List<Video>, val next: Page?, val channel: Channel? = null)
data class VideoComment(val id: String, val author: String, val text: String, val likes: Int, val replies: Int, val replyPage: Page?)
data class CommentPage(val comments: List<VideoComment>, val next: Page?, val disabled: Boolean = false)

object RecommendationRank {
    fun rank(candidates: List<Video>, library: PhotonLibrary): List<Video> {
        val frequencies = candidates.groupingBy { it.id }.eachCount()
        val seen = library.history.map { it.id }.toSet()
        val subscriptions = library.subscriptions.map { it.url }.toSet()
        val channels = mutableMapOf<String, Int>()
        return candidates.distinctBy { it.id }.filterNot { it.id in seen }
            .sortedByDescending { frequencies.getValue(it.id) * 10 + if (it.channelUrl in subscriptions) 5 else 0 }
            .filter { video ->
                val key = video.channelUrl.ifBlank { video.channel }
                val count = channels.getOrDefault(key, 0)
                channels[key] = count + 1
                count < 3
            }.take(40)
    }
}

object YouTubeLinks {
    private fun trusted(text: String): Uri? = Uri.parse(text.trim()).takeIf { it.scheme in listOf("https", "http") && it.host?.lowercase() in listOf("youtube.com", "www.youtube.com", "m.youtube.com") }
    fun channelUrl(text: String): String? {
        val uri = trusted(text) ?: return null
        val parts = uri.pathSegments
        return if ((parts.firstOrNull()?.startsWith("@") == true && parts.first().length > 1) || (parts.firstOrNull() in listOf("channel", "c", "user") && parts.size >= 2))
            "https://www.youtube.com/" + parts.take(if (parts.first().startsWith("@")) 1 else 2).joinToString("/") else null
    }
    fun playlistUrl(text: String): String? {
        val uri = trusted(text) ?: return null
        val id = uri.getQueryParameter("list")?.takeIf { Regex("[A-Za-z0-9_-]{2,200}").matches(it) } ?: return null
        return "https://www.youtube.com/playlist?list=$id"
    }

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


/** Language identity takes priority over bitrate. Never choose an explicitly dubbed/descriptive track automatically. */
object OriginalAudioPolicy {
    data class Track(val type: String?, val bitrate: Int)
    fun pick(tracks: List<Track>): Int? {
        val original = tracks.indices.filter { tracks[it].type == "ORIGINAL" }
        val unlabelled = tracks.indices.filter { tracks[it].type == null }
        return original.ifEmpty { unlabelled }.maxByOrNull { tracks[it].bitrate }
    }
}
