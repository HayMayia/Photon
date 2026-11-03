# Photon 1.1.0.13 Beta

- Fix the shared Liked videos/Liked Music loader rejecting modern account playlist layouts. The selector previously required playlistVideoListRenderer even after an authenticated, exact-playlist browse succeeded. It now accepts the selected primary richGrid/sectionList layout, bound to the requested playlist or its canonical identity.
- Continue excluding sidebar and unselected tabs, rejecting explicit wrong playlist identities and signed-out/error responses. Bare preview cards are not accepted as playlists.
- Keep the fast music snapshot, incremental newest-like checks, playlist queues and deletion confirmation unchanged.
- Do not claim songs were retained when the failed initial music load had no cached songs.

Validation: **run 41611885395 PASS (9m19s)**: debug/release, focused Android unit suite, permanent signer verification and publication. **PR run 41611892271 PASS**. Six new regressions cover modern bound primary extraction, canonical-bound HTML, unbound HTML/unselected-tab exclusion, wrong-identity rejection, signed-out rejection, and both liked shelves through authenticated HTML/bootstrap + exact VLLL browse requests.

Published source `02d4ef412aa7d30b08de19f665c036a18d062164`, version code 42.
Verified Photon.apk asset 705073717, 25,385,772 bytes, SHA256
`e1ef04047e6634691543d158226e44d0afc728876b386418a14e2986b8547d48`.

The parser rejection is reproducible with modern response fixtures; the user's private live-account response remains unavailable in this environment. These tests do not prove that every possible account-side authentication/layout failure is resolved.
