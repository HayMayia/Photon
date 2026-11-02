package com.prism.music.photon

/** Keep Google's order. Only consecutive, source-proven Shorts share a two-column row. */
internal object HomeFeedRows {
    data class Row(val videos: List<Video>, val portrait: Boolean) { val key get() = videos.first().id }
    fun make(videos: List<Video>): List<Row> {
        val unique = videos.distinctBy { it.id }
        val rows = mutableListOf<Row>()
        var index = 0
        while (index < unique.size) {
            val first = unique[index++]
            val items = mutableListOf(first)
            if (first.short && index < unique.size && unique[index].short) items += unique[index++]
            rows += Row(items, first.short)
        }
        return rows
    }
}
