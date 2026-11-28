package com.prism.music.photon

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import androidx.media3.common.Player
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
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
import androidx.media3.session.DefaultMediaNotificationProvider

/** A real media session: headset, lock-screen and background playback survive closing the screen. */
@UnstableApi
class PhotonPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var navigationJob: Job? = null
    private var lastTick = android.os.SystemClock.elapsedRealtime()
    private var lastVideo: Video? = null
    private var wasPlaying = false
    private var historyWasPaused = false
    private val accountHistoryGate = PlaybackHistoryGate()
    private var historyRefreshJob: Job? = null
    private var lastHistoryRefresh = -30_000L
    private val json = Json { ignoreUnknownKeys = true }

    private fun playbackAllowed(): Boolean {
        val app = application as PhotonApplication
        val television = isPhotonTv()
        if (if (television) app.tvForeground.value else app.playbackForeground.value) return true
        val library = app.library.state.value
        val raw = session?.player?.currentMediaItem?.mediaMetadata?.extras?.getString("photon_video")
        val music = raw?.let { runCatching { json.decodeFromString<Video>(it).music }.getOrDefault(false) } ?: false
        return MusicPlaybackPolicy.backgroundAllowed(music, library.musicBackground, library.videoBackground, television, library.tvMusicBackground)
    }

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
        accountHistoryGate.tick(viewKey, now, player.isPlaying, eligible, AccountHistoryPolicy.reportThreshold(library))?.let { watched ->
            if (id != null) scope.launch { try { history.report(id, watched, itemRevision) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* No automatic retry or UI credential logging. */ } }
        }
        // Recovery is device-local transport state, independent of paused Google history.
        player.currentMediaItem?.mediaMetadata?.extras?.getString("photon_video")?.let { raw ->
            getSharedPreferences("photon_media_resume", MODE_PRIVATE).edit()
                .putString("video", raw).putLong("position", player.currentPosition.coerceAtLeast(0))
                .putLong("duration", player.duration.coerceAtLeast(0)).apply()
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
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(com.prism.music.R.drawable.ic_notification)
        })
        // Match Prism: paused/restored queues must keep a visible media control card too.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)
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
                .setBufferDurationsMs(StartupPolicy.MIN_BUFFER_MS, StartupPolicy.MAX_BUFFER_MS, StartupPolicy.START_BUFFER_MS, StartupPolicy.REBUFFER_MS)
                .setPrioritizeTimeOverSizeThresholds(true).build())
            .build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxAudioChannelCount(Int.MAX_VALUE).build()
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        val launch = PendingIntent.getActivity(this, 0, Intent(this, PhotonActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val app = application as PhotonApplication
        val transport = app.transport
        fun navigate(forward: Boolean) {
            if (navigationJob?.isActive == true) return
            navigationJob = scope.launch {
                transport.busy.value = true; transport.error.value = null
                val started = transport.state.value
                try {
                    if (started.source?.startsWith("account:") == true)
                        check(started.revision == app.library.state.value.youtubeSessionRevision && app.library.state.value.youtubeSessionReady) { "Reconnect the playlist's Google account" }
                    var state = started
                    if (forward && state.next() == null && state.continuation != null) {
                        val seen = mutableSetOf<String>()
                        for (pageNumber in 0 until 200) {
                            val token = state.continuation ?: break
                            check(seen.add(token)) { "Playlist page repeated" }
                            val incoming: List<Video>
                            if (transport.publicPage != null) {
                                val page = app.videos.playlist(requireNotNull(transport.publicUrl), transport.publicPage)
                                transport.publicPage = page.next
                                incoming = page.videos
                                state = state.copy(continuation = if (page.next != null) "@public:$pageNumber" else null)
                            } else {
                                check(!token.startsWith("@public")) { "Reopen this public playlist to load more items" }
                                val page = app.accountLibrary.more(app.youtubeHistory.session(),
                                    AccountLibraryPage(emptyList(), token, state.context), AccountEntryKind.VIDEO)
                                incoming = page.items.mapNotNull { it.video }
                                state = state.copy(continuation = page.continuation, context = page.context)
                            }
                            state = state.copy(items = PlaylistQueuePolicy.append(state.items, incoming))
                            if (state.next() != null) break
                        }
                    }
                    val target = (if (forward) state.next() else state.previous()) ?: return@launch
                    val library = app.library.state.value
                    val local = library.downloads.firstOrNull { it.video.id == target.id && java.io.File(it.path).isFile }
                    val details = if (local == null) app.videos.details(target.id) else null
                    // A foreground selection or account change during extraction wins over this request.
                    if (transport.state.value.currentId != started.currentId || transport.state.value.source != started.source) return@launch
                    if (state.source?.startsWith("account:") == true)
                        check(state.revision == app.library.state.value.youtubeSessionRevision && app.library.state.value.youtubeSessionReady)
                    val video = details?.video?.copy(music = target.music || details.video.music, short = target.short || details.video.short) ?: target
                    val quality = details?.let { it.qualities.firstOrNull { q -> q.height <= library.defaultHeight } ?: it.qualities.last() }
                    transport.update(state.move(video, details?.related.orEmpty()))
                    player.setMediaItem(playbackItem(video, quality, local?.path, details?.subtitles.orEmpty(),
                        android.os.SystemClock.elapsedRealtime(), library.youtubeSessionRevision))
                    player.prepare(); player.play()
                    transport.selection.value = TransportSelection(video, details, local?.path)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { transport.error.value = "Could not play the requested queue item. Your playlist has not been replaced. Retry Next or Previous." }
                finally { transport.busy.value = false }
            }
        }
        app.transportNavigate = ::navigate
        val transportPlayer = QueueSessionPlayer(player, { transport.state.value }, ::navigate)
        scope.launch { transport.state.collect {
            transportPlayer.refreshCommands()
            session?.let { onUpdateNotification(it, false) }
        } }
        session = MediaSession.Builder(this, transportPlayer).setSessionActivity(launch)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    val forward = when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT, android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> true
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> false
                        else -> return false // Play/Pause stays with Media3; do not hijack seek/volume keys.
                    }
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) navigate(forward)
                    return true
                }
                override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
                    val app = application as PhotonApplication
                    val library = app.library.state.value
                    val stored = getSharedPreferences("photon_media_resume", MODE_PRIVATE)
                    val savedVideo = stored.getString("video", null)?.let { runCatching { json.decodeFromString<Video>(it) }.getOrNull() }
                    val queue = app.transport.state.value
                    val video = queue.items.firstOrNull { it.id == queue.currentId } ?: savedVideo
                        ?: throw UnsupportedOperationException("No previous music to resume")
                    if (queue.source?.startsWith("account:") == true)
                        check(queue.revision == library.youtubeSessionRevision && library.youtubeSessionReady)
                    check(MusicRecoveryPolicy.allowed(video, library, isPhotonTv())) {
                        "Music recovery is disabled"
                    }
                    val local = library.downloads.firstOrNull { it.video.id == video.id && java.io.File(it.path).isFile }
                    val item = if (local != null) playbackItem(video, localPath = local.path)
                    else {
                        // Never reuse an expired signed media URL after process death.
                        val details = app.videos.details(video.id)
                        val quality = details.qualities.firstOrNull { it.height <= library.defaultHeight }
                            ?: details.qualities.lastOrNull() ?: error("No playable stream")
                        playbackItem(details.video.copy(music = video.music), quality, subtitles = details.subtitles,
                            openedAtMs = android.os.SystemClock.elapsedRealtime(), webRevision = library.youtubeSessionRevision)
                    }
                    if (app.transport.state.value.currentId != video.id)
                        app.transport.select(video, emptyList(), emptyList(), null, library.youtubeSessionRevision, null)
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, if (savedVideo?.id == video.id) MusicRecoveryPolicy.position(stored.getLong("position", 0), stored.getLong("duration", 0)) else 0)
                }
            }).build()
        // Register immediately, including cold media-button starts, instead of depending on UI binding.
        addSession(requireNotNull(session))
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) snapshot(true)
            }
            override fun onRenderedFirstFrame() {
                val opened = player.currentMediaItem?.mediaMetadata?.extras?.getLong("photon_open_elapsed", 0) ?: 0
                if (opened > 0) PhotonDiagnostics.event("First frame after open: ${android.os.SystemClock.elapsedRealtime() - opened} ms")
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && !playbackAllowed()) player.pause()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!playbackAllowed()) player.pause()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) navigate(true)
                PhotonDiagnostics.event("Playback state=$state")
                if (state == androidx.media3.common.Player.STATE_BUFFERING && player.playWhenReady && player.currentPosition > 0)
                    PhotonDiagnostics.event("Rebuffer: buffered=${player.totalBufferedDuration} ms, speed=${player.playbackParameters.speed}")
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { PhotonDiagnostics.event("Playback error=${error.errorCodeName}") }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PhotonDiagnostics.event("Playing=$isPlaying")
                snapshot()
                lastTick = android.os.SystemClock.elapsedRealtime()
            }
        })
        scope.launch {
            val app = application as PhotonApplication
            combine(app.playbackForeground, app.tvForeground, app.library.state) { _, _, _ -> Unit }
                .collect { if (!playbackAllowed()) player.pause() }
        }
        scope.launch {
            (application as PhotonApplication).sleepTimer.deadline.collectLatest { deadline ->
                if (deadline != null) {
                    delay((deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    if ((application as PhotonApplication).sleepTimer.expire(deadline)) {
                        player.pause()
                        snapshot(true)
                    }
                }
            }
        }
        scope.launch {
            while (isActive) { delay(5_000); snapshot() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playbackAllowed()) { session?.player?.pause(); stopSelf(); return }
        // An explicitly paused session can stop; playing media keeps its foreground notification.
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        snapshot(true)
        (application as PhotonApplication).transportNavigate = null
        scope.cancel()
        (application as PhotonApplication).sleepTimer.set(0)
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
