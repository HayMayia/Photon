package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class PlaylistPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun severalActualSongRowsAreVisibleAndClickOpensTheChosenEntry() {
        var selected: String? = null
        val videos = (0..5).map { Video("video${it.toString().padStart(6, '0')}", "Song $it", "Creator") }
        compose.setContent { MaterialTheme {
            LazyColumn(Modifier.width(360.dp).height(600.dp)) {
                items(videos, key = { it.id }) { video -> PlaylistEntryRow(video, false, { selected = video.id }, {}) }
            }
        } }
        videos.forEach { compose.onNodeWithTag("playlist-entry-${it.id}").assertIsDisplayed() }
        compose.onNodeWithTag("playlist-entry-video000004").performClick()
        assertEquals("video000004", selected)
    }
    @Test fun deletionRequiresExplicitConfirmationAndCancelDoesNotDelete() {
        var deleted = 0
        var shown by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            if (shown) PlaylistDeletionDialog("My playlist", { deleted++; shown = false }, { shown = false })
        } }
        compose.onNodeWithText("Delete playlist?").assertIsDisplayed()
        assertEquals(0, deleted)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, deleted)
        compose.runOnIdle { shown = true }
        compose.onNodeWithText("Delete playlist").performClick()
        assertEquals(1, deleted)
    }
}
