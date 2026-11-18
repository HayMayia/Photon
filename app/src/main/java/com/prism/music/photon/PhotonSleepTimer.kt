package com.prism.music.photon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local, monotonic deadline. Never changes preferences or Google watch-history policy. */
internal class PhotonSleepTimer(private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private val mutable = MutableStateFlow<Long?>(null)
    val deadline = mutable.asStateFlow()
    fun set(minutes: Int) {
        require(minutes in listOf(0, 15, 30, 60))
        mutable.value = if (minutes == 0) null else clock() + minutes * 60_000L
    }
    fun expire(expected: Long): Boolean {
        if (clock() < expected) return false
        return mutable.compareAndSet(expected, null)
    }
}
