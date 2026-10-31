package com.prism.music.photon

/** Shapes follow explicit Shorts metadata, never guesses based on a video's duration. */
internal object VideoArtworkShape {
    fun ratio(short: Boolean): Float = if (short) 9f / 16f else 16f / 9f
    fun portraitPriority(short: Boolean, width: Int, height: Int): Int = if (short && width > 0 && height > width) 1 else 0
    fun rowWidth(short: Boolean): Int = if (short) 72 else 128
}

/** A valid draft can start authorization/review; publishing still requires official authorization. */
internal object CommentComposerPolicy {
    fun validDraft(draft: String): Boolean = draft.trim().let { it.isNotEmpty() && it.length <= 10_000 }
    fun canReview(draft: String, posting: Boolean, authorizing: Boolean, commentsDisabled: Boolean): Boolean =
        validDraft(draft) && !posting && !authorizing && !commentsDisabled
    fun reason(draft: String, posting: Boolean, authorizing: Boolean, commentsDisabled: Boolean): String? = when {
        commentsDisabled -> "YouTube reports that comments are disabled for this video."
        posting -> "Waiting for YouTube to confirm your comment."
        authorizing -> "Finish Google's comment authorization to continue. Your draft is kept."
        draft.trim().isEmpty() -> "Write a comment to enable review and posting."
        draft.trim().length > 10_000 -> "Keep the comment within 10,000 characters."
        else -> null
    }
}

/** Navigation history, separate from public YouTube watch history. */
internal object PhotonNavigation {
    fun record(history: List<Int>, current: Int, target: Int): List<Int> =
        if (current == target) history else (history + current).takeLast(32)
    fun previous(history: List<Int>, current: Int): Int? = history.lastOrNull() ?: 0.takeIf { current != 0 }
}

internal object PlayerLayoutPolicy {
    fun immersive(manual: Boolean, landscape: Boolean, expanded: Boolean, pip: Boolean, shorts: Boolean): Boolean =
        expanded && (manual || landscape) && !pip && !shorts
}

/** Only confirmed Shorts share rows; normal videos and library collections stay full width. */
internal object AccountGridPolicy {
    fun span(video: Boolean, short: Boolean): Int = if (video && short) 1 else 2
    fun portrait(entry: AccountEntry): Boolean = entry.layoutShort ?: (entry.video?.short == true)
    fun span(entry: AccountEntry): Int = span(entry.video != null, portrait(entry))
    fun ratio(entry: AccountEntry): Float = VideoArtworkShape.ratio(portrait(entry))
}

/** Dataset-scoped immutable geometry, independent of the global presentation LRU. */
internal object AccountLibraryRows {
    fun key(entry: AccountEntry): String = "${entry.kind}:${entry.video?.id ?: entry.id}"
    fun append(existing: List<AccountEntry>, incoming: List<AccountEntry>, metadata: Map<String, Video> = emptyMap()): List<AccountEntry> {
        val rows = linkedMapOf<String, AccountEntry>()
        existing.forEach { row -> rows.putIfAbsent(key(row), row.copy(layoutShort = row.layoutShort ?: (row.video?.short == true))) }
        incoming.forEach { row ->
            if (key(row) !in rows) {
                val video = row.video?.let { ShortsPresentation.merge(it, metadata[it.id]) }
                rows[key(row)] = row.copy(video = video, layoutShort = row.layoutShort ?: (video?.short == true))
            }
        }
        return rows.values.toList()
    }
    fun metadata(rows: List<AccountEntry>, resolved: Video): List<AccountEntry> = rows.map { row ->
        val source = row.video
        if (source?.id != resolved.id) row else row.copy(video = ShortsPresentation.merge(source, resolved),
            layoutShort = row.layoutShort ?: source.short)
    }
}
