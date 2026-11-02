# Photon 1.1.0.12 Beta

- Keep instant cached Liked Music opening, but automatically repair caches written by earlier versions. A cached single song is no longer accepted as proof that the whole account scan completed successfully.
- Newest likes are checked from the first Google page at two-minute intervals while active, adding source-proven music to the cached list without rescanning all old pages. Older cached songs are retained. Completed full reconciliation still uses the six-hour interval.
- Persist scan completeness and unresolved metadata. Incomplete scans retry in the background after a two-minute backoff rather than being frozen for six hours or fetched on every navigation. Existing confirmed music stays visible during repair.
- Preserve continuation tokens after large pages: the former 250-row early return could silently discard a trailing token and incorrectly finish a scan.
- Support source-proven music row renderers and exact-video music watch configurations, avoiding unnecessary per-video lookups when Google already identifies the row as music. Unreadable video rows are counted as unresolved, not silently treated as non-music.
- Optional authenticated music metadata reads have a six-second deadline. Refresh progress is shown instead of silently displaying an incomplete list as finished.

Validation: **run 41465004904 PASS (9m35s)**: debug/release builds, focused Android unit suite, permanent signer verification and publication. **PR run 41465017099 PASS**. Seven new checks cover legacy one-song cache repair, incomplete-scan retry versus completed freshness, all 300 entries plus a trailing continuation, exact-video music configuration binding, music row parsing, unsupported row accounting, and adding new head-page likes without dropping older cached songs.

Published source `24cf6c59333db3c5acdb89b9fb40eed0d8c2778e`, version code 41.
Verified Photon.apk asset 702103795, 25,385,776 bytes, SHA256
`88fcc13160216b41511a61fd2d0421c90d3d2162205c1ff1d1431f29e8c41024`.

These changes address confirmed cache/completeness and pagination defects; the exact cause of the user's live account containing only one song is not established without live-account verification. No title/channel guessing or generated songs are substituted. Checks run while Photon is active, not as guaranteed scheduled sync after it is stopped. Unavailable source metadata can still delay individual entries.

1.1.0.11 is superseded: it published the first recovery changes but not the complete incremental-head refresh. This independently versioned release includes both.
