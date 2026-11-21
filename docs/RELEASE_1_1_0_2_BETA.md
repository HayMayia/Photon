# v1.1.0.2 Beta

Playback stability takes priority over speculative startup savings.

- Separate media/chunk networking from short metadata deadlines. Both the primary stream and prefetched ranges now have no whole-response lifetime limit, while connection/read stalls remain bounded (10/35 seconds). A prefetched body waiting behind the existing playback buffer no longer inherits the extractor's 35-second total deadline. Media connections/dispatch are independent of optional metadata work.
- Restore the older 750 ms initial buffer (before the 1.1.0.1 reduction); retain 15–50 seconds of buffer and the 1.5-second rebuffer threshold. Do not silently reduce picture quality, switch language or discard original supplied multichannel audio.
- Remove automatic next-video metadata extraction while playing. Existing details caching still helps repeat opens, but active playback does not compete with speculative extraction.
- Fail a truncated range explicitly so the media loader can recover, instead of silently refetching incomplete ranges. Bound reads to each requested chunk and test exact continuity across boundaries.
- Add local rebuffer diagnostics (buffer duration/speed, no stream URL or credentials).

Compared source against 0.12.0-alpha (the older 0.12 Beta generation); its 750 ms startup threshold is restored. Source confirms the deadline/prefetch hazard, not that it was the sole cause on this phone. Physical continuous-playback, pause/resume, weak-network and high-speed testing remains pending. This release does not promise zero buffering on every connection. Thumbnail/Shorts, 360/viewer and Google authority policies are retained.

## Verified publication

Source `397c5339d2ab3923e291d1630561b5c7c4210441`, code 31. [Run 41424687509](https://github.com/HayMayia/Photon/actions/runs/41424687509) **PASS in 9m30s**: debug/release, 227-method focused suite, permanent signing and publication. PR run 41424695349 also passes. Official prerelease targets that source; uploaded `Photon.apk` is 25,353,008 bytes. Six local signing tests pass.

The new regressions exercise a real loopback HTTP response held unread past the metadata deadline, media timeout/dispatcher/pool isolation, exact range stitching and early EOF propagation. They are not physical-device reproduction or proof that all causes of buffering have been removed.
