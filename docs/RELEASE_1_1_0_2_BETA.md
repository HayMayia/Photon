# v1.1.0.2 Beta

Playback stability takes priority over speculative startup savings.

- Separate media/chunk networking from short metadata deadlines. Both the primary stream and prefetched ranges now have no whole-response lifetime limit, while connection/read stalls remain bounded (10/35 seconds). A prefetched body waiting behind the existing playback buffer no longer inherits the extractor's 35-second total deadline. Media connections/dispatch are independent of optional metadata work.
- Restore the older 750 ms initial buffer (before the 1.1.0.1 reduction); retain 15–50 seconds of buffer and the 1.5-second rebuffer threshold. Do not silently reduce picture quality, switch language or discard original supplied multichannel audio.
- Remove automatic next-video metadata extraction while playing. Existing details caching still helps repeat opens, but active playback does not compete with speculative extraction.
- Fail a truncated range explicitly so the media loader can recover, instead of silently refetching incomplete ranges. Bound reads to each requested chunk and test exact continuity across boundaries.
- Add local rebuffer diagnostics (buffer duration/speed, no stream URL or credentials).

Compared source against 0.12.0-alpha (the older 0.12 Beta generation); its 750 ms startup threshold is restored. Source confirms the deadline/prefetch hazard, not that it was the sole cause on this phone. Physical continuous-playback, pause/resume, weak-network and high-speed testing remains pending. This release does not promise zero buffering on every connection. Thumbnail/Shorts, 360/viewer and Google authority policies are retained.
