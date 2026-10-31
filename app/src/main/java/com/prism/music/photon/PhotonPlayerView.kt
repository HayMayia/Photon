package com.prism.music.photon

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.MediaItem
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
    var remoteBack: (() -> Unit)? = null
    private val television = context.isPhotonTv()
    private var remoteHoldEligible = false
    private var remoteBoosted = false
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (television && event.keyCode == android.view.KeyEvent.KEYCODE_BACK && remoteBack != null) {
            if (event.action == android.view.KeyEvent.ACTION_UP) remoteBack?.invoke()
            return true
        }
        if (television && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) remoteHoldEligible = !isControllerFullyVisible && player?.isPlaying == true
                if (remoteBoosted) return true
                if (event.repeatCount > 0 && remoteHoldEligible && beginBoost()) { remoteBoosted = true; return true }
            } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                remoteHoldEligible = false
                if (remoteBoosted) { remoteBoosted = false; endBoost(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    private val boostBadge = android.widget.TextView(context).apply {
        text = "2×"; textSize = 14f; setTextColor(android.graphics.Color.WHITE)
        val density = resources.displayMetrics.density
        setPadding((14 * density).toInt(), (6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt())
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = 18 * density }
        visibility = View.GONE
    }
    init {
        addView(boostBadge, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
            .apply { topMargin = (16 * resources.displayMetrics.density).toInt() })
    }
    private var boostPlayer: Player? = null
    private var priorParameters: PlaybackParameters? = null
    private val boostListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) endBoost() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { endBoost() }
    }
    private fun beginBoost(): Boolean {
        val current = player ?: return false
        if (!isAttachedToWindow || !hasWindowFocus() || !useController || !current.isPlaying || current.isCurrentMediaItemLive || boostPlayer != null ||
            !current.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return false
        priorParameters = current.playbackParameters
        boostPlayer = current
        current.addListener(boostListener)
        current.playbackParameters = PlaybackParameters(2f, current.playbackParameters.pitch)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        hideController()
        feedback?.cancel()
        boostBadge.visibility = View.VISIBLE
        return true
    }
    private fun endBoost() {
        remoteHoldEligible = false
        val current = boostPlayer ?: return
        val previous = priorParameters
        boostPlayer = null; priorParameters = null
        boostBadge.visibility = View.GONE
        current.removeListener(boostListener)
        if (previous != null && current.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) current.playbackParameters = previous
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private var seekingGesture = false
    private var feedback: Toast? = null
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onLongPress(event: MotionEvent) {
            if (hitsControl(this@PhotonPlayerView, event.rawX, event.rawY) || !beginBoost()) return
            parent?.requestDisallowInterceptTouchEvent(true)
            cancelNativeTouch(event)
        }
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

    private fun cancelNativeTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) endBoost()
        if (event.pointerCount > 1) endBoost()
        gestures.onTouchEvent(event)
        if (boostPlayer != null) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) endBoost()
            return true
        }
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

    override fun setPlayer(player: Player?) { if (player !== this.player) endBoost(); super.setPlayer(player) }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { if (!hasWindowFocus) { endBoost(); remoteBoosted = false }; super.onWindowFocusChanged(hasWindowFocus) }
    override fun onDetachedFromWindow() {
        endBoost()
        remoteBoosted = false
        feedback?.cancel()
        seekingGesture = false
        super.onDetachedFromWindow()
    }
}
