package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PhotonNavigationTest {
    @Test fun backRetracesTabsInsteadOfExiting() {
        var history = PhotonNavigation.record(emptyList(), 0, 1)
        history = PhotonNavigation.record(history, 1, 3)
        assertEquals(1, PhotonNavigation.previous(history, 3))
        assertEquals(0, PhotonNavigation.previous(history.dropLast(1), 1))
        assertNull(PhotonNavigation.previous(emptyList(), 0))
    }
    @Test fun reselectingATabDoesNotAddABackStep() {
        assertEquals(listOf(0), PhotonNavigation.record(listOf(0), 1, 1))
    }
    @Test fun restoredNonHomeTabFallsBackToHome() {
        assertEquals(0, PhotonNavigation.previous(emptyList(), 3))
    }
    @Test fun historyIsBoundedAndRetainsMostRecentDestination() {
        val history = PhotonNavigation.record(List(50) { it % 4 }, 3, 0)
        assertEquals(32, history.size)
        assertEquals(3, PhotonNavigation.previous(history, 0))
    }
}
