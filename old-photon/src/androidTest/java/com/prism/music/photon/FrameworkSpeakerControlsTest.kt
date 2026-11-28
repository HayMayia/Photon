package com.prism.music.photon

import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Requires an Android emulator/device. Unlike Robolectric, this exercises the OS legacy session. */
@RunWith(AndroidJUnit4::class)
class FrameworkSpeakerControlsTest {
    private class ReadyPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("one")
                .setMediaItem(MediaItem.Builder().setMediaId("one").setUri("file:///unused.mp3").build())
                .setDurationUs(60_000_000).setIsSeekable(true).build()))
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    @Test fun platformSkipActionsNavigateTheServiceQueue() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val a = Video("aaaaaaaaaaa", "A", "Artist", music = true)
        val b = Video("bbbbbbbbbbb", "B", "Artist", music = true)
        val queue = AtomicReference(PlaybackQueueState(listOf(a,b), a.id, "liked-music"))
        val nextAvailable = CountDownLatch(1); val previousAvailable = CountDownLatch(1)
        val reachedB = CountDownLatch(1); val reachedA = CountDownLatch(1)
        lateinit var raw: ReadyPlayer
        lateinit var player: QueueSessionPlayer
        lateinit var session: MediaSession
        lateinit var controller: MediaController
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if ((state?.actions ?: 0) and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) nextAvailable.countDown()
                if ((state?.actions ?: 0) and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) previousAvailable.countDown()
            }
        }
        main {
            raw = ReadyPlayer()
            player = QueueSessionPlayer(raw, { queue.get() }) { forward ->
                val state = queue.get()
                (if (forward) state.next() else state.previous())?.let {
                    queue.set(state.move(it, emptyList())); player.refreshCommands()
                    if (it.id == b.id) reachedB.countDown() else reachedA.countDown()
                }
            }
            session = MediaSession.Builder(context, player).build()
            controller = MediaController(context, session.platformToken)
            controller.registerCallback(callback, Handler(Looper.getMainLooper()))
            callback.onPlaybackStateChanged(controller.playbackState)
        }
        try {
            assertTrue("OS must advertise Next", nextAvailable.await(5, TimeUnit.SECONDS))
            controller.transportControls.skipToNext()
            assertTrue("OS Next must reach queue B", reachedB.await(5, TimeUnit.SECONDS))
            assertEquals(b.id, queue.get().currentId)
            assertTrue("OS must advertise Previous", previousAvailable.await(5, TimeUnit.SECONDS))
            controller.transportControls.skipToPrevious()
            assertTrue("OS Previous must reach queue A", reachedA.await(5, TimeUnit.SECONDS))
            assertEquals(a.id, queue.get().currentId)
        } finally { main { controller.unregisterCallback(callback); session.release(); raw.release() } }
    }
}
