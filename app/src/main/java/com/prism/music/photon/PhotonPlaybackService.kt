package com.prism.music.photon

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastTick = android.os.SystemClock.elapsedRealtime()
    private var lastVideo: Video? = null
    private var wasPlaying = false
    private val json = Json { ignoreUnknownKeys = true }

    private fun snapshot() {
        val player = session?.player ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsed = if (wasPlaying) now - lastTick else 0L
        lastTick = now
        val raw = player.currentMediaItem?.mediaMetadata?.extras?.getString("photon_video") ?: return
        val video = runCatching { json.decodeFromString<Video>(raw) }.getOrNull() ?: return
        if (player.isPlaying || (wasPlaying && lastVideo?.id == video.id)) {
            (application as PhotonApplication).library.recordPlayback(video, player.currentPosition, player.duration, elapsed)
            lastVideo = video
        }
        wasPlaying = player.isPlaying
    }

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
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                snapshot()
                lastTick = android.os.SystemClock.elapsedRealtime()
            }
        })
        scope.launch {
            while (isActive) { delay(5_000); snapshot() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // An explicitly paused session can stop; playing media keeps its foreground notification.
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        snapshot()
        scope.cancel()
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
            .setExtras(android.os.Bundle().apply {
                putString("photon_video", Json.encodeToString(video))
                quality?.audioUrl?.let { putString("photon_audio", it) }
            })
            .build())
        .build()
