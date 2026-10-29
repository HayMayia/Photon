package com.prism.music.photon

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*

/** Explicit opt-in to a distinct, app-private WebView session. No cookie paste or OAuth-token conversion. */
@Composable
internal fun YouTubeHomeConnection(model: PhotonViewModel) {
    val library by model.library.collectAsStateWithLifecycle()
    val feed by model.youtubeHome.collectAsStateWithLifecycle()
    val history by model.googleHistory.collectAsStateWithLifecycle()
    var confirmSync by remember { mutableStateOf(false) }
    var changePause by remember { mutableStateOf<Boolean?>(null) }
    var disclosure by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your actual YouTube Home", style = MaterialTheme.typography.titleMedium)
        Text("Separate from comment authorization. Uses your YouTube web session, not Photon's local recommendation ranking.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { disclosure = true }) { Text(if (library.youtubeHome) "Review / switch web account" else "Connect YouTube Home") }
            TextButton(modifier = Modifier.weight(0.6f), onClick = model::disconnectYouTubeHome) { Text("Clear web session") }
        }
        if (feed.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        feed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            Text("Additional local history pause", Modifier.weight(1f))
            Switch(library.historyPaused, model::pauseHistory)
        }
        Text("Google's last verified watch-history state: ${history.status.name.lowercase()}. In connected mode, paused, unknown or stale Google state also blocks local tracking. Additional local pause never changes Google's setting.", style = MaterialTheme.typography.bodySmall)
        if (library.youtubeHome) {
            history.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (history.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = model::refreshGoogleHistory) { Text("Check Google history") }
                if (history.status != GoogleHistoryStatus.UNKNOWN) TextButton(enabled = !history.checking,
                    onClick = { changePause = history.status == GoogleHistoryStatus.ENABLED }) {
                    Text(if (history.status == GoogleHistoryStatus.ENABLED) "Pause on YouTube" else "Resume on YouTube")
                }
            }
            Row {
                Text("Report real playback to YouTube history", Modifier.weight(1f))
                Switch(library.accountHistorySync, { enabled -> if (enabled) confirmSync = true else model.enableAccountHistory(false) }, enabled = library.youtubeSessionReady)
            }
            Text("Off until you explicitly enable it. One report after 15 seconds of actual native playback, only with freshly verified enabled Google history; no offline uploads or automatic report retries.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("Connect a YouTube web session?") },
        text = { Text("To read the real YouTube Home feed, Photon needs a separate website session, like Prism's feed integration. Google comment authorization cannot provide it. Google's pages handle your login; Photon does not read password fields. WebView keeps sensitive session cookies in this app's private storage (app backup is disabled), and Photon uses these cookies only with allowlisted YouTube hosts to read Home and history controls. Confirmed Google pause/resume changes and separately opted-in playback reports also make authenticated YouTube requests. Disconnect clears this app's web cookies, not Google's native authorization.\n\nThis unofficial integration can stop working. If Google blocks embedded sign-in, Photon will not disguise its login browser or ask you to paste cookies. Home shows YouTube's returned cards/messages without a generated fallback, Google watch-history controls are read separately; unknown state blocks tracking. Optional playback reports require another explicit opt-in and a confirmed active history setting. Website interactions are handled by YouTube itself.", Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { disclosure = false; model.beginYouTubeWebChange(); signingIn = true }) { Text("Continue to YouTube") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } })
    if (confirmSync) AlertDialog(onDismissRequest = { confirmSync = false }, title = { Text("Add real plays to YouTube history?") },
        text = { Text("Photon will report a video once after at least 15 seconds of real playback, for the connected website account. It checks Google's history setting before sending; paused or unknown state blocks reporting. This can change your account's history and future recommendations. Previously saved local history and downloaded/offline plays are not uploaded. Additional local pause will be turned off when you enable this; Google's pause is never overridden. Switching/reviewing web accounts disables reporting until you opt in again.") },
        confirmButton = { TextButton(onClick = { confirmSync = false; model.enableAccountHistory(true) }) { Text("Enable playback reporting") } },
        dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Cancel") } })
    changePause?.let { paused -> AlertDialog(onDismissRequest = { changePause = null }, title = { Text(if (paused) "Pause YouTube watch history?" else "Resume YouTube watch history?") },
        text = { Text("This changes the connected YouTube WEBSITE account's Google/YouTube watch-history setting, not just Photon. That website account may differ from your Google comment account; review it on YouTube first if unsure. Existing history is not deleted. Photon will verify the resulting state; an unconfirmed change will block tracking, not trigger an automatic retry.") },
        confirmButton = { TextButton(onClick = { changePause = null; model.setGoogleHistoryPaused(paused) }) { Text(if (paused) "Pause account history" else "Resume account history") } },
        dismissButton = { TextButton(onClick = { changePause = null }) { Text("Cancel") } }) }
    if (signingIn) YouTubeSessionDialog(done = { index -> signingIn = false; model.connectYouTubeHome(index) }, close = { signingIn = false })
}

internal object YouTubeSessionNavigation {
    private val hosts = setOf("www.youtube.com", "youtube.com", "m.youtube.com", "accounts.youtube.com", "consent.youtube.com", "accounts.google.com", "consent.google.com", "www.google.com")
    fun allowed(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeSessionDialog(done: (Int) -> Unit, close: () -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false } }
    var error by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf("www.youtube.com") }
    Dialog(onDismissRequest = { active = false; close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Text("YouTube website session · $host", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                Text("If Google says this browser is unsupported, stop here. No cookie-paste or browser-disguise bypass is provided.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        // Keep WebView's real/default user-agent for Google; do not impersonate Chrome to bypass restrictions.
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (YouTubeSessionNavigation.allowed(request.url.toString())) return false
                                error = "Navigation outside the allowed Google/YouTube sign-in hosts was blocked."
                                return true
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                if (url != null && !YouTubeSessionNavigation.allowed(url)) { view.stopLoading(); error = "Untrusted navigation blocked."; return }
                                host = url?.let { Uri.parse(it).host }.orEmpty()
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                                handler.cancel(); error = "Secure connection failed. Sign-in was stopped."
                            }
                        }
                        browser = this
                        loadUrl("https://www.youtube.com/")
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { active = false; close() }) { Text("Cancel") }
                    Button(enabled = error == null, onClick = {
                        val view = browser
                        val current = view?.url.orEmpty()
                        if (!YouTubeSessionNavigation.allowed(current) || Uri.parse(current).host !in setOf("www.youtube.com", "m.youtube.com", "youtube.com")) {
                            error = "Finish sign-in and return to the YouTube website first."
                        } else if (view != null) view.evaluateJavascript("(function(){return window.ytcfg && ytcfg.get ? String(ytcfg.get('SESSION_INDEX')) : null;})()") { result ->
                            if (!active || view.url != current) return@evaluateJavascript
                            val index = runCatching { Json.parseToJsonElement(result).jsonPrimitive.content.toIntOrNull() }.getOrNull()
                            if (index == null || index !in 0..9) error = "Could not verify the web account selection. Open YouTube Home and try again."
                            else { active = false; CookieManager.getInstance().flush(); done(index) }
                        }
                    }) { Text("Use this YouTube session") }
                }
            }
        }
    }
}
