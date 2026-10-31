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
    @Test fun unavailableMetadataNeverHidesAccountRowsOrMarksThemAsShorts() = runBlocking {
        val source = listOf(row("unavailable"), row("another"), row("third"))
        val prepared = AccountLibraryAdmission.prepare(source) { throw java.io.IOException("Unknown") }
        val admitted = AccountLibraryRows.append(emptyList(), prepared)
        assertEquals(source.map { it.id }, admitted.map { it.id })
        assertTrue(admitted.none { it.video!!.short })
        assertEquals(listOf(1, 1, 1), admitted.map(AccountGridPolicy::span))
        assertEquals(source.map { it.video }, admitted.map { it.video })
        val normal = AccountLibraryRows.metadata(admitted, admitted.first().video!!.copy(short = false))
        val short = AccountLibraryRows.metadata(normal, admitted[1].video!!.copy(short = true))
        assertEquals(admitted.map(AccountGridPolicy::span), short.map(AccountGridPolicy::span))
        assertEquals(admitted.map(AccountGridPolicy::ratio), short.map(AccountGridPolicy::ratio))
    }
    @Test fun singleLookupFailureCannotDiscardHealthyRowsOrContinuationEntries() = runBlocking {
        val prepared = AccountLibraryAdmission.prepare(listOf(row("a"), row("b"), row("c"))) {
            if (it.id == "b") throw java.io.IOException("Unavailable") else it.copy(short = it.id == "a")
        }
        val first = AccountLibraryRows.append(emptyList(), prepared)
        val next = AccountLibraryAdmission.prepare(listOf(row("b"), row("d"))) { throw java.io.IOException("Unavailable") }
        val all = AccountLibraryRows.append(first, next)
        assertEquals(listOf("a", "b", "c", "d"), all.map { it.id })
        assertEquals(listOf(1, 1, 2, 1), all.map(AccountGridPolicy::span))
    }
    @Test fun accountCancellationIsNeverSwallowedAsAMetadataFailure() = runBlocking {
        try {
            AccountLibraryAdmission.prepare(listOf(row("a"))) { throw CancellationException("Account changed") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
