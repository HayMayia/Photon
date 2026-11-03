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
    var historySettings by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google account", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { model.beginYouTubeWebChange(); signingIn = true }) { Text(if (library.youtubeHome) "Switch Google account" else "Sign in with Google") }
            TextButton(modifier = Modifier.weight(0.6f), onClick = model::disconnectYouTubeHome) { Text("Sign out") }
        }
        if (feed.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        feed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { historySettings = !historySettings }) { Text("History & privacy") }
        if (historySettings) {
        Row {
            Text("Pause device history", Modifier.weight(1f))
            Switch(library.historyPaused, model::pauseHistory)
        }
        if (library.youtubeHome) {
            Text("YouTube history: ${history.status.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
            history.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (history.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = model::refreshGoogleHistory) { Text("Check Google history") }
                if (history.status != GoogleHistoryStatus.UNKNOWN) TextButton(enabled = !history.checking,
                    onClick = { model.setGoogleHistoryPaused(history.status == GoogleHistoryStatus.ENABLED) }) {
                    Text(if (history.status == GoogleHistoryStatus.ENABLED) "Pause" else "Resume")
                }
            }
            Row {
                Text("Save plays to YouTube history", Modifier.weight(1f))
                Switch(library.accountHistorySync, model::enableAccountHistory, enabled = library.youtubeSessionReady)
            }
            Row {
                Text("Include short plays", Modifier.weight(1f))
                Switch(library.includeShortPlays, model::includeShortPlays, enabled = library.youtubeSessionReady)
            }
        }
        }
    }
    if (signingIn) YouTubeSessionDialog(done = { index -> signingIn = false; model.connectYouTubeHome(index) }, close = { signingIn = false })
}

internal object YouTubeSessionNavigation {
    // evaluateJavascript returns a JSON-encoded string only after the trusted page confirms LOGGED_IN.
    fun verifiedIndex(result: String): Int? = runCatching {
        val value = Json.parseToJsonElement(result) as? JsonPrimitive
        if (value?.isString != true) null else value.content.toIntOrNull()?.takeIf { it in 0..9 }
    }.getOrNull()
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
    fun finishIfSignedIn(view: WebView, explicit: Boolean = false) {
        val current = view.url.orEmpty()
        if (!active || !YouTubeSessionNavigation.allowed(current) || Uri.parse(current).host !in setOf("www.youtube.com", "m.youtube.com", "youtube.com")) return
        view.evaluateJavascript("(function(){return window.ytcfg && ytcfg.get && ytcfg.get('LOGGED_IN') === true ? String(ytcfg.get('SESSION_INDEX')) : null;})()") { result ->
            if (!active || view.url != current) return@evaluateJavascript
            val index = YouTubeSessionNavigation.verifiedIndex(result)
            if (index != null) { active = false; CookieManager.getInstance().flush(); done(index) }
            else if (explicit) error = "Finish Google's sign-in first. The app will connect account Home automatically when YouTube confirms the login."
        }
    }
    Dialog(onDismissRequest = { active = false; close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Text("Sign in with Google · $host", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
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
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (error == null) finishIfSignedIn(view)
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                                handler.cancel(); error = "Secure connection failed. Sign-in was stopped."
                            }
                        }
                        browser = this
                        loadUrl("https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fwww.youtube.com%2F&service=youtube")
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { active = false; close() }) { Text("Cancel") }
                    TextButton(onClick = { error = null; browser?.let { finishIfSignedIn(it, explicit = true) } }) { Text("Check sign-in") }
                }
            }
        }
    }
}
