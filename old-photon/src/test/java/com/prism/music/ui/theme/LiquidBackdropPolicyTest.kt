package com.prism.music.ui.theme

import org.junit.Assert.*
import org.junit.Test

class LiquidBackdropPolicyTest {
    @Test fun renderEffectsRequireAndroid12AndHardware() {
        assertFalse(liquidBackdropEnabled(26, true, true, false))
        assertFalse(liquidBackdropEnabled(30, true, true, false))
        assertTrue(liquidBackdropEnabled(31, true, true, false))
        assertTrue(liquidBackdropEnabled(34, true, true, false))
        assertFalse(liquidBackdropEnabled(34, false, true, false))
    }
    @Test fun performanceModeAndGlassOptOutAlwaysDisableEffects() {
        for (sdk in listOf(26, 31, 33, 36)) {
            assertFalse(liquidBackdropEnabled(sdk, true, true, true))
            assertFalse(liquidBackdropEnabled(sdk, true, false, false))
        }
    }
}
