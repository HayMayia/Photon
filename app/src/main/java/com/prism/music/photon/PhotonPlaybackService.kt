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
import androidx.media3.exoplayer.source.FilteringMediaSource
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
    private var historyWasPaused = false
    private val accountHistoryGate = PlaybackHistoryGate()
    private var historyRefreshJob: Job? = null
    private var lastHistoryRefresh = -30_000L
    private val json = Json { ignoreUnknownKeys = true }

    private fun snapshot(savePosition: Boolean = false) {
        val player = session?.player ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = now - lastTick
        lastTick = now
        val app = application as PhotonApplication
        val library = app.library.state.value
        val history = app.youtubeHistory
        if (player.playWhenReady && player.playbackState in setOf(androidx.media3.common.Player.STATE_BUFFERING, androidx.media3.common.Player.STATE_READY) && library.youtubeHome && library.youtubeSessionReady && now - lastHistoryRefresh >= 30_000 && historyRefreshJob?.isActive != true) {
            lastHistoryRefresh = now
            historyRefreshJob = scope.launch { history.refresh() }
        }
        val extras = player.currentMediaItem?.mediaMetadata?.extras
        val itemRevision = extras?.getLong("photon_web_revision", -1) ?: -1
        val opened = extras?.getLong("photon_open_elapsed", 0) ?: 0
        val id = player.currentMediaItem?.mediaId
        // Genuine plays may use downloaded bytes too. Live Google verification is still mandatory;
        // this does not queue or backfill plays made without a verified online account.
        val viewKey = if (opened > 0 && id != null) "$id:$opened:$itemRevision" else null
        val eligible = opened > 0 && library.accountHistorySync && library.youtubeHome && library.youtubeSessionReady &&
            itemRevision == library.youtubeSessionRevision && !history.locallyPaused()
        accountHistoryGate.tick(viewKey, now, player.isPlaying, eligible)?.let { watched ->
            if (id != null) scope.launch { try { history.report(id, watched, itemRevision) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* No automatic retry or UI credential logging. */ } }
        }
        if (history.locallyPaused()) {
            wasPlaying = false; historyWasPaused = true; return
        }
        if (historyWasPaused) { wasPlaying = false; historyWasPaused = false }
        val raw = player.currentMediaItem?.mediaMetadata?.extras?.getString("photon_video")
        val video = raw?.let { runCatching { json.decodeFromString<Video>(it) }.getOrNull() }
        if (video == null) { wasPlaying = false; return }
        val elapsed = if (wasPlaying && lastVideo?.id == video.id) delta else 0L
        if (player.isPlaying || ((wasPlaying || savePosition) && lastVideo?.id == video.id)) {
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
                return MergingMediaSource(true, FilteringMediaSource(video, setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT)), audio)
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
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(sources)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 750, 1_500)
                .setPrioritizeTimeOverSizeThresholds(true).build())
            .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        val launch = PendingIntent.getActivity(this, 0, Intent(this, PhotonActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(launch).build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) snapshot(true)
            }
            override fun onRenderedFirstFrame() {
                val opened = player.currentMediaItem?.mediaMetadata?.extras?.getLong("photon_open_elapsed", 0) ?: 0
                if (opened > 0) PhotonDiagnostics.event("First frame after open: ${android.os.SystemClock.elapsedRealtime() - opened} ms")
            }
            override fun onPlaybackStateChanged(state: Int) { PhotonDiagnostics.event("Playback state=$state") }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { PhotonDiagnostics.event("Playback error=${error.errorCodeName}") }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PhotonDiagnostics.event("Playing=$isPlaying")
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
        snapshot(true)
        scope.cancel()
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}

fun playbackItem(video: Video, quality: VideoQuality? = null, localPath: String? = null, subtitles: List<VideoSubtitle> = emptyList(), openedAtMs: Long = 0, webRevision: Long = -1): MediaItem =
    MediaItem.Builder()
        .setMediaId(video.id)
        .setUri(localPath?.let { Uri.fromFile(java.io.File(it)).toString() } ?: requireNotNull(quality).url)
        .apply { quality?.let { setMimeType(it.mime) } }
        .setSubtitleConfigurations(if (localPath != null) emptyList() else subtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url)).setMimeType(subtitle.mime)
                .setLanguage(subtitle.language).setLabel(subtitle.label).build()
        })
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(video.title).setArtist(video.channel)
            .setArtworkUri(video.thumbnail.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setExtras(android.os.Bundle().apply {
                putString("photon_video", Json.encodeToString(video))
                putLong("photon_open_elapsed", openedAtMs)
                putLong("photon_web_revision", webRevision)
                putBoolean("photon_remote", localPath == null)
                quality?.audioUrl?.let { putString("photon_audio", it) }
            })
            .build())
        .build()
