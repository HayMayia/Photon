package com.prism.music.photon

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun CollectionScreen(model: PhotonViewModel, library: PhotonLibrary, open: (Video) -> Unit, close: () -> Unit) {
    val page by model.collection.collectAsStateWithLifecycle()
    val busy by model.collectionBusy.collectAsStateWithLifecycle()
    val error by model.collectionError.collectAsStateWithLifecycle()
    val googleSubscriptions by model.googleSubscriptions.collectAsStateWithLifecycle()
    val current = page ?: return
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(PrismIcons.ArrowBack, "Back") }
                Text(current.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            current.channel?.let { channel ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(channel.avatar, null, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        if (channel.subscribers >= 0) Text("${channel.subscribers} subscribers")
                        Text("Subscription changes are sent to YouTube", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = { model.subscribe(channel) }) { Text(if (googleSubscriptions[channel.url.substringAfterLast('/')] == true) "Unsubscribe on YouTube" else "Subscribe on YouTube") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.channelTab(false) }) { Text("Videos") }
                    TextButton(onClick = { model.channelTab(true) }) { Text("Shorts") }
                }
            }
            if (current.description.isNotBlank()) Text(current.description, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (current.videos.isNotEmpty()) FilledTonalButton(onClick = { model.playAll(current.videos) }) { Icon(PrismIcons.Play, null); Text("Play loaded videos") }
        }
        items(current.videos, key = { it.id }) { video -> CommunityVideoRow(video, { open(video) }) }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            if (busy) CircularProgressIndicator(Modifier.padding(20.dp))
            else if (current.next != null) OutlinedButton(onClick = model::moreCollection) { Text("Load more") }
        }
    }
}

@Composable
internal fun LibraryCollections(library: PhotonLibrary, model: PhotonViewModel, channels: Boolean, open: (Video) -> Unit, subscriptions: () -> Unit) {
    Text("Use your Google-account library for channels and playlists. No local-only collection is created.", Modifier.padding(22.dp))
    YouTubeHomeConnection(model)
}

@Composable
internal fun PlaylistPicker(library: PhotonLibrary, video: Video, model: PhotonViewModel, done: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val state by model.accountLibrary.collectAsStateWithLifecycle()
    LaunchedEffect(library.youtubeSessionRevision) { if (library.youtubeSessionReady && library.youtubeHome) model.openAccountShelf(AccountShelf.PLAYLISTS) }
    Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add to YouTube playlist", style = MaterialTheme.typography.headlineSmall)
        Text("This changes the same Google account as Home. New playlists are private; no local fallback.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("New YouTube playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FilledTonalButton(enabled = name.isNotBlank(), onClick = { model.createGooglePlaylist(name, video); done() }) { Text("Create on YouTube and add") }
        if (state.busy) LinearProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.heightIn(max = 340.dp)) {
            items(state.page?.items.orEmpty().filter { it.kind == AccountEntryKind.PLAYLIST }, key = { it.id }) { playlist ->
                ListItem(headlineContent = { Text(playlist.title) }, modifier = Modifier.clickable { model.addToPlaylist(playlist.id, video); done() })
            }
            if (state.page?.continuation != null) item { TextButton(onClick = model::moreAccountShelf) { Text("More YouTube playlists") } }
        }
    }
}

