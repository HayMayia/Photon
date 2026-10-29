package com.prism.music.photon

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotonStoreTest {
    private lateinit var store: PhotonStore
    private val video = Video("dQw4w9WgXcQ", "Example", "Creator")
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("photon_library", 0).edit().clear().commit()
        store = PhotonStore(context)
    }
    @Test fun savesAndUnsavesWithoutDuplicates() {
        store.toggleSaved(video)
        assertEquals(listOf(video), store.state.value.saved)
        store.toggleSaved(video)
        assertTrue(store.state.value.saved.isEmpty())
    }
    @Test fun historyMovesToFrontAndCapsAtOneHundred() {
        repeat(110) { store.watched(video.copy(id = "video-$it")) }
        store.watched(video.copy(id = "video-100"))
        assertEquals(100, store.state.value.history.size)
        assertEquals("video-100", store.state.value.history.first().id)
        assertEquals(1, store.state.value.history.count { it.id == "video-100" })
    }
    @Test fun clearingHistoryPreservesDownloadsAndSaved() {
        store.toggleSaved(video); store.watched(video)
        val download = SavedDownload(video, "/files/example.mp4", 1080, 1024)
        store.downloaded(download)
        store.update { it.copy(history = emptyList()) }
        assertEquals(listOf(video), store.state.value.saved)
        assertEquals(listOf(download), store.state.value.downloads)
    }
    @Test fun downloadReplacementDeduplicatesSameQuality() {
        val download = SavedDownload(video, "/files/a.mp4", 1080, 1024)
        store.downloaded(download); store.downloaded(download.copy(bytes = 2048))
        assertEquals(1, store.state.value.downloads.size)
        assertEquals(2048L, store.state.value.downloads.single().bytes)
        store.downloaded(download.copy(height = 720))
        assertEquals(2, store.state.value.downloads.size)
    }
    @Test fun persistsPreferencesAndLibraryAcrossInstances() {
        store.toggleSaved(video)
        store.update { it.copy(dark = false, defaultHeight = 720, wifiOnly = false) }
        val reopened = PhotonStore(RuntimeEnvironment.getApplication()).state.value
        assertFalse(reopened.dark); assertFalse(reopened.wifiOnly)
        assertEquals(720, reopened.defaultHeight)
        assertEquals(listOf(video), reopened.saved)
    }
    @Test fun durations() {
        assertEquals("0:00", durationLabel(0))
        assertEquals("1:05", durationLabel(65))
        assertEquals("1:01:01", durationLabel(3661))
        assertEquals("LIVE", durationLabel(-1))
    }
    @Test fun pausedHistoryDoesNotSavePlaybackOrResumePositions() {
        store.update { it.copy(historyPaused = true) }
        store.watched(video)
        store.recordPlayback(video, 30_000, 120_000, 10_000)
        assertTrue(store.state.value.history.isEmpty())
        assertTrue(store.state.value.progress.isEmpty())
    }
    @Test fun pausingRetainsExistingHistoryAndExplicitUnpauseAllowsNewTracking() {
        store.recordPlayback(video, 20_000, 120_000, 5_000)
        val old = store.state.value.progress
        store.update { it.copy(historyPaused = true) }
        store.recordPlayback(video, 60_000, 120_000, 30_000)
        assertEquals(old, store.state.value.progress)
        store.update { it.copy(historyPaused = false) }
        store.recordPlayback(video, 70_000, 120_000, 5_000)
        assertEquals(70_000L, store.state.value.progress[video.id]?.positionMs)
        assertEquals(10_000L, store.state.value.progress[video.id]?.watchedMs)
    }
    @Test fun homeModeAndLocalPauseSurviveRestartWithoutStoringCookiesInLibrary() {
        store.update { it.copy(youtubeHome = true, youtubeWebUser = 2, historyPaused = true) }
        val reopened = PhotonStore(RuntimeEnvironment.getApplication()).state.value
        assertTrue(reopened.youtubeHome); assertTrue(reopened.historyPaused)
        assertEquals(2, reopened.youtubeWebUser)
        assertTrue(reopened.saved.isEmpty())
    }

}
