package com.prism.music.photon

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.work.WorkInfo
import coil3.compose.AsyncImage
import com.prism.music.BuildConfig
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.PrismTheme
import com.prism.music.ui.theme.PrismTokens
import java.util.UUID

@UnstableApi
class PhotonActivity : ComponentActivity() {
    private val account get() = (application as PhotonApplication).account
    private var authGeneration = -1L
    private val googleAuthorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val generation = authGeneration
        if (!account.current(generation)) return@registerForActivityResult
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) account.fail(generation, "Google sign-in was canceled. No comment was submitted.")
        else try { acceptGoogle(generation, Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)) }
        catch (error: Exception) { account.fail(generation, googleError(error)) }
    }
    fun signInToYouTube() {
        val generation = account.begin() ?: return
        authGeneration = generation
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            account.fail(generation, "Native Google sign-in requires current Google Play services. Signed-out features still work.")
            return
        }
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(YOUTUBE_COMMENT_SCOPE))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (!account.current(generation)) return@addOnSuccessListener
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    account.fail(generation, "Sign-in was interrupted. Open the app and try again.")
                    return@addOnSuccessListener
                }
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) account.fail(generation, "Google did not provide an authorization screen. Try again.")
                    else try { googleAuthorization.launch(IntentSenderRequest.Builder(pending).build()) }
                    catch (_: Exception) { account.fail(generation, "Could not open Google's authorization screen.") }
                } else acceptGoogle(generation, result)
            }
            .addOnFailureListener { error -> account.fail(generation, googleError(error)) }
    }
    private fun acceptGoogle(generation: Long, result: AuthorizationResult) {
        lifecycleScope.launch {
            account.accept(generation, result.accessToken.orEmpty(), result.grantedScopes.map { it.toString() })
        }
    }
    private fun googleError(error: Exception): String = when ((error as? ApiException)?.statusCode) {
        10, 12500 -> "Google rejected this build's OAuth setup. Register this app's package and signing SHA-1 in Google Cloud and enable YouTube Data API v3. See Google sign-in setup."
        7 -> "Google authorization needs a network connection. Your comment draft has been kept."
        16, 12501 -> "Google authorization was canceled. No comment was submitted."
        else -> "Google authorization failed. Check Google Play services and the OAuth setup, then retry."
    }
    private var incoming by mutableStateOf<String?>(null)
    private var pip by mutableStateOf(false)
    private var lanAction: (() -> Unit)? = null
    private val lanPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lanAction?.invoke()
        lanAction = null
    }
    fun withLanPermission(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            lanAction = action
            lanPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
        } else action()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authGeneration = savedInstanceState?.getLong("photon_auth_generation", -1L) ?: -1L
        if (authGeneration >= 0 && !account.current(authGeneration)) authGeneration = -1L
        enableEdgeToEdge()
        accept(intent)
        setContent {
            val model: PhotonViewModel = viewModel()
            val library by model.library.collectAsStateWithLifecycle()
            val controller = rememberController()
            LaunchedEffect(incoming) {
                incoming?.let { model.search(it) }
                incoming = null
            }
            PrismTheme(AppSettings(themeMode = if (library.dark) ThemeMode.DARK else ThemeMode.LIGHT, accent = library.accent), null) {
                InterfaceTheme(library.youtubeLayout, library.dark) {
                    OfflineArtworkProvider(library.downloads) { PhotonRoot(model, controller, pip, ::requestNotifications, ::enterPip) }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("photon_auth_generation", authGeneration)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); accept(intent) }
    private fun accept(intent: Intent?) {
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent?.dataString
        incoming = text
    }
    private fun requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun enterPip() {
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip = isInPictureInPictureMode
    }
}

