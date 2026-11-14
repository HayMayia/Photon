package com.prism.music.photon

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import com.prism.music.ui.components.SectionHeader
import com.prism.music.ui.theme.*

/** Different content shelves, not a restriction on which YouTube videos can be opened. */
data class HomeSection(val id: String, val title: String, val subtitle: String, val query: String, val music: Boolean = false)
data class HomeShelf(val section: HomeSection, val videos: List<Video> = emptyList(), val busy: Boolean = true, val error: String? = null)
object HomeCatalog {
    val sections = listOf(
        HomeSection("music", "Music for your day", "DISCOVER SONGS · YOUTUBE", "official music videos songs", true),
        HomeSection("videos", "A world to explore", "VIDEOS · BEYOND MUSIC", "science technology travel documentaries"),
        HomeSection("new-music", "New music discoveries", "MUSIC · SEARCH DISCOVERY", "new music releases official music video", true),
        HomeSection("gaming", "Play something different", "GAMING · YOUTUBE", "gaming videos"),
    )
}

@Composable
internal fun PhotonHome(model: PhotonViewModel, library: PhotonLibrary, open: (Video) -> Unit,
                        offline: (SavedDownload) -> Unit, discover: (String) -> Unit, forYou: () -> Unit,
                        shorts: () -> Unit, subscriptions: () -> Unit, downloads: () -> Unit, saved: () -> Unit,
                        settings: () -> Unit, search: () -> Unit) {
    ActualYouTubeHome(model, open, settings, search, shorts, subscriptions, downloads, saved, discover)
}

@Composable
private fun HomeVideoShelf(title: String?, subtitle: String?, videos: List<Video>, music: Boolean, open: (Video) -> Unit) {
    Column {
        if (title != null) SectionHeader(title, strapline = subtitle)
        LazyRow(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(videos, key = { it.id }) { source -> val video = resolvedVideo(source); HomeCover(video, music) { open(video) } }
        }
    }
}

@Composable
private fun HomeCover(source: Video, music: Boolean, open: () -> Unit) {
    val video = resolvedVideo(source)
    val ui = LocalUi.current
    Column(Modifier.width(if (video.short) 144.dp else 240.dp).clickable(onClick = open)) {
        Box(Modifier.fillMaxWidth().aspectRatio(VideoArtworkShape.ratio(video.short)).clip(ui.art).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            VideoThumbnail(video, video.title, Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = ui.smallArt, color = Color.Black.copy(alpha = 0.75f)) {
                Text(durationLabel(video.duration), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(video.title, Modifier.padding(top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        VideoHeart(video)
    }
}


/** Account Home never falls back to local ranking, quick picks or search shelves. */
@Composable
private fun ActualYouTubeHome(model: PhotonViewModel, open: (Video) -> Unit, settings: () -> Unit, search: () -> Unit,
                              shorts: () -> Unit, subscriptions: () -> Unit, downloads: () -> Unit, saved: () -> Unit, discover: (String) -> Unit) {
    val feed by model.youtubeHome.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val rows = rememberHomeFeedRows(feed.page?.videos.orEmpty(), model, list)
    LaunchedEffect(feed.page?.continuation, feed.page?.videos?.size, feed.loadingMore, feed.autoMore, feed.error) {
        if (feed.page?.continuation == null || feed.loadingMore || !feed.autoMore || feed.error != null || feed.page?.videos.isNullOrEmpty()) return@LaunchedEffect
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged().collect { nearEnd -> if (nearEnd) model.moreYouTubeHome() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(22.dp)) {
        item { PhotonUpdateCard() }
        item {
            val scheme = MaterialTheme.colorScheme
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Photon", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Watch. Listen. Discover.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = settings) { Icon(PrismIcons.Settings, "Settings") }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = .20f), scheme.surfaceContainerHigh, scheme.primary.copy(alpha = .08f))))
                .border(1.dp, scheme.primary.copy(alpha = .25f), RoundedCornerShape(28.dp))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("A little more you.", color = scheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Surface(onClick = search, shape = RoundedCornerShape(18.dp), color = scheme.surface.copy(alpha = .78f),
                        contentColor = scheme.onSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(PrismIcons.Search, null, tint = scheme.primary)
                            Text("Search videos, songs & creators", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Your collection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val shortcuts = listOf(
                Triple("Videos", PrismIcons.Play, { discover("popular videos documentaries") }),
                Triple("Music", PrismIcons.Song, { discover("official music videos songs") }),
                Triple("History", PrismIcons.History, { model.openAccountShelf(AccountShelf.HISTORY); saved() }),
                Triple("Playlists", PrismIcons.Library, { model.openAccountShelf(AccountShelf.PLAYLISTS); saved() }),
                Triple(LikedMusicPlaylist.NAME, PrismIcons.Song, { model.openLikedMusic(); saved() }),
                Triple("Liked videos", PrismIcons.Heart, { model.openAccountShelf(AccountShelf.LIKED); saved() }),
                Triple("Channels", PrismIcons.Person, subscriptions),
                Triple("Shorts", PrismIcons.Phone, shorts),
                Triple("Downloads", PrismIcons.Download, downloads)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.chunked(3).forEach { entries ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEach { (label, icon, action) ->
                            Surface(onClick = action, shape = RoundedCornerShape(20.dp),
                                color = scheme.surfaceContainerHigh.copy(alpha = .85f), modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .6f))) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, Modifier.size(24.dp), tint = scheme.primary)
                                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                        repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Made for you", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(enabled = !feed.busy, onClick = { model.refreshYouTubeHome() }) { Text("Refresh") }
            }
            if (feed.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            feed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            feed.page?.message?.let { message ->
                Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(PrismIcons.Play, null, tint = scheme.primary)
                        Text(if (message.contains("watch history is off", ignoreCase = true)) "Your history is paused" else "From YouTube",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(message, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (feed.error != null || (feed.page == null && !feed.busy)) YouTubeHomeConnection(model)
        }
        items(rows, key = { it.key }) { row ->
            HomeRecommendationRow(row, open)
        }
        item {
            if (feed.loadingMore) CircularProgressIndicator()
            else if (feed.page?.continuation != null) TextButton(onClick = model::moreYouTubeHome) { Text("Load more recommendations") }
            else if (!feed.page?.videos.isNullOrEmpty()) Text("YouTube returned no further page. Refresh Home for a new response.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
