package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class ShortPlayHistoryTest {
    @Test fun shortPlayConsentIsSeparateFromTheOldFifteenSecondDefault() {
        assertEquals(15_000L, AccountHistoryPolicy.reportThreshold(PhotonLibrary()))
        assertEquals(1_000L, AccountHistoryPolicy.reportThreshold(PhotonLibrary(includeShortPlays = true)))
    }
    @Test fun actualShortPlayCanReportOnceButBufferingAndSubsecondDoNot() {
        val gate = PlaybackHistoryGate()
        assertNull(gate.tick("short", 0, true, true, 1_000))
        assertNull(gate.tick("short", 500, true, true, 1_000))
        assertEquals(1_000L, gate.tick("short", 1_000, false, true, 1_000))
        assertNull(gate.tick("short", 10_000, true, true, 1_000))
        val buffered = PlaybackHistoryGate()
        buffered.tick("buffered", 0, false, true, 1_000)
        assertNull(buffered.tick("buffered", 10_000, true, true, 1_000))
    }
}
