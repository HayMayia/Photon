package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OfflineArtworkTest {
    @Test fun legacyDownloadDataKeepsRemoteArtworkAndHasNoLocalFileYet() {
        val value = Json.decodeFromString<SavedDownload>("""{"video":{"id":"dQw4w9WgXcQ","title":"Video","channel":"Creator","thumbnail":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"},"path":"/movies/video.mp4","height":720,"bytes":1024}""")
        assertNull(value.thumbnailPath)
        assertTrue(value.video.thumbnail.startsWith("https://"))
    }
    @Test fun newDownloadPersistsOfflineThumbnailPath() {
        val download = SavedDownload(Video("dQw4w9WgXcQ", "Video", "Creator"), "/movies/video.mp4", 720, 1024, "/movies/video.thumbnail")
        val encoded = Json.encodeToString(SavedDownload.serializer(), download)
        assertEquals(download, Json.decodeFromString<SavedDownload>(encoded))
    }
    @Test fun oldLibraryDefaultsToPrismViolet() {
        assertEquals(0xFF7C5CFF.toInt(), Json.decodeFromString<PhotonLibrary>("{}").accent)
    }
}
