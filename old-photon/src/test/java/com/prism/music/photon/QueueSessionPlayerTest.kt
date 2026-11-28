package com.prism.music.photon

import android.app.Application
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QueueSessionPlayerTest {
    private fun raw(): Player = androidx.media3.exoplayer.ExoPlayer.Builder(
        androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()).build()
    @Test fun serviceCommandsAdvanceAndGoToPreviousVideoWithoutAnActivity() {
        val a = Video("aaaaaaaaaaa", "A", "Artist")
        val b = Video("bbbbbbbbbbb", "B", "Artist")
        var state = PlaybackQueueState(listOf(a,b), a.id, "liked-music")
        val player = QueueSessionPlayer(raw(), { state }) { forward ->
            val target = if (forward) state.next() else state.previous()
            if (target != null) state = state.move(target, emptyList())
        }
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        player.seekToNextMediaItem()
        player.refreshCommands() // Service's queue-state collector invalidates the state adapter.
        assertEquals(b.id, state.currentId)
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        player.seekToPrevious()
        assertEquals(a.id, state.currentId)
    }
    @Test fun queueUpdatesNotifyTheSessionAboutButtonAvailability() {
        var changes = 0
        var batches = 0
        val a = Video("aaaaaaaaaaa", "A", "Artist")
        val b = Video("bbbbbbbbbbb", "B", "Artist")
        var queue = PlaybackQueueState(listOf(a), a.id, "playlist")
        val player = QueueSessionPlayer(raw(), { queue }) { }
        val listener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { changes++ }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) batches++
            }
        }
        player.addListener(listener)
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) // establish initial state
        queue = queue.copy(items = listOf(a,b))
        player.refreshCommands()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, changes)
        assertEquals(1, batches)
        player.removeListener(listener)
        queue = queue.copy(items = listOf(a))
        player.refreshCommands()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, changes)
    }
    @Test fun timelineAndPlaybackCallbacksReachSessionListenersNotOnlyCommands() {
        val raw = raw()
        val player = QueueSessionPlayer(raw, { PlaybackQueueState() }) { }
        var timelines = 0
        var playIntents = 0
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { timelines++ }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playIntents++ }
        })
        assertTrue(player.currentTimeline.isEmpty) // establish state before mutations
        raw.setMediaItem(androidx.media3.common.MediaItem.fromUri("file:///test.mp4"))
        raw.playWhenReady = true
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("Timeline callbacks must not disappear in a Kotlin listener delegate", timelines > 0)
        assertTrue("Play intent callbacks must reach the session", playIntents > 0)
        player.release()
    }
}
