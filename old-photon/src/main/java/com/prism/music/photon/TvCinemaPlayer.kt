package com.prism.music.photon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

internal data class TvChapter(val title: String, val positionMs: Long)
internal object TvChapterParser {
    // Only explicit creator timestamps, never invented chapters or recommendations.
    fun parse(description: String): List<TvChapter> {
        val text = org.jsoup.Jsoup.parse(description.replace(Regex("(?i)<br\\s*/?>"), "\n")).wholeText()
        val pattern = Regex("^\\s*(\\d{1,3}:\\d{2}(?::\\d{2})?)\\s+[-–—]?\\s*(.+)$")
        val chapters = text.lines().mapNotNull { line ->
            val match = pattern.find(line) ?: return@mapNotNull null
            val parts = match.groupValues[1].split(':').map { it.toLong() }
            if (parts.drop(1).any { it > 59 }) return@mapNotNull null
            TvChapter(match.groupValues[2].trim(), parts.fold(0L) { total, n -> total * 60 + n } * 1000)
        }.distinctBy { it.positionMs }
        return chapters.takeIf { it.size >= 2 && it.first().positionMs == 0L && it.zipWithNext().all { (a,b) -> a.positionMs < b.positionMs } }.orEmpty()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TvCinemaPlayer(model: PhotonViewModel, controller: MediaController?, watch: WatchState, open: (Video) -> Unit, close: () -> Unit) {
    val video = watch.video ?: return
    val menus = LocalPlayerMenus.current
    val queue by model.queue.collectAsStateWithLifecycle()
    val source by model.playlistSource.collectAsStateWithLifecycle()
    val transport by model.transport.state.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    val more by model.playlistMore.collectAsStateWithLifecycle()
    val queueBusy by model.playlistQueueBusy.collectAsStateWithLifecycle()
    val queueError by model.playlistQueueError.collectAsStateWithLifecycle()
    val chapters = remember(watch.details?.description) { TvChapterParser.parse(watch.details?.description.orEmpty()) }
    val section = if (TvPlaybackPolicy.playlist(source)) "Playlist" else if (chapters.isNotEmpty()) "Chapters" else null
    var visible by remember(video.id) { mutableStateOf(true) }
    var shelf by remember(video.id) { mutableStateOf<String?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    LaunchedEffect(video.id) { root.requestFocus() }
    LaunchedEffect(controller) { while (true) { position = controller?.currentPosition?.coerceAtLeast(0) ?: 0; duration = controller?.duration?.coerceAtLeast(0) ?: 0; playing = controller?.isPlaying == true; delay(300) } }
    LaunchedEffect(interaction, shelf, playing) { if (shelf == null && playing) { delay(6000); visible = false; root.requestFocus() } }
    BackHandler { when { shelf != null -> shelf = null; visible -> visible = false; else -> close() } }
    fun time(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s/3600,s/60%60,s%60) else "%02d:%02d".format(s/60,s%60) }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else {
            interaction++
            if (!visible) { visible = true; true }
            else if (it.key == Key.DirectionCenter || it.key == Key.Enter) false
            else if (it.key == Key.MediaPlayPause) { if (playing) controller?.pause() else controller?.play(); true } else false
        }
    }.onKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
            if (playing) controller?.pause() else controller?.play(); true
        } else false
    }.focusRequester(root).focusable()) {
        VideoSurface(controller, Modifier.fillMaxSize(), controls = false)
        if (watch.busy || controller == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
        watch.error?.let { message ->
            Surface(Modifier.align(Alignment.Center).widthIn(max = 500.dp), color = Color.Black.copy(.9f)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message, color = Color.White)
                    OutlinedButton(onClick = { model.retryPlayback(video) }, modifier = Modifier.remoteFocus()) { Text("Retry video") }
                }
            }
        }
        if (visible) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.65f), Color.Transparent, Color.Black.copy(.8f)))))
            if (shelf == null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 32.dp)) {
                    Text(video.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(video.channel, color = Color.White.copy(.8f), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = menus.settings, modifier = Modifier.remoteFocus()) { Text("Settings", color = Color.White) }
            }
            Column(Modifier.align(if (shelf == null) Alignment.BottomCenter else Alignment.TopCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (shelf == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { if (playing) controller?.pause() else controller?.play() }, modifier = Modifier.remoteFocus()) { Text(if (playing) "Pause" else "Play", color = Color.White) }
                        TvSeekBar(position, duration, { controller?.seekTo(it) }, Modifier.weight(1f))
                        Text(time(position) + " / " + time(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (library.youtubeHome && source != "feed:subscriptions") TextButton(onClick = { shelf = if (shelf == "Up next") null else "Up next" }, modifier = Modifier.remoteFocus()) { Text("Up next", color = Color.White) }
                    if (section != null) TextButton(onClick = { shelf = if (shelf == section) null else section }, modifier = Modifier.remoteFocus()) { Text(section, color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    if (transport.next() != null || transport.continuation != null) TextButton(onClick = model::next, modifier = Modifier.remoteFocus()) { Text("Next video", color = Color.White) }
                }
                if (shelf != null) {
                    HorizontalDivider(color = Color.White.copy(.3f))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(8.dp)) {
                        if (shelf == "Chapters") items(chapters) { chapter ->
                            OutlinedButton(onClick = { controller?.seekTo(chapter.positionMs); shelf = null }, modifier = Modifier.width(240.dp).height(100.dp).remoteFocus()) { Text(time(chapter.positionMs) + "  " + chapter.title, maxLines = 3, color = Color.White) }
                        } else {
                            val videos = if (shelf == "Playlist") queue else watch.details?.related.orEmpty()
                            items(videos.distinctBy { it.id }, key = { it.id }) { item ->
                                Box(Modifier.width(230.dp)) { TvPosterCard(item, open = { if (shelf == "Playlist") model.prepareQueuedSelection(item); open(item) }) }
                            }
                            if (videos.isEmpty()) item { Text("No videos available", color = Color.White, modifier = Modifier.padding(24.dp)) }
                            if (shelf == "Playlist" && more) item { OutlinedButton(onClick = { model.morePlaylistQueue() }, enabled = !queueBusy, modifier = Modifier.remoteFocus()) { Text(if (queueBusy) "Loading…" else "More from playlist") } }
                        }
                    }
                    if (shelf == "Playlist") queueError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun TvSeekBar(position: Long, duration: Long, seek: (Long) -> Unit, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableLongStateOf(0) }
    OutlinedButton(onClick = { if (editing) { seek(draft); editing = false } else { draft = position; editing = true } },
        modifier = modifier.remoteFocus().onPreviewKeyEvent {
            if (editing && it.type == KeyEventType.KeyDown) when (it.key) {
                Key.DirectionLeft, Key.DirectionRight -> { draft = (draft + if (it.key == Key.DirectionRight) 10000 else -10000).coerceIn(0, duration); true }
                Key.Back, Key.DirectionUp, Key.DirectionDown -> { editing = false; it.key == Key.Back }
                else -> false
            } else false
        }) {
        LinearProgressIndicator(progress = { if (duration > 0) (if (editing) draft else position).toFloat() / duration else 0f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF39D5F8), trackColor = Color.White.copy(.25f))
    }
}
