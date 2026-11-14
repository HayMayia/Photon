package com.prism.music.photon

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LikedMusicSnapshot(val revision: Long = -1, val videos: List<Video> = emptyList(), val updatedAt: Long = 0,
    val schemaVersion: Int = 0, val scanComplete: Boolean = false, val unresolved: Int = 0,
    val classifications: Map<String, MusicClassification> = emptyMap())

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
    fun needsRefresh(snapshot: LikedMusicSnapshot?, now: Long): Boolean = snapshot == null || snapshot.schemaVersion < 3 ||
        now - snapshot.updatedAt >= if (snapshot.scanComplete) 6 * 60 * 60_000L else 2 * 60_000L
}

/** A first-page check cannot delete older songs simply because they are on later pages. */
internal object LikedMusicHeadUpdates {
    fun progressive(cached: List<Video>, fresh: List<Video>): List<Video> = (fresh + cached).distinctBy { it.id }
    fun merge(cached: List<Video>, incoming: List<Video>, headOrder: List<String>): List<Video> {
        val byId = (cached + incoming).associateBy { it.id }
        val headIds = headOrder.toSet()
        return (headOrder.mapNotNull { byId[it] } + cached.filterNot { it.id in headIds }).distinctBy { it.id }
    }
}

@Serializable
internal data class MusicClassification(val music: Boolean, val checkedAt: Long)

/** Cache both yes and no decisions; cached list membership itself is never classification proof. */
internal object MusicClassificationPolicy {
    // Classification metadata changes much less often than the account's like membership.
    // Keep source reconciliation at six hours without re-fetching every watch page each time.
    const val TTL_MS = 30L * 24 * 60 * 60_000L
    fun verdict(snapshot: LikedMusicSnapshot, revision: Long, id: String, now: Long): Boolean? {
        if (snapshot.revision != revision || snapshot.schemaVersion < 2) return null
        val proof = snapshot.classifications[id] ?: return null
        return proof.music.takeIf { now >= proof.checkedAt && now - proof.checkedAt < TTL_MS }
    }
    fun verified(snapshot: LikedMusicSnapshot, revision: Long): List<Video> =
        if (snapshot.revision != revision) emptyList()
        else if (snapshot.schemaVersion >= 3) snapshot.videos
        else if (snapshot.schemaVersion == 2) snapshot.videos.filter { snapshot.classifications[it.id]?.music == true }
        else emptyList()
}
