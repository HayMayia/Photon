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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppMode
import com.example.data.ExploreTopic
import com.example.data.FeedLoadState
import com.example.data.MusicExploreTopics
import com.example.data.SearchFilter
import com.example.data.YouTubeExploreTopics
import com.example.data.YouTubeMediaItem
import com.example.ui.PhotonUiState
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.PhotonFilterChipRow
import com.example.ui.components.PhotonMediaRow
import com.example.ui.components.PhotonModeSwitcher
import com.example.ui.components.PhotonSectionHeader
import com.example.ui.components.PhotonVideoCardSkeleton
import com.example.ui.components.PhotonYouTubeVideoCard
import com.example.ui.components.photonPressable
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun SearchScreen(
    state: PhotonUiState,
    isExpandedWidth: Boolean,
    bottomContentPadding: Dp,
    onSelectMode: (AppMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onPlayItem: (YouTubeMediaItem, List<YouTubeMediaItem>) -> Unit,
    onOpenItemOptions: (YouTubeMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasSubmittedSearch = state.committedSearchQuery.isNotBlank()
    val showSuggestions = state.searchQuery.isNotBlank() &&
        state.searchQuery.trim() != state.committedSearchQuery &&
        state.searchSuggestions.isNotEmpty()

    val exploreTopics = if (state.appMode == AppMode.YOUTUBE) YouTubeExploreTopics else MusicExploreTopics

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .testTag("search_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomContentPadding)
    ) {
        // Header + Mode Switcher
        item(key = "search_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_title),
                    style = PhotonTitleStyle,
                    color = PhotonPrimaryText
                )
                PhotonModeSwitcher(
                    currentMode = state.appMode,
                    onSelectMode = onSelectMode
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Flat borderless Search Input (#1E1E1E, 12dp radius)
        item(key = "search_input") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .clip(PhotonCardShape)
                    .background(PhotonSurfaceCard)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = PhotonSecondaryText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (state.appMode == AppMode.YOUTUBE) {
                                    R.string.search_placeholder_youtube
                                } else {
                                    R.string.search_placeholder_music
                                }
                            ),
                            style = PhotonBodyStyle,
                            color = PhotonSecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = PhotonBodyStyle.copy(color = PhotonPrimaryText),
                        cursorBrush = SolidColor(PhotonPrimaryText),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSubmitQuery(state.searchQuery)
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input_field")
                    )
                }
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_clear_search),
                            tint = PhotonSecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Live YouTube Autocomplete Suggestions
        if (showSuggestions) {
            items(state.searchSuggestions, key = { "sug_$it" }) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            keyboardController?.hide()
                            onSubmitQuery(suggestion)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("search_suggestion_$suggestion"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = PhotonSecondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        style = PhotonBodyStyle,
                        color = PhotonPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.NorthWest,
                        contentDescription = null,
                        tint = PhotonSecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else if (!hasSubmittedSearch) {
            // Recent Searches
            if (state.recentSearches.isNotEmpty()) {
                item(key = "recent_header") {
                    PhotonSectionHeader(
                        title = stringResource(R.string.search_recent),
                        actionLabel = stringResource(R.string.search_clear_all),
                        onActionClick = onClearRecent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(state.recentSearches, key = { "recent_$it" }) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboardController?.hide()
                                onSubmitQuery(term)
                            }
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = PhotonSecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = term,
                            style = PhotonBodyStyle,
                            color = PhotonPrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveRecent(term) },
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

                item(key = "recent_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Explore Topics Grid
            item(key = "explore_header") {
                PhotonSectionHeader(title = stringResource(R.string.search_explore))
                Spacer(modifier = Modifier.height(16.dp))
            }

            item(key = "explore_grid") {
                val cols = if (isExpandedWidth) 3 else 2
                val rows = exploreTopics.chunked(cols)
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
                            rowItems.forEach { topic ->
                                ExploreTopicTile(
                                    topic = topic,
                                    onClick = {
                                        keyboardController?.hide()
                                        onSubmitQuery(topic.query)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            // Search Filter Chips
            item(key = "search_filters") {
                PhotonFilterChipRow(
                    items = SearchFilter.entries,
                    selectedItem = state.searchFilter,
                    labelProvider = { it.label },
                    onSelect = onSelectFilter,
                    tagPrefix = "search_filter"
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            when (val searchState = state.searchState) {
                is FeedLoadState.Loading -> {
                    items(4) {
                        PhotonVideoCardSkeleton(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                is FeedLoadState.Error -> {
                    item(key = "search_err") {
                        FeedErrorCard(
                            message = searchState.message,
                            onRetry = { onSubmitQuery(state.committedSearchQuery) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }

                is FeedLoadState.Success -> {
                    val results = searchState.items
                    if (results.isEmpty()) {
                        item(key = "search_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = PhotonSecondaryText,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.search_empty_title),
                                    style = PhotonBodyMediumStyle,
                                    color = PhotonPrimaryText
                                )
                                Text(
                                    text = stringResource(R.string.search_empty_subtitle),
                                    style = PhotonCaptionStyle,
                                    color = PhotonSecondaryText
                                )
                            }
                        }
                    } else if (state.appMode == AppMode.YOUTUBE) {
                        items(
                            items = results,
                            key = { "sr_vid_${it.videoId}" },
                            contentType = { "search_video_card" }
                        ) { item ->
                            PhotonYouTubeVideoCard(
                                item = item,
                                isCurrentItem = state.currentItem?.videoId == item.videoId,
                                onClick = { onPlayItem(item, results) },
                                onMoreClick = { onOpenItemOptions(item) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(
                            items = results,
                            key = { "sr_mus_${it.videoId}" },
                            contentType = { "search_music_row" }
                        ) { item ->
                            PhotonMediaRow(
                                item = item,
                                isCurrentItem = state.currentItem?.videoId == item.videoId,
                                isPlaying = state.isPlaying,
                                useWidescreenThumb = false,
                                onClick = { onPlayItem(item, results) },
                                onMoreClick = { onOpenItemOptions(item) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreTopicTile(
    topic: ExploreTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .photonPressable(interactionSource = interactionSource, pressedScale = 0.96f)
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = PhotonPrimaryText),
                onClick = onClick
            )
            .padding(16.dp)
            .testTag("explore_topic_${topic.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Explore,
            contentDescription = null,
            tint = PhotonSecondaryText,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = topic.title,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = topic.subtitle,
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
