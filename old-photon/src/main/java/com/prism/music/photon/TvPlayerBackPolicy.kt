package com.prism.music.photon

/** One priority order for both Activity remote dispatch and Compose Back. */
internal object TvPlayerBackPolicy {
    enum class Action { CLOSE_SHELF, HIDE_HUD, EXIT }
    fun action(shelfVisible: Boolean, hudVisible: Boolean): Action = when {
        shelfVisible -> Action.CLOSE_SHELF
        hudVisible -> Action.HIDE_HUD
        else -> Action.EXIT
    }
}
