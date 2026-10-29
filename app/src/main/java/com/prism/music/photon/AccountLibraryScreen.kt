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
    val state by model.accountLibrary.collectAsStateWithLifecycle()
    LaunchedEffect(revision, ready) {
        if (ready && state.page == null && !state.busy && state.error == null) model.openAccountShelf(state.shelf, state.playlist, state.title)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionHeader("Your YouTube library", strapline = "FROM YOUR CONNECTED GOOGLE ACCOUNT")
            Row(Modifier.padding(horizontal = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountShelf.entries.forEach { shelf ->
                    FilterChip(selected = state.shelf == shelf && state.playlist == null, onClick = { model.openAccountShelf(shelf) }, label = { Text(shelf.label) })
                }
            }
            if (state.playlist != null) TextButton(onClick = { model.openAccountShelf(AccountShelf.PLAYLISTS) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Back to playlists") }
            Text(state.title, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            Text("Read-only account lists. Local history, bookmarks, channels and downloads are in On device. Google may return only part of its saved activity; unsupported layouts are not replaced with local data.", Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = ready && !state.busy && !state.moreBusy, onClick = { model.openAccountShelf(state.shelf, state.playlist, state.title) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Refresh account list") }
            if (!ready) Text("Finish Google sign-in to read account lists.", Modifier.padding(22.dp))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp))
            state.error?.let { Text(it, Modifier.padding(22.dp), color = MaterialTheme.colorScheme.error) }
            state.page?.message?.let { Text(it, Modifier.padding(22.dp)) }
        }
        items(state.page?.items.orEmpty(), key = { "${it.kind}:${it.id}" }) { entry ->
            if (entry.video != null) {
                Column(Modifier.fillMaxWidth().clickable { open(entry.video) }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                    VideoThumbnail(entry.video, entry.title, Modifier.fillMaxWidth().aspectRatio(16f / 9))
                    Text(entry.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                    Text(entry.video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else ListItem(
                headlineContent = { Text(entry.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(if (entry.kind == AccountEntryKind.CHANNEL) "Subscribed channel · open channel" else "YouTube playlist · open videos") },
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
