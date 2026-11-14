package com.prism.music.photon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A read-only projection of authenticated Google likes, never another playlist or a local likes database. */
internal object LikedMusicRows {
    data class Batch(val items: List<AccountEntry>, val unresolved: Int)
    suspend fun classify(rows: List<AccountEntry>, proof: suspend (Video) -> Boolean?): Batch = coroutineScope {
        val slots = Semaphore(4)
        val classified = rows.map { row -> async {
            val video = row.video
            if (row.kind != AccountEntryKind.VIDEO || video == null) return@async null to false
            try {
                val music = if (video.music) true else slots.withPermit { proof(video) }
                (if (music == true) row.copy(video = video.copy(music = true), layoutShort = video.short) else null) to (music == null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null to true }
        } }.awaitAll()
        Batch(classified.mapNotNull { it.first }.distinctBy { it.id }, classified.count { it.second })
    }
    fun message(scanning: Boolean, unresolved: Int, scanned: Int): String? = when {
        scanning -> "Checking older Google likes · $scanned videos checked" + if (unresolved > 0) " · $unresolved music classifications unavailable" else ""
        unresolved > 0 -> "$unresolved liked videos could not be classified. They were not treated as non-music; Refresh to retry. Unavailable/restricted videos may remain unresolved."
        else -> null
    }
}
