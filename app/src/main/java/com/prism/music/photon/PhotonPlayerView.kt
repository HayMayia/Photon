package com.prism.music.photon

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/** Observe native touch routing rather than covering PlayerView with a gesture-stealing overlay. */
@UnstableApi
internal class PhotonPlayerView(context: Context) : PlayerView(context) {
    private var seekingGesture = false
    private var feedback: Toast? = null
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(event: MotionEvent): Boolean {
            val current = player ?: return false
            if (!useController || !current.isCurrentMediaItemSeekable ||
                !current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                hitsControl(this@PhotonPlayerView, event.rawX, event.rawY)) return false
            val forward = event.x >= width / 2f
            current.seekTo(VideoSeek.target(current.currentPosition, current.duration, forward))
            feedback?.cancel()
            feedback = Toast.makeText(context, if (forward) "Forward 10 seconds" else "Rewind 10 seconds", Toast.LENGTH_SHORT).also { it.show() }
            seekingGesture = true
            return true
        }
    })

    private fun hitsControl(group: ViewGroup, x: Float, y: Float): Boolean {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.alpha == 0f || !child.isShown) continue
            val bounds = android.graphics.Rect()
            if (!child.getGlobalVisibleRect(bounds) || !bounds.contains(x.toInt(), y.toInt())) continue
            if (child is ViewGroup) {
                if (hitsControl(child, x, y)) return true
            } else if (child.isClickable || child.isFocusable) return true
        }
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        if (seekingGesture) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) seekingGesture = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        feedback?.cancel()
        seekingGesture = false
        super.onDetachedFromWindow()
    }
}
