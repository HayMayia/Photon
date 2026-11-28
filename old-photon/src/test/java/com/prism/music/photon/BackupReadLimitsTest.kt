package com.prism.music.photon

import com.prism.music.data.BackupReadLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupReadLimitsTest {
    @Test fun exactBudgetIsAcceptedAcrossEntries() {
        val limit = BackupReadLimits(4)
        val output = ByteArrayOutputStream()
        repeat(2) { limit.nextEntry(); limit.copy(ByteArrayInputStream(byteArrayOf(1, 2)), output) }
        assertEquals(4, output.size())
    }
    @Test fun rejectsExpandedPayloadBeyondBudget() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            BackupReadLimits(1).copy(ByteArrayInputStream(byteArrayOf(1, 2)), output)
        }
        assertEquals(0, output.size())
    }
    @Test fun manifestHasItsOwnBoundEvenWithFreeDisk() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupReadLimits(100).copy(ByteArrayInputStream(ByteArray(10)), ByteArrayOutputStream(), 9)
        }
    }
    @Test fun emptyEntryBombIsBounded() {
        val limit = BackupReadLimits(0)
        repeat(100_000) { limit.nextEntry() }
        assertThrows(IllegalArgumentException::class.java) { limit.nextEntry() }
    }
}
