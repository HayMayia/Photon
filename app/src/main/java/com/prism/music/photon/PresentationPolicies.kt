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
