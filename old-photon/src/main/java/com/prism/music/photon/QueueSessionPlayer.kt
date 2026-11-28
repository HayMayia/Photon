package com.prism.music.photon

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** State adapter owns coherent events; outer transport intercepts all four queue commands. */
@UnstableApi
internal class QueueSessionPlayer private constructor(
    private val statePlayer: QueueCommandStatePlayer, private val navigate: (Boolean) -> Unit,
) : ForwardingPlayer(statePlayer) {
    constructor(player: Player, queue: () -> PlaybackQueueState, navigate: (Boolean) -> Unit) :
        this(QueueCommandStatePlayer(player, queue), navigate)
    override fun seekToNext() { navigate(true) }
    override fun seekToNextMediaItem() { navigate(true) }
    override fun seekToPrevious() { navigate(false) }
    override fun seekToPreviousMediaItem() { navigate(false) }
    fun refreshCommands() { statePlayer.refreshCommands() }
}

/** Never use Kotlin `Player.Listener by listener`: Java default callbacks can be lost. */
@UnstableApi
private class QueueCommandStatePlayer(player: Player, private val queue: () -> PlaybackQueueState) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val queue = queue()
        val commands = state.availableCommands.buildUpon().apply {
            for (command in listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                if (queue.next() != null || queue.continuation != null) add(command) else remove(command)
            }
            for (command in listOf(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
                if (queue.previous() != null) add(command) else remove(command)
            }
        }.build()
        return state.buildUpon().setAvailableCommands(commands).build()
    }
    fun refreshCommands() { invalidateState() }
}
