package com.prism.music.photon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.music.ui.components.pane
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.PrismTokens

/** Quiet unless an actual newer release is known; Prism's glass/spectrum banner treatment. */
@Composable
internal fun PhotonUpdateCard() {
    val context = LocalContext.current
    val updater = (context.applicationContext as PhotonApplication).updates
    val state by updater.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) updater.install()
    }
    LaunchedEffect(Unit) { updater.check() }
    DisposableEffect(owner, updater) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) updater.check() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.permission) {
        if (state.permission) runCatching { allow.launch(updater.permissionIntent()) }
            .onFailure { android.widget.Toast.makeText(context, "Install permission settings are unavailable on this device.", android.widget.Toast.LENGTH_LONG).show() }
    }
    val release = state.release ?: return
    val ui = LocalUi.current
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = ui.gap(14.dp)).fillMaxWidth()
        .pane(ui.shape(16.dp)).border(1.5.dp, PrismTokens.spectrum, ui.shape(16.dp)).remoteFocus()
        .clickable(enabled = !state.busy) {
            if (state.permission) runCatching { allow.launch(updater.permissionIntent()) }
            else updater.install()
        }) {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PrismIcons.Update, null, tint = ink)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = if (state.busy) 10.dp else 0.dp)) {
                Text("Update to ${PhotonReleasePolicy.displayVersion(release.version)}", style = MaterialTheme.typography.titleSmall, color = ink)
                Text(if (state.busy || state.permission || state.message.contains("couldn't", true) || state.message.startsWith("Confirm")) state.message else "Tap to update",
                    style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .8f))
            }
            if (!state.busy) IconButton(onClick = updater::dismiss, modifier = Modifier.remoteFocus()) { Icon(PrismIcons.Close, "Dismiss update", tint = ink) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
}
