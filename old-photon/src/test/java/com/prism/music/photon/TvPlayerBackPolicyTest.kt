package com.prism.music.photon

import org.junit.Assert.assertEquals
import org.junit.Test

class TvPlayerBackPolicyTest {
    @Test fun shelfThenHudThenExit() {
        assertEquals(TvPlayerBackPolicy.Action.CLOSE_SHELF, TvPlayerBackPolicy.action(true, true))
        assertEquals(TvPlayerBackPolicy.Action.CLOSE_SHELF, TvPlayerBackPolicy.action(true, false))
        assertEquals(TvPlayerBackPolicy.Action.HIDE_HUD, TvPlayerBackPolicy.action(false, true))
        assertEquals(TvPlayerBackPolicy.Action.EXIT, TvPlayerBackPolicy.action(false, false))
    }
}
