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
    val openedAtMs: Long = 0,
    val webRevision: Long = -1,
)

class PhotonViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PhotonApplication
    val library = app.library.state
    val account = app.account.state
    val youtubeHome = MutableStateFlow(YouTubeHomeState())
    private var youtubeHomeJob: Job? = null
    private var youtubeMoreJob: Job? = null
    private val consumedHomeTokens = mutableSetOf<String>()
    val googleChange = MutableStateFlow(GoogleChangeState())
    val googleLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val googleSubscriptions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private var googleChangeJob: Job? = null
    private fun requestGoogleChange(change: GoogleChange) {
        if (googleChange.value.pending != null || googleChange.value.busy) return
        if (!library.value.youtubeHome || !library.value.youtubeSessionReady) {
            googleChange.value = GoogleChangeState(message = "Sign in with the primary Google button first. This action was not saved locally or sent to another account.")
            return
        }
        googleChange.value = GoogleChangeState(pending = change, revision = library.value.youtubeSessionRevision)
    }
    fun dismissGoogleChange() { if (!googleChange.value.busy) googleChange.value = GoogleChangeState() }
    fun confirmGoogleChange() {
        val pending = googleChange.value
        val change = pending.pending ?: return
        if (pending.busy) return
        googleChange.value = pending.copy(busy = true)
        googleChangeJob = viewModelScope.launch {
            val revision = pending.revision
            fun permitted() = revision == library.value.youtubeSessionRevision && library.value.youtubeHome && library.value.youtubeSessionReady && googleChange.value.busy && googleChange.value.revision == revision
            try {
                check(permitted())
                val session = app.youtubeHistory.session()
                val result = app.accountActions.execute(change, session, ::permitted)
                if (!permitted()) return@launch
                result.liked?.let { googleLikes.value = googleLikes.value + (change.id to it) }
                result.subscribed?.let { googleSubscriptions.value = googleSubscriptions.value + (change.id to it) }
                googleChange.value = GoogleChangeState(message = result.message)
                val shelf = accountLibrary.value
                if (shelf.page != null) openAccountShelf(shelf.shelf, shelf.playlist, shelf.title)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == library.value.youtubeSessionRevision) googleChange.value = GoogleChangeState(message = "This Google change could not be verified. Nothing was saved locally instead, and no automatic retry was made. Check official YouTube before retrying: a request might already have reached it.")
            }
        }
    }
    private fun invalidateGoogleChanges() {
        googleChangeJob?.cancel(); googleChange.value = GoogleChangeState()
        googleLikes.value = emptyMap(); googleSubscriptions.value = emptyMap()
    }
    val accountLibrary = MutableStateFlow(AccountLibraryState())
    private var accountLibraryJob: Job? = null
    private val accountLibraryTokens = mutableSetOf<String>()
    fun openAccountShelf(shelf: AccountShelf, playlist: String? = null, title: String = shelf.label, more: Boolean = false) {
        if (more && (accountLibrary.value.busy || accountLibrary.value.moreBusy)) return
        accountLibraryJob?.cancel()
        val previous = accountLibrary.value
        val oldPage = previous.page
        if (more && (oldPage?.continuation == null || oldPage.continuation in accountLibraryTokens || accountLibraryTokens.size >= 200)) return
        if (!more) accountLibraryTokens.clear()
        accountLibraryJob = viewModelScope.launch {
            val revision = library.value.youtubeSessionRevision
            accountLibrary.value = if (more) previous.copy(moreBusy = true, error = null) else AccountLibraryState(shelf, playlist, title, busy = true)
            try {
                val session = app.youtubeHistory.session()
                val kind = if (playlist != null || shelf in listOf(AccountShelf.HISTORY, AccountShelf.LIKED)) AccountEntryKind.VIDEO else if (shelf == AccountShelf.CHANNELS) AccountEntryKind.CHANNEL else AccountEntryKind.PLAYLIST
                val page = if (more) app.accountLibrary.more(session, oldPage!!, kind) else app.accountLibrary.load(session, shelf, playlist)
                if (revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady) return@launch
                if (more) accountLibraryTokens += oldPage!!.continuation!!
                val items = ((if (more) oldPage!!.items else emptyList()) + page.items).distinctBy { it.kind to it.id }
                accountLibrary.value = AccountLibraryState(shelf, playlist, title, page.copy(items = items, continuation = page.continuation?.takeUnless { it in accountLibraryTokens }))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == library.value.youtubeSessionRevision) accountLibrary.value = if (more) previous.copy(moreBusy = false, error = "More account items could not load. Existing rows are retained; retry explicitly.") else AccountLibraryState(shelf, playlist, title, error = "Could not read this YouTube account list. Retry or check the connected account. No local items were substituted.")
            }
        }
    }
    fun moreAccountShelf() { val state = accountLibrary.value; openAccountShelf(state.shelf, state.playlist, state.title, more = true) }
    private fun invalidateAccountLibrary() {
        accountLibraryJob?.cancel(); accountLibraryTokens.clear(); accountLibrary.value = AccountLibraryState()
    }
    val googleHistory = app.youtubeHistory.state
    fun refreshGoogleHistory() { viewModelScope.launch { app.youtubeHistory.refresh() } }
    fun setGoogleHistoryPaused(paused: Boolean) { viewModelScope.launch { try { app.youtubeHistory.changePause(paused) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { app.youtubeHistory.invalidate() } } }
    fun enableAccountHistory(enabled: Boolean) {
        app.library.update { it.copy(accountHistorySync = enabled, historyPaused = if (enabled) false else it.historyPaused) }
        if (enabled) refreshGoogleHistory()
    }
    fun beginYouTubeWebChange() {
        invalidateAccountLibrary(); invalidateGoogleChanges()
        youtubeHomeJob?.cancel(); youtubeMoreJob?.cancel()
        app.library.update { it.copy(youtubeSessionRevision = it.youtubeSessionRevision + 1, youtubeSessionReady = false, accountHistorySync = false) }
        app.youtubeHistory.invalidate(); youtubeHome.value = YouTubeHomeState(error = "Finish reconnecting the web account. History reporting is off.")
    }
    fun moreYouTubeHome() {
        val previous = youtubeHome.value
        val page = previous.page ?: return
        val token = page.continuation ?: return
        if (previous.busy || previous.loadingMore || youtubeMoreJob?.isActive == true || !library.value.youtubeSessionReady) return
        if (token in consumedHomeTokens || consumedHomeTokens.size >= 200) {
            youtubeHome.value = previous.copy(page = page.copy(continuation = null), error = "YouTube repeated a continuation or reached the session limit. Refresh Home.")
            return
        }
        youtubeMoreJob = viewModelScope.launch {
            val revision = library.value.youtubeSessionRevision
            youtubeHome.value = previous.copy(loadingMore = true, error = null)
            try {
                val session = app.youtubeHistory.session()
                val result = app.youtubeHome.more(session, page)
                if (revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady) return@launch
                consumedHomeTokens += token
                val merged = (page.videos + result.videos).distinctBy { it.id }
                val next = result.continuation?.takeUnless { it in consumedHomeTokens }
                youtubeHome.value = previous.copy(page = page.copy(videos = merged, continuation = next), loadingMore = false,
                    error = null, autoMore = merged.size > page.videos.size)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == library.value.youtubeSessionRevision) youtubeHome.value = previous.copy(loadingMore = false, autoMore = false, error = "More recommendations could not load. Your existing Home cards are kept. Tap Load more to retry explicitly.") }
        }
    }
    fun pauseHistory(paused: Boolean) {
        app.library.update { it.copy(historyPaused = paused) }
        if (paused) { searchJob?.cancel(); shelfJob?.cancel(); shelves.value = emptyList(); mutableBrowse.value = BrowseState() }
        else { home(); if (!library.value.youtubeHome) refreshShelves() }
    }
    fun connectYouTubeHome(accountIndex: Int) {
        // Enter account Home before fetching: a failure must be visible, not leave the old local feed on screen.
        app.library.update { it.copy(youtubeHome = true, youtubeWebUser = accountIndex, youtubeSessionReady = false) }
        refreshYouTubeHome(accountIndex, connecting = true)
    }
    fun refreshYouTubeHome(accountIndex: Int = library.value.youtubeWebUser, connecting: Boolean = false) {
        youtubeHomeJob?.cancel(); youtubeMoreJob?.cancel(); consumedHomeTokens.clear()
        youtubeHomeJob = viewModelScope.launch {
            youtubeHome.value = YouTubeHomeState(busy = true)
            try {
                val revision = library.value.youtubeSessionRevision
                check(connecting || library.value.youtubeSessionReady)
                val cookies = android.webkit.CookieManager.getInstance()
                val page = app.youtubeHome.home(YouTubeWebSession(accountIndex, revision,
                    cookies.getCookie("https://www.youtube.com").orEmpty(), cookies.getCookie("https://s.youtube.com").orEmpty()))
                if (revision != library.value.youtubeSessionRevision) return@launch
                if (connecting) {
                    searchJob?.cancel(); shelfJob?.cancel(); shelves.value = emptyList()
                    app.library.update { it.copy(youtubeHome = true, youtubeWebUser = accountIndex, youtubeSessionReady = true,
                        youtubeSessionRevision = it.youtubeSessionRevision + 1, accountHistorySync = false) }
                }
                youtubeHome.value = YouTubeHomeState(page = page)
                refreshGoogleHistory()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { youtubeHome.value = YouTubeHomeState(error = "Could not load a verified signed-in YouTube Home. Review the web session/consent and try again. No local or search recommendations were substituted.") }
        }
    }
    fun disconnectYouTubeHome() {
        invalidateAccountLibrary(); invalidateGoogleChanges()
        youtubeHomeJob?.cancel(); youtubeMoreJob?.cancel(); consumedHomeTokens.clear()
        app.library.update { it.copy(youtubeHome = false, youtubeWebUser = 0, youtubeSessionReady = false,
            youtubeSessionRevision = it.youtubeSessionRevision + 1, accountHistorySync = false) }
        app.youtubeHistory.invalidate()
        youtubeHome.value = YouTubeHomeState()
        android.webkit.CookieManager.getInstance().removeAllCookies { android.webkit.CookieManager.getInstance().flush() }
        android.webkit.WebStorage.getInstance().deleteAllData()
        // Do not automatically replace a disconnected/paused feed with generated recommendations.
        shelves.value = emptyList()
        mutableBrowse.value = BrowseState()
    }
    val commentPosting = MutableStateFlow(false)
    val commentPostError = MutableStateFlow<String?>(null)
    val lastPublished = MutableStateFlow<PublishedComment?>(null)
    fun disconnectAccount() { if (!commentPosting.value) app.account.disconnect() }
    fun postComment(video: Video, text: String) {
        if (commentPosting.value) return
        commentPosting.value = true
        commentPostError.value = null
        viewModelScope.launch {
            try { lastPublished.value = app.account.post(video.id, text) }
            catch (cancelled: CancellationException) { commentPostError.value = "Posting was interrupted. Check YouTube before retrying to avoid a duplicate."; throw cancelled }
            catch (error: Exception) { commentPostError.value = error.message ?: "Posting could not be confirmed. Check YouTube before retrying." }
            finally { commentPosting.value = false }
        }
    }
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
        if (library.value.youtubeHome || library.value.historyPaused) return
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

    init { home(); if (!library.value.youtubeHome && !library.value.historyPaused) refreshShelves() }

    fun home() {
        if (library.value.youtubeHome) { refreshYouTubeHome(); return }
        if (library.value.historyPaused) {
            searchJob?.cancel(); mutableBrowse.value = BrowseState(personalized = true, error = "Local history is paused. Generated recommendations are paused too; search remains available."); return
        }
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

    fun retryPlayback(video: Video) { app.videos.invalidateDetails(video.id); open(video) }

    fun open(video: Video, fromQueue: Boolean = false) {
        val opened = android.os.SystemClock.elapsedRealtime()
        val revision = library.value.youtubeSessionRevision
        PhotonDiagnostics.event("Open video requested")
        if (!fromQueue) queue.value = emptyList()
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            mutableWatch.value = WatchState(video = video, busy = true)
            try {
                val details = app.videos.details(video.id)
                val max = library.value.defaultHeight
                val quality = details.qualities.firstOrNull { it.height <= max } ?: details.qualities.last()
                mutableWatch.value = WatchState(details.video, details, quality, request = System.nanoTime(), startPositionMs = library.value.progress[video.id]?.resumeMs() ?: 0, openedAtMs = opened, webRevision = revision)
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
    fun save(video: Video) = requestGoogleChange(GoogleChange(GoogleChangeKind.LIKE, video.id, video.title, video, desired = googleLikes.value[video.id] != true))
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
    fun subscribe(channel: Channel) {
        val id = android.net.Uri.parse(channel.url).pathSegments.let { if (it.size == 2 && it[0] == "channel") it[1] else "" }
        requestGoogleChange(GoogleChange(GoogleChangeKind.SUBSCRIBE, id, channel.name, channel = channel, desired = googleSubscriptions.value[id] != true))
    }
    fun createGooglePlaylist(name: String, video: Video? = null) = requestGoogleChange(GoogleChange(GoogleChangeKind.CREATE_PLAYLIST, "", name, video))
    fun addToPlaylist(id: String, video: Video) = requestGoogleChange(GoogleChange(GoogleChangeKind.ADD_VIDEO, id, video.title, video))
    fun removeFromPlaylist(id: String, video: Video) = requestGoogleChange(GoogleChange(GoogleChangeKind.REMOVE_VIDEO, id, video.title, video))
    fun deletePlaylist(id: String, title: String) = requestGoogleChange(GoogleChange(GoogleChangeKind.DELETE_PLAYLIST, id, title))
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
