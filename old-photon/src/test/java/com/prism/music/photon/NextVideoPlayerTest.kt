package com.prism.music.photon

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class NextVideoPlayerTest {
    private fun emptyPlayer(): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getAvailableCommands" -> Player.Commands.Builder().build()
            "isCommandAvailable" -> false
            else -> null
        }
    } as Player
    @Test fun nextIsEnabledEvenForASingleExtractedStreamAndRunsTheAppAction() {
        var calls = 0
        val player = NextVideoPlayer(emptyPlayer(), true) { calls++ }
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        player.seekToNext(); player.seekToNextMediaItem()
        assertEquals(2, calls)
    }
    @Test fun noRecommendationOrPlaylistSuccessorDoesNotAdvertiseFakeNext() {
        var calls = 0
        val player = NextVideoPlayer(emptyPlayer(), false) { calls++ }
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        player.seekToNext()
        assertEquals(0, calls)
    }
}
