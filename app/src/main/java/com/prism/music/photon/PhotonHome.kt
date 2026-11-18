package com.prism.music.photon

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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
    val library by model.library.collectAsStateWithLifecycle()
    val playlistState by model.homePlaylists.collectAsStateWithLifecycle()
    val playlists = playlistState.page?.items.orEmpty().takeIf {
        library.youtubeHome && library.youtubeSessionReady && playlistState.generation == library.youtubeSessionRevision
    }.orEmpty()
    LaunchedEffect(library.youtubeSessionRevision, library.youtubeSessionReady, library.youtubeHome) { model.loadHomePlaylists() }
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
                    Text("PHOTON", style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp, color = scheme.primary)
                    Text(homeGreeting(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                FilledTonalIconButton(onClick = settings) { Icon(PrismIcons.Settings, "Settings") }
            }
            Surface(onClick = search, shape = RoundedCornerShape(50),
                color = scheme.surfaceContainerHigh, contentColor = scheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = .22f)),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PrismIcons.Search, null, tint = scheme.primary)
                    Text("What do you want to play?", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeCollectionTile("Liked Music", "Your soundtrack", PrismIcons.Song, Modifier.weight(1f), true) { model.openLikedMusic(); saved() }
                HomeCollectionTile("Downloads", "${library.downloads.size} offline", PrismIcons.Download, Modifier.weight(1f), false, downloads)
            }
            Row(Modifier.padding(top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { model.openAccountShelf(AccountShelf.PLAYLISTS); saved() }) { Text("See all") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "native-liked-music") {
                    HomePlaylistCover(LikedMusicPlaylist.NAME, "", PrismIcons.Heart, { model.openLikedMusic(); saved() })
                }
                items(playlists, key = { it.id }) { entry ->
                    HomePlaylistCover(entry.title, entry.artwork, PrismIcons.Library, {
                        model.openAccountShelf(AccountShelf.PLAYLISTS, entry.id, entry.title); saved()
                    })
                }
            }
            if (playlistState.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            playlistState.error?.let {
                TextButton(onClick = { model.loadHomePlaylists(force = true) }) { Text("Retry playlists") }
            }
            Spacer(Modifier.height(18.dp))
            val shortcuts = listOf(
                Triple(LikedMusicPlaylist.NAME, PrismIcons.Song, { model.openLikedMusic(); saved() }),
                Triple("Liked videos", PrismIcons.Heart, { model.openAccountShelf(AccountShelf.LIKED); saved() }),
                Triple("History", PrismIcons.History, { model.openAccountShelf(AccountShelf.HISTORY); saved() }),
                Triple("Playlists", PrismIcons.Library, { model.openAccountShelf(AccountShelf.PLAYLISTS); saved() }),
                Triple("Videos", PrismIcons.Play, { discover("popular videos documentaries") }),
                Triple("Music", PrismIcons.Song, { discover("official music videos songs") }),
                Triple("Channels", PrismIcons.Person, subscriptions),
                Triple("Shorts", PrismIcons.Phone, shorts),
                Triple("Downloads", PrismIcons.Download, downloads)
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { (label, icon, action) ->
                    AssistChip(onClick = action, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
                }
            }
            Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recommended today", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        items(rows.chunked(4), key = { it.first().key }) { shelf ->
            LazyRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shelf.flatMap { it.videos }, key = { it.id }) { source ->
                    val video = resolvedVideo(source)
                    DiscoveryCover(video) { open(video) }
                }
            }
        }
        item {
            if (feed.loadingMore) CircularProgressIndicator()
            else if (feed.page?.continuation != null) TextButton(onClick = model::moreYouTubeHome) { Text("Load more recommendations") }
            else if (!feed.page?.videos.isNullOrEmpty()) Text("YouTube returned no further page. Refresh Home for a new response.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun homeGreeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun HomePlaylistCover(title: String, artwork: String, icon: ImageVector, open: () -> Unit) {
    Column(Modifier.width(142.dp).remoteFocus().clickable(onClick = open)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            if (artwork.isNotBlank()) AsyncImage(artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(title, Modifier.padding(top = 9.dp), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeCollectionTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, accent: Boolean, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier.remoteFocus(), shape = RoundedCornerShape(24.dp),
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DiscoveryCover(video: Video, open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(if (video.short) 142.dp else 240.dp).remoteFocus()) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(VideoArtworkShape.ratio(video.short)).clip(RoundedCornerShape(16.dp))) {
                VideoThumbnail(video, video.title, Modifier.fillMaxSize())
                if (video.duration > 0 || video.short) Surface(Modifier.align(Alignment.BottomEnd).padding(7.dp), shape = RoundedCornerShape(7.dp), color = Color.Black.copy(alpha = .75f)) {
                    Text(if (video.short) "Shorts" else durationLabel(video.duration), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(video.title, Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(video.channel, Modifier.padding(start = 4.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            VideoHeart(video)
        }
    }
}
