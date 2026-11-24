package com.example.ui

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppMode
import com.example.data.FeedLoadState
import com.example.data.LibraryFilter
import com.example.data.LibrarySort
import com.example.data.MediaBucket
import com.example.data.MusicCategory
import com.example.data.PhotonCloudRepository
import com.example.data.PhotonPreferencesRepository
import com.example.data.PlaybackProgress
import com.example.data.PlayerDisplayMode
import com.example.data.RepeatMode
import com.example.data.SearchFilter
import com.example.data.SleepTimerOption
import com.example.data.UserAccountProfile
import com.example.data.UserPlaylist
import com.example.data.YouTubeCategory
import com.example.data.YouTubeMediaItem
import com.example.data.YouTubeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PlayerCommand {
    data class LoadVideo(val videoId: String, val autoplay: Boolean) : PlayerCommand
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data class SeekTo(val seconds: Int) : PlayerCommand
    data class SetRate(val rate: Float) : PlayerCommand
}

@Immutable
data class PhotonUiState(
    // Authenticated Google & YouTube/YT Music profile
    val userProfile: UserAccountProfile = UserAccountProfile(),
    val isSyncingYouTube: Boolean = false,
    val syncStatusText: String? = null,
    val isAccountSyncSheetOpen: Boolean = false,
    val isYouTubeWebSyncOpen: Boolean = false,

    val appMode: AppMode = AppMode.YOUTUBE,
    val playerDisplayMode: PlayerDisplayMode = PlayerDisplayMode.VIDEO,
    val selectedYouTubeCategory: YouTubeCategory = YouTubeCategory.FOR_YOU,
    val selectedMusicCategory: MusicCategory = MusicCategory.FOR_YOU,
    val youtubeFeedState: FeedLoadState = FeedLoadState.Loading,
    val musicFeedState: FeedLoadState = FeedLoadState.Loading,

    // Playback state (progress ticks isolated in playbackProgress StateFlow)
    val queue: List<YouTubeMediaItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val relatedVideos: List<YouTubeMediaItem> = emptyList(),
    val isLoadingRelated: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val autoplayEnabled: Boolean = true,
    val sleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    val sleepRemainingSec: Int? = null,
    val playerErrorMessage: String? = null,

    // Search state
    val searchQuery: String = "",
    val committedSearchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val searchSuggestions: List<String> = emptyList(),
    val searchState: FeedLoadState = FeedLoadState.Success(emptyList()),
    val recentSearches: List<String> = emptyList(),

    // Cloud-synced Library state
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val likedItems: List<YouTubeMediaItem> = emptyList(),
    val watchLaterItems: List<YouTubeMediaItem> = emptyList(),
    val historyItems: List<YouTubeMediaItem> = emptyList(),
    val userPlaylists: List<UserPlaylist> = emptyList(),

    // Overlays & Sheets
    val isPlayerExpanded: Boolean = false,
    val isQueueSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isCreatePlaylistSheetOpen: Boolean = false,
    val itemForOptionsSheet: YouTubeMediaItem? = null,
    val itemForPlaylistPicker: YouTubeMediaItem? = null,
    val statusMessage: String? = null
) {
    val currentItem: YouTubeMediaItem?
        get() = queue.getOrNull(currentQueueIndex)

    val upcomingQueue: List<Pair<Int, YouTubeMediaItem>>
        get() = queue.mapIndexed { idx, item -> idx to item }.filter { it.first > currentQueueIndex }

    val likedVideoIds: Set<String>
        get() = likedItems.map { it.videoId }.toSet()

    val watchLaterVideoIds: Set<String>
        get() = watchLaterItems.map { it.videoId }.toSet()
}

