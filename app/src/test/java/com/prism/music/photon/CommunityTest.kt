package com.prism.music.photon

import android.app.Application
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CommunityTest {
    private lateinit var store: PhotonStore
    private val video = Video("dQw4w9WgXcQ", "Video", "Channel", channelUrl = "https://www.youtube.com/@creator")
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("photon_library", 0).edit().clear().commit()
        store = PhotonStore(context)
    }
    @Test fun oldLibraryDataMigratesWithEmptyCollections() {
        val result = Json.decodeFromString<PhotonLibrary>("""{"saved":[],"history":[],"dark":false}""")
        assertFalse(result.dark); assertTrue(result.subscriptions.isEmpty()); assertTrue(result.playlists.isEmpty()); assertTrue(result.progress.isEmpty())
    }
    @Test fun subscriptionsPersistAndToggleWithoutDuplicates() {
        val channel = Channel(video.channelUrl, "Creator")
        store.subscribe(channel)
        assertEquals(listOf(channel), PhotonStore(RuntimeEnvironment.getApplication()).state.value.subscriptions)
        store.subscribe(channel)
        assertTrue(store.state.value.subscriptions.isEmpty())
    }
    @Test fun playlistNamesTrimAndVideosDeduplicate() {
        val id = store.createPlaylist("  Watch later  ")
        store.playlistVideo(id, video); store.playlistVideo(id, video)
        assertEquals("Watch later", store.state.value.playlists.single().name)
        assertEquals(listOf(video), store.state.value.playlists.single().videos)
        store.playlistVideo(id, video, true)
        assertTrue(store.state.value.playlists.single().videos.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun emptyPlaylistNameRejected() { store.createPlaylist("   ") }
    @Test fun deletingPlaylistDoesNotDeleteSavedOrDownloads() {
        val id = store.createPlaylist("Test")
        store.toggleSaved(video)
        store.downloaded(SavedDownload(video, "/files/video.mp4", 1080, 20))
        store.deletePlaylist(id)
        assertTrue(store.state.value.playlists.isEmpty()); assertEquals(1, store.state.value.saved.size); assertEquals(1, store.state.value.downloads.size)
    }
    @Test fun progressAccumulatesActualElapsedTimeNotSeekDistance() {
        store.recordPlayback(video, 90_000, 600_000, 5_000)
        store.recordPlayback(video, 300_000, 600_000, 5_000)
        val progress = store.state.value.progress.getValue(video.id)
        assertEquals(10_000L, progress.watchedMs); assertEquals(300_000L, progress.resumeMs())
        assertEquals(listOf(video), store.state.value.history)
    }
    @Test fun resumeRestartsCompletedAndNearlyNewVideos() {
        assertEquals(0L, WatchProgress(4_000, 100_000).resumeMs())
        assertEquals(0L, WatchProgress(95_000, 100_000).resumeMs())
        assertEquals(50_000L, WatchProgress(50_000, 100_000).resumeMs())
    }
    @Test fun progressHasSameBoundedRetentionAsHistory() {
        repeat(110) { store.recordPlayback(video.copy(id = "id-$it"), 10_000, 100_000, 5_000) }
        assertEquals(100, store.state.value.history.size); assertEquals(100, store.state.value.progress.size)
    }
    @Test fun recommendationsExcludeSeenDeduplicateAndLimitChannelMonopoly() {
        val candidates = (0..6).map { video.copy(id = "candidate-$it") } + listOf(video, video)
        val result = RecommendationRank.rank(candidates, PhotonLibrary(history = listOf(video)))
        assertEquals(3, result.size); assertFalse(result.any { it.id == video.id }); assertEquals(result.size, result.distinctBy { it.id }.size)
    }
    @Test fun canonicalChannelAndPlaylistLinksRejectHostSpoofing() {
        assertEquals("https://www.youtube.com/@creator", YouTubeLinks.channelUrl("https://youtube.com/@creator/videos"))
        assertEquals("https://www.youtube.com/playlist?list=PL123", YouTubeLinks.playlistUrl("https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
        assertNull(YouTubeLinks.channelUrl("https://youtube.com.evil.test/@creator"))
        assertNull(YouTubeLinks.playlistUrl("https://youtube.com@evil.test/playlist?list=PL123"))
        assertNull(YouTubeLinks.channelUrl("file:///channel/UC123"))
    }
}
