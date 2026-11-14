package com.prism.music.photon

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
internal fun PlaylistDeletionDialog(title: String, confirm: () -> Unit, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = cancel,
        title = { Text("Delete playlist?") },
        text = { Text("Delete “$title” from your Google account? The videos themselves won't be deleted.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete playlist") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } })
}
