package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppMode
import com.example.data.LibraryFilter
import com.example.data.LibrarySort
import com.example.data.UserPlaylist
import com.example.data.YouTubeMediaItem
import com.example.ui.PhotonUiState
import com.example.ui.PhotonViewModel
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonFilterChipRow
import com.example.ui.components.PhotonMediaRow
import com.example.ui.components.PhotonNetworkThumbnail
import com.example.ui.components.PhotonSectionHeader
import com.example.ui.components.photonPressable
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonSurfaceHigh
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun LibraryScreen(
    state: PhotonUiState,
    bottomContentPadding: Dp,
    onSelectFilter: (LibraryFilter) -> Unit,
    onToggleSort: () -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onPlayItem: (YouTubeMediaItem, List<YouTubeMediaItem>) -> Unit,
    onOpenItemOptions: (YouTubeMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortList: (List<YouTubeMediaItem>) -> List<YouTubeMediaItem> = { list ->
        if (state.librarySort == LibrarySort.ALPHABETICAL) list.sortedBy { it.title.lowercase() } else list
    }

    val liked = remember(state.likedItems, state.librarySort) { sortList(state.likedItems) }
    val watchLater = remember(state.watchLaterItems, state.librarySort) { sortList(state.watchLaterItems) }
    val history = remember(state.historyItems, state.librarySort) { sortList(state.historyItems) }
    val playlists = remember(state.userPlaylists, state.librarySort) {
        if (state.librarySort == LibrarySort.ALPHABETICAL) {
            state.userPlaylists.sortedBy { it.name.lowercase() }
        } else {
            state.userPlaylists
        }
    }

    val useWidescreen = state.appMode == AppMode.YOUTUBE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .testTag("library_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding)
    ) {
        item(key = "library_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = PhotonTitleStyle,
                    color = PhotonPrimaryText
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(PhotonCardShape)
                            .background(PhotonSurfaceCard)
                            .clickable(onClick = onToggleSort)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("library_sort_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = PhotonSecondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.librarySort.label,
                            style = PhotonCaptionStyle,
                            color = PhotonPrimaryText
                        )
                    }

                    IconButton(
                        onClick = onOpenCreatePlaylist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_create_playlist),
                            tint = PhotonPrimaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item(key = "library_filters") {
            PhotonFilterChipRow(
                items = LibraryFilter.entries,
                selectedItem = state.libraryFilter,
                labelProvider = { it.label },
                onSelect = onSelectFilter,
                tagPrefix = "library_filter"
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        when (state.libraryFilter) {
            LibraryFilter.ALL -> {
                item(key = "pinned_collections") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PinnedCollectionCard(
                            title = stringResource(R.string.library_watch_later),
                            subtitle = "${watchLater.size} saved ${if (watchLater.size == 1) "video" else "videos"}",
                            icon = Icons.Filled.WatchLater,
                            thumbnailUrl = watchLater.firstOrNull()?.highResThumbnailUrl,
                            onClick = { onOpenCollection(PhotonViewModel.COLLECTION_WATCH_LATER) },
                            testTag = "library_watch_later_card"
                        )
                        PinnedCollectionCard(
                            title = stringResource(R.string.library_liked),
                            subtitle = "${liked.size} liked ${if (liked.size == 1) "item" else "items"}",
                            icon = Icons.Filled.Favorite,
                            thumbnailUrl = liked.firstOrNull()?.highResThumbnailUrl,
                            onClick = { onOpenCollection(PhotonViewModel.COLLECTION_LIKED) },
                            testTag = "library_liked_card"
                        )
                        PinnedCollectionCard(
                            title = stringResource(R.string.library_history),
                            subtitle = "${history.size} recently played",
                            icon = Icons.Filled.History,
                            thumbnailUrl = history.firstOrNull()?.highResThumbnailUrl,
                            onClick = { onOpenCollection(PhotonViewModel.COLLECTION_HISTORY) },
                            testTag = "library_history_card"
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                if (playlists.isNotEmpty()) {
                    item(key = "playlists_header") {
                        PhotonSectionHeader(title = stringResource(R.string.library_playlists))
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(playlists, key = { it.id }, contentType = { "playlist_row" }) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            onClick = { onOpenCollection(playlist.id) },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    item(key = "playlists_spacer") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                if (history.isNotEmpty()) {
                    item(key = "recent_history_header") {
                        PhotonSectionHeader(title = stringResource(R.string.library_history))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(history.take(8), key = { "lib_hist_${it.videoId}" }, contentType = { "media_row" }) { item ->
                        PhotonMediaRow(
                            item = item,
                            isCurrentItem = state.currentItem?.videoId == item.videoId,
                            isPlaying = state.isPlaying,
                            useWidescreenThumb = useWidescreen,
                            onClick = { onPlayItem(item, history) },
                            onMoreClick = { onOpenItemOptions(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            LibraryFilter.WATCH_LATER -> {
                if (watchLater.isEmpty()) {
                    item(key = "wl_empty") { LibraryEmptyState() }
                } else {
                    items(watchLater, key = { "wl_${it.videoId}" }, contentType = { "media_row" }) { item ->
                        PhotonMediaRow(
                            item = item,
                            isCurrentItem = state.currentItem?.videoId == item.videoId,
                            isPlaying = state.isPlaying,
                            useWidescreenThumb = useWidescreen,
                            onClick = { onPlayItem(item, watchLater) },
                            onMoreClick = { onOpenItemOptions(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            LibraryFilter.LIKED -> {
                if (liked.isEmpty()) {
                    item(key = "liked_empty") { LibraryEmptyState() }
                } else {
                    items(liked, key = { "lk_${it.videoId}" }, contentType = { "media_row" }) { item ->
                        PhotonMediaRow(
                            item = item,
                            isCurrentItem = state.currentItem?.videoId == item.videoId,
                            isPlaying = state.isPlaying,
                            useWidescreenThumb = useWidescreen,
                            onClick = { onPlayItem(item, liked) },
                            onMoreClick = { onOpenItemOptions(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            LibraryFilter.HISTORY -> {
                if (history.isEmpty()) {
                    item(key = "hist_empty") { LibraryEmptyState() }
                } else {
                    items(history, key = { "hs_${it.videoId}" }, contentType = { "media_row" }) { item ->
                        PhotonMediaRow(
                            item = item,
                            isCurrentItem = state.currentItem?.videoId == item.videoId,
                            isPlaying = state.isPlaying,
                            useWidescreenThumb = useWidescreen,
                            onClick = { onPlayItem(item, history) },
                            onMoreClick = { onOpenItemOptions(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            LibraryFilter.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    item(key = "pl_empty") { LibraryEmptyState() }
                } else {
                    items(playlists, key = { it.id }, contentType = { "playlist_row" }) { playlist ->
                        UserPlaylistRow(
                            playlist = playlist,
                            onClick = { onOpenCollection(playlist.id) },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedCollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .photonPressable(interactionSource = interactionSource, pressedScale = 0.98f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            PhotonNetworkThumbnail(
                imageUrl = thumbnailUrl,
                contentDescription = title,
                modifier = Modifier.size(52.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(PhotonCardShape)
                    .background(PhotonSurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PhotonPrimaryText,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText
            )
            Text(
                text = subtitle,
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
        }
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = playlist.items.firstOrNull()?.highResThumbnailUrl
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .photonPressable(interactionSource = interactionSource, pressedScale = 0.98f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(12.dp)
            .testTag("user_playlist_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!coverUrl.isNullOrBlank()) {
            PhotonNetworkThumbnail(
                imageUrl = coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(PhotonCardShape)
                    .background(PhotonSurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = PhotonSecondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playlist.name,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist · ${playlist.items.size} ${if (playlist.items.size == 1) "item" else "items"}",
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
        }
    }
}

@Composable
private fun LibraryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.LibraryMusic,
            contentDescription = null,
            tint = PhotonSecondaryText,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.library_empty_title),
            style = PhotonBodyMediumStyle,
            color = PhotonPrimaryText
        )
        Text(
            text = stringResource(R.string.library_empty_subtitle),
            style = PhotonBodyStyle,
            color = PhotonSecondaryText
        )
    }
}
