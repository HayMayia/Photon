package com.prism.music.photon

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Transport state belongs to the application/service, not the Activity's lifetime. */
@Serializable
internal data class PlaybackQueueState(
    val items: List<Video> = emptyList(), val currentId: String? = null,
    val source: String? = null, val revision: Long = -1,
    val related: List<Video> = emptyList(), val continuation: String? = null,
    val context: JsonObject? = null,
) {
    val index get() = items.indexOfFirst { it.id == currentId }
    fun previous(): Video? = if (index > 0) items[index - 1] else null
    fun next(): Video? = items.getOrNull(index + 1).takeIf { index >= 0 }
        ?: if (source == null) related.firstOrNull { candidate -> items.none { it.id == candidate.id } } else null
    fun upcoming(): List<Video> = if (source != null) items.drop((index + 1).coerceAtLeast(0))
        else items.drop((index + 1).coerceAtLeast(0)) + related.filter { candidate -> items.none { it.id == candidate.id } }
    fun move(video: Video, recommendations: List<Video>): PlaybackQueueState = copy(
        items = if (items.any { it.id == video.id }) items else items + video,
        currentId = video.id, related = if (source == null) recommendations else emptyList())
}
internal data class TransportSelection(val video: Video, val details: VideoDetails?, val localPath: String? = null)

internal class PlaybackQueue(context: Context) {
    private val prefs = context.getSharedPreferences("photon_transport_queue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    val state = MutableStateFlow(runCatching { json.decodeFromString<PlaybackQueueState>(prefs.getString("queue", "{}")!!) }.getOrDefault(PlaybackQueueState()))
    val selection = MutableStateFlow<TransportSelection?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    var publicPage: org.schabi.newpipe.extractor.Page? = null
    var publicUrl: String? = null
    fun update(value: PlaybackQueueState) {
        state.value = value
        prefs.edit().putString("queue", json.encodeToString(value)).apply()
    }
    fun select(video: Video, related: List<Video>, playlist: List<Video>, source: String?, revision: Long, page: AccountLibraryPage?) {
        val old = state.value
        val items = if (source != null) playlist.distinctBy { it.id }.let { if (it.any { v -> v.id == video.id }) it else it + video }
            else if (old.source == null) old.items.take((old.index + 1).coerceAtLeast(0)).filter { it.id != video.id } + video
            else listOf(video)
        update(PlaybackQueueState(items, video.id, source, revision, if (source == null) related else emptyList(), page?.continuation, page?.context))
        error.value = null
    }
}
