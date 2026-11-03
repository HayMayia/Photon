package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionsLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun subscriptionFeedPairsShortsAndKeepsNormalVideoFullWidthAfterLateEvidence() {
        val videos = listOf(Video("normal", "A full video", "Creator"), Video("short-a", "First Short", "Creator"), Video("short-b", "Second Short", "Creator"))
        val rows = mutableStateOf(SubscriptionFeedRows.update(emptyList(), videos))
        compose.setContent {
            MaterialTheme {
                SubscriptionFeedGrid(rows.value, emptyMap(), {}, {}, {},
                    commitFormats = { rows.value = AccountLibraryRows.commitFormats(rows.value) },
                    modifier = Modifier.width(360.dp).height(950.dp), artwork = { _, frame, _ -> Box(frame) })
            }
        }
        compose.waitForIdle()
        fun bounds(id: String) = compose.onNodeWithTag("subscription-thumbnail:$id").fetchSemanticsNode().boundsInRoot
        val full = bounds("normal").width
        compose.runOnIdle { rows.value = SubscriptionFeedRows.update(rows.value, videos.map { it.copy(short = it.id != "normal") }) }
        compose.waitForIdle()
        assertEquals(full, bounds("normal").width, 0.1f)
        assertTrue(bounds("short-a").width < full / 2f)
        assertEquals(bounds("short-a").width, bounds("short-b").width, 0.1f)
        assertEquals(bounds("short-a").top, bounds("short-b").top, 0.1f)
        assertTrue(bounds("short-a").right < bounds("short-b").left)
        compose.runOnIdle {
            rows.value = SubscriptionFeedRows.update(rows.value, videos + videos.last())
            assertEquals(3, rows.value.size)
            assertEquals(listOf(2, 1, 1), rows.value.map(AccountGridPolicy::span))
        }
        compose.waitForIdle()
        assertEquals(bounds("short-a").top, bounds("short-b").top, 0.1f)
    }
}
