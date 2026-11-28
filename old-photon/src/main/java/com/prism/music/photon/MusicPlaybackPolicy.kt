package com.prism.music.photon

/** Server category proof only. Titles, aspect ratio, playlists and soundtrack channel counts are not proof. */
internal object MusicPlaybackPolicy {
    // Native Liked Music membership is server proof too; extraction must not erase it.
    fun retainProof(extracted: Video, selected: Video): Video = extracted.copy(music = extracted.music || selected.music)
    fun isMusicCategory(category: String?): Boolean = category?.trim()?.let { it.equals("Music", ignoreCase = true) || it == "10" } == true
    fun backgroundAllowed(music: Boolean, musicEnabled: Boolean, videosEnabled: Boolean, television: Boolean, tvMusicEnabled: Boolean = false): Boolean =
        if (television) music && tvMusicEnabled else if (music) musicEnabled else videosEnabled
}

internal object LikedMusicPlaylist {
    const val NAME = "Liked Music"
}
