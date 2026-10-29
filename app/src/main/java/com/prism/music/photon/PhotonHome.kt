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
                val shortcuts = listOf(
                    Triple("Videos", PrismIcons.Play, { discover("popular videos documentaries") }),
                    Triple("Music", PrismIcons.Song, { discover("official music videos songs") }),
                    Triple("Shorts", PrismIcons.Phone, shorts),
                    Triple("Subscriptions", PrismIcons.Person, subscriptions),
                    Triple("Downloads", PrismIcons.Download, downloads),
                    Triple("Saved", PrismIcons.Heart, saved),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { (label, icon, action) ->
                    AssistChip(onClick = action, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
                }
            }
            SectionHeader("For you")
            TextButton(enabled = !feed.busy, onClick = { model.refreshYouTubeHome() }) { Text("Refresh") }
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
