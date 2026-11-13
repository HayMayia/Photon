package com.prism.music.photon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Equal TV card frames, unclipped landscape art, visible remote focus and stable two-line titles. */
@Composable
internal fun TvPosterCard(video: Video, open: () -> Unit, modifier: Modifier = Modifier,
    focused: () -> Unit = {}, remove: (() -> Unit)? = null) {
    var hasFocus by remember(video.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hasFocus) 1.025f else 1f, label = "tv-card-focus")
    OutlinedCard(onClick = open,
        border = BorderStroke(if (hasFocus) 3.dp else 1.dp, if (hasFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (hasFocus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth().testTag("tv-video-${video.id}").graphicsLayer { scaleX = scale; scaleY = scale }
            .focusProperties { canFocus = true }
            .onFocusChanged { hasFocus = it.hasFocus; if (it.hasFocus) focused() }) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                VideoThumbnail(video, video.title, if (video.short) Modifier.fillMaxHeight().aspectRatio(9f / 16f) else Modifier.fillMaxSize())
                if (video.short) Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                    Text("Shorts", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(video.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                color = if (hasFocus) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            if (remove != null) TextButton(onClick = remove, modifier = Modifier.remoteFocus()) { Text("Delete download") }
        }
    }
}
