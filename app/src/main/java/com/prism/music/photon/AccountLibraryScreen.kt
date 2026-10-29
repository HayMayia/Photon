package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.music.ui.components.SectionHeader

@Composable
internal fun AccountLibraryScreen(model: PhotonViewModel, revision: Long, ready: Boolean, open: (Video) -> Unit, channel: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val state by model.accountLibrary.collectAsStateWithLifecycle()
    LaunchedEffect(revision, ready) {
        if (ready && state.page == null && !state.busy && state.error == null) model.openAccountShelf(state.shelf, state.playlist, state.title)
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("Create private YouTube playlist") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.createGooglePlaylist(name); name = ""; creating = false }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionHeader("Your YouTube library", strapline = "FROM YOUR CONNECTED GOOGLE ACCOUNT")
            Row(Modifier.padding(horizontal = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountShelf.entries.forEach { shelf ->
                    FilterChip(selected = state.shelf == shelf && state.playlist == null, onClick = { model.openAccountShelf(shelf) }, label = { Text(shelf.label) })
                }
            }
            if (state.shelf == AccountShelf.PLAYLISTS && state.playlist == null) TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Create YouTube playlist") }
            if (state.playlist != null) TextButton(onClick = { model.openAccountShelf(AccountShelf.PLAYLISTS) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Back to playlists") }
            Text(state.title, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            Text("Your Google account is the library. Likes, subscriptions and playlist edits are sent to YouTube after confirmation, never saved locally instead. Downloads and playback caches still need device storage. Unsupported states fail rather than inventing success.", Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = ready && !state.busy && !state.moreBusy, onClick = { model.openAccountShelf(state.shelf, state.playlist, state.title) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Refresh account list") }
            if (!ready) Text("Finish Google sign-in to read account lists.", Modifier.padding(22.dp))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp))
            state.error?.let { Text(it, Modifier.padding(22.dp), color = MaterialTheme.colorScheme.error) }
            state.page?.message?.let { Text(it, Modifier.padding(22.dp)) }
        }
        items(state.page?.items.orEmpty(), key = { "${it.kind}:${it.id}" }) { entry ->
            if (entry.video != null) {
                Column(Modifier.fillMaxWidth().clickable { open(entry.video) }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                    VideoThumbnail(entry.video, entry.title, (if (entry.video.short) Modifier.width(160.dp) else Modifier.fillMaxWidth()).aspectRatio(VideoArtworkShape.ratio(entry.video.short)))
                    Text(entry.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                    if (state.playlist != null) TextButton(onClick = { model.removeFromPlaylist(state.playlist!!, entry.video) }) { Text("Remove from YouTube playlist") }
                    Text(entry.video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else ListItem(
                headlineContent = { Text(entry.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(if (entry.kind == AccountEntryKind.CHANNEL) "Subscribed channel · open channel" else "YouTube playlist · open videos") },
                trailingContent = { if (entry.kind == AccountEntryKind.PLAYLIST) TextButton(onClick = { model.deletePlaylist(entry.id, entry.title) }) { Text("Delete on YouTube") } },
                modifier = Modifier.clickable {
                    if (entry.kind == AccountEntryKind.CHANNEL) channel("https://www.youtube.com/channel/${entry.id}")
                    else model.openAccountShelf(AccountShelf.PLAYLISTS, entry.id, entry.title)
                }
            )
        }
        item {
            if (state.moreBusy) CircularProgressIndicator(Modifier.padding(22.dp))
            else if (state.page?.continuation != null) TextButton(onClick = model::moreAccountShelf, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Load more account items") }
        }
    }
}
