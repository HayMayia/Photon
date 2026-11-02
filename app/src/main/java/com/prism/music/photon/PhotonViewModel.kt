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
import kotlinx.coroutines.sync.withPermit
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
    val channelArtwork = MutableStateFlow<Map<String, String>>(emptyMap())
    private val avatarPending = mutableSetOf<String>()
    fun loadChannelArtwork(id: String) {
        if (id in channelArtwork.value || !avatarPending.add(id)) return
        viewModelScope.launch {
            try {
                val image = app.videos.channelArtwork(id)
                if (image.isNotBlank()) channelArtwork.value = (channelArtwork.value + (id to image)).entries.toList().takeLast(256).associate { it.toPair() }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* A visible initial remains when artwork cannot load. */ }
            finally { avatarPending.remove(id) }
        }
    }
    val videoPresentation = MutableStateFlow<Map<String, Video>>(emptyMap())
    private val presentationRevisions = mutableMapOf<String, Long>()
    private val presentationPending = mutableSetOf<String>()
    private val likesPending = mutableSetOf<String>()
    private val metadataReadSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val likeReadSlots = kotlinx.coroutines.sync.Semaphore(2)
    fun resolveVideoPresentation(video: Video, includeLike: Boolean = true) {
        val requestedRevision = library.value.youtubeSessionRevision
        val presentationKey = "$requestedRevision:${video.id}"
        if ((video.id !in videoPresentation.value || (videoPresentation.value[video.id]?.short != true && presentationRevisions[video.id] != requestedRevision)) && presentationPending.add(presentationKey)) viewModelScope.launch {
            try {
                val resolved = metadataReadSlots.withPermit {
                    if (video.short) video else {
                        // One authoritative account read first; do not serially probe both routes for every normal row.
                        val account = if (requestedRevision == library.value.youtubeSessionRevision && library.value.youtubeHome && library.value.youtubeSessionReady) {
                            try { app.accountActions.presentation(video.id, app.youtubeHistory.session()) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        } else null
                        if (account != null) video.copy(short = account.first,
                            thumbnail = if (account.first) account.second.ifBlank { video.thumbnail } else video.thumbnail)
                        else app.videos.presentation(video)
                    }
                } ?: return@launch
                if (requestedRevision != library.value.youtubeSessionRevision) return@launch
                presentationRevisions[video.id] = requestedRevision
                if (resolved.short && library.value.downloads.any { it.video.id == video.id && !it.video.short }) app.library.update { stored ->
                    stored.copy(downloads = stored.downloads.map { download -> if (download.video.id == video.id)
                        download.copy(video = ShortsPresentation.merge(download.video, resolved)) else download })
                }
                val current = mutableWatch.value
                if (current.video?.id == video.id) mutableWatch.value = current.copy(video = current.video.copy(
                    short = current.video.short || resolved.short, thumbnail = if (resolved.short) resolved.thumbnail else current.video.thumbnail))
                // Retain positive presentation on the dataset even if the global 512-item LRU evicts it.
                val shelf = accountLibrary.value
                shelf.page?.let { page -> accountLibrary.value = shelf.copy(page = page.copy(items = AccountLibraryRows.metadata(page.items, resolved))) }
                val activeCollection = collection.value
                if (activeCollection != null) collection.value = activeCollection.copy(videos = activeCollection.videos.map { if (it.id == resolved.id) ShortsPresentation.merge(it, resolved) else it })
                val feed = mutableBrowse.value
                if (feed.query == "Google subscriptions") mutableBrowse.value = feed.copy(videos = feed.videos.map {
                    if (it.id == resolved.id) ShortsPresentation.merge(it, resolved) else it
                })
                videoPresentation.value = (videoPresentation.value + (video.id to resolved)).entries.toList().takeLast(512).associate { it.toPair() }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Unknown stays unknown; no duration guesses. */ }
            finally { presentationPending.remove(presentationKey) }
        }
        if (!includeLike) return
        val revision = library.value.youtubeSessionRevision
        val key = "$revision:${video.id}"
        val previousLike = googleLikes.value[video.id]
        if (library.value.youtubeHome && library.value.youtubeSessionReady && video.id !in googleLikes.value && likesPending.add(key)) viewModelScope.launch {
            try {
                val value = likeReadSlots.withPermit {
                    if (revision != library.value.youtubeSessionRevision) return@withPermit null
                    app.accountActions.likeState(video.id, app.youtubeHistory.session())
                }
                if (value != null && googleLikes.value[video.id] == previousLike && revision == library.value.youtubeSessionRevision && library.value.youtubeHome && library.value.youtubeSessionReady)
                    googleLikes.value = googleLikes.value + (video.id to value)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* No fabricated unliked state on failure. */ }
            finally { likesPending.remove(key) }
        }
    }
    private var likesIndexRevision: Long? = null
    private var likesIndexJob: Job? = null
    fun loadGoogleLikedIndex(force: Boolean = false) {
        val current = library.value
        if (!current.youtubeHome || !current.youtubeSessionReady || (!force && likesIndexRevision == current.youtubeSessionRevision)) return
        likesIndexRevision = current.youtubeSessionRevision
        likesIndexJob?.cancel()
        likesIndexJob = viewModelScope.launch {
            val revision = current.youtubeSessionRevision
            fun active() = library.value.youtubeHome && library.value.youtubeSessionReady && library.value.youtubeSessionRevision == revision
            try {
                val session = app.youtubeHistory.session()
                var page = app.accountLibrary.load(session, AccountShelf.LIKED)
                val tokens = mutableSetOf<String>()
                repeat(200) {
                    if (!active()) return@launch
                    val known = googleLikes.value
                    googleLikes.value = known + page.items.mapNotNull { it.video?.id }.filter { it !in known }.associateWith { true }
                    val token = page.continuation ?: return@launch
                    if (!tokens.add(token)) return@launch
                    page = app.accountLibrary.more(session, page, AccountEntryKind.VIDEO)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active()) likesIndexRevision = null /* Retry explicitly or when reconnecting; never infer false. */ }
        }
    }
    val googleLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val googleSubscriptions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private var subscriptionStateGeneration = 0L
    private var googleChangeJob: Job? = null
    private fun requestGoogleChange(change: GoogleChange) {
        if (googleChange.value.pending != null || googleChange.value.busy) return
        if (!library.value.youtubeHome || !library.value.youtubeSessionReady) {
            googleChange.value = GoogleChangeState(message = "Sign in with the primary Google button first. This action was not saved locally or sent to another account.")
            return
        }
        if (change.kind == GoogleChangeKind.SUBSCRIBE) {
            ++subscriptionStateGeneration
            subscriptionChannelsJob?.cancel()
            subscriptionChannels.value = subscriptionChannels.value.copy(busy = false)
        }
        googleChange.value = GoogleChangeState(pending = change, revision = library.value.youtubeSessionRevision)
        confirmGoogleChange()
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
                result.liked?.let { liked ->
                    googleLikes.value = googleLikes.value + (change.id to liked)
                    change.video?.let { updateLikedMusicLike(it, liked, revision) }
                }
                result.subscribed?.let { googleSubscriptions.value = googleSubscriptions.value + (change.id to it); loadSubscriptionChannels() }
                googleChange.value = GoogleChangeState()
                if (change.kind !in listOf(GoogleChangeKind.LIKE, GoogleChangeKind.SUBSCRIBE)) accountPlaylistCache.clear()
                val shelf = accountLibrary.value
                if (shelf.shelf != AccountShelf.LIKED_MUSIC && shelf.page != null)
                    openAccountShelf(shelf.shelf, shelf.playlist, shelf.title, force = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == library.value.youtubeSessionRevision) googleChange.value = GoogleChangeState(message = "This Google change could not be verified. Nothing was saved locally instead, and no automatic retry was made. Check official YouTube before retrying: a request might already have reached it.")
            }
        }
    }
    private fun invalidateGoogleChanges() {
        if (mutableBrowse.value.query == "Google subscriptions") {
            searchJob?.cancel()
            mutableBrowse.value = BrowseState(query = "Google subscriptions", error = "Account changed. Reload the Google subscriptions feed for the current account.")
        }
        ++subscriptionStateGeneration
        subscriptionChannelsJob?.cancel(); subscriptionChannels.value = AccountLibraryState(shelf = AccountShelf.CHANNELS, title = "Subscribed channels")
        googleChangeJob?.cancel(); googleChange.value = GoogleChangeState()
        likesIndexJob?.cancel(); likesIndexRevision = null
        videoPresentation.value = emptyMap(); presentationRevisions.clear()
        googleLikes.value = emptyMap(); googleSubscriptions.value = emptyMap()
    }
    val accountLibrary = MutableStateFlow(AccountLibraryState())
    private var accountLibraryJob: Job? = null
    private val accountLibraryTokens = mutableSetOf<String>()
    private var accountLibraryGeneration = 0L
    private val accountPlaylistCache = object : LinkedHashMap<String, Pair<Long, AccountLibraryPage>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, AccountLibraryPage>>?) = size > 8
    }
    fun openAccountShelf(shelf: AccountShelf, playlist: String? = null, title: String = shelf.label, more: Boolean = false, force: Boolean = false) {
        if (shelf == AccountShelf.LIKED_MUSIC && playlist == null) { openLikedMusic(); return }
        if (more && (accountLibrary.value.busy || accountLibrary.value.moreBusy)) return
        accountLibraryJob?.cancel()
        val previous = accountLibrary.value
        val oldPage = previous.page
        if (more && (oldPage?.continuation == null || oldPage.continuation in accountLibraryTokens || accountLibraryTokens.size >= 200)) return
        if (!more) accountLibraryTokens.clear()
        val generation = if (more) previous.generation else ++accountLibraryGeneration
        val playlistIdentity = playlist ?: if (shelf == AccountShelf.LIKED) "LL" else null
        val cacheKey = playlistIdentity?.let { PlaylistQueuePolicy.key(library.value.youtubeSessionRevision, it) }
        val cached = cacheKey?.let { accountPlaylistCache[it] }
        if (!more && !force && cached != null && System.currentTimeMillis() - cached.first < 5 * 60_000L) {
            accountLibrary.value = AccountLibraryState(shelf, playlist, title, cached.second, generation = generation)
            return
        }
        accountLibraryJob = viewModelScope.launch {
            val revision = library.value.youtubeSessionRevision
            accountLibrary.value = if (more) previous.copy(moreBusy = true, error = null) else AccountLibraryState(shelf, playlist, title, page = cached?.second, busy = cached == null, moreBusy = cached != null, generation = generation)
            try {
                val session = app.youtubeHistory.session()
                val kind = if (playlist != null || shelf in listOf(AccountShelf.HISTORY, AccountShelf.LIKED)) AccountEntryKind.VIDEO else if (shelf == AccountShelf.CHANNELS) AccountEntryKind.CHANNEL else AccountEntryKind.PLAYLIST
                val page = if (more) app.accountLibrary.more(session, oldPage!!, kind) else app.accountLibrary.load(session, shelf, playlist)
                if (revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady || generation != accountLibraryGeneration) return@launch
                // Admit account rows without waiting for optional metadata. Existing cards never change span.
                // Incoming order is preserved even when later metadata requests finish first.
                val existingKeys = if (more) accountLibrary.value.page?.items.orEmpty().map(AccountLibraryRows::key).toSet() else emptySet()
                if (!more) accountLibrary.value = accountLibrary.value.copy(page = null)
                val incoming = page.items.distinctBy(AccountLibraryRows::key).filter { AccountLibraryRows.key(it) !in existingKeys }
                for (batch in incoming.chunked(6)) {
                    val prepared = AccountLibraryAdmission.prepare(batch) { video ->
                        // Cached proof is optional. Network metadata resolves after cards are visible.
                        val cached = videoPresentation.value[video.id]
                            ?.takeIf { it.short || presentationRevisions[video.id] == revision }
                            ?: throw java.io.IOException("Format not cached")
                        ShortsPresentation.merge(video, cached)
                    }
                    if (revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady || generation != accountLibraryGeneration) return@launch
                    val items = AccountLibraryRows.append(accountLibrary.value.page?.items.orEmpty(), prepared)
                    if (shelf == AccountShelf.LIKED && playlist == null) googleLikes.value = googleLikes.value + prepared.mapNotNull { it.video?.id }.filter { googleLikes.value[it] != false }.associateWith { true }
                    accountLibrary.value = AccountLibraryState(shelf, playlist, title,
                        page.copy(items = items, continuation = if (more) oldPage?.continuation else null),
                        busy = !more, moreBusy = more, generation = generation)
                }
                if (shelf == AccountShelf.CHANNELS) googleSubscriptions.value = googleSubscriptions.value + accountLibrary.value.page?.items.orEmpty().filter { it.kind == AccountEntryKind.CHANNEL }.associate { it.id to true }
                if (more) accountLibraryTokens += oldPage!!.continuation!!
                accountLibrary.value = AccountLibraryState(shelf, playlist, title,
                    page.copy(items = accountLibrary.value.page?.items.orEmpty(), continuation = page.continuation?.takeUnless { it in accountLibraryTokens }), generation = generation)
                if (cacheKey != null) accountLibrary.value.page?.let { accountPlaylistCache[cacheKey] = System.currentTimeMillis() to it }
                if (playlistIdentity != null && playlistSource.value == PlaylistQueuePolicy.key(revision, playlistIdentity)) {
                    // A tap during incremental admission must not freeze a partially admitted first page.
                    queue.value = PlaylistQueuePolicy.append(queue.value, accountLibrary.value.page?.items.orEmpty().mapNotNull { it.video })
                    queuedAccountPage = accountLibrary.value.page
                    playlistMore.value = queuedAccountPage?.continuation != null
                }
                page.items.mapNotNull { it.video }.take(6).forEach { resolveVideoPresentation(it, includeLike = false) }

            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == library.value.youtubeSessionRevision && generation == accountLibraryGeneration) accountLibrary.value = if (more) accountLibrary.value.copy(moreBusy = false, error = "More account items could not load. Existing rows are retained; retry explicitly.") else accountLibrary.value.copy(busy = false, error = "Could not load this account list. Refresh to retry.")
            }
        }
    }
    fun commitAccountLibraryFormats(generation: Long) {
        val state = accountLibrary.value
        val page = state.page ?: return
        if (state.generation != generation || !AccountLibraryRows.needsFormatCommit(page.items)) return
        accountLibrary.value = state.copy(page = page.copy(items = AccountLibraryRows.commitFormats(page.items)))
    }
    fun openLikedMusic(force: Boolean = false) {
        accountLibraryJob?.cancel()
        val generation = ++accountLibraryGeneration
        val revision = library.value.youtubeSessionRevision
        val cached = app.likedMusicCache.snapshot.takeIf { it.revision == revision }
        val snapshot = likedMusicSnapshot?.takeIf { likedMusicSnapshotRevision == revision } ?: cached?.videos
        val rows = snapshot.orEmpty().map { AccountEntry(it.id, it.title, AccountEntryKind.VIDEO, it) }
        googleLikes.value += rows.map { it.id }.filter { googleLikes.value[it] != false }.associateWith { true }
        accountLibrary.value = AccountLibraryState(AccountShelf.LIKED_MUSIC, title = LikedMusicPlaylist.NAME,
            page = snapshot?.let { AccountLibraryPage(rows, message = if (likedMusicJob?.isActive == true) "Updating Liked Music…"
                else cached?.takeIf { it.unresolved > 0 }?.let { "Some liked music is still being checked." }) }, busy = snapshot == null, generation = generation)
        if (force || LikedMusicFreshness.needsRefresh(cached, System.currentTimeMillis())) scanLikedMusic()
    }

    private val likedMusicProof = ExpiringVideoCache<Boolean>(4096, 6 * 60 * 60_000L)
    private var likedMusicSnapshot: List<Video>? = null
    private var likedMusicSnapshotRevision = -1L
    private var likedMusicJob: Job? = null
    private var likedMusicEpoch = 0L
    private var likedMusicMutationSequence = 0L
    private val likedMusicMutations = mutableMapOf<String, LikedMusicSnapshotUpdates.Change>()
    private fun scanLikedMusic() {
        if (likedMusicJob?.isActive == true) return
        val epoch = ++likedMusicEpoch
        val mutationStart = likedMusicMutationSequence
        val revision = library.value.youtubeSessionRevision
        likedMusicJob = viewModelScope.launch {
            fun active() = epoch == likedMusicEpoch && revision == library.value.youtubeSessionRevision && library.value.youtubeSessionReady && library.value.youtubeHome
            val old = app.likedMusicCache.snapshot.takeIf { it.revision == revision }?.videos.orEmpty()
            var found = emptyList<AccountEntry>()
            var unresolved = 0
            var scanned = 0
            var complete = false
            fun publish(done: Boolean, error: String? = null) {
                if (!active()) return
                val fresh = found.mapNotNull { it.video }.filter { googleLikes.value[it.id] != false }
                val base = if (done && unresolved == 0) fresh else (old + fresh).distinctBy { it.id }.filter { googleLikes.value[it.id] != false }
                val videos = LikedMusicSnapshotUpdates.merge(base, likedMusicMutations.values.toList(), mutationStart)
                likedMusicSnapshot = videos; likedMusicSnapshotRevision = revision
                if (playlistSource.value == PlaylistQueuePolicy.key(revision, "liked-music")) queue.value = videos
                if (done || error != null) {
                    app.likedMusicCache.save(LikedMusicSnapshot(revision, videos, System.currentTimeMillis(),
                        schemaVersion = 1, scanComplete = done && unresolved == 0, unresolved = unresolved))
                    if (done) likedMusicMutations.entries.removeAll { it.value.sequence <= mutationStart }
                }
                if (accountLibrary.value.shelf == AccountShelf.LIKED_MUSIC && accountLibrary.value.playlist == null) {
                    accountLibrary.value = accountLibrary.value.copy(busy = false, moreBusy = false, error = error,
                        page = AccountLibraryPage(videos.map { AccountEntry(it.id, it.title, AccountEntryKind.VIDEO, it) },
                            message = if (done) LikedMusicRows.message(false, unresolved, scanned) else if (error == null) "Updating Liked Music · $scanned checked" else null))
                }
            }
            try {
                check(active())
                val session = app.youtubeHistory.session()
                var page = app.accountLibrary.load(session, AccountShelf.LIKED_MUSIC)
                val tokens = mutableSetOf<String>()
                val seen = mutableSetOf<String>()
                for (index in 0 until 200) {
                    if (!active()) return@launch
                    unresolved += page.unsupportedRows
                    for (batch in page.items.filter { it.kind == AccountEntryKind.VIDEO && seen.add(it.id) }.chunked(2)) {
                        val classified = LikedMusicRows.classify(batch) { video ->
                            if (old.any { it.id == video.id }) true else {
                                val key = "$revision:${video.id}"
                                likedMusicProof.get(key) ?: app.accountLibrary.musicCategory(session, video)?.also { likedMusicProof.put(key, it) }
                            }
                        }
                        if (!active()) return@launch
                        unresolved += classified.unresolved; scanned += batch.size
                        found = AccountLibraryRows.append(found, classified.items)
                        publish(false)
                    }
                    val token = page.continuation
                    if (token == null) { complete = true; break }
                    check(tokens.add(token))
                    page = app.accountLibrary.more(session, page, AccountEntryKind.VIDEO)
                }
                check(complete)
                publish(true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publish(false, "Couldn't refresh Liked Music. Your loaded playlist is kept; Refresh to retry.") }
        }
    }
    private fun updateLikedMusicLike(video: Video, liked: Boolean, revision: Long) {
        viewModelScope.launch {
            val known = app.likedMusicCache.snapshot.takeIf { it.revision == revision }?.videos.orEmpty()
            val music = if (!liked) false else if (video.music || known.any { it.id == video.id }) true
                else runCatching { app.accountLibrary.musicCategory(app.youtubeHistory.session(), video) }.getOrNull()
            if (revision != library.value.youtubeSessionRevision) return@launch
            if (!liked || music == true) likedMusicMutations[video.id] = LikedMusicSnapshotUpdates.Change(++likedMusicMutationSequence, video, liked)
            val snapshot = app.likedMusicCache.snapshot.takeIf { it.revision == revision } ?: LikedMusicSnapshot(revision)
            val videos = if (!liked) snapshot.videos.filterNot { it.id == video.id }
                else if (music == true) (listOf(video.copy(music = true)) + snapshot.videos).distinctBy { it.id } else snapshot.videos
            app.likedMusicCache.save(snapshot.copy(videos = videos))
            likedMusicSnapshot = videos; likedMusicSnapshotRevision = revision
            if (accountLibrary.value.shelf == AccountShelf.LIKED_MUSIC) openLikedMusic()
        }
    }

    fun moreAccountShelf() { val state = accountLibrary.value; openAccountShelf(state.shelf, state.playlist, state.title, more = true) }
    private fun invalidateAccountLibrary() {
        accountPlaylistCache.clear(); playlistToDelete.value = null
        ++likedMusicEpoch; likedMusicJob?.cancel(); likedMusicJob = null
        likedMusicSnapshot = null; likedMusicSnapshotRevision = -1; likedMusicMutations.clear()
        commentLikes.value = emptySet(); commentLikeBusy.value = emptySet(); commentLikeError.value = null
        if (queuedAccountPage != null || playlistSource.value?.startsWith("account:") == true) clearPlaylistQueue()
        if (collection.value?.accountPage != null) { collectionJob?.cancel(); collection.value = null; collectionBusy.value = false }
        accountLibraryJob?.cancel(); ++accountLibraryGeneration; accountLibraryTokens.clear(); accountLibrary.value = AccountLibraryState()
    }
    val googleHistory = app.youtubeHistory.state
    fun refreshGoogleHistory() { viewModelScope.launch { app.youtubeHistory.refresh() } }
    fun setGoogleHistoryPaused(paused: Boolean) { viewModelScope.launch { try { app.youtubeHistory.changePause(paused) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { app.youtubeHistory.invalidate() } } }
    fun includeShortPlays(enabled: Boolean) {
        app.library.update { it.copy(includeShortPlays = enabled, accountHistorySync = if (enabled) true else it.accountHistorySync, historyPaused = if (enabled) false else it.historyPaused) }
        if (enabled) refreshGoogleHistory()
    }
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
        else { home() }
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
    val commentLikes = MutableStateFlow<Set<String>>(emptySet())
    val commentLikeBusy = MutableStateFlow<Set<String>>(emptySet())
    val commentLikeError = MutableStateFlow<String?>(null)
    fun likeComment(video: Video, comment: VideoComment) {
        if (comment.id in commentLikeBusy.value || comment.id in commentLikes.value) return
        val revision = library.value.youtubeSessionRevision
        fun permitted() = revision == library.value.youtubeSessionRevision && library.value.youtubeHome && library.value.youtubeSessionReady
        if (!permitted()) { commentLikeError.value = "Sign in with Google to like comments"; return }
        commentLikeBusy.value += comment.id; commentLikeError.value = null
        viewModelScope.launch {
            try {
                if (YouTubeCommentLikes().like(video.id, comment.id, app.youtubeHistory.session(), ::permitted) && permitted()) commentLikes.value += comment.id
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (permitted()) commentLikeError.value = error.message ?: "Comment like could not be confirmed" }
            finally { if (revision == library.value.youtubeSessionRevision) commentLikeBusy.value -= comment.id }
        }
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
    val playlistSource = MutableStateFlow<String?>(null)
    val playlistMore = MutableStateFlow(false)
    val playlistQueueBusy = MutableStateFlow(false)
    val playlistQueueError = MutableStateFlow<String?>(null)
    private var queuedSelection: String? = null
    private var queuedAccountPage: AccountLibraryPage? = null
    private var queuedPublicPage: org.schabi.newpipe.extractor.Page? = null
    private var queuedPublicUrl: String? = null
    private var queuedRevision = -1L
    private var queueJob: Job? = null
    private var queueGeneration = 0L
    private var queueAdvanceFrom: String? = null
    private val queueTokens = mutableSetOf<String>()
    fun preparedPlaylistSelection(id: String) = queuedSelection == id && playlistSource.value != null
    fun prepareQueuedSelection(video: Video) { if (playlistSource.value != null && queue.value.any { it.id == video.id }) queuedSelection = video.id }
    private fun clearPlaylistQueue() {
        ++queueGeneration; queueAdvanceFrom = null; queueTokens.clear()
        queueJob?.cancel(); queueJob = null; queue.value = emptyList(); playlistSource.value = null
        queuedSelection = null; queuedAccountPage = null; queuedPublicPage = null; queuedPublicUrl = null
        playlistMore.value = false; playlistQueueBusy.value = false; playlistQueueError.value = null
    }
    fun prepareAccountPlaylist(video: Video) {
        val state = accountLibrary.value
        val derivedMusic = state.shelf == AccountShelf.LIKED_MUSIC && state.playlist == null
        val id = state.playlist ?: if (derivedMusic) "liked-music" else if (state.shelf == AccountShelf.LIKED) "LL" else return
        clearPlaylistQueue()
        queue.value = state.page?.items.orEmpty().mapNotNull { it.video }.distinctBy { it.id }
        playlistSource.value = PlaylistQueuePolicy.key(library.value.youtubeSessionRevision, id)
        queuedRevision = library.value.youtubeSessionRevision; queuedAccountPage = if (derivedMusic) null else state.page
        playlistMore.value = !derivedMusic && state.page?.continuation != null; queuedSelection = video.id
    }
    fun prepareCollectionPlaylist(video: Video) {
        if (collectionIsChannel) return
        val page = collection.value ?: return
        clearPlaylistQueue(); queue.value = page.videos
        playlistSource.value = "collection:$collectionUrl"; queuedRevision = library.value.youtubeSessionRevision
        queuedAccountPage = page.accountPage; queuedPublicPage = page.next; queuedPublicUrl = collectionUrl
        playlistMore.value = page.next != null || page.accountPage?.continuation != null; queuedSelection = video.id
    }
    fun morePlaylistQueue(playAfter: Boolean = false) {
        if (!playlistMore.value) return
        if (playAfter) queueAdvanceFrom = watch.value.video?.id
        if (playlistQueueBusy.value) return
        if (playlistSource.value == null) return
        val generation = queueGeneration
        playlistQueueBusy.value = true; playlistQueueError.value = null
        queueJob = viewModelScope.launch {
            try {
                var appended = false
                for (pageIndex in 0 until 200) {
                    if (queueGeneration != generation) return@launch
                    val incoming: List<Video>
                    val account = queuedAccountPage
                    if (account != null) {
                        check(queuedRevision == library.value.youtubeSessionRevision && library.value.youtubeSessionReady)
                        val token = account.continuation ?: break
                        check(token !in queueTokens) { "Repeated playlist page" }
                        val page = app.accountLibrary.more(app.youtubeHistory.session(), account, AccountEntryKind.VIDEO)
                        if (queueGeneration != generation) return@launch
                        check(queuedRevision == library.value.youtubeSessionRevision && library.value.youtubeSessionReady)
                        queueTokens += token
                        incoming = page.items.mapNotNull { it.video }; queuedAccountPage = page
                        playlistMore.value = page.continuation != null
                    } else {
                        val next = queuedPublicPage ?: break
                        val page = app.videos.playlist(requireNotNull(queuedPublicUrl), next)
                        if (queueGeneration != generation) return@launch
                        incoming = page.videos; queuedPublicPage = page.next; playlistMore.value = page.next != null
                    }
                    if (queueGeneration != generation) return@launch
                    val before = queue.value.size
                    queue.value = PlaylistQueuePolicy.append(queue.value, incoming)
                    appended = queue.value.size > before
                    if (appended || !playlistMore.value) break
                }
                check(appended || !playlistMore.value) { "Playlist pages could not advance" }
                if (queueAdvanceFrom != null && queueAdvanceFrom == watch.value.video?.id && queueGeneration == generation) {
                    val index = queue.value.indexOfFirst { it.id == queueAdvanceFrom }
                    queueAdvanceFrom = null
                    if (index >= 0) queue.value.getOrNull(index + 1)?.let { open(it, true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (queueGeneration == generation) playlistQueueError.value = "The next playlist page could not load. Retry; unrelated videos were not substituted." }
            finally { if (queueGeneration == generation) playlistQueueBusy.value = false }
        }
    }
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

    init {
        home()
        // Background freshness checks do not depend on revisiting the shelf.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2 * 60_000L)
                val snapshot = app.likedMusicCache.snapshot
                if (library.value.youtubeHome && library.value.youtubeSessionReady && snapshot.revision == library.value.youtubeSessionRevision &&
                    LikedMusicFreshness.needsRefresh(snapshot, System.currentTimeMillis())) scanLikedMusic()
            }
        }
    }

    fun playerOptions(transform: (PhotonLibrary) -> PhotonLibrary) = app.library.update { old ->
        val next = transform(old)
        next.copy(playbackSpeed = PlayerOptions.speed(next.playbackSpeed), captionSizePercent = PlayerOptions.subtitleSize(next.captionSizePercent))
    }

    fun home() {
        searchJob?.cancel(); shelfJob?.cancel(); shelves.value = emptyList()
        if (library.value.youtubeHome) { refreshYouTubeHome(); return }
        // Home must not silently become search discovery or a local approximation of Google's feed.
        mutableBrowse.value = BrowseState()
        youtubeHome.value = YouTubeHomeState(error = "Sign in with Google to load your account's Home recommendations. No random or generated feed is substituted.")
    }

    val subscriptionChannels = MutableStateFlow(AccountLibraryState(shelf = AccountShelf.CHANNELS, title = "Subscribed channels"))
    private var subscriptionChannelsJob: Job? = null
    fun loadSubscriptionChannels(more: Boolean = false) {
        if (subscriptionChannels.value.busy || more && subscriptionChannels.value.page?.continuation == null) return
        subscriptionChannelsJob?.cancel()
        val previous = subscriptionChannels.value
        val epoch = subscriptionStateGeneration
        val revision = library.value.youtubeSessionRevision
        subscriptionChannelsJob = viewModelScope.launch {
            subscriptionChannels.value = previous.copy(busy = true, error = null)
            try {
                val session = app.youtubeHistory.session()
                val page = if (more) app.accountLibrary.more(session, previous.page!!, AccountEntryKind.CHANNEL) else app.accountLibrary.load(session, AccountShelf.CHANNELS)
                if (epoch != subscriptionStateGeneration || revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady) return@launch
                val rows = AccountLibraryRows.append(if (more) previous.page?.items.orEmpty() else emptyList(), page.items)
                googleSubscriptions.value = googleSubscriptions.value + rows.filter { it.kind == AccountEntryKind.CHANNEL }.associate { it.id to true }
                subscriptionChannels.value = previous.copy(page = page.copy(items = rows), busy = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (epoch == subscriptionStateGeneration && revision == library.value.youtubeSessionRevision) subscriptionChannels.value = previous.copy(busy = false, error = "Channels could not load. Retry.") }
        }
    }
    private val subscriptionReads = mutableSetOf<String>()
    fun resolveSubscription(channel: Channel) {
        val id = channel.url.substringAfterLast('/')
        if (!id.matches(Regex("UC[A-Za-z0-9_-]{20,30}")) || !subscriptionReads.add(id)) return
        val epoch = subscriptionStateGeneration
        val revision = library.value.youtubeSessionRevision
        viewModelScope.launch {
            try {
                val state = app.accountActions.subscriptionState(id, app.youtubeHistory.session())
                if (state != null && epoch == subscriptionStateGeneration && revision == library.value.youtubeSessionRevision && library.value.youtubeSessionReady) googleSubscriptions.value = googleSubscriptions.value + (id to state)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Keep known directory proof; unknown is not false. */ }
            finally { subscriptionReads.remove(id) }
        }
    }
    fun subscriptionsFeed() {
        loadSubscriptionChannels()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            page = null
            val revision = library.value.youtubeSessionRevision
            mutableBrowse.value = BrowseState(busy = true, query = "Google subscriptions")
            try {
                val result = app.youtubeHome.subscriptions(app.youtubeHistory.session())
                if (revision != library.value.youtubeSessionRevision || !library.value.youtubeSessionReady) return@launch
                mutableBrowse.value = BrowseState(videos = result.videos.map { ShortsPresentation.merge(it, videoPresentation.value[it.id]) }, query = "Google subscriptions", error = result.message)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == library.value.youtubeSessionRevision) mutableBrowse.value = BrowseState(query = "Google subscriptions", error = "Could not read Google subscriptions. Sign in with the primary Google button and retry. No local channel feed was substituted.") }
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
                val sources = result.videos.map { it.channelUrl }
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

    fun retryPlayback(video: Video) { app.videos.invalidateDetails(video.id); open(video, fromQueue = playlistSource.value != null && queue.value.any { it.id == video.id }) }

    val vrViewing = MutableStateFlow(false)
    fun likeShort(video: Video) { if (googleLikes.value[video.id] != true) requestGoogleChange(GoogleChange(GoogleChangeKind.LIKE, video.id, video.title, video, desired = true)) }

    fun open(video: Video, fromQueue: Boolean = false) {
        val opened = android.os.SystemClock.elapsedRealtime()
        val revision = library.value.youtubeSessionRevision
        PhotonDiagnostics.event("Open video requested")
        if (!fromQueue && !preparedPlaylistSelection(video.id)) clearPlaylistQueue()
        queuedSelection = null; queueAdvanceFrom = null
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            mutableWatch.value = WatchState(video = video, busy = true)
            try {
                val details = app.videos.details(video.id)
                // Metadata may finish while stream extraction is suspended; use the latest proof, not the opening snapshot.
                val presentation = videoPresentation.value[video.id] ?: video
                val proved = ShortsPresentation.merge(ShortsPresentation.merge(details.video, video), videoPresentation.value[video.id])
                videoPresentation.value = (videoPresentation.value + (video.id to proved)).entries.toList().takeLast(512).associate { it.toPair() }
                presentationRevisions[video.id] = revision
                val max = library.value.defaultHeight
                val quality = details.qualities.firstOrNull { it.height <= max } ?: details.qualities.last()
                mutableWatch.value = WatchState(details.video.copy(short = video.short || presentation.short || details.video.short, thumbnail = if (presentation.short && presentation.thumbnail.isNotBlank()) presentation.thumbnail else details.video.thumbnail), details, quality, request = System.nanoTime(), startPositionMs = library.value.progress[video.id]?.resumeMs() ?: 0, openedAtMs = opened, webRevision = revision)
                // Playback is published first; account-only reads must not contend with startup.
                viewModelScope.launch { kotlinx.coroutines.delay(1_000); if (mutableWatch.value.video?.id == video.id) { resolveVideoPresentation(proved); details.channel?.let(::resolveSubscription) } }
                // No speculative next-video extraction while the selected video is using the connection.

            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableWatch.value = WatchState(video = video, error = error.message ?: "This video could not be played") }
        }
    }

    fun offline(download: SavedDownload) {
        clearPlaylistQueue()
        watchJob?.cancel()
        if (!File(download.path).isFile) {
            mutableWatch.value = WatchState(video = download.video, error = "The downloaded file is missing. Remove this entry and download it again.")
            return
        }
        mutableWatch.value = WatchState(video = videoPresentation.value[download.video.id]?.let { download.video.copy(short = download.video.short || it.short, thumbnail = it.thumbnail.ifBlank { download.video.thumbnail }) } ?: download.video, localPath = download.path, request = System.nanoTime(), startPositionMs = library.value.progress[download.video.id]?.resumeMs() ?: 0,
            openedAtMs = android.os.SystemClock.elapsedRealtime(), webRevision = library.value.youtubeSessionRevision)
    }

    fun quality(quality: VideoQuality) {
        mutableWatch.value = mutableWatch.value.copy(quality = quality, request = System.nanoTime(), preservePosition = true)
    }

    fun adoptPlaying(video: Video) {
        if (mutableWatch.value.video == null) mutableWatch.value = WatchState(video = video)
    }

    fun close() { vrViewing.value = false; watchJob?.cancel(); mutableWatch.value = WatchState() }
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
    fun youtubeLayout(value: Boolean) = app.library.update { it.copy(youtubeLayout = value) }
    fun presentShort(video: Video) {
        searchJob?.cancel()
        mutableBrowse.value = BrowseState(videos = (listOf(video) + mutableBrowse.value.videos.filter { it.short }).distinctBy { it.id }, query = "Shorts")
        open(video)
    }
    fun accent(value: Int) = app.library.update { it.copy(accent = value) }
    fun dark(value: Boolean) = app.library.update { it.copy(dark = value) }
    fun wifi(value: Boolean) = app.library.update { it.copy(wifiOnly = value) }
    fun musicBackground(value: Boolean) = app.library.update { it.copy(musicBackground = value) }
    fun videoBackground(value: Boolean) = app.library.update { it.copy(videoBackground = value) }
    fun maxHeight(value: Int) = app.library.update { it.copy(defaultHeight = value) }
    fun clearHistory() = app.library.update { it.copy(history = emptyList(), progress = emptyMap()) }
    fun subscribe(channel: Channel) {
        val id = android.net.Uri.parse(channel.url).pathSegments.let { if (it.size == 2 && it[0] == "channel") it[1] else "" }
        requestGoogleChange(GoogleChange(GoogleChangeKind.SUBSCRIBE, id, channel.name, channel = channel, desired = googleSubscriptions.value[id] != true))
    }
    fun createGooglePlaylist(name: String, video: Video? = null) = requestGoogleChange(GoogleChange(GoogleChangeKind.CREATE_PLAYLIST, "", name, video))
    fun addToPlaylist(id: String, video: Video) = requestGoogleChange(GoogleChange(GoogleChangeKind.ADD_VIDEO, id, video.title, video))
    fun removeFromPlaylist(id: String, video: Video) = requestGoogleChange(GoogleChange(GoogleChangeKind.REMOVE_VIDEO, id, video.title, video))
    val playlistToDelete = MutableStateFlow<Pair<String, String>?>(null)
    fun deletePlaylist(id: String, title: String) { playlistToDelete.value = id to title }
    fun cancelPlaylistDeletion() { playlistToDelete.value = null }
    fun confirmPlaylistDeletion() {
        val target = playlistToDelete.value ?: return
        playlistToDelete.value = null
        requestGoogleChange(GoogleChange(GoogleChangeKind.DELETE_PLAYLIST, target.first, target.second))
    }
    fun next() {
        val index = queue.value.indexOfFirst { it.id == watch.value.video?.id }
        if (index >= 0) {
            val next = queue.value.getOrNull(index + 1)
            if (next != null) open(next, true) else if (playlistMore.value) morePlaylistQueue(playAfter = true)
        }
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
                val result = if (channel) app.videos.channel(url, previous?.next, shorts)
                else if (library.value.youtubeHome && library.value.youtubeSessionReady) {
                    val revision = library.value.youtubeSessionRevision
                    val id = android.net.Uri.parse(requireNotNull(YouTubeLinks.playlistUrl(url))).getQueryParameter("list")!!
                    val session = app.youtubeHistory.session()
                    val page = previous?.accountPage?.let { app.accountLibrary.more(session, it, AccountEntryKind.VIDEO) }
                        ?: app.accountLibrary.load(session, AccountShelf.PLAYLISTS, id)
                    check(revision == library.value.youtubeSessionRevision && library.value.youtubeSessionReady)
                    CollectionPage(previous?.title ?: "Playlist", "", page.items.mapNotNull { it.video }, null, accountPage = page)
                } else app.videos.playlist(url, previous?.next)
                result.channel?.let(::resolveSubscription)
                val enriched = result.copy(videos = result.videos.map { ShortsPresentation.merge(it, videoPresentation.value[it.id]) })
                val current = collection.value
                collection.value = if (previous == null) enriched else (current ?: previous).copy(videos = ((current ?: previous).videos + enriched.videos).distinctBy { it.id }, next = result.next, accountPage = result.accountPage)
                enriched.videos.take(6).forEach { resolveVideoPresentation(it, includeLike = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { collectionError.value = error.message ?: "Could not load this page" }
            finally { if (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) collectionBusy.value = false }
        }
    }
    fun moreCollection() { if (collection.value?.let { it.next != null || it.accountPage?.continuation != null } == true) openCollection(collectionUrl, collectionIsChannel, collectionShorts, true) }
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
