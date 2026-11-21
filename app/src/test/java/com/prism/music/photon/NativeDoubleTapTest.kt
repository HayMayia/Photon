package com.prism.music.photon

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class NativeDoubleTapTest {
    private fun doubleTap(view: PhotonPlayerView, x: Float) {
        val start = SystemClock.uptimeMillis()
        listOf(0L to MotionEvent.ACTION_DOWN, 20L to MotionEvent.ACTION_UP,
            100L to MotionEvent.ACTION_DOWN, 120L to MotionEvent.ACTION_UP).forEach { (offset, action) ->
            val event = MotionEvent.obtain(start, start + offset, action, x, 100f, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
    @Test fun nativeShortCentreDoubleTapLikesWithoutOpeningControls() {
        var likes = 0
        val view = PhotonPlayerView(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 400, 600); useController = true; shortsGestures = true
            doubleTapLike = { likes++ }; hideController()
        }
        doubleTap(view, 200f)
        assertEquals(1, likes)
        assertFalse(view.isControllerFullyVisible)
    }
    @Test fun nativeNormalCentreDoubleTapIsInert() {
        var likes = 0
        val view = PhotonPlayerView(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 400, 600); useController = true; shortsGestures = false
            doubleTapLike = { likes++ }; hideController()
        }
        doubleTap(view, 200f)
        assertEquals(0, likes)
        assertFalse(view.isControllerFullyVisible)
    }
}
