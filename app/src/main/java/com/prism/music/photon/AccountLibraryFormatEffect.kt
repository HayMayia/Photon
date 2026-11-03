package com.prism.music.photon

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*

/** Correct format once evidence arrives, but never repack during an active scroll gesture. */
@Composable
internal fun AccountLibraryFormatEffect(grid: LazyGridState, rows: List<AccountEntry>, commit: () -> Unit) {
    val currentRows by rememberUpdatedState(rows)
    val currentCommit by rememberUpdatedState(commit)
    LaunchedEffect(grid) {
        snapshotFlow { grid.isScrollInProgress to currentRows }.collect { (scrolling, entries) ->
            if (!scrolling && AccountLibraryRows.needsFormatCommit(entries)) currentCommit()
        }
    }
}
