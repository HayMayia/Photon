package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppMode
import com.example.data.PlaybackProgress
import com.example.data.YouTubeMediaItem
import com.example.ui.theme.PhotonAccentTeal
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonOnAccent
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonSurfaceHigh
import com.example.ui.theme.PhotonSurfaceHighest
import com.example.ui.theme.PhotonTitleStyle

val PhotonCardShape = RoundedCornerShape(12.dp)

/**
 * Tactile spring scale press animation for buttons, cards, and chips.
 */
fun Modifier.photonPressable(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Primary Play/Pause button — strictly circular, filled with #2DD4BF teal
 * and #000000 icon. Animated play/pause/buffering morph + tactile press spring.
 */
@Composable
fun PhotonPrimaryPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    testTag: String = "primary_play_button"
) {
    val contentDesc = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    val interactionSource = remember { MutableInteractionSource() }
    val visualState = when {
        isBuffering -> 2
        isPlaying -> 1
        else -> 0
    }

    Box(
        modifier = modifier
            .photonPressable(interactionSource, pressedScale = 0.90f)
            .size(size)
            .clip(CircleShape)
            .background(PhotonAccentTeal)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonOnAccent),
                onClickLabel = contentDesc,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = visualState,
            transitionSpec = {
                (fadeIn(tween(140)) + scaleIn(initialScale = 0.78f, animationSpec = spring(stiffness = 500f))) togetherWith
                    (fadeOut(tween(100)) + scaleOut(targetScale = 0.78f))
            },
            label = "play_pause_morph"
        ) { stateKey ->
            when (stateKey) {
                2 -> CircularProgressIndicator(
                    color = PhotonOnAccent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(iconSize * 0.78f)
                )
                1 -> Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = contentDesc,
                    tint = PhotonOnAccent,
                    modifier = Modifier.size(iconSize)
                )
                else -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = contentDesc,
                    tint = PhotonOnAccent,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Optimized Coil thumbnail loader with aggressive memory/disk caching and smooth crossfade.
 */
@Composable
fun PhotonNetworkThumbnail(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(180)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(PhotonCardShape)
            .background(PhotonSurfaceHigh)
    )
}

/**
 * Flat AMOLED segmented mode switcher ([YouTube | Music]) with animated tonal transitions.
 */
