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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
            account.fail(generation, "Native Google sign-in requires current Google Play services. Signed-out Photon features still work.")
            return
        }
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(YOUTUBE_COMMENT_SCOPE))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (!account.current(generation)) return@addOnSuccessListener
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    account.fail(generation, "Sign-in was interrupted. Open Photon and try again.")
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
        10, 12500 -> "Google rejected this build's OAuth setup. Register Photon's package and signing SHA-1 in Google Cloud and enable YouTube Data API v3. See Google sign-in setup."
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
                OfflineArtworkProvider(library.downloads) { PhotonRoot(model, controller, pip, ::requestNotifications, ::enterPip) }
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
    val work by model.work.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var qualitySheet by remember { mutableStateOf(false) }
    var downloadSheet by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<SavedDownload?>(null) }
    val collection by model.collection.collectAsStateWithLifecycle()
    var commentsSheet by remember { mutableStateOf(false) }
    var playlistSheet by remember { mutableStateOf(false) }
    var shortsMode by rememberSaveable { mutableStateOf(false) }
    var castSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as PhotonActivity
    val casting = (context.applicationContext as PhotonApplication).casting

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_ENDED) model.next() }
            override fun onPlayerError(error: PlaybackException) { playerError = "Playback failed: ${error.errorCodeName}. Try reopening the video or choosing another quality." }
        }
        controller?.addListener(listener)
        isPlaying = controller?.isPlaying == true
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
        controller.setMediaItem(playbackItem(video, watch.quality, watch.localPath, watch.details?.subtitles.orEmpty(), watch.openedAtMs), position)
        controller.prepare()
        controller.playWhenReady = play
    }
    LaunchedEffect(watch.video?.id, watch.busy, controller) {
        if (watch.video != null) {
            expanded = true
            if (watch.busy) { controller?.stop(); controller?.clearMediaItems() }
        }
    }
    DisposableEffect(fullscreen) {
        activity.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    BackHandler(shortsMode && !pip) { shortsMode = false; expanded = false }
    BackHandler(collection != null && !expanded && !pip && !shortsMode) { model.closeCollection() }
    BackHandler(expanded && !pip && !shortsMode) { if (fullscreen) fullscreen = false else expanded = false }

    fun open(video: Video) { fullscreen = false; expanded = true; model.open(video) }
    fun share(video: Video) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.url), "Share video"))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (pip) {
            VideoSurface(controller, Modifier.fillMaxSize(), controls = false)
        } else if (shortsMode) {
            ShortsScreen(browse, watch, controller, model) { shortsMode = false; expanded = false }
        } else if (collection != null && !expanded) {
            CollectionScreen(model, library, { open(it) }, model::closeCollection)
        } else if (expanded && watch.video != null) {
            val video = watch.video!!
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (fullscreen) Modifier else Modifier.statusBarsPadding())) {
                if (!fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { expanded = false }) { Icon(PrismIcons.ChevronDown, "Minimize player") }
                    Text("NOW WATCHING", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                    IconButton(onClick = { controller?.stop(); controller?.clearMediaItems(); model.close(); expanded = false }) { Icon(PrismIcons.Close, "Close video") }
                }
                Box(if (fullscreen) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(16f / 9)) {
                    VideoSurface(controller, Modifier.fillMaxSize(), true)
                    if (watch.busy || buffering || controller == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    if (fullscreen) IconButton(onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                        Icon(PrismIcons.FullscreenExit, "Exit full screen", tint = Color.White)
                    }
                }
                if (!fullscreen) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(PrismTokens.spectrum))
                            Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(listOf(video.channel, viewLabel(video.views), video.published).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            if (watch.details != null) Text(
                                if (watch.details!!.subtitles.isEmpty()) "No compatible subtitles were provided by YouTube for this video."
                                else "Subtitles: tap the video controls, then CC to select a language or turn captions off.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            (watch.error ?: playerError)?.let { message ->
                                ErrorCard(message) { model.retryPlayback(video) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionChip(if (library.saved.any { it.id == video.id }) "Saved" else "Save", PrismIcons.Heart) { model.save(video) }
                                if (watch.details != null) {
                                    ActionChip(watch.quality?.label ?: "Quality", PrismIcons.Settings) { qualitySheet = true }
                                    if (!watch.details!!.live) ActionChip("Download", PrismIcons.Download) { downloadSheet = true }
                                }
                                if (watch.details == null && watch.localPath == null && !watch.busy) ActionChip("Reload details", PrismIcons.Refresh) { model.open(video) }
                                watch.details?.channel?.let { channel ->
                                    ActionChip("View Channel", PrismIcons.Person) { expanded = false; model.openCollection(channel.url, true) }
                                    ActionChip(if (library.subscriptions.any { it.url == channel.url }) "Subscribed locally" else "Subscribe locally", PrismIcons.PersonAdd) { model.subscribe(channel) }
                                }
                                ActionChip("Comments", PrismIcons.Info) { model.loadComments(video); commentsSheet = true }
                                ActionChip("Cast", PrismIcons.Cast) { activity.withLanPermission { casting.discover(); castSheet = true } }
                                ActionChip("Add to playlist", PrismIcons.PlaylistAdd) { playlistSheet = true }
                                if (model.queue.value.isNotEmpty()) ActionChip("Next in playlist", PrismIcons.Next) { model.next() }
                                ActionChip("Share", PrismIcons.Share) { share(video) }
                                ActionChip("Full screen", PrismIcons.Fullscreen) { fullscreen = true }
                                ActionChip("Pop out", PrismIcons.PlayCircle) { enterPip() }
                            }
                            watch.quality?.let { Text(it.audioLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (watch.localPath != null) Text("Playing your downloaded copy · offline", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            if (watch.details?.description?.isNotBlank() == true) {
                                var description by remember(video.id) { mutableStateOf(false) }
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.clickable { description = !description }.padding(16.dp)) {
                                        Text(if (description) "Description · tap to collapse" else "Description · tap to expand", fontWeight = FontWeight.SemiBold)
                                        Text(android.text.Html.fromHtml(watch.details!!.description, android.text.Html.FROM_HTML_MODE_COMPACT).toString(), maxLines = if (description) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (!watch.details?.related.isNullOrEmpty()) Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(watch.details?.related.orEmpty(), key = { it.id }) { related ->
                        VideoRow(related, { open(related) }, { model.save(related) }, library.saved.any { it.id == related.id })
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
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                            listOf("Home" to PrismIcons.Home, "Search" to PrismIcons.Search, "Library" to PrismIcons.Library, "Settings" to PrismIcons.Settings).forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(selected = tab == index, onClick = { tab = index; if (index == 0) model.home() }, icon = { Icon(icon, label) }, label = { Text(label) })
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.055f), Color.Transparent)))) {
                    when (tab) {
                        0 -> PhotonHome(model, library,
                            open = { open(it) },
                            offline = { expanded = true; model.offline(it) },
                            discover = { model.search(it); tab = 1 },
                            forYou = { model.home(); tab = 1 },
                            shorts = { model.shorts(); shortsMode = true },
                            subscriptions = { model.subscriptionsFeed(); tab = 1 },
                            downloads = { tab = 2; libraryTab = 2 },
                            saved = { tab = 2; libraryTab = 0 },
                            settings = { tab = 3 }, search = { tab = 1 })
                        1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                            item {
                                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (tab == 0) "YOUR WORLD, REFRACTED" else "FIND YOUR NEXT WATCH", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(if (tab == 0) "Photon" else "Search", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, onClick = { tab = 3 }) {
                                            Icon(PrismIcons.Play, "Settings", Modifier.padding(15.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Box(Modifier.width(64.dp).height(3.dp).clip(CircleShape).background(PrismTokens.spectrum))
                                    OutlinedTextField(
                                        value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Search or paste video/channel/playlist link") }, singleLine = true, shape = RoundedCornerShape(22.dp),
                                        leadingIcon = { Icon(PrismIcons.Search, null) },
                                        trailingIcon = { IconButton(onClick = { model.search(search); tab = 1 }) { Icon(PrismIcons.ArrowForward, "Search") } },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { model.search(search); tab = 1 })
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
                                if (tab == 0) VideoCard(video, { open(video) }, { model.save(video) }, library.saved.any { it.id == video.id })
                                else VideoRow(video, { open(video) }, { model.save(video) }, library.saved.any { it.id == video.id })
                            }
                            if (browse.hasMore) item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    if (browse.moreBusy) CircularProgressIndicator() else OutlinedButton(onClick = model::loadMore) { Text("Load more videos") }
                                }
                            }
                        }
                        2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            item {
                                PageHeading("Library", "Your corner of YouTube. Kept on this device.")
                                Row(Modifier.padding(horizontal = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Saved", "History", "Downloads", "Channels", "Playlists").forEachIndexed { index, name -> FilterChip(selected = libraryTab == index, onClick = { libraryTab = index }, label = { Text(name) }) }
                                }
                            }
                            if (libraryTab >= 3) {
                                item { LibraryCollections(library, model, libraryTab == 3, { open(it) }, { tab = 0; model.subscriptionsFeed() }) }
                            } else if (libraryTab == 2) {
                                item { Text("Downloads stay in Photon's app storage. Uninstalling removes them.", Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                            } else {
                                val videos = if (libraryTab == 0) library.saved else library.history
                                if (videos.isEmpty()) item { EmptyState(if (libraryTab == 0) "Keep the good ones" else "A fresh start", if (libraryTab == 0) "Tap the heart on any video to save it here." else "Videos you open appear here. Your history and playback resume positions stay on your phone.", if (libraryTab == 0) PrismIcons.Heart else PrismIcons.History) }
                                items(videos, key = { it.id }) { video ->
                                    Column {
                                        VideoRow(video, { open(video) }, { model.save(video) }, library.saved.any { it.id == video.id })
                                        if (libraryTab == 1) library.progress[video.id]?.let { progress ->
                                            Text("${durationLabel(progress.watchedMs / 1000)} watched · resume at ${durationLabel(progress.resumeMs() / 1000)}", Modifier.padding(start = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        3 -> SettingsPage(library, model, notifications)
                    }
                }
            }
        }
    }
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
                                tab = 2; libraryTab = 2; expanded = false
                            } else model.quality(quality)
                            qualitySheet = false; downloadSheet = false
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    delete?.let { download ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete downloaded video?") }, text = { Text("This removes the ${download.height}p offline copy of “${download.video.title}” from this device.") },
            confirmButton = { TextButton(onClick = { model.remove(download); delete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep") } })
    }
}

@Composable
@UnstableApi
internal fun VideoSurface(controller: MediaController?, modifier: Modifier, controls: Boolean) {
    AndroidView(factory = { context -> PhotonPlayerView(context).apply { player = controller; useController = controls; setShowSubtitleButton(true); setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
        modifier = modifier.background(Color.Black),
        update = { it.player = controller; it.useController = controls },
        onRelease = { it.player = null })
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, shape = CircleShape)
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
private fun VideoCard(video: Video, open: () -> Unit, save: () -> Unit, saved: Boolean) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = open)) {
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
            IconButton(onClick = save, modifier = Modifier.size(36.dp)) { Icon(if (saved) PrismIcons.HeartFilled else PrismIcons.Heart, if (saved) "Unsave video" else "Save video", Modifier.size(20.dp), tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun VideoRow(video: Video, open: () -> Unit, save: () -> Unit, saved: Boolean, actionIcon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            VideoThumbnail(video, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(durationLabel(video.duration), Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.8f)).padding(3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = save, modifier = Modifier.size(34.dp)) { Icon(actionIcon ?: if (saved) PrismIcons.HeartFilled else PrismIcons.Heart, if (actionIcon != null) "Delete download" else if (saved) "Unsave video" else "Save video", Modifier.size(18.dp)) }
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
    fun link(url: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading("Make it yours", "Prism's design. A new window into YouTube.") }
        item { YouTubeAccountCard(model) }
        item {
            SettingsGroup("APPEARANCE") {
                Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.prism.music.ui.theme.AccentSwatches.take(8).forEach { color ->
                        val argb = color.toArgb()
                        Surface(onClick = { model.accent(argb) }, shape = CircleShape, color = color, modifier = Modifier.size(34.dp)) {
                            if (library.accent == argb) Icon(PrismIcons.Check, "Selected accent", Modifier.padding(8.dp), tint = Color.White)
                        }
                    }
                }
                ListItem(headlineContent = { Text("Dark appearance") }, supportingContent = { Text("Soft surfaces and Prism's spectrum accents") }, leadingContent = { Icon(PrismIcons.Moon, null) }, trailingContent = { Switch(library.dark, model::dark) })
            }
        }
        item {
            SettingsGroup("PLAYBACK & DOWNLOADS") {
                ListItem(headlineContent = { Text("Download on Wi-Fi only") }, supportingContent = { Text("Uses unmetered networks; turn off to use mobile data") }, leadingContent = { Icon(PrismIcons.Download, null) }, trailingContent = { Switch(library.wifiOnly, model::wifi) })
                Text("Preferred playback quality", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(360, 720, 1080, 2160).forEach { height -> FilterChip(selected = library.defaultHeight == height, onClick = { model.maxHeight(height) }, label = { Text("${height}p") }) }
                }
                Text("Falls back to an available lower quality. Download qualities are selected per video.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                ListItem(headlineContent = { Text("Save offline thumbnails") }, supportingContent = { Text("Repair thumbnails for existing downloads; follows the Wi-Fi-only setting") }, leadingContent = { Icon(PrismIcons.Download, null) }, modifier = Modifier.clickable(onClick = model::repairArtwork))
                ListItem(headlineContent = { Text("Enable notifications") }, supportingContent = { Text("Playback controls and download progress") }, leadingContent = { Icon(PrismIcons.PlayCircle, null) }, modifier = Modifier.clickable(onClick = notifications))
            }
        }
        item {
            SettingsGroup("YOUR DATA") {
                ListItem(headlineContent = { Text("Clear watch history") }, supportingContent = { Text("Saved videos and downloads are not affected") }, leadingContent = { Icon(PrismIcons.History, null) }, modifier = Modifier.clickable { clear = true })
                Text("No Photon account, analytics, or server. Searches, video requests and thumbnail requests go to YouTube and its delivery services. Library data stays on this device.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsGroup("ABOUT PHOTON") {
                ListItem(headlineContent = { Text("Photon ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text("Early Android preview · not affiliated with Google or YouTube") }, leadingContent = { Icon(PrismIcons.Play, null) })
                ListItem(headlineContent = { Text("Share device test report") }, supportingContent = { Text("Device/API, app version and URL-free playback events. Review before sending.") }, leadingContent = { Icon(PrismIcons.Share, null) }, modifier = Modifier.clickable {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PhotonDiagnostics.report()), "Share test report"))
                })
                ListItem(headlineContent = { Text("Google account on YouTube (browser)") }, supportingContent = { Text("Opens official YouTube. This does not sign Photon in or sync its local library.") }, trailingContent = { Icon(PrismIcons.OpenInNew, null) }, modifier = Modifier.clickable { context.startActivity(browserIntent("https://www.youtube.com/")) })
                ListItem(headlineContent = { Text("Source code & issues") }, supportingContent = { Text("HayMayia/Photon · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/HayMayia/Photon") })
                ListItem(headlineContent = { Text("Built on Prism by Yigder") }, supportingContent = { Text("Original design, icons, typography and Android foundation · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/Yigder/Prism") })
                ListItem(headlineContent = { Text("NewPipe Extractor") }, supportingContent = { Text("YouTube extraction · GPL-3.0-or-later") }, trailingContent = { Icon(PrismIcons.ArrowForward, null) }, modifier = Modifier.clickable { link("https://github.com/TeamNewPipe/NewPipeExtractor") })
                Text("Download only when you have permission. No DRM, paid-access, age-check or sign-in bypass. YouTube changes may temporarily break extraction. Channel subscriptions and playlists remain local. Google authorization enables real YouTube comment posting; full account-library sync is not implemented.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear watch history?") }, text = { Text("This deletes the watch history kept on this device.") }, confirmButton = { TextButton(onClick = { model.clearHistory(); clear = false }) { Text("Clear history") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
    }
}
