# Photon 1.1.0.9 Beta

- Liked Music reopens from a persistent, account-revision-bound, read-only snapshot instead of rescanning all likes on every visit. A stale snapshot remains usable during refresh; switching shelves no longer cancels its scan. Completed scans refresh at six-hour age or explicit Refresh, with periodic freshness checks while Photon is running. Initial metadata classification still takes time.
- Liked Music entry selection also establishes a music-only playback queue, rather than falling back to unrelated Up next videos.
- Verified likes/unlikes in Photon update the music snapshot automatically; new unknown music uses a single source-category lookup rather than rescanning every old like. External Google changes are reconciled on a stale-list refresh. This is cached Google data, not a separate custom playlist or locally simulated likes.
- Comments have a simpler composer labeled Publish Comment. Redundant browser/identity/refresh controls and duplicated post details are removed.
- Comment Like uses the existing Google connection and only a server-issued action bound to that exact comment. Already-liked controls are respected; unsupported/missing controls and unconfirmed requests report an error instead of a fake local like. Supports classic controls and modern comment/entity toolbar keys; unexposed controls can still be unavailable.
- The music snapshot is stored separately from playback preferences so progress updates do not repeatedly serialize a large music library.
- Playback retains the 750 ms initial start threshold, but buffers 45–120 seconds ahead and requires 5 seconds after a stall to reduce repeated short buffering cycles. This is increased recovery headroom, not a guarantee against inadequate bandwidth, CDN throttling or server errors.

Validation: **hosted run 41456909398 PASS (7m51s)**: debug/release builds, focused unit suite, permanent certificate verification and publication. PR run **41456918669 PASS**. Eight new checks cover snapshot serialization/defaults, scan-vs-like/unlike races, exact classic/modern comment-control bindings, missing action rejection, already-liked state, and startup/rebuffer headroom. Existing Android regressions also pass.

Published source `b952b6e9f62e450d77f43f55d95c2d0b5547f14d`, version code 38.
Verified Photon.apk asset 701948164, 25,369,392 bytes, SHA256
`05c914e9be70185ae7c6898a4c6b9a45ef9ca7b0cc4018898e0fadcc94f0e6e6`.

No live-account comment mutation or physical-device buffering comparison was performed. Comment discovery is bounded to 12 returned comment pages; some replies/unsupported controls may not be exposed. Likes are never simulated when the server cannot confirm them.
