package com.prism.music.photon

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real decoder + framework session + production TV view. Not a Robolectric shadow. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class TvRenderSmokeTest {
    @Test fun serviceControllerRendersRealFramesAndRemoteBackEscapesNativeHierarchy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PhotonApplication
        val clip = File(app.cacheDir, "render-smoke.mp4")
        instrumentation.context.assets.open("render-smoke.mp4").use { input -> clip.outputStream().use { input.copyTo(it) } }
        app.playbackForeground.value = true; app.tvForeground.value = true
        app.library.update { it.copy(youtubeHome = false, accountHistorySync = false, videoBackground = true) }
        val intent = Intent(instrumentation.targetContext, TvRenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val scenario = ActivityScenario.launch<TvRenderTestActivity>(intent)
        var controller: MediaController? = null
        val frame = CountDownLatch(1)
        val failed = AtomicReference<String?>(null)
        try {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PhotonPlaybackService::class.java))).buildAsync()
            val player = future.get(20, TimeUnit.SECONDS)
            controller = player
            scenario.onActivity { activity ->
                activity.output.player = player
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { frame.countDown() }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed.set(error.errorCodeName); frame.countDown() }
                })
                player.setMediaItem(playbackItem(Video("aaaaaaaaaaa", "Local render probe", "Photon test"), localPath = clip.absolutePath))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare(); player.play()
            }
            assertTrue("No first-frame callback within 20 seconds", frame.await(20, TimeUnit.SECONDS))
            assertNull("Decoder failure", failed.get())
            val pixels = CountDownLatch(1)
            var colorful = false
            scenario.onActivity { activity ->
                val texture = activity.output.videoSurfaceView as TextureView
                val bitmap = texture.bitmap
                assertNotNull("Texture has no frame bitmap", bitmap)
                if (bitmap != null) {
                    val samples = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(samples, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    colorful = samples.any { android.graphics.Color.red(it) > 35 || android.graphics.Color.green(it) > 35 || android.graphics.Color.blue(it) > 35 }
                    bitmap.recycle()
                }
                pixels.countDown()
            }
            assertTrue(pixels.await(5, TimeUnit.SECONDS))
            assertTrue("Decoder callback fired but actual texture remains black", colorful)
            var exits = 0
            scenario.onActivity { activity ->
                activity.back.action = { exits++; player.pause() }
                activity.output.isFocusableInTouchMode = true; activity.output.requestFocus()
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                assertEquals("Back must exit once before native hierarchy", 1, exits)
            }
            val path = File(instrumentation.targetContext.getExternalFilesDir(null), "tv-render-smoke.png")
            scenario.onActivity { activity ->
                val bitmap = Bitmap.createBitmap(activity.output.width, activity.output.height, Bitmap.Config.ARGB_8888)
                val done = CountDownLatch(1)
                PixelCopy.request(activity.window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle(); done.countDown()
                }, Handler(Looper.getMainLooper()))
            }
        } finally {
            scenario.onActivity { it.output.player = null; controller?.release() }
            scenario.close(); clip.delete()
        }
    }
}
