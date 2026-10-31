package com.prism.music.photon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

internal val LocalYouTubeLayout = staticCompositionLocalOf { false }

/** Native presentation only. Never creates a second player, account, transport, or library. */
@Composable
internal fun InterfaceTheme(youtube: Boolean, dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (!youtube) MaterialTheme.colorScheme else if (dark) darkColorScheme(
        primary = Color.White, onPrimary = Color.Black, secondary = Color.White,
        background = Color(0xFF0F0F0F), surface = Color(0xFF0F0F0F), surfaceContainer = Color(0xFF212121),
        surfaceContainerHigh = Color(0xFF272727), primaryContainer = Color(0xFF303030), onPrimaryContainer = Color.White,
    ) else lightColorScheme(primary = Color.Black, onPrimary = Color.White, secondary = Color.Black,
        background = Color.White, surface = Color.White, surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFEEEEEE), primaryContainer = Color(0xFFE5E5E5), onPrimaryContainer = Color.Black)
    // Keep this call site stable when switching: retained player effects must not be recreated.
    MaterialTheme(colorScheme = colors, typography = if (youtube) Typography() else MaterialTheme.typography,
        shapes = if (youtube) Shapes() else MaterialTheme.shapes) {
        CompositionLocalProvider(LocalYouTubeLayout provides youtube, content = content)
    }
}

internal object YouTubeLayoutIcons {
    private fun vector(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()
    val Home = vector("Home", "M12 3L2 12h3v9h6v-6h2v6h6v-9h3z")
    val Search = vector("Search", "M9.5 3a6.5 6.5 0 1 0 4.1 11.55L19.55 20 21 18.55l-5.45-5.95A6.5 6.5 0 0 0 9.5 3M9.5 5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9")
    val Person = vector("Account", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20M12 4a8 8 0 0 1 6.2 13.05C16.7 15.75 14.6 15 12 15s-4.7.75-6.2 2.05A8 8 0 0 1 12 4M12 6a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7")
    val Subscriptions = vector("Subscriptions", "M5 2h14v2H5zM3 5h18v2H3zM2 8h20v14H2zM4 10v10h16V10zM10 12l6 3-6 3z")
    val Shorts = vector("Shorts", "M15.5 2.3a4 4 0 0 1 4 6.9L16 11.3l1.7 1a4 4 0 0 1 0 6.9l-9.2 5.3a4 4 0 0 1-4-6.9L8 15.5l-1.7-1a4 4 0 0 1 0-6.9zM10 9v8l6-4z")
    val Add = vector("Create", "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z")
    val More = vector("More", "M12 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4M12 17a2 2 0 1 0 0 4 2 2 0 0 0 0-4")
    val Settings = vector("Settings", "M19.4 13a7.8 7.8 0 0 0 0-2l2-1.5-2-3.4-2.3 1a8 8 0 0 0-1.7-1L15 3h-4l-.4 3.1a8 8 0 0 0-1.7 1l-2.3-1-2 3.4 2 1.5a7.8 7.8 0 0 0 0 2l-2 1.5 2 3.4 2.3-1a8 8 0 0 0 1.7 1L11 21h4l.4-3.1a8 8 0 0 0 1.7-1l2.3 1 2-3.4zM13 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8")
}

@Composable
internal fun YouTubeLayoutHeader(title: String? = null, search: () -> Unit, settings: () -> Unit, cast: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (title == null) {
            Box(Modifier.size(31.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFF0033)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, fontSize = 15.sp)
            }
            Text("YouTube", Modifier.weight(1f).padding(start = 4.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        } else Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (cast != null) IconButton(onClick = cast) { Icon(com.prism.music.ui.theme.PrismIcons.Cast, "Cast") }
        IconButton(onClick = search) { Icon(YouTubeLayoutIcons.Search, "Search") }
        IconButton(onClick = settings) { Icon(if (title == "You") YouTubeLayoutIcons.Settings else YouTubeLayoutIcons.Person, "Settings and account") }
    }
}

@Composable
internal fun YouTubeLayoutNavigation(selected: Int, home: () -> Unit, shorts: () -> Unit, create: () -> Unit, subscriptions: () -> Unit, you: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun item(label: String, icon: ImageVector, active: Boolean, action: () -> Unit) {
            Column(Modifier.weight(1f).fillMaxHeight().clickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, label, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else 0.7f))
                Text(label, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
        item("Home", YouTubeLayoutIcons.Home, selected == 0, home)
        item("Shorts", YouTubeLayoutIcons.Shorts, selected == 5, shorts)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = create, modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) { Icon(YouTubeLayoutIcons.Add, "Create playlist") }
        }
        item("Subscriptions", YouTubeLayoutIcons.Subscriptions, selected == 4, subscriptions)
        item("You", YouTubeLayoutIcons.Person, selected == 2 || selected == 3, you)
    }
}

