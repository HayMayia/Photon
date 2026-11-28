package com.prism.music.photon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.prism.music.ui.theme.PrismIcons

/** A selected Short, not a second independently-running feed/pager. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TvShortsPlayer(watch: WatchState, controller: MediaController?, model: PhotonViewModel, close: () -> Unit) {
    val video = watch.video ?: return
    val transport by model.transport.state.collectAsStateWithLifecycle()
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    var comments by remember(video.id) { mutableStateOf(false) }
    val menus = LocalPlayerMenus.current
    fun move(delta: Int) {
        TvPlaybackPolicy.adjacent(transport.items, video.id, delta)?.let {
            model.prepareQueuedSelection(it)
            model.open(it, fromQueue = true)
        }
    }
    fun back() { if (comments) comments = false else close() }
    BackHandler { back() }
    TvShortsFrame(video, likes[video.id] == true, watch.busy, watch.error,
        like = { model.save(video) }, comments = { model.loadComments(video); comments = true },
        settings = menus.settings, close = ::back, step = ::move,
        togglePlayback = { if (controller?.isPlaying == true) controller.pause() else controller?.play() }) {
        VideoSurface(controller, Modifier.fillMaxSize(), false)
    }
        if (comments) androidx.compose.ui.window.Dialog(onDismissRequest = { comments = false }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.85f)) { CommentsPanel(model, video) }
        }
}

/** Stateless production layout lets remote exit/navigation be tested without stream extraction. */
@Composable
internal fun TvShortsFrame(video: Video, liked: Boolean, busy: Boolean, error: String?,
    like: () -> Unit, comments: () -> Unit, settings: () -> Unit, close: () -> Unit,
    step: (Int) -> Unit, togglePlayback: () -> Unit, videoContent: @Composable BoxScope.() -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(video.id) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xFF41494D)).onPreviewKeyEvent {
        if (it.key == Key.Back || it.key == Key.Escape) {
            if (it.type == KeyEventType.KeyUp) close()
            true
        } else false
    }) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 14.dp)) {
            // Center the portrait itself, as in the reference; actions do not push it left.
            Box(Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(9f/16f)
                .clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(.5f), RoundedCornerShape(12.dp))
                .then(Modifier.testTag("tv-short-video")).focusRequester(focus).onKeyEvent {
                    if (it.type == KeyEventType.KeyDown) when (it.key) {
                        Key.DirectionUp -> { step(-1); true }
                        Key.DirectionDown -> { step(1); true }
                        Key.DirectionCenter, Key.Enter -> { togglePlayback(); true }
                        else -> false
                    } else false
                }.focusable()) { videoContent() }
            Column(Modifier.align(Alignment.BottomEnd).width(maxWidth * .27f).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 3)
                Text(video.channel, color = Color.White.copy(.9f), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = like, modifier = Modifier.remoteFocus()) { Icon(if (liked) PrismIcons.HeartFilled else PrismIcons.Heart, "Like", tint = Color.White) }
                    IconButton(onClick = comments, modifier = Modifier.remoteFocus()) { Icon(PrismIcons.Lyrics, "Comments", tint = Color.White) }
                    IconButton(onClick = settings, modifier = Modifier.remoteFocus()) { Icon(PrismIcons.Settings, "Settings", tint = Color.White) }
                }
                TextButton(onClick = close, modifier = Modifier.remoteFocus()) { Text("Back", color = Color.White) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
