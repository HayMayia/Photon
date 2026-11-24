package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppMode
import com.example.data.SleepTimerOption
import com.example.data.UserPlaylist
import com.example.data.YouTubeMediaItem
import com.example.ui.PhotonUiState
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonCheckbox
import com.example.ui.components.PhotonNetworkThumbnail
import com.example.ui.components.PhotonRadioButton
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

private val PhotonSheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

@Composable
private fun PhotonSheetDragHandle() {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    state: PhotonUiState,
    onDismiss: () -> Unit,
    onJumpToIndex: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onClearUpcoming: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PhotonSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = { PhotonSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("queue_bottom_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = PhotonTitleStyle,
                    color = PhotonPrimaryText
                )
                if (state.upcomingQueue.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(PhotonCardShape)
                            .clickable(onClick = onClearUpcoming)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("queue_clear_button")
                    ) {
                        Text(
                            text = stringResource(R.string.queue_clear),
                            style = PhotonCaptionStyle,
                            color = PhotonSecondaryText
                        )
                    }
                }
            }

            state.currentItem?.let { current ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.queue_now_playing),
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(PhotonCardShape)
                        .background(PhotonSurfaceHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhotonNetworkThumbnail(
                        imageUrl = current.highResThumbnailUrl,
                        contentDescription = current.title,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = current.title,
                            style = PhotonBodyMediumStyle,
                            color = PhotonPrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = current.channelName,
                            style = PhotonCaptionStyle,
                            color = PhotonSecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.queue_next_in_queue),
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (state.upcomingQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = PhotonBodyStyle,
                        color = PhotonSecondaryText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(state.upcomingQueue, key = { (idx, item) -> "${idx}_${item.videoId}" }) { (index, item) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToIndex(index) }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                .testTag("queue_item_${item.videoId}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotonNetworkThumbnail(
                                imageUrl = item.highResThumbnailUrl,
                                contentDescription = item.title,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonPrimaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.channelName,
                                    style = PhotonCaptionStyle,
                                    color = PhotonSecondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index > state.currentQueueIndex + 1) {
                                IconButton(
                                    onClick = { onMoveUp(index) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = "Move up",
                                        tint = PhotonSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveIndex(index) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_remove_item),
                                    tint = PhotonSecondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemOptionsBottomSheet(
    item: YouTubeMediaItem,
    isLiked: Boolean,
    isWatchLater: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PhotonSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = { PhotonSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("item_options_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhotonNetworkThumbnail(
                    imageUrl = item.highResThumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = PhotonBodyMediumStyle,
                        color = PhotonPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.channelName,
                        style = PhotonCaptionStyle,
                        color = PhotonSecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SheetActionRow(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(R.string.action_play_next),
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
                testTag = "option_play_next"
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.action_add_to_queue),
                onClick = {
                    onAddToQueue()
                    onDismiss()
                },
                testTag = "option_add_queue"
            )
            SheetActionRow(
                icon = if (isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                label = stringResource(
                    if (isWatchLater) R.string.action_remove_watch_later else R.string.action_watch_later
                ),
                onClick = {
                    onToggleWatchLater()
                    onDismiss()
                },
                testTag = "option_watch_later"
            )
            SheetActionRow(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(if (isLiked) R.string.action_unlike else R.string.action_like),
                onClick = {
                    onToggleLike()
                    onDismiss()
                },
                testTag = "option_toggle_like"
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.action_add_to_playlist),
                onClick = onAddToPlaylist,
                testTag = "option_add_playlist"
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PhotonPrimaryText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = PhotonBodyStyle,
            color = PhotonPrimaryText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistBottomSheet(
    candidateItems: List<YouTubeMediaItem>,
    onDismiss: () -> Unit,
    onCreate: (String, List<YouTubeMediaItem>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var playlistName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PhotonSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = { PhotonSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("create_playlist_sheet")
        ) {
            Text(
                text = stringResource(R.string.create_playlist_title),
                style = PhotonTitleStyle,
                color = PhotonPrimaryText
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(PhotonCardShape)
                    .background(PhotonCanvas)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (playlistName.isEmpty()) {
                    Text(
                        text = stringResource(R.string.create_playlist_name_placeholder),
                        style = PhotonBodyStyle,
                        color = PhotonSecondaryText
                    )
                }
                BasicTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    textStyle = PhotonBodyStyle.copy(color = PhotonPrimaryText),
                    cursorBrush = SolidColor(PhotonPrimaryText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
            }

            if (candidateItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.create_playlist_select_tracks),
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(candidateItems, key = { "pick_${it.videoId}" }) { item ->
                        val checked = item.videoId in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedIds.remove(item.videoId) else selectedIds.add(item.videoId)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotonNetworkThumbnail(
                                imageUrl = item.highResThumbnailUrl,
                                contentDescription = item.title,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonPrimaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.channelName,
                                    style = PhotonCaptionStyle,
                                    color = PhotonSecondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            PhotonCheckbox(
                                checked = checked,
                                onToggle = {
                                    if (checked) selectedIds.remove(item.videoId) else selectedIds.add(item.videoId)
                                },
                                testTag = "pick_checkbox_${item.videoId}"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(PhotonCardShape)
                        .background(PhotonSurfaceHigh)
                        .clickable(onClick = onDismiss)
                        .testTag("cancel_create_playlist_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = PhotonBodyMediumStyle,
                        color = PhotonSecondaryText
                    )
                }

                val canCreate = playlistName.trim().isNotEmpty()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(PhotonCardShape)
                        .background(if (canCreate) PhotonPrimaryText else PhotonSurfaceHighest)
                        .clickable(enabled = canCreate) {
                            val chosen = candidateItems.filter { it.videoId in selectedIds }
                            onCreate(playlistName, chosen)
                        }
                        .testTag("confirm_create_playlist_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.create_playlist_action),
                        style = PhotonBodyMediumStyle,
                        color = if (canCreate) PhotonCanvas else PhotonSecondaryText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    item: YouTubeMediaItem,
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onTogglePlaylist: (String) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PhotonSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = { PhotonSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("add_to_playlist_sheet")
        ) {
            Text(
                text = stringResource(R.string.action_add_to_playlist),
                style = PhotonTitleStyle,
                color = PhotonPrimaryText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SheetActionRow(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.library_create_playlist),
                onClick = {
                    onDismiss()
                    onCreateNewPlaylist()
                },
                testTag = "picker_new_playlist_button"
            )

            if (playlists.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val contains = playlist.items.any { it.videoId == item.videoId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePlaylist(playlist.id) }
                                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonPrimaryText
                                )
                                Text(
                                    text = "${playlist.items.size} items",
                                    style = PhotonCaptionStyle,
                                    color = PhotonSecondaryText
                                )
                            }
                            PhotonCheckbox(
                                checked = contains,
                                onToggle = { onTogglePlaylist(playlist.id) },
                                testTag = "playlist_checkbox_${playlist.id}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsBottomSheet(
    state: PhotonUiState,
    onDismiss: () -> Unit,
    onSelectMode: (AppMode) -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PhotonSheetShape,
        containerColor = PhotonSurfaceCard,
        scrimColor = PhotonCanvas.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = { PhotonSheetDragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("listening_settings_sheet")
        ) {
            item(key = "settings_title") {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = PhotonTitleStyle,
                    color = PhotonPrimaryText
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Autoplay checkbox
            item(key = "autoplay_toggle") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PhotonCardShape)
                        .background(PhotonSurfaceHigh)
                        .clickable { onToggleAutoplay(!state.autoplayEnabled) }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.autoplay_label),
                            style = PhotonBodyMediumStyle,
                            color = PhotonPrimaryText
                        )
                        Text(
                            text = stringResource(R.string.autoplay_desc),
                            style = PhotonCaptionStyle,
                            color = PhotonSecondaryText
                        )
                    }
                    PhotonCheckbox(
                        checked = state.autoplayEnabled,
                        onToggle = { onToggleAutoplay(!state.autoplayEnabled) },
                        testTag = "autoplay_checkbox"
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Default Experience Radio Buttons (YouTube vs Music)
            item(key = "mode_header") {
                Text(
                    text = stringResource(R.string.default_mode_label),
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(AppMode.entries, key = { "mode_pref_${it.name}" }) { mode ->
                val selected = state.appMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PhotonCardShape)
                        .clickable { onSelectMode(mode) }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mode == AppMode.YOUTUBE) "YouTube (Video-first)" else "Music (Audio-first)",
                        style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
                        color = if (selected) PhotonPrimaryText else PhotonSecondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    PhotonRadioButton(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        testTag = "mode_radio_${mode.name.lowercase()}"
                    )
                }
            }

            // Sleep Timer Radio Buttons
            item(key = "sleep_header") {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_label),
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(SleepTimerOption.entries, key = { "sleep_${it.name}" }) { option ->
                val selected = state.sleepTimer == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PhotonCardShape)
                        .clickable { onSelectSleepTimer(option) }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
                        color = if (selected) PhotonPrimaryText else PhotonSecondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    PhotonRadioButton(
                        selected = selected,
                        onClick = { onSelectSleepTimer(option) },
                        testTag = "sleep_radio_${option.name.lowercase()}"
                    )
                }
            }
        }
    }
}
