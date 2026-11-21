package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun TvMusicBackgroundSetting(enabled: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text("Music in background on TV") },
        supportingContent = { Text("Off by default. Allow music while browsing or outside Photon. Regular videos still pause.") },
        trailingContent = { Switch(enabled, change, Modifier.testTag("tv-music-background")) },
        modifier = Modifier.remoteFocus().clickable { change(!enabled) })
}
