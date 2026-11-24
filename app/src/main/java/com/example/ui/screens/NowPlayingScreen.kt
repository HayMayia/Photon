package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaybackProgress
import com.example.data.PlayerDisplayMode
import com.example.data.RepeatMode
import com.example.data.YouTubeMediaItem
import com.example.data.formatDuration
import com.example.ui.PhotonUiState
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonMediaRow
import com.example.ui.components.PhotonNetworkThumbnail
import com.example.ui.components.PhotonPrimaryPlayButton
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
import com.example.ui.theme.PhotonSurfaceHighest
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun NowPlayingScreen(
    state: PhotonUiState,
    progress: PlaybackProgress,
    onCollapse: () -> Unit,
    onSelectDisplayMode: (PlayerDisplayMode) -> Unit,
    onVideoSlotPositioned: (Rect) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayRelated: (YouTubeMediaItem) -> Unit,
    onOpenItemOptions: (YouTubeMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true, onBack = onCollapse)

    val item = state.currentItem ?: return
    val isLiked = item.videoId in state.likedVideoIds
    val isWatchLater = item.videoId in state.watchLaterVideoIds
    val effectiveDuration = if (progress.durationSec > 0) progress.durationSec else item.durationSec
    val progressFraction = if (effectiveDuration > 0) {
        (progress.positionSec.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = state.playerDisplayMode,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))) togetherWith
                    fadeOut(tween(150))
            },
            label = "player_mode_transition"
        ) { displayMode ->
            if (displayMode == PlayerDisplayMode.VIDEO) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                ) {
                    PlayerTopBar(
                        displayMode = displayMode,
                        onCollapse = onCollapse,
                        onSelectDisplayMode = onSelectDisplayMode,
                        onOpenSettings = onOpenSettings
                    )

                    // Pinned 16:9 Video Viewport (persistent m.youtube.com WebView aligns here)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(16f / 9f)
                            .clip(PhotonCardShape)
                            .background(PhotonSurfaceCard)
                            .onGloballyPositioned { coords ->
                                onVideoSlotPositioned(coords.boundsInRoot())
                            }
                            .testTag("youtube_video_viewport"),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotonNetworkThumbnail(
                            imageUrl = item.highResThumbnailUrl,
                            contentDescription = item.title,
                            modifier = Modifier.matchParentSize()
                        )
                        if (state.isBuffering) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(PhotonCanvas.copy(alpha = 0.50f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PhotonPrimaryText,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        if (state.playerErrorMessage != null) {
                            item(key = "player_error") {
                                PlayerErrorBanner(
                                    message = state.playerErrorMessage,
                                    onSkipNext = onSkipNext,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        item(key = "video_meta") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = PhotonTitleStyle,
                                    color = PhotonPrimaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.metadataLine,
                                    style = PhotonCaptionStyle,
                                    color = PhotonSecondaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        item(key = "video_controls") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!item.isLive) {
                                    ScrubberSection(
                                        progress = progressFraction,
                                        positionSec = progress.positionSec,
                                        durationSec = effectiveDuration,
                                        onSeek = onSeekFraction
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = onSkipPrevious,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("now_playing_prev_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipPrevious,
                                            contentDescription = stringResource(R.string.cd_previous),
                                            tint = PhotonPrimaryText,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    PhotonPrimaryPlayButton(
                                        isPlaying = state.isPlaying,
                                        isBuffering = state.isBuffering,
                                        onClick = onTogglePlay,
                                        size = 56.dp,
                                        iconSize = 30.dp,
                                        testTag = "now_playing_play_button"
                                    )

                                    IconButton(
                                        onClick = onSkipNext,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("now_playing_next_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = stringResource(R.string.cd_next),
                                            tint = PhotonPrimaryText,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item(key = "video_actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WatchActionChip(
                                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    label = stringResource(R.string.library_liked),
                                    active = isLiked,
                                    onClick = onToggleLike,
                                    testTag = "watch_like_chip"
                                )
                                WatchActionChip(
                                    icon = if (isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                                    label = stringResource(R.string.library_watch_later),
                                    active = isWatchLater,
                                    onClick = onToggleWatchLater,
                                    testTag = "watch_later_chip"
                                )
                                WatchActionChip(
                                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    label = "Playlist",
                                    active = false,
                                    onClick = onOpenPlaylistPicker,
                                    testTag = "watch_playlist_chip"
                                )
                                WatchActionChip(
                                    icon = Icons.Outlined.Speed,
                                    label = "${state.playbackSpeed}x",
                                    active = state.playbackSpeed != 1.0f,
                                    onClick = onCycleSpeed,
                                    testTag = "watch_speed_chip"
                                )
                                WatchActionChip(
                                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                                    label = stringResource(R.string.queue_title),
                                    active = false,
                                    onClick = onOpenQueue,
                                    testTag = "watch_queue_chip"
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        item(key = "related_header") {
                            PhotonSectionHeader(title = stringResource(R.string.section_up_next))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (state.isLoadingRelated && state.relatedVideos.isEmpty()) {
                            item(key = "related_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = PhotonSecondaryText,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = state.relatedVideos,
                                key = { "rel_${it.videoId}" },
                                contentType = { "related_row" }
                            ) { related ->
                                PhotonMediaRow(
                                    item = related,
                                    isCurrentItem = state.currentItem?.videoId == related.videoId,
                                    isPlaying = state.isPlaying,
                                    useWidescreenThumb = true,
                                    onClick = { onPlayRelated(related) },
                                    onMoreClick = { onOpenItemOptions(related) },
                                    modifier = Modifier.animateItem(),
                                    testTag = "related_video_${related.videoId}"
                                )
                            }
                        }
                    }
                }
            } else {
                // Calm AMOLED Audio / Music Now Playing
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerTopBar(
                        displayMode = displayMode,
                        onCollapse = onCollapse,
                        onSelectDisplayMode = onSelectDisplayMode,
                        onOpenSettings = onOpenSettings,
                        horizontalPadding = 0.dp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val artScale by animateFloatAsState(
                            targetValue = if (state.isPlaying) 1.0f else 0.94f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "artwork_breathing_scale"
                        )
                        PhotonNetworkThumbnail(
                            imageUrl = item.highResThumbnailUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        if (state.playerErrorMessage != null) {
                            PlayerErrorBanner(
                                message = state.playerErrorMessage,
                                onSkipNext = onSkipNext
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = PhotonTitleStyle,
                                    color = PhotonPrimaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.channelName,
                                    style = PhotonBodyStyle,
                                    color = PhotonSecondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val likeScale by animateFloatAsState(
                                targetValue = if (isLiked) 1.14f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "like_bounce"
                            )
                            IconButton(
                                onClick = onToggleLike,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("now_playing_like_button")
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.cd_like),
                                    tint = if (isLiked) PhotonPrimaryText else PhotonSecondaryText,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = likeScale
                                            scaleY = likeScale
                                        }
                                )
                            }
                        }

                        if (!item.isLive) {
                            ScrubberSection(
                                progress = progressFraction,
                                positionSec = progress.positionSec,
                                durationSec = effectiveDuration,
                                onSeek = onSeekFraction
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TransportToggleIcon(
                                active = state.shuffleEnabled,
                                onClick = onToggleShuffle,
                                testTag = "now_playing_shuffle_button"
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = if (state.shuffleEnabled) PhotonPrimaryText else PhotonSecondaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = onSkipPrevious,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("now_playing_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.cd_previous),
                                    tint = PhotonPrimaryText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            PhotonPrimaryPlayButton(
                                isPlaying = state.isPlaying,
                                isBuffering = state.isBuffering,
                                onClick = onTogglePlay,
                                size = 56.dp,
                                iconSize = 30.dp,
                                testTag = "now_playing_play_button"
                            )

                            IconButton(
                                onClick = onSkipNext,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("now_playing_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.cd_next),
                                    tint = PhotonPrimaryText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            TransportToggleIcon(
                                active = state.repeatMode != RepeatMode.OFF,
                                onClick = onCycleRepeat,
                                testTag = "now_playing_repeat_button"
                            ) {
                                Icon(
                                    imageVector = if (state.repeatMode == RepeatMode.ONE) {
                                        Icons.Filled.RepeatOne
                                    } else {
                                        Icons.Filled.Repeat
                                    },
                                    contentDescription = stringResource(R.string.cd_repeat),
                                    tint = if (state.repeatMode != RepeatMode.OFF) PhotonPrimaryText else PhotonSecondaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PhotonCardShape)
                                .background(PhotonSurfaceCard)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UtilityStripAction(
                                icon = Icons.Outlined.Speed,
                                label = "${state.playbackSpeed}x",
                                active = state.playbackSpeed != 1.0f,
                                onClick = onCycleSpeed,
                                testTag = "now_playing_speed_button",
                                modifier = Modifier.weight(1f)
                            )
                            val sleepText = state.sleepRemainingSec?.let { formatDuration(it) } ?: "Sleep"
                            UtilityStripAction(
                                icon = Icons.Outlined.Bedtime,
                                label = sleepText,
                                active = state.sleepRemainingSec != null,
                                onClick = onOpenSettings,
                                testTag = "now_playing_sleep_button",
                                modifier = Modifier.weight(1f)
                            )
                            UtilityStripAction(
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                label = stringResource(R.string.queue_title),
                                active = false,
                                onClick = onOpenQueue,
                                testTag = "now_playing_queue_button",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    displayMode: PlayerDisplayMode,
    onCollapse: () -> Unit,
    onSelectDisplayMode: (PlayerDisplayMode) -> Unit,
    onOpenSettings: () -> Unit,
    horizontalPadding: Dp = 8.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("now_playing_collapse_button")
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_collapse),
                tint = PhotonPrimaryText,
                modifier = Modifier.size(28.dp)
            )
        }

        Row(
            modifier = Modifier
                .clip(PhotonCardShape)
                .background(PhotonSurfaceCard)
                .padding(3.dp)
                .testTag("player_display_mode_toggle"),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PlayerDisplayMode.entries.forEach { mode ->
                val selected = mode == displayMode
                val bg by animateColorAsState(
                    targetValue = if (selected) PhotonSurfaceHighest else Color.Transparent,
                    animationSpec = tween(160),
                    label = "player_mode_bg"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) PhotonPrimaryText else PhotonSecondaryText,
                    animationSpec = tween(160),
                    label = "player_mode_fg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .clickable { onSelectDisplayMode(mode) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("player_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
                        color = fg
                    )
                }
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("now_playing_settings_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.cd_settings),
                tint = PhotonSecondaryText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun WatchActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (active) PhotonSurfaceHighest else PhotonSurfaceCard,
        animationSpec = tween(160),
        label = "watch_chip_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (active) PhotonPrimaryText else PhotonSecondaryText,
        animationSpec = tween(160),
        label = "watch_chip_fg"
    )
    Row(
        modifier = Modifier
            .photonPressable(interactionSource, pressedScale = 0.94f)
            .clip(PhotonCardShape)
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = if (active) PhotonBodyMediumStyle else PhotonBodyStyle,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerErrorBanner(
    message: String,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = PhotonCaptionStyle,
            color = PhotonSecondaryText,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PhotonSurfaceHigh)
                .clickable(onClick = onSkipNext)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Next",
                style = PhotonCaptionStyle,
                color = PhotonPrimaryText
            )
        }
    }
}

@Composable
private fun ScrubberSection(
    progress: Float,
    positionSec: Int,
    durationSec: Int,
    onSeek: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PhotonFlatScrubber(
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_scrubber")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(positionSec),
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
            Text(
                text = if (durationSec > 0) formatDuration(durationSec) else "--:--",
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
        }
    }
}

@Composable
private fun TransportToggleIcon(
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(PhotonCardShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon()
            if (active) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(PhotonPrimaryText)
                )
            }
        }
    }
}

@Composable
private fun UtilityStripAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val tint = if (active) PhotonPrimaryText else PhotonSecondaryText
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(PhotonCardShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = PhotonCaptionStyle,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun PhotonFlatScrubber(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }
    val smoothProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360),
        label = "scrubber_progress"
    )
    val displayFraction = if (isDragging) dragFraction else smoothProgress

    Box(
        modifier = modifier
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val trackHeight = 3.dp.toPx()
            val centerY = size.height / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = PhotonSurfaceHighest,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = corner
            )

            val activeWidth = (size.width * displayFraction).coerceIn(0f, size.width)
            drawRoundRect(
                color = PhotonPrimaryText,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(activeWidth, trackHeight),
                cornerRadius = corner
            )

            val thumbRadius = if (isDragging) 7.dp.toPx() else 5.dp.toPx()
            drawCircle(
                color = PhotonPrimaryText,
                radius = thumbRadius,
                center = Offset(activeWidth, centerY)
            )
        }
    }
}
