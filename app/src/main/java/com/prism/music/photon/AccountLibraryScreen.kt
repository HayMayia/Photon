package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import com.prism.music.ui.theme.PrismIcons
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag

@Composable
internal fun AccountLibraryScreen(model: PhotonViewModel, revision: Long, ready: Boolean, open: (Video) -> Unit, channel: (String) -> Unit, subscriptions: () -> Unit = model::subscriptionsFeed) {
    val television = androidx.compose.ui.platform.LocalContext.current.isPhotonTv()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    val state by model.accountLibrary.collectAsStateWithLifecycle()
    LaunchedEffect(revision, ready) {
        if (ready && state.page == null && !state.busy && state.error == null) model.openAccountShelf(state.shelf, state.playlist, state.title)
    }
    androidx.activity.compose.BackHandler(state.playlist != null) { model.openAccountShelf(AccountShelf.PLAYLISTS) }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("Create private YouTube playlist") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.createGooglePlaylist(name); name = ""; creating = false }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
    val grid = rememberLazyGridState()
    AccountLibraryFormatEffect(grid, state.page?.items.orEmpty()) { model.commitAccountLibraryFormats(state.generation) }
    LazyVerticalGrid(state = grid, columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(2) }) {
            AccountLibraryHeader(state, ready, onShelf = { model.openAccountShelf(it) },
                onCreate = { creating = true }, onBack = { model.openAccountShelf(AccountShelf.PLAYLISTS) },
                onSubscriptions = subscriptions, onRefresh = { model.loadGoogleLikedIndex(force = true); model.openAccountShelf(state.shelf, state.playlist, state.title) })
        }
        items(state.page?.items.orEmpty(), key = AccountLibraryRows::key,
            span = { entry -> GridItemSpan(AccountGridPolicy.span(entry)) }) { entry ->
            if (entry.video != null) {
                val video = resolvedVideo(entry.video)
                Column(Modifier.fillMaxWidth().remoteFocus().clickable { open(video) }.padding(horizontal = 6.dp, vertical = 10.dp)) {
                    VideoThumbnail(video, entry.title, if (television && AccountGridPolicy.portrait(entry))
                        Modifier.height(180.dp).aspectRatio(VideoArtworkShape.ratio(true)).align(androidx.compose.ui.Alignment.CenterHorizontally)
                        else Modifier.fillMaxWidth().aspectRatio(AccountGridPolicy.ratio(entry)),
                        contentScale = if (video.short != AccountGridPolicy.portrait(entry)) ContentScale.Fit else ContentScale.Crop)
                    Text(entry.title, Modifier.fillMaxWidth().padding(top = 8.dp), fontWeight = FontWeight.SemiBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (state.playlist != null) TextButton(onClick = { model.removeFromPlaylist(state.playlist!!, video) }) { Text("Remove") }
                    IconButton(onClick = { model.save(video) }) { Icon(if (likes[video.id] == true) PrismIcons.HeartFilled else PrismIcons.Heart, if (likes[video.id] == true) "Remove like" else "Like", tint = if (likes[video.id] == true) androidx.compose.ui.graphics.Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else ListItem(
                headlineContent = { Text(entry.title, fontWeight = FontWeight.SemiBold) },
                leadingContent = {
                    if (entry.kind == AccountEntryKind.CHANNEL) ChannelAvatar(entry, model)
                    else if (entry.artwork.isNotBlank()) AsyncImage(entry.artwork, entry.title,
                        Modifier.size(64.dp).clip(if (entry.kind == AccountEntryKind.CHANNEL) CircleShape else RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    else Icon(if (entry.kind == AccountEntryKind.CHANNEL) PrismIcons.Person else PrismIcons.Library, null, Modifier.size(48.dp))
                },
                supportingContent = { Text(if (entry.kind == AccountEntryKind.CHANNEL) "Channel" else "Playlist") },
                trailingContent = { if (entry.kind == AccountEntryKind.PLAYLIST) TextButton(onClick = { model.deletePlaylist(entry.id, entry.title) }) { Text("Delete") } },
                modifier = Modifier.remoteFocus().clickable {
                    if (entry.kind == AccountEntryKind.CHANNEL) channel("https://www.youtube.com/channel/${entry.id}")
                    else model.openAccountShelf(AccountShelf.PLAYLISTS, entry.id, entry.title)
                }
            )
        }
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.fillMaxWidth()) {
                if (state.moreBusy) CircularProgressIndicator(Modifier.padding(22.dp))
                else if (state.page?.continuation != null) TextButton(onClick = model::moreAccountShelf, modifier = Modifier.padding(horizontal = 16.dp)) { Text("More") }
            }
        }
    }
}

@Composable
internal fun ChannelAvatar(entry: AccountEntry, model: PhotonViewModel) {
    val images by model.channelArtwork.collectAsStateWithLifecycle()
    var failed by remember(entry.id) { mutableStateOf(false) }
    val image = images[entry.id] ?: entry.artwork
    LaunchedEffect(entry.id, failed) { if (entry.artwork.isBlank() || failed) model.loadChannelArtwork(entry.id) }
    androidx.compose.foundation.layout.Box(Modifier.size(64.dp).clip(CircleShape)
        .then(Modifier.background(MaterialTheme.colorScheme.primaryContainer)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(entry.title.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (image.isNotBlank()) AsyncImage(image, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onError = { failed = true })
    }
}

/** A grid item must emit one layout root: siblings otherwise stack at the same origin. */
@Composable
internal fun AccountLibraryHeader(state: AccountLibraryState, ready: Boolean,
    onShelf: (AccountShelf) -> Unit = {}, onSubscriptions: () -> Unit = {}, onCreate: () -> Unit = {}, onBack: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("library-header"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("library-tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSubscriptions, modifier = Modifier.remoteFocus()) { Text("Subscriptions") }
            AccountShelf.entries.forEach { shelf ->
                FilterChip(selected = state.shelf == shelf && state.playlist == null, onClick = { onShelf(shelf) }, label = { Text(shelf.label) }, modifier = Modifier.remoteFocus())
            }
        }
        if (state.shelf == AccountShelf.PLAYLISTS && state.playlist == null) TextButton(onClick = onCreate, modifier = Modifier.remoteFocus()) { Text("New playlist") }
        if (state.playlist != null) TextButton(onClick = onBack, modifier = Modifier.remoteFocus()) { Text("Back to playlists") }
        Text(state.title, Modifier.fillMaxWidth().testTag("library-title"), style = MaterialTheme.typography.titleLarge)
        TextButton(enabled = ready && !state.busy && !state.moreBusy, onClick = onRefresh, modifier = Modifier.remoteFocus().testTag("library-refresh")) { Text("Refresh") }
        if (!ready) Text("Finish Google sign-in to read account lists.", Modifier.fillMaxWidth())
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, Modifier.fillMaxWidth().testTag("library-error"), color = MaterialTheme.colorScheme.error) }
        state.page?.message?.let { Text(it, Modifier.fillMaxWidth().testTag("library-message")) }
    }
}
