# v1.1.0.5 Beta — automatic Liked Music correction

**Liked Music is now a music-only view of your actual Google-account likes, not
an independently filled playlist.** Like a music video normally; it is eligible
automatically. Unlike it and it is removed on the refreshed account read. Existing
older likes are included by automatically traversing all returned liked-video pages,
including pages with no music. No separate Save action or second Google login.

- Removed the special manual Save-to-Liked-Music destination and automatic creation
  of a custom playlist. A custom playlist created with 1.1.0.4 is left untouched;
  Photon does not silently delete user-owned Google playlists.
- Source category is read from metadata using the same pinned Google account,
  independently of playable stream/format lookup. Metadata lookups run two at a
  time; music appears incrementally in returned Google order. Checking a large old
  library can take time and is explicitly shown rather than presented as empty/done.
- Category proof is cached by account-session revision; account changes/cancellation
  prevent old results from populating a new session. Likes are never a local fallback.
- Missing/restricted/unrecognized metadata is reported as unresolved with Refresh,
  not silently labeled non-music. Google-returned limits still apply; repeated
  continuation tokens and the 200-page safety limit show an incomplete-load error.
- Music category comes from YouTube, not titles, channel names or playlist membership.
  This view targets liked music videos and is not a claim to reproduce every separate
  YouTube Music library type. Background defaults/settings from 1.1.0.4 are unchanged.

Automated tests do not certify live-account pagination or device behavior.


## Verified publication

[Run 41449550707](https://github.com/HayMayia/Photon/actions/runs/41449550707)
PASS: debug/release builds, focused unit tests including ten new automatic-music
regressions, permanent signing checks and Beta publication. Source `3e51a8c`, code 34.
Live-account scans/likes and physical phone behavior remain unverified.
