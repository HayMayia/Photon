package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogleAccount
import com.example.data.PhotonCloudRepository
import com.example.data.PhotonPreferencesRepository
import com.example.data.PlayerDisplayMode
import com.example.player.YouTubePlayerController
import com.example.player.YouTubePlayerListener
import com.example.ui.components.PhotonBottomNavBar
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonDestination
import com.example.ui.components.PhotonMiniPlayer
import com.example.ui.components.PhotonNavRail
import com.example.ui.screens.AddToPlaylistBottomSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.CreatePlaylistBottomSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ItemOptionsBottomSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaybackSettingsBottomSheet
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.YouTubeAccountSyncSheet
import com.example.ui.screens.YouTubeWebSyncDialog
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSurfaceHigh
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

const val ROUTE_HOME = "home"
const val ROUTE_SEARCH = "search"
const val ROUTE_LIBRARY = "library"
const val ROUTE_COLLECTION = "collection/{collectionId}"

@Composable
fun PhotonApp() {
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(currentUser!!.uid) {
            PhotonAuthenticatedApp(user = currentUser!!)
        }
    }
}

@Composable
private fun PhotonAuthenticatedApp(user: FirebaseUser) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PhotonViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(
                    appContext.getString(R.string.firestore_database_id)
                )
                PhotonViewModel(
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName.orEmpty(),
                    initialEmail = user.email.orEmpty(),
                    initialPhotoUrl = user.photoUrl?.toString().orEmpty(),
                    cloudRepo = PhotonCloudRepository(db),
                    prefsRepo = PhotonPreferencesRepository(appContext)
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerController = remember(context, user.uid) {
        YouTubePlayerController(
            context = context,
            listener = object : YouTubePlayerListener {
                override fun onPlayerReady() = viewModel.onPlayerReady()
                override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean, ended: Boolean) =
                    viewModel.onPlaybackStateChanged(isPlaying, isBuffering, ended)

                override fun onProgressUpdate(currentSec: Int, durationSec: Int) =
                    viewModel.onProgressUpdate(currentSec, durationSec)

                override fun onPlayerError(message: String) = viewModel.onPlayerError(message)
            }
        )
    }

    DisposableEffect(playerController) {
        onDispose {
            playerController.destroy()
        }
    }

    LaunchedEffect(playerController, viewModel) {
        viewModel.playerCommands.collect { command ->
            when (command) {
                is PlayerCommand.LoadVideo -> playerController.loadVideo(command.videoId, command.autoplay)
                PlayerCommand.Play -> playerController.play()
                PlayerCommand.Pause -> playerController.pause()
                is PlayerCommand.SeekTo -> playerController.seekTo(command.seconds)
                is PlayerCommand.SetRate -> playerController.setPlaybackRate(command.rate)
            }
        }
    }

    var videoSlotBounds by remember { mutableStateOf<Rect?>(null) }

    val activeTabRoute = when (currentRoute) {
        ROUTE_HOME -> ROUTE_HOME
        ROUTE_SEARCH -> ROUTE_SEARCH
        ROUTE_LIBRARY -> ROUTE_LIBRARY
        else -> null
    }

    val navigateToTab: (PhotonDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openCollection: (String) -> Unit = { collectionId ->
        navController.navigate("collection/$collectionId") {
            launchSingleTop = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhotonCanvas)
    ) {
        // Persistent full-viewport-sized WebView (`m.youtube.com/watch`):
        // - In Video Mode: elevated cleanly above NowPlayingScreen at exact 16:9 viewport bounds.
        // - In Audio Mode / Mini-Player: tucked behind the opaque #000000 canvas at full 360x202dp
        //   so m.youtube.com always initializes and plays songs & videos reliably with zero stalls!
        val showVideoSurface = state.isPlayerExpanded &&
            state.currentItem != null &&
            state.playerDisplayMode == PlayerDisplayMode.VIDEO &&
            videoSlotBounds != null &&
            videoSlotBounds!!.width > 10f &&
            videoSlotBounds!!.height > 10f

        val webViewModifier = if (showVideoSurface) {
            val rect = videoSlotBounds!!
            val widthDp = with(density) { rect.width.toDp() }
            val heightDp = with(density) { rect.height.toDp() }
            Modifier
                .zIndex(2f)
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
                .clip(PhotonCardShape)
        } else {
            Modifier
                .zIndex(-1f)
                .size(width = 360.dp, height = 202.dp)
        }

        AndroidView(
            factory = { playerController.webView },
            modifier = webViewModifier
        )

        // Main AMOLED App Shell (opaque #000000 at zIndex 0f hides WebView when in Audio/Mini-Player mode)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .background(PhotonCanvas)
        ) {
            val isExpandedWidth = maxWidth >= 600.dp
            val hasActiveMedia = state.currentItem != null
            val contentBottomPadding = when {
                isExpandedWidth && hasActiveMedia -> 104.dp
                isExpandedWidth -> 28.dp
                hasActiveMedia -> 160.dp
                else -> 88.dp
            }

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedWidth) {
                    PhotonNavRail(
                        currentRoute = activeTabRoute,
                        onNavigate = navigateToTab
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(140)) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                state = state,
                                isExpandedWidth = isExpandedWidth,
                                bottomContentPadding = contentBottomPadding,
                                onSelectMode = viewModel::setAppMode,
                                onSelectYouTubeCategory = viewModel::selectYouTubeCategory,
                                onSelectMusicCategory = viewModel::selectMusicCategory,
                                onRefresh = viewModel::refreshCurrentFeed,
                                onPlayItem = { item, list -> viewModel.playItem(item, list) },
                                onOpenItemOptions = viewModel::openItemOptions,
                                onOpenSettings = { viewModel.setSettingsSheetOpen(true) },
                                onOpenAccountSync = { viewModel.setAccountSyncSheetOpen(true) }
                            )
                        }

                        composable(ROUTE_SEARCH) {
                            SearchScreen(
                                state = state,
                                isExpandedWidth = isExpandedWidth,
                                bottomContentPadding = contentBottomPadding,
                                onSelectMode = viewModel::setAppMode,
                                onQueryChange = viewModel::onSearchQueryChanged,
                                onSubmitQuery = viewModel::submitSearch,
                                onRemoveRecent = viewModel::removeRecentSearch,
                                onClearRecent = viewModel::clearRecentSearches,
                                onSelectFilter = viewModel::setSearchFilter,
                                onPlayItem = { item, list -> viewModel.playItem(item, list) },
                                onOpenItemOptions = viewModel::openItemOptions
                            )
                        }

                        composable(ROUTE_LIBRARY) {
                            LibraryScreen(
                                state = state,
                                bottomContentPadding = contentBottomPadding,
                                onSelectFilter = viewModel::setLibraryFilter,
                                onToggleSort = viewModel::toggleLibrarySort,
                                onOpenCreatePlaylist = { viewModel.setCreatePlaylistSheetOpen(true) },
                                onOpenCollection = openCollection,
                                onPlayItem = { item, list -> viewModel.playItem(item, list) },
                                onOpenItemOptions = viewModel::openItemOptions
                            )
                        }

                        composable(
                            route = ROUTE_COLLECTION,
                            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                        ) { entry ->
                            val collectionId = entry.arguments?.getString("collectionId").orEmpty()
                            val (title, items) = viewModel.resolvePlaylistItems(collectionId)
                            CollectionDetailScreen(
                                title = title,
                                items = items,
                                state = state,
                                bottomContentPadding = contentBottomPadding,
                                onBack = { navController.popBackStack() },
                                onPlayAll = { viewModel.playList(items, shuffle = false) },
                                onShuffleAll = { viewModel.playList(items, shuffle = true) },
                                onPlayItem = { item, list -> viewModel.playItem(item, list) },
                                onOpenItemOptions = viewModel::openItemOptions
                            )
                        }
                    }

                    // Persistent Mini-Player + Bottom Navigation Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedVisibility(
                            visible = state.statusMessage != null && !state.isPlayerExpanded,
                            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
                        ) {
                            state.statusMessage?.let { msg ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .widthIn(max = 480.dp)
                                        .clip(PhotonCardShape)
                                        .background(PhotonSurfaceHigh)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = msg,
                                        style = PhotonBodyStyle,
                                        color = PhotonPrimaryText
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = state.currentItem != null,
                            enter = slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetY = { it }
                            ) + fadeIn(tween(200)),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150))
                        ) {
                            state.currentItem?.let { current ->
                                // Isolated progress collection so ONLY the mini-player recomposes on ticks
                                val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
                                PhotonMiniPlayer(
                                    item = current,
                                    isPlaying = state.isPlaying,
                                    isBuffering = state.isBuffering,
                                    progress = progress,
                                    onTogglePlay = viewModel::togglePlayPause,
                                    onExpand = { viewModel.setPlayerExpanded(true) },
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .then(
                                            if (isExpandedWidth) {
                                                Modifier
                                                    .navigationBarsPadding()
                                                    .padding(bottom = 16.dp)
                                            } else {
                                                Modifier.padding(bottom = 8.dp)
                                            }
                                        )
                                )
                            }
                        }

                        if (!isExpandedWidth) {
                            PhotonBottomNavBar(
                                currentRoute = activeTabRoute,
                                onNavigate = navigateToTab
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Watch / Now Playing Overlay (smooth spring expand/collapse at zIndex 1f)
        AnimatedVisibility(
            visible = state.isPlayerExpanded && state.currentItem != null,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 380f
                ),
                initialOffsetY = { it }
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { it }
            ) + fadeOut(tween(180)),
            modifier = Modifier.zIndex(1f)
        ) {
            val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
            NowPlayingScreen(
                state = state,
                progress = progress,
                onCollapse = { viewModel.setPlayerExpanded(false) },
                onSelectDisplayMode = viewModel::setPlayerDisplayMode,
                onVideoSlotPositioned = { bounds -> videoSlotBounds = bounds },
                onTogglePlay = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onSeekFraction = viewModel::seekToFraction,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleLike = { state.currentItem?.let(viewModel::toggleLike) },
                onToggleWatchLater = { state.currentItem?.let(viewModel::toggleWatchLater) },
                onOpenPlaylistPicker = { state.currentItem?.let(viewModel::openPlaylistPicker) },
                onCycleSpeed = viewModel::cyclePlaybackSpeed,
                onOpenQueue = { viewModel.setQueueSheetOpen(true) },
                onOpenSettings = { viewModel.setSettingsSheetOpen(true) },
                onPlayRelated = { related ->
                    viewModel.playItem(
                        item = related,
                        contextList = listOf(related) + state.relatedVideos.filter { it.videoId != related.videoId },
                        expandPlayer = true
                    )
                },
                onOpenItemOptions = viewModel::openItemOptions
            )
        }

        // Modal Bottom Sheets
        if (state.isQueueSheetOpen) {
            QueueBottomSheet(
                state = state,
                onDismiss = { viewModel.setQueueSheetOpen(false) },
                onJumpToIndex = viewModel::jumpToQueueIndex,
                onMoveUp = viewModel::moveQueueItemUp,
                onRemoveIndex = viewModel::removeFromQueue,
                onClearUpcoming = viewModel::clearUpcomingQueue
            )
        }

        state.itemForOptionsSheet?.let { selected ->
            ItemOptionsBottomSheet(
                item = selected,
                isLiked = selected.videoId in state.likedVideoIds,
                isWatchLater = selected.videoId in state.watchLaterVideoIds,
                onDismiss = { viewModel.openItemOptions(null) },
                onPlayNext = { viewModel.playNextInQueue(selected) },
                onAddToQueue = { viewModel.addToQueue(selected) },
                onToggleLike = { viewModel.toggleLike(selected) },
                onToggleWatchLater = { viewModel.toggleWatchLater(selected) },
                onAddToPlaylist = { viewModel.openPlaylistPicker(selected) }
            )
        }

        state.itemForPlaylistPicker?.let { itemToPick ->
            AddToPlaylistBottomSheet(
                item = itemToPick,
                playlists = state.userPlaylists,
                onDismiss = { viewModel.openPlaylistPicker(null) },
                onTogglePlaylist = { plId -> viewModel.toggleItemInPlaylist(plId, itemToPick) },
                onCreateNewPlaylist = { viewModel.setCreatePlaylistSheetOpen(true) }
            )
        }

        if (state.isCreatePlaylistSheetOpen) {
            CreatePlaylistBottomSheet(
                candidateItems = viewModel.candidateItemsForPlaylistCreation(),
                onDismiss = { viewModel.setCreatePlaylistSheetOpen(false) },
                onCreate = viewModel::createUserPlaylist
            )
        }

        if (state.isSettingsSheetOpen) {
            PlaybackSettingsBottomSheet(
                state = state,
                onDismiss = { viewModel.setSettingsSheetOpen(false) },
                onSelectMode = viewModel::setAppMode,
                onSelectSleepTimer = viewModel::setSleepTimer,
                onToggleAutoplay = viewModel::setAutoplayEnabled
            )
        }

        if (state.isAccountSyncSheetOpen) {
            YouTubeAccountSyncSheet(
                profile = state.userProfile,
                isSyncing = state.isSyncingYouTube,
                syncStatusText = state.syncStatusText,
                onDismiss = { viewModel.setAccountSyncSheetOpen(false) },
                onOpenLiveWebSync = { viewModel.setYouTubeWebSyncOpen(true) },
                onResyncExistingSession = viewModel::resyncLiveYouTubeSession,
                onSavePersonalization = viewModel::savePersonalization,
                onSignOut = {
                    viewModel.setAccountSyncSheetOpen(false)
                    playerController.pause()
                    signOutGoogleAccount(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = { viewModel.clearLocalSessionBeforeSignOut() }
                    )
                }
            )
        }

        if (state.isYouTubeWebSyncOpen) {
            YouTubeWebSyncDialog(
                onCookiesCaptured = viewModel::onLiveYouTubeCookiesCaptured,
                onDismiss = { viewModel.setYouTubeWebSyncOpen(false) }
            )
        }
    }
}
