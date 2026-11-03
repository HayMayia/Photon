package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeShortsAlignmentTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pairedPortraitFramesAndTitlesHaveTheSameBounds() {
        val videos = listOf(Video("abcdefghijk", "Short", "Creator", short = true),
            Video("otherVideo1", "Long title that wraps under its own portrait frame", "Creator", short = true))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    HomeRecommendationColumns(HomeFeedRows.make(videos).single()) { video, column ->
                        Column(column) {
                            Box(Modifier.fillMaxWidth().aspectRatio(VideoArtworkShape.ratio(true)).testTag("art-${video.id}"))
                            Text(video.title, Modifier.fillMaxWidth().testTag("title-${video.id}"), minLines = 2, maxLines = 2)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val left = bounds("art-abcdefghijk"); val right = bounds("art-otherVideo1")
        assertEquals(left.top, right.top, 0.1f)
        assertEquals(left.width, right.width, 0.1f)
        assertEquals(left.height, right.height, 0.1f)
        assertTrue(left.right < right.left)
        val a = bounds("title-abcdefghijk"); val b = bounds("title-otherVideo1")
        assertEquals(left.left, a.left, 0.1f); assertEquals(right.left, b.left, 0.1f)
        assertEquals(a.top, b.top, 0.1f); assertEquals(a.height, b.height, 0.1f)
    }
}
