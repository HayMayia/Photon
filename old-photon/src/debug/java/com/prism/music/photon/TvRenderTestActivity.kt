package com.prism.music.photon
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.media3.common.util.UnstableApi
@androidx.annotation.OptIn(UnstableApi::class)
class TvRenderTestActivity : ComponentActivity() {
    internal lateinit var output: PhotonPlayerView
    internal val back = TvBackDispatcher()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = layoutInflater.inflate(com.prism.music.R.layout.photon_tv_video, null) as PhotonPlayerView
        output.configureTvOverlayFocus(true, false)
        setContentView(output)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = back.dispatch(event) || super.dispatchKeyEvent(event)
}

