package com.prism.music.photon
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
@androidx.annotation.OptIn(UnstableApi::class)
class TvRenderTestActivity : ComponentActivity() {
    internal lateinit var output: PhotonPlayerView
    internal val back = TvBackDispatcher()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = {
                    (layoutInflater.inflate(com.prism.music.R.layout.photon_tv_video, null) as PhotonPlayerView).also { view ->
                        view.configureTvOverlayFocus(true, false); output = view
                    }
                }, modifier = Modifier.fillMaxSize())
                Text("Photon TV · real-frame probe", color = Color.White)
            }
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = back.dispatch(event) || super.dispatchKeyEvent(event)
}

