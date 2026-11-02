package com.prism.music.photon

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LikedMusicSnapshot(val revision: Long = -1, val videos: List<Video> = emptyList(), val updatedAt: Long = 0,
    val schemaVersion: Int = 0, val scanComplete: Boolean = false, val unresolved: Int = 0)

/** Separate from playback preferences: progress ticks must not reserialize thousands of likes. */
internal class LikedMusicCache(context: Context) {
    private val preferences = context.getSharedPreferences("photon_liked_music", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    var snapshot: LikedMusicSnapshot = runCatching {
        json.decodeFromString<LikedMusicSnapshot>(preferences.getString("snapshot", "{}")!!)
    }.getOrDefault(LikedMusicSnapshot())
        private set
    fun save(value: LikedMusicSnapshot) {
        preferences.edit().putString("snapshot", json.encodeToString(value)).apply()
        snapshot = value
    }
}

/** Preserve confirmed mutations that raced with a scan whose server page predates them. */
internal object LikedMusicSnapshotUpdates {
    data class Change(val sequence: Long, val video: Video, val liked: Boolean)
    fun merge(videos: List<Video>, changes: List<Change>, startedAt: Long): List<Video> {
        val newer = changes.filter { it.sequence > startedAt }.sortedByDescending { it.sequence }
        val replaced = newer.map { it.video.id }.toSet()
        return (newer.filter { it.liked }.map { it.video.copy(music = true) } + videos.filterNot { it.id in replaced }).distinctBy { it.id }
    }
}

internal object LikedMusicFreshness {
    fun needsRefresh(snapshot: LikedMusicSnapshot?, now: Long): Boolean = snapshot == null || snapshot.schemaVersion < 1 ||
        now - snapshot.updatedAt >= if (snapshot.scanComplete) 6 * 60 * 60_000L else 2 * 60_000L
}
