package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.data.SuggestedMusicArtists
import com.example.data.SuggestedYouTubeChannels
import com.example.data.UserAccountProfile
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.photonPressable
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonSurfaceHigh
import com.example.ui.theme.PhotonSurfaceHighest
import com.example.ui.theme.PhotonTitleStyle

private val SyncSheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YouTubeAccountSyncSheet(
    profile: UserAccountProfile,
    isSyncing: Boolean,
    syncStatusText: String?,
    onDismiss: () -> Unit,
    onOpenLiveWebSync: () -> Unit,
    onResyncExistingSession: () -> Unit,
    onSavePersonalization: (youtubeHandle: String, channels: List<String>, artists: List<String>) -> Unit,
    onSignOut: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedChannels = remember(profile.subscribedChannels) {
        mutableStateListOf<String>().apply { addAll(profile.subscribedChannels) }
    }
    val selectedArtists = remember(profile.favoriteArtists) {
        mutableStateListOf<String>().apply { addAll(profile.favoriteArtists) }
    }
    var customChannelInput by remember { mutableStateOf("") }
    var customArtistInput by remember { mutableStateOf("") }
    var handleInput by remember(profile.youtubeHandle) { mutableStateOf(profile.youtubeHandle) }

    val allChannelOptions = remember(selectedChannels.toList()) {
        (selectedChannels + SuggestedYouTubeChannels).distinct()
    }
    val allArtistOptions = remember(selectedArtists.toList()) {
        (selectedArtists + SuggestedMusicArtists).distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SyncSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PhotonSurfaceHighest)
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("youtube_account_sync_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Google Account Header Card
            item(key = "account_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PhotonCardShape)
                        .background(PhotonSurfaceHigh)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (profile.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profile.photoUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(160)
                                .build(),
                            contentDescription = profile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PhotonSurfaceHighest)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PhotonSurfaceHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = PhotonPrimaryText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = profile.displayName.ifBlank { "Google Account" },
                            style = PhotonBodyMediumStyle,
                            color = PhotonPrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.email.isNotBlank()) {
                            Text(
                                text = profile.email,
                                style = PhotonCaptionStyle,
                                color = PhotonSecondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (profile.youtubeConnected) {
                                buildString {
                                    append("YouTube & YT Music synced")
                                    if (profile.youtubeHandle.isNotBlank()) append(" · ${profile.youtubeHandle}")
                                }
                            } else {
                                "Google signed in · Personalize your feeds below"
                            },
                            style = PhotonCaptionStyle,
                            color = PhotonPrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Live YouTube & YT Music Import CTA
            item(key = "live_yt_import") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PhotonCardShape)
                        .background(PhotonCanvas)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = PhotonPrimaryText,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sync_live_yt_title),
                                style = PhotonBodyMediumStyle,
                                color = PhotonPrimaryText
                            )
                            Text(
                                text = stringResource(R.string.sync_live_yt_desc),
                                style = PhotonCaptionStyle,
                                color = PhotonSecondaryText
                            )
                        }
                    }

                    if (!syncStatusText.isNullOrBlank()) {
                        Text(
                            text = syncStatusText,
                            style = PhotonCaptionStyle,
                            color = PhotonSecondaryText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val connectInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .photonPressable(connectInteraction)
                                .clip(PhotonCardShape)
                                .background(PhotonPrimaryText)
                                .clickable(
                                    interactionSource = connectInteraction,
                                    indication = ripple(),
                                    enabled = !isSyncing,
                                    onClick = onOpenLiveWebSync
                                )
                                .testTag("connect_live_youtube_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = PhotonCanvas,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.sync_importing),
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonCanvas
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = PhotonCanvas,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (profile.youtubeConnected) {
                                        stringResource(R.string.sync_relink_youtube)
                                    } else {
                                        stringResource(R.string.sync_import_youtube)
                                    },
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonCanvas,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (profile.youtubeConnected && !isSyncing) {
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(PhotonCardShape)
                                    .background(PhotonSurfaceHigh)
                                    .clickable(onClick = onResyncExistingSession)
                                    .padding(horizontal = 16.dp)
                                    .testTag("resync_now_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.action_refresh),
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonPrimaryText
                                )
                            }
                        }
                    }
                }
            }

            // 3. YouTube Channel Handle & Subscriptions Tuner
            item(key = "yt_channels_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.sync_channels_header),
                        style = PhotonTitleStyle,
                        color = PhotonPrimaryText
                    )
                    Text(
                        text = stringResource(R.string.sync_channels_subtitle),
                        style = PhotonCaptionStyle,
                        color = PhotonSecondaryText
                    )

                    // Optional YouTube Handle input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(PhotonCardShape)
                            .background(PhotonCanvas)
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (customChannelInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sync_add_channel_placeholder),
                                    style = PhotonBodyStyle,
                                    color = PhotonSecondaryText
                                )
                            }
                            BasicTextField(
                                value = customChannelInput,
                                onValueChange = { customChannelInput = it },
                                singleLine = true,
                                textStyle = PhotonBodyStyle.copy(color = PhotonPrimaryText),
                                cursorBrush = SolidColor(PhotonPrimaryText),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val clean = customChannelInput.trim()
                                        if (clean.isNotEmpty() && selectedChannels.none { it.equals(clean, true) }) {
                                            if (clean.startsWith("@")) handleInput = clean
                                            selectedChannels.add(0, clean.removePrefix("@"))
                                            customChannelInput = ""
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_channel_input")
                            )
                        }
                        IconButton(
                            onClick = {
                                val clean = customChannelInput.trim()
                                if (clean.isNotEmpty() && selectedChannels.none { it.equals(clean, true) }) {
                                    if (clean.startsWith("@")) handleInput = clean
                                    selectedChannels.add(0, clean.removePrefix("@"))
                                    customChannelInput = ""
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_channel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add channel",
                                tint = PhotonPrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allChannelOptions.forEach { channel ->
                            val selected = selectedChannels.any { it.equals(channel, true) }
                            SelectableTagPill(
                                label = channel,
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        selectedChannels.removeAll { it.equals(channel, true) }
                                    } else {
                                        selectedChannels.add(channel)
                                    }
                                },
                                testTag = "channel_chip_${channel.lowercase().replace(" ", "_")}"
                            )
                        }
                    }
                }
            }

            // 4. YouTube Music Artists Tuner
            item(key = "ytm_artists_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.sync_artists_header),
                        style = PhotonTitleStyle,
                        color = PhotonPrimaryText
                    )
                    Text(
                        text = stringResource(R.string.sync_artists_subtitle),
                        style = PhotonCaptionStyle,
                        color = PhotonSecondaryText
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(PhotonCardShape)
                            .background(PhotonCanvas)
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (customArtistInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sync_add_artist_placeholder),
                                    style = PhotonBodyStyle,
                                    color = PhotonSecondaryText
                                )
                            }
                            BasicTextField(
                                value = customArtistInput,
                                onValueChange = { customArtistInput = it },
                                singleLine = true,
                                textStyle = PhotonBodyStyle.copy(color = PhotonPrimaryText),
                                cursorBrush = SolidColor(PhotonPrimaryText),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val clean = customArtistInput.trim()
                                        if (clean.isNotEmpty() && selectedArtists.none { it.equals(clean, true) }) {
                                            selectedArtists.add(0, clean)
                                            customArtistInput = ""
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_artist_input")
                            )
                        }
                        IconButton(
                            onClick = {
                                val clean = customArtistInput.trim()
                                if (clean.isNotEmpty() && selectedArtists.none { it.equals(clean, true) }) {
                                    selectedArtists.add(0, clean)
                                    customArtistInput = ""
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_artist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add artist",
                                tint = PhotonPrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allArtistOptions.forEach { artist ->
                            val selected = selectedArtists.any { it.equals(artist, true) }
                            SelectableTagPill(
                                label = artist,
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        selectedArtists.removeAll { it.equals(artist, true) }
                                    } else {
                                        selectedArtists.add(artist)
                                    }
                                },
                                testTag = "artist_chip_${artist.lowercase().replace(" ", "_")}"
                            )
                        }
                    }
                }
            }

            // 5. Save & Apply + Sign Out
            item(key = "sync_actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val saveInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .photonPressable(saveInteraction)
                            .clip(PhotonCardShape)
                            .background(PhotonPrimaryText)
                            .clickable(
                                interactionSource = saveInteraction,
                                indication = ripple()
                            ) {
                                onSavePersonalization(
                                    handleInput.trim(),
                                    selectedChannels.toList(),
                                    selectedArtists.toList()
                                )
                            }
                            .testTag("save_personalization_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.sync_save_adjust_feed),
                            style = PhotonBodyMediumStyle,
                            color = PhotonCanvas
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(PhotonCardShape)
                            .background(PhotonSurfaceHigh)
                            .clickable(onClick = onSignOut)
                            .testTag("sign_out_button"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = PhotonSecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_sign_out),
                            style = PhotonBodyMediumStyle,
                            color = PhotonPrimaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableTagPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) PhotonPrimaryText else PhotonSurfaceHigh,
        animationSpec = tween(160),
        label = "tag_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) PhotonCanvas else PhotonSecondaryText,
        animationSpec = tween(160),
        label = "tag_fg"
    )
    val chipInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .photonPressable(chipInteraction)
            .clip(PhotonCardShape)
            .background(bgColor)
            .clickable(
                interactionSource = chipInteraction,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Embedded AMOLED dialog allowing the signed-in Google user to link their live YouTube & YT Music
 * web session so Photon can import their actual YouTube Home, Subscriptions, Liked Videos, and
 * YT Music Home / Liked Songs directly via first-party InnerTube cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebSyncDialog(
    onCookiesCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var capturedOnce by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            } catch (_: RuntimeException) {
                android.util.Log.w("PhotonAccount", "Account WebView cleanup failed")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PhotonCanvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("youtube_web_sync_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PhotonSurfaceCard)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_web_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = PhotonPrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sync_web_title),
                        style = PhotonBodyMediumStyle,
                        color = PhotonPrimaryText
                    )
                    Text(
                        text = stringResource(R.string.sync_web_subtitle),
                        style = PhotonCaptionStyle,
                        color = PhotonSecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(Color.BLACK)
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return true
                                val scheme = uri.scheme.orEmpty().lowercase()
                                if (scheme != "https") return true
                                val host = uri.host.orEmpty().lowercase()
                                val trusted = host == "accounts.google.com" ||
                                    host.endsWith(".google.com") ||
                                    host == "youtube.com" ||
                                    host.endsWith(".youtube.com") ||
                                    host.endsWith(".gstatic.com") ||
                                    host.endsWith(".googleapis.com")
                                return !trusted
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (capturedOnce || url.isNullOrBlank()) return
                                if (url.contains("youtube.com")) {
                                    cm.flush()
                                    val ytCookie = cm.getCookie("https://www.youtube.com").orEmpty()
                                    val mCookie = cm.getCookie("https://m.youtube.com").orEmpty()
                                    val combined = if (ytCookie.contains("SAPISID=") || ytCookie.contains("__Secure-3PAPISID=")) {
                                        ytCookie
                                    } else {
                                        mCookie
                                    }
                                    if (combined.contains("SAPISID=") || combined.contains("__Secure-3PAPISID=")) {
                                        capturedOnce = true
                                        onCookiesCaptured(combined)
                                    }
                                }
                            }
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
                    }
                }
            )
        }
    }
}