class PhotonViewModel(
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String,
    initialPhotoUrl: String,
    private val cloudRepo: PhotonCloudRepository,
    private val prefsRepo: PhotonPreferencesRepository,
    private val youtubeRepo: YouTubeRepository = YouTubeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PhotonUiState(
            userProfile = UserAccountProfile(
                uid = currentUserId,
                displayName = initialDisplayName.ifBlank { "Google User" },
                email = initialEmail,
                photoUrl = initialPhotoUrl
            )
        )
    )
    val uiState: StateFlow<PhotonUiState> = _uiState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val _playerCommands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16)
    val playerCommands: SharedFlow<PlayerCommand> = _playerCommands.asSharedFlow()

    private val youtubeFeedCache = mutableMapOf<YouTubeCategory, List<YouTubeMediaItem>>()
    private val musicFeedCache = mutableMapOf<MusicCategory, List<YouTubeMediaItem>>()

    private var youtubeSessionCookie: String = ""
    private var lastPersonalizationKey: String = ""
    private var youtubeFeedJob: Job? = null
    private var musicFeedJob: Job? = null
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var relatedJob: Job? = null
    private var sleepJob: Job? = null
    private var messageClearJob: Job? = null
    private var initialFeedTriggered = false

    init {
        // 1. Ensure Google user profile exists in Firestore
        viewModelScope.launch {
            cloudRepo.ensureUserProfile(
                userId = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail,
                photoUrl = initialPhotoUrl
            )
        }

        // 2. Observe local private prefs (recent searches & optional live YouTube cookie)
        viewModelScope.launch {
            prefsRepo.state.collect { local ->
                val cookieChanged = youtubeSessionCookie != local.youtubeSessionCookie
                youtubeSessionCookie = local.youtubeSessionCookie
                _uiState.update {
                    it.copy(
                        recentSearches = local.recentSearches,
                        autoplayEnabled = local.autoplayEnabled
                    )
                }
                if (!initialFeedTriggered) {
                    initialFeedTriggered = true
                    loadYouTubeFeed(_uiState.value.selectedYouTubeCategory, forceRefresh = false)
                } else if (cookieChanged && local.youtubeSessionCookie.isNotBlank()) {
                    invalidatePersonalizedCachesAndReload()
                }
            }
        }

        // 3. Observe Firestore UserProfile (two-tier Flow error unwrapping)
        viewModelScope.launch {
            cloudRepo.observeUserProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing profile", firestoreError ?: error)
                }
                .collect { profile ->
                    if (profile != null) {
                        val key = "${profile.youtubeConnected}|${profile.youtubeHandle}|${profile.subscribedChannels.joinToString(",")}|${profile.favoriteArtists.joinToString(",")}"
                        val personalizationChanged = lastPersonalizationKey.isNotEmpty() && lastPersonalizationKey != key
                        lastPersonalizationKey = key
                        _uiState.update {
                            it.copy(
                                userProfile = profile,
                                autoplayEnabled = profile.autoplayEnabled
                            )
                        }
                        if (personalizationChanged) {
                            invalidatePersonalizedCachesAndReload()
                        }
                    }
                }
        }

        // 4. Observe Firestore Saved Media (Liked, Watch Later, History)
        viewModelScope.launch {
            cloudRepo.observeSavedItems(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing saved items", firestoreError ?: error)
                }
                .collect { saved ->
                    _uiState.update {
                        it.copy(
                            likedItems = saved.liked,
                            watchLaterItems = saved.watchLater,
                            historyItems = saved.history
                        )
                    }
                }
        }

        // 5. Observe Firestore User Playlists
        viewModelScope.launch {
            cloudRepo.observePlaylists(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing playlists", firestoreError ?: error)
                }
                .collect { playlists ->
                    _uiState.update { it.copy(userPlaylists = playlists) }
                }
        }
    }

    // Account & YouTube / YT Music Import Actions
    fun setAccountSyncSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isAccountSyncSheetOpen = open) }
    }

    fun setYouTubeWebSyncOpen(open: Boolean) {
        _uiState.update { it.copy(isYouTubeWebSyncOpen = open) }
    }

    fun onLiveYouTubeCookiesCaptured(cookieHeader: String) {
        _uiState.update { it.copy(isYouTubeWebSyncOpen = false) }
        viewModelScope.launch {
            prefsRepo.saveYouTubeSessionCookie(cookieHeader)
            youtubeSessionCookie = cookieHeader
            runLiveYouTubeImport(cookieHeader)
        }
    }

    fun resyncLiveYouTubeSession() {
        val cookie = youtubeSessionCookie
        if (!youtubeRepo.hasAuthenticatedYouTubeSession(cookie)) {
            _uiState.update { it.copy(isYouTubeWebSyncOpen = true) }
            return
        }
        viewModelScope.launch {
            runLiveYouTubeImport(cookie)
        }
    }

    private suspend fun runLiveYouTubeImport(cookieHeader: String) {
        _uiState.update {
            it.copy(
                isSyncingYouTube = true,
                syncStatusText = "Importing your YouTube & YT Music feeds..."
            )
        }
        val result = youtubeRepo.fetchAuthenticatedYouTubeImport(cookieHeader)
        result.onSuccess { imported ->
            val currentProfile = _uiState.value.userProfile
            val mergedChannels = (imported.discoveredChannels + currentProfile.subscribedChannels).distinct().take(40)
            val mergedArtists = (imported.discoveredArtists + currentProfile.favoriteArtists).distinct().take(40)
            val resolvedHandle = imported.accountHandle.ifBlank { currentProfile.youtubeHandle }

            cloudRepo.updatePersonalization(
                userId = currentUserId,
                youtubeConnected = true,
                youtubeHandle = resolvedHandle,
                subscribedChannels = mergedChannels,
                favoriteArtists = mergedArtists,
                onboardingComplete = true
            )

            // Sync imported liked videos & YT Music liked songs into Firestore Library
            (imported.likedVideos.take(8) + imported.ytMusicLikedSongs.take(8))
                .distinctBy { it.videoId }
                .forEach { item ->
                    cloudRepo.upsertSavedItem(
                        userId = currentUserId,
                        bucket = MediaBucket.LIKED,
                        item = item,
                        importedFromYouTube = true
                    )
                }

            if (imported.ytMusicLikedSongs.isNotEmpty()) {
                cloudRepo.upsertPlaylist(
                    userId = currentUserId,
                    playlistId = "ytm_imported_likes",
                    name = "YT Music Liked Songs",
                    items = imported.ytMusicLikedSongs.take(40),
                    source = "YT_MUSIC"
                )
            }

            if (imported.youtubeHomeItems.isNotEmpty()) {
                youtubeFeedCache[YouTubeCategory.FOR_YOU] = imported.youtubeHomeItems
            }
            if (imported.subscriptionItems.isNotEmpty()) {
                youtubeFeedCache[YouTubeCategory.SUBSCRIPTIONS] = imported.subscriptionItems
            }
            if (imported.ytMusicHomeItems.isNotEmpty()) {
                musicFeedCache[MusicCategory.FOR_YOU] = imported.ytMusicHomeItems
            }

            val totalImported = imported.youtubeHomeItems.size + imported.ytMusicHomeItems.size
            _uiState.update { state ->
                state.copy(
                    isSyncingYouTube = false,
                    syncStatusText = "Imported ${imported.discoveredChannels.size} channels & ${imported.discoveredArtists.size} music artists",
                    youtubeFeedState = if (state.selectedYouTubeCategory == YouTubeCategory.FOR_YOU && imported.youtubeHomeItems.isNotEmpty()) {
                        FeedLoadState.Success(imported.youtubeHomeItems)
                    } else {
                        state.youtubeFeedState
                    },
                    musicFeedState = if (state.selectedMusicCategory == MusicCategory.FOR_YOU && imported.ytMusicHomeItems.isNotEmpty()) {
                        FeedLoadState.Success(imported.ytMusicHomeItems)
                    } else {
                        state.musicFeedState
                    }
                )
            }
            if (totalImported == 0) {
                invalidatePersonalizedCachesAndReload()
            }
            showMessage("Synced with YouTube & YT Music")
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isSyncingYouTube = false,
                    syncStatusText = err.message ?: "Unable to sync YouTube session. You can pick channels & artists below."
                )
            }
        }
    }

    fun savePersonalization(youtubeHandle: String, channels: List<String>, artists: List<String>) {
        viewModelScope.launch {
            val current = _uiState.value.userProfile
            cloudRepo.updatePersonalization(
                userId = currentUserId,
                youtubeConnected = current.youtubeConnected,
                youtubeHandle = youtubeHandle.ifBlank { current.youtubeHandle },
                subscribedChannels = channels,
                favoriteArtists = artists,
                onboardingComplete = true
            )
            _uiState.update {
                it.copy(
                    isAccountSyncSheetOpen = false,
                    userProfile = it.userProfile.copy(
                        youtubeHandle = youtubeHandle.ifBlank { it.userProfile.youtubeHandle },
                        subscribedChannels = channels,
                        favoriteArtists = artists,
                        onboardingComplete = true
                    )
                )
            }
            invalidatePersonalizedCachesAndReload()
            showMessage("Feeds adjusted to your YouTube & YT Music tastes")
        }
    }

    suspend fun clearLocalSessionBeforeSignOut() {
        youtubeSessionCookie = ""
        prefsRepo.clearSessionOnSignOut()
    }

    private fun invalidatePersonalizedCachesAndReload() {
        youtubeFeedCache.remove(YouTubeCategory.FOR_YOU)
        youtubeFeedCache.remove(YouTubeCategory.SUBSCRIPTIONS)
        musicFeedCache.remove(MusicCategory.FOR_YOU)
        musicFeedCache.remove(MusicCategory.YOUR_MIX)

        val state = _uiState.value
        if (state.appMode == AppMode.YOUTUBE) {
            loadYouTubeFeed(state.selectedYouTubeCategory, forceRefresh = true)
        } else {
            loadMusicFeed(state.selectedMusicCategory, forceRefresh = true)
        }
    }

    // Mode Switching
    fun setAppMode(mode: AppMode) {
        if (_uiState.value.appMode == mode) return
        _uiState.update {
            it.copy(
                appMode = mode,
                playerDisplayMode = if (mode == AppMode.YOUTUBE) PlayerDisplayMode.VIDEO else PlayerDisplayMode.AUDIO
            )
        }
        viewModelScope.launch {
            prefsRepo.setDefaultMode(mode)
            cloudRepo.updatePreferences(currentUserId, preferredMode = mode)
        }

        if (mode == AppMode.YOUTUBE && _uiState.value.youtubeFeedState !is FeedLoadState.Success) {
            loadYouTubeFeed(_uiState.value.selectedYouTubeCategory, forceRefresh = false)
        } else if (mode == AppMode.MUSIC && _uiState.value.musicFeedState !is FeedLoadState.Success) {
            loadMusicFeed(_uiState.value.selectedMusicCategory, forceRefresh = false)
        }

        val committed = _uiState.value.committedSearchQuery
        if (committed.isNotBlank()) {
            performSearch(committed, mode = mode, filter = _uiState.value.searchFilter)
        }
    }

    fun setPlayerDisplayMode(displayMode: PlayerDisplayMode) {
        _uiState.update { it.copy(playerDisplayMode = displayMode) }
    }

    // Home Feeds (Personalized to signed-in Google + YouTube / YT Music account)
    fun selectYouTubeCategory(category: YouTubeCategory) {
        _uiState.update { it.copy(selectedYouTubeCategory = category) }
        loadYouTubeFeed(category, forceRefresh = false)
    }

    fun selectMusicCategory(category: MusicCategory) {
        _uiState.update { it.copy(selectedMusicCategory = category) }
        loadMusicFeed(category, forceRefresh = false)
    }

    fun refreshCurrentFeed() {
        val state = _uiState.value
        if (state.appMode == AppMode.YOUTUBE) {
            loadYouTubeFeed(state.selectedYouTubeCategory, forceRefresh = true)
        } else {
            loadMusicFeed(state.selectedMusicCategory, forceRefresh = true)
        }
    }

    private fun loadYouTubeFeed(category: YouTubeCategory, forceRefresh: Boolean) {
        val cached = youtubeFeedCache[category]
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            _uiState.update { it.copy(youtubeFeedState = FeedLoadState.Success(cached)) }
            return
        }
        youtubeFeedJob?.cancel()
        _uiState.update { it.copy(youtubeFeedState = FeedLoadState.Loading) }
        youtubeFeedJob = viewModelScope.launch {
            val snap = _uiState.value
            val result = youtubeRepo.fetchPersonalizedYouTubeFeed(
                category = category,
                profile = snap.userProfile,
                likedItems = snap.likedItems,
                historyItems = snap.historyItems,
                cookieHeader = youtubeSessionCookie
            )
            result.onSuccess { items ->
                if (items.isEmpty()) {
                    _uiState.update { it.copy(youtubeFeedState = FeedLoadState.Error("No videos returned")) }
                } else {
                    youtubeFeedCache[category] = items
                    _uiState.update { it.copy(youtubeFeedState = FeedLoadState.Success(items)) }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(youtubeFeedState = FeedLoadState.Error(err.message ?: "Network error"))
                }
            }
        }
    }

    private fun loadMusicFeed(category: MusicCategory, forceRefresh: Boolean) {
        val cached = musicFeedCache[category]
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            _uiState.update { it.copy(musicFeedState = FeedLoadState.Success(cached)) }
            return
        }
        musicFeedJob?.cancel()
        _uiState.update { it.copy(musicFeedState = FeedLoadState.Loading) }
        musicFeedJob = viewModelScope.launch {
            val snap = _uiState.value
            val result = youtubeRepo.fetchPersonalizedMusicFeed(
                category = category,
                profile = snap.userProfile,
                likedItems = snap.likedItems,
                historyItems = snap.historyItems,
                cookieHeader = youtubeSessionCookie
            )
            result.onSuccess { items ->
                if (items.isEmpty()) {
                    _uiState.update { it.copy(musicFeedState = FeedLoadState.Error("No tracks returned")) }
                } else {
                    musicFeedCache[category] = items
                    _uiState.update { it.copy(musicFeedState = FeedLoadState.Success(items)) }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(musicFeedState = FeedLoadState.Error(err.message ?: "Network error"))
                }
            }
        }
    }

    // Playback Actions
    fun playItem(
        item: YouTubeMediaItem,
        contextList: List<YouTubeMediaItem>? = null,
        expandPlayer: Boolean = _uiState.value.appMode == AppMode.YOUTUBE
    ) {
        val mode = _uiState.value.appMode
        val targetQueue = if (!contextList.isNullOrEmpty() && contextList.any { it.videoId == item.videoId }) {
            contextList
        } else {
            listOf(item)
        }
        val idx = targetQueue.indexOfFirst { it.videoId == item.videoId }.coerceAtLeast(0)
        val displayMode = if (mode == AppMode.YOUTUBE) PlayerDisplayMode.VIDEO else PlayerDisplayMode.AUDIO

        _playbackProgress.value = PlaybackProgress(positionSec = 0, durationSec = item.durationSec)
        _uiState.update {
            it.copy(
                queue = targetQueue,
                currentQueueIndex = idx,
                isPlaying = true,
                isBuffering = true,
                playerDisplayMode = displayMode,
                isPlayerExpanded = expandPlayer || it.isPlayerExpanded,
                playerErrorMessage = null
            )
        }
        recordHistory(item)
        _playerCommands.tryEmit(PlayerCommand.LoadVideo(item.videoId, autoplay = true))
        loadRelatedFor(item)
    }

    fun playList(items: List<YouTubeMediaItem>, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        val ordered = if (shuffle) items.shuffled() else items
        _uiState.update { it.copy(shuffleEnabled = shuffle) }
        playItem(ordered.first(), contextList = ordered, expandPlayer = _uiState.value.appMode == AppMode.YOUTUBE)
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val current = state.currentItem ?: return
        if (state.isPlaying || state.isBuffering) {
            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
            _playerCommands.tryEmit(PlayerCommand.Pause)
        } else {
            _uiState.update { it.copy(isPlaying = true, playerErrorMessage = null) }
            _playerCommands.tryEmit(PlayerCommand.Play)
            if (_playbackProgress.value.durationSec == 0 && _playbackProgress.value.positionSec == 0) {
                _playerCommands.tryEmit(PlayerCommand.LoadVideo(current.videoId, autoplay = true))
            }
        }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        val nextIdx = when {
            state.repeatMode == RepeatMode.ONE -> state.currentQueueIndex
            state.shuffleEnabled && state.queue.size > 1 -> {
                state.queue.indices.filter { it != state.currentQueueIndex }.random()
            }
            state.currentQueueIndex + 1 < state.queue.size -> state.currentQueueIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                val nextRelated = state.relatedVideos.firstOrNull { rel ->
                    state.queue.none { it.videoId == rel.videoId }
                }
                if (nextRelated != null && state.autoplayEnabled) {
                    val extended = state.queue + nextRelated
                    _uiState.update { it.copy(queue = extended) }
                    extended.lastIndex
                } else {
                    _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                    _playerCommands.tryEmit(PlayerCommand.Pause)
                    return
                }
            }
        }

        val target = _uiState.value.queue.getOrNull(nextIdx) ?: return
        _playbackProgress.value = PlaybackProgress(positionSec = 0, durationSec = target.durationSec)
        _uiState.update {
            it.copy(
                currentQueueIndex = nextIdx,
                isPlaying = true,
                isBuffering = true,
                playerErrorMessage = null
            )
        }
        recordHistory(target)
        _playerCommands.tryEmit(PlayerCommand.LoadVideo(target.videoId, autoplay = true))
        loadRelatedFor(target)
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (_playbackProgress.value.positionSec > 4) {
            seekToSeconds(0)
            return
        }
        if (state.queue.isEmpty()) return
        val prevIdx = when {
            state.currentQueueIndex > 0 -> state.currentQueueIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.queue.lastIndex
            else -> 0
        }
        val target = state.queue.getOrNull(prevIdx) ?: return
        _playbackProgress.value = PlaybackProgress(positionSec = 0, durationSec = target.durationSec)
        _uiState.update {
            it.copy(
                currentQueueIndex = prevIdx,
                isPlaying = true,
                isBuffering = true,
                playerErrorMessage = null
            )
        }
        recordHistory(target)
        _playerCommands.tryEmit(PlayerCommand.LoadVideo(target.videoId, autoplay = true))
        loadRelatedFor(target)
    }

    fun seekToFraction(fraction: Float) {
        val dur = _playbackProgress.value.durationSec.takeIf { it > 0 }
            ?: _uiState.value.currentItem?.durationSec
            ?: 0
        if (dur <= 0) return
        val targetSec = (fraction.coerceIn(0f, 1f) * dur).toInt()
        seekToSeconds(targetSec)
    }

    fun seekToSeconds(seconds: Int) {
        val clamped = seconds.coerceAtLeast(0)
        _playbackProgress.update { it.copy(positionSec = clamped) }
        _playerCommands.tryEmit(PlayerCommand.SeekTo(clamped))
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun cycleRepeatMode() {
        _uiState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOfFirst { it == _uiState.value.playbackSpeed }.coerceAtLeast(1)
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        _uiState.update { it.copy(playbackSpeed = nextSpeed) }
        _playerCommands.tryEmit(PlayerCommand.SetRate(nextSpeed))
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoplayEnabled = enabled) }
        viewModelScope.launch {
            prefsRepo.setAutoplayEnabled(enabled)
            cloudRepo.updatePreferences(currentUserId, autoplayEnabled = enabled)
        }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepJob?.cancel()
        val totalSec = option.minutes?.times(60)
        _uiState.update { it.copy(sleepTimer = option, sleepRemainingSec = totalSec) }
        if (totalSec != null) {
            showMessage("Sleep timer set for ${option.label}")
            sleepJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000L)
                    val rem = _uiState.value.sleepRemainingSec ?: break
                    if (rem <= 1) {
                        _uiState.update {
                            it.copy(
                                isPlaying = false,
                                sleepTimer = SleepTimerOption.OFF,
                                sleepRemainingSec = null
                            )
                        }
                        _playerCommands.tryEmit(PlayerCommand.Pause)
                        showMessage("Sleep timer ended")
                        break
                    } else {
                        _uiState.update { it.copy(sleepRemainingSec = rem - 1) }
                    }
                }
            }
        } else {
            showMessage("Sleep timer cleared")
        }
    }

    fun onPlayerReady() {
        // Player bridge initialized
    }

    fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean, ended: Boolean) {
        if (ended) {
            if (_uiState.value.repeatMode == RepeatMode.ONE) {
                seekToSeconds(0)
                _playerCommands.tryEmit(PlayerCommand.Play)
            } else if (_uiState.value.autoplayEnabled || _uiState.value.currentQueueIndex + 1 < _uiState.value.queue.size) {
                skipNext()
            } else {
                _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
            }
            return
        }
        val prev = _uiState.value
        if (prev.isPlaying != isPlaying || prev.isBuffering != isBuffering || (isPlaying && prev.playerErrorMessage != null)) {
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playerErrorMessage = if (isPlaying) null else it.playerErrorMessage
                )
            }
        }
    }

    fun onProgressUpdate(currentSec: Int, durationSec: Int) {
        val current = _playbackProgress.value
        val resolvedDur = if (durationSec > 0) durationSec else current.durationSec
        if (current.positionSec != currentSec || current.durationSec != resolvedDur) {
            _playbackProgress.value = PlaybackProgress(
                positionSec = currentSec,
                durationSec = resolvedDur
            )
        }
    }

    fun onPlayerError(message: String) {
        _uiState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                playerErrorMessage = message
            )
        }
    }

    private fun loadRelatedFor(item: YouTubeMediaItem) {
        relatedJob?.cancel()
        _uiState.update { it.copy(isLoadingRelated = true) }
        relatedJob = viewModelScope.launch {
            val result = youtubeRepo.fetchRelatedVideos(item.videoId, isMusicContext = item.isMusic)
            result.onSuccess { related ->
                _uiState.update { state ->
                    val updatedQueue = if (state.queue.size <= 1 && related.isNotEmpty()) {
                        state.queue + related.take(10)
                    } else {
                        state.queue
                    }
                    state.copy(
                        relatedVideos = related,
                        queue = updatedQueue,
                        isLoadingRelated = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingRelated = false) }
            }
        }
    }

    // Queue Management
    fun jumpToQueueIndex(index: Int) {
        val target = _uiState.value.queue.getOrNull(index) ?: return
        _playbackProgress.value = PlaybackProgress(positionSec = 0, durationSec = target.durationSec)
        _uiState.update {
            it.copy(
                currentQueueIndex = index,
                isPlaying = true,
                isBuffering = true,
                playerErrorMessage = null
            )
        }
        recordHistory(target)
        _playerCommands.tryEmit(PlayerCommand.LoadVideo(target.videoId, autoplay = true))
        loadRelatedFor(target)
    }

    fun playNextInQueue(item: YouTubeMediaItem) {
        _uiState.update { state ->
            if (state.queue.isEmpty()) {
                return@update state.copy(queue = listOf(item), currentQueueIndex = 0)
            }
            val mutable = state.queue.toMutableList()
            val insertAt = (state.currentQueueIndex + 1).coerceAtMost(mutable.size)
            mutable.add(insertAt, item)
            state.copy(queue = mutable)
        }
        showMessage("Playing next")
    }

    fun addToQueue(item: YouTubeMediaItem) {
        _uiState.update { state ->
            if (state.queue.isEmpty()) {
                state.copy(queue = listOf(item), currentQueueIndex = 0)
            } else {
                state.copy(queue = state.queue + item)
            }
        }
        showMessage("Added to queue")
    }

    fun moveQueueItemUp(index: Int) {
        _uiState.update { state ->
            if (index <= state.currentQueueIndex + 1 || index !in state.queue.indices) return@update state
            val mutable = state.queue.toMutableList()
            val moved = mutable.removeAt(index)
            mutable.add(index - 1, moved)
            state.copy(queue = mutable)
        }
    }

    fun removeFromQueue(index: Int) {
        _uiState.update { state ->
            if (index !in state.queue.indices || index == state.currentQueueIndex) return@update state
            val mutable = state.queue.toMutableList()
            mutable.removeAt(index)
            val newCurrent = if (index < state.currentQueueIndex) state.currentQueueIndex - 1 else state.currentQueueIndex
            state.copy(queue = mutable, currentQueueIndex = newCurrent)
        }
    }

    fun clearUpcomingQueue() {
        _uiState.update { state ->
            if (state.currentQueueIndex < 0) state
            else state.copy(queue = state.queue.take(state.currentQueueIndex + 1))
        }
        showMessage("Cleared upcoming queue")
    }

    // Live Search & Autocomplete
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            suggestionJob?.cancel()
            _uiState.update {
                it.copy(
                    committedSearchQuery = "",
                    searchSuggestions = emptyList(),
                    searchState = FeedLoadState.Success(emptyList())
                )
            }
            return
        }
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(200L)
            val suggestions = youtubeRepo.fetchSuggestions(query)
            _uiState.update { it.copy(searchSuggestions = suggestions) }
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updatedRecents = listOf(trimmed) + _uiState.value.recentSearches.filterNot { it.equals(trimmed, true) }
        _uiState.update {
            it.copy(
                searchQuery = trimmed,
                committedSearchQuery = trimmed,
                searchSuggestions = emptyList(),
                recentSearches = updatedRecents.take(10)
            )
        }
        viewModelScope.launch { prefsRepo.saveRecentSearches(updatedRecents.take(10)) }
        performSearch(trimmed, mode = _uiState.value.appMode, filter = _uiState.value.searchFilter)
    }

    fun setSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
        val active = _uiState.value.committedSearchQuery
        if (active.isNotBlank()) {
            performSearch(active, mode = _uiState.value.appMode, filter = filter)
        }
    }

    private fun performSearch(query: String, mode: AppMode, filter: SearchFilter) {
        searchJob?.cancel()
        _uiState.update { it.copy(searchState = FeedLoadState.Loading) }
        searchJob = viewModelScope.launch {
            val result = youtubeRepo.search(query, mode, filter)
            result.onSuccess { items ->
                _uiState.update { it.copy(searchState = FeedLoadState.Success(items)) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(searchState = FeedLoadState.Error(err.message ?: "Search failed"))
                }
            }
        }
    }

    fun removeRecentSearch(term: String) {
        val updated = _uiState.value.recentSearches.filterNot { it == term }
        _uiState.update { it.copy(recentSearches = updated) }
        viewModelScope.launch { prefsRepo.saveRecentSearches(updated) }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
        viewModelScope.launch { prefsRepo.saveRecentSearches(emptyList()) }
    }

    // Cloud-Synced Library & Playlists (Firestore)
    fun setLibraryFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(libraryFilter = filter) }
    }

    fun toggleLibrarySort() {
        _uiState.update { state ->
            val next = if (state.librarySort == LibrarySort.RECENT) LibrarySort.ALPHABETICAL else LibrarySort.RECENT
            state.copy(librarySort = next)
        }
    }

    fun toggleLike(item: YouTubeMediaItem) {
        val current = _uiState.value.likedItems
        val exists = current.any { it.videoId == item.videoId }
        val updated = if (exists) current.filterNot { it.videoId == item.videoId } else listOf(item) + current
        _uiState.update { it.copy(likedItems = updated) }
        viewModelScope.launch {
            if (exists) {
                cloudRepo.removeSavedItem(currentUserId, MediaBucket.LIKED, item.videoId)
            } else {
                cloudRepo.upsertSavedItem(currentUserId, MediaBucket.LIKED, item)
            }
        }
        showMessage(if (exists) "Removed from Liked" else "Saved to Liked")
    }

    fun toggleWatchLater(item: YouTubeMediaItem) {
        val current = _uiState.value.watchLaterItems
        val exists = current.any { it.videoId == item.videoId }
        val updated = if (exists) current.filterNot { it.videoId == item.videoId } else listOf(item) + current
        _uiState.update { it.copy(watchLaterItems = updated) }
        viewModelScope.launch {
            if (exists) {
                cloudRepo.removeSavedItem(currentUserId, MediaBucket.WATCH_LATER, item.videoId)
            } else {
                cloudRepo.upsertSavedItem(currentUserId, MediaBucket.WATCH_LATER, item)
            }
        }
        showMessage(if (exists) "Removed from Watch Later" else "Saved to Watch Later")
    }

    private fun recordHistory(item: YouTubeMediaItem) {
        val updated = (listOf(item) + _uiState.value.historyItems.filterNot { it.videoId == item.videoId }).take(40)
        _uiState.update { it.copy(historyItems = updated) }
        viewModelScope.launch {
            cloudRepo.upsertSavedItem(currentUserId, MediaBucket.HISTORY, item)
        }
    }

    fun createUserPlaylist(name: String, items: List<YouTubeMediaItem>) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val newId = "pl_${System.currentTimeMillis()}"
        val created = UserPlaylist(
            id = newId,
            name = clean,
            items = items,
            createdAt = System.currentTimeMillis(),
            source = "PHOTON"
        )
        val updated = listOf(created) + _uiState.value.userPlaylists
        _uiState.update { it.copy(userPlaylists = updated, isCreatePlaylistSheetOpen = false) }
        viewModelScope.launch {
            cloudRepo.upsertPlaylist(currentUserId, newId, clean, items, source = "PHOTON")
        }
        showMessage("Created \"$clean\"")
    }

    fun toggleItemInPlaylist(playlistId: String, item: YouTubeMediaItem) {
        var targetPlaylist: UserPlaylist? = null
        val updated = _uiState.value.userPlaylists.map { pl ->
            if (pl.id != playlistId) pl
            else {
                val exists = pl.items.any { it.videoId == item.videoId }
                val nextItems = if (exists) pl.items.filterNot { it.videoId == item.videoId } else pl.items + item
                pl.copy(items = nextItems).also { targetPlaylist = it }
            }
        }
        _uiState.update { it.copy(userPlaylists = updated) }
        targetPlaylist?.let { pl ->
            viewModelScope.launch {
                cloudRepo.upsertPlaylist(currentUserId, pl.id, pl.name, pl.items, source = pl.source)
            }
        }
    }

    fun resolvePlaylistItems(collectionId: String): Pair<String, List<YouTubeMediaItem>> {
        val state = _uiState.value
        return when (collectionId) {
            COLLECTION_LIKED -> "Liked" to state.likedItems
            COLLECTION_WATCH_LATER -> "Watch Later" to state.watchLaterItems
            COLLECTION_HISTORY -> "History" to state.historyItems
            else -> {
                val pl = state.userPlaylists.find { it.id == collectionId }
                (pl?.name ?: "Playlist") to (pl?.items ?: emptyList())
            }
        }
    }

    fun candidateItemsForPlaylistCreation(): List<YouTubeMediaItem> {
        val state = _uiState.value
        val combined = buildList {
            addAll(state.likedItems)
            addAll(state.watchLaterItems)
            addAll(state.historyItems)
            (state.youtubeFeedState as? FeedLoadState.Success)?.items?.let { addAll(it.take(8)) }
            (state.musicFeedState as? FeedLoadState.Success)?.items?.let { addAll(it.take(8)) }
        }
        return combined.distinctBy { it.videoId }
    }

    // Overlays & Sheets
    fun setPlayerExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isPlayerExpanded = expanded) }
    }

    fun setQueueSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isQueueSheetOpen = open) }
    }

    fun setSettingsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsSheetOpen = open) }
    }

    fun setCreatePlaylistSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isCreatePlaylistSheetOpen = open) }
    }

    fun openItemOptions(item: YouTubeMediaItem?) {
        _uiState.update { it.copy(itemForOptionsSheet = item) }
    }

    fun openPlaylistPicker(item: YouTubeMediaItem?) {
        _uiState.update { it.copy(itemForPlaylistPicker = item, itemForOptionsSheet = null) }
    }

    private fun showMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
        messageClearJob?.cancel()
        messageClearJob = viewModelScope.launch {
            delay(2200L)
            _uiState.update { it.copy(statusMessage = null) }
        }
    }

    companion object {
        private const val TAG = "PhotonViewModel"
        const val COLLECTION_LIKED = "liked"
        const val COLLECTION_WATCH_LATER = "watch_later"
        const val COLLECTION_HISTORY = "history"
    }
}
