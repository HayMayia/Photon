package com.prism.music.photon

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortsOverlayTest {
    @get:Rule val compose = createComposeRule()
    @Test fun metadataHiddenUntilControlsVisibleAndActionsAreDirect() {
        val visible = mutableStateOf(false)
        var likes = 0
        var comments = 0
        var details = 0
        var saves = 0
        var subscriptions = 0
        compose.setContent {
            MaterialTheme {
                ShortsOverlay(visible.value, Video("short-id", "Short title", "Channel", short = true), true,
                    like = { likes++ }, comments = { comments++ }, information = { details++ },
                    fullscreen = {}, channel = {}, save = { saves++ }, share = {}, subscribe = { subscriptions++ }, subscribed = true)
            }
        }
        compose.onNodeWithText("Short title").assertDoesNotExist()
        compose.onNodeWithText("Comments").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Short title").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unlike").performClick()
        compose.onNodeWithText("Comments").performClick()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 1, 1, 1), listOf(likes, comments, details, saves, subscriptions))
            visible.value = false
        }
        compose.onNodeWithText("Short title").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unlike").assertDoesNotExist()
    }
}