@Composable
internal fun CommentsPanel(model: PhotonViewModel, video: Video) {
    val page by model.comments.collectAsStateWithLifecycle()
    val busy by model.commentsBusy.collectAsStateWithLifecycle()
    val error by model.commentsError.collectAsStateWithLifecycle()
    var replies by remember(video.id) { mutableStateOf(false) }
    val account by model.account.collectAsStateWithLifecycle()
    val posting by model.commentPosting.collectAsStateWithLifecycle()
    val postError by model.commentPostError.collectAsStateWithLifecycle()
    val published by model.lastPublished.collectAsStateWithLifecycle()
    var draft by rememberSaveable(video.id) { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var authorizeComment by remember { mutableStateOf(false) }
    var seenId by remember(video.id) { mutableStateOf(model.lastPublished.value?.comment?.id) }
    LaunchedEffect(published) {
        if (published?.videoId == video.id && published?.comment?.id != seenId) {
            seenId = published?.comment?.id
            draft = ""
        }
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 22.dp)) {
        Text(if (replies) "Replies" else "Comments", style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = { replies = false; model.loadComments(video) }) { Text(if (replies) "Back to comments" else "Refresh") }
            TextButton(onClick = { context.startActivity(browserIntent(video.url)) }) { Text("Post on YouTube") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                GoogleAccountActions(model)
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(10_001) }, enabled = !posting,
                    label = { Text("Public comment") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    supportingText = { Text("${draft.trim().length}/10000 · Top-level comments only; replies remain read-only.") })
                Button(onClick = { if (account.identity == null) authorizeComment = true else confirming = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = CommentComposerPolicy.canReview(draft, posting, account.busy, page?.disabled == true)) {
                    Text(if (posting) "Posting…" else if (account.identity == null) "Authorize and review comment" else "Review and post")
                }
                CommentComposerPolicy.reason(draft, posting, account.busy, page?.disabled == true)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (account.identity == null && CommentComposerPolicy.validDraft(draft)) Text("Your Home login is connected separately. Google comment permission is needed before publishing; your draft will be kept and nothing posts automatically.", style = MaterialTheme.typography.bodySmall)
                if (posting) LinearProgressIndicator(Modifier.fillMaxWidth())
                postError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                published?.takeIf { it.videoId == video.id }?.let { result ->
                    Text("YouTube confirmed your comment · ${result.comment.id}", color = MaterialTheme.colorScheme.primary)
                    Text(result.comment.author, fontWeight = FontWeight.Bold)
                    Text(if (result.comment.html) android.text.Html.fromHtml(result.comment.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString() else result.comment.text)
                    Text("YouTube may moderate or delay its appearance in the public feed.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (page?.disabled == true) item { Text("Comments are disabled for this video.") }
            items(page?.comments.orEmpty(), key = { it.id }) { comment ->
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(comment.author, fontWeight = FontWeight.Bold)
                    Text(if (comment.html) android.text.Html.fromHtml(comment.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString() else comment.text)
                    if (comment.likes >= 0) Text("${comment.likes} likes", style = MaterialTheme.typography.labelSmall)
                    comment.replyPage?.let { next -> TextButton(onClick = { replies = true; model.loadComments(video, replies = next) }) { Text("View replies (${comment.replies.coerceAtLeast(0)})") } }
                }
                HorizontalDivider()
            }
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
                else if (page?.next != null) TextButton(onClick = { model.loadComments(video, more = true) }) { Text("Load more") }
                else if (page?.comments?.isEmpty() == true && page?.disabled != true) Text("No comments returned by YouTube.")
            }
        }
    }
    if (authorizeComment) AlertDialog(onDismissRequest = { authorizeComment = false }, title = { Text("Authorize YouTube commenting?") },
        text = { Text("Google's own screen will request youtube.force-ssl permission to identify your channel and post comments you explicitly confirm. The app does not read your password. Your draft is kept; authorization alone will not publish it. Return here to review and post after authorization.") },
        confirmButton = { TextButton(onClick = { authorizeComment = false; (context as? PhotonActivity)?.signInToYouTube() }) { Text("Continue to Google") } },
        dismissButton = { TextButton(onClick = { authorizeComment = false }) { Text("Cancel") } })
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Post publicly on YouTube?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Channel: ${account.identity?.name.orEmpty()}")
            Text("Video: ${video.title} (${video.id})")
            Text(draft.take(1000) + if (draft.length > 1000) "…" else "", maxLines = 12, overflow = TextOverflow.Ellipsis)
            (CommentComposerPolicy.reason(draft, posting, account.busy, page?.disabled == true)
                ?: if (account.identity == null) "Comment permission is missing. Cancel this review and authorize commenting; your draft stays." else null)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("This submits your full draft to YouTube, not a local note. Check for an existing comment before retrying after a network error.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = account.identity != null && CommentComposerPolicy.canReview(draft, posting, account.busy, page?.disabled == true), onClick = { confirming = false; model.postComment(video, draft) }) { Text("Post publicly") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}

@Composable
@UnstableApi
internal fun ShortsScreen(browse: BrowseState, watch: WatchState, controller: MediaController?, model: PhotonViewModel, fullscreen: () -> Unit, close: () -> Unit) {
    val googleLikes by model.googleLikes.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { browse.videos.size })
    LaunchedEffect(browse.videos) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { index ->
            browse.videos.getOrNull(index)?.let { video -> if (model.watch.value.video?.id != video.id) model.open(video) }
            if (index >= browse.videos.size - 3 && browse.hasMore) model.loadMore()
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        if (browse.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!browse.busy && browse.videos.isEmpty()) Text(browse.error ?: "No Shorts returned. Try refreshing.", Modifier.align(Alignment.Center).padding(24.dp))
        VerticalPager(pager, Modifier.fillMaxSize()) { index ->
            val video = browse.videos[index]
            Column(Modifier.fillMaxSize()) {
                if (index == pager.currentPage) VideoSurface(controller, Modifier.weight(1f).fillMaxWidth(), true)
                else VideoThumbnail(video, video.title, Modifier.weight(1f).fillMaxWidth())
                Text(video.title, Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, maxLines = 2)
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.channel, Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { model.save(video) }) { Icon(if (googleLikes[video.id] == true) PrismIcons.HeartFilled else PrismIcons.Heart,
                        if (googleLikes[video.id] == true) "Unlike on YouTube" else "Like on YouTube", tint = if (googleLikes[video.id] == true) androidx.compose.ui.graphics.Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = fullscreen) { Icon(PrismIcons.Fullscreen, "Full screen") }
                }
                if (index == pager.currentPage && watch.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (index == browse.videos.lastIndex && browse.hasMore) TextButton(onClick = model::loadMore) { Text("Load more Shorts") }
                if (index == pager.currentPage) watch.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            }
        }
        IconButton(onClick = close, modifier = Modifier.align(Alignment.TopStart)) { Icon(PrismIcons.ArrowBack, "Exit Shorts") }
    }
}

@Composable
private fun CommunityVideoRow(source: Video, open: () -> Unit) {
    val video = resolvedVideo(source)
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoThumbnail(video, null, Modifier.width(VideoArtworkShape.rowWidth(video.short).dp).aspectRatio(VideoArtworkShape.ratio(video.short)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(video.channel, style = MaterialTheme.typography.labelMedium)
        }
        VideoHeart(video)
    }
}

@Composable
internal fun CastPanel(casting: PhotonCast, watch: WatchState, pausePhone: () -> Unit) {
    val devices by casting.devices.collectAsStateWithLifecycle()
    val status by casting.status.collectAsStateWithLifecycle()
    val connected by casting.connected.collectAsStateWithLifecycle()
    val busy by casting.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Watch on your TV", style = MaterialTheme.typography.headlineSmall)
        Text("Google Cast and DLNA on the same Wi-Fi. Keep the app open. Uses a combined MP4 source, or your merged download. Live casting is not supported.")
        Text(status, color = MaterialTheme.colorScheme.primary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = casting::discover, enabled = !busy) { Text("Scan again") }
            if (connected != null) {
                TextButton(onClick = { casting.play() }) { Text("Play") }
                TextButton(onClick = { casting.pause() }) { Text("Pause") }
                TextButton(onClick = { casting.stop() }) { Text("Disconnect") }
            }
        }
        if (devices.isEmpty()) Text("No devices found yet. Check Wi-Fi, TV power, local-network permission and router client isolation.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(devices, key = { it.id }) { device ->
                ListItem(headlineContent = { Text(device.name) }, supportingContent = { Text(device.detail) }, modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch { if (casting.cast(device, watch)) pausePhone() }
                })
            }
        }
    }
}


internal fun browserIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER) }
