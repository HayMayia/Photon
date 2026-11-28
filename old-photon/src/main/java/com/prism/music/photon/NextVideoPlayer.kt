package com.prism.music.photon

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** PlayerView normally disables Next because Photon resolves one stream at a time.
 * Advertise the real app queue action, not a nonexistent second ExoPlayer item. */
@UnstableApi
internal class NextVideoPlayer(player: Player, private val available: Boolean, private val next: () -> Unit) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().apply {
        if (available) { add(Player.COMMAND_SEEK_TO_NEXT); add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        else { remove(Player.COMMAND_SEEK_TO_NEXT); remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
    }.build()
    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
    override fun seekToNext() { if (available) next() }
    override fun seekToNextMediaItem() { if (available) next() }
}
