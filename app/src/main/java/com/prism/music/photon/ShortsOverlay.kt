package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prism.music.ui.theme.PrismIcons

/** Native player visibility drives this overlay; no gesture-intercepting surface over playback. */
@Composable
internal fun ShortsOverlay(visible: Boolean, video: Video, liked: Boolean, modifier: Modifier = Modifier,
    like: () -> Unit, comments: () -> Unit, information: () -> Unit, fullscreen: () -> Unit, channel: () -> Unit, save: () -> Unit, share: () -> Unit, subscribe: (() -> Unit)? = null, subscribed: Boolean = false, vr: (() -> Unit)? = null) {
    if (!visible) return
    var more by remember(video.id) { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.78f), contentColor = Color.White) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(video.title, Modifier.clickable(onClick = information), fontWeight = FontWeight.Bold, maxLines = 2)
            Text(video.channel, Modifier.clickable(onClick = channel).padding(vertical = 8.dp), maxLines = 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = like) { Icon(if (liked) PrismIcons.HeartFilled else PrismIcons.Heart,
                    if (liked) "Unlike" else "Like", tint = if (liked) Color(0xFFFF3355) else Color.White) }
                TextButton(onClick = comments) { Text("Comments", color = Color.White) }
                TextButton(onClick = share) { Text("Share", color = Color.White) }
                Box {
                    TextButton(onClick = { more = true }) { Text("More", color = Color.White) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Details") }, onClick = { more = false; information() })
                        DropdownMenuItem(text = { Text("Save") }, onClick = { more = false; save() })
                        DropdownMenuItem(text = { Text("Full screen") }, onClick = { more = false; fullscreen() })
                        if (vr != null) DropdownMenuItem(text = { Text("VR viewer · 2D") }, onClick = { more = false; vr() })
                        if (subscribe != null) DropdownMenuItem(text = { Text(if (subscribed) "Unsubscribe" else "Subscribe") }, onClick = { more = false; subscribe() })
                    }
                }
            }

        }
    }
}
