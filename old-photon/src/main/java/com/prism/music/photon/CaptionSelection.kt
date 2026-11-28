package com.prism.music.photon

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** Select an available text track when captions are enabled but no default was supplied. */
@UnstableApi
internal fun ensureCaptionTrack(player: Player, preferences: PhotonLibrary) {
    if (!preferences.captionsEnabled || !player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
    val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    if (groups.any { group -> (0 until group.length).any { group.isTrackSelected(it) } }) return
    val candidates = groups.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
    val preferred = preferences.captionLanguage.ifBlank { Locale.getDefault().language }
    val track = candidates.firstOrNull { (group, index) ->
        group.getTrackFormat(index).language?.substringBefore('-') == preferred.substringBefore('-')
    } ?: candidates.firstOrNull() ?: return
    // A disabled type can still be observed while the preceding parameter update is in flight.
    if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(track.first.mediaTrackGroup, listOf(track.second))).build()
}
