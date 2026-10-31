package com.prism.music.photon

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountLibraryAdmissionTest {
    private fun row(id: String) = AccountEntry(id, id, AccountEntryKind.VIDEO, Video(id, id, "Creator"))
    @Test fun unknownShortsAreClassifiedBeforeSpansCommitDespiteOutOfOrderResponses() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()
        val loading = async {
            AccountLibraryAdmission.prepare(listOf(row("a"), row("b"), row("normal"))) { video ->
                if (video.id == "a") first.await()
                if (video.id == "b") secondFinished.complete(Unit)
                video.copy(short = video.id != "normal")
            }
        }
        secondFinished.await()
        assertFalse(loading.isCompleted)
        first.complete(Unit)
        val admitted = AccountLibraryRows.append(emptyList(), loading.await())
        assertEquals(listOf("a", "b", "normal"), admitted.map { it.video!!.id })
        assertEquals(listOf(1, 1, 2), admitted.map(AccountGridPolicy::span))
        assertEquals(listOf(9f / 16f, 9f / 16f, 16f / 9f), admitted.map(AccountGridPolicy::ratio))
        val updated = AccountLibraryRows.metadata(admitted, admitted.first().video!!.copy(thumbnail = "new"))
        assertEquals(admitted.map(AccountGridPolicy::span), updated.map(AccountGridPolicy::span))
    }
    @Test fun unresolvedFormatDoesNotProduceAFallbackLandscapeCard() = runBlocking {
        try {
            AccountLibraryAdmission.prepare(listOf(row("unavailable"))) { throw java.io.IOException("Unknown") }
            fail("Unknown format must remain unadmitted for explicit retry")
        } catch (_: java.io.IOException) { }
    }
}
