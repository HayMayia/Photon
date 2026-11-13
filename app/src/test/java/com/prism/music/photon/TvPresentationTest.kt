package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun remoteMovesBetweenProductionCardsAndOpensTheFocusedOne() {
        val request = FocusRequester()
        var opened = ""
        compose.setContent { MaterialTheme {
            Row(Modifier.width(700.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TvPosterCard(Video("abcdefghijk", "First video", "Creator"), { opened = "first" }, Modifier.weight(1f).focusRequester(request))
                TvPosterCard(Video("otherVideo1", "Second video with a longer title", "Creator"), { opened = "second" }, Modifier.weight(1f))
            }
            LaunchedEffect(Unit) { request.requestFocus() }
        } }
        // Request focus after the host window is attached; Robolectric's first window-focus
        // event can otherwise replace an early LaunchedEffect request.
        compose.onNodeWithTag("tv-video-abcdefghijk").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.onNodeWithTag("tv-video-abcdefghijk").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tv-video-otherVideo1").assertIsFocused()
        compose.onNodeWithTag("tv-video-otherVideo1").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("second", opened)
    }
    @Test fun tvMusicSwitchStartsOffAndCanBeEnabledExplicitly() {
        var enabled by mutableStateOf(false)
        compose.setContent { MaterialTheme { TvMusicBackgroundSetting(enabled) { enabled = it } } }
        compose.onNodeWithTag("tv-music-background").assertIsOff().performClick().assertIsOn()
        assertTrue(enabled)
    }
}