@Composable
@UnstableApi
private fun rememberController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PhotonPlaybackService::class.java))).buildAsync()
        future.addListener({ runCatching { controller = future.get() } }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }
    return controller
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UnstableApi
private fun PhotonRoot(model: PhotonViewModel, controller: MediaController?, pip: Boolean, notifications: () -> Unit, enterPip: () -> Unit) {
    val browse by model.browse.collectAsStateWithLifecycle()
    val watch by model.watch.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    val googleLikes by model.googleLikes.collectAsStateWithLifecycle()
    val googleSubscriptions by model.googleSubscriptions.collectAsStateWithLifecycle()
    val googleChange by model.googleChange.collectAsStateWithLifecycle()
    val work by model.work.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tabHistory by rememberSaveable { mutableStateOf(arrayListOf<Int>()) }
    var createPlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var collectionBackToPlayer by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    fun navigateTo(target: Int) {
        tabHistory = ArrayList(PhotonNavigation.record(tabHistory, tab, target))
        tab = target
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var syncRealPlays by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var localLibrary by rememberSaveable { mutableStateOf(false) }
    var qualitySheet by remember { mutableStateOf(false) }
    var downloadSheet by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playIntent by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<SavedDownload?>(null) }
    val collection by model.collection.collectAsStateWithLifecycle()
    var commentsSheet by remember { mutableStateOf(false) }
    var playlistSheet by remember { mutableStateOf(false) }
    var shortsMode by rememberSaveable { mutableStateOf(false) }
    var castSheet by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val immersive = PlayerLayoutPolicy.immersive(fullscreen, landscape, expanded, pip, shortsMode)
    fun leaveFullscreen() { fullscreen = false; if (landscape) expanded = false }
    LaunchedEffect(library.youtubeLayout) {
        if (!library.youtubeLayout) {
            if (tab == 4) tab = 1
            tabHistory = ArrayList(tabHistory.map { if (it == 4) 1 else it })
        }
    }
    LaunchedEffect(library.youtubeSessionRevision) { syncRealPlays = false }
    LaunchedEffect(library.youtubeHome, library.youtubeSessionRevision, library.youtubeSessionReady) { model.loadGoogleLikedIndex() }
    val context = LocalContext.current
    val activity = context as PhotonActivity
    val casting = (context.applicationContext as PhotonApplication).casting

    val wantsAwake = isPlaying || (buffering && playIntent)
    DisposableEffect(activity, wantsAwake) {
        fun updateAwake() {
            if (wantsAwake && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> updateAwake() }
        activity.lifecycle.addObserver(observer); updateAwake()
        onDispose { activity.lifecycle.removeObserver(observer); activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playIntent = playWhenReady }
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_ENDED) model.next() }
            override fun onPlayerError(error: PlaybackException) { playerError = "Playback failed: ${error.errorCodeName}. Try reopening the video or choosing another quality." }
        }
        controller?.addListener(listener)
        isPlaying = controller?.isPlaying == true
        playIntent = controller?.playWhenReady == true
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) {
        val item = controller?.currentMediaItem
        if (item != null && model.watch.value.video == null) {
            model.adoptPlaying(Video(item.mediaId, item.mediaMetadata.title?.toString() ?: "Playing video",
                item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString().orEmpty()))
        }
    }
    // Bind the retained view model's selection once; expanding/minimizing never restarts playback.
    LaunchedEffect(watch.request, controller) {
        if (watch.request == 0L || controller == null) return@LaunchedEffect
        val video = watch.video ?: return@LaunchedEffect
        val position = if (watch.preservePosition) controller.currentPosition else watch.startPositionMs
        val play = if (watch.preservePosition) controller.playWhenReady else true
        playerError = null
        controller.setMediaItem(playbackItem(video, watch.quality, watch.localPath, watch.details?.subtitles.orEmpty(), watch.openedAtMs, watch.webRevision), position)
        controller.prepare()
        controller.playWhenReady = play
    }
    LaunchedEffect(watch.video?.id) { if (watch.video != null) expanded = true }
    LaunchedEffect(watch.video?.id, watch.busy, controller) {
        if (watch.video != null && watch.busy) { controller?.stop(); controller?.clearMediaItems() }
    }
    // Physical rotation and the fullscreen button share exactly the same layout/insets path.
    DisposableEffect(fullscreen, watch.video?.short) {
        val previous = activity.requestedOrientation
        if (fullscreen) activity.requestedOrientation = if (watch.video?.short == true) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (fullscreen) activity.requestedOrientation = previous }
    }
    DisposableEffect(immersive) {
        val decor = activity.window.decorView
        val bars = WindowCompat.getInsetsController(activity.window, decor)
        val before = ViewCompat.getRootWindowInsets(decor)
        val behavior = bars.systemBarsBehavior
        if (immersive) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (immersive) {
                bars.systemBarsBehavior = behavior
                for (type in listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars())) {
                    if (before?.isVisible(type) != false) bars.show(type) else bars.hide(type)
                }
            }
        }
    }
    // Registered first: sheets, collections and the player take precedence over tab navigation.
    BackHandler(!pip) {
        val previous = PhotonNavigation.previous(tabHistory, tab)
        if (previous == null) confirmLeave = true
        else {
            tab = previous
            tabHistory = ArrayList(tabHistory.dropLast(1))
        }
    }
    fun closeCollection() {
        model.closeCollection()
        if (collectionBackToPlayer) expanded = true
        collectionBackToPlayer = false
    }
    BackHandler(shortsMode && !pip) { shortsMode = false; expanded = false }
    BackHandler(collection != null && !expanded && !pip && !shortsMode) { closeCollection() }
    BackHandler(expanded && !pip && !shortsMode) { if (immersive) leaveFullscreen() else expanded = false }

    fun open(source: Video) {
        val video = ShortsPresentation.merge(source, model.videoPresentation.value[source.id])
        fullscreen = false
        if (video.short) { shortsMode = true; model.presentShort(video) }
        else { expanded = true; model.open(video) }
    }
    fun openChannel(url: String) {
        if (url.isBlank()) return
        collectionBackToPlayer = false; expanded = false; model.openCollection(url, true)
    }
    fun backTab() {
        val previous = PhotonNavigation.previous(tabHistory, tab)
        if (previous == null) confirmLeave = true else { tab = previous; tabHistory = ArrayList(tabHistory.dropLast(1)) }
    }
    @Composable fun nativeNavigation(selected: Int) {
        YouTubeLayoutNavigation(selected,
            home = { shortsMode = false; expanded = false; navigateTo(0); model.home() },
            shorts = { model.shorts(); shortsMode = true },
            create = { createPlaylist = true },
            subscriptions = { shortsMode = false; expanded = false; model.subscriptionsFeed(); navigateTo(4) },
            you = { shortsMode = false; expanded = false; localLibrary = false; navigateTo(2) })
    }
    fun share(video: Video) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.url), "Share video"))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (pip) {
            VideoSurface(controller, Modifier.fillMaxSize(), controls = false)
        } else if (shortsMode) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { ShortsScreen(browse, watch, controller, model, fullscreen = { shortsMode = false; expanded = true; fullscreen = true }) { shortsMode = false; expanded = false } }
                if (library.youtubeLayout) nativeNavigation(5)
            }
        } else if (collection != null && !expanded) {
            CollectionScreen(model, library, { open(it) }, ::closeCollection)
        } else if (expanded && watch.video != null) {
            val video = watch.video!!
            Column(Modifier.fillMaxSize().background(if (immersive) Color.Black else MaterialTheme.colorScheme.background)
                .then(if (immersive) Modifier.safeDrawingPadding() else Modifier.statusBarsPadding())) {
                if (!immersive) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { expanded = false }) { Icon(PrismIcons.ChevronDown, "Minimize player") }
                    Text("NOW WATCHING", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                    IconButton(onClick = { controller?.stop(); controller?.clearMediaItems(); model.close(); expanded = false }) { Icon(PrismIcons.Close, "Close video") }
                }
                Box(if (immersive) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(VideoArtworkShape.ratio(video.short))) {
                    VideoSurface(controller, Modifier.fillMaxSize(), true)
                    if (watch.busy || buffering || controller == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    if (!immersive) IconButton(onClick = { fullscreen = true }, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
                        Icon(PrismIcons.Fullscreen, "Full screen", tint = Color.White)
                    }
                    if (immersive) IconButton(onClick = { leaveFullscreen() }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                        Icon(PrismIcons.FullscreenExit, "Exit full screen", tint = Color.White)
                    }
                }
                if (!immersive) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(PrismTokens.spectrum))
                            Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(listOf(video.channel, viewLabel(video.views), video.published).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            (watch.error ?: playerError)?.let { message ->
                                ErrorCard(message) { model.retryPlayback(video) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionChip(if ((googleLikes[video.id] == true)) "Unlike" else "Like", if (googleLikes[video.id] == true) PrismIcons.HeartFilled else PrismIcons.Heart) { model.save(video) }
                                if (watch.details != null) {
                                    ActionChip(watch.quality?.label ?: "Quality", PrismIcons.Settings) { qualitySheet = true }
                                    if (!watch.details!!.live) ActionChip("Download", PrismIcons.Download) { downloadSheet = true }
                                }
                                if (watch.details == null && watch.localPath == null && !watch.busy) ActionChip("Reload details", PrismIcons.Refresh) { model.open(video) }
                                watch.details?.channel?.let { channel ->
                                    ActionChip("Channel", PrismIcons.Person) { collectionBackToPlayer = true; expanded = false; model.openCollection(channel.url, true) }
                                    ActionChip(if (googleSubscriptions[channel.url.substringAfterLast('/')] == true) "Unsubscribe" else "Subscribe", PrismIcons.PersonAdd) { model.subscribe(channel) }
                                }
                                ActionChip("Comments", PrismIcons.Info) { model.loadComments(video); commentsSheet = true }
                                ActionChip("Cast", PrismIcons.Cast) { activity.withLanPermission { casting.discover(); castSheet = true } }
                                ActionChip("Save", PrismIcons.PlaylistAdd) { playlistSheet = true }
                                if (model.queue.value.isNotEmpty()) ActionChip("Next", PrismIcons.Next) { model.next() }
                                ActionChip("Share", PrismIcons.Share) { share(video) }
                                ActionChip("Full screen", PrismIcons.Fullscreen) { fullscreen = true }
                                ActionChip("PiP", PrismIcons.PlayCircle) { enterPip() }
                            }
                            if (watch.localPath != null) Text("Offline", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            if (watch.details?.description?.isNotBlank() == true) {
                                var description by remember(video.id) { mutableStateOf(false) }
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.clickable { description = !description }.padding(16.dp)) {
                                        Text(if (description) "Less" else "Description", fontWeight = FontWeight.SemiBold)
                                        Text(android.text.Html.fromHtml(watch.details!!.description, android.text.Html.FROM_HTML_MODE_COMPACT).toString(), maxLines = if (description) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (!watch.details?.related.isNullOrEmpty()) Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(watch.details?.related.orEmpty(), key = { it.id }) { related ->
                        VideoRow(related, { open(related) }, { model.save(related) }, (googleLikes[related.id] == true))
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Column {
                        watch.video?.let { video ->
                            Surface(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 4.dp) {
                                Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    VideoThumbnail(video, null, Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(if (watch.busy) "Opening…" else video.channel, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() }) { Icon(if (isPlaying) PrismIcons.Pause else PrismIcons.Play, if (isPlaying) "Pause" else "Play") }
                                    IconButton(onClick = { controller?.stop(); controller?.clearMediaItems(); model.close() }) { Icon(PrismIcons.Close, "Stop and close") }
                                }
                            }
                        }
                        if (library.youtubeLayout) nativeNavigation(tab) else NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                            listOf("Home" to PrismIcons.Home, "Search" to PrismIcons.Search, "Library" to PrismIcons.Library, "Settings" to PrismIcons.Settings).forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(selected = tab == index, onClick = { navigateTo(index); if (index == 0) model.home() }, icon = { Icon(icon, label) }, label = { Text(label) })
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).then(if (library.youtubeLayout) Modifier else Modifier.background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.055f), Color.Transparent))))) {
                    when (tab) {
                        0 -> if (library.youtubeLayout) YouTubeLayoutHome(model, library, ::open,
                            search = { navigateTo(1) }, settings = { navigateTo(3) },
                            cast = { activity.withLanPermission { casting.discover(); castSheet = true } },
                            topic = { search = it; model.search(it); navigateTo(1) },
                            shorts = { model.shorts(); shortsMode = true }, channel = ::openChannel)
                        else PhotonHome(model, library,
                            open = { open(it) },
                            offline = { expanded = true; model.offline(it) },
                            discover = { model.search(it); navigateTo(1) },
                            forYou = { model.home(); navigateTo(1) },
                            shorts = { model.shorts(); shortsMode = true },
                            subscriptions = { if (library.youtubeHome) { localLibrary = false; model.openAccountShelf(AccountShelf.CHANNELS); navigateTo(2) } else { model.subscriptionsFeed(); navigateTo(1) } },
                            downloads = { localLibrary = true; navigateTo(2); libraryTab = 2 },
                            saved = { localLibrary = false; navigateTo(2); libraryTab = 0 },
                            settings = { navigateTo(3) }, search = { navigateTo(1) })
                        1 -> if (library.youtubeLayout) YouTubeLayoutSearch(model, browse, search, { search = it }, { model.search(search) }, ::backTab, ::open, ::openChannel)
                        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                            item {
                                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (tab == 0) "YOUR WORLD, REFRACTED" else "FIND YOUR NEXT WATCH", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(if (tab == 0) "Photon" else "Search", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, onClick = { navigateTo(3) }) {
                                            Icon(PrismIcons.Play, "Settings", Modifier.padding(15.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Box(Modifier.width(64.dp).height(3.dp).clip(CircleShape).background(PrismTokens.spectrum))
                                    OutlinedTextField(
                                        value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Search or paste video/channel/playlist link") }, singleLine = true, shape = RoundedCornerShape(22.dp),
                                        leadingIcon = { Icon(PrismIcons.Search, null) },
                                        trailingIcon = { IconButton(onClick = { model.search(search); navigateTo(1) }) { Icon(PrismIcons.ArrowForward, "Search") } },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { model.search(search); navigateTo(1) })
                                    )
                                    if (tab == 0) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(onClick = model::home, label = { Text("For you") })
                                        AssistChip(onClick = model::subscriptionsFeed, label = { Text("Subscriptions") })
                                        AssistChip(onClick = { model.shorts(); shortsMode = true }, label = { Text("Shorts") })
                                    }
                                    if (tab == 0) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Explore" to "science and technology", "Technology" to "technology", "Gaming" to "gaming", "Learning" to "educational documentaries", "Travel" to "travel films", "Cooking" to "cooking recipes").forEach { (label, query) ->
                                            FilterChip(selected = browse.query == query, onClick = { model.search(query) }, label = { Text(label) })
                                        }
                                    }
                                    Text(if (tab == 0) "A little curiosity goes a long way." else "Videos, creators, and new rabbit holes.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    Text(if (tab == 0) (if (browse.personalized) "For you · based on your watching" else browse.query.replaceFirstChar { it.uppercase() }) else "Results for “${browse.query}”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (browse.busy) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                            browse.error?.let { error -> item { Box(Modifier.padding(22.dp)) { ErrorCard(error) { model.search(browse.query) } } } }
                            if (!browse.busy && browse.error == null && browse.videos.isEmpty()) item { EmptyState("No videos found", "Try a different search or paste a YouTube video link.", PrismIcons.Search) }
                            items(browse.videos, key = { it.id }) { video ->
                                if (tab == 0) VideoCard(video, { open(video) }, { model.save(video) }, (googleLikes[video.id] == true))
                                else VideoRow(video, { open(video) }, { model.save(video) }, (googleLikes[video.id] == true))
                            }
                            if (browse.hasMore) item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    if (browse.moreBusy) CircularProgressIndicator() else OutlinedButton(onClick = model::loadMore) { Text("Load more videos") }
                                }
                            }
                        }
                        2 -> Column(Modifier.fillMaxSize()) {
                            if (library.youtubeLayout) YouTubeLayoutHeader("You", search = { navigateTo(1) }, settings = { navigateTo(3) })
                            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !localLibrary, onClick = { localLibrary = false }, label = { Text("YouTube account") })
                                FilterChip(selected = localLibrary, onClick = { localLibrary = true }, label = { Text("Downloads") })
                            }
                            if (!localLibrary) {
                                if (library.youtubeHome) AccountLibraryScreen(model, library.youtubeSessionRevision, library.youtubeSessionReady,
                                    open = { open(it) }, channel = ::openChannel)
                                else Column { PageHeading("Your YouTube library", "Sign in with Google. No local-only library is used."); YouTubeHomeConnection(model) }
                            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(work.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }, key = { it.id.toString() }) { info ->
                                    DownloadJob(info, { if (info.state.isFinished) model.dismissFinishedDownloads() else model.cancel(info.id) })
                                }
                                if (library.downloads.isEmpty() && work.none { !it.state.isFinished }) item { EmptyState("Ready when you are", "Open a video and tap Download. Available HD qualities include sound, merged on your phone.", PrismIcons.Download) }
                                items(library.downloads, key = { it.path }) { download ->
                                    Column {
                                        VideoRow(download.video, { expanded = true; model.offline(download) }, { delete = download }, false, PrismIcons.Delete)
                                        Text("${download.height}p · ${download.bytes / 1024 / 1024} MB · available offline", Modifier.padding(start = 22.dp, bottom = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        3 -> SettingsPage(library, model, notifications)
                        4 -> LazyColumn(Modifier.fillMaxSize()) {
                            item { YouTubeLayoutHeader("Subscriptions", search = { navigateTo(1) }, settings = { navigateTo(3) }) }
                            item { Row {
                                TextButton(onClick = model::subscriptionsFeed) { Text("Refresh") }
                                TextButton(onClick = { model.openAccountShelf(AccountShelf.CHANNELS); localLibrary = false; navigateTo(2) }) { Text("All channels") }
                            } }
                            if (browse.query == "Google subscriptions") {
                                if (browse.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                browse.error?.let { error -> item { Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
                                items(browse.videos, key = { it.id }) { video -> YouTubeLayoutVideo(video, { open(video) }, { model.save(video) }, { openChannel(video.channelUrl) }) }
                            } else item { TextButton(onClick = model::subscriptionsFeed) { Text("Load subscribed videos") } }
                        }
                    }
                }
            }
        }
    }
    if (googleChange.pending != null || googleChange.message != null) AlertDialog(
        onDismissRequest = model::dismissGoogleChange,
        title = { Text(if (googleChange.pending == null) "YouTube account result" else "Change your YouTube account?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            googleChange.pending?.let { change ->
                val verb = when (change.kind) {
                    GoogleChangeKind.LIKE -> if (change.desired == false) "Remove YouTube like" else "Like"
                    GoogleChangeKind.SUBSCRIBE -> if (change.desired == false) "Unsubscribe" else "Subscribe"
                    else -> change.kind.name.lowercase().replace('_', ' ')
                }
                Text("$verb: ${change.title}")
                if (change.kind == GoogleChangeKind.LIKE && change.desired == false) Text("This removes the video from your YouTube Liked videos. Nothing changes until you confirm.")
                if (change.kind == GoogleChangeKind.DELETE_PLAYLIST) Text("Permanently deletes this selected YouTube playlist. The videos themselves are not deleted. This cannot be undone.", color = MaterialTheme.colorScheme.error)
                if (change.id.isNotBlank()) Text("Target: ${change.id}", style = MaterialTheme.typography.bodySmall)
                Text("This changes the Google account connected to Home, not a local list or the separate comment account. Likes/subscriptions are read before changing and verified afterward. New playlists are private. A failed/ambiguous request is not automatically retried.")
            }
            if (googleChange.pending != null && !library.accountHistorySync) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = syncRealPlays, enabled = !googleChange.busy, onCheckedChange = { syncRealPlays = it })
                Text("Also report real native plays to YouTube history including short plays after at least 1 actual playing second. Google paused/unknown state blocks reporting. Enabling removes the extra local playback pause.", style = MaterialTheme.typography.bodySmall)
            }
            googleChange.message?.let { Text(it) }
            if (googleChange.busy) LinearProgressIndicator()
        } },
        confirmButton = { TextButton(enabled = !googleChange.busy, onClick = { if (googleChange.pending == null) model.dismissGoogleChange() else { if (syncRealPlays) model.includeShortPlays(true); model.confirmGoogleChange() } }) { Text(if (googleChange.pending == null) "Close" else "Confirm on YouTube") } },
        dismissButton = { if (googleChange.pending != null) TextButton(enabled = !googleChange.busy, onClick = model::dismissGoogleChange) { Text("Cancel") } }
    )
    if (castSheet) ModalBottomSheet(onDismissRequest = { castSheet = false; casting.stopDiscovery() }) {
        DisposableEffect(Unit) { onDispose { casting.stopDiscovery() } }
        CastPanel(casting, watch) { controller?.pause() }
    }
    if (commentsSheet && watch.video != null) ModalBottomSheet(onDismissRequest = { commentsSheet = false }) {
        CommentsPanel(model, watch.video!!)
    }
    if (playlistSheet && watch.video != null) ModalBottomSheet(onDismissRequest = { playlistSheet = false }) {
        PlaylistPicker(library, watch.video!!, model) { playlistSheet = false }
    }
    if (qualitySheet || downloadSheet) {
        ModalBottomSheet(onDismissRequest = { qualitySheet = false; downloadSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (downloadSheet) "Take it with you" else "Video quality", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (downloadSheet) "Only download content you own or have permission to save. HD video and audio are merged on your phone." else "Choose a source quality. Switching keeps your place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                watch.details?.qualities?.filter { !downloadSheet || it.downloadable }?.forEach { quality ->
                    ListItem(
                        headlineContent = { Text(quality.label, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(if (quality.height >= 720) "HD · video + audio" else if (quality.height == 0) "Live stream" else "Video + audio") },
                        trailingContent = { Icon(if (downloadSheet) PrismIcons.Download else if (watch.quality == quality) PrismIcons.Check else PrismIcons.Play, null) },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            if (downloadSheet) {
                                notifications()
                                watch.video?.let { model.download(it, quality) }
                                navigateTo(2); libraryTab = 2; expanded = false
                            } else model.quality(quality)
                            qualitySheet = false; downloadSheet = false
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (createPlaylist) AlertDialog(onDismissRequest = { createPlaylist = false }, title = { Text("Create private playlist") },
        text = { OutlinedTextField(playlistName, { playlistName = it.take(100) }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { model.createGooglePlaylist(playlistName.trim()); createPlaylist = false; playlistName = "" }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("Cancel") } })
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        title = { Text(if (library.youtubeLayout) "Leave app?" else "Leave Photon?") },
        text = { Text("You are already on the first screen. Leave the app in the background? Playback follows your background-playback setting.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; activity.moveTaskToBack(true) }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } })
    delete?.let { download ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete downloaded video?") }, text = { Text("This removes the ${download.height}p offline copy of “${download.video.title}” from this device.") },
            confirmButton = { TextButton(onClick = { model.remove(download); delete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep") } })
    }
}

@Composable
@UnstableApi
internal fun VideoSurface(controller: MediaController?, modifier: Modifier, controls: Boolean) {
    val youtubeLayout = LocalYouTubeLayout.current
    AndroidView(factory = { context -> PhotonPlayerView(context).apply { player = controller; useController = controls; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; setShowSubtitleButton(true); setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        if (youtubeLayout) findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setPlayedColor(android.graphics.Color.rgb(255, 0, 51))
    } },
        modifier = modifier.background(Color.Black),
        update = { it.player = controller; it.useController = controls; it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT },
        onRelease = { it.player = null })
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = if (icon == PrismIcons.HeartFilled) Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurface) }, shape = CircleShape)
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Box(Modifier.width(58.dp).height(3.dp).clip(CircleShape).background(PrismTokens.spectrum))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VideoCard(source: Video, open: () -> Unit, save: () -> Unit, saved: Boolean) {
    val video = resolvedVideo(source)
    Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
        Box((if (video.short) Modifier.width(160.dp) else Modifier.fillMaxWidth()).aspectRatio(VideoArtworkShape.ratio(video.short)).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = open)) {
            VideoThumbnail(video, video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), shape = RoundedCornerShape(7.dp), color = Color.Black.copy(alpha = 0.8f)) {
                Text(durationLabel(video.duration), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable(onClick = open)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(listOf(video.channel, viewLabel(video.views)).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = save, modifier = Modifier.size(36.dp)) { Icon(if (saved) PrismIcons.HeartFilled else PrismIcons.Heart, if (saved) "Unsave video" else "Save video", Modifier.size(20.dp), tint = if (saved) Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun VideoRow(source: Video, open: () -> Unit, save: () -> Unit, saved: Boolean, actionIcon: ImageVector? = null) {
    val video = resolvedVideo(source)
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(VideoArtworkShape.rowWidth(video.short).dp).aspectRatio(VideoArtworkShape.ratio(video.short)).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            VideoThumbnail(video, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(durationLabel(video.duration), Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.8f)).padding(3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionIcon != null) VideoHeart(video)
        IconButton(onClick = save, modifier = Modifier.size(34.dp)) { Icon(actionIcon ?: if (saved) PrismIcons.HeartFilled else PrismIcons.Heart, if (actionIcon != null) "Delete download" else if (saved) "Unsave video" else "Save video", Modifier.size(18.dp), tint = if (actionIcon == null && saved) Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun EmptyState(title: String, message: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(22.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = retry) { Icon(PrismIcons.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Try again") }
        }
    }
}

@Composable
private fun DownloadJob(info: WorkInfo, cancel: () -> Unit) {
    Surface(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = info.progress.getString("title") ?: info.outputData.getString("title")
            title?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (info.state) {
                    WorkInfo.State.ENQUEUED -> "Queued · waiting for allowed network"
                    WorkInfo.State.RUNNING -> info.progress.getString("stage") ?: "Preparing video…"
                    WorkInfo.State.FAILED -> "Download failed"
                    else -> info.state.name
                }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (!info.state.isFinished) IconButton(onClick = cancel) { Icon(PrismIcons.Close, "Cancel download") }
                else TextButton(onClick = cancel) { Text("Dismiss") }
            }
            if (info.state == WorkInfo.State.RUNNING) LinearProgressIndicator(progress = { info.progress.getInt("percent", 0) / 100f }, modifier = Modifier.fillMaxWidth())
            info.outputData.getString("error")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SettingsPage(library: PhotonLibrary, model: PhotonViewModel, notifications: () -> Unit) {
    val context = LocalContext.current
    var clear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    fun link(url: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Settings", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall) }
        item {
            ListItem(headlineContent = { Text(if (library.youtubeLayout) "Switch to Photon" else "Switch to YouTube layout") },
                supportingContent = { Text(if (library.youtubeLayout) "YouTube layout" else "Prism layout") },
                trailingContent = { Switch(library.youtubeLayout, model::youtubeLayout) },
                modifier = Modifier.clickable { model.youtubeLayout(!library.youtubeLayout) })
        }
        item { YouTubeAccountCard(model) }
        item {
            SettingsGroup("APPEARANCE") {
                if (!library.youtubeLayout) Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.prism.music.ui.theme.AccentSwatches.take(8).forEach { color ->
                        val argb = color.toArgb()
                        Surface(onClick = { model.accent(argb) }, shape = CircleShape, color = color, modifier = Modifier.size(34.dp)) {
                            if (library.accent == argb) Icon(PrismIcons.Check, "Selected accent", Modifier.padding(8.dp), tint = Color.White)
                        }
                    }
                }
                ListItem(headlineContent = { Text("Dark appearance") }, supportingContent = { Text(if (library.youtubeLayout) "Use dark theme" else "Soft surfaces and Prism's spectrum accents") }, leadingContent = { Icon(PrismIcons.Moon, null) }, trailingContent = { Switch(library.dark, model::dark) })
            }
        }
        item {
            SettingsGroup("PLAYBACK & DOWNLOADS") {
                ListItem(headlineContent = { Text("Download on Wi-Fi only") }, supportingContent = { Text("Uses unmetered networks; turn off to use mobile data") }, leadingContent = { Icon(PrismIcons.Download, null) }, trailingContent = { Switch(library.wifiOnly, model::wifi) })
                Text("Preferred playback quality", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(360, 720, 1080, 2160).forEach { height -> FilterChip(selected = library.defaultHeight == height, onClick = { model.maxHeight(height) }, label = { Text("${height}p") }) }
                }
                ListItem(headlineContent = { Text("Save offline thumbnails") }, supportingContent = { Text("Repair thumbnails for existing downloads; follows the Wi-Fi-only setting") }, leadingContent = { Icon(PrismIcons.Download, null) }, modifier = Modifier.clickable(onClick = model::repairArtwork))
                ListItem(headlineContent = { Text("Enable notifications") }, supportingContent = { Text("Playback controls and download progress") }, leadingContent = { Icon(PrismIcons.PlayCircle, null) }, modifier = Modifier.clickable(onClick = notifications))
            }
        }
        item {
            SettingsGroup("YOUR DATA") {
                ListItem(headlineContent = { Text("Clear device playback cache") }, supportingContent = { Text("Does not delete Google watch history, likes or playlists") }, leadingContent = { Icon(PrismIcons.History, null) }, modifier = Modifier.clickable { clear = true })
            }
        }
        item { ListItem(headlineContent = { Text("About & help") }, trailingContent = { Icon(PrismIcons.Info, null) }, modifier = Modifier.clickable { about = !about }) }
        if (about) item {
            SettingsGroup(if (library.youtubeLayout) "ABOUT" else "ABOUT PHOTON") {
                ListItem(headlineContent = { Text(if (library.youtubeLayout) "Version ${BuildConfig.VERSION_NAME}" else "Photon ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text("Early Android preview · not affiliated with Google or YouTube") }, leadingContent = { Icon(PrismIcons.Play, null) })
                ListItem(headlineContent = { Text("Share device test report") }, supportingContent = { Text("Device/API, app version and URL-free playback events. Review before sending.") }, leadingContent = { Icon(PrismIcons.Share, null) }, modifier = Modifier.clickable {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PhotonDiagnostics.report()), "Share test report"))
                })
                ListItem(headlineContent = { Text("Google account on YouTube (browser)") }, supportingContent = { Text("Opens official YouTube. This does not connect the in-app account.") }, trailingContent = { Icon(PrismIcons.OpenInNew, null) }, modifier = Modifier.clickable { context.startActivity(browserIntent("https://www.youtube.com/")) })
                ListItem(headlineContent = { Text("Source code & issues") }, supportingContent = { Text(if (library.youtubeLayout) "GPL-3.0-or-later" else "HayMayia/Photon · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/HayMayia/Photon") })
                ListItem(headlineContent = { Text(if (library.youtubeLayout) "Upstream foundation by Yigder" else "Built on Prism by Yigder") }, supportingContent = { Text("Original design, icons, typography and Android foundation · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/Yigder/Prism") })
                ListItem(headlineContent = { Text("NewPipe Extractor") }, supportingContent = { Text("YouTube extraction · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/TeamNewPipe/NewPipeExtractor") })
                Text("Download only when you have permission. No DRM, paid-access, age-check or sign-in bypass. YouTube changes may temporarily break extraction. Likes, subscriptions and supported playlist changes use the connected Google account. Comment permission is separate. Search history and offline watch-history transfer remain unsupported.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear device playback cache?") }, text = { Text("This clears only cached device history/resume positions, not your Google activity.") }, confirmButton = { TextButton(onClick = { model.clearHistory(); clear = false }) { Text("Clear history") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(if (LocalYouTubeLayout.current) 8.dp else 22.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
    }
}
