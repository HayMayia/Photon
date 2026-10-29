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
)

class PhotonViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PhotonApplication
    val library = app.library.state
    val work = WorkManager.getInstance(app).getWorkInfosByTagFlow(VideoDownloadWorker.TAG)
    private val mutableBrowse = MutableStateFlow(BrowseState())
    val browse = mutableBrowse.asStateFlow()
    private val mutableWatch = MutableStateFlow(WatchState())
    val watch = mutableWatch.asStateFlow()
    private var page: VideoPage? = null
    private var searchJob: Job? = null
    private var watchJob: Job? = null

    init { search("science and technology") }

    fun search(query: String) {
        val text = query.trim().take(200)
        if (text.isEmpty()) return
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
                mutableBrowse.value = mutableBrowse.value.copy(videos = (mutableBrowse.value.videos + result.videos).distinctBy { it.id }, hasMore = result.next != null, moreBusy = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableBrowse.value = mutableBrowse.value.copy(moreBusy = false, error = error.message ?: "Could not load more videos") }
        }
    }

    fun openId(id: String) = open(Video(id, "Opening video…", "YouTube"))

    fun open(video: Video) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            mutableWatch.value = WatchState(video = video, busy = true)
            try {
                val details = app.videos.details(video.id)
                val max = library.value.defaultHeight
                val quality = details.qualities.firstOrNull { it.height <= max } ?: details.qualities.last()
                app.library.watched(details.video)
                mutableWatch.value = WatchState(details.video, details, quality, request = System.nanoTime())
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
        app.library.watched(download.video)
        mutableWatch.value = WatchState(video = download.video, localPath = download.path, request = System.nanoTime())
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
    fun remove(download: SavedDownload) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(download.path)
            if (!file.exists() || file.delete()) app.library.update { it.copy(downloads = it.downloads.filterNot { d -> d.path == download.path }) }
        }
    }
    fun dark(value: Boolean) = app.library.update { it.copy(dark = value) }
    fun wifi(value: Boolean) = app.library.update { it.copy(wifiOnly = value) }
    fun maxHeight(value: Int) = app.library.update { it.copy(defaultHeight = value) }
    fun clearHistory() = app.library.update { it.copy(history = emptyList()) }
}
