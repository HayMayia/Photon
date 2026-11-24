package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Tune
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
import com.example.data.FeedLoadState
import com.example.data.MusicCategory
import com.example.data.UserAccountProfile
import com.example.data.YouTubeCategory
import com.example.data.YouTubeMediaItem
import com.example.ui.PhotonUiState
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonCompactQuickTile
import com.example.ui.components.PhotonFilterChipRow
import com.example.ui.components.PhotonMediaRow
import com.example.ui.components.PhotonModeSwitcher
import com.example.ui.components.PhotonMusicCard
import com.example.ui.components.PhotonSectionHeader
import com.example.ui.components.PhotonVideoCardSkeleton
import com.example.ui.components.PhotonYouTubeVideoCard
import com.example.ui.components.photonPressable
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonSurfaceHigh
import com.example.ui.theme.PhotonSurfaceHighest
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun HomeScreen(
    state: PhotonUiState,
    isExpandedWidth: Boolean,
    bottomContentPadding: Dp,
    onSelectMode: (AppMode) -> Unit,
    onSelectYouTubeCategory: (YouTubeCategory) -> Unit,
    onSelectMusicCategory: (MusicCategory) -> Unit,
    onRefresh: () -> Unit,
    onPlayItem: (YouTubeMediaItem, List<YouTubeMediaItem>) -> Unit,
    onOpenItemOptions: (YouTubeMediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFeedState = if (state.appMode == AppMode.YOUTUBE) {
        state.youtubeFeedState
    } else {
        state.musicFeedState
    }
    val showPersonalizationBanner = if (state.appMode == AppMode.YOUTUBE) {
        state.selectedYouTubeCategory == YouTubeCategory.FOR_YOU ||
            state.selectedYouTubeCategory == YouTubeCategory.SUBSCRIPTIONS
    } else {
        state.selectedMusicCategory == MusicCategory.FOR_YOU ||
            state.selectedMusicCategory == MusicCategory.YOUR_MIX
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .testTag("home_screen")
    ) {
        // Fixed AMOLED Header + Mode Switcher + Category Pills
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = PhotonTitleStyle,
                color = PhotonPrimaryText
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = PhotonSecondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = if (state.sleepRemainingSec != null) {
                            Icons.Outlined.Bedtime
                        } else {
                            Icons.Outlined.Tune
                        },
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = if (state.sleepRemainingSec != null) PhotonPrimaryText else PhotonSecondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onOpenAccountSync,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_account_button")
                ) {
                    if (state.userProfile.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.userProfile.photoUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(160)
                                .build(),
                            contentDescription = stringResource(R.string.cd_account_sync),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(PhotonSurfaceHigh)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = stringResource(R.string.cd_account_sync),
                            tint = PhotonPrimaryText,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhotonModeSwitcher(
                currentMode = state.appMode,
                onSelectMode = onSelectMode
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.appMode == AppMode.YOUTUBE) {
            PhotonFilterChipRow(
                items = YouTubeCategory.entries,
                selectedItem = state.selectedYouTubeCategory,
                labelProvider = { it.label },
                onSelect = onSelectYouTubeCategory,
                tagPrefix = "yt_cat"
            )
        } else {
            PhotonFilterChipRow(
                items = MusicCategory.entries,
                selectedItem = state.selectedMusicCategory,
                labelProvider = { it.label },
                onSelect = onSelectMusicCategory,
                tagPrefix = "music_cat"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Smooth animated feed swap between YouTube Mode / Music Mode & Loading / Success states
        AnimatedContent(
            targetState = Triple(state.appMode, activeFeedState, isExpandedWidth),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 28 }) togetherWith
                    fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f),
            label = "home_feed_transition"
        ) { (mode, feedState, expanded) ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding)
            ) {
                if (showPersonalizationBanner) {
                    item(key = "feed_personalization_banner", contentType = "personalization_banner") {
                        FeedPersonalizationBanner(
                            profile = state.userProfile,
                            isSyncing = state.isSyncingYouTube,
                            onOpenAccountSync = onOpenAccountSync,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                when (feedState) {
                    is FeedLoadState.Loading -> {
                        items(4, contentType = { "skeleton" }) {
                            PhotonVideoCardSkeleton(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    is FeedLoadState.Error -> {
                        item(key = "feed_error", contentType = "error") {
                            FeedErrorCard(
                                message = feedState.message,
                                onRetry = onRefresh,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    }

                    is FeedLoadState.Success -> {
                        val items = feedState.items
                        if (mode == AppMode.YOUTUBE) {
                            if (expanded) {
                                val cols = 2
                                val rows = items.chunked(cols)
                                items(
                                    count = rows.size,
                                    key = { "yt_grid_row_$it" },
                                    contentType = { "yt_grid_row" }
                                ) { rowIdx ->
                                    val rowItems = rows[rowIdx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .animateItem(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        rowItems.forEach { video ->
                                            PhotonYouTubeVideoCard(
                                                item = video,
                                                isCurrentItem = state.currentItem?.videoId == video.videoId,
                                                onClick = { onPlayItem(video, items) },
                                                onMoreClick = { onOpenItemOptions(video) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(cols - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = items,
                                    key = { "yt_vid_${it.videoId}" },
                                    contentType = { "yt_video_card" }
                                ) { video ->
                                    PhotonYouTubeVideoCard(
                                        item = video,
                                        isCurrentItem = state.currentItem?.videoId == video.videoId,
                                        onClick = { onPlayItem(video, items) },
                                        onMoreClick = { onOpenItemOptions(video) },
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .animateItem()
                                    )
                                }
                            }
                        } else {
                            val quickPicks = items.take(4)
                            val featured = items.drop(4).take(8)
                            val trackList = items.drop(4)

                            if (quickPicks.isNotEmpty()) {
                                item(key = "music_quick_header", contentType = "header") {
                                    PhotonSectionHeader(title = stringResource(R.string.section_quick_picks))
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                item(key = "music_quick_grid", contentType = "quick_grid") {
                                    val cols = if (expanded) 4 else 2
                                    val rows = quickPicks.chunked(cols)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rows.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowItems.forEach { track ->
                                                    PhotonCompactQuickTile(
                                                        item = track,
                                                        onClick = { onPlayItem(track, items) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(cols - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(28.dp))
                                }
                            }

                            if (featured.isNotEmpty()) {
                                item(key = "music_featured_header", contentType = "header") {
                                    PhotonSectionHeader(title = stringResource(R.string.section_featured_music))
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                item(key = "music_featured_row", contentType = "featured_carousel") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(
                                            items = featured,
                                            key = { "music_card_${it.videoId}" },
                                            contentType = { "music_card" }
                                        ) { track ->
                                            PhotonMusicCard(
                                                item = track,
                                                onClick = { onPlayItem(track, items) },
                                                modifier = Modifier.width(172.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(28.dp))
                                }
                            }

                            if (trackList.isNotEmpty()) {
                                item(key = "music_tracks_header", contentType = "header") {
                                    PhotonSectionHeader(title = stringResource(R.string.section_for_you))
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                items(
                                    items = trackList,
                                    key = { "music_row_${it.videoId}" },
                                    contentType = { "music_row" }
                                ) { track ->
                                    PhotonMediaRow(
                                        item = track,
                                        isCurrentItem = state.currentItem?.videoId == track.videoId,
                                        isPlaying = state.isPlaying,
                                        useWidescreenThumb = false,
                                        onClick = { onPlayItem(track, items) },
                                        onMoreClick = { onOpenItemOptions(track) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = PhotonSecondaryText,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.error_network_title),
            style = PhotonBodyMediumStyle,
            color = PhotonPrimaryText
        )
        Text(
            text = message.ifBlank { stringResource(R.string.error_network_subtitle) },
            style = PhotonCaptionStyle,
            color = PhotonSecondaryText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(PhotonCardShape)
                .background(PhotonSurfaceHighest)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("feed_retry_button")
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText
            )
        }
    }
}

@Composable
private fun FeedPersonalizationBanner(
    profile: UserAccountProfile,
    isSyncing: Boolean,
    onOpenAccountSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstName = profile.displayName
        .trim()
        .substringBefore(" ")
        .ifBlank { "you" }
    val hasSignals = profile.hasPersonalizationSignals

    val titleText = if (hasSignals) {
        "Tuned for $firstName"
    } else {
        stringResource(R.string.sync_live_yt_title)
    }

    val subtitleText = when {
        isSyncing -> stringResource(R.string.sync_importing)
        profile.youtubeConnected -> buildString {
            append("Imported from YouTube & YT Music")
            if (profile.youtubeHandle.isNotBlank()) append(" · ${profile.youtubeHandle}")
        }
        hasSignals -> buildList {
            if (profile.subscribedChannels.isNotEmpty()) {
                val c = profile.subscribedChannels.size
                add("$c ${if (c == 1) "channel" else "channels"}")
            }
            if (profile.favoriteArtists.isNotEmpty()) {
                val a = profile.favoriteArtists.size
                add("$a ${if (a == 1) "artist" else "artists"}")
            }
        }.joinToString(" · ")
        else -> "Sync subscriptions, liked songs & custom mixes"
    }

    val bannerInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .photonPressable(bannerInteraction)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = bannerInteraction,
                indication = ripple(),
                onClick = onOpenAccountSync
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("home_personalization_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(PhotonCardShape)
                .background(PhotonSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasSignals) Icons.Filled.Sync else Icons.Outlined.CloudSync,
                contentDescription = null,
                tint = PhotonPrimaryText,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = titleText,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .clip(PhotonCardShape)
                .background(if (hasSignals) PhotonSurfaceHighest else PhotonPrimaryText)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(if (hasSignals) "home_banner_sync_button" else "home_banner_import_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasSignals) "Sync" else "Import",
                style = PhotonCaptionStyle,
                color = if (hasSignals) PhotonPrimaryText else PhotonCanvas
            )
        }
    }
}

