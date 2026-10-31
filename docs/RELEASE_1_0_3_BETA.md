# v1.0.3 Beta

Fixes library cards changing positions and apparently repeating during scrolling.

- Grid spans and thumbnail-frame aspect ratios are committed when each entry enters the current dataset. Scrolling, lazy-card remounts, late Shorts probes and global metadata-cache eviction cannot repack admitted rows. Already-known Shorts retain their two-column portrait layout.
- If an initially unknown video is identified as a Short later, its portrait artwork fits within the reserved frame and playback uses the resolved format. The frame is not resized mid-browse. An explicit library refresh can create a newly packed layout from the now-known metadata; no duration/aspect guess is made.
- Keep resolved presentation on the current account dataset instead of relying only on the bounded global presentation map.
- Merge continuation pages by actual video identity, not renderer identity, retaining first-seen order. Continuations preserve any metadata already resolved on existing rows, and old dataset generations cannot overwrite newer requests.
- The header-overlap fix, wrapped titles, real-account actions, quiet update banner, TV foreground-only playback and permanent signing identity are unchanged.

Adds pure regressions for immutable geometry, overlapping pages/renderer aliases, cache eviction and intentional refresh, plus an actual Robolectric Compose scroll/remount/append regression asserting unchanged visible keys, offsets, anchor and unique cards.

Delivered through the existing Home updater. If a recent check is cached, force-stop and reopen Photon, then visit Home. Do not clear storage or uninstall. Build/layout tests are not physical-device or live-account certification.
