# Photon 1.1.0.9 Beta

- Liked Music reopens from a persistent, account-revision-bound, read-only snapshot instead of rescanning all likes on every visit. A stale snapshot remains usable during refresh; switching shelves no longer cancels its scan. Completed scans refresh at six-hour age or explicit Refresh. Initial metadata classification still takes time.
- Verified likes/unlikes in Photon update the music snapshot automatically; new unknown music uses a single source-category lookup rather than rescanning every old like. External Google changes are reconciled on a stale-list refresh. This is cached Google data, not a separate custom playlist or locally simulated likes.
- Comments have a simpler composer labeled Publish Comment. Redundant browser/identity/refresh controls and duplicated post details are removed.
- Comment Like uses the existing Google connection and only a server-issued action bound to that exact comment. Already-liked controls are respected; unsupported/missing controls and unconfirmed requests report an error instead of a fake local like. Currently supports the classic commentRenderer control; modern/unexposed controls may remain unavailable.
- Playback retains the 750 ms initial start threshold, but buffers 45–120 seconds ahead and requires 5 seconds after a stall to reduce repeated short buffering cycles. This is increased recovery headroom, not a guarantee against inadequate bandwidth, CDN throttling or server errors.

Validation pending hosted Android build, regression suite, signing and release publication. No live-account comment mutation or physical-device buffering comparison was performed.
