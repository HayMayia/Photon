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
import androidx.compose.runtime.saveable.rememberSaveable
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
                        settings: () -> Unit, search: () -> Unit, channel: (String) -> Unit) {
    ActualYouTubeHome(model, open, settings, search, shorts, subscriptions, downloads, saved, discover, channel)
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
                              shorts: () -> Unit, subscriptions: () -> Unit, downloads: () -> Unit, saved: () -> Unit, discover: (String) -> Unit, channel: (String) -> Unit) {
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
    var music by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The supplied 1.1.1.0 design: fixed title/actions, mode tabs, then the feed.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Photon", Modifier.weight(1f), style = PhotonTitleStyle)
            IconButton(onClick = search) { Icon(PrismIcons.Search, "Search") }
            IconButton(onClick = {
                if (music) model.openLikedMusic(force = true) else model.refreshYouTubeHome()
            }) { Icon(PrismIcons.Refresh, "Refresh") }
            IconButton(onClick = settings) { Icon(PrismIcons.Settings, "Settings") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "Videos", true to "Music").forEach { (isMusic, title) ->
                Surface(selected = music == isMusic, onClick = {
                    music = isMusic
                    if (isMusic) model.openLikedMusic()
                }, shape = RoundedCornerShape(8.dp),
                    color = if (music == isMusic) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    modifier = Modifier.heightIn(min = 48.dp).remoteFocus()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isMusic) PrismIcons.Heart else PrismIcons.Play, null, Modifier.size(18.dp))
                        Text(title, style = PhotonBodyMediumStyle)
                    }
                }
            }
        }
        if (music) {
            // Reuse the verified native Music shelf, cache, filters, mutations and playlist queue.
            // No Firebase account, fabricated "Your Mix", or search-based recommendations.
            AccountLibraryScreen(model, library.youtubeSessionRevision, library.youtubeSessionReady, open,
                channel = channel, subscriptions = subscriptions)
        } else LazyColumn(Modifier.weight(1f), state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item { PhotonUpdateCard() }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = shorts, label = { Text("Shorts") })
                    AssistChip(onClick = subscriptions, label = { Text("Subscriptions") })
                    AssistChip(onClick = downloads, label = { Text("Downloads") })
                    AssistChip(onClick = { model.openAccountShelf(AccountShelf.HISTORY); saved() }, label = { Text("History") })
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your playlists", Modifier.weight(1f), style = PhotonTitleStyle)
                    TextButton(onClick = { model.openAccountShelf(AccountShelf.PLAYLISTS); saved() }) { Text("See all") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "native-liked-music") {
                        HomePlaylistCover(LikedMusicPlaylist.NAME, "", PrismIcons.Heart, { music = true; model.openLikedMusic() })
                    }
                    items(playlists, key = { it.id }) { entry ->
                        HomePlaylistCover(entry.title, entry.artwork, PrismIcons.Library, {
                            model.openAccountShelf(AccountShelf.PLAYLISTS, entry.id, entry.title); saved()
                        })
                    }
                }
                if (playlistState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                playlistState.error?.let {
                    TextButton(onClick = { model.loadHomePlaylists(force = true) }) { Text("Retry playlists") }
                }
                Text("For you", Modifier.padding(top = 24.dp, bottom = 12.dp), style = PhotonTitleStyle)
                if (feed.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                feed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                feed.page?.message?.let { Text(it, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (feed.error != null || (feed.page == null && !feed.busy)) YouTubeHomeConnection(model)
            }
            // Full-width landscape videos and at most two portrait Shorts per row; never hide
            // account results in nested horizontal carousels or discard the first row's sibling.
            items(rows, key = { it.key }) { row -> HomeRecommendationRow(row, open) }
            item {
                if (feed.loadingMore) CircularProgressIndicator()
                else if (feed.page?.continuation != null) TextButton(onClick = model::moreYouTubeHome) { Text("Load more") }
            }
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
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            if (artwork.isNotBlank()) AsyncImage(artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(title, Modifier.padding(top = 9.dp), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DiscoveryCover(video: Video, featured: Boolean = false, open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = (if (featured) Modifier.fillMaxWidth() else Modifier.width(if (video.short) 142.dp else 240.dp)).remoteFocus()) {
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