@Composable
internal fun YouTubeLayoutVideo(source: Video, open: () -> Unit, like: () -> Unit, channel: () -> Unit, modifier: Modifier = Modifier) {
    val video = resolvedVideo(source)
    val model: PhotonViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val likes by model.googleLikes.collectAsStateWithLifecycle()
    var menu by remember(video.id) { mutableStateOf(false) }
    Column(modifier.padding(bottom = 16.dp)) {
        Box((if (video.short) Modifier.width(164.dp) else Modifier.fillMaxWidth()).aspectRatio(VideoArtworkShape.ratio(video.short)).clip(RoundedCornerShape(8.dp)).clickable(onClick = open)) {
            VideoThumbnail(video, video.title, Modifier.fillMaxSize())
            if (!video.short) Text(durationLabel(video.duration), Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp), color = Color.White, fontSize = 12.sp)
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            if (!video.short) Box(Modifier.padding(end = 12.dp).size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = channel), contentAlignment = Alignment.Center) {
                if (video.channelAvatar.isNotBlank()) coil3.compose.AsyncImage(video.channelAvatar, video.channel,
                    Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Text(video.channel.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).clickable(onClick = open)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(listOf(video.channel, viewLabel(video.views), video.published).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = like, modifier = Modifier.size(30.dp)) {
                Icon(if (likes[video.id] == true) com.prism.music.ui.theme.PrismIcons.HeartFilled else com.prism.music.ui.theme.PrismIcons.Heart,
                    if (likes[video.id] == true) "Unlike" else "Like", Modifier.size(20.dp),
                    tint = if (likes[video.id] == true) Color(0xFFFF3355) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(YouTubeLayoutIcons.More, "Video actions", Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; open() })
                    DropdownMenuItem(text = { Text(if (likes[video.id] == true) "Remove like" else "Like") }, onClick = { menu = false; like() })
                    if (video.channelUrl.isNotBlank()) DropdownMenuItem(text = { Text("View channel") }, onClick = { menu = false; channel() })
                }
            }
        }
    }
}

@Composable
internal fun YouTubeLayoutHome(model: PhotonViewModel, library: PhotonLibrary, open: (Video) -> Unit, search: () -> Unit,
                               settings: () -> Unit, cast: () -> Unit, topic: (String) -> Unit, shorts: () -> Unit, channel: (String) -> Unit) {
    val feed by model.youtubeHome.collectAsStateWithLifecycle()
    val browse by model.browse.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(feed.page?.continuation, feed.loadingMore, feed.autoMore, feed.error, library.youtubeHome) {
        if (!library.youtubeHome || feed.page?.continuation == null || feed.loadingMore || !feed.autoMore || feed.error != null || feed.page?.videos.isNullOrEmpty()) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } == true }
            .distinctUntilChanged().collect { if (it) model.moreYouTubeHome() }
    }
    val videos = if (library.youtubeHome) feed.page?.videos.orEmpty() else browse.videos
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PhotonUpdateCard() }
        item { YouTubeLayoutHeader(search = search, settings = settings, cast = cast) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, model::home, label = { Text("All") }, shape = RoundedCornerShape(8.dp))
                listOf("Music" to "official music videos", "Gaming" to "gaming videos", "Learning" to "educational documentaries").forEach { (label, query) ->
                    FilterChip(false, { topic(query) }, label = { Text(label) }, shape = RoundedCornerShape(8.dp))
                }
                FilterChip(false, shorts, label = { Text("Shorts") }, shape = RoundedCornerShape(8.dp))
            }
        }
        if (if (library.youtubeHome) feed.busy else browse.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val error = if (library.youtubeHome) feed.error else browse.error
        if (error != null) item { Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error); TextButton(onClick = model::home) { Text("Retry") } }
        feed.page?.message?.takeIf { library.youtubeHome }?.let { message -> item { Text(message, Modifier.padding(16.dp)) } }
        if (!library.youtubeHome) item { TextButton(onClick = settings, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Sign in for your recommendations") } }
        if (videos.any { it.short }) item {
            Text("Shorts", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(videos.filter { it.short }, key = { it.id }) { video ->
                    YouTubeLayoutVideo(video, { open(video) }, { model.save(video) }, { channel(video.channelUrl) }, Modifier.width(164.dp))
                }
            }
        }
        items(videos.filterNot { it.short }, key = { it.id }) { video -> YouTubeLayoutVideo(video, { open(video) }, { model.save(video) }, { channel(video.channelUrl) }) }
        item {
            if (library.youtubeHome && feed.loadingMore || !library.youtubeHome && browse.moreBusy) CircularProgressIndicator(Modifier.padding(16.dp))
            else if (library.youtubeHome && feed.page?.continuation != null) TextButton(onClick = model::moreYouTubeHome) { Text("Load more") }
            else if (!library.youtubeHome && browse.hasMore) TextButton(onClick = model::loadMore) { Text("Load more") }
        }
    }
}

@Composable
internal fun YouTubeLayoutSearch(model: PhotonViewModel, browse: BrowseState, query: String, setQuery: (String) -> Unit,
                                 submit: () -> Unit, back: () -> Unit, open: (Video) -> Unit, channel: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(com.prism.music.ui.theme.PrismIcons.ArrowBack, "Back") }
                OutlinedTextField(query, setQuery, Modifier.weight(1f), placeholder = { Text("Search YouTube") }, singleLine = true,
                    shape = RoundedCornerShape(28.dp), trailingIcon = { IconButton(onClick = submit) { Icon(YouTubeLayoutIcons.Search, "Search") } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }))
            }
        }
        if (browse.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        browse.error?.let { error -> item { Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
        if (query.isNotBlank() && browse.query == query.trim()) items(browse.videos, key = { it.id }) { video -> YouTubeLayoutVideo(video, { open(video) }, { model.save(video) }, { channel(video.channelUrl) }) }
        if (browse.hasMore && query.isNotBlank() && browse.query == query.trim()) item { if (browse.moreBusy) CircularProgressIndicator(Modifier.padding(16.dp)) else TextButton(onClick = model::loadMore) { Text("Load more") } }
    }
}
