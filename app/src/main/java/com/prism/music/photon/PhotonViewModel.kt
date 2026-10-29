package com.prism.music.photon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** UI state lives outside the Activity, so rotating does not start playback or searches again. */
data class BrowseState(
    val videos: List<Video> = emptyList(),
    val busy: Boolean = false,
    val moreBusy: Boolean = false,
    val error: String? = null,
    val query: String = "science and technology",
    val hasMore: Boolean = false,
    val personalized: Boolean = false,
)

data class WatchState(
    val video: Video? = null,
    val details: VideoDetails? = null,
    val quality: VideoQuality? = null,
    val localPath: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val request: Long = 0,
    val preservePosition: Boolean = false,
    val startPositionMs: Long = 0,
)

class PhotonViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PhotonApplication
    val library = app.library.state
    val work = WorkManager.getInstance(app).getWorkInfosByTagFlow(VideoDownloadWorker.TAG)
    private val mutableBrowse = MutableStateFlow(BrowseState())
    val browse = mutableBrowse.asStateFlow()
    private val mutableWatch = MutableStateFlow(WatchState())
    val watch = mutableWatch.asStateFlow()
    val collection = MutableStateFlow<CollectionPage?>(null)
    val collectionBusy = MutableStateFlow(false)
    val collectionError = MutableStateFlow<String?>(null)
    val comments = MutableStateFlow<CommentPage?>(null)
    val commentsBusy = MutableStateFlow(false)
    val commentsError = MutableStateFlow<String?>(null)
    val queue = MutableStateFlow<List<Video>>(emptyList())
    val shelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    private var shelfJob: Job? = null

    fun refreshShelves(force: Boolean = false) {
        if (!force && shelves.value.isNotEmpty()) return
        shelfJob?.cancel()
        shelves.value = HomeCatalog.sections.map { HomeShelf(it) }
        shelfJob = viewModelScope.launch {
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            kotlinx.coroutines.coroutineScope {
                HomeCatalog.sections.forEach { section -> launch {
                    semaphore.acquire()
                    try {
                        val videos = app.videos.search(section.query).videos.take(12)
                        shelves.value = shelves.value.map { if (it.section.id == section.id) it.copy(videos = videos, busy = false) else it }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { shelves.value = shelves.value.map { if (it.section.id == section.id) it.copy(busy = false, error = error.message ?: "Could not load YouTube") else it } }
                    finally { semaphore.release() }
                } }
            }
        }
    }
    private var collectionUrl = ""
    private var collectionIsChannel = false
    private var collectionShorts = false
    private var collectionJob: Job? = null
    private var commentsJob: Job? = null
    private var commentVideo: Video? = null
    private var page: VideoPage? = null
    private var searchJob: Job? = null
    private var watchJob: Job? = null

    init { home(); refreshShelves() }

    fun home() {
        if (library.value.history.isEmpty()) { search("science and technology"); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page = null
            mutableBrowse.value = BrowseState(busy = true, query = "For you", personalized = true)
            try {
                val videos = app.videos.recommendations(library.value)
                mutableBrowse.value = BrowseState(videos = videos, query = "For you", personalized = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableBrowse.value = BrowseState(query = "For you", personalized = true, error = error.message ?: "Recommendations unavailable") }
        }
    }

    fun subscriptionsFeed() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page = null
            mutableBrowse.value = BrowseState(busy = true, query = "Subscriptions")
            val videos = mutableListOf<Video>()
            val failures = mutableListOf<String>()
            for (channel in library.value.subscriptions.take(20)) {
                try { videos += app.videos.channel(channel.url).videos.take(8) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failures += channel.name }
            }
            mutableBrowse.value = BrowseState(videos = videos.distinctBy { it.id }, query = "Subscriptions",
                error = if (failures.isEmpty()) null else "Could not refresh: ${failures.joinToString()}")
        }
    }

    fun shorts() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page = null
            mutableBrowse.value = BrowseState(busy = true, query = "Shorts")
            try {
                val result = app.videos.search("shorts")
                page = result
                val videos = result.videos.filter { it.short }.toMutableList()
                val sources = (library.value.subscriptions.map { it.url } + library.value.history.map { it.channelUrl } + result.videos.map { it.channelUrl })
                    .filter { it.isNotBlank() }.distinct().take(3)
                for (url in sources) {
                    try { videos += app.videos.channel(url, shorts = true).videos.map { it.copy(short = true) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* A creator may have no Shorts tab. */ }
                }
                mutableBrowse.value = BrowseState(videos.distinctBy { it.id }, query = "Shorts", hasMore = result.next != null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableBrowse.value = BrowseState(query = "Shorts", error = error.message ?: "Shorts unavailable") }
        }
    }

    fun search(query: String) {
        val text = query.trim().take(200)
        if (text.isEmpty()) return
        YouTubeLinks.playlistUrl(text)?.let { openCollection(it, false); return }
        YouTubeLinks.channelUrl(text)?.let { openCollection(it, true); return }
        YouTubeLinks.videoId(text)?.let { openId(it); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page = null
            mutableBrowse.value = BrowseState(busy = true, query = text)
            try {
                val result = app.videos.search(text)
                page = result
                mutableBrowse.value = BrowseState(result.videos.distinctBy { it.id }, query = text, hasMore = result.next != null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableBrowse.value = BrowseState(query = text, error = error.message ?: "Could not load YouTube. Check your connection and try again.") }
        }
    }

    fun loadMore() {
        val current = page ?: return
        val next = current.next ?: return
        if (mutableBrowse.value.moreBusy || mutableBrowse.value.busy) return
        searchJob = viewModelScope.launch {
            mutableBrowse.value = mutableBrowse.value.copy(moreBusy = true, error = null)
            try {
                val result = app.videos.search(current.query, next)
                page = result
                mutableBrowse.value = mutableBrowse.value.copy(videos = (mutableBrowse.value.videos + if (mutableBrowse.value.query == "Shorts") result.videos.filter { it.short } else result.videos).distinctBy { it.id }, hasMore = result.next != null, moreBusy = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableBrowse.value = mutableBrowse.value.copy(moreBusy = false, error = error.message ?: "Could not load more videos") }
        }
    }

    fun openId(id: String) = open(Video(id, "Opening video…", "YouTube"))

    fun open(video: Video, fromQueue: Boolean = false) {
        if (!fromQueue) queue.value = emptyList()
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            mutableWatch.value = WatchState(video = video, busy = true)
            try {
                val details = app.videos.details(video.id)
                val max = library.value.defaultHeight
                val quality = details.qualities.firstOrNull { it.height <= max } ?: details.qualities.last()
                mutableWatch.value = WatchState(details.video, details, quality, request = System.nanoTime(), startPositionMs = library.value.progress[video.id]?.resumeMs() ?: 0)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableWatch.value = WatchState(video = video, error = error.message ?: "This video could not be played") }
        }
    }

    fun offline(download: SavedDownload) {
        watchJob?.cancel()
        if (!File(download.path).isFile) {
            mutableWatch.value = WatchState(video = download.video, error = "The downloaded file is missing. Remove this entry and download it again.")
            return
        }
        mutableWatch.value = WatchState(video = download.video, localPath = download.path, request = System.nanoTime(), startPositionMs = library.value.progress[download.video.id]?.resumeMs() ?: 0)
    }

    fun quality(quality: VideoQuality) {
        mutableWatch.value = mutableWatch.value.copy(quality = quality, request = System.nanoTime(), preservePosition = true)
    }

    fun adoptPlaying(video: Video) {
        if (mutableWatch.value.video == null) mutableWatch.value = WatchState(video = video)
    }

    fun close() { watchJob?.cancel(); mutableWatch.value = WatchState() }
    fun save(video: Video) = app.library.toggleSaved(video)
    fun download(video: Video, quality: VideoQuality) = VideoDownloadWorker.enqueue(app, video, quality.height, library.value.wifiOnly)
    fun cancel(id: java.util.UUID) { WorkManager.getInstance(app).cancelWorkById(id) }
    fun dismissFinishedDownloads() { WorkManager.getInstance(app).pruneWork() }
    fun remove(download: SavedDownload) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(download.path)
            if (!file.exists() || file.delete()) {
                download.thumbnailPath?.let { File(it).delete() }
                app.library.update { it.copy(downloads = it.downloads.filterNot { d -> d.path == download.path }) }
            }
        }
    }
    fun repairArtwork() = OfflineArtwork.schedule(app, library.value.wifiOnly)
    fun accent(value: Int) = app.library.update { it.copy(accent = value) }
    fun dark(value: Boolean) = app.library.update { it.copy(dark = value) }
    fun wifi(value: Boolean) = app.library.update { it.copy(wifiOnly = value) }
    fun maxHeight(value: Int) = app.library.update { it.copy(defaultHeight = value) }
    fun clearHistory() = app.library.update { it.copy(history = emptyList(), progress = emptyMap()) }
    fun subscribe(channel: Channel) = app.library.subscribe(channel)
    fun createPlaylist(name: String): String = app.library.createPlaylist(name)
    fun addToPlaylist(id: String, video: Video) = app.library.playlistVideo(id, video)
    fun removeFromPlaylist(id: String, video: Video) = app.library.playlistVideo(id, video, true)
    fun deletePlaylist(id: String) = app.library.deletePlaylist(id)
    fun playAll(videos: List<Video>) {
        if (videos.isEmpty()) return
        queue.value = videos
        open(videos.first(), true)
    }
    fun next() {
        val index = queue.value.indexOfFirst { it.id == watch.value.video?.id }
        if (index >= 0) queue.value.getOrNull(index + 1)?.let { open(it, true) }
    }
    fun closeCollection() { collectionJob?.cancel(); collection.value = null; collectionBusy.value = false; collectionError.value = null }
    fun openCollection(url: String, channel: Boolean, shorts: Boolean = false, more: Boolean = false) {
        if (more && collectionBusy.value) return
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            val previous = if (more) collection.value else null
            collectionUrl = url; collectionIsChannel = channel; collectionShorts = shorts
            if (!more) collection.value = CollectionPage("Loading…", "", emptyList(), null)
            collectionBusy.value = true; collectionError.value = null
            try {
                val result = if (channel) app.videos.channel(url, previous?.next, shorts) else app.videos.playlist(url, previous?.next)
                collection.value = if (previous == null) result else previous.copy(videos = (previous.videos + result.videos).distinctBy { it.id }, next = result.next)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { collectionError.value = error.message ?: "Could not load this page" }
            finally { if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) collectionBusy.value = false }
        }
    }
    fun moreCollection() { if (collection.value?.next != null) openCollection(collectionUrl, collectionIsChannel, collectionShorts, true) }
    fun channelTab(shorts: Boolean) = openCollection(collectionUrl, true, shorts)
    fun loadComments(video: Video, more: Boolean = false, replies: org.schabi.newpipe.extractor.Page? = null) {
        if (more && commentsBusy.value) return
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            val previous = if (more && commentVideo?.id == video.id) comments.value else null
            commentVideo = video
            if (!more) comments.value = null
            commentsBusy.value = true; commentsError.value = null
            try {
                val result = app.videos.comments(video, replies ?: previous?.next)
                comments.value = if (previous == null) result else result.copy(comments = (previous.comments + result.comments).distinctBy { it.id })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { commentsError.value = error.message ?: "Comments unavailable" }
            finally { if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) commentsBusy.value = false }
        }
    }
}
