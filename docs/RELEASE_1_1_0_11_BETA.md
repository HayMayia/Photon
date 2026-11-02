# Photon 1.1.0.11 Beta

- Keep instant cached Liked Music opening, but automatically repair caches written by earlier versions. A cached single song is no longer accepted as proof that the whole account scan completed successfully.
- Newest likes are checked from the first Google page at two-minute intervals while active, adding source-proven music to the cached list without rescanning all old pages. Older cached songs are retained. Completed full reconciliation still uses the six-hour interval.
- Persist scan completeness and unresolved metadata. Incomplete scans retry in the background after a two-minute backoff rather than being frozen for six hours or fetched on every navigation. Existing confirmed music stays visible during repair.
- Preserve continuation tokens after large pages: the former 250-row early return could silently discard a trailing token and incorrectly finish a scan.
- Support source-proven music row renderers and exact-video music watch configurations, avoiding unnecessary per-video lookups when Google already identifies the row as music. Unreadable video rows are counted as unresolved, not silently treated as non-music.
- Optional authenticated music metadata reads have a six-second deadline. Refresh progress is shown instead of silently displaying an incomplete list as finished.

Validation pending hosted Android tests/build and publication. These changes address confirmed cache/completeness and pagination defects; the exact cause of the user's live account containing only one song is not established without live-account verification. No title/channel guessing or generated songs are substituted.
