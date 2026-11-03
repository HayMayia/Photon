# Photon 1.1.0.13 Beta

- Fix the shared Liked videos/Liked Music loader rejecting modern account playlist layouts. The selector previously required playlistVideoListRenderer even after an authenticated, exact-playlist browse succeeded. It now accepts the selected primary richGrid/sectionList layout, bound to the requested playlist or its canonical identity.
- Continue excluding sidebar and unselected tabs, rejecting explicit wrong playlist identities and signed-out/error responses. Bare preview cards are not accepted as playlists.
- Keep the fast music snapshot, incremental newest-like checks, playlist queues and deletion confirmation unchanged.
- Do not claim songs were retained when the failed initial music load had no cached songs.

Validation pending hosted regression tests/build/signing/publication. The parser rejection is reproducible with modern response fixtures; the user's private live-account response remains unavailable in this environment.
