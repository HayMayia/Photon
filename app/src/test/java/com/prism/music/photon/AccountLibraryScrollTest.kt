package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
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
class AccountLibraryScrollTest {
    @get:Rule val compose = createComposeRule()
    private fun entry(index: Int) = AccountEntry("renderer-$index", "Video $index", AccountEntryKind.VIDEO,
        Video("video-$index", "Video $index", "Creator", short = index % 3 != 0))

    @Test fun metadataRemountsAndOverlappingPagesKeepVisibleSlotsStable() {
        val rows = mutableStateOf(AccountLibraryRows.append(emptyList(), (0 until 60).map(::entry)))
        lateinit var grid: LazyGridState
        compose.setContent {
            MaterialTheme {
                grid = rememberLazyGridState()
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.width(360.dp).height(700.dp), state = grid) {
                    items(rows.value, key = AccountLibraryRows::key, span = { GridItemSpan(AccountGridPolicy.span(it)) }) { row ->
                        Box(Modifier.fillMaxWidth().aspectRatio(AccountGridPolicy.ratio(row)).testTag(AccountLibraryRows.key(row))) { Text(row.title) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { grid.scrollToItem(26, 17) } }
        compose.waitForIdle()
        val anchor = compose.runOnIdle { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }
        fun positions() = grid.layoutInfo.visibleItemsInfo.associate { it.key to it.offset }
        val before = compose.runOnIdle { positions() }
        compose.runOnIdle {
            // Every unknown row now gets positive Shorts proof, as happens during lazy reads.
            var enriched = rows.value
            enriched.forEach { row -> enriched = AccountLibraryRows.metadata(enriched, row.video!!.copy(short = true)) }
            rows.value = enriched
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(anchor, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset)
            assertEquals(before, positions())
            runBlocking { grid.scrollToItem(46) }
        }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { grid.scrollToItem(anchor.first, anchor.second) } }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(before, positions())
            rows.value = AccountLibraryRows.append(rows.value, (50 until 70).map(::entry))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(70, rows.value.size)
            assertEquals(70, rows.value.map(AccountLibraryRows::key).distinct().size)
            assertEquals(anchor, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset)
            assertEquals(before, positions())
        }
        before.keys.forEach { key -> compose.onAllNodesWithTag(key as String).assertCountEquals(1) }
    }
}
