package com.prism.music.photon

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Installed on MediaSession itself: notification, Bluetooth and UI share these commands. */
@UnstableApi
internal class QueueSessionPlayer(player: Player, private val queue: () -> PlaybackQueueState,
    private val navigate: (Boolean) -> Unit) : ForwardingPlayer(player) {
    private val listeners = linkedMapOf<Player.Listener, Player.Listener>()
    override fun addListener(listener: Player.Listener) {
        if (listeners.containsKey(listener)) return
        val adapter = object : Player.Listener by listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(this@QueueSessionPlayer.availableCommands)
            }
            override fun onEvents(player: Player, events: Player.Events) { listener.onEvents(this@QueueSessionPlayer, events) }
        }
        listeners[listener] = adapter
        super.addListener(adapter)
    }
    override fun removeListener(listener: Player.Listener) { listeners.remove(listener)?.let { super.removeListener(it) } }
    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().apply {
        val state = queue()
        for (command in listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            if (state.next() != null || state.continuation != null) add(command) else remove(command)
        }
        for (command in listOf(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            if (state.previous() != null) add(command) else remove(command)
        }
    }.build()
    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
    override fun seekToNext() { navigate(true) }
    override fun seekToNextMediaItem() { navigate(true) }
    override fun seekToPrevious() { navigate(false) }
    override fun seekToPreviousMediaItem() { navigate(false) }
    fun refreshCommands() { listeners.keys.toList().forEach { it.onAvailableCommandsChanged(availableCommands) } }
}
