package com.prism.music.photon

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

internal object PlayerOptions {
    val speeds = (1..40).map { it / 4f }
    val subtitleSizes = (1..8).map { it * 25 }
    fun speed(value: Float): Float = if (value.isFinite()) value.coerceIn(.1f, 10f) else 1f
    fun subtitleSize(value: Int): Int = value.coerceIn(25, 200)
    fun fullscreen(tv: Boolean, manual: Boolean, landscape: Boolean, expanded: Boolean, pip: Boolean, shorts: Boolean): Boolean =
        PlayerLayoutPolicy.immersive(manual, landscape && !tv, expanded, pip, shorts)
}

internal fun Context.isPhotonTv(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
