package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PhotonSleepTimerTest {
    @Test fun startsOffAndExpiresOnlyOnceAtItsMonotonicDeadline() {
        var now = 500L
        val timer = PhotonSleepTimer { now }
        assertNull(timer.deadline.value)
        timer.set(15)
        val deadline = timer.deadline.value!!
        assertEquals(900_500L, deadline)
        assertFalse(timer.expire(deadline))
        now = deadline
        assertTrue(timer.expire(deadline))
        assertNull(timer.deadline.value)
        assertFalse(timer.expire(deadline))
    }
    @Test fun cancellingOrReplacingPreventsAnOlderJobFromPausingPlayback() {
        var now = 0L
        val timer = PhotonSleepTimer { now }
        timer.set(15)
        val old = timer.deadline.value!!
        now = 100L
        timer.set(60)
        val replacement = timer.deadline.value
        now = old
        assertFalse(timer.expire(old))
        assertEquals(replacement, timer.deadline.value)
        timer.set(0)
        now = replacement!!
        assertFalse(timer.expire(replacement))
        assertNull(timer.deadline.value)
    }
    @Test fun unsupportedDurationsDoNotChangeAnExistingTimer() {
        val timer = PhotonSleepTimer { 100L }
        timer.set(30)
        val original = timer.deadline.value
        try { timer.set(-1); fail("Must reject invalid duration") } catch (_: IllegalArgumentException) { }
        assertEquals(original, timer.deadline.value)
    }
}
