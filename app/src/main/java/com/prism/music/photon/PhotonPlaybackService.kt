package com.prism.music.photon

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** A real media session: headset, lock-screen and background playback survive closing the screen. */
@UnstableApi
class PhotonPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val repository = (application as PhotonApplication).videos
        val data = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(repository.streamHttp))
        val delegate = DefaultMediaSourceFactory(data)
        val sources = object : MediaSource.Factory {
            override fun createMediaSource(item: MediaItem): MediaSource {
                val video = delegate.createMediaSource(item)
                val audioUrl = item.mediaMetadata.extras?.getString("photon_audio") ?: return video
                val audio = delegate.createMediaSource(MediaItem.fromUri(audioUrl))
                return MergingMediaSource(true, video, audio)
            }
            override fun getSupportedTypes(): IntArray = delegate.supportedTypes
            override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
                delegate.setDrmSessionManagerProvider(provider)
                return this
            }
            override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
                delegate.setLoadErrorHandlingPolicy(policy)
                return this
            }
        }
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(sources).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        val launch = PendingIntent.getActivity(this, 0, Intent(this, PhotonActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(launch).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // An explicitly paused session can stop; playing media keeps its foreground notification.
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}

fun playbackItem(video: Video, quality: VideoQuality? = null, localPath: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(video.id)
        .setUri(localPath?.let { Uri.fromFile(java.io.File(it)).toString() } ?: requireNotNull(quality).url)
        .apply { quality?.let { setMimeType(it.mime) } }
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(video.title).setArtist(video.channel)
            .setArtworkUri(video.thumbnail.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setExtras(android.os.Bundle().apply { quality?.audioUrl?.let { putString("photon_audio", it) } })
            .build())
        .build()
