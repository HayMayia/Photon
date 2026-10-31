package com.prism.music.photon

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.music.BuildConfig

@Composable
internal fun PhotonUpdateCard() {
    val context = LocalContext.current
    val updater = (context.applicationContext as PhotonApplication).updates
    val state by updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updater.check() }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.release?.let { "Update · ${it.version}" } ?: "v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                TextButton(enabled = !state.busy, onClick = { updater.check(true) }, modifier = Modifier.remoteFocus()) { Text("Check for updates") }
            }
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.release != null && !state.busy) Button(onClick = {
                if (state.permission) runCatching { context.startActivity(updater.permissionIntent()) }
                    .onFailure { android.widget.Toast.makeText(context, "Install permission settings are unavailable on this device.", android.widget.Toast.LENGTH_LONG).show() }
                else updater.install()
            }, modifier = Modifier.remoteFocus()) { Text(if (state.permission) "Allow updates" else "Update") }
            if (state.permission) TextButton(onClick = updater::install, modifier = Modifier.remoteFocus()) { Text("Permission granted · retry") }
        }
    }
}
