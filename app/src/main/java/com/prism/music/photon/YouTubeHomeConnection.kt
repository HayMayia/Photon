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
            Text("Pause local history & resume tracking", Modifier.weight(1f))
            Switch(library.historyPaused, model::pauseHistory)
        }
        Text("Local pause is separate from Google's setting. Photon does not upload playback history or change Google's history/recommendation controls. Connecting Home pauses local tracking by default; you can change it here. Existing history is not erased.", style = MaterialTheme.typography.bodySmall)
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("Connect a YouTube web session?") },
        text = { Text("To read the real YouTube Home feed, Photon needs a separate website session, like Prism's feed integration. Google comment authorization cannot provide it. Google's pages handle your login; Photon does not read password fields. WebView keeps sensitive session cookies in this app's private storage (app backup is disabled), and Photon temporarily sends YouTube cookies only to www.youtube.com to read Home. Disconnect clears this app's web cookies, not Google's native authorization.\n\nThis unofficial integration can stop working. If Google blocks embedded sign-in, Photon will not disguise its login browser or ask you to paste cookies. Home shows YouTube's returned cards/messages without a generated fallback, but it cannot read/sync Google's history-pause switch. Native playback is not added to Google history. Website interactions are handled by YouTube itself.", Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { disclosure = false; signingIn = true }) { Text("Continue to YouTube") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } })
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
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                    TextButton(onClick = close) { Text("Cancel") }
                    Button(enabled = error == null, onClick = {
                        val view = browser
                        val current = view?.url.orEmpty()
                        if (!YouTubeSessionNavigation.allowed(current) || Uri.parse(current).host !in setOf("www.youtube.com", "m.youtube.com", "youtube.com")) {
                            error = "Finish sign-in and return to the YouTube website first."
                        } else if (view != null) view.evaluateJavascript("(function(){return window.ytcfg && ytcfg.get ? String(ytcfg.get('SESSION_INDEX')) : null;})()") { result ->
                            if (!active || view.url != current) return@evaluateJavascript
                            val index = runCatching { Json.parseToJsonElement(result).jsonPrimitive.content.toIntOrNull() }.getOrNull()
                            if (index == null || index !in 0..9) error = "Could not verify the web account selection. Open YouTube Home and try again."
                            else { CookieManager.getInstance().flush(); done(index) }
                        }
                    }) { Text("Use this YouTube session") }
                }
            }
        }
    }
}
