package com.prism.music.photon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeRecommendationRow(row: HomeFeedRows.Row, open: (Video) -> Unit) {
    HomeRecommendationColumns(row) { source, column ->
            val video = resolvedVideo(source)
            Column(column.clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .85f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f), RoundedCornerShape(24.dp))
                .remoteFocus().clickable { open(video) }.padding(10.dp)) {
                // Both thumbnail AND text use the same column, never a narrow image in a full-width card.
                VideoThumbnail(video, video.title, Modifier.fillMaxWidth()
                    .aspectRatio(VideoArtworkShape.ratio(row.portrait)).clip(MaterialTheme.shapes.medium))
                Text(video.title, Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(video.channel, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                VideoHeart(video)
            }
    }
}

/** Shared measured geometry; the production cards and rendered alignment test use this same container. */
@Composable
internal fun HomeRecommendationColumns(row: HomeFeedRows.Row, card: @Composable (Video, Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.videos.forEach { source -> card(source, Modifier.weight(1f)) }
        if (row.portrait && row.videos.size == 1) Spacer(Modifier.weight(1f))
    }
}


/** Observe only already-resolved metadata; never eagerly extract every offscreen recommendation. */
@Composable
internal fun rememberHomeFeedRows(videos: List<Video>, model: PhotonViewModel, list: LazyListState): List<HomeFeedRows.Row> {
    val metadata by model.videoPresentation.collectAsStateWithLifecycle()
    val proposed = HomeFeedRows.make(videos.map { ShortsPresentation.merge(it, metadata[it.id]) })
    var committed by remember { mutableStateOf(proposed) }
    // New dataset/empty account must clear immediately; late portrait corrections wait for scroll idle.
    val sameDataset = committed.flatMap { it.videos }.map { it.id }.all { id -> videos.any { it.id == id } }
    if (videos.isEmpty() || !list.isScrollInProgress || !sameDataset) {
        SideEffect { if (committed != proposed) committed = proposed }
        return proposed
    }
    return committed
}
