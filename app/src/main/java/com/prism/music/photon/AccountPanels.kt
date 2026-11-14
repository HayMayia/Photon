package com.prism.music.photon

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.security.MessageDigest

/** Public app identity only, safe to copy into the Cloud Console; no signing key or OAuth token. */
object GoogleSignInSetup {
    @Suppress("DEPRECATION")
    fun identity(context: Context): String {
        val sha1 = runCatching {
            val signature = if (Build.VERSION.SDK_INT >= 28) context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            signature?.let { MessageDigest.getInstance("SHA-1").digest(it.toByteArray()).joinToString(":") { byte -> "%02X".format(byte.toInt() and 255) } }
        }.getOrNull() ?: "Unavailable on this device"
        return "Package: ${context.packageName}\nSigning certificate SHA-1: $sha1\nScope: $YOUTUBE_COMMENT_SCOPE\nAPI: YouTube Data API v3\nOAuth client type: Android\nNo client secret is needed or accepted in this app."
    }
}

@Composable
internal fun YouTubeAccountCard(model: PhotonViewModel) {
    var comments by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(22.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YouTube account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            YouTubeHomeConnection(model)
            HorizontalDivider()
            TextButton(onClick = { comments = !comments }) { Text("Comment account") }
            if (comments) GoogleAccountActions(model)
        }
    }
}

@Composable
internal fun GoogleAccountActions(model: PhotonViewModel) {
    val state by model.account.collectAsStateWithLifecycle()
    val posting by model.commentPosting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var setup by remember { mutableStateOf(false) }
    state.identity?.let { Text("Comments authorized as ${it.name}", fontWeight = FontWeight.SemiBold) }
        ?: Text("Connect Google to post real YouTube comments.", style = MaterialTheme.typography.bodyMedium)
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(enabled = !state.busy && !posting, onClick = { (context as? PhotonActivity)?.signInToYouTube() }) { Text(if (state.identity == null) "Authorize comments" else "Reconnect comments") }
        if (state.identity != null || state.busy) TextButton(enabled = !posting, onClick = model::disconnectAccount) { Text("Disconnect") }
    }
    TextButton(onClick = { setup = true }) { Text("Google sign-in setup / troubleshooting") }
    if (setup) AlertDialog(onDismissRequest = { setup = false },
        title = { Text("Register this build with Google") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The app owner must enable YouTube Data API v3, configure Google's consent screen/test users, and create an Android OAuth client matching this installed build. Google Play services is required. A fresh CI signing key needs a new registration; use a stable release signing key for distribution.")
            Text(GoogleSignInSetup.identity(context), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(browserIntent("https://github.com/HayMayia/Photon/blob/arena/c217a41b-photon/docs/GOOGLE_SIGN_IN.md")) }) { Text("Setup instructions") }
            TextButton(onClick = { context.startActivity(browserIntent("https://myaccount.google.com/connections")) }) { Text("Manage / revoke Google access") }
        } },
        confirmButton = { TextButton(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, GoogleSignInSetup.identity(context)), "Share public OAuth setup identity"))
        }) { Text("Share package / SHA-1") } },
        dismissButton = { TextButton(onClick = { setup = false }) { Text("Close") } })
}
