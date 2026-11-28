package com.prism.music.photon

/** Local presentation only: never changes Google's native Liked Songs membership. */
internal enum class LikedMusicFilter(val title: String) {
    ALL("All"), SHORTS("Shorts"), FULL_LENGTH("Full-length");

    fun apply(rows: List<AccountEntry>): List<AccountEntry> = when (this) {
        ALL -> rows
        SHORTS -> rows.filter { it.video != null && AccountGridPolicy.portrait(it) }
        FULL_LENGTH -> rows.filter { it.video != null && !AccountGridPolicy.portrait(it) }
    }
}
