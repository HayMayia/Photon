package com.prism.music.ui.theme

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFallbackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun performanceFallbackKeepsContentAndClickSemantics() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppSettings provides AppSettings(liquidGlass = true, performanceMode = true).effective()) {
                    GlassSurface {
                        Text("Open library", Modifier.clickable { clicks++ }.padding(20.dp))
                    }
                }
            }
        }
        compose.onNodeWithText("Open library").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }
}
