# Original Photon 1.1.1.3 Beta — queue transport correction

Only the original app (`:old-photon`, io.github.haymayia.photon) changes. Version
code 58. No redesign or changes to the supplied Firebase app.

Root cause: the previous fix attached Next to a foreground PlayerView adapter,
while the actual MediaSession still exposed a single-item player. The queue also
lived in a ViewModel and was lost when that UI was recreated; Previous was still
ExoPlayer's current-item seek behaviour.

- Move Next/Previous and end-of-video advancement into the playback service.
  MediaSession uses a queue-aware player for Bluetooth, notifications, lock screen
  and native in-app controls. Remove the separate UI-only Next adapter.
- Preserve source, ordered items, current item, related candidates and account
  continuation metadata in an application/service-owned persisted transport queue.
  Recovery no longer silently turns a Liked Music queue into recommendations.
- Liked Music/playlist queues never use related recommendations, including at the
  last item. Background playlist pagination uses the same account source; failures
  retain the queue and show an error rather than substituting random videos.
- Previous opens the preceding video, not the beginning of the current video.
  Non-playlist sessions retain their back/forward sequence as well.
- Liked Music refreshes append newly discovered items without reordering the active
  queue underneath playback. Google account-revision checks still apply.
- UI metadata and Up next follow the service's selection, without restarting a
  background-selected stream on return to the screen.

Validation: Actions run 46119416876 passed debug/release assembly, configured
Photon unit/rendered tests and permanent signing-certificate verification.
Regressions cover serialized Liked Music boundaries, queue-store recreation,
back/forward ordering, session command availability/notifications and stale
metadata clearing. Published source a2e79cfd29481124c279eca654bc5af0f42e6a4c;
GitHub asset 803624513, Photon.apk, 24,567,517 bytes.

After upgrading from 1.1.1.2, select a song from Liked Music once to establish the
new persisted source-aware queue. The previous version saved only the current
song, so its original queue cannot be reconstructed reliably. Do not uninstall.
No physical speaker/device background validation claimed; Android force-stop/
routing limits from the prior release still apply.
