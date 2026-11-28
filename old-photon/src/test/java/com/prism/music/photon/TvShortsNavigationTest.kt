package com.prism.music.photon

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class TvShortsNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun backExitsAndActionNavigationNeverSelectsAnotherVideo() {
        var exits = 0; var steps = 0
        compose.setContent { MaterialTheme {
            TvShortsFrame(Video("aaaaaaaaaaa", "Selected Short", "Creator", short = true), false, false, null,
                {}, {}, {}, { exits++ }, { steps += it }, {}) { Box(Modifier.fillMaxSize()) }
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
        } }
        compose.onNodeWithContentDescription("Like").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(0, steps)
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, exits)
        compose.onNodeWithTag("tv-short-video").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(1, steps)
        compose.onNodeWithTag("tv-short-video").performKeyInput { pressKey(Key.Back) }
        assertEquals(2, exits)
        compose.onNodeWithText("Selected Short").assertIsDisplayed()
    }
}
