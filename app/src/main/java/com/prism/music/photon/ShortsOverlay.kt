package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.PrismIcons

/** Native player visibility drives this overlay; no gesture-intercepting surface over playback. */
@Composable
internal fun ShortsOverlay(visible: Boolean, video: Video, liked: Boolean, modifier: Modifier = Modifier,
    like: () -> Unit, comments: () -> Unit, information: () -> Unit, fullscreen: () -> Unit, channel: () -> Unit, save: () -> Unit, share: () -> Unit, subscribe: (() -> Unit)? = null, subscribed: Boolean = false) {
    if (!visible) return
    Surface(modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.72f), contentColor = Color.White) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(video.title, Modifier.clickable(onClick = information), fontWeight = FontWeight.Bold, maxLines = 2)
            Text(video.channel, Modifier.clickable(onClick = channel).padding(vertical = 8.dp), maxLines = 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = like) { Icon(if (liked) PrismIcons.HeartFilled else PrismIcons.Heart,
                    if (liked) "Unlike" else "Like", tint = if (liked) Color(0xFFFF3355) else Color.White) }
                TextButton(onClick = comments) { Text("Comments", color = Color.White) }
                TextButton(onClick = information) { Text("Details", color = Color.White) }
                IconButton(onClick = fullscreen) { Icon(PrismIcons.Fullscreen, "Full screen") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = save) { Text("Save", color = Color.White) }
                TextButton(onClick = share) { Text("Share", color = Color.White) }
                if (subscribe != null) TextButton(onClick = subscribe) { Text(if (subscribed) "Unsubscribe" else "Subscribe", color = Color.White) }
            }
        }
    }
}
