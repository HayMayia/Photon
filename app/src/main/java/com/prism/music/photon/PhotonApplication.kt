package com.prism.music.photon

import android.app.Application
import com.prism.music.ui.theme.AppFonts
import com.prism.music.ui.theme.IconStyle

/** Photon deliberately does not start Prism's music jobs, account sync, or update installer. */
class PhotonApplication : Application() {
    internal val updates by lazy { PhotonUpdates(this) }
    internal val tvForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
    val account by lazy { YouTubeAccount() }
    internal val accountActions by lazy { YouTubeAccountActionsApi() }
    internal val accountLibrary by lazy { YouTubeAccountLibraryApi() }
    val youtubeHome by lazy { YouTubeHomeApi() }
    val youtubeHistory by lazy { YouTubeHistoryCoordinator(library) }
    val casting by lazy { PhotonCast(this, videos) }
    val library by lazy { PhotonStore(this) }
    lateinit var videos: VideoRepository
        private set
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) { if (activity is PhotonActivity) tvForeground.value = true }
            override fun onActivityPaused(activity: android.app.Activity) { if (activity is PhotonActivity) tvForeground.value = false }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        IconStyle.load(this)
        AppFonts.init(this)
        videos = VideoRepository()
        if (library.state.value.downloads.isNotEmpty()) OfflineArtwork.schedule(this, library.state.value.wifiOnly)
    }
}
