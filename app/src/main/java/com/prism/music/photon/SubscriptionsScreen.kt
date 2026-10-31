package com.prism.music.photon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.music.ui.theme.PrismIcons

internal object SubscriptionFeedRows {
    fun update(previous: List<AccountEntry>, videos: List<Video>): List<AccountEntry> {
        val old = previous.associateBy { it.video!!.id }
        return videos.distinctBy { it.id }.map { video ->
            val prior = old[video.id]
            val merged = ShortsPresentation.merge(video, prior?.video)
            AccountEntry(video.id, video.title, AccountEntryKind.VIDEO, merged,
                layoutShort = prior?.layoutShort ?: merged.short)
        }
    }
}

@Composable
internal fun SubscriptionsScreen(model: PhotonViewModel, browse: BrowseState, open: (Video) -> Unit,
    channel: (String) -> Unit, search: () -> Unit, settings: () -> Unit, allChannels: () -> Unit) {
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    val evidence by model.videoPresentation.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf(emptyList<AccountEntry>()) }
    LaunchedEffect(browse.videos, browse.busy, browse.query, evidence) {
        rows = if (browse.query != "Google subscriptions" || browse.busy) emptyList()
            else SubscriptionFeedRows.update(rows, browse.videos.map { ShortsPresentation.merge(it, evidence[it.id]) })
    }
    SubscriptionFeedGrid(rows, likes, open, { model.save(it) }, channel,
        commitFormats = { rows = AccountLibraryRows.commitFormats(rows) }, presentation = { resolvedVideo(it) },
        header = {
            YouTubeLayoutHeader("Subscriptions", search = search, settings = settings)
            SubscriptionChannels(model, channel)
            Row {
                TextButton(onClick = model::subscriptionsFeed) { Text("Refresh") }
                TextButton(onClick = allChannels) { Text("All channels") }
            }
            if (browse.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            browse.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            if (browse.query != "Google subscriptions") TextButton(onClick = model::subscriptionsFeed) { Text("Load subscribed videos") }
        })
}

/** Shared by the real feed and rendered regressions: one full-width header, video-dependent spans. */
@Composable
internal fun SubscriptionFeedGrid(rows: List<AccountEntry>, likes: Map<String, Boolean>, open: (Video) -> Unit,
    like: (Video) -> Unit, channel: (String) -> Unit, commitFormats: () -> Unit,
    modifier: Modifier = Modifier, presentation: @Composable (Video) -> Video = { it },
    artwork: @Composable (Video, Modifier, ContentScale) -> Unit = { video, frame, scale -> VideoThumbnail(video, video.title, frame, contentScale = scale) },
    header: @Composable ColumnScope.() -> Unit = {}) {
    val grid = rememberLazyGridState()
    AccountLibraryFormatEffect(grid, rows, commitFormats)
    LazyVerticalGrid(GridCells.Fixed(2), modifier.fillMaxSize(), state = grid,
        horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "subscriptions-header", span = { GridItemSpan(2) }) { Column(Modifier.fillMaxWidth(), content = header) }
        items(rows, key = AccountLibraryRows::key, span = { GridItemSpan(AccountGridPolicy.span(it)) }) { row ->
            val video = presentation(row.video!!)
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).remoteFocus()) {
                val frame = Modifier.fillMaxWidth().aspectRatio(AccountGridPolicy.ratio(row))
                    .testTag("subscription-thumbnail:${video.id}").clickable { open(video) }
                artwork(video, frame, if (video.short != AccountGridPolicy.portrait(row)) ContentScale.Fit else ContentScale.Crop)
                Text(video.title, Modifier.fillMaxWidth().clickable { open(video) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    Text(video.channel, Modifier.weight(1f).clickable { if (video.channelUrl.isNotBlank()) channel(video.channelUrl) },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { like(video) }) { Icon(if (likes[video.id] == true) PrismIcons.HeartFilled else PrismIcons.Heart,
                        if (likes[video.id] == true) "Unlike" else "Like", tint = if (likes[video.id] == true) androidx.compose.ui.graphics.Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}
