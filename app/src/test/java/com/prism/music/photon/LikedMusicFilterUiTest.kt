package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LikedMusicFilterUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allStartsSelectedAndFiltersSitToRightOfRefresh() {
        var selected by mutableStateOf(LikedMusicFilter.ALL)
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) {
                AccountLibraryHeader(AccountLibraryState(shelf = AccountShelf.LIKED_MUSIC, title = "Liked Music"),
                    ready = true, musicFilter = selected, onMusicFilter = { selected = it })
            }
        } }
        compose.onNodeWithTag("music-filter-ALL").assertIsSelected()
        val refresh = compose.onNodeWithTag("library-refresh").fetchSemanticsNode().boundsInRoot
        val filters = compose.onNodeWithTag("liked-music-filters").fetchSemanticsNode().boundsInRoot
        assertTrue(refresh.right <= filters.left)
        compose.onNodeWithTag("music-filter-SHORTS").performClick().assertIsSelected()
        compose.onNodeWithTag("music-filter-ALL").assertIsNotSelected()
        compose.onNodeWithTag("music-filter-FULL_LENGTH").performScrollTo().performClick().assertIsSelected()
    }
}
