package com.prism.music.photon

import android.app.Application
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PanoramaControlsTest {
    @Test fun controlsInflateWithoutCreatingACompetingDecoderSurface() {
        val view = LayoutInflater.from(RuntimeEnvironment.getApplication()).inflate(com.prism.music.R.layout.photon_panorama_controls, null) as PhotonPlayerView
        assertNull(view.videoSurfaceView)
    }
    @Test fun panoramaBackgroundRoutesDragToViewerInsteadOfSeekOrShortLike() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val routed = mutableListOf<Int>()
        var likes = 0
        val view = PhotonPlayerView(context).apply {
            layout(0, 0, 400, 600); hideController(); shortsGestures = true; doubleTapLike = { likes++ }
            panoramaSurface = object : View(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean { routed += event.actionMasked; return true }
            }
        }
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(100, 100L + index * 20, action, 200f + index * 20, 100f, 0)
            assertTrue(view.dispatchTouchEvent(event)); event.recycle()
        }
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP), routed)
        assertEquals(0, likes)
    }
    @Test fun oldSavedVideoStillDecodesAndNewProjectionSurvivesRoundTrip() {
        assertEquals(VideoProjection.FLAT, Json.decodeFromString<Video>("""{"id":"abcdefghijk","title":"Old file","channel":"Creator"}""").projection)
        val saved = Video("abcdefghijk", "Source", "Creator", projection = VideoProjection.EQUIRECTANGULAR_TOP_BOTTOM)
        assertEquals(saved, Json.decodeFromString<Video>(Json.encodeToString(Video.serializer(), saved)))
    }
}
