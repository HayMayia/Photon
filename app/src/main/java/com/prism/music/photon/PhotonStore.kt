package com.prism.music.photon

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedDownload(val video: Video, val path: String, val height: Int, val bytes: Long)

@Serializable
data class PhotonLibrary(
    val saved: List<Video> = emptyList(),
    val history: List<Video> = emptyList(),
    val downloads: List<SavedDownload> = emptyList(),
    val dark: Boolean = true,
    val defaultHeight: Int = 1080,
    val wifiOnly: Boolean = true,
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
    fun watched(video: Video) = update { it.copy(history = (listOf(video) + it.history.filterNot { v -> v.id == video.id }).take(100)) }
    fun toggleSaved(video: Video) = update {
        it.copy(saved = if (it.saved.any { v -> v.id == video.id }) it.saved.filterNot { v -> v.id == video.id } else listOf(video) + it.saved)
    }
    fun downloaded(download: SavedDownload) = update { it.copy(downloads = listOf(download) + it.downloads.filterNot { v -> v.video.id == download.video.id && v.height == download.height }) }
}
