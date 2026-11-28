package com.prism.music.photon

import android.view.KeyEvent

/** Handle remote Back before native PlayerView or Compose focus can swallow it. */
internal class TvBackDispatcher {
    var action: (() -> Unit)? = null
    fun dispatch(event: KeyEvent): Boolean {
        val callback = action ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_BACK && event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) callback()
        return true
    }
}
