package com.prism.music.photon

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Resolve a small ordered batch BEFORE committing its spans; never admit unknown as landscape. */
internal object AccountLibraryAdmission {
    suspend fun prepare(rows: List<AccountEntry>, classify: suspend (Video) -> Video): List<AccountEntry> = coroutineScope {
        rows.map { row -> async {
            val video = row.video?.let { if (it.short) it else classify(it) }
            row.copy(video = video, layoutShort = video?.short == true)
        } }.awaitAll()
    }
}
