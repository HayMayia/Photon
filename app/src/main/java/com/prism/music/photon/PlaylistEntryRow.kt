package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.PrismIcons

/** Compact landscape entries leave several songs visible, not a screen-sized card per song. */
@Composable
internal fun PlaylistEntryRow(video: Video, liked: Boolean, open: () -> Unit, like: () -> Unit, remove: (() -> Unit)? = null) {
    var menu by remember(video.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).testTag("playlist-entry-${video.id}").remoteFocus().clickable(onClick = open).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VideoThumbnail(video, video.title, Modifier.width(104.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = like, modifier = Modifier.size(40.dp)) {
            Icon(if (liked) PrismIcons.HeartFilled else PrismIcons.Heart, if (liked) "Remove like" else "Like")
        }
        if (remove != null) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(YouTubeLayoutIcons.More, "Playlist entry actions") }
            DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; remove() }) }
        }
    }
}
