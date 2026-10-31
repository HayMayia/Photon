package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.prism.music.ui.components.SectionHeader

@Composable
internal fun AccountLibraryScreen(model: PhotonViewModel, revision: Long, ready: Boolean, open: (Video) -> Unit, channel: (String) -> Unit) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            if (!LocalYouTubeLayout.current) SectionHeader("Your YouTube library", strapline = "FROM YOUR CONNECTED GOOGLE ACCOUNT")
            Row(Modifier.padding(horizontal = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountShelf.entries.forEach { shelf ->
                    FilterChip(selected = state.shelf == shelf && state.playlist == null, onClick = { model.openAccountShelf(shelf) }, label = { Text(shelf.label) })
                }
            }
            if (state.shelf == AccountShelf.PLAYLISTS && state.playlist == null) TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("New playlist") }
            if (state.playlist != null) TextButton(onClick = { model.openAccountShelf(AccountShelf.PLAYLISTS) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Back to playlists") }
            Text(state.title, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = ready && !state.busy && !state.moreBusy, onClick = { model.loadGoogleLikedIndex(force = true); model.openAccountShelf(state.shelf, state.playlist, state.title) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Refresh") }
            if (!ready) Text("Finish Google sign-in to read account lists.", Modifier.padding(22.dp))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp))
            state.error?.let { Text(it, Modifier.padding(22.dp), color = MaterialTheme.colorScheme.error) }
            state.page?.message?.let { Text(it, Modifier.padding(22.dp)) }
        }
        items(state.page?.items.orEmpty(), key = { "${it.kind}:${it.id}" }) { entry ->
            if (entry.video != null) {
                val video = resolvedVideo(entry.video)
                Column(Modifier.fillMaxWidth().remoteFocus().clickable { open(video) }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                    VideoThumbnail(video, entry.title, (if (video.short) Modifier.width(160.dp) else Modifier.fillMaxWidth()).aspectRatio(VideoArtworkShape.ratio(video.short)))
                    Text(entry.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                    if (state.playlist != null) TextButton(onClick = { model.removeFromPlaylist(state.playlist!!, video) }) { Text("Remove") }
                    IconButton(onClick = { model.save(video) }) { Icon(if (likes[video.id] == true) PrismIcons.HeartFilled else PrismIcons.Heart, if (likes[video.id] == true) "Remove like" else "Like", tint = if (likes[video.id] == true) androidx.compose.ui.graphics.Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        item {
            if (state.moreBusy) CircularProgressIndicator(Modifier.padding(22.dp))
            else if (state.page?.continuation != null) TextButton(onClick = model::moreAccountShelf, modifier = Modifier.padding(horizontal = 16.dp)) { Text("More") }
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
