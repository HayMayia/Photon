package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MusicPlaybackPolicyTest {
    @Test fun defaultsMigrateExistingUsersToMusicOnly() {
        val old = Json.decodeFromString<PhotonLibrary>("""{"youtubeHome":true,"youtubeSessionRevision":42}""")
        assertTrue(old.musicBackground)
        assertFalse(old.videoBackground)
        assertEquals(42L, old.youtubeSessionRevision)
    }
    @Test fun switchesPersistIndependently() {
        val settings = PhotonLibrary(musicBackground = false, videoBackground = true, historyPaused = true)
        assertEquals(settings, Json.decodeFromString<PhotonLibrary>(Json.encodeToString(settings)))
    }
    @Test fun musicNeedsSourceCategoryNotWordsOrPlaylistMembership() {
        assertTrue(MusicPlaybackPolicy.isMusicCategory("Music"))
        assertTrue(MusicPlaybackPolicy.isMusicCategory("10"))
        assertFalse(MusicPlaybackPolicy.isMusicCategory(null))
        assertFalse(MusicPlaybackPolicy.isMusicCategory("Entertainment"))
        assertFalse(MusicPlaybackPolicy.isMusicCategory("Music reviews and documentaries"))
        assertFalse(Video("abcdefghijk", "Official Music Video", "Music channel").music)
    }
    @Test fun unknownIsRegularAndDefaultsOff() {
        assertTrue(MusicPlaybackPolicy.backgroundAllowed(true, true, false, false))
        assertFalse(MusicPlaybackPolicy.backgroundAllowed(false, true, false, false))
    }
    @Test fun bothSwitchesCanBeEnabledOrDisabled() {
        for (music in listOf(false, true)) {
            assertTrue(MusicPlaybackPolicy.backgroundAllowed(music, true, true, false))
            assertFalse(MusicPlaybackPolicy.backgroundAllowed(music, false, false, false))
        }
    }
    @Test fun musicOffDoesNotTurnOffRegularVideos() {
        assertFalse(MusicPlaybackPolicy.backgroundAllowed(true, false, true, false))
        assertTrue(MusicPlaybackPolicy.backgroundAllowed(false, false, true, false))
    }
    @Test fun tvNeverAllowsBackgroundRegardlessOfPreferences() {
        for (music in listOf(false, true)) for (musicEnabled in listOf(false, true)) for (videoEnabled in listOf(false, true))
            assertFalse(MusicPlaybackPolicy.backgroundAllowed(music, musicEnabled, videoEnabled, true))
    }
    @Test fun sourceMusicProofSurvivesMediaMetadataSerialization() {
        val video = Video("abcdefghijk", "Source", "Creator", music = true)
        assertTrue(Json.decodeFromString<Video>(Json.encodeToString(video)).music)
        assertFalse(Json.decodeFromString<Video>("""{"id":"abcdefghijk","title":"Older download","channel":"Creator"}""").music)
    }
    @Test fun likedMusicReadsTheSameGoogleLikesAsLikedVideos() {
        assertEquals(AccountShelf.LIKED.path, AccountShelf.LIKED_MUSIC.path)
        assertEquals("/playlist?list=LL", AccountShelf.LIKED_MUSIC.path)
    }
}
