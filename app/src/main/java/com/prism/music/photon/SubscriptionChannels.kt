package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Authenticated channel directory, not a locally remembered subscriptions list. */
@Composable
internal fun SubscriptionChannels(model: PhotonViewModel, open: (String) -> Unit) {
    val state by model.subscriptionChannels.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.page == null && !state.busy) model.loadSubscriptionChannels() }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.page?.items.orEmpty(), key = AccountLibraryRows::key) { entry ->
                Column(Modifier.width(80.dp).remoteFocus().clickable { open("https://www.youtube.com/channel/${entry.id}") }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ChannelAvatar(entry, model)
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.page?.continuation != null && !state.busy) item { TextButton(onClick = { model.loadSubscriptionChannels(true) }) { Text("More channels") } }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { TextButton(onClick = { model.loadSubscriptionChannels() }) { Text(it) } }
    }
}
