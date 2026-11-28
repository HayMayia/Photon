package com.prism.music.photon

/** Feed membership is explicit; a Short never borrows a mutable search/recommendation list. */
internal object TvPlaybackPolicy {
    fun adjacent(items: List<Video>, currentId: String?, delta: Int): Video? {
        val shorts = items.filter { it.short }
        val index = shorts.indexOfFirst { it.id == currentId }
        return if (index < 0 || delta !in listOf(-1, 1)) null else shorts.getOrNull(index + delta)
    }
    fun autoAdvance(state: PlaybackQueueState, recommendationsEnabled: Boolean): Boolean {
        if (state.source?.startsWith("feed:") == true || state.items.firstOrNull { it.id == state.currentId }?.short == true) return false
        return playlist(state.source) || (recommendationsEnabled && state.recommendationsAllowed)
    }
    fun playlist(source: String?): Boolean = source != null && !source.startsWith("feed:")
}
