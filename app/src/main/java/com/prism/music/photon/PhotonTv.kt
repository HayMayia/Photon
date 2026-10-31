package com.prism.music.photon

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Focus ring is visible for keyboards/remotes on either form factor, without stealing focus. */
@Composable
internal fun Modifier.remoteFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }.border(3.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotonTvScreen(model: PhotonViewModel, selected: Int, select: (Int) -> Unit,
    open: (Video) -> Unit, offline: (SavedDownload) -> Unit, channel: (String) -> Unit,
    remove: (SavedDownload) -> Unit, resume: (() -> Unit)?, settings: @Composable () -> Unit) {
    val library by model.library.collectAsStateWithLifecycle()
    val browse by model.browse.collectAsStateWithLifecycle()
    val home by model.youtubeHome.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var focusedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    val navFocus = remember { FocusRequester() }
    val videos = when {
        selected == 4 -> library.downloads.map { it.video }
        selected == 0 && library.youtubeHome -> home.page?.videos.orEmpty()
        else -> browse.videos
    }
    LaunchedEffect(Unit) { if (selected == 0 && videos.isEmpty()) model.home() }
    LaunchedEffect(selected, videos.map { it.id }) {
        if (focusedVideo == null || videos.none { it.id == focusedVideo }) { focusedVideo = null; navFocus.requestFocus() }
    }
    Row(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (library.youtubeLayout) "Home" else "Photon", style = MaterialTheme.typography.headlineMedium)
            listOf(0 to "Home", 1 to "Search", 5 to "Subscriptions", 6 to "Shorts", 2 to "Library", 4 to "Downloads", 3 to "Settings").forEachIndexed { index, (tab, label) ->
                FilledTonalButton(onClick = { focusedVideo = null; select(tab); if (tab == 0) model.home(); if (tab == 5) model.subscriptionsFeed(); if (tab == 6) model.shorts() },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(navFocus) else Modifier).remoteFocus()) {
                    Text(if (tab == selected) "• $label" else label)
                }
            }
            if (resume != null) OutlinedButton(onClick = resume, modifier = Modifier.fillMaxWidth().remoteFocus()) { Text("Now playing") }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == 0) PhotonUpdateCard()
            when (selected) {
                3 -> settings()
                2 -> if (library.youtubeHome) AccountLibraryScreen(model, library.youtubeSessionRevision, library.youtubeSessionReady, open, channel) else YouTubeHomeConnection(model)
                else -> {
                    if (selected == 1) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it }, label = { Text("Search videos and music") }, singleLine = true,
                            modifier = Modifier.weight(1f).remoteFocus(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusedVideo = null; model.search(query) }))
                        Button(onClick = { focusedVideo = null; model.search(query) }, modifier = Modifier.remoteFocus()) { Text("Search") }
                    }
                    val busy = if (selected == 0 && library.youtubeHome) home.busy else browse.busy && selected != 4
                    val error = if (selected == 0 && library.youtubeHome) home.error else browse.error.takeIf { selected != 4 }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyVerticalGrid(columns = GridCells.Adaptive(220.dp), modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(videos, key = { it.id }) { source ->
                            val video = resolvedVideo(source)
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(video.id) { if (focusedVideo == video.id) focus.requestFocus() }
                            OutlinedCard(onClick = { focusedVideo = video.id
                                if (selected == 4) library.downloads.firstOrNull { it.video.id == video.id }?.let(offline) else open(video)
                            }, modifier = Modifier.fillMaxWidth().focusRequester(focus).remoteFocus()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.fillMaxWidth().height(145.dp), contentAlignment = Alignment.Center) {
                                        VideoThumbnail(video, if (selected == 4) library.downloads.firstOrNull { it.video.id == video.id }?.thumbnailPath else null, Modifier.fillMaxHeight().aspectRatio(VideoArtworkShape.ratio(video.short)))
                                    }
                                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (selected == 4) TextButton(onClick = { library.downloads.firstOrNull { it.video.id == video.id }?.let(remove) }, modifier = Modifier.remoteFocus()) { Text("Delete download") }
                                }
                            }
                        }
                        item {
                            if (selected == 0 && library.youtubeHome && home.page?.continuation != null) Button(onClick = model::moreYouTubeHome, modifier = Modifier.remoteFocus()) { Text("More") }
                            else if (selected != 4 && browse.hasMore) Button(onClick = model::loadMore, modifier = Modifier.remoteFocus()) { Text("More") }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TvVideoDetails(model: PhotonViewModel, controller: androidx.media3.session.MediaController?, watch: WatchState,
    fullscreen: () -> Unit, close: () -> Unit, comments: () -> Unit, channel: (String) -> Unit, cast: () -> Unit,
    open: (Video) -> Unit, download: () -> Unit, save: () -> Unit) {
    val video = watch.video ?: return
    val menus = LocalPlayerMenus.current
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(.58f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VideoSurface(controller, Modifier.fillMaxWidth().weight(1f), controls = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = fullscreen, modifier = Modifier.remoteFocus()) { Text("Fullscreen") }
                OutlinedButton(onClick = close, modifier = Modifier.remoteFocus()) { Text("Browse") }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(.42f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(video.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text(video.channel) }
            item { Text(listOf(video.published, if (video.views >= 0) "${video.views} views" else "").filter { it.isNotBlank() }.joinToString(" · ")) }
            watch.details?.channel?.let { creator -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { channel(creator.url) }, modifier = Modifier.remoteFocus()) { Text("Channel") }
                    OutlinedButton(onClick = { model.subscribe(creator) }, modifier = Modifier.remoteFocus()) { Text("Subscription") }
                }
            } }
            item { Button(onClick = fullscreen, modifier = Modifier.remoteFocus()) { Text("Watch fullscreen") } }
            item { OutlinedButton(onClick = menus.settings, modifier = Modifier.remoteFocus()) { Text("Player settings") } }
            item { OutlinedButton(onClick = menus.captions, modifier = Modifier.remoteFocus()) { Text("Captions") } }
            item { OutlinedButton(onClick = { model.save(video) }, modifier = Modifier.remoteFocus()) { Text(if (likes[video.id] == true) "Unlike" else "Like") } }
            item { OutlinedButton(onClick = save, modifier = Modifier.remoteFocus()) { Text("Save to playlist") } }
            item { OutlinedButton(onClick = cast, modifier = Modifier.remoteFocus()) { Text("Cast") } }
            if (model.queue.value.isNotEmpty()) item { OutlinedButton(onClick = model::next, modifier = Modifier.remoteFocus()) { Text("Next") } }
            item { OutlinedButton(onClick = comments, modifier = Modifier.remoteFocus()) { Text("Comments") } }
            if (watch.details?.live == false) item { OutlinedButton(onClick = download, modifier = Modifier.remoteFocus()) { Text("Download") } }
            watch.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            watch.details?.description?.takeIf { it.isNotBlank() }?.let { description -> item {
                Text(android.text.Html.fromHtml(description, android.text.Html.FROM_HTML_MODE_COMPACT).toString(), maxLines = 8, overflow = TextOverflow.Ellipsis)
            } }
            items(watch.details?.related.orEmpty(), key = { "related:${it.id}" }) { related ->
                OutlinedButton(onClick = { open(related) }, modifier = Modifier.fillMaxWidth().remoteFocus()) { Text(related.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
