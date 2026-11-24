package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppMode
import com.example.data.YouTubeMediaItem
import com.example.ui.PhotonUiState
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonMediaRow
import com.example.ui.components.PhotonNetworkThumbnail
import com.example.ui.components.PhotonPrimaryPlayButton
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun CollectionDetailScreen(
    title: String,
    items: List<YouTubeMediaItem>,
    state: PhotonUiState,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayItem: (YouTubeMediaItem, List<YouTubeMediaItem>) -> Unit,
    onOpenItemOptions: (YouTubeMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = items.firstOrNull()?.highResThumbnailUrl
    val isPlayingFromHere = state.isPlaying &&
        state.currentItem != null &&
        items.any { it.videoId == state.currentItem?.videoId }

    val useWidescreen = state.appMode == AppMode.YOUTUBE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .testTag("collection_detail_screen"),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        item(key = "detail_top") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("collection_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = PhotonPrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = PhotonBodyMediumStyle,
                    color = PhotonSecondaryText
                )
            }
        }

        item(key = "detail_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    PhotonNetworkThumbnail(
                        imageUrl = coverUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(PhotonCardShape)
                            .background(PhotonSurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QueueMusic,
                            contentDescription = null,
                            tint = PhotonSecondaryText,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    style = PhotonTitleStyle,
                    color = PhotonPrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${items.size} ${if (items.size == 1) "item" else "items"}",
                    style = PhotonCaptionStyle,
                    color = PhotonSecondaryText
                )

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhotonPrimaryPlayButton(
                            isPlaying = isPlayingFromHere,
                            onClick = onPlayAll,
                            size = 48.dp,
                            iconSize = 24.dp,
                            testTag = "collection_play_button"
                        )
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(PhotonCardShape)
                                .background(PhotonSurfaceCard)
                                .clickable(onClick = onShuffleAll)
                                .padding(horizontal = 16.dp)
                                .testTag("collection_shuffle_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = PhotonPrimaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_shuffle_play),
                                style = PhotonBodyMediumStyle,
                                color = PhotonPrimaryText
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (items.isEmpty()) {
            item(key = "detail_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_subtitle),
                        style = PhotonBodyStyle,
                        color = PhotonSecondaryText
                    )
                }
            }
        } else {
            items(items, key = { "col_item_${it.videoId}" }) { item ->
                PhotonMediaRow(
                    item = item,
                    isCurrentItem = state.currentItem?.videoId == item.videoId,
                    isPlaying = state.isPlaying,
                    useWidescreenThumb = useWidescreen,
                    onClick = { onPlayItem(item, items) },
                    onMoreClick = { onOpenItemOptions(item) }
                )
            }
        }
    }
}
