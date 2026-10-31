package com.prism.music.photon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Optional metadata must never gate real account rows. Unknown frames are paired, not fake Shorts. */
internal object AccountLibraryAdmission {
    suspend fun prepare(rows: List<AccountEntry>, classify: suspend (Video) -> Video): List<AccountEntry> = coroutineScope {
        rows.map { row -> async {
            val source = row.video
            if (source == null) row.copy(layoutShort = false)
            else try {
                val video = if (source.short) source else classify(source)
                row.copy(video = video, layoutShort = video.short)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Reserve a stable half-width portrait frame without changing actual format/playback.
                row.copy(layoutShort = true)
            }

        } }.awaitAll()
    }
}
