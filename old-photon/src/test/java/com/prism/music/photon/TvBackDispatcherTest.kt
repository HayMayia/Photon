package com.prism.music.photon
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TvBackDispatcherTest {
    @Test fun remoteExitIsHandledBeforeFocusHierarchyAndOnlyOnce() {
        val back = TvBackDispatcher(); var exits = 0
        assertFalse(back.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        back.action = { exits++ }
        assertTrue(back.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertEquals(0, exits)
        assertTrue(back.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertEquals(1, exits)
        assertFalse(back.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        back.action = null
        assertFalse(back.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
    }
}
