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
    private fun raw(): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getAvailableCommands" -> Player.Commands.Builder().build()
            "isCommandAvailable" -> false
            else -> null
        }
    } as Player
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
        assertEquals(b.id, state.currentId)
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        player.seekToPrevious()
        assertEquals(a.id, state.currentId)
    }
    @Test fun queueUpdatesNotifyTheSessionAboutButtonAvailability() {
        var changes = 0
        var batches = 0
        val player = QueueSessionPlayer(raw(), { PlaybackQueueState() }) { }
        val listener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { changes++ }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) batches++
            }
        }
        player.addListener(listener); player.refreshCommands()
        assertEquals(1, changes)
        assertEquals(1, batches)
        player.removeListener(listener); player.refreshCommands()
        assertEquals(1, changes)
    }
}
