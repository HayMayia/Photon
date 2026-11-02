# Photon 1.1.0.10 Beta

- Playlist loading now selects the requested playlist's entry list, not preview cards or unrelated page sections. A different returned playlist ID is rejected. Preview-only bootstrap pages trigger an authenticated browse request for the exact playlist.
- Modern playlist video cards without contentType are accepted only when their watch target proves the video ID. Unknown lockups are not inferred from an ID alone.
- Normal playlist/song entries use compact rows so several can be seen together instead of one large thumbnail/action card filling the screen. Portrait Shorts retain the existing two-column library layout. Switching playlists resets the scroll position.
- Recently opened account playlists reuse their exact-account, exact-playlist page for five minutes; Refresh bypasses this cache. Playlist mutations invalidate cached pages. Liked Music's persistent cache remains separate.
- Deleting a playlist now requires a confirmation naming the playlist. Cancel sends no deletion request. Like/unlike and other actions do not gain additional warnings.

Validation: **hosted run 41459160060 PASS (9m47s)**: debug/release builds, focused unit suite, permanent signing verification and publication. **PR run 41459173403 PASS**. Seven new regressions cover exact playlist content selection, wrong-playlist rejection, preview-only rejection, watch-bound modern rows, exact authenticated hydration of 20 entries, six production compact rows visible together with selection of the fifth, and cancel/confirm behavior of the production deletion dialog.

Published source `384441b0743a9dc9647277050ddd88732dc1f938`, version code 39.
Verified Photon.apk asset 701997391, 25,385,776 bytes, SHA256
`819af956cecdc82bc301943c7170b75bb70dabf938a1b05942c2594f58e30a17`.

These changes target source selection, modern row parsing, list density and repeat-load latency. Live-account and physical-device behavior remains unverified; first uncached loads still depend on Google/network latency.
