package com.prism.music.photon

/** A playlist never falls through to video recommendations, even at its end. */
internal object PlaylistQueuePolicy {
    fun append(current: List<Video>, incoming: List<Video>): List<Video> = (current + incoming).distinctBy { it.id }
    fun upcoming(queue: List<Video>, currentId: String?, playlist: Boolean, related: List<Video>): List<Video> {
        if (!playlist) return related
        val index = queue.indexOfFirst { it.id == currentId }
        return if (index >= 0) queue.drop(index + 1) else emptyList()
    }
    fun key(accountRevision: Long, id: String) = "account:$accountRevision:$id"
}
