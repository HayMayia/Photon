package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class PlaybackInteractionPolicyTest {
    @Test fun shortDoubleTapLikesAndNeverSeeks() {
        listOf(.1f, .3f, .5f, .7f, .9f).forEach { assertEquals(DoubleTapAction.LIKE, PlaybackInteractionPolicy.doubleTap(true, it)) }
    }
    @Test fun normalCentreAndExtremeEdgesAreInert() {
        listOf(0f, .03f, .05f, .4f, .5f, .6f, .95f, 1f, Float.NaN).forEach { assertEquals(DoubleTapAction.NONE, PlaybackInteractionPolicy.doubleTap(false, it)) }
    }
    @Test fun normalInnerSidesSeekTenSeconds() {
        assertEquals(DoubleTapAction.REWIND, PlaybackInteractionPolicy.doubleTap(false, .25f))
        assertEquals(DoubleTapAction.FORWARD, PlaybackInteractionPolicy.doubleTap(false, .75f))
    }
    @Test fun multichannelOriginalBeatsHigherBitrateStereoButNeverOriginalLanguage() {
        assertEquals(1, OriginalAudioPolicy.pick(listOf(OriginalAudioPolicy.Track("ORIGINAL", 320, 2), OriginalAudioPolicy.Track("ORIGINAL", 256, 6))))
        assertEquals(0, OriginalAudioPolicy.pick(listOf(OriginalAudioPolicy.Track("ORIGINAL", 128, 2), OriginalAudioPolicy.Track("DUBBED", 512, 8))))
    }
}
