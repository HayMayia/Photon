package com.prism.music.photon

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
    if (library.youtubeHome) {
        ActualYouTubeHome(model, open, settings, search, shorts, subscriptions, downloads, saved, discover)
        return
    }
    val shelves by model.shelves.collectAsStateWithLifecycle()
    val browse by model.browse.collectAsStateWithLifecycle()
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val quick = (library.saved + library.history + shelves.flatMap { it.videos }.take(6)).distinctBy { it.id }.take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Text("Photon", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = search) { Icon(PrismIcons.Search, "Search all YouTube") }
                    Surface(shape = CircleShape, color = scheme.primaryContainer, onClick = settings) {
                        Icon(PrismIcons.Settings, "Settings", Modifier.padding(13.dp).size(22.dp), tint = scheme.primary)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(PrismTokens.spectrum))
                Text("Every video. Every song. Your light.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                val shortcuts = listOf(
                    Triple("Videos", PrismIcons.Play, { discover("popular videos documentaries") }),
                    Triple("Music", PrismIcons.Song, { discover("official music videos songs") }),
                    Triple("Shorts", PrismIcons.Phone, shorts),
                    Triple("Subscriptions", PrismIcons.Person, subscriptions),
                    Triple("Downloads", PrismIcons.Download, downloads),
                    Triple("Saved", PrismIcons.Heart, saved),
                )
                shortcuts.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { column, (label, icon, action) ->
                            val tint = PrismTokens.spectrumAt((row * 2 + column) / 5f)
                            Surface(onClick = action, modifier = Modifier.weight(1f), shape = ui.tile, color = tint.copy(alpha = 0.12f)) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(icon, null, Modifier.size(23.dp), tint = tint)
                                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { YouTubeHomeConnection(model) }
        if (!library.historyPaused && quick.isNotEmpty()) item {
            SectionHeader("Quick picks", strapline = "YOURS TO PLAY")
            Column(Modifier.padding(horizontal = 22.dp)) {
                quick.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { video ->
                            Row(Modifier.weight(1f).clip(ui.tile).clickable { open(video) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                VideoThumbnail(video, null, Modifier.width(if (video.short) 27.dp else 48.dp).aspectRatio(VideoArtworkShape.ratio(video.short)).clip(ui.smallArt))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                    Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        val continuing = library.history.filter { (library.progress[it.id]?.resumeMs() ?: 0) > 0 }
        if (continuing.isNotEmpty()) item { HomeVideoShelf("Continue watching", "PICK UP WHERE YOU LEFT OFF", continuing, false, open) }
        if (library.downloads.isNotEmpty()) item {
            SectionHeader("Ready offline", strapline = "KEPT ON THIS DEVICE", onMore = downloads)
            LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(library.downloads.take(12), key = { it.path }) { item -> HomeCover(item.video, false) { offline(item) } }
            }
        }
        if (!library.historyPaused) item {
            SectionHeader("Made for you", strapline = "YOUR WATCHING, REFRACTED", onMore = forYou)
            if (browse.personalized && browse.videos.isNotEmpty()) HomeVideoShelf(null, null, browse.videos.take(12), false, open)
            else Surface(Modifier.padding(horizontal = 22.dp), shape = ui.card, color = scheme.primaryContainer.copy(alpha = 0.45f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A little more you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (library.history.isEmpty()) "Watch a few videos or songs to grow your local recommendations." else "Find related videos and songs from your watch history.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = forYou) { Text("Explore your recommendations") }
                }
            }
        }
        if (library.historyPaused) item { Text("Local history and generated recommendations are paused. Search and your saved/downloaded videos remain available.", Modifier.padding(22.dp)) }
        items(if (library.historyPaused) emptyList() else shelves, key = { it.section.id }) { shelf ->
            SectionHeader(shelf.section.title, strapline = shelf.section.subtitle, onMore = { discover(shelf.section.query) })
            when {
                shelf.busy -> Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shelf.error != null -> Column(Modifier.padding(horizontal = 22.dp)) {
                    Text("This YouTube section could not load.", color = scheme.error)
                    TextButton(onClick = { model.refreshShelves(true) }) { Text("Retry sections") }
                }
                shelf.videos.isEmpty() -> Text("No results returned by YouTube.", Modifier.padding(22.dp), color = scheme.onSurfaceVariant)
                else -> HomeVideoShelf(null, null, shelf.videos, shelf.section.music, open)
            }
        }
        if (!library.historyPaused) item { TextButton(onClick = { model.refreshShelves(true) }, modifier = Modifier.padding(16.dp)) { Icon(PrismIcons.Refresh, null); Text("Refresh discovery sections") } }
    }
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
    LaunchedEffect(feed.page?.continuation, feed.page?.videos?.size, feed.loadingMore, feed.autoMore, feed.error) {
        if (feed.page?.continuation == null || feed.loadingMore || !feed.autoMore || feed.error != null || feed.page?.videos.isNullOrEmpty()) return@LaunchedEffect
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 5 }
            .distinctUntilChanged().collect { nearEnd -> if (nearEnd) model.moreYouTubeHome() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(22.dp)) {
        item {
            val ui = LocalUi.current
            val scheme = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR WORLD, REFRACTED", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Text("Photon", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = search) { Icon(PrismIcons.Search, "Search all YouTube") }
                Surface(shape = CircleShape, color = scheme.primaryContainer, onClick = settings) {
                    Icon(PrismIcons.Settings, "Settings", Modifier.padding(13.dp).size(22.dp), tint = scheme.primary)
                }
            }
            Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(2.dp).background(PrismTokens.spectrum))
            Text("Every video. Every song. Your light.", color = scheme.onSurfaceVariant)
            val shortcuts = listOf(
                Triple("Explore videos", PrismIcons.Play, { discover("popular videos documentaries") }),
                Triple("Explore music", PrismIcons.Song, { discover("official music videos songs") }),
                Triple("History", PrismIcons.History, { model.openAccountShelf(AccountShelf.HISTORY); saved() }),
                Triple("Playlists", PrismIcons.Library, { model.openAccountShelf(AccountShelf.PLAYLISTS); saved() }),
                Triple("Liked videos", PrismIcons.Heart, { model.openAccountShelf(AccountShelf.LIKED); saved() }),
                Triple("Channels", PrismIcons.Person, subscriptions),
                Triple("Shorts", PrismIcons.Phone, shorts),
                Triple("Downloads", PrismIcons.Download, downloads)
            )
            shortcuts.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { column, (label, icon, action) ->
                        val tint = PrismTokens.spectrumAt((row * 2 + column) / 7f)
                        Surface(onClick = action, modifier = Modifier.weight(1f), shape = ui.tile, color = tint.copy(alpha = 0.12f)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(icon, null, Modifier.size(23.dp), tint = tint)
                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            SectionHeader("Made for you", strapline = "RECOMMENDED BY YOUR YOUTUBE ACCOUNT")
            TextButton(enabled = !feed.busy, onClick = { model.refreshYouTubeHome() }) { Text("Refresh recommendations") }
            if (feed.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            feed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            feed.page?.message?.let { Text(it, Modifier.padding(vertical = 12.dp)) }
            if (feed.error != null) YouTubeHomeConnection(model)
        }
        items(feed.page?.videos.orEmpty(), key = { it.id }) { source ->
            val video = resolvedVideo(source)
            Column(Modifier.fillMaxWidth().clickable { open(video) }.padding(vertical = 14.dp)) {
                VideoThumbnail(video, video.title, (if (video.short) Modifier.width(160.dp) else Modifier.fillMaxWidth()).aspectRatio(VideoArtworkShape.ratio(video.short)))
                Text(video.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VideoHeart(video)
            }
        }
        item {
            if (feed.loadingMore) CircularProgressIndicator()
            else if (feed.page?.continuation != null) TextButton(onClick = model::moreYouTubeHome) { Text("Load more recommendations") }
            else if (!feed.page?.videos.isNullOrEmpty()) Text("YouTube returned no further page. Refresh Home for a new response.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
