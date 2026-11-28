package com.prism.music.photon

import android.app.Application
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Connect through the real MediaSession binder, not only the Kotlin adapter.
 * Framework AVRCP assertions live in androidTest: Robolectric ShadowMediaController
 * returns only manually injected state and does not bridge to a framework session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlatformMediaControlsTest {
    private class ReadyPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun handleRelease(): com.google.common.util.concurrent.ListenableFuture<*> =
            com.google.common.util.concurrent.Futures.immediateVoidFuture()
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("one")
                .setMediaItem(MediaItem.Builder().setMediaId("one").setUri("file:///test.mp3").build())
                .setDurationUs(60_000_000).setIsSeekable(true).build()))
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
    }
    @Test fun frameworkSpeakerSkipUsesTheServiceQueueWithoutAnActivity() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val a = Video("aaaaaaaaaaa", "A", "Artist", music = true)
        val b = Video("bbbbbbbbbbb", "B", "Artist", music = true)
        var queue = PlaybackQueueState(listOf(a,b), a.id, "liked-music")
        val raw = ReadyPlayer()
        val player = QueueSessionPlayer(raw, { queue }) { forward ->
            val video = if (forward) queue.next() else queue.previous()
            if (video != null) queue = queue.move(video, emptyList())
        }
        val session = MediaSession.Builder(context, player).build()
        try {
            val connection = MediaController.Builder(context, session.token).buildAsync()
            repeat(20) { shadowOf(Looper.getMainLooper()).idle() }
            val controller = connection.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            controller.seekToNext()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertEquals(b.id, queue.currentId)
            player.refreshCommands()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
            controller.seekToPrevious()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertEquals(a.id, queue.currentId)
            controller.release()
        } finally { session.release(); raw.release() }
    }
}
