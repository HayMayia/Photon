package com.prism.music.photon

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedDownload(val video: Video, val path: String, val height: Int, val bytes: Long, val thumbnailPath: String? = null)

@Serializable
data class Channel(val url: String, val name: String, val avatar: String = "", val subscribers: Long = -1)

@Serializable
data class LocalPlaylist(val id: String, val name: String, val videos: List<Video> = emptyList())

@Serializable
data class WatchProgress(val positionMs: Long = 0, val durationMs: Long = 0, val watchedMs: Long = 0, val updatedAt: Long = 0) {
    fun resumeMs(): Long = if (positionMs < 5_000 || (durationMs > 0 && positionMs >= durationMs - 10_000)) 0 else positionMs
}

@Serializable
data class PhotonLibrary(
    val saved: List<Video> = emptyList(),
    val history: List<Video> = emptyList(),
    val downloads: List<SavedDownload> = emptyList(),
    val dark: Boolean = true,
    val defaultHeight: Int = 1080,
    val wifiOnly: Boolean = true,
    val accent: Int = 0xFF7C5CFF.toInt(),
    val subscriptions: List<Channel> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val progress: Map<String, WatchProgress> = emptyMap(),
    val historyPaused: Boolean = false,
    val youtubeHome: Boolean = false,
    val youtubeWebUser: Int = 0,
    val youtubeSessionRevision: Long = 0,
    val youtubeSessionReady: Boolean = true,
    val accountHistorySync: Boolean = false,
)

/** One application-scoped store; updates from workers and UI are serialized together. */
class PhotonStore(context: Context) {
    private val preferences = context.getSharedPreferences("photon_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(runCatching {
        json.decodeFromString<PhotonLibrary>(preferences.getString("library", "{}")!!)
    }.getOrDefault(PhotonLibrary()))
    val state: StateFlow<PhotonLibrary> = mutable

    @Synchronized
    fun update(transform: (PhotonLibrary) -> PhotonLibrary) {
        val next = transform(mutable.value)
        preferences.edit().putString("library", json.encodeToString(next)).apply()
        mutable.value = next
    }
    fun watched(video: Video) = update { if (it.historyPaused) it else it.copy(history = (listOf(video) + it.history.filterNot { v -> v.id == video.id }).take(100)) }
    fun toggleSaved(video: Video) = update {
        it.copy(saved = if (it.saved.any { v -> v.id == video.id }) it.saved.filterNot { v -> v.id == video.id } else listOf(video) + it.saved)
    }
    fun subscribe(channel: Channel) = update { library ->
        library.copy(subscriptions = if (library.subscriptions.any { it.url == channel.url }) library.subscriptions.filterNot { it.url == channel.url } else library.subscriptions + channel)
    }
    fun createPlaylist(name: String): String {
        val clean = name.trim().take(100)
        require(clean.isNotEmpty()) { "Give your playlist a name" }
        val id = java.util.UUID.randomUUID().toString()
        update { it.copy(playlists = it.playlists + LocalPlaylist(id, clean)) }
        return id
    }
    fun playlistVideo(id: String, video: Video, remove: Boolean = false) = update { library ->
        library.copy(playlists = library.playlists.map { list ->
            if (list.id != id) list else list.copy(videos = if (remove) list.videos.filterNot { it.id == video.id } else (list.videos + video).distinctBy { it.id })
        })
    }
    fun deletePlaylist(id: String) = update { it.copy(playlists = it.playlists.filterNot { list -> list.id == id }) }
    fun recordPlayback(video: Video, positionMs: Long, durationMs: Long, elapsedMs: Long) = update { library ->
        if (library.historyPaused) return@update library
        val history = (listOf(video) + library.history.filterNot { it.id == video.id }).take(100)
        val old = library.progress[video.id] ?: WatchProgress()
        val progress = WatchProgress(positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), old.watchedMs + elapsedMs.coerceAtLeast(0), System.currentTimeMillis())
        library.copy(history = history, progress = (library.progress + (video.id to progress)).filterKeys { id -> history.any { it.id == id } })
    }
    fun downloaded(download: SavedDownload) = update { it.copy(downloads = listOf(download) + it.downloads.filterNot { v -> v.video.id == download.video.id && v.height == download.height }) }
}
