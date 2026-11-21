package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class OriginalAudioPolicyTest {
    private fun track(type: String?, rate: Int) = OriginalAudioPolicy.Track(type, rate)
    @Test fun originalWinsOverHigherBitrateDub() {
        assertEquals(1, OriginalAudioPolicy.pick(listOf(track("DUBBED", 256), track("ORIGINAL", 128))))
    }
    @Test fun originalWinsOverUnlabelled() {
        assertEquals(1, OriginalAudioPolicy.pick(listOf(track(null, 256), track("ORIGINAL", 64))))
    }
    @Test fun bestBitrateWithinOriginalOnly() {
        assertEquals(2, OriginalAudioPolicy.pick(listOf(track("DUBBED", 320), track("ORIGINAL", 64), track("ORIGINAL", 128))))
    }
    @Test fun unlabelledFallbackDoesNotSelectDub() {
        assertEquals(1, OriginalAudioPolicy.pick(listOf(track("DUBBED", 256), track(null, 64))))
    }
    @Test fun noSilentDubOrDescriptionFallback() {
        assertNull(OriginalAudioPolicy.pick(listOf(track("DUBBED", 256), track("DESCRIPTIVE", 128), track("SECONDARY", 320))))
    }
    @Test fun emptyListReturnsNoAudio() { assertNull(OriginalAudioPolicy.pick(emptyList())) }
    @Test fun homeIncludesBothMusicAndGeneralVideos() {
        assertTrue(HomeCatalog.sections.any { it.music })
        assertTrue(HomeCatalog.sections.any { !it.music })
        assertEquals(HomeCatalog.sections.size, HomeCatalog.sections.distinctBy { it.id }.size)
    }
}
