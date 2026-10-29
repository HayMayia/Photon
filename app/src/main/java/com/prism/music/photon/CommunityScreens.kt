package com.prism.music.photon

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun CollectionScreen(model: PhotonViewModel, library: PhotonLibrary, open: (Video) -> Unit, close: () -> Unit) {
    val page by model.collection.collectAsStateWithLifecycle()
    val busy by model.collectionBusy.collectAsStateWithLifecycle()
    val error by model.collectionError.collectAsStateWithLifecycle()
    val current = page ?: return
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(PrismIcons.ArrowBack, "Back") }
                Text(current.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            current.channel?.let { channel ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(channel.avatar, null, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        if (channel.subscribers >= 0) Text("${channel.subscribers} subscribers")
                        Text("Subscription stored on this device", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = { model.subscribe(channel) }) { Text(if (library.subscriptions.any { it.url == channel.url }) "Subscribed" else "Subscribe") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.channelTab(false) }) { Text("Videos") }
                    TextButton(onClick = { model.channelTab(true) }) { Text("Shorts") }
                }
            }
            if (current.description.isNotBlank()) Text(current.description, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (current.videos.isNotEmpty()) FilledTonalButton(onClick = { model.playAll(current.videos) }) { Icon(PrismIcons.Play, null); Text("Play loaded videos") }
        }
        items(current.videos, key = { it.id }) { video -> CommunityVideoRow(video, { open(video) }) }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            if (busy) CircularProgressIndicator(Modifier.padding(20.dp))
            else if (current.next != null) OutlinedButton(onClick = model::moreCollection) { Text("Load more") }
        }
    }
}

