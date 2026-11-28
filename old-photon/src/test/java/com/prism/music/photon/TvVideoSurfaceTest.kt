package com.prism.music.photon

import android.app.Application
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvVideoSurfaceTest {
    @Test fun tvVideoUsesAnActualTextureOutputWithoutNativeFocusInterception() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = LayoutInflater.from(app).inflate(com.prism.music.R.layout.photon_tv_video, null) as PhotonPlayerView
        assertTrue(view.videoSurfaceView is TextureView)
        assertFalse(view.useController)
        assertFalse(view.isFocusable)
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, view.descendantFocusability)
    }
}
