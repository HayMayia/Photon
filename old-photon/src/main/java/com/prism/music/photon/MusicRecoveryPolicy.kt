package com.prism.music.photon

internal object MusicRecoveryPolicy {
    fun allowed(video: Video?, library: PhotonLibrary, television: Boolean): Boolean =
        library.musicOverride && video?.music == true && MusicPlaybackPolicy.backgroundAllowed(true,
            library.musicBackground, library.videoBackground, television, library.tvMusicBackground)
    fun position(positionMs: Long, durationMs: Long): Long = WatchProgress(positionMs, durationMs).resumeMs().coerceAtLeast(0)
}