@Composable
internal fun LibraryCollections(library: PhotonLibrary, model: PhotonViewModel, channels: Boolean, open: (Video) -> Unit, subscriptions: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<LocalPlaylist?>(null) }
    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (channels) {
            Text("Local subscriptions · not synced to Google", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (library.subscriptions.isEmpty()) Text("Open a video's channel and subscribe to see uploads here.")
            else FilledTonalButton(onClick = subscriptions) { Text("Refresh subscription uploads") }
            library.subscriptions.forEach { channel ->
                ListItem(headlineContent = { Text(channel.name) }, leadingContent = { AsyncImage(channel.avatar, null, Modifier.size(44.dp)) },
                    trailingContent = { IconButton(onClick = { model.subscribe(channel) }) { Icon(PrismIcons.Close, "Unsubscribe") } },
                    modifier = Modifier.clickable { model.openCollection(channel.url, true) })
            }
        } else {
            Text("Local playlists · paste a public YouTube playlist link in Search to browse it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FilledTonalButton(enabled = name.isNotBlank(), onClick = { selected = model.createPlaylist(name); name = "" }) { Text("Create playlist") }
            library.playlists.forEach { playlist ->
                ListItem(headlineContent = { Text(playlist.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${playlist.videos.size} videos") },
                    trailingContent = { IconButton(onClick = { remove = playlist }) { Icon(PrismIcons.Delete, "Delete playlist") } }, modifier = Modifier.clickable { selected = if (selected == playlist.id) null else playlist.id })
                if (selected == playlist.id) {
                    if (playlist.videos.isNotEmpty()) FilledTonalButton(onClick = { model.playAll(playlist.videos) }) { Text("Play all") }
                    else Text("Open a video and tap Add to playlist.")
                    playlist.videos.forEach { video ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(video.title, Modifier.weight(1f).clickable { open(video) }.padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { model.removeFromPlaylist(playlist.id, video) }) { Icon(PrismIcons.Close, "Remove from playlist") }
                        }
                    }
                }
            }
        }
    }
    remove?.let { playlist -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Delete ${playlist.name}?") }, text = { Text("Only this local playlist is deleted. Downloads and saved videos stay.") },
        confirmButton = { TextButton(onClick = { model.deletePlaylist(playlist.id); remove = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}

@Composable
internal fun PlaylistPicker(library: PhotonLibrary, video: Video, model: PhotonViewModel, done: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add to playlist", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FilledTonalButton(enabled = name.isNotBlank(), onClick = { val id = model.createPlaylist(name); model.addToPlaylist(id, video); done() }) { Text("Create and add") }
        LazyColumn(Modifier.heightIn(max = 340.dp)) {
            items(library.playlists, key = { it.id }) { playlist ->
                ListItem(headlineContent = { Text(playlist.name) }, supportingContent = { Text("${playlist.videos.size} videos") }, modifier = Modifier.clickable { model.addToPlaylist(playlist.id, video); done() })
            }
        }
    }
}

@Composable
internal fun CommentsPanel(model: PhotonViewModel, video: Video) {
    val page by model.comments.collectAsStateWithLifecycle()
    val busy by model.commentsBusy.collectAsStateWithLifecycle()
    val error by model.commentsError.collectAsStateWithLifecycle()
    var replies by remember(video.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 22.dp)) {
        Text(if (replies) "Replies" else "Comments", style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = { replies = false; model.loadComments(video) }) { Text(if (replies) "Back to comments" else "Refresh") }
            TextButton(onClick = { context.startActivity(browserIntent(video.url)) }) { Text("Post on YouTube") }
        }
        Text("Read-only in Photon. Posting opens YouTube.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) {
            if (page?.disabled == true) item { Text("Comments are disabled for this video.") }
            items(page?.comments.orEmpty(), key = { it.id }) { comment ->
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(comment.author, fontWeight = FontWeight.Bold)
                    Text(android.text.Html.fromHtml(comment.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString())
                    if (comment.likes >= 0) Text("${comment.likes} likes", style = MaterialTheme.typography.labelSmall)
                    comment.replyPage?.let { next -> TextButton(onClick = { replies = true; model.loadComments(video, replies = next) }) { Text("View replies (${comment.replies.coerceAtLeast(0)})") } }
                }
                HorizontalDivider()
            }
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
                else if (page?.next != null) TextButton(onClick = { model.loadComments(video, more = true) }) { Text("Load more") }
                else if (page?.comments?.isEmpty() == true && page?.disabled != true) Text("No comments returned by YouTube.")
            }
        }
    }
}

@Composable
@UnstableApi
internal fun ShortsScreen(browse: BrowseState, watch: WatchState, controller: MediaController?, model: PhotonViewModel, close: () -> Unit) {
    val pager = rememberPagerState(pageCount = { browse.videos.size })
    LaunchedEffect(browse.videos) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { index ->
            browse.videos.getOrNull(index)?.let { video -> if (model.watch.value.video?.id != video.id) model.open(video) }
            if (index >= browse.videos.size - 3 && browse.hasMore) model.loadMore()
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        if (browse.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!browse.busy && browse.videos.isEmpty()) Text(browse.error ?: "No Shorts returned. Try refreshing.", Modifier.align(Alignment.Center).padding(24.dp))
        VerticalPager(pager, Modifier.fillMaxSize()) { index ->
            val video = browse.videos[index]
            Column(Modifier.fillMaxSize()) {
                if (index == pager.currentPage) VideoSurface(controller, Modifier.weight(1f).fillMaxWidth(), true)
                else VideoThumbnail(video, video.title, Modifier.weight(1f).fillMaxWidth())
                Text(video.title, Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, maxLines = 2)
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.channel, Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { model.save(video) }) { Text("Save locally") }
                }
                if (index == pager.currentPage && watch.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (index == browse.videos.lastIndex && browse.hasMore) TextButton(onClick = model::loadMore) { Text("Load more Shorts") }
                if (index == pager.currentPage) watch.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            }
        }
        IconButton(onClick = close, modifier = Modifier.align(Alignment.TopStart)) { Icon(PrismIcons.ArrowBack, "Exit Shorts") }
    }
}

@Composable
private fun CommunityVideoRow(video: Video, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoThumbnail(video, null, Modifier.width(120.dp).aspectRatio(16f / 9))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(video.channel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun CastPanel(casting: PhotonCast, watch: WatchState, pausePhone: () -> Unit) {
    val devices by casting.devices.collectAsStateWithLifecycle()
    val status by casting.status.collectAsStateWithLifecycle()
    val connected by casting.connected.collectAsStateWithLifecycle()
    val busy by casting.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Watch on your TV", style = MaterialTheme.typography.headlineSmall)
        Text("Google Cast and DLNA on the same Wi-Fi. Keep Photon open. Uses a combined MP4 source, or your merged download. Live casting is not supported.")
        Text(status, color = MaterialTheme.colorScheme.primary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = casting::discover, enabled = !busy) { Text("Scan again") }
            if (connected != null) {
                TextButton(onClick = { casting.play() }) { Text("Play") }
                TextButton(onClick = { casting.pause() }) { Text("Pause") }
                TextButton(onClick = { casting.stop() }) { Text("Disconnect") }
            }
        }
        if (devices.isEmpty()) Text("No devices found yet. Check Wi-Fi, TV power, local-network permission and router client isolation.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(devices, key = { it.id }) { device ->
                ListItem(headlineContent = { Text(device.name) }, supportingContent = { Text(device.detail) }, modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch { if (casting.cast(device, watch)) pausePhone() }
                })
            }
        }
    }
}


internal fun browserIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER) }
