# v1.0.4 Beta

## Direct actions, no extra warnings

Like/unlike, subscribe/unsubscribe, playlist creation/add/remove/delete now submit directly to the connected YouTube account from the action tap. No extra Confirm change or success-result dialog. Existing session-revision guards, in-flight duplicate prevention, authenticated writes and fresh-read verification remain; errors remain visible, without claiming local changes as Google success.

Removed Photon-only public-comment review, login/authorization pre-dialogs, history switch/pause confirmations, root-exit confirmation, downloaded-copy delete confirmation and local-cache clear confirmation. Comment Post publishes the current valid draft directly after authorization; authorization alone never submits a draft. History settings still require the user's explicit switch/button and never override Google's pause, send unknown-state reports, or backfill past plays.

Actual Google login/consent and Android permission/install approval remain mandatory. Playlist-name forms and user-opened troubleshooting information are not warning prompts and remain.

## Shorts overlay

Shorts now keep the video full-size instead of placing metadata below it. One native-player tap reveals the title, channel, like/unlike, comments, description/details, save/share, subscription (when a channel is returned) and fullscreen actions over the video. Another tap or the native controls timeout hides the overlay. Native double-tap seeking and hold-to-2× remain; no touch-intercepting Compose layer covers playback. Comments open the existing real YouTube comment/reply/pagination and posting panel; details show returned description, views and published data. No invented counts or synthetic comments.

## Validation and delivery

Includes a rendered Compose overlay visibility/action regression. Full debug/release build and focused tests must pass before publication. No physical-device or live-account verification is claimed. Existing stable library geometry/page deduplication and header fixes remain.

Use **Home → Update to v1.0.4 Beta** after publication; if cached, force-stop/reopen then visit Home. Same package and permanent signing key; do not uninstall or clear data. No manual APK delivery.
