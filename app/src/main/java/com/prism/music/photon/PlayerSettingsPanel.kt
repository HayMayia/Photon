package com.prism.music.photon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import java.util.Locale

internal data class PlayerMenuActions(val settings: () -> Unit = {}, val captions: () -> Unit = {}, val back: () -> Unit = {})
internal val LocalPlayerMenus = staticCompositionLocalOf { PlayerMenuActions() }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSettingsPanel(model: PhotonViewModel, controller: MediaController?, captionsOnly: Boolean, dismiss: () -> Unit) {
    val initialFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    val library by model.library.collectAsStateWithLifecycle()
    var speedDraft by remember(library.playbackSpeed) { mutableFloatStateOf(PlayerOptions.speed(library.playbackSpeed)) }
    var sizeDraft by remember(library.captionSizePercent) { mutableIntStateOf(PlayerOptions.subtitleSize(library.captionSizePercent)) }
    val watch by model.watch.collectAsStateWithLifecycle()
    var tracks by remember(controller) { mutableStateOf(controller?.currentTracks ?: Tracks.EMPTY) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener { override fun onTracksChanged(value: Tracks) { tracks = value } }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    val speedEnabled = controller != null && !controller.isCurrentMediaItemLive && controller.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)
    fun update(transform: (PhotonLibrary) -> PhotonLibrary) = model.playerOptions(transform)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(.94f).fillMaxHeight(.88f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (captionsOnly) "Captions" else "Player settings", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = dismiss, modifier = Modifier.focusRequester(initialFocus).remoteFocus()) { Text("Done") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!captionsOnly) {
                        item { Text("Quality", style = MaterialTheme.typography.titleMedium) }
                        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(watch.details?.qualities.orEmpty()) { quality ->
                                FilterChip(selected = quality == watch.quality, onClick = { model.quality(quality) }, label = { Text(quality.label) }, modifier = Modifier.remoteFocus())
                            }
                        } }
                        if (watch.details == null) item { Text(if (watch.localPath != null) "Downloaded quality" else "Quality unavailable") }
                        item { Text("Speed · ${"%.2f".format(Locale.US, speedDraft).trimEnd('0').trimEnd('.')}×", style = MaterialTheme.typography.titleMedium) }
                        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PlayerOptions.speeds) { speed ->
                                FilterChip(selected = kotlin.math.abs(library.playbackSpeed - speed) < .001f,
                                    enabled = speedEnabled, onClick = { update { it.copy(playbackSpeed = speed) } },
                                    label = { Text(if (speed == 1f) "1× (normal)" else "${speed}×") }, modifier = Modifier.remoteFocus())
                            }
                        } }
                        item { Slider(value = speedDraft, onValueChange = { v -> speedDraft = kotlin.math.round(v * 10) / 10f },
                            onValueChangeFinished = { update { it.copy(playbackSpeed = speedDraft) } }, enabled = speedEnabled, valueRange = .1f..10f, steps = 98, modifier = Modifier.remoteFocus()) }
                        if (!speedEnabled) item { Text("Speed changes are unavailable for this playback.") }
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Repeat video")
                            Switch(library.repeatVideo, { value -> update { it.copy(repeatVideo = value) } }, modifier = Modifier.remoteFocus())
                        } }
                    }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Captions")
                        Switch(library.captionsEnabled, { value -> update { it.copy(captionsEnabled = value) } }, modifier = Modifier.remoteFocus())
                    } }
                    item { Text("Language", style = MaterialTheme.typography.titleMedium) }
                    item { TextButton(onClick = {
                        controller?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).build() }
                        update { it.copy(captionsEnabled = true, captionLanguage = "") }
                    }, modifier = Modifier.remoteFocus()) { Text("Automatic") } }
                    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                        items((0 until group.length).filter { group.isTrackSupported(it) }) { index ->
                            val format = group.getTrackFormat(index)
                            val language = format.language.orEmpty()
                            TextButton(onClick = {
                                update { it.copy(captionsEnabled = true, captionLanguage = language) }
                                controller?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build() }
                            }, modifier = Modifier.remoteFocus()) {
                                Text((if (group.isTrackSelected(index)) "✓ " else "") + (format.label ?: language.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).displayLanguage } ?: "Unspecified"))
                            }
                        }
                    }
                    if (tracks.groups.none { it.type == C.TRACK_TYPE_TEXT }) item { Text("No captions available for this video.") }
                    item { Text("Size · ${sizeDraft}%", style = MaterialTheme.typography.titleMedium) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PlayerOptions.subtitleSizes) { size ->
                            FilterChip(selected = library.captionSizePercent == size, onClick = { update { it.copy(captionSizePercent = size) } }, label = { Text("$size%") }, modifier = Modifier.remoteFocus())
                        }
                    } }
                    item { Slider(value = sizeDraft.toFloat(), onValueChange = { v -> sizeDraft = v.toInt() },
                        onValueChangeFinished = { update { it.copy(captionSizePercent = sizeDraft) } }, valueRange = 25f..200f, steps = 174, modifier = Modifier.remoteFocus()) }
                    item { Text("Appearance", style = MaterialTheme.typography.titleMedium) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("White on black", "Yellow on black", "White with outline")) { style ->
                            FilterChip(selected = library.captionStyle == style, onClick = { update { it.copy(captionStyle = style) } }, label = { Text(style) }, modifier = Modifier.remoteFocus())
                        }
                    } }
                }
            }
        }
    }
}