@Composable
fun PhotonModeSwitcher(
    currentMode: AppMode,
    onSelectMode: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .padding(4.dp)
            .testTag("mode_switcher"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor by animateColorAsState(
                targetValue = if (selected) PhotonSurfaceHighest else Color.Transparent,
                animationSpec = tween(180),
                label = "mode_pill_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) PhotonPrimaryText else PhotonSecondaryText,
                animationSpec = tween(180),
                label = "mode_pill_fg"
            )
            val icon = when (mode) {
                AppMode.YOUTUBE -> if (selected) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay
                AppMode.MUSIC -> if (selected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
            }
            Row(
                modifier = Modifier
                    .photonPressable(interactionSource, pressedScale = 0.95f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = PhotonPrimaryText),
                        onClick = { onSelectMode(mode) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("mode_${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = mode.label,
                    style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PhotonSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = PhotonTitleStyle,
            color = PhotonPrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .clip(PhotonCardShape)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText
                )
            }
        }
    }
}

@Composable
fun <T> PhotonFilterChipRow(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "filter_chip"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item == selectedItem
            val label = labelProvider(item)
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor by animateColorAsState(
                targetValue = if (selected) PhotonSurfaceHighest else PhotonSurfaceCard,
                animationSpec = tween(160),
                label = "chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) PhotonPrimaryText else PhotonSecondaryText,
                animationSpec = tween(160),
                label = "chip_text"
            )
            Box(
                modifier = Modifier
                    .photonPressable(interactionSource, pressedScale = 0.95f)
                    .clip(PhotonCardShape)
                    .background(bgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = PhotonPrimaryText),
                        onClick = { onSelect(item) }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("${tagPrefix}_${label.lowercase().replace(" ", "_")}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = if (selected) PhotonBodyMediumStyle else PhotonBodyStyle,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PhotonYouTubeVideoCard(
    item: YouTubeMediaItem,
    isCurrentItem: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardColor by animateColorAsState(
        targetValue = if (isCurrentItem) PhotonSurfaceHigh else PhotonSurfaceCard,
        animationSpec = tween(200),
        label = "yt_card_bg"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .photonPressable(interactionSource, pressedScale = 0.985f)
            .clip(PhotonCardShape)
            .background(cardColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(16.dp)
            .testTag("yt_video_card_${item.videoId}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            PhotonNetworkThumbnail(
                imageUrl = item.highResThumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier.matchParentSize()
            )

            if (item.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PhotonCanvas.copy(alpha = 0.90f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.durationText,
                        style = PhotonCaptionStyle,
                        color = PhotonPrimaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (item.channelAvatarUrl.isNotBlank()) {
                val context = LocalContext.current
                val avatarReq = remember(item.channelAvatarUrl) {
                    ImageRequest.Builder(context)
                        .data(item.channelAvatarUrl)
                        .crossfade(160)
                        .build()
                }
                AsyncImage(
                    model = avatarReq,
                    contentDescription = item.channelName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PhotonSurfaceHighest)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PhotonSurfaceHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.channelName.firstOrNull()?.uppercase() ?: "Y",
                        style = PhotonBodyMediumStyle,
                        color = PhotonPrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = PhotonBodyMediumStyle,
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

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_more_${item.videoId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = PhotonSecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PhotonMediaRow(
    item: YouTubeMediaItem,
    isCurrentItem: Boolean,
    isPlaying: Boolean,
    useWidescreenThumb: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "media_row_${item.videoId}"
) {
    val rowBg by animateColorAsState(
        targetValue = if (isCurrentItem) PhotonSurfaceCard else Color.Transparent,
        animationSpec = tween(180),
        label = "row_bg"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbModifier = if (useWidescreenThumb) {
            Modifier.width(96.dp).height(54.dp)
        } else {
            Modifier.size(48.dp)
        }
        Box(modifier = thumbModifier) {
            PhotonNetworkThumbnail(
                imageUrl = item.highResThumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier.matchParentSize()
            )
            if (isCurrentItem) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(PhotonCardShape)
                        .background(PhotonCanvas.copy(alpha = 0.60f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = PhotonPrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText,
                maxLines = if (useWidescreenThumb) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (useWidescreenThumb) item.metadataLine else item.channelName,
                style = if (useWidescreenThumb) PhotonCaptionStyle else PhotonBodyStyle,
                color = PhotonSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!useWidescreenThumb && item.durationText.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.durationText,
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("row_more_${item.videoId}")
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = PhotonSecondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PhotonMusicCard(
    item: YouTubeMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .photonPressable(interactionSource, pressedScale = 0.96f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(16.dp)
            .testTag("music_card_${item.videoId}")
    ) {
        PhotonNetworkThumbnail(
            imageUrl = item.highResThumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = item.title,
            style = PhotonBodyMediumStyle,
            color = PhotonPrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.channelName,
            style = PhotonCaptionStyle,
            color = PhotonSecondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PhotonCompactQuickTile(
    item: YouTubeMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(60.dp)
            .photonPressable(interactionSource, pressedScale = 0.96f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .testTag("quick_tile_${item.videoId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotonNetworkThumbnail(
            imageUrl = item.highResThumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.size(60.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
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
}

/**
 * Flat AMOLED pulsing skeleton card (strictly solid color pulse, ZERO gradients).
 */
@Composable
fun PhotonVideoCardSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val blockColor = PhotonSurfaceHigh.copy(alpha = pulseAlpha)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(PhotonCardShape)
                .background(blockColor)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(blockColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor)
                )
            }
        }
    }
}

@Composable
fun PhotonCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "photon_checkbox"
) {
    val boxColor by animateColorAsState(
        targetValue = if (checked) PhotonPrimaryText else PhotonSurfaceHighest,
        animationSpec = tween(150),
        label = "checkbox_bg"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = PhotonPrimaryText),
                onClick = onToggle
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(boxColor),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = PhotonCanvas,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PhotonSurfaceCard)
                )
            }
        }
    }
}

@Composable
fun PhotonRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "photon_radio"
) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) PhotonPrimaryText else PhotonSecondaryText,
        animationSpec = tween(150),
        label = "radio_ring"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "radio_dot"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = PhotonPrimaryText),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(ringColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PhotonSurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                if (dotScale > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(CircleShape)
                            .background(PhotonPrimaryText)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotonMiniPlayer(
    item: YouTubeMediaItem,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: PlaybackProgress,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 360),
        label = "mini_progress"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .photonPressable(interactionSource, pressedScale = 0.985f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onExpand
            )
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            Spacer(modifier = Modifier.width(12.dp))
            PhotonPrimaryPlayButton(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onClick = onTogglePlay,
                size = 40.dp,
                iconSize = 22.dp,
                testTag = "mini_player_play_button"
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PhotonSurfaceHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(PhotonPrimaryText)
            )
        }
    }
}

enum class PhotonDestination(
    val route: String,
    val labelRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("library", R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
}

@Composable
fun PhotonBottomNavBar(
    currentRoute: String?,
    onNavigate: (PhotonDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PhotonSurfaceCard)
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotonDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val tint by animateColorAsState(
                targetValue = if (selected) PhotonAccentTeal else PhotonSecondaryText,
                animationSpec = tween(180),
                label = "nav_tint"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 450f),
                label = "nav_scale"
            )
            val label = stringResource(destination.labelRes)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = PhotonPrimaryText),
                        onClick = { onNavigate(destination) }
                    )
                    .testTag("nav_${destination.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (selected) destination.activeIcon else destination.inactiveIcon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = PhotonCaptionStyle,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PhotonNavRail(
    currentRoute: String?,
    onNavigate: (PhotonDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(PhotonSurfaceCard)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PhotonDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val tint by animateColorAsState(
                targetValue = if (selected) PhotonAccentTeal else PhotonSecondaryText,
                animationSpec = tween(180),
                label = "rail_tint"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 450f),
                label = "rail_scale"
            )
            val label = stringResource(destination.labelRes)
            Column(
                modifier = Modifier
                    .size(64.dp)
                    .clip(PhotonCardShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = PhotonPrimaryText),
                        onClick = { onNavigate(destination) }
                    )
                    .testTag("rail_${destination.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (selected) destination.activeIcon else destination.inactiveIcon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = PhotonCaptionStyle,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}
