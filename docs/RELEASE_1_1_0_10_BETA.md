# Photon 1.1.0.10 Beta

- Playlist loading now selects the requested playlist's entry list, not preview cards or unrelated page sections. A different returned playlist ID is rejected. Preview-only bootstrap pages trigger an authenticated browse request for the exact playlist.
- Modern playlist video cards without contentType are accepted only when their watch target proves the video ID. Unknown lockups are not inferred from an ID alone.
- Normal playlist/song entries use compact rows so several can be seen together instead of one large thumbnail/action card filling the screen. Portrait Shorts retain the existing two-column library layout. Switching playlists resets the scroll position.
- Recently opened account playlists reuse their exact-account, exact-playlist page for five minutes; Refresh bypasses this cache. Playlist mutations invalidate cached pages. Liked Music's persistent cache remains separate.
- Deleting a playlist now requires a confirmation naming the playlist. Cancel sends no deletion request. Like/unlike and other actions do not gain additional warnings.

Hosted build and tests pending. These changes target source selection, modern row parsing, list density and repeat-load latency; live-account and physical-device behavior remains unverified.
