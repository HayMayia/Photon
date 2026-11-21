package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class AccountLibraryStabilityTest {
    private fun row(id: String, short: Boolean = false, entryId: String = id) =
        AccountEntry(entryId, "Video $id", AccountEntryKind.VIDEO, Video(id, "Video $id", "Creator", short = short))

    @Test fun lateMetadataCannotChangeAdmittedSpansOrFrameSizes() {
        val original = AccountLibraryRows.append(emptyList(), listOf(row("a"), row("b", true), row("c", true)))
        val enriched = AccountLibraryRows.metadata(original, original.first().video!!.copy(short = true, thumbnail = "portrait"))
        assertTrue(enriched.first().video!!.short)
        assertEquals(original.map(AccountGridPolicy::span), enriched.map(AccountGridPolicy::span))
        assertEquals(original.map(AccountGridPolicy::ratio), enriched.map(AccountGridPolicy::ratio))
        assertEquals(original.map(AccountLibraryRows::key), enriched.map(AccountLibraryRows::key))
    }
    @Test fun overlappingPagesDeduplicateByActualVideoNotRendererId() {
        val first = AccountLibraryRows.append(emptyList(), listOf(row("a", true), row("b", true)))
        val result = AccountLibraryRows.append(first, listOf(row("b", true, "other-renderer"), row("c", true), row("c", true, "duplicate")))
        assertEquals(listOf("a", "b", "c"), result.map { it.video!!.id })
        assertEquals(first, result.take(2))
    }
    @Test fun datasetRetainsProofAfterGlobalCacheEvictionAndRefreshCanRepack() {
        val first = AccountLibraryRows.append(emptyList(), listOf(row("a")))
        val resolved = first.single().video!!.copy(short = true)
        val enriched = AccountLibraryRows.metadata(first, resolved)
        val appended = AccountLibraryRows.append(enriched, listOf(row("a"), row("b")), emptyMap())
        assertTrue(appended.first().video!!.short)
        assertEquals(2, AccountGridPolicy.span(appended.first()))
        val refreshed = AccountLibraryRows.append(emptyList(), listOf(row("a")), mapOf("a" to resolved))
        assertEquals(1, AccountGridPolicy.span(refreshed.first()))
    }
}
