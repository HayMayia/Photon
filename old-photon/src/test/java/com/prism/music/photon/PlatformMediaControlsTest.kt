package com.prism.music.photon

import android.app.Application
import android.os.Looper
import android.media.session.MediaController
import android.media.session.PlaybackState
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

/** Exercise the actual framework transport used by AVRCP, not only the Kotlin adapter. */
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
            val controller = MediaController(context, session.platformToken)
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertTrue(requireNotNull(controller.playbackState).actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            controller.transportControls.skipToNext()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertEquals(b.id, queue.currentId)
            player.refreshCommands()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertTrue(requireNotNull(controller.playbackState).actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
            controller.transportControls.skipToPrevious()
            repeat(10) { shadowOf(Looper.getMainLooper()).idle() }
            assertEquals(a.id, queue.currentId)
        } finally { session.release(); raw.release() }
    }
}
