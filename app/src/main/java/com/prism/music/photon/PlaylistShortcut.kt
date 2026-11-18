package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal object PlaylistShortcutPolicy {
    fun selected(settings: PhotonLibrary): Boolean = settings.youtubeHome && settings.youtubeSessionReady &&
        settings.playlistShortcutRevision == settings.youtubeSessionRevision &&
        settings.playlistShortcutId.matches(Regex("[A-Za-z0-9_-]{2,150}"))
}

@Composable
internal fun PlaylistShortcutChooser(model: PhotonViewModel, dismiss: () -> Unit, selected: (AccountEntry) -> Unit) {
    val library by model.library.collectAsStateWithLifecycle()
    val state by model.homePlaylists.collectAsStateWithLifecycle()
    LaunchedEffect(library.youtubeSessionRevision, library.youtubeSessionReady, library.youtubeHome) { model.loadHomePlaylists() }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose shortcut playlist", style = MaterialTheme.typography.titleLarge)
                Text("This button opens your chosen Google playlist. Long-press it or use Settings to change it.", style = MaterialTheme.typography.bodyMedium)
                if (!library.youtubeHome || !library.youtubeSessionReady) Text("Sign in with your existing Google account to choose a playlist.")
                else {
                    if (state.busy || state.moreBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        if (state.generation == library.youtubeSessionRevision)
                            items(state.page?.items.orEmpty().filter { it.kind == AccountEntryKind.PLAYLIST }, key = { it.id }) { entry ->
                                ListItem(headlineContent = { Text(entry.title) }, modifier = Modifier.remoteFocus().clickable { selected(entry) })
                            }
                        if (!state.busy && state.page?.items?.isEmpty() == true) item { Text("No playlists were returned. Create one in Library, then refresh.") }
                        if (state.page?.continuation != null) item { TextButton(onClick = model::moreHomePlaylists, enabled = !state.moreBusy, modifier = Modifier.remoteFocus()) { Text("More playlists") } }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { model.loadHomePlaylists(force = true) }, enabled = !state.busy, modifier = Modifier.remoteFocus()) { Text("Refresh playlists") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (PlaylistShortcutPolicy.selected(library)) TextButton(onClick = { model.clearPlaylistShortcut(); dismiss() }, modifier = Modifier.remoteFocus()) { Text("Clear") }
                    TextButton(onClick = dismiss, modifier = Modifier.remoteFocus()) { Text("Cancel") }
                }
            }
        }
    }
}
