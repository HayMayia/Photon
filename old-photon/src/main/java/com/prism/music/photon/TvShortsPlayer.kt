package com.prism.music.photon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController

/** TV portrait player: full-height video with a separate readable action rail. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TvShortsPlayer(browse: BrowseState, watch: WatchState, controller: MediaController?, model: PhotonViewModel, close: () -> Unit) {
    val video = watch.video ?: browse.videos.firstOrNull()
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    var comments by remember(video?.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun move(delta: Int) {
        val index = browse.videos.indexOfFirst { it.id == video?.id }.coerceAtLeast(0)
        browse.videos.getOrNull(index + delta)?.let { model.open(it) }
        if (index + delta >= browse.videos.size - 3 && browse.hasMore) model.loadMore()
    }
    LaunchedEffect(video?.id) { if (watch.video == null && video != null) model.open(video); focus.requestFocus() }
    BackHandler { if (comments) comments = false else close() }
    Box(Modifier.fillMaxSize().background(Color(0xFF141A24)).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) when (it.key) {
            Key.DirectionUp -> { move(-1); true }
            Key.DirectionDown -> { move(1); true }
            else -> false
        } else false
    }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(9f/16f).focusRequester(focus).onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    if (controller?.isPlaying == true) controller.pause() else controller?.play(); true
                } else false
            }.focusable()) { VideoSurface(controller, Modifier.fillMaxSize(), false) }
            Column(Modifier.widthIn(max = 380.dp).padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(video?.title ?: "Loading Shorts…", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(video?.channel.orEmpty(), color = Color.White.copy(.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { video?.let(model::save) }, modifier = Modifier.remoteFocus()) { Text(if (likes[video?.id] == true) "♥ Liked" else "♡ Like") }
                    OutlinedButton(onClick = { video?.let { model.loadComments(it); comments = true } }, modifier = Modifier.remoteFocus()) { Text("Comments") }
                }
                OutlinedButton(onClick = LocalPlayerMenus.current.settings, modifier = Modifier.remoteFocus()) { Text("Settings") }
                Text("↑ Previous Short    ↓ Next Short\nOK on video to play / pause", color = Color.White.copy(.65f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = close, modifier = Modifier.remoteFocus()) { Text("Back to browse") }
                if (watch.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                watch.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (comments && video != null) androidx.compose.ui.window.Dialog(onDismissRequest = { comments = false }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.85f)) { CommentsPanel(model, video) }
        }
    }
}
