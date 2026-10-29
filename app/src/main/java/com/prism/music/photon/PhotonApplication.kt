package com.prism.music.photon

import android.app.Application
import com.prism.music.ui.theme.AppFonts
import com.prism.music.ui.theme.IconStyle

/** Photon deliberately does not start Prism's music jobs, account sync, or update installer. */
class PhotonApplication : Application() {
    val library by lazy { PhotonStore(this) }
    lateinit var videos: VideoRepository
        private set
    override fun onCreate() {
        super.onCreate()
        IconStyle.load(this)
        AppFonts.init(this)
        videos = VideoRepository()
    }
}
