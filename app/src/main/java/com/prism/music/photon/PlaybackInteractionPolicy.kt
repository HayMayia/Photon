package com.prism.music.photon

internal enum class DoubleTapAction { NONE, LIKE, REWIND, FORWARD }
internal object PlaybackInteractionPolicy {
    fun doubleTap(short: Boolean, fraction: Float): DoubleTapAction = when {
        !fraction.isFinite() || fraction !in 0.04f..0.96f -> DoubleTapAction.NONE
        short -> DoubleTapAction.LIKE
        fraction in 0.08f..0.38f -> DoubleTapAction.REWIND
        fraction in 0.62f..0.92f -> DoubleTapAction.FORWARD
        else -> DoubleTapAction.NONE
    }
}
