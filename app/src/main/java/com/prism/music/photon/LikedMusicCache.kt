package com.prism.music.photon

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LikedMusicSnapshot(val revision: Long = -1, val videos: List<Video> = emptyList(), val updatedAt: Long = 0)

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
