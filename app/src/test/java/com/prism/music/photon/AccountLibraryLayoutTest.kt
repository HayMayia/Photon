package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountLibraryLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun checkLayout(fontScale: Float) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.width(360.dp).height(800.dp)) {
                        item(span = { GridItemSpan(2) }) {
                            AccountLibraryHeader(AccountLibraryState(title = "History", busy = true, error = "Example account error",
                                page = AccountLibraryPage(emptyList(), message = "This list has no videos.")), ready = true)
                        }
                        item { Box(Modifier.fillMaxWidth().height(60.dp).testTag("short-left")) { Text("Short one") } }
                        item { Box(Modifier.fillMaxWidth().height(60.dp).testTag("short-right")) { Text("Short two") } }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val vertical = listOf("library-tabs", "library-title", "library-refresh", "library-error", "library-message")
        vertical.zipWithNext().forEach { (a, b) ->
            assertTrue("$a must not overlap $b at font scale $fontScale", bounds(a).bottom <= bounds(b).top)
        }
        assertTrue(bounds("library-header").bottom <= bounds("short-left").top)
        assertTrue(bounds("short-left").top == bounds("short-right").top)
        assertTrue(bounds("short-left").right <= bounds("short-right").left)
    }
    @Test fun libraryHeaderStacksAboveTwoShorts() = checkLayout(1f)
    @Test fun largerFontsDoNotOverlapHeaderControls() = checkLayout(1.8f)
}
